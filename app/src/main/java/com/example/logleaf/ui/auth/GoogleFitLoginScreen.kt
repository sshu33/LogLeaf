package com.example.logleaf.ui.auth

import android.app.Activity
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
import com.example.logleaf.ui.theme.SnsType
import com.yourpackage.logleaf.ui.components.UserFontText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleFitLoginScreen(navController: NavController) {
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
                navController.popBackStack()
            } else {
                errorMessage = error
            }
        }
    }

    LaunchedEffect(Unit) {
        authManager.debugAuthStatus()
        isSignedIn = authManager.isSignedIn()
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
                text = if (isSignedIn) "連携完了" else "Google Fit",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 説明テキスト
            Text(
                text = if (isSignedIn)
                    "健康データの自動同期が有効です"
                else
                    "健康データを自動で取り込みます",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                                isLoading = false
                                isSignedIn = true
                                navController.popBackStack()
                            } else {
                                if (error != null) {
                                    isLoading = false
                                    errorMessage = error
                                }
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