// 文件路径: feature/video/VideoPlayerOverlay.kt
package com.android.purebilibili.feature.video.ui.overlay

import android.content.ClipData
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.android.purebilibili.core.store.DanmakuPanelWidthMode
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.util.FormatUtils
// Import reusable components from standalone files
import com.android.purebilibili.feature.video.ui.components.QualitySelectionMenu
import com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenuDialog
import com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.AspectRatioMenu
import com.android.purebilibili.feature.video.ui.components.VideoSettingsPanel
import com.android.purebilibili.feature.video.ui.components.ChapterListPanel
import com.android.purebilibili.feature.video.ui.components.PagesSelector
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.data.repository.VideoRepository
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.BottomProgressBehavior
import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.util.ShareUtils
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.Logger

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import io.github.alexzhirkevich.cupertino.icons.filled.HandThumbsup
import io.github.alexzhirkevich.cupertino.icons.outlined.HandThumbsup
import io.github.alexzhirkevich.cupertino.icons.outlined.HandThumbsup
import com.android.purebilibili.core.ui.AppIcons
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.video.usecase.playPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction
import com.android.purebilibili.feature.video.playback.policy.shouldHoldPlaybackTransitionPosition
import com.android.purebilibili.feature.cast.DeviceListDialog
import com.android.purebilibili.feature.cast.DlnaManager
import com.android.purebilibili.feature.cast.LocalProxyServer
import com.android.purebilibili.feature.cast.SsdpCastClient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionModuleState
import com.android.purebilibili.feature.video.playback.session.PendingPlaybackUserAction
import dev.chrisbanes.haze.HazeState

internal fun shouldShowEpisodeEntryFromVideoData(
    relatedVideosCount: Int,
    hasSeasonEpisodes: Boolean,
    pagesCount: Int
): Boolean {
    return pagesCount > 1 || relatedVideosCount > 0 || hasSeasonEpisodes
}

internal data class NextEpisodeTarget(
    val nextPageIndex: Int? = null,
    val nextBvid: String? = null
)

internal fun resolveNextEpisodeTarget(
    pagesCount: Int,
    currentPageIndex: Int,
    seasonEpisodeBvids: List<String>,
    currentBvid: String,
    relatedBvids: List<String>
): NextEpisodeTarget? {
    if (pagesCount > 1 && currentPageIndex in 0 until (pagesCount - 1)) {
        return NextEpisodeTarget(nextPageIndex = currentPageIndex + 1)
    }
    if (seasonEpisodeBvids.isNotEmpty()) {
        val currentIndex = seasonEpisodeBvids.indexOf(currentBvid)
        if (currentIndex in 0 until seasonEpisodeBvids.lastIndex) {
            return NextEpisodeTarget(nextBvid = seasonEpisodeBvids[currentIndex + 1])
        }
    }
    val nextRelated = relatedBvids.firstOrNull { it != currentBvid }
    if (!nextRelated.isNullOrBlank()) {
        return NextEpisodeTarget(nextBvid = nextRelated)
    }
    return null
}

internal fun shouldConsumeBackgroundGesturesForEndDrawer(
    endDrawerVisible: Boolean
): Boolean {
    return endDrawerVisible
}

internal fun shouldReleaseCastBindingAfterDialogVisibilityChange(
    previousVisible: Boolean,
    currentVisible: Boolean
): Boolean {
    return previousVisible && !currentVisible
}

internal fun shouldPollInlineVideoOverlayProgress(
    playerExists: Boolean,
    hostLifecycleStarted: Boolean
): Boolean {
    return playerExists && hostLifecycleStarted
}

internal fun resolveOverlayPlaybackButtonPlayingState(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)
}

internal fun shouldShowCenterPlayButton(
    isVisible: Boolean,
    isPlaying: Boolean,
    isQualitySwitching: Boolean,
    isFullscreen: Boolean,
    isBuffering: Boolean,
    isScrubbing: Boolean,
    isSeekTransitionPending: Boolean
): Boolean {
    return isVisible &&
        !isPlaying &&
        !isQualitySwitching &&
        isFullscreen &&
        !isBuffering &&
        !isScrubbing &&
        !isSeekTransitionPending
}

internal fun shouldShowBufferingIndicator(
    isBuffering: Boolean,
    isQualitySwitching: Boolean,
    isVisible: Boolean,
    isScrubbing: Boolean
): Boolean {
    return isBuffering && !isQualitySwitching && (!isVisible || isScrubbing)
}

internal fun resolvePageSelectorSheetOuterBottomPaddingDp(
    isFullscreen: Boolean
): Int {
    return if (isFullscreen) 0 else 8
}

internal fun shouldShowPersistentBottomProgressBar(
    controlsVisible: Boolean,
    isFullscreen: Boolean,
    behavior: BottomProgressBehavior
): Boolean {
    if (controlsVisible) return false
    return when (behavior) {
        BottomProgressBehavior.ALWAYS_SHOW -> true
        BottomProgressBehavior.ALWAYS_HIDE -> false
        BottomProgressBehavior.ONLY_SHOW_FULLSCREEN -> isFullscreen
        BottomProgressBehavior.ONLY_HIDE_FULLSCREEN -> !isFullscreen
    }
}

internal fun resolveDisplayedOnlineCount(
    onlineCount: String,
    showOnlineCount: Boolean
): String {
    return if (showOnlineCount) onlineCount else ""
}

private const val CENTER_PLAY_BUTTON_SEEK_TRANSITION_GRACE_MS = 350L

