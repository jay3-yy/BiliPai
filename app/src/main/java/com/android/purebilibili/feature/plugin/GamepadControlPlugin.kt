package com.android.purebilibili.feature.plugin

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.core.input.gamepad.GamepadConfig
import com.android.purebilibili.core.input.gamepad.GamepadControlEngine
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "GamepadControlPlugin"

/**
 * 多型号手柄全局操控内置插件。
 *
 * 在「插件中心」中统一管理，支持 Xbox / PlayStation / Switch / 通用手柄、
 * 隐藏底部键位显示、虚拟光标外观与自定义键位映射。
 */
class GamepadControlPlugin : BuiltinPlugin {

    override val id: String = BuiltinPluginIds.GAMEPAD_CONTROL
    override val name: String = "手柄控制"
    override val description: String = "支持 Xbox / PlayStation / Switch 等手柄双摇杆操控、隐藏底部键位与自定义键位"
    override val version: String = "2.0.0"
    override val author: String = "longdaydream40"
    override val icon: ImageVector = Icons.Outlined.SportsEsports

    override suspend fun onEnable() {
        val context = PluginManager.getContext()
        ensurePersistCallback(context)
        loadPersistedConfig(context)
        GamepadControlEngine.setPluginEnabled(true)
        Logger.d(TAG, "手柄控制插件已启用")
    }

    override suspend fun onDisable() {
        GamepadControlEngine.setPluginEnabled(false)
        Logger.d(TAG, "手柄控制插件已禁用")
    }

    internal fun snapshotConfig(): GamepadConfig = GamepadControlEngine.configFlow.value

    internal suspend fun loadConfigSuspend() {
        loadPersistedConfig(PluginManager.getContext())
    }

    internal fun commitConfig(newConfig: GamepadConfig) {
        commitConfig(PluginManager.getContext(), newConfig)
    }

    @Composable
    override fun SettingsContent() {
        SettingsContent(Modifier.fillMaxWidth())
    }

    @Composable
    override fun SettingsContent(modifier: Modifier) {
        GamepadControlSettings(plugin = this, modifier = modifier)
    }

    companion object {
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val storeMutex = Mutex()
        private val configRevision = AtomicLong(0L)

        @Volatile
        private var hasLoadedFromStore = false

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private fun ensurePersistCallback(context: Context) {
            val appContext = context.applicationContext
            GamepadControlEngine.setConfigPersistCallback { updated ->
                commitConfig(appContext, updated)
            }
        }

        suspend fun loadPersistedConfig(context: Context): GamepadConfig {
            val appContext = context.applicationContext
            ensurePersistCallback(appContext)
            if (hasLoadedFromStore) {
                return GamepadControlEngine.configFlow.value
            }
            return storeMutex.withLock {
                if (hasLoadedFromStore) {
                    return@withLock GamepadControlEngine.configFlow.value
                }
                val revisionBeforeRead = configRevision.get()
                try {
                    val raw = PluginStore.getConfigJson(appContext, BuiltinPluginIds.GAMEPAD_CONTROL)
                    val parsed = if (!raw.isNullOrBlank()) {
                        json.decodeFromString<GamepadConfig>(raw)
                    } else {
                        GamepadConfig()
                    }
                    hasLoadedFromStore = true
                    // 若在读取磁盘期间用户已经产生了新的内存提交，则不覆盖最新配置。
                    if (configRevision.get() == revisionBeforeRead) {
                        GamepadControlEngine.updateConfig(parsed)
                    }
                    GamepadControlEngine.configFlow.value
                } catch (e: Exception) {
                    Logger.e(TAG, "加载手柄控制配置失败", e)
                    GamepadControlEngine.configFlow.value
                }
            }
        }

        fun commitConfig(context: Context, newConfig: GamepadConfig) {
            val appContext = context.applicationContext
            ensurePersistCallback(appContext)
            hasLoadedFromStore = true
            val targetRevision = configRevision.incrementAndGet()
            GamepadControlEngine.updateConfig(newConfig)
            ioScope.launch {
                storeMutex.withLock {
                    // 仅落盘最新快照，避免高频滑杆拖动时旧写入乱序覆盖新配置。
                    if (configRevision.get() != targetRevision) return@withLock
                    try {
                        PluginStore.setConfigJson(
                            context = appContext,
                            pluginId = BuiltinPluginIds.GAMEPAD_CONTROL,
                            configJson = json.encodeToString(newConfig),
                        )
                    } catch (e: Exception) {
                        Logger.e(TAG, "保存手柄控制配置失败", e)
                    }
                }
            }
        }
    }
}
