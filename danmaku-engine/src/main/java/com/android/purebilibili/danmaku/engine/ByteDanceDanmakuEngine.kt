package com.android.purebilibili.danmaku.engine

import android.os.Trace
import com.bytedance.danmaku.render.engine.render.sortOnlyIfOutOfOrder
import com.bytedance.danmaku.render.engine.DanmakuView
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.bitmap.BitmapData
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskData
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import com.bytedance.danmaku.render.engine.touch.IItemClickListener
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER
import java.util.function.LongSupplier

internal class ByteDanceDanmakuEngine(
    private val view: DanmakuView
) : DanmakuEngine {
    private val controller = view.controller
    private val visibility = DanmakuVisibility()
    @Volatile
    private var closed = false
    private val currentItems = ArrayList<DanmakuItem>()
    private var currentWindowItems: List<DanmakuItem>? = null
    private var currentSegmentIndices: List<Int> = emptyList()
    private val currentMaskFrames = ArrayList<DanmakuMaskFrame>()
    private var currentPositionMs: Long = 0L
    private var replaceWindowCount = 0L
    private var appendBatchCount = 0L
    private var appendedItemCount = 0L
    private var trimCount = 0L
    private var currentConfig = DanmakuRenderConfig()

    override var upOwnerUserHash: String? = null

    override var playbackState: DanmakuPlaybackState = DanmakuPlaybackState.STOPPED
        private set

    override val diagnostics: DanmakuEngineDiagnostics
        get() = DanmakuEngineDiagnostics(
            replaceWindowCount = replaceWindowCount,
            appendBatchCount = appendBatchCount,
            appendedItemCount = appendedItemCount,
            trimCount = trimCount,
            residentItemCount = currentItems.size
        )

    init {
        controller.addRenderLayer(ReverseScrollLayer())
    }

    private var configApplied = false

    override fun updateConfig(config: DanmakuRenderConfig) {
        if (closed) return
        if (configApplied && currentConfig == config) return
        val remeasureText = !configApplied || currentConfig.textSizePx != config.textSizePx ||
            currentConfig.typeface != config.typeface
        configApplied = true
        currentConfig = config
        val pinnedLineCount = if (config.lineCount <= 4) 0 else (config.lineCount / 2).coerceAtLeast(1)
        controller.config.apply {
            if (common.alpha != config.alpha) common.alpha = config.alpha
            if (common.playSpeed != config.playSpeedPercent) common.playSpeed = config.playSpeedPercent
            if (text.size != config.textSizePx) text.size = config.textSizePx
            if (text.typeface != config.typeface) text.typeface = config.typeface
            if (text.strokeWidth != config.strokeWidthPx) text.strokeWidth = config.strokeWidthPx
            if (text.strokeColor != config.strokeColor) text.strokeColor = config.strokeColor
            if (scroll.moveTime != config.scrollDurationMs) scroll.moveTime = config.scrollDurationMs
            if (scroll.lineHeight != config.lineHeightPx) scroll.lineHeight = config.lineHeightPx
            if (scroll.lineMargin != config.lineMarginPx) scroll.lineMargin = config.lineMarginPx
            if (scroll.itemMargin != 24f) scroll.itemMargin = 24f
            if (scroll.lineCount != config.lineCount) scroll.lineCount = config.lineCount
            if (scroll.marginTop != config.topMarginPx) scroll.marginTop = config.topMarginPx
            if (top.lineHeight != config.lineHeightPx) top.lineHeight = config.lineHeightPx
            if (top.lineMargin != config.lineMarginPx) top.lineMargin = config.lineMarginPx
            // The display-area setting is a total vertical budget. Splitting the
            // line count into top and bottom layers made a 1/4 selection occupy
            // roughly half the viewport (top + bottom each got half the budget).
            // Keep pinned layers conservative so they cannot expand the selected
            // band; scrolling remains the source of the configured line budget.
            if (top.lineCount != pinnedLineCount) top.lineCount = pinnedLineCount
            if (top.marginTop != config.topMarginPx) top.marginTop = config.topMarginPx
            if (top.showTimeMin != config.pinnedDurationMs) top.showTimeMin = config.pinnedDurationMs
            if (top.showTimeMax != config.pinnedDurationMs) top.showTimeMax = config.pinnedDurationMs
            if (bottom.lineHeight != config.lineHeightPx) bottom.lineHeight = config.lineHeightPx
            if (bottom.lineMargin != config.lineMarginPx) bottom.lineMargin = config.lineMarginPx
            if (bottom.lineCount != pinnedLineCount) bottom.lineCount = pinnedLineCount
            if (bottom.marginBottom != config.bottomMarginPx) bottom.marginBottom = config.bottomMarginPx
            if (bottom.showTimeMin != config.pinnedDurationMs) bottom.showTimeMin = config.pinnedDurationMs
            if (bottom.showTimeMax != config.pinnedDurationMs) bottom.showTimeMax = config.pinnedDurationMs
            if (mask.enable != config.maskEnabled) mask.enable = config.maskEnabled
        }
        if (remeasureText) controller.remeasureData { data ->
            when (data) {
                is EngineTextData -> {
                    val item = data.sourceItem
                    data.textSize = item.textSize ?: (config.textSizePx * item.textSizeScale)
                    data.textStrokeWidth = item.textStrokeWidth
                }
                is EngineBitmapData -> {
                    data.width = data.sourceItem.bitmapWidth
                    data.height = data.sourceItem.bitmapHeight
                }
            }
        }
    }

    override fun setHiddenDanmaku(ids: Set<Long>, texts: Set<String>) {
        if (closed) return
        if (visibility.setHiddenItems(ids, texts)) controller.invalidateView()
    }

    override fun replaceWindow(window: DanmakuWindow, currentPositionMs: Long) {
        clearClickSelection()
        if (closed) return
        this.currentPositionMs = currentPositionMs.coerceAtLeast(0L)
        if (currentWindowItems === window.items && currentSegmentIndices == window.segmentIndices) {
            controller.seekTimeline(this.currentPositionMs)
            return
        }
        currentWindowItems = window.items
        currentItems.clear()
        currentItems.addAll(window.items)
        sortOnlyIfOutOfOrder(currentItems, compareBy(DanmakuItem::showAtTime))
        currentSegmentIndices = window.segmentIndices
        replaceWindowCount++
        traceDanmakuEngineSection(TRACE_SET_DATA) {
            controller.setData(buildEngineTimeline(), this.currentPositionMs)
        }
    }

    override fun rollWindowForward(window: DanmakuWindow): Boolean {
        if (closed || currentSegmentIndices.isEmpty()) return false
        val previous = currentSegmentIndices.toSet()
        val next = window.segmentIndices.toSet()
        if (previous.intersect(next).isEmpty() || window.anchorSegment < currentSegmentIndices.first()) {
            return false
        }

        val addedSegments = next - previous
        val appended = window.items
            .filter { item -> segmentIndexForTime(item.showAtTime) in addedSegments }
            .sortedBy(DanmakuItem::showAtTime)
        if (appended.isNotEmpty()) {
            val ordered = currentItems.isEmpty() || currentItems.last().showAtTime <= appended.first().showAtTime
            currentItems.addAll(appended)
            if (!ordered) currentItems.sortBy(DanmakuItem::showAtTime)
            appendBatchCount++
            appendedItemCount += appended.size
            traceDanmakuEngineSection(TRACE_APPEND_DATA) {
                controller.appendData(appended.map(::toEngineData))
            }
        }

        val firstRetainedPositionMs = (window.segmentIndices.first() - 1L) * SEGMENT_DURATION_MS
        val firstRetained = currentItems.indexOfFirst { it.showAtTime >= firstRetainedPositionMs }
            .let { if (it < 0) currentItems.size else it }
        if (firstRetained > 0) {
            currentWindowItems = null
            currentItems.subList(0, firstRetained).clear()
            controller.discardDataBefore(firstRetainedPositionMs)
            trimCount++
        }
        currentSegmentIndices = window.segmentIndices
        currentWindowItems = window.items
        return true
    }

    override fun replaceMaskFrames(frames: List<DanmakuMaskFrame>, currentPositionMs: Long) {
        if (closed) return
        currentMaskFrames.clear()
        currentMaskFrames.addAll(frames)
        sortOnlyIfOutOfOrder(currentMaskFrames, compareBy(DanmakuMaskFrame::startTimeMs))
        this.currentPositionMs = currentPositionMs.coerceAtLeast(0L)
        // Mask refreshes never restart, recreate or sort the ordinary timeline.
        controller.replaceMaskData(currentMaskFrames.map(::toMaskData), this.currentPositionMs)
    }

    override fun append(items: List<DanmakuItem>) {
        if (closed || items.isEmpty()) return
        currentWindowItems = null
        appendBatchCount++
        appendedItemCount += items.size
        val sorted = items.toMutableList().apply { sortOnlyIfOutOfOrder(this, compareBy(DanmakuItem::showAtTime)) }
        if (currentItems.isEmpty() || currentItems.last().showAtTime <= sorted.first().showAtTime) {
            currentItems.addAll(sorted)
        } else {
            currentItems.addAll(sorted)
            currentItems.sortBy(DanmakuItem::showAtTime)
        }
        traceDanmakuEngineSection(TRACE_APPEND_DATA) {
            controller.appendData(sorted.map(::toEngineData))
        }
    }

    override fun trimBefore(positionMs: Long) {
        if (closed || currentItems.isEmpty()) return
        val firstRetained = currentItems.indexOfFirst { it.showAtTime >= positionMs }
            .let { if (it < 0) currentItems.size else it }
        if (firstRetained > 0) {
            currentWindowItems = null
            currentItems.subList(0, firstRetained).clear()
            controller.discardDataBefore(positionMs)
            trimCount++
        }
    }

    override fun addImmediate(item: DanmakuItem) {
        if (closed) return
        controller.addFakeData(toEngineData(item))
    }

    override fun seekTo(positionMs: Long) {
        clearClickSelection()
        if (closed) return
        currentPositionMs = positionMs.coerceAtLeast(0L)
        val shouldResume = playbackState == DanmakuPlaybackState.PLAYING
        controller.pause()
        controller.clear()
        controller.start(currentPositionMs)
        if (!shouldResume) controller.pause()
    }

    override fun synchronizeTo(positionMs: Long) {
        if (closed) return
        currentPositionMs = positionMs.coerceAtLeast(0L)
        controller.synchronizeTo(currentPositionMs)
    }

    override fun setPlaybackPositionProvider(provider: LongSupplier?) {
        if (!closed) controller.setPlaybackPositionProvider(provider)
    }

    override fun start(positionMs: Long) {
        if (closed) return
        currentPositionMs = positionMs.coerceAtLeast(0L)
        controller.start(currentPositionMs)
        playbackState = DanmakuPlaybackState.PLAYING
    }

    override fun pause() {
        if (closed) return
        controller.pause()
        playbackState = DanmakuPlaybackState.PAUSED
    }

    override fun stop() {
        clearClickSelection()
        if (closed) return
        controller.stop()
        currentWindowItems = null
        playbackState = DanmakuPlaybackState.STOPPED
        currentPositionMs = 0L
    }

    override fun clear() {
        clearClickSelection()
        if (closed) return
        controller.clear()
    }

    override fun invalidate() {
        if (!closed) controller.invalidateView()
    }

    private var clickSelection: DanmakuClickCapture? = null

    override fun clearClickSelection() {
        clickSelection?.cancel()
        clickSelection = null
    }

    override fun captureClick(x: Float, y: Float): DanmakuClickCapture? {
        clearClickSelection()
        if (closed) return null
        val (confirm, release) = controller.captureDanmakuClick(x, y) ?: return null
        return DanmakuClickCapture(
            confirm = { if (!closed) confirm() },
            release = { if (!closed) release() },
        ).also { clickSelection = it }
    }

    override fun setOnItemClickListener(listener: ((DanmakuItem, android.graphics.RectF, android.graphics.PointF) -> Unit)?) {
        if (closed) return
        controller.itemClickListener = listener?.let { callback ->
            object : IItemClickListener {
                override fun onDanmakuClick(
                    data: DanmakuData,
                    itemRect: android.graphics.RectF,
                    clickPoint: android.graphics.PointF
                ) {
                    if (!data.isVisible) return
                    (data as? SourceBackedDanmakuData)?.sourceItem?.let {
                        callback(it, itemRect, clickPoint)
                    }
                }
            }
        }
    }

    override fun close() {
        clearClickSelection()
        if (closed) return
        closed = true
        visibility.close()
        controller.itemClickListener = null
        controller.stop()
        controller.setPlaybackPositionProvider(null)
        currentItems.clear()
        currentWindowItems = null
        currentMaskFrames.clear()
        playbackState = DanmakuPlaybackState.CLOSED
    }

    private fun toEngineData(item: DanmakuItem): DanmakuData {
        val sourceData = if (item.bitmap != null) {
            EngineBitmapData(item, visibility).apply {
                bitmap = item.bitmap
                width = item.bitmapWidth
                height = item.bitmapHeight
            }
        } else {
            EngineTextData(item, visibility).apply {
                text = item.text
                textSize = item.textSize ?: item.textSizeScale
                    .takeUnless { it == 1f }
                    ?.let { scale -> currentConfig.textSizePx * scale }
                textColor = item.textColor
                isVipGradualColor = item.isVipGradualColor
                isUpOwner = upOwnerUserHash != null && item.userHash == upOwnerUserHash
                typeface = item.typeface
                textStrokeWidth = item.textStrokeWidth
                textStrokeColor = item.textStrokeColor
                includeFontPadding = item.includeFontPadding
                hasUnderline = item.hasUnderline
            }
        }
        sourceData.showAtTime = item.showAtTime
        sourceData.layerType = when (item.layerType) {
            DANMAKU_LAYER_TOP -> LAYER_TYPE_TOP_CENTER
            DANMAKU_LAYER_BOTTOM -> LAYER_TYPE_BOTTOM_CENTER
            DANMAKU_LAYER_REVERSE -> REVERSE_LAYER_TYPE
            else -> LAYER_TYPE_SCROLL
        }
        return sourceData
    }

    private fun buildEngineTimeline(): List<DanmakuData> =
        currentItems.map(::toEngineData) + currentMaskFrames.map(::toMaskData)

    private fun toMaskData(frame: DanmakuMaskFrame): DanmakuData = MaskData().apply {
        showAtTime = frame.startTimeMs
        start = frame.startTimeMs
        end = frame.endTimeMs
        path = frame.path
        pathWidth = frame.sourceWidth
        pathHeight = frame.sourceHeight
    }
}

private const val SEGMENT_DURATION_MS = 360_000L

private fun segmentIndexForTime(positionMs: Long): Int =
    (positionMs.coerceAtLeast(0L) / SEGMENT_DURATION_MS).toInt() + 1

internal interface SourceBackedDanmakuData {
    val sourceItem: DanmakuItem
}

internal class EngineTextData(
    override val sourceItem: DanmakuItem,
    private val visibility: DanmakuVisibility
) : TextData(), SourceBackedDanmakuData {
    override val isVisible: Boolean
        get() = visibility.isVisible(sourceItem.danmakuId, sourceItem.mergedOriginalText ?: sourceItem.text)
}

internal class EngineBitmapData(
    override val sourceItem: DanmakuItem,
    private val visibility: DanmakuVisibility
) : BitmapData(), SourceBackedDanmakuData {
    override val isVisible: Boolean
        get() = visibility.isVisible(sourceItem.danmakuId, sourceItem.mergedOriginalText ?: sourceItem.text)
}

private inline fun <T> traceDanmakuEngineSection(name: String, block: () -> T): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

private const val TRACE_SET_DATA = "BiliPaiDanmakuSetData"
private const val TRACE_APPEND_DATA = "BiliPaiDanmakuAppend"
