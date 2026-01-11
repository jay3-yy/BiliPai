// File: feature/video/PlayerViewModel.kt
//  [重构] 简化版 PlayerViewModel - 使用 UseCase 层
package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.feature.video.usecase.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.cache.PlayUrlCache
import com.android.purebilibili.core.cooldown.PlaybackCooldownManager
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.SkipAction
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.feature.video.controller.QualityManager
import com.android.purebilibili.feature.video.controller.QualityPermissionResult
import com.android.purebilibili.feature.video.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.feature.video.player.PlayMode

// ========== UI State ==========
sealed class PlayerUiState {
    data class Loading(
        val retryAttempt: Int = 0,
        val maxAttempts: Int = 4,
        val message: String = "\u52a0\u8f7d\u4e2d..."
    ) : PlayerUiState() {
        companion object { val Initial = Loading() }
    }
    
    data class Success(
        val info: ViewInfo,
        val playUrl: String,
        val audioUrl: String? = null,
        val related: List<RelatedVideo> = emptyList(),
        val currentQuality: Int = 64,
        val qualityLabels: List<String> = emptyList(),
        val qualityIds: List<Int> = emptyList(),
        val startPosition: Long = 0L,
        val cachedDashVideos: List<DashVideo> = emptyList(),
        val cachedDashAudios: List<DashAudio> = emptyList(),
        val isQualitySwitching: Boolean = false,
        val requestedQuality: Int? = null,
        val isLoggedIn: Boolean = false,
        val isVip: Boolean = false,
        val isFollowing: Boolean = false,
        val isFavorited: Boolean = false,
        val isLiked: Boolean = false,
        val coinCount: Int = 0,
        val emoteMap: Map<String, String> = emptyMap(),
        val isInWatchLater: Boolean = false,  //  稍后再看状态
        val followingMids: Set<Long> = emptySet(),  //  已关注用户 ID 列表
        val videoTags: List<VideoTag> = emptyList(),  //  视频标签列表
        //  CDN 线路切换
        val currentCdnIndex: Int = 0,  // 当前使用的 CDN 索引 (0=主线路)
        val allVideoUrls: List<String> = emptyList(),  // 所有可用视频 URL (主+备用)
        val allAudioUrls: List<String> = emptyList(),   // 所有可用音频 URL (主+备用)
        // 🖼️ [新增] 视频预览图数据（用于进度条拖动预览）
        val videoshotData: VideoshotData? = null
    ) : PlayerUiState() {
        val cdnCount: Int get() = allVideoUrls.size.coerceAtLeast(1)
        val currentCdnLabel: String get() = "线路${currentCdnIndex + 1}"
    }
    
    data class Error(
        val error: VideoLoadError,
        val canRetry: Boolean = true
    ) : PlayerUiState() {
        val msg: String get() = error.toUserMessage()
    }
}

// ========== ViewModel ==========
class PlayerViewModel : ViewModel() {
    // UseCases
    private val playbackUseCase = VideoPlaybackUseCase()
    private val interactionUseCase = VideoInteractionUseCase()
    private val qualityManager = QualityManager()
    
    //  插件系统（替代旧的SponsorBlockUseCase）
    private var pluginCheckJob: Job? = null
    
