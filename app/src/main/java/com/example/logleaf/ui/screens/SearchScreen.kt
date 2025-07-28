package com.example.logleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.logleaf.Post
import com.example.logleaf.R
import com.example.logleaf.ui.components.HighlightedText
import com.example.logleaf.ui.theme.SnsType
import com.yourpackage.logleaf.ui.components.UserFontText
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onPostClick: (Post) -> Unit
) {

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSns by viewModel.selectedSns.collectAsState()
    val searchResultPosts by viewModel.searchResultPosts.collectAsState()
    val isTagOnlySearch by viewModel.isTagOnlySearch.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // ▼▼▼ [変更点1] Boxを削除し、Surfaceだけに戻す ▼▼▼
        Surface(
            shadowElevation = 2.dp
        ) {
            SearchTopBar(
                query = searchQuery,
                onQueryChanged = viewModel::onQueryChanged,
                selectedSns = selectedSns,
                onSnsFilterChanged = viewModel::onSnsFilterChanged,
                isTagOnlySearch = isTagOnlySearch,
                onTagOnlySearchChanged = viewModel::onTagOnlySearchChanged,
                onReset = viewModel::onReset
            )
        }

        if (searchQuery.isNotBlank() && searchResultPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "投稿が見つかりませんでした。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResultPosts) { post ->
                    SearchResultItem(
                        post = post,
                        searchQuery = searchQuery,
                        onClick = { onPostClick(post) }
                    )
                }
            }
        }
    }
}


@Composable
fun SearchTopBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    selectedSns: SnsType?,
    onSnsFilterChanged: (SnsType?) -> Unit,
    isTagOnlySearch: Boolean,
    onTagOnlySearchChanged: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    var snsFilterMenuExpanded by remember { mutableStateOf(false) }

    Row(
        // ▼▼▼ [変更点2] ここに、上下の余白を追加する ▼▼▼
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp), // 上下12dpの余白を追加
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // --- 検索バー ---
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            // ▼▼▼ [変更点3] heightを、安全なdefaultMinSizeに戻す ▼▼▼
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp), // 最小の高さを48dpに指定
            placeholder = { Text("投稿を検索...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Query")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
        )
        Spacer(modifier = Modifier.width(8.dp))

        // --- フィルターとリセットのアイコンをグループ化 ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // ★★★ 2. アイコン間のスペースを調整 ★★★
            horizontalArrangement = Arrangement.spacedBy(0.dp) // 4.dpくらいに詰める
        ) {
            // --- SNSフィルターボタン ---
            Box {
                IconButton(onClick = { snsFilterMenuExpanded = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_filter),
                        contentDescription = "SNS Filter",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // --- ドロップダウンメニュー ---
                DropdownMenu(
                    expanded = snsFilterMenuExpanded,
                    onDismissRequest = { snsFilterMenuExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {

                    // --- 「タグのみで検索」のトグル項目 ---
                    DropdownMenuItem(
                        // ▼▼▼ textプロパティの中を、Rowで再構築する ▼▼▼
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 1. アイコン
                                Icon(
                                    painter = painterResource(
                                        id = if (isTagOnlySearch) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off
                                    ),
                                    contentDescription = "Toggle Tag Search",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isTagOnlySearch) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                                // 2.余白を置く
                                Spacer(modifier = Modifier.width(9.dp)) // ◀◀ この値を調整！
                                // 3. テキスト
                                UserFontText(text = "タグで検索")
                            }
                        },
                        onClick = {
                            onTagOnlySearchChanged(!isTagOnlySearch)
                        }
                    )

                    // 「全て」のメニュー項目 (contentPaddingは削除)
                    DropdownMenuItem(
                        text = { UserFontText(text = "All") },
                        leadingIcon = { /* アイコンなし */ },
                        onClick = {
                            onSnsFilterChanged(null)
                            snsFilterMenuExpanded = false
                        }
                    )

                    SnsType.entries.forEach { sns ->
                        DropdownMenuItem(
                            text = {
                                UserFontText(
                                    text = sns.name.lowercase().replaceFirstChar { it.uppercase() })},
                            leadingIcon = {
                                // ★★★ アイコンとテキストの間を詰めるためのRow ★★★
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val iconResId = when (sns) {
                                        SnsType.BLUESKY -> R.drawable.ic_bluesky
                                        SnsType.MASTODON -> R.drawable.ic_mastodon
                                        SnsType.LOGLEAF -> R.drawable.ic_logleaf
                                    }
                                    Icon(
                                        painter = painterResource(id = iconResId),
                                        contentDescription = sns.name,
                                        modifier = Modifier.size(24.dp),
                                        // ★★★ ここ！SNSごとのテーマカラーを適用 ★★★
                                        tint = sns.brandColor
                                    )
                                    // Spacerでアイコンとテキストの間の距離を調整
                                    Spacer(modifier = Modifier.width(-20.dp))
                                }
                            },
                            onClick = {
                                onSnsFilterChanged(sns)
                                snsFilterMenuExpanded = false
                            },
                            // contentPaddingの指定は削除
                        )
                    }
                }
            }

            // --- 一括リセットボタン ---
            IconButton(onClick = onReset) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cancel),
                    contentDescription = "Reset Search",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SearchResultItem(
    post: Post,
    searchQuery: String,
    onClick: () -> Unit
) {
    val keywords = remember(searchQuery) {
        searchQuery.split(" ", "　").filter { it.isNotBlank() }
    }

    val displayText = remember(post.text, keywords) {
        if (keywords.isEmpty()) {
            post.text
        } else {
            // 全てのキーワードの最初の出現位置を調べる
            val keywordPositions = keywords.mapNotNull { keyword ->
                val index = post.text.indexOf(keyword, ignoreCase = true)
                if (index != -1) index else null
            }

            if (keywordPositions.isEmpty()) {
                post.text
            } else {
                // 一番最初に現れるキーワードの位置を取得
                val firstKeywordIndex = keywordPositions.minOrNull() ?: 0

                // 検索ワードが後ろの方にある長文の場合のみ調整
                if (firstKeywordIndex <= 30) {
                    // 検索ワードが前の方なら、そのまま表示（HighlightedTextが3行で切る）
                    post.text
                } else {
                    // 検索ワードの前に最大30文字だけ表示（絶対に1行以内）
                    val beforeContextLength = 25
                    val startIndex = (firstKeywordIndex - beforeContextLength).coerceAtLeast(0)

                    val extractedText = post.text.substring(startIndex)

                    // 前に省略記号を付ける（必要な場合のみ）
                    val prefix = if (startIndex > 0) "…" else ""

                    "$prefix$extractedText"
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            // カラーバーを丸角の内側に配置
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp) // 先にpadding
                    .width(5.dp) // その後でwidth
                    .fillMaxHeight()
                    .background(
                        color = post.source.brandColor,
                        shape = RoundedCornerShape(2.dp) // カラーバー自体も少し丸角に
                    )
            )
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                )
            ) {
                Text(
                    text = post.createdAt
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                HighlightedText(
                    text = displayText,
                    keywordsToHighlight = keywords
                )
            }
        }
    }
}