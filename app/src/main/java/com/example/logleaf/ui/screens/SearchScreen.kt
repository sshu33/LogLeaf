package com.example.logleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.logleaf.Post
import com.example.logleaf.R
import com.example.logleaf.ui.components.SearchResultPostItem
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
                        onClick = { onPostClick(post) } // ← ここで渡す
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
    onClick: () -> Unit // ← onClickを受け取る口を追加
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // ★★★ ここがポイント！ ★★★
        // カードの直接の子であるRowに、カード全体の内部余白を設定します。
        Row(
            modifier = Modifier
                // このpaddingが、カードの縁と中身全体の間の余白になります。
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(IntrinsicSize.Min)
        ) {
            // カラーバー
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(post.source.brandColor)
            )

            // カラーバーとテキストの間の明確なスペース
            Spacer(modifier = Modifier.width(16.dp))

            // テキスト部分をColumnでまとめる
            // こちらのColumnにはもうpaddingは不要です。
            Column {
                // 日付表示
                Text(
                    text = post.createdAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // スペース
                Spacer(modifier = Modifier.height(4.dp))

                // 投稿本文
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    // ★★★ 以下の2行を追加 ★★★
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}