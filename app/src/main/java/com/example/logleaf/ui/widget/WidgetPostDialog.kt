package com.example.logleaf.ui.widget

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.logleaf.R
import com.example.logleaf.ui.entry.Tag
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
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
    // === 状態管理 ===
    var isTagEditorVisible by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var isSuggestionVisible by remember { mutableStateOf(false) }
    var isTimeEditing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // === 時刻編集 ===
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
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // フォーカスエラーを無視
        }
    }

    // === フォーカス管理 ===
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

    // === 投稿実行関数 ===
    val executePost: () -> Unit = {
        val tagsToSubmit = currentTags.map { it.tagName }.toMutableList()
        if (tagInput.isNotBlank()) {
            tagsToSubmit.add(tagInput.trim())
            onAddTag(tagInput)
            tagInput = ""
        }

        scope.launch {
            focusManager.clearFocus()
            keyboardController?.hide()
            delay(50)

            if (isTimeEditing) {
                onPostSubmit(timeText.text, tagsToSubmit)
            } else {
                onPostSubmit(null, tagsToSubmit)
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
                // === 黒ベール ===
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
                                    if (isTimeEditing) {
                                        confirmAndFinishEditing()
                                        return@clickable
                                    }
                                    onDismiss()
                                }
                            )
                    )
                }

                // === メインダイアログ ===
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
                                enabled = isTimeEditing,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (isTimeEditing) confirmAndFinishEditing() }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            // === メイン入力エリア ===
                            TextField(
                                value = postText,
                                onValueChange = onTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 180.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && isTimeEditing) {
                                            confirmAndFinishEditing()
                                        }
                                    },
                                placeholder = {
                                    Text(
                                        "本文を入力...",
                                        color = Color.Gray.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                singleLine = false,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (postText.text.isNotBlank()) {
                                            executePost()
                                        }
                                    }
                                )
                            )

                            // === 現在のタグ表示 ===
                            if (currentTags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    currentTags.forEach { tag ->
                                        WidgetTagChip(tag = tag, onRemove = { onRemoveTag(tag) })
                                    }
                                }
                            }

                            // === タグ入力エリア ===
                            AnimatedVisibility(visible = isTagEditorVisible) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    // タグ入力欄
                                    BasicTextField(
                                        value = tagInput,
                                        onValueChange = { tagInput = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused) {
                                                    if (tagInput.isNotBlank()) {
                                                        onAddTag(tagInput)
                                                        tagInput = ""
                                                    }
                                                }
                                            },
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
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        Color.Gray.copy(alpha = 0.1f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_tag),
                                                    contentDescription = "タグ",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Box(modifier = Modifier.weight(1f)) {
                                                    if (tagInput.isEmpty()) {
                                                        Text("タグを追加...", color = Color.Gray.copy(alpha = 0.6f))
                                                    }
                                                    innerTextField()
                                                }

                                                // サジェスト表示ボタン
                                                IconButton(
                                                    onClick = { isSuggestionVisible = !isSuggestionVisible },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(
                                                            id = if (isSuggestionVisible) R.drawable.ic_arrowup else R.drawable.ic_arrowdown
                                                        ),
                                                        contentDescription = "タグサジェスト",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    // お気に入りタグサジェスト
                                    AnimatedVisibility(visible = isSuggestionVisible) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .background(Color.White, RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            if (favoriteTags.isNotEmpty()) {
                                                LazyRow(
                                                    modifier = Modifier.heightIn(max = 120.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    items(favoriteTags.size) { index ->
                                                        val tag = favoriteTags[index]
                                                        WidgetFavoriteTag(
                                                            tag = tag,
                                                            onClick = {
                                                                onAddTag(tag.tagName)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // === 日時 + アクションエリア ===
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 時刻表示
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UserFontText(
                                        text = confirmedDateTime.format(DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        modifier = Modifier
                                            .background(
                                                Color.Gray.copy(alpha = 0.1f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    // 時刻
                                    if (isTimeEditing) {
                                        val timeFocusRequester = remember { FocusRequester() }

                                        LaunchedEffect(Unit) {
                                            timeFocusRequester.requestFocus()
                                        }

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
                                                .focusRequester(timeFocusRequester)
                                                .width(60.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    } else {
                                        UserFontText(
                                            text = confirmedDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray,
                                            modifier = Modifier
                                                .background(
                                                    Color.Gray.copy(alpha = 0.1f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { isTimeEditing = true }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // リセットボタン
                                    IconButton(
                                        onClick = { onRevertDateTime() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_sync),
                                            contentDescription = "時刻をリセット",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // アクションボタン
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // タグボタン
                                    IconButton(
                                        onClick = { isTagEditorVisible = !isTagEditorVisible },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_tag),
                                            contentDescription = "タグ",
                                            tint = if (isTagEditorVisible || currentTags.isNotEmpty()) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.Gray
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // 投稿ボタン
                                    Button(
                                        onClick = executePost,
                                        enabled = postText.text.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .width(56.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_post),
                                            contentDescription = "投稿",
                                            tint = Color.White, // ← この行追加
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// === ウィジェット専用タグチップ ===
@Composable
private fun WidgetTagChip(
    tag: Tag,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        UserFontText(
            text = "#${tag.tagName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "削除",
            tint = MaterialTheme.colorScheme.primary,
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

// === お気に入りタグ（サジェスト用） ===
@Composable
private fun WidgetFavoriteTag(
    tag: Tag,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_star_filled),
            contentDescription = "お気に入り",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(10.dp)
        )
        UserFontText(
            text = tag.tagName,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}