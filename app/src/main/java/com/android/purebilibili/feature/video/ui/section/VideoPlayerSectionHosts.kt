@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.ui.section

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.View
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackParameters
import androidx.media3.ui.PlayerView
import com.android.purebilibili.R
import com.android.purebilibili.core.input.gamepad.GamepadControlEngine
import com.android.purebilibili.core.input.gamepad.GamepadPlayerAction
import com.android.purebilibili.core.input.gamepad.GamepadScene
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.core.ui.adaptive.CustomShortcutOverrides
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.IdUtils
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.toUserFriendlyMessage
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.repository.DanmakuRepository
import com.android.purebilibili.data.repository.DynamicVoteRepository
import com.android.purebilibili.feature.anime4k.gl.Anime4KDisplayScaleMode
import com.android.purebilibili.feature.anime4k.gl.Anime4KGLSurfaceView
import com.android.purebilibili.feature.audio.player.SleepTimerFinishedEvent
import com.android.purebilibili.feature.video.danmaku.CommandDanmakuItem
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.danmaku.VoteDanmakuKind
import com.android.purebilibili.feature.video.danmaku.rememberPlayerDanmakuTargetFrameRateHz
import com.android.purebilibili.feature.video.danmaku.resolveHotDanmakuSlotHeightPx
import com.android.purebilibili.feature.video.danmaku.shouldUseIndependentDanmakuSurface
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.PlayerKeyAction
import com.android.purebilibili.feature.video.player.calculateSeekTargetPositionMs
import com.android.purebilibili.feature.video.player.resolvePlayerKeyAction
import com.android.purebilibili.feature.video.postcard.VideoPostcardDraft
import com.android.purebilibili.feature.video.screen.TabletTheaterModeController
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.ui.components.SleepTimerGoodnightOverlay
import com.android.purebilibili.feature.video.ui.components.SponsorContributionOverlay
import com.android.purebilibili.feature.video.ui.components.SponsorSkipButton
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.resolvePlayerViewportAspectRatio
import com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout
import com.android.purebilibili.feature.video.ui.gesture.PlayerGestureInputSurface
import com.android.purebilibili.feature.video.ui.overlay.AdvancedDanmakuOverlay
import com.android.purebilibili.feature.video.ui.overlay.BasDanmakuOverlay
import com.android.purebilibili.feature.video.ui.overlay.CommandDanmakuOverlay
import com.android.purebilibili.feature.video.ui.overlay.CommandDanmakuOverlayState
import com.android.purebilibili.feature.video.ui.overlay.HotDanmakuBar
import com.android.purebilibili.feature.video.ui.overlay.hotDanmakuBarMinHeightPx
import com.android.purebilibili.feature.video.ui.overlay.resolveCommandDanmakuBottomInsetPx
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction
import com.android.purebilibili.feature.video.viewmodel.SponsorContributionUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.danmaku.parser.AdvancedDanmakuData
import com.android.purebilibili.danmaku.parser.bas.BasDanmaku
import com.android.purebilibili.plugin.sdk.Anime4KConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 拆出手柄/键盘事件分发、Anime4K 独立渲染面、弹幕复合层与尾部浮层宿主，
 * 降低 VideoPlayerSectionContent 单方法 DEX 指令规模以稳定落在 ART 16,384 上限内。
 */
