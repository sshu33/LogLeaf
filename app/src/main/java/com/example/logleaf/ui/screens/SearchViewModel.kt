package com.example.logleaf.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.Post
import com.example.logleaf.db.PostDao
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

// Hilt/Daggerを使っている場合は @HiltViewModel と @Inject を忘れずに
// class SearchViewModel @Inject constructor(
//     private val postDao: PostDao
// ) : ViewModel() {

// Hilt/Daggerをまだ導入していない場合は、こちらのコンストラクタを使います
class SearchViewModel(private val postDao: PostDao) : ViewModel() {

    // ユーザーが入力した検索キーワード
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // ユーザーが選択したSNSフィルター
    // nullの場合は「全て」を意味する
    private val _selectedSns = MutableStateFlow<SnsType?>(null)
    val selectedSns = _selectedSns.asStateFlow()

    // 検索結果の投稿リスト
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResultPosts = searchQuery
        .debounce(300L) // 入力が300ミリ秒止まったら検索実行
        .combine(selectedSns) { query, sns -> Pair(query, sns) } // 検索語とSNSフィルターを組み合わせる
        .flatMapLatest { (query, sns) ->
            // DAOを呼び出す。検索語が空なら空リストを返す
            if (query.isBlank()) {
                MutableStateFlow(emptyList())
            } else {
                postDao.searchPosts("%$query%") // クエリの前後に%を付けてLIKE検索
            }
        }
        .map { posts ->
            // SNSフィルターが設定されていれば、さらに絞り込む
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