package com.android.purebilibili.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.feature.home.UserState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val user: UserState) : ProfileUiState()
    // LoggedOut 代表“当前是游客/未登录状态”，UI 应该显示“去登录”
    object LoggedOut : ProfileUiState()
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                // 1. 检查本地是否有 Token，如果没有直接设为 LoggedOut
                if (TokenManager.sessDataCache.isNullOrEmpty()) {
                    _uiState.value = ProfileUiState.LoggedOut
                    return@launch
                }

                _uiState.value = ProfileUiState.Loading

                // 2. 并行请求：基本信息 + 统计信息
                val navDeferred = async { NetworkModule.api.getNavInfo() }
                val statDeferred = async { NetworkModule.api.getNavStat() }

                val navResp = navDeferred.await()
                val statResp = statDeferred.await()

                val data = navResp.data
                val statData = statResp.data

                // 3. 判断是否登录有效
                if (data != null && data.isLogin) {
                    _uiState.value = ProfileUiState.Success(
                        UserState(
                            isLogin = true,
                            face = data.face,
                            name = data.uname,
                            mid = data.mid,
                            level = data.level_info.current_level,
                            coin = data.money,
                            bcoin = data.wallet.bcoin_balance,
                            isVip = data.vip.status == 1,
                            vipLabel = data.vip.label.text,
                            // 绑定统计数据
                            following = statData?.following ?: 0,
                            follower = statData?.follower ?: 0,
                            dynamic = statData?.dynamic_count ?: 0
                        )
                    )
                } else {
                    // Cookie 过期或无效
                    TokenManager.clear(getApplication())
                    _uiState.value = ProfileUiState.LoggedOut
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 网络错误也暂时显示未登录，或者你可以加一个 Error 状态重试
                _uiState.value = ProfileUiState.LoggedOut
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            TokenManager.clear(getApplication())
            _uiState.value = ProfileUiState.LoggedOut
        }
    }
}