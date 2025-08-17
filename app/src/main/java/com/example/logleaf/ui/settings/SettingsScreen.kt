package com.example.logleaf.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.MainViewModel
import com.example.logleaf.R
import com.example.logleaf.ui.components.CustomTopAppBar
import com.example.logleaf.ui.theme.SnsType
import com.yourpackage.logleaf.ui.components.UserFontText


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    onLogout: () -> Unit,
    showAccountBadge: Boolean,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()  //Debug

    // 画面全体の親をColumnに変更し、Scaffoldを完全に削除
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 背景色を設定
            .statusBarsPadding() // ステータスバー（画面上部の時間や電波表示）の高さを考慮
    ) {
        // --- 1. 自作のタイトルバーをここに配置 ---
        CustomTopAppBar(
            title = "設定",
            onNavigateBack = { navController.popBackStack() }
        )

        // --- 2. スクロール可能なコンテンツエリア ---
        Column(
            modifier = Modifier
                .weight(1f) // タイトルバー以外の残りの領域をすべて使う
                .verticalScroll(rememberScrollState())
        ) {
            // スクロールするコンテンツ全体に上下の余白を追加
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                UpgradeBanner()
                Spacer(modifier = Modifier.height(24.dp))

                // 各セクションの左右にだけpaddingを適用
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    StyledSettingsSection(title = "Account") {
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_account),
                            title = "アカウント管理",
                            onClick = { navController.navigate("accounts") }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    StyledSettingsSection(title = "Custom") {
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_setting),
                            title = "基本の設定",
                            onClick = { navController.navigate("basic_settings") }
                        )
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_font),
                            title = "文字の設定",
                            onClick = { navController.navigate("font_settings") }
                        )
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_color),
                            title = "外観の設定",
                            onClick = { Toast.makeText(context, "「外観の設定」は開発中です", Toast.LENGTH_SHORT).show() }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    StyledSettingsSection(title = "Premium") {
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_print),
                            title = "日記の印刷",
                            onClick = { Toast.makeText(context, "「日記の印刷」は開発中です", Toast.LENGTH_SHORT).show() }
                        )
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_widget),
                            title = "ウィジェット",
                            onClick = { Toast.makeText(context, "「ウィジェット」は開発中です", Toast.LENGTH_SHORT).show() }
                        )
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_analytics),
                            title = "統計機能",
                            onClick = { Toast.makeText(context, "「統計機能」は開発中です", Toast.LENGTH_SHORT).show() }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    StyledSettingsSection(title = "Backup & Privacy") {
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_backup),
                            title = "バックアップ",
                            onClick = { navController.navigate("backup_settings") }
                        )
                        StyledSettingsItem(
                            icon = painterResource(id = R.drawable.ic_password),
                            title = "パスワード",
                            onClick = { Toast.makeText(context, "「パスワード」は開発中です", Toast.LENGTH_SHORT).show() }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    StyledSettingsItem(
                        icon = painterResource(id = R.drawable.ic_logout),
                        title = "ログアウト",
                        onClick = { showLogoutDialog = true }
                    )

                    // Fitbitダミーデータ作成

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mainViewModel.createDummyFitbitPosts() }
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fitbit),
                            contentDescription = null,
                            tint = SnsType.FITBIT.brandColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fitbitテストデータ作成",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "UI確認用のダミーFitbitポストを作成",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    // ログアウトダイアログは変更なし
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("ログアウトの確認") },
            text = { Text("すべてのSNSアカウントからログアウトし、アプリを終了します。よろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("はい") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("いいえ") }
            }
        )
    }
}

// StyledSettingsSection と StyledSettingsItem のコードは、以前の修正版（IntrinsicSizeを使った版）をそのまま使ってください
@Composable
fun StyledSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    UserFontText(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun StyledSettingsItem(
    icon: Painter,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
    }
}

// UpgradeBannerは変更なし
@Composable
fun UpgradeBanner() {

}