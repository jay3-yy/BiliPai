// 文件路径: feature/plugin/SponsorBlockPlugin.kt
package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.plugin.BuiltinPluginIds

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import com.android.purebilibili.core.ui.components.AppSegmentOption
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.core.plugin.SponsorBlockSessionApi
import com.android.purebilibili.plugin.sdk.PlayerPluginApi
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.plugin.sdk.PluginCapabilityManifest
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.rememberNotificationPermissionState
import com.android.purebilibili.data.model.response.SponsorBlockMarkerMode
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.data.repository.SponsorBlockRepository
import com.android.purebilibili.feature.settings.SettingsSingleChoicePreference
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.FileOutputStream
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

private const val TAG = "SponsorBlockPlugin"
private const val SPONSOR_BLOCK_RECENT_COVER_ASPECT_RATIO = 16f / 10f
private val PLUGIN_EVENT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun normalizeSponsorSegments(
    segments: List<SponsorSegment>
): List<SponsorSegment> {
    return segments
        .asSequence()
        .filter { segment ->
            segment.endTimeMs > segment.startTimeMs ||
                (segment.actionType == com.android.purebilibili.data.model.response.SponsorActionType.FULL &&
                    segment.startTimeMs == 0L && segment.endTimeMs == 0L)
        }
        .groupBy { segment -> "${segment.category}:${segment.actionType}" }
        .values
        .mapNotNull { candidates ->
            candidates.maxWithOrNull(
                compareBy<SponsorSegment> { it.locked }
                    .thenBy { it.votes }
                    .thenByDescending { it.startTimeMs }
                    .thenByDescending { it.endTimeMs }
            )
        }
        .sortedWith(compareBy<SponsorSegment> { it.startTimeMs }.thenBy { it.endTimeMs })
        .toList()
}

internal fun resolveSponsorProgressMarkers(
    segments: List<SponsorSegment>,
    markerMode: SponsorBlockMarkerMode,
    categoryColors: Map<String, String> = emptyMap(),
): List<SponsorProgressMarker> {
    if (markerMode == SponsorBlockMarkerMode.OFF) return emptyList()
    return segments.asSequence()
        .filter { segment ->
            when (markerMode) {
                SponsorBlockMarkerMode.OFF -> false
                SponsorBlockMarkerMode.SPONSOR_ONLY -> segment.category == com.android.purebilibili.data.model.response.SponsorCategory.SPONSOR
                SponsorBlockMarkerMode.ALL_SKIPPABLE -> true
            }
        }
        .filterNot { segment ->
            segment.actionType == com.android.purebilibili.data.model.response.SponsorActionType.FULL &&
                segment.startTimeMs == 0L && segment.endTimeMs == 0L
        }
        .map { segment ->
            SponsorProgressMarker(
                segmentId = segment.UUID,
                category = segment.category,
                startTimeMs = segment.startTimeMs,
                endTimeMs = segment.endTimeMs,
                colorHex = categoryColors[segment.category],
            )
        }
        .toList()
}

internal fun resetSkippedSegmentsForSeek(
    segments: List<SponsorSegment>,
    skippedIds: Set<String>,
    seekPositionMs: Long
): Set<String> {
    return skippedIds.filterTo(mutableSetOf()) { skippedId ->
        val segment = segments.firstOrNull { it.UUID == skippedId } ?: return@filterTo true
        seekPositionMs > segment.endTimeMs
    }
}

internal data class SponsorBlockAboutItemModel(
    val title: String,
    val subtitle: String?,
    val value: String?
)

internal fun resolveSponsorBlockAboutItemModel(): SponsorBlockAboutItemModel {
    return SponsorBlockAboutItemModel(
        title = "关于空降助手",
        subtitle = "BilibiliSponsorBlock",
        value = null
    )
}

/**
 *  空降助手插件
 *
 * 基于 SponsorBlock 数据库自动跳过视频中的广告、赞助、片头片尾等片段。
 * 播放状态由 createSession 为每次播放独立创建；此定义只持有设置界面的配置。
 */
class SponsorBlockPlugin : BuiltinPlugin, PlayerPluginApi {

