package com.android.purebilibili.feature.space

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.HomeFeedCardWidthPreset
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.resolveWindowWidthSizeClass
import com.android.purebilibili.feature.home.resolveHomeFeedGridColumns
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.SeasonArchiveItem
import com.android.purebilibili.data.model.response.SeasonItem
import com.android.purebilibili.data.model.response.SeriesArchiveItem
import com.android.purebilibili.data.model.response.SeriesItem
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceAggregateData
import com.android.purebilibili.data.model.response.SpaceAggregateFavoriteItem
import com.android.purebilibili.data.model.response.SpaceAggregateImages
import com.android.purebilibili.data.model.response.SpaceAggregateRelation
import com.android.purebilibili.data.model.response.SpaceAudioItem
import com.android.purebilibili.data.model.response.SpaceTagItem
import com.android.purebilibili.data.model.response.SpaceUserInfo
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.RelationStatData
import com.android.purebilibili.data.model.response.UpStatData
import com.android.purebilibili.data.model.response.ArchiveStatInfo
import com.android.purebilibili.data.model.response.SpaceArticleItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.VideoSortOrder

enum class SpaceSearchScope {
    NONE,
    DYNAMIC,
    VIDEO
}

internal fun resolveSpaceSearchScope(
    selectedMainTab: SpaceMainTab,
    selectedSubTab: SpaceSubTab
): SpaceSearchScope {
    return when {
        selectedMainTab == SpaceMainTab.DYNAMIC -> SpaceSearchScope.DYNAMIC
        selectedMainTab == SpaceMainTab.CONTRIBUTION &&
            selectedSubTab == SpaceSubTab.VIDEO -> {
            SpaceSearchScope.VIDEO
        }
        else -> SpaceSearchScope.NONE
    }
}

internal fun resolveSpaceSearchPlaceholder(scope: SpaceSearchScope): String {
    return when (scope) {
        SpaceSearchScope.DYNAMIC -> "搜索 TA 的动态"
        SpaceSearchScope.VIDEO -> "搜索 TA 的视频"
        SpaceSearchScope.NONE -> ""
    }
}

internal fun resolveSpaceSearchBarGridItemIndex(
    scope: SpaceSearchScope,
    @Suppress("UNUSED_PARAMETER") hasContributionToolbar: Boolean
): Int? {
    return when (scope) {
        // Header(0) + optional SearchEntry. Main/secondary tabs are pinned outside the grid.
        SpaceSearchScope.DYNAMIC,
        SpaceSearchScope.VIDEO -> 1
        SpaceSearchScope.NONE -> null
    }
}

/**
 * Always-visible search entry under main tabs (not only top-right icon).
 * Returns a short CTA label for the current searchable scope.
 */
internal fun resolveSpaceSearchEntryLabel(scope: SpaceSearchScope): String {
    return when (scope) {
        SpaceSearchScope.DYNAMIC -> "搜索 TA 的动态"
        SpaceSearchScope.VIDEO -> "搜索 TA 的视频"
        SpaceSearchScope.NONE -> ""
    }
}

internal fun shouldShowSpaceSearchEntry(
    scope: SpaceSearchScope,
    isSearchMode: Boolean
): Boolean {
    return scope != SpaceSearchScope.NONE && !isSearchMode
}

internal fun resolveSpaceSearchBarRevealScrollOffsetPx(
    topBarHeightPx: Int,
    extraVisibleMarginPx: Int
): Int {
    return -(topBarHeightPx.coerceAtLeast(0) + extraVisibleMarginPx.coerceAtLeast(0))
}

internal fun shouldEnableSpaceLazyGridSharedTransition(
    transitionEnabled: Boolean,
    hasSharedTransitionScope: Boolean,
    hasAnimatedVisibilityScope: Boolean
): Boolean {
    return transitionEnabled && hasSharedTransitionScope && hasAnimatedVisibilityScope
}

internal fun shouldApplySpaceLoadResult(
    requestMid: Long,
    activeMid: Long,
    requestGeneration: Long,
    activeGeneration: Long
): Boolean {
    return requestMid > 0L &&
        requestMid == activeMid &&
        requestGeneration == activeGeneration
}

