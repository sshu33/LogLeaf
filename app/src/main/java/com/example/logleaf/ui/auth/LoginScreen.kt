package com.example.logleaf.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import com.example.logleaf.api.bluesky.BlueskyApi
import com.example.logleaf.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.logleaf.data.credentials.CredentialsManager
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class BlueskyViewModelFactory(private val blueskyApi: BlueskyApi) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BlueskyLoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BlueskyLoginViewModel(blueskyApi) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: BlueskyLoginViewModel,
    screenTitle: String = "Blueskyにログイン"
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val credentialsManager = remember { CredentialsManager(context) }
    var rememberPassword by remember { mutableStateOf(false) }

    // 画面初期化時に保存された認証情報を読み込み
    LaunchedEffect(Unit) {
        credentialsManager.getBlueskyCredentials()?.let { (savedHandle, savedPassword) ->
            viewModel.onHandleChange(savedHandle)
            viewModel.onPasswordChange(savedPassword)
            rememberPassword = true
        }
    }

    // イベント処理（既存のまま）
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is BlueskyLoginEvent.LoginSuccess -> {
                    navController.popBackStack("accounts", inclusive = false)
                }
                is BlueskyLoginEvent.HideKeyboard -> {
                    focusManager.clearFocus()
                }
            }
        }
    }

    // ボトムシート（既存のまま）
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
            TopAppBar(
                title = { Text("Bluesky ログイン") },
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Blueskyアイコン
            Icon(
                painter = painterResource(id = R.drawable.ic_bluesky),
                contentDescription = "Bluesky",
                tint = SnsType.BLUESKY.brandColor,
                modifier = Modifier.size(80.dp)
            )

            // タイトル
            Text(
                text = "Bluesky と連携",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

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
                                onClick = { viewModel.onPeriodChanged(period) },
                                brandColor = SnsType.BLUESKY.brandColor
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
                                onClick = { viewModel.onPeriodChanged(period) },
                                brandColor = SnsType.BLUESKY.brandColor
                            )
                        }
                    }
                }
            }

                // AutofillNode作成
                val handleAutofillNode = remember {
                    AutofillNode(
                        autofillTypes = listOf(AutofillType.Username),
                        onFill = { viewModel.onHandleChange(it) }
                    )
                }
                val autofill = LocalAutofill.current
                LocalAutofillTree.current += handleAutofillNode

                // ハンドル名入力
                OutlinedTextField(
                    value = uiState.handle,
                    onValueChange = { viewModel.onHandleChange(it) },
                    label = { Text("ハンドル名") },
                    placeholder = { Text("example.bsky.social") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            handleAutofillNode.boundingBox = it.boundsInWindow()
                        }
                        .onFocusChanged { focusState ->
                            println("DEBUG: Focus changed: ${focusState.isFocused}")
                            autofill?.run {
                                println("DEBUG: Autofill is available")
                                if (focusState.isFocused) {
                                    println("DEBUG: Requesting autofill")
                                    requestAutofillForNode(handleAutofillNode)
                                } else {
                                    println("DEBUG: Canceling autofill")
                                    cancelAutofillForNode(handleAutofillNode)
                                }
                            } ?: println("DEBUG: Autofill is null")
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }),
                    enabled = !uiState.isLoading
                )

                // パスワード用AutofillNode作成
                val passwordAutofillNode = remember {
                    AutofillNode(
                        autofillTypes = listOf(AutofillType.Password),
                        onFill = { viewModel.onPasswordChange(it) }
                    )
                }
                LocalAutofillTree.current += passwordAutofillNode


            Column {

                // アプリパスワード入力
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.onPasswordChange(it) },
                    label = { Text("アプリパスワード") },
                    placeholder = {
                        Text(
                            text = "xxxx-xxxx-xxxx-xxxx",
                            color = Color.LightGray
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            passwordAutofillNode.boundingBox = it.boundsInWindow()
                        }
                        .onFocusChanged { focusState ->
                            autofill?.run {
                                if (focusState.isFocused) {
                                    requestAutofillForNode(passwordAutofillNode)
                                } else {
                                    cancelAutofillForNode(passwordAutofillNode)
                                }
                            }
                        },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 表示/非表示切り替えボタン
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                                    ),
                                    contentDescription = if (passwordVisible) "非表示" else "表示",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // ヒントボタン
                            IconButton(
                                onClick = { showBottomSheet = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(end = 10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_help),
                                    contentDescription = "アプリパスワードとは？",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        if (uiState.handle.isNotBlank() && uiState.password.isNotBlank()) {
                            viewModel.onLoginSubmitted()
                        }
                    }),
                    enabled = !uiState.isLoading
                )

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // チェックボックス
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 自前のアイコンを使ったチェックボックス
                    IconButton(
                        onClick = { rememberPassword = !rememberPassword },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (rememberPassword)
                                    R.drawable.ic_toggle_on
                                else
                                    R.drawable.ic_toggle_off
                            ),
                            contentDescription = if (rememberPassword) "チェック済み" else "未チェック",
                            tint = if (rememberPassword)
                                SnsType.BLUESKY.brandColor // ブランドカラー
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp)) // 間隔を少し狭く
                    Text(
                        text = "ログイン情報を記憶",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val autofillManager = LocalAutofillManager.current

            // ログインボタン
            Button(
                onClick = {
                    keyboardController?.hide()
                    if (uiState.handle.isNotBlank() && uiState.password.isNotBlank()) {
                        // ログイン情報の保存・削除
                        if (rememberPassword) {
                            credentialsManager.saveBlueskyCredentials(uiState.handle, uiState.password)
                        } else {
                            credentialsManager.clearBlueskyCredentials()
                        }

                        viewModel.onLoginSubmitted()

                        // AutofillManagerのcommit（既存のAutofill用）
                        autofillManager?.commit()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = uiState.handle.isNotBlank() && uiState.password.isNotBlank() && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SnsType.BLUESKY.brandColor
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
                        text = "ログイン",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
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