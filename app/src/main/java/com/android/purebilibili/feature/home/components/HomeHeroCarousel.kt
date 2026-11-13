@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import coil3.request.crossfade

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.MediaContrastPalette

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import com.android.purebilibili.core.util.homeCoverDecodePolicy
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.adaptive.adaptiveCardHoverEffect
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.recordNativeVideoCardLayer
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.feature.home.components.cards.RegisterVisibleHomeCover
import com.android.purebilibili.feature.home.components.cards.videoCardShellReturnChromeAlpha
import com.android.purebilibili.feature.home.components.cards.isVideoCardSharedSourceInstanceOwner
import com.android.purebilibili.feature.home.components.cards.isVideoCardSharedReturnTarget
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardScrolling
import com.android.purebilibili.feature.home.components.cards.VideoCardSourceInteraction
import com.android.purebilibili.feature.home.components.cards.VideoCardPreparedClickEffect
import com.android.purebilibili.feature.home.components.cards.rememberVideoCardCaptureState
import kotlinx.coroutines.launch
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.feature.home.resolveHomeHeroCarouselCardTransform
import com.android.purebilibili.feature.home.resolveHomeHeroCarouselItemKey
import com.android.purebilibili.feature.home.resolveHomeHeroCarouselItemOrNull
import com.android.purebilibili.feature.home.resolveHomeHeroCarouselLayout
import com.android.purebilibili.feature.home.resolveHomeHeroCarouselPreviewAlpha
import com.android.purebilibili.feature.home.homeHeroBannerEntranceSpec
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeHeroCarousel(
    videos: List<VideoItem>,
    autoplayEnabled: Boolean,
    onVideoClick: (VideoItem) -> Unit,
    onGetPreviewUrl: suspend (String, Long) -> String?,
    onGestureActiveChange: (Boolean) -> Unit = {},
    dissolvingVideos: Set<String> = emptySet(),
    onDissolveComplete: (String) -> Unit = {},
    onLongPress: ((VideoItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) return

    val pagerState = rememberPagerState { videos.size }
    val onGestureActiveChangeLatest = rememberUpdatedState(onGestureActiveChange)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    onGestureActiveChangeLatest.value(true)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        onGestureActiveChangeLatest.value(false)
                    }
                }
            }
            .padding(vertical = AppSpacingTokens.ExtraSmall)
    ) {
        val windowSizeClass = LocalWindowSizeClass.current
        val carouselLayout = remember(
            maxWidth,
            windowSizeClass.widthDp,
            windowSizeClass.heightDp,
        ) {
            resolveHomeHeroCarouselLayout(
                containerWidthDp = maxWidth.value,
                windowWidthDp = windowSizeClass.widthDp.value,
                windowHeightDp = windowSizeClass.heightDp.value,
            )
        }
        val carouselWidth = carouselLayout.widthDp.dp
        val aspectRatio = carouselLayout.aspectRatio
        val carouselHeight = carouselWidth / aspectRatio
        // 封面流布局：pager 占满整行，用 contentPadding 让两侧邻卡露边折向中心。
        val horizontalPeekPadding = ((maxWidth - carouselWidth) / 2).coerceAtLeast(0.dp)
        HorizontalPager(
            state = pagerState,
            key = { page ->
                resolveHomeHeroCarouselItemKey(videos, page, VideoItem::bvid)
            },
            pageSpacing = 10.dp,
            userScrollEnabled = videos.size > 1,
            beyondViewportPageCount = 1,
            contentPadding = PaddingValues(horizontal = horizontalPeekPadding),
            modifier = Modifier
                .fillMaxWidth()
                .height(carouselHeight)
                .align(Alignment.Center)

        ) { page ->
            val video = resolveHomeHeroCarouselItemOrNull(videos, page)
                ?: return@HorizontalPager
            // 连续值（currentPageOffsetFraction）只允许在图层/绘制阶段读取；
            // 组合期只读离散的 currentPage，滑动全程不再逐帧重组页面。
            val currentPage = pagerState.currentPage
            val pageOffsetProvider = {
                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .coerceIn(-1f, 1f)
            }
            val settledNearCenter by remember {
                derivedStateOf { pagerState.currentPageOffsetFraction.absoluteValue < 0.12f }
            }
            val activeForPlayback = autoplayEnabled && currentPage == page && settledNearCenter
            val isDissolving = video.bvid in dissolvingVideos
            // 新横幅淡入：被消散移除后，下一条在原位轻柔浮现，避免硬切跳变。
            val entranceAlpha = remember(video.bvid) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(video.bvid) {
                entranceAlpha.animateTo(1f, homeHeroBannerEntranceSpec())
            }
            Column(modifier = Modifier.graphicsLayer { alpha = entranceAlpha.value }) {
                HomeHeroCarouselCard(
                    video = video,
                    pageOffsetProvider = pageOffsetProvider,
                    isCenterPage = currentPage == page,
                    activeForPlayback = activeForPlayback,
                    aspectRatio = aspectRatio,
                    isDissolving = isDissolving,
                    onVideoClick = { onVideoClick(video) },
                    onDissolveComplete = { onDissolveComplete(video.bvid) },
                    onLongPress = onLongPress,
                    onGetPreviewUrl = onGetPreviewUrl,
                )

            }
        }

        if (videos.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppSpacingTokens.Small),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(videos.size) { index ->
                    val selected = index == pagerState.currentPage
                    val targetWidth = if (selected) AppSpacingTokens.Medium else AppSpacingTokens.ExtraSmall
                    val width by animateDpAsState(
                        targetValue = targetWidth,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "hero_dot_width_$index",
                    )
                    val targetAlpha = if (selected) 0.92f else 0.38f
                    val alpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "hero_dot_alpha_$index",
                    )
                    Box(
                        modifier = Modifier
                            .height(AppSpacingTokens.ExtraSmall)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                MediaContrastPalette.Foreground.copy(
                                    alpha = alpha
                                )
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeHeroCarouselCard(
    video: VideoItem,
    pageOffsetProvider: () -> Float,
    isCenterPage: Boolean,
    activeForPlayback: Boolean,
    aspectRatio: Float,
    isDissolving: Boolean,
    onVideoClick: () -> Unit,
    onDissolveComplete: () -> Unit,
    onLongPress: ((VideoItem) -> Unit)?,
    onGetPreviewUrl: suspend (String, Long) -> String?,
) {
    val context = LocalContext.current
    var previewUrl by remember(video.bvid, video.cid) { mutableStateOf<String?>(null) }
    LaunchedEffect(activeForPlayback, video.bvid, video.cid) {
        if (activeForPlayback && previewUrl == null && video.bvid.isNotBlank()) {
            previewUrl = kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                onGetPreviewUrl(video.bvid, video.cid)
            }
        }
    }

    // 整卡 sharedBounds：横幅卡片与详情 shell 同源。
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val sharedSourceInstanceId = rememberSaveable(video.bvid) {
        CardPositionManager.newVideoCardSourceInstanceId()
    }
    val sourceEnabled = LocalSharedTransitionEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val sourceInteraction = remember(video.bvid, video.cid, sourceRoute) {
        VideoCardSourceInteraction()
    }
    val sourceInteractionAllowed = LocalPageImageLoadingAllowed.current &&
        com.android.purebilibili.core.ui.rememberVisualWorkActive() &&
        LocalHomeCardScrolling.current?.value != true && !isDissolving
    val ownsSharedTransitionSource = isVideoCardSharedSourceInstanceOwner(
        sourceInstanceId = sharedSourceInstanceId,
        lastClickedSourceInstanceId = CardPositionManager.lastClickedVideoSourceInstanceId,
    )
    val isSharedReturnTarget = ownsSharedTransitionSource && isVideoCardSharedReturnTarget(
        video.bvid, sourceRoute, CardPositionManager.lastClickedVideoSourceKey,
    )
    val preparingInput = sourceInteractionAllowed && (isPressed || sourceInteraction.preparing)
    val prepareCardTransition = sourceEnabled && (preparingInput || isSharedReturnTarget)
    val captureState = if (prepareCardTransition) rememberVideoCardCaptureState(video.bvid) else null
    val captureScope = if (prepareCardTransition) rememberCoroutineScope() else null
    val useCardShellSharedBounds = prepareCardTransition &&
        (preparingInput || ownsSharedTransitionSource) &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null &&
        video.bvid.isNotBlank() &&
        !sourceRoute.isNullOrBlank()
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val cardShellMotionSpec = remember(
        sourceRoute,
        useCardShellSharedBounds,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = useCardShellSharedBounds,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }

    // 点击前写入 CardPositionManager，供返回 morph 对齐源卡 bounds。
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx: Float
    val screenHeightPx: Float
    val densityValue: Float
    remember(configuration.screenWidthDp, configuration.screenHeightDp, density) {
        Triple(
            with(density) { configuration.screenWidthDp.dp.toPx() },
            with(density) { configuration.screenHeightDp.dp.toPx() },
            density.density
        )
    }.let { (w, h, d) ->
        screenWidthPx = w
        screenHeightPx = h
        densityValue = d
    }

    // 保存布局坐标引用，实际边界仅在点击时读取。
    val cardCoordsRef = remember { object { var value: LayoutCoordinates? = null } }

    val cardShape = AppShapes.container(ContainerLevel.Card)
    val cardCornerDp = AppShapes.containerCornerDp(ContainerLevel.Card)
    val normalizedCoverUrl = remember(video.pic) { FormatUtils.fixImageUrl(video.pic) }
    var measuredCoverSize by remember(normalizedCoverUrl) {
        mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    }
    val stationaryCoverRequest = remember(normalizedCoverUrl) {
        ImageRequest.Builder(context)
            .data(normalizedCoverUrl)
            .homeCoverDecodePolicy()
            .crossfade(false)
            .memoryCacheKey(normalizedCoverUrl)
            .diskCacheKey(normalizedCoverUrl)
            .build()
    }

    // Use the same cover identity and return prefetch registry as ordinary
    // video cards so a detail return can settle into the hero without a
    // second decode or a one-frame placeholder.
    val coverRegistrationAllowed = LocalPageImageLoadingAllowed.current
    RegisterVisibleHomeCover(
        allowed = coverRegistrationAllowed,
        bvid = video.bvid,
        url = normalizedCoverUrl,
        cacheKey = normalizedCoverUrl,
        widthPx = measuredCoverSize.width,
        heightPx = measuredCoverSize.height,
    )

    // 记录源卡位置后进入详情。
    val clickAction: () -> Unit = heroCardClick@{
        if (CardPositionManager.isVideoNavigationDebounceActive()) {
            // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
            return@heroCardClick
        }
        cardCoordsRef.value?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()?.let { bounds ->
            CardPositionManager.recordVideoCardPosition(
                bvid = video.bvid,
                sourceRoute = sourceRoute,
                bounds = bounds,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                density = densityValue,
                sourceCornerDp = cardCornerDp.value.roundToInt(),
                isSingleColumn = true,
                sourceLayout = VideoCardSourceLayout.COVER_ONLY,
                coverBounds = bounds,
                sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                    title = video.title,
                    ownerName = video.owner.name,
                    ownerFaceUrl = video.owner.face,
                    viewText = FormatUtils.formatStat(video.stat.view.toLong()),
                    danmakuText = FormatUtils.formatStat(video.stat.danmaku.toLong()),
                    durationText = FormatUtils.formatDuration(video.duration),
                    infoPresentation = com.android.purebilibili.core.ui.transition
                        .resolveVideoCardSourceInfoPresentation(
                            publishTimeText = "",
                            showStatsInInfo = false,
                        ),
                    coverUrl = normalizedCoverUrl,
                    coverCacheKey = normalizedCoverUrl,
                    coverDecodeWidthPx = measuredCoverSize.width,
                    coverDecodeHeightPx = measuredCoverSize.height,
                ),
                sourceInstanceId = sharedSourceInstanceId,
            )
            if (captureState != null && captureScope != null) {
                val owner = captureState.freezeOnClick()
                if (owner != null) captureScope.launch { captureState.captureAfterEntry(owner) }
            }
        }
        onVideoClick()
    }

    val requestCardClick: () -> Unit = {
        if (!sourceEnabled) {
            clickAction()
        } else if (!isDissolving && !CardPositionManager.isVideoNavigationDebounceActive()) {
            sourceInteraction.requestClick()
        }
    }
    if (sourceInteraction.preparing) {
        VideoCardPreparedClickEffect(
            sourceInteraction, captureState, sourceEnabled && sourceInteractionAllowed, clickAction,
        )
    }
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "hero_card_press",
    )
    // 长按复用网格卡片的预览弹窗（立即播放/稍后再看/保存封面/分享/屏蔽/不感兴趣）。
    val hapticFeedback = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION)
    val longPressAction: (() -> Unit)? = onLongPress?.let { callback ->
        {
            hapticFeedback.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
            )
            if (!isDissolving) callback(video)
        }
    }

    com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard(
        isDissolving = isDissolving,
        onDissolveComplete = onDissolveComplete,
        cardId = video.bvid,
        preset = com.android.purebilibili.core.ui.animation.DissolveAnimationPreset.TELEGRAM_FAST,
        collapseAfterDissolve = false,
        publishGlobalDissolveState = false,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    ) {
        AppSurface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = AppSpacingTokens.None,
        shadowElevation = AppSpacingTokens.None,
        modifier = Modifier
            .testTag("home_hero_video_card")
            .adaptiveCardHoverEffect(shape = cardShape, hoverShadowElevation = AppSpacingTokens.None)
            .videoCardSourceAnchor(video.bvid, cardShape)
            // 折角覆盖层差只有 0.01，层级按离散的当前页固定即可；
            // 连续 z 会导致滑动全程逐帧重建 zIndex 修饰符。
            .zIndex(if (isCenterPage) 1f else 0.99f)
            .recordNativeVideoCardLayer(
                layer = captureState?.cardLayer,
                freezeProvider = { captureState?.freezeCard() == true },
                bvid = video.bvid,
                sourceRoute = sourceRoute,
                landingFeedbackEnabled = true,
                onRecorded = captureState?.let { { it.onCardRecorded() } },
            )
            .graphicsLayer {
                val transform = resolveHomeHeroCarouselCardTransform(pageOffsetProvider())
                transformOrigin = TransformOrigin(transform.pivotFractionX, 0.5f)
                cameraDistance = transform.cameraDistanceMultiplier * density.density
                translationX = transform.translationXFraction * size.width
                rotationY = transform.rotationY
                val pressMultiplier = 1f - pressProgress * 0.02f
                scaleX = transform.scale * pressMultiplier
                scaleY = transform.scale * pressMultiplier
                alpha = transform.alpha
            }
            .clip(cardShape)
            .onPlaced { coordinates -> cardCoordsRef.value = coordinates }
            .onSizeChanged { measuredCoverSize = it }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = requestCardClick,
                onLongClick = longPressAction,
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val transform = resolveHomeHeroCarouselCardTransform(pageOffsetProvider())
                        translationX = transform.contentParallaxFraction * size.width
                        scaleX = transform.contentScale
                        scaleY = transform.contentScale
                    }
            ) {
                PageAwareAsyncImage(
                    model = stationaryCoverRequest,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (activeForPlayback && previewUrl != null) {
                    MutedHeroVideoPlayer(url = previewUrl.orEmpty())
                }
            }
            // 覆盖层与整卡分别录制；闲置卡片不创建来源图层，原生与玻璃布局保持共用。
            Box(
                Modifier.matchParentSize().recordNativeVideoCardLayer(
                    layer = captureState?.coverOverlayLayer,
                    freezeProvider = { captureState?.freezeCover() == true },
                    bvid = video.bvid,
                    sourceRoute = sourceRoute,
                    onRecorded = captureState?.let { { it.onCoverRecorded() } },
                )
            ) {
                // 侧卡边缘遮罩：随偏移在绘制阶段建立/消退，中心卡为空绘制，不再逐帧重建渐变。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val transform = resolveHomeHeroCarouselCardTransform(pageOffsetProvider())
                            if (transform.edgeShadeAlpha > 0.001f) {
                                drawRect(
                                    if (transform.edgeShadeStartFromLeft) {
                                        Brush.horizontalGradient(
                                            0f to MediaContrastPalette.Scrim.copy(alpha = transform.edgeShadeAlpha),
                                            0.48f to Color.Transparent,
                                            1f to Color.Transparent
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            0f to Color.Transparent,
                                            0.52f to Color.Transparent,
                                            1f to MediaContrastPalette.Scrim.copy(alpha = transform.edgeShadeAlpha)
                                        )
                                    }
                                )
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.54f to Color.Transparent,
                                1f to MediaContrastPalette.Scrim.copy(alpha = 0.76f)
                            )
                        )
                )
                // 底部标题与统计（时长 · 播放 · 弹幕）
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = AppSpacingTokens.Large,
                            end = AppSpacingTokens.Large,
                            bottom = AppSpacingTokens.ExtraLarge,
                        )
                        .videoCardShellReturnChromeAlpha(
                            enabled = useCardShellSharedBounds,
                            bvid = video.bvid,
                            sourceRoute = sourceRoute,
                        )
                ) {
                    // 标题行（预览播放中显示播放图标）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeForPlayback) {
                            AppIcon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MediaContrastPalette.Foreground.copy(alpha = 0.9f),
                                modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro)
                            )
                            Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro))
                        }
                        AppText(
                            text = video.title,
                            color = MediaContrastPalette.Foreground,
                            // 背景图亮暗变化较大，给标题加柔和阴影保持可读性。
                            style = MaterialTheme.typography.titleMedium.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.72f),
                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                    blurRadius = 5f,
                                )
                            ),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (video.owner.name.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        AppText(
                            text = video.owner.name,
                            color = MediaContrastPalette.Foreground.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelMedium.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.62f),
                                    offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
                                    blurRadius = 4f,
                                )
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 统计信息：时长 · 播放量 · 弹幕
                    if (video.duration > 0 || video.stat.view > 0 || video.stat.danmaku > 0) {
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                        var separatorNeeded = false
                        // 时长
                        if (video.duration > 0) {
                            AppText(
                                text = FormatUtils.formatDuration(video.duration),
                                color = MediaContrastPalette.Foreground.copy(alpha = 0.65f),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    shadow = Shadow(Color.Black.copy(alpha = 0.58f), blurRadius = 3f)
                                ),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.wrapContentSize()
                            )
                            separatorNeeded = true
                        }
                        // 播放量
                        if (video.stat.view > 0) {
                            if (separatorNeeded) AppText(
                                " · ",
                                color = MediaContrastPalette.Foreground.copy(alpha = 0.5f),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                            AppText(
                                text = FormatUtils.formatStat(video.stat.view.toLong()) + "播放",
                                color = MediaContrastPalette.Foreground.copy(alpha = 0.65f),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.wrapContentSize()
                            )
                            separatorNeeded = true
                        }
                        // 弹幕
                        if (video.stat.danmaku > 0) {
                            if (separatorNeeded) AppText(
                                " · ",
                                color = MediaContrastPalette.Foreground.copy(alpha = 0.5f),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                            AppText(
                                text = FormatUtils.formatStat(video.stat.danmaku.toLong()) + "弹幕",
                                color = MediaContrastPalette.Foreground.copy(alpha = 0.65f),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.wrapContentSize()
                            )
                        }
                    }
                }
            }
            }
        }
    }
    }
}

@Composable
private fun MutedHeroVideoPlayer(url: String) {
    var hasRenderedFirstFrame by remember(url) { mutableStateOf(false) }
    DisposableVideoPlayer(
        url = url,
        onFirstFrame = { hasRenderedFirstFrame = true },
        onPlaybackError = { hasRenderedFirstFrame = false },
        modifier = Modifier.graphicsLayer {
            alpha = resolveHomeHeroCarouselPreviewAlpha(hasRenderedFirstFrame)
        },
    )
}
