// 文件路径: feature/video/PlayerViewModel.kt
package com.android.purebilibili.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Success(
        val info: ViewInfo,
        val playUrl: String,
        val related: List<RelatedVideo> = emptyList(),
        val danmakuStream: InputStream? = null,
        val currentQuality: Int = 64,
        val qualityLabels: List<String> = emptyList(),
        val qualityIds: List<Int> = emptyList(),
        val startPosition: Long = 0L,

        val replies: List<ReplyItem> = emptyList(),
        val isRepliesLoading: Boolean = false,
        val replyCount: Int = 0,

        // 🔥🔥 [新增] 表情包映射表
        val emoteMap: Map<String, String> = emptyMap()
    ) : PlayerUiState()
    data class Error(val msg: String) : PlayerUiState()
}

class PlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var currentBvid: String = ""
    private var currentCid: Long = 0
    private var exoPlayer: ExoPlayer? = null

    // 绑定 Player 实例
    fun attachPlayer(player: ExoPlayer) { this.exoPlayer = player }

    // 获取当前播放位置 (供手势计算初始值)
    fun getPlayerCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    // 获取视频总时长 (供手势计算边界)
    fun getPlayerDuration(): Long {
        val d = exoPlayer?.duration ?: 0L
        return if (d < 0) 0L else d
    }

    // 跳转进度
    fun seekTo(pos: Long) { exoPlayer?.seekTo(pos) }

    // 清理引用防止泄漏
    override fun onCleared() {
        super.onCleared()
        exoPlayer = null
    }

    fun loadVideo(bvid: String) {
        if (bvid.isBlank()) return
        currentBvid = bvid
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            // 🔥🔥 并行加载：详情、相关视频、表情包
            val detailDeferred = async { VideoRepository.getVideoDetails(bvid) }
            val relatedDeferred = async { VideoRepository.getRelatedVideos(bvid) }
            val emoteDeferred = async { VideoRepository.getEmoteMap() }

            val detailResult = detailDeferred.await()
            val relatedVideos = relatedDeferred.await()
            val emoteMap = emoteDeferred.await()

            detailResult.onSuccess { (info, playData) ->
                currentCid = info.cid
                val danmaku = VideoRepository.getDanmakuStream(info.cid)
                val url = playData.durl?.firstOrNull()?.url ?: ""
                val qualities = playData.accept_quality ?: emptyList()
                val labels = playData.accept_description ?: emptyList()
                val realQuality = playData.quality

                if (url.isNotEmpty()) {
                    _uiState.value = PlayerUiState.Success(
                        info = info,
                        playUrl = url,
                        related = relatedVideos,
                        danmakuStream = danmaku,
                        currentQuality = realQuality,
                        qualityIds = qualities,
                        qualityLabels = labels,
                        startPosition = 0L,
                        // 🔥🔥 传入加载好的表情包
                        emoteMap = emoteMap
                    )
                } else {
                    _uiState.value = PlayerUiState.Error("无法获取播放地址")
                }
            }.onFailure {
                _uiState.value = PlayerUiState.Error(it.message ?: "加载失败")
            }
        }
    }

    fun loadComments(aid: Long) {
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Success) {
            _uiState.value = currentState.copy(isRepliesLoading = true)
            viewModelScope.launch {
                val result = VideoRepository.getComments(aid, 1, 3)
                result.onSuccess { data ->
                    val current = _uiState.value
                    if (current is PlayerUiState.Success) {
                        _uiState.value = current.copy(
                            replies = data.replies ?: emptyList(),
                            replyCount = data.cursor.all_count,
                            isRepliesLoading = false
                        )
                    }
                }.onFailure {
                    val current = _uiState.value
                    if (current is PlayerUiState.Success) {
                        _uiState.value = current.copy(isRepliesLoading = false)
                    }
                }
            }
        }
    }

    fun changeQuality(qualityId: Int, currentPos: Long) {
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Success) {
            viewModelScope.launch {
                fetchAndPlay(
                    currentBvid, currentCid, qualityId,
                    currentState.info, currentState.related, currentState.danmakuStream, currentPos,
                    currentState.replies, currentState.replyCount,
                    // 🔥🔥 透传表情包
                    currentState.emoteMap
                )
            }
        }
    }

    private suspend fun fetchAndPlay(
        bvid: String, cid: Long, qn: Int,
        info: ViewInfo, related: List<RelatedVideo>,
        danmaku: InputStream?, startPos: Long,
        replies: List<ReplyItem>, replyCount: Int,
        // 🔥🔥 新增参数：表情包 Map
        emoteMap: Map<String, String>
    ) {
        try {
            val playUrlData = VideoRepository.getPlayUrlData(bvid, cid, qn)
            val url = playUrlData?.durl?.firstOrNull()?.url ?: ""
            val qualities = playUrlData?.accept_quality ?: emptyList()
            val labels = playUrlData?.accept_description ?: emptyList()
            val realQuality = playUrlData?.quality ?: qn

            if (url.isNotEmpty()) {
                _uiState.value = PlayerUiState.Success(
                    info = info,
                    playUrl = url,
                    related = related,
                    danmakuStream = danmaku,
                    currentQuality = realQuality,
                    qualityIds = qualities,
                    qualityLabels = labels,
                    startPosition = startPos,
                    replies = replies,
                    replyCount = replyCount,
                    isRepliesLoading = false,
                    // 🔥🔥 恢复表情包
                    emoteMap = emoteMap
                )
            } else {
                _uiState.value = PlayerUiState.Error("该清晰度无法播放")
            }
        } catch (e: Exception) {
            _uiState.value = PlayerUiState.Error("清晰度切换失败: ${e.message}")
        }
    }
}