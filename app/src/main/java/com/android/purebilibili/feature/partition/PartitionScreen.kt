// 文件路径: feature/partition/PartitionScreen.kt
package com.android.purebilibili.feature.partition

import com.android.purebilibili.feature.home.collectAsHomeFeedState

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.feature.home.LocalHomeWallpaperBackdrop

import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.platform.LocalViewConfiguration
import com.android.purebilibili.core.ui.AppActionHaptics
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.home.components.LiquidDockHapticPolicy
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.theme.LocalAppUiStyle

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import com.android.purebilibili.feature.home.components.miuix.DampedDragTrackingMode
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.skeleton.ContentMediaListSkeleton
import com.android.purebilibili.core.util.resolveReplaceRefreshPage
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.rememberAppChromeLiquidGlassEnabled
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.data.model.response.BangumiType
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
import com.android.purebilibili.feature.home.components.cards.HomeStyleSingleColumnVideoCard
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockCaptureSurface
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.resolveAndroidNativeBottomBarTuning
import com.android.purebilibili.feature.home.components.resolveAndroidNativeFloatingBottomBarContainerColor
import com.android.purebilibili.feature.home.components.BottomBarIndicatorLayerTransform
import com.android.purebilibili.feature.home.components.FloatingBottomBarGeometryMode
import com.android.purebilibili.feature.home.components.resolveFloatingDockIndicatorHeightDp
import com.android.purebilibili.feature.home.components.resolveFloatingDockIndicatorGeometry
import com.android.purebilibili.feature.home.components.resolveFloatingDockIndicatorCornerRadiusDp
import com.android.purebilibili.feature.home.components.BottomBarLiquidOrientation
import com.android.purebilibili.feature.home.components.BottomBarMatchedLiquidIndicator
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.bottomBarMatchedCaptureOverflow
import com.android.purebilibili.feature.home.components.miuix.DampedDragAnimation
import com.android.purebilibili.feature.home.components.miuix.InteractiveHighlight
import com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop
import com.android.purebilibili.feature.home.components.resolveAndroidNativeExportTintColor
import com.android.purebilibili.feature.home.components.resolveAndroidNativeIdleIndicatorSurfaceColor
import com.android.purebilibili.feature.home.components.resolveSharedLiquidExportMonochromeColor
import com.android.purebilibili.feature.home.components.resolveBottomBarBackdropPresetIndicatorLens
import com.android.purebilibili.feature.home.components.resolveBottomBarCaptureSafeInsetDp
import com.android.purebilibili.feature.home.components.resolveBottomBarBackdropPresetProgress
import com.android.purebilibili.feature.home.components.resolveBottomBarIndicatorGlowAlpha
import com.android.purebilibili.feature.home.components.resolveBottomBarLiquidGlassHighlightAlpha
import com.android.purebilibili.feature.home.components.resolveBottomBarRefractionMotionProfile
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import com.android.purebilibili.feature.home.components.rememberBottomBarIndicatorLayerScaleTransform
import com.android.purebilibili.feature.home.components.normalizeTopTabLabelMode
import com.android.purebilibili.feature.home.components.resolveSegmentedControlMotionProgress
import com.android.purebilibili.feature.home.components.resolveSegmentedControlMotionSpec
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorGeometry
import com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorHeightDp
import com.android.purebilibili.feature.home.components.shouldShowTopTabIcon
import com.android.purebilibili.feature.home.components.shouldShowTopTabText
import com.android.purebilibili.feature.home.components.HomeSelectionIndicatorStyle
import com.android.purebilibili.feature.home.components.resolveHomeSelectionIndicatorStyle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.blur.unifiedBlur
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 *  分区数据类
 */
data class PartitionCategory(
    val id: Int,
    val name: String
)

/**
 *  所有分区列表 (参考官方 Bilibili API)
 * tid 是 Bilibili 官方的分区 ID，用于 x/web-interface/newlist 接口
 * 注意：番剧/国创/电影/电视剧/纪录片是特殊分区，使用不同的 API
 */
val allPartitions = listOf(
    // === 视频分区（支持 newlist API）===
    PartitionCategory(1, "动画"),
    PartitionCategory(13, "番剧"),      // 特殊分区
    PartitionCategory(167, "国创"),     // 特殊分区
    PartitionCategory(3, "音乐"),
    PartitionCategory(129, "舞蹈"),
    PartitionCategory(4, "游戏"),
    PartitionCategory(36, "知识"),
    PartitionCategory(188, "科技"),
    PartitionCategory(234, "运动"),
    PartitionCategory(223, "汽车"),
    PartitionCategory(160, "生活"),
    PartitionCategory(211, "美食"),
    PartitionCategory(217, "动物圈"),
    PartitionCategory(119, "鬼畜"),
    PartitionCategory(155, "时尚"),
    PartitionCategory(202, "资讯"),
    PartitionCategory(5, "娱乐"),
    // === 特殊分区（番剧/电影等使用不同 API）===
    PartitionCategory(23, "电影"),      // 特殊分区
    PartitionCategory(11, "电视剧"),    // 特殊分区
    PartitionCategory(177, "纪录片"),   // 特殊分区
    PartitionCategory(181, "影视")      // 特殊分区
)

internal val partitionTabs = listOf(
    PartitionCategory(0, "全站"), PartitionCategory(13, "番剧"),
    PartitionCategory(167, "国创"), PartitionCategory(1, "动画"),
    PartitionCategory(3, "音乐"), PartitionCategory(129, "舞蹈"),
    PartitionCategory(4, "游戏"), PartitionCategory(36, "知识"),
    PartitionCategory(188, "科技"), PartitionCategory(234, "运动"),
    PartitionCategory(223, "汽车"), PartitionCategory(211, "美食"),
    PartitionCategory(217, "动物"), PartitionCategory(119, "鬼畜"),
    PartitionCategory(155, "时尚"), PartitionCategory(5, "娱乐"),
    PartitionCategory(181, "影视"), PartitionCategory(177, "记录"),
    PartitionCategory(23, "电影"), PartitionCategory(11, "剧集"),
    PartitionCategory(-7, "综艺"),
)

private val PartitionSideRailItemHeight = 48.dp
private val PartitionSideRailIndicatorHeight =
    resolveMatchedLiquidIndicatorHeightDp(PartitionSideRailItemHeight.value).dp
private val PartitionSideRailItemSpacing = 4.dp
private val PartitionSideRailMd3UnderlineWidth = 3.dp
private val PartitionSideRailMd3UnderlineHeight = 28.dp
private val PartitionSideRailMd3UnderlineStartPadding = 3.dp
private val PartitionVideoListMaxPush = 20.dp

internal fun resolvePartitionBangumiType(partitionId: Int): Int? = when (partitionId) {
    13 -> BangumiType.ANIME.value
    167 -> BangumiType.GUOCHUANG.value
    23 -> BangumiType.MOVIE.value
    11 -> BangumiType.TV_SHOW.value
    -7 -> 7
    177 -> BangumiType.DOCUMENTARY.value
    else -> null
}

internal data class PartitionSideRailIndicatorHorizontalPadding(
    val start: androidx.compose.ui.unit.Dp,
    val end: androidx.compose.ui.unit.Dp
)

internal fun resolvePartitionSideRailLabelMode(requestedLabelMode: Int): Int =
    normalizeTopTabLabelMode(requestedLabelMode)

internal fun shouldShowPartitionSideRailIcon(labelMode: Int): Boolean =
    shouldShowTopTabIcon(resolvePartitionSideRailLabelMode(labelMode))

internal fun shouldShowPartitionSideRailText(labelMode: Int): Boolean =
    shouldShowTopTabText(resolvePartitionSideRailLabelMode(labelMode))

