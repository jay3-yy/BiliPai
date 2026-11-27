package com.android.purebilibili.feature.video.ui.overlay

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import kotlinx.coroutines.delay

internal const val WEAK_NETWORK_SINGLE_STALL_MS = 5_000L
internal const val WEAK_NETWORK_REBUFFER_WINDOW_MS = 30_000L
internal const val WEAK_NETWORK_REBUFFER_COUNT_THRESHOLD = 3

internal data class WeakNetworkDegradeBannerState(
    val message: String,
    val lowerQualityTarget: LowerQualityTarget?,
    val secondaryActionLabel: String? = null,
)

/**
 * 统计播放期间的二次卡顿缓冲（排除首帧前起播、拖动进度与切换画质期间）。
 * 30 秒窗口内达到 3 次二次缓冲，或单次缓冲超时 5 秒时返回提示文案。
 */
internal class WeakNetworkStallTracker(
    private val rebufferWindowMs: Long = WEAK_NETWORK_REBUFFER_WINDOW_MS,
    private val rebufferCountThreshold: Int = WEAK_NETWORK_REBUFFER_COUNT_THRESHOLD,
) {
    private val rebufferTimestampsMs = ArrayDeque<Long>()

    fun recordRebuffer(nowMs: Long): String? {
        while (rebufferTimestampsMs.isNotEmpty() && nowMs - rebufferTimestampsMs.first() > rebufferWindowMs) {
            rebufferTimestampsMs.removeFirst()
        }
        rebufferTimestampsMs.addLast(nowMs)
        return if (rebufferTimestampsMs.size >= rebufferCountThreshold) {
            "近期频繁缓冲卡顿（${rebufferTimestampsMs.size} 次），建议降低画质提升流畅度。"
        } else {
            null
        }
    }

    fun buildLongStallMessage(): String =
        "当前网络较慢，缓冲时间较长，建议降低画质以减少卡顿。"

    fun reset() {
        rebufferTimestampsMs.clear()
    }
}

internal fun resolveWeakNetworkDegradeBannerState(
    warningMessage: String,
    currentQuality: Int,
    qualityIds: List<Int>,
    qualityLabels: List<String>,
    switchableQualityIds: List<Int>,
    secondaryActionLabel: String?,
    isScreenLocked: Boolean,
    suppressTransientOverlays: Boolean,
    suppressedByHigherPriorityBanner: Boolean,
    dismissed: Boolean,
): WeakNetworkDegradeBannerState? {
    if (dismissed || isScreenLocked || suppressTransientOverlays || suppressedByHigherPriorityBanner) {
        return null
    }
    if (warningMessage.isBlank()) return null
    val lowerQuality = resolveLowerQualityTarget(
        currentQuality = currentQuality,
        qualityIds = qualityIds,
        qualityLabels = qualityLabels,
        switchableQualityIds = switchableQualityIds,
    )
    if (lowerQuality == null && secondaryActionLabel.isNullOrBlank()) return null
    return WeakNetworkDegradeBannerState(
        message = warningMessage,
        lowerQualityTarget = lowerQuality,
        secondaryActionLabel = secondaryActionLabel?.takeIf { it.isNotBlank() },
    )
}

