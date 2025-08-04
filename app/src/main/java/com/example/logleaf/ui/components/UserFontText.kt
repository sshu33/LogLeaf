package com.yourpackage.logleaf.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.example.logleaf.ui.theme.LocalUserFontFamily
import java.util.regex.Pattern


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



@Composable
fun AutoSizeUserFontText(
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
    style: TextStyle = LocalTextStyle.current,
    autoSize: TextAutoSize? = null // 新しいパラメータ
) {
    // CompositionLocalから、現在ユーザーが設定しているFontFamilyを取得します
    val userFontFamily = LocalUserFontFamily.current

    // 引数で渡されたスタイル(style)をベースに、フォントファミリーだけを上書き（マージ）します
    val finalStyle = style.copy(fontFamily = userFontFamily)

    // BasicTextを使用（autoSizeパラメータが使用可能）
    if (autoSize != null) {
        // autoSize使用時
        BasicText(
            text = text,
            modifier = modifier,
            style = finalStyle.copy(
                color = if (color != Color.Unspecified) color else finalStyle.color,
                fontSize = if (fontSize != TextUnit.Unspecified) fontSize else finalStyle.fontSize,
                fontStyle = fontStyle ?: finalStyle.fontStyle,
                fontWeight = fontWeight ?: finalStyle.fontWeight,
                textAlign = textAlign ?: finalStyle.textAlign,
                lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else finalStyle.lineHeight
            ),
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            autoSize = autoSize
        )
    } else {
        // 通常のText使用時（既存と同じ）
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
}

@Composable
fun HyperlinkUserFontText(
    fullText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    // CompositionLocalから、現在ユーザーが設定しているFontFamilyを取得
    val userFontFamily = LocalUserFontFamily.current

    // 引数で渡されたスタイルをベースに、フォントファミリーを上書き（UserFontTextの機能）
    val finalStyle = style.copy(fontFamily = userFontFamily)

    val uriHandler = LocalUriHandler.current
    val urlPattern = Pattern.compile(
        "(https?://|www\\.)[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"
    )

    val annotatedString = buildAnnotatedString {
        append(fullText)
        val matcher = urlPattern.matcher(fullText)

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val url = matcher.group()

            addStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = "URL",
                annotation = url,
                start = start,
                end = end
            )
        }
    }

    ClickableText(
        text = annotatedString,
        style = finalStyle, // ユーザーフォント適用済みのスタイルを使用
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    // URLの先頭に "http" がない場合（例: www.google.com）に追加
                    var url = annotation.item
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    uriHandler.openUri(url)
                }
        }
    )
}