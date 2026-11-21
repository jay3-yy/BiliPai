package com.android.purebilibili.core.performance

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import androidx.metrics.performance.FrameData
import com.android.purebilibili.BuildConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal const val PERFORMANCE_CAPTURE_SETTING_ID = "diagnostics.performance_capture"
internal const val PERFORMANCE_CAPTURE_TITLE = "性能专项测试"
internal const val PERFORMANCE_CAPTURE_SECTION = "performance_diagnostics"
internal const val PERFORMANCE_CAPTURE_SECTION_TITLE = "性能排查"
private const val PERFORMANCE_MEASUREMENT_NOTES = "offsetMs 相对 startedMonotonicNs，使用不含深度休眠的单调时钟；elapsedMs 包含休眠。API31+ 硬件窗口的 frames 与 hwui 同源，按原始 intended-vsync 去重；其他窗口的 UI 统计使用 JankStats。UI 卡顿采用截止时间两倍启发式，API31 以下沿用 JankStats 判定；≥100ms 长帧独立计数，不受截止时间放宽影响。uiFrameSource 标记每窗来源，duplicateHwuiCallbacks 记录重复回调。原始帧的 activeSignals 未采集时为 null。面板刷新率不代表应用提交帧率。百分位为 1ms 桶上界，501 表示超过 500ms。CPU 以单核 100% 计。资源约每 5 秒采样，恢复前台时重置区间。前台窗口分别记录，最多 32 个窗口，每窗保留最近 40 次转场。窗口恢复到首个 UI 帧记录应用生命周期响应时间。转场 startReason、outcome 和 completeMotionBoundaries 标记观察边界；转场时长包含手势停留，不等于页面加载耗时。导航排行只覆盖每窗保留的最近 40 次转场。滚动尾段为最后位移后的 120ms。GPU 和帧截止指标由 API31+ 系统提供，缺失项为 null。droppedMetricCallbacks 记录采集回调丢失数。检查点按采样周期保存。播放器解码、缓冲和媒体丢帧不在采集范围内。"
private const val PERFORMANCE_COVERAGE_NOTE = "页面排序包含停留、滚动和切换，优先按 ≥100ms 长帧数排序；sceneRanking 保留各操作独立分布，navigationRanking 保留单次转场。pageAtEnd 和 windowIdAtEnd 仅记录采样结束时的位置。"
private const val MAX_CAPTURE_MS = 15 * 60 * 1000L
private val HWUI_UI_METRIC_IDS = intArrayOf(
    FrameMetrics.UNKNOWN_DELAY_DURATION, FrameMetrics.INPUT_HANDLING_DURATION,
    FrameMetrics.ANIMATION_DURATION, FrameMetrics.LAYOUT_MEASURE_DURATION,
    FrameMetrics.DRAW_DURATION, FrameMetrics.SYNC_DURATION,
)

internal data class PerformanceCaptureStatus(val recording: Boolean = false, val message: String = "尚未开始采集")
internal data class PerformanceWorkToken(
    val sessionStartNs: Long, val startedNs: Long, val stage: String, val windowId: Int, val page: String,
    val isMainThread: Boolean,
)

/** 用户主动开启的有界会话。只在 Activity RESUMED 时采样，不改变刷新率或视觉策略。 */
internal object PerformanceCapture {
    private val mutableStatus = MutableStateFlow(PerformanceCaptureStatus())
    val status = mutableStatus.asStateFlow()
    private var session: Session? = null
    private class WindowState(val id: Int, val kind: String, var page: String, val displaySnapshot: () -> JSONObject) {
        var resumed = false
        var lastResumeOrder = 0L
        val transitionOwners = mutableSetOf<Any>()
        var motionKind = "navigation"
        var phase: String? = null
        var destination: Any? = null
    }
    private data class WindowHandoff(
        val fromWindowId: Int?, val fromPage: String?, val toWindowId: Int, val toPage: String, val resumedNs: Long,
        var firstUiFrameNs: Long? = null, var outcome: String = "awaiting_ui_frame",
    )
    private class CapturedWindow(val id: Int, val kind: String, startNs: Long, page: String, transitioning: Boolean) {
        val timeline = PerformanceSceneTimeline(startNs, page, transitioning)
        val navigation = PerformanceNavigationTracker(page)
        val ui = PerformanceFrameSummary()
        val hwui = PerformanceHwuiSummary()
        val hwuiIdentity = PerformanceFrameIdentity()
        val fallbackUiIdentity = PerformanceFrameIdentity()
        var hwuiAvailable: Boolean? = null
        var closed = false
        var pendingHandoff: WindowHandoff? = null
    }
    private val registeredWindows = java.util.IdentityHashMap<Any, WindowState>()
    private var nextWindowId = 0
    private var resumeOrder = 0L
    private val samplingOwner = MutableStateFlow<Any?>(null)
    private val diagnosticSignalKeys = setOf(
        "VideoCardTransition", "home:pager_swipe", "home:header_transition",
        "video_detail:tab_swipe", "video_detail:intro_scroll", "video_detail:comment_scroll",
        "video_detail:player_swipe_collapse",
    )
    private val navigationWorkStages = setOf(
        "return_prepare", "player_construct", "player_create", "player_attach",
        "player_view_create", "player_release", "playback_exit_cleanup",
    )
    private data class NavigationWork(val token: PerformanceWorkToken, val durationNs: Long)
    private data class DataEvent(val timeNs: Long, val screen: String, val action: String, val windowId: Int, val page: String)

