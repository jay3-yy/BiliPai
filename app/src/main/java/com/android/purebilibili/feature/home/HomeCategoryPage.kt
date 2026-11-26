package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.offset

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.resolveGlobalWallpaperProtectiveColor

import com.android.purebilibili.core.ui.components.AppButton

import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppGlassMenuAnchor
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appHorizontalScroll
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.core.util.FormatUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import java.util.Calendar
import androidx.compose.runtime.*
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticClick
import androidx.compose.ui.semantics.selected
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
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.motion.scrollCompactExpansionSpring
import com.android.purebilibili.core.ui.performance.TrackScrollJank
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.LocalNavigationControlMotionGroup
import com.android.purebilibili.navigation3.rememberNavigationControlMotionGroup
import com.android.purebilibili.navigation3.rememberSystemWindowControlNeighborModifier
import com.android.purebilibili.navigation3.rememberSystemWindowNavigationControl
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
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardDynamicTintEnabled
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardAuxiliaryWorkAllowed
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardTransitionRunning
import com.android.purebilibili.feature.home.components.cards.isGlobalWallpaperVisibleForHomeCard
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard
import com.android.purebilibili.feature.common.ListLoadError

import androidx.compose.ui.Alignment
import com.android.purebilibili.feature.home.components.LastPositionPill
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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

