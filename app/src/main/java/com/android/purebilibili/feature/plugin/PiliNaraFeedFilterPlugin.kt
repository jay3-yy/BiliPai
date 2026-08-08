// 文件路径: feature/plugin/PiliNaraFeedFilterPlugin.kt
package com.android.purebilibili.feature.plugin

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.FeedKind
import com.android.purebilibili.core.plugin.FeedPlugin
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppNativeSegmentedControl
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 推荐流过滤插件(移植自 PiliNara 的「推荐流设置」RecommendFilter)。
 *
 * 过滤规则: 白名单豁免 → 视频时长 → 播放量/点赞率 → 标题关键词 → 本地屏蔽用户 →
 * 分区关键词(tname)。已关注 UP 主可豁免(推荐流)。
 *
 * 默认关闭, 可在插件中心启用; 各规则阈值与 PiliNara 默认值一致(0 = 不过滤)。
 */
class PiliNaraFeedFilterPlugin : FeedPlugin {

    override val id = "pilinara_feed_filter"
    override val name = "推荐流过滤"
    override val description = "移植自 PiliNara 的推荐流过滤: 时长/播放量/点赞率/标题关键词/屏蔽用户/白名单"
    override val version = "1.0.0"
    override val author = "qyo123oyq"

    private var config = PiliNaraFeedFilterConfig()

    // 编译期组装的正则(配置变更时重建)
    private var rcmdRegExp: Regex? = null       // 标题关键词
    private var zoneRegExp: Regex? = null       // 分区关键词(tname)

    override suspend fun onEnable() {
        loadConfig(PluginManager.getContext())
        rebuildRegexes()
        Log.d(TAG, "推荐流过滤已启用")
    }

    override suspend fun onDisable() {
        Log.d(TAG, "推荐流过滤已禁用")
    }

    // ==================== 过滤规则(还原 PiliNara RecommendFilter) ====================

    override fun shouldShowItem(item: VideoItem): Boolean = shouldShowItem(item, FeedKind.HOME_RECOMMEND)

    override fun shouldShowItem(item: VideoItem, feedKind: FeedKind): Boolean {
        return shouldShowFeedItem(
            config = config,
            item = item,
            feedKind = feedKind,
            rcmdRegExp = rcmdRegExp,
            zoneRegExp = zoneRegExp
        )
    }

    // ==================== 配置持久化 ====================

    private suspend fun loadConfig(context: android.content.Context) {
        try {
            val json = PluginStore.getConfigJson(context, id)
            if (json != null) {
                config = Json.decodeFromString(PiliNaraFeedFilterConfig.serializer(), json)
            }
        } catch (e: Exception) {
            Log.w(TAG, "加载配置失败, 使用默认值: ${e.message}")
        }
    }

    private fun rebuildRegexes() {
        rcmdRegExp = parseBanWordToRegex(config.banWordForRecommend)?.let { Regex(it, RegexOption.IGNORE_CASE) }
        zoneRegExp = parseBanWordToRegex(config.banWordForZone)?.let { Regex(it, RegexOption.IGNORE_CASE) }
    }

    private fun persistConfig(context: android.content.Context, next: PiliNaraFeedFilterConfig) {
        config = next
        rebuildRegexes()
        com.android.purebilibili.core.coroutines.AppScope.ioScope.launch {
            PluginStore.setConfigJson(context, id, Json.encodeToString(PiliNaraFeedFilterConfig.serializer(), next))
        }
    }

    // ==================== 设置 UI ====================

    @Composable
    override fun SettingsContent() {
        FeedFilterSettingsContent(Modifier.padding(16.dp))
    }

    @Composable
    override fun SettingsContent(modifier: Modifier) {
        FeedFilterSettingsContent(modifier)
    }

    @Composable
    private fun FeedFilterSettingsContent(modifier: Modifier = Modifier) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        var loaded by remember { mutableStateOf(false) }
        var cfg by remember { mutableStateOf(config) }

        LaunchedEffect(Unit) {
            loadConfig(context)
            cfg = config
            loaded = true
        }
        if (!loaded) return

