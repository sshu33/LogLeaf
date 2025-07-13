package com.example.logleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.Post
import com.example.logleaf.UiState
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    uiState: UiState,
    initialDateString: String? = null,
    navController: NavController,
    // selectedDateはもう不要なので削除
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    // ----------------------------------------------------
    // ★★★ ここからが新しいロジックです ★★★
    // ----------------------------------------------------

    // 1. 初期日付を決定する (遷移元から渡された日付 or 今日)
    val initialDate = remember(initialDateString) {
        if (initialDateString != null) {
            try {
                LocalDate.parse(initialDateString)
            } catch (e: Exception) {
                LocalDate.now()
            }
        } else {
            LocalDate.now()
        }
    }

    // 2. ユーザーがカレンダー上で選択している日付の状態を管理する
    var selectedDate by remember { mutableStateOf(initialDate) }

    // 3. 画面が表示された時に一度だけ、初期日付を反映させる
    //    (initialDateStringが変わった時も再実行される)
    LaunchedEffect(initialDate) {
        selectedDate = initialDate
    }

    // ----------------------------------------------------
    // ★★★ ここまでは新しいロジックです ★★★
    // ----------------------------------------------------


    // 選択された日付の投稿をフィルタリング (ここは変更なし)
    val postsForSelectedDay = uiState.allPosts.filter {
        it.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == selectedDate
    }

    // --- ここから下のレイアウト部分は、ほぼ変更ありません ---
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            CalendarHeader(
                yearMonth = YearMonth.from(selectedDate),
                onMonthSelected = { newMonth -> selectedDate = selectedDate.withMonth(newMonth.value).withDayOfMonth(1) },
                onYearChanged = { yearChange -> selectedDate = selectedDate.plusYears(yearChange.toLong()) },
                onMonthTitleClick = { selectedDate = LocalDate.now() },
                onRefresh = onRefresh,
                isRefreshing = isRefreshing
            )
            CalendarGrid(
                posts = uiState.allPosts,
                selectedDate = selectedDate,
                onDateSelected = { newDate -> selectedDate = newDate },
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (postsForSelectedDay.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("この日の投稿はありません")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    items(postsForSelectedDay, key = { post -> post.id }) { post ->
                        CalendarPostCardItem(post = post)
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarHeader(
    yearMonth: YearMonth,
    onMonthSelected: (Month) -> Unit,
    onYearChanged: (Int) -> Unit,
    onMonthTitleClick: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean // ★ 追加
) {
    val monthString = yearMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 1段目: 年移動とアイコン ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(48.dp))

            // 中央の年移動UI
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onYearChanged(-1) }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Year")
                }
                Text(
                    text = yearMonth.year.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                IconButton(onClick = { onYearChanged(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Year")
                }
            }

            // 右側のアイコンを「更新」に置き換え
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                // isRefreshing の状態に応じて、表示するものを切り替える
                if (isRefreshing) {
                    // 更新中: クルクルマークを表示
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.0.dp
                    )
                } else {
                    // 通常時: 更新アイコンを表示
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        // --- 2段目: 月選択バー (変更なし) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Month.values().forEach { month ->
                val monthChar = month.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH)
                Text(
                    text = monthChar,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (yearMonth.month == month) FontWeight.Bold else FontWeight.Normal,
                    color = if (yearMonth.month == month) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.clickable { onMonthSelected(month) }
                )
            }
        }

        // --- 3段目: 月のタイトル (変更なし) ---
        Text(
            text = monthString,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onMonthTitleClick() }
        )
    }
}

@Composable
fun CalendarGrid(
    posts: List<Post>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val currentYearMonth = YearMonth.from(selectedDate)
        val daysInMonth = currentYearMonth.lengthOfMonth()
        val firstDayOfMonth = currentYearMonth.atDay(1).dayOfWeek.value % 7
        val postsByDay = posts.filter { YearMonth.from(it.createdAt.withZoneSameInstant(ZoneId.systemDefault())) == currentYearMonth }.groupBy { it.createdAt.withZoneSameInstant(
            ZoneId.systemDefault()).dayOfMonth }

        val dayCells = mutableListOf<LocalDate?>()
        repeat(firstDayOfMonth) { dayCells.add(null) }
        for (day in 1..daysInMonth) {
            dayCells.add(currentYearMonth.atDay(day))
        }
        while (dayCells.size % 7 != 0) {
            dayCells.add(null)
        }
        val weeks = dayCells.chunked(7)
        val totalRows = 1 + weeks.size
        val rowHeight = this.maxHeight / totalRows

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = day, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(rowHeight)
                ) {
                    week.forEach { date ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val colorsForDay = postsByDay[date.dayOfMonth]?.map { it.color }?.distinct() ?: emptyList()
                                DayCell(
                                    day = date.dayOfMonth,
                                    colors = colorsForDay,
                                    isSelected = (date == selectedDate),
                                    height = rowHeight,
                                    onClick = { onDateSelected(date) }
                                )
                            } else {
                                Spacer(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(day: Int, colors: List<Color>, isSelected: Boolean, height: Dp, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val textContainerSize = height * 0.55f
        Box(
            modifier = Modifier
                .size(textContainerSize)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.take(4).forEach { color ->
                Box(modifier = Modifier.size(height * 0.1f).background(color, shape = CircleShape))
            }
        }
    }
}



@Composable
fun CalendarPostCardItem(post: Post) {
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
    val timeString = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    // --- タイムライン画面と同じスタイルのCardで全体を囲む ---
    Card(
        modifier = Modifier.fillMaxWidth(), // PaddingはLazyColumn側で制御
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        // --- ここから下は元のCalendarPostRowのレイアウトとほぼ同じ ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp) // カードの内側の余白
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.width(56.dp)
            )
            Row(modifier = Modifier.fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        // ★ post.source.brandColor に統一
                        .background(post.source.brandColor)
                )
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}