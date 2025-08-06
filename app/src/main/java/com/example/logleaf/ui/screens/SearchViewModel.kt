package com.example.logleaf.ui.screens // パッケージ名はあなたのものに合わせました

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.Post
import com.example.logleaf.SessionManager
import com.example.logleaf.db.PostDao
import com.example.logleaf.ui.entry.Tag
import com.example.logleaf.ui.search.SearchMode
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(
    private val postDao: PostDao,
    private val sessionManager: SessionManager
) : ViewModel() {

    init {
        Log.d("VM_LIFECYCLE", "SearchViewModel instance created: ${this.hashCode()}")
    }

    // --- UIの状態 ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedSns = MutableStateFlow<SnsType?>(null)
    val selectedSns = _selectedSns.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.ALL)
    val searchMode = _searchMode.asStateFlow()

    private val searchKeywords = searchQuery.map { query ->
        query.split(" ", "　").filter { it.isNotBlank() }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResultPosts: StateFlow<List<Post>> =
        combine(
            _searchQuery,
            _selectedSns,
            sessionManager.accountsFlow,
            _searchMode // ◀◀◀ 正しい「_searchMode」に修正
        ) { query, sns, accounts, mode -> // ◀ ラムダの引数名も「mode」に修正
            val keywords = query.split(" ", "　").filter { it.isNotBlank() }
            Quadruple(keywords, sns, accounts, mode) // ◀ Quadrupleに渡す変数も「mode」に修正
        }
            .debounce(300L)
            .flatMapLatest { (keywords, sns, accounts, mode) ->
                // アカウントIDの準備は先に行う
                val allAccountIds = accounts.map { it.userId }
                if (allAccountIds.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList())
                }

                // when式で、モードごとに必要な処理を行う
                when (mode) {
                    // タグ検索
                    SearchMode.TAG_ONLY -> {
                        if (keywords.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            val tagName = keywords.first().removePrefix("#")
                            val tagNamePattern = "%$tagName%"
                            postDao.searchPostsByTag(tagNamePattern, allAccountIds)
                        }
                    }
                    // 本文のみ検索
                    SearchMode.TEXT_ONLY -> {
                        if (keywords.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            postDao.searchPostsByTextOnly(
                                keywords = keywords,
                                visibleAccountIds = allAccountIds,
                                includeHidden = 0
                            )
                        }
                    }
                    // 全文検索
                    SearchMode.ALL -> {
                        if (keywords.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            postDao.searchPostsWithAnd(
                                keywords = keywords,
                                visibleAccountIds = allAccountIds,
                                includeHidden = 0
                            )
                        }
                    }
                }
            }
            .map { posts ->
                val snsValue = selectedSns.value
                if (snsValue != null) {
                    posts.filter { it.source == snsValue }
                } else {
                    posts
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * 検索結果の投稿からタグ情報を取得する（タグ検索時のみ使用）
     */
    suspend fun getTagsForPost(postId: String): List<Tag> {
        return postDao.getTagsForPost(postId)
    }


    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSnsFilterChanged(snsType: SnsType?) {
        _selectedSns.value = snsType
    }

    fun searchByTag(tagName: String) {
        _searchMode.value = SearchMode.TAG_ONLY // ◀◀◀ 1. 修正
        _searchQuery.value = tagName
        Log.d("TagSearch", "タグ検索実行: tagName=${tagName}")
    }

    fun onSearchModeChanged(mode: SearchMode) {
        _searchMode.value = mode
    }

    fun onReset() {
        _searchQuery.value = ""
        _selectedSns.value = null
        _searchMode.value = SearchMode.ALL // ◀ isTagOnlySearch から変更
    }


    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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