package com.example.logleaf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.Account
import com.example.logleaf.SessionManager
import com.example.logleaf.db.PostDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val sessionManager: SessionManager,
    private val postDao: PostDao // ◀️ 追加：投稿を削除するために必要
) : ViewModel() {

    /**
     * ▼ 変更点: SessionManagerのFlowを直接監視し、UIに公開する
     * これにより、SessionManagerでの変更が自動的にUIに反映されるようになる。
     */
    val accounts: StateFlow<List<Account>> = sessionManager.accountsFlow
        .stateIn(
            scope = viewModelScope,
            // 画面が非表示になって5秒後に監視を停止する（電池に優しい）
            started = SharingStarted.WhileSubscribed(5000),
            // 初期値は空リスト
            initialValue = emptyList()
        )

    /**
     * ◀ 追加：アカウントの表示/非表示を切り替える
     */
    fun toggleAccountVisibility(accountId: String) {
        // SessionManagerの関数を呼び出すだけ。
        // `accounts` StateFlowが自動的に更新を検知してUIに反映してくれる。
        sessionManager.toggleAccountVisibility(accountId)
    }

    /**
     * ▼ 変更点: アカウントと、それに紐づく投稿をすべて削除する関数に強化
     */
    fun deleteAccountAndPosts(account: Account) {
        viewModelScope.launch {
            // 1. データベースからこのアカウントの投稿をすべて削除
            postDao.deletePostsByAccountId(account.userId)
            // 2. SessionManagerからアカウントを削除
            sessionManager.deleteAccount(account)
            // `accounts` StateFlowが自動的に更新を検知するので、手動でのリスト更新は不要
        }
    }

    /**
     * ▼ 変更点: Factoryも postDao を受け取るように変更
     */
    companion object {
        fun provideFactory(
            sessionManager: SessionManager,
            postDao: PostDao // ◀ 追加
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // ◀ postDaoを渡してViewModelを生成
                return AccountViewModel(sessionManager, postDao) as T
            }
        }
    }

    /**
     * GitHubアカウントの期間設定を変更する
     */
    fun updateGitHubAccountPeriod(username: String, newPeriod: String) {
        sessionManager.updateGitHubAccountPeriod(username, newPeriod)
    }
}