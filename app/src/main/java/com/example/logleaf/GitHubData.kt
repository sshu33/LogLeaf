package com.example.logleaf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHubのユーザー情報
 */
@Serializable
data class GitHubUser(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("name") val name: String?
)

/**
 * GitHubのイベント情報（活動履歴）
 */
@Serializable
data class GitHubEvent(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String, // "PushEvent", "CreateEvent", "ReleaseEvent" など
    @SerialName("created_at") val createdAt: String,
    @SerialName("repo") val repo: GitHubRepo,
    @SerialName("payload") val payload: GitHubEventPayload
)

@Serializable
data class GitHubRepo(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String, // "username/repository"
    @SerialName("url") val url: String
)

/**
 * イベントのペイロード（種類によって内容が異なる）
 */
@Serializable
data class GitHubEventPayload(
    // Push Event
    @SerialName("commits") val commits: List<GitHubCommit>? = null,
    @SerialName("size") val size: Int? = null, // コミット数

    // Release Event
    @SerialName("release") val release: GitHubRelease? = null,

    // Create Event
    @SerialName("ref") val ref: String? = null, // ブランチ/タグ名
    @SerialName("ref_type") val refType: String? = null // "branch" or "tag"
)

@Serializable
data class GitHubCommit(
    @SerialName("sha") val sha: String,
    @SerialName("message") val message: String,
    @SerialName("url") val url: String
)

@Serializable
data class GitHubRelease(
    @SerialName("id") val id: Long,
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String?,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("html_url") val htmlUrl: String
)

/**
 * 個別コミットの詳細情報（正確な時刻付き）
 */
@Serializable
data class GitHubCommitDetail(
    @SerialName("sha") val sha: String,
    @SerialName("commit") val commit: GitHubCommitInfo,
    @SerialName("author") val author: GitHubCommitAuthor?
)

@Serializable
data class GitHubCommitInfo(
    @SerialName("message") val message: String,
    @SerialName("author") val author: GitHubCommitUser,
    @SerialName("committer") val committer: GitHubCommitUser
)

@Serializable
data class GitHubCommitUser(
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("date") val date: String // ← これが正確なコミット時刻！
)

@Serializable
data class GitHubCommitAuthor(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long
)