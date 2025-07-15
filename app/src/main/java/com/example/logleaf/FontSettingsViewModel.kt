// ★★★ FontSettingsViewModel.kt (全体をこれに置き換えてください) ★★★

package com.example.logleaf // あなたのパッケージ名に合わせてください

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight // ★ FontWeight を import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.ui.theme.availableFonts // あなたのパッケージ名に合わせてください
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ★★★ [変更点1] UIの状態に、fontWeightを追加 ★★★
data class FontSettingsUiState(
    val selectedFontName: String = "Default",
    val selectedFontFamily: FontFamily = FontFamily.Default,
    val selectedFontWeight: FontWeight = FontWeight.Normal, // ← これを追加！
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
        viewModelScope.launch {
            combine(
                fontSettingsManager.fontNameFlow,
                fontSettingsManager.fontSizeFlow,
                fontSettingsManager.lineHeightFlow,
                fontSettingsManager.letterSpacingFlow
            ) { fontName, fontSize, lineHeight, letterSpacing ->
                // ★★★ [変更点2] 選択されたフォントの情報を、パレットから見つけ出す ★★★
                val selectedAppFont = availableFonts.find { it.name == fontName }

                // 新しい設定値でUiStateを更新
                FontSettingsUiState(
                    selectedFontName = fontName,
                    // ★ パレットの情報を使って、familyとweightを正しく更新する
                    selectedFontFamily = selectedAppFont?.fontFamily ?: FontFamily.Default,
                    selectedFontWeight = selectedAppFont?.fontWeight ?: FontWeight.Normal, // ← これを追加！
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    letterSpacing = letterSpacing
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // --- UIからのイベントを処理する関数 (変更なし) ---

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

    // ★★★ [新設] リセットボタンのための関数 ★★★
    fun resetSettings() {
        viewModelScope.launch {
            // 保存されている設定を、全てデフォルト値で上書きする
            fontSettingsManager.saveFontName("Default")
            fontSettingsManager.saveFontSize(16f)
            fontSettingsManager.saveLineHeight(1.5f)
            fontSettingsManager.saveLetterSpacing(0.1f)
            // これにより、initブロックのcombineが再実行され、UIが自動的に更新される
        }
    }

    // --- ViewModelを生成するためのFactory (変更なし) ---
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