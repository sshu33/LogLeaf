package com.example.logleaf.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.logleaf.DayLog
import com.example.logleaf.UiState
import com.yourpackage.logleaf.ui.components.UserFontText
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    uiState: UiState,
    onRefresh: () -> Unit,
    navController: NavController
) {
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Timeline") })
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.dayLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "投稿がありません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val groupedByMonth = uiState.dayLogs.groupBy {
                    YearMonth.from(it.date)
                }

                LazyColumn(
                    // ★★★ ここが原因でした ★★★
                    // innerPaddingをcontentPaddingではなく、Modifier.padding()で指定します。
                    // これにより、LazyColumnコンポーネント全体がTopAppBarの下に配置されます。
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()), // 上のパディングだけ適用
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // ★★★ 下のパディングは、リストの最後にSpacerを追加することで確保します
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                ) {
                    groupedByMonth.forEach { (yearMonth, logsInMonth) ->
                        stickyHeader {
                            val monthString = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                            UserFontText(
                                text = monthString,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        items(logsInMonth, key = { it.date }) { dayLog ->
                            PostItem(
                                dayLog = dayLog,
                                onTextClick = {
                                    // テキストクリック時は今まで通りfirstPostに飛ぶ
                                    dayLog.firstPost?.let { post ->
                                        val date = dayLog.date.toString()
                                        val postId = post.id
                                        navController.navigate("calendar?date=$date&postId=$postId")
                                    }
                                },
                                onImageClick = {
                                    // ★追加: 画像クリック時は、画像の投稿ID (imagePostId) に飛ぶ
                                    dayLog.imagePostId?.let { postId ->
                                        val date = dayLog.date.toString()
                                        navController.navigate("calendar?date=$date&postId=$postId")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = innerPadding.calculateTopPadding())
            )
        }
    }
}

@Composable
fun PostItem(
    dayLog: DayLog,
    onTextClick: () -> Unit = {}, // ★変更: 名前をより具体的に
    onImageClick: () -> Unit = {}  // ★追加: 画像クリック用のコールバック
) {
    val post = dayLog.firstPost ?: return
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(IntrinsicSize.Min)
        ) {
            // --- 日付と本文エリアをBoxで囲み、クリック可能にする ---
            Box(
                modifier = Modifier
                    .weight(1f) // 画像がない場合は全幅、ある場合は残りの領域を確保
                    .fillMaxHeight()
                    .clickable(onClick = onTextClick) // ★移動: テキスト側のクリックをここに設定
            ) {
                Row { // 元のRow構造を維持
                    // --- 日付部分 ---
                    Column(
                        modifier = Modifier.width(56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = localDateTime.dayOfMonth.toString(), fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        Text(text = localDateTime.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase(), fontSize = 12.sp, color = Color.Gray)
                    }

                    // --- カラーバーと本文 ---
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(post.source.brandColor)
                        )
                        Column(modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                        ) {
                            Text(
                                text = post.text,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )

                            if (dayLog.totalPosts > 1) {
                                Text(
                                    text = "${dayLog.totalPosts} Social Moments",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- 画像サムネ（その日の画像があれば表示） ---
            if (dayLog.dayImageUrl != null) {
                Spacer(modifier = Modifier.width(8.dp))
                AsyncImage(
                    model = dayLog.dayImageUrl,
                    contentDescription = "その日の投稿画像",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onImageClick), // ★追加: 画像のクリックをここに設定
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}