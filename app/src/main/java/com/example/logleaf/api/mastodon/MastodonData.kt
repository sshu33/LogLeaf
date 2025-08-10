package com.example.logleaf.api.mastodon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mastodonの投稿（Status）一つを表すデータクラス
 * APIレスポンスのJSONに対応
 */
@Serializable
data class MastodonStatus(
    @SerialName("id") val id: String,
    @SerialName("content") val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("media_attachments") val mediaAttachments: List<MastodonMediaAttachment>
)

@Serializable
data class MastodonMediaAttachment(
    @SerialName("type") val type: String, // "image", "video" など
    @SerialName("url") val url: String     // ◀◀◀ これが画像のURL
)

@Serializable
data class MastodonAccount(
    @SerialName("id") val id: String,
    @SerialName("acct") val acct: String, // ★ この行を追加
    @SerialName("username") val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar") val avatarUrl: String
)