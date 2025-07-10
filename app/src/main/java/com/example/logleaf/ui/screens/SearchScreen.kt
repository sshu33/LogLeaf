package com.example.logleaf.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 検索バー (全体の ⅗ 程度の幅)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.weight(3f),
            placeholder = { Text("投稿を検索...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) { // 文字列だけをクリア
                        Icon(Icons.Default.Clear, contentDescription = "Clear Query")
                    }
                }
            },
            singleLine = true
        )

        // SNSフィルター (全体の ⅕ 程度の幅)
        Box(modifier = Modifier.weight(1f)) {
            IconButton(onClick = { snsFilterMenuExpanded = true }) {
                // ここは後で各SNSのかっこいいアイコンに差し替えましょう！
                // とりあえずテキストで表示
                Text(selectedSns?.name ?: "All")
            }
            DropdownMenu(
                expanded = snsFilterMenuExpanded,
                onDismissRequest = { snsFilterMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All") },
                    onClick = {
                        onSnsFilterChanged(null)
                        snsFilterMenuExpanded = false
                    }
                )
                SnsType.entries.forEach { sns ->
                    DropdownMenuItem(
                        text = { Text(sns.name) },
                        onClick = {
                            onSnsFilterChanged(sns)
                            snsFilterMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 一括リセットボタン (全体の ⅕ 程度の幅)
        TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
            Text("リセット")
        }
    }
}