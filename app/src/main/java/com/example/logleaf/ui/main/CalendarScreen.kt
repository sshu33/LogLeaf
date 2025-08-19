package com.example.logleaf.ui.main


import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.logleaf.MainViewModel.SyncState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.logleaf.MainViewModel
import com.example.logleaf.R
import com.example.logleaf.UiState
import com.example.logleaf.data.model.Post
import com.example.logleaf.data.model.PostWithTagsAndImages
import com.example.logleaf.data.model.UiPost
import com.example.logleaf.data.settings.TimeSettings
import com.example.logleaf.ui.components.CompactFitbitHealthView
import com.example.logleaf.ui.components.CompactHealthView
import com.example.logleaf.ui.components.GradientProgressBar
import com.example.logleaf.ui.entry.PostImage
import com.example.logleaf.ui.theme.SettingsTheme
import com.example.logleaf.ui.theme.SnsType
import com.yourpackage.logleaf.ui.components.AutoSizeUserFontText
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class EnlargedImageState(
    val images: List<PostImage>,
    val initialIndex: Int
)

@Composable
private fun adjustDateByDayStart(dateTime: ZonedDateTime, timeSettings: TimeSettings): LocalDate {
    val adjustedDateTime = dateTime.minusHours(timeSettings.dayStartHour.toLong())
        .minusMinutes(timeSettings.dayStartMinute.toLong())
    return adjustedDateTime.toLocalDate()
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onStartEditingPost: (PostWithTagsAndImages) -> Unit,
    onSetPostHidden: (String, Boolean) -> Unit,
    onDeletePost: (PostWithTagsAndImages) -> Unit,
    scrollToTopEvent: Boolean,
    onConsumeScrollToTopEvent: () -> Unit,
    mainViewModel: MainViewModel
) {

    val pullToRefreshState = rememberPullToRefreshState()
    var postForDetail by remember { mutableStateOf<PostWithTagsAndImages?>(null) }
    val showDetailDialog = postForDetail != null

    val timeSettings by mainViewModel.timeSettings.collectAsState()
    val periodChangeProgress by mainViewModel.periodChangeProgress.collectAsState()
    val restoreState by mainViewModel.restoreState.collectAsState()
    val zeppImportState by mainViewModel.zeppImportState.collectAsState()
    val isFitbitHistoryFetching by mainViewModel.isFitbitHistoryFetching.collectAsState()
    val fitbitSyncProgress by mainViewModel.fitbitSyncProgress.collectAsState()

    val (enlargedImageState, setEnlargedImageState) = remember { mutableStateOf<EnlargedImageState?>(null) }

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

    LaunchedEffect(scrollToTopEvent) {
        if (scrollToTopEvent) {
            // 今日の日付に切り替え（新規投稿は今日の日付で作られるため）
            selectedDate = LocalDate.now()
            // スムーズにトップまでスクロール
            listState.animateScrollToItem(0)
            // イベントを消費
            onConsumeScrollToTopEvent()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
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

    LaunchedEffect(initialDate, targetPostId) {
        selectedDate = initialDate
        if (targetPostId != null) {
            val postsForDay = uiState.allPosts.filter {
                val adjustedDate = it.post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                    .minusHours(timeSettings.dayStartHour.toLong())
                    .minusMinutes(timeSettings.dayStartMinute.toLong())
                    .toLocalDate()
                adjustedDate == initialDate
            }
            val index = postsForDay.indexOfFirst { it.post.id == targetPostId }
            if (index != -1) {
                listState.animateScrollToItem(index)
                focusedPostIdForRipple = targetPostId
            }
        }
    }
    val postsForSelectedDay = uiState.allPosts
        .filter {
            val adjustedDate = it.post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                .minusHours(timeSettings.dayStartHour.toLong())
                .minusMinutes(timeSettings.dayStartMinute.toLong())
                .toLocalDate()
            adjustedDate == selectedDate
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
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
                    posts = uiState.allPosts.map { it.post },
                    selectedDate = selectedDate,
                    onDateSelected = { newDate -> selectedDate = newDate },
                    modifier = Modifier.weight(1f),
                    timeSettings = timeSettings
                )
            }

        val syncState by mainViewModel.syncState.collectAsState()
        if (syncState is SyncState.Progress) {
            val currentState = syncState as SyncState.Progress
            GradientProgressBar(
                progress = currentState.current.toFloat() / currentState.total,
                isError = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (periodChangeProgress != null) {
            GradientProgressBar(
                progress = periodChangeProgress!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (restoreState.isInProgress) {
            GradientProgressBar(
                progress = restoreState.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (zeppImportState.isInProgress) {
            GradientProgressBar(
                progress = zeppImportState.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (isFitbitHistoryFetching && fitbitSyncProgress != null) {
            val (current, total) = fitbitSyncProgress!!
            GradientProgressBar(
                progress = current.toFloat() / total,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {

                        UserFontText(
                            text = "投稿がありません",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        items(postsForSelectedDay, key = { it.post.id }) { postWithTagsAndImages ->
                            CalendarPostCardItem(
                                uiPost = UiPost(postWithTagsAndImages), // ◀◀◀ ここを修正
                                maxLines = 5,
                                isFocused = (postWithTagsAndImages.post.id == focusedPostIdForRipple),
                                onClick = { postForDetail = postWithTagsAndImages },
                                onImageClick = { uri ->
                                    val clickedImageIndex = postWithTagsAndImages.images.indexOfFirst {
                                        it.imageUrl == uri.toString()
                                    }
                                    if (clickedImageIndex != -1) {
                                        setEnlargedImageState(
                                            EnlargedImageState(
                                                images = postWithTagsAndImages.images,
                                                initialIndex = clickedImageIndex
                                            )
                                        )
                                    }
                                },
                                onStartEditing = { onStartEditingPost(postWithTagsAndImages) },
                                onSetHidden = { isHidden ->
                                    onSetPostHidden(
                                        postWithTagsAndImages.post.id,
                                        isHidden
                                    )
                                },
                                onDelete = { onDeletePost(postWithTagsAndImages) }
                            )
                        }
                    }
                }
            }
        }

        if (showDetailDialog) {
            Dialog(
                onDismissRequest = { postForDetail = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                LogViewScreen(
                    uiPosts = postsForSelectedDay.map { UiPost(it) },
                    targetPostId = postForDetail!!.post.id,
                    onDismiss = { postForDetail = null },
                    navController = navController,
                    // ★★★ この3行を追加 ★★★
                    onStartEditingPost = onStartEditingPost,
                    onSetPostHidden = onSetPostHidden,
                    onDeletePost = onDeletePost
                )
            }
        }

        enlargedImageState?.let { state ->
            ZoomableImageDialog(
                imageUri = Uri.parse(state.images[state.initialIndex].imageUrl), // とりあえず残す
                images = state.images,
                initialIndex = state.initialIndex,
                onDismiss = { setEnlargedImageState(null) }
            )
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
                    painter = painterResource(
                        id = if (showHiddenPosts) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                    ),
                    contentDescription = if (showHiddenPosts) "非表示投稿を隠す" else "非表示投稿を表示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
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
                UserFontText(
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
        UserFontText(
            text = monthString,
            // styleとfontWeightを、copyを使って一つにまとめます
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.clickable { onMonthTitleClick() }
        )
    }
}

@Composable
fun CalendarGrid(
    posts: List<Post>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    timeSettings: TimeSettings
) {
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val currentYearMonth = YearMonth.from(selectedDate)
        val daysInMonth = currentYearMonth.lengthOfMonth()
        val firstDayOfMonth = currentYearMonth.atDay(1).dayOfWeek
        val adjustedFirstDay = (firstDayOfMonth.value - timeSettings.weekStartDay.value + 7) % 7
        val postsByDay =
            posts.filter {
                val adjustedDate = it.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                    .minusHours(timeSettings.dayStartHour.toLong())
                    .minusMinutes(timeSettings.dayStartMinute.toLong())
                YearMonth.from(adjustedDate) == currentYearMonth
            }
                .groupBy {
                    val adjustedDate = it.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                        .minusHours(timeSettings.dayStartHour.toLong())
                        .minusMinutes(timeSettings.dayStartMinute.toLong())
                    adjustedDate.dayOfMonth
                }

        val dayCells = mutableListOf<LocalDate?>()
        repeat(adjustedFirstDay) { dayCells.add(null) }
        for (day in 1..daysInMonth) {
            dayCells.add(currentYearMonth.atDay(day))
        }
        while (dayCells.size % 7 != 0) {
            dayCells.add(null)
        }
        val weeks = dayCells.chunked(7)
        val totalRows = 1 + weeks.size
        val rowHeight = this.maxHeight / totalRows

        // 週のヘッダーを設定に合わせて動的に生成
        val weekDays = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val startDayIndex = when (timeSettings.weekStartDay) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }
        val reorderedWeekDays = weekDays.drop(startDayIndex) + weekDays.take(startDayIndex)


        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (dragOffset * 0.3f).dp) // 軽い追従感（30%の移動）
                .pointerInput(currentYearMonth) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            if (abs(dragOffset) > 100f) {
                                val newMonth = if (dragOffset > 0) {
                                    // 右スワイプ → 前の月（指についてくる感じ）
                                    currentYearMonth.minusMonths(1)
                                } else {
                                    // 左スワイプ → 次の月（指についてくる感じ）
                                    currentYearMonth.plusMonths(1)
                                }
                                onDateSelected(selectedDate.withMonth(newMonth.monthValue).withYear(newMonth.year))
                            }
                            dragOffset = 0f
                        }
                    ) { _, dragAmount ->
                        // ドラッグ中はリアルタイムで位置を更新
                        dragOffset += dragAmount.x
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                reorderedWeekDays.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        UserFontText(
                            text = day,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                                    onClick = { onDateSelected(date) },
                                    date = date
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
    height: Dp, // このheightが月によって変わるが、もう気にしない
    onClick: () -> Unit,
    date: LocalDate
) {
    val interactionSource = remember { MutableInteractionSource() }
    val today = LocalDate.now()
    val isToday = date == today
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val offsetX = 0.0f
    val offsetY = 3.0f

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

        val circleSize = 21.dp

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(circleSize)
                .background(
                    color = if (isSelected) primaryColor else Color.Transparent,
                    shape = CircleShape
                )
                .drawBehind {
                    if (isToday && !isSelected) {
                        drawCircle(
                            color = primaryColor,
                            radius = size.minDimension / 2.0f,
                            style = Stroke(width = 0.5.dp.toPx())
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                // ★★★ こちらも同様に修正しました ★★★
                modifier = Modifier.offset { IntOffset(-offsetX.roundToInt(), -offsetY.roundToInt()) },
                color = if (isSelected) onPrimaryColor else onSurfaceColor
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
                        .size(height * 0.1f) // 下のドットは行の高さに合わせる
                        .background(color, shape = CircleShape)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarPostCardItem(
    uiPost: UiPost,
    maxLines: Int,
    isFocused: Boolean,
    onStartEditing: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onImageClick: (Uri) -> Unit,
) {
    // 分解して従来通りに使用
    val postWithTagsAndImages = uiPost.postWithTagsAndImages
    val post = postWithTagsAndImages.post
    val tags = postWithTagsAndImages.tags
    val images = postWithTagsAndImages.images

    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
    val timeString = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    val interactionSource = remember { MutableInteractionSource() }
    var isMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            val press = PressInteraction.Press(Offset.Zero)
            interactionSource.emit(press)
            delay(150)
            interactionSource.emit(PressInteraction.Release(press))
        }
    }

    val cardShape = RoundedCornerShape(12.dp)

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (post.isHidden) 0.6f else 1.0f)
                .clip(cardShape) // ◀◀◀ 1. この形状でクリッピングする
                .combinedClickable( // ◀◀◀ 2. その後でクリック可能にする
                    interactionSource = interactionSource,
                    indication = rememberRipple(),
                    onClick = onClick,
                    onLongClick = { isMenuExpanded = true }
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = cardShape, // ◀◀◀ 定数を再利用
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
                // 時刻
                AutoSizeUserFontText(
                    text = timeString,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier
                        .width(52.dp)
                        .padding(end = 8.dp),
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 8.sp,
                        maxFontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * 0.9).sp,
                        stepSize = 1.sp
                    ),
                    softWrap = false
                )

                // カラーバー
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(post.source.brandColor)
                )

                // ▼▼▼ 本文・タグと画像を分けるための親Column ▼▼▼
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    // 健康データかどうかで表示を分岐
                    if (post.isHealthData || post.source == SnsType.GOOGLEFIT ||
                        (post.source == SnsType.FITBIT && (post.text.contains("🛏️") || post.text.contains("🏃‍♂️") || post.text.contains("📊")))) {
                        if (post.source == SnsType.FITBIT || post.source == SnsType.GOOGLEFIT) {
                            CompactFitbitHealthView(postText = post.text, modifier = Modifier)
                        } else {
                            CompactHealthView(postText = post.text, modifier = Modifier)
                        }
                    } else {
                        Log.d("Calendar", "通常投稿として表示: source=${post.source}")
                        // 通常投稿の場合：既存の表示
                        Text(
                            text = uiPost.displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // GoogleFit投稿以外の場合のみタグを表示
                    if (tags.isNotEmpty() && !post.isHealthData) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    // Material Symbols の Tag アイコン
                                    Icon(
                                        imageVector = Icons.Default.Numbers,
                                        contentDescription = "タグアイコン",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    // タグ名を表示するText
                                    UserFontText(
                                        text = tag.tagName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // ▼▼▼ 画像表示部分を修正 ▼▼▼
                if (images.isNotEmpty()) {
                    val firstImage = images.first()

                    // サムネイルがあればサムネイル、なければ元画像を使用
                    val displayImageUrl = firstImage.thumbnailUrl ?: firstImage.imageUrl

                    Log.d("CalendarImage", "Displaying: ${displayImageUrl.takeLast(15)} (thumbnail: ${firstImage.thumbnailUrl != null}) (orderIndex: ${firstImage.orderIndex})")

                    val displayImageUri = remember(displayImageUrl) {
                        Uri.parse(displayImageUrl)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        AsyncImage(
                            model = displayImageUri,
                            contentDescription = "投稿画像",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    // クリック時は元画像を拡大表示
                                    onImageClick(Uri.parse(firstImage.imageUrl))
                                },
                            contentScale = ContentScale.Crop
                        )
                        // 複数画像の場合は枚数表示
                        if (images.size > 1) {
                            UserFontText(
                                text = "${images.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        Color.Black.copy(alpha = 0.4f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 3.dp, vertical = 0.dp)
                            )
                        }
                    }
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
                    text = { UserFontText(text = "コピー") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(post.text))
                        isMenuExpanded = false
                    },
                    modifier = itemModifier,
                    contentPadding = itemPadding
                )

                // 「編集」メニュー
                if (post.source == SnsType.LOGLEAF || post.isDeletedFromSns) {
                    DropdownMenuItem(
                        text = { UserFontText(text = "編集") },
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
                    text = { UserFontText(text = if (post.isHidden) "再表示" else "非表示") },
                    onClick = {
                        onSetHidden(!post.isHidden)
                        isMenuExpanded = false
                    },
                    modifier = itemModifier,
                    contentPadding = itemPadding
                )

                // 「削除」メニュー
                if (post.source == SnsType.LOGLEAF || post.isDeletedFromSns) {
                    DropdownMenuItem(
                        text = { UserFontText(text = "削除") },
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