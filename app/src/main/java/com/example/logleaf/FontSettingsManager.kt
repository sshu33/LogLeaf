package com.example.logleaf

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// アプリ全体で唯一のDataStoreインスタンスを作成するおまじない
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class FontSettingsManager(private val context: Context) {

    // DataStoreにデータを保存するための「鍵」を定義する
    companion object {
        // フォント名を保存するための鍵 (String型)
        val FONT_NAME_KEY = stringPreferencesKey("font_name")
        // 文字サイズを保存するための鍵 (Float型)
        val FONT_SIZE_KEY = floatPreferencesKey("font_size")
        // 行間を保存するための鍵 (Float型)
        val LINE_HEIGHT_KEY = floatPreferencesKey("line_height")
        // 字間を保存するための鍵 (Float型)
        val LETTER_SPACING_KEY = floatPreferencesKey("letter_spacing")
    }

    // --- 設定を保存するための関数 ---

    suspend fun saveFontName(fontName: String) {
        context.dataStore.edit { preferences ->
            preferences[FONT_NAME_KEY] = fontName
        }
    }

    suspend fun saveFontSize(fontSize: Float) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE_KEY] = fontSize
        }
    }

    suspend fun saveLineHeight(lineHeight: Float) {
        context.dataStore.edit { preferences ->
            preferences[LINE_HEIGHT_KEY] = lineHeight
        }
    }

    suspend fun saveLetterSpacing(letterSpacing: Float) {
        context.dataStore.edit { preferences ->
            preferences[LETTER_SPACING_KEY] = letterSpacing
        }
    }


    // --- 設定を読み出すためのFlow ---

    val fontNameFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            // 保存された値がなければ、デフォルトのフォント名 "Default" を返す
            preferences[FONT_NAME_KEY] ?: "Default"
        }

    val fontSizeFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            // 保存された値がなければ、デフォルトの文字サイズ 16f を返す
            preferences[FONT_SIZE_KEY] ?: 16f
        }

    val lineHeightFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            // 保存された値がなければ、デフォルトの行間 1.5f を返す
            preferences[LINE_HEIGHT_KEY] ?: 1.5f
        }

    val letterSpacingFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            // 保存された値がなければ、デフォルトの字間 0.1f を返す
            preferences[LETTER_SPACING_KEY] ?: 0.1f
        }
}