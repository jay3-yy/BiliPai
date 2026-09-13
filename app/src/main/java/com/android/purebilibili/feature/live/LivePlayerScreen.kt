package com.android.purebilibili.feature.live

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.data.repository.LiveRedPocketInfo
import com.android.purebilibili.feature.live.components.LandscapeChatOverlay
import com.android.purebilibili.feature.live.components.LiveChatSection
import com.android.purebilibili.feature.live.components.LiveContributionRankSheet
import com.android.purebilibili.feature.live.components.LiveDmBlockSheet
import com.android.purebilibili.feature.live.components.LiveEmoticonSheet
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.hazeEffectCompat
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
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
    var showStreamSourceSheet by remember { mutableStateOf(false) }
    var showPortraitMoreSheet by remember(roomId, siteId) { mutableStateOf(false) }
    var showPortraitInteractionSheet by remember(roomId, siteId) { mutableStateOf(false) }
    var isPortraitClearScreen by rememberSaveable(roomId, siteId) { mutableStateOf(false) }
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
    var videoAspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    var backgroundPlaybackEnabled by remember {
        mutableStateOf(SettingsManager.getBackgroundPlaybackEnabledSync(context))
    }
    var shutdownAtMillis by remember { mutableStateOf<Long?>(null) }
    val showLivePipButton = remember { shouldShowLivePipButton(android.os.Build.VERSION.SDK_INT) }
    val successState = uiState as? LivePlayerState.Success
    val isLiveAudioOnly = successState?.isAudioOnly == true
    val superChatItems by viewModel.superChatItems.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
    val emoticonPackages by viewModel.emoticonPackages.collectAsStateWithLifecycle()
    val shieldInfo by viewModel.shieldInfo.collectAsStateWithLifecycle()
    val voteSnapshot by viewModel.voteSnapshot.collectAsStateWithLifecycle()
    val roomInfo = successState?.roomInfo ?: RoomInfo()
    val anchorInfo = successState?.anchorInfo ?: AnchorInfo()
    val currentIsRoomLive by rememberUpdatedState(roomInfo.liveStatus == 1)
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
        isPortraitLive = isPortraitLive
    )
    val portraitPresentation = resolveLivePortraitPresentation(
        layoutMode = liveLayoutMode,
        clearScreen = isPortraitClearScreen,
        chatVisible = isPortraitChatVisible,
    )
    val playerGesturePolicy = resolveLivePlayerGesturePolicy(liveLayoutMode)
    LaunchedEffect(liveLayoutMode) {
        if (!portraitPresentation.usePortraitControls) {
            showPortraitMoreSheet = false
            showPortraitInteractionSheet = false
        }
    }
    val portraitChatPreviewCount = resolveLivePortraitChatPreviewCount(
        configuration.screenHeightDp,
        configuration.fontScale,
    )
    val portraitChatMessages = remember(roomId, siteId) { mutableStateListOf<LiveDanmakuItem>() }
    var portraitDanmakuSequence by remember(roomId, siteId) { mutableLongStateOf(0L) }
    // 持续监听当前直播间的弹幕流；状态转换与清屏不会导致消息丢失或清空重置
    LaunchedEffect(roomId, siteId, viewModel.danmakuFlow) {
        portraitChatMessages.clear()
        portraitDanmakuSequence = 0L
        viewModel.danmakuFlow.collect { item ->
            if (shouldRenderLiveDanmaku(item.text, item.emoticonUrl)) {
                portraitChatMessages.add(item)
                if (portraitChatMessages.size > 200) portraitChatMessages.removeAt(0)
                portraitDanmakuSequence++
            }
        }
    }
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
    val showSplitChatPanel = remember(liveLayoutMode) {
        shouldShowLiveSplitChatPanel(
            layoutMode = liveLayoutMode,
            isInteractionPanelVisible = true
        )
    }
    val splitChatPanelWidthDp = remember(configuration.screenWidthDp) {
        resolveLiveSplitChatPanelWidthDp(
            screenWidthDp = configuration.screenWidthDp,
            contentPaddingDp = AppSpacingTokens.Medium.value.toInt() * 2,
        )
    }
    val showLandscapeChatOverlay = remember(liveLayoutMode, isInteractionPanelVisible) {
        shouldShowLiveLandscapeChatOverlay(
            layoutMode = liveLayoutMode,
            isInteractionPanelVisible = isInteractionPanelVisible
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
        isFullscreen = !isFullscreen
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
        Toast.makeText(context, "请选择联系人后发送直播间链接", Toast.LENGTH_SHORT).show()
        shareLiveUrl()
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
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
                }
                CrashReporter.markLivePlaybackStage(if (playing) "playing" else "not_playing")
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
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    
    // 播放 URL 管理
    LaunchedEffect(siteId, roomId) { viewModel.loadLiveStream(bilibiliRoomId) }
    // 播放 URL 管理 - 只在 playUrl 变化时重新加载
    val playUrl = (uiState as? LivePlayerState.Success)?.playUrl
    LaunchedEffect(playUrl) {
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
                        viewModel.pauseLiveHeartbeat()
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
                    viewModel.resumeLiveHeartbeatIfNeeded()
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
        
        if (liveLayoutMode == LiveRoomLayoutMode.LandscapeOverlay) {
            // 隐藏状态栏和导航栏
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            // 恢复显示
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val liveRequestedOrientationMode = remember(windowSizeClass.isTabletDevice, isFullscreen) {
        resolveLiveRequestedOrientationMode(
            isTabletDevice = windowSizeClass.isTabletDevice,
            isFullscreen = isFullscreen,
        )
    }
    LaunchedEffect(liveRequestedOrientationMode) {
        val requestedOrientation = when (liveRequestedOrientationMode) {
            LiveRequestedOrientationMode.Unspecified ->
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            LiveRequestedOrientationMode.SensorLandscape ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            LiveRequestedOrientationMode.Portrait ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        activity?.applyPlayerRequestedOrientation(requestedOrientation)
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val viewportAspectRatio = videoAspectRatio
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
                    Modifier.size(
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
            if (portraitPresentation.showMediaOverlays && shouldRenderLiveDanmakuOverlayForAudioOnly(
                    isDanmakuEnabled = successState?.isDanmakuEnabled == true,
                    isAudioOnly = isLiveAudioOnly
                )
            ) {
                LiveDanmakuOverlay(
                    danmakuFlow = viewModel.danmakuFlow,
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
                isPlaying = isPlaying,
                isFullscreen = isFullscreen,
                gesturePolicy = playerGesturePolicy,
                usePortraitControls = portraitPresentation.usePortraitControls,
                isClearScreen = portraitPresentation.clearScreen,
                onPortraitTap = { isPortraitClearScreen = !isPortraitClearScreen },
                onOpenPortraitMore = {
                    isPortraitClearScreen = false
                    showPortraitMoreSheet = true
                },
                showTopBar = shouldShowLivePlayerControlsTopBar(
                    layoutMode = liveLayoutMode,
                    isFullscreen = isFullscreen
                ),
                title = liveRoomTitle,
                subtitle = liveSubtitle,
                onPlayPause = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onToggleFullscreen = { toggleFullscreen() },
                onBack = { exitLiveRoom() },
                // 侧边栏开关
                isChatVisible = isInteractionPanelVisible,
                onToggleChat = { isInteractionPanelVisible = !isInteractionPanelVisible },
                showChatToggle = showChatToggle,
                // 弹幕开关
                isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
                onToggleDanmaku = { viewModel.toggleDanmaku() },
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
            danmakuFlow = viewModel.danmakuFlow,
            onSendDanmaku = { text -> viewModel.sendDanmaku(text) },
            headerTitle = "实时互动",
            supportingText = "发送弹幕和主播互动",
            isOverlay = isOverlay,
            showHeader = showHeader,
            isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
            onToggleDanmaku = { viewModel.toggleDanmaku() },
            onLike = { count -> viewModel.clickLike(count) },
            onOpenEmote = {
                showEmoticonSheet = true
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
            modifier = Modifier.fillMaxSize()
        )
    }

    val interactionContent: @Composable (Boolean) -> Unit = { isOverlay ->
        LivePrimaryInteractionPanel(
            selectedTab = selectedInteractionTab,
            onSelectedTab = { selectedInteractionTab = it },
            chatContent = { chatContent(isOverlay, false) },
            superChatContent = {
                LiveSuperChatSection(
                    items = superChatItems,
                    modifier = Modifier.fillMaxSize(),
                    onExpired = { item ->
                        if (item.superChatId > 0L) {
                            viewModel.dismissSuperChat(item.superChatId)
                        }
                    },
                )
            },
            voteContent = { LiveVotePanel(voteSnapshot, Modifier.fillMaxSize()) }
        )
    }

    // 统一容器：SC 全屏浮层需要盖住四种布局的播放器/弹幕层
    Box(modifier = Modifier.fillMaxSize()) {
    when (liveLayoutMode) {
        LiveRoomLayoutMode.LandscapeSplit -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(roomColorTokens.baseBackgroundColor)
            ) {
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
                        onOpenRank = { showContributionRankSheet = true },
                        onOpenSend = { showSendDanmakuSheet = true },
                        onOpenBlock = { showBlockDialog = true },
                        redPocketInfo = successState?.redPocketInfo,
                        onRedPocketClick = {
                            successState?.redPocketInfo?.let { openRedPocket(it) }
                        }
                    )
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
                                interactionContent(false)
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
                if (showLandscapeChatOverlay) {
                    val screenWidthDp = maxWidth.value.roundToInt()
                    val screenHeightDp = maxHeight.value.roundToInt()
                    val overlayMetrics = remember(screenWidthDp, screenHeightDp) {
                        resolveLiveLandscapeChatOverlayMetrics(
                            screenWidthDp = screenWidthDp,
                            screenHeightDp = screenHeightDp
                        )
                    }
                    val overlayWidthDp = remember(screenWidthDp, overlayMetrics) {
                        resolveLiveLandscapeChatOverlayWidthDp(
                            screenWidthDp = screenWidthDp,
                            metrics = overlayMetrics
                        )
                    }
                    val overlayHeightDp = remember(screenHeightDp, overlayMetrics) {
                        resolveLiveLandscapeChatOverlayHeightDp(
                            screenHeightDp = screenHeightDp,
                            metrics = overlayMetrics
                        )
                    }
                    val overlayShape = AppShapes.borderedContainer(ContainerLevel.Floating)
                    AppSurface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = overlayMetrics.edgePaddingDp.dp,
                                bottom = overlayMetrics.bottomControlReserveDp.dp
                            )
                            .width(overlayWidthDp.dp)
                            .height(overlayHeightDp.dp)
                            .clip(overlayShape)
                            .hazeEffectCompat(
                                state = hazeState,
                                style = HazeMaterials.ultraThin()
                            ),
                        shape = overlayShape,
                        color = roomColorTokens.baseBackgroundColor.copy(alpha = 0.28f),
                        border = androidx.compose.foundation.BorderStroke(
                            AppSpacingTokens.Micro / 2f,
                            roomColorTokens.inputOverlayColor.copy(alpha = 0.15f)
                        )
                    ) {
                        LandscapeChatOverlay(
                            danmakuFlow = viewModel.danmakuFlow,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        LiveRoomLayoutMode.PortraitVerticalOverlay -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(roomColorTokens.baseBackgroundColor)
            ) {
                if (liveCoverForUi.isNotBlank()) {
                    AsyncImage(
                        model = liveCoverForUi,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = roomColorTokens.backdropImageAlpha)
                    )
                }
                playerContent()
                if (portraitPresentation.showChrome) {
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
                        onOpenRank = { showContributionRankSheet = true },
                        onOpenSend = { showSendDanmakuSheet = true },
                        onOpenBlock = { showBlockDialog = true },
                        redPocketInfo = successState?.redPocketInfo,
                        onRedPocketClick = {
                            successState?.redPocketInfo?.let { openRedPocket(it) }
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                horizontal = AppSpacingTokens.Medium,
                                vertical = AppSpacingTokens.Small,
                            ),
                        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                    ) {
                        if (portraitPresentation.showChatPreview) {
                            LivePortraitChatStream(
                                messages = portraitChatMessages,
                                danmakuSequence = portraitDanmakuSequence,
                                superChatCount = superChatItems.size,
                                onOpenSuperChat = {
                                    selectedInteractionTab = 1
                                    showPortraitInteractionSheet = true
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
                                onOpenHistory = {
                                    selectedInteractionTab = 0
                                    showPortraitInteractionSheet = true
                                },
                                hazeState = hazeState,
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .heightIn(max = portraitOverlayPanelHeightDp.dp),
                            )
                        }
                        LivePortraitBottomBar(
                            isDanmakuEnabled = successState?.isDanmakuEnabled ?: true,
                            chatVisible = isPortraitChatVisible,
                            onToggleDanmaku = { viewModel.toggleDanmaku() },
                            onOpenSend = { showSendDanmakuSheet = true },
                            onOpenEmote = { showEmoticonSheet = true },
                            onToggleChat = { isPortraitChatVisible = !isPortraitChatVisible },
                            onOpenMore = { showPortraitMoreSheet = true },
                            onLike = { count -> viewModel.clickLike(count) },
                            hazeState = hazeState,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    AppSurface(
                        onClick = { isPortraitClearScreen = false },
                        shape = AppShapes.container(ContainerLevel.Pill),
                        color = LiveStatusPalette.MediaScrim.copy(alpha = 0.56f),
                        contentColor = LiveStatusPalette.MediaContent,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(AppSpacingTokens.Medium)
                            .heightIn(min = 48.dp),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = AppSpacingTokens.Large),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText("退出清屏", color = LiveStatusPalette.MediaContent)
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
                        interactionContent(shouldUseLiveChatMediaOverlay(liveLayoutMode))
                    }
                }
            }
        }
    }

    // SC 左下角非侵入式悬浮卡片（不遮挡中央视频画面，仅响应实时新 SC，带倒计时与独立关闭）
    if (portraitPresentation.showMediaOverlays) {
        LiveSuperChatFlashOverlay(
            flashFlow = viewModel.superChatFlashFlow,
            onUserClick = onUserClick,
            modifier = Modifier.fillMaxSize()
        )
    }
    }

    if (portraitPresentation.showChrome && showPortraitMoreSheet) {
        LivePortraitMoreSheet(
            onDismiss = { showPortraitMoreSheet = false },
            actions = buildList {
                add(AppWindowAction("清屏", onClick = { isPortraitClearScreen = true }))
                add(AppWindowAction("完整聊天 / SC / 投票", onClick = {
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
                add(AppWindowAction(if (isPlaying) "暂停播放" else "继续播放", onClick = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }))
                add(AppWindowAction("横屏观看", onClick = { toggleFullscreen() }))
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
                    onClick = { viewModel.toggleDanmaku() },
                ))
                add(AppWindowAction("弹幕设置", onClick = { showDanmakuSettingsDialog = true }))
                add(AppWindowAction("屏蔽设置", onClick = { showBlockDialog = true }))
                add(AppWindowAction("刷新直播", onClick = { viewModel.retry() }))
                add(AppWindowAction("播放信息", onClick = { showPlayerInfoDialog = true }))
                add(AppWindowAction("截图", onClick = { captureLiveScreenshot() }))
            },
        )
    }

    if (portraitPresentation.showChrome && showPortraitInteractionSheet) {
        AppModalBottomSheet(onDismissRequest = { showPortraitInteractionSheet = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((configuration.screenHeightDp * 0.58f).dp)
            ) {
                interactionContent(false)
            }
        }
    }
    
    // 画质菜单（BiliPai 同款底部选择 + 原生 chip 分发）
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
            onToggleDanmaku = { viewModel.toggleDanmaku() },
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

    if (showEmoticonSheet) {
        LiveEmoticonSheet(
            packages = emoticonPackages,
            onSelected = { item ->
                viewModel.sendEmoticon(item)
                showEmoticonSheet = false
            },
            onDismiss = { showEmoticonSheet = false }
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
            onDismiss = { showContributionRankSheet = false }
        )
    }

    if (showSendDanmakuSheet) {
        LiveSendDanmakuSheet(
            onDismiss = {
                showSendDanmakuSheet = false
                viewModel.clearReplyTarget()
            },
            onSend = { message, color, mode ->
                viewModel.sendDanmaku(message, color, mode)
                showSendDanmakuSheet = false
            },
            permission = successState?.danmakuPermission ?: com.android.purebilibili.data.repository.LiveDanmakuPermission(),
            replyTarget = replyTarget,
            onOpenEmote = {
                showSendDanmakuSheet = false
                showEmoticonSheet = true
            },
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
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val liveVisualSpec = remember(playerChromeProfile.tabPresentation) {
        resolveLiveVisualSpec(playerChromeProfile.tabPresentation)
    }
    val compactChrome = playerChromeProfile.compactChromeSpec
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
            .padding(
                horizontal = AppSpacingTokens.Medium,
                vertical = AppSpacingTokens.Medium
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconButton(
            onClick = onBack,
            modifier = Modifier.size(liveVisualSpec.playerButtonTouchTargetDp.dp)
        ) {
            AppIcon(
                backIcon,
                contentDescription = "返回",
                tint = roomColorTokens.inputOverlayColor
            )
        }
        Spacer(Modifier.width(AppSpacingTokens.ExtraSmall))
        AppSurface(
            shape = AppShapes.container(ContainerLevel.Pill),
            color = LiveStatusPalette.MediaScrim.copy(alpha = 0.42f),
            contentColor = roomColorTokens.inputOverlayColor,
            modifier = Modifier
                .clickable(
                    enabled = anchorInfo.uid > 0L,
                    role = Role.Button,
                    onClickLabel = "查看主播${anchorInfo.uname}",
                    onClick = { onUserClick(anchorInfo.uid) }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "主播：${anchorInfo.uname}"
                }
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 3.dp,
                    end = if (!isFollowing) 4.dp else AppSpacingTokens.Small,
                    top = 3.dp,
                    bottom = 3.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = anchorInfo.face,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(roomColorTokens.inputOverlayColor.copy(alpha = 0.18f))
                )
                Spacer(Modifier.width(AppSpacingTokens.Small))
                Column(
                    modifier = Modifier.widthIn(min = 40.dp, max = 110.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    AppText(
                        text = anchorInfo.uname.ifBlank { roomTitle },
                        color = roomColorTokens.inputOverlayColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val secondaryText = subtitle.ifBlank { roomTitle }
                    if (secondaryText.isNotBlank()) {
                        AppText(
                            text = secondaryText,
                            color = roomColorTokens.inputOverlayColor.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!isFollowing) {
                    Spacer(Modifier.width(AppSpacingTokens.Small))
                    AppSurface(
                        onClick = onFollowClick,
                        shape = AppShapes.container(ContainerLevel.Pill),
                        color = palette.accent,
                        contentColor = palette.onAccent,
                        modifier = Modifier
                            .height(26.dp)
                            .semantics { contentDescription = "关注主播" }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AppIcon(
                                imageVector = rememberAppProfileAddIcon(),
                                contentDescription = null,
                                tint = palette.onAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            AppText(
                                text = "关注",
                                color = palette.onAccent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        AppSurface(
            onClick = onOpenRank,
            shape = AppShapes.container(ContainerLevel.Pill),
            color = LiveStatusPalette.MediaScrim.copy(alpha = 0.42f),
            contentColor = roomColorTokens.inputOverlayColor,
            modifier = Modifier
                .height(30.dp)
                .semantics { contentDescription = "高能榜" }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro)
            ) {
                AppIcon(
                    imageVector = rememberAppAnalyticsIcon(),
                    contentDescription = null,
                    tint = roomColorTokens.inputOverlayColor,
                    modifier = Modifier.size(13.dp)
                )
                AppText(
                    text = "高能榜",
                    color = roomColorTokens.inputOverlayColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (redPocketInfo != null) {
            Spacer(Modifier.width(AppSpacingTokens.Small))
            LiveRedPocketChip(
                info = redPocketInfo,
                onClick = onRedPocketClick,
                compact = true
            )
        }
        Spacer(Modifier.width(AppSpacingTokens.Small))
        AppWindowActionMenu(
            modifier = Modifier.size(liveVisualSpec.playerButtonTouchTargetDp.dp),
            groups = listOf(
                listOf(
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
    selectedTab: Int,
    onSelectedTab: (Int) -> Unit,
    chatContent: @Composable () -> Unit,
    superChatContent: @Composable () -> Unit,
    voteContent: @Composable () -> Unit
) {
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val segmentedSpec = remember(playerChromeProfile.compactChromeSpec) {
        resolveLiveInteractionSegmentedControlSpec(playerChromeProfile.compactChromeSpec)
    }
    val tabs = remember { listOf("聊天", "SC", "投票") }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val selectionBackdrop = rememberLayerBackdrop()

    LaunchedEffect(selectedTab) {
        val target = selectedTab.coerceIn(0, tabs.lastIndex)
        if (pagerState.currentPage != target) {
            animatePagerSelection(pagerState, target)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) {
            onSelectedTab(pagerState.currentPage)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(selectionBackdrop)
                .background(MaterialTheme.colorScheme.background),
        )
        Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = segmentedSpec.horizontalPaddingDp.dp,
                    vertical = segmentedSpec.verticalPaddingDp.dp
                )
        ) {
            AppThemeAdaptiveTabRow(
                options = tabs.mapIndexed { index, label -> AppSegmentOption(index, label) },
                selectedValue = pagerState.currentPage,
                onSelectionChange = { index ->
                    onSelectedTab(index)
                },
                modifier = Modifier.fillMaxWidth(),
                height = segmentedSpec.heightDp.dp,
                indicatorHeight = segmentedSpec.indicatorHeightDp.dp,
                labelFontSize = segmentedSpec.labelFontSizeSp.sp,
                miuixBackdrop = selectionBackdrop,
                dragSelectionEnabled = tabs.size > 1,
                tapPressRefractionEnabled = true,
                indicatorPositionProvider = {
                    pagerState.currentPage + pagerState.currentPageOffsetFraction
                },
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> Box(modifier = Modifier.fillMaxSize()) { chatContent() }
                1 -> Box(modifier = Modifier.fillMaxSize()) { superChatContent() }
                else -> Box(modifier = Modifier.fillMaxSize()) { voteContent() }
            }
        }
        }
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("定时关闭") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
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
            }
        },
        confirmButton = {
            if (activeTargetMillis != null) {
                AppTextButton(onClick = onCancelTimer) { AppText("取消定时") }
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { AppText("关闭") }
        }
    )
}