internal fun resolvePartitionSideRailIcon(
    partitionId: Int,
    iconFamily: AppSemanticIconFamily,
    selected: Boolean,
): ImageVector = when (iconFamily) {
    AppSemanticIconFamily.MATERIAL,
    AppSemanticIconFamily.MIUIX -> when (partitionId) {
        0 -> if (selected) Icons.Filled.GridView else Icons.Outlined.GridView
        1 -> if (selected) Icons.Filled.Animation else Icons.Outlined.Animation
        13 -> if (selected) Icons.Filled.Tv else Icons.Outlined.Tv
        167 -> if (selected) Icons.Filled.Flag else Icons.Outlined.Flag
        3 -> if (selected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote
        129 -> if (selected) Icons.Filled.DirectionsRun else Icons.Outlined.DirectionsRun
        4 -> if (selected) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports
        36 -> if (selected) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb
        188 -> if (selected) Icons.Filled.SmartToy else Icons.Outlined.SmartToy
        234 -> if (selected) Icons.Filled.SportsSoccer else Icons.Outlined.SportsSoccer
        223 -> if (selected) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar
        160 -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        211 -> if (selected) Icons.Filled.Restaurant else Icons.Outlined.Restaurant
        217 -> if (selected) Icons.Filled.Pets else Icons.Outlined.Pets
        119, 5 -> if (selected) Icons.Filled.TheaterComedy else Icons.Outlined.TheaterComedy
        155 -> if (selected) Icons.Filled.Checkroom else Icons.Outlined.Checkroom
        202 -> if (selected) Icons.Filled.Newspaper else Icons.Outlined.Newspaper
        23 -> if (selected) Icons.Filled.Movie else Icons.Outlined.Movie
        11 -> if (selected) Icons.Filled.Tv else Icons.Outlined.Tv
        -7 -> if (selected) Icons.Filled.TheaterComedy else Icons.Outlined.TheaterComedy
        177 -> if (selected) Icons.Filled.OndemandVideo else Icons.Outlined.OndemandVideo
        181 -> if (selected) Icons.Filled.LocalMovies else Icons.Outlined.LocalMovies
        else -> if (selected) Icons.Filled.GridView else Icons.Outlined.GridView
    }
}

internal fun resolvePartitionSideRailIndicatorHorizontalPadding(
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection
): PartitionSideRailIndicatorHorizontalPadding {
    return PartitionSideRailIndicatorHorizontalPadding(
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection)
    )
}

data class PartitionFeedUiState(
    val selectedPartition: PartitionCategory = partitionTabs.first(),
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class PartitionFeedViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PartitionFeedUiState())
    val uiState = _uiState.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private var requestGeneration = 0
    private val rankingCache = mutableMapOf<Int, PartitionFeedUiState>()

    init {
        loadSelectedPartition(mode = PartitionLoadMode.RESET)
    }

    fun selectPartition(partition: PartitionCategory) {
        if (_uiState.value.selectedPartition.id == partition.id) return
        val current = _uiState.value
        if (!current.isLoading && !current.isRefreshing && current.error == null) {
            rankingCache[current.selectedPartition.id] = current
        }
        requestGeneration++
        rankingCache[partition.id]?.let { cached ->
            hasMore = false
            _uiState.value = cached
            return
        }
        _uiState.update {
            it.copy(
                selectedPartition = partition,
                videos = emptyList(),
                error = null
            )
        }
        loadSelectedPartition(mode = PartitionLoadMode.RESET)
    }

    fun loadMore() {
        loadSelectedPartition(mode = PartitionLoadMode.APPEND)
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        loadSelectedPartition(mode = PartitionLoadMode.REPLACE_REFRESH)
    }

    private fun loadSelectedPartition(mode: PartitionLoadMode) {
        val isRefresh = mode == PartitionLoadMode.REPLACE_REFRESH
        val isReset = mode == PartitionLoadMode.RESET
        if (_uiState.value.isLoading && !isReset && !isRefresh) return
        if (mode == PartitionLoadMode.APPEND && !hasMore) return

        val pageToFetch = when (mode) {
            PartitionLoadMode.RESET -> 1
            PartitionLoadMode.APPEND -> currentPage
            PartitionLoadMode.REPLACE_REFRESH -> resolveReplaceRefreshPage(
                nextLoadPage = currentPage,
                hasMore = hasMore
            )
        }
        if (isReset || isRefresh) {
            if (isReset) {
                currentPage = 1
                hasMore = true
            }
            requestGeneration++
        }
        val generation = requestGeneration
        val partition = _uiState.value.selectedPartition
        val replaceList = isReset || isRefresh

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null
                )
            }
            val seasonType = resolvePartitionBangumiType(partition.id)
            val result = if (seasonType != null) {
                com.android.purebilibili.data.repository.PartitionRankingRepository.getSeasonRanking(seasonType)
            } else {
                val rid = if (partition.id == 0) 0 else
                    com.android.purebilibili.data.repository.VideoCatalogRepository.resolveRegionRankingRid(partition.id)
                if (rid == null) Result.failure(IllegalArgumentException("不支持的分区榜单"))
                else VideoRepository.getRankingVideos(rid = rid)
            }
            if (generation != requestGeneration) return@launch

            result
                .onSuccess { newVideos ->
                    hasMore = false // 榜单接口一次返回全部结果。
                    currentPage = if (newVideos.isNotEmpty()) {
                        pageToFetch + 1
                    } else if (isRefresh) {
                        1
                    } else {
                        currentPage
                    }
                    _uiState.update { state ->
                        val nextVideos = when {
                            isRefresh && newVideos.isEmpty() -> state.videos
                            replaceList -> newVideos
                            else -> state.videos + newVideos
                        }
                        state.copy(
                            videos = nextVideos,
                            isLoading = false,
                            isRefreshing = false,
                            error = if (nextVideos.isEmpty()) {
                                if (isRefresh || isReset) "没有更多内容了" else state.error
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "加载失败"
                        )
                    }
                }
        }
    }
}

private enum class PartitionLoadMode {
    RESET,
    APPEND,
    REPLACE_REFRESH
}

/**
 *  分区页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartitionScreen(
    onBack: () -> Unit,
    onVideoClick: (String, Long, String) -> Unit = { _, _, _ -> },
    onBangumiClick: (Int) -> Unit = {},
    onBangumiSeasonClick: (Long) -> Unit = {},
) {
    val headerBlurEnabled =
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.headerBlurEnabled
    val hazeState = if (
        headerBlurEnabled &&
            com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
            !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    ) com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState() else null
    val headerBlurActive = headerBlurEnabled &&
        hazeState?.let { com.android.purebilibili.core.ui.blur.recoverableBlurEnabled(it) } == true
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = "分区",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (headerBlurActive) Color.Transparent else MaterialTheme.colorScheme.background,
                    scrolledContainerColor = if (headerBlurActive) Color.Transparent else MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.then(
                    if (headerBlurActive) {
                        Modifier.unifiedBlur(
                            hazeState = hazeState,
                            surfaceType = com.android.purebilibili.core.ui.blur.BlurSurfaceType.HEADER,
                        )
                    } else {
                        Modifier
                    }
                )
            )
        }
    ) { paddingValues ->
        PartitionContent(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            hazeState = hazeState,
            onVideoClick = { video -> onVideoClick(video.bvid, video.cid, video.pic) },
            onBangumiClick = onBangumiClick,
            onBangumiSeasonClick = onBangumiSeasonClick,
        )
    }
}

/**
 * 分区主体内容。独立页面和首页内嵌分区页共用，避免两套分区网格状态分叉。
 */
