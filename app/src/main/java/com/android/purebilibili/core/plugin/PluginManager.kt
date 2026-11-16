// 文件路径: core/plugin/PluginManager.kt
package com.android.purebilibili.core.plugin

import com.android.purebilibili.core.filter.keepsVideo

import android.content.Context
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.plugin.sdk.CastPluginApi
import com.android.purebilibili.plugin.sdk.DanmakuPluginApi
import com.android.purebilibili.plugin.sdk.PlayerPluginApi
import com.android.purebilibili.plugin.sdk.FeedKind
import com.android.purebilibili.plugin.sdk.FeedPlugin
import com.android.purebilibili.plugin.sdk.FeedTransformPlugin
import com.android.purebilibili.plugin.sdk.RecommendationPluginApi
import com.android.purebilibili.plugin.sdk.Plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private const val TAG = "PluginManager"


/**
 *  插件管理器
 * 
 * 负责管理所有插件的注册、启用/禁用、生命周期调用等。
 * 使用单例模式，在 Application 启动时初始化。
 */
object PluginManager {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val registry = BuiltinPluginRegistry(
        readEnabled = { PluginStore.isEnabled(appContext, it) },
        writeEnabled = { id, enabled -> PluginStore.setEnabled(appContext, id, enabled) },
        onFailure = { id, error -> Logger.e(TAG, "Plugin state failed: $id", error) },
        onEnabledChanged = { plugin, enabled ->
            if (enabled) {
                PluginEffectHintBus.tryEmit(
                    resolvePluginEnabledEffectHint(plugin.id, plugin.name),
                    cooldownMs = PLUGIN_EFFECT_HINT_ENABLED_COOLDOWN_MS
                )
            }
            if (plugin is DanmakuPluginApi) notifyDanmakuPluginsUpdated()
        }
    )

