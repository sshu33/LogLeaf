package com.example.logleaf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.Account
import com.example.logleaf.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountViewModel(private val sessionManager: SessionManager) : ViewModel() {

    // (ViewModelの中身は変更なし)
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        viewModelScope.launch {
            _accounts.value = sessionManager.getAccounts()
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            sessionManager.deleteAccount(account)
            loadAccounts()
        }
    }

    // ★★★ このViewModelを生成するための専用ファクトリを追加 ★★★
    companion object {
        fun provideFactory(
            sessionManager: SessionManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AccountViewModel(sessionManager) as T
            }
        }
    }
}