@Composable
fun PartitionContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        top = 8.dp,
        bottom = 16.dp,
        start = 16.dp,
        end = 16.dp
    ),
    sideRailTopPaddingProvider: () -> Dp = { contentPadding.calculateTopPadding() },
    hazeState: HazeState? = null,
    onVideoClick: (VideoItem) -> Unit = {},
    onBangumiClick: (Int) -> Unit = {},
    onBangumiSeasonClick: (Long) -> Unit = {},
    scrollToTopRequestId: Int = 0,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    viewModel: PartitionFeedViewModel = viewModel()
) {
    val context = LocalContext.current
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(initialValue = HomeSettings())
    val topChromeIconFamily = rememberAppTopChromePolicy().effectiveIconFamily
    val liquidGlassIndicatorEnabled = rememberAppChromeLiquidGlassEnabled(
        androidNativeEnabled = homeSettings.otherGlassEnabled,
    )
    val liquidGlassTuning = remember(
        homeSettings.liquidGlassProgress,
        homeSettings.liquidGlassAdvancedSettings,
        homeSettings.liquidGlassReadabilityMode,
    ) {
        resolveLiquidGlassTuning(
            homeSettings.liquidGlassProgress,
            homeSettings.liquidGlassAdvancedSettings,
            homeSettings.liquidGlassReadabilityMode,
        )
    }
    val pageWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val state by viewModel.uiState.collectAsHomeFeedState(pageWorkActive)
    val rankingListStates = partitionTabs.associate { partition ->
        partition.id to key(partition.id) { rememberLazyListState() }
    }
    val activeListState = if (state.selectedPartition.id == 0) listState
        else rankingListStates.getValue(state.selectedPartition.id)

    LaunchedEffect(scrollToTopRequestId) {
        if (scrollToTopRequestId <= 0) return@LaunchedEffect
        if (activeListState.firstVisibleItemIndex > 0 || activeListState.firstVisibleItemScrollOffset > 0) {
            activeListState.animateScrollToTop()
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val sideRailOnRight = homeSettings.partitionSideRailOnRight
    val leftPadding = contentPadding.calculateLeftPadding(layoutDirection)
    val rightPadding = contentPadding.calculateRightPadding(layoutDirection)
    val railOuterPadding = if (sideRailOnRight) rightPadding else leftPadding
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = contentPadding.calculateBottomPadding()
    // 给标签保留文字缩放空间，侧栏和内容间距在所有材质下使用同一布局。
    val sideRailWidth = 56.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.35f) + railOuterPadding
    var sideRailVideoPushTargetPx by remember { mutableFloatStateOf(0f) }
    val sideRailVideoPushPx = animateFloatAsState(
        targetValue = sideRailVideoPushTargetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "partitionVideoListPush"
    )

    val shouldLoadMore by remember(activeListState, state.videos.size, state.isLoading) {
        derivedStateOf {
            val lastVisibleIndex = activeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex != null && lastVisibleIndex >= state.videos.lastIndex - 4
        }
    }
    LaunchedEffect(shouldLoadMore, pageWorkActive) {
        if (pageWorkActive && shouldLoadMore && !state.isLoading && state.videos.isNotEmpty()) {
            viewModel.loadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .globalWallpaperAwareBackground()
            .responsiveContentWidth(maxWidth = 1000.dp)
    ) {
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (hazeState != null &&
                        com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current
                    ) {
                        Modifier.hazeSource(state = hazeState)
                    } else {
                        Modifier
                    }
                ),
            content = {
                PartitionSideRail(
                    partitions = partitionTabs,
                    selectedId = state.selectedPartition.id,
                    labelMode = homeSettings.topTabLabelMode,
                    iconFamily = topChromeIconFamily,
                    modifier = Modifier
                        .width(sideRailWidth)
                        // 侧栏按当前顶栏的可见高度测量，收起后释放顶部空间；
                        // 在测量阶段读取滚动状态，玻璃、MD3、Miuix 共用视口规则。
                        .layout { measurable, constraints ->
                            val top = sideRailTopPaddingProvider().roundToPx()
                                .coerceIn(0, constraints.maxHeight)
                            val rail = measurable.measure(constraints.offset(vertical = -top))
                            layout(rail.width, rail.height + top) {
                                rail.placeRelative(0, top)
                            }
                        }
                        // 横向外边距和底部避让仅用于玻璃；原生侧栏继续使用列表内边距。
                        .then(
                            if (liquidGlassIndicatorEnabled) {
                                Modifier.padding(start = 4.dp, end = 4.dp, bottom = bottomPadding)
                            } else Modifier
                        )
                        .zIndex(if (liquidGlassIndicatorEnabled) 1f else 0f),
                    contentPadding = if (liquidGlassIndicatorEnabled) {
                        PaddingValues(
                            horizontal = ((railOuterPadding + 4.dp) / 2 - 4.dp).coerceAtLeast(4.dp),
                            // 竖向首末项与左右边沿共用 dock 的比例内缩，避免固定 8dp 多留一层空白。
                            vertical = com.android.purebilibili.feature.home.components
                                .resolveFloatingDockRestIndicatorVerticalInsetDp(
                                    (sideRailWidth - 8.dp).value,
                                ).dp,
                        )
                    } else PaddingValues(
                        start = if (sideRailOnRight) 4.dp else railOuterPadding,
                        top = 8.dp,
                        bottom = bottomPadding,
                        end = if (sideRailOnRight) railOuterPadding else 4.dp
                    ),
                    liquidGlassIndicatorEnabled = liquidGlassIndicatorEnabled,
                    liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
                    liquidGlassTuning = liquidGlassTuning,
                    onVideoListPushChanged = { sideRailVideoPushTargetPx = it },
                    onPartitionSelected = viewModel::selectPartition
                )

                // Match list content top (status/insets + 8dp) so indicator sits above first row.
                val partitionRefreshIndicatorTopInset = topPadding + 8.dp
                AdaptivePullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    indicatorTopInset = partitionRefreshIndicatorTopInset,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = sideRailVideoPushPx.value * if (sideRailOnRight) -1f else 1f
                        }
                ) {
                    PartitionVideoList(
                        state = state,
                        listState = activeListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues.Absolute(
                            left = if (sideRailOnRight) leftPadding else 4.dp,
                            top = partitionRefreshIndicatorTopInset,
                            right = if (sideRailOnRight) 4.dp else rightPadding,
                            bottom = bottomPadding
                        ),
                        onVideoClick = { video ->
                            if (video.contentType == "pgc_rank") onBangumiSeasonClick(video.id)
                            else onVideoClick(video)
                        }
                    )
                }
            },
        ) { measurables, constraints ->
            // 两种位置只改变摆放，保持侧栏与列表的组合身份及滚动状态。
            // 所有材质共用正常测量，不查询内部懒列表的固有尺寸。
            val rail = measurables[0].measure(constraints.copy(minWidth = 0))
            val list = measurables[1].measure(
                Constraints.fixed(constraints.maxWidth - rail.width, constraints.maxHeight)
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                rail.place(if (sideRailOnRight) list.width else 0, 0)
                list.place(if (sideRailOnRight) 0 else rail.width, 0)
            }
        }
    }
}