    val pluginsFlow: StateFlow<List<PluginInfo>> = registry.pluginsFlow
    /** Immutable execution-state snapshot; lifecycle effects complete before publication. */
    val plugins: List<PluginInfo> get() = pluginsFlow.value

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
        scope.launch {
            PluginStore.effectMatchHintsEnabledFlow(appContext).collect { enabled ->
                PluginEffectHintBus.setEffectMatchHintsEnabled(enabled)
            }
        }
        Logger.d(TAG, " PluginManager initialized")
    }
    
    /** 获取Application Context供插件使用 */
    fun getContext(): Context = appContext
    
    /**
     * 注册插件
     * 内置插件在 Application 中注册
     */
    fun register(plugin: BuiltinPlugin) {
        scope.launch { registry.register(plugin) }
    }

    /** Wait until a built-in plugin has finished restoring its persisted configuration. */
    suspend fun awaitPluginReady(pluginId: String, awaitContentRules: Boolean = true) {
        registry.awaitPluginReady(pluginId)
        if (awaitContentRules) com.android.purebilibili.core.store.ContentFilterRules.snapshot(appContext)
    }
    
    /**
     * 启用/禁用插件
     */
    suspend fun setEnabled(pluginId: String, enabled: Boolean) =
        registry.setEnabled(pluginId, enabled)

    fun isEnabled(pluginId: String): Boolean =
        plugins.firstOrNull { it.plugin.id == pluginId }?.enabled == true

    fun <T : Plugin> getPlugins(type: KClass<T>): List<T> =
        plugins.mapNotNull { if (type.isInstance(it.plugin)) type.java.cast(it.plugin) else null }

    fun <T : Plugin> getEnabledPlugins(type: KClass<T>): List<T> =
        plugins.mapNotNull {
            if (it.enabled && type.isInstance(it.plugin)) type.java.cast(it.plugin) else null
        }

    fun <T : Plugin> getPlugin(type: KClass<T>): T? =
        plugins.firstOrNull { type.isInstance(it.plugin) }?.let { type.java.cast(it.plugin) }

    fun <T : Plugin> getEnabledPlugin(type: KClass<T>): T? =
        plugins.firstOrNull { it.enabled && type.isInstance(it.plugin) }
            ?.let { type.java.cast(it.plugin) }
    
    /** Enabled definitions; mutable execution is owned by each player's session host. */
    fun getEnabledPlayerPlugins(): List<PlayerPluginApi> = getEnabledPlugins(PlayerPluginApi::class)
    
    fun getEnabledDanmakuPlugins(): List<DanmakuPluginApi> =
        getEnabledPlugins(DanmakuPluginApi::class)
    
    /** Video capabilities bind directly to the host's VideoItem without DTO projections. */
    @Suppress("UNCHECKED_CAST")
    fun getEnabledFeedPlugins(): List<FeedPlugin<VideoItem>> =
        getEnabledPlugins(FeedPlugin::class) as List<FeedPlugin<VideoItem>>

    @Suppress("UNCHECKED_CAST")
    fun getEnabledFeedTransformPlugins(): List<FeedTransformPlugin<VideoItem>> =
        getEnabledPlugins(FeedTransformPlugin::class) as List<FeedTransformPlugin<VideoItem>>

    @Suppress("UNCHECKED_CAST")
    fun getEnabledRecommendationPlugins(): List<RecommendationPluginApi<VideoItem>> =
        getEnabledPlugins(RecommendationPluginApi::class) as List<RecommendationPluginApi<VideoItem>>

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
        feedKind: FeedKind = FeedKind.GENERIC,
        applyContentRules: Boolean = true,
    ): List<com.android.purebilibili.data.model.response.VideoItem> {
        val feedPlugins = getEnabledFeedPlugins()
        val transformPlugins = getEnabledFeedTransformPlugins()
        val rules = com.android.purebilibili.core.store.ContentFilterRules.current.value.takeIf { applyContentRules }

        val filtered = items.filter { item ->
            rules?.keepsVideo(item, feedKind) != false &&
                feedPlugins.all { plugin ->
                try {
                    plugin.shouldShowItem(item, feedKind)
                } catch (e: Exception) {
                    Logger.e(TAG, " Feed plugin failed: ${plugin.name}", e)
                    true
                }
            }
        }
        val removedCount = items.size - filtered.size
        if (removedCount > 0) {
            PluginEffectHintBus.tryEmit(
                resolveFeedFilterEffectHint(
                    removedCount = removedCount,
                    pluginNames = feedPlugins.map { it.name }
                ),
                cooldownMs = PLUGIN_EFFECT_HINT_FEED_COOLDOWN_MS
            )
        }
        return applyFeedTransformPlugins(filtered, transformPlugins, feedKind) { plugin, error ->
            Logger.e(TAG, " Feed transform plugin failed: ${plugin.name}", error)
        }
    }

    /** 页面保留原始批次时，共享规则由其展示投影应用，解除屏蔽无需重新请求。 */
    internal fun filterFeedItemsBeforeContentProjection(
        items: List<com.android.purebilibili.data.model.response.VideoItem>,
        feedKind: FeedKind = FeedKind.GENERIC,
    ) = filterFeedItems(items, feedKind, applyContentRules = false)
    
    /**
     * 获取已启用插件数量
     */
    fun getEnabledCount(): Int = plugins.count { it.enabled }

    fun notifyDanmakuPluginsUpdated() {
        _danmakuPluginUpdateToken.value = System.currentTimeMillis()
    }
}

internal fun applyFeedTransformPlugins(
    items: List<com.android.purebilibili.data.model.response.VideoItem>,
    plugins: List<FeedTransformPlugin<VideoItem>>,
    feedKind: FeedKind,
    onFailure: (FeedTransformPlugin<VideoItem>, Exception) -> Unit = { _, _ -> },
): List<com.android.purebilibili.data.model.response.VideoItem> = plugins.fold(items) { current, plugin ->
    try {
        plugin.transformFeedItems(current, feedKind)
    } catch (error: Exception) {
        onFailure(plugin, error)
        current
    }
}

/**
 * 插件信息包装类
 */
data class PluginInfo(
    val plugin: BuiltinPlugin,
    val enabled: Boolean
)
