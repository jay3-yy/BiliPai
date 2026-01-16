// 文件路径: feature/bangumi/BangumiPlayerViewModel.kt
package com.android.purebilibili.feature.bangumi

import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.feature.player.BasePlayerViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 番剧播放器 UI 状态
 */
sealed class BangumiPlayerState {
    object Loading : BangumiPlayerState()
    
    data class Success(
        val seasonDetail: BangumiDetail,
        val currentEpisode: BangumiEpisode,
        val currentEpisodeIndex: Int,
        val playUrl: String?,
        val audioUrl: String?,
        val quality: Int,
        val acceptQuality: List<Int>,
        val acceptDescription: List<String>
    ) : BangumiPlayerState()
    
    data class Error(
        val message: String,
        val isVipRequired: Boolean = false,
        val isLoginRequired: Boolean = false,
        val canRetry: Boolean = true
    ) : BangumiPlayerState()
}

/**
 * 番剧播放器 ViewModel
 * 
 *  [重构] 继承 BasePlayerViewModel，复用空降助手、DASH 播放、弹幕等公共功能
 */
class BangumiPlayerViewModel : BasePlayerViewModel() {
    
    private val _uiState = MutableStateFlow<BangumiPlayerState>(BangumiPlayerState.Loading)
    val uiState = _uiState.asStateFlow()
    
    //  Toast 事件通道
    private val _toastEvent = Channel<String>()
    val toastEvent = _toastEvent.receiveAsFlow()
    
    private var currentSeasonId: Long = 0
    private var currentEpId: Long = 0
    
    //  [重构] 覆盖基类的空降跳过回调，显示 toast
    override fun onSponsorSkipped(segment: SponsorSegment) {
        viewModelScope.launch {
            _toastEvent.send("已跳过: ${segment.categoryName}")
        }
    }
    
