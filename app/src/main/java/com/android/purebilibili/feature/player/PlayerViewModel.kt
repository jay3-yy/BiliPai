package com.android.purebilibili.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream // 👈 记得导入这个

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Success(
        val info: ViewInfo,
        val playUrl: String,
        val related: List<RelatedVideo> = emptyList(),
        // 👇👇👇 之前报错是因为缺了这行 👇👇👇
        val danmakuStream: InputStream? = null,
        // 👆👆👆 补上它 👆👆👆

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

                // 👇 新增：获取弹幕流
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
        danmaku: InputStream?, // 👈 增加参数
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
                    danmakuStream = danmaku, // 👈 填入数据
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