    override val id = BuiltinPluginIds.SPONSOR_BLOCK
    override val name = "空降助手"
    override val description = "自动跳过视频中的广告、赞助、片头片尾等片段"
    override val version = "1.1.1"
    override val author = "BiliPai项目组"
    override val icon: ImageVector = Icons.Outlined.Send
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = "com.android.purebilibili.feature.plugin.SponsorBlockPlugin",
        capabilities = setOf(
            PluginCapability.PLAYER_STATE,
            PluginCapability.PLAYER_CONTROL,
            PluginCapability.NETWORK
        )
    )

    // 配置
    private var config: SponsorBlockConfig = SponsorBlockConfig().normalized()

    override suspend fun onEnable() {
        Logger.d(TAG, " 空降助手已启用")
    }

    override suspend fun onDisable() {
        Logger.d(TAG, "🔴 空降助手已禁用")
    }

    override fun createSession(): SponsorBlockSessionApi = SponsorBlockPlaybackSession()

    private suspend fun loadConfigSuspend(context: Context) {
        try {
            config = loadSponsorBlockConfig(context, config)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.e(TAG, "Failed to load config", error)
            config = config.normalized()
        }
    }

    @Composable
    override fun SettingsContent() {
        SponsorBlockSettingsContent(modifier = Modifier.padding(16.dp))
    }

    @Composable
    override fun SettingsContent(modifier: Modifier) {
        SponsorBlockSettingsContent(modifier = modifier)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun SponsorBlockSettingsContent(modifier: Modifier) {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val scope = rememberCoroutineScope()
        val neutralMiuix = isMiuixNonGlassEnabled()
        val preferenceIconColor = MaterialTheme.colorScheme.surfaceContainerHighest
        var autoSkip by remember { mutableStateOf(config.autoSkip) }
        var markerMode by remember { mutableStateOf(config.markerMode) }
        var dailySummaryNotificationEnabled by remember { mutableStateOf(config.dailySummaryNotificationEnabled) }
        var dailySummaryNotificationPrefix by remember { mutableStateOf(config.dailySummaryNotificationPrefix) }
        var categorySettings by remember { mutableStateOf(resolveSponsorBlockCategorySettings(config)) }
        var communityTrackingEnabled by remember { mutableStateOf(config.communityTrackingEnabled) }
        var communityContributionEnabled by remember { mutableStateOf(config.communityContributionEnabled) }
        var skipToastEnabled by remember { mutableStateOf(config.skipToastEnabled) }
        var feedBadgeEnabled by remember { mutableStateOf(config.feedBadgeEnabled) }
        var serverStatus by remember { mutableStateOf<SponsorBlockRepository.ServerStatus?>(null) }
        var communityUserInfo by remember { mutableStateOf<SponsorBlockRepository.CommunityUserInfo?>(null) }
        var showServerDialog by remember { mutableStateOf(false) }
        var serverDraft by remember { mutableStateOf(config.serverBaseUrl) }
        var showDurationDialog by remember { mutableStateOf(false) }
        var durationDraft by remember { mutableStateOf(config.minimumSegmentDurationSeconds.toString()) }
        var showCommunityUserDialog by remember { mutableStateOf(false) }
        var showRegenerateUserIdDialog by remember { mutableStateOf(false) }
        var userIdDraft by remember { mutableStateOf(config.userId) }
        var colorDialogCategory by remember { mutableStateOf<SponsorBlockCategorySetting?>(null) }
        var colorDraft by remember { mutableStateOf("") }
        var insightRecords by remember { mutableStateOf<List<SponsorBlockSkipRecord>>(emptyList()) }
        val aboutItem = remember { resolveSponsorBlockAboutItemModel() }
        val markerOptions = remember {
            SponsorBlockMarkerMode.entries.map { mode ->
                AppSegmentOption(
                    value = mode,
                    label = mode.label
                )
            }
        }
        val insightSummary = remember(insightRecords) {
            resolveSponsorBlockInsightSummary(
                records = insightRecords,
                dayStartMs = currentLocalDayStartMs()
            )
        }
        fun persistConfig(nextConfig: SponsorBlockConfig) {
            config = nextConfig.normalized()
            scope.launch {
                PluginStore.setConfigJson(context, id, Json.encodeToString(config))
                scheduleSponsorBlockDailySummary(context, config.dailySummaryNotificationEnabled)
            }
        }
        fun showNotificationToast(message: String) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
        fun sendTestNotification() {
            val posted = postSponsorBlockTestNotification(context, config)
            showNotificationToast(if (posted) "测试通知已发送" else "系统通知未开启")
        }
        val notificationPermission = rememberNotificationPermissionState { granted ->
            if (granted) {
                sendTestNotification()
            } else {
                showNotificationToast("通知权限未开启")
            }
        }

        // 加载配置
        LaunchedEffect(Unit) {
            loadConfigSuspend(context)
            autoSkip = config.autoSkip
            markerMode = config.markerMode
            dailySummaryNotificationEnabled = config.dailySummaryNotificationEnabled
            dailySummaryNotificationPrefix = config.dailySummaryNotificationPrefix
            categorySettings = resolveSponsorBlockCategorySettings(config)
            communityTrackingEnabled = config.communityTrackingEnabled
            communityContributionEnabled = config.communityContributionEnabled
            skipToastEnabled = config.skipToastEnabled
            feedBadgeEnabled = config.feedBadgeEnabled
            userIdDraft = config.userId
            serverDraft = config.serverBaseUrl
            durationDraft = config.minimumSegmentDurationSeconds.toString()
            insightRecords = SponsorBlockInsightStore.readRecords(context)
            scheduleSponsorBlockDailySummary(context, config.dailySummaryNotificationEnabled)
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SponsorBlockInsightPanel(
                summary = insightSummary,
                onShareClick = { shareSponsorBlockInsightImage(context, insightSummary) }
            )

            SponsorBlockSettingsSection(title = "片段规则", subtitle = "按类别选择总是跳过、本次跳过、手动跳过、仅显示或禁用") {
                AppSwitchPreference(
                    icon = Icons.Outlined.Bolt,
                    title = "全部自动跳过",
                    subtitle = "关闭后，所有类别先改为显示跳过按钮",
                    checked = autoSkip,
                    onCheckedChange = { newValue ->
                        autoSkip = newValue
                        val behavior = if (newValue) SponsorBlockSegmentBehavior.AUTOMATIC else SponsorBlockSegmentBehavior.MANUAL
                        persistConfig(config.copy(autoSkip = newValue, categoryBehaviorRaw = config.categoryBehaviorRaw.mapValues { behavior.name }))
                        categorySettings = resolveSponsorBlockCategorySettings(config)
                    },
                    iconTint = if (neutralMiuix) preferenceIconColor else MaterialTheme.colorScheme.tertiary,
                )
                SponsorBlockCategorySettingsSection(
                    settings = categorySettings,
                    onBehaviorChange = { category, behavior ->
                        persistConfig(config.copy(categoryBehaviorRaw = config.categoryBehaviorRaw + (category to behavior.name)))
                        categorySettings = resolveSponsorBlockCategorySettings(config)
                        autoSkip = categorySettings.all { it.behavior == SponsorBlockSegmentBehavior.AUTOMATIC }
                    },
                    onColorClick = { setting ->
                        colorDialogCategory = setting
                        colorDraft = config.categoryColorHex[setting.category] ?: setting.defaultColorHex
                    },
                )
                AppPreference(
                    icon = Icons.Outlined.Timer,
                    title = "最短片段时长",
                    subtitle = "短于该时长的社区片段不会参与跳过或提示",
                    value = "${config.minimumSegmentDurationSeconds}s",
                    onClick = { showDurationDialog = true },
                    iconTint = if (neutralMiuix) preferenceIconColor else MaterialTheme.colorScheme.tertiary,
                )
            }

            SponsorBlockSettingsSection(title = "进度条") {
                SettingsSingleChoicePreference(
                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.SPONSOR_MARKER),
                    title = "进度条提示：${markerMode.label}",
                    subtitle = "关闭、仅提示恰饭，或显示全部可跳过片段",
                    options = markerOptions,
                    selectedValue = markerMode,
                    onSelectionChange = { newValue ->
                        markerMode = newValue
                        persistConfig(config.copy(markerModeRaw = newValue.name))
                    },
                )
            }

            SponsorBlockSettingsSection(title = "通知") {
                AppSwitchPreference(
                    icon = Icons.Outlined.Info,
                    title = "跳过提示",
                    subtitle = "自动跳过时显示简短提示",
                    checked = skipToastEnabled,
                    onCheckedChange = { enabled ->
                        skipToastEnabled = enabled
                        persistConfig(config.copy(skipToastEnabled = enabled))
                    },
                    iconTint = if (neutralMiuix) preferenceIconColor else AppContentColors.accent(),
                )
                AppSwitchPreference(
                    icon = Icons.Outlined.Notifications,
                    title = "每日汇总通知",
                    subtitle = "当天有跳过记录时，汇总跳过次数和节省时间",
                    checked = dailySummaryNotificationEnabled,
                    onCheckedChange = { newValue ->
                        dailySummaryNotificationEnabled = newValue
                        persistConfig(config.copy(dailySummaryNotificationEnabled = newValue))
                    },
                    iconTint = if (neutralMiuix) preferenceIconColor else AppContentColors.accent(),
                )
                if (dailySummaryNotificationEnabled) {
                    AppOutlinedTextField(
                        value = dailySummaryNotificationPrefix,
                        onValueChange = { nextValue ->
                            dailySummaryNotificationPrefix = nextValue
                            persistConfig(config.copy(dailySummaryNotificationPrefix = nextValue))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        label = { AppText("通知文案前缀") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
                AppPreference(
                    icon = Icons.Outlined.Notifications,
                    title = "发送测试通知",
                    subtitle = "确认通知权限和展示效果，不写入跳过记录",
                    onClick = { notificationPermission.launchWithPermission { sendTestNotification() } },
                    iconTint = if (neutralMiuix) preferenceIconColor else AppContentColors.accent(),
                )
            }

            SponsorBlockSettingsSection(title = "列表标记") {
                AppSwitchPreference(
                    icon = Icons.Outlined.Sell,
                    title = "列表恰饭角标",
                    subtitle = "动态、UP 主页、首页推荐、稍后再看等列表中，为社区数据标记了恰饭片段的视频显示角标",
                    checked = feedBadgeEnabled,
                    onCheckedChange = { enabled ->
                        feedBadgeEnabled = enabled
                        persistConfig(config.copy(feedBadgeEnabled = enabled))
                    },
                    iconTint = if (neutralMiuix) preferenceIconColor else AppContentColors.accent(),
                )
            }

            SponsorBlockSettingsSection(title = "社区与隐私", subtitle = "所有发送都指向下方服务器；默认关闭，提交片段始终需要逐次确认") {
                AppPreference(
                    icon = Icons.Outlined.Dns,
                    title = "社区服务器",
                    subtitle = serverStatus?.message ?: config.serverBaseUrl,
                    value = if (serverStatus?.reachable == true) "正常" else null,
                    onClick = { showServerDialog = true },
                    iconTint = if (neutralMiuix) preferenceIconColor else MaterialTheme.colorScheme.secondary,
                )
                AppSwitchPreference(
                    icon = Icons.Outlined.BarChart,
                    title = "上传跳过贡献",
                    subtitle = "仅在实际跳过时上传片段匿名 UUID；不会上传连续观看轨迹",
                    checked = communityTrackingEnabled,
                    onCheckedChange = { enabled ->
                        communityTrackingEnabled = enabled
                        persistConfig(config.copy(communityTrackingEnabled = enabled))
                    },
                    iconTint = if (neutralMiuix) preferenceIconColor else MaterialTheme.colorScheme.tertiary,
                )
                AppSwitchPreference(
                    icon = Icons.Outlined.Send,
                    title = "允许提交社区片段",
                    subtitle = "允许播放页标记起止时间；提交前会显示类别、时间和服务器供确认",
                    checked = communityContributionEnabled,
                    onCheckedChange = { enabled ->
                        communityContributionEnabled = enabled
                        persistConfig(config.copy(communityContributionEnabled = enabled))
                    },
                    iconTint = if (neutralMiuix) preferenceIconColor else MaterialTheme.colorScheme.tertiary,
                )
                AppPreference(
                    icon = Icons.Outlined.Person,
                    title = "社区用户 ID",
                    subtitle = "${config.userId.take(8)}… · 用于归属你的片段贡献",
                    onClick = {
                        showCommunityUserDialog = true
                        scope.launch {
                            communityUserInfo = SponsorBlockRepository.getCommunityUserInfo(config.serverBaseUrl, config.userId)
                                .getOrElse { error ->
                                    showNotificationToast(error.message ?: "无法读取社区用户信息")
                                    null
                                }
                        }
                    },
                    iconTint = if (neutralMiuix) preferenceIconColor else MaterialTheme.colorScheme.tertiary,
                )
            }

            SponsorBlockSettingsSection(title = "关于") {
                AppPreference(
                    icon = Icons.Outlined.Info,
                    title = aboutItem.title,
                    subtitle = aboutItem.subtitle,
                    value = aboutItem.value,
                    onClick = { uriHandler.openUri("https://github.com/hanydd/BilibiliSponsorBlock") },
                    iconTint = if (neutralMiuix) preferenceIconColor else MaterialTheme.colorScheme.secondary,
                )
            }
        }

        if (showDurationDialog) {
            AppAlertDialog(
                onDismissRequest = { showDurationDialog = false },
                title = { AppText("最短片段时长") },
                text = {
                    AppTextField(
                        value = durationDraft,
                        onValueChange = { durationDraft = it },
                        label = "秒数",
                        singleLine = true
                    )
                },
                confirmButton = {
                    AppTextButton(onClick = {
                        val value = durationDraft.toFloatOrNull()
                        if (value == null || value < 0f) {
                            showNotificationToast("请输入大于或等于 0 的秒数")
                        } else {
                            persistConfig(config.copy(minimumSegmentDurationSeconds = value))
                            showDurationDialog = false
                        }
                    }) { AppText("保存") }
                },
                dismissButton = { AppTextButton(onClick = { showDurationDialog = false }) { AppText("取消") } }
            )
        }

        if (showServerDialog) {
            AppAlertDialog(
                onDismissRequest = { showServerDialog = false },
                title = { AppText("社区服务器") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText("自定义服务器会接收片段查询、可选跳过记录和你确认提交的片段。")
                        AppTextField(
                            value = serverDraft,
                            onValueChange = { serverDraft = it },
                            label = "API 地址",
                            placeholder = "https://example.com/api",
                            singleLine = true
                        )
                        communityUserInfo?.let { info ->
                            AppText("${info.userName.ifBlank { "匿名用户" }} · 已贡献 ${info.segmentCount} 段 · 节省 ${info.minutesSaved.toInt()} 分钟")
                        }
                    }
                },
                confirmButton = {
                    AppTextButton(onClick = {
                        val normalized = normalizeSponsorBlockServerUrl(serverDraft)
                        if (normalized == null) {
                            showNotificationToast("请输入有效的 http/https API 地址")
                        } else {
                            persistConfig(config.copy(serverBaseUrl = normalized))
                            serverDraft = normalized
                            scope.launch {
                                serverStatus = SponsorBlockRepository.checkServerStatus(normalized)
                                communityUserInfo = SponsorBlockRepository.getCommunityUserInfo(normalized, config.userId)
                                    .getOrNull()
                            }
                        }
                    }) { AppText("保存并检测") }
                },
                dismissButton = {
                    AppTextButton(onClick = {
                        persistConfig(config.copy(serverBaseUrl = SponsorBlockRepository.DEFAULT_BASE_URL))
                        serverDraft = SponsorBlockRepository.DEFAULT_BASE_URL
                        showServerDialog = false
                    }) { AppText("恢复默认") }
                }
            )
        }

        if (showCommunityUserDialog) {
            AppAlertDialog(
                onDismissRequest = { showCommunityUserDialog = false },
                title = { AppText("社区用户 ID") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(config.userId, style = MaterialTheme.typography.bodySmall)
                        AppTextField(
                            value = userIdDraft,
                            onValueChange = { userIdDraft = it },
                            label = "用户 ID",
                            singleLine = true,
                        )
                        communityUserInfo?.let { info ->
                            AppText("${info.userName.ifBlank { "匿名用户" }} · 已贡献 ${info.segmentCount} 段 · 节省 ${info.minutesSaved.toInt()} 分钟")
                        }
                        AppText("此 ID 仅用于当前服务器上的贡献归属。重新生成后，旧贡献不会迁移。", style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                        AppTextButton(onClick = { showRegenerateUserIdDialog = true }) { AppText("重新生成 ID") }
                    }
                },
                confirmButton = {
                    AppTextButton(onClick = {
                        validateSponsorBlockUserId(userIdDraft)?.let(::showNotificationToast) ?: run {
                            persistConfig(config.copy(userId = userIdDraft.trim()))
                            showNotificationToast("用户 ID 已保存")
                        }
                    }) { AppText("保存") }
                },
                dismissButton = {
                    AppTextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("社区用户 ID", config.userId))
                        showNotificationToast("用户 ID 已复制")
                    }) { AppText("复制") }
                },
            )
        }

        if (showRegenerateUserIdDialog) {
            AppAlertDialog(
                onDismissRequest = { showRegenerateUserIdDialog = false },
                title = { AppText("重新生成社区用户 ID？") },
                text = { AppText("旧 ID 的贡献不会迁移到新 ID。") },
                confirmButton = {
                    AppTextButton(onClick = {
                        persistConfig(config.copy(userId = generateSponsorBlockUserId()))
                        showRegenerateUserIdDialog = false
                        showCommunityUserDialog = false
                    }) { AppText("确认重新生成") }
                },
                dismissButton = { AppTextButton(onClick = { showRegenerateUserIdDialog = false }) { AppText("取消") } },
            )
        }

        colorDialogCategory?.let { setting ->
            val normalizedDraft = colorDraft.trim().uppercase()
            val validDraft = normalizedDraft.isBlank() || Regex("^#[0-9A-F]{6}$").matches(normalizedDraft)
            val initialColor = remember(setting.category) {
                Color(AndroidColor.parseColor(config.categoryColorHex[setting.category] ?: setting.defaultColorHex))
            }
            var previewColor by remember(setting.category) { mutableStateOf(initialColor) }
            AppAlertDialog(
                onDismissRequest = { colorDialogCategory = null },
                title = { AppText("${setting.title}进度条颜色") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppText("拖动选择颜色，或输入 #RRGGBB。保存后应用于此类片段。")
                        AppColorPicker(
                            color = previewColor,
                            onColorChanged = {
                                previewColor = it
                                colorDraft = "#%06X".format(it.toArgb() and 0xFFFFFF)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = colorDraft,
                            onValueChange = {
                                colorDraft = it
                                val hex = it.trim()
                                if (Regex("^#[0-9a-fA-F]{6}$").matches(hex)) {
                                    previewColor = Color(AndroidColor.parseColor(hex))
                                } else if (hex.isBlank()) {
                                    previewColor = Color(AndroidColor.parseColor(setting.defaultColorHex))
                                }
                            },
                            label = "颜色（#RRGGBB）",
                            singleLine = true,
                        )
                        if (!validDraft) AppText("颜色格式应为 #RRGGBB", color = MaterialTheme.colorScheme.error)
                        AppTextButton(onClick = {
                            colorDraft = ""
                            previewColor = Color(AndroidColor.parseColor(setting.defaultColorHex))
                        }) { AppText("恢复默认颜色") }
                    }
                },
                confirmButton = {
                    AppTextButton(onClick = {
                        val normalized = colorDraft.trim().uppercase()
                        if (normalized.isNotBlank() && !Regex("^#[0-9A-F]{6}$").matches(normalized)) {
                            showNotificationToast("颜色格式应为 #RRGGBB")
                        } else {
                            val colors = if (normalized.isBlank()) config.categoryColorHex - setting.category
                                else config.categoryColorHex + (setting.category to normalized)
                            persistConfig(config.copy(categoryColorHex = colors))
                            colorDialogCategory = null
                        }
                    }, enabled = validDraft) { AppText("保存") }
                },
                dismissButton = { AppTextButton(onClick = { colorDialogCategory = null }) { AppText("取消") } },
            )
        }
    }
}

