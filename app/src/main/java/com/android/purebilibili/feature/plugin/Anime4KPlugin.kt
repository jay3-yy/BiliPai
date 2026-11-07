package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.plugin.BuiltinPluginIds

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.plugin.sdk.PluginCapabilityManifest
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppSliderPreference
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.plugin.sdk.Anime4KConfig
import com.android.purebilibili.plugin.sdk.Anime4KPreset
import com.android.purebilibili.feature.anime4k.FSR_SHARPNESS_SLIDER_STEPS
import com.android.purebilibili.plugin.sdk.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.anime4k.VideoEnhancementConfigLoadGuard
import com.android.purebilibili.feature.anime4k.decodeVideoEnhancementConfig
import com.android.purebilibili.feature.anime4k.encodeVideoEnhancementConfig
import com.android.purebilibili.feature.anime4k.normalizeFsrSharpness
import com.android.purebilibili.feature.anime4k.resolveConfigAfterRememberAcrossVideosChange
import com.android.purebilibili.feature.anime4k.resolveConfigAfterVideoEnhancementToggle
import com.android.purebilibili.feature.anime4k.resolveAnime4KPresetLabel
import com.android.purebilibili.feature.anime4k.shouldConfirmRememberAcrossVideosChange
import com.android.purebilibili.plugin.sdk.VideoEnhancementPluginApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "Anime4KPlugin"

/**
 * Anime4K 是内置插件：插件层负责配置和可见性，播放器输出仍由 VideoOutputRouter 管理。
 */
class Anime4KPlugin : BuiltinPlugin, VideoEnhancementPluginApi {

    override val id: String = BuiltinPluginIds.VIDEO_ENHANCEMENT
    override val name: String = "画质增强"
    override val description: String = "提供 Anime4K 与 AMD FSR 1.0 实时画质增强"
    override val version: String = "0.4.0"
    override val author: String = "BiliPai项目组"
    override val icon: ImageVector = Icons.Outlined.HighQuality
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = Anime4KPlugin::class.java.name,
        capabilities = emptySet()
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configSaveMutex = Mutex()
    private var config = Anime4KConfig()
    private val configLoadGuard = VideoEnhancementConfigLoadGuard()
    private val _configState = MutableStateFlow(config)
    override val configState: StateFlow<Anime4KConfig> = _configState.asStateFlow()

    override suspend fun onEnable() {
        loadConfig()
        Logger.d(TAG, "画质增强插件已启用")
    }

    override fun setPreset(preset: Anime4KPreset) {
        updateConfig(config.copy(preset = preset))
    }

    override fun setAlgorithm(algorithm: VideoEnhancementAlgorithm) {
        updateConfig(config.copy(algorithm = algorithm))
    }

    override fun setFsrSharpness(strength: Float) {
        updateConfig(config.copy(fsrSharpness = normalizeFsrSharpness(strength)))
    }

    override fun setRememberAcrossVideos(enabled: Boolean, currentVideoEnabled: Boolean) {
        updateConfig(
            resolveConfigAfterRememberAcrossVideosChange(
                config = config,
                rememberAcrossVideos = enabled,
                currentVideoEnabled = currentVideoEnabled
            )
        )
    }

    override fun rememberCurrentVideoEnabled(enabled: Boolean) {
        updateConfig(resolveConfigAfterVideoEnhancementToggle(config, enabled))
    }

    private fun updateConfig(value: Anime4KConfig) {
        if (config == value) return
        configLoadGuard.markLocalChange()
        config = value
        _configState.value = value
        ioScope.launch {
            runCatching {
                configSaveMutex.withLock {
                    // Persist the latest snapshot in write order, not a stale queued setter value.
                    PluginStore.setConfigJson(
                        context = PluginManager.getContext(),
                        pluginId = id,
                        configJson = encodeVideoEnhancementConfig(_configState.value)
                    )
                }
            }.onFailure { error ->
                Logger.e(TAG, "保存画质增强配置失败", error)
            }
        }
    }

