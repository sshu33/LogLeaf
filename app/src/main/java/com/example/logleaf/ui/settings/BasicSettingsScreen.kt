package com.example.logleaf.ui.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.logleaf.MainViewModel
import com.example.logleaf.R
import com.example.logleaf.ui.components.CustomTopAppBar
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicSettingsScreen(
    navController: NavController,
    mainViewModel: MainViewModel
) {
    val context = LocalContext.current

    // 設定項目の状態
    var dayStartHour by remember { mutableIntStateOf(0) } // 日の変わり目時刻（デフォルト: 0時）
    var weekStartDay by remember { mutableStateOf("日曜日") } // 週の始まり（デフォルト: 月曜日）
    var timeFormat by remember { mutableStateOf("24時間") } // 時間表示形式（デフォルト: 24時間）
    var autoTagging by remember { mutableStateOf(true) } // 自動タグ付け（デフォルト: ON）
    var fetchReplies by remember { mutableStateOf(false) } // 返信取得（デフォルト: OFF）
    var dayStartMinute by remember { mutableIntStateOf(0) }


    // ダイアログ表示制御
    var showTimeDialog by remember { mutableStateOf(false) }
    var showWeekDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {

        Spacer(modifier = Modifier.height(30.dp))

        // タイトルバー
        CustomTopAppBar(
            title = "基本の設定",
            onNavigateBack = { navController.popBackStack() }
        )

        // スクロール可能なコンテンツエリア
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 時間設定セクション
            UserFontText(
                text = "時間設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 時間設定項目
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(160.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    // 日の変わり目時刻
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimeDialog = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_time),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "日の変わり目",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${dayStartHour}:${
                                    dayStartMinute.toString().padStart(2, '0')
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 週の始まり
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Log.d("Debug", "週の始まりクリック、現在のweekStartDay: $weekStartDay")
                                showWeekDialog = true
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_week),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "週の始まり",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = weekStartDay,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 時間表示形式
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                timeFormat = if (timeFormat == "24時間") "12時間" else "24時間"
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_date),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "時間表示",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = timeFormat,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SNS設定セクション
            UserFontText(
                text = "SNS設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // SNS設定項目
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(110.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    // 自動タグ付け
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoTagging = !autoTagging }
                            .padding(vertical = 14.dp), // これのまま
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tag),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自動タグ付け",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (autoTagging) "ON" else "OFF",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 返信取得
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fetchReplies = !fetchReplies }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_reply),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "返信を取得",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (fetchReplies) "ON" else "OFF",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

// 日の変わり目時刻選択ダイアログ
    if (showTimeDialog) {
        Dialog(onDismissRequest = { showTimeDialog = false }) {

            var localHour by remember { mutableIntStateOf(dayStartHour) }
            var localMinute by remember { mutableIntStateOf(dayStartMinute) }

            Card(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 日の変わり目時刻選択ダイアログの中身を置き換え
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProperDrumRollPicker(
                            items = (0..23).toList(),
                            selectedIndex = localHour,
                            onSelectionChanged = { localHour = it },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        ProperDrumRollPicker(
                            items = listOf(0, 15, 30, 45),
                            selectedIndex = listOf(0, 15, 30, 45).indexOf(localMinute),
                            onSelectionChanged = { localMinute = listOf(0, 15, 30, 45)[it] },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showTimeDialog = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            dayStartHour = localHour
                            dayStartMinute = localMinute
                            showTimeDialog = false
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }

    // 週の始まり選択ダイアログ
    if (showWeekDialog) {
        Log.d("Debug", "ダイアログ表示、weekStartDay: $weekStartDay")
        Dialog(onDismissRequest = { showWeekDialog = false }) {
            Card(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val weekDays = listOf("日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日")
                    Log.d("Debug", "weekStartDay: $weekStartDay")

                    var localWeekStart by remember { mutableStateOf(weekStartDay) }
                    Log.d("Debug", "localWeekStart: $localWeekStart")
                    Log.d("Debug", "indexOf: ${weekDays.indexOf(localWeekStart)}")

                    StringDrumRollPicker(
                        items = weekDays,
                        selectedIndex = weekDays.indexOf(localWeekStart).let { if (it == -1) 0 else it },
                        onSelectionChanged = {
                            Log.d("Debug", "onSelectionChanged called: $it -> ${weekDays[it]}")
                            localWeekStart = weekDays[it]
                        },
                        modifier = Modifier.height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showWeekDialog = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            weekStartDay = localWeekStart
                            showWeekDialog = false
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProperDrumRollPicker(
    items: List<Int>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialOffset: Int = 1000
) {
    // 初期位置を修正：contentPaddingによる余白を考慮
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex + (initialOffset / items.size) * items.size
    )

    val snapBehavior = rememberSnapFlingBehavior(listState)

    // 中央の項目を監視（contentPaddingとの整合性を保つ）
    val centerItemIndex by remember {
        derivedStateOf {
            // contentPaddingで上に40dpの余白があることを考慮
            val centerIndex = listState.firstVisibleItemIndex
            centerIndex % items.size
        }
    }

    // 選択状態の更新
    LaunchedEffect(centerItemIndex) {
        if (centerItemIndex != selectedIndex) {
            onSelectionChanged(centerItemIndex)
        }
    }

    // スクロール状態を監視して初期表示かどうか判定
    val isInitialDisplay by remember {
        derivedStateOf {
            !listState.isScrollInProgress && listState.firstVisibleItemScrollOffset == 0
        }
    }

    Box(modifier = modifier.height(120.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (isInitialDisplay) (-4).dp else 0.dp), // 初期表示時のみ補正
            contentPadding = PaddingValues(vertical = 40.dp),
            flingBehavior = snapBehavior
        ) {
            items(Int.MAX_VALUE) { index ->
                val actualIndex = index % items.size
                val item = items[actualIndex]

                // 中央位置かどうかを判定（contentPaddingと一致）
                val isCenterPosition = index == listState.firstVisibleItemIndex

                Text(
                    text = item.toString().padStart(2, '0'),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    color = if (isCenterPosition) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = if (isCenterPosition) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // 中央線表示
        Divider(
            thickness = 2.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Divider(
            thickness = 2.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 20.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StringDrumRollPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialOffset: Int = 1000
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex + (initialOffset / items.size) * items.size
    )

    val isInitialDisplay by remember {
        derivedStateOf {
            !listState.isScrollInProgress && listState.firstVisibleItemScrollOffset == 0
        }
    }

    val snapBehavior = rememberSnapFlingBehavior(listState)

    val centerItemIndex by remember {
        derivedStateOf {
            val centerIndex = listState.firstVisibleItemIndex
            val result = centerIndex % items.size
            Log.d("Debug", "firstVisibleItemIndex: $centerIndex, centerItemIndex: $result")
            result
        }
    }

    LaunchedEffect(centerItemIndex) {
        if (centerItemIndex != selectedIndex && !listState.isScrollInProgress) {
            onSelectionChanged(centerItemIndex)
        }
    }

    Box(modifier = modifier.height(120.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (isInitialDisplay) (-6).dp else 0.dp),
            contentPadding = PaddingValues(vertical = 40.dp),
            flingBehavior = snapBehavior
        ) {
            items(Int.MAX_VALUE) { index ->
                val actualIndex = index % items.size
                val item = items[actualIndex]
                val isCenterPosition = index == listState.firstVisibleItemIndex

                Text(
                    text = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    color = if (isCenterPosition) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = if (isCenterPosition) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Divider(
            thickness = 2.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Divider(
            thickness = 2.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 20.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    }
}