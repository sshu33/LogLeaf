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
import com.example.logleaf.api.bluesky.BlueskyApi
import com.example.logleaf.api.fitbit.FitbitApi
import com.example.logleaf.api.github.GitHubApi
import com.example.logleaf.api.mastodon.MastodonApi
import com.example.logleaf.api.mastodon.MastodonPostResult
import com.example.logleaf.data.model.Account
import com.example.logleaf.data.model.Post
import com.example.logleaf.data.model.PostWithTagsAndImages
import com.example.logleaf.data.model.UiPost
import com.example.logleaf.data.session.FitbitHistoryManager
import com.example.logleaf.data.session.SessionManager
import com.example.logleaf.data.settings.TimeSettingsRepository
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
import kotlinx.coroutines.delay
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
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
) {
    val displayPosts: List<UiPost> by lazy {
        allPosts.map { UiPost(it) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: Application,
    private val blueskyApi: BlueskyApi,
    private val mastodonApi: MastodonApi,
    private val gitHubApi: GitHubApi, // ← 追加
    private val fitbitApi: FitbitApi,
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

    private var lastFitbitRefresh = 0L

    // 非表示投稿を表示するかの状態
    private val _showHiddenPosts = MutableStateFlow(false)

    private val _scrollToTopEvent = MutableStateFlow<Boolean>(false)
    val scrollToTopEvent = _scrollToTopEvent.asStateFlow()

    private val _fitbitSyncProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val fitbitSyncProgress = _fitbitSyncProgress.asStateFlow()

    // プログレス状態の定義
    sealed class SyncState {
        object Idle : SyncState()
        data class Progress(val current: Int, val total: Int) : SyncState()
        object ErrorFlashing : SyncState()
    }

    // 統合プログレス管理
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun setSyncProgress(current: Int, total: Int) {
        _syncState.value = SyncState.Progress(current, total)
    }

    fun setSyncError() {
        _syncState.value = SyncState.ErrorFlashing
    }

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
                    pwtai.post.accountId in accountIds &&
                            (!pwtai.post.isHidden || includeHiddenFlag == 1)
                }
            }
        }.map { posts ->
            posts.sortedByDescending { it.post.createdAt }
        }
    private val favoriteTagsFlow: Flow<List<Tag>> = postDao.getFavoriteTags()
    private val frequentlyUsedTagsFlow: Flow<List<Tag>> = combine(
        postDao.getFrequentlyUsedTags(),
        postDao.getTemporaryShownTags()
    ) { frequentlyUsed, temporaryShown ->
        frequentlyUsed + temporaryShown  // よく使うタグ + 一時表示タグ
    }

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

    private val timeSettingsRepository = TimeSettingsRepository(getApplication())
    val timeSettings = timeSettingsRepository.timeSettings

    private val _dataSize = MutableStateFlow<String>("計算中...")
    val dataSize = _dataSize.asStateFlow()

    private val _backupState = MutableStateFlow(BackupState.Idle)
    val backupState = _backupState.asStateFlow()

    // Zeppインポート用の状態管理
    private val _zeppImportState = MutableStateFlow(BackupState.Idle)
    val zeppImportState = _zeppImportState.asStateFlow()

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
        val uniqueFrequentTags =
            frequentTags.distinctBy { it.tagName } // ← ★定義済みの'frequentTags'をここで加工する

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
        // BlueskyApiにMainViewModelの参照を設定
        blueskyApi.setMainViewModel(this)

        // MastodonApiにも追加
        mastodonApi.setMainViewModel(this)

        // GitHubApiにも追加
        gitHubApi.setMainViewModel(this)
    }
    private fun loadInitialData() {
        viewModelScope.launch {
            fetchPosts(sessionManager.accountsFlow.first()).join()
        }
    }


    private fun fetchPosts(accounts: List<Account>): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val accountsToFetch = accounts.filter { !it.needsReauthentication }
            try {

                val hasFitbit = accountsToFetch.any { it is Account.Fitbit }
                if (hasFitbit) {
                    Log.d("Fitbit", "Fitbitアカウントを検出、データ同期開始")
                    val fitbitAccount = accountsToFetch.find { it is Account.Fitbit } as Account.Fitbit
                    fetchFitbitHistoryData(fitbitAccount.userId) {} // 過去データ（差分取得）
                    syncFitbitData() // 今日のデータ（2回のAPI）
                }

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
                            is Account.GitHub -> gitHubApi.getPostsForAccount(account)
                            is Account.Fitbit -> emptyList()    // Fitbitは別途同期
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
                sessionManager.debugLastSyncedAt()
                _scrollToTopEvent.value = true

                val accounts = sessionManager.accountsFlow.first()
                val accountsToFetch = accounts.filter { !it.needsReauthentication }

                // Fitbit制限チェック
                val hasFitbit = accountsToFetch.any { it is Account.Fitbit }
                if (hasFitbit) {
                    val now = System.currentTimeMillis()
                    if (now - lastFitbitRefresh > 5 * 60 * 1000) { // 5分制限
                        syncFitbitData()
                        lastFitbitRefresh = now
                        Log.d("Fitbit", "Fitbitデータ更新実行")
                    } else {
                        val remaining = ((lastFitbitRefresh + 5 * 60 * 1000 - now) / 1000).toInt()
                        Log.d("Fitbit", "Fitbitデータ更新は${remaining}秒後に可能")
                    }
                }

                fetchPosts(accountsToFetch).join()
                sessionManager.debugLastSyncedAt()
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
        debugTagDuplicates()
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
                var tagId = postDao.getTagIdByName(tagName) // まず既存チェック
                if (tagId == null) {
                    tagId = postDao.insertTag(Tag(tagName = tagName)) // なければ新規作成
                }
                if (tagId != null && tagId != -1L) {
                    tagIds.add(tagId)
                }
            }
            val crossRefs = tagIds.map { tagId -> PostTagCrossRef(postToSave.id, tagId) }

            // 投稿、タグ、画像を一緒に保存
            postDao.updatePostWithTagsAndImages(postToSave, crossRefs, finalPostImages)

            // 5. 下書きをクリアしてダイアログを閉じる
            withContext(Dispatchers.Main) {
                clearDraftAndDismiss()
                clearTemporaryTags()

                if (isNewPost) {
                    // データ保存と同時にスクロール（即座に実行）
                    _scrollToTopEvent.value = true
                }

                // スクロール実行後にデータ更新
                launch(Dispatchers.IO) {
                    refreshPostsWithoutScroll()
                }
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
            if (currentState.currentTags.none { it.tagName.equals(trimmed, ignoreCase = true) }) {

                // ★★★ 新規・既存問わず一時表示に設定 ★★★
                viewModelScope.launch(Dispatchers.IO) {
                    val existingTagId = postDao.getTagIdByName(trimmed)
                    if (existingTagId == null) {
                        // 新規タグの場合：作成 + 一時表示
                        postDao.insertTag(Tag(tagName = trimmed))
                        postDao.setTagTemporaryShown(trimmed)
                        Log.d("TagDebug", "新規タグ '$trimmed' を作成 & 一時表示に設定")
                    } else {
                        // 既存タグの場合：一時表示のみ
                        postDao.setTagTemporaryShown(trimmed)
                        Log.d("TagDebug", "既存タグ '$trimmed' を一時表示に設定")
                    }
                }

                val newTag = Tag(tagId = 0, tagName = trimmed)
                val newState = currentState.copy(currentTags = currentState.currentTags + newTag)

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

    private fun clearTemporaryTags() {
        viewModelScope.launch(Dispatchers.IO) {
            postDao.clearAllTemporaryShown()
            Log.d("TagDebug", "一時表示タグをクリア")
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

    fun onFavoriteTagReorder(from: Int, to: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 現在のお気に入りタグリストを取得
            val currentFavoriteTags = favoriteTagsFlow.first().toMutableList()

            // 2. リスト内で要素を移動
            if (from in currentFavoriteTags.indices && to in currentFavoriteTags.indices) {
                val item = currentFavoriteTags.removeAt(from)
                currentFavoriteTags.add(to, item)
            } else {
                // インデックスが範囲外の場合は何もしない
                return@launch
            }

            // 3. 新しい順序に基づいて `favoriteOrder` を更新
            val updatedTags = currentFavoriteTags.mapIndexed { index, tag ->
                tag.copy(favoriteOrder = index)
            }

            // 4. 更新したタグのリストをデータベースに保存
            postDao.updateTags(updatedTags)
        }
    }

    private fun adjustDateByDayStart(dateTime: ZonedDateTime): LocalDate {
        val settings = timeSettings.value
        val adjustedDateTime = dateTime.minusHours(settings.dayStartHour.toLong())
            .minusMinutes(settings.dayStartMinute.toLong())
        return adjustedDateTime.toLocalDate()
    }

    private fun groupPostsByDay(posts: List<PostWithTagsAndImages>): List<DayLog> {
        if (posts.isEmpty()) {
            return emptyList()
        }
        val groupedByDate: Map<LocalDate, List<PostWithTagsAndImages>> = posts.groupBy {
            adjustDateByDayStart(it.post.createdAt.withZoneSameInstant(ZoneId.systemDefault()))
        }
        return groupedByDate.map { (date, postList) ->
            val sortedPostList = postList.sortedBy { it.post.createdAt }

            // ★修正点1: 通常通りfirstPostを選択
            val firstNonHealthPost = sortedPostList
                .firstOrNull { !it.post.isHealthData && it.post.source != SnsType.GOOGLEFIT }?.post
                ?: sortedPostList.firstOrNull()?.post

            // ★修正点2: 画像検索（GoogleFit除外不要）
            val firstImageInfo: Pair<String, String>? = sortedPostList
                .filter { !it.post.isHealthData && it.post.source != SnsType.GOOGLEFIT }
                .firstNotNullOfOrNull { postWithTagsAndImages ->
                    // 投稿から画像URLを探す
                    val imageUrl = postWithTagsAndImages.images.firstOrNull()?.let { image ->
                        image.thumbnailUrl ?: image.imageUrl
                    } ?: postWithTagsAndImages.post.imageUrl

                    // 画像URLが見つかった場合、その投稿IDとURLのペアを返す
                    imageUrl?.let { url ->
                        Pair(postWithTagsAndImages.post.id, url)
                    }
                }
                ?: sortedPostList.firstNotNullOfOrNull { postWithTagsAndImages ->
                    // 健康データからも画像検索
                    val imageUrl = postWithTagsAndImages.images.firstOrNull()?.let { image ->
                        image.thumbnailUrl ?: image.imageUrl
                    } ?: postWithTagsAndImages.post.imageUrl

                    imageUrl?.let { url ->
                        Pair(postWithTagsAndImages.post.id, url)
                    }
                }

            DayLog(
                date = date,
                firstPost = firstNonHealthPost, // ★修正: 健康データ以外の最初の投稿
                totalPosts = postList.size,
                imagePostId = firstImageInfo?.first,
                dayImageUrl = firstImageInfo?.second
            )
        }.sortedByDescending { it.date }
    }

    companion object {
        fun provideFactory(
            application: Application,
            blueskyApi: BlueskyApi,
            mastodonApi: MastodonApi,
            gitHubApi: GitHubApi, // ← 追加
            fitbitApi: FitbitApi,
            sessionManager: SessionManager,
            postDao: PostDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    application,
                    blueskyApi,
                    mastodonApi,
                    gitHubApi,
                    fitbitApi,  // ← これを追加
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
                Log.d("ImportDebug", "一時フォルダ作成完了: ${tempDir.absolutePath}")

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

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _backupState.value = BackupState.Error(e.message ?: "不明なエラー")
                    launch {
                        delay(3000)
                        _backupState.value = BackupState.Idle
                    }
                }
            }
        }
    }

    fun restoreFromBackup(backupUri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _restoreState.value = BackupState.Starting.copy(statusText = "復元準備中...")
                val context = getApplication<Application>().applicationContext

                _restoreState.value = BackupState.Progress(0.2f, "ZIPファイルを読み込み中...")

                // ★★★ 本物の処理開始 ★★★
                val inputStream = context.contentResolver.openInputStream(backupUri)
                if (inputStream == null) {
                    throw Exception("ファイルを読み込めませんでした")
                }

                _restoreState.value = BackupState.Progress(0.4f, "ファイルを展開中...")

                // ZIPファイル展開
                val tempDir = File(context.cacheDir, "restore_temp")
                tempDir.mkdirs()
                extractZip(inputStream, tempDir)

                _restoreState.value = BackupState.Progress(0.6f, "データを解析中...")

                // posts.txt読み込み
                val postsFile = File(tempDir, "posts.txt")
                if (!postsFile.exists()) {
                    throw Exception("posts.txtが見つかりません")
                }

                val restoredPosts = parsePostsFromText(postsFile.readText())

                _restoreState.value = BackupState.Progress(0.8f, "データベースに保存中...")

                // 画像ファイル復元
                val imagesDir = File(tempDir, "images")
                if (imagesDir.exists()) {
                    copyImagesToInternalStorage(imagesDir)
                }

                // DBに保存
                var newPostCount = 0
                var updatedPostCount = 0

                restoredPosts.forEach { restoredData ->
                    val existingPost = postDao.getPostById(restoredData.post.id)

                    if (existingPost == null) {
                        // 新規投稿として保存
                        postDao.insert(restoredData.post)

                        // タグ情報を保存
                        restoredData.tagNames.forEach { tagName ->
                            // まず既存タグIDを検索
                            var tagId = postDao.getTagIdByName(tagName)

                            // 存在しない場合は新規作成
                            if (tagId == null) {
                                tagId = postDao.insertTag(Tag(tagName = tagName))
                            }

                            // タグIDが有効な場合のみ関連付けを作成
                            tagId?.let { id ->
                                if (id > 0L) {
                                    postDao.insertPostTagCrossRef(
                                        PostTagCrossRef(restoredData.post.id, id)
                                    )
                                }
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
                            // まず既存タグIDを検索
                            var tagId = postDao.getTagIdByName(tagName)

                            // 存在しない場合は新規作成
                            if (tagId == null) {
                                tagId = postDao.insertTag(Tag(tagName = tagName))
                            }

                            // タグIDが有効な場合のみ関連付けを作成
                            tagId?.let { id ->
                                if (id > 0L) {
                                    postDao.insertPostTagCrossRef(
                                        PostTagCrossRef(restoredData.post.id, id)
                                    )
                                }
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

                // 一時フォルダを削除
                tempDir.deleteRecursively()

                _restoreState.value = BackupState.Progress(1.0f, "完了")

                withContext(Dispatchers.Main) {
                    launch {
                        delay(1000)
                        _restoreState.value = BackupState.Completed.copy(
                            statusText = "復元完了 (新規: ${newPostCount}件、更新: ${updatedPostCount}件)"
                        )
                        delay(2000)
                        _restoreState.value = BackupState.Idle
                    }
                }

                withContext(Dispatchers.Main) {
                    refreshPosts() // データ更新
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _restoreState.value = BackupState.Error(e.message ?: "復元に失敗しました")
                    launch {
                        delay(3000)
                        _restoreState.value = BackupState.Idle
                    }
                }
            }
        }
    }

    private val _maintenanceState = MutableStateFlow(BackupState.Idle)
    val maintenanceState = _maintenanceState.asStateFlow()

    fun performTagMaintenance() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _maintenanceState.value = BackupState.Starting.copy(statusText = "準備中...")

                _maintenanceState.value = BackupState.Progress(0.3f, "投稿データを読み込み中...")

                // タグ再取得（重複作らないように改良版）
                val (postCount, tagCount) = postDao.applyHashtagExtractionToAllPosts()

                _maintenanceState.value =
                    BackupState.Progress(0.7f, "重複タグをクリーンアップ中...")

                Log.d("TagMaintenance", "forceRemoveDuplicateTags開始")
                // 最後に重複削除
                postDao.forceRemoveDuplicateTags()
                Log.d("TagMaintenance", "forceRemoveDuplicateTags完了")

                _maintenanceState.value = BackupState.Progress(0.8f, "処理を完了中...")

                _maintenanceState.value = BackupState.Progress(1.0f, "完了")

                withContext(Dispatchers.Main) {
                    launch {
                        delay(1000)
                        _maintenanceState.value = BackupState.Completed.copy(
                            statusText = "再取得完了 (${postCount}件の投稿から${tagCount}個のタグを抽出)"
                        )
                        delay(3000)
                        _maintenanceState.value = BackupState.Idle
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _maintenanceState.value = BackupState.Error(e.message ?: "再取得に失敗しました")
                    launch {
                        delay(3000)
                        _maintenanceState.value = BackupState.Idle
                    }
                }
            }
        }
    }

    fun debugTagDuplicates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTags = postDao.getAllTagsForDebug()
                Log.d("TagDebug", "=== 全タグ一覧 ===")

                // タグ名でグループ化して重複をチェック
                val grouped = allTags.groupBy { it.tagName.lowercase().replace("#", "") }

                grouped.forEach { (normalizedName, tags) ->
                    if (tags.size > 1) {
                        Log.d("TagDebug", "重複発見: $normalizedName")
                        tags.forEach { tag ->
                            Log.d(
                                "TagDebug",
                                "  - ID:${tag.tagId}, 名前:'${tag.tagName}', お気に入り:${tag.isFavorite}"
                            )
                        }
                    } else {
                        Log.d("TagDebug", "正常: $normalizedName (ID:${tags[0].tagId})")
                    }
                }

                Log.d("TagDebug", "=== デバッグ完了 ===")
            } catch (e: Exception) {
                Log.e("TagDebug", "デバッグエラー: ${e.message}")
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
                    val tagText = tagLine.substringAfter("タグ: ").trim()
                    if (tagText.isBlank()) {
                        emptyList()
                    } else {
                        // カンマ、スペース、全角スペースで分割
                        tagText.split(",", " ", "　")
                            .map { tag ->
                                // タグから # を削除し、前後の空白も除去
                                tag.replace("#+".toRegex(), "").trim()
                            }
                            .filterNot { it.isBlank() } // 空文字を除外
                    }
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
                    "GITHUB" -> SnsType.GITHUB // ← 追加
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

    private val _dataSizeDetails = MutableStateFlow("テキスト 0 MB / 画像 0 MB")
    val dataSizeDetails = _dataSizeDetails.asStateFlow()

    fun calculateDataSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext

                // 1. データベースファイルのサイズ（テキストデータ）
                val dbFile = context.getDatabasePath("app_database")
                val dbSize = if (dbFile.exists()) dbFile.length() else 0L

                // 2. 画像フォルダのサイズ
                val imagesSize = context.filesDir.listFiles()?.sumOf { file ->
                    if (file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".png"))) {
                        file.length()
                    } else 0L
                } ?: 0L

                // 3. サイズをMBで計算
                val totalSizeBytes = dbSize + imagesSize
                val totalSizeMB = totalSizeBytes / (1024.0 * 1024.0)
                val textSizeMB = dbSize / (1024.0 * 1024.0)
                val imagesSizeMB = imagesSize / (1024.0 * 1024.0)

                // 4. 表示用の文字列を作成
                _dataSize.value = "%.1f MB".format(totalSizeMB)
                _dataSizeDetails.value =
                    "テキスト %.1f MB / 画像 %.1f MB".format(textSizeMB, imagesSizeMB)

            } catch (e: Exception) {
                _dataSize.value = "計算エラー"
                _dataSizeDetails.value = "計算エラー"
            }
        }
    }

    fun updateDayStartTime(hour: Int, minute: Int) {
        timeSettingsRepository.updateDayStartTime(hour, minute)
    }

    fun updateWeekStartDay(dayOfWeek: java.time.DayOfWeek) {
        Log.d("Debug", "updateWeekStartDay called with: $dayOfWeek")
        timeSettingsRepository.updateWeekStartDay(dayOfWeek)
        Log.d("Debug", "repository.updateWeekStartDay完了")
    }

    fun updateTimeFormat(format: com.example.logleaf.data.settings.TimeFormat) {
        timeSettingsRepository.updateTimeFormat(format)
    }

    fun cleanupDuplicateTags() {
        viewModelScope.launch(Dispatchers.IO) {
            postDao.normalizeTagNames() // ← 追加
            postDao.removeDuplicateTags()
            postDao.cleanupOrphanedTagRelations()
            Log.d("TagCleanup", "重複タグ削除・正規化完了")
        }
    }

    /**
     * Zeppの健康データ（パスワード付きZIP）をインポートする
     */
    fun importZeppHealthData(zipUri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _zeppImportState.value =
                    BackupState.Starting.copy(statusText = "インポート準備中...")

                Log.d("ZeppImport", "=== Zeppデータインポート開始 ===")
                val context = getApplication<Application>().applicationContext

                _zeppImportState.value = BackupState.Progress(0.2f, "ZIPファイルを解凍中...")

                // 1. ZIPファイルを一時ディレクトリに展開
                val tempDir = File(context.cacheDir, "zepp_import_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                // 2. zip4jでパスワード付きZIPを解凍
                val inputStream = context.contentResolver.openInputStream(zipUri)
                val tempZipFile = File(tempDir, "zepp_data.zip")
                inputStream?.use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. zip4jで解凍
                val zipFile = net.lingala.zip4j.ZipFile(tempZipFile)
                if (zipFile.isEncrypted) {
                    zipFile.setPassword(password.toCharArray())
                }
                zipFile.extractAll(tempDir.absolutePath)

                // 4. CSVファイルを探して解析
                val sleepCsv = tempDir.walkTopDown()
                    .find { it.name.contains("SLEEP") && it.extension == "csv" }
                val sportCsv = tempDir.walkTopDown()
                    .find { it.name.contains("SPORT") && it.extension == "csv" }
                val activityCsv = tempDir.walkTopDown()
                    .find { it.name.contains("ACTIVITY") && it.extension == "csv" }

                Log.d("ZeppImport", "SLEEP.csv: ${sleepCsv?.exists()}")
                Log.d("ZeppImport", "SPORT.csv: ${sportCsv?.exists()}")

                _zeppImportState.value = BackupState.Progress(0.4f, "CSVファイルを解析中...")

                // 5. CSV解析して投稿生成
                val posts = mutableListOf<Post>()
                val timeSettings = timeSettingsRepository.timeSettings.first()

                // 睡眠データ解析
                sleepCsv?.let { csvFile ->
                    val lines = csvFile.readLines().drop(1) // ヘッダー行をスキップ
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            val columns = line.split(",")
                            if (columns.size >= 7) {
                                try {
                                    val date = columns[0]
                                    val deepSleep = columns[1].toIntOrNull() ?: 0 // 分
                                    val shallowSleep = columns[2].toIntOrNull() ?: 0 // 分
                                    val startTime = columns[4] // "2022-10-20 21:06:00+0000"
                                    val stopTime = columns[5] // "2022-10-21 05:25:00+0000"
                                    val remSleep = columns[6].toIntOrNull() ?: 0 // 分

                                    // 時刻解析
                                    val startDateTime = ZonedDateTime.parse(
                                        startTime.replace(" ", "T").replace("+0000", "Z")
                                    )
                                    val stopDateTime = ZonedDateTime.parse(
                                        stopTime.replace(" ", "T").replace("+0000", "Z")
                                    )

                                    // 日本時間に変換
                                    val startJST =
                                        startDateTime.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
                                    val stopJST =
                                        stopDateTime.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))

                                    // 総睡眠時間計算
                                    val totalMinutes = deepSleep + shallowSleep + remSleep
                                    val totalHours = totalMinutes / 60
                                    val remainingMinutes = totalMinutes % 60

                                    if (totalMinutes == 0) {
                                        Log.d("ZeppImport", "0分睡眠データをスキップ: $date")
                                        return@forEach // この行をスキップして次の行へ
                                    }

                                    // 投稿時刻は起床日の日切り替え時間
                                    val sleepDate = stopJST.toLocalDate()
                                    val postTime = sleepDate.atTime(
                                        timeSettings.dayStartHour,
                                        timeSettings.dayStartMinute
                                    )
                                        .atZone(ZoneId.of("Asia/Tokyo"))

                                    // 投稿テキスト生成
                                    val sleepText = """
                        🛏️ ${startJST.format(DateTimeFormatter.ofPattern("HH:mm"))} → ${
                                        stopJST.format(
                                            DateTimeFormatter.ofPattern("HH:mm")
                                        )
                                    } (${totalHours}h${remainingMinutes}m)
                        深い睡眠: ${deepSleep}分
                        浅い睡眠: ${shallowSleep}分
                        レム睡眠: ${remSleep}分
                    """.trimIndent()

                                    val sleepPost = Post(
                                        id = "zepp_sleep_${date.replace("-", "")}",
                                        accountId = sessionManager.accountsFlow.first()
                                            .first().userId,
                                        text = sleepText,
                                        createdAt = postTime,
                                        source = SnsType.FITBIT,
                                        imageUrl = null,
                                        isHealthData = true
                                    )

                                    posts.add(sleepPost)
                                    Log.d("ZeppImport", "睡眠投稿生成: $date")

                                } catch (e: Exception) {
                                    Log.e("ZeppImport", "睡眠データ解析エラー: $line", e)
                                }
                            }
                        }
                    }
                }

                // 運動データ解析
                sportCsv?.let { csvFile ->
                    val lines = csvFile.readLines().drop(1) // ヘッダー行をスキップ
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            val columns = line.split(",")
                            if (columns.size >= 8) {
                                try {
                                    val type = columns[0] // 運動タイプ
                                    val startTime = columns[1] // "2022-10-23 13:27:52+0000"
                                    val sportTimeSeconds = columns[2].toIntOrNull() ?: 0
                                    val distanceMeters = columns[5].toDoubleOrNull() ?: 0.0
                                    val calories = columns[7].toDoubleOrNull() ?: 0.0

                                    // 時刻解析・日本時間変換
                                    val startDateTime = ZonedDateTime.parse(
                                        startTime.replace(" ", "T").replace("+0000", "Z")
                                    )
                                    val startJST =
                                        startDateTime.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))

// 運動終了時刻を計算
                                    val endJST = startJST.plusSeconds(sportTimeSeconds.toLong())

// 運動時間・距離変換
                                    val sportMinutes = sportTimeSeconds / 60
                                    val distanceKm = distanceMeters / 1000.0

// 運動タイプ判定（とりあえず1=ランニング）
                                    val sportTypeName = when (type) {
                                        "1" -> "ランニング"
                                        else -> "運動"
                                    }

// 投稿テキスト生成（時刻情報を追加）
                                    val sportText = """
🏃‍♂️ $sportTypeName ${startJST.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${
                                        endJST.format(
                                            DateTimeFormatter.ofPattern("HH:mm")
                                        )
                                    } ${sportMinutes}分
距離: ${String.format("%.1f", distanceKm)}km
カロリー: ${calories.toInt()}kcal
""".trimIndent()
                                    val sportPost = Post(
                                        id = "zepp_sport_${
                                            startTime.replace(":", "").replace("-", "")
                                                .replace(" ", "_")
                                        }",
                                        accountId = sessionManager.accountsFlow.first()
                                            .first().userId,
                                        text = sportText,
                                        createdAt = startJST, // 運動開始時刻
                                        source = SnsType.FITBIT,
                                        imageUrl = null,
                                        isHealthData = true
                                    )

                                    posts.add(sportPost)
                                    Log.d(
                                        "ZeppImport",
                                        "運動投稿生成: $sportTypeName ${sportMinutes}分"
                                    )

                                } catch (e: Exception) {
                                    Log.e("ZeppImport", "運動データ解析エラー: $line", e)
                                }
                            }
                        }
                    }
                }

                // アクティビティデータ解析（運動データ解析の後に追加）
                activityCsv?.let { csvFile ->
                    val lines = csvFile.readLines().drop(1) // ヘッダー行をスキップ
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            val columns = line.split(",")
                            if (columns.size >= 5) {
                                try {
                                    val date = columns[0] // "2021-06-22"
                                    val steps = columns[1].toIntOrNull() ?: 0
                                    val calories = columns[4].toIntOrNull() ?: 0

                                    // 投稿時刻は23:59
                                    val activityDate = LocalDate.parse(date)
                                    val postTime = activityDate.atTime(
                                        timeSettings.dayStartHour,
                                        timeSettings.dayStartMinute
                                    )
                                        .minusMinutes(1)
                                        .atZone(ZoneId.of("Asia/Tokyo"))

                                    // 投稿テキスト生成
                                    val activityText = """
📊 今日の健康データ
歩数: ${steps.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}歩
消費カロリー: ${calories}kcal
                    """.trimIndent()

                                    val activityPost = Post(
                                        id = "zepp_activity_${date.replace("-", "")}",
                                        accountId = sessionManager.accountsFlow.first()
                                            .first().userId,
                                        text = activityText,
                                        createdAt = postTime,
                                        source = SnsType.FITBIT,
                                        imageUrl = null,
                                        isHealthData = true
                                    )

                                    posts.add(activityPost)
                                    Log.d(
                                        "ZeppImport",
                                        "アクティビティ投稿生成: $date - ${steps}歩, ${calories}kcal"
                                    )

                                } catch (e: Exception) {
                                    Log.e("ZeppImport", "アクティビティデータ解析エラー: $line", e)
                                }
                            }
                        }
                    }
                }

                _zeppImportState.value = BackupState.Progress(0.8f, "健康データを保存中...")

                // データベースに保存
                Log.d("ZeppImport", "合計 ${posts.size} 件の投稿を保存開始")
                posts.forEach { post ->
                    postDao.insertWithHashtagExtraction(post)
                }

                _zeppImportState.value = BackupState.Progress(1.0f, "完了")

                // 投稿リスト更新
                refreshPostsWithoutScroll()

                Log.d("ZeppImport", "健康データ投稿生成完了: ${posts.size}件")

                // 少し待ってからCompleted状態に
                delay(500)
                _zeppImportState.value = BackupState.Completed

                // 6. 一時ファイル削除
                tempDir.deleteRecursively()

                Log.d("ZeppImport", "=== Zeppデータインポート完了 ===")

            } catch (e: Exception) {
                _zeppImportState.value = BackupState.Error(e.message ?: "インポートエラー")
                Log.e("ZeppImport", "インポートエラー: ${e.message}", e)
            }
        }
    }

    /**
     * 睡眠データの期間一括取得
     */
    private suspend fun syncFitbitSleepDataRange(
        startDate: LocalDate,
        endDate: LocalDate,
        account: Account.Fitbit
    ) {
        try {
            val result = fitbitApi.getSleepDataRange(account.accessToken, startDate, endDate)

            if (result != null) {
                val (sleepDataMap, napDataMap) = result

                // 既存の睡眠データ処理
                val currentTimeSettings = timeSettings.first() // 関数の最初で一度だけ取得
                sleepDataMap.forEach { (date, sleepData) ->

                    val postTime = date.atTime(currentTimeSettings.dayStartHour, currentTimeSettings.dayStartMinute)
                        .atZone(ZoneId.of("Asia/Tokyo"))

                    val sleepText = """
💤 睡眠記録
${sleepData.startTime} → ${sleepData.endTime} (${sleepData.duration})
深い睡眠: ${sleepData.deepSleep}分
浅い睡眠: ${sleepData.lightSleep}分
レム睡眠: ${sleepData.remSleep}分
覚醒: ${sleepData.awakeSleep}分
睡眠効率: ${sleepData.efficiency}%
""".trimIndent()

                    val sleepPost = Post(
                        id = "fitbit_sleep_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}_${account.userId}",
                        accountId = account.userId,
                        text = sleepText,
                        createdAt = postTime,
                        source = SnsType.FITBIT,
                        imageUrl = null,
                        isHidden = false,
                        isHealthData = true
                    )

                    postDao.deletePostById(sleepPost.id)
                    insertFitbitPostWithTags(sleepPost, listOf("睡眠"))
                }

                // 仮眠データ処理（新規追加）
                napDataMap.forEach { (date, napData) ->

                    // 仮眠開始時刻をパース（"14:30:00" → 14:30）
                    val napStartTime = try {
                        val timeParts = napData.startTime.split(":")
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()
                        date.atTime(hour, minute).atZone(ZoneId.of("Asia/Tokyo"))
                    } catch (e: Exception) {
                        Log.e("Fitbit", "仮眠開始時刻パースエラー: ${napData.startTime}", e)
                        date.atTime(12, 0).atZone(ZoneId.of("Asia/Tokyo")) // デフォルト時刻
                    }

                    val napText = """
😴 仮眠記録
${napData.startTime} → ${napData.endTime} (${napData.duration})
深い睡眠: ${napData.deepSleep}分
浅い睡眠: ${napData.lightSleep}分
レム睡眠: ${napData.remSleep}分
覚醒: ${napData.awakeSleep}分
睡眠効率: ${napData.efficiency}%
""".trimIndent()

                    val napPost = Post(
                        id = "fitbit_nap_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}_${napData.startTime.replace(":", "")}_${account.userId}",
                        accountId = account.userId,
                        text = napText,
                        createdAt = napStartTime,
                        source = SnsType.FITBIT,
                        imageUrl = null,
                        isHealthData = true
                    )

                    postDao.deletePostById(napPost.id)
                    insertFitbitPostWithTags(napPost, listOf("仮眠"))

                    Log.d("Fitbit", "仮眠データ投稿作成完了: ${napData.startTime}")
                }
            }

            Log.d("Fitbit", "睡眠データ一括取得完了: $startDate ～ $endDate")
        } catch (e: Exception) {
            Log.e("Fitbit", "睡眠データ期間取得エラー", e)
        }
    }

    private suspend fun syncFitbitActivityData(date: LocalDate, account: Account.Fitbit) {
        try {
            Log.e("DEBUG", "=== syncFitbitActivityData開始 ===")
            Log.e("DEBUG", "date: $date")

            val result = fitbitApi.getActivityData(account.accessToken, date)  // ← 変更
            Log.d("DEBUG", "result: $result")

            if (result != null) {  // ← 変更
                val (activityData, exerciseDataList) = result  // ← 追加：Pairを分解

                // 健康データ処理（既存ロジック）
                if (activityData != null && (activityData.steps > 0 || activityData.calories > 0)) {
                    Log.d("DEBUG", "投稿作成実行")
                    val currentTimeSettings = timeSettings.first()

                    Log.e("DEBUG", "timeSettings: ${currentTimeSettings.dayStartHour}:${currentTimeSettings.dayStartMinute}")

                    val postTime = date.plusDays(1)
                        .atTime(currentTimeSettings.dayStartHour, currentTimeSettings.dayStartMinute)
                        .minusMinutes(1)
                        .atZone(ZoneId.of("Asia/Tokyo"))

                    Log.e("DEBUG", "計算後のpostTime: $postTime")
                    Log.e("DEBUG", "postTimeの日付: ${postTime.toLocalDate()}")

                    val adjustedDate = postTime.minusHours(currentTimeSettings.dayStartHour.toLong())
                        .minusMinutes(currentTimeSettings.dayStartMinute.toLong())
                        .toLocalDate()
                    Log.e("DEBUG", "調整後の表示日付: $adjustedDate")

                    val activityText = """
📊 今日の健康データ
歩数: ${activityData.steps.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}歩
消費カロリー: ${activityData.calories}kcal
""".trimIndent()

                    val activityPost = Post(
                        id = "fitbit_activity_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}_${account.userId}",
                        accountId = account.userId,
                        text = activityText,
                        createdAt = postTime,
                        source = SnsType.FITBIT,
                        imageUrl = null,
                        isHealthData = true
                    )

                    postDao.deletePostById(activityPost.id)
                    val activityTags = listOf("歩数", "カロリー")
                    insertFitbitPostWithTags(activityPost, activityTags)
                } else {
                    Log.d("DEBUG", "健康データ投稿作成スキップ")
                }

                // 運動データ処理
                exerciseDataList.forEach { exerciseData ->
                    Log.d("DEBUG", "運動データ処理: ${exerciseData.name}")

                    // 運動開始時刻をパース（"18:30:00" → 18:30）
                    val exerciseStartTime = try {
                        val timeParts = exerciseData.startTime.split(":")
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()
                        date.atTime(hour, minute).atZone(ZoneId.of("Asia/Tokyo"))
                    } catch (e: Exception) {
                        Log.e("DEBUG", "運動開始時刻パースエラー: ${exerciseData.startTime}", e)
                        date.atTime(12, 0).atZone(ZoneId.of("Asia/Tokyo")) // デフォルト時刻
                    }

                    // 運動データのテキスト作成
                    val exerciseText = buildString {
                        append("🏃 運動記録\n")
                        append("運動: ${exerciseData.name}\n")
                        append("開始時刻: ${exerciseData.startTime}\n")
                        append("継続時間: ${exerciseData.duration}\n")
                        if (exerciseData.calories != null) {
                            append("消費カロリー: ${exerciseData.calories}kcal\n")
                        }
                        if (exerciseData.distance != null) {
                            append("距離: ${String.format("%.2f", exerciseData.distance)}km")
                        }
                    }.trimEnd()

                    val exercisePost = Post(
                        id = "fitbit_exercise_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}_${exerciseData.startTime.replace(":", "")}_${account.userId}",
                        accountId = account.userId,
                        text = exerciseText,
                        createdAt = exerciseStartTime,
                        source = SnsType.FITBIT,
                        imageUrl = null,
                        isHealthData = true
                    )

                    // 既存データを削除してから保存
                    postDao.deletePostById(exercisePost.id)

                    // 運動種類をタグとして使用
                    val exerciseTags = listOf(exerciseData.name)
                    insertFitbitPostWithTags(exercisePost, exerciseTags)

                    Log.d("DEBUG", "運動データ投稿作成完了: ${exerciseData.name}")
                }

            } else {
                Log.d("DEBUG", "データなしのためスキップ")
            }
        } catch (e: Exception) {
            Log.e("Fitbit", "アクティビティデータ同期エラー", e)
        }
    }

    private suspend fun insertFitbitPostWithTags(post: Post, tagNames: List<String>) {
        // 1. 投稿を保存（ハッシュタグ抽出なし）
        postDao.insert(post)  // ← insertPostから変更

        // 2. タグを処理
        val tagIds = mutableListOf<Long>()
        tagNames.forEach { tagName ->
            var tagId = postDao.getTagIdByName(tagName)
            if (tagId == null) {
                // タグが存在しない場合は新規作成
                tagId = postDao.insertTag(Tag(tagName = tagName))
            }
            if (tagId != null && tagId != -1L) {
                tagIds.add(tagId)
            }
        }

        // 3. 投稿とタグの関連付け
        val crossRefs = tagIds.map { tagId ->
            PostTagCrossRef(postId = post.id, tagId = tagId)
        }
        crossRefs.forEach { crossRef ->
            postDao.insertPostTagCrossRef(crossRef)
        }
    }

    /**
     * Fitbit過去データを取得（2ヶ月分）
     */
    fun fetchFitbitHistoryData(userId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val historyManager = FitbitHistoryManager(context)

                // 取得制限チェック
                if (!historyManager.canFetchHistory(userId)) {
                    Log.d("Fitbit", "履歴取得制限中: $userId")
                    onComplete()
                    return@launch
                }

                val fitbitAccount = sessionManager.getAccounts()
                    .find { it is Account.Fitbit && it.userId == userId } as? Account.Fitbit

                if (fitbitAccount == null) {
                    Log.e("Fitbit", "Fitbitアカウントが見つかりません: $userId")
                    onComplete()
                    return@launch
                }

                // 取得可能期間を計算
                val availablePeriod = historyManager.getAvailablePeriod(userId)
                Log.e("FITBIT_DEBUG", "=== Fitbit期間取得デバッグ ===")
                Log.e("FITBIT_DEBUG", "userId: $userId")
                Log.e("FITBIT_DEBUG", "availablePeriod: $availablePeriod")
                Log.e("FITBIT_DEBUG", "oldestDate: ${historyManager.getOldestDataDate(userId)}")
                Log.e("FITBIT_DEBUG", "newestDate: ${historyManager.getNewestDataDate(userId)}")
                Log.e("FITBIT_DEBUG", "lastSyncedAt: ${fitbitAccount.lastSyncedAt}")

                if (availablePeriod == null) {
                    // 初回取得の場合：デフォルト2ヶ月期間を使用
                    val endDate = LocalDate.now()

                    val startDate = endDate.minusMonths(2)

                    Log.d("Fitbit", "初回取得: $startDate ～ $endDate")

                    // 以下は既存のコードと同じ処理を実行
                    historyManager.setLastHistoryFetchTime(userId)
                    syncFitbitSleepDataRange(startDate, endDate, fitbitAccount)

                    var currentDate = startDate
                    var apiCallCount = 0
                    val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

                    while (!currentDate.isAfter(endDate) && apiCallCount < 120) {
                        syncFitbitActivityData(currentDate, fitbitAccount)
                        apiCallCount++

                        // ← ここに進捗更新を追加
                        _fitbitSyncProgress.value = Pair(apiCallCount, totalDays)

                        delay(100)
                        currentDate = currentDate.plusDays(1)
                    }

                    // ← ここに完了時のクリアを追加
                    _fitbitSyncProgress.value = null

                    historyManager.recordInitialPeriod(userId, startDate, endDate)  // ← 修正
                    Log.d("Fitbit", "初回取得完了: $startDate ～ $endDate (API: $apiCallCount 回)")
                    onComplete()
                    return@launch
                }

                val (startDate, endDate) = availablePeriod
                Log.d("Fitbit", "履歴取得開始: $userId, $startDate ～ $endDate")

                // 取得時刻を記録（開始時）
                historyManager.setLastHistoryFetchTime(userId)

                // 睡眠データ：期間一括取得
                syncFitbitSleepDataRange(startDate, endDate, fitbitAccount)

                // 健康データ・運動データ：日別取得
                var currentDate = startDate
                var apiCallCount = 0
                val maxApiCalls = 120 // 余裕を持って設定

                while (!currentDate.isAfter(endDate) && apiCallCount < maxApiCalls) {
                    // 健康データ同期
                    syncFitbitActivityData(currentDate, fitbitAccount)
                    apiCallCount++

                    // 運動データ同期（将来実装）
                    // syncFitbitExerciseData(currentDate, fitbitAccount)
                    // apiCallCount++

                    // API制限対策
                    delay(100)

                    currentDate = currentDate.plusDays(1)

                    // 進捗ログ（10日ごと）
                    if (currentDate.dayOfMonth % 10 == 0) {
                        Log.d("Fitbit", "履歴取得進捗: $currentDate (API: $apiCallCount)")
                    }
                }

                // 履歴期間を記録
                historyManager.recordHistoryPeriod(userId, startDate)

                Log.d("Fitbit", "履歴取得完了: $userId, $startDate ～ $endDate (API: $apiCallCount 回)")

            } catch (e: Exception) {
                Log.e("Fitbit", "履歴取得エラー: $userId", e)
            } finally {
                onComplete()
            }
        }
    }

    // 既存の syncFitbitData メソッドも修正が必要
    fun syncFitbitData(targetDate: LocalDate? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fitbitAccount = sessionManager.getAccounts()
                    .find { it is Account.Fitbit } as? Account.Fitbit

                if (fitbitAccount == null) {
                    Log.d("Fitbit", "Fitbitアカウントが見つかりません")
                    return@launch
                }

                Log.d("Fitbit", "データ同期開始")

                // 指定日のデータのみ取得（シンプル版）
                val today = targetDate ?: LocalDate.now()

                // 睡眠データ同期
                //syncFitbitSleepData(today, fitbitAccount)

                // アクティビティデータ同期
                syncFitbitActivityData(today, fitbitAccount)

                Log.d("Fitbit", "データ同期完了")

            } catch (e: Exception) {
                Log.e("Fitbit", "データ同期エラー", e)
            }
        }
    }

    fun clearFitbitHistory(userId: String) {
        val context = getApplication<Application>().applicationContext
        val historyManager = FitbitHistoryManager(context)
        historyManager.clearAllData(userId)
        Log.d("Fitbit", "履歴制限をクリア: $userId")
    }


    // テスト用：ダミーFitbitポストを作成
    fun createDummyFitbitPosts() {
        viewModelScope.launch {
            val today = LocalDate.now()

            // 睡眠データ
            val dummySleepPost = Post(
                id = "test_fitbit_sleep_${today}",
                accountId = "CR9FZ2",
                text = """
💤 睡眠記録
23:30 → 07:15 (7時間45分)
深い睡眠: 96分
浅い睡眠: 301分
レム睡眠: 58分
覚醒: 10分
睡眠効率: 85%
""".trimIndent(),
                createdAt = today.atTime(7, 0).atZone(ZoneId.systemDefault()),
                source = SnsType.FITBIT,
                imageUrl = null,
                isHealthData = true
            )

            // アクティビティデータ
            val dummyActivityPost = Post(
                id = "test_fitbit_activity_${today}",
                accountId = "CR9FZ2",
                text = """
🏃 アクティビティ記録
歩数: 8,247歩
消費カロリー: 287kcal
""".trimIndent(),
                createdAt = today.atTime(6, 59).atZone(ZoneId.systemDefault()),
                source = SnsType.FITBIT,
                imageUrl = null,
                isHealthData = true
            )

            val dummyExercisePost = Post(
                id = "test_fitbit_exercise_${today}",
                accountId = "CR9FZ2",
                text = """
🏃 運動記録
運動: ランニング
開始時刻: 18:30
継続時間: 45分
消費カロリー: 420kcal
""".trimIndent(),
                createdAt = today.atTime(18, 30).atZone(ZoneId.systemDefault()),
                source = SnsType.FITBIT,
                imageUrl = null,
                isHealthData = true
            )

            insertFitbitPostWithTags(dummySleepPost, listOf("睡眠"))
            insertFitbitPostWithTags(dummyActivityPost, listOf("歩数", "カロリー"))
            insertFitbitPostWithTags(dummyExercisePost, listOf("運動")) // ← この行を追加

            Log.d("Fitbit", "ダミーポスト作成完了")
        }
    }
    fun fixZeppData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = postDao.fixZeppHealthDataFlag()
                Log.d("ZeppFix", "${count}件のZeppデータを修正完了")
            } catch (e: Exception) {
                Log.e("ZeppFix", "修正エラー", e)
            }
        }
    }
}