        fun persist(next: PiliNaraFeedFilterConfig) {
            cfg = next
            config = next
            rebuildRegexes()
            scope.launch {
                PluginStore.setConfigJson(context, id, Json.encodeToString(PiliNaraFeedFilterConfig.serializer(), next))
            }
        }

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterSection("过滤规则") {
                NumericCustomSelect(
                    title = "视频时长过滤",
                    subtitle = "隐藏时长小于该值的视频(秒)",
                    presets = listOf(
                        0L to "不过滤",
                        30L to "30 秒",
                        60L to "60 秒",
                        90L to "90 秒",
                        120L to "120 秒",
                    ),
                    selected = cfg.minDurationForRcmd,
                    customMarker = -1L,
                    parse = { it.toLongOrNull()?.coerceAtLeast(0L) },
                    onSelect = { persist(cfg.copy(minDurationForRcmd = it)) }
                )
                NumericCustomSelect(
                    title = "播放量过滤",
                    subtitle = "隐藏播放量低于该值的视频",
                    presets = listOf(
                        0L to "不过滤",
                        50L to "50",
                        100L to "100",
                        500L to "500",
                        1000L to "1000",
                    ),
                    selected = cfg.minPlayForRcmd,
                    customMarker = -1L,
                    parse = { it.toLongOrNull()?.coerceAtLeast(0L) },
                    onSelect = { persist(cfg.copy(minPlayForRcmd = it)) }
                )
                NumericCustomSelect(
                    title = "点赞率过滤",
                    subtitle = "隐藏点赞率低于该值的视频(%)",
                    presets = listOf(
                        0 to "不过滤",
                        1 to "1%",
                        2 to "2%",
                        3 to "3%",
                        4 to "4%",
                    ),
                    selected = cfg.minLikeRatioForRecommend,
                    customMarker = -1,
                    parse = { it.toIntOrNull()?.coerceAtLeast(0) },
                    onSelect = { persist(cfg.copy(minLikeRatioForRecommend = it)) }
                )
                AppSwitchPreference(
                    title = "已关注 UP 主豁免",
                    subtitle = "推荐流中已关注的 UP 主不受过滤影响",
                    checked = cfg.exemptFilterForFollowed,
                    onCheckedChange = { persist(cfg.copy(exemptFilterForFollowed = it)) }
                )
            }

            FilterSection("关键词过滤") {
                KeywordField(
                    title = "标题关键词",
                    subtitle = "每行一个关键词, 支持正则",
                    value = cfg.banWordForRecommend,
                    onValue = { persist(cfg.copy(banWordForRecommend = it)) }
                )
                KeywordField(
                    title = "分区关键词",
                    subtitle = "按视频分区名(tname)过滤, 每行一个, 支持正则",
                    value = cfg.banWordForZone,
                    onValue = { persist(cfg.copy(banWordForZone = it)) }
                )
            }

            FilterSection("屏蔽用户 / 白名单") {
                KeywordField(
                    title = "屏蔽用户",
                    subtitle = "每行一个 UID, 推荐流中隐藏其视频",
                    value = config.recommendBlockedMids.toEditorText(),
                    onValue = {
                        persist(cfg.copy(recommendBlockedMids = parseUidMap(it)))
                    }
                )
                KeywordField(
                    title = "白名单用户",
                    subtitle = "每行一个 UID, 白名单用户完全豁免过滤",
                    value = config.whitelistMids.toEditorText(),
                    onValue = {
                        persist(cfg.copy(whitelistMids = parseUidMap(it)))
                    }
                )
            }

            FilterSection("应用于其他信息流") {
                AppSwitchPreference(
                    title = "过滤器也应用于热门视频",
                    checked = cfg.applyToHotVideos,
                    onCheckedChange = { persist(cfg.copy(applyToHotVideos = it)) }
                )
                AppSwitchPreference(
                    title = "过滤器也应用于排行榜",
                    checked = cfg.applyToRankVideos,
                    onCheckedChange = { persist(cfg.copy(applyToRankVideos = it)) }
                )
                AppSwitchPreference(
                    title = "过滤器也应用于搜索结果",
                    checked = cfg.applyToSearch,
                    onCheckedChange = { persist(cfg.copy(applyToSearch = it)) }
                )
            }
        }
    }

    @Composable
    private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            ) {
                Column(content = content)
            }
        }
    }

    @Composable
    private fun <T : Number> NumericCustomSelect(
        title: String,
        subtitle: String,
        presets: List<Pair<T, String>>,
        selected: T,
        customMarker: T,
        parse: (String) -> T?,
        onSelect: (T) -> Unit,
    ) {
        val isCustom = presets.none { it.first == selected }
        var showDialog by remember { mutableStateOf(false) }
        var draft by remember { mutableStateOf("") }
        val options = presets.map { AppSegmentOption(it.first, it.second) } +
            AppSegmentOption(customMarker, "自定义")

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            AppText(
                text = "$title($subtitle)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AppNativeSegmentedControl(
                options = options,
                selectedValue = if (isCustom) customMarker else selected,
                modifier = Modifier.padding(top = 6.dp),
                onSelectionChange = { value ->
                    if (value == customMarker) {
                        draft = "$selected"
                        showDialog = true
                    } else {
                        onSelect(value)
                    }
                }
            )
            if (isCustom) {
                AppText(
                    text = "当前值: $selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (showDialog) {
            AppAlertDialog(
                onDismissRequest = { showDialog = false },
                title = { AppText("自定义$title") },
                text = {
                    AppOutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                },
                confirmButton = {
                    AppDialogAction(onClick = {
                        parse(draft.trim())?.let { onSelect(it) }
                        showDialog = false
                    }) { AppText("确定") }
                },
                dismissButton = {
                    AppDialogAction(onClick = { showDialog = false }) { AppText("取消") }
                }
            )
        }
    }

    @Composable
    private fun KeywordField(
        title: String,
        subtitle: String,
        value: String,
        onValue: (String) -> Unit,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                AppText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppOutlinedTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                minLines = 2,
                maxLines = 6,
            )
        }
    }

    internal companion object {
        const val TAG = "PiliNaraFeedFilterPlugin"

        /**
         * 移植 PiliNara Pref.parseBanWordToRegex:
         * 换行分隔、trim、去空; 含 `|` 且不以 `(` 开头的项包成 `(item)`; 最后用 `|` 连接。
         */
        fun parseBanWordToRegex(stored: String): String? {
            val items = stored.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            if (items.isEmpty()) return null
            val normalized = items.map { item ->
                if (item.contains('|') && !item.startsWith("(")) "($item)" else item
            }
            return normalized.joinToString("|")
        }

        /** 纯数字行 → mid; 其余行按 "名称 (UID)" 或 "UID" 解析 */
        fun parseUidMap(text: String): Map<Long, String> {
            val result = LinkedHashMap<Long, String>()
            for (line in text.split('\n')) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val mid = extractMid(trimmed)
                if (mid != null) result[mid] = trimmed
            }
            return result
        }

        private fun extractMid(line: String): Long? {
            // 支持 "名称 (123456)" 与纯数字 "123456"
            Regex("(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
            return line.toLongOrNull()
        }
    }
}

