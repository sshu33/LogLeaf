package com.example.logleaf.ui.settings.font // あなたのパッケージ名に合わせてください

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.logleaf.R // あなたのRファイルのパッケージ名に合わせてください

// --- 状態定義 (変更なし) ---
enum class FontStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    INTERNAL // ★★★ 内部リソースであることを示す、新しい状態を追加 ★★★
}

// --- データ構造定義 (ハイブリッド対応) ---
data class AppFont(
    val name: String,
    val fontWeight: FontWeight = FontWeight.Normal,

    // ★★★ ここからが、ハイブリッドシステムの心臓部 ★★★
    val sourceType: FontSourceType, // このフォントが、どこから来るのか

    // --- 状態管理のためのプロパティ ---
    var status: FontStatus,
    var fontFamily: FontFamily = FontFamily.Default,
    var downloadId: Long = -1L
)

// ★★★ フォントの「出どころ」を定義するための sealed class ★★★
sealed class FontSourceType {
    // 内部リソースの場合：フォントリソースのIDを持つ
    data class Internal(val resourceId: Int) : FontSourceType()
    // ダウンロード対象の場合：ダウンロードURLを持つ
    data class Downloadable(val url: String) : FontSourceType()
}