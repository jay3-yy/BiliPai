@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.repository.DanmakuRepository
import com.android.purebilibili.data.repository.SponsorBlockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 播放器基类 ViewModel
 * 
 * 提供 VideoPlaybackViewModel 和 BangumiPlayerViewModel 共用的功能：
 * 1. ExoPlayer 管理
 * 2. 空降助手 (SponsorBlock) 逻辑
 * 3. DASH 视频播放
 * 4. 弹幕数据加载
 */
abstract class BasePlayerViewModel : ViewModel() {
    
    // ========== 播放器引用 ==========
    protected var exoPlayer: ExoPlayer? = null
    
    /**
     * 绑定播放器实例
     */
    open fun attachPlayer(player: ExoPlayer) {
        this.exoPlayer = player
        PlayerVolumeController.applyPreferredVolume(player)
    }
    
    /**
     * 获取播放器当前位置
     */
    fun getPlayerCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    
    /**
     * 获取播放器总时长
     */
    fun getPlayerDuration(): Long {
        val duration = exoPlayer?.duration ?: 0L
        return if (duration < 0) 0L else duration
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    // ========== 空降助手 (SponsorBlock) ==========
    
    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments.asStateFlow()
    
    private val _currentSponsorSegment = MutableStateFlow<SponsorSegment?>(null)
    val currentSponsorSegment: StateFlow<SponsorSegment?> = _currentSponsorSegment.asStateFlow()
    
    private val _showSkipButton = MutableStateFlow(false)
    val showSkipButton: StateFlow<Boolean> = _showSkipButton.asStateFlow()
    
    private val skippedSegmentIds = mutableSetOf<String>()
    
    /**
     * 加载空降片段
     */
    protected fun loadSponsorSegments(bvid: String) {
        viewModelScope.launch {
            try {
                val segments = SponsorBlockRepository.getSegments(bvid)
                _sponsorSegments.value = segments
                skippedSegmentIds.clear()
                Logger.d(TAG, " SponsorBlock: loaded ${segments.size} segments for $bvid")
            } catch (e: Exception) {
                Logger.w(TAG, " SponsorBlock: load failed: ${e.message}")
            }
        }
    }
    
    /**
     * 检查当前播放位置是否在空降片段内，并执行跳过逻辑
     * 
     * @param context 需要 Context 来读取设置
     * @return 是否执行了自动跳过
     */
    suspend fun checkAndSkipSponsor(context: Context): Boolean {
        val player = exoPlayer ?: return false
        val segments = _sponsorSegments.value
        if (segments.isEmpty()) return false
        
        val currentPos = player.currentPosition
        val segment = SponsorBlockRepository.findSegmentAtPosition(segments, currentPos)
        
        if (segment != null && segment.UUID !in skippedSegmentIds) {
            _currentSponsorSegment.value = segment
            
            val autoSkip = SettingsManager.getSponsorBlockAutoSkip(context).first()
            
            if (autoSkip) {
                player.seekTo(segment.endTimeMs)
                skippedSegmentIds.add(segment.UUID)
                _currentSponsorSegment.value = null
                _showSkipButton.value = false
                onSponsorSkipped(segment)
                return true
            } else {
                _showSkipButton.value = true
            }
        } else if (segment == null) {
            _currentSponsorSegment.value = null
            _showSkipButton.value = false
        }
        
        return false
    }
    
    /**
     * 手动跳过当前空降片段
     */
    fun skipCurrentSponsorSegment() {
        val segment = _currentSponsorSegment.value ?: return
        val player = exoPlayer ?: return
        
        player.seekTo(segment.endTimeMs)
        skippedSegmentIds.add(segment.UUID)
        _currentSponsorSegment.value = null
        _showSkipButton.value = false
        
        onSponsorSkipped(segment)
    }
    
    /**
     * 忽略当前空降片段（不跳过）
     */
    fun dismissSponsorSkipButton() {
        val segment = _currentSponsorSegment.value ?: return
        skippedSegmentIds.add(segment.UUID)
        _currentSponsorSegment.value = null
        _showSkipButton.value = false
    }
    
    /**
     * 重置空降片段状态（切换视频时调用）
     */
    protected fun resetSponsorState() {
        _sponsorSegments.value = emptyList()
        _currentSponsorSegment.value = null
        _showSkipButton.value = false
        skippedSegmentIds.clear()
    }
    
    /**
     * 空降片段被跳过后的回调（子类可覆盖以显示 toast 等）
     */
    protected open fun onSponsorSkipped(segment: SponsorSegment) {
        // 子类可覆盖
    }
    
    // ========== DASH 视频播放 ==========
    
    /**
     * 播放 DASH 格式视频（视频+音频分离）
     * 
     * @param videoUrl 视频流 URL
     * @param audioUrl 音频流 URL（可选）
     * @param seekToMs 开始播放位置（毫秒）
     * @param resetPlayer 是否重置播放器状态（默认true，切换清晰度时可设为false以减少闪烁）
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    protected fun playDashVideo(
        videoUrl: String, 
        audioUrl: String?, 
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com"
    ) {
        val player = exoPlayer
        if (player == null) {
            Logger.e(TAG, "❌ playDashVideo: exoPlayer is NULL! Cannot play video.")
            return
        }
        Logger.d(TAG, "▶️ playDashVideo: referer=$referer, seekTo=${seekToMs}ms, reset=$resetPlayer, video=${videoUrl.take(50)}...")
        
        PlayerVolumeController.applyPreferredVolume(player)
        
        val mediaSourceFactory = buildProgressiveMediaSourceFactory(referer)
        
        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUrl))
        
        val finalSource = if (!audioUrl.isNullOrEmpty()) {
            val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
        
        //  [修复] 使用 resetPosition=false 减少切换时的闪烁
        player.setMediaSource(finalSource, /* resetPosition = */ resetPlayer)
        player.prepare()
        if (seekToMs > 0) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = true
        Logger.d(TAG, "✅ playDashVideo: Player prepared and started, playWhenReady=true")
    }

    /**
     * 播放分段 durl 视频（多段 MP4）
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    protected fun playSegmentedVideo(
        segmentUrls: List<String>,
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com"
    ) {
        val player = exoPlayer ?: return
        val cleanUrls = segmentUrls.filter { it.isNotBlank() }
        if (cleanUrls.isEmpty()) return

        if (cleanUrls.size == 1) {
            playDashVideo(
                videoUrl = cleanUrls.first(),
                audioUrl = null,
                seekToMs = seekToMs,
                resetPlayer = resetPlayer,
                referer = referer
            )
            return
        }

        PlayerVolumeController.applyPreferredVolume(player)
        val mediaSourceFactory = buildProgressiveMediaSourceFactory(referer)
        val concatenated = ConcatenatingMediaSource().apply {
            cleanUrls.forEach { url ->
                addMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(url)))
            }
        }

        player.setMediaSource(concatenated, resetPlayer)
        player.prepare()
        if (seekToMs > 0) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = true
        Logger.d(TAG, "✅ playSegmentedVideo: segmentCount=${cleanUrls.size}, seekTo=${seekToMs}ms")
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildProgressiveMediaSourceFactory(referer: String): ProgressiveMediaSource.Factory {
        val headers = mapOf(
            "Referer" to referer,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val upstreamFactory = buildPlaybackUpstreamDataSourceFactory(NetworkModule.appContext, headers)
        val dataSourceFactory: DataSource.Factory = NetworkModule.appContext?.let { context ->
            PlaybackMediaCache.buildCachedDataSourceFactory(context, upstreamFactory)
        } ?: upstreamFactory

        // B站分段与 DASH 常用 fMP4/m4s，需要显式 extractor 配置保证可播
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        return ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
    }
    
    /**
     * 播放普通视频（单一 URL）
     * 
     * @param url 视频 URL
     * @param seekToMs 开始播放位置（毫秒）
     */
    protected fun playVideo(url: String, seekToMs: Long = 0L) {
        val player = exoPlayer ?: return
        Logger.d(TAG, " playVideo: seekTo=${seekToMs}ms, url=${url.take(50)}...")
        
        PlayerVolumeController.applyPreferredVolume(player)
        
        val mediaSource = buildProgressiveMediaSourceFactory("https://www.bilibili.com")
            .createMediaSource(MediaItem.fromUri(url))
        player.setMediaSource(mediaSource)
        player.prepare()
        if (seekToMs > 0) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = true
    }
    
    // ========== 弹幕数据 ==========
    
    private val _danmakuData = MutableStateFlow<ByteArray?>(null)
    val danmakuData: StateFlow<ByteArray?> = _danmakuData.asStateFlow()
    
    /**
     * 加载弹幕数据
     */
    protected fun loadDanmaku(cid: Long) {
        viewModelScope.launch {
            val data = DanmakuRepository.getDanmakuRawData(cid)
            if (data != null) {
                _danmakuData.value = data
                Logger.d(TAG, "📝 Danmaku loaded: ${data.size} bytes for cid=$cid")
            }
        }
    }
    
    /**
     * 清除弹幕数据
     */
    protected fun clearDanmaku() {
        _danmakuData.value = null
    }
    
    // ========== 生命周期 ==========
    
    override fun onCleared() {
        super.onCleared()
        exoPlayer = null
    }
    
    companion object {
        private const val TAG = "BasePlayerVM"
    }
}
