package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.verticalScroll
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.android.purebilibili.core.ui.AppAlertDialog
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppDurationPicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.state.rememberVideoPlayerState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.toEngagementSeed
import com.android.purebilibili.feature.video.viewmodel.toSupplementSeed
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

internal fun resolveAudioPlayModeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENTIAL -> "顺序播放"
    PlayMode.SHUFFLE -> "随机播放"
    PlayMode.REPEAT_ONE -> "单曲循环"
    PlayMode.REPEAT_ALL -> "列表循环"
}

internal fun shouldUseAudioModeLiquidPlayModeControl(
    supportsIndependentLiquidGlass: Boolean,
    androidNativeLiquidGlassEnabled: Boolean
): Boolean = androidNativeLiquidGlassEnabled

internal enum class AudioModePlayPauseAction {
    PAUSE,
    RESUME,
    RESTART_FROM_BEGINNING,
    PREPARE_AND_RESUME
}

internal fun resolveAudioModePlayPauseAction(
    isPlaying: Boolean,
    playbackState: Int,
    playWhenReady: Boolean
): AudioModePlayPauseAction {
    if (isPlaying) return AudioModePlayPauseAction.PAUSE
    return when (playbackState) {
        Player.STATE_ENDED -> AudioModePlayPauseAction.RESTART_FROM_BEGINNING
        Player.STATE_IDLE -> AudioModePlayPauseAction.PREPARE_AND_RESUME
        Player.STATE_BUFFERING -> if (playWhenReady) AudioModePlayPauseAction.PAUSE else AudioModePlayPauseAction.RESUME
        Player.STATE_READY -> AudioModePlayPauseAction.RESUME
        else -> if (playWhenReady) AudioModePlayPauseAction.PAUSE else AudioModePlayPauseAction.RESUME
    }
}

internal fun shouldCreateAudioModeStandalonePlayer(
    hasPlayer: Boolean,
    initialBvid: String
): Boolean = !hasPlayer && initialBvid.isNotBlank()

internal fun resolveAudioModePageSwitchAutoPlay(): Boolean = true

internal fun resolveAudioModeCollectionSwitchAutoPlay(): Boolean = true

internal fun resolveAudioModeOrientationActionLabel(isLandscape: Boolean): String =
    if (isLandscape) "竖屏" else "横屏"