@Composable
private fun PartitionSideRail(
    partitions: List<PartitionCategory>,
    selectedId: Int,
    labelMode: Int,
    iconFamily: AppSemanticIconFamily,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    liquidGlassIndicatorEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    liquidGlassTuning: LiquidGlassTuning,
    onVideoListPushChanged: (Float) -> Unit,
    onPartitionSelected: (PartitionCategory) -> Unit
) {
    val listState = rememberLazyListState()
    val railOverscrollEffect = rememberOverscrollEffect()
    val listOverscrollEffect = remember(railOverscrollEffect) {
        railOverscrollEffect?.withoutVisualEffect()
    }
    val selectedIndex = partitions.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val density = LocalDensity.current
    // 玻璃侧栏使用纵向槽位；随文字缩放保留胶囊比例，原生 MD3/Miuix 沿用 48dp。
    val railItemHeight = if (liquidGlassIndicatorEnabled) {
        64.dp * density.fontScale.coerceIn(1f, 1.35f)
    } else PartitionSideRailItemHeight
    val animationScope = rememberCoroutineScope()
    val motionSpec = remember { resolveSegmentedControlMotionSpec() }
    val resolvedLabelMode = resolvePartitionSideRailLabelMode(labelMode)
    val showIcon = shouldShowPartitionSideRailIcon(resolvedLabelMode)
    val showText = shouldShowPartitionSideRailText(resolvedLabelMode)
    // 外层左右边距不对称（如 start=16、end=4）时条目会整体偏向一侧；
    // 取水平均值对称化，使条目与选中指示器在整条侧栏内水平居中。
    val railHorizontalInset = run {
        val layoutDirection = LocalLayoutDirection.current
        (contentPadding.calculateStartPadding(layoutDirection) +
            contentPadding.calculateEndPadding(layoutDirection)) / 2
    }
    val railContentPadding = PaddingValues(
        start = railHorizontalInset,
        end = railHorizontalInset,
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding()
    )
    val indicatorGeometry = remember {
        resolveMatchedLiquidIndicatorGeometry(
            dockHeightDp = PartitionSideRailItemHeight.value,
            indicatorHeightDp = PartitionSideRailIndicatorHeight.value,
        )
    }
    val itemCount = partitions.size.coerceAtLeast(1)
    val maxTabIndex = (itemCount - 1).coerceAtLeast(0)
    val onSelectedLatest by rememberUpdatedState(onPartitionSelected)
    val partitionsLatest by rememberUpdatedState(partitions)
    val offsetAnimation = remember { Animatable(0f) }
    val holder = remember { PartitionSideRailDragHolder() }
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION)
    val latestHaptic by rememberUpdatedState(haptic)
    val hapticsEnabled by rememberUpdatedState(
        liquidGlassIndicatorEnabled && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.hapticFeedbackEnabled
    )
    val touchSlopPx by rememberUpdatedState(LocalViewConfiguration.current.touchSlop)
    // 与底栏复用起步阈值、跨槽滞回和节流；只接收真实拖动及其边缘滚动位置。
    val gestureHaptics = remember(itemCount, liquidGlassIndicatorEnabled) { LiquidDockHapticPolicy() }

    // LazyList offsets include the real measured slot sizes and dynamic header padding.
    // Use the same anchor for the visible indicator, its hit test, and the glyph recording.
    fun railOffset(position: Float): Float {
        val layout = listState.layoutInfo
        val anchor = layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.index - position) }
            ?: return holder.contentTopPaddingPx + position * holder.itemSlotHeightPx
        val next = layout.visibleItemsInfo.firstOrNull { it.index == anchor.index + 1 }
        val step = next?.let { (it.offset - anchor.offset).toFloat() }
            ?.takeIf { it > 0f } ?: (anchor.size + with(density) { PartitionSideRailItemSpacing.toPx() })
        return resolvePartitionSideRailMeasuredOffsetPx(
            position, anchor.index, anchor.offset, layout.viewportStartOffset, step,
        )
    }

    val dampedDragAnimation = remember(animationScope, itemCount, density, liquidGlassIndicatorEnabled, gestureHaptics) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..maxTabIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = indicatorGeometry.pressedScale,
            trackingMode = DampedDragTrackingMode.DIRECT,
            useMiuixIndicatorMotion = liquidGlassIndicatorEnabled,
            canDrag = { offset ->
                val animation = holder.instance ?: return@DampedDragAnimation true
                if (holder.itemSlotHeightPx <= 0f) return@DampedDragAnimation false
                val indicatorY = railOffset(animation.value)
                val globalTouchY = indicatorY + offset.y
                globalTouchY in 0f..holder.totalHeightPx &&
                    shouldStartPartitionSideRailIndicatorDrag(offset.y, 0f, holder.itemHeightPx)
            },
            onDragStarted = {
                if (liquidGlassIndicatorEnabled) {
                    gestureHaptics.start(targetValue, itemCount, touchSlopPx / holder.itemSlotHeightPx.coerceAtLeast(1f))
                }
                holder.dragCenterY = railOffset(value) + holder.itemHeightPx / 2f
            },
            onDragStopped = {
                holder.dragCenterY = Float.NaN
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, maxTabIndex)
                if (liquidGlassIndicatorEnabled) updateValue(targetIndex.toFloat())
                else animateToValue(targetIndex.toFloat(), animatePress = false)
                val hadGestureFeedback = gestureHaptics.finish()
                partitionsLatest.getOrNull(targetIndex)?.let { partition ->
                    if (liquidGlassIndicatorEnabled && hadGestureFeedback) {
                        AppActionHaptics.runWithoutAdditionalFeedback { onSelectedLatest(partition) }
                    } else onSelectedLatest(partition)
                }
                holder.offsetJob?.cancel()
                holder.offsetJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (holder.itemSlotHeightPx > 0f) {
                    if (liquidGlassIndicatorEnabled) {
                        val center = if (holder.dragCenterY.isFinite()) holder.dragCenterY
                            else railOffset(targetValue) + holder.itemHeightPx / 2f
                        holder.dragCenterY = (center + dragAmount.y).coerceIn(0f, holder.totalHeightPx)
                        val visibleCenter = resolvePartitionRailDragCenter(
                            holder.dragCenterY, holder.totalHeightPx, holder.itemHeightPx,
                        )
                        updateValue(
                            (targetValue + (visibleCenter - railOffset(targetValue) - holder.itemHeightPx / 2f) /
                                holder.itemSlotHeightPx).fastCoerceIn(0f, maxTabIndex.toFloat())
                        )
                    } else {
                        updateValue(
                            (targetValue + dragAmount.y / holder.itemSlotHeightPx)
                                .fastCoerceIn(0f, maxTabIndex.toFloat()),
                        )
                    }
                    if (liquidGlassIndicatorEnabled && gestureHaptics.update(targetValue, SystemClock.uptimeMillis()) && hapticsEnabled) {
                        latestHaptic(HapticType.GESTURE_STEP)
                    }
                    holder.offsetJob?.cancel()
                    holder.offsetJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.y)
                    }
                }
            },
        ).also { holder.instance = it }
    }

    val visualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val reduceMotion = com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion()
    val edgeSizePx = with(density) { 56.dp.toPx() }
    val maximumScrollSpeedPx = with(density) { 360.dp.toPx() }
    // 只有实际拖动到边缘才创建逐帧任务；隐藏、失焦、退到后台或减少动态效果时取消。
    LaunchedEffect(dampedDragAnimation, listState, liquidGlassIndicatorEnabled, visualWorkActive, reduceMotion, edgeSizePx, maximumScrollSpeedPx) {
        snapshotFlow {
            if (!liquidGlassIndicatorEnabled || !visualWorkActive || reduceMotion || !dampedDragAnimation.isDragging) 0f else {
                resolvePartitionRailEdgeScrollFraction(
                    holder.dragCenterY, holder.totalHeightPx, edgeSizePx,
                    listState.canScrollBackward, listState.canScrollForward,
                )
            }
        }.distinctUntilChanged().collectLatest { fraction ->
            if (fraction == 0f) return@collectLatest
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.032f)
                previousFrame = frame
                val consumed = listState.scrollBy(fraction * maximumScrollSpeedPx * seconds)
                if (kotlin.math.abs(consumed) < 0.01f) break
                // 抵消列表滚动，指示器留在手指附近，索引继续经过新进入视口的分类。
                if (holder.itemSlotHeightPx > 0f) {
                    dampedDragAnimation.updateValue(
                        (dampedDragAnimation.targetValue + consumed / holder.itemSlotHeightPx)
                            .fastCoerceIn(0f, maxTabIndex.toFloat()),
                    )
                    if (gestureHaptics.update(dampedDragAnimation.targetValue, SystemClock.uptimeMillis()) && hapticsEnabled) {
                        latestHaptic(HapticType.GESTURE_STEP)
                    }
                }
            }
        }
    }
    // 点击、拖动松手及外部选择都把目标完整带回可视范围；减少动态效果时直接滚动。
    LaunchedEffect(selectedIndex, dampedDragAnimation.isDragging, liquidGlassIndicatorEnabled, visualWorkActive, reduceMotion) {
        if (!liquidGlassIndicatorEnabled || !visualWorkActive || dampedDragAnimation.isDragging) return@LaunchedEffect
        val top = railOffset(selectedIndex.toFloat())
        val inset = holder.contentTopPaddingPx
        val bottom = top + holder.itemHeightPx
        val delta = when {
            top < inset -> top - inset
            bottom > holder.totalHeightPx - inset -> bottom - (holder.totalHeightPx - inset)
            else -> 0f
        }
        if (delta != 0f) {
            if (reduceMotion) listState.scrollBy(delta) else listState.animateScrollBy(delta)
        }
    }

    LaunchedEffect(selectedIndex, dampedDragAnimation, liquidGlassIndicatorEnabled) {
        // Glass clicks start their spring before submitting selection. Do not restart it
        // when the selected content changes; external selections use the same feedback.
        if (!liquidGlassIndicatorEnabled || dampedDragAnimation.targetValue != selectedIndex.toFloat()) {
            dampedDragAnimation.animateToValue(
                selectedIndex.toFloat(),
                animatePress = liquidGlassIndicatorEnabled,
            )
        }
    }
    val activatePartition: (Int) -> Unit = { index ->
        partitions.getOrNull(index)?.let { partition ->
            if (liquidGlassIndicatorEnabled) dampedDragAnimation.animateToValue(index.toFloat())
            onPartitionSelected(partition)
        }
    }
    val railHighlight = if (liquidGlassIndicatorEnabled &&
        com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    ) {
        remember(animationScope, dampedDragAnimation, listState) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    resolvePartitionSideRailInteractiveHighlightPosition(
                        railWidthPx = size.width,
                        indicatorOffsetPx = railOffset(dampedDragAnimation.value),
                        itemHeightPx = holder.itemHeightPx,
                    )
                },
            )
        }
    } else null

    // Overscroll stretches drawing without changing LazyList layout offsets. Render it once
    // around the list, indicator and glyph source so they cannot separate during edge pulls.
    // This shared placement applies to glass and native MD3/Miuix; their styling stays separate.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        // 在约束作用域内读取宽度，内层 Box 仅使用计算结果。
        val railDockWidth = maxWidth
        val dockShellLensIntensity = (railDockWidth / 64.dp).coerceIn(0f, 1f)
        val itemHeightPx = with(density) { railItemHeight.toPx() }
        val itemSlotHeightPx = with(density) { (railItemHeight + PartitionSideRailItemSpacing).toPx() }
        val contentTopPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
        holder.itemSlotHeightPx = itemSlotHeightPx
        holder.itemHeightPx = itemHeightPx
        holder.totalHeightPx = with(density) { maxHeight.toPx() }
        holder.contentTopPaddingPx = contentTopPaddingPx
        val nativeIndicatorPadding = resolvePartitionSideRailIndicatorHorizontalPadding(
            contentPadding = railContentPadding,
            layoutDirection = LocalLayoutDirection.current,
        )
        // 横向的壳高在纵向对应壳宽，横向槽宽对应分类行高；其余几何规则共用。
        val indicatorWidth = if (liquidGlassIndicatorEnabled) {
            resolveFloatingDockIndicatorHeightDp(
                requestedHeightDp = resolveMatchedLiquidIndicatorHeightDp(railDockWidth.value),
                tabWidthDp = railItemHeight.value,
                geometryMode = FloatingBottomBarGeometryMode.TopNavigation,
                shellHeightDp = railDockWidth.value,
            ).dp
        } else (railDockWidth - nativeIndicatorPadding.start - nativeIndicatorPadding.end).coerceAtLeast(0.dp)
        val indicatorHeight = if (liquidGlassIndicatorEnabled) railItemHeight
            else PartitionSideRailIndicatorHeight
        val indicatorHorizontalPadding = if (liquidGlassIndicatorEnabled) {
            val inset = ((railDockWidth - indicatorWidth) / 2).coerceAtLeast(0.dp)
            PartitionSideRailIndicatorHorizontalPadding(inset, inset)
        } else nativeIndicatorPadding
        val railIndicatorShape = if (liquidGlassIndicatorEnabled) {
            RoundedCornerShape(resolveFloatingDockIndicatorCornerRadiusDp(
                shellThicknessDp = railDockWidth.value,
                indicatorThicknessDp = indicatorWidth.value,
                indicatorSlotExtentDp = indicatorHeight.value,
            ).dp)
        } else resolveSharedBottomBarCapsuleShape()
        val railGeometry = if (liquidGlassIndicatorEnabled) {
            resolveFloatingDockIndicatorGeometry(
                shellHeightDp = railDockWidth.value,
                indicatorHeightDp = indicatorWidth.value,
                geometryMode = FloatingBottomBarGeometryMode.TopNavigation,
            )
        } else indicatorGeometry
        val railDragScaleTarget = railGeometry.pressedScale
        SideEffect { dampedDragAnimation.pressedScale = railDragScaleTarget }
        val maxVideoPushPx = with(density) { PartitionVideoListMaxPush.toPx() }
        val currentIndicatorOffsetPxProvider = {
            val naturalTop = railOffset(dampedDragAnimation.value)
            if (liquidGlassIndicatorEnabled && dampedDragAnimation.isDragging) {
                val scaledHeight = with(density) { indicatorHeight.toPx() } *
                    maxOf(1f, dampedDragAnimation.scaleX, dampedDragAnimation.scaleY)
                resolvePartitionRailDragCenter(
                    naturalTop + itemHeightPx / 2f, holder.totalHeightPx, scaledHeight,
                ) - itemHeightPx / 2f
            } else naturalTop
        }
        val fullIndicatorLensSpec = resolveBottomBarBackdropPresetIndicatorLens(progress = 1f)
        val captureSafeInset = resolveBottomBarCaptureSafeInsetDp(
            indicatorWidthDp = indicatorWidth.value,
            refractionHeightDp = fullIndicatorLensSpec.refractionHeightDp,
            refractionAmountDp = fullIndicatorLensSpec.refractionAmountDp,
            panelOffsetDp = 0f,
            dragScaleTarget = railDragScaleTarget,
        ).dp
        // 扩大滚动效果的横向绘制边界，再还原内部布局宽度；玻璃关闭时保持原生边界。
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (liquidGlassIndicatorEnabled) {
                        Modifier.bottomBarMatchedCaptureOverflow(
                            horizontalInset = captureSafeInset,
                            verticalInset = captureSafeInset,
                        )
                    } else Modifier
                )
                .overscroll(railOverscrollEffect)
                .then(
                    if (liquidGlassIndicatorEnabled) Modifier.padding(captureSafeInset)
                    else Modifier
                )
                .then(railHighlight?.gestureModifier ?: Modifier),
        ) {
            val railPageBackdrop = rememberLayerBackdrop()
            val railContentBackdrop = rememberLayerBackdrop()
            val glassPageSource = if (liquidGlassIndicatorEnabled) {
                rememberChromeBackdropSource(drawContentToScreen = false)
            } else null
            val glassContentSource = if (liquidGlassIndicatorEnabled) {
                rememberChromeBackdropSource(drawContentToScreen = false)
            } else null
            val globalWallpaperSource = LocalGlobalWallpaperBackdropSource.current
            val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
            val wallpaperBackdrop = if (liquidGlassIndicatorEnabled) {
                globalWallpaperSource?.takeIf {
                    globalWallpaperVisible && it.isReady
                }?.backdrop ?: LocalHomeWallpaperBackdrop.current
            } else null
            // Only wallpaper/background siblings are safe sources: the enclosing page records
            // this indicator too. Keep the original native capture path when glass is disabled.
            val sampledPageBackdrop = wallpaperBackdrop ?: glassPageSource?.backdrop ?: railPageBackdrop
            val sampledContentBackdrop = glassContentSource?.backdrop ?: railContentBackdrop
            val combinedBackdrop = rememberCombinedBackdrop(sampledPageBackdrop, sampledContentBackdrop)
            // Reserve the same overflow as the home dock so the indicator lens can
            // sample pixels just outside the rail bounds without clipping.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .bottomBarMatchedCaptureOverflow(captureSafeInset)
                    .then(
                        glassPageSource?.modifier ?: Modifier.alpha(0f).then(
                            if (com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current) {
                                Modifier.layerBackdrop(railPageBackdrop)
                            } else Modifier
                        )
                    )
                    .background(AppSurfaceTokens.background())
                    .then(railHighlight?.modifier ?: Modifier)
            )

            if (liquidGlassIndicatorEnabled) {
                val darkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
                val dockTuning = resolveAndroidNativeBottomBarTuning(blurEnabled = true, darkTheme = darkTheme)
                val dockContainerColor = resolveAndroidNativeFloatingBottomBarContainerColor(
                    surfaceColor = AppSurfaceTokens.cardContainer(),
                    tuning = dockTuning,
                    glassEnabled = true,
                    blurEnabled = true,
                    blurIntensity = com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity(),
                    liquidGlassPreset = liquidGlassPreset,
                    liquidGlassTuning = liquidGlassTuning,
                )
                // 外壳只采样独立背景源，不采样自身、分类文字或选中指示器。
                // 窄侧栏以实际宽度限制折射强度，避免纵向胶囊两侧光学边沿相撞。
                Box(
                    Modifier
                        .matchParentSize()
                        .biliPaiFloatingDockShell(
                            backdrop = sampledPageBackdrop,
                            containerColor = dockContainerColor,
                            pressProgress = 0f,
                            shape = resolveSharedBottomBarCapsuleShape(),
                            lensIntensity = dockShellLensIntensity,
                            liquidGlassTuning = liquidGlassTuning,
                        ),
                )
                // 与横向标签栏相同：隐藏采样带按静止指示器厚度录制玻璃、文字与高光。
                // 纵向取窄于外壳的实际内容宽度，放大透镜跨过材质边界后形成左右缝隙和色散。
                // 此层不显示到屏幕，也不回采可见外壳或移动指示器。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Match the visible LazyColumn's horizontal content bounds exactly.
                        .padding(start = indicatorHorizontalPadding.start, end = indicatorHorizontalPadding.end)
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .clearAndSetSemantics {}
                        .graphicsLayer {
                            translationY = railOffset(0f)
                            clip = false
                        }
                        .then(requireNotNull(glassContentSource).modifier)
                        .biliPaiFloatingDockCaptureSurface(
                            backdrop = sampledPageBackdrop,
                            containerColor = dockContainerColor,
                            shape = resolveSharedBottomBarCapsuleShape(),
                            liquidGlassTuning = liquidGlassTuning,
                        )
                        .then(railHighlight?.modifier ?: Modifier),
                    verticalArrangement = Arrangement.spacedBy(PartitionSideRailItemSpacing),
                ) {
                    partitions.forEach { partition ->
                        Box(
                            Modifier.fillMaxWidth().height(railItemHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            PartitionSideRailItem(
                                partition = partition,
                                selected = true,
                                selectionProgress = 1f,
                                showIcon = showIcon,
                                showText = showText,
                                iconFamily = iconFamily,
                                onClick = {},
                                interactive = false,
                                contentColorOverride = AppContentColors.selectionAccent(liquidGlassIndicatorEnabled),
                                itemHeight = indicatorHeight,
                                modifier = Modifier.width(indicatorWidth),
                            )
                        }
                    }
                }
            }
            LazyColumn(
                state = listState,
                // The list still handles scroll/fling events; only the outer rail renders overscroll.
                overscrollEffect = listOverscrollEffect,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (liquidGlassIndicatorEnabled) 0f else 2f)
                    .then(
                        if (liquidGlassIndicatorEnabled) Modifier.clip(resolveSharedBottomBarCapsuleShape())
                        else Modifier
                    )
                    .then(railHighlight?.modifier ?: Modifier),
                contentPadding = railContentPadding,
                verticalArrangement = Arrangement.spacedBy(PartitionSideRailItemSpacing)
            ) {
                itemsIndexed(
                    items = partitions,
                    key = { _, partition -> partition.id }
                ) { index, partition ->
                    PartitionSideRailItem(
                        partition = partition,
                        itemHeight = railItemHeight,
                        selected = partition.id == selectedId,
                        selectionProgress = resolvePartitionSideRailItemSelectionProgress(
                            itemIndex = index,
                            indicatorPosition = dampedDragAnimation.value
                        ),
                        showIcon = showIcon,
                        showText = showText,
                        iconFamily = iconFamily,
                        selectedAccent = AppContentColors.selectionAccent(liquidGlassIndicatorEnabled),
                        onClick = { activatePartition(index) }
                    )
                }
            }

            PartitionSideRailMovingIndicator(
                dragAnimation = dampedDragAnimation,
                dragOffsetPx = offsetAnimation.value,
                itemSlotHeightPx = itemSlotHeightPx,
                itemHeightPx = itemHeightPx,
                indicatorHeight = indicatorHeight,
                indicatorShape = railIndicatorShape,
                dragScaleTarget = railDragScaleTarget,
                indicatorOffsetPxProvider = currentIndicatorOffsetPxProvider,
                indicatorWidth = indicatorWidth,
                dockWidth = railDockWidth,
                liquidGlassIndicatorEnabled = liquidGlassIndicatorEnabled,
                liquidGlassPreset = liquidGlassPreset,
                liquidGlassTuning = liquidGlassTuning,
                contentBackdrop = combinedBackdrop,
                backdrop = sampledPageBackdrop,
                maxVideoPushPx = maxVideoPushPx,
                horizontalPadding = indicatorHorizontalPadding,
                onVideoListPushChanged = onVideoListPushChanged,
                interactionModifier = Modifier
                    .then(dampedDragAnimation.modifier)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            activatePartition(selectedIndex)
                        },
                    )
                    .clearAndSetSemantics {},
                modifier = Modifier.matchParentSize().drawWithContent {
                    clipRect(
                        left = -size.width, right = size.width * 2f,
                        top = -size.height, bottom = size.height * 2f,
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
            )
        }
    }
}

