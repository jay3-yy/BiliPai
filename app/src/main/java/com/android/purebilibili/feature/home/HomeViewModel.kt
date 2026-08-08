// 文件路径: feature/home/HomeViewModel.kt
package com.android.purebilibili.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.plugin.FeedKind
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.RecommendationCreatorSignal
import com.android.purebilibili.core.plugin.RecommendationFeedbackSignals
import com.android.purebilibili.core.plugin.RecommendationGroup
import com.android.purebilibili.core.plugin.RecommendationMode
import com.android.purebilibili.core.plugin.RecommendationPluginApi
import com.android.purebilibili.core.plugin.RecommendationRequest
import com.android.purebilibili.core.plugin.RecommendationResult
import com.android.purebilibili.core.plugin.RecommendationSceneSignals
import com.android.purebilibili.feature.plugin.ADFILTER_PLUGIN_ID
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TodayWatchDislikedVideoSnapshot
import com.android.purebilibili.core.store.TodayWatchFeedbackStore
import com.android.purebilibili.core.store.TodayWatchProfileStore
import com.android.purebilibili.core.store.withDislikedVideoFeedback
import com.android.purebilibili.core.util.appendDistinctByKey
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.prependDistinctByKey
import com.android.purebilibili.data.model.response.LiveRoom
import com.android.purebilibili.data.model.response.RecommendationFeedbackLocalAction
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.HistoryRepository
import com.android.purebilibili.data.repository.MessageRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.LiveRepository
import com.android.purebilibili.feature.message.totalMessageUnreadCount
import com.android.purebilibili.feature.plugin.EyeProtectionPlugin
import com.android.purebilibili.feature.plugin.TodayWatchPlugin
import com.android.purebilibili.feature.plugin.TodayWatchPluginConfig
import com.android.purebilibili.feature.plugin.TodayWatchPluginMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

// 状态类已移至 HomeUiState.kt

internal fun trimIncrementalRefreshVideosToEvenCount(videos: List<VideoItem>): List<VideoItem> {
    val size = videos.size
    if (size <= 1 || size % 2 == 0) return videos
    return videos.dropLast(1)
}

internal fun selectHomeFeedIncomingVideos(
    responseVideos: List<VideoItem>,
    currentVideos: List<VideoItem>,
    isLoadMore: Boolean,
    isIncrementalRefresh: Boolean,
): List<VideoItem> {
    val responseDistinct = responseVideos.distinctBy { it.bvid }
    val currentBvids = currentVideos.asSequence().map { it.bvid }.filter { it.isNotBlank() }.toHashSet()
    val outsideCurrentList = if (isLoadMore || isIncrementalRefresh) {
        responseDistinct.filter { it.bvid !in currentBvids }
    } else {
        responseDistinct
    }
    return if (isIncrementalRefresh) {
        trimIncrementalRefreshVideosToEvenCount(outsideCurrentList)
    } else {
        outsideCurrentList
    }
}

internal fun resolveRecommendFeedRequestIndex(
    isLoadMore: Boolean,
    isManualRefresh: Boolean,
    currentRefreshIndex: Int
): Int {
    return if (isLoadMore || isManualRefresh) {
        currentRefreshIndex + 1
    } else {
        0
    }
}

/**
 * 首页分类 → 插件信息流来源(供 FeedPlugin 按来源区分过滤行为)。
 */
internal fun HomeCategory.toFeedKind(popularSubCategory: PopularSubCategory? = null): FeedKind {
    return when (this) {
        HomeCategory.RECOMMEND -> FeedKind.HOME_RECOMMEND
        HomeCategory.POPULAR -> when (popularSubCategory) {
            PopularSubCategory.RANKING -> FeedKind.HOME_RANK
            else -> FeedKind.HOME_POPULAR
        }
        else -> FeedKind.HOME_REGION
    }
}

internal fun shouldAdvanceRecommendFeedRequestIndex(
    category: HomeCategory,
    isLoadMore: Boolean,
    isManualRefresh: Boolean,
    validVideoCount: Int
): Boolean {
    return category == HomeCategory.RECOMMEND &&
        (isLoadMore || isManualRefresh) &&
        validVideoCount > 0
}

internal data class HomeRefreshUndoSnapshot(
    val videos: List<VideoItem>,
    val pageIndex: Int,
    val hasMore: Boolean
)

internal fun buildHomeRefreshUndoSnapshot(
    refreshingCategory: HomeCategory,
    recommendCategoryState: CategoryContent?,
    fallbackVideos: List<VideoItem>,
    maxItems: Int = 20
): HomeRefreshUndoSnapshot? {
    if (refreshingCategory != HomeCategory.RECOMMEND) return null
    val sourceVideos = recommendCategoryState?.videos ?: fallbackVideos
    if (sourceVideos.isEmpty()) return null
    val sourcePageIndex = recommendCategoryState?.pageIndex ?: 1
    val sourceHasMore = recommendCategoryState?.hasMore ?: true
    return HomeRefreshUndoSnapshot(
        videos = sourceVideos.take(maxItems.coerceAtLeast(1)),
        pageIndex = sourcePageIndex,
        hasMore = sourceHasMore
    )
}

internal fun shouldExposeHomeRefreshUndo(
    refreshingCategory: HomeCategory,
    snapshot: HomeRefreshUndoSnapshot?
): Boolean {
    return refreshingCategory == HomeCategory.RECOMMEND && snapshot != null
}

internal fun shouldRefreshHomeUserInfoAfterFeedLoad(isLoadMore: Boolean): Boolean {
    return !isLoadMore
}

internal fun shouldKeepHomeCategoryAutoPagingAfterFailure(isLoadMore: Boolean): Boolean {
    return !isLoadMore
}

internal fun applyHomeRefreshUndoSnapshot(
    oldState: CategoryContent,
    snapshot: HomeRefreshUndoSnapshot
): CategoryContent {
    return oldState.copy(
        videos = snapshot.videos.toImmutableList(),
        pageIndex = snapshot.pageIndex,
        hasMore = snapshot.hasMore,
        isLoading = false,
        error = null
    )
}

private const val HISTORY_SAMPLE_CACHE_TTL_MS = 10 * 60 * 1000L
private const val HOME_REFRESH_UNDO_TIMEOUT_MS = 5_000L

private fun TodayWatchPluginMode.toUiMode(): TodayWatchMode {
    return when (this) {
        TodayWatchPluginMode.RELAX -> TodayWatchMode.RELAX
        TodayWatchPluginMode.LEARN -> TodayWatchMode.LEARN
    }
}

private fun TodayWatchMode.toPluginMode(): TodayWatchPluginMode {
    return when (this) {
        TodayWatchMode.RELAX -> TodayWatchPluginMode.RELAX
        TodayWatchMode.LEARN -> TodayWatchPluginMode.LEARN
    }
}

private fun TodayWatchMode.toRecommendationMode(): RecommendationMode {
    return when (this) {
        TodayWatchMode.RELAX -> RecommendationMode.RELAX
        TodayWatchMode.LEARN -> RecommendationMode.LEARN
    }
}

private fun RecommendationMode.toTodayWatchMode(): TodayWatchMode {
    return when (this) {
        RecommendationMode.RELAX -> TodayWatchMode.RELAX
        RecommendationMode.LEARN -> TodayWatchMode.LEARN
    }
}

private fun RecommendationResult.toTodayWatchPlan(): TodayWatchPlan {
    val creatorGroup = groups.firstOrNull { it.id == "preferred_creators" }
    return TodayWatchPlan(
        mode = mode.toTodayWatchMode(),
        upRanks = creatorGroup.toTodayUpRanks().toImmutableList(),
        videoQueue = items.map { it.video }.toImmutableList(),
        explanationByBvid = items.associate { it.video.bvid to it.explanation }.toImmutableMap(),
        historySampleCount = historySampleCount,
        nightSignalUsed = sceneSignals.eyeCareNightActive,
        generatedAt = generatedAt
    )
}

private fun RecommendationGroup?.toTodayUpRanks(): List<TodayUpRank> {
    return this?.items.orEmpty().mapNotNull { item ->
        val mid = item.id.toLongOrNull() ?: return@mapNotNull null
        TodayUpRank(
            mid = mid,
            name = item.title,
            score = item.score ?: 0.0,
            watchCount = 1
        )
    }
}

