// 文件路径: feature/plugin/DanmakuEnhancePlugin.kt
package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.plugin.BuiltinPluginIds

import com.android.purebilibili.core.ui.AppContentColors

import android.content.Context
import androidx.compose.foundation.layout.*
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.plugin.sdk.DanmakuItem
import com.android.purebilibili.plugin.sdk.DanmakuPluginApi
import com.android.purebilibili.plugin.sdk.DanmakuStyle
import com.android.purebilibili.core.plugin.PLUGIN_EFFECT_HINT_DANMAKU_COOLDOWN_MS
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.plugin.sdk.PluginCapabilityManifest
import com.android.purebilibili.core.plugin.PluginEffectHintBus
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.plugin.resolveDanmakuFilterEffectHint
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private const val TAG = "DanmakuEnhancePlugin"

/**
 *  弹幕增强插件
 *
 * 提供弹幕过滤和高亮功能：
 * - 关键词屏蔽
 * - 同传弹幕高亮
 */
class DanmakuEnhancePlugin : BuiltinPlugin, DanmakuPluginApi {

    override val id = BuiltinPluginIds.DANMAKU_ENHANCE
    override val name = "弹幕增强"
    override val description = "关键词屏蔽、按用户ID屏蔽、同传弹幕高亮"
    override val version = "1.1.1"
    override val author = "BiliPai项目组"
    override val icon: ImageVector = Icons.Outlined.ChatBubble
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = "com.android.purebilibili.feature.plugin.DanmakuEnhancePlugin",
        capabilities = setOf(
            PluginCapability.DANMAKU_STREAM,
            PluginCapability.DANMAKU_MUTATION,
            PluginCapability.PLUGIN_STORAGE
        )
    )

    private var config: DanmakuEnhanceConfig = DanmakuEnhanceConfig()
    private var filteredCount = 0
    private var blockedUsersCache: List<String> = splitKeywords(config.blockedUserIds)
    private var highlightKeywordsCache: List<String> = splitKeywords(config.highlightKeywords)

    private suspend fun loadConfig(context: Context) {
        PluginStore.getConfigJson(context, id)?.let { config = Json.decodeFromString<DanmakuEnhanceConfig>(it) }
        com.android.purebilibili.core.store.ContentFilterRules.migrateDanmakuKeywords(context, splitKeywords(config.blockedKeywords))
        if (config.blockedKeywords.isNotEmpty()) {
            config = config.copy(blockedKeywords = "")
            PluginStore.setConfigJson(context, id, Json.encodeToString(config))
        }
        refreshKeywordCache()
    }

    private fun splitKeywords(value: String): List<String> {
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun refreshKeywordCache() {
        blockedUsersCache = splitKeywords(config.blockedUserIds)
        highlightKeywordsCache = splitKeywords(config.highlightKeywords)
    }

    private fun emitFilterHint() {
        PluginEffectHintBus.tryEmit(
            resolveDanmakuFilterEffectHint(id, name),
            cooldownMs = PLUGIN_EFFECT_HINT_DANMAKU_COOLDOWN_MS
        )
    }

    private suspend fun persistConfig(context: Context, newConfig: DanmakuEnhanceConfig) {
        config = newConfig
        refreshKeywordCache()
        PluginStore.setConfigJson(context, id, Json.encodeToString(config))
        com.android.purebilibili.core.store.ContentFilterRules.setDanmakuFilteringEnabled(
            PluginManager.isEnabled(id) && config.enableFilter
        )
        PluginManager.notifyDanmakuPluginsUpdated()
    }

    private fun isUserBlocked(userId: String): Boolean {
        if (userId.isBlank() || blockedUsersCache.isEmpty()) return false
        val normalized = userId.trim().lowercase()
        return blockedUsersCache.any { blocked ->
            val target = blocked.trim().lowercase()
            target.isNotBlank() && (
                normalized == target ||
                    normalized.startsWith(target) ||
                    normalized.contains(target)
                )
        }
    }

    override suspend fun onEnable() {
        filteredCount = 0
        loadConfig(PluginManager.getContext())
        com.android.purebilibili.core.store.ContentFilterRules.setDanmakuFilteringEnabled(config.enableFilter)
        Logger.d(TAG, " 弹幕增强已启用")
    }

    override suspend fun onDisable() {
        com.android.purebilibili.core.store.ContentFilterRules.setDanmakuFilteringEnabled(false)
        Logger.d(TAG, "🔴 弹幕增强已禁用，本次过滤了 $filteredCount 条弹幕")
        filteredCount = 0
    }

    override fun filterDanmaku(danmaku: DanmakuItem): DanmakuItem? {
        if (!config.enableFilter) return danmaku

        if (isUserBlocked(danmaku.userId)) {
            filteredCount++
            emitFilterHint()
            return null
        }

        return danmaku
    }

    override fun styleDanmaku(danmaku: DanmakuItem): DanmakuStyle? {
        if (!config.enableHighlight) return null

        if (highlightKeywordsCache.any { danmaku.content.contains(it, ignoreCase = true) }) {
            return DanmakuStyle(
                textColor = 0xFFFFD700.toInt(),
                backgroundColor = 0x80000000.toInt(),
                bold = true,
                scale = 1.05f
            )
        }

        return null
    }

    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var enableFilter by remember { mutableStateOf(config.enableFilter) }
        var enableHighlight by remember { mutableStateOf(config.enableHighlight) }
        var blockedUserIds by remember { mutableStateOf(config.blockedUserIds) }
        var highlightKeywords by remember { mutableStateOf(config.highlightKeywords) }

        // 加载配置
        LaunchedEffect(Unit) {
            loadConfig(context)
            enableFilter = config.enableFilter
            enableHighlight = config.enableHighlight
            blockedUserIds = config.blockedUserIds
            highlightKeywords = config.highlightKeywords
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 启用屏蔽
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText("启用关键词屏蔽", style = MaterialTheme.typography.bodyLarge)
                }
                AppSwitch(
                    checked = enableFilter,
                    onCheckedChange = { newValue ->
                        enableFilter = newValue
                        scope.launch {
                            persistConfig(context, config.copy(enableFilter = newValue))
                        }
                    },
                )
            }

            // 屏蔽关键词输入
            if (enableFilter) {
                SharedContentFilterPreferences()
                Spacer(modifier = Modifier.height(8.dp))

                AppOutlinedTextField(
                    value = blockedUserIds,
                    onValueChange = { newValue ->
                        blockedUserIds = newValue
                        scope.launch {
                            persistConfig(context, config.copy(blockedUserIds = newValue))
                        }
                    },
                    label = { AppText("屏蔽用户 ID/哈希") },
                    placeholder = { AppText("用逗号分隔，如：abc123,7f9d...,123456") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 启用高亮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText("启用同传高亮", style = MaterialTheme.typography.bodyLarge)
                    AppText(
                        "高亮显示同传/翻译弹幕",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                }
                AppSwitch(
                    checked = enableHighlight,
                    onCheckedChange = { newValue ->
                        enableHighlight = newValue
                        scope.launch {
                            persistConfig(context, config.copy(enableHighlight = newValue))
                        }
                    },
                )
            }

            // 高亮关键词输入
            if (enableHighlight) {
                AppOutlinedTextField(
                    value = highlightKeywords,
                    onValueChange = { newValue ->
                        highlightKeywords = newValue
                        scope.launch {
                            persistConfig(context, config.copy(highlightKeywords = newValue))
                        }
                    },
                    label = { AppText("高亮关键词") },
                    placeholder = { AppText("用逗号分隔，如：【,】,同传") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            }
        }
    }
}

/**
 * 弹幕增强配置
 */
@Serializable
data class DanmakuEnhanceConfig(
    val enableFilter: Boolean = true,
    val enableHighlight: Boolean = true,
    val blockedKeywords: String = "剧透,前方高能",
    val blockedUserIds: String = "",
    val highlightKeywords: String = "【,】,同传,翻译"
)
