package com.example.logleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.logleaf.Account
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
    navController: NavController
) {
    val accounts by viewModel.accounts.collectAsState()
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    // ★★★ Scaffoldを撤廃し、Boxを画面全体の親にしました ★★★
    Box(modifier = Modifier.fillMaxSize()) {

        // --- メインコンテンツ（上部のバーとリスト） ---
        Column(modifier = Modifier.fillMaxSize()) {
            // ★★★ あなたが用意してくれたCustomTopAppBarを、敬意を払って使用します ★★★
            CustomTopAppBar(
                title = "アカウント管理",
                onNavigateBack = { navController.popBackStack() }
            )

            // --- リスト表示部分 ---
            if (accounts.isEmpty()) {
                Box(
                    // weight(1f)で残りのスペースをすべて埋める
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("アカウントが登録されていません")
                }
            } else {
                SettingsTheme {
                    LazyColumn(
                        // weight(1f)でCustomTopAppBar以外の全スペースを使用する
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(accounts) { account ->
                            ListCard(
                                onClick = {
                                    if (account.needsReauthentication && account is Account.Mastodon) {
                                        val encodedUrl = URLEncoder.encode(
                                            account.instanceUrl,
                                            StandardCharsets.UTF_8.toString()
                                        )
                                        navController.navigate("mastodon_instance?instanceUrl=$encodedUrl")
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = when (account.snsType) {
                                            SnsType.BLUESKY -> R.drawable.ic_bluesky
                                            SnsType.MASTODON -> R.drawable.ic_mastodon
                                            SnsType.LOGLEAF -> R.drawable.ic_logleaf
                                        }
                                    ),
                                    contentDescription = account.snsType.name,
                                    tint = account.snsType.brandColor,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = account.displayName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
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

        FloatingActionButton(
            onClick = { navController.navigate("sns_select") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // ★★★ このpaddingの値で、最終的な微調整を行います ★★★
                .padding(
                    // 右端からの距離
                    end = 20.dp,
                    // 下端からの距離
                    bottom = 12.dp
                ),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            // ★★★ 内側のBoxからはpaddingを削除します ★★★
            // 位置調整は外側のpaddingに一本化しました
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
}