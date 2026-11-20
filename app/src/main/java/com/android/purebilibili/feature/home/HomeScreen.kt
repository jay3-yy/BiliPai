// 文件路径: feature/home/HomeScreen.kt
package com.android.purebilibili.feature.home

import com.android.purebilibili.core.plugin.BuiltinPluginIds


import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi //  Added
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import com.android.purebilibili.core.ui.rememberHomeStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.*  // 🌊 瀑布流布局
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed
import com.android.purebilibili.core.ui.blur.rememberPagerChromeBackdropRecordingAllowed
import com.android.purebilibili.core.ui.common.HOME_PAGER_HORIZONTAL_LOCK_SLOP_MULTIPLIER
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.util.animateScrollWhileActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material3.DrawerValue
import com.android.purebilibili.core.ui.components.AppModalNavigationDrawer
import com.android.purebilibili.core.ui.components.AppButton
import androidx.compose.material3.rememberDrawerState
import com.android.purebilibili.feature.home.components.MineSideDrawer
import com.android.purebilibili.feature.home.components.resolveHomeTopLinkedBottomBarAppearance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.android.purebilibili.feature.home.policy.captureHomeFeedScrollAnchor
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderTransitionRunning
import com.android.purebilibili.feature.home.policy.shouldReserveHomeBottomBarListPadding
import com.android.purebilibili.feature.home.policy.reduceHomeBottomBarListScroll
import com.android.purebilibili.feature.home.policy.shouldEmitBottomBarScrollSample
import com.android.purebilibili.feature.home.policy.resolveHomeBottomBarBaseVisibility
import com.android.purebilibili.feature.home.policy.resolveHomeRecommendationHeaderCollapseMode
import com.android.purebilibili.feature.bangumi.BangumiChannel
import com.android.purebilibili.feature.bangumi.HomeBangumiTabPage
import com.android.purebilibili.feature.live.LiveListScreen
import com.android.purebilibili.feature.home.policy.resolveHomeInitialTopTabPage
import com.android.purebilibili.feature.home.policy.shouldEnableHomeTopPagerUserScroll
//  从 cards 子包导入卡片组件
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard   //  故事卡片
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.ui.ErrorState as ModernErrorState
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
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
import com.android.purebilibili.core.ui.animation.LocalAppNavigationEntranceReady
import com.android.purebilibili.core.store.CardRemovalDissolveMode
import com.android.purebilibili.core.ui.animation.animationPreset
import com.android.purebilibili.core.ui.animation.gl.isThanosEffectSupported
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.core.util.VideoDetailPresentationMode
import com.android.purebilibili.core.util.VideoDetailPresentationInput
import com.android.purebilibili.core.util.resolveVideoDetailPresentation
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackJankStateValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged  //  性能优化：防止重复触发
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map as mapFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import androidx.compose.animation.ExperimentalSharedTransitionApi  //  共享过渡实验API
import com.android.purebilibili.core.ui.LocalSetBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding

import kotlinx.coroutines.channels.Channel
import com.android.purebilibili.data.model.response.VideoItem // [Fix] Import VideoItem
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.feature.home.components.HomeNotInterestedReasonSheet
import com.android.purebilibili.feature.partition.PartitionContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class HomeScrollRequest {
    SCROLL_TO_TOP,
    SCROLL_TO_TOP_OR_REFRESH,
    SCROLL_TO_TOP_AND_REFRESH,
}