@Composable
private fun PartitionSideRailMovingIndicator(
    dragAnimation: DampedDragAnimation,
    dragOffsetPx: Float,
    itemSlotHeightPx: Float,
    itemHeightPx: Float,
    indicatorHeight: androidx.compose.ui.unit.Dp,
    indicatorShape: androidx.compose.ui.graphics.Shape,
    dragScaleTarget: Float,
    indicatorOffsetPxProvider: () -> Float,
    indicatorWidth: androidx.compose.ui.unit.Dp,
    dockWidth: androidx.compose.ui.unit.Dp,
    liquidGlassIndicatorEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    liquidGlassTuning: LiquidGlassTuning,
    contentBackdrop: top.yukonga.miuix.kmp.blur.Backdrop,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop,
    maxVideoPushPx: Float,
    horizontalPadding: PartitionSideRailIndicatorHorizontalPadding,
    onVideoListPushChanged: (Float) -> Unit,
    interactionModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val selectionIndicatorStyle = resolveHomeSelectionIndicatorStyle(
        uiStyle = LocalAppUiStyle.current,
        liquidGlassEnabled = liquidGlassIndicatorEnabled,
    )
    if (selectionIndicatorStyle == HomeSelectionIndicatorStyle.MD3_UNDERLINE) {
        SideEffect {
            onVideoListPushChanged(0f)
        }
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = with(density) { horizontalPadding.start.toPx() }
                        translationY = indicatorOffsetPxProvider()
                    }
                    .width(indicatorWidth)
                    .height(with(density) { itemHeightPx.toDp() })
                    .then(interactionModifier),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = PartitionSideRailMd3UnderlineStartPadding)
                        .width(PartitionSideRailMd3UnderlineWidth)
                        .height(PartitionSideRailMd3UnderlineHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(AppContentColors.accent()),
                )
            }
        }
        return
    }

    val shape = indicatorShape
    val isDarkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val motionSpec = remember { resolveSegmentedControlMotionSpec() }
    val pressProgress = dragAnimation.pressProgress
    val refractionMotionProfile = resolveBottomBarRefractionMotionProfile(
        position = dragAnimation.value,
        velocity = dragAnimation.velocity,
        isDragging = dragAnimation.isDragging,
        motionSpec = motionSpec
    )
    val motionProgress = resolveSegmentedControlMotionProgress(
        pressProgress = pressProgress,
        refractionProgress = refractionMotionProfile.progress,
        tapPressRefractionEnabled = true
    )
    val videoListPushPx = resolvePartitionVideoListPushPx(
        // Glass selection bloom belongs to the lens; only a real rail drag pushes the feed.
        pressProgress = if (liquidGlassIndicatorEnabled && !dragAnimation.isDragging && dragOffsetPx == 0f) {
            0f
        } else pressProgress,
        dragOffsetPx = dragOffsetPx,
        itemSlotHeightPx = itemSlotHeightPx,
        maxPushPx = maxVideoPushPx
    )
    SideEffect {
        onVideoListPushChanged(videoListPushPx)
    }
    // Glass renders the controller's X/Y springs directly, so shrink starts near the
    // position target. Native controls retain their existing independent scale timing.
    val indicatorLayerScaleTransform = if (liquidGlassIndicatorEnabled) {
        null // 玻璃由绘制阶段的 provider 读取实时弹簧值。
    } else {
        rememberBottomBarIndicatorLayerScaleTransform(
            active = dragAnimation.isDragging || pressProgress > 0f,
            target = dragScaleTarget,
        )
    }
    val indicatorLensSpec = resolveBottomBarBackdropPresetIndicatorLens(
        progress = pressProgress
    )

    Box(modifier = modifier) {
        val indicatorHeightPx = with(density) { indicatorHeight.toPx() }
        val centeringOffsetPx = ((itemHeightPx - indicatorHeightPx) / 2f).coerceAtLeast(0f)
        val centeredIndicatorOffsetPx = 0f
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = indicatorOffsetPxProvider() + centeringOffsetPx
            }
        ) {
            BottomBarMatchedLiquidIndicator(
                visible = true,
                dockContentAlpha = 1f,
                indicatorTranslationXPx = with(density) { horizontalPadding.start.toPx() },
                indicatorTranslationYPx = centeredIndicatorOffsetPx,
                indicatorPanelOffsetPx = 0f,
                indicatorWidth = indicatorWidth,
                indicatorHeight = indicatorHeight,
                shellShape = shape,
                liquidGlassPreset = liquidGlassPreset,
                contentBackdrop = contentBackdrop,
                backdrop = backdrop,
                indicatorLensSpec = indicatorLensSpec,
                liquidGlassTuning = liquidGlassTuning,
                effectivePressProgress = pressProgress,
                indicatorIdleSurfaceColor = resolveAndroidNativeIdleIndicatorSurfaceColor(
                    darkTheme = isDarkTheme,
                ),
                glassEnabled = liquidGlassIndicatorEnabled,
                motionProgress = motionProgress,
                velocityItemsPerSecond = dragAnimation.velocity,
                isDragging = dragAnimation.isDragging,
                indicatorLayerScaleProgress = 0f,
                indicatorLayerScaleTransform = indicatorLayerScaleTransform,
                dragScaleTarget = dragScaleTarget,
                bottomBarMotionSpec = motionSpec,
                isDarkTheme = isDarkTheme,
                orientation = BottomBarLiquidOrientation.VERTICAL,
                opticalShellExtentDp = dockWidth.value,
                indicatorScaleTransformProvider = if (liquidGlassIndicatorEnabled) {
                    { BottomBarIndicatorLayerTransform(scaleX = dragAnimation.scaleX, scaleY = dragAnimation.scaleY) }
                } else null,
                wobblePosition = { dragAnimation.value },
                wobbleSelection = { dragAnimation.targetValue.roundToInt() },
                wobbleReady = { dragAnimation.isReadyForRecoil },
                wobbleLastIndex = dragAnimation.valueRange.endInclusive.roundToInt(),
                indicatorAlignment = Alignment.TopStart,
                interactionModifier = interactionModifier,
            )
        }
    }
}

