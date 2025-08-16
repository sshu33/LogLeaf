package com.example.logleaf.ui.settings

import android.util.Log
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.example.logleaf.data.model.Account
import com.example.logleaf.api.github.GitHubRepository
import com.example.logleaf.MainViewModel
import com.example.logleaf.R
import com.example.logleaf.data.session.FitbitHistoryManager
import com.example.logleaf.ui.components.CustomTopAppBar
import com.example.logleaf.ui.components.ListCard
import com.example.logleaf.ui.theme.SettingsTheme
import com.example.logleaf.ui.theme.SnsType
import com.google.firebase.dataconnect.LocalDate
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    navController: NavController,
    mainViewModel: MainViewModel
) {
    val accounts by viewModel.accounts.collectAsState()

    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var blueskyAccountToEdit by remember { mutableStateOf<Account.Bluesky?>(null) }
    var mastodonAccountToEdit by remember { mutableStateOf<Account.Mastodon?>(null) }
    var githubAccountToEdit by remember { mutableStateOf<Account.GitHub?>(null) }
    var googleFitAccountToEdit by remember { mutableStateOf<Account.GoogleFit?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {
            CustomTopAppBar(
                title = "アカウント管理",
                onNavigateBack = { navController.popBackStack() }
            )

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                            // ★ Fitbitアカウントかどうかで分岐
                            when (account) {
                                is Account.Fitbit -> {
                                    // Fitbit専用のUIコンポーネント
                                    FitbitAccountItem(
                                        account = account,
                                        viewModel = viewModel,
                                        mainViewModel = mainViewModel,
                                        onDeleteClick = { accountToDelete = account }
                                    )
                                }
                                else -> {
                                    // 既存のListCard（他のアカウント用）
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
                                                account is Account.Bluesky -> {
                                                    blueskyAccountToEdit = account
                                                }
                                                account is Account.Mastodon -> {
                                                    mastodonAccountToEdit = account
                                                }
                                                account is Account.GoogleFit -> {
                                                    googleFitAccountToEdit = account
                                                }
                                            }
                                        }
                                    ) {
                                        // 既存のアカウント表示UI（アイコン、名前、スイッチ、削除ボタン）
                                        Icon(
                                            painter = painterResource(
                                                id = when (account.snsType) {
                                                    SnsType.BLUESKY -> R.drawable.ic_bluesky
                                                    SnsType.MASTODON -> R.drawable.ic_mastodon
                                                    SnsType.LOGLEAF -> R.drawable.ic_logleaf
                                                    SnsType.GITHUB -> R.drawable.ic_github
                                                    SnsType.GOOGLEFIT -> R.drawable.ic_googlefit
                                                    SnsType.FITBIT -> R.drawable.ic_fitbit
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

                                                // GoogleFitの場合はゴミ箱アイコンも表示
                                                if (account is Account.GoogleFit) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_delete),
                                                        contentDescription = "削除",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clickable { accountToDelete = account }
                                                            .padding(4.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(
                                                    checked = account.isVisible,
                                                    onCheckedChange = { viewModel.toggleAccountVisibility(account.userId) }
                                                )

                                                Spacer(Modifier.width(8.dp))

                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_delete),
                                                    contentDescription = "削除",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clickable { accountToDelete = account }
                                                        .padding(4.dp)
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

    // Bluesky期間変更ダイアログ
    blueskyAccountToEdit?.let { account ->
        BlueskyPeriodDialog(
            account = account,
            onDismiss = { blueskyAccountToEdit = null },
            onPeriodChanged = { newPeriod ->
                viewModel.updateBlueskyAccountPeriod(account.handle, newPeriod)
                mainViewModel.refreshPosts()
            }
        )
    }

    // Mastodon期間変更ダイアログ
    mastodonAccountToEdit?.let { account ->
        MastodonPeriodDialog(
            account = account,
            onDismiss = { mastodonAccountToEdit = null },
            onPeriodChanged = { newPeriod ->
                viewModel.updateMastodonAccountPeriod(account.acct, newPeriod)
                mainViewModel.refreshPosts()
            }
        )
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

    // Google Fit設定変更ダイアログ
    googleFitAccountToEdit?.let { account ->
        GoogleFitPeriodDialog(
            account = account,
            onDismiss = { googleFitAccountToEdit = null },
            onPeriodChanged = { newPeriod ->
                viewModel.updateGoogleFitAccountPeriod(newPeriod)
                mainViewModel.refreshPosts()
            }
        )
    }

    // 削除確認ダイアログ
    accountToDelete?.let { account ->
        DeleteDialog(
            account = account,
            onDismiss = { accountToDelete = null },
            onConfirm = { deletePostsAlso ->
                viewModel.deleteAccountAndPosts(account, deletePostsAlso)
                accountToDelete = null
            }
        )
    }
}

// AccountScreen.kt の削除確認ダイアログを置き換える
@Composable
fun DeleteDialog(
    account: Account,
    onDismiss: () -> Unit,
    onConfirm: (deletePostsAlso: Boolean) -> Unit
) {
    var deletePostsAlso by remember { mutableStateOf(false) }

    val isGoogleFit = account is Account.GoogleFit

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFFFFFF),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp), // 余白を適度に
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp) // スペースを適度に
            ) {
                // タイトル
                UserFontText(
                    text = if (isGoogleFit) "連携解除" else "アカウントの削除",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // アカウント名（Google Fit以外のみ）
                if (!isGoogleFit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = account.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // 説明文
                UserFontText(
                    text = if (isGoogleFit)
                        "新しい健康データの取得が\n停止されます"
                    else
                        "OKを押すと\nすべてのポストが削除されます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                // Google Fit用オプション
                if (isGoogleFit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deletePostsAlso = !deletePostsAlso }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 自前のチェックボックスアイコン
                        Icon(
                            painter = painterResource(
                                id = if (deletePostsAlso)
                                    R.drawable.ic_toggle_on
                                else
                                    R.drawable.ic_toggle_off
                            ),
                            contentDescription = null,
                            tint = if (deletePostsAlso)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )

                        // アイコンとテキストの間に空白を追加
                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "既存のポストも削除",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp)) // 追加の余白

                // ボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp) // 元のスペースに
                ) {
                    // キャンセルボタン
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) // 元のサイズに
                    ) {
                        UserFontText(
                            text = "キャンセル",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }

                    // 実行ボタン（プライマリカラー使用）
                    Button(
                        onClick = { onConfirm(deletePostsAlso) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isGoogleFit)
                                SnsType.GOOGLEFIT.brandColor
                            else
                                account.snsType.brandColor // 各アカウントのブランドカラー
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) // 元のサイズに
                    ) {
                        UserFontText(
                            text = "OK",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlueskyPeriodDialog(
    account: Account.Bluesky,
    onDismiss: () -> Unit,
    onPeriodChanged: (String) -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(account.period) }

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
                    text = "Bluesky設定",
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
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.BLUESKY.brandColor
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("12ヶ月", "24ヶ月", "全期間").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.BLUESKY.brandColor
                                )
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
                            onPeriodChanged(selectedPeriod)
                            onDismiss()
                        }
                    ) {
                        Text("変更", color = SnsType.BLUESKY.brandColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun MastodonPeriodDialog(
    account: Account.Mastodon,
    onDismiss: () -> Unit,
    onPeriodChanged: (String) -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(account.period) }

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
                    text = "Mastodon設定",
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
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.MASTODON.brandColor
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("12ヶ月", "24ヶ月", "全期間").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.MASTODON.brandColor
                                )
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
                            onPeriodChanged(selectedPeriod)
                            onDismiss()
                        }
                    ) {
                        Text("変更", color = SnsType.MASTODON.brandColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
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
fun GoogleFitPeriodDialog(
    account: Account.GoogleFit,
    onDismiss: () -> Unit,
    onPeriodChanged: (String) -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(account.period) }

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
                    text = "Google Fit設定",
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

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1ヶ月", "3ヶ月", "6ヶ月").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.GOOGLEFIT.brandColor
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("12ヶ月", "24ヶ月", "全期間").forEach { period ->
                                val isSelected = selectedPeriod == period
                                PeriodChip(
                                    period = period,
                                    isSelected = isSelected,
                                    onClick = { selectedPeriod = period },
                                    brandColor = SnsType.GOOGLEFIT.brandColor
                                )
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
                            onPeriodChanged(selectedPeriod)
                            onDismiss()
                        }
                    ) {
                        Text("変更", color = SnsType.GOOGLEFIT.brandColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun FitbitAccountItem(
    account: Account.Fitbit,
    viewModel: AccountViewModel,
    mainViewModel: MainViewModel,
    onDeleteClick: () -> Unit
) {
    var isHistoryFetching by remember { mutableStateOf(false) }
    var remainingTime by remember { mutableStateOf("") }
    var availablePeriod by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }

    val context = LocalContext.current
    val historyManager = remember { FitbitHistoryManager(context) }

    // タイマー更新用
    LaunchedEffect(Unit) {
        while (true) {
            val canFetch = historyManager.canFetchHistory(account.userId)
            remainingTime = if (canFetch) "" else historyManager.formatRemainingTime(account.userId)
            availablePeriod = historyManager.getAvailablePeriod(account.userId)

            delay(1000) // 1秒ごとに更新
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // アカウント情報表示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fitbit),
                    contentDescription = "Fitbit",
                    tint = SnsType.FITBIT.brandColor,
                    modifier = Modifier.size(26.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 表示切り替えスイッチ
                Switch(
                    checked = account.isVisible,
                    onCheckedChange = { viewModel.toggleAccountVisibility(account.userId) }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 削除ボタン
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onDeleteClick() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 取得可能期間表示
            if (availablePeriod != null) {
                val (startDate, endDate) = availablePeriod!!
                val formatter = DateTimeFormatter.ofPattern("yyyy/M/d")

                Text(
                    text = "取得可能期間",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "${startDate.format(formatter)}〜${endDate.format(formatter)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 過去データ取得ボタン
            val canFetch = historyManager.canFetchHistory(account.userId) && availablePeriod != null

            Button(
                onClick = {
                    if (canFetch && !isHistoryFetching) {
                        isHistoryFetching = true
                        // 過去データ取得処理
                        mainViewModel.fetchFitbitHistoryData(account.userId) {
                            isHistoryFetching = false
                        }
                    }
                },
                enabled = canFetch && !isHistoryFetching,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canFetch) SnsType.FITBIT.brandColor else Color.Gray,
                    disabledContainerColor = Color.Gray
                )
            ) {
                when {
                    isHistoryFetching -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("取得中...")
                    }
                    canFetch -> {
                        Text("過去データを取得")
                    }
                    availablePeriod == null -> {
                        Text("取得可能なデータがありません")
                    }
                    else -> {
                        Text("過去データを取得 (残り $remainingTime)")
                    }
                }
            }

            // 注意書き
            if (availablePeriod != null) {
                Text(
                    text = "※最古のデータより2ヶ月分を取得します",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PeriodChip(
    period: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    brandColor: Color = MaterialTheme.colorScheme.onSurface // ← 追加
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(32.dp)
            .width(72.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) brandColor else Color.Transparent, // ← 修正
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) // ← 修正
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