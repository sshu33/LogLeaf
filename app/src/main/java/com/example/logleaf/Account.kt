package com.example.logleaf

import com.example.logleaf.ui.theme.SnsType
import kotlinx.serialization.Serializable

@Serializable
sealed class Account {
    // すべてのアカウントが共通して持つプロパティ
    abstract val snsType: SnsType
    abstract val userId: String // 各SNSでのユニークなID（BlueskyならDID, Mastodonならacctなど）
    abstract val displayName: String // UIに表示するための名前（Blueskyならhandle, Mastodonならusername）
    abstract val needsReauthentication: Boolean
    abstract val isVisible: Boolean // 追加：アカウントの表示/非表示フラグ

    @Serializable
    data class Bluesky(
        val did: String,
        val handle: String,
        val accessToken: String,
        val refreshToken: String,
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true //  追加 (デフォルトは true)
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
        override val isVisible: Boolean = true //  追加 (デフォルトは true)
    ) : Account() {
        override val snsType: SnsType get() = SnsType.MASTODON
        override val userId: String get() = id
        override val displayName: String get() = username
    }

    @Serializable
    data class GitHub(
        val username: String,
        val accessToken: String,
        override val needsReauthentication: Boolean = false,
        override val isVisible: Boolean = true
    ) : Account() {
        override val snsType: SnsType get() = SnsType.GITHUB
        override val userId: String get() = username
        override val displayName: String get() = username
    }
}