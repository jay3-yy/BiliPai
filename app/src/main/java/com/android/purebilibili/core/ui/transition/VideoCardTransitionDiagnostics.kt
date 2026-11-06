package com.android.purebilibili.core.ui.transition

import android.os.Trace
import android.os.Build
import android.util.Log
import com.android.purebilibili.core.util.Logger
import java.util.concurrent.atomic.AtomicLong

/** Perfetto counters are cumulative, so a SettledHidden interval must have a zero delta. */
internal object VideoCardTransitionDiagnostics {
    /** 仅记录实际发生的同步工作；没有帧循环，耗时不包含异步 GPU 完成时间。 */
    fun <T> measureWork(stage: String, detail: () -> String = { "" }, block: () -> T): T {
        val logging = Logger.areVerboseRuntimeLogsEnabled()
        val tracing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()
        if (!logging && !tracing) return block()
        val startedNs = System.nanoTime()
        if (tracing) Trace.beginSection("video.transition.$stage")
        try {
            return block()
        } finally {
            if (tracing) Trace.endSection()
            if (logging) {
                val elapsedUs = (System.nanoTime() - startedNs) / 1_000L
                Logger.d("VideoReturnWork", "monotonic_ns=$startedNs stage=$stage elapsed_us=$elapsedUs " +
                    "thread=${Thread.currentThread().name} ${detail()}")
            }
        }
    }

    private val snapshotRecords = AtomicLong()
    private val blurEffectUpdates = AtomicLong()
    private val sourceLayerDraws = AtomicLong()
    private val navBackdropDraws = AtomicLong()
    private var lastState: VideoCardTransitionSettleState? = null
    private var ownership = "UNKNOWN"
    private var liveSurfaceEnabled: Boolean? = null
    private const val TAG = "VideoCardMotion"

    /** 只在点击解析失败时记录一次，区分未测量与来源身份失配；不进入逐帧路径。 */
    fun onSourceGeometryRejected(sourceKey: String?, recordedSourceKey: String?, hasRecordedBounds: Boolean) {
        if (!Log.isLoggable(TAG, Log.DEBUG) && !Logger.areVerboseRuntimeLogsEnabled()) return
        val reason = when {
            !hasRecordedBounds -> "missing_bounds"
            sourceKey != recordedSourceKey -> "source_identity_mismatch"
            else -> "unusable_bounds"
        }
        val message = "phase=SOURCE_FALLBACK reason=$reason source_key=$sourceKey recorded_key=$recordedSourceKey"
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message)
        Logger.d(TAG, message)
    }

    fun onOwnershipChanged(value: VideoCardReturnCoverOwnership, liveSurface: Boolean) {
        ownership = value.name
        liveSurfaceEnabled = liveSurface
    }

    /** Boundary records carry CLOCK_MONOTONIC timestamps, matching gfxinfo's IntendedVsync. */
    fun onMotionPhase(
        state: VideoCardTransitionSettleState,
        spec: VideoHeroMotionSpec,
        sourceLayout: VideoCardSourceLayout,
        configuration: String,
    ) {
        val phase = when (state) {
            VideoCardTransitionSettleState.AutoEnter -> "OPENING"
            VideoCardTransitionSettleState.AutoReturn -> if (
                lastState == VideoCardTransitionSettleState.InteractiveSeek
            ) "PredictiveCommit" else "RETURNING"
            VideoCardTransitionSettleState.CancelRestore -> "PredictiveCancel"
            else -> state.name.uppercase()
        }
        lastState = state
        setCounter("bili.video_card.phase", state.ordinal.toLong())
        if (!Log.isLoggable(TAG, Log.DEBUG) && !Logger.areVerboseRuntimeLogsEnabled()) return
        val duration = if (state == VideoCardTransitionSettleState.AutoEnter) {
            spec.enterDurationMillis
        } else if (state == VideoCardTransitionSettleState.CancelRestore) {
            spec.cancelDurationMillis
        } else spec.returnDurationMillis
        val message = "monotonic_ns=${System.nanoTime()} phase=$phase " +
            "curve_id=hero_v1 resolved_duration=$duration source_layout=${sourceLayout.name} " +
            "ownership=$ownership live_surface=$liveSurfaceEnabled $configuration " +
            "blur_effect_updates=${blurEffectUpdates.get()} " +
            "snapshot_records=${snapshotRecords.get()} nav_backdrop_draws=${navBackdropDraws.get()}"
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message)
        Logger.d(TAG, message)
    }

    fun onSnapshotRecorded() = increment("bili.video_card.snapshot_records", snapshotRecords)

    fun onBlurEffectUpdated() = increment("bili.video_card.blur_updates", blurEffectUpdates)

    fun onSourceLayerDrawn() = increment("bili.video_card.source_layer_draws", sourceLayerDraws)

    fun onNavBackdropDrawn() = increment("bili.video_card.nav_backdrop_draws", navBackdropDraws)

    fun onExposureChanged(exposure: VideoCardTransitionExposure) {
        setCounter("bili.video_card.exposure", exposure.ordinal.toLong())
    }

    fun onDepthAnimationJobChanged(active: Boolean) {
        setCounter("bili.video_card.depth_jobs", if (active) 1L else 0L)
    }

    fun onNavigationBackJobChanged(active: Boolean) {
        setCounter("bili.video_card.back_jobs", if (active) 1L else 0L)
    }

    private fun increment(name: String, counter: AtomicLong) {
        val value = counter.incrementAndGet()
        setCounter(name, value)
    }

    private fun setCounter(name: String, value: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.setCounter(name, value)
        }
    }
}
