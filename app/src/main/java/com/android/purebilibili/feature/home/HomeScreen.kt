// 文件路径: feature/home/HomeScreen.kt
package com.android.purebilibili.feature.home

import com.android.purebilibili.core.plugin.BuiltinPluginIds

import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi //  Added
import androidx.compose.foundation.LocalOverscrollFactory // [Fix] Import for disabling overscroll (New API)
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import com.android.purebilibili.core.ui.rememberHomeStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.*  // 🌊 瀑布流布局
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material3.DrawerValue
import com.android.purebilibili.core.ui.components.AppModalNavigationDrawer
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.navigation.animatePagerSelection
import androidx.compose.material3.rememberDrawerState
import com.android.purebilibili.feature.home.components.MineSideDrawer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppPullRefreshLoadingIndicator
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppPullRefreshIndicatorStyle
import com.android.purebilibili.core.ui.rememberAppPullRefreshProfile
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy

import com.android.purebilibili.feature.settings.GITHUB_URL
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.core.store.HomeHeaderCollapseMode
import com.android.purebilibili.core.store.SettingsManager //  引入 SettingsManager
import com.android.purebilibili.core.store.resolveEffectiveHomeSettings

import com.android.purebilibili.core.store.resolveHomeHeaderBlurEnabled
import com.android.purebilibili.core.plugin.skin.rememberUiSkinState
//  从 components 包导入拆分后的组件
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.FluidHomeTopBar
import com.android.purebilibili.feature.home.components.FrostedSideBar
import com.android.purebilibili.feature.home.components.CategoryTabRow
import com.android.purebilibili.feature.home.components.HomeHeader
import com.android.purebilibili.feature.home.components.HomeRefreshIndicator
import com.android.purebilibili.feature.home.components.Md3ScreenshotRefreshIndicator
import com.android.purebilibili.feature.home.components.nativeMd3TopTabSelectionSpec
import com.android.purebilibili.feature.home.components.HomeInteractionMotionBudget
import com.android.purebilibili.feature.home.components.rememberHomeUiSkinDecoration
import com.android.purebilibili.feature.home.components.resolveHomeInteractionMotionBudget
import com.android.purebilibili.feature.home.components.resolveHomeDrawerScrimAlpha
import com.android.purebilibili.feature.home.components.resolveTopTabStyle
import com.android.purebilibili.feature.home.components.resolveHomeTopChromeMaterialMode
import com.android.purebilibili.feature.home.components.resolveHomeTopPresetStyle
import com.android.purebilibili.feature.home.components.resolveHomeTopTabYOffsetDp
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import com.android.purebilibili.feature.home.policy.BottomBarVisibilityIntent
import com.android.purebilibili.feature.home.policy.HomeBottomBarScrollState
import com.android.purebilibili.feature.home.policy.HomeFeedScrollAnchor
import com.android.purebilibili.feature.home.policy.HomeFeedScrollAnchorSaver
import com.android.purebilibili.feature.home.policy.captureHomeFeedScrollAnchor
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderTransitionRunning
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderSettleTransition
import com.android.purebilibili.feature.home.policy.resolveHomeFeedScrollRestorePlan
import com.android.purebilibili.feature.home.policy.shouldReserveHomeBottomBarListPadding
import com.android.purebilibili.feature.home.policy.shouldRestoreHomeFeedScrollAnchor
import com.android.purebilibili.feature.home.policy.reduceHomeBottomBarListScroll
import com.android.purebilibili.feature.home.policy.resolveHomeBottomBarBaseVisibility
import com.android.purebilibili.feature.home.policy.resolveHomeRecommendationHeaderCollapseMode
import com.android.purebilibili.feature.bangumi.HomeBangumiTabPage
import com.android.purebilibili.feature.live.LiveListScreen
import com.android.purebilibili.feature.home.policy.resolveHomePagerSettledAction
import com.android.purebilibili.feature.home.policy.shouldAnimateHomePagerToCategory
import com.android.purebilibili.feature.home.policy.HomePagerSettledAction
import com.android.purebilibili.feature.home.policy.resolveHomeInitialTopTabPage
import com.android.purebilibili.feature.home.policy.resolveHomePagerTargetPage
import com.android.purebilibili.feature.home.policy.shouldEnableHomeTopPagerUserScroll
import com.android.purebilibili.feature.home.policy.shouldSkipHomePagerStateDrive
import com.android.purebilibili.feature.home.policy.shouldTreatInitialHomePagerPageAsSyncedWithState
import com.android.purebilibili.feature.home.policy.shouldUseInitialHomePagerSnap
//  从 cards 子包导入卡片组件
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard   //  故事卡片
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.ui.ErrorState as ModernErrorState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.LocalSharedTransitionScope  //  共享过渡
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionClock
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VideoCardTransitionSettleState
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionBackgroundScaleReduction
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionBackgroundSource
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.shouldHomeFeedOwnVideoCardTransitionSnapshot
import com.android.purebilibili.core.ui.transition.shouldShowHomeOverlayChromeDuringVideoCardTransition
import com.android.purebilibili.core.ui.transition.shouldUseRealtimeVideoCardTransitionBackgroundBlur
import com.android.purebilibili.core.ui.transition.videoCardTransitionBackgroundEffect
import com.android.purebilibili.core.ui.transition.videoCardTransitionOverlayDepthEffect
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockEdge
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockVisibility
import com.android.purebilibili.core.ui.animation.DissolvableVideoCard  //  粒子消散动画
import com.android.purebilibili.core.ui.animation.gl.isThanosEffectSupported
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackJankStateValue
import coil3.imageLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged  //  性能优化：防止重复触发
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map as mapFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import androidx.compose.animation.ExperimentalSharedTransitionApi  //  共享过渡实验API
import com.android.purebilibili.core.ui.LocalSetBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding

import kotlinx.coroutines.channels.Channel
import com.android.purebilibili.data.model.response.VideoItem // [Fix] Import VideoItem
import com.android.purebilibili.feature.home.components.VideoPreviewDialog // [Fix] Import VideoPreviewDialog
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.feature.home.components.HomeNotInterestedReasonSheet
import com.android.purebilibili.feature.partition.PartitionContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class HomeScrollRequest {
    SCROLL_TO_TOP,
    SCROLL_TO_TOP_OR_REFRESH,
    SCROLL_TO_TOP_AND_REFRESH,
}

private const val HOME_RESELECT_DOUBLE_TAP_WINDOW_MS = 300L

internal fun mergeHomeScrollRequests(
    first: HomeScrollRequest,
    followUp: HomeScrollRequest,
): HomeScrollRequest = when {
    first == HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH ||
        followUp == HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH ->
        HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH
    first == HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH ||
        followUp == HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH ->
        HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH
    else -> HomeScrollRequest.SCROLL_TO_TOP
}

// 首页只有一个可见消费者；Channel 保证底栏重选/双击事件不会在暂停采集时丢失。
val LocalHomeScrollChannel = compositionLocalOf<Channel<HomeScrollRequest>?> { null }

/** Home feed LayerBackdrop for card info liquid glass (sibling capture, not nested SO). */
val LocalHomeMiuixBackdrop = staticCompositionLocalOf<MiuixBackdrop?> { null }

/** Wallpaper-only LayerBackdrop used by video-card info surfaces. */
val LocalHomeWallpaperBackdrop = staticCompositionLocalOf<MiuixBackdrop?> { null }
val LocalHomeWallpaperBackdropReady = staticCompositionLocalOf { false }
val LocalHomeWallpaperIsStatic = staticCompositionLocalOf { false }

