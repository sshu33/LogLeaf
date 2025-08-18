package com.example.logleaf.ui.components

import android.util.Log
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
    availableWidth: Dp,
    modifier: Modifier = Modifier
) {
    // ★ まず最初にこのログを追加
    Log.d("DEBUG", "SmartTagDisplay呼び出し: タグ数=${tags.size}, 初期幅=${availableWidth}dp")
    Log.d("DEBUG", "処理開始")

    var showAllTagsDialog by remember { mutableStateOf(false) }
    Log.d("DEBUG", "状態初期化完了")

    var actualAvailableWidth by remember { mutableStateOf(availableWidth) }
    Log.d("DEBUG", "実際幅初期化: ${actualAvailableWidth}dp")

    val density = LocalDensity.current
    Log.d("DEBUG", "density取得完了") // ★ ここで取得

    // タグを短い順にソート
    val sortedTags = remember(tags) {
        tags.sortedBy { it.tagName.length }
    }

    // 実際の測定結果で表示できるタグを決定
    val (visibleTags, hiddenTags) = remember(sortedTags, actualAvailableWidth) {
        calculateVisibleTagsWithMeasurement(sortedTags, actualAvailableWidth)
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
        // ★ onGloballyPositionedを完全に削除
    ) {
        Log.d("DEBUG", "FlowRow内部開始: 表示タグ数=${visibleTags.size}")

        // 表示可能なタグを表示
        visibleTags.forEach { tag ->
            Log.d("DEBUG", "タグ描画: ${tag.tagName}")
            LogViewTagChip(
                tag = tag,
                onClick = { onTagClick(tag.tagName) }
            )
        }

        // 残りのタグがある場合「+N」チップを表示
        if (hiddenTags.isNotEmpty()) {
            Log.d("DEBUG", "+Nチップ描画: +${hiddenTags.size}")
            Box {
                MoreTagsChip(
                    count = hiddenTags.size,
                    onClick = { showAllTagsDialog = true }
                )

                // 隠れたタグだけ表示するDropdownMenu
                AllTagsDropdown(
                    tags = hiddenTags,
                    expanded = showAllTagsDialog,
                    onTagClick = onTagClick,
                    onDismiss = { showAllTagsDialog = false }
                )
            }
        }
    }
}

/**
 * 実際の測定幅を使って表示可能なタグを決定
 */
private fun calculateVisibleTagsWithMeasurement(tags: List<Tag>, availableWidth: Dp): Pair<List<Tag>, List<Tag>> {
    if (availableWidth <= 20.dp) { // 最小幅チェック
        return Pair(emptyList(), tags)
    }

    val visibleTags = mutableListOf<Tag>()
    val tagSpacing = 4.dp
    val moreChipWidth = 40.dp // "+N"チップの安全な幅
    val safetyMargin = 16.dp // 安全マージン

    // 実際に使える幅
    val usableWidth = availableWidth - safetyMargin
    var currentUsedWidth = 0.dp

    tags.forEach { tag ->
        // より正確なタグチップ幅の推定（日本語対応）
        val iconWidth = 12.dp
        val iconSpacing = 3.dp
        val horizontalPadding = 12.dp
        val estimatedTextWidth = (tag.tagName.length * 11).dp // 日本語考慮で増加
        val estimatedTagWidth = iconWidth + iconSpacing + estimatedTextWidth + horizontalPadding

        val widthWithThisTag = currentUsedWidth + estimatedTagWidth + if (currentUsedWidth > 0.dp) tagSpacing else 0.dp

        // 残りタグがある場合は+Nチップの余地も考慮
        val remainingTags = tags.size - visibleTags.size
        val needsMoreChip = remainingTags > 1
        val reservedWidth = if (needsMoreChip) moreChipWidth + tagSpacing else 0.dp

        if (widthWithThisTag + reservedWidth <= usableWidth) {
            visibleTags.add(tag)
            currentUsedWidth = widthWithThisTag
        } else {
            // これ以上入らない
            return@forEach
        }
    }

    val hiddenTags = tags.drop(visibleTags.size).sortedByDescending { it.tagName.length }
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
                painter = painterResource(id = R.drawable.ic_tag2),
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
        UserFontText(
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
            .heightIn(max = 200.dp) // 高さ制限（約4-5個分）
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
                        Text(
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