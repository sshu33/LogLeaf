package com.example.logleaf.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.logleaf.ui.theme.NoticeGreen
import com.example.logleaf.ui.theme.SnsType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    navController: NavController
) {
    val accounts by viewModel.accounts.collectAsState()
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    // ★★★ この画面が表示されるたびに、最新のアカウントリストを読み込むように変更 ★★★
    LaunchedEffect(Unit) {
        viewModel.loadAccounts()
    }

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { account ->
                    ListCard(
                        onClick = {
                            if (account.needsReauthentication && account is Account.Mastodon) {
                                // ★★★ 再認証フローを開始するナビゲーション ★★★
                                val encodedUrl = URLEncoder.encode(account.instanceUrl, StandardCharsets.UTF_8.toString())
                                navController.navigate("mastodon_instance?instanceUrl=$encodedUrl")
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = when (account.snsType) {
                                    SnsType.BLUESKY -> R.drawable.ic_bluesky
                                    SnsType.MASTODON -> R.drawable.ic_mastodon
                                }
                            ),
                            contentDescription = account.snsType.name,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = account.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (account.needsReauthentication) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    if (account is Account.Mastodon) {
                                        val encodedUrl = URLEncoder.encode(account.instanceUrl, StandardCharsets.UTF_8.toString())
                                        navController.navigate("mastodon_instance?instanceUrl=$encodedUrl")
                                    }
                                }
                            ) {
                                Text(
                                    text = "再認証",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_sync), // ★ アイコンは ic_sync に変更済みと仮定
                                    contentDescription = "再認証が必要です",
                                    // ★★★ ここを修正 ★★★
                                    tint = NoticeGreen, // primaryからNoticeGreenに変更
                                    modifier = Modifier.size(24.dp) // サイズも調整済みと仮定
                                )
                            }
                        } else {
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

    // 削除確認ダイアログ (変更なし)
    if (accountToDelete != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("アカウントの削除") },
            text = { Text("${accountToDelete!!.displayName} を削除しますか？\nこのアカウントのデータは表示されなくなります。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(accountToDelete!!)
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