package com.example.logleaf.ui.auth

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.R
import com.example.logleaf.api.fitbit.FitbitApi
import com.example.logleaf.api.fitbit.FitbitAuthHolder
import com.example.logleaf.data.credentials.CredentialsManager
import com.example.logleaf.data.session.SessionManager
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitbitLoginScreen(
    navController: NavController,
    sessionManager: SessionManager,
    fitbitApi: FitbitApi
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf("3ヶ月") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    // ボトムシート状態
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val fitbitHelpUrl = "https://dev.fitbit.com/apps"

    // Fitbitのブランドカラー
    val fitbitColor = SnsType.FITBIT.brandColor

    // 期間オプション
    val periodOptions = listOf("1ヶ月", "3ヶ月", "6ヶ月", "12ヶ月", "24ヶ月", "全期間")

    // パスワード表示切り替え
    var clientSecretVisible by remember { mutableStateOf(false) }

    val credentialsManager = remember { CredentialsManager(context) }
    var rememberCredentials by remember { mutableStateOf(false) }

// 画面初期化時に保存された認証情報を読み込み
    LaunchedEffect(Unit) {
        credentialsManager.getFitbitCredentials()?.let { (savedClientId, savedClientSecret) ->
            clientId = savedClientId
            clientSecret = savedClientSecret
            rememberCredentials = true
        }
    }

    LaunchedEffect(Unit) {
        FitbitAuthHolder.authCodeFlow.collectLatest { code ->
            try {
                val cleanClientId = FitbitAuthHolder.clientId ?: return@collectLatest
                val cleanClientSecret = FitbitAuthHolder.clientSecret ?: return@collectLatest

                val tokenResponse = fitbitApi.exchangeCodeForToken(
                    clientId = cleanClientId,
                    clientSecret = cleanClientSecret,
                    code = code,
                    redirectUri = "logleaf://fitbit/callback"
                )

                if (tokenResponse != null) {
                    val success = fitbitApi.saveAccount(tokenResponse, selectedPeriod)

                    if (success) {

                        isLoading = false
                        showSuccess = true
                        delay(2000)
                        navController.popBackStack("accounts", inclusive = false)
                    } else {
                        isLoading = false
                        errorMessage = "アカウント保存に失敗しました"
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "認証処理エラー: ${e.message}"
            }
        }
    }

    // ヘルプボトムシート
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = Color.White  // 背景を白に
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Client IDとSecretとは？",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "LogLeafがFitbitデータにアクセスするために必要な認証情報です。\n" +
                            "dev.fitbit.comでアプリを登録して取得します。\nRedirect URLの欄にはlogleaf://fitbit/callbackと入力してください。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        uriHandler.openUri(fitbitHelpUrl)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SnsType.FITBIT.brandColor  // ブランドカラーに
                    )
                ) {
                    Text("Client IDとSecretを取得する")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fitbit 連携") },
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
            Spacer(modifier = Modifier.height(12.dp))

            // Fitbitアイコン
            Icon(
                painter = painterResource(id = R.drawable.ic_fitbit),
                contentDescription = "Fitbit",
                tint = fitbitColor,
                modifier = Modifier.size(80.dp)
            )

            // タイトル
            Text(
                text = if (showSuccess) "連携完了" else "Fitbit と連携",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!showSuccess) {
                // 説明文（期間選択の代わり）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "初回連携では過去2ヶ月分のデータを取得します。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "さらに過去のデータは連携後に追加取得できます。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }

                // Client ID入力
                OutlinedTextField(
                    value = clientId,
                    onValueChange = {
                        clientId = it.trim()
                        errorMessage = null
                    },
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    enabled = !isLoading,
                )

                Column {

                    // Client Secret入力
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = {
                            clientSecret = it
                            errorMessage = null
                        },
                        label = { Text("Client Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (clientId.trim().isNotBlank() && clientSecret.trim()
                                        .isNotBlank()
                                ) {
                                    // ログイン処理実行（上記のonClickと同じ処理）
                                }
                            }
                        ),
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = if (clientSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 表示/非表示切り替えボタン
                                IconButton(
                                    onClick = { clientSecretVisible = !clientSecretVisible },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (clientSecretVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                                        ),
                                        contentDescription = if (clientSecretVisible) "非表示" else "表示",
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
                                        contentDescription = "Client Secretとは？",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    )

                    // エラーメッセージ
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // 認証情報を記憶するチェックボックス
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { rememberCredentials = !rememberCredentials },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (rememberCredentials)
                                        R.drawable.ic_toggle_on
                                    else
                                        R.drawable.ic_toggle_off
                                ),
                                contentDescription = if (rememberCredentials) "チェック済み" else "未チェック",
                                tint = if (rememberCredentials)
                                    SnsType.FITBIT.brandColor // Fitbitブランドカラー
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "認証情報を記憶",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 連携ボタン
                Button(
                    onClick = {
                        val cleanClientId = clientId.trim()
                        val cleanClientSecret = clientSecret.trim()

                        if (cleanClientId.isBlank() || cleanClientSecret.isBlank()) {
                            errorMessage = "Client IDとClient Secretを入力してください"
                            return@Button
                        }

                        // 認証情報の保存・削除
                        if (rememberCredentials) {
                            credentialsManager.saveFitbitCredentials(cleanClientId, cleanClientSecret)
                        } else {
                            credentialsManager.clearFitbitCredentials()
                        }

                        isLoading = true
                        errorMessage = null

                        // 認証情報を保存
                        FitbitAuthHolder.clientId = cleanClientId
                        FitbitAuthHolder.clientSecret = cleanClientSecret

                        // OAuth認証URLを生成してブラウザで開く
                        coroutineScope.launch {
                            try {
                                val redirectUri = "logleaf://fitbit/callback"
                                val authUrl = fitbitApi.getAuthorizationUrl(cleanClientId, redirectUri)

                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                isLoading = false
                                // 既存のエラー処理...
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading && clientId.isNotBlank() && clientSecret.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = fitbitColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_fitbit),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Fitbitと連携する",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // 連携完了時の説明
                Text(
                    text = "Fitbitデータの自動同期が有効です",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
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