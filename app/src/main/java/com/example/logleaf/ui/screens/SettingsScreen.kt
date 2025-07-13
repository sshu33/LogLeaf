package com.example.logleaf.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.R
import com.example.logleaf.ui.components.SettingsMenuItem
import com.example.logleaf.ui.components.SettingsSectionHeader
import com.example.logleaf.ui.theme.NoticeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onLogout: () -> Unit,
    showAccountBadge: Boolean,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("設定") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SettingsSectionHeader(title = "アカウント")

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
                            tint = NoticeGreen
                        )
                    }
                }
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp))

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