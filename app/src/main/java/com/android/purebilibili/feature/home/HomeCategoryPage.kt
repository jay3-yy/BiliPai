package com.android.purebilibili.feature.home
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.FeedVerticalStaggeredGrid

import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
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
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard
import com.android.purebilibili.core.ui.animation.jiggleOnDissolve
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.performance.TrackScrollJank
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.util.responsiveContentWidth
import kotlinx.collections.immutable.ImmutableSet
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.home.components.HomeHeroCarousel
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.LiveRoomCard
import com.android.purebilibili.feature.home.components.cards.LocalHomeScrollTickProvider
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard

import androidx.compose.ui.Alignment
import coil3.compose.AsyncImage
import kotlinx.coroutines.yield

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
 * Rows used by the truncated-card feed. A divider starts a fresh row so content below it
 * remains aligned; full-card mode bypasses this grouping and keeps the waterfall layout.
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
    hasVisibleContent: Boolean
): Boolean {
    return hasVisibleContent &&
        totalItems > 0 &&
        lastVisibleItemIndex >= totalItems - 4 &&
        !isLoading &&
        hasMore
}

@Composable
internal fun HomeCategoryPageContent(
    category: HomeCategory,
    categoryState: CategoryContent,
    gridState: LazyStaggeredGridState,
    gridColumns: Int,
    contentPadding: PaddingValues,
    dissolvingVideos: ImmutableSet<String>,
    followingMids: ImmutableSet<Long>,
    showOnlineCount: Boolean,
    coverRequestSpec: HomeCoverRequestSpec,
    onVideoClick: (HomeVideoClickRequest) -> Unit,
    onUpClick: (Long) -> Unit = {},
    onLiveClick: (Long, String, String) -> Unit,
    /** 顶栏直播已统一到 LiveList；首页内嵌直播分类仅作跳转入口。 */
    onOpenLiveHome: () -> Unit = {},
    onLoadMore: () -> Unit,
    onDismissVideo: (VideoItem) -> Unit,
    onWatchLater: (String, Long) -> Unit,
    onDissolveComplete: (String) -> Unit,
    longPressCallback: ((VideoItem) -> Unit)? = null, // [Feature] Long Press
    displayMode: Int,
    cardAnimationEnabled: Boolean,
    cardMotionTier: MotionTier = MotionTier.Normal,
    cardTransitionEnabled: Boolean,
    isReturningFromVideoDetail: Boolean = false,
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
    onTodayWatchVideoClick: (VideoItem) -> Unit = { video ->
        onVideoClick(
            HomeVideoClickRequest(
                bvid = video.bvid,
                cid = video.cid,
                coverUrl = video.pic,
                isVerticalVideo = video.isVertical,
                source = HomeVideoClickSource.TODAY_WATCH,
                sourceRoute = resolveHomeCategoryVideoSourceRoute(HomeCategory.RECOMMEND)
            )
        )
    },
    firstGridItemModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val sourceRoute = remember(category) {
        resolveHomeCategoryVideoSourceRoute(category)
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
    val adaptiveInfo = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hingeGridSpec = remember(adaptiveInfo, density.density) {
        resolveHomeFeedBookHingeGridSpec(adaptiveInfo, density.density)
    }
    val horizontalArrangement = remember(gridColumns, cardLayout.itemSpacingDp, hingeGridSpec) {
        resolveHomeFeedHorizontalArrangement(
            columns = gridColumns,
            baseSpacing = cardLayout.itemSpacingDp.dp,
            hingeSpec = hingeGridSpec,
        )
    }
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
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            // Staggered lanes do not guarantee that the last visible entry has the greatest
            // adapter index. Use the maximum across lanes so pagination cannot stall.
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            shouldRequestHomeCategoryLoadMore(
                totalItems = totalItems,
                lastVisibleItemIndex = lastVisibleItemIndex,
                isLoading = categoryState.isLoading,
                hasMore = categoryState.hasMore,
                hasVisibleContent = categoryState.videos.isNotEmpty() ||
                    categoryState.liveRooms.isNotEmpty() ||
                    categoryState.followedLiveRooms.isNotEmpty()
            )
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    val carouselVideos = remember(category, categoryState.videos) {
        if (category == HomeCategory.RECOMMEND) {
            selectHomeHeroCarouselItems(categoryState.videos)
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

    val renderVideoCard: @Composable (Int, VideoItem, Modifier) -> Unit = { index, video, itemModifier ->
        val isDynamicDetailCard = video.dynamicId.isNotBlank() &&
            !video.bvid.startsWith("BV", ignoreCase = true)
        val isDissolving = video.bvid in dissolvingVideos

        MaybeDissolvableVideoCard(
            isDissolving = isDissolving,
            onDissolveComplete = { onDissolveComplete(video.bvid) },
            cardId = video.bvid,
            preset = DissolveAnimationPreset.TELEGRAM_FAST,
            preserveContentLayerWhenIdle = cardTransitionEnabled,
            modifier = itemModifier
                .jiggleOnDissolve(
                    cardId = video.bvid,
                    isCurrentCardDissolving = isDissolving
                )
                .then(if (index == 0) firstGridItemModifier else Modifier)
        ) {
            when (displayMode) {
                1 -> StoryVideoCard(
                    video = video,
                    index = index,
                    animationEnabled = cardAnimationEnabled,
                    motionTier = cardMotionTier,
                    transitionEnabled = cardTransitionEnabled,
                    isReturningFromVideoDetail = isReturningFromVideoDetail,
                    isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail,
                    scrollLiteModeEnabled = false,
                    isDataSaverActive = isDataSaverActive,
                    preferLowQualityCover = preferLowQualityCover,
                    coverRequestSpec = coverRequestSpec,
                    showCoverGlassBadges = showCoverGlassBadges,
                    showInfoGlassBadges = showInfoGlassBadges,
                    showUpBadge = showUpBadges,
                    showUpAvatar = showUpAvatars,
                    homeDurationStyle = homeDurationStyle,
                    coverAspectRatio = cardLayout.coverAspectRatio,
                    cardHorizontalPadding = cardLayout.storyCardHorizontalPaddingDp.dp,
                    compactMetadata = cardLayout.compactMetadata,
                    titleMinLines = cardLayout.titleMinLines,
                    titleMaxLines = cardLayout.titleMaxLines,
                    showOnlineCount = showOnlineCount,
                    onUpClick = onUpClick,
                    showPublishTime = showPublishTime,
                    onDismiss = { onDismissVideo(video) },
                    onLongClick = if (isDynamicDetailCard) null else longPressCallback?.let { cb -> { cb(video) } },
                    onClick = { bvid, cid ->
                        onVideoClick(
                            HomeVideoClickRequest(
                                bvid = bvid,
                                dynamicId = video.dynamicId,
                                cid = cid,
                                coverUrl = video.pic,
                                isVerticalVideo = video.isVertical,
                                source = HomeVideoClickSource.GRID,
                                sourceRoute = sourceRoute
                            )
                        )
                    }
                )

                else -> ElegantVideoCard(
                    video = video,
                    index = index,
                    isFollowing = video.owner.mid in followingMids && category != HomeCategory.FOLLOW,
                    animationEnabled = cardAnimationEnabled,
                    motionTier = cardMotionTier,
                    transitionEnabled = cardTransitionEnabled,
                    isReturningFromVideoDetail = isReturningFromVideoDetail,
                    isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail,
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
                    homeDurationStyle = homeDurationStyle,
                    coverAspectRatio = cardLayout.coverAspectRatio,
                    compactMetadata = cardLayout.compactMetadata,
                    titleMinLines = cardLayout.titleMinLines,
                    titleMaxLines = cardLayout.titleMaxLines,
                    showOnlineCount = showOnlineCount,
                    onUpClick = onUpClick,
                    onDismiss = { onDismissVideo(video) },
                    onWatchLater = if (isDynamicDetailCard) null else ({
                        onWatchLater(video.bvid, resolveWatchLaterAid(video))
                    }),
                    onLongClick = if (isDynamicDetailCard) null else longPressCallback?.let { cb -> { cb(video) } },
                    onClick = { bvid, cid ->
                        onVideoClick(
                            HomeVideoClickRequest(
                                bvid = bvid,
                                dynamicId = video.dynamicId,
                                cid = cid,
                                coverUrl = video.pic,
                                isVerticalVideo = video.isVertical,
                                source = HomeVideoClickSource.GRID,
                                sourceRoute = sourceRoute
                            )
                        )
                    }
                )
            }
        }
    }

    val homeScrollTickProvider = remember(gridState) {
        { (gridState.firstVisibleItemIndex shl 16) + gridState.firstVisibleItemScrollOffset }
    }

    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalVideoCardSharedElementSourceRoute provides sourceRoute,
            LocalHomeScrollTickProvider provides homeScrollTickProvider
        ) {
            FeedVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(gridColumns),
                contentPadding = contentPadding,
                horizontalArrangement = horizontalArrangement,
                verticalItemSpacing = cardLayout.verticalItemSpacingDp.dp,
                modifier = Modifier.fillMaxSize()
            ) {
        if (category == HomeCategory.LIVE) {
            // 顶栏/侧滑偶发进入内嵌直播页时，引导到与底栏一致的 LiveList 首页。
            item(span = StaggeredGridItemSpan.FullLine) {
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AppText(
                        text = "与底栏「直播」相同，支持分区、排序与下拉刷新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        span = StaggeredGridItemSpan.FullLine
                    ) {
                        HomeHeroCarousel(
                            videos = carouselVideos,
                            autoplayEnabled = homeHeroCarouselAutoplayEnabled,
                            onGestureActiveChange = onHeroCarouselGestureActiveChange,
                            onVideoClick = { video ->
                                onVideoClick(
                                    HomeVideoClickRequest(
                                        bvid = video.bvid,
                                        dynamicId = video.dynamicId,
                                        cid = video.cid,
                                        coverUrl = video.pic,
                                        isVerticalVideo = video.isVertical,
                                        source = HomeVideoClickSource.GRID,
                                        sourceRoute = sourceRoute
                                    )
                                )
                            },
                            onGetPreviewUrl = onGetPreviewUrl
                        )
                    }
                }
                if (todayWatchEnabled) {
                    item(span = StaggeredGridItemSpan.FullLine) {
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
            if (category == HomeCategory.POPULAR) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    PopularSubCategorySegmentedControl(
                        selectedSubCategory = popularSubCategory,
                        onSubCategoryChange = onPopularSubCategoryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.None)
                    )
                }
            }

            if (visibleGridVideos.isNotEmpty()) {
                val shouldShowOldContentDivider = category == HomeCategory.RECOMMEND &&
                    (
                        (oldContentAnchorBvid != null && visibleGridVideos.any { it.bvid == oldContentAnchorBvid }) ||
                            (oldContentStartIndex != null && oldContentStartIndex > 0 && oldContentStartIndex < visibleGridVideos.size)
                        )

                val dividerIndex = if (shouldShowOldContentDivider) {
                    if (oldContentAnchorBvid != null) {
                        visibleGridVideos
                            .indexOfFirst { it.bvid == oldContentAnchorBvid }
                            .takeIf { it > 0 }
                    } else {
                        oldContentStartIndex
                    }
                } else {
                    null
                }

                if (showFullVideoCardContent) {
                    // Complete titles intentionally retain the independent-lane waterfall.
                    visibleGridVideos.forEachIndexed { index, video ->
                        if (index == dividerIndex) {
                            item(
                                key = "old_content_divider_$index",
                                contentType = "home_old_content_divider",
                                span = StaggeredGridItemSpan.FullLine
                            ) {
                                OldContentDivider()
                            }
                        }
                        item(key = videoGridKeys[index], contentType = "home_video_card") {
                            renderVideoCard(
                                index,
                                video,
                                videoListItemModifier(enabled = cardAnimationEnabled),
                            )
                        }
                    }
                } else {
                    // Truncated cards advance as complete rows. Metadata remains unabridged, but
                    // a long timestamp can no longer pull only its own lane out of alignment.
                    resolveHomeFeedAlignedRows(
                        itemCount = visibleGridVideos.size,
                        columns = gridColumns,
                        dividerIndex = dividerIndex,
                    ).forEach { rowIndices ->
                        if (rowIndices.first == dividerIndex) {
                            item(
                                key = "old_content_divider_${rowIndices.first}",
                                contentType = "home_old_content_divider",
                                span = StaggeredGridItemSpan.FullLine
                            ) {
                                OldContentDivider()
                            }
                        }
                        val rowKey = rowIndices.joinToString(
                            prefix = "home_video_row_",
                            separator = "_",
                        ) { videoGridKeys[it] }
                        item(
                            key = rowKey,
                            contentType = "home_video_row",
                            span = StaggeredGridItemSpan.FullLine,
                        ) {
                            Row(
                                modifier = videoListItemModifier(enabled = cardAnimationEnabled)
                                    .fillMaxWidth(),
                                horizontalArrangement = horizontalArrangement,
                                verticalAlignment = Alignment.Top,
                            ) {
                                rowIndices.forEach { index ->
                                    key(videoGridKeys[index]) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            renderVideoCard(
                                                index,
                                                visibleGridVideos[index],
                                                Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                                repeat(gridColumns - rowIndices.count()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading Indicator at bottom
        if (categoryState.isLoading || categoryState.hasMore) {
             item(span = StaggeredGridItemSpan.FullLine) {
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
        item(span = StaggeredGridItemSpan.FullLine) {
            Box(modifier = Modifier.fillMaxWidth().height(AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall))
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
        BottomBarLiquidSegmentedControl(
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
            // MD3 非玻璃下 4 项会触发 size>3 的可滚动启发式而靠左;
            // 本行是页面级分类,等宽居中(与首页顶 Tab 一致),MIUIX 保持原启发式。
            forceEqualWidth = LocalAppUiStyle.current == AppUiStyle.MATERIAL3,
            containerHorizontalPadding = AppSpacingTokens.ExtraSmall,
            containerVerticalPadding = AppSpacingTokens.ExtraSmall,
            miuixBackdrop = null,
            liquidGlassEffectsEnabled = true,
            tapPressRefractionEnabled = true,
            dragSelectionEnabled = labels.size > 1,
            preferInlineContentStyle = true,
            contentSizedMiuixNonGlassItems = true,
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
        items = labels,
        selectedIndex = selectedIndex,
        onSelected = { index ->
            modes.getOrNull(index)?.takeIf { it != selectedMode }?.let(onModeChange)
        },
        modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally),
        itemWidth = 120.dp,
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
                    .layerBackdrop(todayWatchBackdrop)
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
                    color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!error.isNullOrBlank()) {
                    AppText(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.error
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
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
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (showUpAvatars && video.owner.face.isNotBlank()) {
                                    AsyncImage(
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
                                            color = MaterialTheme.colorScheme.primary
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
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    UpBadgeName(
                                        name = video.owner.name,
                                        nameStyle = MaterialTheme.typography.labelSmall,
                                        nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        badgeBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
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
private fun OldContentDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppHorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = AppSpacingTokens.Micro / 4,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
        AppText(
            text = "以下是上次最新的视频",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacingTokens.Small + AppSpacingTokens.Micro)
        )
        AppHorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = AppSpacingTokens.Micro / 4,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    }
}
