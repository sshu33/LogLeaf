package com.example.logleaf.ui.components

import android.net.Uri
import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.logleaf.utils.UrlMetadata
import com.example.logleaf.utils.UrlMetadataExtractor
import com.yourpackage.logleaf.ui.components.HyperlinkUserFontText
import java.util.regex.Pattern

@Composable
fun UrlPreviewText(
    fullText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    // URLを検出
    val detectedUrls = remember(fullText) {
        extractUrlsFromText(fullText)
    }

    Column(modifier = modifier) {
        // テキスト部分（ハイパーリンク付き）
        HyperlinkUserFontText(
            fullText = fullText,
            style = style,
            linkColor = linkColor
        )

        // URLプレビューカード（URLが見つかった場合のみ）
        detectedUrls.forEach { url ->
            UrlPreviewCardWithData(
                url = url,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun UrlPreviewCardWithData(
    url: String,
    modifier: Modifier = Modifier
) {
    var metadata by remember { mutableStateOf(UrlMetadata(url = url, isLoading = true)) }

    // メタデータを非同期で取得
    LaunchedEffect(url) {
        metadata = UrlMetadataExtractor.extractMetadata(url)
    }

    // ローディング中やエラー時は表示しない
    if (metadata.isLoading || metadata.hasError) {
        return
    }

    // メタデータが取得できた場合のみプレビューカードを表示
    if (metadata.title != null || metadata.description != null || metadata.imageUrl != null) {
        UrlPreviewCard(
            url = metadata.url,
            title = metadata.title,
            description = metadata.description,
            imageUrl = metadata.imageUrl,
            siteName = metadata.siteName,
            modifier = modifier
        )
    }
}

@Composable
fun UrlPreviewCard(
    url: String,
    title: String?,
    description: String?,
    imageUrl: String?,
    siteName: String?,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    // URLのドメイン部分を抽出
    val domain = try {
        val uri = Uri.parse(if (!url.startsWith("http")) "https://$url" else url)
        uri.host ?: url
    } catch (e: Exception) {
        url
    }

    // 画像ポストのサムネイルと同じ高さ（80dp）に設定
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                Color.Gray.copy(alpha = 0.1f), // うっすいグレー
                RoundedCornerShape(8.dp)
            )
            .clickable {
                // プレビューカードをタップでURLを開く
                val finalUrl = if (!url.startsWith("http")) "https://$url" else url
                uriHandler.openUri(finalUrl)
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左1/3: 画像エリア
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "プレビュー画像",
                modifier = Modifier
                    .width(80.dp) // 横長
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            // 画像がない場合のプレースホルダー
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(56.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "URLプレビュー",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 右2/3: 情報エリア
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // サイト名/ドメイン
                Text(
                    text = siteName ?: domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // タイトル
                if (!title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 説明（下部に配置）
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * テキストからURLを抽出する関数（修正版）
 */
private fun extractUrlsFromText(text: String): List<String> {
    val urls = mutableListOf<String>()

    // より厳密なURL検出パターン
    val urlPatterns = listOf(
        // 1. 完全なURL（http/https付き）- これを最優先
        Pattern.compile("https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"),

        // 2. www.で始まるURL - store-jp.nintendo.comを正しく検出
        Pattern.compile("\\bwww\\.[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"),

        // 3. 短縮URL（特定ドメインのみ）
        Pattern.compile("\\b(?:t\\.co|bit\\.ly|tinyurl\\.com|ow\\.ly|goo\\.gl|youtu\\.be|amzn\\.to|tiny\\.cc)/[a-zA-Z0-9\\-._~]+"),

        // 4. 一般的なドメイン（より厳密に）- ハイフンを含むサブドメインに対応
        Pattern.compile("\\b[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9]*\\.[a-zA-Z]{2,}(?:\\.[a-zA-Z]{2,})*(?:/[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?\\b")
    )

    Log.d("UrlExtraction", "抽出対象テキスト: $text")

    urlPatterns.forEachIndexed { index, pattern ->
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val url = matcher.group().trim()
            Log.d("UrlExtraction", "パターン${index + 1}で検出: $url")

            // 重複チェック & 有効性チェック
            if (!urls.contains(url) && isValidUrl(url)) {
                urls.add(url)
                Log.d("UrlExtraction", "追加: $url")
            } else {
                Log.d("UrlExtraction", "スキップ: $url (重複または無効)")
            }
        }
    }

    Log.d("UrlExtraction", "最終結果: $urls")
    return urls
}

/**
 * URLが有効かどうかをチェック
 */
private fun isValidUrl(url: String): Boolean {
    // 明らかに無効なパターンを除外
    if (url.length < 4) return false
    if (url.startsWith("-") || url.endsWith("-")) return false
    if (!url.contains(".")) return false

    // 最低限のドメイン形式チェック
    val domain = when {
        url.startsWith("http://") || url.startsWith("https://") -> {
            url.substringAfter("://").substringBefore("/")
        }
        url.startsWith("www.") -> {
            url.substringBefore("/")
        }
        else -> {
            url.substringBefore("/")
        }
    }

    // ドメインが有効かチェック
    val domainParts = domain.split(".")
    if (domainParts.size < 2) return false
    if (domainParts.any { it.isEmpty() || it.startsWith("-") || it.endsWith("-") }) return false

    return true
}