@Composable
fun VideoPlayerOverlay(
    player: Player,
    title: String,
    isVisible: Boolean,
    onToggleVisible: () -> Unit,
    isFullscreen: Boolean,
    currentQualityLabel: String,
    qualityLabels: List<String>,
    qualityIds: List<Int> = emptyList(),
    isLoggedIn: Boolean = false,
    onQualitySelected: (Int) -> Unit,

    onBack: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onToggleFullscreen: () -> Unit,
    // [New] Player Data for Download
    bvid: String = "",
    cid: Long = 0L,
    videoOwnerName: String = "",
    videoOwnerFace: String = "",
    videoDuration: Long = 0L,
    videoTitle: String = "",
    currentAid: Long = 0L,
    currentQuality: Int = 80,
    currentVideoUrl: String = "",
    currentAudioUrl: String = "", 
    // 🔒 [新增] 屏幕锁定
    isScreenLocked: Boolean = false,
    onLockToggle: () -> Unit = {},
    showStats: Boolean = false,
    debugInfo: PlaybackDebugInfo = PlaybackDebugInfo(),
    diagnosticEvents: List<String> = emptyList(),
    pendingUserAction: PendingPlaybackUserAction? = null,
    playerDiagnosticLoggingEnabled: Boolean = true,
    realResolution: String = "",
    isQualitySwitching: Boolean = false,
    isBuffering: Boolean = false,  // 缓冲状态
    isVip: Boolean = false,
    //  [新增] 弹幕开关和设置
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: () -> Unit = {},
    onDanmakuInputClick: () -> Unit = {},
    danmakuOpacity: Float = 0.85f,
    danmakuFontScale: Float = 1.0f,
    danmakuSpeed: Float = 1.0f,
    danmakuDisplayArea: Float = 0.5f,
    danmakuMergeDuplicates: Boolean = true,
    danmakuAllowScroll: Boolean = true,
    danmakuAllowTop: Boolean = true,
    danmakuAllowBottom: Boolean = true,
    danmakuAllowColorful: Boolean = true,
    danmakuAllowSpecial: Boolean = true,
    danmakuBlockRulesRaw: String = "",
    danmakuSmartOcclusion: Boolean = true,
    danmakuFullscreenPanelWidthMode: DanmakuPanelWidthMode = DanmakuPanelWidthMode.THIRD,
    onDanmakuOpacityChange: (Float) -> Unit = {},
    onDanmakuFontScaleChange: (Float) -> Unit = {},
    onDanmakuSpeedChange: (Float) -> Unit = {},
    onDanmakuDisplayAreaChange: (Float) -> Unit = {},
    onDanmakuMergeDuplicatesChange: (Boolean) -> Unit = {},
    onDanmakuAllowScrollChange: (Boolean) -> Unit = {},
    onDanmakuAllowTopChange: (Boolean) -> Unit = {},
    onDanmakuAllowBottomChange: (Boolean) -> Unit = {},
    onDanmakuAllowColorfulChange: (Boolean) -> Unit = {},
    onDanmakuAllowSpecialChange: (Boolean) -> Unit = {},
    onDanmakuBlockRulesRawChange: (String) -> Unit = {},
    onDanmakuSmartOcclusionChange: (Boolean) -> Unit = {},
    onDanmakuFullscreenPanelWidthModeChange: (DanmakuPanelWidthMode) -> Unit = {},
    subtitleControlState: SubtitleControlUiState = SubtitleControlUiState(),
    subtitleControlCallbacks: SubtitleControlCallbacks = SubtitleControlCallbacks(),
    smartOcclusionModuleState: FaceOcclusionModuleState = FaceOcclusionModuleState.Checking,
    smartOcclusionDownloadProgress: Int? = null,
    onDanmakuSmartOcclusionDownloadClick: () -> Unit = {},
    //  [实验性功能] 双击点赞
    doubleTapLikeEnabled: Boolean = true,
    onDoubleTapLike: () -> Unit = {},
    //  视频比例调节
    currentAspectRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    onAspectRatioChange: (VideoAspectRatio) -> Unit = {},
    // 🔗 [新增] 分享功能 (Moved bvid to top)
    onShare: (() -> Unit)? = null,
    showDislikeAction: Boolean = true,
    // [New] Cover URL for Download
    coverUrl: String = "",
    //  [新增] 视频设置面板回调
    onReloadVideo: () -> Unit = {},
    sleepTimerMinutes: Int? = null,
    onSleepTimerChange: (Int?) -> Unit = {},
    isFlippedHorizontal: Boolean = false,
    isFlippedVertical: Boolean = false,
    onFlipHorizontal: () -> Unit = {},
    onFlipVertical: () -> Unit = {},
    isAudioOnly: Boolean = false,
    onAudioOnlyToggle: () -> Unit = {},
    //  [新增] 画质列表和回调
    onQualityChange: (Int, Long) -> Unit = { _, _ -> },
    //  [新增] CDN 线路切换
    currentCdnIndex: Int = 0,
    cdnCount: Int = 1,
    onSwitchCdn: () -> Unit = {},
    onSwitchCdnTo: (Int) -> Unit = {},
    // 🖼️ [新增] 视频预览图数据
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    // 📖 [新增] 视频章节数据
    viewPoints: List<ViewPoint> = emptyList(),
    sponsorMarkers: List<SponsorProgressMarker> = emptyList(),
    // 📱 [新增] 竖屏全屏模式
    isVerticalVideo: Boolean = false,
    onPortraitFullscreen: () -> Unit = {},
    // 📲 [新增] 小窗模式
    onPipClick: () -> Unit = {},
    //  [新增] 拖动进度条开始回调（用于清除弹幕）
    onSeekStart: () -> Unit = {},
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    //  [新增] 外部可接管 seek 行为（用于同步弹幕等）
    onSeekTo: ((Long) -> Unit)? = null,
    previewSeekPositionMs: Long? = null,
    previewSeekActive: Boolean = false,
    playbackTransitionPositionMs: Long? = null,
    // [New] Codec & Audio Params
    currentCodec: String = "hev1",
    onCodecChange: (String) -> Unit = {},
    currentSecondCodec: String = "avc1",
    onSecondCodecChange: (String) -> Unit = {},
    currentAudioQuality: Int = -1,
    onAudioQualityChange: (Int) -> Unit = {},
    // [New] AI Audio Translation
    aiAudioInfo: com.android.purebilibili.data.model.response.AiAudioInfo? = null,
    currentAudioLang: String? = null,
    onAudioLangChange: (String) -> Unit = {},
    // 👀 [新增] 在线观看人数
    onlineCount: String = "",
    // [New Actions]
    onSaveCover: () -> Unit = {},
    onCaptureScreenshot: () -> Unit = {},
    onDownloadAudio: () -> Unit = {},
    // 🔁 [新增] 播放模式
    currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode = com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL,
    onPlayModeClick: () -> Unit = {},
    
    // [新增] 侧边栏抽屉数据与交互
    relatedVideos: List<com.android.purebilibili.data.model.response.RelatedVideo> = emptyList(),
    ugcSeason: com.android.purebilibili.data.model.response.UgcSeason? = null,
    isFollowed: Boolean = false,
    isLiked: Boolean = false,
    isCoined: Boolean = false,
    isFavorited: Boolean = false,
    likeCount: Long = 0L,
    favoriteCount: Long = 0L,
    coinCount: Int = 0,
    onToggleFollow: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    onCoin: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    // 复用 onRelatedVideoClick 或 onVideoClick
    onDrawerVideoClick: (String, android.os.Bundle?) -> Unit = { _, _ -> },
    onPlaybackTransitionSettled: (Long) -> Unit = {},
    // 分P
    pages: List<com.android.purebilibili.data.model.response.Page> = emptyList(),
    currentPageIndex: Int = 0,
    onPageSelect: (Int) -> Unit = {},
    drawerHazeState: HazeState? = null,
) {
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRatioMenu by remember { mutableStateOf(false) }
    // [新增] 侧边栏显示状态
    var showEndDrawer by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }  //  新增
    var showChapterList by remember { mutableStateOf(false) }  // 📖 章节列表
    var showCastDialog by remember { mutableStateOf(false) }   // 📺 投屏对话框
    var showPlaybackOrderSheet by remember { mutableStateOf(false) }
    var showPageSelectorSheet by remember { mutableStateOf(false) }
    var currentSpeed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    //  使用传入的比例状态
    var isPlaying by remember {
        mutableStateOf(
            resolveOverlayPlaybackButtonPlayingState(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState
            )
        )
    }
    var isProgressScrubbing by remember { mutableStateOf(false) }
    var suppressCenterPlayButtonForSeekTransition by remember { mutableStateOf(false) }
    var wasPlayingWhenProgressScrubbingStarted by remember { mutableStateOf(false) }
    var lastSettledPlaybackTransitionPositionMs by remember { mutableStateOf<Long?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val configuration = LocalConfiguration.current
    val effectiveDebugInfo = remember(debugInfo, realResolution) {
        if (debugInfo.resolution.isBlank() && realResolution.isNotBlank()) {
            debugInfo.copy(resolution = realResolution)
        } else {
            debugInfo
        }
    }
    val debugRows = remember(effectiveDebugInfo) {
        resolvePlaybackDebugRows(effectiveDebugInfo)
    }
    var bufferingStartedAtMs by remember(player) { mutableLongStateOf(0L) }
    var waitingFirstFrameStartedAtMs by remember(player, bvid, cid) { mutableLongStateOf(0L) }
    var playbackIssueSignal by remember(player, bvid, cid) { mutableStateOf<PlaybackIssueSignal?>(null) }
    var dismissedPlaybackIssueTypes by remember(player, bvid, cid) {
        mutableStateOf(setOf<PlaybackIssueType>())
    }
    val exportDiagnosticReport: (PlaybackIssueSignal?) -> String = remember(
        player,
        title,
        videoTitle,
        bvid,
        cid,
        effectiveDebugInfo,
        diagnosticEvents,
        pendingUserAction,
        playerDiagnosticLoggingEnabled
    ) {
        { issue ->
            buildPlaybackDiagnosticReport(
                title = videoTitle.ifBlank { title },
                bvid = bvid,
                cid = cid,
                currentPositionMs = player.currentPosition,
                bufferedPositionMs = player.bufferedPosition,
                debugInfo = effectiveDebugInfo,
                recentEvents = buildList {
                    issue?.let { add("detectedIssue=${it.type}") }
                    pendingUserAction?.let { action ->
                        add(
                            "pendingUserAction=${action.type} ageMs=${System.currentTimeMillis() - action.requestedAtMs}"
                        )
                    }
                    addAll(diagnosticEvents)
                }
            )
        }
    }
    LaunchedEffect(
        playerDiagnosticLoggingEnabled,
        player.playbackState,
        player.playWhenReady,
        effectiveDebugInfo.firstFrame
    ) {
        if (!playerDiagnosticLoggingEnabled) {
            bufferingStartedAtMs = 0L
            waitingFirstFrameStartedAtMs = 0L
            playbackIssueSignal = null
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        bufferingStartedAtMs = when {
            player.playbackState == Player.STATE_BUFFERING && player.playWhenReady ->
                if (bufferingStartedAtMs == 0L) now else bufferingStartedAtMs
            else -> 0L
        }
        waitingFirstFrameStartedAtMs = when {
            player.playbackState == Player.STATE_READY &&
                player.playWhenReady &&
                effectiveDebugInfo.firstFrame.isBlank() ->
                if (waitingFirstFrameStartedAtMs == 0L) now else waitingFirstFrameStartedAtMs
            else -> 0L
        }
        val currentSignal = resolvePlaybackIssueSignal(
            playbackState = player.playbackState,
            playWhenReady = player.playWhenReady,
            firstFrameRendered = effectiveDebugInfo.firstFrame.isNotBlank(),
            bufferingDurationMs = if (bufferingStartedAtMs > 0L) now - bufferingStartedAtMs else 0L,
            waitingFirstFrameDurationMs = if (waitingFirstFrameStartedAtMs > 0L) {
                now - waitingFirstFrameStartedAtMs
            } else {
                0L
            }
        )
        if (currentSignal == null) {
            playbackIssueSignal = null
        }
    }
    LaunchedEffect(
        player,
        playerDiagnosticLoggingEnabled,
        effectiveDebugInfo.firstFrame,
        bufferingStartedAtMs,
        waitingFirstFrameStartedAtMs,
        dismissedPlaybackIssueTypes,
        pendingUserAction
    ) {
        if (!playerDiagnosticLoggingEnabled) {
            playbackIssueSignal = null
            return@LaunchedEffect
        }
        while (isActive) {
            val now = System.currentTimeMillis()
            val playbackSignal = resolvePlaybackIssueSignal(
                playbackState = player.playbackState,
                playWhenReady = player.playWhenReady,
                firstFrameRendered = effectiveDebugInfo.firstFrame.isNotBlank(),
                bufferingDurationMs = if (bufferingStartedAtMs > 0L) now - bufferingStartedAtMs else 0L,
                waitingFirstFrameDurationMs = if (waitingFirstFrameStartedAtMs > 0L) {
                    now - waitingFirstFrameStartedAtMs
                } else {
                    0L
                }
            )
            val actionSignal = pendingUserAction?.let { action ->
                resolvePlaybackActionNoResponseSignal(
                    actionType = action.type,
                    actionAgeMs = now - action.requestedAtMs,
                    hasPlayerResponded = false
                )
            }
            val signal = actionSignal ?: playbackSignal
            if (signal != null && signal.type !in dismissedPlaybackIssueTypes) {
                playbackIssueSignal = signal
            }
            if (!shouldMonitorPlaybackIssues(
                    diagnosticsEnabled = playerDiagnosticLoggingEnabled,
                    bufferingStartedAtMs = bufferingStartedAtMs,
                    waitingFirstFrameStartedAtMs = waitingFirstFrameStartedAtMs,
                    hasPendingUserAction = pendingUserAction != null
                )
            ) {
                break
            }
            delay(1000)
        }
    }
    val showFullscreenLockButton by SettingsManager
        .getShowFullscreenLockButton(context)
        .collectAsState(initial = true)
    val showFullscreenScreenshotButton by SettingsManager
        .getShowFullscreenScreenshotButton(context)
        .collectAsState(initial = true)
    val showFullscreenBatteryLevel by SettingsManager
        .getShowFullscreenBatteryLevel(context)
        .collectAsState(initial = true)
    val showFullscreenTime by SettingsManager
        .getShowFullscreenTime(context)
        .collectAsState(initial = true)
    val showFullscreenActionItems by SettingsManager
        .getShowFullscreenActionItems(context)
        .collectAsState(initial = true)
    val showOnlineCount by SettingsManager
        .getShowOnlineCount(context)
        .collectAsState(initial = false)
    val bottomProgressBehavior by SettingsManager
        .getBottomProgressBehavior(context)
        .collectAsState(initial = BottomProgressBehavior.ALWAYS_SHOW)
    val displayedOnlineCount = remember(onlineCount, showOnlineCount) {
        resolveDisplayedOnlineCount(
            onlineCount = onlineCount,
            showOnlineCount = showOnlineCount
        )
    }
    val hasEpisodeEntry = remember(relatedVideos, ugcSeason) {
        shouldShowEpisodeEntryFromVideoData(
            relatedVideosCount = relatedVideos.size,
            hasSeasonEpisodes = ugcSeason?.sections?.any { section ->
                section.episodes.isNotEmpty()
            } == true,
            pagesCount = pages.size
        )
    }
    val nextEpisodeTarget = remember(
        pages,
        currentPageIndex,
        ugcSeason,
        bvid,
        relatedVideos
    ) {
        resolveNextEpisodeTarget(
            pagesCount = pages.size,
            currentPageIndex = currentPageIndex,
            seasonEpisodeBvids = ugcSeason?.sections
                ?.flatMap { section -> section.episodes }
                ?.map { episode -> episode.bvid }
                .orEmpty(),
            currentBvid = bvid,
            relatedBvids = relatedVideos.map { it.bvid }
        )
    }
    val playbackCompletionBehavior by SettingsManager
        .getPlaybackCompletionBehavior(context)
        .collectAsState(initial = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC)

    DisposableEffect(player) {
        currentSpeed = player.playbackParameters.speed
        val speedListener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                currentSpeed = playbackParameters.speed
            }
        }
        player.addListener(speedListener)
        onDispose { player.removeListener(speedListener) }
    }
    

    //  双击检测状态
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    var previousShowCastDialog by remember { mutableStateOf(false) }
    val overlayVisualPolicy = remember(configuration.screenWidthDp) {
        resolveVideoPlayerOverlayVisualPolicy(
            widthDp = configuration.screenWidthDp
        )
    }

    // 📺 [DLNA] 按需权限请求
    val dlnaPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.all { it }
        if (isGranted) {
            // 权限授予，绑定服务并显示对话框
            DlnaManager.bindService(context)
            DlnaManager.refresh() // 刷新列表
            showCastDialog = true
        } else {
            // 权限被拒绝，提示用户（可选）
             com.android.purebilibili.core.util.Logger.d("VideoPlayerOverlay", "DLNA permissions denied")
        }
    }

    val onCastClickAction = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: NEARBY_WIFI_DEVICES
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 DlnaManager.bindService(context)
                 DlnaManager.refresh()
                 showCastDialog = true
            } else {
                dlnaPermissionLauncher.launch(arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES))
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12: ACCESS_FINE_LOCATION
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 DlnaManager.bindService(context)
                 DlnaManager.refresh()
                 showCastDialog = true
            } else {
                dlnaPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
            }
        } else {
            // Android 11-: 无需运行时权限（除了 Internet/WifiState）
            DlnaManager.bindService(context)
            DlnaManager.refresh()
            showCastDialog = true
        }
    }

    val progressState by produceState(
        initialValue = PlayerProgress(),
        key1 = player,
        key2 = isVisible,
        key3 = hostLifecycleStarted
    ) {
        if (!shouldPollInlineVideoOverlayProgress(
                playerExists = true,
                hostLifecycleStarted = hostLifecycleStarted
            )
        ) {
            val duration = resolveSeekableDurationMs(
                playbackDurationMs = player.duration,
                fallbackDurationMs = videoDuration
            )
            value = PlayerProgress(
                current = player.currentPosition,
                duration = duration,
                buffered = player.bufferedPosition
            )
            isPlaying = resolveOverlayPlaybackButtonPlayingState(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState
            )
            return@produceState
        }
        while (isActive) {
            //  [修复] 始终更新进度，不仅在播放时
            // 这样横竖屏切换后也能显示正确的进度
            val duration = resolveSeekableDurationMs(
                playbackDurationMs = player.duration,
                fallbackDurationMs = videoDuration
            )
            value = PlayerProgress(
                current = player.currentPosition,
                duration = duration,
                buffered = player.bufferedPosition
            )
            isPlaying = resolveOverlayPlaybackButtonPlayingState(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState
            )
            val delayMs = if (isVisible && player.isPlaying) 200L else 500L
            delay(delayMs)
        }
    }
    val displayedProgressState = remember(
        progressState,
        previewSeekPositionMs,
        previewSeekActive,
        playbackTransitionPositionMs
    ) {
        resolveDisplayedPlayerProgress(
            progress = progressState,
            previewPositionMs = previewSeekPositionMs,
            previewActive = previewSeekActive,
            playbackTransitionPositionMs = playbackTransitionPositionMs
        )
    }

    LaunchedEffect(playbackTransitionPositionMs) {
        if (playbackTransitionPositionMs == null) {
            lastSettledPlaybackTransitionPositionMs = null
        }
    }

    LaunchedEffect(progressState.current, playbackTransitionPositionMs) {
        val pendingPositionMs = playbackTransitionPositionMs ?: return@LaunchedEffect
        if (pendingPositionMs == lastSettledPlaybackTransitionPositionMs) return@LaunchedEffect
        if (!shouldHoldPlaybackTransitionPosition(progressState.current, pendingPositionMs)) {
            lastSettledPlaybackTransitionPositionMs = pendingPositionMs
            onPlaybackTransitionSettled(pendingPositionMs)
        }
    }
    
    // 📖 计算当前章节（必须在 progressState 之后定义）
    val currentChapter = remember(progressState.current, viewPoints) {
        if (viewPoints.isEmpty()) null
        else viewPoints.lastOrNull { progressState.current >= it.fromMs }?.content
    }

    LaunchedEffect(isVisible, isPlaying) {
        if (isVisible && isPlaying) {
            delay(4000)
            if (isVisible) {
                onToggleVisible()
            }
        }
    }
    
    //  双击点赞动画自动消失
    LaunchedEffect(showLikeAnimation) {
        if (showLikeAnimation) {
            delay(800)
            showLikeAnimation = false
        }
    }

    LaunchedEffect(suppressCenterPlayButtonForSeekTransition) {
        if (suppressCenterPlayButtonForSeekTransition) {
            delay(CENTER_PLAY_BUTTON_SEEK_TRANSITION_GRACE_MS)
            suppressCenterPlayButtonForSeekTransition = false
        }
    }

    LaunchedEffect(isPlaying, isBuffering, isProgressScrubbing, suppressCenterPlayButtonForSeekTransition) {
        if (
            suppressCenterPlayButtonForSeekTransition &&
            !isProgressScrubbing &&
            (isPlaying || isBuffering)
        ) {
            suppressCenterPlayButtonForSeekTransition = false
        }
    }

    LaunchedEffect(showCastDialog) {
        if (shouldReleaseCastBindingAfterDialogVisibilityChange(previousShowCastDialog, showCastDialog)) {
            DlnaManager.unbindService(context)
        }
        previousShowCastDialog = showCastDialog
    }

    fun togglePlayPause() {
        if (player.playbackState == Player.STATE_ENDED) {
            onSeekTo?.invoke(0L) ?: player.seekTo(0L)
            playPlayerFromUserAction(player)
            isPlaying = true
        } else if (isPlaying) {
            togglePlayerPlaybackFromUserAction(player)
            isPlaying = false
        } else {
            playPlayerFromUserAction(player)
            isPlaying = true
        }
    }

    val commitSeek: (Long) -> Unit = { position ->
        val safePosition = position.coerceAtLeast(0L)
        onSeekTo?.invoke(safePosition) ?: seekPlayerFromUserAction(player, safePosition)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // --- 1. 顶部渐变遮罩 ---
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            //  [修复] align 必须在 AnimatedVisibility 的 modifier 上，而不是内部 Box 上
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(overlayVisualPolicy.topScrimHeightDp.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // --- 2. 底部渐变遮罩 ---
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(overlayVisualPolicy.bottomScrimHeightDp.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                )
            )
        }

        // 控件隐藏时保留底部细进度条，便于持续感知当前播放进度
        if (
            shouldShowPersistentBottomProgressBar(
                controlsVisible = isVisible,
                isFullscreen = isFullscreen,
                behavior = bottomProgressBehavior
            )
        ) {
            PersistentBottomProgressBar(
                current = displayedProgressState.current,
                duration = displayedProgressState.duration,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // --- 3. 控制栏内容 (锁定时隐藏) ---
        AnimatedVisibility(
            visible = isVisible && !isScreenLocked,  // 🔒 锁定时隐藏控制栏
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            //  [修复] 确保 AnimatedVisibility 填充整个父容器
            modifier = Modifier.fillMaxSize()
        ) {
            //  [修复] 使用 Box 分别定位顶部和底部控制栏
            Box(modifier = Modifier.fillMaxSize()) {
                //  顶部控制栏 - 仅在横屏（全屏）模式显示标题和清晰度
                if (isFullscreen) {
                    TopControlBar(
                        title = title,
                        onlineCount = displayedOnlineCount,
                        isFullscreen = isFullscreen,
                        showBatteryLevel = showFullscreenBatteryLevel,
                        showCurrentTime = showFullscreenTime,
                        showInteractiveActions = showFullscreenActionItems,
                        onBack = onBack,
                        // Interactions
                        isLiked = isLiked,
                        isCoined = isCoined,
                        allowDislikeAction = showDislikeAction,
                        onLikeClick = onToggleLike,
                        onDislikeClick = onDislike,
                        onCoinClick = onCoin,
                        onShareClick = {
                            if (onShare != null) {
                                onShare()
                            } else if (bvid.isNotEmpty()) {
                                ShareUtils.shareVideo(context, title, bvid)
                            }
                        },
                        onCastClick = onCastClickAction,
                        onMoreClick = { showEndDrawer = true },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else {
                    //  [新增] 竖屏模式顶部栏（返回 + 画质 + 设置 + 分享按钮）
                    val context = LocalContext.current
                    PortraitTopBar(
                        onlineCount = displayedOnlineCount,
                        onBack = onBack,
                        onHome = onHomeClick,
                        onSettings = { showVideoSettings = true },
                        onShare = onShare ?: {
                            if (bvid.isNotEmpty()) {
                                ShareUtils.shareVideo(context, title, bvid)
                            }
                        },
                        onAudioMode = onAudioOnlyToggle,
                        isAudioOnly = isAudioOnly,
                        //  [新增] 投屏按钮
                        onCastClick = onCastClickAction,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                
                //  [修复] 底部控制栏 - 固定在底部
                BottomControlBar(
                    isPlaying = isPlaying,
                    progress = displayedProgressState,
                    isFullscreen = isFullscreen,
                    currentSpeed = currentSpeed,
                    currentRatio = currentAspectRatio,
                    onPlayPauseClick = {
                        togglePlayPause()
                    },
                    onSeek = commitSeek,
                    onSeekStart = onSeekStart,  //  拖动进度条开始时清除弹幕
                    onSeekDragStart = onSeekDragStart,
                    onSeekDragUpdate = onSeekDragUpdate,
                    onSeekDragCancel = onSeekDragCancel,
                    onScrubbingChanged = { scrubbing ->
                        if (scrubbing) {
                            wasPlayingWhenProgressScrubbingStarted = isPlaying
                            suppressCenterPlayButtonForSeekTransition = false
                        } else if (wasPlayingWhenProgressScrubbingStarted) {
                            suppressCenterPlayButtonForSeekTransition = true
                        }
                        isProgressScrubbing = scrubbing
                    },
                    seekPositionMs = displayedProgressState.current,
                    isSeekScrubbing = isProgressScrubbing,
                    onSpeedClick = { showSpeedMenu = true },
                    onRatioClick = { showRatioMenu = true },
                    onNextEpisodeClick = {
                        val target = nextEpisodeTarget
                        when {
                            target?.nextPageIndex != null -> onPageSelect(target.nextPageIndex)
                            !target?.nextBvid.isNullOrBlank() -> onDrawerVideoClick(target?.nextBvid ?: "", null)
                        }
                    },
                    hasNextEpisode = nextEpisodeTarget != null,
                    onEpisodeClick = {
                        if (pages.size > 1) {
                            showPageSelectorSheet = true
                        } else {
                            showEndDrawer = true
                        }
                    },
                    hasEpisodeEntry = hasEpisodeEntry,
                    onToggleFullscreen = onToggleFullscreen,
                    //  [新增] 竖屏模式弹幕和清晰度控制
                    danmakuEnabled = danmakuEnabled,
                    onDanmakuToggle = onDanmakuToggle,
                    onDanmakuInputClick = onDanmakuInputClick,
                    onDanmakuSettingsClick = { showDanmakuSettings = true },
                    subtitleControlState = subtitleControlState,
                    subtitleControlCallbacks = subtitleControlCallbacks,
                    currentQualityLabel = currentQualityLabel,
                    onQualityClick = { showQualityMenu = true },
                    // 🖼️ [新增] 视频预览图数据
                    videoshotData = videoshotData,
                    // 📖 [新增] 视频章节数据
                    viewPoints = viewPoints,
                    sponsorMarkers = sponsorMarkers,
                    currentChapter = currentChapter,
                    onChapterClick = { showChapterList = true },
                    // 📱 [新增] 竖屏全屏模式
                    isVerticalVideo = isVerticalVideo,
                    onPortraitFullscreen = onPortraitFullscreen,
                    // 📲 [新增] 小窗模式
                    onPipClick = onPipClick,
                    // 🔁 [新增] 播放模式
                    currentPlayMode = currentPlayMode,
                    onPlayModeClick = onPlayModeClick,
                    playbackOrderLabel = resolvePlaybackOrderDisplayLabel(
                        behavior = playbackCompletionBehavior,
                        compact = !isFullscreen
                    ),
                    onPlaybackOrderClick = { showPlaybackOrderSheet = true },
                    //  [修复] 传入 modifier 确保在底部
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
        
        // --- 3.5 🔒 [新增] 屏幕锁定按钮 (仅全屏模式) ---
        if (isFullscreen && showFullscreenLockButton) {
            AnimatedVisibility(
                visible = isVisible || isScreenLocked,  // 锁定时始终显示解锁按钮
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = overlayVisualPolicy.lockButtonEndPaddingDp.dp)
            ) {
                Surface(
                    onClick = onLockToggle,
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(overlayVisualPolicy.lockButtonCornerRadiusDp.dp),
                    modifier = Modifier.size(overlayVisualPolicy.lockButtonSizeDp.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            if (isScreenLocked) CupertinoIcons.Default.LockOpen else CupertinoIcons.Default.Lock,
                            contentDescription = if (isScreenLocked) "解锁" else "锁定",
                            tint = if (isScreenLocked) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(overlayVisualPolicy.lockIconSizeDp.dp)
                        )
                    }
                }
            }
        }

        if (isFullscreen && showFullscreenScreenshotButton) {
            AnimatedVisibility(
                visible = isVisible && !isScreenLocked,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = overlayVisualPolicy.lockButtonEndPaddingDp.dp)
            ) {
                Surface(
                    onClick = onCaptureScreenshot,
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(overlayVisualPolicy.lockButtonCornerRadiusDp.dp),
                    modifier = Modifier.size(overlayVisualPolicy.lockButtonSizeDp.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = CupertinoIcons.Default.Camera,
                            contentDescription = "截图",
                            tint = Color.White,
                            modifier = Modifier.size(overlayVisualPolicy.lockIconSizeDp.dp)
                        )
                    }
                }
            }
        }

        // --- 4.  [新增] 真实分辨率统计信息 (仅在设置开启时显示) ---
        if (showStats && debugRows.isNotEmpty() && isVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = overlayVisualPolicy.statsTopPaddingDp.dp,
                        end = overlayVisualPolicy.statsEndPaddingDp.dp
                    )
                    .background(
                        Color.Black.copy(0.6f),
                        RoundedCornerShape(overlayVisualPolicy.statsCornerRadiusDp.dp)
                    )
                    .padding(
                        horizontal = overlayVisualPolicy.statsHorizontalPaddingDp.dp,
                        vertical = overlayVisualPolicy.statsVerticalPaddingDp.dp
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Player stats",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = overlayVisualPolicy.statsFontSp.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (playerDiagnosticLoggingEnabled) {
                            Text(
                                text = "Copy diag",
                                color = BiliPink,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = overlayVisualPolicy.statsFontSp.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "BiliPai Player Diagnostics",
                                            exportDiagnosticReport(null)
                                        )
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        "播放器诊断已复制",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    debugRows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.widthIn(min = 164.dp)
                        ) {
                            Text(
                                text = row.label,
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = overlayVisualPolicy.statsFontSp.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = row.value,
                                color = Color(0xFF9BFFB0),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = overlayVisualPolicy.statsFontSp.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        if (playerDiagnosticLoggingEnabled) playbackIssueSignal?.let { signal ->
            AlertDialog(
                onDismissRequest = {
                    dismissedPlaybackIssueTypes = dismissedPlaybackIssueTypes + signal.type
                    playbackIssueSignal = null
                },
                title = {
                    Text(signal.title)
                },
                text = {
                    Text(signal.message)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val savedPath = Logger.exportPlayerDiagnostic(
                                context = context,
                                content = exportDiagnosticReport(signal)
                            )
                            if (savedPath != null) {
                                android.widget.Toast.makeText(
                                    context,
                                    "已导出到: $savedPath",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "导出失败，请稍后重试",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            dismissedPlaybackIssueTypes = dismissedPlaybackIssueTypes + signal.type
                            playbackIssueSignal = null
                        }
                    ) {
                        Text("导出日志")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dismissedPlaybackIssueTypes = dismissedPlaybackIssueTypes + signal.type
                            playbackIssueSignal = null
                        }
                    ) {
                        Text("关闭")
                    }
                }
            )
        }

        // --- 5. 中央播放/暂停大图标 (仅全屏模式显示) ---
        AnimatedVisibility(
            visible = shouldShowCenterPlayButton(
                isVisible = isVisible,
                isPlaying = isPlaying,
                isQualitySwitching = isQualitySwitching,
                isFullscreen = isFullscreen,
                isBuffering = isBuffering,
                isScrubbing = isProgressScrubbing,
                isSeekTransitionPending = suppressCenterPlayButtonForSeekTransition || playbackTransitionPositionMs != null
            ),
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(tween(250)) + fadeIn(tween(200)),
            exit = scaleOut(tween(200)) + fadeOut(tween(200))
        ) {
            OverlayPlaybackButton(
                isPlaying = false,
                onClick = { playPlayerFromUserAction(player); isPlaying = true },
                outerSize = overlayVisualPolicy.centerPlayButtonSizeDp.dp,
                innerSize = overlayVisualPolicy.centerPlayInnerButtonSizeDp.dp,
                glyphSize = overlayVisualPolicy.centerPlayIconSizeDp.dp
            )
        }

        // --- 5.4  缓冲加载指示器 ---
        AnimatedVisibility(
            visible = shouldShowBufferingIndicator(
                isBuffering = isBuffering,
                isQualitySwitching = isQualitySwitching,
                isVisible = isVisible,
                isScrubbing = isProgressScrubbing
            ),
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            CupertinoActivityIndicator()
        }

        // --- 5.5  清晰度切换中 Loading 指示器 ---
        AnimatedVisibility(
            visible = isQualitySwitching,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(overlayVisualPolicy.qualitySwitchCornerRadiusDp.dp),
                modifier = Modifier.padding(overlayVisualPolicy.qualitySwitchOuterPaddingDp.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(
                        horizontal = overlayVisualPolicy.qualitySwitchContentHorizontalPaddingDp.dp,
                        vertical = overlayVisualPolicy.qualitySwitchContentVerticalPaddingDp.dp
                    )
                ) {
                    //  iOS 风格加载器
                    CupertinoActivityIndicator()
                    Spacer(modifier = Modifier.height(overlayVisualPolicy.qualitySwitchContentSpacingDp.dp))
                    Text(
                        text = "正在切换清晰度...",
                        color = Color.White,
                        fontSize = overlayVisualPolicy.qualitySwitchMessageFontSp.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 6. 清晰度菜单 ---
        if (showQualityMenu) {
            QualitySelectionMenu(
                qualities = qualityLabels,
                qualityIds = qualityIds,
                currentQuality = currentQualityLabel,
                isLoggedIn = isLoggedIn,
                isVip = isVip,
                onQualitySelected = { index ->
                    onQualitySelected(index)
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false },
                useDialog = true
            )
        }
        
        // --- 7.  [新增] 倍速选择菜单 ---
        if (showSpeedMenu) {
            SpeedSelectionMenuDialog(
                currentSpeed = currentSpeed,
                onSpeedSelected = { speed ->
                    currentSpeed = speed
                    player.setPlaybackSpeed(speed)
                    scope.launch {
                        SettingsManager.setLastPlaybackSpeed(context, speed)
                    }
                    showSpeedMenu = false
                },
                onDismiss = { showSpeedMenu = false }
            )
        }
        
        // --- 7.5  [新增] 视频比例选择菜单 ---
        if (showRatioMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showRatioMenu = false },
                contentAlignment = Alignment.Center
            ) {
                AspectRatioMenu(
                    currentRatio = currentAspectRatio,
                    onRatioSelected = { ratio ->
                        onAspectRatioChange(ratio)
                        showRatioMenu = false
                    },
                    onDismiss = { showRatioMenu = false }
                )
            }
        }
        
        // --- 8.  [新增] 弹幕设置面板 ---
        if (showDanmakuSettings) {
            DanmakuSettingsPanel(
                isFullscreen = isFullscreen,
                settingsScope = com.android.purebilibili.core.store.resolveDanmakuSettingsScope(
                    isLandscape = isFullscreen
                ),
                opacity = danmakuOpacity,
                fontScale = danmakuFontScale,
                speed = danmakuSpeed,
                displayArea = danmakuDisplayArea,
                mergeDuplicates = danmakuMergeDuplicates,
                allowScroll = danmakuAllowScroll,
                allowTop = danmakuAllowTop,
                allowBottom = danmakuAllowBottom,
                allowColorful = danmakuAllowColorful,
                allowSpecial = danmakuAllowSpecial,
                showBlockRuleEditor = true,
                blockRulesRaw = danmakuBlockRulesRaw,
                smartOcclusion = danmakuSmartOcclusion,
                fullscreenWidthMode = danmakuFullscreenPanelWidthMode,
                smartOcclusionModuleState = smartOcclusionModuleState,
                smartOcclusionDownloadProgress = smartOcclusionDownloadProgress,
                onOpacityChange = onDanmakuOpacityChange,
                onFontScaleChange = onDanmakuFontScaleChange,
                onSpeedChange = onDanmakuSpeedChange,
                onDisplayAreaChange = onDanmakuDisplayAreaChange,
                onMergeDuplicatesChange = onDanmakuMergeDuplicatesChange,
                onAllowScrollChange = onDanmakuAllowScrollChange,
                onAllowTopChange = onDanmakuAllowTopChange,
                onAllowBottomChange = onDanmakuAllowBottomChange,
                onAllowColorfulChange = onDanmakuAllowColorfulChange,
                onAllowSpecialChange = onDanmakuAllowSpecialChange,
                onBlockRulesRawChange = onDanmakuBlockRulesRawChange,
                onSmartOcclusionChange = onDanmakuSmartOcclusionChange,
                onFullscreenWidthModeChange = onDanmakuFullscreenPanelWidthModeChange,
                onSmartOcclusionDownloadClick = onDanmakuSmartOcclusionDownloadClick,
                onDismiss = { showDanmakuSettings = false }
            )
        }
        
        // --- 9.  [新增] 视频设置面板 ---
        if (showVideoSettings) {
            VideoSettingsPanel(
                sleepTimerMinutes = sleepTimerMinutes,
                onSleepTimerChange = onSleepTimerChange,
                onReload = onReloadVideo,
                currentQualityLabel = currentQualityLabel,
                qualityLabels = qualityLabels,
                qualityIds = qualityIds,
                onQualitySelected = { index ->
                    val id = qualityIds.getOrNull(index) ?: 0
                    onQualityChange(id, 0L)  // 位置由上层处理
                    showVideoSettings = false
                },
                currentSpeed = currentSpeed,
                onSpeedChange = { speed ->
                    currentSpeed = speed
                    player.setPlaybackSpeed(speed)
                    scope.launch {
                        SettingsManager.setLastPlaybackSpeed(context, speed)
                    }
                },
                isFlippedHorizontal = isFlippedHorizontal,
                isFlippedVertical = isFlippedVertical,
                onFlipHorizontal = onFlipHorizontal,
                onFlipVertical = onFlipVertical,
                isAudioOnly = isAudioOnly,
                onAudioOnlyToggle = onAudioOnlyToggle,
                //  CDN 线路切换
                currentCdnIndex = currentCdnIndex,
                cdnCount = cdnCount,
                onSwitchCdn = onSwitchCdn,
                onSwitchCdnTo = { index ->
                    onSwitchCdnTo(index)
                    showVideoSettings = false
                },
                // [New] Codec & Audio
                currentCodec = currentCodec,
                onCodecChange = { codec ->
                    onCodecChange(codec)
                    showVideoSettings = false
                },
                currentSecondCodec = currentSecondCodec,
                onSecondCodecChange = { codec ->
                    onSecondCodecChange(codec)
                    showVideoSettings = false
                },
                currentAudioQuality = currentAudioQuality,
                onAudioQualityChange = { quality ->
                    onAudioQualityChange(quality)
                    showVideoSettings = false
                },
                // [New] AI Audio
                aiAudioInfo = aiAudioInfo,
                currentAudioLang = currentAudioLang,
                onAudioLangChange = { lang ->
                    onAudioLangChange(lang)
                    showVideoSettings = false
                },

                onSaveCover = {
                    onSaveCover()
                    // Disimss moved to VideoSettingsPanel internal or caller responsibility?
                    // VideoSettingsPanel calls onSaveCover then onDismiss.
                    // We just invoke the callback.
                },
                onCaptureScreenshot = {
                    onCaptureScreenshot()
                },
                onDownloadAudio = {
                    onDownloadAudio()
                },
                onDismiss = { showVideoSettings = false }
            )
        }

        if (showPlaybackOrderSheet) {
            PlaybackOrderSelectionSheet(
                currentBehavior = playbackCompletionBehavior,
                onSelect = { behavior ->
                    scope.launch {
                        SettingsManager.setPlaybackCompletionBehavior(context, behavior)
                    }
                    showPlaybackOrderSheet = false
                },
                onDismiss = { showPlaybackOrderSheet = false }
            )
        }
        
        // --- 10. 📖 [新增] 章节列表面板 ---
        if (showChapterList && viewPoints.isNotEmpty()) {
            ChapterListPanel(
                viewPoints = viewPoints,
                currentPositionMs = displayedProgressState.current,
                onSeek = commitSeek,
                onDismiss = { showChapterList = false }
            )
        }

        if (showPageSelectorSheet && pages.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showPageSelectorSheet = false
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            top = 8.dp,
                            end = 8.dp,
                            bottom = resolvePageSelectorSheetOuterBottomPaddingDp(isFullscreen).dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {}
                    ) {
                        PagesSelector(
                            pages = pages,
                            currentPageIndex = currentPageIndex,
                            forceGridMode = true,
                            onDismissRequest = { showPageSelectorSheet = false },
                            onPageSelect = { index ->
                                onPageSelect(index)
                                showPageSelectorSheet = false
                            }
                        )
                    }
                }
            }
        }

        if (shouldConsumeBackgroundGesturesForEndDrawer(showEndDrawer)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(showEndDrawer) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                change.consume()
                            }
                        )
                    }
            )
        }
        
        // --- 11. [新增] 侧边栏抽屉 ---
        LandscapeEndDrawer(
            visible = showEndDrawer,
            onDismiss = { showEndDrawer = false },
            relatedVideos = relatedVideos,
            ugcSeason = ugcSeason,
            currentBvid = bvid,
            currentCid = cid,
            ownerName = videoOwnerName,
            ownerFace = videoOwnerFace,
            isFollowed = isFollowed,
            onToggleFollow = onToggleFollow,
            onVideoClick = { vid, options ->
                onDrawerVideoClick(vid, options)
                showEndDrawer = false
            },
            hazeState = drawerHazeState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        
        // --- 12. 📺 投屏对话框 ---
        if (showCastDialog) {
            suspend fun resolveCastUrlOrNull(): String? = withContext(Dispatchers.IO) {
                val tvCastUrl = runCatching {
                    VideoRepository.getTvCastPlayUrl(
                        aid = currentAid,
                        cid = cid,
                        qn = currentQuality
                    )
                }.getOrNull()

                if (!tvCastUrl.isNullOrBlank()) {
                    return@withContext tvCastUrl
                }

                runCatching {
                    if (currentVideoUrl.isBlank()) return@runCatching null
                    LocalProxyServer.ensureStarted()
                    LocalProxyServer.getProxyUrl(context, currentVideoUrl)
                }.getOrNull()
            }

            DeviceListDialog(
                onDismissRequest = { showCastDialog = false },
                onDeviceSelected = { device ->
                    showCastDialog = false
                    scope.launch {
                        val castUrl = resolveCastUrlOrNull()
                        if (castUrl == null) {
                            android.widget.Toast.makeText(context, "投屏地址解析失败", android.widget.Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        DlnaManager.cast(device, castUrl, videoTitle, videoOwnerName)
                    }
                },
                onSsdpDeviceSelected = { ssdpDevice ->
                    showCastDialog = false
                    scope.launch {
                        val castUrl = resolveCastUrlOrNull()
                        if (castUrl == null) {
                            android.widget.Toast.makeText(context, "投屏地址解析失败", android.widget.Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val result = SsdpCastClient.cast(
                            device = ssdpDevice,
                            mediaUrl = castUrl,
                            title = videoTitle,
                            creator = videoOwnerName
                        )
                        val message = if (result.isSuccess) {
                            "已发送投屏指令"
                        } else {
                            "投屏失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                        }
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

/**
 *  竖屏模式顶部控制栏
 * 
 * 包含返回首页按钮、设置按钮和分享按钮
 */
@Composable
private fun PortraitTopBar(
    onlineCount: String = "",
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit,
    onAudioMode: () -> Unit,
    isAudioOnly: Boolean,
    // 📺 [新增] 投屏
    onCastClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolvePortraitTopBarLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = layoutPolicy.horizontalPaddingDp.dp,
                vertical = layoutPolicy.verticalPaddingDp.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：返回按钮 + 在线人数
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layoutPolicy.leftSectionSpacingDp.dp)
        ) {
            // 返回按钮 - 简洁无背景
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.ChevronBackward,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                )
            }

            IconButton(
                onClick = onHome,
                modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.House,
                    contentDescription = "主界面",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                )
            }
            
            // 👀 在线人数
            if (onlineCount.isNotEmpty()) {
                Text(
                    text = onlineCount,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = layoutPolicy.onlineCountFontSp.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 右侧按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(layoutPolicy.rightSectionSpacingDp.dp)
        ) {
            //  听视频模式按钮 - 激活时保留背景色
            IconButton(
                onClick = onAudioMode,
                modifier = Modifier
                    .size(layoutPolicy.buttonSizeDp.dp)
                    .then(
                        if (isAudioOnly) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    )
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.Headphones,
                    contentDescription = "听视频",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                )
            }

            // 📺 投屏按钮 - 无背景
            IconButton(
                onClick = onCastClick,
                modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
            ) {
                Icon(
                    imageVector = io.github.alexzhirkevich.cupertino.icons.CupertinoIcons.Default.Tv,
                    contentDescription = "投屏",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                )
            }
            
            //  设置按钮 - 无背景
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.Ellipsis,
                    contentDescription = "设置",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                )
            }
            
            // 分享按钮 - 无背景
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.SquareAndArrowUp,
                    contentDescription = "分享",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                )
            }
        }
    }
}

// --- 11. 侧边栏抽屉 (Landscape End Drawer) ---
@Composable
fun LandscapeEndDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    // Data
    relatedVideos: List<com.android.purebilibili.data.model.response.RelatedVideo>,
    ugcSeason: com.android.purebilibili.data.model.response.UgcSeason?,
    currentBvid: String,
    currentCid: Long,
    // UP Info
    ownerName: String,
    ownerFace: String,
    // Interaction States
    isFollowed: Boolean,
    // Callbacks
    onToggleFollow: () -> Unit,
    onVideoClick: (String, android.os.Bundle?) -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val widthSizeClass = remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp < 600 -> WindowWidthSizeClass.Compact
            configuration.screenWidthDp < 840 -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        }
    }
    val deviceUiProfile = remember(widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = widthSizeClass
        )
    }
    val cardAnimationEnabled by SettingsManager
        .getCardAnimationEnabled(context)
        .collectAsState(initial = true)
    val overlayMotionTier = resolveEffectiveMotionTier(
        baseTier = deviceUiProfile.motionTier,
        animationEnabled = cardAnimationEnabled
    )
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolveLandscapeEndDrawerLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    val useDarkDrawerOverlay = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val drawerOverlayColor = if (useDarkDrawerOverlay) {
        Color(0xCC11141A)
    } else {
        Color(0xBFF6F8FC)
    }
    val dividerColor = if (useDarkDrawerOverlay) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 点击空白处关闭
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            
            // 抽屉内容
            Surface(
                modifier = Modifier
                    .width(layoutPolicy.drawerWidthDp.dp)
                    .fillMaxHeight(),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (hazeState != null) {
                                Modifier.unifiedBlur(hazeState = hazeState)
                            } else {
                                Modifier
                            }
                        )
                        .background(drawerOverlayColor)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    // 1. 顶部信息区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(layoutPolicy.headerPaddingDp.dp)
                    ) {
                        // Row 1: UP主头像、名字、关注按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 头像
                            coil.compose.AsyncImage(
                                model = ownerFace,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(layoutPolicy.avatarSizeDp.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray),
                                contentScale = ContentScale.Crop
                            )
                            
                            Spacer(modifier = Modifier.width(layoutPolicy.headerSpacingDp.dp))
                            
                            // 名字
                            Text(
                                text = ownerName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = layoutPolicy.titleFontSp.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(layoutPolicy.headerSpacingDp.dp))
                            
                            // 关注按钮 (放在右上角)
                            Button(
                                onClick = onToggleFollow,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowed) MaterialTheme.colorScheme.onSurface.copy(0.2f) else MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = layoutPolicy.followButtonHorizontalPaddingDp.dp,
                                    vertical = 0.dp
                                ),
                                modifier = Modifier.height(layoutPolicy.followButtonHeightDp.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    if (isFollowed) "已关注" else "+ 关注",
                                    fontSize = layoutPolicy.followButtonFontSp.sp
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(color = dividerColor)
                    
                    // 2. Tab Row
                    var selectedTab by remember { mutableIntStateOf(0) } // 0: 推荐, 1: 合集
                    val hasSeason = ugcSeason != null && ugcSeason.sections.isNotEmpty()
                    
                    if (hasSeason) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            indicator = { tabPositions ->
                                TabRowDefaults.Indicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("推荐视频", fontSize = layoutPolicy.followButtonFontSp.sp) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("合集列表", fontSize = layoutPolicy.followButtonFontSp.sp) }
                            )
                        }
                    } else {
                        // 只有推荐，显示标题
                        Text(
                            text = "推荐视频",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(layoutPolicy.listContentPaddingDp.dp)
                        )
                    }
                    
                    // 3. 列表内容
                    Box(modifier = Modifier.weight(1f)) {
                        if (selectedTab == 0) {
                            // 推荐视频列表
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(layoutPolicy.listContentPaddingDp.dp),
                                verticalArrangement = Arrangement.spacedBy(layoutPolicy.listItemSpacingDp.dp)
                            ) {
                                items(relatedVideos) { video ->
                                    LandscapeVideoItem(
                                        video = video,
                                        layoutPolicy = layoutPolicy,
                                        screenWidthDp = configuration.screenWidthDp,
                                        motionTier = overlayMotionTier,
                                        isCurrent = video.bvid == currentBvid,
                                        onClick = { onVideoClick(video.bvid, null) }
                                    )
                                }
                            }
                        } else if (hasSeason && ugcSeason != null) {
                            // 合集列表
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(layoutPolicy.listContentPaddingDp.dp),
                                verticalArrangement = Arrangement.spacedBy(layoutPolicy.listItemSpacingDp.dp)
                            ) {
                                ugcSeason.sections.forEach { section ->
                                    item {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    items(section.episodes) { episode ->
                                        LandscapeEpisodeItem(
                                            episode = episode,
                                            layoutPolicy = layoutPolicy,
                                            screenWidthDp = configuration.screenWidthDp,
                                            motionTier = overlayMotionTier,
                                            isCurrent = com.android.purebilibili.feature.video.ui.components.isCurrentUgcEpisode(
                                                currentBvid = currentBvid,
                                                currentCid = currentCid,
                                                episode = episode
                                            ),
                                            onClick = {
                                                onVideoClick(
                                                    episode.bvid,
                                                    com.android.purebilibili.feature.video.screen.buildVideoNavigationOptions(
                                                        targetCid = episode.cid
                                                    )
                                                )
                                            }
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
}
}

@Composable
private fun InteractionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

/**
 * 一键三连长按按钮 (横屏版) - 长按显示点赞、投币、收藏三个图标的圆形进度条
 */
@Composable
private fun TripleLikeInteractionButton(
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onTripleComplete: () -> Unit,
    layoutPolicy: VideoPlayerOverlayVisualPolicy,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    
    var isLongPressing by remember { mutableStateOf(false) }
    var longPressProgress by remember { mutableFloatStateOf(0f) }
    val progressDuration = 1500
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (isLongPressing) 1f else 0f,
        animationSpec = if (isLongPressing) {
            androidx.compose.animation.core.tween(durationMillis = progressDuration, easing = LinearEasing)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 200, easing = FastOutSlowInEasing)
        },
        label = "tripleLikeProgress",
        finishedListener = { progress ->
            if (progress >= 1f && isLongPressing) {
                haptic(HapticType.MEDIUM)
                onTripleComplete()
                isLongPressing = false
            }
        }
    )
    
    LaunchedEffect(animatedProgress) {
        longPressProgress = animatedProgress
    }
    
    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            haptic(HapticType.LIGHT)
        }
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(layoutPolicy.tripleActionSpacingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // 点赞
        LandscapeProgressIcon(
            icon = if (isLiked) CupertinoIcons.Filled.HandThumbsup else CupertinoIcons.Outlined.HandThumbsup,
            label = "点赞",
            progress = longPressProgress,
            progressColor = MaterialTheme.colorScheme.primary,
            isActive = isLiked,
            onClick = onLikeClick,
            onLongPress = { isLongPressing = true },
            onRelease = { 
                if (longPressProgress < 0.1f) onLikeClick()
                isLongPressing = false 
            },
            layoutPolicy = layoutPolicy
        )
        
        // 投币 (显示时带进度)
        LandscapeProgressIcon(
            icon = AppIcons.BiliCoin,
            label = "投币",
            progress = longPressProgress,
            progressColor = Color(0xFFFFB300),
            isActive = isCoined,
            onClick = onCoinClick,
            showProgress = longPressProgress > 0.05f,
            layoutPolicy = layoutPolicy
        )
        
        // 收藏 (显示时带进度)
        LandscapeProgressIcon(
            icon = if (isFavorited) CupertinoIcons.Filled.Star else CupertinoIcons.Default.Star,
            label = "收藏",
            progress = longPressProgress,
            progressColor = Color(0xFFFFC107),
            isActive = isFavorited,
            onClick = onFavoriteClick,
            showProgress = longPressProgress > 0.05f,
            layoutPolicy = layoutPolicy
        )
    }
}