/** 插件配置(默认值对齐 PiliNara) */
@Serializable
data class PiliNaraFeedFilterConfig(
    val minDurationForRcmd: Long = 0,                  // 最小时长(秒), 0 不过滤
    val minPlayForRcmd: Long = 0,                      // 最小播放量, 0 不过滤
    val minLikeRatioForRecommend: Int = 0,             // 最小点赞率(%), 0 不过滤
    val banWordForRecommend: String = "",              // 标题关键词(换行分隔)
    val banWordForZone: String = "",                   // 分区关键词(tname, 换行分隔)
    val recommendBlockedMids: Map<Long, String> = emptyMap(), // 本地推荐屏蔽用户
    val whitelistMids: Map<Long, String> = emptyMap(), // 白名单用户
    val exemptFilterForFollowed: Boolean = true,       // 已关注豁免
    val applyToHotVideos: Boolean = false,             // 应用于热门
    val applyToRankVideos: Boolean = false,            // 应用于排行榜
    val applyToSearch: Boolean = false                 // 应用于搜索
)

private fun Map<Long, String>.toEditorText(): String = entries.joinToString("\n") { (mid, name) ->
    if (name.isBlank() || name == "UID:$mid") mid.toString() else "$name ($mid)"
}

/**
 * 推荐流过滤规则(纯函数, 便于单元测试)。
 * 还原 PiliNara RecommendFilter: 白名单 → 时长 → 播放量/点赞率 → 标题关键词 → 屏蔽用户 → 分区关键词。
 * 返回 true 表示显示, false 表示隐藏。
 */
internal fun shouldShowFeedItem(
    config: PiliNaraFeedFilterConfig,
    item: VideoItem,
    feedKind: FeedKind,
    rcmdRegExp: Regex?,
    zoneRegExp: Regex?
): Boolean {
    // 按来源开关: PiliNara 的「过滤器也应用于热门/排行/搜索」
    val allowedByKind = when (feedKind) {
        FeedKind.HOME_POPULAR -> config.applyToHotVideos
        FeedKind.HOME_RANK -> config.applyToRankVideos
        FeedKind.SEARCH -> config.applyToSearch
        else -> true
    }
    if (!allowedByKind) return true

    val mid = item.owner.mid
    // 白名单: 最高优先级豁免
    if (mid > 0L && config.whitelistMids.containsKey(mid)) return true

    // 已关注豁免(仅推荐流, 与 PiliNara filter() 一致; 其余来源用 filterAll 无豁免)
    if (feedKind == FeedKind.HOME_RECOMMEND && item.isFollowed && config.exemptFilterForFollowed) {
        return true
    }

    // 时长过滤: duration > 0 && < minDuration
    if (item.duration > 0 && item.duration < config.minDurationForRcmd) return false

    // 播放量 / 点赞率(整数乘法, 避免浮点)
    val view = item.stat.view.toLong()
    val like = item.stat.like.toLong()
    if (view >= 0 && (view < config.minPlayForRcmd ||
            (like > -1 && like * 100L < config.minLikeRatioForRecommend * view))) {
        return false
    }

    // 标题关键词
    if (rcmdRegExp != null && rcmdRegExp.containsMatchIn(item.title)) return false

    // 本地屏蔽用户
    if (mid > 0L && config.recommendBlockedMids.isNotEmpty() && config.recommendBlockedMids.containsKey(mid)) {
        return false
    }

    // 分区关键词(tname)
    if (item.tname.isNotEmpty() && zoneRegExp != null && zoneRegExp.containsMatchIn(item.tname)) {
        return false
    }

    return true
}