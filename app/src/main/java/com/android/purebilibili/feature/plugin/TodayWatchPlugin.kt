package com.android.purebilibili.feature.plugin
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppFilterChip
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.plugin.PluginCapability
import com.android.purebilibili.core.plugin.PluginCapabilityManifest
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.plugin.RecommendationAction
import com.android.purebilibili.core.plugin.RecommendationCreatorSignal
import com.android.purebilibili.core.plugin.RecommendationGroup
import com.android.purebilibili.core.plugin.RecommendationGroupItem
import com.android.purebilibili.core.plugin.RecommendationMode
import com.android.purebilibili.core.plugin.RecommendationPluginApi
import com.android.purebilibili.core.plugin.RecommendationRequest
import com.android.purebilibili.core.plugin.RecommendationResult
import com.android.purebilibili.core.plugin.RecommendationStrategy
import com.android.purebilibili.core.plugin.RecommendedVideo
import com.android.purebilibili.core.store.TodayWatchFeedbackSnapshot
import com.android.purebilibili.core.store.TodayWatchFeedbackStore
import com.android.purebilibili.core.store.TodayWatchProfileStore
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.home.TodayWatchCreatorSignal
import com.android.purebilibili.feature.home.TodayWatchMode
import com.android.purebilibili.feature.home.TodayWatchPenaltySignals
import com.android.purebilibili.feature.home.buildTodayWatchPlan
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ViewList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

private const val TAG = "TodayWatchPlugin"

@Serializable
enum class TodayWatchPluginMode {
    RELAX,
    LEARN
}

@Serializable
enum class TodayWatchCandidatePoolMode {
    LOCAL,
    EXPANDED
}

@Serializable
data class TodayWatchPluginConfig(
    val currentMode: TodayWatchPluginMode = TodayWatchPluginMode.RELAX,
    val recommendationStrategy: RecommendationStrategy = RecommendationStrategy.BALANCED,
    val candidatePoolMode: TodayWatchCandidatePoolMode = TodayWatchCandidatePoolMode.LOCAL,
    val upRankLimit: Int = 5,
    val queueBuildLimit: Int = 20,
    val queuePreviewLimit: Int = 6,
    val historySampleLimit: Int = 80,
    val linkEyeCareSignal: Boolean = true,
    val showUpRank: Boolean = true,
    val showReasonHint: Boolean = true,
    val enableWaterfallAnimation: Boolean = true,
    val waterfallExponent: Float = 1.38f,
    val collapsed: Boolean = false,
    val refreshTriggerToken: Long = 0L
)

class TodayWatchPlugin : RecommendationPluginApi {

    override val id: String = PLUGIN_ID
    override val name: String = "今日推荐单"
    override val description: String = "本地分析观看历史，生成可定制推荐队列"
    override val version: String = "1.1.0"
    override val author: String = "BiliPai项目组"
    override val icon: ImageVector = Icons.Outlined.ViewList
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = "com.android.purebilibili.feature.plugin.TodayWatchPlugin",
        capabilities = setOf(
            PluginCapability.RECOMMENDATION_CANDIDATES,
            PluginCapability.LOCAL_HISTORY_READ,
            PluginCapability.LOCAL_FEEDBACK_READ
        )
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var config: TodayWatchPluginConfig = TodayWatchPluginConfig()
    private val _configState = MutableStateFlow(config)
    val configState: StateFlow<TodayWatchPluginConfig> = _configState.asStateFlow()

    override suspend fun onEnable() {
        loadConfigSuspend()
        Logger.d(TAG, "今日推荐单插件已启用")
    }

    override suspend fun onDisable() {
        Logger.d(TAG, "今日推荐单插件已禁用")
    }

    fun updateConfig(transform: (TodayWatchPluginConfig) -> TodayWatchPluginConfig) {
        val updated = normalizeConfig(transform(config))
        if (updated == config) return
        config = updated
        _configState.value = updated
        saveConfig()
    }

