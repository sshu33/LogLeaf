package com.example.logleaf

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// アプリ登録情報とインスタンスURLを一時的に保持するシングルトンオブジェクト
object MastodonAuthHolder {
    var instanceUrl: String? = null
    var clientId: String? = null
    var clientSecret: String? = null

    // ★★★ ブラウザからのコールバックURIを受け取るための「郵便受け」を追加 ★★★
    private val _uriFlow = MutableSharedFlow<Uri>()
    val uriFlow = _uriFlow.asSharedFlow()

    suspend fun postUri(uri: Uri) {
        _uriFlow.emit(uri)
    }
    // ★★★ ここまで追加 ★★★

    fun clear() {
        instanceUrl = null
        clientId = null
        clientSecret = null
    }
}