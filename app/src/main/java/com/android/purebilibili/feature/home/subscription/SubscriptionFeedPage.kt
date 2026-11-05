package com.android.purebilibili.feature.home.subscription

import com.android.purebilibili.feature.home.collectAsHomeFeedState
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppContentColors

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.android.purebilibili.core.ui.common.AppSelectionContainer as SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as lazyListItems
import com.android.purebilibili.core.ui.components.AppLazyVerticalStaggeredGrid as LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items
import com.android.purebilibili.core.ui.rememberCompatibleStaggeredGridState
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.util.Logger
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.android.purebilibili.core.plugin.feed.ArticleNoteStore
import com.android.purebilibili.core.plugin.feed.SavedArticleNote
import com.android.purebilibili.core.plugin.feed.FeedInline
import com.android.purebilibili.core.plugin.feed.ParsedFeedItem
import com.android.purebilibili.core.plugin.feed.FeedSource
import com.android.purebilibili.core.plugin.feed.FeedReadingStore
import com.android.purebilibili.core.plugin.feed.FeedConditionalStore
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
import com.android.purebilibili.core.plugin.feed.feedPlainText
import com.android.purebilibili.feature.video.note.VideoNoteContentCodec
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.ui.LocalSetBottomBarVisible
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.feature.home.HomeCardWallpaperSurfaceMode
import com.android.purebilibili.feature.home.HomeWallpaperBackdrop
import com.android.purebilibili.feature.home.LocalHomeWallpaperBackdrop
import com.android.purebilibili.feature.home.LocalHomeWallpaperBackdropReady
import com.android.purebilibili.feature.home.LocalHomeWallpaperIsStatic
import com.android.purebilibili.feature.home.resolveHomeCardWallpaperSurfaceMode
import com.android.purebilibili.feature.home.resolveHomeWallpaperBackdropAppearance
import com.android.purebilibili.feature.home.resolveHomeWallpaperUri
import com.android.purebilibili.core.ui.AppShapes
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden
import com.android.purebilibili.feature.dynamic.components.imagePreviewSourceBounds
import com.android.purebilibili.feature.dynamic.components.rememberImagePreviewSourceRect
import com.android.purebilibili.feature.dynamic.components.prepareImagePreviewSourceTransition
import com.android.purebilibili.feature.home.homeFeedPinchZoom
import java.time.Instant
import java.time.ZoneId

