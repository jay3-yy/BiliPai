package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.plugin.sdk.CdnLineDiagnostic
import com.android.purebilibili.feature.plugin.CdnTransferControls
import com.android.purebilibili.feature.plugin.CdnTransferPanel
import com.android.purebilibili.feature.plugin.CdnTransferRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Wiring for the native player settings sheet; the shared diagnostics panel renders plain state. */
@Composable
internal fun PlaybackCdnDiagnostics(
    diagnostics: List<CdnLineDiagnostic>,
    checking: Boolean,
    canCheck: Boolean,
    onCheck: () -> Unit,
    onSwitchTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by CdnTransferRuntime.state.collectAsStateWithLifecycle()
    val plugin = PluginManager.getEnabledPlugin(CdnTransferControls::class)
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = canCheck,
            onClick = {
                CdnTransferRuntime.restoreRoutes()
                val best = diagnostics.maxByOrNull { diagnostic ->
                    state.nodes.firstOrNull { it.host == diagnostic.host }?.speedBps
                        ?: ((diagnostic.speedKbps ?: 0) * 125L)
                }
                best?.let { onSwitchTo(it.index) }
                message = "已恢复自动选线，后续请求按节点表现调度"
            }
        ) {
            AppText(if (state.preferredHost == null) "自动选线" else "恢复自动选线")
        }
        if (plugin == null) AppText("启用 CDN 智能选线插件后，可查看实时传输状态并使用并发下载；手动切线仍可使用。")
        message?.let { AppText(it) }
        CdnTransferPanel(
            state = state,
            diagnostics = diagnostics,
            checking = checking,
            canCheck = canCheck,
            parallelEnabled = state.parallelEnabled,
            experimentalRewriteEnabled = plugin?.canUseParallelDownload() != true,
            onParallelChange = { enabled ->
                scope.launch {
                    try {
                        plugin?.setParallelDownloadEnabled(enabled)
                        val cellular = plugin?.currentParallelConfig()?.allowCellular == true
                        message = if (enabled) {
                            if (cellular) "并发下载已开启，已在 Wi-Fi 与移动网络生效" else "并发下载已开启，仅在 Wi-Fi 下生效"
                        } else {
                            "并发下载已关闭"
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        message = "保存失败，请重试"
                    }
                }
            },
            onMirrorModeChange = if (plugin != null) { mode ->
                scope.launch {
                    try {
                        plugin.setParallelMirrorMode(mode)
                        message = "多节点模式已切换为${mode.label}"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        message = "保存失败，请重试"
                    }
                }
            } else null,
            onConcurrencyPresetChange = if (plugin != null) { preset ->
                scope.launch {
                    try {
                        plugin.setParallelConcurrencyPreset(preset)
                        message = if (preset.isAuto) {
                            "并发线程已切换为自动（最高 ${preset.maxConnections} 线程）"
                        } else {
                            "并发上限已设为 ${preset.maxConnections} 线程"
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        message = "保存失败，请重试"
                    }
                }
            } else null,
            onAllowCellularChange = if (plugin != null) { allow ->
                scope.launch {
                    try {
                        plugin.setParallelAllowCellular(allow)
                        message = if (allow) "已允许在移动网络使用并发下载" else "已恢复仅在 Wi-Fi 下使用并发下载"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        message = "保存失败，请重试"
                    }
                }
            } else null,
            onCheck = onCheck,
            onAvoid = CdnTransferRuntime::avoid,
            onRestore = CdnTransferRuntime::restoreRoutes,
            parallelUnavailableReason = if (plugin == null) "启用 CDN 智能选线插件后可使用并发下载" else null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
