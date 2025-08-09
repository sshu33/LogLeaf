package com.example.logleaf.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.logleaf.Account
import com.example.logleaf.GitHubRepository
import com.example.logleaf.MainViewModel
import com.example.logleaf.R
import com.example.logleaf.ui.components.CustomTopAppBar
import com.example.logleaf.ui.components.ListCard
import com.example.logleaf.ui.theme.SettingsTheme
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    navController: NavController,
    mainViewModel: MainViewModel
) {
    val accounts by viewModel.accounts.collectAsState()
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var githubAccountToEdit by remember { mutableStateOf<Account.GitHub?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {
            CustomTopAppBar(
                title = "アカウント管理",
                onNavigateBack = { navController.popBackStack() }
            )

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("アカウントが登録されていません")
                }
            } else {
                SettingsTheme {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(accounts) { account ->
                            ListCard(
                                onClick = {
                                    when {
                                        account.needsReauthentication && account is Account.Mastodon -> {
                                            val encodedUrl = URLEncoder.encode(
                                                account.instanceUrl,
                                                StandardCharsets.UTF_8.toString()
                                            )
                                            navController.navigate("mastodon_instance?instanceUrl=$encodedUrl")
                                        }
                                        account.needsReauthentication && account is Account.Bluesky -> {
                                            navController.navigate("login")
                                        }
                                        account.needsReauthentication && account is Account.GitHub -> {
                                            navController.navigate("github_login")
                                        }
                                        account is Account.GitHub -> {
                                            githubAccountToEdit = account
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = when (account.snsType) {
                                            SnsType.BLUESKY -> R.drawable.ic_bluesky
                                            SnsType.MASTODON -> R.drawable.ic_mastodon
                                            SnsType.LOGLEAF -> R.drawable.ic_logleaf
                                            SnsType.GITHUB -> R.drawable.ic_github
                                        }
                                    ),
                                    contentDescription = account.snsType.name,
                                    tint = account.snsType.brandColor,
                                    modifier = Modifier.size(26.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (account is Account.GitHub) {
                                        Text(
                                            text = "取得期間: ${account.period}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                if (account.needsReauthentication) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "再認証",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_sync),
                                            contentDescription = "再認証が必要です",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = account.isVisible,
                                            onCheckedChange = { viewModel.toggleAccountVisibility(account.userId) }
                                        )
                                        IconButton(onClick = { accountToDelete = account }) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_delete),
                                                contentDescription = "削除",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // カスタムFAB
        FloatingActionButton(
            onClick = { navController.navigate("sns_select") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 12.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "アカウントの追加",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    // GitHub設定変更ダイアログ
    githubAccountToEdit?.let { account ->
        GitHubPeriodDialog(
            account = account,
            viewModel = viewModel,
            onDismiss = { githubAccountToEdit = null },
            onSettingsChanged = { newPeriod, fetchMode, selectedRepos ->
                viewModel.updateGitHubAccountPeriod(account.username, newPeriod)
                viewModel.updateGitHubAccountRepositories(account.username, fetchMode, selectedRepos)
                mainViewModel.refreshPosts()
            }
        )
    }

    // 削除確認ダイアログ
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("アカウントの完全削除") },
            text = { Text("${account.displayName} を削除しますか？\n\n注意：このアカウントの投稿もすべて削除され、元に戻すことはできません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccountAndPosts(account)
                        accountToDelete = null
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
fun GitHubPeriodDialog(
    account: Account.GitHub,
    viewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onSettingsChanged: (period: String, fetchMode: Account.RepositoryFetchMode, selectedRepos: List<String>) -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(account.period) }
    var selectedFetchMode by remember { mutableStateOf(account.repositoryFetchMode) }
    var selectedRepositories by remember { mutableStateOf(account.selectedRepositories.toSet()) }
    var isLoadingRepos by remember { mutableStateOf(false) }
    var repoLoadError by remember { mutableStateOf<String?>(null) }
    var availableRepositories by remember { mutableStateOf<List<GitHubRepository>?>(null) }

    val scope = rememberCoroutineScope()
    val isCustomSelected = selectedFetchMode == Account.RepositoryFetchMode.Selected

    val loadRepositories = {
        scope.launch {
            isLoadingRepos = true
            repoLoadError = null
            try {
                val repos = viewModel.getGitHubRepositories(account.accessToken)
                availableRepositories = repos
                if (repos.isEmpty()) {
                    repoLoadError = "リポジトリが見つかりません"
                }
            } catch (e: Exception) {
                repoLoadError = "取得に失敗しました: ${e.message}"
                Log.e("GitHubDialog", "リポジトリ取得失敗", e)
            } finally {
                isLoadingRepos = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .wrapContentHeight()
                .widthIn(min = 300.dp, max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // タイトル
                Text(
                    text = "GitHub設定",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 取得期間段落
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "取得期間",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(Modifier.width(4.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1ヶ月", "3ヶ月", "6ヶ月").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(period = period, isSelected = isSelected, onClick = { selectedPeriod = period })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("12ヶ月", "24ヶ月", "全期間").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(period = period, isSelected = isSelected, onClick = { selectedPeriod = period })
                            }
                        }
                    }
                }

                // 取得対象段落
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "取得対象",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { selectedFetchMode = Account.RepositoryFetchMode.All }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (selectedFetchMode == Account.RepositoryFetchMode.All)
                                        R.drawable.ic_radio_button_on else R.drawable.ic_radio_button_off
                                ),
                                contentDescription = if (selectedFetchMode == Account.RepositoryFetchMode.All) "選択済み" else "未選択",
                                tint = if (selectedFetchMode == Account.RepositoryFetchMode.All)
                                    SnsType.GITHUB.brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "全リポジトリ",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    selectedFetchMode = Account.RepositoryFetchMode.Selected
                                    if (availableRepositories?.isEmpty() != false && !isLoadingRepos) {
                                        loadRepositories()
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (selectedFetchMode == Account.RepositoryFetchMode.Selected)
                                        R.drawable.ic_radio_button_on else R.drawable.ic_radio_button_off
                                ),
                                contentDescription = if (selectedFetchMode == Account.RepositoryFetchMode.Selected) "選択済み" else "未選択",
                                tint = if (selectedFetchMode == Account.RepositoryFetchMode.Selected)
                                    SnsType.GITHUB.brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "カスタム選択",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    AnimatedVisibility(
                        visible = isCustomSelected && (isLoadingRepos || availableRepositories != null || repoLoadError != null),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                when {
                                    isLoadingRepos -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = SnsType.GITHUB.brandColor,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                    repoLoadError != null -> {
                                        Text(
                                            text = "リポジトリの取得に失敗しました",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    availableRepositories?.isEmpty() == true -> {
                                        Text(
                                            text = "リポジトリが見つかりません",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    else -> {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            items(availableRepositories ?: emptyList()) { repo ->
                                                val isSelected = selectedRepositories.contains(repo.fullName)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedRepositories = if (isSelected) {
                                                                selectedRepositories - repo.fullName
                                                            } else {
                                                                selectedRepositories + repo.fullName
                                                            }
                                                        }
                                                        .padding(vertical = 2.dp, horizontal = 4.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(
                                                            id = if (isSelected) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off
                                                        ),
                                                        contentDescription = if (isSelected) "選択済み" else "未選択",
                                                        tint = if (isSelected) SnsType.GITHUB.brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Column(
                                                        modifier = Modifier.padding(start = 8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = repo.name,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    TextButton(
                        onClick = {
                            onSettingsChanged(selectedPeriod, selectedFetchMode, selectedRepositories.toList())
                            onDismiss()
                        }
                    ) {
                        Text("変更", color = SnsType.GITHUB.brandColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(
    period: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(32.dp)
            .width(72.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.3f)
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