/** revision 触发的非手动刷新最小联网间隔；间隔内只重读本地缓存。 */
private const val MIN_NETWORK_REFRESH_INTERVAL_MS = 8_000L

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun SubscriptionFeedPage(
    contentPadding: PaddingValues,
    articleContentPadding: PaddingValues = contentPadding,
    scrollToTopRequestId: Int,
    listState: LazyStaggeredGridState = rememberCompatibleStaggeredGridState(),
    gridColumns: Int = 1,
    pinchEnabled: Boolean = false,
    pinchBounds: IntRange = 1..1,
    onColumnsChange: (Int) -> Unit = {},
    onPinchEnd: (Int) -> Unit = {},
    onArticleOpenChanged: (Boolean) -> Unit = {},
    onArticleScrollInProgressChanged: (Boolean) -> Unit = {},
    onOpenPluginSettings: () -> Unit = {},
    /** 下拉中、请求中、回弹中任一阶段为 true，供首页顶栏在刷新会话期间保持展开。 */
    onRefreshInteractionActiveChanged: (Boolean) -> Unit = {},
    pageActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val subscriptionRevision by SubscriptionFeedStore.revision
        .collectAsHomeFeedState(pageActive)
    var sources by remember { mutableStateOf<List<FeedSource>>(emptyList()) }
    var items by remember { mutableStateOf<List<ParsedFeedItem>>(emptyList()) }
    var readKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cachedBodies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var unreadOnly by remember { mutableStateOf(false) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<ParsedFeedItem?>(null) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var previewSourceRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var previewSourceRects by remember { mutableStateOf<Map<Int, androidx.compose.ui.geometry.Rect>>(emptyMap()) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // 提升到文章分支之外：文章打开导致刷新容器离场时也能把会话结束上报给顶栏。
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    SideEffect {
        onRefreshInteractionActiveChanged(
            loading || pullRefreshState.distanceFraction > 0f || pullRefreshState.isAnimating
        )
    }
    val transitionState = remember { SeekableTransitionState<ParsedFeedItem?>(null) }
    val isArticleOpen = opened != null || transitionState.currentState != null || transitionState.targetState != null

    LaunchedEffect(isArticleOpen) {
        onArticleOpenChanged(isArticleOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onArticleOpenChanged(false) }
    }
    PredictiveBackHandler(enabled = pageActive && isArticleOpen) { progress ->
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
                        // Rewind the same transition so cancellation cannot start a second
                        // page movement. Conflate frames while the suspending seek catches up.
                        channelFlow {
                            animate(
                                initialValue = startFraction,
                                targetValue = 0f,
                                animationSpec = tween(durationMs, easing = AppMotionEasing.M1Standard),
                            ) { fraction, _ -> trySend(fraction) }
                        }.conflate().collect { fraction ->
                            transitionState.seekTo(fraction = fraction, targetState = null)
                        }
                    }
                    transitionState.seekTo(fraction = 0f, targetState = null)
                    transitionState.snapTo(currentOpened)
                }
            }
            throw cancelled
        }
    }
    var reloadToken by remember { mutableIntStateOf(0) }
    var refreshErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastNetworkFetchAt by remember { mutableLongStateOf(0L) }
    var lastFetchReloadToken by remember { mutableIntStateOf(-1) }

    LaunchedEffect(scrollToTopRequestId) {
        if (pageActive && scrollToTopRequestId > 0 && !isArticleOpen) listState.animateScrollToTop()
    }
    LaunchedEffect(reloadToken, subscriptionRevision, pageActive) {
        if (!pageActive) return@LaunchedEffect
        loading = true
        try {
            val loadedSources = withContext(Dispatchers.IO) { loadEnabledFeedSources(context) }
            sources = loadedSources
            val groupNames = loadedSources.map { it.group }.filter { it.isNotBlank() }.distinct()
            val activeGroup = selectedGroup
            if (activeGroup != null && activeGroup.isNotEmpty() && activeGroup !in groupNames) {
                selectedGroup = null
            }
            if (selectedSourceId != null && loadedSources.none { it.id == selectedSourceId }) {
                selectedSourceId = null
            }
            val cache = FeedReadingStore.load(context)
            readKeys = cache.readKeys.toSet()
            cachedBodies = cache.fullBodies
            val enabledIds = loadedSources.map { it.id }.toSet()
            items = mergeCachedFeedItems(cache.items, emptyList(), enabledIds)
            // 手动下拉始终联网；revision 触发（如设置里切换开关）在最小间隔内只重读本地缓存，
            // 避免每次开关订阅都全量请求所有源。
            val manualRefresh = reloadToken != lastFetchReloadToken
            val shouldFetchNetwork = loadedSources.isNotEmpty() && (
                manualRefresh ||
                    items.isEmpty() ||
                    System.currentTimeMillis() - lastNetworkFetchAt >= MIN_NETWORK_REFRESH_INTERVAL_MS
                )
            if (!shouldFetchNetwork) return@LaunchedEffect
            refreshProgress = 0 to loadedSources.size
            val snapshot = loadFeedSources(loadedSources, FeedConditionalStore.load(context)) { update ->
                refreshProgress = update.completedSources to update.totalSources
                val preserveOrder = listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0
                val merged = mergeCachedFeedItems(cache.items, update.items, enabledIds)
                items = stabilizeFeedOrder(items, merged, preserveOrder)
            }
            refreshProgress = snapshot.completedSources to snapshot.totalSources
            val merged = mergeCachedFeedItems(cache.items, snapshot.items, enabledIds)
            val preserveOrder = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            items = stabilizeFeedOrder(items, merged, preserveOrder)
            refreshErrors = snapshot.errors
            snapshot.errors.forEach { Logger.w("SubscriptionFeed", it) }
            runCatching { FeedReadingStore.saveItems(context, merged) }
                .onFailure { Logger.w("SubscriptionFeed", "本地缓存保存失败: ${it.message}") }
            if (snapshot.validators.isNotEmpty()) {
                runCatching { FeedConditionalStore.update(context, snapshot.validators) }
                    .onFailure { Logger.w("SubscriptionFeed", "刷新状态保存失败: ${it.message}") }
            }
            // 离页取消的请求不算完成，重新可见时仍可继续本轮刷新。
            lastFetchReloadToken = reloadToken
            lastNetworkFetchAt = System.currentTimeMillis()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Logger.w("SubscriptionFeed", "刷新失败，保留已显示内容: ${failure.message}")
        } finally {
            loading = false
        }
    }

    val groupSourceIds = remember(sources, selectedGroup) {
        if (selectedGroup == null) {
            emptySet()
        } else {
            sources.filter { it.group == selectedGroup }.map { it.id }.toSet()
        }
    }
    val visibleItems = items.filter { item ->
        (selectedSourceId == null || item.sourceId == selectedSourceId) &&
            (selectedGroup == null || item.sourceId in groupSourceIds) &&
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
                    onScrollInProgressChanged = onArticleScrollInProgressChanged,
                    scrollToTopRequestId = scrollToTopRequestId,
                    contentPadding = articleContentPadding,
                    cachedBody = cachedBodies[feedItemKey(article)],
                    isRead = feedItemKey(article) in readKeys,
                    onOpenUrl = { url -> webUrl = url },
                    onReadChange = { read ->
                        val key = feedItemKey(article)
                        readKeys = if (read) readKeys + key else readKeys - key
                        scope.launch {
                            runCatching {
                                if (read) FeedReadingStore.recordRead(context, key)
                                else FeedReadingStore.setRead(context, key, false)
                            }
                                .onFailure { Logger.w("SubscriptionFeed", "阅读状态保存失败: ${it.message}") }
                        }
                    },
                    onFullBody = { body ->
                        val key = feedItemKey(article)
                        cachedBodies = cachedBodies + (key to body)
                        scope.launch {
                            runCatching { FeedReadingStore.saveFullBody(context, key, body) }
                                .onFailure { Logger.w("SubscriptionFeed", "正文缓存保存失败: ${it.message}") }
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
                    onOpenImages = { images, index, rect, rects ->
                        previewImages = images
                        previewIndex = index
                        previewSourceRect = rect
                        previewSourceRects = rects
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            } else {
                AdaptivePullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = { reloadToken += 1 },
                    state = pullRefreshState,
                    indicatorTopInset = contentPadding.calculateTopPadding(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SubscriptionFeedGrid(
                        sources = sources,
                        visibleItems = visibleItems,
                        loading = loading,
                        unreadOnly = unreadOnly,
                        refreshErrors = refreshErrors,
                        onDismissRefreshErrors = { refreshErrors = emptyList() },
                        refreshProgress = refreshProgress,
                        selectedGroup = selectedGroup,
                        onSelectGroup = { selectedGroup = it },
                        onUnreadOnlyChange = { unreadOnly = it },
                        readKeys = readKeys,
                        selectedSourceId = selectedSourceId,
                        onSelectSource = { selectedSourceId = it },
                        onRefresh = { reloadToken += 1 },
                        onOpenPluginSettings = onOpenPluginSettings,
                        onOpen = { item ->
                            opened = item
                            val key = feedItemKey(item)
                            readKeys = readKeys + key
                            scope.launch {
                                runCatching { FeedReadingStore.recordRead(context, key) }
                                    .onFailure { Logger.w("SubscriptionFeed", "阅读状态保存失败: ${it.message}") }
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
                        animatedVisibilityScope = this@AnimatedContent,
                    )
                }
            }
        }
    }
    if (webUrl != null) {
        com.android.purebilibili.feature.web.WebViewScreen(
            url = webUrl.orEmpty(),
            title = "原文",
            onBack = { webUrl = null },
        )
    }
    if (previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            publicContent = true,
            initialIndex = previewIndex.coerceIn(0, previewImages.lastIndex),
            sourceRect = previewSourceRect,
            sourceRects = previewSourceRects,
            // FeedArticleImage 用 MaterialTheme.shapes.medium 裁角，回位圆角保持一致
            sourceCornerRadiusDp = with(density) {
                (MaterialTheme.shapes.medium as? CornerBasedShape)?.topStart
                    ?.toPx(Size.Unspecified, this)?.toDp()?.value
            } ?: 12f,
            onDismiss = {
                previewImages = emptyList()
                previewSourceRects = emptyMap()
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SubscriptionFeedGrid(
    sources: List<FeedSource>,
    visibleItems: List<ParsedFeedItem>,
    loading: Boolean,
    unreadOnly: Boolean,
    refreshErrors: List<String>,
    onDismissRefreshErrors: () -> Unit,
    refreshProgress: Pair<Int, Int>?,
    selectedGroup: String?,
    onSelectGroup: (String?) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    readKeys: Set<String>,
    selectedSourceId: String?,
    onSelectSource: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenPluginSettings: () -> Unit,
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
            val groupNames = sources.map { it.group }.filter { it.isNotBlank() }.distinct()
            val hasGroups = groupNames.isNotEmpty()
            val displayedSources = if (selectedGroup == null) {
                sources
            } else {
                sources.filter { it.group == selectedGroup }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasGroups) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppAssistChip(
                            onClick = { onSelectGroup(null) },
                            label = { AppText(if (selectedGroup == null) "✓ 全部分组" else "全部分组") },
                        )
                        groupNames.forEach { group ->
                            AppAssistChip(
                                onClick = { onSelectGroup(if (selectedGroup == group) null else group) },
                                label = {
                                    AppText(
                                        if (selectedGroup == group) "✓ $group" else group
                                    )
                                },
                            )
                        }
                        if (sources.any { it.group.isBlank() }) {
                            AppAssistChip(
                                onClick = { onSelectGroup(if (selectedGroup == "") null else "") },
                                label = {
                                    AppText(
                                        if (selectedGroup == "") "✓ 未分组" else "未分组"
                                    )
                                },
                            )
                        }
                    }
                }
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
                    displayedSources.forEach { source ->
                        AppAssistChip(
                            onClick = { onSelectSource(source.id) },
                            label = { AppText(source.title) },
                        )
                    }
                    AppTextButton(onClick = onRefresh, enabled = !loading) {
                        val progress = refreshProgress
                        AppText(
                            when {
                                !loading -> "刷新"
                                progress == null || progress.second <= 0 -> "刷新中"
                                progress.first >= progress.second -> "刷新中 ${progress.second}/${progress.second}"
                                else -> "刷新中 ${progress.first}/${progress.second}"
                            }
                        )
                    }
                }
            }
        }
        if (refreshErrors.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine, key = "subscription_refresh_errors") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(
                            text = if (refreshErrors.size == 1) {
                                refreshErrors.first()
                            } else {
                                "${refreshErrors.size} 个订阅源刷新失败：${refreshErrors.first()}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        AppTextButton(onClick = onDismissRefreshErrors) {
                            AppText("知道了")
                        }
                    }
                }
            }
        }
        if (sources.isEmpty() && !loading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppText("还没有可用的订阅源。到插件中心添加 RSS 或 Atom 地址，或启用已添加的订阅。")
                    AppButton(
                        onClick = onOpenPluginSettings,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        AppText("去添加订阅")
                    }
                }
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
    //  [壁纸毛玻璃] 与首页视频卡片同一套壁纸模糊样式（详见 SubscriptionCardFrost）。
    val frost = rememberSubscriptionCardFrost()
    val palette = com.android.purebilibili.feature.home.components.cards.LocalWallpaperPalette.current
    val dynamicTintEnabled = com.android.purebilibili.feature.home.components.cards.LocalHomeCardDynamicTintEnabled.current
    val frostedGlassEnabled = com.android.purebilibili.feature.home.components.cards.LocalHomeCardFrostedGlassEnabled.current
    val baseColor = AppSurfaceTokens.cardContainer()
    val scrollTick = com.android.purebilibili.feature.home.components.cards.LocalHomeScrollTickProvider.current
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val viewportHeightPx = with(LocalDensity.current) { screenHeightDp.dp.toPx() }
    val cardCoordinates = remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
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
                .then(frost.backdropModifier())
                .onPlaced { cardCoordinates.value = it }
                .drawBehind {
                    val color = if (dynamicTintEnabled && !frost.useRealtimeFrosted) {
                        scrollTick?.invoke()
                        val coordinates = cardCoordinates.value
                        val yFraction = if (coordinates != null && coordinates.isAttached && viewportHeightPx > 0f) {
                            (coordinates.positionInRoot().y / viewportHeightPx).coerceIn(0f, 1f)
                        } else 0.5f
                        com.android.purebilibili.feature.home.components.cards.resolveVideoCardAmbientDrawSpec(
                            wallpaperPalette = palette,
                            yFraction = yFraction,
                            coverTint = null,
                            wallpaperTintEnabled = palette != null,
                            isDarkTheme = frost.isDarkCardTheme,
                            defaultContainerColor = baseColor,
                            defaultBorderColor = Color.White,
                            frostedGlassEnabled = frostedGlassEnabled,
                            dynamicTintEnabled = dynamicTintEnabled,
                        ).containerColor
                    } else frost.containerColor
                    drawRect(color)
                }
                .then(frost.borderModifier())
                .clickable(onClick = onClick)
        },
        // 材质已由 drawBackdrop + drawBehind 绘制，Surface 不再覆盖一层实心底色。
        color = Color.Transparent,
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
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 订阅卡片与回顾卡共用的壁纸毛玻璃外观（与首页 VideoCard 实时毛玻璃同参数）。 */
private class SubscriptionCardFrost(
    val useRealtimeFrosted: Boolean,
    val isDarkCardTheme: Boolean,
    private val backdrop: MiuixBackdrop?,
    private val shape: androidx.compose.ui.graphics.Shape,
    private val blurRadiusPx: Float,
    val containerColor: Color,
) {
    fun backdropModifier(): Modifier = if (!useRealtimeFrosted || backdrop == null) {
        Modifier
    } else {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(blurRadiusPx, blurRadiusPx) },
        )
    }

    fun borderModifier(): Modifier = if (!useRealtimeFrosted) {
        Modifier
    } else {
        Modifier.border(
            width = 0.5.dp,
            color = Color.White.copy(alpha = if (isDarkCardTheme) 0.14f else 0.22f),
            shape = shape,
        )
    }
}

