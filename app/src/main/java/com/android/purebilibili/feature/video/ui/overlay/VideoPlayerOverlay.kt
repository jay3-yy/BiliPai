// 文件路径: feature/video/VideoPlayerOverlay.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.components.rememberFollowActionWithConfirmation
import com.android.purebilibili.core.ui.MediaContrastPalette

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.performance.rememberPanelFrameRateLabel

import android.content.ClipData
import android.content.Context
import android.os.Build
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import androidx.media3.common.Player
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.DanmakuPanelWidthMode
import com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode

import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.danmaku.DanmakuCloudSyncUiState
// Import reusable components from standalone files
import com.android.purebilibili.feature.video.ui.components.QualitySelectionMenu
import com.android.purebilibili.feature.video.ui.components.AudioQualitySelectionMenuDialog
import com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenuDialog
import com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenuPlacement
import com.android.purebilibili.feature.video.ui.components.PlayerListPopupPlacement
import com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel
import com.android.purebilibili.feature.video.ui.components.LandscapeDanmakuComposer
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.AspectRatioMenu
import com.android.purebilibili.feature.video.ui.components.VideoSettingsPanel
import com.android.purebilibili.feature.video.ui.components.PagesSelector
import com.android.purebilibili.feature.video.ui.components.resolveCurrentUgcEpisodeLazyListIndex
import com.android.purebilibili.feature.video.ui.components.LandscapeSidePanel
import com.android.purebilibili.feature.video.ui.components.landscapeControlCenterGroup
import com.android.purebilibili.feature.video.ui.components.landscapeEndDrawerPadding
import com.android.purebilibili.feature.video.ui.components.LandscapeSidePanelEdge
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.isCastDashManifestAvailable
import com.android.purebilibili.data.repository.selectCastDashAudio
import com.android.purebilibili.data.repository.selectCastDashVideo
import com.android.purebilibili.feature.plugin.CdnLineDiagnostic
import com.android.purebilibili.core.player.dash.buildLocalDashManifest
import com.android.purebilibili.feature.video.playback.audio.AudioQualityOption
import com.android.purebilibili.feature.video.playback.audio.resolveAudioQualityControlPresentation
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import com.android.purebilibili.feature.anime4k.VideoEnhancementAlgorithm
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.plugin.skin.LocalUiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinAnimatedAsset
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import com.android.purebilibili.core.plugin.skin.assetPath
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppModalPresentation
import com.android.purebilibili.feature.video.ui.components.PlaybackCdnDiagnostics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.BottomProgressBehavior
import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.video.screen.resolveVideoDetailSystemBarsVisibilityPolicy
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.feature.video.share.VideoSharePayload
import com.android.purebilibili.feature.video.share.VideoShareSheetHost
import com.android.purebilibili.feature.video.share.buildVideoSharePayload
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.feature.anime4k.Anime4KBypassReason
import com.android.purebilibili.feature.anime4k.Anime4KPreset
import com.android.purebilibili.feature.anime4k.DEFAULT_FSR_SHARPNESS

import androidx.compose.foundation.lazy.LazyListState
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import com.android.purebilibili.core.ui.rememberAppBookmarkIcon
import com.android.purebilibili.core.ui.rememberAppCoinIcon
import com.android.purebilibili.core.ui.rememberAppLikeFilledIcon
import com.android.purebilibili.core.ui.rememberAppLikeIcon
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.video.usecase.applyPlaybackButtonUserAction
import com.android.purebilibili.feature.video.usecase.playPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.cast.DeviceListDialog
import com.android.purebilibili.core.plugin.CastPluginApi
import com.android.purebilibili.core.plugin.CastPluginMediaRequest
import com.android.purebilibili.core.plugin.CastPluginRoute
import com.android.purebilibili.core.plugin.CastPluginPlaybackState
import com.android.purebilibili.feature.cast.LocalProxyServer
import androidx.compose.animation.core.LinearEasing
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
import com.android.purebilibili.feature.video.playback.session.PendingPlaybackUserAction
import dev.chrisbanes.haze.HazeState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