internal fun applySpaceSupplementalData(
    state: SpaceUiState.Success,
    seasons: List<SeasonItem>,
    series: List<SeriesItem>,
    createdFavoriteFolders: List<FavFolder>,
    collectedFavoriteFolders: List<FavFolder>,
    seasonArchives: Map<Long, List<SeasonArchiveItem>>,
    seriesArchives: Map<Long, List<SeriesArchiveItem>>
): SpaceUiState.Success {
    val mergedContributionTabs = mergeSpaceContributionTabsWithCollections(
        baseTabs = state.contributionTabs,
        seasons = seasons,
        series = series
    )
    val nextState = state.copy(
        seasons = seasons,
        series = series,
        createdFavoriteFolders = createdFavoriteFolders,
        collectedFavoriteFolders = collectedFavoriteFolders,
        seasonArchives = mergeArchiveMapsByLargestList(state.seasonArchives, seasonArchives),
        seriesArchives = mergeArchiveMapsByLargestList(state.seriesArchives, seriesArchives),
        contributionTabs = mergedContributionTabs,
        headerState = state.headerState.copy(
            createdFavorites = createdFavoriteFolders,
            collectedFavorites = collectedFavoriteFolders
        )
    )

    val hasCollectionsLoaded = seasons.isNotEmpty() ||
        series.isNotEmpty() ||
        createdFavoriteFolders.isNotEmpty() ||
        collectedFavoriteFolders.isNotEmpty()

    return nextState.copy(
        tabShellState = nextState.tabShellState.withUpdatedTab(SpaceMainTab.COLLECTIONS) {
            it.copy(hasLoaded = hasCollectionsLoaded)
        }
    )
}

private fun <T> mergeArchiveMapsByLargestList(
    existing: Map<Long, List<T>>,
    incoming: Map<Long, List<T>>
): Map<Long, List<T>> {
    return (existing.keys + incoming.keys).mapNotNull { id ->
        val existingItems = existing[id].orEmpty()
        val incomingItems = incoming[id].orEmpty()
        val selectedItems = if (incomingItems.size >= existingItems.size) {
            incomingItems
        } else {
            existingItems
        }
        if (selectedItems.isNotEmpty()) id to selectedItems else null
    }.toMap()
}

internal fun resolveEmbeddedSeasonArchives(
    seasons: List<SeasonItem>
): Map<Long, List<SeasonArchiveItem>> {
    return seasons.mapNotNull { season ->
        val seasonId = season.meta.season_id
        val archives = season.archives
        if (seasonId > 0L && archives.isNotEmpty()) {
            seasonId to archives
        } else {
            null
        }
    }.toMap()
}

internal fun resolveEmbeddedSeriesArchives(
    series: List<SeriesItem>
): Map<Long, List<SeriesArchiveItem>> {
    return series.mapNotNull { seriesItem ->
        val seriesId = seriesItem.meta.series_id
        val archives = seriesItem.archives
        if (seriesId > 0L && archives.isNotEmpty()) {
            seriesId to archives
        } else {
            null
        }
    }.toMap()
}

internal fun mapSeasonArchiveToVideoItem(
    item: SeasonArchiveItem,
    mid: Long,
    ownerName: String = ""
): VideoItem {
    return VideoItem(
        bvid = item.bvid,
        title = item.title,
        pic = item.pic,
        owner = com.android.purebilibili.data.model.response.Owner(
            mid = mid,
            name = item.author.ifBlank { ownerName }
        ),
        stat = Stat(
            view = item.stat.view.toInt(),
            danmaku = item.stat.danmaku.toInt(),
            reply = item.stat.reply.toInt()
        ),
        duration = item.duration,
        pubdate = item.pubdate
    )
}

internal fun mapSeriesArchiveToVideoItem(
    item: SeriesArchiveItem,
    mid: Long,
    ownerName: String = ""
): VideoItem {
    return VideoItem(
        bvid = item.bvid,
        title = item.title,
        pic = item.pic,
        owner = com.android.purebilibili.data.model.response.Owner(
            mid = mid,
            name = item.author.ifBlank { ownerName }
        ),
        stat = Stat(
            view = item.stat.view.toInt(),
            danmaku = item.stat.danmaku.toInt(),
            reply = item.stat.reply.toInt()
        ),
        duration = item.duration,
        pubdate = item.pubdate
    )
}

internal fun resolveSpaceArchiveSharedTransitionKey(bvid: String): String? {
    return bvid.trim().takeIf { it.isNotEmpty() }
}

internal fun resolveSpaceAggregateLazyItemKey(
    section: String,
    index: Int,
    item: SpaceAggregateArchiveItem,
): String {
    return "${section}_${item.aid}_${item.bvid}_$index"
}

/**
 * Resolves the in-app video target used by aggregate cards (coin/like previews).
 *
 * The aggregate endpoint is inconsistent: some responses populate [bvid], while
 * others only provide an `av`/numeric [param] or a `bilibili://video/...` [uri].
 * Keep that fallback inside the navigation policy so a missing bvid cannot send
 * a video deep link through the generic browser callback.
 */
