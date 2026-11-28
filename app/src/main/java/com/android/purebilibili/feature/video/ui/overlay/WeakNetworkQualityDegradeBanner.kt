package com.android.purebilibili.feature.video.ui.overlay

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.android.purebilibili.core.store.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val WEAK_NETWORK_MIN_EFFECTIVE_STALL_MS = 1_200L
internal const val WEAK_NETWORK_SINGLE_STALL_MS = 7_500L
internal const val WEAK_NETWORK_REBUFFER_WINDOW_MS = 45_000L
internal const val WEAK_NETWORK_REBUFFER_COUNT_THRESHOLD = 3
internal const val WEAK_NETWORK_REBUFFER_MIN_TOTAL_DURATION_MS = 4_500L
internal const val WEAK_NETWORK_STALL_COALESCE_GAP_MS = 1_500L
internal const val WEAK_NETWORK_SEEK_GRACE_MS = 1_800L
internal const val WEAK_NETWORK_RECONFIGURE_GRACE_MS = 1_500L
internal const val WEAK_NETWORK_UNDERRUN_BUFFER_AHEAD_MS = 2_500L
internal const val WEAK_NETWORK_AUTO_DISMISS_RECOVERY_MS = 8_000L

internal data class WeakNetworkDegradeBannerState(
    val message: String,
    val lowerQualityTarget: LowerQualityTarget?,
    val secondaryActionLabel: String? = null,
)

internal data class WeakNetworkPlaybackTransition(
    val trackingActiveBuffering: Boolean,
    val warningMessage: String? = null,
)

private data class EffectiveStallRecord(
    val startMs: Long,
    var endMs: Long,
    var durationMs: Long,
)

internal fun isNetworkUnderrunBuffer(
    bufferedAheadMs: Long?,
    underrunThresholdMs: Long = WEAK_NETWORK_UNDERRUN_BUFFER_AHEAD_MS,
): Boolean = bufferedAheadMs == null || bufferedAheadMs <= underrunThresholdMs

internal fun resolveWeakNetworkSeekSuppression(
    playbackState: Int,
    externallySuppressed: Boolean,
): Boolean = externallySuppressed || playbackState == Player.STATE_BUFFERING

/**
 * 播放期弱网卡顿状态机（专门过滤瞬态微抖动、主动操作与非网络原因导致的缓冲）：
 * 1. 仅统计已稳定起播（`sawReadyInSession`）后、设备在线且处于网络欠载（剩余缓冲 <= 2.5s）的播放期缓冲；
 * 2. 排除拖动/Seek（1.8s 宽限）、切轨/倍速/切流/回前台（1.5s 宽限）产生的过渡缓冲；
 * 3. 单次缓冲持续满 1.2s 才记为有效卡顿，1.5s 内连续抖动归并为同一次体验；
 * 4. 触发条件：45 秒窗口内有效卡顿 >= 3 次且累计时长 >= 4.5s，或单次持续网络欠载 >= 7.5s。
 */