    //  [新增] 播放完成监听器
    private val playbackEndListener = object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                // 播放完成，自动播放下一集
                playNextEpisode()
            }
        }
    }
    
    /**
     *  [新增] 自动播放下一集
     */
    fun playNextEpisode() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val episodes = currentState.seasonDetail.episodes ?: return
        val currentIndex = currentState.currentEpisodeIndex
        
        // 检查是否有下一集
        if (currentIndex < episodes.size - 1) {
            val nextEpisode = episodes[currentIndex + 1]
            viewModelScope.launch {
                _toastEvent.send("正在播放下一集: ${nextEpisode.title ?: nextEpisode.longTitle ?: "第${currentIndex + 2}集"}")
            }
            switchEpisode(nextEpisode)
        } else {
            // 已经是最后一集
            viewModelScope.launch {
                _toastEvent.send("已是最后一集")
            }
        }
    }
    
    /**
     * 绑定播放器
     */
    override fun attachPlayer(player: ExoPlayer) {
        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🔗 attachPlayer called, player hashCode: ${player.hashCode()}")
        super.attachPlayer(player)
        //  [新增] 添加播放完成监听
        player.addListener(playbackEndListener)
    }
    
    /**
     *  [新增] 清理时移除监听器
     */
    override fun onCleared() {
        super.onCleared()
        // 监听器会随 player 一起清理，无需手动移除
    }
    
    /**
     * 加载番剧播放（从详情页进入）
     */
    fun loadBangumiPlay(seasonId: Long, epId: Long) {
        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📥 loadBangumiPlay: seasonId=$seasonId, epId=$epId, exoPlayer=${exoPlayer?.hashCode()}")
        if (seasonId == currentSeasonId && epId == currentEpId && _uiState.value is BangumiPlayerState.Success) {
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "⏭️ loadBangumiPlay: skipped (already loaded)")
            return // 避免重复加载
        }
        
        currentSeasonId = seasonId
        currentEpId = epId
        
        viewModelScope.launch {
            _uiState.value = BangumiPlayerState.Loading
            
            // 1. 获取番剧详情（包含剧集列表）
            val detailResult = BangumiRepository.getSeasonDetail(seasonId)
            
            detailResult.onSuccess { detail ->
                // 找到当前剧集
                val episode = detail.episodes?.find { it.id == epId }
                    ?: detail.episodes?.firstOrNull()
                
                if (episode == null) {
                    _uiState.value = BangumiPlayerState.Error("未找到可播放的剧集")
                    return@onSuccess
                }
                
                val episodeIndex = detail.episodes?.indexOfFirst { it.id == episode.id } ?: 0
                
                // 2. 获取播放地址
                fetchPlayUrl(detail, episode, episodeIndex)
                
            }.onFailure { e ->
                _uiState.value = BangumiPlayerState.Error(
                    message = e.message ?: "加载失败",
                    canRetry = true
                )
            }
        }
    }
    
    /**
     * 获取播放地址
     */
    private suspend fun fetchPlayUrl(detail: BangumiDetail, episode: BangumiEpisode, episodeIndex: Int) {
        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🎬 fetchPlayUrl: epId=${episode.id}, cid=${episode.cid}")
        val playUrlResult = BangumiRepository.getBangumiPlayUrl(episode.id)
        
        playUrlResult.onSuccess { playData ->
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📡 PlayUrl success: quality=${playData.quality}, hasDash=${playData.dash != null}, hasDurl=${!playData.durl.isNullOrEmpty()}")
            
            // 解析播放地址
            var videoUrl: String? = null
            var audioUrl: String? = null
            
            if (playData.dash != null) {
                // DASH 格式
                val dash = playData.dash
                val video = dash.getBestVideo(playData.quality)
                val audio = dash.getBestAudio()
                
                com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📹 DASH videos: ${dash.video.size}, audios: ${dash.audio?.size ?: 0}")
                
                //  [优化] 尝试主 URL，失败则使用备用 URL
                videoUrl = video?.getValidUrl()
                if (videoUrl.isNullOrEmpty() && video?.backupUrl?.isNotEmpty() == true) {
                    videoUrl = video.backupUrl.firstOrNull()
                    com.android.purebilibili.core.util.Logger.w("BangumiPlayerVM", " 主 URL 无效，使用备用 CDN: ${videoUrl?.take(60)}...")
                }
                
                audioUrl = audio?.getValidUrl()
                if (audioUrl.isNullOrEmpty() && audio?.backupUrl?.isNotEmpty() == true) {
                    audioUrl = audio.backupUrl.firstOrNull()
                }
                
                com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", " DASH: video=${videoUrl?.take(60)}..., audio=${audioUrl?.take(40)}...")
                
            } else if (!playData.durl.isNullOrEmpty()) {
                // FLV/MP4 格式
                val durl = playData.durl.first()
                videoUrl = durl.url
                //  [优化] durl 也有备用 URL
                val backupUrls = durl.backup_url
                if (videoUrl.isNullOrEmpty() && !backupUrls.isNullOrEmpty()) {
                    videoUrl = backupUrls.firstOrNull()
                }
                audioUrl = null
                com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "📹 DURL: url=${videoUrl?.take(60)}...")
            } else {
                com.android.purebilibili.core.util.Logger.e("BangumiPlayerVM", "❌ No dash or durl in response!")
                _uiState.value = BangumiPlayerState.Error("无法获取播放地址：服务器未返回视频流")
                return
            }
            
            if (videoUrl.isNullOrEmpty()) {
                _uiState.value = BangumiPlayerState.Error("无法获取播放地址")
                return
            }
            
            _uiState.value = BangumiPlayerState.Success(
                seasonDetail = detail,
                currentEpisode = episode,
                currentEpisodeIndex = episodeIndex,
                playUrl = videoUrl,
                audioUrl = audioUrl,
                quality = playData.quality,
                acceptQuality = playData.acceptQuality ?: emptyList(),
                acceptDescription = playData.acceptDescription ?: emptyList()
            )
            
            //  [修复] 检查播放器是否已附加，添加调试日志
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🎯 About to call playDashVideo, exoPlayer attached: ${exoPlayer != null}")
            if (exoPlayer == null) {
                com.android.purebilibili.core.util.Logger.e("BangumiPlayerVM", "❌ exoPlayer is NULL when trying to play! Video URL: ${videoUrl.take(50)}...")
            }
            
            //  [修复] 构建番剧专用 Referer，解决 CDN 403 播放失败问题
            val referer = "https://www.bilibili.com/bangumi/play/ep${episode.id}"
            com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", "🔗 Using Referer: $referer")
            
            //  [重构] 使用基类方法播放视频
            playDashVideo(videoUrl, audioUrl, referer = referer)
            
            //  [重构] 使用基类方法加载弹幕
            loadDanmaku(episode.cid)
            
            //  [重构] 使用基类方法加载空降片段
            episode.bvid?.let { loadSponsorSegments(it) }
            
            //  [新增] 上报播放心跳，记录到历史记录
            episode.bvid?.let { bvid ->
                viewModelScope.launch {
                    try {
                        com.android.purebilibili.data.repository.VideoRepository.reportPlayHeartbeat(bvid, episode.cid, 0)
                        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", " Heartbeat reported for bangumi: $bvid cid=${episode.cid}")
                    } catch (e: Exception) {
                        com.android.purebilibili.core.util.Logger.d("BangumiPlayerVM", " Heartbeat failed: ${e.message}")
                    }
                }
            }
            
        }.onFailure { e ->
            val isVip = e.message?.contains("大会员") == true
            val isLogin = e.message?.contains("登录") == true
            _uiState.value = BangumiPlayerState.Error(
                message = e.message ?: "获取播放地址失败",
                isVipRequired = isVip,
                isLoginRequired = isLogin,
                canRetry = !isVip && !isLogin
            )
        }
    }
    
    /**
     * 切换剧集
     */
    fun switchEpisode(episode: BangumiEpisode) {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        
        if (episode.id == currentState.currentEpisode.id) return
        
        currentEpId = episode.id
        val newIndex = currentState.seasonDetail.episodes?.indexOfFirst { it.id == episode.id } ?: 0
        
        viewModelScope.launch {
            _uiState.value = BangumiPlayerState.Loading
            fetchPlayUrl(currentState.seasonDetail, episode, newIndex)
        }
    }
    
    /**
     * 切换清晰度
     */
    fun changeQuality(qualityId: Int) {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val currentPos = getPlayerCurrentPosition()
        
        viewModelScope.launch {
            val playUrlResult = BangumiRepository.getBangumiPlayUrl(currentState.currentEpisode.id, qualityId)
            
            playUrlResult.onSuccess { playData ->
                val videoUrl: String?
                val audioUrl: String?
                
                if (playData.dash != null) {
                    val video = playData.dash.getBestVideo(qualityId)
                    val audio = playData.dash.getBestAudio()
                    videoUrl = video?.getValidUrl()
                    audioUrl = audio?.getValidUrl()
                } else if (!playData.durl.isNullOrEmpty()) {
                    videoUrl = playData.durl.firstOrNull()?.url
                    audioUrl = null
                } else {
                    return@onSuccess
                }
                
                if (videoUrl.isNullOrEmpty()) return@onSuccess
                
                _uiState.value = currentState.copy(
                    playUrl = videoUrl,
                    audioUrl = audioUrl,
                    quality = playData.quality
                )
                
                //  [修复] 切换清晰度时使用 resetPlayer=false 减少闪烁，并传入 Referer
                val referer = "https://www.bilibili.com/bangumi/play/ep${currentState.currentEpisode.id}"
                playDashVideo(videoUrl, audioUrl, currentPos, resetPlayer = false, referer = referer)
            }
        }
    }
    
    /**
     * 追番/取消追番
     */
    fun toggleFollow() {
        val currentState = _uiState.value as? BangumiPlayerState.Success ?: return
        val isFollowing = currentState.seasonDetail.userStatus?.follow == 1
        
        viewModelScope.launch {
            val result = if (isFollowing) {
                BangumiRepository.unfollowBangumi(currentState.seasonDetail.seasonId)
            } else {
                BangumiRepository.followBangumi(currentState.seasonDetail.seasonId)
            }
            
            if (result.isSuccess) {
                //  [修复] 立即更新本地状态，不等待重新获取
                val newFollowStatus = if (isFollowing) 0 else 1
                val updatedUserStatus = currentState.seasonDetail.userStatus?.copy(follow = newFollowStatus)
                    ?: com.android.purebilibili.data.model.response.UserStatus(follow = newFollowStatus)
                val updatedDetail = currentState.seasonDetail.copy(userStatus = updatedUserStatus)
                _uiState.value = currentState.copy(seasonDetail = updatedDetail)
                
                //  显示 Toast 反馈
                _toastEvent.send(if (isFollowing) "已取消追番" else "追番成功")
            } else {
                _toastEvent.send("操作失败，请重试")
            }
        }
    }
    
    /**
     * 重试
     */
    fun retry() {
        loadBangumiPlay(currentSeasonId, currentEpId)
    }
}
