package com.example.logleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.logleaf.FontSettingsViewModel
import com.example.logleaf.ui.theme.availableFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsScreen(
    viewModel: FontSettingsViewModel,
    navController: NavController
) {
    // ViewModelから、現在のUIの状態を受け取る
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文字の設定") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()) // 画面全体をスクロール可能に
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- 1. プレビューエリア ---
            PreviewArea(
                fontFamily = uiState.selectedFontFamily,
                fontSize = uiState.fontSize,
                lineHeight = uiState.lineHeight,
                letterSpacing = uiState.letterSpacing
            )

            // --- 2. フォント選択エリア ---
            FontSelector(
                selectedFontName = uiState.selectedFontName,
                onFontSelected = viewModel::onFontSelected
            )

            // --- 3. 文字サイズ調整スライダー ---
            SettingsSlider(
                label = "文字サイズ",
                value = uiState.fontSize,
                onValueChange = viewModel::onFontSizeChanged,
                valueRange = 12f..24f, // 12spから24spまで
                steps = 11, // (24-12) / 1
                valueLabel = { "${it.toInt()} sp" }
            )

            // --- 4. 行間調整スライダー ---
            SettingsSlider(
                label = "行間",
                value = uiState.lineHeight,
                onValueChange = viewModel::onLineHeightChanged,
                valueRange = 1.2f..2.0f,
                steps = 7,
                valueLabel = { "%.1f".format(it) }
            )

            // --- 5. 字間調整スライダー ---
            SettingsSlider(
                label = "字間",
                value = uiState.letterSpacing,
                onValueChange = viewModel::onLetterSpacingChanged,
                valueRange = 0f..0.5f,
                steps = 9,
                valueLabel = { "%.2f".format(it) }
            )
        }
    }
}


/**
 * プレビュー表示のための部品
 */
@Composable
private fun PreviewArea(
    fontFamily: androidx.compose.ui.text.font.FontFamily,
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = "2025年、夏の夜空に浮かぶType-Gの衛星。LogLeafのスクリーンに、思い出の欠片がキラキラと、108個の星みたいに流れていく。タイムラインが交差する、この静かなデジタル・アーカイブの中で。",
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineHeight).sp,
                letterSpacing = letterSpacing.sp
            )
        )
    }
}

/**
 * フォント選択チップを表示するための部品
 */
@Composable
private fun FontSelector(
    selectedFontName: String,
    onFontSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(availableFonts) { font ->
            val isSelected = font.name == selectedFontName
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onFontSelected(font.name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = font.name,
                    fontFamily = font.fontFamily,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}


/**
 * 設定項目を調整するスライダーのための、再利用可能な部品
 */
@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = valueLabel(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}