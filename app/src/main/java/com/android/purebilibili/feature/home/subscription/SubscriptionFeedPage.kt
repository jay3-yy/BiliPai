package com.android.purebilibili.feature.home.subscription

import android.content.Intent
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import com.android.purebilibili.core.util.animateScrollToTop
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.plugin.feed.FeedBlock
import com.android.purebilibili.core.plugin.feed.FeedInline
import com.android.purebilibili.core.plugin.feed.ParsedFeedItem
import com.android.purebilibili.core.plugin.feed.FeedSource
import com.android.purebilibili.core.plugin.feed.FeedReadingStore
import com.android.purebilibili.core.plugin.feed.SubscriptionFeedStore
import com.android.purebilibili.core.plugin.feed.feedItemKey
import com.android.purebilibili.core.plugin.feed.mergeCachedFeedItems
import com.android.purebilibili.core.plugin.feed.cleanFeedSummary
import com.android.purebilibili.core.plugin.feed.feedBodyNeedsRemoteFetch
import com.android.purebilibili.core.plugin.feed.isHttpFeedUrl
import com.android.purebilibili.core.plugin.feed.fetchArticleHtml
import com.android.purebilibili.core.plugin.feed.loadEnabledFeedSources
import com.android.purebilibili.core.plugin.feed.loadFeedSources
import com.android.purebilibili.core.plugin.feed.parseFeedHtml
import com.android.purebilibili.core.plugin.feed.stabilizeFeedOrder
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden
import com.android.purebilibili.feature.dynamic.components.imagePreviewSourceBounds
import com.android.purebilibili.feature.dynamic.components.rememberImagePreviewSourceRect
import com.android.purebilibili.feature.home.homeFeedPinchZoom
import java.time.Instant
import java.time.ZoneId

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun SubscriptionFeedPage(
    contentPadding: PaddingValues,
    articleContentPadding: PaddingValues = contentPadding,
    scrollToTopRequestId: Int,
    listState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    gridColumns: Int = 1,
    pinchEnabled: Boolean = false,
    pinchBounds: IntRange = 1..1,
    onColumnsChange: (Int) -> Unit = {},
    onPinchEnd: (Int) -> Unit = {},
    onArticleOpenChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val subscriptionRevision by SubscriptionFeedStore.revision.collectAsStateWithLifecycle()
    var sources by remember { mutableStateOf<List<FeedSource>>(emptyList()) }
    var items by remember { mutableStateOf<List<ParsedFeedItem>>(emptyList()) }
    var readKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cachedBodies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var unreadOnly by remember { mutableStateOf(false) }
    var loadErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<ParsedFeedItem?>(null) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var previewSourceRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var loading by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val transitionState = remember { SeekableTransitionState<ParsedFeedItem?>(null) }
    val isArticleOpen = opened != null || transitionState.currentState != null || transitionState.targetState != null

    LaunchedEffect(isArticleOpen) {
        onArticleOpenChanged(isArticleOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onArticleOpenChanged(false) }
    }
    PredictiveBackHandler(enabled = isArticleOpen) { progress ->
        var lastFraction = 0f
        try {
            progress.collect { event ->
                lastFraction = event.progress.coerceIn(0f, 1f)
                transitionState.seekTo(
                    fraction = lastFraction,
                    targetState = null
                )
            }
            val remainingMs = ((1f - lastFraction) * 360).toInt().coerceIn(100, 360)
            transitionState.animateTo(
                targetState = null,
                animationSpec = tween(remainingMs, easing = LinearEasing)
            )
            opened = null
        } catch (cancelled: CancellationException) {
            val currentOpened = opened
            if (currentOpened != null) {
                withContext(NonCancellable) {
                    if (lastFraction > 0.001f) {
                        val startFraction = lastFraction
                        val durationMs = (startFraction * 200).toInt().coerceIn(50, 180)
                        runCatching {
                            val startTime = withFrameMillis { it }
                            while (true) {
                                val currentTime = withFrameMillis { it }
                                val elapsed = currentTime - startTime
                                val p = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                                val eased = 1f - FastOutSlowInEasing.transform(p)
                                transitionState.seekTo(
                                    fraction = startFraction * eased,
                                    targetState = null
                                )
                                if (p >= 1f) break
                            }
                        }
                    }
                    runCatching { transitionState.seekTo(fraction = 0f, targetState = null) }
                    runCatching { transitionState.snapTo(currentOpened) }
                }
            }
        }
    }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollToTopRequestId) {
        if (scrollToTopRequestId > 0) listState.animateScrollToTop()
    }
    LaunchedEffect(reloadToken, subscriptionRevision) {
        loading = true
        loadErrors = emptyList()
        val loadedSources = withContext(Dispatchers.IO) { loadEnabledFeedSources(context) }
        sources = loadedSources
        if (selectedSourceId != null && loadedSources.none { it.id == selectedSourceId }) {
            selectedSourceId = null
        }
        val cache = FeedReadingStore.load(context)
        readKeys = cache.readKeys.toSet()
        cachedBodies = cache.fullBodies
        val enabledIds = loadedSources.map { it.id }.toSet()
        items = mergeCachedFeedItems(cache.items, emptyList(), enabledIds)
        val snapshot = loadFeedSources(loadedSources) { update ->
            val preserveOrder = listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
            val merged = mergeCachedFeedItems(cache.items, update.items, enabledIds)
            items = stabilizeFeedOrder(items, merged, preserveOrder)
            loadErrors = update.errors
        }
        val merged = mergeCachedFeedItems(cache.items, snapshot.items, enabledIds)
        val preserveOrder = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        items = stabilizeFeedOrder(items, merged, preserveOrder)
        loadErrors = snapshot.errors
        runCatching { FeedReadingStore.saveItems(context, merged) }
            .onFailure { loadErrors = loadErrors + "本地缓存保存失败" }
        loading = false
    }

    val visibleItems = items.filter { item ->
        (selectedSourceId == null || item.sourceId == selectedSourceId) &&
            (!unreadOnly || feedItemKey(item) !in readKeys || feedItemKey(item) == opened?.let(::feedItemKey))
    }
    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        val transition = rememberTransition(transitionState, label = "subscription-article")
        transition.AnimatedContent(
            transitionSpec = {
                fadeIn(tween(360, easing = LinearEasing)) togetherWith fadeOut(tween(360, easing = LinearEasing))
            },
            contentKey = { it?.let { "${it.sourceId}:${it.id}" } ?: "grid" },
            modifier = Modifier.fillMaxSize(),
        ) { article ->
            if (article != null) {
                SubscriptionArticleScreen(
                    item = article,
                    contentPadding = articleContentPadding,
                    cachedBody = cachedBodies[feedItemKey(article)],
                    isRead = feedItemKey(article) in readKeys,
                    onReadChange = { read ->
                        val key = feedItemKey(article)
                        readKeys = if (read) readKeys + key else readKeys - key
                        scope.launch {
                            runCatching { FeedReadingStore.setRead(context, key, read) }
                                .onFailure { loadErrors = loadErrors + "阅读状态保存失败" }
                        }
                    },
                    onFullBody = { body ->
                        val key = feedItemKey(article)
                        cachedBodies = cachedBodies + (key to body)
                        scope.launch {
                            runCatching { FeedReadingStore.saveFullBody(context, key, body) }
                                .onFailure { loadErrors = loadErrors + "正文缓存保存失败" }
                        }
                    },
                    onBack = {
                        scope.launch {
                            transitionState.animateTo(
                                targetState = null,
                                animationSpec = tween(360, easing = LinearEasing)
                            )
                            opened = null
                        }
                    },
                    onOpenImages = { images, index, rect ->
                        previewImages = images
                        previewIndex = index
                        previewSourceRect = rect
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            } else {
                SubscriptionFeedGrid(
                    sources = sources,
                    visibleItems = visibleItems,
                    loading = loading,
                    errors = loadErrors,
                    unreadOnly = unreadOnly,
                    onUnreadOnlyChange = { unreadOnly = it },
                    readKeys = readKeys,
                    selectedSourceId = selectedSourceId,
                    onSelectSource = { selectedSourceId = it },
                    onRefresh = { reloadToken += 1 },
                    onOpen = { item ->
                        opened = item
                        val key = feedItemKey(item)
                        readKeys = readKeys + key
                        scope.launch {
                            runCatching { FeedReadingStore.setRead(context, key, true) }
                                .onFailure { loadErrors = loadErrors + "阅读状态保存失败" }
                        }
                        scope.launch {
                            transitionState.animateTo(
                                targetState = item,
                                animationSpec = tween(360, easing = LinearEasing)
                            )
                        }
                    },
                    contentPadding = contentPadding,
                    listState = listState,
                    gridColumns = gridColumns,
                    pinchEnabled = pinchEnabled,
                    pinchBounds = pinchBounds,
                    onColumnsChange = onColumnsChange,
                    onPinchEnd = onPinchEnd,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
        }
    }
    if (previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewIndex.coerceIn(0, previewImages.lastIndex),
            sourceRect = previewSourceRect,
            // FeedArticleImage 用 MaterialTheme.shapes.medium 裁角，回位圆角保持一致
            sourceCornerRadiusDp = with(density) {
                (MaterialTheme.shapes.medium as? CornerBasedShape)?.topStart
                    ?.toPx(Size.Unspecified, this)?.toDp()?.value
            } ?: 12f,
            onDismiss = { previewImages = emptyList() },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SubscriptionFeedGrid(
    sources: List<FeedSource>,
    visibleItems: List<ParsedFeedItem>,
    loading: Boolean,
    errors: List<String>,
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    readKeys: Set<String>,
    selectedSourceId: String?,
    onSelectSource: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ParsedFeedItem) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyStaggeredGridState,
    gridColumns: Int,
    pinchEnabled: Boolean,
    pinchBounds: IntRange,
    onColumnsChange: (Int) -> Unit,
    onPinchEnd: (Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(gridColumns.coerceAtLeast(1)),
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .homeFeedPinchZoom(
                enabled = pinchEnabled,
                currentColumns = gridColumns.coerceAtLeast(1),
                bounds = pinchBounds,
                onColumnsChange = onColumnsChange,
                onGestureEnd = onPinchEnd,
            ),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppAssistChip(onClick = { onSelectSource(null) }, label = { AppText("全部") })
                AppAssistChip(
                    onClick = { onUnreadOnlyChange(!unreadOnly) },
                    label = { AppText(if (unreadOnly) "✓ 只看未读" else "只看未读") },
                )
                sources.forEach { source ->
                    AppAssistChip(
                        onClick = { onSelectSource(source.id) },
                        label = { AppText(source.title) },
                    )
                }
                AppTextButton(onClick = onRefresh, enabled = !loading) {
                    AppText(if (loading) "刷新中" else "刷新")
                }
            }
        }
        if (sources.isEmpty() && !loading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                AppText("还没有订阅。到插件中心打开「订阅」，添加 RSS 或 Atom 地址。")
            }
        }
        if (errors.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                AppText(
                    text = errors.take(2).joinToString("；"),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (sources.isNotEmpty() && visibleItems.isEmpty() && !loading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                AppText(if (unreadOnly) "没有未读文章" else "暂无文章，下拉后可刷新订阅。")
            }
        }
        items(visibleItems, key = { "${it.sourceId}:${it.id}" }) { item ->
            SubscriptionFeedCard(
                item = item,
                isRead = feedItemKey(item) in readKeys,
                onClick = { onOpen(item) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
        item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(28.dp)) }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SubscriptionFeedCard(
    item: ParsedFeedItem,
    isRead: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    AppSurface(
        modifier = with(sharedTransitionScope) {
            Modifier
                .fillMaxWidth()
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(subscriptionSharedKey(item)),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ -> tween(360, easing = LinearEasing) },
                    clipInOverlayDuringTransition = OverlayClip(AppShapes.container(ContainerLevel.Card)),
                )
                .clip(AppShapes.container(ContainerLevel.Card))
                .clickable(onClick = onClick)
        },
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 0.dp,
    ) {
        Column {
            if (!item.imageUrl.isNullOrBlank()) {
                FeedCoverImage(
                    url = item.imageUrl,
                    aspectRatio = item.coverAspectRatio,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = item.title.ifBlank { item.link },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = listOf(item.sourceTitle, item.author, formatFeedAge(item.publishedEpochSec))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FeedCoverImage(
    url: String,
    aspectRatio: Float,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.62f, 1.35f)),
        contentScale = ContentScale.Crop,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SubscriptionArticleScreen(
    item: ParsedFeedItem,
    contentPadding: PaddingValues,
    cachedBody: String?,
    isRead: Boolean,
    onReadChange: (Boolean) -> Unit,
    onFullBody: (String) -> Unit,
    onBack: () -> Unit,
    onOpenImages: (List<String>, Int, androidx.compose.ui.geometry.Rect?) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var articleHtml by remember(item.sourceId, item.id, item.link) {
        mutableStateOf(cachedBody ?: item.htmlContent.ifBlank { item.summary })
    }
    var loadingBody by remember(item.sourceId, item.id, item.link) { mutableStateOf(false) }
    var bodyError by remember(item.sourceId, item.id, item.link) { mutableStateOf<String?>(null) }
    var retryToken by remember(item.sourceId, item.id, item.link) { mutableIntStateOf(0) }
    LaunchedEffect(item.sourceId, item.id, item.link, retryToken) {
        if (cachedBody != null || !feedBodyNeedsRemoteFetch(item)) return@LaunchedEffect
        loadingBody = true
        bodyError = null
        fetchArticleHtml(item.link)
            .onSuccess { fetched ->
                if (com.android.purebilibili.core.plugin.feed.feedPlainText(fetched).length >
                    com.android.purebilibili.core.plugin.feed.feedPlainText(articleHtml).length
                ) {
                    articleHtml = fetched
                    onFullBody(fetched)
                } else {
                    bodyError = "原文没有更多正文，已保留订阅内容"
                }
            }
            .onFailure { bodyError = it.message ?: "暂时无法读取原文" }
        loadingBody = false
    }
    val blocks = remember(articleHtml) {
        parseFeedHtml(articleHtml, item.link).ifEmpty {
            listOf(FeedBlock.Paragraph(listOf(FeedInline.Text(cleanFeedSummary(item.summary).ifBlank { item.title }))))
        }
    }
    val imageUrls = remember(blocks) {
        blocks.filterIsInstance<FeedBlock.Image>().map { it.url }.distinct()
    }
    val layoutDirection = LocalLayoutDirection.current
    AppSurface(
        modifier = with(sharedTransitionScope) {
            modifier
                .fillMaxSize()
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(subscriptionSharedKey(item)),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ -> tween(360, easing = LinearEasing) },
                    clipInOverlayDuringTransition = OverlayClip(AppShapes.container(ContainerLevel.Card)),
                )
        },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        ImmersiveAppScaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBarSurfaceColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTopBar(
                    title = "文章",
                    navigationIcon = {
                        AppIconButton(onClick = onBack) {
                            AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                        }
                    },
                    actions = {
                        AppTextButton(
                            onClick = { onReadChange(!isRead) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            AppText(if (isRead) "标未读" else "标已读")
                        }
                        AppTextButton(
                            onClick = {
                                copyFeedText(context, feedBlocksPlainText(blocks).ifBlank { item.title })
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            AppText("复制")
                        }
                        if (isHttpFeedUrl(item.link)) {
                            AppTextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.link)))
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                AppText("原文")
                            }
                        }
                    },
                )
            },
        ) { scaffoldPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    AppText(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    AppText(
                        text = listOf(item.sourceTitle, item.author, formatFeedAge(item.publishedEpochSec))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loadingBody) AppText("正在补全正文，当前内容仍可阅读", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    bodyError?.let { error ->
                        AppText(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AppTextButton(onClick = { retryToken += 1 }) { AppText("重试读取全文") }
                    }
                    }
                }
                lazyListItems(blocks) { block ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    when (block) {
                        is FeedBlock.Heading -> SelectionContainer {
                            FeedInlineText(
                                block.inlines,
                                style = when (block.level) {
                                    1 -> MaterialTheme.typography.headlineSmall
                                    2 -> MaterialTheme.typography.titleLarge
                                    else -> MaterialTheme.typography.titleMedium
                                },
                            )
                        }
                        is FeedBlock.Paragraph -> SelectionContainer { FeedInlineText(block.inlines) }
                        is FeedBlock.Quote -> SelectionContainer {
                            FeedInlineText(
                                block.inlines,
                                modifier = Modifier.padding(start = 12.dp),
                                italic = true,
                            )
                        }
                        is FeedBlock.Code -> SelectionContainer {
                            AppText(
                                block.text,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        is FeedBlock.Image -> FeedArticleImage(
                            url = block.url,
                            alt = block.alt,
                            onClick = { rect ->
                                val index = imageUrls.indexOf(block.url).coerceAtLeast(0)
                                onOpenImages(imageUrls, index, rect)
                            },
                        )
                        is FeedBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            block.items.forEach { line ->
                                Row {
                                    AppText("• ")
                                    FeedInlineText(line, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        is FeedBlock.NumberedList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            block.items.forEachIndexed { index, line ->
                                Row {
                                    AppText("${index + 1}. ")
                                    FeedInlineText(line, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        is FeedBlock.EmbeddedLink -> AppTextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(block.url)))
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { AppText(block.title) }
                    }
                    }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun FeedInlineText(
    inlines: List<FeedInline>,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    italic: Boolean = false,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        inlines.forEach { inline ->
            when (inline) {
                is FeedInline.Text -> withStyle(
                    SpanStyle(
                        fontWeight = if (inline.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (inline.italic || italic) FontStyle.Italic else FontStyle.Normal,
                    )
                ) {
                    append(inline.text)
                }
                is FeedInline.Link -> withLink(
                    LinkAnnotation.Url(
                        inline.url,
                        TextLinkStyles(SpanStyle(color = linkColor)),
                    )
                ) {
                    append(inline.text)
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style.copy(lineHeight = style.fontSize * 1.55f),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FeedArticleImage(
    url: String,
    alt: String,
    modifier: Modifier = Modifier,
    onClick: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    var failed by remember(url) { mutableStateOf(false) }
    val sourceRect = rememberImagePreviewSourceRect()
    val sourceHidden = isImagePreviewSourceHidden(sourceRect.value)
    if (failed) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            AppText(alt.ifBlank { "图片无法显示" })
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = alt.ifBlank { "查看图片" },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clip(MaterialTheme.shapes.medium)
                .alpha(if (sourceHidden) 0f else 1f)
                .imagePreviewSourceBounds(sourceRect)
                .clickable(enabled = !sourceHidden) { onClick(sourceRect.value) },
            contentScale = ContentScale.Fit,
            onError = { failed = true },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun subscriptionSharedKey(item: ParsedFeedItem): String = "subscription:${item.sourceId}:${item.id}"

private fun feedBlocksPlainText(blocks: List<FeedBlock>): String {
    return blocks.joinToString("\n\n") { block ->
        when (block) {
            is FeedBlock.Heading -> inlinePlainText(block.inlines)
            is FeedBlock.Paragraph -> inlinePlainText(block.inlines)
            is FeedBlock.Quote -> inlinePlainText(block.inlines)
            is FeedBlock.Code -> block.text
            is FeedBlock.BulletList -> block.items.joinToString("\n") { "• ${inlinePlainText(it)}" }
            is FeedBlock.NumberedList -> block.items.mapIndexed { index, line ->
                "${index + 1}. ${inlinePlainText(line)}"
            }.joinToString("\n")
            is FeedBlock.Image -> block.alt
            is FeedBlock.EmbeddedLink -> "${block.title} ${block.url}"
        }
    }.trim()
}

private fun inlinePlainText(inlines: List<FeedInline>): String {
    return inlines.joinToString("") { inline ->
        when (inline) {
            is FeedInline.Text -> inline.text
            is FeedInline.Link -> inline.text
        }
    }
}

private fun copyFeedText(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("订阅正文", text))
    android.widget.Toast.makeText(context, "已复制正文", android.widget.Toast.LENGTH_SHORT).show()
}

internal fun formatFeedAge(epochSec: Long?, nowSec: Long = System.currentTimeMillis() / 1000): String {
    if (epochSec == null || epochSec <= 0L) return ""
    val delta = (nowSec - epochSec).coerceAtLeast(0L)
    return when {
        delta < 60 -> "刚刚"
        delta < 3600 -> "${delta / 60}分钟前"
        delta < 86_400 -> "${delta / 3600}小时前"
        delta < 86_400 * 30 -> "${delta / 86_400}天前"
        else -> Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
}
