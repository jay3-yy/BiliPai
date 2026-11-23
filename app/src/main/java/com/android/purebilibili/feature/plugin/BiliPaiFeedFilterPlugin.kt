// 文件路径: feature/plugin/BiliPaiFeedFilterPlugin.kt
package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.filter.recordFilteredVideo
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.store.ContentFilterReason

import com.android.purebilibili.core.ui.AppContentColors

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.plugin.sdk.FeedKind
import com.android.purebilibili.plugin.sdk.FeedPlugin
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppChoiceOption
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSingleChoicePreference
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 推荐流过滤插件(移植自 BiliPai 的「推荐流设置」RecommendFilter)。
 *
 * 过滤规则: 白名单豁免 → 视频时长 → 播放量/点赞率 → 标题关键词 → 本地屏蔽用户 →
 * 分区关键词(tname)。已关注 UP 主可豁免(推荐流)。
 *
 * 默认关闭, 可在插件中心启用; 各规则阈值与 BiliPai 默认值一致(0 = 不过滤)。
 */
class BiliPaiFeedFilterPlugin : BuiltinPlugin, FeedPlugin<VideoItem> {

    override val id = BuiltinPluginIds.FEED_FILTER
    override val name = "内容过滤"
    override val description = "管理视频、评论、弹幕、动态共享屏蔽词，以及视频时长、播放量、点赞率与过滤范围"
    override val version = "1.0.0"
    override val author = "qyo123oyq"
    override val icon: ImageVector = Icons.Outlined.FilterList

    private var config = BiliPaiFeedFilterConfig()

    // 编译期组装的正则(配置变更时重建)
    private var rcmdRegExp: Regex? = null       // 标题关键词
    private var zoneRegExp: Regex? = null       // 分区关键词(tname)

    override suspend fun onEnable() {
        loadConfig(PluginManager.getContext())
        rebuildRegexes()
        com.android.purebilibili.core.store.ContentFilterRules.setKeywordFilteringEnabled(true)
        Log.d(TAG, "推荐流过滤已启用")
    }

    override suspend fun onDisable() {
        com.android.purebilibili.core.store.ContentFilterRules.setKeywordFilteringEnabled(false)
        Log.d(TAG, "推荐流过滤已禁用")
    }

    // ==================== 过滤规则(还原 BiliPai RecommendFilter) ====================

    override fun shouldShowItem(item: VideoItem, feedKind: FeedKind): Boolean {
        val reason = feedItemFilterReason(config, item, feedKind, rcmdRegExp, zoneRegExp) ?: return true
        recordFilteredVideo(item, reason)
        return false
    }

    // ==================== 配置持久化 ====================

    private suspend fun loadConfig(context: android.content.Context) {
        PluginStore.getConfigJson(context, id)?.let {
            config = Json.decodeFromString(BiliPaiFeedFilterConfig.serializer(), it)
        }
        com.android.purebilibili.core.store.ContentFilterRules.migrateVideoPattern(context, config.banWordForRecommend)
        val users = com.android.purebilibili.data.repository.BlockedUpRepository.getInstance(context)
        val existing = users.getAllBlockedUps().first().mapTo(hashSetOf()) { it.mid }
        config.recommendBlockedMids.forEach { (mid, name) ->
            if (mid > 0L && mid !in existing) users.blockUp(mid, name, "")
        }
        if (config.banWordForRecommend.isNotEmpty() || config.recommendBlockedMids.isNotEmpty()) {
            config = config.copy(banWordForRecommend = "", recommendBlockedMids = emptyMap())
            PluginStore.setConfigJson(context, id, Json.encodeToString(BiliPaiFeedFilterConfig.serializer(), config))
        }
    }

    private fun rebuildRegexes() {
        com.android.purebilibili.core.store.ContentFilterRules.setVideoPolicy(
            com.android.purebilibili.core.store.ContentVideoKeywordPolicy(
                whitelist = config.whitelistMids.keys.toSet(), exemptFollowed = config.exemptFilterForFollowed,
                applyToPopular = config.applyToHotVideos, applyToRank = config.applyToRankVideos,
                applyToSearch = config.applyToSearch,
            )
        )
        rcmdRegExp = parseBanWordToRegex(config.banWordForRecommend)?.let { Regex(it, RegexOption.IGNORE_CASE) }
        zoneRegExp = parseBanWordToRegex(config.banWordForZone)?.let { Regex(it, RegexOption.IGNORE_CASE) }
    }

    private fun persistConfig(context: android.content.Context, next: BiliPaiFeedFilterConfig) {
        config = next
        rebuildRegexes()
        com.android.purebilibili.core.coroutines.AppScope.ioScope.launch {
            PluginStore.setConfigJson(context, id, Json.encodeToString(BiliPaiFeedFilterConfig.serializer(), next))
        }
    }

    // ==================== 设置 UI ====================

