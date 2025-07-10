package com.example.logleaf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.HelpOutline // ★★★ ヘルプアイコンをインポート
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet // ★★★ ボトムシート関連をインポート
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
import androidx.compose.ui.platform.LocalUriHandler // ★★★ リンクを開くためにインポート
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.BlueskyApi
import com.example.logleaf.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    blueskyApi: BlueskyApi,
    navController: NavController? = null,
    screenTitle: String = "Blueskyにログイン" // デフォルトのタイトルをより具体的に
) {
    var handle by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // ★★★ ボトムシートの状態管理と操作のための設定
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val blueskyHelpUrl = "https://bsky.app/settings/app-passwords"

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 上下に直接余白を指定する
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("アプリパスワードとは？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    "これは通常のログインパスワードではありません。\n" +
                            "LogLeafが安全に投稿を取得するために、Blueskyの公式サイトで発行する特別なパスワードです。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        uriHandler.openUri(blueskyHelpUrl)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Blueskyでアプリパスワードを発行する")
                }
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }) {
                    Text("閉じる")
                }
            }
        }
    }


    Scaffold(
        topBar = {
            if (navController != null) {
                TopAppBar(
                    title = { Text(screenTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ★★★ 上側のスペーサー。残りの空間を押し上げる役割
            Spacer(modifier = Modifier.weight(0.6f))

            // --- ここからが中央に配置したいコンテンツ群 ---
            Icon(
                painter = painterResource(id = R.drawable.ic_bluesky),
                contentDescription = "Bluesky Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(32.dp))

            // 初回起動時のテキスト (このロジックは変更なし)
            if (navController == null) {
                Text("LogLeafへようこそ", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(32.dp))
            }

            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it },
                label = { Text("ハンドル名") },
                placeholder = { Text("example.bsky.social") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("アプリパスワード") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = "アプリパスワードとは？"
                        )
                    }
                }
            )
            Spacer(Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val success = blueskyApi.login(handle.trim(), password.trim())
                            isLoading = false
                            if (success) {
                                onLoginSuccess()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = MaterialTheme.shapes.large,
                    enabled = handle.isNotBlank() && password.isNotBlank()
                ) {
                    Text("ログイン", style = MaterialTheme.typography.bodyLarge)
                }
            }
            // --- ここまでが中央に配置したいコンテンツ群 ---

            // ★★★ 下側のスペーサー。上のスペーサーと協力してフォームを中央に押しやる
            Spacer(modifier = Modifier.weight(1.4f))
        }
    }
}