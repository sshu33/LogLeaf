package com.example.logleaf

import com.example.logleaf.ui.theme.SnsType
import kotlinx.serialization.Serializable

@Serializable
sealed class Account {
    abstract val snsType: SnsType
    abstract val userId: String
    abstract val displayName: String
    abstract val needsReauthentication: Boolean
    abstract val isVisible: Boolean
    abstract val lastSyncedAt: String? // ← 全Accountの共通プロパティとして追加

    @Serializable
    data class Bluesky(
        val did: String,
        val handle: String,
        val accessToken: String,
        val refreshToken: String,
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true,
        override val lastSyncedAt: String? = null // ← 追加
    ) : Account() {
        override val snsType: SnsType get() = SnsType.BLUESKY
        override val userId: String get() = did
        override val displayName: String get() = handle
    }

    @Serializable
    data class Mastodon(
        val instanceUrl: String,
        val id: String,
        val acct: String,
        val username: String,
        val accessToken: String,
        val clientId: String = "",
        val clientSecret: String = "",
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true,
        override val lastSyncedAt: String? = null // ← 追加
    ) : Account() {
        override val snsType: SnsType get() = SnsType.MASTODON
        override val userId: String get() = id
        override val displayName: String get() = username
    }

    @Serializable
    data class GitHub(
        val username: String,
        val accessToken: String,
        val period: String = "3ヶ月",
        val repositoryFetchMode: RepositoryFetchMode = RepositoryFetchMode.All,
        val selectedRepositories: List<String> = emptyList(),
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true,
        override val lastSyncedAt: String? = null // ← 追加
    ) : Account() {
        override val snsType: SnsType get() = SnsType.GITHUB
        override val userId: String get() = username
        override val displayName: String get() = username
    }

    /**
     * リポジトリ取得モード
     */
    @Serializable
    enum class RepositoryFetchMode {
        All,    // 全リポジトリから取得
        Selected // 選択したリポジトリのみ取得
    }
}