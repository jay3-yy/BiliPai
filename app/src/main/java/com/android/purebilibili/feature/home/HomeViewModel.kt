// 文件路径: feature/home/HomeViewModel.kt
package com.android.purebilibili.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 保持 UserState 不变
data class UserState(
    val isLogin: Boolean = false,
    val face: String = "",
    val name: String = "",
    val mid: Long = 0,
    val level: Int = 0,
    val coin: Double = 0.0,
    val bcoin: Double = 0.0,
    val following: Int = 0,
    val follower: Int = 0,
    val dynamic: Int = 0,
    val isVip: Boolean = false,
    val vipLabel: String = ""
)

data class HomeUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val user: UserState = UserState()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private var refreshIdx = 0

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            fetchData(isLoadMore = false)
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshIdx++
            fetchData(isLoadMore = false)
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _isRefreshing.value) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            refreshIdx++
            fetchData(isLoadMore = true)
        }
    }

    private suspend fun fetchData(isLoadMore: Boolean) {
        // 🔥🔥 [核心修复]：每次刷新都重新获取用户信息，确保状态同步
        // 并行请求视频列表和用户信息
        val videoResult = VideoRepository.getHomeVideos(refreshIdx)
        val navResult = VideoRepository.getNavInfo()

        // 更新 UserState
        var newUserState = _uiState.value.user
        navResult.onSuccess { navData ->
            if (navData.isLogin) {
                // 登录成功
                newUserState = UserState(
                    isLogin = true,
                    face = navData.face,
                    name = navData.uname,
                    level = navData.level_info.current_level,
                    coin = navData.money,
                    bcoin = navData.wallet.bcoin_balance,
                    isVip = navData.vip.status == 1
                )
            } else {
                // 🔥🔥 接口明确返回未登录，强制重置为 Guest
                newUserState = UserState(isLogin = false)
            }
        }.onFailure {
            // 网络彻底失败，如果是 LoadMore 不用管，如果是刷新且没数据，可以考虑重置
        }

        if (isLoadMore) delay(300)

        videoResult.onSuccess { videos ->
            val validVideos = videos.filter { it.bvid.isNotEmpty() && it.title.isNotEmpty() }
            if (validVideos.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    videos = if (isLoadMore) _uiState.value.videos + validVideos else validVideos,
                    isLoading = false,
                    user = newUserState, // 应用最新的用户状态
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = newUserState,
                    error = if (!isLoadMore && _uiState.value.videos.isEmpty()) "没有更多推荐了" else null
                )
            }
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = if (!isLoadMore && _uiState.value.videos.isEmpty()) error.message ?: "网络错误" else null,
                user = newUserState
            )
        }
    }
}