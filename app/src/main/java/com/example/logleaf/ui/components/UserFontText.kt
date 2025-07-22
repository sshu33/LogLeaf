package com.yourpackage.logleaf.ui.components // あなたのパッケージ名に合わせてください

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.example.logleaf.ui.theme.LocalUserFontFamily

/**
 * ユーザー設定のフォントファミリーを自動的に適用する、Textの代替コンポーネント。
 * Jetpack Composeの標準のTextと、ほぼ同じように使用できます。
 *
 * このコンポーネントは、CompositionLocalからユーザー設定のフォントファミリーを取得し、
 * styleプロパティに渡されたTextStyleとマージして、最終的なスタイルを適用します。
 */
@Composable
fun UserFontText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    // CompositionLocalから、現在ユーザーが設定しているFontFamilyを取得します
    val userFontFamily = LocalUserFontFamily.current

    // 引数で渡されたスタイル(style)をベースに、フォントファミリーだけを上書き（マージ）します
    val finalStyle = style.copy(fontFamily = userFontFamily)

    // すべての引数と、最終的に完成したスタイルを、標準のTextコンポーネントに渡します
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = finalStyle
    )
}