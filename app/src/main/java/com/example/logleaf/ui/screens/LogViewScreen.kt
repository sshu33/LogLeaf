package com.example.logleaf.ui.screens

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.logleaf.Post
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogViewScreen(
    posts: List<Post>,
    targetPostId: String,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    // 最初に表示すべき投稿までスクロールする処理
    LaunchedEffect(posts, targetPostId) {
        if (posts.isNotEmpty()) {
            val index = posts.indexOfFirst { it.id == targetPostId }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    // ★★★ 問題を解決する、シンプルで正しいレイアウト構造 ★★★
    Box(
        modifier = Modifier
            .fillMaxSize()
            // (1) このBoxが画面全体のタップを監視します。
            // ただし、子要素がイベントを消費した場合は、呼ばれません。
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDismiss() }
                )
            }
    ) {
        // 背景の半透明ベール（視覚的なものなので、クリックは受け取りません）
        Surface(
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxSize()
        ) {}

        // (2) 投稿リスト
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 220.dp, bottom = 16.dp)
        ) {

            items(posts, key = { it.id }) { post ->
                // LogViewPostCardを呼び出します（次のステップで修正）
                LogViewPostCard(
                    post = post,
                    isTargetPost = (post.id == targetPostId),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // (3) インデックスタブ
        // 全ての要素の最前面に重なる。
        if (posts.isNotEmpty()) {
            // postsリストから安全に最初の投稿の日付を取得
            val firstPostDate = remember(posts) {
                posts.first().createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
            }
            IndexTab(
                dateString = firstPostDate.toString(),
                onClick = onDismiss, // タブのクリックでも閉じる
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 8.dp)
            )
        }
    }
}

@Composable
fun IndexTab(dateString: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = LocalDate.parse(dateString)
    val fullText = date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH))
    val density = LocalDensity.current

    val outlineWidth = 3.dp
    val outerCornerRadius = 16.dp
    // 内側の角丸は、外側からアウトラインの幅を引いた値にします
    val innerCornerRadius = outerCornerRadius - outlineWidth

    val outerShape = RoundedCornerShape(topStart = outerCornerRadius, bottomStart = outerCornerRadius)
    val innerShape = RoundedCornerShape(topStart = innerCornerRadius, bottomStart = innerCornerRadius)

    // SubcomposeLayoutを使い、レイアウトを2段階で構築します

    SubcomposeLayout(
        modifier = modifier
            // 1. まず、外枠となる「白」で全体を塗りつぶします
            .background(Color.White, outerShape)
            // 2. 次に、アウトラインの太さ分だけ内側に余白を作ります
            .padding(top = outlineWidth, start = outlineWidth, bottom = outlineWidth)
            // 3. 最後に、内側の領域を、角丸を調整したShapeで塗りつぶします
            .background(MaterialTheme.colorScheme.primary, innerShape) // ◀◀◀ ここにinnerShapeを指定
            .clip(outerShape) // 念のため全体をクリップ
            .clickable(onClick = onClick),
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
fun LogViewPostCard(
    post: Post,
    isTargetPost: Boolean, // ◀◀◀ この引数はそのまま使います
    modifier: Modifier = Modifier
) {
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
    val timeString = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    val scale = remember { Animatable(1f) }

    // 2. isTargetPostがtrueの時に、一度だけアニメーションを実行
    LaunchedEffect(isTargetPost) {
        if (isTargetPost) {
            delay(200L)
            // ほんの少しだけ縮んで…
            scale.animateTo(
                targetValue = 0.97f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy, // 跳ね返りをなくす
                    stiffness = Spring.StiffnessVeryLow  // ◀◀◀ 「普通」の硬さに変更
                )
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessLow   // ◀◀◀ 「柔らかい」硬さに変更
                )
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {})
            .scale(scale.value),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // 影を統一
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        // ▼▼▼ このRowが全体のコンテナになります ▼▼▼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp) // ◀◀◀ 親にpaddingを適用
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically // ◀◀◀ 上下中央揃えにすると綺麗です
        ) {
            // 1. カラーバー
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight() // ◀◀◀ 親（padding適用済み）の高さに追従
                    .background(post.source.brandColor)
            )

            // 2. スペーサー（バーとコンテンツの間の余白）
            Spacer(modifier = Modifier.width(16.dp))

            // 3. 元のコンテンツ（時刻・テキスト・画像）
            Column(
                // こちらのColumnからはpaddingを削除
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    AsyncImage(
                        model = Uri.parse(post.imageUrl),
                        contentDescription = "投稿画像",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}