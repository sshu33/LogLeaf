package com.example.logleaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.data.model.Account
import com.example.logleaf.api.github.GitHubApi
import com.example.logleaf.api.github.GitHubRepository
import com.example.logleaf.data.session.SessionManager
import com.example.logleaf.db.PostDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val sessionManager: SessionManager,
    private val postDao: PostDao,
    private val gitHubApi: GitHubApi // ← 追加！
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = combine(
        sessionManager.accountsFlow,
        postDao.getPostsCountByAccountId("LOGLEAF_INTERNAL_POST"),
        sessionManager.isLogLeafVisible
    ) { sessionAccounts, internalPostCount, isLogLeafVisible ->
        val allAccounts = mutableListOf<Account>()

        // 内部投稿がある場合のみLogLeafアカウントを追加
        if (internalPostCount > 0) {
            allAccounts.add(
                Account.Internal(
                    postCount = internalPostCount,
                    isVisible = isLogLeafVisible
                )
            )
        }

        allAccounts.addAll(sessionAccounts)
        allAccounts
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun toggleAccountVisibility(accountId: String) {
        if (accountId == "LOGLEAF_INTERNAL_POST") {
            // LogLeafアカウントの場合
            sessionManager.toggleLogLeafVisibility()
        } else {
            // 既存のSNSアカウントの場合
            sessionManager.toggleAccountVisibility(accountId)
        }
    }

    fun deleteAccountAndPosts(account: Account, deletePostsAlso: Boolean = true) {
        viewModelScope.launch {
            when (account) {
                is Account.Internal -> {
                    // LogLeaf内部投稿の削除
                    if (deletePostsAlso) {
                        postDao.deletePostsByAccountId("LOGLEAF_INTERNAL_POST")
                    }
                    // Account.Internalは動的生成なので、アカウント削除処理は不要
                }
                is Account.Fitbit -> {
                    // 既存のFitbit処理
                    if (deletePostsAlso) {
                        postDao.deletePostsByAccountId(account.userId)
                    }
                    sessionManager.deleteAccount(account)
                }
                else -> {
                    // 既存の他SNS処理
                    if (deletePostsAlso) {
                        postDao.deletePostsByAccountId(account.userId)
                    }
                    sessionManager.deleteAccount(account)
                }
            }
        }
    }

    // オーバーロード：既存の呼び出しとの互換性を保つ
    fun deleteAccountAndPosts(account: Account) {
        deleteAccountAndPosts(account, deletePostsAlso = true)
    }

    /**
     * GitHubアカウントの期間設定を変更する
     */
    fun updateGitHubAccountPeriod(username: String, newPeriod: String) {
        sessionManager.updateGitHubAccountPeriod(username, newPeriod)
    }

    /**
     * Blueskyアカウントの期間設定を更新する
     */
    fun updateBlueskyAccountPeriod(handle: String, newPeriod: String) {
        sessionManager.updateBlueskyAccountPeriod(handle, newPeriod)
    }

    /**
     * Mastodonアカウントの期間設定を更新する
     */
    fun updateMastodonAccountPeriod(acct: String, newPeriod: String) {
        sessionManager.updateMastodonAccountPeriod(acct, newPeriod)
    }

    /**
     * GitHubアカウントのリポジトリ設定を更新
     */
    fun updateGitHubAccountRepositories(
        username: String,
        fetchMode: Account.RepositoryFetchMode,
        selectedRepos: List<String>
    ) {
        sessionManager.updateGitHubAccountRepositories(username, fetchMode, selectedRepos)
    }

    /**
     * GitHubのリポジトリ一覧を取得
     */
    suspend fun getGitHubRepositories(accessToken: String): List<GitHubRepository> {
        return gitHubApi.getUserRepositories(accessToken)
    }

    companion object {
        fun provideFactory(
            sessionManager: SessionManager,
            postDao: PostDao,
            gitHubApi: GitHubApi // ← 追加！
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AccountViewModel(sessionManager, postDao, gitHubApi) as T // ← gitHubApi追加
            }
        }
    }


}