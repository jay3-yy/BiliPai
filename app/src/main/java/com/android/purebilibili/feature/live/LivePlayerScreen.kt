package com.android.purebilibili.feature.live

import com.android.purebilibili.core.util.toUserFriendlyMessage

import com.android.purebilibili.core.player.withAppVideoDecoderDiagnostics
import com.android.purebilibili.core.player.withAppVideoDecoderPreference

import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.ui.graphics.Color

import com.android.purebilibili.core.ui.components.AppDurationPicker
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.feature.video.ui.components.resolvePlayerSleepTimerPresets
import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ForwardToInbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Refresh
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.key.onKeyEvent
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.android.purebilibili.core.player.withDynamicScheduling
import com.android.purebilibili.feature.video.danmaku.rememberPlayerDanmakuTargetFrameRateHz
import com.android.purebilibili.feature.video.danmaku.shouldUseIndependentDanmakuSurface
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.ui.common.AppSelectedTextAction
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.resolveDanmakuSettingsScope
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.tabletopUpperViewportLayout
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.data.repository.LiveRedPocketInfo
import com.android.purebilibili.feature.video.screen.findActivity
import com.android.purebilibili.feature.live.components.LiveChatSection
import com.android.purebilibili.feature.live.components.LiveChatInputBar
import com.android.purebilibili.feature.live.components.rememberLiveRoomMedalControl
import com.android.purebilibili.feature.video.ui.feedback.followActionAnchor
import com.android.purebilibili.feature.live.components.LiveContributionRankSheet
import com.android.purebilibili.feature.live.components.LiveDmBlockSheet
import com.android.purebilibili.feature.live.components.LiveEmoticonPanel
import com.android.purebilibili.feature.live.components.LivePlayerControls
import com.android.purebilibili.feature.live.components.LiveReportDialog
import com.android.purebilibili.feature.live.components.LiveSendDanmakuSheet
import com.android.purebilibili.feature.live.components.LiveStreamSourceSheet
import com.android.purebilibili.feature.live.components.LiveSuperChatSection
import com.android.purebilibili.feature.live.components.LiveVotePanel
import com.android.purebilibili.feature.live.components.LiveSuperChatFlashOverlay
import com.android.purebilibili.feature.live.components.LiveSuperChatStrip
import com.android.purebilibili.feature.live.components.LocalLiveSuperChatSelectionActions
import com.android.purebilibili.feature.live.components.LiveShareToMessageSheet
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.feature.video.player.shouldContinuePlaybackDuringPause
import com.android.purebilibili.feature.video.state.isPlaybackActiveForLifecycle
import com.android.purebilibili.feature.video.state.shouldResumeAfterLifecyclePause
import com.android.purebilibili.feature.video.ui.overlay.shouldRebindFullscreenSurfaceOnResume
import com.android.purebilibili.feature.video.ui.section.rebindPlayerSurfaceIfNeeded
import com.android.purebilibili.feature.video.ui.section.shouldKickPlaybackAfterSurfaceRecovery
import com.android.purebilibili.feature.video.ui.overlay.LiveDanmakuOverlay
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout
import com.android.purebilibili.feature.video.player.PlayerKeyAction
import com.android.purebilibili.feature.video.player.resolvePlayerKeyAction
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.hazeEffectCompat
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.rememberAppProfileAddIcon
import com.android.purebilibili.core.ui.rememberAppAnalyticsIcon
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.LocalAppIconStyle
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.feature.video.playback.session.PlaybackWindowLifecycle
import com.android.purebilibili.feature.video.playback.session.isInMultiWindowPlaybackMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TAG = "LivePlayerScreen"

