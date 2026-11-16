package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.util.appendDistinctByKey
import com.android.purebilibili.core.util.prependDistinctByKey
import com.android.purebilibili.data.model.response.DynamicItem
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.max

// 头像、名称基线及字体下行（如英文 y）都需要落在裁切边界内。
internal const val DynamicHorizontalUserListReservedHeightDp = 96
internal const val DynamicHeaderCollapseTriggerPx = 0

internal fun normalizeDynamicNotInterestedIds(
    ids: Iterable<String>,
    maxSize: Int = 500,
): Set<String> = ids
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .takeLast(maxSize.coerceAtLeast(0))
    .toSet()

internal data class DynamicPagePresentation(
    val items: List<DynamicItem>,
    val isLoading: Boolean,
    val error: String?,
    val hasMore: Boolean,
    val isSelectedUserFeed: Boolean,
    val incrementalRefreshBoundaryKey: String?,
    val incrementalPrependedCount: Int
)

internal fun resolveDynamicPagePresentation(
    state: DynamicUiState,
    logicalTab: Int,
    selectedUserId: Long?
): DynamicPagePresentation {
    val isSelectedUserFeed = shouldUseSelectedUserDynamicFeed(logicalTab, selectedUserId)
    if (isSelectedUserFeed) {
        val items = filterSelectedUserDynamicItems(
            items = resolveSelectedUserVisibleItems(
                timelineItems = state.timelinePage(resolveDynamicFeedRequestType(logicalTab)).items,
                remoteUserItems = state.userItems,
                selectedUid = selectedUserId
            ),
            logicalTab = logicalTab
        )
            .filter { it.visible }
            .filterNot { it.id_str in state.tempBannedDynamicIds }
            .distinctBy { it.id_str }
        return DynamicPagePresentation(
            items = items,
            isLoading = state.userIsLoading,
            error = state.userError,
            // Local timeline matches are provisional. Keep pagination available while
            // the authoritative user-space feed reports more pages.
            hasMore = state.hasUserMore,
            isSelectedUserFeed = true,
            incrementalRefreshBoundaryKey = null,
            incrementalPrependedCount = 0
        )
    }

    val page = state.timelinePage(resolveDynamicFeedRequestType(logicalTab))
    val items = when (logicalTab) {
        1 -> page.items.filter(::shouldIncludeDynamicItemInVideoTab)
        2 -> page.items.filter(::shouldIncludeDynamicItemInPgcTab)
        3 -> page.items.filter(::shouldIncludeDynamicItemInArticleTab)
        else -> page.items
    }
        .filter { it.visible }
        .filterNot { it.id_str in state.tempBannedDynamicIds }
        .distinctBy { it.id_str }
    return DynamicPagePresentation(
        items = items,
        isLoading = page.isLoading,
        error = page.error,
        hasMore = page.hasMore,
        isSelectedUserFeed = false,
        incrementalRefreshBoundaryKey = page.incrementalRefreshBoundaryKey,
        incrementalPrependedCount = page.incrementalPrependedCount
    )
}

internal fun resolveDynamicListTopPaddingExtraDp(
    isHorizontalMode: Boolean,
    actionDockCollapsed: Boolean,
    fontScale: Float,
    shouldShowHorizontalUserList: Boolean = true,
): Float {
    // Only explicit action expansion changes this inset. Scroll-driven chrome collapse
    // must not remeasure the staggered grid or disturb its restored anchors.
    val topBarHeightDp = resolveDynamicTopBarTabHeightDp(fontScale) +
        AppSpacingTokens.Small.value +
        if (actionDockCollapsed) 0f else AppChromeSizeTokens.MinimumTouchTarget.value
    return topBarHeightDp + if (isHorizontalMode && shouldShowHorizontalUserList) {
        DynamicHorizontalUserListReservedHeightDp
    } else {
        0
    }
}

