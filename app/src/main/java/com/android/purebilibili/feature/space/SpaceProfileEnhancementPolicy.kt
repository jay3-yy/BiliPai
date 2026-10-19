package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.*

enum class SpaceMainTab {
    HOME,
    DYNAMIC,
    CONTRIBUTION,
    FAVORITE,
    BANGUMI,
    COLLECTIONS,
    CHEESE
}

data class SpaceMainTabItem(
    val tab: SpaceMainTab,
    val title: String
)

data class SpaceContributionTab(
    val id: String,
    val title: String,
    val subTab: SpaceSubTab,
    val param: String,
    val seasonId: Long = 0,
    val seriesId: Long = 0
)

data class SpaceTabContentState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false
)

data class SpaceTabShellState(
    val selectedTab: SpaceMainTab,
    val tabStates: Map<SpaceMainTab, SpaceTabContentState>
) {
    fun withUpdatedTab(tab: SpaceMainTab, transform: (SpaceTabContentState) -> SpaceTabContentState): SpaceTabShellState {
        val current = tabStates[tab] ?: SpaceTabContentState()
        return copy(
            tabStates = tabStates + (tab to transform(current))
        )
    }

    fun withSelectedTab(tab: SpaceMainTab): SpaceTabShellState {
        return if (tab == selectedTab) this else copy(selectedTab = tab)
    }
}

data class SpaceHeaderState(
    val userInfo: SpaceUserInfo?,
    val relationStat: RelationStatData?,
    val upStat: UpStatData?,
    val topVideo: SpaceTopArcData?,
    val notice: String,
    val createdFavorites: List<FavFolder>,
    val collectedFavorites: List<FavFolder>
)

fun buildDefaultSpaceMainTabs(): List<SpaceMainTabItem> {
    return listOf(
        SpaceMainTabItem(SpaceMainTab.HOME, "主页"),
        SpaceMainTabItem(SpaceMainTab.DYNAMIC, "动态"),
        SpaceMainTabItem(SpaceMainTab.CONTRIBUTION, "投稿"),
        SpaceMainTabItem(SpaceMainTab.COLLECTIONS, "合集"),
        SpaceMainTabItem(SpaceMainTab.FAVORITE, "收藏"),
        SpaceMainTabItem(SpaceMainTab.BANGUMI, "追番"),
    )
}

/** All library destinations share the primary rail; only contribution categories are secondary. */
internal fun resolveSpaceDisplayedMainTabs(
    tabs: List<SpaceMainTabItem>,
    selectedTab: SpaceMainTab,
    hasCheese: Boolean = false,
    cheeseTitle: String = "课堂",
): List<SpaceMainTabItem> {
    val defaults = buildDefaultSpaceMainTabs()
    val base = if (tabs.isEmpty()) defaults else {
        defaults.map { default ->
            tabs.firstOrNull { it.tab == default.tab } ?: default
        }
    }
    val result = base.toMutableList()
    val cheeseTab = tabs.firstOrNull { it.tab == SpaceMainTab.CHEESE }
    if (cheeseTab != null) {
        result.add(cheeseTab)
    } else if (hasCheese || selectedTab == SpaceMainTab.CHEESE) {
        result.add(SpaceMainTabItem(SpaceMainTab.CHEESE, cheeseTitle.ifBlank { "课堂" }))
    }
    return result
}

fun buildDefaultSpaceContributionTabs(): List<SpaceContributionTab> {
    return listOf(
        SpaceContributionTab(
            id = createSpaceContributionTabId(param = "video"),
            title = "视频",
            subTab = SpaceSubTab.VIDEO,
            param = "video"
        ),
        SpaceContributionTab(
            id = createSpaceContributionTabId(param = "article"),
            title = "图文",
            subTab = SpaceSubTab.ARTICLE,
            param = "article"
        ),
        SpaceContributionTab(
            id = createSpaceContributionTabId(param = "audio"),
            title = "音频",
            subTab = SpaceSubTab.AUDIO,
            param = "audio"
        )
    )
}

