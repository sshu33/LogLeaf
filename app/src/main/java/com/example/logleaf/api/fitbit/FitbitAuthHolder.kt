package com.example.logleaf.api.fitbit

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Fitbit認証情報を一時的に保持するシングルトンオブジェクト
object FitbitAuthHolder {
    var clientId: String? = null
    var clientSecret: String? = null

    // ブラウザからの認証コードを受け取るための「郵便受け」
    private val _authCodeFlow = MutableSharedFlow<String>()
    val authCodeFlow = _authCodeFlow.asSharedFlow()

    suspend fun postAuthCode(code: String) {
        _authCodeFlow.emit(code)
    }

    fun clear() {
        clientId = null
        clientSecret = null
    }
}