@Composable
private fun SponsorBlockSettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(text = title, style = MaterialTheme.typography.titleMedium, color = AppContentColors.primaryText())
        if (subtitle != null) {
            AppText(text = subtitle, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            shape = AppShapes.container(ContainerLevel.Card),
        ) {
            Column(content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SponsorBlockCategorySettingsSection(
    settings: List<SponsorBlockCategorySetting>,
    onBehaviorChange: (String, SponsorBlockSegmentBehavior) -> Unit,
    onColorClick: (SponsorBlockCategorySetting) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
        settings.forEach { setting ->
            AppSurface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                shape = AppShapes.container(ContainerLevel.Card),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(setting.title, style = MaterialTheme.typography.titleSmall, color = AppContentColors.primaryText())
                    AppText(setting.description, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SponsorBlockSegmentBehavior.entries.forEach { behavior ->
                            AppFilterChip(
                                selected = setting.behavior == behavior,
                                onClick = { onBehaviorChange(setting.category, behavior) },
                                label = { AppText(behavior.label) },
                            )
                        }
                    }
                    AppTextButton(onClick = { onColorClick(setting) }) { AppText("自定义进度条颜色") }
                }
            }
        }
    }
}

@Composable
private fun SponsorBlockInsightPanel(
    summary: SponsorBlockInsightSummary,
    onShareClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(14.dp)
    ) {
        val useWideLayout = maxWidth >= 460.dp
        if (useWideLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SponsorBlockSummaryRail(
                    summary = summary,
                    onShareClick = onShareClick,
                    modifier = Modifier.widthIn(min = 156.dp, max = 190.dp)
                )
                SponsorBlockRecentSection(
                    summary = summary,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SponsorBlockSummaryHeader(summary = summary)
                SponsorBlockCompactStats(summary = summary)
                SponsorBlockPeriodStats(summary = summary)
                SponsorBlockFavoriteSection(summary = summary)
                AppButton(
                    onClick = onShareClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppText("分享统计图片")
                }
                SponsorBlockRecentSection(summary = summary)
            }
        }
    }
}

