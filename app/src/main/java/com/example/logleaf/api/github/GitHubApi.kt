package com.example.logleaf.api.github

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
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
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
     * GitHubアカウントの活動を取得（統一処理版）
     */
    suspend fun getPostsForAccount(account: Account.GitHub, period: String = "3ヶ月"): List<PostWithImageUrls> {
        return when (account.repositoryFetchMode) {
            Account.RepositoryFetchMode.All -> {
                // 全リポジトリの場合：リポジトリ一覧を取得して全部処理
                Log.d("GitHubApi", "全リポジトリモード：リポジトリ一覧を取得中...")
                val allRepos = getUserRepositories(account.accessToken)
                val repoNames = allRepos.map { it.fullName }
                Log.d("GitHubApi", "取得したリポジトリ数: ${repoNames.size}")

                getPostsFromRepositories(account, period, repoNames)
            }
            Account.RepositoryFetchMode.Selected -> {
                // 選択リポジトリの場合：既存処理
                getPostsFromRepositories(account, period, account.selectedRepositories)
            }
        }
    }

    /**
     * 指定されたリポジトリ群からコミットを取得（共通処理）
     */
    private suspend fun getPostsFromRepositories(account: Account.GitHub, period: String, repoNames: List<String>): List<PostWithImageUrls> {
        if (repoNames.isEmpty()) {
            Log.d("GitHubApi", "対象リポジトリが空のため、取得をスキップ")
            return emptyList()
        }

        // 差分取得のためのsince日時を決定
        val sinceDate = determineSinceDate(account, period)

        Log.d("GitHubApi", "GitHubアカウント(${account.username})のコミット取得開始: since=$sinceDate, 対象リポジトリ数: ${repoNames.size}")

        return try {
            val allPosts = mutableListOf<PostWithImageUrls>()

            repoNames.forEach { repoFullName ->
                try {
                    val commits = getRepositoryCommits(account.accessToken, repoFullName, sinceDate)
                    allPosts.addAll(commits)
                    Log.d("GitHubApi", "$repoFullName から ${commits.size}件のコミットを取得")
                } catch (e: Exception) {
                    Log.e("GitHubApi", "$repoFullName の取得に失敗: ${e.message}")
                }
            }

            // 取得成功後、最終同期時刻を更新
            if (allPosts.isNotEmpty()) {
                sessionManager.updateLastSyncedAt(account.userId, ZonedDateTime.now())
                Log.d("GitHubApi", "GitHubアカウント(${account.username})の同期時刻を更新")
            }

            Log.d("GitHubApi", "全リポジトリから合計 ${allPosts.size}件の投稿を取得")
            allPosts.sortedByDescending { it.post.createdAt }

        } catch (e: Exception) {
            Log.e("GitHubApi", "リポジトリからの取得失敗: ${e.message}")
            emptyList()
        }
    }

    /**
     * 従来の方法：イベントAPIから全活動取得
     */
    private suspend fun getPostsFromEvents(account: Account.GitHub, period: String): List<PostWithImageUrls> {
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
                parameter("per_page", 100)
                // sinceは効かないので削除
            }.body()

            Log.d("GitHubApi", "期間: $period - イベント取得成功: ${events.size}件")

            val allPosts = mutableListOf<PostWithImageUrls>()
            events.forEach { event ->
                when (event.type) {
                    "PushEvent" -> {
                        val commits = event.payload.commits ?: emptyList()
                        commits.take(2).forEach { commit ->
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
     * 新しい方法：選択リポジトリから差分取得
     */
    private suspend fun getPostsFromSelectedRepositories(account: Account.GitHub, period: String): List<PostWithImageUrls> {
        if (account.selectedRepositories.isEmpty()) {
            Log.d("GitHubApi", "選択リポジトリが空のため、取得をスキップ")
            return emptyList()
        }

        // 差分取得のためのsince日時を決定
        val sinceDate = determineSinceDate(account, period)

        Log.d("GitHubApi", "GitHubアカウント(${account.username})の差分取得開始: since=$sinceDate")

        return try {
            val allPosts = mutableListOf<PostWithImageUrls>()

            account.selectedRepositories.forEach { repoFullName ->
                try {
                    val commits = getRepositoryCommits(account.accessToken, repoFullName, sinceDate)
                    allPosts.addAll(commits)
                    Log.d("GitHubApi", "$repoFullName から ${commits.size}件のコミットを取得")
                } catch (e: Exception) {
                    Log.e("GitHubApi", "$repoFullName の取得に失敗: ${e.message}")
                }
            }

            Log.d("GitHubApi", "選択リポジトリから合計 ${allPosts.size}件の投稿を取得")
            allPosts.sortedByDescending { it.post.createdAt }

        } catch (e: Exception) {
            Log.e("GitHubApi", "選択リポジトリからの取得失敗: ${e.message}")
            emptyList()
        }
    }

    /**
     * 差分取得のためのsince日時を決定
     */
    private fun determineSinceDate(account: Account.GitHub, period: String): String? {
        return when {
            // 前回同期時刻がある場合：それ以降を取得
            account.lastSyncedAt != null -> {
                Log.d("GitHubApi", "前回同期時刻を使用: ${account.lastSyncedAt}")
                account.lastSyncedAt
            }
            // 初回同期の場合：期間指定を使用
            else -> {
                val months = periodToMonths(period)
                val sinceDate = months?.let {
                    ZonedDateTime.now().minusMonths(it.toLong()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
                Log.d("GitHubApi", "初回同期のため期間指定を使用: $period -> since=$sinceDate")
                sinceDate
            }
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
     * ユーザーのリポジトリ一覧を取得
     */
    suspend fun getUserRepositories(accessToken: String): List<GitHubRepository> {
        return try {
            val repositories: List<GitHubRepository> = client.get("https://api.github.com/user/repos") {
                headers {
                    append(HttpHeaders.Authorization, "token $accessToken")
                    append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                }
                parameter("type", "all") // public, private, all
                parameter("sort", "updated") // updated, created, pushed, full_name
                parameter("per_page", 100)
            }.body()

            Log.d("GitHubApi", "リポジトリ取得成功: ${repositories.size}件")
            repositories

        } catch (e: Exception) {
            Log.e("GitHubApi", "リポジトリ取得失敗: ${e.message}")
            emptyList()
        }
    }

    /**
     * 指定リポジトリのコミット履歴を取得（ページネーション対応版）
     */
    suspend fun getRepositoryCommits(
        accessToken: String,
        repoFullName: String,
        since: String? = null
    ): List<PostWithImageUrls> {
        return try {
            Log.d("GitHubApi", "$repoFullName の取得パラメータ: since=$since, ページネーション有効")

            val allCommits = mutableListOf<GitHubCommitDetail>()
            var currentPage = 1
            var hasNextPage = true

            while (hasNextPage) {
                Log.d("GitHubApi", "$repoFullName ページ $currentPage を取得中...")

                val response = client.get("https://api.github.com/repos/$repoFullName/commits") {
                    headers {
                        append(HttpHeaders.Authorization, "token $accessToken")
                        append(HttpHeaders.Accept, "application/vnd.github.v3+json")
                    }
                    parameter("per_page", 100)
                    parameter("page", currentPage)
                    since?.let { parameter("since", it) }
                }

                val commits: List<GitHubCommitDetail> = response.body()
                allCommits.addAll(commits)

                Log.d("GitHubApi", "$repoFullName ページ $currentPage: ${commits.size}件取得（累計: ${allCommits.size}件）")

                // Linkヘッダーから次のページがあるかチェック
                val linkHeader = response.headers["Link"]
                hasNextPage = linkHeader?.contains("rel=\"next\"") == true

                // 空のページまたは100件未満なら終了
                if (commits.isEmpty() || commits.size < 100) {
                    hasNextPage = false
                }

                currentPage++

                // 安全装置：10ページ（1000件）で制限
                if (currentPage > 10) {
                    Log.w("GitHubApi", "$repoFullName: 10ページ制限に到達、取得を停止")
                    break
                }
            }

            Log.d("GitHubApi", "$repoFullName のコミット取得完了: 全${allCommits.size}件")

            allCommits.map { commitDetail ->
                val repoName = repoFullName.substringAfter("/")
                val text = buildString {
                    append("$repoName にコミット")
                    appendLine()
                    append("「${commitDetail.commit.message}」")
                }

                val post = Post(
                    id = "github_commit_${commitDetail.sha}",
                    accountId = repoFullName.substringBefore("/"),
                    text = text,
                    createdAt = ZonedDateTime.parse(
                        commitDetail.commit.author.date,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    ),
                    source = SnsType.GITHUB,
                    imageUrl = null
                )

                PostWithImageUrls(post = post, imageUrls = emptyList())
            }

        } catch (e: Exception) {
            Log.e("GitHubApi", "$repoFullName のコミット取得失敗: ${e.message}")
            emptyList()
        }
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