package com.example.logleaf.ui.widget

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.logleaf.R
import com.example.logleaf.ui.entry.Tag
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WidgetPostDialog(
    visible: Boolean,
    postText: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onPostSubmit: (String?, List<String>) -> Unit,
    onDismiss: () -> Unit,
    dateTime: ZonedDateTime,
    onDateTimeChange: (ZonedDateTime) -> Unit,
    onRevertDateTime: () -> Unit,
    currentTags: List<Tag>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Tag) -> Unit,
    favoriteTags: List<Tag>,
    frequentlyUsedTags: List<Tag>,
    onToggleFavorite: (Tag) -> Unit,
    selectedImageUris: List<Uri>,
    onLaunchPhotoPicker: () -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onImageRemoved: (Int) -> Unit,
    onImageReordered: (Int, Int) -> Unit,
    onCreateCameraImageUri: () -> Uri,
    requestFocus: Boolean,
    onFocusConsumed: () -> Unit
) {
    // PostEntryDialogから必要な状態だけコピー
    var isTagEditorVisible by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var isSuggestionVisible by remember { mutableStateOf(false) }
    var isCalendarVisible by remember { mutableStateOf(false) }
    var isTimeEditing by remember { mutableStateOf(false) }
    var dateRowPosition by remember { mutableStateOf(IntOffset.Zero) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // 時刻編集用の状態
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
        focusRequester.requestFocus()
    }

    // 自動フォーカス
    LaunchedEffect(visible) {
        if (visible) {
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // フォーカスエラーを無視
            }
        }
    }

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 軽い黒ベール：即座にフェードイン/アウト
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(100))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (isCalendarVisible) {
                                        isCalendarVisible = false
                                        return@clickable
                                    }
                                    if (isTimeEditing) {
                                        confirmAndFinishEditing()
                                        return@clickable
                                    }
                                    onDismiss()
                                }
                            )
                    )
                }

                // 軽いダイアログカード：下からスライド
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(250)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(200)
                    ),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(bottom = 100.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* カードクリックは何もしない */ }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        // ★★★ すべてのコンテンツを一つのColumnに収める ★★★
                        Column(modifier = Modifier.padding(16.dp)) {
                            // メインのテキスト入力欄
                            TextField(
                                value = postText,
                                onValueChange = onTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 200.dp)
                                    .focusRequester(focusRequester),
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

                            // 画像表示エリア（シンプル版）
                            AnimatedVisibility(visible = selectedImageUris.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(selectedImageUris) { index, uri ->
                                        Box(modifier = Modifier.size(80.dp)) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "選択された画像 ${index + 1}",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )

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
                                                        .size(30.dp)
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .clickable { onImageRemoved(index) }
                                                )

                                                if (index == 0) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_star_filled),
                                                        contentDescription = "メイン画像",
                                                        tint = Color(0xFFfbd144),
                                                        modifier = Modifier
                                                            .size(25.dp)
                                                            .align(Alignment.BottomStart)
                                                            .padding(4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 現在のタグ表示
                            if (currentTags.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    currentTags.forEach { tag ->
                                        WidgetTagChip(tag = tag, onRemove = { onRemoveTag(tag) })
                                    }
                                }
                            }

                            // タグエディター
                            AnimatedVisibility(visible = isTagEditorVisible) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    // 手動入力欄
                                    BasicTextField(
                                        value = tagInput,
                                        onValueChange = { tagInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (tagInput.isNotBlank()) {
                                                    onAddTag(tagInput)
                                                    tagInput = ""
                                                }
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_tag),
                                                    contentDescription = "タグアイコン",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Box(modifier = Modifier.weight(1f)) {
                                                    if (tagInput.isEmpty()) {
                                                        Text(
                                                            "タグを追加...(Enterで確定)",
                                                            color = Color.LightGray
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                                if (favoriteTags.isNotEmpty()) {
                                                    Spacer(Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = { isSuggestionVisible = !isSuggestionVisible },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_star_filled),
                                                            contentDescription = "お気に入りタグ",
                                                            tint = if (isSuggestionVisible) MaterialTheme.colorScheme.primary else Color.Gray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    // お気に入りタグ表示（条件付き）
                                    AnimatedVisibility(visible = isSuggestionVisible && favoriteTags.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            items(favoriteTags.size) { index ->
                                                val tag = favoriteTags[index]
                                                FavoriteTagButton(
                                                    tag = tag,
                                                    onClick = {
                                                        onAddTag(tag.tagName)
                                                        isSuggestionVisible = false // タップしたら閉じる
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // ★★★ 日時表示をColumnの中に移動 ★★★
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
                                    BasicTextField(
                                        value = timeText,
                                        onValueChange = { newValue ->
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
                                            onDone = { confirmAndFinishEditing() }
                                        ),
                                        modifier = Modifier
                                            .width(60.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(vertical = 4.dp)
                                    )
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

                                Spacer(modifier = Modifier.width(2.dp))
                            }

                            // ★★★ 下部ボタンもColumnの中に移動 ★★★
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { /* カメラ機能は後で */ }) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_camera),
                                        "カメラ",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = onLaunchPhotoPicker) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_image),
                                        "ギャラリー",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = { isTagEditorVisible = !isTagEditorVisible }) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_tag),
                                        "タグ",
                                        tint = if (isTagEditorVisible || currentTags.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        val tagsToSubmit = currentTags.map { it.tagName }.toMutableList()
                                        if (tagInput.isNotBlank()) {
                                            tagsToSubmit.add(tagInput.trim())
                                            onAddTag(tagInput)
                                            tagInput = ""
                                        }

                                        if (isTimeEditing) {
                                            onPostSubmit(timeText.text, tagsToSubmit)
                                        } else {
                                            onPostSubmit(null, tagsToSubmit)
                                        }
                                    },
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
                        } // ★★★ Columnをここで閉じる ★★★
                    }
                }

                // シンプルなカレンダーポップアップ（重いアニメーションなし）
                if (isCalendarVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { isCalendarVisible = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* カードクリックは何もしない */ }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            MinimalCalendar(
                                selectedDate = confirmedDateTime.toLocalDate(),
                                onDateSelected = { newDate ->
                                    onDateTimeChange(
                                        confirmedDateTime.with(newDate).atZone(ZoneId.systemDefault())
                                    )
                                    isCalendarVisible = false
                                },
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetTagChip(
    tag: Tag,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tag),
                contentDescription = "タグアイコン",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            UserFontText(
                text = tag.tagName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "タグを削除",
                tint = Color.Gray,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove
                    )
            )
        }
    }
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
            .background(Color.White)
    ) {
        // 月移動のヘッダー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "前の月",
                    tint = Color.Black
                )
            }
            UserFontText(
                text = displayedMonth.format(
                    DateTimeFormatter.ofPattern("yyyy年 MMMM", Locale.JAPAN)
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "次の月",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 曜日のヘッダー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            listOf("日", "月", "火", "水", "木", "金", "土").forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    UserFontText(
                        text = day,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 日付グリッド
        val daysInMonth = displayedMonth.lengthOfMonth()
        val firstDayOfMonth = displayedMonth.atDay(1).dayOfWeek.value % 7
        val dayCells = (1..firstDayOfMonth).map { null } + (1..daysInMonth).map { displayedMonth.atDay(it) }
        val weekChunks = dayCells.chunked(7)

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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// お気に入りタグボタンのコンポーネント
@Composable
private fun FavoriteTagButton(
    tag: Tag,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tag),
                contentDescription = "タグアイコン",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            UserFontText(
                text = tag.tagName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}