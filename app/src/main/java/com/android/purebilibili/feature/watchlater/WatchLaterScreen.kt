// 文件路径: feature/watchlater/WatchLaterScreen.kt
package com.android.purebilibili.feature.watchlater

import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.core.util.responsiveContentWidth

import com.android.purebilibili.navigation.tabSelectedPage
import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.feature.home.effectiveGridColumnCountForWindow
import com.android.purebilibili.feature.home.effectiveGridColumnCountCompact
import com.android.purebilibili.feature.home.gridColumnPinchEnabled
import com.android.purebilibili.core.ui.components.VideoStatusBadges
import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.AppFloatingToolbar
import android.os.Build
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.feature.home.GridPinchColumnHudPill
import com.android.purebilibili.feature.home.rememberFeedGridColumnPreference
import com.android.purebilibili.feature.home.homeFeedPinchZoom
import com.android.purebilibili.feature.home.resolveHomeFeedPinchColumnBounds
import com.android.purebilibili.core.util.LocalWindowSizeClass
import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.pager.HorizontalPager
import com.android.purebilibili.core.ui.common.rememberContentTabPager
import com.android.purebilibili.core.ui.common.contentTabPagerGestures
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalStaggeredGrid
import com.android.purebilibili.core.ui.rememberCompatibleStaggeredGridState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.coroutines.AppScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.CancellationException
import com.android.purebilibili.core.refresh.WatchLaterRefreshBus
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppLiquidAwareSearchField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppWatchLaterIcon
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.withMeasuredCoverDecodeSize
import com.android.purebilibili.feature.home.components.cards.videoCardShellReturnChromeAlpha
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.data.repository.WatchLaterRepository
import com.android.purebilibili.feature.list.resolveCommonListRenderKeys
import com.android.purebilibili.feature.list.resolveHistoryFilterTabChromeSpec
import com.android.purebilibili.feature.personal.PersonalMediaCardFrame
import com.android.purebilibili.feature.personal.PersonalMediaCardSkeleton
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.feature.plugin.rememberSponsorFeedBadgeLabel
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Close
import com.android.purebilibili.core.util.FormatUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// 辅助函数：格式化时长
private fun formatDuration(seconds: Int): String {
    return FormatUtils.formatDuration(seconds)
}

// 辅助函数：格式化数字
private fun formatNumber(num: Int): String {
    return FormatUtils.formatStat(num.toLong())
}

// 辅助函数：修复封面 URL 协议（B站API可能返回http或缺少协议的URL）
private fun fixCoverUrl(url: String?): String {
    if (url.isNullOrEmpty()) return ""
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        else -> url
    }
}

private const val WATCH_LATER_DELETE_MAX_ATTEMPTS = 3
private const val WATCH_LATER_DELETE_RETRY_BASE_DELAY_MS = 850L

internal fun isRetryableWatchLaterDeleteError(code: Int, message: String): Boolean {
    if (code in setOf(-412, -352, -509, 22015, 34004)) return true
    if (message.isBlank()) return false
    return message.contains("频繁") ||
        message.contains("过快") ||
        message.contains("风控") ||
        message.contains("稍后") ||
        message.contains("too many", ignoreCase = true) ||
        message.contains("rate", ignoreCase = true)
}

internal fun resolveWatchLaterPlayAllStartTarget(
    items: List<VideoItem>
): Pair<String, Long>? {
    val first = items.firstOrNull() ?: return null
    return first.bvid to first.cid
}

private fun resolveWatchLaterPlaybackTargetOrDefault(
    items: List<VideoItem>,
    bvid: String,
    fallbackCid: Long = 0L
): WatchLaterPlaybackTarget {
    return resolveWatchLaterPlaybackTarget(items, bvid)
        ?: WatchLaterPlaybackTarget(
            bvid = bvid,
            cid = fallbackCid.coerceAtLeast(0L),
            resumePositionMs = 0L
        )
}

internal fun resolveWatchLaterTitle(itemCount: Int): String {
    return "稍后再看 ($itemCount)"
}

/**
 * 稍后再看 UI 状态
 */
data class WatchLaterUiState(
    val items: List<VideoItem> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isManaging: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val filter: WatchLaterFilter = WatchLaterFilter.ALL,
    val query: String = "",
    val sortOrder: WatchLaterSortOrder = WatchLaterSortOrder.FORWARD,
    val favoriteFolders: List<FavFolder> = emptyList(),
    val isTransferLoading: Boolean = false,
    val error: String? = null,
    val dissolvingIds: Set<String> = emptySet() // [新增] 用于已播放 Thanos Snap 动画的卡片
)

/**
 * 稍后再看 ViewModel
 */
class WatchLaterViewModel(application: Application) : AndroidViewModel(application) {
    private var isPageActive = false

    fun setPageActive(active: Boolean) {
        isPageActive = active
        if (active && needsAccountReload) loadData()
    }

