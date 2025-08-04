package com.example.logleaf.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.logleaf.MainViewModel
import com.example.logleaf.R
import com.example.logleaf.ui.components.CustomTopAppBar
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    navController: NavController,
    mainViewModel: MainViewModel
) {
    val dataSize by mainViewModel.dataSize.collectAsState()
    val dataSizeDetails by mainViewModel.dataSizeDetails.collectAsState()

    val context = LocalContext.current

    val backupProgress by mainViewModel.backupProgress.collectAsState()
    val restoreProgress by mainViewModel.restoreProgress.collectAsState()

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                mainViewModel.restoreFromBackup(uri) { success, message ->
                    Toast.makeText(
                        context,
                        if (success) "復元が完了しました！" else "復元に失敗しました: $message",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )

    // --- ▼ 最適化メニューで使う変数を準備 ---
    var isOptimizationExpanded by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val periodOptions = listOf("１ヶ月", "３ヶ月", "半年", "１年")
    val qualityOptions = mapOf(
        "高画質" to "high",
        "中画質" to "medium",
        "低画質" to "low"
    )
    var selectedPeriod by remember { mutableStateOf(periodOptions[0]) }
    var selectedQualityText by remember { mutableStateOf(qualityOptions.keys.elementAt(1)) }

    var isPeriodMenuExpanded by remember { mutableStateOf(false) }
    var isQualityMenuExpanded by remember { mutableStateOf(false) }

    val backupState by mainViewModel.backupState.collectAsState()

    val animatedProgress by animateFloatAsState(
        targetValue = backupState.progress,
        animationSpec = tween(durationMillis = 300),
        label = "BackupProgress"
    )

    val restoreState by mainViewModel.restoreState.collectAsState()

    val animatedRestoreProgress by animateFloatAsState(
        targetValue = restoreState.progress,
        animationSpec = tween(durationMillis = 300),
        label = "RestoreProgress"
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                mainViewModel.importDataFromZip(uri)
            }
        }
    )

    LaunchedEffect(Unit) {
        mainViewModel.calculateDataSize()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // --- 1. 自作のタイトルバーを配置 ---
        CustomTopAppBar(
            title = "バックアップ",
            onNavigateBack = { navController.popBackStack() }
        )

        // --- 2. スクロール可能なコンテンツエリア ---
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp) // コンテンツ全体の左右の余白
        ) {

            // データサイズ表示（シンプル右詰め）
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. タイトル
                UserFontText(
                    text = "現在のデータサイズ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. データサイズ（アイコン付き）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "データサイズアイコン",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildAnnotatedString {
                            append(dataSize.replace(" MB", " "))
                            withStyle(style = SpanStyle(fontSize = 0.7.em)) {
                                append("MB")
                            }
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. データ内訳
                UserFontText(
                    text = dataSizeDetails, // ← 固定値から変更
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- ▼ 改良版：ミニマルでスタイリッシュな最適化ボタン ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton(
                    onClick = { isOptimizationExpanded = !isOptimizationExpanded },
                    modifier = Modifier.wrapContentWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "ストレージの最適化",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 展開されるメニュー
                AnimatedVisibility(visible = isOptimizationExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // --- 期間選択 ---
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { isPeriodMenuExpanded = true },
                                    modifier = Modifier.width(80.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 4.dp
                                    )
                                ) {
                                    Text(
                                        text = selectedPeriod,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                DropdownMenu(
                                    expanded = isPeriodMenuExpanded,
                                    onDismissRequest = { isPeriodMenuExpanded = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    periodOptions.forEach { period ->
                                        DropdownMenuItem(
                                            text = { Text(period) },
                                            onClick = {
                                                selectedPeriod = period
                                                isPeriodMenuExpanded = false
                                            },
                                            colors = MenuDefaults.itemColors(textColor = Color.Black)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "以前の画像を",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        // --- 画質選択 ---
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { isQualityMenuExpanded = true },
                                    modifier = Modifier.width(80.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 4.dp
                                    )
                                ) {
                                    Text(
                                        text = selectedQualityText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DropdownMenu(
                                    expanded = isQualityMenuExpanded,
                                    onDismissRequest = { isQualityMenuExpanded = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    qualityOptions.keys.forEach { qualityText ->
                                        DropdownMenuItem(
                                            text = { Text(qualityText) },
                                            onClick = {
                                                selectedQualityText = qualityText
                                                isQualityMenuExpanded = false
                                            },
                                            colors = MenuDefaults.itemColors(textColor = Color.Black)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "に圧縮",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // --- 注意書き ---
                        Text(
                            text = "クラウドに保存済みの元画像には影響しません",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- 実行ボタン ---
                        Button(
                            onClick = { showConfirmDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "圧縮を実行",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 基本操作見出し
            UserFontText(
                text = "基本操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 基本操作セクション
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(120.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    // バックアップ作成
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !backupState.isInProgress) {
                                if (!backupState.isInProgress) {
                                    mainViewModel.exportPostsWithImages()
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = null,
                            tint = if (backupState.isInProgress)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else if (backupState.isCompleted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "バックアップ作成",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 状態に応じた説明文
                            val subtitleText = when {
                                backupState.isInProgress -> backupState.statusText
                                backupState.isCompleted -> "バックアップが完了しました"
                                backupState.statusText.startsWith("エラー") -> backupState.statusText
                                else -> "すべての投稿と画像をZIP形式で保存"
                            }

                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    backupState.isCompleted -> MaterialTheme.colorScheme.onSurface
                                    backupState.statusText.startsWith("エラー") -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                }
                            )

                            // プログレスバー（進行中のみ表示）
                            if (backupState.isInProgress) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            }
                        }

                        // 右側のアイコン
                        Box(modifier = Modifier.size(24.dp)) {
                            when {
                                backupState.isInProgress -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .alpha(0.7f),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                backupState.isCompleted -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check), // チェックマークアイコン
                                        contentDescription = "完了",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                backupState.statusText.startsWith("エラー") -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_error), // エラーアイコン
                                        contentDescription = "エラー",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    // データ復元
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !restoreState.isInProgress) {
                                if (!restoreState.isInProgress) {
                                    // ファイルピッカーを開く（BackupSettingsScreenで追加したfilePickerLauncherを使用）
                                    filePickerLauncher.launch("application/zip")
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_upload),
                            contentDescription = null,
                            tint = if (restoreState.isInProgress)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else if (restoreState.isCompleted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "データ復元",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 状態に応じた説明文
                            val subtitleText = when {
                                restoreState.isInProgress -> restoreState.statusText
                                restoreState.isCompleted -> "復元が完了しました"
                                restoreState.statusText.startsWith("エラー") -> restoreState.statusText
                                else -> "ZIP形式のバックアップファイルから復元"
                            }

                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    restoreState.isCompleted -> MaterialTheme.colorScheme.onSurface // 黒文字
                                    restoreState.statusText.startsWith("エラー") -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                }
                            )

                            // プログレスバー（進行中のみ表示）
                            if (restoreState.isInProgress) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { animatedRestoreProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            }
                        }

                        // 右側のアイコン
                        Box(modifier = Modifier.size(24.dp)) {
                            when {
                                restoreState.isInProgress -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .alpha(0.7f),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                restoreState.isCompleted -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check),
                                        contentDescription = "完了",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                restoreState.statusText.startsWith("エラー") -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_error),
                                        contentDescription = "エラー",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 高度な設定見出し
            UserFontText(
                text = "高度な設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 高度な設定セクション
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(180.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    // クラウド連携
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "クラウド連携は準備中です",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cloud),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "クラウド同期",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Google Drive と連携",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "未設定",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 自動バックアップ
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "自動バックアップ設定は準備中です",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_auto_backup),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自動バックアップ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "定期的な自動バックアップ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "オフ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // バックアップ履歴
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "バックアップ履歴は準備中です",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_backup_log),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "バックアップ履歴",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    if (backupProgress != null) {
        Dialog(onDismissRequest = { }) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(backupProgress!!)
                }
            }
        }
    }
}

