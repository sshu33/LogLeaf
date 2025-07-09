package com.example.logleaf.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer // これで解決されるはず
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // これで解決されるはず
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.DayLog
import com.example.logleaf.UiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// OptInは、ExperimentalMaterial3ApiだけでOK
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text("Timeline") }
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.dayLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("投稿がありません")
                    }
                } else {
                    val groupedByMonth = uiState.dayLogs.groupBy {
                        it.date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        groupedByMonth.forEach { (month, logsInMonth) ->
                            stickyHeader {
                                Text(
                                    text = month,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
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
                                    onClick = {
                                        navController.navigate("calendar?date=${dayLog.date}")
                                    }
                                )
                                Divider(thickness = 0.5.dp, color = Color.LightGray)
                            }
                        }
                    }
                }

                PullToRefreshContainer(
                    state = pullToRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    )
}

// PostItemは変更なし
@Composable
fun PostItem(dayLog: DayLog, onClick: () -> Unit = {}) {
    val post = dayLog.firstPost ?: return
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = localDateTime.dayOfMonth.toString(), fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text(text = localDateTime.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase(), fontSize = 12.sp, color = Color.Gray)
        }
        Row(modifier = Modifier.fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(post.color)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.height(48.dp)
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