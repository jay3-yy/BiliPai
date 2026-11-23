// File: feature/video/ui/components/DanmakuSettingsPanel.kt
package com.android.purebilibili.feature.video.ui.components.glass

import com.android.purebilibili.feature.video.ui.components.*

import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.liquidControlThemeColor
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors

import com.android.purebilibili.core.store.DanmakuPanelWidthMode
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode
import com.android.purebilibili.feature.video.danmaku.DanmakuBlockRuleSections
import com.android.purebilibili.feature.video.danmaku.DanmakuBlockRuleImportResult
import com.android.purebilibili.feature.video.danmaku.parseDanmakuBlockRuleImport
import com.android.purebilibili.feature.video.danmaku.mergeDanmakuBlockRuleSections
import com.android.purebilibili.feature.video.danmaku.normalizeDanmakuRegexManagerInput
import com.android.purebilibili.feature.video.danmaku.normalizeDanmakuUserHashManagerInput
import com.android.purebilibili.feature.video.danmaku.parseDanmakuBlockRules
import com.android.purebilibili.feature.video.danmaku.partitionDanmakuBlockRules
import com.android.purebilibili.feature.video.danmaku.DanmakuCloudRuleSyncPolicy
import com.android.purebilibili.data.repository.DanmakuRepository
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppSliderDefaults
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppPredictiveDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 横屏全屏侧栏：分区 Tab 后放宽到 ~45% 屏宽，避免控件拥挤；
 * 过窄（≤220）会导致「横屏专用」旁长文案逐字竖排。
 */
private const val FULLSCREEN_DANMAKU_PANEL_WIDTH_FRACTION = 0.45f
private const val FULLSCREEN_DANMAKU_PANEL_MIN_WIDTH_DP = 380
private const val FULLSCREEN_DANMAKU_PANEL_MAX_WIDTH_DP = 440
private const val WIDE_INLINE_DANMAKU_PANEL_MAX_WIDTH_DP = 640
private const val WIDE_INLINE_DANMAKU_PANEL_SCREEN_WIDTH_DP = 840

enum class DanmakuSettingsPanelAnchor {
    Center,
    End,
    Bottom
}

data class DanmakuSettingsPanelSurfaceColors(
    val panelColor: Color,
    val itemColor: Color,
    val titleColor: Color,
    val supportingColor: Color,
    val dividerColor: Color,
    val badgeBackgroundColor: Color,
    val badgeBorderColor: Color,
    val badgeContentColor: Color,
    val sliderActiveTrackColor: Color,
    val sliderInactiveTrackColor: Color,
    val sliderActiveTickColor: Color,
    val sliderInactiveTickColor: Color,
    val sliderThumbColor: Color,
    val resetButtonColor: Color,
    val resetButtonBackgroundColor: Color,
    val fieldBorderColor: Color,
    val fieldBackgroundColor: Color
)

internal fun resolveDanmakuSettingsPanelSurfaceColors(
    colorScheme: ColorScheme
): DanmakuSettingsPanelSurfaceColors {
    val isDark = colorScheme.surface.luminance() < 0.5f
    val titleColor = colorScheme.onSurface
    val supportingColor = colorScheme.onSurfaceVariant.copy(
        alpha = if (isDark) 0.78f else 0.72f
    )
    val panelColor = if (isDark) {
        colorScheme.surfaceContainerHigh
    } else {
        colorScheme.surface
    }
    val itemColor = if (isDark) {
        colorScheme.surfaceContainer
    } else {
        colorScheme.surfaceContainerLowest
    }
    val badgeColors = resolveAccessibleContainerColors(
        containerColor = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.55f else 0.85f),
        contentColor = if (isDark) colorScheme.primary else colorScheme.onPrimaryContainer,
        backgroundColor = itemColor,
        fallbackContentColors = listOf(colorScheme.onSurface, colorScheme.onBackground),
    )

    // 深色：高对比容器；浅色：纯 surface + 低阴影，避免发灰糊成一团
    return DanmakuSettingsPanelSurfaceColors(
        panelColor = panelColor,
        itemColor = itemColor,
        titleColor = titleColor,
        supportingColor = supportingColor,
        dividerColor = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.55f else 0.7f),
        badgeBackgroundColor = badgeColors.containerColor,
        badgeBorderColor = colorScheme.primary.copy(alpha = if (isDark) 0.45f else 0.28f),
        badgeContentColor = badgeColors.contentColor,
        sliderActiveTrackColor = colorScheme.primary,
        sliderInactiveTrackColor = colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.85f else 1f),
        sliderActiveTickColor = colorScheme.primary.copy(alpha = 0.4f),
        sliderInactiveTickColor = colorScheme.onSurface.copy(alpha = if (isDark) 0.18f else 0.12f),
        sliderThumbColor = colorScheme.primary,
        resetButtonColor = colorScheme.onSurfaceVariant,
        resetButtonBackgroundColor = colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.55f else 0.7f),
        fieldBorderColor = colorScheme.outline.copy(alpha = if (isDark) 0.45f else 0.35f),
        fieldBackgroundColor = if (isDark) {
            colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
        } else {
            colorScheme.surfaceContainerLow
        }
    )
}

