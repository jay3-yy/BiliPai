package com.android.purebilibili.core.performance

/** 单次导航按真实运动边界计时，迟到的帧仍回填到其发生时的记录，不按回调时间归属。 */
internal class PerformanceNavigationTracker(initialPage: String) {
    internal class Record(
        val id: Long,
        val startedNs: Long,
        val fromPage: String,
        var toPage: String,
        var kind: String,
        var endedNs: Long? = null,
        var outcome: String = "running",
        val ui: PerformanceFrameSummary = PerformanceFrameSummary(),
        var hwuiFrames: Long = 0,
        var deadlineAvailableFrames: Long = 0,
        var missedDeadlineFrames: Long = 0,
        var droppedMetricCallbacks: Long = 0,
        var maxTotalNs: Long? = null,
        var maxDrawNs: Long? = null,
        var maxGpuNs: Long? = null,
        val startReason: String = "motion_boundary",
        var destinationAssigned: Boolean = false,
    ) {
        fun snapshot() = Record(id, startedNs, fromPage, toPage, kind, endedNs, outcome, ui.snapshot(),
            hwuiFrames, deadlineAvailableFrames, missedDeadlineFrames, droppedMetricCallbacks, maxTotalNs, maxDrawNs, maxGpuNs, startReason, destinationAssigned)
    }

    private var page = initialPage
    private var settledPage = initialPage
    private var running: Record? = null
    private var lastPhase: String? = null
    private var sequence = 0L
    private var pendingDestination = false
    private var pageChangedAtNs = 0L
    private val records = ArrayDeque<Record>()

    fun pageChanged(value: String, timeNs: Long) {
        pageChangedAtNs = timeNs
        val previous = running
        if (previous?.destinationAssigned == true) {
            // 同类型页面也可对应不同导航实例；新目的地中断旧运动，分别保留统计。
            finish(timeNs, "superseded")
            page = value
            pendingDestination = true
            motion(timeNs, true, previous.kind, null, "destination_changed_mid_motion")
        } else {
            page = value
            if (previous != null) {
                previous.toPage = value
                previous.destinationAssigned = true
            } else pendingDestination = true
        }
    }

    val observedMotions: Long get() = sequence

    fun motion(timeNs: Long, active: Boolean, kind: String, phase: String?, startReason: String = "motion_boundary") {
        if (active) {
            val record = running ?: Record(++sequence, timeNs, settledPage, page, kind, startReason = startReason, destinationAssigned = pendingDestination).also {
                records.addLast(it)
                if (records.size > 40) records.removeFirst()
                running = it
                pendingDestination = false
                lastPhase = null
            }
            record.kind = kind
            if (phase != null) lastPhase = phase
        } else if (running != null) {
            finish(timeNs, if (lastPhase == "Cancel") "cancelled" else "settled")
        }
    }

    fun finish(timeNs: Long, reason: String) {
        running?.let {
            it.endedNs = timeNs
            it.toPage = page
            it.outcome = reason
        }
        running = null
        settledPage = page
    }

    private fun recordForFrame(startNs: Long, endNs: Long): Record? =
        records.lastOrNull { it.startedNs <= endNs && (it.endedNs == null || it.endedNs!! > startNs) }

    fun uiFrame(startNs: Long, endNs: Long, durationNs: Long, isJank: Boolean) {
        recordForFrame(startNs, endNs)?.ui?.add(durationNs, isJank)
        // 没有运动的目的地变化只更新基线，不伪造一段动画时长。
        // 目的地已经发布时，迟到的旧页帧不能把新页提前提升为已落位基线。
        // 无动画导航仍可由覆盖目的地变化时刻或其后的实际帧完成基线更新。
        if (running == null && (!pendingDestination || endNs >= pageChangedAtNs)) {
            settledPage = page
            pendingDestination = false
        }
    }

    fun hwuiFrame(startNs: Long, endNs: Long, totalNs: Long, drawNs: Long, gpuNs: Long, deadlineNs: Long, dropped: Int) {
        val record = recordForFrame(startNs, endNs) ?: return
        record.hwuiFrames++
        record.droppedMetricCallbacks += dropped
        if (totalNs >= 0) record.maxTotalNs = maxOf(record.maxTotalNs ?: totalNs, totalNs)
        if (drawNs >= 0) record.maxDrawNs = maxOf(record.maxDrawNs ?: drawNs, drawNs)
        if (gpuNs >= 0) record.maxGpuNs = maxOf(record.maxGpuNs ?: gpuNs, gpuNs)
        if (deadlineNs >= 0 && totalNs >= 0) {
            record.deadlineAvailableFrames++
            if (totalNs > deadlineNs) record.missedDeadlineFrames++
        }
    }

    fun snapshot(): List<Record> = records.map { it.snapshot() }
}
