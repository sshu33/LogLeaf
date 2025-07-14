package com.example.logleaf

import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.ui.theme.availableFonts
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// 画面の状態を表すデータクラス
data class FontSettingsUiState(
    val selectedFontName: String = "Default",
    val selectedFontFamily: FontFamily = FontFamily.Default,
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0.1f
)

class FontSettingsViewModel(
    private val fontSettingsManager: FontSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FontSettingsUiState())
    val uiState: StateFlow<FontSettingsUiState> = _uiState.asStateFlow()

    init {
        // ViewModelが作られた瞬間に、保存されている設定を読み込む
        viewModelScope.launch {
            // 複数のFlowを一つにまとめる
            combine(
                fontSettingsManager.fontNameFlow,
                fontSettingsManager.fontSizeFlow,
                fontSettingsManager.lineHeightFlow,
                fontSettingsManager.letterSpacingFlow
            ) { fontName, fontSize, lineHeight, letterSpacing ->
                // 新しい設定値でUiStateを更新
                FontSettingsUiState(
                    selectedFontName = fontName,
                    selectedFontFamily = availableFonts.find { it.name == fontName }?.fontFamily ?: FontFamily.Default,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    letterSpacing = letterSpacing
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // --- UIからのイベントを処理する関数 ---

    fun onFontSelected(fontName: String) {
        viewModelScope.launch {
            fontSettingsManager.saveFontName(fontName)
        }
    }

    fun onFontSizeChanged(size: Float) {
        viewModelScope.launch {
            fontSettingsManager.saveFontSize(size)
        }
    }

    fun onLineHeightChanged(height: Float) {
        viewModelScope.launch {
            fontSettingsManager.saveLineHeight(height)
        }
    }

    fun onLetterSpacingChanged(spacing: Float) {
        viewModelScope.launch {
            fontSettingsManager.saveLetterSpacing(spacing)
        }
    }

    // --- ViewModelを生成するためのFactory ---
    companion object {
        fun provideFactory(
            fontSettingsManager: FontSettingsManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FontSettingsViewModel(fontSettingsManager) as T
            }
        }
    }
}