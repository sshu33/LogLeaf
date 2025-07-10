package com.example.logleaf.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SNSの種類を定義する列挙型
 */
enum class SnsType(val brandColor: Color) {
    BLUESKY(brandColor = Color(0xFF57dfe9)), // Blueskyのテーマカラー（例：青色）
    MASTODON(brandColor = Color(0xFF9E76B4)), // Mastodonのテーマカラー（例：紫色）
    // 将来的に他のSNSを追加する場合はここに追加する
}