fun buildInitialTabShellState(selectedTab: SpaceMainTab = SpaceMainTab.HOME): SpaceTabShellState {
    val tabs = SpaceMainTab.values()
    return SpaceTabShellState(
        selectedTab = selectedTab,
        tabStates = tabs.associateWith { SpaceTabContentState() }
    )
}

fun tabIndexToMainTab(index: Int): SpaceMainTab {
    return when (index) {
        0 -> SpaceMainTab.HOME
        1 -> SpaceMainTab.DYNAMIC
        2 -> SpaceMainTab.CONTRIBUTION
        3 -> SpaceMainTab.FAVORITE
        4 -> SpaceMainTab.BANGUMI
        5 -> SpaceMainTab.COLLECTIONS
        6 -> SpaceMainTab.CHEESE
        else -> SpaceMainTab.HOME
    }
}

fun mainTabToTabIndex(tab: SpaceMainTab): Int {
    return when (tab) {
        SpaceMainTab.HOME -> 0
        SpaceMainTab.DYNAMIC -> 1
        SpaceMainTab.CONTRIBUTION -> 2
        SpaceMainTab.FAVORITE -> 3
        SpaceMainTab.BANGUMI -> 4
        SpaceMainTab.COLLECTIONS -> 5
        SpaceMainTab.CHEESE -> 6
    }
}

fun buildHeaderState(
    userInfo: SpaceUserInfo?,
    relationStat: RelationStatData?,
    upStat: UpStatData?,
    topVideo: SpaceTopArcData?,
    notice: String,
    createdFavorites: List<FavFolder>,
    collectedFavorites: List<FavFolder>
): SpaceHeaderState {
    return SpaceHeaderState(
        userInfo = userInfo,
        relationStat = relationStat,
        upStat = upStat,
        topVideo = topVideo,
        notice = notice,
        createdFavorites = createdFavorites,
        collectedFavorites = collectedFavorites
    )
}

internal fun resolveSpaceMainTabs(tab2: List<SpaceAggregateTab>): List<SpaceMainTabItem> {
    val defaults = buildDefaultSpaceMainTabs()
    if (tab2.isEmpty()) return defaults

    val resolved = tab2.mapNotNull { item ->
        when (item.param.lowercase()) {
            "home" -> SpaceMainTabItem(SpaceMainTab.HOME, item.title.ifBlank { "主页" })
            "dynamic" -> SpaceMainTabItem(SpaceMainTab.DYNAMIC, item.title.ifBlank { "动态" })
            "contribute" -> SpaceMainTabItem(SpaceMainTab.CONTRIBUTION, item.title.ifBlank { "投稿" })
            "favorite" -> SpaceMainTabItem(SpaceMainTab.FAVORITE, item.title.ifBlank { "收藏" })
            "bangumi" -> SpaceMainTabItem(SpaceMainTab.BANGUMI, item.title.ifBlank { "追番" })
            "channel", "collection", "collections", "series" ->
                SpaceMainTabItem(SpaceMainTab.COLLECTIONS, item.title.ifBlank { "合集" })
            "cheese" -> SpaceMainTabItem(SpaceMainTab.CHEESE, item.title.ifBlank { "课堂" })
            else -> null
        }
    }.distinctBy { it.tab }

    if (resolved.isEmpty()) return defaults

    val result = defaults.map { default ->
        resolved.firstOrNull { it.tab == default.tab } ?: default
    }.toMutableList()

    resolved.firstOrNull { it.tab == SpaceMainTab.CHEESE }?.let(result::add)

    return result
}

