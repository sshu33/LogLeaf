package com.example.logleaf

import android.text.Html
import android.util.Log
import com.example.logleaf.ui.theme.SnsType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class AppRegistrationResponse(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("created_at") val createdAt: Long
)

sealed class MastodonPostResult {
    data class Success(val postsWithImages: List<PostWithImageUrls>) : MastodonPostResult() // 変更
    object TokenInvalid : MastodonPostResult()
    data class Error(val message: String) : MastodonPostResult()
}

class MastodonApi(private val sessionManager: SessionManager) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * 指定されたMastodonインスタンスにこのアプリを登録するメソッド
     */
    suspend fun registerApp(instanceUrl: String): AppRegistrationResponse? {
        val trimmedUrl = instanceUrl.trim().removeSuffix("/")
        val registrationUrl = "https://$trimmedUrl/api/v1/apps"

        return try {
            val response: AppRegistrationResponse = client.post(registrationUrl) {
                parameter("client_name", "LogLeaf")
                parameter("scopes", "read:statuses read:accounts")
                parameter("redirect_uris", "logleaf://callback")
                parameter("website", "")
            }.body()
            println("アプリ登録成功: $trimmedUrl")
            response
        } catch (e: Exception) {
            println("アプリ登録失敗 ($trimmedUrl): ${e.message}")
            null
        }
    }

    suspend fun verifyCredentials(instanceUrl: String, accessToken: String): MastodonAccount? {
        val trimmedUrl = instanceUrl.trim().removeSuffix("/")
        val verifyUrl = "https://$trimmedUrl/api/v1/accounts/verify_credentials"

        return try {
            client.get(verifyUrl) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }.body<MastodonAccount>()
        } catch (e: Exception) {
            println("アカウント情報取得失敗 ($trimmedUrl): ${e.message}")
            null
        }
    }

    /**
     * ★★★ 今回追加した関数 ★★★
     * 認可コードを使ってアクセストークンを取得するメソッド
     */
    suspend fun fetchAccessToken(
        instanceUrl: String,
        clientId: String,
        clientSecret: String,
        code: String
    ): TokenResponse? {
        val trimmedUrl = instanceUrl.trim().removeSuffix("/")
        val tokenUrl = "https://${trimmedUrl}/oauth/token"

        try {
            // ★ まずはサーバーからの生の応答を受け取る
            val httpResponse: io.ktor.client.statement.HttpResponse = client.post(tokenUrl) {
                parameter("client_id", clientId)
                parameter("client_secret", clientSecret)
                parameter("redirect_uri", "logleaf://callback")
                parameter("grant_type", "authorization_code")
                parameter("code", code)
                parameter("scope", "read:statuses")
            }

            // ★ 応答のステータスコードで成功か失敗かを判断
            if (httpResponse.status.isSuccess()) {
                // 成功していれば、TokenResponseとして解釈
                val tokenResponse = httpResponse.body<TokenResponse>()
                println("トークン取得成功: $trimmedUrl")
                return tokenResponse
            } else {
                // 失敗していれば、エラーの本文をそのまま表示
                val errorBody = httpResponse.body<String>()
                println("トークン取得失敗 ($trimmedUrl): HTTP Status: ${httpResponse.status}")
                println("サーバーからのエラーメッセージ: $errorBody")
                return null
            }

        } catch (e: Exception) {
            println("トークン取得処理中に予期せぬ例外が発生 ($trimmedUrl): ${e.message}")
            e.printStackTrace()
            return null
        }
    }


    /**
     * 特定のMastodonアカウントの投稿を取得する汎用的なメソッド（差分取得対応版）
     */
    suspend fun getPosts(account: Account.Mastodon): MastodonPostResult {
        // 差分取得のためのsince日時を決定
        val sinceDate = determineSinceDate(account)
        Log.d("MastodonApi", "Mastodonアカウント(${account.username})の差分取得開始: since=$sinceDate")

        // 最初に取得するURL（差分取得パラメータ付き）
        var url: String? = buildInitialUrl(account, sinceDate)
        val allPosts = mutableListOf<PostWithImageUrls>()
        var pageCount = 1

        try {
            // urlがnullになるまで（=次のページがなくなるまで）ループ
            while (url != null) {
                Log.d("MastodonApi", "Fetching page $pageCount from: ${url.take(100)}...")

                val httpResponse: io.ktor.client.statement.HttpResponse = client.get(url) {
                    headers { append(HttpHeaders.Authorization, "Bearer ${account.accessToken}") }
                }

                if (!httpResponse.status.isSuccess()) {
                    return when (httpResponse.status.value) {
                        401 -> MastodonPostResult.TokenInvalid
                        else -> MastodonPostResult.Error("HTTP ${httpResponse.status.value}")
                    }
                }

                val mastodonStatuses = httpResponse.body<List<MastodonStatus>>()

                // もし取得した投稿が空なら、ループを終了
                if (mastodonStatuses.isEmpty()) {
                    break
                }

                val postsWithImages = mastodonStatuses.map { mastodonStatus ->
                    val sanitizedText = Html.fromHtml(mastodonStatus.content, Html.FROM_HTML_MODE_LEGACY).toString()
                    val imageUrls = mastodonStatus.mediaAttachments.filter { it.type == "image" }.map { it.url }
                    val imageUrl = imageUrls.firstOrNull()

                    val post = Post(
                        id = mastodonStatus.id,
                        accountId = account.userId,
                        text = sanitizedText.trim(),
                        createdAt = ZonedDateTime.parse(mastodonStatus.createdAt),
                        imageUrl = imageUrl,
                        source = SnsType.MASTODON
                    )

                    PostWithImageUrls(post = post, imageUrls = imageUrls)
                }

                allPosts.addAll(postsWithImages)

                // 差分取得時の早期終了：since日時よりも古い投稿に達したら停止
                if (sinceDate != null) {
                    val sinceDateTime = ZonedDateTime.parse(sinceDate)
                    val oldestPostInPage = postsWithImages.minByOrNull { it.post.createdAt }
                    if (oldestPostInPage != null && oldestPostInPage.post.createdAt.isBefore(sinceDateTime)) {
                        Log.d("MastodonApi", "差分取得完了: since日時より古い投稿に到達")
                        break
                    }
                }

                // --- 次のページのURLを取得 ---
                val linkHeader = httpResponse.headers["Link"]
                val nextUrlMatch = linkHeader?.split(",")?.find { it.contains("rel=\"next\"") }
                url = nextUrlMatch?.substringAfter("<")?.substringBefore(">")

                pageCount++
                // 念のため、無限ループを防ぐ
                if (pageCount > 25) { // 40件 * 25ページ = 1000件
                    Log.w("MastodonApi", "Reached page limit (25). Stopping.")
                    break
                }
            }

            // 差分取得：since日時以降の投稿のみフィルタリング
            val filteredPosts = if (sinceDate != null) {
                val sinceDateTime = ZonedDateTime.parse(sinceDate)
                val filtered = allPosts.filter { it.post.createdAt.isAfter(sinceDateTime) }
                Log.d("MastodonApi", "差分フィルタリング後: ${filtered.size}件（${allPosts.size}件から絞り込み）")
                filtered
            } else {
                Log.d("MastodonApi", "初回取得: ${allPosts.size}件")
                allPosts
            }

            // 取得成功後、最終同期時刻を更新
            if (filteredPosts.isNotEmpty()) {
                sessionManager.updateLastSyncedAt(account.userId, ZonedDateTime.now())
            } else {
            }

            Log.d("MastodonApi", "Total ${filteredPosts.size} posts fetched for ${account.displayName}.")
            return MastodonPostResult.Success(filteredPosts)

        } catch (e: Exception) {
            e.printStackTrace()
            return MastodonPostResult.Error(e.message ?: "不明なネットワークエラー")
        }
    }

    /**
     * 初期取得URLを構築（差分取得パラメータ付き）
     */
    private fun buildInitialUrl(account: Account.Mastodon, sinceDate: String?): String {
        val baseUrl = "https://${account.instanceUrl}/api/v1/accounts/${account.userId}/statuses"
        return if (sinceDate != null) {
            // Mastodon APIでは since_id パラメータが使用可能
            // ただし、ここでは時刻ベースの簡略実装として min_id を使わずにクライアント側フィルタリング
            "$baseUrl?limit=40"
        } else {
            "$baseUrl?limit=40"
        }
    }

    /**
     * 差分取得のためのsince日時を決定
     */
    private fun determineSinceDate(account: Account.Mastodon): String? {
        return when {
            // 前回同期時刻がある場合：それ以降を取得
            account.lastSyncedAt != null -> {
                Log.d("MastodonApi", "前回同期時刻を使用: ${account.lastSyncedAt}")
                account.lastSyncedAt
            }
            // 初回同期の場合：最近2週間分を取得
            else -> {
                val twoWeeksAgo = ZonedDateTime.now().minusWeeks(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                Log.d("MastodonApi", "初回同期のため2週間前から取得: $twoWeeksAgo")
                twoWeeksAgo
            }
        }
    }
}