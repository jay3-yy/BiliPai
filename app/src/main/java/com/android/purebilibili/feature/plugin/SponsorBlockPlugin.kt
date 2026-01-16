// 文件路径: feature/plugin/SponsorBlockPlugin.kt
package com.android.purebilibili.feature.plugin

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import com.android.purebilibili.core.plugin.PlayerPlugin
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.plugin.SkipAction
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.repository.SponsorBlockRepository
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private const val TAG = "SponsorBlockPlugin"

/**
 *  空降助手插件
 * 
 * 基于 SponsorBlock 数据库自动跳过视频中的广告、赞助、片头片尾等片段。
 */
class SponsorBlockPlugin : PlayerPlugin {
    
    override val id = "sponsor_block"
    override val name = "空降助手"
    override val description = "自动跳过视频中的广告、赞助、片头片尾等片段"
    override val version = "1.0.0"
    override val author = "YangY"
    override val icon: ImageVector = CupertinoIcons.Default.Paperplane
    
    // 当前视频的跳过片段
    private var segments: List<SponsorSegment> = emptyList()
    
    // 已跳过的片段 UUID（防止重复跳过）
    private val skippedIds = mutableSetOf<String>()
    
    // 配置
    private var config: SponsorBlockConfig = SponsorBlockConfig()
    
    override suspend fun onEnable() {
        Logger.d(TAG, " 空降助手已启用")
    }
    
    override suspend fun onDisable() {
        segments = emptyList()
        skippedIds.clear()
        Logger.d(TAG, "🔴 空降助手已禁用")
    }
    
    override suspend fun onVideoLoad(bvid: String, cid: Long) {
        // 重置状态
        segments = emptyList()
        skippedIds.clear()
        
        //  [修复] 加载配置
        loadConfigSuspend()
        
        // 加载片段数据
        try {
            segments = SponsorBlockRepository.getSegments(bvid)
            Logger.d(TAG, " 加载了 ${segments.size} 个片段 for $bvid, autoSkip=${config.autoSkip}")
        } catch (e: Exception) {
            Logger.w(TAG, " 加载片段失败: ${e.message}")
        }
    }
    
    // 记录上次播放位置，用于检测回拉
    private var lastPositionMs: Long = 0
    
    override suspend fun onPositionUpdate(positionMs: Long): SkipAction? {
        if (segments.isEmpty()) return SkipAction.None
        
        //  [修复] 检测用户回拉进度条，如果回拉到片段之前则清除该片段的已跳过记录
        if (positionMs < lastPositionMs - 2000) {  // 回拉超过2秒
            // 检查是否回拉到了某些已跳过片段之前
            val segmentsToReset = segments.filter { seg ->
                seg.UUID in skippedIds && positionMs < seg.startTimeMs - 1000
            }
            segmentsToReset.forEach { seg ->
                skippedIds.remove(seg.UUID)
                Logger.d(TAG, " 回拉检测: 重置片段 ${seg.categoryName} 的跳过状态")
            }
        }
        lastPositionMs = positionMs
        
        //  调试日志（每5秒一次）
        val firstSeg = segments.firstOrNull()
        if (firstSeg != null && positionMs % 5000 < 600) {
            Logger.d(TAG, "📍 当前位置: ${positionMs}ms, 片段范围: ${firstSeg.startTimeMs}ms - ${firstSeg.endTimeMs}ms, autoSkip=${config.autoSkip}")
        }
        
        // 查找当前位置是否在某个片段内
        val segment = segments.find { seg ->
            positionMs in seg.startTimeMs..seg.endTimeMs && seg.UUID !in skippedIds
        } ?: return SkipAction.None
        
        Logger.d(TAG, "🎯 命中片段: ${segment.categoryName}, 位置${positionMs}ms在[${segment.startTimeMs}-${segment.endTimeMs}]ms范围内")
        
        // 如果配置为自动跳过
        if (config.autoSkip) {
            skippedIds.add(segment.UUID)
            Logger.d(TAG, " 自动跳过: ${segment.categoryName}")
            //  记录空降助手跳过事件
            com.android.purebilibili.core.util.AnalyticsHelper.logSponsorBlockSkip(
                videoId = skippedIds.firstOrNull() ?: "",
                segmentType = segment.categoryName
            )
            return SkipAction.SkipTo(
                positionMs = segment.endTimeMs,
                reason = "已跳过: ${segment.categoryName}"
            )
        }
        
        //  [修复] 非自动跳过模式：返回 ShowButton 让 UI 显示跳过按钮
        Logger.d(TAG, "🔘 显示跳过按钮: ${segment.categoryName}")
        return SkipAction.ShowButton(
            skipToMs = segment.endTimeMs,
            label = "跳过${segment.categoryName}",
            segmentId = segment.UUID
        )
    }
    
    /** 手动跳过时调用，标记片段已跳过 */
    fun markAsSkipped(segmentId: String) {
        skippedIds.add(segmentId)
        Logger.d(TAG, " 手动跳过完成: $segmentId")
    }
    
    override fun onVideoEnd() {
        segments = emptyList()
        skippedIds.clear()
        lastPositionMs = 0
    }

    /**  suspend版本的配置加载 */
    private suspend fun loadConfigSuspend() {
        try {
            val context = PluginManager.getContext()
            val jsonStr = PluginStore.getConfigJson(context, id)
            if (jsonStr != null) {
                config = Json.decodeFromString<SponsorBlockConfig>(jsonStr)
            } else {
                //  没有保存的配置时，使用默认值（autoSkip=true）
                config = SponsorBlockConfig(autoSkip = true)
            }
            Logger.d(TAG, "📖 配置已加载: autoSkip=${config.autoSkip}")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load config", e)
            // 出错时也使用默认值
            config = SponsorBlockConfig(autoSkip = true)
        }
    }
    
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val scope = rememberCoroutineScope()
        var autoSkip by remember { mutableStateOf(config.autoSkip) }
        
        // 加载配置
        LaunchedEffect(Unit) {
            loadConfigSuspend()
            autoSkip = config.autoSkip
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 使用原设置组件 - 自动跳过
            IOSSwitchItem(
                icon = CupertinoIcons.Default.Bolt,
                title = "自动跳过",
                subtitle = "关闭后将显示手动跳过按钮而非自动跳过",
                checked = autoSkip,
                onCheckedChange = { newValue ->
                    autoSkip = newValue
                    config = config.copy(autoSkip = newValue)
                    scope.launch {
                        PluginStore.setConfigJson(context, id, Json.encodeToString(config))
                    }
                },
                iconTint = androidx.compose.ui.graphics.Color(0xFFFF9800) // iOS Orange
            )
            
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            // 使用原设置组件 - 关于空降助手
            IOSClickableItem(
                icon = CupertinoIcons.Default.InfoCircle,
                title = "关于空降助手",
                value = "BilibiliSponsorBlock",
                onClick = { uriHandler.openUri("https://github.com/hanydd/BilibiliSponsorBlock") },
                iconTint = androidx.compose.ui.graphics.Color(0xFF2196F3) // iOS Blue
            )
        }
    }
}

/**
 * 空降助手配置
 */
@Serializable
data class SponsorBlockConfig(
    val autoSkip: Boolean = true,
    val skipSponsor: Boolean = true,
    val skipIntro: Boolean = true,
    val skipOutro: Boolean = true,
    val skipInteraction: Boolean = true
)