internal fun resolveSpaceAggregateVideoId(item: SpaceAggregateArchiveItem): String? {
    item.bvid.trim().takeIf { it.isNotEmpty() }?.let { return it }

    val isVideoArchive = item.goto.equals("av", ignoreCase = true) ||
        item.goto.equals("video", ignoreCase = true) ||
        item.goto.equals("archive", ignoreCase = true)
    val hasExplicitVideoUri = item.uri.contains("/video/", ignoreCase = true)
    if (!isVideoArchive && !hasExplicitVideoUri) return null

    sequenceOf(item.uri, item.param)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { candidate ->
            BilibiliNavigationTargetParser.parse(candidate)
                ?.let { target -> (target as? BilibiliNavigationTarget.Video)?.videoId }
        }
        .firstOrNull()
        ?.let { return it }

    if (isVideoArchive) {
        item.param.trim().toLongOrNull()?.takeIf { it > 0L }?.let { return "av$it" }
        item.aid.takeIf { it > 0L }?.let { return "av$it" }
    }
    return null
}

internal fun resolveInitialSpaceVideoPage(
    order: VideoSortOrder,
    totalCount: Int,
    pageSize: Int
): Int {
    val lastPage = resolveSpaceVideoLastPage(totalCount = totalCount, pageSize = pageSize)
    return if (order == VideoSortOrder.OLDEST_PUBDATE) lastPage else 1
}

internal fun resolveNextSpaceVideoPage(
    order: VideoSortOrder,
    currentPage: Int,
    totalCount: Int,
    pageSize: Int
): Int? {
    val lastPage = resolveSpaceVideoLastPage(totalCount = totalCount, pageSize = pageSize)
    if (lastPage <= 0) return null
    return when (order) {
        VideoSortOrder.OLDEST_PUBDATE -> currentPage.takeIf { it > 1 }?.minus(1)
        else -> currentPage.takeIf { it < lastPage }?.plus(1)
    }
}

internal fun normalizeSpaceVideoPage(
    order: VideoSortOrder,
    videos: List<SpaceVideoItem>
): List<SpaceVideoItem> {
    return if (order == VideoSortOrder.OLDEST_PUBDATE) videos.asReversed() else videos
}

internal const val SPACE_CONTENT_MAX_WIDTH_DP = 980
internal const val SPACE_EXPANDED_CONTENT_MAX_WIDTH_DP = 1280
internal const val SPACE_LIST_CONTENT_MAX_WIDTH_DP = 720
private const val SPACE_DYNAMIC_MIN_COLUMN_WIDTH_DP = 360
private const val SPACE_DYNAMIC_MAX_COLUMNS = 3

internal data class SpaceAdaptiveLayoutSpec(
    val contentMaxWidthDp: Int,
    val useExpandedHeader: Boolean,
    val dynamicColumns: Int,
    val listContentMaxWidthDp: Int = SPACE_LIST_CONTENT_MAX_WIDTH_DP,
)

internal const val SPACE_BANNER_ASPECT_RATIO = 1125f / 396f
/** Matches PiliPlus `kHeaderHeight = 135.0`. */
internal const val SPACE_HEADER_HEIGHT_DP = 135f
internal const val SPACE_WIDE_BANNER_MAX_HEIGHT_DP = 135f
internal const val SPACE_WIDE_BANNER_MIN_HEIGHT_DP = 120f

internal data class SpaceBannerMetrics(
    val heightDp: Float,
    val cropToFill: Boolean,
    val heroHeightDp: Float = heightDp,
)

internal data class SpaceUserCardVisuals(
    val largePhoto: String = "",
    val smallPhoto: String = "",
    val ipLocation: String? = null,
)

/**
 * Flat unfolded foldables and tablets deliberately share the same width-class policy.
 * Geometry is derived from the current window, not from a device/model distinction.
 */
internal fun resolveSpaceAdaptiveLayoutSpec(
    widthDp: Int,
    widthSizeClass: WindowWidthSizeClass = resolveWindowWidthSizeClass(widthDp.dp),
): SpaceAdaptiveLayoutSpec {
    val expanded = widthSizeClass >= WindowWidthSizeClass.Expanded
    val useExpandedHeader = widthSizeClass != WindowWidthSizeClass.Compact
    val contentMaxWidthDp = if (expanded) {
        SPACE_EXPANDED_CONTENT_MAX_WIDTH_DP
    } else {
        SPACE_CONTENT_MAX_WIDTH_DP
    }
    val boundedContentWidthDp = minOf(widthDp.coerceAtLeast(0), contentMaxWidthDp)
    val dynamicColumns = (boundedContentWidthDp / SPACE_DYNAMIC_MIN_COLUMN_WIDTH_DP)
        .coerceIn(1, SPACE_DYNAMIC_MAX_COLUMNS)
    return SpaceAdaptiveLayoutSpec(
        contentMaxWidthDp = contentMaxWidthDp,
        useExpandedHeader = useExpandedHeader,
        dynamicColumns = dynamicColumns,
    )
}

