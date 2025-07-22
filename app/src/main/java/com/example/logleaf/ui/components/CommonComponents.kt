package com.example.logleaf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.logleaf.Post
import com.example.logleaf.R
import com.example.logleaf.ui.screens.Screen
import com.example.logleaf.ui.theme.NoticeGreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.CompositionLocalProvider


object NoRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor() = Color.Unspecified

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(0.0f, 0.0f, 0.0f, 0.0f)
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    // ★★★ タイトルの横に表示する、オプショナルなコンテンツ ★★★
    statusContent: (@Composable () -> Unit)? = null,
    // ★★★ 右端に表示する、オプショナルなコンテンツ ★★★
    trailingContent: (@Composable () -> Unit)? = { // デフォルトは矢印アイコン
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 左端のアイコン
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))

        // 2. 中央のコンテンツ（タイトルと状態表示）
        Row(
            modifier = Modifier.weight(1f), // ★ 残りのスペースを全て使う
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            // statusContentが提供されていれば、タイトルの横に表示
            if (statusContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                statusContent()
            }
        }

        // 3. 右端のコンテンツ（デフォルトは矢印）
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar(
    navController: NavController,
    showSettingsBadge: Boolean
) {
    val items = listOf(Screen.Timeline, Screen.Calendar, Screen.Search, Screen.Settings)

    // ✅ リップル効果を透明にするテーマでラップ
    CompositionLocalProvider(
        LocalRippleTheme provides NoRippleTheme
    ) {
        Surface(shadowElevation = 4.dp, color = Color.Transparent) {
            NavigationBar(
                modifier = Modifier.height(100.dp),
                containerColor = Color.White, // 背景色は白
                tonalElevation = 0.dp // 色合いを適用する高さを0に設定
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { screen ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isSelected = currentRoute?.startsWith(screen.route) == true

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        label = { },
                        interactionSource = interactionSource,
                        // ▼▼▼ [変更点1] デフォルトのインジケーターは、完全に透明にする ▼▼▼
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = {
                            // ▼▼▼ [変更点2] 我々自身の手で、完璧な「飲み薬」を描画する ▼▼▼
                            Box(
                                modifier = Modifier
                                    // これが「飲み薬」のサイズだ！
                                    .height(32.dp)
                                    .width(64.dp)
                                    .background(
                                        // 選択されている時だけ、君が気に入ってくれた色で塗る
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        } else {
                                            Color.Transparent
                                        },
                                        // これが「飲み薬」の形だ！
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // 「飲み薬」の真ん中に、アイコンを配置する
                                if (screen.route == "settings") {
                                    BadgedBox(
                                        badge = {
                                            if (showSettingsBadge) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_sync2),
                                                    contentDescription = "再認証が必要です",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .offset(x = (-1).dp, y = 12.dp),
                                                    tint = NoticeGreen
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.label,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.label,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * アプリ内で共通して使用する、リスト項目用のカード
 * @param onClick カードがタップされた時の動作
 * @param content カードの内部に表示するコンテンツ（Icon, Text, Spacerなど）
 */
@Composable
fun ListCard(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // ★★★ 高さを60.dpに固定し、縦のpaddingを削除 ★★★
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun SearchResultPostItem(post: Post, onClick: () -> Unit = {}) {
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        // SNSの色を示すサイドバー
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(60.dp) // 高さを固定
                .background(post.source.brandColor) // PostのSnsTypeから直接色を取得
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 投稿内容
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 日付と時刻
            Text(
                text = localDateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            // 投稿本文
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