/** 关注入口在所有分类保留；番剧不套用作者筛选，选择作者时转到全部动态。 */
internal fun resolveDynamicAuthorSelectionTab(selectedTab: Int): Int =
    if (selectedTab == 2) 0 else selectedTab

internal fun shouldCollapseDynamicHorizontalUserList(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    topTolerancePx: Int = 8
): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > topTolerancePx
}

internal fun resolveDynamicScrollCollapsedHeaderHeightPx(
    expandedHeightPx: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Int {
    if (expandedHeightPx <= 0 || firstVisibleItemIndex > 0) return 0
    return (expandedHeightPx - firstVisibleItemScrollOffset).coerceIn(0, expandedHeightPx)
}

internal fun resolveDynamicScrollCollapsedHeaderOffsetYPx(
    expandedHeightPx: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Int {
    if (expandedHeightPx <= 0) return 0
    if (firstVisibleItemIndex > 0) return -expandedHeightPx
    return -firstVisibleItemScrollOffset.coerceIn(0, expandedHeightPx)
}

/**
 * Tab top-bar collapse is optional. When [collapseOnScrollEnabled] is false the bar stays pinned;
 * the horizontal UP list still uses [shouldCollapseDynamicHorizontalUserList] independently.
 */
internal fun shouldCollapseDynamicTopBar(
    collapseOnScrollEnabled: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    topTolerancePx: Int = 8
): Boolean {
    if (!collapseOnScrollEnabled) return false
    return shouldCollapseDynamicHorizontalUserList(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        topTolerancePx = topTolerancePx
    )
}

internal const val DYNAMIC_UP_PANEL_ALL_UID = -1L

internal fun resolveDynamicUpPanelUsers(
    users: List<SidebarUser>,
    selfUid: Long,
    selfFace: String
): List<SidebarUser> {
    val allItem = SidebarUser(
        uid = DYNAMIC_UP_PANEL_ALL_UID,
        name = "全部动态",
        face = ""
    )
    val selfItem = selfUid.takeIf { it > 0L }?.let { uid ->
        SidebarUser(
            uid = uid,
            name = "我",
            face = selfFace
        )
    }
    val rest = users.filterNot { user ->
        user.uid == DYNAMIC_UP_PANEL_ALL_UID || (selfUid > 0L && user.uid == selfUid)
    }
    return listOfNotNull(allItem, selfItem) + rest
}

internal fun isDynamicUpPanelAllShortcut(uid: Long?): Boolean {
    return uid == DYNAMIC_UP_PANEL_ALL_UID
}

internal fun isDynamicUpPanelShortcut(uid: Long, selfUid: Long): Boolean {
    return uid == DYNAMIC_UP_PANEL_ALL_UID || (selfUid > 0L && uid == selfUid)
}

internal fun isDynamicUpPanelItemSelected(
    selectedUserId: Long?,
    itemUid: Long
): Boolean {
    return if (itemUid == DYNAMIC_UP_PANEL_ALL_UID) {
        selectedUserId == null
    } else {
        selectedUserId == itemUid
    }
}

internal fun resolveDynamicSelectedUserIdAfterClick(
    selectedUserId: Long?,
    clickedUserId: Long?
): Long? {
    if (clickedUserId == null || isDynamicUpPanelAllShortcut(clickedUserId)) return null
    // 作者选择是当前分类内的筛选，不作为开关；“全部动态”清除作者筛选，
    // 但保留当前的全部、投稿或专栏分类。
    return selectedUserId.takeIf { it == clickedUserId } ?: clickedUserId
}

internal fun shouldUseSelectedUserDynamicFeed(
    selectedTab: Int,
    selectedUserId: Long?
): Boolean {
    return selectedUserId != null && (selectedTab == 0 || selectedTab == 1 || selectedTab == 3)
}


internal fun shouldResetDynamicFeedScrollOnSourceChange(
    previousTab: Int,
    nextTab: Int,
    previousSelectedUserId: Long?,
    nextSelectedUserId: Long?
): Boolean {
    return previousTab != nextTab || previousSelectedUserId != nextSelectedUserId
}

internal fun resolveDynamicSelectedTab(
    savedTab: Int?,
    tabCount: Int
): Int {
    if (tabCount <= 0) return 0
    return savedTab?.takeIf { it in 0 until tabCount } ?: 0
}

internal fun resolveDynamicSwipeTargetTab(
    currentTab: Int,
    tabCount: Int,
    dragDistancePx: Float,
    thresholdPx: Float = 96f
): Int? {
    if (tabCount <= 0 || currentTab !in 0 until tabCount) return null
    if (kotlin.math.abs(dragDistancePx) < thresholdPx) return null
    val target = if (dragDistancePx < 0f) currentTab + 1 else currentTab - 1
    return target.takeIf { it in 0 until tabCount && it != currentTab }
}

internal fun resolveDynamicFeedRequestType(selectedTab: Int): String {
    return when (selectedTab) {
        1 -> "video"
        2 -> "pgc"
        3 -> "article"
        else -> "all"
    }
}

internal fun shouldUseServerFilteredDynamicFeed(selectedTab: Int): Boolean {
    return selectedTab in 1..3
}

internal fun resolveHorizontalUserListVerticalPaddingDp(): Int {
    return 4
}

internal fun shouldShowDynamicErrorOverlay(
    error: String?,
    activeItemsCount: Int
): Boolean {
    return !error.isNullOrBlank() && activeItemsCount == 0
}

internal fun shouldShowDynamicLoadingFooter(
    isLoading: Boolean,
    activeItemsCount: Int
): Boolean {
    return isLoading && activeItemsCount > 0
}

internal fun shouldLoadMoreDynamicFeed(
    furthestVisibleItemIndex: Int?,
    totalItemsCount: Int,
    allowAutomaticLoadMore: Boolean,
    isLoading: Boolean,
    hasMore: Boolean,
    prefetchDistance: Int = 3,
    error: String? = null,
): Boolean {
    if (!allowAutomaticLoadMore || isLoading || !hasMore || totalItemsCount <= 0 || error != null) return false
    val visibleIndex = furthestVisibleItemIndex ?: return false
    return visibleIndex >= totalItemsCount - prefetchDistance.coerceAtLeast(1)
}

internal fun shouldShowDynamicNoMoreFooter(
    hasMore: Boolean,
    activeItemsCount: Int
): Boolean {
    return !hasMore && activeItemsCount > 0
}

internal fun shouldRevealDynamicBottomBarForStaticContent(
    activeItemsCount: Int,
    isLoading: Boolean
): Boolean {
    return activeItemsCount == 0 && !isLoading
}

internal fun shouldShowDynamicCommentSheet(selectedDynamicId: String?): Boolean {
    return !selectedDynamicId.isNullOrBlank()
}

internal fun resolveDynamicCommentSheetTotalCount(
    liveCount: Int,
    fallbackCount: Int
): Int {
    return if (liveCount > 0) liveCount else fallbackCount.coerceAtLeast(0)
}

internal fun resolveDynamicStateAfterAuthorUnfollow(
    currentState: DynamicUiState,
    authorMid: Long
): DynamicUiState {
    if (authorMid <= 0L) return currentState
    return mapDynamicTimelineItems(currentState) { items ->
        items.filterNot { it.modules.module_author?.mid == authorMid }
    }.copy(
        userItems = currentState.userItems.filterNot { it.modules.module_author?.mid == authorMid }.toImmutableList()
    )
}

internal fun resolveFollowedUsersAfterAuthorUnfollow(
    users: List<SidebarUser>,
    authorMid: Long
): List<SidebarUser> {
    if (authorMid <= 0L) return users
    return users.filterNot { it.uid == authorMid }
}

/**
 * 判断动态条目是否代表真实独立的 UP 主账号（排除合集/剧集/番剧等虚拟发布主体及明确未关注账号）
 */
internal fun isDynamicItemRealUser(item: DynamicItem): Boolean {
    val author = item.modules.module_author ?: return false
    if (author.mid <= 0L || author.name.isBlank()) return false
    // 若 B 站接口明确标记未关注，绝不作为已关注候选
    if (author.following == false) return false

    val type = item.type.trim()
    if (type in setOf(
            "DYNAMIC_TYPE_UGC_SEASON",
            "DYNAMIC_TYPE_PGC",
            "DYNAMIC_TYPE_PGC_UNION",
            "DYNAMIC_TYPE_COURSES_SEASON"
        )
    ) {
        return false
    }

    val major = item.modules.module_dynamic?.major
    if (major?.ugc_season != null || major?.pgc != null) {
        return false
    }

    return true
}

/**
 * 从动态流中提取活跃用户（过滤掉非真实UP主体及明确未关注账号，并基于 uid 与 name+face 严格去重）
 */
internal fun extractUsersFromDynamicItems(items: List<DynamicItem>): List<SidebarUser> {
    val latestByUser = mutableMapOf<Long, SidebarUser>()
    // 用于防止不同 synthetic mid 伪装成相同名字+头像的重复账号刷屏
    val seenIdentities = mutableMapOf<String, Long>()

    items.forEach { item ->
        if (!isDynamicItemRealUser(item)) return@forEach
        val author = item.modules.module_author ?: return@forEach
        val identityKey = "${author.name.trim()}|${author.face.trim()}"
        val existingOwnerMid = seenIdentities[identityKey]
        if (existingOwnerMid != null && existingOwnerMid != author.mid) {
            // 已有相同名称与头像的真实账号，忽略不同 mid 的重复条目
            return@forEach
        }
        seenIdentities[identityKey] = author.mid

        val lastActive = author.pub_ts.takeIf { it > 0L } ?: 0L
        val existing = latestByUser[author.mid]
        if (existing == null || lastActive > existing.lastActiveTs) {
            latestByUser[author.mid] = SidebarUser(
                uid = author.mid,
                name = author.name,
                face = author.face,
                isLive = false,
                lastActiveTs = lastActive
            )
        }
    }
    return latestByUser.values.toList()
}

/**
 * 合并关注用户列表、直播中用户及动态活跃用户：
 * 1. 当 followingUsers（真实关注列表）已加载非空时：
 *    以 followingUsers 为权威白名单主体，仅允许真正的关注 UP 主存在于侧边栏；
 *    liveUsers（来自关注的直播间）更新直播中状态与最近活跃时间；
 *    dynamicUsers 仅用来更新已关注 UP 主的最新活跃时间及头像/名称，绝不随意把未关注陌生人塞进侧边栏！
 * 2. 当 followingUsers 为空时（如初始加载或离线缓存阶段）：
 *    仅以严格过滤后的 liveUsers 与 dynamicUsers 兜底，绝不包含合集或未关注账号。
 */
internal fun resolveMergedFollowedUsers(
    followingUsers: List<SidebarUser>,
    liveUsers: List<SidebarUser>,
    dynamicUsers: List<SidebarUser> = emptyList()
): List<SidebarUser> {
    if (followingUsers.isNotEmpty()) {
        val merged = followingUsers.associateBy { it.uid }.toMutableMap()
        // 关注的直播中 UP
        liveUsers.forEach { liveUser ->
            val existing = merged[liveUser.uid]
            if (existing != null) {
                merged[liveUser.uid] = existing.copy(
                    isLive = true,
                    lastActiveTs = max(existing.lastActiveTs, liveUser.lastActiveTs)
                )
            } else {
                // 来自 getFollowedLive 的用户本身即为已关注且正在直播，可加入
                merged[liveUser.uid] = liveUser
            }
        }
        // 动态活跃信息：仅对白名单中的关注用户进行活跃时间与信息丰富，绝不新增未关注用户
        dynamicUsers.forEach { dynamicUser ->
            val existing = merged[dynamicUser.uid]
            if (existing != null) {
                merged[dynamicUser.uid] = existing.copy(
                    name = if (dynamicUser.name.isNotBlank()) dynamicUser.name else existing.name,
                    face = if (dynamicUser.face.isNotBlank()) dynamicUser.face else existing.face,
                    lastActiveTs = max(existing.lastActiveTs, dynamicUser.lastActiveTs)
                )
            }
        }
        return merged.values.toList()
    } else {
        // 未完成全量关注加载时的兜底策略
        val merged = mutableMapOf<Long, SidebarUser>()
        val seenIdentities = mutableSetOf<String>()
        (liveUsers + dynamicUsers).forEach { user ->
            val identityKey = "${user.name.trim()}|${user.face.trim()}"
            if (identityKey.isNotBlank() && seenIdentities.contains(identityKey) && !merged.containsKey(user.uid)) {
                // 忽略相同姓名头像但不同 uid 的重复项
                return@forEach
            }
            val existing = merged[user.uid]
            if (existing == null) {
                merged[user.uid] = user
                if (identityKey.isNotBlank()) seenIdentities.add(identityKey)
            } else {
                merged[user.uid] = existing.copy(
                    name = if (user.name.isNotBlank()) user.name else existing.name,
                    face = if (user.face.isNotBlank()) user.face else existing.face,
                    isLive = existing.isLive || user.isLive,
                    lastActiveTs = max(existing.lastActiveTs, user.lastActiveTs)
                )
            }
        }
        return merged.values.toList()
    }
}

internal fun shouldResetFollowedUserListToTopOnRefresh(
    boundaryKey: String?,
    prependedCount: Int,
    selectedUserId: Long?,
    handledBoundaryKey: String?
): Boolean {
    if (boundaryKey.isNullOrBlank()) return false
    if (prependedCount <= 0) return false
    if (selectedUserId != null) return false
    return boundaryKey != handledBoundaryKey
}

enum class DynamicFeedErrorSource {
    NONE,
    INITIAL_LOAD,
    REFRESH,
    APPEND
}

internal fun DynamicUiState.timelinePage(requestType: String): DynamicTimelinePageState {
    timelinePages[requestType]?.let { return it }
    if (timelineRequestType != requestType) return DynamicTimelinePageState()
    return DynamicTimelinePageState(
        items = items,
        isLoading = isLoading,
        error = error,
        hasMore = hasMore,
        incrementalRefreshBoundaryKey = incrementalRefreshBoundaryKey,
        incrementalPrependedCount = incrementalPrependedCount,
        errorSource = errorSource
    )
}

internal fun updateDynamicTimelinePage(
    currentState: DynamicUiState,
    requestType: String,
    transform: (DynamicTimelinePageState) -> DynamicTimelinePageState
): DynamicUiState {
    val updatedPage = transform(currentState.timelinePage(requestType))
    val updatedState = currentState.copy(
        timelinePages = currentState.timelinePages.put(requestType, updatedPage)
    )
    return if (currentState.timelineRequestType == requestType) {
        updatedState.copyActiveTimelinePage(requestType, updatedPage)
    } else {
        updatedState
    }
}

internal fun DynamicUiState.selectTimelinePage(requestType: String): DynamicUiState {
    return copyActiveTimelinePage(requestType, timelinePage(requestType))
}

internal fun mapDynamicTimelineItems(
    currentState: DynamicUiState,
    transform: (List<DynamicItem>) -> List<DynamicItem>
): DynamicUiState {
    val requestTypes = currentState.timelinePages.keys + currentState.timelineRequestType
    return requestTypes.fold(currentState) { state, requestType ->
        updateDynamicTimelinePage(state, requestType) { page ->
            page.copy(items = transform(page.items).toImmutableList())
        }
    }
}

private fun DynamicUiState.copyActiveTimelinePage(
    requestType: String,
    page: DynamicTimelinePageState
): DynamicUiState {
    return copy(
        items = page.items,
        timelineRequestType = requestType,
        isLoading = page.isLoading,
        error = page.error,
        hasMore = page.hasMore,
        incrementalRefreshBoundaryKey = page.incrementalRefreshBoundaryKey,
        incrementalPrependedCount = page.incrementalPrependedCount,
        errorSource = page.errorSource
    )
}

internal fun resolveDynamicTimelinePageForLoadStart(
    currentPage: DynamicTimelinePageState,
    refresh: Boolean,
    showLoading: Boolean
): DynamicTimelinePageState {
    val basePage = currentPage.copy(
        error = null,
        errorSource = DynamicFeedErrorSource.NONE
    )
    return when {
        refresh && showLoading -> basePage.copy(isLoading = true)
        !refresh -> basePage.copy(isLoading = true)
        else -> basePage
    }
}

internal fun resolveDynamicTimelinePageAfterSuccess(
    currentPage: DynamicTimelinePageState,
    incomingItems: List<DynamicItem>,
    isRefresh: Boolean,
    incrementalRefreshEnabled: Boolean,
    hasMore: Boolean
): DynamicTimelinePageState {
    val currentItems = currentPage.items
    val canUseIncrementalRefresh = canPerformIncrementalTimelineRefresh(
        isRefresh = isRefresh,
        incrementalRefreshEnabled = incrementalRefreshEnabled,
        isCachePlaceholder = currentPage.isCachePlaceholder,
        existingItems = currentItems,
        incomingItems = incomingItems
    )
    val mergedItems = when {
        canUseIncrementalRefresh -> sortDynamicTimelineItemsByPublishTime(
            prependDistinctByKey(
                existing = currentItems,
                incoming = incomingItems,
                keySelector = ::dynamicFeedItemKey
            )
        )
        isRefresh -> sortDynamicTimelineItemsByPublishTime(incomingItems)
        else -> appendDistinctByKey(
            existing = currentItems,
            incoming = incomingItems,
            keySelector = ::dynamicFeedItemKey
        )
    }
    val boundary = when {
        canUseIncrementalRefresh -> resolveIncrementalRefreshBoundary(
            existingKeys = currentItems.map(::dynamicFeedItemKey),
            mergedKeys = mergedItems.map(::dynamicFeedItemKey)
        )
        isRefresh -> IncrementalRefreshBoundary(null, 0)
        else -> IncrementalRefreshBoundary(
            currentPage.incrementalRefreshBoundaryKey,
            currentPage.incrementalPrependedCount
        )
    }
    return currentPage.copy(
        items = mergedItems.toImmutableList(),
        isLoading = false,
        error = null,
        hasMore = hasMore,
        incrementalRefreshBoundaryKey = boundary.boundaryKey,
        incrementalPrependedCount = boundary.prependedCount,
        errorSource = DynamicFeedErrorSource.NONE,
        isCachePlaceholder = false
    )
}

internal fun resolveDynamicTimelinePageAfterFailure(
    currentPage: DynamicTimelinePageState,
    errorMessage: String,
    refresh: Boolean
): DynamicTimelinePageState {
    val source = when {
        currentPage.items.isEmpty() -> DynamicFeedErrorSource.INITIAL_LOAD
        refresh -> DynamicFeedErrorSource.REFRESH
        else -> DynamicFeedErrorSource.APPEND
    }
    return currentPage.copy(
        isLoading = false,
        error = errorMessage,
        errorSource = source
    )
}

internal fun resolveDynamicActiveLoadingState(
    currentState: DynamicUiState,
    selectedUserId: Long?
): Boolean {
    return if (selectedUserId != null) currentState.userIsLoading else currentState.isLoading
}

internal fun resolveDynamicActiveError(
    currentState: DynamicUiState,
    selectedUserId: Long?
): String? {
    return if (selectedUserId != null) currentState.userError else currentState.error
}

internal fun resolveDynamicFeedStateForLoadStart(
    currentState: DynamicUiState,
    refresh: Boolean,
    showLoading: Boolean
): DynamicUiState {
    val baseState = currentState.copy(
        error = null,
        errorSource = DynamicFeedErrorSource.NONE
    )
    return when {
        refresh && showLoading -> baseState.copy(isLoading = true)
        !refresh -> baseState.copy(isLoading = true)
        else -> baseState
    }
}

internal fun resolveDynamicFeedStateAfterSuccess(
    currentState: DynamicUiState,
    incomingItems: List<DynamicItem>,
    isRefresh: Boolean,
    requestType: String,
    incrementalRefreshEnabled: Boolean,
    hasMore: Boolean
): DynamicUiState {
    val currentItems = currentState.items
    val canUseIncrementalRefresh = currentState.timelineRequestType == requestType &&
        canPerformIncrementalTimelineRefresh(
            isRefresh = isRefresh,
            incrementalRefreshEnabled = incrementalRefreshEnabled,
            isCachePlaceholder = false,
            existingItems = currentItems,
            incomingItems = incomingItems
        )
    val mergedItems = when {
        canUseIncrementalRefresh -> sortDynamicTimelineItemsByPublishTime(
            prependDistinctByKey(
                existing = currentItems,
                incoming = incomingItems,
                keySelector = ::dynamicFeedItemKey
            )
        )
        isRefresh -> sortDynamicTimelineItemsByPublishTime(incomingItems)
        else -> appendDistinctByKey(
            existing = currentItems,
            incoming = incomingItems,
            keySelector = ::dynamicFeedItemKey
        )
    }
    val boundary = when {
        canUseIncrementalRefresh -> resolveIncrementalRefreshBoundary(
            existingKeys = currentItems.map(::dynamicFeedItemKey),
            mergedKeys = mergedItems.map(::dynamicFeedItemKey)
        )
        isRefresh -> IncrementalRefreshBoundary(
            boundaryKey = null,
            prependedCount = 0
        )
        else -> IncrementalRefreshBoundary(
            boundaryKey = currentState.incrementalRefreshBoundaryKey,
            prependedCount = currentState.incrementalPrependedCount
        )
    }
    return currentState.copy(
        items = mergedItems.toImmutableList(),
        isLoading = false,
        error = null,
        errorSource = DynamicFeedErrorSource.NONE,
        hasMore = hasMore,
        timelineRequestType = requestType,
        incrementalRefreshBoundaryKey = boundary.boundaryKey,
        incrementalPrependedCount = boundary.prependedCount
    )
}

internal fun sortDynamicTimelineItemsByPublishTime(items: List<DynamicItem>): List<DynamicItem> {
    if (items.size <= 1) return items
    return items
        .mapIndexed { index, item -> index to item }
        .sortedWith(
            compareByDescending<Pair<Int, DynamicItem>> { (_, item) ->
                item.modules.module_author?.pub_ts ?: 0L
            }.thenBy { (index, _) -> index }
        )
        .map { (_, item) -> item }
}

internal fun resolveDynamicFeedStateAfterFailure(
    currentState: DynamicUiState,
    errorMessage: String,
    refresh: Boolean
): DynamicUiState {
    val source = when {
        currentState.items.isEmpty() -> DynamicFeedErrorSource.INITIAL_LOAD
        refresh -> DynamicFeedErrorSource.REFRESH
        else -> DynamicFeedErrorSource.APPEND
    }
    return currentState.copy(
        isLoading = false,
        error = errorMessage,
        errorSource = source
    )
}