@Composable
internal fun VideoPlayerGamepadControllerEffect(
    context: Context,
    playerState: VideoPlayerState,
    danmakuManager: DanmakuManager,
    audioManager: AudioManager,
    settingsScope: CoroutineScope,
    uiState: VideoPlaybackUiState,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    isScreenLocked: Boolean,
    likeStatusKnown: Boolean,
    currentAudioQuality: Int,
    getActivity: () -> Activity?,
    onShowControls: () -> Unit,
    onToggleControls: () -> Unit,
    onToggleScreenLock: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleLike: () -> Unit,
    onTriple: () -> Unit,
    onPageSelect: (Int) -> Unit,
    showPlayerFeedback: (String) -> Unit,
) {
    val gamepadSceneToken = remember { Any() }
    var gamepadSavedSpeedParams by remember { mutableStateOf<PlaybackParameters?>(null) }
    val latestGamepadUiState by rememberUpdatedState(uiState)
    val latestGamepadInPip by rememberUpdatedState(isInPipMode)
    val latestGamepadLocked by rememberUpdatedState(isScreenLocked)
    val latestGamepadToggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    val latestGamepadToggleLike by rememberUpdatedState(onToggleLike)
    val latestGamepadTriple by rememberUpdatedState(onTriple)
    val latestGamepadLikeKnown by rememberUpdatedState(likeStatusKnown)
    val latestGamepadPageSelect by rememberUpdatedState(onPageSelect)
    val latestGamepadAudioQuality by rememberUpdatedState(currentAudioQuality)
    val latestShowControls by rememberUpdatedState(onShowControls)
    val latestToggleControls by rememberUpdatedState(onToggleControls)
    val latestToggleScreenLock by rememberUpdatedState(onToggleScreenLock)
    val latestShowFeedback by rememberUpdatedState(showPlayerFeedback)

    DisposableEffect(gamepadSceneToken, isFullscreen, isInPipMode) {
        if (!isInPipMode) {
            GamepadControlEngine.registerScene(
                token = gamepadSceneToken,
                scene = if (isFullscreen) {
                    GamepadScene.VIDEO_FULLSCREEN
                } else {
                    GamepadScene.VIDEO_DETAIL
                },
            )
        } else {
            GamepadControlEngine.unregisterScene(gamepadSceneToken)
        }
        onDispose {
            gamepadSavedSpeedParams?.let { saved ->
                playerState.player.playbackParameters = saved
                gamepadSavedSpeedParams = null
            }
            GamepadControlEngine.unregisterScene(gamepadSceneToken)
        }
    }

    LaunchedEffect(gamepadSceneToken) {
        GamepadControlEngine.playerActions.collect { action ->
            if (!GamepadControlEngine.isTopScene(gamepadSceneToken)) {
                return@collect
            }
            if (latestGamepadInPip) return@collect
            if (latestGamepadLocked && action !is GamepadPlayerAction.ToggleScreenLock) {
                return@collect
            }
            val player = playerState.player
            when (action) {
                GamepadPlayerAction.PlayPause -> {
                    togglePlayerPlaybackFromUserAction(player)
                    latestShowControls()
                }
                is GamepadPlayerAction.SeekRelative -> {
                    val target = calculateSeekTargetPositionMs(
                        currentPositionMs = player.currentPosition,
                        durationMs = player.duration,
                        action = PlayerKeyAction.SeekRelative(action.deltaMs),
                    ) ?: return@collect
                    seekPlayerFromUserAction(player, target)
                    danmakuManager.seekTo(target)
                    latestShowControls()
                }
                is GamepadPlayerAction.AdjustVolume -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        if (action.direction >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI,
                    )
                }
                is GamepadPlayerAction.AdjustBrightness -> {
                    val window = getActivity()?.window ?: return@collect
                    val params = window.attributes
                    val base = if (params.screenBrightness >= 0f) {
                        params.screenBrightness
                    } else {
                        runCatching {
                            Settings.System.getInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                            ) / 255f
                        }.getOrDefault(0.5f)
                    }
                    params.screenBrightness = (base + action.deltaFraction).coerceIn(0.02f, 1.0f)
                    window.attributes = params
                }
                GamepadPlayerAction.ToggleMute -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.FLAG_SHOW_UI,
                    )
                }
                GamepadPlayerAction.ToggleDanmaku -> {
                    val newState = !danmakuManager.isEnabled
                    danmakuManager.isEnabled = newState
                    if (!newState) danmakuManager.clear()
                    settingsScope.launch {
                        SettingsManager.setDanmakuEnabled(context, newState)
                    }
                    latestShowFeedback(if (newState) "弹幕已开启" else "弹幕已关闭")
                }
                GamepadPlayerAction.ToggleFullscreen -> {
                    latestGamepadToggleFullscreen()
                }
                GamepadPlayerAction.ToggleLike -> {
                    latestGamepadToggleLike()
                }
                GamepadPlayerAction.TripleAction -> {
                    if (latestGamepadLikeKnown) latestGamepadTriple()
                }
                GamepadPlayerAction.ToggleScreenLock -> {
                    latestToggleScreenLock()
                }
                GamepadPlayerAction.PreviousPart -> {
                    val pages = (latestGamepadUiState as? VideoPlaybackUiState.Success)?.info?.pages.orEmpty()
                    val currentCid = (latestGamepadUiState as? VideoPlaybackUiState.Success)?.info?.cid ?: 0L
                    val currentIndex = pages.indexOfFirst { it.cid == currentCid }
                    if (currentIndex > 0) {
                        latestGamepadPageSelect(pages[currentIndex - 1].page)
                        latestShowFeedback("切换至上一分P")
                    } else {
                        latestShowFeedback("已经是第一分P了")
                    }
                }
                GamepadPlayerAction.NextPart -> {
                    val pages = (latestGamepadUiState as? VideoPlaybackUiState.Success)?.info?.pages.orEmpty()
                    val currentCid = (latestGamepadUiState as? VideoPlaybackUiState.Success)?.info?.cid ?: 0L
                    val currentIndex = pages.indexOfFirst { it.cid == currentCid }
                    if (currentIndex in 0 until pages.lastIndex) {
                        latestGamepadPageSelect(pages[currentIndex + 1].page)
                        latestShowFeedback("切换至下一分P")
                    } else {
                        latestShowFeedback("已经是最后一分P了")
                    }
                }
                GamepadPlayerAction.ToggleControls -> {
                    latestToggleControls()
                }
                is GamepadPlayerAction.SpeedBoostChanged -> {
                    if (action.active) {
                        if (gamepadSavedSpeedParams == null) {
                            gamepadSavedSpeedParams = player.playbackParameters
                        }
                        player.playbackParameters = resolveSpeedSafePlaybackParameters(
                            requestedSpeed = action.speed,
                            currentAudioQuality = latestGamepadAudioQuality,
                        )
                    } else {
                        gamepadSavedSpeedParams?.let { saved ->
                            player.playbackParameters = saved
                            gamepadSavedSpeedParams = null
                        }
                    }
                }
            }
        }
    }
}

