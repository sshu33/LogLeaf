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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private val _showSettingsBadge = MutableStateFlow(false)
    val showSettingsBadge = _showSettingsBadge.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.accountsFlow.collect { accounts ->
                println("【MainViewModel】アカウント変更を検知。アカウント数: ${accounts.size}")
                _showSettingsBadge.value = accounts.any { it.needsReauthentication }

                // ★ MODIFIED: 初回ロード時のみisLoadingをtrueにする
                if (_uiState.value.isLoading) {
                    fetchPosts(accounts, isInitialLoad = true)
                } else {
                    // アカウント変更後の自動更新（プルリフレッシュとは別の挙動）
                    fetchPosts(accounts, isInitialLoad = false)
                }
            }
        }
    }

    private fun fetchPosts(accounts: List<Account>, isInitialLoad: Boolean) {
        viewModelScope.launch {
            // isInitialLoadがtrueの場合のみ、画面全体のローディングインジケータを出す
            if (isInitialLoad) {
                _uiState.update { it.copy(isLoading = true) }
            }

            try {
                val postLists = accounts.map { account ->
                    async(Dispatchers.IO) { // IOスレッドでネットワーク通信
                        // ★★★ ここからが修正の核心 ★★★
                        when (account) {
                            is Account.Bluesky -> {
                                // Blueskyの場合は、とりあえずそのまま投稿リストを返す
                                blueskyApi.getPostsForAccount(account)
                            }
                            is Account.Mastodon -> {
                                // Mastodonの場合は、まずAPIの結果を受け取る
                                when (val result = mastodonApi.getPosts(account)) {
                                    is MastodonPostResult.Success -> {
                                        // 成功なら投稿リストを返す
                                        result.posts
                                    }
                                    is MastodonPostResult.TokenInvalid -> {
                                        // トークン無効なら、SessionManagerに通知し、空のリストを返す
                                        sessionManager.markAccountForReauthentication(account.userId)
                                        emptyList<Post>()
                                    }
                                    is MastodonPostResult.Error -> {
                                        // その他のエラーも、今回は空のリストを返す
                                        println("Mastodon API Error: ${result.message}")
                                        emptyList<Post>()
                                    }
                                }
                            }
                        }

                    }
                }

                val allPosts = postLists.awaitAll().flatten()

                postDao.insertAll(allPosts)

                val postsFromDb = postDao.getAllPosts().first()
                val sortedPosts = postsFromDb.sortedByDescending { it.createdAt } // 新しい順
                val dayLogs = groupPostsByDay(sortedPosts)

                // 手順4: UIの状態を更新
                _uiState.update { currentState ->
                    currentState.copy(
                        dayLogs = dayLogs,
                        allPosts = sortedPosts, // sortedPostsもDB由来のものに
                        isLoading = false,
                        isRefreshing = false
                    )
                }

            } catch (e: Exception) {
                println("Error fetching posts: ${e.message}")
                // ★ MODIFIED: エラー時も両方の状態を解除
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
        val groupedByDate: Map<LocalDate, List<Post>> = posts.groupBy { post ->
            post.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }
        return groupedByDate.map { (date, postList) ->
            DayLog(
                date = date,
                firstPost = postList.firstOrNull(),
                totalPosts = postList.size
            )
        }.sortedBy { it.date }
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