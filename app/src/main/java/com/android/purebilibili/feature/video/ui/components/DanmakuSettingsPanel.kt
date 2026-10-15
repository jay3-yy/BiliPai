// File: feature/video/ui/components/DanmakuSettingsPanel.kt
package com.android.purebilibili.feature.video.ui.components

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
import com.android.purebilibili.feature.video.danmaku.DanmakuCloudSyncStatus
import com.android.purebilibili.feature.video.danmaku.DanmakuCloudSyncUiState
import com.android.purebilibili.feature.video.danmaku.DanmakuBlockRuleImportResult
import com.android.purebilibili.feature.video.danmaku.parseDanmakuBlockRuleImport
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuCloudSyncToggleSubtitle
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
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

internal fun resolveDanmakuSyncStatusBadgeText(syncUiState: DanmakuCloudSyncUiState): String {
    return when (syncUiState.status) {
        DanmakuCloudSyncStatus.IDLE -> "未同步"
        DanmakuCloudSyncStatus.PENDING -> "待同步"
        DanmakuCloudSyncStatus.SYNCING -> "同步中"
        DanmakuCloudSyncStatus.SUCCESS -> "已同步"
        DanmakuCloudSyncStatus.FAILURE -> "同步失败"
    }
}

internal fun shouldShowDanmakuSyncRetry(status: DanmakuCloudSyncStatus): Boolean {
    return status == DanmakuCloudSyncStatus.FAILURE
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
enum class DanmakuSliderParameter {
    OPACITY, FONT_SCALE, FONT_WEIGHT, SPEED, DISPLAY_AREA, STROKE_WIDTH, LINE_HEIGHT,
    SCROLL_DURATION, STATIC_DURATION,
}

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
    showSyncSection: Boolean = false,
    cloudSyncEnabled: Boolean = true,
    blockRulesRaw: String = "",
    smartOcclusion: Boolean = true,
    fullscreenWidthMode: DanmakuPanelWidthMode = DanmakuPanelWidthMode.THIRD,
    portraitDisplayAreaMode: PortraitDanmakuDisplayAreaMode =
        PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT,
    syncUiState: DanmakuCloudSyncUiState = DanmakuCloudSyncUiState(),
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
    onCloudSyncEnabledChange: (Boolean) -> Unit = {},
    onSyncNowClick: () -> Unit = {},
    onShowDanmakuPool: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val settingsContext = LocalContext.current
    val settingsScopeCoroutine = rememberCoroutineScope()
    val hotDanmakuEnabled by remember(settingsContext) {
        SettingsManager.getDanmakuHotBarEnabled(settingsContext)
    }.collectAsStateWithLifecycle(initialValue = true)
    var showBlockManager by remember { mutableStateOf(false) }
    val blockManagerSections = remember(blockRulesRaw) {
        resolveDanmakuBlockManagerSections(blockRulesRaw)
    }
    val totalBlockRuleCount = remember(blockManagerSections) {
        resolveDanmakuBlockRuleCount(blockManagerSections)
    }
    val configuration = LocalConfiguration.current
    val colorScheme = MaterialTheme.colorScheme
    val panelColors = rememberDanmakuPanelColors(colorScheme)
    val isFullscreenStyle = isFullscreen
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    val syncShape = if (glass) AppShapes.container(ContainerLevel.Chip) else AppShapes.container(ContainerLevel.Field)
    // 横屏分区 Tab（基础/高级/屏蔽）；竖屏不分区，全部展示。
    var fullscreenActiveSection by remember { mutableIntStateOf(0) }
    val showSectionBasic = !isFullscreenStyle || fullscreenActiveSection == 0
    val showSectionAdvanced = !isFullscreenStyle || fullscreenActiveSection == 1
    val showSectionBlocking = !isFullscreenStyle || fullscreenActiveSection == 2
    val layoutPolicy = remember(
        isFullscreen,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        fullscreenWidthMode
    ) {
        resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = isFullscreen,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            fullscreenWidthMode = fullscreenWidthMode
        )
    }
    DanmakuSettingsPanelHost(
        isFullscreen = isFullscreenStyle,
        layoutPolicy = layoutPolicy,
        onDismiss = onDismiss,
    ) { requestPanelDismiss ->
            AppSurface(
                modifier = Modifier
                    .then(
                        if (
                            isFullscreenStyle &&
                            layoutPolicy.anchor == DanmakuSettingsPanelAnchor.End
                        ) {
                            // 横屏侧栏：固定窄宽，避免 fillMaxWidth 在部分机型上撑满半屏。
                            Modifier.width(layoutPolicy.maxWidthDp.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .widthIn(
                                    min = layoutPolicy.minWidthDp.dp,
                                    max = layoutPolicy.maxWidthDp.dp
                                )
                        }
                    )
                    .padding(
                        start = layoutPolicy.horizontalPaddingDp.dp,
                        end = layoutPolicy.horizontalPaddingDp.dp,
                        bottom = layoutPolicy.bottomPaddingDp.dp
                    )
                    .heightIn(max = layoutPolicy.maxHeightDp.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { },
                color = panelColors.panelColor,
                shape = AppShapes.container(ContainerLevel.Dialog),
                tonalElevation = if (isFullscreenStyle) 6.dp else 16.dp,
                shadowElevation = if (isFullscreenStyle) 8.dp else 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(if (isFullscreenStyle) 20.dp else 24.dp)
                ) {
                    // Header：窄侧栏只放标题+徽章+关闭，长副标题不挤进横排（避免竖字）
                    Row(
                        modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 0),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = "弹幕设置",
                            color = panelColors.titleColor,
                            style = (if (isFullscreenStyle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                                .copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(AppShapes.container(ContainerLevel.Pill))
                                .background(panelColors.badgeBackgroundColor)
                                .border(
                                    width = 1.dp,
                                    color = panelColors.badgeBorderColor,
                                    shape = AppShapes.container(ContainerLevel.Pill)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            AppText(
                                text = settingsScope.badgeLabel,
                                color = panelColors.badgeContentColor,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        AppIconButton(
                            onClick = requestPanelDismiss,
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
                    if (!isFullscreenStyle && settingsScope.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AppText(
                            text = settingsScope.subtitle,
                            color = panelColors.supportingColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isFullscreenStyle) 14.dp else 24.dp))

                    if (isFullscreenStyle) {
                        // 横屏分区 Tab：基础 / 高级 / 屏蔽，避免窄侧栏长滚动。
                        AppThemeAdaptiveTabRow(
                            role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                            options = listOf("基础", "高级", "屏蔽").mapIndexed { index, label ->
                                AppSegmentOption(index, label)
                            },
                            selectedValue = fullscreenActiveSection,
                            onSelectionChange = { fullscreenActiveSection = it },
                            dragSelectionEnabled = true,
                            tapPressRefractionEnabled = true,
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 1),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (showSectionBasic && onShowDanmakuPool != null) {
                        AppSurface(
                            modifier = Modifier
                                .landscapeControlCenterGroup(index = 2)
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onShowDanmakuPool()
                                },
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(
                                        imageVector = Icons.Outlined.FormatListBulleted,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = AppContentColors.accent()
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                     AppText(
                                        text = "查看弹幕列表",
                                        color = panelColors.titleColor,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                                AppText(
                                    text = "搜索 · 筛选 · 跳转",
                                    color = panelColors.supportingColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (showSectionBasic && showSyncSection) {
                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 3),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        AppText(
                                            text = "同步弹幕设置到账号",
                                            color = panelColors.titleColor,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        AppText(
                                            text = resolveDanmakuCloudSyncToggleSubtitle(cloudSyncEnabled),
                                            color = panelColors.supportingColor,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    AppSwitch(
                                        checked = cloudSyncEnabled,
                                        onCheckedChange = onCloudSyncEnabledChange
                                    )
                                }

                                if (cloudSyncEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            AppText(
                                                text = "账号同步状态",
                                                color = panelColors.titleColor,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            AppText(
                                                text = syncUiState.message
                                                    ?: when (syncUiState.status) {
                                                        DanmakuCloudSyncStatus.SUCCESS -> "当前基础弹幕设置已同步到账号"
                                                        DanmakuCloudSyncStatus.SYNCING -> "正在同步当前弹幕设置"
                                                        DanmakuCloudSyncStatus.PENDING -> "检测到设置变更，等待同步"
                                                        DanmakuCloudSyncStatus.FAILURE -> "最近一次同步失败，可立即重试"
                                                        DanmakuCloudSyncStatus.IDLE -> "当前设备本地设置尚未触发同步"
                                                    },
                                                color = panelColors.supportingColor,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .then(if (glass) Modifier.size(width = 100.dp, height = 36.dp) else Modifier)
                                                    .clip(if (glass) syncShape else AppShapes.container(ContainerLevel.Pill))
                                                    .background(panelColors.badgeBackgroundColor)
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AppText(
                                                    text = resolveDanmakuSyncStatusBadgeText(syncUiState),
                                                    color = panelColors.badgeContentColor,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                            }
                                            DanmakuSyncButton(
                                                onClick = onSyncNowClick,
                                                enabled = syncUiState.status != DanmakuCloudSyncStatus.SYNCING,
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                accent = panelColors.sliderActiveTrackColor
                                            ) {
                                                AppText(
                                                    text = if (shouldShowDanmakuSyncRetry(syncUiState.status)) {
                                                        "重试同步"
                                                    } else {
                                                        "立即同步"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Settings Card（基础分区：字体/透明度/速度）
                    if (showSectionBasic) {
                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 4),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    if (isFullscreenStyle) 12.dp else 16.dp
                                ),
                            verticalArrangement = Arrangement.spacedBy(
                                if (isFullscreenStyle) 14.dp else 20.dp
                            )
                        ) {
                            DanmakuSliderItem(
                                label = if (isFullscreenStyle) "字体" else "字体大小",
                                value = fontScale,
                                valueRange = 0.3f..2f,
                                displayValue = { "${(it * 100).toInt()}%" },
                                onValueChange = onFontScaleChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.FONT_SCALE, next) ?: onFontScaleChange(next)
                                    },
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle,
                                resetValue = 1f,
                                tickCount = if (isFullscreenStyle) 10 else 20,
                                valueText = { String.format("%.0f%%", it * 100f) }
                            )
                            if (isFullscreenStyle && showAdvancedSection) {
                                DanmakuSliderItem(
                                    label = "滚动弹幕时长",
                                    value = scrollDurationSeconds,
                                    valueRange = 2f..15f,
                                    displayValue = { "${it.roundToInt()}s" },
                                    onValueChange = onScrollDurationSecondsChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.SCROLL_DURATION, next) ?: onScrollDurationSecondsChange(next)
                                    },
                                    colors = panelColors,
                                    fullscreenStyle = true,
                                    resetValue = 7f,
                                    tickCount = 20,
                                    valueText = { String.format("%.1f 秒", it) }
                                )
                                DanmakuSliderItem(
                                    label = "静态弹幕时长",
                                    value = staticDurationSeconds,
                                    valueRange = 2f..15f,
                                    displayValue = { "${it.roundToInt()}s" },
                                    onValueChange = onStaticDurationSecondsChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.STATIC_DURATION, next) ?: onStaticDurationSecondsChange(next)
                                    },
                                    colors = panelColors,
                                    fullscreenStyle = true,
                                    resetValue = 4f,
                                    tickCount = 20,
                                    valueText = { String.format("%.1f 秒", it) }
                                )
                                DanmakuSliderItem(
                                    label = "弹幕行高",
                                    value = lineHeight,
                                    valueRange = 0.8f..2.2f,
                                    displayValue = { String.format("%.1f", it) },
                                    onValueChange = onLineHeightChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.LINE_HEIGHT, next) ?: onLineHeightChange(next)
                                    },
                                    colors = panelColors,
                                    fullscreenStyle = true,
                                    resetValue = 1.6f,
                                    tickCount = 14
                                )
                            }
                            DanmakuSliderItem(
                                label = "透明度",
                                value = opacity,
                                valueRange = 0.3f..1f,
                                displayValue = { "${(it * 100).toInt()}%" },
                                onValueChange = onOpacityChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.OPACITY, next) ?: onOpacityChange(next)
                                    },
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle,
                                resetValue = 1f,
                                tickCount = 16
                            )
                            DanmakuSliderItem(
                                label = "弹幕速度",
                                value = speed,
                                valueRange = 0.5f..2f,
                                displayValue = { v ->
                                    when {
                                        v >= 1.5f -> "慢"
                                        v <= 0.7f -> "快"
                                        else -> "中"
                                    }
                                },
                                onValueChange = onSpeedChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.SPEED, next) ?: onSpeedChange(next)
                                    },
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle,
                                resetValue = 1f,
                                tickCount = 18
                            )
                        }
                    }
                    } // showSectionBasic

                    if (showSectionAdvanced && showAdvancedSection) {
                        Spacer(modifier = Modifier.height(16.dp))

                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 5),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                AppText(
                                    text = "高级渲染",
                                    color = panelColors.titleColor,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                AppText(
                                    text = "更细的弹幕渲染控制",
                                    color = panelColors.supportingColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                DanmakuSliderItem(
                                    label = "字体粗细",
                                    value = fontWeight.toFloat(),
                                    valueRange = 1f..9f,
                                    steps = 7,
                                    displayValue = { "${it.roundToInt()}" },
                                    onValueChange = { onFontWeightChange(it.roundToInt()) },
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.FONT_WEIGHT, next)
                                            ?: onFontWeightChange(next.roundToInt())
                                    },
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle,
                                    resetValue = 5f,
                                    tickCount = 9
                                )
                                DanmakuSliderItem(
                                    label = "描边粗细",
                                    value = strokeWidth,
                                    valueRange = 0f..4f,
                                    displayValue = { String.format("%.1f dp", it) },
                                    onValueChange = onStrokeWidthChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.STROKE_WIDTH, next) ?: onStrokeWidthChange(next)
                                    },
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle,
                                    resetValue = 1.5f,
                                    tickCount = 12
                                )
                                if (!isFullscreenStyle) {
                                    DanmakuSliderItem(
                                        label = "弹幕行高",
                                        value = lineHeight,
                                        valueRange = 0.8f..2.2f,
                                        displayValue = { String.format("%.1f", it) },
                                        onValueChange = onLineHeightChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.LINE_HEIGHT, next) ?: onLineHeightChange(next)
                                    },
                                        colors = panelColors,
                                        fullscreenStyle = false,
                                        resetValue = 1.6f,
                                        tickCount = 14
                                    )
                                    DanmakuSliderItem(
                                        label = "滚动弹幕时长",
                                        value = scrollDurationSeconds,
                                        valueRange = 2f..15f,
                                        displayValue = { "${it.roundToInt()}s" },
                                        onValueChange = onScrollDurationSecondsChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.SCROLL_DURATION, next) ?: onScrollDurationSecondsChange(next)
                                    },
                                        colors = panelColors,
                                        fullscreenStyle = false,
                                        resetValue = 7f,
                                        tickCount = 20,
                                        valueText = { String.format("%.1f 秒", it) }
                                    )
                                    DanmakuSliderItem(
                                        label = "静态弹幕时长",
                                        value = staticDurationSeconds,
                                        valueRange = 2f..15f,
                                        displayValue = { "${it.roundToInt()}s" },
                                        onValueChange = onStaticDurationSecondsChange,
                                    onValuePreview = { next ->
                                        onSliderPreview?.invoke(DanmakuSliderParameter.STATIC_DURATION, next) ?: onStaticDurationSecondsChange(next)
                                    },
                                        colors = panelColors,
                                        fullscreenStyle = false,
                                        resetValue = 4f,
                                        tickCount = 20,
                                        valueText = { String.format("%.1f 秒", it) }
                                    )
                                }
                                DanmakuFilterSwitchRow(
                                    label = "固定滚动速度",
                                    checked = scrollFixedVelocity,
                                    onCheckedChange = onScrollFixedVelocityChange,
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle
                                )
                                DanmakuFilterSwitchRow(
                                    label = "固定弹幕转滚动",
                                    checked = staticDanmakuToScroll,
                                    onCheckedChange = onStaticDanmakuToScrollChange,
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle
                                )
                                DanmakuFilterSwitchRow(
                                    label = "显示计数弹幕",
                                    checked = hotDanmakuEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsScopeCoroutine.launch {
                                            SettingsManager.setDanmakuHotBarEnabled(settingsContext, enabled)
                                        }
                                    },
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle,
                                )
                                AppText(
                                    text = "根据文字、计数和按钮宽度自动显示 1～3 条完整横幅，均匀排布。普通模式按实际高度避让；海量模式不改变轨道和行数。关闭后普通弹幕照常显示。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = panelColors.supportingColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                DanmakuFilterSwitchRow(
                                    label = "海量弹幕模式",
                                    checked = massiveMode,
                                    onCheckedChange = onMassiveModeChange,
                                    showDivider = false,
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle
                                )
                            }
                        }
                    }

                    if (showSectionBasic) {
                        Spacer(modifier = Modifier.height(16.dp))

                        DanmakuAreaSelector(
                            currentArea = displayArea,
                            onAreaChange = onDisplayAreaChange,
                            colors = panelColors,
                            fullscreenStyle = isFullscreenStyle
                        )

                        if (settingsScope == DanmakuSettingsScope.PORTRAIT) {
                            Spacer(modifier = Modifier.height(12.dp))
                            PortraitDanmakuDisplayAreaModeSelector(
                                currentMode = portraitDisplayAreaMode,
                                onModeChange = onPortraitDisplayAreaModeChange,
                                colors = panelColors
                            )
                        }
                    }

                    if (showSectionAdvanced) {
                        Spacer(modifier = Modifier.height(16.dp))

                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 6),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card),
                            onClick = { onMergeDuplicatesChange(!mergeDuplicates) }
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                AppText(
                                    text = "合并重复弹幕",
                                    color = panelColors.titleColor,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                AppText(
                                    text = "首条锚定窗口，合并并累加点赞",
                                    color = panelColors.supportingColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            AppSwitch(
                                checked = mergeDuplicates,
                                onCheckedChange = onMergeDuplicatesChange,
                            )
                        }
                    }

                    if (mergeDuplicates) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 7),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                DanmakuSliderItem(
                                    label = "合并窗口",
                                    value = duplicateMergeWindowMs.toFloat(),
                                    valueRange = 1000f..15000f,
                                    steps = 139,
                                    displayValue = { "${it.roundToInt()}ms" },
                                    onValueChange = { onDuplicateMergeWindowMsChange(it.roundToInt()) },
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle,
                                    resetValue = 15000f,
                                    tickCount = 6
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                DanmakuSliderItem(
                                    label = "计数阈值",
                                    value = duplicateMergeCountThreshold.toFloat(),
                                    valueRange = 2f..10f,
                                    steps = 7,
                                    displayValue = { "${it.roundToInt()} 条" },
                                    onValueChange = {
                                        onDuplicateMergeCountThresholdChange(it.roundToInt())
                                    },
                                    colors = panelColors,
                                    fullscreenStyle = isFullscreenStyle,
                                    resetValue = 2f,
                                    tickCount = 5
                                )
                            }
                        }
                    }
                    } // showSectionAdvanced

                    if (showSectionAdvanced && showSmartOcclusionSection) {
                        Spacer(modifier = Modifier.height(16.dp))

                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 8),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card),
                            onClick = { onSmartOcclusionChange(!smartOcclusion) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    AppText(
                                        text = "智能避脸遮挡",
                                        color = panelColors.titleColor,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AppText(
                                        text = "实时识别人脸并避让弹幕轨道",
                                        color = panelColors.supportingColor,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                AppSwitch(
                                    checked = smartOcclusion,
                                    onCheckedChange = onSmartOcclusionChange,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (showSectionBlocking) {
                        Spacer(modifier = Modifier.height(16.dp))

                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 9),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                AppText(
                                    text = "屏蔽类型",
                                    color = panelColors.titleColor,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                AppText(
                                    text = "关闭对应开关即可屏蔽",
                                    color = panelColors.supportingColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            Spacer(modifier = Modifier.height(12.dp))

                            DanmakuFilterSwitchRow(
                                label = "滚动弹幕",
                                checked = allowScroll,
                                onCheckedChange = onAllowScrollChange,
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle
                            )
                            DanmakuFilterSwitchRow(
                                label = "顶部弹幕",
                                checked = allowTop,
                                onCheckedChange = onAllowTopChange,
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle
                            )
                            DanmakuFilterSwitchRow(
                                label = "底部弹幕",
                                checked = allowBottom,
                                onCheckedChange = onAllowBottomChange,
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle
                            )
                            DanmakuFilterSwitchRow(
                                label = "彩色弹幕",
                                checked = allowColorful,
                                onCheckedChange = onAllowColorfulChange,
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle
                            )
                            DanmakuFilterSwitchRow(
                                label = "高级弹幕",
                                checked = allowSpecial,
                                onCheckedChange = onAllowSpecialChange,
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle
                            )
                            DanmakuFilterSwitchRow(
                                label = "视频内互动提示",
                                checked = !hideInteractiveCommands,
                                onCheckedChange = { onHideInteractiveCommandsChange(!it) },
                                showDivider = true,
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle
                            )
                            DanmakuSliderItem(
                                label = "智能云屏蔽",
                                value = weightFilterLevel.toFloat(),
                                valueRange = 0f..10f,
                                steps = 9,
                                displayValue = { value ->
                                    val level = value.roundToInt()
                                    if (level == 0) "关闭" else "$level 级"
                                },
                                onValueChange = { onWeightFilterLevelChange(it.roundToInt()) },
                                colors = panelColors,
                                fullscreenStyle = isFullscreenStyle,
                                resetValue = 0f,
                                tickCount = 11
                            )
                        }
                    }
                    } // showSectionBlocking

                    if (showSectionBlocking && showBlockRuleEditor) {
                        Spacer(modifier = Modifier.height(16.dp))

                        AppSurface(
                            modifier = Modifier.fillMaxWidth().landscapeControlCenterGroup(index = 10),
                            color = panelColors.itemColor,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                AppText(
                                    text = "自定义屏蔽词",
                                    color = panelColors.titleColor,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AppOutlinedButton(
                                    onClick = { showBlockManager = true },
                                    shape = AppShapes.container(ContainerLevel.Field),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                         AppText(
                                            text = "屏蔽管理",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                                        )
                                        if (totalBlockRuleCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(AppShapes.container(ContainerLevel.Pill))
                                                    .background(panelColors.badgeBackgroundColor)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                AppText(
                                                    text = resolveDanmakuBlockRuleBadgeText(totalBlockRuleCount),
                                                    color = panelColors.badgeContentColor,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                AppText(
                                    text = if (totalBlockRuleCount > 0) {
                                        "已维护 $totalBlockRuleCount 条规则，修改后立即生效"
                                    } else {
                                        "每行一个，也可粘贴 JSON：keywords / regex / userHashes"
                                    },
                                    color = panelColors.supportingColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                AppOutlinedTextField(
                                    value = blockRulesRaw,
                                    onValueChange = onBlockRulesRawChange,
                                    placeholder = {
                                        AppText(
                                            text = "例如：剧透\\nregex:第\\\\d+集\\n/哈{3,}/",
                                            color = panelColors.supportingColor.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 6,
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
                            }
                        }
                    }
                }
            }
    }

    if (showBlockManager) {
        DanmakuBlockManagerDialog(
            rawRules = blockRulesRaw,
            onRulesSave = { onBlockRulesRawChange(it) },
            onDismiss = { showBlockManager = false }
        )
    }
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
