package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.video.subtitle.*
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlCallbacks
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** Plain state and callbacks shared by the bangumi surface and the common control overlay. */
data class BangumiPlayerFeatures(
    val commentVisible: Boolean = false,
    val commentOnLeft: Boolean = false,
    val commentWidthDp: Int = 0,
    val commentCloseProgressProvider: () -> Float = { 0f },
    val onCommentClick: () -> Unit = {},
    val onDanmakuInputClick: () -> Unit = {},
    val isInPipMode: Boolean = false,
    val onPipClick: () -> Unit = {},
    val isAudioOnly: Boolean = false,
    val onAudioOnlyToggle: () -> Unit = {},
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false,
    val onFlipHorizontal: () -> Unit = {},
    val onFlipVertical: () -> Unit = {},
    val onSaveCover: () -> Unit = {},
    val onDownloadAudio: () -> Unit = {},
    val sleepTimerMinutes: Int? = null,
    val onSleepTimerChange: (Int?) -> Unit = {},
    val subtitle: BangumiSubtitleState = BangumiSubtitleState(),
)

data class BangumiSubtitleState(
    val controls: SubtitleControlUiState = SubtitleControlUiState(),
    val callbacks: SubtitleControlCallbacks = SubtitleControlCallbacks(),
    val primaryCues: List<SubtitleCue> = emptyList(),
    val secondaryCues: List<SubtitleCue> = emptyList(),
)

