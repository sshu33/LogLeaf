package com.example.logleaf

import com.example.logleaf.ui.theme.SnsType
import kotlinx.serialization.Serializable

/**
 * アプリで管理するSNSアカウント情報を表現する、型安全なクラス。
 *
 * `Serializable` をつけることで、このクラスのオブジェクトをJSONに変換したり、
 * JSONから復元したりできるようになる。
 */
@Serializable
sealed class Account {
    // すべてのアカウントが共通して持つプロパティ
    abstract val snsType: SnsType
    abstract val userId: String // 各SNSでのユニークなID（BlueskyならDID, Mastodonならacctなど）
    abstract val displayName: String // UIに表示するための名前（Blueskyならhandle, Mastodonならusername）
    abstract val needsReauthentication: Boolean

    @Serializable
    data class Bluesky(
        val did: String,
        val handle: String,
        val accessToken: String,
        val refreshToken: String,
        // ★★★ プロパティを追加し、デフォルト値を与える ★★★
        override val needsReauthentication: Boolean = false
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
        // ★★★ プロパティを追加し、デフォルト値を与える ★★★
        override val needsReauthentication: Boolean = false
    ) : Account() {
        override val snsType: SnsType get() = SnsType.MASTODON
        override val userId: String get() = id
        override val displayName: String get() = username
    }
}