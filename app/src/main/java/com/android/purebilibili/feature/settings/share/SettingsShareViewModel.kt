package com.android.purebilibili.feature.settings.share

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsShareUiState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val pendingImportSession: SettingsShareImportSession? = null,
    val pendingShareUri: Uri? = null,
    /** 导出时附带设备/显示调试信息，默认开启供 UI 问题排查。 */
    val includeDeviceDebug: Boolean = true,
    val savedProfiles: ImmutableList<SavedSettingsProfile> = persistentListOf(),
)

class SettingsShareViewModel(
    application: Application,
    private val service: SettingsShareServiceContract =
        SettingsShareService(application.applicationContext)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        service = SettingsShareService(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(SettingsShareUiState())
    val uiState: StateFlow<SettingsShareUiState> = _uiState.asStateFlow()

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun setIncludeDeviceDebug(enabled: Boolean) {
        _uiState.update { it.copy(includeDeviceDebug = enabled) }
    }

    fun loadSavedProfiles() {
        viewModelScope.launch {
            service.listSavedProfiles().onSuccess { profiles ->
                val immutableProfiles = profiles.toImmutableList()
                _uiState.update { it.copy(savedProfiles = immutableProfiles) }
            }
        }
    }

    fun saveCurrentProfile(name: String) {
        runAction("正在保存配置…", "保存配置失败，请稍后重试") {
            service.saveCurrentProfile(name).fold(
                onSuccess = { profile ->
                    _uiState.update { current ->
                        current.copy(
                            savedProfiles = (listOf(profile) + current.savedProfiles
                                .filterNot { it.fileName == profile.fileName }).toImmutableList(),
                            statusMessage = "已保存配置：${profile.name}",
                        )
                    }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _uiState.update {
                        it.copy(statusMessage = error.message ?: "保存配置失败，请稍后重试")
                    }
                },
            )
        }
    }

    fun restoreSavedProfile(profile: SavedSettingsProfile) {
        runAction("正在恢复配置…", "恢复配置失败，请稍后重试") {
            service.restoreSavedProfile(profile).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(statusMessage = "已恢复 ${profile.name}（${result.appliedKeys.size} 项）")
                    }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _uiState.update {
                        it.copy(statusMessage = error.message ?: "恢复配置失败，请稍后重试")
                    }
                },
            )
        }
    }

    fun exportToUri(uri: Uri) {
        val includeDeviceDebug = _uiState.value.includeDeviceDebug
        runAction(
            loadingMessage = "正在导出设置…",
            fallbackError = "导出失败，请稍后重试"
        ) {
            service.exportToUri(uri = uri, includeDeviceDebug = includeDeviceDebug)
                .fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                statusMessage = if (includeDeviceDebug) {
                                    "已导出设置文件（含设备调试信息）"
                                } else {
                                    "已导出设置文件"
                                },
                                pendingShareUri = null
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        _uiState.update {
                            it.copy(statusMessage = error.message ?: "导出失败，请稍后重试")
                        }
                    }
                )
        }
    }

    fun prepareShare() {
        val includeDeviceDebug = _uiState.value.includeDeviceDebug
        runAction(
            loadingMessage = "正在生成分享文件…",
            fallbackError = "分享文件生成失败，请稍后重试"
        ) {
            service.createShareUri(includeDeviceDebug = includeDeviceDebug)
                .fold(
                    onSuccess = { uri ->
                        _uiState.update {
                            it.copy(
                                pendingShareUri = uri,
                                statusMessage = if (includeDeviceDebug) {
                                    "已生成分享文件（含设备调试信息）"
                                } else {
                                    "已生成分享文件"
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        _uiState.update {
                            it.copy(statusMessage = error.message ?: "分享文件生成失败，请稍后重试")
                        }
                    }
                )
        }
    }

    fun consumeShareUri(expectedUri: Uri? = null) {
        _uiState.update {
            if (expectedUri == null || it.pendingShareUri == expectedUri) {
                it.copy(pendingShareUri = null)
            } else {
                it
            }
        }
    }

    fun loadImportPreview(uri: Uri) {
        runAction(
            loadingMessage = "正在读取设置文件…",
            fallbackError = "导入文件读取失败，请检查文件内容"
        ) {
            service.readImportSession(uri)
                .fold(
                    onSuccess = { session ->
                        _uiState.update {
                            it.copy(
                                pendingImportSession = session,
                                statusMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                pendingImportSession = null,
                                statusMessage = error.message ?: "导入文件读取失败，请检查文件内容"
                            )
                        }
                    }
                )
        }
    }

    fun dismissImportPreview() {
        _uiState.update { it.copy(pendingImportSession = null) }
    }

    fun confirmImport() {
        val session = _uiState.value.pendingImportSession ?: return
        runAction(
            loadingMessage = "正在应用设置…",
            fallbackError = "导入失败，请稍后重试"
        ) {
            service.applyImport(session)
                .fold(
                    onSuccess = { result ->
                        val appliedCount = result.appliedKeys.size
                        val skippedCount = result.skippedKeys.size
                        _uiState.update {
                            it.copy(
                                pendingImportSession = null,
                                statusMessage = buildImportStatusMessage(appliedCount, skippedCount)
                            )
                        }
                    },
                    onFailure = { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        _uiState.update {
                            it.copy(statusMessage = error.message ?: "导入失败，请稍后重试")
                        }
                    }
                )
        }
    }

    internal fun setPendingImportSessionForTest(session: SettingsShareImportSession?) {
        _uiState.update { it.copy(pendingImportSession = session) }
    }

    private fun runAction(
        loadingMessage: String,
        fallbackError: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    statusMessage = loadingMessage
                )
            }
            try {
                action()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(statusMessage = error.message ?: fallbackError)
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }
}

internal class SettingsShareViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsShareViewModel::class.java)) {
            return SettingsShareViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun buildImportStatusMessage(appliedCount: Int, skippedCount: Int): String {
    return if (skippedCount > 0) {
        "已导入 $appliedCount 项设置，已跳过 $skippedCount 项"
    } else {
        "已导入 $appliedCount 项设置"
    }
}