@Composable
private fun PartitionSideRailItem(
    partition: PartitionCategory,
    selected: Boolean,
    selectionProgress: Float,
    showIcon: Boolean,
    showText: Boolean,
    iconFamily: AppSemanticIconFamily,
    onClick: () -> Unit,
    interactive: Boolean = true,
    contentColorOverride: Color? = null,
    selectedAccent: Color? = null,
    itemHeight: androidx.compose.ui.unit.Dp = PartitionSideRailItemHeight,
    modifier: Modifier = Modifier,
) {
    // Glass uses the exact custom accent; native MD3/Miuix retain their existing palette.
    val selectedColor = selectedAccent ?: AppContentColors.accent()
    val unselectedColor = AppContentColors.secondaryText()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val clampedSelectionProgress = selectionProgress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clip(resolveSharedBottomBarCapsuleShape())
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val contentColor = contentColorOverride ?: when {
                clampedSelectionProgress > 0f -> lerp(
                    unselectedColor,
                    selectedColor,
                    clampedSelectionProgress
                )
                pressed -> AppContentColors.primaryText()
                else -> unselectedColor
            }
            if (showIcon) {
                AppIcon(
                    imageVector = resolvePartitionSideRailIcon(
                        partitionId = partition.id,
                        iconFamily = iconFamily,
                        selected = selected || clampedSelectionProgress > 0.5f,
                    ),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (showText) 18.dp else 24.dp)
                )
            }
            if (showIcon && showText) {
                Spacer(modifier = Modifier.height(1.dp))
            }
            if (showText) {
                AppText(
                    text = partition.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = if (showIcon) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected || clampedSelectionProgress > 0.5f) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    color = contentColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

internal fun shouldStartPartitionSideRailIndicatorDrag(
    pointerY: Float,
    indicatorTopPx: Float,
    indicatorHeightPx: Float
): Boolean {
    if (indicatorHeightPx <= 0f) return false
    return pointerY in indicatorTopPx..(indicatorTopPx + indicatorHeightPx)
}

internal fun resolvePartitionSideRailInteractiveHighlightPosition(
    railWidthPx: Float,
    indicatorOffsetPx: Float,
    itemHeightPx: Float,
): Offset = Offset(
    x = railWidthPx / 2f,
    y = indicatorOffsetPx + itemHeightPx / 2f,
)

internal fun resolvePartitionSideRailIndicatorOffsetPx(
    indicatorPosition: Float,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffsetPx: Int,
    contentTopPaddingPx: Float,
    itemSlotHeightPx: Float
): Float {
    return contentTopPaddingPx +
        indicatorPosition * itemSlotHeightPx -
        firstVisibleItemIndex * itemSlotHeightPx -
        firstVisibleItemScrollOffsetPx
}

/** Use an actual measured row rather than reconstructing the entire lazy list's scroll. */
internal fun resolvePartitionSideRailMeasuredOffsetPx(
    indicatorPosition: Float,
    anchorIndex: Int,
    anchorOffsetPx: Int,
    viewportStartOffsetPx: Int,
    measuredSlotHeightPx: Float,
): Float = anchorOffsetPx - viewportStartOffsetPx +
    (indicatorPosition - anchorIndex) * measuredSlotHeightPx

internal fun resolvePartitionSideRailItemSelectionProgress(
    itemIndex: Int,
    indicatorPosition: Float
): Float {
    return (1f - abs(indicatorPosition - itemIndex.toFloat())).coerceIn(0f, 1f)
}

internal fun resolvePartitionVideoListPushPx(
    pressProgress: Float,
    dragOffsetPx: Float,
    itemSlotHeightPx: Float,
    maxPushPx: Float
): Float {
    if (maxPushPx <= 0f) return 0f
    val dragProgress = if (itemSlotHeightPx > 0f) {
        (abs(dragOffsetPx) / itemSlotHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress = max(pressProgress.coerceIn(0f, 1f), dragProgress * 0.65f)
    return maxPushPx * EaseOut.transform(progress)
}

private class PartitionSideRailDragHolder {
    var instance: DampedDragAnimation? = null
    var itemSlotHeightPx: Float = 0f
    var itemHeightPx: Float = 0f
    var totalHeightPx: Float = 0f
    var contentTopPaddingPx: Float = 0f
    var offsetJob: Job? = null
    var dragCenterY by mutableFloatStateOf(Float.NaN)
}

@Composable
private fun PartitionVideoList(
    state: PartitionFeedUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    onVideoClick: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val showUpBadges by SettingsManager
        .getHomeUpBadgesVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val homeDurationStyle by SettingsManager
        .getHomeDurationStyle(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeDurationStyle.OUTSIDE_COVER)
    val showHomePublishTime by SettingsManager
        .getHomePublishTimeVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val sharedTransitionEnabled = LocalSharedTransitionEnabled.current
    val sharedElementSourceRoute = LocalVideoCardSharedElementSourceRoute.current
        ?.takeIf { it.isNotBlank() }
        ?: "partition"
    when {
        state.videos.isEmpty() && state.isLoading -> {
            BoxWithConstraints(modifier = modifier.fillMaxHeight().padding(contentPadding)) {
                ContentMediaListSkeleton(
                    modifier = Modifier.fillMaxSize(),
                    itemCount = 8,
                    contentPadding = PaddingValues(0.dp),
                    mediaRowCoverWidth = com.android.purebilibili.feature.home.components.cards
                        .resolvePlainHorizontalVideoCoverWidthDp(maxWidth.value, 16f / 10f).dp,
                    mediaRowContentPadding = PaddingValues(vertical = 6.dp),
                    mediaRowContentGap = 10.dp,
                )
            }
        }
        state.videos.isEmpty() && state.error != null -> {
            Box(modifier = modifier.fillMaxHeight()) {
                AppText(
                    text = state.error,
                    modifier = Modifier.align(Alignment.Center),
                    color = AppContentColors.error()
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxHeight(),
                contentPadding = contentPadding,
                // 剧集卡片带完整背景，需要独立卡片间距；普通透明视频行保留紧凑间距。
                // 这是内容类型差异，玻璃、MD3、Miuix 共用，不修改各材质的卡片内部尺寸。
                verticalArrangement = Arrangement.spacedBy(
                    if (resolvePartitionBangumiType(state.selectedPartition.id) != null) 12.dp else 2.dp
                )
            ) {
                itemsIndexed(
                    items = state.videos,
                    key = { index, video ->
                        resolveIndexedVideoLazyKey(
                            namespace = "partition_feed_item",
                            index = index,
                            bvid = video.bvid,
                            aid = video.aid,
                            cid = video.cid
                        )
                    }
                ) { _, video ->
                    if (video.contentType == "pgc_rank") {
                        PartitionSeasonRankCard(video = video, onClick = { onVideoClick(video) })
                    } else HomeStyleSingleColumnVideoCard(
                        video = video,
                        sourceRoute = sharedElementSourceRoute,
                        coverAspectRatio = 16f / 10f,
                        transitionEnabled = sharedTransitionEnabled,
                        showUpBadge = showUpBadges,
                        durationStyle = homeDurationStyle,
                        showPublishTime = showHomePublishTime,
                        transparentCardBackground = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onVideoClick(video) },
                    )
                }

                if (state.isLoading) {
                    item(key = "partition_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AdaptiveLoadingIndicator(size = 24.dp)
                        }
                    }
                }
            }
        }
    }
}

/** 剧集榜单条目展示海报、更新进度、播放量和追剧量，点击进入该剧集详情。 */
@Composable
private fun PartitionSeasonRankCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // AppText 按主题读取自身默认样式，不能仅依赖父级 Material ProvideTextStyle。
    // 在入口显式分配标题、更新信息和统计字阶，两种材质及原生主题共用。
    val typography = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography()
    com.android.purebilibili.feature.personal.PersonalMediaCardFrame(
        modifier = modifier,
        coverAspectRatio = 3f / 4f,
        allowPortraitCover = true,
        onClick = onClick,
        headlineContent = {
            AppText(
                text = video.title,
                style = typography.title,
                color = AppContentColors.primaryText(),
                maxLines = com.android.purebilibili.core.ui.videoCardTitleMaxLines(),
                overflow = com.android.purebilibili.core.ui.videoCardTitleOverflow(),
            )
        },
        supportingContent = {
            if (video.owner.name.isNotBlank()) {
                AppText(
                    text = video.owner.name,
                    style = typography.author,
                    color = AppContentColors.secondaryText(),
                )
            }
            AppText(
                text = "${com.android.purebilibili.core.util.FormatUtils.formatStat(video.stat.view.toLong())} 播放 · " +
                    "${com.android.purebilibili.core.util.FormatUtils.formatStat(video.stat.favorite.toLong())} 追剧",
                style = typography.statistic,
                color = AppContentColors.secondaryText(),
            )
        },
        coverContent = {
            com.android.purebilibili.core.ui.components.PageAwareAsyncImage(
                model = video.pic,
                contentDescription = video.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/** 指示器纵向留在视口内；横向膨胀不受此约束影响。 */
internal fun resolvePartitionRailDragCenter(center: Float, viewportHeight: Float, indicatorHeight: Float): Float {
    val height = viewportHeight.coerceAtLeast(0f)
    val half = (indicatorHeight.coerceAtLeast(0f) / 2f + 4f).coerceAtMost(height / 2f)
    return center.coerceIn(half, height - half)
}

internal fun resolvePartitionRailEdgeScrollFraction(
    center: Float,
    viewportHeight: Float,
    edgeSize: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Float {
    if (!center.isFinite() || viewportHeight <= 0f || edgeSize <= 0f) return 0f
    val edge = edgeSize.coerceAtMost(viewportHeight / 2f)
    return when {
        center < edge && canScrollBackward -> -((edge - center) / edge).coerceIn(0f, 1f)
        center > viewportHeight - edge && canScrollForward ->
            ((center - viewportHeight + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}
