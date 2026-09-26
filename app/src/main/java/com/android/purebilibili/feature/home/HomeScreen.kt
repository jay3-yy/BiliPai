// 文件路径: feature/home/HomeScreen.kt
package com.android.purebilibili.feature.home
import com.android.purebilibili.core.ui.components.AppText

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.*  // 🌊 瀑布流布局
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material3.DrawerValue
import com.android.purebilibili.core.ui.components.AppModalNavigationDrawer
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.ui.common.HOME_PAGER_HORIZONTAL_LOCK_SLOP_MULTIPLIER
import com.android.purebilibili.navigation.animatePagerSelection
import androidx.compose.material3.rememberDrawerState
import com.android.purebilibili.feature.home.components.MineSideDrawer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppPullRefreshLoadingIndicator
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppPullRefreshIndicatorStyle
import com.android.purebilibili.core.ui.rememberAppPullRefreshProfile
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy

import com.android.purebilibili.feature.settings.GITHUB_URL
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.core.store.SettingsManager //  引入 SettingsManager
import com.android.purebilibili.core.store.AppNavigationSettings
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
import com.android.purebilibili.feature.home.policy.quantizeHomeHeaderOffset
import com.android.purebilibili.feature.home.policy.canRevealHomeHeaderForList
import com.android.purebilibili.feature.home.policy.reduceHomePreScroll
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderListIndex
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderTransitionRunning
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderSettleTransition
import com.android.purebilibili.feature.home.policy.resolveHomeEmbeddedPageTopPaddingPx
import com.android.purebilibili.feature.home.policy.shouldApplyHomeFeedScrollAnchor
import com.android.purebilibili.feature.home.policy.shouldHandleHomeVerticalPreScroll
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
import com.android.purebilibili.feature.home.components.cards.LiveRoomCard
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
import com.android.purebilibili.core.ui.animation.jiggleOnDissolve      // 📳 iOS 风格抖动效果
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.motion.pullRefreshReleaseSpring
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackJankStateValue
import com.android.purebilibili.core.util.animateScrollToTop
import coil3.imageLoader
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
    partitionVideoSourceRoute: String = "partition",
    onPartitionVideoClick: (VideoItem) -> Unit = { video ->
        onVideoClick(
            HomeVideoClickRequest(
                bvid = video.bvid,
                cid = video.cid,
                coverUrl = video.pic,
                isVerticalVideo = video.isVertical,
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
    onLiveAreaClick: () -> Unit = {},
    onLiveFollowingClick: () -> Unit = {},
    onLiveAreaDetailClick: (Int, Int, String) -> Unit = { _, _, _ -> },
    onBangumiSeasonClick: (Long) -> Unit = {},
    onBangumiEpisodeClick: (Long, Long) -> Unit = { seasonId, _ -> onBangumiSeasonClick(seasonId) },
    onWatchLaterClick: () -> Unit = {},  // 稍后再看页面
    onDownloadClick: () -> Unit = {},  // 离线缓存页面
    onInboxClick: () -> Unit = {},  // 私信页面
    onStoryClick: () -> Unit = {},  //  [新增] 竖屏短视频
    onPluginsClick: () -> Unit = {},
    onSpaceClick: (Long) -> Unit = {},
    globalHazeState: dev.chrisbanes.haze.HazeState? = null,  //  [新增] 全局底栏模糊状态
    isTopLevelActive: Boolean = true,
    isReturningFromVideoDetail: Boolean = false,
    isQuickReturningFromVideoDetail: Boolean = false,
    onVideoDetailReturnAnimationConsumed: () -> Unit = {}
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentCategory by viewModel.currentCategory.collectAsStateWithLifecycle()
    val displayedTabIndexFromState by viewModel.displayedTabIndex.collectAsStateWithLifecycle()
    val popularSubCategory by viewModel.popularSubCategory.collectAsStateWithLifecycle()
    val liveSubCategory by viewModel.liveSubCategory.collectAsStateWithLifecycle()
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
    val uiSkinState by rememberUiSkinState(context)
    val homeUiSkinDecoration = rememberHomeUiSkinDecoration(uiSkinState)
    val overlayMotionSpec = remember { resolveHomeOverlayMotionSpec() }
    //  [Refactor] Use a map of grid states for each category to support HorizontalPager
    // [Refactor] Use a map of grid states for each category to support HorizontalPager
    val gridStates = remember { mutableMapOf<HomeCategory, LazyStaggeredGridState>() }
    HomeCategory.entries.forEach { category ->
        gridStates[category] = rememberSaveable(
            category.name,
            saver = LazyStaggeredGridState.Saver
        ) {
            LazyStaggeredGridState()
        }
    }
    val popularGridStates = remember { mutableMapOf<PopularSubCategory, LazyStaggeredGridState>() }
    PopularSubCategory.entries.forEach { subCategory ->
        popularGridStates[subCategory] = rememberSaveable(
            "popular_${subCategory.name}",
            saver = LazyStaggeredGridState.Saver
        ) {
            LazyStaggeredGridState()
        }
    }
    var liveScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var bangumiScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var partitionScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var subscriptionScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var subscriptionArticleOpen by remember { mutableStateOf(false) }
    val subscriptionListState = rememberLazyStaggeredGridState()
    // [Feature] Video Preview State (Global Scope)
    val targetVideoItemState = remember { mutableStateOf<VideoItem?>(null) }
    var pendingNotInterestedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var pendingVideoShare by remember {
        mutableStateOf<com.android.purebilibili.feature.video.share.VideoSharePayload?>(null)
    }
    val coroutineScope = rememberCoroutineScope() // 用于双击回顶动画
    val headerSettleMotionSpec = AppMotionTokens.emphasizedSpec<Float>()
    val globalScrollOffset = LocalHomeScrollOffset.current
    val globalFeedScrollInProgress = LocalHomeFeedScrollInProgress.current
    // [Header] 首页重选/双击回顶时需要强制恢复顶部，避免自动收缩后残留空白区域。
    // saveable：进 UP 空间等二级页后返回时保留折叠态，避免顶栏重张开带动列表“自动下滑”感。
    var headerOffsetHeightPx by rememberSaveable { mutableFloatStateOf(0f) }
    var topTabsAutoCollapsedByScroll by rememberSaveable { mutableStateOf(false) }
    var headerSettleAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var homeHeaderRevealLock by remember { mutableStateOf(false) }
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
        headerOffsetHeightPx = value
    }

    fun revealHomeHeaderNow() {
        topTabsAutoCollapsedByScroll = false
        setHeaderOffsetImmediate(0f)
        globalScrollOffset.floatValue = 0f
    }

    fun animateHeaderOffsetTo(targetValue: Float) {
        val transition = resolveHomeHeaderSettleTransition(
            currentHeaderOffsetPx = headerOffsetHeightPx,
            targetHeaderOffsetPx = targetValue
        )
        if (!transition.shouldAnimate) {
            setHeaderOffsetImmediate(transition.targetOffsetPx)
            return
        }
        headerSettleAnimationJob?.cancel()
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
                }
                // Keep the tab visibility state tied to the settled header position.
                if (targetValue >= -0.5f) {
                    topTabsAutoCollapsedByScroll = false
                }
            }
        }
    }

    suspend fun withHomeScrollToTopLock(block: suspend () -> Unit) {
        homeHeaderRevealLock = true
        headerSettleAnimationJob?.cancel()
        topTabsAutoCollapsedByScroll = false
        globalScrollOffset.floatValue = 0f
        val headerAnimJob = coroutineScope.launch {
            if (headerOffsetHeightPx < -0.5f) {
                animate(
                    initialValue = headerOffsetHeightPx,
                    targetValue = 0f,
                    animationSpec = headerSettleMotionSpec
                ) { value, _ ->
                    headerOffsetHeightPx = value
                }
            } else {
                headerOffsetHeightPx = 0f
            }
        }
        headerSettleAnimationJob = headerAnimJob
        try {
            block()
        } finally {
            headerAnimJob.join()
            revealHomeHeaderNow()
            homeHeaderRevealLock = false
        }
    }

    // [新增] 监听全局回顶事件
    val scrollChannel = LocalHomeScrollChannel.current
    val latestHomeScrollCategory by rememberUpdatedState(currentCategory)
    val latestHomeScrollPopularSubCategory by rememberUpdatedState(popularSubCategory)

    val homeTopTabSettings by SettingsManager.getHomeTopTabSettings(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeTopTabSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    // 顶部标签顺序和可见项交给设置页控制；默认仍是六项。
    // [Refactor] Hoist PagerState to be available for both Content and Header
    // 确保 pagerState 在所有作用域均可见，以便传给 HomeHeader
    val subscriptionRevision by com.android.purebilibili.core.plugin.feed.SubscriptionFeedStore.revision
        .collectAsStateWithLifecycle()
    val installedPlugins by com.android.purebilibili.core.plugin.PluginManager.pluginsFlow
        .collectAsStateWithLifecycle()
    val isSubscriptionPluginPersistedEnabled by com.android.purebilibili.core.plugin.PluginStore
        .isEnabledFlow(context, com.android.purebilibili.feature.plugin.SubscriptionFeedPlugin.PLUGIN_ID)
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
    val heroCarouselPointerActive = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val onHeroCarouselGestureActiveChange = remember(heroCarouselPointerActive) {
        { active: Boolean -> heroCarouselPointerActive.set(active) }
    }
    val shouldYieldHomePagerToHeroCarousel = remember(heroCarouselPointerActive) {
        { shouldYieldHomeTopPagerToHeroCarousel(heroCarouselPointerActive.get()) }
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
    val latestHomePagerPage by rememberUpdatedState(pagerState.currentPage)
    val latestHomeTopTabEntries by rememberUpdatedState(topTabEntries)
    LaunchedEffect(scrollChannel) {
        val channel = scrollChannel ?: return@LaunchedEffect
        for (initialRequest in channel) {
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
            withHomeScrollToTopLock {
                val entry = resolveHomeTopTabEntryOrNull(
                    latestHomeTopTabEntries,
                    latestHomePagerPage
                )
                when (resolveHomeTopTabScrollTarget(entry)) {
                    HomeTopTabScrollTarget.LIVE -> liveScrollToTopRequestId++
                    HomeTopTabScrollTarget.BANGUMI -> bangumiScrollToTopRequestId++
                    HomeTopTabScrollTarget.PARTITION -> partitionScrollToTopRequestId++
                    HomeTopTabScrollTarget.SUBSCRIPTION -> subscriptionScrollToTopRequestId++
                    HomeTopTabScrollTarget.FEED -> {
                        val activeCategory = latestHomeScrollCategory
                        val gridState = if (activeCategory == HomeCategory.POPULAR) {
                            popularGridStates[latestHomeScrollPopularSubCategory]
                        } else {
                            gridStates[activeCategory]
                        }
                        val isAtTop = gridState == null ||
                            (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 50)

                        if (!isAtTop) {
                            val listState = requireNotNull(gridState)
                            listState.animateScrollToTop(
                                fast = request != HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH,
                            )
                        }
                        val shouldRefresh = request == HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH ||
                            (request == HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH && isAtTop)
                        if (shouldRefresh) {
                            viewModel.refresh()
                        }
                    }
                }
            }
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
        if (
            shouldApplyHomeFeedScrollAnchor(
                currentIndex = gridState.firstVisibleItemIndex,
                currentOffset = gridState.firstVisibleItemScrollOffset,
                currentHeaderOffsetPx = headerOffsetHeightPx,
                anchor = restoreAnchor
            )
        ) {
            gridState.scrollToItem(
                restoreAnchor.firstVisibleItemIndex,
                restoreAnchor.firstVisibleItemScrollOffset
            )
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
    LaunchedEffect(currentCategory, topTabEntries, isTopLevelActive) {
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
                animatePagerSelection(pagerState, targetPage)
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

    //  [性能优化] 合并首页设置为单一 Flow，减少 6 个 collectAsState → 1 个
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val videoCardTransitionBackgroundState = LocalVideoCardTransitionBackgroundState.current
    val videoCardReturnGestureInProgress =
        videoCardTransitionBackgroundState.isReturnGestureInProgressProvider()
    var settledShowHomeUpAvatars by rememberSaveable {
        mutableStateOf(homeSettings.showHomeUpAvatars)
    }
    LaunchedEffect(
        homeSettings.showHomeUpAvatars,
        videoCardReturnGestureInProgress,
        isReturningFromVideoDetail,
    ) {
        // 首页作为预测返回的底层页重新进入活跃状态时，DataStore 可能正好补发新值。
        // 在手势/整卡落位期间改变作者行高度会让 sharedBounds 的目标边界跳动；先沿用
        // 首页上次稳定展示的值，落位完成后再应用，同时关闭头像时仍不保留横向头像槽。
        if (!videoCardReturnGestureInProgress && !isReturningFromVideoDetail) {
            settledShowHomeUpAvatars = homeSettings.showHomeUpAvatars
        }
    }
    val dissolvingVideos by viewModel.dissolvingVideos.collectAsStateWithLifecycle()
    val followingMids by viewModel.followingMids.collectAsStateWithLifecycle()
    val showOnlineCount by SettingsManager
        .getShowOnlineCount(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeFeedCardStyle.BILIPAI,
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

    LaunchedEffect(refreshNewItemsKey, isRefreshing, currentCategory) {
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
        delay(2200)
        refreshDeltaTipText = null
        viewModel.markRefreshNewItemsHandled(refreshKey)
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
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = { 
                AppText(
                    "关闭后下拉刷新将不再显示趣味消息。\n\n你可以在「设置」中随时重新开启。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                ) { AppText("关闭彩蛋", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                AppTextButton(
                    onClick = { showEasterEggDialog = false }
                ) { AppText("保留彩蛋", color = MaterialTheme.colorScheme.primary) }
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
    val baseIsDataSaverActive = remember(context) {
        com.android.purebilibili.core.store.SettingsManager.isDataSaverActive(context)
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
    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val chromeCategoryStateFlow = remember(viewModel, currentCategory, popularSubCategory) {
        if (currentCategory == HomeCategory.POPULAR) {
            viewModel.getPopularCategoryState(popularSubCategory)
        } else {
            viewModel.getCategoryState(currentCategory)
        }
    }
    val chromeCategoryState by chromeCategoryStateFlow.collectAsStateWithLifecycle()
    val chromeContentReady = !(chromeCategoryState.isLoading &&
        chromeCategoryState.videos.isEmpty() && chromeCategoryState.liveRooms.isEmpty())
    val shouldCaptureHomeChromeBackdrop = isLiquidGlassEnabled ||
        isHeaderBlurEnabled || isBottomBarBlurEnabled || appThemeConfig.progressiveTopBlurEnabled
    val homeMiuixBackdropSource = if (shouldCaptureHomeChromeBackdrop) {
        rememberChromeBackdropSource()
    } else {
        null
    }
    val homeMiuixBackdrop = homeMiuixBackdropSource?.backdrop
    val readyHomeMiuixBackdrop = homeMiuixBackdropSource?.takeIf {
        chromeContentReady && it.isReady
    }?.backdrop

    // [性能优化] 避免双重捕获：当 MiuixBackdrop 已经激活且处于 Miuix 视觉体系时，顶栏与底栏均走
    // MiuixBackdrop 渲染，此时 Feed 容器无需重复挂载 HazeSource 离屏录制。仅在 MD3 工具栏或 Backdrop 缺失时保留 Haze。
    val appUiStyle = com.android.purebilibili.core.theme.LocalAppUiStyle.current
    val shouldCaptureHomeHaze = if (homeMiuixBackdropSource != null) {
        com.android.purebilibili.feature.home.components.shouldUseOfficialMd3HomeTopToolbar(
            uiStyle = appUiStyle,
            liquidGlassEnabled = isLiquidGlassEnabled,
        )
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
    val configuredHomeWallpaperUri by SettingsManager.getHomeWallpaperUri(context).collectAsStateWithLifecycle(initialValue = ""
        )
    val splashWallpaperUri by SettingsManager.getSplashWallpaperUri(context).collectAsStateWithLifecycle(initialValue = ""
        )
    val homeWallpaperUri = remember(configuredHomeWallpaperUri, splashWallpaperUri) {
        resolveHomeWallpaperUri(
            homeWallpaperUri = configuredHomeWallpaperUri,
            splashWallpaperUri = splashWallpaperUri
        )
    }
    val wallpaperPalette by com.android.purebilibili.feature.home.components.cards.WallpaperPaletteStore.currentPalette.collectAsStateWithLifecycle()
    val homeLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val homeLifecycleState by homeLifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(homeWallpaperUri, wallpaperPalette == null, homeLifecycleState) {
        if (homeLifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            if (wallpaperPalette == null || homeWallpaperUri.isNotBlank()) {
                com.android.purebilibili.feature.home.components.cards.WallpaperPaletteStore.loadWallpaperPalette(
                    context = context,
                    uri = homeWallpaperUri,
                    scope = this
                )
            }
        }
    }

    val appNavigationSettings by SettingsManager.getAppNavigationSettings(context).collectAsStateWithLifecycle(initialValue = AppNavigationSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
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
        homeSettings.gridColumnCount,
        homeSettings.gridColumnCountCompact,
        homeSettings.homeFeedCardWidthPreset,
        windowSizeClass.widthSizeClass
    ) {
        resolveHomeFeedGridColumns(
            contentWidthDp = contentWidth.value.toInt(),
            displayMode = displayMode,
            // 窄屏（折叠屏外屏/手机竖屏）与宽屏（内屏/平板）各自独立的固定列数记忆
            fixedColumnCount = resolveHomeFeedStoredColumnCount(
                widthSizeClass = windowSizeClass.widthSizeClass,
                compactColumnCount = homeSettings.gridColumnCountCompact,
                defaultColumnCount = homeSettings.gridColumnCount,
            ),
            cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    var interactiveColumns by remember { mutableStateOf<Int?>(null) }
    var isPinchPillVisible by remember { mutableStateOf(false) }
    var pinchPillDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val effectiveGridColumns = interactiveColumns ?: gridColumns
    val haptic = LocalHapticFeedback.current
    val pinchColumnBounds = remember(windowSizeClass.widthSizeClass, contentWidth, displayMode) {
        resolveHomeFeedPinchColumnBounds(
            widthSizeClass = windowSizeClass.widthSizeClass,
            contentWidthDp = contentWidth.value.toInt(),
            displayMode = displayMode,
        )
    }
    LaunchedEffect(
        homeSettings.gridColumnCount,
        homeSettings.gridColumnCountCompact,
    ) {
        interactiveColumns = null
    }

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
    val homeFeedCoverAspectRatio = homeFeedCardLayout.coverAspectRatio
    val density = LocalDensity.current
    val hingeGridSpec = remember(appWindowAdaptiveInfo, density.density) {
        resolveHomeFeedBookHingeGridSpec(appWindowAdaptiveInfo, density.density)
    }
    val homeFeedHorizontalArrangement = remember(
        effectiveGridColumns,
        homeFeedCardLayout.itemSpacingDp,
        hingeGridSpec,
    ) {
        resolveHomeFeedHorizontalArrangement(
            columns = effectiveGridColumns,
            baseSpacing = homeFeedCardLayout.itemSpacingDp.dp,
            hingeSpec = hingeGridSpec,
        )
    }
    
    
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
        homeSettings.homeCardDynamicTintEnabled &&
            homeWallpaperBackdropAppearance.visible &&
            homeWallpaperUri.isNotBlank() &&
            isStaticHomeWallpaperUri(homeWallpaperUri) &&
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
            //  根据背景亮度设置状态栏图标颜色
            insetsController.isAppearanceLightStatusBars = useDarkStatusBarIcons
            //  [修复] 导航栏也需要根据背景亮度设置图标颜色
            insetsController.isAppearanceLightNavigationBars = isLightBackground
            //  确保状态栏可见且透明
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
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
                coroutineScope.launch {
                    withHomeScrollToTopLock {
                        val gridState = if (currentCategory == HomeCategory.POPULAR) {
                            popularGridStates[popularSubCategory]
                        } else {
                            gridStates[currentCategory]
                        }
                        val isAtTop = gridState == null || (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 50)

                        if (isAtTop) {
                            viewModel.refresh()
                        } else {
                            val listState = requireNotNull(gridState)
                            listState.animateScrollToTop(fast = true)
                        }
                    }
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
    LaunchedEffect(currentCategory, popularSubCategory, bottomBarVisibilityMode, useSideNavigation) {
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
    val searchBarHeightDp = homeTopPresetStyle.searchBarHeight
    val tabRowHeightDp = resolveEffectiveHomeTabRowHeight(
        hideTopTabs = effectiveHomeSettings.hideTopTabs,
        defaultTabRowHeight = if (topTabStyle.floating) {
            homeTopPresetStyle.tabRowHeightFloating
        } else {
            homeTopPresetStyle.tabRowHeightDocked
        }
    )
    val searchCollapseDistanceDp = searchBarHeightDp +
        (if (effectiveHomeSettings.hideTopTabs) AppSpacingTokens.None else homeTopPresetStyle.searchToTabsSpacing) +
        homeTopPresetStyle.searchCollapseExtraSpacing
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
        searchToTabsSpacing = homeTopPresetStyle.searchToTabsSpacing
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
    val headerAutoCollapseDistancePx = if (isAnyHeaderCollapseEnabled) {
        searchCollapseDistancePx
    } else {
        0f
    }
    val collapsedEmbeddedTabInset by animateDpAsState(
        targetValue = if (topTabsAutoCollapsedByScroll) tabRowHeightDp else AppSpacingTokens.None,
        animationSpec = AppMotionTokens.emphasizedSpec(),
        label = "homeEmbeddedTabInset",
    )
    val embeddedPageTopPadding by remember(
        density,
        listTopPadding,
        statusBarHeight,
        collapsedEmbeddedTabInset,
    ) {
        derivedStateOf {
            with(density) {
                resolveHomeEmbeddedPageTopPaddingPx(
                    expandedTopPaddingPx = listTopPadding.toPx(),
                    headerOffsetPx = quantizeHomeHeaderOffset(
                        offsetPx = headerOffsetHeightPx,
                        stepPx = AppSpacingTokens.ExtraSmall.toPx(),
                    ),
                    collapsedTabInsetPx = collapsedEmbeddedTabInset.toPx(),
                    minimumTopPaddingPx = statusBarHeight.toPx(),
                ).toDp()
            }
        }
    }
    
    // [Feature] Bottom Bar Auto-Hide (based on scroll hide mode)
    val isBottomBarAutoHideEnabled = bottomBarVisibilityMode == SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE
    val bottomBarVisibleState = LocalSetBottomBarVisible.current
    
    // [Feature] Global Scroll Offset for Liquid Glass
    val activeGridState = if (currentCategory == HomeCategory.POPULAR) {
        popularGridStates[popularSubCategory]
    } else {
        gridStates[currentCategory]
    }

    val nestedScrollConnection = remember(
        isAnyHeaderCollapseEnabled,
        headerAutoCollapseDistancePx,
        isBottomBarAutoHideEnabled,
        useSideNavigation,
        isLiquidGlassEnabled,
        collapseTabsOnScroll,
        homeBarHideType,
        currentCategory,
        popularSubCategory,
        activeGridState,
        homeHeaderRevealLock,
        pagerState,
        topTabEntries,
        subscriptionListState,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (homeHeaderRevealLock) {
                    return Offset.Zero
                }
                if (!shouldHandleHomeVerticalPreScroll(deltaX = available.x, deltaY = available.y)) {
                    return Offset.Zero
                }
                val onSubscriptionTab = resolveHomeTopTabEntryOrNull(
                    topTabEntries,
                    pagerState.currentPage,
                ) == HomeTopTabEntry.Subscriptions
                val headerListIndex = resolveHomeHeaderListIndex(
                    displayedEntryIsSubscription = onSubscriptionTab,
                    categoryFirstVisibleIndex = activeGridState?.firstVisibleItemIndex ?: 0,
                    subscriptionFirstVisibleIndex = subscriptionListState.firstVisibleItemIndex,
                )
                val firstItemVisible = canRevealHomeHeaderForList(
                    firstVisibleItemIndex = headerListIndex,
                    listMissing = !onSubscriptionTab && activeGridState == null,
                )
                val scrollUpdate = reduceHomePreScroll(
                    currentHeaderOffsetPx = headerOffsetHeightPx,
                    deltaY = available.y,
                    minHeaderOffsetPx = -headerAutoCollapseDistancePx,
                    canRevealHeader = firstItemVisible,
                    collapseMode = CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY,
                    isHeaderCollapseEnabled = isAnyHeaderCollapseEnabled,
                    isBottomBarAutoHideEnabled = isBottomBarAutoHideEnabled,
                    useSideNavigation = useSideNavigation,
                    liquidGlassEnabled = isLiquidGlassEnabled,
                    currentGlobalScrollOffset = globalScrollOffset.value,
                    hideType = homeBarHideType,
                    isHeaderRevealLocked = homeHeaderRevealLock,
                )

                if (scrollUpdate.shouldAnimateHeader) {
                    animateHeaderOffsetTo(scrollUpdate.headerOffsetPx)
                } else {
                    headerSettleAnimationJob?.cancel()
                    headerSettleAnimationJob = null
                    headerOffsetHeightPx = scrollUpdate.headerOffsetPx
                }
                topTabsAutoCollapsedByScroll = collapseTabsOnScroll &&
                    !homeHeaderRevealLock &&
                    (
                        headerListIndex > 0 ||
                            headerOffsetHeightPx < -0.5f
                    )
                scrollUpdate.globalScrollOffset?.let { nextOffset ->
                    globalScrollOffset.value = nextOffset
                }
                when (scrollUpdate.bottomBarVisibilityIntent) {
                    BottomBarVisibilityIntent.SHOW -> bottomBarVisibleState(true)
                    BottomBarVisibilityIntent.HIDE -> bottomBarVisibleState(false)
                    null -> Unit
                }

                if (headerListIndex == 0 &&
                    headerOffsetHeightPx >= -0.5f
                ) {
                    topTabsAutoCollapsedByScroll = false
                }

                return Offset.Zero
            }
        }
    }
    //  包装 onVideoClick：点击视频时先隐藏底栏再导航
    val wrappedOnVideoClick: (HomeVideoClickRequest) -> Unit = remember(
        onVideoClick,
        setBottomBarVisible,
        currentCategory,
        popularSubCategory,
        gridStates,
        popularGridStates
    ) {
        { request ->
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
            onVideoClick(request)
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
                    isVerticalVideo = video.isVertical,
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
        currentCategory
    ) {
        if (todayWatchStartupRevealHandled) return@LaunchedEffect

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
    val homeFeedOwnsVideoCardSnapshot = shouldHomeFeedOwnVideoCardTransitionSnapshot(
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
        )
    } else {
        Modifier
    }
    val scaffoldLayout: @Composable () -> Unit = {
        // Composite header and feed before applying depth; separate blur layers form a seam.
        Box(modifier = Modifier.fillMaxSize().then(homeFeedSnapshotModifier)) {
        AppScaffold(
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
                            homeWallpaperUri.isNotBlank() && isStaticHomeWallpaperUri(homeWallpaperUri)
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
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 0,
                            userScrollEnabled = false,
                            modifier = Modifier
                                .responsiveContentWidth(maxWidth = contentWidth)
                                .fillMaxSize()
                                .verticalPriorityHorizontalPagerSwipe(
                                    state = pagerState,
                                    enabled = homeTopPagerSwipeEnabled,
                                    horizontalLockSlopMultiplier =
                                        HOME_PAGER_HORIZONTAL_LOCK_SLOP_MULTIPLIER,
                                    shouldYield = shouldYieldHomePagerToHeroCarousel,
                                ),
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
                                    articleContentPadding = PaddingValues(
                                        top = statusBarHeight + AppSpacingTokens.Small,
                                        bottom = homeListBottomPadding,
                                        start = AppSpacingTokens.Large,
                                        end = AppSpacingTokens.Large,
                                    ),
                                    scrollToTopRequestId = subscriptionScrollToTopRequestId,
                                    listState = subscriptionListState,
                                    gridColumns = effectiveGridColumns,
                                    pinchEnabled = !isSingleColumnMode && homeSettings.pinchToChangeGridColumnsEnabled,
                                    pinchBounds = pinchColumnBounds,
                                    onColumnsChange = { newColumns ->
                                        interactiveColumns = newColumns
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        isPinchPillVisible = true
                                        pinchPillDismissJob?.cancel()
                                    },
                                    onArticleOpenChanged = { subscriptionArticleOpen = it },
                                    onPinchEnd = { finalColumns ->
                                        coroutineScope.launch {
                                            // 窄屏（外屏/手机）与宽屏（内屏/平板）各写各的记忆
                                            if (isCompactHomeFeedScreen(windowSizeClass.widthSizeClass)) {
                                                SettingsManager.setGridColumnCountCompact(context, finalColumns)
                                            } else {
                                                SettingsManager.setGridColumnCount(context, finalColumns)
                                            }
                                        }
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
                            LiveListScreen(
                                onBack = {},
                                onLiveClick = onLiveClick,
                                onSearchClick = onLiveSearchClick,
                                onAreaListClick = onLiveAreaClick,
                                onFollowingClick = onLiveFollowingClick,
                                onAreaDetailClick = onLiveAreaDetailClick,
                                showNavigationBack = false,
                                embeddedInHome = true,
                                contentTopPadding = embeddedPageTopPadding,
                                scrollToTopRequestId = liveScrollToTopRequestId,
                            )
                        } else if (shouldEmbedBangumiPageInHomeTopTab(category)) {
                            HomeBangumiTabPage(
                                contentPadding = PaddingValues(
                                    top = embeddedPageTopPadding,
                                    bottom = homeListBottomPadding
                                ),
                                onBangumiClick = onBangumiSeasonClick,
                                onBangumiEpisodeClick = onBangumiEpisodeClick,
                                scrollToTopRequestId = bangumiScrollToTopRequestId,
                            )
                        } else {
                        val categoryStateFlow = remember(viewModel, category, popularSubCategory) {
                            if (category == HomeCategory.POPULAR) {
                                viewModel.getPopularCategoryState(popularSubCategory)
                            } else {
                                viewModel.getCategoryState(category)
                            }
                        }
                        val categoryState by categoryStateFlow.collectAsStateWithLifecycle()
                        
                        //  独立的 PullToRefreshState，避免所有页面共享一个状态导致冲突
                        val pullRefreshState = rememberPullToRefreshState()
                        val pullDistanceFraction = pullRefreshState.distanceFraction
                        val isPageRefreshing = isRefreshing && currentCategory == category
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
                        val animatedDragOffsetFraction by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = resolvedStablePullOffsetFraction,
                            animationSpec = if (
                                shouldSnapPullOffsetToFinger(
                                    distanceFraction = pullDistanceFraction,
                                    isRefreshing = isPageRefreshing,
                                    isStateAnimating = pullRefreshState.isAnimating,
                                    indicatorStyle = pullRefreshIndicatorStyle
                                )
                            ) {
                                androidx.compose.animation.core.snap()
                            } else {
                                pullRefreshReleaseSpring()
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
                                maxPx * animatedDragOffsetFraction
                            }
                        }
                        
                        //  每个页面独立的 GridState
                        //  使用 saveable 记住滚动位置
                        val pageGridState = if (category == HomeCategory.POPULAR) {
                            popularGridStates[popularSubCategory] ?: rememberLazyStaggeredGridState()
                        } else {
                            gridStates[category] ?: rememberLazyStaggeredGridState()
                        }
                        
                        //  把 GridState 提升给父级用于控制 Header? 
                        
                        // Overlay chrome (status + search + tabs); not for Scaffold-padded screens.
                        val homeRefreshIndicatorTopInset = listTopPadding
                        AdaptivePullToRefreshBox(
                            isRefreshing = isRefreshing && currentCategory == category,
                            onRefresh = {
                                viewModel.refresh(category)
                            },
                            state = pullRefreshState,
                            indicatorTopInset = homeRefreshIndicatorTopInset,
                            modifier = Modifier.fillMaxSize(),
                             //  不同原生外观使用不同下拉刷新指示器，位移策略仍由 policy 统一控制。
                             //  Custom indicators must include the same top inset as MIUIX contentPadding.
                             indicator = {
                                when (pullRefreshIndicatorStyle) {
                                    AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT -> {
                                        // Official M3 expressive ContainedLoadingIndicator
                                        // (dynamic color) for Android Native Material 3.
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
                                           translationY = if (
                                               pullRefreshIndicatorStyle == AppPullRefreshIndicatorStyle.MIUIX_NATIVE ||
                                               pullRefreshIndicatorStyle == AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT
                                           ) {
                                               0f
                                           } else {
                                               calculateDragOffset()
                                           }
                                       }
                                       .homeFeedPinchZoom(
                                           enabled = !isSingleColumnMode && homeSettings.pinchToChangeGridColumnsEnabled,
                                           currentColumns = effectiveGridColumns,
                                           bounds = pinchColumnBounds,
                                           onColumnsChange = { newColumns ->
                                               interactiveColumns = newColumns
                                               haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                               isPinchPillVisible = true
                                               pinchPillDismissJob?.cancel()
                                           },
                                           onGestureEnd = { finalColumns ->
                                               coroutineScope.launch {
                                                   // 窄屏（外屏/手机）与宽屏（内屏/平板）各写各的记忆
                                                   if (isCompactHomeFeedScreen(windowSizeClass.widthSizeClass)) {
                                                       SettingsManager.setGridColumnCountCompact(context, finalColumns)
                                                   } else {
                                                       SettingsManager.setGridColumnCount(context, finalColumns)
                                                   }
                                               }
                                               pinchPillDismissJob?.cancel()
                                               pinchPillDismissJob = coroutineScope.launch {
                                                   kotlinx.coroutines.delay(1000)
                                                   isPinchPillVisible = false
                                               }
                                           }
                                       )
                              ) {
                              if (category != HomeCategory.POPULAR && categoryState.isLoading && categoryState.videos.isEmpty() && categoryState.liveRooms.isEmpty()) {
                                  // Loading Skeleton per page
                                  val skeletonPulse = rememberHomeFeedSkeletonPulse()
                                  LazyVerticalStaggeredGrid(
                                      columns = StaggeredGridCells.Fixed(effectiveGridColumns),
                                      contentPadding = PaddingValues(
                                          bottom = homeListBottomPadding,
                                          start = homeFeedCardLayout.outerPaddingDp.dp,
                                          end = homeFeedCardLayout.outerPaddingDp.dp,
                                          top = listTopPadding
                                      ),
                                      horizontalArrangement = homeFeedHorizontalArrangement,
                                      verticalItemSpacing = homeFeedCardLayout.verticalItemSpacingDp.dp,
                                      modifier = Modifier.fillMaxSize()
                                  ) {
                                      // [新增] 用户启用首页横幅时，骨架顶部渲染横幅占位，
                                      // 与加载完成后的 HomeHeroCarousel 布局对齐
                                      if (category == HomeCategory.RECOMMEND && homeSettings.homeHeroCarouselEnabled) {
                                          item(
                                              key = "home_hero_carousel_skeleton",
                                              contentType = "home_hero_carousel_skeleton",
                                              span = StaggeredGridItemSpan.FullLine
                                          ) {
                                              HomeFeedHeroCarouselSkeleton(
                                                  pulse = skeletonPulse
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
                                             pulse = skeletonPulse,
                                             wallpaperTintEnabled = homeWallpaperBackdropAppearance.visible,
                                             wallpaperEffectMode = homeSettings.homeWallpaperEffectMode,
                                             isDataSaverActive = isDataSaverActive,
                                             coverAspectRatio = homeFeedCoverAspectRatio
                                         )
                                     }
                                 }
                             } else {
                                 val categoryError = categoryState.error
                                 if (categoryError != null && categoryState.videos.isEmpty()) {
                                 // Error State per page
                                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                     ModernErrorState(
                                         message = categoryError,
                                         onRetry = { viewModel.refresh() }
                                     )
                                 }
                                 } else {
                                 // Data Content
                                 // [性能优化] Stabilize event callbacks to prevent recomposition on scroll
                                 val onLoadMoreCallback = remember(viewModel) { { viewModel.loadMore() } }
                                 val onDismissVideoCallback = remember {
                                     { video: VideoItem ->
                                         pendingNotInterestedVideo = video
                                     }
                                 }
                                 val onWatchLaterCallback = remember(viewModel) { { bvid: String, aid: Long -> viewModel.addToWatchLater(bvid, aid) } }
                                 val onDissolveCompleteCallback = remember(viewModel) { { bvid: String -> viewModel.completeVideoDissolve(bvid) } }
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
                                 val onLiveClickCallback = remember(onLiveClick) { onLiveClick }
                                 val onTodayWatchModeChange = remember(viewModel) { { mode: TodayWatchMode -> viewModel.switchTodayWatchMode(mode) } }
                                 val onTodayWatchCollapsedChange = remember(viewModel) { { collapsed: Boolean -> viewModel.setTodayWatchCollapsed(collapsed) } }
                                 val onTodayWatchRefresh = remember(viewModel) { { viewModel.refreshTodayWatchOnly() } }
                                 val onTodayWatchUpClick = remember(onSpaceClick) { { mid: Long -> onSpaceClick(mid) } }
                                 val onHomeFeedUpClick = remember(onSpaceClick) { { mid: Long -> onSpaceClick(mid) } }
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
                                     gridState = contentGridState,
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
                                     onDismissVideo = onDismissVideoCallback,
                                     onWatchLater = onWatchLaterCallback,
                                     onDissolveComplete = onDissolveCompleteCallback,
                                     longPressCallback = onLongPressCallback, // [Feature] Pass callback
                                     displayMode = displayMode,
                                     // 刷新数据换位时不再同时启动整屏卡片 placement spring。
                                     cardAnimationEnabled = cardAnimationEnabled && !isPageRefreshing,
                                     cardMotionTier = cardMotionTier,
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
                                             oldContentStartIndex = recommendOldContentStartIndex
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
                                             oldContentStartIndex = recommendOldContentStartIndex
                                         )
                                     ) {
                                         recommendOldContentStartIndex
                                     } else {
                                         null
                                     },
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
                                     onTodayWatchVideoClick = onTodayWatchVideoClick,
                                     firstGridItemModifier = Modifier
                                 )
                                 }
                                 if (category == HomeCategory.POPULAR) {
                                     val subCategoryStateFlow = remember(viewModel, popularSubCategory) {
                                         viewModel.getPopularCategoryState(popularSubCategory)
                                     }
                                     val subCategoryState by subCategoryStateFlow.collectAsStateWithLifecycle()
                                     val subCategoryGridState =
                                         popularGridStates[popularSubCategory] ?: rememberLazyStaggeredGridState()
                                     renderHomeCategoryPage(
                                         subCategoryState,
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
                             }
                             } // Close Box wrapper
                        }
                            }
                            }
                            null -> Unit
                        }
                } // Close HorizontalPager lambda
            } // Close Box wrapper
                    } // Close LocalHomeMiuixBackdrop provider
        } // Close Scaffold lambda
        
        //  ===== Header Overlay (毛玻璃效果) =====
        //  Header 现在在外层 Box 内、hazeSource 外部，可以正确模糊内层内容
        //  [Restored] Header 始终显示，不再随 Loading/Error 状态隐藏
        //  这保证了 Tab 指示器状态的连续性，防止消失或重置
        val isFeedScrollInProgress by remember(activeGridState) {
            derivedStateOf { activeGridState?.isScrollInProgress == true }
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
        val overlayPillColors = rememberHomeGlassPillColors(
            glassEnabled = isLiquidGlassEnabled,
            blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled,
            emphasized = true,
            baseColor = AppSurfaceTokens.cardContainer()
        )
        
        // Calculate parameters based on scroll
        val topTabsCollapsedForHeader = if (collapseTabsOnScroll) {
            topTabsAutoCollapsedByScroll
        } else {
            false
        }
        // [Optimization] Stable lambda: defers the state read to draw and keeps
        // Keep HomeHeader skippable (a fresh lambda each frame would defeat skipping).
        val headerOffsetProvider = remember { { headerOffsetHeightPx } }
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
            topRightUnreadCount = messageUnreadCount,
            onSearchClick = onSearchClick,
            topCategories = localizedTopTabLabels,
            topCategoryKeys = topTabKeys,
            categoryIndex = displayedTabIndex,
            onCategorySelected = onCategorySelected@ { index ->
                val selectedEntry = topTabEntries.getOrNull(index) ?: return@onCategorySelected
                viewModel.updateDisplayedTabIndex(index)
                retainedTopTabEntry = selectedEntry
                if (pagerState.currentPage != index) {
                    programmaticPageSwitchInProgress = true
                    coroutineScope.launch {
                        try {
                            animatePagerSelection(pagerState, index)
                        } finally {
                            programmaticPageSwitchInProgress = false
                        }
                    }
                }
                if (selectedEntry is HomeTopTabEntry.Category) {
                    viewModel.switchCategory(selectedEntry.category)
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
                coroutineScope.launch {
                    withHomeScrollToTopLock {
                        activeGridState?.animateScrollToTop()
                    }
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
                        color = MaterialTheme.colorScheme.onSurface,
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
        val undoVisible = undoAvailable && currentCategory == HomeCategory.RECOMMEND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(95f),
            contentAlignment = Alignment.BottomEnd
        ) {
            AnimatedVisibility(
                visible = undoVisible,
                enter = fadeIn(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideInVertically(
                    animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideOutVertically(
                    animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                    targetOffsetY = { it }
                ),
                modifier = Modifier.padding(end = AppSpacingTokens.Large, bottom = homeListBottomPadding + AppSpacingTokens.Small)
            ) {
            AppButton(
                onClick = { viewModel.undoRefresh() },
                modifier = Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = overlayPillColors.containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(AppSpacingTokens.Micro * 0.4f, overlayPillColors.borderColor),
                shape = AppShapes.container(ContainerLevel.Pill),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = AppSpacingTokens.ExtraSmall,
                    pressedElevation = AppSpacingTokens.Micro
                ),
                contentPadding = PaddingValues(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small + AppSpacingTokens.Micro)
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
            }
            }
        }

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
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ),
            exit = fadeOut(tween(overlayMotionSpec.previewOverlayFadeDurationMillis)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(
                    overlayMotionSpec.previewOverlayScaleDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
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
                             isVerticalVideo = item.isVertical,
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
                onNotInterested = {
                    pendingNotInterestedVideo = item
                    targetVideoItemState.value = null
                },
                onBlockCreator = {
                    viewModel.blockCreator(item)
                    targetVideoItemState.value = null
                },
                onGetPreviewUrl = { bvid, cid ->
                    viewModel.getPreviewVideoUrl(bvid, cid)
                },
                hazeState = hazeState
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
    LaunchedEffect(
        currentCategory,
        popularSubCategory,
        isDataSaverActive,
        preloadAheadCount,
        isReturningFromVideoDetail,
    ) {
        // 📉 省流量模式下跳过预加载
        if (isDataSaverActive) return@LaunchedEffect
        if (preloadAheadCount <= 0) return@LaunchedEffect
        // 详情返回 morph 窗口：延后封面预加载，避免与 live surface + 景深抢 IO/主线程。
        if (isReturningFromVideoDetail) return@LaunchedEffect
        
        val currentGridState = if (currentCategory == HomeCategory.POPULAR) {
            popularGridStates[popularSubCategory]
        } else {
            gridStates[currentCategory]
        } ?: return@LaunchedEffect
        
        snapshotFlow {
            val visibleKeys = currentGridState.layoutInfo.visibleItemsInfo.map { it.key }
            visibleKeys to currentGridState.isScrollInProgress
        }
            .distinctUntilChanged()
            // Coalesce the burst of layout updates after a fling and wait until the feed has
            // been settled briefly. A new scroll event cancels this pending preload batch.
            .debounce(180)
            .collect { (visibleKeys, isScrollInProgress) ->
                val videos = viewModel.getPreloadVideosSnapshot(
                    category = currentCategory,
                    popularSubCategory = popularSubCategory
                )
                val visibleKeySet = visibleKeys.toSet()
                val lastVisibleIndex = resolveHomeCategoryVideoGridKeys(videos)
                    .indexOfLast { it in visibleKeySet }
                val preloadRange = resolveHomeCoverPreloadRange(
                    isDataSaverActive = isDataSaverActive,
                    isScrollInProgress = isScrollInProgress,
                    lastVisibleIndex = lastVisibleIndex,
                    totalItemCount = videos.size,
                    preloadAheadCount = preloadAheadCount
                ) ?: return@collect
                // Avoid enqueueing the same cover more than once when adjacent feed entries share
                // a URL; Coil still handles caching, but deduping keeps the IO queue smaller.
                val imageUrls = preloadRange
                    .mapNotNull { index -> videos.getOrNull(index)?.pic }
                    .distinct()
                if (imageUrls.isEmpty()) return@collect

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    for (imageUrl in imageUrls) {
                        val fixedUrl = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(imageUrl)

                        val request = coil3.request.ImageRequest.Builder(context)
                            .data(fixedUrl)
                            .size(360, 225)  //  预加载也使用限制尺寸
                            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .build()
                        context.imageLoader.enqueue(request)
                    }
                }
            }
    }


    //  PullToRefreshBox 自动处理下拉刷新逻辑
    
    //  [已移除] 特殊分类（ANIME, MOVIE等）不再在首页切换，直接导航到独立页面
    
    //  [修复] 如果当前在直播-关注分类且列表为空，返回时先切换到热门，再切换到推荐
    val liveCategoryStateFlow = remember(viewModel) {
        viewModel.getCategoryState(HomeCategory.LIVE)
    }
    val liveCategoryState by liveCategoryStateFlow.collectAsStateWithLifecycle()
    val isEmptyLiveFollowed = currentCategory == HomeCategory.LIVE &&
                               liveSubCategory == LiveSubCategory.FOLLOWED &&
                               liveCategoryState.followedLiveRooms.isEmpty() &&
                               !liveCategoryState.isLoading
    androidx.activity.compose.BackHandler(enabled = isEmptyLiveFollowed) {
        // 切换到热门直播
        viewModel.switchLiveSubCategory(LiveSubCategory.POPULAR)
    }

    //  [修复] 如果当前在直播分类（非关注空列表情况），返回时切换到推荐
    val isLiveCategoryNotHome = currentCategory == HomeCategory.LIVE && !isEmptyLiveFollowed
    androidx.activity.compose.BackHandler(enabled = isLiveCategoryNotHome) {
        viewModel.switchCategory(HomeCategory.RECOMMEND)
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
                    pendingNotInterestedVideo = null
                    viewModel.markNotInterested(
                        video = video,
                        reason = reason,
                        cardAnimationEnabled = cardAnimationEnabled
                    )
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
