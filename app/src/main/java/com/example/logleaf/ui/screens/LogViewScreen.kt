package com.example.logleaf.ui.screens

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.logleaf.Post
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogViewScreen(
    posts: List<Post>,
    targetPostId: String,
    onDismiss: () -> Unit
) {
    val (enlargedImageUri, setEnlargedImageUri) = remember { mutableStateOf<Uri?>(null) }

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
            .pointerInput(Unit) {
                // もし画像が拡大表示中なら、背景タップで何もしない
                if (enlargedImageUri == null) { // ◀◀◀ 条件分岐を追加
                    detectTapGestures(
                        onTap = { onDismiss() }
                    )
                }
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
                LogViewPostCard(
                    post = post,
                    isTargetPost = (post.id == targetPostId),
                    // ▼▼▼【変更点 2/3】値の更新に専用の関数を使う ▼▼▼
                    onImageClick = { uri -> setEnlargedImageUri(uri) },
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
    enlargedImageUri?.let { uri ->
        ZoomableImageDialog(
            imageUri = uri,
            // ▼▼▼【変更点 3/3】ここも同様に専用関数を使う ▼▼▼
            onDismiss = { setEnlargedImageUri(null) }
        )
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

    val outerShape =
        RoundedCornerShape(topStart = outerCornerRadius, bottomStart = outerCornerRadius)
    val innerShape =
        RoundedCornerShape(topStart = innerCornerRadius, bottomStart = innerCornerRadius)

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
    isTargetPost: Boolean,
    onImageClick: (Uri) -> Unit, // ◀◀◀ この引数を追加
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
                    stiffness = Spring.StiffnessMediumLow  // ◀◀◀ 硬さ変更
                )
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow   // ◀◀◀ 硬さ変更
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
                    val imageUri = remember { Uri.parse(post.imageUrl) } // Uriのパースを一度だけ行う
                    AsyncImage(
                        model = imageUri, // ◀◀◀ 変更
                        contentDescription = "投稿画像",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(imageUri) }, // ◀◀◀ クリックイベントを追加
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun ZoomableImageDialog(
    imageUri: Uri,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // 状態変数
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var backgroundAlpha by remember { mutableFloatStateOf(1f) }
    var lastPanVelocity by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
                    .pointerInput(Unit) {
                        // ダブルタップ検出
                        detectTapGestures(
                            onDoubleTap = {
                                // 拡大されている時のみ元に戻す
                                if (scale > 1f) {
                                    scope.launch {
                                        val scaleAnimation = async {
                                            Animatable(scale).animateTo(1f, tween(100))
                                        }
                                        val offsetAnimation = async {
                                            Animatable(offset, Offset.VectorConverter).animateTo(Offset.Zero, tween(100))
                                        }

                                        scaleAnimation.await()
                                        scale = 1f

                                        offsetAnimation.await()
                                        offset = Offset.Zero
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        // トランスフォームジェスチャー（ピンチ・パン）
                        detectTransformGestures { _, pan, zoom, _ ->
                            val isZooming = abs(zoom - 1f) > 0.005f  // より敏感な検出閾値
                            val oldScale = scale

                            // 速度を記録（下スワイプ用）
                            if (!isZooming && oldScale <= 1f) {
                                lastPanVelocity = pan
                            }

                            // スケール更新（常に実行、ただし1f未満にならないよう制限）
                            if (isZooming) {
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale

                                // 縮小時にオフセットも調整（画像が画面からはみ出さないように）
                                if (newScale < oldScale && newScale <= 1f) {
                                    offset = Offset.Zero
                                }
                            }

                            // パン操作の処理（ピンチ操作中は無視）
                            if (!isZooming) {
                                if (scale > 1f) {
                                    // 拡大中：制限付きパン操作
                                    val imageWidthScaled = screenWidthPx * scale
                                    val imageHeightScaled = screenHeightPx * scale
                                    val maxX = maxOf(0f, (imageWidthScaled - screenWidthPx) / 2)
                                    val maxY = maxOf(0f, (imageHeightScaled - screenHeightPx) / 2)

                                    val newOffset = offset + pan
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-maxX, maxX),
                                        y = newOffset.y.coerceIn(-maxY, maxY)
                                    )
                                } else if (oldScale <= 1f) {
                                    // 拡大されていない時：下方向スワイプのみ許可
                                    if (pan.y > 0) {
                                        // 下スワイプ
                                        offset = Offset(0f, maxOf(0f, offset.y + pan.y))

                                        // 背景透明度の調整（同期処理）
                                        backgroundAlpha = (1f - offset.y / (screenHeightPx / 6f)).coerceIn(0.2f, 1f)
                                    } else if (pan.y < 0 && offset.y > 0f) {
                                        // 上スワイプで位置リセット（下にずれている場合のみ）
                                        val newY = maxOf(0f, offset.y + pan.y)
                                        offset = Offset(0f, newY)

                                        if (newY == 0f) {
                                            backgroundAlpha = 1f
                                        } else {
                                            backgroundAlpha = (1f - newY / (screenHeightPx / 6f)).coerceIn(0.2f, 1f)
                                        }
                                    }
                                    // 横スワイプは無視（offset.xは常に0のまま）
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // 指を離した時の処理
                        forEachGesture {
                            awaitPointerEventScope {
                                // ポインターイベントを待機
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { it.pressed })

                                // 指が全て離れた時の後処理
                                if (scale <= 1f) {
                                    val dismissThreshold = screenHeightPx / 6f
                                    val velocityThreshold = 800f // 速度による閉じる判定

                                    // 速度または位置による閉じる判定
                                    val shouldDismiss = offset.y > dismissThreshold || lastPanVelocity.y > velocityThreshold

                                    if (shouldDismiss) {
                                        // 慣性を考慮した自然なスライドアウト
                                        scope.launch {
                                            val initialVelocity = maxOf(lastPanVelocity.y, 500f) // 最低速度を保証
                                            val targetY = screenHeightPx + 300f

                                            // 慣性アニメーション
                                            val animatable = Animatable(offset.y)

                                            // アニメーション実行と値の監視を同時に開始
                                            val animationJob = launch {
                                                animatable.animateTo(
                                                    targetValue = targetY,
                                                    initialVelocity = initialVelocity,
                                                    animationSpec = tween(150)
                                                )
                                            }

                                            val updateJob = launch {
                                                while (animationJob.isActive) {
                                                    offset = Offset(0f, animatable.value)
                                                    backgroundAlpha = (1f - animatable.value / (screenHeightPx / 6f)).coerceIn(0f, 1f)
                                                    kotlinx.coroutines.delay(8) // 約120fps
                                                }
                                            }

                                            // アニメーション完了を待つ
                                            animationJob.join()
                                            updateJob.cancel()

                                            // 画像と背景を同時に消す
                                            backgroundAlpha = 0f
                                            onDismiss()
                                        }
                                    } else if (offset.y > 0f) {
                                        // 不十分な場合は元の位置に戻す（拡大されていない時のみ）
                                        scope.launch {
                                            val offsetAnimation = async {
                                                Animatable(offset, Offset.VectorConverter).animateTo(
                                                    Offset.Zero,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                            val alphaAnimation = async {
                                                val alphaAnimatable = Animatable(backgroundAlpha)
                                                alphaAnimatable.animateTo(
                                                    1f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }

                                            offsetAnimation.await()
                                            offset = Offset.Zero
                                            alphaAnimation.await()
                                            backgroundAlpha = 1f
                                        }
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "拡大画像",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
        }
    }
}