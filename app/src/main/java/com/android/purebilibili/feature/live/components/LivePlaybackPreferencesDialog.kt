package com.android.purebilibili.feature.live.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.feature.live.livePreferredQualityOptions
import com.android.purebilibili.feature.live.normalizeLiveCdnHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LivePlaybackPreferencesDialog(onDismiss: () -> Unit, onQualityChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wifi by SettingsManager.getLivePreferredQuality(context, true).collectAsStateWithLifecycle(initialValue = 10000)
    val mobile by SettingsManager.getLivePreferredQuality(context, false).collectAsStateWithLifecycle(initialValue = 250)
    val scEnabled by SettingsManager.getLiveSuperChatEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val autoMiniEnabled by SettingsManager.getLiveAutoMiniPlayerEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val autoWearMedal by SettingsManager.getLiveAutoWearMedal(context).collectAsStateWithLifecycle(initialValue = false)
    val cdnHost by SettingsManager.getLiveCdnHost(context).collectAsStateWithLifecycle(initialValue = null)
    var editingHost by remember { mutableStateOf(false) }
    var hostDraft by remember { mutableStateOf("") }
    var hostError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "保存失败，请重试", Toast.LENGTH_SHORT).show()
            } finally { saving = false }
        }
    }
    if (editingHost) {
        AppAlertDialog(
            onDismissRequest = { if (!saving) editingHost = false },
            title = { AppText("直播 CDN 域名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedTextField(
                        value = hostDraft,
                        onValueChange = { hostDraft = it; hostError = null },
                        singleLine = true,
                        enabled = !saving,
                        label = { AppText("域名及可选端口") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppText(hostError ?: "留空使用原始线路；保留播放路径和鉴权参数。", color = AppContentColors.secondaryText())
                }
            },
            confirmButton = {
                AppTextButton(enabled = !saving, onClick = {
                    normalizeLiveCdnHost(hostDraft).onSuccess { host ->
                        save {
                            SettingsManager.setLiveCdnHost(context, host)
                            editingHost = false
                            onQualityChanged()
                        }
                    }.onFailure { hostError = it.message }
                }) { AppText("保存") }
            },
            dismissButton = { AppTextButton(enabled = !saving, onClick = { editingHost = false }) { AppText("取消") } },
        )
        return
    }
    AppAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { AppText("直播偏好") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveQualityPreferenceRow("Wi-Fi 画质", wifi, !saving) { quality ->
                    save {
                        SettingsManager.setLivePreferredQuality(context, quality, true)
                        if (NetworkUtils.isWifi(context)) onQualityChanged()
                    }
                }
                LiveQualityPreferenceRow("流量画质", mobile, !saving) { quality ->
                    save {
                        SettingsManager.setLivePreferredQuality(context, quality, false)
                        if (!NetworkUtils.isWifi(context)) onQualityChanged()
                    }
                }
                AppText("可用画质受直播间及账号权限限制。", color = AppContentColors.secondaryText())
                AppTextButton(enabled = !saving, onClick = { hostDraft = cdnHost.orEmpty(); hostError = null; editingHost = true }) {
                    AppText("直播 CDN 域名：${cdnHost ?: "默认"}")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        AppText("退出直播间时开启小窗")
                        AppText("返回列表后通过应用内小窗继续观看", color = AppContentColors.secondaryText())
                    }
                    AppSwitch(checked = autoMiniEnabled, enabled = !saving, onCheckedChange = { enabled ->
                        save { SettingsManager.setLiveAutoMiniPlayerEnabled(context, enabled) }
                    })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppText("显示醒目留言")
                    AppSwitch(checked = scEnabled, enabled = !saving, onCheckedChange = { enabled ->
                        save { SettingsManager.setLiveSuperChatEnabled(context, enabled) }
                    })
                }
                AppSwitchPreference(
                    title = "自动佩戴当前主播的勋章",
                    subtitle = "进入直播间时切换到已拥有的粉丝勋章",
                    checked = autoWearMedal,
                    enabled = !saving,
                    onCheckedChange = { enabled ->
                        save { SettingsManager.setLiveAutoWearMedal(context, enabled) }
                    },
                )
            }
        },
        confirmButton = { AppTextButton(enabled = !saving, onClick = onDismiss) { AppText("完成") } },
    )
}

@Composable
private fun LiveQualityPreferenceRow(title: String, quality: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = livePreferredQualityOptions.firstOrNull { it.first == quality }?.second ?: "画质 $quality"
    com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { expanded = true }, enabled = enabled) {
        AppTextButton(enabled = enabled, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { AppText("$title：$label") }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            livePreferredQualityOptions.forEach { (code, name) ->
                AppDropdownMenuItem(selected = code == quality, text = { AppText(if (code == quality) "$name · 当前" else name) }, onClick = { expanded = false; onSelect(code) })
            }
        }
    }
}
