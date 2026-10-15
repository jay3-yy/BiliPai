// 文件路径: navigation/AppNavigation.kt
package com.android.purebilibili.navigation

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.android.purebilibili.feature.home.components.cards.WallpaperPaletteRequest
import com.android.purebilibili.feature.home.components.cards.WallpaperPaletteResult
import com.android.purebilibili.feature.home.components.cards.WallpaperPaletteStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue //  新增
import androidx.compose.runtime.LaunchedEffect // 新增
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.purebilibili.feature.article.ArticleDetailScreen
import com.android.purebilibili.feature.article.shouldUseArticleNoOpRouteTransition
import com.android.purebilibili.feature.audio.library.resolveListenVideoPlaybackSelection
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.audio.screen.AudioNowPlayingBar
import com.android.purebilibili.feature.audio.screen.AudioNowPlayingBarPresenceHost
import com.android.purebilibili.feature.audio.screen.AudioNowPlayingBarState
import com.android.purebilibili.feature.audio.screen.ListenVideoRoute
import com.android.purebilibili.feature.audio.screen.isAudioNowPlayingPlayerDestination
import com.android.purebilibili.feature.audio.screen.resolveAudioNowPlayingVisible
import com.android.purebilibili.feature.home.components.LinkedDockNowPlayingSlot
import com.android.purebilibili.feature.home.components.LinkedDockPhase
import com.android.purebilibili.feature.home.components.resolveLinkedDockPhaseOnAudioChange
import com.android.purebilibili.feature.home.HomeVideoClickRequest
import com.android.purebilibili.feature.home.HomeVideoClickSource
import com.android.purebilibili.feature.home.HomeCategory
import com.android.purebilibili.feature.home.PopularSubCategory
import com.android.purebilibili.feature.home.HomeScreen
import com.android.purebilibili.feature.home.HomeViewModel
import com.android.purebilibili.core.ui.wallpaper.LocalHomeWallpaperSelection
import com.android.purebilibili.core.ui.wallpaper.LocalWallpaperPlaybackCoordinator
import com.android.purebilibili.core.ui.wallpaper.WallpaperPlaybackCoordinator
import com.android.purebilibili.core.store.home.HomeWallpaperSelection
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperUri
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.feature.home.shouldRenderRootHomeWallpaperMedia
import com.android.purebilibili.feature.home.DepthSyncedGlobalHomeWallpaperBackdrop
import com.android.purebilibili.feature.home.resolveHomeWallpaperBackdropAppearance
import com.android.purebilibili.feature.home.resolveHomeWallpaperUri
import com.android.purebilibili.feature.home.shouldExposeGlobalHomeWallpaperChrome
import com.android.purebilibili.feature.home.shouldRenderGlobalHomeWallpaperBackdrop
import com.android.purebilibili.feature.login.LoginScreen
import com.android.purebilibili.feature.profile.ProfileScreen
import com.android.purebilibili.feature.profile.AccountSwitchDialog
import com.android.purebilibili.feature.search.ArticleNavigationTarget
import com.android.purebilibili.feature.search.resolveArticleNavigationTarget
import com.android.purebilibili.feature.search.SearchEntryMotionSource
import com.android.purebilibili.feature.search.SearchScreen
import com.android.purebilibili.feature.settings.SettingsScreen
import com.android.purebilibili.feature.settings.resolveSettingsCategoryNavKey
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
import com.android.purebilibili.feature.onboarding.APP_WELCOME_PREFS_NAME
import com.android.purebilibili.feature.onboarding.USER_AGREEMENT_ACK_KEY
import com.android.purebilibili.feature.onboarding.isUserAgreementRequired
import com.android.purebilibili.feature.settings.RELEASE_DISCLAIMER_ACK_KEY
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
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.dynamic.DynamicScreen
import com.android.purebilibili.feature.dynamic.DynamicScrollRequest
import com.android.purebilibili.feature.dynamic.LocalDynamicScrollChannel
import com.android.purebilibili.feature.dynamic.components.ImagePreviewOverlayHost
import com.android.purebilibili.feature.live.shouldStopLivePlaybackOnRouteDispose
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchRegistry
import com.android.purebilibili.core.util.prefetchHomeCoverImages
import com.android.purebilibili.core.util.resolveHomeCoverReturnPrefetchCandidates
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.resolveShortcutRoute
import com.android.purebilibili.shouldNavigateToVideoFromNotification
import com.android.purebilibili.core.ui.transition.LocalPredictiveBackBackgroundState
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.adaptive.toAdaptiveFoldPosture
import com.android.purebilibili.core.ui.adaptive.HingeOcclusionInputShield
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.LocalVideoTransitionAdaptiveInfo
import com.android.purebilibili.core.ui.transition.NowPlayingBarHandoffState
import com.android.purebilibili.core.ui.transition.VideoTransitionAdaptiveInfo
import com.android.purebilibili.core.ui.transition.rememberVideoCardTransitionClock
import com.android.purebilibili.core.ui.transition.VideoCardTransitionVisualTimeline
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionDurationMillis
import com.android.purebilibili.core.ui.transition.predictiveBackBackgroundEffect
import com.android.purebilibili.core.ui.transition.pinSourcePageDuringSharedTransition
import com.android.purebilibili.core.ui.transition.shouldApplyPredictiveBackBlurToRoute
import com.android.purebilibili.core.ui.transition.shouldApplyVideoCardTransitionBackgroundToRoute
import com.android.purebilibili.core.ui.transition.shouldApplyVideoCardTransitionSnapshotOnRouteShell
import com.android.purebilibili.core.ui.transition.shouldShowVideoCardTransitionSourceChrome
import com.android.purebilibili.core.ui.transition.shouldDriveVideoCardTransitionChromeByProgress
import com.android.purebilibili.core.ui.transition.isVideoCardTransitionBottomBarSource
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionChromeBottomBarRoute
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionChromeReveal
import com.android.purebilibili.core.ui.transition.videoCardTransitionChromeReveal
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionBackgroundScaleReduction
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionBackgroundSource
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.VideoCardTransitionSettleState
import com.android.purebilibili.core.ui.transition.shouldUseHostOwnedVideoCardTransitionSnapshot
import com.android.purebilibili.core.ui.transition.shouldUseRealtimeVideoCardTransitionBackgroundBlur
import com.android.purebilibili.core.ui.transition.videoCardTransitionBackgroundEffect
import com.android.purebilibili.core.ui.transition.videoCardTransitionLiveBackgroundEffect
import androidx.compose.runtime.mutableFloatStateOf
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import com.android.purebilibili.data.model.response.BgmInfo

