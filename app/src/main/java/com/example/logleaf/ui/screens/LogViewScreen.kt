package com.example.logleaf.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.logleaf.LogViewViewModel
import com.example.logleaf.Post
import com.example.logleaf.SessionManager
import com.example.logleaf.db.PostDao
import com.yourpackage.logleaf.ui.components.UserFontText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogViewScreen(
    dateString: String,
    targetPostId: String,
    postDao: PostDao,
    sessionManager: SessionManager,
    onDismiss: () -> Unit
) {
    val viewModel: LogViewViewModel = viewModel(
        key = dateString, // ★ 日付が変わったらViewModelを再生成するキー
        factory = LogViewViewModel.provideFactory(postDao, sessionManager, dateString)
    )
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.posts) {
        if (uiState.posts.isNotEmpty()) {
            val index = uiState.posts.indexOfFirst { it.id == targetPostId }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 88.dp, bottom = 16.dp)
            ) {
                items(uiState.posts, key = { it.id }) { post ->
                    LogViewPostCard(
                        post = post,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            LogViewHeader(
                dateString = dateString,
                onBackClick = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun LogViewHeader(dateString: String, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = LocalDate.parse(dateString)
    val formatter = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)
    val formattedDate = date.format(formatter)
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "戻る", tint = Color.White)
        }
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun LogViewPostCard(post: Post, modifier: Modifier = Modifier) {
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
    val timeString = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // カラーバーはまだ表示されない状態
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserFontText(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                UserFontText(text = post.text, style = MaterialTheme.typography.bodyLarge)
                if (post.imageUrl != null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            AsyncImage(
                                model = Uri.parse(post.imageUrl),
                                contentDescription = "投稿画像",
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}