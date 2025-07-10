package com.example.logleaf

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log  // これが重要！

class SessionManager(context: Context) {
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
                }
            }

            val jsonString = json.encodeToString(accounts)
            Log.d("SessionManager", "保存するJSON: $jsonString")

            prefs.edit()
                .putString(KEY_ACCOUNTS_JSON, jsonString)
                .apply()
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
}