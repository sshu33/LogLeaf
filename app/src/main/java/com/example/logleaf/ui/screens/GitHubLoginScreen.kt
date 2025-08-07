package com.example.logleaf.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.logleaf.GitHubApi
import com.example.logleaf.R
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.launch

class GitHubLoginViewModel(private val gitHubApi: GitHubApi) : ViewModel() {
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _loginResult = mutableStateOf<String?>(null)
    val loginResult: State<String?> = _loginResult

    fun login(accessToken: String, period: String = "3ヶ月") {
        viewModelScope.launch {
            _isLoading.value = true
            _loginResult.value = null

            try {
                val user = gitHubApi.validateToken(accessToken)

                if (user != null) {
                    val success = gitHubApi.saveAccount(user, accessToken)
                    if (success) {
                        _loginResult.value = "success"
                    } else {
                        _loginResult.value = "アカウントの保存に失敗しました"
                    }
                } else {
                    _loginResult.value = "無効なアクセストークンです"
                }
            } catch (e: Exception) {
                _loginResult.value = "ログインに失敗しました: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        fun provideFactory(gitHubApi: GitHubApi): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GitHubLoginViewModel(gitHubApi) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubLoginScreen(
    navController: NavController,
    viewModel: GitHubLoginViewModel = viewModel()
) {
    var accessToken by remember { mutableStateOf("") }
    var showBottomSheet by remember { mutableStateOf(false) }
    var isTokenVisible by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("3ヶ月") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current

    val isLoading by viewModel.isLoading
    val loginResult by viewModel.loginResult

    LaunchedEffect(loginResult) {
        if (loginResult == "success") {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub ログイン") },
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

            // GitHubアイコン
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = "GitHub",
                tint = SnsType.GITHUB.brandColor,
                modifier = Modifier.size(80.dp)
            )

            // タイトル
            Text(
                text = "GitHub と連携",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // アクセストークン入力
            OutlinedTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = { Text("Personal Access Token") },
                placeholder = { Text("github_pat_11A...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 表示/非表示切り替えボタン
                        IconButton(
                            onClick = { isTokenVisible = !isTokenVisible },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isTokenVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                                ),
                                contentDescription = if (isTokenVisible) "非表示" else "表示",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // ヒントボタン
                        IconButton(
                            onClick = { showBottomSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .padding(end = 10.dp) // 右側に余白を追加
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_help),
                                contentDescription = "作成方法",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (accessToken.isNotEmpty()) {
                            viewModel.login(accessToken)
                        }
                    }
                ),
                enabled = !isLoading,
                isError = loginResult != null && loginResult != "success"
            )

            // エラーメッセージ
            if (loginResult != null && loginResult != "success") {
                Text(
                    text = loginResult!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            // ログインボタン
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.login(accessToken, selectedPeriod) // ← selectedPeriodを追加
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = accessToken.isNotEmpty() && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SnsType.GITHUB.brandColor
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
                        text = "ログイン",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 期間選択を追加（ログインボタンの直後に）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "取得期間",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val periods = listOf("1ヶ月", "3ヶ月", "6ヶ月", "12ヶ月", "全期間")
                    periods.forEach { period ->
                        val isSelected = selectedPeriod == period

                        OutlinedButton(
                            onClick = { selectedPeriod = period },
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.3f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = period,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // ボトムシート
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false }
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Personal Access Token の作成方法",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. GitHub → Settings → Developer settings")
                        Text("2. Personal access tokens → Fine-grained tokens")
                        Text("3. Generate new token をクリック")
                        Text("4. Repository access: All repositories")
                        Text("5. Permissions: Contents + Metadata を追加")
                    }

                    Button(
                        onClick = {
                            uriHandler.openUri("https://github.com/settings/personal-access-tokens/new")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GitHub でトークンを作成")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}