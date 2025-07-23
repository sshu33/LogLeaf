package com.leaf.logleaf.ui.entry

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.example.logleaf.R
import com.yourpackage.logleaf.ui.components.UserFontText
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEntryDialog(
    postText: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onPostSubmit: () -> Unit,
    onDismissRequest: () -> Unit,
    dateTime: ZonedDateTime,
    onDateTimeChange: (ZonedDateTime) -> Unit,
    onRevertDateTime: () -> Unit,
    selectedImageUri: Uri?,
    onLaunchPhotoPicker: () -> Unit,
    onImageSelected: (Uri?) -> Unit,
    requestFocus: Boolean,
    onFocusConsumed: () -> Unit
) {


    val keyboardController = LocalSoftwareKeyboardController.current
    val bodyFocusRequester = remember { FocusRequester() }
    var isCalendarVisible by remember { mutableStateOf(false) }
    var isTimeEditing by remember { mutableStateOf(false) }
    val confirmedDateTime = remember(dateTime) {
        dateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    }
    var timeText by remember {
        val initialString = confirmedDateTime.format(DateTimeFormatter.ofPattern("HHmm"))
        mutableStateOf(
            TextFieldValue(
                text = initialString,
                selection = TextRange(0, initialString.length)
            )
        )
    }
    val confirmAndFinishEditing = {
        val text = timeText.text.padEnd(4, '0')
        val hour = text.substring(0, 2).toIntOrNull()?.coerceIn(0, 23) ?: confirmedDateTime.hour
        val minute = text.substring(2, 4).toIntOrNull()?.coerceIn(0, 59) ?: confirmedDateTime.minute
        val newDateTime = confirmedDateTime.withHour(hour).withMinute(minute)
        onDateTimeChange(newDateTime.atZone(ZoneId.systemDefault()))
        isTimeEditing = false
        bodyFocusRequester.requestFocus()
    }
    var dateRowPosition by remember { mutableStateOf(IntOffset.Zero) }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            bodyFocusRequester.requestFocus()
            keyboardController?.show()
            onFocusConsumed() // トリガーをリセット
        }
    }

    LaunchedEffect(isCalendarVisible, isTimeEditing) {
        if (!isCalendarVisible && !isTimeEditing) {
            bodyFocusRequester.requestFocus()
        }
    }

    val imeInsets = WindowInsets.ime
    val isKeyboardVisible = imeInsets.getBottom(LocalDensity.current) > 0
    val keyboardHeight = imeInsets.asPaddingValues().calculateBottomPadding()
    val animatedOffset by animateDpAsState(
        targetValue = if (isKeyboardVisible) -(keyboardHeight + 42.dp) else 0.dp,
        label = "PostBoxOffsetAnimation"
    )

    // --- ▼▼▼ ここからが正しいレイアウト構造です ▼▼▼ ---
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 背景の黒ベール (タップでダイアログを閉じる機能を持つ)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isCalendarVisible) isCalendarVisible = false
                        if (isTimeEditing) {
                            confirmAndFinishEditing() // 確定処理を呼ぶ
                        } else if (!isCalendarVisible) {
                            onDismissRequest()
                        }
                    }
                )
        ) {
            AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                )
            }
        }

        // 2. 投稿BOX本体
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = animatedOffset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .clickable(
                        enabled = isTimeEditing, // ◀◀◀ 時刻編集中だけクリック可能にする
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // 波紋エフェクトはなし
                        onClick = {
                            if (isTimeEditing) {
                                confirmAndFinishEditing() // ◀◀◀ タップで確定処理を呼ぶ
                            }
                        }
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextField(
                        value = postText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 350.dp)
                            .focusRequester(bodyFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && isTimeEditing) {
                                    confirmAndFinishEditing()
                                }
                            },
                        placeholder = null,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                    )

                    AnimatedVisibility(visible = selectedImageUri != null) {
                        // 画像をタップで削除できるように、Boxで囲む
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(80.dp) // ◀◀◀ サムネイルのサイズを小さく固定
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageSelected(null) } // ◀◀◀ タップで削除
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "選択された画像",
                                modifier = Modifier.fillMaxSize(), // Boxのサイズに合わせる
                                contentScale = ContentScale.Crop
                            )
                            // 「削除」の目印として、半透明の×アイコンを右上に表示
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "画像を削除",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                    }

                    // あなたが調整したカスタムUIをここに配置
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp, bottom = 8.dp)
                            .onGloballyPositioned { coordinates ->
                                val positionInRoot = coordinates.positionInRoot()
                                dateRowPosition = IntOffset(
                                    positionInRoot.x.roundToInt(),
                                    positionInRoot.y.roundToInt()
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserFontText(
                            text = confirmedDateTime.format(DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color.Gray,
                            modifier = Modifier.clickable {
                                isCalendarVisible = true
                                isTimeEditing = false
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isTimeEditing) {
                            val keyboardController = LocalSoftwareKeyboardController.current
                            val timeFocusRequester = remember { FocusRequester() }
                            var hasFocus by remember { mutableStateOf(false) }

                            LaunchedEffect(Unit) {
                                timeFocusRequester.requestFocus()
                            }

                            BasicTextField(
                                value = timeText, // valueにTextFieldValueを渡す
                                onValueChange = { newValue ->
                                    // --- ★★★ 修正点②：4桁制限の判定を .text で行う ★★★ ---
                                    if (newValue.text.length <= 4 && newValue.text.all { it.isDigit() }) {
                                        timeText = newValue
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { confirmAndFinishEditing() } // エンターキーで確定
                                ),
                                modifier = Modifier
                                    .focusRequester(timeFocusRequester)
                                    .width(60.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(vertical = 4.dp)
                            )
                            // --- ▲ 編集モードの時のUI ▲ ---
                        } else {
                            UserFontText(
                                text = confirmedDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.Gray,
                                modifier = Modifier.clickable { isTimeEditing = true }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { onRevertDateTime() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_sync),
                                contentDescription = "元に戻す",
                                tint = Color.Gray
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(
                                painterResource(id = R.drawable.ic_camera),
                                "カメラ",
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { onLaunchPhotoPicker() }) {
                            Icon(
                                painterResource(id = R.drawable.ic_image),
                                "ギャラリー",
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(
                                painterResource(id = R.drawable.ic_tag),
                                "タグ",
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = onPostSubmit,
                            enabled = postText.text.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledContentColor = Color.White
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_post),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isCalendarVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isCalendarVisible = false }
                    )
            )
        }

        // 3. カレンダーPopup (投稿BOXとは完全に独立)
        if (isCalendarVisible) {
            val popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize
                ): IntOffset {
                    // dateRowPositionを使用してタップ位置の上に配置
                    val targetY = (dateRowPosition.y - popupContentSize.height - 16).coerceAtLeast(50)
                    val targetX = (windowSize.width - popupContentSize.width) / 2

                    return IntOffset(targetX, targetY)
                }
            }
            Popup(
                popupPositionProvider = popupPositionProvider,
                properties = PopupProperties(focusable = false),
                onDismissRequest = { isCalendarVisible = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    MinimalCalendar(
                        selectedDate = confirmedDateTime.toLocalDate(),
                        onDateSelected = { newDate ->
                            onDateTimeChange(confirmedDateTime.with(newDate).atZone(ZoneId.systemDefault()))
                            isCalendarVisible = false
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color.White) // 追加：明示的に白背景を設定
                    )
                }
            }
        }
    }
}

private enum class ActiveField { HOUR, MINUTE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    initialHour: Int,
    initialMinute: Int
) {
    // --- ▼▼▼ ここからが変更箇所です ▼▼▼ ---
    var hour by remember { mutableStateOf(initialHour.toString().padStart(2, '0')) }
    var minute by remember { mutableStateOf(initialMinute.toString().padStart(2, '0')) }

    var activeField by remember { mutableStateOf(ActiveField.HOUR) }

    // フォーカスをプログラムで制御するための準備
    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }

    // activeFieldが変更されたら、対応する入力欄にフォーカスを当てる
    LaunchedEffect(activeField) {
        if (activeField == ActiveField.HOUR) {
            hourFocusRequester.requestFocus()
        } else {
            minuteFocusRequester.requestFocus()
        }
    }
    // --- ▲▲▲ ここまでが変更箇所です ▲▲▲ ---

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Text(
                "時刻を選択",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp)
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- ▼▼▼ 時間入力欄の見た目を変更 ▼▼▼ ---
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeField == ActiveField.HOUR) MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.1f
                            ) else Color.Transparent
                        )
                        .clickable { activeField = ActiveField.HOUR }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = hour,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                hour = newValue
                                // 2桁入力されたら、自動で「分」の入力欄にフォーカスを移動
                                if (newValue.length == 2) {
                                    activeField = ActiveField.MINUTE
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.displayLarge.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(96.dp)
                            .focusRequester(hourFocusRequester)
                    )
                }

                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // --- ▼▼▼ 分入力欄の見た目を変更 ▼▼▼ ---
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeField == ActiveField.MINUTE) MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.1f
                            ) else Color.Transparent
                        )
                        .clickable { activeField = ActiveField.MINUTE }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = minute,
                        onValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                minute = newValue
                            }
                        },
                        textStyle = MaterialTheme.typography.displayLarge.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(96.dp)
                            .focusRequester(minuteFocusRequester)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalHour = hour.toIntOrNull() ?: initialHour
                    val finalMinute = minute.toIntOrNull() ?: initialMinute
                    val validatedHour = finalHour.coerceIn(0, 23)
                    val validatedMinute = finalMinute.coerceIn(0, 59)
                    onConfirm(validatedHour, validatedMinute)
                },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            ) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun MinimalCalendar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMonth = YearMonth.from(selectedDate)
    var displayedMonth by remember { mutableStateOf(currentMonth) }

    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
            .background(Color.White) // 追加：明示的に白背景を設定
    ) {
        // --- 1. 月移動のヘッダー ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White), // 追加：ヘッダーも白背景
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "前の月",
                    tint = Color.Black // 追加：アイコンを黒色に
                )
            }
            UserFontText(
                text = displayedMonth.format(
                    DateTimeFormatter.ofPattern(
                        "yyyy年 MMMM",
                        Locale.JAPAN
                    )
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black // 追加：テキストを黒色に
            )
            IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "次の月",
                    tint = Color.Black // 追加：アイコンを黒色に
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 2. 曜日のヘッダー ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White) // 追加：曜日ヘッダーも白背景
        ) {
            listOf("日", "月", "火", "水", "木", "金", "土").forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    UserFontText(
                        text = day,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black // 追加：曜日テキストを黒色に
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- 3. 日付グリッド ---
        val daysInMonth = displayedMonth.lengthOfMonth()
        val firstDayOfMonth = displayedMonth.atDay(1).dayOfWeek.value % 7
        val dayCells =
            (1..firstDayOfMonth).map { null } + (1..daysInMonth).map { displayedMonth.atDay(it) }

        val weekChunks = dayCells.chunked(7) // 事前に計算してローカル変数に保存

        weekChunks.forEachIndexed { weekIndex, week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onDateSelected(date) }
                                    .background(
                                        if (date == selectedDate) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .wrapContentSize(Alignment.Center),
                                color = if (date == selectedDate) MaterialTheme.colorScheme.onPrimary else Color.Black,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                // 足りない分のスペーサーを追加
                if (week.size < 7) {
                    repeat(7 - week.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 最後の週以外は行間スペーサーを追加
            if (weekIndex < weekChunks.size - 1) {
                Spacer(modifier = Modifier.height(8.dp)) // ここで余白を調整
            }
        }
    }
}