internal fun resolveAudioModeRequestedOrientation(isLandscape: Boolean): Int =
    if (isLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

internal fun shouldShowAudioModePipButton(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.O

internal fun parseAudioModeSleepTimerInput(raw: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.all { it.isDigit() }) {
        return trimmed.toIntOrNull()?.takeIf { it > 0 }
    }
    val match = Regex("""^(\d{1,3})\s*[:：]\s*(\d{1,2})$""").matchEntire(trimmed) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    if (minutes !in 0..59) return null
    return (hours * 60 + minutes).takeIf { it > 0 }
}

internal fun formatAudioModeSleepTimerButtonLabel(minutes: Int?): String {
    minutes ?: return "定时关闭"
    return when {
        minutes < 60 -> "${minutes}分钟"
        minutes % 60 == 0 -> "${minutes / 60}小时"
        else -> "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }
}

internal fun formatAudioModeSleepTimerInput(minutes: Int?): String {
    minutes ?: return ""
    return if (minutes >= 60 && minutes % 60 != 0) {
        "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    } else {
        minutes.toString()
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

internal fun Player.handleAudioModePlayPause() {
    when (
        resolveAudioModePlayPauseAction(
            isPlaying = isPlaying,
            playbackState = playbackState,
            playWhenReady = playWhenReady
        )
    ) {
        AudioModePlayPauseAction.PAUSE -> pause()
        AudioModePlayPauseAction.RESUME -> play()
        AudioModePlayPauseAction.RESTART_FROM_BEGINNING -> {
            seekTo(0L)
            play()
        }
        AudioModePlayPauseAction.PREPARE_AND_RESUME -> {
            prepare()
            play()
        }
    }
}

private fun enterAudioModePip(activity: Activity?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) return
    val paramsBuilder = PictureInPictureParams.Builder().setAspectRatio(Rational(1, 1))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        paramsBuilder.setSeamlessResizeEnabled(true)
    }
    activity.enterPictureInPictureMode(paramsBuilder.build())
}

@Composable
fun AudioModeScreen(
    viewModel: VideoPlaybackViewModel,
    engagementViewModel: com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    composerViewModel: com.android.purebilibili.feature.video.viewmodel.VideoComposerViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    supplementViewModel: com.android.purebilibili.feature.video.viewmodel.VideoSupplementViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onVideoModeClick: (String, Long) -> Unit,
    isInPipMode: Boolean = false,
    initialBvid: String = "",
    initialCid: Long = 0L,
    initialResumePositionMs: Long = 0L,
    titleOverride: String? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subjectSnapshot by viewModel.subjectSnapshot.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context.findHostActivity()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val orientationActionLabel = resolveAudioModeOrientationActionLabel(isLandscape)
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.applyPlayerRequestedOrientation(originalOrientation)
        }
    }
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsStateWithLifecycle(
        initialValue = HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val liquidPlayModeControlEnabled = shouldUseAudioModeLiquidPlayModeControl(
        supportsIndependentLiquidGlass =
            playerChromeProfile.effects.supportsIndependentLiquidGlass,
        androidNativeLiquidGlassEnabled = homeSettings.otherGlassEnabled,
    )
    val shouldCreateStandalonePlayer = remember(initialBvid) {
        shouldCreateAudioModeStandalonePlayer(
            hasPlayer = viewModel.currentPlayer != null,
            initialBvid = initialBvid
        )
    }
    var cachedSuccessState by remember(initialBvid, initialCid) {
        mutableStateOf<VideoPlaybackUiState.Success?>(null)
    }
    var initialSourceResolved by remember(initialBvid, initialCid) { mutableStateOf(initialBvid.isBlank()) }
    // 本页是否已经发起（或由独立播放器发起）入口内容加载。
    var entryLoadRequested by remember(initialBvid, initialCid) {
        mutableStateOf(shouldCreateStandalonePlayer)
    }
    // 入口加载之后是否观察到过 Loading；只有此时到达的成功态才属于本次会话。
    var sawLoadingSinceEntry by remember(initialBvid, initialCid) { mutableStateOf(false) }

    LaunchedEffect(subjectSnapshot, uiState) {
        val subject = subjectSnapshot ?: return@LaunchedEffect
        val ready = uiState as? VideoPlaybackUiState.Success ?: return@LaunchedEffect
        engagementViewModel.bindSubject(
            subject,
            ready.toEngagementSeed()
        )
        composerViewModel.bindSubject(subject)
        supplementViewModel.bindSubject(
            subject,
            ready.toSupplementSeed()
        )
    }

    LaunchedEffect(initialBvid, initialCid, initialResumePositionMs, shouldCreateStandalonePlayer) {
        if (shouldCreateStandalonePlayer || initialBvid.isBlank()) return@LaunchedEffect
        entryLoadRequested = true
        // 本页建立时可能已有别的曲目在加载（例如队列里的「下一首」已经发起请求）：
        // 此时再发入口请求会取消并覆盖那次加载，把播放拉回入口曲目；交给首屏门控
        // 等这次加载落地即可。
        val currentState = viewModel.uiState.value
        val inFlightBvid = viewModel.detailState.value.requestBvid
        if (shouldSkipAudioModeEntryLoadForInFlightSwitch(
                isLoading = currentState is VideoPlaybackUiState.Loading,
                inFlightBvid = inFlightBvid,
                requestedBvid = initialBvid,
            )
        ) {
            return@LaunchedEffect
        }
        viewModel.loadVideo(
            bvid = initialBvid,
            cid = initialCid,
            autoPlay = true,
            fallbackResumePositionMs = initialResumePositionMs
        )
    }
    LaunchedEffect(uiState, initialBvid, initialCid, entryLoadRequested) {
        if (entryLoadRequested && uiState is VideoPlaybackUiState.Loading) {
            sawLoadingSinceEntry = true
        }
        val success = uiState as? VideoPlaybackUiState.Success ?: return@LaunchedEffect
        if (!initialSourceResolved) {
            initialSourceResolved = shouldResolveAudioModeInitialSource(
                requestedBvid = initialBvid,
                requestedCid = initialCid,
                loadedBvid = success.info.bvid,
                loadedCid = success.info.cid,
                entryLoadRequested = entryLoadRequested,
                sawLoadingSinceEntry = sawLoadingSinceEntry,
            )
        }
        if (initialSourceResolved) cachedSuccessState = success
    }
    val displayState = if (initialSourceResolved) {
        (uiState as? VideoPlaybackUiState.Success) ?: cachedSuccessState
    } else null
    val standalonePlayerState = if (shouldCreateStandalonePlayer) {
        rememberVideoPlayerState(
            context = context,
            viewModel = viewModel,
            bvid = initialBvid,
            cid = initialCid,
            fallbackResumePositionMs = initialResumePositionMs
        )
    } else {
        null
    }
    val player = standalonePlayerState?.player ?: viewModel.currentPlayer

    // 复用旧 Success 进入（如小横条展开）时播放器可能已被释放：无 player 且入口
    // 未携带 bvid 时，用缓存内容强制重载自愈，避免停留在只有封面的死页面。
    LaunchedEffect(player, displayState, initialBvid) {
        val success = displayState ?: return@LaunchedEffect
        if (player != null || initialBvid.isNotBlank()) return@LaunchedEffect
        val bvid = success.info.bvid
        if (bvid.isBlank()) return@LaunchedEffect
        viewModel.loadVideo(
            bvid = bvid,
            cid = success.info.cid,
            force = true,
            autoPlay = true,
            fallbackResumePositionMs = success.startPosition,
        )
    }
    DisposableEffect(viewModel) {
        viewModel.setAudioMode(true)
        // 听视频没有画面入场过渡，媒体 prepare 不必等它；若沿用详情页入场时写入的
        // "暂不许 prepare"，切歌请求会挂起在准备阶段，表现为一直加载、切换不完成。
        viewModel.setPlaybackPreparationAllowed(true)
        AudioNowPlayingSession.markListening()
        onDispose { viewModel.setAudioMode(false) }
    }

    if (displayState == null) {
        AudioModeInitialState(
            state = uiState,
            title = titleOverride?.takeIf { it.isNotBlank() } ?: "正在加载音频",
            onBack = onBack,
            onRetry = { viewModel.retry() }
        )
        return
    }

    val enterPip = remember(context) { { enterAudioModePip(context.findHostActivity()) } }
    AudioModeMusicPlayer(
        viewModel = viewModel,
        successState = displayState,
        player = player,
        onBack = onBack,
        onVideoModeClick = onVideoModeClick,
        isInPipMode = isInPipMode,
        showPipButton = shouldShowAudioModePipButton(Build.VERSION.SDK_INT),
        onEnterPip = enterPip,
        sleepTimerMinutes = sleepTimerMinutes,
        titleOverride = titleOverride,
        liquidGlassEffectsEnabled = liquidPlayModeControlEnabled,
        onToggleOrientation = {
            activity?.applyPlayerRequestedOrientation(resolveAudioModeRequestedOrientation(isLandscape))
        },
        orientationActionLabel = orientationActionLabel,
        engagementViewModel = engagementViewModel
    )
}

@Composable
private fun AudioModeInitialState(
    state: VideoPlaybackUiState,
    title: String,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(com.android.purebilibili.feature.audio.lyrics.halcyon.PlayerPalette.Default.middle)) {
        AppTextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 12.dp)
                .height(48.dp)
        ) {
            AppText("返回", color = Color.White)
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (state) {
                is VideoPlaybackUiState.Error -> {
                    AppText("音频加载失败", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    AppText(state.msg, color = Color.White.copy(alpha = 0.72f))
                    if (state.canRetry) {
                        AppTextButton(onClick = onRetry, modifier = Modifier.height(48.dp)) { AppText("重试", color = Color.White) }
                    }
                }
                else -> {
                    AdaptiveLoadingIndicator()
                    AppText(title, color = Color.White)
                    if (title != "正在加载音频") {
                        AppText("正在加载音频", color = Color.White.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudioModeSleepTimerDialog(
    currentMinutes: Int?,
    sleepAfterCurrent: Boolean = false,
    onSleepAfterCurrent: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSelectPreset: (Int?) -> Unit,
    onConfirmCustom: (Int) -> Unit
) {
    var customInput by remember(currentMinutes) {
        mutableStateOf(formatAudioModeSleepTimerInput(currentMinutes))
    }
    val parsedCustomMinutes = remember(customInput) { parseAudioModeSleepTimerInput(customInput) }
    val showCustomError = customInput.isNotBlank() && parsedCustomMinutes == null
    val presetOptions = listOf<Int?>(null, 15, 30, 60, 90)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("定时关闭") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppText(
                    text = "选择常用时长，或输入分钟数 / 小时:分钟，例如 90、1:30。",
                    color = AppContentColors.secondaryText(),
                    style = MaterialTheme.typography.bodySmall
                )
                if (onSleepAfterCurrent != null) {
                    AppTextButton(onClick = onSleepAfterCurrent) {
                        AppText(if (sleepAfterCurrent) "取消播完当前首后停止" else "播完当前首后停止")
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetOptions.forEach { minutes ->
                        val isSelected = !sleepAfterCurrent && currentMinutes == minutes
                        AppSurface(
                            onClick = { onSelectPreset(minutes) },
                            shape = AppShapes.container(ContainerLevel.Card),
                            color = if (isSelected) {
                                AppContentColors.accent()
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppText(
                                    text = formatAudioModeSleepTimerButtonLabel(minutes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) {
                                        AppContentColors.onAccent()
                                    } else {
                                        AppContentColors.secondaryText()
                                    }
                                )
                            }
                        }
                    }
                }
                AppDurationPicker(
                    minutes = parsedCustomMinutes ?: 0,
                    onMinutesChange = { customInput = it.toString() },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppOutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.take(8) },
                    singleLine = true,
                    label = { AppText("自定义时间") },
                    placeholder = { AppText("例如 45 或 1:30") },
                    isError = showCustomError,
                    supportingText = {
                        if (showCustomError) {
                            AppText("请输入正整数分钟，或 小时:分钟，分钟需在 0-59。")
                        } else if (parsedCustomMinutes != null) {
                            AppText("将于 ${formatAudioModeSleepTimerButtonLabel(parsedCustomMinutes)}后暂停播放")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = { parsedCustomMinutes?.let(onConfirmCustom) },
                enabled = parsedCustomMinutes != null
            ) { AppText("应用") }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } }
    )
}
