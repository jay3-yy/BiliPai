package com.android.purebilibili.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer // 👈 新增导入
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.VideoRepository
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

        // 清晰度相关状态
        val currentQuality: Int = 64,
        val qualityLabels: List<String> = emptyList(),
        val qualityIds: List<Int> = emptyList(),
        val startPosition: Long = 0L
    ) : PlayerUiState()
    data class Error(val msg: String) : PlayerUiState()
}

class PlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var currentBvid: String = ""
    private var currentCid: Long = 0

    // 👇👇👇 新增：持有 Player 引用以支持手势控制 👇👇👇
    private var exoPlayer: ExoPlayer? = null

    // 绑定 Player 实例
    fun attachPlayer(player: ExoPlayer) {
        this.exoPlayer = player
    }

    // 获取当前播放位置 (供手势计算初始值)
    fun getPlayerCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    // 获取视频总时长 (供手势计算边界)
    fun getPlayerDuration(): Long {
        val d = exoPlayer?.duration ?: 0L
        return if (d < 0) 0L else d
    }

    // 跳转进度
    fun seekTo(pos: Long) {
        exoPlayer?.seekTo(pos)
    }

    // 清理引用防止泄漏
    override fun onCleared() {
        super.onCleared()
        exoPlayer = null
    }
    // 👆👆👆 新增结束 👆👆👆


    // 首次加载
    fun loadVideo(bvid: String) {
        if (bvid.isBlank()) return
        currentBvid = bvid

        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            val detailResult = VideoRepository.getVideoDetails(bvid)

            detailResult.onSuccess { (info, _) ->
                currentCid = info.cid
                // 并行获取推荐
                val related = VideoRepository.getRelatedVideos(bvid)

                // 获取弹幕流
                val danmaku = VideoRepository.getDanmakuStream(info.cid)

                // 统一走 fetchAndPlay 流程获取初始播放地址 (默认 64)
                fetchAndPlay(bvid, info.cid, 64, info, related, danmaku, 0L)

            }.onFailure {
                _uiState.value = PlayerUiState.Error(it.message ?: "加载失败")
            }
        }
    }

    // 切换清晰度
    fun changeQuality(qualityId: Int, currentPos: Long) {
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Success) {
            viewModelScope.launch {
                fetchAndPlay(
                    currentBvid,
                    currentCid,
                    qualityId,
                    currentState.info,
                    currentState.related,
                    currentState.danmakuStream, // 保持已有弹幕流
                    currentPos
                )
            }
        }
    }

    // 统一获取地址并更新状态
    private suspend fun fetchAndPlay(
        bvid: String, cid: Long, qn: Int,
        info: ViewInfo, related: List<RelatedVideo>,
        danmaku: InputStream?,
        startPos: Long
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
                    startPosition = startPos
                )
            } else {
                _uiState.value = PlayerUiState.Error("该清晰度无法播放")
            }
        } catch (e: Exception) {
            _uiState.value = PlayerUiState.Error("清晰度切换失败: ${e.message}")
        }
    }
}