import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.CompositionLocalProvider
// [LayerBackdrop] miuix-blur 用于全局底栏真实背景折射。
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.LocalSetBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.rememberAppBottomBarContentPadding
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween
import com.android.purebilibili.core.ui.motion.softLandingSpring
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.shouldUseSidebarNavigationForLayout
import com.android.purebilibili.core.plugin.skin.rememberUiSkinState
// import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass (Removed)
// import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass (Removed)
// import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass (Removed)
// import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi (Removed)
import com.android.purebilibili.feature.home.components.FrostedBottomBar
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockEdge
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockVisibility
import com.android.purebilibili.feature.home.components.LiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import com.android.purebilibili.feature.home.components.rememberBottomBarUiSkinDecoration
import com.android.purebilibili.feature.home.components.rememberDynamicPublishSkinDecoration
import com.android.purebilibili.feature.home.components.rememberHomeUiSkinDecoration
import com.android.purebilibili.feature.profile.shouldShowProfileHistoryService
import com.android.purebilibili.core.store.AppNavigationSettings
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.HomeWallpaperEffectScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.navigation.NavigationSettingsStore
import com.android.purebilibili.core.store.resolveEffectiveHomeSettings
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.navigation3.BiliPaiNavDisplayHost
import com.android.purebilibili.navigation3.BiliPaiProgrammaticBackDispatcher
import com.android.purebilibili.navigation3.BiliPaiNavCardSourceDirection
import com.android.purebilibili.navigation3.BiliPaiNavEntryContentRole
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiReturnSessionState
import com.android.purebilibili.navigation3.BiliPaiVideoSource
import com.android.purebilibili.navigation3.VideoCardTransitionSession
import com.android.purebilibili.navigation3.legacyRouteToBiliPaiNavKey
import com.android.purebilibili.navigation3.popBiliPaiNavKey
import com.android.purebilibili.navigation3.popBiliPaiNavKeyToRoot
import com.android.purebilibili.navigation3.pushBiliPaiNavKey
import com.android.purebilibili.navigation3.pushOrReplaceSettingsCategoryNavKey
import com.android.purebilibili.navigation3.resolveBiliPaiBackGestureDecision
import com.android.purebilibili.navigation3.resolveBiliPaiNavCardSourceDirection
import com.android.purebilibili.navigation3.resolveBiliPaiNavEntryContentRole
import com.android.purebilibili.navigation3.resolveRemovedNavigation3SaveableStateKeys
import com.android.purebilibili.navigation3.resolveNavigation3SaveableStateKey
import com.android.purebilibili.navigation3.resolveBiliPaiNavSourceMetadata
import com.android.purebilibili.navigation3.relativeToHost
import com.android.purebilibili.navigation3.shouldBindVideoDetailBackPreviewPlayer
import com.android.purebilibili.navigation3.shouldActivateVideoDetailPlaybackSession
import com.android.purebilibili.navigation3.shouldRecoverVideoPlayerAfterBackCancellation
import com.android.purebilibili.navigation3.resolveBiliPaiVideoSource
import com.android.purebilibili.navigation3.resolveVideoCardTransitionEnabledForSource
import com.android.purebilibili.navigation3.shouldUseMiuixVideoCardMorph
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackExitDirection
import com.android.purebilibili.navigation3.resolveInitialBiliPaiBackStack
import com.android.purebilibili.navigation3.toLegacyRoute
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier // 确保 Modifier 被导入
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.Box // 确保 Box 被导入
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize // 确保 fillMaxSize 被导入
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
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
    const val route = "$base/{bvid}?cid={cid}&cover={cover}&startAudio={startAudio}&autoPortrait={autoPortrait}&fullscreen={fullscreen}&resumePositionMs={resumePositionMs}&commentRootRpid={commentRootRpid}&commentTargetRpid={commentTargetRpid}&initialVertical={initialVertical}&directPortraitEntry={directPortraitEntry}"

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
        initialVertical: Boolean = false,
        directPortraitEntry: Boolean = false,
    ): String {
        val initialVerticalQuery = if (initialVertical) "&initialVertical=true" else ""
        val directPortraitQuery = if (directPortraitEntry) "&directPortraitEntry=true" else ""
        return "$base/$bvid?cid=$cid&cover=$encodedCover&startAudio=$startAudio&autoPortrait=$autoPortrait&fullscreen=$fullscreen&resumePositionMs=${resumePositionMs.coerceAtLeast(0L)}&commentRootRpid=${commentRootRpid.coerceAtLeast(0L)}&commentTargetRpid=${commentTargetRpid.coerceAtLeast(0L)}$initialVerticalQuery$directPortraitQuery"
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
        initialVertical: Boolean = false,
        directPortraitEntry: Boolean = false,
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
            initialVertical = initialVertical,
            directPortraitEntry = directPortraitEntry,
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
    initialVertical: Boolean = false,
    directPortraitEntry: Boolean = false,
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
        initialVertical = initialVertical,
        directPortraitEntry = directPortraitEntry,
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
    mainHazeState: dev.chrisbanes.haze.HazeState? = null, //  全局 Haze 状态
    onBrandFeedbackBottomInsetChanged: (androidx.compose.ui.unit.Dp) -> Unit = {}
) {
    val homeViewModel: HomeViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()
    val videoDetailViewModelOwners = remember {
        mutableMapOf<BiliPaiNavKey.VideoDetail, ViewModelStoreOwner>()
    }
    
    // 单一首页视觉配置源：减少根导航层多路 DataStore 收集导致的全局重组。
    val context = androidx.compose.ui.platform.LocalContext.current
    val windowConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val windowDensity = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.SideEffect {
        CardPositionManager.invalidateVideoSourceIfWindowChanged(
            screenWidth = with(windowDensity) { windowConfiguration.screenWidthDp.dp.toPx() },
            screenHeight = with(windowDensity) { windowConfiguration.screenHeightDp.dp.toPx() },
        )
    }
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
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val effectiveHomeSettings = remember(homeSettings) {
        resolveEffectiveHomeSettings(
            homeSettings = homeSettings,
        )
    }
    val liquidGlassRenderConfig = remember(
        homeSettings.liquidGlassProgress,
        homeSettings.liquidGlassAdvancedSettings,
        homeSettings.liquidGlassReadabilityMode,
        homeSettings.bottomBarLiquidGlassPreset,
    ) {
        LiquidGlassRenderConfig(
            tuning = resolveLiquidGlassTuning(
                progress = homeSettings.liquidGlassProgress,
                advancedSettings = homeSettings.liquidGlassAdvancedSettings,
                readabilityMode = homeSettings.liquidGlassReadabilityMode,
            ),
            preset = homeSettings.bottomBarLiquidGlassPreset,
        )
    }
    val uiSkinState by rememberUiSkinState(context)
    val bottomBarUiSkinDecoration = rememberBottomBarUiSkinDecoration(uiSkinState)
    val homeUiSkinDecoration = rememberHomeUiSkinDecoration(uiSkinState)
    val dynamicPublishSkinDecoration = rememberDynamicPublishSkinDecoration(uiSkinState)
    val appearance = remember(homeSettings) {
        resolveAppNavigationAppearance(
            homeSettings = homeSettings,
        )
    }
    val cardTransitionEnabled = appearance.cardTransitionEnabled
    val videoTransitionRealtimeBlurEnabled by SettingsManager
        .getVideoTransitionRealtimeBlurEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val relatedVideoTransitionEnabled by SettingsManager
        .getRelatedVideoTransitionEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val isBottomBarBlurEnabled = appearance.bottomBarBlurEnabled
    val bottomBarLabelMode = appearance.bottomBarLabelMode
    val isBottomBarFloating = appearance.bottomBarFloating
    val showUpBadges by SettingsManager
        .getHomeUpBadgesVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val showUpAvatars by SettingsManager
        .getHomeUpAvatarsVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)

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
    var searchEntryMotionKey by remember { mutableIntStateOf(0) }
    var bottomBarSearchLaunchKey by remember { mutableIntStateOf(0) }
    var navigation3ReturnSession by remember { mutableStateOf(BiliPaiReturnSessionState()) }
    val effectiveInitialSearchKeyword = inAppSearchKeyword ?: initialSearchKeyword
    val consumeInitialSearchKeyword: (String) -> Unit = { consumedKeyword ->
        if (inAppSearchKeyword == consumedKeyword) {
            inAppSearchKeyword = null
        } else {
            onInitialSearchKeywordConsumed(consumedKeyword)
        }
    }
    // 使用须知：新/老用户只要未确认 user_agreement_ack_v1 都必须进入门禁页
    val welcomePrefs = androidx.compose.runtime.remember {
        context.getSharedPreferences(APP_WELCOME_PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    val userAgreementAcked = welcomePrefs.getBoolean(USER_AGREEMENT_ACK_KEY, false)
    val agreementRequired = isUserAgreementRequired(userAgreementAcked)
    // Read the persisted order before mounting the pager: page 0 is the user's first tab.
    val navigationSettingsFlow = remember(context) {
        kotlinx.coroutines.flow.flow<AppNavigationSettings?> {
            NavigationSettingsStore.ensureListenVideoBottomTabMigration(context)
            SettingsManager.getAppNavigationSettings(context).collect { emit(it) }
        }
    }
    val resolvedAppNavigationSettings by navigationSettingsFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val appNavigationSettings = resolvedAppNavigationSettings ?: return
    val startDestination =
        if (agreementRequired) ScreenRoutes.Onboarding.route else ScreenRoutes.Home.route
    val cachedPortraitStartupRoute = remember(context) {
        SettingsManager.getCachedLaunchToPortraitFeedOnStartup(context)
    }
    val resolvedPortraitStartupRoute by produceState<Boolean?>(
        initialValue = cachedPortraitStartupRoute,
        key1 = context,
    ) {
        if (value == null) {
            value = try {
                SettingsManager.resolveLaunchToPortraitFeedOnStartup(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
    }
    // 缓存缺失时等待 DataStore 首值；先用 false 建栈会让恢复备份后的本次启动进错首页。
    val launchToPortraitFeedOnStartupAtInit = resolvedPortraitStartupRoute ?: return

    val videoSharedTransitionSpeedSettings = remember(
        homeSettings.videoSharedTransitionSpeed,
        homeSettings.videoSharedTransitionCustomDurationMillis,
    ) {
        VideoSharedTransitionSpeedSettings(
            speed = homeSettings.videoSharedTransitionSpeed,
            customDurationMillis = homeSettings.videoSharedTransitionCustomDurationMillis,
        )
    }
    val windowSizeClass = LocalWindowSizeClass.current
    val appWindowAdaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val videoTransitionAdaptiveInfo = remember(windowSizeClass, appWindowAdaptiveInfo) {
        VideoTransitionAdaptiveInfo(
            widthSizeClass = windowSizeClass.widthSizeClass,
            foldPosture = appWindowAdaptiveInfo.posture.toAdaptiveFoldPosture(),
        )
    }
    val videoSharedTransitionDurationMillis = remember(videoSharedTransitionSpeedSettings, videoTransitionAdaptiveInfo) {
        resolveVideoSharedTransitionDurationMillis(videoSharedTransitionSpeedSettings, videoTransitionAdaptiveInfo)
    }
    val videoCardTransitionClock = rememberVideoCardTransitionClock()
    val systemReduceMotion = rememberSystemReduceMotion()
    val sharedVideoCardTransitionEnabled = cardTransitionEnabled && !systemReduceMotion
    val effectiveVideoCardTransitionDurationMillis = if (systemReduceMotion) {
        VideoCardTransitionVisualTimeline.REDUCED_MOTION_DURATION_MILLIS
    } else {
        videoSharedTransitionDurationMillis
    }
    CompositionLocalProvider(
            LocalVideoSharedTransitionSpeedSettings provides videoSharedTransitionSpeedSettings,
            LocalVideoTransitionAdaptiveInfo provides videoTransitionAdaptiveInfo,
            LocalLiquidGlassRenderConfig provides liquidGlassRenderConfig,
            com.android.purebilibili.feature.home.components.cards.LocalHomeCardDynamicTintEnabled provides
                effectiveHomeSettings.homeCardDynamicTintEnabled,
            com.android.purebilibili.feature.home.components.cards.LocalHomeCardFrostedGlassEnabled provides
                effectiveHomeSettings.homeCardFrostedGlassEnabled,
            com.android.purebilibili.core.plugin.skin.LocalUiSkinState provides uiSkinState,
        ) {
        // [新增] 全局底栏状态管理
        val initialNavigationBackStack = remember(
            startDestination,
            launchToPortraitFeedOnStartupAtInit,
        ) {
                resolveInitialBiliPaiBackStack(
                    firstRoute = startDestination,
                    onboardingRequired = agreementRequired,
                    openPortraitFeedOnStartup = launchToPortraitFeedOnStartupAtInit
                )
        }
        @Suppress("UNCHECKED_CAST")
        val navigation3BackStack = rememberNavBackStack<BiliPaiNavKey>(
            *initialNavigationBackStack.toTypedArray()
        ) as androidx.compose.runtime.snapshots.SnapshotStateList<BiliPaiNavKey>
        val navigation3SaveableStateHolder = rememberSaveableStateHolder()
        fun replaceNavigation3BackStack(keys: List<BiliPaiNavKey>) {
            val replacementStack = keys.ifEmpty { listOf(BiliPaiNavKey.MainHost) }
            if (navigation3BackStack == replacementStack) return
            val removedStateKeys = resolveRemovedNavigation3SaveableStateKeys(
                currentStack = navigation3BackStack,
                replacementStack = replacementStack,
            )
            // NavDisplay rejects an empty stack. clear()+addAll() as two observable writes can
            // expose an empty intermediate frame when a bottom-tab switch races recomposition.
            Snapshot.withMutableSnapshot {
                navigation3BackStack.clear()
                navigation3BackStack.addAll(replacementStack)
            }
            // SaveableStateHolder intentionally retains disposed providers. Media detail keys are
            // session-scoped, so without explicit eviction every watched video remains in the
            // Activity saved-state Bundle and eventually causes TransactionTooLargeException.
            removedStateKeys.forEach(navigation3SaveableStateHolder::removeState)
        }
        val navigation3ProgrammaticBackDispatcher = remember {
            BiliPaiProgrammaticBackDispatcher()
        }
        var predictiveBackCancelRecoveryGeneration by remember { mutableIntStateOf(0) }
        var accountSessionRefreshGeneration by remember { mutableIntStateOf(0) }
        val currentNavigation3Key = navigation3BackStack.lastOrNull()
        val currentRoute = currentNavigation3Key?.toLegacyRoute()
        val homeWallpaperSelection by SettingsManager.getHomeWallpaperSelection(context)
            .collectAsStateWithLifecycle(initialValue = HomeWallpaperSelection())
        val configuredHomeWallpaperUri = homeWallpaperSelection.uri
        val wallpaperPlaybackCoordinator = remember { WallpaperPlaybackCoordinator() }
        val splashWallpaperUri by SettingsManager.getSplashWallpaperUri(context).collectAsStateWithLifecycle(initialValue = ""
        )
        val globalHomeWallpaperUri = remember(configuredHomeWallpaperUri, splashWallpaperUri) {
            resolveHomeWallpaperUri(
                homeWallpaperUri = configuredHomeWallpaperUri,
                splashWallpaperUri = splashWallpaperUri
            )
        }
        val backgroundColor = MaterialTheme.colorScheme.background
        val wallpaperIsStatic = com.android.purebilibili.core.ui.wallpaper.rememberIsStaticWallpaper(globalHomeWallpaperUri)
        val wallpaperTransform = if (windowSizeClass.isTablet) homeWallpaperSelection.tabletTransform
            else homeWallpaperSelection.mobileTransform
        val wallpaperEffectsEnabled = effectiveHomeSettings.homeWallpaperEffectMode !=
            com.android.purebilibili.core.store.HomeWallpaperEffectMode.OFF
        val wallpaperViewport = remember { mutableStateOf(IntSize.Zero) }
        var wallpaperPaletteResult by remember { mutableStateOf<WallpaperPaletteResult?>(null) }
        val wallpaperPalette = wallpaperPaletteResult?.takeIf {
            it.request.uri == globalHomeWallpaperUri && it.request.transform == wallpaperTransform &&
                it.request.baseColor == backgroundColor && wallpaperIsStatic && wallpaperEffectsEnabled
        }?.palette
        val deferGlobalWallpaperPalette by remember(videoCardTransitionClock) {
            derivedStateOf {
                // All non-idle phases retain the detail/source transition contract.
                videoCardTransitionClock.phase !=
                    com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase.IDLE
            }
        }
        val foregroundSettling by com.android.purebilibili.core.lifecycle.ForegroundSettleBudget
            .isSettling
            .collectAsStateWithLifecycle()
        LaunchedEffect(globalHomeWallpaperUri, wallpaperTransform, backgroundColor, wallpaperIsStatic,
            deferGlobalWallpaperPalette, foregroundSettling, wallpaperEffectsEnabled) {
            if (!wallpaperEffectsEnabled || !wallpaperIsStatic || globalHomeWallpaperUri.isBlank()) {
                wallpaperPaletteResult = null
                return@LaunchedEffect
            }
            if (deferGlobalWallpaperPalette || foregroundSettling) return@LaunchedEffect
            snapshotFlow { wallpaperViewport.value }.collectLatest { viewport ->
                if (viewport.width <= 0 || viewport.height <= 0) return@collectLatest
                // Resize events cancel the previous request; never sample on every drag frame.
                delay(140)
                val request = WallpaperPaletteRequest(globalHomeWallpaperUri, viewport, wallpaperTransform, backgroundColor)
                val palette = WallpaperPaletteStore.loadWallpaperPalette(context, request)
                currentCoroutineContext().ensureActive()
                wallpaperPaletteResult = WallpaperPaletteResult(request, palette)
            }
        }
        val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }
        val isDataSaverActiveForGlobalWallpaper = remember(context) {
            SettingsManager.isDataSaverActive(context)
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

        var navigationHostOriginInRoot by remember { mutableStateOf(Offset.Zero) }
        var sidebarAccountSwitcherVisible by rememberSaveable { mutableStateOf(false) }
        var sidebarAccountSessionGeneration by remember { mutableIntStateOf(0) }
        val sidebarAccountSnapshot by produceState(
            initialValue = com.android.purebilibili.core.store.AccountSessionSnapshot(),
            key1 = context,
            key2 = accountSessionRefreshGeneration,
            key3 = sidebarAccountSessionGeneration,
        ) {
            value = withContext(Dispatchers.IO) {
                AccountSessionStore.readSnapshot(context)
            }
        }
        val sidebarAccounts = sidebarAccountSnapshot.accounts
        val sidebarActiveAccountMid = sidebarAccountSnapshot.activeAccountMid
        val sidebarPlaybackAccountMid = sidebarAccountSnapshot.playbackAccountMid
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
        // 跨 Activity 重建保留：进程仍然热（配置变更 / 系统回收 Activity）时，重新挂载这些页
        // 既便宜又必要；冷启动时没有可恢复内容，轻量首帧策略照旧生效。
        var bottomPagerContentReady by rememberSaveable { mutableStateOf(false) }
        var preloadedBottomPagerItems by rememberSaveable(
            stateSaver = BottomPagerPreloadedItemsSaver
        ) { mutableStateOf(emptySet<BottomNavItem>()) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            bottomPagerContentReady = true
        }
        LaunchedEffect(bottomPagerState.currentPage, mainBottomPagerState) {
            mainBottomPagerState.syncPage()
        }
        // Keep visited/transition participants mounted by tab identity, including after reorder.
        LaunchedEffect(
            visibleBottomBarItems,
            bottomPagerState.currentPage,
            mainBottomPagerState.selectedPage,
            mainBottomPagerState.navigationStartPage,
        ) {
            val participants = setOf(
                bottomPagerState.currentPage,
                mainBottomPagerState.selectedPage,
                mainBottomPagerState.navigationStartPage,
            ).mapNotNull { visibleBottomBarItems.getOrNull(it) }
            preloadedBottomPagerItems = preloadedBottomPagerItems + participants
        }
        LaunchedEffect(visibleBottomBarItems, mainBottomPagerState.selectedPage) {
            val lastPage = visibleBottomBarItems.lastIndex
            if (lastPage >= 0 && mainBottomPagerState.selectedPage > lastPage) {
                mainBottomPagerState.switchToPage(lastPage)
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
        // A new tab visit is distinct even if the history pager page was uncomposed
        // while away. Playback navigation leaves the selected tab and this identity intact.
        val historyBrowsingSessionKey = remember(currentBottomNavItem) { Any() }
        val renderGlobalHomeWallpaperBackdrop = shouldRenderGlobalHomeWallpaperBackdrop(
            effectScope = effectiveHomeSettings.homeWallpaperEffectScope,
            currentRoute = currentRoute,
            mainHostTabRoute = currentBottomNavItem.route,
        )
        val exposeGlobalHomeWallpaperChrome = shouldExposeGlobalHomeWallpaperChrome(
            effectScope = effectiveHomeSettings.homeWallpaperEffectScope,
            hasWallpaperUri = globalHomeWallpaperUri.isNotBlank(),
            currentRoute = currentRoute,
            mainHostTabRoute = currentBottomNavItem.route,
        )
        val globalWallpaperEnabled = effectiveHomeSettings.homeWallpaperEffectScope ==
            com.android.purebilibili.core.store.HomeWallpaperEffectScope.GLOBAL &&
            effectiveHomeSettings.homeWallpaperEffectMode !=
            com.android.purebilibili.core.store.HomeWallpaperEffectMode.OFF &&
            globalHomeWallpaperUri.isNotBlank()
        val sharedStaticWallpaper = globalWallpaperEnabled && wallpaperIsStatic
        val globalWallpaperSource = if (sharedStaticWallpaper) {
            key(
                globalHomeWallpaperUri,
                effectiveHomeSettings.homeWallpaperEffectMode,
                backgroundColor,
                wallpaperViewport.value.width,
                wallpaperViewport.value.height,
            ) {
                // LayerBackdrop retains its recorded source between page switches. Recreate
                // that source when the app viewport changes (rotation / fold / window resize)
                // so destinations never replay a layer recorded at the previous orientation.
                com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
            }
        } else null
        val renderRootWallpaper = shouldRenderRootHomeWallpaperMedia(
            wallpaperVisible = sharedStaticWallpaper || (globalWallpaperEnabled && renderGlobalHomeWallpaperBackdrop),
            isStaticWallpaper = sharedStaticWallpaper,
            currentRoute = currentRoute,
            mainHostTabRoute = currentBottomNavItem.route,
        )
        val globalHomeWallpaperAppearance = remember(
            globalHomeWallpaperUri,
            effectiveHomeSettings.homeWallpaperEffectMode,
            renderRootWallpaper,
            isLightBackground,
            isDataSaverActiveForGlobalWallpaper
        ) {
            resolveHomeWallpaperBackdropAppearance(
                hasWallpaper = renderRootWallpaper && globalHomeWallpaperUri.isNotBlank(),
                effectMode = effectiveHomeSettings.homeWallpaperEffectMode,
                isDarkTheme = !isLightBackground,
                isDataSaverActive = isDataSaverActiveForGlobalWallpaper,
            )
        }
        val bottomBarItemColors = appNavigationSettings.bottomBarItemColors
        val bottomBarItemLabels = appNavigationSettings.bottomBarItemLabels

        // [修复] 平板模式下，仅当用户开启侧边栏设置时才使用侧边导航
        val tabletUseSidebar = appNavigationSettings.tabletUseSidebar
        
        // 统一侧边栏判定策略：Expanded(840dp)+ 且用户开启侧边栏；折叠半开时强制底栏
        val useSideNavigation = shouldUseSidebarNavigationForLayout(
            windowSizeClass = windowSizeClass,
            tabletUseSidebar = tabletUseSidebar,
            foldPosture = appWindowAdaptiveInfo.posture,
        )
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
            sourceKey = navigation3ReturnSession.transitionSession?.sourceKey
                ?: navigation3ReturnSession.lastVideoSourceKey,
            sourceRoute = navigation3ReturnSession.transitionSession?.sourceRoute
                ?: navigation3ReturnSession.lastVideoSourceRoute,
            clickedBoundsRecorded = navigation3ReturnSession.transitionSession
                ?.hasUsableSourceGeometry
                ?: false,
            cardFullyVisible = navigation3ReturnSession.transitionSession
                ?.cardFullyVisible
                ?: false,
            cardSourceDirection = navigation3ReturnSession.transitionSession
                ?.cardSourceDirection
                ?: navigation3ReturnSession.lastCardSourceDirection,
            sourceCornerDp = navigation3ReturnSession.transitionSession?.sourceCornerDp,
            coverIdentity = navigation3ReturnSession.transitionSession?.coverIdentity,
            sourceBounds = navigation3ReturnSession.transitionSession?.cardBounds,
            sourceCoverBounds = navigation3ReturnSession.transitionSession?.coverBounds,
            sourceInfoBounds = navigation3ReturnSession.transitionSession?.infoBounds,
            sourceLayout = navigation3ReturnSession.transitionSession?.sourceLayout
                ?: com.android.purebilibili.core.ui.transition.VideoCardSourceLayout.COVER_ONLY,
            sourceChromeSnapshot = navigation3ReturnSession.transitionSession?.sourceChromeSnapshot,
        )
        fun captureCardSourceDirectionForSession(): BiliPaiNavCardSourceDirection {
            return resolveBiliPaiNavCardSourceDirection(
                clickedBoundsRecorded = CardPositionManager.lastClickedCardBounds != null,
                cardFullyVisible = CardPositionManager.isCardFullyVisible,
                isSingleColumnCard = CardPositionManager.isSingleColumnCard,
                normalizedCenterX = CardPositionManager.lastClickedCardCenter?.x
            )
        }
        fun captureVideoCardTransitionSession(
            bvid: String,
            source: BiliPaiVideoSource,
            coverIdentity: String?,
            useRecordedCardSource: Boolean = true,
        ) = VideoCardTransitionSession.create(
            bvid = bvid,
            source = source,
            recordedSourceKey = CardPositionManager.lastClickedVideoSourceKey
                .takeIf { useRecordedCardSource },
            cardBounds = CardPositionManager.lastClickedCardBounds,
            coverBounds = CardPositionManager.lastClickedCoverBounds,
            infoBounds = CardPositionManager.lastClickedInfoBounds,
            sourceCornerDp = CardPositionManager.lastClickedVideoSourceCornerDp,
            cardSourceDirection = captureCardSourceDirectionForSession(),
            coverIdentity = coverIdentity,
            cardFullyVisible = CardPositionManager.isCardFullyVisible,
            isSingleColumnCard = CardPositionManager.isSingleColumnCard,
            sourceLayout = CardPositionManager.lastClickedVideoSourceLayout,
            sourceChromeSnapshot = CardPositionManager.lastClickedVideoSourceChromeSnapshot,
            hostOriginInRoot = navigationHostOriginInRoot,
        )
        fun prearmVideoCardOpening(session: VideoCardTransitionSession) {
            val transitionEnabledForSource = resolveVideoCardTransitionEnabledForSource(
                cardTransitionEnabled = sharedVideoCardTransitionEnabled,
                relatedVideoTransitionEnabled = relatedVideoTransitionEnabled,
                sourceRoute = session.sourceRoute,
            )
            val hasUsableSourceBounds = session.cardBounds
                ?.let { it.width > 1f && it.height > 1f } == true
            if (
                shouldUseMiuixVideoCardMorph(
                    cardTransitionEnabled = transitionEnabledForSource,
                    reduceMotion = systemReduceMotion,
                    sourceRoute = session.sourceRoute,
                    hasUsableSourceBounds = hasUsableSourceBounds,
                )
            ) {
                // Activate the source-cover session before NavDisplay mounts the destination.
                // Waiting for its stack-observer effect exposes the black player Surface for one frame.
                videoCardTransitionClock.beginOpeningIfNeeded(session.sourceRoute)
            }
        }
        var lastVideoDetailOpenId by remember { mutableLongStateOf(0L) }
        var lastLiveAreaDetailOpenId by remember { mutableLongStateOf(0L) }
        var lastSearchOpenId by remember { mutableLongStateOf(0L) }
        fun pushNavigation3KeyDirect(key: BiliPaiNavKey) {
            val currentSearch = navigation3BackStack.lastOrNull() as? BiliPaiNavKey.Search
            val requestedSearchKeyword = when (key) {
                BiliPaiNavKey.Search -> ""
                is BiliPaiNavKey.Search -> key.keyword
                else -> null
            }
            // Guard before allocating openId: repeated entrance taps belong to the same page.
            if (currentSearch != null && requestedSearchKeyword == currentSearch.keyword) return
            val sessionScopedKey = when (key) {
                is BiliPaiNavKey.VideoDetail -> {
                    if (key.openId > 0L) {
                        key
                    } else {
                        val nextOpenId = maxOf(
                            SystemClock.uptimeMillis(),
                            lastVideoDetailOpenId + 1L,
                        )
                        lastVideoDetailOpenId = nextOpenId
                        key.copy(openId = nextOpenId)
                    }
                }
                is BiliPaiNavKey.LiveAreaDetail -> {
                    if (key.openId > 0L) {
                        key
                    } else {
                        val nextOpenId = maxOf(
                            SystemClock.uptimeMillis(),
                            lastLiveAreaDetailOpenId + 1L,
                        )
                        lastLiveAreaDetailOpenId = nextOpenId
                        key.copy(openId = nextOpenId)
                    }
                }
                is BiliPaiNavKey.Search -> {
                    if (key.openId > 0L) {
                        key
                    } else {
                        val nextOpenId = maxOf(
                            SystemClock.uptimeMillis(),
                            lastSearchOpenId + 1L,
                        )
                        lastSearchOpenId = nextOpenId
                        key.copy(openId = nextOpenId)
                    }
                }
                BiliPaiNavKey.Search -> {
                    val nextOpenId = maxOf(
                        SystemClock.uptimeMillis(),
                        lastSearchOpenId + 1L,
                    )
                    lastSearchOpenId = nextOpenId
                    BiliPaiNavKey.Search(openId = nextOpenId)
                }
                BiliPaiNavKey.LikedVideos -> {
                    BiliPaiNavKey.LikedVideos()
                }
                else -> key
            }
            replaceNavigation3BackStack(when (sessionScopedKey) {
                is BiliPaiNavKey.SettingsCategory -> pushOrReplaceSettingsCategoryNavKey(
                    currentStack = navigation3BackStack,
                    key = sessionScopedKey,
                )
                else -> pushBiliPaiNavKey(
                    currentStack = navigation3BackStack,
                    key = sessionScopedKey,
                )
            })
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
            replaceNavigation3BackStack(pushBiliPaiNavKey(
                currentStack = popBiliPaiNavKey(navigation3BackStack),
                key = key
            ))
        }
        fun requestBottomPagerPageForRoute(route: String, beforeNavigation: (() -> Unit)? = null): Boolean {
            val page = resolveBottomPagerPageForRoute(
                route = route,
                visibleItems = visibleBottomBarItems
            ) ?: return false
            val target = legacyRouteToBiliPaiNavKey(route).toPrivacyNavigationTarget()
            val performPagerNavigation = {
                beforeNavigation?.invoke()
                // Same-level bottom tabs switch directly through the pager. Keep Nav3 out of the
                // path when MainHost is already the sole destination.
                if (shouldResetNavigation3BackStackForBottomPager(navigation3BackStack)) {
                    replaceNavigation3BackStack(listOf(BiliPaiNavKey.MainHost))
                }
                mainBottomPagerState.switchToPage(page)
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
            seed: PortraitStoryNavigationSeed,
            sourceRoute: String? = null
        ) {
            if (!canNavigate(false)) return
            isBottomBarVisible = false
            val matchedVisibleCardRoute = resolveVideoCardSourceRouteForNavigation(
                currentRoute = navigation3BackStack.lastOrNull()?.toLegacyRoute(),
                videoBvid = seed.bvid,
                lastClickedVideoSourceKey = CardPositionManager.lastClickedVideoSourceKey,
                visibleBottomBarRoutes = visibleBottomBarRoutes
            )
            val source = resolveBiliPaiVideoSource(
                bvid = seed.bvid,
                explicitSourceRoute = sourceRoute ?: matchedVisibleCardRoute,
                currentKey = navigation3BackStack.lastOrNull(),
                previousSourceRoute = navigation3ReturnSession.lastVideoSourceRoute
            )
            if (source.route != null) {
                val transitionSession = captureVideoCardTransitionSession(
                    bvid = seed.bvid,
                    source = source,
                    coverIdentity = seed.coverUrl,
                )
                navigation3ReturnSession = navigation3ReturnSession
                    .recordTransitionSession(
                        session = transitionSession,
                        preserveCurrentSession = navigation3BackStack.any {
                            it is BiliPaiNavKey.VideoDetail
                        },
                    )
                    .markDetailEntered(SystemClock.uptimeMillis())
                prearmVideoCardOpening(transitionSession)
            }
            pushNavigation3Key(
                BiliPaiNavKey.Story(
                    seedBvid = seed.bvid,
                    seedCid = seed.cid,
                    seedCover = seed.coverUrl,
                    sourceRoute = source.route,
                    openId = SystemClock.uptimeMillis()
                )
            )
        }
        fun navigateToVideoRouteInNavigation3(
            route: String,
            sourceRoute: String?,
            skipPortraitStoryResolution: Boolean = false,
            useRecordedCardSource: Boolean = true,
        ) {
            val parsedKey = legacyRouteToBiliPaiNavKey(route)
            val videoKey = parsedKey as? BiliPaiNavKey.VideoDetail
            val hasCommentJump = (videoKey?.commentRootRpid ?: 0L) > 0L || (videoKey?.commentTargetRpid ?: 0L) > 0L
            if (!skipPortraitStoryResolution && !hasCommentJump) {
                resolvePortraitStoryNavigationSeed(
                    directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                    isVerticalVideo = videoKey?.initialVertical == true,
                    startAudio = videoKey?.startAudio == true,
                    bvid = videoKey?.bvid.orEmpty(),
                    cid = videoKey?.cid ?: 0L,
                    coverUrl = videoKey?.coverUrl.orEmpty(),
                    cardTransitionEnabled = cardTransitionEnabled,
                )?.let { seed ->
                    navigateToPortraitStoryInNavigation3(seed, sourceRoute = sourceRoute)
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
                            if (cardTransitionEnabled) {
                                navigateToVideoRouteInNavigation3(
                                    route = videoKey.copy(
                                        autoPortrait = true,
                                        initialVertical = true,
                                        directPortraitEntry = true,
                                    ).toLegacyRoute(),
                                    sourceRoute = sourceRoute,
                                    skipPortraitStoryResolution = true,
                                    useRecordedCardSource = useRecordedCardSource,
                                )
                            } else {
                                navigateToPortraitStoryInNavigation3(
                                    seed = PortraitStoryNavigationSeed(
                                        bvid = videoKey.bvid,
                                        cid = videoKey.cid,
                                        coverUrl = videoKey.coverUrl
                                    ),
                                    sourceRoute = sourceRoute
                                )
                            }
                        } else {
                            navigateToVideoRouteInNavigation3(
                                route = route,
                                sourceRoute = sourceRoute,
                                skipPortraitStoryResolution = true,
                                useRecordedCardSource = useRecordedCardSource,
                            )
                        }
                    }
                    return
                }
            }
            // Resolve redirects first. Reserving the debounce slot before delegating to
            // Story (or a cached direction lookup) makes that same click reject itself.
            if (!canNavigate(false)) return
            val videoBvid = videoKey?.bvid.orEmpty()
            val matchedVisibleCardRoute = resolveVideoCardSourceRouteForNavigation(
                currentRoute = navigation3BackStack.lastOrNull()?.toLegacyRoute(),
                videoBvid = videoBvid,
                lastClickedVideoSourceKey = CardPositionManager.lastClickedVideoSourceKey,
                visibleBottomBarRoutes = visibleBottomBarRoutes
            )
            val source = resolveBiliPaiVideoSource(
                bvid = videoBvid,
                explicitSourceRoute = sourceRoute ?: matchedVisibleCardRoute.takeIf { useRecordedCardSource },
                currentKey = navigation3BackStack.lastOrNull(),
                previousSourceRoute = navigation3ReturnSession.lastVideoSourceRoute
            )
            val transitionSession = captureVideoCardTransitionSession(
                bvid = videoBvid,
                source = source,
                coverIdentity = videoKey?.coverUrl,
                useRecordedCardSource = useRecordedCardSource,
            )
            navigation3ReturnSession = navigation3ReturnSession
                .recordTransitionSession(
                    session = transitionSession,
                    preserveCurrentSession = navigation3BackStack.any {
                        it is BiliPaiNavKey.VideoDetail
                    },
                )
                .markDetailEntered(SystemClock.uptimeMillis())
            prearmVideoCardOpening(transitionSession)
            miniPlayerManager?.isNavigatingToVideo = true
            // 合集列表 / 详情压详情：进新片前立刻挂起上一级仍在响的 player，避免只听见旧声音。
            if (videoBvid.isNotBlank()) {
                miniPlayerManager?.haltForeignPlaybackForIncomingVideo(videoBvid)
            }
            miniPlayerManager?.exitMiniMode(animate = false)
            val key = when (parsedKey) {
                is BiliPaiNavKey.VideoDetail -> {
                    val morphDirectPortrait = resolveDirectPortraitDetailMorphEntry(
                        directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                        cardTransitionEnabled = cardTransitionEnabled,
                        isVerticalVideo = parsedKey.initialVertical || parsedKey.directPortraitEntry,
                        coverUrl = parsedKey.coverUrl,
                        startAudio = parsedKey.startAudio,
                    ) || parsedKey.directPortraitEntry
                    parsedKey.copy(
                        sourceRoute = source.route,
                        autoPortrait = parsedKey.autoPortrait || morphDirectPortrait,
                        initialVertical = parsedKey.initialVertical || morphDirectPortrait,
                        directPortraitEntry = morphDirectPortrait,
                    )
                }
                else -> parsedKey
            }
            pushNavigation3Key(key)
        }
        fun navigateToVideoInNavigation3(
            bvid: String,
            cid: Long = 0L,
            coverUrl: String = "",
            // 从全屏播放中 push 新详情页时，把全屏意图带到新路由，避免中途掉出全屏。
            fullscreen: Boolean = false,
            startAudio: Boolean = false,
            autoPortrait: Boolean = shouldAutoEnterPortraitForStandardVideoNavigation(),
            resumePositionMs: Long = 0L,
            initialVertical: Boolean = false,
            directPortraitEntry: Boolean = false,
            sourceRoute: String? = null,
            skipPortraitStoryResolution: Boolean = false,
            useRecordedCardSource: Boolean = true,
        ) {
            val morphDirectPortrait = resolveDirectPortraitDetailMorphEntry(
                directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                cardTransitionEnabled = cardTransitionEnabled,
                isVerticalVideo = initialVertical || directPortraitEntry,
                coverUrl = coverUrl,
                startAudio = startAudio,
            ) || directPortraitEntry
            if (!skipPortraitStoryResolution) {
                resolvePortraitStoryNavigationSeed(
                    directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                    isVerticalVideo = initialVertical,
                    startAudio = startAudio,
                    bvid = bvid,
                    cid = cid,
                    coverUrl = coverUrl,
                    cardTransitionEnabled = cardTransitionEnabled,
                )?.let { seed ->
                    navigateToPortraitStoryInNavigation3(seed, sourceRoute = sourceRoute)
                    return
                }
            }
            val isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
            if (!isNetworkAvailable && !com.android.purebilibili.feature.download.DownloadManager.isInitialized.value) {
                val sourceBackStack = navigation3BackStack.toList()
                coroutineScope.launch {
                    com.android.purebilibili.feature.download.DownloadManager.awaitInitialization()
                    if (navigation3BackStack.toList() != sourceBackStack) return@launch
                    navigateToVideoInNavigation3(
                        bvid = bvid,
                        cid = cid,
                        coverUrl = coverUrl,
                        fullscreen = fullscreen,
                        startAudio = startAudio,
                        autoPortrait = autoPortrait,
                        resumePositionMs = resumePositionMs,
                        initialVertical = initialVertical,
                        directPortraitEntry = directPortraitEntry,
                        sourceRoute = sourceRoute,
                        skipPortraitStoryResolution = skipPortraitStoryResolution,
                        useRecordedCardSource = useRecordedCardSource,
                    )
                }
                return
            }
            val offlineTask = com.android.purebilibili.feature.download.resolveOfflineVideoNavigationTask(
                // 下载进度仅在点击时用于离线分流，不应让整个导航树订阅进度更新。
                tasks = com.android.purebilibili.feature.download.DownloadManager.tasks.value.values,
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
                autoPortrait = autoPortrait || morphDirectPortrait,
                fullscreen = fullscreen,
                resumePositionMs = resumePositionMs,
                initialVertical = initialVertical || morphDirectPortrait,
                directPortraitEntry = morphDirectPortrait,
            )
            if (
                !skipPortraitStoryResolution &&
                com.android.purebilibili.data.model.response.shouldResolveVerticalVideoForPortraitEntry(
                    directPortraitStoryEntry = playerInteractionSettings.directPortraitStoryEntry,
                    startAudio = startAudio,
                    bvid = bvid,
                    isVerticalVideo = initialVertical || morphDirectPortrait,
                    coverUrl = coverUrl
                )
            ) {
                coroutineScope.launch {
                    if (com.android.purebilibili.data.repository.VideoRepository.isVerticalVideo(bvid)) {
                        if (cardTransitionEnabled) {
                            navigateToVideoInNavigation3(
                                bvid = bvid,
                                cid = cid,
                                coverUrl = coverUrl,
                                startAudio = startAudio,
                                autoPortrait = true,
                                resumePositionMs = resumePositionMs,
                                initialVertical = true,
                                directPortraitEntry = true,
                                sourceRoute = sourceRoute,
                                skipPortraitStoryResolution = true,
                                useRecordedCardSource = useRecordedCardSource,
                            )
                        } else {
                            navigateToPortraitStoryInNavigation3(
                                seed = PortraitStoryNavigationSeed(
                                    bvid = bvid.trim(),
                                    cid = cid,
                                    coverUrl = coverUrl
                                ),
                                sourceRoute = sourceRoute
                            )
                        }
                    } else {
                        navigateToVideoRouteInNavigation3(
                            route = videoRoute,
                            sourceRoute = sourceRoute,
                            skipPortraitStoryResolution = true,
                            useRecordedCardSource = useRecordedCardSource,
                        )
                    }
                }
                return
            }
            navigateToVideoRouteInNavigation3(
                route = videoRoute,
                sourceRoute = sourceRoute,
                skipPortraitStoryResolution = true,
                useRecordedCardSource = useRecordedCardSource,
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
            .relativeToHost(
                navigation3ReturnSession.transitionSession?.hostOriginInRoot
                    ?: navigationHostOriginInRoot,
            )
        val previousNavigation3Key = navigation3BackStack.getOrNull(navigation3BackStack.lastIndex - 1)
        val activeBottomTabRoute = resolveActiveBottomTabRoute(
            currentKey = currentNavigation3Key,
            currentBottomItem = currentBottomNavItem
        )
        // `activeBottomTabRoute` follows the top-most destination (for example `video/...`)
        // and is intentionally used by bottom-bar visibility. Shared-card return matching needs
        // the page retained inside MainHost instead, even while VideoDetail is on top.
        val activeMainHostRoute = currentBottomNavItem.route
        val backGestureDecision = remember(
            cardTransitionEnabled,
            systemBackAction,
            currentNavigation3Key,
            previousNavigation3Key,
            navigation3SourceMetadata,
            activeMainHostRoute,
        ) {
            resolveBiliPaiBackGestureDecision(
                cardTransitionEnabled = sharedVideoCardTransitionEnabled,
                systemBackAction = systemBackAction,
                currentKey = currentNavigation3Key,
                previousKey = previousNavigation3Key,
                sourceMetadata = navigation3SourceMetadata,
                activeMainHostRoute = activeMainHostRoute,
            )
        }
        val predictiveBackEnabled = appNavigationSettings.predictiveBackEnabled
        // 返回封面预热：每次进入详情(栈顶 key 变化)重置一次，预测手势首帧 / 返回提交
        // 各触发一次。Coil 对相同 cacheKey 幂等，重复调用无网络开销。
        val homeCoverPrefetchTriggered = remember(currentNavigation3Key) {
            mutableStateOf(false)
        }
        fun maybePrefetchHomeCoversForVideoReturn() {
            if (homeCoverPrefetchTriggered.value) return
            homeCoverPrefetchTriggered.value = true
            val sourceBvid = (currentNavigation3Key as? BiliPaiNavKey.VideoDetail)?.bvid
            val candidates = resolveHomeCoverReturnPrefetchCandidates(
                visibleEntries = HomeCoverReturnPrefetchRegistry.snapshot(),
                sourceBvid = sourceBvid,
            )
            prefetchHomeCoverImages(context = context, entries = candidates)
        }
        // 预测返回样式/方向从设置读取。style 为 legacy 存储值(默认 "scale"),
        // 经 fromStorageValue 归一化后由策略层按 routeTransition 分发,不再改变 handler 选择;
        // exitDirection 默认 "auto" 时走 autoDerived(卡片来源方向),显式值(follow_gesture /
        // always_left / always_right)直接覆盖。
        val predictiveBackAnimationStyle = if (appNavigationSettings.predictiveBackEnabled) {
            BiliPaiPredictiveBackAnimationStyle.fromStorageValue(
                appNavigationSettings.predictiveBackAnimationStyle,
            )
        } else {
            BiliPaiPredictiveBackAnimationStyle.NONE
        }
        val predictiveBackExitDirection = BiliPaiPredictiveBackExitDirection.fromStorageValue(
            appNavigationSettings.predictiveBackExitDirection
        )
        val shouldInterceptTabBack = backGestureDecision.interceptSystemBack
        val isVideoDetailDestination = isVideoDetailRoute(currentRoute)
        val videoCardSettleState = videoCardTransitionClock.settleState
        val videoCardChromeExposure = resolveVideoCardTransitionExposure(
            phase = videoCardTransitionClock.phase,
            predictiveBackInProgress = videoCardSettleState ==
                VideoCardTransitionSettleState.InteractiveSeek,
            gestureRestoreInProgress = videoCardSettleState ==
                VideoCardTransitionSettleState.CancelRestore ||
                videoCardTransitionClock.gestureRestoreInProgress,
        )
        val sourceIsBottomBarDestination = isVideoCardTransitionBottomBarSource(
            sourceRoute = videoCardTransitionClock.sourceRoute,
            visibleBottomBarRoutes = visibleBottomBarRoutes,
        )
        val videoCardSourceChromeVisible = shouldShowVideoCardTransitionSourceChrome(
            isVideoDetailDestination = isVideoDetailDestination,
            exposure = videoCardChromeExposure,
            sourceIsBottomBarDestination = sourceIsBottomBarDestination,
        )
        val driveBottomBarByProgress = shouldDriveVideoCardTransitionChromeByProgress(
            cardTransitionEnabled = cardTransitionEnabled,
            exposure = videoCardChromeExposure,
            sourceIsBottomBarDestination = sourceIsBottomBarDestination,
        )
        val bottomBarMountRoute = resolveVideoCardTransitionChromeBottomBarRoute(
            isVideoDetailDestination = isVideoDetailDestination,
            activeBottomTabRoute = activeBottomTabRoute,
            retainedTabRoute = currentBottomNavItem.route,
        )
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
        val sideBarRouteGate = shouldShowBottomBarForNavigation(
            activeRoute = bottomBarMountRoute,
            visibleBottomBarRoutes = visibleBottomBarRoutes,
            useSideNavigation = false,
            shouldHideBottomBarOnTablet = shouldHideBottomBarOnTablet,
            shouldDeferReveal = false
        )
        // Every video detail needs the full content width, including non-fullscreen playback.
        // A transparent sidebar still reserves space in the Row.
        val sideBarMountGate = shouldMountSidebarForNavigation(
            routeAllowsSidebar = sideBarRouteGate,
            isVideoDetailDestination = isVideoDetailDestination
        )
        val showBottomBar = shouldShowBottomBarForNavigation(
            activeRoute = bottomBarMountRoute,
            visibleBottomBarRoutes = visibleBottomBarRoutes,
            useSideNavigation = useSideNavigation,
            shouldHideBottomBarOnTablet = shouldHideBottomBarOnTablet,
            shouldDeferReveal = shouldDeferBottomBarReveal
        )
        
        // 核心可见性逻辑：
        // 1. 永久隐藏模式 -> 始终隐藏
        // 2. 始终显示模式 -> 始终显示
        // 3. 向下浏览时隐藏模式 -> 由子页面通过 LocalSetBottomBarVisible 控制，初始为 true
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
        // - 且 (模式为始终显示 OR (模式为向下浏览时隐藏 AND 当前状态为可见))
        // - 且 模式不是永久隐藏
        val audioNowPlayingBarEnabled by SettingsManager
            .getAudioNowPlayingBarEnabled(context)
            .collectAsStateWithLifecycle(initialValue = true)
        val audioNowPlayingBarOpensAudioMode by SettingsManager
            .getAudioNowPlayingBarOpensAudioMode(context)
            .collectAsStateWithLifecycle(initialValue = false)
        val audioNowPlayingActive by AudioNowPlayingSession.active.collectAsStateWithLifecycle()
        val audioPlaylist by PlaylistManager.playlist.collectAsStateWithLifecycle()
        val audioPlaylistIndex by PlaylistManager.currentIndex.collectAsStateWithLifecycle()
        val audioNowPlayingItem = audioPlaylist.getOrNull(audioPlaylistIndex)
        var linkedDockPhase by rememberSaveable { mutableStateOf(LinkedDockPhase.Expanded) }
        val hasActiveAudioPlayback = audioNowPlayingActive && audioNowPlayingItem != null && audioNowPlayingBarEnabled
        LaunchedEffect(hasActiveAudioPlayback) {
            val reconciled = resolveLinkedDockPhaseOnAudioChange(linkedDockPhase, hasActiveAudioPlayback)
            if (reconciled != linkedDockPhase) {
                linkedDockPhase = reconciled
            }
        }
        // Shared scroll position is also used by non-home destinations to drive the
        // linked playback dock without forcing the bottom bar itself to disappear.
        val scrollOffsetState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val finalBottomBarVisible = showBottomBar &&
            (driveBottomBarByProgress || videoCardSourceChromeVisible) &&
            bottomBarVisibilityMode != SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN &&
            (
                bottomBarVisibilityMode == SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE ||
                    isBottomBarVisible ||
                    (isBottomBarFloating && audioNowPlayingBarEnabled && audioNowPlayingActive &&
                        audioNowPlayingItem != null)
            )
        // This raw signal is intentionally independent from the user's bottom-bar visibility
        // mode: the linked playback strip still compacts on downward browsing when the bar itself
        // is configured to remain visible.
        val collapseLinkedPlaybackDock by remember(
            isBottomBarVisible,
            currentBottomNavItem,
            scrollOffsetState,
        ) {
            // 根导航只关心是否越过折叠阈值，不读取每一帧的滚动距离。
            derivedStateOf {
                !isBottomBarVisible ||
                    (currentBottomNavItem == BottomNavItem.DYNAMIC && scrollOffsetState.floatValue > 50f)
            }
        }
        val bottomBarVisibilityState = remember { MutableTransitionState(finalBottomBarVisible) }
        bottomBarVisibilityState.targetState = finalBottomBarVisible
        val bottomBarCanMount =
            bottomBarVisibilityMode != SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN &&
                (
                    bottomBarMountGate ||
                        driveBottomBarByProgress ||
                        bottomBarVisibilityState.currentState ||
                        bottomBarVisibilityState.targetState
                )
        val bottomBarReservesSpace = bottomBarCanMount &&
            !driveBottomBarByProgress &&
            (bottomBarVisibilityState.currentState || bottomBarVisibilityState.targetState)
        val bottomBarContentPadding = rememberAppBottomBarContentPadding(
            navigationBarsBottom = WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding(),
            reserveBottomBar = bottomBarReservesSpace && !useSideNavigation,
            isBottomBarFloating = isBottomBarFloating,
            hasUiSkinDecoration = bottomBarUiSkinDecoration != null,
        )
        val latestBrandFeedbackInsetCallback by androidx.compose.runtime.rememberUpdatedState(onBrandFeedbackBottomInsetChanged)
        LaunchedEffect(bottomBarContentPadding) {
            latestBrandFeedbackInsetCallback(bottomBarContentPadding)
        }

        val setBottomBarVisible: (Boolean) -> Unit = remember {
            bottomBarSetter@{ visible: Boolean ->
                if (isBottomBarVisible != visible) {
                    isBottomBarVisible = visible
                }
            }
        }

        // [新增] 首页回顶事件通道 (Channel based event bus)
        val homeScrollChannel = remember {
            kotlinx.coroutines.channels.Channel<com.android.purebilibili.feature.home.HomeScrollRequest>(
                kotlinx.coroutines.channels.Channel.CONFLATED
            )
        }
        val dynamicScrollChannel = remember { kotlinx.coroutines.channels.Channel<DynamicScrollRequest>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val historyScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val profileScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val favoriteScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val liveScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val watchLaterScrollChannel = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
        val historyListScopedSearchChannel = remember {
            kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.CONFLATED)
        }
        val favoriteListScopedSearchChannel = remember {
            kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.CONFLATED)
        }
        val watchLaterListScopedSearchChannel = remember {
            kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.CONFLATED)
        }
        var dynamicUnreadCount by remember { mutableIntStateOf(0) }
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
                    BottomNavItem.HOME -> homeScrollChannel.trySend(
                        com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP
                    )
                    BottomNavItem.DYNAMIC -> dynamicScrollChannel.trySend(DynamicScrollRequest.SCROLL_TO_TOP_OR_REFRESH)
                    BottomNavItem.HISTORY -> historyScrollChannel.trySend(Unit)
                    BottomNavItem.PROFILE -> profileScrollChannel.trySend(Unit)
                    BottomNavItem.FAVORITE -> favoriteScrollChannel.trySend(Unit)
                    BottomNavItem.LIVE -> liveScrollChannel.trySend(Unit)
                    BottomNavItem.WATCHLATER -> watchLaterScrollChannel.trySend(Unit)
                    else -> Unit
                }
            }
        }
        fun pushSearchRouteInNavigation3(keyword: String) {
            val normalizedKeyword = keyword.trim()
            if (normalizedKeyword.isNotEmpty()) {
                pushNavigation3Key(BiliPaiNavKey.Search(keyword = normalizedKeyword)) {
                    inAppSearchKeyword = normalizedKeyword
                }
            }
        }

        fun openBilibiliNativeTargetInNavigation3(target: BilibiliNavigationTarget): Boolean {
            when (target) {
                is BilibiliNavigationTarget.Video -> navigateToVideoInNavigation3(
                    target.videoId, 0L, "", useRecordedCardSource = false,
                )
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
                    pushNavigation3Key(
                        BiliPaiNavKey.BangumiDetail(
                            seasonId = target.seasonId,
                            mediaId = target.mediaId
                        )
                    )
                }
                is BilibiliNavigationTarget.BangumiEpisode -> {
                    pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = 0L, epId = target.epId))
                }
                is BilibiliNavigationTarget.Music -> {
                    pushNavigation3Route(ScreenRoutes.createMusicRoute(target.musicId) ?: return false)
                }
                is BilibiliNavigationTarget.Article -> {
                    pushNavigation3Key(BiliPaiNavKey.ArticleDetail(target.articleId))
                }
                is BilibiliNavigationTarget.PopularFeed -> {
                    if (target.subCategoryKey == "weekly") {
                        pushNavigation3Key(BiliPaiNavKey.WeeklySeries(target.weeklyNumber))
                        return true
                    }
                    // 热门榜单(排行榜/入站必刷/综合热门):切回首页 POPULAR
                    // 子分类的原生 feed,并弹回主宿主,与底栏点首页一致。
                    // 注意顺序:先切子分类(同步写状态),再切大类(其状态更新在协程中,
                    // 若后切子分类,switchCategory 捕获的旧快照会把子分类覆盖回去)。
                    val homeIndex = visibleBottomBarItems.indexOf(BottomNavItem.HOME)
                    if (homeIndex >= 0) {
                        mainBottomPagerState.switchToPage(homeIndex)
                    }
                    homeViewModel.switchPopularSubCategory(
                        when (target.subCategoryKey) {
                            "weekly" -> PopularSubCategory.WEEKLY
                            "rank" -> PopularSubCategory.RANKING
                            "all", "precious" -> PopularSubCategory.PRECIOUS
                            else -> PopularSubCategory.COMPREHENSIVE
                        }
                    )
                    homeViewModel.switchCategory(HomeCategory.POPULAR)
                    replaceNavigation3BackStack(
                        popBiliPaiNavKeyToRoot(navigation3BackStack)
                    )
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
        val submitBottomBarSearchKeyword: (String) -> Unit = { keyword ->
            val listScopedSearchActive = com.android.purebilibili.feature.list.isListScopedSearchActive(
                bottomBarSearchEnabled = effectiveHomeSettings.isBottomBarSearchEnabled,
                listScopedSearchEnabled = effectiveHomeSettings.listScopedSearchEnabled,
            )
            val scopedChannel = if (
                listScopedSearchActive &&
                currentNavigation3Key == BiliPaiNavKey.MainHost
            ) {
                when (currentBottomNavItem) {
                    BottomNavItem.HISTORY -> historyListScopedSearchChannel
                    BottomNavItem.FAVORITE -> favoriteListScopedSearchChannel
                    BottomNavItem.WATCHLATER -> watchLaterListScopedSearchChannel
                    else -> null
                }
            } else {
                null
            }
            if (scopedChannel != null) {
                scopedChannel.trySend(keyword.trim())
            } else {
                submitSearchKeywordInNavigation3(keyword)
            }
        }
        fun openBilibiliLinkInNavigation3(rawLink: String) {
            when (val action = resolveBilibiliLinkNavigationAction(rawLink)) {
                is BilibiliLinkNavigationAction.NativeTarget -> {
                    openBilibiliNativeTargetInNavigation3(action.target)
                }
                is BilibiliLinkNavigationAction.InAppWeb -> {
                    if (!canNavigate(false)) return
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
                    } else {
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
                is MessageLinkNavigationAction.CommentDetail -> {
                    pushNavigation3Key(
                        BiliPaiNavKey.CommentDetail(
                            oid = action.oid,
                            rootId = action.rootReplyId,
                            targetId = action.targetReplyId,
                            type = action.businessId,
                            enterUri = action.enterUri
                        )
                    )
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
                    pushNavigation3Key(
                        BiliPaiNavKey.BangumiDetail(
                            seasonId = action.seasonId,
                            mediaId = action.mediaId
                        )
                    )
                }
                is MessageLinkNavigationAction.BangumiEpisode -> {
                    pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = 0L, epId = action.epId))
                }
                is MessageLinkNavigationAction.Music -> {
                    ScreenRoutes.createMusicRoute(action.musicId)?.let { pushNavigation3Route(it) }
                        ?: pushNavigation3Key(BiliPaiNavKey.Web(rawLink))
                }
                is MessageLinkNavigationAction.Article -> {
                    pushNavigation3Key(BiliPaiNavKey.ArticleDetail(action.articleId))
                }
                is MessageLinkNavigationAction.Web -> {
                    val url = action.url.trim()
                    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                        pushNavigation3Key(BiliPaiNavKey.Web(url))
                    }
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
        val homeFeedScrollInProgressState = remember { androidx.compose.runtime.mutableStateOf(false) }
        val bottomPagerBackgroundWorkAllowed = shouldAllowBottomPagerBackgroundWork(
            isMainHostTop = currentNavigation3Key == BiliPaiNavKey.MainHost,
            isCardTransitionIdle = videoCardTransitionClock.phase ==
                com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase.IDLE &&
                videoCardTransitionClock.settleState == VideoCardTransitionSettleState.Idle,
            isPagerNavigating = mainBottomPagerState.isNavigating || bottomPagerState.isScrollInProgress,
        )
        val allowBottomPagerPreload = bottomPagerBackgroundWorkAllowed &&
            !homeFeedScrollInProgressState.value
        LaunchedEffect(allowBottomPagerPreload, visibleBottomBarItems) {
            if (!allowBottomPagerPreload) return@LaunchedEffect
            // A quiet window is a scheduling budget, not proof that all startup work completed.
            preloadBottomPagerPages(
                visibleItems = visibleBottomBarItems,
                preloadedItems = { preloadedBottomPagerItems },
                awaitFrame = { withFrameNanos { } },
                onPreload = { preloadedBottomPagerItems = preloadedBottomPagerItems + it },
            )
        }
        LaunchedEffect(currentRoute, currentBottomNavItem) {
            scrollOffsetState.floatValue = 0f
            homeFeedScrollInProgressState.value = false
        }

        // [LayerBackdrop] Create backdrop for bottom bar refraction effect.
        // Capture the wallpaper and navigation content together so transparent wallpaper-aware
        // pages feed the same background into the floating dock as Home.
        val shouldCaptureBottomBarBackdrop =
            homeSettings.androidNativeLiquidGlassEnabled || isBottomBarBlurEnabled
        val bottomBarBackdropSource = if (shouldCaptureBottomBarBackdrop) {
            // Bind the shared recording to every frame of the app theme palette,
            // rather than waiting for navigation/layout to invalidate a static page.
            rememberChromeBackdropSource(recordingBackground = backgroundColor)
        } else {
            null
        }
        val bottomBarBackdrop = bottomBarBackdropSource?.backdrop
        val componentMotionEnabled = com.android.purebilibili.core.ui.resolveComponentMotionEnabled(
            entranceEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.uiEntranceAnimationEnabled,
            cardEntranceEnabled = homeSettings.cardAnimationEnabled,
            cardTransitionEnabled = cardTransitionEnabled,
            navigationEnabled = predictiveBackAnimationStyle != BiliPaiPredictiveBackAnimationStyle.NONE,
            systemReduceMotion = systemReduceMotion,
        )
        CompositionLocalProvider(
            com.android.purebilibili.core.ui.LocalComponentMotionEnabled provides componentMotionEnabled,
            com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorator provides
                com.android.purebilibili.core.ui.components.BiliPaiTopBarIconDecorator,
            com.android.purebilibili.core.ui.LocalSystemReduceMotion provides systemReduceMotion,
            com.android.purebilibili.core.ui.LocalAppPopupSurfaceRenderer provides
                com.android.purebilibili.core.ui.components.BiliPaiPopupSurfaceRenderer,
            com.android.purebilibili.core.ui.blur.LocalFloatingChromeBackdrop provides
                bottomBarBackdrop,
            com.android.purebilibili.feature.aicu.LocalAicuNavigation provides { uid: Long? ->
                pushNavigation3Key(BiliPaiNavKey.AicuQuery(uid = uid ?: 0L))
            },
            LocalSetBottomBarVisible provides setBottomBarVisible,
            LocalBottomBarVisible provides (finalBottomBarVisible && !driveBottomBarByProgress),
            LocalBottomBarContentPadding provides bottomBarContentPadding,
            LocalGlobalWallpaperBackdropVisible provides globalWallpaperEnabled,
            LocalHomeWallpaperSelection provides homeWallpaperSelection,
            LocalWallpaperPlaybackCoordinator provides wallpaperPlaybackCoordinator,
            LocalGlobalWallpaperBackdropSource provides globalWallpaperSource,
            LocalGlobalWallpaperUri provides globalHomeWallpaperUri,
            com.android.purebilibili.feature.home.components.cards.LocalWallpaperPalette provides
                wallpaperPalette,
            LocalPredictiveBackGestureEnabled provides predictiveBackEnabled,
            com.android.purebilibili.core.ui.LocalUpBadgeVisibility provides
                com.android.purebilibili.core.ui.UpBadgeVisibility(
                    showBadges = showUpBadges,
                    showAvatars = showUpAvatars
                ),
            com.android.purebilibili.core.ui.LocalFullVideoCardContentVisible provides
                homeSettings.showFullVideoCardContent,
            com.android.purebilibili.core.ui.LocalVideoCardLongPressEnabled provides
                homeSettings.videoCardLongPressActionEnabled,
            com.android.purebilibili.core.ui.LocalMainHazeState provides mainHazeState,
            // 卡片标签 / 信息区实时玻璃效果已下线，不再为首页建立额外 Haze 录制树。
            com.android.purebilibili.core.ui.LocalWallpaperHazeState provides null,
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
                if (manager.prepareAudioNowPlayingForNavigationExit(videoKey.bvid)) {
                    manager.markLeavingByNavigation(
                        expectedBvid = videoKey.bvid,
                        deferPlaybackStop = com.android.purebilibili.feature.video.screen
                            .shouldDeferPlaybackStopForSharedLiveReturn(
                                cardTransitionEnabled = cardTransitionEnabled,
                                hasSourceRoute = !videoKey.sourceRoute.isNullOrBlank(),
                            ),
                    )
                    return
                }
                if (manager.shouldShowInAppMiniPlayer()) {
                    manager.enterMiniMode()
                } else if (shouldMarkNavigationLeaveBeforeVideoExit(isMiniMode = manager.isMiniMode)) {
                    // 卡片过渡开启时延后停播：完整进入后再返回需要 live surface 跟壳缩。
                    manager.markLeavingByNavigation(
                        expectedBvid = videoKey.bvid,
                        deferPlaybackStop = com.android.purebilibili.feature.video.screen
                            .shouldDeferPlaybackStopForSharedLiveReturn(
                                cardTransitionEnabled = cardTransitionEnabled,
                                hasSourceRoute = !videoKey.sourceRoute.isNullOrBlank(),
                            ),
                    )
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
                            mainBottomPagerState.switchToPage(homeIndex)
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
                                replaceNavigation3BackStack(popBiliPaiNavKey(navigation3BackStack))
                            }
                        } else {
                            replaceNavigation3BackStack(popBiliPaiNavKey(navigation3BackStack))
                        }
                    }
                    AppSystemBackAction.FINISH_ACTIVITY -> context.findActivity()?.finish()
                }
            }
            if (sidebarAccountSwitcherVisible) {
                AccountSwitchDialog(
                    accounts = sidebarAccounts,
                    activeAccountMid = sidebarActiveAccountMid,
                    playbackAccountMid = sidebarPlaybackAccountMid,
                    onDismiss = { sidebarAccountSwitcherVisible = false },
                    onAddAccount = {
                        sidebarAccountSwitcherVisible = false
                        pushNavigation3Key(BiliPaiNavKey.Login)
                    },
                    onSwitch = { mid ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val switched = AccountSessionStore.activateAccount(context, mid)
                            withContext(Dispatchers.Main.immediate) {
                                if (!switched) {
                                    Toast.makeText(context, "切换账号失败", Toast.LENGTH_SHORT).show()
                                    return@withContext
                                }
                                sidebarAccountSessionGeneration += 1
                                accountSessionRefreshGeneration += 1
                                homeViewModel.refresh()
                                sidebarAccountSwitcherVisible = false
                                Toast.makeText(context, "已切换账号", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSetPlayback = { mid ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val updated = AccountSessionStore.setPlaybackAccountMid(context, mid)
                            withContext(Dispatchers.Main.immediate) {
                                if (updated) {
                                    sidebarAccountSessionGeneration += 1
                                } else {
                                    Toast.makeText(
                                        context,
                                        "播放账号不可用，请重新登录后再试",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                    onRemove = { mid ->
                        if (mid == sidebarActiveAccountMid) {
                            Toast.makeText(context, "请先切换到其他账号后再移除当前账号", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            coroutineScope.launch(Dispatchers.IO) {
                                val removed = AccountSessionStore.removeAccount(context, mid)
                                withContext(Dispatchers.Main.immediate) {
                                    if (removed) {
                                        sidebarAccountSessionGeneration += 1
                                    } else {
                                        Toast.makeText(context, "移除账号失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Remove the whole slot on video detail so an exit animation cannot reserve width.
                if (windowSizeClass.shouldUseSideNavigation && sideBarMountGate) {
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
                            onHomeDoubleTap = {
                                homeScrollChannel.trySend(
                                    com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH
                                )
                            },
                            onDynamicDoubleTap = {
                                dynamicScrollChannel.trySend(
                                    DynamicScrollRequest.SCROLL_TO_TOP_AND_REFRESH
                                )
                            },
                            hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                            visibleItems = visibleBottomBarItems,
                            itemColorIndices = bottomBarItemColors,
                            itemLabels = bottomBarItemLabels,
                            uiSkinDecoration = bottomBarUiSkinDecoration,
                            sidebarExpanded = appNavigationSettings.sidebarExpanded,
                            onSidebarExpandedChange = { expanded ->
                                coroutineScope.launch {
                                    SettingsManager.setSidebarExpanded(context, expanded)
                                }
                            },
                            onToggleSidebar = {
                                // [Tablet] Toggle sidebar mode
                                coroutineScope.launch {
                                    SettingsManager.setTabletUseSidebar(context, false)
                                }
                            },
                            onAccountSwitchClick = if (
                                appNavigationSettings.sidebarAccountSwitcherEnabled
                            ) {
                                { sidebarAccountSwitcherVisible = true }
                            } else {
                                null
                            },
                        )
                    }
                }
                // 这里原本挂着 animateContentSize()。它在全屏根节点上做两件坏事：
                // 强制该节点每次都 measure 两遍（一次拿目标尺寸、一次按动画插值），
                // 并隐式加上 clipToBounds。而这个 Box 的尺寸只随键盘/系统栏/旋转变化，
                // 对这类变化做尺寸补间没有观感价值，代价却压在每一帧的测量上。
                Box {
                // ===== 内容层 (hazeSource) =====
                // 这个 Box 包裹全局壁纸和所有导航内容，作为底栏模糊/折射的源
                // [LayerBackdrop] Apply layerBackdrop before the bottom bar sibling so the dock
                // captures wallpaper + page content, but never captures itself.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(bottomBarBackdropSource?.modifier ?: Modifier)
                        // [Fix] 将内容标记为全局底栏模糊的源
                        // 必须添加 hazeSource，否则底栏的 hazeEffect 无法获取背景内容，导致模糊失效
                        .then(
                            if (isBottomBarBlurEnabled && mainHazeState != null) {
                                Modifier.hazeSourceCompat(mainHazeState)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    DepthSyncedGlobalHomeWallpaperBackdrop(
                        wallpaperUri = globalHomeWallpaperUri,
                        appearance = globalHomeWallpaperAppearance,
                        baseColor = backgroundColor,
                        depthProgressProvider = {
                            videoCardTransitionClock.depthProgress()
                        },
                        depthPhaseProvider = {
                            videoCardTransitionClock.phase
                        },
                        depthGestureRestoreProvider = {
                            videoCardTransitionClock.gestureRestoreInProgress
                        },
                        sourceBoundsProvider = {
                            navigation3SourceMetadata.sourceBounds
                        },
                        isDataSaverActive = isDataSaverActiveForGlobalWallpaper,
                        isLightBackground = isLightBackground,
                        realtimeBlurEnabled = videoTransitionRealtimeBlurEnabled,
                        playbackEnabled = exposeGlobalHomeWallpaperChrome,
                        modifier = Modifier
                            .onSizeChanged { wallpaperViewport.value = it }
                            .then(globalWallpaperSource?.modifier ?: Modifier),
                    )
                fun bottomPagerNavKeyForItem(item: BottomNavItem): BiliPaiNavKey {
                    return when (item) {
                        BottomNavItem.HOME -> BiliPaiNavKey.Home
                        BottomNavItem.DYNAMIC -> BiliPaiNavKey.Dynamic
                        BottomNavItem.STORY -> BiliPaiNavKey.Story()
                        BottomNavItem.HISTORY -> BiliPaiNavKey.History
                        BottomNavItem.LISTEN_VIDEO -> BiliPaiNavKey.ListenVideo
                        BottomNavItem.PROFILE -> BiliPaiNavKey.Profile
                        BottomNavItem.FAVORITE -> BiliPaiNavKey.Favorite
                        BottomNavItem.LIVE -> BiliPaiNavKey.LiveList
                        BottomNavItem.WATCHLATER -> BiliPaiNavKey.WatchLater
                        BottomNavItem.SETTINGS -> BiliPaiNavKey.Settings
                        BottomNavItem.PLUGINS -> BiliPaiNavKey.PluginsSettings()
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
                    val backgroundSource = resolveVideoCardTransitionBackgroundSource(
                        sourceRoute = backgroundState.sourceRouteProvider(),
                    )
                    val backgroundScaleReduction = resolveVideoCardTransitionBackgroundScaleReduction(
                        backgroundSource
                    )
                    val shouldApplyBackground = cardTransitionEnabled &&
                        shouldApplyVideoCardTransitionBackgroundToRoute(
                            entryRoute = entryRoute,
                            sourceRoute = backgroundState.sourceRouteProvider(),
                            activeMainHostRoute = activeMainHostRoute
                        )
                    val useHostOwnedBackgroundSnapshot =
                        shouldUseHostOwnedVideoCardTransitionSnapshot(
                            backgroundState.sourceRouteProvider()
                        )
                    val shouldApplyPredictiveBlur = shouldApplyPredictiveBackBlurToRoute(
                        entryKey = key,
                        targetBackKey = predictiveBackState.targetKeyProvider(),
                        videoCardBackgroundApplied = shouldApplyBackground,
                    )
                    val routeModifier = Modifier
                        .fillMaxSize()
                        .let { baseModifier ->
                            baseModifier
                                .let { modifier ->
                                    if (shouldApplyBackground) {
                                        val pinnedModifier = modifier
                                            .pinSourcePageDuringSharedTransition()
                                        val applyHostOwnedSnapshotOnRoute =
                                            useHostOwnedBackgroundSnapshot &&
                                                shouldApplyVideoCardTransitionSnapshotOnRouteShell(
                                                    entryRoute = entryRoute,
                                                    sourceRoute = backgroundState.sourceRouteProvider(),
                                                    activeMainHostRoute = activeMainHostRoute,
                                                )
                                        if (applyHostOwnedSnapshotOnRoute) {
                                            pinnedModifier.videoCardTransitionBackgroundEffect(
                                                progressProvider = backgroundState.progressProvider,
                                                phaseProvider = backgroundState.phaseProvider,
                                                exposureProvider = backgroundState.exposureProvider,
                                                isGestureRestoreInProgressProvider = backgroundState.isGestureRestoreInProgressProvider,
                                                motionTierProvider = backgroundState.motionTierProvider,
                                                isLightBackgroundProvider = backgroundState.isLightBackgroundProvider,
                                                realtimeBlurEnabledProvider = {
                                                    shouldUseRealtimeVideoCardTransitionBackgroundBlur(
                                                        source = backgroundSource,
                                                        realtimeBlurEnabled = videoTransitionRealtimeBlurEnabled,
                                                    )
                                                },
                                                scaleReductionProvider = {
                                                    backgroundScaleReduction
                                                },
                                                sourceBoundsProvider = backgroundState.sourceBoundsProvider,
                                                snapshotHandle = backgroundState.snapshotHandle,
                                            )
                                        } else if (!useHostOwnedBackgroundSnapshot) {
                                            pinnedModifier.videoCardTransitionLiveBackgroundEffect(
                                                progressProvider = backgroundState.progressProvider,
                                                phaseProvider = backgroundState.phaseProvider,
                                                exposureProvider = backgroundState.exposureProvider,
                                                isGestureRestoreInProgressProvider = backgroundState.isGestureRestoreInProgressProvider,
                                                motionTierProvider = backgroundState.motionTierProvider,
                                                isLightBackgroundProvider = backgroundState.isLightBackgroundProvider,
                                                realtimeBlurEnabledProvider = {
                                                    shouldUseRealtimeVideoCardTransitionBackgroundBlur(
                                                        source = backgroundSource,
                                                        realtimeBlurEnabled = videoTransitionRealtimeBlurEnabled,
                                                    )
                                                },
                                                scaleReductionProvider = {
                                                    backgroundScaleReduction
                                                },
                                                sourceBoundsProvider = backgroundState.sourceBoundsProvider,
                                            )
                                        } else {
                                            pinnedModifier
                                        }
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
                fun RenderSpaceDestination(spaceKey: BiliPaiNavKey.Space, onBack: () -> Unit) {
                    com.android.purebilibili.feature.space.SpaceScreen(
                        mid = spaceKey.mid,
                        targetBvid = spaceKey.targetBvid,
                        onBack = onBack,
                        onVideoClick = { bvid, cid, resumePositionMs ->
                            navigateToVideoInNavigation3(
                                bvid = bvid,
                                cid = cid,
                                coverUrl = "",
                                resumePositionMs = resumePositionMs,
                                sourceRoute = spaceKey.toLegacyRoute()
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
                        onCheeseClick = { seasonId ->
                            if (seasonId > 0L) {
                                pushNavigation3Key(
                                    BiliPaiNavKey.BangumiPlayer(
                                        seasonId = seasonId,
                                        epId = 0L,
                                        isCourse = true
                                    )
                                )
                            }
                        },
                        onWebClick = { url, title ->
                            pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                        },
                        onLiveClick = { roomId, title, uname ->
                            if (roomId > 0L) {
                                pushNavigation3Key(
                                    BiliPaiNavKey.Live(
                                        roomId = roomId.toString(),
                                        title = title,
                                        uname = uname
                                    )
                                )
                            }
                        },
                        onPlayAllAudioClick = { bvid, resumePositionMs ->
                            pushNavigation3Key(
                                BiliPaiNavKey.AudioMode(
                                    sourceBvid = bvid,
                                    sourceResumePositionMs = resumePositionMs
                                )
                            )
                        },
                        onDynamicDetailClick = { dynamicId ->
                            pushNavigation3Key(BiliPaiNavKey.DynamicDetail(dynamicId))
                        },
                        onTopicClick = { topicId ->
                            if (topicId > 0L) {
                                pushNavigation3Key(BiliPaiNavKey.TopicDetail(topicId))
                            }
                        },
                        onTopicKeywordClick = { keyword ->
                            pushSearchRouteInNavigation3(keyword)
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
                            if (type.equals("coin", ignoreCase = true)) {
                                pushNavigation3Key(
                                    BiliPaiNavKey.LikedVideos(
                                        mid = mid,
                                        ownerName = ownerName,
                                        isCoinArchive = true,
                                    )
                                )
                            } else if (type.equals("like", ignoreCase = true) || type.equals("liked", ignoreCase = true)) {
                                pushNavigation3Key(
                                    BiliPaiNavKey.LikedVideos(
                                        mid = mid,
                                        ownerName = ownerName
                                    )
                                )
                            } else {
                                pushNavigation3Key(
                                    BiliPaiNavKey.SeasonSeriesDetail(
                                        type = type,
                                        id = id,
                                        mid = mid,
                                        title = title,
                                        ownerName = ownerName
                                    )
                                )
                            }
                        },
                        onLikedVideosClick = { upMid, upName ->
                            pushNavigation3Key(
                                BiliPaiNavKey.LikedVideos(
                                    mid = upMid,
                                    ownerName = upName
                                )
                            )
                        },
                        onMessageClick = { talkerId, userName, _ ->
                            pushNavigation3Key(
                                BiliPaiNavKey.Chat(
                                    talkerId = talkerId,
                                    sessionType = 1,
                                    userName = userName
                                )
                            )
                        },
                        onFollowingClick = { followingMid ->
                            pushNavigation3Key(BiliPaiNavKey.Following(followingMid))
                        },
                        onFansClick = { fansMid ->
                            pushNavigation3Key(
                                BiliPaiNavKey.Following(mid = fansMid, fans = true)
                            )
                        },
                        onUpowerRankClick = { upMid, upName, upCount ->
                            pushNavigation3Key(
                                BiliPaiNavKey.UpowerRank(
                                    mid = upMid,
                                    name = upName,
                                    count = upCount
                                )
                            )
                        },
                        onMemberGuardClick = { guardMid, guardName, guardCount ->
                            pushNavigation3Key(
                                BiliPaiNavKey.MemberGuard(
                                    mid = guardMid,
                                    name = guardName,
                                    count = guardCount
                                )
                            )
                        },
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "space:${spaceKey.mid}"),
                        sharedTransitionScope = null,
                        animatedVisibilityScope = null
                    )
                }

                @Composable
                fun RenderBangumiDestination(
                    bangumiKey: BiliPaiNavKey.BangumiDetail,
                    onBack: () -> Unit,
                    onSeasonChange: (Long) -> Unit,
                    contentReady: Boolean = true,
                ) {
                    com.android.purebilibili.feature.bangumi.BangumiDetailScreen(
                        seasonId = bangumiKey.seasonId,
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "bangumi:${bangumiKey.seasonId}"),
                        contentTransitionReady = contentReady,
                        epId = bangumiKey.epId,
                        mediaId = bangumiKey.mediaId,
                        onBack = onBack,
                        onEpisodeClick = { actionSeasonId, episode ->
                            pushNavigation3Key(
                                BiliPaiNavKey.BangumiPlayer(
                                    seasonId = actionSeasonId,
                                    epId = episode.id,
                                    preferredAid = episode.aid,
                                    isCourse = episode.from == "pugv" ||
                                        episode.playable ||
                                        episode.episodeCanView
                                )
                            )
                        },
                        onSeasonClick = onSeasonChange,
                        onReviewsClick = { mediaId, title ->
                            if (mediaId > 0L) {
                                pushNavigation3Key(
                                    BiliPaiNavKey.BangumiReview(
                                        mediaId = mediaId,
                                        title = title
                                    )
                                )
                            }
                        },
                        onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) }
                    )
                }

                @Composable
                fun RenderNavigationContent(
                    key: BiliPaiNavKey,
                    isBottomPagerPageActive: Boolean = true,
                    isBottomPagerHosted: Boolean = false,
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
                                // MainHost 已由 NavDisplay entry 外层的
                                // VideoCardTransitionBackgroundRouteContent 持有唯一冻结层。
                                // 此处不能再给 Pager 页挂同一个 snapshotHandle：嵌套
                                // GraphicsLayer.record 会递归录制自身，返回时只剩 shared 卡片、
                                // 来源页变黑。页面路由仍通过 Local source route 提供给卡片匹配。
                                Box(modifier = Modifier.fillMaxSize()) {
                                    HorizontalPager(
                                        modifier = Modifier.fillMaxSize(),
                                        state = bottomPagerState,
                                        key = { page -> resolveBottomPagerLayoutKey(page, visibleBottomBarItems) },
                                        beyondViewportPageCount = resolveBottomPagerBeyondViewportPageCount(
                                            pageCount = visibleBottomBarItems.size,
                                            contentReady = bottomPagerContentReady
                                        ).coerceAtMost(BOTTOM_PAGER_MAX_PRELOAD_DISTANCE),
                                        userScrollEnabled = shouldEnableBottomPagerUserScroll()
                                    ) { page ->
                                        val slotItem = visibleBottomBarItems.getOrNull(page)
                                        if (
                                            slotItem != null && shouldComposeBottomPagerPage(
                                                item = slotItem,
                                                page = page,
                                                currentPage = bottomPagerState.currentPage,
                                                selectedPage = mainBottomPagerState.selectedPage,
                                                isNavigating = mainBottomPagerState.isNavigating,
                                                navigationStartPage = mainBottomPagerState.navigationStartPage,
                                                contentReady = slotItem in preloadedBottomPagerItems
                                            )
                                        ) {
                                            val pageKey = bottomPagerNavKeyForItem(slotItem)
                                            bottomPagerSaveableStateHolder.SaveableStateProvider(
                                                resolveBottomPagerSaveableStateKey(slotItem)
                                            ) {
                                                CompositionLocalProvider(
                                                    LocalGlobalWallpaperBackdropVisible provides (
                                                        globalWallpaperEnabled && shouldRenderGlobalHomeWallpaperBackdrop(
                                                            effectScope = effectiveHomeSettings.homeWallpaperEffectScope,
                                                            currentRoute = pageKey.toLegacyRoute(),
                                                        )
                                                    ),
                                                    LocalVideoCardSharedElementSourceRoute provides pageKey.toLegacyRoute(),
                                                    com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed provides (
                                                        page == bottomPagerState.settledPage &&
                                                            bottomPagerBackgroundWorkAllowed
                                                    ),
                                                ) {
                                                    RenderNavigationContent(
                                                        key = pageKey,
                                                        isBottomPagerPageActive = page == bottomPagerState.settledPage &&
                                                            bottomPagerBackgroundWorkAllowed,
                                                        isBottomPagerHosted = true,
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
                        BiliPaiNavEntryContentRole.HOME -> HomeScreen(
                                viewModel = homeViewModel,
                                onWeeklySeriesClick = { pushNavigation3Key(BiliPaiNavKey.WeeklySeries()) },
                                onVideoClick = { request -> navigateToHomeVideoInNavigation3(request) },
                                onSearchClick = {
                                    if (canNavigate(false)) pushNavigation3Key(BiliPaiNavKey.Search)
                                },
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
                                onAccountSwitchClick = if (
                                    appNavigationSettings.sidebarAccountSwitcherEnabled
                                ) {
                                    { sidebarAccountSwitcherVisible = true }
                                } else {
                                    null
                                },
                                onSettingsClick = { pushNavigation3Route(ScreenRoutes.Settings.route) },
                                onPluginsClick = { pushNavigation3Key(BiliPaiNavKey.PluginsSettings()) },
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
                                onLiveSearchClick = { pushNavigation3Key(BiliPaiNavKey.LiveSearch) },
                                onLiveAreaClick = { pushNavigation3Key(BiliPaiNavKey.LiveArea) },
                                onLiveFollowingClick = { pushNavigation3Key(BiliPaiNavKey.LiveFollowing) },
                                onLiveAreaDetailClick = { parentAreaId, areaId, title ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.LiveAreaDetail(
                                            parentAreaId = parentAreaId,
                                            areaId = areaId,
                                            title = title
                                        )
                                    )
                                },
                                onBangumiSeasonClick = { seasonId ->
                                    pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = seasonId))
                                },
                                onBangumiEpisodeClick = { seasonId, epId ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId)
                                    )
                                },
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
                        BiliPaiNavEntryContentRole.LISTEN_VIDEO ->
                            ListenVideoRoute(
                                onPlayFromHistory = { bvid, cid ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.AudioMode(sourceBvid = bvid, sourceCid = cid)
                                    )
                                },
                                onPlayCollection = { mediaId, seasonId, title ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.AudioMode(
                                            collectionMediaId = mediaId,
                                            collectionSeasonId = seasonId,
                                            collectionTitle = title,
                                        )
                                    )
                                },
                                onNowPlayingClick = { bvid, _ ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.AudioMode(sourceBvid = bvid)
                                    )
                                },
                                onPlayTracks = { tracks, clickedBvid ->
                                    val selection = resolveListenVideoPlaybackSelection(
                                        tracks = tracks,
                                        clickedBvid = clickedBvid
                                    )
                                    if (selection.items.isNotEmpty() && selection.startIndex >= 0) {
                                        PlaylistManager.setExternalPlaylist(
                                            items = selection.items,
                                            startIndex = selection.startIndex,
                                            source = ExternalPlaylistSource.FAVORITE
                                        )
                                        val clickedTrack = tracks.firstOrNull {
                                            it.bvid == clickedBvid
                                        }
                                        pushNavigation3Key(
                                            BiliPaiNavKey.AudioMode(
                                                sourceBvid = clickedBvid,
                                                sourceCid = clickedTrack?.cid ?: 0L
                                            )
                                        )
                                    }
                                },
                                onLogin = { pushNavigation3Key(BiliPaiNavKey.Login) }
                            )
                        BiliPaiNavEntryContentRole.HISTORY -> {
                                val historyViewModel: HistoryViewModel = viewModel()
                                val historySearchKey = key as? BiliPaiNavKey.HistorySearch
                                val historyNavigationScope = rememberCoroutineScope()
                                // Keep a history browsing session through playback and return.
                                // Background-work availability changes during card transitions
                                // must not trigger a new server ordering.
                                val historySessionSelected = !isBottomPagerHosted ||
                                    currentBottomNavItem == BottomNavItem.HISTORY
                                androidx.compose.runtime.LaunchedEffect(
                                    historyViewModel,
                                    historySessionSelected,
                                    historyBrowsingSessionKey,
                                ) {
                                    if (historySessionSelected) {
                                        historyViewModel.enterBrowsingSession(
                                            sessionKey = historyBrowsingSessionKey.takeIf { isBottomPagerHosted }
                                        )
                                    }
                                }
                                CommonListScreen(
                                    viewModel = historyViewModel,
                                    onBack = { performSystemBackAction() },
                                    globalHazeState = mainHazeState,
                                    scrollToTopChannel = historyScrollChannel,
                                    isCurrentPage = isBottomPagerPageActive,
                                    initialSearchQuery = historySearchKey?.query.orEmpty(),
                                    isSearchDestination = historySearchKey != null,
                                    onOpenSearchDestination = if (historySearchKey == null) {
                                        { query -> pushNavigation3Key(BiliPaiNavKey.HistorySearch(query)) }
                                    } else null,
                                    listScopedSearchChannel = if (historySearchKey == null) {
                                        historyListScopedSearchChannel
                                    } else {
                                        null
                                    },
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
                                                        initialVertical = isVertical,
                                                        sourceRoute = ScreenRoutes.History.route
                                                    )
                                                }
                                            }
                                            HistoryNavigationKind.CHEESE -> {
                                                if (historyItem != null && (historyItem.seasonId > 0 || historyItem.epid > 0)) {
                                                    pushNavigation3Key(
                                                        BiliPaiNavKey.BangumiPlayer(
                                                            seasonId = historyItem.seasonId,
                                                            epId = historyItem.epid,
                                                            resumePositionMs = resumePositionMs,
                                                            isCourse = true
                                                        )
                                                    )
                                                } else {
                                                    navigateToVideoInNavigation3(
                                                        lookupKey,
                                                        resolvedCid,
                                                        cover,
                                                        resumePositionMs = resumePositionMs,
                                                        initialVertical = isVertical,
                                                        sourceRoute = ScreenRoutes.History.route
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
                                                        initialVertical = isVertical,
                                                        sourceRoute = ScreenRoutes.History.route
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
                                                        initialVertical = isVertical,
                                                        sourceRoute = ScreenRoutes.History.route
                                                    )
                                                }
                                            }
                                            HistoryNavigationKind.VIDEO -> {
                                                navigateToVideoInNavigation3(
                                                    lookupKey,
                                                    resolvedCid,
                                                    cover,
                                                    resumePositionMs = resumePositionMs,
                                                    initialVertical = isVertical,
                                                    sourceRoute = ScreenRoutes.History.route
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.DYNAMIC -> DynamicScreen(
                            isCurrentPage = isBottomPagerPageActive,
                            onVideoClick = { bvid ->
                                navigateToVideoInNavigation3(
                                    bvid = bvid,
                                    cid = 0L,
                                    coverUrl = "",
                                    sourceRoute = ScreenRoutes.Dynamic.route
                                )
                            },
                            onBangumiClick = { seasonId, epId ->
                                if (seasonId > 0L || epId > 0L) {
                                    if (epId > 0L) {
                                        // 动态里的番剧卡带集数信息，直接播放该集
                                        pushNavigation3Key(
                                            BiliPaiNavKey.BangumiPlayer(
                                                seasonId = seasonId,
                                                epId = epId
                                            )
                                        )
                                    } else {
                                        pushNavigation3Route(ScreenRoutes.BangumiDetail.createRoute(seasonId, epId))
                                    }
                                }
                            },
                            onArticleClick = { articleId, title ->
                                coroutineScope.launch {
                                    when (val target = resolveArticleNavigationTarget(articleId)) {
                                        is ArticleNavigationTarget.NativeDynamic -> {
                                            pushNavigation3Route(
                                                ScreenRoutes.DynamicDetail.createRoute(target.dynamicId)
                                            )
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
                            onDynamicDetailClick = { dynamicId ->
                                pushNavigation3Route(ScreenRoutes.DynamicDetail.createRoute(dynamicId))
                            },
                            onUserClick = { mid -> pushNavigation3Route(ScreenRoutes.Space.createRoute(mid)) },
                            onTopicClick = { topicId ->
                                if (topicId > 0L) {
                                    pushNavigation3Key(BiliPaiNavKey.TopicDetail(topicId))
                                }
                            },
                            onTopicKeywordClick = { keyword ->
                                pushSearchRouteInNavigation3(keyword)
                            },
                            onLiveClick = { roomId, title, uname ->
                                pushNavigation3Route(ScreenRoutes.Live.createRoute(roomId, title, uname))
                            },
                            onMusicClick = { musicId ->
                                if (musicId > 0L) pushNavigation3Key(BiliPaiNavKey.MusicDetail(musicId))
                            },
                            onCollectionClick = { mediaId, ownerMid, title, url ->
                                if (mediaId > 0L && url.contains("medialist/detail/ml", ignoreCase = true)) {
                                    pushNavigation3Key(
                                        BiliPaiNavKey.SeasonSeriesDetail(
                                            type = "favorite",
                                            id = mediaId,
                                            mid = ownerMid,
                                            title = title,
                                        )
                                    )
                                } else if (url.isNotBlank()) {
                                    pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                                }
                            },
                            onCourseClick = { url, title ->
                                val courseNav = com.android.purebilibili.feature.bangumi.policy.parseCourseNavigation(url)
                                if (courseNav != null) {
                                    pushNavigation3Key(
                                        BiliPaiNavKey.BangumiPlayer(
                                            seasonId = courseNav.seasonId,
                                            epId = courseNav.epId,
                                            isCourse = true
                                        )
                                    )
                                } else if (url.isNotBlank()) {
                                    pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                                }
                            },
                            onBack = { pushNavigation3Route(ScreenRoutes.Home.route) },
                            onLoginClick = { pushNavigation3Key(BiliPaiNavKey.Login) },
                            onHomeClick = { pushNavigation3Route(ScreenRoutes.Home.route) },
                            publishSkinDecoration = dynamicPublishSkinDecoration,
                            globalHazeState = mainHazeState
                        )
                        BiliPaiNavEntryContentRole.SEARCH -> {
                            val homeState by homeViewModel.uiState.collectAsStateWithLifecycle(
                                context = kotlin.coroutines.EmptyCoroutineContext
                            )
                            val searchKey = key as? BiliPaiNavKey.Search
                            val initialSearchKeyword = searchKey?.keyword?.takeIf { it.isNotBlank() }
                                ?: effectiveInitialSearchKeyword.orEmpty()
                            com.android.purebilibili.feature.search.SearchResultContainerHost(
                                content = { openUpResult, openBangumiResult, sourceVisible ->
                                    SearchScreen(
                                        userFace = homeState.user.face,
                                        initialKeyword = initialSearchKeyword,
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
                                        onNavigateSearchTarget = { target ->
                                            openBilibiliNativeTargetInNavigation3(target)
                                        },
                                        onVideoClick = { bvid, cid, coverUrl ->
                                            navigateToVideoInNavigation3(
                                                bvid = bvid,
                                                cid = cid,
                                                coverUrl = coverUrl,
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
                                        onUpResultClick = openUpResult,
                                        onBangumiResultClick = openBangumiResult,
                                        backEnabled = sourceVisible,
                                        onCheeseClick = { seasonId, epId ->
                                            pushNavigation3Key(
                                                BiliPaiNavKey.BangumiPlayer(
                                                    seasonId = seasonId,
                                                    epId = epId,
                                                    isCourse = true
                                                )
                                            )
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
                                },
                                destination = { target, close, replace, ready, modifier ->
                                    Box(modifier) {
                                        if (target.isUp) {
                                            RenderSpaceDestination(BiliPaiNavKey.Space(target.id), close)
                                        } else {
                                            RenderBangumiDestination(
                                                BiliPaiNavKey.BangumiDetail(target.id),
                                                onBack = close,
                                                onSeasonChange = { replace(target.copy(id = it)) },
                                                contentReady = ready,
                                            )
                                        }
                                    }
                                },
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
                                            if (epId > 0L) {
                                                // 动态里的番剧卡带集数信息，直接播放该集
                                                pushNavigation3Key(
                                                    BiliPaiNavKey.BangumiPlayer(
                                                        seasonId = seasonId,
                                                        epId = epId
                                                    )
                                                )
                                            } else {
                                                pushNavigation3Key(
                                                    BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId)
                                                )
                                            }
                                        }
                                    },
                                    onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) },
                                    onTopicClick = { nestedTopicId ->
                                        if (nestedTopicId > 0L) {
                                            pushNavigation3Key(BiliPaiNavKey.TopicDetail(nestedTopicId))
                                        }
                                    },
                                    onTopicKeywordClick = { keyword ->
                                        pushSearchRouteInNavigation3(keyword)
                                    },
                                    onLiveClick = { roomId, title, uname ->
                                        pushNavigation3Key(BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname))
                                    },
                                    onMusicClick = { musicId ->
                                        if (musicId > 0L) pushNavigation3Key(BiliPaiNavKey.MusicDetail(musicId))
                                    },
                                    onCollectionClick = { mediaId, ownerMid, title, url ->
                                        if (mediaId > 0L && url.contains("medialist/detail/ml", ignoreCase = true)) {
                                            pushNavigation3Key(
                                                BiliPaiNavKey.SeasonSeriesDetail(
                                                    type = "favorite",
                                                    id = mediaId,
                                                    mid = ownerMid,
                                                    title = title,
                                                )
                                            )
                                        } else if (url.isNotBlank()) {
                                            pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                                        }
                                    },
                                    onCourseClick = { url, title ->
                                        val courseNav = com.android.purebilibili.feature.bangumi.policy.parseCourseNavigation(url)
                                        if (courseNav != null) {
                                            pushNavigation3Key(
                                                BiliPaiNavKey.BangumiPlayer(
                                                    seasonId = courseNav.seasonId,
                                                    epId = courseNav.epId,
                                                    isCourse = true
                                                )
                                            )
                                        } else if (url.isNotBlank()) {
                                            pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                                        }
                                    },
                                    onArticleClick = { articleId, title ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.ArticleDetail(articleId = articleId, title = title)
                                        )
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
                                accountSessionRefreshGeneration = accountSessionRefreshGeneration,
                                onBack = { pushNavigation3Route(ScreenRoutes.Home.route) },
                                onGoToLogin = { pushNavigation3Key(BiliPaiNavKey.Login) },
                                onLogoutSuccess = { homeViewModel.refresh() },
                                onAccountSwitchSuccess = { homeViewModel.refresh() },
                                onSettingsClick = { navigateFromProfile(ScreenRoutes.Settings.route) },
                                onSearchClick = { navigateFromProfile(ScreenRoutes.Search.route) },
                                onHistoryClick = { navigateFromProfile(ScreenRoutes.History.route) },
                                showHistoryService = shouldShowProfileHistoryService(
                                    visibleBottomBarItems.map { it.name }
                                ),
                                onFavoriteClick = { navigateFromProfile(ScreenRoutes.Favorite.route) },
                                onSubscriptionClick = {
                                    pushNavigation3Key(BiliPaiNavKey.FavoriteSubscribed)
                                },
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
                                onFansClick = { mid ->
                                    pushNavigation3Key(
                                        BiliPaiNavKey.Following(mid = mid, fans = true)
                                    )
                                },
                                onDownloadClick = { navigateFromProfile(ScreenRoutes.DownloadList.route) },
                                onWatchLaterClick = { navigateFromProfile(ScreenRoutes.WatchLater.route) },
                                onInboxClick = { navigateFromProfile(ScreenRoutes.Inbox.route) },
                                onVideoClick = { bvid -> navigateToVideoInNavigation3(bvid, 0L, "") },
                                onBangumiClick = { seasonId, epId ->
                                    if (seasonId > 0L || epId > 0L) {
                                        if (epId > 0L) {
                                            // 动态里的番剧卡带集数信息，直接播放该集
                                            pushNavigation3Key(
                                                BiliPaiNavKey.BangumiPlayer(
                                                    seasonId = seasonId,
                                                    epId = epId
                                                )
                                            )
                                        } else {
                                            pushNavigation3Key(
                                                BiliPaiNavKey.BangumiDetail(seasonId = seasonId, epId = epId)
                                            )
                                        }
                                    }
                                },
                                onBangumiMoreClick = { navigateFromProfile(ScreenRoutes.Bangumi.createRoute(1)) },
                                skinBackgroundImagePath = homeUiSkinDecoration?.profileBackgroundImagePath,
                                skinSquaredBackgroundImagePath = homeUiSkinDecoration?.profileSquaredBackgroundImagePath,
                                skinVideoBackgroundPath = homeUiSkinDecoration?.profileVideoBackgroundPath,
                                skinVideoPlayMode = homeUiSkinDecoration?.profileVideoPlayMode,
                                deferImmersiveRenderBudget = bottomPagerRenderBudget.deferProfileImmersiveBackground,
                                scrollToTopChannel = profileScrollChannel
                            )
                        }
                        BiliPaiNavEntryContentRole.VIDEO_DETAIL -> {
                            val videoKey = key as BiliPaiNavKey.VideoDetail
                            val activity = context as? android.app.Activity
                            val videoDetailOwner = LocalViewModelStoreOwner.current
                            val videoPlaybackViewModel:
                                com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel =
                                viewModel()
                            var isNavigatingToAudioMode by remember(videoKey) { mutableStateOf(false) }
                            DisposableEffect(videoKey, videoDetailOwner) {
                                if (videoDetailOwner != null) {
                                    videoDetailViewModelOwners[videoKey] = videoDetailOwner
                                }
                                onDispose {
                                    if (videoDetailViewModelOwners[videoKey] === videoDetailOwner) {
                                        videoDetailViewModelOwners.remove(videoKey)
                                    }
                                }
                            }
                            LaunchedEffect(navigation3BackStack.lastOrNull(), videoKey) {
                                if (navigation3BackStack.lastOrNull() == videoKey) {
                                    isNavigatingToAudioMode = false
                                }
                            }
                            val isImmediateVideoBackPreview =
                                navigation3BackStack.getOrNull(
                                    navigation3BackStack.lastIndex - 1
                                ) == videoKey
                            val bindVideoBackPreviewPlayer =
                                isImmediateVideoBackPreview &&
                                    shouldBindVideoDetailBackPreviewPlayer(
                                        currentKey = navigation3BackStack.lastOrNull(),
                                        previewKey = videoKey,
                                    )
                            val activateVideoBackPreviewPlayback =
                                bindVideoBackPreviewPlayer &&
                                    navigation3ReturnSession.isReturningFromDetail
                            val videoDetailPlaybackSessionActive =
                                shouldActivateVideoDetailPlaybackSession(
                                    currentKey = navigation3BackStack.lastOrNull(),
                                    detailKey = videoKey,
                                    isImmediateBackPreview = isImmediateVideoBackPreview,
                                    activateBackPreviewPlayback = activateVideoBackPreviewPlayback,
                                )
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
                                onUpClickWithVideo = { mid, targetBvid ->
                                    pushNavigation3Key(BiliPaiNavKey.Space(mid = mid, targetBvid = targetBvid))
                                },
                                miniPlayerManager = miniPlayerManager,
                                isInPipMode = isInPipMode,
                                isVisible = videoDetailPlaybackSessionActive &&
                                    navigation3BackStack.lastOrNull() !is BiliPaiNavKey.AudioMode,
                                isPlaybackSessionActive = videoDetailPlaybackSessionActive,
                                startInFullscreen = videoKey.fullscreen,
                                startAudioFromRoute = videoKey.startAudio,
                                autoEnterPortraitFromRoute = videoKey.autoPortrait,
                                initialVerticalFromRoute = videoKey.initialVertical,
                                directPortraitEntryFromRoute = videoKey.directPortraitEntry,
                                resumePositionMsFromRoute = videoKey.resumePositionMs,
                                openCommentRootRpidFromRoute = videoKey.commentRootRpid,
                                openCommentTargetRpidFromRoute = videoKey.commentTargetRpid,
                                sourceRouteForSharedElement = videoKey.sourceRoute,
                                keepLoadedContentForBackPreview = isImmediateVideoBackPreview,
                                bindLivePlayerForBackPreview = bindVideoBackPreviewPlayer,
                                predictiveBackCancelRecoveryGeneration =
                                    predictiveBackCancelRecoveryGeneration.takeIf {
                                        navigation3BackStack.lastOrNull() == videoKey
                                    } ?: 0,
                                isReturningFromDetail = navigation3ReturnSession.isReturningFromDetail,
                                isQuickReturningFromDetail = navigation3ReturnSession.isQuickReturnFromDetail,
                                onMarkReturningFromDetail = {
                                    markVideoReturnSession()
                                },
                                onClearReturningFromDetail = {
                                    navigation3ReturnSession = navigation3ReturnSession.clearReturning()
                                },
                                transitionEnabled = shouldEnableVideoDetailSharedTransition(
                                    cardTransitionEnabled = sharedVideoCardTransitionEnabled,
                                    sourceRoute = videoKey.sourceRoute,
                                ),
                                transitionEnterDurationMillis = navMotionSpec.slowFadeDurationMillis,
                                onBack = {
                                    if (!navigation3ProgrammaticBackDispatcher.dispatch()) {
                                        performSystemBackAction()
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
                                            mainBottomPagerState.switchToPage(homeIndex)
                                        }
                                        replaceNavigation3BackStack(
                                            popBiliPaiNavKeyToRoot(navigation3BackStack)
                                        )
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
                                onReplaceVideoDetail = { targetBvid, targetCid, targetCover, resumePositionMs ->
                                    val normalizedBvid = targetBvid.trim()
                                    if (normalizedBvid.isBlank() || normalizedBvid == videoKey.bvid) {
                                        false
                                    } else {
                                        val nextOpenId = maxOf(
                                            SystemClock.uptimeMillis(),
                                            lastVideoDetailOpenId + 1L,
                                        )
                                        lastVideoDetailOpenId = nextOpenId
                                        miniPlayerManager?.isNavigatingToVideo = true
                                        // The portrait recommendation has no matching source card
                                        // in the retained page. Clear the old card session so back
                                        // cannot morph the new video into the first video's card.
                                        navigation3ReturnSession = navigation3ReturnSession
                                            .recordVideoSourceRoute(null)
                                        replaceNavigation3TopWithKey(
                                            BiliPaiNavKey.VideoDetail(
                                                bvid = normalizedBvid,
                                                cid = targetCid.coerceAtLeast(0L),
                                                coverUrl = targetCover,
                                                resumePositionMs = resumePositionMs.coerceAtLeast(0L),
                                                openId = nextOpenId,
                                            )
                                        )
                                        true
                                    }
                                },
                                onVideoClick = { vid, options ->
                                    val targetCid = options?.getLong(
                                        com.android.purebilibili.feature.video.screen.VIDEO_NAV_TARGET_CID_KEY
                                    ) ?: 0L
                                    val coverUrl = options?.getString(
                                        com.android.purebilibili.feature.video.screen.VIDEO_NAV_COVER_URL_KEY
                                    ).orEmpty()
                                    // 全屏中点击下一集/相关视频：新详情页要继承全屏意图与封面，
                                    // 否则会中途掉出全屏。
                                    val startInFullscreen =
                                        com.android.purebilibili.feature.video.screen
                                            .resolveVideoNavigationStartInFullscreen(options)
                                    navigateToVideoInNavigation3(
                                        bvid = vid,
                                        cid = targetCid,
                                        coverUrl = coverUrl,
                                        fullscreen = startInFullscreen,
                                        sourceRoute = "video/${videoKey.bvid}"
                                    )
                                },
                                onBgmClick = { bgm ->
                                    val musicId = bgm.musicId.ifBlank {
                                        (BilibiliNavigationTargetParser.parse(bgm.jumpUrl) as? BilibiliNavigationTarget.Music)?.musicId.orEmpty()
                                    }
                                    if (musicId.isNotBlank()) {
                                        pushNavigation3Key(BiliPaiNavKey.BgmDetail(musicId, cid = videoKey.cid))
                                    } else if (bgm.jumpUrl.isNotBlank()) {
                                        pushNavigation3Key(BiliPaiNavKey.Web(bgm.jumpUrl, "发现音乐"))
                                    }
                                },
                                viewModel = videoPlaybackViewModel,
                            )
                        }
                        BiliPaiNavEntryContentRole.ONBOARDING ->
                            com.android.purebilibili.feature.onboarding.OnboardingScreen(
                                onFinish = {
                                    welcomePrefs.edit()
                                        .putBoolean(USER_AGREEMENT_ACK_KEY, true)
                                        // Keep legacy flags in sync so splash / older checks stay consistent.
                                        .putBoolean("first_launch_shown", true)
                                        .putBoolean(RELEASE_DISCLAIMER_ACK_KEY, true)
                                        .apply()
                                    replaceNavigation3BackStack(
                                        resolveInitialBiliPaiBackStack(
                                            firstRoute = ScreenRoutes.Home.route,
                                            onboardingRequired = false,
                                            openPortraitFeedOnStartup = launchToPortraitFeedOnStartupAtInit
                                        )
                                    )
                                },
                                onDisagree = {
                                    // Leave no residual task: disagree means exit the app.
                                    context.findActivity()?.finishAffinity()
                                },
                            )
                        BiliPaiNavEntryContentRole.SETTINGS ->
                            SettingsTabletEntry {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                    onOpenSourceLicensesClick = { pushNavigation3Key(BiliPaiNavKey.OpenSourceLicenses) },
                                    onAppearanceClick = { pushNavigation3Key(BiliPaiNavKey.AppearanceSettings) },
                                    onHomeClick = { pushNavigation3Key(BiliPaiNavKey.HomeSettings) },
                                    onAnimationClick = { pushNavigation3Key(BiliPaiNavKey.AnimationSettings) },
                                    onPlaybackClick = { pushNavigation3Key(BiliPaiNavKey.PlaybackSettings) },
                                    onPermissionClick = { pushNavigation3Key(BiliPaiNavKey.PermissionSettings) },
                                    onMessageNotificationClick = { pushNavigation3Key(BiliPaiNavKey.MessageNotificationSettings) },
                                    onPluginsClick = { pushNavigation3Key(BiliPaiNavKey.PluginsSettings()) },
                                    onSettingsShareClick = { pushNavigation3Key(BiliPaiNavKey.SettingsShare) },
                                    onWebDavBackupClick = { pushNavigation3Key(BiliPaiNavKey.WebDavBackup) },
                                    onNavigateToBottomBarSettings = { pushNavigation3Key(BiliPaiNavKey.BottomBarSettings) },
                                    onTipsClick = { pushNavigation3Key(BiliPaiNavKey.TipsSettings) },
                                    onReplayOnboardingClick = { pushNavigation3Route(ScreenRoutes.Onboarding.route) },
                                    onCategoryClick = { category ->
                                        pushNavigation3Key(resolveSettingsCategoryNavKey(category))
                                    },
                                    onSearchOpen = { pushNavigation3Key(BiliPaiNavKey.SettingsSearch) },
                                    mainHazeState = mainHazeState,
                                    forceSinglePaneContent = true,
                                    rootEntranceEnabled = !isBottomPagerHosted,
                                )
                            }
                        BiliPaiNavEntryContentRole.SETTINGS_CATEGORY -> {
                            val categoryKey = key as BiliPaiNavKey.SettingsCategory
                            SettingsTabletEntry {
                                SettingsCategoryScreen(
                                    category = categoryKey.category,
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                    onSettingsHome = {
                                        val rootIndex = navigation3BackStack.indexOfLast { it == BiliPaiNavKey.Settings }
                                        if (rootIndex >= 0) {
                                            replaceNavigation3BackStack(navigation3BackStack.take(rootIndex + 1))
                                        } else {
                                            val categoryIndex = navigation3BackStack.indexOfFirst { it is BiliPaiNavKey.SettingsCategory }
                                            val prefix = navigation3BackStack.take(categoryIndex.coerceAtLeast(1))
                                            replaceNavigation3BackStack(prefix + BiliPaiNavKey.Settings)
                                        }
                                    },
                                    onOpenSourceLicensesClick = { pushNavigation3Key(BiliPaiNavKey.OpenSourceLicenses) },
                                    onAppearanceClick = { pushNavigation3Key(BiliPaiNavKey.AppearanceSettings) },
                                    onHomeClick = { pushNavigation3Key(BiliPaiNavKey.HomeSettings) },
                                    onAnimationClick = { pushNavigation3Key(BiliPaiNavKey.AnimationSettings) },
                                    onPlaybackClick = { pushNavigation3Key(BiliPaiNavKey.PlaybackSettings) },
                                    onPermissionClick = { pushNavigation3Key(BiliPaiNavKey.PermissionSettings) },
                                    onMessageNotificationClick = { pushNavigation3Key(BiliPaiNavKey.MessageNotificationSettings) },
                                    onPluginsClick = { pushNavigation3Key(BiliPaiNavKey.PluginsSettings()) },
                                    onSettingsShareClick = { pushNavigation3Key(BiliPaiNavKey.SettingsShare) },
                                    onWebDavBackupClick = { pushNavigation3Key(BiliPaiNavKey.WebDavBackup) },
                                    onNavigateToBottomBarSettings = { pushNavigation3Key(BiliPaiNavKey.BottomBarSettings) },
                                    onTipsClick = { pushNavigation3Key(BiliPaiNavKey.TipsSettings) },
                                    onReplayOnboardingClick = { pushNavigation3Route(ScreenRoutes.Onboarding.route) },
                                    onCategoryClick = { category ->
                                        pushNavigation3Key(resolveSettingsCategoryNavKey(category))
                                    },
                                    onSearchOpen = { pushNavigation3Key(BiliPaiNavKey.SettingsSearch) },
                                    mainHazeState = mainHazeState,
                                    forceSinglePaneContent = true,
                                )
                            }
                        }
                        BiliPaiNavEntryContentRole.SETTINGS_SEARCH ->
                            SettingsTabletEntry {
                                SettingsSearchScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                    onCategoryClick = { category ->
                                        pushNavigation3Key(resolveSettingsCategoryNavKey(category))
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
                                    onAnimationClick = { pushNavigation3Key(BiliPaiNavKey.AnimationSettings) },
                                )
                            }
                        BiliPaiNavEntryContentRole.HOME_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.HomeSettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
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
                                    onBack = { performSystemBackAction() },
                                )
                            }
                        BiliPaiNavEntryContentRole.PLAYBACK_SETTINGS ->
                            SettingsTabletEntry {
                                PlaybackSettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { performSystemBackAction() },
                                )
                            }
                        BiliPaiNavEntryContentRole.PERMISSION_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.PermissionSettingsScreen(
                                    onBack = { performSystemBackAction() }
                                )
                            }
                        BiliPaiNavEntryContentRole.MESSAGE_NOTIFICATION_SETTINGS ->
                            SettingsTabletEntry {
                                com.android.purebilibili.feature.settings.MessageNotificationSettingsScreen(
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
                        BiliPaiNavEntryContentRole.AICU_QUERY -> {
                            val aicuKey = key as BiliPaiNavKey.AicuQuery
                            com.android.purebilibili.feature.aicu.AicuRoute(
                                uid = aicuKey.uid.takeIf { it > 0 },
                                initialCategory = com.android.purebilibili.data.model.response.AicuCategory.fromRoute(aicuKey.category),
                                onBack = { performSystemBackAction() },
                                onOpenTarget = { target -> pushNavigation3Key(target) },
                            )
                        }
                        BiliPaiNavEntryContentRole.WATCH_LATER -> {
                                val watchLaterViewModel: com.android.purebilibili.feature.watchlater.WatchLaterViewModel = viewModel()
                                val watchLaterSearchKey = key as? BiliPaiNavKey.WatchLaterSearch
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
                                    initialSearchQuery = watchLaterSearchKey?.query.orEmpty(),
                                    isSearchDestination = watchLaterSearchKey != null,
                                    onOpenSearchDestination = if (watchLaterSearchKey == null) {
                                        { query -> pushNavigation3Key(BiliPaiNavKey.WatchLaterSearch(query)) }
                                    } else null,
                                    listScopedSearchChannel = if (watchLaterSearchKey == null) {
                                        watchLaterListScopedSearchChannel
                                    } else {
                                        null
                                    },
                                    onVideoClick = { bvid, cid, resumePositionMs ->
                                        navigateToVideoInNavigation3(
                                            bvid = bvid,
                                            cid = cid,
                                            coverUrl = "",
                                            resumePositionMs = resumePositionMs,
                                            sourceRoute = ScreenRoutes.WatchLater.route
                                        )
                                    },
                                    onPlayAllAudioClick = { bvid, cid, resumePositionMs ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.AudioMode(
                                                sourceBvid = bvid,
                                                sourceCid = cid,
                                                sourceResumePositionMs = resumePositionMs
                                            )
                                        )
                                    },
                                    viewModel = watchLaterViewModel,
                                    globalHazeState = mainHazeState,
                                    isCurrentPage = isBottomPagerPageActive,
                                    scrollToTopChannel = watchLaterScrollChannel
                                )
                            }
                        BiliPaiNavEntryContentRole.FOLLOWING -> {
                                val followingKey = key as BiliPaiNavKey.Following
                                if (followingKey.fans) {
                                    com.android.purebilibili.feature.following.FansListScreen(
                                        mid = followingKey.mid,
                                        onBack = { performSystemBackAction() },
                                        onUserClick = { userMid -> pushNavigation3Key(BiliPaiNavKey.Space(userMid)) },
                                        onFansClick = { userMid -> pushNavigation3Key(BiliPaiNavKey.Following(userMid, fans = true)) },
                                    )
                                } else com.android.purebilibili.feature.following.FollowingListScreen(
                                    mid = followingKey.mid,
                                    onBack = { performSystemBackAction() },
                                    onFansClick = { userMid -> pushNavigation3Key(BiliPaiNavKey.Following(userMid, fans = true)) },
                                    onUserClick = { userMid -> pushNavigation3Key(BiliPaiNavKey.Space(userMid)) }
                                )
                            }
                        BiliPaiNavEntryContentRole.UPOWER_RANK -> {
                                val upowerRankKey = key as BiliPaiNavKey.UpowerRank
                                com.android.purebilibili.feature.space.SpaceUpowerRankScreen(
                                    mid = upowerRankKey.mid,
                                    name = upowerRankKey.name,
                                    count = upowerRankKey.count,
                                    onBack = { performSystemBackAction() },
                                    onUserClick = { userMid -> pushNavigation3Key(BiliPaiNavKey.Space(userMid)) }
                                )
                            }
                        BiliPaiNavEntryContentRole.MEMBER_GUARD -> {
                                val memberGuardKey = key as BiliPaiNavKey.MemberGuard
                                com.android.purebilibili.feature.space.SpaceMemberGuardScreen(
                                    mid = memberGuardKey.mid,
                                    name = memberGuardKey.name,
                                    count = memberGuardKey.count,
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
                                // 底栏/顶栏进入的主直播首页：无返回箭头，与 BiliPai 主 tab 一致。
                                showNavigationBack = false,
                                scrollToTopChannel = liveScrollChannel,
                                onLiveClick = { roomId, title, uname ->
                                    pushNavigation3Key(BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname))
                                },
                                onSearchClick = { pushNavigation3Key(BiliPaiNavKey.LiveSearch) },
                                onMatchClick = {
                                    pushNavigation3Key(
                                        BiliPaiNavKey.Web(
                                            url = "https://www.bilibili.com/match/",
                                            title = "电竞赛事"
                                        )
                                    )
                                },
                                onAreaListClick = { pushNavigation3Key(BiliPaiNavKey.LiveArea) },
                                onFollowingClick = { pushNavigation3Key(BiliPaiNavKey.LiveFollowing) },
                                onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) },
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
                                    onSearchClick = { pushNavigation3Key(BiliPaiNavKey.LiveSearch) },
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
                            com.android.purebilibili.feature.message.MessageCenterScreen(
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
                                onOpenSessionFullScreen = { talkerId, sessionType, userName ->
                                    pushNavigation3Key(BiliPaiNavKey.Chat(talkerId, sessionType, userName))
                                },
                                onNavigateToVideo = { bvid ->
                                    navigateToVideoInNavigation3(bvid, 0L, "")
                                },
                                onOpenBilibiliLink = ::openBilibiliLinkInNavigation3
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
                                val favoriteSearchKey = key as? BiliPaiNavKey.FavoriteSearch
                                CommonListScreen(
                                    viewModel = favoriteViewModel,
                                    onBack = { performSystemBackAction() },
                                    globalHazeState = mainHazeState,
                                    scrollToTopChannel = favoriteScrollChannel,
                                    isCurrentPage = isBottomPagerPageActive,
                                    initialSearchQuery = favoriteSearchKey?.query.orEmpty(),
                                    initialFavoriteSearchScope = favoriteSearchKey?.scope
                                        ?: com.android.purebilibili.data.model.response.FavoriteSearchScope.CURRENT_FOLDER,
                                    initialFavoriteSubscribed = key == BiliPaiNavKey.FavoriteSubscribed,
                                    isSearchDestination = favoriteSearchKey != null,
                                    onOpenSearchDestination = if (favoriteSearchKey == null) {
                                        { query -> pushNavigation3Key(BiliPaiNavKey.FavoriteSearch(query)) }
                                    } else null,
                                    listScopedSearchChannel = if (favoriteSearchKey == null) {
                                        favoriteListScopedSearchChannel
                                    } else {
                                        null
                                    },
                                    onVideoClick = { bvid, cid, cover, isVertical ->
                                        navigateToVideoInNavigation3(
                                            bvid = bvid,
                                            cid = cid,
                                            coverUrl = cover,
                                            initialVertical = isVertical,
                                            sourceRoute = ScreenRoutes.Favorite.route
                                        )
                                    },
                                    onUpClick = { mid ->
                                        pushNavigation3Route(ScreenRoutes.Space.createRoute(mid))
                                    },
                                    onFavoriteBangumiClick = { seasonId ->
                                        pushNavigation3Key(BiliPaiNavKey.BangumiDetail(seasonId = seasonId))
                                    },
                                    onFavoriteCheeseClick = { seasonId ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.BangumiPlayer(
                                                seasonId = seasonId,
                                                epId = 0L,
                                                isCourse = true
                                            )
                                        )
                                    },
                                    onFavoriteArticleClick = { articleId, title ->
                                        pushNavigation3Route(
                                            ScreenRoutes.ArticleDetail.createRoute(articleId, title)
                                        )
                                    },
                                    onFavoriteTopicClick = { topicId ->
                                        pushNavigation3Key(BiliPaiNavKey.TopicDetail(topicId))
                                    },
                                    onFavoriteWebClick = { url, title ->
                                        if (url.isNotBlank()) {
                                            pushNavigation3Route(ScreenRoutes.Web.createRoute(url, title))
                                        }
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
                                        pushNavigation3Key(
                                            BiliPaiNavKey.AudioMode(
                                                sourceBvid = bvid,
                                                sourceCid = cid
                                            )
                                        )
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.LIKED_VIDEOS -> {
                                val likedVideosKey = key as? BiliPaiNavKey.LikedVideos
                                val targetMid = likedVideosKey?.mid?.takeIf { it > 0L }
                                val ownerName = likedVideosKey?.ownerName?.takeIf { it.isNotBlank() }.orEmpty()
                                val isCoinArchive = likedVideosKey?.isCoinArchive == true
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val application = context.applicationContext as android.app.Application
                                val likedVideosViewModel: LikedVideosViewModel = viewModel(
                                    key = "${if (isCoinArchive) "coin_videos" else "liked_videos"}_${targetMid ?: "self"}",
                                    factory = com.android.purebilibili.feature.list.LikedVideosViewModelFactory(
                                        application = application,
                                        targetMid = targetMid,
                                        ownerName = ownerName,
                                        isCoinArchive = isCoinArchive,
                                    )
                                )
                                val sourceRoute = (key as? BiliPaiNavKey)?.toLegacyRoute()
                                    ?: ScreenRoutes.LikedVideos.route
                                CommonListScreen(
                                    viewModel = likedVideosViewModel,
                                    onBack = { performSystemBackAction() },
                                    globalHazeState = mainHazeState,
                                    onVideoClick = { bvid, cid, cover, isVertical ->
                                        navigateToVideoInNavigation3(
                                            bvid = bvid,
                                            cid = cid,
                                            coverUrl = cover,
                                            initialVertical = isVertical,
                                            sourceRoute = sourceRoute
                                        )
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.LOGIN -> LoginScreen(
                                onClose = { performSystemBackAction() },
                                onLoginSuccess = {
                                    accountSessionRefreshGeneration += 1
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
                                    sourceRoute = storyKey.sourceRoute,
                                    transitionEnabled = cardTransitionEnabled,
                                    isActive = isBottomPagerPageActive,
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid, cid, _ -> navigateToVideoInNavigation3(bvid, cid, "") },
                                    onUserClick = { mid -> pushNavigation3Route(ScreenRoutes.Space.createRoute(mid)) },
                                    onSearchClick = { pushNavigation3Key(BiliPaiNavKey.Search) }
                                )
                            }
                        BiliPaiNavEntryContentRole.AUDIO_MODE -> {
                                val audioModeKey = key as BiliPaiNavKey.AudioMode
                                val previousVideoKey = remember(audioModeKey) {
                                    navigation3BackStack
                                        .takeWhile { it != audioModeKey }
                                        .lastOrNull() as? BiliPaiNavKey.VideoDetail
                                }
                                val sharedVideoOwner =
                                    previousVideoKey?.let(videoDetailViewModelOwners::get)
                                val viewModel: com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel =
                                    if (sharedVideoOwner != null) {
                                        viewModel(viewModelStoreOwner = sharedVideoOwner)
                                    } else {
                                        viewModel()
                                    }
                                DisposableEffect(Unit) {
                                    onAudioModeEnter()
                                    onDispose {
                                        onAudioModeExit()
                                    }
                                }
                                val initialLoadRequest = resolveAudioModeInitialLoadRequest(
                                    key = audioModeKey,
                                    hasDisplayState =
                                        viewModel.uiState.value is
                                            com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState.Success
                                )
                                val renderAudioPlayer: @Composable (String, Long) -> Unit = { bvid, cid ->
                                    com.android.purebilibili.feature.video.screen.AudioModeScreen(
                                        viewModel = viewModel,
                                        onBack = { performSystemBackAction() },
                                        onVideoModeClick = { currentBvid, currentCid ->
                                            replaceNavigation3BackStack(
                                                popBiliPaiNavKey(navigation3BackStack)
                                            )
                                            navigateToVideoInNavigation3(currentBvid, currentCid, "")
                                        },
                                        isInPipMode = isInPipMode,
                                        initialBvid = bvid,
                                        initialCid = cid,
                                        initialResumePositionMs = initialLoadRequest?.resumePositionMs ?: 0L,
                                        titleOverride = audioModeKey.collectionTitle.takeIf { bvid.isBlank() && it.isNotBlank() },
                                    )
                                }
                                if (audioModeKey.collectionMediaId > 0L || audioModeKey.collectionSeasonId > 0L) {
                                    com.android.purebilibili.feature.audio.screen.ListenVideoCollectionPlayer(
                                        mediaId = audioModeKey.collectionMediaId,
                                        seasonId = audioModeKey.collectionSeasonId,
                                        title = audioModeKey.collectionTitle,
                                        onBack = { performSystemBackAction() },
                                        player = renderAudioPlayer,
                                    )
                                } else {
                                    renderAudioPlayer(initialLoadRequest?.bvid.orEmpty(), initialLoadRequest?.cid ?: 0L)
                                }
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
                                val seasonSeriesSourceRoute =
                                    com.android.purebilibili.navigation3.normalizeBiliPaiVideoSourceRoute(
                                        seasonSeriesKey.toLegacyRoute()
                                    ) ?: seasonSeriesKey.toLegacyRoute()

                                CompositionLocalProvider(
                                    LocalVideoCardSharedElementSourceRoute provides seasonSeriesSourceRoute
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
                                                sourceRoute = seasonSeriesSourceRoute
                                            )
                                        },
                                        onUpClick = { mid ->
                                            pushNavigation3Route(ScreenRoutes.Space.createRoute(mid))
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
                                    onPlayEpisode = { seasonId, episode ->
                                        pushNavigation3Key(
                                            BiliPaiNavKey.BangumiPlayer(
                                                seasonId = seasonId,
                                                epId = episode.id,
                                                preferredAid = episode.aid,
                                                isCourse = episode.from == "pugv" ||
                                                    episode.playable || episode.episodeCanView,
                                            )
                                        )
                                    },
                                    onReviewsClick = { mediaId, title ->
                                        if (mediaId > 0L) {
                                            pushNavigation3Key(BiliPaiNavKey.BangumiReview(mediaId, title))
                                        }
                                    },
                                    onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) },
                                    initialType = bangumiKey.initialType
                                )
                            }
                        BiliPaiNavEntryContentRole.BANGUMI_PLAYER -> {
                                val playerKey = key as BiliPaiNavKey.BangumiPlayer
                                com.android.purebilibili.feature.bangumi.BangumiPlayerScreen(
                                    seasonId = playerKey.seasonId,
                                    epId = playerKey.epId,
                                    resumePositionMs = playerKey.resumePositionMs,
                                    isCourse = playerKey.isCourse,
                                    preferredAid = playerKey.preferredAid,
                                    onBack = { performSystemBackAction() },
                                    onNavigateToLogin = { pushNavigation3Key(BiliPaiNavKey.Login) },
                                    onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) },
                                    onOpenBilibiliLink = { url ->
                                        pushNavigation3Key(BiliPaiNavKey.Web(url = url))
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.WEEKLY_SERIES -> {
                            val weeklyKey = key as BiliPaiNavKey.WeeklySeries
                            com.android.purebilibili.feature.home.WeeklySeriesScreen(
                                initialNumber = weeklyKey.number,
                                onBack = { performSystemBackAction() },
                                onVideoClick = { video, videos ->
                                    PlaylistManager.setExternalPlaylist(
                                        items = videos.map { item ->
                                            com.android.purebilibili.feature.video.player.PlaylistItem(
                                                bvid = item.bvid, cid = item.cid, title = item.title,
                                                cover = item.pic, owner = item.owner.name,
                                                ownerFace = item.owner.face, duration = item.duration.toLong()
                                            )
                                        },
                                        startIndex = videos.indexOfFirst { it.bvid == video.bvid }.coerceAtLeast(0)
                                    )
                                    PlaylistManager.setPlayMode(com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL)
                                    navigateToVideoInNavigation3(video.bvid, video.cid, video.pic)
                                }
                            )
                        }
                        BiliPaiNavEntryContentRole.BGM_DETAIL -> {
                            val bgmKey = key as BiliPaiNavKey.BgmDetail
                            com.android.purebilibili.feature.audio.bgm.BgmDetailScreen(
                                musicId = bgmKey.musicId, aid = bgmKey.aid, cid = bgmKey.cid,
                                showVideos = bgmKey.showVideos,
                                onBack = { performSystemBackAction() },
                                onVideosClick = { pushNavigation3Key(bgmKey.copy(showVideos = true)) },
                                onVideoClick = { bvid, cid, cover -> navigateToVideoInNavigation3(bvid, cid, cover) },
                                onUserClick = { pushNavigation3Key(BiliPaiNavKey.Space(it)) },
                                onCommentClick = { oid, root, target, type ->
                                    pushNavigation3Key(BiliPaiNavKey.CommentDetail(oid, root, target, type))
                                },
                                onLinkClick = { url -> openBilibiliLinkInNavigation3(url) },
                                onLogin = { pushNavigation3Key(BiliPaiNavKey.Login) },
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
                                    onBack = { performSystemBackAction() },
                                    onVideoModeClick = { currentBvid, currentCid ->
                                        replaceNavigation3BackStack(
                                            popBiliPaiNavKey(navigation3BackStack)
                                        )
                                        navigateToVideoInNavigation3(currentBvid, currentCid, "")
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.SPACE -> {
                            RenderSpaceDestination(key as BiliPaiNavKey.Space) { performSystemBackAction() }
                        }
                        BiliPaiNavEntryContentRole.WEB -> {
                                val webKey = key as BiliPaiNavKey.Web
                                com.android.purebilibili.feature.web.WebViewScreen(
                                    url = webKey.url,
                                    title = webKey.title.ifEmpty { null },
                                    onBack = { performSystemBackAction() },
                                    onVideoClick = { bvid ->
                                        replaceNavigation3BackStack(
                                            popBiliPaiNavKey(navigation3BackStack)
                                        )
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
                                    onBangumiClick = { seasonId, epId, mediaId ->
                                        replaceNavigation3TopWithKey(
                                            BiliPaiNavKey.BangumiDetail(
                                                seasonId = seasonId,
                                                epId = epId,
                                                mediaId = mediaId
                                            )
                                        )
                                    },
                                    onMusicClick = { musicId ->
                                        val musicRoute = ScreenRoutes.createMusicRoute(musicId)
                                        if (musicRoute != null) {
                                            replaceNavigation3TopWithKey(legacyRouteToBiliPaiNavKey(musicRoute))
                                        } else {
                                            replaceNavigation3BackStack(
                                                popBiliPaiNavKey(navigation3BackStack)
                                            )
                                        }
                                    }
                                )
                            }
                        BiliPaiNavEntryContentRole.COMMENT_DETAIL -> {
                            val commentKey = key as BiliPaiNavKey.CommentDetail
                            com.android.purebilibili.feature.comment.CommentDetailScreen(
                                oid = commentKey.oid,
                                rootId = commentKey.rootId,
                                targetId = commentKey.targetId,
                                type = commentKey.type,
                                enterUri = commentKey.enterUri,
                                onBack = { performSystemBackAction() },
                                onOpenLink = ::openMessageLinkInNavigation3,
                                onUserClick = { mid -> pushNavigation3Key(BiliPaiNavKey.Space(mid)) }
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
                                        onTopicClick = { topicId ->
                                            if (topicId > 0L) {
                                                pushNavigation3Key(BiliPaiNavKey.TopicDetail(topicId))
                                            }
                                        },
                                        onTopicKeywordClick = { keyword ->
                                            pushSearchRouteInNavigation3(keyword)
                                        },
                                        onArticleClick = { articleId, title ->
                                            pushNavigation3Key(
                                                BiliPaiNavKey.ArticleDetail(articleId = articleId, title = title)
                                            )
                                        },
                                        onLiveClick = { roomId, title, uname ->
                                            pushNavigation3Key(
                                                BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = uname)
                                            )
                                        },
                                        onMusicClick = { musicId ->
                                            if (musicId > 0L) pushNavigation3Key(BiliPaiNavKey.MusicDetail(musicId))
                                        },
                                        onCollectionClick = { mediaId, ownerMid, title, url ->
                                            if (mediaId > 0L && url.contains("medialist/detail/ml", ignoreCase = true)) {
                                                pushNavigation3Key(
                                                    BiliPaiNavKey.SeasonSeriesDetail(
                                                        type = "favorite",
                                                        id = mediaId,
                                                        mid = ownerMid,
                                                        title = title,
                                                    )
                                                )
                                            } else if (url.isNotBlank()) {
                                                pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                                            }
                                        },
                                        onCourseClick = { url, title ->
                                            val courseNav = com.android.purebilibili.feature.bangumi.policy.parseCourseNavigation(url)
                                            if (courseNav != null) {
                                                pushNavigation3Key(
                                                    BiliPaiNavKey.BangumiPlayer(
                                                        seasonId = courseNav.seasonId,
                                                        epId = courseNav.epId,
                                                        isCourse = true
                                                    )
                                                )
                                            } else if (url.isNotBlank()) {
                                                pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = title))
                                            }
                                        },
                                        onDynamicDetailClick = { targetDynamicId ->
                                            pushNavigation3Key(BiliPaiNavKey.DynamicDetail(targetDynamicId))
                                        },
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
                                        replaceNavigation3BackStack(
                                            popBiliPaiNavKey(navigation3BackStack)
                                        )
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
                            RenderBangumiDestination(
                                key as BiliPaiNavKey.BangumiDetail,
                                onBack = { performSystemBackAction() },
                                onSeasonChange = { replaceNavigation3TopWithKey(BiliPaiNavKey.BangumiDetail(seasonId = it)) },
                            )
                        }
                        BiliPaiNavEntryContentRole.BANGUMI_REVIEW -> {
                                val reviewKey = key as BiliPaiNavKey.BangumiReview
                                com.android.purebilibili.feature.bangumi.BangumiReviewScreen(
                                    mediaId = reviewKey.mediaId,
                                    title = reviewKey.title,
                                    onBack = { performSystemBackAction() },
                                    onOpenWeb = { url, webTitle ->
                                        pushNavigation3Key(BiliPaiNavKey.Web(url = url, title = webTitle))
                                    }
                                )
                            }
                        }
                    }

                BiliPaiNavDisplayHost(
                    backStack = navigation3BackStack,
                    activeMainHostRoute = activeBottomTabRoute,
                    cardTransitionEnabled =
                        com.android.purebilibili.navigation3.resolveVideoCardTransitionEnabledForSource(
                            cardTransitionEnabled = sharedVideoCardTransitionEnabled,
                            relatedVideoTransitionEnabled = relatedVideoTransitionEnabled,
                            sourceRoute = navigation3SourceMetadata.sourceRoute,
                        ),
                    videoTransitionRealtimeBlurEnabled = videoTransitionRealtimeBlurEnabled,
                    isLightBackground = isLightBackground,
                    reduceMotion = systemReduceMotion,
                    videoSharedTransitionDurationMillis =
                        effectiveVideoCardTransitionDurationMillis,
                    videoCardClock = videoCardTransitionClock,
                    predictiveBackAnimationStyle = predictiveBackAnimationStyle,
                    predictiveBackExitDirection = predictiveBackExitDirection,
                    miuixTransitionBlurEnabled =
                        appNavigationSettings.miuixTransitionBlurEnabled,
                    miuixPredictiveBackMaxProgressPercent =
                        appNavigationSettings.miuixPredictiveBackMaxProgressPercent,
                    videoSharedReturnGestureFollowEnabled =
                        appNavigationSettings.videoSharedReturnGestureFollowEnabled,
                    videoSharedReturnGestureTranslationEnabled =
                        appNavigationSettings.videoSharedReturnGestureTranslationEnabled,
                    sourceMetadata = navigation3SourceMetadata,
                    programmaticBackDispatcher = navigation3ProgrammaticBackDispatcher,
                    // List cover waits for the live handoff window; list info is native throughout return.
                    videoReturnContentFollowProgressEnabled =
                        appNavigationSettings.videoReturnContentFollowProgressEnabled,
                    preferWholeCardReturn = false,
                    onBack = { performSystemBackAction() },
                    onPrepareVideoCardSharedReturn = {
                        // 普通返回(顶部按钮/系统手势提交)兜底预热。
                        maybePrefetchHomeCoversForVideoReturn()
                        val previousKey =
                            navigation3BackStack.getOrNull(navigation3BackStack.lastIndex - 1)
                        markNavigation3VideoReturnBeforeBackAction(targetKey = previousKey)
                        navigation3ReturnSession.isQuickReturnFromDetail
                    },
                    onRelatedVideoDetailReturned = {
                        navigation3ReturnSession =
                            navigation3ReturnSession.restorePreviousVideoSourceAfterRelatedReturn()
                        CardPositionManager.restoreVideoSourceKey(
                            navigation3ReturnSession.lastVideoSourceKey
                        )
                    },
                    restorePreviousVideoSourceOnDetailReturn =
                        navigation3ReturnSession.previousTransitionSessions.isNotEmpty() ||
                            navigation3ReturnSession.previousVideoSources.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            navigationHostOriginInRoot = coordinates.positionInRoot()
                        },
                ) { key ->
                    navigation3SaveableStateHolder.SaveableStateProvider(
                        key = resolveNavigation3SaveableStateKey(key)
                    ) {
                        VideoCardTransitionBackgroundRouteContent(key) {
                            RenderNavigationContent(key)
                        }
                    }
                }
                }
            } // End of Content Box
            } // End of navigation content row

            val isLandscapeNowPlaying =
                androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val effectiveAudioDockRoute = bottomBarMountRoute ?: currentRoute
            val isPlayerNowPlayingDestination = isAudioNowPlayingPlayerDestination(effectiveAudioDockRoute)
            val showAudioNowPlayingInDock = resolveAudioNowPlayingVisible(
                sessionActive = audioNowPlayingActive,
                isOnAudioModeScreen = currentNavigation3Key is BiliPaiNavKey.AudioMode,
                isInPipMode = isInPipMode,
                hasCurrentItem = audioNowPlayingItem != null,
                barEnabled = audioNowPlayingBarEnabled,
                isInMiniMode = miniPlayerManager?.isMiniMode == true,
                isLandscape = isLandscapeNowPlaying,
                isPlayerDestination = isPlayerNowPlayingDestination
            )
            val isReturningSameAudioVideo = navigation3ReturnSession.isReturningFromDetail &&
                navigation3ReturnSession.transitionSession?.bvid == audioNowPlayingItem?.bvid
            val isPlayerIndependentDestination = isAudioNowPlayingPlayerDestination(currentRoute) || isReturningSameAudioVideo
            val showAudioNowPlayingIndependent = resolveAudioNowPlayingVisible(
                sessionActive = audioNowPlayingActive,
                isOnAudioModeScreen = currentNavigation3Key is BiliPaiNavKey.AudioMode,
                isInPipMode = isInPipMode,
                hasCurrentItem = audioNowPlayingItem != null,
                barEnabled = audioNowPlayingBarEnabled,
                isInMiniMode = miniPlayerManager?.isMiniMode == true,
                isVideoDetailDestination = isVideoDetailDestination,
                isLandscape = isLandscapeNowPlaying,
                isPlayerDestination = isPlayerIndependentDestination
            )
            // 发布“听视频小横条是否悬浮在内容上方”，供首页/动态等悬浮元素避让
            androidx.compose.runtime.SideEffect {
                com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
                    .publishBarOverlayVisible(
                        showAudioNowPlayingInDock || showAudioNowPlayingIndependent
                    )
            }

            val capsuleOwnsNowPlayingPixels =
                navigation3ReturnSession.transitionSession?.sourceChromeSnapshot?.isNowPlayingBar == true &&
                    videoCardChromeExposure in setOf(
                        VideoCardTransitionExposure.Opening,
                        VideoCardTransitionExposure.BackPreview,
                        VideoCardTransitionExposure.Returning,
                        VideoCardTransitionExposure.Restoring,
                    )
            val audioNowPlayingHandoff =
                if (capsuleOwnsNowPlayingPixels ||
                    (navigation3ReturnSession.isReturningFromDetail && driveBottomBarByProgress)
                ) {
                    NowPlayingBarHandoffState.Returning(
                        targetBvid = navigation3ReturnSession.transitionSession?.bvid,
                        isSourceOwner = capsuleOwnsNowPlayingPixels || videoCardSourceChromeVisible,
                        morphDepthProvider = { videoCardTransitionClock.depthProgress() },
                    )
                } else {
                    NowPlayingBarHandoffState.Idle
                }

            if (bottomBarCanMount) {
                val bottomBarModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .then(
                        if (driveBottomBarByProgress) {
                            Modifier.videoCardTransitionChromeReveal(
                                revealProvider = {
                                    resolveVideoCardTransitionChromeReveal(
                                        videoCardTransitionClock.depthProgress(),
                                    )
                                },
                                slideDown = true,
                            )
                        } else {
                            Modifier
                        },
                    )

                Box(modifier = bottomBarModifier) {
                    BottomBarMatchedDockVisibility(
                        visibleState = bottomBarVisibilityState,
                        edge = BottomBarMatchedDockEdge.BOTTOM,
                        enterFadeDurationMillis = navMotionSpec.slowFadeDurationMillis,
                        exitFadeDurationMillis = navMotionSpec.fastFadeDurationMillis
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        val dockAudioContent: LinkedDockNowPlayingSlot? =
                            if (showAudioNowPlayingInDock && audioNowPlayingItem != null) {
                                { audioModifier, dockMergeProgress, iconOnlyProgress, surfaceMergeProgress,
                                    compactClick, layoutStable ->
                                    val playbackManager = miniPlayerManager ?: MiniPlayerManager.getInstance(context)
                                    AudioNowPlayingBar(
                                        state = AudioNowPlayingBarState(
                                            bvid = audioNowPlayingItem.bvid,
                                            title = audioNowPlayingItem.title,
                                            artist = audioNowPlayingItem.owner,
                                            artistAvatarUrl = audioNowPlayingItem.ownerFace,
                                            coverUrl = audioNowPlayingItem.cover,
                                            isPlaying = playbackManager.isPlaying,
                                            playbackSpeed = playbackManager.player?.playbackParameters?.speed ?: 1f
                                        ),
                                        onCompactClick = compactClick,
                                        isLayoutStable = layoutStable && !driveBottomBarByProgress,
                                        sourceRoute = currentRoute ?: ScreenRoutes.Home.route,
                                        handoff = audioNowPlayingHandoff,
                                        onExpand = {
                                            val expandRoute = resolveAudioNowPlayingBarExpandRoute(
                                                opensAudioMode = audioNowPlayingBarOpensAudioMode,
                                                bvid = audioNowPlayingItem.bvid,
                                                cid = audioNowPlayingItem.cid,
                                                coverUrl = audioNowPlayingItem.cover,
                                            )
                                            if (audioNowPlayingBarOpensAudioMode) {
                                                pushNavigation3Route(expandRoute)
                                            } else {
                                                navigateToVideoRouteInNavigation3(
                                                    route = expandRoute,
                                                    sourceRoute = currentRoute ?: ScreenRoutes.Home.route,
                                                )
                                            }
                                        },
                                        onPlayPause = {
                                            if (!playbackManager.togglePlayPause()) {
                                                pushNavigation3Route(
                                                    ScreenRoutes.AudioMode.createRoute(
                                                        bvid = audioNowPlayingItem.bvid,
                                                        cid = audioNowPlayingItem.cid
                                                    )
                                                )
                                            }
                                        },
                                        onSkipNext = { playbackManager.playNext() },
                                        onSkipPrevious = { playbackManager.playPrevious() },
                                        onCancelPlayback = { playbackManager.pausePlayback() },
                                        onDismiss = {
                                            playbackManager.pausePlayback()
                                            AudioNowPlayingSession.dismiss()
                                        },
                                        expandDestinationLabel = if (audioNowPlayingBarOpensAudioMode) {
                                            "听视频"
                                        } else {
                                            "视频详情页"
                                        },
                                        glassEnabled = effectiveHomeSettings.androidNativeLiquidGlassEnabled,
                                        blurEnabled = isBottomBarBlurEnabled,
                                        hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                                        miuixBackdrop = bottomBarBackdrop,
                                        liquidGlassTuning = liquidGlassRenderConfig.tuning,
                                        liftAboveBottomBar = false,
                                        consumeNavigationBarsPadding = false,
                                        dockHosted = isBottomBarFloating,
                                        dockMergeProgress = dockMergeProgress,
                                        surfaceMergeProgress = surfaceMergeProgress,
                                        iconOnlyProgress = iconOnlyProgress,
                                        modifier = audioModifier
                                    )
                                }
                            } else null
                        if (!isBottomBarFloating) {
                            dockAudioContent?.invoke(Modifier, { 0f }, { 0f }, { 0f }, null, true)
                        }
                        if (isBottomBarFloating) {
                            val isBookPosture = appWindowAdaptiveInfo.posture == com.android.purebilibili.core.util.AppFoldPosture.Book
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isBookPosture) {
                                            Modifier.padding(start = 24.dp)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = if (isBookPosture) Alignment.CenterStart else Alignment.Center
                            ) {
                                FrostedBottomBar(
                                    nowPlayingContent = dockAudioContent,
                                    currentItem = currentBottomNavItem,
                                    onItemClick = handleNavItemClick,
                                    onHomeDoubleTap = {
                                        homeScrollChannel.trySend(
                                            com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH
                                        )
                                    },
                                    onDynamicDoubleTap = {
                                        dynamicScrollChannel.trySend(
                                            DynamicScrollRequest.SCROLL_TO_TOP_AND_REFRESH
                                        )
                                    },
                                    onSearchClick = { requestSearchFromBottomBar() },
                                    onSearchKeywordSubmit = submitBottomBarSearchKeyword,
                                    searchLaunchKey = bottomBarSearchLaunchKey,
                                    hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                                    isFloating = true,
                                    labelMode = bottomBarLabelMode,
                                    visibleItems = visibleBottomBarItems,
                                    itemColorIndices = bottomBarItemColors,
                                    itemLabels = bottomBarItemLabels,
                                    dynamicUnreadCount = dynamicUnreadCount,
                                    homeSettings = effectiveHomeSettings,
                                    miuixBackdrop = bottomBarBackdrop,
                                    motionTier = com.android.purebilibili.core.ui.adaptive.MotionTier.Normal,
                                    isTransitionRunning = bottomPagerRenderBudget.isTransitionRunning,
                                    // 底栏是独立的常驻材质层。栏目切换时保持液态玻璃渲染树，
                                    // 避免先卸载折射效果、页面落定后再等待 backdrop 重新捕获。
                                    forceLowBlurBudget = false,
                                    isFeedScrollInProgress = homeFeedScrollInProgressState.value,
                                    collapseLinkedDock = collapseLinkedPlaybackDock,
                                    indicatorPositionProvider =
                                        mainBottomPagerState.indicatorPositionProvider,
                                    isPagerScrollInProgressProvider =
                                        mainBottomPagerState.scrollInProgressProvider,
                                    uiSkinDecoration = bottomBarUiSkinDecoration,
                                    linkedDockPhase = linkedDockPhase,
                                    onLinkedDockPhaseChange = { linkedDockPhase = it },
                                    isTopLevelDestination = currentNavigation3Key == BiliPaiNavKey.MainHost,
                                    // 共享过渡驱动的开关（点条进详情/返回落位）瞬时切换小横条
                                    // presence，morph 是唯一几何时间轴；仅会话起止播放动画。
                                    animateNowPlayingPresence = !driveBottomBarByProgress,
                                    onToggleSidebar = if (tabletUseSidebar) {
                                        {
                                            coroutineScope.launch {
                                                SettingsManager.setTabletUseSidebar(context, true)
                                            }
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        } else {
                            FrostedBottomBar(
                                currentItem = currentBottomNavItem,
                                onItemClick = handleNavItemClick,
                                onHomeDoubleTap = {
                                    homeScrollChannel.trySend(
                                        com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP_AND_REFRESH
                                    )
                                },
                                onDynamicDoubleTap = {
                                    dynamicScrollChannel.trySend(
                                        DynamicScrollRequest.SCROLL_TO_TOP_AND_REFRESH
                                    )
                                },
                                onSearchClick = { requestSearchFromBottomBar() },
                                onSearchKeywordSubmit = submitBottomBarSearchKeyword,
                                searchLaunchKey = bottomBarSearchLaunchKey,
                                hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                                isFloating = false,
                                labelMode = bottomBarLabelMode,
                                visibleItems = visibleBottomBarItems,
                                itemColorIndices = bottomBarItemColors,
                                itemLabels = bottomBarItemLabels,
                                dynamicUnreadCount = dynamicUnreadCount,
                                homeSettings = effectiveHomeSettings,
                                miuixBackdrop = bottomBarBackdrop,
                                motionTier = com.android.purebilibili.core.ui.adaptive.MotionTier.Normal,
                                isTransitionRunning = bottomPagerRenderBudget.isTransitionRunning,
                                // 固定底栏同样保持材质连续，切页预算只作用于页面内容。
                                forceLowBlurBudget = false,
                                isFeedScrollInProgress = homeFeedScrollInProgressState.value,
                                collapseLinkedDock = collapseLinkedPlaybackDock,
                                indicatorPositionProvider =
                                    mainBottomPagerState.indicatorPositionProvider,
                                isPagerScrollInProgressProvider =
                                    mainBottomPagerState.scrollInProgressProvider,
                                uiSkinDecoration = bottomBarUiSkinDecoration,
                                linkedDockPhase = linkedDockPhase,
                                onLinkedDockPhaseChange = { linkedDockPhase = it },
                                isTopLevelDestination = currentNavigation3Key == BiliPaiNavKey.MainHost,
                                animateNowPlayingPresence = !driveBottomBarByProgress,
                                onToggleSidebar = if (tabletUseSidebar) {
                                    {
                                        coroutineScope.launch {
                                            SettingsManager.setTabletUseSidebar(context, true)
                                        }
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                        }
                    }
                }
            } else if (audioNowPlayingItem != null) {
                val playbackManager = miniPlayerManager ?: MiniPlayerManager.getInstance(context)
                // 听视频标题横条自动沉浸：听视频页 + 播放中静置 5 秒隐藏，点把柄恢复。
                val immersiveBarEnabled by com.android.purebilibili.core.store.SettingsManager
                    .getAudioNowPlayingBarImmersiveEnabled(context)
                    .collectAsStateWithLifecycle(initialValue = true)
                var immersiveBarHidden by remember { mutableStateOf(false) }
                val isOnListenVideoScreen = currentNavigation3Key is BiliPaiNavKey.AudioMode
                LaunchedEffect(
                    immersiveBarEnabled,
                    isOnListenVideoScreen,
                    playbackManager.isPlaying,
                    audioNowPlayingItem.bvid,
                ) {
                    if (immersiveBarEnabled && isOnListenVideoScreen && playbackManager.isPlaying) {
                        immersiveBarHidden = false
                        kotlinx.coroutines.delay(5000)
                        immersiveBarHidden = true
                    } else {
                        immersiveBarHidden = false
                    }
                }
                AudioNowPlayingBarPresenceHost(
                    visible = showAudioNowPlayingIndependent && !(immersiveBarEnabled && immersiveBarHidden),
                    animatePresence = !driveBottomBarByProgress && !isReturningSameAudioVideo,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f),
                ) {
                    if (immersiveBarEnabled && immersiveBarHidden) {
                        // 沉浸态把柄：低调的居中小胶囊，点击唤回标题横条。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clickable(
                                    interactionSource = remember {
                                        androidx.compose.foundation.interaction.MutableInteractionSource()
                                    },
                                    indication = null,
                                ) { immersiveBarHidden = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                            )
                        }
                    } else {
                    AudioNowPlayingBar(
                    state = AudioNowPlayingBarState(
                        bvid = audioNowPlayingItem.bvid,
                        title = audioNowPlayingItem.title,
                        artist = audioNowPlayingItem.owner,
                        artistAvatarUrl = audioNowPlayingItem.ownerFace,
                        coverUrl = audioNowPlayingItem.cover,
                        isPlaying = playbackManager.isPlaying,
                        playbackSpeed = playbackManager.player?.playbackParameters?.speed ?: 1f
                    ),
                    onManualHide = if (immersiveBarEnabled && isOnListenVideoScreen) {
                        { immersiveBarHidden = true }
                    } else {
                        null
                    },
                    isLayoutStable = !driveBottomBarByProgress,
                    sourceRoute = currentRoute ?: ScreenRoutes.Home.route,
                    handoff = audioNowPlayingHandoff,
                    onExpand = {
                        val expandRoute = resolveAudioNowPlayingBarExpandRoute(
                            opensAudioMode = audioNowPlayingBarOpensAudioMode,
                            bvid = audioNowPlayingItem.bvid,
                            cid = audioNowPlayingItem.cid,
                            coverUrl = audioNowPlayingItem.cover,
                        )
                        if (audioNowPlayingBarOpensAudioMode) {
                            pushNavigation3Route(expandRoute)
                        } else {
                            navigateToVideoRouteInNavigation3(
                                route = expandRoute,
                                sourceRoute = currentRoute ?: ScreenRoutes.Home.route,
                            )
                        }
                    },
                    onPlayPause = {
                        if (!playbackManager.togglePlayPause()) {
                            pushNavigation3Route(
                                ScreenRoutes.AudioMode.createRoute(
                                    bvid = audioNowPlayingItem.bvid,
                                    cid = audioNowPlayingItem.cid
                                )
                            )
                        }
                    },
                    onSkipNext = { playbackManager.playNext() },
                    onSkipPrevious = { playbackManager.playPrevious() },
                    onCancelPlayback = { playbackManager.pausePlayback() },
                    onDismiss = {
                        playbackManager.pausePlayback()
                        AudioNowPlayingSession.dismiss()
                    },
                    expandDestinationLabel = if (audioNowPlayingBarOpensAudioMode) {
                        "听视频"
                    } else {
                        "视频详情页"
                    },
                    glassEnabled = effectiveHomeSettings.androidNativeLiquidGlassEnabled,
                    blurEnabled = isBottomBarBlurEnabled,
                    hazeState = if (isBottomBarBlurEnabled) mainHazeState else null,
                    miuixBackdrop = bottomBarBackdrop,
                    liquidGlassTuning = liquidGlassRenderConfig.tuning,
                    liftAboveBottomBar = false,
                    consumeNavigationBarsPadding = true,
                    )
                    }
                }
            }

            // BiliPai MainScreenBackHandler: onBackCompleted → animateToPage(home)
            MainHostTabBackHandler(
                enabled = shouldInterceptTabBack,
                onReturnToHomeTab = {
                    val homeIndex = visibleBottomBarItems.indexOf(BottomNavItem.HOME)
                    if (homeIndex >= 0) {
                        mainBottomPagerState.switchToPage(homeIndex)
                    }
                },
            )

            ImagePreviewOverlayHost(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            )

            HingeOcclusionInputShield(
                adaptiveInfo = appWindowAdaptiveInfo,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(101f),
            )
        } // End of Main Box
        } // End of CompositionLocalProvider
    }
}
