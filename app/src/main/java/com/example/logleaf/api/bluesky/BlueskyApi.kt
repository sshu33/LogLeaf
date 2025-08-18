package com.example.logleaf.api.bluesky

import android.util.Log
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


@Serializable
data class LoginRequest(val identifier: String, val password: String)
@Serializable
data class LoginResponse(val accessJwt: String, val did: String, val refreshJwt: String, val handle: String)
@Serializable
data class BskyFeedResponse(val feed: List<BskyFeedItem>)
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
    private val client = HttpClient(CIO) {
        // JSONの変換設定 (これは元々ありました)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        // ネットワークログを出力するための設定
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v("KtorLogger", message) // Logcatのタグを"KtorLogger"に
                }
            }
            level = LogLevel.ALL // ヘッダーやボディを含むすべての情報をログに出力
        }
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
            // 差分取得のためのsince日時を決定
            val sinceDate = determineSinceDate(account)
            Log.d("BlueskyApi", "Blueskyアカウント(${account.handle})の差分取得開始: since=$sinceDate")

            // 1回目の試行
            val posts = tryToGetPosts(account.accessToken, account.did, account.userId, sinceDate)

            // 取得成功後、最終同期時刻を更新
            if (posts.isNotEmpty()) {
                sessionManager.updateLastSyncedAt(account.userId, ZonedDateTime.now())
                Log.d("BlueskyApi", "Blueskyアカウント(${account.handle})の同期時刻を更新")
            }

            return posts

        } catch (e: Exception) {
            Log.e("BlueskyApi", "1回目の投稿取得に失敗しました: ${e.message}")
            val refreshedAccount = refreshSession(account)

            return if (refreshedAccount != null) {
                Log.d("BlueskyApi", "セッション更新成功。投稿を再取得します。")
                try {
                    val sinceDate = determineSinceDate(refreshedAccount)
                    val posts = tryToGetPosts(refreshedAccount.accessToken, refreshedAccount.did, refreshedAccount.userId, sinceDate)

                    // 再取得成功後も同期時刻を更新
                    if (posts.isNotEmpty()) {
                        sessionManager.updateLastSyncedAt(refreshedAccount.userId, ZonedDateTime.now())
                        Log.d("BlueskyApi", "Blueskyアカウント(${refreshedAccount.handle})の同期時刻を更新（再取得後）")
                    }

                    posts
                } catch (e2: Exception) {
                    Log.e("BlueskyApi", "再取得にも失敗しました: ${e2.message}")
                    emptyList()
                }
            } else {
                Log.e("BlueskyApi", "セッション更新に失敗しました。再ログインが必要です。")
                emptyList()
            }
        }
    }

    private suspend fun tryToGetPosts(token: String, did: String, accountId: String, sinceDate: String?): List<PostWithImageUrls> {
        val response: BskyFeedResponse = client.get("https://bsky.social/xrpc/app.bsky.feed.getAuthorFeed") {
            headers { append(HttpHeaders.Authorization, "Bearer $token") }
            parameter("actor", did)
            parameter("limit", 100) // 取得件数の上限を最大値の100に設定

            // 差分取得：since日時が指定されている場合のみ追加
            // 注：BlueskyのAPIでは実際には cursor ベースのページネーションが主流
            // ここでは簡略化してlimitで制限しているが、本格実装では cursor も考慮
        }.body()

        Log.d("BlueskyApi", "Bluesky投稿取得: ${response.feed.size}件（since=$sinceDate）")

        val allPosts = response.feed.map { feedItem ->
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

        // 差分取得：since日時以降の投稿のみフィルタリング
        return if (sinceDate != null) {
            val sinceDateTime = ZonedDateTime.parse(sinceDate)
            val filteredPosts = allPosts.filter { it.post.createdAt.isAfter(sinceDateTime) }
            Log.d("BlueskyApi", "差分フィルタリング後: ${filteredPosts.size}件（${allPosts.size}件から絞り込み）")
            filteredPosts
        } else {
            Log.d("BlueskyApi", "初回取得: ${allPosts.size}件")
            allPosts
        }
    }

    /**
     * 差分取得のためのsince日時を決定（期間設定対応版）
     */
    private fun determineSinceDate(account: Account.Bluesky): String? {
        return when {
            // 「全期間」の場合は常にnull（制限なし）
            account.period == "全期間" -> {
                Log.d("BlueskyApi", "全期間設定：前回同期時刻(${account.lastSyncedAt})を使用")
                account.lastSyncedAt
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
}