internal fun resolveDanmakuBlockManagerSections(blockRulesRaw: String): DanmakuBlockRuleSections {
    return partitionDanmakuBlockRules(parseDanmakuBlockRules(blockRulesRaw))
}

internal fun persistDanmakuBlockManagerSections(sections: DanmakuBlockRuleSections): String {
    return mergeDanmakuBlockRuleSections(
        keywordRules = sections.keywordRules,
        regexRules = sections.regexRules,
        userHashRules = sections.userHashRules
    ).joinToString(separator = "\n")
}

internal fun resolveDanmakuBlockRuleCount(sections: DanmakuBlockRuleSections): Int {
    return sections.keywordRules.size + sections.regexRules.size + sections.userHashRules.size
}

internal fun resolveDanmakuBlockRuleBadgeText(count: Int): String {
    if (count <= 0) return "0"
    return if (count > 99) "99+" else count.toString()
}

internal fun resolveDanmakuBlockManagerTabLabel(label: String, count: Int): String {
    return if (count > 0) "$label $count" else label
}

enum class DanmakuSettingsPanelPresentation {
    CenteredDialog,
    BottomSheet
}

data class DanmakuSettingsPanelLayoutPolicy(
    val presentation: DanmakuSettingsPanelPresentation,
    val anchor: DanmakuSettingsPanelAnchor,
    val horizontalPaddingDp: Int,
    val bottomPaddingDp: Int,
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val maxHeightDp: Int
)

fun resolveDanmakuSettingsPanelLayoutPolicy(
    isFullscreen: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int,
    fullscreenWidthMode: DanmakuPanelWidthMode = DanmakuPanelWidthMode.THIRD
): DanmakuSettingsPanelLayoutPolicy {
    if (isFullscreen) {
        val availableWidthDp = (screenWidthDp - 24).coerceAtLeast(0)
        // 模式保留入参兼容；横屏全屏统一收窄侧栏，不再铺半屏/全宽。
        @Suppress("UNUSED_VARIABLE")
        val ignoredMode = fullscreenWidthMode
        val resolvedWidth = (
            availableWidthDp * FULLSCREEN_DANMAKU_PANEL_WIDTH_FRACTION
            )
            .roundToInt()
            .coerceIn(
                FULLSCREEN_DANMAKU_PANEL_MIN_WIDTH_DP,
                FULLSCREEN_DANMAKU_PANEL_MAX_WIDTH_DP
            )
        return DanmakuSettingsPanelLayoutPolicy(
            presentation = DanmakuSettingsPanelPresentation.CenteredDialog,
            anchor = DanmakuSettingsPanelAnchor.End,
            horizontalPaddingDp = 10,
            bottomPaddingDp = 0,
            minWidthDp = resolvedWidth,
            maxWidthDp = resolvedWidth,
            maxHeightDp = (screenHeightDp - 20).coerceIn(320, 560)
        )
    }

    if (
        screenWidthDp >= WIDE_INLINE_DANMAKU_PANEL_SCREEN_WIDTH_DP &&
        screenWidthDp > screenHeightDp
    ) {
        return DanmakuSettingsPanelLayoutPolicy(
            presentation = DanmakuSettingsPanelPresentation.CenteredDialog,
            anchor = DanmakuSettingsPanelAnchor.Center,
            horizontalPaddingDp = 24,
            bottomPaddingDp = 0,
            minWidthDp = 520,
            maxWidthDp = minOf(
                WIDE_INLINE_DANMAKU_PANEL_MAX_WIDTH_DP,
                (screenWidthDp - 48).coerceAtLeast(520)
            ),
            maxHeightDp = (screenHeightDp - 96).coerceIn(420, 560)
        )
    }

    val horizontalPaddingDp = if (screenWidthDp >= 600) 24 else 16
    val maxHeightDp = (screenHeightDp - 72).coerceIn(420, 560)

    return DanmakuSettingsPanelLayoutPolicy(
        presentation = DanmakuSettingsPanelPresentation.BottomSheet,
        anchor = DanmakuSettingsPanelAnchor.Bottom,
        horizontalPaddingDp = horizontalPaddingDp,
        bottomPaddingDp = 20,
        minWidthDp = 0,
        maxWidthDp = maxOf(520, screenWidthDp - horizontalPaddingDp * 2),
        maxHeightDp = maxHeightDp
    )
}

