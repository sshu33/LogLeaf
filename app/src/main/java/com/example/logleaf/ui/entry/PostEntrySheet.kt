package com.example.logleaf.ui.entry // ★あなたのパッケージ名に合わせてください

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PostEntryDialog(
    onDismissRequest: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val currentDateTime = remember { LocalDateTime.now() }
    val formatter = remember { DateTimeFormatter.ofPattern("M月d日(E) HH:mm", Locale.JAPANESE) }
    val formattedDateTime = remember { currentDateTime.format(formatter) }

    // ダイアログの表示状態を内部で管理し、アニメーションを制御
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Composition後、即座にアニメーションを開始
        isVisible = true
    }

    // 閉じる処理を共通化
    val closeDialog = {
        // まずアニメーションでコンポーネントを消す
        isVisible = false
    }

    // アニメーション完了後に、親に通知してDialog自体を破棄させる
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            // アニメーションが終わるのを待つ
            delay(250) // exitアニメーションの時間
            onDismissRequest()
        }
    }

    // キーボードの表示・非表示を、isVisibleと同期させる
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(100) // コンポーネントの準備を待つ
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusRequester.freeFocus()
            keyboardController?.hide()
        }
    }

    // 静止したDialogの上で、全てを我々が制御する
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // ★キーボードの盾をここに装備！
            // 背景タップで閉じるための、見えない下地
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = closeDialog
            ),
        contentAlignment = Alignment.Center // ★すべてのコンテンツを中央に配置！
    ) {
        // 黒ベールのアニメーション
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            )
        }

        // 投稿BOXのアニメーション
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(250)) + scaleIn(initialScale = 0.9f, animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.9f, animationSpec = tween(250))
        ) {
            // Card自体がクリックされて背景にイベントが貫通するのを防ぐ
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .clickable(enabled = false) {}, // クリックを消費する
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 350.dp)
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
                                        return@onKeyEvent true
                                    }
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
                    )
                    Text(
                        text = formattedDateTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* TODO */ }) { Icon(Icons.Outlined.PhotoCamera, "カメラ", tint = Color.Gray) }
                        IconButton(onClick = { /* TODO */ }) { Icon(Icons.Outlined.AddPhotoAlternate, "ギャラリー", tint = Color.Gray) }
                        IconButton(onClick = { /* TODO */ }) { Icon(Icons.Outlined.Tag, "タグ", tint = Color.Gray) }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = { /* TODO */ }, shape = RoundedCornerShape(8.dp)) { Text("投稿") }
                    }
                }
            }
        }
    }
}