internal class WeakNetworkStallTracker(
    private var minEffectiveStallMs: Long = WEAK_NETWORK_MIN_EFFECTIVE_STALL_MS,
    private var singleStallThresholdMs: Long = WEAK_NETWORK_SINGLE_STALL_MS,
    private var rebufferWindowMs: Long = WEAK_NETWORK_REBUFFER_WINDOW_MS,
    private var rebufferCountThreshold: Int = WEAK_NETWORK_REBUFFER_COUNT_THRESHOLD,
    private var rebufferMinTotalDurationMs: Long = WEAK_NETWORK_REBUFFER_MIN_TOTAL_DURATION_MS,
    private val stallCoalesceGapMs: Long = WEAK_NETWORK_STALL_COALESCE_GAP_MS,
    private val seekGraceMs: Long = WEAK_NETWORK_SEEK_GRACE_MS,
    private val underrunBufferAheadMs: Long = WEAK_NETWORK_UNDERRUN_BUFFER_AHEAD_MS,
) {
    private val effectiveStalls = ArrayDeque<EffectiveStallRecord>()
    private var activeStallStartMs: Long? = null
    private var activeStallCommitted: Boolean = false
    private var activeStallCommittedDurationMs: Long = 0L
    private var graceUntilMs: Long = 0L
    private var suppressCurrentBuffering: Boolean = false

    fun updateProfile(profile: WeakNetworkPromptProfile) {
        minEffectiveStallMs = profile.minEffectiveStallMs
        singleStallThresholdMs = profile.singleStallThresholdMs
        rebufferWindowMs = profile.rebufferWindowMs
        rebufferCountThreshold = profile.rebufferCountThreshold
        rebufferMinTotalDurationMs = profile.rebufferMinTotalDurationMs
    }

    fun markGraceWindow(nowMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        graceUntilMs = maxOf(graceUntilMs, nowMs + durationMs)
        clearActiveStall()
    }

    fun onExternalSuppressionChanged(
        nowMs: Long,
        suppressed: Boolean,
        playbackState: Int,
    ) {
        clearActiveStall()
        if (suppressed) {
            suppressCurrentBuffering = true
            return
        }
        // 拖动进度条/切画质结束时：仅当播放器当下仍在缓冲才抑制至 READY；
        // 若已在 READY（例如缓存内拖动松手），立刻清掉抑制标记并仅保留短暂宽限期。
        graceUntilMs = maxOf(graceUntilMs, nowMs + seekGraceMs)
        suppressCurrentBuffering = playbackState == Player.STATE_BUFFERING
    }

    fun onSeekDiscontinuity(
        nowMs: Long,
        playbackState: Int,
        externallySuppressed: Boolean,
    ) {
        clearActiveStall()
        graceUntilMs = maxOf(graceUntilMs, nowMs + seekGraceMs)
        suppressCurrentBuffering = resolveWeakNetworkSeekSuppression(
            playbackState = playbackState,
            externallySuppressed = externallySuppressed,
        )
    }

    fun onPlaybackState(
        nowMs: Long,
        playbackState: Int,
        playWhenReady: Boolean,
        sawReadyInSession: Boolean,
        externallySuppressed: Boolean,
        bufferedAheadMs: Long?,
        isNetworkAvailable: Boolean = true,
    ): WeakNetworkPlaybackTransition {
        if (externallySuppressed) {
            suppressCurrentBuffering = true
            clearActiveStall()
            return WeakNetworkPlaybackTransition(trackingActiveBuffering = false)
        }

        if (playbackState == Player.STATE_READY) {
            val settledWarning = settleActiveStallOnReady(nowMs)
            suppressCurrentBuffering = false
            return WeakNetworkPlaybackTransition(
                trackingActiveBuffering = false,
                warningMessage = settledWarning,
            )
        }

        if (playbackState != Player.STATE_BUFFERING || !playWhenReady) {
            clearActiveStall()
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                suppressCurrentBuffering = false
            }
            return WeakNetworkPlaybackTransition(trackingActiveBuffering = false)
        }

        // STATE_BUFFERING && playWhenReady
        if (!sawReadyInSession || !isNetworkAvailable) {
            suppressCurrentBuffering = true
            clearActiveStall()
            return WeakNetworkPlaybackTransition(trackingActiveBuffering = false)
        }
        if (suppressCurrentBuffering) {
            return WeakNetworkPlaybackTransition(trackingActiveBuffering = false)
        }
        if (activeStallStartMs == null) {
            if (nowMs < graceUntilMs) {
                suppressCurrentBuffering = true
                return WeakNetworkPlaybackTransition(trackingActiveBuffering = false)
            }
            if (!isNetworkUnderrunBuffer(bufferedAheadMs, underrunBufferAheadMs)) {
                return WeakNetworkPlaybackTransition(trackingActiveBuffering = false)
            }
            activeStallStartMs = nowMs
            activeStallCommitted = false
        }
        return WeakNetworkPlaybackTransition(trackingActiveBuffering = true)
    }

    /**
     * 在缓冲进行中按里程碑（1.2s 有效卡顿门槛 / 7.5s 单次长卡顿门槛）复核一次：
     * 避免非网络原因或已经离开缓冲状态时误弹窗，同时让第 3 次卡顿无需等到恢复即可提示。
     */
    fun evaluateOngoingStall(
        nowMs: Long,
        playbackState: Int,
        playWhenReady: Boolean,
        externallySuppressed: Boolean,
        bufferedAheadMs: Long?,
        isNetworkAvailable: Boolean = true,
    ): String? {
        val startMs = activeStallStartMs ?: return null
        if (externallySuppressed ||
            suppressCurrentBuffering ||
            !isNetworkAvailable ||
            playbackState != Player.STATE_BUFFERING ||
            !playWhenReady ||
            !isNetworkUnderrunBuffer(bufferedAheadMs, underrunBufferAheadMs)
        ) {
            return null
        }
        val elapsedMs = (nowMs - startMs).coerceAtLeast(0L)
        if (elapsedMs >= singleStallThresholdMs) {
            return buildLongStallMessage()
        }
        if (elapsedMs >= minEffectiveStallMs && !activeStallCommitted) {
            activeStallCommitted = true
            activeStallCommittedDurationMs = elapsedMs
            return commitEffectiveStall(startMs = startMs, endMs = nowMs, durationMs = elapsedMs)
        }
        return null
    }

    fun buildLongStallMessage(): String =
        "当前网络较慢，缓冲时间较长，建议降低画质以减少卡顿。"

    fun reset() {
        effectiveStalls.clear()
        clearActiveStall()
        graceUntilMs = 0L
        suppressCurrentBuffering = false
    }

    private fun settleActiveStallOnReady(nowMs: Long): String? {
        val startMs = activeStallStartMs ?: return null
        val alreadyCommitted = activeStallCommitted
        val committedDurationMs = activeStallCommittedDurationMs
        clearActiveStall()
        val durationMs = (nowMs - startMs).coerceAtLeast(0L)
        if (durationMs < minEffectiveStallMs) {
            return null
        }
        if (alreadyCommitted) {
            val last = effectiveStalls.lastOrNull() ?: return null
            val extraDurationMs = (durationMs - committedDurationMs).coerceAtLeast(0L)
            last.endMs = maxOf(last.endMs, nowMs)
            last.durationMs += extraDurationMs
            return resolveFrequentRebufferMessage(nowMs)
        }
        return commitEffectiveStall(startMs = startMs, endMs = nowMs, durationMs = durationMs)
    }

    private fun commitEffectiveStall(startMs: Long, endMs: Long, durationMs: Long): String? {
        pruneExpiredStalls(endMs)
        val previous = effectiveStalls.lastOrNull()
        if (previous != null && startMs - previous.endMs <= stallCoalesceGapMs) {
            // 极短间隔内的连续抖动归并为同一次卡顿体验，累加时长但不重复计数。
            previous.endMs = maxOf(previous.endMs, endMs)
            previous.durationMs += durationMs
        } else {
            effectiveStalls.addLast(
                EffectiveStallRecord(
                    startMs = startMs,
                    endMs = endMs,
                    durationMs = durationMs,
                )
            )
        }
        return resolveFrequentRebufferMessage(endMs)
    }

    private fun resolveFrequentRebufferMessage(nowMs: Long): String? {
        pruneExpiredStalls(nowMs)
        val totalDurationMs = effectiveStalls.sumOf { it.durationMs }
        return if (effectiveStalls.size >= rebufferCountThreshold &&
            totalDurationMs >= rebufferMinTotalDurationMs
        ) {
            "近期频繁缓冲卡顿（${effectiveStalls.size} 次），建议降低画质提升流畅度。"
        } else {
            null
        }
    }

    private fun pruneExpiredStalls(nowMs: Long) {
        while (effectiveStalls.isNotEmpty() && nowMs - effectiveStalls.first().endMs > rebufferWindowMs) {
            effectiveStalls.removeFirst()
        }
    }

    private fun clearActiveStall() {
        activeStallStartMs = null
        activeStallCommitted = false
        activeStallCommittedDurationMs = 0L
    }
}

