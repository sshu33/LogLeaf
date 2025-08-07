package com.example.logleaf.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SNSの種類を定義する列挙型
 */
enum class SnsType(val brandColor: Color) {
    BLUESKY(brandColor = Color(0xFF5fcfd9)), // Blueskyのテーマカラー（例：青色）
    MASTODON(brandColor = Color(0xFFb589c3)), // Mastodonのテーマカラー（例：紫色）
    LOGLEAF(brandColor = Color(0xFFb4d95f)),
    GITHUB(brandColor = Color(0xFF878787))  // GitHubの黒
}