internal fun resolveSpaceBannerMetrics(
    renderedBannerWidthDp: Float,
    windowWidthDp: Float,
    windowHeightDp: Float,
    topInsetDp: Float = 0f,
): SpaceBannerMetrics {
    val landscape = windowHeightDp > 0f && windowWidthDp > windowHeightDp
    val useDesktopHeader = windowWidthDp >= 600f || landscape
    val naturalHeroHeight = renderedBannerWidthDp.coerceAtLeast(0f) / SPACE_BANNER_ASPECT_RATIO
    val heroHeight = if (useDesktopHeader) {
        if (windowHeightDp > 0f) {
            (windowHeightDp * 0.22f).coerceIn(
                SPACE_WIDE_BANNER_MIN_HEIGHT_DP,
                SPACE_WIDE_BANNER_MAX_HEIGHT_DP,
            )
        } else {
            SPACE_WIDE_BANNER_MAX_HEIGHT_DP
        }
    } else {
        naturalHeroHeight
    }
    val totalHeight = heroHeight + topInsetDp
    return SpaceBannerMetrics(
        heightDp = totalHeight,
        cropToFill = useDesktopHeader,
        heroHeightDp = heroHeight,
    )
}

/**
 * 投稿网格列数：与首页信息流共用同一套策略（用户固定列数优先，其次按卡宽预设自适应），
 * 内容宽度按当前空间页自适应上限截断，保证投稿卡片排版与首页 feed 对齐。
 */
internal fun resolveSpaceContentGridColumnCount(
    widthDp: Int,
    fixedColumnCount: Int = 0,
    cardWidthPreset: HomeFeedCardWidthPreset = HomeFeedCardWidthPreset.AUTO,
    contentMaxWidthDp: Int = SPACE_CONTENT_MAX_WIDTH_DP,
    widthSizeClass: WindowWidthSizeClass = resolveWindowWidthSizeClass(widthDp.dp)
): Int {
    val contentWidthDp = minOf(widthDp, contentMaxWidthDp)
    return resolveHomeFeedGridColumns(
        contentWidthDp = contentWidthDp,
        displayMode = 0,
        fixedColumnCount = fixedColumnCount,
        cardWidthPreset = cardWidthPreset,
        widthSizeClass = widthSizeClass
    )
}

internal enum class SpaceContributionVideoLayoutMode {
    GRID,
    SINGLE_COLUMN
}

internal fun defaultSpaceContributionVideoLayoutMode(): SpaceContributionVideoLayoutMode {
    return SpaceContributionVideoLayoutMode.GRID
}

internal fun toggleSpaceContributionVideoLayoutMode(
    current: SpaceContributionVideoLayoutMode
): SpaceContributionVideoLayoutMode {
    return when (current) {
        SpaceContributionVideoLayoutMode.GRID -> SpaceContributionVideoLayoutMode.SINGLE_COLUMN
        SpaceContributionVideoLayoutMode.SINGLE_COLUMN -> SpaceContributionVideoLayoutMode.GRID
    }
}

