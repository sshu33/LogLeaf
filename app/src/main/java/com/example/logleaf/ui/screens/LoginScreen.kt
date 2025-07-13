package com.example.logleaf.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.HelpOutline
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
import com.example.logleaf.BlueskyApi
import com.example.logleaf.R
import com.example.logleaf.BlueskyLoginEvent
import com.example.logleaf.BlueskyLoginViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ViewModelを生成するためのFactoryクラスは変更なし
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

    // (LaunchedEffectは変更なし)
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is BlueskyLoginEvent.LoginSuccess -> {
                    navController.popBackStack("accounts", inclusive = false)
                }
                is BlueskyLoginEvent.LoginFailed -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is BlueskyLoginEvent.HideKeyboard -> {
                    focusManager.clearFocus()
                }
            }
        }
    }

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
                title = { Text(screenTitle) },
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

            // (IconやハンドルのTextFieldは変更なし)
            Spacer(modifier = Modifier.weight(0.6f))
            Icon(
                painter = painterResource(id = R.drawable.ic_bluesky),
                contentDescription = "Bluesky Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = uiState.handle,
                onValueChange = { viewModel.onHandleChange(it) },
                label = { Text("ハンドル名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                })
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("アプリパスワード") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                // ★★★ ここを修正：2つのアイコンをRowで囲む ★★★
                trailingIcon = {
                    Row {
                        // ヘルプアイコン
                        IconButton(onClick = { showBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "アプリパスワードとは？"
                            )
                        }
                        // パスワード表示切替アイコン
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "パスワードを非表示にする" else "パスワードを表示する"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, description)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.onLoginSubmitted()
                })
            )
            Spacer(Modifier.height(32.dp))

            // (Button以下の部分は変更なし)
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        viewModel.onLoginSubmitted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = uiState.handle.isNotBlank() && uiState.password.isNotBlank()
                ) {
                    Text("ログイン", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(modifier = Modifier.weight(1.4f))
        }
    }
}