internal fun resolveSpaceContributionTabs(tab2: List<SpaceAggregateTab>): List<SpaceContributionTab> {
    val contributeTab = tab2.firstOrNull { it.param.equals("contribute", ignoreCase = true) }
    val resolved = contributeTab
        ?.items
        ?.mapNotNull { item ->
            val mappedSubTab = resolveSpaceContributionSubTab(item.param)
            mappedSubTab?.let {
                SpaceContributionTab(
                    id = createSpaceContributionTabId(
                        param = item.param,
                        seasonId = item.seasonId,
                        seriesId = item.seriesId
                    ),
                    title = item.title.ifBlank { resolveSpaceContributionTitleFallback(it) },
                    subTab = it,
                    param = item.param,
                    seasonId = item.seasonId,
                    seriesId = item.seriesId
                )
            }
        }
        .orEmpty()
        .distinctBy { it.id }

    return resolved.ifEmpty { buildDefaultSpaceContributionTabs() }
}

internal fun resolveSelectedContributionTab(
    tabs: List<SpaceContributionTab>,
    selectedTabId: String,
    selectedSubTab: SpaceSubTab
): SpaceContributionTab {
    return tabs.firstOrNull { it.id == selectedTabId }
        ?: tabs.firstOrNull { it.subTab == selectedSubTab }
        ?: tabs.firstOrNull()
        ?: buildDefaultSpaceContributionTabs().first()
}

internal fun mergeSpaceContributionTabsWithCollections(
    baseTabs: List<SpaceContributionTab>,
    seasons: List<SeasonItem>,
    series: List<SeriesItem>
): List<SpaceContributionTab> {
    val merged = mutableListOf<SpaceContributionTab>()
    val seenIds = HashSet<String>()

    fun add(tab: SpaceContributionTab) {
        if (seenIds.add(tab.id)) merged += tab
    }

    baseTabs.firstOrNull { it.subTab == SpaceSubTab.VIDEO }?.let(::add)
    baseTabs.firstOrNull { it.subTab == SpaceSubTab.ARTICLE || it.subTab == SpaceSubTab.OPUS }?.let(::add)

    seasons.forEach { season ->
        val seasonId = season.meta.season_id
        if (seasonId > 0L && season.meta.name.isNotBlank()) {
            add(
                SpaceContributionTab(
                    id = createSpaceContributionTabId(param = "season_video", seasonId = seasonId),
                    title = season.meta.name,
                    subTab = SpaceSubTab.SEASON_VIDEO,
                    param = "season_video",
                    seasonId = seasonId
                )
            )
        }
    }

    series.forEach { seriesItem ->
        val seriesId = seriesItem.meta.series_id
        if (seriesId > 0L && seriesItem.meta.name.isNotBlank()) {
            add(
                SpaceContributionTab(
                    id = createSpaceContributionTabId(param = "series", seriesId = seriesId),
                    title = seriesItem.meta.name,
                    subTab = SpaceSubTab.SERIES,
                    param = "series",
                    seriesId = seriesId
                )
            )
        }
    }

    baseTabs.firstOrNull { it.subTab == SpaceSubTab.AUDIO }?.let(::add)
    baseTabs
        .filterNot {
            it.subTab in setOf(
                SpaceSubTab.VIDEO,
                SpaceSubTab.ARTICLE,
                SpaceSubTab.OPUS,
                SpaceSubTab.SEASON_VIDEO,
                SpaceSubTab.SERIES,
                SpaceSubTab.AUDIO
            )
        }
        .forEach(::add)

    return if (merged.isNotEmpty()) merged else baseTabs
}

internal fun resolveDisplayedSpaceContributionTabs(
    tabs: List<SpaceContributionTab>,
    totalAudios: Int
): List<SpaceContributionTab> {
    return tabs.filterNot { it.subTab == SpaceSubTab.AUDIO && totalAudios <= 0 }
}

internal fun ensureSpaceContributionTabsForAvailableContent(
    tabs: List<SpaceContributionTab>,
    hasArticles: Boolean
): List<SpaceContributionTab> {
    val result = tabs.toMutableList()
    val defaults = buildDefaultSpaceContributionTabs()

    fun addDefaultIfMissing(
        shouldAdd: Boolean,
        matches: (SpaceContributionTab) -> Boolean
    ) {
        if (!shouldAdd || result.any(matches)) return
        defaults.firstOrNull(matches)?.let { result += it }
    }

    addDefaultIfMissing(hasArticles) {
        it.subTab == SpaceSubTab.ARTICLE || it.subTab == SpaceSubTab.OPUS
    }

    return result.distinctBy { it.id }
}