@Composable
private fun SponsorBlockSummaryRail(
    summary: SponsorBlockInsightSummary,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SponsorBlockSummaryHeader(summary = summary)
        SponsorBlockStatTile(
            title = "今日节省",
            value = summary.todaySavedText,
            modifier = Modifier.fillMaxWidth()
        )
        SponsorBlockStatTile(
            title = "累计节省",
            value = summary.totalSavedText,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SponsorBlockStatTile(
                title = "次数",
                value = "${summary.totalSkipCount}",
                modifier = Modifier.weight(1f)
            )
            SponsorBlockStatTile(
                title = "UP",
                value = "${summary.uniqueUpCount}",
                modifier = Modifier.weight(1f)
            )
        }
        SponsorBlockPeriodStats(summary = summary)
        SponsorBlockFavoriteSection(summary = summary)
        AppButton(
            onClick = onShareClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            AppText("分享统计图片")
        }
    }
}

@Composable
private fun SponsorBlockSummaryHeader(summary: SponsorBlockInsightSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppText(
                text = "跳过统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText()
            )
            AppText(
                text = "记录只保存在本地",
                style = MaterialTheme.typography.labelMedium,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(AppShapes.container(ContainerLevel.Pill))
                .background(sponsorBlockBadgeContainerColor(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            AppText(
                text = "${summary.totalSkipCount} 次",
                style = MaterialTheme.typography.labelMedium,
                color = sponsorBlockBadgeContentColor(),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SponsorBlockCompactStats(summary: SponsorBlockInsightSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SponsorBlockCompactStatItem(
            title = "今日",
            value = summary.todaySavedText,
            modifier = Modifier.weight(1f)
        )
        SponsorBlockCompactStatItem(
            title = "累计",
            value = summary.totalSavedText,
            modifier = Modifier.weight(1f)
        )
        SponsorBlockCompactStatItem(
            title = "次数",
            value = "${summary.totalSkipCount}",
            modifier = Modifier.weight(1f)
        )
        SponsorBlockCompactStatItem(
            title = "UP",
            value = "${summary.uniqueUpCount}",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SponsorBlockCompactStatItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText(),
            maxLines = 1
        )
        AppText(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = AppContentColors.primaryText(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SponsorBlockPeriodStats(summary: SponsorBlockInsightSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppText(
            text = "频次对比",
            style = MaterialTheme.typography.labelLarge,
            color = AppContentColors.secondaryText(),
            fontWeight = FontWeight.Medium
        )
        summary.periodStats.forEach { stat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = stat.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.primaryText()
                )
                AppText(
                    text = "${stat.skipCount} 次 · ${stat.uniqueVideoCount} 个视频",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText()
                )
            }
        }
    }
}

@Composable
private fun SponsorBlockFavoriteSection(summary: SponsorBlockInsightSummary) {
    val topVideoText = summary.topVideo?.let {
        "${it.title.ifBlank { it.bvid }} · ${it.skipCount} 次"
    } ?: "暂无"
    val topUpText = summary.topUp?.let {
        "${it.name.ifBlank { "未知UP" }} · ${it.skipCount} 次"
    } ?: "暂无"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SponsorBlockFavoriteRow(title = "最常跳过视频", value = topVideoText)
        SponsorBlockFavoriteRow(title = "最常命中 UP", value = topUpText)
    }
}

@Composable
private fun SponsorBlockFavoriteRow(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppText(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText()
        )
        AppText(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = AppContentColors.primaryText(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SponsorBlockRecentSection(
    summary: SponsorBlockInsightSummary,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (summary.recentRecords.isEmpty()) {
            SponsorBlockEmptyInsight()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = "最近跳过 · ${summary.recentRecords.size} 条",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppContentColors.secondaryText(),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                AppTextButton(onClick = { expanded = !expanded }) {
                    AppText(if (expanded) "一键收起" else "一键展开")
                }
            }
            if (expanded) {
                summary.recentRecords.forEach { record ->
                    SponsorBlockRecordRow(record = record)
                }
            }
        }
    }
}

@Composable
private fun SponsorBlockStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText()
        )
        AppText(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AppContentColors.primaryText()
        )
    }
}

@Composable
private fun SponsorBlockEmptyInsight() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = "还没有跳过记录",
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText()
        )
    }
}