internal fun shouldDismissDanmakuSettingsPanelFromBackdropGesture(
    maxDragDistancePx: Float,
    touchSlopPx: Float
): Boolean {
    return maxDragDistancePx <= touchSlopPx
}

/**
 * Danmaku Settings Panel
 *
 * A modern, visually appealing panel for configuring danmaku settings:
 * - Opacity
 * - Font scale
 * - Speed
 * - Display area
 *
 * Requirement Reference: AC2.4 - Reusable DanmakuSettingsPanel
 */

fun applyDanmakuSliderPreview(
    manager: com.android.purebilibili.feature.video.danmaku.DanmakuManager,
    parameter: DanmakuSliderParameter,
    value: Float,
) {
    when (parameter) {
        DanmakuSliderParameter.OPACITY -> manager.opacity = value
        DanmakuSliderParameter.FONT_SCALE -> manager.fontScale = value
        DanmakuSliderParameter.FONT_WEIGHT -> manager.fontWeight = value.roundToInt()
        DanmakuSliderParameter.SPEED -> manager.speedFactor = value
        DanmakuSliderParameter.DISPLAY_AREA -> manager.displayArea = value
        DanmakuSliderParameter.STROKE_WIDTH -> manager.strokeWidth = value
        DanmakuSliderParameter.LINE_HEIGHT -> manager.lineHeight = value
        DanmakuSliderParameter.SCROLL_DURATION -> manager.scrollDurationSeconds = value
        DanmakuSliderParameter.STATIC_DURATION -> manager.staticDurationSeconds = value
    }
}

