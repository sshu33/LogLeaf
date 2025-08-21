package com.example.logleaf.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.logleaf.data.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


class SessionManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("LogLeaf_Prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true  // 追加
        isLenient = true  // 追加（柔軟なパース）
    }

    // ★ アカウントリストの状態をリアルタイムで保持・通知するStateFlow
    private val _accountsFlow = MutableStateFlow<List<Account>>(emptyList())
    val accountsFlow = _accountsFlow.asStateFlow()

    // ★ SessionManagerが生成された時に、一度だけSharedPreferencesから読み込む
    init {
        // この部分に追加
        Log.d("SessionManager", "【初期化】SessionManagerが生成されました")
        Log.d("SessionManager", "【初期化】読み込んだアカウント数: ${loadAccountsFromPrefs().size}")

        _accountsFlow.value = loadAccountsFromPrefs()
    }

    // getAccounts()は、現在の最新のスナップショットを返す
    fun getAccounts(): List<Account> = _accountsFlow.value

    fun saveAccount(newAccount: Account) {
        val currentAccounts = getAccounts().toMutableList()
        val existingAccount = currentAccounts.find { it.userId == newAccount.userId && it.snsType == newAccount.snsType }
        val accountToSave = if (existingAccount != null && newAccount is Account.Mastodon) {
            newAccount.copy(needsReauthentication = false)
        } else if (existingAccount != null && newAccount is Account.Bluesky) {
            newAccount.copy(needsReauthentication = false)
        } else if (existingAccount != null && newAccount is Account.GitHub) {  // ← 追加
            newAccount.copy(needsReauthentication = false)
        } else {
            newAccount
        }


        val existingAccountIndex = currentAccounts.indexOfFirst { it.userId == accountToSave.userId && it.snsType == accountToSave.snsType }
        if (existingAccountIndex != -1) {
            currentAccounts[existingAccountIndex] = accountToSave
        } else {
            currentAccounts.add(accountToSave)
        }

        saveAccountsList(currentAccounts)
        _accountsFlow.value = currentAccounts
        println("【SessionManager】アカウント保存。現在のアカウント数: ${currentAccounts.size}")
    }

    fun deleteAccount(accountToDelete: Account) {
        when (accountToDelete) {
            is Account.Fitbit -> {
                handleFitbitDisconnection(accountToDelete)
            }
            else -> {
                val currentAccounts = getAccounts().toMutableList()
                currentAccounts.removeAll { it.userId == accountToDelete.userId && it.snsType == accountToDelete.snsType }
                saveAccountsList(currentAccounts)
                _accountsFlow.value = currentAccounts
            }
        }
    }

    // Fitbit連携解除の専用処理（新規追加）
    private fun handleFitbitDisconnection(fitbitAccount: Account.Fitbit) {
        // FitbitHistoryManagerの履歴もクリア
        val historyManager = FitbitHistoryManager(context)
        historyManager.clearAllData(fitbitAccount.userId)

        // 既存の処理...
        val currentAccounts = getAccounts().toMutableList()
        currentAccounts.removeAll { it is Account.Fitbit }
        saveAccountsList(currentAccounts)
        _accountsFlow.value = currentAccounts

        Log.d("SessionManager", "Fitbit連携解除完了")
    }

    fun toggleAccountVisibility(accountId: String) {
        updateAccountState(accountId) { account ->
            when (account) {
                is Account.Bluesky -> account.copy(isVisible = !account.isVisible)
                is Account.Mastodon -> account.copy(isVisible = !account.isVisible)
                is Account.GitHub -> account.copy(isVisible = !account.isVisible)
                is Account.Fitbit -> account.copy(isVisible = !account.isVisible)
                is Account.Internal -> account.copy(isVisible = !account.isVisible)
            }
        }
    }

    fun markAccountForReauthentication(accountId: String) {
        updateAccountState(accountId) { account ->
            when (account) {
                is Account.Bluesky -> account.copy(needsReauthentication = true)
                is Account.Mastodon -> account.copy(needsReauthentication = true)
                is Account.GitHub -> account.copy(needsReauthentication = true)
                is Account.Fitbit -> account.copy(needsReauthentication = true)
                is Account.Internal -> account.copy(needsReauthentication = true) // ← この行を追加
            }
        }
        println("【SessionManager】アカウント($accountId)を要再認証としてマークしました。")
    }

    fun updateAccountState(accountId: String, updateAction: (Account) -> Account) {
        Log.d("SessionManager", "=== updateAccountState開始: accountId=$accountId ===")
        val currentAccounts = getAccounts().toMutableList()
        val accountIndex = currentAccounts.indexOfFirst { it.userId == accountId }

        if (accountIndex != -1) {
            val oldAccount = currentAccounts[accountIndex]
            val newAccount = updateAction(oldAccount)
            currentAccounts[accountIndex] = newAccount
            saveAccountsList(currentAccounts)
            _accountsFlow.value = currentAccounts
        }
        Log.d("SessionManager", "=== updateAccountState完了: accountId=$accountId ===")
    }

    private fun saveAccountsList(accounts: List<Account>) {
        try {
            val jsonString = json.encodeToString(accounts)

            val success = prefs.edit()
                .putString(KEY_ACCOUNTS_JSON, jsonString)
                .commit()

            if (!success) {
                Log.e("SessionManager", "アカウント保存失敗")
            }

        } catch (e: Exception) {
            Log.e("SessionManager", "アカウント保存エラー: ${e.message}", e)
        }
    }

    private fun loadAccountsFromPrefs(): List<Account> {
        val jsonString = prefs.getString(KEY_ACCOUNTS_JSON, null)

        Log.d("SessionManager", "読み込んだJSON文字列: $jsonString")

        return try {
            if (jsonString != null) {
                val accounts = json.decodeFromString<List<Account>>(jsonString)
                Log.d("SessionManager", "読み込んだアカウント数: ${accounts.size}")
                accounts
            } else {
                Log.d("SessionManager", "保存されたJSONがnull")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SessionManager", "アカウント読み込み中にエラー: ${e.message}", e)
            prefs.edit().remove(KEY_ACCOUNTS_JSON).apply()
            emptyList()
        }
    }

    /**
     * Blueskyアカウントの期間設定を更新する
     */
    fun updateBlueskyAccountPeriod(handle: String, newPeriod: String) {
        val account = getAccounts().find { it is Account.Bluesky && it.handle == handle } as? Account.Bluesky
        if (account != null) {
            updateAccountState(account.did) { acc ->
                when (acc) {
                    is Account.Bluesky -> acc.copy(
                        period = newPeriod,
                        lastSyncedAt = null,
                        lastPeriodSetting = acc.period
                    )
                    else -> acc
                }
            }
        } else {
            Log.e("SessionManager", "Blueskyアカウントが見つかりません: $handle")
        }
    }

    /**
     * Mastodonアカウントの期間設定を更新する
     */
    fun updateMastodonAccountPeriod(acct: String, newPeriod: String) {
        val account = getAccounts().find { it is Account.Mastodon && it.acct == acct } as? Account.Mastodon
        if (account != null) {
            updateAccountState(account.userId) { acc ->
                when (acc) {
                    is Account.Mastodon -> acc.copy(
                        period = newPeriod,
                        lastSyncedAt = null,
                        lastPeriodSetting = acc.period
                    )
                    else -> acc
                }
            }
        } else {
            Log.e("SessionManager", "Mastodonアカウントが見つかりません: $acct")
        }
    }

    fun updateMastodonAccountLastPeriod(acct: String, currentPeriod: String) {
        val account = getAccounts().find { it is Account.Mastodon && it.acct == acct } as? Account.Mastodon
        if (account != null) {
            updateAccountState(account.userId) { acc ->
                when (acc) {
                    is Account.Mastodon -> acc.copy(lastPeriodSetting = currentPeriod)
                    else -> acc
                }
            }
        }
    }

    /**
     * GitHubアカウントの期間設定を更新する
     */
    fun updateGitHubAccountPeriod(username: String, newPeriod: String) {
        val account = getAccounts().find { it is Account.GitHub && it.username == username } as? Account.GitHub
        if (account != null) {
            updateAccountState(account.userId) { acc ->
                when (acc) {
                    is Account.GitHub -> acc.copy(
                        period = newPeriod,
                        lastSyncedAt = null,
                        lastPeriodSetting = acc.period
                    )
                    else -> acc
                }
            }
        } else {
            Log.e("SessionManager", "GitHubアカウントが見つかりません: $username")
        }
    }

    fun updateGitHubAccountLastPeriod(username: String, currentPeriod: String) {
        val account = getAccounts().find {
            it is Account.GitHub && it.username == username
        } as? Account.GitHub

        if (account != null) {
            updateAccountState(account.userId) { acc ->
                when (acc) {
                    is Account.GitHub -> acc.copy(lastPeriodSetting = currentPeriod)
                    else -> acc
                }
            }
        }
    }

    /**
     * GitHubアカウントのリポジトリ設定を更新する
     */
    fun updateGitHubAccountRepositories(
        username: String,
        fetchMode: Account.RepositoryFetchMode,
        selectedRepos: List<String>
    ) {
        updateAccountState(username) { account ->
            when (account) {
                is Account.GitHub -> account.copy(
                    repositoryFetchMode = fetchMode,
                    selectedRepositories = selectedRepos
                )
                else -> account
            }
        }
        Log.d("SessionManager", "GitHubアカウント($username)のリポジトリ設定を更新: $fetchMode, 選択数: ${selectedRepos.size}")
    }

    /**
     * アカウントの最終同期時刻を更新する
     */
    fun updateLastSyncedAt(userId: String, syncTime: ZonedDateTime) {
        val syncTimeString = syncTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        updateAccountState(userId) { account ->
            when (account) {
                is Account.GitHub -> account.copy(lastSyncedAt = syncTimeString)
                is Account.Bluesky -> account.copy(lastSyncedAt = syncTimeString)
                is Account.Mastodon -> account.copy(lastSyncedAt = syncTimeString)
                else -> account
            }
        }
    }

    fun updateBlueskyAccountLastPeriod(handle: String, currentPeriod: String) {
        val account = getAccounts().find { it is Account.Bluesky && it.handle == handle } as? Account.Bluesky
        if (account != null) {
            updateAccountState(account.did) { acc ->
                when (acc) {
                    is Account.Bluesky -> acc.copy(lastPeriodSetting = currentPeriod)
                    else -> acc
                }
            }
        }
    }

    // LogLeaf アカウントの表示状態管理
    private val _isLogLeafVisible = MutableStateFlow(true)
    val isLogLeafVisible = _isLogLeafVisible.asStateFlow()

    fun toggleLogLeafVisibility() {
        val newValue = !_isLogLeafVisible.value
        _isLogLeafVisible.value = newValue
        // SharedPreferencesに保存
        prefs.edit().putBoolean("logleaf_visible", newValue).apply()
    }

    // 初期化時に読み込み
    init {
        // 既存のコードの後に追加
        _accountsFlow.value = loadAccountsFromPrefs()
        _isLogLeafVisible.value = prefs.getBoolean("logleaf_visible", true)
    }
}