@Composable
private fun SponsorBlockRecordRow(record: SponsorBlockSkipRecord) {
    val context = LocalContext.current
    var showDetailDialog by remember(record.timestampMs, record.segmentId) { mutableStateOf(false) }
    if (showDetailDialog) {
        SponsorBlockRecordDetailDialog(
            record = record,
            onDismiss = { showDetailDialog = false }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .combinedClickable(
                onClick = {},
                onLongClick = { showDetailDialog = true }
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(FormatUtils.fixImageUrl(record.videoCoverUrl))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(SPONSOR_BLOCK_RECENT_COVER_ASPECT_RATIO)
                .clip(AppShapes.container(ContainerLevel.Chip))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            AppText(
                text = record.videoTitle.ifBlank { "未知视频" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(FormatUtils.fixImageUrl(record.upFaceUrl))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                AppText(
                    text = record.upName.ifBlank { "未知 UP" },
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SponsorBlockChip(text = record.segmentCategoryName)
                SponsorBlockChip(text = record.triggerLabel)
            }
            AppText(
                text = "${record.progressText}  ·  节省 ${record.savedText}",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
        }
    }
}

@Composable
private fun SponsorBlockRecordDetailDialog(
    record: SponsorBlockSkipRecord,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("跳过详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SponsorBlockDetailLine("视频", record.videoTitle.ifBlank { "未知视频" })
                SponsorBlockDetailLine("UP 主", record.upName.ifBlank { "未知 UP" })
                SponsorBlockDetailLine("片段类型", record.segmentCategoryName)
                SponsorBlockDetailLine("来源", record.triggerLabel)
                SponsorBlockDetailLine("跳转区间", record.progressText)
                SponsorBlockDetailLine("节省时间", record.savedText)
                SponsorBlockDetailLine("跳过时间", formatPluginEventTime(record.timestampMs))
                if (record.bvid.isNotBlank()) {
                    SponsorBlockDetailLine("BVID", record.bvid)
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("知道了")
            }
        }
    )
}

@Composable
private fun SponsorBlockDetailLine(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText()
        )
        AppText(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.primaryText()
        )
    }
}

@Composable
private fun sponsorBlockBadgeContainerColor(alpha: Float): Color =
    if (isMiuixNonGlassEnabled()) MaterialTheme.colorScheme.surfaceContainerHighest
    else AppContentColors.accent().copy(alpha = alpha)

@Composable
private fun sponsorBlockBadgeContentColor(): Color =
    if (isMiuixNonGlassEnabled()) AppContentColors.primaryText()
    else AppContentColors.accent()

@Composable
private fun SponsorBlockChip(text: String) {
    Box(
        modifier = Modifier
            .clip(AppShapes.container(ContainerLevel.Pill))
            .background(sponsorBlockBadgeContainerColor(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = sponsorBlockBadgeContentColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shareSponsorBlockInsightImage(
    context: Context,
    summary: SponsorBlockInsightSummary
) {
    runCatching {
        val bitmap = buildSponsorBlockInsightBitmap(summary)
        val shareDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val shareFile = File(shareDir, "sponsor_block_insight.png")
        FileOutputStream(shareFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildSponsorBlockInsightShareText(summary))
            putExtra(Intent.EXTRA_SUBJECT, "BiliPai 空降助手统计")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "分享空降助手统计"))
    }.onFailure { error ->
        Logger.e(TAG, "分享空降助手统计图片失败: ${error.message}")
        android.widget.Toast.makeText(context, "分享图片生成失败", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun buildSponsorBlockInsightBitmap(summary: SponsorBlockInsightSummary): Bitmap {
    val width = 1080
    val horizontalPadding = 72f
    val bitmap = Bitmap.createBitmap(width, 1480, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.rgb(18, 24, 34))

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 54f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(190, 202, 218)
        textSize = 30f
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 214, 102)
        textSize = 42f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(230, 236, 244)
        textSize = 32f
    }
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(34, 44, 59)
    }

    var y = 96f
    canvas.drawText("BiliPai 空降助手统计", horizontalPadding, y, titlePaint)
    y += 54f
    canvas.drawText("记录只保存在本地，可一键分享", horizontalPadding, y, subtitlePaint)

    y += 58f
    drawSponsorShareCard(canvas, cardPaint, horizontalPadding, y, width - horizontalPadding, y + 190f)
    canvas.drawText("累计跳过", horizontalPadding + 34f, y + 62f, subtitlePaint)
    canvas.drawText("${summary.totalSkipCount} 次", horizontalPadding + 34f, y + 128f, valuePaint)
    canvas.drawText("累计节省 ${summary.totalSavedText}", horizontalPadding + 430f, y + 128f, valuePaint)

    y += 245f
    summary.periodStats.forEach { stat ->
        drawSponsorShareCard(canvas, cardPaint, horizontalPadding, y, width - horizontalPadding, y + 118f)
        canvas.drawText(stat.label, horizontalPadding + 30f, y + 48f, bodyPaint)
        canvas.drawText("${stat.skipCount} 次 / ${stat.uniqueVideoCount} 个视频", horizontalPadding + 260f, y + 48f, bodyPaint)
        canvas.drawText("节省 ${stat.savedText}", horizontalPadding + 260f, y + 90f, subtitlePaint)
        y += 136f
    }

    y += 18f
    val topVideo = summary.topVideo?.let {
        "最常跳过视频：${it.title.ifBlank { it.bvid }}（${it.skipCount} 次）"
    } ?: "最常跳过视频：暂无"
    val topUp = summary.topUp?.let {
        "最常命中 UP：${it.name.ifBlank { "未知UP" }}（${it.skipCount} 次，${it.uniqueVideoCount} 个视频）"
    } ?: "最常命中 UP：暂无"
    listOf(topVideo, topUp).forEach { line ->
        wrapShareText(line, bodyPaint, width - horizontalPadding * 2 - 40f).forEach { wrapped ->
            canvas.drawText(wrapped, horizontalPadding, y, bodyPaint)
            y += 44f
        }
        y += 16f
    }

    canvas.drawText("由 BiliPai 生成", horizontalPadding, 1408f, subtitlePaint)
    return bitmap
}

private fun drawSponsorShareCard(
    canvas: Canvas,
    paint: Paint,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
) {
    canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, paint)
}

private fun wrapShareText(
    text: String,
    paint: Paint,
    maxWidth: Float
): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)
    val result = mutableListOf<String>()
    var current = ""
    text.forEach { char ->
        val next = current + char
        if (paint.measureText(next) > maxWidth && current.isNotEmpty()) {
            result += current
            current = char.toString()
        } else {
            current = next
        }
    }
    if (current.isNotEmpty()) result += current
    return result
}

