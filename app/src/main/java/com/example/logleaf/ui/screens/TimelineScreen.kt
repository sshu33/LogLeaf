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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    uiState: UiState,
    onRefresh: () -> Unit,
    navController: NavController,
    listState: LazyListState
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

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

            Surface(
                color = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // ★★★ 2. ヘッダー全体をタップ可能にします ★★★
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        listState.animateScrollToItem(index = 0)
                    }
                }
            ) {
                // --- デザインの微調整値は、すべて維持します ---
                val headerHeight = 65.dp
                val horizontalPadding = 12.dp
                val titleStartPadding = 4.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Timeline",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 24.sp
                        ),
                        modifier = Modifier.padding(start = titleStartPadding)
                    )
                }
            }
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
                    // ★★★ 3. リモコンをLazyColumnに接続します ★★★
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                ) {
                    // ... LazyColumnの中身（月のセクションヘッダーなど）は一切変更ありません ...
                    groupedByMonth.forEach { (yearMonth, logsInMonth) ->
                        stickyHeader {
                            val monthString = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                            UserFontText(
                                text = monthString,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp
                                ),
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
                                    dayLog.firstPost?.let { post ->
                                        val date = dayLog.date.toString()
                                        val postId = post.id
                                        navController.navigate("calendar?date=$date&postId=$postId")
                                    }
                                },
                                onImageClick = {
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
    onTextClick: () -> Unit = {},
    onImageClick: () -> Unit = {}
) {
    val post = dayLog.firstPost ?: return
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())

    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = cardShape, // ◀◀◀ 定数を使用
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape) // ◀ リップルエフェクトをカードの形にクリップ
                .clickable(onClick = onTextClick) // ◀ カード全体（下地）のクリックをここに設定
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
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row { // 元のRow構造を維持
                        // --- 日付部分 ---
                        Column(
                            modifier = Modifier.width(56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = localDateTime.dayOfMonth.toString(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = localDateTime.format(
                                    DateTimeFormatter.ofPattern(
                                        "EEE",
                                        Locale.ENGLISH
                                    )
                                ).uppercase(), fontSize = 12.sp, color = Color.Gray
                            )
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
                            Column(
                                modifier = Modifier
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
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.9
                                        ),
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
}