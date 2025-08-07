package com.example.logleaf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.logleaf.Account
import com.example.logleaf.MainViewModel
import com.example.logleaf.R
import com.example.logleaf.ui.components.CustomTopAppBar
import com.example.logleaf.ui.components.ListCard
import com.example.logleaf.ui.theme.SettingsTheme
import com.example.logleaf.ui.theme.SnsType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    navController: NavController,
    mainViewModel: MainViewModel // ← 追加
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
                                        account is Account.GitHub -> {
                                            // GitHubアカウントタップで期間変更ダイアログを表示
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
                                    // GitHubアカウントの場合は期間を表示
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
                                    Switch(
                                        checked = account.isVisible,
                                        onCheckedChange = { viewModel.toggleAccountVisibility(account.userId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB（Add Account）
        FloatingActionButton(
            onClick = { navController.navigate("sns_select") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 12.dp,
                pressedElevation = 16.dp
            ),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "アカウントを追加",
                tint = Color.White
            )
        }
    }

    // GitHub期間変更ダイアログ
    githubAccountToEdit?.let { account ->
        GitHubPeriodDialog(
            account = account,
            onDismiss = { githubAccountToEdit = null },
            onPeriodChanged = { newPeriod ->
                viewModel.updateGitHubAccountPeriod(account.username, newPeriod)
                mainViewModel.refreshPosts() // ← 自動更新！
            }
        )
    }

    // 削除確認ダイアログ（既存のまま）
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("アカウントを削除") },
            text = { Text("${account.displayName} を削除しますか？関連する投稿もすべて削除されます。") },
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
    onDismiss: () -> Unit,
    onPeriodChanged: (String) -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(account.period) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // タイトル
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = SnsType.GITHUB.brandColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = account.username,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 期間選択部分（既存のまま）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "取得期間",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

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
                            Spacer(modifier = Modifier.weight(1f))
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
                    TextButton(onClick = { onPeriodChanged(selectedPeriod); onDismiss() }) {
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