/**
 * 空降助手配置
 */
@Serializable
data class SponsorBlockConfig(
    val autoSkip: Boolean = true,
    val markerModeRaw: String = SponsorBlockMarkerMode.SPONSOR_ONLY.name,
    val skipSponsor: Boolean = true,
    val skipIntro: Boolean = true,
    val skipOutro: Boolean = true,
    val skipInteraction: Boolean = true,
    val categoryBehaviorRaw: Map<String, String> = emptyMap(),
    val categoryColorHex: Map<String, String> = emptyMap(),
    val minimumSegmentDurationSeconds: Float = 0f,
    val skipToastEnabled: Boolean = true,
    val feedBadgeEnabled: Boolean = true,
    val serverBaseUrl: String = SponsorBlockRepository.DEFAULT_BASE_URL,
    val userId: String = "",
    val communityTrackingEnabled: Boolean = false,
    val communityContributionEnabled: Boolean = false,
    val dailySummaryNotificationEnabled: Boolean = false,
    val dailySummaryNotificationPrefix: String = DEFAULT_DAILY_SUMMARY_PREFIX
) {
    val markerMode: SponsorBlockMarkerMode
        get() = com.android.purebilibili.data.model.response.resolveSponsorBlockMarkerMode(markerModeRaw)

    val requestedCategories: List<String>
        get() = com.android.purebilibili.data.model.response.SponsorCategory.ALL_CATEGORIES
            .filter { behaviorFor(it) != SponsorBlockSegmentBehavior.DISABLED }

    fun behaviorFor(category: String): SponsorBlockSegmentBehavior {
        val legacyFallback = if (autoSkip) SponsorBlockSegmentBehavior.AUTOMATIC else SponsorBlockSegmentBehavior.MANUAL
        return resolveSponsorBlockSegmentBehavior(category, categoryBehaviorRaw, legacyFallback)
    }

    fun normalized(): SponsorBlockConfig {
        val migratedBehaviors = if (categoryBehaviorRaw.isEmpty()) {
            defaultSponsorBlockCategoryBehaviors(
                autoSkip = autoSkip,
                skipSponsor = skipSponsor,
                skipIntro = skipIntro,
                skipOutro = skipOutro,
                skipInteraction = skipInteraction
            ).mapValues { it.value.name }
        } else {
            categoryBehaviorRaw.mapValues { (_, value) ->
                SponsorBlockSegmentBehavior.entries.firstOrNull { it.name == value }?.name
                    ?: SponsorBlockSegmentBehavior.MANUAL.name
            }
        }
        return copy(
            markerModeRaw = markerMode.name,
            categoryBehaviorRaw = migratedBehaviors,
            categoryColorHex = categoryColorHex.mapNotNull { (category, color) ->
                color.takeIf { category in com.android.purebilibili.data.model.response.SponsorCategory.ALL_CATEGORIES &&
                    Regex("^#[0-9a-fA-F]{6}$").matches(it) }?.let { category to it.uppercase() }
            }.toMap(),
            minimumSegmentDurationSeconds = minimumSegmentDurationSeconds.coerceAtLeast(0f),
            serverBaseUrl = normalizeSponsorBlockServerUrl(serverBaseUrl)
                ?: SponsorBlockRepository.DEFAULT_BASE_URL,
            userId = userId.ifBlank(::generateSponsorBlockUserId)
        )
    }

    companion object {
        const val DEFAULT_DAILY_SUMMARY_PREFIX = "今日空降助手已帮你节省"

        fun default(): SponsorBlockConfig = SponsorBlockConfig()
    }
}

private val SponsorBlockMarkerMode.label: String
    get() = when (this) {
        SponsorBlockMarkerMode.OFF -> "关闭"
        SponsorBlockMarkerMode.SPONSOR_ONLY -> "仅恰饭"
        SponsorBlockMarkerMode.ALL_SKIPPABLE -> "全部可跳过"
    }

private fun formatPluginEventTime(timestampMs: Long): String {
    return Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(PLUGIN_EVENT_TIME_FORMATTER)
}