internal fun resolveSpaceContributionVideoGridSpan(
    layoutMode: SpaceContributionVideoLayoutMode,
    maxLineSpan: Int
): Int {
    return when (layoutMode) {
        SpaceContributionVideoLayoutMode.GRID -> 1
        SpaceContributionVideoLayoutMode.SINGLE_COLUMN -> maxLineSpan
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveSpaceContributionVideoItemKey(
    layoutMode: SpaceContributionVideoLayoutMode,
    bvid: String,
    aid: Long
): String {
    // The wrapper retains one node while span/content changes; placement springs need a stable key.
    return "space_video_${bvid}_${aid}"
}

internal data class SpaceInitialSeed(
    val userInfo: SpaceUserInfo,
    val relationStat: RelationStatData?,
    val upStat: UpStatData?,
    val videos: List<SpaceVideoItem>,
    val totalVideos: Int,
    val audios: List<SpaceAudioItem>,
    val totalAudios: Int,
    val articles: List<SpaceArticleItem>,
    val totalArticles: Int,
    val homeFavoriteFolders: List<FavFolder>,
    val homeFavoriteFolderCount: Int,
    val homeCoinVideos: List<SpaceAggregateArchiveItem>,
    val homeCoinVideoCount: Int,
    val homeLikeVideos: List<SpaceAggregateArchiveItem>,
    val homeLikeVideoCount: Int,
    val homeBangumiItems: List<SpaceAggregateArchiveItem>,
    val homeBangumiCount: Int,
    val homeComicItems: List<SpaceAggregateArchiveItem>,
    val homeComicCount: Int,
    val mainTabs: List<SpaceMainTabItem>,
    val contributionTabs: List<SpaceContributionTab>,
    val defaultMainTab: SpaceMainTab,
    val defaultSubTab: SpaceSubTab,
    val defaultContributionTabId: String,
    val hasCheeseTab: Boolean = false,
    val reservations: List<com.android.purebilibili.data.model.response.SpaceReservationItem> = emptyList(),
    val hasMedalWall: Boolean = false,
)

internal fun resolveSpaceAggregateTopPhoto(
    images: SpaceAggregateImages?,
    isDarkTheme: Boolean = false,
): String {
    if (images == null) return ""
    val collectionTopItem = images.collectionTopSimple?.top?.result?.firstOrNull()
    val collectionPhoto = collectionTopItem?.item?.image?.defaultImage?.takeIf { it.isNotBlank() }
        ?: collectionTopItem?.cover?.takeIf { it.isNotBlank() }
    if (!collectionPhoto.isNullOrBlank()) {
        return collectionPhoto
    }
    return if (isDarkTheme && images.nightImgUrl.isNotBlank()) {
        images.nightImgUrl
    } else {
        images.imgUrl.ifBlank { images.nightImgUrl }
    }
}

internal fun parseTopImageDy(location: String, height: Double): Float {
    if (location.isBlank() || height <= 0.0) return 0f
    return try {
        val parts = location.split('-').drop(1).take(2).mapNotNull { it.toFloatOrNull() }
        if (parts.size == 2) {
            val start = parts[0]
            val end = parts[1]
            ((start + end) / height.toFloat() - 1f).coerceIn(-1f, 1f)
        } else {
            0f
        }
    } catch (_: Exception) {
        0f
    }
}

internal fun resolveSpaceTopImageItems(images: SpaceAggregateImages?): List<com.android.purebilibili.data.model.response.SpaceTopImageItem> {
    if (images == null) return emptyList()
    val collectionItems = images.collectionTopSimple?.top?.result.orEmpty()
    if (collectionItems.isNotEmpty()) {
        return collectionItems.mapNotNull { item ->
            val detail = item.item
            val img = detail?.image ?: detail?.animation
            val defaultImg = img?.defaultImage?.takeIf { it.isNotBlank() }
            val fullCover = item.cover.takeIf { it.isNotBlank() } ?: defaultImg ?: return@mapNotNull null
            val header = defaultImg ?: fullCover
            val dy = parseTopImageDy(img?.location.orEmpty(), img?.height ?: 0.0)
            com.android.purebilibili.data.model.response.SpaceTopImageItem(
                header = header,
                fullCover = fullCover,
                dy = dy,
                title = item.title
            )
        }
    }
    return emptyList()
}

internal fun resolveSpaceRelationState(
    aggregateRelation: Int? = null,
    relSpecial: Int? = null,
    cardRelation: SpaceAggregateRelation? = null
): Pair<Boolean, Int> {
    if (aggregateRelation == -1) {
        return Pair(false, 128)
    }
    val relation = cardRelation ?: SpaceAggregateRelation()
    if (relation.isFollow == 1) {
        val status = if (relSpecial == 1) {
            -10
        } else {
            relation.status.takeIf { it != 0 } ?: 2
        }
        return Pair(true, status)
    }
    return Pair(false, 0)
}

internal fun resolveSpaceInitialSeedFromAggregate(
    data: SpaceAggregateData,
    cardLargePhoto: String = "",
    cardSmallPhoto: String = "",
    cardIpLocation: String? = null,
): SpaceInitialSeed? {
    val card = data.card ?: return null
    val userMid = card.mid.toLongOrNull()?.takeIf { it > 0L } ?: return null
    if (card.name.isBlank() || card.face.isBlank()) return null

    val topPhoto = resolveSpaceTopPhoto(
        topPhoto = resolveSpaceAggregateTopPhoto(data.images),
        cardLargePhoto = cardLargePhoto,
        cardSmallPhoto = cardSmallPhoto
    )
    val topImageItems = resolveSpaceTopImageItems(data.images)
    val (isFollowed, relationStatus) = resolveSpaceRelationState(
        aggregateRelation = data.relation,
        relSpecial = data.relSpecial,
        cardRelation = card.relation
    )
    val mainTabs = resolveSpaceMainTabs(data.tab2)
    val contributionTabs = ensureSpaceContributionTabsForAvailableContent(
        tabs = resolveSpaceContributionTabs(data.tab2),
        hasArticles = (data.article?.count ?: 0) > 0 || data.article?.item.orEmpty().isNotEmpty()
    )
    val defaultSelection = resolveSpaceAggregateDefaultSelection(
        defaultTab = data.defaultTab,
        contributionTabs = contributionTabs
    )
    val ipFromTag = card.spaceTag.firstOrNull { it.type == "location" }?.title
    val resolvedIpLocation = ipFromTag
        ?: data.card?.ipLocation?.takeIf { it.isNotBlank() }
        ?: cardIpLocation?.takeIf { it.isNotBlank() }
    val filteredTags = card.spaceTag.filter { it.type in setOf("location", "real_name") }
    val resolvedSpaceTags = if (filteredTags.none { it.type == "location" } && !resolvedIpLocation.isNullOrBlank()) {
        val locationTitle = if (resolvedIpLocation.startsWith("IP属地")) resolvedIpLocation else "IP属地：$resolvedIpLocation"
        filteredTags + SpaceTagItem(type = "location", title = locationTitle)
    } else {
        filteredTags
    }

    return SpaceInitialSeed(
        userInfo = SpaceUserInfo(
            mid = userMid,
            name = card.name,
            sex = card.sex,
            face = card.face,
            sign = card.sign,
            level = card.levelInfo.currentLevel,
            silence = card.silence,
            official = card.officialVerify,
            vip = card.vip,
            isFollowed = isFollowed,
            relationStatus = relationStatus,
            topPhoto = topPhoto,
            nightTopPhoto = data.images?.nightImgUrl.orEmpty(),
            topImages = topImageItems,
            followingsFollowed = card.followingsFollowedUpper,
            spaceTags = resolvedSpaceTags,
            liveRoom = data.live,
            ipLocation = resolvedIpLocation,
        ),
        relationStat = RelationStatData(
            mid = userMid,
            following = card.attention,
            follower = card.fans
        ),
        upStat = UpStatData(
            archive = ArchiveStatInfo(view = 0),
            likes = card.likes.likeNum
        ),
        videos = data.archive?.item.orEmpty().map(::mapSpaceAggregateVideoItem),
        totalVideos = data.archive?.count ?: 0,
        audios = data.audios?.item.orEmpty(),
        totalAudios = data.audios?.count ?: 0,
        articles = data.article?.item.orEmpty(),
        totalArticles = data.article?.count ?: 0,
        homeFavoriteFolders = data.favourite2?.item.orEmpty().map(::mapSpaceAggregateFavoriteFolder),
        homeFavoriteFolderCount = data.favourite2?.count ?: 0,
        homeCoinVideos = data.coinArchive?.item.orEmpty(),
        homeCoinVideoCount = data.coinArchive?.count ?: 0,
        homeLikeVideos = data.likeArchive?.item.orEmpty(),
        homeLikeVideoCount = data.likeArchive?.count ?: 0,
        homeBangumiItems = data.season?.item.orEmpty(),
        homeBangumiCount = data.season?.count ?: 0,
        homeComicItems = data.comic?.item.orEmpty(),
        homeComicCount = data.comic?.count ?: 0,
        mainTabs = mainTabs,
        contributionTabs = contributionTabs,
        defaultMainTab = defaultSelection.first,
        defaultSubTab = defaultSelection.second,
        defaultContributionTabId = defaultSelection.third,
        hasCheeseTab = data.tab2.any { it.param.equals("cheese", ignoreCase = true) },
        reservations = data.reservations,
        hasMedalWall = data.medal == 1,
    )
}

internal fun buildInitialSpaceSuccessState(
    seed: SpaceInitialSeed,
    selectedMainTab: SpaceMainTab,
    selectedSubTab: SpaceSubTab = seed.defaultSubTab
): SpaceUiState.Success {
    val categories = extractSpaceVideoCategories(seed.videos)
    val shouldShowInitialVideoLoading = shouldHydrateSpaceContributionVideos(
        totalVideos = seed.totalVideos,
        seededVideoCount = seed.videos.size,
        pageSize = 30,
        selectedSubTab = selectedSubTab,
        selectedTid = 0,
        currentOrder = VideoSortOrder.PUBDATE,
        currentKeyword = ""
    )
    val seededContributionLoaded = seed.totalVideos > 0 || seed.totalAudios > 0 || seed.totalArticles > 0
    var tabShellState = buildInitialTabShellState(selectedTab = selectedMainTab)
        .withUpdatedTab(selectedMainTab) { it.copy(hasLoaded = true) }
    if (seededContributionLoaded) {
        tabShellState = tabShellState.withUpdatedTab(SpaceMainTab.CONTRIBUTION) { it.copy(hasLoaded = true) }
    }
    return SpaceUiState.Success(
        userInfo = seed.userInfo,
        relationStat = seed.relationStat,
        upStat = seed.upStat,
        videos = if (selectedSubTab == SpaceSubTab.CHARGING_VIDEO) emptyList() else seed.videos,
        totalVideos = if (selectedSubTab == SpaceSubTab.CHARGING_VIDEO) 0 else seed.totalVideos,
        homeVideos = seed.videos,
        homeVideoCount = seed.totalVideos,
        categories = categories,
        selectedSubTab = selectedSubTab,
        selectedContributionTabId = seed.defaultContributionTabId,
        contributionTabs = seed.contributionTabs,
        audios = seed.audios,
        articles = seed.articles,
        totalAudios = seed.totalAudios,
        totalArticles = seed.totalArticles,
        homeFavoriteFolders = seed.homeFavoriteFolders,
        homeFavoriteFolderCount = seed.homeFavoriteFolderCount,
        homeCoinVideos = seed.homeCoinVideos,
        homeCoinVideoCount = seed.homeCoinVideoCount,
        homeLikeVideos = seed.homeLikeVideos,
        homeLikeVideoCount = seed.homeLikeVideoCount,
        homeBangumiItems = seed.homeBangumiItems,
        homeBangumiCount = seed.homeBangumiCount,
        homeComicItems = seed.homeComicItems,
        homeComicCount = seed.homeComicCount,
        isLoadingMore = selectedSubTab != SpaceSubTab.CHARGING_VIDEO && shouldShowInitialVideoLoading,
        hasMoreVideos = seed.totalVideos > seed.videos.size,
        hasMoreAudios = seed.totalAudios > seed.audios.size,
        hasMoreArticles = seed.totalArticles > seed.articles.size,
        headerState = buildHeaderState(
            userInfo = seed.userInfo,
            relationStat = seed.relationStat,
            upStat = seed.upStat,
            topVideo = null,
            notice = "",
            createdFavorites = emptyList(),
            collectedFavorites = emptyList()
        ),
        tabShellState = tabShellState,
        mainTabs = seed.mainTabs,
        hasCheeseTab = seed.hasCheeseTab,
        reservations = seed.reservations,
        hasMedalWall = seed.hasMedalWall,
    )
}

internal fun resolveSpaceAggregateDefaultSelection(
    defaultTab: String,
    contributionTabs: List<SpaceContributionTab>
): Triple<SpaceMainTab, SpaceSubTab, String> {
    val contributionTab = when (defaultTab.lowercase()) {
        "article" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.ARTICLE }
            ?: contributionTabs.firstOrNull { it.subTab == SpaceSubTab.OPUS }
        "opus" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.OPUS }
            ?: contributionTabs.firstOrNull { it.subTab == SpaceSubTab.ARTICLE }
        "charging_video" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.CHARGING_VIDEO }
        "audio" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.AUDIO }
        "season_video" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.SEASON_VIDEO }
        "series" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.SERIES }
        "ugcseason" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.UGC_SEASON }
        "comic" -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.COMIC }
        else -> contributionTabs.firstOrNull { it.subTab == SpaceSubTab.VIDEO } ?: contributionTabs.firstOrNull()
    } ?: buildDefaultSpaceContributionTabs().first()

    return when (defaultTab.lowercase()) {
        "dynamic" -> Triple(SpaceMainTab.DYNAMIC, contributionTab.subTab, contributionTab.id)
        "home" -> Triple(SpaceMainTab.HOME, contributionTab.subTab, contributionTab.id)
        "favorite" -> Triple(SpaceMainTab.FAVORITE, contributionTab.subTab, contributionTab.id)
        "bangumi" -> Triple(SpaceMainTab.BANGUMI, contributionTab.subTab, contributionTab.id)
        "cheese" -> Triple(SpaceMainTab.CHEESE, contributionTab.subTab, contributionTab.id)
        "shop" -> Triple(SpaceMainTab.SHOP, contributionTab.subTab, contributionTab.id)
        "video", "contribute", "article", "opus", "audio", "charging_video", "season_video", "series", "ugcseason", "comic" ->
            Triple(SpaceMainTab.CONTRIBUTION, contributionTab.subTab, contributionTab.id)
        else -> Triple(SpaceMainTab.HOME, contributionTab.subTab, contributionTab.id)
    }
}

