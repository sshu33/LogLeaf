package com.example.logleaf.data.font // あなたのパッケージ名に合わせてください

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey // ★ stringSetPreferencesKey を import
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "font_settings") // 名前をより明確に

class FontSettingsManager(private val context: Context) {

    companion object {
        val FONT_NAME_KEY = stringPreferencesKey("font_name")
        val FONT_SIZE_KEY = floatPreferencesKey("font_size")
        val LINE_HEIGHT_KEY = floatPreferencesKey("line_height")
        val LETTER_SPACING_KEY = floatPreferencesKey("letter_spacing")

        // ★★★ これが、赤字の原因だった、新しい金庫の「鍵」です ★★★
        val DOWNLOADING_FONTS_KEY = stringSetPreferencesKey("downloading_fonts_v2")
    }

    // --- 設定を保存するための関数 ---
    suspend fun saveFontName(fontName: String) { context.dataStore.edit { it[FONT_NAME_KEY] = fontName } }
    suspend fun saveFontSize(fontSize: Float) { context.dataStore.edit { it[FONT_SIZE_KEY] = fontSize } }
    suspend fun saveLineHeight(lineHeight: Float) { context.dataStore.edit { it[LINE_HEIGHT_KEY] = lineHeight } }
    suspend fun saveLetterSpacing(letterSpacing: Float) { context.dataStore.edit { it[LETTER_SPACING_KEY] = letterSpacing } }

    // ★★★ 「ダウンロード中のフォント」を追加/削除するための、新しい関数 ★★★
    suspend fun addDownloadingFont(downloadId: Long, fontName: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[DOWNLOADING_FONTS_KEY] ?: emptySet()
            preferences[DOWNLOADING_FONTS_KEY] = currentSet + "$downloadId,$fontName"
        }
    }
    suspend fun removeDownloadingFont(downloadId: Long) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[DOWNLOADING_FONTS_KEY] ?: return@edit
            val fontToRemove = currentSet.find { it.startsWith("$downloadId,") }
            if (fontToRemove != null) {
                preferences[DOWNLOADING_FONTS_KEY] = currentSet - fontToRemove
            }
        }
    }

    // --- 設定を読み出すためのFlow ---
    val fontNameFlow: Flow<String> = context.dataStore.data.map { it[FONT_NAME_KEY] ?: "Default" }
    val fontSizeFlow: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE_KEY] ?: 16f }
    val lineHeightFlow: Flow<Float> = context.dataStore.data.map { it[LINE_HEIGHT_KEY] ?: 1.5f }
    val letterSpacingFlow: Flow<Float> = context.dataStore.data.map { it[LETTER_SPACING_KEY] ?: 0.1f }

    // ★★★ 「ダウンロード中のフォント情報」を読み出すための、新しいFlow ★★★
    val downloadingFontsFlow: Flow<List<Pair<Long, String>>> = context.dataStore.data
        .map { preferences ->
            (preferences[DOWNLOADING_FONTS_KEY] ?: emptySet()).mapNotNull { entry ->
                val parts = entry.split(',')
                if (parts.size == 2) {
                    parts[0].toLongOrNull()?.let { id -> Pair(id, parts[1]) }
                } else { null }
            }
        }
}