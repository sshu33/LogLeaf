package com.example.logleaf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.logleaf.db.PostDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

data class LogUiState(
    val isLoading: Boolean = true,
    val posts: List<Post> = emptyList()
)

class LogViewViewModel(
    private val postDao: PostDao,
    private val sessionManager: SessionManager,
    private val date: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadPosts()
    }

    private fun loadPosts() {
        viewModelScope.launch {
            val visibleAccountIdsFlow = sessionManager.getVisibleAccountIds()
            val showHiddenFlow = sessionManager.getShowHiddenPosts()

            combine(visibleAccountIdsFlow, showHiddenFlow) { ids, showHidden ->
                Pair(ids, if (showHidden) 1 else 0)
            }.flatMapLatest { (visibleIds, includeHidden) ->
                postDao.getPostsForDate(date, visibleIds, includeHidden)
            }.collect { posts ->
                _uiState.value = LogUiState(isLoading = false, posts = posts)
            }
        }
    }

    companion object {
        fun provideFactory(
            postDao: PostDao,
            sessionManager: SessionManager,
            date: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LogViewViewModel(postDao, sessionManager, date) as T
            }
        }
    }
}