// 首页只有一个可见消费者；Channel 保证底栏重选/双击事件不会在暂停采集时丢失。
val LocalHomeScrollChannel = compositionLocalOf<Channel<HomeScrollRequest>?> { null }

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
internal fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    homeTopTabSettings: com.android.purebilibili.core.store.HomeTopTabSettings,
    onVideoClick: (HomeVideoClickRequest) -> Unit,
    onCompanionVideoClick: (HomeVideoClickRequest) -> Unit = {},
    availableFeedWidth: Dp? = null,
    companionPaneEnabled: Boolean = true,
    onProfileClick: () -> Unit,
    onLogout: (() -> Unit)? = null,
    onAccountSwitchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onHeaderNavigate: (com.android.purebilibili.navigation3.BiliPaiNavKey, HomeHeaderControl, () -> Boolean) -> Unit,
    headerReturn: HomeHeaderReturn?,
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
                title = video.title,
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
    onOpenFavoritePgc: ((BangumiChannel) -> Unit)? = null,
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
    onVideoDetailReturnAnimationConsumed: () -> Unit = {},
    onRecoverStaleTransitionGate: () -> Unit = {},
) {
    if (!rememberHomeMemoryRecoveryReady(viewModel)) return
    com.android.purebilibili.core.performance.PerformanceScreenProbe("home")
    val pendingMemoryRecovery by viewModel.pendingMemoryRecovery.collectAsStateWithLifecycle()
    val feedNavigation = rememberHomeFeedNavigationCallbacks(onVideoClick, onSpaceClick, onLiveClick)
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentCategory by viewModel.currentCategory.collectAsStateWithLifecycle()
    val displayedTabIndexFromState by viewModel.displayedTabIndex.collectAsStateWithLifecycle()
    val popularSubCategory by viewModel.popularSubCategory.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val refreshNewItemsCount by viewModel.refreshNewItemsCount.collectAsStateWithLifecycle()
    val refreshNewItemsKey by viewModel.refreshNewItemsKey.collectAsStateWithLifecycle()
    val recommendOldContentAnchorBvid by viewModel.recommendOldContentAnchorBvid.collectAsStateWithLifecycle()
    val recommendOldContentStartIndex by viewModel.recommendOldContentStartIndex.collectAsStateWithLifecycle()
    val recommendOldContentRevealKey by viewModel.recommendOldContentRevealKey.collectAsStateWithLifecycle()
// val pullRefreshState = rememberPullToRefreshState() // [Removed] Moved inside HorizontalPager
    val context = LocalContext.current
    val messageBadgeMode = remember(context) {
        com.android.purebilibili.core.store.MessageCenterPreferences.messageBadgeMode(context)
    }
    val uiSkinState by rememberUiSkinState(context)
    val homeUiSkinDecoration = rememberHomeUiSkinDecoration(uiSkinState)
    val overlayMotionSpec = remember { resolveHomeOverlayMotionSpec() }
    val homeSettings = com.android.purebilibili.feature.settings.rememberSettingsHomeConfiguration()
    val useRegularHomeGrid = !homeSettings.showFullVideoCardContent
    val gridStates = remember(useRegularHomeGrid) { mutableMapOf<HomeCategory, HomeFeedGridState>() }
    HomeCategory.entries.forEach { category ->
        gridStates[category] = rememberHomeFeedGridState(useRegularHomeGrid)
    }
    val popularGridStates = remember(useRegularHomeGrid) { mutableMapOf<PopularSubCategory, HomeFeedGridState>() }
    PopularSubCategory.entries.forEach { subCategory ->
        popularGridStates[subCategory] = rememberHomeFeedGridState(useRegularHomeGrid)
    }
    var liveScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var bangumiScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var partitionScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var subscriptionScrollToTopRequestId by remember { mutableIntStateOf(0) }
    var subscriptionArticleOpen by remember { mutableStateOf(false) }
    var subscriptionArticleScrolling by remember { mutableStateOf(false) }
    val subscriptionListState = rememberHomeStaggeredGridState()
    // [Feature] Video Preview State (Global Scope)
    val downloadAccount by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val downloadSelection = remember(currentCategory, popularSubCategory, downloadAccount) { HomeDownloadSelection() }
    val targetVideoItemState = remember { mutableStateOf<VideoItem?>(null) }
    var dissolvingNotInterestedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var pendingNotInterestedVideo by remember { mutableStateOf<VideoItem?>(null) }
    val pendingVideoShare = remember {
        mutableStateOf<com.android.purebilibili.feature.video.share.VideoSharePayload?>(null)
    }
    val coroutineScope = rememberCoroutineScope() // 用于双击回顶动画
    val headerSettleMotionSpec = AppMotionTokens.emphasizedSpec<Float>()
    val globalScrollOffset = LocalHomeScrollOffset.current
    val globalFeedScrollInProgress = LocalHomeFeedScrollInProgress.current
    val headerScrollState = rememberHomeHeaderScrollState(globalScrollOffset)
    val headerOffsetProvider = headerScrollState.offsetProvider
    var homeNavigationRevision by remember { mutableLongStateOf(0L) }
    val returnState = rememberHomeReturnState()

    // [新增] 监听全局回顶事件
    val scrollChannel = LocalHomeScrollChannel.current
    val latestHomeScrollCategory by rememberUpdatedState(currentCategory)
    val latestHomeScrollPopularSubCategory by rememberUpdatedState(popularSubCategory)

    // 顶部标签顺序和可见项交给设置页控制；默认仍是六项。
    // [Refactor] Hoist PagerState to be available for both Content and Header
    // 确保 pagerState 在所有作用域均可见，以便传给 HomeHeader
    val subscriptionRevision by com.android.purebilibili.core.plugin.feed.SubscriptionFeedStore.revision
        .collectAsStateWithLifecycle()
    val installedPlugins by com.android.purebilibili.core.plugin.PluginManager.pluginsFlow
        .collectAsStateWithLifecycle()
    val isSubscriptionPluginPersistedEnabled by remember(context) {
        com.android.purebilibili.core.plugin.PluginStore.isEnabledFlow(context, BuiltinPluginIds.SUBSCRIPTION_FEED)
    }.collectAsStateWithLifecycle(initialValue = false)
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
    val initialPage = pendingMemoryRecovery?.let { saved ->
        topTabEntries.indexOfFirst { it.id == saved.category }.takeIf { it >= 0 }
    } ?: resolveHomeInitialTopTabPage(
        topTabEntries = topTabEntries,
        currentCategory = currentCategory,
        displayedTabIndex = displayedTabIndexFromState
    )
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage) { topTabEntries.size }
    LaunchedEffect(pendingMemoryRecovery?.id, topTabKeys) {
        val saved = pendingMemoryRecovery ?: return@LaunchedEffect
        val target = topTabEntries.indexOfFirst { it.id == saved.category }
        if (target < 0) viewModel.consumeMemoryRecovery(saved.id)
        else {
            viewModel.updateDisplayedTabIndex(target)
            pagerState.scrollToPage(target)
        }
    }
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
    val pagerCoordinator = rememberHomePagerCoordinator(
        pagerState = pagerState,
        topTabEntries = topTabEntries,
        currentCategory = currentCategory,
        initialPage = initialPage,
    )
    val latestHomeTopTabEntries by rememberUpdatedState(topTabEntries)
    val latestHomeTopLevelActive by rememberUpdatedState(isTopLevelActive)
    val homeVisualWorkActive = rememberVisualWorkActive()
    val latestHomeReturnMotionAllowed by rememberUpdatedState(
        homeVisualWorkActive && LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current,
    )

    fun selectHomeTopTab(index: Int) {
        val selectedEntry = topTabEntries.getOrNull(index) ?: return
        if (selectedEntry != resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage) ||
            pagerState.isScrollInProgress
        ) {
            // 点击或返回入口立即失效旧请求，不等待横向动画开始上报。
            homeNavigationRevision++
            headerScrollState.setOffsetImmediately(headerOffsetProvider())
        }
        viewModel.updateDisplayedTabIndex(index)
        pagerCoordinator.retainedEntry = selectedEntry
        if (pagerState.currentPage != index ||
            (nativeMd3TabMotion && pagerState.isScrollInProgress)
        ) {
            pagerCoordinator.selectPage(index, homeTabSelectionAnimationSpec, coroutineScope)
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
        if (request == HomeScrollRequest.SCROLL_TO_TOP && headerScrollState.scrollToTopInProgress) return
        val requestIsCurrent = {
            shouldApplyHomeScrollRequest(
                requested = identity,
                current = currentHomeScrollRequestIdentity(),
                isTopLevelActive = latestHomeTopLevelActive,
                pagerScrolling = pagerState.isScrollInProgress,
            )
        }
        val entry = identity.entry
        val target = resolveHomeTopTabScrollTarget(entry)
        headerScrollState.withScrollToTopLock(
            requestIsCurrent = requestIsCurrent,
            settleMotionSpec = headerSettleMotionSpec.takeIf {
                target != HomeTopTabScrollTarget.FEED && latestHomeReturnMotionAllowed
            },
        ) {
            when (target) {
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

                    val shouldRefresh = request == HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH ||
                        (request == HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH && isAtTop)
                    if (!isAtTop) {
                        val listState = requireNotNull(gridState)
                        if (shouldRefresh || isRefreshing || !latestHomeReturnMotionAllowed) {
                            // 刷新换批前一次到位，旧内容不参与回顶动画。
                            listState.scrollToItem(0)
                        } else {
                            val completed = headerScrollState.followReturnToTop(
                                remainingScrollPx = {
                                    listState.remainingScrollToTopPx().takeIf { requestIsCurrent() }
                                },
                            ) {
                                animateScrollWhileActive(
                                    isActive = { requestIsCurrent() && latestHomeReturnMotionAllowed && !isRefreshing },
                                ) {
                                    listState.animateScrollToItem(0)
                                }
                            }
                            if (!completed) return@withScrollToTopLock false
                        }
                    }
                    if (shouldRefresh && requestIsCurrent()) {
                        viewModel.refresh(
                            activeCategory,
                            identity.popularSubCategory ?: latestHomeScrollPopularSubCategory,
                        )
                    }
                }
            }
            true
        }
    }
    LaunchedEffect(scrollChannel, useRegularHomeGrid) {
        val channel = scrollChannel ?: return@LaunchedEffect
        var activeRequest: Job? = null
        var activeIdentity: HomeScrollRequestIdentity? = null
        for (request in channel) {
            val requestIdentity = currentHomeScrollRequestIdentity()
            if (request == HomeScrollRequest.SCROLL_TO_TOP &&
                requestIdentity == activeIdentity && activeRequest?.isActive == true
            ) continue
            // 首击立即滚动；第二击接管当前列表位置，提交一次回顶刷新。
            activeRequest?.cancelAndJoin()
            activeIdentity = requestIdentity
            activeRequest = launch {
                performHomeScrollRequest(requestIdentity, request)
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

    HomePagerSyncEffects(
        coordinator = pagerCoordinator,
        currentCategory = currentCategory,
        displayedTabIndex = displayedTabIndexFromState,
        isTopLevelActive = isTopLevelActive,
        animationSpec = homeTabSelectionAnimationSpec,
        onDisplayedTabIndexChange = viewModel::updateDisplayedTabIndex,
        onCategoryChange = viewModel::switchCategory,
    )

    HomeFeedReturnEffects(
        state = returnState,
        pagerCoordinator = pagerCoordinator,
        headerScrollState = headerScrollState,
        isTopLevelActive = isTopLevelActive,
        useRegularHomeGrid = useRegularHomeGrid,
        currentCategory = currentCategory,
        popularSubCategory = popularSubCategory,
        gridStates = gridStates,
        popularGridStates = popularGridStates,
    )

    //  [新增] JSON 插件过滤提示
    val snackbarHostState = remember { SnackbarHostState() }
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
    val videoCardTransitionBackgroundState = LocalVideoCardTransitionBackgroundState.current
    val deferHomeHeavyWork by remember(videoCardTransitionBackgroundState) {
        derivedStateOf {
            com.android.purebilibili.core.ui.transition.shouldDeferVideoCardSourceHeavyWork(
                videoCardTransitionBackgroundState.exposureProvider(),
            )
        }
    }
    val videoCardReturnGestureInProgress =
        videoCardTransitionBackgroundState.isReturnGestureInProgressProvider()
    val latestIsTopLevelActive by rememberUpdatedState(isTopLevelActive)
    val latestIsReturningFromDetail by rememberUpdatedState(isReturningFromVideoDetail)
    val latestDeferHomeHeavyWork by rememberUpdatedState(deferHomeHeavyWork)
    val latestReturnGestureInProgress by rememberUpdatedState(videoCardReturnGestureInProgress)
    val latestOnReturnConsumed by rememberUpdatedState(onVideoDetailReturnAnimationConsumed)
    val latestOnRecoverStaleTransitionGate by rememberUpdatedState(onRecoverStaleTransitionGate)
    val recoverHomeInteractionGates = remember {
        {
            if (latestIsTopLevelActive &&
                !latestReturnGestureInProgress &&
                (latestIsReturningFromDetail || latestDeferHomeHeavyWork)
            ) {
                com.android.purebilibili.core.util.Logger.w(
                    "HomeReturnGate",
                    "用户下拉/刷新触发门控恢复 (returning=$latestIsReturningFromDetail, defer=$latestDeferHomeHeavyWork)",
                )
                latestOnReturnConsumed()
                latestOnRecoverStaleTransitionGate()
            }
        }
    }
    val pullRecoveryNestedScroll = remember(recoverHomeInteractionGates) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput &&
                    available.y > 0f
                ) {
                    recoverHomeInteractionGates()
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    // 普通返回也先绘制保留列表。落位后再收取新列表和恢复封面请求；
    // 玻璃、MD3、Miuix 共用这个许可，滚动位置和实际曝光继续独立维护。
    val homeNavigationReady = isTopLevelActive && LocalAppNavigationEntranceReady.current.value
    val homeFeedUpdatesAllowed = homeNavigationReady &&
        !isReturningFromVideoDetail &&
        !deferHomeHeavyWork
    // Data may be needed to restore a pending scroll anchor. Only navigation freezes
    // displayed feed data; pager synchronization and anchor recovery gate work below.
    val homeFeedWorkAllowed = homeFeedUpdatesAllowed &&
        pagerCoordinator.hasSynced && returnState.pendingAnchor == null
    val foregroundSettling by com.android.purebilibili.core.lifecycle.ForegroundSettleBudget
        .isSettling
        .collectAsStateWithLifecycle()
    val homeCoverPreloadAllowed = homeFeedWorkAllowed && homeVisualWorkActive &&
        com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed.current
    var settledShowHomeUpAvatars by rememberSaveable {
        mutableStateOf(homeSettings.showHomeUpAvatars)
    }
    LaunchedEffect(
        homeSettings.showHomeUpAvatars,
        videoCardReturnGestureInProgress,
        isReturningFromVideoDetail,
        homeFeedWorkAllowed,
        useRegularHomeGrid,
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
    }.collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeFeedCardStyle.CURRENT,
            context = kotlin.coroutines.EmptyCoroutineContext)
    val topChromePolicy = rememberAppTopChromePolicy()
    val pullRefreshProfile = rememberAppPullRefreshProfile()
    val semanticVisualPolicy = rememberAppSemanticVisualPolicy()
    val pullRefreshMotionStyle = pullRefreshProfile.motionStyle
    val pullRefreshIndicatorStyle = pullRefreshProfile.indicatorStyle


    val refreshDeltaTipText = remember { mutableStateOf<String?>(null) }
    val easterEggDialogVisible = remember { mutableStateOf(false) }
    HomeRefreshFeedbackHost(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        currentCategory = currentCategory,
        isRefreshing = isRefreshing,
        homeFeedWorkAllowed = homeFeedWorkAllowed,
        useRegularHomeGrid = useRegularHomeGrid,
        gridStates = gridStates,
        easterEggEnabled = homeSettings.easterEggEnabled,
        refreshDeltaTipText = refreshDeltaTipText,
        easterEggDialogVisible = easterEggDialogVisible,
    )

    // 仅在推荐页检测“刷新后是否已下滑”，用于激活旧内容分割线
    LaunchedEffect(
        currentCategory,
        refreshNewItemsKey,
        refreshNewItemsCount,
        recommendOldContentAnchorBvid,
        recommendOldContentRevealKey,
        useRegularHomeGrid,
        homeFeedWorkAllowed,
        homeVisualWorkActive,
        homeSettings.homeRefreshTipVisible,
    ) {
        if (!homeFeedWorkAllowed || !homeVisualWorkActive) return@LaunchedEffect
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
                    recommendState.anyVisibleItem { key ->
                        (videoIndicesByKey[key] ?: -1) >= anchorIndex
                    }
                }.first { it }
                viewModel.markRecommendOldContentDividerRevealed(targetKey)
            }
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
    val baseCardAnimationEnabled = homeSettings.cardAnimationEnabled      //  卡片进场动画开关
    val baseCardTransitionEnabled = homeSettings.cardTransitionEnabled
    val baseIsDataSaverActive by remember(context) {
        com.android.purebilibili.core.store.DataSaverState.get(context).active
    }
    val homePerformanceConfig = remember(
        baseIsHeaderBlurEnabled,
        baseIsBottomBarBlurEnabled,
        homeSettings.homeTopGlassEnabled,
        homeSettings.homeSearchGlassEnabled,
        homeSettings.bottomDockGlassEnabled,
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
            topBarLiquidGlassEnabled = homeSettings.homeTopGlassEnabled,
            homeSearchLiquidGlassEnabled = homeSettings.homeSearchGlassEnabled,
            bottomBarLiquidGlassEnabled = homeSettings.bottomDockGlassEnabled,
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
        val dissolveEnabled = homeSettings.cardRemovalDissolveMode != CardRemovalDissolveMode.OFF &&
            !systemReduceMotion && isThanosEffectSupported(context)
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
    val homeChromeBlurEnabled = isLiquidGlassEnabled || isHeaderBlurEnabled ||
        isBottomBarBlurEnabled || appThemeConfig.progressiveTopBlurEnabled
    // 渐进模糊没有有效半径、系统不支持或被预算关闭时，不单独录制整个 feed。
    // 纯色渐隐无需独立的背景源。
    val progressiveTopBlurNeedsBackdrop =
        com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur(
            enabled = appThemeConfig.progressiveTopBlurEnabled && !isHeaderBlurEnabled,
            hasBackdrop = true,
            blurRadiusDp = homeLiquidGlassTuning.progressiveBlurRadius,
        ) && !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val shouldCaptureHomeChromeBackdrop = isLiquidGlassEnabled || isHeaderBlurEnabled ||
        isBottomBarBlurEnabled || progressiveTopBlurNeedsBackdrop
    val appUiStyle = com.android.purebilibili.core.theme.LocalAppUiStyle.current
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
    HomeVideoReturnEffects(
        state = returnState,
        pageOnScreen = pageOnScreen,
        isReturningFromVideoDetail = isReturningFromVideoDetail,
        returnAnimationSuppressionDurationMs = returnAnimationSuppressionDurationMs,
        cardTransitionEnabled = cardTransitionEnabled,
        isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail,
        isTabletLayout = windowSizeClass.isTablet,
        cardAnimationEnabled = cardAnimationEnabled,
        onReturnAnimationConsumed = onVideoDetailReturnAnimationConsumed,
    )

    // 列表面板按实际宽度排卡片；窗口分档仍用于决定视频是否可在右侧打开。
    val feedWidth = availableFeedWidth ?: windowSizeClass.widthDp
    val contentWidth = if (windowSizeClass.isExpandedScreen) {
        minOf(feedWidth, resolveHomeFeedMaxContentWidth())
    } else {
        feedWidth
    }

    // 是否为单列模式 (Story or Cinematic)
    val isSingleColumnMode = displayMode == 1

    val gridColumns = remember(
        contentWidth,
        displayMode,
        homeSettings.effectiveGridColumnCountForWindow(windowSizeClass),
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
                defaultColumnCount = homeSettings.effectiveGridColumnCountForWindow(windowSizeClass),
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
    val effectiveGridColumns = resolveHomeFeedPaneColumns(
        requestedColumns = gridColumnPreference.columns,
        paneWidthDp = availableFeedWidth?.value?.toInt(),
        displayMode = displayMode,
        cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
    )
    // 请求条数按列数换算（官方客户端由服务端按屏幕形态决定下发量，web 接口只能自己算）。
    LaunchedEffect(effectiveGridColumns) {
        viewModel.updateFeedGridColumns(effectiveGridColumns)
    }
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT)


    val nativeMiuixFeed = com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()
    val density = LocalDensity.current


    val tabletUseSidebar = appNavigationSettings.tabletUseSidebar

    //  📐 [大屏适配] 平板导航模式：根据用户偏好决定
    // 仅在 Expanded+ 且用户选择了侧边栏时使用侧边导航
    val useSideNavigation = com.android.purebilibili.core.util.shouldUseSidebarNavigationForLayout(
        windowSizeClass = windowSizeClass,
        tabletUseSidebar = tabletUseSidebar,
        foldPosture = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.posture,
    )
    val isHomeDrawerEnabled = homeSettings.homeAvatarDrawerEnabled && com.android.purebilibili.core.util.shouldEnableHomeDrawer(
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

    //  [修复] 动态计算内容顶部边距，防止被头部遮挡
    // 首页在沉浸播放页下仍保留状态栏占位，返回时顶栏、列表和卡片落点不随可见性跳动。
    val statusBarHeight = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val homeStartupElapsedAt = remember { SystemClock.elapsedRealtime() }

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

    //  [新增] 滚动方向检测逻辑
    LaunchedEffect(
        currentCategory,
        popularSubCategory,
        bottomBarVisibilityMode,
        useSideNavigation,
        homeFeedWorkAllowed,
        useRegularHomeGrid,
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
        // 滚动中 snapshotFlow 每帧都会重算；只有跨过采样门槛的帧才推进序号并结算，
        // 避免列表滚动全程每帧跑一次 reduce 与 Pair 分配（见 shouldEmitBottomBarScrollSample）。
        var lastEmittedState = previousScrollState
        var emittedSampleSequence = 0L
        snapshotFlow {
            val firstVisibleItem = currentGridState.firstVisibleItemIndex
            val scrollOffset = currentGridState.firstVisibleItemScrollOffset
            if (shouldEmitBottomBarScrollSample(
                    previousEmittedItem = lastEmittedState.firstVisibleItem,
                    previousEmittedOffset = lastEmittedState.scrollOffset,
                    firstVisibleItem = firstVisibleItem,
                    scrollOffset = scrollOffset,
                )
            ) {
                lastEmittedState = HomeBottomBarScrollState(firstVisibleItem, scrollOffset)
                emittedSampleSequence += 1L
            }
            emittedSampleSequence
        }
        .collect { _ ->
            val scrollUpdate = reduceHomeBottomBarListScroll(
                previousState = previousScrollState,
                firstVisibleItem = lastEmittedState.firstVisibleItem,
                scrollOffset = lastEmittedState.scrollOffset,
                isVideoNavigating = returnState.videoNavigationInProgress,
                contentInteractionRestored = returnState.contentInteractionRestored
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
    var drawerWasOpen by remember { mutableStateOf(false) }
    var drawerHeaderReturn by remember(headerReturn) { mutableStateOf<HomeHeaderReturn?>(null) }

    // 抽屉打开时隐藏全局底栏，避免覆盖侧边栏底部内容
    val isDrawerStateOpenOrOpening = shouldEnableHomeDrawerGestures(
        currentValue = drawerState.currentValue,
        targetValue = drawerState.targetValue,
    )
    val shouldKeepDrawerBottomBarHidden = drawerOpenRequested || isDrawerStateOpenOrOpening
    LaunchedEffect(drawerState.currentValue, drawerState.targetValue) {
        if (drawerState.currentValue == DrawerValue.Open) drawerWasOpen = true
        if (drawerState.currentValue == DrawerValue.Closed && drawerState.targetValue == DrawerValue.Closed) {
            drawerOpenRequested = false
            if (drawerWasOpen) {
                drawerWasOpen = false
                drawerHeaderReturn = HomeHeaderReturn(HomeHeaderControl.AVATAR)
            }
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
        // 与顶栏搜索场景使用相同测量，避免原生入口变高后内容预留与折叠距离仍用旧值。
        configuredHeight = if (appUiStyle == com.android.purebilibili.core.theme.AppUiStyle.MIUIX &&
            !homeSettings.homeSearchGlassEnabled
        ) maxOf(homeTopPresetStyle.searchBarHeight, top.yukonga.miuix.kmp.basic.SearchBarDefaults.InputFieldMinHeight)
            else homeTopPresetStyle.searchBarHeight,
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
                resolveHomeTabsToContentTighteningDp(appUiStyle, homePerformanceConfig.topBarLiquidGlassEnabled)
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
    var appliedHeaderCollapseMode by rememberSaveable {
        mutableStateOf(homeSettings.homeHeaderCollapseMode)
    }
    // 只在模式真正改变时清理偏移；切换底部栏目后重新挂载应保留已保存的折叠位置。
    LaunchedEffect(headerCollapseMode) {
        if (appliedHeaderCollapseMode != homeSettings.homeHeaderCollapseMode) {
            headerScrollState.setOffsetImmediately(0f)
            headerScrollState.tabsCollapsed = false
            appliedHeaderCollapseMode = homeSettings.homeHeaderCollapseMode
        }
    }
    val headerAutoCollapseDistancePx = if (isAnyHeaderCollapseEnabled) {
        searchCollapseDistancePx
    } else {
        0f
    }
    LaunchedEffect(pendingMemoryRecovery?.id, headerCollapseMode, headerAutoCollapseDistancePx) {
        val saved = pendingMemoryRecovery ?: return@LaunchedEffect
        headerScrollState.setOffsetImmediately(
            (saved.headerOffsetDp * density.density).coerceIn(-headerAutoCollapseDistancePx, 0f),
        )
        headerScrollState.tabsCollapsed = saved.tabsCollapsed && collapseTabsOnScroll
    }
    val embeddedPageTopPadding = rememberHomeEmbeddedPageTopPadding(
        expandedTopPadding = listTopPadding,
        statusBarHeight = statusBarHeight,
        tabRowHeight = tabRowHeightDp,
        tabsCollapsed = headerScrollState.tabsCollapsed,
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
        revealLocked = headerScrollState.revealLocked,
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
                headerScrollState.animateTo(offsetPx, coroutineScope, headerSettleMotionSpec)
            } else {
                headerScrollState.setOffsetImmediately(offsetPx)
            }
        },
        onTabsCollapsedChanged = { headerScrollState.tabsCollapsed = it },
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
                headerScrollState.setOffsetImmediately(headerOffsetProvider())
            }
    }
    // 伴随面板窗口级资格：Expanded+ 宽度、非紧凑高度、非铰链避让姿态。
    // 请求级条件（竖屏视频）在点击时逐次判定。
    val companionPaneWindowEligible by rememberUpdatedState(
        companionPaneEnabled && resolveVideoDetailPresentation(
            VideoDetailPresentationInput(
                isExpandedWindow = windowSizeClass.isExpandedScreen &&
                    windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact,
                foldPosture = appWindowAdaptiveInfo.posture,
                isVerticalVideo = false,
                requestsLandscapeFullscreen = false,
                shouldAvoidHinge = appWindowAdaptiveInfo.shouldAvoidHinge,
            )
        ) == VideoDetailPresentationMode.COMPANION_PANE
    )
    val latestOnCompanionVideoClick by rememberUpdatedState(onCompanionVideoClick)
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
            if (returnState.videoNavigationInProgress) return@homeClick
            if (request.episodeId > 0L ||
                (request.dynamicId.isNotBlank() && !request.bvid.startsWith("BV", ignoreCase = true))) {
                feedNavigation.onVideoClick(request)
                return@homeClick
            }
            // 伴随面板：Expanded+ 窗口的普通横屏视频不推栈，feed 驻留左侧；竖屏视频保持整页接管。
            if (companionPaneWindowEligible && !request.isVerticalVideo) {
                latestOnCompanionVideoClick(request)
                return@homeClick
            }
            val activeGridState = if (currentCategory == HomeCategory.POPULAR) {
                popularGridStates[popularSubCategory]
            } else {
                gridStates[currentCategory]
            }
            // Capture before changing chrome/navigation state. Shared-bounds can remeasure the
            // underlying staggered grid as soon as navigation starts, which is too late for a clean anchor.
            val anchor = activeGridState?.let { gridState ->
                captureHomeFeedScrollAnchor(
                    category = currentCategory,
                    popularSubCategory = popularSubCategory,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    headerOffsetPx = headerScrollState.offsetPx,
                    regularGrid = gridState.regularLayout,
                )
            }
            returnState.beginVideoNavigation(anchor)
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
                    title = video.title,
                    isVerticalVideo = video.resolveIsVerticalForNavigation(),
                    source = HomeVideoClickSource.TODAY_WATCH,
                    sourceRoute = resolveHomeCategoryVideoSourceRoute(HomeCategory.RECOMMEND)
                )
            )
        }
    }

    HomeTodayWatchStartupEffect(
        viewModel = viewModel,
        currentCategory = currentCategory,
        homeFeedWorkAllowed = homeFeedWorkAllowed,
        useRegularHomeGrid = useRegularHomeGrid,
        gridStates = gridStates,
        homeStartupElapsedAt = homeStartupElapsedAt,
        onResetHeader = { headerScrollState.setOffsetImmediately(0f) },
        onScrollReset = { globalScrollOffset.floatValue = 0f },
    )

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
            maskProgressProvider = videoCardTransitionBackgroundState.maskProgressProvider,
            phaseProvider = videoCardTransitionBackgroundState.phaseProvider,
            exposureProvider = videoCardTransitionBackgroundState.exposureProvider,
            isGestureRestoreInProgressProvider =
                videoCardTransitionBackgroundState.isGestureRestoreInProgressProvider,
            motionTierProvider = videoCardTransitionBackgroundState.motionTierProvider,
            isLightBackgroundProvider = videoCardTransitionBackgroundState.isLightBackgroundProvider,
            realtimeBlurEnabledProvider = {
                shouldUseRealtimeVideoCardTransitionBackgroundBlur(
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
    val videoCardClock = LocalVideoCardTransitionClock.current
    val videoCardSettleState = videoCardClock?.settleState
    val homeHeaderChromeVisible = !subscriptionArticleOpen && (
        homeFeedOwnsVideoCardSnapshot ||
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
                exposure = resolveVideoCardTransitionExposure(
                    phase = videoCardClock?.phase ?: VideoCardTransitionBackgroundPhase.IDLE,
                    predictiveBackInProgress =
                        videoCardSettleState == VideoCardTransitionSettleState.InteractiveSeek,
                    gestureRestoreInProgress =
                        videoCardSettleState == VideoCardTransitionSettleState.CancelRestore ||
                            videoCardClock?.gestureRestoreInProgress == true,
                ),
            )
        )
    val appearHomeHeaderFromHidden = homeHeaderChromeVisible && (
        videoCardSettleState == VideoCardTransitionSettleState.InteractiveSeek ||
            videoCardClock?.phase == VideoCardTransitionBackgroundPhase.RETURNING
        )
    val homeHeaderVisibilityState = remember(isHomeDrawerEnabled) {
        MutableTransitionState(!appearHomeHeaderFromHidden)
    }
    homeHeaderVisibilityState.targetState = homeHeaderChromeVisible

    val headerMounted = homeHeaderVisibilityState.currentState || homeHeaderVisibilityState.targetState ||
        !homeHeaderVisibilityState.isIdle
    val linkedTopBlur = remember(effectiveHomeSettings, topChromePolicy.tabPresentation) {
        resolveHomeTopLinkedBottomBarAppearance(effectiveHomeSettings, topChromePolicy.tabPresentation).blurEnabled
    }
    val headerNeedsBackdrop = headerMounted && (
        homePerformanceConfig.topBarLiquidGlassEnabled || homePerformanceConfig.homeSearchLiquidGlassEnabled ||
            isHeaderBlurEnabled || linkedTopBlur || progressiveTopBlurNeedsBackdrop
        )
    val drawerMounted = isHomeDrawerEnabled && shouldKeepDrawerBottomBarHidden
    val previewNeedsBackdrop = targetVideoItemState.value != null && homeChromeBlurEnabled &&
        shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    val homeChromeRecordingAllowed = LocalChromeBackdropRecordingAllowed.current
    val recordHomeChromeBackdrop = shouldCaptureHomeChromeBackdrop && homeChromeRecordingAllowed && (
        headerNeedsBackdrop || (drawerMounted && isLiquidGlassEnabled) || previewNeedsBackdrop
        )
    val rasterizeHomeChromeBackdrop by remember(
        isLiquidGlassEnabled, cardTransitionEnabled, systemReduceMotion,
        recordHomeChromeBackdrop, videoCardTransitionBackgroundState,
    ) {
        derivedStateOf {
            com.android.purebilibili.core.ui.transition.shouldRasterizeVideoCardChromeBackdrop(
                liquidGlassEnabled = isLiquidGlassEnabled,
                cardTransitionEnabled = cardTransitionEnabled,
                reduceMotion = systemReduceMotion,
                recordingAllowed = recordHomeChromeBackdrop,
                exposure = videoCardTransitionBackgroundState.exposureProvider(),
            )
        }
    }
    val homeMiuixBackdropSource = if (shouldCaptureHomeChromeBackdrop) {
        // 顶栏只采样变换前的 feed，外层导航源还包含顶栏本身。停录时保留源身份。
        rememberChromeBackdropSource(
            isolateContentDrawing = true,
            recordingEnabled = recordHomeChromeBackdrop,
            rasterizeContent = rasterizeHomeChromeBackdrop,
        )
    } else null
    val chromeCategoryStateFlow = remember(viewModel, currentCategory, popularSubCategory) {
        if (currentCategory == HomeCategory.POPULAR) {
            viewModel.getPopularCategoryState(popularSubCategory)
        } else {
            viewModel.getCategoryState(currentCategory)
        }
    }
    val chromeContentReady by chromeCategoryStateFlow.collectHomeChromeContentReady(
        homeFeedUpdatesAllowed && recordHomeChromeBackdrop,
    )
    val readyHomeMiuixBackdrop = homeMiuixBackdropSource?.takeIf {
        chromeContentReady && it.isReady
    }?.backdrop

    val shouldCaptureHomeHaze = shouldCaptureHomeChromeBackdrop &&
        com.android.purebilibili.feature.home.components.shouldUseOfficialMd3HomeTopToolbar(
            uiStyle = appUiStyle,
            liquidGlassEnabled = isLiquidGlassEnabled,
        ) && (!appThemeConfig.progressiveTopBlurEnabled || isHeaderBlurEnabled)
    val hazeState = if (shouldCaptureHomeHaze &&
        shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT) &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    ) {
        rememberRecoverableHazeState(initialBlurEnabled = true)
    } else null
    val headerUsesHomeHaze =
        topChromeMaterialMode != com.android.purebilibili.feature.home.components.TopTabMaterialMode.PLAIN &&
            !appThemeConfig.progressiveTopBlurEnabled
    val recordHomeHaze = homeChromeRecordingAllowed && (
        (headerMounted && headerUsesHomeHaze) || (drawerMounted && isHeaderBlurEnabled) || previewNeedsBackdrop
        )
    val readyHomeHaze = hazeState?.takeIf { recordHomeHaze && recoverableBlurEnabled(it) }
    val homeFeedUnobscured = targetVideoItemState.value == null && pendingVideoShare.value == null &&
        downloadSelection.request == null &&
        !easterEggDialogVisible.value && !(isHomeDrawerEnabled && shouldKeepDrawerBottomBarHidden)
    val scaffoldLayout: @Composable () -> Unit = {
        // Composite header and feed before applying depth; separate blur layers form a seam.
        Box(modifier = Modifier.fillMaxSize().then(homeFeedSnapshotModifier)) {
        AppScaffold(
                // 顶栏在 Scaffold 外，其采样由首页提供。
                contentBackdropEnabled = false,
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
                        // Only the underlying feed is suspended. Foreground preview/share UI
                        // keeps the navigation owner's permission and existing material paths.
                        com.android.purebilibili.core.ui.LocalVisualWorkAllowed provides (
                            com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current &&
                                homeFeedUnobscured
                        ),
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
                                if (readyHomeHaze != null) {
                                    Modifier.hazeSourceCompat(state = readyHomeHaze)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                    HomeWallpaperBackdrop(
                        wallpaperUri = homeWallpaperUri,
                        playbackEnabled = isTopLevelActive && homeFeedUnobscured && homeVisualWorkActive,
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
                                nativeMiuix = nativeMiuixFeed,
                            )
                            val gridContentWidthDp = maxWidth.value - homeFeedCardLayout.outerPaddingDp * 2f
                            val gridGapsDp = homeFeedCardLayout.itemSpacingDp * (effectiveGridColumns - 1)
                            val cardWidthDp = ((gridContentWidthDp - gridGapsDp) / effectiveGridColumns)
                                .coerceAtLeast(0f)
                            val useLowQualityCover =
                                isDataSaverActive && homeSettings.lowQualityHomeCoverInDataSaver
                            val homeCoverRequestSpec = remember(cardWidthDp, density.density, useLowQualityCover) {
                                resolveHomeCoverRequestSpec(
                                    cardWidthDp = cardWidthDp,
                                    density = density.density,
                                    useLowQualityCover = useLowQualityCover,
                                )
                            }
                            val homeFeedCoverAspectRatio = homeFeedCardLayout.coverAspectRatio
                            val pinchColumnBounds = resolveHomeFeedPinchColumnBounds(
                                widthSizeClass = windowSizeClass.widthSizeClass,
                                contentWidthDp = maxWidth.value.toInt(),
                                displayMode = displayMode,
                            )
                        RegisterHomeMemoryCapture {
                            val entry = topTabEntries.getOrNull(pagerState.settledPage) as? HomeTopTabEntry.Category
                            val category = entry?.category
                            if (category == null || !supportsHomeFeedMemoryRecovery(category)) null
                            else {
                                val subCategory = viewModel.uiState.value.popularSubCategory
                                val grid = if (category == HomeCategory.POPULAR) popularGridStates.getValue(subCategory)
                                    else gridStates.getValue(category)
                                viewModel.captureMemorySnapshot(
                                    category, subCategory, grid.captureMemoryAnchor(density, effectiveGridColumns),
                                    headerOffsetProvider() / density.density, headerScrollState.tabsCollapsed,
                                )
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 0,
                            userScrollEnabled = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalPriorityHorizontalPagerSwipe(
                                    state = pagerState,
                                    enabled = homeTopPagerSwipeEnabled,
                                    horizontalLockSlopMultiplier = HOME_PAGER_HORIZONTAL_LOCK_SLOP_MULTIPLIER,
                                    shouldYield = shouldYieldHomePagerToHeroCarousel,
                                ),
                            key = { index -> resolveHomeTopTabEntryKey(topTabEntries, index) }
                        ) { page ->
                        val pageWork = resolveHomePagerPageWork(
                            visible = rememberHomePagerPageVisible(pagerState, page),
                            currentPage = pagerState.currentPage == page,
                            pagerScrolling = pagerState.isScrollInProgress,
                            updatesAllowed = homeFeedUpdatesAllowed,
                            workAllowed = homeFeedWorkAllowed,
                            visualWorkActive = homeVisualWorkActive,
                            unobscured = homeFeedUnobscured,
                        )
                        CompositionLocalProvider(
                            // 横滑曝光仅允许显示内容；预加载、遮挡和未落位页不启动装饰任务。
                            com.android.purebilibili.core.ui.LocalVisualWorkAllowed provides (
                                com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current &&
                                    pageWork.runActiveWork
                            ),
                            LocalChromeBackdropRecordingAllowed provides
                                rememberPagerChromeBackdropRecordingAllowed(pagerState, page),
                        ) {
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
                                        sideRailTopPaddingProvider = { embeddedPageTopPadding.value },
                                        rightGlassRailBottomPadding = if (isBottomBarFloating) {
                                            WindowInsets.navigationBarsIgnoringVisibility
                                                .asPaddingValues().calculateBottomPadding() + AppSpacingTokens.Small
                                        } else homeListBottomPadding,
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
                                    pageActive = pageWork.runActiveWork,
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
                                    isCurrentPage = pageWork.runActiveWork,
                                    contentTopPadding = topPadding,
                                    scrollToTopRequestId = liveScrollToTopRequestId,
                                )
                            }
                        } else if (shouldEmbedBangumiPageInHomeTopTab(category)) {
                            HomeEmbeddedPageContent(embeddedPageTopPadding) { topPadding ->
                                HomeBangumiTabPage(
                                    pageActive = pageWork.runActiveWork,
                                    contentPadding = PaddingValues(
                                        top = topPadding,
                                        bottom = homeListBottomPadding
                                    ),
                                    onBangumiClick = onBangumiSeasonClick,
                                    onBangumiEpisodeClick = onBangumiEpisodeClick,
                                    onOpenFavoritePgc = onOpenFavoritePgc,
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
                        val categoryState by categoryStateFlow.collectAsHomeFeedState(pageWork.collectUpdates)

                        //  独立的 PullToRefreshState，避免所有页面共享一个状态导致冲突
                        val pullRefreshState = rememberPullToRefreshState()
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
                        val animatedDragOffsetFraction = rememberHomePullContentOffset(
                            state = pullRefreshState,
                            isRefreshing = isPageRefreshing,
                            motionStyle = pullRefreshMotionStyle,
                            indicatorStyle = pullRefreshIndicatorStyle,
                            reduceMotion = systemReduceMotion ||
                                !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.uiEntranceAnimationEnabled,
                            active = pageWork.visible &&
                                ((pageWork.runActiveWork && homeVisualWorkActive) ||
                                    pullRefreshState.distanceFraction > 0f ||
                                    isPageRefreshing),
                        )
                        LaunchedEffect(pullRefreshState, isTopLevelActive) {
                            if (!isTopLevelActive) return@LaunchedEffect
                            snapshotFlow { pullRefreshState.distanceFraction > 0f }
                                .distinctUntilChanged()
                                .collect { pulling ->
                                    if (pulling) recoverHomeInteractionGates()
                                }
                        }

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
                            popularGridStates.getValue(popularSubCategory)
                        } else {
                            gridStates.getValue(category)
                        }

                        //  把 GridState 提升给父级用于控制 Header?

                        // Overlay chrome (status + search + tabs); not for Scaffold-padded screens.
                        // 指示器 inset 跟随可见顶栏（含收起偏移与 tab 收起动画），内容间距保持不变。
                        HomeFeedPullToRefreshBox(
                            isRefreshing = isRefreshing && currentCategory == category,
                            onRefresh = {
                                recoverHomeInteractionGates()
                                viewModel.refresh(category)
                            },
                            state = pullRefreshState,
                            indicatorTopInset = embeddedPageTopPadding,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(pullRecoveryNestedScroll),
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
                                        HomeScreenshotRefreshIndicator(
                                            state = pullRefreshState,
                                            isRefreshing = isPageRefreshing,
                                            topInset = homeRefreshIndicatorTopInset,
                                            calculateDragOffset = calculateDragOffset,
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
                             val skeletonAnimationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
                                 respectComponentMotion = false,
                             )
                             // [物理优化] 内容容器应用下沉效果
                              Box(
                                  modifier = Modifier
                                      .fillMaxSize()
                                      .then(skeletonAnimationGate.modifier)
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
                                  rememberHomeFeedSkeletonPulseState(animated = skeletonAnimationGate.active)
                              } else {
                                  remember { mutableStateOf(0f) }
                              }
                              // remember 住骨架 provider：它的身份稳定，内容网格才不会因为
                              // 每次重组换新 lambda 而反复重组。
                              val initialSkeletonContent: (HomeFeedGridScope.() -> Unit)? = remember(
                                  showInitialSkeleton,
                                  category,
                                  homeSettings.homeHeroCarouselEnabled,
                                  effectiveGridColumns,
                                  homeWallpaperBackdropAppearance.visible,
                                  homeSettings.homeWallpaperEffectMode,
                                  isDataSaverActive,
                                  homeFeedCoverAspectRatio,
                                  homeFeedCardLayout,
                                  useRegularHomeGrid,
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
                                              fullLine = true
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
                                              modifier = animateItemModifier(),
                                              pulse = { skeletonPulseState.value },
                                              wallpaperTintEnabled = homeWallpaperBackdropAppearance.visible,
                                              wallpaperEffectMode = homeSettings.homeWallpaperEffectMode,
                                              isDataSaverActive = isDataSaverActive,
                                              coverAspectRatio = homeFeedCoverAspectRatio,
                                              compactMetadata = homeFeedCardLayout.compactMetadata,
                                              compactStatsOnCover = homeSettings.compactVideoStatsOnCover || homeFeedCardLayout.compactStatsOnCover,
                                              titleMinLines = if (useRegularHomeGrid) homeFeedCardLayout.titleMaxLines else homeFeedCardLayout.titleMinLines,
                                              showPublishTime = homeSettings.showHomePublishTime,
                                              compactCornerFooter = displayMode != 1,
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
                                     HomeFeedGridState,
                                     PopularSubCategory,
                                     () -> Unit
                                 ) -> Unit = { pageCategoryState, contentGridState, selectedPopularSubCategory, onPageLoadMore ->
                                 val pageShowsHeroCarousel = shouldShowHomeHeroCarousel(
                                     enabled = homeSettings.homeHeroCarouselEnabled,
                                     category = category,
                                     itemCount = pageCategoryState.videos.size
                                 )
                                 val pageContentPadding = PaddingValues(
                                     bottom = homeListBottomPadding + if (category == currentCategory && downloadSelection.active) 64.dp else 0.dp,
                                     start = homeFeedCardLayout.outerPaddingDp.dp,
                                     end = homeFeedCardLayout.outerPaddingDp.dp,
                                     top = resolveHomeFeedTopPaddingDp(
                                         reservedTopPaddingDp = listTopPadding.value,
                                         showHeroCarousel = pageShowsHeroCarousel
                                     ).dp
                                 )
                                 HomeTodayWatchContentHost(viewModel, category, pageWork.collectUpdates) { todayWatch ->
                                     HomeCategoryPageContent(
                                         category = category,
                                         categoryState = pageCategoryState,
                                         memoryRecovery = pendingMemoryRecovery,
                                         onMemoryRecoveryConsumed = { id, located ->
                                             if (!located) {
                                                 headerScrollState.setOffsetImmediately(0f)
                                                 headerScrollState.tabsCollapsed = false
                                             }
                                             viewModel.consumeMemoryRecovery(id)
                                         },
                                         isActive = pageWork.runActiveWork,
                                         pageOnScreen = pageOnScreen && pageWork.visible,
                                         coverRequestsAllowed = homeNavigationReady,
                                         visibleDuringNavigation = visibleDuringNavigation && pageWork.visible &&
                                             homeFeedWorkAllowed,
                                         gridState = contentGridState,
                                         pageScrollInProgress = remember(pagerState) {
                                             derivedStateOf { pagerState.isScrollInProgress }
                                         },
                                         gridColumns = effectiveGridColumns,
                                         cardLayout = homeFeedCardLayout,
                                         cardWidthDp = cardWidthDp,
                                         contentPadding = pageContentPadding,
                                         dissolvingVideos = dissolvingVideos,
                                         followingMids = followingMids,
                                         showOnlineCount = showOnlineCount,
                                         coverRequestSpec = homeCoverRequestSpec,
                                         coverPreloadAllowed = pageWork.runActiveWork && homeCoverPreloadAllowed && !foregroundSettling,
                                         coverPreloadAheadCount = preloadAheadCount,
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
                                         dissolvePreset = homeSettings.cardRemovalDissolveMode.animationPreset,
                                         dissolveReflowEnabled = !systemReduceMotion,
                                         longPressCallback = onLongPressCallback,
                                         downloadSelection = downloadSelection.takeIf { category == currentCategory },
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
                                                 contentGridState.animateScrollToTop()
                                             }
                                             viewModel.refresh(category)
                                         },
                                         todayWatchEnabled = todayWatch.enabled,
                                         todayWatchMode = todayWatch.mode,
                                         todayWatchPlan = todayWatch.plan,
                                         todayWatchLoading = todayWatch.loading,
                                         todayWatchError = todayWatch.error,
                                         todayWatchCollapsed = todayWatch.collapsed,
                                         todayWatchCardConfig = todayWatch.cardConfig,
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
                                 }
                                 if (category == HomeCategory.POPULAR) {
                                     val subCategoryGridState =
                                         popularGridStates.getValue(popularSubCategory)
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
                        } // Close page backdrop exposure provider
                } // Close HorizontalPager lambda
                        } // Close AppHingeSafeContent
            } // Close Box wrapper
                    } // Close wallpaper and feed work provider
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
            isProgrammaticPageSwitchInProgress = pagerCoordinator.isSwitching,
            isFeedScrolling = isFeedScrollInProgress
        )
        val isHeaderTransitionRunning by remember(pagerState, headerScrollState, isFeedScrollInProgress) {
            derivedStateOf {
                resolveHomeHeaderTransitionRunning(
                    isFeedScrolling = isFeedScrollInProgress,
                    isPagerScrolling = pagerState.isScrollInProgress,
                    isHeaderSettleAnimating = headerScrollState.isSettling
                )
            }
        }
        TrackJankStateFlag(
            stateName = "home:header_transition",
            isActive = isHeaderTransitionRunning
        )
        val forceLowBlurBudget = false
        // Calculate parameters based on scroll
        val topTabsCollapsedForHeader = if (collapseTabsOnScroll) {
            headerScrollState.tabsCollapsed
        } else {
            false
        }
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
            HomeUnreadBadgeUpdates(viewModel.messageUnreadCount, messageBadgeMode) { messageUnreadDisplay ->
            val avatarTarget = when (resolveHomeAvatarAction(user.isLogin, isHomeDrawerEnabled)) {
                HomeAvatarAction.OPEN_DRAWER -> null
                HomeAvatarAction.OPEN_PROFILE -> com.android.purebilibili.navigation3.BiliPaiNavKey.Profile
                HomeAvatarAction.OPEN_LOGIN -> com.android.purebilibili.navigation3.BiliPaiNavKey.Login
            }
            val searchEntry = resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage)
            val searchTarget = if (searchEntry is HomeTopTabEntry.Category && shouldEmbedLivePageInHomeTopTab(searchEntry.category)) {
                com.android.purebilibili.navigation3.BiliPaiNavKey.LiveSearch
            } else {
                com.android.purebilibili.navigation3.BiliPaiNavKey.Search()
            }
            HomeHeader(
            headerOffsetProvider = headerOffsetProvider,
            isHeaderCollapseEnabled = collapseSearchOnScroll,
            isTopTabsAutoCollapseEnabled = collapseTabsOnScroll,
            isTopTabsManualCollapseEnabled = false,
            user = user,
            avatarTarget = avatarTarget,
            searchTarget = searchTarget,
            onOpenDrawer = openHomeDrawer,
            onNavigate = onHeaderNavigate,
            headerReturn = drawerHeaderReturn ?: headerReturn,
            headerActive = isTopLevelActive && pageOnScreen && !drawerMounted,
            topRightUnreadCount = messageUnreadDisplay,
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
            hazeState = readyHomeHaze.takeIf { headerUsesHomeHaze },
            isRefreshing = isRefreshing,
            pullProgress = 0f, // [Fix] Outer header doesn't track inner pull state
            pagerState = pagerState,
            miuixBackdrop = readyHomeMiuixBackdrop,
            homeSettings = effectiveHomeSettings,
            topTabsVisible = !effectiveHomeSettings.hideTopTabs,
            topTabsCollapsed = topTabsCollapsedForHeader,
            returnTabRevealFraction = headerScrollState.returnTabRevealFraction,
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
        }

        HomeRefreshDeltaTip(
            text = refreshDeltaTipText,
            listTopPadding = listTopPadding,
            isLiquidGlassEnabled = isLiquidGlassEnabled,
            isHeaderBlurEnabled = isHeaderBlurEnabled,
            isBottomBarBlurEnabled = isBottomBarBlurEnabled,
        )

        // [新增] 双指缩放切换网格列数 HUD 胶囊 (自适应 MD3 / MIUIX)
        GridPinchColumnHudPill(
            visible = isPinchPillVisible,
            columns = effectiveGridColumns,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = listTopPadding + AppSpacingTokens.Small)
        )

        if (homeSettings.homeRefreshUndoVisible) {
            HomeRefreshUndoHost(
                viewModel = viewModel,
                currentCategory = currentCategory,
                refreshNewItemsKey = refreshNewItemsKey,
                homeListBottomPadding = homeListBottomPadding,
                oldContentLocatorVisible = shouldShowRecommendOldContentDivider(
                    currentCategory = currentCategory,
                    refreshNewItemsKey = refreshNewItemsKey,
                    revealedRefreshKey = recommendOldContentRevealKey,
                    anchorBvid = recommendOldContentAnchorBvid,
                    oldContentStartIndex = recommendOldContentStartIndex,
                    refreshTipVisible = homeSettings.homeRefreshTipVisible,
                ),
            )
        }

        androidx.compose.runtime.key(downloadSelection) {
            HomeBatchDownloadHost(
                selection = downloadSelection,
                pageActive = isTopLevelActive,
                onOpenDownloads = onDownloadClick,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = homeListBottomPadding),
            )
        }

        HomeVideoPreviewHost(
            viewModel = viewModel,
            downloadSelection = downloadSelection,
            targetVideoItemState = targetVideoItemState,
            pendingVideoShare = pendingVideoShare,
            currentCategory = currentCategory,
            onVideoClick = wrappedOnVideoClick,
            onDismissVideoCallback = onDismissVideoCallback,
            hazeState = readyHomeHaze,
            miuixBackdrop = readyHomeMiuixBackdrop,
            backgroundBlurEnabled = homeChromeBlurEnabled,
        )
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
                        hazeState = readyHomeHaze,
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


    // 首页实际活跃时刷新角标，沿用账号请求归属与在途请求合并。
    val unreadBadgeLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(unreadBadgeLifecycleOwner, viewModel, isTopLevelActive, pageOnScreen) {
        if (!isTopLevelActive || !pageOnScreen) return@LaunchedEffect
        unreadBadgeLifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.refreshMessageUnread()
        }
    }

    HomeReturnLifecycleEffect(
        state = returnState,
        useSideNavigation = useSideNavigation,
        bottomBarVisible = bottomBarVisible,
        onBottomBarVisibleChange = setBottomBarVisible,
    )

    //  滚动方向（简化版 - 不再需要复杂检测，因为标签页只在顶部显示）
    val isScrollingUp = true  // 保留参数兼容性

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
