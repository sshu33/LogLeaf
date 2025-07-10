package com.example.logleaf.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.example.logleaf.R
import com.example.logleaf.ui.components.SearchResultPostItem
import com.example.logleaf.ui.theme.SnsType

// ViewModelを引数で受け取る
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    // onPostClick: (Post) -> Unit // 将来的に投稿詳細画面に遷移する場合
) {
    // ViewModelからStateを収集
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSns by viewModel.selectedSns.collectAsState()
    // searchResultPostsはFlowなので、collectAsStateでStateに変換する
    val searchResultPosts by viewModel.searchResultPosts.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            // 画面上部の検索バーやフィルターを配置するエリア
            SearchTopBar(
                query = searchQuery,
                onQueryChanged = viewModel::onQueryChanged,
                selectedSns = selectedSns,
                onSnsFilterChanged = viewModel::onSnsFilterChanged,
                onReset = viewModel::onReset
            )
        }
    ) { paddingValues ->
        // 検索結果のリスト表示
        Column(modifier = Modifier.padding(paddingValues)) {
            if (searchQuery.isNotBlank() && searchResultPosts.isEmpty()) {
                // 何か検索しているのに結果が0件の場合
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("投稿が見つかりませんでした。")
                }
            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // items(...) の中を修正
                    items(searchResultPosts) { post ->
                        // 新しく作った SearchResultPostItem を呼び出す
                        SearchResultPostItem(post = post)
                    }
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
                .defaultMinSize(minHeight = 46.dp), // ★★★ .height() を .defaultMinSize() に変更 ★★★
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