@Composable
internal fun WeakNetworkDegradeOverlayHost(
    player: Player,
    mediaKey: String,
    currentQuality: Int,
    qualityIds: List<Int>,
    qualityLabels: List<String>,
    switchableQualityIds: List<Int> = emptyList(),
    isScreenLocked: Boolean = false,
    suppressTransientOverlays: Boolean = false,
    suppressedByHigherPriorityBanner: Boolean = false,
    isBufferingSuppressed: Boolean = false,
    onSelectLowerQuality: (LowerQualityTarget) -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    useGlass: Boolean = LocalPlayerControlGlassEnabled.current,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tracker = remember(mediaKey, currentQuality) { WeakNetworkStallTracker() }
    var dismissed by remember(mediaKey, currentQuality) { mutableStateOf(false) }
    var warningMessage by remember(mediaKey, currentQuality) { mutableStateOf("") }
    var activeBuffering by remember(mediaKey, currentQuality) { mutableStateOf(false) }
    var bufferingSessionId by remember(mediaKey, currentQuality) { mutableIntStateOf(0) }

    DisposableEffect(player, mediaKey, currentQuality, isBufferingSuppressed) {
        var sawReadyInSession = player.playbackState == Player.STATE_READY
        var wasBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        activeBuffering = wasBuffering && !isBufferingSuppressed

        fun syncState() {
            val state = player.playbackState
            val playWhenReady = player.playWhenReady
            if (state == Player.STATE_READY) {
                sawReadyInSession = true
            }
            val nowBuffering = state == Player.STATE_BUFFERING && playWhenReady && !isBufferingSuppressed
            if (nowBuffering && !wasBuffering) {
                bufferingSessionId += 1
                if (sawReadyInSession) {
                    tracker.recordRebuffer(SystemClock.elapsedRealtime())?.let { message ->
                        warningMessage = message
                    }
                }
            }
            wasBuffering = nowBuffering
            activeBuffering = nowBuffering
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = syncState()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncState()
            override fun onRenderedFirstFrame() {
                sawReadyInSession = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            activeBuffering = false
        }
    }

    LaunchedEffect(mediaKey, currentQuality, activeBuffering, bufferingSessionId, dismissed, isBufferingSuppressed) {
        if (!activeBuffering || dismissed || isBufferingSuppressed || warningMessage.isNotBlank()) {
            return@LaunchedEffect
        }
        delay(WEAK_NETWORK_SINGLE_STALL_MS)
        if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady && !isBufferingSuppressed) {
            warningMessage = tracker.buildLongStallMessage()
        }
    }

    val bannerState = resolveWeakNetworkDegradeBannerState(
        warningMessage = warningMessage,
        currentQuality = currentQuality,
        qualityIds = qualityIds,
        qualityLabels = qualityLabels,
        switchableQualityIds = switchableQualityIds,
        secondaryActionLabel = if (onSecondaryAction != null) secondaryActionLabel else null,
        isScreenLocked = isScreenLocked,
        suppressTransientOverlays = suppressTransientOverlays,
        suppressedByHigherPriorityBanner = suppressedByHigherPriorityBanner,
        dismissed = dismissed,
    )

    AnimatedVisibility(
        visible = bannerState != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val activeState = bannerState ?: return@AnimatedVisibility
        val lowerQuality = activeState.lowerQualityTarget
        val primaryLabel = lowerQuality?.let { "降至 ${it.label}" } ?: activeState.secondaryActionLabel
        val primaryAction: (() -> Unit)? = if (lowerQuality != null) {
            {
                dismissed = true
                warningMessage = ""
                tracker.reset()
                Toast.makeText(context, "已切换至 ${lowerQuality.label}", Toast.LENGTH_SHORT).show()
                onSelectLowerQuality(lowerQuality)
            }
        } else if (onSecondaryAction != null) {
            {
                dismissed = true
                warningMessage = ""
                tracker.reset()
                onSecondaryAction()
            }
        } else null
        val secondaryLabel = if (lowerQuality != null) activeState.secondaryActionLabel else null
        val secondaryAction: (() -> Unit)? = if (lowerQuality != null && onSecondaryAction != null) {
            {
                dismissed = true
                warningMessage = ""
                tracker.reset()
                onSecondaryAction()
            }
        } else null

        PlayerDegradePromptBanner(
            message = activeState.message,
            dismissContentDescription = "关闭弱网降级提示",
            primaryActionLabel = primaryLabel,
            onPrimaryAction = primaryAction,
            secondaryActionLabel = secondaryLabel,
            onSecondaryAction = secondaryAction,
            onDismiss = {
                dismissed = true
                warningMessage = ""
            },
            useGlass = useGlass,
        )
    }
}
