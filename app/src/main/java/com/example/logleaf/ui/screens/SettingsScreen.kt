package com.example.logleaf.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.R
import com.example.logleaf.ui.components.SettingsMenuItem
import com.example.logleaf.ui.components.SettingsSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onLogout: () -> Unit,
    showAccountBadge: Boolean, // この引数は、新しいデザインではまだ使いませんが、将来のために残しておきます
) {
    // --- 状態管理（ログアウトダイアログ）は変更なし ---
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // ★★★ ScaffoldとTopAppBarを撤去し、LazyColumnで画面全体を構築 ★★★
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // ★ リスト全体の上下に余白を追加
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // --- 1. トップバナー ---
        item {
            UpgradeBanner()
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ★★★ 2. "アカウント" セクションを一番上に移動 ★★★
        item {
            SettingsSectionHeader(title = "Account")
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.ManageAccounts,
                title = "アカウント管理",
                onClick = { navController.navigate("accounts") },
                statusContent = {
                    if (showAccountBadge) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync),
                            contentDescription = "再認証が必要です",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }

        // --- 2. "Custom" セクション ---
        item {
            SettingsSectionHeader(title = "Custom")
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Tune,
                title = "基本の設定",
                onClick = { Toast.makeText(context, "「基本の設定」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.TextFields,
                title = "文字の設定",
                onClick = { navController.navigate("font_settings") }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Palette,
                title = "外観の設定",
                onClick = { Toast.makeText(context, "「外観の設定」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }

        // --- 3. "Premium" セクション ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(title = "Premium")
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Print,
                title = "日記の印刷",
                onClick = { Toast.makeText(context, "「日記の印刷」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Widgets,
                title = "ウィジェット",
                onClick = { Toast.makeText(context, "「ウィジェット」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.BarChart,
                title = "統計機能",
                onClick = { Toast.makeText(context, "「統計機能」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }

        // --- 4. "Backup & Privacy" セクション ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(title = "Backup & Privacy")
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Backup,
                title = "バックアップ",
                onClick = { Toast.makeText(context, "「バックアップ」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Lock,
                title = "パスワード",
                onClick = { Toast.makeText(context, "「パスワード」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }

        // --- 5. "Backup & Privacy" セクション ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(title = "Backup & Privacy")
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Backup,
                title = "バックアップ",
                onClick = { Toast.makeText(context, "「バックアップ」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }
        item {
            SettingsMenuItem(
                icon = Icons.Default.Lock,
                title = "パスワード",
                onClick = { Toast.makeText(context, "「パスワード」は開発中です", Toast.LENGTH_SHORT).show() }
            )
        }

        // ★★★ 6. ログアウトを一番下に配置 ★★★
        item {
            // 他のセクションとの間に、少し大きめのスペースを空けて区別する
            Spacer(modifier = Modifier.height(24.dp))
            SettingsMenuItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "ログアウト",
                onClick = { showLogoutDialog = true }
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("ログアウトの確認") },
            text = { Text("すべてのSNSアカウントからログアウトし、アプリを終了します。よろしいですか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("はい")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("いいえ")
                }
            }
        )
    }
}

// SettingsScreen.kt の一番下に追加してください

/**
 * ★★★ 新しく追加した、トップバナーのための部品 ★★★
 */
@Composable
private fun UpgradeBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            // ★ あなたのデザインに近い、暖色系の背景色
            containerColor = Color(0xFFFBF3E9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "あなたの 日記を",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "アップグレード",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp, // 少し大きくしてアクセントに
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // ★★★ 画像リソースがまだないため、一旦アイコンで代用します ★★★
            Icon(
                imageVector = Icons.Default.Inventory2, // 宝箱に近いアイコン
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}