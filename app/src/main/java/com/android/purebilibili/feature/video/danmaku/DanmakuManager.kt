// 文件路径: feature/video/danmaku/DanmakuManager.kt
package com.android.purebilibili.feature.video.danmaku

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bytedance.danmaku.render.engine.DanmakuView
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.data.DanmakuData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 弹幕管理器（单例模式）
 * 
 * 使用 ByteDance DanmakuRenderEngine 重构
 * 
 * 负责：
 * 1. 加载和解析弹幕数据
 * 2. 与 ExoPlayer 同步弹幕播放
 * 3. 管理弹幕视图生命周期
 * 
 * 使用单例模式确保横竖屏切换时保持弹幕状态
 */
class DanmakuManager private constructor(
    private val context: Context,
    private var scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DanmakuManager"
        
        @Volatile
        private var instance: DanmakuManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(context: Context, scope: CoroutineScope): DanmakuManager {
            return instance ?: synchronized(this) {
                instance ?: DanmakuManager(context.applicationContext, scope).also { 
                    instance = it 
                    Log.d(TAG, " DanmakuManager instance created")
                }
            }
        }
        
        /**
         * 更新 CoroutineScope（用于配置变化时）
         */
        fun updateScope(scope: CoroutineScope) {
            instance?.scope = scope
        }
        
        /**
         * 释放单例实例
         */
        fun clearInstance() {
            instance?.release()
            instance = null
            Log.d(TAG, " DanmakuManager instance cleared")
        }
    }
    
    // 视图和控制器
    private var danmakuView: DanmakuView? = null
    private var controller: DanmakuController? = null
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var loadJob: Job? = null
    
    // 弹幕状态
    private var isPlaying = false
    private var isLoading = false
    
    // 缓存解析后的弹幕数据（横竖屏切换时复用）
    private var cachedDanmakuList: List<DanmakuData>? = null
    private var cachedCid: Long = 0L
    
    //  [新增] 记录原始弹幕滚动时间（用于倍速同步）
    private var originalMoveTime: Long = 8000L  // 默认 8 秒
    private var currentVideoSpeed: Float = 1.0f
    
    // 配置
    val config = DanmakuConfig()
    
    // 便捷属性访问器
    var isEnabled: Boolean
        get() = config.isEnabled
        set(value) {
            config.isEnabled = value
            if (value) show() else hide()
        }
    
    var opacity: Float
        get() = config.opacity
        set(value) {
            config.opacity = value
            applyConfigToController("opacity")
        }
    
    var fontScale: Float
        get() = config.fontScale
        set(value) {
            config.fontScale = value
            applyConfigToController("fontScale")
        }
    
    var speedFactor: Float
        get() = config.speedFactor
        set(value) {
            config.speedFactor = value
            applyConfigToController("speedFactor")
        }
    
    var displayArea: Float
        get() = config.displayAreaRatio
        set(value) {
            config.displayAreaRatio = value
            applyConfigToController("displayArea")
        }
    
    /**
     *  批量更新弹幕设置（实时生效）
     */
    fun updateSettings(
        opacity: Float = this.opacity,
        fontScale: Float = this.fontScale,
        speed: Float = this.speedFactor,
        displayArea: Float = this.displayArea
    ) {
        config.opacity = opacity
        config.fontScale = fontScale
        config.speedFactor = speed
        config.displayAreaRatio = displayArea
        applyConfigToController("batch")
    }

    /**
     * 应用弹幕配置到 Controller，并同步倍速基准
     *  [修复] fontScale/displayArea 改变时重新设置数据，让新配置生效
     */
    private fun applyConfigToController(reason: String) {
        controller?.let { ctrl ->
            config.applyTo(ctrl.config)

            // 记录设置后的基准滚动时间，供倍速同步使用
            originalMoveTime = ctrl.config.scroll.moveTime

            // 若视频非 1.0x，则按倍速调整弹幕滚动时间
            if (currentVideoSpeed != 1.0f) {
                ctrl.config.scroll.moveTime = (originalMoveTime / currentVideoSpeed).toLong()
            }

            //  [关键修复] fontScale/displayArea 改变时，需要重新设置弹幕数据
            // 因为引擎的 config.text.size 只对新弹幕生效，已显示的弹幕不会更新
            if (reason == "fontScale" || reason == "displayArea" || reason == "batch") {
                cachedDanmakuList?.let { list ->
                    val currentPos = player?.currentPosition ?: 0L
                    Log.w(TAG, " Re-applying danmaku data after $reason change at ${currentPos}ms")
                    ctrl.setData(list, 0)
                    ctrl.start(currentPos)
                    if (player?.isPlaying != true) {
                        ctrl.pause()
                    }
                }
            } else {
                ctrl.invalidateView()
            }
            
            Log.w(
                TAG,
                " Config applied ($reason): opacity=${config.opacity}, fontScale=${config.fontScale}, " +
                    "speed=${config.speedFactor}, area=${config.displayAreaRatio}, " +
                    "baseMoveTime=$originalMoveTime, videoSpeed=$currentVideoSpeed, " +
                    "moveTime=${ctrl.config.scroll.moveTime}"
            )
        }
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
    fun attachView(view: DanmakuView) {
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
            applyDanmakuDataToController()
            return
        }
        
        Log.w(TAG, "📎 attachView: new view, old=${danmakuView != null}, hashCode=${view.hashCode()}")
        
        danmakuView = view
        controller = view.controller
        
        Log.w(TAG, "📎 controller obtained: ${controller != null}")
        
        // 内置渲染层（ScrollLayer, TopCenterLayer, BottomCenterLayer）由 DanmakuRenderEngine 自动注册
        // 不需要手动添加，手动添加会报错 "The custom LayerType must not be less than 2000"
        
        // 应用配置并同步倍速基准
        applyConfigToController("attachView")
        
        //  [关键修复] 等待 View 布局完成后再设置弹幕数据
        // DanmakuRenderEngine 需要有效的 View 尺寸来计算弹幕轨道位置
        if (hasValidSize) {
            // View 已经有有效尺寸，直接设置数据
            Log.w(TAG, "📎 View has valid size, setting data immediately")
            lastAppliedWidth = view.width
            lastAppliedHeight = view.height
            applyDanmakuDataToController()
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
                        applyDanmakuDataToController()
                    } else if (danmakuView === view) {
                        //  [修复] 如果布局回调时尺寸仍为 0，延迟 100ms 再试一次
                        Log.w(TAG, " View still zero size, scheduling delayed retry...")
                        view.postDelayed({
                            if (danmakuView === view && view.width > 0 && view.height > 0) {
                                Log.w(TAG, "📎 Delayed retry: width=${view.width}, height=${view.height}")
                                lastAppliedWidth = view.width
                                lastAppliedHeight = view.height
                                applyDanmakuDataToController()
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
            controller?.setData(list, 0)
            Log.w(TAG, "📎 setData completed")
            
            // 强制刷新视图
            controller?.invalidateView()
            Log.w(TAG, "📎 invalidateView called")
            
            // 同步到当前播放位置
            player?.let { p ->
                val position = p.currentPosition
                Log.w(TAG, "📎 Player state: isPlaying=${p.isPlaying}, isEnabled=${config.isEnabled}, position=${position}ms")
                
                //  [修复] 始终先 start 到当前位置，让 controller 知道视频在哪里
                controller?.start(position)
                Log.w(TAG, " controller.start($position) called")
                
                if (p.isPlaying && config.isEnabled) {
                    isPlaying = true
                    Log.w(TAG, " Danmaku playing")
                } else {
                    // 如果视频暂停中，也暂停弹幕
                    controller?.pause()
                    isPlaying = false
                    Log.w(TAG, " Danmaku paused (player not playing)")
                }
            } ?: Log.w(TAG, "📎 Player is null, not syncing")
        } ?: Log.w(TAG, "📎 No cached danmaku list to apply")
    }
    
    /**
     * 解绑 DanmakuView（不释放弹幕数据）
     */
    fun detachView() {
        Log.d(TAG, "📎 detachView: Pausing and clearing controller")
        controller?.pause()
        controller = null
        danmakuView = null
    }
    
    /**
     * 绑定 ExoPlayer
     */
    fun attachPlayer(exoPlayer: ExoPlayer) {
        Log.d(TAG, " attachPlayer")
        
        // 移除旧监听器
        playerListener?.let { player?.removeListener(it) }
        
        player = exoPlayer
        
        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayerPlaying: Boolean) {
                Log.w(TAG, " onIsPlayingChanged: isPlaying=$isPlayerPlaying, isEnabled=${config.isEnabled}, hasData=${cachedDanmakuList != null}")
                
                if (isPlayerPlaying && config.isEnabled) {
                    //  [修复] 只有当数据已加载时才启动弹幕
                    if (cachedDanmakuList != null) {
                        val position = exoPlayer.currentPosition
                        controller?.start(position)
                        isPlaying = true
                        Log.w(TAG, " Danmaku STARTED at ${position}ms")
                    } else {
                        Log.w(TAG, " Player playing but danmaku data not loaded yet, will start after load")
                        // 数据加载完成后会自动 start
                    }
                } else if (!isPlayerPlaying) {
                    // 暂停 - DanmakuRenderEngine 的 pause() 会让弹幕停在原地
                    controller?.pause()
                    isPlaying = false
                    Log.w(TAG, " Danmaku PAUSED (danmakus stay in place)")
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, " onPlaybackStateChanged: state=$playbackState")
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (exoPlayer.isPlaying && config.isEnabled) {
                            val position = exoPlayer.currentPosition
                            controller?.start(position)
                            isPlaying = true
                        }
                    }
                    Player.STATE_ENDED -> {
                        // 视频结束时暂停弹幕（保持在屏幕上）
                        controller?.pause()
                        isPlaying = false
                    }
                    Player.STATE_BUFFERING -> {
                        // 缓冲时暂停弹幕
                        if (isPlaying) {
                            controller?.pause()
                            Log.d(TAG, " Buffering, danmaku paused")
                        }
                    }
                }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    Log.w(TAG, " Seek detected: ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms")
                    
                    //  关键修复：Seek 时重新调用 setData(list, 0) + start(newPosition)
                    cachedDanmakuList?.let { list ->
                        Log.w(TAG, " Re-setting data with playTime=0, then start at ${newPosition.positionMs}ms")
                        controller?.setData(list, 0)  // 始终用 0 作为基准
                        controller?.start(newPosition.positionMs)  // 用实际位置启动
                        
                        if (exoPlayer.isPlaying && config.isEnabled) {
                            isPlaying = true
                            Log.w(TAG, " Danmaku restarted at ${newPosition.positionMs}ms")
                        } else {
                            controller?.pause()
                            isPlaying = false
                            Log.w(TAG, " Danmaku paused after seek (player not playing)")
                        }
                    } ?: run {
                        controller?.clear()
                        Log.w(TAG, " No cached danmaku, just cleared screen")
                    }
                }
            }
            
            //  [新增] 视频倍速变化时同步弹幕速度
            //  [问题10修复] 优化长按加速视频时的弹幕同步
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                val videoSpeed = playbackParameters.speed
                Log.w(TAG, "⏩ onPlaybackParametersChanged: videoSpeed=$videoSpeed, previous=$currentVideoSpeed")
                
                //  同步弹幕速度：视频 2x 时，弹幕也需要 2 倍速滚动
                // 通过减少 moveTime 来加快弹幕滚动
                if (videoSpeed != currentVideoSpeed) {
                    val previousSpeed = currentVideoSpeed
                    currentVideoSpeed = videoSpeed
                    
                    controller?.let { ctrl ->
                        // 根据视频倍速调整弹幕滚动时间
                        // 视频 2x 倍速 = 弹幕滚动时间减半
                        val adjustedMoveTime = (originalMoveTime / videoSpeed).toLong()
                        ctrl.config.scroll.moveTime = adjustedMoveTime
                        
                        // [问题10修复] 当从加速恢复到正常速度时，重新同步弹幕位置
                        // 这防止长按快进后弹幕位置不同步
                        if (previousSpeed > 1.0f && videoSpeed == 1.0f) {
                            val currentPos = exoPlayer.currentPosition
                            Log.w(TAG, "⏩ Speed returned to normal, resyncing danmaku at ${currentPos}ms")
                            cachedDanmakuList?.let { list ->
                                ctrl.setData(list, 0)
                                ctrl.start(currentPos)
                            }
                        }
                        
                        ctrl.invalidateView()
                        Log.w(TAG, "⏩ Danmaku moveTime: original=$originalMoveTime, adjusted=$adjustedMoveTime (video=${videoSpeed}x)")
                    }
                }
            }
        }
        
        exoPlayer.addListener(playerListener!!)
    }
    
    /**
     * 加载弹幕数据
     * 
     * @param cid 视频 cid
     * @param durationMs 视频时长 (毫秒)，用于计算 Protobuf 分段数。如果为 0，则回退到 XML API
     */
    fun loadDanmaku(cid: Long, durationMs: Long = 0L) {
        Log.w(TAG, "========== loadDanmaku CALLED cid=$cid, duration=${durationMs}ms ==========")
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
            val currentPos = player?.currentPosition ?: 0L
            Log.w(TAG, " Using cached danmaku list (${cachedDanmakuList!!.size} items) for cid=$cid, position=${currentPos}ms")
            
            //  [修复] 仿照 Seek 处理器的模式：先用 0 设置基准，再用 currentPos 启动
            controller?.setData(cachedDanmakuList!!, 0)  // 基准时间 0
            controller?.start(currentPos)  // 跳到当前位置
            Log.w(TAG, " Cached data: setData(0) + start(${currentPos}ms)")
            
            player?.let { p ->
                if (p.isPlaying && config.isEnabled) {
                    isPlaying = true
                    Log.w(TAG, " Player playing, danmaku active")
                } else {
                    controller?.pause()
                    isPlaying = false
                    Log.w(TAG, " Player paused, danmaku paused")
                }
            }
            return
        }
        
        // 需要从网络加载新 cid 的弹幕
        Log.w(TAG, " loadDanmaku: New cid=$cid, loading from network")
        isLoading = true
        cachedCid = cid
        cachedDanmakuList = null
        
        // 清除现有弹幕
        controller?.stop()
        
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val (segments, rawData) = withContext(Dispatchers.IO) {
                    var segmentList: List<ByteArray>? = null
                    var xmlData: ByteArray? = null
                    
                    //  [新增] 优先使用 Protobuf API (seg.so)
                    if (durationMs > 0) {
                        Log.w(TAG, " Trying Protobuf API (seg.so)...")
                        try {
                            val fetched = com.android.purebilibili.data.repository.DanmakuRepository.getDanmakuSegments(cid, durationMs)
                            if (fetched.isNotEmpty()) {
                                segmentList = fetched
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, " Protobuf API failed: ${e.message}, falling back to XML")
                        }
                    }
                    
                    //  [后备] 如果 Protobuf 失败或未提供 duration，使用 XML API
                    if (segmentList.isNullOrEmpty()) {
                        Log.w(TAG, " Trying XML API (fallback)...")
                        xmlData = com.android.purebilibili.data.repository.DanmakuRepository.getDanmakuRawData(cid)
                    }
                    
                    Pair(segmentList, xmlData)
                }
                
                val danmakuList = withContext(Dispatchers.Default) {
                    when {
                        !segments.isNullOrEmpty() -> {
                            val parsed = DanmakuParser.parseProtobuf(segments)
                            Log.w(TAG, " Protobuf parsed ${parsed.size} danmakus")
                            parsed
                        }
                        rawData != null && rawData.isNotEmpty() -> {
                            val parsed = DanmakuParser.parse(rawData)
                            Log.w(TAG, " XML parsed ${parsed.size} danmakus")
                            parsed
                        }
                        else -> emptyList()
                    }
                }
                
                if (danmakuList.isEmpty()) {
                    Log.w(TAG, " No danmaku data available for cid=$cid")
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                    return@launch
                }
                
                cachedDanmakuList = danmakuList
                Log.w(TAG, " Final: ${danmakuList.size} danmakus for cid=$cid")
                
                withContext(Dispatchers.Main) {
                    isLoading = false
                    
                    //  [核心修复] 仿照 Seek 处理器的模式
                    val currentPlayTime = player?.currentPosition ?: 0L
                    Log.w(TAG, "📎 View size: width=${danmakuView?.width}, height=${danmakuView?.height}")
                    
                    //  [核心修复] 先用 0 作为基准设置数据，再用实际位置启动
                    // 这与 Seek 处理器的模式一致，确保引擎知道完整的时间线
                    Log.w(TAG, "📎 Calling setData with ${danmakuList.size} items, playTime=0 (base)")
                    controller?.setData(danmakuList, 0)  // 基准时间 0
                    Log.w(TAG, "📎 setData completed")
                    
                    //  [关键] 强制刷新视图 - 与横竖屏切换路径一致
                    controller?.invalidateView()
                    Log.w(TAG, "📎 invalidateView called")
                    
                    // start 同步到当前位置
                    controller?.start(currentPlayTime)
                    Log.w(TAG, " controller.start($currentPlayTime) called - video is at this position")
                    
                    // 如果 player 暂停中，也暂停 controller
                    if (player?.isPlaying != true) {
                        controller?.pause()
                        isPlaying = false
                        Log.w(TAG, " Player not playing, controller paused")
                    } else {
                        isPlaying = true
                        Log.w(TAG, " Player is playing, danmaku active")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, " Failed to load danmaku for cid=$cid: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
    
    fun show() {
        Log.d(TAG, "👁️ show()")
        danmakuView?.visibility = android.view.View.VISIBLE
        
        if (player?.isPlaying == true) {
            val position = player?.currentPosition ?: 0L
            controller?.start(position)
            isPlaying = true
        }
    }
    
    fun hide() {
        Log.d(TAG, "🙈 hide()")
        controller?.pause()
        danmakuView?.visibility = android.view.View.GONE
        isPlaying = false
    }
    
    /**
     *  清除当前显示的弹幕（拖动进度条时调用）
     */
    fun clear() {
        Log.d(TAG, "🧹 clear() - clearing displayed danmakus")
        controller?.clear()
    }
    
    /**
     *  跳转到指定时间（拖动进度条完成时调用）
     * 会清除当前弹幕并从新位置开始显示
     * 
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Long) {
        Log.w(TAG, "⏭️ seekTo($positionMs) - refreshing danmaku")
        
        cachedDanmakuList?.let { list ->
            // 先清除当前显示的弹幕
            controller?.clear()
            // 重新设置数据基准
            controller?.setData(list, 0)
            // 从新位置开始
            controller?.start(positionMs)
            
            // 根据播放器状态决定是否暂停
            if (player?.isPlaying == true && config.isEnabled) {
                isPlaying = true
                Log.w(TAG, "⏭️ Danmaku restarted at ${positionMs}ms")
            } else {
                controller?.pause()
                isPlaying = false
                Log.w(TAG, "⏭️ Danmaku paused at ${positionMs}ms (player not playing)")
            }
        } ?: run {
            controller?.clear()
            Log.w(TAG, "⏭️ No cached danmaku, just cleared")
        }
    }
    
    /**
     * 清除视图引用（防止内存泄漏）
     */
    fun clearViewReference() {
        Log.d(TAG, " clearViewReference: Clearing all references")
        
        // 移除播放器监听器
        playerListener?.let { listener ->
            player?.removeListener(listener)
        }
        playerListener = null
        player = null
        
        // 停止弹幕
        controller?.stop()
        controller = null
        danmakuView = null
        
        //  [修复] 重置尺寸记录
        lastAppliedWidth = 0
        lastAppliedHeight = 0
        
        // 取消加载任务
        loadJob?.cancel()
        loadJob = null
        
        isPlaying = false
        isLoading = false
        
        Log.d(TAG, " All references cleared")
    }
    
    /**
     * 释放所有资源
     */
    fun release() {
        Log.d(TAG, " release")
        clearViewReference()
        
        // 清除缓存
        cachedDanmakuList = null
        cachedCid = 0L
        
        Log.d(TAG, " DanmakuManager fully released")
    }
}

/**
 * Composable 辅助函数：获取弹幕管理器实例
 */
@Composable
fun rememberDanmakuManager(): DanmakuManager {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val manager = remember { 
        DanmakuManager.getInstance(context, scope) 
    }
    
    // 确保 scope 是最新的
    DisposableEffect(scope) {
        DanmakuManager.updateScope(scope)
        onDispose { }
    }
    
    return manager
}