@Composable
private fun rememberSubscriptionCardFrost(): SubscriptionCardFrost {
    val context = LocalContext.current
    val backdrop = LocalHomeWallpaperBackdrop.current
    val frostedGlassEnabled = com.android.purebilibili.feature.home.components.cards.LocalHomeCardFrostedGlassEnabled.current
    val surfaceMode = resolveHomeCardWallpaperSurfaceMode(
        dynamicTintEnabled = false,
        frostedGlassEnabled = frostedGlassEnabled,
        wallpaperVisible = LocalHomeWallpaperBackdropReady.current,
        wallpaperIsStatic = LocalHomeWallpaperIsStatic.current,
        backdropReady = LocalHomeWallpaperBackdropReady.current,
        blurEnabled = true,
        isDataSaverActive = false,
        lowBlurBudgetForced = false,
        sdkInt = Build.VERSION.SDK_INT,
    )
    return SubscriptionCardFrost(
        useRealtimeFrosted = surfaceMode == HomeCardWallpaperSurfaceMode.REALTIME_FROSTED && backdrop != null,
        isDarkCardTheme = AppSurfaceTokens.chromeBackground().luminance() < 0.5f,
        backdrop = backdrop,
        shape = AppShapes.container(ContainerLevel.Card),
        blurRadiusPx = with(LocalDensity.current) { 24.dp.toPx() },
        containerColor = if (surfaceMode == HomeCardWallpaperSurfaceMode.REALTIME_FROSTED) {
            //  与 VideoCard 实时毛玻璃一致的不透明度，保证标题文字可读。
            AppSurfaceTokens.cardContainer().copy(alpha = if (AppSurfaceTokens.chromeBackground().luminance() < 0.5f) 0.44f else 0.36f)
        } else if (frostedGlassEnabled) {
            AppSurfaceTokens.cardContainer().copy(
                alpha = if (AppSurfaceTokens.chromeBackground().luminance() < 0.5f) 0.38f else 0.34f
            )
        } else {
            AppSurfaceTokens.cardContainer()
        },
    )
}

