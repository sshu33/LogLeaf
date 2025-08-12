package com.example.logleaf.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.R
import com.example.logleaf.auth.GoogleFitAuthManager
import com.example.logleaf.data.session.SessionManager
import com.example.logleaf.ui.theme.SnsType
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleFitLoginScreen(
    navController: NavController,
    sessionManager: SessionManager
) {
    val context = LocalContext.current
    val authManager = remember { GoogleFitAuthManager(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSignedIn by remember { mutableStateOf(authManager.isSignedIn()) }

    // Activity Result Launcher for permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager.handleSignInResult(
            GoogleFitAuthManager.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
            result.resultCode
        ) { success, error ->
            isLoading = false
            if (success) {
                isSignedIn = true
                sessionManager.addGoogleFitAccount()
                navController.popBackStack("accounts", inclusive = false)
            } else {
                errorMessage = error
                Log.e("GoogleFitLogin", "連携失敗: $error")
            }
        }
    }

    // 初期状態チェックと既存連携の検出
    LaunchedEffect(Unit) {
        val currentSignedIn = authManager.isSignedIn()
        isSignedIn = currentSignedIn

        // 既に連携済みの場合もアカウントを追加
        if (currentSignedIn && !sessionManager.isGoogleFitConnected()) {
            sessionManager.addGoogleFitAccount()
        }
    }

    // ローディング中の連携状態監視
    LaunchedEffect(isLoading) {
        if (isLoading) {
            // 連携完了まで継続的にチェック
            while (isLoading) {
                delay(1000)
                val newSignedIn = authManager.isSignedIn()

                if (newSignedIn && !isSignedIn) {
                    isLoading = false
                    isSignedIn = true
                    sessionManager.addGoogleFitAccount()
                    navController.popBackStack("accounts", inclusive = false)
                }
            }
        }
    }

    // Google Fitのブランドカラー（LogLeaf統一カラー）
    val googleFitColor = SnsType.GOOGLEFIT.brandColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // カスタムトップバー（他のログイン画面と同じ）
        Surface(
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                }
                Text(
                    text = "Google Fit 連携",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Google Fitアイコン（他のログイン画面と同じサイズ・位置）
            Icon(
                painter = painterResource(id = R.drawable.ic_googlefit),
                contentDescription = "GitHub",
                tint = SnsType.GOOGLEFIT.brandColor,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // タイトルテキスト
            UserFontText(
                text = if (isSignedIn) "連携完了" else "Google Fit と連携",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 説明テキスト
            UserFontText(
                text = if (isSignedIn)
                    "健康データの自動同期が有効"
                else
                    "健康データを自動同期",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // エラーメッセージ
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 連携ボタン（他のログイン画面と同じスタイル）
            if (!isSignedIn) {
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        authManager.signIn(context as Activity) { success, error ->
                            if (success) {
                                // 即座に成功した場合
                                isLoading = false
                                isSignedIn = true
                                sessionManager.addGoogleFitAccount()
                                navController.popBackStack("accounts", inclusive = false)
                            } else {
                                // エラーまたは権限ダイアログ表示
                                if (error != null) {
                                    isLoading = false
                                    errorMessage = error
                                    Log.e("GoogleFitLogin", "認証エラー: $error")
                                }
                                // error == null の場合は権限ダイアログが表示中
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = googleFitColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Google Fit と連携",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            } else {
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = googleFitColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    UserFontText(
                        text = "OK",
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