    private val _uiState = MutableStateFlow(WatchLaterUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    private data class WatchLaterManagementSnapshot(
        val state: WatchLaterUiState,
        val affectedCount: Int
    )
    private val tabCache = mutableMapOf<WatchLaterFilter, WatchLaterUiState>()
    private var loadGeneration = 0L
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var needsAccountReload = false

    init {
        accountRequests.observe {
            loadGeneration++
            tabCache.clear()
            _uiState.value = WatchLaterUiState()
            needsAccountReload = true
            if (isPageActive) loadData()
        }
        observeWatchLaterRefresh()
    }

    private fun observeWatchLaterRefresh() {
        viewModelScope.launch {
            WatchLaterRefreshBus.changes.collect {
                // The navigation host reloads when this page becomes active again.
                if (isPageActive) loadData(showLoading = false)
            }
        }
    }

    fun loadData(showLoading: Boolean = true) {
        needsAccountReload = false
        if (!showLoading && _uiState.value.items.isNotEmpty()) {
            refreshLoadedPages()
        } else {
            loadPage(reset = true, showLoading = showLoading)
        }
    }

    private fun refreshLoadedPages() {
        val request = _uiState.value
        val generation = ++loadGeneration
        val identity = accountRequests.identity
        _uiState.value = request.copy(isLoadingMore = true, error = null)
        accountRequests.launch {
            val items = mutableListOf<VideoItem>()
            var lastPage: com.android.purebilibili.data.repository.WatchLaterPage? = null
            var loadedPage = 0
            for (pageNumber in 1..request.page.coerceAtLeast(1)) {
                val result = WatchLaterRepository.getPage(
                    page = pageNumber,
                    viewed = request.filter.viewed,
                    keyword = request.query,
                    ascending = request.sortOrder == WatchLaterSortOrder.REVERSE,
                )
                if (!accountRequests.isCurrent(identity) || generation != loadGeneration ||
                    _uiState.value.filter != request.filter
                ) return@launch
                val page = result.getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = error.message ?: "加载失败",
                    )
                    return@launch
                }
                items.addAll(page.items)
                lastPage = page
                loadedPage = pageNumber
                if (!page.hasMore) break
            }
            val page = lastPage ?: return@launch
            // 管理操作可能在刷新期间修改列表，不能用较早的请求结果覆盖它。
            if (_uiState.value.items != request.items) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
                return@launch
            }
            val next = _uiState.value.copy(
                items = items.distinctBy { it.aid },
                totalCount = page.totalCount,
                isLoading = false,
                isLoadingMore = false,
                page = loadedPage,
                hasMore = page.hasMore,
                error = null,
            )
            _uiState.value = next
            tabCache.clear()
            tabCache[next.filter] = next
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        loadPage(reset = false, showLoading = false)
    }

    /** 仅供切页预览读取；未访问的筛选显示占位，手势落定后才请求数据。 */
    fun previewFilter(filter: WatchLaterFilter): WatchLaterUiState {
        val current = _uiState.value
        if (filter == current.filter) return current
        return tabCache[filter]?.takeIf {
            it.query == current.query && it.sortOrder == current.sortOrder
        }?.copy(dissolvingIds = emptySet()) ?: WatchLaterUiState(
            filter = filter,
            query = current.query,
            sortOrder = current.sortOrder,
            isLoading = true,
        )
    }

    fun selectFilter(filter: WatchLaterFilter) {
        if (filter == _uiState.value.filter) return
        loadGeneration++
        val current = _uiState.value
        // 加载或失败状态不能缓存成成功的空页，快速切回时应重新请求。
        if (!current.isLoading && current.error == null) {
            tabCache[current.filter] = current.copy(isLoadingMore = false)
        } else {
            tabCache.remove(current.filter)
        }
        val cached = tabCache[filter]
        _uiState.value = if (
            cached != null && cached.query == current.query && cached.sortOrder == current.sortOrder
        ) {
            cached.copy(filter = filter, dissolvingIds = emptySet())
        } else {
            WatchLaterUiState(
                isLoading = true,
                filter = filter,
                query = current.query,
                sortOrder = current.sortOrder,
                favoriteFolders = current.favoriteFolders,
            )
        }
        if (cached == null || cached.query != current.query || cached.sortOrder != current.sortOrder) {
            loadPage(reset = true, showLoading = true)
        }
    }

    fun updateQuery(query: String) {
        if (query == _uiState.value.query) return
        _uiState.value = _uiState.value.copy(query = query)
        tabCache.clear()
        loadPage(reset = true, showLoading = true)
    }

    fun updateSortOrder(order: WatchLaterSortOrder) {
        if (order == _uiState.value.sortOrder) return
        _uiState.value = _uiState.value.copy(sortOrder = order)
        tabCache.clear()
        loadPage(reset = true, showLoading = true)
    }

    private fun loadPage(reset: Boolean, showLoading: Boolean) {
        val request = _uiState.value
        val requestPage = if (reset) 1 else request.page + 1
        val generation = ++loadGeneration
        _uiState.value = if (reset) {
            request.copy(
                isLoading = showLoading || request.items.isEmpty(),
                isLoadingMore = false,
                error = null,
                page = 1,
                hasMore = false,
            )
        } else {
            request.copy(isLoadingMore = true, error = null)
        }
        val identity = accountRequests.identity
        accountRequests.launch {
            val result = WatchLaterRepository.getPage(
                page = requestPage,
                viewed = request.filter.viewed,
                keyword = request.query,
                ascending = request.sortOrder == WatchLaterSortOrder.REVERSE,
            )
            if (!accountRequests.isCurrent(identity) ||
                generation != loadGeneration || _uiState.value.filter != request.filter
            ) return@launch
            result.fold(
                onSuccess = { page ->
                    val current = _uiState.value
                    val next = current.copy(
                        items = if (reset) page.items else (current.items + page.items).distinctBy { it.aid },
                        totalCount = page.totalCount,
                        isLoading = false,
                        isLoadingMore = false,
                        page = requestPage,
                        hasMore = page.hasMore,
                        error = null,
                    )
                    _uiState.value = next
                    tabCache[next.filter] = next
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = error.message ?: "加载失败",
                    )
                },
            )
        }
    }

    // [新增] 开始消散动画
    fun startVideoDissolve(bvid: String) {
        _uiState.value = _uiState.value.copy(
            dissolvingIds = _uiState.value.dissolvingIds + bvid
        )
    }

    // [新增] 动画完成，执行删除
    fun completeVideoDissolve(bvid: String) {
        // Ignore duplicate/deferred completion after a filter change or another deletion.
        if (bvid !in _uiState.value.dissolvingIds) return
        // deleteItem removes both the item and its animation flag in one state update.
        val item = _uiState.value.items.find { it.bvid == bvid }
        item?.let { deleteItem(it.id) }
    }

    /**
     * 从稍后再看删除视频
     */
    fun deleteItem(aid: Long) {
        // 乐观更新：直接从列表中移除，不需要重新请求
        val snapshotState = _uiState.value
        val currentList = snapshotState.items
        val newList = currentList.filter { it.id != aid }
        val removedBvid = currentList.firstOrNull { it.id == aid }?.bvid
        // A failed request restores the card, not the already completed dissolve request.
        val rollbackState = snapshotState.copy(
            dissolvingIds = snapshotState.dissolvingIds - setOfNotNull(removedBvid)
        )
        _uiState.value = _uiState.value.copy(
            items = newList,
            totalCount = (snapshotState.totalCount - (currentList.size - newList.size)).coerceAtLeast(newList.size),
            dissolvingIds = if (removedBvid == null) {
                _uiState.value.dissolvingIds
            } else {
                _uiState.value.dissolvingIds - removedBvid
            }
        )

        val identity = accountRequests.identity
        val csrf = TokenManager.csrfCache.orEmpty()
        val session = TokenManager.sessDataCache
        accountRequests.launch {
            try {
                if (csrf.isEmpty()) {
                    _uiState.value = rollbackState
                    android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val result = deleteWatchLaterAidWithRetry(aid, csrf, identity, session)
                if (!accountRequests.isCurrent(identity)) return@launch
                if (result.isSuccess) {
                    tabCache.clear()
                    android.widget.Toast.makeText(getApplication(), "已从稍后再看移除", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    _uiState.value = rollbackState
                    android.widget.Toast.makeText(
                        getApplication(),
                        "移除失败: ${result.exceptionOrNull()?.message ?: "请稍后重试"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!accountRequests.isCurrent(identity)) return@launch
                e.printStackTrace()
                _uiState.value = rollbackState
                android.widget.Toast.makeText(getApplication(), "移除失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteItems(aids: List<Long>) {
        if (aids.isEmpty()) return
        val aidSet = aids.toSet()
        val identity = accountRequests.identity
        val csrf = TokenManager.csrfCache.orEmpty()
        val session = TokenManager.sessDataCache
        val snapshotState = _uiState.value
        val snapshot = snapshotState.items
        val removeCount = snapshot.count { it.id in aidSet }
        val optimisticItems = snapshot.filterNot { it.id in aidSet }
        _uiState.value = _uiState.value.copy(
            items = optimisticItems,
            totalCount = (snapshotState.totalCount - removeCount).coerceAtLeast(optimisticItems.size),
            dissolvingIds = _uiState.value.dissolvingIds - snapshot.filter { it.id in aidSet }.map { it.bvid }.toSet()
        )

        AppScope.ioScope.launch {
            try {
                if (!isCurrentSession(identity, csrf, session)) return@launch
                if (csrf.isEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        if (!isCurrentSession(identity, csrf, session)) return@withContext
                        _uiState.value = snapshotState
                        android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val successIds = deleteWatchLaterItemsInBackground(aids, csrf, identity, session)

                withContext(Dispatchers.Main.immediate) {
                    if (!isCurrentSession(identity, csrf, session)) return@withContext
                    val successCount = successIds.size
                    if (successCount > 0) tabCache.clear()
                    _uiState.value = _uiState.value.copy(
                        items = snapshot.filterNot { it.id in successIds },
                        totalCount = (snapshotState.totalCount - successCount).coerceAtLeast(
                            snapshot.count { it.id !in successIds }
                        ),
                        dissolvingIds = _uiState.value.dissolvingIds -
                            snapshot.filter { it.id in successIds }.map { it.bvid }.toSet()
                    )

                    if (successCount == aids.size) {
                        android.widget.Toast.makeText(getApplication(), "已删除 ${aids.size} 个视频", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "批量删除完成：成功 $successCount / ${aids.size}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                withContext(Dispatchers.Main.immediate) {
                    if (!isCurrentSession(identity, csrf, session)) return@withContext
                    _uiState.value = snapshotState
                    android.widget.Toast.makeText(getApplication(), "批量删除失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isCurrentSession(
        identity: AccountSessionIdentity,
        csrf: String,
        session: String?,
    ): Boolean = accountRequests.isCurrent(identity) &&
        TokenManager.csrfCache.orEmpty() == csrf && TokenManager.sessDataCache == session

    private suspend fun deleteWatchLaterItemsInBackground(
        aids: List<Long>,
        csrf: String,
        identity: AccountSessionIdentity,
        session: String?,
    ): Set<Long> = withContext(Dispatchers.IO) {
        if (!isCurrentSession(identity, csrf, session)) return@withContext emptySet()
        val response = NetworkModule.api.deleteMultipleFromWatchLater(
            aids = aids.distinct().joinToString(","),
            csrf = csrf,
        )
        if (response.code == 0) aids.toSet() else emptySet()
    }

    internal fun runManagementAction(action: WatchLaterManagementAction) {
        val snapshotState = _uiState.value
        if (snapshotState.isManaging) return
        val snapshot = applyWatchLaterManagementOptimisticState(snapshotState, action)

        val identity = accountRequests.identity
        val csrf = TokenManager.csrfCache.orEmpty()
        accountRequests.launch {
            if (csrf.isBlank()) {
                _uiState.value = snapshot.state
                android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            val result = executeWatchLaterManagementAction(action = action, csrf = csrf)
            if (!accountRequests.isCurrent(identity)) return@launch
            handleWatchLaterManagementResult(action, snapshot, result)
        }
    }

    private fun applyWatchLaterManagementOptimisticState(
        snapshotState: WatchLaterUiState,
        action: WatchLaterManagementAction
    ): WatchLaterManagementSnapshot {
        val optimisticItems = resolveWatchLaterItemsAfterManagementAction(
            items = snapshotState.items,
            action = action
        )
        val affectedCount = (snapshotState.items.size - optimisticItems.size).coerceAtLeast(0)
        val optimisticBvids = optimisticItems.map { it.bvid }.toSet()
        val removedBvids = snapshotState.items.map { it.bvid }.filterNot { it in optimisticBvids }.toSet()
        _uiState.value = snapshotState.copy(
            items = optimisticItems,
            totalCount = when (action) {
                WatchLaterManagementAction.CLEAR_INVALID -> snapshotState.totalCount
                WatchLaterManagementAction.CLEAR_VIEWED ->
                    (snapshotState.totalCount - affectedCount).coerceAtLeast(optimisticItems.size)
                WatchLaterManagementAction.CLEAR_ALL -> 0
            },
            isManaging = true,
            dissolvingIds = snapshotState.dissolvingIds - removedBvids
        )
        return WatchLaterManagementSnapshot(snapshotState, affectedCount)
    }

    private fun handleWatchLaterManagementResult(
        action: WatchLaterManagementAction,
        snapshot: WatchLaterManagementSnapshot,
        result: Result<Unit>
    ) {
        if (result.isSuccess) {
            tabCache.clear()
            _uiState.value = _uiState.value.copy(isManaging = false)
            android.widget.Toast.makeText(
                getApplication(),
                resolveWatchLaterManagementSuccessMessage(action, snapshot.affectedCount),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            loadData()
        } else {
            _uiState.value = snapshot.state
            android.widget.Toast.makeText(
                getApplication(),
                "操作失败: ${result.exceptionOrNull()?.message ?: "请稍后重试"}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun executeWatchLaterManagementAction(
        action: WatchLaterManagementAction,
        csrf: String
    ): Result<Unit> {
        if (csrf.isBlank()) return Result.failure(Exception("请先登录"))
        return WatchLaterRepository.clear(
            cleanType = when (action) {
                WatchLaterManagementAction.CLEAR_INVALID -> 1
                WatchLaterManagementAction.CLEAR_VIEWED -> 2
                WatchLaterManagementAction.CLEAR_ALL -> null
            }
        )
    }

    fun loadFavoriteFolders() {
        if (_uiState.value.isTransferLoading) return
        val identity = accountRequests.identity
        val mid = identity.mid
        accountRequests.launch {
            if (mid == null) {
                android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            _uiState.value = _uiState.value.copy(isTransferLoading = true)
            val result = FavoriteRepository.getFavFolders(mid)
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = { folders ->
                    _uiState.value = _uiState.value.copy(
                        favoriteFolders = folders,
                        isTransferLoading = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isTransferLoading = false)
                    android.widget.Toast.makeText(
                        getApplication(),
                        error.message ?: "加载收藏夹失败",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    fun copyOrMoveToFavorite(aids: Set<Long>, targetMediaId: Long, copy: Boolean) {
        if (aids.isEmpty() || _uiState.value.isTransferLoading) return
        val snapshot = _uiState.value
        if (!copy) {
            _uiState.value = snapshot.copy(
                items = snapshot.items.filterNot { it.aid in aids },
                totalCount = (snapshot.totalCount - aids.size).coerceAtLeast(0),
                isTransferLoading = true,
            )
        } else {
            _uiState.value = snapshot.copy(isTransferLoading = true)
        }
        val identity = accountRequests.identity
        accountRequests.launch {
            val result = WatchLaterRepository.copyOrMoveToFavorite(targetMediaId, aids, copy)
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = {
                    tabCache.clear()
                    _uiState.value = _uiState.value.copy(isTransferLoading = false)
                    android.widget.Toast.makeText(
                        getApplication(),
                        if (copy) "复制成功" else "移动成功",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    if (!copy) loadData(showLoading = false)
                },
                onFailure = { error ->
                    _uiState.value = snapshot.copy(isTransferLoading = false)
                    android.widget.Toast.makeText(
                        getApplication(),
                        error.message ?: if (copy) "复制失败" else "移动失败",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    private suspend fun deleteWatchLaterAidWithRetry(
        aid: Long,
        csrf: String,
        identity: AccountSessionIdentity,
        session: String?,
    ): Result<Unit> {
        val api = NetworkModule.api
        repeat(WATCH_LATER_DELETE_MAX_ATTEMPTS) { attempt ->
            if (!isCurrentSession(identity, csrf, session)) throw CancellationException("Account changed")
            try {
                val response = api.deleteFromWatchLater(aid = aid, csrf = csrf)
                if (response.code == 0) {
                    return Result.success(Unit)
                }

                val retryable = isRetryableWatchLaterDeleteError(response.code, response.message)
                if (!retryable || attempt >= WATCH_LATER_DELETE_MAX_ATTEMPTS - 1) {
                    return Result.failure(
                        Exception(response.message.ifEmpty { "删除失败: ${response.code}" })
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (attempt >= WATCH_LATER_DELETE_MAX_ATTEMPTS - 1) {
                    return Result.failure(e)
                }
            }

            val backoffMs = WATCH_LATER_DELETE_RETRY_BASE_DELAY_MS * (attempt + 1)
            kotlinx.coroutines.delay(backoffMs)
        }
        return Result.failure(Exception("删除失败，请稍后重试"))
    }
}

/**
 *  稍后再看页面
 */

// ... (existing imports)

/**
 *  稍后再看页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterScreen(
    onBack: () -> Unit,
    onVideoClick: (String, Long, Long) -> Unit,
    onPlayAllAudioClick: ((String, Long, Long) -> Unit)? = null,
    initialSearchQuery: String = "",
    onOpenSearchDestination: ((String) -> Unit)? = null,
    isSearchDestination: Boolean = false,
    listScopedSearchChannel: Channel<String>? = null,
    viewModel: WatchLaterViewModel = viewModel(),
    globalHazeState: HazeState? = null, // [新增]
    scrollToTopChannel: Channel<Unit>? = null,
    isCurrentPage: Boolean = true
) {
    DisposableEffect(viewModel, isCurrentPage) {
        viewModel.setPageActive(isCurrentPage)
        onDispose { viewModel.setPageActive(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accountIdentity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val initialAccountIdentity = remember { accountIdentity }
    // 默认单列，可通过默认列数或双指缩放调整。
    val windowSizeClass = LocalWindowSizeClass.current
    val configuration = LocalConfiguration.current
    val pinchColumnBounds = remember(windowSizeClass.widthSizeClass, configuration.screenWidthDp) {
        resolveHomeFeedPinchColumnBounds(
            widthSizeClass = windowSizeClass.widthSizeClass,
            contentWidthDp = configuration.screenWidthDp,
        )
    }
    val context = LocalContext.current
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val gridColumnPreference = rememberFeedGridColumnPreference(
        settings = homeSettings,
        widthSizeClass = windowSizeClass.widthSizeClass,
        defaultColumns = com.android.purebilibili.feature.home.resolveHomeFeedStoredColumnCount(
            windowSizeClass.widthSizeClass, homeSettings.effectiveGridColumnCountCompact, homeSettings.effectiveGridColumnCountForWindow(windowSizeClass),
        ).takeIf { it > 0 } ?: 1,
        bounds = resolveHomeFeedPinchColumnBounds(
            windowSizeClass.widthSizeClass, LocalConfiguration.current.screenWidthDp,
        ),
    )
    val pinchListColumns = gridColumnPreference.columns
    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val hazeState = if (
        appThemeConfig.headerBlurEnabled &&
            shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
            !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    ) rememberRecoverableHazeState() else null
    val watchLaterChromeSource = if (appThemeConfig.progressiveTopBlurEnabled || appThemeConfig.liquidGlassEnabled) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    } else {
        null
    }
    val watchLaterChromeBackdrop = watchLaterChromeSource?.takeIf { !state.isLoading && it.isReady }?.backdrop
    val progressiveChromeActive = shouldUseBiliPaiProgressiveTopBlur(
        enabled = appThemeConfig.progressiveTopBlurEnabled && !appThemeConfig.headerBlurEnabled,
        hasBackdrop = watchLaterChromeBackdrop != null,
    ) && !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val fadeActive = appThemeConfig.progressiveTopFadeEnabled && !appThemeConfig.headerBlurEnabled
    val headerBlurActive = appThemeConfig.headerBlurEnabled &&
        hazeState?.let { recoverableBlurEnabled(it) } == true &&
        !progressiveChromeActive
    val topChromePolicy = rememberAppTopChromePolicy()
    val watchLaterFilterChrome = remember(homeSettings, topChromePolicy) {
        resolveHistoryFilterTabChromeSpec(
            homeSettings = homeSettings,
            topChromePolicy = topChromePolicy,
            filterCount = WatchLaterFilter.entries.size,
        )
    }
    val watchLaterHeaderCollapseMode = homeSettings.commonListHeaderCollapseMode
    val scrollBehavior = when (watchLaterHeaderCollapseMode) {
        com.android.purebilibili.core.store.CommonListHeaderCollapseMode.ALWAYS_VISIBLE ->
            TopAppBarDefaults.pinnedScrollBehavior()
        com.android.purebilibili.core.store.CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL ->
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        com.android.purebilibili.core.store.CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY ->
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    var isBatchMode by rememberSaveable(accountIdentity) { mutableStateOf(false) }
    var selectedBvids by rememberSaveable(accountIdentity) { mutableStateOf(setOf<String>()) }
    var showBatchDeleteConfirm by rememberSaveable(accountIdentity) { mutableStateOf(false) }
    var pendingTransferCopy by rememberSaveable(accountIdentity) { mutableStateOf<Boolean?>(null) }
    var selectedTransferFolderId by rememberSaveable(accountIdentity) { mutableStateOf<Long?>(null) }
    var pendingManagementAction by rememberSaveable(accountIdentity) { mutableStateOf<WatchLaterManagementAction?>(null) }
    var searchQuery by rememberSaveable(accountIdentity) {
        mutableStateOf(if (accountIdentity == initialAccountIdentity) initialSearchQuery else "")
    }
    val hideListTopSearchBar = com.android.purebilibili.feature.list.shouldHideListTopSearchBar(
        bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
        listScopedSearchEnabled = homeSettings.listScopedSearchEnabled,
        isSearchDestination = isSearchDestination,
    )
    val showListScopedSearchActiveBar =
        com.android.purebilibili.feature.list.shouldShowListScopedSearchActiveBar(
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            listScopedSearchEnabled = homeSettings.listScopedSearchEnabled,
            searchQuery = searchQuery,
        )
    LaunchedEffect(listScopedSearchChannel) {
        listScopedSearchChannel?.receiveAsFlow()?.collect { query ->
            searchQuery = query
        }
    }
    val displayedItems = state.items
    val filters = WatchLaterFilter.entries
    val filterGridStates = filters.associateWith { filter ->
        key(accountIdentity, filter) { rememberCompatibleStaggeredGridState() }
    }
    val gridState = filterGridStates.getValue(state.filter)
    val filterPagerState = key(accountIdentity) {
        rememberContentTabPager(
            pageCount = filters.size,
            selectedPage = filters.indexOf(state.filter),
            onPageSelected = { page ->
                if (!isBatchMode) viewModel.selectFilter(filters[page])
            },
            prepareNeighbors = { },
        )
    }

    // 双指缩放列数：换档震动 + HUD 胶囊提示
    val pinchScope = rememberCoroutineScope()
    val hapticFeedback = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.REFRESH)
    var pinchPillVisible by remember { mutableStateOf(false) }
    var pinchPillDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val onPinchColumnsChange: (Int) -> Unit = { newColumns ->
        gridColumnPreference.onChange(newColumns)
        hapticFeedback.performHapticFeedback(
            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
        )
        pinchPillVisible = true
        pinchPillDismissJob?.cancel()
    }
    val onPinchColumnsEnd: (Int) -> Unit = { finalColumns ->
        gridColumnPreference.onGestureEnd(finalColumns)
        pinchPillDismissJob?.cancel()
        pinchPillDismissJob = pinchScope.launch {
            kotlinx.coroutines.delay(1000)
            pinchPillVisible = false
        }
    }

    // 分类 tab 行：下滑折叠隐藏，上滑/回顶重新出现
    var watchLaterTabsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(gridState, watchLaterHeaderCollapseMode, scrollBehavior) {
        var lastFirstVisibleItem = 0
        var lastScrollOffset = 0
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (firstVisibleItem, scrollOffset) ->
            val isAtTop = firstVisibleItem == 0 && scrollOffset == 0
            if (watchLaterHeaderCollapseMode == com.android.purebilibili.core.store.CommonListHeaderCollapseMode.ALWAYS_VISIBLE || isAtTop) {
                watchLaterTabsVisible = true
            } else if (watchLaterHeaderCollapseMode == com.android.purebilibili.core.store.CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY) {
                watchLaterTabsVisible = false
            } else {
                val isScrollingDown = when {
                    firstVisibleItem > lastFirstVisibleItem -> true
                    firstVisibleItem < lastFirstVisibleItem -> false
                    else -> scrollOffset > lastScrollOffset + 50
                }
                val isScrollingUp = when {
                    firstVisibleItem < lastFirstVisibleItem -> true
                    firstVisibleItem > lastFirstVisibleItem -> false
                    else -> scrollOffset < lastScrollOffset - 50
                }
                if (isScrollingDown) watchLaterTabsVisible = false
                if (isScrollingUp) watchLaterTabsVisible = true
            }
            lastFirstVisibleItem = firstVisibleItem
            lastScrollOffset = scrollOffset
        }
    }

    LaunchedEffect(scrollToTopChannel) {
        scrollToTopChannel?.receiveAsFlow()?.collect {
            if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
                gridState.animateScrollToItem(0)
            }
        }
    }

    // 与推荐页共用滚动偏移与「列表正在滑」信号，驱动底栏搜索胶囊展开/收起。
    val setBottomBarVisible = com.android.purebilibili.core.ui.LocalSetBottomBarVisible.current
    val bottomBarChromeScrollOffset = com.android.purebilibili.feature.home.LocalHomeScrollOffset.current
    val globalFeedScrollInProgress = com.android.purebilibili.feature.home.LocalHomeFeedScrollInProgress.current
    val appNavigationSettings by SettingsManager.getAppNavigationSettings(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.AppNavigationSettings())
    val shouldAutoHideBottomBar = com.android.purebilibili.core.ui.shouldAutoHideBottomBarOnScroll(
        visibilityMode = appNavigationSettings.bottomBarVisibilityMode,
    )
    val liveWatchLaterBottomPadding = com.android.purebilibili.core.ui.LocalBottomBarContentPadding.current
    val isBottomBarVisibleForPadding = com.android.purebilibili.core.ui.LocalBottomBarVisible.current
    val watchLaterBottomPadding = com.android.purebilibili.core.ui.rememberStickyBottomBarContentPadding(
        autoHideEnabled = shouldAutoHideBottomBar,
        liveBottomPadding = liveWatchLaterBottomPadding,
        isBottomBarVisible = isBottomBarVisibleForPadding,
    )
    val bottomBarScrollHideConnection =
        com.android.purebilibili.core.ui.rememberBottomBarScrollHideConnection(
            chromeScrollOffset = bottomBarChromeScrollOffset,
            autoHideEnabled = shouldAutoHideBottomBar,
            isAtTop = {
                gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset <
                    com.android.purebilibili.core.ui.BottomBarScrollHideTopRevealPx
            },
            isActivePage = isCurrentPage,
            onVisibilityIntent = { intent ->
                when (intent) {
                    com.android.purebilibili.core.ui.BottomBarScrollHideIntent.SHOW ->
                        setBottomBarVisible(true)
                    com.android.purebilibili.core.ui.BottomBarScrollHideIntent.HIDE ->
                        setBottomBarVisible(false)
                }
            },
        )
    val isListScrollInProgress by remember(gridState) {
        derivedStateOf { gridState.isScrollInProgress }
    }
    val isWatchLaterAtTop by remember(gridState) {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }
    val watchLaterHeaderRevealSpec = AppMotionTokens.standardSpec<Float>()
    LaunchedEffect(
        scrollBehavior,
        watchLaterHeaderCollapseMode,
        isWatchLaterAtTop,
        isListScrollInProgress,
    ) {
        // 滚动结束后从当前位移展开；新的滚动会取消此动画，交还原生滚动行为。
        if (!isListScrollInProgress && (isWatchLaterAtTop ||
                watchLaterHeaderCollapseMode == com.android.purebilibili.core.store.CommonListHeaderCollapseMode.ALWAYS_VISIBLE)) {
            animate(
                initialValue = scrollBehavior.state.heightOffset,
                targetValue = 0f,
                animationSpec = watchLaterHeaderRevealSpec,
            ) { value, _ ->
                scrollBehavior.state.heightOffset = value
            }
        }
    }
    if (isCurrentPage) {
        SideEffect {
            globalFeedScrollInProgress.value = isListScrollInProgress
        }
    }
    DisposableEffect(isCurrentPage) {
        if (!isCurrentPage) {
            globalFeedScrollInProgress.value = false
            bottomBarChromeScrollOffset.value = 0f
        }
        onDispose {
            if (isCurrentPage) {
                globalFeedScrollInProgress.value = false
                bottomBarChromeScrollOffset.value = 0f
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            setBottomBarVisible(true)
            bottomBarChromeScrollOffset.value = 0f
        }
    }
    LaunchedEffect(shouldAutoHideBottomBar) {
        if (!shouldAutoHideBottomBar) {
            setBottomBarVisible(true)
            bottomBarChromeScrollOffset.value = 0f
        }
    }

    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(350)
        viewModel.updateQuery(searchQuery)
    }

    LaunchedEffect(state.items) {
        val valid = state.items.map { it.bvid }.toSet()
        selectedBvids = selectedBvids.filter { it in valid }.toSet()
        if (isBatchMode && state.items.isEmpty()) {
            isBatchMode = false
        }
        if (state.items.isEmpty()) {
            pendingManagementAction = null
        }
    }

    // PiliPlus：多选模式下返回键先退出多选
    androidx.activity.compose.BackHandler(enabled = isBatchMode) {
        isBatchMode = false
        selectedBvids = emptySet()
    }

    val batchActions: @Composable () -> Unit = {
        val allSelected = selectedBvids.size == state.items.size
        AppTextButton(
            onClick = {
                selectedBvids = if (allSelected) emptySet() else state.items.map { it.bvid }.toSet()
            }
        ) {
            AppText(if (allSelected) "取消全选" else "全选")
        }
        // PiliPlus：批量操作平铺为文字按钮，移除为红色
        AppTextButton(
            enabled = selectedBvids.isNotEmpty() && !state.isTransferLoading,
            onClick = {
                pendingTransferCopy = true
                selectedTransferFolderId = null
                viewModel.loadFavoriteFolders()
            },
        ) {
            AppText("复制")
        }
        AppTextButton(
            enabled = selectedBvids.isNotEmpty() && !state.isTransferLoading,
            onClick = {
                pendingTransferCopy = false
                selectedTransferFolderId = null
                viewModel.loadFavoriteFolders()
            },
        ) {
            AppText("移动")
        }
        AppTextButton(
            enabled = selectedBvids.isNotEmpty() && !state.isManaging,
            onClick = { showBatchDeleteConfirm = true },
        ) {
            AppText(
                "移除",
                color = AppContentColors.error(),
            )
        }
    }

    AppScaffold(
        modifier = Modifier
            .nestedScroll(bottomBarScrollHideConnection)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // 使用 Box 包裹实现毛玻璃背景
            BiliPaiImmersiveTopBar(
                backdrop = watchLaterChromeBackdrop,
                enabled = progressiveChromeActive,
                headerBlurActive = headerBlurActive,
                surfaceColor = AppSurfaceTokens.groupedListContainer(),
                fadeEnabled = fadeActive,
                extendBelowBounds = false,
                modifier = Modifier.fillMaxWidth().then(
                    if (progressiveChromeActive || fadeActive) {
                        Modifier.background(Color.Transparent)
                    } else if (headerBlurActive && hazeState != null) {
                        Modifier
                            .unifiedBlur(
                                hazeState = hazeState,
                                surfaceType = com.android.purebilibili.core.ui.blur.BlurSurfaceType.HEADER,
                            )
                            .background(
                                AppSurfaceTokens.cardContainer()
                                    .copy(alpha = AppSurfaceTokens.FrostedScrimAlpha)
                            )
                    } else {
                        Modifier.background(AppSurfaceTokens.groupedListContainer())
                    }
                ),
            ) {
                Column {
                AppTopBar(
                    title = if (isBatchMode) "已选: ${selectedBvids.size}" else "稍后再看",
                    navigationIcon = {
                        AppIconButton(
                            onClick = {
                                if (isBatchMode) {
                                    isBatchMode = false
                                    selectedBvids = emptySet()
                                } else {
                                    onBack()
                                }
                            }
                        ) {
                            AppIcon(
                                if (isBatchMode) Icons.Rounded.Close else rememberAppBackIcon(),
                                contentDescription = if (isBatchMode) "退出多选" else "返回",
                            )
                        }
                    },
                    actions = {
                        if (state.items.isNotEmpty() && !isBatchMode) {
                            AppTextButton(
                                modifier = Modifier.appElasticPress(),
                                onClick = { viewModel.updateSortOrder(state.sortOrder.toggled()) },
                            ) {
                                AppText(
                                    if (state.sortOrder == WatchLaterSortOrder.FORWARD) "最近添加" else "最早添加",
                                    tapToCopyEnabled = false,
                                )
                            }
                        }
                        if (state.items.isNotEmpty()) {
                            if (!isBatchMode) {
                                AppWindowActionMenu(
                                    enabled = !state.isManaging,
                                    groups = listOf(
                                        listOf(
                                            AppWindowAction(
                                                label = "管理",
                                                onClick = {
                                                    selectedBvids = emptySet()
                                                    isBatchMode = true
                                                },
                                            ),
                                            AppWindowAction(
                                                label = "全部听",
                                                enabled = !state.isManaging,
                                                onClick = {
                                                    val externalPlaylist = buildExternalPlaylistFromWatchLater(
                                                        items = displayedItems,
                                                        clickedBvid = displayedItems.firstOrNull()?.bvid
                                                    )
                                                    if (externalPlaylist != null) {
                                                        com.android.purebilibili.feature.video.player.PlaylistManager.setExternalPlaylist(
                                                            externalPlaylist.playlistItems,
                                                            externalPlaylist.startIndex,
                                                            source = com.android.purebilibili.feature.video.player.ExternalPlaylistSource.WATCH_LATER
                                                        )
                                                        com.android.purebilibili.feature.video.player.PlaylistManager
                                                            .setPlayMode(com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL)

                                                        resolveWatchLaterPlayAllStartTarget(displayedItems)?.let { target ->
                                                            val playbackTarget = resolveWatchLaterPlaybackTargetOrDefault(
                                                                items = displayedItems,
                                                                bvid = target.first,
                                                                fallbackCid = target.second
                                                            )
                                                            onPlayAllAudioClick?.invoke(
                                                                playbackTarget.bvid,
                                                                playbackTarget.cid,
                                                                playbackTarget.resumePositionMs
                                                            ) ?: onVideoClick(
                                                                playbackTarget.bvid,
                                                                playbackTarget.cid,
                                                                playbackTarget.resumePositionMs
                                                            )
                                                        }
                                                    }
                                                },
                                            ),
                                            AppWindowAction(
                                                label = "清除失效",
                                                enabled = !state.isManaging,
                                                onClick = {
                                                    pendingManagementAction = WatchLaterManagementAction.CLEAR_INVALID
                                                },
                                            ),
                                            AppWindowAction(
                                                label = "清空已看",
                                                enabled = !state.isManaging,
                                                onClick = {
                                                    pendingManagementAction = WatchLaterManagementAction.CLEAR_VIEWED
                                                },
                                            ),
                                            AppWindowAction(
                                                label = "清空全部",
                                                enabled = !state.isManaging,
                                                onClick = {
                                                    pendingManagementAction = WatchLaterManagementAction.CLEAR_ALL
                                                },
                                            ),
                                        ),
                                    ),
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "更多管理",
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = AppContentColors.primaryText(),
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
                val watchLaterFilterOptions = remember(state.filter, state.totalCount) {
                    WatchLaterFilter.entries.map { filter ->
                        AppSegmentOption(
                            value = filter,
                            label = if (filter == state.filter) {
                                "${filter.label}(${state.totalCount})"
                            } else {
                                filter.label
                            },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = watchLaterTabsVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        if (hideListTopSearchBar) {
                            if (showListScopedSearchActiveBar) {
                                com.android.purebilibili.feature.list.ListScopedSearchActiveBar(
                                    searchQuery = searchQuery,
                                    onClear = { searchQuery = "" },
                                    backdrop = watchLaterChromeBackdrop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AppSpacingTokens.Medium),
                                )
                            }
                        } else {
                            AppLiquidAwareSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = "搜索稍后再看",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacingTokens.Medium),
                                backdrop = watchLaterChromeBackdrop,
                            )
                        }
                        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                    }
                }
                AnimatedVisibility(
                    visible = watchLaterTabsVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    AppThemeAdaptiveTabRow(
                        // Glass indicator fills the measured slot; native geometry keeps its defaults.
                        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                        role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
                        options = watchLaterFilterOptions,
                        selectedValue = filters[filterPagerState.tabSelectedPage],
                        onSelectionChange = viewModel::selectFilter,
                        // 同一连续页位置驱动玻璃与原生指示器，拖动、回弹和点击切页保持同步。
                        indicatorPositionProvider = {
                            filterPagerState.tabIndicatorPosition
                        },
                        isScrollInProgressProvider = { filterPagerState.isScrollInProgress },
                        pagerTargetPositionProvider = { filterPagerState.targetPage.toFloat() },
                        enabled = !isBatchMode,
                        height = watchLaterFilterChrome.heightDp.dp,
                        indicatorHeight = watchLaterFilterChrome.indicatorHeightDp.dp,
                        labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                        dragSelectionEnabled = !isBatchMode,
                        tapPressRefractionEnabled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = watchLaterFilterChrome.horizontalPaddingDp.dp),
                        miuixBackdrop = watchLaterChromeBackdrop,
                    )
                }
                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                }
                // 分割线 (仅在滚动时显示? 这里简化一直显示细线或跟随滚动)
                // 暂时不加显式分割线，依靠毛玻璃效果
            }
        },
        containerColor = AppSurfaceTokens.groupedListContainer()
    ) { padding ->
        // 听视频小横条悬浮在内容上方时，列表上浮避让（与首页 76dp 预留一致）
        val nowPlayingBarOverlayVisible = com.android.purebilibili.core.ui
            .rememberNowPlayingBarOverlayVisible()
        val bottomOverlayPadding = watchLaterBottomPadding +
            if (nowPlayingBarOverlayVisible) {
                com.android.purebilibili.core.ui.NowPlayingBarOverlayAvoidancePadding
            } else {
                0.dp
            }
        val bottomContentPadding = bottomOverlayPadding + if (isBatchMode) 80.dp else 0.dp
        val pagerDensity = LocalDensity.current
        val activeState = state
        val parentVisualWorkAllowed = LocalVisualWorkAllowed.current
        val parentImageLoadingAllowed = LocalPageImageLoadingAllowed.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(watchLaterChromeSource?.modifier ?: Modifier)
                .then(
                    if (hazeState != null &&
                        com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current
                    ) {
                        Modifier.hazeSourceCompat(state = hazeState)
                    } else {
                        Modifier
                    }
                )
                .globalWallpaperAwareBackground(AppSurfaceTokens.groupedListContainer())
        ) {
            HorizontalPager(
                state = filterPagerState,
                key = { filters[it] },
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .contentTabPagerGestures(
                        pager = filterPagerState,
                        enabled = isCurrentPage && !isBatchMode,
                        contentTopPx = { with(pagerDensity) { padding.calculateTopPadding().toPx() } },
                    ),
            ) { page ->
                val pageFilter = filters[page]
                val state = if (pageFilter == activeState.filter) activeState else viewModel.previewFilter(pageFilter)
                val pageActive = isCurrentPage && pageFilter == activeState.filter && page == filterPagerState.settledPage
                val gridState = filterGridStates.getValue(pageFilter)
                val displayedItems = state.items
                val renderKeys = remember(displayedItems) {
                    resolveCommonListRenderKeys(displayedItems.map { item ->
                        "watch_later_video_${item.bvid.ifBlank { "id_${item.id}" }}"
                    })
                }
                CompositionLocalProvider(
                    LocalVisualWorkAllowed provides (parentVisualWorkAllowed && pageActive && !filterPagerState.isScrollInProgress),
                    LocalPageImageLoadingAllowed provides (parentImageLoadingAllowed && isCurrentPage &&
                        (page == filterPagerState.currentPage || page == filterPagerState.targetPage)),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        when {
                            state.isLoading -> {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val skeletonColumns = pinchListColumns
                                    val skeletonBlockColor = com.android.purebilibili.core.ui.skeleton
                                        .rememberContentSkeletonBlockColor(
                                            com.android.purebilibili.core.ui.skeleton.rememberSkeletonSweepState()
                                        )
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(skeletonColumns),
                                        contentPadding = PaddingValues(
                                            start = AppSpacingTokens.Medium,
                                            end = AppSpacingTokens.Medium,
                                            top = padding.calculateTopPadding() + AppSpacingTokens.Small,
                                            bottom = bottomContentPadding,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                                        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                                        userScrollEnabled = false,
                                        modifier = Modifier
                                            .then(if (skeletonColumns == 1) Modifier.responsiveContentWidth(com.android.purebilibili.core.util.resolveSingleColumnFeedMaxWidth()) else Modifier)
                                            .fillMaxSize(),
                                    ) {
                                        items(skeletonColumns * 6) {
                                            PersonalMediaCardSkeleton(
                                                blockColor = skeletonBlockColor,
                                                stacked = skeletonColumns > 1,
                                                horizontalCoverMaxWidthDp = com.android.purebilibili.feature.home.components.cards.WIDE_LIST_COVER_MAX_WIDTH_DP,
                                            )
                                        }
                                    }
                                }
                            }
                            state.error != null -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AppText(
                                        text = state.error ?: "未知错误",
                                        color = AppContentColors.error()
                                    )
                                    Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                                    AppOutlinedButton(enabled = pageActive, onClick = { viewModel.loadData() }) {
                                        AppText("重试")
                                    }
                                }
                            }
                            state.items.isEmpty() -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AppIcon(
                                        rememberAppWatchLaterIcon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large),
                                        tint = AppContentColors.secondaryText().copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                                    AppText(
                                        text = if (searchQuery.isBlank()) "稍后再看列表为空" else "未找到相关视频",
                                        color = AppContentColors.secondaryText()
                                    )
                                }
                            }
                            else -> {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val columns = pinchListColumns
                                    val showFullCardContent = com.android.purebilibili.core.ui.LocalFullVideoCardContentVisible.current
                                    val alignRows = columns > 1 && !showFullCardContent
                                    AppLazyVerticalStaggeredGrid(
                                        state = gridState,
                                        columns = StaggeredGridCells.Fixed(columns),
                                        contentPadding = PaddingValues(
                                            start = AppSpacingTokens.Medium,
                                            end = AppSpacingTokens.Medium,
                                            top = padding.calculateTopPadding() + AppSpacingTokens.Small,
                                            bottom = bottomContentPadding,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                                        verticalItemSpacing = AppSpacingTokens.Medium,
                                        modifier = Modifier
                                            .then(if (columns == 1) Modifier.responsiveContentWidth(com.android.purebilibili.core.util.resolveSingleColumnFeedMaxWidth()) else Modifier)
                                            .fillMaxSize()
                                            .homeFeedPinchZoom(
                                                enabled = homeSettings.gridColumnPinchEnabled && !isBatchMode,
                                                currentColumns = columns,
                                                bounds = pinchColumnBounds,
                                                onColumnsChange = onPinchColumnsChange,
                                                onGestureEnd = onPinchColumnsEnd,
                                            ),
                                    ) {
                                        val renderCard: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                                            val item = displayedItems[index]
                                            Box(modifier = cardModifier) {
                                                if (pageActive && index == displayedItems.lastIndex && state.hasMore && !state.isLoadingMore) {
                                                    LaunchedEffect(state.page, displayedItems.size) { viewModel.loadMore() }
                                                }
                                                val isDissolving = item.bvid in state.dissolvingIds
                                                val isSelected = item.bvid in selectedBvids

                                                MaybeDissolvableVideoCard(
                                                    isDissolving = isDissolving,
                                                    onDissolveComplete = { viewModel.completeVideoDissolve(item.bvid) },
                                                    cardId = item.bvid,
                                                    preset = DissolveAnimationPreset.TELEGRAM_FAST,
                                                ) {
                                                    WatchLaterVideoCard(
                                                        item = item,
                                                        modifier = if (alignRows) Modifier.fillMaxHeight() else Modifier,
                                                        stacked = columns > 1,
                                                        enabled = pageActive && !filterPagerState.isScrollInProgress,
                                                        isBatchMode = isBatchMode,
                                                        isSelected = isSelected,
                                                        transitionEnabled = pageActive && homeSettings.cardTransitionEnabled,
                                                        onRemove = { viewModel.startVideoDissolve(item.bvid) },
                                                        onLongClick = {
                                                            isBatchMode = true
                                                            selectedBvids = selectedBvids + item.bvid
                                                        },
                                                        onClick = {
                                                            if (isBatchMode) {
                                                                selectedBvids = if (item.bvid in selectedBvids) {
                                                                    selectedBvids - item.bvid
                                                                } else {
                                                                    selectedBvids + item.bvid
                                                                }
                                                            } else {
                                                                val externalPlaylist = buildExternalPlaylistFromWatchLater(
                                                                    items = displayedItems,
                                                                    clickedBvid = item.bvid
                                                                )
                                                                if (externalPlaylist != null) {
                                                                    com.android.purebilibili.feature.video.player.PlaylistManager.setExternalPlaylist(
                                                                        externalPlaylist.playlistItems,
                                                                        externalPlaylist.startIndex,
                                                                        source = com.android.purebilibili.feature.video.player.ExternalPlaylistSource.WATCH_LATER
                                                                    )
                                                                    com.android.purebilibili.feature.video.player.PlaylistManager
                                                                        .setPlayMode(com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL)
                                                                }

                                                                val target = resolveWatchLaterPlaybackTargetOrDefault(
                                                                    items = displayedItems,
                                                                    bvid = item.bvid,
                                                                    fallbackCid = item.cid
                                                                )
                                                                onVideoClick(target.bvid, target.cid, target.resumePositionMs)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (!alignRows) {
                                            itemsIndexed(displayedItems, key = { index, _ -> renderKeys[index] }) { index, _ ->
                                                renderCard(index, videoListItemModifier(enabled = homeSettings.cardAnimationEnabled))
                                            }
                                        } else {
                                            com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(displayedItems.size, columns)
                                                .forEach { row ->
                                                    item(
                                                        key = "watch_later_row_${renderKeys[row.first]}",
                                                        span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine,
                                                    ) {
                                                        com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                                            indices = row,
                                                            itemKeys = renderKeys,
                                                            columns = columns,
                                                            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                                                            modifier = videoListItemModifier(enabled = homeSettings.cardAnimationEnabled).fillMaxWidth(),
                                                            content = renderCard,
                                                        )
                                                    }
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    } // 每个筛选的内容
                } // 后台页暂停视觉任务
            }
            GridPinchColumnHudPill(
                visible = pinchPillVisible,
                columns = pinchListColumns,
                modifier = Modifier.align(Alignment.Center),
            )

            if (isBatchMode) {
                AppFloatingToolbar(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomOverlayPadding + 12.dp),
                ) {
                    batchActions()
                }
            }

        }
    }

    if (showBatchDeleteConfirm) {
        AppAlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { AppText("批量删除") },
            text = { AppText("确认删除已选择的 ${selectedBvids.size} 个视频吗？") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        val aidList = state.items
                            .filter { it.bvid in selectedBvids }
                            .map { it.id }
                        viewModel.deleteItems(aidList)
                        selectedBvids = emptySet()
                        isBatchMode = false
                        showBatchDeleteConfirm = false
                    }
                ) {
                    AppText("删除")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showBatchDeleteConfirm = false }) {
                    AppText("取消")
                }
            }
        )
    }

    pendingTransferCopy?.let { copy ->
        AppAlertDialog(
            onDismissRequest = { pendingTransferCopy = null },
            title = { AppText(if (copy) "复制到收藏夹" else "移动到收藏夹") },
            text = {
                if (state.isTransferLoading && state.favoriteFolders.isEmpty()) {
                    AppText("正在加载收藏夹…")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(state.favoriteFolders, key = { it.id }) { folder ->
                            AppSingleChoiceRow(
                                selected = selectedTransferFolderId == folder.id,
                                onClick = { selectedTransferFolderId = folder.id },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AppText(folder.title, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = selectedTransferFolderId != null && !state.isTransferLoading,
                    onClick = {
                        val aids = state.items
                            .filter { it.bvid in selectedBvids }
                            .map { it.aid }
                            .filter { it > 0L }
                            .toSet()
                        selectedTransferFolderId?.let { folderId ->
                            viewModel.copyOrMoveToFavorite(aids, folderId, copy)
                        }
                        pendingTransferCopy = null
                        selectedBvids = emptySet()
                        isBatchMode = false
                    },
                ) { AppText("确认") }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingTransferCopy = null }) { AppText("取消") }
            },
        )
    }

    pendingManagementAction?.let { action ->
        val affectedCount = remember(action, state.items) {
            state.items.size - resolveWatchLaterItemsAfterManagementAction(
                items = state.items,
                action = action
            ).size
        }
        AppAlertDialog(
            onDismissRequest = { pendingManagementAction = null },
            title = {
                AppText(
                    when (action) {
                        WatchLaterManagementAction.CLEAR_INVALID -> "清除失效"
                        WatchLaterManagementAction.CLEAR_VIEWED -> "清空已看"
                        WatchLaterManagementAction.CLEAR_ALL -> "清空全部"
                    }
                )
            },
            text = { AppText(resolveWatchLaterManagementConfirmText(action, affectedCount)) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        viewModel.runManagementAction(action)
                        pendingManagementAction = null
                    }
                ) {
                    AppText("确认")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingManagementAction = null }) {
                    AppText("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WatchLaterVideoCard(
    item: VideoItem,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    enabled: Boolean = true,
    isBatchMode: Boolean,
    isSelected: Boolean,
    transitionEnabled: Boolean,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val sponsorStatusLabel = rememberSponsorFeedBadgeLabel(item.bvid)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val cardBoundsRef = remember { object { var value: androidx.compose.ui.geometry.Rect? = null } }
    val coverBoundsRef = remember { object { var value: androidx.compose.ui.geometry.Rect? = null } }
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(item.bvid)
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedElementReady = transitionEnabled &&
        item.bvid.isNotBlank() &&
        sourceRoute != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val sharedTransitionMotionSpec = remember(
        sourceRoute,
        transitionEnabled,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = transitionEnabled,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val stationaryCoverUrl = remember(item.pic) { fixCoverUrl(item.pic) }
    val stationaryCoverRequest = remember(stationaryCoverUrl) {
        ImageRequest.Builder(context)
            .data(stationaryCoverUrl)
            .crossfade(false)
            .memoryCacheKey(stationaryCoverUrl)
            .diskCacheKey(stationaryCoverUrl)
            .build()
    }
    val cardCornerRadiusDp = AppShapes.containerCornerDp(ContainerLevel.Card).value.toInt()
    val cardClick = watchLaterCardClick@{
        if (!isBatchMode) {
            if (CardPositionManager.isVideoNavigationDebounceActive()) {
                // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
                return@watchLaterCardClick
            }
            cardBoundsRef.value?.let { bounds ->
                val sourceCoverBounds = coverBoundsRef.value
                CardPositionManager.recordVideoCardPosition(
                    bvid = item.bvid,
                    sourceRoute = sourceRoute,
                    bounds = bounds,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    sourceCornerDp = cardCornerRadiusDp,
                    coverBounds = sourceCoverBounds,
                    sourceLayout = if (stacked) VideoCardSourceLayout.STACKED else VideoCardSourceLayout.SIDE_BY_SIDE,
                    sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                        title = item.title,
                        ownerName = item.owner.name.ifBlank { "未知UP主" },
                        ownerFaceUrl = item.owner.face,
                        viewText = formatNumber(item.stat.view),
                        danmakuText = formatNumber(item.stat.danmaku),
                        durationText = formatDuration(item.duration),
                        infoPresentation = com.android.purebilibili.core.ui.transition
                            .resolveVideoCardSourceInfoPresentation(
                                publishTimeText = "",
                                showStatsInInfo = true,
                                showOverflowMenu = false,
                            ),
                        coverPresentation = VideoCardSourceCoverPresentation(
                            showDurationOnCover = true,
                            showHistoryProgressBar = item.duration > 0 && item.progress > 0,
                            historyProgressFraction = if (item.duration > 0) {
                                (item.progress.toFloat() / item.duration).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                        ),
                        coverUrl = stationaryCoverUrl,
                        coverCacheKey = stationaryCoverUrl,
                    ).withMeasuredCoverDecodeSize(sourceCoverBounds),
                )
                nativeCardSnapshot.capture()
            }
        }
        onClick()
    }
    val useCardShellSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRoute,
        transitionEnabled = sharedElementReady
    )
    val cardShellShape = AppShapes.container(ContainerLevel.Card)

    PersonalMediaCardFrame(
        stacked = stacked,
        stackedSupportingContentAtBottom = stacked &&
            !com.android.purebilibili.core.ui.LocalFullVideoCardContentVisible.current,
        horizontalCoverMaxWidthDp = com.android.purebilibili.feature.home.components.cards.WIDE_LIST_COVER_MAX_WIDTH_DP,
        enabled = enabled,
        coverModifier = Modifier.onGloballyPositioned { coordinates ->
            coverBoundsRef.value = coordinates.videoCardSourceBoundsInRoot()
        },
        selected = isSelected,
        modifier = modifier
            .videoCardSourceAnchor(item.bvid, cardShellShape)
            .onGloballyPositioned { coordinates ->
                cardBoundsRef.value = coordinates.videoCardSourceBoundsInRoot()
            },
        nativeSnapshotModifier = nativeCardSnapshot.modifier,
        coverOverlayModifier = nativeCardSnapshot.coverOverlayModifier,
        headlineContent = {
            AppText(
                text = item.title,
                style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().title,
                maxLines = videoCardTitleMaxLines(),
                overflow = videoCardTitleOverflow(),
                color = AppContentColors.primaryText(),
            )
        },
        overlineContent = item.contentType.takeIf(String::isNotBlank)?.let { badges ->
            {
                AppText(
                    badges,
                    style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().statistic,
                    color = AppContentColors.accent(),
                    overflow = TextOverflow.Visible,
                )
            }
        },
        supportingContent = {
            Column(
                modifier = Modifier.videoCardShellReturnChromeAlpha(
                    enabled = useCardShellSharedBounds,
                    bvid = item.bvid,
                    sourceRoute = sourceRoute,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
            ) {
                AppText(
                    text = item.owner.name,
                    style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().author,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalVideoStatRow(
                        playText = formatNumber(item.stat.view),
                        danmakuText = formatNumber(item.stat.danmaku),
                        modifier = Modifier.weight(1f),
                    )
                    if (!isBatchMode) {
                        AppIconButton(
                            onClick = onRemove,
                            enabled = enabled,
                            modifier = Modifier.size(32.dp),
                        ) {
                            AppIcon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "从稍后再看移除",
                                modifier = Modifier.size(20.dp),
                                tint = AppContentColors.secondaryText(),
                            )
                        }
                    }
                }
            }
        },
        coverContent = {
            PageAwareAsyncImage(
                model = stationaryCoverRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        coverOverlayContent = {
            VideoStatusBadges(
                labels = item.statusLabels + listOfNotNull(sponsorStatusLabel),
                modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacingTokens.ExtraSmall),
            )
            val progressState = com.android.purebilibili.data.model.resolveWatchLaterDisplayProgressState(item)
            VideoCardCoverDurationText(
                text = if (progressState.progressSec == -1) "已看完" else formatDuration(item.duration),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppSpacingTokens.ExtraSmall),
            )
            if (progressState.showProgressBar) {
                AppLinearProgressIndicator(
                    progress = { progressState.progressFraction },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color = AppContentColors.accent(),
                    trackColor = Color.Transparent,
                )
            }
            com.android.purebilibili.feature.personal.PersonalCardSelectMask(selected = isSelected)
        },
        onClick = cardClick,
        onLongClick = onLongClick,
    )
}