    fun setCurrentMode(mode: TodayWatchPluginMode) {
        updateConfig { it.copy(currentMode = mode) }
    }

    override fun buildRecommendations(request: RecommendationRequest): RecommendationResult {
        val plan = buildTodayWatchPlan(
            historyVideos = request.historyVideos,
            candidateVideos = request.candidateVideos,
            mode = request.mode.toTodayWatchMode(),
            eyeCareNightActive = request.sceneSignals.eyeCareNightActive,
            nowEpochSec = request.sceneSignals.nowEpochSec,
            upRankLimit = request.groupLimit,
            queueLimit = request.queueLimit,
            strategy = request.strategy,
            creatorSignals = request.creatorSignals.map { it.toTodayWatchCreatorSignal() },
            penaltySignals = TodayWatchPenaltySignals(
                consumedBvids = request.feedbackSignals.consumedBvids,
                dislikedBvids = request.feedbackSignals.dislikedBvids,
                dislikedCreatorMids = request.feedbackSignals.dislikedCreatorMids,
                dislikedKeywords = request.feedbackSignals.dislikedKeywords
            )
        )
        return RecommendationResult(
            sourcePluginId = id,
            mode = request.mode,
            items = plan.videoQueue.map { video ->
                RecommendedVideo(
                    video = video,
                    score = plan.scoreByBvid[video.bvid] ?: 0.0,
                    confidence = plan.confidenceByBvid[video.bvid] ?: 0f,
                    explanation = plan.explanationByBvid[video.bvid].orEmpty(),
                    actions = listOf(
                        RecommendationAction(
                            id = "open",
                            label = "播放",
                            targetBvid = video.bvid
                        )
                    )
                )
            },
            groups = listOf(
                RecommendationGroup(
                    id = "preferred_creators",
                    title = "偏好 UP",
                    items = plan.upRanks.map { rank ->
                        RecommendationGroupItem(
                            id = rank.mid.toString(),
                            title = rank.name,
                            subtitle = "${rank.watchCount} 次观看",
                            score = rank.score,
                            watchCount = rank.watchCount
                        )
                    }
                )
            ),
            historySampleCount = plan.historySampleCount,
            sceneSignals = request.sceneSignals,
            generatedAt = plan.generatedAt
        )
    }

    fun clearPersonalizationData() {
        ioScope.launch {
            try {
                val context = PluginManager.getContext()
                TodayWatchProfileStore.clear(context)
                TodayWatchFeedbackStore.clear(context)
                updateConfig { current ->
                    current.copy(refreshTriggerToken = System.currentTimeMillis())
                }
                Logger.d(TAG, "已清空今日推荐画像与反馈缓存")
            } catch (e: Exception) {
                Logger.e(TAG, "清空画像失败", e)
            }
        }
    }

    private suspend fun loadConfigSuspend() {
        try {
            val context = PluginManager.getContext()
            val json = PluginStore.getConfigJson(context, id)
            config = if (json.isNullOrBlank()) {
                TodayWatchPluginConfig()
            } else {
                normalizeConfig(Json.decodeFromString(json))
            }
            _configState.value = config
        } catch (e: Exception) {
            Logger.e(TAG, "加载配置失败", e)
            config = TodayWatchPluginConfig()
            _configState.value = config
        }
    }

    private fun saveConfig() {
        ioScope.launch {
            try {
                val context = PluginManager.getContext()
                PluginStore.setConfigJson(context, id, Json.encodeToString(config))
            } catch (e: Exception) {
                Logger.e(TAG, "保存配置失败", e)
            }
        }
    }

