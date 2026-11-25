package com.android.purebilibili.feature.settings.webdav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebDavBackupUiState(
    val config: WebDavBackupConfig = WebDavBackupConfig(),
    val remoteBackups: List<WebDavBackupEntry> = emptyList(),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val restoreRequiresRestart: Boolean = false
)

class WebDavBackupViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val service = WebDavBackupService(appContext)

    private val _uiState = MutableStateFlow(WebDavBackupUiState())
    val uiState: StateFlow<WebDavBackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            WebDavBackupStore.getConfig(appContext).collectLatest { config ->
                WebDavAutoBackupScheduler.sync(appContext, config)
                _uiState.update { it.copy(config = config) }
            }
        }
    }

    fun saveConfig(config: WebDavBackupConfig) {
        viewModelScope.launch {
            WebDavBackupStore.setConfig(appContext, config)
            _uiState.update {
                it.copy(
                    statusMessage = "WebDAV 配置已保存",
                    restoreRequiresRestart = false
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            WebDavBackupStore.setEnabled(appContext, enabled)
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun testConnection() {
        runAction("正在测试连接…") { config ->
            service.testConnection(config)
                .fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                statusMessage = "连接成功",
                                restoreRequiresRestart = false
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                statusMessage = error.message ?: "连接失败",
                                restoreRequiresRestart = false
                            )
                        }
                    }
                )
        }
    }

    fun refreshRemoteBackups() {
        runAction("正在读取远端备份…") { config ->
            service.listBackups(config)
                .fold(
                    onSuccess = { list ->
                        _uiState.update {
                            it.copy(
                                remoteBackups = list,
                                statusMessage = if (list.isEmpty()) "远端暂无备份" else "已读取 ${list.size} 条备份",
                                restoreRequiresRestart = false
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                statusMessage = error.message ?: "读取失败",
                                restoreRequiresRestart = false
                            )
                        }
                    }
                )
        }
    }

    fun backupNow() {
        runAction("正在上传备份…") { config ->
            service.backupNow(config)
                .fold(
                    onSuccess = { entry ->
                        _uiState.update { current ->
                            val merged = (current.remoteBackups + entry)
                                .distinctBy { it.fileName }
                                .sortedByDescending { it.lastModifiedEpochMs }
                            current.copy(
                                remoteBackups = merged,
                                statusMessage = "备份完成：${entry.fileName}",
                                restoreRequiresRestart = false
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                statusMessage = error.message ?: "备份失败",
                                restoreRequiresRestart = false
                            )
                        }
                    }
                )
        }
    }

    fun restoreLatest() {
        runAction("正在恢复最新备份…") { config ->
            service.restoreLatest(config)
                .fold(
                    onSuccess = { entry ->
                        _uiState.update {
                            it.copy(
                                statusMessage = "恢复完成：${entry.fileName}，重启应用后生效",
                                restoreRequiresRestart = true
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                statusMessage = error.message ?: "恢复失败",
                                restoreRequiresRestart = false
                            )
                        }
                    }
                )
        }
    }

    private fun runAction(loadingMessage: String, action: suspend (WebDavBackupConfig) -> Unit) {
        val config = _uiState.value.config
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    statusMessage = loadingMessage,
                    restoreRequiresRestart = false
                )
            }
            try {
                action(config)
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }
}

internal class WebDavBackupViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WebDavBackupViewModel::class.java)) {
            return WebDavBackupViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
