package com.android.purebilibili.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.CacheUtils // 🔥 确保导入 CacheUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val autoPlay: Boolean = true,
    val hwDecode: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val cacheSize: String = "计算中..." // 🔥 新增：缓存大小状态
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // 🔥 1. 本地状态流：缓存大小
    private val _cacheSize = MutableStateFlow("计算中...")

    // 🔥 2. 将 DataStore 数据与本地缓存状态合并
    val state: StateFlow<SettingsUiState> = combine(
        SettingsManager.getAutoPlay(context),
        SettingsManager.getHwDecode(context),
        SettingsManager.getThemeMode(context),
        _cacheSize // 合并缓存状态
    ) { autoPlay, hwDecode, themeMode, cacheSize ->
        SettingsUiState(
            autoPlay = autoPlay,
            hwDecode = hwDecode,
            themeMode = themeMode,
            cacheSize = cacheSize
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    // 初始化时计算一次缓存
    init {
        refreshCacheSize()
    }

    // --- 功能方法 ---

    // 🔥 计算缓存大小
    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = CacheUtils.getTotalCacheSize(context)
        }
    }

    // 🔥 清理缓存
    fun clearCache() {
        viewModelScope.launch {
            CacheUtils.clearAllCache(context)
            // 清理完后重新计算并更新 UI
            _cacheSize.value = CacheUtils.getTotalCacheSize(context)
        }
    }

    fun toggleAutoPlay(value: Boolean) {
        viewModelScope.launch { SettingsManager.setAutoPlay(context, value) }
    }

    fun toggleHwDecode(value: Boolean) {
        viewModelScope.launch { SettingsManager.setHwDecode(context, value) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { SettingsManager.setThemeMode(context, mode) }
    }
}