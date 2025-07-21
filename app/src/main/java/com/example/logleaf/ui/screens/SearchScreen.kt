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
                    // 「全て」のメニュー項目 (contentPaddingは削除)
                    DropdownMenuItem(
                        text = { Text("All") },
                        leadingIcon = { /* アイコンなし */ },
                        onClick = {
                            onSnsFilterChanged(null)
                            snsFilterMenuExpanded = false
                        }
                    )

                    SnsType.entries.forEach { sns ->
                        DropdownMenuItem(
                            text = { Text(sns.name.lowercase().replaceFirstChar { it.uppercase() }) },
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
    onClick: () -> Unit // ★ クリック命令を受け取る「口」を追加
) {
    val keywords = remember(searchQuery) {
        searchQuery.split(" ", "　").filter { it.isNotBlank() }
    }
    val displayText = remember(post.text, keywords) {
        if (keywords.isEmpty()) {
            post.text
        } else {
            val firstKeyword = keywords.first()
            val index = post.text.indexOf(firstKeyword, ignoreCase = true)
            if (index == -1 || index < 100) {
                post.text
            } else {
                val startIndex = (index - 20).coerceAtLeast(0)
                "... " + post.text.substring(startIndex)
            }
        }
    }

    Card(
        // ★ Card自体をクリック可能にする
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
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(post.source.brandColor)
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
                        .withZoneSameInstant(ZoneId.systemDefault()) // 1. 端末のタイムゾーンに変換
                        .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")), // 2. その後でフォーマット
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