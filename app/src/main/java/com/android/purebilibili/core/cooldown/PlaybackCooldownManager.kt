// 文件路径: core/cooldown/PlaybackCooldownManager.kt
package com.android.purebilibili.core.cooldown

import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.repository.videoPlaybackAccountScope

/**
 *  播放冷却管理器
 * 
 * 用于防止在遇到风控时过度重试，减轻风控评分。
 * 
 * 功能：
 * - 单视频冷却：同一 bvid 失败后，一段时间内不再请求
 * - 全局冷却：连续多个视频失败后，暂停所有请求
 * - 冷却状态查询：供 UI 显示倒计时和提示
 */
object PlaybackCooldownManager {
    
    private const val TAG = "CooldownManager"
    
    // ========== 配置 ==========
    
    /** 单视频冷却时长：1分钟 (原5分钟，放宽) */
    private const val SINGLE_VIDEO_COOLDOWN_MS = 60 * 1000L
    
    /** 全局冷却触发阈值：连续失败次数 (原3次，放宽到8次) */
    private const val GLOBAL_FAILURE_THRESHOLD = 8
    
    /** 全局冷却时长：2分钟 (原5分钟，放宽) */
    private const val GLOBAL_COOLDOWN_MS = 2 * 60 * 1000L
    
    /** 最大缓存失败视频数量（避免内存泄漏） */
    private const val MAX_FAILED_VIDEOS_CACHE = 50
    
    // ========== 状态 ==========
    
    /** 失败的视频记录：bvid -> 失败时间戳 */
    private val failedVideos = LinkedHashMap<String, Long>(
        MAX_FAILED_VIDEOS_CACHE, 0.75f, true // LRU 访问顺序
    )
    
    /** 最近失败次数记录：bvid -> 失败次数 */
    private val failureCounts = LinkedHashMap<String, Int>(
        MAX_FAILED_VIDEOS_CACHE, 0.75f, true
    )
    
    /** 连续失败计数 */
    private var consecutiveFailures = 0
    
    /** 全局冷却开始时间 */
    private var globalCooldownStart = 0L
    
    /** 上次成功的时间 */
    private var lastSuccessTime = 0L

    /** 当前冷却状态绑定的播放账号作用域；切换播放账号或登录状态时自动重置。 */
    private var currentAccountScope: String? = null

    internal var accountScopeProvider: () -> String = { videoPlaybackAccountScope() }
    internal var nowMsProvider: () -> Long = { System.currentTimeMillis() }

    private fun syncAccountScopeLocked() {
        val scope = accountScopeProvider()
        if (currentAccountScope != null && currentAccountScope != scope) {
            failedVideos.clear()
            failureCounts.clear()
            consecutiveFailures = 0
            globalCooldownStart = 0L
            Logger.d(TAG, "播放账号作用域已变更，重置冷却状态")
        }
        currentAccountScope = scope
    }
    
    // ========== Public API ==========
    
    /**
     * 记录视频加载失败
     * 
     * @param bvid 失败的视频 ID
     * @param reason 失败原因（用于日志）
     */
    @Synchronized
    fun recordFailure(bvid: String, reason: String = "") {
        syncAccountScopeLocked()
        val now = nowMsProvider()
        
        // 记录单视频失败
        val currentCount = (failureCounts[bvid] ?: 0) + 1
        failureCounts[bvid] = currentCount
        
        // 只有失败 3 次才触发单视频冷却
        if (currentCount >= 3) {
            failedVideos[bvid] = now
            Logger.w(TAG, "📛 触发视频冷却: bvid=$bvid, 失败次数=$currentCount")
        } else {
            Logger.w(TAG, "⚠️ 记录失败: bvid=$bvid, 次数=$currentCount/3")
        }
        
        // 清理过期的缓存
        cleanupExpiredCache(now)
        
        // 更新连续失败计数
        consecutiveFailures++
        
        Logger.w(TAG, "📛 记录失败: bvid=$bvid, reason=$reason, 连续失败=$consecutiveFailures")
        
        // 检查是否触发全局冷却
        if (consecutiveFailures >= GLOBAL_FAILURE_THRESHOLD && globalCooldownStart == 0L) {
            globalCooldownStart = now
            Logger.w(TAG, " 触发全局冷却！连续 $consecutiveFailures 个视频失败")
        }
    }
    
    /**
     * 记录视频加载成功
     * 
     * 成功加载后重置连续失败计数、全局冷却状态和单视频失败计数
     */
    @Synchronized
    fun recordSuccess() {
        syncAccountScopeLocked()
        if (consecutiveFailures > 0) {
            Logger.d(TAG, " 加载成功，重置失败计数 ($consecutiveFailures -> 0)")
        }
        consecutiveFailures = 0
        globalCooldownStart = 0L
        lastSuccessTime = nowMsProvider()
    }
    
