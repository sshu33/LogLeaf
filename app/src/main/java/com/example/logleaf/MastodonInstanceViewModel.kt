package com.example.logleaf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UIの状態を表すデータクラス
data class MastodonInstanceUiState(
    val instanceUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

// 一度きりのイベントを通知するためのクラス
sealed class MastodonInstanceEvent {
    // 認証フローを開始し、ブラウザへ遷移するイベント
    data class NavigateToBrowser(val authUrl: String) : MastodonInstanceEvent()
    // 認証が全て成功し、前の画面に戻るイベント
    object AuthenticationSuccess : MastodonInstanceEvent()
}

// ★ SessionManagerも受け取るように変更
class MastodonInstanceViewModel(
    private val mastodonApi: MastodonApi,
    private val sessionManager: SessionManager,
    private val initialInstanceUrl: String?

) : ViewModel() {

    private val _uiState = MutableStateFlow(
        // ★★★ 初期URLがあれば、それをUI状態の初期値にセットする ★★★
        MastodonInstanceUiState(instanceUrl = initialInstanceUrl ?: "")
    )
    val uiState = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<MastodonInstanceEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    // ★★★ 再認証フローかどうかを判定するプロパティ ★★★
    val isReAuthFlow: Boolean = initialInstanceUrl != null

    init {
        // ★★★ もし初期URLが渡されていたら（＝再認証フローなら）、自動で認証を開始 ★★★
        if (isReAuthFlow) {
            println("再認証フローを自動開始します: $initialInstanceUrl")
            onAppRegisterClicked()
        }

        // コールバックURIの監視はそのまま
        viewModelScope.launch {
            MastodonAuthHolder.uriFlow.collect { uri ->
                val code = uri.getQueryParameter("code")
                if (code != null) {
                    // 認可コードがあれば、手動テストと同じ処理を呼び出す
                    handleMastodonAuth(code)
                }
            }
        }
    }

    fun onInstanceUrlChange(newUrl: String) {
        _uiState.update { it.copy(instanceUrl = newUrl, error = null) }
    }


    // 「次へ」ボタン（アプリ登録）の処理
    fun onAppRegisterClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val url = _uiState.value.instanceUrl.trim().removeSuffix("/")

            val response = mastodonApi.registerApp(url)
            if (response != null) {
                // 成功した場合、次のステップのために情報を保管庫に保存
                MastodonAuthHolder.instanceUrl = url
                MastodonAuthHolder.clientId = response.clientId
                MastodonAuthHolder.clientSecret = response.clientSecret

                // 認可URLを組み立て
                val authUrl = "https://${url}/oauth/authorize?response_type=code&client_id=${response.clientId}&scope=read:statuses%20read:accounts&redirect_uri=logleaf://callback"
                _eventFlow.emit(MastodonInstanceEvent.NavigateToBrowser(authUrl))
            } else {
                _uiState.update { it.copy(error = "アプリの登録に失敗しました。URLを確認してください。") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun handleMastodonAuth(manualCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val instanceUrl = MastodonAuthHolder.instanceUrl
            val clientId = MastodonAuthHolder.clientId
            val clientSecret = MastodonAuthHolder.clientSecret

            if (manualCode.isNotBlank() && instanceUrl != null && clientId != null && clientSecret != null) {
                println("認可コードを使ってトークン交換を開始します。")
                val tokenResponse = mastodonApi.fetchAccessToken(instanceUrl, clientId, clientSecret, manualCode)

                if (tokenResponse != null) {
                    println("トークン交換成功！アカウント情報を取得します。")
                    val verifiedAccount = mastodonApi.verifyCredentials(instanceUrl, tokenResponse.accessToken)

                    if (verifiedAccount != null) {
                        // ★★★ 正しいパラメータでアカウントを生成する ★★★
                        val newAccount = Account.Mastodon(
                            instanceUrl = instanceUrl,
                            id = verifiedAccount.id, // ← verifiedAccountから数字のIDを設定
                            acct = verifiedAccount.acct,
                            username = verifiedAccount.username,
                            accessToken = tokenResponse.accessToken
                        )

                        sessionManager.saveAccount(newAccount)

                        println("アカウント保存成功！ User: ${newAccount.acct}")
                        _eventFlow.emit(MastodonInstanceEvent.AuthenticationSuccess)

                    } else {
                        _uiState.update { it.copy(error = "アカウント情報の取得に失敗しました。") }
                    }
                } else {
                    _uiState.update { it.copy(error = "トークンの交換に失敗しました。") }
                }
            } else {
                _uiState.update { it.copy(error = "認証情報が不足しています。最初からやり直してください。") }
            }

            MastodonAuthHolder.clear()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    companion object {
        fun provideFactory(
            mastodonApi: MastodonApi,
            sessionManager: SessionManager,
            initialInstanceUrl: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MastodonInstanceViewModel(
                    mastodonApi,
                    sessionManager,
                    initialInstanceUrl
                ) as T
            }
        }
    }
}