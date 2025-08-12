package com.example.logleaf.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.auth.GoogleFitAuthManager

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
                // 認証成功時は設定画面に戻る
                navController.popBackStack()
            } else {
                errorMessage = error
            }
        }
    }

    LaunchedEffect(Unit) {
        authManager.debugAuthStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Fit 連携") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isSignedIn) {
                // 連携済みの場合
                Text(
                    text = "✅ Google Fit と連携済みです",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "健康データの自動同期が有効になりました",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // 未連携の場合
                Text(
                    text = "Google Fit と連携して健康データを自動取得します",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        authManager.signIn(context as Activity) { success, error ->
                            if (success) {
                                // 権限が既にある場合
                                isLoading = false
                                isSignedIn = true
                                navController.popBackStack()
                            } else {
                                // エラーがある場合、permissionLauncherで処理される
                                if (error != null) {
                                    isLoading = false
                                    errorMessage = error
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Google Fit と連携")
                }
            }

            // エラーメッセージ表示
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}