internal fun shouldHydrateSpaceContributionVideos(
    totalVideos: Int,
    seededVideoCount: Int,
    pageSize: Int,
    selectedSubTab: SpaceSubTab,
    selectedTid: Int,
    currentOrder: VideoSortOrder,
    currentKeyword: String
): Boolean {
    // VIDEO / CHARGING_VIDEO 共用 videos 列表，充电专属默认 Tab 也需要首屏补齐。
    if (selectedSubTab != SpaceSubTab.VIDEO && selectedSubTab != SpaceSubTab.CHARGING_VIDEO) {
        return false
    }
    if (totalVideos <= 0) return false
    val expectedVisibleCount = minOf(totalVideos, pageSize.coerceAtLeast(1))
    if (seededVideoCount >= expectedVisibleCount) return false
    if (selectedTid != 0) return false
    if (currentOrder != VideoSortOrder.PUBDATE) return false
    if (currentKeyword.isNotBlank()) return false
    return true
}

internal fun shouldApplySpaceVideoResult(
    requestMid: Long,
    activeMid: Long,
    requestGeneration: Long,
    activeGeneration: Long,
    requestTid: Int,
    activeTid: Int,
    requestOrder: VideoSortOrder,
    activeOrder: VideoSortOrder,
    requestKeyword: String,
    activeKeyword: String
): Boolean {
    return requestMid > 0L &&
        requestMid == activeMid &&
        requestGeneration == activeGeneration &&
        requestTid == activeTid &&
        requestOrder == activeOrder &&
        requestKeyword == activeKeyword
}

