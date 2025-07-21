package com.example.logleaf.ui.screens // パッケージ名はあなたのものに合わせました

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.Post
import com.example.logleaf.SessionManager
import com.example.logleaf.db.PostDao
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(
    private val postDao: PostDao,
    private val sessionManager: SessionManager // ★ 変更点 2/5: SessionManagerをコンストラクタで受け取る
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedSns = MutableStateFlow<SnsType?>(null)
    val selectedSns = _selectedSns.asStateFlow()

    private val searchKeywords = searchQuery.map { query ->
        query.split(" ", "　").filter { it.isNotBlank() }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResultPosts: StateFlow<List<Post>> =
        combine(searchKeywords, selectedSns, sessionManager.accountsFlow) { keywords, sns, accounts ->
            Triple(keywords, sns, accounts)
        }
            .debounce(300L)
            .flatMapLatest { (keywords, sns, accounts) ->
                if (keywords.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val visibleAccountIds = accounts.filter { it.isVisible }.map { it.userId }
                    if (visibleAccountIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        postDao.searchPostsWithAnd(
                            keywords = keywords,
                            visibleAccountIds = visibleAccountIds,
                            includeHidden = 0 // 非表示の投稿は含めない
                        )
                    }
                }
            }
            .map { posts ->
                val sns = selectedSns.value
                if (sns != null) {
                    posts.filter { it.source == sns }
                } else {
                    posts
                }
            }
            // ▼▼▼ [変更点2] FlowをStateFlowに変換する、魔法の呪文を追加！ ▼▼▼
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList() // 初期値は、空のリスト
            )


    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSnsFilterChanged(snsType: SnsType?) {
        _selectedSns.value = snsType
    }

    fun onReset() {
        _searchQuery.value = ""
        _selectedSns.value = null
    }

    companion object {
        fun provideFactory(
            postDao: PostDao,
            sessionManager: SessionManager // ★ 変更点 5/5: FactoryもSessionManagerを受け取る
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(postDao, sessionManager) as T
            }
        }
    }
}