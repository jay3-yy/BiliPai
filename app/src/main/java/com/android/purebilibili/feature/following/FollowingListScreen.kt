package com.android.purebilibili.feature.following

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.FollowingCacheStore
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.OfficialVerifyAvatarBadge
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSnackbarHost
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberStaticSkeletonBlockColor
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.FollowingUser
import com.android.purebilibili.data.model.response.RelationTagItem
import com.android.purebilibili.data.repository.ActionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.purebilibili.core.util.PinyinUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// UI 状态
sealed class FollowingListUiState {
    object Loading : FollowingListUiState()
    data class Success(
        val users: List<FollowingUser>,
        val total: Int,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true
    ) : FollowingListUiState()
    data class Error(val message: String) : FollowingListUiState()
}

data class BatchUnfollowResult(
    val successCount: Int,
    val failedCount: Int,
    val succeededMids: Set<Long> = emptySet()
)

internal fun toggleFollowingSelection(current: Set<Long>, mid: Long): Set<Long> {
    return if (current.contains(mid)) current - mid else current + mid
}

internal fun resolveFollowingSelectAll(
    visibleMids: List<Long>,
    currentSelected: Set<Long>
): Set<Long> {
    val visibleSet = visibleMids.toSet()
    if (visibleSet.isEmpty()) return currentSelected
    val allSelected = visibleSet.all { currentSelected.contains(it) }
    return if (allSelected) currentSelected - visibleSet else currentSelected + visibleSet
}

internal fun buildBatchUnfollowResultMessage(successCount: Int, failedCount: Int): String {
    return when {
        failedCount == 0 -> "已取消关注 $successCount 位 UP 主"
        successCount == 0 -> "批量取关失败，请稍后重试"
        else -> "已取消关注 $successCount 位，$failedCount 位失败"
    }
}

data class BatchFollowGroupDialogData(
    val tags: List<RelationTagItem>,
    val initialSelection: Set<Long>,
    val hasMixedSelection: Boolean
)

private const val SPECIAL_FOLLOW_TAG_ID = -10L
private const val DEFAULT_FOLLOW_TAG_ID = 0L
private const val FOLLOW_GROUP_META_FETCH_INTERVAL_MS = 80L
private const val FOLLOWING_PAGE_PUBLISH_INTERVAL_PAGES = 3
private const val BATCH_UNFOLLOW_INTERVAL_MS = 320L
private const val BATCH_UNFOLLOW_MAX_ATTEMPTS = 3
private const val BATCH_UNFOLLOW_RETRY_BASE_DELAY_MS = 900L

internal fun shouldSkipFollowingReload(
    cachedMid: Long,
    targetMid: Long,
    uiState: FollowingListUiState,
    forceRefresh: Boolean
): Boolean {
    if (forceRefresh) return false
    if (cachedMid != targetMid) return false
    return uiState is FollowingListUiState.Success || uiState is FollowingListUiState.Loading
}

internal fun shouldUseFollowingPersistentCache(
    forceRefresh: Boolean,
    requestMid: Long,
    cachedMid: Long,
    cachedUsersCount: Int
): Boolean {
    if (forceRefresh) return false
    if (requestMid <= 0L) return false
    if (cachedMid != requestMid) return false
    return cachedUsersCount > 0
}

internal fun resolveFollowingRefreshFailureState(
    currentState: FollowingListUiState,
    message: String
): FollowingListUiState {
    return if (currentState is FollowingListUiState.Success && currentState.users.isNotEmpty()) {
        currentState.copy(isLoadingMore = false)
    } else {
        FollowingListUiState.Error(message)
    }
}

internal fun isRetryableBatchOperationError(message: String?): Boolean {
    val text = message.orEmpty()
    if (text.isBlank()) return false
    return text.contains("频繁") ||
        text.contains("过快") ||
        text.contains("风控") ||
        text.contains("稍后") ||
        text.contains("-412") ||
        text.contains("-352") ||
        text.contains("too many", ignoreCase = true) ||
        text.contains("rate", ignoreCase = true)
}

internal fun resolveFollowGroupInitialSelection(groupSets: List<Set<Long>>): Set<Long> {
    if (groupSets.isEmpty()) return emptySet()
    val normalized = groupSets.map { it.filterNot { id -> id == 0L }.toSet() }
    val first = normalized.first()
    val allSame = normalized.all { it == first }
    return if (allSame) first else emptySet()
}

internal fun hasMixedFollowGroupSelection(groupSets: List<Set<Long>>): Boolean {
    if (groupSets.isEmpty()) return false
    val normalized = groupSets.map { it.filterNot { id -> id == 0L }.toSet() }
    return normalized.distinct().size > 1
}

class FollowingListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<FollowingListUiState>(FollowingListUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isBatchUnfollowing = MutableStateFlow(false)
    val isBatchUnfollowing = _isBatchUnfollowing.asStateFlow()

    private val _followGroupTags = MutableStateFlow<List<RelationTagItem>>(emptyList())
    val followGroupTags = _followGroupTags.asStateFlow()

    private val _userFollowGroupIds = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val userFollowGroupIds = _userFollowGroupIds.asStateFlow()

    private val _isFollowGroupMetaLoading = MutableStateFlow(false)
    val isFollowGroupMetaLoading = _isFollowGroupMetaLoading.asStateFlow()

    private var currentMid: Long = 0
    private val removedUserMids = mutableSetOf<Long>()
    private var followGroupRefreshJob: Job? = null
    private var followingGeneration = 0L
    private var followingRequestActive = false
    private var nextFollowingPage = 1
    private var loadRemainingPagesJob: Job? = null
    private var followingCacheSaveJob: Job? = null

    fun loadFollowingList(mid: Long, forceRefresh: Boolean = false) {
        if (mid <= 0) return
        if (shouldSkipFollowingReload(currentMid, mid, _uiState.value, forceRefresh)) return

        val generation = ++followingGeneration
        followingRequestActive = true
        nextFollowingPage = 1
        currentMid = mid
        removedUserMids.clear()
        followGroupRefreshJob?.cancel()
        followGroupRefreshJob = null
        loadRemainingPagesJob?.cancel()
        loadRemainingPagesJob = null
        followingCacheSaveJob?.cancel()
        followingCacheSaveJob = null
        _userFollowGroupIds.value = emptyMap()
        _isFollowGroupMetaLoading.value = false

        val restoredFromCache = restoreFollowingListFromPersistentCache(mid, forceRefresh)

        viewModelScope.launch {
            if (!restoredFromCache) {
                _uiState.value = FollowingListUiState.Loading
            }

            try {
                // 缓存只负责首屏快速展示，服务器第一页始终作为最新关注关系的真源。
                val response = NetworkModule.api.getFollowings(mid, pn = 1, ps = 50)
                if (generation != followingGeneration) return@launch
                if (response.code == 0 && response.data != null) {
                    nextFollowingPage = 2
                    val checkedResponseData = requireNotNull(response.data)
                    val initialUsers = checkedResponseData.list.orEmpty()
                        .filterNot { removedUserMids.contains(it.mid) }
                    val total = checkedResponseData.total
                    _uiState.value = FollowingListUiState.Success(
                        users = initialUsers,
                        total = total,
                        hasMore = initialUsers.size < total // 还有更多数据需要加载
                    )
                    persistFollowingCache(mid = mid, total = total, users = initialUsers)
                    refreshFollowGroupMetadata(initialUsers)
                    // 后续页仅由滚动或显式全量操作触发。
                } else {
                    _uiState.update { current ->
                        resolveFollowingRefreshFailureState(
                            currentState = current,
                            message = "加载失败: ${response.message}"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != followingGeneration) return@launch
                _uiState.update { current ->
                    resolveFollowingRefreshFailureState(
                        currentState = current,
                        message = e.message ?: "网络错误"
                    )
                }
            } finally {
                if (generation == followingGeneration) followingRequestActive = false
            }
        }
    }

    private fun restoreFollowingListFromPersistentCache(mid: Long, forceRefresh: Boolean): Boolean {
        val context = NetworkModule.appContext ?: return false
        val snapshot = FollowingCacheStore.getSnapshot(context, mid) ?: return false
        if (!shouldUseFollowingPersistentCache(
                forceRefresh = forceRefresh,
                requestMid = mid,
                cachedMid = snapshot.mid,
                cachedUsersCount = snapshot.users.size
            )
        ) {
            return false
        }

        val users = snapshot.users
            .filterNot { removedUserMids.contains(it.mid) }
            .distinctBy { it.mid }
        if (users.isEmpty()) return false

        _uiState.value = FollowingListUiState.Success(
            users = users,
            total = snapshot.total.coerceAtLeast(users.size),
            isLoadingMore = false,
            hasMore = false
        )
        refreshFollowGroupMetadata(users)
        return true
    }

    // 全量加载只允许由用户显式触发；普通滚动每次仅请求一页。
    fun loadAllForSearch() {
        if (followingRequestActive || loadRemainingPagesJob?.isActive == true) return
        val generation = followingGeneration
        loadRemainingPagesJob = viewModelScope.launch {
            try {
                while (generation == followingGeneration) {
                    val current = _uiState.value as? FollowingListUiState.Success ?: break
                    if (!current.hasMore || !loadNextFollowingPage(generation)) break
                }
            } finally {
                if (generation == followingGeneration) loadRemainingPagesJob = null
            }
        }
    }

    private suspend fun loadNextFollowingPage(generation: Long): Boolean {
        val current = _uiState.value as? FollowingListUiState.Success ?: return false
        if (generation != followingGeneration || followingRequestActive || !current.hasMore) return false
        followingRequestActive = true
        _uiState.value = current.copy(isLoadingMore = true)
        val mid = currentMid
        val page = nextFollowingPage
        try {
            val response = NetworkModule.api.getFollowings(mid, pn = page, ps = 50)
            if (generation != followingGeneration) return false
            val data = response.data
            if (response.code != 0 || data == null) return false
            val incoming = data.list.orEmpty()
            val latest = _uiState.value as? FollowingListUiState.Success ?: return false
            val previousCount = latest.users.size
            val merged = mergeFollowingUsersOffMain(latest.users, incoming)
            if (generation != followingGeneration) return false
            nextFollowingPage = page + 1
            publishFollowingUsers(mid, data.total, merged, isLoadingMore = false)
            val published = _uiState.value as? FollowingListUiState.Success ?: return false
            // 只有「本页有内容」且「合并后确有新增」才继续，避免服务器重复返回同一页时无限翻页。
            val hasProgress = shouldContinueFollowingPagination(
                previousCount = previousCount,
                mergedCount = merged.size,
                incomingCount = incoming.size,
                total = data.total
            )
            _uiState.value = published.copy(hasMore = hasProgress)
            refreshFollowGroupMetadata(merged)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        } finally {
            if (generation == followingGeneration) {
                followingRequestActive = false
                (_uiState.value as? FollowingListUiState.Success)?.let {
                    _uiState.value = it.copy(isLoadingMore = false)
                }
            }
        }
    }

    fun loadMore() {
        if (followingRequestActive || loadRemainingPagesJob?.isActive == true || currentMid <= 0L) return
        val generation = followingGeneration
        loadRemainingPagesJob = viewModelScope.launch {
            try {
                loadNextFollowingPage(generation)
            } finally {
                if (generation == followingGeneration) loadRemainingPagesJob = null
            }
        }
    }

    private suspend fun mergeFollowingUsersOffMain(
        currentUsers: List<FollowingUser>,
        incomingUsers: List<FollowingUser>
    ): MutableList<FollowingUser> {
        val removedMids = removedUserMids.toSet()
        return withContext(Dispatchers.Default) {
            mergeFollowingUsersDistinct(
                currentUsers = currentUsers,
                incomingUsers = incomingUsers,
                removedUserMids = removedMids
            ).toMutableList()
        }
    }

    private fun publishFollowingUsers(
        mid: Long,
        total: Int,
        users: List<FollowingUser>,
        isLoadingMore: Boolean
    ) {
        val safeTotal = total.coerceAtLeast(users.size)
        _uiState.value = FollowingListUiState.Success(
            users = users.toList(),
            total = safeTotal,
            hasMore = isFollowingListIncomplete(users.size, safeTotal),
            isLoadingMore = isLoadingMore
        )
        persistFollowingCache(mid = mid, total = safeTotal, users = users)
    }

    suspend fun batchUnfollow(targetUsers: List<FollowingUser>): BatchUnfollowResult {
        if (targetUsers.isEmpty()) {
            return BatchUnfollowResult(successCount = 0, failedCount = 0)
        }
        if (_isBatchUnfollowing.value) {
            return BatchUnfollowResult(successCount = 0, failedCount = targetUsers.size)
        }

        _isBatchUnfollowing.value = true
        val successMids = mutableSetOf<Long>()
        var failedCount = 0
        try {
            targetUsers.forEachIndexed { index, user ->
                val success = unfollowWithRetry(user.mid)
                if (success) {
                    successMids.add(user.mid)
                } else {
                    failedCount += 1
                }
                if (index < targetUsers.lastIndex) {
                    delay(BATCH_UNFOLLOW_INTERVAL_MS)
                }
            }
            if (successMids.isNotEmpty()) {
                removedUserMids.addAll(successMids)
                applyRemovedUsers(successMids)
                com.android.purebilibili.core.events.BrandSuccessEvents.followChanged(
                    following = false,
                    detail = if (failedCount == 0) "已取关 ${successMids.size} 位 UP 主"
                        else "已取关 ${successMids.size} 位，${failedCount} 位失败"
                )
            }
            return BatchUnfollowResult(
                successCount = successMids.size,
                failedCount = failedCount,
                succeededMids = successMids
            )
        } finally {
            _isBatchUnfollowing.value = false
        }
    }

    private suspend fun unfollowWithRetry(mid: Long): Boolean {
        repeat(BATCH_UNFOLLOW_MAX_ATTEMPTS) { attempt ->
            val result = ActionRepository.followUser(mid, follow = false, emitBrandFeedback = false)
            if (result.isSuccess) return true

            val message = result.exceptionOrNull()?.message
            val retryable = isRetryableBatchOperationError(message)
            if (!retryable || attempt >= BATCH_UNFOLLOW_MAX_ATTEMPTS - 1) {
                return false
            }

            val backoffMs = BATCH_UNFOLLOW_RETRY_BASE_DELAY_MS * (attempt + 1)
            delay(backoffMs)
        }
        return false
    }

    private fun applyRemovedUsers(removedMids: Set<Long>) {
        val current = _uiState.value as? FollowingListUiState.Success ?: return
        val remainingUsers = current.users.filterNot { removedMids.contains(it.mid) }
        val reducedTotal = (current.total - removedMids.size).coerceAtLeast(remainingUsers.size)
        _userFollowGroupIds.update { currentMap ->
            currentMap - removedMids
        }
        _uiState.value = current.copy(
            users = remainingUsers,
            total = reducedTotal
        )
        persistFollowingCache(mid = currentMid, total = reducedTotal, users = remainingUsers)
    }

    private fun persistFollowingCache(mid: Long, total: Int, users: List<FollowingUser>) {
        if (mid <= 0L) return
        val context = NetworkModule.appContext ?: return
        val snapshotUsers = users.toList()

        followingCacheSaveJob?.cancel()
        followingCacheSaveJob = viewModelScope.launch(Dispatchers.IO) {
            if (snapshotUsers.isEmpty()) {
                FollowingCacheStore.clear(context)
            } else {
                FollowingCacheStore.saveSnapshot(
                    context = context,
                    mid = mid,
                    total = total,
                    users = snapshotUsers
                )
            }
        }
    }

    private fun refreshFollowGroupMetadata(users: List<FollowingUser>) {
        if (users.isEmpty()) return

        followGroupRefreshJob?.cancel()
        followGroupRefreshJob = viewModelScope.launch {
            if (_followGroupTags.value.isEmpty()) {
                ActionRepository.getFollowGroupTags().onSuccess { tags ->
                    _followGroupTags.value = tags
                        .filter { it.tagid != DEFAULT_FOLLOW_TAG_ID }
                        .sortedBy { it.tagid != SPECIAL_FOLLOW_TAG_ID }
                }
            }

            _isFollowGroupMetaLoading.value = true
            try {
                val userMidSet = users.asSequence().map { it.mid }.toSet()
                val orderedTagIds = buildList {
                    add(SPECIAL_FOLLOW_TAG_ID)
                    addAll(_followGroupTags.value.map { it.tagid })
                }.distinct().filter { it != DEFAULT_FOLLOW_TAG_ID }

                // 每轮重建当前列表的“已命中分组”映射，避免旧结果残留。
                _userFollowGroupIds.update { currentMap ->
                    currentMap - userMidSet
                }

                var allTagsFetched = true
                orderedTagIds.forEachIndexed { index, tagId ->
                    val result = ActionRepository.getFollowGroupMemberMids(
                        tagId = tagId,
                        targetMids = userMidSet
                    )
                    result.onSuccess { mids ->
                        if (mids.isEmpty()) return@onSuccess
                        _userFollowGroupIds.update { currentMap ->
                            val mutable = currentMap.toMutableMap()
                            mids.forEach { mid ->
                                val existing = mutable[mid].orEmpty().toMutableSet()
                                existing.add(tagId)
                                mutable[mid] = existing
                            }
                            mutable
                        }
                    }.onFailure { error ->
                        allTagsFetched = false
                        com.android.purebilibili.core.util.Logger.w(
                            "FollowingListVM",
                            "skip tag=$tagId group mapping: ${error.message}"
                        )
                    }

                    if (index < orderedTagIds.lastIndex) {
                        delay(FOLLOW_GROUP_META_FETCH_INTERVAL_MS)
                    }
                }

                // 只有全部分组都成功时，才把“未命中任何分组”的用户标记为默认分组。
                if (allTagsFetched) {
                    _userFollowGroupIds.update { currentMap ->
                        val mutable = currentMap
                            .filterKeys { userMidSet.contains(it) }
                            .toMutableMap()
                        users.forEach { user ->
                            if (!mutable.containsKey(user.mid)) {
                                mutable[user.mid] = emptySet()
                            }
                        }
                        mutable
                    }
                }
            } finally {
                _isFollowGroupMetaLoading.value = false
                followGroupRefreshJob = null
            }
        }
    }

    suspend fun prepareBatchGroupDialogData(targetMids: List<Long>): Result<BatchFollowGroupDialogData> {
        return runCatching {
            val mids = targetMids.toSet().toList()
            if (mids.isEmpty()) {
                return@runCatching BatchFollowGroupDialogData(
                    tags = emptyList(),
                    initialSelection = emptySet(),
                    hasMixedSelection = false
                )
            }

            val tags = ActionRepository.getFollowGroupTags().getOrThrow()
                .filter { it.tagid != 0L }
                .sortedBy { it.tagid != -10L }

            val currentGroups = _userFollowGroupIds.value
            val missingMids = mids.filterNot { currentGroups.containsKey(it) }
            val fetched = linkedMapOf<Long, Set<Long>>()
            missingMids.forEachIndexed { index, mid ->
                val error = addFollowGroupMappingIfSuccess(
                    target = fetched,
                    userMid = mid,
                    result = ActionRepository.getUserFollowGroupIds(mid)
                )
                if (error != null) {
                    com.android.purebilibili.core.util.Logger.w(
                        "FollowingListVM",
                        "skip group mapping for batch mid=$mid: ${error.message}"
                    )
                }
                if (index < missingMids.lastIndex) {
                    delay(FOLLOW_GROUP_META_FETCH_INTERVAL_MS)
                }
            }
            if (fetched.isNotEmpty()) {
                _userFollowGroupIds.update { it + fetched }
            }
            val mergedGroups = _userFollowGroupIds.value + fetched
            val groupSets = mids.map { mid -> mergedGroups[mid] ?: emptySet() }

            BatchFollowGroupDialogData(
                tags = tags,
                initialSelection = resolveFollowGroupInitialSelection(groupSets),
                hasMixedSelection = hasMixedFollowGroupSelection(groupSets)
            )
        }
    }

    suspend fun saveBatchGroupSelection(targetMids: List<Long>, selectedTagIds: Set<Long>): Result<Boolean> {
        return ActionRepository.overwriteFollowGroupIds(targetMids.toSet(), selectedTagIds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingListScreen(
    mid: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,  // 点击跳转到 UP 主空间
    onFansClick: (Long) -> Unit,
    viewModel: FollowingListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBatchUnfollowing by viewModel.isBatchUnfollowing.collectAsStateWithLifecycle()
    val followGroupTags by viewModel.followGroupTags.collectAsStateWithLifecycle()
    val userFollowGroupIds by viewModel.userFollowGroupIds.collectAsStateWithLifecycle()
    val isFollowGroupMetaLoading by viewModel.isFollowGroupMetaLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()
    var isPullRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(mid) {
        viewModel.loadFollowingList(mid)
    }
    LaunchedEffect(uiState) {
        if (isPullRefreshing && uiState !is FollowingListUiState.Loading) {
            isPullRefreshing = false
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf<Long?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedMids by remember { mutableStateOf(setOf<Long>()) }
    var showBatchUnfollowConfirm by remember { mutableStateOf(false) }
    var showBatchGroupDialog by remember { mutableStateOf(false) }
    var groupDialogLoading by remember { mutableStateOf(false) }
    var groupDialogSaving by remember { mutableStateOf(false) }
    var groupDialogTags by remember { mutableStateOf<List<RelationTagItem>>(emptyList()) }
    var groupDialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var groupDialogMixed by remember { mutableStateOf(false) }

    AppScaffold(
        blurContentReady = uiState !is FollowingListUiState.Loading,
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
            AppTopBar(
                title = "我的关注",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isEditMode) {
                        AppTextButton(onClick = { onFansClick(mid) }) { AppText("粉丝") }
                    }
                    if (uiState is FollowingListUiState.Success) {
                        if ((uiState as? FollowingListUiState.Success)?.hasMore == true) {
                            AppTextButton(onClick = { viewModel.loadAllForSearch() }) {
                                AppText("加载全部")
                            }
                        }
                        AppTextButton(
                            onClick = {
                                isEditMode = !isEditMode
                                if (!isEditMode) {
                                    selectedMids = emptySet()
                                }
                            },
                            enabled = !isBatchUnfollowing
                        ) {
                            AppText(if (isEditMode) "完成" else "管理")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppSurfaceTokens.chromeBackground()
                )
            )
            // 🔍 搜索栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small)
            ) {
                com.android.purebilibili.core.ui.components.AppLiquidAwareSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索已加载 UP 主（全量请点加载全部）",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .globalWallpaperAwareBackground()
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // 加载中 / 出错 / 成功共用同一个 LazyColumn：骨架行与真实用户行使用同一种
                // contentType，数据到达时是 item 原地替换，列表不会被整棵重建，首屏不跳。
                val successState = uiState as? FollowingListUiState.Success
                val errorState = uiState as? FollowingListUiState.Error
                val showInitialSkeleton = uiState is FollowingListUiState.Loading
                val followedUsers = successState?.users.orEmpty()

                        LaunchedEffect(followedUsers) {
                            val available = followedUsers.asSequence().map { it.mid }.toSet()
                            selectedMids = selectedMids.intersect(available)
                        }

                        val groupFilterChips = remember(followedUsers, followGroupTags, userFollowGroupIds) {
                            val users = followedUsers
                            val defaultCount = countUsersInDefaultFollowGroup(users, userFollowGroupIds)
                            val dynamicTags = followGroupTags.ifEmpty {
                                listOf(
                                    RelationTagItem(tagid = SPECIAL_FOLLOW_TAG_ID, name = "特别关注", count = 0)
                                )
                            }
                            buildList {
                                add(RelationTagItem(tagid = Long.MIN_VALUE, name = "全部", count = users.size))
                                add(RelationTagItem(tagid = DEFAULT_FOLLOW_TAG_ID, name = "默认分组", count = defaultCount))
                                dynamicTags.forEach { tag ->
                                    val count = users.count { user ->
                                        userFollowGroupIds[user.mid]?.contains(tag.tagid) == true
                                    }
                                    add(tag.copy(count = count))
                                }
                            }
                        }

                        val usersByGroup = remember(followedUsers, selectedGroupFilter, userFollowGroupIds) {
                            filterUsersBySelectedFollowGroup(
                                users = followedUsers,
                                selectedGroupFilter = selectedGroupFilter,
                                userFollowGroupIds = userFollowGroupIds,
                                defaultGroupTagId = DEFAULT_FOLLOW_TAG_ID,
                                allGroupTagId = Long.MIN_VALUE
                            )
                        }
                        val followGroupMetaLoadedCount = remember(followedUsers, userFollowGroupIds) {
                            followedUsers.count { user -> userFollowGroupIds.containsKey(user.mid) }
                        }

                        // 🔍 过滤列表
                        val filteredUsers = remember(usersByGroup, searchQuery) {
                            if (searchQuery.isBlank()) usersByGroup
                            else {
                                usersByGroup.filter {
                                    PinyinUtils.matches(it.uname, searchQuery) ||
                                    PinyinUtils.matches(it.sign, searchQuery)
                                }
                            }
                        }
                        val visibleMids = remember(filteredUsers) { filteredUsers.map { it.mid } }
                        val selectedCount = selectedMids.size
                        val hasSelection = selectedCount > 0

                        // Scaffold + search sit above this box; indicator at list region top.
                        AdaptivePullToRefreshBox(
                            isRefreshing = isPullRefreshing,
                            onRefresh = {
                                if (!isPullRefreshing) {
                                    isPullRefreshing = true
                                    viewModel.loadFollowingList(mid, forceRefresh = true)
                                }
                            },
                            state = pullRefreshState,
                            indicatorTopInset = padding.calculateTopPadding(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    top = padding.calculateTopPadding(),
                                    bottom = padding.calculateBottomPadding(),
                                ),
                                modifier = Modifier
                                    .responsiveContentWidth(resolveFollowingListMaxWidth())
                                    .fillMaxSize(),
                            ) {
                                when {
                                    showInitialSkeleton -> {
                                        items(
                                            count = FOLLOWING_LIST_SKELETON_ROW_COUNT,
                                            key = { index -> "following_skeleton_$index" },
                                            contentType = { FOLLOWING_USER_ROW_CONTENT_TYPE }
                                        ) { index ->
                                            FollowingUserRowSkeleton(
                                                modifier = Modifier.animateItem(),
                                                slotIndex = index,
                                            )
                                        }
                                    }

                                    errorState != null -> {
                                        item(key = "following_error") {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .fillParentMaxHeight(0.6f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    AppText("😢", fontSize = MaterialTheme.typography.displaySmall.fontSize)
                                                    Spacer(Modifier.height(AppSpacingTokens.Large))
                                                    AppText(errorState.message, color = AppContentColors.secondaryText())
                                                    Spacer(Modifier.height(AppSpacingTokens.Large))
                                                    AppButton(onClick = { viewModel.loadFollowingList(mid, forceRefresh = true) }) {
                                                        AppText("重试")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    successState != null -> {
                                        if (filteredUsers.isEmpty() && searchQuery.isNotEmpty()) {
                                            item(key = "following_no_result") {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .fillParentMaxHeight(0.6f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AppText("没有找到相关 UP 主", color = AppContentColors.secondaryText())
                                                }
                                            }
                                        } else {
                                    // 统计信息
                                    item {
                                        AnimatedBlurFadeText(
                                            targetText = when {
                                                isEditMode -> "已选 $selectedCount 人"
                                                searchQuery.isEmpty() && (selectedGroupFilter == null || selectedGroupFilter == Long.MIN_VALUE) ->
                                                    "共 ${successState.total} 个关注"
                                                searchQuery.isEmpty() -> "当前分组 ${filteredUsers.size} 人"
                                                else -> "找到 ${filteredUsers.size} 个结果"
                                            },
                                            modifier = Modifier.padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium)
                                        ) { text, animatedModifier ->
                                            AppText(
                                                text = text,
                                                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                                color = AppContentColors.secondaryText(),
                                                modifier = animatedModifier
                                            )
                                        }
                                    }

                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.ExtraSmall),
                                            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
                                        ) {
                                            groupFilterChips.forEach { chip ->
                                                val chipFilterId = if (chip.tagid == Long.MIN_VALUE) null else chip.tagid
                                                AppFilterChip(
                                                    selected = selectedGroupFilter == chipFilterId ||
                                                        (selectedGroupFilter == null && chip.tagid == Long.MIN_VALUE),
                                                    onClick = { selectedGroupFilter = chipFilterId },
                                                    label = {
                                                        AnimatedBlurFadeText(targetText = "${chip.name} ${chip.count}") { text, modifier ->
                                                            AppText(text = text, modifier = modifier)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (isFollowGroupMetaLoading) {
                                        item {
                                            AppText(
                                                text = "分组信息加载中...($followGroupMetaLoadedCount/${followedUsers.size})",
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                color = AppContentColors.secondaryText(),
                                                modifier = Modifier.padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.ExtraSmall)
                                            )
                                        }
                                    }

                                    items(
                                        items = filteredUsers,
                                        key = { it.mid },
                                        contentType = { FOLLOWING_USER_ROW_CONTENT_TYPE }
                                    ) { user ->
                                        LaunchedEffect(user.mid, filteredUsers.size) {
                                            if (user.mid == filteredUsers.lastOrNull()?.mid && searchQuery.isBlank()) {
                                                viewModel.loadMore()
                                            }
                                        }
                                        FollowingUserItem(
                                            user = user,
                                            isEditMode = isEditMode,
                                            isSelected = selectedMids.contains(user.mid),
                                            onFansClick = { onFansClick(user.mid) },
                                            modifier = Modifier.animateItem(),
                                            onClick = {
                                                if (isEditMode) {
                                                    selectedMids = toggleFollowingSelection(selectedMids, user.mid)
                                                } else {
                                                    onUserClick(user.mid)
                                                }
                                            }
                                        )
                                    }

                                    // 加载更多 (仅在未搜索时显示，因为搜索是本地过滤)
                                    if (searchQuery.isEmpty()) {
                                        if (successState.isLoadingMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(AppSpacingTokens.Large),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AdaptiveLoadingIndicator()
                                                }
                                            }
                                        } else if (successState.hasMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.loadMore() }
                                                        .padding(AppSpacingTokens.Large),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AppText(
                                                        "加载更多",
                                                        color = AppContentColors.accent(),
                                                        fontSize = MaterialTheme.typography.labelMedium.fontSize
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

                        if (isEditMode && successState != null) {
                            AppSurface(
                                tonalElevation = AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2,
                                shadowElevation = AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small + AppSpacingTokens.Micro),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppOutlinedButton(
                                        onClick = {
                                            selectedMids = resolveFollowingSelectAll(
                                                visibleMids = visibleMids,
                                                currentSelected = selectedMids
                                            )
                                        },
                                        enabled = !isBatchUnfollowing
                                    ) {
                                        val allVisibleSelected = visibleMids.isNotEmpty() &&
                                            visibleMids.all { selectedMids.contains(it) }
                                        AppText(if (allVisibleSelected) "取消全选" else "全选当前")
                                    }

                                    AppOutlinedButton(
                                        onClick = {
                                            showBatchGroupDialog = true
                                            groupDialogLoading = true
                                            scope.launch {
                                                val result = viewModel.prepareBatchGroupDialogData(selectedMids.toList())
                                                result.onSuccess { dialogData ->
                                                    groupDialogTags = dialogData.tags
                                                    groupDialogSelection = dialogData.initialSelection
                                                    groupDialogMixed = dialogData.hasMixedSelection
                                                }.onFailure {
                                                    showBatchGroupDialog = false
                                                    snackbarHostState.showSnackbar("加载分组失败: ${it.message}")
                                                }
                                                groupDialogLoading = false
                                            }
                                        },
                                        enabled = hasSelection && !isBatchUnfollowing
                                    ) {
                                        AppText("设置分组")
                                    }

                                    AppButton(
                                        onClick = { showBatchUnfollowConfirm = true },
                                        enabled = hasSelection && !isBatchUnfollowing,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isBatchUnfollowing) {
                                            AppCircularProgressIndicator(
                                                modifier = Modifier.size(AppSpacingTokens.Large),
                                                strokeWidth = AppSpacingTokens.Micro,
                                                color = AppContentColors.onAccent()
                                            )
                                        } else {
                                            AppText("取消关注 ($selectedCount)")
                                        }
                                    }
                                }
                            }
                        }

                        if (showBatchUnfollowConfirm) {
                            AppAlertDialog(
                                onDismissRequest = {
                                    if (!isBatchUnfollowing) showBatchUnfollowConfirm = false
                                },
                                title = { AppText("批量取消关注") },
                                text = { AppText("确认取消关注已选择的 $selectedCount 位 UP 主吗？") },
                                confirmButton = {
                                    AppButton(
                                        onClick = {
                                            val targets = followedUsers.filter { selectedMids.contains(it.mid) }
                                            scope.launch {
                                                val result = viewModel.batchUnfollow(targets)
                                                snackbarHostState.showSnackbar(
                                                    buildBatchUnfollowResultMessage(
                                                        successCount = result.successCount,
                                                        failedCount = result.failedCount
                                                    )
                                                )
                                                selectedMids = selectedMids - result.succeededMids
                                                if (selectedMids.isEmpty()) {
                                                    isEditMode = false
                                                }
                                                showBatchUnfollowConfirm = false
                                            }
                                        },
                                        enabled = !isBatchUnfollowing
                                    ) {
                                        AppText("确认")
                                    }
                                },
                                dismissButton = {
                                    AppTextButton(
                                        onClick = { showBatchUnfollowConfirm = false },
                                        enabled = !isBatchUnfollowing
                                    ) {
                                        AppText("取消")
                                    }
                                }
                            )
                        }

                        if (showBatchGroupDialog) {
                            AppAlertDialog(
                                onDismissRequest = {
                                    if (!groupDialogSaving) showBatchGroupDialog = false
                                },
                                title = { AppText("批量设置分组") },
                                text = {
                                    if (groupDialogLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = AppSpacingTokens.Medium),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AdaptiveLoadingIndicator()
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = resolveFollowingBatchGroupDialogMaxHeight())
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            if (groupDialogMixed) {
                                                AppText(
                                                    text = "检测到已选 UP 主原分组不一致，已默认全部不选。",
                                                    color = AppContentColors.secondaryText(),
                                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                    modifier = Modifier.padding(bottom = AppSpacingTokens.Small)
                                                )
                                            }
                                            if (groupDialogTags.isEmpty()) {
                                                AppText(
                                                    text = "暂无可用分组（不勾选即回到默认分组）",
                                                    color = AppContentColors.secondaryText(),
                                                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                                                )
                                            } else {
                                                groupDialogTags.forEach { tag ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                groupDialogSelection = if (groupDialogSelection.contains(tag.tagid)) {
                                                                    groupDialogSelection - tag.tagid
                                                                } else {
                                                                    groupDialogSelection + tag.tagid
                                                                }
                                                            }
                                                            .padding(vertical = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        AppCheckbox(
                                                            checked = groupDialogSelection.contains(tag.tagid),
                                                            onCheckedChange = { checked ->
                                                                groupDialogSelection = if (checked == true) {
                                                                    groupDialogSelection + tag.tagid
                                                                } else {
                                                                    groupDialogSelection - tag.tagid
                                                                }
                                                            }
                                                        )
                                                        AppText(
                                                            text = "${tag.name} (${tag.count})",
                                                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                                            color = AppContentColors.primaryText()
                                                        )
                                                    }
                                                }
                                            }
                                            AppText(
                                                text = "确定后会完全覆盖原分组设置。",
                                                color = AppContentColors.secondaryText(),
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                modifier = Modifier.padding(top = AppSpacingTokens.Small)
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    AppButton(
                                        onClick = {
                                            groupDialogSaving = true
                                            scope.launch {
                                                val result = viewModel.saveBatchGroupSelection(
                                                    targetMids = selectedMids.toList(),
                                                    selectedTagIds = groupDialogSelection
                                                )
                                                result.onSuccess {
                                                    showBatchGroupDialog = false
                                                    snackbarHostState.showSnackbar("分组设置已保存")
                                                }.onFailure {
                                                    snackbarHostState.showSnackbar("分组设置失败: ${it.message}")
                                                }
                                                groupDialogSaving = false
                                            }
                                        },
                                        enabled = !groupDialogLoading && !groupDialogSaving
                                    ) {
                                        if (groupDialogSaving) {
                                            AppCircularProgressIndicator(
                                                modifier = Modifier.size(AppSpacingTokens.Large),
                                                strokeWidth = AppSpacingTokens.Micro,
                                                color = AppContentColors.onAccent()
                                            )
                                        } else {
                                            AppText("确定")
                                        }
                                    }
                                },
                                dismissButton = {
                                    AppTextButton(
                                        onClick = { showBatchGroupDialog = false },
                                        enabled = !groupDialogSaving
                                    ) {
                                        AppText("取消")
                                    }
                                }
                            )
                        }
            }
        }
    }
}

@Composable
private fun AnimatedBlurFadeText(
    targetText: String,
    modifier: Modifier = Modifier,
    content: @Composable (String, Modifier) -> Unit
) {
    val blurAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }
    val standardMotionSpec = AppMotionTokens.standardSpec<Float>()

    LaunchedEffect(targetText) {
        blurAnim.snapTo(6f)
        alphaAnim.snapTo(0.55f)
        launch {
            blurAnim.animateTo(
                targetValue = 0f,
                animationSpec = standardMotionSpec
            )
        }
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = standardMotionSpec
        )
    }

    AnimatedContent(
        targetState = targetText,
        transitionSpec = {
            (fadeIn(animationSpec = standardMotionSpec) togetherWith
                fadeOut(animationSpec = standardMotionSpec)) using
                SizeTransform(clip = false)
        },
        label = "following-count-blur-fade"
    ) { text ->
        content(
            text,
            modifier
                .alpha(alphaAnim.value)
                .blur(blurAnim.value.dp)
        )
    }
}

/**
 * 关注用户行的 lazy item 身份。骨架行与真实用户行共用同一个 contentType，加载完成时
 * LazyList 才能在原地替换 item，而不是「整批删除 + 整批新增」重建可视区。
 */
private const val FOLLOWING_USER_ROW_CONTENT_TYPE = "following_user_row"

/** 首屏骨架行数，与原来的 itemCount 同量级，避免加载完成后列表长度突变。 */
private const val FOLLOWING_LIST_SKELETON_ROW_COUNT = 10

/**
 * 关注用户行骨架：对齐 [FollowingUserItem] 的布局（水平 16dp / 垂直 12dp 内边距、
 * 48dp 头像、昵称 + 签名两行）。行高基本一致，列表不会被突然撑长或缩短。
 *
 * 色块用固定色：骨架与真实行在同一个 LazyColumn 内原地替换，切换时只应看到「内容淡入」。
 */
@Composable
private fun FollowingUserRowSkeleton(
    modifier: Modifier = Modifier,
    slotIndex: Int = 0,
) {
    val blockColor = rememberStaticSkeletonBlockColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContentSkeletonBlock(
            color = blockColor,
            shape = CircleShape,
            modifier = Modifier.size(AppSpacingTokens.TripleExtraLarge),
        )
        Spacer(Modifier.width(AppSpacingTokens.Medium))
        Column(modifier = Modifier.weight(1f)) {
            ContentSkeletonBlock(
                color = blockColor,
                modifier = Modifier
                    .fillMaxWidth(if (slotIndex % 2 == 0) 0.32f else 0.26f)
                    .height(AppSpacingTokens.Medium + AppSpacingTokens.ExtraSmall),
            )
            Spacer(Modifier.height(AppSpacingTokens.ExtraSmall))
            ContentSkeletonBlock(
                color = blockColor,
                modifier = Modifier
                    .fillMaxWidth(if (slotIndex % 3 == 0) 0.58f else 0.44f)
                    .height(AppSpacingTokens.Small),
            )
        }
    }
}

@Composable
private fun FollowingUserItem(
    user: FollowingUser,
    isEditMode: Boolean,
    isSelected: Boolean,
    onFansClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val officialBadge = remember(user.officialVerify) {
        resolveFollowingOfficialVerifyBadge(user.officialVerify)
    }
    val followingSinceLabel = remember(user.mtime) {
        formatFollowingSinceLabel(user.mtime)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        Box(modifier = Modifier.size(AppSpacingTokens.TripleExtraLarge)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(user.face))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (officialBadge != null) {
                OfficialVerifyAvatarBadge(
                    badge = officialBadge,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            } else {
                UserAvatarCornerMarkBadge(
                    mark = resolveUserAvatarCornerMark(
                        officialType = null,
                        vipStatus = user.vip?.vipStatus,
                    ),
                    modifier = Modifier.align(Alignment.BottomEnd),
                    badgeSize = 14.dp,
                )
            }
        }

        Spacer(Modifier.width(AppSpacingTokens.Medium))

        // 用户信息
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppText(
                    text = user.uname,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.primaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (followingSinceLabel.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacingTokens.ExtraSmall))
                AppText(
                    text = followingSinceLabel,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    color = AppContentColors.accent(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (user.sign.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacingTokens.ExtraSmall))
                AppText(
                    text = user.sign,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isEditMode) {
            AppCheckbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
        } else {
            AppTextButton(onClick = onFansClick) { AppText("粉丝") }
        }
    }
}
