package com.example.logleaf.ui.screens

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.logleaf.Post
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogViewScreen(
    posts: List<Post>,
    targetPostId: String,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    // スクロール可能かどうかを判定するフラグ
    val isScrollEnabled = offsetY.value == 0f

    LaunchedEffect(posts) {
        if (posts.isNotEmpty()) {
            val index = posts.indexOfFirst { it.id == targetPostId }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    // 画面の高さを取得するためにBoxWithConstraintsを使用
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight.toFloat()
        val dismissThreshold = screenHeight / 3 // 画面の1/3以上スワイプしたら閉じる

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                // 閾値を超えていたら、下までアニメーションさせて閉じる
                                if (offsetY.value > dismissThreshold) {
                                    offsetY.animateTo(
                                        targetValue = screenHeight,
                                        animationSpec = tween(durationMillis = 300)
                                    )
                                    onDismiss()
                                } else {
                                    // 閾値未満なら、元の位置にバネのように戻る
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring()
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                // キャンセル時も元の位置に戻る
                                offsetY.animateTo(0f, animationSpec = spring())
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val isScrolledToTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            val newOffsetY = (offsetY.value + dragAmount).coerceAtLeast(0f)

                            // 条件に応じてドラッグイベントを消費
                            when {
                                // ダイアログが既にずれている場合 (上下どちらにも追従)
                                offsetY.value > 0f -> {
                                    change.consume()
                                    coroutineScope.launch { offsetY.snapTo(newOffsetY) }
                                }
                                // リストが一番上で、下にスワイプした場合
                                isScrolledToTop && dragAmount > 0 -> {
                                    change.consume()
                                    coroutineScope.launch { offsetY.snapTo(newOffsetY) }
                                }
                            }
                        }
                    )
                }
        ) {
            // (1) 背景のベール（タップで閉じる機能）
            Surface(
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            ) {}

            // (2) コンテンツ全体（このBoxがスワイプで動く）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = 0, y = offsetY.value.roundToInt()) }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    // ★★★ ダイアログがずれている間はリストのスクロールを無効化 ★★★
                    userScrollEnabled = isScrollEnabled
                ) {
                    items(posts, key = { it.id }) { post ->
                        LogViewPostCard(
                            post = post,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                if (posts.isNotEmpty()) {
                    val dateString = posts.first().createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString()
                    IndexTab(
                        dateString = dateString,
                        onClick = onDismiss, // タブのクリックでも閉じる
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 24.dp, end = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IndexTab(dateString: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = LocalDate.parse(dateString)
    val fullText = date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH))
    val density = LocalDensity.current

    // SubcomposeLayoutを使い、レイアウトを2段階で構築します
    SubcomposeLayout(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
    ) { constraints ->
        // ステップ1: まず、テキストを描画せずにサイズだけを「前もって測定」します
        val textMeasurable = subcompose("text") {
            Text(
                text = fullText,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1,
                fontSize = with(density) { 22.dp.toSp() }
            )
        }.first().measure(Constraints())

        // ステップ2: 測定したテキストのサイズを基に、タブ全体のサイズを計算します
        val tabWidth = 48.dp.roundToPx()
        val tabHeight = textMeasurable.width + 32.dp.roundToPx() // テキストの幅 + 余白

        // ステップ3: 最終的なレイアウトを確定させます
        layout(tabWidth, tabHeight) {
            // ステップ4: 実際にテキストを描画し、回転させて中央に配置します
            val textPlaceable = subcompose("draw") {
                Box(
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        rotationZ = 90f
                    }
                ) {
                    Text(
                        text = fullText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1,
                        fontSize = with(density) { 22.dp.toSp() }
                    )
                }
            }.first().measure(constraints)

            // 中央に配置
            textPlaceable.place(
                (tabWidth - textPlaceable.width) / 2,
                (tabHeight - textPlaceable.height) / 2
            )
        }
    }
}

@Composable
fun LogViewPostCard(post: Post, modifier: Modifier = Modifier) {
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
    val timeString = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ★ material3 の Typography と ColorScheme を使う
                UserFontText(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                UserFontText(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge
                )
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