@Composable
fun DanmakuSettingsPanel(
    isFullscreen: Boolean = true,
    settingsScope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT,
    opacity: Float,
    fontScale: Float,
    showAdvancedSection: Boolean = false,
    fontWeight: Int = 5,
    speed: Float,
    timeOffsetMs: Long = 0L,
    onTimeOffsetMsChange: (Long) -> Unit = {},
    onExportDanmaku: ((com.android.purebilibili.feature.video.danmaku.DanmakuExportFormat) -> Unit)? = null,
    displayArea: Float = 0.5f,
    strokeWidth: Float = 1.5f,
    lineHeight: Float = 1.6f,
    scrollDurationSeconds: Float = 7.0f,
    staticDurationSeconds: Float = 4.0f,
    scrollFixedVelocity: Boolean = false,
    staticDanmakuToScroll: Boolean = false,
    massiveMode: Boolean = false,
    mergeDuplicates: Boolean = true,
    duplicateMergeWindowMs: Int = 15_000,
    duplicateMergeCountThreshold: Int = 2,
    allowScroll: Boolean = true,
    allowTop: Boolean = true,
    allowBottom: Boolean = true,
    allowColorful: Boolean = true,
    allowSpecial: Boolean = true,
    weightFilterLevel: Int = 0,
    hideInteractiveCommands: Boolean = false,
    showBlockRuleEditor: Boolean = false,
    showSmartOcclusionSection: Boolean = false,
    blockRulesRaw: String = "",
    smartOcclusion: Boolean = true,
    fullscreenWidthMode: DanmakuPanelWidthMode = DanmakuPanelWidthMode.THIRD,
    portraitDisplayAreaMode: PortraitDanmakuDisplayAreaMode =
        PortraitDanmakuDisplayAreaMode.SCREEN_TOP,
    onSliderPreview: ((DanmakuSliderParameter, Float) -> Unit)? = null,
    onOpacityChange: (Float) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit = {},
    onSpeedChange: (Float) -> Unit,
    onDisplayAreaChange: (Float) -> Unit = {},
    onStrokeWidthChange: (Float) -> Unit = {},
    onLineHeightChange: (Float) -> Unit = {},
    onScrollDurationSecondsChange: (Float) -> Unit = {},
    onStaticDurationSecondsChange: (Float) -> Unit = {},
    onScrollFixedVelocityChange: (Boolean) -> Unit = {},
    onStaticDanmakuToScrollChange: (Boolean) -> Unit = {},
    onMassiveModeChange: (Boolean) -> Unit = {},
    onMergeDuplicatesChange: (Boolean) -> Unit = {},
    onDuplicateMergeWindowMsChange: (Int) -> Unit = {},
    onDuplicateMergeCountThresholdChange: (Int) -> Unit = {},
    onAllowScrollChange: (Boolean) -> Unit = {},
    onAllowTopChange: (Boolean) -> Unit = {},
    onAllowBottomChange: (Boolean) -> Unit = {},
    onAllowColorfulChange: (Boolean) -> Unit = {},
    onAllowSpecialChange: (Boolean) -> Unit = {},
    onWeightFilterLevelChange: (Int) -> Unit = {},
    onHideInteractiveCommandsChange: (Boolean) -> Unit = {},
    onBlockRulesRawChange: (String) -> Unit = {},
    onSmartOcclusionChange: (Boolean) -> Unit = {},
    onFullscreenWidthModeChange: (DanmakuPanelWidthMode) -> Unit = {},
    onPortraitDisplayAreaModeChange: (PortraitDanmakuDisplayAreaMode) -> Unit = {},
    onShowDanmakuPool: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel(
        isFullscreen = isFullscreen,
        settingsScope = settingsScope,
        opacity = opacity,
        fontScale = fontScale,
        showAdvancedSection = showAdvancedSection,
        fontWeight = fontWeight,
        speed = speed,
        timeOffsetMs = timeOffsetMs,
        onTimeOffsetMsChange = onTimeOffsetMsChange,
        onExportDanmaku = onExportDanmaku,
        displayArea = displayArea,
        strokeWidth = strokeWidth,
        lineHeight = lineHeight,
        scrollDurationSeconds = scrollDurationSeconds,
        staticDurationSeconds = staticDurationSeconds,
        scrollFixedVelocity = scrollFixedVelocity,
        staticDanmakuToScroll = staticDanmakuToScroll,
        massiveMode = massiveMode,
        mergeDuplicates = mergeDuplicates,
        duplicateMergeWindowMs = duplicateMergeWindowMs,
        duplicateMergeCountThreshold = duplicateMergeCountThreshold,
        allowScroll = allowScroll,
        allowTop = allowTop,
        allowBottom = allowBottom,
        allowColorful = allowColorful,
        allowSpecial = allowSpecial,
        weightFilterLevel = weightFilterLevel,
        hideInteractiveCommands = hideInteractiveCommands,
        showBlockRuleEditor = showBlockRuleEditor,
        showSmartOcclusionSection = showSmartOcclusionSection,
        blockRulesRaw = blockRulesRaw,
        smartOcclusion = smartOcclusion,
        fullscreenWidthMode = fullscreenWidthMode,
        portraitDisplayAreaMode = portraitDisplayAreaMode,
        onSliderPreview = onSliderPreview,
        onOpacityChange = onOpacityChange,
        onFontScaleChange = onFontScaleChange,
        onFontWeightChange = onFontWeightChange,
        onSpeedChange = onSpeedChange,
        onDisplayAreaChange = onDisplayAreaChange,
        onStrokeWidthChange = onStrokeWidthChange,
        onLineHeightChange = onLineHeightChange,
        onScrollDurationSecondsChange = onScrollDurationSecondsChange,
        onStaticDurationSecondsChange = onStaticDurationSecondsChange,
        onScrollFixedVelocityChange = onScrollFixedVelocityChange,
        onStaticDanmakuToScrollChange = onStaticDanmakuToScrollChange,
        onMassiveModeChange = onMassiveModeChange,
        onMergeDuplicatesChange = onMergeDuplicatesChange,
        onDuplicateMergeWindowMsChange = onDuplicateMergeWindowMsChange,
        onDuplicateMergeCountThresholdChange = onDuplicateMergeCountThresholdChange,
        onAllowScrollChange = onAllowScrollChange,
        onAllowTopChange = onAllowTopChange,
        onAllowBottomChange = onAllowBottomChange,
        onAllowColorfulChange = onAllowColorfulChange,
        onAllowSpecialChange = onAllowSpecialChange,
        onWeightFilterLevelChange = onWeightFilterLevelChange,
        onHideInteractiveCommandsChange = onHideInteractiveCommandsChange,
        onBlockRulesRawChange = onBlockRulesRawChange,
        onSmartOcclusionChange = onSmartOcclusionChange,
        onFullscreenWidthModeChange = onFullscreenWidthModeChange,
        onPortraitDisplayAreaModeChange = onPortraitDisplayAreaModeChange,
        onShowDanmakuPool = onShowDanmakuPool,
        onDismiss = onDismiss,
    )
}

