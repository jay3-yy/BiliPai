package com.android.purebilibili.danmaku.engine

import android.graphics.PointF
import android.graphics.RectF
import java.util.function.LongSupplier

interface DanmakuEngine : AutoCloseable {
    val playbackState: DanmakuPlaybackState
    val diagnostics: DanmakuEngineDiagnostics

    /**
     * UP 主身份标识（owner.mid 的 crc32 hex）。命中该标识的弹幕渲染时附带 UP 徽章。
     * 置空（null）清除标识。
     */
    var upOwnerUserHash: String?

    fun updateConfig(config: DanmakuRenderConfig)

    /**
     * Hides ordinary danmaku matching a selected ID or exact original text.
     * Active items keep moving; clearing both sets restores their visibility.
     * This update may be called from any thread and does not replace the timeline.
     */
    fun setHiddenDanmaku(ids: Set<Long>, texts: Set<String>)

    fun replaceWindow(window: DanmakuWindow, currentPositionMs: Long = 0L)

    /**
     * Rolls an overlapping segment window forward without restarting the renderer.
     * Returns false when the current engine state cannot be updated incrementally.
     */
    fun rollWindowForward(window: DanmakuWindow): Boolean

    fun replaceMaskFrames(frames: List<DanmakuMaskFrame>, currentPositionMs: Long)

    fun append(items: List<DanmakuItem>)

    /** Drops timeline items older than [positionMs] without restarting rendering. */
    fun trimBefore(positionMs: Long)

    fun addImmediate(item: DanmakuItem)

    fun seekTo(positionMs: Long)

    /** Reanchors the playback clock without clearing currently visible danmaku. */
    fun synchronizeTo(positionMs: Long)

    /** Reads media milliseconds on the UI thread; null restores the standalone clock. */
    fun setPlaybackPositionProvider(provider: LongSupplier?)

    fun start(positionMs: Long = 0L)

    fun pause()

    fun stop()

    fun clear()

    fun invalidate()

    /** Capture a tap in renderer-local pixels; invoke only after single-tap confirmation. */
    fun captureClick(x: Float, y: Float): DanmakuClickCapture?

    fun clearClickSelection()

    fun setOnItemClickListener(listener: ((DanmakuItem, RectF, PointF) -> Unit)?)

    override fun close()
}

/** A press owns the frozen item until cancellation or the action menu closes. */
class DanmakuClickCapture(
    private val confirm: () -> Unit,
    private val release: () -> Unit,
) {
    private var cancelled = false
    private var confirmed = false
    operator fun invoke() {
        if (!cancelled && !confirmed) {
            confirmed = true
            confirm()
        }
    }
    fun cancel() {
        if (!cancelled) {
            cancelled = true
            release()
        }
    }
}