internal fun shouldShowHomeGuestLoginBanner(
    category: HomeCategory,
    isUserLoggedIn: Boolean,
    hasStoredSession: Boolean,
    isSessionRestored: Boolean,
    dismissed: Boolean,
): Boolean = category == HomeCategory.RECOMMEND &&
    isSessionRestored &&
    !isUserLoggedIn &&
    !hasStoredSession &&
    !dismissed

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeCategoryPageContent(
    category: HomeCategory,
    categoryState: CategoryContent,
    memoryRecovery: HomeFeedMemorySnapshot? = null,
    onMemoryRecoveryConsumed: (String, Boolean) -> Unit = { _, _ -> },
    isActive: Boolean,
    /** 页面是否在屏幕上，仍可在返回落位前恢复列表位置。 */
    pageOnScreen: Boolean = isActive,
    coverRequestsAllowed: Boolean,
    visibleDuringNavigation: Boolean = false,
    gridState: HomeFeedGridState,
    gridColumns: Int,
    cardLayout: HomeFeedCardLayout,
    cardWidthDp: Float,
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
    dissolvePreset: DissolveAnimationPreset,
    onDissolveReflowStarted: (String) -> Unit = {},
    dissolveReflowEnabled: Boolean = true,
    longPressCallback: ((VideoItem) -> Unit)? = null,
    downloadSelection: HomeDownloadSelection? = null,
    displayMode: Int,
    cardAnimationEnabled: Boolean,
    refreshedCardKeys: Flow<Set<String>>? = null,
    cardMotionTier: MotionTier = MotionTier.Normal,
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
    showFullVideoCardContent: Boolean = false,
    homeHeroCarouselEnabled: Boolean = true,
    homeHeroCarouselAutoplayEnabled: Boolean = false,
    onHeroCarouselGestureActiveChange: (Boolean) -> Unit = {},
    onGetPreviewUrl: suspend (String, Long) -> String? = { _, _ -> null },
    oldContentAnchorBvid: String? = null,
    oldContentStartIndex: Int? = null,
    oldContentLocatorRefreshKey: Long = 0L,
    oldContentLocatorDismissed: Boolean = false,
    onOldContentLocatorDismiss: () -> Unit = {},
    refreshPillBackdropSource: ChromeBackdropSource? = null,
    onOldContentDividerClick: () -> Unit = {},
    todayWatchEnabled: Boolean = false,
    todayWatchMode: TodayWatchMode = TodayWatchMode.RELAX,
    todayWatchPlan: TodayWatchPlan? = null,
    todayWatchLoading: Boolean = false,
    todayWatchError: String? = null,
    todayWatchCollapsed: Boolean = false,
    todayWatchCardConfig: TodayWatchCardUiConfig = TodayWatchCardUiConfig(),
    onTodayWatchModeChange: (TodayWatchMode) -> Unit = {},
    onTodayWatchStrategyChange: (com.android.purebilibili.plugin.sdk.RecommendationStrategy) -> Unit = {},
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
    onTodayWatchVideoDismiss: (VideoItem) -> Unit = {},
    onTodayWatchCreatorDismiss: (VideoItem) -> Unit = {},
    onTodayWatchUndoCreator: (Long) -> Unit = {},
    onTodayWatchUndoVideo: (String) -> Unit = {},
    onTodayWatchUndoKeyword: (String) -> Unit = {},
    onTodayWatchClearFeedback: () -> Unit = {},
    firstGridItemModifier: Modifier = Modifier,
    showGuestLoginBanner: Boolean = false,
    onGuestLoginClick: () -> Unit = {},
    onGuestLoginDismiss: () -> Unit = {},
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
    val horizontalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp)
    TrackScrollJank(
        scrollableState = gridState,
        stateName = "home:feed:${category.name.lowercase()}"
    )
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
        visibleVideoRange, visibleGridVideos.size,
    ) {
        derivedStateOf {
            val visible = visibleVideoRange.value
            resolveHomeCoverLoadRange(
                visible?.first, visible?.last, visibleGridVideos.size,
            )
        }
    }
    val coverLoadingActive = (pageOnScreen && coverRequestsAllowed) || visibleDuringNavigation
    val pageImageWorkAllowed = LocalPageImageLoadingAllowed.current && isActive
    val visualWorkActive = rememberVisualWorkActive()
    val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val componentMotionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val refreshMotionAllowed = isActive && pageOnScreen && visualWorkActive &&
        componentMotionEnabled && !reduceMotion &&
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.uiEntranceAnimationEnabled
    val refreshOffset = remember(gridState) { Animatable(0f) }
    var liftingCardKeys by remember(gridState) { mutableStateOf(emptySet<String>()) }
    val latestGridVideos by rememberUpdatedState(visibleGridVideos)
    val latestVideoRanges by rememberUpdatedState(videoRangesByLazyKey)
    val refreshLiftPx = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(refreshedCardKeys, gridState, refreshMotionAllowed, refreshLiftPx) {
        if (refreshedCardKeys == null || !refreshMotionAllowed) return@LaunchedEffect
        refreshedCardKeys.collectLatest { newKeys ->
            // 等本轮数据进入视口，短时间内没有可见新卡片就结束这轮入场。
            val visibleNewKeys = withTimeoutOrNull(160L) {
                snapshotFlow {
                    visibleLazyItemKeys.value.asSequence().filterIsInstance<String>().filter { key ->
                        val index = latestVideoRanges[key]?.first ?: return@filter false
                        HomeFeedMergePolicy.keyOf(latestGridVideos[index]) in newKeys
                    }.toSet()
                }.first { it.isNotEmpty() }
            } ?: return@collectLatest
            refreshOffset.snapTo(0f)
            liftingCardKeys = visibleNewKeys
            try {
                refreshOffset.animateTo(-refreshLiftPx, continuityTween(90))
                refreshOffset.animateTo(0f, scrollCompactExpansionSpring())
            } finally {
                liftingCardKeys = emptySet()
            }
        }
    }
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
        val selecting = downloadSelection?.active == true
        val canSelect = selecting && video.supportsFeedDownload()
        val selectedForDownload = canSelect && downloadSelection?.contains(video.bvid) == true
        val isDissolving = video.bvid in dissolvingVideos
        MaybeDissolvableVideoCard(
            isDissolving = isDissolving,
            onDissolveComplete = { onDissolveComplete(video.bvid) },
            cardId = video.bvid,
            preset = dissolvePreset,
            publishGlobalDissolveState = false,
            reflowDuringFinalTail = dissolveReflowEnabled,
            onReflowStarted = {
                cardReflowActive = dissolveReflowEnabled
                onDissolveReflowStarted(video.bvid)
            },
            preserveContentLayerWhenIdle = cardTransitionEnabled,
            propagateMinConstraints = gridState.regularLayout,
            modifier = itemModifier
                .then(if (index == 0) firstGridItemModifier else Modifier)
                .then(
                    if (refreshMotionAllowed && lazyItemKey in liftingCardKeys) {
                        Modifier.graphicsLayer {
                            translationY = if (lazyItemKey in visibleLazyItemKeys.value) refreshOffset.value else 0f
                        }
                    } else Modifier
                )
        ) {
            CompositionLocalProvider(
                LocalHomeCardVisibility provides cardVisibility,
                LocalPageImageLoadingAllowed provides cardImageWorkAllowed,
                LocalHomeCardCoverLoadingAllowed provides cardCoverWorkAllowed,
                LocalHomeCardAuxiliaryWorkAllowed provides (cardImageWorkAllowed && auxiliaryWorkAllowed),
            ) {
                Box(
                    modifier = if (selecting) Modifier.clearAndSetSemantics {
                        if (canSelect) {
                            contentDescription = video.title
                            selected = selectedForDownload
                            semanticClick(if (selectedForDownload) "取消选择" else "选择下载") {
                                downloadSelection?.toggle(video)
                                true
                            }
                        }
                    } else Modifier,
                    propagateMinConstraints = gridState.regularLayout,
                ) {
                    when (displayMode) {
                        1 -> StoryVideoCard(
                            video = video,
                            modifier = Modifier,
                            index = index,
                            animationEnabled = cardAnimationEnabled,
                            motionTier = cardMotionTier,
                            transitionEnabled = cardTransitionEnabled && !isNativeNonVideoCard && !selecting,
                            isReturningFromVideoDetail = returnFlags.returning,
                            isQuickReturningFromVideoDetail = returnFlags.quickReturn,
                            scrollLiteModeEnabled = false,
                            isDataSaverActive = isDataSaverActive,
                            preferLowQualityCover = preferLowQualityCover,
                            coverRequestSpec = coverRequestSpec,
                            showCoverGlassBadges = showCoverGlassBadges,
                            showInfoGlassBadges = showInfoGlassBadges,
                            wallpaperVisible = wallpaperTintEnabled,
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
                            onUpClick = onUpClick.takeUnless { selecting },
                            showPublishTime = showPublishTime,
                            onDismiss = if (selecting) null else ({ onDismissVideo(video) }),
                            onLongClick = if (isNativeNonVideoCard || selecting) null else longPressCallback?.let { cb -> { cb(video) } },
                            onClick = { bvid, cid ->
                                if (selecting) downloadSelection?.toggle(video)
                                else onVideoClick(
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
                            compactCornerFooter = true,
                            modifier = Modifier,
                            index = index,
                            isFollowing = video.owner.mid in followingMids && category != HomeCategory.FOLLOW,
                            animationEnabled = cardAnimationEnabled,
                            motionTier = cardMotionTier,
                            transitionEnabled = cardTransitionEnabled && !isNativeNonVideoCard && !selecting,
                            isReturningFromVideoDetail = returnFlags.returning,
                            isQuickReturningFromVideoDetail = returnFlags.quickReturn,
                            scrollLiteModeEnabled = false,
                            cardWidthDp = cardWidthDp,
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
                            onUpClick = onUpClick.takeUnless { selecting },
                            onDismiss = if (selecting) null else ({ onDismissVideo(video) }),
                            onWatchLater = if (isNativeNonVideoCard || selecting) null else ({
                                onWatchLater(video.bvid, resolveWatchLaterAid(video))
                            }),
                            onLongClick = if (isNativeNonVideoCard || selecting) null else longPressCallback?.let { cb -> { cb(video) } },
                            onClick = { bvid, cid ->
                                if (selecting) downloadSelection?.toggle(video)
                                else onVideoClick(
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
                    if (canSelect) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = { downloadSelection?.toggle(video) },
                                ),
                        )
                        com.android.purebilibili.core.ui.components.AppSurface(
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                            shape = CircleShape,
                            color = com.android.purebilibili.core.ui.AppSurfaceTokens.cardContainer(),
                        ) {
                            com.android.purebilibili.core.ui.components.AppCheckbox(
                                checked = selectedForDownload,
                                onCheckedChange = null,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                    }
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
    // 低端机 / 计费网络只在组合期解析一次（并在网络切换时刷新），不要放进滚动路径。
    val coverPreloadDeviceProfile = rememberHomeCoverPreloadDeviceProfile()
    HomeFeedCoverPreload(
        videos = visibleGridVideos,
        visibleVideoRange = visibleVideoRange,
        isScrolling = cardScrolling,
        gridState = gridState,
        columns = gridColumns,
        requestSpec = coverRequestSpec,
        allowed = coverPreloadAllowed && pageImageWorkAllowed && visualWorkActive && !isDataSaverActive,
        isDataSaverActive = isDataSaverActive,
        isLowRamDevice = coverPreloadDeviceProfile.isLowRamDevice,
        isMeteredNetwork = coverPreloadDeviceProfile.isMeteredNetwork,
        aheadCount = coverPreloadAheadCount,
        warmCoverTint = displayMode != 1 && LocalHomeCardDynamicTintEnabled.current &&
            !wallpaperTintEnabled && !isGlobalWallpaperVisibleForHomeCard(),
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
        val popularMotionGroup = if (category == HomeCategory.POPULAR) rememberNavigationControlMotionGroup() else null
        CompositionLocalProvider(
            LocalVideoCardSharedElementSourceRoute provides sourceRoute,
            LocalHomeScrollTickProvider provides homeScrollTickProvider,
            LocalHomeCardScrolling provides cardScrolling,
            LocalHomeCardTransitionRunning provides cardTransitionRunning,
            LocalNavigationControlMotionGroup provides popularMotionGroup,
        ) {
            HomeFeedGrid(
                state = gridState,
                columns = gridColumns,
                contentPadding = contentPadding,
                horizontalArrangement = horizontalArrangement,
                verticalItemSpacing = cardLayout.verticalItemSpacingDp.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .then(refreshPillBackdropSource?.modifier ?: Modifier)
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
                if (showGuestLoginBanner) {
                    item(
                        key = "home_guest_login_banner",
                        contentType = "home_guest_login_banner",
                        fullLine = true
                    ) {
                        com.android.purebilibili.core.ui.components.AppSurface(
                            shape = com.android.purebilibili.core.ui.AppShapes.container(
                                com.android.purebilibili.core.ui.ContainerLevel.Card
                            ),
                            color = com.android.purebilibili.core.ui.AppSurfaceTokens.cardContainer(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AppText(
                                    text = "登录解锁个性化推荐与 1080P60/4K 高画质",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f),
                                )
                                AppTextButton(onClick = onGuestLoginClick) {
                                    AppText(
                                        text = "去登录",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AppContentColors.accent(),
                                    )
                                }
                                com.android.purebilibili.core.ui.components.AppIconButton(
                                    onClick = onGuestLoginDismiss,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    com.android.purebilibili.core.ui.components.AppIcon(
                                        imageVector = androidx.compose.material.icons.Icons.Rounded.Close,
                                        contentDescription = "关闭登录提示",
                                        tint = AppContentColors.secondaryText(),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
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
                                    autoAdvanceAllowed = heroMotionAllowed && downloadSelection?.active != true,
                                    onGestureActiveChange = onHeroCarouselGestureActiveChange,
                                    horizontalEscapeDp = contentPadding.calculateLeftPadding(LocalLayoutDirection.current),
                                    onVideoClick = { video ->
                                        if (downloadSelection?.active == true) downloadSelection.toggle(video)
                                        else onVideoClick(
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
                                    autoplayEnabled = homeHeroCarouselAutoplayEnabled && heroMotionAllowed && downloadSelection?.active != true,
                                    onGestureActiveChange = onHeroCarouselGestureActiveChange,
                                    dissolvingVideos = dissolvingVideos,
                                    onDissolveComplete = onDissolveComplete,
                                    dissolvePreset = dissolvePreset,
                                    onLongPress = longPressCallback.takeUnless { downloadSelection?.active == true },
                                    onVideoClick = { video ->
                                        if (downloadSelection?.active == true) downloadSelection.toggle(video)
                                        else onVideoClick(
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
                                onStrategyChange = onTodayWatchStrategyChange,
                                onCollapsedChange = onTodayWatchCollapsedChange,
                                onRefresh = onTodayWatchRefresh,
                                onUpClick = onTodayWatchUpClick,
                                onVideoClick = onTodayWatchVideoClick,
                                onVideoDismiss = onTodayWatchVideoDismiss,
                                onCreatorDismiss = onTodayWatchCreatorDismiss,
                                onWatchLater = { video ->
                                    onWatchLater(video.bvid, resolveWatchLaterAid(video))
                                },
                                onUndoCreator = onTodayWatchUndoCreator,
                                onUndoVideo = onTodayWatchUndoVideo,
                                onUndoKeyword = onTodayWatchUndoKeyword,
                                onClearAllNegativeFeedback = onTodayWatchClearFeedback,
                            )
                        }
                    }
                }
            }
            if (category == HomeCategory.POPULAR) {
                item(fullLine = true) {
                    val categoryMotion = if (popularSubCategory == PopularSubCategory.WEEKLY) {
                        rememberSystemWindowControlNeighborModifier()
                    } else Modifier
                    PopularSubCategorySegmentedControl(
                        selectedSubCategory = popularSubCategory,
                        onSubCategoryChange = onPopularSubCategoryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(categoryMotion)
                            .padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.None)
                    )
                }
            }

            if (category == HomeCategory.POPULAR && popularSubCategory == PopularSubCategory.WEEKLY) {
                item(fullLine = true) {
                    val archive = rememberSystemWindowNavigationControl(
                        BiliPaiNavKey.WeeklySeries(), ButtonDefaults.textShape, onWeeklySeriesClick,
                    )
                    AppTextButton(
                        onClick = archive.onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .then(archive.modifier),
                    ) {
                        AppText("查看往期每周必看")
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

        // 三种材质及网格/瀑布流共用稳定的底部槽位，加载启停或结束不改变占位高度。
        // 错误提示允许随文字和字体自然增高，保留完整信息与重试入口。
        val loadMoreError = categoryState.loadMoreError
        item(key = "load_more_status", fullLine = true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSpacingTokens.ExtraLarge + AppSpacingTokens.Large * 2),
                contentAlignment = Alignment.Center,
            ) {
                if (loadMoreError != null) {
                    ListLoadError(
                        message = "加载更多失败：$loadMoreError",
                        onRetry = onRetryLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacingTokens.Medium),
                    )
                } else if (categoryState.isLoading) {
                    AdaptiveLoadingIndicator(
                        size = AppSpacingTokens.ExtraLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
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
        if (oldContentGridItemIndex != null && !oldContentLocatorDismissed) {
            LastPositionPill(
                refreshKey = oldContentLocatorRefreshKey,
                backdrop = refreshPillBackdropSource?.takeIf { it.isReady }?.backdrop,
                // 内容可见性与后台任务门控分开，任务暂停不销毁按钮或重放入场。
                visible = pageOnScreen,
                onDismiss = onOldContentLocatorDismiss,
                onClick = {
                    oldContentLocatorScope.launch {
                        gridState.animateScrollToItem(oldContentGridItemIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        start = AppSpacingTokens.Large,
                        top = contentPadding.calculateTopPadding() + AppSpacingTokens.Medium,
                        end = AppSpacingTokens.Large,
                        // 听视频小横条悬浮时上浮避让（与动态页 76dp 预留一致）；
                        // 整体抬高 120dp，避免胶囊压在底部卡片上
                        bottom = contentPadding.calculateBottomPadding() + AppSpacingTokens.Medium +
                            120.dp +
                            if (nowPlayingBarOverlayVisible) 76.dp else 0.dp,
                    ),
            )
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
    val comprehensiveLabel = stringResource(resolvePopularSubCategoryLabelRes(PopularSubCategory.COMPREHENSIVE))
    val rankingLabel = stringResource(resolvePopularSubCategoryLabelRes(PopularSubCategory.RANKING))
    val weeklyLabel = stringResource(resolvePopularSubCategoryLabelRes(PopularSubCategory.WEEKLY))
    val preciousLabel = stringResource(resolvePopularSubCategoryLabelRes(PopularSubCategory.PRECIOUS))
    val labels = remember(comprehensiveLabel, rankingLabel, weeklyLabel, preciousLabel) {
        listOf(comprehensiveLabel, rankingLabel, weeklyLabel, preciousLabel)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Glass owns content-sized slots; center the actual rail, not a wider wrapper
        // whose child dock is start-aligned. Disabled materials retain the equal-slot path.
        if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
            val options = remember(subCategories, labels) {
                subCategories.mapIndexed { index, category ->
                    com.android.purebilibili.core.ui.components.AppSegmentOption(category, labels[index])
                }
            }
            // 页内标签栏只取壁纸层，避免采到包含自身的首页内容录制层。
            val wallpaperBackdrop = LocalHomeWallpaperBackdrop.current
            val glassContainerColor = wallpaperBackdrop?.let {
                resolveGlobalWallpaperProtectiveColor(
                    baseColor = AppSurfaceTokens.cardContainer(),
                    lightAlpha = 0.28f,
                    darkAlpha = 0.38f,
                )
            }
            com.android.purebilibili.core.ui.components.AppLiquidAwareTabRow(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                options = options,
                selectedValue = selectedSubCategory,
                onSelectionChange = onSubCategoryChange,
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                miuixBackdrop = wallpaperBackdrop,
                glassContainerColor = glassContainerColor,
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
    val relaxLabel = stringResource(resolveTodayWatchModeLabelRes(TodayWatchMode.RELAX))
    val learnLabel = stringResource(resolveTodayWatchModeLabelRes(TodayWatchMode.LEARN))
    val labels = remember(relaxLabel, learnLabel) {
        listOf(relaxLabel, learnLabel)
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
    onStrategyChange: (com.android.purebilibili.plugin.sdk.RecommendationStrategy) -> Unit = {},
    onCollapsedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUpClick: (Long) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onVideoDismiss: (VideoItem) -> Unit = {},
    onCreatorDismiss: (VideoItem) -> Unit = {},
    onWatchLater: (VideoItem) -> Unit = {},
    onUndoCreator: (Long) -> Unit = {},
    onUndoVideo: (String) -> Unit = {},
    onUndoKeyword: (String) -> Unit = {},
    onClearAllNegativeFeedback: () -> Unit = {},
) {
    val todayWatchBackdropSource = com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    val todayWatchBackdrop = todayWatchBackdropSource.backdrop
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    var showTasteSheet by remember { mutableStateOf(false) }
    var durationFilter by remember(selectedMode, plan?.generatedAt) {
        mutableStateOf(TodayWatchDurationFilter.ALL)
    }

    val previewLimit = cardConfig.queuePreviewLimit.coerceAtLeast(1)
    val rawQueue = plan?.videoQueue.orEmpty()
    val countsByFilter = remember(rawQueue) {
        TodayWatchDurationFilter.entries.associateWith { filter ->
            filterTodayWatchQueueByDuration(rawQueue, filter).size
        }
    }
    val filteredQueue = remember(rawQueue, durationFilter) {
        filterTodayWatchQueueByDuration(rawQueue, durationFilter)
    }
    val visibleQueue = remember(filteredQueue, previewLimit) {
        filteredQueue.take(previewLimit)
    }
    val totalMinutes = remember(visibleQueue) {
        estimateTodayWatchQueueMinutes(visibleQueue)
    }
    val currentHour = remember(plan?.generatedAt) {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
    val contextHeadline = remember(selectedMode, plan?.nightSignalUsed, currentHour) {
        resolveTodayWatchContextHeadline(
            mode = selectedMode,
            nightSignalUsed = plan?.nightSignalUsed == true,
            hourOfDay = currentHour,
        )
    }
    val cardContainerColor = AppSurfaceTokens.surfaceContainer()

    AppCard(
        colors = AppCardDefaults.colors(
            containerColor = cardContainerColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.ExtraSmall),
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(todayWatchBackdropSource.modifier)
                    .background(cardContainerColor),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) cardBody@ {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppText(
                                text = "今日推荐单",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppContentColors.primaryText(),
                                maxLines = 1,
                            )
                            if (isLoading) {
                                AdaptiveLoadingIndicator(
                                    size = 14.dp,
                                    strokeWidth = 1.8.dp,
                                )
                            } else if (visibleQueue.isNotEmpty() && !collapsed) {
                                AppSurface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = AppContentColors.accent().copy(alpha = 0.14f),
                                ) {
                                    AppText(
                                        text = if (totalMinutes > 0) {
                                            "${visibleQueue.size} 支 · ${totalMinutes} 分"
                                        } else {
                                            "${visibleQueue.size} 支"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = AppContentColors.accent(),
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!collapsed) {
                                AppIconButton(
                                    onClick = { showTasteSheet = true },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = "兴趣画像与负反馈管理",
                                        tint = AppContentColors.secondaryText(),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (!collapsed && visibleQueue.isNotEmpty()) {
                                AppIconButton(
                                    enabled = !isLoading,
                                    onClick = {
                                        val picked = visibleQueue.randomOrNull() ?: return@AppIconButton
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onVideoClick(picked)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = "帮我随机挑一支",
                                        tint = AppContentColors.accent(),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            AppIconButton(
                                enabled = !isLoading,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRefresh()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                AppIcon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "换一批",
                                    tint = AppContentColors.secondaryText(),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            AppIconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCollapsedChange(!collapsed)
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                AppIcon(
                                    imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                    contentDescription = if (collapsed) "展开今日推荐单" else "收起今日推荐单",
                                    tint = AppContentColors.secondaryText(),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    if (!collapsed && cardConfig.showReasonHint) {
                        AppText(
                            text = contextHeadline,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (collapsed) {
                    val count = plan?.videoQueue?.size ?: 0
                    AppText(
                        text = if (count > 0) "已收起 · 备选 $count 支精选，展开即可选看" else "已收起推荐单 · 点右侧展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.clickable { onCollapsedChange(false) },
                    )
                    if (!error.isNullOrBlank()) {
                        AppText(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.error(),
                        )
                    }
                    return@cardBody
                }

                TodayWatchModeSegmentedControl(
                    selectedMode = selectedMode,
                    enabled = !isLoading,
                    onModeChange = { nextMode ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onModeChange(nextMode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    miuixBackdrop = todayWatchBackdrop,
                )

                if (isLoading && plan == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AdaptiveLoadingIndicator(
                            size = AppSpacingTokens.Medium + AppSpacingTokens.Micro,
                            strokeWidth = AppSpacingTokens.Micro * 0.9f,
                        )
                        AppText("正在结合你的完播偏好定制今日片单…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!error.isNullOrBlank()) {
                    AppText(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.error(),
                    )
                }

                val activePlan = plan ?: return@cardBody

                // 紧凑时长预算筛选胶囊条（带每档数量提示，减少纵向留白）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appHorizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TodayWatchDurationFilter.entries.forEach { filter ->
                        val selected = durationFilter == filter
                        val bucketCount = countsByFilter[filter] ?: 0
                        val hasItems = filter == TodayWatchDurationFilter.ALL || bucketCount > 0
                        val chipShape = AppShapes.container(ContainerLevel.Pill)
                        val labelText = if (filter == TodayWatchDurationFilter.ALL || bucketCount <= 0) {
                            filter.label
                        } else {
                            "${filter.label} $bucketCount"
                        }
                        AppSurface(
                            shape = chipShape,
                            color = if (selected) {
                                AppContentColors.accent()
                            } else {
                                AppSurfaceTokens.surfaceContainerHigh().copy(alpha = if (hasItems) 1f else 0.55f)
                            },
                            modifier = Modifier
                                .clip(chipShape)
                                .clickable(enabled = hasItems || selected) {
                                    if (durationFilter != filter) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        durationFilter = filter
                                    }
                                },
                        ) {
                            AppText(
                                text = labelText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = when {
                                    selected -> AppContentColors.onAccent()
                                    hasItems -> AppContentColors.primaryText().copy(alpha = 0.88f)
                                    else -> AppContentColors.secondaryText().copy(alpha = 0.52f)
                                },
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                // 常看 UP 胶囊
                if (cardConfig.showUpRank && activePlan.upRanks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .appHorizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            text = "常看",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText(),
                        )
                        activePlan.upRanks.forEachIndexed { index, up ->
                            val clickable = shouldEnableTodayWatchUpRankClick(up)
                            val pillShape = AppShapes.container(ContainerLevel.Pill)
                            AppSurface(
                                shape = pillShape,
                                color = AppSurfaceTokens.surfaceContainerHigh(),
                                modifier = Modifier
                                    .clip(pillShape)
                                    .clickable(enabled = clickable) { onUpClick(up.mid) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    AppText(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) AppContentColors.accent() else AppContentColors.secondaryText(),
                                    )
                                    AppText(
                                        text = up.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (clickable) AppContentColors.primaryText() else AppContentColors.secondaryText(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = Triple(selectedMode, durationFilter, activePlan.generatedAt),
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(continuityTween(0)) togetherWith fadeOut(continuityTween(0))
                        } else {
                            fadeIn(continuityTween(180)) togetherWith fadeOut(continuityTween(120))
                        }
                    },
                    label = "todayWatchQueueTransition",
                ) { _ ->
                    if (visibleQueue.isEmpty()) {
                        AppSurface(
                            shape = AppShapes.container(ContainerLevel.Card),
                            color = AppSurfaceTokens.surfaceContainerHigh(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AppText(
                                    text = if (durationFilter != TodayWatchDurationFilter.ALL) {
                                        "当前时长档位暂无片源"
                                    } else {
                                        "当前片单已看完，点刷新换一批"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                                if (durationFilter != TodayWatchDurationFilter.ALL) {
                                    AppTextButton(onClick = { durationFilter = TodayWatchDurationFilter.ALL }) {
                                        AppText("看全部")
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            visibleQueue.forEachIndexed { index, video ->
                                TodayWatchQueueVideoRow(
                                    index = index,
                                    video = video,
                                    explanation = activePlan.explanationByBvid[video.bvid].orEmpty(),
                                    showUpBadges = showUpBadges,
                                    showUpAvatars = showUpAvatars,
                                    onClick = { onVideoClick(video) },
                                    onUpClick = onUpClick,
                                    onWatchLater = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onWatchLater(video)
                                    },
                                    onDismiss = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onVideoDismiss(video)
                                    },
                                    onDismissCreator = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCreatorDismiss(video)
                                    },
                                )
                            }
                            if (visibleQueue.size >= 2) {
                                val accent = AppContentColors.accent()
                                val playAllShape = AppShapes.container(ContainerLevel.Pill)
                                AppSurface(
                                    shape = playAllShape,
                                    color = accent.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, accent.copy(alpha = 0.28f), playAllShape)
                                        .clip(playAllShape)
                                        .clickable(enabled = !isLoading) {
                                            val items = buildTodayWatchPlaylistItems(visibleQueue)
                                            val first = visibleQueue.firstOrNull() ?: return@clickable
                                            if (items.isNotEmpty()) {
                                                com.android.purebilibili.feature.video.player.PlaylistManager.setExternalPlaylist(
                                                    items = items,
                                                    startIndex = 0,
                                                    source = com.android.purebilibili.feature.video.player.ExternalPlaylistSource.UNKNOWN,
                                                )
                                                com.android.purebilibili.feature.video.player.PlaylistManager.setPlayMode(
                                                    com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL
                                                )
                                            }
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onVideoClick(first)
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        AppText(
                                            text = if (totalMinutes > 0) {
                                                "按片单连播（${visibleQueue.size} 支 · 约 ${totalMinutes} 分）"
                                            } else {
                                                "按片单连播（${visibleQueue.size} 支）"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
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

    if (showTasteSheet) {
        TodayWatchTasteSheet(
            mode = selectedMode,
            currentStrategy = cardConfig.strategy,
            onStrategyChange = onStrategyChange,
            onUpClick = onUpClick,
            onUndoCreator = onUndoCreator,
            onUndoVideo = onUndoVideo,
            onUndoKeyword = onUndoKeyword,
            onClearAllNegativeFeedback = onClearAllNegativeFeedback,
            onDismiss = { showTasteSheet = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayWatchQueueVideoRow(
    index: Int,
    video: VideoItem,
    explanation: String,
    showUpBadges: Boolean,
    showUpAvatars: Boolean,
    onClick: () -> Unit,
    onUpClick: (Long) -> Unit,
    onWatchLater: () -> Unit,
    onDismiss: () -> Unit,
    onDismissCreator: () -> Unit,
) {
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    var menuExpanded by remember(video.bvid) { mutableStateOf(false) }
    val coverUrl = remember(video.pic) { FormatUtils.fixImageUrl(video.pic) }
    val durationLabel = remember(video.duration) {
        if (video.duration > 0) FormatUtils.formatDuration(video.duration) else ""
    }
    val viewLabel = remember(video.stat.view) {
        if (video.stat.view > 0) "${FormatUtils.formatStat(video.stat.view.toLong())}播放" else ""
    }

    val reasonTags = remember(explanation) {
        explanation.split(" · ").map { it.trim() }.filter { it.isNotBlank() }.take(2)
    }
    val rowShape = AppShapes.container(ContainerLevel.Card)
    val accent = AppContentColors.accent()

    AppSurface(
        shape = rowShape,
        color = AppSurfaceTokens.surfaceContainerHigh(),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                shape = rowShape,
            )
            .clip(rowShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuExpanded = true
                },
            ),
    ) {
        com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame(
            plainHorizontal = true,
            fillInfoHeight = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            coverContent = {
                if (coverUrl.isNotBlank()) {
                    PageAwareAsyncImage(
                        model = coverUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            coverOverlayContent = {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == 0) accent else Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    AppText(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) AppContentColors.onAccent() else Color.White,
                    )
                }
                if (durationLabel.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.68f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        AppText(
                            text = durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            },
            infoContent = {
                AppText(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = AppContentColors.primaryText(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (showUpAvatars && video.owner.face.isNotBlank()) {
                            PageAwareAsyncImage(
                                model = FormatUtils.fixImageUrl(video.owner.face),
                                contentDescription = video.owner.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape),
                            )
                        }
                        UpBadgeName(
                            name = if (viewLabel.isNotEmpty()) "${video.owner.name} · $viewLabel" else video.owner.name,
                            nameStyle = MaterialTheme.typography.labelSmall,
                            nameColor = AppContentColors.secondaryText(),
                            badgeTextColor = AppContentColors.secondaryText().copy(alpha = 0.85f),
                            badgeBorderColor = AppContentColors.secondaryText().copy(alpha = 0.35f),
                            showUpBadge = showUpBadges,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (reasonTags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            reasonTags.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    AppText(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = accent,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            trailingContent = {
                AppGlassMenuAnchor(onOpen = { menuExpanded = true }) {
                    AppIconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "更多操作",
                            tint = AppContentColors.secondaryText().copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        AppDropdownMenuItem(
                            text = { AppText("加入稍后再看") },
                            leadingIcon = { AppIcon(Icons.Outlined.WatchLater, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onWatchLater()
                            },
                        )
                        if (video.owner.mid > 0L) {
                            AppDropdownMenuItem(
                                text = { AppText("查看 UP 主空间") },
                                leadingIcon = { AppIcon(Icons.Outlined.PersonOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onUpClick(video.owner.mid)
                                },
                            )
                        }
                        AppDropdownMenuItem(
                            text = { AppText("不感兴趣（换一条）") },
                            leadingIcon = { AppIcon(Icons.Rounded.Close, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDismiss()
                            },
                        )
                        if (video.owner.mid > 0L) {
                            AppDropdownMenuItem(
                                text = { AppText("减少该 UP 推荐") },
                                leadingIcon = { AppIcon(Icons.Outlined.PersonOff, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDismissCreator()
                                },
                            )
                        }
                    }
                }
            },
        )
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
