package com.example.logleaf.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 指定されたキーワードをハイライト表示するテキストコンポーネント
 * @param text 表示する全文
 * @param keywordsToHighlight ハイライトするキーワードのリスト
 * @param modifier Modifier
 */
@Composable
fun HighlightedText(
    text: String,
    keywordsToHighlight: List<String>,
    modifier: Modifier = Modifier
) {
    // ハイライト対象のキーワードがない場合、または本文が空の場合は、通常のTextを表示して終了
    if (keywordsToHighlight.isEmpty() || text.isBlank()) {
        Text(text = text, modifier = modifier, maxLines = 3) // 通常は3行表示
        return
    }

    // AnnotatedStringを組み立てる
    val annotatedString = buildAnnotatedString {
        // 元のテキストを保持
        append(text)

        // 各キーワードについてハイライト処理を行う
        keywordsToHighlight.forEach { keyword ->
            // 大文字・小文字を区別せずにキーワードを探す
            var startIndex = text.indexOf(keyword, ignoreCase = true)
            while (startIndex != -1) {
                val endIndex = startIndex + keyword.length
                // 見つかった範囲にスタイルを適用
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        // ★★★ この一行を追加！ ★★★
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                    start = startIndex,
                    end = endIndex
                )
                // 同じキーワードが複数ある場合も探す
                startIndex = text.indexOf(keyword, startIndex + 1, ignoreCase = true)
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        maxLines = 3 // こちらも3行に制限
    )
}