    private suspend fun loadConfig() {
        val loadedConfig = runCatching {
            val raw = PluginStore.getConfigJson(PluginManager.getContext(), id)
            if (raw.isNullOrBlank()) {
                Anime4KConfig()
            } else {
                decodeVideoEnhancementConfig(raw)
            }
        }.onFailure { error ->
            Logger.e(TAG, "读取画质增强配置失败", error)
        }.getOrDefault(Anime4KConfig())
        if (!configLoadGuard.shouldApplyLoadedConfig()) {
            Logger.d(TAG, "保留本次进程中刚修改的画质增强配置")
            return
        }
        config = loadedConfig
        _configState.value = config
    }

    @Composable
    override fun SettingsContent() {
        val configSnapshot by configState.collectAsStateWithLifecycle()
        var showRememberWarning by remember { mutableStateOf(false) }
        val anime4kOptions = remember {
            listOf(
                Anime4KPreset.FAST,
                Anime4KPreset.QUALITY
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppText("增强算法", style = MaterialTheme.typography.titleSmall)
            VideoEnhancementAlgorithm.entries.forEach { algorithm ->
                AppFilterChip(
                    selected = configSnapshot.algorithm == algorithm,
                    onClick = { setAlgorithm(algorithm) },
                    label = {
                        AppText(
                            when (algorithm) {
                                VideoEnhancementAlgorithm.ANIME4K -> "Anime4K（动漫）"
                                VideoEnhancementAlgorithm.FSR_1_0 -> "AMD FSR 1.0（通用）"
                            }
                        )
                    }
                )
            }
            AppText(
                text = "HDR、杜比视界、小窗和后台播放会自动使用原始视频输出。",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText()
            )
            if (configSnapshot.algorithm == VideoEnhancementAlgorithm.ANIME4K) {
                AppText("CNN 模型", style = MaterialTheme.typography.titleSmall)
                anime4kOptions.forEach { preset ->
                    AppFilterChip(
                        selected = configSnapshot.preset == preset,
                        onClick = { setPreset(preset) },
                        label = { AppText(resolveAnime4KPresetLabel(preset)) }
                    )
                }
            } else {
                AppSliderPreference(
                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.FSR_SHARPNESS),
                    title = "FSR 锐化强度",
                    subtitle = "默认值来自公开 FSR 1.0 视频实现；过高可能产生锐化边缘。",
                    value = configSnapshot.fsrSharpness,
                    onValueChange = ::setFsrSharpness,
                    valueRange = 0f..1f,
                    steps = FSR_SHARPNESS_SLIDER_STEPS,
                    valueLabel = "${(configSnapshot.fsrSharpness * 100).toInt()}%"
                )
            }

            AppSwitchPreference(
                icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.REMEMBER_FILTER_STATE),
                title = "跨视频记忆开启状态",
                subtitle = if (configSnapshot.rememberAcrossVideos) {
                    "后续视频沿用开关，请留意较高分辨率内容"
                } else {
                    "默认关闭；每个视频需要单独开启"
                },
                checked = configSnapshot.rememberAcrossVideos,
                onCheckedChange = { requestedValue ->
                    if (
                        shouldConfirmRememberAcrossVideosChange(
                            currentValue = configSnapshot.rememberAcrossVideos,
                            requestedValue = requestedValue
                        )
                    ) {
                        showRememberWarning = true
                    } else {
                        setRememberAcrossVideos(
                            enabled = requestedValue,
                            currentVideoEnabled = false
                        )
                    }
                }
            )
        }

        if (showRememberWarning) {
            AppAlertDialog(
                onDismissRequest = { showRememberWarning = false },
                title = { AppText("是否记住后续视频的开关？") },
                text = {
                    AppText(
                        "后续视频会沿用最近一次开关。请留意播放器中的开启状态，" +
                            "避免之后忘记关闭，在较高分辨率视频上持续增强而出现发热、耗电或卡顿。" +
                            "HDR 等自动旁路场景不会执行增强。"
                    )
                },
                confirmButton = {
                    AppTextButton(
                        onClick = {
                            setRememberAcrossVideos(
                                enabled = true,
                                currentVideoEnabled = false
                            )
                            showRememberWarning = false
                        }
                    ) {
                        AppText("开启记忆")
                    }
                },
                dismissButton = {
                    AppTextButton(onClick = { showRememberWarning = false }) {
                        AppText("取消")
                    }
                }
            )
        }
    }
}
