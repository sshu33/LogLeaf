package com.example.logleaf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.db.PostDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId


data class DayLog(
    val date: LocalDate,
    val firstPost: Post?,
    val totalPosts: Int
)

data class UiState(
    val dayLogs: List<DayLog> = emptyList(),
    val allPosts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

class MainViewModel(
    private val blueskyApi: BlueskyApi,
    private val mastodonApi: MastodonApi,
    private val sessionManager: SessionManager,
    private val postDao: PostDao
) : ViewModel() {

    // isLoadingの初期値は「true」のままの方が、UIの初期描画時に安全です。
    // 代わりに、initのロジックを修正します。
    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private val _showSettingsBadge = MutableStateFlow(false)
    val showSettingsBadge = _showSettingsBadge.asStateFlow()

    init {
        // ViewModelが生成された瞬間に、初回ロードを開始する
        loadInitialData()

        // アカウントの変更を監視し、バッジ表示や自動更新を行う
        viewModelScope.launch {
            // drop(1)で、初回ロード時の重複実行を防ぐ
            sessionManager.accountsFlow.drop(1).collect { accounts ->
                println("【MainViewModel】アカウント変更を検知（2回目以降）。自動更新します。")
                _showSettingsBadge.value = accounts.any { it.needsReauthentication }
                fetchPosts(accounts, isInitialLoad = false) // isInitialLoadは常にfalse
            }
        }
    }

    // 初回ロード専用の関数
    private fun loadInitialData() {
        viewModelScope.launch {
            // SessionManagerから、最新のアカウント情報を一度だけ取得する
            val initialAccounts = sessionManager.accountsFlow.first()
            _showSettingsBadge.value = initialAccounts.any { it.needsReauthentication }

            // 取得したアカウント情報で、初回データ取得を実行
            println("【MainViewModel】初回データロードを開始します。")
            fetchPosts(initialAccounts, isInitialLoad = true)
        }
    }

    // fetchPosts関数は、以前のあなたのコードに戻します。
    // isInitialLoadのフラグだけで制御するのが最もシンプルで安全でした。
    private fun fetchPosts(accounts: List<Account>, isInitialLoad: Boolean) {
        viewModelScope.launch {
            if (isInitialLoad) {
                _uiState.update { it.copy(isLoading = true) }
            }
            try {
                val postLists = accounts.map { account ->
                    async(Dispatchers.IO) {
                        when (account) {
                            is Account.Bluesky -> blueskyApi.getPostsForAccount(account)
                            is Account.Mastodon -> mastodonApi.getPosts(account).let { result ->
                                when (result) {
                                    is MastodonPostResult.Success -> result.posts
                                    is MastodonPostResult.TokenInvalid -> {
                                        sessionManager.markAccountForReauthentication(account.userId)
                                        emptyList()
                                    }
                                    is MastodonPostResult.Error -> {
                                        println("Mastodon API Error: ${result.message}")
                                        emptyList()
                                    }
                                }
                            }
                        }
                    }
                }
                val allPosts = postLists.awaitAll().flatten()
                postDao.insertAll(allPosts)
                val postsFromDb = postDao.getAllPosts().first()
                val dayLogs = groupPostsByDay(postsFromDb)
                _uiState.update { currentState ->
                    currentState.copy(
                        dayLogs = dayLogs,
                        allPosts = postsFromDb, // ここも修正
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                println("Error fetching posts: ${e.message}")
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun refreshPosts() {
        // すでに更新中なら何もしない（多重実行防止）
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            // まずUIに「リフレッシュ開始」を通知
            _uiState.update { it.copy(isRefreshing = true) }
            // 実際のデータ取得処理を呼び出す
            fetchPosts(sessionManager.getAccounts(), isInitialLoad = false)
        }
    }

    private fun groupPostsByDay(posts: List<Post>): List<DayLog> {
        if (posts.isEmpty()) {
            return emptyList()
        }

        // 1. 日付でグループ化する (順序はまだ保証されない)
        val groupedByDate: Map<LocalDate, List<Post>> = posts.groupBy { post ->
            post.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }

        // 2. DayLogのリストに変換する
        return groupedByDate.map { (date, postList) ->
            // ★★★ ここが核心！ ★★★
            // ★ 各日の投稿リストを、「古い順」にソートする
            val sortedPostList = postList.sortedBy { it.createdAt }

            // ★ ソート後のリストの「最初の投稿（＝その日で一番古い投稿）」を代表として選ぶ
            DayLog(
                date = date,
                firstPost = sortedPostList.firstOrNull(),
                totalPosts = postList.size
            )
        }
            // 3. 最後に、DayLogのリスト全体を「日付の新しい順」に並べ替える
            .sortedByDescending { it.date }
    }

    companion object {
        fun provideFactory(
            blueskyApi: BlueskyApi,
            mastodonApi: MastodonApi,
            sessionManager: SessionManager,
            postDao: PostDao // ★★★ 引数に postDao を追加 ★★★
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(blueskyApi, mastodonApi, sessionManager, postDao) as T // ★★★ ここにも渡す ★★★
            }
        }
    }
}