    @Synchronized
    fun recordSuccess(bvid: String? = null) {
        syncAccountScopeLocked()
        if (consecutiveFailures > 0) {
            Logger.d(TAG, " 加载成功，重置失败计数 ($consecutiveFailures -> 0)")
        }
        consecutiveFailures = 0
        globalCooldownStart = 0L
        lastSuccessTime = nowMsProvider()
        
        bvid?.let {
            failureCounts.remove(it)
        }
    }
    
    /**
     * 检查是否可以请求指定视频
     * 
     * @param bvid 视频 ID
     * @return 冷却状态
     */
    @Synchronized
    fun getCooldownStatus(bvid: String): CooldownStatus {
        syncAccountScopeLocked()
        val now = nowMsProvider()
        
        // 1. 检查全局冷却
        if (globalCooldownStart > 0) {
            val elapsed = now - globalCooldownStart
            if (elapsed < GLOBAL_COOLDOWN_MS) {
                val remaining = GLOBAL_COOLDOWN_MS - elapsed
                Logger.d(TAG, "🌐 全局冷却中: 剩余 ${remaining / 1000}s")
                return CooldownStatus.GlobalCooldown(remaining)
            } else {
                // 全局冷却结束
                globalCooldownStart = 0L
                consecutiveFailures = 0
                Logger.d(TAG, "🌐 全局冷却结束")
            }
        }
        
        // 2. 检查单视频冷却
        val failedTime = failedVideos[bvid]
        if (failedTime != null) {
            val elapsed = now - failedTime
            if (elapsed < SINGLE_VIDEO_COOLDOWN_MS) {
                val remaining = SINGLE_VIDEO_COOLDOWN_MS - elapsed
                Logger.d(TAG, " 视频冷却中: bvid=$bvid, 剩余 ${remaining / 1000}s")
                return CooldownStatus.VideoCooldown(remaining, bvid)
            } else {
                // 冷却结束，移除记录
                failedVideos.remove(bvid)
            }
        }
        
        return CooldownStatus.Ready
    }
    
    /**
     * 快速检查：是否在冷却中
     */
    fun isCoolingDown(bvid: String): Boolean {
        return getCooldownStatus(bvid) !is CooldownStatus.Ready
    }
    
    /**
     * 清除所有冷却状态（用于用户手动清理缓存时）
     */
    @Synchronized
    fun clearAll() {
        syncAccountScopeLocked()
        failedVideos.clear()
        failureCounts.clear()
        consecutiveFailures = 0
        globalCooldownStart = 0L
        Logger.d(TAG, " 已清除所有冷却状态")
    }
    
    /**
     * 手动清除单个视频的冷却状态（用于用户主动重试）
     */
    @Synchronized
    fun clearForVideo(bvid: String) {
        syncAccountScopeLocked()
        failedVideos.remove(bvid)
        failureCounts.remove(bvid)
        Logger.d(TAG, " 已清除视频冷却状态: $bvid")
    }
    
    /**
     * 获取当前连续失败次数
     */
    @Synchronized
    fun getConsecutiveFailures(): Int {
        syncAccountScopeLocked()
        return consecutiveFailures
    }
    
    // ========== Private ==========
    
    /**
     * 清理过期的缓存记录
     */
    private fun cleanupExpiredCache(now: Long) {
        val iterator = failedVideos.entries.iterator()
        while (iterator.hasNext()) {
            val (_, failedTime) = iterator.next()
            if (now - failedTime > SINGLE_VIDEO_COOLDOWN_MS) {
                iterator.remove()
            }
        }
        
        // 如果缓存仍然过大，移除最旧的条目
        while (failedVideos.size > MAX_FAILED_VIDEOS_CACHE) {
            val oldestKey = failedVideos.keys.firstOrNull() ?: break
            failedVideos.remove(oldestKey)
        }
        
        // 同时也清理 failureCounts
        while (failureCounts.size > MAX_FAILED_VIDEOS_CACHE) {
             val oldestKey = failureCounts.keys.firstOrNull() ?: break
             failureCounts.remove(oldestKey)
        }
    }
}

/**
 * 冷却状态
 */
sealed class CooldownStatus {
    /** 可以请求 */
    object Ready : CooldownStatus()
    
    /** 单视频冷却中 */
    data class VideoCooldown(
        val remainingMs: Long,
        val bvid: String
    ) : CooldownStatus() {
        val remainingMinutes: Int get() = (remainingMs / 60_000).toInt()
        val remainingSeconds: Int get() = ((remainingMs % 60_000) / 1000).toInt()
    }
    
    /** 全局冷却中（连续多视频失败） */
    data class GlobalCooldown(
        val remainingMs: Long
    ) : CooldownStatus() {
        val remainingMinutes: Int get() = (remainingMs / 60_000).toInt()
        val remainingSeconds: Int get() = ((remainingMs % 60_000) / 1000).toInt()
    }
}
