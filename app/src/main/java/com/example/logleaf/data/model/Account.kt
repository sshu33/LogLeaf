package com.example.logleaf.data.model

import com.example.logleaf.ui.theme.SnsType
import kotlinx.serialization.SerialName
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
    @SerialName("Account.Bluesky")
    data class Bluesky(
        val did: String,
        val handle: String,
        val accessToken: String,
        val refreshToken: String,
        val period: String = "3ヶ月",
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true,
        override val lastSyncedAt: String? = null // ← 追加
    ) : Account() {
        override val snsType: SnsType get() = SnsType.BLUESKY
        override val userId: String get() = did
        override val displayName: String get() = handle
    }

    @Serializable
    @SerialName("Account.Mastodon")
    data class Mastodon(
        val instanceUrl: String,
        val id: String,
        val acct: String,
        val username: String,
        val accessToken: String,
        val clientId: String = "",
        val clientSecret: String = "",
        val period: String = "3ヶ月",
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true,
        override val lastSyncedAt: String? = null // ← 追加
    ) : Account() {
        override val snsType: SnsType get() = SnsType.MASTODON
        override val userId: String get() = id
        override val displayName: String get() = username
    }

    @Serializable
    @SerialName("Account.GitHub")
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
    @SerialName("Account.RepositoryFetchMode")
    enum class RepositoryFetchMode {
        All,    // 全リポジトリから取得
        Selected // 選択したリポジトリのみ取得
    }

    @Serializable
    @SerialName("Account.GoogleFit")
    data class GoogleFit(
        val isConnected: Boolean = true,
        val period: String = "3ヶ月", // ★ 追加
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true,
        override val lastSyncedAt: String? = null
    ) : Account() {
        override val snsType: SnsType get() = SnsType.GOOGLEFIT
        override val userId: String get() = "googlefit_user"
        override val displayName: String get() = "Google Fit"
    }

    @Serializable
    @SerialName("Account.Fitbit")
    data class Fitbit(
        val accessToken: String,
        val refreshToken: String,
        val fitbitUserId: String,  // ← 名前を変更
        val period: String = "3ヶ月",
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true,
        override val lastSyncedAt: String? = null
    ) : Account() {
        override val snsType: SnsType get() = SnsType.FITBIT
        override val userId: String get() = fitbitUserId  // ← これを追加
        override val displayName: String get() = "Fitbit"
    }
}