    // State
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading.Initial)
    val uiState = _uiState.asStateFlow()
    
    private val _toastEvent = Channel<String>()
    val toastEvent = _toastEvent.receiveAsFlow()
    
    // Celebration animations
    private val _likeBurstVisible = MutableStateFlow(false)
    val likeBurstVisible = _likeBurstVisible.asStateFlow()
    
    private val _tripleCelebrationVisible = MutableStateFlow(false)
    val tripleCelebrationVisible = _tripleCelebrationVisible.asStateFlow()
    
    // Coin dialog
    private val _coinDialogVisible = MutableStateFlow(false)
    val coinDialogVisible = _coinDialogVisible.asStateFlow()
    
    //  SponsorBlock (via Plugin)
    private val _showSkipButton = MutableStateFlow(false)
    val showSkipButton = _showSkipButton.asStateFlow()
    private val _currentSkipReason = MutableStateFlow<String?>( null)
    val currentSkipReason = _currentSkipReason.asStateFlow()
    
    //  Download state
    private val _downloadProgress = MutableStateFlow(-1f)
    val downloadProgress = _downloadProgress.asStateFlow()
    
    //  [新增] 视频章节/看点数据
    private val _viewPoints = MutableStateFlow<List<ViewPoint>>(emptyList())
    val viewPoints = _viewPoints.asStateFlow()
    
    // Internal state
    private var currentBvid = ""
    private var currentCid = 0L
    private var exoPlayer: ExoPlayer? = null
    private var heartbeatJob: Job? = null
    private var appContext: android.content.Context? = null  //  [新增] 保存 Context 用于网络检测
    
    //  Public Player Accessor
    val currentPlayer: Player?
        get() = exoPlayer
        
    /**
     *  UI 仅音频模式状态
     * 
     * 注意：这与 SettingsManager.MiniPlayerMode.BACKGROUND 是两个不同的概念：
     * - isInAudioMode: UI 层的仅音频显示模式，用户主动切换，显示音频播放界面
     * - MiniPlayerMode.BACKGROUND: 设置层的后台音频模式，应用退到后台时的行为
     * 
     * isInAudioMode 控制 UI 显示，MiniPlayerMode.BACKGROUND 控制后台行为
     */
    private val _isInAudioMode = MutableStateFlow(false)
    val isInAudioMode = _isInAudioMode.asStateFlow()
    
    fun setAudioMode(enabled: Boolean) {
        _isInAudioMode.value = enabled
    }

    //  Sleep Timer State
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()
    private var sleepTimerJob: Job? = null

    /**
     * 设置定时关闭
     * @param minutes 分钟数，null 表示关闭定时
     */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        
        if (minutes != null) {
            sleepTimerJob = viewModelScope.launch {
                Logger.d("PlayerVM", "⏰ 定时关闭已启动: ${minutes}分钟")
                toast("将在 ${minutes} 分钟后停止播放")
                delay(minutes * 60 * 1000L)
                
                // 定时结束
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    exoPlayer?.pause()
                    toast("⏰ 定时结束，已暂停播放")
                    _sleepTimerMinutes.value = null
                    // 如果需要关闭应用或退出页面，可以在这里添加逻辑
                }
            }
        } else {
            Logger.d("PlayerVM", "⏰ 定时关闭已取消")
            toast("定时关闭已取消")
        }
    }
    
    // ========== Public API ==========
    
    /**
     * 初始化持久化存储（需要在使用前调用一次）
     */
    fun initWithContext(context: android.content.Context) {
        appContext = context.applicationContext  //  [新增] 保存应用 Context
        playbackUseCase.initWithContext(context)
    }
    
    fun attachPlayer(player: ExoPlayer) {
        val changed = exoPlayer !== player
        if (changed && exoPlayer != null) saveCurrentPosition()
        exoPlayer = player
        playbackUseCase.attachPlayer(player)
        player.volume = 1.0f
        
        //  [新增] 添加播放完成监听器
        player.addListener(playbackEndListener)
    }
    
    //  [新增] 播放完成监听器
    private val playbackEndListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                // 🔧 [修复] 检查自动播放设置 - 使用 SettingsManager 同步读取
                val context = appContext ?: return
                val autoPlayEnabled = com.android.purebilibili.core.store.SettingsManager
                    .getAutoPlaySync(context)
                
                if (autoPlayEnabled) {
                    // 🎵 [修复] 优先播放下一个分P，没有分P时再播放推荐视频
                    playNextPageOrRecommended()
                } else {
                    // 自动播放关闭，只显示提示
                    // 播放器应该保持在完成状态，这样播放按钮可以重新开始播放
                    toast(" 播放完成")
                }
            }
        }
    }
    
    /**
     *  [新增] 自动播放推荐视频（使用 PlaylistManager）
     */
    fun playNextRecommended() {
        // 使用 PlaylistManager 获取下一曲
        val nextItem = PlaylistManager.playNext()
        
        if (nextItem != null) {
            viewModelScope.launch {
                toast("正在播放: ${nextItem.title}")
            }
            // 加载新视频
            loadVideo(nextItem.bvid)
        } else {
            // 根据播放模式显示不同提示
            val mode = PlaylistManager.playMode.value
            when (mode) {
                PlayMode.SEQUENTIAL -> toast(" 播放列表结束")
                PlayMode.REPEAT_ONE -> {
                    // 单曲循环：重新播放当前视频
                    exoPlayer?.seekTo(0)
                    exoPlayer?.play()
                }
                else -> toast("没有更多视频")
            }
        }
    }
    
    /**
     * 🎵 [新增] 优先播放下一个分P，如果没有分P则检查合集，最后播放推荐视频
     * 用于分集视频（如音乐合集）的连续播放
     * 优先级: 分P > 合集下一集 > 推荐视频
     */
    fun playNextPageOrRecommended() {
        val current = _uiState.value as? PlayerUiState.Success ?: run {
            // 如果当前没有成功状态，直接播放推荐
            playNextRecommended()
            return
        }
        
        // 1. 优先检查分P
        val pages = current.info.pages
        if (pages.size > 1) {
            val currentPageIndex = pages.indexOfFirst { it.cid == currentCid }
            val nextPageIndex = currentPageIndex + 1
            
            if (nextPageIndex < pages.size) {
                // 播放下一个分P
                val nextPage = pages[nextPageIndex]
                Logger.d("PlayerVM", "🎵 播放下一个分P: P${nextPageIndex + 1} - ${nextPage.part}")
                switchPage(nextPageIndex)
                return
            }
            // 所有分P播放完成，继续检查合集
        }
        
        // 2. 检查合集 (UGC Season)
        current.info.ugc_season?.let { season ->
            val allEpisodes = season.sections.flatMap { it.episodes }
            val currentEpIndex = allEpisodes.indexOfFirst { it.bvid == current.info.bvid }
            val nextEpIndex = currentEpIndex + 1
            
            if (nextEpIndex < allEpisodes.size) {
                // 播放合集下一集
                val nextEpisode = allEpisodes[nextEpIndex]
                Logger.d("PlayerVM", "📂 播放合集下一集: ${nextEpisode.title}")
                viewModelScope.launch {
                    toast("播放合集下一集: ${nextEpisode.title}")
                }
                loadVideo(nextEpisode.bvid)
                return
            }
            // 合集已播放完成
            Logger.d("PlayerVM", "📂 合集全部播放完成")
        }
        
        // 3. 最后播放推荐视频
        Logger.d("PlayerVM", "🎵 播放推荐视频")
        playNextRecommended()
    }
    
    /**
     *  [新增] 播放上一个推荐视频（使用 PlaylistManager）
     */
    fun playPreviousRecommended() {
        // 使用 PlaylistManager 获取上一曲
        val prevItem = PlaylistManager.playPrevious()
        
        if (prevItem != null) {
            viewModelScope.launch {
                toast("正在播放: ${prevItem.title}")
            }
            // 加载新视频
            loadVideo(prevItem.bvid)
        } else {
            toast("没有上一个视频")
        }
    }
    
    fun loadVideo(bvid: String) {
        if (bvid.isBlank()) return
        
        //  防止重复加载：只有在正在加载同一视频时才跳过
        if (currentBvid == bvid && _uiState.value is PlayerUiState.Loading) {
            Logger.d("PlayerVM", " Already loading $bvid, skip")
            return
        }
        
        //  [修复] 更智能的重复检测：只有播放器真正在播放同一视频时才跳过
        // 如果播放器已停止、出错或处于空闲状态，应该重新加载
        val player = exoPlayer
        val isPlayerHealthy = player != null && 
            player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING) &&
            player.playerError == null // 没有播放错误
        
        val currentSuccess = _uiState.value as? PlayerUiState.Success
        if (currentSuccess != null && currentBvid == bvid && isPlayerHealthy && player != null) {
            Logger.d("PlayerVM", " $bvid already playing healthy, skip reload")
            //  确保音量正常
            player.volume = 1.0f
            if (!player.isPlaying) {
                player.play()
            }
            return
        }
        
        if (currentBvid.isNotEmpty() && currentBvid != bvid) saveCurrentPosition()
        
        val cachedPosition = playbackUseCase.getCachedPosition(bvid)
        currentBvid = bvid
        
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading.Initial
            
            //  [网络感知] 根据网络类型选择默认清晰度
            var defaultQuality = appContext?.let { NetworkUtils.getDefaultQualityId(it) } ?: 64
            
            // 📉 [省流量] 省流量模式逻辑：
            // - ALWAYS: 任何网络都限制 480P
            // - MOBILE_ONLY: 仅移动数据时限制 480P（WiFi不受限）
            val isOnMobileNetwork = appContext?.let { NetworkUtils.isMobileData(it) } ?: false
            val dataSaverMode = appContext?.let { 
                com.android.purebilibili.core.store.SettingsManager.getDataSaverModeSync(it) 
            } ?: com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY
            
            //  判断是否应该限制画质
            val shouldLimitQuality = when (dataSaverMode) {
                com.android.purebilibili.core.store.SettingsManager.DataSaverMode.OFF -> false
                com.android.purebilibili.core.store.SettingsManager.DataSaverMode.ALWAYS -> true  // 任何网络都限制
                com.android.purebilibili.core.store.SettingsManager.DataSaverMode.MOBILE_ONLY -> isOnMobileNetwork  // 仅移动数据
            }
            
            if (shouldLimitQuality && defaultQuality > 32) {
                defaultQuality = 32  // 480P
                com.android.purebilibili.core.util.Logger.d("PlayerViewModel", "📉 省流量模式(${dataSaverMode.label}): 限制画质为480P")
            }
            
            when (val result = playbackUseCase.loadVideo(bvid, defaultQuality)) {
                is VideoLoadResult.Success -> {
                    currentCid = result.info.cid
                    
                    // Play video
                    if (result.audioUrl != null) {
                        playbackUseCase.playDashVideo(result.playUrl, result.audioUrl, cachedPosition)
                    } else {
                        playbackUseCase.playVideo(result.playUrl, cachedPosition)
                    }
                    
                    //  收集所有 CDN URL (主+备用)
                    val allVideoUrls = buildList {
                        add(result.playUrl)
                        result.cachedDashVideos
                            .find { it.id == result.quality }
                            ?.backupUrl
                            ?.filterNotNull()
                            ?.filter { it.isNotEmpty() }
                            ?.let { addAll(it) }
                    }.distinct()
                    
                    val allAudioUrls = buildList {
                        result.audioUrl?.let { add(it) }
                        result.cachedDashAudios.firstOrNull()
                            ?.backupUrl
                            ?.filterNotNull()
                            ?.filter { it.isNotEmpty() }
                            ?.let { addAll(it) }
                    }.distinct()
                    
                    Logger.d("PlayerVM", "📡 CDN 线路: 视频${allVideoUrls.size}个, 音频${allAudioUrls.size}个")
                    
                    _uiState.value = PlayerUiState.Success(
                        info = result.info,
                        playUrl = result.playUrl,
                        audioUrl = result.audioUrl,
                        related = result.related,
                        currentQuality = result.quality,
                        qualityIds = result.qualityIds,
                        qualityLabels = result.qualityLabels,
                        cachedDashVideos = result.cachedDashVideos,
                        cachedDashAudios = result.cachedDashAudios,
                        emoteMap = result.emoteMap,
                        isLoggedIn = result.isLoggedIn,
                        isVip = result.isVip,
                        isFollowing = result.isFollowing,
                        isFavorited = result.isFavorited,
                        isLiked = result.isLiked,
                        coinCount = result.coinCount,
                        //  CDN 线路
                        currentCdnIndex = 0,
                        allVideoUrls = allVideoUrls,
                        allAudioUrls = allAudioUrls
                    )
                    
                    //  [新增] 异步加载关注列表（用于推荐视频的已关注标签）
                    if (result.isLoggedIn) {
                        loadFollowingMids()
                    }
                    
                    //  异步加载视频标签
                    loadVideoTags(bvid)
                    
                    // 🖼️ 异步加载视频预览图（用于进度条拖动预览）
                    loadVideoshot(bvid, result.info.cid)
                    
                    // 📖 异步加载视频章节信息（用于进度条章节标记）
                    loadChapterInfo(bvid, result.info.cid)
                    
                    //  [新增] 更新播放列表
                    updatePlaylist(result.info, result.related)
                    
                    startHeartbeat()
                    
                    //  通知插件系统：视频已加载
                    PluginManager.getEnabledPlayerPlugins().forEach { plugin ->
                        try {
                            plugin.onVideoLoad(bvid, currentCid)
                        } catch (e: Exception) {
                            Logger.e("PlayerVM", "Plugin ${plugin.name} onVideoLoad failed", e)
                        }
                    }
                    
                    //  启动插件检查定时器
                    startPluginCheck()
                    
                    AnalyticsHelper.logVideoPlay(bvid, result.info.title, result.info.owner.name)
                }
                is VideoLoadResult.Error -> {
                    CrashReporter.reportVideoError(bvid, "load_failed", result.error.toUserMessage())
                    _uiState.value = PlayerUiState.Error(result.error, result.canRetry)
                }
            }
        }
    }
    
    /**
     *  [新增] 更新播放列表
     */
    private fun updatePlaylist(currentInfo: com.android.purebilibili.data.model.response.ViewInfo, related: List<com.android.purebilibili.data.model.response.RelatedVideo>) {
        val currentPlaylist = PlaylistManager.playlist.value
        val currentIndex = PlaylistManager.currentIndex.value
        val currentItemInList = currentPlaylist.getOrNull(currentIndex)

        // 转换推荐视频为播放项
        val relatedItems = related.map { video ->
            PlaylistItem(
                bvid = video.bvid,
                title = video.title,
                cover = video.pic,
                owner = video.owner.name,
                duration = video.duration.toLong()
            )
        }
        
        // 创建当前视频的播放项 (updated with full info)
        val currentFullItem = PlaylistItem(
            bvid = currentInfo.bvid,
            title = currentInfo.title,
            cover = currentInfo.pic,
            owner = currentInfo.owner.name,
            duration = 0L // ViewInfo 暂无 duration 字段，暂置为 0
        )

        if (currentItemInList != null && currentItemInList.bvid == currentInfo.bvid) {
             // 命中当前播放列表逻辑：保留历史，更新未来
             // 1. 获取当前索引及之前的列表 (历史 + 当前)
             val history = currentPlaylist.take(currentIndex) // 0 .. currentIndex-1
             
             // 2. 组合新列表: 历史 + 当前(更新详情) + 新推荐
             val newPlaylist = history + currentFullItem + relatedItems
             
             // 3. 更新列表，保持当前索引不变
             PlaylistManager.setPlaylist(newPlaylist, currentIndex)
             Logger.d("PlayerVM", " 播放列表已扩展: 保留 ${history.size} 项历史, 更新后续 ${relatedItems.size} 项")
        } else {
            // 新播放逻辑：当前 + 推荐
            val playlist = listOf(currentFullItem) + relatedItems
            PlaylistManager.setPlaylist(playlist, 0)
            Logger.d("PlayerVM", " 播放列表已重置: 1 + ${relatedItems.size} 项")
        }
    }
    
    fun retry() {
        val bvid = currentBvid.takeIf { it.isNotBlank() } ?: return
        
        //  检查当前错误类型，如果是全局冷却则清除所有冷却
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Error && 
            currentState.error is VideoLoadError.GlobalCooldown) {
            PlaybackCooldownManager.clearAll()
        } else {
            // 清除该视频的冷却状态，允许用户强制重试
            PlaybackCooldownManager.clearForVideo(bvid)
        }
        
        PlayUrlCache.invalidate(bvid, currentCid)
        currentBvid = ""
        loadVideo(bvid)
    }
    
    /**
     *  重载视频 - 保持当前播放位置
     * 用于设置面板的"重载视频"功能
     */
    fun reloadVideo() {
        val bvid = currentBvid.takeIf { it.isNotBlank() } ?: return
        val currentPos = exoPlayer?.currentPosition ?: 0L
        
        // 清除缓存
        PlayUrlCache.invalidate(bvid, currentCid)
        PlaybackCooldownManager.clearForVideo(bvid)
        
        // 重新加载
        currentBvid = ""
        
        viewModelScope.launch {
            loadVideo(bvid)
            // 等待加载完成后恢复位置
            kotlinx.coroutines.delay(500)
            exoPlayer?.seekTo(currentPos)
        }
    }
    
    /**
     *  切换 CDN 线路
     * 在当前画质下切换到下一个 CDN
     */
    fun switchCdn() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        
        if (current.cdnCount <= 1) {
            viewModelScope.launch { toast("没有其他可用线路") }
            return
        }
        
        // 计算下一个 CDN 索引（循环）
        val nextIndex = (current.currentCdnIndex + 1) % current.cdnCount
        val nextVideoUrl = current.allVideoUrls.getOrNull(nextIndex) ?: return
        val nextAudioUrl = current.allAudioUrls.getOrNull(nextIndex)
        
        val currentPos = exoPlayer?.currentPosition ?: 0L
        
        viewModelScope.launch {
            Logger.d("PlayerVM", "📡 切换线路: ${current.currentCdnIndex + 1} → ${nextIndex + 1}")
            
            // 使用新的 URL 播放
            if (nextAudioUrl != null) {
                playbackUseCase.playDashVideo(nextVideoUrl, nextAudioUrl, currentPos)
            } else {
                playbackUseCase.playVideo(nextVideoUrl, currentPos)
            }
            
            // 更新状态
            _uiState.value = current.copy(
                playUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                currentCdnIndex = nextIndex
            )
            
            toast("已切换到线路${nextIndex + 1}")
        }
    }
    
    /**
     *  切换到指定 CDN 线路
     */
    fun switchCdnTo(index: Int) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        
        if (index < 0 || index >= current.cdnCount) return
        if (index == current.currentCdnIndex) {
            viewModelScope.launch { toast("已是当前线路") }
            return
        }
        
        val nextVideoUrl = current.allVideoUrls.getOrNull(index) ?: return
        val nextAudioUrl = current.allAudioUrls.getOrNull(index)
        
        val currentPos = exoPlayer?.currentPosition ?: 0L
        
        viewModelScope.launch {
            Logger.d("PlayerVM", "📡 切换到线路: ${index + 1}")
            
            if (nextAudioUrl != null) {
                playbackUseCase.playDashVideo(nextVideoUrl, nextAudioUrl, currentPos)
            } else {
                playbackUseCase.playVideo(nextVideoUrl, currentPos)
            }
            
            _uiState.value = current.copy(
                playUrl = nextVideoUrl,
                audioUrl = nextAudioUrl,
                currentCdnIndex = index
            )
            
            toast("已切换到线路${index + 1}")
        }
    }
    
    // ========== Interaction ==========
    
    fun toggleFollow() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleFollow(current.info.owner.mid, current.isFollowing)
                .onSuccess { _uiState.value = current.copy(isFollowing = it); toast(if (it) "\u5173\u6ce8\u6210\u529f" else "\u5df2\u53d6\u6d88\u5173\u6ce8") }
                .onFailure { toast(it.message ?: "\u64cd\u4f5c\u5931\u8d25") }
        }
    }
    
    fun toggleFavorite() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleFavorite(current.info.aid, current.isFavorited, currentBvid)
                .onSuccess { 
                    val newStat = current.info.stat.copy(favorite = current.info.stat.favorite + if (it) 1 else -1)
                    _uiState.value = current.copy(info = current.info.copy(stat = newStat), isFavorited = it)
                    //  彩蛋：使用趣味消息（如果设置开启）
                    val message = if (it && appContext?.let { ctx -> com.android.purebilibili.core.store.SettingsManager.isEasterEggEnabledSync(ctx) } == true) {
                        com.android.purebilibili.core.util.EasterEggs.getFavoriteMessage()
                    } else {
                        if (it) "已收藏" else "已取消收藏"
                    }
                    toast(message)
                }
                .onFailure { toast(it.message ?: "\u64cd\u4f5c\u5931\u8d25") }
        }
    }
    
    fun toggleLike() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleLike(current.info.aid, current.isLiked, currentBvid)
                .onSuccess { 
                    val newStat = current.info.stat.copy(like = current.info.stat.like + if (it) 1 else -1)
                    _uiState.value = current.copy(info = current.info.copy(stat = newStat), isLiked = it)
                    if (it) _likeBurstVisible.value = true
                    //  彩蛋：使用趣味消息（如果设置开启）
                    val message = if (it && appContext?.let { ctx -> com.android.purebilibili.core.store.SettingsManager.isEasterEggEnabledSync(ctx) } == true) {
                        com.android.purebilibili.core.util.EasterEggs.getLikeMessage()
                    } else {
                        if (it) "点赞成功" else "已取消点赞"
                    }
                    toast(message)
                }
                .onFailure { toast(it.message ?: "\u64cd\u4f5c\u5931\u8d25") }
        }
    }
    
    //  稍后再看
    fun toggleWatchLater() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            interactionUseCase.toggleWatchLater(current.info.aid, current.isInWatchLater, currentBvid)
                .onSuccess { inWatchLater ->
                    _uiState.value = current.copy(isInWatchLater = inWatchLater)
                    toast(if (inWatchLater) "已添加到稍后再看" else "已从稍后再看移除")
                }
                .onFailure { toast(it.message ?: "操作失败") }
        }
    }
    
    //  异步加载关注列表（用于推荐视频的已关注标签）
    private fun loadFollowingMids() {
        viewModelScope.launch {
            try {
                val mid = com.android.purebilibili.core.store.TokenManager.midCache ?: return@launch
                val allMids = mutableSetOf<Long>()
                var page = 1
                val pageSize = 50
                
                // 只加载前 200 个关注（4页），避免请求过多
                while (page <= 4) {
                    try {
                        val result = com.android.purebilibili.core.network.NetworkModule.api.getFollowings(mid, page, pageSize)
                        if (result.code == 0 && result.data != null) {
                            val list = result.data.list ?: break
                            if (list.isEmpty()) break
                            allMids.addAll(list.map { it.mid })
                            if (list.size < pageSize) break
                            page++
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
                
                // 更新 UI 状态
                val current = _uiState.value as? PlayerUiState.Success ?: return@launch
                _uiState.value = current.copy(followingMids = allMids)
                Logger.d("PlayerVM", " Loaded ${allMids.size} following mids")
            } catch (e: Exception) {
                Logger.d("PlayerVM", " Failed to load following mids: ${e.message}")
            }
        }
    }
    
    //  异步加载视频标签
    private fun loadVideoTags(bvid: String) {
        viewModelScope.launch {
            try {
                val response = com.android.purebilibili.core.network.NetworkModule.api.getVideoTags(bvid)
                if (response.code == 0 && response.data != null) {
                    val current = _uiState.value as? PlayerUiState.Success ?: return@launch
                    _uiState.value = current.copy(videoTags = response.data)
                    Logger.d("PlayerVM", "🏷️ Loaded ${response.data.size} video tags")
                }
            } catch (e: Exception) {
                Logger.d("PlayerVM", " Failed to load video tags: ${e.message}")
            }
        }
    }
    
    // 🖼️ 异步加载视频预览图数据（用于进度条拖动预览）
    private fun loadVideoshot(bvid: String, cid: Long) {
        viewModelScope.launch {
            try {
                val videoshotData = VideoRepository.getVideoshot(bvid, cid)
                if (videoshotData != null && videoshotData.isValid) {
                    val current = _uiState.value as? PlayerUiState.Success ?: return@launch
                    _uiState.value = current.copy(videoshotData = videoshotData)
                    Logger.d("PlayerVM", "🖼️ Loaded videoshot: ${videoshotData.image.size} images, ${videoshotData.index.size} frames")
                }
            } catch (e: Exception) {
                Logger.d("PlayerVM", "🖼️ Failed to load videoshot: ${e.message}")
            }
        }
    }
    
    //  [新增] 异步加载视频章节/看点数据（用于进度条章节标记）
    private fun loadChapterInfo(bvid: String, cid: Long) {
        viewModelScope.launch {
            try {
                val response = com.android.purebilibili.core.network.NetworkModule.api.getPlayerInfo(bvid, cid)
                if (response.code == 0 && response.data != null) {
                    val points = response.data.viewPoints
                    if (points.isNotEmpty()) {
                        _viewPoints.value = points
                        Logger.d("PlayerVM", "📖 Loaded ${points.size} chapter points")
                    } else {
                        _viewPoints.value = emptyList()
                        Logger.d("PlayerVM", "📖 No chapter points for this video")
                    }
                }
            } catch (e: Exception) {
                Logger.d("PlayerVM", "📖 Failed to load chapter info: ${e.message}")
                _viewPoints.value = emptyList()
            }
        }
    }
    
    fun openCoinDialog() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.coinCount >= 2) { toast("\u5df2\u6295\u6ee12\u4e2a\u786c\u5e01"); return }
        _coinDialogVisible.value = true
    }
    
    fun closeCoinDialog() { _coinDialogVisible.value = false }
    
    fun doCoin(count: Int, alsoLike: Boolean) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        _coinDialogVisible.value = false
        viewModelScope.launch {
            interactionUseCase.doCoin(current.info.aid, count, alsoLike, currentBvid)
                .onSuccess { 
                    var newState = current.copy(coinCount = minOf(current.coinCount + count, 2))
                    if (alsoLike && !current.isLiked) newState = newState.copy(isLiked = true)
                    _uiState.value = newState
                    //  彩蛋：使用趣味消息（如果设置开启）
                    val message = if (appContext?.let { ctx -> com.android.purebilibili.core.store.SettingsManager.isEasterEggEnabledSync(ctx) } == true) {
                        com.android.purebilibili.core.util.EasterEggs.getCoinMessage()
                    } else {
                        "投币成功"
                    }
                    toast(message)
                }
                .onFailure { toast(it.message ?: "\u6295\u5e01\u5931\u8d25") }
        }
    }
    
    fun doTripleAction() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            toast("\u6b63\u5728\u4e09\u8fde...")
            interactionUseCase.doTripleAction(current.info.aid)
                .onSuccess { result ->
                    var newState = current
                    if (result.likeSuccess) newState = newState.copy(isLiked = true)
                    if (result.coinSuccess) newState = newState.copy(coinCount = 2)
                    if (result.favoriteSuccess) newState = newState.copy(isFavorited = true)
                    _uiState.value = newState
                    if (result.allSuccess) _tripleCelebrationVisible.value = true
                    toast(result.toSummaryMessage())
                }
                .onFailure { toast(it.message ?: "\u4e09\u8fde\u5931\u8d25") }
        }
    }
    
    fun dismissLikeBurst() { _likeBurstVisible.value = false }
    fun dismissTripleCelebration() { _tripleCelebrationVisible.value = false }
    
    // ========== Download ==========
    
    //  下载对话框状态
    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog = _showDownloadDialog.asStateFlow()
    
    fun openDownloadDialog() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        
        // 检查是否已下载
        val existingTask = com.android.purebilibili.feature.download.DownloadManager.getTask(currentBvid, currentCid)
        if (existingTask != null) {
            if (existingTask.isComplete) {
                toast("视频已缓存")
                return
            }
            if (existingTask.isDownloading) {
                toast("正在下载中...")
                return
            }
        }
        
        _showDownloadDialog.value = true
    }
    
    fun closeDownloadDialog() {
        _showDownloadDialog.value = false
    }
    
    fun downloadWithQuality(qualityId: Int) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        _showDownloadDialog.value = false
        
        viewModelScope.launch {
            // 如果选择的画质不同，需要获取对应画质的 URL
            val videoUrl: String
            val audioUrl: String?
            val qualityDesc: String
            
            if (qualityId == current.currentQuality) {
                // 使用当前画质
                videoUrl = current.playUrl
                audioUrl = current.audioUrl
                qualityDesc = current.qualityLabels.getOrNull(
                    current.qualityIds.indexOf(qualityId)
                ) ?: "${qualityId}P"
            } else {
                // 从缓存或 API 获取指定画质的 URL
                val dashVideo = current.cachedDashVideos.find { it.id == qualityId }
                val dashAudio = current.cachedDashAudios.firstOrNull()
                
                if (dashVideo != null) {
                    videoUrl = dashVideo.getValidUrl() ?: current.playUrl
                    audioUrl = dashAudio?.getValidUrl() ?: current.audioUrl
                    qualityDesc = current.qualityLabels.getOrNull(
                        current.qualityIds.indexOf(qualityId)
                    ) ?: "${qualityId}P"
                } else {
                    // 使用当前画质
                    videoUrl = current.playUrl
                    audioUrl = current.audioUrl
                    qualityDesc = current.qualityLabels.getOrNull(
                        current.qualityIds.indexOf(current.currentQuality)
                    ) ?: "${current.currentQuality}P"
                }
            }
            
            // 创建下载任务
            val task = com.android.purebilibili.feature.download.DownloadTask(
                bvid = currentBvid,
                cid = currentCid,
                title = current.info.title,
                cover = current.info.pic,
                ownerName = current.info.owner.name,
                ownerFace = current.info.owner.face,
                duration = 0,
                quality = qualityId,
                qualityDesc = qualityDesc,
                videoUrl = videoUrl,
                audioUrl = audioUrl ?: ""
            )
            
            val added = com.android.purebilibili.feature.download.DownloadManager.addTask(task)
            if (added) {
                toast("开始下载: ${current.info.title} [$qualityDesc]")
                // 开始监听下载进度
                com.android.purebilibili.feature.download.DownloadManager.tasks.collect { tasks ->
                    val downloadTask = tasks[task.id]
                    _downloadProgress.value = downloadTask?.progress ?: -1f
                }
            } else {
                toast("下载任务已存在")
            }
        }
    }
    
    // ========== Quality ==========
    
    fun changeQuality(qualityId: Int, currentPos: Long) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.isQualitySwitching) { toast("正在切换中..."); return }
        if (current.currentQuality == qualityId) { toast("已是当前清晰度"); return }
        
        //  [新增] 权限检查
        val permissionResult = qualityManager.checkQualityPermission(
            qualityId, current.isLoggedIn, current.isVip
        )
        
        when (permissionResult) {
            is QualityPermissionResult.RequiresVip -> {
                toast("${permissionResult.qualityLabel} 需要大会员")
                // 自动降级到最高可用画质
                val fallbackQuality = qualityManager.getMaxAvailableQuality(
                    current.qualityIds, current.isLoggedIn, current.isVip
                )
                if (fallbackQuality != current.currentQuality) {
                    changeQuality(fallbackQuality, currentPos)
                }
                return
            }
            is QualityPermissionResult.RequiresLogin -> {
                toast("${permissionResult.qualityLabel} 需要登录")
                return
            }
            is QualityPermissionResult.Permitted -> {
                // 继续切换
            }
        }
        
        _uiState.value = current.copy(isQualitySwitching = true, requestedQuality = qualityId)
        
        viewModelScope.launch {
            val result = playbackUseCase.changeQualityFromCache(qualityId, current.cachedDashVideos, current.cachedDashAudios, currentPos)
                ?: playbackUseCase.changeQualityFromApi(currentBvid, currentCid, qualityId, currentPos)
            
            if (result != null) {
                _uiState.value = current.copy(
                    playUrl = result.videoUrl, audioUrl = result.audioUrl,
                    currentQuality = result.actualQuality, isQualitySwitching = false, requestedQuality = null,
                    //  [修复] 更新缓存的DASH流，否则后续画质切换可能失败
                    cachedDashVideos = result.cachedDashVideos.ifEmpty { current.cachedDashVideos },
                    cachedDashAudios = result.cachedDashAudios.ifEmpty { current.cachedDashAudios }
                )
                val label = current.qualityLabels.getOrNull(current.qualityIds.indexOf(result.actualQuality)) ?: "${result.actualQuality}"
                toast(if (result.wasFallback) " 已切换至 $label" else "✓ 已切换至 $label")
                //  记录画质切换事件
                AnalyticsHelper.logQualityChange(currentBvid, current.currentQuality, result.actualQuality)
            } else {
                _uiState.value = current.copy(isQualitySwitching = false, requestedQuality = null)
                toast("清晰度切换失败")
            }
        }
    }
    
    // ========== Page Switch ==========
    
    fun switchPage(pageIndex: Int) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        val page = current.info.pages.getOrNull(pageIndex) ?: return
        if (page.cid == currentCid) { toast("\u5df2\u662f\u5f53\u524d\u5206P"); return }
        
        currentCid = page.cid
        _uiState.value = current.copy(isQualitySwitching = true)
        
        viewModelScope.launch {
            try {
                val playUrlData = VideoRepository.getPlayUrlData(currentBvid, page.cid, current.currentQuality)
                if (playUrlData != null) {
                    val dashVideo = playUrlData.dash?.getBestVideo(current.currentQuality)
                    val dashAudio = playUrlData.dash?.getBestAudio()
                    val videoUrl = dashVideo?.getValidUrl() ?: playUrlData.durl?.firstOrNull()?.url ?: ""
                    val audioUrl = dashAudio?.getValidUrl()
                    
                    if (videoUrl.isNotEmpty()) {
                        if (dashVideo != null) playbackUseCase.playDashVideo(videoUrl, audioUrl, 0L)
                        else playbackUseCase.playVideo(videoUrl, 0L)
                        
                        _uiState.value = current.copy(
                            info = current.info.copy(cid = page.cid), playUrl = videoUrl, audioUrl = audioUrl,
                            startPosition = 0L, isQualitySwitching = false,
                            cachedDashVideos = playUrlData.dash?.video ?: emptyList(),
                            cachedDashAudios = playUrlData.dash?.audio ?: emptyList()
                        )
                        toast("\u5df2\u5207\u6362\u81f3 P${pageIndex + 1}")
                        return@launch
                    }
                }
                _uiState.value = current.copy(isQualitySwitching = false)
                toast("\u5206P\u5207\u6362\u5931\u8d25")
            } catch (e: Exception) {
                _uiState.value = current.copy(isQualitySwitching = false)
            }
        }
    }
    
    // ==========  Plugin System (SponsorBlock等) ==========
    
    /**
     * 定期检查插件（约500ms一次）
     */
    private fun startPluginCheck() {
        pluginCheckJob?.cancel()
        pluginCheckJob = viewModelScope.launch {
            while (true) {
                delay(500)  // 每500ms检查一次
                val plugins = PluginManager.getEnabledPlayerPlugins()
                if (plugins.isEmpty()) continue
                
                val currentPos = playbackUseCase.getCurrentPosition()
                
                for (plugin in plugins) {
                    try {
                        when (val action = plugin.onPositionUpdate(currentPos)) {
                            is SkipAction.SkipTo -> {
                                playbackUseCase.seekTo(action.positionMs)
                                toast(action.reason)
                                Logger.d("PlayerVM", " Plugin ${plugin.name} skipped to ${action.positionMs}ms")
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Logger.e("PlayerVM", "Plugin ${plugin.name} onPositionUpdate failed", e)
                    }
                }
            }
        }
    }
    
    fun dismissSponsorSkipButton() { _showSkipButton.value = false }
    
    // ========== Playback Control ==========
    
    fun seekTo(pos: Long) { playbackUseCase.seekTo(pos) }
    fun getPlayerCurrentPosition() = playbackUseCase.getCurrentPosition()
    fun getPlayerDuration() = playbackUseCase.getDuration()
    fun saveCurrentPosition() { playbackUseCase.savePosition(currentBvid) }
    
    fun restoreFromCache(cachedState: PlayerUiState.Success, startPosition: Long = -1L) {
        currentBvid = cachedState.info.bvid
        currentCid = cachedState.info.cid
        _uiState.value = if (startPosition >= 0) cachedState.copy(startPosition = startPosition) else cachedState
    }
    
    // ========== Private ==========
    
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            // [修复] 立即上报一次心跳，确保进入历史记录
            // 短时间观看也应该被记录
            if (currentBvid.isNotEmpty() && currentCid > 0) {
                try { 
                    VideoRepository.reportPlayHeartbeat(currentBvid, currentCid, 0)
                    Logger.d("PlayerVM", " Initial heartbeat reported for $currentBvid")
                }
                catch (e: Exception) {
                    Logger.d("PlayerVM", " Initial heartbeat failed: ${e.message}")
                }
            }
            
            // 之后每30秒上报一次
            while (true) {
                delay(30_000)
                if (exoPlayer?.isPlaying == true && currentBvid.isNotEmpty() && currentCid > 0) {
                    try { VideoRepository.reportPlayHeartbeat(currentBvid, currentCid, playbackUseCase.getCurrentPosition() / 1000) }
                    catch (_: Exception) {}
                }
            }
        }
    }
    
    private fun toast(msg: String) { viewModelScope.launch { _toastEvent.send(msg) } }
    
    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
        pluginCheckJob?.cancel()
        
        //  通知插件系统：视频结束
        PluginManager.getEnabledPlayerPlugins().forEach { plugin ->
            try {
                plugin.onVideoEnd()
            } catch (e: Exception) {
                Logger.e("PlayerVM", "Plugin ${plugin.name} onVideoEnd failed", e)
            }
        }
        
        exoPlayer = null
    }
}