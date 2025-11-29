// 文件路径: feature/home/HomeViewModel.kt
package com.android.purebilibili.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
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

    // 记录当前的 index，用于请求 API
    private var refreshIdx = 0

    init {
        loadData()
    }

    // 1. 初始加载
    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            fetchData(isLoadMore = false)
        }
    }

    // 2. 下拉刷新 (重置列表)
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            // 刷新时 idx 自增，获取新的一批推荐
            refreshIdx++
            // 注意：刷新时不设置全局 isLoading，避免白屏，而是依靠 PullToRefresh 组件的状态
            fetchData(isLoadMore = false)
            _isRefreshing.value = false
        }
    }

    // 3. 🔥 新增：上拉加载更多 (追加列表)
    fun loadMore() {
        // 如果正在加载或正在刷新，则忽略
        if (_uiState.value.isLoading || _isRefreshing.value) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true) // 底部显示 Loading
            refreshIdx++
            fetchData(isLoadMore = true)
        }
    }

    // 🔥 核心逻辑：区分追加还是替换
    private suspend fun fetchData(isLoadMore: Boolean) {
        var currentUser = _uiState.value.user

        // 尝试获取用户信息 (仅在未登录或信息为空时尝试，避免频繁请求)
        if (!currentUser.isLogin) {
            try {
                val navResp = NetworkModule.api.getNavInfo()
                val data = navResp.data
                if (data != null && data.isLogin) {
                    currentUser = UserState(isLogin = true, face = data.face, name = data.uname)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 模拟一点延迟，让加载动画不至于一闪而过
        if (isLoadMore) delay(300)

        val result = VideoRepository.getHomeVideos(refreshIdx)

        result.onSuccess { videos ->
            val validVideos = videos.filter { it.bvid.isNotEmpty() && it.title.isNotEmpty() }

            if (validVideos.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    // 如果是 LoadMore，则将旧数据 + 新数据；否则直接替换
                    videos = if (isLoadMore) _uiState.value.videos + validVideos else validVideos,
                    isLoading = false,
                    user = currentUser,
                    error = null
                )
            } else {
                // 如果没有新数据返回
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = currentUser,
                    // 只有在列表为空且加载失败时才显示全屏错误，否则只是停止加载动画
                    error = if (!isLoadMore && _uiState.value.videos.isEmpty()) "没有更多推荐了" else null
                )
            }
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                // 只有在初始加载失败时显示全屏错误，LoadMore 失败则暂时忽略或弹出 Toast (这里简化处理)
                error = if (!isLoadMore && _uiState.value.videos.isEmpty()) error.message ?: "网络错误" else null,
                user = currentUser
            )
        }
    }
}