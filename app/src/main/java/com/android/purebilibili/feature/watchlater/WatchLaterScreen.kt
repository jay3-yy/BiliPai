// 文件路径: feature/watchlater/WatchLaterScreen.kt
package com.android.purebilibili.feature.watchlater

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.animation.DissolvableVideoCard
import com.android.purebilibili.core.ui.animation.jiggleOnDissolve
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.android.purebilibili.core.ui.blur.unifiedBlur
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.purebilibili.core.coroutines.AppScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.components.IOSSearchBar
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.feature.list.resolveDeleteBatchParallelism
import com.android.purebilibili.feature.video.controller.PlaybackProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import com.android.purebilibili.core.util.FormatUtils

// 辅助函数：格式化时长
private fun formatDuration(seconds: Int): String {
    return FormatUtils.formatDuration(seconds)
}

// 辅助函数：格式化数字
private fun formatNumber(num: Int): String {
    return when {
        num >= 10000 -> String.format("%.1f万", num / 10000f)
        else -> num.toString()
    }
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

/**
 * 稍后再看 UI 状态
 */
data class WatchLaterUiState(
    val items: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val dissolvingIds: Set<String> = emptySet() // [新增] 用于已播放 Thanos Snap 动画的卡片
)

/**
 * 稍后再看 ViewModel
 */
class WatchLaterViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(WatchLaterUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val api = NetworkModule.api
                val response = api.getWatchLaterList()
                if (response.code == 0 && response.data != null) {
                    val items = response.data.list?.map { item ->
                        val rawProgress = item.progress ?: -1
                        val normalizedViewAt = when {
                            rawProgress == -1 && (item.view_at ?: 0L) <= 0L -> 1L
                            else -> item.view_at ?: 0L
                        }
                        VideoItem(
                            id = item.aid,  // 存储 aid 用于删除
                            bvid = item.bvid ?: "",
                            cid = item.cid ?: 0L,
                            title = item.title ?: "",
                            pic = item.pic ?: "",
                            duration = item.duration ?: 0,
                            progress = rawProgress,
                            view_at = normalizedViewAt,
                            owner = Owner(
                                mid = item.owner?.mid ?: 0L,
                                name = item.owner?.name ?: "",
                                face = item.owner?.face ?: ""
                            ),
                            stat = Stat(
                                view = item.stat?.view ?: 0,
                                danmaku = item.stat?.danmaku ?: 0,
                                reply = item.stat?.reply ?: 0,
                                like = item.stat?.like ?: 0,
                                coin = item.stat?.coin ?: 0,
                                favorite = item.stat?.favorite ?: 0,
                                share = item.stat?.share ?: 0
                            ),
                            pubdate = item.pubdate ?: 0L
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(isLoading = false, items = items)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = response.message ?: "加载失败")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "加载失败")
            }
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
        // 先从 UI 状态移除 ID（动画结束），然后调用删除逻辑
        _uiState.value = _uiState.value.copy(
            dissolvingIds = _uiState.value.dissolvingIds - bvid
        )
        // 查找对应的 aid 进行删除
        val item = _uiState.value.items.find { it.bvid == bvid }
        item?.let { deleteItem(it.id) }
    }

    /**
     * 从稍后再看删除视频
     */
    fun deleteItem(aid: Long) {
        // 乐观更新：直接从列表中移除，不需要重新请求
        val currentList = _uiState.value.items
        val newList = currentList.filter { it.id != aid }
        val removedBvid = currentList.firstOrNull { it.id == aid }?.bvid
        _uiState.value = _uiState.value.copy(
            items = newList,
            dissolvingIds = if (removedBvid == null) {
                _uiState.value.dissolvingIds
            } else {
                _uiState.value.dissolvingIds - removedBvid
            }
        )

        viewModelScope.launch {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    _uiState.value = _uiState.value.copy(items = currentList)
                    android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val result = deleteWatchLaterAidWithRetry(aid = aid, csrf = csrf)
                if (result.isSuccess) {
                    android.widget.Toast.makeText(getApplication(), "已从稍后再看移除", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    _uiState.value = _uiState.value.copy(items = currentList)
                    android.widget.Toast.makeText(
                        getApplication(),
                        "移除失败: ${result.exceptionOrNull()?.message ?: "请稍后重试"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(items = currentList)
                android.widget.Toast.makeText(getApplication(), "移除失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteItems(aids: List<Long>) {
        if (aids.isEmpty()) return
        val aidSet = aids.toSet()
        val snapshot = _uiState.value.items
        _uiState.value = _uiState.value.copy(
            items = snapshot.filterNot { it.id in aidSet },
            dissolvingIds = _uiState.value.dissolvingIds - snapshot.filter { it.id in aidSet }.map { it.bvid }.toSet()
        )

        AppScope.ioScope.launch {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache ?: ""
                if (csrf.isEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        _uiState.value = _uiState.value.copy(items = snapshot)
                        android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val successIds = deleteWatchLaterItemsInBackground(aids = aids, csrf = csrf)

                withContext(Dispatchers.Main.immediate) {
                    val successCount = successIds.size
                    _uiState.value = _uiState.value.copy(
                        items = snapshot.filterNot { it.id in successIds },
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
                e.printStackTrace()
                withContext(Dispatchers.Main.immediate) {
                    _uiState.value = _uiState.value.copy(items = snapshot)
                    android.widget.Toast.makeText(getApplication(), "批量删除失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun deleteWatchLaterItemsInBackground(
        aids: List<Long>,
        csrf: String
    ): Set<Long> = supervisorScope {
        val semaphore = Semaphore(resolveDeleteBatchParallelism(aids.size))
        aids.map { aid ->
            async {
                semaphore.withPermit {
                    if (deleteWatchLaterAidWithRetry(aid = aid, csrf = csrf).isSuccess) {
                        aid
                    } else {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    private suspend fun deleteWatchLaterAidWithRetry(
        aid: Long,
        csrf: String
    ): Result<Unit> {
        val api = NetworkModule.api
        repeat(WATCH_LATER_DELETE_MAX_ATTEMPTS) { attempt ->
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
    onVideoClick: (String, Long) -> Unit,
    onPlayAllAudioClick: ((String, Long) -> Unit)? = null,
    viewModel: WatchLaterViewModel = viewModel(),
    globalHazeState: HazeState? = null // [新增]
) {
    val state by viewModel.uiState.collectAsState()
    val hazeState = rememberRecoverableHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val showOnlineCount by SettingsManager.getShowOnlineCount(context).collectAsState(initial = false)
    val windowSizeClass = LocalWindowSizeClass.current
    val cardAnimationEnabled by SettingsManager.getCardAnimationEnabled(context).collectAsState(initial = true)
    val cardTransitionEnabled by SettingsManager.getCardTransitionEnabled(context).collectAsState(initial = true)
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardMotionTier = resolveEffectiveMotionTier(
        baseTier = deviceUiProfile.motionTier,
        animationEnabled = cardAnimationEnabled
    )
    val playbackProgressManager = remember(context) {
        PlaybackProgressManager.getInstance(context)
    }
    var isBatchMode by rememberSaveable { mutableStateOf(false) }
    var selectedBvids by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showBatchDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredItems = remember(state.items, searchQuery) {
        filterWatchLaterItemsByQuery(
            items = state.items,
            query = searchQuery
        )
    }
    val actionItems = remember(state.items, filteredItems, searchQuery) {
        if (searchQuery.isBlank()) state.items else filteredItems
    }

    LaunchedEffect(state.items) {
        val valid = state.items.map { it.bvid }.toSet()
        selectedBvids = selectedBvids.filter { it in valid }.toSet()
        if (isBatchMode && state.items.isEmpty()) {
            isBatchMode = false
        }
    }

    AdaptiveScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // 使用 Box 包裹实现毛玻璃背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .unifiedBlur(hazeState)
            ) {
                AdaptiveTopAppBar(
                    title = "稍后再看",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(rememberAppBackIcon(), contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (state.items.isNotEmpty()) {
                            if (isBatchMode) {
                                val allSelected = actionItems.isNotEmpty() &&
                                    actionItems.all { it.bvid in selectedBvids }
                                TextButton(
                                    onClick = {
                                        selectedBvids = if (allSelected) {
                                            selectedBvids - actionItems.map { it.bvid }.toSet()
                                        } else {
                                            selectedBvids + actionItems.map { it.bvid }.toSet()
                                        }
                                    }
                                ) {
                                    Text(if (allSelected) "取消全选" else "全选")
                                }
                                TextButton(
                                    enabled = selectedBvids.isNotEmpty(),
                                    onClick = { showBatchDeleteConfirm = true }
                                ) {
                                    Text("删除(${selectedBvids.size})")
                                }
                                TextButton(
                                    onClick = {
                                        isBatchMode = false
                                        selectedBvids = emptySet()
                                    }
                                ) {
                                    Text("完成")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        val externalPlaylist = buildExternalPlaylistFromWatchLater(
                                            items = actionItems,
                                            clickedBvid = actionItems.firstOrNull()?.bvid
                                        ) ?: return@IconButton

                                        com.android.purebilibili.feature.video.player.PlaylistManager.setExternalPlaylist(
                                            externalPlaylist.playlistItems,
                                            externalPlaylist.startIndex,
                                            source = com.android.purebilibili.feature.video.player.ExternalPlaylistSource.WATCH_LATER
                                        )
                                        com.android.purebilibili.feature.video.player.PlaylistManager
                                            .setPlayMode(com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL)

                                        onVideoClick(
                                            actionItems[externalPlaylist.startIndex].bvid,
                                            actionItems[externalPlaylist.startIndex].cid
                                        )
                                    }
                                ) {
                                    Icon(
                                        CupertinoIcons.Filled.Play,
                                        contentDescription = "全部播放",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val externalPlaylist = buildExternalPlaylistFromWatchLater(
                                            items = actionItems,
                                            clickedBvid = actionItems.firstOrNull()?.bvid
                                        ) ?: return@IconButton

                                        com.android.purebilibili.feature.video.player.PlaylistManager.setExternalPlaylist(
                                            externalPlaylist.playlistItems,
                                            externalPlaylist.startIndex,
                                            source = com.android.purebilibili.feature.video.player.ExternalPlaylistSource.WATCH_LATER
                                        )
                                        com.android.purebilibili.feature.video.player.PlaylistManager
                                            .setPlayMode(com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL)

                                        val target = resolveWatchLaterPlayAllStartTarget(actionItems)
                                            ?: return@IconButton
                                        onPlayAllAudioClick?.invoke(target.first, target.second)
                                            ?: onVideoClick(target.first, target.second)
                                    }
                                ) {
                                    Icon(
                                        CupertinoIcons.Outlined.Headphones,
                                        contentDescription = "全部听",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        isBatchMode = true
                                        selectedBvids = emptySet()
                                    }
                                ) {
                                    Text("批量删除")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
                
                // 分割线 (仅在滚动时显示? 这里简化一直显示细线或跟随滚动)
                // 暂时不加显式分割线，依靠毛玻璃效果
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState) // 内容作为模糊源（全局源由根层提供）
        ) {
            when {
                state.isLoading -> {
                    com.android.purebilibili.core.ui.CutePersonLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error ?: "未知错误",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.loadData() }) {
                            Text("重试")
                        }
                    }
                }
                state.items.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            CupertinoIcons.Default.Clock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "稍后再看列表为空",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                filteredItems.isEmpty() && searchQuery.isNotBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = padding.calculateTopPadding() + 12.dp,
                                bottom = padding.calculateBottomPadding()
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IOSSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "搜索稍后再看",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "没有找到相关视频",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("清空搜索")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                else -> {
                    // 计算合适的列数
                    val minColWidth = if (windowSizeClass.isExpandedScreen) 240.dp else 170.dp
                    
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minColWidth), // 使用 Adaptive 自适应列宽
                        contentPadding = PaddingValues(
                            start = 8.dp, 
                            end = 8.dp, 
                            top = padding.calculateTopPadding() + 8.dp, 
                            bottom = padding.calculateBottomPadding() + 8.dp + 80.dp // [新增] 底部Padding
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            IOSSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = "搜索稍后再看",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        itemsIndexed(
                            items = filteredItems,
                            key = { _, item -> item.bvid } 
                        ) { index, item ->
                            val isDissolving = item.bvid in state.dissolvingIds
                            val isSelected = item.bvid in selectedBvids
                            val localPositionMs = remember(item.bvid, item.cid) {
                                if (item.bvid.isBlank()) {
                                    0L
                                } else {
                                    playbackProgressManager.getCachedPosition(item.bvid, item.cid)
                                }
                            }
                            val playbackVisualState = remember(
                                item.bvid,
                                item.cid,
                                item.duration,
                                item.progress,
                                item.view_at,
                                localPositionMs
                            ) {
                                resolveWatchLaterPlaybackVisualState(
                                    video = item,
                                    localPositionMs = localPositionMs
                                )
                            }
                            
                            DissolvableVideoCard(
                                isDissolving = isDissolving,
                                onDissolveComplete = { viewModel.completeVideoDissolve(item.bvid) },
                                cardId = item.bvid,
                                preset = DissolveAnimationPreset.TELEGRAM_FAST,
                                modifier = Modifier.jiggleOnDissolve(
                                    cardId = item.bvid,
                                    isCurrentCardDissolving = isDissolving
                                )
                            ) {
                                Box {
                                    ElegantVideoCard(
                                        video = item,
                                        index = index,
                                        animationEnabled = cardAnimationEnabled,
                                        motionTier = cardMotionTier,
                                        transitionEnabled = cardTransitionEnabled,
                                        showPublishTime = true,
                                        showOnlineCount = showOnlineCount,
                                        durationBadgeTextOverride = playbackVisualState.durationBadgeText,
                                        dismissMenuText = "\uD83D\uDDD1\uFE0F 删除",
                                        // 触发 Thanos 响指动画 (开始消散)
                                        onDismiss = if (isBatchMode) null else ({ viewModel.startVideoDissolve(item.bvid) }),
                                        onClick = { bvid, _ ->
                                            if (isBatchMode) {
                                                selectedBvids = if (bvid in selectedBvids) {
                                                    selectedBvids - bvid
                                                } else {
                                                    selectedBvids + bvid
                                                }
                                            } else {
                                                val externalPlaylist = buildExternalPlaylistFromWatchLater(
                                                    items = state.items,
                                                    clickedBvid = bvid
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

                                                onVideoClick(bvid, item.cid)
                                            }
                                        }
                                    )
                                    if (isBatchMode) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .background(
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                        )
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = if (isSelected) "已选择" else "未选择",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
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

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量删除") },
            text = { Text("确认删除已选择的 ${selectedBvids.size} 个视频吗？") },
            confirmButton = {
                TextButton(
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
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WatchLaterVideoCard(
    item: VideoItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = fixCoverUrl(item.pic),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 时长
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(item.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        
        // 信息
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = item.owner?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "${formatNumber(item.stat?.view ?: 0)}播放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
