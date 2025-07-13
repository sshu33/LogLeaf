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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.logleaf.Post
import com.example.logleaf.R
import com.example.logleaf.ui.components.HighlightedText
import com.example.logleaf.ui.theme.SnsType
import java.time.format.DateTimeFormatter

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onPostClick: (Post) -> Unit // ← onPostClickを受け取る口を追加
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSns by viewModel.selectedSns.collectAsState()
    val searchResultPosts by viewModel.searchResultPosts.collectAsState(initial = emptyList())

    // ★★★ ここからが、我々が調整した新しい骨格です ★★★
    Scaffold(
        topBar = {
            // TopBarをColumnで囲み、上の余白を追加
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChanged = viewModel::onQueryChanged,
                    selectedSns = selectedSns,
                    onSnsFilterChanged = viewModel::onSnsFilterChanged,
                    onReset = viewModel::onReset
                )
            }
        }
    ) { paddingValues ->
        // ★ リスト表示部分も、新しいものに置き換え ★
        if (searchQuery.isNotBlank() && searchResultPosts.isEmpty()) {
            // 結果が0件の場合の表示 (ここは変更なし)
            // ただし、背景色だけはリストと合わせる
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant), // 背景色を追加
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "投稿が見つかりませんでした。",
                    // テキストカラーも背景色に合わせる
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // ★ こちらがメインのリスト表示 ★
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant), // 背景色を設定
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResultPosts) { post ->
                    SearchResultItem(
                        post = post,
                        // ★★★ 検索クエリを渡す ★★★
                        searchQuery = searchQuery
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // --- 検索バー ---
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 30.dp), // ★★★ .height() を .defaultMinSize() に変更 ★★★
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
            shape = RoundedCornerShape(24.dp), // 高さに合わせて角丸も調整

            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
        )
        // ★★★ アイコン群と検索バーの間に、手動でスペースを設ける ★★★
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
                    onDismissRequest = { snsFilterMenuExpanded = false }
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
    searchQuery: String // 検索クエリを受け取る
) {
    // -----------------------------------------------------------------
    // ★★★ ここからが表示ロジックです ★★★
    // -----------------------------------------------------------------

    // 1. 検索クエリを単語リストに変換
    val keywords = remember(searchQuery) {
        searchQuery.split(" ", "　").filter { it.isNotBlank() }
    }

    // 2. 表示するテキストを決定する
    val displayText = remember(post.text, keywords) {
        // キーワードがない場合は、元のテキストをそのまま使う
        if (keywords.isEmpty()) {
            post.text
        } else {
            // 最初のキーワードが本文のどこにあるか探す
            val firstKeyword = keywords.first()
            val index = post.text.indexOf(firstKeyword, ignoreCase = true)

            // 見つからなかったり、表示範囲内（先頭から約100文字）にある場合は、そのまま
            if (index == -1 || index < 100) {
                post.text
            } else {
                // 隠れてしまう場合は、キーワードの少し前から表示を開始し、先頭に "..." を付ける
                val startIndex = (index - 20).coerceAtLeast(0)
                "... " + post.text.substring(startIndex)
            }
        }
    }
    // -----------------------------------------------------------------


    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = post.createdAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ★★★ ここで、新しく作った HighlightedText を使う！ ★★★
                HighlightedText(
                    text = displayText,
                    keywordsToHighlight = keywords
                )
            }
        }
    }
}