    fun recordScreenCommit(owner: Any, screen: String, navigationReady: Boolean) {
        if (!status.value.recording) return
        synchronized(this) {
            val captured = recordingWindow(owner) ?: return
            session?.screenCommits?.record(captured.id, screen, navigationReady)
        }
    }

    /** 数据工作入口事件，不等同于 HTTP 请求；不保存查询词、账号或媒体身份。 */
    fun recordDataEvent(screen: String, action: String) {
        if (!status.value.recording) return
        synchronized(this) {
            val current = session?.takeIf { status.value.recording } ?: return
            val window = registeredWindows.values.filter { it.resumed }.maxByOrNull { it.lastResumeOrder } ?: return
            if (current.dataEvents.size == 100) current.dataEvents.removeFirst()
            current.dataEvents.addLast(DataEvent(System.nanoTime(), screen, action, window.id, window.page))
        }
    }

    /** 仅专项开启时测量离散的导航同步工作；不采集逐帧录制，也不保存媒体身份。 */
    fun beginNavigationWork(stage: String): PerformanceWorkToken? {
        if (!status.value.recording || stage !in navigationWorkStages) return null
        return synchronized(this) {
            val current = session?.takeIf { status.value.recording } ?: return@synchronized null
            val window = registeredWindows.values.filter { it.resumed }.maxByOrNull { it.lastResumeOrder }
                ?: return@synchronized null
            PerformanceWorkToken(current.startedNanoTime, System.nanoTime(), stage, window.id, window.page,
                android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        }
    }

    fun finishNavigationWork(token: PerformanceWorkToken) {
        val durationNs = System.nanoTime() - token.startedNs
        synchronized(this) {
            val current = session?.takeIf { status.value.recording && it.startedNanoTime == token.sessionStartNs } ?: return
            if (current.navigationWork.size == 100) current.navigationWork.removeFirst()
            current.navigationWork.addLast(NavigationWork(token, durationNs))
        }
    }

    private data class SampleCounters(val frames: Long, val jankyFrames: Long, val longFrames: Long, val deadlineFrames: Long, val missedDeadlines: Long)

    private class Session(val environment: JSONObject) {
        val startedNanoTime = System.nanoTime()
        val windows = linkedMapOf<Int, CapturedWindow>()
        var omittedWindows = 0
        val handoffs = ArrayDeque<WindowHandoff>()
        var lastForeground: Pair<Int, String>? = null
        val scenes = linkedMapOf<String, PerformanceFrameSummary>()
        val hwui = PerformanceHwuiSummary()
        val hwuiScenes = linkedMapOf<String, PerformanceHwuiSummary>()
        val slowHwuiFrames = JSONArray()
        val startedWallMs = System.currentTimeMillis()
        val startedElapsedMs = SystemClock.elapsedRealtime()
        var nextResourceSampleAtMs = startedElapsedMs + 5_000
        val total = PerformanceFrameSummary()
        val pages = linkedMapOf<String, PerformanceFrameSummary>()
        val samples = JSONArray()
        val intervals = mutableListOf<PerformanceIntervalInsight>()
        val markers = JSONArray()
        val navigationWork = ArrayDeque<NavigationWork>()
        val screenCommits = PerformanceScreenCommitCounts()
        val dataEvents = ArrayDeque<DataEvent>()
        val slowFrames = JSONArray()
        var foregroundMs = 0L
        var foregroundSinceMs: Long? = null
        var stopReason: String? = null
        var stoppedElapsedMs: Long? = null
        var stoppedNanoTime: Long? = null
    }

    private fun Session.sampleCounters() = SampleCounters(total.frames, total.jankyFrames, total.longFrames100Ms, hwui.deadlineAvailableFrames, hwui.missedDeadlines)

    @Synchronized
    fun registerWindow(owner: Any, kind: String, initialRoute: String?, displaySnapshot: () -> JSONObject) {
        val state = WindowState(++nextWindowId, kind, pageName(initialRoute), displaySnapshot)
        registeredWindows[owner] = state
        session?.takeIf { status.value.recording }?.let { addWindow(it, state) }
    }

    @Synchronized
    fun unregisterWindow(owner: Any) {
        val state = registeredWindows.remove(owner) ?: return
        session?.windows?.get(state.id)?.let {
            it.navigation.finish(System.nanoTime(), "window_destroyed")
            it.pendingHandoff?.outcome = "window_destroyed_before_ui_frame"
            it.pendingHandoff = null
            it.closed = true
        }
        selectSampler()
        if (registeredWindows.values.none { it.resumed }) session?.let(::closeForeground)
    }

    private fun pageName(route: String?) = route?.substringBefore('/')?.substringBefore('?') ?: "unknown"

    private fun addWindow(current: Session, state: WindowState) {
        if (current.windows.size == 32) { current.omittedWindows++; return }
        val now = System.nanoTime()
        current.windows[state.id] = CapturedWindow(state.id, state.kind, now, state.page, state.transitionOwners.isNotEmpty()).also {
            if (state.resumed && state.transitionOwners.isNotEmpty()) it.navigation.motion(now, true, state.motionKind, state.phase, "capture_started_mid_motion")
        }
    }

    private fun selectSampler() {
        samplingOwner.value = if (registeredWindows.values.any { it.resumed })
            registeredWindows.entries.minByOrNull { it.value.id }?.key else null
    }

    @Synchronized
    fun setPage(owner: Any, route: String?, destination: Any) {
        val state = registeredWindows[owner] ?: return
        val destinationChanged = state.destination !== destination
        state.destination = destination
        state.page = pageName(route)
        if (!status.value.recording) return
        session?.let { if (it.lastForeground?.first == state.id) it.lastForeground = state.id to state.page }
        session?.windows?.get(state.id)?.let {
            it.timeline.boundary(System.nanoTime(), state.page, state.transitionOwners.isNotEmpty())
            if (destinationChanged) it.navigation.pageChanged(state.page, System.nanoTime())
        }
    }

    private fun recordingWindow(owner: Any): CapturedWindow? {
        if (!status.value.recording) return null
        val state = registeredWindows[owner]?.takeIf { it.resumed } ?: return null
        return session?.windows?.get(state.id)
    }

    @Synchronized
    fun onFrame(owner: Any, frame: FrameData) {
        val current = session ?: return
        val captured = recordingWindow(owner) ?: return
        // API31+ 硬件窗口统一消费原始 FrameMetrics；JankStats 的起点可能被推至上一帧末尾，
        // 因而不能用它重建原始帧身份或再累计一份 UI 数据。
        if (Build.VERSION.SDK_INT >= 31 && captured.hwuiAvailable != false) return
        if (elapsed(current) >= MAX_CAPTURE_MS) {
            stop("time_limit")
            return
        }
        if (frame.frameStartNanos < current.startedNanoTime) return
        if (!captured.fallbackUiIdentity.accept(frame.frameStartNanos)) return
        recordUiFrame(current, captured, frame.frameStartNanos, frame.frameDurationUiNanos, frame.isJank,
            frame.states.map { it.key }.filter { it in diagnosticSignalKeys })
    }

    private fun recordUiFrame(current: Session, captured: CapturedWindow, startNs: Long, durationNs: Long,
        isJank: Boolean, activeSignals: List<String>? = null,
    ) {
        val scene = captured.timeline.sceneAt(startNs, startNs + durationNs)
        val windowSceneKey = "${captured.id}/${scene.key}"
        val sceneKey = if (windowSceneKey in current.scenes || current.scenes.size < 64) windowSceneKey else "other"
        captured.pendingHandoff?.takeIf { startNs >= it.resumedNs }?.let {
            it.firstUiFrameNs = startNs
            it.outcome = "ui_frame_observed"
            captured.pendingHandoff = null
        }
        captured.ui.add(durationNs, isJank)
        captured.navigation.uiFrame(startNs, startNs + durationNs, durationNs, isJank)
        current.scenes.getOrPut(sceneKey) { PerformanceFrameSummary() }.add(durationNs, isJank)
        current.total.add(durationNs, isJank)
        val windowPage = "${captured.id}/${scene.page}"
        val pageKey = if (windowPage in current.pages || current.pages.size < 48) windowPage else "other"
        current.pages.getOrPut(pageKey) { PerformanceFrameSummary() }
            .add(durationNs, isJank)
        if (durationNs >= 100_000_000L) {
            if (current.slowFrames.length() == 100) current.slowFrames.remove(0)
            current.slowFrames.put(JSONObject().put("offsetMs", (startNs - current.startedNanoTime) / 1_000_000.0)
                .put("windowId", captured.id).put("page", scene.page).put("interaction", scene.interaction).put("uiDurationMs", durationNs / 1_000_000.0)
                .put("activeSignals", activeSignals?.let(::JSONArray) ?: JSONObject.NULL))
        }
    }

    @Synchronized
    fun onScroll(owner: Any) {
        recordingWindow(owner)?.timeline?.scroll(System.nanoTime())
    }

    @Synchronized
    fun setTransition(windowOwner: Any, owner: Any, active: Boolean, kind: String = "navigation", phase: String? = null, disposed: Boolean = false) {
        val state = registeredWindows[windowOwner] ?: return
        val wasRunning = state.transitionOwners.isNotEmpty()
        if (active) state.transitionOwners.add(owner) else state.transitionOwners.remove(owner)
        if (active) {
            if (!wasRunning) state.phase = null
            state.motionKind = kind
            if (phase != null) state.phase = phase
        }
        val running = state.transitionOwners.isNotEmpty()
        val motionPhase = state.phase
        if (!running) state.phase = null
        val captured = recordingWindow(windowOwner) ?: return
        val now = System.nanoTime()
        if (wasRunning != running) captured.timeline.boundary(now, state.page, running)
        if (disposed && !running) captured.navigation.finish(now, "entry_disposed")
        else captured.navigation.motion(now, running, state.motionKind, motionPhase)
    }

    @Synchronized
    fun setHwuiAvailable(owner: Any, available: Boolean) { recordingWindow(owner)?.hwuiAvailable = available }

    @Synchronized
    fun onHwuiFrame(owner: Any, metrics: FrameMetrics, droppedCallbacks: Int) {
        val current = session ?: return
        val captured = recordingWindow(owner) ?: return
        if (elapsed(current) >= MAX_CAPTURE_MS) { stop("time_limit"); return }
        val startNs = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        if (startNs < current.startedNanoTime || !captured.hwuiIdentity.accept(startNs)) return
        val uiDuration = HWUI_UI_METRIC_IDS.sumOf { metrics.getMetric(it).coerceAtLeast(0) }
        val scene = captured.timeline.sceneAt(startNs, startNs + uiDuration)
        val windowSceneKey = "${captured.id}/${scene.key}"
        val key = if (windowSceneKey in current.hwuiScenes || current.hwuiScenes.size < 32) windowSceneKey else "other"
        captured.hwui.add(metrics, droppedCallbacks)
        current.hwui.add(metrics, droppedCallbacks)
        current.hwuiScenes.getOrPut(key) { PerformanceHwuiSummary() }.add(metrics, droppedCallbacks)
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
        if (Build.VERSION.SDK_INT >= 31) {
            recordUiFrame(current, captured, startNs, uiDuration, isPerformanceUiJank(uiDuration, deadline))
        }
        val gpuDuration = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1L
        captured.navigation.hwuiFrame(startNs, startNs + uiDuration, total, metrics.getMetric(FrameMetrics.DRAW_DURATION), gpuDuration, deadline, droppedCallbacks)
        if (total >= 100_000_000 || (deadline >= 0 && total > deadline)) {
            if (current.slowHwuiFrames.length() == 100) current.slowHwuiFrames.remove(0)
            current.slowHwuiFrames.put(JSONObject()
                .put("offsetMs", (startNs - current.startedNanoTime) / 1_000_000.0)
                .put("intendedVsyncNs", startNs).put("windowId", captured.id).put("page", scene.page).put("interaction", scene.interaction)
                .put("deadlineMs", if (deadline < 0) JSONObject.NULL else deadline / 1_000_000.0)
                .put("stagesMs", JSONObject().apply {
                    for ((name, metric) in PerformanceHwuiSummary.BASE_STAGES) {
                        val duration = metrics.getMetric(metric)
                        put(name, if (duration < 0) JSONObject.NULL else duration / 1_000_000.0)
                    }
                    val gpu = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1L
                    put("gpu", if (gpu < 0) JSONObject.NULL else gpu / 1_000_000.0)
                }))
        }
    }

    suspend fun start(context: Context) {
        val environment = withContext(Dispatchers.IO) {
            JSONObject().put("version", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
                .put("buildType", BuildConfig.BUILD_TYPE).put("debug", BuildConfig.DEBUG)
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT).put("android", Build.VERSION.RELEASE)
                .put("processors", Runtime.getRuntime().availableProcessors())
                .put("uiStyle", com.android.purebilibili.core.store.SettingsManager.getUiStyle(context).first().name)
                .put("liquidGlass", com.android.purebilibili.core.store.SettingsManager.getLiquidGlassEnabled(context).first())
        }
        synchronized(this) {
            session = Session(environment).apply {
                registeredWindows.values.forEach { addWindow(this, it) }
                registeredWindows.values.filter { it.resumed }.maxByOrNull { it.lastResumeOrder }?.let {
                    lastForeground = it.id to it.page
                    foregroundSinceMs = SystemClock.elapsedRealtime()
                }
            }
            mutableStatus.value = PerformanceCaptureStatus(true, "正在采集；请正常使用并复现问题，最长 15 分钟")
        }
        checkpoint(context)
    }

    @Synchronized
    fun fail(message: String) {
        stop("storage_error")
        mutableStatus.value = PerformanceCaptureStatus(false, message)
    }

    @Synchronized
    fun mark(owner: Any) {
        val current = session ?: return
        if (!status.value.recording) return
        if (current.markers.length() < 100) {
            current.markers.put(JSONObject().put("offsetMs", timelineOffset(current)).put("wallMs", System.currentTimeMillis())
                .put("reviewFromOffsetMs", (timelineOffset(current) - 30_000).coerceAtLeast(0))
                .put("windowId", registeredWindows[owner]?.id ?: JSONObject.NULL)
                .put("page", registeredWindows[owner]?.page ?: JSONObject.NULL))
            mutableStatus.value = status.value.copy(message = "已标记问题时间点（${current.markers.length()}）；采集继续")
        } else {
            mutableStatus.value = status.value.copy(message = "已保留 100 个问题标记；采集继续")
        }
    }

    @Synchronized
    fun stop(reason: String = "user") {
        val current = session ?: return
        if (!status.value.recording) return
        closeForeground(current)
        current.windows.values.forEach {
            it.navigation.finish(System.nanoTime(), reason)
            it.pendingHandoff?.outcome = "capture_ended_before_ui_frame"
            it.pendingHandoff = null
        }
        current.stopReason = reason
        current.stoppedElapsedMs = SystemClock.elapsedRealtime()
        current.stoppedNanoTime = System.nanoTime()
        mutableStatus.value = PerformanceCaptureStatus(false,
            "采集已结束：${current.windows.size} 个窗口，${current.windows.values.sumOf { it.navigation.observedMotions }} 次转场，${current.total.frames} 帧；可分享报告")
    }

    @Synchronized
    fun setWindowResumed(owner: Any, resumed: Boolean) {
        val state = registeredWindows[owner] ?: return
        state.resumed = resumed
        if (resumed) state.lastResumeOrder = ++resumeOrder
        selectSampler()
        val now = System.nanoTime()
        session?.windows?.get(state.id)?.let {
            if (resumed) {
                it.timeline.boundary(now, state.page, state.transitionOwners.isNotEmpty())
                if (status.value.recording && state.transitionOwners.isNotEmpty()) it.navigation.motion(now, true, state.motionKind, state.phase, "window_resumed_mid_motion")
                if (status.value.recording) session?.let { current ->
                    val from = current.lastForeground
                    val handoff = WindowHandoff(from?.first, from?.second, state.id, state.page, now)
                    current.handoffs.addLast(handoff)
                    if (current.handoffs.size > 100) current.handoffs.removeFirst()
                    it.pendingHandoff = handoff
                    current.lastForeground = state.id to state.page
                }
            } else {
                it.navigation.finish(now, "window_paused")
                it.pendingHandoff?.outcome = "window_paused_before_ui_frame"
                it.pendingHandoff = null
            }
        }
        session?.takeIf { status.value.recording }?.let {
            if (registeredWindows.values.any { state -> state.resumed }) {
                if (it.foregroundSinceMs == null) it.foregroundSinceMs = SystemClock.elapsedRealtime()
            } else closeForeground(it)
        }
    }

    /** 各 Activity 提供生命周期作用域，但仅被选中的一个运行进程资源采样；无前台窗口时取消。 */
    suspend fun observeProcessSamples(context: Context, owner: Any) {
        try {
            combine(status.map { it.recording }, samplingOwner) { recording, sampler -> recording && sampler === owner }
                .distinctUntilChanged().collectLatest { sampling ->
                    if (!sampling) {
                        val ownsSampler = synchronized(this@PerformanceCapture) {
                            registeredWindows.entries.minByOrNull { it.value.id }?.key === owner
                        }
                        if (ownsSampler) checkpointObserved(context)
                        return@collectLatest
                    }
                    synchronized(this@PerformanceCapture) {
                        session?.let { if (elapsed(it) >= MAX_CAPTURE_MS) stop("time_limit") }
                    }
                    var previousTime = SystemClock.elapsedRealtime()
                    var previousNanoTime = System.nanoTime()
                    var previousCounters = synchronized(this@PerformanceCapture) { session?.sampleCounters() } ?: return@collectLatest
                    var previousCpu = Process.getElapsedCpuTime()
                    while (status.value.recording) {
                        val waitMs = synchronized(this@PerformanceCapture) {
                            ((session?.nextResourceSampleAtMs ?: SystemClock.elapsedRealtime()) - SystemClock.elapsedRealtime()).coerceIn(250L, 5_000L)
                        }
                        delay(waitMs)
                        val now = SystemClock.elapsedRealtime()
                        val cpu = Process.getElapsedCpuTime()
                        val sampleNanoTime = System.nanoTime()
                        val activeWindow = synchronized(this@PerformanceCapture) {
                            registeredWindows.values.filter { it.resumed }.maxByOrNull { it.lastResumeOrder }
                        }
                        val display = activeWindow?.displaySnapshot?.invoke() ?: JSONObject()
                        val (frameCounts, hwuiCumulative) = synchronized(this@PerformanceCapture) {
                            session?.let { it.sampleCounters() to it.hwui.cumulativeJson() }
                        } ?: break
                        val sampleWindow = synchronized(this@PerformanceCapture) {
                            activeWindow?.let { it.id to it.page }
                        }
                        val sample = withContext(Dispatchers.Default) {
                            val runtime = Runtime.getRuntime()
                            val power = context.getSystemService(PowerManager::class.java)
                            JSONObject().put("processCpuPercentOneCore", (cpu - previousCpu) * 100.0 / (now - previousTime))
                                .put("javaHeapUsedBytes", runtime.totalMemory() - runtime.freeMemory())
                                .put("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize())
                                .put("pssKb", Debug.getPss()).put("powerSave", power.isPowerSaveMode)
                                .put("thermalStatus", if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else JSONObject.NULL)
                                .put("display", display)
                        }
                        val previousSampleTime = previousTime
                        previousTime = now
                        previousCpu = cpu
                        synchronized(this@PerformanceCapture) {
                            session?.let { current ->
                                sample.put("offsetMs", (sampleNanoTime - current.startedNanoTime) / 1_000_000.0).put("windowId", sampleWindow?.first ?: JSONObject.NULL).put("page", sampleWindow?.second ?: JSONObject.NULL)
                                    .put("startOffsetMs", (previousNanoTime - current.startedNanoTime) / 1_000_000.0)
                                    .put("hwuiDeadlineFrames", frameCounts.deadlineFrames - previousCounters.deadlineFrames)
                                    .put("hwuiMissedDeadlines", if (frameCounts.deadlineFrames == previousCounters.deadlineFrames) JSONObject.NULL
                                        else frameCounts.missedDeadlines - previousCounters.missedDeadlines)
                                    .put("foregroundWindowIds", JSONArray(registeredWindows.values.filter { it.resumed }.map { it.id }))
                                    .put("hwuiCumulative", hwuiCumulative)
                                    .put("sampleIntervalMs", now - previousSampleTime)
                                    .put("observedUiFrames", frameCounts.frames - previousCounters.frames)
                                    .put("jankyUiFrames", frameCounts.jankyFrames - previousCounters.jankyFrames)
                                    .put("longUiFrames100Ms", frameCounts.longFrames - previousCounters.longFrames)
                                current.nextResourceSampleAtMs = now + 5_000
                                if (current.samples.length() < 180) {
                                    current.samples.put(sample)
                                    current.intervals.add(PerformanceIntervalInsight(
                                        (previousNanoTime - current.startedNanoTime) / 1_000_000.0,
                                        (sampleNanoTime - current.startedNanoTime) / 1_000_000.0,
                                        sampleWindow?.first, sampleWindow?.second,
                                        sample.getLong("observedUiFrames"), sample.getLong("jankyUiFrames"),
                                        sample.getDouble("processCpuPercentOneCore"),
                                        frameCounts.deadlineFrames - previousCounters.deadlineFrames,
                                        frameCounts.missedDeadlines - previousCounters.missedDeadlines,
                                        longFrames100Ms = frameCounts.longFrames - previousCounters.longFrames,
                                    ))
                                }
                                previousNanoTime = sampleNanoTime
                                previousCounters = frameCounts
                                if (elapsed(current) >= MAX_CAPTURE_MS) stop("time_limit")
                            }
                        }
                        checkpointObserved(context)
                    }
                }
        } finally {
            withContext(NonCancellable) { checkpointObserved(context) }
        }
    }

    private suspend fun checkpointObserved(context: Context) {
        if (synchronized(this) { session?.stopReason == "storage_error" }) return
        try {
            checkpoint(context)
        } catch (error: java.io.IOException) {
            fail("报告保存失败：${error.localizedMessage}")
            com.android.purebilibili.core.util.Logger.e("PerformanceCapture", "性能报告写入失败", error)
        }
    }

    private fun closeForeground(current: Session) {
        current.foregroundSinceMs?.let { current.foregroundMs += SystemClock.elapsedRealtime() - it }
        current.foregroundSinceMs = null
    }

    private fun elapsed(current: Session) = (current.stoppedElapsedMs ?: SystemClock.elapsedRealtime()) - current.startedElapsedMs

    private fun timelineOffset(current: Session) = ((current.stoppedNanoTime ?: System.nanoTime()) - current.startedNanoTime) / 1_000_000L

    private fun PerformanceFrameSummary.json() = JSONObject().put("frames", frames)
        .put("jankyFrames", jankyFrames)
        .put("longFrames100Ms", longFrames100Ms)
        .put("jankPercent", if (frames == 0L) JSONObject.NULL else jankyFrames * 100.0 / frames)
        .put("p50UiMsUpperBound", percentileUpperBoundMs(.50) ?: JSONObject.NULL)
        .put("p95UiMsUpperBound", percentileUpperBoundMs(.95) ?: JSONObject.NULL)
        .put("p99UiMsUpperBound", percentileUpperBoundMs(.99) ?: JSONObject.NULL)
        .put("maxUiMs", if (frames == 0L) JSONObject.NULL else maxDurationNanos / 1_000_000.0)

    private fun PerformanceNavigationTracker.Record.json(originNs: Long) = JSONObject()
        .put("id", id).put("startReason", startReason)
        .put("completeMotionBoundaries", startReason == "motion_boundary" && outcome in setOf("settled", "cancelled"))
        .put("fromPage", fromPage).put("toPage", toPage).put("kind", kind).put("outcome", outcome)
        .put("startedOffsetMs", (startedNs - originNs) / 1_000_000.0)
        .put("endedOffsetMs", endedNs?.let { (it - originNs) / 1_000_000.0 } ?: JSONObject.NULL)
        .put("durationMs", endedNs?.let { (it - startedNs) / 1_000_000.0 } ?: JSONObject.NULL)
        .put("ui", ui.json()).put("hwui", JSONObject().put("frames", hwuiFrames)
            .put("deadlineAvailableFrames", deadlineAvailableFrames)
            .put("missedDeadlineFrames", if (deadlineAvailableFrames == 0L) JSONObject.NULL else missedDeadlineFrames)
            .put("droppedMetricCallbacks", droppedMetricCallbacks)
            .put("maxTotalMs", maxTotalNs?.let { it / 1_000_000.0 } ?: JSONObject.NULL)
            .put("maxDrawMs", maxDrawNs?.let { it / 1_000_000.0 } ?: JSONObject.NULL)
            .put("maxGpuMs", maxGpuNs?.let { it / 1_000_000.0 } ?: JSONObject.NULL))

    private fun PerformanceIntervalInsight.json() = JSONObject()
        .put("startOffsetMs", startOffsetMs).put("endOffsetMs", endOffsetMs)
        .put("windowIdAtEnd", windowIdAtEnd ?: JSONObject.NULL).put("pageAtEnd", pageAtEnd ?: JSONObject.NULL)
        .put("uiFrames", frames).put("jankyUiFrames", jankyFrames).put("cpuPercentOneCore", cpuPercentOneCore)
        .put("longUiFrames100Ms", longFrames100Ms ?: JSONObject.NULL)
        .put("hwuiDeadlineFrames", hwuiDeadlineFrames)
        .put("hwuiMissedDeadlines", if (hwuiDeadlineFrames == 0L) JSONObject.NULL else hwuiMissedDeadlines)

    @Synchronized
    private fun snapshot(): (() -> JSONObject)? = session?.let { current ->
        val environment = current.environment
        val startedWallMs = current.startedWallMs
        val startedNanoTime = current.startedNanoTime
        val elapsedMs = elapsed(current)
        val foregroundMs = current.foregroundMs + (current.foregroundSinceMs?.let { SystemClock.elapsedRealtime() - it } ?: 0)
        val stopReason = current.stopReason ?: "checkpoint_unfinished"
        val total = current.total.snapshot()
        val pages = current.pages.mapValues { it.value.snapshot() }
        val scenes = current.scenes.mapValues { it.value.snapshot() }
        val hwui = current.hwui.snapshot()
        val hwuiScenes = current.hwuiScenes.mapValues { it.value.snapshot() }
        val navigationInputs = mutableListOf<PerformanceNavigationInsight>()
        val windowReports = current.windows.values.map { captured ->
            val ui = captured.ui.snapshot()
            val hardware = captured.hwui.snapshot()
            val navigation = captured.navigation.snapshot()
            navigationInputs += navigation.map { PerformanceNavigationInsight(captured.id, it) }
            val observedMotions = captured.navigation.observedMotions
            val id = captured.id
            val kind = captured.kind
            val closed = captured.closed
            val available = captured.hwuiAvailable
            val duplicates = captured.hwuiIdentity.duplicateCallbacks
            val encodeWindow: () -> JSONObject = {
                JSONObject().put("id", id).put("kind", kind).put("closed", closed)
                    .put("hwuiAvailable", available ?: JSONObject.NULL).put("frames", ui.json()).put("hwui", hardware.json())
                    .put("uiFrameSource", if (available == true && Build.VERSION.SDK_INT >= 31) "raw_frame_metrics"
                        else if (available != null) "jankstats" else JSONObject.NULL)
                    .put("duplicateHwuiCallbacks", duplicates)
                    .put("observedNavigationMotions", observedMotions)
                    .put("navigation", JSONArray(navigation.map { it.json(startedNanoTime) }))
            }
            encodeWindow
        }
        val omittedWindows = current.omittedWindows
        val handoffs = current.handoffs.map { it.copy() }
        val samples = current.samples.copyEntries()
        val intervals = current.intervals.toList()
        val markers = current.markers.copyEntries()
        val slowFrames = current.slowFrames.copyEntries()
        val slowHwuiFrames = current.slowHwuiFrames.copyEntries()
        val navigationWork = current.navigationWork.toList()
        val screenCommits = current.screenCommits.snapshot()
        val dataEvents = current.dataEvents.toList()
        val encode: () -> JSONObject = {
            val sceneInputs = scenes.mapNotNull { (key, value) ->
                if (key == "other") null else {
                    val parts = key.split('/')
                    PerformanceSceneInput(parts[0].toInt(), parts[1], parts[2], value)
                }
            }
            val markerInputs = (0 until markers.length()).map { index ->
                val marker = markers.getJSONObject(index)
                PerformanceMarkerInput(marker.getDouble("offsetMs"), marker.getDouble("reviewFromOffsetMs"),
                    if (marker.isNull("windowId")) null else marker.getInt("windowId"))
            }
            val insights = buildPerformanceReportInsights(sceneInputs, intervals, markerInputs)
            val navigationRanking = rankPerformanceNavigation(navigationInputs)
            val sceneRanking = sceneInputs.filter { it.frames.longFrames100Ms > 0 || it.frames.jankyFrames > 0 }
                .sortedWith(compareByDescending<PerformanceSceneInput> { it.frames.longFrames100Ms }
                    .thenByDescending { it.frames.jankyFrames }.thenByDescending { it.frames.maxDurationNanos })
                .take(10)
            JSONObject().put("schema", 5).put("scope", "page_scroll_and_navigation").put("environment", environment)
                .put("startedWallMs", startedWallMs).put("startedMonotonicNs", startedNanoTime).put("elapsedMs", elapsedMs)
                .put("foregroundMs", foregroundMs)
                .put("stopReason", stopReason)
                .put("measurementNotes", PERFORMANCE_MEASUREMENT_NOTES)
                .put("analysis", JSONObject()
                    .put("pageRankingMethod", "All interactions; UI frames >=100ms descending, then janky frames, jank ratio and max UI duration")
                    .put("coverageNote", PERFORMANCE_COVERAGE_NOTE)
                    .put("pageRanking", JSONArray(insights.pages.map {
                        JSONObject().put("windowId", it.windowId).put("page", it.page)
                            .put("interactions", JSONArray(it.interactions)).put("ui", it.frames.json())
                    }))
                    .put("sceneRanking", JSONArray(sceneRanking.map {
                        JSONObject().put("windowId", it.windowId).put("page", it.page)
                            .put("interaction", it.interaction).put("ui", it.frames.json())
                    }))
                    .put("navigationRanking", JSONArray(navigationRanking.map {
                        it.record.json(startedNanoTime).put("windowId", it.windowId)
                    }))
                    .put("problemIntervals", JSONArray(insights.intervals.map { it.json() }))
                    .put("markedWindows", JSONArray(insights.markers.map {
                        JSONObject().put("markerOffsetMs", it.marker.offsetMs).put("reviewFromOffsetMs", it.marker.reviewFromMs)
                            .put("markerWindowId", it.marker.windowId ?: JSONObject.NULL)
                            .put("overlappingIntervals", JSONArray(it.intervals.map { interval -> interval.json() }))
                    }))
                    .put("summaryText", insights.summaryText + formatPerformanceNavigationSummary(navigationRanking.map { it.summaryRow() })))
                .put("frames", total.json())
                .put("scenes", JSONObject().apply { scenes.forEach { (key, value) -> put(key, value.json()) } })
                .put("windows", JSONArray(windowReports.map { it() })).put("omittedWindows", omittedWindows)
                .put("windowHandoffs", JSONArray(handoffs.map {
                    JSONObject().put("fromWindowId", it.fromWindowId ?: JSONObject.NULL).put("fromPage", it.fromPage ?: JSONObject.NULL)
                        .put("toWindowId", it.toWindowId).put("toPage", it.toPage).put("outcome", it.outcome)
                        .put("resumedOffsetMs", (it.resumedNs - startedNanoTime) / 1_000_000.0)
                        .put("resumeToFirstUiFrameMs", it.firstUiFrameNs?.let { frame -> (frame - it.resumedNs) / 1_000_000.0 } ?: JSONObject.NULL)
                }))
                .put("hwui", hwui.json())
                .put("hwuiScenes", JSONObject().apply { hwuiScenes.forEach { (key, value) -> put(key, value.json()) } })
                .put("latest100SlowHwuiFrames", slowHwuiFrames)
                .put("screenCommits", JSONArray(screenCommits.map { count ->
                    JSONObject().put("windowId", count.windowId).put("screen", count.screen)
                        .put("navigationReady", count.navigationReady).put("commits", count.commits)
                }))
                .put("latest100DataEvents", JSONArray(dataEvents.map { event ->
                    JSONObject().put("offsetMs", (event.timeNs - startedNanoTime) / 1_000_000.0)
                        .put("sourceScreen", event.screen).put("action", event.action)
                        .put("windowIdAtEvent", event.windowId).put("pageAtEvent", event.page)
                }))
                .put("latest100NavigationWork", JSONArray(navigationWork.map { work ->
                    JSONObject().put("stage", work.token.stage).put("windowIdAtStart", work.token.windowId)
                        .put("pageAtStart", work.token.page)
                        .put("isMainThread", work.token.isMainThread)
                        .put("offsetMs", (work.token.startedNs - startedNanoTime) / 1_000_000.0)
                        .put("durationMs", work.durationNs / 1_000_000.0)
                }))
                .put("pages", JSONObject().apply { pages.forEach { (key, value) -> put(key, value.json()) } })
                .put("samples", samples).put("markers", markers)
                .put("latest100SlowUiFrames", slowFrames)
        }
        encode
    }

    private fun JSONArray.copyEntries() = JSONArray().also { copy ->
        for (index in 0 until length()) copy.put(get(index))
    }

    // 写入串行化，结束操作与周期检查点不会交错覆盖；磁盘操作始终在 IO 线程。
    private val fileMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun checkpoint(context: Context) {
        fileMutex.lock()
        try {
            withContext(Dispatchers.IO) {
                val report = snapshot()?.invoke() ?: return@withContext
                PerformanceHistoryStore(reportFile(context).parentFile!!).writeCheckpoint(report)
            }
        } finally {
            fileMutex.unlock()
        }
    }

    fun reportFile(context: Context) = File(context.filesDir, "performance/latest.json")

    suspend fun readHistory(context: Context): List<PerformanceHistoryEntry> = withContext(Dispatchers.IO) {
        fileMutex.lock()
        try {
            PerformanceHistoryStore(reportFile(context).parentFile!!).read()
        } finally {
            fileMutex.unlock()
        }
    }

    /** 读取已保存报告，不重新采样、不修改检查点；旧版无摘要时显式返回未知。 */
    suspend fun readPreview(context: Context): PerformanceReportPreview = withContext(Dispatchers.IO) {
        fileMutex.lock()
        try {
            val source = reportFile(context)
            check(source.exists()) { "暂无性能报告，请先完成一次采集" }
            val report = JSONObject(android.util.AtomicFile(source).readFully().toString(Charsets.UTF_8))
            val environment = report.getJSONObject("environment")
            PerformanceReportPreview(
                version = environment.getString("version"),
                buildType = environment.getString("buildType"),
                device = "${environment.getString("manufacturer")} ${environment.getString("model")}",
                startedWallMs = report.getLong("startedWallMs"),
                elapsedMs = report.getLong("elapsedMs"),
                stopReason = report.getString("stopReason"),
                summary = savedPerformanceSummary(report),
            )
        } finally {
            fileMutex.unlock()
        }
    }

    suspend fun export(context: Context): File = withContext(Dispatchers.IO) {
        fileMutex.lock()
        try {
            val source = reportFile(context)
            check(source.exists()) { "暂无性能报告，请先开始采集" }
            val directory = File(context.cacheDir, "logs").apply { mkdirs() }
            val destination = File(directory, "bilipai-performance.json")
            val report = JSONObject(android.util.AtomicFile(source).readFully().toString(Charsets.UTF_8))
            savedPerformanceSummary(report)?.let { summary ->
                // 历史报告保留采样时的算法与口径，不套用当前 schema 的测量说明。
                report.getJSONObject("analysis").put("summaryText", summary)
            }
            destination.writeText(report.toString(2))
            destination
        } finally {
            fileMutex.unlock()
        }
    }
}
