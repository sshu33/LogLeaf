package com.example.logleaf.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.logleaf.R

// 1. 内蔵したTTFファイルを、直接、Fontファミリーとして定義します。
// これで、もうダウンロードは一切行われません。
val MPlus1 = FontFamily(Font(R.font.m_plus_1))
val NotoSansJp = FontFamily(Font(R.font.noto_sans_jp))
val SawarabiMincho = FontFamily(Font(R.font.sawarabi_mincho))
val DotGothic16 = FontFamily(Font(R.font.dotgothic16))
val YuseiMagic = FontFamily(Font(R.font.yusei_magic))

// 2. フォントの選択肢を管理するデータクラス（変更なし）
data class AppFont(
    val name: String,
    val fontFamily: FontFamily
)

// 3. アプリで利用可能なフォントのリスト（変更なし）
val availableFonts = listOf(
    // デフォルトフォントも、引き続き選択肢の先頭に加えます
    AppFont(name = "Default", fontFamily = FontFamily.Default),
    AppFont(name = "M PLUS 1", fontFamily = MPlus1),
    AppFont(name = "Noto Sans JP", fontFamily = NotoSansJp),
    AppFont(name = "Sawarabi Mincho", fontFamily = SawarabiMincho),
    AppFont(name = "DotGothic16", fontFamily = DotGothic16),
    AppFont(name = "Yusei Magic", fontFamily = YuseiMagic)
)