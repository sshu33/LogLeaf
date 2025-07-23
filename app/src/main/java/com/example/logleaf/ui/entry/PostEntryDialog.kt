package com.leaf.logleaf.ui.entry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.logleaf.R
import com.yourpackage.logleaf.ui.components.UserFontText
import java.time.Instant
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

    val keyboardController = LocalSoftwareKeyboardController.current
    val bodyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        bodyFocusRequester.requestFocus()
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
                        .focusRequester(bodyFocusRequester)
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

                var showDatePicker by remember { mutableStateOf(false) }

                var isTimeEditing by remember { mutableStateOf(false) }

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


                // 日時表示エリア
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    UserFontText(
                        text = localDateTime.format(DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp ),
                        color = Color.Gray,
                        modifier = Modifier.clickable { showDatePicker = true }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // --- 2. isTimeEditing の状態によって、表示を切り替える ---
                    if (isTimeEditing) {
                        val keyboardController = LocalSoftwareKeyboardController.current
                        val timeFocusRequester = remember { FocusRequester() }
                        var timeText by remember {
                            val initialString = localDateTime.format(DateTimeFormatter.ofPattern("HHmm"))
                            mutableStateOf(
                                TextFieldValue(
                                    text = initialString,
                                    selection = TextRange(0, initialString.length)
                                )
                            )
                        }

                        val confirmAndFinishEditing = {
                            val text = timeText.text.padEnd(4, '0')
                            val hour = text.substring(0, 2).toIntOrNull()?.coerceIn(0, 23) ?: localDateTime.hour
                            val minute = text.substring(2, 4).toIntOrNull()?.coerceIn(0, 59) ?: localDateTime.minute

                            val newDateTime = localDateTime.withHour(hour).withMinute(minute)
                            onDateTimeChange(newDateTime.atZone(ZoneId.systemDefault()))

                            isTimeEditing = false
                            bodyFocusRequester.requestFocus() // 本文の入力欄にフォーカスを戻す
                            // keyboardController?.show() は不要。フォーカスが当たれば自動でキーボードは切り替わる
                        }

                        // フォーカス状態を監視するための変数
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
                                .onFocusChanged { focusState ->
                                    if (hasFocus && !focusState.isFocused) {
                                        // 以前はフォーカスがあったのに、今は無い -> 外側がタップされた
                                        confirmAndFinishEditing()
                                    }
                                    hasFocus = focusState.isFocused
                                }
                                .width(60.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(vertical = 4.dp)
                        )
                        // --- ▲ 編集モードの時のUI ▲ ---
                    } else {
                        // --- ▼ 通常表示モードの時のUI (ただのテキスト) ▼ ---
                        UserFontText(
                            text = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp ),
                            color = Color.Gray,
                            modifier = Modifier.clickable { isTimeEditing = true } // タップで編集モードに切り替え
                        )
                        // --- ▲ 通常表示モードの時のUI ▲ ---
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onDateTimeChange(ZonedDateTime.now()) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync),
                            contentDescription = "現在時刻にリセット",
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- ▼▼▼ 時間入力欄の見た目を変更 ▼▼▼ ---
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeField == ActiveField.HOUR) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
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
                            if (activeField == ActiveField.MINUTE) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
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