internal fun shouldShowEpisodeEntryFromVideoData(
    relatedVideosCount: Int,
    hasSeasonEpisodes: Boolean,
    pagesCount: Int,
    hasFavoritePlaylist: Boolean = false
): Boolean {
    return pagesCount > 1 || hasSeasonEpisodes || hasFavoritePlaylist
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

internal fun shouldDismissCastDialogOnUrlFailure(castUrl: String?): Boolean = castUrl.isNullOrBlank()

internal data class CastMediaResolution(val url: String, val contentType: String)

internal fun resolveEffectivePlayerProgress(
    localProgress: PlayerProgress,
    pluginState: CastPluginPlaybackState?
): PlayerProgress {
    if (pluginState?.isActive != true) return localProgress
    return PlayerProgress(
        current = pluginState.currentPositionMs,
        duration = pluginState.durationMs,
        buffered = pluginState.bufferedPositionMs
    )
}

internal fun resolveEffectivePlayingState(
    localIsPlaying: Boolean,
    pluginState: CastPluginPlaybackState?
): Boolean {
    if (pluginState?.isActive != true) return localIsPlaying
    return pluginState.isPlaying
}

internal fun shouldActivatePluginPlaybackAfterCast(pluginState: CastPluginPlaybackState): Boolean = pluginState.isActive

internal data class CastMediaSourceSignature(val aid: Long, val cid: Long, val quality: Int, val videoUrl: String, val audioUrl: String = "")

internal fun buildCastMediaSourceSignature(currentAid: Long, cid: Long, currentQuality: Int, currentVideoUrl: String, currentAudioUrl: String = ""): CastMediaSourceSignature {
    return CastMediaSourceSignature(aid = currentAid, cid = cid, quality = currentQuality, videoUrl = currentVideoUrl, audioUrl = currentAudioUrl)
}

internal fun shouldReloadActiveCastAfterMediaSourceChange(
    activePluginExists: Boolean,
    activeRouteExists: Boolean,
    pluginState: CastPluginPlaybackState,
    currentSignature: CastMediaSourceSignature,
    lastCastSignature: CastMediaSourceSignature?
): Boolean {
    if (!activePluginExists || !activeRouteExists || !pluginState.isActive || lastCastSignature == null) return false
    return currentSignature != lastCastSignature
}

internal fun resolveCastDashDurationMs(timelengthMs: Long, dashDurationSec: Int): Long = when {
    timelengthMs > 0L -> timelengthMs
    dashDurationSec > 0 -> dashDurationSec * 1000L
    else -> 0L
}

internal suspend fun resolveCastPlayUrl(
    context: Context,
    currentAid: Long,
    cid: Long,
    currentQuality: Int,
    currentVideoUrl: String
): CastMediaResolution? = withContext(Dispatchers.IO) {
    val tvData = runCatching {
        VideoRepository.getTvCastPlayData(aid = currentAid, cid = cid, qn = currentQuality)
    }.getOrNull()

    if (tvData != null) {
        val durlUrl = tvData.durl.orEmpty().firstOrNull { it.url.isNotBlank() }?.url
            ?: tvData.durl.orEmpty().firstNotNullOfOrNull { segment ->
                segment.backupUrl?.firstOrNull { it.isNotBlank() }
            }
        if (!durlUrl.isNullOrBlank()) {
            return@withContext CastMediaResolution(url = durlUrl, contentType = "video/mp4")
        }

        val dash = tvData.dash
        if (dash != null && isCastDashManifestAvailable(dash)) {
            val selectedVideo = selectCastDashVideo(dash.video, currentQuality.takeIf { it > 0 } ?: 80)
            val selectedAudio = selectCastDashAudio(dash.audio.orEmpty(), dash.dolby, dash.flac)
            if (selectedVideo != null && selectedAudio != null) {
                val proxyVideo = selectedVideo.copy(
                    baseUrl = LocalProxyServer.getProxyUrl(context, selectedVideo.getValidUrl()),
                    backupUrl = null
                )
                val proxyAudio = selectedAudio.copy(
                    baseUrl = LocalProxyServer.getProxyUrl(context, selectedAudio.getValidUrl()),
                    backupUrl = null
                )
                val durationMs = resolveCastDashDurationMs(tvData.timelength, dash.duration)
                val minBufferMs = (dash.minBufferTime * 1000f).toLong().coerceAtLeast(0L)
                val manifest = buildLocalDashManifest(
                    durationMs = durationMs,
                    minBufferTimeMs = minBufferMs,
                    videoTracks = listOf(proxyVideo),
                    audioTracks = listOf(proxyAudio)
                )
                val manifestUrl = LocalProxyServer.registerDashManifest(context, manifest)
                return@withContext CastMediaResolution(url = manifestUrl, contentType = LocalProxyServer.DASH_CONTENT_TYPE)
            }
        }
    }

    runCatching {
        if (currentVideoUrl.isBlank()) return@runCatching null
        LocalProxyServer.ensureStarted()
        CastMediaResolution(
            url = LocalProxyServer.getProxyUrl(context, currentVideoUrl),
            contentType = "video/mp4"
        )
    }.getOrNull()
}

internal fun shouldPollInlineVideoOverlayProgress(
    playerExists: Boolean,
    hostLifecycleStarted: Boolean
): Boolean {
    return playerExists && hostLifecycleStarted
}

internal fun resolveInlineVideoOverlayProgressPollingIntervalMs(
    controlsVisible: Boolean,
    isPlaying: Boolean,
    highFrequencyProgressActive: Boolean
): Long {
    return when {
        highFrequencyProgressActive -> 100L
        controlsVisible && isPlaying -> 200L
        else -> 1000L
    }
}

internal fun resolveOverlayPlaybackButtonPlayingState(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    hasPendingSeekResume: Boolean = false
): Boolean {
    return isPlaying || (playWhenReady && (playbackState == Player.STATE_BUFFERING || hasPendingSeekResume))
}

/**
 * 缓冲加载圈的显隐判据。
 *
 * 进场时播放器刚挂上就是 `STATE_BUFFERING` 且 `playWhenReady=true`，而首帧尚未渲染。
 * 此时视频槽位由封面/黑底承担加载表达，再叠一层转圈会在首帧到达瞬间闪一次
 * （`isBuffering` 与 `isFirstFrameRendered` 来自两个 Listener，中间隔着若干帧）。
 *
 * 因此除既有条件外，必须同时满足：
 * - [hasRenderedFirstFrame]：首帧已渲染，转圈只用于表达「已有画面后的再缓冲」。
 * - [!suppressTransientOverlays]：转场/返回动画期间不合成瞬时 overlay。
 */
internal fun shouldShowBufferingIndicator(
    isBuffering: Boolean,
    isQualitySwitching: Boolean,
    playWhenReady: Boolean,
    hasRenderedFirstFrame: Boolean = true,
    suppressTransientOverlays: Boolean = false,
): Boolean {
    if (!hasRenderedFirstFrame) return false
    if (suppressTransientOverlays) return false
    return isBuffering && playWhenReady && !isQualitySwitching
}

internal enum class FullscreenLockButtonIcon {
    LOCKED,
    UNLOCKED
}

internal data class FullscreenLockButtonVisualState(
    val icon: FullscreenLockButtonIcon,
    val contentDescription: String,
    val highlighted: Boolean
)

internal fun resolveFullscreenLockButtonVisualState(
    isScreenLocked: Boolean
): FullscreenLockButtonVisualState {
    return if (isScreenLocked) {
        FullscreenLockButtonVisualState(
            icon = FullscreenLockButtonIcon.LOCKED,
            contentDescription = "已锁定",
            highlighted = true
        )
    } else {
        FullscreenLockButtonVisualState(
            icon = FullscreenLockButtonIcon.UNLOCKED,
            contentDescription = "未锁定",
            highlighted = false
        )
    }
}

/** Media contrast belongs to this control, not the surrounding page's accent palette. */
@Composable
internal fun FullscreenLockButton(
    visualState: FullscreenLockButtonVisualState,
    onClick: () -> Unit,
    cornerRadiusDp: Int,
    iconSizeDp: Int,
    modifier: Modifier = Modifier,
) {
    val glass = LocalPlayerControlGlassEnabled.current
    val foreground = if (glass || !visualState.highlighted) Color.White else Color.Black
    val background = when {
        glass -> Color.Transparent
        visualState.highlighted -> Color.White
        else -> Color.Black.copy(alpha = 0.6f)
    }
    PlayerControlGlassDock(
        enabled = true,
        shape = CircleShape,
        minimumReadabilityScrimAlpha = 0.6f,
    ) {
        // Glass owns the translucent backing; native mode retains its state inversion.
        // Both glass states use media white, with the glyph and label conveying lock state.
        CompositionLocalProvider(
            com.android.purebilibili.core.ui.LocalAppButtonSlotColorCorrectionEnabled provides false,
        ) {
            AppSurface(
                onClick = onClick,
                color = background,
                contentColor = foreground,
                shape = RoundedCornerShape(cornerRadiusDp.dp),
                modifier = modifier.size(PlayerChromeLayout.CircularButtonSizeDp.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AppIcon(
                        imageVector = when (visualState.icon) {
                            FullscreenLockButtonIcon.LOCKED -> Icons.Outlined.Lock
                            FullscreenLockButtonIcon.UNLOCKED -> Icons.Outlined.LockOpen
                        },
                        contentDescription = visualState.contentDescription,
                        tint = foreground,
                        modifier = Modifier.size(iconSizeDp.dp),
                    )
                }
            }
        }
    }
}

internal fun resolvePageSelectorSheetOuterBottomPaddingDp(
    isFullscreen: Boolean
): Int {
    return if (isFullscreen) 0 else 8
}

internal fun resolvePageSelectorSheetMaxWidthDp(
    windowWidthDp: Int,
    isFullscreen: Boolean
): Int? {
    return if (isFullscreen || windowWidthDp >= 600) {
        640
    } else {
        null
    }
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

internal fun shouldAutoHideInlineControlsAfterDelay(
    controlsVisible: Boolean,
    isPlaying: Boolean,
    isSeekScrubbing: Boolean,
    floatingPanelVisible: Boolean = false
): Boolean {
    return controlsVisible && isPlaying && !isSeekScrubbing && !floatingPanelVisible
}

internal fun shouldCancelSeekScrubWhenControlsHidden(
    controlsVisible: Boolean,
    isSeekScrubbing: Boolean
): Boolean {
    return !controlsVisible && isSeekScrubbing
}

internal fun resolveDisplayedOnlineCount(
    onlineCount: String,
    showOnlineCount: Boolean
): String {
    return if (showOnlineCount) onlineCount else ""
}

@Composable
private fun SkinAwareLoadingIndicator(color: Color) {
    val skinPath = LocalUiSkinState.current.assetPath(UiSkinSurface.LOADING_INDICATOR) {
        it.loadingAnimation ?: it.loadingFrame
    }
    if (skinPath != null) {
        UiSkinAnimatedAsset(
            path = skinPath,
            size = 48.dp,
            contentDescription = "加载中",
        )
    } else {
        AdaptiveLoadingIndicator(color = color)
    }
}

private const val SEEK_RESUME_TRANSITION_GRACE_MS = 350L

// 播放器 overlay 色层动效 token：遮罩/控制栏/锁屏按钮/加载指示共用同一节奏与曲线。
// 曲线取全局 alpha 主曲线 Continuity（AppMotionTokens 体系），时长保持既有节奏。
private const val OVERLAY_CHROME_FADE_DURATION_MILLIS = PLAYER_GLASS_VISIBILITY_DURATION_MS
private const val OVERLAY_CONTROL_FADE_DURATION_MILLIS = 200
private val OverlayChromeFadeSpec =
    tween<Float>(OVERLAY_CHROME_FADE_DURATION_MILLIS, easing = AppMotionEasing.Continuity)
private val OverlayControlFadeSpec =
    tween<Float>(OVERLAY_CONTROL_FADE_DURATION_MILLIS, easing = AppMotionEasing.Continuity)
private val OverlayControlSlideSpec =
    tween<IntOffset>(OVERLAY_CONTROL_FADE_DURATION_MILLIS, easing = AppMotionEasing.Continuity)

@Composable
fun VideoPlayerOverlay(
    state: VideoPlayerOverlayState,
    actions: VideoPlayerOverlayActions,
) {
    // Keep the non-inline backdrop slot small. Capturing this screen's locals
    // here exceeds DEX's 255 argument-register limit in synthetic constructors.
    PlayerControlsBackdropHost(
        enabled = true,
        modifier = Modifier.fillMaxSize(),
    ) {
        VideoPlayerOverlayContent(state, actions)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VideoPlayerOverlayContent(
    state: VideoPlayerOverlayState,
    actions: VideoPlayerOverlayActions,
) {
    val levelGestureActive = LocalPlayerLevelGestureActive.current
    val player = state.player
    val title = state.title
    val isVisible = state.isVisible
    val onToggleVisible = actions.onToggleVisible
    val isFullscreen = state.isFullscreen
    val currentQualityLabel = state.currentQualityLabel
    val qualityLabels = state.qualityLabels
    val qualityIds = state.qualityIds
    val switchableQualityIds = state.switchableQualityIds
    val isLoggedIn = state.isLoggedIn
    val onQualitySelected = actions.onQualitySelected
    val onBack = actions.onBack
    val onLandscapeCommentClick = actions.onLandscapeCommentClick
    val landscapeCommentPanelVisible = state.landscapeCommentPanelVisible
    val landscapeCommentPanelOnLeft = state.landscapeCommentPanelOnLeft
    val onHomeClick = actions.onHomeClick
    val onToggleFullscreen = actions.onToggleFullscreen
    val bvid = state.bvid
    val cid = state.cid
    val videoOwnerName = state.videoOwnerName
    val videoOwnerFace = state.videoOwnerFace
    val videoDuration = state.videoDuration
    val videoTitle = state.videoTitle
    val currentAid = state.currentAid
    val currentQuality = state.currentQuality
    val currentVideoUrl = state.currentVideoUrl
    val currentAudioUrl = state.currentAudioUrl
    val isScreenLocked = state.isScreenLocked
    val onLockToggle = actions.onLockToggle
    val insightMode = state.insightMode
    val debugInfo = state.debugInfo
    val playerViewportSize = state.playerViewportSize
    val viewportWidthDpOverride = state.viewportWidthDpOverride
    val diagnosticEvents = state.diagnosticEvents
    val pendingUserAction = state.pendingUserAction
    val hasPendingSeekResume = state.hasPendingSeekResume
    val playerDiagnosticLoggingEnabled = state.playerDiagnosticLoggingEnabled
    val realResolution = state.realResolution
    val isQualitySwitching = state.isQualitySwitching
    val isBuffering = state.isBuffering
    val hasRenderedFirstFrame = state.hasRenderedFirstFrame
    val suppressTransientOverlaysForTransition = state.suppressTransientOverlaysForTransition
    val onBottomControlsSizeChanged = actions.onBottomControlsSizeChanged
    val isVip = state.isVip
    val sponsorContributionAvailable = state.sponsorContributionAvailable
    val sponsorContributionMarking = state.sponsorContributionMarking
    val onSponsorContributionMarkBoundary = actions.onSponsorContributionMarkBoundary
    val onSponsorContributionMarkWholeVideo = actions.onSponsorContributionMarkWholeVideo
    val onSponsorContributionCancel = actions.onSponsorContributionCancel
    val danmakuEnabled = state.danmakuEnabled
    val onDanmakuToggle = actions.onDanmakuToggle
    val onDanmakuInputClick = actions.onDanmakuInputClick
    val danmakuComposerVisible = state.danmakuComposerVisible
    val onDismissDanmakuComposer = actions.onDismissDanmakuComposer
    val onSendDanmakuComposer = actions.onSendDanmakuComposer
    val isSendingDanmakuComposer = state.isSendingDanmakuComposer
    val danmakuComposerInitialText = state.danmakuComposerInitialText
    val danmakuComposerInitialAttentionCommand = state.danmakuComposerInitialAttentionCommand
    val danmakuComposerInitialColor = state.danmakuComposerInitialColor
    val danmakuComposerInitialMode = state.danmakuComposerInitialMode
    val danmakuComposerInitialFontSize = state.danmakuComposerInitialFontSize
    val onDanmakuComposerDraftChange = actions.onDanmakuComposerDraftChange
    val onDanmakuComposerSelectionChange = actions.onDanmakuComposerSelectionChange
    val danmakuOpacity = state.danmakuOpacity
    val danmakuFontScale = state.danmakuFontScale
    val danmakuFontWeight = state.danmakuFontWeight
    val danmakuSpeed = state.danmakuSpeed
    val danmakuDisplayArea = state.danmakuDisplayArea
    val danmakuStrokeWidth = state.danmakuStrokeWidth
    val danmakuLineHeight = state.danmakuLineHeight
    val danmakuScrollDurationSeconds = state.danmakuScrollDurationSeconds
    val danmakuStaticDurationSeconds = state.danmakuStaticDurationSeconds
    val danmakuScrollFixedVelocity = state.danmakuScrollFixedVelocity
    val danmakuStaticToScroll = state.danmakuStaticToScroll
    val danmakuMassiveMode = state.danmakuMassiveMode
    val danmakuMergeDuplicates = state.danmakuMergeDuplicates
    val danmakuDuplicateMergeWindowMs = state.danmakuDuplicateMergeWindowMs
    val danmakuDuplicateMergeCountThreshold = state.danmakuDuplicateMergeCountThreshold
    val danmakuAllowScroll = state.danmakuAllowScroll
    val danmakuAllowTop = state.danmakuAllowTop
    val danmakuAllowBottom = state.danmakuAllowBottom
    val danmakuAllowColorful = state.danmakuAllowColorful
    val danmakuAllowSpecial = state.danmakuAllowSpecial
    val danmakuWeightFilterLevel = state.danmakuWeightFilterLevel
    val danmakuHideInteractiveCommands = state.danmakuHideInteractiveCommands
    val danmakuBlockRulesRaw = state.danmakuBlockRulesRaw
    val danmakuSmartOcclusion = state.danmakuSmartOcclusion
    val danmakuFullscreenPanelWidthMode = state.danmakuFullscreenPanelWidthMode
    val portraitDanmakuDisplayAreaMode = state.portraitDanmakuDisplayAreaMode
    val danmakuSettingsScope = state.danmakuSettingsScope
    val showDanmakuSyncSection = state.showDanmakuSyncSection
    val danmakuCloudSyncEnabled = state.danmakuCloudSyncEnabled
    val danmakuSyncUiState = state.danmakuSyncUiState
    val onDanmakuOpacityChange = actions.onDanmakuOpacityChange
    val onDanmakuFontScaleChange = actions.onDanmakuFontScaleChange
    val onDanmakuFontWeightChange = actions.onDanmakuFontWeightChange
    val onDanmakuSpeedChange = actions.onDanmakuSpeedChange
    val onDanmakuDisplayAreaChange = actions.onDanmakuDisplayAreaChange
    val onDanmakuStrokeWidthChange = actions.onDanmakuStrokeWidthChange
    val onDanmakuLineHeightChange = actions.onDanmakuLineHeightChange
    val onDanmakuScrollDurationSecondsChange = actions.onDanmakuScrollDurationSecondsChange
    val onDanmakuStaticDurationSecondsChange = actions.onDanmakuStaticDurationSecondsChange
    val onDanmakuScrollFixedVelocityChange = actions.onDanmakuScrollFixedVelocityChange
    val onDanmakuStaticToScrollChange = actions.onDanmakuStaticToScrollChange
    val onDanmakuMassiveModeChange = actions.onDanmakuMassiveModeChange
    val onDanmakuMergeDuplicatesChange = actions.onDanmakuMergeDuplicatesChange
    val onDanmakuDuplicateMergeWindowMsChange = actions.onDanmakuDuplicateMergeWindowMsChange
    val onDanmakuDuplicateMergeCountThresholdChange = actions.onDanmakuDuplicateMergeCountThresholdChange
    val onDanmakuAllowScrollChange = actions.onDanmakuAllowScrollChange
    val onDanmakuAllowTopChange = actions.onDanmakuAllowTopChange
    val onDanmakuAllowBottomChange = actions.onDanmakuAllowBottomChange
    val onDanmakuAllowColorfulChange = actions.onDanmakuAllowColorfulChange
    val onDanmakuAllowSpecialChange = actions.onDanmakuAllowSpecialChange
    val onDanmakuWeightFilterLevelChange = actions.onDanmakuWeightFilterLevelChange
    val onDanmakuHideInteractiveCommandsChange = actions.onDanmakuHideInteractiveCommandsChange
    val onDanmakuBlockRulesRawChange = actions.onDanmakuBlockRulesRawChange
    val onDanmakuSmartOcclusionChange = actions.onDanmakuSmartOcclusionChange
    val onDanmakuFullscreenPanelWidthModeChange = actions.onDanmakuFullscreenPanelWidthModeChange
    val onPortraitDanmakuDisplayAreaModeChange = actions.onPortraitDanmakuDisplayAreaModeChange
    val onDanmakuCloudSyncEnabledChange = actions.onDanmakuCloudSyncEnabledChange
    val onDanmakuSyncNowClick = actions.onDanmakuSyncNowClick
    val subtitleControlState = state.subtitleControlState
    val subtitleControlCallbacks = actions.subtitleControlCallbacks
    val doubleTapLikeEnabled = state.doubleTapLikeEnabled
    val onDoubleTapLike = actions.onDoubleTapLike
    val currentAspectRatio = state.currentAspectRatio
    val onAspectRatioChange = actions.onAspectRatioChange
    val onShare = actions.onShare
    val showDislikeAction = state.showDislikeAction
    val coverUrl = state.coverUrl
    val videoSharePlayCountText = state.videoSharePlayCountText
    val onReloadVideo = actions.onReloadVideo
    val sleepTimerMinutes = state.sleepTimerMinutes
    val onSleepTimerChange = actions.onSleepTimerChange
    val isFlippedHorizontal = state.isFlippedHorizontal
    val isFlippedVertical = state.isFlippedVertical
    val onFlipHorizontal = actions.onFlipHorizontal
    val onFlipVertical = actions.onFlipVertical
    val isAudioOnly = state.isAudioOnly
    val onAudioOnlyToggle = actions.onAudioOnlyToggle
    val onQualityChange = actions.onQualityChange
    val currentCdnIndex = state.currentCdnIndex
    val cdnCount = state.cdnCount
    val cdnLineDiagnostics = state.cdnLineDiagnostics
    val isCdnProbing = state.isCdnProbing
    val onSwitchCdn = actions.onSwitchCdn
    val onSwitchCdnTo = actions.onSwitchCdnTo
    val onProbeCdnCandidates = actions.onProbeCdnCandidates
    val videoshotData = state.videoshotData
    val viewPoints = state.viewPoints
    val sponsorMarkers = state.sponsorMarkers
    val pbpRidgeSamples = state.pbpRidgeSamples
    val isVerticalVideo = state.isVerticalVideo
    val onPortraitFullscreen = actions.onPortraitFullscreen
    val onPipClick = actions.onPipClick
    val onSeekStart = actions.onSeekStart
    val onSeekDragStart = actions.onSeekDragStart
    val onSeekDragUpdate = actions.onSeekDragUpdate
    val onSeekDragCancel = actions.onSeekDragCancel
    val isSeekScrubbing = state.isSeekScrubbing
    val onSeekTo = actions.onSeekTo
    val progressDisplayOverridePositionMs = state.progressDisplayOverridePositionMs
    val progressDisplayOverridePositionProvider = actions.progressDisplayOverridePositionProvider
    val isPlaybackTransitionPending = state.isPlaybackTransitionPending
    val highFrequencyProgressActive = state.highFrequencyProgressActive
    val currentCodec = state.currentCodec
    val onCodecChange = actions.onCodecChange
    val currentSecondCodec = state.currentSecondCodec
    val onSecondCodecChange = actions.onSecondCodecChange
    val currentAudioQuality = state.currentAudioQuality
    val selectedAudioQuality = state.selectedAudioQuality
    val availableAudioQualities = state.availableAudioQualities
    val onAudioQualityChange = actions.onAudioQualityChange
    val anime4kEnabled = state.anime4kEnabled
    val anime4kAvailable = state.anime4kAvailable
    val anime4kBypassReason = state.anime4kBypassReason
    val videoEnhancementAlgorithm = state.videoEnhancementAlgorithm
    val anime4kPreset = state.anime4kPreset
    val fsrSharpness = state.fsrSharpness
    val onAnime4kToggle = actions.onAnime4kToggle
    val onVideoEnhancementAlgorithmChange = actions.onVideoEnhancementAlgorithmChange
    val onAnime4kPresetChange = actions.onAnime4kPresetChange
    val onFsrSharpnessChange = actions.onFsrSharpnessChange
    val aiAudioInfo = state.aiAudioInfo
    val currentAudioLang = state.currentAudioLang
    val onAudioLangChange = actions.onAudioLangChange
    val onlineCount = state.onlineCount
    val onSaveCover = actions.onSaveCover
    val onCaptureScreenshot = actions.onCaptureScreenshot
    val onDownloadAudio = actions.onDownloadAudio
    val currentPlayMode = state.currentPlayMode
    val onPlayModeClick = actions.onPlayModeClick
    val onPlaybackSpeedChange = actions.onPlaybackSpeedChange ?: { speed: Float -> state.player.setPlaybackSpeed(speed) }
    val endDrawerVisible = state.endDrawerVisible
    val endDrawerInitialTab = state.endDrawerInitialTab
    val endDrawerReservedWidthProvider = state.endDrawerReservedWidthProvider
    val endDrawerCloseProgress = state.endDrawerCloseProgress ?: remember { mutableFloatStateOf(1f) }
    val onShowEndDrawer = actions.onShowEndDrawer
    val onDismissEndDrawer = actions.onDismissEndDrawer
    val relatedVideos = state.relatedVideos
    val ugcSeason = state.ugcSeason
    val isFollowed = state.isFollowed
    val followStatusKnown = state.followStatusKnown
    val isLiked = state.isLiked
    val likeStatusKnown = state.likeStatusKnown
    val isCoined = state.isCoined
    val isFavorited = state.isFavorited
    val likeCount = state.likeCount
    val favoriteCount = state.favoriteCount
    val coinCount = state.coinCount
    val onToggleFollow = actions.onToggleFollow
    val onToggleLike = actions.onToggleLike
    val onDislike = actions.onDislike
    val onCoin = actions.onCoin
    val onToggleFavorite = actions.onToggleFavorite
    val onDrawerVideoClick = actions.onDrawerVideoClick
    val pages = state.pages
    val currentPageIndex = state.currentPageIndex
    val onPageSelect = actions.onPageSelect
    val hasFavoritePlaylist = state.hasFavoritePlaylist
    val onFavoritePlaylistClick = actions.onFavoritePlaylistClick
    val drawerHazeState = state.drawerHazeState
    val statusBarAmbientFrame = state.statusBarAmbientFrame
    val statusBarBackdropHeight = state.statusBarBackdropHeight
    val ambientVideoBoundsInWindow = state.ambientVideoBoundsInWindow
    val onShowDanmakuPool = actions.onShowDanmakuPool

    var showQualityMenu by remember { mutableStateOf(false) }
    var showAudioQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRatioMenu by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }  //  新增
    var pendingVideoShare by remember { mutableStateOf<VideoSharePayload?>(null) }
    fun openVideoShareSheet() {
        if (bvid.isEmpty()) return
        pendingVideoShare = buildVideoSharePayload(
            title = title,
            bvid = bvid,
            coverUrl = coverUrl,
            upName = videoOwnerName,
            playCountText = videoSharePlayCountText,
        )
    }
    var showChapterList by remember { mutableStateOf(false) }  // 📖 章节列表
    var showCastDialog by remember { mutableStateOf(false) }   // 📺 投屏对话框
    var activeCastPlugin by remember { mutableStateOf<CastPluginApi?>(null) }
    var activeCastRoute by remember { mutableStateOf<CastPluginRoute?>(null) }
    var lastCastMediaSignature by remember { mutableStateOf<CastMediaSourceSignature?>(null) }
    var lastCastMediaUrl by remember { mutableStateOf<String?>(null) }
    val pluginPlaybackState = produceState(CastPluginPlaybackState(), activeCastPlugin) {
        val plugin = activeCastPlugin
        if (plugin != null) {
            plugin.playbackState.collect { value = it }
        } else {
            value = CastPluginPlaybackState()
        }
    }
    // Only transport changes invalidate the shell; cast position stays with progress consumers.
    val pluginPlaybackStatus by remember(pluginPlaybackState) {
        derivedStateOf {
            val snapshot = pluginPlaybackState.value
            snapshot.isActive to snapshot.isPlaying
        }
    }
    var showPlaybackOrderSheet by remember { mutableStateOf(false) }
    var showPageSelectorSheet by remember { mutableStateOf(false) }
    var bottomControlFloatingPanelVisible by remember { mutableStateOf(false) }
    var showCdnDiagnostics by remember { mutableStateOf(false) }
    // 换集后强制关掉分集/菜单等全屏遮罩，避免 Dialog/Sheet 残留挡触摸。
    LaunchedEffect(bvid, cid) {
        showPageSelectorSheet = false
        showQualityMenu = false
        showAudioQualityMenu = false
        showSpeedMenu = false
        showRatioMenu = false
        showDanmakuSettings = false
        showChapterList = false
        showPlaybackOrderSheet = false
        showCastDialog = false
        showCdnDiagnostics = false
    }
    var currentSpeed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    //  使用传入的比例状态
    var isPlaying by remember(player) {
        mutableStateOf(
            resolveOverlayPlaybackButtonPlayingState(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState,
                hasPendingSeekResume = hasPendingSeekResume
            )
        )
    }
    var seekResumeTransitionGraceActive by remember { mutableStateOf(false) }
    var wasPlayingWhenProgressScrubbingStarted by remember { mutableStateOf(false) }
    var previousSeekScrubbingState by remember { mutableStateOf(isSeekScrubbing) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fullscreenLockButtonState = remember(isScreenLocked) {
        resolveFullscreenLockButtonVisualState(isScreenLocked = isScreenLocked)
    }
    val audioQualityPresentation = remember(availableAudioQualities, selectedAudioQuality) {
        resolveAudioQualityControlPresentation(
            options = availableAudioQualities,
            selectedAudioQuality = selectedAudioQuality
        )
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val configuration = LocalConfiguration.current
    val effectiveDebugInfo = remember(
        debugInfo,
        realResolution,
        playerViewportSize,
        currentVideoUrl,
        currentCdnIndex,
        cdnCount,
        player.playbackState,
        player.currentPosition,
        player.bufferedPosition,
        player.playerError
    ) {
        val viewport = playerViewportSize
            .takeIf { it.width > 0 && it.height > 0 }
            ?.let { "${it.width} x ${it.height}" }
            .orEmpty()
        val cdnHost = runCatching { android.net.Uri.parse(currentVideoUrl).host.orEmpty() }
            .getOrDefault("")
        debugInfo.copy(
            resolution = debugInfo.resolution.ifBlank { realResolution },
            playerViewport = viewport,
            cdnHost = cdnHost,
            cdnIndex = "${currentCdnIndex + 1}/${cdnCount.coerceAtLeast(1)}",
            networkType = NetworkUtils.getNetworkTypeLabel(context),
            forwardBuffer = "${(player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)} ms",
            lastLoadError = player.playerError?.let { error ->
                listOf(error.errorCodeName, error.message.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(": ")
            } ?: debugInfo.lastLoadError
        )
    }
    val panelFrameRateLabel = rememberPanelFrameRateLabel()
    val debugRows = remember(effectiveDebugInfo, panelFrameRateLabel) {
        resolvePlaybackDebugRows(effectiveDebugInfo)
            .plus(DebugStatRow("Panel rate", panelFrameRateLabel))
            .filter { it.value.isNotBlank() }
    }
    val insightPresentation = remember(effectiveDebugInfo) {
        resolvePlaybackInsightPresentation(effectiveDebugInfo)
    }
    val insightPanelLayoutPolicy = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp
    ) {
        resolvePlaybackInsightPanelLayoutPolicy(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp
        )
    }
    var showInsightDetails by remember(player, bvid, cid) { mutableStateOf(false) }
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
            val liveDebugInfo = effectiveDebugInfo.copy(
                networkType = NetworkUtils.getNetworkTypeLabel(context),
                forwardBuffer = "${(player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)} ms",
                lastLoadError = player.playerError?.let { error ->
                    listOf(error.errorCodeName, error.message.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString(": ")
                } ?: effectiveDebugInfo.lastLoadError
            )
            buildPlaybackDiagnosticReport(
                title = videoTitle.ifBlank { title },
                bvid = bvid,
                cid = cid,
                currentPositionMs = player.currentPosition,
                bufferedPositionMs = player.bufferedPosition,
                debugInfo = liveDebugInfo,
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
        .collectAsStateWithLifecycle(initialValue = true
        )
    val showFullscreenScreenshotButton by SettingsManager
        .getShowFullscreenScreenshotButton(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val showFullscreenBatteryLevel by SettingsManager
        .getShowFullscreenBatteryLevel(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val showFullscreenTime by SettingsManager
        .getShowFullscreenTime(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val showFullscreenActionItems by SettingsManager
        .getShowFullscreenActionItems(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val showOnlineCount by SettingsManager
        .getShowOnlineCount(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    val bottomProgressBehavior by SettingsManager
        .getBottomProgressBehavior(context)
        .collectAsStateWithLifecycle(initialValue = BottomProgressBehavior.ALWAYS_HIDE
        )
    val playerControlVisibility by SettingsManager
        .getPlayerControlVisibilitySettings(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerControlVisibilitySettings()
        )
    val progressPlacement by SettingsManager
        .getPlayerProgressPlacement(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerProgressPlacement.ABOVE_CONTROLS
        )
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
            pagesCount = pages.size,
            hasFavoritePlaylist = hasFavoritePlaylist
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
        .collectAsStateWithLifecycle(initialValue = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC
        )
    // The legacy preference key now enables the inline-player Haze status-bar backdrop.
    val immersiveVideoPageStatusBar by SettingsManager
        .getHideVideoPageStatusBar(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getHideVideoPageStatusBarSync(context),
        )
    val playerChromeStatusBarVisible = !resolveVideoDetailSystemBarsVisibilityPolicy(
        isFullscreenMode = isFullscreen,
        hideVideoPageStatusBar = immersiveVideoPageStatusBar,
        isInPipMode = false,
        isScreenActive = true,
        isPortraitFullscreen = false,
    ).hideStatusBars
    val effectiveProgressPlacement = remember(
        progressPlacement,
        isFullscreen
    ) {
        resolveVideoDetailProgressPlacement(
            requestedPlacement = progressPlacement,
            isFullscreen = isFullscreen
        )
    }

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
    val overlayVisualPolicy = remember(
        configuration.screenWidthDp,
        playerControlVisibility.compactPlayerChrome
    ) {
        resolveVideoPlayerOverlayVisualPolicy(
            widthDp = configuration.screenWidthDp,
            compact = playerControlVisibility.compactPlayerChrome
        ).copy(lockButtonSizeDp = 48, lockIconSizeDp = 24)
    }
    val landscapeCommentReservedWidth = if (landscapeCommentPanelVisible && !state.landscapeCommentViewportAlreadyReserved) {
        resolveLandscapeEndDrawerLayoutPolicy(configuration.screenWidthDp).drawerWidthDp.dp
    } else {
        0.dp
    }
    val overlayContentModifier = Modifier
        .fillMaxSize()
        .layout { measurable, constraints ->
            val commentInset = (landscapeCommentReservedWidth.toPx() *
                (1f - state.landscapeCommentCloseProgressProvider().coerceIn(0f, 1f))).toInt()
            val endInset = endDrawerReservedWidthProvider().roundToPx()
            val totalInset = (commentInset + endInset).coerceAtMost(constraints.maxWidth)
            val placeable = measurable.measure(constraints.offset(horizontal = -totalInset))
            layout(placeable.width + totalInset, placeable.height) {
                placeable.placeRelative(if (landscapeCommentPanelOnLeft) commentInset else 0, 0)
            }
        }

    // 先打开设备面板；只有用户选择搜索 DLNA 时才请求原始局域网权限。
    val onCastClickAction = { showCastDialog = true }

    val progressState = rememberVideoPlayerOverlayProgress(
        player = player,
        bvid = bvid,
        cid = cid,
        videoDuration = videoDuration,
        controlsVisible = isVisible,
        hostLifecycleStarted = hostLifecycleStarted,
        hasPendingSeekResume = hasPendingSeekResume,
        highFrequencyProgressActive = highFrequencyProgressActive,
        onPlayingChanged = { isPlaying = it },
    )
    val effectiveProgressState = remember(progressState, pluginPlaybackState) {
        derivedStateOf {
            resolveEffectivePlayerProgress(progressState.value, pluginPlaybackState.value)
        }
    }
    val latestProgressOverrideProvider = rememberUpdatedState(progressDisplayOverridePositionProvider)
    val displayedProgressState = remember(effectiveProgressState, progressDisplayOverridePositionMs) {
        derivedStateOf {
            resolveDisplayedPlayerProgressWithOverride(
                progress = effectiveProgressState.value,
                overridePositionMs = latestProgressOverrideProvider.value?.invoke()
                    ?: progressDisplayOverridePositionMs
            )
        }
    }
    val effectiveIsPlaying = if (pluginPlaybackStatus.first) pluginPlaybackStatus.second else isPlaying
    val progressProvider = remember(effectiveProgressState) { { effectiveProgressState.value } }
    val displayedProgressProvider = remember(displayedProgressState) { { displayedProgressState.value } }
    val seekPositionProvider = remember(displayedProgressState) { { displayedProgressState.value.current } }
    val centerLoadingUiState = remember(
        isBuffering,
        isQualitySwitching,
        seekResumeTransitionGraceActive,
        isPlaybackTransitionPending,
        hasPendingSeekResume,
        player.playWhenReady,
        player.isPlaying,
        debugInfo.bandwidthEstimate
    ) {
        resolveCenterLoadingUiState(
            isBuffering = isBuffering,
            isQualitySwitching = isQualitySwitching,
            isSeekTransitionPending = seekResumeTransitionGraceActive ||
                isPlaybackTransitionPending ||
                hasPendingSeekResume,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            bandwidthEstimate = debugInfo.bandwidthEstimate
        )
    }
    val themePrimary = AppContentColors.accent()
    val centerLoadingVisualState = remember(themePrimary) {
        resolveCenterLoadingVisualState(
            themePrimary = themePrimary
        )
    }

    val currentChapterState = remember(effectiveProgressState, viewPoints) {
        derivedStateOf {
            val position = effectiveProgressState.value.current
            viewPoints.lastOrNull { position >= it.fromMs }?.content
        }
    }
    val currentChapterProvider = remember(currentChapterState) { { currentChapterState.value } }

    LaunchedEffect(
        isVisible,
        effectiveIsPlaying,
        isSeekScrubbing,
        bottomControlFloatingPanelVisible,
        showCdnDiagnostics,
        danmakuComposerVisible,
        levelGestureActive,
    ) {
        if (!danmakuComposerVisible && !levelGestureActive && !showCdnDiagnostics &&
            shouldAutoHideInlineControlsAfterDelay(
                controlsVisible = isVisible,
                isPlaying = effectiveIsPlaying,
                isSeekScrubbing = isSeekScrubbing,
                floatingPanelVisible = bottomControlFloatingPanelVisible
            )
        ) {
            delay(4000)
            if (!danmakuComposerVisible && !levelGestureActive && !showCdnDiagnostics &&
                shouldAutoHideInlineControlsAfterDelay(
                    controlsVisible = isVisible,
                    isPlaying = effectiveIsPlaying,
                    isSeekScrubbing = isSeekScrubbing,
                    floatingPanelVisible = bottomControlFloatingPanelVisible
                )
            ) {
                onToggleVisible()
            }
        }
    }

    LaunchedEffect(isVisible, isSeekScrubbing) {
        if (
            shouldCancelSeekScrubWhenControlsHidden(
                controlsVisible = isVisible,
                isSeekScrubbing = isSeekScrubbing
            )
        ) {
            onSeekDragCancel()
        }
    }

    //  双击点赞动画自动消失
    LaunchedEffect(showLikeAnimation) {
        if (showLikeAnimation) {
            delay(800)
            showLikeAnimation = false
        }
    }

    LaunchedEffect(seekResumeTransitionGraceActive) {
        if (seekResumeTransitionGraceActive) {
            delay(SEEK_RESUME_TRANSITION_GRACE_MS)
            seekResumeTransitionGraceActive = false
        }
    }

    LaunchedEffect(isSeekScrubbing) {
        if (isSeekScrubbing && !previousSeekScrubbingState) {
            wasPlayingWhenProgressScrubbingStarted = isPlaying
            seekResumeTransitionGraceActive = false
        } else if (
            !isSeekScrubbing &&
            previousSeekScrubbingState &&
            wasPlayingWhenProgressScrubbingStarted
        ) {
            seekResumeTransitionGraceActive = true
        }
        previousSeekScrubbingState = isSeekScrubbing
    }

    LaunchedEffect(isPlaying, isBuffering, isSeekScrubbing, seekResumeTransitionGraceActive) {
        if (
            seekResumeTransitionGraceActive &&
            !isSeekScrubbing &&
            (isPlaying || isBuffering)
        ) {
            seekResumeTransitionGraceActive = false
        }
    }

    fun togglePlayPause() {
        val plugin = activeCastPlugin
        if (plugin != null && pluginPlaybackState.value.isActive) {
            scope.launch {
                if (pluginPlaybackState.value.isPlaying) {
                    plugin.pause()
                } else {
                    plugin.play()
                }
            }
            return
        }
        if (player.playbackState == Player.STATE_ENDED) {
            onSeekTo?.invoke(0L) ?: player.seekTo(0L)
            playPlayerFromUserAction(player)
            isPlaying = true
        } else {
            applyPlaybackButtonUserAction(
                player = player,
                isShowingPauseIcon = isPlaying
            )
            isPlaying = !isPlaying
        }
    }

    val commitSeek: (Long) -> Unit = { position ->
        val plugin = activeCastPlugin
        if (plugin != null && pluginPlaybackState.value.isActive) {
            scope.launch { plugin.seek(position.coerceAtLeast(0L)) }
        } else {
            val safePosition = position.coerceAtLeast(0L)
            onSeekTo?.invoke(safePosition) ?: seekPlayerFromUserAction(player, safePosition)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isFullscreen) {
            ImmersiveStatusBarBackdrop(
                ambientFrame = statusBarAmbientFrame,
                height = statusBarBackdropHeight,
                useAmbientHaze = immersiveVideoPageStatusBar,
                videoBoundsInWindow = ambientVideoBoundsInWindow,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // --- 1. 顶部渐变遮罩 ---
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(OverlayChromeFadeSpec),
            exit = fadeOut(OverlayChromeFadeSpec),
            //  [修复] align 必须在 AnimatedVisibility 的 modifier 上，而不是内部 Box 上
            modifier = Modifier
                .align(Alignment.TopCenter)
                .landscapeEndDrawerPadding(endDrawerReservedWidthProvider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(overlayVisualPolicy.topScrimHeightDp.dp)
                    .background(
                        if (!isFullscreen) {
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.2f to Color.Black.copy(alpha = 0.52f),
                                    1f to Color.Transparent,
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        }
                    )
            )
        }

        // --- 2. 底部渐变遮罩 ---
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(OverlayChromeFadeSpec),
            exit = fadeOut(OverlayChromeFadeSpec),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .landscapeEndDrawerPadding(endDrawerReservedWidthProvider)
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
        if (!danmakuComposerVisible &&
            shouldShowPersistentBottomProgressBar(
                controlsVisible = isVisible,
                isFullscreen = isFullscreen,
                behavior = bottomProgressBehavior
            )
        ) {
            OverlayPersistentProgressBar(
                progressProvider = displayedProgressProvider,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .landscapeEndDrawerPadding(endDrawerReservedWidthProvider)
            )
        }

        // --- 3. 控制栏内容 (锁定时隐藏) ---
        val showPlayerChrome = isVisible && !isScreenLocked && !danmakuComposerVisible
        AnimatedVisibility(
            visible = showPlayerChrome,
            enter = fadeIn(OverlayChromeFadeSpec),
            exit = fadeOut(OverlayChromeFadeSpec),
            //  [修复] 确保 AnimatedVisibility 填充整个父容器
            modifier = overlayContentModifier
        ) {
            //  [修复] 使用 Box 分别定位顶部和底部控制栏
            Box(modifier = Modifier.fillMaxSize()) {
                //  顶部控制栏 - 仅在横屏（全屏）模式显示标题和清晰度
                if (isFullscreen) {
                    TopControlBar(
                        backButtonStartPaddingDp = overlayVisualPolicy.lockButtonEndPaddingDp,
                        actionEndPaddingDp = overlayVisualPolicy.lockButtonEndPaddingDp,
                        title = title,
                        onlineCount = displayedOnlineCount,
                        isFullscreen = isFullscreen,
                        statusBarVisible = playerChromeStatusBarVisible,
                        showBatteryLevel = showFullscreenBatteryLevel,
                        showCurrentTime = showFullscreenTime,
                        showInteractiveActions = showFullscreenActionItems,
                        onBack = onBack,
                        // Interactions
                        isLiked = isLiked,
                        likeStatusKnown = likeStatusKnown,
                        isCoined = isCoined,
                        allowDislikeAction = showDislikeAction,
                        onLikeClick = onToggleLike,
                        onDislikeClick = onDislike,
                        onCoinClick = onCoin,
                        onShareClick = {
                            if (onShare != null) {
                                onShare()
                            } else {
                                openVideoShareSheet()
                            }
                        },
                        onCommentClick = onLandscapeCommentClick,
                        isLoggedIn = isLoggedIn,
                        onCastClick = onCastClickAction,
                        showCastButton = playerControlVisibility.showCastButton,
                        onMoreClick = {
                            onShowEndDrawer(0)
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else {
                    //  [新增] 竖屏模式顶部栏（返回 + 画质 + 设置 + 分享按钮）
                    val context = LocalContext.current
                    PortraitTopBar(
                        onlineCount = displayedOnlineCount,
                        viewportWidthDpOverride = viewportWidthDpOverride,
                        onBack = onBack,
                        onHome = onHomeClick,
                        onSettings = { showVideoSettings = true },
                        onShare = onShare ?: {
                            openVideoShareSheet()
                        },
                        onAudioMode = onAudioOnlyToggle,
                        isAudioOnly = isAudioOnly,
                        onNotInterested = onDislike,
                        sleepTimerMinutes = sleepTimerMinutes,
                        onSleepTimerChange = onSleepTimerChange,
                        //  [新增] 投屏按钮
                        onCastClick = onCastClickAction,
                        showCastButton = playerControlVisibility.showCastButton,
                        compactPlayerChrome = playerControlVisibility.compactPlayerChrome,
                        statusBarVisible = playerChromeStatusBarVisible,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .onSizeChanged { onBottomControlsSizeChanged(it.height) }
                ) {
                    key(player, bvid, cid) {
                    BottomControlBar(
                    viewportWidthDpOverride = viewportWidthDpOverride,
                    isPlaying = effectiveIsPlaying,
                    progressProvider = progressProvider,
                    isFullscreen = isFullscreen,
                    compactPlayerChrome = playerControlVisibility.compactPlayerChrome,
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
                    seekPositionProvider = seekPositionProvider,
                    isSeekScrubbing = isSeekScrubbing,
                    onSpeedClick = { showSpeedMenu = true },
                    onRatioClick = { showRatioMenu = true },
                    onNextEpisodeClick = {
                        onDismissEndDrawer()
                        val target = nextEpisodeTarget
                        when {
                            target?.nextPageIndex != null -> onPageSelect(target.nextPageIndex)
                            !target?.nextBvid.isNullOrBlank() -> onDrawerVideoClick(target.nextBvid, null)
                        }
                    },
                    hasNextEpisode = nextEpisodeTarget != null,
                    onEpisodeClick = {
                        if (pages.size > 1) {
                            showPageSelectorSheet = true
                        } else if (ugcSeason?.sections?.any { it.episodes.isNotEmpty() } == true) {
                            onShowEndDrawer(1)
                        } else if (hasFavoritePlaylist) {
                            onFavoritePlaylistClick()
                        } else {
                            onShowEndDrawer(0)
                        }
                    },
                    hasEpisodeEntry = hasEpisodeEntry,
                    onToggleFullscreen = onToggleFullscreen,
                    //  [新增] 竖屏模式弹幕和清晰度控制
                    sponsorContributionAvailable = sponsorContributionAvailable,
                    sponsorContributionMarking = sponsorContributionMarking,
                    onSponsorContributionMarkBoundary = onSponsorContributionMarkBoundary,
                    onSponsorContributionMarkWholeVideo = onSponsorContributionMarkWholeVideo,
                    onSponsorContributionCancel = onSponsorContributionCancel,
                    danmakuEnabled = danmakuEnabled,
                    onDanmakuToggle = onDanmakuToggle,
                    onDanmakuSettingsClick = { showDanmakuSettings = true },
                    onDanmakuInputClick = onDanmakuInputClick,
                    isLoggedIn = isLoggedIn,
                    subtitleControlState = subtitleControlState,
                    subtitleControlCallbacks = subtitleControlCallbacks,
                    anime4kEnabled = anime4kEnabled,
                    anime4kAvailable = anime4kAvailable,
                    videoEnhancementAlgorithm = videoEnhancementAlgorithm,
                    anime4kPreset = anime4kPreset,
                    fsrSharpness = fsrSharpness,
                    onAnime4kToggle = onAnime4kToggle,
                    onVideoEnhancementAlgorithmChange = onVideoEnhancementAlgorithmChange,
                    onAnime4kPresetChange = onAnime4kPresetChange,
                    onFsrSharpnessChange = onFsrSharpnessChange,
                    currentAudioQualityLabel = audioQualityPresentation.label,
                    isHiResAudioSelected = audioQualityPresentation.showHiResBadge,
                    isDolbyAudioSelected = audioQualityPresentation.showDolbyBadge,
                    onAudioQualityClick = { showAudioQualityMenu = true },
                    currentQualityLabel = currentQualityLabel,
                    onQualityClick = { showQualityMenu = true },
                    // 🖼️ [新增] 视频预览图数据
                    videoshotData = videoshotData,
                    // 📖 [新增] 视频章节数据
                    viewPoints = viewPoints,
                    sponsorMarkers = sponsorMarkers,
                    pbpRidgeSamples = pbpRidgeSamples,
                    currentChapterProvider = currentChapterProvider,
                    onChapterClick = { showChapterList = true },
                    // 📱 [新增] 竖屏全屏模式
                    isVerticalVideo = isVerticalVideo,
                    onPortraitFullscreen = onPortraitFullscreen,
                    // 📲 [新增] 小窗模式
                    onPipClick = onPipClick,
                    cdnDiagnosticsAvailable = cdnCount > 0,
                    onCdnDiagnosticsClick = { showCdnDiagnostics = true },
                    onPlayerSettingsClick = { showVideoSettings = true },
                    // 🔁 [新增] 播放模式
                    currentPlayMode = currentPlayMode,
                    onPlayModeClick = onPlayModeClick,
                    playbackOrderLabel = resolvePlaybackOrderDisplayLabel(
                        behavior = playbackCompletionBehavior,
                        compact = !isFullscreen
                    ),
                    onPlaybackOrderClick = { showPlaybackOrderSheet = true },
                    onFloatingPanelVisibilityChange = { visible ->
                        bottomControlFloatingPanelVisible = visible
                    },
                    progressPlacement = effectiveProgressPlacement
                )
                    }
                }
            }
        }

        if (isFullscreen && danmakuComposerVisible) {
            LandscapeDanmakuComposer(
                visible = true,
                onDismiss = onDismissDanmakuComposer,
                onSend = onSendDanmakuComposer,
                isSending = isSendingDanmakuComposer,
                initialColor = danmakuComposerInitialColor,
                initialMode = danmakuComposerInitialMode,
                initialFontSize = danmakuComposerInitialFontSize,
                initialText = danmakuComposerInitialText,
                initialAttentionCommand = danmakuComposerInitialAttentionCommand,
                onDraftChange = onDanmakuComposerDraftChange,
                onSelectionChange = onDanmakuComposerSelectionChange
            )
        }

        // --- 3.5 🔒 [新增] 屏幕锁定按钮 (仅全屏模式) ---
        if (isFullscreen && showFullscreenLockButton) {
            AnimatedVisibility(
                visible = isVisible && !danmakuComposerVisible,  // 锁定后按控制栏状态自动隐藏
                enter = fadeIn(OverlayControlFadeSpec),
                exit = fadeOut(OverlayControlFadeSpec),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = overlayVisualPolicy.lockButtonEndPaddingDp.dp)
            ) {
                FullscreenLockButton(
                    visualState = fullscreenLockButtonState,
                    onClick = onLockToggle,
                    cornerRadiusDp = overlayVisualPolicy.lockButtonCornerRadiusDp,
                    iconSizeDp = overlayVisualPolicy.lockIconSizeDp,
                )
            }
        }

        if (isFullscreen && showFullscreenScreenshotButton) {
            AnimatedVisibility(
                visible = isVisible && !isScreenLocked && !danmakuComposerVisible,
                enter = fadeIn(OverlayControlFadeSpec),
                exit = fadeOut(OverlayControlFadeSpec),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = overlayVisualPolicy.lockButtonEndPaddingDp.dp)
            ) {
                PlayerControlGlassDock(enabled = true, shape = androidx.compose.foundation.shape.CircleShape) {
                    AppSurface(
                        onClick = onCaptureScreenshot,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(overlayVisualPolicy.lockButtonCornerRadiusDp.dp),
                        modifier = Modifier.size(PlayerChromeLayout.CircularButtonSizeDp.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            AppIcon(
                                imageVector = Icons.Outlined.Camera,
                                contentDescription = "截图",
                                tint = Color.White,
                                modifier = Modifier.size(overlayVisualPolicy.lockIconSizeDp.dp)
                            )
                        }
                    }
                }
            }
        }

        val showInsightHud = shouldShowPlaybackInsightHud(
            mode = insightMode,
            hasMeasuredData = debugRows.isNotEmpty(),
            controlsVisible = isVisible,
            screenLocked = isScreenLocked,
            level = insightPresentation.level
        )
        if (showInsightHud) {
            AppSurface(
                onClick = { showInsightDetails = true },
                color = if (isFullscreen) Color.Transparent else Color.Black.copy(alpha = 0.68f),
                contentColor = Color.White,
                shape = AppShapes.container(ContainerLevel.Dialog),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = overlayVisualPolicy.statsTopPaddingDp.dp,
                        end = overlayVisualPolicy.statsEndPaddingDp.dp
                    )
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = when (insightPresentation.level) {
                                    PlaybackInsightLevel.LIVE -> MediaContrastPalette.Success
                                    PlaybackInsightLevel.ATTENTION -> MediaContrastPalette.Warning
                                    PlaybackInsightLevel.UNAVAILABLE -> MediaContrastPalette.SecondaryForeground
                                },
                                shape = CircleShape
                            )
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        AppText(
                            text = insightPresentation.summary,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = overlayVisualPolicy.statsFontSp.sp,
                            maxLines = 1
                        )
                        AppText(
                            text = insightPresentation.statusText,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = (overlayVisualPolicy.statsFontSp - 1f).coerceAtLeast(9f).sp
                        )
                    }
                }
            }
        }

        if (showInsightDetails) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showInsightDetails = false }
                    )
            )
            PlaybackInsightPanel(
                presentation = insightPresentation,
                presentationProvider = {
                    val localProgress = progressState.value
                    resolvePlaybackInsightPresentation(
                        effectiveDebugInfo.copy(
                            forwardBuffer = "${(localProgress.buffered - localProgress.current).coerceAtLeast(0L)} ms",
                        )
                    )
                },
                diagnosticEvents = diagnosticEvents,
                onCopyReport = if (playerDiagnosticLoggingEnabled) {
                    {
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
                } else {
                    null
                },
                onDismiss = { showInsightDetails = false },
                hazeState = drawerHazeState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(insightPanelLayoutPolicy.edgePaddingDp.dp)
                    .width(insightPanelLayoutPolicy.widthDp.dp)
                    .heightIn(max = insightPanelLayoutPolicy.maxHeightDp.dp)
            )
        }

        if (playerDiagnosticLoggingEnabled) playbackIssueSignal?.let { signal ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(OverlayControlFadeSpec) + slideInVertically(OverlayControlSlideSpec) { -it / 2 },
                exit = fadeOut(OverlayControlFadeSpec) + slideOutVertically(OverlayControlSlideSpec) { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = Color.Black.copy(alpha = 0.82f),
                    contentColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                    ) {
                        AppText(
                            text = signal.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        AppTextButton(
                            onClick = {
                                showInsightDetails = true
                                dismissedPlaybackIssueTypes = dismissedPlaybackIssueTypes + signal.type
                                playbackIssueSignal = null
                            }
                        ) {
                            AppText("查看")
                        }
                        AppTextButton(
                            onClick = {
                                val savedPath = Logger.exportPlayerDiagnostic(
                                    context = context,
                                    content = exportDiagnosticReport(signal)
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    if (savedPath != null) "已导出到: $savedPath" else "导出失败，请稍后重试",
                                    if (savedPath != null) {
                                        android.widget.Toast.LENGTH_LONG
                                    } else {
                                        android.widget.Toast.LENGTH_SHORT
                                    }
                                ).show()
                                dismissedPlaybackIssueTypes = dismissedPlaybackIssueTypes + signal.type
                                playbackIssueSignal = null
                            }
                        ) {
                            AppText("导出")
                        }
                        AppIconButton(
                            onClick = {
                                dismissedPlaybackIssueTypes = dismissedPlaybackIssueTypes + signal.type
                                playbackIssueSignal = null
                            }
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭卡顿提示"
                            )
                        }
                    }
                }
            }
        }

        // --- 5.4  缓冲加载指示器 ---
        AnimatedVisibility(
            visible = shouldShowBufferingIndicator(
                isBuffering = isBuffering,
                isQualitySwitching = isQualitySwitching,
                playWhenReady = player.playWhenReady,
                hasRenderedFirstFrame = hasRenderedFirstFrame,
                suppressTransientOverlays = suppressTransientOverlaysForTransition,
            ) && centerLoadingUiState == null,
            modifier = Modifier.align(Alignment.Center),
            // 首帧前整段不参与合成，出现时也硬切：淡入会让「已有画面后的再缓冲」
            // 看起来像又一次进场闪烁。
            enter = EnterTransition.None,
            exit = fadeOut(OverlayControlFadeSpec)
        ) {
            SkinAwareLoadingIndicator(color = centerLoadingVisualState.indicatorColor)
        }

        AnimatedVisibility(
            visible = centerLoadingUiState != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(OverlayControlFadeSpec),
            exit = fadeOut(OverlayControlFadeSpec)
        ) {
            val loadingState = centerLoadingUiState ?: return@AnimatedVisibility
            AppSurface(
                color = Color.Black.copy(alpha = 0.72f),
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
                    SkinAwareLoadingIndicator(color = centerLoadingVisualState.indicatorColor)
                    Spacer(modifier = Modifier.height(overlayVisualPolicy.qualitySwitchContentSpacingDp.dp))
                    AppText(
                        text = loadingState.primaryText,
                        color = centerLoadingVisualState.primaryTextColor,
                        fontSize = overlayVisualPolicy.qualitySwitchMessageFontSp.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    loadingState.secondaryText?.let { secondaryText ->
                        Spacer(modifier = Modifier.height(6.dp))
                        AppText(
                            text = secondaryText,
                            color = centerLoadingVisualState.secondaryTextColor,
                            fontSize = (overlayVisualPolicy.qualitySwitchMessageFontSp - 1).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- 5.5  清晰度切换中 Loading 指示器 ---
        AnimatedVisibility(
            visible = isQualitySwitching && centerLoadingUiState == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(OverlayControlFadeSpec),
            exit = fadeOut(OverlayControlFadeSpec)
        ) {
            AppSurface(
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
                    SkinAwareLoadingIndicator(color = centerLoadingVisualState.indicatorColor)
                    Spacer(modifier = Modifier.height(overlayVisualPolicy.qualitySwitchContentSpacingDp.dp))
                    AppText(
                        text = "正在切换清晰度...",
                        color = centerLoadingVisualState.primaryTextColor,
                        fontSize = overlayVisualPolicy.qualitySwitchMessageFontSp.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        VideoShareSheetHost(
            payload = pendingVideoShare,
            onDismiss = { pendingVideoShare = null },
        )

        // --- 6. 清晰度菜单 ---
        if (showQualityMenu) {
            QualitySelectionMenu(
                qualities = qualityLabels,
                qualityIds = qualityIds,
                switchableQualityIds = switchableQualityIds,
                currentQuality = currentQualityLabel,
                isLoggedIn = isLoggedIn,
                isVip = isVip,
                onQualitySelected = { index ->
                    onQualitySelected(index)
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false },
                useDialog = true,
                placement = if (isFullscreen) PlayerListPopupPlacement.END_BOTTOM else PlayerListPopupPlacement.CENTER,
            )
        }

        if (showAudioQualityMenu) {
            AudioQualitySelectionMenuDialog(
                options = availableAudioQualities,
                requestedAudioQuality = currentAudioQuality,
                onAudioQualitySelected = { preferenceId ->
                    onAudioQualityChange(preferenceId)
                    showAudioQualityMenu = false
                },
                onDismiss = { showAudioQualityMenu = false },
                placement = if (isFullscreen) PlayerListPopupPlacement.END_BOTTOM else PlayerListPopupPlacement.CENTER,
            )
        }

        // --- 7.  [新增] 倍速选择菜单 ---
        if (showSpeedMenu) {
            SpeedSelectionMenuDialog(
                currentSpeed = currentSpeed,
                onSpeedSelected = { speed ->
                    currentSpeed = speed
                    onPlaybackSpeedChange(speed)
                    scope.launch {
                        SettingsManager.setLastPlaybackSpeed(context, speed)
                    }
                    showSpeedMenu = false
                },
                onDismiss = { showSpeedMenu = false },
                placement = if (isFullscreen) {
                    SpeedSelectionMenuPlacement.RIGHT_SIDE
                } else {
                    SpeedSelectionMenuPlacement.CENTER
                }
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
                settingsScope = danmakuSettingsScope,
                opacity = danmakuOpacity,
                fontScale = danmakuFontScale,
                showAdvancedSection = true,
                fontWeight = danmakuFontWeight,
                speed = danmakuSpeed,
                displayArea = danmakuDisplayArea,
                strokeWidth = danmakuStrokeWidth,
                lineHeight = danmakuLineHeight,
                scrollDurationSeconds = danmakuScrollDurationSeconds,
                staticDurationSeconds = danmakuStaticDurationSeconds,
                scrollFixedVelocity = danmakuScrollFixedVelocity,
                staticDanmakuToScroll = danmakuStaticToScroll,
                massiveMode = danmakuMassiveMode,
                mergeDuplicates = danmakuMergeDuplicates,
                duplicateMergeWindowMs = danmakuDuplicateMergeWindowMs,
                duplicateMergeCountThreshold = danmakuDuplicateMergeCountThreshold,
                allowScroll = danmakuAllowScroll,
                allowTop = danmakuAllowTop,
                allowBottom = danmakuAllowBottom,
                allowColorful = danmakuAllowColorful,
                allowSpecial = danmakuAllowSpecial,
                weightFilterLevel = danmakuWeightFilterLevel,
                hideInteractiveCommands = danmakuHideInteractiveCommands,
                showBlockRuleEditor = true,
                showSmartOcclusionSection = true,
                blockRulesRaw = danmakuBlockRulesRaw,
                smartOcclusion = danmakuSmartOcclusion,
                fullscreenWidthMode = danmakuFullscreenPanelWidthMode,
                portraitDisplayAreaMode = portraitDanmakuDisplayAreaMode,
                showSyncSection = showDanmakuSyncSection,
                cloudSyncEnabled = danmakuCloudSyncEnabled,
                syncUiState = danmakuSyncUiState,
                onSliderPreview = actions.onDanmakuSliderPreview,
                onOpacityChange = onDanmakuOpacityChange,
                onFontScaleChange = onDanmakuFontScaleChange,
                onFontWeightChange = onDanmakuFontWeightChange,
                onSpeedChange = onDanmakuSpeedChange,
                onDisplayAreaChange = onDanmakuDisplayAreaChange,
                onStrokeWidthChange = onDanmakuStrokeWidthChange,
                onLineHeightChange = onDanmakuLineHeightChange,
                onScrollDurationSecondsChange = onDanmakuScrollDurationSecondsChange,
                onStaticDurationSecondsChange = onDanmakuStaticDurationSecondsChange,
                onScrollFixedVelocityChange = onDanmakuScrollFixedVelocityChange,
                onStaticDanmakuToScrollChange = onDanmakuStaticToScrollChange,
                onMassiveModeChange = onDanmakuMassiveModeChange,
                onMergeDuplicatesChange = onDanmakuMergeDuplicatesChange,
                onDuplicateMergeWindowMsChange = onDanmakuDuplicateMergeWindowMsChange,
                onDuplicateMergeCountThresholdChange = onDanmakuDuplicateMergeCountThresholdChange,
                onAllowScrollChange = onDanmakuAllowScrollChange,
                onAllowTopChange = onDanmakuAllowTopChange,
                onAllowBottomChange = onDanmakuAllowBottomChange,
                onAllowColorfulChange = onDanmakuAllowColorfulChange,
                onAllowSpecialChange = onDanmakuAllowSpecialChange,
                onWeightFilterLevelChange = onDanmakuWeightFilterLevelChange,
                onHideInteractiveCommandsChange = onDanmakuHideInteractiveCommandsChange,
                onBlockRulesRawChange = onDanmakuBlockRulesRawChange,
                onSmartOcclusionChange = onDanmakuSmartOcclusionChange,
                onFullscreenWidthModeChange = onDanmakuFullscreenPanelWidthModeChange,
                onPortraitDisplayAreaModeChange = onPortraitDanmakuDisplayAreaModeChange,
                onCloudSyncEnabledChange = onDanmakuCloudSyncEnabledChange,
                onSyncNowClick = onDanmakuSyncNowClick,
                onShowDanmakuPool = onShowDanmakuPool?.let { action ->
                    {
                        showDanmakuSettings = false
                        action()
                    }
                },
                onDismiss = { showDanmakuSettings = false }
            )
        }

        // --- 9.  [新增] 视频设置面板 ---
        if (showCdnDiagnostics) {
            AppModalBottomSheet(
                onDismissRequest = { showCdnDiagnostics = false },
                presentationOverride = AppModalPresentation.BottomSheet,
                backgroundBlurBehind = true,
            ) {
                AppSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.container(ContainerLevel.Sheet),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = (configuration.screenHeightDp * 0.8f).dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppText("CDN 测速与线路", style = MaterialTheme.typography.titleLarge)
                        PlaybackCdnDiagnostics(
                            diagnostics = cdnLineDiagnostics,
                            checking = isCdnProbing,
                            canCheck = cdnCount > 0 && !isCdnProbing,
                            onCheck = onProbeCdnCandidates,
                            onSwitchTo = onSwitchCdnTo,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextButton(onClick = { showCdnDiagnostics = false }) { AppText("关闭") }
                    }
                }
            }
        }

        if (showVideoSettings) {
            VideoSettingsPanel(
                sleepTimerMinutes = sleepTimerMinutes,
                onSleepTimerChange = onSleepTimerChange,
                onReload = onReloadVideo,
                onDanmakuSettingsClick = {
                    showVideoSettings = false
                    showDanmakuSettings = true
                },
                onShowDanmakuPool = onShowDanmakuPool?.let { action ->
                    {
                        showVideoSettings = false
                        action()
                    }
                },
                currentQualityLabel = currentQualityLabel,
                qualityLabels = qualityLabels,
                qualityIds = qualityIds,
                switchableQualityIds = switchableQualityIds,
                isLoggedIn = isLoggedIn,
                isVip = isVip,
                onQualitySelected = { index ->
                    val id = qualityIds.getOrNull(index) ?: 0
                    onQualityChange(id)
                    showVideoSettings = false
                },
                currentSpeed = currentSpeed,
                onSpeedChange = { speed ->
                    currentSpeed = speed
                    onPlaybackSpeedChange(speed)
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
                cdnLineDiagnostics = cdnLineDiagnostics,
                isCdnProbing = isCdnProbing,
                onSwitchCdn = onSwitchCdn,
                onSwitchCdnTo = { index ->
                    onSwitchCdnTo(index)
                    showVideoSettings = false
                },
                onProbeCdnCandidates = onProbeCdnCandidates,
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
                currentAudioQuality = selectedAudioQuality,
                availableAudioQualities = availableAudioQualities,
                onAudioQualityChange = { quality ->
                    onAudioQualityChange(quality)
                    showVideoSettings = false
                },
                anime4kEnabled = anime4kEnabled,
                anime4kAvailable = anime4kAvailable,
                anime4kBypassReason = anime4kBypassReason,
                videoEnhancementAlgorithm = videoEnhancementAlgorithm,
                anime4kPreset = anime4kPreset,
                fsrSharpness = fsrSharpness,
                onAnime4kToggle = onAnime4kToggle,
                onVideoEnhancementAlgorithmChange = onVideoEnhancementAlgorithmChange,
                onAnime4kPresetChange = onAnime4kPresetChange,
                onFsrSharpnessChange = onFsrSharpnessChange,
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
                isFullscreen = isFullscreen,
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
            OverlayChapterListPanel(
                viewPoints = viewPoints,
                progressProvider = displayedProgressProvider,
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
                val pageSelectorMaxWidthDp = remember(configuration.screenWidthDp, isFullscreen) {
                    resolvePageSelectorSheetMaxWidthDp(
                        windowWidthDp = configuration.screenWidthDp,
                        isFullscreen = isFullscreen
                    )
                }
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Sheet),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .then(
                            if (pageSelectorMaxWidthDp != null) {
                                Modifier.widthIn(max = pageSelectorMaxWidthDp.dp)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        )
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
                            coverUrl = coverUrl,
                            currentPageIndex = currentPageIndex,
                            forceGridMode = true,
                            blockParentVerticalScroll = true,
                            onDismissRequest = { showPageSelectorSheet = false },
                            onPageSelect = { index ->
                                showPageSelectorSheet = false
                                onPageSelect(index)
                            }
                        )
                    }
                }
            }
        }

        if (shouldConsumeBackgroundGesturesForEndDrawer(endDrawerVisible)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(endDrawerVisible) {
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
            visible = endDrawerVisible && isFullscreen,
            closeProgress = endDrawerCloseProgress,
            onDismiss = onDismissEndDrawer,
            relatedVideos = relatedVideos,
            ugcSeason = ugcSeason,
            currentBvid = bvid,
            currentCid = cid,
            initialSelectedTab = endDrawerInitialTab,
            ownerName = videoOwnerName,
            ownerFace = videoOwnerFace,
            isFollowed = isFollowed,
            followStatusKnown = followStatusKnown,
            onToggleFollow = onToggleFollow,
            onVideoClick = { vid, options ->
                onDismissEndDrawer()
                onDrawerVideoClick(vid, options)
            },
            hazeState = drawerHazeState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // --- 12. 📺 投屏对话框 ---
        val currentCastSignature = remember(currentAid, cid, currentQuality, currentVideoUrl, currentAudioUrl) {
            buildCastMediaSourceSignature(currentAid, cid, currentQuality, currentVideoUrl, currentAudioUrl)
        }
        if (showCastDialog) {
            DeviceListDialog(
                onDismissRequest = { showCastDialog = false },
                onPluginCastDeviceSelected = { plugin, route ->
                    scope.launch {
                        val resolution = resolveCastPlayUrl(context, currentAid, cid, currentQuality, currentVideoUrl)
                        if (resolution == null) {
                            showCastDialog = false
                            android.widget.Toast.makeText(context, "投屏地址解析失败", android.widget.Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val pluginCastUrl = resolution.url
                        val mediaRequest = CastPluginMediaRequest(
                            url = pluginCastUrl,
                            title = videoTitle,
                            creator = videoOwnerName,
                            contentType = resolution.contentType
                        )
                        val result = plugin.cast(context, route, mediaRequest)
                        showCastDialog = false
                        if (result.isSuccess) {
                            val state = plugin.playbackState.value
                            if (shouldActivatePluginPlaybackAfterCast(state)) {
                                player.pause()
                                activeCastPlugin = plugin
                                activeCastRoute = route
                                lastCastMediaSignature = currentCastSignature
                                lastCastMediaUrl = pluginCastUrl
                            } else {
                                activeCastPlugin = null
                                activeCastRoute = null
                                lastCastMediaSignature = null
                                lastCastMediaUrl = null
                            }
                        }
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

        // --- 13. 🔄 Active Cast quality/source reload ---
        LaunchedEffect(currentCastSignature, activeCastPlugin, activeCastRoute, pluginPlaybackStatus.first) {
            val plugin = activeCastPlugin ?: return@LaunchedEffect
            val route = activeCastRoute ?: return@LaunchedEffect
            if (!shouldReloadActiveCastAfterMediaSourceChange(
                    activePluginExists = true,
                    activeRouteExists = true,
                    pluginState = pluginPlaybackState.value,
                    currentSignature = currentCastSignature,
                    lastCastSignature = lastCastMediaSignature
                )) {
                return@LaunchedEffect
            }

            val resolution = resolveCastPlayUrl(context, currentAid, cid, currentQuality, currentVideoUrl)
            if (resolution == null) {
                android.widget.Toast.makeText(context, "投屏地址解析失败", android.widget.Toast.LENGTH_SHORT).show()
                return@LaunchedEffect
            }

            if (resolution.url == lastCastMediaUrl) {
                lastCastMediaSignature = currentCastSignature
                return@LaunchedEffect
            }

            val request = CastPluginMediaRequest(
                url = resolution.url,
                title = videoTitle,
                creator = videoOwnerName,
                contentType = resolution.contentType,
                startPositionMs = pluginPlaybackState.value.currentPositionMs.coerceAtLeast(0L),
                autoplay = pluginPlaybackState.value.isPlaying
            )

            val result = plugin.cast(context, route, request)
            if (result.isSuccess) {
                lastCastMediaSignature = currentCastSignature
                lastCastMediaUrl = resolution.url
            } else {
                android.widget.Toast.makeText(
                    context,
                    "投屏画质切换失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

/**
 *  竖屏模式顶部控制栏
 *
 * 包含返回首页按钮、听视频/投屏与更多菜单；经典布局另有分享按钮
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
    onNotInterested: () -> Unit = {},
    sleepTimerMinutes: Int? = null,
    onSleepTimerChange: (Int?) -> Unit = {},
    viewportWidthDpOverride: Int? = null,
    // 📺 [新增] 投屏
    onCastClick: () -> Unit = {},
    showCastButton: Boolean = true,
    /** 紧凑布局隐藏顶栏分享，并收紧按钮间距。 */
    compactPlayerChrome: Boolean = false,
    /** 系统状态栏可见时为顶栏加 statusBarsPadding，避免与系统图标重叠。 */
    statusBarVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val useMiuixNonGlassChrome = isMiuixNonGlassEnabled()
    val configuration = LocalConfiguration.current
    val uiLayoutWidthDp = remember(configuration.screenWidthDp, viewportWidthDpOverride) {
        (viewportWidthDpOverride ?: configuration.screenWidthDp).coerceAtLeast(1)
    }
    val moreIcon = rememberAppMoreIcon()
    val shareIcon = rememberAppShareIcon()
    val layoutPolicy = remember(uiLayoutWidthDp, compactPlayerChrome) {
        resolvePortraitTopBarLayoutPolicy(
            widthDp = uiLayoutWidthDp,
            compact = compactPlayerChrome
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (shouldApplyStatusBarPaddingToPortraitTopBar(statusBarVisible = statusBarVisible)) {
                    Modifier.statusBarsPadding()
                } else {
                    Modifier
                }
            )
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
            // 独立导航使用圆形玻璃，原生按钮保留点击行为。
            PlayerControlGlassDock(enabled = true, shape = CircleShape) {
                AppIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                ) {
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                    )
                }
            }

            PlayerControlGlassDock(enabled = true, shape = CircleShape) {
                AppIconButton(
                    onClick = onHome,
                    modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = "主界面",
                        tint = Color.White,
                        modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                    )
                }

            }

            // 👀 在线人数
            if (onlineCount.isNotEmpty()) {
                AppText(
                    text = onlineCount,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = layoutPolicy.onlineCountFontSp.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 相关操作共享一个 Dock。
        PlayerControlGlassDock(enabled = true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(layoutPolicy.rightSectionSpacingDp.dp)
            ) {
                //  听视频模式按钮 - 激活时保留背景色
                AppIconButton(
                    onClick = onAudioMode,
                    modifier = Modifier
                        .size(layoutPolicy.buttonSizeDp.dp)
                        .then(
                            if (isAudioOnly) Modifier.background(AppContentColors.accent(), CircleShape)
                            else Modifier
                        )
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Headphones,
                        contentDescription = "听视频",
                        tint = if (isAudioOnly) AppContentColors.onAccent() else Color.White,
                        modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                    )
                }

                if (showCastButton) {
                    AppIconButton(
                        onClick = onCastClick,
                        modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Tv,
                            contentDescription = "投屏",
                            tint = Color.White,
                            modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                        )
                    }
                }

                //  设置按钮 - 无背景
                AppIconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                ) {
                    AppIcon(
                        imageVector = moreIcon,
                        contentDescription = "设置",
                        tint = Color.White,
                        modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                    )
                }

                if (useMiuixNonGlassChrome) {
                    AppDropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        AppDropdownMenuItem(
                            text = { AppText("播放设置") },
                            onClick = { showMoreMenu = false; onSettings() },
                        )
                        AppDropdownMenuItem(
                            text = { AppText(if (sleepTimerMinutes == null) "定时关闭" else "取消定时关闭（${sleepTimerMinutes}分钟）") },
                            onClick = {
                                showMoreMenu = false
                                onSleepTimerChange(if (sleepTimerMinutes == null) 30 else null)
                            },
                        )
                        AppDropdownMenuItem(
                            text = { AppText("不感兴趣") },
                            onClick = { showMoreMenu = false; onNotInterested() },
                        )
                        if (compactPlayerChrome) {
                            AppDropdownMenuItem(
                                text = { AppText("分享") },
                                onClick = { showMoreMenu = false; onShare() },
                            )
                        }
                    }
                } else {
                    AppDropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        AppDropdownMenuItem(
                            text = { AppText("播放设置") },
                            onClick = { showMoreMenu = false; onSettings() }
                        )
                        AppDropdownMenuItem(
                            text = { AppText(if (sleepTimerMinutes == null) "定时关闭" else "取消定时关闭（${sleepTimerMinutes}分钟）") },
                            onClick = {
                                showMoreMenu = false
                                onSleepTimerChange(if (sleepTimerMinutes == null) 30 else null)
                            }
                        )
                        AppDropdownMenuItem(
                            text = { AppText("不感兴趣") },
                            onClick = { showMoreMenu = false; onNotInterested() }
                        )
                        if (compactPlayerChrome) {
                            AppDropdownMenuItem(
                                text = { AppText("分享") },
                                onClick = { showMoreMenu = false; onShare() }
                            )
                        }
                    }
                }

                if (!compactPlayerChrome) {
                    // 分享按钮 - 无背景
                    AppIconButton(
                        onClick = onShare,
                        modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                    ) {
                        AppIcon(
                            imageVector = shareIcon,
                            contentDescription = "分享",
                            tint = Color.White,
                            modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun PlaybackInsightPanel(
    presentation: PlaybackInsightPresentation,
    diagnosticEvents: List<String>,
    onCopyReport: (() -> Unit)?,
    onDismiss: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    presentationProvider: (() -> PlaybackInsightPresentation)? = null,
) {
    val displayedPresentation = presentationProvider?.invoke() ?: presentation
    val panelShape = AppShapes.container(ContainerLevel.Floating)
    val realtimeHazeState = hazeState?.takeIf {
        shouldAllowRuntimeShaderBackedHazeEffect(Build.VERSION.SDK_INT)
    }
    AppSurface(
        modifier = modifier.then(
            if (realtimeHazeState != null) {
                Modifier.unifiedBlur(
                    hazeState = realtimeHazeState,
                    shape = panelShape,
                    surfaceType = BlurSurfaceType.DRAWER_OR_SHEET
                )
            } else {
                Modifier
            }
        ),
        shape = panelShape,
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (realtimeHazeState != null) 0.72f else 0.94f
        ),
        contentColor = AppContentColors.primaryText(),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText("播放器洞察", style = MaterialTheme.typography.titleMedium)
                    AppText(
                        displayedPresentation.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText()
                    )
                }
                if (onCopyReport != null) {
                    AppTextButton(onClick = onCopyReport) {
                        AppText("复制")
                    }
                }
                AppIconButton(onClick = onDismiss) {
                    AppIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭"
                    )
                }
            }
            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                displayedPresentation.sections.forEach { (section, rows) ->
                    item(key = section.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppText(
                                section.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = AppContentColors.accent()
                            )
                            rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppText(
                                        row.label,
                                        modifier = Modifier.weight(0.8f),
                                        color = AppContentColors.secondaryText(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    AppText(
                                        row.value,
                                        modifier = Modifier.weight(1.2f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                if (onCopyReport != null && diagnosticEvents.isNotEmpty()) {
                    item(key = "diagnostic-events") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppText(
                                "诊断事件",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppContentColors.accent()
                            )
                            diagnosticEvents.takeLast(6).asReversed().forEach { event ->
                                AppText(
                                    event,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
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
    initialSelectedTab: Int = 0,
    // UP Info
    ownerName: String,
    ownerFace: String,
    // Interaction States
    isFollowed: Boolean,
    followStatusKnown: Boolean,
    // Callbacks
    onToggleFollow: () -> Unit,
    onVideoClick: (String, android.os.Bundle?) -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    closeProgress: androidx.compose.runtime.MutableFloatState = remember { mutableFloatStateOf(1f) },
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val widthSizeClass = remember(configuration.screenWidthDp) {
        com.android.purebilibili.core.util.resolveWindowWidthSizeClass(
            configuration.screenWidthDp.dp
        )
    }
    val foldPosture = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.posture
    val deviceUiProfile = remember(widthSizeClass, foldPosture) {
        resolveDeviceUiProfile(
            widthSizeClass = widthSizeClass,
            foldPosture = foldPosture,
        )
    }
    val cardAnimationEnabled by SettingsManager
        .getCardAnimationEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
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
    var requestDrawerDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(OverlayControlFadeSpec),
        exit = fadeOut(OverlayControlFadeSpec),
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
                        onClick = { requestDrawerDismiss?.invoke() ?: onDismiss() }
                    )
            )

            // 抽屉内容：横向拖动会跟手，松开后根据距离和速度回弹或关闭。
            LandscapeSidePanel(
                visible = true,
                edge = LandscapeSidePanelEdge.End,
                width = layoutPolicy.drawerWidthDp.dp,
                closeProgress = closeProgress,
                enableControlCenterMotion = true,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxHeight(),
            ) { requestDismiss ->
                SideEffect { requestDrawerDismiss = requestDismiss }
                AppSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = AppContentColors.primaryText(),
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
                            .landscapeControlCenterGroup(index = 0)
                            .padding(layoutPolicy.headerPaddingDp.dp)
                    ) {
                        // Row 1: UP主头像、名字、关注按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 头像
                            coil3.compose.AsyncImage(
                                model = ownerFace,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(layoutPolicy.avatarSizeDp.dp)
                                    .clip(CircleShape)
                                    .background(MediaContrastPalette.ControlSurface),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(layoutPolicy.headerSpacingDp.dp))

                            // 名字
                            AppText(
                                text = ownerName,
                                color = AppContentColors.primaryText(),
                                fontSize = layoutPolicy.titleFontSp.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(layoutPolicy.headerSpacingDp.dp))

                            // 关注按钮 (放在右上角)
                            AppButton(
                                onClick = rememberFollowActionWithConfirmation(
                                    targetKey = currentBvid,
                                    userName = ownerName,
                                    isFollowing = isFollowed,
                                    onFollowClick = onToggleFollow,
                                ),
                                enabled = followStatusKnown,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowed) AppContentColors.primaryText().copy(0.2f) else AppContentColors.accent(),
                                    contentColor = if (isFollowed) AppContentColors.primaryText() else AppContentColors.onAccent(),
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = layoutPolicy.followButtonHorizontalPaddingDp.dp,
                                    vertical = 0.dp
                                ),
                                modifier = Modifier.height(layoutPolicy.followButtonHeightDp.dp),
                                shape = AppShapes.container(ContainerLevel.Card)
                            ) {
                                AppText(
                                    if (!followStatusKnown) "—" else if (isFollowed) "已关注" else "+ 关注",
                                    fontSize = layoutPolicy.followButtonFontSp.sp
                                )
                            }
                        }
                    }

                    AppHorizontalDivider(color = dividerColor)

                    // 2. Tab Row
                    val hasSeason = ugcSeason != null && ugcSeason.sections.isNotEmpty()
                    var selectedTab by remember { mutableIntStateOf(0) } // 0: 推荐, 1: 合集
                    LaunchedEffect(visible, initialSelectedTab, hasSeason) {
                        if (visible) {
                            selectedTab = if (hasSeason) initialSelectedTab.coerceIn(0, 1) else 0
                        }
                    }

                    if (hasSeason) {
                        AppThemeAdaptiveTabRow(
                            // Glass indicator fills the measured slot; native geometry keeps its defaults.
                            liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                            role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                            options = listOf(
                                AppSegmentOption(0, "推荐视频"),
                                AppSegmentOption(1, "合集列表"),
                            ),
                            selectedValue = selectedTab,
                            onSelectionChange = { selectedTab = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .landscapeControlCenterGroup(index = 1)
                                .padding(horizontal = layoutPolicy.listContentPaddingDp.dp),
                            labelFontSize = layoutPolicy.followButtonFontSp.sp,
                            dragSelectionEnabled = false,
                        )
                    } else {
                        // 只有推荐，显示标题
                        AppText(
                            text = "推荐视频",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.landscapeControlCenterGroup(index = 1)
                                .padding(layoutPolicy.listContentPaddingDp.dp)
                        )
                    }

                    // 3. 列表内容
                    Box(modifier = Modifier.weight(1f).landscapeControlCenterGroup(index = 2)) {
                        if (selectedTab == 0) {
                            // 推荐视频列表（滚动位置跨抽屉开关保留）
                            val relatedListState = rememberSaveable(
                                saver = LazyListState.Saver
                            ) { LazyListState() }
                            LazyColumn(
                                state = relatedListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(layoutPolicy.listContentPaddingDp.dp),
                                verticalArrangement = Arrangement.spacedBy(layoutPolicy.listItemSpacingDp.dp)
                            ) {
                                itemsIndexed(
                                    items = relatedVideos,
                                    key = { index, item ->
                                        resolveIndexedVideoLazyKey(
                                            namespace = "overlay_related",
                                            index = index,
                                            bvid = item.bvid,
                                            aid = item.aid,
                                            cid = item.cid
                                        )
                                    }
                                ) { _, video ->
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
                        } else if (hasSeason) {
                            // 合集列表
                            val seasonSections = ugcSeason.sections
                            val collectionListState = rememberLazyListState()
                            val currentLazyIndex = remember(seasonSections, currentBvid, currentCid) {
                                resolveCurrentUgcEpisodeLazyListIndex(
                                    sections = seasonSections,
                                    currentBvid = currentBvid,
                                    currentCid = currentCid
                                )
                            }
                            LaunchedEffect(visible, selectedTab, currentLazyIndex) {
                                if (visible && selectedTab == 1 && currentLazyIndex >= 0) {
                                    collectionListState.scrollToItem(currentLazyIndex)
                                }
                            }
                            LazyColumn(
                                state = collectionListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(layoutPolicy.listContentPaddingDp.dp),
                                verticalArrangement = Arrangement.spacedBy(layoutPolicy.listItemSpacingDp.dp)
                            ) {
                                seasonSections.forEach { section ->
                                    item {
                                        AppText(
                                            text = section.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AppContentColors.secondaryText(),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    items(section.episodes, key = { it.id }) { episode ->
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
        modifier = Modifier.appElasticPress().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) AppContentColors.accent() else AppContentColors.primaryText(),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        AppText(
            text = label,
            color = if (isActive) AppContentColors.accent() else AppContentColors.secondaryText(),
            style = MaterialTheme.typography.labelSmall
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
    val favoriteIcon = rememberAppBookmarkIcon()
    val coinIcon = rememberAppCoinIcon()
    val likeIcon = rememberAppLikeIcon()
    val likeFilledIcon = rememberAppLikeFilledIcon()

    var isLongPressing by remember { mutableStateOf(false) }
    var longPressProgress by remember { mutableFloatStateOf(0f) }
    val progressDuration = 1500

    val animatedProgress by animateFloatAsState(
        targetValue = if (isLongPressing) 1f else 0f,
        animationSpec = if (isLongPressing) {
            androidx.compose.animation.core.tween(durationMillis = progressDuration, easing = LinearEasing)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 200, easing = AppMotionEasing.Continuity)
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
            icon = if (isLiked) likeFilledIcon else likeIcon,
            label = "点赞",
            progress = longPressProgress,
            progressColor = AppContentColors.accent(),
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
            icon = coinIcon,
            label = "投币",
            progress = longPressProgress,
            progressColor = AppContentColors.accent(),
            isActive = isCoined,
            onClick = onCoinClick,
            showProgress = longPressProgress > 0.05f,
            layoutPolicy = layoutPolicy
        )

        // 收藏 (显示时带进度)
        LandscapeProgressIcon(
            icon = favoriteIcon,
            label = "收藏",
            progress = longPressProgress,
            progressColor = AppContentColors.accent(),
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
            .appElasticPress(dragEnabled = onLongPress == null)
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

            AppIcon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) progressColor else AppContentColors.primaryText(),
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.height(layoutPolicy.interactionLabelTopSpacingDp.dp))
        AppText(
            text = label,
            color = if (isActive) progressColor else AppContentColors.secondaryText(),
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
            .background(if (isCurrent) AppContentColors.primaryText().copy(0.1f) else Color.Transparent, AppShapes.container(ContainerLevel.Tag))
            .padding(4.dp)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .fillMaxHeight()
                .clip(AppShapes.container(ContainerLevel.Tag))
        ) {
             coil3.compose.AsyncImage(
                model = video.pic,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText(
                text = FormatUtils.formatDuration(video.duration),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 2.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 标题和UP
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AppText(
                text = video.title,
                color = if (isCurrent) AppContentColors.accent() else AppContentColors.primaryText(),
                fontSize = layoutPolicy.itemTitleFontSp.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = (layoutPolicy.itemTitleFontSp + 3).sp
            )
            AppText(
                text = video.owner.name,
                color = AppContentColors.secondaryText(),
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
            .background(if (isCurrent) AppContentColors.accent().copy(alpha = 0.1f) else Color.Transparent, AppShapes.container(ContainerLevel.Tag))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 封面 (如果有 arc 信息)
        if (episode.arc != null && requireNotNull(episode.arc).pic.isNotEmpty()) {
            val checkedEpisodeArc = requireNotNull(episode.arc)
            Box(
                modifier = Modifier
                    .aspectRatio(16f / 9f)
                    .fillMaxHeight()
                    .clip(AppShapes.container(ContainerLevel.Tag))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                coil3.compose.AsyncImage(
                    model = checkedEpisodeArc.pic,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText(
                    text = FormatUtils.formatDuration(checkedEpisodeArc.duration),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            // 无封面时的占位 (或纯文本模式)
             if (isCurrent) {
                AppIcon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(layoutPolicy.metaIconSizeDp.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                 Box(
                    modifier = Modifier
                        .size((layoutPolicy.metaIconSizeDp / 2).dp)
                        .background(AppContentColors.primaryText().copy(0.3f), CircleShape)
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
            AppText(
                text = episode.title,
                color = if (isCurrent) AppContentColors.accent() else AppContentColors.primaryText(),
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
            val episodeStat = episode.arc?.stat
            if (episodeStat != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 播放量
                    AppIcon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(layoutPolicy.metaIconSizeDp.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    AppText(
                        text = FormatUtils.formatStat(episodeStat.view.toLong()),
                        color = AppContentColors.secondaryText(),
                        fontSize = layoutPolicy.itemMetaFontSp.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 弹幕
                    AppIcon(
                        imageVector = Icons.Filled.ChatBubble,
                        contentDescription = null,
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(layoutPolicy.metaIconSizeDp.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    AppText(
                        text = FormatUtils.formatStat(episodeStat.danmaku.toLong()),
                        color = AppContentColors.secondaryText(),
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
