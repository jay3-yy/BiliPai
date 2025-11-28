package com.android.purebilibili.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val autoPlay: Boolean = true,
    val hwDecode: Boolean = true,
    val darkMode: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        // 启动时监听所有设置的变化
        viewModelScope.launch {
            SettingsManager.getAutoPlay(application).collect { val_ ->
                _state.value = _state.value.copy(autoPlay = val_)
            }
        }
        viewModelScope.launch {
            SettingsManager.getHwDecode(application).collect { val_ ->
                _state.value = _state.value.copy(hwDecode = val_)
            }
        }
        viewModelScope.launch {
            SettingsManager.getDarkMode(application).collect { val_ ->
                _state.value = _state.value.copy(darkMode = val_)
            }
        }
    }

    fun toggleAutoPlay(value: Boolean) {
        viewModelScope.launch { SettingsManager.setAutoPlay(getApplication(), value) }
    }

    fun toggleHwDecode(value: Boolean) {
        viewModelScope.launch { SettingsManager.setHwDecode(getApplication(), value) }
    }

    fun toggleDarkMode(value: Boolean) {
        viewModelScope.launch { SettingsManager.setDarkMode(getApplication(), value) }
    }
}