package com.example.logleaf

import android.util.Log
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
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GitHubApi(private val sessionManager: SessionManager) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v("GitHubApiLogger", message)
                }
            }
            level = LogLevel.ALL
        }
    }

    /**
     * アクセストークンを検証し、ユーザー情報を取得する
     */
    suspend fun validateToken(accessToken: String): GitHubUser? {
        return try {
            val user: GitHubUser = client.get("https://api.github.com/user") {
                headers {
                    append(HttpHeaders.Authorization, "token $accessToken")
                    append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
            }.body()

            Log.d("GitHubApi", "ユーザー情報取得成功: ${user.login}")
            user
        } catch (e: Exception) {
            Log.e("GitHubApi", "ユーザー情報取得失敗: ${e.message}")
            null
        }
    }

    /**
     * GitHubアカウントを保存する（期間対応版）
     */
    suspend fun saveAccount(user: GitHubUser, accessToken: String, period: String = "3ヶ月"): Boolean {
        return try {
            val newAccount = Account.GitHub(
                username = user.login,
                accessToken = accessToken,
                period = period // ← 追加！
            )
            sessionManager.saveAccount(newAccount)

            Log.d("GitHubApi", "GitHubアカウント保存成功: ${user.login}, 期間: $period")
            true
        } catch (e: Exception) {
            Log.e("GitHubApi", "GitHubアカウント保存失敗: ${e.message}")
            false
        }
    }

    /**
     * 期間文字列を月数に変換（24ヶ月対応版）
     */
    private fun periodToMonths(period: String): Int? {
        return when (period) {
            "1ヶ月" -> 1
            "3ヶ月" -> 3
            "6ヶ月" -> 6
            "12ヶ月" -> 12
            "24ヶ月" -> 24  // ← 追加！
            "全期間" -> null
            else -> 3
        }
    }

    /**
     * GitHubアカウントの活動を取得（軽量化版）
     */
    suspend fun getPostsForAccount(account: Account.GitHub, period: String = "3ヶ月"): List<PostWithImageUrls> {
        val months = periodToMonths(period)
        val sinceDate = months?.let {
            ZonedDateTime.now().minusMonths(it.toLong()).format(DateTimeFormatter.ISO_INSTANT)
        }

        return try {
            val events: List<GitHubEvent> = client.get("https://api.github.com/users/${account.username}/events") {
                headers {
                    append(HttpHeaders.Authorization, "token ${account.accessToken}")
                    append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
                parameter("per_page", if (months == null) 100 else 50)
                sinceDate?.let { parameter("since", it) } // 期間指定
            }.body()

            Log.d("GitHubApi", "期間: $period - イベント取得成功: ${events.size}件")

            // 軽量化：個別API呼び出しを削除し、順次処理で安全に
            val allPosts = mutableListOf<PostWithImageUrls>()
            events.forEach { event ->
                when (event.type) {
                    "PushEvent" -> {
                        val commits = event.payload.commits ?: emptyList()
                        commits.take(2).forEach { commit ->
                            // 個別API呼び出しを削除し、イベント情報から直接作成
                            val post = createSimpleCommitPost(event, commit, account.username)
                            allPosts.add(post)
                        }
                    }

                    "ReleaseEvent" -> {
                        val release = event.payload.release
                        if (release != null) {
                            allPosts.add(createReleasePost(event, release, account.username))
                        }
                    }

                    "CreateEvent" -> {
                        if (event.payload.refType == "repository") {
                            allPosts.add(createRepoPost(event, account.username))
                        }
                    }
                }
            }

            allPosts.sortedByDescending { it.post.createdAt }

        } catch (e: Exception) {
            Log.e("GitHubApi", "GitHub活動取得失敗: ${e.message}")
            emptyList()
        }
    }

    /**
     * 軽量版：イベント情報からコミット投稿を作成（個別API呼び出しなし）
     */
    private fun createSimpleCommitPost(event: GitHubEvent, commit: GitHubCommit, accountId: String): PostWithImageUrls {
        val repoName = event.repo.name.substringAfter("/")
        val text = buildString {
            append("$repoName にコミット")
            appendLine()
            append("「${commit.message}」")
        }

        val post = Post(
            id = "github_commit_${commit.sha}",
            accountId = accountId,
            text = text,
            createdAt = ZonedDateTime.parse(
                event.createdAt, // イベント時刻を使用（個別取得不要）
                DateTimeFormatter.ISO_OFFSET_DATE_TIME
            ),
            source = SnsType.GITHUB,
            imageUrl = null
        )

        return PostWithImageUrls(post = post, imageUrls = emptyList())
    }

    /**
     * リリース投稿を作成
     */
    private fun createReleasePost(event: GitHubEvent, release: GitHubRelease, accountId: String): PostWithImageUrls {
        val repoName = event.repo.name.substringAfter("/")
        val text = buildString {
            append("$repoName ${release.tagName} リリース")
            if (!release.name.isNullOrBlank()) {
                appendLine()
                append("「${release.name}」")
            }
        }

        val post = Post(
            id = "github_release_${release.id}",
            accountId = accountId,
            text = text,
            createdAt = ZonedDateTime.parse(
                release.publishedAt,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME
            ),
            source = SnsType.GITHUB,
            imageUrl = null
        )

        return PostWithImageUrls(post = post, imageUrls = emptyList())
    }

    /**
     * リポジトリ作成投稿を作成
     */
    private fun createRepoPost(event: GitHubEvent, accountId: String): PostWithImageUrls {
        val repoName = event.repo.name.substringAfter("/")
        val text = "新しいリポジトリ「$repoName」を作成"

        val post = Post(
            id = "github_create_${event.id}",
            accountId = accountId,
            text = text,
            createdAt = ZonedDateTime.parse(
                event.createdAt,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME
            ),
            source = SnsType.GITHUB,
            imageUrl = null
        )

        return PostWithImageUrls(post = post, imageUrls = emptyList())
    }
}