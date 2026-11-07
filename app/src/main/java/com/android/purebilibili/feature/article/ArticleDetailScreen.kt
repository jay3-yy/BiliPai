package com.android.purebilibili.feature.article

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.skeleton.ArticleDetailSkeleton
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.repository.ArticleDetailUiModel
import com.android.purebilibili.data.repository.ArticleRepository
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden
import com.android.purebilibili.feature.dynamic.components.prepareImagePreviewSourceTransition
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.appDesktopFocusableItemVisuals

private const val ARTICLE_BANNER_CORNER_RADIUS_DP = 20f
private const val ARTICLE_BODY_IMAGE_CORNER_RADIUS_DP = 18f

private sealed interface ArticleDetailUiState {
    data object Loading : ArticleDetailUiState
    data class Success(val article: ArticleDetailUiModel) : ArticleDetailUiState
    data class Error(val message: String) : ArticleDetailUiState
}

private data class ArticleImagePreviewRequest(
    val images: List<String>,
    val initialIndex: Int,
    val sourceRect: Rect?,
    val sourceRects: Map<Int, Rect>,
    val sourceCornerRadiusDp: Float
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ArticleDetailScreen(
    articleId: Long,
    initialTitle: String,
    modifier: Modifier = Modifier,
    transitionEnabled: Boolean = false,
    onBack: (Boolean) -> Unit,
    onUserClick: (Long) -> Unit,
    contentTransitionReady: Boolean = true,
    backHandlerEnabled: Boolean = true,
    onArticleClick: (Long, String) -> Unit = { _, _ -> },
    onLinkClick: ((String) -> Unit)? = null,
    onOpenWeb: ((String) -> Unit)? = null,
    onCollectionClick: ((Long, String, Long) -> Unit)? = null,
    openCommentRootRpid: Long = 0L,
    openCommentTargetRpid: Long = 0L,
    onAdjacentArticleClick: ((Long, String) -> Unit)? = null,
) {
    val readingContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val chromeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val navigationSettings by remember(readingContext) {
        com.android.purebilibili.core.store.SettingsManager.getAppNavigationSettings(readingContext)
    }.collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.AppNavigationSettings())
    val bottomBarMode = navigationSettings.bottomBarVisibilityMode
    val autoHideBottomBar = bottomBarMode == com.android.purebilibili.core.store.SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE
    var bottomBarScrollVisible by remember(articleId) { mutableStateOf(true) }
    LaunchedEffect(bottomBarMode) { bottomBarScrollVisible = true }
    val bottomBarEnabled = bottomBarMode != com.android.purebilibili.core.store.SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN
    val bottomBarVisible = bottomBarEnabled && (!autoHideBottomBar || bottomBarScrollVisible)
    // 背景只录制正文视口；底栏保持兄弟层，避免玻璃和磨砂采样到自身。
    val bottomBarBackdrop = if (bottomBarEnabled && chromeConfig.liquidGlassEnabled) com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource() else null
    val bottomBarHaze = if (bottomBarEnabled && !chromeConfig.liquidGlassEnabled && chromeConfig.bottomBarBlurEnabled) {
        com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState()
    } else null
    val readingPreferences by remember(readingContext) { ArticleReadingStore.observePreferences(readingContext) }
        .collectAsStateWithLifecycle(initialValue = remember(readingContext) { ArticleReadingStore.readPreferences(readingContext) })
    var showReadingSettings by remember { mutableStateOf(false) }
    var readFromStartToken by remember(articleId) { mutableIntStateOf(0) }
    var showOutline by remember(articleId) { mutableStateOf(false) }
    var bodyReadingRequested by rememberSaveable(articleId, openCommentRootRpid, openCommentTargetRpid) { mutableStateOf(false) }
    if (showReadingSettings) {
        ArticleReadingSettingsDialog(
            preferences = readingPreferences,
            onChange = { value ->
                ArticleReadingStore.savePreferences(readingContext, value)
            },
            onDismiss = { showReadingSettings = false },
        )
    }
    val comments: com.android.purebilibili.feature.dynamic.DynamicViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(key = "article-comments:$articleId")
    val articleSubReplyState by comments.subReplyState.collectAsStateWithLifecycle()
    val showArticleInteractionBar = bottomBarVisible && !articleSubReplyState.visible
    var commentJumpToken by rememberSaveable(articleId, openCommentRootRpid, openCommentTargetRpid) {
        mutableIntStateOf(if (openCommentRootRpid > 0L || openCommentTargetRpid > 0L) 1 else 0)
    }
    var retryToken by rememberSaveable { mutableIntStateOf(0) }
    val backLabel = stringResource(R.string.common_back)
    val retryLabel = stringResource(R.string.common_retry)
    val loadFailedMessage = stringResource(R.string.dynamic_detail_load_failed)
    val account by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val loadedUiState by androidx.compose.runtime.key(articleId, account) {
        produceState<ArticleDetailUiState>(
            initialValue = ArticleDetailUiState.Loading,
            key1 = articleId,
            key2 = retryToken,
        ) {
            value = ArticleDetailUiState.Loading
            value = ArticleRepository.getArticleDetail(articleId).fold(
                onSuccess = { article -> ArticleDetailUiState.Success(article) },
                onFailure = { error -> ArticleDetailUiState.Error(error.message ?: loadFailedMessage) },
            )
        }
    }

    val uiState = if (contentTransitionReady) loadedUiState else ArticleDetailUiState.Loading
    val contentRevealAlpha = com.android.purebilibili.core.ui.transition.rememberSharedCardDetailRevealAlpha(
        entryKey = articleId,
        contentTransitionReady = contentTransitionReady,
        contentLoaded = uiState !is ArticleDetailUiState.Loading,
    )
    val screenTitle = when (val state = uiState) {
        is ArticleDetailUiState.Success -> state.article.title
        else -> initialTitle.ifBlank { "专栏详情" }
    }
    var sharedReturnReady by remember(articleId, account) { mutableStateOf(false) }
    var showShare by remember(articleId, account) { mutableStateOf(false) }
    val shareMenuSource = androidx.compose.runtime.key(articleId, account) {
        com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
    }
    if (showShare) {
        (uiState as? ArticleDetailUiState.Success)?.article?.let { article ->
            com.android.purebilibili.feature.share.ContentShareSheet(
                source = com.android.purebilibili.feature.share.ContentSharePayload(
                    key = "article:$articleId", title = article.title,
                    url = "https://www.bilibili.com/read/cv$articleId",
                    cover = article.bannerUrl.orEmpty(), author = article.authorName,
                    authorId = article.authorMid, source = 6, resourceId = articleId,
                ),
                onDismiss = { showShare = false },
                glassSource = shareMenuSource,
            )
        }
    }


    // Container entries let the host seek the whole card during predictive back.
    BackHandler(enabled = backHandlerEnabled) {
        onBack(sharedReturnReady)
    }

    AppScaffold(
        modifier = modifier,
        blurContentReady = uiState !is ArticleDetailUiState.Loading,
        bottomBar = {
            (uiState as? ArticleDetailUiState.Success)?.article?.let { article ->
                ArticleInteractionBar(
                    article = article, comments = comments,
                    onLinkClick = onLinkClick,
                    onCommentClick = { commentJumpToken++ }, onUserClick = onUserClick,
                    openCommentRootRpid = openCommentRootRpid,
                    openCommentTargetRpid = openCommentTargetRpid,
                    visible = showArticleInteractionBar,
                    backdrop = bottomBarBackdrop?.backdrop,
                    hazeState = bottomBarHaze,
                )
            }
        },
        topBar = {
            AppTopBar(
                title = screenTitle,
                actions = {
                    if (uiState is ArticleDetailUiState.Success) {
                        Box(Modifier.appGlassMenuSource(shareMenuSource, onOpen = { showShare = true })) {
                            AppIconButton(onClick = { showShare = true }) {
                                AppIcon(com.android.purebilibili.core.ui.rememberAppShareIcon(), contentDescription = "分享专栏")
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    ArticleMoreMenu(
                        articleId = articleId, onOpenWeb = onOpenWeb,
                        onReadingSettings = { showReadingSettings = true },
                        outlineEnabled = uiState is ArticleDetailUiState.Success,
                        onOpenOutline = { showOutline = true },
                        onReadFromStart = {
                            ArticleReadingStore.savePosition(readingContext, articleId, ArticleReadingPosition())
                            commentJumpToken = 0
                            bodyReadingRequested = true
                            readFromStartToken++
                        },
                    )
                },
                navigationIcon = {
                    AppIconButton(onClick = { onBack(sharedReturnReady) }) {
                        AppIcon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                }
            )
        }
    ) { paddingValues ->
        // 遮挡铰链时整页内容落入最大安全区，软折痕允许跨越；长文保持居中单栏。
        com.android.purebilibili.core.ui.adaptive.AppHingeSafeContent(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (uiState is ArticleDetailUiState.Loading) 1f else contentRevealAlpha.value
            }.then(bottomBarBackdrop?.modifier ?: Modifier)
                .then(if (bottomBarHaze != null) Modifier.hazeSourceCompat(bottomBarHaze) else Modifier)
                .then(if (bottomBarBackdrop != null || bottomBarHaze != null) {
                    Modifier.sharedGlobalWallpaperBackground(MaterialTheme.colorScheme.background)
                } else Modifier),
        ) {
        when (val state = uiState) {
            ArticleDetailUiState.Loading -> {
                ArticleDetailSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            is ArticleDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppText(
                            text = state.message,
                            color = AppContentColors.secondaryText()
                        )
                        AppButton(onClick = { retryToken++ }) {
                            AppText(retryLabel)
                        }
                    }
                }
            }

            is ArticleDetailUiState.Success -> {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    ArticleDetailContent(
                        article = state.article,
                        paddingValues = paddingValues,
                        transitionEnabled = transitionEnabled,
                        onSharedReturnReadyChange = { sharedReturnReady = it },
                        onUserClick = onUserClick,
                        onArticleClick = onArticleClick,
                        onAdjacentArticleClick = onAdjacentArticleClick ?: onArticleClick,
                        comments = comments,
                        commentJumpToken = commentJumpToken,
                        readingPreferences = readingPreferences,
                        readFromStartToken = readFromStartToken,
                        autoHideBottomBar = autoHideBottomBar,
                        bottomBarVisible = bottomBarVisible,
                        onBottomBarVisibilityChange = { bottomBarScrollVisible = it },
                        commentEntry = (openCommentRootRpid > 0L || openCommentTargetRpid > 0L) && !bodyReadingRequested,
                        showOutline = showOutline,
                        onDismissOutline = { showOutline = false },
                        onOutlineNavigation = {
                            commentJumpToken = 0
                            bodyReadingRequested = true
                        },
                        onLinkClick = onLinkClick,
                        onCollectionClick = onCollectionClick,
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ArticleDetailContent(
    article: ArticleDetailUiModel,
    paddingValues: PaddingValues,
    transitionEnabled: Boolean,
    onSharedReturnReadyChange: (Boolean) -> Unit,
    onUserClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onAdjacentArticleClick: (Long, String) -> Unit,
    comments: com.android.purebilibili.feature.dynamic.DynamicViewModel,
    commentJumpToken: Int,
    readingPreferences: ArticleReadingPreferences,
    readFromStartToken: Int,
    autoHideBottomBar: Boolean,
    bottomBarVisible: Boolean,
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    commentEntry: Boolean,
    showOutline: Boolean,
    onDismissOutline: () -> Unit,
    onOutlineNavigation: () -> Unit,
    onLinkClick: ((String) -> Unit)?,
    onCollectionClick: ((Long, String, Long) -> Unit)?,
) {
    val articleLinkListener = remember(onLinkClick) {
        onLinkClick?.let { open ->
            androidx.compose.ui.text.LinkInteractionListener { annotation ->
                (annotation as? androidx.compose.ui.text.LinkAnnotation.Url)?.url?.let(open)
            }
        }
    }
    // 预约会话由文章持有，卡片滚出视口时仍可完成回写；退出文章后关闭旧会话。
    val reserveSessions = remember(article.articleId, article.dynamicId, article.blocks) {
        article.blocks.filterIsInstance<ArticleContentBlock.MediaCard>()
            .filter { (it.card.reserve?.rid ?: 0L) > 0L }
            .distinctBy { it.card.reserve?.rid }
            .associate { media ->
                requireNotNull(media.card.reserve).rid to
                    com.android.purebilibili.feature.dynamic.components.DynamicReserveCardSession(media.card)
            }
    }
    androidx.compose.runtime.DisposableEffect(reserveSessions) {
        onDispose { reserveSessions.values.forEach { it.close() } }
    }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var pendingVoteId by remember(article.articleId) { mutableStateOf<Long?>(null) }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val bodyHeaderCount = (if (!article.bannerUrl.isNullOrBlank()) 1 else 0) + 1 +
        (if ((article.collection?.id ?: 0L) > 0L) 1 else 0)
    val collectionId = article.collection?.id?.takeIf { it > 0L }
    val readingContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(article.articleId, collectionId) {
        if (collectionId != null) ArticleReadingStore.saveCollectionArticle(readingContext, collectionId, article.articleId)
    }
    var collectionRetry by remember(article.articleId, collectionId) { mutableIntStateOf(0) }
    val collectionResult by androidx.compose.runtime.key(article.articleId, collectionId) {
        produceState<Result<com.android.purebilibili.data.model.response.ArticleCollectionData>?>(
            initialValue = null, key1 = collectionRetry,
        ) {
            value = null
            if (collectionId != null) value = ArticleRepository.getCollection(collectionId)
        }
    }
    val listState = rememberArticleReadingListState(article.articleId, bodyHeaderCount, article.blocks.size, commentEntry)
    val readingScope = rememberCoroutineScope()
    val outline = remember(article.blocks) { buildArticleOutline(article.blocks) }
    if (showOutline) {
        val currentBlock by remember(outline, listState, bodyHeaderCount, article.blocks.size) {
            derivedStateOf {
                resolveArticleOutlineCurrentBlock(outline, listState.firstVisibleItemIndex - bodyHeaderCount, article.blocks.size)
            }
        }
        androidx.compose.foundation.text.selection.DisableSelection {
            ArticleOutlineDialog(
                entries = outline,
                currentBlock = currentBlock,
                onSelect = { entry ->
                    val target = resolveArticleOutlineListIndex(entry, bodyHeaderCount, article.blocks.size)
                    onDismissOutline()
                    if (target != null) {
                        onOutlineNavigation()
                        readingScope.launch { listState.scrollToItem(target) }
                    }
                },
                onDismiss = onDismissOutline,
            )
        }
    }
    LaunchedEffect(readFromStartToken) {
        if (readFromStartToken > 0) listState.scrollToItem(0)
    }
    val articleBackground = MaterialTheme.colorScheme.background
    val articleTextColor = resolveArticleTextColor(AppContentColors.primaryText(), MaterialTheme.colorScheme.onBackground, articleBackground)
    val articleQuoteColor = resolveArticleTextColor(AppContentColors.secondaryText(), articleTextColor, articleBackground)
    val articleCodeColor = resolveArticleTextColor(AppContentColors.primaryText(), articleTextColor, AppSurfaceTokens.surfaceContainer())
    val bodyStyle = MaterialTheme.typography.bodyLarge.withArticleReadingPreferences(readingPreferences)
        .copy(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto)
    val headingStyle = MaterialTheme.typography.titleLarge.withArticleReadingPreferences(readingPreferences)
        .copy(lineBreak = LineBreak.Heading)
    val codeStyle = MaterialTheme.typography.bodyMedium.withArticleReadingPreferences(readingPreferences)
    val sharedTransitionEnabled = transitionEnabled &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null &&
        article.articleId > 0L
    val coverTransitionKey = remember(article.articleId) {
        resolveArticleSharedTransitionKey(article.articleId, ArticleSharedElementSlot.COVER)
    }
    // 用 derivedStateOf 而不是 remember(firstVisibleItemIndex)：
    // 后者把「每滚过一个 item」都变成一次重组，而这里真正关心的只是一个布尔值，
    // 它在整个滚动过程中至多翻转一次。derivedStateOf 只在结果变化时才失效。
    val sharedReturnReady by remember(listState) {
        derivedStateOf {
            shouldEnableArticleSharedReturn(
                firstVisibleItemIndex = listState.firstVisibleItemIndex
            )
        }
    }
    val bodyImageUrls = remember(article.blocks) {
        collectArticleBodyImageUrls(article.blocks)
    }
    val hasBannerImage = !article.bannerUrl.isNullOrBlank()
    val previewImages = remember(article.bannerUrl, bodyImageUrls) {
        buildList {
            article.bannerUrl?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(bodyImageUrls)
        }
    }
    val bodyImageIndexOffset = if (hasBannerImage) 1 else 0
    val bottomSafeAreaPadding = resolveArticleDetailBottomPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    var bannerSourceRect by remember(article.bannerUrl) {
        mutableStateOf<Rect?>(null)
    }
    val bodyImageSourceRects = remember(article.blocks) {
        mutableStateMapOf<Int, Rect>()
    }
    fun currentPreviewSourceRects(): Map<Int, Rect> = buildMap {
        if (hasBannerImage) bannerSourceRect?.let { put(0, it) }
        var pageIndex = bodyImageIndexOffset
        article.blocks.forEachIndexed { blockIndex, block ->
            if (block is ArticleContentBlock.Image) {
                bodyImageSourceRects[blockIndex]?.let { put(pageIndex, it) }
                pageIndex++
            }
        }
    }
    var imagePreviewRequest by remember(article.bannerUrl, article.blocks) {
        mutableStateOf<ArticleImagePreviewRequest?>(null)
    }
    val bannerSourceHidden = isImagePreviewSourceHidden(bannerSourceRect)
    val baseBannerModifier = Modifier
        .fillMaxWidth()
        .clip(AppShapes.container(ContainerLevel.Floating))
        .onGloballyPositioned { coordinates ->
            bannerSourceRect = coordinates.boundsInWindow()
        }
        .alpha(if (bannerSourceHidden) 0f else 1f)
        .clickable(
            interactionSource = null,
            indication = null,
            enabled = previewImages.isNotEmpty(),
        ) {
            prepareImagePreviewSourceTransition(bannerSourceRect)
            imagePreviewRequest = ArticleImagePreviewRequest(
                images = previewImages,
                initialIndex = 0,
                sourceRect = bannerSourceRect,
                sourceRects = currentPreviewSourceRects(),
                sourceCornerRadiusDp = ARTICLE_BANNER_CORNER_RADIUS_DP
            )
        }
    val bannerModifier = if (sharedTransitionEnabled && !article.bannerUrl.isNullOrBlank()) {
        with(requireNotNull(sharedTransitionScope)) {
            baseBannerModifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = coverTransitionKey),
                animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 260f) },
                clipInOverlayDuringTransition = OverlayClip(AppShapes.container(ContainerLevel.Floating))
            )
        }
    } else {
        baseBannerModifier
    }
    LaunchedEffect(sharedReturnReady) {
        onSharedReturnReadyChange(sharedReturnReady)
    }

    val commentContent = rememberArticleCommentContent(article, comments, onUserClick, onLinkClick) { images, index ->
        imagePreviewRequest = ArticleImagePreviewRequest(images, index, null, emptyMap(), 0f)
    }
    val commentListState = rememberLazyListState()
    val bottomChromeOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val bodyScrollConnection = com.android.purebilibili.core.ui.rememberBottomBarScrollHideConnection(
        chromeScrollOffset = bottomChromeOffset, autoHideEnabled = autoHideBottomBar,
        isAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < com.android.purebilibili.core.ui.BottomBarScrollHideTopRevealPx },
        isActivePage = true,
        onVisibilityIntent = { onBottomBarVisibilityChange(it == com.android.purebilibili.core.ui.BottomBarScrollHideIntent.SHOW) },
    )
    val commentScrollConnection = com.android.purebilibili.core.ui.rememberBottomBarScrollHideConnection(
        chromeScrollOffset = bottomChromeOffset, autoHideEnabled = autoHideBottomBar,
        isAtTop = { commentListState.firstVisibleItemIndex == 0 && commentListState.firstVisibleItemScrollOffset < com.android.purebilibili.core.ui.BottomBarScrollHideTopRevealPx },
        isActivePage = true,
        onVisibilityIntent = { onBottomBarVisibilityChange(it == com.android.purebilibili.core.ui.BottomBarScrollHideIntent.SHOW) },
    )
    val stableBottomBarPadding = com.android.purebilibili.core.ui.rememberStickyBottomBarContentPadding(
        autoHideEnabled = autoHideBottomBar,
        liveBottomPadding = paddingValues.calculateBottomPadding(),
        isBottomBarVisible = bottomBarVisible,
    )
    val commentState by comments.commentUiState.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val splitComments = maxWidth >= 840.dp
        val commentHeaderIndex = bodyHeaderCount + article.blocks.size + if (collectionId != null) 1 else 0
        LaunchedEffect(commentJumpToken, splitComments) {
            if (commentJumpToken > 0) {
                if (splitComments) commentListState.animateScrollToItem(0)
                else listState.animateScrollToItem(commentHeaderIndex)
            }
        }
        LaunchedEffect(listState, commentListState, splitComments) {
            snapshotFlow {
                val activeList = if (splitComments) commentListState else listState
                val last = activeList.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = activeList.layoutInfo.totalItemsCount
                val current = commentState
                if (last >= (if (splitComments) 0 else commentHeaderIndex) && last >= total - 4 &&
                    current.hasMore && !current.isLoading && !current.isRefreshing &&
                    !current.isLoadingMore && current.refreshError == null
                ) current.nextPage else null
            }.distinctUntilChanged().filterNotNull().collect { comments.loadMoreComments() }
        }
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(if (splitComments) 0.6f else 1f)
                    .fillMaxHeight()
                    .responsiveContentWidth().nestedScroll(bodyScrollConnection),
                state = listState,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    end = 20.dp,
                    bottom = maxOf(bottomSafeAreaPadding, stableBottomBarPadding + 24.dp)
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!article.bannerUrl.isNullOrBlank()) {
                    item {
                        AsyncImage(
                            model = article.bannerUrl,
                            contentDescription = article.title,
                            modifier = bannerModifier,
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppText(
                            text = article.title,
                            style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
                            fontWeight = FontWeight.Bold
                        )
                        if (article.authorName.isNotBlank() || article.publishTime.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (article.authorFace.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .appDesktopFocusableItemVisuals(enabled = article.authorMid > 0)
                                            .clickable(
                                                enabled = article.authorMid > 0,
                                                role = Role.Button,
                                            ) {
                                                if (article.authorMid > 0) onUserClick(article.authorMid)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AsyncImage(
                                            model = article.authorFace,
                                            contentDescription = article.authorName,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.padding(start = if (article.authorFace.isNotBlank()) 12.dp else 0.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (article.authorName.isNotBlank()) {
                                        AppText(
                                            text = article.authorName,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.clickable(enabled = article.authorMid > 0) {
                                                if (article.authorMid > 0) onUserClick(article.authorMid)
                                            }
                                        )
                                    }
                                    if (article.publishTime.isNotBlank()) {
                                        AppText(
                                            text = article.publishTime,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.secondaryText()
                                        )
                                    }
                                }
                            }
                        }
                        if (shouldShowArticleSummary(article.summary, article.blocks)) {
                            AppText(
                                text = article.summary,
                                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                                color = AppContentColors.secondaryText()
                            )
                        }
                    }
                }

                article.collection?.takeIf { it.id > 0L }?.let { collection ->
                    item(key = "article-collection") {
                        androidx.compose.foundation.text.selection.DisableSelection {
                            ArticleCollectionEntry(collection, article.articleId, onArticleClick,
                                onCollectionClick = onCollectionClick, onAuthorClick = onUserClick)
                        }
                    }
                }

                itemsIndexed(article.blocks, key = { index, _ -> index }) { index, block ->
                    when (block) {
                        is ArticleContentBlock.Heading -> {
                            if (block.spans.isEmpty()) {
                                AppText(
                                    text = block.text,
                                    style = headingStyle,
                                    color = articleTextColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                AppText(
                                    text = buildArticleAnnotatedString(
                                        spans = block.spans,
                                        defaultColor = articleTextColor,
                                        baseFontSize = headingStyle.fontSize,
                                        linkListener = articleLinkListener,
                                        explicitFontScale = readingPreferences.fontScale,
                                        backgroundColor = articleBackground,
                                    ),
                                    style = headingStyle,
                                    color = articleTextColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        is ArticleContentBlock.Paragraph -> {
                            if (block.spans.isEmpty()) {
                                AppText(
                                    text = block.text,
                                    style = bodyStyle,
                                    color = articleTextColor
                                )
                            } else {
                                AppText(
                                    text = buildArticleAnnotatedString(
                                        spans = block.spans,
                                        defaultColor = articleTextColor,
                                        baseFontSize = bodyStyle.fontSize,
                                        linkListener = articleLinkListener,
                                        explicitFontScale = readingPreferences.fontScale,
                                        backgroundColor = articleBackground,
                                    ),
                                    style = bodyStyle,
                                    color = articleTextColor,
                                )
                            }
                        }

                        is ArticleContentBlock.Quote -> {
                            if (block.spans.isEmpty()) {
                                AppText(
                                    text = block.text,
                                    style = bodyStyle,
                                    color = articleQuoteColor,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            } else {
                                AppText(
                                    text = buildArticleAnnotatedString(
                                        spans = block.spans,
                                        defaultColor = articleQuoteColor,
                                        baseFontSize = bodyStyle.fontSize,
                                        linkListener = articleLinkListener,
                                        explicitFontScale = readingPreferences.fontScale,
                                        backgroundColor = articleBackground,
                                    ),
                                    style = bodyStyle,
                                    color = articleQuoteColor,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }

                        is ArticleContentBlock.ListBlock -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                block.items.forEachIndexed { itemIndex, item ->
                                    val prefix = if (block.ordered) "${itemIndex + 1}. " else "• "
                                    AppText(
                                        text = prefix + item,
                                        style = bodyStyle,
                                        color = articleTextColor
                                    )
                                }
                            }
                        }

                        is ArticleContentBlock.Code -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.container(ContainerLevel.Card))
                                    .background(AppSurfaceTokens.surfaceContainer())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                AppText(
                                    text = block.content,
                                    style = codeStyle.copy(fontFamily = FontFamily.Monospace),
                                    color = articleCodeColor
                                )
                            }
                        }

                        is ArticleContentBlock.MediaCard -> {
                            androidx.compose.foundation.text.selection.DisableSelection {
                                ArticleMediaCard(
                                    media = block,
                                    dynamicId = article.dynamicId,
                                    onReserveClick = comments::toggleDynamicReserve,
                                    reserveSession = block.card.reserve?.rid?.let { reserveSessions[it] },
                                    onVoteClick = { pendingVoteId = it },
                                    onOpenUrl = { url ->
                                        if (onLinkClick != null) onLinkClick(url)
                                        else runCatching { uriHandler.openUri(url) }
                                    },
                                )
                            }
                        }

                        is ArticleContentBlock.Image -> {
                            val sourceHidden = isImagePreviewSourceHidden(bodyImageSourceRects[index])
                            val imageAspectRatio = resolveArticleImageAspectRatio(
                                width = block.width,
                                height = block.height
                            )
                            AsyncImage(
                                model = block.url,
                                contentDescription = "${article.title}-$index",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (imageAspectRatio != null) {
                                            Modifier.aspectRatio(imageAspectRatio)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clip(AppShapes.container(ContainerLevel.Card))
                                    .onGloballyPositioned { coordinates ->
                                        bodyImageSourceRects[index] = coordinates.boundsInWindow()
                                    }
                                    .alpha(if (sourceHidden) 0f else 1f)
                                    .clickable(interactionSource = null, indication = null) {
                                        val payload = resolveArticleImagePreviewPayload(
                                            blocks = article.blocks,
                                            tappedBlockIndex = index
                                        ) ?: return@clickable
                                        prepareImagePreviewSourceTransition(bodyImageSourceRects[index])
                                        imagePreviewRequest = ArticleImagePreviewRequest(
                                            images = previewImages,
                                            initialIndex = payload.initialIndex + bodyImageIndexOffset,
                                            sourceRect = bodyImageSourceRects[index],
                                            sourceRects = currentPreviewSourceRects(),
                                            sourceCornerRadiusDp = ARTICLE_BODY_IMAGE_CORNER_RADIUS_DP
                                        )
                                    },
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
                article.collection?.takeIf { it.id > 0L }?.let { collection ->
                    item(key = "article-collection-navigation") {
                        androidx.compose.foundation.text.selection.DisableSelection {
                            ArticleCollectionNavigation(
                                collection = collection,
                                currentArticleId = article.articleId,
                                result = collectionResult,
                                onAdjacentArticleClick = onAdjacentArticleClick,
                                onRetry = { collectionRetry++ },
                                onArticleClick = onArticleClick,
                                onCollectionClick = onCollectionClick,
                                onAuthorClick = onUserClick,
                            )
                        }
                    }
                }
                if (!splitComments) commentContent()
            }
            if (splitComments) {
                LazyColumn(
                    state = commentListState,
                    modifier = Modifier.weight(0.4f).fillMaxHeight().nestedScroll(commentScrollConnection),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 20.dp,
                        top = paddingValues.calculateTopPadding() + 12.dp,
                        bottom = stableBottomBarPadding + 24.dp,
                    ),
                    content = commentContent,
                )
            }
        }
    }

    pendingVoteId?.let { voteId ->
        com.android.purebilibili.feature.dynamic.components.DynamicVoteDialog(
            voteId = voteId, dynamicId = article.dynamicId,
            onDismiss = { pendingVoteId = null },
        )
    }

    imagePreviewRequest?.let { request ->
        ImagePreviewDialog(
            images = request.images,
            initialIndex = request.initialIndex,
            sourceRect = request.sourceRect,
            sourceRects = request.sourceRects,
            sourceCornerRadiusDp = request.sourceCornerRadiusDp,
            onDismiss = { imagePreviewRequest = null }
        )
    }
}
