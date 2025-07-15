package com.example.logleaf.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight // ★ FontWeightをインポート
import com.example.logleaf.R

// --- 個別のフォントファミリーの定義（変更なし） ---
val MPlus1 = FontFamily(Font(R.font.m_plus_1))
val NotoSansJp = FontFamily(Font(R.font.noto_sans_jp))
val SawarabiMincho = FontFamily(Font(R.font.sawarabi_mincho))
val DotGothic16 = FontFamily(Font(R.font.dotgothic16))
val YuseiMagic = FontFamily(Font(R.font.yusei_magic))


// ★★★ 1. AppFontデータクラスに、fontWeightプロパティを追加 ★★★
data class AppFont(
    val name: String,
    val fontFamily: FontFamily,
    // このフォントを使う時の、推奨される「太さ」を定義する
    val fontWeight: FontWeight = FontWeight.Normal // デフォルトは「Normal」
)

// 3. アプリで利用可能なフォントのリスト（変更なし）
val availableFonts = listOf(
    AppFont(name = "Default", fontFamily = FontFamily.Default),
    AppFont(name = "M PLUS 1", fontFamily = MPlus1),
    AppFont(
        name = "Noto Sans JP",
        fontFamily = NotoSansJp,
        fontWeight = FontWeight.Bold
    ),
    AppFont(name = "Sawarabi Mincho", fontFamily = SawarabiMincho),
    AppFont(name = "DotGothic16", fontFamily = DotGothic16),
    AppFont(name = "Yusei Magic", fontFamily = YuseiMagic)
)