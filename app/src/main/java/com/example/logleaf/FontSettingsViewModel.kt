// ★★★ FontSettingsViewModel.kt (全体をこれに置き換えてください) ★★★

package com.example.logleaf // あなたのパッケージ名に合わせてください

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.data.font.FontSettingsManager
import com.example.logleaf.ui.settings.font.AppFont
import com.example.logleaf.ui.settings.font.FontSourceType
import com.example.logleaf.ui.settings.font.FontStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class FontSettingsUiState(
    val availableFonts: List<AppFont> = emptyList(),
    val selectedFontName: String = "Default",
    val selectedFontFamily: FontFamily = FontFamily.Default,
    val selectedFontWeight: FontWeight = FontWeight.Normal,
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0.1f
)
// --- ViewModel本体 ---
class FontSettingsViewModel(
    private val application: Application,
    private val fontSettingsManager: FontSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FontSettingsUiState())
    val uiState: StateFlow<FontSettingsUiState> = _uiState.asStateFlow()

    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // ★★★ ハイブリッドなフォントリストを定義 ★★★
    private val fontTemplates = listOf(
        AppFont(name = "Default", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Internal(0), status = FontStatus.INTERNAL, fontFamily = FontFamily.Default),
        AppFont(name = "M PLUS 1", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Internal(R.font.m_plus_1), status = FontStatus.INTERNAL),
        AppFont(name = "Noto Sans JP", fontWeight = FontWeight.Bold, sourceType = FontSourceType.Internal(R.font.noto_sans_jp), status = FontStatus.INTERNAL),
        AppFont(name = "Sawarabi Mincho", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/sawarabimincho/SawarabiMincho-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED),
        AppFont(name = "DotGothic16", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/dotgothic16/DotGothic16-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED),
        AppFont(name = "Yusei Magic", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/yuseimagic/YuseiMagic-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED),
        AppFont(name = "Zen Maru Gothic", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/zenmarugothic/ZenMaruGothic-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED),
        AppFont(name = "Shippori Mincho", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/shipporimincho/ShipporiMincho-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED),
        AppFont(name = "Kiwi Maru", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/kiwimaru/KiwiMaru-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED),
        AppFont(name = "RocknRoll One", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/rocknrollone/RocknRollOne-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED),
        AppFont(name = "Hina Mincho", fontWeight = FontWeight.Normal, sourceType = FontSourceType.Downloadable("https://github.com/google/fonts/raw/main/ofl/hinamincho/HinaMincho-Regular.ttf"), status = FontStatus.NOT_DOWNLOADED)

    )

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id != -1L) {
                    handleDownloadCompletion(id)
                }
            }
        }
        application.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)

        viewModelScope.launch {
            combine(
                fontSettingsManager.fontNameFlow,
                fontSettingsManager.fontSizeFlow,
                fontSettingsManager.lineHeightFlow,
                fontSettingsManager.letterSpacingFlow,
                fontSettingsManager.downloadingFontsFlow
            ) { fontName, fontSize, lineHeight, letterSpacing, downloadingList ->

                val currentFontList = fontTemplates.map { fontTemplate ->
                    when (val source = fontTemplate.sourceType) {
                        is FontSourceType.Downloadable -> {
                            val file = File(application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${fontTemplate.name.replace(" ", "_")}.ttf")
                            val downloadingEntry = downloadingList.find { it.second == fontTemplate.name }

                            when {
                                file.exists() && file.length() > 0 -> fontTemplate.copy(status = FontStatus.DOWNLOADED, fontFamily = createFontFamily(file))
                                downloadingEntry != null -> fontTemplate.copy(status = FontStatus.DOWNLOADING, downloadId = downloadingEntry.first)
                                else -> fontTemplate.copy(status = FontStatus.NOT_DOWNLOADED)
                            }
                        }
                        is FontSourceType.Internal -> {
                            fontTemplate.copy(fontFamily = if (source.resourceId != 0) FontFamily(Font(source.resourceId)) else FontFamily.Default)
                        }
                    }
                }

                val selectedFont = currentFontList.find { it.name == fontName }

                FontSettingsUiState(
                    availableFonts = currentFontList,
                    selectedFontName = fontName,
                    selectedFontFamily = selectedFont?.fontFamily ?: FontFamily.Default,
                    selectedFontWeight = selectedFont?.fontWeight ?: FontWeight.Normal,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    letterSpacing = letterSpacing
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onFontSelected(font: AppFont) {
        when (font.sourceType) {
            is FontSourceType.Internal -> {
                viewModelScope.launch { fontSettingsManager.saveFontName(font.name) }
            }
            is FontSourceType.Downloadable -> {
                when (font.status) {
                    FontStatus.DOWNLOADED -> viewModelScope.launch { fontSettingsManager.saveFontName(font.name) }
                    FontStatus.NOT_DOWNLOADED -> downloadFont(font)
                    FontStatus.DOWNLOADING -> cancelDownload(font)
                    FontStatus.INTERNAL -> {} // このケースはありえない
                }
            }
        }
    }

    private fun downloadFont(font: AppFont) {
        val source = font.sourceType as? FontSourceType.Downloadable ?: return
        val targetFileName = "${font.name.replace(" ", "_")}.ttf"
        val request = DownloadManager.Request(Uri.parse(source.url))
            .setTitle("${font.name} をダウンロード中")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(application, Environment.DIRECTORY_DOWNLOADS, targetFileName)
            .setAllowedOverMetered(true)

        val downloadId = downloadManager.enqueue(request)
        viewModelScope.launch { fontSettingsManager.addDownloadingFont(downloadId, font.name) }
    }

    private fun cancelDownload(font: AppFont) {
        if (font.status != FontStatus.DOWNLOADING || font.downloadId == -1L) return
        downloadManager.remove(font.downloadId)
        viewModelScope.launch { fontSettingsManager.removeDownloadingFont(font.downloadId) }
    }

    private fun handleDownloadCompletion(id: Long) {
        viewModelScope.launch {
            val downloadingFonts = fontSettingsManager.downloadingFontsFlow.first()
            val fontName = downloadingFonts.find { it.first == id }?.second ?: return@launch

            val query = DownloadManager.Query().setFilterById(id)
            val cursor: Cursor? = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)

                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    if (reasonIndex != -1) {
                        val reason = cursor.getInt(reasonIndex)
                        Log.e("FontSettingsViewModel", "Download failed for '$fontName'. Reason code: $reason")
                    } else {
                        Log.e("FontSettingsViewModel", "Download failed for '$fontName'. Reason code not available.")
                    }
                }
                cursor.close()
            }
            fontSettingsManager.removeDownloadingFont(id)
        }
    }

    private fun createFontFamily(file: File): FontFamily {
        return try { FontFamily(Font(file)) } catch (e: Exception) { FontFamily.Default }
    }

    fun onFontSizeChanged(size: Float) { viewModelScope.launch { fontSettingsManager.saveFontSize(size) } }
    fun onLineHeightChanged(height: Float) { viewModelScope.launch { fontSettingsManager.saveLineHeight(height) } }
    fun onLetterSpacingChanged(spacing: Float) { viewModelScope.launch { fontSettingsManager.saveLetterSpacing(spacing) } }
    fun resetSettings() {
        viewModelScope.launch {
            fontSettingsManager.saveFontName("Default")
            fontSettingsManager.saveFontSize(16f)
            fontSettingsManager.saveLineHeight(1.5f)
            fontSettingsManager.saveLetterSpacing(0.1f)
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            fontSettingsManager: FontSettingsManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FontSettingsViewModel(application, fontSettingsManager) as T
            }
        }
    }
}