internal fun handleVideoPlayerKeyEvent(
    event: KeyEvent,
    context: Context,
    playerState: VideoPlayerState,
    danmakuManager: DanmakuManager,
    audioManager: AudioManager,
    settingsScope: CoroutineScope,
    uiState: VideoPlaybackUiState,
    isFullscreen: Boolean,
    isScreenLocked: Boolean,
    isInPipMode: Boolean,
    danmakuComposerVisible: Boolean,
    keyboardShortcutOverrides: CustomShortcutOverrides,
    theaterModeController: TabletTheaterModeController?,
    likeStatusKnown: Boolean,
    playerViewRef: PlayerView?,
    videoSizeState: Pair<Int, Int>,
    onShowControls: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleLike: () -> Unit,
    onCoin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTriple: () -> Unit,
    onToggleScreenLock: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onApplyPlaybackSpeed: (Float) -> Unit,
    captureScreenshot: (PlayerView, Int, Int, String) -> Unit,
    showPlayerFeedback: (String) -> Unit,
): Boolean {
    val action = resolvePlayerKeyAction(
        event = event,
        isScreenLocked = isScreenLocked,
        isInPipMode = isInPipMode,
        isTextInputActive = danmakuComposerVisible,
        overrides = keyboardShortcutOverrides,
    ) ?: return false

    return when (action) {
        PlayerKeyAction.PlayPause -> {
            togglePlayerPlaybackFromUserAction(playerState.player)
            onShowControls()
            true
        }
        is PlayerKeyAction.SeekRelative -> {
            val player = playerState.player ?: return false
            val target = calculateSeekTargetPositionMs(
                currentPositionMs = player.currentPosition,
                durationMs = player.duration,
                action = action,
            ) ?: return false
            seekPlayerFromUserAction(player, target)
            danmakuManager.seekTo(target)
            onShowControls()
            true
        }
        is PlayerKeyAction.SeekPercent -> {
            val player = playerState.player ?: return false
            val target = calculateSeekTargetPositionMs(
                currentPositionMs = player.currentPosition,
                durationMs = player.duration,
                action = action,
            ) ?: return false
            seekPlayerFromUserAction(player, target)
            danmakuManager.seekTo(target)
            onShowControls()
            true
        }
        PlayerKeyAction.VolumeUp -> {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI,
            )
            true
        }
        PlayerKeyAction.VolumeDown -> {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI,
            )
            true
        }
        PlayerKeyAction.ToggleMute -> {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI,
            )
            true
        }
        PlayerKeyAction.ToggleTheaterMode -> {
            if (!isFullscreen && theaterModeController != null) {
                theaterModeController.toggle()
                true
            } else {
                false
            }
        }
        PlayerKeyAction.ToggleFullscreen -> {
            onToggleFullscreen()
            true
        }
        PlayerKeyAction.ToggleDanmaku -> {
            val newState = !danmakuManager.isEnabled
            danmakuManager.isEnabled = newState
            if (!newState) {
                danmakuManager.clear()
            }
            // 弹幕开关由横竖屏共用；快捷键与按钮一样保存，换片后继续沿用。
            settingsScope.launch {
                SettingsManager.setDanmakuEnabled(context, newState)
            }
            showPlayerFeedback(if (newState) "弹幕已开启" else "弹幕已关闭")
            true
        }
        PlayerKeyAction.ToggleLike -> {
            onToggleLike()
            true
        }
        PlayerKeyAction.Coin -> {
            onCoin()
            true
        }
        PlayerKeyAction.ToggleFavorite -> {
            onToggleFavorite()
            true
        }
        PlayerKeyAction.TripleAction -> {
            if (likeStatusKnown) onTriple()
            true
        }
        PlayerKeyAction.TakeScreenshot -> {
            if (playerViewRef != null) {
                captureScreenshot(
                    playerViewRef,
                    videoSizeState.first,
                    videoSizeState.second,
                    (uiState as? VideoPlaybackUiState.Success)?.info?.title.orEmpty(),
                )
            }
            true
        }
        PlayerKeyAction.ToggleScreenLock -> {
            onToggleScreenLock()
            true
        }
        PlayerKeyAction.PreviousPart -> {
            val pages = (uiState as? VideoPlaybackUiState.Success)?.info?.pages.orEmpty()
            val currentCid = (uiState as? VideoPlaybackUiState.Success)?.info?.cid ?: 0L
            val currentIndex = pages.indexOfFirst { it.cid == currentCid }
            if (currentIndex > 0) {
                onPageSelect(pages[currentIndex - 1].page)
                showPlayerFeedback("切换至上一分P")
            } else {
                showPlayerFeedback("已经是第一分P了")
            }
            true
        }
        PlayerKeyAction.NextPart -> {
            val pages = (uiState as? VideoPlaybackUiState.Success)?.info?.pages.orEmpty()
            val currentCid = (uiState as? VideoPlaybackUiState.Success)?.info?.cid ?: 0L
            val currentIndex = pages.indexOfFirst { it.cid == currentCid }
            if (currentIndex in 0 until pages.lastIndex) {
                onPageSelect(pages[currentIndex + 1].page)
                showPlayerFeedback("切换至下一分P")
            } else {
                showPlayerFeedback("已经是最后一分P了")
            }
            true
        }
        is PlayerKeyAction.SetSpeed -> {
            onApplyPlaybackSpeed(action.speed)
            showPlayerFeedback("${action.speed}x 倍速")
            true
        }
    }
}

