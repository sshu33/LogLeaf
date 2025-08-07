package com.example.logleaf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UIの状態を表すデータクラス
data class BlueskyLoginUiState(
    val handle: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null  // ← 追加！
)

// 一度きりのUIイベントを通知するためのクラス
sealed class BlueskyLoginEvent {
    object LoginSuccess : BlueskyLoginEvent()
    object HideKeyboard : BlueskyLoginEvent()
}


// ViewModel本体
class BlueskyLoginViewModel(
    private val blueskyApi: BlueskyApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlueskyLoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<BlueskyLoginEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun onHandleChange(newHandle: String) {
        _uiState.update { it.copy(handle = newHandle, error = null) } // エラークリア
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.update { it.copy(password = newPassword, error = null) } // エラークリア
    }

    // UIからの実行命令を受け取る司令塔となる関数
    fun onLoginSubmitted() {
        // 入力が空の場合はエラー表示
        if (_uiState.value.handle.isBlank() || _uiState.value.password.isBlank()) {
            _uiState.update { it.copy(error = "ハンドル名とアプリパスワードを入力してください") }
            return
        }

        viewModelScope.launch {
            // 1. まずUIに「キーボードを隠せ」と命令する
            _eventFlow.emit(BlueskyLoginEvent.HideKeyboard)
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 2. 実際のログイン処理を実行
            val success = blueskyApi.login(
                handle = _uiState.value.handle.trim(),
                password = _uiState.value.password.trim()
            )

            // 3. 結果に応じて適切な状態更新
            if (success) {
                _eventFlow.emit(BlueskyLoginEvent.LoginSuccess)
            } else {
                // ★ トーストの代わりにエラー状態を更新
                _uiState.update {
                    it.copy(
                        error = "ログインに失敗しました。ハンドル名とアプリパスワードを確認してください。",
                        isLoading = false
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}