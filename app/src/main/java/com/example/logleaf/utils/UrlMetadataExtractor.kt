package com.example.logleaf.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.regex.Pattern

/**
 * URLのメタデータ（タイトル、説明、画像など）を抽出するクラス
 */
data class UrlMetadata(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false
)

object UrlMetadataExtractor {

    // メタデータのキャッシュ
    private val metadataCache = mutableMapOf<String, UrlMetadata>()

    /**
     * URLからメタデータを取得（キャッシュ機能付き）
     */
    suspend fun extractMetadata(url: String): UrlMetadata {
        val normalizedUrl = normalizeUrl(url)

        Log.d("UrlMetadata", "メタデータ取得開始: $normalizedUrl")

        // キャッシュにあればそれを返す
        metadataCache[normalizedUrl]?.let { cached ->
            if (!cached.isLoading && !cached.hasError) {
                Log.d("UrlMetadata", "キャッシュから返却: $normalizedUrl")
                return cached
            }
        }

        // ローディング状態をキャッシュに保存
        val loadingMetadata = UrlMetadata(url = normalizedUrl, isLoading = true)
        metadataCache[normalizedUrl] = loadingMetadata

        return try {
            Log.d("UrlMetadata", "実際の取得開始: $normalizedUrl")
            val metadata = fetchMetadataFromUrl(normalizedUrl)
            Log.d("UrlMetadata", "取得成功: title=${metadata.title}, desc=${metadata.description}")
            metadataCache[normalizedUrl] = metadata
            metadata
        } catch (e: Exception) {
            Log.e("UrlMetadataExtractor", "メタデータ取得エラー: $normalizedUrl", e)
            val errorMetadata = UrlMetadata(url = normalizedUrl, hasError = true)
            metadataCache[normalizedUrl] = errorMetadata
            errorMetadata
        }
    }

    /**
     * 実際にURLにアクセスしてメタデータを取得
     */
    private suspend fun fetchMetadataFromUrl(url: String): UrlMetadata = withContext(Dispatchers.IO) {
        Log.d("UrlMetadata", "HTTP接続開始: $url")

        var connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8")

        // リダイレクトを手動で追従（最大3回）
        var redirectCount = 0
        var finalUrl = url

        while (redirectCount < 3) {
            connection = URL(finalUrl).openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8")

            val responseCode = (connection as java.net.HttpURLConnection).responseCode
            Log.d("UrlMetadata", "レスポンスコード: $responseCode for $finalUrl")

            when (responseCode) {
                in 300..399 -> {
                    // リダイレクト
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        finalUrl = resolveUrl(location, finalUrl)
                        Log.d("UrlMetadata", "リダイレクト先: $finalUrl")
                        redirectCount++
                        continue
                    } else {
                        break
                    }
                }
                200 -> {
                    // 成功
                    break
                }
                else -> {
                    throw Exception("HTTP $responseCode")
                }
            }
        }

        val html = connection.getInputStream().bufferedReader().use { it.readText() }
        Log.d("UrlMetadata", "HTML取得完了: ${html.length}文字")

        val metadata = UrlMetadata(
            url = finalUrl,
            title = extractTitle(html),
            description = extractDescription(html),
            imageUrl = extractImageUrl(html, finalUrl),
            siteName = extractSiteName(html, finalUrl)
        )

