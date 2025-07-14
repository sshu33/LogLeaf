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

    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private val _showSettingsBadge = MutableStateFlow(false)
    val showSettingsBadge = _showSettingsBadge.asStateFlow()

    init {
        loadInitialData()

        viewModelScope.launch {
            sessionManager.accountsFlow.drop(1).collect { accounts ->
                println("【MainViewModel】アカウント変更を検知。自動更新します。")
                _showSettingsBadge.value = accounts.any { it.needsReauthentication }
                // ★ 変更点: fetchPostsに渡すアカウントリストはフィルタリングしない（DBには全件保存したいため）
                fetchPosts(accounts, isInitialLoad = false)
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val initialAccounts = sessionManager.accountsFlow.first()
            _showSettingsBadge.value = initialAccounts.any { it.needsReauthentication }
            println("【MainViewModel】初回データロードを開始します。")
            fetchPosts(initialAccounts, isInitialLoad = true)
        }
    }

    private fun fetchPosts(accounts: List<Account>, isInitialLoad: Boolean) {
        viewModelScope.launch {
            if (isInitialLoad) {
                _uiState.update { it.copy(isLoading = true) }
            }

            // ★★★ ここが最重要の変更点 (1/2) ★★★
            // APIからは全アカウントのデータを取得する（非表示でも裏では最新データを保つため）
            // ただし、APIにリクエストを送るのは「再認証不要」なアカウントのみにするのが安全
            val accountsToFetch = accounts.filter { !it.needsReauthentication }

            try {
                // APIからのデータ取得とDBへの保存は、表示状態に関わらず行う
                val postLists = accountsToFetch.map { account ->
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
                val allNewPosts = postLists.awaitAll().flatten()
                if (allNewPosts.isNotEmpty()) {
                    postDao.insertAll(allNewPosts)
                }

                // ★★★ ここが最重要の変更点 (2/2) ★★★
                // 1. 表示を許可されたアカウントのIDリストを作成する
                val visibleAccountIds = accounts.filter { it.isVisible }.map { it.userId }

                // 2. そのIDリストを使って、DBから投稿を取得する
                val postsFromDb = postDao.getAllPosts(visibleAccountIds).first() // ◀️ 改造したDAOの関数を正しく使う！

                val dayLogs = groupPostsByDay(postsFromDb)
                _uiState.update { currentState ->
                    currentState.copy(
                        dayLogs = dayLogs,
                        allPosts = postsFromDb,
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
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // ★ SessionManagerから最新のアカウントリストを取得して渡す
            fetchPosts(sessionManager.accountsFlow.first(), isInitialLoad = false)
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
            val sortedPostList = postList.sortedBy { it.createdAt }
            DayLog(
                date = date,
                firstPost = sortedPostList.firstOrNull(),
                totalPosts = postList.size
            )
        }.sortedByDescending { it.date }
    }

    companion object {
        fun provideFactory(
            blueskyApi: BlueskyApi,
            mastodonApi: MastodonApi,
            sessionManager: SessionManager,
            postDao: PostDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(blueskyApi, mastodonApi, sessionManager, postDao) as T
            }
        }
    }
}