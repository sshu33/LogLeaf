package com.example.logleaf

import android.util.Log
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
    data class NavigateToBrowser(val authUrl: String) : MastodonInstanceEvent()
    object AuthenticationSuccess : MastodonInstanceEvent()
    // ★★★ 追加：キーボードを隠すようUIに指示するイベント ★★★
    object HideKeyboard : MastodonInstanceEvent()
}

// ★ SessionManagerも受け取るように変更
class MastodonInstanceViewModel(
    private val mastodonApi: MastodonApi,
    private val sessionManager: SessionManager,
    private val initialInstanceUrl: String?
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow("3ヶ月")
    val selectedPeriod = _selectedPeriod.asStateFlow()

    // 期間変更メソッド追加
    fun onPeriodChanged(newPeriod: String) {
        _selectedPeriod.value = newPeriod
    }

    private val _uiState = MutableStateFlow(
        MastodonInstanceUiState(instanceUrl = initialInstanceUrl ?: "")
    )
    val uiState = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<MastodonInstanceEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    val isReAuthFlow: Boolean = initialInstanceUrl != null

    init {
        Log.d("MastodonDebug", "ViewModel初期化")

        viewModelScope.launch {
            MastodonAuthHolder.uriFlow.collect { uri ->
                Log.d("MastodonDebug", "URI受信: $uri")
                val code = uri.getQueryParameter("code")
                Log.d("MastodonDebug", "認証コード: $code")
                if (code != null) {
                    handleMastodonAuth(code)
                }
            }
        }
    }

    // ★★★ 追加：UIから呼び出される新しい司令塔 ★★★
    fun onInstanceSubmitted() {
        viewModelScope.launch {
            // 1. まずUIに「キーボードを隠せ」と命令する
            _eventFlow.emit(MastodonInstanceEvent.HideKeyboard)
            // 2. その後、実際の認証処理を開始する
            onAppRegisterClicked()
        }
    }

    fun onInstanceUrlChange(newUrl: String) {
        _uiState.update { it.copy(instanceUrl = newUrl, error = null) }
    }

    // 「次へ」ボタン（アプリ登録）の処理
    fun onAppRegisterClicked() {
        // この関数は private にしても良いですが、一旦 public のままにしておきます
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val url = _uiState.value.instanceUrl.trim().removeSuffix("/")

            // アプリ登録に成功したらブラウザへ遷移
            val response = mastodonApi.registerApp(url)
            if (response != null) {
                MastodonAuthHolder.instanceUrl = url
                MastodonAuthHolder.clientId = response.clientId
                MastodonAuthHolder.clientSecret = response.clientSecret

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
                        val newAccount = Account.Mastodon(
                            instanceUrl = instanceUrl,
                            id = verifiedAccount.id,
                            acct = verifiedAccount.acct,
                            username = verifiedAccount.username,
                            accessToken = tokenResponse.accessToken,
                            clientId = clientId,
                            clientSecret = clientSecret,
                            period = _selectedPeriod.value
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