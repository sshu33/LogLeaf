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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.DayLog
import com.example.logleaf.UiState
import com.example.logleaf.ui.theme.GrayLimeGreen
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
                    Text("投稿がありません")
                }
            } else {
                val groupedByMonth = uiState.dayLogs.groupBy {
                    YearMonth.from(it.date)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedByMonth.forEach { (yearMonth, logsInMonth) ->
                        stickyHeader {
                            val monthString = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                            Text(
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
                                // ★★★ ここが最重要の修正箇所です ★★★
                                onClick = {
                                    // クリックされた投稿(firstPost)がnullでないことを確認
                                    dayLog.firstPost?.let { post ->
                                        val date = dayLog.date.toString()
                                        val postId = post.id
                                        // 日付と投稿IDの両方を渡してナビゲーション！
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
fun PostItem(dayLog: DayLog, onClick: () -> Unit = {}) {
    val post = dayLog.firstPost ?: return
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())

    // ★★★ 全体を Card で囲む ★★★
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // ★★★ 上下のパディング (vertical) を削除！ ★★★
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            // ★★★ カードの色を Color.White に指定 ★★★
            containerColor = Color.White
        )
    ) {
        // ★★★ あなたの元のレイアウトは、ここから下、一切変更しません ★★★
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp) // カードの内側の余白
                .height(IntrinsicSize.Min)
        ) {
            // --- 日付部分 ---
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = localDateTime.dayOfMonth.toString(), fontSize = 28.sp, fontWeight = FontWeight.Medium)
                Text(text = localDateTime.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase(), fontSize = 12.sp, color = Color.Gray)
            }

            // --- カラーバーと本文 ---
            Row(modifier = Modifier.fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(post.source.brandColor)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
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
}