package com.example.logleaf

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("LogLeaf_Prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
    }

    // ★ アカウントリストの状態をリアルタイムで保持・通知するStateFlow
    private val _accountsFlow = MutableStateFlow<List<Account>>(emptyList())
    val accountsFlow = _accountsFlow.asStateFlow()

    // ★ SessionManagerが生成された時に、一度だけSharedPreferencesから読み込む
    init {
        _accountsFlow.value = loadAccountsFromPrefs()
    }

    // getAccounts()は、現在の最新のスナップショットを返す
    fun getAccounts(): List<Account> = _accountsFlow.value

    fun saveAccount(newAccount: Account) {
        val currentAccounts = getAccounts().toMutableList()
        // ★★★ セッション切れ状態を引き継ぐための修正 ★★★
        // 新しいアカウント情報で上書きする際、古いアカウントの'needsReauthentication'状態を保持する
        val existingAccount = currentAccounts.find { it.userId == newAccount.userId && it.snsType == newAccount.snsType }
        val accountToSave = if (existingAccount != null && newAccount is Account.Mastodon) {
            newAccount.copy(needsReauthentication = false) // 再認証成功時はフラグをリセット
        } else if (existingAccount != null && newAccount is Account.Bluesky) {
            newAccount.copy(needsReauthentication = false) // Blueskyも同様
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
        val currentAccounts = getAccounts().toMutableList()
        currentAccounts.removeAll { it.userId == accountToDelete.userId && it.snsType == accountToDelete.snsType }
        saveAccountsList(currentAccounts)
        // StateFlowに新しいリストを通知する
        _accountsFlow.value = currentAccounts
        // ★★★ デバッグログを追加 ★★★
        println("【SessionManager】アカウント削除。残りのアカウント数: ${currentAccounts.size}")
    }

    fun markAccountForReauthentication(accountId: String) {
        updateAccountState(accountId) { account ->
            when (account) {
                is Account.Bluesky -> account.copy(needsReauthentication = true)
                is Account.Mastodon -> account.copy(needsReauthentication = true)
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
        val jsonString = Json.encodeToString(accounts)
        prefs.edit().putString(KEY_ACCOUNTS_JSON, jsonString).apply()
    }

    private fun loadAccountsFromPrefs(): List<Account> {
        val jsonString = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return try {
            Json.decodeFromString<List<Account>>(jsonString)
        } catch (e: Exception) {
            println("アカウント情報のデコードに失敗しました: ${e.message}")
            // 不正なデータはクリアする
            prefs.edit().remove(KEY_ACCOUNTS_JSON).apply()
            emptyList()
        }
    }
}