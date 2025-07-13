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

// ★★★ 不足していたデータクラス ★★★
@Serializable
data class AppRegistrationResponse(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

// ★★★ 今回追加したデータクラス ★★★
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("created_at") val createdAt: Long
)

sealed class MastodonPostResult {
    /** 成功。投稿リストを持つ */
    data class Success(val posts: List<Post>) : MastodonPostResult()

    /** 失敗: トークンが無効になっている (401 Unauthorized) */
    object TokenInvalid : MastodonPostResult()

    /** 失敗: その他のエラー (サーバーエラー、ネットワークエラーなど) */
    data class Error(val message: String) : MastodonPostResult()
}

class MastodonApi {

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
     * 特定のMastodonアカウントの投稿を取得する汎用的なメソッド
     */
    suspend fun getPosts(account: Account.Mastodon): MastodonPostResult {
        // 最初に取得するURL
        var url: String? = "https://${account.instanceUrl}/api/v1/accounts/${account.userId}/statuses?limit=40"
        val allPosts = mutableListOf<Post>()
        var pageCount = 1

        try {
            // urlがnullになるまで（=次のページがなくなるまで）ループ
            while (url != null) {
                Log.d("PAGINATION_DEBUG", "Fetching page $pageCount from: $url")

                val httpResponse: io.ktor.client.statement.HttpResponse = client.get(url) {
                    headers { append(HttpHeaders.Authorization, "Bearer ${account.accessToken}") }
                }

                if (!httpResponse.status.isSuccess()) {
                    // 途中でエラーが起きたら、それまでの結果を返すか、エラーとして処理するか
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

                val posts = mastodonStatuses.map { mastodonStatus ->
                    val sanitizedText = Html.fromHtml(mastodonStatus.content, Html.FROM_HTML_MODE_LEGACY).toString()
                    Post(
                        id = mastodonStatus.id,
                        text = sanitizedText.trim(),
                        createdAt = ZonedDateTime.parse(mastodonStatus.createdAt),
                        source = SnsType.MASTODON
                    )
                }
                allPosts.addAll(posts)

                // --- 次のページのURLを取得 ---
                // 'Link'ヘッダーを解析 (例: <...>; rel="next", <...>; rel="prev")
                val linkHeader = httpResponse.headers["Link"]
                val nextUrlMatch = linkHeader?.split(",")?.find { it.contains("rel=\"next\"") }
                url = nextUrlMatch?.substringAfter("<")?.substringBefore(">")

                pageCount++
                // 念のため、無限ループを防ぐ
                if (pageCount > 25) { // 40件 * 25ページ = 1000件
                    Log.w("PAGINATION_DEBUG", "Reached page limit (25). Stopping.")
                    break
                }
            }

            Log.d("PAGINATION_DEBUG", "Total ${allPosts.size} posts fetched for ${account.displayName}.")
            return MastodonPostResult.Success(allPosts)

        } catch (e: Exception) {
            e.printStackTrace()
            return MastodonPostResult.Error(e.message ?: "不明なネットワークエラー")
        }
    }
}