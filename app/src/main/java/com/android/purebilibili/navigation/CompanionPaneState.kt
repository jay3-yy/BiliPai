package com.android.purebilibili.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

internal enum class CompanionPaneMode { Split, Feed, Video }

/** 拖动值只供测量读取，松手后才提交列表列数和详情分档使用的宽度。 */
@Stable
internal class CompanionPaneState(
    val defaultFeedFraction: Float,
    initialMode: CompanionPaneMode = CompanionPaneMode.Split,
    initialSplitFraction: Float = defaultFeedFraction,
) {
    var mode by mutableStateOf(initialMode)
        private set
    var splitFraction by mutableFloatStateOf(initialSplitFraction)
        private set
    var splitCommitRevision by mutableStateOf(0)
        private set
    var isDragging by mutableStateOf(false)
        private set
    private var dragFraction by mutableFloatStateOf(initialSplitFraction)

    fun applyPersistedSplitFraction(fraction: Float, widthDp: Float) {
        if (isDragging || splitCommitRevision > 0 || !fraction.isFinite()) return
        splitFraction = if (widthDp > 0f) constrainSplit(fraction, widthDp) else fraction.coerceIn(0.15f, 0.85f)
    }

    fun beginDrag() {
        dragFraction = when (mode) {
            CompanionPaneMode.Split -> splitFraction
            CompanionPaneMode.Feed -> 1f
            CompanionPaneMode.Video -> 0f
        }
        isDragging = true
    }

    fun dragBy(fraction: Float) {
        dragFraction = (dragFraction + fraction).coerceIn(0f, 1f)
    }

    fun endDrag(widthDp: Float) {
        if (!isDragging) return
        when {
            dragFraction <= 0.15f -> mode = CompanionPaneMode.Video
            dragFraction >= 0.85f -> mode = CompanionPaneMode.Feed
            else -> {
                splitFraction = constrainSplit(dragFraction, widthDp)
                splitCommitRevision++
                mode = CompanionPaneMode.Split
            }
        }
        isDragging = false
    }

    fun cancelDrag() { isDragging = false }

    fun visibleMode(canSplit: Boolean, immersivePlayback: Boolean): CompanionPaneMode = when {
        mode == CompanionPaneMode.Feed -> CompanionPaneMode.Feed
        !canSplit || immersivePlayback -> CompanionPaneMode.Video
        else -> mode
    }

    fun restoreSplit() {
        isDragging = false
        if (mode == CompanionPaneMode.Split) {
            splitFraction = defaultFeedFraction
            splitCommitRevision++
        }
        mode = CompanionPaneMode.Split
    }

    fun selectFraction(fraction: Float, widthDp: Float) {
        dragFraction = fraction.coerceIn(0f, 1f)
        isDragging = true
        endDrag(widthDp)
    }

    fun committedFeedFraction(widthDp: Float): Float = when (mode) {
        CompanionPaneMode.Split -> constrainSplit(splitFraction, widthDp)
        CompanionPaneMode.Feed -> 1f
        CompanionPaneMode.Video -> 0f
    }

    fun metadataFeedFraction(widthDp: Float): Float =
        if (mode == CompanionPaneMode.Video) constrainSplit(splitFraction, widthDp)
        else committedFeedFraction(widthDp)

    fun metadataVideoFraction(widthDp: Float): Float =
        if (mode == CompanionPaneMode.Feed) 1f - constrainSplit(splitFraction, widthDp)
        else 1f - committedFeedFraction(widthDp)

    /** 拖动中线条与把手实时跟随手指直到边缘，最小栏宽只约束松手后提交的双栏值。 */
    fun layoutFeedFraction(widthDp: Float): Float =
        if (isDragging) dragFraction.coerceIn(0f, 1f)
        else committedFeedFraction(widthDp)

    /**
     * 面板测量宽度：拖动中锁定在已提交值，只有分隔线/把手跟手，
     * 松手后一次性提交重排；逐帧重测整栏（首页网格 + 播放器 Surface）会造成拖动卡顿。
     * 隐藏面板继续以最近双栏宽度测量，避免零宽测量破坏内容状态。
     */
    fun measuredFeedFraction(widthDp: Float): Float =
        if (mode == CompanionPaneMode.Video) constrainSplit(splitFraction, widthDp)
        else committedFeedFraction(widthDp)

    fun measuredVideoFraction(widthDp: Float): Float =
        if (mode == CompanionPaneMode.Feed) 1f - constrainSplit(splitFraction, widthDp)
        else 1f - committedFeedFraction(widthDp)

    private fun constrainSplit(fraction: Float, widthDp: Float): Float = fraction.coerceIn(
        (280f / widthDp).coerceAtMost(0.45f),
        1f - (360f / widthDp).coerceAtMost(0.45f),
    )

    companion object {
        val Saver = listSaver<CompanionPaneState, Float>(
            save = { listOf(it.defaultFeedFraction, it.mode.ordinal.toFloat(), it.splitFraction) },
            restore = { CompanionPaneState(it[0], CompanionPaneMode.entries[it[1].toInt()], it[2]) },
        )
    }
}