        Log.d("UrlMetadata", "抽出結果: title=${metadata.title}, desc=${metadata.description}, image=${metadata.imageUrl}")
        metadata
    }


    /**
     * HTMLからタイトルを抽出（フォールバック付き）
     */
    private fun extractTitle(html: String): String? {
        // 優先順位: og:title -> twitter:title -> title タグ -> h1 タグ
        return extractMetaProperty(html, "og:title")
            ?: extractMetaProperty(html, "twitter:title")
            ?: extractTitleTag(html)
            ?: extractFirstH1Tag(html)
    }

    /**
     * HTMLから説明文を抽出（フォールバック付き）
     */
    private fun extractDescription(html: String): String? {
        return extractMetaProperty(html, "og:description")
            ?: extractMetaProperty(html, "twitter:description")
            ?: extractMetaProperty(html, "description")
            ?: extractFirstParagraph(html)
    }

    /**
     * HTMLから画像URLを抽出（フォールバック付き）
     */
    private fun extractImageUrl(html: String, baseUrl: String): String? {
        val imageUrl = extractMetaProperty(html, "og:image")
            ?: extractMetaProperty(html, "twitter:image")
            ?: extractFavicon(html, baseUrl)

        return imageUrl?.let { resolveUrl(it, baseUrl) }
    }

    /**
     * 最初のh1タグからタイトルを抽出
     */
    private fun extractFirstH1Tag(html: String): String? {
        val pattern = Pattern.compile("<h1[^>]*>([^<]*)</h1>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }
        } else null
    }

    /**
     * 最初のpタグから説明文を抽出
     */
    private fun extractFirstParagraph(html: String): String? {
        val pattern = Pattern.compile("<p[^>]*>([^<]{10,100})</p>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.trim()?.takeIf { it.length >= 10 }
        } else null
    }

    /**
     * ファビコンのURLを抽出
     */
    private fun extractFavicon(html: String, baseUrl: String): String? {
        val patterns = listOf(
            // <link rel="icon" href="...">
            Pattern.compile("<link[^>]+rel=[\"'](?:icon|shortcut icon)[\"'][^>]+href=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            // <link href="..." rel="icon">
            Pattern.compile("<link[^>]+href=[\"']([^\"']*)[\"'][^>]+rel=[\"'](?:icon|shortcut icon)[\"']", Pattern.CASE_INSENSITIVE)
        )

        patterns.forEach { pattern ->
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                return matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }

        // フォールバック: /favicon.ico
        return try {
            val baseUri = URL(baseUrl)
            "${baseUri.protocol}://${baseUri.host}/favicon.ico"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * HTMLからサイト名を抽出
     */
    private fun extractSiteName(html: String, url: String): String? {
        return extractMetaProperty(html, "og:site_name")
            ?: extractDomainFromUrl(url)
    }

    /**
     * メタプロパティの値を抽出
     */
    private fun extractMetaProperty(html: String, property: String): String? {
        val patterns = listOf(
            // <meta property="og:title" content="...">
            Pattern.compile("<meta\\s+property=[\"']$property[\"']\\s+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            // <meta content="..." property="og:title">
            Pattern.compile("<meta\\s+content=[\"']([^\"']*)[\"']\\s+property=[\"']$property[\"']", Pattern.CASE_INSENSITIVE),
            // <meta name="description" content="...">
            Pattern.compile("<meta\\s+name=[\"']$property[\"']\\s+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            // <meta content="..." name="description">
            Pattern.compile("<meta\\s+content=[\"']([^\"']*)[\"']\\s+name=[\"']$property[\"']", Pattern.CASE_INSENSITIVE)
        )

        patterns.forEach { pattern ->
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                return matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }

        return null
    }

    /**
     * <title>タグからタイトルを抽出
     */
    private fun extractTitleTag(html: String): String? {
        val pattern = Pattern.compile("<title>([^<]*)</title>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }
        } else null
    }

    /**
     * URLを正規化（修正版）
     */
    private fun normalizeUrl(url: String): String {
        Log.d("UrlMetadata", "正規化前: $url")

        val normalized = when {
            url.startsWith("http://") || url.startsWith("https://") -> {
                // 既にプロトコルがある場合はそのまま
                url
            }
            url.startsWith("www.") -> {
                // www.で始まる場合
                "https://$url"
            }
            url.startsWith("//") -> {
                // //で始まる場合
                "https:$url"
            }
            else -> {
                // その他の場合
                "https://$url"
            }
        }

        Log.d("UrlMetadata", "正規化後: $normalized")
        return normalized
    }

    /**
     * 相対URLを絶対URLに変換（修正版）
     */
    private fun resolveUrl(targetUrl: String, baseUrl: String): String {
        return try {
            when {
                targetUrl.startsWith("http://") || targetUrl.startsWith("https://") -> {
                    // 既に絶対URL
                    targetUrl
                }
                targetUrl.startsWith("//") -> {
                    // プロトコル相対URL
                    val baseProtocol = URL(baseUrl).protocol
                    "$baseProtocol:$targetUrl"
                }
                targetUrl.startsWith("/") -> {
                    // サイト相対URL
                    val baseUri = URL(baseUrl)
                    "${baseUri.protocol}://${baseUri.host}$targetUrl"
                }
                else -> {
                    // パス相対URL
                    val baseUri = URL(baseUrl)
                    val basePath = baseUri.path.substringBeforeLast("/")
                    "${baseUri.protocol}://${baseUri.host}$basePath/$targetUrl"
                }
            }
        } catch (e: Exception) {
            Log.e("UrlMetadata", "URL解決エラー: $targetUrl from $baseUrl", e)
            targetUrl
        }
    }

    /**
     * URLからドメイン名を抽出
     */
    private fun extractDomainFromUrl(url: String): String {
        return try {
            URL(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }

    /**
     * キャッシュをクリア（メモリ節約用）
     */
    fun clearCache() {
        metadataCache.clear()
    }
}