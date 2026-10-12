package com.android.purebilibili.feature.live

import androidx.compose.ui.graphics.Color

import com.android.purebilibili.core.ui.components.AppDurationPicker
import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ForwardToInbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.resolveDanmakuSettingsScope
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.data.repository.LiveRedPocketInfo
import com.android.purebilibili.feature.live.components.LandscapeChatOverlay
import com.android.purebilibili.feature.live.components.LiveChatSection
import com.android.purebilibili.feature.live.components.LiveChatInputBar
import com.android.purebilibili.feature.video.ui.feedback.followActionAnchor
import com.android.purebilibili.feature.live.components.LiveContributionRankSheet
import com.android.purebilibili.feature.live.components.LiveDmBlockSheet
import com.android.purebilibili.feature.live.components.LiveEmoticonPanel
import com.android.purebilibili.feature.live.components.LivePlayerControls
import com.android.purebilibili.feature.live.components.LivePortraitBottomBar
import com.android.purebilibili.feature.live.components.LivePortraitChatPreview
import com.android.purebilibili.feature.live.components.LivePortraitChatStream
import com.android.purebilibili.feature.live.components.LivePortraitMoreSheet
import com.android.purebilibili.feature.live.components.LiveReportDialog
import com.android.purebilibili.feature.live.components.LiveSendDanmakuSheet
import com.android.purebilibili.feature.live.components.LiveStreamSourceSheet
import com.android.purebilibili.feature.live.components.LiveSuperChatSection
import com.android.purebilibili.feature.live.components.LiveVotePanel
import com.android.purebilibili.feature.live.components.LiveSuperChatFlashOverlay
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
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.rememberAppProfileAddIcon
import com.android.purebilibili.core.ui.rememberAppAnalyticsIcon
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
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
import kotlin.math.roundToInt
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
    siteId: String = "bilibili"
) {
    val bilibiliRoomId = roomId.toLongOrNull() ?: 0L
    val context = LocalContext.current
    val activity = context as? Activity
    // 📺 [新增] 获取小窗管理器
    val miniPlayerManager = remember { com.android.purebilibili.feature.video.player.MiniPlayerManager.getInstance(context) }
    val configuration = LocalConfiguration.current
    val windowSizeClass = LocalWindowSizeClass.current
    val hingeAdaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val displayContext = hingeAdaptiveInfo.displayContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val liveKeyboardFocusRequester = remember { FocusRequester() }
    
    // Shared Element Transition Scopes
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = rememberLiveChromePalette()
    val roomColorTokens = resolveLiveBiliPaiRoomColorTokens()
    
    // 状态
    var showQualityMenu by remember { mutableStateOf(false) }
    var showVideoFitMenu by remember { mutableStateOf(false) }
    var showDanmakuSettingsDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showPlayerInfoDialog by remember { mutableStateOf(false) }
    var showShutdownTimerDialog by remember { mutableStateOf(false) }
    var showContributionRankSheet by remember { mutableStateOf(false) }
    var showSendDanmakuSheet by remember { mutableStateOf(false) }
    var showEmoticonSheet by remember { mutableStateOf(false) }
    var sendColor by rememberSaveable(roomId, siteId) { mutableIntStateOf(16777215) }
    var sendMode by rememberSaveable(roomId, siteId) { mutableIntStateOf(1) }
    var danmakuDraft by rememberSaveable(roomId, siteId) { mutableStateOf("") }
    var showShareMessageSheet by remember { mutableStateOf(false) }
    var showStreamSourceSheet by remember { mutableStateOf(false) }
    var showPortraitMoreSheet by remember(roomId, siteId) { mutableStateOf(false) }
    var showPortraitInteractionSheet by remember(roomId, siteId) { mutableStateOf(false) }
    var isPortraitClearScreen by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
    var showPortraitPlayerControls by rememberSaveable(roomId, siteId) { mutableStateOf(true) }
    var isPortraitChatVisible by rememberSaveable(roomId, siteId) { mutableStateOf(true) }
    var reportTarget by remember { mutableStateOf<LiveDanmakuItem?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isInteractionPanelVisible by remember {
        mutableStateOf(defaultLiveInteractionPanelVisible())
    }
    var selectedInteractionTab by remember { mutableIntStateOf(0) }
    var isPipRequested by remember { mutableStateOf(false) }
    var wasPlaybackActiveBeforePause by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var trackSelectionBeforeAudioOnly by remember { mutableStateOf<TrackSelectionParameters?>(null) }
    var videoScale by rememberSaveable(roomId, siteId) { mutableFloatStateOf(1f) }
    var showVoteSheet by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    var backgroundPlaybackEnabled by remember {
        mutableStateOf(SettingsManager.getBackgroundPlaybackEnabledSync(context))
    }
    val autoEnterFullscreenEnabled by SettingsManager.getAutoEnterFullscreen(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val autoExitFullscreenEnabled by SettingsManager.getAutoExitFullscreen(context)
        .collectAsStateWithLifecycle(initialValue = true)
    var didAutoEnterFullscreen by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
    var shutdownAtMillis by remember { mutableStateOf<Long?>(null) }
    val showLivePipButton = remember { shouldShowLivePipButton(android.os.Build.VERSION.SDK_INT) }
    val successState = uiState as? LivePlayerState.Success
    val isLiveAudioOnly = successState?.isAudioOnly == true
    val superChatItems by viewModel.superChatItems.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val emoticonPackages by viewModel.emoticonPackages.collectAsStateWithLifecycle()
    val shieldInfo by viewModel.shieldInfo.collectAsStateWithLifecycle()
    val voteSnapshot by viewModel.voteSnapshot.collectAsStateWithLifecycle()
    val roomInfo = successState?.roomInfo ?: RoomInfo()
    val anchorInfo = successState?.anchorInfo ?: AnchorInfo()
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
    val currentSourceDesc = remember(successState?.playUrl) {
        val candidates = viewModel.playbackCandidatesSnapshot()
        val (candidateIndex, urlIndex) = viewModel.currentPlaybackPosition()
        val candidate = candidates.getOrNull(candidateIndex)
        if (candidate != null) {
            buildString {
                append(com.android.purebilibili.feature.live.components.resolveLiveStreamProtocolLabel(candidate.protocolName))
                append(" · ")
                append(candidate.codecName.uppercase().ifBlank { "?" })
                append(" · ")
                append(urlIndex + 1)
            }
        } else {
            ""
        }
    }
    
    // Haze blur 状态 (用于侧边栏实时模糊)
    val hazeState = com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState()
    
    // 直播布局只看当前可用窗口，分屏/自由窗口不会沿用物理平板布局。
    val isTablet = windowSizeClass.isTablet
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val liveLayoutMode = resolveLiveRoomLayoutMode(
        isLandscape = isLandscape,
        isTablet = isTablet,
        isFullscreen = isFullscreen,
        isPortraitLive = isPortraitLive,
        hasObstructingHinge = hingeAdaptiveInfo.shouldAvoidHinge,
    )
    val portraitPresentation = resolveLivePortraitPresentation(
        layoutMode = liveLayoutMode,
        clearScreen = isPortraitClearScreen,
        chatVisible = isPortraitChatVisible,
        controlsVisible = showPortraitPlayerControls,
        isFullscreen = isFullscreen,
    )
    val playerGesturePolicy = resolveLivePlayerGesturePolicy(liveLayoutMode)
    val reduceMotion = rememberSystemReduceMotion()
    val visibleInteractionOverlay = if (portraitPresentation.usePortraitControls) {
        portraitPresentation.showChatPreview
    } else {
        isInteractionPanelVisible
    }
    val liveDanmakuSettingsScope = remember(isLandscape) {
        resolveDanmakuSettingsScope(isLandscape = isLandscape)
    }
    val liveDanmakuSettings by SettingsManager
        .getDanmakuSettings(context, liveDanmakuSettingsScope)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val liveDanmakuDisplayArea = liveDanmakuSettings.displayArea
    val liveSuperChatPanelEnabled by SettingsManager.getLiveSuperChatPanelEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val liveSuperChatFlashEnabled by SettingsManager
        .getLiveSuperChatFlashEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val liveSuperChatPersistent by SettingsManager
        .getLiveSuperChatPersistent(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val liveSuperChatMaxWidthDp by SettingsManager
        .getLiveSuperChatMaxWidthDp(context)
        .collectAsStateWithLifecycle(initialValue = 360)
    val livePortraitExpandEnabled by SettingsManager
        .getLivePortraitExpandEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val liveKeyboardControlsEnabled by SettingsManager
        .getLiveKeyboardControlsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(liveKeyboardControlsEnabled, liveLayoutMode) {
        if (liveKeyboardControlsEnabled) liveKeyboardFocusRequester.requestFocus()
    }
    val liveDanmakuEnabledPreference by SettingsManager
        .getLiveDanmakuEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val pipNoDanmaku by SettingsManager
        .getPipNoDanmakuEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val portraitOverlayMetrics = remember(configuration.screenHeightDp) {
        resolveLivePortraitOverlayMetrics(configuration.screenHeightDp)
    }
    val portraitOverlayPanelHeightDp = remember(configuration.screenHeightDp, portraitOverlayMetrics) {
        resolveLivePortraitOverlayPanelHeightDp(
            screenHeightDp = configuration.screenHeightDp,
            metrics = portraitOverlayMetrics
        )
    }
    val overlayContentInsets = remember(
        liveLayoutMode,
        portraitOverlayPanelHeightDp,
        portraitOverlayMetrics,
        visibleInteractionOverlay
    ) {
        resolveLiveOverlayContentInsets(
            layoutMode = liveLayoutMode,
            portraitPanelHeightDp = portraitOverlayPanelHeightDp,
            portraitMetrics = portraitOverlayMetrics,
            isInteractionPanelVisible = visibleInteractionOverlay
        )
    }
    val reservedBottomOverlayDp = if (shouldReserveLivePortraitInteractionPanel(
            layoutMode = liveLayoutMode,
            isInteractionPanelVisible = visibleInteractionOverlay
        )
    ) {
        portraitOverlayPanelHeightDp
    } else {
        0
    }
    val showChatToggle = remember(liveLayoutMode) {
        shouldShowLiveChatToggle(liveLayoutMode)
    }
    val showSplitChatPanel = remember(liveLayoutMode, isInteractionPanelVisible) {
        shouldShowLiveSplitChatPanel(
            layoutMode = liveLayoutMode,
            isInteractionPanelVisible = isInteractionPanelVisible
        )
    }
    val splitChatPanelWidthDp = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        resolveLiveSplitChatPanelWidthDp(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            contentPaddingDp = AppSpacingTokens.Medium.value.toInt() * 2,
        )
    }

    val useTextureSurfaceForLivePlayer = remember(sharedTransitionScope, animatedVisibilityScope) {
        shouldUseTextureSurfaceForLivePlayer(
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null
        )
    }
    val liveSubtitle = remember(roomInfo, anchorInfo) {
        listOf(
            roomInfo.watchedText,
            formatLiveDuration(roomInfo.liveStartTime)
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }
    
    // 强制横屏切换
    fun toggleFullscreen() {
        showPortraitMoreSheet = false
        showPortraitInteractionSheet = false
        showPortraitPlayerControls = true
        isFullscreen = !isFullscreen
    }

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
        miniPlayerManager.markLeavingByNavigation(forceStop = true)
        onBack()
    }

    fun captureLiveScreenshot() {
        val playerView = playerViewRef
        if (playerView == null) {
            Toast.makeText(context, "截图失败：播放器未就绪", Toast.LENGTH_SHORT).show()
        } else {
            coroutineScope.launch {
                val success = com.android.purebilibili.feature.video.util.captureAndSaveVideoScreenshot(
                    context = context,
                    playerView = playerView,
                    videoWidth = 0,
                    videoHeight = 0,
                    videoTitle = liveRoomTitle,
                )
                Toast.makeText(
                    context,
                    if (success) "截图已保存到相册（PNG）" else "截图失败，请稍后重试",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun copyLiveUrl() {
        val liveUrl = "https://live.bilibili.com/$roomId"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("直播间链接", liveUrl))
        Toast.makeText(context, "已复制直播间链接", Toast.LENGTH_SHORT).show()
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
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(liveUrl)))
    }

    fun openRedPocket(info: LiveRedPocketInfo) {
        val url = info.h5Url
        if (url.isBlank()) {
            Toast.makeText(context, "红包入口暂不可用", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun shareLiveToMessage() {
        showShareMessageSheet = true
    }

    fun toggleBackgroundPlayback() {
        val newValue = !backgroundPlaybackEnabled
        backgroundPlaybackEnabled = newValue
        coroutineScope.launch {
            SettingsManager.setBackgroundPlaybackEnabled(context, newValue)
        }
        Toast.makeText(
            context,
            if (newValue) "已开启后台播放" else "已关闭后台播放",
            Toast.LENGTH_SHORT
        ).show()
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
            Toast.makeText(context, "已加入屏蔽词", Toast.LENGTH_SHORT).show()
        }
    }

    fun enterLivePip() {
        if (!showLivePipButton) return
        val hostActivity = activity ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            hostActivity.isInPictureInPictureMode
        ) {
            return
        }
        try {
            isPipRequested = true
            val paramsBuilder = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                paramsBuilder.setSeamlessResizeEnabled(true)
            }
            hostActivity.enterPictureInPictureMode(paramsBuilder.build())
        } catch (e: Exception) {
            isPipRequested = false
            Logger.e(TAG, "Enter live PiP failed", e)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LivePlayerEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is LivePlayerEvent.DanmakuSent -> {
                    if (danmakuDraft.trim() == event.message) {
                        danmakuDraft = ""
                        showSendDanmakuSheet = false
                    }
                }
                LivePlayerEvent.EmoticonSent -> {
                    showEmoticonSheet = false
                }
            }
        }
    }
    
    // 清屏时先恢复入口，普通竖屏仍保留系统预测性返回。
    LocalNavigationBackHandler(enabled = isFullscreen || portraitPresentation.clearScreen) {
        if (portraitPresentation.clearScreen) isPortraitClearScreen = false
        else if (isFullscreen) toggleFullscreen()
    }

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
                    .setEnableDecoderFallback(true)
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply { playWhenReady = true }
    }

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

    LaunchedEffect(shutdownAtMillis) {
        val target = shutdownAtMillis ?: return@LaunchedEffect
        val delayMillis = (target - System.currentTimeMillis()).coerceAtLeast(0L)
        delay(delayMillis)
        if (shutdownAtMillis == target) {
            exoPlayer.pause()
            shutdownAtMillis = null
            Toast.makeText(context, "定时关闭已执行", Toast.LENGTH_SHORT).show()
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
    
    // ... (播放监听与 URL 管理保持不变)
    
    // 播放状态监听
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
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
                        exoPlayer.play()
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
                        if (!isFullscreen) toggleFullscreen()
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
                    isFullscreen
                ) {
                    toggleFullscreen()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    
    // 播放 URL 管理
    LaunchedEffect(siteId, roomId) {
        val savedQuality = if (com.android.purebilibili.core.util.NetworkUtils.isWifi(context)) {
            SettingsManager.getWifiQualitySync(context)
        } else {
            SettingsManager.getMobileQualitySync(context)
        }
        viewModel.loadLiveStream(
            bilibiliRoomId,
            qn = resolveLiveDefaultQualityQn(savedQuality),
        )
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
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val isInPictureInPictureMode =
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                            (activity?.isInPictureInPictureMode == true)
                    val isBackgroundAudioEnabled = miniPlayerManager.shouldContinueBackgroundAudio()
                    val hasRecentUserLeaveHint = miniPlayerManager.hasRecentUserLeaveHint()
                    wasPlaybackActiveBeforePause = isPlaybackActiveForLifecycle(
                        isPlaying = exoPlayer.isPlaying,
                        playWhenReady = exoPlayer.playWhenReady,
                        playbackState = exoPlayer.playbackState
                    )
                    val shouldKeepPlayingInBackground = shouldContinuePlaybackDuringPause(
                        isMiniMode = miniPlayerManager.isMiniMode,
                        isPip = isInPictureInPictureMode || isPipRequested,
                        isBackgroundAudio = isBackgroundAudioEnabled,
                        wasPlaybackActive = wasPlaybackActiveBeforePause
                    )
                    val shouldKeepBackgroundAudioByFallback =
                        isBackgroundAudioEnabled &&
                            wasPlaybackActiveBeforePause
                    val shouldPausePlayback = shouldPauseLivePlaybackOnPause(
                        isInPictureInPictureMode = isInPictureInPictureMode,
                        isPipRequested = isPipRequested,
                        shouldKeepPlayingInBackground =
                            shouldKeepPlayingInBackground || shouldKeepBackgroundAudioByFallback
                    )
                    Logger.d(
                        TAG,
                        "ON_PAUSE live policy: pip=$isInPictureInPictureMode, pipRequested=$isPipRequested, " +
                            "bgAudio=$isBackgroundAudioEnabled, leaveHint=$hasRecentUserLeaveHint, " +
                            "wasActive=$wasPlaybackActiveBeforePause, keepByPolicy=$shouldKeepPlayingInBackground, " +
                            "keepByFallback=$shouldKeepBackgroundAudioByFallback, shouldPause=$shouldPausePlayback"
                    )
                    if (shouldPausePlayback) {
                        exoPlayer.pause()
                        viewModel.setLivePlaybackRequested(false)
                        CrashReporter.markLivePlaybackStage("lifecycle_pause")
                    } else {
                        CrashReporter.markLivePlaybackStage("lifecycle_pause_keep_playing")
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isPipRequested = false
                    val shouldResumePlayback = shouldResumeAfterLifecyclePause(
                        wasPlaybackActive = wasPlaybackActiveBeforePause,
                        isPlaying = exoPlayer.isPlaying,
                        playWhenReady = exoPlayer.playWhenReady,
                        playbackState = exoPlayer.playbackState
                    )
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
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerViewRef = null
            // 📺 [修改] 仅当 MiniPlayerManager 未持有该播放器时才释放
            if (!miniPlayerManager.isPlayerManaged(exoPlayer)) {
                exoPlayer.release()
                Logger.d(TAG, "📺 播放器未被小窗持有，释放")
            } else {
                Logger.d(TAG, "📺 播放器被小窗持有，保留")
            }
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.applyPlayerRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            viewModel.pauseLiveHeartbeat()
        }
    }
    
    // 横屏时隐藏系统栏
    LaunchedEffect(isLandscape, liveLayoutMode) {
        val window = activity?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
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
        onDispose {
            val exitWindow = activity?.window ?: return@onDispose
            val exitController = WindowCompat.getInsetsController(exitWindow, exitWindow.decorView)
            exitController.show(WindowInsetsCompat.Type.systemBars())
            exitController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    val liveRequestedOrientationMode = remember(displayContext, isFullscreen, isPortraitLive) {
        resolveLiveRequestedOrientationMode(
            displayContext = displayContext,
            isFullscreen = isFullscreen,
            isPortraitLive = isPortraitLive,
        )
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
    LaunchedEffect(activity, displayContext, liveRequestedOrientationMode) {
        val requestedOrientation = when (liveRequestedOrientationMode) {
            LiveRequestedOrientationMode.Unspecified ->
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            LiveRequestedOrientationMode.SensorLandscape ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            LiveRequestedOrientationMode.SensorPortrait ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            LiveRequestedOrientationMode.Portrait ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        activity?.applyPlayerRequestedOrientation(
            requestedOrientation = requestedOrientation,
            displayContext = displayContext,
        )
    }

    // 布局结构
    val playerContent = @Composable {
        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(roomColorTokens.baseBackgroundColor)
                    .hazeSourceCompat(state = hazeState)
                .then(
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = com.android.purebilibili.core.ui.transition.liveCoverSharedElementKey(bilibiliRoomId)),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else Modifier
                )
        ) {
            // Video View
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                contentAlignment = if (liveLayoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay && !isFullscreen) Alignment.TopCenter else Alignment.Center
            ) {
                val density = LocalDensity.current
                val viewportAspectRatio = resolveLiveViewportAspectRatio(
                    selected = videoAspectRatio,
                    usePortraitControls = false,
                    portraitExpandEnabled = livePortraitExpandEnabled,
                )
                val viewportLayout = remember(maxWidth, maxHeight, viewportAspectRatio) {
                    with(density) {
                        resolveVideoViewportLayout(
                            containerWidth = maxWidth.roundToPx(),
                            containerHeight = maxHeight.roundToPx(),
                            aspectRatio = viewportAspectRatio
                        )
                    }
                }
                val viewportModifier = with(density) {
                    Modifier.graphicsLayer(scaleX = videoScale, scaleY = videoScale).size(
                        width = viewportLayout.width.toDp(),
                        height = viewportLayout.height.toDp()
                    )
                }

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
                            resizeMode = viewportAspectRatio.playerResizeMode
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    update = { playerView ->
                        playerView.player = if (shouldBindLivePlayerViewForAudioOnly(isLiveAudioOnly)) exoPlayer else null
                        if (playerView.resizeMode != viewportAspectRatio.playerResizeMode) {
                            playerView.resizeMode = viewportAspectRatio.playerResizeMode
                        }
                        playerViewRef = playerView
                    },
                    modifier = viewportModifier
                )
            }
            
            // Danmaku Overlay (Only render if enabled)
            val successState = uiState as? LivePlayerState.Success
            val showLiveDanmakuOverlay =
                portraitPresentation.showMediaOverlays &&
                (liveLayoutMode != LiveRoomLayoutMode.PortraitVerticalOverlay || isFullscreen) &&
                !(isPipRequested && pipNoDanmaku) &&
                shouldRenderLiveDanmakuOverlayForAudioOnly(
                    isDanmakuEnabled = successState?.isDanmakuEnabled == true,
                    isAudioOnly = isLiveAudioOnly
                )
            AnimatedVisibility(
                visible = showLiveDanmakuOverlay,
                enter = fadeIn(animationSpec = AppMotionTokens.standardSpec()),
                exit = fadeOut(animationSpec = AppMotionTokens.standardSpec()),
                modifier = Modifier.fillMaxSize(),
            ) {
                LiveDanmakuOverlay(
                    danmakuFlow = viewModel.danmakuFlow,
                    isFullscreen = isFullscreen,
                    displayArea = liveDanmakuDisplayArea,
                    danmakuSettings = liveDanmakuSettings,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = overlayContentInsets.topDp.dp,
                            bottom = overlayContentInsets.bottomDp.dp
                        )
                )
            }
            
            // Custom Controls
            LivePlayerControls(
                isPlaying = exoPlayer.playWhenReady,
                isFullscreen = isFullscreen,
                gesturePolicy = playerGesturePolicy,
                usePortraitControls = false,
                isClearScreen = portraitPresentation.clearScreen,
                onPortraitTap = { showPortraitPlayerControls = !showPortraitPlayerControls },
                onOpenPortraitMore = {
                    showPortraitPlayerControls = true
                    showPortraitMoreSheet = true
                },
                showTopBar = shouldShowLivePlayerControlsTopBar(
                    layoutMode = liveLayoutMode,
                    isFullscreen = isFullscreen
                ),
                title = liveRoomTitle,
                subtitle = liveSubtitle,
                onPlayPause = {
                    if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
                },
                onToggleFullscreen = { toggleFullscreen() },
                onBack = { if (isFullscreen) toggleFullscreen() else exitLiveRoom() },
                // 侧边栏开关
                isChatVisible = isInteractionPanelVisible,
                onToggleChat = {
                    if (isFullscreen) showPortraitInteractionSheet = true
                    else isInteractionPanelVisible = !isInteractionPanelVisible
                },
                showChatToggle = showChatToggle,
                // 弹幕开关
                isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
                onToggleDanmaku = { toggleLiveDanmaku() },
                onOpenDanmakuSettings = { showDanmakuSettingsDialog = true },
                onOpenBlockSettings = { showBlockDialog = true },
                // [新增] 刷新
                onRefresh = { viewModel.retry() },
                isAudioOnly = successState?.isAudioOnly ?: false,
                onToggleAudioOnly = { viewModel.toggleAudioOnly() },
                isBackgroundPlaybackEnabled = backgroundPlaybackEnabled,
                onToggleBackgroundPlayback = { toggleBackgroundPlayback() },
                onOpenShutdownTimer = { showShutdownTimerDialog = true },
                onOpenPlayerInfo = { showPlayerInfoDialog = true },
                onOpenSend = { showSendDanmakuSheet = true },
                videoFitDesc = videoAspectRatio.displayName,
                onVideoFitClick = { showVideoFitMenu = true },
                currentQualityDesc = currentQualityDesc,
                onQualityClick = { showQualityMenu = true },
                currentSourceDesc = currentSourceDesc,
                onSourceClick = { showStreamSourceSheet = true },
                onOpenMore = { showPortraitMoreSheet = true },
                videoScale = videoScale,
                onScale = { zoom -> videoScale = (videoScale * zoom).coerceIn(0.5f, 3f) },
                onResetScale = { videoScale = 1f },
                showPipButton = showLivePipButton,
                onEnterPip = { enterLivePip() },
                showLockButton = isFullscreen,
                onCaptureScreenshot = { captureLiveScreenshot() },
                onLike = { count -> viewModel.clickLike(count) },
                applyTopSystemBarPadding = shouldApplyLiveTopControlSystemInsets(
                    layoutMode = liveLayoutMode,
                    isFullscreen = isFullscreen
                ),
                applyBottomSystemBarPadding = shouldApplyLiveBottomControlSystemInsets(
                    layoutMode = liveLayoutMode,
                    isFullscreen = isFullscreen,
                    hasReservedBottomOverlay = reservedBottomOverlayDp > 0
                ),
                bottomControlsBottomPadding = if (reservedBottomOverlayDp > 0) {
                    (reservedBottomOverlayDp + portraitOverlayMetrics.playerControlsGapDp).dp
                } else {
                    AppSpacingTokens.None
                }
            )

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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AdaptiveLoadingIndicator() }
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
        LiveChatSection(
            messages = chatHistory,
            onOpenSend = { showSendDanmakuSheet = true },
            headerTitle = "实时互动",
            supportingText = "发送弹幕和主播互动",
            isOverlay = isOverlay,
            showHeader = showHeader,
            isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
            onToggleDanmaku = { toggleLiveDanmaku() },
            onLike = { count -> viewModel.clickLike(count) },
            onOpenEmote = {
                showEmoticonSheet = true
                showSendDanmakuSheet = true
            },
            onUserClick = onUserClick,
            onAtUser = { item ->
                viewModel.setReplyTarget(item)
                showSendDanmakuSheet = true
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
            showInputBar = false,
            modifier = Modifier.fillMaxSize()
        )
    }

    val interactionInput: @Composable (Boolean) -> Unit = { isOverlay ->
        LiveChatInputBar(
            isOverlay = isOverlay,
            isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
            onToggleDanmaku = { toggleLiveDanmaku() },
            onLike = { count -> viewModel.clickLike(count) },
            onOpenEmote = { showEmoticonSheet = true
                showSendDanmakuSheet = true },
            onOpenSend = { showSendDanmakuSheet = true },
            selectedPage = selectedInteractionTab,
            showPageIndicator = liveSuperChatPanelEnabled,
        )
    }

    val interactionContent: @Composable (Boolean, Boolean) -> Unit = { isOverlay, includeFooter ->
        LivePrimaryInteractionPanel(
            isOverlay = isOverlay,
            selectedTab = selectedInteractionTab,
            showSuperChat = liveSuperChatPanelEnabled,
            onSelectedTab = { selectedInteractionTab = it },
            chatContent = { chatContent(isOverlay, false) },
            persistentFooter = if (includeFooter) { { interactionInput(isOverlay) } } else null,
            superChatContent = {
                LiveSuperChatSection(
                    items = superChatItems,
                    modifier = Modifier.fillMaxSize(),
                    onExpired = { item ->
                        if (item.superChatId > 0L) {
                            viewModel.dismissSuperChat(item.superChatId)
                        }
                    },
                    onUserClick = onUserClick,
                    onReport = { item -> reportTarget = item },
                )
            },
            voteContent = { LiveVotePanel(voteSnapshot, Modifier.fillMaxSize()) }
        )
    }

    val keyboardModifier = if (liveKeyboardControlsEnabled) {
        Modifier
            .focusRequester(liveKeyboardFocusRequester)
            .focusGroup()
            .onKeyEvent { event ->
                val action = resolvePlayerKeyAction(
                    event = event,
                    isInPipMode = isPipRequested || activity?.isInPictureInPictureMode == true,
                    isTextInputActive = showSendDanmakuSheet || showShareMessageSheet,
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
        LivePortraitOverlayAppBar(
            roomTitle = liveRoomTitle,
            anchorInfo = anchorInfo,
            subtitle = liveSubtitle,
            onBack = { exitLiveRoom() },
            onUserClick = onUserClick,
            onCopyLink = { copyLiveUrl() },
            onShare = { shareLiveUrl() },
            onShareToMessage = { shareLiveToMessage() },
            onOpenBrowser = { openLiveUrl() },
            isFollowing = successState?.isFollowing ?: false,
            currentQualityDesc = currentQualityDesc,
            onFollowClick = { viewModel.toggleFollow() },
            onQualityClick = { showQualityMenu = true },
            onlineRankText = roomInfo.onlineRankText,
            onOpenRank = { showContributionRankSheet = true },
            onOpenSend = { showSendDanmakuSheet = true },
            onOpenBlock = { showBlockDialog = true },
            redPocketInfo = successState?.redPocketInfo,
            onRedPocketClick = {
                successState?.redPocketInfo?.let { openRedPocket(it) }
            }
        )
    }

    // 统一容器：SC 全屏浮层需要盖住四种布局的播放器/弹幕层
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(keyboardModifier)

    ) {
    if (hingeAdaptiveInfo.shouldAvoidHinge) {
        com.android.purebilibili.core.ui.adaptive.AppHingePaneLayout(
            modifier = Modifier.fillMaxSize().background(roomColorTokens.baseBackgroundColor),
            primaryContent = {
                Column(Modifier.fillMaxSize()) {
                    if (!isFullscreen) roomAppBar()
                    Box(Modifier.weight(1f).fillMaxWidth()) { playerContent() }
                }
            },
            secondaryContent = if (isInteractionPanelVisible) {
                { LiveLandscapeChatPanel(Modifier.fillMaxSize()) { interactionContent(false, true) } }
            } else null,
        )
    } else when (liveLayoutMode) {
        LiveRoomLayoutMode.LandscapeSplit -> {
            Box(Modifier.fillMaxSize().background(roomColorTokens.baseBackgroundColor)) {
                Column(Modifier.fillMaxSize()) {
                    roomAppBar()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacingTokens.Medium)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(
                                    bottom = AppSpacingTokens.Medium,
                                    end = if (showSplitChatPanel) {
                                        AppSpacingTokens.Small
                                    } else {
                                        AppSpacingTokens.None
                                    }
                                )
                        ) {
                            playerContent()
                        }
                        if (showSplitChatPanel) {
                            LiveLandscapeChatPanel(
                                modifier = Modifier
                                    .width(splitChatPanelWidthDp.dp)
                                    .fillMaxHeight()
                                    .padding(bottom = AppSpacingTokens.Medium)
                            ) {
                                interactionContent(false, true)
                            }
                        }
                    }
                }
            }
        }
        LiveRoomLayoutMode.LandscapeOverlay -> {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(roomColorTokens.baseBackgroundColor)
            ) {
                playerContent()
                successState?.redPocketInfo?.let { redPocket ->
                    LiveRedPocketChip(
                        info = redPocket,
                        onClick = { openRedPocket(redPocket) },
                        compact = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = portraitOverlayMetrics.topChromeReserveDp.dp,
                                end = AppSpacingTokens.Large
                            )
                            .widthIn(max = (configuration.screenWidthDp / 2).dp)
                    )
                }
            }
        }

        LiveRoomLayoutMode.PortraitVerticalOverlay -> {
            Box(Modifier.fillMaxSize()) {
                LiveRoomBackdrop(imageUrl = liveCoverForUi, modifier = Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize()) {
                    if (portraitPresentation.showChrome) roomAppBar()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Box(
                            Modifier.fillMaxSize().padding(bottom = if (portraitPresentation.showChatPreview) LIVE_INPUT_HEIGHT_DP.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp),
                        ) { playerContent() }
                        if (portraitPresentation.showChatPreview) {
                            Box(
                                Modifier.align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(bottom = (LIVE_INPUT_HEIGHT_DP + LIVE_CHAT_INPUT_GAP_DP).dp)
                                    .height(portraitOverlayPanelHeightDp.dp),
                            ) { interactionContent(true, false) }
                            // Footer owns its navigation inset; the player and chat reserve the same height.
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                                interactionInput(true)
                            }
                        }
                        if (portraitPresentation.clearScreen) {
                            AppTextButton(
                                onClick = { isPortraitClearScreen = false },
                                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding(),
                            ) { AppText("退出清屏", color = LiveStatusPalette.MediaContent) }
                        }
                    }
                }
            }
        }
        LiveRoomLayoutMode.PortraitPanel -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(roomColorTokens.baseBackgroundColor)
            ) {
                LiveRoomBackdrop(
                    imageUrl = liveCoverForUi,
                    modifier = Modifier.fillMaxSize()
                )
                Column(Modifier.fillMaxSize()) {
                    LivePortraitOverlayAppBar(
                        roomTitle = liveRoomTitle,
                        anchorInfo = anchorInfo,
                        subtitle = liveSubtitle,
                        onBack = { exitLiveRoom() },
                        onUserClick = onUserClick,
                        onCopyLink = { copyLiveUrl() },
                        onShare = { shareLiveUrl() },
                        onShareToMessage = { shareLiveToMessage() },
                        onOpenBrowser = { openLiveUrl() },
                        isFollowing = successState?.isFollowing ?: false,
                        currentQualityDesc = currentQualityDesc,
                        onFollowClick = { viewModel.toggleFollow() },
                        onQualityClick = { showQualityMenu = true },
                        onlineRankText = roomInfo.onlineRankText,
            onOpenRank = { showContributionRankSheet = true },
                        onOpenSend = { showSendDanmakuSheet = true },
                        onOpenBlock = { showBlockDialog = true },
                        redPocketInfo = successState?.redPocketInfo,
                        onRedPocketClick = {
                            successState?.redPocketInfo?.let { openRedPocket(it) }
                        }
                    )
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                        playerContent()
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        interactionContent(true, true)
                    }
                }
            }
        }
    }

    // SC 左下角非侵入式悬浮卡片（不遮挡中央视频画面，仅响应实时新 SC，带倒计时与独立关闭）
    // 跟随弹幕开关显示，并可在弹幕设置中独立关闭
    if (isFullscreen && shouldShowLiveSuperChatFlash(
            showMediaOverlays = portraitPresentation.showMediaOverlays,
            isDanmakuEnabled = successState?.isDanmakuEnabled == true,
            flashEnabled = liveSuperChatFlashEnabled
        )
    ) {
        LiveSuperChatFlashOverlay(
            flashFlow = viewModel.superChatFlashFlow,
            onUserClick = onUserClick,
            persistUntilDismiss = liveSuperChatPersistent,
            maxWidthDp = liveSuperChatMaxWidthDp,
            modifier = Modifier.fillMaxSize()
        )
    }
    }

    if (showPortraitMoreSheet) {
        LivePortraitMoreSheet(
            onDismiss = { showPortraitMoreSheet = false },
            actions = buildList {
                if (portraitPresentation.usePortraitControls) {
                    add(AppWindowAction("清屏", onClick = { isPortraitClearScreen = true }))
                }
                add(AppWindowAction("投票", onClick = { showVoteSheet = true }))
                add(AppWindowAction("完整聊天 / SC", onClick = {
                    selectedInteractionTab = 0
                    showPortraitInteractionSheet = true
                }))
                add(AppWindowAction("画质：$currentQualityDesc", onClick = { showQualityMenu = true }))
                add(AppWindowAction("线路：${currentSourceDesc.ifBlank { "自动" }}", onClick = {
                    showStreamSourceSheet = true
                }))
                add(AppWindowAction("画面比例：${videoAspectRatio.displayName}", onClick = {
                    showVideoFitMenu = true
                }))
                add(AppWindowAction(if (exoPlayer.playWhenReady) "暂停播放" else "继续播放", onClick = {
                    if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
                }))
                add(AppWindowAction(
                    if (isFullscreen) "退出全屏" else "全屏观看",
                    onClick = { toggleFullscreen() },
                ))
                if (showLivePipButton) {
                    add(AppWindowAction("画中画", onClick = { enterLivePip() }))
                }
                add(AppWindowAction(
                    if (isLiveAudioOnly) "恢复视频画面" else "仅听声音",
                    onClick = { viewModel.toggleAudioOnly() },
                ))
                add(AppWindowAction(
                    if (backgroundPlaybackEnabled) "关闭后台播放" else "开启后台播放",
                    onClick = { toggleBackgroundPlayback() },
                ))
                add(AppWindowAction("定时关闭", onClick = { showShutdownTimerDialog = true }))
                add(AppWindowAction(
                    if (successState?.isDanmakuEnabled == true) "关闭滚动弹幕" else "开启滚动弹幕",
                    onClick = { toggleLiveDanmaku() },
                ))
                add(AppWindowAction("弹幕设置", onClick = { showDanmakuSettingsDialog = true }))
                add(AppWindowAction("屏蔽设置", onClick = { showBlockDialog = true }))
                add(AppWindowAction("刷新直播", onClick = { viewModel.retry() }))
                add(AppWindowAction("播放信息", onClick = { showPlayerInfoDialog = true }))
                add(AppWindowAction("截图", onClick = { captureLiveScreenshot() }))
            },
        )
    }

    if (showPortraitInteractionSheet) {
        AppModalBottomSheet(onDismissRequest = { showPortraitInteractionSheet = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((configuration.screenHeightDp * 0.58f).dp)
            ) {
                interactionContent(false, true)
            }
        }
    }
    
    // 画质菜单（BiliPai 同款底部选择 + 原生 chip 分发）
    if (showVoteSheet) {
        AppModalBottomSheet(onDismissRequest = { showVoteSheet = false }) {
            Box(Modifier.fillMaxWidth().height((configuration.screenHeightDp * 0.6f).dp)) {
                LiveVotePanel(voteSnapshot, Modifier.fillMaxSize())
            }
        }
    }
    if (showQualityMenu) {
        val successState = uiState as? LivePlayerState.Success
        if (successState != null) {
            com.android.purebilibili.feature.live.components.LiveQualitySheet(
                qualityList = successState.qualityList,
                currentQuality = successState.currentQuality,
                onQualitySelected = { qn ->
                    viewModel.changeQuality(qn)
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false }
            )
        }
    }

    if (showVideoFitMenu) {
        LiveVideoFitMenu(
            current = videoAspectRatio,
            onSelected = { mode ->
                videoAspectRatio = mode
                showVideoFitMenu = false
            },
            onDismiss = { showVideoFitMenu = false }
        )
    }

    if (showDanmakuSettingsDialog) {
        LiveDanmakuSettingsDialog(
            danmakuEnabled = successState?.isDanmakuEnabled ?: true,
            chatVisible = if (portraitPresentation.usePortraitControls) isPortraitChatVisible
                else isInteractionPanelVisible,
            displayArea = liveDanmakuDisplayArea,
            fontScale = liveDanmakuSettings.fontScale,
            opacity = liveDanmakuSettings.opacity,
            speed = liveDanmakuSettings.speed,
            allowScroll = liveDanmakuSettings.allowScroll,
            allowTop = liveDanmakuSettings.allowTop,
            allowBottom = liveDanmakuSettings.allowBottom,
            allowColorful = liveDanmakuSettings.allowColorful,
            superChatPanelEnabled = liveSuperChatPanelEnabled,
            onToggleSuperChatPanel = {
                coroutineScope.launch { SettingsManager.setLiveSuperChatPanelEnabled(context, !liveSuperChatPanelEnabled) }
            },
            superChatFlashEnabled = liveSuperChatFlashEnabled,
            superChatPersistent = liveSuperChatPersistent,
            superChatMaxWidthDp = liveSuperChatMaxWidthDp,
            portraitExpandEnabled = livePortraitExpandEnabled,
            keyboardControlsEnabled = liveKeyboardControlsEnabled,
            onToggleDanmaku = { toggleLiveDanmaku() },
            onToggleChat = {
                if (portraitPresentation.usePortraitControls) isPortraitChatVisible = !isPortraitChatVisible
                else isInteractionPanelVisible = !isInteractionPanelVisible
            },
            onDisplayAreaSelected = { area ->
                coroutineScope.launch {
                    SettingsManager.setDanmakuArea(context, area, liveDanmakuSettingsScope)
                }
            },
            onFontScaleChanged = { value ->
                coroutineScope.launch {
                    SettingsManager.setDanmakuFontScale(context, value, liveDanmakuSettingsScope)
                }
            },
            onOpacityChanged = { value ->
                coroutineScope.launch {
                    SettingsManager.setDanmakuOpacity(context, value, liveDanmakuSettingsScope)
                }
            },
            onSpeedChanged = { value ->
                coroutineScope.launch {
                    SettingsManager.setDanmakuSpeed(context, value, liveDanmakuSettingsScope)
                }
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
                coroutineScope.launch {
                    SettingsManager.setLiveSuperChatPersistent(context, !liveSuperChatPersistent)
                }
            },
            onSuperChatMaxWidthChanged = { widthDp ->
                coroutineScope.launch {
                    SettingsManager.setLiveSuperChatMaxWidthDp(context, widthDp)
                }
            },
            onTogglePortraitExpand = {
                coroutineScope.launch {
                    SettingsManager.setLivePortraitExpandEnabled(context, !livePortraitExpandEnabled)
                }
            },
            onToggleKeyboardControls = {
                coroutineScope.launch {
                    SettingsManager.setLiveKeyboardControlsEnabled(context, !liveKeyboardControlsEnabled)
                }
            },
            onOpenBlock = {
                showDanmakuSettingsDialog = false
                showBlockDialog = true
            },
            onDismiss = { showDanmakuSettingsDialog = false }
        )
    }

    if (showBlockDialog) {
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
            onSetMinutes = { minutes ->
                shutdownAtMillis = System.currentTimeMillis() + minutes * 60_000L
                showShutdownTimerDialog = false
                Toast.makeText(context, "${minutes}分钟后关闭", Toast.LENGTH_SHORT).show()
            },
            onCancelTimer = {
                shutdownAtMillis = null
                showShutdownTimerDialog = false
                Toast.makeText(context, "已取消定时关闭", Toast.LENGTH_SHORT).show()
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

    if (showShareMessageSheet) {
        LiveShareToMessageSheet(
            roomId = bilibiliRoomId,
            roomTitle = liveRoomTitle,
            onDismiss = { showShareMessageSheet = false },
            onResult = { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showSendDanmakuSheet) {
        LiveSendDanmakuSheet(
            onDismiss = {
                showSendDanmakuSheet = false
                showEmoticonSheet = false
            },
            onSend = { message, color, mode ->
                viewModel.sendDanmaku(message, color, mode)
            },
            message = danmakuDraft,
            onMessageChange = { danmakuDraft = it },
            permission = successState?.danmakuPermission ?: com.android.purebilibili.data.repository.LiveDanmakuPermission(),
            replyTarget = replyTarget,
            isSending = isSending,
            selectedColor = sendColor,
            selectedMode = sendMode,
            onColorSelected = { sendColor = it },
            onModeSelected = { sendMode = it },
            onClearReply = viewModel::clearReplyTarget,
            onOpenEmote = { showEmoticonSheet = !showEmoticonSheet },
            emoticonContent = if (showEmoticonSheet) ({
                LiveEmoticonPanel(
                    packages = emoticonPackages,
                    enabled = !isSending,
                    onInsertText = { item ->
                        val maxLength = successState?.danmakuPermission?.maxLength ?: 40
                        com.android.purebilibili.data.repository.appendLiveTextEmoticon(danmakuDraft, item, maxLength)
                            .onSuccess { danmakuDraft = it }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
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
                Toast.makeText(context, "暂无可用线路", Toast.LENGTH_SHORT).show()
                showStreamSourceSheet = false
            }
        }
    }
}

@Composable
private fun LiveRoomBackdrop(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    val tokens = resolveLiveBiliPaiRoomColorTokens()
    Box(modifier = modifier.background(tokens.baseBackgroundColor)) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = tokens.backdropImageAlpha)
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
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onShareToMessage: () -> Unit,
    onOpenBrowser: () -> Unit,
    isFollowing: Boolean,
    currentQualityDesc: String,
    onFollowClick: () -> Unit,
    onQualityClick: () -> Unit,
    onlineRankText: String,
    onOpenRank: () -> Unit,
    onOpenSend: () -> Unit,
    onOpenBlock: () -> Unit,
    redPocketInfo: LiveRedPocketInfo?,
    onRedPocketClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberLiveChromePalette()
    val roomColorTokens = resolveLiveBiliPaiRoomColorTokens()
    val backIcon = rememberAppBackIcon()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.scrim.copy(alpha = 0.92f),
                        palette.scrim.copy(alpha = 0.42f),
                        palette.scrim.copy(alpha = 0f)
                    )
                )
            )
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconButton(
            onClick = onBack,
            modifier = Modifier.size(30.dp)
        ) {
            AppIcon(
                backIcon,
                contentDescription = "返回",
                tint = roomColorTokens.inputOverlayColor
            )
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
        AppWindowActionMenu(
            modifier = Modifier.size(30.dp),
            groups = listOf(
                listOfNotNull(
                    if (redPocketInfo != null) AppWindowAction("人气红包", onClick = onRedPocketClick) else null,
                    AppWindowAction(
                        label = if (isFollowing) "取消关注" else "关注主播",
                        onClick = onFollowClick,
                    ),
                    AppWindowAction(
                        label = "画质：$currentQualityDesc",
                        onClick = onQualityClick,
                    ),
                    AppWindowAction(
                        label = "高能榜",
                        onClick = onOpenRank,
                    ),
                    AppWindowAction(
                        label = "发弹幕",
                        onClick = onOpenSend,
                    ),
                    AppWindowAction(
                        label = "屏蔽弹幕",
                        icon = Icons.Outlined.Block,
                        onClick = onOpenBlock,
                    ),
                    AppWindowAction(
                        label = "复制链接",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = onCopyLink,
                    ),
                    AppWindowAction(
                        label = "分享直播间",
                        icon = Icons.Outlined.Share,
                        onClick = onShare,
                    ),
                    AppWindowAction(
                        label = "分享至消息",
                        icon = Icons.AutoMirrored.Outlined.ForwardToInbox,
                        onClick = onShareToMessage,
                    ),
                    AppWindowAction(
                        label = "浏览器打开",
                        icon = Icons.Outlined.OpenInBrowser,
                        onClick = onOpenBrowser,
                    ),
                ),
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

@Composable
private fun LiveRedPocketChip(
    info: LiveRedPocketInfo,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val compactChrome = rememberAppPlayerChromeProfile().compactChromeSpec
    val label = if (compact) {
        "红包"
    } else {
        info.awardsText.ifBlank { info.danmu.ifBlank { "人气红包" } }
    }
    val chipColors = resolveAccessibleContainerColors(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        backgroundColor = MaterialTheme.colorScheme.surface,
        fallbackContentColors = listOf(
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.onBackground,
        ),
    )
    AppSurface(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppSpacingTokens.TripleExtraLarge),
        shape = AppShapes.borderedContainer(ContainerLevel.Pill),
        color = chipColors.containerColor,
        border = androidx.compose.foundation.BorderStroke(
            AppSpacingTokens.Micro / 2f,
            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.40f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) AppSpacingTokens.Small else AppSpacingTokens.Medium,
                vertical = AppSpacingTokens.Small
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = Icons.Outlined.CardGiftcard,
                contentDescription = null,
                tint = chipColors.contentColor,
                modifier = Modifier.size(compactChrome.smallIconSizeDp.dp)
            )
            Spacer(Modifier.width(AppSpacingTokens.ExtraSmall))
            AppText(
                text = label,
                color = chipColors.contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LivePortraitInfoPanel(
    anchorInfoBar: @Composable () -> Unit,
    bodyContent: @Composable () -> Unit
) {
    AppSurface(
        modifier = Modifier.fillMaxSize(),
        shape = AppShapes.container(ContainerLevel.Sheet),
        color = AppSurfaceTokens.surface(),
        tonalElevation = AppSpacingTokens.Micro,
        shadowElevation = AppSpacingTokens.None
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AppSurfaceTokens.surface(),
                            AppSurfaceTokens.surfaceContainer()
                        )
                    )
                )
        ) {
            Spacer(Modifier.height(AppSpacingTokens.Small))
            anchorInfoBar()
            AppHorizontalDivider(color = AppSurfaceTokens.divider().copy(alpha = 0.45f))
            Box(modifier = Modifier.weight(1f)) {
                bodyContent()
            }
        }
    }
}

@Composable
private fun LivePrimaryInteractionPanel(
    isOverlay: Boolean = false,
    selectedTab: Int,
    showSuperChat: Boolean,
    onSelectedTab: (Int) -> Unit,
    chatContent: @Composable () -> Unit,
    superChatContent: @Composable () -> Unit,
    voteContent: @Composable () -> Unit,
    persistentFooter: (@Composable () -> Unit)? = null,
) {
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, if (showSuperChat) 1 else 0),
        pageCount = { if (showSuperChat) 2 else 1 },
    )
    val latestOnSelectedTab by rememberUpdatedState(onSelectedTab)
    // 主页面与完整聊天弹层共用选择；恢复时直接定位，避免指示器和内容脱节。
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
            modifier = Modifier.weight(1f),
        ) { page ->
            Box(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                if (page == 0) chatContent() else superChatContent()
            }
        }
        persistentFooter?.invoke()
    }
}

