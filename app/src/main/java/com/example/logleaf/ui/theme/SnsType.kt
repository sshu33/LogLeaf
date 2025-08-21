package com.example.logleaf.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SNSの種類を定義する列挙型
 */
enum class SnsType(val brandColor: Color) {
    BLUESKY(brandColor = Color(0xFF5fcfd9)), // Blueskyのテーマカラー
    MASTODON(brandColor = Color(0xFFb589c3)), // Mastodonのテーマカラー
    LOGLEAF(brandColor = Color(0xFFb4d95f)),
    GITHUB(brandColor = Color(0xFF8d8d8d)),  // GitHubの黒
    GOOGLEFIT(brandColor = Color(0xFFffffff)),
    FITBIT(brandColor = Color(0xFFec909f))
}