    private fun normalizeConfig(source: TodayWatchPluginConfig): TodayWatchPluginConfig {
        val upRankLimit = source.upRankLimit.coerceIn(1, 12)
        val queueBuildLimit = source.queueBuildLimit.coerceIn(6, 40)
        val queuePreviewLimit = source.queuePreviewLimit.coerceIn(3, 12).coerceAtMost(queueBuildLimit)
        return source.copy(
            upRankLimit = upRankLimit,
            queueBuildLimit = queueBuildLimit,
            queuePreviewLimit = queuePreviewLimit,
            historySampleLimit = source.historySampleLimit.coerceIn(20, 120),
            waterfallExponent = source.waterfallExponent.coerceIn(1.0f, 2.2f)
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        val configSnapshot by configState.collectAsStateWithLifecycle()
        var uiConfig by remember { mutableStateOf(configSnapshot) }
        var feedbackSnapshot by remember {
            mutableStateOf(TodayWatchFeedbackStore.getSnapshot(context))
        }
        var showResetDialog by remember { mutableStateOf(false) }
        var resetMessage by remember { mutableStateOf<String?>(null) }
        var creatorSignals by remember {
            mutableStateOf(TodayWatchProfileStore.getCreatorSignals(context, limit = 5))
        }
        val insightState = remember(uiConfig.currentMode, feedbackSnapshot, creatorSignals) {
            buildTodayWatchTasteInsightState(
                mode = uiConfig.currentMode,
                feedbackSnapshot = feedbackSnapshot,
                creatorSignals = creatorSignals
            )
        }

        LaunchedEffect(Unit) {
            loadConfigSuspend()
            feedbackSnapshot = TodayWatchFeedbackStore.getSnapshot(context)
            creatorSignals = TodayWatchProfileStore.getCreatorSignals(context, limit = 5)
        }
        LaunchedEffect(configSnapshot) {
            uiConfig = configSnapshot
            creatorSignals = TodayWatchProfileStore.getCreatorSignals(context, limit = 5)
        }

        fun commit(next: TodayWatchPluginConfig) {
            updateConfig { next }
        }

        val settingsBackdrop = rememberLayerBackdrop()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(settingsBackdrop)
                    .background(MaterialTheme.colorScheme.background),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            AppText(
                text = "默认模式",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            TodayWatchPluginModeSegmentedControl(
                selectedMode = uiConfig.currentMode,
                onModeChange = { mode -> commit(uiConfig.copy(currentMode = mode)) },
                modifier = Modifier.fillMaxWidth(),
                miuixBackdrop = settingsBackdrop,
            )

            AppText("推荐策略", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RecommendationStrategy.BALANCED to "均衡推荐",
                    RecommendationStrategy.AFFINITY to "兴趣优先",
                    RecommendationStrategy.EXPLORE to "探索优先"
                ).forEach { (strategy, label) ->
                    AppFilterChip(
                        selected = uiConfig.recommendationStrategy == strategy,
                        onClick = { commit(uiConfig.copy(recommendationStrategy = strategy)) },
                        label = { AppText(label) }
                    )
                }
            }
            AppText(
                text = when (uiConfig.recommendationStrategy) {
                    RecommendationStrategy.BALANCED -> "兼顾兴趣、新鲜度与内容多样性"
                    RecommendationStrategy.AFFINITY -> "优先匹配常看 UP 与主题"
                    RecommendationStrategy.EXPLORE -> "增加新 UP、新主题与近期内容"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppText("候选范围", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TodayWatchCandidatePoolMode.LOCAL to "本地候选",
                    TodayWatchCandidatePoolMode.EXPANDED to "扩大候选池"
                ).forEach { (poolMode, label) ->
                    AppFilterChip(
                        selected = uiConfig.candidatePoolMode == poolMode,
                        onClick = { commit(uiConfig.copy(candidatePoolMode = poolMode)) },
                        label = { AppText(label) }
                    )
                }
            }
            AppText(
                text = if (uiConfig.candidatePoolMode == TodayWatchCandidatePoolMode.EXPANDED) {
                    "先显示本地结果，再在后台获取一批候选"
                } else {
                    "仅重排首页已有内容，不产生额外请求"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            AppText(
                text = "推荐规模",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            AppText("UP主榜数量", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 8, 10).forEach { value ->
                    AppFilterChip(
                        selected = uiConfig.upRankLimit == value,
                        onClick = { commit(uiConfig.copy(upRankLimit = value)) },
                        label = { AppText("$value 个") }
                    )
                }
            }

            AppText("队列生成长度", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 20, 30, 40).forEach { value ->
                    AppFilterChip(
                        selected = uiConfig.queueBuildLimit == value,
                        onClick = {
                            val preview = uiConfig.queuePreviewLimit.coerceAtMost(value)
                            commit(uiConfig.copy(queueBuildLimit = value, queuePreviewLimit = preview))
                        },
                        label = { AppText("$value 条") }
                    )
                }
            }

            AppText("卡片展示条数", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 6, 8, 10).forEach { value ->
                    val clamped = value.coerceAtMost(uiConfig.queueBuildLimit)
                    AppFilterChip(
                        selected = uiConfig.queuePreviewLimit == clamped,
                        onClick = { commit(uiConfig.copy(queuePreviewLimit = clamped)) },
                        label = { AppText("$clamped 条") }
                    )
                }
            }

            AppText("历史样本量", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(40, 80, 120).forEach { value ->
                    AppFilterChip(
                        selected = uiConfig.historySampleLimit == value,
                        onClick = { commit(uiConfig.copy(historySampleLimit = value)) },
                        label = { AppText("$value 条") }
                    )
                }
            }

            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            AppSwitchPreference(
                icon = Icons.Outlined.AutoAwesome,
                title = "联动护眼信号",
                subtitle = "夜间优先短时长、低刺激内容",
                checked = uiConfig.linkEyeCareSignal,
                onCheckedChange = { enabled -> commit(uiConfig.copy(linkEyeCareSignal = enabled)) }
            )

            AppSwitchPreference(
                icon = Icons.Outlined.Lightbulb,
                title = "显示模式说明",
                subtitle = "显示“已结合护眼状态”等提示文案",
                checked = uiConfig.showReasonHint,
                onCheckedChange = { enabled -> commit(uiConfig.copy(showReasonHint = enabled)) }
            )

            AppSwitchPreference(
                icon = Icons.Outlined.ViewList,
                title = "显示 UP 主榜",
                subtitle = "在卡片中展示你近期偏好的创作者",
                checked = uiConfig.showUpRank,
                onCheckedChange = { enabled -> commit(uiConfig.copy(showUpRank = enabled)) }
            )

            AppSwitchPreference(
                icon = Icons.Outlined.AutoAwesome,
                title = "瀑布展开动画",
                subtitle = "卡片内容按非线性节奏依次展开",
                checked = uiConfig.enableWaterfallAnimation,
                onCheckedChange = { enabled -> commit(uiConfig.copy(enableWaterfallAnimation = enabled)) }
            )

            if (uiConfig.enableWaterfallAnimation) {
                Spacer(modifier = Modifier.height(4.dp))
                AppText("动画曲率", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        1.2f to "柔和",
                        1.38f to "标准",
                        1.6f to "明显",
                        1.9f to "强烈"
                    ).forEach { (value, label) ->
                        AppFilterChip(
                            selected = kotlin.math.abs(uiConfig.waterfallExponent - value) < 0.01f,
                            onClick = { commit(uiConfig.copy(waterfallExponent = value)) },
                            label = { AppText(label) }
                        )
                    }
                }
            }

            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            TodayWatchTasteInsightSection(insightState)

            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            AppText(
                text = "推荐画像维护",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            AppText(
                text = "会清空本地学习到的偏好与不感兴趣反馈，推荐会回到冷启动状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppTextButton(
                onClick = { showResetDialog = true }
            ) {
                AppText("清空本地推荐画像与反馈")
            }

            if (!resetMessage.isNullOrBlank()) {
                AppText(
                    text = resetMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = "所有设置仅在本地生效，不上传你的历史记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }

        if (showResetDialog) {
            AppAlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { AppText("清空推荐画像") },
                text = { AppText("确定清空本地推荐画像与不感兴趣反馈吗？该操作不可恢复。") },
                confirmButton = {
                    AppTextButton(
                        onClick = {
                            clearPersonalizationData()
                            feedbackSnapshot = TodayWatchFeedbackSnapshot()
                            creatorSignals = emptyList()
                            resetMessage = "已清空，本地推荐将重新学习"
                            showResetDialog = false
                        }
                    ) {
                        AppText("确定")
                    }
                },
                dismissButton = {
                    AppTextButton(onClick = { showResetDialog = false }) {
                        AppText("取消")
                    }
                }
            )
        }
    }

    companion object {
        const val PLUGIN_ID: String = "today_watch"

        fun getInstance(): TodayWatchPlugin? {
            return PluginManager.plugins
                .find { it.plugin.id == PLUGIN_ID }
                ?.plugin as? TodayWatchPlugin
        }
    }
}

@Composable
private fun TodayWatchPluginModeSegmentedControl(
    selectedMode: TodayWatchPluginMode,
    onModeChange: (TodayWatchPluginMode) -> Unit,
    modifier: Modifier = Modifier,
    miuixBackdrop: MiuixBackdrop? = null,
) {
    val modes = TodayWatchPluginMode.entries
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
    val labels = modes.map { mode ->
        if (mode == TodayWatchPluginMode.RELAX) "今晚轻松看" else "深度学习看"
    }
    BottomBarLiquidSegmentedControl(
        items = labels,
        selectedIndex = selectedIndex,
        onSelected = { index ->
            modes.getOrNull(index)?.takeIf { it != selectedMode }?.let(onModeChange)
        },
        modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally),
        itemWidth = 120.dp,
        height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
        indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
        labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
        containerHorizontalPadding = 4.dp,
        containerVerticalPadding = 4.dp,
        miuixBackdrop = miuixBackdrop,
        liquidGlassEffectsEnabled = true,
        tapPressRefractionEnabled = true,
        dragSelectionEnabled = true,
        preferInlineContentStyle = false
    )
}

@Composable
private fun TodayWatchTasteInsightSection(
    state: TodayWatchTasteInsightState
) {
    AppText(
        text = "推荐依据",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    AppText(
        text = "${state.modeTitle}：${state.modeSummary}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (state.preferredCreators.isNotEmpty()) {
        AppText("近期偏好 UP", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.preferredCreators.forEach { signal ->
                AppFilterChip(
                    selected = false,
                    onClick = {},
                    label = { AppText("${signal.label} · ${signal.value}") }
                )
            }
        }
    }

    AppText("最近不感兴趣", style = MaterialTheme.typography.labelLarge)
    if (state.recentDislikedVideos.isEmpty()) {
        AppText(
            text = "还没有本地负反馈。点视频菜单里的“不感兴趣”后，会在这里显示近期样本。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.recentDislikedVideos.forEach { item ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppText(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppText(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (state.negativeSignals.isNotEmpty()) {
        AppText("已降权信号", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.negativeSignals.forEach { signal ->
                AppFilterChip(
                    selected = false,
                    onClick = {},
                    label = { AppText("${signal.label} · ${signal.value}") }
                )
            }
        }
    }
}

private fun RecommendationMode.toTodayWatchMode(): TodayWatchMode {
    return when (this) {
        RecommendationMode.RELAX -> TodayWatchMode.RELAX
        RecommendationMode.LEARN -> TodayWatchMode.LEARN
    }
}

private fun RecommendationCreatorSignal.toTodayWatchCreatorSignal(): TodayWatchCreatorSignal {
    return TodayWatchCreatorSignal(
        mid = mid,
        name = name,
        score = score,
        watchCount = watchCount
    )
}
