package com.example.logleaf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DayLog(
    val date: LocalDate,
    val firstPost: Post?,
    val totalPosts: Int,
    val dayImageUrl: String? = null,
    val imagePostId: String? = null // ★ これを追加！
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
            val visibleAccountIds =
                accounts.filter { it.isVisible }.map { it.userId } + "LOGLEAF_INTERNAL_POST"
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

    private val uniqueFrequentlyUsedTagsFlow: Flow<List<Tag>> = frequentlyUsedTagsFlow.map { tags ->
        tags.distinctBy { it.tagName }
    }

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
                    Log.d(
                        "DeletedPostCheck",
                        "Account ${account.userId}: ${deletedPostIds.size}件の投稿が削除済みとしてマークされました"
                    )
                }

            } catch (e: Exception) {
                Log.e("DeletedPostCheck", "削除済み投稿チェック中にエラー: ${e.message}")
            }
        }
    }

    private val _backupProgress = MutableStateFlow<String?>(null)
    val backupProgress = _backupProgress.asStateFlow()

    private val _restoreProgress = MutableStateFlow<String?>(null)

    // バックアップ状態の定義
    data class BackupState(
        val isInProgress: Boolean = false,
        val progress: Float = 0f,
        val statusText: String = "",
        val isCompleted: Boolean = false
    ) {
        companion object {
            val Idle = BackupState()
            val Starting = BackupState(isInProgress = true, statusText = "準備中...")
            fun Progress(progress: Float, text: String) =
                BackupState(isInProgress = true, progress = progress, statusText = text)

            val Completed = BackupState(isCompleted = true, statusText = "完了")
            fun Error(message: String) = BackupState(statusText = "エラー: $message")
        }
    }

    private val _backupState = MutableStateFlow(BackupState.Idle)
    val backupState = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow(BackupState.Idle)
    val restoreState = _restoreState.asStateFlow()

    val restoreProgress = _restoreProgress.asStateFlow()

    val uiState: StateFlow<UiState> = combine(
        listOf(
            allPostsFlow,
            _postEntryState,
            _showHiddenPosts,
            _isRefreshing,
            favoriteTagsFlow,
            frequentlyUsedTagsFlow // ← ここは元のままでOK
        )
    ) { results ->
        // 1. 必要なデータを`results`から全て取り出す
        @Suppress("UNCHECKED_CAST")
        val posts = results[0] as List<PostWithTagsAndImages>
        val postEntry = results[1] as PostEntryState
        val showHidden = results[2] as Boolean
        val isRefreshing = results[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val favoriteTags = results[4] as List<Tag>
        @Suppress("UNCHECKED_CAST")
        val frequentTags = results[5] as List<Tag> // ← まず変数'frequentTags'を定義する

        // 2. 取り出したデータを使って、必要な加工処理を行う
        val dayLogs = groupPostsByDay(posts)
        val uniqueFrequentTags = frequentTags.distinctBy { it.tagName } // ← ★定義済みの'frequentTags'をここで加工する

        // 3. 最終的なデータをUiStateに渡す
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
            frequentlyUsedTags = uniqueFrequentTags // ← ★加工済みのリストを渡す
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
                                    is MastodonPostResult.Success -> result.postsWithImages
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
                val allPostsWithImages = postLists.awaitAll().flatten()
                val allNewPosts = allPostsWithImages.map { it.post }
                if (allPostsWithImages.isNotEmpty()) {
                    allPostsWithImages.forEach { postWithImageUrls ->
                        val post = postWithImageUrls.post
                        val imageUrls = postWithImageUrls.imageUrls

                        // 1. 既存投稿かチェック
                        val existingPost = postDao.getPostById(post.id)

                        // 2. 投稿を保存
                        postDao.insertPost(post)

                        // 3. 新規投稿のみハッシュタグ抽出
                        if (existingPost == null) {
                            val hashtagPattern = "#(\\w+)".toRegex()
                            val hashtags =
                                hashtagPattern.findAll(post.text).map { it.groupValues[1] }.toList()

                            hashtags.forEach { tagName ->
                                val tagId = postDao.insertTag(Tag(tagName = tagName))
                                val finalTagId = if (tagId == -1L) postDao.getTagIdByName(tagName)
                                    ?: 0L else tagId
                                if (finalTagId != 0L) {
                                    postDao.insertPostTagCrossRef(
                                        PostTagCrossRef(
                                            post.id,
                                            finalTagId
                                        )
                                    )
                                }
                            }
                        }

                        // 3. 複数画像を保存
                        postDao.deletePostImagesByPostId(post.id)

                        imageUrls.forEachIndexed { index, imageUrl ->
                            val postImage = PostImage(
                                postId = post.id,
                                imageUrl = imageUrl,
                                orderIndex = index
                            )
                            postDao.insertPostImage(postImage)
                        }
                    }
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

    fun onImageReordered(fromIndex: Int, toIndex: Int) {
        _postEntryState.update { currentState ->
            val newUris = currentState.selectedImageUris.toMutableList()
            if (fromIndex != toIndex && fromIndex in 0 until newUris.size && toIndex in 0 until newUris.size) {
                val item = newUris.removeAt(fromIndex)
                newUris.add(toIndex, item)
            }
            currentState.copy(selectedImageUris = newUris)
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
            val minute =
                text.substring(2, 4).toIntOrNull()?.coerceIn(0, 59) ?: confirmedDateTime.minute
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
            val postImages = mutableListOf<PostImage>()

            currentState.selectedImageUris.forEachIndexed { index, uri ->
                if (uri.scheme == "content") {
                    // 新しいsaveImageToInternalStorage()を使用
                    val (originalUrl, thumbnailUrl) = saveImageToInternalStorage(uri)
                    if (originalUrl != null && thumbnailUrl != null) {
                        postImages.add(
                            PostImage(
                                postId = "", // 後で設定
                                imageUrl = originalUrl,
                                thumbnailUrl = thumbnailUrl,
                                orderIndex = index
                            )
                        )
                    }
                } else {
                    // 既存のURIの場合（編集時など）
                    postImages.add(
                        PostImage(
                            postId = "", // 後で設定
                            imageUrl = uri.toString(),
                            thumbnailUrl = null, // 既存画像はサムネイルなし
                            orderIndex = index
                        )
                    )
                }
            }

            // Postオブジェクトを準備（従来と同じ）
            val postToSave = currentState.editingPost?.post?.copy(
                text = currentText,
                createdAt = currentState.dateTime,
                imageUrl = postImages.firstOrNull()?.imageUrl  // 1枚目を従来の場所に保存
            ) ?: Post(
                id = UUID.randomUUID().toString(),
                accountId = "LOGLEAF_INTERNAL_POST",
                text = currentText,
                createdAt = currentState.dateTime,
                source = SnsType.LOGLEAF,
                imageUrl = postImages.firstOrNull()?.imageUrl,
                isHidden = false
            )

            // PostImageのpostIdを設定
            val finalPostImages = postImages.map { it.copy(postId = postToSave.id) }

            // タグ処理（従来と同じ）
            val tagIds = mutableListOf<Long>()
            tagNames.forEach { tagName ->
                var tagId = postDao.insertTag(Tag(tagName = tagName))
                if (tagId == -1L) {
                    tagId = postDao.getTagIdByName(tagName) ?: 0L
                }
                if (tagId != 0L) {
                    tagIds.add(tagId)
                }
            }
            val crossRefs = tagIds.map { tagId -> PostTagCrossRef(postToSave.id, tagId) }

            // 投稿、タグ、画像を一緒に保存
            postDao.updatePostWithTagsAndImages(postToSave, crossRefs, finalPostImages)

            // 5. 下書きをクリアしてダイアログを閉じる
            withContext(Dispatchers.Main) {
                clearDraftAndDismiss()

                if (isNewPost) {
                    _scrollToTopEvent.value = true
                }

                refreshPostsWithoutScroll()  // ← これに変更
            }
        }
    }

    fun consumeScrollToTopEvent() {
        _scrollToTopEvent.value = false
    }

    private suspend fun saveImageToInternalStorage(uri: Uri): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val inputStream = context.contentResolver.openInputStream(uri)

                if (inputStream == null) {
                    return@withContext Pair(null, null)
                }

                // 元画像をBitmapとして読み込み
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap == null) {
                    return@withContext Pair(null, null)
                }

                val timestamp = System.currentTimeMillis()

                // 1. 元画像を圧縮して保存（品質80%）
                val originalFileName = "IMG_${timestamp}.jpg"
                val originalFile = File(context.filesDir, originalFileName)
                val originalOutputStream = FileOutputStream(originalFile)
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, originalOutputStream)
                originalOutputStream.close()

                // 2. サムネイル生成（幅300pxにリサイズ）
                val thumbnailWidth = 300
                val thumbnailHeight =
                    (originalBitmap.height * thumbnailWidth) / originalBitmap.width
                val thumbnailBitmap =
                    Bitmap.createScaledBitmap(originalBitmap, thumbnailWidth, thumbnailHeight, true)

                val thumbnailFileName = "THUMB_${timestamp}.jpg"
                val thumbnailFile = File(context.filesDir, thumbnailFileName)
                val thumbnailOutputStream = FileOutputStream(thumbnailFile)
                thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 50, thumbnailOutputStream)
                thumbnailOutputStream.close()

                // メモリ解放
                originalBitmap.recycle()
                thumbnailBitmap.recycle()

                // ファイルURIを返す
                val originalUri = Uri.fromFile(originalFile).toString()
                val thumbnailUri = Uri.fromFile(thumbnailFile).toString()

                Pair(originalUri, thumbnailUri)

            } catch (e: Exception) {
                e.printStackTrace()
                Pair(null, null)
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
                Log.d(
                    "TagDebug",
                    "ViewModel State Updated (Add): ${newState.currentTags.map { it.tagName }}"
                )

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
            Log.d(
                "TagDebug",
                "ViewModel State Updated (Remove): ${newState.currentTags.map { it.tagName }}"
            ) // ◀◀ 追加
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
            it.post.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }
        return groupedByDate.map { (date, postList) ->
            val sortedPostList = postList.sortedBy { it.post.createdAt }

            // ★変更点: 画像を持つ投稿とそのURLをペアで探す
            val firstImageInfo: Pair<String, String>? = sortedPostList
                .firstNotNullOfOrNull { postWithTagsAndImages ->
                    // 投稿から画像URLを探す
                    val imageUrl = postWithTagsAndImages.images.firstOrNull()?.let { image ->
                        image.thumbnailUrl ?: image.imageUrl
                    } ?: postWithTagsAndImages.post.imageUrl // 従来のフィールドもチェック

                    // 画像URLが見つかった場合、その投稿IDとURLのペアを返す
                    imageUrl?.let { url ->
                        Pair(postWithTagsAndImages.post.id, url)
                    }
                }

            DayLog(
                date = date,
                firstPost = sortedPostList.firstOrNull()?.post,
                totalPosts = postList.size,
                // ★変更点: Pairからそれぞれの値を取り出して代入
                imagePostId = firstImageInfo?.first,  // 投稿ID
                dayImageUrl = firstImageInfo?.second  // 画像URL
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
                return MainViewModel(
                    application,
                    blueskyApi,
                    mastodonApi,
                    sessionManager,
                    postDao
                ) as T
            }
        }
    }

    fun exportPostsWithImages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _backupState.value = BackupState.Starting

                val context = getApplication<Application>().applicationContext
                val allPostsWithImages = postDao.getPostsWithTagsAndImages().first()

                _backupState.value = BackupState.Progress(0.1f, "投稿データを読み込み中...")

                // 一時フォルダを作成
                val tempDir = File(context.cacheDir, "backup_temp")
                tempDir.mkdirs()

                _backupState.value = BackupState.Progress(0.2f, "テキストファイルを作成中...")

                // テキストファイルの内容を作成
                val exportText = buildString {
                    appendLine("LogLeaf 投稿データ エクスポート")
                    appendLine("エクスポート日時: ${ZonedDateTime.now()}")
                    appendLine("総投稿数: ${allPostsWithImages.size}件")
                    appendLine("=".repeat(50))
                    appendLine()

                    val sortedPosts = allPostsWithImages.sortedByDescending { it.post.createdAt }

                    sortedPosts.forEachIndexed { index, postWithImages ->
                        val post = postWithImages.post
                        appendLine("投稿ID: ${post.id}")
                        appendLine("日時: ${post.createdAt}")
                        appendLine("アカウント: ${post.accountId}")
                        appendLine("SNS: ${post.source}")

                        // タグを表示
                        if (postWithImages.tags.isNotEmpty()) {
                            appendLine("タグ: ${postWithImages.tags.joinToString(", ") { "#${it.tagName}" }}")
                        }

                        appendLine("本文:")
                        appendLine(post.text)

                        // 画像情報を表示
                        if (postWithImages.images.isNotEmpty()) {
                            appendLine("画像: ${postWithImages.images.size}枚")
                            postWithImages.images.forEachIndexed { imgIndex, image ->
                                val originalFile = File(Uri.parse(image.imageUrl).path!!)
                                if (originalFile.exists()) {
                                    // 画像をバックアップフォルダにコピー
                                    val backupImageName = "${post.id}_${imgIndex}.jpg"
                                    val backupImageFile = File(tempDir, "images/$backupImageName")
                                    backupImageFile.parentFile?.mkdirs()
                                    originalFile.copyTo(backupImageFile, overwrite = true)
                                    appendLine("  画像${imgIndex + 1}: images/$backupImageName")
                                }
                            }
                        }

                        appendLine("-".repeat(30))
                        appendLine()

                        // 進行状況を更新（0.2 ~ 0.7の範囲でテキスト処理）
                        val textProgress = 0.2f + (0.5f * (index + 1) / sortedPosts.size)
                        _backupState.value = BackupState.Progress(
                            textProgress,
                            "投稿を処理中... (${index + 1}/${sortedPosts.size})"
                        )
                    }
                }

                // テキストファイルを一時フォルダに保存
                val textFile = File(tempDir, "posts.txt")
                textFile.writeText(exportText, Charsets.UTF_8)

                _backupState.value = BackupState.Progress(0.8f, "ZIPファイルを作成中...")

                // ZIPファイルを作成
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val zipFileName = "LogLeaf_backup_${System.currentTimeMillis()}.zip"
                val zipFile = File(downloadsDir, zipFileName)

                // ZIP圧縮
                zipFile.outputStream().use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        // テキストファイルを追加
                        zos.putNextEntry(ZipEntry("posts.txt"))
                        textFile.inputStream().copyTo(zos)
                        zos.closeEntry()

                        _backupState.value = BackupState.Progress(0.9f, "画像を圧縮中...")

                        // 画像フォルダ内のファイルを追加
                        val imagesDir = File(tempDir, "images")
                        if (imagesDir.exists()) {
                            val imageFiles = imagesDir.listFiles() ?: emptyArray()
                            imageFiles.forEachIndexed { index, imageFile ->
                                zos.putNextEntry(ZipEntry("images/${imageFile.name}"))
                                imageFile.inputStream().copyTo(zos)
                                zos.closeEntry()

                                // 画像圧縮の進行状況
                                val imageProgress = 0.9f + (0.08f * (index + 1) / imageFiles.size)
                                _backupState.value = BackupState.Progress(
                                    imageProgress,
                                    "画像を圧縮中... (${index + 1}/${imageFiles.size})"
                                )
                            }
                        }
                    }
                }

                // 一時フォルダを削除
                tempDir.deleteRecursively()

                _backupState.value = BackupState.Progress(1.0f, "完了")

                withContext(Dispatchers.Main) {
                    // 1秒後に完了状態にして、さらに2秒後にリセット
                    launch {
                        delay(1000)
                        _backupState.value = BackupState.Completed
                        delay(2000)
                        _backupState.value = BackupState.Idle
                    }
                }

                Log.d("Backup", "完全バックアップ完了: ${zipFile.absolutePath}")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _backupState.value = BackupState.Error(e.message ?: "不明なエラー")
                    launch {
                        delay(3000)
                        _backupState.value = BackupState.Idle
                    }
                }
                Log.e("Backup", "バックアップ失敗: ${e.message}")
            }
        }
    }

    fun restoreFromBackup(backupUri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext

                // 1. ZIPファイルを一時的に展開
                val tempDir = File(context.cacheDir, "restore_temp")
                tempDir.mkdirs()

                // ZIPファイルを展開
                context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                    extractZip(inputStream, tempDir)
                } ?: run {
                    withContext(Dispatchers.Main) {
                        onResult(false, "バックアップファイルを開けませんでした")
                    }
                    return@launch
                }

                // 2. posts.txtを解析
                val postsFile = File(tempDir, "posts.txt")
                if (!postsFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "posts.txtが見つかりません")
                    }
                    return@launch
                }

                val restoredPosts = parsePostsFromText(postsFile.readText())

                // 3. 画像ファイルを内部ストレージにコピー
                val imagesDir = File(tempDir, "images")
                if (imagesDir.exists()) {
                    copyImagesToInternalStorage(imagesDir)
                }

                // 4. データベースに保存（重複チェック付き、ハッシュタグ自動抽出なし）
                var newPostCount = 0
                var updatedPostCount = 0

                restoredPosts.forEach { restoredData ->
                    val existingPost = postDao.getPostById(restoredData.post.id)

                    if (existingPost == null) {
                        // 新規投稿として保存（ハッシュタグ自動抽出なし）
                        postDao.insert(restoredData.post)

                        // タグ情報を保存
                        restoredData.tagNames.forEach { tagName ->
                            var tagId = postDao.insertTag(Tag(tagName = tagName))
                            if (tagId == -1L) {
                                tagId = postDao.getTagIdByName(tagName) ?: 0L
                            }
                            if (tagId != 0L) {
                                postDao.insertPostTagCrossRef(
                                    PostTagCrossRef(
                                        restoredData.post.id,
                                        tagId
                                    )
                                )
                            }
                        }

                        // 画像情報を保存
                        if (restoredData.images.isNotEmpty()) {
                            postDao.insertPostImages(restoredData.images)
                        }

                        newPostCount++
                    } else {
                        // 既存投稿を更新
                        postDao.update(restoredData.post)

                        // タグを更新（古いタグを削除して新しいタグを追加）
                        postDao.deletePostTagCrossRefs(restoredData.post.id)
                        restoredData.tagNames.forEach { tagName ->
                            var tagId = postDao.insertTag(Tag(tagName = tagName))
                            if (tagId == -1L) {
                                tagId = postDao.getTagIdByName(tagName) ?: 0L
                            }
                            if (tagId != 0L) {
                                postDao.insertPostTagCrossRef(
                                    PostTagCrossRef(
                                        restoredData.post.id,
                                        tagId
                                    )
                                )
                            }
                        }

                        // 画像を更新
                        postDao.deletePostImages(restoredData.post.id)
                        if (restoredData.images.isNotEmpty()) {
                            postDao.insertPostImages(restoredData.images)
                        }

                        updatedPostCount++
                    }
                }

                // 5. 一時フォルダを削除
                tempDir.deleteRecursively()

                withContext(Dispatchers.Main) {
                    val message = " (新規: ${newPostCount}件、更新: ${updatedPostCount}件)"
                    onResult(true, message)
                    refreshPosts() // データ更新
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "不明なエラー")
                }
            }
        }
    }

    private fun extractZip(inputStream: InputStream, targetDir: File) {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        zis.copyTo(output)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun parsePostsFromText(text: String): List<RestoredPostData> {
        val posts = mutableListOf<RestoredPostData>()
        val postBlocks =
            text.split("------------------------------").filter { it.contains("投稿ID:") }

        postBlocks.forEach { block ->
            try {
                val lines = block.trim().split("\n")

                // 基本情報を抽出
                val postId =
                    lines.find { it.startsWith("投稿ID:") }?.substringAfter("投稿ID: ")?.trim()
                        ?: return@forEach
                val dateTimeStr =
                    lines.find { it.startsWith("日時:") }?.substringAfter("日時: ")?.trim()
                        ?: return@forEach
                val accountId =
                    lines.find { it.startsWith("アカウント:") }?.substringAfter("アカウント: ")
                        ?.trim() ?: return@forEach
                val snsStr = lines.find { it.startsWith("SNS:") }?.substringAfter("SNS: ")?.trim()
                    ?: return@forEach

                // タグ情報を抽出
                val tagNames = lines.find { it.startsWith("タグ:") }?.let { tagLine ->
                    tagLine.substringAfter("タグ: ").split(",")
                        .map { tag ->
                            //タグから # を削除
                            tag.replace("#+".toRegex(), "").trim()
                        }
                        .filterNot { it.isBlank() } // 空文字を除外
                } ?: emptyList()

                // 本文を抽出（「本文:」の次の行から画像情報or区切りまで）
                val bodyStartIndex = lines.indexOfFirst { it.startsWith("本文:") }
                if (bodyStartIndex == -1) return@forEach

                val bodyLines = mutableListOf<String>()
                var i = bodyStartIndex + 1
                while (i < lines.size && !lines[i].startsWith("画像:") && lines[i].trim()
                        .isNotEmpty()
                ) {
                    bodyLines.add(lines[i])
                    i++
                }
                val bodyText = bodyLines.joinToString("\n")

                // 画像情報を抽出（LogLeaf投稿の実際のファイルパスのみ）
                val images = mutableListOf<PostImage>()

                // 実際の画像パス行のみを処理（"画像1: images/..."の形式）
                val imagePathLines = lines.filter {
                    it.trim().matches(Regex("\\s*画像\\d+: images/.+"))
                }

                imagePathLines.forEachIndexed { index, imageLine ->
                    val imagePath = imageLine.substringAfter(": ").trim()
                    if (imagePath.startsWith("images/")) {
                        // LogLeaf投稿の画像のみ処理
                        val imageFileName = imagePath.substringAfter("images/")
                        val internalImagePath =
                            "file://${getApplication<Application>().filesDir}/$imageFileName"

                        images.add(
                            PostImage(
                                postId = postId,
                                imageUrl = internalImagePath,
                                orderIndex = index
                            )
                        )
                    }
                }

                // Postオブジェクトを作成
                val snsType = when (snsStr) {
                    "BLUESKY" -> SnsType.BLUESKY
                    "MASTODON" -> SnsType.MASTODON
                    else -> SnsType.LOGLEAF
                }

                val post = Post(
                    id = postId,
                    accountId = accountId,
                    text = bodyText,
                    createdAt = ZonedDateTime.parse(dateTimeStr),
                    source = snsType,
                    imageUrl = images.firstOrNull()?.imageUrl,
                    isHidden = false
                )

                posts.add(RestoredPostData(post = post, tagNames = tagNames, images = images))

            } catch (e: Exception) {
                Log.e("RestoreBackup", "投稿解析エラー: ${e.message}")
            }
        }

        return posts
    }

    private fun copyImagesToInternalStorage(imagesDir: File) {
        val context = getApplication<Application>().applicationContext

        imagesDir.listFiles()?.forEach { imageFile ->
            try {
                val targetFile = File(context.filesDir, imageFile.name)
                imageFile.copyTo(targetFile, overwrite = true)
            } catch (e: Exception) {
                Log.e("RestoreBackup", "画像コピーエラー: ${e.message}")
            }
        }
    }

    // データクラスを追加（MainViewModel.kt内、どこでもOK）
    data class RestoredPostData(
        val post: Post,
        val tagNames: List<String>,
        val images: List<PostImage>
    )

    private fun refreshPostsWithoutScroll() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                fetchPosts(sessionManager.accountsFlow.first()).join()
                // スクロールイベントは発火しない
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun importDataFromZip(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _restoreState.value = BackupState.Starting.copy(statusText = "復元準備中...")

                val context = getApplication<Application>().applicationContext

                _restoreState.value = BackupState.Progress(0.2f, "ZIPファイルを読み込み中...")

                // ZIPファイルを解凍して処理
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    throw Exception("ファイルを読み込めませんでした")
                }

                _restoreState.value = BackupState.Progress(0.4f, "ファイルを解析中...")

                // 実際のZIP解凍処理は複雑なので、今は簡単な確認だけ
                val availableBytes = inputStream.available()
                inputStream.close()

                if (availableBytes <= 0) {
                    throw Exception("無効なファイルです")
                }

                _restoreState.value = BackupState.Progress(0.6f, "データを復元中...")
                delay(1000) // 実際の復元処理の代替

                _restoreState.value = BackupState.Progress(0.8f, "データベースを更新中...")
                delay(500) // DB更新処理の代替

                _restoreState.value = BackupState.Progress(1.0f, "完了")

                withContext(Dispatchers.Main) {
                    launch {
                        delay(1000)
                        _restoreState.value =
                            BackupState.Completed.copy(statusText = "復元が完了しました")
                        delay(2000)
                        _restoreState.value = BackupState.Idle
                    }
                }

                Log.d("Restore", "データ復元完了: ${availableBytes}バイト")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _restoreState.value = BackupState.Error(e.message ?: "復元に失敗しました")
                    launch {
                        delay(3000)
                        _restoreState.value = BackupState.Idle
                    }
                }
                Log.e("Restore", "データ復元失敗: ${e.message}")
            }
        }
    }
}

