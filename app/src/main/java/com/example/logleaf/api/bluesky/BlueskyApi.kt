package com.example.logleaf.api.bluesky

import android.util.Log
import com.example.logleaf.MainViewModel
import com.example.logleaf.data.model.Account
import com.example.logleaf.data.model.Post
import com.example.logleaf.data.model.PostWithImageUrls
import com.example.logleaf.data.session.SessionManager
import com.example.logleaf.ui.theme.SnsType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


@Serializable
data class LoginRequest(val identifier: String, val password: String)
@Serializable
data class LoginResponse(val accessJwt: String, val did: String, val refreshJwt: String, val handle: String)
@Serializable
data class BskyFeedResponse(
    val feed: List<BskyFeedItem>,
    val cursor: String? = null
)
@Serializable
data class BskyFeedItem(val post: BskyPost)
@Serializable
data class BskyPost(
    val uri: String,
    val record: BskyRecord,
    val embed: BskyEmbed? = null // ◀◀◀ 画像情報などが入る場所を追加
)
@Serializable
data class BskyRecord(val text: String, val createdAt: String)
@Serializable
data class BskyEmbed(
    val images: List<BskyImage>? = null
)
@Serializable
data class BskyImage(
    val fullsize: String, // ◀◀◀ これが画像のURL
    val alt: String
)

class BlueskyApi(private val sessionManager: SessionManager) {
    private var mainViewModel: MainViewModel? = null

    fun setMainViewModel(viewModel: MainViewModel) {
        this.mainViewModel = viewModel
    }