@Composable
private fun DanmakuBlockManagerDialog(
    rawRules: String,
    onRulesSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val panelColors = rememberDanmakuPanelColors(colorScheme)
    val initialSections = remember(rawRules) {
        resolveDanmakuBlockManagerSections(rawRules)
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var keywordRules by remember(rawRules) { mutableStateOf(initialSections.keywordRules) }
    var regexRules by remember(rawRules) { mutableStateOf(initialSections.regexRules) }
    var userHashRules by remember(rawRules) { mutableStateOf(initialSections.userHashRules) }
    var inputValue by remember(selectedTabIndex) { mutableStateOf("") }
    var pendingImportResult by remember { mutableStateOf<DanmakuBlockRuleImportResult?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            pendingImportResult = try {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    } ?: throw IOException("无法读取文件")
                }
                parseDanmakuBlockRuleImport(raw)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DanmakuBlockRuleImportResult(
                    errorMessage = error.message?.takeIf(String::isNotBlank) ?: "文件读取失败"
                )
            }
        }
    }

    var cloudSyncInProgress by remember { mutableStateOf(false) }
    var cloudRules by remember {
        mutableStateOf<List<com.android.purebilibili.data.repository.DanmakuCloudFilterRule>>(emptyList())
    }
    var cloudBaseline by remember { mutableStateOf(initialSections) }

    fun syncCloudRules(manual: Boolean) {
        if (cloudSyncInProgress) return
        scope.launch {
            cloudSyncInProgress = true
            DanmakuRepository.getDanmakuCloudFilterRules()
                .onSuccess { result ->
                    cloudRules = result.rules
                    val mapped = result.rules.mapNotNull { rule ->
                        DanmakuCloudRuleSyncPolicy.cloudRuleToLocalRule(rule.type, rule.filter)
                            ?.let { rule.type to it }
                    }
                    val newKeyword = (keywordRules + mapped.filter { it.first == 0 }.map { it.second }).distinct()
                    val newRegex = (regexRules + mapped.filter { it.first == 1 }.map { it.second }).distinct()
                    val newUid = (userHashRules + mapped.filter { it.first == 2 }.map { it.second }).distinct()
                    keywordRules = newKeyword
                    regexRules = newRegex
                    userHashRules = newUid
                    val baseline = DanmakuBlockRuleSections(newKeyword, newRegex, newUid)
                    cloudBaseline = baseline
                    onRulesSave(persistDanmakuBlockManagerSections(baseline))
                    if (manual) {
                        android.widget.Toast.makeText(
                            context,
                            "已同步云端弹幕屏蔽规则（${mapped.size} 条）",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    result.toast?.takeIf { it.isNotBlank() }?.let {
                        android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { error ->
                    if (manual) {
                        android.widget.Toast.makeText(
                            context,
                            error.message?.takeIf(String::isNotBlank) ?: "云端规则同步失败",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            cloudSyncInProgress = false
        }
    }

    LaunchedEffect(Unit) { syncCloudRules(manual = false) }

    fun updateCurrentRules(transform: (List<String>) -> List<String>) {
        when (selectedTabIndex) {
            0 -> keywordRules = transform(keywordRules)
            1 -> regexRules = transform(regexRules)
            else -> userHashRules = transform(userHashRules)
        }
    }

    val currentRules = when (selectedTabIndex) {
        0 -> keywordRules
        1 -> regexRules
        else -> userHashRules
    }
    val tabCounts = remember(keywordRules, regexRules, userHashRules) {
        listOf(keywordRules.size, regexRules.size, userHashRules.size)
    }
    val currentHint = when (selectedTabIndex) {
        0 -> "例如：剧透"
        1 -> "例如：regex:第\\d+集"
        else -> "例如：uid:abc123 或 abc123"
    }

    pendingImportResult?.let { result ->
        val sections = result.sections
        AppAlertDialog(
            onDismissRequest = { pendingImportResult = null },
            title = { AppText("导入屏蔽规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.errorMessage?.let { AppText(it, color = AppContentColors.error()) }
                    if (result.errorMessage == null) {
                        AppText(
                            "关键词 ${sections.keywordRules.size} 条 · 正则 ${sections.regexRules.size} 条 · UID ${sections.userHashRules.size} 条"
                        )
                        if (result.invalidEntries.isNotEmpty()) {
                            AppText(
                                "无效规则 ${result.invalidEntries.size} 条，将跳过",
                                color = AppContentColors.error()
                            )
                        }
                        if (result.skippedDisabledCount > 0) {
                            AppText("已跳过 ${result.skippedDisabledCount} 条禁用规则")
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    onClick = {
                        keywordRules = (keywordRules + sections.keywordRules).distinct()
                        regexRules = (regexRules + sections.regexRules).distinct()
                        userHashRules = (userHashRules + sections.userHashRules).distinct()
                        pendingImportResult = null
                    },
                    enabled = result.canImport
                ) {
                    AppText("合并导入")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingImportResult = null }) {
                    AppText("取消")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            color = panelColors.panelColor,
            shape = AppShapes.container(ContainerLevel.Floating),
            tonalElevation = 16.dp,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        AppText(
                            text = "屏蔽管理",
                            color = panelColors.titleColor,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AppText(
                            text = "分类维护关键词、正则和 UID(hash) 规则",
                            color = panelColors.supportingColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    AppIconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(panelColors.resetButtonBackgroundColor, CircleShape)
                    ) {
                        AppIcon(
                            Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = panelColors.resetButtonColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AppThemeAdaptiveTabRow(
                    // Glass indicator fills the measured slot; native geometry keeps its defaults.
                    liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                    role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                    options = listOf("关键词", "正则", "UID(hash)").mapIndexed { index, label ->
                        AppSegmentOption(
                            index,
                            resolveDanmakuBlockManagerTabLabel(label, tabCounts[index]),
                        )
                    },
                    selectedValue = selectedTabIndex,
                    onSelectionChange = { selectedTabIndex = it },
                    scrollable = true,
                    minTabWidth = 88.dp,
                    dragSelectionEnabled = true,
                    tapPressRefractionEnabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                AppOutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    placeholder = {
                        AppText(
                            text = currentHint,
                            color = panelColors.supportingColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = panelColors.titleColor,
                        unfocusedTextColor = panelColors.titleColor,
                        focusedBorderColor = AppContentColors.accent(),
                        unfocusedBorderColor = panelColors.fieldBorderColor,
                        focusedContainerColor = panelColors.fieldBackgroundColor,
                        unfocusedContainerColor = panelColors.fieldBackgroundColor
                    ),
                    shape = AppShapes.container(ContainerLevel.Card)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppOutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("text/plain", "application/json", "text/xml", "application/xml")
                            )
                        }
                    ) {
                        AppText("导入文件")
                    }
                    AppOutlinedButton(
                        onClick = { syncCloudRules(manual = true) },
                        enabled = !cloudSyncInProgress
                    ) {
                        AppText(if (cloudSyncInProgress) "同步中…" else "同步云端")
                    }
                    AppButton(
                        onClick = {
                            val candidate = inputValue.trim()
                            if (candidate.isEmpty()) return@AppButton
                            val normalizedCandidate = when (selectedTabIndex) {
                                1 -> normalizeDanmakuRegexManagerInput(candidate) ?: candidate
                                2 -> normalizeDanmakuUserHashManagerInput(candidate) ?: candidate
                                else -> candidate
                            }
                            updateCurrentRules { (it + normalizedCandidate).distinct() }
                            inputValue = ""
                        },
                        enabled = inputValue.isNotBlank()
                    ) {
                        AppText("添加")
                    }
                }

                AppSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = panelColors.itemColor,
                    shape = AppShapes.container(ContainerLevel.Card)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (currentRules.isEmpty()) {
                            AppText(
                                text = "当前分类还没有规则",
                                color = panelColors.supportingColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            currentRules.forEachIndexed { index, rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppText(
                                        text = rule,
                                        color = panelColors.titleColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppTextButton(
                                        onClick = {
                                            updateCurrentRules { rules ->
                                                rules.filterIndexed { currentIndex, _ ->
                                                    currentIndex != index
                                                }
                                            }
                                        }
                                    ) {
                                        AppText("删除")
                                    }
                                }
                                if (index != currentRules.lastIndex) {
                                    AppHorizontalDivider(color = panelColors.dividerColor)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppTextButton(onClick = onDismiss) {
                        AppText("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AppButton(
                        onClick = {
                            val finalSections = DanmakuBlockRuleSections(
                                keywordRules = keywordRules,
                                regexRules = regexRules,
                                userHashRules = userHashRules
                            )
                            val cloudAdds = DanmakuCloudRuleSyncPolicy.resolveCloudRuleAdds(
                                baseline = cloudBaseline,
                                current = finalSections
                            )
                            val cloudDeletes = DanmakuCloudRuleSyncPolicy.resolveCloudRuleDeletes(
                                cloudRules = cloudRules,
                                current = finalSections
                            )
                            if (cloudAdds.isNotEmpty() || cloudDeletes.isNotEmpty()) {
                                scope.launch {
                                    cloudAdds.forEach { add ->
                                        DanmakuRepository.addDanmakuCloudFilterRule(add.type, add.filter)
                                            .onSuccess { cloudRules = cloudRules + it }
                                    }
                                    cloudDeletes.forEach { id ->
                                        DanmakuRepository.deleteDanmakuCloudFilterRule(id)
                                            .onSuccess {
                                                cloudRules = cloudRules.filterNot { rule -> rule.id == id }
                                            }
                                    }
                                }
                            }
                            onRulesSave(persistDanmakuBlockManagerSections(finalSections))
                            onDismiss()
                        }
                    ) {
                        AppText("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun DanmakuFilterSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: DanmakuSettingsPanelSurfaceColors,
    fullscreenStyle: Boolean,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = if (fullscreenStyle) 10.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = label,
                color = colors.titleColor,
                style = if (fullscreenStyle) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
            )
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
        if (showDivider) {
            AppHorizontalDivider(color = colors.dividerColor)
        }
    }
}

/**
 * 竖屏弹幕显示区域选择器
 */
@Composable
private fun PortraitDanmakuDisplayAreaModeSelector(
    currentMode: PortraitDanmakuDisplayAreaMode,
    onModeChange: (PortraitDanmakuDisplayAreaMode) -> Unit,
    colors: DanmakuSettingsPanelSurfaceColors
) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.itemColor,
        shape = AppShapes.container(ContainerLevel.Card)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = "竖屏弹幕显示区域",
                color = colors.titleColor,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PortraitDanmakuDisplayAreaMode.entries.forEach { mode ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppShapes.container(ContainerLevel.Card))
                            .then(
                                if (isSelected) {
                                    Modifier.background(AppContentColors.accent())
                                } else {
                                    Modifier
                                        .background(colors.fieldBackgroundColor)
                                        .border(
                                            1.dp,
                                            colors.fieldBorderColor,
                                            AppShapes.container(ContainerLevel.Card)
                                        )
                                }
                            )
                            .clickable { onModeChange(mode) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(
                            text = mode.label,
                            color = if (isSelected) {
                                AppContentColors.onAccent()
                            } else {
                                colors.titleColor
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 弹幕显示区域选择器
 */
@Composable
private fun DanmakuAreaSelector(
    currentArea: Float,
    onAreaChange: (Float) -> Unit,
    colors: DanmakuSettingsPanelSurfaceColors,
    fullscreenStyle: Boolean
) {
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    val optionShape = AppShapes.container(if (glass) ContainerLevel.Chip else ContainerLevel.Card)
    val accent = if (glass) liquidControlThemeColor() else AppContentColors.accent()
    val onAccent = if (glass) resolveAccessibleContainerColors(
        containerColor = accent,
        contentColor = AppContentColors.onAccent(),
        backgroundColor = colors.itemColor,
        fallbackContentColors = listOf(Color.White, Color.Black),
    ).contentColor else AppContentColors.onAccent()
    //  本地状态确保即时 UI 响应
    var localArea by remember(currentArea) { mutableFloatStateOf(currentArea) }

    data class AreaOption(val value: Float, val label: String, val subLabel: String)

    val areaOptions = listOf(
        AreaOption(0.125f, "1/8", "窄带"),
        AreaOption(0.25f, "1/4", "顶部"),
        AreaOption(0.375f, "3/8", "小半"),
        AreaOption(0.5f, "1/2", "半屏"),
        AreaOption(0.625f, "5/8", "过半"),
        AreaOption(0.75f, "3/4", "大部"),
        AreaOption(0.875f, "7/8", "近全"),
        AreaOption(1.0f, "全屏", "铺满")
    )

    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.itemColor,
        shape = AppShapes.container(ContainerLevel.Card)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            AppText(
                text = "显示区域",
                color = colors.titleColor,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )

            Spacer(modifier = Modifier.height(12.dp))

            areaOptions.chunked(4).forEachIndexed { rowIndex, rowOptions ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowOptions.forEach { option ->
                        //  使用本地状态判断选中状态
                        val isSelected = kotlin.math.abs(localArea - option.value) < 0.001f

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(if (glass) Modifier.height(80.dp) else Modifier)
                                .clip(optionShape)
                                .then(
                                    if (isSelected) {
                                        Modifier.background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    accent,
                                                    if (glass) accent else accent.copy(alpha = 0.8f)
                                                )
                                            )
                                        )
                                    } else {
                                        Modifier
                                            .background(colors.fieldBackgroundColor)
                                            .border(1.dp, colors.fieldBorderColor, optionShape)
                                    }
                                )
                                .clickable {
                                    localArea = option.value  //  即时更新 UI
                                    onAreaChange(option.value)
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppText(
                                    text = option.label,
                                    color = if (isSelected) {
                                        onAccent
                                    } else {
                                        colors.titleColor
                                    },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                AppText(
                                    text = option.subLabel,
                                    color = if (isSelected) {
                                        onAccent.copy(alpha = 0.8f)
                                    } else {
                                        colors.supportingColor
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DanmakuSliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    displayValue: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onValuePreview: ((Float) -> Unit)? = null,
    colors: DanmakuSettingsPanelSurfaceColors,
    fullscreenStyle: Boolean,
    resetValue: Float? = null,
    tickCount: Int = 0,
    valueText: (Float) -> String = displayValue
) {
    var localValue by remember(valueRange) { mutableFloatStateOf(value) }
    var previewing by remember { mutableStateOf(false) }
    val latestCommit by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    androidx.compose.runtime.LaunchedEffect(value) {
        if (!previewing) localValue = value
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (previewing) latestCommit(localValue) }
    }
    val liquidSlider = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
        android.os.Build.VERSION.SDK_INT >= 33
    val sliderProgress = remember(localValue, valueRange) {
        val total = valueRange.endInclusive - valueRange.start
        if (total <= 0f) {
            0f
        } else {
            ((localValue - valueRange.start) / total).coerceIn(0f, 1f)
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fullscreenStyle) {
                AppText(
                    text = "$label ${valueText(localValue)}",
                    color = colors.titleColor,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                if (resetValue != null) {
                    AppIconButton(
                        onClick = {
                            localValue = resetValue
                            previewing = false
                            onValueChange(resetValue)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(colors.resetButtonBackgroundColor, CircleShape)
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "重置",
                            tint = colors.resetButtonColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                AppText(
                    text = label,
                    color = colors.titleColor,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                AppSurface(
                    color = colors.itemColor,
                    shape = AppShapes.container(ContainerLevel.Pill)
                ) {
                    AppText(
                        text = displayValue(localValue),
                        color = colors.sliderActiveTrackColor,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (fullscreenStyle) 10.dp else 12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (liquidSlider) 48.dp else if (fullscreenStyle) 32.dp else 28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!liquidSlider && fullscreenStyle && tickCount > 1) {
                DanmakuSliderTicks(
                    tickCount = tickCount,
                    progress = sliderProgress,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                )
            }

            AppSlider(
                value = localValue,
                onValueChange = { newValue ->
                    localValue = newValue
                    previewing = true
                    onValuePreview?.invoke(newValue)
                },
                onValueChangeFinished = {
                    previewing = false
                    onValueChange(localValue)
                },
                valueRange = valueRange,
                steps = steps,
                // Glass uses the exact custom accent while retaining the shared lens geometry.
                // Native themes retain the panel's existing contrast palette.
                colors = AppSliderDefaults.colors(
                    thumbColor = colors.sliderThumbColor,
                    activeTrackColor = colors.sliderActiveTrackColor,
                    inactiveTrackColor = colors.sliderInactiveTrackColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DanmakuSliderTicks(
    tickCount: Int,
    progress: Float,
    colors: DanmakuSettingsPanelSurfaceColors,
    modifier: Modifier = Modifier
) {
    val activeTickIndex = ((tickCount - 1) * progress).roundToInt()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(tickCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == activeTickIndex) 8.dp else 6.dp)
                    .background(
                        color = if (index <= activeTickIndex) {
                            colors.sliderActiveTickColor
                        } else {
                            colors.sliderInactiveTickColor
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}
