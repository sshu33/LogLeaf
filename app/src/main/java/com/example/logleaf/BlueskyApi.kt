package com.example.logleaf

import com.example.logleaf.ui.theme.SnsType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime


@Serializable
data class LoginRequest(val identifier: String, val password: String)
@Serializable
data class LoginResponse(val accessJwt: String, val did: String, val refreshJwt: String, val handle: String) // ★ handleを追加
@Serializable
data class BskyFeedResponse(val feed: List<BskyFeedItem>)
@Serializable
data class BskyFeedItem(val post: BskyPost)
@Serializable
data class BskyPost(val uri: String, val record: BskyRecord)
@Serializable
data class BskyRecord(val text: String, val createdAt: String)


class BlueskyApi(private val sessionManager: SessionManager) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // ★ 変更：loginメソッド
    suspend fun login(handle: String, password: String): Boolean {
        return try {
            val response: LoginResponse = client.post("https://bsky.social/xrpc/com.atproto.server.createSession") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(identifier = handle, password = password))
            }.body()

            // ★ 変更：取得した情報からAccount.Blueskyオブジェクトを作成して保存する
            val newAccount = Account.Bluesky(
                did = response.did,
                handle = response.handle, // ★ APIレスポンスからhandleを取得
                accessToken = response.accessJwt,
                refreshToken = response.refreshJwt
            )
            sessionManager.saveAccount(newAccount)

            println("セッションの作成と保存に成功！")
            true
        } catch (e: Exception) {
            println("ログインエラー: ${e.message}")
            false
        }
    }

    suspend fun getPostsForAccount(account: Account.Bluesky): List<Post> {
        try {
            // 1回目の試行
            // ★ 引数で渡されたaccountの情報を直接使う
            return tryToGetPosts(account.accessToken, account.did)
        } catch (e: Exception) {
            println("1回目の投稿取得に失敗しました: ${e.message}")
            // セッション更新を試みる
            val refreshedAccount = refreshSession(account)

            if (refreshedAccount != null) {
                // セッション更新に成功した場合、新しいトークンで再試行
                println("セッション更新成功。投稿を再取得します。")
                try {
                    return tryToGetPosts(refreshedAccount.accessToken, refreshedAccount.did)
                } catch (e2: Exception) {
                    println("再取得にも失敗しました: ${e2.message}")
                    return emptyList()
                }
            } else {
                println("セッション更新に失敗しました。再ログインが必要です。")
                return emptyList()
            }
        }
    }

    // tryToGetPostsメソッド (変更なし)
    private suspend fun tryToGetPosts(token: String, did: String): List<Post> {
        val response: BskyFeedResponse = client.get("https://bsky.social/xrpc/app.bsky.feed.getAuthorFeed") {
            headers { append(HttpHeaders.Authorization, "Bearer $token") }
            parameter("actor", did)
        }.body()

        println("投稿取得成功！ ${response.feed.size}件の投稿を取得しました。")
        return response.feed.map { feedItem ->
            val postRecord = feedItem.post.record
            Post(
                id = feedItem.post.uri,
                createdAt = ZonedDateTime.parse(postRecord.createdAt),
                text = postRecord.text,
                source = SnsType.BLUESKY
            )
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
            // 更新に失敗したアカウントはリストから削除する
            sessionManager.deleteAccount(accountToRefresh)
            null
        }
    }
}