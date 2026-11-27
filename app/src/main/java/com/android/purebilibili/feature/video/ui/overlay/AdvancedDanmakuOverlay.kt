package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.android.purebilibili.core.ui.adaptive.DeviceThermalBudgetState
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.danmaku.parser.AdvancedDanmakuData
import com.android.purebilibili.danmaku.parser.TimedDanmakuWindow
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil

/** Mode 7: only membership/count changes compose; movement reads media time in the layer. */
@Composable
fun AdvancedDanmakuOverlay(
    danmakuList: List<AdvancedDanmakuData>,
    player: Player,
    viewport: DanmakuViewport,
    opacity: Float = 1f,
    fontScale: Float = 1f,
    fontWeight: Int = 5,
    modifier: Modifier = Modifier,
    renderingPaused: Boolean = false,
) {
    val position = remember(player) { mutableLongStateOf(player.currentPosition) }
    val window = remember(danmakuList) {
        TimedDanmakuWindow(danmakuList,
            startTimeMs = { it.startTimeMs - 100L },
            endTimeMs = { it.startTimeMs + it.durationMs + 100L },
        )
    }
    val active = remember(window) { mutableStateOf(emptyList<AdvancedDanmakuData>()) }
    val wake = remember(player) { Channel<Unit>(Channel.CONFLATED) }
    DisposableEffect(player, wake) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                wake.trySend(Unit)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, window, renderingPaused) {
        if (renderingPaused) return@LaunchedEffect
        var lastRenderedFrameNs = 0L
        while (isActive) {
            val now = player.currentPosition
            position.longValue = now
            val visible = window.update(now)
            active.value = visible
            if (!player.isPlaying) {
                lastRenderedFrameNs = 0L
                wake.receive()
            } else if (visible.isNotEmpty()) {
                val minIntervalNs = resolveSpecialDanmakuMinFrameIntervalNs(
                    thermalTier = DeviceThermalBudgetState.tier,
                    activeCount = visible.size,
                )
                lastRenderedFrameNs = awaitNextSpecialDanmakuFrame(lastRenderedFrameNs, minIntervalNs)
            } else {
                lastRenderedFrameNs = 0L
                val next = window.nextStartTimeMs
                if (next == null) wake.receive()
                else {
                    val speed = player.playbackParameters.speed.coerceAtLeast(0.01f)
                    val waitMs = ceil((next - now) / speed.toDouble()).toLong().coerceAtLeast(1L)
                    withTimeoutOrNull(waitMs) { wake.receive() }
                }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        active.value.forEach { danmaku ->
            key(danmaku.id) {
                RenderSingleAdvancedDanmaku(danmaku, position, viewport, opacity, fontScale, fontWeight)
            }
        }
    }
}

@Composable
private fun RenderSingleAdvancedDanmaku(
    danmaku: AdvancedDanmakuData,
    position: State<Long>,
    viewport: DanmakuViewport,
    opacity: Float,
    fontScale: Float,
    fontWeight: Int,
) {
    // Recompose growing counters only when the displayed integer changes.
    val displayText by remember(danmaku, position) {
        derivedStateOf {
            if (danmaku.maxCount <= 1) danmaku.content
            else {
                val progress = ((position.value - danmaku.startTimeMs).toFloat() /
                    danmaku.accumulationDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                val count = (1 + (danmaku.maxCount - 1) * progress).toInt()
                "${danmaku.content} ×$count"
            }
        }
    }
    Box(Modifier.graphicsLayer {
        val now = position.value
        val progress = danmaku.easing.transform(danmaku.getTranslationProgress(now))
        if (danmaku.path.isNotEmpty()) {
            val point = danmaku.getPathPointAt(progress)
            translationX = point.x * viewport.widthPx
            translationY = point.y * viewport.heightPx
        } else {
            translationX = (danmaku.startX + (danmaku.endX - danmaku.startX) * progress) * viewport.widthPx
            translationY = (danmaku.startY + (danmaku.endY - danmaku.startY) * progress) * viewport.heightPx
        }
        alpha = (danmaku.getAlphaAt(now) * opacity.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        rotationZ = danmaku.rotateZ
        val accumulating = danmaku.maxCount > 1 && now < danmaku.startTimeMs + danmaku.accumulationDurationMs
        val phase = (now % 300L) / 300f
        val pulse = if (!accumulating) 1f else if (phase < 0.5f) 1f + 0.6f * phase else 1.6f - 0.6f * phase
        scaleX = pulse
        scaleY = pulse
    }) {
        AppText(
            text = displayText,
            color = Color(danmaku.color or 0xFF000000.toInt()),
            fontSize = (danmaku.fontSize * fontScale.coerceIn(0.3f, 2f)).sp,
            fontWeight = FontWeight(fontWeight.coerceIn(1, 9) * 100),
        )
    }
}
