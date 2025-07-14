package com.example.logleaf.ui.screens // パッケージ名はあなたのものに合わせました

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.logleaf.Post
import com.example.logleaf.SessionManager
import com.example.logleaf.db.PostDao
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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
    val searchResultPosts: Flow<List<Post>> =
        // ★ 変更点 3/5: 検索キーワード、SNSフィルター、そしてアカウント情報の3つを組み合わせる
        combine(searchKeywords, selectedSns, sessionManager.accountsFlow) { keywords, sns, accounts ->
            Triple(keywords, sns, accounts) // 3つの値をセットで下流に渡す
        }
            .debounce(300L)
            .flatMapLatest { (keywords, sns, accounts) -> // 3つの値を受け取る
                if (keywords.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // ★ 変更点 4/5: 表示がONになっているアカウントのIDリストを生成する
                    val visibleAccountIds = accounts.filter { it.isVisible }.map { it.userId }

                    // 表示対象アカウントがなければ、空のリストを返す
                    if (visibleAccountIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        // ★ 修正点: postDaoに単語リストと「表示OKリスト」の両方を渡して検索
                        postDao.searchPostsWithAnd(keywords, visibleAccountIds)
                    }
                }
            }
            .map { posts ->
                // SNSフィルターのロジックはあなたのものを完全に維持します
                val sns = selectedSns.value
                if (sns != null) {
                    posts.filter { it.source == sns }
                } else {
                    posts
                }
            }

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