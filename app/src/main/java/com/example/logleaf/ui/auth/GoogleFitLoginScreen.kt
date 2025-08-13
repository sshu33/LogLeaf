package com.example.logleaf.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
    var selectedPeriod by remember { mutableStateOf("3ヶ月") } // ★ 期間選択追加

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
                // ★ 期間付きでアカウント追加
                sessionManager.addGoogleFitAccount(selectedPeriod)
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
            sessionManager.addGoogleFitAccount(selectedPeriod)
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
                    sessionManager.addGoogleFitAccount(selectedPeriod)
                    navController.popBackStack("accounts", inclusive = false)
                }
            }
        }
    }

    // Google Fitのブランドカラー
    val googleFitColor = SnsType.GOOGLEFIT.brandColor

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // カスタムトップバー
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Google Fitアイコン
                Icon(
                    painter = painterResource(id = R.drawable.ic_googlefit),
                    contentDescription = "Google Fit",
                    tint = SnsType.GOOGLEFIT.brandColor,
                    modifier = Modifier.size(80.dp)
                )

                // タイトル
                Text(
                    text = if (isSignedIn) "連携完了" else "Google Fit と連携",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isSignedIn) {
                    // 期間選択（他のSNSと同様）
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
                                        brandColor = googleFitColor
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
                                        brandColor = googleFitColor
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 連携完了時の説明
                    Text(
                        text = "健康データの自動同期が有効です",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                // エラーメッセージ
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 連携ボタン
                if (!isSignedIn) {
                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            authManager.signIn(context as Activity) { success, error ->
                                if (success) {
                                    isLoading = false
                                    isSignedIn = true
                                    sessionManager.addGoogleFitAccount(selectedPeriod)
                                    navController.popBackStack("accounts", inclusive = false)
                                } else {
                                    if (error != null) {
                                        isLoading = false
                                        errorMessage = error
                                        Log.e("GoogleFitLogin", "認証エラー: $error")
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
                        onClick = { navController.popBackStack("accounts", inclusive = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = googleFitColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
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