internal fun mergeSpaceVideoPages(
    existing: List<SpaceVideoItem>,
    incoming: List<SpaceVideoItem>
): List<SpaceVideoItem> {
    val seen = LinkedHashSet<String>()
    val merged = ArrayList<SpaceVideoItem>(existing.size + incoming.size)
    fun addAll(source: List<SpaceVideoItem>) {
        for (item in source) {
            val key = item.bvid.ifBlank { item.aid.toString() }
            if (seen.add(key)) {
                merged += item
            }
        }
    }
    addAll(existing)
    addAll(incoming)
    return merged
}

internal fun shouldContinueSpaceBangumiPagination(
    previousItemCount: Int,
    mergedItemCount: Int,
    incomingItemCount: Int,
    responsePage: Int,
    pageSize: Int,
    total: Int,
): Boolean {
    if (incomingItemCount <= 0 || mergedItemCount <= previousItemCount) return false
    if (mergedItemCount >= total.coerceAtLeast(0)) return false
    return responsePage.coerceAtLeast(1) * pageSize.coerceAtLeast(1) < total
}

private fun mapSpaceAggregateVideoItem(item: SpaceAggregateArchiveItem): SpaceVideoItem {
    return SpaceVideoItem(
        isCooperation = item.isCooperation,
        isSteins = item.isSteins,
        isLessonVideo = item.isLessonVideo,
        badges = item.badges + item.label.takeIf(String::isNotBlank)
            ?.let { listOf(com.android.purebilibili.data.model.response.SpaceAggregateBadge(it)) }.orEmpty(),
        aid = item.aid,
        bvid = item.bvid,
        title = item.title,
        pic = item.cover,
        play = item.play,
        comment = item.reply,
        length = item.length,
        created = item.ctime,
        author = item.author,
        typename = item.tname
    )
}

private fun mapSpaceAggregateFavoriteFolder(item: SpaceAggregateFavoriteItem): FavFolder {
    val resolvedId = item.mediaId.takeIf { it > 0L } ?: item.id.takeIf { it > 0L } ?: item.fid
    return FavFolder(
        id = resolvedId,
        fid = item.fid,
        mid = item.mid,
        title = item.title,
        media_count = item.media_count.takeIf { it > 0 } ?: item.count
    )
}

private fun extractSpaceVideoCategories(videos: List<SpaceVideoItem>): List<com.android.purebilibili.data.model.response.SpaceVideoCategory> {
    return videos
        .filter { it.typename.isNotBlank() }
        .groupBy { it.typename }
        .entries
        .mapIndexed { index, entry ->
            com.android.purebilibili.data.model.response.SpaceVideoCategory(
                tid = index + 1,
                name = entry.key,
                count = entry.value.size
            )
        }
}

private fun resolveSpaceVideoLastPage(totalCount: Int, pageSize: Int): Int {
    if (totalCount <= 0 || pageSize <= 0) return 1
    return ((totalCount - 1) / pageSize) + 1
}
