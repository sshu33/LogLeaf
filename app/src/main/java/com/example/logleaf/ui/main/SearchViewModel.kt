package com.example.logleaf.ui.main // パッケージ名はあなたのものに合わせました

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.data.model.Post
import com.example.logleaf.data.session.SessionManager
import com.example.logleaf.db.PostDao
import com.example.logleaf.ui.entry.Tag
import com.example.logleaf.ui.search.SearchMode
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
    private val sessionManager: SessionManager
) : ViewModel() {

    // --- UIの状態 ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedSns = MutableStateFlow(SnsType.entries.toSet())
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
            flowOf(sessionManager.getAccounts()),  // ← ここを変更
            _searchMode
        ) { query, selectedSnsSet, accounts, mode ->
            val keywords = query.split(" ", "　").filter { it.isNotBlank() }
            Quadruple(keywords, selectedSnsSet, accounts, mode) // ◀ Quadrupleに渡す
        }
            .debounce(300L)
            .flatMapLatest { (keywords, selectedSnsSet, accounts, mode) ->
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
                val selectedSnsValue = selectedSns.value
                // 選択されたSNSが空でなければ（=何かで絞り込まれていれば）フィルタリング
                if (selectedSnsValue.isNotEmpty()) {
                    posts.filter { it.source in selectedSnsValue }
                } else {
                    emptyList()
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

    fun onSnsFilterChanged(snsType: SnsType) {
        val currentSelection = _selectedSns.value.toMutableSet()
        val isCurrentlyAllSelected = currentSelection.size == SnsType.entries.size

        // ★★★ 仕様の核となるロジック ★★★
        if (isCurrentlyAllSelected) {
            // 現在が「全選択」の場合、タップされたSNS一つだけを選択状態にする
            _selectedSns.value = setOf(snsType)
        } else {
            // それ以外の場合は、通常のトグル（追加/削除）を行う
            if (currentSelection.contains(snsType)) {
                currentSelection.remove(snsType)
            } else {
                currentSelection.add(snsType)
            }
            _selectedSns.value = currentSelection
        }
    }

    fun onAllSnsToggled() {
        val isCurrentlyAllSelected = _selectedSns.value.size == SnsType.entries.size

        // 全選択状態であれば空に、そうでなければ全選択にする（ON/OFFトグル）
        if (isCurrentlyAllSelected) {
            _selectedSns.value = emptySet()
        } else {
            _selectedSns.value = SnsType.entries.toSet()
        }
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
        _selectedSns.value = SnsType.entries.toSet() // ◀ 全選択状態に戻す
        _searchMode.value = SearchMode.ALL
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