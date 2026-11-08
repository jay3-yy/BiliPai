package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.offset

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.AppButton
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material.icons.outlined.Close
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.*
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.ui.animation.DissolvableVideoCard
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.performance.TrackScrollJank
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.util.responsiveContentWidth
import kotlinx.collections.immutable.ImmutableSet
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.home.components.HomeHeroCarousel
import com.android.purebilibili.feature.home.components.HomeHeroCarouselImmersive
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.LocalHomeScrollTickProvider
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardVisibility
import com.android.purebilibili.feature.home.components.cards.VideoCardVisibilityState
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardScrolling
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardAuxiliaryWorkAllowed
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardTransitionRunning
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard
import com.android.purebilibili.feature.common.ListLoadError

import androidx.compose.ui.Alignment
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalDensity

internal fun resolveHomeCategoryVideoGridKey(
    video: VideoItem,
    duplicateOrdinal: Int
): String {
    val primaryId = when {
        video.bvid.isNotBlank() -> video.bvid
        video.id > 0L -> "${video.id}_${video.aid.takeIf { it > 0L } ?: video.cid}"
        video.aid > 0L -> "aid_${video.aid}"
        video.cid > 0L -> "cid_${video.cid}"
        else -> "${video.owner.mid}_${video.title.hashCode()}_${video.pubdate}"
    }
    return "home_video_${primaryId}_$duplicateOrdinal"
}

/**
 * Keeps a video's lazy-grid identity stable when unrelated items are inserted or removed before it.
 * Duplicate API entries still receive distinct keys through their occurrence ordinal.
 */
internal fun resolveHomeCategoryVideoGridKeys(videos: List<VideoItem>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return videos.map { video ->
        val identity = resolveHomeHeroCarouselDedupKey(video)
        val duplicateOrdinal = occurrences.getOrDefault(identity, 0)
        occurrences[identity] = duplicateOrdinal + 1
        resolveHomeCategoryVideoGridKey(video, duplicateOrdinal)
    }
}

/**
 * Rows for list screens that explicitly align card shells. The home grid uses per-video
 * items; callers of this helper can still start a fresh row after a full-width divider.
 */
internal fun resolveHomeFeedAlignedRows(
    itemCount: Int,
    columns: Int,
    dividerIndex: Int? = null,
): List<IntRange> {
    if (itemCount <= 0) return emptyList()
    val safeColumns = columns.coerceAtLeast(1)
    val safeDividerIndex = dividerIndex?.takeIf { it in 1 until itemCount }
    return buildList {
        var rowStart = 0
        while (rowStart < itemCount) {
            val naturalEndExclusive = (rowStart + safeColumns).coerceAtMost(itemCount)
            val rowEndExclusive = safeDividerIndex
                ?.takeIf { it > rowStart && it < naturalEndExclusive }
                ?: naturalEndExclusive
            add(rowStart until rowEndExclusive)
            rowStart = rowEndExclusive
        }
    }
}

internal fun resolveHomeFeedVideoRangesByLazyKey(videoGridKeys: List<String>): Map<String, IntRange> =
    videoGridKeys.mapIndexed { index, key -> key to (index..index) }.toMap()

internal fun resolveHomeOldContentGridItemIndex(
    anchorVideoIndex: Int,
    itemCount: Int,
    dividerIndex: Int?,
    headerItemCount: Int,
): Int? {
    if (anchorVideoIndex !in 0 until itemCount) return null
    val dividerItemBeforeAnchor = dividerIndex != null && anchorVideoIndex >= dividerIndex
    return headerItemCount + anchorVideoIndex + if (dividerItemBeforeAnchor) 1 else 0
}

internal fun resolveHomeHeroCarouselDedupKey(video: VideoItem): String {
    return when {
        video.bvid.isNotBlank() -> "bvid_${video.bvid}"
        video.id > 0L -> "id_${video.id}"
        video.aid > 0L -> "aid_${video.aid}"
        video.cid > 0L -> "cid_${video.cid}"
        else -> "fallback_${video.owner.mid}_${video.title.hashCode()}_${video.pubdate}"
    }
}

