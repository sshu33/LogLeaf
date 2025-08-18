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
        Log.d("SessionManager", "saveAccount呼び出し:")
        Log.d("SessionManager", "アカウントタイプ: ${newAccount.snsType}")
        Log.d("SessionManager", "ユーザーID: ${newAccount.userId}")

        val currentAccounts = getAccounts().toMutableList()
        // ★★★ セッション切れ状態を引き継ぐための修正 ★★★
        // 新しいアカウント情報で上書きする際、古いアカウントの'needsReauthentication'状態を保持する
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
        val stackTrace = Thread.currentThread().stackTrace.joinToString(separator = "\n") { "  at $it" }
        Log.e("ACCOUNT_DELETE_INVESTIGATION", "deleteAccountが呼び出されました！")
        Log.e("ACCOUNT_DELETE_INVESTIGATION", "--- 削除対象アカウント情報 ---")
        Log.e("ACCOUNT_DELETE_INVESTIGATION", accountToDelete.toString())
        Log.e("ACCOUNT_DELETE_INVESTIGATION", "--- 呼び出し元の履歴（スタックトレース） ---")
        Log.e("ACCOUNT_DELETE_INVESTIGATION", stackTrace)
        Log.e("ACCOUNT_DELETE_INVESTIGATION", "------------------------------------")

        when (accountToDelete) {
            is Account.Fitbit -> {  // ← 追加
                // Fitbit連携解除処理（新規追加）
                handleFitbitDisconnection(accountToDelete)
            }
            else -> {
                // 従来のSNSアカウント削除処理
                val currentAccounts = getAccounts().toMutableList()
                currentAccounts.removeAll { it.userId == accountToDelete.userId && it.snsType == accountToDelete.snsType }
                saveAccountsList(currentAccounts)
                _accountsFlow.value = currentAccounts
                println("【SessionManager】アカウント削除。残りのアカウント数: ${currentAccounts.size}")
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
            }
        }
    }

    fun markAccountForReauthentication(accountId: String) {
        updateAccountState(accountId) { account ->
            when (account) {
                is Account.Bluesky -> account.copy(needsReauthentication = true)
                is Account.Mastodon -> account.copy(needsReauthentication = true)
                is Account.GitHub -> account.copy(needsReauthentication = true)
                is Account.Fitbit -> account.copy(needsReauthentication = true)  // ← この行を追加
            }
        }
        println("【SessionManager】アカウント($accountId)を要再認証としてマークしました。")
    }

    private fun updateAccountState(accountId: String, updateAction: (Account) -> Account) {
        val currentAccounts = getAccounts().toMutableList()
        val accountIndex = currentAccounts.indexOfFirst { it.userId == accountId }

        if (accountIndex != -1) {
            val oldAccount = currentAccounts[accountIndex]
            val newAccount = updateAction(oldAccount)
            currentAccounts[accountIndex] = newAccount
            saveAccountsList(currentAccounts)
            _accountsFlow.value = currentAccounts
        }
    }

    private fun saveAccountsList(accounts: List<Account>) {
        try {
            accounts.forEach { account ->
                when (account) {
                    is Account.Mastodon -> {
                        Log.d("SessionManager", "Mastodonアカウント詳細:")
                        Log.d("SessionManager", "インスタンスURL: ${account.instanceUrl}")
                        Log.d("SessionManager", "ID: ${account.id}")
                        Log.d("SessionManager", "アクセストークン長さ: ${account.accessToken.length}")
                        Log.d("SessionManager", "ClientID: ${account.clientId}")
                        Log.d("SessionManager", "ClientSecret: ${account.clientSecret}")
                    }
                    is Account.Bluesky -> {
                        Log.d("SessionManager", "Blueskyアカウント詳細:")
                        Log.d("SessionManager", "DID: ${account.did}")
                        Log.d("SessionManager", "ハンドル: ${account.handle}")
                        Log.d("SessionManager", "アクセストークン長さ: ${account.accessToken.length}")
                    }
                    is Account.GitHub -> {
                        Log.d("SessionManager", "GitHubアカウント詳細:")
                        Log.d("SessionManager", "ユーザー名: ${account.username}")
                        Log.d("SessionManager", "アクセストークン長さ: ${account.accessToken.length}")
                    }
                    is Account.Fitbit -> {  // ← この部分を追加
                        Log.d("SessionManager", "Fitbitアカウント詳細:")
                        Log.d("SessionManager", "ユーザーID: ${account.fitbitUserId}")
                        Log.d("SessionManager", "アクセストークン長さ: ${account.accessToken.length}")
                    }
                }
            }

            val jsonString = json.encodeToString(accounts)
            Log.d("SessionManager", "保存するJSON: $jsonString")

            val success = prefs.edit()
                .putString(KEY_ACCOUNTS_JSON, jsonString)
                .commit() // .apply() から .commit() に変更

            // 書き込みが成功したかどうかの結果をログに出力
            if (success) {
                Log.d("SessionManager", "SharedPreferencesへの保存に成功しました (commit)")
            } else {
                Log.e("SessionManager", "SharedPreferencesへの保存に失敗しました (commit)")
            }

        } catch (e: Exception) {
            Log.e("SessionManager", "アカウント保存中にエラー: ${e.message}", e)
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
        Log.d("SessionManager", "updateBlueskyAccountPeriod呼び出し: handle=$handle, newPeriod=$newPeriod")

        val account = getAccounts().find { it is Account.Bluesky && it.handle == handle } as? Account.Bluesky
        if (account != null) {
            updateAccountState(account.did) { acc ->
                Log.d("SessionManager", "アカウント発見: ${acc.displayName}")
                when (acc) {
                    is Account.Bluesky -> {
                        Log.d("SessionManager", "Blueskyアカウント更新中...")
                        acc.copy(
                            period = newPeriod,
                            lastSyncedAt = null,
                            lastPeriodSetting = acc.period // 現在の期間を前回期間として保存
                        )
                    }
                    else -> acc
                }
            }
        } else {
            Log.e("SessionManager", "handleに対応するBlueskyアカウントが見つかりません: $handle")
        }

        Log.d("SessionManager", "Blueskyアカウント($handle)の期間を${newPeriod}に変更し、同期時刻をリセットしました。")
    }

    /**
     * Mastodonアカウントの期間設定を更新する
     */
    fun updateMastodonAccountPeriod(acct: String, newPeriod: String) {
        Log.d("SessionManager", "updateMastodonAccountPeriod呼び出し: acct=$acct, newPeriod=$newPeriod")

        val account = getAccounts().find {
            it is Account.Mastodon && it.acct == acct
        } as? Account.Mastodon

        if (account != null) {
            updateAccountState(account.userId) { acc ->
                Log.d("SessionManager", "アカウント発見: ${acc.displayName}")
                when (acc) {
                    is Account.Mastodon -> {
                        Log.d("SessionManager", "Mastodonアカウント更新中...")
                        acc.copy(
                            period = newPeriod,
                            lastSyncedAt = null,
                            lastPeriodSetting = acc.period
                        )
                    }
                    else -> acc
                }
            }
        } else {
            Log.e("SessionManager", "acctに対応するMastodonアカウントが見つかりません: $acct")
        }

        Log.d("SessionManager", "Mastodonアカウント($acct)の期間を${newPeriod}に変更し、同期時刻をリセットしました。")
    }

    fun updateMastodonAccountLastPeriod(acct: String, currentPeriod: String) {
        val account = getAccounts().find {
            it is Account.Mastodon && it.acct == acct
        } as? Account.Mastodon

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
        Log.d("SessionManager", "updateGitHubAccountPeriod呼び出し: username=$username, newPeriod=$newPeriod")

        val account = getAccounts().find {
            it is Account.GitHub && it.username == username
        } as? Account.GitHub

        if (account != null) {
            updateAccountState(account.userId) { acc ->
                Log.d("SessionManager", "アカウント発見: ${acc.displayName}")
                when (acc) {
                    is Account.GitHub -> {
                        Log.d("SessionManager", "GitHubアカウント更新中...")
                        acc.copy(
                            period = newPeriod,
                            lastSyncedAt = null,
                            lastPeriodSetting = acc.period
                        )
                    }
                    else -> acc
                }
            }
        } else {
            Log.e("SessionManager", "usernameに対応するGitHubアカウントが見つかりません: $username")
        }

        Log.d("SessionManager", "GitHubアカウント($username)の期間を${newPeriod}に変更し、同期時刻をリセットしました。")
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
        Log.d("SessionManager", "アカウント($userId)の最終同期時刻を更新: $syncTimeString")
    }

    /**
     * デバッグ用：全アカウントのlastSyncedAtを確認
     */
    fun debugLastSyncedAt() {
        Log.d("SessionManager", "=== lastSyncedAt デバッグ情報 ===")
        getAccounts().forEach { account ->
            Log.d("SessionManager", "${account.snsType} (${account.displayName}): lastSyncedAt=${account.lastSyncedAt}")
        }
        Log.d("SessionManager", "=== デバッグ情報 終了 ===")
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
}