@Composable
private fun FeedCoverImage(
    url: String,
    aspectRatio: Float,
) {
    AsyncImage(
        imageLoader = com.android.purebilibili.core.plugin.feed.PublicContentImageLoader.get(LocalContext.current),
        model = url,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.62f, 1.35f)),
        contentScale = ContentScale.Crop,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
private fun SubscriptionArticleScreen(
    item: ParsedFeedItem,
    onScrollInProgressChanged: (Boolean) -> Unit,
    scrollToTopRequestId: Int,
    contentPadding: PaddingValues,
    cachedBody: String?,
    isRead: Boolean,
    onReadChange: (Boolean) -> Unit,
    onFullBody: (String) -> Unit,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onOpenImages: (
        List<String>,
        Int,
        androidx.compose.ui.geometry.Rect?,
        Map<Int, androidx.compose.ui.geometry.Rect>
    ) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val articleScope = rememberCoroutineScope()
    val articleListState = rememberLazyListState()
    val latestOnScrollInProgressChanged by rememberUpdatedState(onScrollInProgressChanged)
    LaunchedEffect(articleListState) {
        snapshotFlow { articleListState.isScrollInProgress }.collect {
            latestOnScrollInProgressChanged(it)
        }
    }
    DisposableEffect(articleListState) {
        onDispose { latestOnScrollInProgressChanged(false) }
    }
    // Glass-only toolbar density; native MD3/Miuix keep their original actions and metrics.
    val glassReaderChrome = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    // Consume only new presses: opening another article must not replay an old request.
    var lastScrollToTopRequestId by remember(item.sourceId, item.id, item.link) {
        mutableIntStateOf(scrollToTopRequestId)
    }
    LaunchedEffect(scrollToTopRequestId) {
        if (scrollToTopRequestId != lastScrollToTopRequestId) {
            lastScrollToTopRequestId = scrollToTopRequestId
            // Match the recommendation tab's atomic return-to-top, regardless of distance.
            articleListState.scrollToItem(0)
        }
    }
    var articleHtml by remember(item.sourceId, item.id, item.link) {
        mutableStateOf(cachedBody ?: item.htmlContent.ifBlank { item.summary })
    }
    var loadingBody by remember(item.sourceId, item.id, item.link) { mutableStateOf(false) }
    var bodyLoadFailed by remember(item.sourceId, item.id, item.link) { mutableStateOf(false) }
    var retryToken by remember(item.sourceId, item.id, item.link) { mutableIntStateOf(0) }
    var pureReading by rememberSaveable(item.sourceId, item.id, item.link) { mutableStateOf(false) }
    var readerControlsVisible by rememberSaveable(item.sourceId, item.id, item.link) { mutableStateOf(true) }
    val readingChromeVisible = !pureReading || readerControlsVisible
    val bottomBarVisible = LocalBottomBarVisible.current
    val setBottomBarVisible by rememberUpdatedState(LocalSetBottomBarVisible.current)
    DisposableEffect(pureReading) {
        val wasVisible = bottomBarVisible
        onDispose { if (pureReading) setBottomBarVisible(wasVisible) }
    }
    LaunchedEffect(pureReading, bottomBarVisible) {
        if (pureReading && bottomBarVisible) setBottomBarVisible(false)
    }
    BackHandler(enabled = pureReading) {
        pureReading = false
        readerControlsVisible = true
    }
    val wallpaperEnabled by remember(context) { SettingsManager.getSubscriptionArticleWallpaperEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val configuredWallpaperUri by remember(context) { SettingsManager.getHomeWallpaperUri(context) }
        .collectAsStateWithLifecycle(initialValue = "")
    val splashWallpaperUri by remember(context) { SettingsManager.getSplashWallpaperUri(context) }
        .collectAsStateWithLifecycle(initialValue = "")
    val customArticleWallpaperUri by remember(context) {
        SettingsManager.getSubscriptionArticleWallpaperUri(context)
    }.collectAsStateWithLifecycle(initialValue = "")
    val wallpaperMode by remember(context) { SettingsManager.getHomeWallpaperEffectMode(context) }
        .collectAsStateWithLifecycle(initialValue = HomeWallpaperEffectMode.SOFT_BLUR)
    //  [独立壁纸] RSS 阅读页可设置专属壁纸；为空时回退首页壁纸链。
    val wallpaperUri = customArticleWallpaperUri.ifBlank {
        resolveHomeWallpaperUri(configuredWallpaperUri, splashWallpaperUri)
    }
    val articleBackground = MaterialTheme.colorScheme.surface
    val dataSaverActive = remember(context) { SettingsManager.isDataSaverActive(context) }
    val wallpaperAppearance = remember(wallpaperEnabled, wallpaperUri, wallpaperMode, articleBackground, dataSaverActive) {
        resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = wallpaperEnabled && wallpaperUri.isNotBlank(),
            effectMode = if (wallpaperMode == HomeWallpaperEffectMode.OFF) HomeWallpaperEffectMode.SOFT_BLUR else wallpaperMode,
            isDarkTheme = articleBackground.luminance() < 0.5f,
            isDataSaverActive = dataSaverActive,
            globalWallpaper = true,
        )
    }
    var actionsExpanded by remember { mutableStateOf(false) }
    var fontScale by remember { mutableIntStateOf(1) }
    var articleWallpaperPickerVisible by remember { mutableStateOf(false) }
    val openArticleWallpaperPicker = { articleWallpaperPickerVisible = true }
    // 文章笔记（本地）：按文章链接为键，写笔记/摘录/摘要草稿共用一个编辑器。
    val noteRevision by ArticleNoteStore.revision.collectAsStateWithLifecycle()
    val savedArticleNote = remember(noteRevision, item.link) { ArticleNoteStore.get(context, item.link) }
    var noteEditorVisible by remember { mutableStateOf(false) }
    var notePrefillDocument by remember { mutableStateOf<VideoNoteEditorDocument?>(null) }
    var savingNote by remember { mutableStateOf(false) }
    var excerptMode by remember { mutableStateOf(false) }
    val pendingExcerpts = remember { mutableStateListOf<String>() }
    val articlePlainText = remember(articleHtml) { feedPlainText(articleHtml) }
    val summaryDraft = remember(item.title, articlePlainText) {
        buildArticleSummaryDraft(articleTitle = item.title, articlePlainText = articlePlainText)
    }
    LaunchedEffect(Unit) {
        fontScale = com.android.purebilibili.core.store.SettingsManager
            .getSubscriptionArticleFontScale(context).first()
    }
    val textScale = remember(fontScale) { floatArrayOf(0.88f, 1f, 1.18f)[fontScale.coerceIn(0, 2)] }
    LaunchedEffect(item.sourceId, item.id, item.link, retryToken) {
        if (retryToken == 0 && (cachedBody != null || !feedBodyNeedsRemoteFetch(item))) return@LaunchedEffect
        loadingBody = true
        bodyLoadFailed = false
        try {
            fetchArticleHtml(item.link)
                .onSuccess { fetched ->
                    if (com.android.purebilibili.core.plugin.feed.feedPlainText(fetched).length >
                        com.android.purebilibili.core.plugin.feed.feedPlainText(articleHtml).length
                    ) {
                        articleHtml = fetched
                        onFullBody(fetched)
                    }
                }
                .onFailure {
                    bodyLoadFailed = true
                    Logger.w("SubscriptionFeed", "正文补全失败: ${it.message}")
                }
        } finally {
            loadingBody = false
        }
    }
    val blocks = remember(articleHtml) {
        parseFeedHtml(articleHtml, item.link).ifEmpty {
            listOf(FeedBlock.Paragraph(listOf(FeedInline.Text(cleanFeedSummary(item.summary).ifBlank { item.title }))))
        }
    }
    val readingBlocks = blocks
    val onReadingLinkClick: (String) -> Unit = onOpenUrl
    //  [阅读进展] 记录进度并在重开未读完的文章时自动续读。
    val articleKey = feedItemKey(item)
    LaunchedEffect(articleKey) {
        val savedPercent = runCatching { FeedReadingStore.readProgress(context, articleKey) }.getOrDefault(0)
        if (savedPercent in 10..95 && readingBlocks.isNotEmpty()) {
            kotlinx.coroutines.delay(400)
            articleListState.scrollToItem(
                index = (readingBlocks.size * savedPercent / 100).coerceIn(0, readingBlocks.size - 1),
            )
            android.widget.Toast.makeText(
                context,
                "已恢复到上次阅读位置 · ${savedPercent}%",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    LaunchedEffect(articleKey, readingBlocks.size) {
        if (readingBlocks.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val first = articleListState.firstVisibleItemIndex
            if (first <= 0) 0
            else ((first + 1) * 100 / readingBlocks.size).coerceIn(0, 100)
        }
            .sample(1200L)
            .collect { percent ->
                runCatching { FeedReadingStore.recordProgress(context, articleKey, percent) }
            }
    }
    val imageUrls = remember(blocks) {
        blocks.filterIsInstance<FeedBlock.Image>().map { it.url }.distinct()
    }
    val imageSourceRects = remember(item.sourceId, item.id, item.link) {
        mutableMapOf<Int, androidx.compose.ui.geometry.Rect>()
    }
    val layoutDirection = LocalLayoutDirection.current
    val articleTopBar: @Composable () -> Unit = {
        AppTopBar(
            title = "文章",
            navigationIcon = {
                AppIconButton(onClick = onBack) {
                    AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                }
            },
            actions = {
                if (!glassReaderChrome) {
                    AppIconButton(onClick = openArticleWallpaperPicker) {
                        AppIcon(
                            Icons.Outlined.Wallpaper,
                            contentDescription = "选择正文壁纸",
                        )
                    }
                }
                val readerActions: @Composable () -> Unit = {
                    AppIconButton(onClick = {
                        notePrefillDocument = null
                        noteEditorVisible = true
                    }) {
                        AppIcon(
                            Icons.Outlined.EditNote,
                            contentDescription = if (savedArticleNote != null) "编辑笔记" else "写笔记"
                        )
                    }
                    val cycleFontScale = {
                        fontScale = (fontScale + 1) % 3
                        articleScope.launch {
                            SettingsManager.setSubscriptionArticleFontScale(context, fontScale)
                        }
                        Unit
                    }
                    if (glassReaderChrome) {
                        AppIconButton(onClick = cycleFontScale) {
                            AppText("Aa", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        AppTextButton(onClick = cycleFontScale, modifier = Modifier.heightIn(min = 48.dp)) {
                            AppText("Aa")
                        }
                    }
                    AppIconButton(onClick = {
                        shareFeedArticle(context, item)
                    }) {
                        AppIcon(Icons.Default.Share, contentDescription = "分享文章")
                    }
                    com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { actionsExpanded = true }) {
                        AppIconButton(onClick = { actionsExpanded = true }) {
                            AppIcon(Icons.Default.MoreVert, contentDescription = "阅读选项")
                        }
                        AppDropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false },
                        ) {
                            AppDropdownMenuItem(
                                text = { AppText(if (pureReading) "退出纯净模式" else "纯净模式（隐藏界面组件）") },
                                onClick = {
                                    pureReading = !pureReading
                                    readerControlsVisible = !pureReading
                                    if (pureReading) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "已进入纯净模式，轻点正文空白处显示工具栏",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    actionsExpanded = false
                                },
                            )
                            AppDropdownMenuItem(
                                text = {
                                    AppText(
                                        if (wallpaperUri.isBlank()) "正文壁纸（请先设置首页壁纸）"
                                        else if (wallpaperEnabled) "正文壁纸：开" else "正文壁纸：关"
                                    )
                                },
                                enabled = wallpaperUri.isNotBlank(),
                                onClick = {
                                    articleScope.launch {
                                        SettingsManager.setSubscriptionArticleWallpaperEnabled(context, !wallpaperEnabled)
                                    }
                                    actionsExpanded = false
                                },
                            )
                            AppDropdownMenuItem(
                                text = {
                                    AppText(
                                        if (customArticleWallpaperUri.isBlank()) "选择阅读壁纸（当前跟随首页）"
                                        else "更换阅读壁纸"
                                    )
                                },
                                onClick = {
                                    actionsExpanded = false
                                    openArticleWallpaperPicker()
                                },
                            )
                            if (customArticleWallpaperUri.isNotBlank()) {
                                AppDropdownMenuItem(
                                    text = { AppText("恢复跟随首页壁纸") },
                                    onClick = {
                                        articleScope.launch {
                                            SettingsManager.setSubscriptionArticleWallpaperUri(context, "")
                                        }
                                        actionsExpanded = false
                                    },
                                )
                            }
                            AppDropdownMenuItem(
                                text = { AppText(if (excerptMode) "退出摘录模式" else "摘录模式（点选段落进笔记）") },
                                onClick = {
                                    excerptMode = !excerptMode
                                    if (!excerptMode) pendingExcerpts.clear()
                                    actionsExpanded = false
                                },
                            )
                            AppDropdownMenuItem(
                                text = { AppText(if (isRead) "标未读" else "标已读") },
                                onClick = {
                                    onReadChange(!isRead)
                                    actionsExpanded = false
                                },
                            )
                            AppDropdownMenuItem(
                                text = { AppText("复制正文") },
                                onClick = {
                                    copyFeedText(context, feedBlocksPlainText(readingBlocks).ifBlank { item.title })
                                    actionsExpanded = false
                                },
                            )
                            if (isHttpFeedUrl(item.link)) {
                                AppDropdownMenuItem(
                                    text = { AppText("重新读取正文") },
                                    enabled = !loadingBody,
                                    onClick = {
                                        retryToken += 1
                                        actionsExpanded = false
                                    },
                                )
                                AppDropdownMenuItem(
                                    text = { AppText("打开原文") },
                                    onClick = {
                                        onOpenUrl(item.link)
                                        actionsExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (glassReaderChrome) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { readerActions() }
                } else {
                    readerActions()
                }
            },
        )
    }
    if (articleWallpaperPickerVisible) {
        com.android.purebilibili.feature.profile.SplashWallpaperPickerSheet(
            target = com.android.purebilibili.feature.profile.WallpaperPickerTarget.SUBSCRIPTION_ARTICLE,
            onDismiss = { articleWallpaperPickerVisible = false },
        )
    }
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
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalGlobalWallpaperBackdropVisible provides false) {
                ImmersiveAppScaffold(
                    containerColor = Color.Transparent,
                    topBarSurfaceColor = articleBackground,
                    topBar = {
                        if (readingChromeVisible) {
                            articleTopBar()
                        } else {
                            // Pure reading hides controls, while preserving status-bar chrome
                            // and its existing progressive/Haze source and render-mode policy.
                            Spacer(
                                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                            )
                        }
                    },
                ) { scaffoldPadding ->
                    // The scaffold records its content for both progressive blur and Haze.
                    // Capture the opaque wallpaper base with the article, not transparent
                    // text alone; chrome stays outside this source so it cannot sample itself.
                    Box(Modifier.fillMaxSize()) {
                        HomeWallpaperBackdrop(
                            wallpaperUri = wallpaperUri,
                            appearance = wallpaperAppearance,
                            // The reader owns its background independently of home/global framing.
                            transformProvider = { com.android.purebilibili.core.ui.wallpaper.WallpaperTransform() },
                            baseColor = articleBackground,
                            isDataSaverActive = dataSaverActive,
                        )
                        LazyColumn(
                            state = articleListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (pureReading) Modifier.pointerInput(Unit) {
                                        detectTapGestures(onTap = { readerControlsVisible = !readerControlsVisible })
                                    } else Modifier
                                ),
                            contentPadding = PaddingValues(
                                start = contentPadding.calculateStartPadding(layoutDirection),
                                top = scaffoldPadding.calculateTopPadding() + if (glassReaderChrome) 16.dp else 8.dp,
                                end = contentPadding.calculateEndPadding(layoutDirection),
                                bottom = if (pureReading) scaffoldPadding.calculateBottomPadding() + 16.dp
                                    else contentPadding.calculateBottomPadding(),
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AppText(
                                        item.title,
                                        style = if (glassReaderChrome) MaterialTheme.typography.headlineSmall.copy(
                                            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.15f,
                                        ) else MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (!pureReading) {
                                        AppText(
                                            text = listOf(item.sourceTitle, item.author, formatFeedAge(item.publishedEpochSec))
                                                .filter { it.isNotBlank() }
                                                .joinToString(" · "),
                                            color = AppContentColors.secondaryText(),
                                        )
                                        if (loadingBody) {
                                            FeedBodySkeleton()
                                        }
                                        if (bodyLoadFailed) {
                                            AppText(
                                                "暂时无法补全正文，已保留现有内容。可在右上角菜单重新读取或打开原文。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppContentColors.secondaryText(),
                                            )
                                        }
                                    }
                                }
                            }
                            lazyListItems(readingBlocks) { block ->
                                val excerptText = if (excerptMode) feedBlockExcerptText(block) else null
                                val excerptSelected = excerptText != null && excerptText in pendingExcerpts
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().then(
                                        if (excerptText != null) {
                                            Modifier
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(
                                                    if (excerptSelected) MaterialTheme.colorScheme.primaryContainer
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    if (excerptSelected) {
                                                        pendingExcerpts.remove(excerptText)
                                                    } else {
                                                        pendingExcerpts.add(excerptText)
                                                    }
                                                }
                                        } else {
                                            Modifier
                                        }
                                    )) {
                                        FeedReadingBlock(
                                            block = block,
                                            textScale = textScale,
                                            imageUrls = imageUrls,
                                            imageSourceRects = imageSourceRects,
                                            onOpenUrl = onReadingLinkClick,
                                            onOpenImages = onOpenImages,
                                        )
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(28.dp)) }
                        }
                    }
                }
            }
            if (excerptMode) {
                // 摘录提示按钮悬浮于文章层之上：需要让出底栏（含导航栏）与音频播放条的
                // 高度，否则会被两者盖住。64dp ≈ 音频横条自身高度。
                val excerptBottomClearance = maxOf(
                    32.dp,
                    LocalBottomBarContentPadding.current + 64.dp,
                )
                AppButton(
                    onClick = {
                        notePrefillDocument = buildArticleExcerptDocument(
                            articleTitle = item.title,
                            excerpts = pendingExcerpts.toList()
                        )
                        noteEditorVisible = true
                        excerptMode = false
                        pendingExcerpts.clear()
                    },
                    enabled = pendingExcerpts.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = excerptBottomClearance),
                ) {
                    AppText(
                        if (pendingExcerpts.isEmpty()) "摘录模式：点选段落加入笔记"
                        else "已选 ${pendingExcerpts.size} 段 · 加入笔记"
                    )
                }
            }
            ArticleNoteEditorSheet(
                visible = noteEditorVisible,
                articleTitle = item.title,
                sourceTitle = item.sourceTitle,
                savedNoteTitle = savedArticleNote?.noteTitle,
                savedNoteContent = savedArticleNote?.content,
                prefillDocument = notePrefillDocument,
                summaryDraft = summaryDraft,
                saving = savingNote,
                onDismiss = {
                    noteEditorVisible = false
                    notePrefillDocument = null
                },
                onSave = { document ->
                    savingNote = true
                    articleScope.launch {
                        val encoded = VideoNoteContentCodec.encode(document)
                        withContext(Dispatchers.IO) {
                            ArticleNoteStore.save(
                                context,
                                SavedArticleNote(
                                    link = item.link,
                                    articleTitle = item.title,
                                    sourceTitle = item.sourceTitle,
                                    noteTitle = document.title,
                                    content = encoded.content,
                                    updatedAtEpochMs = System.currentTimeMillis()
                                )
                            )
                        }
                        savingNote = false
                        noteEditorVisible = false
                        notePrefillDocument = null
                        android.widget.Toast.makeText(context, "笔记已保存", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = {
                    articleScope.launch {
                        withContext(Dispatchers.IO) { ArticleNoteStore.remove(context, item.link) }
                        noteEditorVisible = false
                        android.widget.Toast.makeText(context, "笔记已删除", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

/** Shared article rendering; all material branches retain the RSS reader's original styles. */
@Composable
internal fun FeedReadingBlock(
    block: FeedBlock,
    textScale: Float,
    imageUrls: List<String>,
    imageSourceRects: MutableMap<Int, androidx.compose.ui.geometry.Rect>,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
    onOpenImages: (List<String>, Int, androidx.compose.ui.geometry.Rect?, Map<Int, androidx.compose.ui.geometry.Rect>) -> Unit,
) {
    when (block) {
        is FeedBlock.Heading -> SelectionContainer(modifier = modifier) {
            FeedInlineText(
                block.inlines,
                fontScale = textScale,
                onLinkClick = onOpenUrl,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.headlineSmall
                    2 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.titleMedium
                },
            )
        }
        is FeedBlock.Paragraph -> SelectionContainer(modifier = modifier) {
            FeedInlineText(block.inlines, fontScale = textScale, onLinkClick = onOpenUrl)
        }
        is FeedBlock.Quote -> SelectionContainer(modifier = modifier) {
            FeedInlineText(
                block.inlines,
                modifier = Modifier.padding(start = 12.dp),
                italic = true,
                fontScale = textScale,
                onLinkClick = onOpenUrl,
            )
        }
        is FeedBlock.Code -> SelectionContainer(modifier = modifier) {
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
            pageIndex = imageUrls.indexOf(block.url).coerceAtLeast(0),
            galleryRects = imageSourceRects,
            modifier = modifier,
            onClick = { rect ->
                val index = imageUrls.indexOf(block.url).coerceAtLeast(0)
                onOpenImages(imageUrls, index, rect, imageSourceRects.toMap())
            },
        )
        is FeedBlock.BulletList -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { line ->
                Row {
                    AppText("• ")
                    FeedInlineText(line, modifier = Modifier.weight(1f), fontScale = textScale, onLinkClick = onOpenUrl)
                }
            }
        }
        is FeedBlock.NumberedList -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, line ->
                Row {
                    AppText("${index + 1}. ")
                    FeedInlineText(line, modifier = Modifier.weight(1f), fontScale = textScale, onLinkClick = onOpenUrl)
                }
            }
        }
        is FeedBlock.EmbeddedLink -> AppTextButton(
            onClick = { onOpenUrl(block.url) },
            modifier = modifier.heightIn(min = 48.dp),
        ) { AppText(block.title) }
    }
}

@Composable
private fun FeedInlineText(
    inlines: List<FeedInline>,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    italic: Boolean = false,
    fontScale: Float = 1f,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val linkColor = AppContentColors.accent()
    val linkInteractionListener = remember(onLinkClick) {
        onLinkClick?.let { handler ->
            androidx.compose.ui.text.LinkInteractionListener { link ->
                (link as? LinkAnnotation.Url)?.url?.let(handler)
            }
        }
    }
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
                        linkInteractionListener,
                    )
                ) {
                    append(inline.text)
                }
            }
        }
    }
    val scaledStyle = if (fontScale != 1f) {
        style.copy(fontSize = style.fontSize * fontScale, lineHeight = style.lineHeight * fontScale)
    } else {
        style
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = scaledStyle.copy(lineHeight = scaledStyle.fontSize * 1.55f),
        color = AppContentColors.primaryText(),
    )
}

@Composable
private fun FeedBodySkeleton(modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0.92f, 1f, 0.78f, 1f, 0.64f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .background(barColor, MaterialTheme.shapes.small),
            )
        }
    }
}

@Composable
private fun FeedArticleImage(
    url: String,
    alt: String,
    pageIndex: Int,
    galleryRects: MutableMap<Int, androidx.compose.ui.geometry.Rect>,
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
            imageLoader = com.android.purebilibili.core.plugin.feed.PublicContentImageLoader.get(LocalContext.current),
            model = url,
            contentDescription = alt.ifBlank { "查看图片" },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clip(MaterialTheme.shapes.medium)
                .alpha(if (sourceHidden) 0f else 1f)
                .imagePreviewSourceBounds(sourceRect)
                .onGloballyPositioned { coordinates ->
                    galleryRects[pageIndex] = coordinates.boundsInWindow()
                }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = !sourceHidden,
                ) {
                    prepareImagePreviewSourceTransition(sourceRect.value)
                    onClick(sourceRect.value)
                },
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

private fun shareFeedArticle(context: android.content.Context, item: ParsedFeedItem) {
    val shareText = buildSubscriptionArticleShareText(item.title, item.sourceTitle, item.link)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, item.title)
        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "分享文章"))
}
