package com.example.logleaf.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.MastodonInstanceEvent
import com.example.logleaf.MastodonInstanceViewModel
import com.example.logleaf.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MastodonInstanceScreen(
    navController: NavController,
    // ★★★ 引数をViewModelFactoryからViewModel自体に変更 ★★★
    viewModel: MastodonInstanceViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is MastodonInstanceEvent.NavigateToBrowser -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.authUrl))
                    context.startActivity(intent)
                }
                is MastodonInstanceEvent.AuthenticationSuccess -> {
                    navController.popBackStack("accounts", inclusive = false)
                }
                // ★ whenを網羅的にするために else を追加。何もしない。
                else -> {}
            }
        }
    }

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
                Text("インスタンスURLとは？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    "あなたがアカウントを持っているMastodonサーバーのアドレスです。(例: mstdn.jp, pawoo.net など)\n" +
                            "ブラウザでいつもアクセスしているMastodonのドメイン名を入力してください。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showBottomSheet = false
                    }
                }) {
                    Text("閉じる")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // ★★★ フローに応じてタイトルを変更 ★★★
                title = {
                    val titleText = if (viewModel.isReAuthFlow) "再認証中..." else "Mastodonサーバーを選択"
                    Text(titleText)
                },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // ★ 中央揃えを追加
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mastodon),
                contentDescription = "Mastodon Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(32.dp))

            // ★★★ フローに応じてUIを切り替え ★★★
            if (viewModel.isReAuthFlow) {
                // --- 再認証フローの場合 ---
                Text("再認証を実行中です...")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            } else {
                // --- 新規追加フローの場合（★ ここを正しく記述）---
                OutlinedTextField(
                    value = uiState.instanceUrl,
                    onValueChange = { viewModel.onInstanceUrlChange(it) }, // ← これです！
                    label = { Text("インスタンスURL") },
                    placeholder = { Text("mstdn.jp") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    isError = uiState.error != null,
                    trailingIcon = {
                        IconButton(onClick = { showBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "インスタンスURLとは？"
                            )
                        }
                    }
                )

                val errorText = uiState.error
                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { viewModel.onAppRegisterClicked() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = MaterialTheme.shapes.large,
                        enabled = uiState.instanceUrl.isNotBlank()
                    ) {
                        Text("次へ", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}