private fun isDeviceNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun resolveActiveWeakNetworkBucket(context: Context): WeakNetworkBucket {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return WeakNetworkBucket.WIFI
    val network = cm.activeNetwork ?: return WeakNetworkBucket.WIFI
    val capabilities = cm.getNetworkCapabilities(network) ?: return WeakNetworkBucket.WIFI
    return if (
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    ) {
        WeakNetworkBucket.WIFI
    } else {
        WeakNetworkBucket.CELLULAR
    }
}

private fun readPlayerBufferedAheadMs(player: Player): Long? {
    val bufferedPos = player.bufferedPosition
    val currentPos = player.currentPosition
    if (player.isCurrentMediaItemLive && bufferedPos <= 0L) {
        return null
    }
    if (bufferedPos < 0L || currentPos < 0L) {
        return null
    }
    return (bufferedPos - currentPos).coerceAtLeast(0L)
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
    val promptEnabled by remember(context) {
        SettingsManager.getWeakNetworkDegradePromptEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    if (!promptEnabled) {
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val adaptiveLearningState by remember(context) {
        SettingsManager.getWeakNetworkAdaptiveLearningState(context)
    }.collectAsStateWithLifecycle(initialValue = WeakNetworkAdaptiveState())
    var activeNetworkBucket by remember(context) {
        mutableStateOf(resolveActiveWeakNetworkBucket(context))
    }
    val learningState = adaptiveLearningState.stateFor(activeNetworkBucket)
    val promptProfile = remember(learningState.strictnessScore) {
        resolveWeakNetworkPromptProfile(learningState)
    }
    val latestAdaptiveLearningState by rememberUpdatedState(adaptiveLearningState)
    val latestPromptProfile by rememberUpdatedState(promptProfile)

    val tracker = remember(mediaKey, currentQuality) {
        WeakNetworkStallTracker(
            minEffectiveStallMs = promptProfile.minEffectiveStallMs,
            singleStallThresholdMs = promptProfile.singleStallThresholdMs,
            rebufferWindowMs = promptProfile.rebufferWindowMs,
            rebufferCountThreshold = promptProfile.rebufferCountThreshold,
            rebufferMinTotalDurationMs = promptProfile.rebufferMinTotalDurationMs,
        )
    }
    LaunchedEffect(tracker, promptProfile) {
        tracker.updateProfile(promptProfile)
    }

    var dismissed by remember(mediaKey, currentQuality) { mutableStateOf(false) }
    var warningMessage by remember(mediaKey, currentQuality) { mutableStateOf("") }
    var bannerNetworkBucket by remember(mediaKey, currentQuality) { mutableStateOf(activeNetworkBucket) }
    var activeBuffering by remember(mediaKey, currentQuality) { mutableStateOf(false) }
    var playingReady by remember(mediaKey, currentQuality) {
        mutableStateOf(player.playbackState == Player.STATE_READY && player.playWhenReady)
    }
    var bufferingSessionId by remember(mediaKey, currentQuality) { mutableIntStateOf(0) }
    val latestIsBufferingSuppressed by rememberUpdatedState(isBufferingSuppressed)

    // 跨画质切换保留同一视频的最近卡顿时间戳，识别“用户不等提示就主动降画质”的隐式正向反馈。
    var lastObservedMediaKey by remember { mutableStateOf(mediaKey) }
    var lastObservedQuality by remember { mutableIntStateOf(currentQuality) }
    var lastStallActivityRealtimeMs by remember(mediaKey) { mutableLongStateOf(0L) }
    var lastStallNetworkBucket by remember(mediaKey) { mutableStateOf(activeNetworkBucket) }
    var bannerTriggeredQualityChange by remember(mediaKey) { mutableStateOf(false) }

    LaunchedEffect(mediaKey, currentQuality) {
        val nowRealtimeMs = SystemClock.elapsedRealtime()
        if (mediaKey == lastObservedMediaKey &&
            currentQuality > 0 &&
            lastObservedQuality > 0 &&
            currentQuality < lastObservedQuality
        ) {
            val wasBannerAction = bannerTriggeredQualityChange
            bannerTriggeredQualityChange = false
            val recentlyStalled = lastStallActivityRealtimeMs > 0L &&
                nowRealtimeMs - lastStallActivityRealtimeMs <= WEAK_NETWORK_MANUAL_DOWNGRADE_WINDOW_MS
            if (!wasBannerAction && recentlyStalled) {
                SettingsManager.recordWeakNetworkPromptOutcome(
                    context = context,
                    bucket = lastStallNetworkBucket,
                    outcome = WeakNetworkPromptOutcome.MANUAL_DOWNGRADE_DURING_STALL,
                )
            }
        } else {
            bannerTriggeredQualityChange = false
        }
        lastObservedMediaKey = mediaKey
        lastObservedQuality = currentQuality
    }

    // 外部抑制（拖动进度条、切换画质）状态变化时同步更新抑制与松手宽限，避免缓存内拖动松手遗留脏抑制。
    var wasExternallySuppressed by remember(mediaKey, currentQuality) { mutableStateOf(isBufferingSuppressed) }
    LaunchedEffect(isBufferingSuppressed, tracker) {
        if (isBufferingSuppressed != wasExternallySuppressed) {
            tracker.onExternalSuppressionChanged(
                nowMs = SystemClock.elapsedRealtime(),
                suppressed = isBufferingSuppressed,
                playbackState = player.playbackState,
            )
            if (isBufferingSuppressed) {
                activeBuffering = false
            }
            wasExternallySuppressed = isBufferingSuppressed
        }
    }

    DisposableEffect(player, lifecycleOwner, mediaKey, currentQuality) {
        var sawReadyInSession = player.playbackState == Player.STATE_READY

        fun syncState() {
            val nowMs = SystemClock.elapsedRealtime()
            val state = player.playbackState
            val playWhenReady = player.playWhenReady
            val bucket = resolveActiveWeakNetworkBucket(context)
            activeNetworkBucket = bucket
            if (state == Player.STATE_READY) {
                sawReadyInSession = true
            }
            playingReady = state == Player.STATE_READY && playWhenReady
            val transition = tracker.onPlaybackState(
                nowMs = nowMs,
                playbackState = state,
                playWhenReady = playWhenReady,
                sawReadyInSession = sawReadyInSession,
                externallySuppressed = latestIsBufferingSuppressed,
                bufferedAheadMs = readPlayerBufferedAheadMs(player),
                isNetworkAvailable = isDeviceNetworkAvailable(context),
            )
            if (transition.trackingActiveBuffering) {
                lastStallActivityRealtimeMs = nowMs
                lastStallNetworkBucket = bucket
                if (!activeBuffering) {
                    bufferingSessionId += 1
                }
            }
            activeBuffering = transition.trackingActiveBuffering
            val bucketLearningState = latestAdaptiveLearningState.stateFor(bucket)
            if (transition.warningMessage != null &&
                warningMessage.isBlank() &&
                !isWeakNetworkPromptInCooldown(bucketLearningState, System.currentTimeMillis())
            ) {
                bannerNetworkBucket = bucket
                warningMessage = transition.warningMessage
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = syncState()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncState()
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    tracker.onSeekDiscontinuity(
                        nowMs = SystemClock.elapsedRealtime(),
                        playbackState = player.playbackState,
                        externallySuppressed = latestIsBufferingSuppressed,
                    )
                    activeBuffering = false
                }
            }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                tracker.markGraceWindow(SystemClock.elapsedRealtime(), WEAK_NETWORK_RECONFIGURE_GRACE_MS)
                activeBuffering = false
            }
            override fun onTracksChanged(tracks: Tracks) {
                tracker.markGraceWindow(SystemClock.elapsedRealtime(), WEAK_NETWORK_RECONFIGURE_GRACE_MS)
                activeBuffering = false
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sawReadyInSession = player.playbackState == Player.STATE_READY
                tracker.reset()
                activeBuffering = false
            }
            override fun onRenderedFirstFrame() {
                sawReadyInSession = true
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 切回前台重绑 Surface 或恢复解码可能产生瞬态缓冲，给予短宽限避免误报弱网。
                tracker.markGraceWindow(SystemClock.elapsedRealtime(), WEAK_NETWORK_RECONFIGURE_GRACE_MS)
                activeBuffering = false
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        syncState()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            activeBuffering = false
        }
    }

    LaunchedEffect(mediaKey, currentQuality, activeBuffering, bufferingSessionId, dismissed, isBufferingSuppressed) {
        if (!activeBuffering || dismissed || isBufferingSuppressed || warningMessage.isNotBlank()) {
            return@LaunchedEffect
        }
        val currentProfile = latestPromptProfile
        delay(currentProfile.minEffectiveStallMs)
        val nowMs = SystemClock.elapsedRealtime()
        val frequentWarning = tracker.evaluateOngoingStall(
            nowMs = nowMs,
            playbackState = player.playbackState,
            playWhenReady = player.playWhenReady,
            externallySuppressed = latestIsBufferingSuppressed,
            bufferedAheadMs = readPlayerBufferedAheadMs(player),
            isNetworkAvailable = isDeviceNetworkAvailable(context),
        )
        if (frequentWarning != null) {
            val bucket = resolveActiveWeakNetworkBucket(context)
            activeNetworkBucket = bucket
            val bucketLearningState = latestAdaptiveLearningState.stateFor(bucket)
            if (!isWeakNetworkPromptInCooldown(bucketLearningState, System.currentTimeMillis())) {
                bannerNetworkBucket = bucket
                warningMessage = frequentWarning
            }
            return@LaunchedEffect
        }
        val remainingLongStallMs = (currentProfile.singleStallThresholdMs - currentProfile.minEffectiveStallMs)
            .coerceAtLeast(0L)
        delay(remainingLongStallMs)
        val longStallWarning = tracker.evaluateOngoingStall(
            nowMs = SystemClock.elapsedRealtime(),
            playbackState = player.playbackState,
            playWhenReady = player.playWhenReady,
            externallySuppressed = latestIsBufferingSuppressed,
            bufferedAheadMs = readPlayerBufferedAheadMs(player),
            isNetworkAvailable = isDeviceNetworkAvailable(context),
        )
        if (longStallWarning != null) {
            val bucket = resolveActiveWeakNetworkBucket(context)
            activeNetworkBucket = bucket
            val bucketLearningState = latestAdaptiveLearningState.stateFor(bucket)
            if (!isWeakNetworkPromptInCooldown(bucketLearningState, System.currentTimeMillis())) {
                bannerNetworkBucket = bucket
                warningMessage = longStallWarning
            }
        }
    }

    // 网络恢复并连续流畅播放 8 秒后自动收起提示并清空过期卡顿记录，同时记为“未采纳”以温和降低打扰。
    LaunchedEffect(mediaKey, currentQuality, warningMessage, playingReady, dismissed) {
        if (warningMessage.isBlank() || dismissed || !playingReady) return@LaunchedEffect
        delay(WEAK_NETWORK_AUTO_DISMISS_RECOVERY_MS)
        if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
            warningMessage = ""
            tracker.reset()
            SettingsManager.recordWeakNetworkPromptOutcome(
                context = context,
                bucket = bannerNetworkBucket,
                outcome = WeakNetworkPromptOutcome.AUTO_DISMISSED_IGNORED,
            )
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
                bannerTriggeredQualityChange = true
                tracker.reset()
                coroutineScope.launch {
                    SettingsManager.recordWeakNetworkPromptOutcome(
                        context = context,
                        bucket = bannerNetworkBucket,
                        outcome = WeakNetworkPromptOutcome.ACCEPTED,
                    )
                }
                Toast.makeText(context, "已切换至 ${lowerQuality.label}", Toast.LENGTH_SHORT).show()
                onSelectLowerQuality(lowerQuality)
            }
        } else if (onSecondaryAction != null) {
            {
                dismissed = true
                warningMessage = ""
                tracker.reset()
                coroutineScope.launch {
                    SettingsManager.recordWeakNetworkPromptOutcome(
                        context = context,
                        bucket = bannerNetworkBucket,
                        outcome = WeakNetworkPromptOutcome.ACCEPTED,
                    )
                }
                onSecondaryAction()
            }
        } else null
        val secondaryLabel = if (lowerQuality != null) activeState.secondaryActionLabel else null
        val secondaryAction: (() -> Unit)? = if (lowerQuality != null && onSecondaryAction != null) {
            {
                dismissed = true
                warningMessage = ""
                tracker.reset()
                coroutineScope.launch {
                    SettingsManager.recordWeakNetworkPromptOutcome(
                        context = context,
                        bucket = bannerNetworkBucket,
                        outcome = WeakNetworkPromptOutcome.ACCEPTED,
                    )
                }
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
                coroutineScope.launch {
                    SettingsManager.recordWeakNetworkPromptOutcome(
                        context = context,
                        bucket = bannerNetworkBucket,
                        outcome = WeakNetworkPromptOutcome.DISMISSED,
                    )
                }
            },
            useGlass = useGlass,
        )
    }
}
