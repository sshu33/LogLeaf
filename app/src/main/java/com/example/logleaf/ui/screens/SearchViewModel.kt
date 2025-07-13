package com.example.logleaf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.Post
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

class SearchViewModel(private val postDao: PostDao) : ViewModel() {

    // ユーザーが入力した検索キーワード
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // ユーザーが選択したSNSフィルター
    private val _selectedSns = MutableStateFlow<SnsType?>(null)
    val selectedSns = _selectedSns.asStateFlow()

    // 1. 検索クエリを、スペースで区切られた単語のリストに変換するFlow
    private val searchKeywords = searchQuery.map { query ->
        query.split(" ", "　").filter { it.isNotBlank() } // 半角・全角スペースで分割し、空の要素は除去
    }

    // 2. 検索結果の投稿リスト
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResultPosts: Flow<List<Post>> = searchKeywords
        .debounce(300L) // 入力が300ミリ秒止まったら検索実行
        .combine(selectedSns) { keywords, sns -> Pair(keywords, sns) } // 単語リストとSNSフィルターを組み合わせる
        .flatMapLatest { (keywords, sns) ->
            // 3. 単語リストが空なら、空のFlowを返す
            if (keywords.isEmpty()) {
                flowOf(emptyList())
            } else {
                // 4. postDaoに単語リストを渡して検索を実行
                postDao.searchPostsWithAnd(keywords)
            }
        }
        .map { posts ->
            // 5. SNSフィルターが設定されていれば、さらに絞り込む (この部分は変更なし)
            val sns = selectedSns.value
            if (sns != null) {
                posts.filter { it.source == sns }
            } else {
                posts
            }
        }

    /**
     * 検索キーワードが変更されたときにUIから呼び出す
     */
    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /**
     * SNSフィルターが変更されたときにUIから呼び出す
     */
    fun onSnsFilterChanged(snsType: SnsType?) {
        _selectedSns.value = snsType
    }

    /**
     * リセットボタンが押されたときにUIから呼び出す
     */
    fun onReset() {
        _searchQuery.value = ""
        _selectedSns.value = null
    }

    companion object {
        fun provideFactory(
            postDao: PostDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(postDao) as T
            }
        }
    }
}