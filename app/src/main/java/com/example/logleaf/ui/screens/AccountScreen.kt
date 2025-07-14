package com.example.logleaf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.Account
import com.example.logleaf.R
import com.example.logleaf.ui.components.ListCard
import com.example.logleaf.ui.theme.SnsType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    navController: NavController
) {
    // ★ 改善されたロジックは維持: StateFlowを直接collectする効率的な方法
    val accounts by viewModel.accounts.collectAsState()
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アカウント管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("sns_select") }
            ) {
                Icon(Icons.Default.Add, "アカウントの追加")
            }
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("アカウントが登録されていません")
            }
        } else {
            // ★ SnsSelectScreenとレイアウトを完全に一致させる
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { account ->
                    // ★ あなたの作ったListCardを、敬意を払って使用します
                    ListCard(
                        onClick = {
                            if (account.needsReauthentication && account is Account.Mastodon) {
                                val encodedUrl = URLEncoder.encode(account.instanceUrl, StandardCharsets.UTF_8.toString())
                                navController.navigate("mastodon_instance?instanceUrl=$encodedUrl")
                            }
                        }
                    ) {
                        // --- ここから下がListCardの content ---

                        // ★ 過去の設計を完全再現：SNSアイコン
                        Icon(
                            painter = painterResource(
                                id = when (account.snsType) {
                                    SnsType.BLUESKY -> R.drawable.ic_bluesky
                                    SnsType.MASTODON -> R.drawable.ic_mastodon
                                }
                            ),
                            contentDescription = account.snsType.name,
                            tint = account.snsType.brandColor,
                            modifier = Modifier.size(26.dp)
                        )

                        // ★ 過去の設計を完全再現：アカウント名
                        Text(
                            text = account.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // ★ ここが最重要ポイントです ★
                        if (account.needsReauthentication) {
                            // ★ 過去の設計を完全再現：再認証が必要な場合の表示
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
                                    tint = MaterialTheme.colorScheme.primary, // NoticeGreenよりこちらの方がテーマに合致します
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            // ★ 新しい機能（トグルスイッチ）を、あなたの希望通りに自然に統合 ★
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

    // ★ 改善されたロジックは維持: 警告文がより親切な削除確認ダイアログ
    if (accountToDelete != null) {
        val currentAccount = accountToDelete!!
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("アカウントの完全削除") },
            text = { Text("${currentAccount.displayName} を削除しますか？\n\n注意：このアカウントの投稿もすべて削除され、元に戻すことはできません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccountAndPosts(currentAccount)
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