internal fun shouldRequestHomeCategoryLoadMore(
    totalItems: Int,
    lastVisibleItemIndex: Int,
    isLoading: Boolean,
    hasMore: Boolean,
    hasVisibleContent: Boolean,
    preloadItemCount: Int = 4,
): Boolean {
    return hasVisibleContent &&
        totalItems > 0 &&
        lastVisibleItemIndex >= totalItems - preloadItemCount &&
        !isLoading &&
        hasMore
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeCategoryPageContent(
    category: HomeCategory,
    categoryState: CategoryContent,
    memoryRecovery: HomeFeedMemorySnapshot? = null,
    onMemoryRecoveryConsumed: (String, Boolean) -> Unit = { _, _ -> },
    isActive: Boolean,
    /** 页面是否在屏幕上。图片加载只看这个，不看数据同步是否就绪。 */
    pageOnScreen: Boolean = isActive,
    visibleDuringNavigation: Boolean = false,
    gridState: HomeFeedGridState,
    gridColumns: Int,
    contentPadding: PaddingValues,
    dissolvingVideos: ImmutableSet<String>,
    followingMids: ImmutableSet<Long>,
    showOnlineCount: Boolean,
    coverRequestSpec: HomeCoverRequestSpec,
    coverPreloadAllowed: Boolean = false,
    coverPreloadAheadCount: Int = 0,
    onVideoClick: (HomeVideoClickRequest) -> Unit,
    onUpClick: (Long) -> Unit = {},
    onLiveClick: (Long, String, String) -> Unit,
    /** 顶栏直播已统一到 LiveList；首页内嵌直播分类仅作跳转入口。 */
    onOpenLiveHome: () -> Unit = {},
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onRetryRefresh: () -> Unit,
    onDismissVideo: (VideoItem) -> Unit,
    onWatchLater: (String, Long) -> Unit,
    onDissolveComplete: (String) -> Unit,
    onDissolveReflowStarted: (String) -> Unit = {},
    dissolveReflowEnabled: Boolean = true,
    longPressCallback: ((VideoItem) -> Unit)? = null, // [Feature] Long Press
    displayMode: Int,
    cardAnimationEnabled: Boolean,
    cardMotionTier: MotionTier = MotionTier.Normal,
    backToTopSquishActive: Boolean = false,
    cardTransitionEnabled: Boolean,
    isReturningFromVideoDetail: Boolean = false,
    pageScrollInProgress: State<Boolean>? = null,
    isQuickReturningFromVideoDetail: Boolean = false,
    smartVisualGuardEnabled: Boolean = false,
    isDataSaverActive: Boolean,
    preferLowQualityCover: Boolean = false,
    compactStatsOnCover: Boolean = false,
    showCoverGlassBadges: Boolean = false,
    showInfoGlassBadges: Boolean = false,
    badgeEffectMode: com.android.purebilibili.core.store.HomeCardBadgeEffectMode =
        com.android.purebilibili.core.store.HomeCardBadgeEffectMode.OFF,
    infoGlassMode: com.android.purebilibili.core.store.HomeCardInfoGlassMode =
        com.android.purebilibili.core.store.HomeCardInfoGlassMode.OFF,
    wallpaperTintEnabled: Boolean = false,
    wallpaperEffectMode: HomeWallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
    showUpBadges: Boolean = true,
    showUpAvatars: Boolean = true,
    showPublishTime: Boolean = true,
    homeDurationStyle: HomeDurationStyle = HomeDurationStyle.OUTSIDE_COVER,
    homeFeedCardStyle: HomeFeedCardStyle = HomeFeedCardStyle.BILIPAI,
    showFullVideoCardContent: Boolean = false,
    homeHeroCarouselEnabled: Boolean = true,
    homeHeroCarouselAutoplayEnabled: Boolean = false,
    onHeroCarouselGestureActiveChange: (Boolean) -> Unit = {},
    onGetPreviewUrl: suspend (String, Long) -> String? = { _, _ -> null },
    oldContentAnchorBvid: String? = null,
    oldContentStartIndex: Int? = null,
    oldContentLocatorRefreshKey: Long = 0L,
    onOldContentDividerClick: () -> Unit = {},
    overlayPillColors: com.android.purebilibili.feature.home.HomeGlassResolvedColors,
    todayWatchEnabled: Boolean = false,
    todayWatchMode: TodayWatchMode = TodayWatchMode.RELAX,
    todayWatchPlan: TodayWatchPlan? = null,
    todayWatchLoading: Boolean = false,
    todayWatchError: String? = null,
    todayWatchCollapsed: Boolean = false,
    todayWatchCardConfig: TodayWatchCardUiConfig = TodayWatchCardUiConfig(),
    onTodayWatchModeChange: (TodayWatchMode) -> Unit = {},
    onTodayWatchCollapsedChange: (Boolean) -> Unit = {},
    onTodayWatchRefresh: () -> Unit = {},
    onTodayWatchUpClick: (Long) -> Unit = {},
    popularSubCategory: PopularSubCategory = PopularSubCategory.COMPREHENSIVE,
    onPopularSubCategoryChange: (PopularSubCategory) -> Unit = {},
    onWeeklySeriesClick: () -> Unit = {},
    onTodayWatchVideoClick: (VideoItem) -> Unit = { video ->
        onVideoClick(
            HomeVideoClickRequest(
                bvid = video.bvid,
                cid = video.cid,
                coverUrl = video.pic,
                title = video.title,
                isVerticalVideo = video.resolveIsVerticalForNavigation(),
                source = HomeVideoClickSource.TODAY_WATCH,
                sourceRoute = resolveHomeCategoryVideoSourceRoute(HomeCategory.RECOMMEND)
            )
        )
    },
    firstGridItemModifier: Modifier = Modifier,
    /**
     * 首屏骨架 item 提供者。非空时网格只铺骨架，与真实卡片共用同一个 LazyLayout 与同一个
     * gridState；数据到达后由真实卡片原地替换，不会整屏重建、也不丢滚动锚点。
     */
    initialSkeletonContent: (HomeFeedGridScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var cardReflowActive by remember(category) { mutableStateOf(false) }
    LaunchedEffect(dissolvingVideos, dissolveReflowEnabled) {
        if (!dissolveReflowEnabled) {
            cardReflowActive = false
        } else if (dissolvingVideos.isEmpty()) {
            kotlinx.coroutines.delay(500L)
            cardReflowActive = false
        }
    }
    val sourceRoute = remember(category) {
        resolveHomeCategoryVideoSourceRoute(category)
    }
    val returningFromDetail = rememberUpdatedState(isReturningFromVideoDetail)
    val quickReturningFromDetail = rememberUpdatedState(isQuickReturningFromVideoDetail)
    val transitionBackground =
        com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState.current
    val returnSourceKey = remember(transitionBackground) {
        {
            transitionBackground.sourceKeyProvider()
                ?: com.android.purebilibili.core.util.CardPositionManager.lastClickedVideoSourceKey
        }
    }
    val widthSizeClass = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current
        .windowSizeClass.widthSizeClass
    val cardLayout = remember(homeFeedCardStyle, gridColumns, widthSizeClass) {
        resolveHomeFeedCardLayout(
            style = homeFeedCardStyle,
            gridColumns = gridColumns,
            widthSizeClass = widthSizeClass,
        )
    }
    val horizontalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp)
    TrackScrollJank(
        scrollableState = gridState,
        stateName = "home:feed:${category.name.lowercase()}"
    )
    val configuration = LocalConfiguration.current
    val estimatedCardWidthDp = remember(gridColumns, cardLayout.itemSpacingDp, configuration.screenWidthDp) {
        val totalSpacing = cardLayout.itemSpacingDp * (gridColumns - 1)
        val contentPaddingTotal = 16f
        ((configuration.screenWidthDp - contentPaddingTotal - totalSpacing) / gridColumns).coerceAtLeast(100f)
    }

    // Check for load more
    val latestCategoryState by rememberUpdatedState(categoryState)
    val latestIsActive by rememberUpdatedState(isActive)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val shouldLoadMore by remember(gridState, gridColumns) {
        derivedStateOf {
            val totalItems = gridState.totalItemsCount
            // Staggered lanes do not guarantee that the last visible entry has the greatest
            // adapter index. Use the maximum across lanes so pagination cannot stall.
            val lastVisibleItemIndex = gridState.lastVisibleItemIndex
            val current = latestCategoryState
            latestIsActive && current.loadMoreError == null && shouldRequestHomeCategoryLoadMore(
                totalItems = totalItems,
                lastVisibleItemIndex = lastVisibleItemIndex,
                isLoading = current.isLoading,
                hasMore = current.hasMore,
                hasVisibleContent = current.videos.isNotEmpty() ||
                    current.liveRooms.isNotEmpty() ||
                    current.followedLiveRooms.isNotEmpty(),
                preloadItemCount = gridColumns * 2,
            )
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) latestOnLoadMore()
    }

    val carouselVideos = remember(category, categoryState.videos, homeHeroCarouselEnabled) {
        if (category == HomeCategory.RECOMMEND && homeHeroCarouselEnabled) {
            // Stop scanning once the banner is full; disabled banners do no selection work.
            selectHomeHeroCarouselItems(categoryState.videos) {
                it.contentType != "bangumi" && it.contentType != "picture"
            }
        } else {
            emptyList()
        }
    }
    val showHeroCarousel = shouldShowHomeHeroCarousel(
        enabled = homeHeroCarouselEnabled,
        category = category,
        itemCount = carouselVideos.size
    )
    val visibleGridVideos = remember(categoryState.videos, carouselVideos, showHeroCarousel) {
        if (showHeroCarousel) {
            excludeHomeHeroCarouselItems(
                items = categoryState.videos,
                carouselItems = carouselVideos,
                keySelector = ::resolveHomeHeroCarouselDedupKey
            )
        } else {
            categoryState.videos
        }
    }
    val videoGridKeys = remember(visibleGridVideos) {
        resolveHomeCategoryVideoGridKeys(visibleGridVideos)
    }
    // 旧内容定位与分界共用一次查找，滚动启停等无关重组不再遍历全部视频。
    val oldContentAnchorIndex = remember(visibleGridVideos, oldContentAnchorBvid) {
        oldContentAnchorBvid?.let { anchor ->
            visibleGridVideos.indexOfFirst { it.bvid == anchor }.takeIf { it >= 0 }
        }
    }
    val oldContentVideoIndex = oldContentAnchorIndex ?: oldContentStartIndex
    val oldContentDividerIndex = oldContentAnchorIndex?.takeIf { it > 0 }
        ?: oldContentStartIndex?.takeIf { it > 0 && it < visibleGridVideos.size }
    val oldContentGridItemIndex = remember(
        category,
        visibleGridVideos,
        gridColumns,
        showFullVideoCardContent,
        oldContentVideoIndex,
        oldContentDividerIndex,
        showHeroCarousel,
        todayWatchEnabled,
        categoryState.refreshError,
        contentPadding,
        cardLayout.verticalItemSpacingDp,
    ) {
        if (category != HomeCategory.RECOMMEND) {
            null
        } else {
            oldContentVideoIndex?.let { videoIndex ->
                resolveHomeOldContentGridItemIndex(
                    anchorVideoIndex = videoIndex,
                    itemCount = visibleGridVideos.size,
                    dividerIndex = oldContentDividerIndex,
                    headerItemCount = (if (showHeroCarousel) 1 else 0) +
                        (if (todayWatchEnabled) 1 else 0) +
                        (if (categoryState.refreshError != null) 1 else 0) +
                        (if (contentPadding.calculateTopPadding() > cardLayout.verticalItemSpacingDp.dp) 1 else 0),
                )
            }
        }
    }
    val oldContentLocatorScope = rememberCoroutineScope()
    var oldContentLocatorDismissed by remember(oldContentLocatorRefreshKey) {
        mutableStateOf(false)
    }

    var oldContentLocatorDissolving by remember(oldContentLocatorRefreshKey) {
        mutableStateOf(false)
    }

    // Share key extraction for cover load ranges and full-width header visibility.
    val visibleLazyItemKeys = remember(gridState) {
        derivedStateOf { gridState.visibleItemKeys() }
    }
    val alignedDividerIndex = remember(
        category, visibleGridVideos, oldContentAnchorBvid, oldContentStartIndex, oldContentAnchorIndex,
    ) {
        if (category != HomeCategory.RECOMMEND) null
        else if (oldContentAnchorBvid != null) {
            oldContentAnchorIndex?.takeIf { it > 0 }
        } else oldContentStartIndex?.takeIf { it > 0 && it < visibleGridVideos.size }
    }
    val memoryRecoveryDensity = LocalDensity.current
    val memoryHeaderItemCount =
        (if (gridState.regularLayout && contentPadding.calculateTopPadding() > cardLayout.verticalItemSpacingDp.dp) 1 else 0) +
        (if (categoryState.refreshError != null) 1 else 0) +
        (if (category == HomeCategory.RECOMMEND && showHeroCarousel) 1 else 0) +
        (if (category == HomeCategory.RECOMMEND && todayWatchEnabled) 1 else 0) +
        (if (category == HomeCategory.POPULAR) 1 else 0) +
        (if (category == HomeCategory.POPULAR && popularSubCategory == PopularSubCategory.WEEKLY) 1 else 0)
    LaunchedEffect(memoryRecovery?.id, videoGridKeys, memoryHeaderItemCount, gridState, pageOnScreen) {
        val saved = memoryRecovery ?: return@LaunchedEffect
        if (!pageOnScreen || saved.category != category.name ||
            (category == HomeCategory.POPULAR && saved.popularSubCategory != popularSubCategory.name)
        ) return@LaunchedEffect
        val anchor = saved.anchor
        var located = false
        if (anchor != null) {
            val target = resolveHomeMemoryAnchorIndex(anchor, videoGridKeys, memoryHeaderItemCount, alignedDividerIndex)
            if (target != null) {
                snapshotFlow { gridState.totalItemsCount }.first { it > target }
                val offset = resolveHomeMemoryScrollOffsetPx(
                    anchor, gridState.regularLayout, gridState.viewportWidthPx, gridColumns, memoryRecoveryDensity,
                )
                gridState.scrollToItem(target, offset)
                located = true
            } else gridState.scrollToItem(0)
        }
        onMemoryRecoveryConsumed(saved.id, located)
    }
    // Both layouts use one lazy item per video; headers never enter this mapping.
    val videoRangesByLazyKey = remember(videoGridKeys) {
        resolveHomeFeedVideoRangesByLazyKey(videoGridKeys)
    }
    val visibleVideoRange = remember(visibleLazyItemKeys, videoRangesByLazyKey) {
        derivedStateOf { resolveHomeVisibleVideoRange(visibleLazyItemKeys.value, videoRangesByLazyKey) }
    }
    val coverLoadRange = remember(
        visibleVideoRange, visibleGridVideos.size, gridColumns, isDataSaverActive,
    ) {
        derivedStateOf {
            val visible = visibleVideoRange.value
            resolveHomeCoverLoadRange(
                visible?.first, visible?.last, visibleGridVideos.size, gridColumns, isDataSaverActive,
            )
        }
    }
    val coverLoadingActive = pageOnScreen || visibleDuringNavigation
    val pageImageWorkAllowed = LocalPageImageLoadingAllowed.current && isActive
    val visualWorkActive = rememberVisualWorkActive()
    val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val componentMotionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val auxiliaryWorkAllowed = rememberHomeCardAuxiliaryWorkAllowed(
        pageActive = pageImageWorkAllowed,
        isScrolling = gridState.isScrollInProgress || pageScrollInProgress?.value == true,
        visualWorkActive = visualWorkActive,
    )
    val renderVideoCard: @Composable (Int, VideoItem, Modifier, String) -> Unit = { index, video, itemModifier, lazyItemKey ->
        val returnFlags by rememberHomeCardReturnFlags(
            video.bvid, sourceRoute, returnSourceKey, returningFromDetail, quickReturningFromDetail,
        )
        val cardVisibility = remember(lazyItemKey) { VideoCardVisibilityState() }
        val cardVisible = cardVisibility.visible
        val coverInLoadWindow by remember(coverLoadRange, index) {
            derivedStateOf { coverLoadRange.value?.contains(index) == true }
        }
        val cardImageWorkAllowed = LocalPageImageLoadingAllowed.current && coverLoadingActive && cardVisible
        val cardCoverWorkAllowed = LocalPageImageLoadingAllowed.current && coverLoadingActive && coverInLoadWindow
        val isNativeNonVideoCard = video.contentType in setOf("bangumi", "picture") ||
            (video.dynamicId.isNotBlank() && !video.bvid.startsWith("BV", ignoreCase = true))
        val isDissolving = video.bvid in dissolvingVideos
        // Disabled motion has no animation state or graphics layer; enabled cards keep
        // their spring mounted through the complete return to the resting scale.
        val cardSquishModifier = if (cardAnimationEnabled && componentMotionEnabled &&
            !reduceMotion && visualWorkActive && isActive && cardVisible
        ) {
            val squishAmount = when (cardMotionTier) {
                MotionTier.Reduced -> 0.004f
                MotionTier.Normal -> 0.008f
                MotionTier.Enhanced -> 0.009f
            }
            val squishActive = cardVisible && isActive && backToTopSquishActive
            val cardSquishScaleX = animateFloatAsState(
                targetValue = if (squishActive) 1f + squishAmount else 1f,
                animationSpec = spring(
                    dampingRatio = if (squishActive) {
                        Spring.DampingRatioNoBouncy
                    } else {
                        0.64f
                    },
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "home_back_to_top_card_squish_x",
            )
            val cardSquishScaleY = animateFloatAsState(
                targetValue = if (squishActive) 1f - squishAmount else 1f,
                animationSpec = spring(
                    dampingRatio = if (squishActive) {
                        Spring.DampingRatioNoBouncy
                    } else {
                        0.64f
                    },
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "home_back_to_top_card_squish_y",
            )
            Modifier.graphicsLayer {
                scaleX = cardSquishScaleX.value
                scaleY = cardSquishScaleY.value
            }
        } else Modifier

        MaybeDissolvableVideoCard(
            isDissolving = isDissolving,
            onDissolveComplete = { onDissolveComplete(video.bvid) },
            cardId = video.bvid,
            preset = DissolveAnimationPreset.TELEGRAM_FAST,
            publishGlobalDissolveState = false,
            reflowDuringFinalTail = dissolveReflowEnabled,
            onReflowStarted = {
                cardReflowActive = dissolveReflowEnabled
                onDissolveReflowStarted(video.bvid)
            },
            preserveContentLayerWhenIdle = cardTransitionEnabled,
            propagateMinConstraints = gridState.regularLayout,
            modifier = itemModifier
                .then(cardSquishModifier)
                .then(if (index == 0) firstGridItemModifier else Modifier)
        ) {
            CompositionLocalProvider(
                LocalHomeCardVisibility provides cardVisibility,
                LocalPageImageLoadingAllowed provides cardImageWorkAllowed,
                LocalHomeCardCoverLoadingAllowed provides cardCoverWorkAllowed,
                LocalHomeCardAuxiliaryWorkAllowed provides (cardImageWorkAllowed && auxiliaryWorkAllowed),
            ) {
                when (displayMode) {
                    1 -> StoryVideoCard(
                        video = video,
                        modifier = Modifier,
                        index = index,
                        animationEnabled = cardAnimationEnabled,
                        motionTier = cardMotionTier,
                        transitionEnabled = cardTransitionEnabled && !isNativeNonVideoCard,
                        isReturningFromVideoDetail = returnFlags.returning,
                        isQuickReturningFromVideoDetail = returnFlags.quickReturn,
                        scrollLiteModeEnabled = false,
                        isDataSaverActive = isDataSaverActive,
                        preferLowQualityCover = preferLowQualityCover,
                        coverRequestSpec = coverRequestSpec,
                        showCoverGlassBadges = showCoverGlassBadges,
                        showInfoGlassBadges = showInfoGlassBadges,
                        showUpBadge = showUpBadges,
                        showUpAvatar = showUpAvatars,
                        homeDurationStyle = if (isNativeNonVideoCard && video.duration <= 0) {
                            HomeDurationStyle.HIDDEN
                        } else homeDurationStyle,
                        coverAspectRatio = cardLayout.coverAspectRatio,
                        cardHorizontalPadding = cardLayout.storyCardHorizontalPaddingDp.dp,
                        compactMetadata = cardLayout.compactMetadata,
                        titleMinLines = if (gridState.regularLayout) cardLayout.titleMaxLines else cardLayout.titleMinLines,
                        titleMaxLines = cardLayout.titleMaxLines,
                        onUpClick = onUpClick,
                        showPublishTime = showPublishTime,
                        onDismiss = { onDismissVideo(video) },
                        onLongClick = if (isNativeNonVideoCard) null else longPressCallback?.let { cb -> { cb(video) } },
                        onClick = { bvid, cid ->
                            onVideoClick(
                                HomeVideoClickRequest(
                                    bvid = bvid,
                                    dynamicId = video.dynamicId,
                                    episodeId = video.episodeId,
                                    cid = cid,
                                    coverUrl = video.pic,
                                    title = video.title,
                                    isVerticalVideo = video.resolveIsVerticalForNavigation(),
                                    source = HomeVideoClickSource.GRID,
                                    sourceRoute = sourceRoute
                                )
                            )
                        }
                    )

                    else -> ElegantVideoCard(
                        video = video,
                        regularGridMetadata = gridState.regularLayout,
                        modifier = Modifier,
                        index = index,
                        isFollowing = video.owner.mid in followingMids && category != HomeCategory.FOLLOW,
                        animationEnabled = cardAnimationEnabled,
                        motionTier = cardMotionTier,
                        transitionEnabled = cardTransitionEnabled && !isNativeNonVideoCard,
                        isReturningFromVideoDetail = returnFlags.returning,
                        isQuickReturningFromVideoDetail = returnFlags.quickReturn,
                        scrollLiteModeEnabled = false,
                        cardWidthDp = estimatedCardWidthDp,
                        showPublishTime = showPublishTime,
                        isDataSaverActive = isDataSaverActive,
                        preferLowQualityCover = preferLowQualityCover,
                        coverRequestSpec = coverRequestSpec,
                        compactStatsOnCover = compactStatsOnCover || cardLayout.compactStatsOnCover,
                        showCoverGlassBadges = showCoverGlassBadges,
                        showInfoGlassBadges = showInfoGlassBadges,
                        badgeEffectMode = badgeEffectMode,
                        infoGlassMode = infoGlassMode,
                        wallpaperTintEnabled = wallpaperTintEnabled,
                        wallpaperEffectMode = wallpaperEffectMode,
                        showUpBadge = showUpBadges,
                        showUpAvatar = showUpAvatars,
                        homeDurationStyle = if (isNativeNonVideoCard && video.duration <= 0) {
                            HomeDurationStyle.HIDDEN
                        } else homeDurationStyle,
                        coverAspectRatio = cardLayout.coverAspectRatio,
                        compactMetadata = cardLayout.compactMetadata,
                        titleMinLines = if (gridState.regularLayout) cardLayout.titleMaxLines else cardLayout.titleMinLines,
                        titleMaxLines = cardLayout.titleMaxLines,
                        showOnlineCount = showOnlineCount,
                        onUpClick = onUpClick,
                        onDismiss = { onDismissVideo(video) },
                        onWatchLater = if (isNativeNonVideoCard) null else ({
                            onWatchLater(video.bvid, resolveWatchLaterAid(video))
                        }),
                        onLongClick = if (isNativeNonVideoCard) null else longPressCallback?.let { cb -> { cb(video) } },
                        onClick = { bvid, cid ->
                            onVideoClick(
                                HomeVideoClickRequest(
                                    bvid = bvid,
                                    dynamicId = video.dynamicId,
                                    episodeId = video.episodeId,
                                    cid = cid,
                                    coverUrl = video.pic,
                                    title = video.title,
                                    isVerticalVideo = video.resolveIsVerticalForNavigation(),
                                    source = HomeVideoClickSource.GRID,
                                    sourceRoute = sourceRoute
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    val homeScrollTickProvider = remember(gridState) {
        { (gridState.firstVisibleItemIndex shl 16) + gridState.firstVisibleItemScrollOffset }
    }
    val cardScrolling = remember(gridState, pageScrollInProgress) {
        derivedStateOf { gridState.isScrollInProgress || pageScrollInProgress?.value == true }
    }
    HomeFeedCoverPreload(
        videos = visibleGridVideos,
        visibleVideoRange = visibleVideoRange,
        isScrolling = cardScrolling,
        requestSpec = coverRequestSpec,
        allowed = coverPreloadAllowed && pageImageWorkAllowed && visualWorkActive && !isDataSaverActive,
        aheadCount = coverPreloadAheadCount,
    )
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    val cardTransitionRunning = remember(sharedTransitionScope, visibilityScope) {
        derivedStateOf {
            returningFromDetail.value ||
                sharedTransitionScope?.isTransitionActive == true ||
                visibilityScope?.transition?.isRunning == true
        }
    }

    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalVideoCardSharedElementSourceRoute provides sourceRoute,
            LocalHomeScrollTickProvider provides homeScrollTickProvider,
            LocalHomeCardScrolling provides cardScrolling,
            LocalHomeCardTransitionRunning provides cardTransitionRunning
        ) {
            HomeFeedGrid(
                state = gridState,
                columns = gridColumns,
                contentPadding = contentPadding,
                horizontalArrangement = horizontalArrangement,
                verticalItemSpacing = cardLayout.verticalItemSpacingDp.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                // 首屏骨架直接铺在内容网格里：同一个 LazyLayout、同一个 gridState，
                // 骨架 → 卡片是一次 item 原地替换，而不是整棵网格重建。
                if (initialSkeletonContent != null) {
                    initialSkeletonContent()
                    return@HomeFeedGrid
                }
        categoryState.refreshError?.let { message ->
            item(key = "refresh_error", fullLine = true) {
                ListLoadError(
                    message = "刷新失败，已保留现有内容：$message",
                    onRetry = onRetryRefresh,
                    modifier = Modifier.fillMaxWidth().padding(AppSpacingTokens.Medium),
                )
            }
        }
        if (category == HomeCategory.LIVE) {
            // 顶栏/侧滑偶发进入内嵌直播页时，引导到与底栏一致的 LiveList 首页。
            item(fullLine = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.DoubleExtraLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                ) {
                    AppText(
                        text = "直播首页已独立",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = "与底栏「直播」相同，支持分区、排序与下拉刷新",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                    AppTextButton(onClick = onOpenLiveHome) {
                        AppText("打开直播首页")
                    }
                }
            }
        } else {
            // Video Category Content
            if (category == HomeCategory.RECOMMEND) {
                if (showHeroCarousel) {
                    item(
                        key = "home_hero_carousel",
                        contentType = "home_hero_carousel",
                        fullLine = true
                    ) {
                        val heroVisible = "home_hero_carousel" in visibleLazyItemKeys.value
                        val heroWorkAllowed = pageImageWorkAllowed && heroVisible && visualWorkActive
                        // Visible covers resume first; automatic motion shares the existing quiet window.
                        val heroMotionAllowed = heroWorkAllowed && auxiliaryWorkAllowed &&
                            componentMotionEnabled && !reduceMotion
                        CompositionLocalProvider(LocalPageImageLoadingAllowed provides heroWorkAllowed) {
                            if (LocalConfiguration.current.screenWidthDp >= HOME_HERO_CAROUSEL_WIDE_BREAKPOINT_DP.toInt()) {
                                // 折叠屏/平板展开态:全幅沉浸式 hero(与 TV 首页同一视觉语言)
                                HomeHeroCarouselImmersive(
                                    videos = carouselVideos,
                                    autoAdvanceAllowed = heroMotionAllowed,
                                    onGestureActiveChange = onHeroCarouselGestureActiveChange,
                                    horizontalEscapeDp = contentPadding.calculateLeftPadding(LocalLayoutDirection.current),
                                    onVideoClick = { video ->
                                        onVideoClick(
                                            HomeVideoClickRequest(
                                                bvid = video.bvid,
                                                dynamicId = video.dynamicId,
                                                episodeId = video.episodeId,
                                                cid = video.cid,
                                                coverUrl = video.pic,
                                                title = video.title,
                                                isVerticalVideo = video.resolveIsVerticalForNavigation(),
                                                source = HomeVideoClickSource.GRID,
                                                sourceRoute = sourceRoute
                                            )
                                        )
                                    },
                                )
                            } else {
                                HomeHeroCarousel(
                                    videos = carouselVideos,
                                    autoplayEnabled = homeHeroCarouselAutoplayEnabled && heroMotionAllowed,
                                    onGestureActiveChange = onHeroCarouselGestureActiveChange,
                                    dissolvingVideos = dissolvingVideos,
                                    onDissolveComplete = onDissolveComplete,
                                    onLongPress = longPressCallback,
                                    onVideoClick = { video ->
                                        onVideoClick(
                                            HomeVideoClickRequest(
                                                bvid = video.bvid,
                                                dynamicId = video.dynamicId,
                                                episodeId = video.episodeId,
                                                cid = video.cid,
                                                coverUrl = video.pic,
                                                title = video.title,
                                                isVerticalVideo = video.resolveIsVerticalForNavigation(),
                                                source = HomeVideoClickSource.GRID,
                                                sourceRoute = sourceRoute
                                            )
                                        )
                                    },
                                    onGetPreviewUrl = onGetPreviewUrl
                                )
                            }
                        }
                    }
                }
                if (todayWatchEnabled) {
                    item(key = "home_today_watch", fullLine = true) {
                        val imageWorkAllowed = pageImageWorkAllowed && visualWorkActive &&
                            "home_today_watch" in visibleLazyItemKeys.value
                        CompositionLocalProvider(LocalPageImageLoadingAllowed provides imageWorkAllowed) {
                            TodayWatchPlanCard(
                                selectedMode = todayWatchMode,
                                plan = todayWatchPlan,
                                isLoading = todayWatchLoading,
                                error = todayWatchError,
                                collapsed = todayWatchCollapsed,
                                cardConfig = todayWatchCardConfig,
                                showUpBadges = showUpBadges,
                                showUpAvatars = showUpAvatars,
                                onModeChange = onTodayWatchModeChange,
                                onCollapsedChange = onTodayWatchCollapsedChange,
                                onRefresh = onTodayWatchRefresh,
                                onUpClick = onTodayWatchUpClick,
                                onVideoClick = onTodayWatchVideoClick
                            )
                        }
                    }
                }
            }
            if (category == HomeCategory.POPULAR) {
                item(fullLine = true) {
                    PopularSubCategorySegmentedControl(
                        selectedSubCategory = popularSubCategory,
                        onSubCategoryChange = onPopularSubCategoryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.None)
                    )
                }
            }

            if (category == HomeCategory.POPULAR && popularSubCategory == PopularSubCategory.WEEKLY) {
                item(fullLine = true) {
                    com.android.purebilibili.core.ui.components.AppTextButton(
                        onClick = onWeeklySeriesClick,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        com.android.purebilibili.core.ui.components.AppText("查看往期每周必看")
                    }
                }
            }

            if (visibleGridVideos.isNotEmpty()) {
                val dividerIndex = alignedDividerIndex

                fun addCards(startIndex: Int, endExclusive: Int) {
                    items(
                        count = endExclusive - startIndex,
                        key = { videoGridKeys[startIndex + it] },
                        contentType = { "home_video_card" },
                    ) { offset ->
                        val index = startIndex + offset
                        renderVideoCard(
                            index,
                            visibleGridVideos[index],
                            videoListItemModifier(
                                enabled = cardAnimationEnabled,
                                dissolveReflowEnabled = dissolveReflowEnabled && isActive &&
                                    visualWorkActive && componentMotionEnabled && !reduceMotion,
                                dissolveReflowActive = cardReflowActive,
                            ),
                            videoGridKeys[index],
                        )
                    }
                }
                addCards(0, dividerIndex ?: visibleGridVideos.size)
                if (dividerIndex != null) {
                    item(
                        key = "old_content_divider_$dividerIndex",
                        contentType = "home_old_content_divider",
                        fullLine = true,
                    ) {
                        OldContentDivider(onClick = onOldContentDividerClick)
                    }
                    addCards(dividerIndex, visibleGridVideos.size)
                }
            }
        }

        // Loading Indicator at bottom
        val loadMoreError = categoryState.loadMoreError
        if (loadMoreError != null) {
            item(key = "load_more_error", fullLine = true) {
                ListLoadError(
                    message = "加载更多失败：$loadMoreError",
                    onRetry = onRetryLoadMore,
                    modifier = Modifier.fillMaxWidth().padding(AppSpacingTokens.Medium),
                )
            }
        } else if (categoryState.isLoading || categoryState.hasMore) {
             item(key = "load_more_status", fullLine = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacingTokens.Large),
                    contentAlignment = Alignment.Center
                ) {
                    if (categoryState.isLoading) {
                        AdaptiveLoadingIndicator(
                            size = AppSpacingTokens.ExtraLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // Spacer
        item(fullLine = true) {
            Box(modifier = Modifier.fillMaxWidth().height(AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall))
        }
        }
        }
        val nowPlayingBarOverlayVisible by com.android.purebilibili.feature.audio.player
            .AudioNowPlayingSession.barOverlayVisible
            .collectAsStateWithLifecycle()
        AnimatedVisibility(
            visible = ((category == HomeCategory.RECOMMEND &&
                oldContentGridItemIndex != null) || oldContentLocatorDissolving) &&
                !oldContentLocatorDismissed,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = if (oldContentLocatorDismissed) androidx.compose.animation.ExitTransition.None else
                fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = AppSpacingTokens.Large,
                    // 听视频小横条悬浮时上浮避让（与动态页 76dp 预留一致）；
                    // 整体抬高 120dp，避免胶囊压在底部卡片上
                    bottom = contentPadding.calculateBottomPadding() + AppSpacingTokens.Medium +
                        120.dp +
                        if (nowPlayingBarOverlayVisible) 76.dp else 0.dp,
                ),
        ) {
            DissolvableVideoCard(
                isDissolving = oldContentLocatorDissolving,
                onDissolveComplete = {
                    oldContentLocatorDismissed = true
                    oldContentLocatorDissolving = false
                },
                cardId = "old-content-locator-$oldContentLocatorRefreshKey",
                preset = DissolveAnimationPreset.TELEGRAM_FAST,
                collapseAfterDissolve = false,
                publishGlobalDissolveState = false,
                keepInvisibleAfterDissolve = true,
            ) {
                com.android.purebilibili.feature.home.components.HomeOverlayPillButton(
                    overlayPillColors = overlayPillColors,
                    onClick = {
                        if (!oldContentLocatorDissolving) {
                            oldContentLocatorDismissed = true
                            oldContentGridItemIndex?.let { targetIndex ->
                                oldContentLocatorScope.launch {
                                    gridState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    AppText("定位上次刷新")
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(enabled = !oldContentLocatorDissolving) { oldContentLocatorDissolving = true },
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularSubCategorySegmentedControl(
    selectedSubCategory: PopularSubCategory,
    onSubCategoryChange: (PopularSubCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val subCategories = PopularSubCategory.entries
    val selectedIndex = subCategories.indexOf(selectedSubCategory).coerceAtLeast(0)
    val labels = subCategories.map { subCategory ->
        stringResource(resolvePopularSubCategoryLabelRes(subCategory))
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Glass owns content-sized slots; center the actual rail, not a wider wrapper
        // whose child dock is start-aligned. Disabled materials retain the equal-slot path.
        if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
            com.android.purebilibili.core.ui.components.AppLiquidAwareTabRow(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                options = subCategories.mapIndexed { index, category ->
                    com.android.purebilibili.core.ui.components.AppSegmentOption(category, labels[index])
                },
                selectedValue = selectedSubCategory,
                onSelectionChange = onSubCategoryChange,
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                // 仅玻璃热门子分类上移，原生槽位及列表卡片位置保持原参数。
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .offset(y = (-6).dp),
                height = AppSpacingTokens.TripleExtraLarge,
                labelFontSize = MaterialTheme.typography.labelMedium.fontSize,
                centerContent = true,
                dragSelectionEnabled = labels.size > 1,
            )
            return@Box
        }
        BottomBarLiquidSegmentedControl(
            // Glass indicator fills the measured slot; native geometry keeps its defaults.
            liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
            // Center the native content rail inside its bounded viewport; glass uses the branch above.
            centerNativeContent = true,
            items = labels,
            selectedIndex = selectedIndex,
            onSelected = { index ->
                subCategories.getOrNull(index)?.let(onSubCategoryChange)
            },
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
            height = AppSpacingTokens.TripleExtraLarge,
            indicatorHeight = com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp(
                AppSpacingTokens.TripleExtraLarge.value
            ).dp,
            labelFontSize = MaterialTheme.typography.labelMedium.fontSize,
            // Page-level categories retain equal slots across material changes.
            forceEqualWidth = LocalAppUiStyle.current == AppUiStyle.MATERIAL3,
            // Four-character labels fit the cell but not Tab's extra 16dp text padding.
            allowNativeLabelOverflow = LocalAppUiStyle.current == AppUiStyle.MATERIAL3,
            containerHorizontalPadding = AppSpacingTokens.ExtraSmall,
            containerVerticalPadding = AppSpacingTokens.ExtraSmall,
            miuixBackdrop = null,
            liquidGlassEffectsEnabled = true,
            tapPressRefractionEnabled = true,
            dragSelectionEnabled = labels.size > 1,
            preferInlineContentStyle = true,
        )
    }
}

@Composable
private fun TodayWatchModeSegmentedControl(
    selectedMode: TodayWatchMode,
    enabled: Boolean,
    onModeChange: (TodayWatchMode) -> Unit,
    modifier: Modifier = Modifier,
    miuixBackdrop: MiuixBackdrop? = null,
) {
    val modes = TodayWatchMode.entries
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
    val labels = modes.map { mode ->
        stringResource(resolveTodayWatchModeLabelRes(mode))
    }
    BottomBarLiquidSegmentedControl(
        // Glass indicator fills the measured slot; native geometry keeps its defaults.
        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        centerGlassContent = true,
        centerNativeContent = true,
        items = labels,
        selectedIndex = selectedIndex,
        onSelected = { index ->
            modes.getOrNull(index)?.takeIf { it != selectedMode }?.let(onModeChange)
        },
        modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally),
        itemWidth = if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) null else 120.dp,
        enabled = enabled,
        height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
        indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
        labelFontSize = MaterialTheme.typography.labelMedium.fontSize,
        containerHorizontalPadding = AppSpacingTokens.ExtraSmall,
        containerVerticalPadding = AppSpacingTokens.ExtraSmall,
        miuixBackdrop = miuixBackdrop,
        liquidGlassEffectsEnabled = true,
        tapPressRefractionEnabled = true,
        dragSelectionEnabled = true,
        preferInlineContentStyle = false
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayWatchPlanCard(
    selectedMode: TodayWatchMode,
    plan: TodayWatchPlan?,
    isLoading: Boolean,
    error: String?,
    collapsed: Boolean,
    cardConfig: TodayWatchCardUiConfig,
    showUpBadges: Boolean,
    showUpAvatars: Boolean,
    onModeChange: (TodayWatchMode) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUpClick: (Long) -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    val todayWatchBackdrop = rememberLayerBackdrop()
    var revealContent by remember(plan?.generatedAt, isLoading, cardConfig.enableWaterfallAnimation) {
        mutableStateOf(!cardConfig.enableWaterfallAnimation)
    }
    LaunchedEffect(plan?.generatedAt, isLoading, cardConfig.enableWaterfallAnimation) {
        if (!cardConfig.enableWaterfallAnimation) {
            revealContent = true
            return@LaunchedEffect
        }
        if (isLoading) {
            revealContent = false
            return@LaunchedEffect
        }
        revealContent = false
        yield()
        revealContent = true
    }

    AppCard(
        colors = AppCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.ExtraSmall)
    ) {
        Box(modifier = Modifier.padding(AppSpacingTokens.Medium)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current) {
                            Modifier.layerBackdrop(todayWatchBackdrop)
                        } else Modifier
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small + AppSpacingTokens.Micro)
            ) cardBody@ {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = "今日推荐单",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppContentColors.accent(),
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro)) {
                    AppTextButton(
                        enabled = !isLoading,
                        onClick = onRefresh
                    ) {
                        AppIcon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(AppSpacingTokens.Large)
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                        AppText("刷新")
                    }
                    AppTextButton(
                        onClick = { onCollapsedChange(!collapsed) }
                    ) {
                        AppIcon(
                            imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(AppSpacingTokens.Large)
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                        AppText(if (collapsed) "展开" else "收起")
                    }
                }
            }

            if (collapsed) {
                AppText(
                    text = "已收起推荐单。展开后恢复自动更新；也可以直接点“刷新”换一批。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText()
                )
                if (!error.isNullOrBlank()) {
                    AppText(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.error()
                    )
                }
                return@cardBody
            }

            TodayWatchModeSegmentedControl(
                selectedMode = selectedMode,
                enabled = !isLoading,
                onModeChange = onModeChange,
                modifier = Modifier.fillMaxWidth(),
                miuixBackdrop = todayWatchBackdrop,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small + AppSpacingTokens.Micro)
            ) {
            AppText(
                text = "点开后会自动从推荐单移除；想换一批可点右上角“刷新”。",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )

            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdaptiveLoadingIndicator(
                        size = AppSpacingTokens.Medium + AppSpacingTokens.Micro,
                        strokeWidth = AppSpacingTokens.Micro * 0.9f
                    )
                    AppText("正在根据你的历史观看习惯生成推荐…", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!error.isNullOrBlank()) {
                AppText(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.error()
                )
            }

            val activePlan = plan ?: return@cardBody
            var revealIndex = 0

            if (cardConfig.showReasonHint) {
                val hintOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = hintOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    AppText(
                        text = if (activePlan.nightSignalUsed) {
                            "已结合护眼状态：夜间优先短时长、低刺激内容"
                        } else {
                            "当前按你的观看习惯与模式偏好生成"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                }
            }

            if (cardConfig.showUpRank && activePlan.upRanks.isNotEmpty()) {
                val titleOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = titleOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    AppText("UP主榜", style = MaterialTheme.typography.labelLarge)
                }
                val ranksOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = ranksOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
                    ) {
                        activePlan.upRanks.forEachIndexed { index, up ->
                            val clickable = shouldEnableTodayWatchUpRankClick(up)
                            AppText(
                                text = "${index + 1}. ${up.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (clickable) {
                                    AppContentColors.accent()
                                } else {
                                    AppContentColors.primaryText()
                                },
                                modifier = Modifier
                                    .clickable(enabled = clickable) { onUpClick(up.mid) }
                                    .padding(horizontal = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Micro)
                            )
                        }
                    }
                }
            }

            if (activePlan.videoQueue.isNotEmpty()) {
                val queueTitleOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = queueTitleOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    AppText("视频队列", style = MaterialTheme.typography.labelLarge)
                }
                activePlan.videoQueue
                    .take(cardConfig.queuePreviewLimit.coerceAtLeast(1))
                    .forEachIndexed { index, video ->
                        val rowOrder = revealIndex++
                        WaterfallReveal(
                            enabled = cardConfig.enableWaterfallAnimation,
                            visible = revealContent,
                            index = rowOrder,
                            exponent = cardConfig.waterfallExponent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(video) }
                                    .padding(vertical = AppSpacingTokens.ExtraSmall),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small + AppSpacingTokens.Micro),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppText(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.accent()
                                )
                                if (showUpAvatars && video.owner.face.isNotBlank()) {
                                    PageAwareAsyncImage(
                                        model = video.owner.face,
                                        contentDescription = video.owner.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(AppSpacingTokens.ExtraLarge)
                                            .clip(CircleShape)
                                    )
                                } else if (showUpAvatars) {
                                    Box(
                                        modifier = Modifier
                                            .size(AppSpacingTokens.ExtraLarge)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppText(
                                            text = video.owner.name.take(1).ifBlank { "UP" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppContentColors.accent()
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro)
                                ) {
                                    AppText(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = AppContentColors.primaryText()
                                    )
                                    UpBadgeName(
                                        name = video.owner.name,
                                        nameStyle = MaterialTheme.typography.labelSmall,
                                        nameColor = AppContentColors.secondaryText(),
                                        badgeTextColor = AppContentColors.secondaryText().copy(alpha = 0.85f),
                                        badgeBorderColor = AppContentColors.secondaryText().copy(alpha = 0.35f),
                                        showUpBadge = showUpBadges,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    val explanation = activePlan.explanationByBvid[video.bvid].orEmpty()
                                    if (explanation.isNotBlank()) {
                                        AppText(
                                            text = explanation,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = AppContentColors.accent().copy(alpha = 0.85f)
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
    }
}

@Composable
private fun WaterfallReveal(
    enabled: Boolean,
    visible: Boolean,
    index: Int,
    exponent: Float,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val delay = nonLinearWaterfallDelayMillis(
        index = index,
        exponent = exponent,
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = homeWaterfallFadeInSpec(delay)
        ) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = homeWaterfallExpandSpec(delay)
        ),
        exit = fadeOut(animationSpec = homeWaterfallFadeOutSpec())
    ) {
        content()
    }
}

@Composable
private fun OldContentDivider(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        // 半透明主色容器：让模糊壁纸背景透出，与毛玻璃卡片的观感一致。
        color = resolveOldContentDividerContainerColor(MaterialTheme.colorScheme),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                text = "上次刷新到这里，点击回顶刷新",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            AppText(
                text = "以下是之前的内容",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
