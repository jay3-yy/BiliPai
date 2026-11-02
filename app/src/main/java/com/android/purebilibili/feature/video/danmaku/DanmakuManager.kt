// 文件路径: feature/video/danmaku/DanmakuManager.kt
package com.android.purebilibili.feature.video.danmaku

import com.android.purebilibili.danmaku.parser.*

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.lifecycle.AbandonableResource
import com.android.purebilibili.plugin.sdk.DanmakuItem as PluginDanmakuItem
import com.android.purebilibili.plugin.sdk.DanmakuPluginApi
import com.android.purebilibili.plugin.sdk.DanmakuStyle
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.json.JsonPluginManager
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.danmaku.parser.resolveBilibiliDanmakuFontScale
import com.android.purebilibili.danmaku.parser.bas.BasDanmaku
import com.android.purebilibili.danmaku.parser.bas.BasParseException
import com.android.purebilibili.danmaku.parser.bas.BasScriptParser
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_BOTTOM
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_REVERSE
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_SCROLL
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_TOP
import com.android.purebilibili.danmaku.engine.DanmakuEngine
import com.android.purebilibili.danmaku.engine.DanmakuItem
import com.android.purebilibili.danmaku.engine.DanmakuRenderConfig
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import com.android.purebilibili.danmaku.engine.DanmakuWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.ContinuationInterceptor
import kotlin.math.abs

internal fun resolveDanmakuClickUserHash(rawUserHash: String): String = rawUserHash.trim()

internal fun resolveDanmakuClickIsSelf(userHash: String, currentMid: Long): Boolean {
    if (currentMid <= 0L) return false
    return userHash.toLongOrNull() == currentMid
}

internal fun shouldApplyDanmakuLoadResult(
    expectedCid: Long,
    expectedGeneration: Long,
    currentCid: Long,
    currentGeneration: Long
): Boolean {
    return expectedCid > 0L &&
        expectedCid == currentCid &&
        expectedGeneration == currentGeneration
}

/**
 * 弹幕管理器
 * 
 * 使用 ByteDance DanmakuRenderEngine 重构
 * 
 * 负责：
 * 1. 加载和解析弹幕数据
 * 2. 与 ExoPlayer 同步弹幕播放
 * 3. 管理弹幕视图生命周期
 * 
 * 每个播放身份拥有独立会话；横竖屏只重绑 View，不共享 Player、数据和请求代际。
 */