@Composable
internal fun rememberBangumiSubtitles(bvid: String, cid: Long, player: Player): BangumiSubtitleState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preference by SettingsManager.getSubtitleAutoPreference(context)
        .collectAsStateWithLifecycle(initialValue = SubtitleAutoPreference.OFF)
    val positionLocked by SettingsManager.getSubtitlePositionLocked(context)
        .collectAsStateWithLifecycle(initialValue = true)
    var isMuted by remember(player) { mutableStateOf(player.volume == 0f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVolumeChanged(volume: Float) { isMuted = volume == 0f }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    var tracks by remember(bvid, cid) { mutableStateOf<List<SubtitleTrackMeta>>(emptyList()) }
    var selectedKey by remember(bvid, cid) { mutableStateOf<String?>(null) }
    var modeOverride by remember(bvid, cid) { mutableStateOf<SubtitleDisplayMode?>(null) }
    var enabled by remember(bvid, cid) { mutableStateOf(true) }
    var largeText by remember { mutableStateOf(false) }
    var primaryCues by remember(bvid, cid) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var secondaryCues by remember(bvid, cid) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    LaunchedEffect(bvid, cid) {
        if (bvid.isBlank() || cid <= 0L) return@LaunchedEffect
        val loaded = VideoRepository.getPlayerInfo(bvid, cid).getOrNull()?.subtitle?.subtitles
            ?.let(::mapPlayerInfoSubtitleTracks)?.let(::orderSubtitleTracksByPreference).orEmpty()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        tracks = loaded
    }
    val primary = tracks.firstOrNull { it.trackKey == selectedKey } ?: tracks.firstOrNull()
    val secondary = tracks.firstOrNull { it.trackKey != primary?.trackKey && it.lan.startsWith("en") }
        ?: tracks.firstOrNull { it.trackKey != primary?.trackKey }
    LaunchedEffect(bvid, cid, primary?.trackKey, secondary?.trackKey) {
        primaryCues = emptyList()
        secondaryCues = emptyList()
        suspend fun load(track: SubtitleTrackMeta?): List<SubtitleCue> {
            if (track == null) return emptyList()
            val loaded = VideoRepository.getSubtitleCues(
                track.subtitleUrl, bvid, cid, track.id, track.idStr, track.lan
            ).getOrNull().orEmpty()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            return loaded
        }
        primaryCues = load(primary)
        secondaryCues = load(secondary)
    }
    val mode = normalizeSubtitleDisplayMode(
        modeOverride ?: resolveSubtitleDisplayModeByAutoPreference(
            preference, primary != null, secondary != null,
            primary?.let(::isLikelyAiSubtitleTrack) == true,
            secondary?.let(::isLikelyAiSubtitleTrack) == true,
            isMuted,
        ), primary != null, secondary != null,
    )
    return BangumiSubtitleState(
        controls = SubtitleControlUiState(
            trackAvailable = tracks.isNotEmpty(), primaryAvailable = primary != null,
            secondaryAvailable = secondary != null, enabled = enabled && mode != SubtitleDisplayMode.OFF, displayMode = mode,
            primaryLabel = primary?.let(::resolveSubtitleTrackDisplayLabel).orEmpty(),
            secondaryLabel = secondary?.let(::resolveSubtitleTrackDisplayLabel).orEmpty(),
            trackOptions = buildSubtitleTrackOptions(tracks, primary?.trackKey),
            largeTextEnabled = largeText, positionLocked = positionLocked,
        ),
        callbacks = SubtitleControlCallbacks(
            onDisplayModeChange = { modeOverride = it; enabled = it != SubtitleDisplayMode.OFF },
            onEnabledChange = {
                enabled = it
                if (it && mode == SubtitleDisplayMode.OFF) modeOverride = SubtitleDisplayMode.PRIMARY_ONLY
            },
            onTrackSelected = { selectedKey = it; enabled = true; modeOverride = SubtitleDisplayMode.PRIMARY_ONLY },
            onLargeTextChange = { largeText = it },
            onPositionLockedChange = { scope.launch { SettingsManager.setSubtitlePositionLocked(context, it) } },
        ),
        primaryCues = primaryCues, secondaryCues = secondaryCues,
    )
}

/** Playback position polling stays in this small subtitle scope rather than the video surface. */
@Composable
internal fun BoxScope.BangumiSubtitleOverlay(
    player: Player,
    state: BangumiSubtitleState,
    controlsVisible: Boolean,
    isFullscreen: Boolean,
    viewportHeightPx: Int,
) {
    if (!state.controls.enabled || state.controls.displayMode == SubtitleDisplayMode.OFF) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val position by produceState(player.currentPosition, player, lifecycle, state.primaryCues, state.secondaryCues) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { value = player.currentPosition; delay(if (player.isPlaying) 120L else 500L) }
        }
    }
    val primary = if (shouldRenderPrimarySubtitle(state.controls.displayMode)) resolveSubtitleTextAt(state.primaryCues, position) else null
    val secondary = if (shouldRenderSecondarySubtitle(state.controls.displayMode)) resolveSubtitleTextAt(state.secondaryCues, position) else null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedOffset by SettingsManager.getSubtitleVerticalOffsetFraction(context)
        .collectAsStateWithLifecycle(initialValue = 0f)
    var offsetFraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(savedOffset) {
        if (!dragging) offsetFraction = normalizeSubtitleVerticalOffsetFraction(savedOffset)
    }
    val maxDragHeightPx = viewportHeightPx.takeIf { it > 0 }?.toFloat()
        ?: with(androidx.compose.ui.platform.LocalDensity.current) {
            androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx()
        }
    if (primary.isNullOrBlank() && secondary.isNullOrBlank()) return
    Column(
        Modifier.align(Alignment.BottomCenter)
            .padding(bottom = if (controlsVisible) 76.dp else if (isFullscreen) 24.dp else 12.dp)
            .offset { IntOffset(0, (offsetFraction * maxDragHeightPx).roundToInt()) }
            .fillMaxWidth(0.9f)
            .then(if (state.controls.positionLocked) Modifier else Modifier.pointerInput(maxDragHeightPx) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        val releasedOffset = offsetFraction
                        dragging = false
                        scope.launch { SettingsManager.setSubtitleVerticalOffsetFraction(context, releasedOffset) }
                    },
                    onDragCancel = { dragging = false; offsetFraction = savedOffset },
                ) { change, delta ->
                    change.consume()
                    offsetFraction = normalizeSubtitleVerticalOffsetFraction(offsetFraction + delta.y / maxDragHeightPx.coerceAtLeast(1f))
                }
            })
            .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!primary.isNullOrBlank()) AppText(primary, color = Color.White, fontSize = if (state.controls.largeTextEnabled) 22.sp else 16.sp)
        if (!secondary.isNullOrBlank()) AppText(secondary, color = Color.White, fontSize = if (state.controls.largeTextEnabled) 18.sp else 14.sp)
    }
}
