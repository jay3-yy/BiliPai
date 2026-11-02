package com.android.purebilibili.feature.space

import androidx.paging.LoadState
import androidx.paging.PagingConfig
import com.android.purebilibili.core.paging.ForwardPagingSession
import com.android.purebilibili.core.store.TokenManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.network.getSpaceAggregate
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.data.repository.HistoryRepository
import com.android.purebilibili.data.repository.shouldContinueDynamicFetchAfterFilter
import com.android.purebilibili.feature.bangumi.MY_FOLLOW_TYPE_BANGUMI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpaceSubTab {
    VIDEO,
    AUDIO,
    ARTICLE,
    OPUS,
    SEASON_VIDEO,
    SERIES,
    UGC_SEASON,
    CHARGING_VIDEO,
    COMIC
}

// UI 状态
sealed class SpaceUiState {
    object Loading : SpaceUiState()
    data class Success(
        val userInfo: SpaceUserInfo,
        val relationStat: RelationStatData? = null,
        val upStat: UpStatData? = null,
        // 头部充电/大航海摘要（App 端 /x/v2/space，失败时为 null，行不显示）
        val chargeGroup: SpaceSupporterGroup? = null,
        val guardGroup: SpaceSupporterGroup? = null,
        val videos: List<SpaceVideoItem> = emptyList(),
        val homeVideos: List<SpaceVideoItem> = emptyList(),
        val homeVideoCount: Int = 0,
        val totalVideos: Int = 0,
        val isLoadingMore: Boolean = false,
        val hasMoreVideos: Boolean = true,
        val videoPageLoadCompletionVersion: Long = 0L,
        val lastVideoPageLoadFailed: Boolean = false,
        //  视频分类
        val categories: List<SpaceVideoCategory> = emptyList(),
        val selectedTid: Int = 0,  // 0 表示全部
        //  视频排序
        val sortOrder: VideoSortOrder = VideoSortOrder.PUBDATE,
        //  合集和系列
        val seasons: List<SeasonItem> = emptyList(),
        val series: List<SeriesItem> = emptyList(),
        val seasonArchives: Map<Long, List<SeasonArchiveItem>> = emptyMap(),  // season_id -> videos
        val seriesArchives: Map<Long, List<SeriesArchiveItem>> = emptyMap(),   // series_id -> videos
        val createdFavoriteFolders: List<FavFolder> = emptyList(),
        val collectedFavoriteFolders: List<FavFolder> = emptyList(),
        //  主页 Tab
        val topVideo: SpaceTopArcData? = null,
        val notice: String = "",
        val homeFavoriteFolders: List<FavFolder> = emptyList(),
        val homeFavoriteFolderCount: Int = 0,
        val homeCoinVideos: List<SpaceAggregateArchiveItem> = emptyList(),
        val homeCoinVideoCount: Int = 0,
        val homeLikeVideos: List<SpaceAggregateArchiveItem> = emptyList(),
        val homeLikeVideoCount: Int = 0,
        val homeBangumiItems: List<SpaceAggregateArchiveItem> = emptyList(),
        val homeBangumiCount: Int = 0,
        val homeComicItems: List<SpaceAggregateArchiveItem> = emptyList(),
        val homeComicCount: Int = 0,
        val reservations: List<SpaceReservationItem> = emptyList(),
        val reservationBusyIds: Set<Long> = emptySet(),
        val hasMedalWall: Boolean = false,
        val medalWall: SpaceMedalWall? = null,
        val showFollowedUsers: Boolean = false,
        val followedUsers: List<FollowingUser> = emptyList(),
        val followedUsersPage: Int = 1,
        val hasMoreFollowedUsers: Boolean = true,
        val isLoadingFollowedUsers: Boolean = false,
        val hasLoadedFollowedUsers: Boolean = false,
        val isProfileActionRunning: Boolean = false,
        val profileNotice: SpaceProfileNotice? = null,
        val comics: List<SpaceAggregateArchiveItem> = emptyList(),
        val isLoadingComics: Boolean = false,
        val hasLoadedComics: Boolean = false,
        val comicError: String? = null,
        val bangumiItems: List<FollowBangumiItem> = emptyList(),
        val bangumiTotal: Int = 0,
        val bangumiPage: Int = 1,
        val isLoadingBangumi: Boolean = false,
        val hasMoreBangumi: Boolean = true,
        val hasLoadedBangumiOnce: Boolean = false,
        val lastBangumiLoadFailed: Boolean = false,
        //  动态 Tab
        val dynamics: List<SpaceDynamicItem> = emptyList(),
        val dynamicOffset: String = "",
        val hasMoreDynamics: Boolean = true,
        val isLoadingDynamics: Boolean = false,
        val hasLoadedDynamicsOnce: Boolean = false,
        val lastDynamicLoadFailed: Boolean = false,
        //  课堂 Tab
        val cheeseItems: List<SpaceCheeseItem> = emptyList(),
        val cheesePage: Int = 1,
        val isLoadingCheese: Boolean = false,
        val hasMoreCheese: Boolean = true,
        val hasLoadedCheeseOnce: Boolean = false,
        val lastCheeseLoadFailed: Boolean = false,
        val hasCheeseTab: Boolean = false,
        
        //  Uploads Sub-Tab
        val selectedSubTab: SpaceSubTab = SpaceSubTab.VIDEO,
        val selectedContributionTabId: String = createSpaceContributionTabId(param = "video"),
        val contributionTabs: List<SpaceContributionTab> = buildDefaultSpaceContributionTabs(),
        val audios: List<SpaceAudioItem> = emptyList(),
        val articles: List<SpaceArticleItem> = emptyList(),
        val opusItems: List<SpaceArticleItem> = emptyList(),
        val opusFilter: String = "all",
        val opusCollections: List<com.android.purebilibili.core.network.grpc.SpaceOpusCollection> = emptyList(),
        val opusOffset: String = "",
        val hasMoreOpus: Boolean = true,
        val hasLoadedOpus: Boolean = false,
        val isLoadingOpus: Boolean = false,
        val opusError: String? = null,
        val totalAudios: Int = 0,
        val totalArticles: Int = 0,
        val audioPage: Int = 1,
        val articlePage: Int = 1,
        val articleOffset: String = "",
        val isLoadingAudios: Boolean = false,
        val isLoadingArticles: Boolean = false,
        val hasMoreAudios: Boolean = true,
        val hasMoreArticles: Boolean = true,
        val watchProgressByBvid: Map<String, SpaceWatchProgress> = emptyMap(),
        val lastWatchedVideo: SpaceWatchProgress? = null,
        val pendingLocateBvid: String? = null,
        val locateMessage: String? = null,
        val isSearchMode: Boolean = false,
        val searchQuery: String = "",
        val headerState: SpaceHeaderState = SpaceHeaderState(null, null, null, null, "", emptyList(), emptyList()),
        val tabShellState: SpaceTabShellState = buildInitialTabShellState(),
        val mainTabs: List<SpaceMainTabItem> = buildDefaultSpaceMainTabs()
    ) : SpaceUiState()
    data class Error(val message: String) : SpaceUiState()
}

class SpaceViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private data class SpaceVideoLoadResult(
        val data: SpaceVideoData,
        val resolvedPage: Int
    )

    /** 首屏 HOME Tab（置顶视频 + 公告）的预取结果。 */
    private data class SpaceHomePayload(
        val mid: Long,
        val topVideo: SpaceTopArcData?,
        val notice: String?,
    )
    
    private val spaceApi = NetworkModule.spaceApi
    
    private val _uiState = MutableStateFlow<SpaceUiState>(SpaceUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Prevent duplicate relation requests while the previous click is still settling.
    private var followToggleInFlight = false

    private val _selectedMainTab = MutableStateFlow(
        savedStateHandle.get<Int>(KEY_SELECTED_MAIN_TAB) ?: 0
    )
    val selectedMainTab = _selectedMainTab.asStateFlow()
    private val hasSavedMainTabPreference = savedStateHandle.contains(KEY_SELECTED_MAIN_TAB)
    
    private var currentSpaceAccount = TokenManager.accountIdentity.value
    private var currentMid: Long = 0
    private var currentPage = 1
    private val pageSize = 30

    private val _followGroupDialogVisible = MutableStateFlow(false)
    val followGroupDialogVisible = _followGroupDialogVisible.asStateFlow()

    private val _followGroupTags = MutableStateFlow<List<RelationTagItem>>(emptyList())
    val followGroupTags = _followGroupTags.asStateFlow()

    private val _followGroupSelectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val followGroupSelectedTagIds = _followGroupSelectedTagIds.asStateFlow()

    private val _isFollowGroupsLoading = MutableStateFlow(false)
    val isFollowGroupsLoading = _isFollowGroupsLoading.asStateFlow()

    private val _isSavingFollowGroups = MutableStateFlow(false)
    val isSavingFollowGroups = _isSavingFollowGroups.asStateFlow()

    private var followGroupTargetMid: Long = 0L
    
    //  缓存 WBI keys 避免重复请求
    private var cachedImgKey: String = ""
    private var cachedSubKey: String = ""
    private var activeSpaceLoadGeneration: Long = 0
    private var activeSpaceLoadJob: Job? = null
    private var activeSpaceSupplementalJob: Job? = null
    private var activeSpaceArticleJob: Job? = null
    private var activeSpaceOpusJob: Job? = null
    private var activeSpaceAudioJob: Job? = null
    private var audioPagingSession: ForwardPagingSession<Int, SpaceContributionPage<SpaceAudioItem>>? = null
    private var articlePagingSession: ForwardPagingSession<Int, SpaceContributionPage<SpaceArticleItem>>? = null
    private var articlePagingReportsErrors = false
    private var contributionAccount = TokenManager.accountIdentity.value
    private var activeSpaceCheeseJob: Job? = null
    private var activeSpaceSearchJob: Job? = null
    private var activeVideoListJob: Job? = null
    private var videoPagingJob: Job? = null
    private var videoPagingSession: ForwardPagingSession<SpaceVideoPageKey, SpaceVideoPageChunk>? = null
    private var activeSpaceWatchHistoryJob: Job? = null
    private var activeVideoListGeneration: Long = 0

    // 首屏 HOME Tab（置顶视频 + 公告）预取：与 loadSpaceInfo 同时发出，把
    // 「等空间信息加载完 → 再拉 HOME」两段串行压成一段并行。结果由 loadSpaceHome() 消费。
    private var homePrefetchMid: Long = 0L
    private var homePrefetchDeferred: Deferred<SpaceHomePayload>? = null
    private val collectionPreviewLimit = 3
    private var currentKeyword: String = ""
    
    init {
        viewModelScope.launch {
            com.android.purebilibili.data.repository.FanManagementRepository.removed.collect { event ->
                if (event.identity != TokenManager.accountIdentity.value) return@collect
                _uiState.update { state ->
                    if (state !is SpaceUiState.Success) return@update state
                    val target = state.userInfo.mid
                    if (target != event.mid && target != event.identity.mid) return@update state
                    val user = if (target == event.mid && state.userInfo.relationStatus == 6) {
                        state.userInfo.copy(relationStatus = 2)
                    } else state.userInfo
                    val stats = state.relationStat?.let { stats ->
                        if (target == event.identity.mid) stats.copy(follower = (stats.follower - 1).coerceAtLeast(0))
                        else stats.copy(following = (stats.following - 1).coerceAtLeast(0))
                    }
                    state.copy(userInfo = user, relationStat = stats, headerState = state.headerState.copy(userInfo = user, relationStat = stats))
                }
            }
        }
        viewModelScope.launch {
            TokenManager.accountIdentity.collect { ensureContributionAccount() }
        }
        viewModelScope.launch {
            ActionRepository.followStateChanges.collect { change ->
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@collect
                if (currentState.userInfo.mid == change.mid && currentState.userInfo.isFollowed != change.isFollowing) {
                    val nextStatus = if (change.isFollowing) 2 else 0
                    val newUserInfo = currentState.userInfo.copy(
                        isFollowed = change.isFollowing,
                        relationStatus = nextStatus
                    )
                    _uiState.value = currentState.copy(
                        userInfo = newUserInfo,
                        headerState = currentState.headerState.copy(userInfo = newUserInfo)
                    )
                }
            }
        }
    }

    fun loadSpaceInfo(mid: Long) {
        if (mid <= 0) return

        // Fix: Prevent reloading if data is already loaded for this mid
        if (currentMid == mid && _uiState.value is SpaceUiState.Success &&
            currentSpaceAccount == TokenManager.accountIdentity.value) {
            return
        }

        currentMid = mid
        currentSpaceAccount = TokenManager.accountIdentity.value
        currentPage = 1
        currentTid = 0
        currentOrder = VideoSortOrder.PUBDATE
        currentKeyword = ""
        currentVideoSpecialType = null
        activeSpaceSearchJob?.cancel()
        activeVideoListJob?.cancel()
        videoPagingJob?.cancel()
        videoPagingSession = null
        activeSpaceLoadGeneration += 1
        activeVideoListGeneration += 1
        val requestGeneration = activeSpaceLoadGeneration
        activeSpaceLoadJob?.cancel()
        activeSpaceSupplementalJob?.cancel()
        activeSpaceArticleJob?.cancel()
        activeSpaceOpusJob?.cancel()
        activeSpaceAudioJob?.cancel()
        articlePagingSession = null
        audioPagingSession = null
        activeSpaceCheeseJob?.cancel()
        activeSpaceWatchHistoryJob?.cancel()

        activeSpaceLoadJob = viewModelScope.launch {
            _uiState.value = SpaceUiState.Loading

            try {
                // HOME Tab（置顶视频 + 公告）与空间信息互不依赖，这里就一起发出去，
                // 省掉「等空间信息加载完 → 再拉 HOME」那段串行往返。
                preloadSpaceHome(mid)
                val cardTopPhotoDeferred = async { fetchUserCardSpaceTopPhoto(mid) }
                val aggregateDeferred = async { fetchSpaceAggregate(mid) }
                val keysDeferred = async { fetchWbiKeys() }
                val userCardVisuals = cardTopPhotoDeferred.await()
                if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    return@launch
                }

                val aggregateSeed = aggregateDeferred.await()?.let { aggregate ->
                    resolveSpaceInitialSeedFromAggregate(
                        data = aggregate,
                        cardLargePhoto = userCardVisuals.largePhoto,
                        cardSmallPhoto = userCardVisuals.smallPhoto,
                        cardIpLocation = userCardVisuals.ipLocation,
                    )
                }

                if (aggregateSeed != null) {
                    val resolvedSavedTab = if (hasSavedMainTabPreference) {
                        tabIndexToMainTab(_selectedMainTab.value)
                    } else {
                        aggregateSeed.defaultMainTab
                    }
                    val initialMainTab = aggregateSeed.mainTabs
                        .firstOrNull { it.tab == resolvedSavedTab }
                        ?.tab
                        ?: aggregateSeed.defaultMainTab
                    _selectedMainTab.value = mainTabToTabIndex(initialMainTab)
                    currentPage = 1
                    currentVideoSpecialType = if (aggregateSeed.defaultSubTab == SpaceSubTab.CHARGING_VIDEO) "charging" else null
                    _uiState.value = buildInitialSpaceSuccessState(
                        seed = aggregateSeed,
                        selectedMainTab = initialMainTab,
                        selectedSubTab = aggregateSeed.defaultSubTab
                    )
                    loadSpaceWatchHistory(mid = mid, requestGeneration = requestGeneration)
                    loadSpaceSupplemental(mid = mid, requestGeneration = requestGeneration)
                    loadSpaceHeaderMetrics(mid = mid, requestGeneration = requestGeneration)
                    loadSpaceFollowStatus(mid = mid, requestGeneration = requestGeneration)
                    val keys = keysDeferred.await()
                    if (shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration) && keys != null) {
                        cachedImgKey = keys.first
                        cachedSubKey = keys.second
                        loadSpaceLegacyProfileVisuals(
                            mid = mid,
                            requestGeneration = requestGeneration,
                            userCardVisuals = userCardVisuals
                        )
                        hydrateInitialContributionVideos(mid = mid, requestGeneration = requestGeneration)
                        ensureSelectedContributionContentLoaded()
                        probeSpaceArticles(mid = mid, requestGeneration = requestGeneration)
                        probeSpaceCheese(mid = mid, requestGeneration = requestGeneration)
                    }
                    return@launch
                }

                val keys = keysDeferred.await()
                if (keys == null) {
                    if (shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                        _uiState.value = SpaceUiState.Error("获取空间信息失败")
                    }
                    return@launch
                }
                if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    return@launch
                }
                cachedImgKey = keys.first
                cachedSubKey = keys.second

                if (!loadSpaceInfoLegacy(mid, requestGeneration, userCardVisuals)) {
                    if (shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                        _uiState.value = SpaceUiState.Error("获取用户信息失败")
                    }
                } else {
                    probeSpaceArticles(mid = mid, requestGeneration = requestGeneration)
                    probeSpaceCheese(mid = mid, requestGeneration = requestGeneration)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "Error loading space: ${e.message}", e)
                if (shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    _uiState.value = SpaceUiState.Error(e.message ?: "加载失败")
                }
            }
        }
    }

    private suspend fun loadSpaceInfoLegacy(
        mid: Long,
        requestGeneration: Long,
        userCardVisuals: SpaceUserCardVisuals
    ): Boolean = coroutineScope {
        val infoDeferred = async { fetchSpaceInfo(mid, cachedImgKey, cachedSubKey) }
        // The space info endpoint is not consistent about including `is_followed`.
        // Use the relation endpoint as the authoritative initial value when available.
        val followStatusDeferred = async { ActionRepository.checkFollowStatus(mid) }
        val relationDeferred = async { fetchRelationStat(mid) }
        val upStatDeferred = async { fetchUpStat(mid) }
        val videosDeferred = async {
            fetchInitialSpaceVideos(
                mid = mid,
                imgKey = cachedImgKey,
                subKey = cachedSubKey,
                tid = currentTid,
                order = currentOrder,
                keyword = currentKeyword
            )
        }

        val userInfoRaw = infoDeferred.await() ?: return@coroutineScope false
        val followStatus = followStatusDeferred.await()
        val relationStat = relationDeferred.await()
        val upStat = upStatDeferred.await()
        val videosResult = videosDeferred.await()
        if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
            return@coroutineScope true
        }

        val resolvedTopPhoto = resolveSpaceTopPhoto(
            topPhoto = userInfoRaw.topPhoto,
            cardLargePhoto = userCardVisuals.largePhoto,
            cardSmallPhoto = userCardVisuals.smallPhoto
        )
        val resolvedIpLocation = userInfoRaw.ipLocation?.takeIf { it.isNotBlank() }
            ?: userCardVisuals.ipLocation?.takeIf { it.isNotBlank() }
        val resolvedSpaceTags = if (!resolvedIpLocation.isNullOrBlank()) {
            val locationTitle = if (resolvedIpLocation.startsWith("IP属地")) resolvedIpLocation else "IP属地：$resolvedIpLocation"
            listOf(SpaceTagItem(type = "location", title = locationTitle))
        } else {
            emptyList()
        }
        val userInfo = userInfoRaw.copy(
            topPhoto = resolvedTopPhoto,
            isFollowed = followStatus,
            relationStatus = if (followStatus) 2 else 0,
            ipLocation = resolvedIpLocation,
            spaceTags = resolvedSpaceTags
        )
        currentPage = videosResult?.resolvedPage ?: 1
        val videoData = videosResult?.data
        val videos = videoData?.list?.vlist ?: emptyList()
        val categories = extractCategories(videos)
        // UP 主页投稿列表到手就预热横竖屏判定，点击进详情时首帧即进入正确布局。
        com.android.purebilibili.data.repository.VideoRepository
            .prefetchVerticalFlags(videos.map { it.bvid })

        _uiState.value = SpaceUiState.Success(
            userInfo = userInfo,
            relationStat = relationStat,
            upStat = upStat,
            videos = videos,
            homeVideos = videos,
            homeVideoCount = videoData?.page?.count ?: 0,
            totalVideos = videoData?.page?.count ?: 0,
            hasMoreVideos = resolveNextSpaceVideoPage(
                order = currentOrder,
                currentPage = currentPage,
                totalCount = videoData?.page?.count ?: 0,
                pageSize = pageSize
            ) != null,
            categories = categories,
            headerState = buildHeaderState(
                userInfo = userInfo,
                relationStat = relationStat,
                upStat = upStat,
                topVideo = null,
                notice = "",
                createdFavorites = emptyList(),
                collectedFavorites = emptyList()
            ),
            tabShellState = buildInitialTabShellState(
                selectedTab = tabIndexToMainTab(_selectedMainTab.value)
            ).withUpdatedTab(SpaceMainTab.CONTRIBUTION) { it.copy(hasLoaded = true) }
        )
        loadSpaceWatchHistory(mid = mid, requestGeneration = requestGeneration)
        loadSpaceSupplemental(mid = mid, requestGeneration = requestGeneration)
        ensureSelectedContributionContentLoaded()
        true
    }

    private fun loadSpaceHeaderMetrics(mid: Long, requestGeneration: Long) {
        viewModelScope.launch {
            try {
                val relationDeferred = async { fetchRelationStat(mid) }
                val upStatDeferred = async { fetchUpStat(mid) }
                val relationStat = relationDeferred.await()
                val upStat = upStatDeferred.await()
                if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    return@launch
                }

                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                val mergedRelation = relationStat ?: currentState.relationStat
                val mergedUpStat = upStat ?: currentState.upStat
                _uiState.value = currentState.copy(
                    relationStat = mergedRelation,
                    upStat = mergedUpStat,
                    headerState = currentState.headerState.copy(
                        relationStat = mergedRelation,
                        upStat = mergedUpStat
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "loadSpaceHeaderMetrics error: ${e.message}", e)
            }
        }
    }

    private fun loadSpaceFollowStatus(mid: Long, requestGeneration: Long) {
        viewModelScope.launch {
            val relationData = ActionRepository.getRelationDetail(mid)
            if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                return@launch
            }
            val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
            val (isFollowed, relationStatus) = if (relationData != null) {
                val following = relationData.isFollowing
                val status = if (!following) {
                    if (relationData.attribute == 128) 128 else 0
                } else if (relationData.special == 1) {
                    -10
                } else {
                    relationData.attribute.takeIf { it != 0 } ?: 2
                }
                Pair(following, status)
            } else {
                val following = ActionRepository.checkFollowStatus(mid)
                Pair(following, if (following) 2 else 0)
            }
            val userInfo = currentState.userInfo.copy(
                isFollowed = isFollowed,
                relationStatus = relationStatus
            )
            _uiState.value = currentState.copy(
                userInfo = userInfo,
                headerState = currentState.headerState.copy(userInfo = userInfo),
            )
        }
    }

    private fun loadSpaceWatchHistory(mid: Long, requestGeneration: Long) {
        activeSpaceWatchHistoryJob?.cancel()
        activeSpaceWatchHistoryJob = viewModelScope.launch {
            val firstPage = HistoryRepository.getHistoryList(ps = 50).getOrNull() ?: return@launch
            val records = firstPage.list.toMutableList()
            val cursor = firstPage.cursor
            if (cursor != null && cursor.max > 0L) {
                HistoryRepository.getHistoryList(
                    ps = 50,
                    max = cursor.max,
                    viewAt = cursor.view_at,
                    business = cursor.business
                ).getOrNull()?.list?.let(records::addAll)
            }
            if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                return@launch
            }

            val progressByBvid = resolveSpaceWatchProgressByBvid(records, upMid = mid)
            val current = _uiState.value as? SpaceUiState.Success ?: return@launch
            _uiState.value = current.copy(
                watchProgressByBvid = progressByBvid,
                lastWatchedVideo = resolveSpaceLastWatchedVideo(progressByBvid)
            )
        }
    }

    fun refreshProfileHeader() {
        val mid = currentMid
        val generation = activeSpaceLoadGeneration
        viewModelScope.launch {
            if (ensureWbiKeysLoaded()) loadSpaceLegacyProfileVisuals(mid, generation, SpaceUserCardVisuals())
        }
    }

    private fun loadSpaceLegacyProfileVisuals(
        mid: Long,
        requestGeneration: Long,
        userCardVisuals: SpaceUserCardVisuals
    ) {
        viewModelScope.launch {
            try {
                val info = fetchSpaceInfo(mid, cachedImgKey, cachedSubKey) ?: return@launch
                if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    return@launch
                }

                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                val resolvedTopPhoto = currentState.userInfo.topPhoto.ifBlank {
                    resolveSpaceTopPhoto(
                        topPhoto = info.topPhoto,
                        cardLargePhoto = userCardVisuals.largePhoto,
                        cardSmallPhoto = userCardVisuals.smallPhoto
                    )
                }
                val resolvedIpLocation = info.ipLocation?.takeIf { it.isNotBlank() }
                    ?: userCardVisuals.ipLocation?.takeIf { it.isNotBlank() }
                    ?: currentState.userInfo.ipLocation
                val updatedTags = if (currentState.userInfo.spaceTags.none { it.type == "location" || it.title.contains("IP") } && !resolvedIpLocation.isNullOrBlank()) {
                    val locationTitle = if (resolvedIpLocation.startsWith("IP属地")) resolvedIpLocation else "IP属地：$resolvedIpLocation"
                    currentState.userInfo.spaceTags + SpaceTagItem(type = "location", title = locationTitle)
                } else {
                    currentState.userInfo.spaceTags
                }
                val mergedUserInfo = currentState.userInfo.copy(
                    name = info.name.ifBlank { currentState.userInfo.name },
                    sex = info.sex.ifBlank { currentState.userInfo.sex },
                    face = info.face.ifBlank { currentState.userInfo.face },
                    sign = info.sign,
                    level = info.level.takeIf { it > 0 } ?: currentState.userInfo.level,
                    official = info.official.takeIf {
                        it.title.isNotBlank() || it.desc.isNotBlank() || it.type >= 0
                    } ?: currentState.userInfo.official,
                    vip = info.vip.takeIf {
                        it.status > 0 || it.type > 0 || it.label.text.isNotBlank()
                    } ?: currentState.userInfo.vip,
                    topPhoto = resolvedTopPhoto,
                    liveRoom = info.liveRoom ?: currentState.userInfo.liveRoom,
                    livePlace = info.livePlace ?: currentState.userInfo.livePlace,
                    ipLocation = resolvedIpLocation,
                    spaceTags = updatedTags
                )

                _uiState.value = currentState.copy(
                    userInfo = mergedUserInfo,
                    headerState = currentState.headerState.copy(userInfo = mergedUserInfo)
                )
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "loadSpaceLegacyProfileVisuals error: ${e.message}", e)
            }
        }
    }

    private fun hydrateInitialContributionVideos(mid: Long, requestGeneration: Long) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (!shouldHydrateSpaceContributionVideos(
                totalVideos = current.totalVideos,
                seededVideoCount = current.videos.size,
                pageSize = pageSize,
                selectedSubTab = current.selectedSubTab,
                selectedTid = current.selectedTid,
                currentOrder = current.sortOrder,
                currentKeyword = currentKeyword
            )
        ) {
            return
        }
        val requestVideoGeneration = beginVideoListRequest()
        val requestTid = currentTid
        val requestOrder = currentOrder
        val requestKeyword = currentKeyword
        val requestSpecialType = currentVideoSpecialType

        _uiState.value = current.copy(isLoadingMore = true)

        activeVideoListJob = viewModelScope.launch {
            try {
                val result = fetchInitialSpaceVideos(
                    mid = mid,
                    imgKey = cachedImgKey,
                    subKey = cachedSubKey,
                    tid = requestTid,
                    order = requestOrder,
                    keyword = requestKeyword,
                    specialType = requestSpecialType
                )
                if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    return@launch
                }
                if (!shouldApplySpaceVideoResult(mid, currentMid, requestVideoGeneration, activeVideoListGeneration, requestTid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val latest = _uiState.value as? SpaceUiState.Success ?: return@launch
                if (result == null) {
                    _uiState.value = latest.copy(isLoadingMore = false)
                    return@launch
                }
                currentPage = result.resolvedPage
                _uiState.value = latest.copy(
                    videos = result.data.list.vlist,
                    totalVideos = result.data.page.count,
                    hasMoreVideos = resolveNextSpaceVideoPage(
                        order = requestOrder,
                        currentPage = currentPage,
                        totalCount = result.data.page.count,
                        pageSize = pageSize
                    ) != null,
                    categories = extractCategories(result.data.list.vlist),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "hydrateInitialContributionVideos error: ${e.message}", e)
                if (!shouldApplySpaceVideoResult(mid, currentMid, requestVideoGeneration, activeVideoListGeneration, requestTid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val latest = _uiState.value as? SpaceUiState.Success ?: return@launch
                _uiState.value = latest.copy(isLoadingMore = false)
            }
        }
    }

    private fun loadSpaceSupplemental(mid: Long, requestGeneration: Long) {
        activeSpaceSupplementalJob?.cancel()
        activeSpaceSupplementalJob = viewModelScope.launch {
            try {
                val seasonsSeriesDeferred = async { fetchSeasonsSeriesList(mid) }
                val createdFavoriteFoldersDeferred = async { fetchCreatedFavoriteFolders(mid) }
                val collectedFavoriteFoldersDeferred = async { fetchCollectedFavoriteFolders(mid) }
                val supportersDeferred = async { fetchSpaceSupporters(mid) }

                val seasonsSeriesResult = seasonsSeriesDeferred.await()
                val seasons = seasonsSeriesResult?.items_lists?.seasons_list ?: emptyList()
                val series = seasonsSeriesResult?.items_lists?.series_list ?: emptyList()
                val createdFavoriteFolders = createdFavoriteFoldersDeferred.await()
                val collectedFavoriteFolders = collectedFavoriteFoldersDeferred.await()
                val embeddedSeasonArchives = resolveEmbeddedSeasonArchives(seasons)
                val embeddedSeriesArchives = resolveEmbeddedSeriesArchives(series)

                com.android.purebilibili.core.util.Logger.d("SpaceVM", " Seasons: ${seasons.size}, Series: ${series.size}")

                val seasonArchiveDeferreds = seasons
                    .take(collectionPreviewLimit)
                    .filter { it.meta.season_id !in embeddedSeasonArchives }
                    .associate { season ->
                        season.meta.season_id to async { fetchSeasonArchives(mid, season.meta.season_id).orEmpty() }
                    }
                val seriesArchiveDeferreds = series
                    .take(collectionPreviewLimit)
                    .filter { it.meta.series_id !in embeddedSeriesArchives }
                    .associate { seriesItem ->
                        seriesItem.meta.series_id to async { fetchSeriesArchives(mid, seriesItem.meta.series_id).orEmpty() }
                    }

                val seasonArchives = embeddedSeasonArchives + seasonArchiveDeferreds.mapValues { (_, deferred) -> deferred.await() }
                    .filterValues { it.isNotEmpty() }
                val seriesArchives = embeddedSeriesArchives + seriesArchiveDeferreds.mapValues { (_, deferred) -> deferred.await() }
                    .filterValues { it.isNotEmpty() }

                if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    return@launch
                }

                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                val updatedSupplementalState = applySpaceSupplementalData(
                    state = currentState,
                    seasons = seasons,
                    series = series,
                    createdFavoriteFolders = createdFavoriteFolders,
                    collectedFavoriteFolders = collectedFavoriteFolders,
                    seasonArchives = seasonArchives,
                    seriesArchives = seriesArchives
                )
                val currentInfo = updatedSupplementalState.userInfo
                if (currentInfo.spaceTags.none { it.type == "location" || it.title.contains("IP") } && currentInfo.ipLocation.isNullOrBlank()) {
                    val dynamicResp = runCatching { spaceApi.getSpaceDynamic(hostMid = mid) }.getOrNull()
                    val dynamicIp = dynamicResp?.data?.items?.firstNotNullOfOrNull { item ->
                        item.modules.module_author?.pub_location_text?.takeIf { it.isNotBlank() }
                    }
                    if (!dynamicIp.isNullOrBlank() && shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                        val locationTitle = if (dynamicIp.startsWith("IP属地")) dynamicIp else "IP属地：$dynamicIp"
                        val updatedTags = currentInfo.spaceTags + SpaceTagItem(type = "location", title = locationTitle)
                        val updatedUserInfo = currentInfo.copy(
                            ipLocation = dynamicIp,
                            spaceTags = updatedTags
                        )
                        _uiState.value = updatedSupplementalState.copy(
                            userInfo = updatedUserInfo,
                            headerState = updatedSupplementalState.headerState.copy(userInfo = updatedUserInfo)
                        )
                    } else {
                        _uiState.value = updatedSupplementalState
                    }
                } else {
                    _uiState.value = updatedSupplementalState
                }
                applySpaceSupporters(mid, requestGeneration, supportersDeferred.await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "loadSpaceSupplemental error: ${e.message}", e)
            }
        }
    }

    private suspend fun fetchSpaceSupporters(mid: Long): Pair<SpaceSupporterGroup?, SpaceSupporterGroup?> {
        return runCatching {
            val params = mapOf(
                "vmid" to mid.toString(),
                "build" to "8430300",
                "mobi_app" to "android",
                "platform" to "android",
                "channel" to "master",
                "s_locale" to "zh_CN",
                "c_locale" to "zh_CN",
                "appkey" to com.android.purebilibili.core.network.AppSignUtils.ANDROID_APP_KEY,
                "ts" to com.android.purebilibili.core.network.AppSignUtils.getTimestamp().toString(),
            )
            val response = spaceApi.getAppSpaceSupporters(
                com.android.purebilibili.core.network.AppSignUtils.signForAndroidApi(params)
            )
            if (response.code != 0) return@runCatching null to null
            resolveSpaceSupporterGroups(response.data)
        }.onFailure {
            android.util.Log.d("SpaceVM", "fetchSpaceSupporters failed: ${it.message}")
        }.getOrDefault(null to null)
    }

    private fun applySpaceSupporters(
        mid: Long,
        requestGeneration: Long,
        groups: Pair<SpaceSupporterGroup?, SpaceSupporterGroup?>,
    ) {
        if (groups.first == null && groups.second == null) return
        if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) return
        val current = _uiState.value as? SpaceUiState.Success ?: return
        _uiState.value = current.copy(chargeGroup = groups.first, guardGroup = groups.second)
    }

    fun selectMainTab(tab: Int) {
        selectMainTab(com.android.purebilibili.feature.space.tabIndexToMainTab(tab))
    }

    fun selectMainTab(tab: SpaceMainTab) {
        val newTab = tab
        val newTabIndex = mainTabToTabIndex(tab)
        _selectedMainTab.value = newTabIndex
        savedStateHandle[KEY_SELECTED_MAIN_TAB] = newTabIndex
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val previousScope = resolveSpaceSearchScope(
            selectedMainTab = current.tabShellState.selectedTab,
            selectedSubTab = current.selectedSubTab
        )
        val nextScope = resolveSpaceSearchScope(
            selectedMainTab = newTab,
            selectedSubTab = current.selectedSubTab
        )
        _uiState.value = current.copy(
            isSearchMode = if (previousScope == nextScope) current.isSearchMode else false,
            searchQuery = if (previousScope == nextScope) current.searchQuery else "",
            tabShellState = current.tabShellState.withSelectedTab(newTab)
        )
        if (previousScope == SpaceSearchScope.VIDEO && previousScope != nextScope && currentKeyword.isNotBlank()) {
            clearVideoSearchResults()
        }
        if (newTab == SpaceMainTab.CONTRIBUTION) {
            ensureSelectedContributionContentLoaded()
        } else if (newTab == SpaceMainTab.CHEESE) {
            val cheeseTabState = current.tabShellState.tabStates[SpaceMainTab.CHEESE]
            if (cheeseTabState?.hasLoaded != true && !current.isLoadingCheese) {
                loadSpaceCheese()
            }
        }
    }
    
    private fun beginVideoListRequest(): Long {
        activeVideoListJob?.cancel()
        videoPagingJob?.cancel()
        videoPagingSession = null
        activeVideoListGeneration += 1
        return activeVideoListGeneration
    }

    fun loadMoreVideos() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isLoadingMore || !current.hasMoreVideos) return
        videoPagingSession?.let { it.appendOrRetry(); return }
        val generation = activeVideoListGeneration
        val requestMid = currentMid
        val requestTid = currentTid
        val requestOrder = currentOrder
        val requestKeyword = currentKeyword
        val requestSpecialType = currentVideoSpecialType
        val identity = TokenManager.accountIdentity.value
        val seed = SpaceVideoPageChunk(currentPage, current.videos, current.totalVideos)
        var appliedPages = 0
        var lastFailed = false
        lateinit var session: ForwardPagingSession<SpaceVideoPageKey, SpaceVideoPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                spaceVideoPagingSource(seed, requestOrder, pageSize) { number ->
                    check(ensureWbiKeysLoaded()) { "无法获取视频请求签名" }
                    val result = checkNotNull(fetchSpaceVideos(
                        requestMid, number, cachedImgKey, cachedSubKey, requestTid, requestOrder, requestKeyword, requestSpecialType
                    )) { "视频加载失败，请重试" }
                    SpaceVideoPageChunk(number, normalizeSpaceVideoPage(requestOrder, result.list.vlist), result.page.count)
                }
            },
            onChanged = { pages, states ->
                if (videoPagingSession === session && identity == TokenManager.accountIdentity.value &&
                    shouldApplySpaceVideoResult(requestMid, currentMid, generation, activeVideoListGeneration,
                        requestTid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword) && states != null
                ) {
                    val failed = states.refresh is LoadState.Error || states.append is LoadState.Error
                    val ready = states.refresh is LoadState.NotLoading && pages.size > appliedPages
                    val completed = (ready && pages.size > 1) || (failed && !lastFailed)
                    _uiState.update { state ->
                        val latest = state as? SpaceUiState.Success
                        if (latest == null) state else latest.copy(
                            videos = if (ready) mergeSpaceVideoPages(latest.videos, pages.drop(appliedPages).flatMap { it.items }) else latest.videos,
                            isLoadingMore = states.refresh is LoadState.Loading || states.append is LoadState.Loading,
                            hasMoreVideos = !states.append.endOfPaginationReached,
                            videoPageLoadCompletionVersion = latest.videoPageLoadCompletionVersion + if (completed) 1 else 0,
                            lastVideoPageLoadFailed = failed,
                        )
                    }
                    if (ready) {
                        // 新一页到手就预热横竖屏判定（首屏已在 Success 构建时预热过）。
                        com.android.purebilibili.data.repository.VideoRepository
                            .prefetchVerticalFlags(pages.drop(appliedPages).flatMap { it.items }.map { it.bvid })
                        appliedPages = pages.size
                        currentPage = pages.last().number
                    }
                    lastFailed = failed
                }
            }
        )
        videoPagingSession = session
        videoPagingJob = viewModelScope.launch {
            session.run()
        }
        viewModelScope.launch {
            session.awaitInitialLoad()
            if (videoPagingSession === session) session.append()
        }
    }

    //  获取 WBI 签名 keys
    private suspend fun fetchWbiKeys(): Pair<String, String>? {
        return try {
            val navResp = NetworkModule.api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img ?: return null
            val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
            Pair(imgKey, subKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchWbiKeys error: ${e.message}")
            null
        }
    }

    private suspend fun ensureWbiKeysLoaded(): Boolean {
        if (cachedImgKey.isNotBlank() && cachedSubKey.isNotBlank()) return true
        val keys = fetchWbiKeys() ?: return false
        cachedImgKey = keys.first
        cachedSubKey = keys.second
        return true
    }

    private suspend fun fetchSpaceAggregate(mid: Long): SpaceAggregateData? {
        return try {
            val response = spaceApi.getSpaceAggregate(mid = mid)
            if (response.code == 0) response.data else null
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchSpaceAggregate error: ${e.message}", e)
            null
        }
    }
    
    private suspend fun fetchSpaceInfo(mid: Long, imgKey: String, subKey: String): SpaceUserInfo? {
        return try {
            val params = WbiUtils.sign(mapOf("mid" to mid.toString()), imgKey, subKey)
            com.android.purebilibili.core.util.Logger.d("SpaceVM", "🔍 fetchSpaceInfo params: $params")
            val response = spaceApi.getSpaceInfo(params)
            com.android.purebilibili.core.util.Logger.d("SpaceVM", " fetchSpaceInfo response: code=${response.code}, message=${response.message}")
            if (response.code == 0) response.data else {
                android.util.Log.e("SpaceVM", " fetchSpaceInfo failed: code=${response.code}, message=${response.message}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchSpaceInfo error: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchUserCardSpaceTopPhoto(mid: Long): SpaceUserCardVisuals {
        return try {
            val response = NetworkModule.api.getUserCard(mid = mid, photo = true)
            if (response.code == 0) {
                val space = response.data?.space
                val rawIpLocation = response.data?.card?.ipLocation?.takeIf { it.isNotBlank() }
                    ?: response.data?.ipLocation?.takeIf { it.isNotBlank() }
                SpaceUserCardVisuals(
                    largePhoto = space?.l_img.orEmpty(),
                    smallPhoto = space?.s_img.orEmpty(),
                    ipLocation = rawIpLocation
                )
            } else {
                SpaceUserCardVisuals()
            }
        } catch (_: Exception) {
            SpaceUserCardVisuals()
        }
    }
    
    private suspend fun fetchRelationStat(mid: Long): RelationStatData? {
        return try {
            val response = spaceApi.getRelationStat(mid)
            if (response.code == 0) response.data else null
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun fetchUpStat(mid: Long): UpStatData? {
        return try {
            val response = spaceApi.getUpStat(mid)
            if (response.code == 0) response.data else null
        } catch (e: Exception) {
            null
        }
    }


    
    //  支持 tid 和 order 参数的视频获取
    private suspend fun fetchSpaceVideos(mid: Long, page: Int, imgKey: String, subKey: String,
        tid: Int = 0, order: VideoSortOrder = VideoSortOrder.PUBDATE, keyword: String = "", specialType: String? = currentVideoSpecialType): SpaceVideoData? =
        com.android.purebilibili.data.repository.UserContentRepository.videos(mid, page, pageSize, order.apiValue,
            tid, keyword, imgKey, subKey, specialType).getOrNull()

    private suspend fun fetchSpaceVideosWithRetry(
        mid: Long,
        page: Int,
        imgKey: String,
        subKey: String,
        tid: Int = 0,
        order: VideoSortOrder = VideoSortOrder.PUBDATE,
        keyword: String = "",
        specialType: String? = currentVideoSpecialType
    ): SpaceVideoData? {
        fetchSpaceVideos(
            mid = mid,
            page = page,
            imgKey = imgKey,
            subKey = subKey,
            tid = tid,
            order = order,
            keyword = keyword,
            specialType = specialType
        )?.let { return it }

        delay(250)

        return fetchSpaceVideos(
            mid = mid,
            page = page,
            imgKey = imgKey,
            subKey = subKey,
            tid = tid,
            order = order,
            keyword = keyword,
            specialType = specialType
        )
    }

    private suspend fun fetchInitialSpaceVideos(
        mid: Long,
        imgKey: String,
        subKey: String,
        tid: Int = 0,
        order: VideoSortOrder = VideoSortOrder.PUBDATE,
        keyword: String = "",
        specialType: String? = currentVideoSpecialType
    ): SpaceVideoLoadResult? {
        val firstPageResult = fetchSpaceVideosWithRetry(
            mid = mid,
            page = 1,
            imgKey = imgKey,
            subKey = subKey,
            tid = tid,
            order = order,
            keyword = keyword,
            specialType = specialType
        ) ?: return null

        val resolvedPage = resolveInitialSpaceVideoPage(
            order = order,
            totalCount = firstPageResult.page.count,
            pageSize = pageSize
        )
        if (resolvedPage == 1) {
            return SpaceVideoLoadResult(
                data = firstPageResult.copy(
                    list = firstPageResult.list.copy(
                        vlist = normalizeSpaceVideoPage(order, firstPageResult.list.vlist)
                    )
                ),
                resolvedPage = 1
            )
        }

        val resolvedPageResult = fetchSpaceVideosWithRetry(
            mid = mid,
            page = resolvedPage,
            imgKey = imgKey,
            subKey = subKey,
            tid = tid,
            order = order,
            keyword = keyword,
            specialType = specialType
        ) ?: return null

        return SpaceVideoLoadResult(
            data = resolvedPageResult.copy(
                page = resolvedPageResult.page.copy(count = firstPageResult.page.count),
                list = resolvedPageResult.list.copy(
                    vlist = normalizeSpaceVideoPage(order, resolvedPageResult.list.vlist)
                )
            ),
            resolvedPage = resolvedPage
        )
    }
    
    //  分类选择
    private var currentVideoSpecialType: String? = null
    private var currentTid = 0
    private var currentOrder = VideoSortOrder.PUBDATE
    
    //  排序选择
    fun selectSortOrder(order: VideoSortOrder) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.sortOrder == order) return
        
        android.util.Log.d("SpaceVM", " selectSortOrder: order=${order.apiValue}, currentTid=$currentTid")
        
        currentOrder = order
        val requestGeneration = beginVideoListRequest()
        val requestTid = currentTid
        val requestKeyword = currentKeyword
        val requestSpecialType = currentVideoSpecialType
        
        activeVideoListJob = viewModelScope.launch {
            _uiState.value = current.copy(
                sortOrder = order,
                videos = emptyList(),
                isLoadingMore = true
            )
            
            try {
                if (!ensureWbiKeysLoaded()) {
                    if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, requestTid, currentTid, order, currentOrder, requestKeyword, currentKeyword)) {
                        return@launch
                    }
                    val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                    _uiState.value = currentState.copy(isLoadingMore = false)
                    return@launch
                }
                val result = fetchInitialSpaceVideos(
                    mid = currentMid,
                    imgKey = cachedImgKey,
                    subKey = cachedSubKey,
                    tid = requestTid,
                    order = order,
                    keyword = requestKeyword,
                    specialType = requestSpecialType
                )
                if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, requestTid, currentTid, order, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                
                if (result != null) {
                    currentPage = result.resolvedPage
                    _uiState.value = currentState.copy(
                        videos = result.data.list.vlist,
                        totalVideos = result.data.page.count,
                        hasMoreVideos = resolveNextSpaceVideoPage(
                            order = order,
                            currentPage = currentPage,
                            totalCount = result.data.page.count,
                            pageSize = pageSize
                        ) != null,
                        isLoadingMore = false
                    )
                } else {
                    _uiState.value = currentState.copy(isLoadingMore = false)
                }
            } catch (e: Exception) {
                if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, requestTid, currentTid, order, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }
    
    fun selectCategory(tid: Int) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.selectedTid == tid) return  // 避免重复选择
        
        android.util.Log.d("SpaceVM", " selectCategory: tid=$tid, currentOrder=$currentOrder")
        
        currentTid = tid
        val requestGeneration = beginVideoListRequest()
        val requestOrder = currentOrder
        val requestKeyword = currentKeyword
        val requestSpecialType = currentVideoSpecialType
        
        activeVideoListJob = viewModelScope.launch {
            _uiState.value = current.copy(
                selectedTid = tid,
                videos = emptyList(),
                isLoadingMore = true
            )
            
            try {
                if (!ensureWbiKeysLoaded()) {
                    if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, tid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                        return@launch
                    }
                    val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                    _uiState.value = currentState.copy(isLoadingMore = false)
                    return@launch
                }
                val result = fetchInitialSpaceVideos(
                    mid = currentMid,
                    imgKey = cachedImgKey,
                    subKey = cachedSubKey,
                    tid = tid,
                    order = requestOrder,
                    keyword = requestKeyword,
                    specialType = requestSpecialType
                )
                if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, tid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                
                if (result != null) {
                    currentPage = result.resolvedPage
                    android.util.Log.d("SpaceVM", " selectCategory success: ${result.data.list.vlist.size} videos")
                    _uiState.value = currentState.copy(
                        videos = result.data.list.vlist,
                        totalVideos = result.data.page.count,
                        hasMoreVideos = resolveNextSpaceVideoPage(
                            order = currentOrder,
                            currentPage = currentPage,
                            totalCount = result.data.page.count,
                            pageSize = pageSize
                        ) != null,
                        isLoadingMore = false
                    )
                } else {
                    android.util.Log.e("SpaceVM", " selectCategory failed: result is null")
                    _uiState.value = currentState.copy(isLoadingMore = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", " selectCategory error: ${e.message}", e)
                if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, tid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }
    
    //  解析分类信息 - 从视频列表中统计分类
    private fun extractCategories(videos: List<SpaceVideoItem>): List<SpaceVideoCategory> {
        // 即使 typename 为空，也使用 typeid 创建分类
        return videos
            .filter { it.typeid > 0 }
            .groupBy { it.typeid }
            .map { (tid, list) ->
                // 优先使用 typename，若为空则使用 typeid 作为名称
                val name = list.firstOrNull { it.typename.isNotEmpty() }?.typename 
                    ?: "分区$tid"
                SpaceVideoCategory(
                    tid = tid,
                    name = name,
                    count = list.size
                )
            }
            .sortedByDescending { it.count }
    }
    
    //  获取合集和系列列表
    private suspend fun fetchSeasonsSeriesList(mid: Long): SeasonsSeriesData? {
        return try {
            val response = spaceApi.getSeasonsSeriesList(mid)
            if (response.code == 0) {
                response.data
            } else {
                android.util.Log.e("SpaceVM", "fetchSeasonsSeriesList failed: ${response.message}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchSeasonsSeriesList error: ${e.message}", e)
            null
        }
    }
    
    //  获取合集内的视频列表
    private suspend fun fetchSeasonArchives(mid: Long, seasonId: Long): List<SeasonArchiveItem>? {
        return try {
            val response = spaceApi.getSeasonArchives(mid, seasonId)
            if (response.code == 0) {
                response.data?.archives
            } else {
                android.util.Log.e("SpaceVM", "fetchSeasonArchives failed: ${response.message}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchSeasonArchives error: ${e.message}", e)
            null
        }
    }
    
    //  获取系列内的视频列表
    private suspend fun fetchSeriesArchives(mid: Long, seriesId: Long): List<SeriesArchiveItem>? {
        return try {
            val response = spaceApi.getSeriesArchives(mid, seriesId)
            if (response.code == 0) {
                response.data?.archives
            } else {
                android.util.Log.e("SpaceVM", "fetchSeriesArchives failed: ${response.message}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchSeriesArchives error: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchCreatedFavoriteFolders(mid: Long): List<FavFolder> {
        return FavoriteRepository
            .getFavFolders(mid)
            .getOrNull()
            .orEmpty()
            .let(::resolveSpaceFavoriteFoldersForDisplay)
    }

    private suspend fun fetchCollectedFavoriteFolders(mid: Long): List<FavFolder> {
        return FavoriteRepository
            .getCollectedFavFolders(mid = mid, pn = 1, ps = 20, platform = "web")
            .getOrNull()
            ?.folders
            .orEmpty()
            .let(::resolveSpaceFavoriteFoldersForDisplay)
    }
    
    // ==========  主页 Tab 数据加载 ==========
    
    /**
     *  加载主页数据（置顶视频 + 公告）
     */
    fun loadSpaceHome() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        
        viewModelScope.launch {
            try {
                _uiState.value = current.markTabLoading(SpaceMainTab.HOME)
                // 命中首屏预取就直接取它的结果（多数情况下已经完成，await 不会挂起，
                // 因此「置 loading」和「落结果」落在同一帧，连 loading 都不会闪一下）。
                val payload = resolveSpaceHomePayload(currentMid)
                val topVideo = payload.topVideo
                val notice = payload.notice
                
                val latest = _uiState.value as? SpaceUiState.Success ?: current
                _uiState.value = latest.copy(
                    topVideo = topVideo,
                    notice = notice ?: "",
                    headerState = buildHeaderState(
                        userInfo = latest.userInfo,
                        relationStat = latest.relationStat,
                        upStat = latest.upStat,
                        topVideo = topVideo,
                        notice = notice ?: "",
                        createdFavorites = latest.createdFavoriteFolders,
                        collectedFavorites = latest.collectedFavoriteFolders
                    )
                ).markTabResult(SpaceMainTab.HOME)
                
                android.util.Log.d("SpaceVM", " loadSpaceHome: topVideo=${topVideo?.title}, notice=${notice?.take(50)}")
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "loadSpaceHome error: ${e.message}", e)
                val latest = _uiState.value as? SpaceUiState.Success ?: return@launch
                _uiState.value = latest.markTabResult(
                    tab = SpaceMainTab.HOME,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    /**
     * 用路由里的 mid 立刻预取 HOME Tab 数据（置顶视频 + 公告）。
     *
     * 只负责把请求提前发出去，不碰 UI 状态——状态仍由 [loadSpaceHome] 统一落盘，
     * 这样 Tab 壳的 loading/hasLoaded 记账逻辑不会被绕过。
     */
    private fun preloadSpaceHome(mid: Long) {
        if (mid <= 0L || mid == homePrefetchMid) return
        homePrefetchMid = mid
        homePrefetchDeferred?.cancel()
        homePrefetchDeferred = viewModelScope.async {
            val topVideoDeferred = async { fetchTopArc(mid) }
            val noticeDeferred = async { fetchNotice(mid) }
            SpaceHomePayload(
                mid = mid,
                topVideo = topVideoDeferred.await(),
                notice = noticeDeferred.await(),
            )
        }
    }

    /**
     * 取 HOME Tab 数据：有同 mid 的预取就复用它，否则当场发请求。
     * 消费后立刻清空，避免刷新时拿到旧快照。
     */
    private suspend fun resolveSpaceHomePayload(mid: Long): SpaceHomePayload {
        val deferred = homePrefetchDeferred
        if (deferred != null && homePrefetchMid == mid) {
            homePrefetchDeferred = null
            homePrefetchMid = 0L
            return deferred.await()
        }
        return coroutineScope {
            val topVideoDeferred = async { fetchTopArc(mid) }
            val noticeDeferred = async { fetchNotice(mid) }
            SpaceHomePayload(
                mid = mid,
                topVideo = topVideoDeferred.await(),
                notice = noticeDeferred.await(),
            )
        }
    }
    
    private suspend fun fetchTopArc(mid: Long): SpaceTopArcData? {
        return try {
            val response = spaceApi.getTopArc(mid)
            if (response.code == 0) {
                response.data
            } else {
                // code != 0 可能是没有置顶视频，不算错误
                android.util.Log.d("SpaceVM", "fetchTopArc: code=${response.code}, msg=${response.message}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchTopArc error: ${e.message}", e)
            null
        }
    }
    
    private suspend fun fetchNotice(mid: Long): String? {
        return try {
            val response = spaceApi.getNotice(mid)
            if (response.code == 0) {
                response.data.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchNotice error: ${e.message}", e)
            null
        }
    }
    
    // ==========  动态 Tab 数据加载 ==========
    
    /**
     *  加载用户动态
     */
    fun loadSpaceDynamic(refresh: Boolean = false) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isLoadingDynamics) return
        if (!refresh && !current.hasMoreDynamics) return
        
        viewModelScope.launch {
            var currentState = current.markTabLoading(SpaceMainTab.DYNAMIC).copy(
                isLoadingDynamics = true,
                lastDynamicLoadFailed = false
            )
            _uiState.value = currentState
            
            try {
                currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                var offset = if (refresh) "" else current.dynamicOffset
                val accumulated = if (refresh) mutableListOf() else currentState.dynamics.toMutableList()
                var hasMore = true
                var pagesFetched = 0
                var failed = false

                while (true) {
                    val response = spaceApi.getSpaceDynamic(currentMid, offset)
                    val responseData = response.data
                    if (response.code != 0 || responseData == null) {
                        android.util.Log.e("SpaceVM", " loadSpaceDynamic failed: code=${response.code}, msg=${response.message}")
                        failed = true
                        hasMore = false
                        break
                    }

                    accumulated += com.android.purebilibili.data.repository.filterGoodsSpaceDynamics(responseData.items)
                    pagesFetched += 1
                    val previousOffset = offset
                    offset = responseData.offset
                    hasMore = responseData.has_more
                    val newlyAccumulatedVisibleCount = if (refresh) {
                        accumulated.size
                    } else {
                        (accumulated.size - currentState.dynamics.size).coerceAtLeast(0)
                    }

                    if (!shouldContinueDynamicFetchAfterFilter(
                            accumulatedVisibleCount = newlyAccumulatedVisibleCount,
                            hasMore = hasMore,
                            previousOffset = previousOffset,
                            nextOffset = offset,
                            pagesFetched = pagesFetched
                        )
                    ) {
                        break
                    }
                }

                val fallbackLocation = if (currentState.headerState.userInfo?.ipLocation.isNullOrBlank()) {
                    accumulated.firstNotNullOfOrNull { item ->
                        item.modules.module_author?.pub_location_text?.takeIf { it.isNotBlank() }
                    }
                } else {
                    null
                }
                val currentUserInfo = currentState.headerState.userInfo ?: currentState.userInfo
                val (nextUserInfo, nextHeaderState) = if (!fallbackLocation.isNullOrBlank()) {
                    val locationTitle = if (fallbackLocation.startsWith("IP属地")) fallbackLocation else "IP属地：$fallbackLocation"
                    val updatedTags = if (currentUserInfo.spaceTags.none { it.type == "location" || it.title.contains("IP") }) {
                        currentUserInfo.spaceTags + SpaceTagItem(type = "location", title = locationTitle)
                    } else {
                        currentUserInfo.spaceTags
                    }
                    val updatedUserInfo = currentUserInfo.copy(
                        ipLocation = fallbackLocation,
                        spaceTags = updatedTags
                    )
                    Pair(updatedUserInfo, currentState.headerState.copy(userInfo = updatedUserInfo))
                } else {
                    Pair(currentState.userInfo, currentState.headerState)
                }

                _uiState.value = currentState.copy(
                    userInfo = nextUserInfo,
                    headerState = nextHeaderState,
                    dynamics = accumulated,
                    dynamicOffset = offset,
                    hasMoreDynamics = hasMore,
                    isLoadingDynamics = false,
                    hasLoadedDynamicsOnce = true,
                    lastDynamicLoadFailed = failed
                ).markTabResult(
                    SpaceMainTab.DYNAMIC,
                    error = if (failed) "加载失败" else null
                )
                android.util.Log.d(
                    "SpaceVM",
                    " loadSpaceDynamic: total=${accumulated.size}, hasMore=$hasMore, pagesFetched=$pagesFetched, failed=$failed"
                )
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "loadSpaceDynamic error: ${e.message}", e)
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                _uiState.value = currentState.copy(
                    isLoadingDynamics = false,
                    hasLoadedDynamicsOnce = true,
                    lastDynamicLoadFailed = true,
                    hasMoreDynamics = false
                ).markTabResult(
                    SpaceMainTab.DYNAMIC,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    fun removeSpaceDynamic(dynamicId: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        _uiState.value = current.copy(
            dynamics = current.dynamics.filterNot { it.id_str == dynamicId }
        )
    }

    fun loadSpaceBangumi(refresh: Boolean = false) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isLoadingBangumi) return
        if (!refresh && !current.hasMoreBangumi) return

        val requestMid = currentMid
        val generation = activeSpaceLoadGeneration
        val page = if (refresh || !current.hasLoadedBangumiOnce) 1 else current.bangumiPage + 1
        _uiState.value = current.copy(isLoadingBangumi = true).markTabLoading(SpaceMainTab.BANGUMI)
        viewModelScope.launch {
            BangumiRepository.getMyFollowBangumi(
                type = MY_FOLLOW_TYPE_BANGUMI,
                page = page,
                pageSize = 20,
                vmid = requestMid
            ).fold(
                onSuccess = { data ->
                    if (!shouldApplySpaceLoadResult(requestMid, currentMid, generation, activeSpaceLoadGeneration)) return@fold
                    val latest = _uiState.value as? SpaceUiState.Success ?: return@fold
                    val incomingItems = data.list.orEmpty()
                    val previousItems = if (refresh) emptyList() else latest.bangumiItems
                    val mergedItems = if (refresh) {
                        incomingItems
                    } else {
                        mergeSpaceBangumiItems(previousItems, incomingItems)
                    }
                    _uiState.value = latest.copy(
                        bangumiItems = mergedItems,
                        bangumiTotal = data.total,
                        bangumiPage = data.pn.coerceAtLeast(page),
                        isLoadingBangumi = false,
                        hasMoreBangumi = shouldContinueSpaceBangumiPagination(
                            previousItemCount = previousItems.size,
                            mergedItemCount = mergedItems.size,
                            incomingItemCount = incomingItems.size,
                            responsePage = data.pn,
                            pageSize = data.ps,
                            total = data.total,
                        ),
                        hasLoadedBangumiOnce = true,
                        lastBangumiLoadFailed = false,
                    ).markTabResult(SpaceMainTab.BANGUMI)
                },
                onFailure = { error ->
                    if (!shouldApplySpaceLoadResult(requestMid, currentMid, generation, activeSpaceLoadGeneration)) return@fold
                    val latest = _uiState.value as? SpaceUiState.Success ?: return@fold
                    _uiState.value = latest.copy(
                        isLoadingBangumi = false,
                        lastBangumiLoadFailed = true,
                        hasLoadedBangumiOnce = true
                    ).markTabResult(
                        SpaceMainTab.BANGUMI,
                        error = error.message ?: "追番加载失败",
                        // An automatic initial request must settle even on privacy/network errors;
                        // otherwise the keyed screen effect immediately retries forever.
                        hasLoaded = true,
                    )
                }
            )
        }
    }

    // ==========  课堂 Tab 数据加载 ==========

    fun loadSpaceCheese(refresh: Boolean = false) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isLoadingCheese) return
        if (!refresh && !current.hasMoreCheese) return

        viewModelScope.launch {
            val page = if (refresh || !current.hasLoadedCheeseOnce) 1 else current.cheesePage + 1
            _uiState.value = current.copy(
                isLoadingCheese = true,
                lastCheeseLoadFailed = false
            ).markTabLoading(SpaceMainTab.CHEESE)

            try {
                val response = spaceApi.getSpaceCheese(
                    mid = currentMid,
                    page = page,
                    pageSize = 30
                )
                val latest = _uiState.value as? SpaceUiState.Success ?: return@launch
                if (response.code == 0 && response.data != null) {
                    val checkedResponseData = requireNotNull(response.data)
                    val incomingItems = checkedResponseData.items
                    val previousItems = if (refresh) emptyList() else latest.cheeseItems
                    val mergedItems = if (refresh) {
                        incomingItems
                    } else {
                        mergeSpaceCheeseItems(previousItems, incomingItems)
                    }
                    val hasNext = checkedResponseData.page?.next == true
                    _uiState.value = latest.copy(
                        cheeseItems = mergedItems,
                        cheesePage = page,
                        isLoadingCheese = false,
                        hasMoreCheese = hasNext && incomingItems.isNotEmpty(),
                        hasLoadedCheeseOnce = true,
                        lastCheeseLoadFailed = false
                    ).markTabResult(SpaceMainTab.CHEESE)
                } else {
                    _uiState.value = latest.copy(
                        isLoadingCheese = false,
                        lastCheeseLoadFailed = true,
                        hasLoadedCheeseOnce = true
                    ).markTabResult(
                        SpaceMainTab.CHEESE,
                        error = response.message.ifBlank { "课堂加载失败" },
                        hasLoaded = true
                    )
                }
            } catch (e: Exception) {
                val latest = _uiState.value as? SpaceUiState.Success ?: return@launch
                _uiState.value = latest.copy(
                    isLoadingCheese = false,
                    lastCheeseLoadFailed = true,
                    hasLoadedCheeseOnce = true
                ).markTabResult(
                    SpaceMainTab.CHEESE,
                    error = e.message ?: "课堂加载失败",
                    hasLoaded = true
                )
            }
        }
    }

    // ==========  Uploads Tab Sub-navigation ==========
    
    fun selectSubTab(tab: SpaceSubTab) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val matched = current.contributionTabs.firstOrNull { it.subTab == tab }
        if (matched != null) {
            selectContributionTab(matched.id)
            return
        }
        updateContributionSelection(
            current = current,
            nextSubTab = tab,
            nextContributionTabId = createSpaceContributionTabId(param = tab.name.lowercase())
        )
    }

    fun selectContributionTab(tabId: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val target = current.contributionTabs.firstOrNull { it.id == tabId } ?: return
        if (current.selectedContributionTabId == target.id && current.selectedSubTab == target.subTab) return
        updateContributionSelection(
            current = current,
            nextSubTab = target.subTab,
            nextContributionTabId = target.id
        )
    }

    private fun updateContributionSelection(
        current: SpaceUiState.Success,
        nextSubTab: SpaceSubTab,
        nextContributionTabId: String
    ) {
        val previousScope = resolveSpaceSearchScope(
            selectedMainTab = current.tabShellState.selectedTab,
            selectedSubTab = current.selectedSubTab
        )
        val nextScope = resolveSpaceSearchScope(
            selectedMainTab = current.tabShellState.selectedTab,
            selectedSubTab = nextSubTab
        )

        _uiState.value = current.copy(
            selectedSubTab = nextSubTab,
            selectedContributionTabId = nextContributionTabId,
            isSearchMode = if (previousScope == nextScope) current.isSearchMode else false,
            searchQuery = if (previousScope == nextScope) current.searchQuery else ""
        )
        if (previousScope == SpaceSearchScope.VIDEO && previousScope != nextScope && currentKeyword.isNotBlank()) {
            clearVideoSearchResults()
        }

        if (nextSubTab == SpaceSubTab.VIDEO || nextSubTab == SpaceSubTab.CHARGING_VIDEO) {
            val specialType = if (nextSubTab == SpaceSubTab.CHARGING_VIDEO) "charging" else null
            if (currentVideoSpecialType != specialType) {
                currentVideoSpecialType = specialType
                refreshVideoSearchResults()
                return
            }
        }
        ensureSelectedContributionContentLoaded()
    }

    fun dismissProfileNotice() {
        _uiState.update { if (it is SpaceUiState.Success) it.copy(profileNotice = null) else it }
    }

    fun dismissMedalWall() {
        _uiState.update { if (it is SpaceUiState.Success) it.copy(medalWall = null) else it }
    }

    fun dismissFollowedUsers() {
        _uiState.update { if (it is SpaceUiState.Success) it.copy(showFollowedUsers = false) else it }
    }

    fun openFollowedUsers() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        _uiState.update { if (it is SpaceUiState.Success) it.copy(showFollowedUsers = true) else it }
        if (!current.hasLoadedFollowedUsers) loadMoreFollowedUsers()
    }

    fun loadMoreFollowedUsers() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isLoadingFollowedUsers || !current.hasMoreFollowedUsers) return
        val mid = currentMid
        val identity = TokenManager.accountIdentity.value
        val page = current.followedUsersPage
        _uiState.update { if (it is SpaceUiState.Success) it.copy(isLoadingFollowedUsers = true) else it }
        viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.SpaceProfileRepository.followedUsers(mid, page)
            if (mid != currentMid || identity != TokenManager.accountIdentity.value) return@launch
            _uiState.update { state ->
                if (state !is SpaceUiState.Success) state else result.fold(
                    onSuccess = { data ->
                        val users = (state.followedUsers + data.list.orEmpty()).distinctBy { it.mid }
                        state.copy(followedUsers = users, followedUsersPage = page + 1,
                            hasMoreFollowedUsers = !data.list.isNullOrEmpty() && users.size < data.total,
                            isLoadingFollowedUsers = false, hasLoadedFollowedUsers = true)
                    },
                    onFailure = { state.copy(isLoadingFollowedUsers = false,
                        profileNotice = SpaceProfileNotice("也关注了 TA", it.message ?: "加载失败")) },
                )
            }
        }
    }

    fun showFollowTime() = runProfileAction("关注时间") {
        com.android.purebilibili.data.repository.SpaceProfileRepository.followTime(currentMid).map { seconds ->
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(seconds * 1000L))
        }
    }

    fun claimVipExperience() = runProfileAction("大会员经验") {
        com.android.purebilibili.data.repository.SpaceProfileRepository.claimVipExperience().map { "大会员经验已领取" }
    }

    fun toggleSpecialFollow() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val add = current.userInfo.relationStatus != -10
        runProfileAction("特别关注") {
            com.android.purebilibili.data.repository.SpaceProfileRepository.specialFollow(current.userInfo.mid, add)
                .onSuccess { refreshRelation() }
                .map { if (add) "已加入特别关注" else "已移除特别关注" }
        }
    }

    private fun runProfileAction(title: String, action: suspend () -> Result<String>) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isProfileActionRunning) return
        val mid = currentMid
        val identity = TokenManager.accountIdentity.value
        _uiState.update { if (it is SpaceUiState.Success) it.copy(isProfileActionRunning = true) else it }
        viewModelScope.launch {
            val result = action()
            if (mid != currentMid || identity != TokenManager.accountIdentity.value) return@launch
            _uiState.update { if (it is SpaceUiState.Success) it.copy(isProfileActionRunning = false,
                profileNotice = SpaceProfileNotice(title, result.getOrElse { error -> error.message ?: "操作失败" })) else it }
        }
    }

    fun loadMedalWall() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isProfileActionRunning) return
        val mid = currentMid
        val identity = TokenManager.accountIdentity.value
        _uiState.update { if (it is SpaceUiState.Success) it.copy(isProfileActionRunning = true) else it }
        viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.SpaceProfileRepository.medalWall(mid)
            if (mid != currentMid || identity != TokenManager.accountIdentity.value) return@launch
            _uiState.update { if (it is SpaceUiState.Success) it.copy(isProfileActionRunning = false,
                medalWall = result.getOrNull(),
                profileNotice = result.exceptionOrNull()?.let { error -> SpaceProfileNotice("勋章墙", error.message ?: "加载失败") }) else it }
        }
    }

    fun toggleReservation(sid: Long) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val item = current.reservations.firstOrNull { it.sid == sid } ?: return
        if (sid in current.reservationBusyIds) return
        val mid = currentMid
        val identity = TokenManager.accountIdentity.value
        _uiState.update { if (it is SpaceUiState.Success) it.copy(reservationBusyIds = it.reservationBusyIds + sid) else it }
        viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.SpaceProfileRepository.reserve(sid, cancel = item.isFollow == 1)
            if (mid != currentMid || identity != TokenManager.accountIdentity.value) return@launch
            _uiState.update { if (it is SpaceUiState.Success) it.copy(
                reservationBusyIds = it.reservationBusyIds - sid,
                reservations = if (result.isFailure) it.reservations else it.reservations.map { reservation ->
                    if (reservation.sid != sid) reservation else reservation.copy(isFollow = if (item.isFollow == 1) 0 else 1,
                        total = (reservation.total + if (item.isFollow == 1) -1 else 1).coerceAtLeast(0))
                },
                profileNotice = result.exceptionOrNull()?.let { error -> SpaceProfileNotice("预约", error.message ?: "操作失败") },
            ) else it }
        }
    }

    fun selectOpusFilter(meta: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.opusFilter == meta) return
        _uiState.update { if (it is SpaceUiState.Success) it.copy(opusFilter = meta, opusItems = emptyList(), opusCollections = emptyList(), opusOffset = "", hasMoreOpus = true) else it }
        loadSpaceOpus(refresh = true)
    }

    fun loadSpaceOpus(refresh: Boolean = false) {
        ensureContributionAccount()
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if ((current.isLoadingOpus || !current.hasMoreOpus) && !refresh) return
        activeSpaceOpusJob?.cancel()
        val mid = currentMid
        val generation = activeSpaceLoadGeneration
        val identity = TokenManager.accountIdentity.value
        val offset = if (refresh) "" else current.opusOffset
        val filter = current.opusFilter
        _uiState.value = current.copy(isLoadingOpus = true, opusError = null)
        activeSpaceOpusJob = viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.SpaceProfileRepository.opus(mid, offset, filter)
            if (identity != TokenManager.accountIdentity.value ||
                !shouldApplySpaceLoadResult(mid, currentMid, generation, activeSpaceLoadGeneration)) return@launch
            _uiState.update { state ->
                if (state !is SpaceUiState.Success) state else result.fold(
                    onSuccess = { data -> state.copy(
                        opusItems = ((if (refresh) emptyList() else state.opusItems) + data.items).distinctBy { it.id },
                        opusCollections = if (refresh || offset.isBlank()) data.collections else state.opusCollections,
                        opusOffset = data.next, hasMoreOpus = data.next.isNotBlank(),
                        hasLoadedOpus = true, isLoadingOpus = false, opusError = null,
                    ) },
                    onFailure = { state.copy(isLoadingOpus = false, opusError = it.message ?: "图文加载失败") },
                )
            }
        }
    }

    fun loadSpaceComics() {
        ensureContributionAccount()
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.isLoadingComics) return
        val mid = currentMid
        val generation = activeSpaceLoadGeneration
        _uiState.value = current.copy(isLoadingComics = true, comicError = null)
        viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.SpaceProfileRepository.comics(mid)
            if (!shouldApplySpaceLoadResult(mid, currentMid, generation, activeSpaceLoadGeneration)) return@launch
            _uiState.update { state ->
                if (state !is SpaceUiState.Success) state else state.copy(
                    comics = result.getOrNull()?.item ?: state.comics,
                    isLoadingComics = false,
                    hasLoadedComics = result.isSuccess,
                    comicError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun ensureSelectedContributionContentLoaded() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.tabShellState.selectedTab != SpaceMainTab.CONTRIBUTION) return

        val selectedContributionTab = resolveSelectedContributionTab(
            tabs = current.contributionTabs,
            selectedTabId = current.selectedContributionTabId,
            selectedSubTab = current.selectedSubTab
        )

        when (selectedContributionTab.subTab) {
            SpaceSubTab.VIDEO,
            SpaceSubTab.CHARGING_VIDEO -> {
                if (current.videos.isEmpty() && !current.isLoadingMore) {
                    refreshVideoSearchResults()
                }
            }
            SpaceSubTab.AUDIO -> {
                // 聚合接口只给 3 条音频预览但 totalAudios 是全量数（如 15）：
                // 预览不足以填满时必须用 song/upper 补拉全量，否则列表与计数不符。
                if ((current.audios.isEmpty() || current.audios.size < current.totalAudios) &&
                    !current.isLoadingAudios
                ) {
                    loadSpaceAudios(refresh = true)
                }
            }
            SpaceSubTab.OPUS -> {
                if (!current.hasLoadedOpus && !current.isLoadingOpus) loadSpaceOpus()
            }
            SpaceSubTab.ARTICLE -> {
                if (current.articles.isEmpty() && !current.isLoadingArticles) {
                    loadSpaceArticles(refresh = true)
                }
            }
            SpaceSubTab.SEASON_VIDEO -> {
                val seasonId = selectedContributionTab.seasonId
                val season = current.seasons.firstOrNull { it.meta.season_id == seasonId }
                val currentArchives = current.seasonArchives[seasonId].orEmpty()
                val expectedCount = (season?.meta?.total ?: 1).coerceAtLeast(1)
                if (seasonId > 0L && currentArchives.size < expectedCount) {
                    loadSelectedSeasonArchives(seasonId)
                }
            }
            SpaceSubTab.SERIES -> {
                val seriesId = selectedContributionTab.seriesId
                val series = current.series.firstOrNull { it.meta.series_id == seriesId }
                val currentArchives = current.seriesArchives[seriesId].orEmpty()
                val expectedCount = (series?.meta?.total ?: 1).coerceAtLeast(1)
                if (seriesId > 0L && currentArchives.size < expectedCount) {
                    loadSelectedSeriesArchives(seriesId)
                }
            }
            SpaceSubTab.COMIC -> if (!current.hasLoadedComics && !current.isLoadingComics) loadSpaceComics()
            else -> Unit
        }
    }

    private fun loadSelectedSeasonArchives(seasonId: Long) {
        viewModelScope.launch {
            val requestMid = currentMid
            val archives = fetchSeasonArchives(requestMid, seasonId).orEmpty()
            if (archives.isEmpty() || requestMid != currentMid) return@launch
            val latest = _uiState.value as? SpaceUiState.Success ?: return@launch
            _uiState.value = latest.copy(
                seasonArchives = latest.seasonArchives + (seasonId to archives)
            )
        }
    }

    private fun loadSelectedSeriesArchives(seriesId: Long) {
        viewModelScope.launch {
            val requestMid = currentMid
            val archives = fetchSeriesArchives(requestMid, seriesId).orEmpty()
            if (archives.isEmpty() || requestMid != currentMid) return@launch
            val latest = _uiState.value as? SpaceUiState.Success ?: return@launch
            _uiState.value = latest.copy(
                seriesArchives = latest.seriesArchives + (seriesId to archives)
            )
        }
    }

    fun setSearchMode(enabled: Boolean) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val scope = resolveSpaceSearchScope(
            selectedMainTab = current.tabShellState.selectedTab,
            selectedSubTab = current.selectedSubTab
        )
        if (scope == SpaceSearchScope.NONE) return

        if (enabled) {
            _uiState.value = current.copy(isSearchMode = true)
            return
        }

        activeSpaceSearchJob?.cancel()
        _uiState.value = current.copy(
            isSearchMode = false,
            searchQuery = ""
        )
        if (scope == SpaceSearchScope.VIDEO && currentKeyword.isNotBlank()) {
            clearVideoSearchResults()
        } else {
            currentKeyword = ""
        }
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val scope = resolveSpaceSearchScope(
            selectedMainTab = current.tabShellState.selectedTab,
            selectedSubTab = current.selectedSubTab
        )
        if (scope == SpaceSearchScope.NONE) return

        _uiState.value = current.copy(
            isSearchMode = true,
            searchQuery = query
        )

        activeSpaceSearchJob?.cancel()
        when (scope) {
            SpaceSearchScope.VIDEO -> {
                activeSpaceSearchJob = viewModelScope.launch {
                    delay(SPACE_DYNAMIC_SEARCH_DEBOUNCE_MS)
                    currentKeyword = query.trim()
                    refreshVideoSearchResults()
                }
            }
            SpaceSearchScope.DYNAMIC -> {
                activeSpaceSearchJob = viewModelScope.launch {
                    delay(SPACE_DYNAMIC_SEARCH_DEBOUNCE_MS)
                    prefetchSpaceDynamicsForSearch(query)
                }
            }
            SpaceSearchScope.NONE -> Unit
        }
    }

    fun locateLastWatchedVideo() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        locateVideo(current.lastWatchedVideo)
    }

    fun locatePlayedVideoContribution(targetBvid: String) {
        val normalizedBvid = targetBvid.trim()
        if (normalizedBvid.isBlank()) return
        // The navigation already carries the exact BVID. Searching by the history title is
        // ambiguous and previously replaced the contribution list with an empty result.
        openVideoContributionPosition(normalizedBvid)
    }

    private fun openVideoContributionPosition(targetBvid: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val videoTab = current.contributionTabs.firstOrNull { it.subTab == SpaceSubTab.VIDEO }
        if (videoTab == null) {
            _uiState.value = current.copy(locateMessage = "该空间没有可定位的视频投稿")
            return
        }

        currentTid = 0
        currentOrder = VideoSortOrder.PUBDATE
        currentKeyword = ""
        _selectedMainTab.value = mainTabToTabIndex(SpaceMainTab.CONTRIBUTION)
        savedStateHandle[KEY_SELECTED_MAIN_TAB] = _selectedMainTab.value
        _uiState.value = current.copy(
            selectedTid = 0,
            sortOrder = VideoSortOrder.PUBDATE,
            selectedSubTab = SpaceSubTab.VIDEO,
            selectedContributionTabId = videoTab.id,
            isSearchMode = false,
            searchQuery = "",
            pendingLocateBvid = targetBvid,
            locateMessage = null,
            lastVideoPageLoadFailed = false,
            tabShellState = current.tabShellState.withSelectedTab(SpaceMainTab.CONTRIBUTION)
        )
        refreshVideoSearchResults()
    }

    private fun locateVideo(target: SpaceWatchProgress?) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val targetBvid = target?.bvid?.trim().orEmpty()
        if (targetBvid.isBlank()) {
            _uiState.value = current.copy(locateMessage = "未找到可定位的最近观看视频")
            return
        }
        openVideoContributionPosition(targetBvid)
    }

    fun consumePendingLocateBvid(bvid: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.pendingLocateBvid == bvid) {
            _uiState.value = current.copy(pendingLocateBvid = null)
        }
    }

    fun reportPendingLocateBvidMissing(bvid: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.pendingLocateBvid == bvid) {
            _uiState.value = current.copy(
                pendingLocateBvid = null,
                locateMessage = "未在该 UP 的投稿中找到该视频",
            )
        }
    }

    fun reportPendingLocateBvidLoadFailed(bvid: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.pendingLocateBvid == bvid) {
            _uiState.value = current.copy(
                pendingLocateBvid = null,
                locateMessage = "加载投稿失败，请稍后重试定位",
            )
        }
    }

    fun consumeLocateMessage(message: String) {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (current.locateMessage == message) {
            _uiState.value = current.copy(locateMessage = null)
        }
    }

    /**
     * When local filter has no hits, auto-pull more dynamic pages and re-filter.
     * Stops on first match, feed end, or [SPACE_DYNAMIC_SEARCH_PREFETCH_PAGE_LIMIT].
     */
    private suspend fun prefetchSpaceDynamicsForSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return

        var pagesFetchedForSearch = 0
        while (true) {
            val state = _uiState.value as? SpaceUiState.Success ?: return
            if (state.searchQuery.trim() != normalizedQuery) return
            if (state.tabShellState.selectedTab != SpaceMainTab.DYNAMIC) return

            val matchCount = filterSpaceDynamicItemsByQuery(state.dynamics, normalizedQuery).size
            if (!shouldPrefetchMoreSpaceDynamicsForSearch(
                    query = normalizedQuery,
                    matchCount = matchCount,
                    hasMore = state.hasMoreDynamics,
                    pagesFetchedForSearch = pagesFetchedForSearch
                )
            ) {
                return
            }

            if (state.isLoadingDynamics) {
                // Wait for an in-flight list load (scroll / initial) instead of racing.
                delay(80L)
                continue
            }

            val fetched = fetchNextSpaceDynamicPage()
            if (!fetched) return
            pagesFetchedForSearch += 1
        }
    }

    /**
     * Loads one additional space-dynamic page into Success state.
     * @return true if a network page was applied; false on failure / no more / cancelled.
     */
    private suspend fun fetchNextSpaceDynamicPage(): Boolean {
        val current = _uiState.value as? SpaceUiState.Success ?: return false
        if (current.isLoadingDynamics || !current.hasMoreDynamics) return false

        _uiState.value = current.markTabLoading(SpaceMainTab.DYNAMIC).copy(
            isLoadingDynamics = true,
            lastDynamicLoadFailed = false
        )

        return try {
            val stateBefore = _uiState.value as? SpaceUiState.Success ?: return false
            val response = spaceApi.getSpaceDynamic(currentMid, stateBefore.dynamicOffset)
            val responseData = response.data
            if (response.code != 0 || responseData == null) {
                val failed = _uiState.value as? SpaceUiState.Success ?: return false
                _uiState.value = failed.copy(
                    isLoadingDynamics = false,
                    lastDynamicLoadFailed = true
                ).markTabResult(SpaceMainTab.DYNAMIC, error = "加载失败")
                return false
            }

            val latest = _uiState.value as? SpaceUiState.Success ?: return false
            val merged = mergeSpaceDynamicPages(existing = latest.dynamics, incoming = com.android.purebilibili.data.repository.filterGoodsSpaceDynamics(responseData.items))
            _uiState.value = latest.copy(
                dynamics = merged,
                dynamicOffset = responseData.offset,
                hasMoreDynamics = responseData.has_more,
                isLoadingDynamics = false,
                hasLoadedDynamicsOnce = true,
                lastDynamicLoadFailed = false
            ).markTabResult(SpaceMainTab.DYNAMIC, error = null)
            true
        } catch (e: CancellationException) {
            val cancelled = _uiState.value as? SpaceUiState.Success
            if (cancelled != null) {
                _uiState.value = cancelled.copy(isLoadingDynamics = false)
            }
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchNextSpaceDynamicPage error: ${e.message}", e)
            val failed = _uiState.value as? SpaceUiState.Success ?: return false
            _uiState.value = failed.copy(
                isLoadingDynamics = false,
                lastDynamicLoadFailed = true
            ).markTabResult(SpaceMainTab.DYNAMIC, error = e.message ?: "加载失败")
            false
        }
    }
    
    private fun ensureContributionAccount() {
        val identity = TokenManager.accountIdentity.value
        if (identity == contributionAccount) return
        contributionAccount = identity
        activeSpaceAudioJob?.cancel()
        activeSpaceArticleJob?.cancel()
        activeSpaceOpusJob?.cancel()
        audioPagingSession = null
        articlePagingSession = null
        videoPagingJob?.cancel()
        videoPagingSession = null
        _uiState.update { state ->
            (state as? SpaceUiState.Success)?.copy(isLoadingAudios = false, isLoadingArticles = false, isLoadingMore = false,
                opusItems = emptyList(), opusCollections = emptyList(), opusOffset = "", opusFilter = "all", hasLoadedOpus = false, hasMoreOpus = true, isLoadingOpus = false,
                comics = emptyList(), hasLoadedComics = false, isLoadingComics = false, comicError = null,
                showFollowedUsers = false, followedUsers = emptyList(), followedUsersPage = 1, hasMoreFollowedUsers = true,
                hasLoadedFollowedUsers = false, isLoadingFollowedUsers = false,
                reservationBusyIds = emptySet(), profileNotice = null, isProfileActionRunning = false, medalWall = null) ?: state
        }
    }

    fun loadSpaceAudios(refresh: Boolean = false) {
        ensureContributionAccount()
        if (_uiState.value !is SpaceUiState.Success) return
        if (!refresh && audioPagingSession != null) {
            audioPagingSession?.appendOrRetry()
            return
        }
        activeSpaceAudioJob?.cancel()
        val requestMid = currentMid
        val generation = activeSpaceLoadGeneration
        val identity = contributionAccount
        lateinit var session: ForwardPagingSession<Int, SpaceContributionPage<SpaceAudioItem>>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                spaceContributionPagingSource<SpaceAudioItem>(itemKey = { it.id }) { number ->
                    val response = checkNotNull(fetchSpaceAudioList(requestMid, number)) { "音频加载失败" }
                    check(response.code == 0) { "音频加载失败：${response.code}" }
                    val data = checkNotNull(response.data) { "音频内容响应不完整" }
                    val items = data.data.orEmpty()
                    val more = if (data.pageCount > 0) number < data.pageCount else
                        (number.toLong() * SPACE_AUDIO_PAGE_SIZE_CONST < data.totalSize || items.size >= SPACE_AUDIO_PAGE_SIZE_CONST)
                    SpaceContributionPage(number, items, data.totalSize, more)
                }
            },
            onChanged = { pages, states ->
                if (audioPagingSession === session && identity == TokenManager.accountIdentity.value &&
                    shouldApplySpaceLoadResult(requestMid, currentMid, generation, activeSpaceLoadGeneration) && states != null
                ) {
                    val failure = (states.refresh as? LoadState.Error)?.error ?: (states.append as? LoadState.Error)?.error
                    val ready = states.refresh is LoadState.NotLoading && pages.isNotEmpty()
                    val loading = states.refresh is LoadState.Loading || states.append is LoadState.Loading
                    _uiState.update { state ->
                        val latest = state as? SpaceUiState.Success
                        if (latest == null) state else {
                            val items = if (ready) pages.flatMap { it.items } else latest.audios
                            val next = latest.copy(
                                audios = items,
                                totalAudios = if (ready) maxOf(pages.last().total, items.size) else latest.totalAudios,
                                audioPage = pages.lastOrNull()?.number ?: latest.audioPage,
                                hasMoreAudios = !states.append.endOfPaginationReached,
                                isLoadingAudios = loading,
                            )
                            if (loading) next.markTabLoading(SpaceMainTab.CONTRIBUTION) else
                                next.markTabResult(SpaceMainTab.CONTRIBUTION, error = failure?.let { it.message ?: "音频加载失败" })
                        }
                    }
                }
            }
        )
        audioPagingSession = session
        activeSpaceAudioJob = viewModelScope.launch { session.run() }
    }

    fun loadSpaceArticles(refresh: Boolean = false) = startSpaceArticles(refresh, silent = false)

    private fun startSpaceArticles(refresh: Boolean, silent: Boolean) {
        ensureContributionAccount()
        if (_uiState.value !is SpaceUiState.Success) return
        if (!silent) articlePagingReportsErrors = true
        if (!refresh && articlePagingSession != null) {
            articlePagingSession?.appendOrRetry()
            return
        }
        activeSpaceArticleJob?.cancel()
        val requestMid = currentMid
        val generation = activeSpaceLoadGeneration
        val identity = contributionAccount
        articlePagingReportsErrors = !silent
        lateinit var session: ForwardPagingSession<Int, SpaceContributionPage<SpaceArticleItem>>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                spaceContributionPagingSource<SpaceArticleItem>(itemKey = { it.id }) { number ->
                    val response = checkNotNull(fetchSpaceArticleList(requestMid, number)) { "专栏加载失败" }
                    check(response.code == 0) { "专栏加载失败：${response.code}" }
                    val data = checkNotNull(response.data) { "专栏内容响应不完整" }
                    SpaceContributionPage(number, data.lists, data.total, data.has_more, data.offset)
                }
            },
            onChanged = { pages, states ->
                if (articlePagingSession === session && identity == TokenManager.accountIdentity.value &&
                    shouldApplySpaceLoadResult(requestMid, currentMid, generation, activeSpaceLoadGeneration) && states != null
                ) {
                    val failure = (states.refresh as? LoadState.Error)?.error ?: (states.append as? LoadState.Error)?.error
                    val ready = states.refresh is LoadState.NotLoading && pages.isNotEmpty()
                    val loading = states.refresh is LoadState.Loading || states.append is LoadState.Loading
                    _uiState.update { state ->
                        val latest = state as? SpaceUiState.Success
                        if (latest == null) state else {
                            val items = if (ready) pages.flatMap { it.items } else latest.articles
                            val next = latest.copy(
                                articles = items,
                                totalArticles = if (ready) maxOf(pages.last().total, items.size) else latest.totalArticles,
                                articlePage = pages.lastOrNull()?.number ?: latest.articlePage,
                                hasMoreArticles = !states.append.endOfPaginationReached,
                                isLoadingArticles = loading,
                                articleOffset = pages.lastOrNull()?.offset ?: latest.articleOffset,
                                contributionTabs = ensureSpaceContributionTabsForAvailableContent(latest.contributionTabs, hasArticles = items.isNotEmpty()),
                            )
                            if (!articlePagingReportsErrors) next else
                            if (loading) next.markTabLoading(SpaceMainTab.CONTRIBUTION) else
                                next.markTabResult(SpaceMainTab.CONTRIBUTION, error = failure?.let { it.message ?: "专栏加载失败" })
                        }
                    }
                }
            }
        )
        articlePagingSession = session
        activeSpaceArticleJob = viewModelScope.launch { session.run() }
    }

    private fun probeSpaceArticles(mid: Long, requestGeneration: Long) {
        if (articlePagingSession != null) return
        if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) return
        startSpaceArticles(refresh = true, silent = true)
    }

    private fun probeSpaceCheese(mid: Long, requestGeneration: Long) {
        activeSpaceCheeseJob?.cancel()
        activeSpaceCheeseJob = viewModelScope.launch {
            try {
                val response = spaceApi.getSpaceCheese(mid = mid, page = 1, pageSize = 30)
                if (!shouldApplySpaceLoadResult(mid, currentMid, requestGeneration, activeSpaceLoadGeneration)) {
                    return@launch
                }
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                val items = response.takeIf { it.code == 0 }?.data?.items.orEmpty()
                if (items.isEmpty()) return@launch

                val hasNext = response.data?.page?.next == true
                val updatedMainTabs = if (currentState.mainTabs.none { it.tab == SpaceMainTab.CHEESE }) {
                    currentState.mainTabs + SpaceMainTabItem(SpaceMainTab.CHEESE, "课堂")
                } else {
                    currentState.mainTabs
                }
                _uiState.value = currentState.copy(
                    hasCheeseTab = true,
                    cheeseItems = items,
                    cheesePage = 1,
                    hasMoreCheese = hasNext && items.isNotEmpty(),
                    hasLoadedCheeseOnce = true,
                    mainTabs = updatedMainTabs
                ).markTabResult(SpaceMainTab.CHEESE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SpaceVM", "probeCheese error: ${e.message}")
            }
        }
    }
    
    private suspend fun fetchSpaceAudioList(uid: Long, page: Int): SpaceAudioResponse? {
        return try {
            spaceApi.getSpaceAudioList(uid = uid, pn = page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchAudio error: ${e.message}")
            null
        }
    }

    private suspend fun fetchSpaceArticleList(mid: Long, page: Int): SpaceArticleResponse? {
        return try {
            if (!ensureWbiKeysLoaded()) return null
            val params = WbiUtils.sign(
                mapOf(
                    "mid" to mid.toString(),
                    "pn" to page.toString(),
                    "ps" to "30",
                    "sort" to "publish_time"
                ),
                cachedImgKey,
                cachedSubKey
            )
            spaceApi.getSpaceArticleList(params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SpaceVM", "fetchArticle error: ${e.message}")
            null
        }
    }
    // ==========  关注 / 取关逻辑 ==========
    
    fun refreshRelation() {
        loadSpaceFollowStatus(currentMid, activeSpaceLoadGeneration)
    }

    fun toggleFollow() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        if (followToggleInFlight) return
        val isFollowing = current.userInfo.isFollowed
        val mid = current.userInfo.mid

        followToggleInFlight = true
        viewModelScope.launch {
            // 1. 乐观更新 UI
            val nextFollowed = !isFollowing
            val nextRelationStatus = if (nextFollowed) 2 else 0
            val newUserInfo = current.userInfo.copy(
                isFollowed = nextFollowed,
                relationStatus = nextRelationStatus
            )
            _uiState.value = current.copy(
                userInfo = newUserInfo,
                headerState = current.headerState.copy(userInfo = newUserInfo)
            )
            
            try {
                // 2. 调用统一仓库逻辑
                val result = ActionRepository.followUser(mid = mid, follow = nextFollowed)
                if (result.isFailure) {
                    _uiState.value = current.copy(
                        userInfo = current.userInfo,
                        headerState = current.headerState.copy(userInfo = current.userInfo)
                    )
                    com.android.purebilibili.core.util.Logger.e(
                        "SpaceVM",
                        "toggleFollow failed: ${result.exceptionOrNull()?.message}"
                    )
                } else {
                    val latestState = _uiState.value as? SpaceUiState.Success
                    if (latestState != null) {
                        _uiState.value = latestState.copy(
                            userInfo = latestState.userInfo.copy(
                                isFollowed = nextFollowed,
                                relationStatus = nextRelationStatus
                            ),
                            headerState = latestState.headerState.copy(
                                userInfo = latestState.userInfo.copy(
                                    isFollowed = nextFollowed,
                                    relationStatus = nextRelationStatus
                                )
                            )
                        )
                    }

                    if (nextFollowed) {
                        showFollowGroupDialogForUser(mid)
                    }

                    val relationStat = fetchRelationStat(mid)
                    if (relationStat != null) {
                        val currentState = _uiState.value as? SpaceUiState.Success
                        if (currentState != null) {
                            _uiState.value = currentState.copy(
                                relationStat = relationStat,
                                headerState = currentState.headerState.copy(relationStat = relationStat)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // 失败回滚
                com.android.purebilibili.core.util.Logger.e("SpaceVM", "modifyRelation error: ${e.message}", e)
                _uiState.value = current.copy(
                    userInfo = current.userInfo,
                    headerState = current.headerState.copy(userInfo = current.userInfo)
                ) // Revert
            } finally {
                followToggleInFlight = false
            }
        }
    }

    fun showFollowGroupDialogForUser(mid: Long) {
        if (mid <= 0L) return
        followGroupTargetMid = mid
        _followGroupDialogVisible.value = true
        loadFollowGroupsForTarget()
    }

    fun dismissFollowGroupDialog() {
        _followGroupDialogVisible.value = false
    }

    fun toggleFollowGroupSelection(tagId: Long) {
        if (tagId == 0L) return
        _followGroupSelectedTagIds.update { selected ->
            if (selected.contains(tagId)) selected - tagId else selected + tagId
        }
    }

    fun saveFollowGroupSelection() {
        if (_isSavingFollowGroups.value || followGroupTargetMid <= 0L) return
        val selected = _followGroupSelectedTagIds.value
        viewModelScope.launch {
            _isSavingFollowGroups.value = true
            ActionRepository
                .overwriteFollowGroupIds(
                    targetMids = setOf(followGroupTargetMid),
                    selectedTagIds = selected
                )
                .onSuccess {
                    dismissFollowGroupDialog()
                }
                .onFailure { e ->
                    com.android.purebilibili.core.util.Logger.e(
                        "SpaceVM",
                        "saveFollowGroupSelection failed: ${e.message}"
                    )
                }
            _isSavingFollowGroups.value = false
        }
    }

    private fun loadFollowGroupsForTarget() {
        val targetMid = followGroupTargetMid
        if (targetMid <= 0L) return
        viewModelScope.launch {
            _isFollowGroupsLoading.value = true
            val tagsResult = ActionRepository.getFollowGroupTags()
            val userGroupResult = ActionRepository.getUserFollowGroupIds(targetMid)

            tagsResult.onSuccess { tags ->
                _followGroupTags.value = tags.filter { it.tagid != 0L }
            }.onFailure {
                _followGroupTags.value = emptyList()
            }

            userGroupResult.onSuccess { groupIds ->
                _followGroupSelectedTagIds.value = groupIds.filterNot { it == 0L }.toSet()
            }.onFailure {
                _followGroupSelectedTagIds.value = emptySet()
            }

            _isFollowGroupsLoading.value = false
        }
    }

    private fun tabIndexToMainTab(index: Int): SpaceMainTab {
        return com.android.purebilibili.feature.space.tabIndexToMainTab(index)
    }

    private fun SpaceUiState.Success.markTabLoading(tab: SpaceMainTab): SpaceUiState.Success {
        return copy(
            tabShellState = tabShellState.withUpdatedTab(tab) {
                it.copy(isLoading = true, error = null)
            }
        )
    }

    private fun SpaceUiState.Success.markTabResult(
        tab: SpaceMainTab,
        error: String? = null,
        hasLoaded: Boolean = true
    ): SpaceUiState.Success {
        return copy(
            tabShellState = tabShellState.withUpdatedTab(tab) {
                it.copy(isLoading = false, error = error, hasLoaded = hasLoaded)
            }
        )
    }

    private fun clearVideoSearchResults() {
        currentKeyword = ""
        refreshVideoSearchResults()
    }

    private fun mergeSpaceBangumiItems(
        existing: List<FollowBangumiItem>,
        incoming: List<FollowBangumiItem>
    ): List<FollowBangumiItem> {
        val seen = LinkedHashSet<Long>()
        val merged = ArrayList<FollowBangumiItem>(existing.size + incoming.size)
        fun addAll(items: List<FollowBangumiItem>) {
            items.forEach { item ->
                val key = item.seasonId.takeIf { it > 0L } ?: item.mediaId
                if (key > 0L && seen.add(key)) {
                    merged += item
                }
            }
        }
        addAll(existing)
        addAll(incoming)
        return merged
    }

    private fun mergeSpaceCheeseItems(
        existing: List<SpaceCheeseItem>,
        incoming: List<SpaceCheeseItem>
    ): List<SpaceCheeseItem> {
        val seen = LinkedHashSet<Long>()
        val merged = ArrayList<SpaceCheeseItem>(existing.size + incoming.size)
        fun addAll(items: List<SpaceCheeseItem>) {
            items.forEach { item ->
                if (item.seasonId > 0L && seen.add(item.seasonId)) {
                    merged += item
                } else if (item.seasonId <= 0L) {
                    merged += item
                }
            }
        }
        addAll(existing)
        addAll(incoming)
        return merged
    }

    private fun refreshVideoSearchResults() {
        val current = _uiState.value as? SpaceUiState.Success ?: return
        val requestGeneration = beginVideoListRequest()
        val requestTid = currentTid
        val requestOrder = currentOrder
        val requestKeyword = currentKeyword
        val requestSpecialType = currentVideoSpecialType

        activeVideoListJob = viewModelScope.launch {
            val loadingState = (_uiState.value as? SpaceUiState.Success ?: current).copy(
                videos = emptyList(),
                isLoadingMore = true,
                hasMoreVideos = true
            )
            _uiState.value = loadingState

            try {
                if (!ensureWbiKeysLoaded()) {
                    if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, requestTid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                        return@launch
                    }
                    val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                    _uiState.value = currentState.copy(
                        isLoadingMore = false,
                        hasMoreVideos = false
                    )
                    return@launch
                }
                val result = fetchInitialSpaceVideos(
                    mid = currentMid,
                    imgKey = cachedImgKey,
                    subKey = cachedSubKey,
                    tid = requestTid,
                    order = requestOrder,
                    keyword = requestKeyword,
                    specialType = requestSpecialType
                )
                if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, requestTid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                if (result != null) {
                    currentPage = result.resolvedPage
                    _uiState.value = currentState.copy(
                        videos = result.data.list.vlist,
                        totalVideos = result.data.page.count,
                        hasMoreVideos = resolveNextSpaceVideoPage(
                            order = requestOrder,
                            currentPage = currentPage,
                            totalCount = result.data.page.count,
                            pageSize = pageSize
                        ) != null,
                        isLoadingMore = false
                    )
                } else {
                    _uiState.value = currentState.copy(
                        isLoadingMore = false,
                        hasMoreVideos = false
                    )
                }
            } catch (e: Exception) {
                if (!shouldApplySpaceVideoResult(currentMid, currentMid, requestGeneration, activeVideoListGeneration, requestTid, currentTid, requestOrder, currentOrder, requestKeyword, currentKeyword)) {
                    return@launch
                }
                val currentState = _uiState.value as? SpaceUiState.Success ?: return@launch
                _uiState.value = currentState.copy(
                    isLoadingMore = false
                )
            }
        }
    }


    private companion object {
        const val KEY_SELECTED_MAIN_TAB = "space_selected_main_tab"
    }

}

/** song/upper 接口请求页大小（ApiClient.getSpaceAudioList ps=30）。 */
private const val SPACE_AUDIO_PAGE_SIZE_CONST = 30
