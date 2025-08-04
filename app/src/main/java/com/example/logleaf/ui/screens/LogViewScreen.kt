package com.example.logleaf.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.logleaf.PostWithTagsAndImages
import com.example.logleaf.ui.components.SmartTagDisplay
import com.example.logleaf.ui.entry.PostImage
import com.example.logleaf.ui.entry.Tag
import com.example.logleaf.ui.theme.SettingsTheme
import com.example.logleaf.ui.theme.SnsType
import com.yourpackage.logleaf.ui.components.UserFontText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogViewScreen(
    posts: List<PostWithTagsAndImages>,
    targetPostId: String,
    onDismiss: () -> Unit,
    navController: NavController,
    onStartEditingPost: (PostWithTagsAndImages) -> Unit,
    onSetPostHidden: (String, Boolean) -> Unit,
    onDeletePost: (String) -> Unit
) {
    val (enlargedImageState, setEnlargedImageState) = remember { mutableStateOf<EnlargedImageState?>(null) }
    val listState = rememberLazyListState()

    // アニメーションの管理
    val scale = remember { Animatable(1f) }
    LaunchedEffect(targetPostId) {
        if (targetPostId.isNotEmpty()) {
            delay(200L)
            scale.animateTo(
                targetValue = 0.97f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    // スクロール処理
    LaunchedEffect(posts, targetPostId) {
        if (posts.isNotEmpty() && targetPostId.isNotEmpty()) {
            val index = posts.indexOfFirst { it.post.id == targetPostId }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                if (enlargedImageState == null) {
                    detectTapGestures(onTap = { onDismiss() })
                }
            }
    ) {
        // 背景の半透明ベール
        Surface(
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxSize()
        ) {}

        // 投稿リスト
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues()
                    .calculateTopPadding() + 220.dp, // ★★★ ステータスバー + インデックスタブ分
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 16.dp // ★★★ ナビゲーションバー + 余白
            )
        ) {
            items(posts, key = { it.post.id }) { postWithTagsAndImages ->
                LogViewPostCard(
                    postWithTagsAndImages = postWithTagsAndImages,
                    scale = if (postWithTagsAndImages.post.id == targetPostId) scale.value else 1f,
                    onImageClick = { uri ->
                        val clickedImageIndex = postWithTagsAndImages.images.indexOfFirst {
                            it.imageUrl == uri.toString()
                        }
                        if (clickedImageIndex != -1) {
                            setEnlargedImageState(
                                EnlargedImageState(
                                    images = postWithTagsAndImages.images,
                                    initialIndex = clickedImageIndex
                                )
                            )
                        }
                    },
                    onTagClick = { tagName ->
                        val encodedTag = URLEncoder.encode(
                            tagName.removePrefix("#"),
                            StandardCharsets.UTF_8.name()
                        )
                        navController.navigate("search?tag=$encodedTag")
                    },
                    // ★★★ 追加：投稿操作のコールバック ★★★
                    onStartEditing = {
                        // 編集の場合：ログビューを閉じてからカレンダー画面で編集開始
                        onDismiss()
                        onStartEditingPost(postWithTagsAndImages)
                    },
                    onSetHidden = { isHidden ->
                        // 非表示切り替え：ログビュー上でそのまま実行
                        onSetPostHidden(postWithTagsAndImages.post.id, isHidden)
                    },
                    onDelete = {
                        // 削除：ログビュー上でそのまま実行
                        postWithTagsAndImages
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // インデックスタブ
        if (posts.isNotEmpty()) {
            val firstPostDate = remember(posts) {
                posts.first().post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate()
            }
            IndexTab(
                dateString = firstPostDate.toString(),
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues()
                            .calculateTopPadding() + 16.dp, // ★★★ ステータスバー分を追加
                        end = 8.dp
                    )
            )
        }
    }

    enlargedImageState?.let { state ->
        ZoomableImageDialog(
            imageUri = Uri.parse(state.images[state.initialIndex].imageUrl), // とりあえず残す
            images = state.images,
            initialIndex = state.initialIndex,
            onDismiss = { setEnlargedImageState(null) }
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
    postWithTagsAndImages: PostWithTagsAndImages,
    scale: Float,
    onImageClick: (Uri) -> Unit,
    onTagClick: (String) -> Unit,
    // ★★★ 追加：長押しメニュー用のコールバック ★★★
    onStartEditing: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {

    val post = postWithTagsAndImages.post
    val localDateTime = post.createdAt.withZoneSameInstant(ZoneId.systemDefault())
    val timeString = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    // ★★★ 追加：メニュー状態とクリップボード ★★★
    var isMenuExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val interactionSource = remember { MutableInteractionSource() }

    Box { // ★★★ 追加：DropdownMenuを表示するためのBox ★★★
        Card(
            modifier = modifier
                .fillMaxWidth()
                .alpha(if (post.isHidden) 0.6f else 1.0f) // ★★★ 追加：非表示投稿の半透明表示
                .combinedClickable( // ★★★ 変更：clickableからcombinedClickableに
                    interactionSource = interactionSource,
                    indication = rememberRipple(),
                    onClick = { /* 通常のクリックは何もしない */ },
                    onLongClick = { isMenuExpanded = true } // ★★★ 追加：長押しでメニュー表示
                )
                .scale(scale),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. カラーバー
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(post.source.brandColor)
                )

                // 2. スペーサー
                Spacer(modifier = Modifier.width(16.dp))

                // 3. コンテンツ
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左側に時刻を表示
                        UserFontText(
                            text = timeString,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        // 中央の余白
                        Spacer(modifier = Modifier.weight(1f))
                        // 右側にタグをFlowRowで表示
                        if (postWithTagsAndImages.tags.isNotEmpty()) {
                            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                            val cardPadding = 32.dp // カード左右余白（16dp × 2）
                            val cardWidth = screenWidth - cardPadding
                            val colorBarAndSpacing = 20.dp // カラーバー + スペーサー

                            // カード内コンテンツ幅から、カラーバー分を除く
                            val contentWidth = cardWidth - colorBarAndSpacing

                            // 右から2/3がタグエリア
                            val tagAreaWidth = contentWidth * 2f / 3f

                            SmartTagDisplay(
                                tags = postWithTagsAndImages.tags,
                                onTagClick = onTagClick,
                                availableWidth = tagAreaWidth
                            )
                        }
                    }
                    UserFontText(
                        text = post.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (postWithTagsAndImages.images.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            postWithTagsAndImages.images.forEach { image ->
                                // サムネイルがあればサムネイル、なければ元画像を使用
                                val displayImageUrl = image.thumbnailUrl ?: image.imageUrl

                                AsyncImage(
                                    model = displayImageUrl,
                                    contentDescription = "投稿画像",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            // クリック時は元画像を拡大表示
                                            onImageClick(Uri.parse(image.imageUrl))
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }

        // ★★★ 追加：DropdownMenu ★★★
        SettingsTheme {
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
                modifier = Modifier
                    .width(80.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                val itemModifier = Modifier.height(35.dp)
                val itemPadding = PaddingValues(horizontal = 14.dp)

                // コピー
                DropdownMenuItem(
                    text = { UserFontText(text = "コピー") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(post.text))
                        isMenuExpanded = false
                    },
                    modifier = itemModifier,
                    contentPadding = itemPadding
                )

                // 編集（LogLeaf投稿のみ）
                if (post.source == SnsType.LOGLEAF) {
                    DropdownMenuItem(
                        text = { UserFontText(text = "編集") },
                        onClick = {
                            onStartEditing()
                            isMenuExpanded = false
                        },
                        modifier = itemModifier,
                        contentPadding = itemPadding
                    )
                }

                // 非表示/再表示
                DropdownMenuItem(
                    text = { UserFontText(text = if (post.isHidden) "再表示" else "非表示") },
                    onClick = {
                        onSetHidden(!post.isHidden)
                        isMenuExpanded = false
                    },
                    modifier = itemModifier,
                    contentPadding = itemPadding
                )

                // 削除（LogLeaf投稿のみ）
                if (post.source == SnsType.LOGLEAF) {
                    DropdownMenuItem(
                        text = { UserFontText(text = "削除") },
                        onClick = {
                            onDelete()
                            isMenuExpanded = false
                        },
                        modifier = itemModifier,
                        contentPadding = itemPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ZoomableImageDialog(
    imageUri: Uri,           // 既存（後で使わなくなる）
    images: List<PostImage>, // 追加
    initialIndex: Int,       // 追加
    onDismiss: () -> Unit
) {

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 状態変数
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var backgroundAlpha by remember { mutableFloatStateOf(1f) }
    var lastPanVelocity by remember { mutableStateOf(Offset.Zero) }
    var currentImageIndex by remember { mutableIntStateOf(initialIndex) }

    var isFirstLoad by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
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
                                            Animatable(offset, Offset.VectorConverter).animateTo(
                                                Offset.Zero,
                                                tween(100)
                                            )
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
                                        backgroundAlpha =
                                            (1f - offset.y / (screenHeightPx / 6f)).coerceIn(
                                                0.2f,
                                                1f
                                            )
                                    } else if (pan.y < 0 && offset.y > 0f) {
                                        // 上スワイプで位置リセット（下にずれている場合のみ）
                                        val newY = maxOf(0f, offset.y + pan.y)
                                        offset = Offset(0f, newY)

                                        if (newY == 0f) {
                                            backgroundAlpha = 1f
                                        } else {
                                            backgroundAlpha =
                                                (1f - newY / (screenHeightPx / 6f)).coerceIn(
                                                    0.2f,
                                                    1f
                                                )
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
                                    val shouldDismiss =
                                        offset.y > dismissThreshold || lastPanVelocity.y > velocityThreshold

                                    if (shouldDismiss) {
                                        // 慣性を考慮した自然なスライドアウト
                                        scope.launch {
                                            val initialVelocity =
                                                maxOf(lastPanVelocity.y, 500f) // 最低速度を保証
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
                                                    backgroundAlpha =
                                                        (1f - animatable.value / (screenHeightPx / 6f)).coerceIn(
                                                            0f,
                                                            1f
                                                        )
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
                                                Animatable(
                                                    offset,
                                                    Offset.VectorConverter
                                                ).animateTo(
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
                Box {
                    AsyncImage(
                        model = Uri.parse(images[currentImageIndex].imageUrl),
                        contentDescription = "拡大画像 $currentImageIndex",
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


            // ★追加：ドットを下端に配置
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .pointerInput(Unit) {
                        var isLongPress = false
                        var startTime = 0L
                        var startX = 0f
                        var longPressJob: Job? = null

                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        startTime = System.currentTimeMillis()
                                        startX = event.changes.first().position.x
                                        isLongPress = false

                                        // 500ms後に長押し判定
                                        longPressJob = CoroutineScope(Dispatchers.Main).launch {
                                            delay(200)
                                            isLongPress = true
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        if (isLongPress) {
                                            val currentX = event.changes.first().position.x

                                            // ドットの実際の位置を計算
                                            val dotSize = 24.dp.toPx() // タップエリアサイズ
                                            val dotSpacing = 0.dp.toPx() // 間隔
                                            val totalDotsWidth = (images.size * dotSize) + ((images.size - 1) * dotSpacing)
                                            val startX = (size.width - totalDotsWidth) / 2 // 中央寄せの開始位置

                                            // どのドットの上にいるかを判定
                                            var targetIndex = currentImageIndex // デフォルトは現在のまま

                                            repeat(images.size) { index ->
                                                val dotLeft = startX + (index * (dotSize + dotSpacing))
                                                val dotRight = dotLeft + dotSize

                                                if (currentX >= dotLeft && currentX <= dotRight) {
                                                    targetIndex = index
                                                }
                                            }

                                            currentImageIndex = targetIndex
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        longPressJob?.cancel() // 長押しタイマーをキャンセル

                                        if (!isLongPress) {
                                            // 短時間 = フリック
                                            val distance = event.changes.first().position.x - startX
                                            if (abs(distance) > 50f) {
                                                if (distance > 0 && currentImageIndex > 0) currentImageIndex--
                                                else if (distance < 0 && currentImageIndex < images.size - 1) currentImageIndex++
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(images.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(24.dp) // タップエリアを大きく
                            .clickable {
                                Log.d("DotTap", "Tapped dot $index")
                                Log.d("DotTap", "Before: currentImageIndex = $currentImageIndex")
                                currentImageIndex = index
                                Log.d("DotTap", "After: currentImageIndex = $currentImageIndex")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (index == currentImageIndex) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentImageIndex) Color.White else Color.Gray
                                )
                        )
                    }
                    if (index < images.size - 1) {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LogViewTagChip(
    tag: Tag,
    onClick: () -> Unit // ◀◀ クリック命令を受け取る「口」を追加
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick) // ◀◀ このBoxをクリック可能にする
            .background(
                color = Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = "#${tag.tagName}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray // ◀◀ 文字色もグレーに
        )
    }
}