package com.android.purebilibili.feature.video.playback.policy

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.android.purebilibili.core.util.FormatUtils
import kotlinx.coroutines.delay

private const val MIN_AB_LOOP_SPAN_MS = 500L

data class VideoAbLoopState(
    val pointAMs: Long? = null,
    val pointBMs: Long? = null,
) {
    val isActive: Boolean
        get() = pointAMs != null && pointBMs != null && pointBMs - pointAMs >= MIN_AB_LOOP_SPAN_MS

    val hasAnyPoint: Boolean
        get() = pointAMs != null || pointBMs != null

    fun shouldLoopBack(currentPositionMs: Long): Boolean {
        val end = pointBMs ?: return false
        return isActive && currentPositionMs >= end
    }
}

class PlayerAbLoopController(
    val state: VideoAbLoopState,
    val setPointA: () -> Unit,
    val setPointB: () -> Unit,
    val clear: () -> Unit,
)

@Composable
fun rememberPlayerAbLoopController(
    player: Player?,
    identityKey: Any?,
    isScrubbingProvider: () -> Boolean = { false },
    fallbackIsPlaying: Boolean = false,
    fallbackPositionProvider: () -> Long = { 0L },
    fallbackDurationProvider: () -> Long = { 0L },
    onSeekTo: ((Long) -> Unit)? = null,
    onSeekedToA: (Long) -> Unit = {},
): PlayerAbLoopController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestIsScrubbing by rememberUpdatedState(isScrubbingProvider)
    val latestFallbackPos by rememberUpdatedState(fallbackPositionProvider)
    val latestFallbackDur by rememberUpdatedState(fallbackDurationProvider)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    val latestOnSeekedToA by rememberUpdatedState(onSeekedToA)

    var abLoopState by remember(identityKey) { mutableStateOf(VideoAbLoopState()) }
    var playerIsPlaying by remember(player, identityKey) { mutableStateOf(player?.isPlaying == true) }
    val effectiveIsPlaying = if (player != null) playerIsPlaying else fallbackIsPlaying

    DisposableEffect(player, abLoopState, identityKey) {
        if (player == null) return@DisposableEffect onDispose {}
        playerIsPlaying = player.isPlaying
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                playerIsPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val pointA = abLoopState.pointAMs
                if (playbackState == Player.STATE_ENDED &&
                    abLoopState.isActive &&
                    pointA != null &&
                    !latestIsScrubbing()
                ) {
                    latestOnSeekTo?.invoke(pointA) ?: player.seekTo(pointA)
                    player.play()
                    latestOnSeekedToA(pointA)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, abLoopState, effectiveIsPlaying, lifecycleOwner, identityKey) {
        if (!abLoopState.isActive || !effectiveIsPlaying) return@LaunchedEffect
        val pointA = abLoopState.pointAMs ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val currentPos = player?.currentPosition ?: latestFallbackPos()
                val playingNow = player?.isPlaying ?: effectiveIsPlaying
                if (!latestIsScrubbing() &&
                    playingNow &&
                    abLoopState.shouldLoopBack(currentPos)
                ) {
                    latestOnSeekTo?.invoke(pointA) ?: player?.seekTo(pointA)
                    latestOnSeekedToA(pointA)
                }
                delay(150L)
            }
        }
    }

    val readPosition: () -> Long = {
        (player?.currentPosition ?: latestFallbackPos()).coerceAtLeast(0L)
    }
    val readDuration: () -> Long = {
        (player?.duration?.takeIf { it > 0L } ?: latestFallbackDur()).coerceAtLeast(0L)
    }

    return PlayerAbLoopController(
        state = abLoopState,
        setPointA = {
            val pos = readPosition()
            val next = setAbLoopPointA(abLoopState, pos, readDuration())
            abLoopState = next
            Toast.makeText(
                context,
                "已设 A 点 ${formatAbLoopTime(next.pointAMs ?: pos)}",
                Toast.LENGTH_SHORT,
            ).show()
        },
        setPointB = {
            val pos = readPosition()
            val next = setAbLoopPointB(abLoopState, pos, readDuration())
            if (next == abLoopState) {
                Toast.makeText(context, "A-B 区间需大于 0.5 秒", Toast.LENGTH_SHORT).show()
            } else {
                abLoopState = next
                val start = next.pointAMs ?: 0L
                latestOnSeekTo?.invoke(start) ?: player?.seekTo(start)
                latestOnSeekedToA(start)
                Toast.makeText(
                    context,
                    "A-B 循环已开启 ${formatAbLoopSummary(next)}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        clear = {
            abLoopState = VideoAbLoopState()
            Toast.makeText(context, "已关闭 A-B 循环", Toast.LENGTH_SHORT).show()
        },
    )
}

fun setAbLoopPointA(
    state: VideoAbLoopState,
    positionMs: Long,
    durationMs: Long,
): VideoAbLoopState {
    val clamped = clampAbLoopPosition(positionMs, durationMs)
    val nextB = state.pointBMs?.takeIf { it - clamped >= MIN_AB_LOOP_SPAN_MS }
    return VideoAbLoopState(pointAMs = clamped, pointBMs = nextB)
}

fun setAbLoopPointB(
    state: VideoAbLoopState,
    positionMs: Long,
    durationMs: Long,
): VideoAbLoopState {
    val clamped = clampAbLoopPosition(positionMs, durationMs)
    val start = state.pointAMs ?: 0L
    if (clamped - start >= MIN_AB_LOOP_SPAN_MS) {
        return VideoAbLoopState(pointAMs = start, pointBMs = clamped)
    }
    if (start - clamped >= MIN_AB_LOOP_SPAN_MS) {
        return VideoAbLoopState(pointAMs = clamped, pointBMs = start)
    }
    return state
}

fun formatAbLoopSummary(state: VideoAbLoopState): String {
    val a = state.pointAMs
    val b = state.pointBMs
    return when {
        a != null && b != null -> "${formatAbLoopTime(a)} - ${formatAbLoopTime(b)}"
        a != null -> "已设 A ${formatAbLoopTime(a)}"
        b != null -> "已设 B ${formatAbLoopTime(b)}"
        else -> "未开启"
    }
}

internal fun formatAbLoopTime(positionMs: Long): String =
    FormatUtils.formatDuration((positionMs.coerceAtLeast(0L) / 1000L).toInt())

internal fun resolveAbLoopProgressFractions(
    pointAMs: Long?,
    pointBMs: Long?,
    durationMs: Long,
): Pair<Float?, Float?> {
    if (durationMs <= 0L) return null to null
    val startFraction = pointAMs?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
    val endFraction = pointBMs?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
    return startFraction to endFraction
}

private fun clampAbLoopPosition(positionMs: Long, durationMs: Long): Long {
    val safePos = positionMs.coerceAtLeast(0L)
    return if (durationMs > 0L) safePos.coerceAtMost(durationMs) else safePos
}
