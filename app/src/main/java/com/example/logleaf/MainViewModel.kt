package com.example.logleaf

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.db.PostDao
import com.example.logleaf.ui.entry.PostImage
import com.example.logleaf.ui.entry.PostTagCrossRef
import com.example.logleaf.ui.entry.Tag
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    val allPosts: List<PostWithTagsAndImages> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isPostEntrySheetVisible: Boolean = false,
    val postText: TextFieldValue = TextFieldValue(""),
    val showHiddenPosts: Boolean = false,
    val editingPost: Post? = null,
    val editingTags: List<Tag> = emptyList(),
    val editingDateTime: ZonedDateTime = ZonedDateTime.now(),
    val selectedImageUris: List<Uri> = emptyList(),
    val requestFocus: Boolean = false,
    val favoriteTags: List<Tag> = emptyList(),
    val frequentlyUsedTags: List<Tag> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: Application,
    private val blueskyApi: BlueskyApi,
    private val mastodonApi: MastodonApi,
    private val sessionManager: SessionManager,
    private val postDao: PostDao
) : AndroidViewModel(application) {

    private val _isRefreshing = MutableStateFlow(false)

    private val _postEntryState = MutableStateFlow(
        PostEntryState(
            isVisible = false,
            text = TextFieldValue(""),
            editingPost = null,
            dateTime = ZonedDateTime.now(),
            originalDateTime = ZonedDateTime.now(),
            selectedImageUris = emptyList(),
            currentTags = emptyList()
        )
    )
    // 非表示投稿を表示するかの状態
    private val _showHiddenPosts = MutableStateFlow(false)

    private val _scrollToTopEvent = MutableStateFlow<Boolean>(false)
    val scrollToTopEvent = _scrollToTopEvent.asStateFlow()

    // データベースから取得した、常に最新の投稿リスト
    private val allPostsFlow: Flow<List<PostWithTagsAndImages>> =
        combine(sessionManager.accountsFlow, _showHiddenPosts) { accounts, showHidden ->
            val visibleAccountIds = accounts.filter { it.isVisible }.map { it.userId } + "LOGLEAF_INTERNAL_POST"
            val includeHidden = if (showHidden) 1 else 0
            Pair(visibleAccountIds, includeHidden)
        }.flatMapLatest { (accountIds, includeHiddenFlag) ->
            postDao.getPostsWithTagsAndImages().map { postsWithTagsAndImages ->
                // フィルタリング処理を追加
                postsWithTagsAndImages.filter { pwtai ->
                    (pwtai.post.accountId in accountIds) &&
                            (!pwtai.post.isHidden || includeHiddenFlag == 1)
                }
            }
        }.map { posts ->
            posts.sortedByDescending { it.post.createdAt }
        }
    private val favoriteTagsFlow: Flow<List<Tag>> = postDao.getFavoriteTags()
    private val frequentlyUsedTagsFlow: Flow<List<Tag>> = postDao.getFrequentlyUsedTags()

    private suspend fun checkForDeletedPosts(accounts: List<Account>, fetchedPosts: List<Post>) {
        accounts.forEach { account ->
            try {
                // LogLeaf内の該当アカウントのアクティブな投稿IDを取得
                val localActivePostIds = postDao.getActivePostIdsForAccount(account.userId)

                if (localActivePostIds.isEmpty()) return@forEach

                // SNS側から取得した投稿のIDリスト
                val fetchedPostIds = fetchedPosts
                    .filter { it.accountId == account.userId }
                    .map { it.id }

                // LogLeaf内にあるがSNS側にない投稿 = 削除された投稿
                val deletedPostIds = localActivePostIds - fetchedPostIds.toSet()

                if (deletedPostIds.isNotEmpty()) {
                    // 削除された投稿を削除済みとしてマーク
                    postDao.markPostsAsDeletedFromSns(account.userId, fetchedPostIds)
                    Log.d("DeletedPostCheck", "Account ${account.userId}: ${deletedPostIds.size}件の投稿が削除済みとしてマークされました")
                }

            } catch (e: Exception) {
                Log.e("DeletedPostCheck", "削除済み投稿チェック中にエラー: ${e.message}")
            }
        }
    }

    val uiState: StateFlow<UiState> = combine(
        listOf(
            allPostsFlow,
            _postEntryState,
            _showHiddenPosts,
            _isRefreshing,
            favoriteTagsFlow,
            frequentlyUsedTagsFlow
        )
    ) { results ->
        @Suppress("UNCHECKED_CAST")
        val posts = results[0] as List<PostWithTagsAndImages>
        val postEntry = results[1] as PostEntryState
        val showHidden = results[2] as Boolean
        val isRefreshing = results[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val favoriteTags = results[4] as List<Tag>
        @Suppress("UNCHECKED_CAST")
        val frequentTags = results[5] as List<Tag>

        val dayLogs = groupPostsByDay(posts)
        UiState(
            dayLogs = dayLogs,
            allPosts = posts,
            isLoading = false,
            isRefreshing = isRefreshing,
            isPostEntrySheetVisible = postEntry.isVisible,
            postText = postEntry.text,
            editingPost = postEntry.editingPost?.post,
            editingTags = postEntry.currentTags,
            editingDateTime = postEntry.dateTime,
            showHiddenPosts = showHidden,
            selectedImageUris = postEntry.selectedImageUris,
            requestFocus = postEntry.requestFocus,
            favoriteTags = favoriteTags,
            frequentlyUsedTags = frequentTags
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
                    postDao.insertAllWithHashtagExtraction(allNewPosts)
                }

                checkForDeletedPosts(accountsToFetch, allNewPosts)

            } catch (e: Exception) {
                println("Error fetching posts: ${e.message}")
            }
        }
    }

    fun refreshPosts() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch

            _isRefreshing.value = true
            try {
                fetchPosts(sessionManager.accountsFlow.first()).join()
                // ★★★ 追加：リフレッシュ完了後にスクロールイベント発火 ★★★
                _scrollToTopEvent.value = true
            } finally {
                _isRefreshing.value = false
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
        val editingPost: PostWithTagsAndImages?, // ← PostWithTagsAndImagesに変更
        val dateTime: ZonedDateTime,
        val originalDateTime: ZonedDateTime,
        val selectedImageUris: List<Uri> = emptyList(),
        val currentTags: List<Tag>,
        val requestFocus: Boolean = false
    )

    fun showPostEntrySheet() {
        _postEntryState.update { currentState ->
            // ▼▼▼ [修正箇所] if文のロジックはそのままに、elseブロックを修正します ▼▼▼
            if (currentState.editingPost != null) {
                // 直前が「編集」なら、まっさらな新規投稿の状態を直接作成して返す
                val now = ZonedDateTime.now()
                PostEntryState(
                    isVisible = true, // isVisibleをtrueに
                    text = TextFieldValue(""),
                    editingPost = null,
                    dateTime = now,
                    originalDateTime = now,
                    selectedImageUris = emptyList(),
                    requestFocus = true,
                    currentTags = emptyList()
                )
            } else {
                // 直前が「新規」なら、下書きを維持しつつ、時刻だけを現在時刻にリフレッシュする
                val now = ZonedDateTime.now() // ★★★ 追加 ★★★
                currentState.copy(
                    isVisible = true,
                    dateTime = now, // ★★★ 追加 ★★★
                    originalDateTime = now, // ★★★ 追加 ★★★
                    requestFocus = true
                )
            }
        }
    }

    fun onCancel() {
        val currentState = _postEntryState.value
        if (currentState.editingPost != null) {
            // 編集モードの場合、問答無用で変更を破棄
            clearDraftAndDismiss()
        } else {
            // 新規モードの場合、下書きを保持したまま非表示にするだけ
            _postEntryState.update { it.copy(isVisible = false, requestFocus = false) }
        }
    }

    fun onPostTextChange(newText: TextFieldValue) {
        _postEntryState.update { it.copy(text = newText) }
    }

    fun onDateTimeChange(newDateTime: ZonedDateTime) {
        _postEntryState.update { it.copy(dateTime = newDateTime) }
    }

    fun startEditingPost(postWithTagsAndImages: PostWithTagsAndImages) {
        val post = postWithTagsAndImages.post
        _postEntryState.value = PostEntryState(
            isVisible = true,
            text = TextFieldValue(post.text),
            editingPost = postWithTagsAndImages, // これも対応済み
            dateTime = post.createdAt,
            originalDateTime = post.createdAt,
            selectedImageUris = postWithTagsAndImages.images.map { Uri.parse(it.imageUrl) }, // 複数画像対応！
            currentTags = postWithTagsAndImages.tags,
            requestFocus = true
        )
    }

    fun onImageSelected(uri: Uri?) {
        _postEntryState.update { currentState ->
            val newUris = if (uri != null) {
                currentState.selectedImageUris + uri // リストに追加
            } else {
                emptyList() // nullの場合は全クリア（後で個別削除機能も追加予定）
            }
            currentState.copy(
                selectedImageUris = newUris,
                requestFocus = uri != null
            )
        }
    }

    fun onMultipleImagesSelected(uris: List<Uri>) {
        _postEntryState.update { currentState ->
            currentState.copy(
                selectedImageUris = currentState.selectedImageUris + uris, // 既存の画像に追加
                requestFocus = uris.isNotEmpty()
            )
        }
    }

    fun onImageRemoved(index: Int) {
        _postEntryState.update { currentState ->
            val newUris = currentState.selectedImageUris.toMutableList()
            if (index in 0 until newUris.size) {
                newUris.removeAt(index)
            }
            currentState.copy(selectedImageUris = newUris)
        }
    }

    fun onImageFavorited(index: Int) {
        _postEntryState.update { currentState ->
            val uris = currentState.selectedImageUris.toMutableList()
            if (index in 0 until uris.size) {
                // 選択された画像を先頭に移動
                val favoriteUri = uris.removeAt(index)
                uris.add(0, favoriteUri)
            }
            currentState.copy(selectedImageUris = uris)
        }
    }

    fun createImageUri(): Uri {
        val context = getApplication<Application>().applicationContext
        // 保存先のファイルを作成（まだ中身は空）
        val imageFile = File(
            context.filesDir, // アプリの内部ストレージの files ディレクトリ
            "CAMERA_${System.currentTimeMillis()}.jpg"
        )
        // FileProviderを使って、そのファイルへの安全なURIを取得
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
    }

    fun submitPost(unconfirmedTimeText: String? = null, tagNames: List<String>) {
        var currentState = _postEntryState.value

        // 1. もし未確定の時刻テキストが渡されていたら、それをパースしてcurrentStateを更新する
        if (unconfirmedTimeText != null) {
            val confirmedDateTime = currentState.dateTime
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()

            val text = unconfirmedTimeText.padEnd(4, '0')
            val hour = text.substring(0, 2).toIntOrNull()?.coerceIn(0, 23) ?: confirmedDateTime.hour
            val minute = text.substring(2, 4).toIntOrNull()?.coerceIn(0, 59) ?: confirmedDateTime.minute
            val newDateTime = confirmedDateTime.withHour(hour).withMinute(minute)

            // パースした新しい時刻で、まず内部の状態を更新する
            _postEntryState.update { it.copy(dateTime = newDateTime.atZone(ZoneId.systemDefault())) }
            // 更新した最新の状態でcurrentStateを再取得する
            currentState = _postEntryState.value
        }

        // 2. 以降は、以前のsubmitPostと同じロジックを実行する
        val currentText = currentState.text.text
        val isNewPost = currentState.editingPost == null
        val isContentEmpty = currentText.isBlank() && currentState.selectedImageUris.isEmpty()

        if (isContentEmpty) {
            if (isNewPost) {
                // 【要件】新規モードで中身が空のまま投稿 → 下書きを破棄して閉じる
                clearDraftAndDismiss()
            }
            // 編集モードで中身を空にした場合、UIでボタンが押せないはずなので、基本的にこのルートは通らない。
            // 万が一通っても、何もせず終了する。
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val savedImageUrls = mutableListOf<String>()
            currentState.selectedImageUris.forEachIndexed { index, uri ->
                val savedUrl = if (uri.scheme == "content") {
                    saveImageToInternalStorage(uri)
                } else {
                    uri.toString()
                }
                if (savedUrl != null) {
                    savedImageUrls.add(savedUrl)
                }
            }

            // 2. 保存するPostオブジェクトを準備（この部分は変更なし）
            val postToSave = currentState.editingPost?.post?.copy(
                text = currentText,
                createdAt = currentState.dateTime,
                imageUrl = savedImageUrls.firstOrNull()  // とりあえず1枚目を従来の場所に保存
            ) ?: Post(
                id = UUID.randomUUID().toString(),
                accountId = "LOGLEAF_INTERNAL_POST",
                text = currentText,
                createdAt = currentState.dateTime,
                source = SnsType.LOGLEAF,
                imageUrl = savedImageUrls.firstOrNull(),
                isHidden = false
            )
            // 3. タグをDBに保存し、IDのリストを作成（重複ロジックを統合）
            val tagIds = mutableListOf<Long>()
            tagNames.forEach { tagName -> // ◀◀ currentState.currentTags の代わりに tagName を使用
                var tagId = postDao.insertTag(Tag(tagName = tagName))
                if (tagId == -1L) {
                    tagId = postDao.getTagIdByName(tagName) ?: 0L
                }
                if (tagId != 0L) {
                    tagIds.add(tagId)
                }
            }

            val crossRefs = tagIds.map { tagId -> PostTagCrossRef(postToSave.id, tagId) }
            // 複数画像のPostImageオブジェクトを作成
            val postImages = savedImageUrls.mapIndexed { index, imageUrl ->
                PostImage(
                    postId = postToSave.id,
                    imageUrl = imageUrl,
                    orderIndex = index
                )
            }

            // 投稿、タグ、画像を一緒に保存
            postDao.updatePostWithTagsAndImages(postToSave, crossRefs, postImages)

            // 5. 下書きをクリアしてダイアログを閉じる
            withContext(Dispatchers.Main) {
                clearDraftAndDismiss()

                if (isNewPost) {
                    _scrollToTopEvent.value = true
                }

                refreshPosts() // ←この行を追加
            }
        }
    }

    fun consumeScrollToTopEvent() {
        _scrollToTopEvent.value = false
    }

    private suspend fun saveImageToInternalStorage(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = "IMG_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                Uri.fromFile(file).toString() // 保存したファイルのURIを返す
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun clearDraftAndDismiss() {
        val now = ZonedDateTime.now()
        _postEntryState.value = PostEntryState(
            isVisible = false,
            text = TextFieldValue(""),
            editingPost = null,
            dateTime = now,
            originalDateTime = now,
            selectedImageUris = emptyList(),
            requestFocus = false,
            currentTags = emptyList()
        )
    }

    fun revertDateTime() {
        _postEntryState.update { currentState ->
            if (currentState.editingPost != null) {
                // 【編集モードの場合】元の投稿時刻に戻す
                currentState.copy(dateTime = currentState.originalDateTime)
            } else {
                // 【新規作成モードの場合】現在の時刻にリセットする
                currentState.copy(dateTime = ZonedDateTime.now())
            }
        }
    }
    fun consumeFocusRequest() {
        _postEntryState.update { it.copy(requestFocus = false) }
    }

    fun onAddTag(tagName: String) {
        val trimmed = tagName.trim()
        if (trimmed.isBlank()) return

        _postEntryState.update { currentState ->
            // すでに同じ名前のタグがなければ追加
            if (currentState.currentTags.none { it.tagName.equals(trimmed, ignoreCase = true) }) {
                // ▼▼▼ ここで newTag を定義します ▼▼▼
                val newTag = Tag(tagId = 0, tagName = trimmed)

                val newState = currentState.copy(currentTags = currentState.currentTags + newTag)

                // ログは状態更新後に記録
                Log.d("TagDebug", "ViewModel State Updated (Add): ${newState.currentTags.map { it.tagName }}")

                newState
            } else {
                currentState
            }
        }
    }

    fun onRemoveTag(tagToRemove: Tag) {
        _postEntryState.update { currentState ->
            val newState = currentState.copy(
                currentTags = currentState.currentTags.filter { it != tagToRemove }
            )
            Log.d("TagDebug", "ViewModel State Updated (Remove): ${newState.currentTags.map { it.tagName }}") // ◀◀ 追加
            newState // ◀◀ 更新した状態を返す
        }
    }

    fun toggleTagFavoriteStatus(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            // 現在のisFavoriteの状態を反転させて、DBを更新する
            postDao.setTagFavoriteStatus(tag.tagId, !tag.isFavorite)
        }
    }

    private fun groupPostsByDay(posts: List<PostWithTagsAndImages>): List<DayLog> {
        if (posts.isEmpty()) {
            return emptyList()
        }
        val groupedByDate: Map<LocalDate, List<PostWithTagsAndImages>> = posts.groupBy {
            it.post.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() // ◀◀ it.post を経由
        }
        return groupedByDate.map { (date, postList) ->
            // ここでのソートは不要になるが、念のため残しておく
            val sortedPostList = postList.sortedByDescending { it.post.createdAt } // ◀◀ 1. .postを追加
            DayLog(
                date = date,
                firstPost = sortedPostList.firstOrNull()?.post, // ◀◀ 2. .postを追加
                totalPosts = postList.size
            )
        }.sortedByDescending { it.date }
    }

    companion object {
        fun provideFactory(
            application: Application,
            blueskyApi: BlueskyApi,
            mastodonApi: MastodonApi,
            sessionManager: SessionManager,
            postDao: PostDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(application, blueskyApi, mastodonApi, sessionManager, postDao) as T
            }
        }
    }
}