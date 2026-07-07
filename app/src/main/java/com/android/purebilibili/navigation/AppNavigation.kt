// 文件路径: navigation/AppNavigation.kt
package com.android.purebilibili.navigation

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue //  新增
import androidx.compose.runtime.LaunchedEffect // 新增
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.article.ArticleDetailScreen
import com.android.purebilibili.feature.article.shouldUseArticleNoOpRouteTransition
import com.android.purebilibili.feature.home.HomeVideoClickRequest
import com.android.purebilibili.feature.home.HomeVideoClickSource
import com.android.purebilibili.feature.home.HomeScreen
import com.android.purebilibili.feature.home.HomeViewModel
import com.android.purebilibili.feature.home.HomeWallpaperBackdrop
import com.android.purebilibili.feature.home.resolveHomeWallpaperBackdropAppearance
import com.android.purebilibili.feature.home.resolveHomeWallpaperUri
import com.android.purebilibili.feature.login.LoginScreen
import com.android.purebilibili.feature.profile.ProfileScreen
import com.android.purebilibili.feature.search.ArticleNavigationTarget
import com.android.purebilibili.feature.search.resolveArticleNavigationTarget
import com.android.purebilibili.feature.search.SearchEntryMotionSource
import com.android.purebilibili.feature.search.SearchScreen
import com.android.purebilibili.feature.settings.SettingsScreen
import com.android.purebilibili.feature.settings.resolveSettingsSearchNavigation
import com.android.purebilibili.feature.settings.screen.SettingsCategoryScreen
import com.android.purebilibili.feature.settings.screen.SettingsSearchScreen
import com.android.purebilibili.feature.settings.screen.SettingsTabletNavEntryShell
import com.android.purebilibili.feature.settings.AppearanceSettingsScreen
import com.android.purebilibili.feature.settings.PlaybackSettingsScreen
import com.android.purebilibili.feature.settings.SettingsViewModel
import com.android.purebilibili.feature.settings.SettingsViewModelFactory
import com.android.purebilibili.feature.settings.share.SettingsShareViewModel
import com.android.purebilibili.feature.settings.share.SettingsShareViewModelFactory
import com.android.purebilibili.feature.settings.webdav.WebDavBackupViewModel
import com.android.purebilibili.feature.settings.webdav.WebDavBackupViewModelFactory
import com.android.purebilibili.feature.settings.OFFICIAL_GITHUB_URL
import com.android.purebilibili.feature.settings.OFFICIAL_TELEGRAM_URL
import com.android.purebilibili.feature.settings.RELEASE_DISCLAIMER_ACK_KEY
import com.android.purebilibili.feature.settings.ReleaseChannelDisclaimerDialog
import com.android.purebilibili.feature.list.CommonListScreen
import com.android.purebilibili.feature.list.HistoryViewModel
import com.android.purebilibili.feature.list.LikedVideosViewModel
import com.android.purebilibili.feature.list.FavoriteViewModel
import com.android.purebilibili.feature.list.FavoriteCollectionRoute
import com.android.purebilibili.feature.list.HistoryNavigationKind
import com.android.purebilibili.feature.list.resolveHistoryNavigationKind
import com.android.purebilibili.feature.list.resolveHistoryPlaybackCid
import com.android.purebilibili.feature.list.resolveHistoryResumePositionMs
import com.android.purebilibili.feature.video.screen.VideoDetailScreen
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.dynamic.DynamicScreen
import com.android.purebilibili.feature.dynamic.LocalDynamicScrollChannel
import com.android.purebilibili.feature.dynamic.components.ImagePreviewOverlayHost
import com.android.purebilibili.feature.live.shouldStopLivePlaybackOnRouteDispose
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.resolveShortcutRoute
import com.android.purebilibili.shouldNavigateToVideoFromNotification
import com.android.purebilibili.core.ui.ProvideAnimatedVisibilityScope
import com.android.purebilibili.core.ui.SharedTransitionProvider
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.transition.LocalPredictiveBackBackgroundState
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.predictiveBackBackgroundEffect
import com.android.purebilibili.core.ui.transition.shouldApplyPredictiveBackBlurToRoute
import com.android.purebilibili.core.ui.transition.shouldApplyVideoCardTransitionBackgroundToRoute
import com.android.purebilibili.core.ui.transition.videoCardTransitionBackgroundEffect
import com.android.purebilibili.data.model.response.BgmInfo

import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.CompositionLocalProvider
// [LayerBackdrop] miuix-blur 用于全局底栏真实背景折射。
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import com.android.purebilibili.core.ui.LocalSetBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween
import com.android.purebilibili.core.ui.motion.softLandingSpring
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.shouldUseSidebarNavigationForLayout
import com.android.purebilibili.core.plugin.skin.rememberUiSkinState
// import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass (Removed)
// import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass (Removed)
// import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass (Removed)
// import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi (Removed)
import com.android.purebilibili.feature.home.components.FrostedBottomBar
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.rememberBottomBarUiSkinDecoration
import com.android.purebilibili.feature.profile.shouldShowProfileHistoryService
import com.android.purebilibili.core.store.AppNavigationSettings
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.resolveEffectiveHomeSettings
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.navigation3.BiliPaiNavDisplayHost
import com.android.purebilibili.navigation3.BiliPaiNavCardSourceDirection
import com.android.purebilibili.navigation3.BiliPaiNavEntryContentRole
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiReturnSessionState
import com.android.purebilibili.navigation3.legacyRouteToBiliPaiNavKey
import com.android.purebilibili.navigation3.popBiliPaiNavKey
import com.android.purebilibili.navigation3.popBiliPaiNavKeyToRoot
import com.android.purebilibili.navigation3.pushBiliPaiNavKey
import com.android.purebilibili.navigation3.resolveBiliPaiBackGestureDecision
import com.android.purebilibili.navigation3.resolveBiliPaiNavCardSourceDirection
import com.android.purebilibili.navigation3.resolveBiliPaiNavEntryContentRole
import com.android.purebilibili.navigation3.resolveBiliPaiNavSourceMetadata
import com.android.purebilibili.navigation3.resolveBiliPaiVideoSource
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.resolveInitialBiliPaiBackStack
import com.android.purebilibili.navigation3.toLegacyRoute
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier // 确保 Modifier 被导入
import androidx.compose.foundation.layout.Box // 确保 Box 被导入
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize // 确保 fillMaxSize 被导入
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import com.android.purebilibili.feature.home.components.FrostedSideBar
import com.android.purebilibili.feature.privacy.PrivacyAuthenticationReason
import com.android.purebilibili.feature.privacy.PrivacyAuthenticationRequest
import com.android.purebilibili.feature.privacy.PrivacyAuthenticationResult
import com.android.purebilibili.feature.privacy.PrivacyNavigationTarget
import com.android.purebilibili.feature.privacy.shouldRequirePrivacyAuthentication
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// 定义路由参数结构
object VideoRoute {
    const val base = "video"
    const val route = "$base/{bvid}?cid={cid}&cover={cover}&startAudio={startAudio}&autoPortrait={autoPortrait}&fullscreen={fullscreen}&resumePositionMs={resumePositionMs}&commentRootRpid={commentRootRpid}&commentTargetRpid={commentTargetRpid}&initialVertical={initialVertical}"

    internal fun resolveVideoRoutePath(
        bvid: String,
        cid: Long,
        encodedCover: String,
        startAudio: Boolean,
        autoPortrait: Boolean,
        fullscreen: Boolean = false,
        resumePositionMs: Long = 0L,
        commentRootRpid: Long = 0L,
        commentTargetRpid: Long = 0L,
        initialVertical: Boolean = false
    ): String {
        val initialVerticalQuery = if (initialVertical) "&initialVertical=true" else ""
        return "$base/$bvid?cid=$cid&cover=$encodedCover&startAudio=$startAudio&autoPortrait=$autoPortrait&fullscreen=$fullscreen&resumePositionMs=${resumePositionMs.coerceAtLeast(0L)}&commentRootRpid=${commentRootRpid.coerceAtLeast(0L)}&commentTargetRpid=${commentTargetRpid.coerceAtLeast(0L)}$initialVerticalQuery"
    }

