package com.example.logleaf.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.MastodonInstanceEvent
import com.example.logleaf.MastodonInstanceViewModel
import com.example.logleaf.R
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MastodonInstanceScreen(
    navController: NavController,
    viewModel: MastodonInstanceViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedPeriod by remember { mutableStateOf("3ヶ月") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // イベント処理（既存のまま）
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
                is MastodonInstanceEvent.HideKeyboard -> {
                    focusManager.clearFocus()
                }
            }
        }
    }

    // ボトムシート（既存のまま）
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                title = {
                    val titleText = if (viewModel.isReAuthFlow) "再認証中..." else "Mastodon ログイン"
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Mastodonアイコン
            Icon(
                painter = painterResource(id = R.drawable.ic_mastodon),
                contentDescription = "Mastodon",
                tint = SnsType.MASTODON.brandColor,
                modifier = Modifier.size(80.dp)
            )

            // タイトル
            Text(
                text = "Mastodon と連携",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (viewModel.isReAuthFlow) {
                Text("再認証を実行中です...")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            } else {
                // 期間選択（GitHubスタイル）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "取得期間",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1行目：1ヶ月、3ヶ月、6ヶ月
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("1ヶ月", "3ヶ月", "6ヶ月").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.MASTODON.brandColor
                                )
                            }
                        }

                        // 2行目：12ヶ月、24ヶ月、全期間
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("12ヶ月", "24ヶ月", "全期間").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.MASTODON.brandColor
                                )
                            }
                        }
                    }
                }

                // インスタンスURL入力
                OutlinedTextField(
                    value = uiState.instanceUrl,
                    onValueChange = { viewModel.onInstanceUrlChange(it) },
                    label = { Text("インスタンスURL") },
                    placeholder = { Text("mstdn.jp") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showBottomSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .padding(end = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "インスタンスURLとは？",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        if (uiState.instanceUrl.isNotBlank()) {
                            viewModel.onInstanceSubmitted()
                        }
                    }),
                    enabled = !uiState.isLoading,
                    isError = uiState.error != null,
                    singleLine = true
                )

                // エラーメッセージ（GitHubスタイル）
                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // 次へボタン
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.onInstanceSubmitted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = uiState.instanceUrl.isNotBlank() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SnsType.MASTODON.brandColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "次へ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PeriodChip(
    period: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    brandColor: Color
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(32.dp)
            .width(72.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) brandColor else Color.Transparent,
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = period,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}