/**
 * 横屏带进度环的交互图标
 */
@Composable
private fun LandscapeProgressIcon(
    icon: ImageVector,
    label: String,
    progress: Float,
    progressColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    showProgress: Boolean = true,
    layoutPolicy: VideoPlayerOverlayVisualPolicy,
    modifier: Modifier = Modifier
) {
    val iconSize = layoutPolicy.interactionIconSizeDp.dp
    val ringSize = iconSize + layoutPolicy.tripleRingExtraSizeDp.dp
    val strokeWidth = 2.5.dp
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(
                if (onLongPress != null && onRelease != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onLongPress()
                                tryAwaitRelease()
                                onRelease()
                            }
                        )
                    }
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                }
            )
    ) {
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            if (showProgress && progress > 0f) {
                Canvas(modifier = Modifier.size(ringSize)) {
                    val stroke = strokeWidth.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                    
                    drawArc(
                        color = progressColor.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) progressColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize)
            )
        }
        
        Spacer(modifier = Modifier.height(layoutPolicy.interactionLabelTopSpacingDp.dp))
        Text(
            text = label,
            color = if (isActive) progressColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = layoutPolicy.interactionLabelFontSp.sp
        )
    }
}

@Composable
private fun LandscapeVideoItem(
    video: com.android.purebilibili.data.model.response.RelatedVideo,
    layoutPolicy: LandscapeEndDrawerLayoutPolicy,
    screenWidthDp: Int,
    motionTier: MotionTier,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(layoutPolicy.videoItemHeightDp.dp)
            .clickable(onClick = onClick)
            .background(if (isCurrent) MaterialTheme.colorScheme.onSurface.copy(0.1f) else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
        ) {
             coil.compose.AsyncImage(
                model = video.pic,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 时长
            Text(
                text = FormatUtils.formatDuration(video.duration),
                color = Color.White,
                fontSize = layoutPolicy.itemDurationFontSp.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 2.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 标题和UP
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = video.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = layoutPolicy.itemTitleFontSp.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = (layoutPolicy.itemTitleFontSp + 3).sp
            )
            Text(
                text = video.owner.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = layoutPolicy.itemMetaFontSp.sp
            )
        }
    }
}