@OptIn(UnstableApi::class, ExperimentalSharedTransitionApi::class)
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LivePlayerScreen(
    roomId: String,
    title: String,
    uname: String,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: LivePlayerViewModel = viewModel(),
    siteId: String = "bilibili",
    startFullscreen: Boolean = false,
    onOpenWeb: ((String) -> Unit)? = null,
    onSearchSelectedText: ((String) -> Unit)? = null,
    onOpenSelectedVideo: ((BilibiliNavigationTarget.Video) -> Unit)? = null,
) {
    val settingsStyle = LocalAppUiStyle.current
    val settingsIconStyle = LocalAppIconStyle.current
    LiveRoomTheme {
        com.android.purebilibili.core.ui.popup.AppGlassPopupHost(
            glassAvailable = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.popupGlassEnabled &&
                com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass(android.os.Build.VERSION.SDK_INT),
            tuning = com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig.current.tuning,
        ) {
            val config = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
            androidx.compose.runtime.CompositionLocalProvider(
                com.android.purebilibili.core.ui.LocalAppThemeConfig provides config.copy(liquidGlassEnabled = config.playerGlassEnabled),
                LocalLiveSuperChatSelectionActions provides { selection ->
                    buildList {
                        if (onOpenSelectedVideo != null) {
                            val video = BilibiliNavigationTargetParser.parse(selection) as? BilibiliNavigationTarget.Video
                            if (video != null) add(AppSelectedTextAction("视频") {
                                onOpenSelectedVideo(video)
                            })
                        }
                        if (onSearchSelectedText != null) {
                            add(AppSelectedTextAction("搜索", onSearchSelectedText))
                        }
                    }
                },
            ) {
                LiveRoomScreen(
                    roomId, title, uname, onBack, onUserClick, viewModel, siteId, startFullscreen,
                    settingsStyle, settingsIconStyle, onOpenWeb,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalSharedTransitionApi::class)
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LiveRoomScreen(
    roomId: String,
    title: String,
    uname: String,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: LivePlayerViewModel,
    siteId: String,
    startFullscreen: Boolean,
    settingsStyle: AppUiStyle,
    settingsIconStyle: AppIconStyle,
    onOpenWeb: ((String) -> Unit)?,
) {
    val sharedSurfaceVisible = com.android.purebilibili.navigation3.LocalNavigationSharedSurfaceVisible.current
    val bilibiliRoomId = roomId.toLongOrNull() ?: 0L
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 📺 [新增] 获取小窗管理器
    val miniPlayerManager = remember { com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstance(context) }
    val configuration = LocalConfiguration.current
    val windowSizeClass = LocalWindowSizeClass.current
    val hingeAdaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val displayContext = hingeAdaptiveInfo.displayContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val liveSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val showLiveFeedback: (String) -> Unit = { feedbackMessage ->
        if (feedbackMessage.isNotBlank()) {
            coroutineScope.launch {
                liveSnackbarHostState.currentSnackbarData?.dismiss()
                liveSnackbarHostState.showSnackbar(
                    message = feedbackMessage,
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                )
            }
        }
    }
    val liveKeyboardFocusRequester = remember { FocusRequester() }

    // Shared Element Transition Scopes
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val roomAccount by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val canInteractWithRoom = (roomAccount.mid ?: 0L) > 0L
    val palette = rememberLiveChromePalette()
    val roomColorTokens = resolveLiveBiliPaiRoomColorTokens()

    // 状态
    var showDanmakuSettingsDialog by remember { mutableStateOf(false) }
    val liveDanmakuSettingsSource = com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
    val liveInteractionWindowSource = com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
    var showBlockDialog by remember { mutableStateOf(false) }
    var showPlayerInfoDialog by remember { mutableStateOf(false) }
    var showShutdownTimerDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showPlaybackPreferences by remember { mutableStateOf(false) }
    var showContributionRankSheet by remember { mutableStateOf(false) }
    var showSendDanmakuSheet by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
    var showEmoticonSheet by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
    var danmakuDraft by rememberSaveable(roomId, siteId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var showShareMessageSheet by remember { mutableStateOf(false) }
    var fansPanelUrl by remember(roomId, siteId, roomAccount) { mutableStateOf<String?>(null) }
    var showStreamSourceSheet by remember { mutableStateOf(false) }
    fun openDanmakuInput(showEmoticons: Boolean = false) {
        if (siteId == "bilibili" && !canInteractWithRoom) {
            showLiveFeedback("账号未登录")
            return
        }
        showEmoticonSheet = showEmoticons
        showSendDanmakuSheet = true
    }
    var reportTarget by remember(roomId, siteId) { mutableStateOf<LiveDanmakuItem?>(null) }
    var fullscreenRequested by rememberSaveable { mutableStateOf(startFullscreen) }
    var isPlaying by remember { mutableStateOf(true) }
    var selectedInteractionTab by rememberSaveable(roomId, siteId) { mutableIntStateOf(0) }
    val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val superChatListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showLandscapeInteraction by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
    var landscapeInteractionCollapsed by remember(roomId, siteId) { mutableStateOf(false) }
    var followingLatestChat by rememberSaveable(roomId, siteId) { mutableStateOf(true) }
    var isPipRequested by remember { mutableStateOf(false) }
    var resumePlaybackOnForeground by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var trackSelectionBeforeAudioOnly by remember { mutableStateOf<TrackSelectionParameters?>(null) }
    var liveVideoSize by remember(roomId, siteId) { mutableStateOf(androidx.media3.common.VideoSize.UNKNOWN) }
    var videoScale by rememberSaveable(roomId, siteId) { mutableFloatStateOf(1f) }
    var videoAspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    val savedBackgroundPlaybackEnabled by remember(context) { SettingsManager.getBackgroundPlaybackEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = remember(context) { SettingsManager.getBackgroundPlaybackEnabledSync(context) })
    val autoEnterFullscreenEnabled by remember(context) {
        SettingsManager.getAutoEnterFullscreen(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    val autoExitFullscreenEnabled by remember(context) {
        SettingsManager.getAutoExitFullscreen(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val fullscreenGestureReverse by remember(context) {
        SettingsManager.getFullscreenGestureReverse(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    var didAutoEnterFullscreen by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
    var shutdownAtMillis by rememberSaveable(roomId, siteId) { mutableStateOf<Long?>(null) }
    var shutdownMinutes by rememberSaveable(roomId, siteId) { mutableStateOf<Int?>(null) }
    var shutdownExitsApp by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
    val showLivePipButton = remember(activity) {
        shouldShowLivePipButton(android.os.Build.VERSION.SDK_INT) &&
            activity?.packageManager?.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    }
    var isInSystemPip by remember(activity) { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val host = activity as? androidx.activity.ComponentActivity
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInSystemPip = info.isInPictureInPictureMode
            isPipRequested = false
        }
        host?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    val pipPresentation = isInSystemPip || isPipRequested
    val successState = uiState as? LivePlayerState.Success
    val isLiveAudioOnly = successState?.isAudioOnly == true
    val switchingAudioOnly by viewModel.switchingAudioOnly.collectAsStateWithLifecycle()
    val canEnterLivePip = successState != null && !isLiveAudioOnly && !switchingAudioOnly
    val roomInfo = successState?.roomInfo ?: RoomInfo()
    val anchorInfo = successState?.anchorInfo ?: AnchorInfo()
    val medalContent = key(roomId, siteId, roomAccount, anchorInfo.uid) {
        if (siteId == "bilibili" && canInteractWithRoom && anchorInfo.uid > 0L) {
            rememberLiveRoomMedalControl(
                userId = roomAccount.mid ?: 0L,
                anchorId = anchorInfo.uid,
                onUserClick = onUserClick,
            )
        } else {
            null
        }
    }
    val currentIsRoomLive by rememberUpdatedState(roomInfo.liveStatus == 1)
    val currentAutoEnterFullscreen by rememberUpdatedState(autoEnterFullscreenEnabled)
    val currentAutoExitFullscreen by rememberUpdatedState(autoExitFullscreenEnabled)
    val isPortraitLive = roomInfo.isPortrait
    val liveRoomTitle = roomInfo.title.ifBlank { title }
    val liveCoverForUi = roomInfo.background.ifBlank { roomInfo.cover }
    val currentQualityDesc = successState
        ?.qualityList
        ?.find { it.qn == successState.currentQuality }
        ?.desc
        ?: "自动"

    // 当前线路描述（协议·编码·线路号），供手动线路切换入口展示
    // Haze blur 状态 (用于侧边栏实时模糊)
    val hazeState = com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState()

    // 直播布局只看当前可用窗口，分屏/自由窗口不会沿用物理平板布局。
    val isTablet = windowSizeClass.isTablet
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val liveRequestedOrientationMode = resolveLiveRequestedOrientationMode(
        displayContext = displayContext,
        isFullscreen = fullscreenRequested,
        isPortraitLive = isPortraitLive,
    )
    val isFullscreen = shouldPresentLiveFullscreen(
        fullscreenRequested = fullscreenRequested,
        isLandscape = isLandscape,
        orientationMode = liveRequestedOrientationMode,
    )
    val liveLayoutMode = resolveLiveRoomLayoutMode(
        isLandscape = isLandscape,
        isTablet = isTablet,
        isFullscreen = isFullscreen,
        isPortraitLive = isPortraitLive,
        hasObstructingHinge = hingeAdaptiveInfo.shouldAvoidHinge,
    )
    val playerGesturePolicy = LivePlayerGesturePolicy.Shared
    var captureLiveDanmakuTap by remember(roomId, siteId) {
        mutableStateOf<((androidx.compose.ui.geometry.Offset) -> com.android.purebilibili.danmaku.engine.DanmakuClickCapture?)?>(null)
    }
    var liveDanmakuInteractionEnabled by remember(roomId, siteId) { mutableStateOf(true) }
    val liveVisualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val liveDanmakuSettingsScope = remember(isLandscape) {
        resolveDanmakuSettingsScope(isLandscape = isLandscape)
    }
    val normalDanmakuSettings by remember(context) {
        SettingsManager.getDanmakuSettings(context, DanmakuSettingsScope.PORTRAIT)
    }.collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val fullscreenDanmakuSettings by remember(context) {
        SettingsManager.getDanmakuSettings(context, DanmakuSettingsScope.LANDSCAPE)
    }.collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val pendingLiveDanmakuStyles = remember(roomId, siteId, liveDanmakuSettingsScope, isFullscreen) {
        mutableStateMapOf<LiveDanmakuStyleOption, Float>()
    }
    val draggingLiveDanmakuStyles = remember(roomId, siteId, liveDanmakuSettingsScope, isFullscreen, showDanmakuSettingsDialog) {
        mutableStateMapOf<LiveDanmakuStyleOption, Float>()
    }
    // 已提交值保留至存储回流，关闭面板仅丢弃未提交的拖动预览。
    val liveDanmakuStylePreview = pendingLiveDanmakuStyles + draggingLiveDanmakuStyles
    val liveDanmakuWriteMutex = remember { kotlinx.coroutines.sync.Mutex() }
    // 直播字号按普通／全屏选择；其余样式仍沿用当前窗口方向的设置。
    val liveDanmakuSettings = applyLiveDanmakuStylePreview(
        resolveLiveDanmakuSettings(isLandscape, isFullscreen, normalDanmakuSettings, fullscreenDanmakuSettings),
        isFullscreen,
        liveDanmakuStylePreview,
    )
    val liveDanmakuDisplayArea = liveDanmakuSettings.displayArea
    val liveSuperChatEnabled by remember(context) {
        SettingsManager.getLiveSuperChatEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(liveSuperChatEnabled) { viewModel.setSuperChatEnabled(liveSuperChatEnabled) }
    val liveSuperChatPanelEnabled by remember(context) {
        SettingsManager.getLiveSuperChatPanelEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val liveSuperChatFlashEnabled by remember(context) {
        SettingsManager.getLiveSuperChatFlashEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val liveSuperChatPersistent by remember(context) {
        SettingsManager.getLiveSuperChatPersistent(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    val savedLiveSuperChatMaxWidthDp by remember(context) {
        SettingsManager.getLiveSuperChatMaxWidthDp(context)
    }.collectAsStateWithLifecycle(initialValue = 360)
    val savedLiveSuperChatOpacity by remember(context) {
        SettingsManager.getLiveSuperChatFlashOpacity(context)
    }.collectAsStateWithLifecycle(initialValue = 1f)
    val savedLiveSuperChatScale by remember(context) {
        SettingsManager.getLiveSuperChatFlashScale(context)
    }.collectAsStateWithLifecycle(initialValue = 1f)
    val liveSuperChatMaxWidthDp = liveDanmakuStylePreview[LiveDanmakuStyleOption.SuperChatWidth]?.roundToInt()
        ?: savedLiveSuperChatMaxWidthDp
    val liveSuperChatOpacity = liveDanmakuStylePreview[LiveDanmakuStyleOption.SuperChatOpacity] ?: savedLiveSuperChatOpacity
    val liveSuperChatScale = liveDanmakuStylePreview[LiveDanmakuStyleOption.SuperChatScale] ?: savedLiveSuperChatScale
    LaunchedEffect(
        normalDanmakuSettings, fullscreenDanmakuSettings, savedLiveSuperChatMaxWidthDp,
        savedLiveSuperChatOpacity, savedLiveSuperChatScale, pendingLiveDanmakuStyles,
    ) {
        pendingLiveDanmakuStyles.entries.toList().forEach { (option, value) ->
            val saved = savedLiveDanmakuStyleValue(
                option, normalDanmakuSettings, fullscreenDanmakuSettings, isLandscape,
                savedLiveSuperChatMaxWidthDp, savedLiveSuperChatOpacity, savedLiveSuperChatScale,
            )
            if (kotlin.math.abs(saved - value) < 0.0001f) pendingLiveDanmakuStyles.remove(option)
        }
    }
    val liveKeyboardControlsEnabled by remember(context) {
        SettingsManager.getLiveKeyboardControlsEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(liveKeyboardControlsEnabled, liveLayoutMode) {
        if (liveKeyboardControlsEnabled) liveKeyboardFocusRequester.requestFocus()
    }
    val liveDanmakuEnabledPreference by remember(context) {
        SettingsManager.getLiveDanmakuEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val pipNoDanmaku by remember(context) {
        SettingsManager.getPipNoDanmakuEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = false)

    // 先请求目标方向，布局按真实窗口方向提交；竖向直播与窗口内全屏保持各自策略。
    fun toggleFullscreen() {
        val targetFullscreen = !fullscreenRequested
        if (!targetFullscreen) showLandscapeInteraction = false
        activity?.applyPlayerRequestedOrientation(
            requestedOrientation = resolveLiveRequestedOrientationMode(
                displayContext = displayContext,
                isFullscreen = targetFullscreen,
                isPortraitLive = isPortraitLive,
            ).toActivityOrientation(),
            displayContext = displayContext,
        )
        fullscreenRequested = targetFullscreen
    }
    val currentToggleFullscreen by rememberUpdatedState({ toggleFullscreen() })

    fun toggleLiveDanmaku() {
        val current = successState?.isDanmakuEnabled ?: liveDanmakuEnabledPreference
        val next = !current
        viewModel.setDanmakuEnabled(next)
        coroutineScope.launch { SettingsManager.setLiveDanmakuEnabled(context, next) }
    }

    LaunchedEffect(roomId, liveDanmakuEnabledPreference, successState?.playUrl) {
        if (successState != null) viewModel.setDanmakuEnabled(liveDanmakuEnabledPreference)
    }

    fun exitLiveRoom() {
        // The visible back affordance means leave the room. Toggling fullscreen here first
        // briefly selects LandscapeSplit on tablets/foldables and exposes its chat column.
        // System back remains responsible for the conventional exit-fullscreen-first flow.
        // 导航层统一决定转入小窗或停播，按钮与系统返回使用同一退出策略。
        onBack()
    }

    val captureAndShareScreenshot = com.android.purebilibili.feature.video.ui.components.rememberVideoScreenshotAction()
    fun captureLiveScreenshot() {
        val playerView = playerViewRef
        if (playerView == null) {
            showLiveFeedback("截图失败：播放器未就绪")
        } else {
            captureAndShareScreenshot(playerView, 0, 0, liveRoomTitle)
        }
    }

    fun copyLiveUrl() {
        val liveUrl = "https://live.bilibili.com/$roomId"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("直播间链接", liveUrl))
        showLiveFeedback("已复制直播间链接")
    }

    fun shareLiveUrl() {
        val liveUrl = "https://live.bilibili.com/$roomId"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, liveUrl)
        }
        context.startActivity(Intent.createChooser(sendIntent, "分享直播间"))
    }

    fun openLiveUrl() {
        val liveUrl = "https://live.bilibili.com/$roomId"
        if (onOpenWeb != null) onOpenWeb(liveUrl)
        else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(liveUrl)))
    }

    fun openBlockSettings() {
        if (canInteractWithRoom) showBlockDialog = true
        else showLiveFeedback("账号未登录")
    }

    fun openRedPocket(info: LiveRedPocketInfo) {
        val url = info.h5Url
        if (url.isBlank()) {
            showLiveFeedback("红包入口暂不可用")
            return
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun shareLiveToMessage() {
        showShareMessageSheet = true
    }


    fun addLiveBlockKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        coroutineScope.launch {
            val scope = if (isLandscape) DanmakuSettingsScope.LANDSCAPE else DanmakuSettingsScope.PORTRAIT
            val currentRaw = SettingsManager.getDanmakuBlockRulesRaw(context, scope).first()
            val nextRaw = listOf(currentRaw, trimmed)
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n")
            SettingsManager.setDanmakuBlockRulesRaw(context, nextRaw, scope)
            showLiveFeedback("已加入屏蔽词")
        }
    }

    fun enterLivePip() {
        if (!showLivePipButton || !canEnterLivePip || pipPresentation) return
        val hostActivity = activity ?: return
        try {
            isPipRequested = true
            miniPlayerManager.updatePlaybackRoutePipRequest(true)
            val sourceRatio = if (liveVideoSize.width > 0 && liveVideoSize.height > 0) {
                liveVideoSize.width * liveVideoSize.pixelWidthHeightRatio / liveVideoSize.height
            } else if (isPortraitLive) 9f / 16f else 16f / 9f
            val pipRatio = sourceRatio.coerceIn(1f / 2.39f, 2.39f)
            val paramsBuilder = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational((pipRatio * 10_000).roundToInt(), 10_000))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                paramsBuilder.setSeamlessResizeEnabled(true)
            }
            if (hostActivity.enterPictureInPictureMode(paramsBuilder.build())) return
        } catch (e: RuntimeException) {
            Logger.w(TAG, "进入画中画失败", e)
        }
        isPipRequested = false
        miniPlayerManager.updatePlaybackRoutePipRequest(false)
        showLiveFeedback("无法进入画中画")
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LivePlayerEvent.Toast -> {
                    showLiveFeedback(event.message)
                }
                is LivePlayerEvent.DanmakuSent -> {
                    if (danmakuDraft.text.trim() == event.message) {
                        danmakuDraft = TextFieldValue("")
                        showSendDanmakuSheet = false
                        showEmoticonSheet = false
                    }
                }
                LivePlayerEvent.EmoticonSent -> {
                    // 表情发送结束同样关闭输入，不能因收起表情面板而重新弹出键盘。
                    showSendDanmakuSheet = false
                    showEmoticonSheet = false
                }
            }
        }
    }

    // 全屏返回先恢复直播间布局。
    LocalNavigationBackHandler(enabled = fullscreenRequested) { toggleFullscreen() }

    DisposableEffect(roomId) {
        miniPlayerManager.resetNavigationFlag()
        CrashReporter.setLastScreen("live_player")
        CrashReporter.markLiveSessionStart(roomId = bilibiliRoomId, title = title, uname = uname)
        CrashReporter.markLivePlaybackStage("screen_enter")
        onDispose {
            CrashReporter.markLiveSessionEnd("screen_dispose")
        }
    }

    // 播放器相关逻辑
    // ... (保持不变)
    val dataSourceFactory = remember(roomId) {
        val sessData = com.android.purebilibili.core.store.TokenManager.sessDataCache ?: ""
        val buvid3 = com.android.purebilibili.core.store.TokenManager.buvid3Cache ?: ""
        val cookies = "SESSDATA=$sessData; buvid3=$buvid3"

        DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://live.bilibili.com/$roomId",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                "Cookie" to cookies
            ))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
    }

    val exoPlayer = remember(dataSourceFactory) {
        // 与视频/番剧/小窗播放器一致：显式声明媒体用途并接管音频焦点，
        // 否则直播忽略用户的 audio_focus_enabled 开关，被其他音频短暂抢占后偶发丢声。
        val handleAudioFocus = SettingsManager.getAudioFocusEnabledSync(context)
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .withAppVideoDecoderPreference(context)
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .withDynamicScheduling()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(true)
            .build().withAppVideoDecoderDiagnostics(context).apply { playWhenReady = true }
    }
    val backgroundSession = com.android.purebilibili.feature.video.player.rememberPlayerBackgroundPlaybackSession(exoPlayer, "live:$bilibiliRoomId")
    val backgroundOverride by backgroundSession.selection.collectAsStateWithLifecycle()
    val backgroundPlaybackEnabled = backgroundOverride ?: savedBackgroundPlaybackEnabled
    fun toggleBackgroundPlayback() {
        val newValue = !backgroundPlaybackEnabled
        backgroundSession.select(newValue)
        coroutineScope.launch { backgroundSession.saveIfRemembered(context, newValue) }
        showLiveFeedback(if (newValue) "已开启后台播放" else "已关闭后台播放")
    }
    com.android.purebilibili.feature.video.screen.VideoDetailKeepScreenOnEffect(
        window = activity?.window,
        player = exoPlayer,
    )
    val liveHdrSurfaceRequired = com.android.purebilibili.feature.video.ui.components.rememberVideoSurfaceHdrRequired(exoPlayer)
    // 横竖屏播放器共用玻璃采样与纹理请求；关闭玻璃后不改变原生控件布局。
    val livePageGlassSupported =
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
            com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass(android.os.Build.VERSION.SDK_INT) &&
            !liveHdrSurfaceRequired && !isLiveAudioOnly && !pipPresentation
    var retainedLiveGlassTexture by remember(roomId, siteId) { mutableStateOf(false) }
    LaunchedEffect(livePageGlassSupported) {
        if (livePageGlassSupported) retainedLiveGlassTexture = true
    }
    val liveHostLifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(liveHostLifecycleState, pipPresentation, exoPlayer) {
        // 从系统小窗直接关闭时，页面已经暂停，需在窗口结束后重新应用后台播放开关。
        if (liveHostLifecycleState == Lifecycle.State.CREATED && !pipPresentation &&
            !miniPlayerManager.isMiniMode && !miniPlayerManager.shouldContinueBackgroundAudio() &&
            exoPlayer.playWhenReady
        ) {
            resumePlaybackOnForeground = true
            exoPlayer.pause()
            viewModel.setLivePlaybackRequested(false)
        }
    }
    val landscapeInteractionAvailable = isLandscape &&
        (isFullscreen || liveLayoutMode == LiveRoomLayoutMode.LandscapeOverlay)
    val landscapeInteractionVisible = showLandscapeInteraction && landscapeInteractionAvailable && liveVisualWorkActive &&
        liveHostLifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused &&
        !miniPlayerManager.isMiniMode && !pipPresentation &&
        liveDanmakuInteractionEnabled
    LocalNavigationBackHandler(enabled = landscapeInteractionVisible) { showLandscapeInteraction = false }
    var liveBackdropRequired by remember(roomId, siteId) { mutableStateOf(true) }
    val liveNavigationScope = com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope.current
    val liveTransitionCapturePaused by remember(liveNavigationScope) {
        derivedStateOf {
            liveNavigationScope?.let {
                it.isRunning || it.isRemoving || it.relativeDepth != 0f
            } == true
        }
    }
    val liveCapturePaused = liveTransitionCapturePaused || !liveVisualWorkActive
    // 录制覆盖控件淡出，隐藏后停止；避免不可见控制层持续录制视频。
    val liveCaptureVisibility = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (liveBackdropRequired && !liveCapturePaused) 1f else 0f,
        animationSpec = if (liveCapturePaused) androidx.compose.animation.core.snap<Float>()
        else androidx.compose.animation.core.tween<Float>(
            com.android.purebilibili.feature.video.ui.overlay.PLAYER_GLASS_VISIBILITY_DURATION_MS,
        ),
        label = "livePlayerGlassCapture",
    )
    val liveCaptureExiting by remember(liveCaptureVisibility) {
        derivedStateOf { liveCaptureVisibility.value > 0f }
    }
    val captureLivePlayerPage = com.android.purebilibili.feature.video.ui.section.shouldCapturePlayerPageBackdrop(
        glassSupported = livePageGlassSupported,
        controlsRequestCapture = liveBackdropRequired || landscapeInteractionVisible,
        controlsExitVisible = liveCaptureExiting,
        visualWorkActive = liveVisualWorkActive,
        miniMode = miniPlayerManager.isMiniMode,
        overlaysSuppressed = liveCapturePaused,
    )
    val livePlayerBackdrop = com.android.purebilibili.feature.video.ui.section.rememberPlayerPageBackdrop(
        recordingEnabled = captureLivePlayerPage &&
            com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current,
    )
    val useTextureSurfaceForLivePlayer = com.android.purebilibili.feature.video.ui.section.shouldUsePlayerPageTexture(
        glassSupported = livePageGlassSupported,
        retainedGlassTexture = retainedLiveGlassTexture,
        hdr = liveHdrSurfaceRequired,
        existingTextureRequest = shouldUseTextureSurfaceForLivePlayer(
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null,
            requiresHdrSurfaceOutput = liveHdrSurfaceRequired,
            hasContainerTransition = LocalLiveCardContainerActive.current,
        ),
    )

    LaunchedEffect(exoPlayer, isLiveAudioOnly) {
        if (isLiveAudioOnly) {
            if (trackSelectionBeforeAudioOnly == null) {
                trackSelectionBeforeAudioOnly = exoPlayer.trackSelectionParameters
            }
            exoPlayer.trackSelectionParameters = resolveLiveTrackSelectionParametersForAudioOnly(
                currentTrackSelectionParameters = exoPlayer.trackSelectionParameters,
                isAudioOnly = true
            )
            exoPlayer.clearVideoSurface()
            playerViewRef?.player = null
            CrashReporter.markLivePlaybackStage("audio_only_video_disabled")
        } else {
            trackSelectionBeforeAudioOnly?.let { originalParams ->
                exoPlayer.trackSelectionParameters = originalParams
                trackSelectionBeforeAudioOnly = null
                CrashReporter.markLivePlaybackStage("audio_only_video_restored")
            }
            playerViewRef?.player = exoPlayer
        }
    }

    LaunchedEffect(shutdownAtMillis, exoPlayer) {
        val target = shutdownAtMillis ?: return@LaunchedEffect
        delay((target - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        if (shutdownAtMillis != target) return@LaunchedEffect
        shutdownAtMillis = null
        shutdownMinutes = null
        resumePlaybackOnForeground = false
        exoPlayer.pause()
        viewModel.setLivePlaybackRequested(false)
        if (shutdownExitsApp) {
            miniPlayerManager.markLeavingByNavigation(forceStop = true)
            activity?.finishAffinity()
        } else {
            showLiveFeedback("定时结束，已暂停直播")
        }
    }

    // 📺 [新增] 将播放器注册到 MiniPlayerManager
    val liveCover = liveCoverForUi
    val liveTitle = liveRoomTitle
    LaunchedEffect(exoPlayer, liveCover, liveTitle, uname) {
        miniPlayerManager.setLiveInfo(
            roomId = bilibiliRoomId,
            title = liveTitle,
            cover = liveCover,
            uname = uname,
            externalPlayer = exoPlayer
        )
    }

    val nearbyLiveQualityId = successState?.currentQuality ?: 0
    val nearbyLiveDanmakuEnabled = successState?.isDanmakuEnabled ?: liveDanmakuEnabledPreference
    val nearbyLiveAvailableQualities = remember(successState?.qualityList) {
        successState?.qualityList?.map { it.qn }.orEmpty()
    }
    DisposableEffect(
        exoPlayer,
        bilibiliRoomId,
        liveTitle,
        uname,
        liveCover,
        siteId,
        nearbyLiveQualityId,
        currentQualityDesc,
        nearbyLiveDanmakuEnabled,
        nearbyLiveAvailableQualities,
    ) {
        if (siteId == "bilibili" && bilibiliRoomId > 0L) {
            com.android.purebilibili.feature.nearby.NearbyPlaybackBridge.bindLive(
                roomId = bilibiliRoomId,
                title = liveTitle,
                uname = uname,
                cover = liveCover,
                player = exoPlayer,
                qualityId = nearbyLiveQualityId,
                qualityLabel = currentQualityDesc.takeUnless { it == "自动" }.orEmpty(),
                danmakuEnabled = nearbyLiveDanmakuEnabled,
                availableQualityIds = nearbyLiveAvailableQualities,
                onChangeQuality = { viewModel.changeQuality(it) },
            )
        }
        onDispose {
            com.android.purebilibili.feature.nearby.NearbyPlaybackBridge.unbindLive(exoPlayer)
        }
    }

    // ... (播放监听与 URL 管理保持不变)

    // 播放状态监听
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                liveVideoSize = videoSize
                viewModel.updateVideoOrientation(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val httpResponseCode = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()
                    ?.responseCode
                when (resolveLivePlaybackErrorRecovery(error.errorCode, httpResponseCode)) {
                    LivePlaybackErrorRecovery.SEEK_TO_LIVE_EDGE -> {
                        CrashReporter.markLivePlaybackStage("recover_live_edge")
                        Logger.w(TAG, "直播播放落后于可用窗口，回到直播边缘")
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                    }
                    LivePlaybackErrorRecovery.TRY_NEXT_SOURCE -> {
                        CrashReporter.markLivePlaybackStage("recover_next_source")
                        Logger.w(
                            TAG,
                            "直播源播放失败，切换备用线路：code=${httpResponseCode ?: error.errorCode}"
                        )
                        viewModel.tryNextUrl()
                    }
                    LivePlaybackErrorRecovery.NONE -> {
                        Logger.e(TAG, "ExoPlayer Error: ${error.message}")
                        CrashReporter.markLivePlaybackStage("player_error")
                        CrashReporter.reportLiveError(
                            roomId = bilibiliRoomId,
                            errorType = "exo_player_error",
                            errorMessage = error.message ?: "unknown",
                            exception = error
                        )
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    viewModel.onPlaybackStarted()
                    if (currentAutoEnterFullscreen && !didAutoEnterFullscreen) {
                        didAutoEnterFullscreen = true
                        if (!fullscreenRequested) currentToggleFullscreen()
                    }
                }
                CrashReporter.markLivePlaybackStage(if (playing) "playing" else "not_playing")
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                viewModel.setLivePlaybackRequested(playWhenReady)
            }
            // 📺 [新增] 直播流结束时自动关闭小窗
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isMiniLiveMode = miniPlayerManager.isMiniMode && miniPlayerManager.isLiveMode
                if (playbackState == Player.STATE_ENDED && isMiniLiveMode) {
                    Logger.d(TAG, "📺 直播流结束，自动关闭小窗")
                    miniPlayerManager.dismiss()
                } else if (shouldRecoverUnexpectedLiveEnd(
                        playbackState = playbackState,
                        playWhenReady = exoPlayer.playWhenReady,
                        isRoomLive = currentIsRoomLive,
                        isMiniLiveMode = isMiniLiveMode
                    )
                ) {
                    Logger.w(TAG, "直播流意外结束，尝试切换播放源")
                    viewModel.tryNextUrl()
                } else if (
                    playbackState == Player.STATE_ENDED &&
                    currentAutoExitFullscreen &&
                    !currentIsRoomLive &&
                    fullscreenRequested
                ) {
                    currentToggleFullscreen()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // 播放 URL 管理
    LaunchedEffect(siteId, roomId) {
        val savedQuality = SettingsManager.getLivePreferredQuality(
            context, com.android.purebilibili.core.util.NetworkUtils.isWifi(context),
        ).first()
        viewModel.loadLiveStream(bilibiliRoomId, qn = savedQuality)
    }
    // 刷新地址即使返回相同 URL，也需要重新 prepare 已失败的播放源。
    val playUrl = (uiState as? LivePlayerState.Success)?.playUrl
    LaunchedEffect(exoPlayer, playUrl, successState?.playbackRevision) {
        if (!playUrl.isNullOrEmpty()) {
            CrashReporter.markLivePlaybackStage("prepare_media_source")
            try {
                // 交给已配置的 DefaultMediaSourceFactory 按 URL/响应识别 HLS、fMP4 或 FLV；
                // 非 m3u8 地址不再被一律强制标记成 FLV。
                exoPlayer.setMediaItem(MediaItem.fromUri(playUrl))
                exoPlayer.prepare()
                miniPlayerManager.updateMediaMetadata(
                    title = liveTitle.ifBlank { "直播中" },
                    artist = uname.ifBlank { "直播" },
                    coverUrl = liveCover
                )
                CrashReporter.markLivePlaybackStage("media_source_prepared")
            } catch (e: Exception) {
                Logger.e(TAG, "Play failed", e)
                CrashReporter.markLivePlaybackStage("prepare_media_source_failed")
                CrashReporter.reportLiveError(
                    roomId = bilibiliRoomId,
                    errorType = "media_prepare_failed",
                    errorMessage = e.message ?: "play failed",
                    exception = e
                )
            }
            // 埋点
            AnalyticsHelper.logLivePlay(bilibiliRoomId, title, uname)
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // 生命周期管理
    val currentIsLiveAudioOnly by rememberUpdatedState(isLiveAudioOnly)
    DisposableEffect(lifecycleOwner, activity) {
        val playbackLifecycle = PlaybackWindowLifecycle()
        val observer = LifecycleEventObserver { _, event ->
            when (playbackLifecycle.playbackEvent(event, activity.isInMultiWindowPlaybackMode())) {
                Lifecycle.Event.ON_PAUSE -> {
                    val isInPictureInPictureMode =
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                            (activity?.isInPictureInPictureMode == true)
                    val isBackgroundAudioEnabled = miniPlayerManager.shouldContinueBackgroundAudio()
                    val wasPlaybackActive = isPlaybackActiveForLifecycle(
                        isPlaying = exoPlayer.isPlaying,
                        playWhenReady = exoPlayer.playWhenReady,
                        playbackState = exoPlayer.playbackState
                    )
                    val shouldKeepPlayingInBackground = shouldContinuePlaybackDuringPause(
                        isMiniMode = miniPlayerManager.isMiniMode,
                        isPip = isInPictureInPictureMode || isPipRequested ||
                            miniPlayerManager.shouldKeepPlaybackForPipTransition(),
                        isBackgroundAudio = isBackgroundAudioEnabled,
                        wasPlaybackActive = wasPlaybackActive
                    )
                    val shouldPausePlayback = shouldPauseLivePlaybackOnPause(
                        isInPictureInPictureMode = isInPictureInPictureMode,
                        isPipRequested = isPipRequested,
                        shouldKeepPlayingInBackground = shouldKeepPlayingInBackground
                    )
                    // 只有页面主动暂停的播放才需要在返回时恢复。
                    resumePlaybackOnForeground = shouldPausePlayback &&
                        (resumePlaybackOnForeground || wasPlaybackActive)
                    if (shouldPausePlayback) {
                        exoPlayer.pause()
                        viewModel.setLivePlaybackRequested(false)
                        CrashReporter.markLivePlaybackStage("lifecycle_pause")
                    } else {
                        CrashReporter.markLivePlaybackStage("lifecycle_pause_keep_playing")
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (activity?.isInPictureInPictureMode != true) {
                        isPipRequested = false
                        miniPlayerManager.updatePlaybackRoutePipRequest(false)
                    }
                    val shouldResumePlayback = shouldResumeAfterLifecyclePause(
                        wasPlaybackActive = resumePlaybackOnForeground,
                        isPlaying = exoPlayer.isPlaying,
                        playWhenReady = exoPlayer.playWhenReady,
                        playbackState = exoPlayer.playbackState
                    )
                    resumePlaybackOnForeground = false
                    Logger.d(TAG, "ON_RESUME live policy: shouldResume=$shouldResumePlayback")
                    val view = playerViewRef
                    if (!currentIsLiveAudioOnly && shouldRebindFullscreenSurfaceOnResume(
                            hasPlayerView = view != null,
                            hasPlayer = true
                        )
                    ) {
                        rebindPlayerSurfaceIfNeeded(
                            playerView = view!!,
                            player = exoPlayer
                        )
                        Logger.d(TAG, "🎬 ON_RESUME live surface rebind applied")
                    }
                    if (shouldResumePlayback) {
                        exoPlayer.play()
                    } else if (shouldKickPlaybackAfterSurfaceRecovery(
                            playWhenReady = exoPlayer.playWhenReady,
                            isPlaying = exoPlayer.isPlaying,
                            playbackState = exoPlayer.playbackState
                        )
                    ) {
                        exoPlayer.play()
                        Logger.d(TAG, "▶️ ON_RESUME live playback kicked after surface recovery")
                    }
                    viewModel.setLivePlaybackRequested(exoPlayer.playWhenReady)
                    CrashReporter.markLivePlaybackStage("lifecycle_resume")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isPipRequested) miniPlayerManager.updatePlaybackRoutePipRequest(false)
            playerViewRef = null
            // 📺 [修改] 仅当 MiniPlayerManager 未持有该播放器时才释放
            if (!miniPlayerManager.isPlayerManaged(exoPlayer)) {
                exoPlayer.release()
                Logger.d(TAG, "📺 播放器未被小窗持有，释放")
            } else {
                // 交出播放器进入应用内小窗时记录退出前的全屏状态，供重新展开时恢复方向。
                miniPlayerManager.noteLiveRestoreFullscreen(bilibiliRoomId, fullscreenRequested)
                Logger.d(TAG, "📺 播放器被小窗持有，保留")
            }
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.applyPlayerRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            viewModel.pauseLiveHeartbeat()
        }
    }

    // 横屏时隐藏系统栏
    LaunchedEffect(isLandscape, liveLayoutMode, isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false

        if (
            liveLayoutMode == LiveRoomLayoutMode.LandscapeOverlay ||
            (liveLayoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay && isFullscreen)
        ) {
            // 隐藏状态栏和导航栏
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            // 恢复显示
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    //  [修复] 离开直播间时恢复系统栏：横屏沉浸只由上方 LaunchedEffect 的翻转分支解除，
    //  此前 onDispose 不恢复，经小窗/深链等非翻转路径离开会泄漏隐藏的系统栏。
    DisposableEffect(activity) {
        val roomWindow = activity?.window
        val barController = roomWindow?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousLightStatus = barController?.isAppearanceLightStatusBars
        val previousLightNavigation = barController?.isAppearanceLightNavigationBars
        onDispose {
            val exitWindow = activity?.window ?: return@onDispose
            val exitController = WindowCompat.getInsetsController(exitWindow, exitWindow.decorView)
            exitController.show(WindowInsetsCompat.Type.systemBars())
            exitController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            previousLightStatus?.let { exitController.isAppearanceLightStatusBars = it }
            previousLightNavigation?.let { exitController.isAppearanceLightNavigationBars = it }
        }
    }

    var previousLiveDisplayRole by remember {
        mutableStateOf(displayContext.foldableDisplayRole)
    }
    LaunchedEffect(activity, displayContext.foldableDisplayRole) {
        if (
            com.android.purebilibili.core.util.shouldReleaseOrientationLockOnDisplayRoleChange(
                previousRole = previousLiveDisplayRole,
                nextRole = displayContext.foldableDisplayRole,
            )
        ) {
            activity?.applyPlayerRequestedOrientation(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                displayContext = displayContext,
            )
        }
        previousLiveDisplayRole = displayContext.foldableDisplayRole
    }
    LaunchedEffect(activity, displayContext, liveRequestedOrientationMode, pipPresentation) {
        if (pipPresentation) return@LaunchedEffect
        activity?.applyPlayerRequestedOrientation(
            requestedOrientation = liveRequestedOrientationMode.toActivityOrientation(),
            displayContext = displayContext,
        )
    }

    // 布局结构
    val superChatOverlayEnabled = liveSuperChatEnabled && liveSuperChatFlashEnabled
    val showFullscreenSuperChat = shouldShowLiveSuperChatFlash(
        showMediaOverlays = isFullscreen && !pipPresentation && successState != null,
        isLandscape = isLandscape,
        isDanmakuEnabled = successState?.isDanmakuEnabled == true,
        flashEnabled = superChatOverlayEnabled,
    )
    val onSuperChatCountdownPauseChange: (LiveDanmakuItem, Boolean) -> Unit = { item, paused ->
        viewModel.setSuperChatMenuOpen(bilibiliRoomId, item.superChatId, paused)
    }
    val onSuperChatReport: (LiveDanmakuItem) -> Unit = { item ->
        if (canInteractWithRoom) reportTarget = item
        else showLiveFeedback("账号未登录")
    }

    val superChatStrip: @Composable (Modifier) -> Unit = { modifier ->
        key(roomId, siteId) {
            LiveRoomUpdates(viewModel.superChatItems) { items ->
                LiveSuperChatStrip(
                    items = items,
                    persistent = liveSuperChatPersistent,
                    countdownEnabled = !miniPlayerManager.isMiniMode,
                    remainingSeconds = viewModel::superChatRemainingSeconds,
                    maxCardWidthDp = liveSuperChatMaxWidthDp,
                    opacity = liveSuperChatOpacity,
                    scale = liveSuperChatScale,
                    onUserClick = onUserClick,
                    onReport = onSuperChatReport,
                    onCountdownPauseChange = onSuperChatCountdownPauseChange,
                    modifier = modifier,
                )
            }
        }
    }

    val playerContent = @Composable {
        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(roomColorTokens.baseBackgroundColor)
                    .hazeSourceCompat(state = hazeState)
                .then(
                    if (useTextureSurfaceForLivePlayer && sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = com.android.purebilibili.core.ui.transition.liveCoverSharedElementKey(bilibiliRoomId)),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else Modifier
                )
        ) {
            // Video View：全屏悬停态收进上半屏，控制条留于下半屏触控区。
            val liveTabletopFullscreen = isFullscreen && !pipPresentation &&
                hingeAdaptiveInfo.posture == AppFoldPosture.Tabletop
            BoxWithConstraints(
                modifier = Modifier
                    .tabletopUpperViewportLayout(
                        enabled = liveTabletopFullscreen,
                        adaptiveInfo = hingeAdaptiveInfo,
                    )
                    .fillMaxSize()
                    .clipToBounds()
                    .then(if (captureLivePlayerPage) livePlayerBackdrop.video.modifier else Modifier),
                contentAlignment = if (liveLayoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay && !isFullscreen) Alignment.TopCenter else Alignment.Center
            ) {
                val density = LocalDensity.current
                val viewportAspectRatio = videoAspectRatio
                val viewportLayout = remember(maxWidth, maxHeight, viewportAspectRatio, liveVideoSize) {
                    with(density) {
                        resolveVideoViewportLayout(
                            containerWidth = maxWidth.roundToPx(),
                            containerHeight = maxHeight.roundToPx(),
                            aspectRatio = viewportAspectRatio,
                            videoWidth = (liveVideoSize.width * liveVideoSize.pixelWidthHeightRatio).roundToInt(),
                            videoHeight = liveVideoSize.height,
                        )
                    }
                }
                val viewportModifier = with(density) {
                    Modifier.graphicsLayer(
                        scaleX = videoScale,
                        scaleY = videoScale
                    ).requiredSize(
                        width = viewportLayout.width.toDp(),
                        height = viewportLayout.height.toDp()
                    )
                }

                key(exoPlayer, useTextureSurfaceForLivePlayer) {
                AndroidView(
                    factory = { ctx ->
                        val livePlayerView = if (useTextureSurfaceForLivePlayer) {
                            LayoutInflater.from(ctx)
                                .inflate(com.android.purebilibili.R.layout.view_player_texture, null, false) as PlayerView
                        } else {
                            PlayerView(ctx)
                        }
                        livePlayerView.apply {
                            player = if (shouldBindLivePlayerViewForAudioOnly(isLiveAudioOnly)) exoPlayer else null
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = viewportAspectRatio.playerResizeMode
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    update = { playerView ->
                        playerView.player = if (shouldBindLivePlayerViewForAudioOnly(isLiveAudioOnly)) exoPlayer else null
                        if (playerView.resizeMode != viewportAspectRatio.playerResizeMode) {
                            playerView.resizeMode = viewportAspectRatio.playerResizeMode
                        }
                        playerView.visibility = if (sharedSurfaceVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                        playerView.alpha = if (sharedSurfaceVisible) 1f else 0f
                        playerView.videoSurfaceView?.alpha = if (sharedSurfaceVisible) 1f else 0f
                        playerViewRef = playerView
                    },
                    onRelease = { view ->
                        if (playerViewRef === view) playerViewRef = null
                        view.player = null
                    },
                    modifier = viewportModifier
                )
                }
                // 封面常驻就绪，避免侧滑或点击返回隐藏 PlayerView 首帧才异步挂载封面造成闪黑。
                if (liveCover.isNotBlank()) {
                    AsyncImage(
                        model = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(liveCover),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (!sharedSurfaceVisible) 1f else 0f }
                            .background(Color.Black),
                    )
                }
            }

            val successState = uiState as? LivePlayerState.Success
            val liveDanmakuLayerEnabled =
                !(pipPresentation && pipNoDanmaku) &&
                shouldRenderLiveDanmakuOverlayForAudioOnly(
                    isDanmakuEnabled = successState?.isDanmakuEnabled == true,
                    isAudioOnly = isLiveAudioOnly,
                )
            val showLiveDanmakuOverlay =
                liveLayoutMode != LiveRoomLayoutMode.PortraitVerticalOverlay || isFullscreen || pipPresentation
            val liveDanmakuInPip = pipPresentation
            if (liveDanmakuLayerEnabled && sharedSurfaceVisible) {
                val liveDanmakuActive = showLiveDanmakuOverlay && (
                    (liveVisualWorkActive && !miniPlayerManager.isMiniMode) ||
                        (liveDanmakuInPip && !pipNoDanmaku)
                    )
                val useIndependentLiveDanmakuSurface = shouldUseIndependentDanmakuSurface(
                    useTextureVideoSurface = useTextureSurfaceForLivePlayer,
                    anime4kActive = false,
                    capturePlayerPage = captureLivePlayerPage,
                    isInPipMode = liveDanmakuInPip,
                )
                val liveDanmakuTargetFrameRateHz = rememberPlayerDanmakuTargetFrameRateHz(
                    player = exoPlayer,
                    isPlaying = isPlaying && liveDanmakuActive,
                )
                // 隐藏时保留渲染器并暂停，恢复后跟随直播时间；画中画沿用弹幕显示偏好。
                LiveDanmakuOverlay(
                    danmakuFlow = viewModel.danmakuFlow,
                    sessionKey = "$siteId:$roomId",
                    active = liveDanmakuActive,
                    interactionEnabled = liveDanmakuInteractionEnabled && liveVisualWorkActive &&
                        !liveDanmakuInPip && !miniPlayerManager.isMiniMode,
                    onCaptureReady = { captureLiveDanmakuTap = it },
                    onUserClick = onUserClick,
                    onReport = { item ->
                        if (canInteractWithRoom) reportTarget = item
                        else showLiveFeedback("请先登录")
                    },
                    isFullscreen = isFullscreen,
                    displayArea = liveDanmakuDisplayArea,
                    danmakuSettings = liveDanmakuSettings,
                    useIndependentSurface = useIndependentLiveDanmakuSurface,
                    surfaceVisible = showLiveDanmakuOverlay,
                    targetFrameRateHz = liveDanmakuTargetFrameRateHz,
                    modifier = Modifier.fillMaxSize()
                        .then(if (captureLivePlayerPage) livePlayerBackdrop.danmaku.modifier else Modifier)
                        .graphicsLayer {
                            alpha = if (showLiveDanmakuOverlay) 1f else 0f
                        },
                )
            }

            // 玻璃控件与菜单只消费同级视频、弹幕源，不将自身录入背景。
            if (!pipPresentation) CompositionLocalProvider(
                com.android.purebilibili.feature.video.ui.overlay.LocalPlayerPageBackdrop provides
                    livePlayerBackdrop.backdrop.takeIf { captureLivePlayerPage && livePlayerBackdrop.video.isReady },
            ) {
                val controlsStats by viewModel.liveRoomStats.collectAsStateWithLifecycle()
                val controlsClockActive = roomInfo.liveStartTime > 0L &&
                    liveHostLifecycleState.isAtLeast(Lifecycle.State.STARTED)
                val controlsClock by produceState(
                    System.currentTimeMillis(),
                    roomInfo.roomId,
                    roomInfo.liveStartTime,
                    controlsClockActive,
                ) {
                    if (!controlsClockActive) return@produceState
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(30_000L)
                    }
                }
                val watchedDisplay = controlsStats.watchedText.ifBlank { roomInfo.watchedText }
                val onlineRankDisplay = controlsStats.onlineRankText.ifBlank { roomInfo.onlineRankText }
                val controlsSubtitle = remember(
                    isFullscreen,
                    anchorInfo.uname,
                    uname,
                    watchedDisplay,
                    onlineRankDisplay,
                    roomInfo.liveStartTime,
                    controlsClock,
                ) {
                    if (isFullscreen) {
                        listOf(
                            anchorInfo.uname.ifBlank { uname },
                            watchedDisplay,
                            onlineRankDisplay,
                            formatLiveDuration(roomInfo.liveStartTime, controlsClock),
                        ).filter { it.isNotBlank() }.joinToString("  ")
                    } else {
                        listOf(
                            watchedDisplay,
                            formatLiveDuration(roomInfo.liveStartTime, controlsClock),
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                    }
                }
                LivePlayerControls(
                    glassEnabled = livePageGlassSupported,
                    onBackdropRequiredChange = { liveBackdropRequired = it },
                    isPlaying = exoPlayer.playWhenReady,
                    isFullscreen = isFullscreen,
                    gesturePolicy = playerGesturePolicy,
                    fullscreenRequested = fullscreenRequested,
                    fullscreenGestureReverse = fullscreenGestureReverse,
                    captureDanmakuTap = { captureLiveDanmakuTap?.invoke(it) },
                    onDanmakuInteractionEnabledChange = { liveDanmakuInteractionEnabled = it },
                    showTopBar = shouldShowLivePlayerControlsTopBar(
                        layoutMode = liveLayoutMode,
                        isFullscreen = isFullscreen
                    ),
                    title = liveRoomTitle,
                    subtitle = controlsSubtitle,
                    onOpenRank = { showContributionRankSheet = true },
                    onShareRoom = if (isFullscreen) ::shareLiveUrl else null,
                    onPlayPause = {
                        if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
                    },
                    onToggleFullscreen = { toggleFullscreen() },
                    onBack = {
                        when {
                            landscapeInteractionVisible -> showLandscapeInteraction = false
                            fullscreenRequested -> toggleFullscreen()
                            else -> exitLiveRoom()
                        }
                    },
                    // 弹幕开关
                    isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
                    onToggleDanmaku = { toggleLiveDanmaku() },
                    onOpenDanmakuSettings = { showDanmakuSettingsDialog = true },
                    danmakuSettingsSource = liveDanmakuSettingsSource,
                    danmakuSettingsVisible = showDanmakuSettingsDialog,
                    onOpenBlockSettings = ::openBlockSettings,
                    // [新增] 刷新
                    onRefresh = { viewModel.retry() },
                    isAudioOnly = successState?.isAudioOnly ?: false,
                    switchingAudioOnly = switchingAudioOnly,
                    onToggleAudioOnly = { viewModel.toggleAudioOnly() },
                    isBackgroundPlaybackEnabled = backgroundPlaybackEnabled,
                    onToggleBackgroundPlayback = { toggleBackgroundPlayback() },
                    onOpenShutdownTimer = { showShutdownTimerDialog = true },
                    onOpenPlayerInfo = { showPlayerInfoDialog = true },
                    onOpenVolume = { showVolumeDialog = true },
                    onOpenPlaybackPreferences = { showPlaybackPreferences = true },
                    onOpenSend = { openDanmakuInput() },
                    showInteractionToggle = landscapeInteractionAvailable,
                    interactionWindowVisible = landscapeInteractionVisible,
                    interactionWindowSource = liveInteractionWindowSource,
                    onToggleInteractionWindow = { showLandscapeInteraction = !showLandscapeInteraction },
                    videoFitDesc = videoAspectRatio.displayName,
                    videoFitOptions = remember(videoAspectRatio) {
                        listOf(VideoAspectRatio.STRETCH, VideoAspectRatio.FIT, VideoAspectRatio.FILL,
                            VideoAspectRatio.FIT_WIDTH, VideoAspectRatio.FIT_HEIGHT, VideoAspectRatio.ORIGINAL,
                            VideoAspectRatio.SCALE_DOWN, VideoAspectRatio.RATIO_4_3, VideoAspectRatio.RATIO_16_9).map { mode ->
                            AppWindowAction(mode.displayName, selected = mode == videoAspectRatio,
                                onClick = { videoAspectRatio = mode })
                        }
                    },
                    currentQualityDesc = currentQualityDesc,
                    qualityOptions = remember(successState?.qualityList, successState?.currentQuality) {
                        successState?.qualityList.orEmpty().map { quality ->
                            AppWindowAction(quality.desc, selected = quality.qn == successState?.currentQuality,
                                onClick = { viewModel.changeQuality(quality.qn) })
                        }
                    },
                    onSourceClick = { showStreamSourceSheet = true },
                    videoScale = videoScale,
                    onScale = { zoom -> videoScale = (videoScale * zoom).coerceIn(0.5f, 3f) },
                    onResetScale = { videoScale = 1f },
                    showPipButton = showLivePipButton,
                    canEnterPip = canEnterLivePip,
                    onEnterPip = { enterLivePip() },
                    showLockButton = isFullscreen,
                    onCaptureScreenshot = { captureLiveScreenshot() },
                    applyTopSystemBarPadding = shouldApplyLiveTopControlSystemInsets(
                        layoutMode = liveLayoutMode,
                        isFullscreen = isFullscreen
                    ),
                    applyBottomSystemBarPadding = shouldApplyLiveBottomControlSystemInsets(
                        layoutMode = liveLayoutMode,
                        isFullscreen = isFullscreen,
                        hasReservedBottomOverlay = false
                    ),
                    bottomControlsBottomPadding = 0.dp,
                )
                if (!isLiveAudioOnly && successState != null) {
                    val liveQualityIds = remember(successState.qualityList) {
                        successState.qualityList.map { it.qn }
                    }
                    val liveQualityLabels = remember(successState.qualityList) {
                        successState.qualityList.map { it.desc }
                    }
                    com.android.purebilibili.feature.video.ui.overlay.WeakNetworkDegradeOverlayHost(
                        player = exoPlayer,
                        mediaKey = "live:$siteId:$roomId",
                        currentQuality = successState.currentQuality,
                        qualityIds = liveQualityIds,
                        qualityLabels = liveQualityLabels,
                        onSelectLowerQuality = { target -> viewModel.changeQuality(target.qualityId) },
                        secondaryActionLabel = "切换线路",
                        onSecondaryAction = { showStreamSourceSheet = true },
                        useGlass = livePageGlassSupported,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                top = if (isFullscreen) 56.dp else 44.dp,
                                start = 12.dp,
                                end = 12.dp,
                            ),
                    )
                }
            }

            if (showFullscreenSuperChat && isLandscape && liveDanmakuInteractionEnabled) {
                BoxWithConstraints(Modifier.matchParentSize().safeDrawingPadding()) {
                    superChatStrip(Modifier.padding(top = maxOf(72.dp, maxHeight * 0.34f)))
                }
            }

            if (successState?.isAudioOnly == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(roomColorTokens.baseBackgroundColor.copy(alpha = 0.46f))
                        .padding(
                            horizontal = AppSpacingTokens.Large,
                            vertical = AppSpacingTokens.Medium
                        )
                ) {
                    AppText(
                        text = "仅播放音频",
                        color = roomColorTokens.inputOverlayColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Loading/Error Indicator
            if (uiState is LivePlayerState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.android.purebilibili.feature.video.ui.overlay.BiliTvAnimatedBufferingIndicator(tint = AppContentColors.accent())
                }
            } else if (successState != null) {
                com.android.purebilibili.feature.video.ui.overlay.PlayerBufferingIndicator(
                    player = exoPlayer, modifier = Modifier.align(Alignment.Center),
                )
            }
            if (uiState is LivePlayerState.Error) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(
                            (uiState as LivePlayerState.Error).message,
                            color = roomColorTokens.inputOverlayColor
                        )
                        AppButton(onClick = { viewModel.retry() }) { AppText("重试") }
                    }
                }
            }
        }
    }

    val chatContent: @Composable (Boolean, Boolean) -> Unit = { isOverlay, showHeader ->
        LiveRoomUpdates(viewModel.chatHistory) { chatHistory ->
            val visibleMessages = remember(chatHistory, liveSuperChatEnabled) {
                if (liveSuperChatEnabled) chatHistory else chatHistory.filterNot { it.item.isSuperChat }
            }
            LiveChatSection(
                compact = landscapeInteractionAvailable,
                messages = visibleMessages,
                anchorUid = anchorInfo.uid,
                canInteract = canInteractWithRoom,
                isOverlay = isOverlay,
                onUserClick = onUserClick,
                onAtUser = { item ->
                    viewModel.setReplyTarget(item)
                    openDanmakuInput()
                },
                onBlockUser = { item ->
                    if (item.uid > 0L) {
                        viewModel.shieldUser(item.uid)
                    } else {
                        val keyword = item.uname.ifBlank { item.text }
                        if (keyword.isNotBlank()) addLiveBlockKeyword(keyword)
                    }
                },
                onReportDanmaku = { item ->
                    reportTarget = item
                },
                listState = chatListState,
                followingLatest = followingLatestChat,
                onFollowingLatestChange = { followingLatestChat = it },
                active = (!landscapeInteractionAvailable || (landscapeInteractionVisible && !landscapeInteractionCollapsed)) &&
                    selectedInteractionTab == 0 && liveHostLifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
                    !miniPlayerManager.isMiniMode && !pipPresentation,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val interactionInput: @Composable (Boolean) -> Unit = { isOverlay ->
        LiveChatInputBar(
            compact = landscapeInteractionAvailable,
            isOverlay = true,
            isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
            onToggleDanmaku = { toggleLiveDanmaku() },
            onLike = { count -> viewModel.clickLike(count) },
            onOpenEmote = { openDanmakuInput(showEmoticons = true) },
            onOpenSend = { openDanmakuInput() },
            selectedPage = selectedInteractionTab,
            showPageIndicator = liveSuperChatEnabled && liveSuperChatPanelEnabled,
            canLike = canInteractWithRoom,
            onLikeUnavailable = { showLiveFeedback("账号未登录") },
            medalContent = medalContent,
        )
    }

    val interactionContent: @Composable (Boolean, Boolean) -> Unit = { isOverlay, includeFooter ->
        LiveRoomCountUpdates(viewModel.superChatItems) { superChatCount ->
            LivePrimaryInteractionPanel(
                compact = landscapeInteractionAvailable,
                isOverlay = isOverlay,
                selectedTab = selectedInteractionTab,
                showSuperChat = liveSuperChatEnabled && liveSuperChatPanelEnabled,
                superChatCount = superChatCount,
                onSelectedTab = { selectedInteractionTab = it },
                chatContent = { chatContent(isOverlay, false) },
                persistentFooter = if (includeFooter) { { interactionInput(isOverlay) } } else null,
                superChatContent = {
                    LiveRoomUpdates(viewModel.superChatItems) { superChatItems ->
                        LiveSuperChatSection(
                            items = superChatItems,
                            listState = superChatListState,
                            onCountdownPauseChange = onSuperChatCountdownPauseChange,
                            persistent = liveSuperChatPersistent,
                            modifier = Modifier.fillMaxSize(),
                            onExpired = { item ->
                                if (item.superChatId > 0L) {
                                    viewModel.dismissSuperChat(item.superChatId)
                                }
                            },
                            onUserClick = onUserClick,
                            onReport = onSuperChatReport,
                        )
                    }
                },
            )
        }
    }

    val liveKeyboardShortcutOverrides = com.android.purebilibili.core.ui.adaptive.LocalKeyboardShortcutOverrides.current
    val keyboardModifier = if (liveKeyboardControlsEnabled) {
        Modifier
            .focusRequester(liveKeyboardFocusRequester)
            .focusGroup()
            .onKeyEvent { event ->
                val action = resolvePlayerKeyAction(
                    event = event,
                    isInPipMode = pipPresentation,
                    isTextInputActive = showSendDanmakuSheet || showShareMessageSheet,
                    overrides = liveKeyboardShortcutOverrides,
                ) ?: return@onKeyEvent false
                when (action) {
                    PlayerKeyAction.PlayPause -> {
                        if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
                        true
                    }
                    PlayerKeyAction.VolumeUp, PlayerKeyAction.VolumeDown -> {
                        val direction = if (action == PlayerKeyAction.VolumeUp) {
                            android.media.AudioManager.ADJUST_RAISE
                        } else {
                            android.media.AudioManager.ADJUST_LOWER
                        }
                        (context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager)
                            ?.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                direction,
                                android.media.AudioManager.FLAG_SHOW_UI,
                            )
                        true
                    }
                    PlayerKeyAction.ToggleMute -> {
                        exoPlayer.volume = if (exoPlayer.volume > 0f) 0f else 1f
                        true
                    }
                    PlayerKeyAction.ToggleFullscreen -> {
                        toggleFullscreen()
                        true
                    }
                    PlayerKeyAction.ToggleDanmaku -> {
                        toggleLiveDanmaku()
                        true
                    }
                    PlayerKeyAction.ToggleLike -> {
                        viewModel.clickLike()
                        true
                    }
                    PlayerKeyAction.TakeScreenshot -> {
                        captureLiveScreenshot()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    } else {
        Modifier
    }

    val roomAppBar: @Composable () -> Unit = {
        val barStats by viewModel.liveRoomStats.collectAsStateWithLifecycle()
        val barClockActive = roomInfo.liveStartTime > 0L &&
            liveHostLifecycleState.isAtLeast(Lifecycle.State.STARTED)
        val barClock by produceState(
            System.currentTimeMillis(),
            roomInfo.roomId,
            roomInfo.liveStartTime,
            barClockActive,
        ) {
            if (!barClockActive) return@produceState
            while (true) {
                value = System.currentTimeMillis()
                delay(30_000L)
            }
        }
        val watchedDisplay = barStats.watchedText.ifBlank { roomInfo.watchedText }
        val onlineRankDisplay = barStats.onlineRankText.ifBlank { roomInfo.onlineRankText }
        val barSubtitle = remember(watchedDisplay, roomInfo.liveStartTime, barClock) {
            listOf(
                watchedDisplay,
                formatLiveDuration(roomInfo.liveStartTime, barClock),
            ).filter { it.isNotBlank() }.joinToString(" · ")
        }
        LivePortraitOverlayAppBar(
            roomTitle = liveRoomTitle,
            anchorInfo = anchorInfo,
            subtitle = barSubtitle,
            onBack = { exitLiveRoom() },
            onUserClick = onUserClick,
            onOpenFansPanel = if (siteId == "bilibili" && canInteractWithRoom &&
                roomInfo.roomId > 0L && anchorInfo.uid > 0L
            ) {
                {
                    val liveId = roomInfo.roomId
                    val anchorId = anchorInfo.uid
                    fansPanelUrl = "https://live.bilibili.com/p/html/live-app-fanspanel/index.html" +
                        "?roomId=$liveId&anchorId=$anchorId&room_id=$liveId&anchor_id=$anchorId"
                }
            } else {
                null
            },
            onCopyLink = { copyLiveUrl() },
            onShare = { shareLiveUrl() },
            onShareToMessage = { shareLiveToMessage() },
            onOpenBrowser = { openLiveUrl() },
            onlineRankText = onlineRankDisplay,
            onOpenRank = { showContributionRankSheet = true },

        )
    }

    // 统一容器承载直播布局和页面级浮层。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(keyboardModifier)

    ) {
    LiveRoomLayout(
        mode = if (pipPresentation) LiveRoomLayoutMode.LandscapeOverlay else liveLayoutMode,
        isFullscreen = isFullscreen || pipPresentation,
        backgroundUrl = when (liveLayoutMode) {
            LiveRoomLayoutMode.PortraitPanel, LiveRoomLayoutMode.PortraitVerticalOverlay -> roomInfo.appBackground
            else -> roomInfo.background
        },
        hasObstructingHinge = hingeAdaptiveInfo.shouldAvoidHinge,
        modifier = Modifier.fillMaxSize(),
        header = roomAppBar,
        player = playerContent,
        interaction = interactionContent,
        inputFooter = { interactionInput(true) },
        superChat = if (!isLandscape && !isFullscreen && !pipPresentation &&
            successState != null && superChatOverlayEnabled
        ) {
            {
                superChatStrip(
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                )
            }
        } else null,
    )

    if (landscapeInteractionVisible) {
        com.android.purebilibili.feature.live.components.LiveLandscapeInteractionWindow(
            roomKey = "$siteId:$roomId",
            glass = livePageGlassSupported,
            source = liveInteractionWindowSource,
            backdrop = livePlayerBackdrop.backdrop.takeIf { captureLivePlayerPage && livePlayerBackdrop.video.isReady },
            onClose = { showLandscapeInteraction = false },
            modifier = Modifier.fillMaxSize(),
            onCollapsedChange = { landscapeInteractionCollapsed = it },
            headerAction = {
                LiveRoomCountUpdates(viewModel.superChatItems) { superChatCount ->
                    if (liveSuperChatEnabled && liveSuperChatPanelEnabled &&
                        (superChatCount > 0 || selectedInteractionTab == 1)
                    ) {
                        AppTextButton(onClick = { selectedInteractionTab = if (selectedInteractionTab == 0) 1 else 0 }) {
                            AppText(
                                if (selectedInteractionTab == 1) "弹幕" else "SC(${if (superChatCount > 99) "99+" else superChatCount.toString()})",
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
        ) { interactionContent(true, true) }
    }

    if (showFullscreenSuperChat && !isLandscape) {
        LiveSuperChatFlashOverlay(
            flashFlow = viewModel.superChatFlashFlow,
            onCountdownPauseChange = onSuperChatCountdownPauseChange,
            onUserClick = onUserClick,
            onReport = onSuperChatReport,
            persistUntilDismiss = liveSuperChatPersistent,
            maxWidthDp = liveSuperChatMaxWidthDp,
            opacity = liveSuperChatOpacity,
            cardScale = liveSuperChatScale,
            modifier = Modifier.fillMaxSize()
        )
    }

    // 播放错误允许关闭提示继续重试，或直接退出直播间。
    if (uiState is LivePlayerState.Error) {
        val error = (uiState as LivePlayerState.Error).message
        var showError by remember(roomId, error) { mutableStateOf(true) }
        if (showError) AppAlertDialog(
            onDismissRequest = { showError = false },
            title = { AppText(error) },
            confirmButton = { AppTextButton(onClick = { showError = false; exitLiveRoom() }) { AppText("退出直播间") } },
            dismissButton = { AppTextButton(onClick = { showError = false }) { AppText("关闭") } },
        )
    }

    if (showPlaybackPreferences) {
        com.android.purebilibili.feature.live.components.LivePlaybackPreferencesDialog(
            onDismiss = { showPlaybackPreferences = false },
            onQualityChanged = viewModel::reloadPlaybackPreferences,
        )
    }

    if (showVolumeDialog) {
        var volume by remember(exoPlayer) { mutableFloatStateOf(exoPlayer.volume) }
        AppAlertDialog(
            onDismissRequest = { showVolumeDialog = false },
            title = { AppText("播放器音量：${(volume * 100).roundToInt()}%") },
            text = {
                AppSlider(value = volume, onValueChange = {
                    volume = it
                    exoPlayer.volume = it
                }, valueRange = 0f..1f)
            },
            confirmButton = { AppTextButton(onClick = { showVolumeDialog = false }) { AppText("完成") } },
        )
    }

    if (showDanmakuSettingsDialog) {
        val useNativeMiuix = settingsStyle == AppUiStyle.MIUIX &&
            !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.popupGlassEnabled
        LiveDanmakuSettingsDialog(
            uiStyle = if (useNativeMiuix) settingsStyle else AppUiStyle.MATERIAL3,
            iconStyle = if (useNativeMiuix) settingsIconStyle else AppIconStyle.MD3_STANDARD,
            isFullscreen = isFullscreen,
            source = liveDanmakuSettingsSource,
            danmakuEnabled = successState?.isDanmakuEnabled ?: true,
            displayArea = liveDanmakuDisplayArea,
            normalFontScale = liveDanmakuStylePreview[LiveDanmakuStyleOption.NormalFontScale] ?: normalDanmakuSettings.fontScale,
            fullscreenFontScale = liveDanmakuStylePreview[LiveDanmakuStyleOption.FullscreenFontScale] ?: fullscreenDanmakuSettings.fontScale,
            advancedSettings = liveDanmakuSettings,
            opacity = liveDanmakuSettings.opacity,
            speed = liveDanmakuSettings.speed,
            allowScroll = liveDanmakuSettings.allowScroll,
            allowTop = liveDanmakuSettings.allowTop,
            allowBottom = liveDanmakuSettings.allowBottom,
            allowColorful = liveDanmakuSettings.allowColorful,
            superChatEnabled = liveSuperChatEnabled,
            onSuperChatEnabledChange = { enabled ->
                coroutineScope.launch { SettingsManager.setLiveSuperChatEnabled(context, enabled) }
            },
            superChatPanelEnabled = liveSuperChatPanelEnabled,
            onToggleSuperChatPanel = {
                coroutineScope.launch { SettingsManager.setLiveSuperChatPanelEnabled(context, !liveSuperChatPanelEnabled) }
            },
            superChatFlashEnabled = liveSuperChatFlashEnabled,
            superChatPersistent = liveSuperChatPersistent,
            superChatMaxWidthDp = liveSuperChatMaxWidthDp,
            superChatOpacity = liveSuperChatOpacity,
            superChatScale = liveSuperChatScale,
            keyboardControlsEnabled = liveKeyboardControlsEnabled,
            onToggleDanmaku = { toggleLiveDanmaku() },
            onDisplayAreaSelected = { area ->
                coroutineScope.launch {
                    SettingsManager.setDanmakuArea(context, area, liveDanmakuSettingsScope)
                }
            },
            onStylePreview = { option, value -> draggingLiveDanmakuStyles[option] = value },
            onStyleChanged = { option, value ->
                val saved = savedLiveDanmakuStyleValue(
                    option, normalDanmakuSettings, fullscreenDanmakuSettings, isLandscape,
                    savedLiveSuperChatMaxWidthDp, savedLiveSuperChatOpacity, savedLiveSuperChatScale,
                )
                if (kotlin.math.abs(saved - value) < 0.0001f) pendingLiveDanmakuStyles.remove(option)
                else pendingLiveDanmakuStyles[option] = value
                draggingLiveDanmakuStyles.remove(option)
                coroutineScope.launch {
                    try {
                        liveDanmakuWriteMutex.withLock {
                            when (option) {
                                LiveDanmakuStyleOption.NormalFontScale -> SettingsManager.setDanmakuFontScale(context, value, DanmakuSettingsScope.PORTRAIT)
                                LiveDanmakuStyleOption.FullscreenFontScale -> SettingsManager.setDanmakuFontScale(context, value, DanmakuSettingsScope.LANDSCAPE)
                                LiveDanmakuStyleOption.Opacity -> SettingsManager.setDanmakuOpacity(context, value, liveDanmakuSettingsScope)
                                LiveDanmakuStyleOption.Speed -> SettingsManager.setDanmakuSpeed(context, value, liveDanmakuSettingsScope)
                                LiveDanmakuStyleOption.FontWeight -> SettingsManager.setDanmakuFontWeight(context, value.roundToInt(), liveDanmakuSettingsScope)
                                LiveDanmakuStyleOption.StrokeWidth -> SettingsManager.setDanmakuStrokeWidth(context, value, liveDanmakuSettingsScope)
                                LiveDanmakuStyleOption.LineHeight -> SettingsManager.setDanmakuLineHeight(context, value, liveDanmakuSettingsScope)
                                LiveDanmakuStyleOption.ScrollDuration -> SettingsManager.setDanmakuScrollDurationSeconds(context, value, liveDanmakuSettingsScope)
                                LiveDanmakuStyleOption.StaticDuration -> SettingsManager.setDanmakuStaticDurationSeconds(context, value, liveDanmakuSettingsScope)
                                LiveDanmakuStyleOption.SuperChatWidth -> SettingsManager.setLiveSuperChatMaxWidthDp(context, value.roundToInt())
                                LiveDanmakuStyleOption.SuperChatOpacity -> SettingsManager.setLiveSuperChatFlashOpacity(context, value)
                                LiveDanmakuStyleOption.SuperChatScale -> SettingsManager.setLiveSuperChatFlashScale(context, value)
                            }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (pendingLiveDanmakuStyles[option] == value) pendingLiveDanmakuStyles.remove(option)
                        showLiveFeedback("弹幕设置保存失败，请重试")
                    }
                }
            },
            onMassiveModeChanged = { value ->
                coroutineScope.launch { SettingsManager.setDanmakuMassiveMode(context, value, liveDanmakuSettingsScope) }
            },
            onStaticToScrollChanged = { value ->
                coroutineScope.launch { SettingsManager.setDanmakuStaticToScroll(context, value, liveDanmakuSettingsScope) }
            },
            onFixedVelocityChanged = { value ->
                coroutineScope.launch { SettingsManager.setDanmakuScrollFixedVelocity(context, value, liveDanmakuSettingsScope) }
            },
            onToggleAllowScroll = {
                coroutineScope.launch {
                    SettingsManager.setDanmakuAllowScroll(
                        context,
                        !liveDanmakuSettings.allowScroll,
                        liveDanmakuSettingsScope
                    )
                }
            },
            onToggleAllowTop = {
                coroutineScope.launch {
                    SettingsManager.setDanmakuAllowTop(
                        context,
                        !liveDanmakuSettings.allowTop,
                        liveDanmakuSettingsScope
                    )
                }
            },
            onToggleAllowBottom = {
                coroutineScope.launch {
                    SettingsManager.setDanmakuAllowBottom(
                        context,
                        !liveDanmakuSettings.allowBottom,
                        liveDanmakuSettingsScope
                    )
                }
            },
            onToggleAllowColorful = {
                coroutineScope.launch {
                    SettingsManager.setDanmakuAllowColorful(
                        context,
                        !liveDanmakuSettings.allowColorful,
                        liveDanmakuSettingsScope
                    )
                }
            },
            onToggleSuperChatFlash = {
                coroutineScope.launch {
                    SettingsManager.setLiveSuperChatFlashEnabled(
                        context,
                        !liveSuperChatFlashEnabled
                    )
                }
            },
            onToggleSuperChatPersistent = {
                viewModel.setSuperChatPersistence(!liveSuperChatPersistent)
                coroutineScope.launch {
                    SettingsManager.setLiveSuperChatPersistent(context, !liveSuperChatPersistent)
                }
            },
            onToggleKeyboardControls = {
                coroutineScope.launch {
                    SettingsManager.setLiveKeyboardControlsEnabled(context, !liveKeyboardControlsEnabled)
                }
            },
            onOpenBlock = {
                showDanmakuSettingsDialog = false
                openBlockSettings()
            },
            onDismiss = { showDanmakuSettingsDialog = false }
        )
    }

    if (showBlockDialog) {
        val shieldInfo by viewModel.shieldInfo.collectAsStateWithLifecycle()
        LiveDmBlockSheet(
            shieldInfo = shieldInfo,
            isLoggedIn = com.android.purebilibili.core.store.TokenManager.midCache != null,
            onAddKeyword = { keyword -> viewModel.addShieldKeyword(keyword) },
            onAddUser = viewModel::shieldUser,
            onDeleteKeyword = { keyword -> viewModel.deleteShieldKeyword(keyword) },
            onUnblockUser = { user -> viewModel.unshieldUser(user.uid) },
            onSetRule = { type, level -> viewModel.setSilentRule(type, level) },
            onDismiss = { showBlockDialog = false }
        )
    }

    reportTarget?.let { target ->
        LiveReportDialog(
            target = target,
            onDismiss = { reportTarget = null },
            onReport = { reason ->
                viewModel.reportDanmaku(target, reason)
                reportTarget = null
            }
        )
    }


    if (showPlayerInfoDialog) {
        LivePlayerInfoDialog(
            roomId = bilibiliRoomId,
            roomTitle = liveRoomTitle,
            currentQuality = currentQualityDesc,
            videoFit = videoAspectRatio.displayName,
            isAudioOnly = successState?.isAudioOnly ?: false,
            isPlaying = isPlaying,
            playUrl = successState?.playUrl.orEmpty(),
            onDismiss = { showPlayerInfoDialog = false }
        )
    }

    if (showShutdownTimerDialog) {
        LiveShutdownTimerDialog(
            activeTargetMillis = shutdownAtMillis,
            selectedMinutes = shutdownMinutes,
            exitApp = shutdownExitsApp,
            onExitAppChange = { shutdownExitsApp = it },
            onSetMinutes = { minutes ->
                shutdownMinutes = minutes.takeIf { it > 0 }
                shutdownAtMillis = shutdownMinutes?.let { SystemClock.elapsedRealtime() + it * 60_000L }
                showShutdownTimerDialog = false
                val message = if (minutes == 0) {
                    "已取消定时关闭"
                } else if (shutdownExitsApp) {
                    "${minutes}分钟后退出应用"
                } else {
                    "${minutes}分钟后暂停直播"
                }
                showLiveFeedback(message)
            },
            onDismiss = { showShutdownTimerDialog = false }
        )
    }

    if (showContributionRankSheet) {
        LiveContributionRankSheet(
            roomTitle = liveRoomTitle,
            anchorInfo = anchorInfo,
            roomInfo = roomInfo,
            onUserClick = onUserClick,
            onDismiss = { showContributionRankSheet = false }
        )
    }

    fansPanelUrl?.let { url ->
        AppModalBottomSheet(
            onDismissRequest = { fansPanelUrl = null },
            useWindowPredictiveBack = true,
            dragHandle = null,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(configuration.screenHeightDp.dp * 0.8f),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    com.android.purebilibili.feature.web.WebViewScreen(
                        url = url,
                        title = "粉丝团",
                        onBack = { fansPanelUrl = null },
                        mobileUserAgent = true,
                    )
                }
            }
        }
    }

    if (showShareMessageSheet) {
        LiveShareToMessageSheet(
            roomId = bilibiliRoomId,
            roomTitle = liveRoomTitle,
            cover = liveCoverForUi,
            author = anchorInfo.uname,
            authorId = anchorInfo.uid,
            onDismiss = { showShareMessageSheet = false },
            onResult = { success, message ->
                showLiveFeedback(message)
            },
        )
    }

    if (showSendDanmakuSheet) {
        val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
        val isSending by viewModel.isSending.collectAsStateWithLifecycle()
        val emoticonPackages by viewModel.emoticonPackages.collectAsStateWithLifecycle()
        LiveSendDanmakuSheet(
            onDismiss = {
                showSendDanmakuSheet = false
                showEmoticonSheet = false
            },
            onSend = { message, color, mode ->
                viewModel.sendDanmaku(message, color, mode)
            },
            message = danmakuDraft,
            emoticonPackages = emoticonPackages,
            onMessageChange = { danmakuDraft = it },
            permission = successState?.danmakuPermission ?: com.android.purebilibili.data.repository.LiveDanmakuPermission(),
            replyTarget = replyTarget,
            isSending = isSending,
            onClearReply = viewModel::clearReplyTarget,
            onOpenEmote = { showEmoticonSheet = !showEmoticonSheet },
            medalContent = medalContent,
            emoticonContent = if (showEmoticonSheet) ({
                LiveEmoticonPanel(
                    packages = emoticonPackages,
                    enabled = !isSending && (successState?.danmakuPermission?.canSend != false),
                    onInsertText = { item ->
                        val maxLength = successState?.danmakuPermission?.maxLength?.takeIf { it > 0 } ?: 40
                        insertLiveTextEmoticon(danmakuDraft, item, maxLength)
                            .onSuccess { danmakuDraft = it }
                            .onFailure { showLiveFeedback(it.toUserFriendlyMessage("插入表情失败")) }
                    },
                    onSendEmoticon = viewModel::sendEmoticon,
                )
            }) else null,
        )
    }

    if (showStreamSourceSheet) {
        val candidates = viewModel.playbackCandidatesSnapshot()
        val (activeCandidateIndex, activeUrlIndex) = viewModel.currentPlaybackPosition()
        if (candidates.isNotEmpty()) {
            LiveStreamSourceSheet(
                candidates = candidates,
                activeCandidateIndex = activeCandidateIndex,
                activeUrlIndex = activeUrlIndex,
                onSelect = { candidateIndex, urlIndex ->
                    viewModel.switchPlaybackCandidate(candidateIndex, urlIndex)
                    showStreamSourceSheet = false
                },
                onDismiss = { showStreamSourceSheet = false }
            )
        } else {
            // 无候选时直接关闭并提示
            LaunchedEffect(Unit) {
                showLiveFeedback("暂无可用线路")
                showStreamSourceSheet = false
            }
        }
    }
    if (!pipPresentation) {
        com.android.purebilibili.core.ui.components.AppSnackbarHost(
            hostState = liveSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
    }
}

@Composable
private fun LivePortraitOverlayAppBar(
    roomTitle: String,
    anchorInfo: AnchorInfo,
    subtitle: String,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    onOpenFansPanel: (() -> Unit)?,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onShareToMessage: () -> Unit,
    onOpenBrowser: () -> Unit,
    onlineRankText: String,
    onOpenRank: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roomColorTokens = resolveLiveBiliPaiRoomColorTokens()
    val backIcon = rememberAppBackIcon()
    val glass = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val headerModifier = modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .height(56.dp)
        .padding(horizontal = 14.dp)
    val moreSource = com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
    Row(modifier = headerModifier, verticalAlignment = Alignment.CenterVertically) {
        LiveRoomHeaderGlassButton(glass = glass) {
            AppIconButton(
                onClick = onBack,
                modifier = Modifier.size(if (glass) 34.dp else 40.dp)
            ) {
                AppIcon(
                    backIcon,
                    contentDescription = "返回",
                    tint = roomColorTokens.inputOverlayColor
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                .clickable(enabled = anchorInfo.uid > 0L) { onUserClick(anchorInfo.uid) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = anchorInfo.face,
                contentDescription = "主播头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp).clip(CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        anchorInfo.uname.ifBlank { roomTitle },
                        modifier = Modifier.weight(1f, fill = false),
                        color = LiveStatusPalette.MediaContent,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (onlineRankText.isNotBlank()) {
                        AppText(
                            onlineRankText,
                            modifier = Modifier.padding(start = 10.dp).clickable(onClick = onOpenRank),
                            color = LiveStatusPalette.MediaContent, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AppText(
                    subtitle, color = LiveStatusPalette.MediaContent.copy(alpha = 0.75f),
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LiveRoomHeaderGlassButton(
            glass = glass,
            modifier = if (glass) Modifier.appGlassMenuSource(moreSource) else Modifier,
        ) {
            val nearbyHandoff = com.android.purebilibili.feature.nearby.LocalNearbyHandoff.current
            AppWindowActionMenu(
                compact = glass,
                source = moreSource.takeIf { glass },
                sourceCapturedByParent = glass,
                anchorModifier = Modifier.size(if (glass) 34.dp else 30.dp),
                modifier = Modifier.size(if (glass) 34.dp else 30.dp),
                groups = listOf(
                    buildList {
                        if (nearbyHandoff != null && com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED) {
                            add(AppWindowAction(
                                label = com.android.purebilibili.feature.nearby.NEARBY_TITLE,
                                icon = Icons.Outlined.Devices,
                                onClick = nearbyHandoff.openDevices,
                            ))
                        }
                        if (onOpenFansPanel != null) add(AppWindowAction(
                            label = "粉丝团",
                            icon = Icons.Outlined.Groups,
                            onClick = onOpenFansPanel,
                        ))
                        add(AppWindowAction(
                            label = "复制链接",
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopyLink,
                        ))
                        add(AppWindowAction(
                            label = "分享直播间",
                            icon = Icons.Outlined.Share,
                            onClick = onShare,
                        ))
                        add(AppWindowAction(
                            label = "浏览器打开",
                            icon = Icons.Outlined.OpenInBrowser,
                            onClick = onOpenBrowser,
                        ))
                        add(AppWindowAction(
                            label = "分享至消息",
                            icon = Icons.AutoMirrored.Outlined.ForwardToInbox,
                            onClick = onShareToMessage,
                        ))
                    },
                ),
            ) {
                AppIcon(
                    Icons.Filled.MoreVert,
                    contentDescription = "更多直播间操作",
                    tint = roomColorTokens.inputOverlayColor
                )
            }
        }
    }
}

/** 顶栏只有两个操作键拥有玻璃表面，主播信息区不附加材质或形变。 */
@Composable
private fun LiveRoomHeaderGlassButton(
    glass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!glass) {
        content()
        return
    }
    com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock(
        shape = CircleShape,
        modifier = modifier.size(34.dp),
        backdrop = LocalLiveRoomHeaderBackdrop.current,
        reuseEnabled = true,
        elasticFeedbackEnabled = true,
        useNeutralLiquidContainer = true,
        shellLensIntensity = com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale(34f),
    ) {
        CompositionLocalProvider(
            com.android.purebilibili.core.ui.components.LocalAppIconButtonGlassSurfaceProvided provides true,
            com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled provides false,
        ) { content() }
    }
}

@Composable
private fun LivePrimaryInteractionPanel(
    compact: Boolean = false,
    isOverlay: Boolean = false,
    selectedTab: Int,
    showSuperChat: Boolean,
    superChatCount: Int,
    onSelectedTab: (Int) -> Unit,
    chatContent: @Composable () -> Unit,
    superChatContent: @Composable () -> Unit,
    persistentFooter: (@Composable () -> Unit)? = null,
) {
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, if (showSuperChat) 1 else 0),
        pageCount = { if (showSuperChat) 2 else 1 },
    )
    val latestOnSelectedTab by rememberUpdatedState(onSelectedTab)
    val pagerScope = rememberCoroutineScope()
    // 点击 SC 入口与手势翻页共用页码；横竖屏恢复时直接定位。
    LaunchedEffect(selectedTab, showSuperChat, pagerState) {
        val target = selectedTab.coerceIn(0, if (showSuperChat) 1 else 0)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        if (selectedTab != target) latestOnSelectedTab(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { latestOnSelectedTab(it) }
    }
    Column(Modifier.fillMaxSize().background(Color.Transparent)) {
        com.android.purebilibili.core.ui.components.AppHorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f)
                .verticalPriorityHorizontalPagerSwipe(pagerState),
        ) { page ->
            Box(Modifier.fillMaxSize().padding(top = if (compact) 0.dp else if (isOverlay) 12.dp else 0.dp, bottom = if (compact) 4.dp else 12.dp)) {
                if (page == 0) {
                    chatContent()
                    // 紧凑悬浮窗的切换入口位于标题栏，不覆盖消息，也不挤占正文高度。
                    if (!compact && showSuperChat && superChatCount > 0) {
                        AppSurface(
                            onClick = { pagerScope.launch { pagerState.animateScrollToPage(1, animationSpec = androidx.compose.animation.core.tween(200)) } },
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.18f),
                        ) {
                            AppText("SC($superChatCount) ›", color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                } else superChatContent()
            }
        }
        persistentFooter?.invoke()
    }
}

@Composable
@kotlin.OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun LiveDanmakuSettingsDialog(
    uiStyle: AppUiStyle,
    iconStyle: AppIconStyle,
    isFullscreen: Boolean,
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource?,
    danmakuEnabled: Boolean,
    displayArea: Float,
    normalFontScale: Float,
    fullscreenFontScale: Float,
    advancedSettings: DanmakuSettings,
    opacity: Float,
    speed: Float,
    allowScroll: Boolean,
    allowTop: Boolean,
    allowBottom: Boolean,
    allowColorful: Boolean,
    superChatEnabled: Boolean,
    onSuperChatEnabledChange: (Boolean) -> Unit,
    superChatPanelEnabled: Boolean,
    onToggleSuperChatPanel: () -> Unit,
    superChatFlashEnabled: Boolean,
    superChatPersistent: Boolean,
    superChatMaxWidthDp: Int,
    superChatOpacity: Float,
    superChatScale: Float,
    keyboardControlsEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onDisplayAreaSelected: (Float) -> Unit,
    onStylePreview: (LiveDanmakuStyleOption, Float) -> Unit,
    onStyleChanged: (LiveDanmakuStyleOption, Float) -> Unit,
    onMassiveModeChanged: (Boolean) -> Unit,
    onStaticToScrollChanged: (Boolean) -> Unit,
    onFixedVelocityChanged: (Boolean) -> Unit,
    onToggleAllowScroll: () -> Unit,
    onToggleAllowTop: () -> Unit,
    onToggleAllowBottom: () -> Unit,
    onToggleAllowColorful: () -> Unit,
    onToggleSuperChatFlash: () -> Unit,
    onToggleSuperChatPersistent: () -> Unit,
    onToggleKeyboardControls: () -> Unit,
    onOpenBlock: () -> Unit,
    onDismiss: () -> Unit
) = LiveRoomTheme(uiStyle = uiStyle, iconStyle = iconStyle) {
    var section by remember { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    val layoutPolicy = com.android.purebilibili.feature.video.ui.components.resolveDanmakuSettingsPanelLayoutPolicy(
        isFullscreen = isFullscreen,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    )
    com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanelHost(
        isFullscreen = isFullscreen,
        layoutPolicy = layoutPolicy,
        onDismiss = onDismiss,
        source = source,
        uiStyle = uiStyle,
    ) { requestDismiss ->
        com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanelFrame(
            layoutPolicy = layoutPolicy,
            containerColor = AppSurfaceTokens.surface(),
        ) {
            // 玻璃页在同一面板内进入和返回；原生材质保留分区栏。
            val glass = com.android.purebilibili.feature.video.ui.components.LocalPlayerGlassSettingsSurface.current
            val sectionScrollStates = List(3) { rememberScrollState() }
            com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanelBody(
                onDismiss = requestDismiss,
                title = if (glass) when (section) {
                    1 -> "弹幕高级设置"
                    2 -> "直播弹幕选项"
                    else -> "弹幕设置"
                } else "弹幕设置",
                onBack = if (glass && section != 0) ({ section = 0 }) else null,
                scrollState = sectionScrollStates[section],
                navigation = {
                    if (!glass) AppThemeAdaptiveTabRow(
                        options = listOf("基础", "样式", "直播").mapIndexed { index, label -> AppSegmentOption(index, label) },
                        selectedValue = section,
                        onSelectionChange = { section = it },
                        role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        miuixBackdrop = if (glass) com.android.purebilibili.core.ui.components.LocalAppGlassMenuBackdrop.current else null,
                        liquidGlassAdaptIndicatorToLabel = !glass,
                    )
                },
            ) {
                when (section) {
                    0 -> {
                        LiveSettingSwitchRow(
                            title = "弹幕显示",
                            checked = danmakuEnabled,
                            onCheckedChange = { onToggleDanmaku() }
                        )
                        LiveDanmakuAreaSelector(
                            currentArea = displayArea,
                            onAreaSelected = onDisplayAreaSelected
                        )
                        LiveDanmakuStyleSliderRow(
                            label = "普通字号",
                            valueText = { "${(it * 100).roundToInt()}%" },
                            value = normalFontScale,
                            valueRange = 0.5f..2.5f,
                            steps = 199,
                            defaultValue = DanmakuSettings().fontScale,
                            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.NormalFontScale, it) },
                            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.NormalFontScale, it) }
                        )
                        LiveDanmakuStyleSliderRow(
                            label = "全屏字号",
                            valueText = { "${(it * 100).roundToInt()}%" },
                            value = fullscreenFontScale,
                            valueRange = 0.5f..2.5f,
                            steps = 199,
                            defaultValue = DanmakuSettings().fontScale,
                            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.FullscreenFontScale, it) },
                            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.FullscreenFontScale, it) }
                        )
                        LiveDanmakuStyleSliderRow(
                            label = "不透明度",
                            valueText = { "${(it * 100).roundToInt()}%" },
                            value = opacity,
                            valueRange = 0f..1f,
                            steps = 99,
                            defaultValue = DanmakuSettings().opacity,
                            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.Opacity, it) },
                            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.Opacity, it) }
                        )
                        LiveDanmakuStyleSliderRow(
                            label = "滚动速度",
                            valueText = { "${(it * 100).roundToInt()}%" },
                            value = speed,
                            valueRange = 0.5f..3.0f,
                            steps = 249,
                            defaultValue = DanmakuSettings().speed,
                            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.Speed, it) },
                            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.Speed, it) }
                        )
                        AppText("按类型屏蔽", style = MaterialTheme.typography.labelLarge)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.android.purebilibili.core.ui.components.AppFilterChip(!allowScroll, onToggleAllowScroll, label = { AppText("滚动") })
                            com.android.purebilibili.core.ui.components.AppFilterChip(!allowTop, onToggleAllowTop, label = { AppText("顶部") })
                            com.android.purebilibili.core.ui.components.AppFilterChip(!allowBottom, onToggleAllowBottom, label = { AppText("底部") })
                            com.android.purebilibili.core.ui.components.AppFilterChip(!allowColorful, onToggleAllowColorful, label = { AppText("彩色") })
                        }
                    }
                    1 -> LiveDanmakuAdvancedSettings(
                        settings = advancedSettings,
                        scrollEnabled = false,
                        onStyleChanged = onStyleChanged,
                        onStylePreview = onStylePreview,
                        onMassiveModeChanged = onMassiveModeChanged,
                        onStaticToScrollChanged = onStaticToScrollChanged,
                        onFixedVelocityChanged = onFixedVelocityChanged,
                    )
                    else -> {
                        if (glass) com.android.purebilibili.feature.video.ui.components.DanmakuSettingsSectionTitle("醒目留言")
                        LiveSettingSwitchRow(
                            title = "显示醒目留言",
                            checked = superChatEnabled,
                            onCheckedChange = onSuperChatEnabledChange,
                        )
                        LiveSettingSwitchRow(
                            title = "聊天区显示 SC 页面",
                            checked = superChatPanelEnabled,
                            onCheckedChange = { onToggleSuperChatPanel() },
                        )
                        LiveSettingSwitchRow(
                            title = "显示 SC 横排和弹窗",
                            checked = superChatFlashEnabled,
                            onCheckedChange = { onToggleSuperChatFlash() }
                        )
                        LiveSettingSwitchRow(
                            title = "SC 卡片常驻显示",
                            checked = superChatPersistent,
                            onCheckedChange = { onToggleSuperChatPersistent() }
                        )
                        com.android.purebilibili.feature.live.components.LiveSuperChatPreview(
                            maxWidthDp = superChatMaxWidthDp,
                            opacity = superChatOpacity,
                            cardScale = superChatScale,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LiveDanmakuStyleSliderRow(
                            label = "SC 详情宽度",
                            valueText = { "${it.roundToInt()} dp" },
                            value = superChatMaxWidthDp.toFloat(),
                            valueRange = 260f..640f,
                            steps = 18,
                            defaultValue = 360f,
                            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.SuperChatWidth, it) },
                            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.SuperChatWidth, it) }
                        )
                        LiveDanmakuStyleSliderRow(
                            label = "SC 不透明度",
                            valueText = { "${(it * 100).roundToInt()}%" },
                            value = superChatOpacity,
                            valueRange = 0.1f..1f,
                            steps = 17,
                            defaultValue = 1f,
                            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.SuperChatOpacity, it) },
                            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.SuperChatOpacity, it) },
                        )
                        LiveDanmakuStyleSliderRow(
                            label = "SC 缩放",
                            valueText = { "${(it * 100).roundToInt()}%" },
                            value = superChatScale,
                            valueRange = 0.1f..1f,
                            steps = 17,
                            defaultValue = 1f,
                            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.SuperChatScale, it) },
                            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.SuperChatScale, it) },
                        )
                        AppTextButton(
                            enabled = superChatMaxWidthDp != 360 || superChatOpacity != 1f || superChatScale != 1f,
                            onClick = {
                                onStyleChanged(LiveDanmakuStyleOption.SuperChatWidth, 360f)
                                onStyleChanged(LiveDanmakuStyleOption.SuperChatOpacity, 1f)
                                onStyleChanged(LiveDanmakuStyleOption.SuperChatScale, 1f)
                            },
                        ) { AppText("恢复 SC 默认样式") }
                        if (glass) com.android.purebilibili.feature.video.ui.components.DanmakuSettingsSectionTitle("操作")
                        LiveSettingSwitchRow(
                            title = "键盘控制",
                            checked = keyboardControlsEnabled,
                            onCheckedChange = { onToggleKeyboardControls() }
                        )
                        if (keyboardControlsEnabled) {
                            AppText(
                                "Space/K 播放暂停 · F 全屏 · D 弹幕 · M 静音 · 方向键音量 · Q 点赞 · S 截图",
                                color = AppContentColors.secondaryText(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (glass && section == 0) {
                    com.android.purebilibili.feature.video.ui.components.DanmakuSettingsAction(
                        title = "高级设置", onClick = { section = 1 },
                    )
                    com.android.purebilibili.feature.video.ui.components.DanmakuSettingsAction(
                        title = "直播弹幕选项", onClick = { section = 2 },
                    )
                }
                com.android.purebilibili.feature.video.ui.components.DanmakuSettingsAction(
                    title = "屏蔽管理",
                    onClick = onOpenBlock,
                )
            }
        }
    }
}

@Composable
internal fun LiveDanmakuStyleSliderRow(
    label: String,
    valueText: (Float) -> String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    defaultValue: Float,
    onValuePreview: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.android.purebilibili.feature.video.ui.components.DanmakuSettingsSlider(
        label = label, valueText = valueText, value = value, valueRange = valueRange,
        steps = steps, defaultValue = defaultValue, onValuePreview = onValuePreview,
        onValueChangeFinished = onValueChangeFinished, modifier = modifier,
    )
}

@Composable
private fun LiveDanmakuAreaSelector(
    currentArea: Float,
    onAreaSelected: (Float) -> Unit
) {
    com.android.purebilibili.feature.video.ui.components.DanmakuSettingsSlider(
        label = "显示区域",
        value = currentArea,
        valueRange = 0.25f..1f,
        steps = 2,
        defaultValue = 0.5f,
        valueText = { "${(it * 100).roundToInt()}%" },
        onValuePreview = onAreaSelected,
        onValueChangeFinished = onAreaSelected,
    )
}

@Composable
private fun LiveSettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppSpacingTokens.TripleExtraLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(title, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LivePlayerInfoDialog(
    roomId: Long,
    roomTitle: String,
    currentQuality: String,
    videoFit: String,
    isAudioOnly: Boolean,
    isPlaying: Boolean,
    playUrl: String,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("播放信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                LiveInfoLine("房间", roomId.toString())
                LiveInfoLine("标题", roomTitle.ifBlank { "-" })
                LiveInfoLine("画质", currentQuality)
                LiveInfoLine("画面比例", videoFit)
                LiveInfoLine("播放模式", if (isAudioOnly) "仅音频" else "视频")
                LiveInfoLine("状态", if (isPlaying) "播放中" else "已暂停")
                LiveInfoLine("地址", playUrl.take(96).ifBlank { "-" })
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) { AppText("关闭") }
        }
    )
}

@Composable
private fun LiveInfoLine(
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.Top) {
        AppText(
            text = label,
            color = AppContentColors.secondaryText(),
            modifier = Modifier.width(
                AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraLarge
            )
        )
        AppText(
            text = value,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveShutdownTimerDialog(
    activeTargetMillis: Long?,
    selectedMinutes: Int?,
    exitApp: Boolean,
    onExitAppChange: (Boolean) -> Unit,
    onSetMinutes: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var customMinutes by rememberSaveable(selectedMinutes) { mutableIntStateOf(selectedMinutes ?: 30) }
    var editingCustomTime by rememberSaveable { mutableStateOf(false) }
    val remainingSeconds by produceState<Long?>(null, activeTargetMillis) {
        val target = activeTargetMillis
        if (target == null) {
            value = null
            return@produceState
        }
        while (true) {
            val remaining = (target - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            value = (remaining + 999L) / 1_000L
            if (remaining == 0L) break
            delay(minOf(1_000L, remaining))
        }
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("定时关闭") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            ) {
                remainingSeconds?.let { seconds ->
                    val minutes = seconds / 60
                    val secondsWithinMinute = (seconds % 60).toString().padStart(2, '0')
                    AppText("剩余 $minutes:$secondsWithinMinute", color = AppContentColors.accent())
                }
                resolvePlayerSleepTimerPresets(selectedMinutes).forEach { minutes ->
                    val selected = (selectedMinutes ?: 0) == minutes
                    AppSurface(
                        onClick = { onSetMinutes(minutes) },
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else AppSurfaceTokens.surfaceContainerHigh().copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
                    ) {
                        AppText(
                            text = if (minutes == 0) "禁用" else "${minutes}分钟",
                            modifier = Modifier.padding(
                                horizontal = AppSpacingTokens.Medium,
                                vertical = AppSpacingTokens.Medium,
                            ),
                        )
                    }
                }
                AppTextButton(onClick = { editingCustomTime = !editingCustomTime }) { AppText("自定义") }
                if (editingCustomTime) {
                    AppDurationPicker(
                        minutes = customMinutes,
                        onMinutesChange = { customMinutes = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppText("倒计时结束")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                    AppFilterChip(selected = !exitApp, onClick = { onExitAppChange(false) }, label = { AppText("暂停直播") })
                    AppFilterChip(selected = exitApp, onClick = { onExitAppChange(true) }, label = { AppText("退出应用") })
                }
            }
        },
        confirmButton = {
            if (editingCustomTime) {
                AppTextButton(enabled = customMinutes > 0, onClick = { onSetMinutes(customMinutes) }) {
                    AppText("应用")
                }
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("关闭") } },
    )
}
