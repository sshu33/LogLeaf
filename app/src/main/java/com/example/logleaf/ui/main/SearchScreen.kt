package com.example.logleaf.ui.main

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
import com.example.logleaf.data.model.Post
import com.example.logleaf.R
import com.example.logleaf.ui.components.CompactHealthView
import com.example.logleaf.ui.components.FitbitHealthDisplay
import com.example.logleaf.ui.components.HealthPostDisplay
import com.example.logleaf.ui.components.HighlightedText
import com.example.logleaf.ui.search.SearchMode
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
    // ▼▼▼ 2. isTagOnlySearch を searchMode に置き換え ▼▼▼
    val searchMode by viewModel.searchMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Surface(
            shadowElevation = 2.dp
        ) {
            SearchTopBar(
                query = searchQuery,
                onQueryChanged = viewModel::onQueryChanged,
                selectedSns = selectedSns, // ◀ 型は変わったが、そのまま渡す
                onSnsFilterChanged = viewModel::onSnsFilterChanged,
                onAllSnsToggled = viewModel::onAllSnsToggled, // ◀ 新しい関数を渡す
                searchMode = searchMode,
                onSearchModeChanged = viewModel::onSearchModeChanged,
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
                        // ▼▼▼ 4. isTagOnlySearch を searchMode に変更 ▼▼▼
                        searchMode = searchMode,
                        viewModel = viewModel,
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
    selectedSns: Set<SnsType>,
    onSnsFilterChanged: (SnsType) -> Unit,
    onAllSnsToggled: () -> Unit,
    searchMode: SearchMode,
    onSearchModeChanged: (SearchMode) -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(0.dp)
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

                // ▼▼▼ 1. DropdownMenuコンテナで全体を囲む ▼▼▼
                DropdownMenu(
                    expanded = snsFilterMenuExpanded,
                    onDismissRequest = { snsFilterMenuExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {

                    val menuItemModifier = Modifier.height(36.dp)
                    val indentedContentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)

                    Text(
                        text = "Search Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
                    )

                    // --- 「本文とタグで検索」 ---
                    DropdownMenuItem(
                        text = { UserFontText(text = "本文とタグ") },
                        onClick = { onSearchModeChanged(SearchMode.ALL) },
                        leadingIcon = { // ◀◀◀ 1. leadingIconに変更
                            val iconResId = if (searchMode == SearchMode.ALL) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off
                            Icon(
                                painter = painterResource(id = iconResId),
                                contentDescription = "本文とタグで検索",
                                modifier = Modifier.size(24.dp),
                                tint = if (searchMode == SearchMode.ALL) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        },
                        modifier = menuItemModifier,
                        contentPadding = indentedContentPadding
                    )

                    // --- 「本文のみで検索」 ---
                    DropdownMenuItem(
                        text = { UserFontText(text = "本文のみ") },
                        onClick = { onSearchModeChanged(SearchMode.TEXT_ONLY) },
                        leadingIcon = {
                            val iconResId = if (searchMode == SearchMode.TEXT_ONLY) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off
                            Icon(
                                painter = painterResource(id = iconResId),
                                contentDescription = "本文のみで検索",
                                modifier = Modifier.size(24.dp),
                                tint = if (searchMode == SearchMode.TEXT_ONLY) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        },
                        modifier = menuItemModifier,
                        contentPadding = indentedContentPadding
                    )

                    // --- 「タグのみで検索」 ---
                    DropdownMenuItem(
                        text = { UserFontText(text = "タグのみ") },
                        onClick = { onSearchModeChanged(SearchMode.TAG_ONLY) },
                        leadingIcon = {
                            val iconResId = if (searchMode == SearchMode.TAG_ONLY) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off
                            Icon(
                                painter = painterResource(id = iconResId),
                                contentDescription = "タグのみで検索",
                                modifier = Modifier.size(24.dp),
                                tint = if (searchMode == SearchMode.TAG_ONLY) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        },
                        modifier = menuItemModifier,
                        contentPadding = indentedContentPadding
                    )

                    UserFontText(
                        text = "SNS Filter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp) // 上の余白を少し広めに
                    )

                    // --- 「All」トグル ---
                    val isAllSelected = selectedSns.size == SnsType.entries.size
                    DropdownMenuItem(
                        text = { UserFontText(text = "ALL") },
                        onClick = onAllSnsToggled, // ◀ 新しい関数を呼び出す
                        leadingIcon = {
                            val iconResId = if (isAllSelected) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off
                            Icon(
                                painter = painterResource(id = iconResId),
                                contentDescription = "すべてのSNS",
                                modifier = Modifier.size(24.dp),
                                tint = if (isAllSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        },
                        modifier = menuItemModifier,
                        contentPadding = indentedContentPadding
                    )

                    // --- 各SNSのフィルター項目 ---
                    SnsType.entries
                        .filter { it != SnsType.GOOGLEFIT } // GOOGLEFITを除外
                        .forEach { sns ->
                        val isSelected = sns in selectedSns
                        DropdownMenuItem(
                            text = {
                                UserFontText(
                                    text = sns.name.lowercase().replaceFirstChar { it.uppercase() }
                                )
                            },
                            leadingIcon = {
                                val iconResId = when (sns) {
                                    SnsType.BLUESKY -> R.drawable.ic_bluesky
                                    SnsType.MASTODON -> R.drawable.ic_mastodon
                                    SnsType.LOGLEAF -> R.drawable.ic_logleaf
                                    SnsType.GITHUB -> R.drawable.ic_github
                                    SnsType.GOOGLEFIT-> R.drawable.ic_googlefit
                                    SnsType.FITBIT -> R.drawable.ic_fitbit
                                }
                                Icon(
                                    painter = painterResource(id = iconResId),
                                    contentDescription = sns.name,
                                    tint = if (isSelected) sns.brandColor else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { onSnsFilterChanged(sns) },
                            modifier = menuItemModifier,
                            contentPadding = indentedContentPadding
                        )
                    }
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

@Composable
fun SearchResultItem(
    post: Post,
    searchQuery: String,
    // ▼▼▼ 7. isTagOnlySearch を searchMode に置き換え ▼▼▼
    searchMode: SearchMode,
    viewModel: SearchViewModel,
    onClick: () -> Unit
) {
    val keywords = remember(searchQuery) {
        searchQuery.split(" ", "　").filter { it.isNotBlank() }
    }

    val displayText = remember(post.text, keywords) {
        if (keywords.isEmpty()) {
            post.text
        } else {
            val keywordPositions = keywords.mapNotNull { keyword ->
                val index = post.text.indexOf(keyword, ignoreCase = true)
                if (index != -1) index else null
            }

            if (keywordPositions.isEmpty()) {
                post.text
            } else {
                val firstKeywordIndex = keywordPositions.minOrNull() ?: 0

                if (firstKeywordIndex <= 30) {
                    post.text
                } else {
                    val beforeContextLength = 25
                    val startIndex = (firstKeywordIndex - beforeContextLength).coerceAtLeast(0)
                    val extractedText = post.text.substring(startIndex)
                    val prefix = if (startIndex > 0) "…" else ""
                    "$prefix$extractedText"
                }
            }
        }
    }

    // タグ検索の場合、該当するタグを取得
    var matchingTagName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchQuery, post.id, searchMode) { // ◀ searchModeをキーに追加
        if (keywords.isNotEmpty()) {
            val tags = viewModel.getTagsForPost(post.id)
            // ▼▼▼ 8. isTagOnlySearch を when (searchMode) に変更 ▼▼▼
            matchingTagName = when (searchMode) {
                SearchMode.TAG_ONLY -> {
                    val searchTagName = keywords.first().removePrefix("#")
                    tags.find { tag ->
                        tag.tagName.contains(searchTagName, ignoreCase = true)
                    }?.tagName
                }
                SearchMode.ALL, SearchMode.TEXT_ONLY -> {
                    // 全文検索または本文検索の場合: 念のためキーワードに一致するタグも探す
                    keywords.firstNotNullOfOrNull { keyword ->
                        tags.find { tag ->
                            tag.tagName.contains(keyword, ignoreCase = true)
                        }?.tagName
                    }
                }
            }
        } else {
            matchingTagName = null
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
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp)
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        color = post.source.brandColor,
                        shape = RoundedCornerShape(2.dp)
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
                // 時刻とタグチップの行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserFontText(
                        text = post.createdAt
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // タグ検索の場合のみ、該当タグを表示
                    Box(
                        modifier = Modifier.height(24.dp), // 固定の高さを設定
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (matchingTagName != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Gray.copy(alpha = 0.15f)
                            ) {
                                UserFontText(
                                    text = "#$matchingTagName",
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    ), // 縦のパディングを少し減らす
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Fitbit投稿かどうかで表示を分岐
                if (post.isHealthData) {
                    if (post.source == SnsType.FITBIT || post.source == SnsType.GOOGLEFIT) {
                        FitbitHealthDisplay(postText = post.text, modifier = Modifier)
                    } else {
                        CompactHealthView(postText = post.text, modifier = Modifier)
                    }
                } else {
                    // 通常投稿の場合：ハイライト付きテキスト表示
                    HighlightedText(
                        text = displayText,
                        keywordsToHighlight = keywords
                    )
                }
            }
        }
    }
}