    private val client = HttpClient(CIO) {
        // JSONの変換設定 (これは元々ありました)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        // ネットワークログを出力するための設定
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v("KtorLogger", message)
                }
            }
            level = LogLevel.ALL
        }
        // getPostsForAccountメソッドで使用時：
        mainViewModel?.setSyncProgress(0, 100)
        mainViewModel?.clearSyncState()
        mainViewModel?.setSyncError()
    }

    suspend fun login(handle: String, password: String, period: String = "3ヶ月"): Boolean {
        return try {
            val response: LoginResponse = client.post("https://bsky.social/xrpc/com.atproto.server.createSession") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(identifier = handle, password = password))
            }.body()

            // 取得した情報からAccount.Blueskyオブジェクトを作成して保存する
            val newAccount = Account.Bluesky(
                did = response.did,
                handle = response.handle,
                accessToken = response.accessJwt,
                refreshToken = response.refreshJwt,
                period = period // ← 期間を追加
            )
            sessionManager.saveAccount(newAccount)

            Log.d("BlueskyApi", "Blueskyアカウント保存成功: ${response.handle}, 期間: $period")
            true
        } catch (e: Exception) {
            Log.e("BlueskyApi", "ログインエラー: ${e.message}")
            false
        }
    }

    suspend fun getPostsForAccount(account: Account.Bluesky): List<PostWithImageUrls> {
        try {
            val sinceDate = determineSinceDate(account)
            val isExpanded = isPeriodExpanded(account) // 1回だけ判定

            // 期間拡大時のプログレス開始
            if (isExpanded) {
                mainViewModel?.setSyncProgress(0, 100)
                Log.d("BlueskyApi", "期間拡大取得開始：プログレス表示")
            }

            val posts = tryToGetPosts(
                account.accessToken,
                account.did,
                account.userId,
                sinceDate,
                onProgress = if (isExpanded) { current, total ->
                    mainViewModel?.setSyncProgress(current, total)
                } else null
            )

            // プログレス完了
            if (isExpanded) {
                mainViewModel?.clearSyncState()
            }

            // 取得成功後、最終同期時刻を更新
            if (posts.isNotEmpty()) {
                sessionManager.updateLastSyncedAt(account.userId, ZonedDateTime.now())
                Log.d("BlueskyApi", "Blueskyアカウント(${account.handle})の同期時刻を更新")
            }

            // 期間拡大処理が完了したらlastPeriodSettingを更新
            if (isExpanded) {
                sessionManager.updateBlueskyAccountLastPeriod(account.handle, account.period)
                Log.d("BlueskyApi", "期間設定を更新: ${account.period}")
            }

            return posts

        } catch (e: Exception) {
            // エラー時
            if (isPeriodExpanded(account)) {
                mainViewModel?.setSyncError()
            }

            Log.e("BlueskyApi", "1回目の投稿取得に失敗しました: ${e.message}")
            val refreshedAccount = refreshSession(account)

            return if (refreshedAccount != null) {
                Log.d("BlueskyApi", "セッション更新成功。投稿を再取得します。")
                try {
                    val sinceDate = determineSinceDate(refreshedAccount)
                    val isRefreshedExpanded = isPeriodExpanded(refreshedAccount) // 再取得時も1回だけ判定

                    val posts = tryToGetPosts(
                        refreshedAccount.accessToken,
                        refreshedAccount.did,
                        refreshedAccount.userId,
                        sinceDate,
                        onProgress = if (isRefreshedExpanded) { current, total ->
                            mainViewModel?.setSyncProgress(current, total)
                        } else null
                    )

                    // プログレス完了（再取得成功時も）
                    if (isRefreshedExpanded) {
                        mainViewModel?.clearSyncState()
                    }

                    // 再取得成功後も同期時刻を更新
                    if (posts.isNotEmpty()) {
                        sessionManager.updateLastSyncedAt(refreshedAccount.userId, ZonedDateTime.now())
                        Log.d("BlueskyApi", "Blueskyアカウント(${refreshedAccount.handle})の同期時刻を更新（再取得後）")
                    }

                    // 期間拡大処理が完了したらlastPeriodSettingを更新
                    if (isRefreshedExpanded) {
                        sessionManager.updateBlueskyAccountLastPeriod(refreshedAccount.handle, refreshedAccount.period)
                        Log.d("BlueskyApi", "期間設定を更新: ${refreshedAccount.period}")
                    }

                    posts
                } catch (e2: Exception) {
                    Log.e("BlueskyApi", "再取得にも失敗しました: ${e2.message}")
                    // 再取得も失敗時のプログレスクリア
                    if (isPeriodExpanded(refreshedAccount)) {
                        mainViewModel?.setSyncError()
                    }
                    emptyList()
                }
            } else {
                Log.e("BlueskyApi", "セッション更新に失敗しました。再ログインが必要です。")
                emptyList()
            }
        }
    }

    private suspend fun tryToGetPosts(
        token: String,
        did: String,
        accountId: String,
        sinceDate: String?,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<PostWithImageUrls> {
        val allPosts = mutableListOf<PostWithImageUrls>()
        var cursor: String? = null
        var pageCount = 0

        Log.d("BlueskyApi", "ページネーション開始: sinceDate=$sinceDate")

        do {
            pageCount++
            // プログレス表示（終了条件不明なので、進行中であることを示す）
            onProgress?.invoke(pageCount, pageCount + 5)

            val response: BskyFeedResponse = client.get("https://bsky.social/xrpc/app.bsky.feed.getAuthorFeed") {
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                parameter("actor", did)
                parameter("limit", 100)
                cursor?.let { parameter("cursor", it) }
            }.body()

            Log.d("BlueskyApi", "ページ$pageCount: ${response.feed.size}件取得")

            val pagePosts = response.feed.map { feedItem ->
                val post = feedItem.post
                val imageUrls = post.embed?.images?.map { it.fullsize } ?: emptyList()
                val imageUrl = imageUrls.firstOrNull()

                val postEntity = Post(
                    id = post.uri,
                    accountId = accountId,
                    createdAt = ZonedDateTime.parse(post.record.createdAt),
                    text = post.record.text,
                    imageUrl = imageUrl,
                    source = SnsType.BLUESKY
                )

                PostWithImageUrls(post = postEntity, imageUrls = imageUrls)
            }

            // 期間フィルタリング
            val filteredPosts = if (sinceDate != null) {
                val sinceDateTime = ZonedDateTime.parse(sinceDate)
                pagePosts.filter { it.post.createdAt.isAfter(sinceDateTime) }
            } else {
                pagePosts
            }

            allPosts.addAll(filteredPosts)

            // 期間外の投稿が含まれた場合は終了（制限なし）
            if (sinceDate != null && filteredPosts.size < pagePosts.size) {
                Log.d("BlueskyApi", "期間外投稿検出。取得終了")
                break
            }

            cursor = response.cursor
            delay(100) // API制限対策

        } while (cursor != null && response.feed.isNotEmpty()) // maxPages制限を削除

        // 完了時に100%表示
        onProgress?.invoke(pageCount, pageCount)
        delay(500) // 完了状態を表示

        Log.d("BlueskyApi", "ページネーション完了: 総${allPosts.size}件（${pageCount}ページ）")
        return allPosts
    }

    /**
     * 差分取得のためのsince日時を決定（期間設定対応版）
     */
    private fun determineSinceDate(account: Account.Bluesky): String? {
        return when {
            // 「全期間」の場合は常にnull（制限なし）
            account.period == "全期間" -> {
                if (isPeriodExpanded(account)) {
                    Log.d("BlueskyApi", "期間拡大検出：全データ取得")
                    null
                } else {
                    Log.d("BlueskyApi", "全期間：差分取得")
                    account.lastSyncedAt
                }
            }
            // 期間指定がある場合
            account.period != "全期間" -> {
                val periodSinceDate = when (account.period) {
                    "1ヶ月" -> ZonedDateTime.now().minusMonths(1)
                    "3ヶ月" -> ZonedDateTime.now().minusMonths(3)
                    "6ヶ月" -> ZonedDateTime.now().minusMonths(6)
                    "12ヶ月" -> ZonedDateTime.now().minusMonths(12)
                    "24ヶ月" -> ZonedDateTime.now().minusMonths(24)
                    else -> ZonedDateTime.now().minusMonths(3) // デフォルト3ヶ月
                }.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                // 前回同期時刻と期間指定の新しい方（最近の方）を使用
                account.lastSyncedAt?.let { lastSync ->
                    val lastSyncDateTime = ZonedDateTime.parse(lastSync)
                    val periodDateTime = ZonedDateTime.parse(periodSinceDate)

                    if (lastSyncDateTime.isAfter(periodDateTime)) {
                        Log.d("BlueskyApi", "差分取得: 前回同期時刻($lastSync)を使用")
                        lastSync
                    } else {
                        Log.d("BlueskyApi", "期間変更: 期間指定(${account.period} -> $periodSinceDate)を使用")
                        periodSinceDate
                    }
                } ?: run {
                    Log.d("BlueskyApi", "初回同期: 期間指定(${account.period} -> $periodSinceDate)を使用")
                    periodSinceDate
                }
            }

            // その他の場合（念のため）
            else -> {
                Log.d("BlueskyApi", "フォールバック: 前回同期時刻を使用")
                account.lastSyncedAt
            }
        }
    }

    suspend fun refreshSession(accountToRefresh: Account.Bluesky): Account.Bluesky? {
        println("セッションの更新を試みます...")
        return try {
            val response: LoginResponse = client.post("https://bsky.social/xrpc/com.atproto.server.refreshSession") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${accountToRefresh.refreshToken}")
                }
            }.body()

            // ★ 変更：新しい情報でAccountオブジェクトを再作成
            val refreshedAccount = accountToRefresh.copy(
                accessToken = response.accessJwt,
                refreshToken = response.refreshJwt
            )
            // 新しいアカウント情報で上書き保存
            sessionManager.saveAccount(refreshedAccount)

            println("セッションの更新に成功！新しいアクセストークンを保存しました。")
            refreshedAccount // 更新されたアカウント情報を返す
        } catch (e: Exception) {
            println("セッションの更新に失敗しました: ${e.message}")
            // ★ 変更：削除ではなく要再認証状態にマーク
            sessionManager.markAccountForReauthentication(accountToRefresh.userId)
            null
        }
    }

    /**
     * 期間設定が拡大されたかを判定
     */
    private fun isPeriodExpanded(account: Account.Bluesky): Boolean {
        val currentPeriod = account.period
        val lastPeriod = account.lastPeriodSetting

        Log.d("BlueskyApi", "期間拡大判定: currentPeriod=$currentPeriod, lastPeriod=$lastPeriod, lastSyncedAt=${account.lastSyncedAt}")

        // lastSyncedAtがある場合は初回ではない
        if (lastPeriod == null && account.lastSyncedAt != null) {
            Log.d("BlueskyApi", "既存アカウント（設定変更履歴なし）: 拡大なし")
            return false // 既存アカウントで設定変更履歴がない場合は拡大なし
        }

        if (lastPeriod == null) {
            Log.d("BlueskyApi", "真の初回: 拡大扱い")
            return true // 真の初回は拡大扱い
        }

        val periodToMonths = mapOf(
            "1ヶ月" to 1,
            "3ヶ月" to 3,
            "6ヶ月" to 6,
            "12ヶ月" to 12,
            "24ヶ月" to 24,
            "全期間" to Int.MAX_VALUE
        )

        val currentMonths = periodToMonths[currentPeriod] ?: 3
        val lastMonths = periodToMonths[lastPeriod] ?: 3

        val result = currentMonths > lastMonths
        Log.d("BlueskyApi", "期間比較: $currentMonths > $lastMonths = $result")

        return result
    }
}