package com.example.logleaf.ui.entry

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PostEntrySheet(
    onDismissRequest: () -> Unit
) {
    // --- 変数定義 (シンプルに) ---
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val currentDateTime = remember { LocalDateTime.now() }
    val formatter = remember { DateTimeFormatter.ofPattern("M月d日(E) HH:mm", Locale.JAPANESE) }
    val formattedDateTime = remember { currentDateTime.format(formatter) }

    // ★★★ 我々の、勝利の、すべては、この、Boxに、ある ★★★
    Box(
        modifier = Modifier
            .fillMaxSize()
            // ★★★ 敵のいない世界で、正規軍が、すべてを、支配する ★★★
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        // ★★★ そして、常に、下へ ★★★
        contentAlignment = Alignment.BottomCenter
    ) {
        // 背景の黒ベール
        BackHandler(onBack = onDismissRequest)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        )

        // offsetも、複雑な計算も、何もいらない。ただ、そこに、あるだけの、カード。
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                // 美しさのための、最後の、余白
                .padding(bottom = 24.dp)
                .wrapContentHeight(),
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
                        .focusRequester(focusRequester),
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

        // 起動時のキーボード表示
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}