// [New] Global Scroll Offset for Liquid Glass Effect
// Used to pass scroll position from HomeScreen to BottomBar without causing recomposition
val LocalHomeScrollOffset = compositionLocalOf { androidx.compose.runtime.mutableFloatStateOf(0f) }
val LocalHomeFeedScrollInProgress = compositionLocalOf { mutableStateOf(false) }

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (HomeVideoClickRequest) -> Unit,
    onAvatarClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: (() -> Unit)? = null,
    onAccountSwitchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    //  新增：动态页面回调
    onDynamicClick: () -> Unit = {},
    //  新增：历史记录回调
    onHistoryClick: () -> Unit = {},
    //  新增：分区回调
    onPartitionClick: () -> Unit = {},
    onWeeklySeriesClick: () -> Unit = {},
    partitionVideoSourceRoute: String = "partition",
    onPartitionVideoClick: (VideoItem) -> Unit = { video ->
        onVideoClick(
            HomeVideoClickRequest(
                bvid = video.bvid,
                cid = video.cid,
                coverUrl = video.pic,
                isVerticalVideo = video.resolveIsVerticalForNavigation(),
                source = HomeVideoClickSource.GRID,
                sourceRoute = partitionVideoSourceRoute
            )
        )
    },
    //  新增：直播点击回调
    onLiveClick: (Long, String, String) -> Unit = { _, _, _ -> },  // roomId, title, uname
    //  [修复] 番剧/影视回调，接受类型参数 (1=番剧 2=电影 等)
    onBangumiClick: (Int) -> Unit = {},
    //  新增：分类点击回调（用于游戏、知识、科技等分类，传入 tid 和 name）
    onCategoryClick: (Int, String) -> Unit = { _, _ -> },
    //  [新增] 底栏扩展项目导航回调
    onFavoriteClick: () -> Unit = {},  // 收藏页面
    onLikedVideosClick: () -> Unit = {},  // 点赞视频页面
    onLiveListClick: () -> Unit = {},  // 底栏直播全屏页
    onLiveSearchClick: () -> Unit = {},
    onLiveMatchClick: () -> Unit = {},
    onLiveAreaClick: () -> Unit = {},
    onLiveFollowingClick: () -> Unit = {},
    onLiveAreaDetailClick: (Int, Int, String) -> Unit = { _, _, _ -> },
    onBangumiSeasonClick: (Long) -> Unit = {},
    onBangumiEpisodeClick: (Long, Long) -> Unit = { seasonId, _ -> onBangumiSeasonClick(seasonId) },
    onBangumiPlayFollow: (Long) -> Unit = onBangumiSeasonClick,
    onWatchLaterClick: () -> Unit = {},  // 稍后再看页面
    onDownloadClick: () -> Unit = {},  // 离线缓存页面
    onInboxClick: () -> Unit = {},  // 私信页面
    onStoryClick: () -> Unit = {},  //  [新增] 竖屏短视频
    onPluginsClick: () -> Unit = {},
    onSpaceClick: (Long) -> Unit = {},
    globalHazeState: dev.chrisbanes.haze.HazeState? = null,  //  [新增] 全局底栏模糊状态
    isTopLevelActive: Boolean = true,
    /** 首页是否在屏幕上。图片加载只看这个，不看数据同步是否就绪。 */
    pageOnScreen: Boolean = isTopLevelActive,
    visibleDuringNavigation: Boolean = false,
    isReturningFromVideoDetail: Boolean = false,
    isQuickReturningFromVideoDetail: Boolean = false,
    onVideoDetailReturnAnimationConsumed: () -> Unit = {}
) {
    val feedNavigation = rememberHomeFeedNavigationCallbacks(onVideoClick, onSpaceClick, onLiveClick)
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentCategory by viewModel.currentCategory.collectAsStateWithLifecycle()
    val displayedTabIndexFromState by viewModel.displayedTabIndex.collectAsStateWithLifecycle()
    val popularSubCategory by viewModel.popularSubCategory.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val messageUnreadCount by viewModel.messageUnreadCount.collectAsStateWithLifecycle()
    val refreshKey by viewModel.refreshKey.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    val refreshNewItemsCount by viewModel.refreshNewItemsCount.collectAsStateWithLifecycle()
    val refreshNewItemsKey by viewModel.refreshNewItemsKey.collectAsStateWithLifecycle()
    val refreshNewItemsHandledKey by viewModel.refreshNewItemsHandledKey.collectAsStateWithLifecycle()
    val recommendOldContentAnchorBvid by viewModel.recommendOldContentAnchorBvid.collectAsStateWithLifecycle()
    val recommendOldContentStartIndex by viewModel.recommendOldContentStartIndex.collectAsStateWithLifecycle()
    val recommendOldContentRevealKey by viewModel.recommendOldContentRevealKey.collectAsStateWithLifecycle()
    val todayWatchMode by viewModel.todayWatchMode.collectAsStateWithLifecycle()
    val todayWatchPlan by viewModel.todayWatchPlan.collectAsStateWithLifecycle()
    val todayWatchLoading by viewModel.todayWatchLoading.collectAsStateWithLifecycle()
    val todayWatchError by viewModel.todayWatchError.collectAsStateWithLifecycle()
    val todayWatchPluginEnabled by viewModel.todayWatchPluginEnabled.collectAsStateWithLifecycle()
    val todayWatchCollapsed by viewModel.todayWatchCollapsed.collectAsStateWithLifecycle()
    val todayWatchCardConfig by viewModel.todayWatchCardConfig.collectAsStateWithLifecycle()
    val undoAvailable by viewModel.undoAvailable.collectAsStateWithLifecycle()
// val pullRefreshState = rememberPullToRefreshState() // [Removed] Moved inside HorizontalPager
    val context = LocalContext.current
    val messageBadgeMode by com.android.purebilibili.core.store.MessageCenterPreferences.messageBadgeMode(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.UnreadBadgeMode.NUMBER)
    // 顶栏消息入口的未读标记形态：隐藏置零，红点用哨兵值，数字模式保留真实计数。
    val messageUnreadDisplay = when (messageBadgeMode) {
        com.android.purebilibili.core.store.UnreadBadgeMode.HIDDEN -> 0
        com.android.purebilibili.core.store.UnreadBadgeMode.DOT ->
            if (messageUnreadCount > 0) com.android.purebilibili.feature.home.components.HOME_TOP_RIGHT_BADGE_DOT else 0
        com.android.purebilibili.core.store.UnreadBadgeMode.NUMBER -> messageUnreadCount
    }
    val uiSkinState by rememberUiSkinState(context)
    val homeUiSkinDecoration = rememberHomeUiSkinDecoration(uiSkinState)
    val overlayMotionSpec = remember { resolveHomeOverlayMotionSpec() }
    //  [Refactor] Use a map of grid states for each category to support HorizontalPager
    // [Refactor] Use a map of grid states for each category to support HorizontalPager
    val gridStates = remember { mutableMapOf<HomeCategory, LazyStaggeredGridState>() }
    HomeCategory.entries.forEach { category ->
        gridStates[category] = rememberSaveable(
            category.name,
            saver = com.android.purebilibili.core.ui.HomeStaggeredGridStateSaver
        ) {
            com.android.purebilibili.core.ui.createHomeStaggeredGridState()
        }
    }
    val popularGridStates = remember { mutableMapOf<PopularSubCategory, LazyStaggeredGridState>() }
    PopularSubCategory.entries.forEach { subCategory ->
        popularGridStates[subCategory] = rememberSaveable(
            "popular_${subCategory.name}",
            saver = com.android.purebilibili.core.ui.HomeStaggeredGridStateSaver
        ) {
            com.android.purebilibili.core.ui.createHomeStaggeredGridState()
        }
    }
    var liveScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var bangumiScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var partitionScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var subscriptionScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var subscriptionArticleOpen by remember { mutableStateOf(false) }
    var subscriptionArticleScrolling by remember { mutableStateOf(false) }
    val subscriptionListState = rememberHomeStaggeredGridState()
    // [Feature] Video Preview State (Global Scope)
    val targetVideoItemState = remember { mutableStateOf<VideoItem?>(null) }
    var dissolvingNotInterestedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var pendingNotInterestedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var pendingVideoShare by remember {
        mutableStateOf<com.android.purebilibili.feature.video.share.VideoSharePayload?>(null)
    }
    val coroutineScope = rememberCoroutineScope() // 用于双击回顶动画
    var homeBackToTopSquishActive by remember { mutableStateOf(false) }
    var homeBackToTopSquishGeneration by remember { mutableIntStateOf(0) }
    val headerSettleMotionSpec = AppMotionTokens.emphasizedSpec<Float>()
    val globalScrollOffset = LocalHomeScrollOffset.current
    val globalFeedScrollInProgress = LocalHomeFeedScrollInProgress.current
    // [Header] 首页重选/双击回顶时需要强制恢复顶部，避免自动收缩后残留空白区域。
    // saveable：进 UP 空间等二级页后返回时保留折叠态，避免顶栏重张开带动列表“自动下滑”感。
    var headerOffsetHeightPx by rememberSaveable { mutableFloatStateOf(0f) }
    // Share the state handle; consumers choose the phase in which they read the offset.
    val headerOffsetProvider = remember { { headerOffsetHeightPx } }
    var topTabsAutoCollapsedByScroll by rememberSaveable { mutableStateOf(false) }
    var headerSettleAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var headerSettleTargetPx by remember { mutableStateOf<Float?>(null) }
    var homeHeaderRevealLock by remember { mutableStateOf(false) }
    var homeNavigationRevision by remember { mutableLongStateOf(0L) }
    // 离开首页顶层时冻结滚动锚点；返回后校正瀑布流因 contentPadding/重组造成的偏移漂移。
    var pendingFeedScrollAnchor by rememberSaveable(stateSaver = HomeFeedScrollAnchorSaver) {
        mutableStateOf<HomeFeedScrollAnchor?>(null)
    }
    var delayTopTabsUntilCardSettled by remember { mutableStateOf(false) }
    var hideTopTabsForForwardDetailNav by remember { mutableStateOf(false) }
    var returnAnimationStartElapsedMs by remember { mutableLongStateOf(0L) }
    var topTabsRevealJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun setHeaderOffsetImmediate(value: Float) {
        headerSettleAnimationJob?.cancel()
        headerSettleAnimationJob = null
        headerSettleTargetPx = null
        headerOffsetHeightPx = value
    }

    fun revealHomeHeaderNow() {
        topTabsAutoCollapsedByScroll = false
        setHeaderOffsetImmediate(0f)
        globalScrollOffset.floatValue = 0f
    }

    fun triggerHomeBackToTopCardSquish() {
        val generation = homeBackToTopSquishGeneration + 1
        homeBackToTopSquishGeneration = generation
        homeBackToTopSquishActive = true
        coroutineScope.launch {
            delay(105L)
            if (homeBackToTopSquishGeneration == generation) {
                homeBackToTopSquishActive = false
            }
        }
    }

    fun animateHeaderOffsetTo(targetValue: Float) {
        // 同方向连续拖动复用当前目标，避免即时模式每次回调都重启动画。
        if (headerSettleAnimationJob?.isActive == true && headerSettleTargetPx == targetValue) return
        val transition = resolveHomeHeaderSettleTransition(
            currentHeaderOffsetPx = headerOffsetHeightPx,
            targetHeaderOffsetPx = targetValue
        )
        if (!transition.shouldAnimate) {
            setHeaderOffsetImmediate(transition.targetOffsetPx)
            return
        }
        headerSettleAnimationJob?.cancel()
        headerSettleTargetPx = transition.targetOffsetPx
        headerSettleAnimationJob = coroutineScope.launch {
            animate(
                initialValue = headerOffsetHeightPx,
                targetValue = transition.targetOffsetPx,
                animationSpec = headerSettleMotionSpec
            ) { value, _ ->
                headerOffsetHeightPx = value
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (headerSettleAnimationJob === job) {
                    headerSettleAnimationJob = null
                    headerSettleTargetPx = null
                }
                // Keep the tab visibility state tied to the settled header position.
                if (targetValue >= -0.5f) {
                    topTabsAutoCollapsedByScroll = false
                }
            }
        }
    }

    val homeScrollToTopMutex = remember { Mutex() }
    suspend fun withHomeScrollToTopLock(
        requestIsCurrent: () -> Boolean,
        block: suspend () -> Unit,
    ) {
        homeScrollToTopMutex.withLock {
            if (!requestIsCurrent()) return@withLock
            kotlinx.coroutines.coroutineScope {
                homeHeaderRevealLock = true
                headerSettleAnimationJob?.cancel()
                topTabsAutoCollapsedByScroll = false
                globalScrollOffset.floatValue = 0f
                val headerAnimJob = launch {
                    if (headerOffsetHeightPx < -0.5f) {
                        animate(
                            initialValue = headerOffsetHeightPx,
                            targetValue = 0f,
                            animationSpec = headerSettleMotionSpec
                        ) { value, _ -> headerOffsetHeightPx = value }
                    } else {
                        headerOffsetHeightPx = 0f
                    }
                }
                headerSettleAnimationJob = headerAnimJob
                try {
                    block()
                    headerAnimJob.join()
                } finally {
                    headerAnimJob.cancel()
                    if (requestIsCurrent()) {
                        revealHomeHeaderNow()
                    } else {
                        // 旧请求只释放自己持有的锁，不把新页面顶栏强行展开。
                        if (headerSettleAnimationJob === headerAnimJob) {
                            setHeaderOffsetImmediate(headerOffsetProvider())
                        }
                    }
                    homeHeaderRevealLock = false
                }
            }
        }
    }

    // [新增] 监听全局回顶事件
    val scrollChannel = LocalHomeScrollChannel.current
    val latestHomeScrollCategory by rememberUpdatedState(currentCategory)
    val latestHomeScrollPopularSubCategory by rememberUpdatedState(popularSubCategory)

    val homeTopTabSettings by remember(context) {
        SettingsManager.getHomeTopTabSettings(context)
    }.collectAsStateWithLifecycle(
        initialValue = com.android.purebilibili.core.store.HomeTopTabSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext,
    )
    // 顶部标签顺序和可见项交给设置页控制；默认仍是六项。
    // [Refactor] Hoist PagerState to be available for both Content and Header
    // 确保 pagerState 在所有作用域均可见，以便传给 HomeHeader
    val subscriptionRevision by com.android.purebilibili.core.plugin.feed.SubscriptionFeedStore.revision
        .collectAsStateWithLifecycle()
    val installedPlugins by com.android.purebilibili.core.plugin.PluginManager.pluginsFlow
        .collectAsStateWithLifecycle()
    val isSubscriptionPluginPersistedEnabled by com.android.purebilibili.core.plugin.PluginStore
        .isEnabledFlow(context, BuiltinPluginIds.SUBSCRIPTION_FEED)
        .collectAsStateWithLifecycle(initialValue = false)
    val subscriptionFeedsEnabled = remember(
        subscriptionRevision,
        installedPlugins,
        isSubscriptionPluginPersistedEnabled,
        homeTopTabSettings,
    ) {
        com.android.purebilibili.core.plugin.feed.isSubscriptionPluginOrFeedEnabled(
            context = context,
            installedPlugins = installedPlugins,
            isPluginPersistedEnabled = isSubscriptionPluginPersistedEnabled,
        )
    }
    val topTabEntries = remember(homeTopTabSettings, subscriptionFeedsEnabled) {
        ensureSubscriptionHomeTab(
            entries = resolveHomeTopTabEntries(
                customOrderIds = homeTopTabSettings.orderIds,
                visibleIds = homeTopTabSettings.visibleIds
            ),
            feedsEnabled = subscriptionFeedsEnabled,
            visibleIds = homeTopTabSettings.visibleIds,
        )
    }
    val localizedTopTabLabels = topTabEntries.map { entry ->
        when (entry) {
            is HomeTopTabEntry.Category -> stringResource(resolveHomeCategoryLabelRes(entry.category))
            HomeTopTabEntry.Partition -> resolveHomeTopTabEntryLabel(entry)
            HomeTopTabEntry.Subscriptions -> resolveHomeTopTabEntryLabel(entry)
        }
    }
    val topTabKeys = remember(topTabEntries) { topTabEntries.map(HomeTopTabEntry::id) }
    val initialPage = resolveHomeInitialTopTabPage(
        topTabEntries = topTabEntries,
        currentCategory = currentCategory,
        displayedTabIndex = displayedTabIndexFromState
    )
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage) { topTabEntries.size }
    // Only native MD3 opts into detail's pager timing; glass and Miuix keep their defaults.
    val nativeMd3TabMotion = com.android.purebilibili.core.theme.LocalAppUiStyle.current ==
        com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3 &&
        !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val homeTabSelectionAnimationSpec = remember(nativeMd3TabMotion) {
        if (nativeMd3TabMotion) nativeMd3TopTabSelectionSpec<Float>() else null
    }
    val heroCarouselPointerActive = remember { mutableStateOf(false) }
    val onHeroCarouselGestureActiveChange = remember(heroCarouselPointerActive) {
        { active: Boolean -> heroCarouselPointerActive.value = active }
    }
    val shouldYieldHomePagerToHeroCarousel = remember(heroCarouselPointerActive) {
        { shouldYieldHomeTopPagerToHeroCarousel(heroCarouselPointerActive.value) }
    }
    // PagerState 会从 SaveableState 恢复实际页码；不能用 initialPage 判断同步状态，
    // 否则详情返回后可能把恢复的旧页反向写回当前分类。
    val initialPageSyncedWithState = shouldTreatInitialHomePagerPageAsSyncedWithState(
        initialEntry = resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage),
        currentCategory = currentCategory
    )
    // 返回详情页时按标签身份恢复，避免自定义顺序把旧页码解释成另一个分类。
    var retainedTopTabEntry by remember {
        mutableStateOf(resolveHomeTopTabEntryOrNull(topTabEntries, initialPage))
    }
    var hasSyncedPagerWithState by remember(topTabEntries) { mutableStateOf(initialPageSyncedWithState) }
    var lastDrivenPagerCategory by remember(topTabEntries) {
        mutableStateOf(if (initialPageSyncedWithState) currentCategory else null)
    }
    var programmaticPageSwitchInProgress by remember { mutableStateOf(false) }
    val currentDisplayedTabIndex by rememberUpdatedState(displayedTabIndexFromState)
    val latestOnLiveListClick by rememberUpdatedState(onLiveListClick)
    val latestOnBangumiClick by rememberUpdatedState(onBangumiClick)
    val latestHomeTopTabEntries by rememberUpdatedState(topTabEntries)
    val latestHomeTopLevelActive by rememberUpdatedState(isTopLevelActive)

    fun selectHomeTopTab(index: Int) {
        val selectedEntry = topTabEntries.getOrNull(index) ?: return
        if (selectedEntry != resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage) ||
            pagerState.isScrollInProgress
        ) {
            // 点击或返回入口立即失效旧请求，不等待横向动画开始上报。
            homeNavigationRevision++
            setHeaderOffsetImmediate(headerOffsetProvider())
        }
        viewModel.updateDisplayedTabIndex(index)
        retainedTopTabEntry = selectedEntry
        if (pagerState.currentPage != index ||
            (nativeMd3TabMotion && pagerState.isScrollInProgress)
        ) {
            programmaticPageSwitchInProgress = true
            coroutineScope.launch {
                try {
                    animatePagerSelection(pagerState, index, homeTabSelectionAnimationSpec)
                } finally {
                    programmaticPageSwitchInProgress = false
                }
            }
        }
        if (selectedEntry is HomeTopTabEntry.Category) {
            viewModel.switchCategory(selectedEntry.category)
        }
    }

    fun currentHomeScrollRequestIdentity(): HomeScrollRequestIdentity {
        val entry = resolveHomeTopTabEntryOrNull(latestHomeTopTabEntries, pagerState.currentPage)
        return HomeScrollRequestIdentity(
            entry = entry,
            popularSubCategory = latestHomeScrollPopularSubCategory.takeIf {
                entry == HomeTopTabEntry.Category(HomeCategory.POPULAR)
            },
            navigationRevision = homeNavigationRevision,
        )
    }
    suspend fun performHomeScrollRequest(
        identity: HomeScrollRequestIdentity,
        request: HomeScrollRequest,
    ) {
        // 分类连续重选只保留正在执行的一轮；底栏显式刷新仍按原顺序处理。
        if (request == HomeScrollRequest.SCROLL_TO_TOP && homeScrollToTopMutex.isLocked) return
        val requestIsCurrent = {
            shouldApplyHomeScrollRequest(
                requested = identity,
                current = currentHomeScrollRequestIdentity(),
                isTopLevelActive = latestHomeTopLevelActive,
                pagerScrolling = pagerState.isScrollInProgress,
            )
        }
        withHomeScrollToTopLock(requestIsCurrent = requestIsCurrent) {
            val entry = identity.entry
            when (resolveHomeTopTabScrollTarget(entry)) {
                HomeTopTabScrollTarget.LIVE -> liveScrollToTopRequestId++
                HomeTopTabScrollTarget.BANGUMI -> bangumiScrollToTopRequestId++
                HomeTopTabScrollTarget.PARTITION -> partitionScrollToTopRequestId++
                HomeTopTabScrollTarget.SUBSCRIPTION -> subscriptionScrollToTopRequestId++
                HomeTopTabScrollTarget.FEED -> {
                    val activeCategory = (entry as? HomeTopTabEntry.Category)?.category
                        ?: latestHomeScrollCategory
                    val gridState = if (activeCategory == HomeCategory.POPULAR) {
                        popularGridStates[requireNotNull(identity.popularSubCategory)]
                    } else {
                        gridStates[activeCategory]
                    }
                    val isAtTop = gridState == null ||
                        (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0)

                    if (!isAtTop) {
                        val listState = requireNotNull(gridState)
                        // 底栏/重选回顶直达：单次 scrollToItem 是原子操作，
                        // 无两段式 preJump+animate 的中间态，任何距离都不掉帧。
                        listState.scrollToItem(0)
                        if (!requestIsCurrent()) return@withHomeScrollToTopLock
                        triggerHomeBackToTopCardSquish()
                    }
                    val shouldRefresh = request == HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH ||
                        (request == HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH && isAtTop)
                    if (shouldRefresh && requestIsCurrent()) {
                        viewModel.refresh(
                            activeCategory,
                            identity.popularSubCategory ?: latestHomeScrollPopularSubCategory,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(scrollChannel) {
        val channel = scrollChannel ?: return@LaunchedEffect
        for (initialRequest in channel) {
            // 合并双击的等待期也属于当前页面，期间切页不能把旧事件送给新页面。
            val requestIdentity = currentHomeScrollRequestIdentity()
            // 双击首先会发出普通重选，随后再发出“回顶并刷新”。
            // 在双击窗口内将它们升级为一个语义事务，避免先回顶、再回顶刷新的两段滚动。
            val request = if (initialRequest == HomeScrollRequest.SCROLL_TO_TOP) {
                withTimeoutOrNull(HOME_RESELECT_DOUBLE_TAP_WINDOW_MS) {
                    channel.receive()
                }?.let { followUp ->
                    mergeHomeScrollRequests(initialRequest, followUp)
                } ?: initialRequest
            } else {
                initialRequest
            }
            performHomeScrollRequest(
                requestIdentity,
                request,
            )
        }
    }
    TrackJankStateFlag(
        stateName = "home:pager_swipe",
        isActive = pagerState.isScrollInProgress
    )
    TrackJankStateValue(
        stateName = "home:current_category",
        stateValue = currentCategory.name
    )

    // [修复] 仅在完成首次“状态->Pager”对齐后，才允许“Pager->状态”反向同步，避免返回首页时误跳分类。
    LaunchedEffect(
        pagerState,
        topTabEntries,
        hasSyncedPagerWithState,
        currentCategory,
        isTopLevelActive
    ) {
        if (!isTopLevelActive) return@LaunchedEffect
        if (!hasSyncedPagerWithState) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (page, scrolling) ->
                val currentCategoryIndex = topTabEntries
                    .indexOf(HomeTopTabEntry.Category(currentCategory))
                    .takeIf { it >= 0 } ?: 0
                val settledEntry = resolveHomeTopTabEntryOrNull(topTabEntries, page)
                val settledCategory = (settledEntry as? HomeTopTabEntry.Category)?.category
                val settledAction = resolveHomePagerSettledAction(
                    isTopLevelActive = isTopLevelActive,
                    hasSyncedPagerWithState = hasSyncedPagerWithState,
                    pagerCurrentPage = page,
                    pagerScrolling = scrolling,
                    currentCategoryIndex = currentCategoryIndex,
                    settledCategory = settledCategory,
                    programmaticPageSwitchInProgress = programmaticPageSwitchInProgress
                )
                val routesAwayFromHome = settledAction == HomePagerSettledAction.OPEN_LIVE_LIST ||
                    settledAction == HomePagerSettledAction.OPEN_BANGUMI
                if (!scrolling && !routesAwayFromHome && currentDisplayedTabIndex != page) {
                    viewModel.updateDisplayedTabIndex(page)
                }
                if (!scrolling && !routesAwayFromHome && settledEntry != null) {
                    retainedTopTabEntry = settledEntry
                }
                when (settledAction) {
                    HomePagerSettledAction.NONE -> return@collect
                    HomePagerSettledAction.SWITCH_CATEGORY -> {
                        val target = settledCategory ?: return@collect
                        viewModel.switchCategory(target)
                    }
                    HomePagerSettledAction.OPEN_LIVE_LIST,
                    HomePagerSettledAction.OPEN_BANGUMI -> {
                        // 先同步回原分类页，再离开首页。否则页面离开后协程可能被取消，
                        // 返回首页时 Pager 会残留在直播/追番页，出现无法侧滑退出或卡住。
                        if (page != currentCategoryIndex) {
                            programmaticPageSwitchInProgress = true
                            try {
                                pagerState.scrollToPage(currentCategoryIndex)
                            } finally {
                                programmaticPageSwitchInProgress = false
                            }
                        }
                        if (currentDisplayedTabIndex != currentCategoryIndex) {
                            viewModel.updateDisplayedTabIndex(currentCategoryIndex)
                        }
                        retainedTopTabEntry = resolveHomeTopTabEntryOrNull(
                            topTabEntries,
                            currentCategoryIndex
                        )
                        when (settledAction) {
                            HomePagerSettledAction.OPEN_LIVE_LIST -> latestOnLiveListClick()
                            HomePagerSettledAction.OPEN_BANGUMI -> latestOnBangumiClick(1)
                            else -> Unit
                        }
                    }
                }
            }
    }

    // 详情/空间等覆盖首页期间 Pager 可能受导航过渡影响；返回后必须先由业务状态重新对齐。
    // 同时冻结 feed 滚动锚点：二级页返回后 contentPadding（底栏）与列表重测可能把视口“顶”下去一段。
    LaunchedEffect(isTopLevelActive) {
        if (!isTopLevelActive) {
            val gridState = if (currentCategory == HomeCategory.POPULAR) {
                popularGridStates[popularSubCategory]
            } else {
                gridStates[currentCategory]
            }
            // Video clicks freeze the anchor synchronously before navigation starts. Do not replace
            // that clean snapshot with coordinates sampled after shared-bounds has begun remeasuring.
            if (gridState != null && pendingFeedScrollAnchor == null) {
                pendingFeedScrollAnchor = captureHomeFeedScrollAnchor(
                    category = currentCategory,
                    popularSubCategory = popularSubCategory,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    headerOffsetPx = headerOffsetHeightPx
                )
            }
            hasSyncedPagerWithState = false
            lastDrivenPagerCategory = null
        }
    }

    LaunchedEffect(isTopLevelActive, hasSyncedPagerWithState, pendingFeedScrollAnchor) {
        val anchor = pendingFeedScrollAnchor
        if (
            !shouldRestoreHomeFeedScrollAnchor(
                isTopLevelActive = isTopLevelActive,
                hasSyncedPagerWithState = hasSyncedPagerWithState,
                anchor = anchor
            )
        ) {
            return@LaunchedEffect
        }
        val restoreAnchor = requireNotNull(anchor)
        // 等 Pager snap / 底栏 padding 落定后再校正，避免用过渡中的中间布局写回错误 offset。
        yield()
        val gridState = if (restoreAnchor.category == HomeCategory.POPULAR) {
            popularGridStates[restoreAnchor.popularSubCategory]
        } else {
            gridStates[restoreAnchor.category]
        } ?: run {
            pendingFeedScrollAnchor = null
            return@LaunchedEffect
        }
        val restorePlan = resolveHomeFeedScrollRestorePlan(
            currentIndex = gridState.firstVisibleItemIndex,
            currentOffset = gridState.firstVisibleItemScrollOffset,
            currentHeaderOffsetPx = headerOffsetHeightPx,
            anchor = restoreAnchor
        )
        if (restorePlan.restoreGrid) {
            gridState.scrollToItem(
                restoreAnchor.firstVisibleItemIndex,
                restoreAnchor.firstVisibleItemScrollOffset
            )
        }
        if (restorePlan.restoreHeader) {
            setHeaderOffsetImmediate(restoreAnchor.headerOffsetPx)
        }
        pendingFeedScrollAnchor = null
    }

    // [P2] 当前分类被隐藏时，自动落到首个可见分类
    LaunchedEffect(topTabEntries) {
        val visibleCategories = topTabEntries.mapNotNull { (it as? HomeTopTabEntry.Category)?.category }
        val firstVisible = visibleCategories.firstOrNull() ?: return@LaunchedEffect
        if (currentCategory !in visibleCategories) {
            viewModel.updateDisplayedTabIndex(0)
            viewModel.switchCategory(firstVisible)
        }
    }

    // [CrashFix] 顶栏配置变化导致页数收缩时，先钳制 pager 当前页，避免越界
    LaunchedEffect(topTabEntries.size) {
        if (topTabEntries.isEmpty()) return@LaunchedEffect
        val lastIndex = topTabEntries.lastIndex
        if (pagerState.currentPage > lastIndex) {
            pagerState.scrollToPage(lastIndex)
        }
    }

    // [修复] 状态变化时驱动 Pager：首次使用无动画对齐，后续用动画跟随
    LaunchedEffect(currentCategory, topTabEntries, isTopLevelActive, homeTabSelectionAnimationSpec) {
        if (!isTopLevelActive) return@LaunchedEffect
        val targetPage = resolveHomePagerTargetPage(
            topTabEntries = topTabEntries,
            retainedEntry = retainedTopTabEntry,
            currentCategory = currentCategory,
            hasSyncedPagerWithState = hasSyncedPagerWithState
        )
        if (targetPage < 0) return@LaunchedEffect
        if (shouldUseInitialHomePagerSnap(
                hasSyncedPagerWithState = hasSyncedPagerWithState,
                targetPage = targetPage
            )
        ) {
            pagerState.scrollToPage(targetPage)
            hasSyncedPagerWithState = true
            retainedTopTabEntry = resolveHomeTopTabEntryOrNull(topTabEntries, targetPage)
            lastDrivenPagerCategory = currentCategory
            return@LaunchedEffect
        }
        if (shouldSkipHomePagerStateDrive(
                hasSyncedPagerWithState = hasSyncedPagerWithState,
                lastDrivenCategory = lastDrivenPagerCategory,
                currentCategory = currentCategory
            )
        ) {
            return@LaunchedEffect
        }
        if (targetPage == pagerState.currentPage && !pagerState.isScrollInProgress) {
            lastDrivenPagerCategory = currentCategory
            return@LaunchedEffect
        }
        if (shouldAnimateHomePagerToCategory(
                hasSyncedPagerWithState = hasSyncedPagerWithState,
                targetPage = targetPage,
                pagerCurrentPage = pagerState.currentPage,
                pagerScrolling = pagerState.isScrollInProgress,
                programmaticPageSwitchInProgress = programmaticPageSwitchInProgress
            )
        ) {
            programmaticPageSwitchInProgress = true
            try {
                animatePagerSelection(pagerState, targetPage, homeTabSelectionAnimationSpec)
            } finally {
                programmaticPageSwitchInProgress = false
            }
            lastDrivenPagerCategory = currentCategory
        }
    }

    //  [新增] JSON 插件过滤提示
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.feedbackEvents.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
    val lastFilteredCount by com.android.purebilibili.core.plugin.json.JsonPluginManager.lastFilteredCount.collectAsStateWithLifecycle()

    //  当有视频被过滤时显示提示
    LaunchedEffect(lastFilteredCount) {
        if (lastFilteredCount > 0) {
            snackbarHostState.showSnackbar(
                message = " 已过滤 $lastFilteredCount 个视频",
                duration = SnackbarDuration.Short
            )
        }
    }

    //  [埋点] 页面浏览追踪
    LaunchedEffect(Unit) {
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("HomeScreen")
    }

    //  [埋点] 分类切换追踪
    LaunchedEffect(currentCategory) {
        com.android.purebilibili.core.util.AnalyticsHelper.logCategoryView(
            categoryName = currentCategory.label,
            categoryId = currentCategory.tid
        )
    }

    // [New] Broadcast Scroll Offset for Liquid Glass Effect & Parallax
    // Create the state here and provide it

    // 宿主已等待真实配置就绪。返回首帧直接复用，避免先按默认参数布局后再切换。
    val homeSettings = com.android.purebilibili.feature.settings.rememberSettingsHomeConfiguration()
    val videoCardTransitionBackgroundState = LocalVideoCardTransitionBackgroundState.current
    val deferHomeHeavyWork by remember(videoCardTransitionBackgroundState) {
        derivedStateOf {
            com.android.purebilibili.core.ui.transition.shouldDeferVideoCardSourceHeavyWork(
                videoCardTransitionBackgroundState.exposureProvider(),
            )
        }
    }
    // Retained home must not issue scroll/chrome/image work while another page or morph owns the UI.
    val homeFeedUpdatesAllowed = isTopLevelActive &&
        !isReturningFromVideoDetail && !deferHomeHeavyWork
    // Data may be needed to restore a pending scroll anchor. Only navigation freezes
    // displayed feed data; pager synchronization and anchor recovery gate work below.
    val homeFeedWorkAllowed = homeFeedUpdatesAllowed &&
        hasSyncedPagerWithState && pendingFeedScrollAnchor == null
    val homeVisualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val homeCoverPreloadAllowed = homeFeedWorkAllowed && homeVisualWorkActive &&
        com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed.current
    val videoCardReturnGestureInProgress =
        videoCardTransitionBackgroundState.isReturnGestureInProgressProvider()
    var settledShowHomeUpAvatars by rememberSaveable {
        mutableStateOf(homeSettings.showHomeUpAvatars)
    }
    LaunchedEffect(
        homeSettings.showHomeUpAvatars,
        videoCardReturnGestureInProgress,
        isReturningFromVideoDetail,
        homeFeedWorkAllowed,
    ) {
        // 首页作为预测返回的底层页重新进入活跃状态时，DataStore 可能正好补发新值。
        // 在手势/整卡落位期间改变作者行高度会让 sharedBounds 的目标边界跳动；先沿用
        // 首页上次稳定展示的值，落位完成后再应用，同时关闭头像时仍不保留横向头像槽。
        if (homeFeedWorkAllowed && !videoCardReturnGestureInProgress) {
            settledShowHomeUpAvatars = homeSettings.showHomeUpAvatars
        }
    }
    val dissolvingVideos by viewModel.dissolvingVideos.collectAsStateWithLifecycle()
    val followingMids by viewModel.followingMids.collectAsHomeFeedState(homeFeedUpdatesAllowed)
    val showOnlineCount by remember(context) {
        SettingsManager.getShowCardOnlineCount(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    val homeFeedCardStyle by remember(context) {
        SettingsManager.getHomeFeedCardStyle(context)
    }.collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeFeedCardStyle.BILIPAI,
            context = kotlin.coroutines.EmptyCoroutineContext)
    val topChromePolicy = rememberAppTopChromePolicy()
    val pullRefreshProfile = rememberAppPullRefreshProfile()
    val semanticVisualPolicy = rememberAppSemanticVisualPolicy()
    val pullRefreshMotionStyle = pullRefreshProfile.motionStyle
    val pullRefreshIndicatorStyle = pullRefreshProfile.indicatorStyle


    var showEasterEggDialog by remember { mutableStateOf(false) }
    var refreshDeltaTipText by remember { mutableStateOf<String?>(null) }

    //  [彩蛋] 下拉刷新成功后显示趣味提示（仅在开关开启时）
    LaunchedEffect(refreshKey, homeSettings.easterEggEnabled) {
        val message = refreshMessage
        if (message != null && refreshKey > 0 && homeSettings.easterEggEnabled) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "关闭彩蛋",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                showEasterEggDialog = true
            }
        }
    }

    LaunchedEffect(refreshNewItemsKey, isRefreshing, currentCategory, homeFeedWorkAllowed) {
        if (!homeFeedWorkAllowed) return@LaunchedEffect
        val refreshKey = refreshNewItemsKey
        if (!shouldHandleRefreshNewItemsEvent(refreshKey, refreshNewItemsHandledKey)) {
            return@LaunchedEffect
        }
        val count = refreshNewItemsCount ?: return@LaunchedEffect
        if (count > 0) {
            val targetGridState = when (currentCategory) {
                HomeCategory.RECOMMEND -> gridStates[HomeCategory.RECOMMEND]
                HomeCategory.FOLLOW -> gridStates[HomeCategory.FOLLOW]
                else -> null
            }
            if (targetGridState != null && shouldResetToTopAfterIncrementalRefresh(
                    currentCategory = currentCategory,
                    newItemsCount = count,
                    isRefreshing = isRefreshing,
                    firstVisibleItemIndex = targetGridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = targetGridState.firstVisibleItemScrollOffset
                )
            ) {
                // 等 PullToRefresh 释放手势后再回顶，避免在手势临界点抢占滚动。
                yield()
                targetGridState.scrollToItem(0)
            }
        }
        refreshDeltaTipText = if (count > 0) "新增 $count 条内容" else "暂无新内容"
        // The scroll/result has been handled; leaving during the tip must not replay it on return.
        viewModel.markRefreshNewItemsHandled(refreshKey)
        try {
            delay(2200)
        } finally {
            refreshDeltaTipText = null
        }
    }

    // 仅在推荐页检测“刷新后是否已下滑”，用于激活旧内容分割线
    LaunchedEffect(
        currentCategory,
        refreshNewItemsKey,
        refreshNewItemsCount,
        recommendOldContentAnchorBvid,
        recommendOldContentRevealKey
    ) {
        if (currentCategory != HomeCategory.RECOMMEND) return@LaunchedEffect
        if (!homeSettings.homeRefreshTipVisible) return@LaunchedEffect
        if ((refreshNewItemsCount ?: 0) <= 0) return@LaunchedEffect
        val targetKey = refreshNewItemsKey
        if (targetKey <= 0L || recommendOldContentRevealKey == targetKey) return@LaunchedEffect

        val anchorBvid = recommendOldContentAnchorBvid ?: return@LaunchedEffect
        val recommendState = gridStates[HomeCategory.RECOMMEND] ?: return@LaunchedEffect
        viewModel.getCategoryState(HomeCategory.RECOMMEND)
            .mapFlow { content ->
                content.videos.indexOfFirst { it.bvid == anchorBvid } to
                    resolveHomeCategoryVideoGridKeys(content.videos)
            }
            .distinctUntilChanged()
            .collectLatest { (anchorIndex, videoKeys) ->
                if (anchorIndex <= 0) return@collectLatest
                val videoIndicesByKey = videoKeys.withIndex().associate { it.value to it.index }
                snapshotFlow {
                    // Grid indices also include chrome, carousel and divider rows.
                    recommendState.layoutInfo.visibleItemsInfo.any {
                        (videoIndicesByKey[it.key] ?: -1) >= anchorIndex
                    }
                }.first { it }
                viewModel.markRecommendOldContentDividerRevealed(targetKey)
            }
    }

    //  [彩蛋] 关闭确认对话框
    if (showEasterEggDialog) {
        AppAlertDialog(
            onDismissRequest = { showEasterEggDialog = false },
            title = {
                AppText(
                    "关闭趣味提示？",
                    color = AppContentColors.primaryText()
                )
            },
            text = {
                AppText(
                    "关闭后下拉刷新将不再显示趣味消息。\n\n你可以在「设置」中随时重新开启。",
                    color = AppContentColors.secondaryText()
                )
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        coroutineScope.launch {
                            SettingsManager.setEasterEggEnabled(context, false)
                        }
                        showEasterEggDialog = false
                    }
                ) { AppText("关闭彩蛋", color = AppContentColors.error()) }
            },
            dismissButton = {
                AppTextButton(
                    onClick = { showEasterEggDialog = false }
                ) { AppText("保留彩蛋") }
            },
            containerColor = AppSurfaceTokens.cardContainer()
        )
    }

    //  [修复] 确保首页显示时 WindowInsets 配置正确，防止从视频页返回时布局跳动
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        // 保持边到边显示（与 VideoDetailScreen 一致）
        com.android.purebilibili.core.ui.AppWindowSystemUiController.ensureEdgeToEdge(window)
    }

    // 解构设置值（避免每次访问都触发重组）
    val effectiveHomeSettings = remember(homeSettings) {
        resolveEffectiveHomeSettings(
            homeSettings = homeSettings,
        )
    }
    val displayMode = homeSettings.displayMode
    val isBottomBarFloating = homeSettings.isBottomBarFloating
    val bottomBarLabelMode = homeSettings.bottomBarLabelMode
    val baseIsHeaderBlurEnabled = remember(homeSettings.headerBlurMode) {
        resolveHomeHeaderBlurEnabled(
            mode = homeSettings.headerBlurMode,
        )
    }
    val baseIsBottomBarBlurEnabled = homeSettings.isBottomBarBlurEnabled
    val crashTrackingConsentShown = homeSettings.crashTrackingConsentShown
    val baseCardAnimationEnabled = homeSettings.cardAnimationEnabled      //  卡片进场动画开关
    val baseCardTransitionEnabled = homeSettings.cardTransitionEnabled
    val baseIsDataSaverActive by remember(context) {
        com.android.purebilibili.core.store.DataSaverState.get(context).active
    }
    val homePerformanceConfig = remember(
        baseIsHeaderBlurEnabled,
        baseIsBottomBarBlurEnabled,
        homeSettings.isTopBarLiquidGlassEnabled,
        homeSettings.isHomeSearchLiquidGlassEnabled,
        homeSettings.isBottomBarLiquidGlassEnabled,
        baseCardAnimationEnabled,
        baseCardTransitionEnabled,
        baseIsDataSaverActive,
        homeSettings.androidNativeLiquidGlassEnabled,
        semanticVisualPolicy.supportsIndependentLiquidGlass
    ) {
        resolveHomePerformanceConfig(
            supportsIndependentLiquidGlass = semanticVisualPolicy.supportsIndependentLiquidGlass,
            headerBlurEnabled = baseIsHeaderBlurEnabled,
            bottomBarBlurEnabled = baseIsBottomBarBlurEnabled,
            topBarLiquidGlassEnabled = homeSettings.isTopBarLiquidGlassEnabled,
            homeSearchLiquidGlassEnabled = homeSettings.isHomeSearchLiquidGlassEnabled,
            bottomBarLiquidGlassEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
            androidNativeLiquidGlassEnabled = homeSettings.androidNativeLiquidGlassEnabled,
            cardAnimationEnabled = baseCardAnimationEnabled,
            cardTransitionEnabled = baseCardTransitionEnabled,
            isDataSaverActive = baseIsDataSaverActive,
            smartVisualGuardEnabled = false
        )
    }
    val isHeaderBlurEnabled = homePerformanceConfig.headerBlurEnabled
    val isBottomBarBlurEnabled = homePerformanceConfig.bottomBarBlurEnabled
    // [统一门控] 系统「减弱动效」是所有界面动效的通用开关:开启时关闭卡片进场/消散等所有卡片动效,
    // 各功能面自身的开关(此处为卡片动画开关)仍各自独立。与设置页入场动画共用同一 reduce-motion 判定。
    val systemReduceMotion = rememberSystemReduceMotion()
    val onDissolveCompleteCallback = remember(viewModel) {
        { bvid: String ->
            viewModel.completeVideoDissolve(bvid)
            if (dissolvingNotInterestedVideo?.bvid == bvid) {
                dissolvingNotInterestedVideo = null
            }
        }
    }
    val onDismissVideoCallback = remember {
        { video: VideoItem ->
            if (dissolvingNotInterestedVideo == null && pendingNotInterestedVideo == null) {
                targetVideoItemState.value = null
                pendingNotInterestedVideo = video
            }
        }
    }
    val confirmNotInterested: (VideoItem, RecommendationFeedbackReason) -> Unit = { video, reason ->
        pendingNotInterestedVideo = null
        val dissolveEnabled = !systemReduceMotion && isThanosEffectSupported(context)
        if (dissolveEnabled) dissolvingNotInterestedVideo = video
        viewModel.markNotInterested(
            video = video,
            reason = reason,
            dissolveAnimationEnabled = dissolveEnabled,
        )
    }
    LaunchedEffect(dissolvingNotInterestedVideo) {
        val video = dissolvingNotInterestedVideo ?: return@LaunchedEffect
        // A lazy card can leave composition before mounting its particle effect.
        delay(6_000L)
        if (dissolvingNotInterestedVideo?.bvid == video.bvid) {
            onDissolveCompleteCallback(video.bvid)
        }
    }
    val cardAnimationEnabled = homePerformanceConfig.cardAnimationEnabled && !systemReduceMotion
    // 过渡由用户设置控制；系统“减弱动效”开启时统一关闭。
    val cardTransitionEnabled = homePerformanceConfig.cardTransitionEnabled && !systemReduceMotion
    val isBottomBarLiquidGlassEnabled = homePerformanceConfig.bottomBarLiquidGlassEnabled
    val homeLiquidGlassTuning = remember(
        homeSettings.liquidGlassProgress,
        homeSettings.liquidGlassAdvancedSettings,
        homeSettings.liquidGlassReadabilityMode,
    ) {
        resolveLiquidGlassTuning(
            progress = homeSettings.liquidGlassProgress,
            advancedSettings = homeSettings.liquidGlassAdvancedSettings,
            readabilityMode = homeSettings.liquidGlassReadabilityMode,
        )
    }
    val isLiquidGlassEnabled = homePerformanceConfig.isAnyLiquidGlassEnabled
    val overlayPillColors = rememberHomeGlassPillColors(
        glassEnabled = isLiquidGlassEnabled,
        blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled,
        emphasized = true,
        baseColor = AppSurfaceTokens.cardContainer()
    )
    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val chromeCategoryStateFlow = remember(viewModel, currentCategory, popularSubCategory) {
        if (currentCategory == HomeCategory.POPULAR) {
            viewModel.getPopularCategoryState(popularSubCategory)
        } else {
            viewModel.getCategoryState(currentCategory)
        }
    }
    val chromeCategoryState by chromeCategoryStateFlow.collectAsHomeFeedState(homeFeedUpdatesAllowed)
    val chromeContentReady = !(chromeCategoryState.isLoading &&
        chromeCategoryState.videos.isEmpty() && chromeCategoryState.liveRooms.isEmpty())
    val homeChromeBlurEnabled = isLiquidGlassEnabled || isHeaderBlurEnabled ||
        isBottomBarBlurEnabled || appThemeConfig.progressiveTopBlurEnabled
    // 渐进模糊没有有效半径、系统不支持或被预算关闭时，不单独录制整个 feed。
    // 搜索、标签和底栏的独立磨砂/玻璃仍按原有需求保留背景源，纯色渐隐无需采样。
    val progressiveTopBlurNeedsBackdrop =
        com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur(
            enabled = appThemeConfig.progressiveTopBlurEnabled && !isHeaderBlurEnabled,
            hasBackdrop = true,
            blurRadiusDp = homeLiquidGlassTuning.progressiveBlurRadius,
        ) && !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val shouldCaptureHomeChromeBackdrop = isLiquidGlassEnabled || isHeaderBlurEnabled ||
        isBottomBarBlurEnabled || progressiveTopBlurNeedsBackdrop
    val homeMiuixBackdropSource = if (shouldCaptureHomeChromeBackdrop) {
        rememberChromeBackdropSource(isolateContentDrawing = true)
    } else {
        null
    }
    val homeMiuixBackdrop = homeMiuixBackdropSource?.backdrop
    val readyHomeMiuixBackdrop = homeMiuixBackdropSource?.takeIf {
        chromeContentReady && it.isReady
    }?.backdrop

    // 已有页面背景源时，只为仍消费 Haze 的原生顶栏保留额外采样。
    // 渐进模糊独立开启时顶栏不消费 Haze；抽屉的顶部模糊需求仍按原开关保留。
    val appUiStyle = com.android.purebilibili.core.theme.LocalAppUiStyle.current
    val shouldCaptureHomeHaze = if (homeMiuixBackdropSource != null) {
        com.android.purebilibili.feature.home.components.shouldUseOfficialMd3HomeTopToolbar(
            uiStyle = appUiStyle,
            liquidGlassEnabled = isLiquidGlassEnabled,
        ) && (!appThemeConfig.progressiveTopBlurEnabled || isHeaderBlurEnabled)
    } else {
        isLiquidGlassEnabled || isHeaderBlurEnabled || isBottomBarBlurEnabled
    }
    // 首页使用独立 HazeState，避免命中外层全局 source 的祖先过滤规则导致无模糊。
    // 实色路径不创建 source；普通模糊或玻璃路径才承担背景采样成本。
    val hazeState = if (shouldCaptureHomeHaze &&
        shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    ) {
        rememberRecoverableHazeState(initialBlurEnabled = true)
    } else {
        null
    }?.takeIf { recoverableBlurEnabled(it) }
    val isDataSaverActive = homePerformanceConfig.isDataSaverActive
    val preloadAheadCount = homePerformanceConfig.preloadAheadCount
    val configuredHomeWallpaperUri = com.android.purebilibili.core.ui.wallpaper.LocalHomeWallpaperSelection.current.uri
    val splashWallpaperUri by remember(context) {
        SettingsManager.getSplashWallpaperUri(context)
    }.collectAsStateWithLifecycle(initialValue = ""
        )
    val homeWallpaperUri = remember(configuredHomeWallpaperUri, splashWallpaperUri) {
        resolveHomeWallpaperUri(
            homeWallpaperUri = configuredHomeWallpaperUri,
            splashWallpaperUri = splashWallpaperUri
        )
    }
    val homeWallpaperIsStatic = com.android.purebilibili.core.ui.wallpaper.rememberIsStaticWallpaper(homeWallpaperUri)
    val wallpaperPalette = com.android.purebilibili.feature.home.components.cards.LocalWallpaperPalette.current

    val appNavigationSettings =
        com.android.purebilibili.feature.settings.rememberSettingsNavigationConfiguration()
    // 将字符串 ID 转换为 BottomNavItem 枚举
    val visibleBottomBarItems = remember(appNavigationSettings.orderedVisibleTabIds) {
        appNavigationSettings.orderedVisibleTabIds.mapNotNull { id ->
            try { BottomNavItem.valueOf(id) } catch (e: Exception) { null }
        }
    }
    val bottomBarItemColors = appNavigationSettings.bottomBarItemColors


    //  📐 [平板适配] 根据屏幕尺寸和展示模式动态设置网格列数
    // 故事卡片(1)和沉浸模式(2)需要单列全宽，网格(0)使用双列
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val appWindowAdaptiveInfo = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass, appWindowAdaptiveInfo.posture) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass,
            foldPosture = appWindowAdaptiveInfo.posture,
        )
    }
    val cardMotionTier = resolveEffectiveMotionTier(
        baseTier = deviceUiProfile.motionTier,
        animationEnabled = cardAnimationEnabled
    )
    val sharedTransitionDurationMillis =
        com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionDurationMillis(
            com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings.current,
            com.android.purebilibili.core.ui.transition.LocalVideoTransitionAdaptiveInfo.current,
        )
    val returnAnimationSuppressionDurationMs = resolveReturnAnimationSuppressionDurationMs(
        isTabletLayout = windowSizeClass.isTablet,
        cardAnimationEnabled = cardAnimationEnabled,
        cardTransitionEnabled = cardTransitionEnabled,
        sharedTransitionDurationMillis = sharedTransitionDurationMillis,
    )
    // Navigation 返回不一定触发首页 Lifecycle.ON_START，顶栏恢复必须直接跟随返回态。
    LaunchedEffect(isReturningFromVideoDetail, cardTransitionEnabled, isQuickReturningFromVideoDetail) {
        if (!isReturningFromVideoDetail) return@LaunchedEffect
        topTabsRevealJob?.cancel()
        returnAnimationStartElapsedMs = SystemClock.elapsedRealtime()
        hideTopTabsForForwardDetailNav = false
        val revealDelayMs = resolveHomeTopTabsRevealDelayMs(
            isReturningFromDetail = true,
            cardTransitionEnabled = cardTransitionEnabled,
            isQuickReturnFromDetail = isQuickReturningFromVideoDetail
        )
        if (revealDelayMs > 0L) {
            delayTopTabsUntilCardSettled = true
            topTabsRevealJob = coroutineScope.launch {
                delay(revealDelayMs)
                delayTopTabsUntilCardSettled = false
            }
        } else {
            delayTopTabsUntilCardSettled = false
        }
    }

    // 从详情页返回时延后清理“返回中”状态，避免卡片进场动画在共享转场期间抢跑造成闪屏。
    LaunchedEffect(returnAnimationSuppressionDurationMs, isReturningFromVideoDetail) {
        if (isReturningFromVideoDetail) {
            val startElapsedMs = if (returnAnimationStartElapsedMs > 0L) {
                returnAnimationStartElapsedMs
            } else {
                SystemClock.elapsedRealtime()
            }
            delay(returnAnimationSuppressionDurationMs)
            val actualDurationMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
            val isQuickReturn = isQuickReturningFromVideoDetail
            val sharedTransitionReady = cardTransitionEnabled &&
                CardPositionManager.lastClickedCardBounds != null &&
                CardPositionManager.isCardFullyVisible

            // 先清除"返回中"状态，让后续 LaunchedEffect 恢复底栏
            returnAnimationStartElapsedMs = 0L
            onVideoDetailReturnAnimationConsumed()

            val builtinPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledCount()
            val playerPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledPlayerPlugins().size
            val feedPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledFeedPlugins().size
            val danmakuPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledDanmakuPlugins().size
            val jsonEnabledPlugins = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled }
            val jsonFeedPluginEnabledCount = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled && it.plugin.type == "feed" }
            val jsonDanmakuPluginEnabledCount = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled && it.plugin.type == "danmaku" }
            com.android.purebilibili.core.util.AnalyticsHelper.logHomeReturnAnimationPerformance(
                actualDurationMs = actualDurationMs,
                plannedSuppressionMs = returnAnimationSuppressionDurationMs,
                sharedTransitionEnabled = cardTransitionEnabled,
                sharedTransitionReady = sharedTransitionReady,
                isQuickReturn = isQuickReturn,
                isTabletLayout = windowSizeClass.isTablet,
                cardAnimationEnabled = cardAnimationEnabled,
                builtinPluginEnabledCount = builtinPluginEnabledCount,
                playerPluginEnabledCount = playerPluginEnabledCount,
                feedPluginEnabledCount = feedPluginEnabledCount,
                danmakuPluginEnabledCount = danmakuPluginEnabledCount,
                jsonPluginEnabledCount = jsonEnabledPlugins,
                jsonFeedPluginEnabledCount = jsonFeedPluginEnabledCount,
                jsonDanmakuPluginEnabledCount = jsonDanmakuPluginEnabledCount
            )
        }
        if (CardPositionManager.isSwitchingCategory) {
            delay(300)
            CardPositionManager.isSwitchingCategory = false
        }
    }

    val contentWidth = if (windowSizeClass.isExpandedScreen) {
        minOf(windowSizeClass.widthDp, resolveHomeFeedMaxContentWidth())
    } else {
        windowSizeClass.widthDp
    }

    // 是否为单列模式 (Story or Cinematic)
    val isSingleColumnMode = displayMode == 1

    val gridColumns = remember(
        contentWidth,
        displayMode,
        homeSettings.effectiveGridColumnCount,
        homeSettings.effectiveGridColumnCountCompact,
        homeSettings.homeFeedCardWidthPreset,
        windowSizeClass.widthSizeClass
    ) {
        resolveHomeFeedGridColumns(
            contentWidthDp = contentWidth.value.toInt(),
            displayMode = displayMode,
            // 窄屏（折叠屏外屏/手机竖屏）与宽屏（内屏/平板）各自独立的固定列数记忆
            fixedColumnCount = resolveHomeFeedStoredColumnCount(
                widthSizeClass = windowSizeClass.widthSizeClass,
                compactColumnCount = homeSettings.effectiveGridColumnCountCompact,
                defaultColumnCount = homeSettings.effectiveGridColumnCount,
            ),
            cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val gridColumnPreference = rememberFeedGridColumnPreference(
        settings = homeSettings,
        widthSizeClass = windowSizeClass.widthSizeClass,
        defaultColumns = gridColumns,
        customizationApplies = !isSingleColumnMode,
        bounds = if (isSingleColumnMode) 1..2 else resolveHomeFeedPinchColumnBounds(
            windowSizeClass.widthSizeClass, contentWidth.value.toInt(),
        ),
    )
    var isPinchPillVisible by remember { mutableStateOf(false) }
    var pinchPillDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val effectiveGridColumns = gridColumnPreference.columns
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT)


    val homeFeedCardLayout = remember(
        homeFeedCardStyle,
        effectiveGridColumns,
        windowSizeClass.widthSizeClass,
    ) {
        resolveHomeFeedCardLayout(
            style = homeFeedCardStyle,
            gridColumns = effectiveGridColumns,
            widthSizeClass = windowSizeClass.widthSizeClass,
        )
    }
    val density = LocalDensity.current


    val tabletUseSidebar = appNavigationSettings.tabletUseSidebar

    //  📐 [大屏适配] 平板导航模式：根据用户偏好决定
    // 仅在 Expanded+ 且用户选择了侧边栏时使用侧边导航
    val useSideNavigation = com.android.purebilibili.core.util.shouldUseSidebarNavigationForLayout(
        windowSizeClass = windowSizeClass,
        tabletUseSidebar = tabletUseSidebar,
        foldPosture = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.posture,
    )
    val isHomeDrawerEnabled = com.android.purebilibili.core.util.shouldEnableHomeDrawer(
        useSideNavigation = useSideNavigation
    )

    //  📱 [切换导航模式] 处理函数
    val onToggleNavigationMode: () -> Unit = {
        coroutineScope.launch {
            SettingsManager.setTabletUseSidebar(context, !tabletUseSidebar)
        }
    }

    //  [修复] 恢复状态栏样式：确保从视频详情页返回后状态栏正确
    // 当使用滑动动画时，Theme.kt 的 SideEffect 可能不会重新执行
    val backgroundColor = AppSurfaceTokens.chromeBackground()
    val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }
    val useDarkStatusBarIcons = remember(homeUiSkinDecoration, isLightBackground) {
        resolveHomeStatusBarDarkIcons(
            hasTopSkinArtwork = !homeUiSkinDecoration?.topAtmosphereImagePath.isNullOrBlank(),
            skinColorMode = homeUiSkinDecoration?.colorMode,
            topSkinTintIsLight = homeUiSkinDecoration?.topAtmosphereTint?.luminance()?.let { it > 0.5f }
                ?: isLightBackground,
            defaultBackgroundIsLight = isLightBackground,
        )
    }
    val homeWallpaperBackdropAppearance = remember(
        homeWallpaperUri,
        homeSettings.homeWallpaperEffectMode,
        isLightBackground,
        isDataSaverActive
    ) {
        resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = homeWallpaperUri.isNotBlank(),
            effectMode = homeSettings.homeWallpaperEffectMode,
            isDarkTheme = !isLightBackground,
            isDataSaverActive = isDataSaverActive
        )
    }
    val shouldCaptureHomeWallpaperBackdrop =
        homeSettings.homeCardFrostedGlassEnabled &&
            homeWallpaperBackdropAppearance.visible &&
            homeWallpaperUri.isNotBlank() &&
            homeWallpaperIsStatic &&
            !isDataSaverActive
    val homeWallpaperBackdropSource = if (shouldCaptureHomeWallpaperBackdrop) {
        // Key the recorder by URI so a wallpaper replacement cannot briefly reuse the old
        // backdrop while the new image is being recorded.
        key(homeWallpaperUri) { rememberChromeBackdropSource() }
    } else {
        null
    }
    val readyHomeWallpaperBackdrop = homeWallpaperBackdropSource?.takeIf { it.isReady }?.backdrop

    if (!view.isInEditMode && shouldApplyHomeSystemBars(isTopLevelActive)) {
        SideEffect {
            val window = (context as? android.app.Activity)?.window ?: return@SideEffect
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            //  根据背景亮度设置状态栏/导航栏图标颜色。
            //  [关键] 图标明暗必须在系统栏隐藏时也照常写入：从沉浸播放页返回的瞬间
            //  系统栏尚未恢复可见，若因此跳过，insets 恢复不会触发重组，图标会停留
            //  在上一页的明暗（浅色模式下白色图标压白底不可见）。隐藏时写入不可见，
            //  且沉浸页面的 effect 会在自己的重组中重新 assert，无竞争副作用。
            insetsController.isAppearanceLightStatusBars = useDarkStatusBarIcons
            insetsController.isAppearanceLightNavigationBars = isLightBackground
            //  沉浸式播放覆盖层（小窗全屏播放等）隐藏系统栏期间，首页不得抢权恢复
            //  系统栏可见性/滑出行为，否则会打断播放器的沉浸；覆盖层退出时会通过
            //  快照自行还原。仅 show() 与 behavior 需要这层守卫。
            val systemBarsVisible = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.systemBars()) ?: true
            if (!systemBarsVisible) return@SideEffect
            //  [修复] 从直播/番剧等沉浸页面返回时导航栏可能仍被隐藏，系统栏整体恢复，
            //  并重置滑出行为，避免上一页面遗留 BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            com.android.purebilibili.core.ui.setWindowStatusBarColor(window, android.graphics.Color.TRANSPARENT)
            //  [修复] 导航栏也设为透明，确保底栏隐藏时手势区域沉浸
            com.android.purebilibili.core.ui.setWindowNavigationBarColor(window, android.graphics.Color.TRANSPARENT)
        }
    }

    val homeCoverRequestSpec = remember(
        contentWidth,
        effectiveGridColumns,
        homeFeedCardLayout,
        density.density,
        isDataSaverActive,
        homeSettings.lowQualityHomeCoverInDataSaver,
    ) {
        val cardWidthDp = (
            contentWidth.value -
                homeFeedCardLayout.outerPaddingDp * 2f -
                homeFeedCardLayout.itemSpacingDp * (effectiveGridColumns - 1).coerceAtLeast(0)
            ) / effectiveGridColumns.coerceAtLeast(1)
        resolveHomeCoverRequestSpec(
            cardWidthDp = cardWidthDp,
            density = density.density,
            useLowQualityCover =
                isDataSaverActive && homeSettings.lowQualityHomeCoverInDataSaver,
        )
    }
    //  [修复] 动态计算内容顶部边距，防止被头部遮挡
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val homeStartupElapsedAt = remember { SystemClock.elapsedRealtime() }
    var todayWatchStartupRevealHandled by rememberSaveable { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    //  当前选中的导航项
    var currentNavItem by remember { mutableStateOf(BottomNavItem.HOME) }

    // 统一导航点击逻辑（底栏/侧栏复用）
    val handleNavItemClick: (BottomNavItem) -> Unit = { item ->
        currentNavItem = item
        when (item) {
            BottomNavItem.HOME -> {
                val identity = currentHomeScrollRequestIdentity()
                coroutineScope.launch {
                    performHomeScrollRequest(identity, HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH)
                }
            }
            BottomNavItem.DYNAMIC -> onDynamicClick()
            BottomNavItem.HISTORY -> onHistoryClick()
            BottomNavItem.LISTEN_VIDEO -> Unit
            BottomNavItem.PROFILE -> onProfileClick()
            BottomNavItem.FAVORITE -> onFavoriteClick()
            BottomNavItem.LIVE -> onLiveListClick()
            BottomNavItem.WATCHLATER -> onWatchLaterClick()
            BottomNavItem.STORY -> onStoryClick()
            BottomNavItem.SETTINGS -> onSettingsClick()
            BottomNavItem.PLUGINS -> onPluginsClick()
        }
    }

    val bottomBarVisibilityMode = appNavigationSettings.bottomBarVisibilityMode

    //  [Refactor] 使用全局 CompositionLocal 控制底栏可见性
    val setBottomBarVisible = LocalSetBottomBarVisible.current
    val isGlobalBottomBarVisible = LocalBottomBarVisible.current
    // 兼容代码：为了最小化改动，将 bottomBarVisible 指向全局状态
    // 注意：这里的 bottomBarVisible 现在是只读的，修改必须通过 setBottomBarVisible
    val bottomBarVisible = isGlobalBottomBarVisible
    // App Shell keeps this stable while the bottom bar animates out, so the feed
    // content padding does not shift during tab/detail navigation.
    val homeListBottomPadding = LocalBottomBarContentPadding.current

    //  [修复] 跟踪是否正在导航到/从视频页 - 必须在 LaunchedEffect 之前声明
    var isVideoNavigating by remember { mutableStateOf(false) }
    var isHomeContentInteractionRestored by remember { mutableStateOf(true) }

    LaunchedEffect(isReturningFromVideoDetail, cardTransitionEnabled, isQuickReturningFromVideoDetail) {
        if (!isReturningFromVideoDetail) return@LaunchedEffect
        val restoreDelayMs = resolveHomeContentInteractionRestoreDelayMs(
            cardTransitionEnabled = cardTransitionEnabled,
            isQuickReturnFromDetail = isQuickReturningFromVideoDetail
        )
        if (restoreDelayMs > 0L) {
            delay(restoreDelayMs)
        }
        isHomeContentInteractionRestored = true
        isVideoNavigating = false
    }

    //  [新增] 滚动方向检测逻辑
    LaunchedEffect(
        currentCategory,
        popularSubCategory,
        bottomBarVisibilityMode,
        useSideNavigation,
        homeFeedWorkAllowed,
    ) {
        if (!homeFeedWorkAllowed) return@LaunchedEffect
        resolveHomeBottomBarBaseVisibility(
            useSideNavigation = useSideNavigation,
            mode = bottomBarVisibilityMode
        )?.let { isVisible ->
            setBottomBarVisible(isVisible)
            return@LaunchedEffect
        }

        // 向下浏览时隐藏模式：监听滚动方向
        val currentGridState = if (currentCategory == HomeCategory.POPULAR) {
            popularGridStates[popularSubCategory]
        } else {
            gridStates[currentCategory]
        } ?: return@LaunchedEffect
        var previousScrollState = HomeBottomBarScrollState(
            firstVisibleItem = currentGridState.firstVisibleItemIndex,
            scrollOffset = currentGridState.firstVisibleItemScrollOffset
        )
        snapshotFlow {
            Pair(currentGridState.firstVisibleItemIndex, currentGridState.firstVisibleItemScrollOffset)
        }
        .distinctUntilChanged()
        .collect { (firstVisibleItem, scrollOffset) ->
            val scrollUpdate = reduceHomeBottomBarListScroll(
                previousState = previousScrollState,
                firstVisibleItem = firstVisibleItem,
                scrollOffset = scrollOffset,
                isVideoNavigating = isVideoNavigating,
                contentInteractionRestored = isHomeContentInteractionRestored
            )

            previousScrollState = scrollUpdate.state
            when (scrollUpdate.visibilityIntent) {
                BottomBarVisibilityIntent.SHOW -> setBottomBarVisible(true)
                BottomBarVisibilityIntent.HIDE -> setBottomBarVisible(false)
                null -> Unit
            }
        }
    }

    // [New] State for side drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var bottomBarVisibleBeforeDrawer by remember { mutableStateOf<Boolean?>(null) }
    var drawerOpenRequested by remember { mutableStateOf(false) }

    // 抽屉打开时隐藏全局底栏，避免覆盖侧边栏底部内容
    val isDrawerStateOpenOrOpening = shouldEnableHomeDrawerGestures(
        currentValue = drawerState.currentValue,
        targetValue = drawerState.targetValue,
    )
    val shouldKeepDrawerBottomBarHidden = drawerOpenRequested || isDrawerStateOpenOrOpening
    LaunchedEffect(drawerState.currentValue, drawerState.targetValue) {
        if (drawerState.currentValue == DrawerValue.Closed && drawerState.targetValue == DrawerValue.Closed) {
            drawerOpenRequested = false
        }
    }
    LaunchedEffect(shouldKeepDrawerBottomBarHidden, isGlobalBottomBarVisible, useSideNavigation) {
        if (useSideNavigation) {
            drawerOpenRequested = false
            return@LaunchedEffect
        }

        if (shouldKeepDrawerBottomBarHidden) {
            if (bottomBarVisibleBeforeDrawer == null) {
                bottomBarVisibleBeforeDrawer = isGlobalBottomBarVisible
            }
            if (isGlobalBottomBarVisible) {
                setBottomBarVisible(false)
            }
        } else {
            bottomBarVisibleBeforeDrawer?.let { previousVisible ->
                setBottomBarVisible(previousVisible)
            }
            bottomBarVisibleBeforeDrawer = null
        }
    }
    val openHomeDrawer: () -> Unit = {
        if (!useSideNavigation) {
            drawerOpenRequested = true
            if (bottomBarVisibleBeforeDrawer == null) {
                bottomBarVisibleBeforeDrawer = isGlobalBottomBarVisible
            }
            if (isGlobalBottomBarVisible) {
                setBottomBarVisible(false)
            }
        }
        coroutineScope.launch { drawerState.open() }
    }

    // 选中槽位以 Pager 当前页为准，分区页不写入 currentCategory 也能保持高亮正确。
    val displayedTabIndex by remember(pagerState, topTabEntries) {
        derivedStateOf {
            pagerState.currentPage.coerceIn(0, (topTabEntries.size - 1).coerceAtLeast(0))
        }
    }

    //  根据滚动距离动态调整 BottomBar 可见性
    //  逻辑优化：使用 nestedScrollConnection 监听滚动
    var isHeaderVisible by rememberSaveable { mutableStateOf(true) }

    // Constants
    val topTabStyle = remember(
        isBottomBarFloating,
        isHeaderBlurEnabled,
        homePerformanceConfig.topBarLiquidGlassEnabled,
    ) {
        resolveTopTabStyle(
            isBottomBarFloating = isBottomBarFloating,
            isBottomBarBlurEnabled = isHeaderBlurEnabled,
            isLiquidGlassEnabled = homePerformanceConfig.topBarLiquidGlassEnabled,
        )
    }
    val topChromeMaterialMode = remember(
        isHeaderBlurEnabled,
        homePerformanceConfig.topBarLiquidGlassEnabled,
        appThemeConfig.progressiveTopBlurEnabled,
    ) {
        resolveHomeTopChromeMaterialMode(
            isHeaderBlurEnabled = isHeaderBlurEnabled,
            isBottomBarBlurEnabled = false,
            isLiquidGlassEnabled = homePerformanceConfig.topBarLiquidGlassEnabled,
            isProgressiveTopBlurEnabled = appThemeConfig.progressiveTopBlurEnabled,
        )
    }
    val homeTopPresetStyle = remember(topChromePolicy, homeSettings.topTabLabelMode) {
        resolveHomeTopPresetStyle(topChromePolicy, homeSettings.topTabLabelMode)
    }
    val homeTopSearchMetrics = resolveHomeTopSearchRowMetrics(
        configuredHeight = homeTopPresetStyle.searchBarHeight,
        configuredTabsSpacing = homeTopPresetStyle.searchToTabsSpacing,
        bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
        hideTopTabs = effectiveHomeSettings.hideTopTabs,
        keepTopSearch = homeSettings.keepHomeTopSearchWithBottomSearch,
    )
    val searchBarHeightDp = homeTopSearchMetrics.height
    val searchToTabsSpacingDp = homeTopSearchMetrics.tabsSpacing
    val tabRowHeightDp = resolveEffectiveHomeTabRowHeight(
        hideTopTabs = effectiveHomeSettings.hideTopTabs,
        defaultTabRowHeight = if (topTabStyle.floating) {
            homeTopPresetStyle.tabRowHeightFloating
        } else {
            homeTopPresetStyle.tabRowHeightDocked
        }
    )
    val searchCollapseDistanceDp = resolveHomeTopSearchScrollDistance(
        searchBarHeight = searchBarHeightDp,
        searchToTabsSpacing = searchToTabsSpacingDp,
        extraSpacing = homeTopPresetStyle.searchCollapseExtraSpacing,
        hideTopTabs = effectiveHomeSettings.hideTopTabs,
    )
    val floatingDockLift = if (effectiveHomeSettings.hideTopTabs) {
        AppSpacingTokens.None
    } else {
        resolveHomeTopTabYOffsetDp(topTabStyle.floating).dp
    }
    val chromeHeight = resolveEffectiveHomeTopChromeHeight(
        hideTopTabs = effectiveHomeSettings.hideTopTabs,
        useUnifiedPanel = homeTopPresetStyle.useUnifiedPanel,
        searchBarHeight = searchBarHeightDp,
        tabRowHeight = tabRowHeightDp,
        unifiedPanelInnerPadding = homeTopPresetStyle.unifiedPanelInnerPadding,
        searchToTabsSpacing = searchToTabsSpacingDp
    )
    // Android 12 (and older) may extend the legacy blur/glass fallback below its
    // measured bounds by a few pixels. Reserve a small safety gap so the first
    // content row cannot slide underneath the top dock on those devices.
    val legacyTopChromeSafetyGap = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
        AppSpacingTokens.Medium
    } else {
        AppSpacingTokens.None
    }
    val listTopPadding = statusBarHeight + chromeHeight +
        (if (effectiveHomeSettings.hideTopTabs) {
            AppSpacingTokens.Small
        } else {
            homeTopPresetStyle.tabsToContentSpacing + floatingDockLift -
                resolveHomeTabsToContentTighteningDp(appUiStyle, isLiquidGlassEnabled)
        }).coerceAtLeast(AppSpacingTokens.None) +
        legacyTopChromeSafetyGap

    // Pixels
    val searchCollapseDistancePx = with(density) { searchCollapseDistanceDp.toPx() }
    val headerCollapseMode = resolveHomeRecommendationHeaderCollapseMode(
        homeHeaderCollapseMode = homeSettings.homeHeaderCollapseMode
    )
    val homeBarHideType = homeSettings.homeBarHideType
    val collapseSearchOnScroll = headerCollapseMode.collapseSearch
    val collapseTabsOnScroll = headerCollapseMode.collapseTabs
    val isAnyHeaderCollapseEnabled = headerCollapseMode.hasAnyCollapse
    // 模式变化立即清理旧偏移，始终显示不依赖下一次滚动才能恢复。
    LaunchedEffect(headerCollapseMode) {
        setHeaderOffsetImmediate(0f)
        topTabsAutoCollapsedByScroll = false
    }
    val headerAutoCollapseDistancePx = if (isAnyHeaderCollapseEnabled) {
        searchCollapseDistancePx
    } else {
        0f
    }
    val embeddedPageTopPadding = rememberHomeEmbeddedPageTopPadding(
        expandedTopPadding = listTopPadding,
        statusBarHeight = statusBarHeight,
        tabRowHeight = tabRowHeightDp,
        tabsCollapsed = topTabsAutoCollapsedByScroll,
        headerOffsetProvider = headerOffsetProvider,
    )

    // [Feature] Bottom Bar Auto-Hide (based on scroll hide mode)
    val isBottomBarAutoHideEnabled = bottomBarVisibilityMode == SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE
    val bottomBarVisibleState = LocalSetBottomBarVisible.current

    // [Feature] Global Scroll Offset for Liquid Glass
    val activeGridState = if (currentCategory == HomeCategory.POPULAR) {
        popularGridStates[popularSubCategory]
    } else {
        gridStates[currentCategory]
    }

    // 刷新会话（下拉中、请求中、回弹中）由各页面上报，顶栏滚动连接据此不反向收起。
    // 每页注册自己的读取器；邻页预加载、刷新完成或销毁不覆盖当前页。
    val homeRefreshInteractionProviders = remember {
        mutableMapOf<HomeTopTabEntry, () -> Boolean>()
    }
    var subscriptionRefreshInteractionActive by remember { mutableStateOf(false) }

    val partitionListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val nestedScrollConnection = rememberHomeHeaderScrollConnection(
        configuration = HomeHeaderScrollConfiguration(
            collapseEnabled = isAnyHeaderCollapseEnabled,
            collapseDistancePx = headerAutoCollapseDistancePx,
            collapseTabs = collapseTabsOnScroll,
            bottomBarAutoHideEnabled = isBottomBarAutoHideEnabled,
            useSideNavigation = useSideNavigation,
            liquidGlassEnabled = isLiquidGlassEnabled,
            hideType = homeBarHideType,
            collapseMode = if (headerCollapseMode == HomeHeaderCollapseMode.SHOW_AT_TOP_ONLY) {
                CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY
            } else {
                CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            },
        ),
        pagerState = pagerState,
        topTabEntries = topTabEntries,
        activeGridStateProvider = {
            val entry = resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage)
            val category = (entry as? HomeTopTabEntry.Category)?.category
            when {
                category == null || shouldEmbedLivePageInHomeTopTab(category) ||
                    shouldEmbedBangumiPageInHomeTopTab(category) -> null
                category == HomeCategory.POPULAR -> popularGridStates[popularSubCategory]
                else -> gridStates[category]
            }
        },
        subscriptionListState = subscriptionListState,
        partitionListState = partitionListState,
        revealLocked = homeHeaderRevealLock,
        refreshInteractionActiveProvider = {
            val entry = resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage)
            if (entry == HomeTopTabEntry.Subscriptions) {
                subscriptionRefreshInteractionActive
            } else {
                homeRefreshInteractionProviders[entry]?.invoke() == true
            }
        },
        headerOffsetProvider = headerOffsetProvider,
        globalScrollOffset = globalScrollOffset,
        onHeaderOffsetChanged = { offsetPx, shouldAnimate ->
            if (shouldAnimate) {
                animateHeaderOffsetTo(offsetPx)
            } else {
                setHeaderOffsetImmediate(offsetPx)
            }
        },
        onTabsCollapsedChanged = { topTabsAutoCollapsedByScroll = it },
        onBottomBarVisibleChanged = bottomBarVisibleState,
    )
    val latestHomePopularSubCategory by rememberUpdatedState(popularSubCategory)
    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(
                resolveHomeTopTabEntryOrNull(latestHomeTopTabEntries, pagerState.currentPage),
                pagerState.isScrollInProgress,
                latestHomeTopLevelActive,
            ) to latestHomePopularSubCategory
        }
            .distinctUntilChanged()
            .collect {
                homeNavigationRevision++
                // 切页、切热门子分类或离开首页都使旧回顶请求失效，并停在当前高度。
                setHeaderOffsetImmediate(headerOffsetProvider())
            }
    }
    //  包装 onVideoClick：点击视频时先隐藏底栏再导航
    val wrappedOnVideoClick: (HomeVideoClickRequest) -> Unit = remember(
        feedNavigation,
        currentCategory,
        popularSubCategory,
        gridStates,
        popularGridStates
    ) {
        homeClick@{ request ->
            // 上一次详情转场未落位时忽略新的卡片点击：并发的第二次导航会让共享元素
            // 注册表在第一场转场中途重新协商，旧卡标题/作者会短暂跳变成新卡内容。
            if (isVideoNavigating) return@homeClick
            if (request.episodeId > 0L ||
                (request.dynamicId.isNotBlank() && !request.bvid.startsWith("BV", ignoreCase = true))) {
                feedNavigation.onVideoClick(request)
                return@homeClick
            }
            val activeGridState = if (currentCategory == HomeCategory.POPULAR) {
                popularGridStates[popularSubCategory]
            } else {
                gridStates[currentCategory]
            }
            // Capture before changing chrome/navigation state. Shared-bounds can remeasure the
            // underlying staggered grid as soon as navigation starts, which is too late for a clean anchor.
            if (activeGridState != null) {
                pendingFeedScrollAnchor = captureHomeFeedScrollAnchor(
                    category = currentCategory,
                    popularSubCategory = popularSubCategory,
                    firstVisibleItemIndex = activeGridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = activeGridState.firstVisibleItemScrollOffset,
                    headerOffsetPx = headerOffsetHeightPx
                )
            }
            hideTopTabsForForwardDetailNav = false
            delayTopTabsUntilCardSettled = false
            isVideoNavigating = true
            isHomeContentInteractionRestored = false
            feedNavigation.onVideoClick(request)
        }
    }
    val onTodayWatchVideoClick: (VideoItem) -> Unit = remember(viewModel, wrappedOnVideoClick) {
        { video ->
            viewModel.markTodayWatchVideoOpened(video)
            wrappedOnVideoClick(
                HomeVideoClickRequest(
                    bvid = video.bvid,
                    cid = video.cid,
                    coverUrl = video.pic,
                    isVerticalVideo = video.resolveIsVerticalForNavigation(),
                    source = HomeVideoClickSource.TODAY_WATCH,
                    sourceRoute = resolveHomeCategoryVideoSourceRoute(HomeCategory.RECOMMEND)
                )
            )
        }
    }

    // [TodayWatch首曝] 冷启动启动窗口内自动回顶一次，确保用户能看到今日推荐单卡片。
    LaunchedEffect(
        todayWatchPluginEnabled,
        todayWatchPlan?.generatedAt,
        currentCategory,
        homeFeedWorkAllowed,
    ) {
        if (!homeFeedWorkAllowed || todayWatchStartupRevealHandled) return@LaunchedEffect

        val recommendGridState = gridStates[HomeCategory.RECOMMEND] ?: return@LaunchedEffect
        val decision = decideTodayWatchStartupReveal(
            startupElapsedMs = SystemClock.elapsedRealtime() - homeStartupElapsedAt,
            isPluginEnabled = todayWatchPluginEnabled,
            currentCategory = currentCategory,
            hasTodayPlan = todayWatchPlan != null && !todayWatchCollapsed,
            firstVisibleItemIndex = recommendGridState.firstVisibleItemIndex,
            firstVisibleItemOffset = recommendGridState.firstVisibleItemScrollOffset
        )

        when (decision) {
            TodayWatchStartupRevealDecision.REVEAL -> {
                setHeaderOffsetImmediate(0f)
                if (recommendGridState.firstVisibleItemIndex > 12) {
                    recommendGridState.scrollToItem(12)
                }
                recommendGridState.animateScrollToItem(0)
                setHeaderOffsetImmediate(0f)
                globalScrollOffset.floatValue = 0f
                todayWatchStartupRevealHandled = true
            }
            TodayWatchStartupRevealDecision.SKIP -> {
                todayWatchStartupRevealHandled = true
            }
            TodayWatchStartupRevealDecision.WAIT -> Unit
        }
    }

    //  Scaffold 内容封装 (用于 Panel 左右布局复用)
    // 过渡动画关闭时时钟恒为 IDLE，景深 effect 只是空转的 draw 包装：整层摘掉。
    val cardTransitionGatesHomeDepth = LocalSharedTransitionEnabled.current
    val homeFeedOwnsVideoCardSnapshot = cardTransitionGatesHomeDepth &&
        shouldHomeFeedOwnVideoCardTransitionSnapshot(
            sourceRoute = videoCardTransitionBackgroundState.sourceRouteProvider(),
            hasSnapshotHandle = videoCardTransitionBackgroundState.snapshotHandle != null,
        )
    val homeFeedSnapshotModifier = if (homeFeedOwnsVideoCardSnapshot) {
        val backgroundSource = resolveVideoCardTransitionBackgroundSource(
            videoCardTransitionBackgroundState.sourceRouteProvider(),
        )
        Modifier.videoCardTransitionBackgroundEffect(
            progressProvider = videoCardTransitionBackgroundState.progressProvider,
            phaseProvider = videoCardTransitionBackgroundState.phaseProvider,
            exposureProvider = videoCardTransitionBackgroundState.exposureProvider,
            isGestureRestoreInProgressProvider =
                videoCardTransitionBackgroundState.isGestureRestoreInProgressProvider,
            motionTierProvider = videoCardTransitionBackgroundState.motionTierProvider,
            isLightBackgroundProvider = videoCardTransitionBackgroundState.isLightBackgroundProvider,
            realtimeBlurEnabledProvider = {
                shouldUseRealtimeVideoCardTransitionBackgroundBlur(
                    source = backgroundSource,
                    realtimeBlurEnabled = videoCardTransitionBackgroundState
                        .realtimeBlurEnabledProvider(),
                )
            },
            scaleReductionProvider = {
                resolveVideoCardTransitionBackgroundScaleReduction(backgroundSource)
            },
            sourceBoundsProvider = videoCardTransitionBackgroundState.sourceBoundsProvider,
            snapshotHandle = videoCardTransitionBackgroundState.snapshotHandle,
            // 清晰底图仅复用无视频壁纸、无横幅自动播放的首页；AndroidView/视频画面
            // 仍由实时路径绘制，不能把 GraphicsLayer 录制当作其有效像素来源。
            reuseJustRecordedSourceForHandoff =
                (homeWallpaperUri.isBlank() || homeWallpaperIsStatic) &&
                    !homeSettings.homeHeroCarouselAutoplayEnabled,
        )
    } else {
        Modifier
    }
    val scaffoldLayout: @Composable () -> Unit = {
        // Composite header and feed before applying depth; separate blur layers form a seam.
        Box(modifier = Modifier.fillMaxSize().then(homeFeedSnapshotModifier)) {
        AppScaffold(
                // 顶部渐进模糊和玻璃控件沿用 feed 背景；避免 scaffold 再录制相同内容。
                topBarIconBackdropSource = homeMiuixBackdropSource,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                containerColor = AppSurfaceTokens.chromeBackground(),
                bottomBar = {
                   // BottomBar logic handled by parent
                },
                contentWindowInsets = WindowInsets(AppSpacingTokens.None)
            ) { padding ->
                   // [Refactor] Use Box to allow overlay and proper blur nesting
                   // [新增] Video Preview State (Long Press)

                    CompositionLocalProvider(
                        LocalHomeMiuixBackdrop provides homeMiuixBackdrop,
                        LocalHomeWallpaperBackdrop provides readyHomeWallpaperBackdrop,
                        LocalHomeWallpaperBackdropReady provides (readyHomeWallpaperBackdrop != null),
                        LocalHomeWallpaperIsStatic provides (
                            homeWallpaperUri.isNotBlank() && homeWallpaperIsStatic
                        ),
                        com.android.purebilibili.feature.home.components.cards.LocalWallpaperPalette provides wallpaperPalette,
                    ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Header samples the untransformed feed; the enclosing snapshot
                            // then transforms their completed composition exactly once.
                            .then(homeMiuixBackdropSource?.modifier ?: Modifier)
                            // 首页使用 Pager + Lazy 子层，source 挂在外层容器更稳定。
                            .then(
                                if (hazeState != null) {
                                    Modifier.hazeSourceCompat(state = hazeState)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                    HomeWallpaperBackdrop(
                        wallpaperUri = homeWallpaperUri,
                        playbackEnabled = isTopLevelActive,
                        appearance = homeWallpaperBackdropAppearance,
                        baseColor = AppSurfaceTokens.chromeBackground(),
                        isDataSaverActive = isDataSaverActive,
                        modifier = homeWallpaperBackdropSource?.modifier ?: Modifier
                    )
                    // [Fix] Re-enabled default overscroll for better feedback
                        val homeTopPagerSwipeEnabled =
                            shouldEnableHomeTopPagerUserScroll(
                                isTopLevelActive = isTopLevelActive,
                                hideTopTabs = effectiveHomeSettings.hideTopTabs
                            )
                        com.android.purebilibili.core.ui.adaptive.AppHingeSafeContent(
                            modifier = Modifier.responsiveContentWidth(maxWidth = contentWidth).fillMaxSize(),
                        ) {
                            val requestedGridColumns = effectiveGridColumns
                            val effectiveGridColumns = if (appWindowAdaptiveInfo.shouldAvoidHinge) {
                                com.android.purebilibili.core.ui.adaptive.resolveHingeSafeFeedColumns(
                                    requestedGridColumns, maxWidth.value,
                                    if (isSingleColumnMode) 280 else homeSettings.homeFeedCardWidthPreset.minCardWidthDp ?: 180,
                                )
                            } else requestedGridColumns
                            val homeFeedCardLayout = resolveHomeFeedCardLayout(
                                style = homeFeedCardStyle,
                                gridColumns = effectiveGridColumns,
                                widthSizeClass = windowSizeClass.widthSizeClass,
                            )
                            val homeFeedCoverAspectRatio = homeFeedCardLayout.coverAspectRatio
                            val pinchColumnBounds = resolveHomeFeedPinchColumnBounds(
                                widthSizeClass = windowSizeClass.widthSizeClass,
                                contentWidthDp = maxWidth.value.toInt(),
                                displayMode = displayMode,
                            )
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 0,
                            userScrollEnabled = homeTopPagerSwipeEnabled,
                            shouldYieldGesture = shouldYieldHomePagerToHeroCarousel,
                            modifier = Modifier
                                .fillMaxSize(),
                            key = { index -> resolveHomeTopTabEntryKey(topTabEntries, index) }
                        ) { page ->
                        when (val entry = resolveHomeTopTabEntryOrNull(topTabEntries, page)) {
                            HomeTopTabEntry.Partition -> {
                                CompositionLocalProvider(
                                    LocalVideoCardSharedElementSourceRoute provides partitionVideoSourceRoute
                                ) {
                                    PartitionContent(
                                        contentPadding = PaddingValues(
                                            top = listTopPadding,
                                            bottom = homeListBottomPadding,
                                            start = AppSpacingTokens.Large,
                                            end = AppSpacingTokens.Large
                                        ),
                                        onVideoClick = onPartitionVideoClick,
                                        onBangumiClick = onBangumiClick,
                                        onBangumiSeasonClick = onBangumiSeasonClick,
                                        listState = partitionListState,
                                        scrollToTopRequestId = partitionScrollToTopRequestId
                                    )
                                }
                            }
                            HomeTopTabEntry.Subscriptions -> {
                                com.android.purebilibili.feature.home.subscription.SubscriptionFeedPage(
                                    contentPadding = PaddingValues(
                                        top = listTopPadding,
                                        bottom = homeListBottomPadding,
                                        start = AppSpacingTokens.Large,
                                        end = AppSpacingTokens.Large,
                                    ),
                                    onOpenPluginSettings = onPluginsClick,
                                    articleContentPadding = PaddingValues(
                                        top = statusBarHeight + AppSpacingTokens.Small,
                                        bottom = homeListBottomPadding,
                                        start = AppSpacingTokens.Large,
                                        end = AppSpacingTokens.Large,
                                    ),
                                    scrollToTopRequestId = subscriptionScrollToTopRequestId,
                                    onRefreshInteractionActiveChanged = { subscriptionRefreshInteractionActive = it },
                                    listState = subscriptionListState,
                                    gridColumns = effectiveGridColumns,
                                    pinchEnabled = !isSingleColumnMode && homeSettings.gridColumnPinchEnabled,
                                    pinchBounds = pinchColumnBounds,
                                    onColumnsChange = { newColumns ->
                                        gridColumnPreference.onChange(newColumns)
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        isPinchPillVisible = true
                                        pinchPillDismissJob?.cancel()
                                    },
                                    onArticleOpenChanged = { subscriptionArticleOpen = it },
                                    onArticleScrollInProgressChanged = { subscriptionArticleScrolling = it },
                                    onPinchEnd = { finalColumns ->
                                        gridColumnPreference.onGestureEnd(finalColumns)
                                        pinchPillDismissJob?.cancel()
                                        pinchPillDismissJob = coroutineScope.launch {
                                            kotlinx.coroutines.delay(1000)
                                            isPinchPillVisible = false
                                        }
                                    },
                                )
                            }
                            is HomeTopTabEntry.Category -> {
                        val category = entry.category
                        if (shouldEmbedLivePageInHomeTopTab(category)) {
                            HomeEmbeddedPageContent(embeddedPageTopPadding) { topPadding ->
                                LiveListScreen(
                                    onBack = {},
                                    onLiveClick = onLiveClick,
                                    onSearchClick = onLiveSearchClick,
                                    onMatchClick = onLiveMatchClick,
                                    onAreaListClick = onLiveAreaClick,
                                    onFollowingClick = onLiveFollowingClick,
                                    onUserClick = onSpaceClick,
                                    onAreaDetailClick = onLiveAreaDetailClick,
                                    showNavigationBack = false,
                                    embeddedInHome = true,
                                    isCurrentPage = isTopLevelActive && pagerState.currentPage == page,
                                    contentTopPadding = topPadding,
                                    scrollToTopRequestId = liveScrollToTopRequestId,
                                )
                            }
                        } else if (shouldEmbedBangumiPageInHomeTopTab(category)) {
                            HomeEmbeddedPageContent(embeddedPageTopPadding) { topPadding ->
                                HomeBangumiTabPage(
                                    contentPadding = PaddingValues(
                                        top = topPadding,
                                        bottom = homeListBottomPadding
                                    ),
                                    onBangumiClick = onBangumiSeasonClick,
                                    onBangumiEpisodeClick = onBangumiEpisodeClick,
                                    onPlayFollow = onBangumiPlayFollow,
                                    scrollToTopRequestId = bangumiScrollToTopRequestId,
                                )
                            }
                        } else {
                        val categoryStateFlow = remember(viewModel, category, popularSubCategory) {
                            if (category == HomeCategory.POPULAR) {
                                viewModel.getPopularCategoryState(popularSubCategory)
                            } else {
                                viewModel.getCategoryState(category)
                            }
                        }
                        val categoryState by categoryStateFlow.collectAsHomeFeedState(homeFeedUpdatesAllowed)

                        //  独立的 PullToRefreshState，避免所有页面共享一个状态导致冲突
                        val pullRefreshState = rememberPullToRefreshState()
                        val pullDistanceFraction = pullRefreshState.distanceFraction
                        val isPageRefreshing = isRefreshing && currentCategory == category
                        val currentPageRefreshing by rememberUpdatedState(isPageRefreshing)
                        val refreshInteractionProvider = remember(pullRefreshState) {
                            {
                                currentPageRefreshing || pullRefreshState.distanceFraction > 0f ||
                                    pullRefreshState.isAnimating
                            }
                        }
                        DisposableEffect(entry, refreshInteractionProvider) {
                            homeRefreshInteractionProviders[entry] = refreshInteractionProvider
                            onDispose {
                                if (homeRefreshInteractionProviders[entry] === refreshInteractionProvider) {
                                    homeRefreshInteractionProviders.remove(entry)
                                }
                            }
                        }
                        var stablePullOffsetFraction by remember { mutableFloatStateOf(0f) }

                        //  下拉物理由策略区分：MD3 截图式跟随当前手指距离回收，旧 iOS 弹性保留防抖滞后。
                        val resolvedStablePullOffsetFraction = resolveStablePullContentOffsetFraction(
                            distanceFraction = pullDistanceFraction,
                            isRefreshing = isPageRefreshing,
                            isStateAnimating = pullRefreshState.isAnimating,
                            previousOffsetFraction = stablePullOffsetFraction,
                            motionStyle = pullRefreshMotionStyle,
                            indicatorStyle = pullRefreshIndicatorStyle
                        )
                        SideEffect {
                            stablePullOffsetFraction = resolvedStablePullOffsetFraction
                        }

                        //  使用 animateFloatAsState 包装偏移量
                        val animatedDragOffsetFraction = androidx.compose.animation.core.animateFloatAsState(
                            targetValue = resolvedStablePullOffsetFraction,
                            animationSpec = if (
                                systemReduceMotion || !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.uiEntranceAnimationEnabled ||
                                shouldSnapPullOffsetToFinger(
                                    distanceFraction = pullDistanceFraction,
                                    isRefreshing = isPageRefreshing,
                                    isStateAnimating = pullRefreshState.isAnimating,
                                    indicatorStyle = pullRefreshIndicatorStyle
                                )
                            ) {
                                androidx.compose.animation.core.snap()
                            } else {
                                com.android.purebilibili.core.ui.components.scrollFeedbackSpring(
                                    com.android.purebilibili.core.ui.components.LocalScrollFeedbackPreset.current,
                                    damping = 0.96f,
                                    stiffness = 520f,
                                )
                            },
                            label = "pull_bounce"
                        )

                        //  Defers calculation to graphicsLayer
                        val calculateDragOffset: androidx.compose.ui.unit.Density.() -> Float = remember(
                            animatedDragOffsetFraction,
                            pullRefreshIndicatorStyle
                        ) {
                            {
                                val maxPx = resolvePullContentMaxOffsetDp(pullRefreshIndicatorStyle).dp.toPx()
                                maxPx * animatedDragOffsetFraction.value
                            }
                        }

                        //  每个页面独立的 GridState
                        //  使用 saveable 记住滚动位置
                        val pageGridState = if (category == HomeCategory.POPULAR) {
                            popularGridStates[popularSubCategory] ?: rememberHomeStaggeredGridState()
                        } else {
                            gridStates[category] ?: rememberHomeStaggeredGridState()
                        }

                        //  把 GridState 提升给父级用于控制 Header?

                        // Overlay chrome (status + search + tabs); not for Scaffold-padded screens.
                        // 指示器 inset 跟随可见顶栏（含收起偏移与 tab 收起动画），内容间距保持不变。
                        HomeFeedPullToRefreshBox(
                            isRefreshing = isRefreshing && currentCategory == category,
                            onRefresh = {
                                viewModel.refresh(category)
                            },
                            state = pullRefreshState,
                            indicatorTopInset = embeddedPageTopPadding,
                            modifier = Modifier.fillMaxSize(),
                             //  不同原生外观使用不同下拉刷新指示器，位移策略仍由 policy 统一控制。
                             //  Custom indicators must include the same top inset as MIUIX contentPadding.
                             indicator = { homeRefreshIndicatorTopInset ->
                                when (pullRefreshIndicatorStyle) {
                                    AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT -> {
                                        // Native MD3 pull-to-refresh indicator.
                                        AppPullRefreshLoadingIndicator(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = homeRefreshIndicatorTopInset),
                                            isRefreshing = isPageRefreshing,
                                            state = pullRefreshState
                                        )
                                    }
                                    AppPullRefreshIndicatorStyle.MIUIX_NATIVE -> Unit
                                    AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE -> {
                                        val indicatorHeight = resolveMd3ScreenshotRefreshIndicatorHeightDp(
                                            progress = pullDistanceFraction,
                                            isRefreshing = isPageRefreshing
                                        ).dp
                                        val indicatorTotalHeight = resolveMd3ScreenshotRefreshIndicatorTotalHeightDp(
                                            indicatorHeightDp = indicatorHeight.value,
                                            hasHintText = pullDistanceFraction > 0f || isPageRefreshing
                                        ).dp
                                        Md3ScreenshotRefreshIndicator(
                                            state = pullRefreshState,
                                            isRefreshing = isPageRefreshing,
                                            indicatorHeight = indicatorHeight,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = homeRefreshIndicatorTopInset)
                                                .zIndex(1f)
                                                .graphicsLayer {
                                                    val currentDragOffset = calculateDragOffset()
                                                    translationY = resolveMd3ScreenshotRefreshIndicatorTranslationY(
                                                        dragOffsetPx = currentDragOffset,
                                                        indicatorTotalHeightPx = indicatorTotalHeight.toPx(),
                                                        minGapPx = AppSpacingTokens.Small.toPx()
                                                    )
                                                }
                                                .fillMaxWidth()
                                        )
                                    }
                                    AppPullRefreshIndicatorStyle.CUPERTINO -> {
                                        HomeRefreshIndicator(
                                            state = pullRefreshState,
                                            isRefreshing = isPageRefreshing,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = homeRefreshIndicatorTopInset)
                                                .zIndex(1f)
                                                .graphicsLayer {
                                                    val currentDragOffset = calculateDragOffset()
                                                    val indicatorHeight = (
                                                        AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small
                                                    ).toPx()
                                                    val minGap = AppSpacingTokens.Small.toPx()
                                                    translationY = resolvePullIndicatorTranslationY(
                                                        dragOffsetPx = currentDragOffset,
                                                        indicatorHeightPx = indicatorHeight,
                                                        minGapPx = minGap,
                                                        isRefreshing = isPageRefreshing
                                                    )
                                                }
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                             }
                        ) {
                             // [物理优化] 内容容器应用下沉效果
                              Box(
                                  modifier = Modifier
                                      .fillMaxSize()
                                      .zIndex(0f)
                                       .graphicsLayer {
                                           translationY = when (pullRefreshIndicatorStyle) {
                                               AppPullRefreshIndicatorStyle.MIUIX_NATIVE -> 0f
                                               AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT ->
                                                   resolveMd3PullContentOffsetPx(
                                                       distanceFraction = pullRefreshState.distanceFraction,
                                                       indicatorThresholdPx = PullToRefreshDefaults.PositionalThreshold.toPx(),
                                                       gapPx = 12.dp.toPx(),
                                                   )
                                               else -> calculateDragOffset()
                                           }
                                       }
                                       .homeFeedPinchZoom(
                                           enabled = !isSingleColumnMode && homeSettings.gridColumnPinchEnabled,
                                           currentColumns = effectiveGridColumns,
                                           bounds = pinchColumnBounds,
                                           onColumnsChange = { newColumns ->
                                               gridColumnPreference.onChange(newColumns)
                                               haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                               isPinchPillVisible = true
                                               pinchPillDismissJob?.cancel()
                                           },
                                           onGestureEnd = { finalColumns ->
                                               gridColumnPreference.onGestureEnd(finalColumns)
                                               pinchPillDismissJob?.cancel()
                                               pinchPillDismissJob = coroutineScope.launch {
                                                   kotlinx.coroutines.delay(1000)
                                                   isPinchPillVisible = false
                                               }
                                           }
                                       )
                              ) {
                              // Loading Skeleton per page
                              // 骨架不再自建独立的 LazyVerticalStaggeredGrid：它与内容网格不是同一个
                              // LazyLayout 实例，切换时整屏重建、滚动锚点丢失。改为把骨架 item 交给内容
                              // 网格（同一个 gridState），由它在原地铺占位卡。
                              val showInitialSkeleton = category != HomeCategory.POPULAR &&
                                  categoryState.isLoading &&
                                  categoryState.videos.isEmpty() &&
                                  categoryState.liveRooms.isEmpty()
                              // [性能优化] 脉冲 state 只包进 provider,值在骨架卡 draw 阶段读取,
                              // 骨架期间屏幕级组合作用域不再逐帧失效;非骨架期不启动无限动画。
                              val skeletonPulseState = if (showInitialSkeleton) {
                                  rememberHomeFeedSkeletonPulseState()
                              } else {
                                  remember { mutableStateOf(0f) }
                              }
                              // remember 住骨架 provider：它的身份稳定，内容网格才不会因为
                              // 每次重组换新 lambda 而反复重组。
                              val initialSkeletonContent: (LazyStaggeredGridScope.() -> Unit)? = remember(
                                  showInitialSkeleton,
                                  category,
                                  homeSettings.homeHeroCarouselEnabled,
                                  effectiveGridColumns,
                                  homeWallpaperBackdropAppearance.visible,
                                  homeSettings.homeWallpaperEffectMode,
                                  isDataSaverActive,
                                  homeFeedCoverAspectRatio,
                                  homeFeedCardLayout,
                                  displayMode,
                                  homeSettings.compactVideoStatsOnCover,
                                  homeSettings.showHomePublishTime,
                                  skeletonPulseState,
                              ) {
                                  if (!showInitialSkeleton) return@remember null
                                  {
                                      // [新增] 用户启用首页横幅时，骨架顶部渲染横幅占位，
                                      // 与加载完成后的 HomeHeroCarousel 布局对齐
                                      if (category == HomeCategory.RECOMMEND && homeSettings.homeHeroCarouselEnabled) {
                                          item(
                                              key = "home_hero_carousel_skeleton",
                                              contentType = "home_hero_carousel_skeleton",
                                              span = StaggeredGridItemSpan.FullLine
                                          ) {
                                              HomeFeedHeroCarouselSkeleton(
                                                  pulse = { skeletonPulseState.value }
                                              )
                                          }
                                      }
                                      // [Fix] Dynamic skeleton count to fill tablet screens (at least 5 rows)
                                      val skeletonItemCount = effectiveGridColumns * 5
                                      items(
                                          count = skeletonItemCount,
                                          key = { it },
                                          contentType = { "home_feed_skeleton_card" }
                                      ) {
                                          HomeFeedSkeletonCard(
                                              modifier = Modifier.animateItem(),
                                              pulse = { skeletonPulseState.value },
                                              wallpaperTintEnabled = homeWallpaperBackdropAppearance.visible,
                                              wallpaperEffectMode = homeSettings.homeWallpaperEffectMode,
                                              isDataSaverActive = isDataSaverActive,
                                              coverAspectRatio = homeFeedCoverAspectRatio,
                                              compactMetadata = homeFeedCardLayout.compactMetadata,
                                              compactStatsOnCover = homeSettings.compactVideoStatsOnCover || homeFeedCardLayout.compactStatsOnCover,
                                              titleMinLines = homeFeedCardLayout.titleMinLines,
                                              showPublishTime = homeSettings.showHomePublishTime,
                                              storyStyle = displayMode == 1,
                                              storyHorizontalPadding = homeFeedCardLayout.storyCardHorizontalPaddingDp.dp,
                                          )
                                      }
                                  }
                              }
                              val categoryError = categoryState.error
                                 if (categoryError != null && categoryState.videos.isEmpty()) {
                                 // Error State per page
                                 Box(
                                     Modifier
                                         .fillMaxSize()
                                         .padding(top = listTopPadding, bottom = homeListBottomPadding),
                                     contentAlignment = Alignment.Center
                                 ) {
                                     ModernErrorState(
                                         message = categoryError,
                                         isVisible = pagerState.currentPage == page,
                                         onRetry = { viewModel.refresh(category) }
                                     )
                                 }
                                 } else {
                                 // Data Content
                                 // [性能优化] Stabilize event callbacks to prevent recomposition on scroll
                                 val onLoadMoreCallback = remember(viewModel, category, popularSubCategory) {
                                     { viewModel.loadMore(category, popularSubCategory) }
                                 }
                                 val onWatchLaterCallback = remember(viewModel) { { bvid: String, aid: Long -> viewModel.addToWatchLater(bvid, aid) } }
                                  val onLongPressCallback = remember(
                                      targetVideoItemState,
                                      homeSettings.videoCardLongPressActionEnabled
                                  ) {
                                      if (homeSettings.videoCardLongPressActionEnabled) {
                                          { item: VideoItem -> targetVideoItemState.value = item }
                                      } else {
                                          null
                                      }
                                  }
                                 val onLiveClickCallback = feedNavigation.onLiveClick
                                 val onTodayWatchModeChange = remember(viewModel) { { mode: TodayWatchMode -> viewModel.switchTodayWatchMode(mode) } }
                                 val onTodayWatchCollapsedChange = remember(viewModel) { { collapsed: Boolean -> viewModel.setTodayWatchCollapsed(collapsed) } }
                                 val onTodayWatchRefresh = remember(viewModel) { { viewModel.refreshTodayWatchOnly() } }
                                 val onTodayWatchUpClick = feedNavigation.onUpClick
                                 val onHomeFeedUpClick = feedNavigation.onUpClick
                                 val onPopularSubCategoryChange = remember(viewModel) {
                                     { subCategory: PopularSubCategory -> viewModel.switchPopularSubCategory(subCategory) }
                                 }

                                 val renderHomeCategoryPage: @Composable (
                                     CategoryContent,
                                     LazyStaggeredGridState,
                                     PopularSubCategory,
                                     () -> Unit
                                 ) -> Unit = { pageCategoryState, contentGridState, selectedPopularSubCategory, onPageLoadMore ->
                                 val pageShowsHeroCarousel = shouldShowHomeHeroCarousel(
                                     enabled = homeSettings.homeHeroCarouselEnabled,
                                     category = category,
                                     itemCount = pageCategoryState.videos.size
                                 )
                                 val pageContentPadding = PaddingValues(
                                     bottom = homeListBottomPadding,
                                     start = homeFeedCardLayout.outerPaddingDp.dp,
                                     end = homeFeedCardLayout.outerPaddingDp.dp,
                                     top = resolveHomeFeedTopPaddingDp(
                                         reservedTopPaddingDp = listTopPadding.value,
                                         showHeroCarousel = pageShowsHeroCarousel
                                     ).dp
                                 )
                                 HomeCategoryPageContent(
                                     category = category,
                                     categoryState = pageCategoryState,
                                     isActive = homeFeedWorkAllowed && pagerState.currentPage == page,
                                     pageOnScreen = pageOnScreen && pagerState.currentPage == page,
                                     visibleDuringNavigation = visibleDuringNavigation && pagerState.currentPage == page &&
                                         homeFeedWorkAllowed,
                                     gridState = contentGridState,
                                     pageScrollInProgress = remember(pagerState) {
                                         derivedStateOf { pagerState.isScrollInProgress }
                                     },
                                     gridColumns = effectiveGridColumns,
                                     contentPadding = pageContentPadding,
                                     dissolvingVideos = dissolvingVideos,
                                     followingMids = followingMids,
                                     showOnlineCount = showOnlineCount,
                                     coverRequestSpec = homeCoverRequestSpec,
                                     onVideoClick = wrappedOnVideoClick,
                                     onUpClick = onHomeFeedUpClick,
                                     onLiveClick = onLiveClickCallback,
                                     onOpenLiveHome = onLiveListClick,
                                     onLoadMore = onPageLoadMore,
                                     onRetryLoadMore = {
                                         viewModel.loadMore(category, selectedPopularSubCategory, retry = true)
                                     },
                                     onRetryRefresh = { viewModel.refresh(category, selectedPopularSubCategory) },
                                     onDismissVideo = { video -> onDismissVideoCallback(video) },
                                     onWatchLater = onWatchLaterCallback,
                                     onDissolveComplete = onDissolveCompleteCallback,
                                     dissolveReflowEnabled = !systemReduceMotion,
                                     longPressCallback = onLongPressCallback, // [Feature] Pass callback
                                     displayMode = displayMode,
                                     // 刷新数据换位时不再同时启动整屏卡片 placement spring。
                                     cardAnimationEnabled = cardAnimationEnabled && !isPageRefreshing,
                                     cardMotionTier = cardMotionTier,
                                     backToTopSquishActive = homeBackToTopSquishActive &&
                                         category == latestHomeScrollCategory,
                                     cardTransitionEnabled = cardTransitionEnabled,
                                     isReturningFromVideoDetail = isReturningFromVideoDetail,
                                     isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail,
                                     smartVisualGuardEnabled = false,
                                     isDataSaverActive = isDataSaverActive,
                                     preferLowQualityCover = homeSettings.lowQualityHomeCoverInDataSaver,
                                     compactStatsOnCover = homeSettings.compactVideoStatsOnCover,
                                     showCoverGlassBadges = homeSettings.showHomeCoverGlassBadges,
                                     // 信息区标签保持轻量；贴封面统计由卡片复用封面标签样式渲染。
                                     showInfoGlassBadges = false,
                                     badgeEffectMode = homeSettings.homeCardBadgeEffectMode,
                                     infoGlassMode = homeSettings.homeCardInfoGlassMode,
                                     wallpaperTintEnabled = homeWallpaperBackdropAppearance.visible,
                                     wallpaperEffectMode = homeSettings.homeWallpaperEffectMode,
                                     showUpBadges = homeSettings.showHomeUpBadges,
                                     showUpAvatars = settledShowHomeUpAvatars,
                                     showPublishTime = homeSettings.showHomePublishTime,
                                     homeDurationStyle = homeSettings.homeDurationStyle,
                                     homeFeedCardStyle = homeFeedCardStyle,
                                     showFullVideoCardContent = homeSettings.showFullVideoCardContent,
                                     homeHeroCarouselEnabled = homeSettings.homeHeroCarouselEnabled,
                                     homeHeroCarouselAutoplayEnabled = homeSettings.homeHeroCarouselAutoplayEnabled,
                                     onHeroCarouselGestureActiveChange = onHeroCarouselGestureActiveChange,
                                     onGetPreviewUrl = { bvid, cid -> viewModel.getPreviewVideoUrl(bvid, cid) },
                                     oldContentAnchorBvid = if (shouldShowRecommendOldContentDivider(
                                             currentCategory = category,
                                             refreshNewItemsKey = refreshNewItemsKey,
                                             revealedRefreshKey = recommendOldContentRevealKey,
                                             anchorBvid = recommendOldContentAnchorBvid,
                                             oldContentStartIndex = recommendOldContentStartIndex,
                                             refreshTipVisible = homeSettings.homeRefreshTipVisible
                                         )
                                     ) {
                                         recommendOldContentAnchorBvid
                                     } else {
                                         null
                                     },
                                     oldContentStartIndex = if (shouldShowRecommendOldContentDivider(
                                             currentCategory = category,
                                             refreshNewItemsKey = refreshNewItemsKey,
                                             revealedRefreshKey = recommendOldContentRevealKey,
                                             anchorBvid = recommendOldContentAnchorBvid,
                                             oldContentStartIndex = recommendOldContentStartIndex,
                                             refreshTipVisible = homeSettings.homeRefreshTipVisible
                                         )
                                     ) {
                                         recommendOldContentStartIndex
                                     } else {
                                         null
                                     },
                                     oldContentLocatorRefreshKey = refreshNewItemsKey,
                                     onOldContentDividerClick = {
                                         coroutineScope.launch {
                                             contentGridState.animateScrollToItem(0)
                                         }
                                         viewModel.refresh(category)
                                     },
                                     overlayPillColors = overlayPillColors,
                                     todayWatchEnabled = category == HomeCategory.RECOMMEND && todayWatchPluginEnabled,
                                     todayWatchMode = todayWatchMode,
                                     todayWatchPlan = if (category == HomeCategory.RECOMMEND) todayWatchPlan else null,
                                     todayWatchLoading = category == HomeCategory.RECOMMEND && todayWatchLoading,
                                     todayWatchError = if (category == HomeCategory.RECOMMEND) todayWatchError else null,
                                     todayWatchCollapsed = category == HomeCategory.RECOMMEND && todayWatchCollapsed,
                                     todayWatchCardConfig = todayWatchCardConfig,
                                     onTodayWatchModeChange = onTodayWatchModeChange,
                                     onTodayWatchCollapsedChange = onTodayWatchCollapsedChange,
                                     onTodayWatchRefresh = onTodayWatchRefresh,
                                     onTodayWatchUpClick = onTodayWatchUpClick,
                                     popularSubCategory = selectedPopularSubCategory,
                                     onPopularSubCategoryChange = onPopularSubCategoryChange,
                                     onWeeklySeriesClick = onWeeklySeriesClick,
                                     onTodayWatchVideoClick = onTodayWatchVideoClick,
                                     firstGridItemModifier = Modifier,
                                     // 首屏骨架作为内容网格的首批 item 铺进去：同一个 LazyLayout、
                                     // 同一种 contentType，数据到达时原地替换 + animateItem 交叉淡入。
                                     initialSkeletonContent = initialSkeletonContent
                                 )
                                 }
                                 if (category == HomeCategory.POPULAR) {
                                     val subCategoryGridState =
                                         popularGridStates[popularSubCategory] ?: rememberHomeStaggeredGridState()
                                     renderHomeCategoryPage(
                                         categoryState,
                                         subCategoryGridState,
                                         popularSubCategory,
                                         onLoadMoreCallback
                                     )
                                 } else {
                                     renderHomeCategoryPage(
                                         categoryState,
                                         pageGridState,
                                         popularSubCategory,
                                         onLoadMoreCallback
                                     )
                                 }
                             }
                             } // Close Box wrapper
                        }
                            }
                            }
                            null -> Unit
                        }
                } // Close HorizontalPager lambda
                        } // Close AppHingeSafeContent
            } // Close Box wrapper
                    } // Close LocalHomeMiuixBackdrop provider
        } // Close Scaffold lambda

        //  ===== Header Overlay (毛玻璃效果) =====
        //  Header 现在在外层 Box 内、hazeSource 外部，可以正确模糊内层内容
        //  [Restored] Header 始终显示，不再随 Loading/Error 状态隐藏
        //  这保证了 Tab 指示器状态的连续性，防止消失或重置
        val isFeedScrollInProgress by remember(activeGridState, pagerState, topTabEntries, subscriptionListState) {
            derivedStateOf {
                if (resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage) == HomeTopTabEntry.Subscriptions) {
                    if (subscriptionArticleOpen) subscriptionArticleScrolling
                    else subscriptionListState.isScrollInProgress
                } else activeGridState?.isScrollInProgress == true
            }
        }
        if (isTopLevelActive) {
            SideEffect {
                globalFeedScrollInProgress.value = isFeedScrollInProgress
            }
        }
        DisposableEffect(isTopLevelActive) {
            if (!isTopLevelActive) {
                globalFeedScrollInProgress.value = false
            }
            onDispose {
                if (isTopLevelActive) {
                    globalFeedScrollInProgress.value = false
                }
            }
        }
        val homeInteractionMotionBudget = resolveHomeInteractionMotionBudget(
            isPagerScrolling = pagerState.isScrollInProgress,
            isProgrammaticPageSwitchInProgress = programmaticPageSwitchInProgress,
            isFeedScrolling = isFeedScrollInProgress
        )
        val isHeaderTransitionRunning by remember(pagerState, headerSettleAnimationJob, isFeedScrollInProgress) {
            derivedStateOf {
                resolveHomeHeaderTransitionRunning(
                    isFeedScrolling = isFeedScrollInProgress,
                    isPagerScrolling = pagerState.isScrollInProgress,
                    isHeaderSettleAnimating = headerSettleAnimationJob != null
                )
            }
        }
        TrackJankStateFlag(
            stateName = "home:header_transition",
            isActive = isHeaderTransitionRunning
        )
        val forceLowBlurBudget = false
        val overlayChromeColors = rememberHomeGlassChromeColors(
            glassEnabled = isLiquidGlassEnabled,
            blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled
        )
        val refreshTipAppearance = remember(isLiquidGlassEnabled, isHeaderBlurEnabled, isBottomBarBlurEnabled) {
            resolveHomeRefreshTipAppearance(
                liquidGlassEnabled = isLiquidGlassEnabled,
                blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled
            )
        }


        // Calculate parameters based on scroll
        val topTabsCollapsedForHeader = if (collapseTabsOnScroll) {
            topTabsAutoCollapsedByScroll
        } else {
            false
        }
        val videoCardClock = LocalVideoCardTransitionClock.current
        val videoCardSettleState = videoCardClock?.settleState
        val homeHeaderChromeVisible = !subscriptionArticleOpen && (
            homeFeedOwnsVideoCardSnapshot ||
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
            exposure = resolveVideoCardTransitionExposure(
                phase = videoCardClock?.phase ?: VideoCardTransitionBackgroundPhase.IDLE,
                predictiveBackInProgress = videoCardSettleState ==
                    VideoCardTransitionSettleState.InteractiveSeek,
                gestureRestoreInProgress = videoCardSettleState ==
                    VideoCardTransitionSettleState.CancelRestore ||
                    videoCardClock?.gestureRestoreInProgress == true,
            ),
            )
        )
        val appearHomeHeaderFromHidden = homeHeaderChromeVisible &&
            (
                videoCardSettleState == VideoCardTransitionSettleState.InteractiveSeek ||
                    videoCardClock?.phase == VideoCardTransitionBackgroundPhase.RETURNING
            )
        val homeHeaderVisibilityState = remember {
            MutableTransitionState(!appearHomeHeaderFromHidden)
        }
        homeHeaderVisibilityState.targetState = homeHeaderChromeVisible
        val headerDepthDensity = LocalDensity.current
        val activeHeaderDepthClock = videoCardClock?.takeIf {
            !homeFeedOwnsVideoCardSnapshot && it.phase != VideoCardTransitionBackgroundPhase.IDLE &&
                (homeHeaderVisibilityState.currentState || homeHeaderVisibilityState.targetState)
        }
        // Keep the RenderEffect layer full-screen even though only the header paints into it.
        // A header-sized layer clamps the blur kernel at the chrome bottom and produces the
        // horizontal seam visible during predictive back.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (activeHeaderDepthClock != null) {
                        Modifier.videoCardTransitionOverlayDepthEffect(
                            progressProvider = { activeHeaderDepthClock.depthProgress() },
                            phaseProvider = { activeHeaderDepthClock.phase },
                            motionTierProvider =
                                videoCardTransitionBackgroundState.motionTierProvider,
                            sourceBoundsProvider =
                                videoCardTransitionBackgroundState.sourceBoundsProvider,
                            scaleReductionProvider = {
                                resolveVideoCardTransitionBackgroundScaleReduction(
                                    resolveVideoCardTransitionBackgroundSource(
                                        videoCardTransitionBackgroundState.sourceRouteProvider(),
                                    ),
                                )
                            },
                            densityProvider = { headerDepthDensity.density },
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            BottomBarMatchedDockVisibility(
                visibleState = homeHeaderVisibilityState,
                edge = BottomBarMatchedDockEdge.TOP,
                enterFadeDurationMillis = 255,
                exitFadeDurationMillis = 160,
            ) {
            HomeHeader(
            headerOffsetProvider = headerOffsetProvider,
            isHeaderCollapseEnabled = collapseSearchOnScroll,
            isTopTabsAutoCollapseEnabled = collapseTabsOnScroll,
            isTopTabsManualCollapseEnabled = false,
            user = user,
            onAvatarClick = {
                when (
                    resolveHomeAvatarAction(
                        isLoggedIn = user.isLogin,
                        isHomeDrawerEnabled = isHomeDrawerEnabled
                    )
                ) {
                    HomeAvatarAction.OPEN_DRAWER -> openHomeDrawer()
                    HomeAvatarAction.OPEN_PROFILE -> onProfileClick()
                    HomeAvatarAction.OPEN_LOGIN -> onAvatarClick()
                }
            },
            onSettingsClick = onSettingsClick,
            onInboxClick = onInboxClick,
            topRightUnreadCount = messageUnreadDisplay,
            onSearchClick = {
                val searchEntry = resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage)
                if (searchEntry is HomeTopTabEntry.Category && shouldEmbedLivePageInHomeTopTab(searchEntry.category)) {
                    onLiveSearchClick()
                } else onSearchClick()
            },
            topCategories = localizedTopTabLabels,
            topCategoryKeys = topTabKeys,
            categoryIndex = displayedTabIndex,
            onCategorySelected = { index -> selectHomeTopTab(index) },
            onCategoryReselected = {
                if (!pagerState.isScrollInProgress) {
                    val identity = currentHomeScrollRequestIdentity()
                    coroutineScope.launch { performHomeScrollRequest(identity, HomeScrollRequest.SCROLL_TO_TOP) }
                }
            },
            onPartitionClick = onPartitionClick,
            // isScrollingUp = isHeaderVisible, // [Removed] logic moved to offset
            hazeState = if (topChromeMaterialMode != com.android.purebilibili.feature.home.components.TopTabMaterialMode.PLAIN && !appThemeConfig.progressiveTopBlurEnabled) {
                hazeState
            } else {
                null
            },
            onStatusBarDoubleTap = {
                val identity = currentHomeScrollRequestIdentity()
                coroutineScope.launch {
                    performHomeScrollRequest(
                        identity,
                        HomeScrollRequest.SCROLL_TO_TOP,
                    )
                }
            },
            isRefreshing = isRefreshing,
            pullProgress = 0f, // [Fix] Outer header doesn't track inner pull state
            pagerState = pagerState,
            miuixBackdrop = readyHomeMiuixBackdrop,
            homeSettings = effectiveHomeSettings,
            topTabsVisible = resolveHomeTopTabsVisible(
                isDelayedForCardSettle = delayTopTabsUntilCardSettled,
                isForwardNavigatingToDetail = hideTopTabsForForwardDetailNav,
                isReturningFromDetail = isReturningFromVideoDetail,
                topTabsCollapsed = topTabsCollapsedForHeader,
                hideTopTabs = effectiveHomeSettings.hideTopTabs
            ),
            topTabsCollapsed = topTabsCollapsedForHeader,
            onTopTabsCollapsedChange = {},
            motionTier = deviceUiProfile.motionTier,
            isScrolling = isFeedScrollInProgress,
            isTransitionRunning = isHeaderTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            interactionBudget = homeInteractionMotionBudget,
            uiSkinDecoration = homeUiSkinDecoration
            )
            }
        }

        AnimatedVisibility(
            visible = refreshDeltaTipText != null,
            enter = fadeIn(animationSpec = tween(overlayMotionSpec.refreshTipEnterFadeDurationMillis)) + slideInVertically(
                animationSpec = tween(overlayMotionSpec.refreshTipSlideDurationMillis),
                initialOffsetY = { -it / 2 }
            ),
            exit = fadeOut(animationSpec = tween(overlayMotionSpec.refreshTipExitFadeDurationMillis)) + slideOutVertically(
                animationSpec = tween(overlayMotionSpec.refreshTipSlideDurationMillis),
                targetOffsetY = { -it / 2 }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = listTopPadding + AppSpacingTokens.Small)
                .zIndex(90f)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Pill),
                    color = if (refreshTipAppearance.surfaceStyle == HomeRefreshTipSurfaceStyle.PLAIN) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        overlayChromeColors.containerColor
                    },
                    border = if (refreshTipAppearance.borderWidthDp > 0f) {
                        BorderStroke(
                            refreshTipAppearance.borderWidthDp.dp,
                            overlayChromeColors.borderColor
                        )
                    } else {
                        null
                    },
                    tonalElevation = refreshTipAppearance.tonalElevationDp.dp,
                    shadowElevation = refreshTipAppearance.shadowElevationDp.dp
                ) {
                    AppText(
                        text = refreshDeltaTipText.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppContentColors.primaryText(),
                        modifier = Modifier.padding(horizontal = AppSpacingTokens.Medium + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Small)
                    )
                }
            }
        }

        // [新增] 双指缩放切换网格列数 HUD 胶囊 (自适应 MD3 / MIUIX)
        GridPinchColumnHudPill(
            visible = isPinchPillVisible,
            columns = effectiveGridColumns,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = listTopPadding + AppSpacingTokens.Small)
        )

        //  [新增] 刷新撤销悬浮按钮（右下角，5秒后自动消失）
        if (homeSettings.homeRefreshUndoVisible) {
        //  与「定位上次刷新」胶囊共用同一底部锚点：跟随听视频横条上浮，且在定位胶囊
        //  可见时再抬一个胶囊位（胶囊高约 36dp + 8dp 间距），避免两者互相遮挡。
        val undoVisible = undoAvailable && currentCategory == HomeCategory.RECOMMEND
        //  手动关闭撤销胶囊；下次撤销可用时自动复位
        var undoDismissed by remember(refreshNewItemsKey) { mutableStateOf(false) }
        var undoDissolving by remember(refreshNewItemsKey) { mutableStateOf(false) }
        val oldContentLocatorVisible = shouldShowRecommendOldContentDivider(
            currentCategory = currentCategory,
            refreshNewItemsKey = refreshNewItemsKey,
            revealedRefreshKey = recommendOldContentRevealKey,
            anchorBvid = recommendOldContentAnchorBvid,
            oldContentStartIndex = recommendOldContentStartIndex,
            refreshTipVisible = homeSettings.homeRefreshTipVisible,
        )
        val nowPlayingBarOverlayVisible by com.android.purebilibili.feature.audio.player
            .AudioNowPlayingSession.barOverlayVisible
            .collectAsStateWithLifecycle()
        val undoPillBottomPadding = homeListBottomPadding + AppSpacingTokens.Medium +
            120.dp +
            (if (nowPlayingBarOverlayVisible) 76.dp else 0.dp) +
            (if (oldContentLocatorVisible) 52.dp else 0.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(95f),
            contentAlignment = Alignment.BottomEnd
        ) {
            AnimatedVisibility(
                visible = (undoVisible || undoDissolving) && !undoDismissed,
                enter = fadeIn(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideInVertically(
                    animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                    initialOffsetY = { it }
                ),
                exit = if (undoDismissed) androidx.compose.animation.ExitTransition.None else
                    fadeOut(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideOutVertically(
                        animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                        targetOffsetY = { it }
                    ),
                modifier = Modifier.padding(
                    end = AppSpacingTokens.Large,
                    bottom = undoPillBottomPadding,
                )
            ) {
                DissolvableVideoCard(
                    isDissolving = undoDissolving,
                    onDissolveComplete = {
                        undoDismissed = true
                        undoDissolving = false
                    },
                    cardId = "undo-refresh-$refreshNewItemsKey",
                    preset = com.android.purebilibili.core.ui.animation.DissolveAnimationPreset.TELEGRAM_FAST,
                    collapseAfterDissolve = false,
                    publishGlobalDissolveState = false,
                    keepInvisibleAfterDissolve = true,
                ) {
                    com.android.purebilibili.feature.home.components.HomeOverlayPillButton(
                        overlayPillColors = overlayPillColors,
                        onClick = { if (!undoDissolving) viewModel.undoRefresh() },
                        modifier = Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                            }
                        },
                    ) {
                        AppText(
                            text = "⟲",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                        AppText(
                            text = "撤销刷新",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .appActionClickable(enabled = !undoDissolving) { undoDissolving = true },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        } // Refresh undo is opt-in; disabled path does not compose the overlay.

        com.android.purebilibili.feature.video.share.VideoShareSheetHost(
            payload = pendingVideoShare,
            onDismiss = { pendingVideoShare = null },
        )

        // [Feature] Video Preview Overlay with Animation
        androidx.compose.animation.AnimatedVisibility(
            visible = targetVideoItemState.value != null,
            enter = fadeIn(tween(overlayMotionSpec.previewOverlayFadeDurationMillis)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(
                    overlayMotionSpec.previewOverlayScaleDurationMillis,
                    easing = AppMotionEasing.M1Standard
                )
            ),
            exit = fadeOut(tween(overlayMotionSpec.previewOverlayFadeDurationMillis)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(
                    overlayMotionSpec.previewOverlayScaleDurationMillis,
                    easing = AppMotionEasing.M1Standard
                )
            ),
            modifier = Modifier.fillMaxSize().zIndex(100f) // Ensure on top
        ) {
            val item = targetVideoItemState.value
            if (item != null) {
                com.android.purebilibili.feature.home.components.VideoPreviewDialog(
                    video = item,
                    onDismiss = { targetVideoItemState.value = null },
                    onPlay = {
                     // 1. Log click
                     wrappedOnVideoClick(
                         HomeVideoClickRequest(
                             bvid = item.bvid,
                             cid = item.cid,
                             coverUrl = item.pic,
                             isVerticalVideo = item.resolveIsVerticalForNavigation(),
                             source = HomeVideoClickSource.PREVIEW,
                             sourceRoute = resolveHomeCategoryVideoSourceRoute(currentCategory)
                         )
                     )
                     // 2. Clear preview state
                     targetVideoItemState.value = null
                },
                onWatchLater = {
                    viewModel.addToWatchLater(item.bvid, item.aid)
                    targetVideoItemState.value = null
                },
                onSaveCover = {
                    val coverUrl = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(item.pic)
                    if (coverUrl.isBlank()) {
                        android.widget.Toast.makeText(context, "无法获取封面地址", android.widget.Toast.LENGTH_SHORT).show()
                        targetVideoItemState.value = null
                    } else {
                        coroutineScope.launch {
                            val success = com.android.purebilibili.feature.download.DownloadManager
                                .saveImageToGallery(context, coverUrl, item.title)
                            val message = if (success) "封面已保存到相册" else "保存失败"
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        targetVideoItemState.value = null
                    }
                },
                onShare = {
                    pendingVideoShare = com.android.purebilibili.feature.video.share.buildVideoSharePayload(
                        title = item.title,
                        bvid = item.bvid,
                        coverUrl = item.pic,
                    )
                    targetVideoItemState.value = null
                },
                onNotInterested = { onDismissVideoCallback(item) },
                onBlockCreator = {
                    viewModel.blockCreator(item)
                    targetVideoItemState.value = null
                },
                onGetPreviewUrl = { bvid, cid ->
                    viewModel.getPreviewVideoUrl(bvid, cid)
                },
                hazeState = hazeState,
                miuixBackdrop = readyHomeMiuixBackdrop,
                backgroundBlurEnabled = homeChromeBlurEnabled,
                modifier = Modifier.fillMaxSize(),
            )
            }
        }
        } // Unified header/feed depth snapshot
    }

    val scaffoldContent: @Composable () -> Unit = {
        if (isHomeDrawerEnabled) {
            val shouldReserveDrawerBottomOverlay = bottomBarVisibleBeforeDrawer == true || bottomBarVisible
            val drawerBottomOverlayHeight = if (shouldReserveDrawerBottomOverlay) {
                homeListBottomPadding
            } else {
                AppSpacingTokens.None
            }
            AppModalNavigationDrawer(
                drawerState = drawerState,
                // 关闭时只允许点击头像打开，避免首页上下滚动与抽屉拖拽竞争。
                gesturesEnabled = isDrawerStateOpenOrOpening,
                scrimColor = MaterialTheme.colorScheme.scrim.copy(
                    alpha = resolveHomeDrawerScrimAlpha(isHeaderBlurEnabled),
                ),
                drawerContent = {
                    MineSideDrawer(
                        drawerState = drawerState,
                        user = user,
                        onLogout = resolveHomeDrawerLogoutAction(
                            onLogout = onLogout,
                            onProfileClick = onProfileClick
                        ),
                        onHistoryClick = onHistoryClick,
                        onFavoriteClick = onFavoriteClick,
                        onLikedVideosClick = onLikedVideosClick,
                        onBangumiClick = { onBangumiClick(1) },
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onInboxClick = onInboxClick,
                        onSettingsClick = onSettingsClick,
                        onProfileClick = onProfileClick,
                        onAccountSwitchClick = onAccountSwitchClick,
                        hazeState = hazeState,
                        isBlurEnabled = isHeaderBlurEnabled,
                        bottomOverlayHeight = drawerBottomOverlayHeight,
                        miuixBackdrop = readyHomeMiuixBackdrop,
                        liquidGlassEnabled = isLiquidGlassEnabled,
                        liquidGlassTuning = homeLiquidGlassTuning,
                        skinBackgroundImagePath = homeUiSkinDecoration?.sideBackgroundImagePath,
                        skinBottomTrimImagePath = homeUiSkinDecoration?.sideBottomTrimImagePath,
                        skinBackgroundTint = homeUiSkinDecoration?.sideBackgroundTint,
                    )
                }
            ) {
                scaffoldLayout()
            }
        } else {
            scaffoldLayout()
        }
    }


    //  使用生命周期事件：
    // ON_START: 非视频返回底栏立即恢复
    // ON_STOP: 清理定时器
    // 视频返回的顶栏/底栏恢复统一由导航返回态 LaunchedEffect 处理，避免依赖不稳定的页面生命周期。
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentBottomBarVisible by rememberUpdatedState(bottomBarVisible)
    DisposableEffect(lifecycleOwner, useSideNavigation) {
        if (useSideNavigation) {
            return@DisposableEffect onDispose { }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    //  底栏由动画完成 LaunchedEffect 统一恢复，此处不再独立计时
                    if (!currentBottomBarVisible && !isVideoNavigating) {
                        //  从设置等非视频页面返回时，立即显示底栏（无延迟）
                        setBottomBarVisible(true)
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    topTabsRevealJob?.cancel()
                    // setBottomBarVisible(false) // REMOVED
                }
                else -> { /* 其他事件不处理 */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            topTabsRevealJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    //  [修复] 使用 rememberSaveable 记住本次会话中是否已处理过弹窗（防止导航后重新显示）
    var consentDialogHandled by rememberSaveable { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }

    // 使用须知确认后再展示崩溃统计同意（避免与门禁叠层）
    val welcomePrefs = remember { context.getSharedPreferences("app_welcome", Context.MODE_PRIVATE) }
    val userAgreementAcked = welcomePrefs.getBoolean(
        com.android.purebilibili.feature.onboarding.USER_AGREEMENT_ACK_KEY,
        false
    ) || welcomePrefs.getBoolean("first_launch_shown", false)

    // 检查是否需要显示弹窗（使用须知已确认 且 同意弹窗尚未显示过 且 本次会话未处理过）
    LaunchedEffect(crashTrackingConsentShown) {
        if (userAgreementAcked && !crashTrackingConsentShown && !consentDialogHandled) {
            showConsentDialog = true
        }
    }

    // 显示弹窗
    if (showConsentDialog) {
        com.android.purebilibili.feature.home.components.CrashTrackingConsentDialog(
            onDismiss = {
                showConsentDialog = false
                consentDialogHandled = true  // 标记为已处理
            }
        )
    }

    //  滚动方向（简化版 - 不再需要复杂检测，因为标签页只在顶部显示）
    val isScrollingUp = true  // 保留参数兼容性

    //  [性能优化] 图片预加载 - 提前加载即将显示的视频封面
    // 📉 [省流量] 省流量模式下禁用预加载
    val foregroundSettling by com.android.purebilibili.core.lifecycle.ForegroundSettleBudget
        .isSettling
        .collectAsStateWithLifecycle()
    LaunchedEffect(
        currentCategory,
        popularSubCategory,
        isDataSaverActive,
        preloadAheadCount,
        homeCoverRequestSpec,
        homeCoverPreloadAllowed,
        foregroundSettling,
        lifecycleOwner,
    ) {
        // 📉 省流量模式下跳过预加载
        if (isDataSaverActive) return@LaunchedEffect
        if (preloadAheadCount <= 0) return@LaunchedEffect
        // Restore anchors before preloading. Focus, foreground, page and image permission
        // changes cancel both the settling delay and any in-flight batch through this effect key.
        if (!homeCoverPreloadAllowed) return@LaunchedEffect
        // 回前台的安定窗口内暂缓封面预取：这批解码会和用户此刻的首次切页抢主线程。
        // 窗口结束后本效应会以 foregroundSettling = false 重新触发。
        if (foregroundSettling) return@LaunchedEffect

        val currentGridState = if (currentCategory == HomeCategory.POPULAR) {
            popularGridStates[popularSubCategory]
        } else {
            gridStates[currentCategory]
        } ?: return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow {
                // 滚动时不订阅 layoutInfo，也不创建可见项列表；停稳后重新读取最新布局。
                if (currentGridState.isScrollInProgress) null
                else currentGridState.layoutInfo.visibleItemsInfo.map { it.key }
            }
                .distinctUntilChanged()
                .collectLatest { visibleKeys ->
                    // Cancel the previous batch immediately when scrolling starts. Debouncing
                    // upstream would keep that batch alive until the next settled emission.
                    if (visibleKeys == null) return@collectLatest
                    // Visible cover work goes first, then card color/online work. Give
                    // those a separate window before starting off-screen decodes.
                    kotlinx.coroutines.delay(HOME_COVER_PRELOAD_SETTLE_MS)
                    val videos = viewModel.getPreloadVideosSnapshot(
                        category = currentCategory,
                        popularSubCategory = popularSubCategory
                    )
                    val visibleKeySet = visibleKeys.toSet()
                    val lastVisibleIndex = resolveHomeCategoryVideoGridKeys(videos)
                        .indexOfLast { it in visibleKeySet }
                    val preloadRange = resolveHomeCoverPreloadRange(
                        isDataSaverActive = isDataSaverActive,
                        isScrollInProgress = false,
                        lastVisibleIndex = lastVisibleIndex,
                        totalItemCount = videos.size,
                        preloadAheadCount = preloadAheadCount
                    ) ?: return@collectLatest
                    // Adjacent entries may share a cover; decode each cache identity only once.
                    val sources = preloadRange
                        .mapNotNull { index -> videos.getOrNull(index) }
                        .map { video ->
                            resolveHomeCoverImageSource(video, false, homeCoverRequestSpec)
                        }
                        .filter { it.url.isNotBlank() }
                        .distinctBy { it.cacheKey }
                    if (sources.isEmpty()) return@collectLatest

                    kotlinx.coroutines.coroutineScope {
                        for (source in sources) launch {
                            val request = coil3.request.ImageRequest.Builder(context)
                                .data(source.url)
                                .size(homeCoverRequestSpec.widthPx, homeCoverRequestSpec.heightPx)
                                .scale(coil3.size.Scale.FILL)
                                .memoryCacheKey(source.cacheKey)
                                .diskCacheKey(source.cacheKey)
                                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                                .build()
                            // execute participates in this effect's cancellation; enqueue would
                            // leave the work running after collectLatest/ON_STOP cancelled it.
                            context.imageLoader.execute(request)
                        }
                    }
                }
        }
    }


    //  PullToRefreshBox 自动处理下拉刷新逻辑

    //  [已移除] 特殊分类（ANIME, MOVIE等）不再在首页切换，直接导航到独立页面

    // 嵌入直播页由 LiveListViewModel 管理；返回只看实际可见页，不读取旧直播关注状态。
    val liveTabBackTargetPage = resolveHomeLiveTabBackTargetPage(
        entries = topTabEntries,
        currentPage = pagerState.currentPage,
        isTopLevelActive = isTopLevelActive,
    )
    androidx.activity.compose.BackHandler(enabled = liveTabBackTargetPage != null) {
        liveTabBackTargetPage?.let { selectHomeTopTab(it) }
    }

// [Removed] Animation logic moved inside HorizontalPager where the active state exists

    // 指示器位置逻辑也移入 graphicsLayer
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        scaffoldContent()
        val video = pendingNotInterestedVideo
        if (video != null) {
            HomeNotInterestedReasonSheet(
                video = video,
                reasons = resolveHomeNotInterestedReasons(video),
                onReasonSelected = { reason ->
                    confirmNotInterested(video, reason)
                },
                onDismissRequest = {
                    pendingNotInterestedVideo = null
                }
            )
        }
    }
}

internal data class HomeOverlayMotionSpec(
    val refreshTipEnterFadeDurationMillis: Int,
    val refreshTipExitFadeDurationMillis: Int,
    val refreshTipSlideDurationMillis: Int,
    val undoFabFadeDurationMillis: Int,
    val undoFabSlideDurationMillis: Int,
    val previewOverlayFadeDurationMillis: Int,
    val previewOverlayScaleDurationMillis: Int,
    val sideNavEnterSlideDurationMillis: Int,
    val sideNavExitSlideDurationMillis: Int,
    val sideNavFadeDurationMillis: Int
)

internal fun resolveHomeOverlayMotionSpec(): HomeOverlayMotionSpec {
    return HomeOverlayMotionSpec(
        refreshTipEnterFadeDurationMillis = 180,
        refreshTipExitFadeDurationMillis = 220,
        refreshTipSlideDurationMillis = 220,
        undoFabFadeDurationMillis = 200,
        undoFabSlideDurationMillis = 250,
        previewOverlayFadeDurationMillis = 200,
        previewOverlayScaleDurationMillis = 200,
        sideNavEnterSlideDurationMillis = 300,
        sideNavExitSlideDurationMillis = 250,
        sideNavFadeDurationMillis = 200
    )
}

private const val RETURN_ANIMATION_SUPPRESSION_BUFFER_MS = 40L
private const val RETURN_ANIMATION_SUPPRESSION_TABLET_EXTRA_MS = 40L

internal fun resolveReturnAnimationSuppressionDurationMs(
    isTabletLayout: Boolean,
    cardAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    sharedTransitionDurationMillis: Int =
        com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_STANDARD_DURATION_MILLIS,
): Long {
    if (cardTransitionEnabled) {
        // 固定时长 tween 返回：保护窗 = 主时长 + 短 buffer，避免 clearReturning / 列表
        // 在 overlay 卸层瞬间抢跑重建封面请求 → 落位闪。
        return sharedTransitionDurationMillis.coerceAtLeast(0).toLong() +
            RETURN_ANIMATION_SUPPRESSION_BUFFER_MS +
            com.android.purebilibili.core.ui.transition.resolveVideoCardReturnSpringSettleBufferMs() +
            if (isTabletLayout) RETURN_ANIMATION_SUPPRESSION_TABLET_EXTRA_MS else 0L
    }
    if (!cardAnimationEnabled) return 220L
    return if (isTabletLayout) 220L else 240L
}

internal fun resolveHomeContentInteractionRestoreDelayMs(
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean
): Long {
    // 视觉返场保护仍由 suppression / 底栏恢复窗口负责；
    // 首页列表手势应在页面重新可见时立即恢复，避免第一下滑动被导航态吞掉。
    return 0L
}