@Composable
internal fun VideoPlayerAnime4kSurfaceHost(
    playerState: VideoPlayerState,
    anime4kConfig: Anime4KConfig,
    videoSizeState: Pair<Int, Int>,
    effectiveFlippedHorizontal: Boolean,
    effectiveFlippedVertical: Boolean,
    displayScaleMode: Anime4KDisplayScaleMode,
    sharedSurfaceVisible: Boolean,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    scale: Float,
    panX: Float,
    panY: Float,
    currentSurfaceViewRef: () -> Anime4KGLSurfaceView?,
    onSurfaceViewRefChange: (Anime4KGLSurfaceView?) -> Unit,
    onInputSurfaceChanged: (Surface?) -> Unit,
    onFirstFrameRendered: () -> Unit,
    onPipelineFailed: () -> Unit,
    onReleaseSurfaceView: (Anime4KGLSurfaceView) -> Unit,
) {
    val density = LocalDensity.current
    val lastAnime4kDisplayState = remember(playerState.player) {
        arrayOfNulls<Any>(6)
    }
    AndroidView(
        factory = { ctx ->
            Anime4KGLSurfaceView(ctx, initialConfig = anime4kConfig).apply {
                onSurfaceViewRefChange(this)
                this.onInputSurfaceChanged = { surface ->
                    if (currentSurfaceViewRef() === this) {
                        onInputSurfaceChanged(surface)
                    }
                }
                this.onFirstFrameRendered = {
                    if (currentSurfaceViewRef() === this) onFirstFrameRendered()
                }
                this.onPipelineError = { error ->
                    if (currentSurfaceViewRef() === this) {
                        Logger.e("VideoPlayerSection", "Anime4K 管线不可用，已回退原始视频输出", error)
                        onPipelineFailed()
                    }
                }
                updateConfig(anime4kConfig)
                updateInputSize(videoSizeState.first, videoSizeState.second)
                updateFlip(effectiveFlippedHorizontal, effectiveFlippedVertical)
                updateDisplayScaleMode(displayScaleMode)
                lastAnime4kDisplayState[0] = anime4kConfig
                lastAnime4kDisplayState[1] = videoSizeState.first
                lastAnime4kDisplayState[2] = videoSizeState.second
                lastAnime4kDisplayState[3] = effectiveFlippedHorizontal
                lastAnime4kDisplayState[4] = effectiveFlippedVertical
                lastAnime4kDisplayState[5] = displayScaleMode
                visibility = View.VISIBLE
            }
        },
        update = { surfaceView ->
            onSurfaceViewRefChange(surfaceView)
            if (lastAnime4kDisplayState[0] != anime4kConfig) {
                surfaceView.updateConfig(anime4kConfig)
                lastAnime4kDisplayState[0] = anime4kConfig
            }
            if (lastAnime4kDisplayState[1] != videoSizeState.first ||
                lastAnime4kDisplayState[2] != videoSizeState.second
            ) {
                surfaceView.updateInputSize(videoSizeState.first, videoSizeState.second)
                lastAnime4kDisplayState[1] = videoSizeState.first
                lastAnime4kDisplayState[2] = videoSizeState.second
            }
            if (lastAnime4kDisplayState[3] != effectiveFlippedHorizontal ||
                lastAnime4kDisplayState[4] != effectiveFlippedVertical
            ) {
                surfaceView.updateFlip(effectiveFlippedHorizontal, effectiveFlippedVertical)
                lastAnime4kDisplayState[3] = effectiveFlippedHorizontal
                lastAnime4kDisplayState[4] = effectiveFlippedVertical
            }
            if (lastAnime4kDisplayState[5] != displayScaleMode) {
                surfaceView.updateDisplayScaleMode(displayScaleMode)
                lastAnime4kDisplayState[5] = displayScaleMode
            }
            val targetVisibility = if (sharedSurfaceVisible) View.VISIBLE else View.INVISIBLE
            if (surfaceView.visibility != targetVisibility) {
                surfaceView.visibility = targetVisibility
            }
            val targetAlpha = if (sharedSurfaceVisible) 1f else 0f
            if (surfaceView.alpha != targetAlpha) {
                surfaceView.alpha = targetAlpha
            }
        },
        onRelease = { surfaceView ->
            surfaceView.onInputSurfaceChanged = {}
            surfaceView.onFirstFrameRendered = {}
            surfaceView.onPipelineError = {}
            onReleaseSurfaceView(surfaceView)
        },
        modifier = with(density) {
            Modifier
                .requiredSize(
                    width = viewportWidthPx.toDp(),
                    height = viewportHeightPx.toDp(),
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panX
                    translationY = panY
                }
        },
    )
}