class DanmakuManager private constructor(
    private val context: Context,
    initialScope: CoroutineScope
) {
    companion object {
        private const val TAG = "DanmakuManager"
        private const val WEB_MASK_LOOK_BEHIND_MS = 10_000L
        private const val WEB_MASK_LOOK_AHEAD_MS = 30_000L
        private const val WEB_MASK_REFRESH_GUARD_MS = 5_000L
        private const val SPECIAL_LOOK_AHEAD_MS = 3_000L
        private const val SPECIAL_REFRESH_GUARD_MS = 1_500L
        private const val SEGMENT_RETRY_INTERVAL_MS = 15_000L

        internal fun createSession(context: Context, scope: CoroutineScope): DanmakuManager {
            return DanmakuManager(context.applicationContext, scope)
        }
    }

    private var scope: CoroutineScope = createDanmakuManagerScope(initialScope)
    
    // 视图和控制器
    private var danmakuView: DanmakuRenderView? = null
    private val renderTargets = LinkedHashSet<DanmakuRenderView>()
    private val renderPauseState = DanmakuRenderPauseState<DanmakuRenderView>()
    private val isRenderingEnabled: Boolean
        get() = renderPauseState.isRenderingEnabled(config.isEnabled, danmakuView)

    /** Pause a retained target without clearing its timeline or changing the user's setting. */
    fun setRenderingPaused(view: DanmakuRenderView, paused: Boolean) {
        val changed = renderPauseState.setPaused(view, paused)
        if (!changed || danmakuView !== view) return
        if (paused) {
            clearTapSelection()
            controller?.pause()
            view.visibility = android.view.View.INVISIBLE
            isPlaying = false
            stopDriftSync()
        } else if (isRenderingEnabled) {
            view.visibility = android.view.View.VISIBLE
            softResyncDanmakuTimeline(
                positionMs = player?.currentPosition ?: 0L,
                shouldPlay = player?.isPlaying == true,
                reason = "transition_resume",
            )
            if (isPlaying) startDriftSync()
        }
    }
    private var controller: DanmakuEngine? = null
    /** UP 主身份（owner.mid 的 crc32 hex）；controller 建立前先暂存，attachView 时补绑。 */
    private var pendingUpOwnerUserHash: String? = null
    /** 供弹幕池列表等组合层读取当前 UP 主标识。 */
    var upOwnerUserHash: String? by mutableStateOf<String?>(null)
        private set
    /** 最近一次成功 replaceWindow 的 controller；用于判断新 view 是否需要补时间线。 */
    private var timelineSyncedController: DanmakuEngine? = null
    /** load 完成时 controller 尚为 null，等 attachView 再补。 */
    private var pendingTimelineResync: Boolean = false
    private var isSeekScrubbing = false
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var playerAttachmentCount: Int = 0
    private var loadJob: Job? = null
    private var loadGeneration: Long = 0L
    private var windowLoadJob: Job? = null
    private var windowGeneration: Long = 0L
    private var missingSegmentRetryAtMs: Long = 0L
    private var maskLoadJob: Job? = null
    private var maskFetchJob: Job? = null
    private var configRebuildJob: Job? = null
    private var syncJob: Job? = null  // ⚙️ [漂移修复] 定期检测漂移
    
    // 弹幕状态
    private var isPlaying = false
    private var isLoading = false
    private var danmakuClickListener: ((String, Long, String, Boolean, DanmakuTapAnchor?) -> Unit)? = null
    
    // 缓存解析后的弹幕数据（横竖屏切换时复用）
    private var cachedDanmakuList: List<DanmakuItem>? = null
    private var sourceDanmakuList: List<DanmakuItem>? = null
    private var sourceAdvancedDanmakuList: List<AdvancedDanmakuData>? = null
    private var sourceBasDanmakuList: List<BasDanmaku>? = null
    private var sourceCommandDanmakuList: List<CommandDanmakuItem> = emptyList()
    private var mergedAdvancedDanmakuList: List<AdvancedDanmakuData> = emptyList()
    private var rawDanmakuList: List<DanmakuItem>? = null
    private var hotDanmakuIds: Set<Long> = emptySet()
    private var hotDanmakuTexts: Set<String> = emptySet()
    // [新增] 高级弹幕数据流
    private val _advancedDanmakuFlow = kotlinx.coroutines.flow.MutableStateFlow<List<AdvancedDanmakuData>>(emptyList())
    val advancedDanmakuFlow: kotlinx.coroutines.flow.StateFlow<List<AdvancedDanmakuData>> = _advancedDanmakuFlow.asStateFlow()
    private val _basDanmakuFlow = MutableStateFlow<List<BasDanmaku>>(emptyList())
    val basDanmakuFlow: kotlinx.coroutines.flow.StateFlow<List<BasDanmaku>> = _basDanmakuFlow.asStateFlow()
    private val _commandDanmakuFlow = kotlinx.coroutines.flow.MutableStateFlow<List<CommandDanmakuItem>>(emptyList())
    val commandDanmakuFlow: kotlinx.coroutines.flow.StateFlow<List<CommandDanmakuItem>> = _commandDanmakuFlow.asStateFlow()
    private var cachedCid: Long = 0L
    private var cachedAid: Long = 0L
    private var cachedBvid: String = ""
    private var cachedDurationMs: Long = 0L
    private var totalSegmentCount: Int = 1
    private val parsedSegments = LinkedHashMap<Int, ParsedDanmaku>()
    private var activeSegmentIndices: List<Int> = emptyList()
    private var pendingSegmentIndices: List<Int> = emptyList()
    private var specialDanmaku: ParsedDanmaku = ParsedDanmaku(emptyList(), emptyList())
    private var specialIndexJob: Job? = null
    private var specialWindow: SpecialDanmakuWindow? = null
    private var specialWindowLoadJob: Job? = null
    private var specialWindowGeneration = 0L
    private var specialWindowStartMs = Long.MIN_VALUE
    private var specialWindowEndMs = Long.MIN_VALUE
    private var specialLoadingPausedForBackground = false
    private val specialWindowMutex = Mutex()
    private val cacheUpdateMutex = Mutex()
    private var localSegmentPaths: List<String> = emptyList()
    private var isLocalSegmentSession: Boolean = false
    private var webMaskBytes: ByteArray? = null
    private var webMaskFps: Int = 0
    private var webMaskWindowStartMs: Long = Long.MIN_VALUE
    private var webMaskWindowEndMs: Long = Long.MIN_VALUE
    private var sessionIdentity: DanmakuSessionIdentity? = null
    private var lastExplicitSeekPositionMs: Long? = null
    private var lastExplicitSeekElapsedRealtimeMs: Long? = null
    private var lastExplicitSeekStartedPlayback: Boolean? = null
    
    //  [新增] 记录原始弹幕滚动时间（用于倍速同步）
    private var originalMoveTime: Long = 8000L  // 默认 8 秒
    private var originalTopShowTimeMin: Long = 4000L
    private var originalTopShowTimeMax: Long = 4000L
    private var originalBottomShowTimeMin: Long = 4000L
    private var originalBottomShowTimeMax: Long = 4000L
    private var baseRenderConfig: DanmakuRenderConfig = DanmakuRenderConfig()
    private var currentVideoSpeed: Float = 1.0f
    private var pluginObserverJob: Job? = null
    private var lastDanmakuPluginUpdateToken: Long = 0L
    private var wasBufferingWhilePlaying = false
    
    // 配置
    val config = DanmakuConfig()
    private var blockedRuleMatchers: List<DanmakuBlockRuleMatcher> = emptyList()

    init {
        startDanmakuPluginObserver()
    }

    internal fun bindSessionIdentity(identity: DanmakuSessionIdentity) {
        sessionIdentity = identity
    }

    /**
     * 绑定视频 UP 主 mid，用于给 UP 主发送的弹幕渲染 "UP" 徽章。
     * 与 B 站协议一致：DanmakuElem.midHash 为 mid 字符串的 crc32 hex。
     */
    fun bindUpOwnerMid(mid: Long) {
        pendingUpOwnerUserHash = if (mid > 0L) {
            DanmakuCloudRuleSyncPolicy.crc32Hex(mid.toString())
        } else {
            null
        }
        upOwnerUserHash = pendingUpOwnerUserHash
        controller?.upOwnerUserHash = pendingUpOwnerUserHash
    }
    
    // 便捷属性访问器
    var isEnabled: Boolean
        get() = config.isEnabled
        set(value) {
            config.isEnabled = value
            if (value) show() else hide()
        }

    /**
     * 获取当前视频已加载的弹幕列表（按时间顺序排序）。
     */
    fun getLoadedDanmakuList(): List<DanmakuItem> {
        return sourceDanmakuList ?: cachedDanmakuList ?: emptyList()
    }
    
    /** 合并前的已过滤快照，交给悬浮条按时间和宽度选择，不复制弹幕对象。 */
    fun getHotDanmakuList(expectedCid: Long): List<DanmakuItem> {
        if (cachedCid != expectedCid || cachedDanmakuList == null) return emptyList()
        return rawDanmakuList.orEmpty()
    }

    var opacity: Float
        get() = config.opacity
        set(value) {
            config.opacity = value
            applyConfigToController("opacity")
        }
    
    var isFullscreenSurface: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyConfigToController("fullscreen")
        }

    var fontScale: Float
        get() = config.fontScale
        set(value) {
            config.fontScale = value
            applyConfigToController("fontScale")
        }

    var fontWeight: Int
        get() = config.fontWeight
        set(value) {
            config.fontWeight = value
            applyConfigToController("fontWeight")
        }
    
    var speedFactor: Float
        get() = config.speedFactor
        set(value) {
            config.speedFactor = value
            applyConfigToController("speedFactor")
        }

    var scrollDurationSeconds: Float
        get() = config.scrollDurationSeconds
        set(value) {
            config.scrollDurationSeconds = value
            applyConfigToController("scrollDuration")
        }
    
    var displayArea: Float
        get() = config.displayAreaRatio
        set(value) {
            config.displayAreaRatio = value
            applyConfigToController("displayArea")
        }

    var strokeWidth: Float
        get() = config.strokeWidth
        set(value) {
            config.strokeWidth = value
            applyConfigToController("strokeWidth")
        }

    var lineHeight: Float
        get() = config.lineHeight
        set(value) {
            config.lineHeight = value
            applyConfigToController("lineHeight")
        }

    var staticDurationSeconds: Float
        get() = config.staticDurationSeconds
        set(value) {
            config.staticDurationSeconds = value
            applyConfigToController("staticDuration")
        }

    var scrollFixedVelocity: Boolean
        get() = config.scrollFixedVelocity
        set(value) {
            config.scrollFixedVelocity = value
            applyConfigToController("scrollFixedVelocity")
        }

    var staticDanmakuToScroll: Boolean
        get() = config.staticDanmakuToScroll
        set(value) {
            config.staticDanmakuToScroll = value
            applyConfigToController("staticDanmakuToScroll")
        }

    var massiveMode: Boolean
        get() = config.massiveMode
        set(value) {
            config.massiveMode = value
            applyConfigToController("massiveMode")
        }

    var allowScrollDanmaku: Boolean
        get() = config.allowScroll
        set(value) {
            config.allowScroll = value
            applyConfigToController("filter_changed")
        }

    var allowTopDanmaku: Boolean
        get() = config.allowTop
        set(value) {
            config.allowTop = value
            applyConfigToController("filter_changed")
        }

    var allowBottomDanmaku: Boolean
        get() = config.allowBottom
        set(value) {
            config.allowBottom = value
            applyConfigToController("filter_changed")
        }

    var allowColorfulDanmaku: Boolean
        get() = config.allowColorful
        set(value) {
            config.allowColorful = value
            applyConfigToController("filter_changed")
        }

    var allowSpecialDanmaku: Boolean
        get() = config.allowSpecial
        set(value) {
            config.allowSpecial = value
            applyConfigToController("filter_changed")
        }

    var weightFilterLevel: Int
        get() = config.weightFilterLevel
        set(value) {
            config.weightFilterLevel = value.coerceIn(0, 11)
            applyConfigToController("filter_changed")
        }

    private fun startDanmakuPluginObserver() {
        pluginObserverJob?.cancel()
        pluginObserverJob = scope.launch {
            PluginManager.danmakuPluginUpdateToken.collect { token ->
                if (token <= 0L || token == lastDanmakuPluginUpdateToken) return@collect
                lastDanmakuPluginUpdateToken = token

                if (isLoading || sourceDanmakuList == null) return@collect

                val expectedCid = cachedCid
                val expectedGeneration = loadGeneration
                val expectedWindowGeneration = windowGeneration

                val rebuild = withContext(Dispatchers.Default) {
                    buildDanmakuCacheFromSource(
                        expectedCid = expectedCid,
                        expectedGeneration = expectedGeneration,
                        expectedWindowGeneration = expectedWindowGeneration
                    )
                }
                if (rebuild == null) return@collect

                withContext(Dispatchers.Main.immediate) {
                    if (!isCurrentSegmentWindowRequest(
                            expectedCid, expectedGeneration, expectedWindowGeneration
                        ) || !commitDanmakuCacheRebuild(rebuild, "plugin_update")
                    ) {
                        return@withContext
                    }
                }
            }
        }
    }

    private data class DanmakuCacheRebuild(
        val sourceStandard: List<DanmakuItem>?,
        val sourceAdvanced: List<AdvancedDanmakuData>?,
        val sourceBas: List<BasDanmaku>?,
        val rawStandard: List<DanmakuItem>,
        val visibleStandard: List<DanmakuItem>,
        val visibleAdvanced: List<AdvancedDanmakuData>,
        val visibleBas: List<BasDanmaku>,
        val mergedAdvanced: List<AdvancedDanmakuData>
    )

    private fun buildDanmakuCacheFromSource(
        expectedCid: Long? = null,
        expectedGeneration: Long? = null,
        expectedWindowGeneration: Long? = null,
        sourceStandardOverride: List<DanmakuItem>? = null,
        sourceAdvancedOverride: List<AdvancedDanmakuData>? = null,
        sourceBasOverride: List<BasDanmaku>? = null
    ): DanmakuCacheRebuild? {
        fun isCurrentLoadRequest(): Boolean {
            val loadMatches = if (expectedCid == null || expectedGeneration == null) true else shouldApplyDanmakuLoadResult(
                expectedCid = expectedCid,
                expectedGeneration = expectedGeneration,
                currentCid = cachedCid,
                currentGeneration = loadGeneration
            )
            return loadMatches &&
                (expectedWindowGeneration == null || expectedWindowGeneration == windowGeneration)
        }

        if (!isCurrentLoadRequest()) return null
        val sourceStandard = sourceStandardOverride ?: sourceDanmakuList ?: return null
        val sourceAdvanced = sourceAdvancedOverride ?: sourceAdvancedDanmakuList ?: emptyList()
        val sourceBas = sourceBasOverride ?: sourceBasDanmakuList.orEmpty()
        val visibleBas = filterBasDanmakuForRender(sourceBas)

        val (pluginFilteredStandardList, pluginFilteredAdvancedList) =
            applyDanmakuPluginPipeline(sourceStandard, sourceAdvanced)
        val (filteredStandardList, filteredAdvancedList) =
            applyDanmakuTypeFilters(pluginFilteredStandardList, pluginFilteredAdvancedList)
        val projectedStandardList = projectStandardDanmakuForRender(filteredStandardList)

        var retainedMergedAdvanced = emptyList<AdvancedDanmakuData>()
        val (rebuiltStandard, rebuiltAdvanced) = if (config.mergeDuplicates) {
            val (mergedStandard, mergedAdvanced) = DanmakuMerger.merge(
                list = projectedStandardList,
                intervalMs = config.duplicateMergeWindowMs.toLong(),
                countThreshold = config.duplicateMergeCountThreshold
            )
            val settings = currentTypeFilterSettings()
            val visibleMergedAdvanced = mergedAdvanced.filter { merged ->
                shouldDisplayMergedAdvancedDanmaku(
                    content = merged.content,
                    color = merged.color,
                    settings = settings,
                    blockedMatchers = blockedRuleMatchers
                )
            }
            retainedMergedAdvanced = visibleMergedAdvanced
            mergedStandard to (filteredAdvancedList + visibleMergedAdvanced)
        } else {
            projectedStandardList to filteredAdvancedList
        }

        if (!isCurrentLoadRequest()) return null
        return DanmakuCacheRebuild(
            sourceStandard = sourceStandard.takeIf { sourceStandardOverride != null },
            sourceAdvanced = sourceAdvanced.takeIf { sourceAdvancedOverride != null },
            sourceBas = sourceBas.takeIf { sourceBasOverride != null },
            rawStandard = projectedStandardList,
            visibleStandard = rebuiltStandard,
            visibleAdvanced = rebuiltAdvanced,
            visibleBas = visibleBas,
            mergedAdvanced = retainedMergedAdvanced
        )
    }

    /** Commit only from the owning Session dispatcher after its generation has been revalidated. */
    private fun commitDanmakuCacheRebuild(rebuild: DanmakuCacheRebuild, reason: String): Boolean {
        rebuild.sourceStandard?.let { sourceDanmakuList = it }
        rebuild.sourceAdvanced?.let { sourceAdvancedDanmakuList = it }
        rebuild.sourceBas?.let { sourceBasDanmakuList = it }
        rawDanmakuList = rebuild.rawStandard
        cachedDanmakuList = rebuild.visibleStandard
        _advancedDanmakuFlow.value = rebuild.visibleAdvanced
        _basDanmakuFlow.value = rebuild.visibleBas
        mergedAdvancedDanmakuList = rebuild.mergedAdvanced
        requestSpecialDanmakuWindow(player?.currentPosition ?: 0L, "data_ready")
        if (rebuild.visibleStandard.isEmpty() && rebuild.visibleAdvanced.isEmpty() && rebuild.visibleBas.isEmpty()) {
            Log.w(TAG, " Danmaku cache rebuilt ($reason): no visible items after filtering")
            // An empty filtered window is a valid result and must replace the old timeline.
            return true
        }
        Log.w(
            TAG,
            " Danmaku cache rebuilt ($reason): standard=${rebuild.visibleStandard.size}, " +
                "advanced=${rebuild.visibleAdvanced.size}, BAS=${rebuild.visibleBas.size}"
        )
        return true
    }

    private fun projectStandardDanmakuForRender(
        standardDanmakuList: List<DanmakuItem>
    ): List<DanmakuItem> {
        if (standardDanmakuList.isEmpty()) return standardDanmakuList
        return standardDanmakuList.map { data ->
            val textData = data
            val projectedLayerType = resolveDanmakuRenderLayerType(
                type = mapLayerTypeToDanmakuType(textData.layerType),
                staticDanmakuToScroll = config.staticDanmakuToScroll
            )
            if (projectedLayerType == textData.layerType) {
                data
            } else {
                textData.copyForPluginPipeline().also { copied ->
                    copied.layerType = projectedLayerType
                }
            }
        }
    }

    private fun applyCachedDanmakuToController(reason: String) {
        val currentPos = player?.currentPosition ?: 0L
        val list = cachedDanmakuList ?: emptyList()
        if (list.isEmpty() && _advancedDanmakuFlow.value.isEmpty() && _basDanmakuFlow.value.isEmpty()) {
            controller?.clear()
            isPlaying = false
            Log.w(TAG, " applyCachedDanmakuToController($reason): cleared (empty list)")
            return
        }

        resyncDanmakuTimeline(
            list = list,
            positionMs = currentPos,
            shouldPlay = player?.isPlaying == true,
            invalidateView = true,
            reason = "applyCached:$reason"
        )
        Log.w(TAG, " applyCachedDanmakuToController($reason): size=${list.size}, pos=${currentPos}ms")
    }

    /**
     * 当前绑定的 controller 若尚未装上 [cachedDanmakuList]，立即补一次时间线。
     * 用于 attachView / 布局完成等「view 后于 load」路径。
     */
    private fun reapplyCachedDanmakuToCurrentControllerIfNeeded(
        previousController: DanmakuEngine?,
        reason: String,
    ) {
        val list = cachedDanmakuList ?: return
        val current = controller ?: return
        if (
            !shouldReapplyDanmakuTimelineOnAttach(
                hasCachedList = true,
                pendingTimelineResync = pendingTimelineResync,
                previousControllerSameAsCurrent = previousController === current,
                timelineAlreadySyncedToCurrent = timelineSyncedController === current,
            )
        ) {
            return
        }
        if (isRenderingEnabled) {
            danmakuView?.visibility = android.view.View.VISIBLE
        }
        resyncDanmakuTimeline(
            list = list,
            positionMs = player?.currentPosition ?: 0L,
            shouldPlay = shouldStartDanmakuOnDataReady(
                isPlaying = player?.isPlaying == true,
                playWhenReady = player?.playWhenReady == true
            ),
            invalidateView = true,
            reason = reason,
        )
    }

    private fun resyncDanmakuTimeline(
        list: List<DanmakuItem>,
        positionMs: Long,
        shouldPlay: Boolean,
        invalidateView: Boolean = false,
        reason: String
    ) {
        if (isSeekScrubbing) {
            pendingTimelineResync = list.isNotEmpty()
            return
        }
        val ctrl = controller ?: run {
            // load 完成早于 view 绑定时标记 pending，等 attachView 再补。
            pendingTimelineResync = list.isNotEmpty()
            Log.w(TAG, " Resync skipped ($reason): controller=null, pending=$pendingTimelineResync")
            return
        }
        applyPlaybackSpeedToController(ctrl)
        executeExplicitDanmakuResync(
            pause = { ctrl.pause() },
            clear = { ctrl.clear() },
            setData = {
                ctrl.replaceWindow(
                    window = DanmakuWindow(
                        anchorSegment = segmentIndexForPosition(positionMs),
                        segmentIndices = activeSegmentIndices,
                        items = list
                    ),
                    currentPositionMs = 0L
                )
            },
            start = { if (isRenderingEnabled) ctrl.start(positionMs) }
        )
        if (invalidateView) {
            ctrl.invalidate()
        }
        if (isRenderingEnabled) {
            // A new episode can finish loading after its enable/view effects already ran.
            // Restore visibility at data commit time so the user never has to toggle again.
            danmakuView?.visibility = android.view.View.VISIBLE
        }
        if (shouldPlay && isRenderingEnabled) {
            isPlaying = true
            if (syncJob?.isActive != true) startDriftSync()
        } else {
            ctrl.pause()
            isPlaying = false
            stopDriftSync()
        }
        timelineSyncedController = ctrl
        pendingTimelineResync = false
        Log.w(TAG, " Resynced danmaku timeline ($reason) at ${positionMs}ms, play=$shouldPlay")
    }

    private fun softResyncDanmakuTimeline(
        positionMs: Long,
        shouldPlay: Boolean,
        invalidateView: Boolean = true,
        reason: String
    ) {
        if (isSeekScrubbing) return
        val ctrl = controller ?: return
        if (timelineSyncedController !== ctrl || pendingTimelineResync) {
            cachedDanmakuList?.let { list ->
                resyncDanmakuTimeline(
                    list = list,
                    positionMs = positionMs,
                    shouldPlay = shouldPlay,
                    invalidateView = invalidateView,
                    reason = "$reason:restore_timeline"
                )
            }
            return
        }
        val safePositionMs = positionMs.coerceAtLeast(0L)
        applyPlaybackSpeedToController(ctrl)
        ctrl.synchronizeTo(safePositionMs)
        if (shouldPlay && isRenderingEnabled) {
            ctrl.start(safePositionMs)
            isPlaying = true
        } else {
            ctrl.pause()
            isPlaying = false
        }
        if (invalidateView) {
            ctrl.invalidate()
        }
        Log.w(TAG, " Soft-resynced danmaku timeline ($reason) at ${safePositionMs}ms, play=$shouldPlay")
    }

    private fun markExplicitSeekResync(positionMs: Long, startedPlayback: Boolean) {
        lastExplicitSeekPositionMs = positionMs
        lastExplicitSeekElapsedRealtimeMs = SystemClock.elapsedRealtime()
        lastExplicitSeekStartedPlayback = startedPlayback
    }

    private fun clearExplicitSeekResyncMarker() {
        lastExplicitSeekPositionMs = null
        lastExplicitSeekElapsedRealtimeMs = null
        lastExplicitSeekStartedPlayback = null
    }

    private fun shouldSuppressFollowupHardResync(positionMs: Long): Boolean {
        return shouldSuppressFollowupDanmakuHardResync(
            positionMs = positionMs,
            explicitSeekPositionMs = lastExplicitSeekPositionMs,
            explicitSeekStartedPlayback = lastExplicitSeekStartedPlayback ?: true,
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            explicitSeekElapsedRealtimeMs = lastExplicitSeekElapsedRealtimeMs
        )
    }

    private fun DanmakuItem.copyForPluginPipeline(): DanmakuItem = copy()

    private fun filterBasDanmakuForRender(items: List<BasDanmaku>): List<BasDanmaku> {
        if (items.isEmpty()) return items
        val settings = currentTypeFilterSettings()
        if (!settings.allowSpecial) return emptyList()
        val nativePlugins = PluginManager.getEnabledDanmakuPlugins()
        val useJsonRules = JsonPluginManager.plugins.value.any { it.enabled && it.plugin.type == "danmaku" }
        val hasPlugins = nativePlugins.isNotEmpty() || useJsonRules
        if (!hasPlugins && settings.allowColorful && blockedRuleMatchers.isEmpty() && config.weightFilterLevel <= 0) {
            return items
        }
        val result = ArrayList<BasDanmaku>(items.size)
        for (item in items) {
            var visible = item
            if (hasPlugins) {
                // Mode 9 plugins receive the script itself; edited scripts must remain valid BAS.
                val sourceItem = PluginDanmakuItem(
                    id = item.id,
                    content = item.source,
                    timeMs = item.startTimeMs,
                    type = 9,
                    color = item.color and 0x00FFFFFF,
                    userId = item.userHash
                )
                val filtered = runDanmakuFilters(sourceItem, nativePlugins, useJsonRules) ?: continue
                val program = if (filtered.content == item.source) item.program else try {
                    BasScriptParser.parse(filtered.content)
                } catch (error: BasParseException) {
                    Log.w(TAG, "Plugin produced invalid BAS ${item.id}: ${error.message}")
                    continue
                }
                val style = collectDanmakuStyle(filtered, nativePlugins, useJsonRules)
                val changedColor = filtered.color.takeIf { it != sourceItem.color }
                val color = filtered.color and 0x00FFFFFF
                val colorOverride = style?.textColor?.and(0x00FFFFFF) ?: changedColor
                val scale = style?.scale ?: item.fontScale
                if (filtered.content != item.source || filtered.timeMs != item.startTimeMs ||
                    color != item.color || colorOverride != item.colorOverride || scale != item.fontScale
                ) {
                    visible = item.copy(
                        source = filtered.content,
                        program = program,
                        startTimeMs = filtered.timeMs,
                        color = color,
                        colorOverride = colorOverride,
                        fontScale = scale
                    )
                }
            }
            if (shouldDisplayBasDanmaku(visible, settings, blockedRuleMatchers, config.weightFilterLevel)) {
                result.add(visible)
            }
        }
        return result
    }

    private fun applyDanmakuPluginPipeline(
        standardDanmakuList: List<DanmakuItem>,
        advancedDanmakuList: List<AdvancedDanmakuData>
    ): Pair<List<DanmakuItem>, List<AdvancedDanmakuData>> {
        val nativePlugins = PluginManager.getEnabledDanmakuPlugins()
        val useJsonRules = JsonPluginManager.plugins.value.any { it.enabled && it.plugin.type == "danmaku" }
        if (nativePlugins.isEmpty() && !useJsonRules) {
            return Pair(standardDanmakuList, advancedDanmakuList)
        }

        var filteredStandardCount = 0
        val filteredStandard = ArrayList<DanmakuItem>(standardDanmakuList.size)
        standardDanmakuList.forEach { data ->
            val textData = data.copyForPluginPipeline()

            val sourceItem = textData.toPluginItem()
            val filteredItem = runDanmakuFilters(sourceItem, nativePlugins, useJsonRules)
            if (filteredItem == null) {
                filteredStandardCount++
                return@forEach
            }

            val style = collectDanmakuStyle(filteredItem, nativePlugins, useJsonRules)
            textData.applyPluginResult(filteredItem, style)
            filteredStandard.add(textData)
        }

        var filteredAdvancedCount = 0
        val filteredAdvanced = ArrayList<AdvancedDanmakuData>(advancedDanmakuList.size)
        advancedDanmakuList.forEach { data ->
            val sourceItem = PluginDanmakuItem(
                id = parseAdvancedDanmakuId(data.id),
                content = data.content,
                timeMs = data.startTimeMs,
                type = 7,
                color = data.color and 0x00FFFFFF,
                userId = ""
            )

            val filteredItem = runDanmakuFilters(sourceItem, nativePlugins, useJsonRules)
            if (filteredItem == null) {
                filteredAdvancedCount++
                return@forEach
            }

            val style = collectDanmakuStyle(filteredItem, nativePlugins, useJsonRules)
            var updated = data.copy(
                content = filteredItem.content,
                startTimeMs = filteredItem.timeMs,
                color = filteredItem.color and 0x00FFFFFF
            )
            style?.textColor?.let { color ->
                updated = updated.copy(color = color and 0x00FFFFFF)
            }
            if (style != null && abs(style.scale - 1.0f) > 0.01f) {
                updated = updated.copy(
                    fontSize = (updated.fontSize * style.scale).coerceIn(8f, 120f)
                )
            }
            filteredAdvanced.add(updated)
        }

        if (filteredStandardCount > 0 || filteredAdvancedCount > 0) {
            Log.w(
                TAG,
                " Danmaku plugin filter applied: standard -$filteredStandardCount, advanced -$filteredAdvancedCount"
            )
        }

        return Pair(filteredStandard, filteredAdvanced)
    }

    private fun currentTypeFilterSettings(): DanmakuTypeFilterSettings {
        return DanmakuTypeFilterSettings(
            allowScroll = config.allowScroll,
            allowTop = config.allowTop,
            allowBottom = config.allowBottom,
            allowColorful = config.allowColorful,
            allowSpecial = config.allowSpecial
        )
    }

    private fun applyDanmakuTypeFilters(
        standardDanmakuList: List<DanmakuItem>,
        advancedDanmakuList: List<AdvancedDanmakuData>
    ): Pair<List<DanmakuItem>, List<AdvancedDanmakuData>> {
        val settings = currentTypeFilterSettings()
        if (
            settings.allowScroll &&
            settings.allowTop &&
            settings.allowBottom &&
            settings.allowColorful &&
            settings.allowSpecial &&
            config.weightFilterLevel <= 0 &&
            blockedRuleMatchers.isEmpty()
        ) {
            return Pair(standardDanmakuList, advancedDanmakuList)
        }

        var filteredStandardCount = 0
        var blockedByKeywordStandardCount = 0
        val filteredStandard = standardDanmakuList.filter { data ->
            val textData = data
            val danmakuType = mapLayerTypeToDanmakuType(textData.layerType)
            val color = textData.textColor ?: 0x00FFFFFF
            val typeVisible = shouldDisplayStandardDanmaku(
                danmakuType = danmakuType,
                color = color,
                settings = settings,
                isVipGradualColor = textData.isVipGradualColor
            )
            if (!typeVisible) {
                filteredStandardCount++
                return@filter false
            }
            if (config.weightFilterLevel > 0 && !textData.isSelf && textData.weight < config.weightFilterLevel) {
                filteredStandardCount++
                return@filter false
            }
            val content = textData.text.orEmpty()
            val blockedByKeyword = shouldBlockDanmakuByMatchers(
                content = content,
                matchers = blockedRuleMatchers,
                userHash = textData.userHash
            )
            if (blockedByKeyword) {
                blockedByKeywordStandardCount++
            }
            !blockedByKeyword
        }

        var filteredAdvancedCount = 0
        var blockedByKeywordAdvancedCount = 0
        val filteredAdvanced = advancedDanmakuList.filter { data ->
            val typeVisible = shouldDisplayAdvancedDanmaku(
                color = data.color,
                settings = settings
            )
            if (!typeVisible) {
                filteredAdvancedCount++
                return@filter false
            }
            val blockedByKeyword = shouldBlockDanmakuByMatchers(
                content = data.content,
                matchers = blockedRuleMatchers
            )
            if (blockedByKeyword) {
                blockedByKeywordAdvancedCount++
            }
            !blockedByKeyword
        }

        if (
            filteredStandardCount > 0 ||
            filteredAdvancedCount > 0 ||
            blockedByKeywordStandardCount > 0 ||
            blockedByKeywordAdvancedCount > 0
        ) {
            Log.w(
                TAG,
                " Danmaku filter applied: type standard -$filteredStandardCount, " +
                    "type advanced -$filteredAdvancedCount, " +
                    "keyword standard -$blockedByKeywordStandardCount, " +
                    "keyword advanced -$blockedByKeywordAdvancedCount"
            )
        }
        return Pair(filteredStandard, filteredAdvanced)
    }

    private fun DanmakuItem.toPluginItem(): PluginDanmakuItem {
        val currentColor = textColor ?: 0xFFFFFF
        return PluginDanmakuItem(
            id = danmakuId,
            content = text.orEmpty(),
            timeMs = showAtTime,
            type = mapLayerTypeToDanmakuType(layerType),
            color = currentColor and 0x00FFFFFF,
            userId = userHash
        )
    }

    private fun DanmakuItem.applyPluginResult(item: PluginDanmakuItem, style: DanmakuStyle?) {
        text = item.content
        showAtTime = item.timeMs
        layerType = mapDanmakuTypeToLayerType(item.type)
        textColor = (item.color and 0x00FFFFFF) or 0xFF000000.toInt()

        style?.textColor?.let { color -> textColor = color }
        if (style != null && abs(style.scale - 1.0f) > 0.01f) {
            val explicitSize = textSize
            if (explicitSize != null) {
                textSize = (explicitSize * style.scale).coerceIn(12f, 192f)
            } else {
                textSizeScale = (textSizeScale * style.scale).coerceIn(0.3f, 4f)
            }
        }
        typeface = if (style?.bold == true) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun runDanmakuFilters(
        item: PluginDanmakuItem,
        nativePlugins: List<DanmakuPluginApi>,
        useJsonRules: Boolean
    ): PluginDanmakuItem? {
        var current = item
        nativePlugins.forEach { plugin ->
            val filtered = try {
                plugin.filterDanmaku(current)
            } catch (e: Exception) {
                Log.e(TAG, " Danmaku plugin filter failed: ${plugin.name}", e)
                current
            }
            if (filtered == null) return null
            current = filtered
        }

        if (useJsonRules) {
            val shouldShow = try {
                JsonPluginManager.shouldShowDanmaku(current)
            } catch (e: Exception) {
                Log.e(TAG, " JSON danmaku rule filter failed", e)
                true
            }
            if (!shouldShow) return null
        }

        return current
    }

    private fun collectDanmakuStyle(
        item: PluginDanmakuItem,
        nativePlugins: List<DanmakuPluginApi>,
        useJsonRules: Boolean
    ): DanmakuStyle? {
        var style: DanmakuStyle? = null
        nativePlugins.forEach { plugin ->
            val next = try {
                plugin.styleDanmaku(item)
            } catch (e: Exception) {
                Log.e(TAG, " Danmaku plugin style failed: ${plugin.name}", e)
                null
            }
            style = mergeDanmakuStyle(style, next)
        }

        if (useJsonRules) {
            val next = try {
                JsonPluginManager.getDanmakuStyle(item)
            } catch (e: Exception) {
                Log.e(TAG, " JSON danmaku rule style failed", e)
                null
            }
            style = mergeDanmakuStyle(style, next)
        }

        return style
    }

    private fun mergeDanmakuStyle(base: DanmakuStyle?, incoming: DanmakuStyle?): DanmakuStyle? {
        if (base == null) return incoming
        if (incoming == null) return base
        return DanmakuStyle(
            textColor = incoming.textColor ?: base.textColor,
            borderColor = incoming.borderColor ?: base.borderColor,
            backgroundColor = incoming.backgroundColor ?: base.backgroundColor,
            bold = base.bold || incoming.bold,
            scale = if (abs(incoming.scale - 1.0f) > 0.01f) incoming.scale else base.scale
        )
    }

    private fun mapLayerTypeToDanmakuType(layerType: Int): Int = when (layerType) {
        DANMAKU_LAYER_BOTTOM -> 4
        DANMAKU_LAYER_TOP -> 5
        DANMAKU_LAYER_REVERSE -> 6
        else -> 1
    }

    private fun mapDanmakuTypeToLayerType(type: Int): Int {
        return resolveDanmakuRenderLayerType(
            type = type,
            staticDanmakuToScroll = config.staticDanmakuToScroll
        )
    }

    private fun parseAdvancedDanmakuId(rawId: String): Long {
        return rawId.toLongOrNull()
            ?: rawId.filter { it.isDigit() }.toLongOrNull()
            ?: 0L
    }


    /**
     *  批量更新弹幕设置（实时生效）
     */
    fun updateSettings(settings: DanmakuSettings) {
        updateSettings(
            opacity = settings.opacity,
            fontScale = settings.fontScale,
            fontWeight = settings.fontWeight,
            speed = settings.speed,
            scrollDurationSeconds = settings.scrollDurationSeconds,
            displayArea = settings.displayArea,
            strokeWidth = settings.strokeWidth,
            lineHeight = settings.lineHeight,
            staticDurationSeconds = settings.staticDurationSeconds,
            scrollFixedVelocity = settings.scrollFixedVelocity,
            staticDanmakuToScroll = settings.staticDanmakuToScroll,
            massiveMode = settings.massiveMode,
            mergeDuplicates = settings.mergeDuplicates,
            duplicateMergeWindowMs = settings.duplicateMergeWindowMs,
            duplicateMergeCountThreshold = settings.duplicateMergeCountThreshold,
            allowScroll = settings.allowScroll,
            allowTop = settings.allowTop,
            allowBottom = settings.allowBottom,
            allowColorful = settings.allowColorful,
            allowSpecial = settings.allowSpecial,
            blockedRules = settings.blockRules,
            smartOcclusion = settings.smartOcclusion,
            weightFilterLevel = settings.weightFilterLevel
        )
    }

    fun updateSettings(
        opacity: Float = this.opacity,
        fontScale: Float = this.fontScale,
        fontWeight: Int = this.fontWeight,
        speed: Float = this.speedFactor,
        scrollDurationSeconds: Float = this.scrollDurationSeconds,
        displayArea: Float = this.displayArea,
        strokeWidth: Float = this.strokeWidth,
        lineHeight: Float = this.lineHeight,
        staticDurationSeconds: Float = this.staticDurationSeconds,
        scrollFixedVelocity: Boolean = this.scrollFixedVelocity,
        staticDanmakuToScroll: Boolean = this.staticDanmakuToScroll,
        massiveMode: Boolean = this.massiveMode,
        mergeDuplicates: Boolean = config.mergeDuplicates,
        duplicateMergeWindowMs: Int = config.duplicateMergeWindowMs,
        duplicateMergeCountThreshold: Int = config.duplicateMergeCountThreshold,
        allowScroll: Boolean = config.allowScroll,
        allowTop: Boolean = config.allowTop,
        allowBottom: Boolean = config.allowBottom,
        allowColorful: Boolean = config.allowColorful,
        allowSpecial: Boolean = config.allowSpecial,
        blockedRules: List<String> = config.blockedRules,
        smartOcclusion: Boolean = config.smartOcclusionEnabled,
        weightFilterLevel: Int = config.weightFilterLevel
    ) {
        val mergeChanged = config.mergeDuplicates != mergeDuplicates ||
            config.duplicateMergeWindowMs != duplicateMergeWindowMs ||
            config.duplicateMergeCountThreshold != duplicateMergeCountThreshold
        val blockedRulesChanged = config.blockedRules != blockedRules
        val filterChanged =
            config.allowScroll != allowScroll ||
                config.allowTop != allowTop ||
                config.allowBottom != allowBottom ||
                config.allowColorful != allowColorful ||
                config.allowSpecial != allowSpecial ||
                config.weightFilterLevel != weightFilterLevel ||
                blockedRulesChanged
        val occlusionChanged = config.smartOcclusionEnabled != smartOcclusion
        val staticToScrollChanged = config.staticDanmakuToScroll != staticDanmakuToScroll
        
        config.opacity = opacity
        config.fontScale = fontScale
        config.fontWeight = fontWeight
        config.speedFactor = speed
        config.scrollDurationSeconds = scrollDurationSeconds
        config.displayAreaRatio = displayArea
        config.strokeWidth = strokeWidth
        config.lineHeight = lineHeight
        config.staticDurationSeconds = staticDurationSeconds
        config.scrollFixedVelocity = scrollFixedVelocity
        config.staticDanmakuToScroll = staticDanmakuToScroll
        config.massiveMode = massiveMode
        config.mergeDuplicates = mergeDuplicates
        config.duplicateMergeWindowMs = duplicateMergeWindowMs
        config.duplicateMergeCountThreshold = duplicateMergeCountThreshold
        config.allowScroll = allowScroll
        config.allowTop = allowTop
        config.allowBottom = allowBottom
        config.allowColorful = allowColorful
        config.allowSpecial = allowSpecial
        config.weightFilterLevel = weightFilterLevel.coerceIn(0, 11)
        config.blockedRules = blockedRules
        config.smartOcclusionEnabled = smartOcclusion
        if (blockedRulesChanged) {
            blockedRuleMatchers = compileDanmakuBlockRules(blockedRules)
        }

        if (occlusionChanged) {
            if (smartOcclusion) {
                if (webMaskBytes == null && cachedCid > 0L && cachedBvid.isNotBlank()) {
                    val expectedCid = cachedCid
                    val expectedGeneration = loadGeneration
                    maskFetchJob?.cancel()
                    maskFetchJob = scope.launch {
                        val maskInfo = com.android.purebilibili.data.repository.VideoRepository
                            .getPlayerInfo(cachedBvid, expectedCid)
                            .getOrNull()
                            ?.dmMask
                            ?: return@launch
                        loadWebMask(
                            cid = expectedCid,
                            url = maskInfo.maskUrl,
                            fps = maskInfo.fps,
                            positionMs = player?.currentPosition ?: 0L,
                            requestGeneration = expectedGeneration
                        )
                    }
                }
            } else {
                maskLoadJob?.cancel()
                maskFetchJob?.cancel()
                webMaskWindowStartMs = Long.MIN_VALUE
                webMaskWindowEndMs = Long.MIN_VALUE
                controller?.replaceMaskFrames(emptyList(), player?.currentPosition ?: 0L)
            }
        }
        
        if (mergeChanged || filterChanged || staticToScrollChanged) {
            val reason = if (mergeChanged) "merge_changed" else "filter_changed"
            applyConfigToController(reason)
        } else if (occlusionChanged) {
            applyConfigToController("smart_occlusion_toggle")
        } else {
            applyConfigToController("batch")
        }
    }

    private var viewport: DanmakuViewport? = null

    fun updateHotDanmakuPresentation(items: List<DanmakuItem>, heightPx: Int) {
        val idsChanged = items.size != hotDanmakuIds.size || items.any { it.danmakuId !in hotDanmakuIds }
        val textsChanged = items.size != hotDanmakuTexts.size || items.any { it.text.orEmpty() !in hotDanmakuTexts }
        if (idsChanged) hotDanmakuIds = items.mapTo(HashSet(items.size)) { it.danmakuId }
        if (textsChanged) hotDanmakuTexts = items.mapTo(HashSet(items.size)) { it.text.orEmpty() }
        if (idsChanged || textsChanged) {
            controller?.setHiddenDanmaku(hotDanmakuIds, hotDanmakuTexts)
        }
        val height = if (items.isEmpty()) 0f else heightPx.coerceAtLeast(0).toFloat()
        if (config.hotBarReservedHeightPx == height) return
        config.hotBarReservedHeightPx = height
        // 海量模式仅隐藏对应弹幕，不改变轨道；普通模式按浮层实测高度避让。
        if (!config.massiveMode) applyConfigToController("hot_bar_space")
    }

    /**
     * Hosts that only render (portrait pager, bangumi, offline, fullscreen overlay) rely on the
     * render target's own size, so every surface applies the same style without describing its
     * picture box first.
     */
    private fun resolveEffectiveViewport(view: DanmakuRenderView): DanmakuViewport? =
        resolveDanmakuViewport(
            widthPx = view.width,
            heightPx = view.height,
            density = context.resources.displayMetrics.density
        )

    /** The player host supplies the same geometry to the engine and Compose overlays. */
    fun updateViewport(value: DanmakuViewport) {
        if (viewport == value) return
        clearTapSelection()
        viewport = value
        val view = danmakuView ?: return
        // A pending layout will apply this configuration in attachView's resize path.
        if (view.width == value.widthPx && view.height == value.heightPx) {
            applyConfigToController("resize")
        }
    }

    /**
     * 应用弹幕配置到 Controller，并同步倍速基准
     * 样式与视口更新保留时间线；源数据变化才重新同步。
     */
    private fun applyConfigToController(reason: String) {
        controller?.let { ctrl ->
            val currentViewport = viewport ?: danmakuView?.let(::resolveEffectiveViewport) ?: return
            baseRenderConfig = config.resolveRenderConfig(currentViewport, isFullscreenSurface)

            // 记录设置后的基准时间，供倍速同步使用
            originalMoveTime = baseRenderConfig.scrollDurationMs
            originalTopShowTimeMin = baseRenderConfig.pinnedDurationMs
            originalTopShowTimeMax = baseRenderConfig.pinnedDurationMs
            originalBottomShowTimeMin = baseRenderConfig.pinnedDurationMs
            originalBottomShowTimeMax = baseRenderConfig.pinnedDurationMs
            applyPlaybackSpeedToController(ctrl)

            // Geometry/style updates remeasure retained items in the engine, including paused frames.
            // Only a changed source list needs a timeline replacement.
            // 正则屏蔽在整表扫描上非常重，禁止在主线程同步 rebuild（ANR）。
            if (reason == "merge_changed" || reason == "filter_changed" || reason == "staticDanmakuToScroll") {
                val rebuildReason = reason
                val expectedCid = cachedCid
                val expectedGeneration = loadGeneration
                val expectedWindowGeneration = windowGeneration
                configRebuildJob?.cancel()
                configRebuildJob = scope.launch {
                    val rebuild = withContext(Dispatchers.Default) {
                        buildDanmakuCacheFromSource(
                            expectedCid = expectedCid,
                            expectedGeneration = expectedGeneration,
                            expectedWindowGeneration = expectedWindowGeneration
                        )
                    } ?: return@launch
                    if (!isCurrentSegmentWindowRequest(
                            expectedCid, expectedGeneration, expectedWindowGeneration
                        )
                    ) return@launch
                    if (!commitDanmakuCacheRebuild(rebuild, rebuildReason)) return@launch
                    val list = cachedDanmakuList ?: return@launch
                    val currentPos = player?.currentPosition ?: 0L
                    Log.w(TAG, " Re-applying danmaku data after $rebuildReason change at ${currentPos}ms")
                    resyncDanmakuTimeline(
                        list = list,
                        positionMs = currentPos,
                        shouldPlay = shouldStartDanmakuOnDataReady(
                            isPlaying = player?.isPlaying == true,
                            playWhenReady = player?.playWhenReady == true
                        ),
                        reason = "config:$rebuildReason"
                    )
                }
            } else {
                ctrl.invalidate()
            }

            val applied = resolvePlaybackAdjustedRenderConfig()
            Log.w(
                TAG,
                " Config applied ($reason): opacity=${config.opacity}, fontScale=${config.fontScale}, " +
                    "fontWeight=${config.fontWeight}, speed=${config.speedFactor}, scrollSeconds=${config.scrollDurationSeconds}, " +
                    "area=${config.displayAreaRatio}, strokeWidth=${config.strokeWidth}, lineHeight=${config.lineHeight}, " +
                    "staticSeconds=${config.staticDurationSeconds}, fixedVelocity=${config.scrollFixedVelocity}, " +
                    "massiveMode=${config.massiveMode}, staticToScroll=${config.staticDanmakuToScroll}, " +
                    "smartOcclusion=${config.smartOcclusionEnabled}, " +
                    "allowScroll=${config.allowScroll}, allowTop=${config.allowTop}, allowBottom=${config.allowBottom}, " +
                    "allowColorful=${config.allowColorful}, allowSpecial=${config.allowSpecial}, " +
                    "baseMoveTime=$originalMoveTime, videoSpeed=$currentVideoSpeed, " +
                    "enginePlaySpeed=${applied.playSpeedPercent}, moveTime=${applied.scrollDurationMs}, " +
                    "pinnedShow=${applied.pinnedDurationMs}"
            )
        }
    }

    private fun applyPlaybackSpeedToController(ctrl: DanmakuEngine) {
        ctrl.updateConfig(resolvePlaybackAdjustedRenderConfig())
    }

    private fun resolvePlaybackAdjustedRenderConfig(): DanmakuRenderConfig {
        val normalizedSpeed = normalizeDanmakuPlaybackSpeed(currentVideoSpeed)
        return baseRenderConfig.copy(
            playSpeedPercent = resolveDanmakuEnginePlaySpeedPercent(normalizedSpeed),
            scrollDurationMs = resolveDanmakuPlaybackAdjustedDurationMillis(
                baseDurationMs = originalMoveTime,
                videoSpeed = normalizedSpeed
            ),
            pinnedDurationMs = resolveDanmakuPlaybackAdjustedDurationMillis(
                baseDurationMs = originalTopShowTimeMin,
                videoSpeed = normalizedSpeed
            ),
            maskEnabled = config.smartOcclusionEnabled && webMaskBytes != null
        )
    }
    
    //  [新增] 记录上次应用的视图尺寸，用于检测横竖屏切换
    private var lastAppliedWidth: Int = 0
    private var lastAppliedHeight: Int = 0
    
    /**
     * 绑定 DanmakuView
     * 
     *  [修复] 支持横竖屏切换时重新应用弹幕数据
     * 当同一个视图的尺寸发生变化时，也会重新设置弹幕数据
     */
    fun attachView(view: DanmakuRenderView) {
        if (danmakuView !== view) clearTapSelection()
        // 使用 Log.w (warning) 确保日志可见
        Log.w(TAG, "========== attachView CALLED ==========")
        Log.w(TAG, "📎 View size: width=${view.width}, height=${view.height}, lastApplied=${lastAppliedWidth}x${lastAppliedHeight}")
        
        //  [关键修复] 如果是同一个视图但尺寸发生变化（横竖屏切换），也需要重新应用弹幕数据
        val isSameView = danmakuView === view
        val sizeChanged = view.width != lastAppliedWidth || view.height != lastAppliedHeight
        val hasValidSize = view.width > 0 && view.height > 0
        
        if (isSameView && !sizeChanged && hasValidSize) {
            Log.w(TAG, "📎 attachView: Same view, same size, skipping")
            return
        }
        
        if (isSameView && sizeChanged && hasValidSize) {
            Log.w(TAG, "📎 attachView: Same view but size changed (rotation?), re-applying danmaku data")
            lastAppliedWidth = view.width
            lastAppliedHeight = view.height
            // [修复] 尺寸变化时，重新应用配置（计算行数）和数据
            applyConfigToController("resize")
            return
        }
        
        Log.w(TAG, "📎 attachView: new view, old=${danmakuView != null}, hashCode=${view.hashCode()}")

        renderTargets.remove(view)
        renderTargets.add(view)
        val previousController = controller
        if (danmakuView !== view) {
            previousController?.pause()
        }
        danmakuView = view
        controller = view.engine
        controller?.upOwnerUserHash = pendingUpOwnerUserHash
        controller?.setHiddenDanmaku(hotDanmakuIds, hotDanmakuTexts)
        if (!isRenderingEnabled) {
            controller?.pause()
            view.visibility = android.view.View.INVISIBLE
            isPlaying = false
            stopDriftSync()
        }
        applyDanmakuClickListener()
        
        Log.w(TAG, "📎 controller obtained: ${controller != null}")
        
        // 内置渲染层（ScrollLayer, TopCenterLayer, BottomCenterLayer）由 DanmakuRenderEngine 自动注册
        // 不需要手动添加，手动添加会报错 "The custom LayerType must not be less than 2000"
        
        // 应用配置并同步倍速基准
        applyConfigToController("attachView")

        // 相关推荐 push 新详情页时，loadDanmaku 常在旧 controller 仍绑定（或 controller=null）
        // 时完成；若只在「此前无 controller」时重放，新 view 会永远吃不到已就绪缓存，
        // 表现为开关显示「开」却无弹幕，只能手动重开开关（show）才恢复。
        // 切换视频时应先 clearForVideoChange 清掉旧缓存，避免把旧片弹幕闪到新 view。
        reapplyCachedDanmakuToCurrentControllerIfNeeded(
            previousController = previousController,
            reason = "attach_view_replay",
        )
        
        //  [关键修复] 等待 View 布局完成后再设置弹幕数据
        // DanmakuRenderEngine 需要有效的 View 尺寸来计算弹幕轨道位置
        if (hasValidSize) {
            // View 已经有有效尺寸，直接设置数据
            Log.w(TAG, "📎 View has valid size, setting data immediately")
            lastAppliedWidth = view.width
            lastAppliedHeight = view.height
            // [修复] 立即应用正确的配置（含高度）和数据
            applyConfigToController("resize")
        } else {
            // View 尺寸为 0，等待布局完成
            Log.w(TAG, "📎 View size is 0, waiting for layout...")
            view.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // 移除监听器，避免重复回调
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    
                    Log.w(TAG, "📎 Layout callback! width=${view.width}, height=${view.height}")
                    
                    // 确保 View 仍然是当前绑定的 View
                    if (danmakuView === view && view.width > 0 && view.height > 0) {
                        lastAppliedWidth = view.width
                        lastAppliedHeight = view.height
                        // [修复] 布局完成后，重新应用配置（含高度）和数据
                        applyConfigToController("resize")
                    } else if (danmakuView === view) {
                        //  [修复] 如果布局回调时尺寸仍为 0，延迟 100ms 再试一次
                        Log.w(TAG, " View still zero size, scheduling delayed retry...")
                        view.postDelayed({
                            if (danmakuView === view && view.width > 0 && view.height > 0) {
                                Log.w(TAG, "📎 Delayed retry: width=${view.width}, height=${view.height}")
                                lastAppliedWidth = view.width
                                lastAppliedHeight = view.height
                                applyConfigToController("resize")
                            } else {
                                Log.w(TAG, " View still invalid after delay, skipping")
                            }
                        }, 100)
                    } else {
                        Log.w(TAG, " View changed, skipping setData")
                    }
                }
            })
        }
        
        Log.w(TAG, "========== attachView COMPLETED ==========")
    }
    
    /**
     * 将缓存的弹幕数据应用到 controller（内部方法）
     */
    private fun applyDanmakuDataToController() {
        Log.w(TAG, "📎 cachedDanmakuList is null? ${cachedDanmakuList == null}, size=${cachedDanmakuList?.size ?: 0}")
        cachedDanmakuList?.let { list ->
            //  [修复] 始终用 playTime=0 设置数据，因为弹幕的 showAtTime 是相对于视频开头的
            Log.w(TAG, "📎 Calling setData with ${list.size} items, playTime=0 (base reference)")
            player?.let { p ->
                val position = p.currentPosition
                Log.w(TAG, "📎 Player state: isPlaying=${p.isPlaying}, isEnabled=${config.isEnabled}, position=${position}ms")
                resyncDanmakuTimeline(
                    list = list,
                    positionMs = position,
                    shouldPlay = p.isPlaying,
                    invalidateView = true,
                    reason = "applyDanmakuData"
                )
            } ?: Log.w(TAG, "📎 Player is null, not syncing")
        } ?: Log.w(TAG, "📎 No cached danmaku list to apply")
    }
    
    /** Detach a render target. A stale target is only allowed to release itself. */
    fun detachView(view: DanmakuRenderView) {
        if (danmakuView === view) clearTapSelection()
        renderPauseState.detach(view)
        renderTargets.remove(view)
        if (danmakuView !== view) {
            view.releaseRenderer()
            return
        }
        hide()
        clear()
        controller?.pause()
        if (timelineSyncedController === controller) {
            timelineSyncedController = null
        }
        // 缓存仍在时标记 pending，待新页面 attachView 再补时间线。
        pendingTimelineResync = cachedDanmakuList?.isNotEmpty() == true
        controller?.close()
        controller = null
        view.releaseRenderer()
        danmakuView = null
        renderTargets.lastOrNull()?.let { attachView(it) }
    }
    
    /**
     * ⚙️ [漂移修复] 启动定期漂移检测。
     * 根据倍速动态调整检测频率，只校正时间锚点，不清空当前渲染层。
     */
    private fun startDriftSync() {
        if (isSeekScrubbing || !isRenderingEnabled) return
        syncJob?.cancel()
        syncJob = scope.launch {
            var tickCount = 0
            var lastOrdinarySyncMs = SystemClock.elapsedRealtime()
            while (isActive) {
                val ordinaryInterval = resolveDanmakuDriftSyncIntervalMs(currentVideoSpeed)
                val specialInterval = (SPECIAL_REFRESH_GUARD_MS / (2.0 * currentVideoSpeed.coerceAtLeast(0.1f)))
                    .toLong().coerceAtLeast(1L)
                delay(if (specialWindow != null) minOf(ordinaryInterval, specialInterval) else ordinaryInterval)
                player?.let { p ->
                    if (p.isPlaying && isRenderingEnabled) {
                        val playerPos = p.currentPosition
                        requestSpecialDanmakuWindow(playerPos, "playback_progress")
                        if (!isPlaying) return@let
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastOrdinarySyncMs < ordinaryInterval) return@let
                        lastOrdinarySyncMs = now
                        tickCount++
                        when (
                            resolveDanmakuGuardAction(
                                videoSpeed = currentVideoSpeed,
                                tickCount = tickCount,
                                danmakuEnabled = isRenderingEnabled,
                                isPlaying = isPlaying,
                                hasData = cachedDanmakuList != null
                            )
                        ) {
                            DanmakuSyncAction.SoftResync -> {
                                softResyncDanmakuTimeline(
                                    positionMs = playerPos,
                                    shouldPlay = true,
                                    reason = "drift_sync_soft"
                                )
                            }
                            DanmakuSyncAction.HardResync -> {
                                cachedDanmakuList?.let { list ->
                                    resyncDanmakuTimeline(
                                        list = list,
                                        positionMs = playerPos,
                                        shouldPlay = true,
                                        reason = "drift_sync"
                                    )
                                }
                            }
                            DanmakuSyncAction.None,
                            DanmakuSyncAction.PauseOnly -> Unit
                        }
                        requestSegmentWindow(playerPos, "playback_progress")
                        requestWebMaskWindow(playerPos)
                        Log.d(
                            TAG,
                            "⚙️ Drift sync at ${playerPos}ms speed=$currentVideoSpeed tick=$tickCount"
                        )
                    }
                }
            }
        }
        Log.d(TAG, "⚙️ Drift sync started")
    }
    
    /**
     * ⚙️ [漂移修复] 停止定期漂移检测
     */
    private fun stopDriftSync() {
        syncJob?.cancel()
        syncJob = null
        Log.d(TAG, "⚙️ Drift sync stopped")
    }
    
    /**
     * 绑定 ExoPlayer
     * 
     * [修复] 添加同一播放器实例检查，避免重复绑定
     * 当从其他视频返回时，需要重新绑定当前播放器
     */
    fun attachPlayer(exoPlayer: ExoPlayer) {
        Log.d(TAG, " attachPlayer: new=${exoPlayer.hashCode()}, old=${player?.hashCode()}")

        if (player === exoPlayer && playerListener != null) {
            playerAttachmentCount++
            return
        }
        
        // 移除旧监听器（无论是同一播放器还是不同播放器）
        playerListener?.let { 
            player?.removeListener(it)
            Log.d(TAG, " Removed old listener from player ${player?.hashCode()}")
        }
        
        player = exoPlayer
        playerAttachmentCount = 1
        currentVideoSpeed = normalizeDanmakuPlaybackSpeed(exoPlayer.playbackParameters.speed)
        controller?.let { ctrl ->
            applyPlaybackSpeedToController(ctrl)
        }
        
        // 🎬 [根本修复] 不在这里启动帧同步，而是在 onIsPlayingChanged 中启动
        
        playerListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (player.isPlaying && isRenderingEnabled && specialIndexJob != null &&
                    syncJob?.isActive != true && !isSeekScrubbing
                ) startDriftSync()
            }

            override fun onIsPlayingChanged(isPlayerPlaying: Boolean) {
                if (isSeekScrubbing) return
                Log.w(TAG, " onIsPlayingChanged: isPlaying=$isPlayerPlaying, isEnabled=${config.isEnabled}, hasData=${cachedDanmakuList != null}")

                val syncAction = resolveDanmakuActionForIsPlayingChange(
                    isPlayerPlaying = isPlayerPlaying,
                    danmakuEnabled = isRenderingEnabled,
                    hasData = cachedDanmakuList != null
                )
                when (syncAction) {
                    DanmakuSyncAction.HardResync -> {
                        val position = exoPlayer.currentPosition
                        val shouldSuppressResync = shouldSuppressFollowupHardResync(position)
                        cachedDanmakuList?.let { list ->
                            if (shouldSuppressResync) {
                                Log.w(TAG, " Skip duplicate danmaku hard resync after explicit seek at ${position}ms (is_playing_changed)")
                            } else {
                                resyncDanmakuTimeline(
                                    list = list,
                                    positionMs = position,
                                    shouldPlay = true,
                                    reason = "is_playing_changed"
                                )
                            }
                        }
                        isPlaying = true
                        wasBufferingWhilePlaying = false
                        startDriftSync()
                        if (shouldSuppressResync) {
                            clearExplicitSeekResyncMarker()
                        }
                        Log.w(TAG, " Danmaku HARD RESYNC at ${position}ms with frame sync")
                    }
                    DanmakuSyncAction.PauseOnly -> {
                        // 暂停保留当前渲染层，普通恢复走 soft resync；若这是 seek/缓冲链路，
                        // 对应的 discontinuity / STATE_READY 事件仍会执行 hard resync。
                        lastExplicitSeekStartedPlayback =
                            resolveExplicitSeekStartedPlaybackAfterSyncAction(
                                explicitSeekStartedPlayback = lastExplicitSeekStartedPlayback,
                                action = syncAction
                            )
                        controller?.pause()
                        isPlaying = false
                        stopDriftSync()
                        Log.w(TAG, " Danmaku PAUSED (danmakus stay in place)")
                    }
                    DanmakuSyncAction.None -> {
                        if (isPlayerPlaying) {
                            Log.w(TAG, " Player playing but danmaku data not loaded/enabled yet, will sync after load")
                        }
                    }
                    DanmakuSyncAction.SoftResync -> {
                        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
                        softResyncDanmakuTimeline(
                            positionMs = position,
                            shouldPlay = true,
                            reason = "is_playing_resume"
                        )
                        isPlaying = true
                        wasBufferingWhilePlaying = false
                        clearExplicitSeekResyncMarker()
                        startDriftSync()
                        Log.w(TAG, " Danmaku SOFT RESUME at ${position}ms")
                    }
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isSeekScrubbing) return
                Log.d(TAG, " onPlaybackStateChanged: state=$playbackState")
                val syncAction = resolveDanmakuActionForPlaybackState(
                    playbackState = playbackState,
                    isPlayerPlaying = exoPlayer.isPlaying,
                    danmakuEnabled = isRenderingEnabled,
                    hasData = cachedDanmakuList != null,
                    resumedFromBuffering = wasBufferingWhilePlaying
                )
                when (syncAction) {
                    DanmakuSyncAction.HardResync -> {
                        val position = exoPlayer.currentPosition
                        val shouldSuppressResync = shouldSuppressFollowupHardResync(position)
                        cachedDanmakuList?.let { list ->
                            if (shouldSuppressResync) {
                                Log.w(TAG, " Skip duplicate danmaku hard resync after explicit seek at ${position}ms (state_ready_resume)")
                            } else {
                                resyncDanmakuTimeline(
                                    list = list,
                                    positionMs = position,
                                    shouldPlay = true,
                                    reason = "state_ready_resume"
                                )
                            }
                        }
                        isPlaying = true
                        wasBufferingWhilePlaying = false
                        startDriftSync()
                        if (shouldSuppressResync) {
                            clearExplicitSeekResyncMarker()
                        }
                    }
                    DanmakuSyncAction.PauseOnly -> {
                        lastExplicitSeekStartedPlayback =
                            resolveExplicitSeekStartedPlaybackAfterSyncAction(
                                explicitSeekStartedPlayback = lastExplicitSeekStartedPlayback,
                                action = syncAction
                            )
                        if (playbackState == Player.STATE_BUFFERING) {
                            wasBufferingWhilePlaying = isPlaying
                        } else {
                            wasBufferingWhilePlaying = false
                        }
                        controller?.pause()
                        if (playbackState == Player.STATE_ENDED) {
                            isPlaying = false
                            stopDriftSync()
                        }
                        if (playbackState == Player.STATE_BUFFERING) {
                            Log.d(TAG, " Buffering, danmaku paused")
                        }
                    }
                    DanmakuSyncAction.None -> {
                        if (playbackState != Player.STATE_BUFFERING) {
                            wasBufferingWhilePlaying = false
                        }
                    }
                    DanmakuSyncAction.SoftResync -> Unit
                }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (
                    resolveDanmakuActionForPositionDiscontinuity(
                        reason = reason,
                        hasData = cachedDanmakuList != null
                    ) == DanmakuSyncAction.HardResync
                ) {
                    if (isSeekScrubbing) return
                    cancelObsoleteWindowRequest(newPosition.positionMs)
                    requestSpecialDanmakuWindow(newPosition.positionMs, "seek_discontinuity")
                    Log.w(TAG, " Seek detected: ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms")
                    if (shouldSuppressFollowupHardResync(newPosition.positionMs)) {
                        Log.w(TAG, " Skip duplicate danmaku hard resync after explicit seek at ${newPosition.positionMs}ms (seek_discontinuity)")
                    } else if (shouldReplaceDanmakuWindow(activeSegmentIndices, newPosition.positionMs, totalSegmentCount)) {
                        controller?.pause()
                        controller?.clear()
                        requestSegmentWindow(newPosition.positionMs, "seek_discontinuity")
                    } else {
                        controller?.seekTo(newPosition.positionMs)
                        if (!exoPlayer.isPlaying) controller?.pause()
                    }
                }
            }
            
            //  [新增] 视频倍速变化时同步弹幕速度
            //  [问题10修复] 优化长按加速视频时的弹幕同步
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                val videoSpeed = normalizeDanmakuPlaybackSpeed(playbackParameters.speed)
                Log.w(TAG, "⏩ onPlaybackParametersChanged: videoSpeed=$videoSpeed, previous=$currentVideoSpeed")
                
                //  同步弹幕速度：同时更新引擎时间轴、滚动速度和静态弹幕停留时间。
                if (abs(videoSpeed - currentVideoSpeed) > 0.001f) {
                    currentVideoSpeed = videoSpeed
                    
                    controller?.let { ctrl ->
                        executeDanmakuPlaybackSpeedUpdate(
                            applyTiming = { applyPlaybackSpeedToController(ctrl) },
                            invalidate = { ctrl.invalidate() }
                        )
                        val applied = resolvePlaybackAdjustedRenderConfig()
                        Log.w(
                            TAG,
                            "⏩ Danmaku speed sync: engine=${applied.playSpeedPercent}, " +
                                "moveTime=${applied.scrollDurationMs} (base=$originalMoveTime), " +
                                "pinnedShow=${applied.pinnedDurationMs}, " +
                                "video=${videoSpeed}x"
                        )
                    }
                }
            }
        }
        
        exoPlayer.addListener(playerListener!!)

        if (shouldResyncDanmakuAfterPlayerAttach(
                danmakuEnabled = isRenderingEnabled,
                hasData = cachedDanmakuList != null,
                hasController = controller != null
            )
        ) {
            applyCachedDanmakuToController("player_attach")
        }
    }

    /**
     * 仅解绑仍由指定播放器持有的监听器，不触碰当前 DanmakuView/controller 或缓存。
     *
     * 同一播放身份可能同时存在普通、全屏等多个 Compose owner；只有最后一个 owner
     * 解绑时才移除播放器监听器，View 的切换由 [attachView] / [detachView] 独立负责。
     */
    fun detachPlayer(exoPlayer: ExoPlayer) {
        if (player !== exoPlayer) {
            Log.d(
                TAG,
                "detachPlayer: player=${exoPlayer.hashCode()} is not current " +
                    "(${player?.hashCode()}), skipping"
            )
            return
        }

        playerAttachmentCount = (playerAttachmentCount - 1).coerceAtLeast(0)
        if (playerAttachmentCount > 0) return

        playerListener?.let(exoPlayer::removeListener)
        playerListener = null
        player = null
        playerAttachmentCount = 0
        stopDriftSync()
        isPlaying = false
        wasBufferingWhilePlaying = false
        clearExplicitSeekResyncMarker()
        Log.d(TAG, "detachPlayer: detached player=${exoPlayer.hashCode()}")
    }
    
    /**
     * 加载弹幕数据
     * 
     * @param cid 视频 cid
     * @param aid 视频 aid (用于获取弹幕高级元数据)
     * @param durationMs 视频时长 (毫秒)，用于计算 Protobuf 分段数。如果为 0，则回退到 XML API
     */
    fun loadDanmaku(cid: Long, aid: Long, durationMs: Long = 0L, bvid: String = "") {
        Log.w(TAG, "========== loadDanmaku CALLED cid=$cid, aid=$aid, duration=${durationMs}ms ==========")
        Log.w(TAG, " loadDanmaku: cid=$cid, cached=$cachedCid, isLoading=$isLoading, controller=${controller != null}")
        
        // 如果正在加载，优先处理新 cid
        if (isLoading) {
            if (cid != cachedCid) {
                Log.w(TAG, " Loading in progress for cid=$cachedCid, canceling to load cid=$cid")
                loadJob?.cancel()
                isLoading = false
            } else {
                Log.w(TAG, " Already loading same cid=$cid, skipping")
                return
            }
        }
        
        // 如果是同一个 cid 且已有缓存数据，直接使用（横竖屏切换场景）
        if (cid == cachedCid && cachedDanmakuList != null) {
            // Lifecycle changes can repeat this request for the same playback identity.
            // Only a replacement/unsynced controller needs the cached window replayed.
            reapplyCachedDanmakuToCurrentControllerIfNeeded(
                previousController = controller,
                reason = "load_cached"
            )
            return
        }
        
        // 需要从网络加载新 cid 的弹幕
        Log.w(TAG, " loadDanmaku: New cid=$cid, loading from network")
        configRebuildJob?.cancel()
        windowLoadJob?.cancel()
        stopDriftSync()
        isPlaying = false
        isLoading = true
        isSeekScrubbing = false
        cachedCid = cid
        cachedAid = aid
        cachedBvid = bvid
        cachedDurationMs = durationMs
        sessionIdentity = sessionIdentity?.copy(cid = cid)
        val requestGeneration = ++loadGeneration
        val requestWindowGeneration = ++windowGeneration
        clearExplicitSeekResyncMarker()
        updateHotDanmakuPresentation(emptyList(), 0)
        cachedDanmakuList = null
        sourceDanmakuList = null
        sourceAdvancedDanmakuList = null
        sourceBasDanmakuList = null
        sourceCommandDanmakuList = emptyList()
        parsedSegments.clear()
        missingSegmentRetryAtMs = 0L
        activeSegmentIndices = emptyList()
        pendingSegmentIndices = emptyList()
        clearSpecialDanmakuWindow()
        localSegmentPaths = emptyList()
        isLocalSegmentSession = false
        webMaskBytes = null
        webMaskFps = 0
        webMaskWindowStartMs = Long.MIN_VALUE
        webMaskWindowEndMs = Long.MIN_VALUE
        maskLoadJob?.cancel()
        maskFetchJob?.cancel()
        _advancedDanmakuFlow.value = emptyList()
        _basDanmakuFlow.value = emptyList()
        _commandDanmakuFlow.value = emptyList()
        // 旧时间线对当前 controller 已失效；新数据就绪后必须重新 setData。
        timelineSyncedController = null
        pendingTimelineResync = false
        
        // 清除现有弹幕
        controller?.stop()
        
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val maskInfoDeferred = async {
                    if (config.smartOcclusionEnabled && bvid.isNotBlank()) {
                        com.android.purebilibili.data.repository.VideoRepository
                            .getPlayerInfo(bvid, cid)
                            .getOrNull()
                            ?.dmMask
                    } else {
                        null
                    }
                }
                val viewReply = if (aid > 0) {
                     com.android.purebilibili.data.repository.DanmakuRepository.getDanmakuView(cid, aid)
                } else null
                if (!isCurrentSegmentWindowRequest(cid, requestGeneration, requestWindowGeneration)) {
                    return@launch
                }
                totalSegmentCount = com.android.purebilibili.data.repository.resolveDanmakuSegmentCount(
                    durationMs = durationMs,
                    metadataSegmentCount = viewReply?.dmSge?.total?.toInt()
                )
                sourceCommandDanmakuList = viewReply?.commandDms.orEmpty().mapNotNull(::buildCommandDanmakuItem)
                _commandDanmakuFlow.value = sourceCommandDanmakuList
                prepareSpecialDanmakuWindow(viewReply?.specialDms.orEmpty())
                loadAndApplySegmentWindow(
                    cid = cid,
                    positionMs = player?.currentPosition ?: 0L,
                    requestGeneration = requestGeneration,
                    requestWindowGeneration = requestWindowGeneration,
                    allowXmlFallback = true,
                    reason = "load_new"
                )
                val maskInfo = maskInfoDeferred.await()
                if (
                    maskInfo != null &&
                    maskInfo.maskUrl.isNotBlank() &&
                    shouldApplyDanmakuLoadResult(cid, requestGeneration, cachedCid, loadGeneration)
                ) {
                    loadWebMask(
                        cid = cid,
                        url = maskInfo.maskUrl,
                        fps = maskInfo.fps,
                        positionMs = player?.currentPosition ?: 0L,
                        requestGeneration = requestGeneration
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, " Failed to load danmaku for cid=$cid: ${e.message}", e)
                withContext(Dispatchers.Main.immediate) {
                    if (shouldApplyDanmakuLoadResult(cid, requestGeneration, cachedCid, loadGeneration)) {
                        isLoading = false
                    }
                }
            }
        }
    }

    private suspend fun loadAndApplySegmentWindow(
        cid: Long,
        positionMs: Long,
        requestGeneration: Long,
        requestWindowGeneration: Long,
        allowXmlFallback: Boolean,
        reason: String
    ) {
        val requestedSegments = segmentWindowForPosition(positionMs, totalSegmentCount)
        val anchorSegment = segmentIndexForPosition(positionMs).coerceIn(1, totalSegmentCount)
        val anchorWasCached = anchorSegment in parsedSegments
        val anchorParsed = parsedSegments[anchorSegment] ?: loadParsedSegment(cid, anchorSegment)
        val xmlFallback = if (anchorParsed == null && allowXmlFallback) loadXmlFallback(cid) else null

        if (!isCurrentSegmentWindowRequest(cid, requestGeneration, requestWindowGeneration)) return
        if (anchorParsed != null) parsedSegments[anchorSegment] = anchorParsed
        if (xmlFallback != null) {
            // Never put a whole-video XML list into one protobuf segment: neighbors would
            // duplicate it, and rolling that window forward would discard future comments.
            requestedSegments.forEach { index ->
                if (index !in parsedSegments) {
                    parsedSegments[index] = sliceDanmakuFallbackSegment(xmlFallback, index)
                }
            }
        }
        val neighborIndices = requestedSegments.filter { it != anchorSegment && it !in parsedSegments }
        // During normal forward playback, keep the existing renderer timeline alive until the
        // complete next window is available. Applying the cached anchor first used to clear and
        // restart the renderer twice at every six-minute boundary (most visibly around 18 min).
        val applyOnlyWhenComplete = reason == "playback_progress" || reason == "segment_retry"
        if (!applyOnlyWhenComplete) {
            applyParsedSegmentWindow(
                cid = cid,
                positionMs = positionMs,
                requestGeneration = requestGeneration,
                requestWindowGeneration = requestWindowGeneration,
                reason = reason,
                requestedSegments = requestedSegments
            )
        }

        val neighborResults = coroutineScope {
            neighborIndices.map { index ->
                async { index to loadParsedSegment(cid, index) }
            }.awaitAll()
        }

        if (!isCurrentSegmentWindowRequest(cid, requestGeneration, requestWindowGeneration)) return
        neighborResults.forEach { (index, parsed) ->
            // A failed fetch must remain missing; a successfully parsed empty segment is valid.
            if (parsed != null) parsedSegments[index] = parsed
        }
        parsedSegments.keys.retainAll(requestedSegments.toSet())
        missingSegmentRetryAtMs = if (requestedSegments.any { it !in parsedSegments }) {
            SystemClock.elapsedRealtime() + SEGMENT_RETRY_INTERVAL_MS
        } else 0L
        if (reason == "segment_retry" && (anchorWasCached || anchorParsed == null) &&
            neighborResults.none { (_, parsed) -> parsed != null }
        ) return
        // Eager callers already applied the cached window above; nothing new arrived.
        if (!applyOnlyWhenComplete && neighborIndices.isEmpty()) return
        applyParsedSegmentWindow(
            cid = cid,
            positionMs = positionMs,
            requestGeneration = requestGeneration,
            requestWindowGeneration = requestWindowGeneration,
            reason = reason,
            requestedSegments = requestedSegments
        )
    }

    private suspend fun loadParsedSegment(cid: Long, segmentIndex: Int): ParsedDanmaku? {
        val localSource = isLocalSegmentSession
        val localPath = localSegmentPaths.getOrNull(segmentIndex - 1)
        val bytes = if (localSource) {
            if (localPath == null) return null
            try {
                withContext(Dispatchers.IO) {
                    java.io.File(localPath).takeIf { it.isFile && it.length() > 0L }?.readBytes()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read local danmaku segment $segmentIndex", e)
                null
            }
        } else {
            com.android.purebilibili.data.repository.DanmakuRepository
                .getDanmakuSegment(cid, segmentIndex)
        } ?: return null
        return withContext(Dispatchers.Default) {
            val parsed = DanmakuParser.parseProtobuf(listOf(bytes))
            if (parsed.failedSegmentCount > 0) {
                if (!localSource) {
                    com.android.purebilibili.data.repository.DanmakuContentRepository
                        .invalidateDanmakuSegment(cid, segmentIndex)
                }
                return@withContext null
            }
            if (parsed.serverDisabled) {
                com.android.purebilibili.data.repository.DanmakuRepository
                    .markDanmakuServerDisabled(cid)
            }
            parsed
        }
    }

    private suspend fun loadXmlFallback(cid: Long): ParsedDanmaku? {
        val raw = com.android.purebilibili.data.repository.DanmakuRepository
            .getDanmakuRawData(cid)?.takeIf { it.isNotEmpty() } ?: return null
        return withContext(Dispatchers.Default) { DanmakuParser.parse(raw) }
    }

    private suspend fun applyParsedSegmentWindow(
        cid: Long,
        positionMs: Long,
        requestGeneration: Long,
        requestWindowGeneration: Long,
        reason: String,
        requestedSegments: List<Int>
    ) {
        cacheUpdateMutex.withLock {
            if (!isCurrentSegmentWindowRequest(cid, requestGeneration, requestWindowGeneration)) return
            val orderedSegments = requestedSegments.mapNotNull(parsedSegments::get)
            val specialSnapshot = specialDanmaku
            val rebuild = withContext(Dispatchers.Default) {
                val windowStandard = (orderedSegments.flatMap { it.standardList } + specialSnapshot.standardList)
                    .sortedBy(DanmakuItem::showAtTime)
                val windowAdvanced =
                    (orderedSegments.flatMap { it.advancedList } + specialSnapshot.advancedList)
                        .sortedBy(AdvancedDanmakuData::startTimeMs)
                val windowBas = (orderedSegments.flatMap { it.basList } + specialSnapshot.basList)
                    .sortedBy(BasDanmaku::startTimeMs)

                buildDanmakuCacheFromSource(
                    expectedCid = cid,
                    expectedGeneration = requestGeneration,
                    expectedWindowGeneration = requestWindowGeneration,
                    sourceStandardOverride = windowStandard,
                    sourceAdvancedOverride = windowAdvanced,
                    sourceBasOverride = windowBas
                )
            }
            if (!isCurrentSegmentWindowRequest(cid, requestGeneration, requestWindowGeneration)) return
            withContext(Dispatchers.Main.immediate) {
                if (!isCurrentSegmentWindowRequest(cid, requestGeneration, requestWindowGeneration)) {
                    return@withContext
                }
                val currentPositionMs = resolveDanmakuDataReadyPositionMs(
                    currentPlayerPositionMs = player?.currentPosition,
                    requestedPositionMs = positionMs,
                )
                val currentAnchor = segmentIndexForPosition(currentPositionMs).coerceIn(1, totalSegmentCount)
                if (currentAnchor !in requestedSegments) {
                    // Weak-network loads can finish after a seek or several segment boundaries.
                    // Keep the displayed window and fetch the one the player now needs.
                    requestSegmentWindow(currentPositionMs, "stale_window_recovery")
                    return@withContext
                }
                isLoading = false
                activeSegmentIndices = requestedSegments
                if (rebuild == null || !commitDanmakuCacheRebuild(rebuild, reason)) {
                    controller?.clear()
                    isPlaying = false
                    return@withContext
                }
                if (isSeekScrubbing) {
                    pendingTimelineResync = true
                    return@withContext
                }
                val currentController = controller
                if (
                    reason == "playback_progress" &&
                    currentController != null &&
                    currentController.rollWindowForward(
                        DanmakuWindow(
                            anchorSegment = segmentIndexForPosition(currentPositionMs),
                            segmentIndices = activeSegmentIndices,
                            items = cachedDanmakuList.orEmpty(),
                        )
                    )
                ) {
                    timelineSyncedController = currentController
                    pendingTimelineResync = false
                    isPlaying = player?.isPlaying == true && isRenderingEnabled
                    Log.d(TAG, "Rolled danmaku window forward without timeline restart ($reason)")
                    return@withContext
                }
                resyncDanmakuTimeline(
                    list = cachedDanmakuList.orEmpty(),
                    positionMs = currentPositionMs,
                    shouldPlay = shouldStartDanmakuOnDataReady(
                        isPlaying = player?.isPlaying == true,
                        playWhenReady = player?.playWhenReady == true
                    ),
                    invalidateView = true,
                    reason = reason
                )
                if (isPlaying) startDriftSync()
            }
        }
    }

    private fun isCurrentSegmentWindowRequest(
        cid: Long,
        requestGeneration: Long,
        requestWindowGeneration: Long
    ): Boolean = shouldApplyDanmakuLoadResult(
        expectedCid = cid,
        expectedGeneration = requestGeneration,
        currentCid = cachedCid,
        currentGeneration = loadGeneration
    ) && requestWindowGeneration == windowGeneration

    private fun cancelObsoleteWindowRequest(positionMs: Long) {
        if (!shouldCancelPendingDanmakuWindow(pendingSegmentIndices, positionMs, totalSegmentCount)) return
        ++windowGeneration
        windowLoadJob?.cancel()
        windowLoadJob = null
        maskLoadJob?.cancel()
        pendingSegmentIndices = emptyList()
    }

    private fun requestSegmentWindow(positionMs: Long, reason: String) {
        cancelObsoleteWindowRequest(positionMs)
        val requestedSegments = segmentWindowForPosition(positionMs, totalSegmentCount)
        val hasMissingSegments = !isLocalSegmentSession && loadJob?.isActive != true &&
            SystemClock.elapsedRealtime() >= missingSegmentRetryAtMs &&
            activeSegmentIndices.any { it in requestedSegments && it !in parsedSegments }
        if (
            cachedCid <= 0L ||
            !shouldRequestDanmakuWindow(
                activeSegments = activeSegmentIndices,
                pendingSegments = pendingSegmentIndices,
                requestInFlight = windowLoadJob?.isActive == true,
                positionMs = positionMs,
                totalSegments = totalSegmentCount,
                hasMissingSegments = hasMissingSegments
            )
        ) {
            return
        }
        windowLoadJob?.cancel()
        pendingSegmentIndices = requestedSegments
        val requestCid = cachedCid
        val requestGeneration = loadGeneration
        val requestWindowGeneration = ++windowGeneration
        val loadReason = if (hasMissingSegments && reason == "playback_progress") {
            "segment_retry"
        } else reason
        windowLoadJob = scope.launch {
            try {
                loadAndApplySegmentWindow(
                    cid = requestCid,
                    positionMs = positionMs,
                    requestGeneration = requestGeneration,
                    requestWindowGeneration = requestWindowGeneration,
                    allowXmlFallback = false,
                    reason = loadReason
                )
                requestWebMaskWindow(
                    positionMs = positionMs,
                    expectedCid = requestCid,
                    requestGeneration = requestGeneration,
                    requestWindowGeneration = requestWindowGeneration
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to replace danmaku segment window", e)
                if (isCurrentSegmentWindowRequest(requestCid, requestGeneration, requestWindowGeneration)) {
                    missingSegmentRetryAtMs = SystemClock.elapsedRealtime() + SEGMENT_RETRY_INTERVAL_MS
                }
            } finally {
                if (requestWindowGeneration == windowGeneration) {
                    pendingSegmentIndices = emptyList()
                }
            }
        }
    }

    private fun clearSpecialDanmakuWindow() {
        ++specialWindowGeneration
        specialWindowLoadJob?.cancel()
        specialWindowLoadJob = null
        specialIndexJob?.cancel()
        specialIndexJob = null
        specialWindow = null
        specialWindowStartMs = Long.MIN_VALUE
        specialWindowEndMs = Long.MIN_VALUE
        specialLoadingPausedForBackground = false
        specialDanmaku = ParsedDanmaku(emptyList(), emptyList())
        mergedAdvancedDanmakuList = emptyList()
    }

    private fun prepareSpecialDanmakuWindow(
        urls: List<String> = emptyList(),
        localPaths: List<String> = emptyList()
    ) {
        if (urls.isEmpty() && localPaths.isEmpty()) return
        val window = SpecialDanmakuWindow()
        specialWindow = window
        val expectedCid = cachedCid
        val expectedGeneration = loadGeneration
        specialIndexJob = scope.launch {
            com.android.purebilibili.data.repository.indexSpecialDanmakuSources(urls, localPaths)
                .collect { indexed ->
                    if (!shouldApplyDanmakuLoadResult(expectedCid, expectedGeneration, cachedCid, loadGeneration)) {
                        return@collect
                    }
                    specialWindowMutex.withLock { window.addSource(indexed) }
                    val position = player?.currentPosition ?: 0L
                    if (indexed.entries.any { it.startTimeMs < position + SPECIAL_LOOK_AHEAD_MS }) {
                        specialWindowStartMs = Long.MIN_VALUE
                        specialWindowEndMs = Long.MIN_VALUE
                        requestSpecialDanmakuWindow(position, "source_ready")
                    }
                }
        }
        requestSpecialDanmakuWindow(player?.currentPosition ?: 0L, "load_new")
        if (player?.isPlaying == true) startDriftSync()
    }

    private fun requestSpecialDanmakuWindow(positionMs: Long, reason: String) {
        if (specialLoadingPausedForBackground) return
        val window = specialWindow ?: return
        val position = positionMs.coerceAtLeast(0L)
        if (specialWindowEndMs != Long.MIN_VALUE && position >= specialWindowStartMs &&
            position < specialWindowEndMs - SPECIAL_REFRESH_GUARD_MS
        ) return
        if (specialWindowLoadJob?.isActive == true &&
            reason != "manual_seek" && reason != "seek_discontinuity" &&
            reason != "seek_scrub_cancel" && reason != "source_ready"
        ) return
        specialWindowLoadJob?.cancel()
        val expectedCid = cachedCid
        val expectedGeneration = loadGeneration
        val requestGeneration = ++specialWindowGeneration
        specialWindowLoadJob = scope.launch {
            try {
                if (!shouldApplyDanmakuLoadResult(expectedCid, expectedGeneration, cachedCid, loadGeneration) ||
                    requestGeneration != specialWindowGeneration
                ) return@launch
                var requestedPosition = position
                while (isActive) {
                    val normalDuration = maxOf(baseRenderConfig.scrollDurationMs, baseRenderConfig.pinnedDurationMs)
                    val result = withContext(Dispatchers.Default) {
                        specialWindowMutex.withLock {
                            window.load(requestedPosition, SPECIAL_LOOK_AHEAD_MS, normalDuration)
                        }
                    }
                    if (!shouldApplyDanmakuLoadResult(expectedCid, expectedGeneration, cachedCid, loadGeneration) ||
                        requestGeneration != specialWindowGeneration
                    ) return@launch
                    val currentPosition = player?.currentPosition ?: requestedPosition
                    if (currentPosition < result.startTimeMs || currentPosition >= result.endTimeMs) {
                        requestedPosition = currentPosition
                        continue
                    }
                    applySpecialDanmakuWindow(result, expectedCid, expectedGeneration, requestGeneration)
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load special danmaku playback window", e)
            }
        }
    }

    private suspend fun applySpecialDanmakuWindow(
        result: SpecialDanmakuWindowResult,
        expectedCid: Long,
        expectedGeneration: Long,
        requestGeneration: Long
    ) {
        cacheUpdateMutex.withLock {
            fun isCurrent(): Boolean =
                shouldApplyDanmakuLoadResult(expectedCid, expectedGeneration, cachedCid, loadGeneration) &&
                    requestGeneration == specialWindowGeneration
            if (!isCurrent()) return
            val ordered = activeSegmentIndices.mapNotNull(parsedSegments::get)
            val special = result.parsed
            val standardChanged = specialDanmaku.standardList != special.standardList
            val rebuild = withContext(Dispatchers.Default) {
                val advanced = (ordered.flatMap { it.advancedList } + special.advancedList)
                    .sortedBy(AdvancedDanmakuData::startTimeMs)
                val bas = (ordered.flatMap { it.basList } + special.basList).sortedBy(BasDanmaku::startTimeMs)
                if (standardChanged) {
                    buildDanmakuCacheFromSource(
                        expectedCid = expectedCid,
                        expectedGeneration = expectedGeneration,
                        sourceStandardOverride = (ordered.flatMap { it.standardList } + special.standardList)
                            .sortedBy(DanmakuItem::showAtTime),
                        sourceAdvancedOverride = advanced,
                        sourceBasOverride = bas
                    )
                } else {
                    val (_, pluginAdvanced) = applyDanmakuPluginPipeline(emptyList(), advanced)
                    val (_, visibleAdvanced) = applyDanmakuTypeFilters(emptyList(), pluginAdvanced)
                    DanmakuCacheRebuild(
                        sourceStandard = null,
                        sourceAdvanced = advanced,
                        sourceBas = bas,
                        rawStandard = rawDanmakuList.orEmpty(),
                        visibleStandard = cachedDanmakuList.orEmpty(),
                        visibleAdvanced = visibleAdvanced + mergedAdvancedDanmakuList,
                        visibleBas = filterBasDanmakuForRender(bas),
                        mergedAdvanced = mergedAdvancedDanmakuList
                    )
                }
            } ?: return
            if (!isCurrent()) return
            specialDanmaku = special
            specialWindowStartMs = result.startTimeMs
            specialWindowEndMs = result.endTimeMs
            commitDanmakuCacheRebuild(rebuild, "special_window")
            if (standardChanged) applyCachedDanmakuToController("special_window")
            Log.d(
                TAG,
                "Special playback window: ${result.startTimeMs}..${result.endTimeMs}ms, " +
                    "advanced=${special.advancedList.size}, BAS=${special.basList.size}"
            )
            if (player?.isPlaying == true && isRenderingEnabled && syncJob?.isActive != true) startDriftSync()
        }
    }

    private suspend fun loadWebMask(
        cid: Long,
        url: String,
        fps: Int,
        positionMs: Long,
        requestGeneration: Long
    ) {
        val bytes = com.android.purebilibili.data.repository.DanmakuRepository.getWebMask(url) ?: return
        if (!shouldApplyDanmakuLoadResult(cid, requestGeneration, cachedCid, loadGeneration)) return
        webMaskBytes = bytes
        webMaskFps = fps
        applyConfigToController("webmask_ready")
        requestWebMaskWindow(
            positionMs = positionMs,
            expectedCid = cid,
            requestGeneration = requestGeneration,
            requestWindowGeneration = windowGeneration
        )
    }

    private fun requestWebMaskWindow(
        positionMs: Long,
        expectedCid: Long = cachedCid,
        requestGeneration: Long = loadGeneration,
        requestWindowGeneration: Long = windowGeneration
    ) {
        val bytes = webMaskBytes ?: return
        if (!config.smartOcclusionEnabled) return
        if (
            positionMs >= webMaskWindowStartMs + WEB_MASK_REFRESH_GUARD_MS &&
            positionMs <= webMaskWindowEndMs - WEB_MASK_REFRESH_GUARD_MS
        ) return
        val windowStartMs = (positionMs - WEB_MASK_LOOK_BEHIND_MS).coerceAtLeast(0L)
        val windowEndMs = positionMs + WEB_MASK_LOOK_AHEAD_MS
        maskLoadJob?.cancel()
        webMaskWindowStartMs = windowStartMs
        webMaskWindowEndMs = windowEndMs
        maskLoadJob = scope.launch {
            val frames = withContext(Dispatchers.Default) {
                WebMaskParser.parseWindow(
                    data = bytes,
                    fps = webMaskFps,
                    windowStartMs = windowStartMs,
                    windowEndMs = windowEndMs
                )
            }
            if (!isCurrentSegmentWindowRequest(expectedCid, requestGeneration, requestWindowGeneration) ||
                !config.smartOcclusionEnabled || isSeekScrubbing
            ) return@launch
            controller?.replaceMaskFrames(
                frames,
                resolveDanmakuDataReadyPositionMs(player?.currentPosition, positionMs)
            )
        }
    }

    /**
     * 加载离线缓存弹幕。该路径只读取本地文件，不做网络补拉。
     */
    fun loadLocalDanmaku(
        cid: Long,
        standardSegmentPaths: List<String>,
        specialSegmentPaths: List<String> = emptyList()
    ) {
        Log.w(TAG, "========== loadLocalDanmaku CALLED cid=$cid, segments=${standardSegmentPaths.size} ==========")
        loadJob?.cancel()
        windowLoadJob?.cancel()
        configRebuildJob?.cancel()
        maskLoadJob?.cancel()
        maskFetchJob?.cancel()
        stopDriftSync()
        isPlaying = false
        isLoading = true
        isSeekScrubbing = false
        cachedCid = cid
        cachedAid = 0L
        cachedBvid = ""
        cachedDurationMs = 0L
        webMaskBytes = null
        webMaskFps = 0
        webMaskWindowStartMs = Long.MIN_VALUE
        webMaskWindowEndMs = Long.MIN_VALUE
        timelineSyncedController = null
        pendingTimelineResync = false
        controller?.replaceMaskFrames(emptyList(), 0L)
        sessionIdentity = sessionIdentity?.copy(cid = cid)
        localSegmentPaths = standardSegmentPaths.toList()
        isLocalSegmentSession = true
        totalSegmentCount = standardSegmentPaths.size.coerceAtLeast(1)
        val requestGeneration = ++loadGeneration
        val requestWindowGeneration = ++windowGeneration
        clearExplicitSeekResyncMarker()
        updateHotDanmakuPresentation(emptyList(), 0)
        cachedDanmakuList = null
        sourceDanmakuList = null
        sourceAdvancedDanmakuList = null
        sourceBasDanmakuList = null
        sourceCommandDanmakuList = emptyList()
        _advancedDanmakuFlow.value = emptyList()
        _basDanmakuFlow.value = emptyList()
        _commandDanmakuFlow.value = emptyList()
        parsedSegments.clear()
        missingSegmentRetryAtMs = 0L
        activeSegmentIndices = emptyList()
        pendingSegmentIndices = emptyList()
        clearSpecialDanmakuWindow()
        prepareSpecialDanmakuWindow(localPaths = specialSegmentPaths)
        controller?.stop()

        loadJob = scope.launch {
            try {

                loadAndApplySegmentWindow(
                    cid = cid,
                    positionMs = player?.currentPosition ?: 0L,
                    requestGeneration = requestGeneration,
                    requestWindowGeneration = requestWindowGeneration,
                    allowXmlFallback = false,
                    reason = "offline_load"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, " Failed to load local danmaku for cid=$cid: ${e.message}", e)
                withContext(Dispatchers.Main.immediate) {
                    if (shouldApplyDanmakuLoadResult(cid, requestGeneration, cachedCid, loadGeneration)) {
                        isLoading = false
                    }
                }
            }
        }
    }
    
    fun show() {
        if (!isRenderingEnabled) {
            controller?.pause()
            danmakuView?.visibility = android.view.View.INVISIBLE
            isPlaying = false
            stopDriftSync()
            return
        }
        Log.d(TAG, "👁️ show()")
        val alreadyVisible = danmakuView?.visibility == android.view.View.VISIBLE
        danmakuView?.visibility = android.view.View.VISIBLE
        // Host/settings recomposition may request show again. A live, synced surface
        // already owns its timeline; restarting it would clear all on-screen comments.
        if (alreadyVisible && controller != null && timelineSyncedController === controller &&
            !pendingTimelineResync && !isSeekScrubbing
        ) return

        // [修复] 相关推荐/同页切集后弹幕开关开启却无弹幕：Enable→show() 常在弹幕数据
        // 就绪前执行，若此时还要求 player.isPlaying 瞬时成立才装填时间线，数据就绪后
        // 引擎可能停在 paused 且没有新的 isPlaying 事件恢复（该事件已在数据加载完成前
        // 被 None 分支吃掉），只能靠手动重开弹幕开关触发 show() 才恢复。
        // 改为「数据就绪即装填时间线」，shouldPlay 仍按实际播放状态决定 start/pause；
        // 之后 onIsPlayingChanged(true) 会走 SoftResync 完成最终时钟校准。
        cachedDanmakuList?.let { list ->
            resyncDanmakuTimeline(
                list = list,
                positionMs = player?.currentPosition ?: 0L,
                shouldPlay = shouldStartDanmakuOnDataReady(
                    isPlaying = player?.isPlaying == true,
                    playWhenReady = player?.playWhenReady == true
                ),
                invalidateView = true,
                reason = "show"
            )
        }
    }
    
    fun hide() {
        clearTapSelection()
        Log.d(TAG, "🙈 hide()")
        controller?.pause()
        controller?.clear()
        danmakuView?.visibility = android.view.View.GONE
        isPlaying = false
        stopDriftSync()
    }
    
    /**
     *  清除当前显示的弹幕（拖动进度条时调用）
     */
    fun clear() {
        clearTapSelection()
        Log.d(TAG, "🧹 clear() - clearing displayed danmakus")
        controller?.clear()
    }

    /**
     * 竖屏切换视频时不仅要清屏，还要废弃未完成的旧 cid 加载结果。
     * 否则旧请求可能在新页面封面阶段回填到同一个 DanmakuView。
     */
    fun clearForVideoChange() {
        clearTapSelection()
        isSeekScrubbing = false
        Log.d(TAG, "clearForVideoChange() - canceling active load and clearing displayed danmakus")
        updateHotDanmakuPresentation(emptyList(), 0)
        loadJob?.cancel()
        windowLoadJob?.cancel()
        maskLoadJob?.cancel()
        maskFetchJob?.cancel()
        configRebuildJob?.cancel()
        stopDriftSync()
        isPlaying = false
        isLoading = false
        loadGeneration++
        cachedCid = 0L
        cachedAid = 0L
        cachedBvid = ""
        cachedDurationMs = 0L
        parsedSegments.clear()
        missingSegmentRetryAtMs = 0L
        activeSegmentIndices = emptyList()
        pendingSegmentIndices = emptyList()
        clearSpecialDanmakuWindow()
        localSegmentPaths = emptyList()
        isLocalSegmentSession = false
        webMaskBytes = null
        webMaskFps = 0
        webMaskWindowStartMs = Long.MIN_VALUE
        webMaskWindowEndMs = Long.MIN_VALUE
        cachedDanmakuList = null
        sourceDanmakuList = null
        sourceAdvancedDanmakuList = null
        sourceBasDanmakuList = null
        sourceCommandDanmakuList = emptyList()
        _advancedDanmakuFlow.value = emptyList()
        _basDanmakuFlow.value = emptyList()
        _commandDanmakuFlow.value = emptyList()
        timelineSyncedController = null
        pendingTimelineResync = false
        clearExplicitSeekResyncMarker()
        controller?.stop()
    }

    /**
     * 进入进度条拖动预览前，先暂停旧时间线，再清空屏幕上的旧弹幕。
     * 仅用于显式 seek scrub，避免用户拖动时继续看到旧时间线弹幕。
     */
    fun prepareForSeekScrub() {
        clearTapSelection()
        if (isSeekScrubbing) return
        isSeekScrubbing = true
        maskLoadJob?.cancel()
        webMaskWindowStartMs = Long.MIN_VALUE
        webMaskWindowEndMs = Long.MIN_VALUE
        val ctrl = controller ?: return
        executeDanmakuSeekScrubStart(
            pause = {
                ctrl.pause()
                isPlaying = false
                stopDriftSync()
            },
            clear = { ctrl.clear() }
        )
        Log.d(TAG, "🧹 prepareForSeekScrub() - paused and cleared stale danmakus")
    }

    /**
     * 进度条拖动取消后，按当前播放位置恢复弹幕时间轴。
     */
    fun cancelSeekScrub() {
        isSeekScrubbing = false
        val positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        cancelObsoleteWindowRequest(positionMs)
        requestSpecialDanmakuWindow(positionMs, "seek_scrub_cancel")
        val shouldPlay = player?.isPlaying == true && isRenderingEnabled
        if (shouldPlay) startDriftSync()
        Log.d(TAG, "↩️ cancelSeekScrub() - restoring danmaku at ${positionMs}ms, play=$shouldPlay")
        if (shouldReplaceDanmakuWindow(activeSegmentIndices, positionMs, totalSegmentCount)) {
            requestSegmentWindow(positionMs, "seek_scrub_cancel")
        } else cachedDanmakuList?.let { list ->
            resyncDanmakuTimeline(
                list = list,
                positionMs = positionMs,
                shouldPlay = shouldPlay,
                reason = "seek_scrub_cancel"
            )
        } ?: run {
            controller?.let { ctrl ->
                applyPlaybackSpeedToController(ctrl)
                if (shouldPlay) {
                    ctrl.start(positionMs)
                    isPlaying = true
                    startDriftSync()
                } else {
                    ctrl.pause()
                    isPlaying = false
                }
            }
        }
    }
    
    /**
     *  跳转到指定时间（拖动进度条完成时调用）
     * 会清除当前弹幕并从新位置开始显示
     * 
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Long) {
        clearTapSelection()
        isSeekScrubbing = false
        cancelObsoleteWindowRequest(positionMs)
        requestSpecialDanmakuWindow(positionMs, "manual_seek")
        Log.w(TAG, "⏭️ seekTo($positionMs) - refreshing danmaku")
        val shouldPlay = player?.isPlaying == true
        markExplicitSeekResync(positionMs, startedPlayback = shouldPlay)
        if (shouldReplaceDanmakuWindow(activeSegmentIndices, positionMs, totalSegmentCount)) {
            controller?.pause()
            controller?.clear()
            requestSegmentWindow(positionMs, "manual_seek")
        } else cachedDanmakuList?.let { list ->
            resyncDanmakuTimeline(
                list = list,
                positionMs = positionMs,
                shouldPlay = shouldPlay,
                reason = "manual_seek"
            )
            if (shouldPlay && isRenderingEnabled) {
                startDriftSync()
            }
            Log.w(TAG, "⏭️ Danmaku restarted at ${positionMs}ms")
        } ?: run {
            controller?.clear()
            Log.w(TAG, "⏭️ No cached danmaku, just cleared")
        }
    }

    fun recoverAfterForeground(
        positionMs: Long,
        playWhenReady: Boolean,
        playbackState: Int,
        preserveTimeline: Boolean = false
    ) {
        specialLoadingPausedForBackground = false
        requestSpecialDanmakuWindow(positionMs, "foreground_recovery")
        when (
            resolveDanmakuActionForForegroundRecovery(
                playWhenReady = playWhenReady,
                isPlayerPlaying = player?.isPlaying == true,
                playbackState = playbackState,
                danmakuEnabled = isRenderingEnabled,
                hasData = cachedDanmakuList != null,
                preserveTimeline = preserveTimeline,
                timelineAlreadySynced = controller != null && timelineSyncedController === controller &&
                    !pendingTimelineResync
            )
        ) {
            DanmakuSyncAction.HardResync -> {
                cachedDanmakuList?.let { list ->
                    resyncDanmakuTimeline(
                        list = list,
                        positionMs = positionMs,
                        shouldPlay = playWhenReady || player?.isPlaying == true,
                        reason = "foreground_recovery"
                    )
                    Log.w(TAG, "🌅 Danmaku foreground recovery resynced at ${positionMs}ms")
                }
            }
            DanmakuSyncAction.PauseOnly -> {
                controller?.pause()
                isPlaying = false
                stopDriftSync()
                Log.w(TAG, "🌅 Danmaku foreground recovery kept paused at end state")
            }
            DanmakuSyncAction.SoftResync -> {
                if (isSeekScrubbing) return
                danmakuView?.visibility = android.view.View.VISIBLE
                softResyncDanmakuTimeline(
                    positionMs = positionMs,
                    shouldPlay = playWhenReady || player?.isPlaying == true,
                    reason = "host_transition_recovery"
                )
                if (isPlaying) startDriftSync()
            }
            DanmakuSyncAction.None -> Unit
        }
    }
    
    /**
     * [新增] 添加本地弹幕（发送成功后立即显示）
     * 
     * 此方法用于在用户发送弹幕后立即将其显示在屏幕上，
     * 无需等待服务器刷新弹幕列表。
     * 
     * @param text 弹幕内容
     * @param color 弹幕颜色 (十进制 RGB，默认白色 16777215)
     * @param mode 弹幕模式: 1=滚动(默认), 4=底部, 5=顶部
     * @param fontSize 字号: 18=小, 25=中(默认), 36=大
     */
    fun addLocalDanmaku(
        text: String,
        color: Int = 16777215,
        mode: Int = 1,
        fontSize: Int = 25,
        isVipGradualColor: Boolean = false,
    ) {
        val currentPosition = player?.currentPosition ?: run {
            Log.w(TAG, "📝 addLocalDanmaku: player is null, cannot add danmaku")
            return
        }
        
        Log.d(TAG, "📝 addLocalDanmaku: text=$text, color=$color, mode=$mode, fontSize=$fontSize, position=${currentPosition}ms")
        
        val danmakuData = DanmakuItem().apply {
            //  [修复] 设置显示时间为当前播放位置 + 100ms 偏移
            // 这确保弹幕不会因为"已经过去"而被跳过
            showAtTime = currentPosition + 100L
            
            // 设置弹幕内容 - [修改] 使用『』包裹作为标记，更美观
            this.text = "『 $text 』"
            
            // 设置颜色 (ARGB 格式)
            textColor = color or 0xFF000000.toInt()
            this.isVipGradualColor = isVipGradualColor
            isSelf = true
            
            // 尝试设置边框/背景
            try {
                val greenBorder = 0xFF4CAF50.toInt()
                val clazz = this::class.java
                
                // 尝试多个可能的字段名 - 希望能命中一个
                // 1. borderColor (边框颜色)
                // 2. strokeColor (可能是文字描边，也可能是框) -> 先前尝试未生效或被覆盖
                // 3. backgroundColor (背景色)
                val fieldNames = listOf("borderColor", "backgroundColor", "backColor", "padding")
                
                for (name in fieldNames) {
                    try {
                        val field = clazz.getDeclaredField(name)
                        field.isAccessible = true
                        
                        if (name == "padding") {
                             field.setFloat(this, 10f)
                        } else {
                             field.setInt(this, greenBorder)
                        }
                        Log.d(TAG, "📝 Reflex set $name to Green/Value")
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                // 忽略
            }
            
            // 设置弹幕类型 - 使用库的常量
            layerType = resolveDanmakuRenderLayerType(
                type = mode,
                staticDanmakuToScroll = config.staticDanmakuToScroll
            )
            textSizeScale = resolveBilibiliDanmakuFontScale(fontSize.toFloat())
        }
        
        // 添加到缓存列表并排序
        // [核心修复] 必须按时间排序！渲染引擎依赖顺序数据，乱序会导致弹幕无法显示
        val visibleLocalDanmaku = applyDanmakuTypeFilters(listOf(danmakuData), emptyList()).first
        cachedDanmakuList = (cachedDanmakuList ?: emptyList()).plus(visibleLocalDanmaku).sortedBy { it.showAtTime }
        sourceDanmakuList = (sourceDanmakuList ?: emptyList()).plus(danmakuData).sortedBy { it.showAtTime }
        Log.d(TAG, "📝 Added to cache and sorted, total: ${cachedDanmakuList?.size} danmakus")
        
        val ctrl = controller
        if (isSeekScrubbing) {
            pendingTimelineResync = true
        } else if (ctrl != null && timelineSyncedController === ctrl && !pendingTimelineResync) {
            // Append only this new item to the sorted timeline. Keep active render
            // layers, playback state and clock intact for ordinary/send-same comments.
            ctrl.append(visibleLocalDanmaku)
            ctrl.invalidate()
        } else cachedDanmakuList?.let { list ->
            // First data/late attachment still needs an initial timeline installation.
            resyncDanmakuTimeline(
                list = list,
                positionMs = currentPosition,
                shouldPlay = player?.isPlaying == true,
                invalidateView = true,
                reason = "initialize_local"
            )
        }
        
        Log.d(TAG, "📝 Local danmaku added and displayed")
    }
    
    /**
     * 清除视图引用（防止内存泄漏）
     */
    private fun clearViewReference() {
        Log.d(TAG, " clearViewReference: Clearing all references")
        
        // 移除播放器监听器
        playerListener?.let { listener ->
            player?.removeListener(listener)
        }
        playerListener = null
        player = null
        playerAttachmentCount = 0
        
        // 停止弹幕
        controller?.close()
        controller = null
        renderTargets.forEach { it.releaseRenderer() }
        renderTargets.clear()
        renderPauseState.clear()
        danmakuView = null
        
        //  [修复] 重置尺寸记录
        lastAppliedWidth = 0
        lastAppliedHeight = 0
        
        // 取消加载任务
        loadJob?.cancel()
        loadJob = null
        windowLoadJob?.cancel()
        windowLoadJob = null
        ++specialWindowGeneration
        specialWindowLoadJob?.cancel()
        specialWindowLoadJob = null
        maskLoadJob?.cancel()
        maskLoadJob = null
        maskFetchJob?.cancel()
        maskFetchJob = null
        configRebuildJob?.cancel()
        configRebuildJob = null
        timelineSyncedController = null
        pendingTimelineResync = false
        
        // 🎬 [根本修复] 停止帧级同步
        stopDriftSync()
        
        isPlaying = false
        isLoading = false
        clearExplicitSeekResyncMarker()
        
        Log.d(TAG, " All references cleared")
    }

    fun trimCachesForBackground() {
        Log.d(TAG, " trimCachesForBackground: pausing drawing and prefetch")
        specialLoadingPausedForBackground = true
        windowLoadJob?.cancel()
        windowLoadJob = null
        ++windowGeneration
        pendingSegmentIndices = emptyList()
        configRebuildJob?.cancel()
        ++specialWindowGeneration
        specialWindowLoadJob?.cancel()
        specialWindowLoadJob = null
        specialWindowStartMs = Long.MIN_VALUE
        specialWindowEndMs = Long.MIN_VALUE
        specialDanmaku = ParsedDanmaku(emptyList(), emptyList())
        sourceAdvancedDanmakuList = activeSegmentIndices.mapNotNull(parsedSegments::get).flatMap { it.advancedList }
        sourceBasDanmakuList = activeSegmentIndices.mapNotNull(parsedSegments::get).flatMap { it.basList }
        _advancedDanmakuFlow.value = emptyList()
        _basDanmakuFlow.value = emptyList()
        val window = specialWindow
        if (window != null) scope.launch { specialWindowMutex.withLock { window.clearPrograms() } }
        maskLoadJob?.cancel()
        maskLoadJob = null
        webMaskWindowStartMs = Long.MIN_VALUE
        webMaskWindowEndMs = Long.MIN_VALUE
        maskFetchJob?.cancel()
        maskFetchJob = null
        rawDanmakuList = null
        controller?.pause()
        controller?.clear()
        // A trimmed renderer no longer owns the visible timeline. Foreground recovery
        // must replay it even when a host otherwise requests a lightweight clock sync.
        timelineSyncedController = null
        pendingTimelineResync = cachedDanmakuList != null
        stopDriftSync()
        isPlaying = false
    }

    /** 先捕获手指触及的弹幕，等 Compose 确认单击后打开菜单，避免双击等待期间弹幕移走。 */
    fun captureTap(
        source: androidx.compose.ui.layout.LayoutCoordinates?,
        position: androidx.compose.ui.geometry.Offset
    ): com.android.purebilibili.danmaku.engine.DanmakuClickCapture? {
        val view = danmakuView ?: return null
        val listener = danmakuClickListener ?: return null
        if (source == null || !source.isAttached || !view.isShown || !isRenderingEnabled) return null
        val windowPosition = source.localToWindow(position)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val x = windowPosition.x - location[0]
        val y = windowPosition.y - location[1]
        if (x < 0f || y < 0f || x >= view.width || y >= view.height) return null
        clearTapSelection()
        val click = controller?.captureClick(x, y) ?: return null
        val generation = loadGeneration
        isTapSelectionActive = true
        return com.android.purebilibili.danmaku.engine.DanmakuClickCapture(
            confirm = {
                if (danmakuView === view && danmakuClickListener === listener &&
                    loadGeneration == generation && isRenderingEnabled
                ) click() else click.cancel()
            },
            release = { click.cancel(); isTapSelectionActive = false },
        )
    }

    val tapSelectionOwner = Any()

    var isTapSelectionActive by mutableStateOf(false)
        private set

    fun clearTapSelection() {
        controller?.clearClickSelection()
        isTapSelectionActive = false
    }

    /**
     * 设置弹幕点击监听器
     *
     * @param listener 回调提供文本、身份、自发状态及窗口内锚点
     */
    fun setOnDanmakuClickListener(listener: ((String, Long, String, Boolean, DanmakuTapAnchor?) -> Unit)?) {
        clearTapSelection()
        danmakuClickListener = listener
        applyDanmakuClickListener()
    }

    private fun applyDanmakuClickListener() {
        val callback = danmakuClickListener
        if (callback == null) {
            controller?.setOnItemClickListener(null)
            return
        }
        controller?.let { ctrl ->
            try {
                ctrl.setOnItemClickListener { danmaku, rect, point ->
                    val text = danmaku.text.orEmpty()
                    val dmid = danmaku.danmakuId
                    val userHash = resolveDanmakuClickUserHash(danmaku.userHash)
                    val currentMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
                    val isSelf = danmaku.isSelf ||
                        resolveDanmakuClickIsSelf(userHash = userHash, currentMid = currentMid)
                    val view = danmakuView
                    val anchor = view?.let {
                        val location = IntArray(2)
                        it.getLocationInWindow(location)
                        DanmakuTapAnchor(
                            left = rect.left + location[0], top = rect.top + location[1],
                            right = rect.right + location[0], bottom = rect.bottom + location[1],
                            tapX = point.x + location[0],
                            owner = tapSelectionOwner,
                            likeCount = danmaku.likeCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                            viewportLeft = location[0].toFloat(), viewportTop = location[1].toFloat(),
                            viewportRight = (location[0] + it.width).toFloat(),
                            viewportBottom = (location[1] + it.height).toFloat(),
                        )
                    }
                    callback(text, dmid, userHash, isSelf, anchor)
                }
                Log.d(TAG, "setOnDanmakuClickListener set (DanmakuRenderEngine)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set listener", e)
            }
        }
    }



    /**
     * 释放所有资源
     */
    fun release() {
        clearTapSelection()
        Log.d(TAG, " release")
        updateHotDanmakuPresentation(emptyList(), 0)
        clearViewReference()
        loadJob?.cancel()
        loadJob = null
        loadGeneration++
        isLoading = false
        pluginObserverJob?.cancel()
        pluginObserverJob = null
        
        // 清除缓存
        cachedDanmakuList = null
        sourceDanmakuList = null
        sourceAdvancedDanmakuList = null
        sourceBasDanmakuList = null
        sourceCommandDanmakuList = emptyList()
        rawDanmakuList = null
        _advancedDanmakuFlow.value = emptyList()
        _basDanmakuFlow.value = emptyList()
        _commandDanmakuFlow.value = emptyList()
        cachedCid = 0L
        cachedAid = 0L
        cachedBvid = ""
        cachedDurationMs = 0L
        parsedSegments.clear()
        missingSegmentRetryAtMs = 0L
        activeSegmentIndices = emptyList()
        pendingSegmentIndices = emptyList()
        clearSpecialDanmakuWindow()
        localSegmentPaths = emptyList()
        isLocalSegmentSession = false
        webMaskBytes = null
        webMaskFps = 0
        webMaskWindowStartMs = Long.MIN_VALUE
        webMaskWindowEndMs = Long.MIN_VALUE
        clearExplicitSeekResyncMarker()
        scope.coroutineContext[Job]?.cancel()
        
        Log.d(TAG, " DanmakuManager fully released")
    }
}

internal fun createDanmakuManagerScope(sourceScope: CoroutineScope): CoroutineScope {
    val dispatcher = sourceScope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Main.immediate
    return CoroutineScope(dispatcher + Job())
}

/**
 * Composable 辅助函数：获取弹幕管理器实例
 */
@Composable
fun rememberDanmakuManager(playbackIdentity: Any): DanmakuManager {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val manager = remember(context.applicationContext, playbackIdentity) {
        val acquired = DanmakuSessionFactory.acquire(
            context = context,
            scope = scope,
            playbackIdentity = playbackIdentity
        )
        // 未提交的组合也必须归还引用，避免单例会话表持有数据和独立协程。
        AbandonableResource(acquired) {
            DanmakuSessionFactory.release(playbackIdentity, it)
        }
    }.value

    DisposableEffect(manager, playbackIdentity) {
        onDispose {
            DanmakuSessionFactory.release(playbackIdentity, manager)
        }
    }

    return manager
}

/**
 * 为会与普通播放器同时存在的播放容器创建独立弹幕会话。
 */
@Composable
fun rememberIsolatedDanmakuManager(sessionKey: Any): DanmakuManager {
    return rememberDanmakuManager(sessionKey)
}

internal fun resolveDanmakuRenderLayerType(
    type: Int,
    staticDanmakuToScroll: Boolean
): Int {
    if (staticDanmakuToScroll && (type == 4 || type == 5)) {
        return DANMAKU_LAYER_SCROLL
    }
    return when (type) {
        4 -> DANMAKU_LAYER_BOTTOM
        5 -> DANMAKU_LAYER_TOP
        6 -> DANMAKU_LAYER_REVERSE
        else -> DANMAKU_LAYER_SCROLL
    }
}
