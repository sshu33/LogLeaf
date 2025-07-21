package com.example.logleaf.ui.screens


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.Post
import com.example.logleaf.UiState
import com.example.logleaf.ui.theme.SettingsTheme
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.delay
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
    targetPostId: String? = null,
    navController: NavController,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onShowPostEntry: () -> Unit,
    onDismissPostEntry: () -> Unit,
    onToggleShowHidden: () -> Unit,
    onStartEditingPost: (Post) -> Unit,
    onSetPostHidden: (String, Boolean) -> Unit,
    onDeletePost: (String) -> Unit
) {

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
    var selectedDate by remember { mutableStateOf(initialDate) }
    val listState = rememberLazyListState()
    var focusedPostIdForRipple by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialDate, targetPostId) {
        selectedDate = initialDate
        if (targetPostId != null) {
            val postsForDay = uiState.allPosts.filter {
                it.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate() == initialDate
            }
            val index = postsForDay.indexOfFirst { it.id == targetPostId }
            if (index != -1) {
                listState.animateScrollToItem(index)
                focusedPostIdForRipple = targetPostId
            }
        }
    }
    val postsForSelectedDay = uiState.allPosts
        .filter {
            it.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == selectedDate
        }
        //.sortedByDescending { it.createdAt } // 新しい順にソートする

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                CalendarHeader(
                    yearMonth = YearMonth.from(selectedDate),
                    onMonthSelected = { newMonth ->
                        selectedDate = selectedDate.withMonth(newMonth.value).withDayOfMonth(1)
                    },
                    onYearChanged = { yearChange ->
                        selectedDate = selectedDate.plusYears(yearChange.toLong())
                    },
                    onMonthTitleClick = { selectedDate = LocalDate.now() },
                    onRefresh = onRefresh,
                    isRefreshing = isRefreshing,
                    showHiddenPosts = uiState.showHiddenPosts,
                    onToggleShowHidden = onToggleShowHidden
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
                if (uiState.isLoading) {
                    // (読み込み中の表示)
                } else if (postsForSelectedDay.isEmpty()) {
                    // (投稿がない場合の表示)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        items(postsForSelectedDay, key = { it.id }) { post ->
                            CalendarPostCardItem(
                                post = post,
                                isFocused = (post.id == focusedPostIdForRipple),
                                onStartEditing = { onStartEditingPost(post) },
                                onSetHidden = { isHidden -> onSetPostHidden(post.id, isHidden) },
                                onDelete = { onDeletePost(post.id) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onShowPostEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            val fabShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp, end = 9.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                // ★★★ 変更点④：.clickable は不要なので削除します ★★★
                // onClickが二重定義になるのを防ぎます
                ,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新しい投稿を作成",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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
    isRefreshing: Boolean,
    showHiddenPosts: Boolean,
    onToggleShowHidden: () -> Unit
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
            // ▼▼▼ 変更点: 左端のスペーサーをアイコンボタンに置き換える ▼▼▼
            IconButton(onClick = onToggleShowHidden) {
                Icon(
                    imageVector = if (showHiddenPosts) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (showHiddenPosts) "非表示投稿を隠す" else "非表示投稿を表示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
                val monthChar =
                    month.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH)
                Text(
                    text = monthChar,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
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
        val postsByDay =
            posts.filter { YearMonth.from(it.createdAt.withZoneSameInstant(ZoneId.systemDefault())) == currentYearMonth }
                .groupBy {
                    it.createdAt.withZoneSameInstant(
                        ZoneId.systemDefault()
                    ).dayOfMonth
                }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                ) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                val colorsForDay =
                                    postsByDay[date.dayOfMonth]?.map { it.color }?.distinct()
                                        ?: emptyList()
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
fun DayCell(
    day: Int,
    colors: List<Color>,
    isSelected: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
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
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.take(4).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(height * 0.1f)
                        .background(color, shape = CircleShape)
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarPostCardItem(
    post: Post,
    isFocused: Boolean,
    onStartEditing: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onDelete: () -> Unit
) {

    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
    val timeString = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    val interactionSource = remember { MutableInteractionSource() }

    // メニューの開閉状態を管理
    var isMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            val press = PressInteraction.Press(Offset.Zero)
            interactionSource.emit(press)
            delay(150)
            interactionSource.emit(PressInteraction.Release(press))
        }
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                // 非表示投稿は半透明にする
                .alpha(if (post.isHidden) 0.6f else 1.0f)
                // 長押しを検知できるように変更
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(),
                    onClick = {}, // 短いクリックの動作はなし
                    onLongClick = { isMenuExpanded = true } // 長押しでメニューを開く
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                SettingsTheme {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodyMedium, // このスタイルが固定される
                        color = Color.Gray,
                        modifier = Modifier.width(56.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
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

        SettingsTheme {

            // ドロップダウンメニュー
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
                modifier = Modifier
                    .width(80.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp) // この数値を大きくすると、もっと丸くなります
                    )
            ) {
                // ★ メニュー項目の縦のスキマは、このpaddingで調整できます
                val customPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

                val itemModifier = Modifier.height(35.dp)
                val itemPadding = PaddingValues(horizontal = 14.dp)

                DropdownMenuItem(
                    text = { Text("コピー") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(post.text))
                        isMenuExpanded = false
                    },
                    modifier = itemModifier,
                    contentPadding = itemPadding
                )

                // 「編集」メニュー
                if (post.source == SnsType.LOGLEAF) {
                    DropdownMenuItem(
                        text = { Text("編集") },
                        onClick = {
                            onStartEditing()
                            isMenuExpanded = false
                        },
                        modifier = itemModifier,
                        contentPadding = itemPadding
                    )
                }

                // 「非表示/再表示」メニュー
                DropdownMenuItem(
                    text = { Text(if (post.isHidden) "再表示" else "非表示") },
                    onClick = {
                        onSetHidden(!post.isHidden)
                        isMenuExpanded = false
                    },
                    modifier = itemModifier,
                    contentPadding = itemPadding
                )

                // 「削除」メニュー
                if (post.source == SnsType.LOGLEAF) {
                    DropdownMenuItem(
                        text = { Text("削除",) },
                        onClick = {
                            onDelete()
                            isMenuExpanded = false
                        },
                        modifier = itemModifier,
                        contentPadding = itemPadding
                    )
                }
            }
        }
    }
}