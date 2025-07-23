package com.leaf.logleaf.ui.entry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api // これも必要です
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.logleaf.R
import com.yourpackage.logleaf.ui.components.UserFontText
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class) // ◀◀◀ 1. Experimental APIの警告を抑制します
@Composable
fun PostEntryDialog(
    postText: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onPostSubmit: () -> Unit,
    onDismissRequest: () -> Unit,
    dateTime: ZonedDateTime,
    onDateTimeChange: (ZonedDateTime) -> Unit
) {
    // ----------------------------------------------------------------
    // ▼ この関数内のコードは、前回から変更ありません ▼
    // ----------------------------------------------------------------
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val imeInsets = WindowInsets.ime
    val isKeyboardVisible = imeInsets.getBottom(LocalDensity.current) > 0
    val keyboardHeight = imeInsets.asPaddingValues().calculateBottomPadding()

    val animatedOffset by animateDpAsState(
        targetValue = if (isKeyboardVisible) -(keyboardHeight + 42.dp) else 0.dp,
        label = "PostBoxOffsetAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .offset(y = animatedOffset)
                .clickable(enabled = false) {},
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
                        .focusRequester(focusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                                return@onKeyEvent keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP
                            }
                            false
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

                // ----------------------------------------------------------------
                // ▼ 日時ピッカー関連のロジック（ここが今回の修正点です）▼
                // ----------------------------------------------------------------
                var showDatePicker by remember { mutableStateOf(false) }
                var showTimePicker by remember { mutableStateOf(false) }

                val localDateTime = remember(dateTime) {
                    dateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                }

                // 日付ピッカー本体
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = dateTime.toInstant().toEpochMilli()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showDatePicker = false
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val newDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                                    onDateTimeChange(localDateTime.with(newDate).atZone(ZoneId.systemDefault()))
                                }
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                // 時刻ピッカー本体（AlertDialogでラップする形に修正）
                if (showTimePicker) {
                    val timePickerState = rememberTimePickerState(
                        initialHour = localDateTime.hour,
                        initialMinute = localDateTime.minute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showTimePicker = false
                                val newDateTime = localDateTime.withHour(timePickerState.hour).withMinute(timePickerState.minute)
                                onDateTimeChange(newDateTime.atZone(ZoneId.systemDefault()))
                            }) { Text("保存") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) { Text("キャンセル") }
                        },
                        title = { Text("時刻の選択") },
                        text = {
                            TimePicker(state = timePickerState, layoutType = TimePickerLayoutType.Vertical)
                        }
                    )
                }

                // 日時表示エリア
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onDateTimeChange(ZonedDateTime.now()) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync), // TODO: ic_refresh.xml を drawable に追加してください
                            contentDescription = "現在時刻にリセット",
                            tint = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    UserFontText(
                        text = localDateTime.format(DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    UserFontText(
                        text = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.clickable { showTimePicker = true }
                    )
                }
                // ----------------------------------------------------------------
                // ▲ 日時ピッカー関連のロジックはここまで ▲
                // ----------------------------------------------------------------

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // (...省略... アイコンボタンや投稿ボタンのコードは変更なし)
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(painterResource(id = R.drawable.ic_camera), "カメラ", tint = Color.Gray, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(painterResource(id = R.drawable.ic_image), "ギャラリー", tint = Color.Gray, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(painterResource(id = R.drawable.ic_tag), "タグ", tint = Color.Gray, modifier = Modifier.size(24.dp))
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
                        Icon(painter = painterResource(id = R.drawable.ic_post), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    state: TimePickerState
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(320.dp), // 横幅を固定
        shape = RoundedCornerShape(28.dp), // 角を丸くする
        containerColor = Color.White, // 背景色
        title = {
            Text(
                "時刻を選択",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp)
            )
        },
        text = {
            // TimePicker自体は変更なし
            TimePicker(state = state, layoutType = TimePickerLayoutType.Vertical)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
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