@Composable
internal fun VideoPlayerDanmakuLayerHost(
    bvid: String,
    uiState: VideoPlaybackUiState,
    playerState: VideoPlayerState,
    danmakuManager: DanmakuManager,
    settingsScope: CoroutineScope,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    observedIsPlaying: Boolean,
    useTextureVideoSurface: Boolean,
    anime4kActive: Boolean,
    currentAspectRatio: VideoAspectRatio,
    videoSizeState: Pair<Int, Int>,
    playerContentModifier: Modifier,
    capturePlayerPage: Boolean,
    playerPageBackdrop: PlayerPageBackdrop,
    shouldShowDanmakuLayer: Boolean,
    danmakuRenderWorkActive: Boolean,
    danmakuBrightnessFactor: Float,
    hdrDanmakuComposeRenderEffect: RenderEffect?,
    danmakuHotBarEnabled: Boolean,
    danmakuFontScale: Float,
    danmakuLineHeight: Float,
    danmakuFontWeight: Int,
    useFullscreenDanmakuBaseline: Boolean,
    advancedDanmakuList: List<AdvancedDanmakuData>,
    basDanmakuList: List<BasDanmaku>,
    commandState: CommandDanmakuOverlayState,
    visibleCommandDanmakuList: List<CommandDanmakuItem>,
    measuredBottomControlsHeightPx: Int,
    bottomGestureExclusionHeightDp: Int,
    isFollowed: Boolean,
    followStatusKnown: Boolean,
    likeStatusKnown: Boolean,
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    isFollowSubmitting: Boolean,
    isTripleSubmitting: Boolean,
    isSendingDanmakuComposer: Boolean,
    likedDanmakuIdsFlow: StateFlow<Set<Long>>,
    onLikeDanmakuToggle: (Long, Boolean) -> Unit,
    onSendSameDanmaku: ((String, (Result<Unit>) -> Unit) -> Unit)?,
    onFollow: () -> Unit,
    onToggleLike: () -> Unit,
    onCoin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRelatedVideoClick: (String, Bundle?) -> Unit,
    showPlayerFeedback: (String) -> Unit,
) {
    val useIndependentSurface = shouldUseIndependentDanmakuSurface(
        useTextureVideoSurface = useTextureVideoSurface,
        anime4kActive = anime4kActive,
        capturePlayerPage = capturePlayerPage,
        isInPipMode = isInPipMode,
    )
    val renderingPaused = !shouldShowDanmakuLayer || !danmakuRenderWorkActive
    val targetFrameRateHz = rememberPlayerDanmakuTargetFrameRateHz(
        player = playerState.player,
        isPlaying = observedIsPlaying && !renderingPaused,
    )
    val hotBarCid = (uiState as? VideoPlaybackUiState.Success)?.info?.cid ?: 0L
    val canShowHotBar = danmakuHotBarEnabled && shouldShowDanmakuLayer
    val canSendHotBarDanmaku = canShowHotBar && !isInPipMode && LocalVisualWorkAllowed.current
    val hotDanmakuSendFailureMessage = stringResource(R.string.hot_danmaku_send_error)
    var sendingHotDanmaku by remember(danmakuManager, bvid, hotBarCid, canSendHotBarDanmaku) {
        mutableStateOf(false)
    }
    var pendingCommandLinkItem by remember {
        mutableStateOf<CommandDanmakuItem?>(null)
    }

    // 玻璃、MD3 与 Miuix 共用可复用弹幕视图；全屏切换仅更新视口和字体。
    val viewportAspectRatio = resolvePlayerViewportAspectRatio(
        isFullscreen = isFullscreen,
        selectedAspectRatio = currentAspectRatio,
    )
    BoxWithConstraints(
        modifier = playerContentModifier
            .then(if (capturePlayerPage) playerPageBackdrop.danmaku.modifier else Modifier)
            .graphicsLayer { alpha = if (shouldShowDanmakuLayer) 1f else 0f }
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportLayout = remember(maxWidth, maxHeight, viewportAspectRatio, videoSizeState) {
            with(density) {
                resolveVideoViewportLayout(
                    containerWidth = maxWidth.roundToPx(),
                    containerHeight = maxHeight.roundToPx(),
                    aspectRatio = viewportAspectRatio,
                    videoWidth = videoSizeState.first,
                    videoHeight = videoSizeState.second,
                )
            }
        }
        val danmakuContainerHeightPx = with(density) { maxHeight.roundToPx() }
        val hotDanmakuBarMinHeight = hotDanmakuBarMinHeightPx()
        val danmakuSurfaceModifier = with(density) {
            Modifier.requiredSize(
                width = viewportLayout.width.toDp(),
                height = viewportLayout.height.toDp(),
            )
        }
        DanmakuViewportHost(danmakuSurfaceModifier) { viewport ->
            // 计数条显隐只改变内容，不改变引擎槽位；槽位仅在功能、视口或字号变化时更新。
            val hotDanmakuSlotHeightPx = remember(
                danmakuHotBarEnabled,
                viewport,
                danmakuFontScale,
                danmakuLineHeight,
                useFullscreenDanmakuBaseline,
                hotDanmakuBarMinHeight,
            ) {
                resolveHotDanmakuSlotHeightPx(
                    enabled = danmakuHotBarEnabled,
                    viewport = viewport,
                    fontScale = danmakuFontScale,
                    lineHeightMultiplier = danmakuLineHeight,
                    isFullscreen = useFullscreenDanmakuBaseline,
                    barMinHeightPx = hotDanmakuBarMinHeight,
                )
            }
            SideEffect {
                danmakuManager.updateHotDanmakuSlotHeight(hotDanmakuSlotHeightPx)
            }
            VideoDanmakuRenderSurface(
                manager = danmakuManager,
                viewport = viewport,
                useFullscreenTextSize = useFullscreenDanmakuBaseline,
                renderingPaused = renderingPaused,
                brightnessFactor = danmakuBrightnessFactor,
                useIndependentSurface = useIndependentSurface,
                surfaceVisible = shouldShowDanmakuLayer,
                targetFrameRateHz = targetFrameRateHz,
                modifier = Modifier.fillMaxSize(),
            )
            // Above the passive renderer, below BAS links and interactive command danmaku.
            PlayerGestureInputSurface(modifier = Modifier.fillMaxSize())
            AdvancedDanmakuOverlay(
                viewport = viewport,
                danmakuList = advancedDanmakuList,
                player = playerState.player,
                opacity = danmakuManager.liveOpacity,
                fontScale = danmakuFontScale,
                fontWeight = danmakuFontWeight,
                renderingPaused = renderingPaused,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = hdrDanmakuComposeRenderEffect
                    },
            )
            BasDanmakuOverlay(
                items = basDanmakuList,
                player = playerState.player,
                viewport = viewport,
                opacity = danmakuManager.liveOpacity,
                fontScale = danmakuFontScale,
                fontWeight = danmakuFontWeight,
                renderingPaused = renderingPaused,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = hdrDanmakuComposeRenderEffect
                    },
            )
            // 控件测量前使用共享高度种子，避免玻璃切换引起互动弹幕避让跳变。
            val commandControlsReservePx = measuredBottomControlsHeightPx.takeIf { it > 0 }
                ?: with(density) { bottomGestureExclusionHeightDp.dp.roundToPx() }
            CommandDanmakuOverlay(
                viewport = viewport,
                bottomInsetPx = resolveCommandDanmakuBottomInsetPx(
                    viewportHeightPx = viewport.heightPx,
                    surfaceHeightPx = danmakuContainerHeightPx,
                    controlsReserveHeightPx = commandControlsReservePx,
                ),
                state = commandState,
                visible = shouldShowDanmakuLayer && danmakuRenderWorkActive,
                fontScale = danmakuFontScale,
                items = visibleCommandDanmakuList,
                player = playerState.player,
                onFollowClick = onFollow,
                onLikeClick = onToggleLike,
                onCoinClick = onCoin,
                onFavoriteClick = onToggleFavorite,
                onLinkClick = { item ->
                    val targetBvid = item.linkBvid.ifBlank {
                        if (item.linkAid > 0L) IdUtils.av2bv(item.linkAid) else ""
                    }
                    if (targetBvid.isBlank()) {
                        showPlayerFeedback("关联视频信息缺失，无法跳转")
                    } else {
                        pendingCommandLinkItem = item
                    }
                },
                onVoteSubmit = { item, option, optionIndex ->
                    val success = uiState as? VideoPlaybackUiState.Success
                    if (item.voteKind == VoteDanmakuKind.GRADE) {
                        val gradeScore = option.score
                        if (success == null || success.info.aid <= 0L || success.info.cid <= 0L ||
                            item.voteId.toLongOrNull() == null || gradeScore == null
                        ) {
                            commandState.endSubmission(item.id)
                            showPlayerFeedback(if (success == null) "打分失败：播放信息不可用" else "打分失败：缺少有效打分信息")
                        } else {
                            // Enter the finally block even if this UI scope is already cancelled.
                            settingsScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                var accepted = false
                                try {
                                    val result = DanmakuRepository.submitGradeDanmaku(
                                        aid = success.info.aid,
                                        cid = success.info.cid,
                                        progress = item.startTimeMs,
                                        gradeId = item.voteId,
                                        gradeScore = gradeScore,
                                    )
                                    if (result.isSuccess) {
                                        accepted = true
                                        commandState.completeGradeSubmission(item, option)
                                        val summary = DanmakuRepository.getGradeDanmakuSummary(
                                            cid = success.info.cid,
                                            aid = success.info.aid,
                                            gradeId = item.voteId,
                                        )
                                        summary.onSuccess { commandState.updateGradeSummary(item.id, it) }
                                            .onFailure {
                                                showPlayerFeedback("打分成功，统计暂不可用")
                                            }
                                    } else {
                                        showPlayerFeedback(
                                            result.exceptionOrNull()?.toUserFriendlyMessage("打分失败") ?: "打分失败"
                                        )
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    showPlayerFeedback(
                                        if (accepted) "打分成功，统计暂不可用" else e.toUserFriendlyMessage("打分失败")
                                    )
                                } finally {
                                    // This only releases pending work; an accepted score remains confirmed.
                                    commandState.endSubmission(item.id)
                                }
                            }
                        }
                    } else {
                        val voteId = item.voteId.toLongOrNull()
                        if (success == null || voteId == null) {
                            commandState.endSubmission(item.id)
                            showPlayerFeedback("投票失败：缺少有效投票信息")
                        } else {
                            settingsScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    val result = DynamicVoteRepository.submitVote(
                                        voteId = voteId,
                                        optionIndexes = listOf(optionIndex),
                                    )
                                    result.onSuccess { commandState.completeVoteSubmission(item, option) }
                                        .onFailure {
                                            showPlayerFeedback(it.toUserFriendlyMessage("投票失败"))
                                        }
                                } finally {
                                    commandState.endSubmission(item.id)
                                }
                            }
                        }
                    }
                },
                isFollowing = isFollowed,
                followStatusKnown = followStatusKnown,
                likeStatusKnown = likeStatusKnown,
                isLiked = isLiked,
                isCoined = isCoined,
                isFavorited = isFavorited,
                isFollowSubmitting = isFollowSubmitting,
                isTripleSubmitting = isTripleSubmitting,
                modifier = Modifier.fillMaxSize(),
            )
            // 关联视频命令弹幕：跳转前确认
            pendingCommandLinkItem?.let { linkItem ->
                val dialogTargetBvid = linkItem.linkBvid.ifBlank {
                    if (linkItem.linkAid > 0L) IdUtils.av2bv(linkItem.linkAid) else ""
                }
                AppAlertDialog(
                    onDismissRequest = { pendingCommandLinkItem = null },
                    title = { AppText("跳转关联视频") },
                    text = {
                        AppText("是否跳转到「${linkItem.linkTitle.ifBlank { linkItem.content }}」？")
                    },
                    confirmButton = {
                        AppTextButton(onClick = {
                            val item = pendingCommandLinkItem
                            pendingCommandLinkItem = null
                            if (item != null) {
                                commandState.dismiss(item.id)
                                onRelatedVideoClick(dialogTargetBvid, null)
                            }
                        }) {
                            AppText("跳转")
                        }
                    },
                    dismissButton = {
                        AppTextButton(onClick = { pendingCommandLinkItem = null }) {
                            AppText("取消")
                        }
                    },
                )
            }
            // 3.1 高赞弹幕悬浮条：当前时间窗内点赞 Top-N，支持一键跟发
            if (canShowHotBar && danmakuRenderWorkActive) {
                val hotBarLikedDanmakuIds by likedDanmakuIdsFlow.collectAsStateWithLifecycle()
                key(danmakuManager, hotBarCid) {
                    HotDanmakuBar(
                        getDanmakuIndex = { danmakuManager.getHotDanmakuIndex(hotBarCid) },
                        onPresentationChange = danmakuManager::updateHotDanmakuPresentation,
                        player = playerState.player,
                        likedDanmakuIds = hotBarLikedDanmakuIds,
                        onLikeDanmaku = onLikeDanmakuToggle,
                        isSending = sendingHotDanmaku || isSendingDanmakuComposer,
                        opacity = danmakuManager.liveOpacity,
                        slotHeight = with(density) { hotDanmakuSlotHeightPx.toDp() },
                        onSendSame = onSendSameDanmaku?.takeIf { canSendHotBarDanmaku }?.let { send ->
                            { message: String ->
                                if (!sendingHotDanmaku && !isSendingDanmakuComposer) {
                                    sendingHotDanmaku = true
                                    send(message) { result ->
                                        sendingHotDanmaku = false
                                        result.onFailure {
                                            showPlayerFeedback(it.toUserFriendlyMessage(hotDanmakuSendFailureMessage))
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.VideoPlayerSuccessPostOverlayHost(
    bvid: String,
    uiState: VideoPlaybackUiState.Success,
    playerState: VideoPlayerState,
    danmakuManager: DanmakuManager,
    activeDanmakuScope: com.android.purebilibili.core.store.DanmakuSettingsScope,
    sponsorSegment: SponsorSegment?,
    showSponsorSkipButton: Boolean,
    suppressTransientOverlaysForTransition: Boolean,
    onSponsorSkip: () -> Unit,
    onSponsorDismiss: () -> Unit,
    onSponsorVote: (Int) -> Unit,
    sponsorContributionState: SponsorContributionUiState,
    onSponsorContributionCategoryChange: (String) -> Unit,
    onSponsorContributionActionTypeChange: (String) -> Unit,
    onSponsorContributionSubmit: () -> Unit,
    onSponsorContributionCancel: () -> Unit,
    pendingPostcardHintDraft: VideoPostcardDraft?,
    onClearPendingPostcardHint: () -> Unit,
    onSavePostcardToNote: ((String, String, Long) -> Unit)?,
    showPlayerFeedback: (String) -> Unit,
    sleepTimerFinishedEvent: SleepTimerFinishedEvent?,
    miniPlayerManagerForOverlay: MiniPlayerManager,
    showDanmakuPoolSheet: Boolean,
    likedDanmakuIdsFlow: StateFlow<Set<Long>>,
    onSeekTo: (Long) -> Unit,
    onLikeDanmaku: (Long, Boolean) -> Unit,
    onRecallDanmaku: (Long) -> Unit,
    onReportDanmaku: (Long, Int) -> Unit,
    onDismissDanmakuPoolSheet: () -> Unit,
) {
    SponsorSkipButton(
        segment = sponsorSegment,
        visible = showSponsorSkipButton && !suppressTransientOverlaysForTransition,
        onSkip = onSponsorSkip,
        onDismiss = onSponsorDismiss,
        onVote = onSponsorVote,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 60.dp, end = 16.dp),
    )
    SponsorContributionOverlay(
        state = sponsorContributionState,
        onCategoryChange = onSponsorContributionCategoryChange,
        onActionTypeChange = onSponsorContributionActionTypeChange,
        onSubmit = onSponsorContributionSubmit,
        onCancel = onSponsorContributionCancel,
    )
    VideoPlayerFrameCaptureHost(
        bvid = bvid,
        pendingPostcardHintDraft = pendingPostcardHintDraft,
        onClearPendingHint = onClearPendingPostcardHint,
        onSavePostcardToNote = onSavePostcardToNote,
        showPlayerFeedback = showPlayerFeedback,
    )
    SleepTimerGoodnightOverlay(
        event = sleepTimerFinishedEvent,
        onExtend15Minutes = { miniPlayerManagerForOverlay.extendAfterSleepTimer() },
        onDismiss = {
            sleepTimerFinishedEvent?.let { miniPlayerManagerForOverlay.consumeSleepTimerFinishedEvent(it.id) }
        },
        extendLabel = "再看 15 分钟",
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 14.dp),
    )
    if (showDanmakuPoolSheet) {
        VideoPlayerDanmakuPoolHost(
            bvid = uiState.info.bvid,
            cid = uiState.info.cid,
            danmakuManager = danmakuManager,
            activeDanmakuScope = activeDanmakuScope,
            currentPositionMs = playerState.player?.currentPosition ?: 0L,
            likedDanmakuIdsFlow = likedDanmakuIdsFlow,
            onSeekTo = onSeekTo,
            onLikeDanmaku = onLikeDanmaku,
            onRecallDanmaku = onRecallDanmaku,
            onReportDanmaku = onReportDanmaku,
            onDismiss = onDismissDanmakuPoolSheet,
        )
    }
}
