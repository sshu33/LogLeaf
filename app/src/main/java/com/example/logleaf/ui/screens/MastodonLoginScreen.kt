package com.example.logleaf.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.Account
import com.example.logleaf.R
import com.example.logleaf.SessionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MastodonLoginScreen(
    navController: NavController,
    sessionManager: SessionManager
) {
    var instanceUrl by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    val context = LocalContext.current

    // ★★★ ボトムシートの状態管理
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    // ★★★ ヘルプ用のボトムシート
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("アクセストークンとは？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    "アクセストークンは、お使いのMastodonサーバーの設定画面から発行できます。\n" +
                            "『開発』→『新しいアプリケーションを作成』と進み、アプリケーションに『read』権限を与えて保存すると、アクセストークンが表示されます。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { showBottomSheet = false }) {
                    Text("閉じる")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mastodonアカウントを追加") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ★★★ Bluesky画面と同じレイアウト比率を適用
            Spacer(modifier = Modifier.weight(0.6f))

            // ★★★ Mastodonアイコンを追加
            Icon(
                painter = painterResource(id = R.drawable.ic_mastodon),
                contentDescription = "Mastodon Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(32.dp))

            // --- 入力フォーム ---
            OutlinedTextField(
                value = instanceUrl,
                onValueChange = { instanceUrl = it },
                label = { Text("インスタンスURL") },
                placeholder = { Text("mstdn.jp") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large, // ★ 角丸
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("ユーザーID (数字)") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large, // ★ 角丸
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = { Text("アクセストークン") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large, // ★ 角丸
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = { // ★ ヘルプアイコン
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(Icons.Outlined.HelpOutline, "アクセストークンとは？")
                    }
                }
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (instanceUrl.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()) {
                        val newAccount = Account.Mastodon(
                            instanceUrl = instanceUrl.trim(),
                            id = userId.trim(),
                            acct = userId.trim(),
                            username = userId.trim(),
                            accessToken = accessToken.trim()
                        )
                        sessionManager.saveAccount(newAccount)
                        Toast.makeText(context, "Mastodonアカウントを保存しました", Toast.LENGTH_SHORT).show()
                        navController.popBackStack("accounts", inclusive = false)
                    } else {
                        Toast.makeText(context, "すべての項目を入力してください", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.large // ★ 角丸
            ) {
                Text("保存", style = MaterialTheme.typography.bodyLarge)
            }
            // ★★★ Bluesky画面と同じレイアウト比率を適用
            Spacer(modifier = Modifier.weight(1.4f))
        }
    }
}