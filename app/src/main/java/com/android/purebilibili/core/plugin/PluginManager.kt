// 文件路径: core/plugin/PluginManager.kt
package com.android.purebilibili.core.plugin

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private const val TAG = "PluginManager"

internal fun consumePendingPluginEnabledState(
    pluginId: String,
    storedEnabled: Boolean,
    pendingEnabledOverrides: MutableMap<String, Boolean>
): Boolean {
    return pendingEnabledOverrides.remove(pluginId) ?: storedEnabled
}

/**
 *  插件管理器
 * 
 * 负责管理所有插件的注册、启用/禁用、生命周期调用等。
 * 使用单例模式，在 Application 启动时初始化。
 */
object PluginManager {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingEnabledOverrides = mutableMapOf<String, Boolean>()
    
    /** 所有已注册插件 */
    private val _plugins = mutableStateListOf<PluginInfo>()
    val plugins: List<PluginInfo> get() = _plugins.toList()
    
    /** 插件列表状态流 (用于 Compose 监听) */
    private val _pluginsFlow = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsFlow: StateFlow<List<PluginInfo>> = _pluginsFlow.asStateFlow()

    private val _readyPluginIds = MutableStateFlow<Set<String>>(emptySet())

    /** 弹幕插件更新信号（用于播放中热刷新当前弹幕） */
    private val _danmakuPluginUpdateToken = MutableStateFlow(0L)
    val danmakuPluginUpdateToken: StateFlow<Long> = _danmakuPluginUpdateToken.asStateFlow()
    
    private var isInitialized = false
    private lateinit var appContext: Context
    
    /**
     * 初始化插件管理器
     * 应在 Application.onCreate() 中调用
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        Logger.d(TAG, " PluginManager initialized")
    }
    
    /** 获取Application Context供插件使用 */
    fun getContext(): Context = appContext
    
    /**
     * 注册插件
     * 内置插件在 Application 中注册
     */
    fun register(plugin: Plugin) {
        if (_plugins.any { it.plugin.id == plugin.id }) {
            Logger.w(TAG, " Plugin already registered: ${plugin.id}")
            return
        }
        
        scope.launch {
            try {
                val storedEnabled = PluginStore.isEnabled(appContext, plugin.id)
                val enabled = consumePendingPluginEnabledState(
                    pluginId = plugin.id,
                    storedEnabled = storedEnabled,
                    pendingEnabledOverrides = pendingEnabledOverrides
                )
                val info = PluginInfo(plugin, enabled)
                _plugins.add(info)
                _pluginsFlow.value = _plugins.toList()

                if (enabled) {
                    try {
                        plugin.onEnable()
                        Logger.d(TAG, " Plugin enabled on start: ${plugin.name}")
                    } catch (e: Exception) {
                        Logger.e(TAG, " Failed to enable plugin: ${plugin.name}", e)
                    }
                }

                Logger.d(TAG, " Plugin registered: ${plugin.name} (enabled=$enabled)")
            } catch (e: Exception) {
                Logger.e(TAG, " Failed to register plugin: ${plugin.name}", e)
            } finally {
                _readyPluginIds.update { it + plugin.id }
            }
        }
    }

    /** Wait until a built-in plugin has finished restoring its persisted configuration. */
    suspend fun awaitPluginReady(pluginId: String) {
        _readyPluginIds.first { pluginId in it }
    }
    
    /**
     * 启用/禁用插件
     */
    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        val index = _plugins.indexOfFirst { it.plugin.id == pluginId }
        if (index == -1) {
            pendingEnabledOverrides[pluginId] = enabled
            PluginStore.setEnabled(appContext, pluginId, enabled)
            Logger.d(TAG, " Deferring plugin enabled change until registration: $pluginId -> $enabled")
            return
        }
        
        val info = _plugins[index]
        val plugin = info.plugin
        if (info.enabled == enabled) return
        
        try {
            if (enabled && !info.enabled) {
                plugin.onEnable()
                Logger.d(TAG, " Plugin enabled: ${plugin.name}")
            } else if (!enabled && info.enabled) {
                plugin.onDisable()
                Logger.d(TAG, "🔴 Plugin disabled: ${plugin.name}")
            }
            
            // 更新状态
            _plugins[index] = info.copy(enabled = enabled)
            _pluginsFlow.value = _plugins.toList()

            if (plugin is DanmakuPlugin) {
                notifyDanmakuPluginsUpdated()
            }
            
            // 持久化
            PluginStore.setEnabled(appContext, pluginId, enabled)
            
        } catch (e: Exception) {
            Logger.e(TAG, " Failed to toggle plugin: ${plugin.name}", e)
        }
    }
    
    /**
     * 获取指定类型的所有已启用插件
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Plugin> getEnabledPlugins(type: KClass<T>): List<T> {
        return _plugins
            .filter { it.enabled && type.isInstance(it.plugin) }
            .map { it.plugin as T }
    }
    
    /**
     * 获取所有 PlayerPlugin
     */
    fun getEnabledPlayerPlugins(): List<PlayerPlugin> = getEnabledPlugins(PlayerPlugin::class)
    
    /**
     * 获取所有 DanmakuPlugin
     */
    fun getEnabledDanmakuPlugins(): List<DanmakuPlugin> = getEnabledPlugins(DanmakuPlugin::class)
    
    /**
     * 获取所有 FeedPlugin
     */
    fun getEnabledFeedPlugins(): List<FeedPlugin> = getEnabledPlugins(FeedPlugin::class)

    /**
     * 获取所有已启用的 CastPluginApi 插件
     */
    fun getEnabledCastPlugins(): List<CastPluginApi> = getEnabledPlugins(CastPluginApi::class)
    
    /**
     *  使用所有启用的 FeedPlugin 过滤视频列表
     * 用于首页推荐和搜索结果
     *
     * @param feedKind 信息流来源(首页推荐/热门/排行/分区/搜索), 默认 GENERIC
     */
    fun filterFeedItems(
        items: List<com.android.purebilibili.data.model.response.VideoItem>,
        feedKind: FeedKind = FeedKind.GENERIC
    ): List<com.android.purebilibili.data.model.response.VideoItem> {
        val feedPlugins = getEnabledFeedPlugins()
        if (feedPlugins.isEmpty()) return items
        
        return items.filter { item ->
            feedPlugins.all { plugin ->
                try {
                    plugin.shouldShowItem(item, feedKind)
                } catch (e: Exception) {
                    Logger.e(TAG, " Feed plugin failed: ${plugin.name}", e)
                    true
                }
            }
        }
    }
    
    /**
     * 获取已启用插件数量
     */
    fun getEnabledCount(): Int = _plugins.count { it.enabled }

    fun notifyDanmakuPluginsUpdated() {
        _danmakuPluginUpdateToken.value = System.currentTimeMillis()
    }
}

/**
 * 插件信息包装类
 */
data class PluginInfo(
    val plugin: Plugin,
    val enabled: Boolean
)