private data class TodayWatchRuntimeConfig(
    val enabled: Boolean,
    val mode: TodayWatchMode,
    val upRankLimit: Int,
    val queueBuildLimit: Int,
    val queuePreviewLimit: Int,
    val historySampleLimit: Int,
    val linkEyeCareSignal: Boolean,
    val showUpRank: Boolean,
    val showReasonHint: Boolean,
    val enableWaterfallAnimation: Boolean,
    val waterfallExponent: Float,
    val collapsed: Boolean
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            // 初始化所有分类的状态
            categoryStates = HomeCategory.entries.associateWith { CategoryContent() }.toImmutableMap(),
            popularCategoryStates = PopularSubCategory.entries.associateWith { CategoryContent() }.toImmutableMap()
        )
    )
    val uiState = _uiState.asStateFlow()
    val currentCategory = homeStateFlow { it.currentCategory }
    val displayedTabIndex = homeStateFlow { it.displayedTabIndex }
    val popularSubCategory = homeStateFlow { it.popularSubCategory }
    val liveSubCategory = homeStateFlow { it.liveSubCategory }
    val user = homeStateFlow { it.user }
    val messageUnreadCount = homeStateFlow { it.messageUnreadCount }
    val refreshKey = homeStateFlow { it.refreshKey }
    val refreshMessage = homeStateFlow { it.refreshMessage }
    val refreshNewItemsCount = homeStateFlow { it.refreshNewItemsCount }
    val refreshNewItemsKey = homeStateFlow { it.refreshNewItemsKey }
    val refreshNewItemsHandledKey = homeStateFlow { it.refreshNewItemsHandledKey }
    val recommendOldContentAnchorBvid = homeStateFlow { it.recommendOldContentAnchorBvid }
    val recommendOldContentStartIndex = homeStateFlow { it.recommendOldContentStartIndex }
    val recommendOldContentRevealKey = homeStateFlow { it.recommendOldContentRevealKey }
    val dissolvingVideos = homeStateFlow { it.dissolvingVideos }
    val followingMids = homeStateFlow { it.followingMids }
    val todayWatchMode = homeStateFlow { it.todayWatchMode }
    val todayWatchPlan = homeStateFlow { it.todayWatchPlan }
    val todayWatchLoading = homeStateFlow { it.todayWatchLoading }
    val todayWatchError = homeStateFlow { it.todayWatchError }
    val todayWatchPluginEnabled = homeStateFlow { it.todayWatchPluginEnabled }
    val todayWatchCollapsed = homeStateFlow { it.todayWatchCollapsed }
    val todayWatchCardConfig = homeStateFlow { it.todayWatchCardConfig }
    val undoAvailable = homeStateFlow { it.undoAvailable }

    private val categoryStateFlows = HomeCategory.entries.associateWith { category ->
        homeStateFlow { it.categoryStates[category] ?: CategoryContent() }
    }
    private val popularCategoryStateFlows = PopularSubCategory.entries.associateWith { subCategory ->
        homeStateFlow { it.popularCategoryStates[subCategory] ?: CategoryContent() }
    }

    fun getCategoryState(category: HomeCategory): StateFlow<CategoryContent> = categoryStateFlows.getValue(category)

    fun getPopularCategoryState(subCategory: PopularSubCategory): StateFlow<CategoryContent> =
        popularCategoryStateFlows.getValue(subCategory)

    fun getPreloadVideosSnapshot(
        category: HomeCategory,
        popularSubCategory: PopularSubCategory
    ): List<VideoItem> {
        val state = _uiState.value
        return if (category == HomeCategory.POPULAR) {
            state.popularCategoryStates[popularSubCategory]?.videos ?: state.videos
        } else {
            state.categoryStates[category]?.videos ?: state.videos
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private fun <T> homeStateFlow(selector: (HomeUiState) -> T): StateFlow<T> {
        return _uiState
            .map(selector)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = selector(_uiState.value)
            )
    }

    private var refreshIdx = 0
    private var livePage = 1     //  直播分页
    private var hasMoreLiveData = true  //  是否还有更多直播数据
    private var incrementalTimelineRefreshEnabled = false
    
    //  [新增] 刷新撤销快照
    private var _undoSnapshot: HomeRefreshUndoSnapshot? = null
    private var undoDismissJob: Job? = null
    private var userInfoRefreshJob: Job? = null
    private var messageUnreadRefreshJob: Job? = null
    private var categoryInitialLoadJob: Job? = null

    // [Feature] Blocked UPs
    private val blockedUpRepository = com.android.purebilibili.data.repository.BlockedUpRepository(application)
    private var blockedMids: Set<Long> = emptySet()
    private var historySampleCache: List<VideoItem> = emptyList()
    private var historySampleLoadedAtMs: Long = 0L
    private val todayConsumedBvids = mutableSetOf<String>()
    private val todayDislikedBvids = mutableSetOf<String>()
    private val todayDislikedCreatorMids = mutableSetOf<Long>()
    private val todayDislikedKeywords = linkedSetOf<String>()
    private val pendingNotInterestedRefilterBvids = mutableSetOf<String>()
    private val _feedbackEvents = Channel<String>(capacity = Channel.BUFFERED)
    val feedbackEvents = _feedbackEvents.receiveAsFlow()
    private var todayWatchPluginObserverJob: Job? = null
    private var observedTodayWatchPlugin: TodayWatchPlugin? = null

    init {
        viewModelScope.launch {
            SettingsManager.getIncrementalTimelineRefresh(getApplication()).collect { enabled ->
                incrementalTimelineRefreshEnabled = enabled
            }
        }
        // Monitor blocked list
        viewModelScope.launch {
            blockedUpRepository.getAllBlockedUps().collect { list ->
                blockedMids = list.map { it.mid }.toSet()
                if (pendingNotInterestedRefilterBvids.isEmpty()) {
                    reFilterAllContent()
                }
            }
        }
        syncTodayWatchFeedbackFromStore()
        viewModelScope.launch {
            PluginManager.awaitPluginReady(ADFILTER_PLUGIN_ID)
            reFilterAllContent()
        }
        viewModelScope.launch {
            PluginManager.pluginsFlow.collect { plugins ->
                val plugin = plugins.find { it.plugin.id == TodayWatchPlugin.PLUGIN_ID }?.plugin as? TodayWatchPlugin
                if (plugin !== observedTodayWatchPlugin) {
                    todayWatchPluginObserverJob?.cancel()
                    observedTodayWatchPlugin = plugin
                    if (plugin != null) {
                        todayWatchPluginObserverJob = viewModelScope.launch {
                            plugin.configState.collect {
                                val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
                                if (shouldAutoRebuildTodayWatchPlan(
                                        currentCategory = _uiState.value.currentCategory,
                                        isTodayWatchEnabled = runtime.enabled,
                                        isTodayWatchCollapsed = runtime.collapsed
                                    )
                                ) {
                                    rebuildTodayWatchPlan()
                                }
                            }
                        }
                    } else {
                        todayWatchPluginObserverJob = null
                    }
                }
                val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
                if (shouldAutoRebuildTodayWatchPlan(
                        currentCategory = _uiState.value.currentCategory,
                        isTodayWatchEnabled = runtime.enabled,
                        isTodayWatchCollapsed = runtime.collapsed
                    )
                ) {
                    rebuildTodayWatchPlan()
                }
            }
        }
        loadData()
    }
    
    // [Feature] Re-filter all content when block list changes
    private fun reFilterAllContent() {
        val oldState = _uiState.value
        val newCategoryStates = oldState.categoryStates.mapValues { (category, content) ->
            content.copy(
                videos = PluginManager.filterFeedItems(
                    filterHomeFeedbackVideos(content.videos.filter { it.owner.mid !in blockedMids }),
                    feedKind = category.toFeedKind(popularSubCategory.value)
                ).toImmutableList(),
                // Filter live rooms if possible (assuming uid matches mid)
                liveRooms = content.liveRooms.filter { it.uid !in blockedMids }.toImmutableList(),
                followedLiveRooms = content.followedLiveRooms.filter { it.uid !in blockedMids }.toImmutableList()
            )
        }.toImmutableMap()
        
        var newState = oldState.copy(categoryStates = newCategoryStates)
        
        // Sync legacy fields for current category
        val currentContent = newCategoryStates[newState.currentCategory]
        if (currentContent != null) {
            newState = newState.copy(
                videos = currentContent.videos,
                liveRooms = currentContent.liveRooms,
                followedLiveRooms = currentContent.followedLiveRooms
            )
        }
        
        _uiState.value = newState
        viewModelScope.launch {
            val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
            if (shouldAutoRebuildTodayWatchPlan(
                    currentCategory = _uiState.value.currentCategory,
                    isTodayWatchEnabled = runtime.enabled,
                    isTodayWatchCollapsed = runtime.collapsed
                )
            ) {
                rebuildTodayWatchPlan()
            }
        }
    }

    private fun resolveTodayWatchRuntimeConfig(
        pluginEnabled: Boolean,
        config: TodayWatchPluginConfig
    ): TodayWatchRuntimeConfig {
        return TodayWatchRuntimeConfig(
            enabled = pluginEnabled,
            mode = config.currentMode.toUiMode(),
            upRankLimit = config.upRankLimit,
            queueBuildLimit = config.queueBuildLimit,
            queuePreviewLimit = config.queuePreviewLimit,
            historySampleLimit = config.historySampleLimit,
            linkEyeCareSignal = config.linkEyeCareSignal,
            showUpRank = config.showUpRank,
            showReasonHint = config.showReasonHint,
            enableWaterfallAnimation = config.enableWaterfallAnimation,
            waterfallExponent = config.waterfallExponent,
            collapsed = config.collapsed
        )
    }

    private fun syncTodayWatchPluginState(clearWhenDisabled: Boolean): TodayWatchRuntimeConfig {
        val info = PluginManager.plugins.find { it.plugin.id == TodayWatchPlugin.PLUGIN_ID }
        val pluginEnabled = info?.enabled == true
        val plugin = info?.plugin as? TodayWatchPlugin
        val config = plugin?.configState?.value ?: TodayWatchPluginConfig()
        val runtime = resolveTodayWatchRuntimeConfig(pluginEnabled = pluginEnabled, config = config)

        val currentState = _uiState.value
        var nextState = currentState.copy(
            todayWatchPluginEnabled = runtime.enabled,
            todayWatchMode = runtime.mode,
            todayWatchCollapsed = runtime.collapsed,
            todayWatchCardConfig = TodayWatchCardUiConfig(
                showUpRank = runtime.showUpRank,
                showReasonHint = runtime.showReasonHint,
                queuePreviewLimit = runtime.queuePreviewLimit,
                enableWaterfallAnimation = runtime.enableWaterfallAnimation,
                waterfallExponent = runtime.waterfallExponent
            )
        )

        if (!runtime.enabled && clearWhenDisabled) {
            nextState = nextState.copy(
                todayWatchPlan = null,
                todayWatchLoading = false,
                todayWatchError = null
            )
        }
        if (nextState != currentState) {
            _uiState.value = nextState
        }
        return runtime
    }

    fun switchTodayWatchMode(mode: TodayWatchMode) {
        val info = PluginManager.plugins.find { it.plugin.id == TodayWatchPlugin.PLUGIN_ID }
        if (info?.enabled != true) return

        val plugin = info.plugin as? TodayWatchPlugin
        plugin?.setCurrentMode(mode.toPluginMode())
        _uiState.value = _uiState.value.copy(todayWatchMode = mode)
        viewModelScope.launch {
            rebuildTodayWatchPlan()
        }
    }

    fun setTodayWatchCollapsed(collapsed: Boolean) {
        val info = PluginManager.plugins.find { it.plugin.id == TodayWatchPlugin.PLUGIN_ID }
        val plugin = info?.plugin as? TodayWatchPlugin
        plugin?.updateConfig { current -> current.copy(collapsed = collapsed) }

        val current = _uiState.value
        if (current.todayWatchCollapsed == collapsed) return
        _uiState.value = current.copy(todayWatchCollapsed = collapsed)

        if (!collapsed) {
            viewModelScope.launch {
                val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
                if (shouldAutoRebuildTodayWatchPlan(
                        currentCategory = _uiState.value.currentCategory,
                        isTodayWatchEnabled = runtime.enabled,
                        isTodayWatchCollapsed = runtime.collapsed
                    )
                ) {
                    rebuildTodayWatchPlan()
                }
            }
        }
    }

    fun refreshTodayWatchOnly() {
        val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
        if (!runtime.enabled) return

        todayConsumedBvids += collectTodayWatchConsumedForManualRefresh(
            plan = _uiState.value.todayWatchPlan,
            previewLimit = _uiState.value.todayWatchCardConfig.queuePreviewLimit
        )
        viewModelScope.launch {
            rebuildTodayWatchPlan(forceReloadHistory = false)
        }
    }

    private suspend fun rebuildTodayWatchPlan(forceReloadHistory: Boolean = false) {
        val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
        if (!runtime.enabled) {
            return
        }
        syncTodayWatchFeedbackFromStore()

        val recommendVideos = getRecommendCandidates()
        if (recommendVideos.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                todayWatchPlan = null,
                todayWatchLoading = false,
                todayWatchError = null
            )
            return
        }

        _uiState.value = _uiState.value.copy(todayWatchLoading = true, todayWatchError = null)

        val historySample = loadHistorySample(
            forceReload = forceReloadHistory,
            sampleLimit = runtime.historySampleLimit
        )
        val creatorSignals = TodayWatchProfileStore.getCreatorSignals(
            context = getApplication(),
            limit = runtime.historySampleLimit / 4
        ).map {
            RecommendationCreatorSignal(
                mid = it.mid,
                name = it.name,
                score = it.score,
                watchCount = it.watchCount
            )
        }
        val eyeCareNightActive = runtime.linkEyeCareSignal &&
            EyeProtectionPlugin.getInstance()?.isNightModeActive?.value == true

        val recommendationPlugin = PluginManager.plugins
            .firstOrNull { it.enabled && it.plugin.id == TodayWatchPlugin.PLUGIN_ID }
            ?.plugin as? RecommendationPluginApi
        if (recommendationPlugin == null) {
            _uiState.value = _uiState.value.copy(
                todayWatchPlan = null,
                todayWatchLoading = false,
                todayWatchError = "今日推荐插件不可用"
            )
            return
        }

        val result = recommendationPlugin.buildRecommendations(
            RecommendationRequest(
                historyVideos = historySample,
                candidateVideos = recommendVideos,
                mode = runtime.mode.toRecommendationMode(),
                sceneSignals = RecommendationSceneSignals(
                    eyeCareNightActive = eyeCareNightActive
                ),
                groupLimit = runtime.upRankLimit,
                queueLimit = runtime.queueBuildLimit,
                creatorSignals = creatorSignals,
                feedbackSignals = RecommendationFeedbackSignals(
                    consumedBvids = todayConsumedBvids.toSet(),
                    dislikedBvids = todayDislikedBvids.toSet(),
                    dislikedCreatorMids = todayDislikedCreatorMids.toSet(),
                    dislikedKeywords = todayDislikedKeywords.toSet()
                )
            )
        )
        val plan = result.toTodayWatchPlan()

        _uiState.value = _uiState.value.copy(
            todayWatchPlan = plan,
            todayWatchMode = runtime.mode,
            todayWatchLoading = false,
            todayWatchError = null
        )
    }

    private suspend fun loadHistorySample(forceReload: Boolean, sampleLimit: Int): List<VideoItem> {
        val now = System.currentTimeMillis()
        if (!forceReload &&
            historySampleCache.isNotEmpty() &&
            now - historySampleLoadedAtMs < HISTORY_SAMPLE_CACHE_TTL_MS
        ) {
            return historySampleCache.take(sampleLimit.coerceIn(20, 120))
        }

        val firstPage = HistoryRepository.getHistoryList(ps = 50, max = 0, viewAt = 0).getOrNull()
        if (firstPage == null) {
            _uiState.value = _uiState.value.copy(
                todayWatchLoading = false,
                todayWatchError = "历史记录不可用，已按当前推荐生成"
            )
            return emptyList()
        }

        val merged = firstPage.list.map { it.toVideoItem() }.toMutableList()
        val cursor = firstPage.cursor
        if (cursor != null && cursor.max > 0 && merged.size < 80) {
            val secondPage = HistoryRepository.getHistoryList(
                ps = 50,
                max = cursor.max,
                viewAt = cursor.view_at,
                business = cursor.business
            ).getOrNull()
            if (secondPage != null) {
                merged += secondPage.list.map { it.toVideoItem() }
            }
        }

        historySampleCache = merged
            .filter { it.bvid.isNotBlank() }
            .distinctBy { it.bvid }
        historySampleLoadedAtMs = now
        return historySampleCache.take(sampleLimit.coerceIn(20, 120))
    }

    private fun getRecommendCandidates(): List<VideoItem> {
        val state = _uiState.value
        val recommendVideos = state.categoryStates[HomeCategory.RECOMMEND]?.videos.orEmpty()
        return if (recommendVideos.isNotEmpty()) {
            recommendVideos
        } else if (state.currentCategory == HomeCategory.RECOMMEND) {
            state.videos
        } else {
            emptyList()
        }
    }

    //  [新增] 切换分类
    fun switchCategory(category: HomeCategory) {
        val currentState = _uiState.value
        if (currentState.currentCategory == category) return
        
        //  [修复] 标记正在切换分类，避免入场动画产生收缩效果
        com.android.purebilibili.core.util.CardPositionManager.isSwitchingCategory = true
        
        categoryInitialLoadJob?.cancel()
        categoryInitialLoadJob = viewModelScope.launch {
            //  [修复] 如果切换到直播分类，未登录用户默认显示热门
            val liveSubCategory = if (category == HomeCategory.LIVE) {
                val isLoggedIn = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
                if (isLoggedIn) currentState.liveSubCategory else LiveSubCategory.POPULAR
            } else {
                currentState.liveSubCategory
            }
            
            _uiState.value = currentState.copy(
                currentCategory = category,
                liveSubCategory = liveSubCategory,
                displayedTabIndex = currentState.displayedTabIndex
            )

            //  [修复] 恢复“追番”分类的数据拉取逻辑，确保滑动到这些页面时有内容显示
            /* 之前禁用了此处拉取，导致滑动展示空白页。现在移除提前返回。 */

            val targetState = _uiState.value
            val targetCategoryState = if (category == HomeCategory.POPULAR) {
                targetState.popularCategoryStates[targetState.popularSubCategory] ?: CategoryContent()
            } else {
                targetState.categoryStates[category] ?: CategoryContent()
            }
            val needFetch = targetCategoryState.videos.isEmpty() && 
                           targetCategoryState.liveRooms.isEmpty() && 
                           !targetCategoryState.isLoading && 
                           targetCategoryState.error == null

            // 如果目标分类没有数据，则加载
            try {
                if (needFetch) {
                    fetchData(isLoadMore = false, category = category)
                } else if (category == HomeCategory.RECOMMEND) {
                    val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
                    if (shouldAutoRebuildTodayWatchPlan(
                            currentCategory = category,
                            isTodayWatchEnabled = runtime.enabled,
                            isTodayWatchCollapsed = runtime.collapsed
                        )
                    ) {
                        rebuildTodayWatchPlan()
                    }
                }
            } catch (error: CancellationException) {
                if (_uiState.value.currentCategory != category) {
                    if (category == HomeCategory.POPULAR) {
                        updatePopularCategoryState(currentState.popularSubCategory) { state ->
                            state.copy(isLoading = false, error = null)
                        }
                    } else {
                        updateCategoryState(category) { state ->
                            state.copy(isLoading = false, error = null)
                        }
                    }
                }
                throw error
            }
        }
    }
    
    //  [新增] 更新显示的标签页索引（用于特殊分类，不改变内容只更新标签高亮）
    fun updateDisplayedTabIndex(index: Int) {
        val normalized = index.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(displayedTabIndex = normalized)
    }
    
    //  [新增] 开始消散动画（触发 UI 播放粒子动画）
    fun startVideoDissolve(bvid: String) {
        _uiState.value = _uiState.value.copy(
            dissolvingVideos = (_uiState.value.dissolvingVideos + bvid).toImmutableSet()
        )
    }
    
    //  [新增] 完成消散动画（从列表移除并记录到已过滤集合）
    //  [新增] 完成消散动画（从列表移除并记录到已过滤集合）
    fun completeVideoDissolve(bvid: String) {
        val currentCategory = _uiState.value.currentCategory
        val shouldRefilterAfterRemove = pendingNotInterestedRefilterBvids.remove(bvid)
        
        // Update global dissolving list
        val newDissolving = (_uiState.value.dissolvingVideos - bvid).toImmutableSet()
        
        // Update category state
        updateCategoryState(currentCategory) { oldState ->
            oldState.copy(
                videos = oldState.videos.filterNot { it.bvid == bvid }.toImmutableList()
            )
        }
        
        // Also update the global dissolving set in UI state
        _uiState.value = _uiState.value.copy(dissolvingVideos = newDissolving)
        if (shouldRefilterAfterRemove) {
            reFilterAllContent()
        }
        if (currentCategory == HomeCategory.RECOMMEND) {
            viewModelScope.launch {
                val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
                if (shouldAutoRebuildTodayWatchPlan(
                        currentCategory = currentCategory,
                        isTodayWatchEnabled = runtime.enabled,
                        isTodayWatchCollapsed = runtime.collapsed
                    )
                ) {
                    rebuildTodayWatchPlan()
                }
            }
        }
    }

    fun markTodayWatchVideoOpened(video: VideoItem) {
        val bvid = video.bvid.takeIf { it.isNotBlank() } ?: return
        todayConsumedBvids += bvid

        val currentState = _uiState.value
        val currentPlan = currentState.todayWatchPlan ?: return
        val consumeUpdate = consumeVideoFromTodayWatchPlan(
            plan = currentPlan,
            consumedBvid = bvid,
            queuePreviewLimit = currentState.todayWatchCardConfig.queuePreviewLimit
        )
        if (!consumeUpdate.consumedApplied) return

        _uiState.value = currentState.copy(todayWatchPlan = consumeUpdate.updatedPlan)
        if (consumeUpdate.shouldRefill && currentState.currentCategory == HomeCategory.RECOMMEND) {
            viewModelScope.launch {
                rebuildTodayWatchPlan()
            }
        }
    }
    
    
    //  [新增] 切换直播子分类
    fun switchLiveSubCategory(subCategory: LiveSubCategory) {
        if (_uiState.value.liveSubCategory == subCategory) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                liveSubCategory = subCategory,
                liveRooms = emptyList<LiveRoom>().toImmutableList(),
                isLoading = true,
                error = null
            )
            livePage = 1
            hasMoreLiveData = true  //  修复：切换分类时重置分页标志
            fetchLiveRooms(isLoadMore = false)
        }
    }

    fun switchPopularSubCategory(subCategory: PopularSubCategory) {
        if (_uiState.value.popularSubCategory == subCategory) return
        val current = _uiState.value
        val targetState = current.popularCategoryStates[subCategory] ?: CategoryContent()
        var nextState = current.copy(popularSubCategory = subCategory)
        if (current.currentCategory == HomeCategory.POPULAR) {
            val nextCategoryStates = current.categoryStates.toMutableMap()
            nextCategoryStates[HomeCategory.POPULAR] = targetState
            nextState = nextState.copy(
                categoryStates = nextCategoryStates.toImmutableMap(),
                videos = targetState.videos,
                isLoading = targetState.isLoading,
                error = targetState.error
            )
        }
        _uiState.value = nextState

        val needFetch = targetState.videos.isEmpty() &&
            !targetState.isLoading &&
            targetState.error == null
        if (current.currentCategory == HomeCategory.POPULAR && needFetch) {
            viewModelScope.launch {
                fetchData(isLoadMore = false)
            }
        }
    }
    
    //  [新增] 添加到稍后再看
    fun addToWatchLater(bvid: String, aid: Long) {
        viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.ActionRepository.toggleWatchLater(aid, true)
            result.onSuccess {
                android.widget.Toast.makeText(getApplication(), "已添加到稍后再看", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                android.widget.Toast.makeText(getApplication(), e.message ?: "添加失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun markNotInterested(
        video: VideoItem,
        reason: RecommendationFeedbackReason,
        cardAnimationEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            val action = resolveHomeNotInterestedAction(video, reason)
            recordTodayWatchNegativeFeedback(video, action)
            if (action.shouldBlockCreator) {
                blockedUpRepository.blockUp(
                    mid = action.creatorMid,
                    name = action.creatorName,
                    face = action.creatorFace
                )
                blockedMids = blockedMids + action.creatorMid
            }
            if (action.shouldBlockCreator || action.keywords.isNotEmpty()) {
                pendingNotInterestedRefilterBvids += video.bvid
            }

            val transition = resolveHomeDismissVisualTransition(
                isFeedbackRecorded = true,
                cardAnimationEnabled = cardAnimationEnabled
            )
            if (transition.shouldStartDissolve) {
                startVideoDissolve(video.bvid)
            } else if (transition.shouldRemoveImmediately) {
                completeVideoDissolve(video.bvid)
            }

            if (action.shouldSyncCreatorToBilibiliBlockedList) {
                viewModelScope.launch {
                    val writeResult = blockedUpRepository.blockUpWithBilibiliSync(
                        mid = action.creatorMid,
                        name = action.creatorName,
                        face = action.creatorFace
                    )
                    Logger.d("HomeVM", writeResult.message)
                }
            }

            val metadata = video.recommendationFeedback
            val supportsServerSync = metadata?.supportsServerSync == true && reason.id != null
            if (supportsServerSync) {
                viewModelScope.launch {
                    ActionRepository.submitRecommendationFeedback(metadata, reason)
                        .onSuccess {
                            _feedbackEvents.send(
                                reason.toast.ifBlank { "已减少相关内容推荐" }
                            )
                        }
                        .onFailure {
                            _feedbackEvents.send("已在本地生效，服务器同步失败")
                        }
                }
            } else {
                _feedbackEvents.send(reason.toast.ifBlank { "已减少相关内容推荐" })
            }
            Logger.d("HomeVM", "已记录不感兴趣: ${video.bvid}, reason=${reason.name}")
        }
    }

    fun blockCreator(video: VideoItem) {
        val action = resolveHomeNotInterestedAction(
            video = video,
            reason = RecommendationFeedbackReason(
                name = "屏蔽 UP 主",
                localAction = RecommendationFeedbackLocalAction.CREATOR
            )
        )
        if (!action.shouldBlockCreator) {
            android.widget.Toast.makeText(getApplication(), "无法获取 UP 主信息", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            val writeResult = blockedUpRepository.blockUpWithBilibiliSync(
                mid = action.creatorMid,
                name = action.creatorName,
                face = action.creatorFace
            )
            blockedMids = blockedMids + action.creatorMid
            reFilterAllContent()
            android.widget.Toast.makeText(getApplication(), writeResult.message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun recordTodayWatchNegativeFeedback(
        video: VideoItem,
        action: HomeNotInterestedAction
    ) {
        val snapshot = TodayWatchFeedbackStore.getSnapshot(getApplication()).withDislikedVideoFeedback(
            video = TodayWatchDislikedVideoSnapshot(
                bvid = video.bvid,
                title = video.title,
                creatorName = video.owner.name,
                creatorMid = video.owner.mid,
                dislikedAtMillis = System.currentTimeMillis()
            ),
            keywords = action.keywords,
            includeCreatorSignal = action.shouldBlockCreator
        )
        todayDislikedBvids.clear()
        todayDislikedBvids.addAll(snapshot.dislikedBvids)
        todayDislikedCreatorMids.clear()
        todayDislikedCreatorMids.addAll(snapshot.dislikedCreatorMids)
        todayDislikedKeywords.clear()
        todayDislikedKeywords.addAll(snapshot.dislikedKeywords)
        TodayWatchFeedbackStore.saveSnapshot(getApplication(), snapshot)
    }

    private fun syncTodayWatchFeedbackFromStore() {
        val snapshot = TodayWatchFeedbackStore.getSnapshot(getApplication())
        todayDislikedBvids.clear()
        todayDislikedBvids.addAll(snapshot.dislikedBvids)
        todayDislikedCreatorMids.clear()
        todayDislikedCreatorMids.addAll(snapshot.dislikedCreatorMids)
        todayDislikedKeywords.clear()
        todayDislikedKeywords.addAll(snapshot.dislikedKeywords)
    }

    private fun filterHomeFeedbackVideos(videos: List<VideoItem>): List<VideoItem> {
        return filterHomeVideosByNotInterestedFeedback(
            videos = videos,
            dislikedBvids = todayDislikedBvids,
            dislikedCreatorMids = todayDislikedCreatorMids,
            dislikedKeywords = todayDislikedKeywords
        )
    }

    private fun persistTodayWatchFeedback() {
        val currentSnapshot = TodayWatchFeedbackStore.getSnapshot(getApplication())
        TodayWatchFeedbackStore.saveSnapshot(
            context = getApplication(),
            snapshot = com.android.purebilibili.core.store.TodayWatchFeedbackSnapshot(
                dislikedBvids = todayDislikedBvids.toSet(),
                dislikedCreatorMids = todayDislikedCreatorMids.toSet(),
                dislikedKeywords = todayDislikedKeywords.toSet(),
                recentDislikedVideos = currentSnapshot.recentDislikedVideos
            )
        )
    }

    private fun loadData() {
        categoryInitialLoadJob?.cancel()
        val category = _uiState.value.currentCategory
        categoryInitialLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                fetchData(isLoadMore = false, category = category)
            } catch (error: CancellationException) {
                updateCategoryState(category) { state ->
                    state.copy(isLoading = false, error = null)
                }
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                throw error
            }
        }
    }

    fun refresh(category: HomeCategory = _uiState.value.currentCategory) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val refreshingCategory = category
            syncCurrentCategoryForRefresh(refreshingCategory)
            _undoSnapshot = buildHomeRefreshUndoSnapshot(
                refreshingCategory = refreshingCategory,
                recommendCategoryState = _uiState.value.categoryStates[HomeCategory.RECOMMEND],
                fallbackVideos = _uiState.value.videos
            )
            //  [新增] 刷新前保存推荐视频快照（用于撤销）
            val previousRecommendTopBvid = if (refreshingCategory == HomeCategory.RECOMMEND) {
                (_uiState.value.categoryStates[HomeCategory.RECOMMEND]?.videos
                    ?: _uiState.value.videos).firstOrNull()?.bvid?.takeIf { it.isNotBlank() }
            } else null
            val newItemsCount = fetchData(
                isLoadMore = false,
                isManualRefresh = true,
                category = refreshingCategory
            )
            
            //  数据加载完成后再更新 refreshKey，避免闪烁
            //  刷新成功后显示趣味提示
            val refreshMessage = com.android.purebilibili.core.util.EasterEggs.getRefreshMessage()
            val oldBoundary = _uiState.value.recommendOldContentStartIndex
            val newBoundary = if (refreshingCategory == HomeCategory.RECOMMEND) {
                if ((newItemsCount ?: 0) > 0) newItemsCount else null
            } else {
                oldBoundary
            }
            val oldAnchor = _uiState.value.recommendOldContentAnchorBvid
            val newAnchor = if (refreshingCategory == HomeCategory.RECOMMEND) {
                if ((newItemsCount ?: 0) > 0) previousRecommendTopBvid else null
            } else {
                oldAnchor
            }
            val undoAvailable = shouldExposeHomeRefreshUndo(
                refreshingCategory = refreshingCategory,
                snapshot = _undoSnapshot
            )
            _uiState.value = _uiState.value.copy(
                refreshKey = System.currentTimeMillis(),
                refreshMessage = refreshMessage,
                refreshNewItemsCount = newItemsCount,
                refreshNewItemsKey = if (newItemsCount != null) System.currentTimeMillis() else _uiState.value.refreshNewItemsKey,
                recommendOldContentAnchorBvid = newAnchor,
                recommendOldContentStartIndex = newBoundary,
                recommendOldContentRevealKey = if (refreshingCategory == HomeCategory.RECOMMEND) 0L else _uiState.value.recommendOldContentRevealKey,
                //  刷新成功且是推荐分类时标记可撤销
                undoAvailable = undoAvailable
            )
            if (undoAvailable) {
                scheduleUndoDismiss()
            } else {
                cancelUndoDismiss()
            }
            _isRefreshing.value = false
        }
    }

    private fun syncCurrentCategoryForRefresh(category: HomeCategory) {
        val current = _uiState.value
        if (current.currentCategory == category) return
        val categoryState = current.categoryStates[category] ?: CategoryContent()
        // 下拉刷新发生在具体 Pager 页上，先同步当前分类，避免刷新态和数据请求落到旧页面。
        _uiState.value = current.copy(
            currentCategory = category,
            videos = categoryState.videos,
            liveRooms = categoryState.liveRooms,
            followedLiveRooms = categoryState.followedLiveRooms,
            isLoading = categoryState.isLoading,
            error = categoryState.error
        )
    }

    fun markRefreshNewItemsHandled(key: Long) {
        if (key <= 0L) return
        val current = _uiState.value
        if (key != current.refreshNewItemsKey || key <= current.refreshNewItemsHandledKey) return
        _uiState.value = current.copy(refreshNewItemsHandledKey = key)
    }

    fun markRecommendOldContentDividerRevealed(key: Long) {
        if (key <= 0L) return
        val current = _uiState.value
        if (current.currentCategory != HomeCategory.RECOMMEND) return
        if (key != current.refreshNewItemsKey || current.recommendOldContentRevealKey == key) return
        if (current.recommendOldContentAnchorBvid == null && current.recommendOldContentStartIndex == null) return
        _uiState.value = current.copy(recommendOldContentRevealKey = key)
    }

    //  [新增] 撤销刷新：恢复刷新前的推荐视频列表
    fun undoRefresh() {
        val snapshot = _undoSnapshot ?: return
        cancelUndoDismiss()
        updateCategoryState(HomeCategory.RECOMMEND) { oldState ->
            applyHomeRefreshUndoSnapshot(oldState = oldState, snapshot = snapshot)
        }
        _undoSnapshot = null
        _uiState.value = _uiState.value.copy(
            undoAvailable = false,
            recommendOldContentAnchorBvid = null,
            recommendOldContentStartIndex = null,
            recommendOldContentRevealKey = 0L
        )
        Logger.d("HomeVM", "↩️ Undo refresh: restored ${snapshot.videos.size} videos")
    }

    //  [新增] 取消撤销（超时或用户主动忽略）
    fun dismissUndo() {
        cancelUndoDismiss()
        _undoSnapshot = null
        if (_uiState.value.undoAvailable) {
            _uiState.value = _uiState.value.copy(undoAvailable = false)
        }
    }

    private fun scheduleUndoDismiss() {
        cancelUndoDismiss()
        undoDismissJob = viewModelScope.launch {
            delay(HOME_REFRESH_UNDO_TIMEOUT_MS)
            dismissUndo()
        }
    }

    private fun cancelUndoDismiss() {
        undoDismissJob?.cancel()
        undoDismissJob = null
    }

    fun loadMore() {
        val currentCategory = _uiState.value.currentCategory
        val categoryState = if (currentCategory == HomeCategory.POPULAR) {
            _uiState.value.popularCategoryStates[_uiState.value.popularSubCategory] ?: return
        } else {
            _uiState.value.categoryStates[currentCategory] ?: return
        }
        
        if (categoryState.isLoading || _isRefreshing.value || !categoryState.hasMore) return
        if (currentCategory == HomeCategory.POPULAR &&
            !supportsPopularLoadMore(_uiState.value.popularSubCategory)
        ) {
            return
        }
        
        //  修复：如果是直播分类且没有更多数据，不再加载
        if (currentCategory == HomeCategory.LIVE && !hasMoreLiveData) {
            com.android.purebilibili.core.util.Logger.d("HomeVM", "🔴 No more live data, skipping loadMore")
            return
        }
        
        viewModelScope.launch {
            fetchData(isLoadMore = true)
        }
    }

    private fun refreshUserInfoInBackground() {
        if (userInfoRefreshJob?.isActive == true) return
        userInfoRefreshJob = viewModelScope.launch {
            fetchUserInfo()
        }
    }

    private fun refreshMessageUnreadInBackground() {
        if (messageUnreadRefreshJob?.isActive == true) return
        messageUnreadRefreshJob = viewModelScope.launch {
            refreshMessageUnreadCount()
        }
    }

    private suspend fun refreshMessageUnreadCount() {
        if (com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(messageUnreadCount = 0)
            return
        }

        val unreadResult = MessageRepository.getUnreadCount()
        val feedUnreadResult = MessageRepository.getFeedUnread()
        if (unreadResult.isSuccess && feedUnreadResult.isSuccess) {
            _uiState.value = _uiState.value.copy(
                messageUnreadCount = totalMessageUnreadCount(
                    unreadData = unreadResult.getOrNull(),
                    feedUnread = feedUnreadResult.getOrNull()
                )
            )
        }
    }

    private suspend fun fetchData(
        isLoadMore: Boolean,
        isManualRefresh: Boolean = false,
        category: HomeCategory = _uiState.value.currentCategory
    ): Int? {
        val currentCategory = category
        val popularSubCategory = _uiState.value.popularSubCategory
        var refreshNewItemsCount: Int? = null
        
        // 更新当前分类为加载状态
        if (currentCategory == HomeCategory.POPULAR) {
            updatePopularCategoryState(popularSubCategory) { it.copy(isLoading = true, error = null) }
        } else {
            updateCategoryState(currentCategory) { it.copy(isLoading = true, error = null) }
        }
        
        //  直播分类单独处理 (TODO: Adapt fetchLiveRooms to use categoryStates)
        if (currentCategory == HomeCategory.LIVE) {
            fetchLiveRooms(isLoadMore)
            currentCoroutineContext().ensureActive()
            return refreshNewItemsCount
        }
        
        //  关注动态分类单独处理 (TODO: Adapt fetchFollowFeed to use categoryStates)
        if (currentCategory == HomeCategory.FOLLOW) {
            val result = fetchFollowFeed(
                isLoadMore = isLoadMore,
                isManualRefresh = isManualRefresh
            )
            currentCoroutineContext().ensureActive()
            return result
        }
        
        val currentCategoryState = if (currentCategory == HomeCategory.POPULAR) {
            _uiState.value.popularCategoryStates[popularSubCategory] ?: CategoryContent()
        } else {
            _uiState.value.categoryStates[currentCategory] ?: CategoryContent()
        }
        val advanceOnManualRefresh = shouldAdvancePagedFeedOnManualRefresh(
            category = currentCategory,
            popularSubCategory = popularSubCategory
        )
        // 分区/热门综合：手动刷新翻下一页换一批；其它分类仍回第 1 页
        val pageToFetch = resolvePagedFeedPageToFetch(
            isLoadMore = isLoadMore,
            isManualRefresh = isManualRefresh,
            currentPageIndex = currentCategoryState.pageIndex,
            advanceOnManualRefresh = advanceOnManualRefresh
        )
        val recommendRequestIndex = resolveRecommendFeedRequestIndex(
            isLoadMore = isLoadMore,
            isManualRefresh = isManualRefresh,
            currentRefreshIndex = refreshIdx
        )

        //  视频类分类处理
        val videoResult = when (currentCategory) {
            HomeCategory.RECOMMEND -> VideoRepository.getHomeVideos(recommendRequestIndex) // Recommend uses idx, slightly different
            HomeCategory.POPULAR -> {
                when (popularSubCategory) {
                    PopularSubCategory.COMPREHENSIVE -> VideoRepository.getPopularVideos(pageToFetch)
                    PopularSubCategory.RANKING -> VideoRepository.getRankingVideos(rid = 0, type = "all")
                    PopularSubCategory.WEEKLY -> VideoRepository.getWeeklyMustWatchVideos()
                    PopularSubCategory.PRECIOUS -> VideoRepository.getPreciousVideos()
                }
            }
            else -> {
                //  Generic categories (Game, Tech, etc.)
                if (currentCategory.tid > 0) {
                     VideoRepository.getRegionVideos(tid = currentCategory.tid, page = pageToFetch)
                } else {
                     Result.failure(Exception("Unknown category"))
                }
            }
        }
        currentCoroutineContext().ensureActive()
        videoResult.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
        }
        
        if (shouldRefreshHomeUserInfoAfterFeedLoad(isLoadMore)) {
            refreshUserInfoInBackground()
        }

        if (isLoadMore) delay(100)

        videoResult.onSuccess { videos ->
            val validVideos = videos.filter { it.bvid.isNotEmpty() && it.title.isNotEmpty() }
            if (shouldAdvanceRecommendFeedRequestIndex(
                    category = currentCategory,
                    isLoadMore = isLoadMore,
                    isManualRefresh = isManualRefresh,
                    validVideoCount = validVideos.size
                )
            ) {
                refreshIdx = maxOf(refreshIdx, recommendRequestIndex)
            }
            
            //  [Feature] 应用屏蔽 + 原生插件 + JSON 规则插件过滤器
            val blockedFiltered = validVideos.filter { video -> video.owner.mid !in blockedMids }
            val feedbackFiltered = filterHomeFeedbackVideos(blockedFiltered)
            val builtinFiltered = PluginManager.filterFeedItems(
                feedbackFiltered,
                feedKind = currentCategory.toFeedKind(popularSubCategory)
            )
            val filteredVideos = com.android.purebilibili.core.plugin.json.JsonPluginManager
                .filterVideos(builtinFiltered)
            
            val useIncrementalRecommendRefresh = !isLoadMore &&
                currentCategory == HomeCategory.RECOMMEND &&
                incrementalTimelineRefreshEnabled

            val incomingVideos = selectHomeFeedIncomingVideos(
                responseVideos = filteredVideos,
                currentVideos = currentCategoryState.videos,
                isLoadMore = isLoadMore,
                isIncrementalRefresh = useIncrementalRecommendRefresh,
            )
            
            if (incomingVideos.isNotEmpty() || useIncrementalRecommendRefresh) {
                var addedCount = 0
                val updateContent: (CategoryContent) -> CategoryContent = { oldState ->
                    val mergedVideos = when {
                        isLoadMore -> appendDistinctByKey(oldState.videos, incomingVideos, ::videoItemKey).toImmutableList()
                        useIncrementalRecommendRefresh -> {
                            val merged = prependDistinctByKey(oldState.videos, incomingVideos, ::videoItemKey)
                            addedCount = (merged.size - oldState.videos.size).coerceAtLeast(0)
                            merged.toImmutableList()
                        }
                        else -> incomingVideos.toImmutableList()
                    }

                    oldState.copy(
                        videos = mergedVideos,
                        liveRooms = emptyList<LiveRoom>().toImmutableList(),
                        isLoading = false,
                        error = null,
                        pageIndex = if (useIncrementalRecommendRefresh) {
                            oldState.pageIndex
                        } else {
                            resolvePagedFeedPageIndexAfterFetch(
                                isLoadMore = isLoadMore,
                                isManualRefresh = isManualRefresh,
                                advanceOnManualRefresh = advanceOnManualRefresh,
                                pageToFetch = pageToFetch,
                                incomingCount = incomingVideos.size,
                                previousPageIndex = oldState.pageIndex
                            )
                        },
                        hasMore = if (currentCategory == HomeCategory.POPULAR) {
                            supportsPopularLoadMore(popularSubCategory)
                        } else {
                            true
                        }
                    )
                }
                if (currentCategory == HomeCategory.POPULAR) {
                    updatePopularCategoryState(popularSubCategory, updateContent)
                } else {
                    updateCategoryState(currentCategory, updateContent)
                }

                if (useIncrementalRecommendRefresh && isManualRefresh) {
                    refreshNewItemsCount = addedCount
                }
            } else {
                 //  全被过滤掉了 OR 空列表
                 val updateContent: (CategoryContent) -> CategoryContent = { oldState ->
                    oldState.copy(
                        isLoading = false,
                        error = if (!isLoadMore && oldState.videos.isEmpty()) "没有更多内容了" else null,
                        hasMore = false,
                        pageIndex = resolvePagedFeedPageIndexAfterFetch(
                            isLoadMore = isLoadMore,
                            isManualRefresh = isManualRefresh,
                            advanceOnManualRefresh = advanceOnManualRefresh,
                            pageToFetch = pageToFetch,
                            incomingCount = 0,
                            previousPageIndex = oldState.pageIndex
                        )
                    )
                 }
                 if (currentCategory == HomeCategory.POPULAR) {
                     updatePopularCategoryState(popularSubCategory, updateContent)
                 } else {
                     updateCategoryState(currentCategory, updateContent)
                 }
            }
            if (currentCategory == HomeCategory.RECOMMEND) {
                viewModelScope.launch {
                    val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
                    if (shouldAutoRebuildTodayWatchPlan(
                            currentCategory = currentCategory,
                            isTodayWatchEnabled = runtime.enabled,
                            isTodayWatchCollapsed = runtime.collapsed
                        )
                    ) {
                        rebuildTodayWatchPlan(forceReloadHistory = !isLoadMore && isManualRefresh)
                    }
                }
            }
        }.onFailure { error ->
            val updateContent: (CategoryContent) -> CategoryContent = { oldState ->
                oldState.copy(
                    isLoading = false,
                    error = if (!isLoadMore && oldState.videos.isEmpty()) error.message ?: "网络错误" else null,
                    hasMore = if (shouldKeepHomeCategoryAutoPagingAfterFailure(isLoadMore)) {
                        oldState.hasMore
                    } else {
                        false
                    }
                )
            }
            if (currentCategory == HomeCategory.POPULAR) {
                updatePopularCategoryState(popularSubCategory, updateContent)
            } else {
                updateCategoryState(currentCategory, updateContent)
            }
            if (currentCategory == HomeCategory.RECOMMEND) {
                val runtime = syncTodayWatchPluginState(clearWhenDisabled = true)
                if (runtime.enabled) {
                    _uiState.value = _uiState.value.copy(
                        todayWatchLoading = false,
                        todayWatchError = error.message ?: "今日推荐单生成失败"
                    )
                }
            }
        }
        return refreshNewItemsCount
    }

    private fun updatePopularCategoryState(
        subCategory: PopularSubCategory,
        update: (CategoryContent) -> CategoryContent
    ) {
        val current = _uiState.value
        val currentSubCategoryState = current.popularCategoryStates[subCategory] ?: CategoryContent()
        val newSubCategoryState = update(currentSubCategoryState)
        val newPopularStates = current.popularCategoryStates.toMutableMap()
        newPopularStates[subCategory] = newSubCategoryState

        var newState = current.copy(popularCategoryStates = newPopularStates.toImmutableMap())
        if (current.currentCategory == HomeCategory.POPULAR && current.popularSubCategory == subCategory) {
            val newCategoryStates = current.categoryStates.toMutableMap()
            newCategoryStates[HomeCategory.POPULAR] = newSubCategoryState
            newState = newState.copy(
                categoryStates = newCategoryStates.toImmutableMap(),
                videos = newSubCategoryState.videos,
                liveRooms = newSubCategoryState.liveRooms,
                followedLiveRooms = newSubCategoryState.followedLiveRooms,
                isLoading = newSubCategoryState.isLoading,
                error = newSubCategoryState.error
            )
        }
        _uiState.value = newState
    }
    
    // Helper to update state for a specific category
    private fun updateCategoryState(category: HomeCategory, update: (CategoryContent) -> CategoryContent) {
        val currentStates = _uiState.value.categoryStates
        val currentCategoryState = currentStates[category] ?: CategoryContent()
        val newCategoryState = update(currentCategoryState)
        val newStates = currentStates.toMutableMap()
        newStates[category] = newCategoryState
        
        // Also update legacy fields if it is current category, to keep UI working until full migration
        // Or if we fully migrated UI, we don't need to update legacy fields 'videos', 'liveRooms' etc in HomeUiState root.
        // But HomeScreen.kt still uses `state.videos`. So we MUST sync variables.
        
        var newState = _uiState.value.copy(categoryStates = newStates.toImmutableMap())
        
        if (category == newState.currentCategory) {
            newState = newState.copy(
                videos = newCategoryState.videos,
                liveRooms = newCategoryState.liveRooms,
                followedLiveRooms = newCategoryState.followedLiveRooms,
                isLoading = newCategoryState.isLoading,
                error = newCategoryState.error
            )
        }
        _uiState.value = newState
    }

    //  [新增] 获取关注动态列表
    //  [新增] 获取关注动态列表
    private suspend fun fetchFollowFeed(
        isLoadMore: Boolean,
        isManualRefresh: Boolean
    ): Int? {
        if (com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()) {
             updateCategoryState(HomeCategory.FOLLOW) { oldState ->
                oldState.copy(
                    isLoading = false,
                    error = "未登录，请先登录以查看关注内容",
                    videos = emptyList<VideoItem>().toImmutableList() // Ensure empty to trigger error state
                )
            }
            return null
        }

        if (!isLoadMore) {
            fetchUserInfo()
        }

        if (isLoadMore) delay(100)

        val followScope = com.android.purebilibili.data.repository.DynamicFeedScope.HOME_FOLLOW
        val followType = "video"
        val establishedBaseline = com.android.purebilibili.data.repository.DynamicRepository
            .currentUpdateBaseline(scope = followScope, type = followType)
        // 手动下拉且已有基线：按 API 文档带 update_baseline 探测新动态（update_num）。
        val probeWithBaseline = isManualRefresh &&
            !isLoadMore &&
            establishedBaseline.isNotBlank()

        val result = com.android.purebilibili.data.repository.DynamicRepository.getDynamicFeed(
            refresh = !isLoadMore,
            scope = followScope,
            type = followType,
            incrementalRefresh = if (probeWithBaseline) {
                true
            } else {
                !isLoadMore && incrementalTimelineRefreshEnabled
            }
        )

        var tipCount: Int? = null
        result.onSuccess { feedResult ->
            val videos = mapHomeFollowDynamicItemsToVideos(feedResult.items)
            val apiUpdateNum = feedResult.updateNum
            val usedBaseline = feedResult.usedUpdateBaseline

            if (
                probeWithBaseline &&
                shouldFullReplaceFollowFeedAfterBaselineProbe(
                    incrementalRefreshEnabled = incrementalTimelineRefreshEnabled,
                    apiUpdateNum = apiUpdateNum
                )
            ) {
                // 关闭「增量刷新」但探测到新动态：再拉一整页替换列表。
                val fullResult = com.android.purebilibili.data.repository.DynamicRepository.getDynamicFeed(
                    refresh = true,
                    scope = followScope,
                    type = followType,
                    incrementalRefresh = false
                )
                fullResult.onSuccess { fullFeed ->
                    val fullVideos = mapHomeFollowDynamicItemsToVideos(fullFeed.items)
                    tipCount = resolveHomeFollowRefreshNewItemsCount(
                        usedUpdateBaseline = true,
                        apiUpdateNum = apiUpdateNum,
                        insertedVideoCount = apiUpdateNum
                    )
                    updateCategoryState(HomeCategory.FOLLOW) { oldState ->
                        oldState.copy(
                            videos = fullVideos.toImmutableList(),
                            liveRooms = emptyList<LiveRoom>().toImmutableList(),
                            isLoading = false,
                            error = if (fullVideos.isEmpty()) "暂无关注动态，请先关注一些UP主" else null,
                            hasMore = com.android.purebilibili.data.repository.DynamicRepository.hasMoreData(
                                scope = followScope,
                                type = followType
                            )
                        )
                    }
                }.onFailure { error ->
                    updateCategoryState(HomeCategory.FOLLOW) { oldState ->
                        oldState.copy(
                            isLoading = false,
                            error = if (oldState.videos.isEmpty()) error.message ?: "请先登录" else null
                        )
                    }
                }
                return tipCount
            }

            updateCategoryState(HomeCategory.FOLLOW) { oldState ->
                val oldSize = oldState.videos.size
                val mergedVideos = when {
                    isLoadMore -> appendDistinctByKey(oldState.videos, videos, ::videoItemKey).toImmutableList()
                    usedBaseline || (!isLoadMore && incrementalTimelineRefreshEnabled) -> {
                        prependDistinctByKey(oldState.videos, videos, ::videoItemKey).toImmutableList()
                    }
                    else -> videos.toImmutableList()
                }
                val insertedCount = (mergedVideos.size - oldSize).coerceAtLeast(0)
                if (isManualRefresh && !isLoadMore) {
                    tipCount = resolveHomeFollowRefreshNewItemsCount(
                        usedUpdateBaseline = usedBaseline,
                        apiUpdateNum = apiUpdateNum,
                        insertedVideoCount = insertedCount
                    )
                }
                oldState.copy(
                    videos = mergedVideos,
                    liveRooms = emptyList<LiveRoom>().toImmutableList(),
                    isLoading = false,
                    error = if (!isLoadMore && mergedVideos.isEmpty()) "暂无关注动态，请先关注一些UP主" else null,
                    hasMore = com.android.purebilibili.data.repository.DynamicRepository.hasMoreData(
                        scope = followScope,
                        type = followType
                    )
                )
            }
        }.onFailure { error ->
             updateCategoryState(HomeCategory.FOLLOW) { oldState ->
                oldState.copy(
                    isLoading = false,
                    error = if (!isLoadMore && oldState.videos.isEmpty()) error.message ?: "请先登录" else null
                )
            }
        }
        return tipCount
    }

    private fun mapHomeFollowDynamicItemsToVideos(
        items: List<com.android.purebilibili.data.model.response.DynamicItem>
    ): List<com.android.purebilibili.data.model.response.VideoItem> {
        return items.mapNotNull { item ->
            if ((item.modules.module_author?.mid ?: 0) in blockedMids) return@mapNotNull null

            val archive = item.modules.module_dynamic?.major?.archive ?: return@mapNotNull null
            if (!shouldIncludeHomeFollowDynamicInVideoFeed(archive.bvid)) {
                return@mapNotNull null
            }

            val resolvedAid = resolveDynamicArchiveAid(
                archiveAid = archive.aid,
                fallbackId = 0L
            )
            com.android.purebilibili.data.model.response.VideoItem(
                id = resolvedAid,
                bvid = archive.bvid,
                dynamicId = item.id_str.trim(),
                aid = resolvedAid,
                title = archive.title,
                pic = archive.cover,
                duration = parseDurationText(archive.duration_text),
                owner = com.android.purebilibili.data.model.response.Owner(
                    mid = item.modules.module_author?.mid ?: 0,
                    name = item.modules.module_author?.name ?: "",
                    face = item.modules.module_author?.face ?: ""
                ),
                stat = com.android.purebilibili.data.model.response.Stat(
                    view = parseStatText(archive.stat.play),
                    danmaku = parseStatText(archive.stat.danmaku)
                )
            )
        }
    }

    private fun videoItemKey(item: com.android.purebilibili.data.model.response.VideoItem): String {
        if (item.dynamicId.isNotBlank()) return "dyn:${item.dynamicId}"
        if (item.bvid.isNotBlank()) return "bvid:${item.bvid}"
        if (item.aid > 0) return "aid:${item.aid}"
        if (item.id > 0) return "id:${item.id}"
        return "${item.owner.mid}:${item.title}:${item.pubdate}"
    }
    
    //  解析时长文本 "10:24" -> 624 秒
    private fun parseDurationText(text: String): Int {
        val parts = text.split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        } catch (e: Exception) { 0 }
    }
    
    //  解析统计文本 "123.4万" -> 1234000
    private fun parseStatText(text: String): Int {
        return try {
            if (text.contains("万")) {
                (text.replace("万", "").toFloat() * 10000).toInt()
            } else if (text.contains("亿")) {
                (text.replace("亿", "").toFloat() * 100000000).toInt()
            } else {
                text.toIntOrNull() ?: 0
            }
        } catch (e: Exception) { 0 }
    }
    
    //  🔴 [改进] 获取直播间列表（同时获取关注和热门）
    private suspend fun fetchLiveRooms(isLoadMore: Boolean) {
        val page = if (isLoadMore) livePage else 1
        
        com.android.purebilibili.core.util.Logger.d("HomeVM", "🔴 fetchLiveRooms: isLoadMore=$isLoadMore, page=$page")
        
        if (!isLoadMore) {
            fetchUserInfo()
            
            // 🔴 [改进] 首次加载时同时获取关注和热门直播
            val isLoggedIn = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
            
            // 并行获取关注和热门直播
            val followedResult = if (isLoggedIn) LiveRepository.getFollowedLive(1) else Result.success(emptyList())
            val popularResult = LiveRepository.getLiveRooms(1)
            
            // 处理关注直播结果
            val followedRooms = followedResult.getOrDefault(emptyList())
            
            // 处理热门直播结果
            popularResult.onSuccess { rooms ->
                if (rooms.isNotEmpty() || followedRooms.isNotEmpty()) {
                    updateCategoryState(HomeCategory.LIVE) { oldState ->
                        oldState.copy(
                            followedLiveRooms = followedRooms.toImmutableList(),
                            liveRooms = rooms.toImmutableList(),
                            videos = emptyList<VideoItem>().toImmutableList(),
                            isLoading = false,
                            error = null,
                            hasMore = true
                        )
                    }
                } else {
                     updateCategoryState(HomeCategory.LIVE) { oldState ->
                        oldState.copy(
                            isLoading = false,
                            error = "暂无直播",
                            hasMore = false
                        )
                    }
                }
            }.onFailure { e ->
                 updateCategoryState(HomeCategory.LIVE) { oldState ->
                    oldState.copy(
                        followedLiveRooms = followedRooms.toImmutableList(),
                        isLoading = false,
                        error = if (followedRooms.isEmpty()) e.message ?: "网络错误" else null
                    )
                }
            }
        } else {
            // 加载更多时只加载热门直播（关注的主播数量有限，不需要分页）
            val result = LiveRepository.getLiveRooms(page)
            delay(100)
            
            result.onSuccess { rooms ->
                if (rooms.isNotEmpty()) {
                    val currentLiveRooms = _uiState.value.categoryStates[HomeCategory.LIVE]?.liveRooms ?: emptyList()
                    val existingRoomIds = currentLiveRooms.map { it.roomid }.toSet()
                    // [Feature] Block Filter
                    val newRooms = rooms.filter { it.roomid !in existingRoomIds && it.uid !in blockedMids }
                    
                    if (newRooms.isEmpty()) {
                        hasMoreLiveData = false
                        updateCategoryState(HomeCategory.LIVE) { it.copy(isLoading = false, hasMore = false) }
                        return@onSuccess
                    }
                    
                    updateCategoryState(HomeCategory.LIVE) { oldState ->
                        oldState.copy(
                            liveRooms = (oldState.liveRooms + newRooms).toImmutableList(),
                            isLoading = false,
                            error = null,
                            hasMore = true
                        )
                    }
                } else {
                    hasMoreLiveData = false
                    updateCategoryState(HomeCategory.LIVE) { it.copy(isLoading = false, hasMore = false) }
                }
            }.onFailure { e ->
                updateCategoryState(HomeCategory.LIVE) { it.copy(isLoading = false) }
            }
        }
    }
    
    //  提取用户信息获取逻辑
    private suspend fun fetchUserInfo() {
        val navResult = VideoRepository.getNavInfo()
        navResult.onSuccess { navData ->
            if (navData.isLogin) {
                val isVip = navData.vip.status == 1
                com.android.purebilibili.core.store.TokenManager.isVipCache = isVip
                com.android.purebilibili.core.store.TokenManager.midCache = navData.mid
                com.android.purebilibili.core.util.AnalyticsHelper.syncUserContext(
                    mid = navData.mid,
                    isVip = isVip,
                    privacyModeEnabled = com.android.purebilibili.core.store.SettingsManager
                        .isPrivacyModeEnabledSync(getApplication())
                )
                _uiState.value = _uiState.value.copy(
                    user = UserState(
                        isLogin = true,
                        face = navData.face,
                        name = navData.uname,
                        mid = navData.mid,
                        level = navData.level_info.current_level,
                        coin = navData.money,
                        bcoin = navData.wallet.bcoin_balance,
                        isVip = isVip
                    )
                )
                refreshMessageUnreadInBackground()
                
                //  获取关注列表（异步，不阻塞主流程）
                fetchFollowingList(navData.mid)
            } else {
                messageUnreadRefreshJob?.cancel()
                messageUnreadRefreshJob = null
                com.android.purebilibili.core.store.TokenManager.isVipCache = false
                com.android.purebilibili.core.store.TokenManager.midCache = null
                com.android.purebilibili.core.util.AnalyticsHelper.syncUserContext(
                    mid = null,
                    isVip = false,
                    privacyModeEnabled = com.android.purebilibili.core.store.SettingsManager
                        .isPrivacyModeEnabledSync(getApplication())
                )
                _uiState.value = _uiState.value.copy(
                    user = UserState(isLogin = false),
                    followingMids = emptySet<Long>().toImmutableSet(),
                    messageUnreadCount = 0
                )
            }
        }
    }
    
    //  获取关注列表（并行分页获取，支持更多关注，带本地缓存）
    private suspend fun fetchFollowingList(mid: Long) {
        val context = getApplication<android.app.Application>()
        val prefs = context.getSharedPreferences("following_cache", android.content.Context.MODE_PRIVATE)
        val cacheKey = "following_mids_$mid"
        val cacheTimeKey = "following_time_$mid"
        
        //  检查缓存（1小时内有效）
        val cachedTime = prefs.getLong(cacheTimeKey, 0)
        val cacheValidDuration = 60 * 60 * 1000L  // 1小时
        if (System.currentTimeMillis() - cachedTime < cacheValidDuration) {
            val cachedMids = prefs.getStringSet(cacheKey, null)
            if (!cachedMids.isNullOrEmpty()) {
                val mids = cachedMids.mapNotNull { it.toLongOrNull() }.toSet()
                _uiState.value = _uiState.value.copy(followingMids = mids.toImmutableSet())
                com.android.purebilibili.core.util.Logger.d("HomeVM", " Loaded ${mids.size} following mids from cache")
                return
            }
        }
        
        //  动态获取所有关注列表（无上限）
        try {
            val allMids = mutableSetOf<Long>()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var page = 1
                while (true) {  //  无限循环，直到获取完所有关注
                    try {
                        val result = com.android.purebilibili.core.network.NetworkModule.api.getFollowings(mid, page, 50)
                        if (result.code == 0 && result.data != null) {
                            val list = result.data.list ?: break
                            if (list.isEmpty()) break
                            
                            list.forEach { user -> allMids.add(user.mid) }
                            
                            // 如果这一页不满50，说明已经获取完所有关注
                            if (list.size < 50) {
                                com.android.purebilibili.core.util.Logger.d("HomeVM", " Reached end at page $page, total: ${allMids.size}")
                                break
                            }
                            page++
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        com.android.purebilibili.core.util.Logger.e("HomeVM", " Error at page $page", e)
                        break
                    }
                }
            }
            
            //  保存到本地缓存
            prefs.edit()
                .putStringSet(cacheKey, allMids.map { it.toString() }.toSet())
                .putLong(cacheTimeKey, System.currentTimeMillis())
                .apply()
            
            _uiState.value = _uiState.value.copy(followingMids = allMids.toImmutableSet())
            com.android.purebilibili.core.util.Logger.d("HomeVM", " Total following mids fetched and cached: ${allMids.size}")
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e("HomeVM", " Error fetching following list", e)
        }
    }

    // [Feature] Preview Video URL logic
    suspend fun getPreviewVideoUrl(bvid: String, cid: Long): String? {
        return try {
            com.android.purebilibili.data.repository.VideoRepository.getPreviewVideoUrl(bvid, cid)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