@Composable
private fun LandscapeEpisodeItem(
    episode: com.android.purebilibili.data.model.response.UgcEpisode,
    layoutPolicy: LandscapeEndDrawerLayoutPolicy,
    screenWidthDp: Int,
    motionTier: MotionTier,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(layoutPolicy.episodeItemHeightDp.dp)
            .clickable(onClick = onClick)
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 封面 (如果有 arc 信息)
        if (episode.arc != null && episode.arc.pic.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .aspectRatio(16f / 9f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                coil.compose.AsyncImage(
                    model = episode.arc.pic,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 时长
                Text(
                    text = FormatUtils.formatDuration(episode.arc.duration),
                    color = Color.White,
                    fontSize = layoutPolicy.itemDurationFontSp.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(0.6f), RoundedCornerShape(topStart = 4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            // 无封面时的占位 (或纯文本模式)
             if (isCurrent) {
                Icon(
                    imageVector = CupertinoIcons.Default.Play,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(layoutPolicy.metaIconSizeDp.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                 Box(
                    modifier = Modifier
                        .size((layoutPolicy.metaIconSizeDp / 2).dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(0.3f), CircleShape)
                )
                 Spacer(modifier = Modifier.width(18.dp))
            }
        }
        
        // 2. 信息列
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceAround // 分散对齐
        ) {
            // 标题
            Text(
                text = episode.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = layoutPolicy.itemTitleFontSp.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = (layoutPolicy.itemTitleFontSp + 3).sp
            )
            
            // 底部元数据 (弹幕/观看等，如果有)
            // 目前 UgcEpisodeArc -> Stat (view, danmaku)
            // 我们暂且假设 stat 存在且包含 view
            /* 
               注意：data.model.response.Stat 通常包含 view, danmaku
               这里我们需要安全访问
            */
            if (episode.arc?.stat != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 播放量
                    Icon(
                        imageVector = CupertinoIcons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(layoutPolicy.metaIconSizeDp.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = FormatUtils.formatStat(episode.arc.stat.view.toLong()), 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = layoutPolicy.itemMetaFontSp.sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 弹幕
                    Icon(
                        imageVector = Icons.Filled.ChatBubble, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(layoutPolicy.metaIconSizeDp.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = FormatUtils.formatStat(episode.arc.stat.danmaku.toLong()), 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = layoutPolicy.itemMetaFontSp.sp
                    )
                }
            } else if (episode.arc != null) {
                // 如果没有 stat 但有 arc，显示 "P<Index>" 或其他信息?
                // 暂时只显示时长 (上面已经显示在封面上了) 或保持空白
            }
        }
    }
}
