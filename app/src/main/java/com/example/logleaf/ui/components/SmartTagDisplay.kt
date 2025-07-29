package com.example.logleaf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.logleaf.R
import com.example.logleaf.ui.entry.Tag
import com.yourpackage.logleaf.ui.components.UserFontText

@Composable
fun SmartTagDisplay(
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    maxWidth: Dp = 200.dp, // タグエリアの最大幅
    modifier: Modifier = Modifier
) {
    var showAllTagsDialog by remember { mutableStateOf(false) }

    // タグを短い順にソート
    val sortedTags = remember(tags) {
        tags.sortedBy { it.tagName.length }
    }

    // 表示できるタグと残りのタグを計算
    val (visibleTags, hiddenTags) = remember(sortedTags, maxWidth) {
        calculateVisibleTags(sortedTags, maxWidth)
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // 表示可能なタグを表示
        visibleTags.forEach { tag ->
            LogViewTagChip(
                tag = tag,
                onClick = { onTagClick(tag.tagName) }
            )
        }

        // 残りのタグがある場合「+N」チップを表示
        if (hiddenTags.isNotEmpty()) {
            Box {
                MoreTagsChip(
                    count = hiddenTags.size,
                    onClick = { showAllTagsDialog = true }
                )

                // 隠れたタグだけ表示するDropdownMenu
                AllTagsDropdown(
                    tags = hiddenTags, // 隠れたタグのみ
                    expanded = showAllTagsDialog,
                    onTagClick = onTagClick,
                    onDismiss = { showAllTagsDialog = false }
                )
            }
        }
    }
}

/**
 * 表示可能なタグを計算する関数
 * 表示: 短い順、隠れたタグ: 長い順でソート
 */
private fun calculateVisibleTags(tags: List<Tag>, maxWidth: Dp): Pair<List<Tag>, List<Tag>> {
    // 短い順にソート
    val sortedByShort = tags.sortedBy { it.tagName.length }

    // 短いタグから3個まで表示
    val visibleTags = sortedByShort.take(3)

    // 残りのタグを長い順にソート（DropdownMenuの幅調整のため）
    val hiddenTags = sortedByShort.drop(3).sortedByDescending { it.tagName.length }

    return Pair(visibleTags, hiddenTags)
}

/**
 * ログビュー用タグチップ（SmartTagDisplay用）
 */
@Composable
private fun LogViewTagChip(
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
                tint = MaterialTheme.colorScheme.primary, // プライマリカラー
                modifier = Modifier.size(12.dp) // さらに小さめサイズ
            )
            UserFontText(
                text = tag.tagName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * 「+N」チップコンポーネント
 */
@Composable
private fun MoreTagsChip(
    count: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

/**
 * 隠れたタグ表示DropdownMenu（コンパクト版）
 */
@Composable
private fun AllTagsDropdown(
    tags: List<Tag>,
    expanded: Boolean,
    onTagClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .wrapContentWidth() // 可変幅
            .widthIn(min = 0.dp) // 最小幅を0に設定
            .heightIn(max = 162.dp) // 高さ制限（約4-5個分）
            .background(
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        tags.forEach { tag ->
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tag),
                            contentDescription = "タグアイコン",
                            tint = MaterialTheme.colorScheme.primary, // プライマリカラー
                            modifier = Modifier.size(14.dp) // 小さめサイズ
                        )
                        UserFontText(
                            text = tag.tagName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray, // タグ名はグレー
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                onClick = {
                    onTagClick(tag.tagName)
                    onDismiss()
                },
                modifier = Modifier
                    .height(32.dp) // アイテム高さを小さく
                    .wrapContentWidth(), // アイテムも可変幅
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp) // 左右余白をさらに小さく
            )
        }
    }
}

/**
 * 隠れたタグ表示Popup（カスタム版）
 */
@Composable
private fun AllTagsPopup(
    tags: List<Tag>,
    expanded: Boolean,
    onTagClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (expanded) {
        Popup(
            alignment = Alignment.TopStart,
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .wrapContentSize() // 完全に内容に合わせたサイズ
                    .heightIn(max = 200.dp), // 高さ制限のみ
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.wrapContentSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(tags) { tag ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTagClick(tag.tagName)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "#${tag.tagName}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}