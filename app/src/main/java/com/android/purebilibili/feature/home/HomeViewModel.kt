// 文件路径: feature/home/HomeViewModel.kt
package com.android.purebilibili.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 🔥 核心修改：扩充 UserState 以支持更多字段
data class UserState(
    val isLogin: Boolean = false,
    val face: String = "",
    val name: String = "",
    // 👇 新增详细字段
    val mid: Long = 0,
    val level: Int = 0,
    val coin: Double = 0.0,   // 硬币
    val bcoin: Double = 0.0,  // B币
    val following: Int = 0,   // 关注数
    val follower: Int = 0,    // 粉丝数
    val dynamic: Int = 0,     // 动态数
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

    private var refreshIdx = 1

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            fetchData()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshIdx++
            _uiState.value = _uiState.value.copy(isLoading = true)
            delay(500)
            fetchData()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchData() {
        var currentUser = _uiState.value.user
        try {
            // 这里只请求简单的 Nav 信息用于首页头像显示
            // 完整信息在 ProfileViewModel 中获取
            val navResp = NetworkModule.api.getNavInfo()
            val data = navResp.data
            if (data != null && data.isLogin) {
                currentUser = UserState(isLogin = true, face = data.face, name = data.uname)
            } else {
                currentUser = UserState(isLogin = false)
            }
        } catch (e: Exception) {
            // Ignore
        }

        val result = VideoRepository.getHomeVideos(refreshIdx)

        result.onSuccess { videos ->
            val validVideos = videos.filter { it.bvid.isNotEmpty() && it.title.isNotEmpty() }
            if (validVideos.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    videos = validVideos,
                    isLoading = false,
                    user = currentUser
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = currentUser,
                    error = if (_uiState.value.videos.isEmpty()) "没有更多推荐了" else null
                )
            }
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = error.message ?: "网络错误",
                user = currentUser
            )
        }
    }
}