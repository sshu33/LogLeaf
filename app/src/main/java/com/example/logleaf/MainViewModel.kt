package com.example.logleaf

import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.db.PostDao
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

data class DayLog(
    val date: LocalDate,
    val firstPost: Post?,
    val totalPosts: Int
)

data class UiState(
    val dayLogs: List<DayLog> = emptyList(),
    val allPosts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isPostEntrySheetVisible: Boolean = false,
    val postText: TextFieldValue = TextFieldValue(""),
    val showHiddenPosts: Boolean = false,
    val editingPost: Post? = null,
    val editingDateTime: ZonedDateTime = ZonedDateTime.now(),
    val selectedImageUri: Uri? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val blueskyApi: BlueskyApi,
    private val mastodonApi: MastodonApi,
    private val sessionManager: SessionManager,
    private val postDao: PostDao
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    private val _postEntryState = MutableStateFlow(
        PostEntryState(
            isVisible = false,
            text = TextFieldValue(""),
            editingPost = null,
            dateTime = ZonedDateTime.now(),
            originalDateTime = ZonedDateTime.now(),
            selectedImageUri = null // ◀◀◀ 初期値はnull
        )
    )
    // 非表示投稿を表示するかの状態
    private val _showHiddenPosts = MutableStateFlow(false)

    // ▼▼▼ [変更点2] リアクティブなデータフローを構築 ▼▼▼
    // データベースから取得した、常に最新の投稿リスト
    private val allPostsFlow: Flow<List<Post>> =
        combine(sessionManager.accountsFlow, _showHiddenPosts) { accounts, showHidden ->
            val visibleAccountIds = accounts.filter { it.isVisible }.map { it.userId } + "LOGLEAF_INTERNAL_POST"
            val includeHidden = if (showHidden) 1 else 0
            Pair(visibleAccountIds, includeHidden)
        }.flatMapLatest { (accountIds, includeHiddenFlag) ->
            postDao.getAllPosts(accountIds, includeHiddenFlag)
        }
            .map { posts ->
                // DAOからのリストを、ここで強制的にソートする
                posts.sortedByDescending { it.createdAt }
            }

    val uiState: StateFlow<UiState> = combine(
        allPostsFlow,
        _postEntryState,
        _showHiddenPosts,
        _isRefreshing
    ) { posts, postEntry, showHidden, isRefreshing ->
        val dayLogs = groupPostsByDay(posts)
        UiState(
            dayLogs = dayLogs,
            allPosts = posts,
            isLoading = false,
            isRefreshing = isRefreshing, // ← [変更点3] ここでUIに状態を渡す
            isPostEntrySheetVisible = postEntry.isVisible,
            postText = postEntry.text,
            editingPost = postEntry.editingPost,
            editingDateTime = postEntry.dateTime,
            showHiddenPosts = showHidden,
            selectedImageUri = postEntry.selectedImageUri
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState(isLoading = true)
    )

    // --- 設定バッジのロジックは変更なし ---
    private val _showSettingsBadge = MutableStateFlow(false)
    val showSettingsBadge = _showSettingsBadge.asStateFlow()

    init {
        // 初回ロード処理
        loadInitialData()
        // アカウント変更の監視
        viewModelScope.launch {
            sessionManager.accountsFlow.collect { accounts ->
                _showSettingsBadge.value = accounts.any { it.needsReauthentication }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            fetchPosts(sessionManager.accountsFlow.first()).join()
        }
    }

    // ▼▼▼ [変更点4] fetchPostsはAPIからのデータ取得とDBへの保存だけに専念 ▼▼▼
    private fun fetchPosts(accounts: List<Account>): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val accountsToFetch = accounts.filter { !it.needsReauthentication }
            try {
                val postLists = accountsToFetch.map { account ->
                    async {
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
            } catch (e: Exception) {
                println("Error fetching posts: ${e.message}")
            }
        }
    }

    fun refreshPosts() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch // すでに更新中なら何もしない

            _isRefreshing.value = true // 更新開始を通知
            try {
                fetchPosts(sessionManager.accountsFlow.first()).join() // 終わるまで待つ
            } finally {
                _isRefreshing.value = false // 必ず更新終了を通知
            }
        }
    }

    // ▼▼▼ [変更点5] UI操作の関数は、トリガーの状態を更新するだけ ▼▼▼
    fun toggleShowHiddenPosts() {
        _showHiddenPosts.value = !_showHiddenPosts.value
    }

    fun setPostHidden(postId: String, isHidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            postDao.setPostHiddenStatus(postId, isHidden)
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            postDao.deletePostById(postId)
        }
    }

    // --- 投稿ダイアログ関連の処理 ---
    data class PostEntryState(
        val isVisible: Boolean,
        val text: TextFieldValue,
        val editingPost: Post?,
        val dateTime: ZonedDateTime,
        val originalDateTime: ZonedDateTime,
        val selectedImageUri: Uri?
    )

    fun showPostEntrySheet() {
        _postEntryState.update { currentState ->
            // もし、直前の状態が「既存投稿の編集中」だった場合
            if (currentState.editingPost != null) {
                // 下書きを完全にリセットして、まっさらな新規投稿画面を開く
                val now = ZonedDateTime.now()
                currentState.copy(
                    isVisible = true,
                    text = TextFieldValue(""),
                    editingPost = null,
                    dateTime = now,
                    originalDateTime = now
                )
            } else {
                // 直前の状態が「新規投稿」のままなら、書きかけの内容を維持して表示する
                currentState.copy(isVisible = true)
            }
        }
    }

    fun dismissPostEntrySheet() {
        val currentState = _postEntryState.value
        // もし、現在の状態が「既存投稿の編集中」だった場合
        if (currentState.editingPost != null) {
            // 編集内容を完全に破棄し、まっさらな状態に戻す
            val now = ZonedDateTime.now()
            _postEntryState.value = PostEntryState(
                isVisible = false,
                text = TextFieldValue(""),
                editingPost = null,
                dateTime = now,
                originalDateTime = now,
                selectedImageUri = null // ◀◀◀ ここも null でOK
            )
        } else {
            // 現在の状態が「新規投稿」なら、下書きを保持したまま非表示にするだけ
            _postEntryState.update { it.copy(isVisible = false) }
        }
    }

    fun onPostTextChange(newText: TextFieldValue) {
        _postEntryState.update { it.copy(text = newText) }
    }

    fun onDateTimeChange(newDateTime: ZonedDateTime) {
        _postEntryState.update { it.copy(dateTime = newDateTime) }
    }

    fun startEditingPost(post: Post) {
        _postEntryState.value = PostEntryState(
            isVisible = true,
            text = TextFieldValue(post.text),
            editingPost = post,
            dateTime = post.createdAt,
            originalDateTime = post.createdAt,
            selectedImageUri = post.imageUrl?.let { Uri.parse(it) }
        )
    }

    fun onImageSelected(uri: Uri?) {
        _postEntryState.update { it.copy(selectedImageUri = uri) }
    }

    fun submitPost() {
        val currentState = _postEntryState.value
        val currentText = currentState.text.text
        if (currentText.isBlank()) return

        val postToSave = currentState.editingPost?.copy(
            text = currentText,
            createdAt = currentState.dateTime
        ) ?: Post(
            id = UUID.randomUUID().toString(),
            accountId = "LOGLEAF_INTERNAL_POST",
            text = currentText,
            createdAt = currentState.dateTime,
            source = SnsType.LOGLEAF,
            imageUrl = null, // ◀◀◀ この行を追加
            isHidden = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            postDao.insert(postToSave) // insertはUpsert(挿入or更新)として機能する
        }

        dismissPostEntrySheet()
    }

    fun revertDateTime() {
        _postEntryState.update { it.copy(dateTime = it.originalDateTime) }
    }

    private fun groupPostsByDay(posts: List<Post>): List<DayLog> {
        if (posts.isEmpty()) {
            return emptyList()
        }
        val groupedByDate: Map<LocalDate, List<Post>> = posts.groupBy { post ->
            post.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }
        return groupedByDate.map { (date, postList) ->
            // ここでのソートは不要になるが、念のため残しておく
            val sortedPostList = postList.sortedByDescending { it.createdAt }
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