package com.android.purebilibili.feature.settings.webdav

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.feature.settings.backup.AppBackupScope
import com.android.purebilibili.feature.settings.backup.formatBackupSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebDavBackupUiState(
    val config: WebDavBackupConfig = WebDavBackupConfig(),
    val selectedScopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
    val remoteBackups: List<WebDavBackupEntry> = emptyList(),
    val pendingLocalRestore: LocalBackupInspectionSession? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val restoreRequiresRestart: Boolean = false,
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

    fun toggleScope(scope: AppBackupScope) {
        _uiState.update { current ->
            val next = if (scope in current.selectedScopes) {
                if (current.selectedScopes.size <= 1) current.selectedScopes else current.selectedScopes - scope
            } else {
                current.selectedScopes + scope
            }
            current.copy(selectedScopes = next)
        }
    }

    fun saveConfig(config: WebDavBackupConfig) {
        viewModelScope.launch {
            WebDavBackupStore.setConfig(appContext, config)
            _uiState.update {
                it.copy(
                    statusMessage = "WebDAV 配置已保存",
                    restoreRequiresRestart = false,
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            WebDavBackupStore.setEnabled(appContext, enabled)
        }
    }

    fun setMaxRemoteBackups(maxRemoteBackups: Int) {
        viewModelScope.launch {
            WebDavBackupStore.setMaxRemoteBackups(appContext, maxRemoteBackups)
            val label = if (maxRemoteBackups <= 0) "不限制" else "保留最近 $maxRemoteBackups 份"
            _uiState.update {
                it.copy(
                    statusMessage = "云端快照保留策略已更新：$label",
                    restoreRequiresRestart = false,
                )
            }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun dismissLocalRestore() {
        _uiState.update { it.copy(pendingLocalRestore = null) }
    }

    fun exportLocalBackup(uri: Uri) {
        val scopes = _uiState.value.selectedScopes
        runAction("正在导出本地备份…") {
            service.exportLocalBackup(uri, scopes).fold(
                onSuccess = { exported ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "已导出本地备份（${formatBackupSize(exported.sizeBytes)}）",
                            restoreRequiresRestart = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            statusMessage = error.message ?: "本地导出失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun inspectLocalBackup(uri: Uri) {
        runAction("正在校验本地备份包…") {
            service.inspectLocalBackup(uri).fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            pendingLocalRestore = session,
                            statusMessage = "已识别 ${session.inspection.restorableEntryCount} 项可恢复数据，请确认恢复",
                            restoreRequiresRestart = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            pendingLocalRestore = null,
                            statusMessage = error.message ?: "读取本地备份失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun confirmLocalRestore() {
        val session = _uiState.value.pendingLocalRestore ?: return
        val scopes = _uiState.value.selectedScopes
        runAction("正在从本地备份恢复…") {
            service.restoreLocalBackup(session, scopes).fold(
                onSuccess = { report ->
                    _uiState.update {
                        it.copy(
                            pendingLocalRestore = null,
                            statusMessage = "已恢复 ${report.restoredCount} 项数据，重启应用后生效",
                            restoreRequiresRestart = true,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            pendingLocalRestore = null,
                            statusMessage = error.message ?: "本地恢复失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun testConnection() {
        val config = _uiState.value.config
        runAction("正在测试连接…") {
            service.testConnection(config).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            statusMessage = "连接成功",
                            restoreRequiresRestart = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            statusMessage = error.message ?: "连接失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun refreshRemoteBackups() {
        val config = _uiState.value.config
        runAction("正在读取远端备份…") {
            service.listBackups(config).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            remoteBackups = list,
                            statusMessage = if (list.isEmpty()) "远端暂无备份" else "已读取 ${list.size} 条云端备份",
                            restoreRequiresRestart = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            statusMessage = error.message ?: "读取失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun backupNow() {
        val config = _uiState.value.config
        val scopes = _uiState.value.selectedScopes
        runAction("正在上传云端备份…") {
            service.backupNow(config, scopes).fold(
                onSuccess = { entry ->
                    _uiState.update { current ->
                        val merged = (current.remoteBackups + entry)
                            .distinctBy { it.fileName }
                            .sortedByDescending { it.lastModifiedEpochMs }
                        current.copy(
                            remoteBackups = merged,
                            statusMessage = "云端备份完成：${entry.fileName}（${formatBackupSize(entry.sizeBytes)}）",
                            restoreRequiresRestart = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            statusMessage = error.message ?: "云端备份失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun restoreLatest() {
        val config = _uiState.value.config
        val scopes = _uiState.value.selectedScopes
        runAction("正在恢复最新云端备份…") {
            service.restoreLatest(config, scopes).fold(
                onSuccess = { entry ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "恢复完成：${entry.fileName}，重启应用后生效",
                            restoreRequiresRestart = true,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            statusMessage = error.message ?: "恢复失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun restoreRemoteBackup(entry: WebDavBackupEntry) {
        val config = _uiState.value.config
        val scopes = _uiState.value.selectedScopes
        runAction("正在恢复 ${entry.fileName}…") {
            service.restoreEntry(config, entry, scopes).fold(
                onSuccess = { report ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "已恢复 ${entry.fileName}（${report.restoredCount} 项），重启应用后生效",
                            restoreRequiresRestart = true,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            statusMessage = error.message ?: "恢复失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    fun deleteRemoteBackup(entry: WebDavBackupEntry) {
        val config = _uiState.value.config
        runAction("正在删除 ${entry.fileName}…") {
            service.deleteBackup(config, entry).fold(
                onSuccess = {
                    _uiState.update { current ->
                        current.copy(
                            remoteBackups = current.remoteBackups.filterNot { it.fileName == entry.fileName },
                            statusMessage = "已删除云端备份：${entry.fileName}",
                            restoreRequiresRestart = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            statusMessage = error.message ?: "删除失败",
                            restoreRequiresRestart = false,
                        )
                    }
                },
            )
        }
    }

    private fun runAction(loadingMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    statusMessage = loadingMessage,
                    restoreRequiresRestart = false,
                )
            }
            try {
                action()
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