    @Composable
    internal fun FeedFilterSettingsContent(
        onOpenHistory: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
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

        fun persist(next: BiliPaiFeedFilterConfig) {
            cfg = next
            config = next
            rebuildRegexes()
            scope.launch {
                PluginStore.setConfigJson(context, id, Json.encodeToString(BiliPaiFeedFilterConfig.serializer(), next))
            }
        }

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterSection("跨页面屏蔽") {
                SharedContentFilterPreferences()
                AiReplyFilterPreference()
                RepeatedReplyFilterPreference()
                ContentFilterHistoryPreference(onClick = onOpenHistory)
                AppText("手动拉黑的用户在首页、搜索、相关推荐、动态与评论中共用，不受本插件开关和视频白名单影响。")
            }
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
                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.FILTER_EXEMPT_FOLLOWED),
                    title = "已关注 UP 主豁免",
                    subtitle = "推荐流中已关注的 UP 主不受过滤影响",
                    checked = cfg.exemptFilterForFollowed,
                    onCheckedChange = { persist(cfg.copy(exemptFilterForFollowed = it)) }
                )
            }

            FilterSection("关键词过滤") {
                KeywordField(
                    title = "分区关键词",
                    subtitle = "按视频分区名(tname)过滤, 每行一个, 支持正则",
                    value = cfg.banWordForZone,
                    onValue = { persist(cfg.copy(banWordForZone = it)) }
                )
            }

            FilterSection("屏蔽用户 / 白名单") {
                SharedBlockedUsersPreference()
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
                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.FILTER_HOT_VIDEOS),
                    title = "过滤器也应用于热门视频",
                    checked = cfg.applyToHotVideos,
                    onCheckedChange = { persist(cfg.copy(applyToHotVideos = it)) }
                )
                AppSwitchPreference(
                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.FILTER_RANKING),
                    title = "过滤器也应用于排行榜",
                    checked = cfg.applyToRankVideos,
                    onCheckedChange = { persist(cfg.copy(applyToRankVideos = it)) }
                )
                AppSwitchPreference(
                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.SEARCH_DISCOVER_SECTION),
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
                color = AppContentColors.primaryText()
            )
            Surface(
                modifier = Modifier.fillMaxWidth().clip(AppShapes.container(ContainerLevel.Card)),
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
        modifier: Modifier = Modifier,
        onSelect: (T) -> Unit,
    ) {
        val isCustom = presets.none { it.first == selected }
        var showDialog by remember { mutableStateOf(false) }
        var draft by remember { mutableStateOf("") }
        val options = presets.map { (value, label) ->
            AppChoiceOption(value = value, label = label)
        } + AppChoiceOption(
            value = customMarker,
            label = if (isCustom) "自定义（当前值：$selected）" else "自定义",
        )

        AppSingleChoicePreference(
            title = title,
            subtitle = subtitle,
            options = options,
            selectedValue = if (isCustom) customMarker else selected,
            modifier = modifier,
            onValueChange = { value ->
                if (value == customMarker) {
                    draft = "$selected"
                    showDialog = true
                } else {
                    onSelect(value)
                }
            },
        )

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
                color = AppContentColors.primaryText()
            )
            if (subtitle.isNotEmpty()) {
                AppText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
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
        const val TAG = "BiliPaiFeedFilterPlugin"

        /**
         * 移植 BiliPai Pref.parseBanWordToRegex:
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

/** 插件配置(默认值对齐 BiliPai) */
@Serializable
data class BiliPaiFeedFilterConfig(
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
 * 还原 BiliPai RecommendFilter: 白名单 → 时长 → 播放量/点赞率 → 标题关键词 → 屏蔽用户 → 分区关键词。
 * 返回 true 表示显示, false 表示隐藏。
 */
internal fun shouldShowFeedItem(
    config: BiliPaiFeedFilterConfig,
    item: VideoItem,
    feedKind: FeedKind,
    rcmdRegExp: Regex?,
    zoneRegExp: Regex?
): Boolean = feedItemFilterReason(config, item, feedKind, rcmdRegExp, zoneRegExp) == null

private fun feedItemFilterReason(
    config: BiliPaiFeedFilterConfig,
    item: VideoItem,
    feedKind: FeedKind,
    rcmdRegExp: Regex?,
    zoneRegExp: Regex?,
): ContentFilterReason? {
    // 按来源开关: BiliPai 的「过滤器也应用于热门/排行/搜索」
    val allowedByKind = when (feedKind) {
        FeedKind.HOME_POPULAR -> config.applyToHotVideos
        FeedKind.HOME_RANK -> config.applyToRankVideos
        FeedKind.SEARCH -> config.applyToSearch
        else -> true
    }
    if (!allowedByKind) return null

    val mid = item.owner.mid
    // 白名单: 最高优先级豁免
    if (mid > 0L && config.whitelistMids.containsKey(mid)) return null

    // 已关注豁免(仅推荐流, 与 BiliPai filter() 一致; 其余来源用 filterAll 无豁免)
    if (feedKind == FeedKind.HOME_RECOMMEND && item.isFollowed && config.exemptFilterForFollowed) {
        return null
    }

    // 时长过滤: duration > 0 && < minDuration
    val hasVideoStatistics = item.contentType !in setOf("bangumi", "picture")
    if (hasVideoStatistics && item.duration > 0 && item.duration < config.minDurationForRcmd) {
        return ContentFilterReason.DURATION
    }

    // 播放量 / 点赞率(整数乘法, 避免浮点)
    val view = item.stat.view.toLong()
    val like = item.stat.like.toLong()
    if (hasVideoStatistics && view >= 0) {
        if (view < config.minPlayForRcmd) return ContentFilterReason.PLAY_COUNT
        if (like > -1 && like * 100L < config.minLikeRatioForRecommend * view) return ContentFilterReason.LIKE_RATIO
    }

    // 标题关键词
    if (rcmdRegExp != null && rcmdRegExp.containsMatchIn(item.title)) return ContentFilterReason.TITLE_PATTERN

    // 本地屏蔽用户
    if (mid > 0L && config.recommendBlockedMids.isNotEmpty() && config.recommendBlockedMids.containsKey(mid)) {
        return ContentFilterReason.BLOCKED_USER
    }

    // 分区关键词(tname)
    if (item.tname.isNotEmpty() && zoneRegExp != null && zoneRegExp.containsMatchIn(item.tname)) {
        return ContentFilterReason.CATEGORY
    }

    return null
}