internal fun createSpaceContributionTabId(
    param: String,
    seasonId: Long = 0,
    seriesId: Long = 0
): String {
    return buildString {
        append(param.ifBlank { "video" })
        if (seasonId > 0L) {
            append(":season:")
            append(seasonId)
        }
        if (seriesId > 0L) {
            append(":series:")
            append(seriesId)
        }
    }
}

private fun resolveSpaceContributionSubTab(param: String): SpaceSubTab? {
    return when (param) {
        "video" -> SpaceSubTab.VIDEO
        "charging_video" -> SpaceSubTab.CHARGING_VIDEO
        "article" -> SpaceSubTab.ARTICLE
        "opus" -> SpaceSubTab.OPUS
        "audio" -> SpaceSubTab.AUDIO
        "season_video" -> SpaceSubTab.SEASON_VIDEO
        "series" -> SpaceSubTab.SERIES
        "ugcSeason" -> SpaceSubTab.UGC_SEASON
        "comic" -> SpaceSubTab.COMIC
        else -> null
    }
}

private fun resolveSpaceContributionTitleFallback(subTab: SpaceSubTab): String {
    return when (subTab) {
        SpaceSubTab.VIDEO -> "视频"
        SpaceSubTab.CHARGING_VIDEO -> "充电专属"
        SpaceSubTab.ARTICLE -> "图文"
        SpaceSubTab.OPUS -> "图文"
        SpaceSubTab.AUDIO -> "音频"
        SpaceSubTab.SEASON_VIDEO -> "合集"
        SpaceSubTab.SERIES -> "系列"
        SpaceSubTab.UGC_SEASON -> "合集和系列"
        SpaceSubTab.COMIC -> "漫画"
    }
}

internal fun shouldEnableSpaceTopPhotoPreview(topPhotoUrl: String): Boolean {
    return normalizeSpaceTopPhotoUrl(topPhotoUrl).isNotBlank()
}

internal fun resolveSpaceTopPhoto(
    topPhoto: String,
    cardLargePhoto: String,
    cardSmallPhoto: String
): String {
    return sequenceOf(topPhoto, cardLargePhoto, cardSmallPhoto)
        .map { normalizeSpaceTopPhotoUrl(it) }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
}

internal fun normalizeSpaceTopPhotoUrl(url: String): String {
    val candidate = url.trim()
    if (candidate.isEmpty()) return ""
    val lower = candidate.lowercase()
    if (
        lower == "null" ||
        lower == "nil" ||
        lower == "none" ||
        lower == "undefined" ||
        lower == "[]" ||
        lower == "{}" ||
        lower == "n/a" ||
        lower == "about:blank"
    ) {
        return ""
    }
    return when {
        candidate.startsWith("//") -> "https:$candidate"
        candidate.startsWith("http://", ignoreCase = true) -> {
            "https://${candidate.substring(startIndex = "http://".length)}"
        }
        else -> candidate
    }
}

internal fun resolveSpaceFavoriteFoldersForDisplay(folders: List<FavFolder>): List<FavFolder> {
    if (folders.isEmpty()) return emptyList()
    val seenIds = HashSet<Long>()
    return folders.filter { folder ->
        val valid = folder.id > 0L &&
            folder.title.isNotBlank() &&
            folder.media_count > 0
        valid && seenIds.add(folder.id)
    }
}

internal fun resolveSpaceCollectionTabCount(
    seasonCount: Int,
    seriesCount: Int,
    createdFavoriteCount: Int,
    collectedFavoriteCount: Int
): Int {
    return seasonCount.coerceAtLeast(0) +
        seriesCount.coerceAtLeast(0) +
        createdFavoriteCount.coerceAtLeast(0) +
        collectedFavoriteCount.coerceAtLeast(0)
}