@Composable
private fun LiveLandscapeChatPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = rememberLiveChromePalette()
    val roomColorTokens = resolveLiveBiliPaiRoomColorTokens()
    AppSurface(
        modifier = modifier,
        shape = AppShapes.borderedContainer(ContainerLevel.Floating),
        color = palette.surface.copy(alpha = if (palette.isDark) 0.72f else 0.90f),
        border = androidx.compose.foundation.BorderStroke(
            AppSpacingTokens.Micro / 2f,
            palette.border
        ),
        tonalElevation = AppSpacingTokens.None,
        shadowElevation = AppSpacingTokens.None
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            roomColorTokens.inputOverlayColor.copy(
                                alpha = if (palette.isDark) 0.04f else 0.16f
                            ),
                            palette.surfaceMuted.copy(alpha = if (palette.isDark) 0.22f else 0.70f),
                            palette.surface.copy(alpha = if (palette.isDark) 0.42f else 0.94f)
                        )
                    )
                )
        ) {
            content()
        }
    }
}

@Composable
private fun LiveVideoFitMenu(
    current: VideoAspectRatio,
    onSelected: (VideoAspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = rememberLiveChromePalette()
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val visualSpec = remember(playerChromeProfile.tabPresentation) {
        resolveLiveVisualSpec(playerChromeProfile.tabPresentation)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.scrim.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AppSurface(
            modifier = Modifier.width(visualSpec.playerQualityDialogWidthDp.dp),
            shape = AppShapes.borderedContainer(ContainerLevel.Dialog),
            color = palette.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(
                AppSpacingTokens.Micro / 2f,
                palette.border
            )
        ) {
            Column(Modifier.padding(vertical = AppSpacingTokens.Small)) {
                AppText(
                    "画面比例",
                    color = palette.primaryText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(AppSpacingTokens.Large)
                )
                VideoAspectRatio.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(mode) }
                            .padding(
                                horizontal = AppSpacingTokens.Large,
                                vertical = AppSpacingTokens.Medium
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(mode.displayName, color = if (mode == current) palette.accent else palette.primaryText)
                        Spacer(Modifier.weight(1f))
                        if (mode == current) {
                            AppIcon(Icons.Outlined.Check, null, tint = palette.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDanmakuSettingsDialog(
    danmakuEnabled: Boolean,
    chatVisible: Boolean,
    displayArea: Float,
    fontScale: Float,
    opacity: Float,
    speed: Float,
    allowScroll: Boolean,
    allowTop: Boolean,
    allowBottom: Boolean,
    allowColorful: Boolean,
    superChatPanelEnabled: Boolean,
    onToggleSuperChatPanel: () -> Unit,
    superChatFlashEnabled: Boolean,
    superChatPersistent: Boolean,
    superChatMaxWidthDp: Int,
    portraitExpandEnabled: Boolean,
    keyboardControlsEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onToggleChat: () -> Unit,
    onDisplayAreaSelected: (Float) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onToggleAllowScroll: () -> Unit,
    onToggleAllowTop: () -> Unit,
    onToggleAllowBottom: () -> Unit,
    onToggleAllowColorful: () -> Unit,
    onToggleSuperChatFlash: () -> Unit,
    onToggleSuperChatPersistent: () -> Unit,
    onSuperChatMaxWidthChanged: (Int) -> Unit,
    onTogglePortraitExpand: () -> Unit,
    onToggleKeyboardControls: () -> Unit,
    onOpenBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("弹幕设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)
            ) {
                LiveSettingSwitchRow(
                    title = "弹幕显示",
                    checked = danmakuEnabled,
                    onCheckedChange = { onToggleDanmaku() }
                )
                LiveSettingSwitchRow(
                    title = "互动区",
                    checked = chatVisible,
                    onCheckedChange = { onToggleChat() }
                )
                LiveDanmakuAreaSelector(
                    currentArea = displayArea,
                    onAreaSelected = onDisplayAreaSelected
                )
                LiveDanmakuStyleSliderRow(
                    label = "字号",
                    valueText = "${(fontScale * 100).roundToInt()}%",
                    value = fontScale,
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    onValueChangeFinished = onFontScaleChanged
                )
                LiveDanmakuStyleSliderRow(
                    label = "不透明度",
                    valueText = "${(opacity * 100).roundToInt()}%",
                    value = opacity,
                    valueRange = 0.2f..1.0f,
                    steps = 3,
                    onValueChangeFinished = onOpacityChanged
                )
                LiveDanmakuStyleSliderRow(
                    label = "滚动速度",
                    valueText = "${(speed * 100).roundToInt()}%",
                    value = speed,
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    onValueChangeFinished = onSpeedChanged
                )
                LiveSettingSwitchRow(
                    title = "滚动弹幕",
                    checked = allowScroll,
                    onCheckedChange = { onToggleAllowScroll() }
                )
                LiveSettingSwitchRow(
                    title = "顶部弹幕",
                    checked = allowTop,
                    onCheckedChange = { onToggleAllowTop() }
                )
                LiveSettingSwitchRow(
                    title = "底部弹幕",
                    checked = allowBottom,
                    onCheckedChange = { onToggleAllowBottom() }
                )
                LiveSettingSwitchRow(
                    title = "彩色弹幕",
                    checked = allowColorful,
                    onCheckedChange = { onToggleAllowColorful() }
                )
                LiveSettingSwitchRow(
                    title = "聊天区显示 SC 页面",
                    checked = superChatPanelEnabled,
                    onCheckedChange = { onToggleSuperChatPanel() },
                )
                LiveSettingSwitchRow(
                    title = "醒目留言弹窗",
                    checked = superChatFlashEnabled,
                    onCheckedChange = { onToggleSuperChatFlash() }
                )
                LiveSettingSwitchRow(
                    title = "SC 卡片常驻显示",
                    checked = superChatPersistent,
                    onCheckedChange = { onToggleSuperChatPersistent() }
                )
                LiveDanmakuStyleSliderRow(
                    label = "全屏 SC 宽度",
                    valueText = "${superChatMaxWidthDp} dp",
                    value = superChatMaxWidthDp.toFloat(),
                    valueRange = 260f..640f,
                    steps = 18,
                    onValueChangeFinished = { onSuperChatMaxWidthChanged(it.roundToInt()) }
                )
                LiveSettingSwitchRow(
                    title = "竖屏扩大展示",
                    checked = portraitExpandEnabled,
                    onCheckedChange = { onTogglePortraitExpand() }
                )
                AppText(
                    "仅对真竖屏直播生效；放大画面填满视窗时会裁去部分边缘。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                LiveSettingSwitchRow(
                    title = "键盘控制",
                    checked = keyboardControlsEnabled,
                    onCheckedChange = { onToggleKeyboardControls() }
                )
                if (keyboardControlsEnabled) {
                    AppText(
                        "Space/K 播放暂停 · F 全屏 · D 弹幕 · M 静音 · 方向键音量 · Q 点赞 · S 截图",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AppSurface(
                    onClick = onOpenBlock,
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = AppSurfaceTokens.surfaceContainerHigh().copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = AppSpacingTokens.Medium,
                            vertical = AppSpacingTokens.Medium
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(Icons.Outlined.Block, contentDescription = null)
                        Spacer(Modifier.width(AppSpacingTokens.Medium))
                        AppText("屏蔽管理", modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) { AppText("完成") }
        }
    )
}

@Composable
private fun LiveDanmakuStyleSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (Float) -> Unit
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            AppText(
                text = valueText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        AppSlider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChangeFinished(localValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = valueText
                }
        )
    }
}

@Composable
private fun LiveDanmakuAreaSelector(
    currentArea: Float,
    onAreaSelected: (Float) -> Unit
) {
    data class LiveDanmakuAreaOption(
        val value: Float,
        val label: String,
        val subtitle: String
    )

    val title = "弹幕区域"
    val options = remember {
        listOf(
            LiveDanmakuAreaOption(0.25f, "1/4", "顶部"),
            LiveDanmakuAreaOption(0.5f, "1/2", "半屏"),
            LiveDanmakuAreaOption(0.75f, "3/4", "大部"),
            LiveDanmakuAreaOption(1.0f, "全屏", "铺满")
        )
    }

    AppSurface(
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.surfaceContainerHigh().copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(
                horizontal = AppSpacingTokens.Medium,
                vertical = AppSpacingTokens.Medium
            )
        ) {
            AppText(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(AppSpacingTokens.Medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
            ) {
                options.forEach { option ->
                    val selected = kotlin.math.abs(currentArea - option.value) < 0.05f
                    AppSingleChoiceRow(
                        selected = selected,
                        onClick = { onAreaSelected(option.value) },
                        shape = AppShapes.borderedContainer(ContainerLevel.Card),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppText(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = AppSurfaceTokens.onSurfaceContainerHigh()
                            )
                            AppText(
                                text = option.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppSurfaceTokens.onSurfaceVariantSummary()
                            )
                        }
                    }
                }
            }
        }
    }
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
private fun LiveDanmakuBlockDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("屏蔽弹幕") },
        text = {
            AppOutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                singleLine = true,
                label = { AppText("关键词") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            AppTextButton(
                enabled = keyword.isNotBlank(),
                onClick = { onConfirm(keyword) }
            ) {
                AppText("添加")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { AppText("取消") }
        }
    )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun LiveShutdownTimerDialog(
    activeTargetMillis: Long?,
    onSetMinutes: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMinutes by remember(activeTargetMillis) {
        mutableStateOf(activeTargetMillis?.let {
            ((it - System.currentTimeMillis()) / 60_000L).coerceIn(1L, 59999L).toInt()
        } ?: 30)
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("定时关闭") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            ) {
                val remainingText = activeTargetMillis
                    ?.let { ((it - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) }
                    ?.let { "剩余约${it}分钟" }
                if (remainingText != null) {
                    AppText(remainingText, color = MaterialTheme.colorScheme.primary)
                }
                listOf(15L, 30L, 60L).forEach { minutes ->
                    AppSurface(
                        onClick = { onSetMinutes(minutes) },
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = AppSurfaceTokens.surfaceContainerHigh().copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppText(
                            text = "${minutes}分钟后关闭",
                            modifier = Modifier.padding(
                                horizontal = AppSpacingTokens.Medium,
                                vertical = AppSpacingTokens.Medium
                            )
                        )
                    }
                }
                AppDurationPicker(
                    minutes = customMinutes,
                    onMinutesChange = { customMinutes = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (activeTargetMillis != null) {
                    AppTextButton(onClick = onCancelTimer) { AppText("取消定时") }
                }
            }
        },
        confirmButton = {
            AppTextButton(enabled = customMinutes > 0, onClick = { onSetMinutes(customMinutes.toLong()) }) {
                AppText("应用")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { AppText("关闭") }
        }
    )
}
