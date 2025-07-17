package com.example.logleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.navigation.NavController
import com.example.logleaf.FontSettingsViewModel
import com.example.logleaf.ui.settings.font.AppFont
import com.example.logleaf.ui.settings.font.FontStatus
import com.example.logleaf.ui.theme.SettingsTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FontSettingsScreen(
    viewModel: FontSettingsViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文字の設定") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::resetSettings) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "リセット",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            )
        }
    ) { innerPadding ->
        ConstraintLayout(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val (previewRef, fontListRef, sliderAreaRef) = createRefs()

            Box(
                modifier = Modifier
                    .constrainAs(previewRef) {
                        top.linkTo(parent.top, margin = 16.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        width = Dimension.fillToConstraints
                    }
                    .padding(horizontal = 24.dp)
                    .heightIn(min = 120.dp)
            ) {
                PreviewArea(
                    fontFamily = uiState.selectedFontFamily,
                    fontWeight = uiState.selectedFontWeight,
                    fontSize = uiState.fontSize,
                    lineHeight = uiState.lineHeight,
                    letterSpacing = uiState.letterSpacing
                )
            }

            SettingsTheme {

            Column(
                modifier = Modifier
                    .constrainAs(fontListRef) {
                        top.linkTo(previewRef.bottom, margin = 24.dp)
                        bottom.linkTo(sliderAreaRef.top, margin = 16.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableFonts.forEach { font ->
                        FontChip(
                            font = font,
                            isSelected = font.name == uiState.selectedFontName,
                            onSelected = { viewModel.onFontSelected(font) }
                        )
                    }
                }
            }

                Column(
                    modifier = Modifier
                        .constrainAs(sliderAreaRef) {
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    MinimalSlider(
                        label = "フォントサイズ",
                        icon = Icons.Default.TextFields,
                        value = uiState.fontSize,
                        onValueChange = viewModel::onFontSizeChanged,
                        valueRange = 12f..24f,
                        steps = 11
                    )
                    MinimalSlider(
                        label = "行間",
                        icon = Icons.Default.FormatLineSpacing,
                        value = uiState.lineHeight,
                        onValueChange = viewModel::onLineHeightChanged,
                        valueRange = 1.2f..2.0f,
                        steps = 7
                    )
                    MinimalSlider(
                        label = "字間",
                        icon = Icons.Default.SpaceBar,
                        value = uiState.letterSpacing,
                        onValueChange = viewModel::onLetterSpacingChanged,
                        valueRange = 0f..0.5f,
                        steps = 9
                    )
                }
            }
        }
    }
}


// ★★★ セーブデータにあった、新しい部品たち ★★★

@Composable
private fun MinimalSlider(
    label: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(modifier = Modifier.padding(vertical = 0.dp)) { // 少し余白を調整
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun FontChip(
    font: AppFont,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    // ★★★ when式に、INTERNALの条件を追加 ★★★
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        font.status == FontStatus.DOWNLOADED || font.status == FontStatus.INTERNAL -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        font.status == FontStatus.DOWNLOADED || font.status == FontStatus.INTERNAL -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onSelected)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = font.name,
                fontFamily = font.fontFamily,
                fontWeight = font.fontWeight,
                color = textColor
            )

            when (font.status) {
                FontStatus.DOWNLOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = textColor,
                        strokeWidth = 2.dp
                    )
                }
                FontStatus.NOT_DOWNLOADED -> {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "ダウンロード",
                        modifier = Modifier.size(16.dp),
                        tint = textColor
                    )
                }
                // ★ 内部リソースとダウンロード済みは、アイコン不要なので、何もしない
                FontStatus.DOWNLOADED, FontStatus.INTERNAL -> { /* 何も表示しない */ }
            }
        }
    }
}

@Composable
private fun PreviewArea(
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSize: Float,
    lineHeight: Float,
    letterSpacing: Float
) {
    Column {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Spacer(Modifier.height(8.dp)) // Spacerは不要なため削除
        Text(
            text = "2025年、夏の夜空に浮かぶType-Gの衛星。LogLeafのスクリーンに、思い出の欠片がキラキラと、108個の星みたいに流れていく。タイムラインが交差する、この静かなデジタル・アーカイブの中で。",
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineHeight).sp,
                letterSpacing = letterSpacing.sp
            ),
            modifier = Modifier.padding(top = 8.dp) // Spacerの代わりにpaddingで調整
        )
    }
}