    // 构建 helper
    fun createRoute(
        bvid: String,
        cid: Long,
        coverUrl: String,
        startAudio: Boolean = false,
        autoPortrait: Boolean = false,
        fullscreen: Boolean = false,
        resumePositionMs: Long = 0L,
        commentRootRpid: Long = 0L,
        commentTargetRpid: Long = 0L,
        initialVertical: Boolean = false
    ): String {
        val encodedCover = Uri.encode(coverUrl)
        return resolveVideoRoutePath(
            bvid = bvid,
            cid = cid,
            encodedCover = encodedCover,
            startAudio = startAudio,
            autoPortrait = autoPortrait,
            fullscreen = fullscreen,
            resumePositionMs = resumePositionMs,
            commentRootRpid = commentRootRpid,
            commentTargetRpid = commentTargetRpid,
            initialVertical = initialVertical
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun shouldAutoEnterPortraitForStandardVideoNavigation(): Boolean = false

internal fun resolveStandardVideoRoute(
    bvid: String,
    cid: Long,
    coverUrl: String,
    startAudio: Boolean = false,
    autoPortrait: Boolean = shouldAutoEnterPortraitForStandardVideoNavigation(),
    fullscreen: Boolean = false,
    resumePositionMs: Long = 0L,
    commentRootRpid: Long = 0L,
    commentTargetRpid: Long = 0L,
    initialVertical: Boolean = false
): String {
    val encodedCover = URLEncoder.encode(coverUrl, StandardCharsets.UTF_8.toString())
    return VideoRoute.resolveVideoRoutePath(
        bvid = bvid,
        cid = cid,
        encodedCover = encodedCover,
        startAudio = startAudio,
        autoPortrait = autoPortrait,
        fullscreen = fullscreen,
        resumePositionMs = resumePositionMs,
        commentRootRpid = commentRootRpid,
        commentTargetRpid = commentTargetRpid,
        initialVertical = initialVertical
    )
}

private fun BiliPaiNavKey.toPrivacyNavigationTarget(): PrivacyNavigationTarget {
    return when (this) {
        is BiliPaiNavKey.SeasonSeriesDetail -> PrivacyNavigationTarget(
            routeBase = routeBase,
            seasonSeriesType = type
        )
        is BiliPaiNavKey.Unknown -> PrivacyNavigationTarget(
            routeBase = route.substringBefore("?")
        )
        else -> PrivacyNavigationTarget(routeBase = routeBase)
    }
}

@androidx.media3.common.util.UnstableApi
// @OptIn(ExperimentalMaterial3WindowSizeClassApi::class) (Removed)
@Composable
fun AppNavigation(
    //  小窗管理器
    miniPlayerManager: MiniPlayerManager? = null,
    //  PiP 支持参数
    //  PiP 支持参数
    isInPipMode: Boolean = false,
    pendingVideoId: String? = null,
    pendingShortcutRoute: String? = null,
    pendingNavigationRoute: String? = null,
    onPendingVideoIdConsumed: (String) -> Unit = {},
    onPendingShortcutRouteConsumed: (String) -> Unit = {},
    onPendingNavigationRouteConsumed: (String) -> Unit = {},
    initialSearchKeyword: String? = null,
    onInitialSearchKeywordConsumed: (String) -> Unit = {},
    onVideoDetailEnter: () -> Unit = {},
    onVideoDetailExit: () -> Unit = {},
    onAudioModeEnter: () -> Unit = {},
    onAudioModeExit: () -> Unit = {},
    onPrivacyAuthenticationRequired: (
        PrivacyAuthenticationRequest,
        (PrivacyAuthenticationResult) -> Unit
    ) -> Unit = { _, onResult ->
        onResult(PrivacyAuthenticationResult.Failure("请先设置系统锁屏后再解锁隐私内容"))
    },
    mainHazeState: dev.chrisbanes.haze.HazeState? = null //  全局 Haze 状态
) {
    val homeViewModel: HomeViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()
    
    // 单一首页视觉配置源：减少根导航层多路 DataStore 收集导致的全局重组。
    val context = androidx.compose.ui.platform.LocalContext.current
    val application = remember(context) { context.applicationContext as Application }
    // Navigation3 的条目级 ViewModelStore 不一定携带 Application extras，设置页统一从根导航注入。
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = remember(application) { SettingsViewModelFactory(application) }
    )
    val privacyAuthenticationEnabled by SettingsManager.getPrivacyContentAuthenticationEnabled(context).collectAsStateWithLifecycle(initialValue = false
        )
    var privacySessionUnlocked by remember { mutableStateOf(false) }
    LaunchedEffect(privacyAuthenticationEnabled) {
        if (!privacyAuthenticationEnabled) {
            privacySessionUnlocked = false
        }
    }
    val uriHandler = LocalUriHandler.current
    val downloadTasks by com.android.purebilibili.feature.download.DownloadManager.tasks.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val uiPreset = LocalUiPreset.current
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val effectiveHomeSettings = remember(homeSettings, uiPreset) {
        resolveEffectiveHomeSettings(
            homeSettings = homeSettings,
            uiPreset = uiPreset
        )
    }
    val uiSkinState by rememberUiSkinState(context)
    val bottomBarUiSkinDecoration = rememberBottomBarUiSkinDecoration(uiSkinState)
    val androidNativeVariant = com.android.purebilibili.core.theme.LocalAndroidNativeVariant.current
    val appearance = remember(homeSettings, uiPreset, androidNativeVariant) {
        resolveAppNavigationAppearance(
            homeSettings = homeSettings,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        )
    }
    val cardTransitionEnabled = appearance.cardTransitionEnabled
    val isBottomBarBlurEnabled = appearance.bottomBarBlurEnabled
    val bottomBarLabelMode = appearance.bottomBarLabelMode
    val isBottomBarFloating = appearance.bottomBarFloating

    // 🔒 [防抖] 全局导航防抖机制 - 防止快速点击导致页面重复加载
    val lastNavigationTime = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val canNavigate: (Boolean) -> Boolean = { bypassDebounce ->
        val currentTime = System.currentTimeMillis()
        val canNav = canProceedWithNavigation(
            currentTimeMillis = currentTime,
            lastNavigationTimeMillis = lastNavigationTime.longValue,
            debounceWindowMillis = 300L,
            bypassDebounce = bypassDebounce
        )
        if (canNav) lastNavigationTime.longValue = currentTime
        canNav
    }
    var inAppSearchKeyword by remember { mutableStateOf<String?>(null) }
    var searchEntryMotionSource by remember { mutableStateOf(SearchEntryMotionSource.NONE) }
    var searchEntryMotionKey by remember { mutableStateOf(0) }
    var bottomBarSearchLaunchKey by remember { mutableStateOf(0) }
    var navigation3ReturnSession by remember { mutableStateOf(BiliPaiReturnSessionState()) }
    val effectiveInitialSearchKeyword = inAppSearchKeyword ?: initialSearchKeyword
    val consumeInitialSearchKeyword: (String) -> Unit = { consumedKeyword ->
        if (inAppSearchKeyword == consumedKeyword) {
            inAppSearchKeyword = null
        } else {
            onInitialSearchKeywordConsumed(consumedKeyword)
        }
    }
    // 🚀 [新手引导] 检查是否首次启动
    // 如果是首次启动，则进入 OnboardingScreen，否则进入 HomeScreen
    val welcomePrefs = androidx.compose.runtime.remember { context.getSharedPreferences("app_welcome", android.content.Context.MODE_PRIVATE) }
    // 注意：这里仅读取初始状态用于设置 startDestination
    // 后续状态更新由 OnboardingScreen 完成
    val firstLaunchShown = welcomePrefs.getBoolean("first_launch_shown", false)
    val launchDisclaimerAck = welcomePrefs.getBoolean(RELEASE_DISCLAIMER_ACK_KEY, false)
    var showLaunchDisclaimer by remember {
        mutableStateOf(!firstLaunchShown && !launchDisclaimerAck)
    }
    val startDestination = if (firstLaunchShown) ScreenRoutes.Home.route else ScreenRoutes.Onboarding.route
    val launchToPortraitFeedOnStartupAtInit = remember {
        SettingsManager.isLaunchToPortraitFeedOnStartupSync(context)
    }

    SharedTransitionProvider(enabled = cardTransitionEnabled) {
        CompositionLocalProvider(
            LocalVideoSharedTransitionSpeedSettings provides VideoSharedTransitionSpeedSettings(
                speed = homeSettings.videoSharedTransitionSpeed,
                customDurationMillis = homeSettings.videoSharedTransitionCustomDurationMillis
            )
        ) {
        // [新增] 全局底栏状态管理
        var navigation3BackStack by remember(startDestination, launchToPortraitFeedOnStartupAtInit) {
            mutableStateOf(
                resolveInitialBiliPaiBackStack(
                    firstRoute = startDestination,
                    onboardingRequired = !firstLaunchShown,
                    openPortraitFeedOnStartup = firstLaunchShown && launchToPortraitFeedOnStartupAtInit
                )
            )
        }
        val currentNavigation3Key = navigation3BackStack.lastOrNull()
        val currentRoute = currentNavigation3Key?.toLegacyRoute()
        val configuredHomeWallpaperUri by SettingsManager.getHomeWallpaperUri(context).collectAsStateWithLifecycle(initialValue = ""
        )
        val splashWallpaperUri by SettingsManager.getSplashWallpaperUri(context).collectAsStateWithLifecycle(initialValue = ""
        )
        val globalHomeWallpaperUri = remember(configuredHomeWallpaperUri, splashWallpaperUri) {
            resolveHomeWallpaperUri(
                homeWallpaperUri = configuredHomeWallpaperUri,
                splashWallpaperUri = splashWallpaperUri
            )
        }
        val backgroundColor = MaterialTheme.colorScheme.background
        val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }
        val isDataSaverActiveForGlobalWallpaper = remember(context) {
            SettingsManager.isDataSaverActive(context)
        }
        val shouldRenderGlobalHomeWallpaper = currentRoute != null &&
            effectiveHomeSettings.homeWallpaperEffectScope == HomeWallpaperEffectScope.GLOBAL &&
            currentRoute != ScreenRoutes.Home.route
        val globalHomeWallpaperAppearance = remember(
            globalHomeWallpaperUri,
            effectiveHomeSettings.homeWallpaperEffectMode,
            shouldRenderGlobalHomeWallpaper,
            isLightBackground,
            isDataSaverActiveForGlobalWallpaper
        ) {
            resolveHomeWallpaperBackdropAppearance(
                hasWallpaper = shouldRenderGlobalHomeWallpaper && globalHomeWallpaperUri.isNotBlank(),
                effectMode = effectiveHomeSettings.homeWallpaperEffectMode,
                isDarkTheme = !isLightBackground,
                isDataSaverActive = isDataSaverActiveForGlobalWallpaper,
                globalWallpaper = true
            )
        }
        var previousRouteForStopPolicy by remember { mutableStateOf<String?>(null) }
        var previousVideoBvidForStopPolicy by remember { mutableStateOf<String?>(null) }
        val currentVideoBvidForStopPolicy = (currentNavigation3Key as? BiliPaiNavKey.VideoDetail)?.bvid

        LaunchedEffect(currentRoute, currentVideoBvidForStopPolicy) {
            if (shouldStopPlaybackEagerlyOnVideoRouteExit(previousRouteForStopPolicy, currentRoute)) {
                if (miniPlayerManager?.isMiniMode != true) {
                    miniPlayerManager?.markLeavingByNavigation(expectedBvid = previousVideoBvidForStopPolicy)
                }
            }
            previousRouteForStopPolicy = currentRoute
            previousVideoBvidForStopPolicy = currentVideoBvidForStopPolicy
        }

        val appNavigationSettings by SettingsManager.getAppNavigationSettings(context).collectAsStateWithLifecycle(initialValue = AppNavigationSettings(),
            context = kotlin.coroutines.EmptyCoroutineContext
        )
        val playerInteractionSettings by SettingsManager.getPlayerInteractionSettings(context)
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.PlayerInteractionSettings(),
                context = kotlin.coroutines.EmptyCoroutineContext
            )
        val bottomBarVisibilityMode = appNavigationSettings.bottomBarVisibilityMode
        val orderedVisibleTabIds = appNavigationSettings.orderedVisibleTabIds
        val visibleBottomBarItems = remember(orderedVisibleTabIds) {
            resolveVisibleBottomBarItems(orderedVisibleTabIds)
        }
        val visibleBottomBarRoutes = remember(visibleBottomBarItems) {
            visibleBottomBarItems.map { it.route }.toSet()
        }
        val bottomPagerState = rememberPagerState(
            pageCount = { visibleBottomBarItems.size.coerceAtLeast(1) }
        )
        val bottomPagerSaveableStateHolder = rememberSaveableStateHolder()
        val mainBottomPagerState = rememberMainBottomPagerState(bottomPagerState)
        var bottomPagerContentReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            bottomPagerContentReady = true
        }
        LaunchedEffect(bottomPagerState.currentPage, mainBottomPagerState) {
            mainBottomPagerState.syncPage()
        }
        LaunchedEffect(visibleBottomBarItems, mainBottomPagerState.selectedPage) {
            val lastPage = visibleBottomBarItems.lastIndex
            if (lastPage >= 0 && mainBottomPagerState.selectedPage > lastPage) {
                mainBottomPagerState.animateToPage(lastPage)
            }
        }
        val bottomPagerRenderBudget =
            resolveBottomPagerRenderBudget(isNavigating = mainBottomPagerState.isNavigating)
        val currentBottomNavItem = remember(
            mainBottomPagerState.selectedPage,
            visibleBottomBarItems
        ) {
            resolveBottomPagerItemForPage(
                page = mainBottomPagerState.selectedPage,
                visibleItems = visibleBottomBarItems
            )
        }

        val bottomBarItemColors = appNavigationSettings.bottomBarItemColors
        // 平板侧边栏模式 (替代 WindowSizeClass)
        val windowSizeClass = LocalWindowSizeClass.current

        // [修复] 平板模式下，仅当用户开启侧边栏设置时才使用侧边导航
        val tabletUseSidebar = appNavigationSettings.tabletUseSidebar
        
        // 统一侧边栏判定策略：600dp+ 且用户开启侧边栏
        val useSideNavigation = shouldUseSidebarNavigationForLayout(windowSizeClass, tabletUseSidebar)
        // 由所有入口共用的底栏内部显隐状态。进视频前先置为隐藏，避免返回到主入口后再补一次隐藏动画。
        var isBottomBarVisible by remember(launchToPortraitFeedOnStartupAtInit) {
            mutableStateOf(!launchToPortraitFeedOnStartupAtInit)
        }
        // [修复] 平板模式下(宽度>=600dp)，进入设置页(Settings.route)时隐藏底栏
        // 因为平板设置页使用 SplitLayout，已经有自己的内部导航结构，不需要底栏
        val isTabletLayout = windowSizeClass.isTablet
        val navMotionSpec = remember(isTabletLayout, cardTransitionEnabled) {
            resolveAppNavigationMotionSpec(
                isTabletLayout = isTabletLayout,
                cardTransitionEnabled = cardTransitionEnabled
            )
        }
        fun markVideoReturnSession(): BiliPaiReturnSessionState {
            navigation3ReturnSession = navigation3ReturnSession.markReturning(SystemClock.uptimeMillis())
            return navigation3ReturnSession
        }
        val isAtMainHostRoot = navigation3BackStack.lastOrNull() == BiliPaiNavKey.MainHost
        val systemBackAction = remember(
            isAtMainHostRoot,
            currentBottomNavItem,
        ) {
            resolveAppSystemBackAction(
                isAtMainHostRoot = isAtMainHostRoot,
                currentBottomItem = currentBottomNavItem,
                homeItem = BottomNavItem.HOME
            )
        }
        fun currentNavigation3SourceMetadata() = resolveBiliPaiNavSourceMetadata(
            sourceKey = navigation3ReturnSession.lastVideoSourceKey,
            sourceRoute = navigation3ReturnSession.lastVideoSourceRoute,
            clickedBoundsRecorded = CardPositionManager.lastClickedCardBounds != null &&
                CardPositionManager.lastClickedVideoSourceKey == navigation3ReturnSession.lastVideoSourceKey,
            cardFullyVisible = CardPositionManager.isCardFullyVisible,
            cardSourceDirection = resolveBiliPaiNavCardSourceDirection(
                clickedBoundsRecorded = CardPositionManager.lastClickedCardBounds != null &&
                    CardPositionManager.lastClickedVideoSourceKey == navigation3ReturnSession.lastVideoSourceKey,
                cardFullyVisible = CardPositionManager.isCardFullyVisible,
                isSingleColumnCard = CardPositionManager.isSingleColumnCard,
                normalizedCenterX = CardPositionManager.lastClickedCardCenter?.x
            )
        )
        fun pushNavigation3KeyDirect(key: BiliPaiNavKey) {
            navigation3BackStack = pushBiliPaiNavKey(
                currentStack = navigation3BackStack,
                key = key
            )
        }
        fun pushNavigation3Key(key: BiliPaiNavKey, beforeNavigation: (() -> Unit)? = null) {
            val target = key.toPrivacyNavigationTarget()
            if (
                shouldRequirePrivacyAuthentication(
                    privacyAuthenticationEnabled = privacyAuthenticationEnabled,
                    privacySessionUnlocked = privacySessionUnlocked,
                    target = target
                )
            ) {
                onPrivacyAuthenticationRequired(
                    PrivacyAuthenticationRequest(PrivacyAuthenticationReason.OPEN_PRIVACY_CONTENT)
                ) { result ->
                    when (result) {
                        PrivacyAuthenticationResult.Success -> {
                            privacySessionUnlocked = true
                            beforeNavigation?.invoke()
                            pushNavigation3KeyDirect(key)
                        }
                        is PrivacyAuthenticationResult.Failure -> {
                            android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                beforeNavigation?.invoke()
                pushNavigation3KeyDirect(key)
            }
        }
        fun replaceNavigation3TopWithKey(key: BiliPaiNavKey) {
            navigation3BackStack = pushBiliPaiNavKey(
                currentStack = popBiliPaiNavKey(navigation3BackStack),
                key = key
            )
        }
        fun requestBottomPagerPageForRoute(route: String, beforeNavigation: (() -> Unit)? = null): Boolean {
            val page = resolveBottomPagerPageForRoute(
                route = route,
                visibleItems = visibleBottomBarItems
            ) ?: return false
            val target = legacyRouteToBiliPaiNavKey(route).toPrivacyNavigationTarget()
            val performPagerNavigation = {
                beforeNavigation?.invoke()
                navigation3BackStack = listOf(BiliPaiNavKey.MainHost)
                mainBottomPagerState.animateToPage(page)
            }
            if (
                shouldRequirePrivacyAuthentication(
                    privacyAuthenticationEnabled = privacyAuthenticationEnabled,
                    privacySessionUnlocked = privacySessionUnlocked,
                    target = target
                )
            ) {
                onPrivacyAuthenticationRequired(
                    PrivacyAuthenticationRequest(PrivacyAuthenticationReason.OPEN_PRIVACY_CONTENT)
                ) { result ->
                    when (result) {
                        PrivacyAuthenticationResult.Success -> {
                            privacySessionUnlocked = true
                            performPagerNavigation()
                        }
                        is PrivacyAuthenticationResult.Failure -> {
                            android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                performPagerNavigation()
            }
            return true
        }
        fun pushNavigation3Route(route: String, beforeNavigation: (() -> Unit)? = null) {
            if (!canNavigate(shouldBypassNavigationDebounceForRoute(route))) return
            if (requestBottomPagerPageForRoute(route, beforeNavigation)) return
            pushNavigation3Key(legacyRouteToBiliPaiNavKey(route), beforeNavigation)
        }
        fun navigateToSearchFromBottomBar() {
            pushNavigation3Key(BiliPaiNavKey.Search) {
                searchEntryMotionSource = SearchEntryMotionSource.BOTTOM_BAR
                searchEntryMotionKey += 1
            }
        }
        fun requestSearchFromBottomBar() {
            bottomBarSearchLaunchKey += 1
            navigateToSearchFromBottomBar()
        }
        fun navigateToPortraitStoryInNavigation3(
            seed: PortraitStoryNavigationSeed
        ) {
            if (!canNavigate(false)) return
            isBottomBarVisible = false
            pushNavigation3Key(
                BiliPaiNavKey.Story(
                    seedBvid = seed.bvid,
                    seedCid = seed.cid,
                    seedCover = seed.coverUrl
                )
            )
        }
        fun navigateToVideoRouteInNavigation3(
            route: String,
            sourceRoute: String?,
            skipPortraitStoryResolution: Boolean = false
        ) {
            if (!canNavigate(false)) return
            val parsedKey = legacyRouteToBiliPaiNavKey(route)
            val videoKey = parsedKey as? BiliPaiNavKey.VideoDetail
            if (!skipPortraitStoryResolution) {
                resolvePortraitStoryNavigationSeed(
                    directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                    isVerticalVideo = videoKey?.initialVertical == true,
                    startAudio = videoKey?.startAudio == true,
                    bvid = videoKey?.bvid.orEmpty(),
                    cid = videoKey?.cid ?: 0L,
                    coverUrl = videoKey?.coverUrl.orEmpty()
                )?.let { seed ->
                    navigateToPortraitStoryInNavigation3(seed)
                    return
                }
                if (
                    videoKey != null &&
                    com.android.purebilibili.data.model.response.shouldResolveVerticalVideoForPortraitEntry(
                        directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                        startAudio = videoKey.startAudio,
                        bvid = videoKey.bvid,
                        isVerticalVideo = videoKey.initialVertical,
                        coverUrl = videoKey.coverUrl
                    )
                ) {
                    coroutineScope.launch {
                        if (com.android.purebilibili.data.repository.VideoRepository.isVerticalVideo(videoKey.bvid)) {
                            navigateToPortraitStoryInNavigation3(
                                PortraitStoryNavigationSeed(
                                    bvid = videoKey.bvid,
                                    cid = videoKey.cid,
                                    coverUrl = videoKey.coverUrl
                                )
                            )
                        } else {
                            navigateToVideoRouteInNavigation3(
                                route = route,
                                sourceRoute = sourceRoute,
                                skipPortraitStoryResolution = true
                            )
                        }
                    }
                    return
                }
            }
            val videoBvid = videoKey?.bvid.orEmpty()
            val matchedVisibleCardRoute = resolveVideoCardSourceRouteForNavigation(
                currentRoute = navigation3BackStack.lastOrNull()?.toLegacyRoute(),
                videoBvid = videoBvid,
                lastClickedVideoSourceKey = CardPositionManager.lastClickedVideoSourceKey,
                visibleBottomBarRoutes = visibleBottomBarRoutes
            )
            val source = resolveBiliPaiVideoSource(
                bvid = videoBvid,
                explicitSourceRoute = sourceRoute ?: matchedVisibleCardRoute,
                currentKey = navigation3BackStack.lastOrNull(),
                previousSourceRoute = navigation3ReturnSession.lastVideoSourceRoute
            )
            navigation3ReturnSession = navigation3ReturnSession
                .recordVideoSource(source)
                .markDetailEntered(SystemClock.uptimeMillis())
            miniPlayerManager?.isNavigatingToVideo = true
            miniPlayerManager?.exitMiniMode(animate = false)
            val key = when (parsedKey) {
                is BiliPaiNavKey.VideoDetail -> parsedKey.copy(sourceRoute = source.route)
                else -> parsedKey
            }
            pushNavigation3Key(key)
        }
        fun navigateToVideoInNavigation3(
            bvid: String,
            cid: Long = 0L,
            coverUrl: String = "",
            startAudio: Boolean = false,
            autoPortrait: Boolean = shouldAutoEnterPortraitForStandardVideoNavigation(),
            resumePositionMs: Long = 0L,
            initialVertical: Boolean = false,
            sourceRoute: String? = null
        ) {
            resolvePortraitStoryNavigationSeed(
                directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                isVerticalVideo = initialVertical,
                startAudio = startAudio,
                bvid = bvid,
                cid = cid,
                coverUrl = coverUrl
            )?.let { seed ->
                navigateToPortraitStoryInNavigation3(seed)
                return
            }
            val isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
            val offlineTask = com.android.purebilibili.feature.download.resolveOfflineVideoNavigationTask(
                tasks = downloadTasks.values,
                bvid = bvid,
                cid = cid,
                isNetworkAvailable = isNetworkAvailable
            )
            if (offlineTask != null) {
                pushNavigation3Route(ScreenRoutes.OfflineVideoPlayer.createRoute(offlineTask.id))
                return
            }
            if (!isNetworkAvailable) {
                Toast.makeText(context, "当前无网络，仅支持播放已缓存视频", Toast.LENGTH_SHORT).show()
                return
            }
            val videoRoute = resolveStandardVideoRoute(
                bvid = bvid,
                cid = cid,
                coverUrl = coverUrl,
                startAudio = startAudio,
                autoPortrait = autoPortrait,
                resumePositionMs = resumePositionMs,
                initialVertical = initialVertical
            )
            if (
                com.android.purebilibili.data.model.response.shouldResolveVerticalVideoForPortraitEntry(
                    directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                    startAudio = startAudio,
                    bvid = bvid,
                    isVerticalVideo = initialVertical,
                    coverUrl = coverUrl
                )
            ) {
                coroutineScope.launch {
                    if (com.android.purebilibili.data.repository.VideoRepository.isVerticalVideo(bvid)) {
                        navigateToPortraitStoryInNavigation3(
                            PortraitStoryNavigationSeed(
                                bvid = bvid.trim(),
                                cid = cid,
                                coverUrl = coverUrl
                            )
                        )
                    } else {
                        navigateToVideoRouteInNavigation3(
                            route = videoRoute,
                            sourceRoute = sourceRoute,
                            skipPortraitStoryResolution = true
                        )
                    }
                }
                return
            }
            navigateToVideoRouteInNavigation3(
                route = videoRoute,
                sourceRoute = sourceRoute,
                skipPortraitStoryResolution = true
            )
        }
        fun navigateToHomeVideoInNavigation3(request: HomeVideoClickRequest) {
            when (val target = resolveHomeNavigationTarget(request)) {
                is HomeNavigationTarget.Video -> {
                    val intent = resolveHomeVideoNavigationIntent(request)
                    if (intent != null) {
                        val navigateToVideo = {
                            navigateToVideoInNavigation3(
                                bvid = intent.bvid,
                                cid = intent.cid,
                                coverUrl = intent.coverUrl,
                                autoPortrait = true,
                                initialVertical = intent.isVerticalVideo,
                                sourceRoute = intent.sourceRoute ?: ScreenRoutes.Home.route
                            )
                        }
                        navigateToVideo()
                    } else {
                        navigateToVideoRouteInNavigation3(
                            route = target.route,
                            sourceRoute = request.sourceRoute ?: ScreenRoutes.Home.route
                        )
                    }
                }
                is HomeNavigationTarget.DynamicDetail -> {
                    pushNavigation3Route(ScreenRoutes.DynamicDetail.createRoute(target.dynamicId))
                }
                null -> Unit
            }
        }
        val navigation3SourceMetadata = currentNavigation3SourceMetadata()
        val previousNavigation3Key = navigation3BackStack.getOrNull(navigation3BackStack.lastIndex - 1)
        val backGestureDecision = remember(
            cardTransitionEnabled,
            systemBackAction,
            currentNavigation3Key,
            previousNavigation3Key,
            navigation3SourceMetadata
        ) {
            resolveBiliPaiBackGestureDecision(
                cardTransitionEnabled = cardTransitionEnabled,
                systemBackAction = systemBackAction,
                currentKey = currentNavigation3Key,
                previousKey = previousNavigation3Key,
                sourceMetadata = navigation3SourceMetadata
            )
        }
        val predictiveBackEnabled = appNavigationSettings.predictiveBackEnabled
        val shouldInterceptTabBack = backGestureDecision.interceptSystemBack
        val shouldUseClassicBackHandler = !predictiveBackEnabled &&
            systemBackAction != AppSystemBackAction.FINISH_ACTIVITY
        val predictiveBackAnimationStyle = remember(appNavigationSettings.predictiveBackAnimationStyle) {
            BiliPaiPredictiveBackAnimationStyle.fromStorageValue(
                appNavigationSettings.predictiveBackAnimationStyle
            )
        }
        val predictiveBackExitDirection = appNavigationSettings.predictiveBackExitDirection
        val activeBottomTabRoute = resolveActiveBottomTabRoute(
            currentKey = currentNavigation3Key,
            currentBottomItem = currentBottomNavItem
        )
        val isVideoDetailDestination = isVideoDetailRoute(currentRoute)
        val bottomBarMountRoute = if (isVideoDetailDestination) {
            currentBottomNavItem.route
        } else {
            activeBottomTabRoute
        }
        val isSettingsScreen = activeBottomTabRoute == ScreenRoutes.Settings.route
        val shouldHideBottomBarOnTablet = isTabletLayout && isSettingsScreen

        // [UX] 底栏仅在“用户配置为可见的一级入口”显示；Story 始终沉浸式隐藏。
        val isBottomBarDestination = shouldShowBottomBarForNavigation(
            activeRoute = activeBottomTabRoute,
            visibleBottomBarRoutes = visibleBottomBarRoutes,
            useSideNavigation = false,
            shouldHideBottomBarOnTablet = false,
            shouldDeferReveal = false
        )
        val shouldDeferBottomBarReveal = shouldDeferBottomBarRevealOnVideoReturn(
            isReturningFromDetail = navigation3ReturnSession.isReturningFromDetail,
            activeBottomTabRoute = activeBottomTabRoute,
            cardTransitionEnabled = cardTransitionEnabled
        )
        val bottomBarMountGate = shouldShowBottomBarForNavigation(
            activeRoute = bottomBarMountRoute,
            visibleBottomBarRoutes = visibleBottomBarRoutes,
            useSideNavigation = useSideNavigation,
            shouldHideBottomBarOnTablet = shouldHideBottomBarOnTablet,
            shouldDeferReveal = false
        )
        val showBottomBar = shouldShowBottomBarForNavigation(
            activeRoute = activeBottomTabRoute,
            visibleBottomBarRoutes = visibleBottomBarRoutes,
            useSideNavigation = useSideNavigation,
            shouldHideBottomBarOnTablet = shouldHideBottomBarOnTablet,
            shouldDeferReveal = shouldDeferBottomBarReveal
        )
        
        // 核心可见性逻辑：
        // 1. 永久隐藏模式 -> 始终隐藏
        // 2. 始终显示模式 -> 始终显示
        // 3. 上滑隐藏模式 -> 由子页面通过 LocalSetBottomBarVisible 控制，初始为 true
        // 根据模式强制重置状态（防止模式切换后状态卡死）
        LaunchedEffect(bottomBarVisibilityMode) {
            isBottomBarVisible = true
        }

        // 视频详情页只通过可见性退出底栏，避免写入隐藏状态后返回首页卡住。
        LaunchedEffect(
            currentRoute,
            activeBottomTabRoute,
            isBottomBarDestination,
            navigation3ReturnSession.isReturningFromDetail,
            navigation3ReturnSession.isQuickReturnFromDetail,
            cardTransitionEnabled
        ) {
            if (!isBottomBarDestination) return@LaunchedEffect
            if (
                shouldDelayBottomBarRevealAfterVideoReturn(
                    isReturningFromDetail = navigation3ReturnSession.isReturningFromDetail,
                    isBottomBarDestination = isBottomBarDestination,
                    cardTransitionEnabled = cardTransitionEnabled
                )
            ) {
                kotlinx.coroutines.delay(
                    resolveVideoReturnBottomBarRevealDelayMs(
                        cardTransitionEnabled = cardTransitionEnabled,
                        isQuickReturnFromDetail = navigation3ReturnSession.isQuickReturnFromDetail
                    )
                )
            }
            isBottomBarVisible = true
        }
        
        // 最终决定是否显示：
        // - 必须是用户配置的可见主入口页面
        // - 不是侧边栏模式
        // - 不是故事模式
        // - 且 (模式为始终显示 OR (模式为上滑隐藏 AND 当前状态为可见))
        // - 且 模式不是永久隐藏
        val finalBottomBarVisible = showBottomBar &&
            !isVideoDetailDestination &&
            bottomBarVisibilityMode != SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN &&
            (
                bottomBarVisibilityMode == SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE ||
                    isBottomBarVisible
            )

        val setBottomBarVisible: (Boolean) -> Unit = remember {
            bottomBarSetter@{ visible: Boolean ->
                if (isBottomBarVisible != visible) {
                    isBottomBarVisible = visible
                }
            }
        }

        // [新增] 首页回顶事件通道 (Channel based event bus)
        val homeScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val dynamicScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val historyScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val favoriteScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        var dynamicUnreadCount by remember { mutableStateOf(0) }
        val dynamicUnreadPollingEnabled = visibleBottomBarItems.contains(BottomNavItem.DYNAMIC)
        LaunchedEffect(currentBottomNavItem, dynamicUnreadPollingEnabled) {
            if (!dynamicUnreadPollingEnabled || currentBottomNavItem == BottomNavItem.DYNAMIC) {
                dynamicUnreadCount = 0
                return@LaunchedEffect
            }
            while (true) {
                com.android.purebilibili.data.repository.DynamicRepository.getDynamicUpdateCount(
                    advanceBaseline = false
                )
                    .onSuccess { count -> dynamicUnreadCount = count }
                kotlinx.coroutines.delay(60_000L)
            }
        }

        val handleNavItemClick: (BottomNavItem) -> Unit = { item ->
            when (resolveBottomBarSelectionAction(currentBottomNavItem, item)) {
                BottomBarSelectionAction.NAVIGATE -> {
                    requestBottomPagerPageForRoute(item.route)
                }
                BottomBarSelectionAction.RESELECT -> when (item) {
                    BottomNavItem.HOME -> homeScrollChannel.trySend(Unit)
                    BottomNavItem.DYNAMIC -> dynamicScrollChannel.trySend(Unit)
                    BottomNavItem.HISTORY -> historyScrollChannel.trySend(Unit)
                    BottomNavItem.FAVORITE -> favoriteScrollChannel.trySend(Unit)
                    else -> Unit
                }
            }
        }
        fun pushSearchRouteInNavigation3(keyword: String) {
            val normalizedKeyword = keyword.trim()
            if (normalizedKeyword.isNotEmpty()) {
                pushNavigation3Route(ScreenRoutes.Search.route) {
                    inAppSearchKeyword = normalizedKeyword
                }
            }
        }

        fun openBilibiliNativeTargetInNavigation3(target: BilibiliNavigationTarget): Boolean {
            when (target) {
                is BilibiliNavigationTarget.Video -> navigateToVideoInNavigation3(target.videoId, 0L, "")
                is BilibiliNavigationTarget.Dynamic -> {
                    pushNavigation3Key(BiliPaiNavKey.DynamicDetail(target.dynamicId))
                }
                is BilibiliNavigationTarget.Search -> pushSearchRouteInNavigation3(target.keyword)
                is BilibiliNavigationTarget.Space -> {
                    if (target.mid <= 0L) return false
                    pushNavigation3Key(BiliPaiNavKey.Space(target.mid))
                }
                is BilibiliNavigationTarget.Live -> {
                    pushNavigation3Key(BiliPaiNavKey.Live(roomId = target.roomId.toString()))
                }
                is BilibiliNavigationTarget.BangumiSeason -> {
                    pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = target.seasonId))
                }
                is BilibiliNavigationTarget.BangumiEpisode -> {
                    pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = 0L, epId = target.epId))
                }
                is BilibiliNavigationTarget.Music -> {
                    val auSid = target.musicId.removePrefix("au").removePrefix("AU").toLongOrNull() ?: return false
                    pushNavigation3Key(BiliPaiNavKey.MusicDetail(auSid))
                }
                is BilibiliNavigationTarget.Article -> {
                    pushNavigation3Key(BiliPaiNavKey.ArticleDetail(target.articleId))
                }
            }
            return true
        }
        val submitSearchKeywordInNavigation3: (String) -> Unit = { keyword ->
            when (val action = resolveSearchSubmitAction(keyword)) {
                SearchSubmitAction.Ignore -> Unit
                is SearchSubmitAction.OpenSearch -> pushSearchRouteInNavigation3(action.keyword)
                is SearchSubmitAction.OpenNativeTarget -> openBilibiliNativeTargetInNavigation3(action.target)
            }
        }
        fun openBilibiliLinkInNavigation3(rawLink: String) {
            when (val action = resolveBilibiliLinkNavigationAction(rawLink)) {
                is BilibiliLinkNavigationAction.NativeTarget -> {
                    openBilibiliNativeTargetInNavigation3(action.target)
                }
                is BilibiliLinkNavigationAction.InAppWeb -> {
                    if (isBilibiliShortWebLink(action.url)) {
                        coroutineScope.launch {
                            val resolvedTarget = BilibiliNavigationTargetParser.resolve(action.url)
                            if (
                                resolvedTarget == null ||
                                !openBilibiliNativeTargetInNavigation3(resolvedTarget)
                            ) {
                                pushNavigation3Key(BiliPaiNavKey.Web(action.url))
                            }
                        }
                    } else if (canNavigate(false)) {
                        pushNavigation3Key(BiliPaiNavKey.Web(action.url))
                    }
                }
                is BilibiliLinkNavigationAction.External -> {
                    runCatching { uriHandler.openUri(action.url) }
                }
                BilibiliLinkNavigationAction.None -> Unit
            }
        }
        fun openMessageLinkInNavigation3(rawLink: String) {
            when (val action = resolveMessageLinkNavigationAction(rawLink)) {
                is MessageLinkNavigationAction.Video -> {
                    navigateToVideoInNavigation3(action.videoId, 0L, "")
                }
                is MessageLinkNavigationAction.VideoComment -> {
                    navigateToVideoRouteInNavigation3(
                        route = VideoRoute.createRoute(
                            bvid = action.videoId,
                            cid = 0L,
                            coverUrl = "",
                            commentRootRpid = action.rootReplyId,
                            commentTargetRpid = action.targetReplyId
                        ),
                        sourceRoute = currentRoute
                    )
                }
                is MessageLinkNavigationAction.Dynamic -> {
                    pushNavigation3Key(BiliPaiNavKey.DynamicDetail(action.dynamicId))
                }
                is MessageLinkNavigationAction.DynamicComment -> {
                    pushNavigation3Key(
                        BiliPaiNavKey.DynamicDetail(
                            dynamicId = action.dynamicId,
                            commentRootRpid = action.rootReplyId,
                            commentTargetRpid = action.targetReplyId
                        )
                    )
                }
                is MessageLinkNavigationAction.Space -> {
                    pushNavigation3Key(BiliPaiNavKey.Space(action.mid))
                }
                is MessageLinkNavigationAction.Live -> {
                    pushNavigation3Key(BiliPaiNavKey.Live(roomId = action.roomId.toString()))
                }
                is MessageLinkNavigationAction.BangumiSeason -> {
                    pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = action.seasonId))
                }
                is MessageLinkNavigationAction.BangumiEpisode -> {
                    pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = 0L, epId = action.epId))
                }
                is MessageLinkNavigationAction.Music -> {
                    action.musicId.toLongOrNull()
                        ?.let { pushNavigation3Key(BiliPaiNavKey.MusicDetail(it)) }
                        ?: pushNavigation3Key(BiliPaiNavKey.Web(rawLink))
                }
                is MessageLinkNavigationAction.Article -> {
                    pushNavigation3Key(BiliPaiNavKey.ArticleDetail(action.articleId))
                }
                is MessageLinkNavigationAction.Web -> {
                    pushNavigation3Key(BiliPaiNavKey.Web(action.url))
                }
            }
        }
        LaunchedEffect(pendingVideoId) {
            pendingVideoId?.let { videoId ->
                val currentVideoBvid = (navigation3BackStack.lastOrNull() as? BiliPaiNavKey.VideoDetail)?.bvid
                if (
                    shouldNavigateToVideoFromNotification(
                        currentRoute = currentRoute,
                        currentBvid = currentVideoBvid,
                        targetBvid = videoId
                    )
                ) {
                    miniPlayerManager?.isNavigatingToVideo = true
                    navigateToVideoInNavigation3(videoId, 0L, "")
                }
                onPendingVideoIdConsumed(videoId)
            }
        }
        LaunchedEffect(pendingShortcutRoute) {
            pendingShortcutRoute?.let { route ->
                resolveShortcutRoute(route)?.let { targetRoute ->
                    pushNavigation3Route(targetRoute)
                }
                onPendingShortcutRouteConsumed(route)
            }
        }
        LaunchedEffect(pendingNavigationRoute) {
            pendingNavigationRoute?.let { route ->
                pushNavigation3Route(route)
                onPendingNavigationRouteConsumed(route)
            }
        }
        // [New] Global Scroll Offset State
        val scrollOffsetState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val homeFeedScrollInProgressState = remember { androidx.compose.runtime.mutableStateOf(false) }
        LaunchedEffect(currentRoute, currentBottomNavItem) {
            scrollOffsetState.floatValue = 0f
            if (currentBottomNavItem != BottomNavItem.HOME) {
                homeFeedScrollInProgressState.value = false
            }
        }

        // [LayerBackdrop] Create backdrop for bottom bar refraction effect.
        // Capture the wallpaper and navigation content together so transparent wallpaper-aware
        // pages feed the same background into the floating dock as Home.
        val bottomBarBackdrop = rememberMiuixLayerBackdrop()

        CompositionLocalProvider(
            LocalSetBottomBarVisible provides setBottomBarVisible,
            LocalBottomBarVisible provides finalBottomBarVisible,
            LocalGlobalWallpaperBackdropVisible provides globalHomeWallpaperAppearance.visible,
            com.android.purebilibili.feature.home.LocalHomeScrollChannel provides homeScrollChannel,
            LocalDynamicScrollChannel provides dynamicScrollChannel,
            com.android.purebilibili.feature.home.LocalHomeScrollOffset provides scrollOffsetState,
            com.android.purebilibili.feature.home.LocalHomeFeedScrollInProgress provides
                homeFeedScrollInProgressState
        ) {
            fun markNavigation3VideoReturnBeforeBackAction(targetKey: BiliPaiNavKey?) {
                val fromRoute = navigation3BackStack.lastOrNull()?.toLegacyRoute()
                val targetRoute = targetKey?.toLegacyRoute()
                if (isVideoDetailRoute(fromRoute) && isVideoCardReturnTargetRoute(targetRoute)) {
                    markVideoReturnSession()
                }
            }

            fun prepareVideoPlaybackForNavigationExit(videoKey: BiliPaiNavKey.VideoDetail) {
                val manager = miniPlayerManager ?: return
                if (manager.shouldShowInAppMiniPlayer()) {
                    manager.enterMiniMode()
                } else if (shouldMarkNavigationLeaveBeforeVideoExit(isMiniMode = manager.isMiniMode)) {
                    manager.markLeavingByNavigation(expectedBvid = videoKey.bvid)
                }
            }

            fun popVideoDetailWithSharedReturnState(
                videoKey: BiliPaiNavKey.VideoDetail,
                targetKey: BiliPaiNavKey?,
                popAction: () -> Unit
            ) {
                markNavigation3VideoReturnBeforeBackAction(targetKey = targetKey)
                prepareVideoPlaybackForNavigationExit(videoKey)
                popAction()
            }

            val performSystemBackAction = {
                when (systemBackAction) {
                    AppSystemBackAction.RETURN_TO_HOME_TAB -> {
                        val homeIndex = visibleBottomBarItems.indexOf(BottomNavItem.HOME)
                        if (homeIndex >= 0) {
                            mainBottomPagerState.animateToPage(homeIndex)
                        }
                    }
                    AppSystemBackAction.NAVIGATE_UP -> {
                        val previousKey = navigation3BackStack.getOrNull(navigation3BackStack.lastIndex - 1)
                        val currentVideoKey = navigation3BackStack.lastOrNull() as? BiliPaiNavKey.VideoDetail
                        if (currentVideoKey != null) {
                            popVideoDetailWithSharedReturnState(
                                videoKey = currentVideoKey,
                                targetKey = previousKey
                            ) {
                                navigation3BackStack = popBiliPaiNavKey(navigation3BackStack)
                            }
                        } else {
                            navigation3BackStack = popBiliPaiNavKey(navigation3BackStack)
                        }
                    }
                    AppSystemBackAction.FINISH_ACTIVITY -> context.findActivity()?.finish()
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (windowSizeClass.shouldUseSideNavigation && isBottomBarDestination) {
                    AnimatedVisibility(
                        visible = useSideNavigation,
                        enter = slideInHorizontally(
                            animationSpec = softLandingSpring(),
                            initialOffsetX = { -it }
                        ) + fadeIn(animationSpec = emphasizedEnterTween(navMotionSpec.slowFadeDurationMillis)),
                        exit = slideOutHorizontally(
                            animationSpec = emphasizedExitTween(navMotionSpec.fastFadeDurationMillis),
                            targetOffsetX = { -it }
                        ) + fadeOut(animationSpec = emphasizedExitTween(navMotionSpec.fastFadeDurationMillis))
                    ) {
                        FrostedSideBar(
                            currentItem = currentBottomNavItem,
                            onItemClick = handleNavItemClick,
                            firstItemModifier = Modifier,
                            onHomeDoubleTap = { homeScrollChannel.trySend(Unit) },
                            hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                            visibleItems = visibleBottomBarItems,
                            itemColorIndices = bottomBarItemColors,
                            uiSkinDecoration = bottomBarUiSkinDecoration,
                            onToggleSidebar = {
                                // [Tablet] Toggle sidebar mode
                                coroutineScope.launch {
                                    SettingsManager.setTabletUseSidebar(context, false)
                                }
                            }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .animateContentSize()
                ) {
                // ===== 内容层 (hazeSource) =====
                // 这个 Box 包裹全局壁纸和所有导航内容，作为底栏模糊/折射的源
                // [LayerBackdrop] Apply layerBackdrop before the bottom bar sibling so the dock
                // captures wallpaper + page content, but never captures itself.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .miuixLayerBackdrop(bottomBarBackdrop)
                        // [Fix] 将内容标记为全局底栏模糊的源
                        // 必须添加 hazeSource，否则底栏的 hazeEffect 无法获取背景内容，导致模糊失效
                        .then(if (mainHazeState != null) Modifier.hazeSourceCompat(mainHazeState) else Modifier)
                ) {
                    HomeWallpaperBackdrop(
                        wallpaperUri = globalHomeWallpaperUri,
                        appearance = globalHomeWallpaperAppearance,
                        baseColor = backgroundColor,
                        isDataSaverActive = isDataSaverActiveForGlobalWallpaper
                    )
                fun bottomPagerNavKeyForItem(item: BottomNavItem): BiliPaiNavKey {
                    return when (item) {
                        BottomNavItem.HOME -> BiliPaiNavKey.Home
                        BottomNavItem.DYNAMIC -> BiliPaiNavKey.Dynamic
                        BottomNavItem.STORY -> BiliPaiNavKey.Story()
                        BottomNavItem.HISTORY -> BiliPaiNavKey.History
                        BottomNavItem.PROFILE -> BiliPaiNavKey.Profile
                        BottomNavItem.FAVORITE -> BiliPaiNavKey.Favorite
                        BottomNavItem.LIVE -> BiliPaiNavKey.LiveList
                        BottomNavItem.WATCHLATER -> BiliPaiNavKey.WatchLater
                        BottomNavItem.SETTINGS -> BiliPaiNavKey.Settings
                    }
                }

                fun mainHostBottomBarRoute(): String {
                    return currentBottomNavItem.route
                }

                fun shouldShowMainHostBottomBar(): Boolean {
                    val route = mainHostBottomBarRoute()
                    return shouldShowBottomBarForNavigation(
                        activeRoute = route,
                        visibleBottomBarRoutes = visibleBottomBarRoutes,
                        useSideNavigation = useSideNavigation,
                        shouldHideBottomBarOnTablet = isTabletLayout && route == ScreenRoutes.Settings.route,
                        shouldDeferReveal = shouldDeferBottomBarReveal
                    )
                }

                fun resolveMainHostBottomBarVisible(): Boolean {
                    return shouldShowMainHostBottomBar() &&
                        bottomBarVisibilityMode != SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN &&
                        (
                            bottomBarVisibilityMode == SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE ||
                                isBottomBarVisible
                        )
                }

                @Composable
                fun VideoCardTransitionBackgroundRouteContent(
                    key: BiliPaiNavKey,
                    content: @Composable () -> Unit
                ) {
                    val entryRoute = key.toLegacyRoute()
                    val backgroundState = LocalVideoCardTransitionBackgroundState.current
                    val predictiveBackState = LocalPredictiveBackBackgroundState.current
                    val predictiveBlurProgress = predictiveBackState.progressProvider()
                    val shouldApplyBackground = cardTransitionEnabled &&
                        shouldApplyVideoCardTransitionBackgroundToRoute(
                            entryRoute = entryRoute,
                            sourceRoute = navigation3SourceMetadata.sourceRoute,
                            activeMainHostRoute = activeBottomTabRoute
                        )
                    val shouldApplyPredictiveBlur = shouldApplyPredictiveBackBlurToRoute(
                        entryKey = key,
                        targetBackKey = predictiveBackState.targetKeyProvider(),
                        blurProgress = predictiveBlurProgress,
                    )
                    val routeModifier = Modifier
                        .fillMaxSize()
                        .let { baseModifier ->
                            baseModifier
                                .let { modifier ->
                                    if (shouldApplyBackground) {
                                        modifier.videoCardTransitionBackgroundEffect(
                                            progressProvider = backgroundState.progressProvider,
                                            phaseProvider = backgroundState.phaseProvider,
                                            motionTierProvider = backgroundState.motionTierProvider,
                                            isLightBackgroundProvider = backgroundState.isLightBackgroundProvider,
                                        )
                                    } else {
                                        modifier
                                    }
                                }
                                .let { modifier ->
                                    if (shouldApplyPredictiveBlur) {
                                        modifier.predictiveBackBackgroundEffect(
                                            progressProvider = predictiveBackState.progressProvider,
                                            motionTierProvider = predictiveBackState.motionTierProvider,
                                            isLightBackgroundProvider = predictiveBackState.isLightBackgroundProvider,
                                        )
                                    } else {
                                        modifier
                                    }
                                }
                        }
                    Box(modifier = routeModifier) {
                        content()
                    }
                }

                @Composable
                fun RenderNavigationContent(
                    key: BiliPaiNavKey,
                    isBottomPagerPageActive: Boolean = true
                ) {
                    @Composable
                    fun SettingsTabletEntry(content: @Composable () -> Unit) {
                        SettingsTabletNavEntryShell(
                            key = key,
                            onSystemBack = { performSystemBackAction() },
                            onPushKey = { pushNavigation3Key(it) },
                            content = content,
                        )
                    }

                    when (resolveBiliPaiNavEntryContentRole(key)) {
                        BiliPaiNavEntryContentRole.MAIN_HOST -> {
                            CompositionLocalProvider(
                                LocalBottomBarVisible provides resolveMainHostBottomBarVisible()
                            ) {
                                VideoCardTransitionBackgroundRouteContent(bottomPagerNavKeyForItem(currentBottomNavItem)) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        HorizontalPager(
                                            modifier = Modifier.fillMaxSize(),
                                            state = bottomPagerState,
                                            beyondViewportPageCount = resolveBottomPagerBeyondViewportPageCount(
                                                pageCount = visibleBottomBarItems.size,
                                                contentReady = bottomPagerContentReady
                                            ),
                                            userScrollEnabled = shouldEnableBottomPagerUserScroll()
                                        ) { page ->
                                            val slotItem = visibleBottomBarItems.getOrNull(page) ?: BottomNavItem.HOME
                                            if (
                                                shouldComposeBottomPagerPage(
                                                    item = slotItem,
                                                    page = page,
                                                    currentPage = bottomPagerState.currentPage,
                                                    selectedPage = mainBottomPagerState.selectedPage,
                                                    isNavigating = mainBottomPagerState.isNavigating,
                                                    navigationStartPage = mainBottomPagerState.navigationStartPage,
                                                    contentReady = bottomPagerContentReady
                                                )
                                            ) {
                                                val pageKey = bottomPagerNavKeyForItem(slotItem)
                                                bottomPagerSaveableStateHolder.SaveableStateProvider(
                                                    resolveBottomPagerSaveableStateKey(slotItem)
                                                ) {
                                                    CompositionLocalProvider(
                                                        LocalVideoCardSharedElementSourceRoute provides pageKey.toLegacyRoute()
                                                    ) {
                                                        RenderNavigationContent(
                                                            key = pageKey,
                                                            isBottomPagerPageActive = page == bottomPagerState.settledPage
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        BiliPaiNavEntryContentRole.HOME -> HomeScreen(
                                viewModel = homeViewModel,
                                onVideoClick = { request -> navigateToHomeVideoInNavigation3(request) },
                                onSearchClick = { pushNavigation3Key(BiliPaiNavKey.Search) },
                                onAvatarClick = { pushNavigation3Key(BiliPaiNavKey.Login) },
                                onProfileClick = { pushNavigation3Route(ScreenRoutes.Profile.route) },
                                onLogout = {
                                    coroutineScope.launch {
                                        com.android.purebilibili.core.store.TokenManager.clear(context)
                                        com.android.purebilibili.core.util.AnalyticsHelper.syncUserContext(
                                            mid = null,
                                            isVip = false,
                                            privacyModeEnabled = SettingsManager.isPrivacyModeEnabledSync(context)
                                        )
                                        com.android.purebilibili.core.util.AnalyticsHelper.logLogout()
                                        homeViewModel.refresh()
                                    }
                                },
                                onSettingsClick = { pushNavigation3Route(ScreenRoutes.Settings.route) },
                                onDynamicClick = { pushNavigation3Route(ScreenRoutes.Dynamic.route) },
                                onHistoryClick = { pushNavigation3Route(ScreenRoutes.History.route) },
                                onPartitionClick = { pushNavigation3Key(BiliPaiNavKey.Partition) },
                                partitionVideoSourceRoute = ScreenRoutes.Partition.route,
                                onPartitionVideoClick = { video ->
                                    navigateToVideoInNavigation3(
                                        bvid = video.bvid,
                                        cid = video.cid,
                                        coverUrl = video.pic,
                                        initialVertical = video.isVertical,
                                        sourceRoute = ScreenRoutes.Partition.route
                                    )
                                },
                                onLiveClick = { roomId, title, uname ->
                                    pushNavigation3Route(ScreenRoutes.Live.createRoute(roomId, title, uname))
                                },
                                onBangumiClick = { initialType ->
                                    pushNavigation3Route(ScreenRoutes.Bangumi.createRoute(initialType))
                                },
                                onCategoryClick = { tid, name ->
                                    pushNavigation3Route(ScreenRoutes.Category.createRoute(tid, name))
                                },
                                onFavoriteClick = { pushNavigation3Route(ScreenRoutes.Favorite.route) },
                                onLikedVideosClick = { pushNavigation3Route(ScreenRoutes.LikedVideos.route) },
                                onLiveListClick = { pushNavigation3Route(ScreenRoutes.LiveList.route) },
                                onWatchLaterClick = { pushNavigation3Route(ScreenRoutes.WatchLater.route) },
                                onDownloadClick = { pushNavigation3Route(ScreenRoutes.DownloadList.route) },
                                onInboxClick = { pushNavigation3Route(ScreenRoutes.Inbox.route) },
                                onStoryClick = { pushNavigation3Key(BiliPaiNavKey.Story()) },
                                onSpaceClick = { mid ->
                                    pushNavigation3Route(ScreenRoutes.Space.createRoute(mid))
                                },
                                globalHazeState = mainHazeState,
                                isTopLevelActive = currentNavigation3Key == BiliPaiNavKey.MainHost &&
                                    currentBottomNavItem == BottomNavItem.HOME,
                                isReturningFromVideoDetail = navigation3ReturnSession.isReturningFromDetail,
                                isQuickReturningFromVideoDetail = navigation3ReturnSession.isQuickReturnFromDetail,
                                onVideoDetailReturnAnimationConsumed = {
                                    navigation3ReturnSession = navigation3ReturnSession.clearReturning()
                                }
                            )
                        BiliPaiNavEntryContentRole.HISTORY -> {
                                val historyViewModel: HistoryViewModel = viewModel()
                                val historyNavigationScope = rememberCoroutineScope()
                                androidx.compose.runtime.LaunchedEffect(
                                    historyViewModel,
                                    isBottomPagerPageActive
                                ) {
                                    if (isBottomPagerPageActive) {
                                        historyViewModel.loadData(showLoading = historyViewModel.uiState.value.items.isEmpty())
                                    }
                                }
                                CommonListScreen(
                                    viewModel = historyViewModel,
                                    onBack = { performSystemBackAction() },
                                    globalHazeState = mainHazeState,
                                    scrollToTopChannel = historyScrollChannel,
                                    onUpClick = { mid -> pushNavigation3Route(ScreenRoutes.Space.createRoute(mid)) },
                                    onVideoClick = { lookupKey, cid, cover, isVertical ->
                                        val historyItem = historyViewModel.getHistoryItem(lookupKey)
                                        val resolvedCid = resolveHistoryPlaybackCid(
                                            clickedCid = cid,
                                            historyItem = historyItem
                                        )
                                        val resumePositionMs = resolveHistoryResumePositionMs(historyItem)
                                        when (resolveHistoryNavigationKind(historyItem)) {
                                            HistoryNavigationKind.PGC -> {
                                                if (historyItem != null && historyItem.epid > 0 && historyItem.seasonId > 0) {
                                                    pushNavigation3Route(ScreenRoutes.BangumiPlayer.createRoute(historyItem.seasonId, historyItem.epid))
                                                } else if (historyItem != null && (historyItem.seasonId > 0 || historyItem.epid > 0)) {
                                                    pushNavigation3Route(ScreenRoutes.BangumiDetail.createRoute(historyItem.seasonId, historyItem.epid))
                                                } else {
                                                    navigateToVideoInNavigation3(
                                                        lookupKey,
                                                        resolvedCid,
                                                        cover,
                                                        resumePositionMs = resumePositionMs,
                                                        initialVertical = isVertical
                                                    )
                                                }
                                            }
                                            HistoryNavigationKind.LIVE -> {
                                                if (historyItem != null && historyItem.roomId > 0) {
                                                    pushNavigation3Route(
                                                        ScreenRoutes.Live.createRoute(
                                                            historyItem.roomId,
                                                            historyItem.videoItem.title,
                                                            historyItem.videoItem.owner.name
                                                        )
                                                    )
                                                } else {
                                                    navigateToVideoInNavigation3(
                                                        lookupKey,
                                                        resolvedCid,
                                                        cover,
                                                        resumePositionMs = resumePositionMs,
                                                        initialVertical = isVertical
                                                    )
                                                }
                                            }
                                            HistoryNavigationKind.ARTICLE -> {
                                                val articleId = historyItem?.videoItem?.id ?: 0L
                                                val articleTitle = historyItem?.videoItem?.title.orEmpty()
                                                if (articleId > 0L) {
                                                    historyNavigationScope.launch {
                                                        when (val target = resolveArticleNavigationTarget(articleId)) {
                                                            is ArticleNavigationTarget.NativeDynamic -> {
                                                                pushNavigation3Route(ScreenRoutes.DynamicDetail.createRoute(target.dynamicId))
                                                            }
                                                            is ArticleNavigationTarget.NativeArticle -> {
                                                                pushNavigation3Route(
                                                                    ScreenRoutes.ArticleDetail.createRoute(target.articleId, articleTitle)
                                                                )
                                                            }
                                                            null -> {
                                                                pushNavigation3Route(
                                                                    ScreenRoutes.ArticleDetail.createRoute(articleId, articleTitle)
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    navigateToVideoInNavigation3(
                                                        lookupKey,
                                                        resolvedCid,
                                                        cover,
                                                        resumePositionMs = resumePositionMs,
                                                        initialVertical = isVertical
                                                    )
                                                }
                                            }
                                            HistoryNavigationKind.VIDEO -> {
                                                navigateToVideoInNavigation3(
                                                    lookupKey,
                                                    resolvedCid,
                                                    cover,
                                                    resumePositionMs = resumePositionMs,
                                                    initialVertical = isVertical
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.DYNAMIC -> DynamicScreen(
                            isCurrentPage = isBottomPagerPageActive,
                            onVideoClick = { bvid -> navigateToVideoInNavigation3(bvid, 0L, "") },
                            onBangumiClick = { seasonId, epId ->
                                if (seasonId > 0L || epId > 0L) {
                                    pushNavigation3Route(ScreenRoutes.BangumiDetail.createRoute(seasonId, epId))
                                }
                            },
                            onDynamicDetailClick = { dynamicId ->
                                pushNavigation3Route(ScreenRoutes.DynamicDetail.createRoute(dynamicId))
                            },
                            onUserClick = { mid -> pushNavigation3Route(ScreenRoutes.Space.createRoute(mid)) },
                            onLiveClick = { roomId, title, uname ->
                                pushNavigation3Route(ScreenRoutes.Live.createRoute(roomId, title, uname))
                            },
                            onBack = { pushNavigation3Route(ScreenRoutes.Home.route) },
                            onLoginClick = { pushNavigation3Key(BiliPaiNavKey.Login) },
                            onHomeClick = { pushNavigation3Route(ScreenRoutes.Home.route) },
                            globalHazeState = mainHazeState
                        )
                        BiliPaiNavEntryContentRole.SEARCH -> {
                            val homeState by homeViewModel.uiState.collectAsStateWithLifecycle(
                                context = kotlin.coroutines.EmptyCoroutineContext
                            )
                            SearchScreen(
                                userFace = homeState.user.face,
                                initialKeyword = effectiveInitialSearchKeyword.orEmpty(),
                                onInitialKeywordConsumed = consumeInitialSearchKeyword,
                                entryMotionSource = searchEntryMotionSource,
                                entryMotionKey = searchEntryMotionKey,
                                onEntryMotionConsumed = { consumedKey ->
                                    if (consumedKey == searchEntryMotionKey) {
                                        searchEntryMotionSource = SearchEntryMotionSource.NONE
                                    }
                                },
                                isReturningFromVideoDetail = navigation3ReturnSession.isReturningFromDetail,
                                isQuickReturningFromVideoDetail =
                                    navigation3ReturnSession.isQuickReturnFromDetail,
                                onVideoDetailReturnAnimationConsumed = {
                                    navigation3ReturnSession = navigation3ReturnSession.clearReturning()
                                },
                                onBack = { performSystemBackAction() },
                                onOpenTrending = { pushNavigation3Key(BiliPaiNavKey.SearchTrending) },
                                onVideoClick = { bvid, cid ->
                                    navigateToVideoInNavigation3(
                                        bvid = bvid,
                                        cid = cid,
                                        coverUrl = "",
                                        sourceRoute = ScreenRoutes.Search.route
                                    )
                                },
                                onWebClick = { url, title ->
                                    pushNavigation3Route(ScreenRoutes.Web.createRoute(url, title))
                                },
                                onUpClick = { mid -> pushNavigation3Route(ScreenRoutes.Space.createRoute(mid)) },
                                onBangumiClick = { seasonId ->
                                    if (seasonId > 0L) {
                                        pushNavigation3Route(ScreenRoutes.BangumiDetail.createRoute(seasonId))
                                    }
                                },
                                onLiveClick = { roomId, title, uname ->
                                    pushNavigation3Route(ScreenRoutes.Live.createRoute(roomId, title, uname))
                                },
                                onTopicClick = { topicId ->
                                    if (topicId > 0L) {
                                        pushNavigation3Key(BiliPaiNavKey.TopicDetail(topicId))
                                    }
                                },
                                onArticleClick = { articleId, title ->
                                    coroutineScope.launch {
                                        when (val target = resolveArticleNavigationTarget(articleId)) {
                                            is ArticleNavigationTarget.NativeDynamic -> {
                                                pushNavigation3Route(ScreenRoutes.DynamicDetail.createRoute(target.dynamicId))
                                            }
                                            is ArticleNavigationTarget.NativeArticle -> {
                                                pushNavigation3Route(
                                                    ScreenRoutes.ArticleDetail.createRoute(target.articleId, title)
                                                )
                                            }
                                            null -> Unit
                                        }
                                    }
                                },
                                onAvatarClick = {
                                    if (homeState.user.isLogin) {
                                        pushNavigation3Route(ScreenRoutes.Profile.route)
                                    } else {
                                        pushNavigation3Key(BiliPaiNavKey.Login)
                                    }
                                }
                            )
                        }
                        BiliPaiNavEntryContentRole.SEARCH_TRENDING ->
                            com.android.purebilibili.feature.search.SearchTrendingScreen(
                                onBack = { performSystemBackAction() },
                                onKeywordClick = submitSearchKeywordInNavigation3
                            )
                        BiliPaiNavEntryContentRole.TOPIC_DETAIL -> {
                                val topicKey = key as BiliPaiNavKey.TopicDetail
                                com.android.purebilibili.feature.search.TopicDetailScreen(
                                    topicId = topicKey.topicId,
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid -> navigateToVideoInNavigation3(bvid, 0L, "") },
                                    onBangumiClick = { seasonId, epId ->
                                        if (seasonId > 0L || epId > 0L) {
                                            pushNavigation3Key(
                                                BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId)
                                            )
                                        }
                                    },
                                    onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) },
                                    onLiveClick = { roomId, title, uname ->
                                        pushNavigation3Key(BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname))
                                    },
                                    onDynamicDetailClick = { dynamicId ->
                                        pushNavigation3Key(BiliPaiNavKey.DynamicDetail(dynamicId))
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.PROFILE -> {
                            val navigateFromProfile: (String) -> Unit = { route ->
                                pushNavigation3Route(route)
                            }
                            ProfileScreen(
                                isCurrentPage = isBottomPagerPageActive,
                                onBack = { pushNavigation3Route(ScreenRoutes.Home.route) },
                                onGoToLogin = { pushNavigation3Key(BiliPaiNavKey.Login) },
                                onLogoutSuccess = { homeViewModel.refresh() },
                                onAccountSwitchSuccess = { homeViewModel.refresh() },
                                onSettingsClick = { navigateFromProfile(ScreenRoutes.Settings.route) },
                                onHistoryClick = { navigateFromProfile(ScreenRoutes.History.route) },
                                showHistoryService = shouldShowProfileHistoryService(
                                    visibleBottomBarItems.map { it.name }
                                ),
                                onFavoriteClick = { navigateFromProfile(ScreenRoutes.Favorite.route) },
                                onFavoriteFolderClick = { mediaId, ownerMid, title ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.SeasonSeriesDetail(
                                            type = "favorite",
                                            id = mediaId,
                                            mid = ownerMid,
                                            title = title
                                        )
                                    )
                                },
                                onFollowingClick = { mid -> navigateFromProfile(ScreenRoutes.Following.createRoute(mid)) },
                                onDownloadClick = { navigateFromProfile(ScreenRoutes.DownloadList.route) },
                                onWatchLaterClick = { navigateFromProfile(ScreenRoutes.WatchLater.route) },
                                onInboxClick = { navigateFromProfile(ScreenRoutes.Inbox.route) },
                                onVideoClick = { bvid -> navigateToVideoInNavigation3(bvid, 0L, "") },
                                onBangumiClick = { seasonId, epId ->
                                    if (seasonId > 0L || epId > 0L) {
                                        pushNavigation3Key(
                                            BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId)
                                        )
                                    }
                                },
                                onBangumiMoreClick = { navigateFromProfile(ScreenRoutes.Bangumi.createRoute(1)) },
                                deferImmersiveRenderBudget = bottomPagerRenderBudget.deferProfileImmersiveBackground
                            )
                        }
                        BiliPaiNavEntryContentRole.VIDEO_DETAIL -> {
                            val videoKey = key as BiliPaiNavKey.VideoDetail
                            val activity = context as? android.app.Activity
                            var isNavigatingToAudioMode by remember(videoKey.bvid) { mutableStateOf(false) }
                            val latestNavTopIsVideo by rememberUpdatedState(
                                navigation3BackStack.lastOrNull() is BiliPaiNavKey.VideoDetail
                            )

                            DisposableEffect(videoKey.bvid) {
                                miniPlayerManager?.isNavigatingToVideo = false
                                miniPlayerManager?.resetNavigationFlag()
                                onVideoDetailEnter()
                                onDispose {
                                    val stillInVideoRoute = latestNavTopIsVideo

                                    if (!stillInVideoRoute) {
                                        onVideoDetailExit()
                                    } else {
                                        com.android.purebilibili.core.util.Logger.d(
                                            "AppNavigation",
                                            "Skip onVideoDetailExit because Navigation3 destination is still video"
                                        )
                                    }

                                    if (shouldClearReturningStateWhenDisposingVideoDestination(stillInVideoRoute)) {
                                        navigation3ReturnSession = navigation3ReturnSession.clearReturning()
                                    }

                                    if (
                                        !stillInVideoRoute &&
                                        activity?.isChangingConfigurations != true &&
                                        !isNavigatingToAudioMode
                                    ) {
                                        prepareVideoPlaybackForNavigationExit(videoKey)
                                    }
                                }
                            }

                            VideoDetailScreen(
                                bvid = videoKey.bvid,
                                coverUrl = videoKey.coverUrl,
                                cid = videoKey.cid,
                                onUpClick = { mid -> pushNavigation3Route(ScreenRoutes.Space.createRoute(mid)) },
                                miniPlayerManager = miniPlayerManager,
                                isInPipMode = isInPipMode,
                                isVisible = true,
                                startInFullscreen = videoKey.fullscreen,
                                startAudioFromRoute = videoKey.startAudio,
                                autoEnterPortraitFromRoute = videoKey.autoPortrait,
                                initialVerticalFromRoute = videoKey.initialVertical,
                                resumePositionMsFromRoute = videoKey.resumePositionMs,
                                openCommentRootRpidFromRoute = videoKey.commentRootRpid,
                                openCommentTargetRpidFromRoute = videoKey.commentTargetRpid,
                                sourceRouteForSharedElement = videoKey.sourceRoute,
                                isReturningFromDetail = navigation3ReturnSession.isReturningFromDetail,
                                isQuickReturningFromDetail = navigation3ReturnSession.isQuickReturnFromDetail,
                                onMarkReturningFromDetail = {
                                    markVideoReturnSession()
                                },
                                onClearReturningFromDetail = {
                                    navigation3ReturnSession = navigation3ReturnSession.clearReturning()
                                },
                                transitionEnabled = shouldEnableVideoDetailSharedTransition(
                                    cardTransitionEnabled = cardTransitionEnabled
                                ),
                                transitionEnterDurationMillis = navMotionSpec.slowFadeDurationMillis,
                                onBack = {
                                    popVideoDetailWithSharedReturnState(
                                        videoKey = videoKey,
                                        targetKey = navigation3BackStack.getOrNull(navigation3BackStack.lastIndex - 1)
                                    ) {
                                        navigation3BackStack = popBiliPaiNavKey(navigation3BackStack)
                                    }
                                },
                                onHomeClick = {
                                    popVideoDetailWithSharedReturnState(
                                        videoKey = videoKey,
                                        targetKey = BiliPaiNavKey.MainHost
                                    ) {
                                        // 先把 bottom pager 静默切到 HOME（被详情页遮挡，切换不可见），
                                        // 再 pop 至 MainHost 触发与系统返回相同的横向过渡。
                                        val homeIndex = visibleBottomBarItems.indexOf(BottomNavItem.HOME)
                                        if (homeIndex >= 0) {
                                            mainBottomPagerState.snapToPage(homeIndex)
                                        }
                                        navigation3BackStack = popBiliPaiNavKeyToRoot(navigation3BackStack)
                                    }
                                },
                                onNavigateToAudioMode = {
                                    isNavigatingToAudioMode = true
                                    pushNavigation3Key(
                                        BiliPaiNavKey.AudioMode(
                                            sourceBvid = videoKey.bvid,
                                            sourceCid = videoKey.cid,
                                            sourceResumePositionMs = videoKey.resumePositionMs
                                        )
                                    )
                                },
                                onNavigateToSearch = { pushNavigation3Key(BiliPaiNavKey.Search) },
                                onSearchKeywordClick = submitSearchKeywordInNavigation3,
                                onOpenBilibiliLink = ::openBilibiliLinkInNavigation3,
                                onVideoClick = { vid, options ->
                                    val targetCid = options?.getLong(
                                        com.android.purebilibili.feature.video.screen.VIDEO_NAV_TARGET_CID_KEY
                                    ) ?: 0L
                                    navigateToVideoInNavigation3(
                                        bvid = vid,
                                        cid = targetCid,
                                        coverUrl = "",
                                        sourceRoute = VideoRoute.base
                                    )
                                },
                                onBgmClick = { bgm ->
                                    if (bgm.jumpUrl.isNotEmpty()) {
                                        pushNavigation3Route(ScreenRoutes.Web.createRoute(bgm.jumpUrl, "发现音乐"))
                                        return@VideoDetailScreen
                                    }

                                    val auSid = bgm.musicId.removePrefix("au").toLongOrNull()
                                    if (auSid != null) {
                                        pushNavigation3Key(BiliPaiNavKey.MusicDetail(auSid))
                                    } else if (bgm.musicId.startsWith("MA") && videoKey.cid > 0) {
                                        val title = bgm.musicTitle.ifEmpty { "背景音乐" }
                                        pushNavigation3Key(
                                            BiliPaiNavKey.NativeMusic(title, videoKey.bvid, videoKey.cid)
                                        )
                                    }
                                }
                            )
                        }
                        BiliPaiNavEntryContentRole.ONBOARDING ->
                            com.android.purebilibili.feature.onboarding.OnboardingScreen(
                                onApplySettingsProfile = { profile ->
                                    com.android.purebilibili.feature.onboarding.applyOnboardingSettingsGuidePreset(
                                        context,
                                        profile
                                    )
                                },
                                onFinish = {
                                    welcomePrefs.edit().putBoolean("first_launch_shown", true).apply()
                                    navigation3BackStack = resolveInitialBiliPaiBackStack(
                                        firstRoute = ScreenRoutes.Home.route,
                                        onboardingRequired = false,
                                        openPortraitFeedOnStartup = SettingsManager
                                            .isLaunchToPortraitFeedOnStartupSync(context)
                                    )
                                }
                            )
                        BiliPaiNavEntryContentRole.SETTINGS ->
                            SettingsTabletEntry {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                    onOpenSourceLicensesClick = { pushNavigation3Key(BiliPaiNavKey.OpenSourceLicenses) },
                                    onAppearanceClick = { pushNavigation3Key(BiliPaiNavKey.AppearanceSettings) },
                                    onAnimationClick = { pushNavigation3Key(BiliPaiNavKey.AnimationSettings) },
                                    onPlaybackClick = { pushNavigation3Key(BiliPaiNavKey.PlaybackSettings) },
                                    onPermissionClick = { pushNavigation3Key(BiliPaiNavKey.PermissionSettings) },
                                    onPluginsClick = { pushNavigation3Key(BiliPaiNavKey.PluginsSettings()) },
                                    onSettingsShareClick = { pushNavigation3Key(BiliPaiNavKey.SettingsShare) },
                                    onWebDavBackupClick = { pushNavigation3Key(BiliPaiNavKey.WebDavBackup) },
                                    onNavigateToBottomBarSettings = { pushNavigation3Key(BiliPaiNavKey.BottomBarSettings) },
                                    onTipsClick = { pushNavigation3Key(BiliPaiNavKey.TipsSettings) },
                                    onReplayOnboardingClick = { pushNavigation3Route(ScreenRoutes.Onboarding.route) },
                                    onCategoryClick = { category ->
                                        pushNavigation3Key(BiliPaiNavKey.SettingsCategory(category))
                                    },
                                    onSearchOpen = { pushNavigation3Key(BiliPaiNavKey.SettingsSearch) },
                                    mainHazeState = mainHazeState,
                                )
                            }
                        BiliPaiNavEntryContentRole.SETTINGS_CATEGORY -> {
                            val categoryKey = key as BiliPaiNavKey.SettingsCategory
                            SettingsTabletEntry {
                                SettingsCategoryScreen(
                                    category = categoryKey.category,
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                    onOpenSourceLicensesClick = { pushNavigation3Key(BiliPaiNavKey.OpenSourceLicenses) },
                                    onAppearanceClick = { pushNavigation3Key(BiliPaiNavKey.AppearanceSettings) },
                                    onAnimationClick = { pushNavigation3Key(BiliPaiNavKey.AnimationSettings) },
                                    onPlaybackClick = { pushNavigation3Key(BiliPaiNavKey.PlaybackSettings) },
                                    onPermissionClick = { pushNavigation3Key(BiliPaiNavKey.PermissionSettings) },
                                    onPluginsClick = { pushNavigation3Key(BiliPaiNavKey.PluginsSettings()) },
                                    onSettingsShareClick = { pushNavigation3Key(BiliPaiNavKey.SettingsShare) },
                                    onWebDavBackupClick = { pushNavigation3Key(BiliPaiNavKey.WebDavBackup) },
                                    onNavigateToBottomBarSettings = { pushNavigation3Key(BiliPaiNavKey.BottomBarSettings) },
                                    onTipsClick = { pushNavigation3Key(BiliPaiNavKey.TipsSettings) },
                                    onReplayOnboardingClick = { pushNavigation3Route(ScreenRoutes.Onboarding.route) },
                                    onCategoryClick = { category ->
                                        pushNavigation3Key(BiliPaiNavKey.SettingsCategory(category))
                                    },
                                    onSearchOpen = { pushNavigation3Key(BiliPaiNavKey.SettingsSearch) },
                                    mainHazeState = mainHazeState,
                                )
                            }
                        }
                        BiliPaiNavEntryContentRole.SETTINGS_SEARCH ->
                            SettingsTabletEntry {
                                SettingsSearchScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                    onCategoryClick = { category ->
                                        pushNavigation3Key(BiliPaiNavKey.SettingsCategory(category))
                                    },
                                    onSearchResultClick = { result ->
                                        resolveSettingsSearchNavigation(result)?.let { navKey ->
                                            pushNavigation3Key(navKey)
                                        }
                                    },
                                    mainHazeState = mainHazeState,
                                )
                            }
                        BiliPaiNavEntryContentRole.OPEN_SOURCE_LICENSES ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.OpenSourceLicensesScreen(
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.APPEARANCE_SETTINGS ->
                            SettingsTabletEntry {
                                AppearanceSettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                    onNavigateToIconSettings = { pushNavigation3Key(BiliPaiNavKey.IconSettings) },
                                    onNavigateToAnimationSettings = {
                                        pushNavigation3Key(BiliPaiNavKey.AnimationSettings)
                                    },
                                )
                            }
                        BiliPaiNavEntryContentRole.ICON_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.IconSettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.ANIMATION_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.AnimationSettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.PLAYBACK_SETTINGS ->
                            SettingsTabletEntry {
                                PlaybackSettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.PERMISSION_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.PermissionSettingsScreen(
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.PLUGINS_SETTINGS -> {
                            val pluginsKey = key as BiliPaiNavKey.PluginsSettings
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.PluginsScreen(
                                    onBack = { performSystemBackAction() },
                                    initialImportUrl = pluginsKey.importUrl,
                                    onOpenJsPlugin = { pluginId ->
                                        pushNavigation3Key(BiliPaiNavKey.JsPluginContent(pluginId))
                                    }
                                )
                            }
                        }
                        BiliPaiNavEntryContentRole.JS_PLUGIN_CONTENT -> {
                            val jsPluginKey = key as BiliPaiNavKey.JsPluginContent
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.plugin.js.BiliPaiJsPluginContentScreen(
                                    pluginId = jsPluginKey.pluginId,
                                    onBack = { performSystemBackAction() },
                                    onPlayMedia = { launchId ->
                                        pushNavigation3Key(BiliPaiNavKey.ExternalMedia(launchId))
                                    }
                                )
                            }
                        }
                        BiliPaiNavEntryContentRole.EXTERNAL_MEDIA -> {
                            val mediaKey = key as BiliPaiNavKey.ExternalMedia
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.plugin.js.ExternalMediaPlayerScreen(
                                    launchId = mediaKey.launchId,
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        }
                        BiliPaiNavEntryContentRole.BOTTOM_BAR_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.BottomBarSettingsScreen(
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.SETTINGS_SHARE -> {
                            val settingsShareViewModel: SettingsShareViewModel = viewModel(
                                factory = remember(application) { SettingsShareViewModelFactory(application) }
                            )
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.share.SettingsShareScreen(
                                    onBack = { performSystemBackAction() },
                                    viewModel = settingsShareViewModel
                                )
                            }
                        }
                        BiliPaiNavEntryContentRole.WEB_DAV_BACKUP -> {
                            val webDavBackupViewModel: WebDavBackupViewModel = viewModel(
                                factory = remember(application) { WebDavBackupViewModelFactory(application) }
                            )
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.webdav.WebDavBackupScreen(
                                    onBack = { performSystemBackAction() },
                                    viewModel = webDavBackupViewModel
                                )
                            }
                        }
                        BiliPaiNavEntryContentRole.TIPS_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.TipsSettingsScreen(
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.WATCH_LATER -> {
                                val watchLaterViewModel: com.android.purebilibili.feature.watchlater.WatchLaterViewModel = viewModel()
                                androidx.compose.runtime.LaunchedEffect(
                                    watchLaterViewModel,
                                    isBottomPagerPageActive
                                ) {
                                    if (isBottomPagerPageActive) {
                                        watchLaterViewModel.loadData(
                                            showLoading = watchLaterViewModel.uiState.value.items.isEmpty()
                                        )
                                    }
                                }
                                com.android.purebilibili.feature.watchlater.WatchLaterScreen(
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid, cid, resumePositionMs ->
                                        navigateToVideoInNavigation3(bvid, cid, "", resumePositionMs = resumePositionMs)
                                    },
                                    onPlayAllAudioClick = { bvid, cid, resumePositionMs ->
                                        navigateToVideoInNavigation3(
                                            bvid,
                                            cid,
                                            "",
                                            startAudio = true,
                                            resumePositionMs = resumePositionMs
                                        )
                                    },
                                    viewModel = watchLaterViewModel,
                                    globalHazeState = mainHazeState
                                )
                            }
                        BiliPaiNavEntryContentRole.FOLLOWING -> {
                                val followingKey = key as BiliPaiNavKey.Following
                                com.android.purebilibili.feature.following.FollowingListScreen(
                                    mid = followingKey.mid,
                                    onBack = { performSystemBackAction() },
                                    onUserClick = { userMid -> pushNavigation3Key(BiliPaiNavKey.Space(userMid)) }
                                )
                            }
                        BiliPaiNavEntryContentRole.DOWNLOAD_LIST ->
                            com.android.purebilibili.feature.download.DownloadListScreen(
                                onBack = { performSystemBackAction() },
                                onVideoClick = { bvid -> navigateToVideoInNavigation3(bvid, 0L, "") },
                                onOfflineVideoClick = { taskId ->
                                    pushNavigation3Key(BiliPaiNavKey.OfflineVideoPlayer(taskId))
                                }
                            )
                        BiliPaiNavEntryContentRole.OFFLINE_VIDEO_PLAYER -> {
                                val offlineVideoKey = key as BiliPaiNavKey.OfflineVideoPlayer
                                com.android.purebilibili.feature.download.OfflineVideoPlayerScreen(
                                    taskId = offlineVideoKey.taskId,
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.LIVE_LIST ->
                            com.android.purebilibili.feature.live.LiveListScreen(
                                onBack = { performSystemBackAction() },
                                onLiveClick = { roomId, title, uname ->
                                    pushNavigation3Key(BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname))
                                },
                                onSearchClick = { pushNavigation3Key(BiliPaiNavKey.LiveSearch) },
                                onAreaListClick = { pushNavigation3Key(BiliPaiNavKey.LiveArea) },
                                onFollowingClick = { pushNavigation3Key(BiliPaiNavKey.LiveFollowing) },
                                onAreaDetailClick = { parentAreaId, areaId, title ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.LiveAreaDetail(
                                            parentAreaId = parentAreaId,
                                            areaId = areaId,
                                            title = title
                                        )
                                    )
                                },
                                globalHazeState = mainHazeState
                            )
                        BiliPaiNavEntryContentRole.LIVE_SEARCH ->
                            com.android.purebilibili.feature.live.LiveSearchScreen(
                                onBack = { performSystemBackAction() },
                                onLiveClick = { roomId, title, uname ->
                                    pushNavigation3Key(BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname))
                                },
                                onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) }
                            )
                        BiliPaiNavEntryContentRole.LIVE_AREA ->
                            com.android.purebilibili.feature.live.LiveAreaScreen(
                                onBack = { performSystemBackAction() },
                                onAreaClick = { parentAreaId, areaId, title ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.LiveAreaDetail(
                                            parentAreaId = parentAreaId,
                                            areaId = areaId,
                                            title = title
                                        )
                                    )
                                }
                            )
                        BiliPaiNavEntryContentRole.LIVE_AREA_DETAIL -> {
                                val liveAreaDetailKey = key as BiliPaiNavKey.LiveAreaDetail
                                com.android.purebilibili.feature.live.LiveAreaDetailScreen(
                                    parentAreaId = liveAreaDetailKey.parentAreaId,
                                    areaId = liveAreaDetailKey.areaId,
                                    title = liveAreaDetailKey.title,
                                    onBack = { performSystemBackAction() },
                                    onAreaClick = { parentAreaId, areaId, title ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.LiveAreaDetail(
                                                parentAreaId = parentAreaId,
                                                areaId = areaId,
                                                title = title
                                            )
                                        )
                                    },
                                    onLiveClick = { roomId, title, uname ->
                                        pushNavigation3Key(BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname))
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.LIVE_FOLLOWING ->
                            com.android.purebilibili.feature.live.LiveFollowingScreen(
                                onBack = { performSystemBackAction() },
                                onLiveClick = { roomId, title, uname ->
                                    pushNavigation3Key(BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname))
                                }
                            )
                        BiliPaiNavEntryContentRole.INBOX ->
                            com.android.purebilibili.feature.message.InboxScreen(
                                onBack = { performSystemBackAction() },
                                onTopItemClick = { destination ->
                                    when (destination) {
                                        com.android.purebilibili.feature.message.MessageCenterDestination.ReplyMe ->
                                            pushNavigation3Key(BiliPaiNavKey.ReplyMe)
                                        com.android.purebilibili.feature.message.MessageCenterDestination.AtMe ->
                                            pushNavigation3Key(BiliPaiNavKey.AtMe)
                                        com.android.purebilibili.feature.message.MessageCenterDestination.LikeMe ->
                                            pushNavigation3Key(BiliPaiNavKey.LikeMe)
                                        com.android.purebilibili.feature.message.MessageCenterDestination.SystemNotice ->
                                            pushNavigation3Key(BiliPaiNavKey.SystemNotice)
                                    }
                                },
                                onSessionClick = { talkerId, sessionType, userName ->
                                    pushNavigation3Key(BiliPaiNavKey.Chat(talkerId, sessionType, userName))
                                }
                            )
                        BiliPaiNavEntryContentRole.REPLY_ME ->
                            com.android.purebilibili.feature.message.feed.ReplyMeScreen(
                                onBack = { performSystemBackAction() },
                                onOpenLink = ::openMessageLinkInNavigation3,
                                onOpenSpace = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) }
                            )
                        BiliPaiNavEntryContentRole.AT_ME ->
                            com.android.purebilibili.feature.message.feed.AtMeScreen(
                                onBack = { performSystemBackAction() },
                                onOpenLink = ::openMessageLinkInNavigation3,
                                onOpenSpace = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) }
                            )
                        BiliPaiNavEntryContentRole.LIKE_ME ->
                            com.android.purebilibili.feature.message.feed.LikeMeScreen(
                                onBack = { performSystemBackAction() },
                                onOpenLink = ::openMessageLinkInNavigation3,
                                onOpenSpace = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) }
                            )
                        BiliPaiNavEntryContentRole.SYSTEM_NOTICE ->
                            com.android.purebilibili.feature.message.feed.SystemNoticeScreen(
                                onBack = { performSystemBackAction() },
                                onOpenLink = ::openMessageLinkInNavigation3
                            )
                        BiliPaiNavEntryContentRole.CHAT -> {
                                val chatKey = key as BiliPaiNavKey.Chat
                                com.android.purebilibili.feature.message.ChatScreen(
                                    talkerId = chatKey.talkerId,
                                    sessionType = chatKey.sessionType,
                                    userName = chatKey.userName.ifBlank { "用户${chatKey.talkerId}" },
                                    onBack = { performSystemBackAction() },
                                    onNavigateToVideo = { bvid ->
                                        navigateToVideoInNavigation3(bvid, 0L, "")
                                    },
                                    onOpenBilibiliLink = ::openBilibiliLinkInNavigation3
                                )
                            }
                        BiliPaiNavEntryContentRole.FAVORITE -> {
                                val favoriteViewModel: FavoriteViewModel = viewModel()
                                CommonListScreen(
                                    viewModel = favoriteViewModel,
                                    onBack = { performSystemBackAction() },
                                    globalHazeState = mainHazeState,
                                    scrollToTopChannel = favoriteScrollChannel,
                                    onVideoClick = { bvid, cid, cover, isVertical ->
                                        navigateToVideoInNavigation3(
                                            bvid = bvid,
                                            cid = cid,
                                            coverUrl = cover,
                                            initialVertical = isVertical,
                                            sourceRoute = ScreenRoutes.Favorite.route
                                        )
                                    },
                                    onFavoriteFolderClick = { mediaId, ownerMid, title, ownerName ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.SeasonSeriesDetail(
                                                type = "favorite",
                                                id = mediaId,
                                                mid = ownerMid,
                                                title = title,
                                                ownerName = ownerName
                                            )
                                        )
                                    },
                                    onCollectionClick = { route ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.SeasonSeriesDetail(
                                                type = route.type,
                                                id = route.id,
                                                mid = route.mid,
                                                title = route.title,
                                                ownerName = route.ownerName,
                                                sharedElementTransition = route.sharedElementTransition
                                            )
                                        )
                                    },
                                    onPlayAllAudioClick = { bvid, cid ->
                                        navigateToVideoInNavigation3(bvid, cid, "", startAudio = true)
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.LIKED_VIDEOS -> {
                                val likedVideosViewModel: LikedVideosViewModel = viewModel()
                                CommonListScreen(
                                    viewModel = likedVideosViewModel,
                                    onBack = { performSystemBackAction() },
                                    globalHazeState = mainHazeState,
                                    onVideoClick = { bvid, cid, cover, isVertical ->
                                        navigateToVideoInNavigation3(
                                            bvid = bvid,
                                            cid = cid,
                                            coverUrl = cover,
                                            initialVertical = isVertical
                                        )
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.LOGIN -> LoginScreen(
                                onClose = { performSystemBackAction() },
                                onLoginSuccess = {
                                    performSystemBackAction()
                                    homeViewModel.refresh()
                                }
                            )
                        BiliPaiNavEntryContentRole.STORY -> {
                                val storyKey = key as BiliPaiNavKey.Story
                                com.android.purebilibili.feature.story.StoryScreen(
                                    seedBvid = storyKey.seedBvid,
                                    seedCid = storyKey.seedCid,
                                    seedCover = storyKey.seedCover,
                                    seedTitle = storyKey.seedTitle,
                                    isActive = true,
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid, cid, _ -> navigateToVideoInNavigation3(bvid, cid, "") },
                                    onUserClick = { mid -> pushNavigation3Route(ScreenRoutes.Space.createRoute(mid)) },
                                    onSearchClick = { pushNavigation3Key(BiliPaiNavKey.Search) }
                                )
                            }
                        BiliPaiNavEntryContentRole.AUDIO_MODE -> {
                                val audioModeKey = key as BiliPaiNavKey.AudioMode
                                val viewModel: com.android.purebilibili.feature.video.viewmodel.PlayerViewModel =
                                    viewModel()
                                DisposableEffect(Unit) {
                                    onAudioModeEnter()
                                    onDispose {
                                        onAudioModeExit()
                                    }
                                }
                                val initialLoadRequest = resolveAudioModeInitialLoadRequest(
                                    key = audioModeKey,
                                    hasDisplayState = false
                                )
                                com.android.purebilibili.feature.video.screen.AudioModeScreen(
                                    viewModel = viewModel,
                                    onBack = { performSystemBackAction() },
                                    onVideoModeClick = { currentBvid, currentCid ->
                                        navigation3BackStack = popBiliPaiNavKey(navigation3BackStack)
                                        navigateToVideoInNavigation3(currentBvid, currentCid, "")
                                    },
                                    isInPipMode = isInPipMode,
                                    initialBvid = initialLoadRequest?.bvid.orEmpty(),
                                    initialCid = initialLoadRequest?.cid ?: 0L,
                                    initialResumePositionMs = initialLoadRequest?.resumePositionMs ?: 0L
                                )
                            }
                        BiliPaiNavEntryContentRole.PARTITION -> com.android.purebilibili.feature.partition.PartitionScreen(
                                onBack = { performSystemBackAction() },
                                onVideoClick = { bvid, cid, cover ->
                                    navigateToVideoInNavigation3(
                                        bvid = bvid,
                                        cid = cid,
                                        coverUrl = cover,
                                        sourceRoute = ScreenRoutes.Partition.route
                                    )
                                },
                                onBangumiClick = { initialType ->
                                    pushNavigation3Route(ScreenRoutes.Bangumi.createRoute(initialType))
                                }
                            )
                        BiliPaiNavEntryContentRole.CATEGORY -> {
                                val categoryKey = key as BiliPaiNavKey.Category
                                com.android.purebilibili.feature.category.CategoryScreen(
                                    tid = categoryKey.tid,
                                    name = categoryKey.name,
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid, cid, cover, isVertical ->
                                        navigateToVideoInNavigation3(
                                            bvid = bvid,
                                            cid = cid,
                                            coverUrl = cover,
                                            initialVertical = isVertical,
                                            sourceRoute = categoryKey.toLegacyRoute()
                                        )
                                    },
                                    isReturningFromVideoDetail = navigation3ReturnSession.isReturningFromDetail,
                                    isQuickReturningFromVideoDetail =
                                        navigation3ReturnSession.isQuickReturnFromDetail
                                )
                            }
                        BiliPaiNavEntryContentRole.SEASON_SERIES_DETAIL -> {
                                val seasonSeriesKey = key as BiliPaiNavKey.SeasonSeriesDetail
                                val viewModel: com.android.purebilibili.feature.space.SeasonSeriesDetailViewModel =
                                    viewModel()
                                LaunchedEffect(
                                    seasonSeriesKey.type,
                                    seasonSeriesKey.id,
                                    seasonSeriesKey.ownerName
                                ) {
                                    viewModel.init(
                                        seasonSeriesKey.type,
                                        seasonSeriesKey.id,
                                        seasonSeriesKey.mid,
                                        seasonSeriesKey.title,
                                        seasonSeriesKey.ownerName
                                    )
                                }

                                CompositionLocalProvider(
                                    LocalVideoCardSharedElementSourceRoute provides seasonSeriesKey.toLegacyRoute()
                                ) {
                                    CommonListScreen(
                                        viewModel = viewModel,
                                        onBack = { performSystemBackAction() },
                                        favoriteCollectionSharedElementRoute = FavoriteCollectionRoute(
                                            type = seasonSeriesKey.type,
                                            id = seasonSeriesKey.id,
                                            mid = seasonSeriesKey.mid,
                                            title = seasonSeriesKey.title,
                                            ownerName = seasonSeriesKey.ownerName,
                                            sharedElementTransition = seasonSeriesKey.sharedElementTransition
                                        ),
                                        onVideoClick = { bvid, cid, cover, isVertical ->
                                            navigateToVideoInNavigation3(
                                                bvid = bvid,
                                                cid = cid,
                                                coverUrl = cover,
                                                initialVertical = isVertical,
                                                sourceRoute = seasonSeriesKey.toLegacyRoute()
                                            )
                                        },
                                        onCollectionClick = { route ->
                                            pushNavigation3Key(
                                                BiliPaiNavKey.SeasonSeriesDetail(
                                                    type = route.type,
                                                    id = route.id,
                                                    mid = route.mid,
                                                    title = route.title,
                                                    ownerName = route.ownerName,
                                                    sharedElementTransition = route.sharedElementTransition
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        BiliPaiNavEntryContentRole.BANGUMI -> {
                                val bangumiKey = key as BiliPaiNavKey.Bangumi
                                com.android.purebilibili.feature.bangumi.BangumiScreen(
                                    onBack = { performSystemBackAction() },
                                    onBangumiClick = { seasonId ->
                                        pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = seasonId))
                                    },
                                    onBangumiEpisodeClick = { seasonId, epId ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.BangumiDetail(
                                                seasonId = seasonId,
                                                epId = epId
                                            )
                                        )
                                    },
                                    initialType = bangumiKey.initialType
                                )
                            }
                        BiliPaiNavEntryContentRole.BANGUMI_PLAYER -> {
                                val playerKey = key as BiliPaiNavKey.BangumiPlayer
                                com.android.purebilibili.feature.bangumi.BangumiPlayerScreen(
                                    seasonId = playerKey.seasonId,
                                    epId = playerKey.epId,
                                    resumePositionMs = playerKey.resumePositionMs,
                                    onBack = { performSystemBackAction() },
                                    onNavigateToLogin = { pushNavigation3Key(BiliPaiNavKey.Login) }
                                )
                            }
                        BiliPaiNavEntryContentRole.MUSIC_DETAIL -> {
                                val musicKey = key as BiliPaiNavKey.MusicDetail
                                com.android.purebilibili.feature.audio.screen.MusicDetailScreen(
                                    sid = musicKey.sid,
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.NATIVE_MUSIC -> {
                                val nativeMusicKey = key as BiliPaiNavKey.NativeMusic
                                com.android.purebilibili.feature.audio.screen.MusicDetailScreen(
                                    musicTitle = nativeMusicKey.title.ifEmpty { "背景音乐" },
                                    bvid = nativeMusicKey.bvid,
                                    cid = nativeMusicKey.cid,
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.SPACE -> {
                                val spaceKey = key as BiliPaiNavKey.Space
                                com.android.purebilibili.feature.space.SpaceScreen(
                                    mid = spaceKey.mid,
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid, resumePositionMs ->
                                        navigateToVideoInNavigation3(
                                            bvid,
                                            0L,
                                            "",
                                            resumePositionMs = resumePositionMs
                                        )
                                    },
                                    onAudioClick = { sid ->
                                        pushNavigation3Key(BiliPaiNavKey.MusicDetail(sid))
                                    },
                                    onBangumiClick = { seasonId ->
                                        if (seasonId > 0L) {
                                            pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = seasonId))
                                        }
                                    },
                                    onWebClick = { url, title ->
                                        pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                                    },
                                    onPlayAllAudioClick = { bvid, resumePositionMs ->
                                        navigateToVideoInNavigation3(
                                            bvid,
                                            0L,
                                            "",
                                            startAudio = true,
                                            resumePositionMs = resumePositionMs
                                        )
                                    },
                                    onDynamicDetailClick = { dynamicId ->
                                        pushNavigation3Key(BiliPaiNavKey.DynamicDetail(dynamicId))
                                    },
                                    onArticleClick = { articleId, title ->
                                        if (canNavigate(false)) {
                                            coroutineScope.launch {
                                                when (val target = resolveArticleNavigationTarget(articleId)) {
                                                    is ArticleNavigationTarget.NativeDynamic -> {
                                                        pushNavigation3Key(
                                                            BiliPaiNavKey.DynamicDetail(target.dynamicId)
                                                        )
                                                    }
                                                    is ArticleNavigationTarget.NativeArticle -> {
                                                        pushNavigation3Key(
                                                            BiliPaiNavKey.ArticleDetail(target.articleId, title)
                                                        )
                                                    }
                                                    null -> Unit
                                                }
                                            }
                                        }
                                    },
                                    onViewAllClick = { type, id, mid, title, ownerName ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.SeasonSeriesDetail(
                                                type = type,
                                                id = id,
                                                mid = mid,
                                                title = title,
                                                ownerName = ownerName
                                            )
                                        )
                                    },
                                    sharedTransitionScope = LocalSharedTransitionScope.current,
                                    animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                                )
                            }
                        BiliPaiNavEntryContentRole.WEB -> {
                                val webKey = key as BiliPaiNavKey.Web
                                com.android.purebilibili.feature.web.WebViewScreen(
                                    url = webKey.url,
                                    title = webKey.title.ifEmpty { null },
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid ->
                                        navigation3BackStack = popBiliPaiNavKey(navigation3BackStack)
                                        navigateToVideoInNavigation3(bvid, 0L, "")
                                    },
                                    onSpaceClick = { mid ->
                                        replaceNavigation3TopWithKey(BiliPaiNavKey.Space(mid))
                                    },
                                    onLiveClick = { roomId ->
                                        replaceNavigation3TopWithKey(BiliPaiNavKey.Live(roomId = roomId.toString()))
                                    },
                                    onDynamicClick = { dynamicId ->
                                        replaceNavigation3TopWithKey(BiliPaiNavKey.DynamicDetail(dynamicId))
                                    },
                                    onBangumiClick = { seasonId, epId ->
                                        replaceNavigation3TopWithKey(
                                            BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId)
                                        )
                                    },
                                    onMusicClick = { musicId ->
                                        val auSid = musicId.removePrefix("au").removePrefix("AU").toLongOrNull()
                                        if (auSid != null) {
                                            replaceNavigation3TopWithKey(
                                                BiliPaiNavKey.MusicDetail(auSid)
                                            )
                                        } else {
                                            navigation3BackStack = popBiliPaiNavKey(navigation3BackStack)
                                        }
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.DYNAMIC_DETAIL -> {
                                val dynamicKey = key as BiliPaiNavKey.DynamicDetail
                                CompositionLocalProvider(
                                    LocalVideoCardSharedElementSourceRoute provides dynamicKey.toLegacyRoute()
                                ) {
                                    com.android.purebilibili.feature.dynamic.DynamicDetailScreen(
                                        dynamicId = dynamicKey.dynamicId,
                                        openCommentRootRpid = dynamicKey.commentRootRpid,
                                        openCommentTargetRpid = dynamicKey.commentTargetRpid,
                                        onBack = { performSystemBackAction() },
                                        onVideoClick = { bvid -> navigateToVideoInNavigation3(bvid, 0L, "") },
                                        onBangumiClick = { seasonId, epId ->
                                            pushNavigation3Key(
                                                BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId)
                                            )
                                        },
                                        onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) },
                                        onArticleClick = { articleId, title ->
                                            pushNavigation3Key(
                                                BiliPaiNavKey.ArticleDetail(articleId = articleId, title = title)
                                            )
                                        },
                                        onLiveClick = { roomId, title, uname ->
                                            pushNavigation3Key(
                                                BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname)
                                            )
                                        }
                                    )
                                }
                            }
                        BiliPaiNavEntryContentRole.ARTICLE_DETAIL -> {
                                val articleKey = key as BiliPaiNavKey.ArticleDetail
                                ArticleDetailScreen(
                                    articleId = articleKey.articleId,
                                    initialTitle = articleKey.title,
                                    transitionEnabled = cardTransitionEnabled,
                                    onBack = { useSharedReturn ->
                                        navigation3ReturnSession = if (useSharedReturn) {
                                            navigation3ReturnSession.markReturning(SystemClock.uptimeMillis())
                                        } else {
                                            navigation3ReturnSession.clearReturning()
                                        }
                                        navigation3BackStack = popBiliPaiNavKey(navigation3BackStack)
                                    },
                                    onUserClick = { mid ->
                                        if (mid > 0) {
                                            pushNavigation3Key(BiliPaiNavKey.Space(mid))
                                        }
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.LIVE -> {
                                val liveKey = key as BiliPaiNavKey.Live
                                val activity = context as? android.app.Activity
                                DisposableEffect(liveKey.roomId, miniPlayerManager) {
                                    onDispose {
                                        val isChangingConfigurations = activity?.isChangingConfigurations == true
                                        if (shouldStopLivePlaybackOnRouteDispose(isChangingConfigurations)) {
                                            miniPlayerManager?.markLeavingByNavigation(forceStop = true)
                                        }
                                    }
                                }

                                com.android.purebilibili.feature.live.LivePlayerScreen(
                                    roomId = liveKey.roomId,
                                    title = liveKey.title,
                                    uname = liveKey.uname,
                                    siteId = liveKey.siteId,
                                    onBack = {
                                        miniPlayerManager?.markLeavingByNavigation(forceStop = true)
                                        performSystemBackAction()
                                    },
                                    onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) }
                                )
                            }
                        BiliPaiNavEntryContentRole.BANGUMI_DETAIL -> {
                                val bangumiKey = key as BiliPaiNavKey.BangumiDetail
                                com.android.purebilibili.feature.bangumi.BangumiDetailScreen(
                                    seasonId = bangumiKey.seasonId,
                                    epId = bangumiKey.epId,
                                    onBack = { performSystemBackAction() },
                                    onEpisodeClick = { actionSeasonId, episode ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.BangumiPlayer(
                                                seasonId = actionSeasonId,
                                                epId = episode.id
                                            )
                                        )
                                    },
                                    onSeasonClick = { newSeasonId ->
                                        replaceNavigation3TopWithKey(
                                            BiliPaiNavKey.BangumiDetail(seasonId = newSeasonId)
                                        )
                                    }
                                )
                            }
                        }
                    }

                BiliPaiNavDisplayHost(
                    backStack = navigation3BackStack,
                    cardTransitionEnabled = cardTransitionEnabled,
                    predictiveBackEnabled = predictiveBackEnabled,
                    predictiveBackAnimationStyle = predictiveBackAnimationStyle,
                    predictiveBackExitDirectionOverride = predictiveBackExitDirection,
                    sourceMetadata = navigation3SourceMetadata,
                    onBack = { performSystemBackAction() },
                    modifier = Modifier.fillMaxSize(),
                    sharedTransitionScope = LocalSharedTransitionScope.current,
                    visibleBottomBarRoutes = visibleBottomBarRoutes,
                    activeMainHostRoute = activeBottomTabRoute,
                    isLightBackground = isLightBackground,
                ) { key ->
                    VideoCardTransitionBackgroundRouteContent(key) {
                        RenderNavigationContent(key)
                    }
                }
                }
            } // End of Content Box
            } // End of Row

            if (bottomBarMountGate && bottomBarVisibilityMode != SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN) {
                val bottomBarModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)

                Box(modifier = bottomBarModifier) {
                    AnimatedVisibility(
                        visible = finalBottomBarVisible,
                        enter = slideInVertically(
                            animationSpec = softLandingSpring(),
                            initialOffsetY = { it }
                        ) + fadeIn(animationSpec = emphasizedEnterTween(navMotionSpec.slowFadeDurationMillis)) +
                            scaleIn(
                                animationSpec = softLandingSpring(),
                                initialScale = 0.96f,
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            ),
                        exit = slideOutVertically(
                            animationSpec = emphasizedExitTween(navMotionSpec.fastFadeDurationMillis),
                            targetOffsetY = { it }
                        ) + fadeOut(animationSpec = emphasizedExitTween(navMotionSpec.fastFadeDurationMillis)) +
                            scaleOut(
                                animationSpec = emphasizedExitTween(navMotionSpec.fastFadeDurationMillis),
                                targetScale = 0.92f,
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            )
                    ) {
                        if (isBottomBarFloating) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                FrostedBottomBar(
                                    currentItem = currentBottomNavItem,
                                    onItemClick = handleNavItemClick,
                                    onHomeDoubleTap = { homeScrollChannel.trySend(Unit) },
                                    onDynamicDoubleTap = { dynamicScrollChannel.trySend(Unit) },
                                    onSearchClick = { requestSearchFromBottomBar() },
                                    onSearchKeywordSubmit = submitSearchKeywordInNavigation3,
                                    searchLaunchKey = bottomBarSearchLaunchKey,
                                    hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                                    isFloating = true,
                                    labelMode = bottomBarLabelMode,
                                    visibleItems = visibleBottomBarItems,
                                    itemColorIndices = bottomBarItemColors,
                                    dynamicUnreadCount = dynamicUnreadCount,
                                    homeSettings = effectiveHomeSettings,
                                    miuixBackdrop = bottomBarBackdrop,
                                    motionTier = com.android.purebilibili.core.ui.adaptive.MotionTier.Normal,
                                    isTransitionRunning = bottomPagerRenderBudget.isTransitionRunning,
                                    forceLowBlurBudget = bottomPagerRenderBudget.forceLowBlurBudget,
                                    isFeedScrollInProgress = currentBottomNavItem == BottomNavItem.HOME &&
                                        homeFeedScrollInProgressState.value,
                                    uiSkinDecoration = bottomBarUiSkinDecoration,
                                    onToggleSidebar = {
                                        coroutineScope.launch {
                                            SettingsManager.setTabletUseSidebar(context, true)
                                        }
                                    }
                                )
                            }
                        } else {
                            FrostedBottomBar(
                                currentItem = currentBottomNavItem,
                                onItemClick = handleNavItemClick,
                                onHomeDoubleTap = { homeScrollChannel.trySend(Unit) },
                                onDynamicDoubleTap = { dynamicScrollChannel.trySend(Unit) },
                                onSearchClick = { requestSearchFromBottomBar() },
                                onSearchKeywordSubmit = submitSearchKeywordInNavigation3,
                                searchLaunchKey = bottomBarSearchLaunchKey,
                                hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                                isFloating = false,
                                labelMode = bottomBarLabelMode,
                                visibleItems = visibleBottomBarItems,
                                itemColorIndices = bottomBarItemColors,
                                dynamicUnreadCount = dynamicUnreadCount,
                                homeSettings = effectiveHomeSettings,
                                miuixBackdrop = bottomBarBackdrop,
                                motionTier = com.android.purebilibili.core.ui.adaptive.MotionTier.Normal,
                                isTransitionRunning = bottomPagerRenderBudget.isTransitionRunning,
                                forceLowBlurBudget = bottomPagerRenderBudget.forceLowBlurBudget,
                                isFeedScrollInProgress = currentBottomNavItem == BottomNavItem.HOME &&
                                    homeFeedScrollInProgressState.value,
                                uiSkinDecoration = bottomBarUiSkinDecoration,
                                onToggleSidebar = {
                                    coroutineScope.launch {
                                        SettingsManager.setTabletUseSidebar(context, true)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (predictiveBackEnabled) {
                MainHostTabBackHandler(
                    enabled = shouldInterceptTabBack,
                    onReturnToHomeTab = {
                        val homeIndex = visibleBottomBarItems.indexOf(BottomNavItem.HOME)
                        if (homeIndex >= 0) {
                            mainBottomPagerState.snapToPage(homeIndex)
                        }
                    },
                )
            } else if (shouldUseClassicBackHandler) {
                BackHandler { performSystemBackAction() }
            }

            if (showLaunchDisclaimer) {
                ReleaseChannelDisclaimerDialog(
                    title = "首次使用声明",
                    onDismiss = {
                        showLaunchDisclaimer = false
                        welcomePrefs.edit().putBoolean(RELEASE_DISCLAIMER_ACK_KEY, true).apply()
                    },
                    onOpenGithub = { uriHandler.openUri(OFFICIAL_GITHUB_URL) },
                    onOpenTelegram = { uriHandler.openUri(OFFICIAL_TELEGRAM_URL) }
                )
            }

            ImagePreviewOverlayHost(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            )
        } // End of Main Box
        } // End of CompositionLocalProvider
    }
}
}
