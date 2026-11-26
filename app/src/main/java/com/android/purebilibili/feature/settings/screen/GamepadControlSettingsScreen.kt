@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.input.gamepad.GamepadActionScope
import com.android.purebilibili.core.input.gamepad.GamepadActionSlot
import com.android.purebilibili.core.input.gamepad.GamepadConfig
import com.android.purebilibili.core.input.gamepad.GamepadControlEngine
import com.android.purebilibili.core.input.gamepad.GamepadControlMode
import com.android.purebilibili.core.input.gamepad.GamepadControllerProfile
import com.android.purebilibili.core.input.gamepad.GamepadCursorColorStyle
import com.android.purebilibili.core.input.gamepad.GamepadCursorSize
import com.android.purebilibili.core.input.gamepad.GamepadKeyCodes
import com.android.purebilibili.core.input.gamepad.applyCustomBinding
import com.android.purebilibili.core.input.gamepad.clearCustomBinding
import com.android.purebilibili.core.input.gamepad.controllerProfileBadgeLabel
import com.android.purebilibili.core.input.gamepad.controllerProfileDisplayName
import com.android.purebilibili.core.input.gamepad.cursorColorDisplayName
import com.android.purebilibili.core.input.gamepad.cursorSizeDisplayName
import com.android.purebilibili.core.input.gamepad.findConflictingSlot
import com.android.purebilibili.core.input.gamepad.formatGamepadKeyBadge
import com.android.purebilibili.core.input.gamepad.formatGamepadKeyDisplayName
import com.android.purebilibili.core.input.gamepad.formatTriggerPairLabels
import com.android.purebilibili.core.input.gamepad.isSlotCustomized
import com.android.purebilibili.core.input.gamepad.resetCustomBinding
import com.android.purebilibili.core.input.gamepad.resolveBoundKeyCode
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.feature.plugin.GamepadControlPlugin
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 设置独立模块：手柄控制。
 * 支持 Xbox / PlayStation (PS4/PS5) / Nintendo Switch / 通用手柄、隐藏下方提示栏、光标外观与自定义键位。
 */
@Composable
fun GamepadControlSettingsScreen(
    onBack: () -> Unit,
) {
    val contentBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 32.dp,
    )

    EntranceGroup {
        SettingsPageScaffold(
            title = settingsDestinationCopy(SettingsSearchTarget.GAMEPAD_CONTROL).title,
            onBack = onBack,
            backContentDescription = "返回",
            bottomContentPadding = contentBottomPadding,
            lazyListContent = {
                item {
                    Box(
                        modifier = Modifier
                            .entrance()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        GamepadControlSettingsBody(
                            showMasterSwitch = true,
                        )
                    }
                }
            },
        )
    }
}

/**
 * 供「设置 → 手柄控制」与「插件中心 → 手柄控制」共用的配置主体，确保两端设置实时一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamepadControlSettingsBody(
    showMasterSwitch: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiConfig by GamepadControlEngine.configFlow.collectAsStateWithLifecycle()
    val uiState by GamepadControlEngine.uiState.collectAsStateWithLifecycle()
    val plugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle()
    val pluginEnabled = remember(plugins, uiState.pluginEnabled) {
        plugins.firstOrNull { it.plugin.id == BuiltinPluginIds.GAMEPAD_CONTROL }?.enabled
            ?: uiState.pluginEnabled
    }

    var cursorSensitivityDraft by remember(uiConfig.cursorSensitivity) {
        mutableStateOf(uiConfig.cursorSensitivity)
    }
    var scrollSensitivityDraft by remember(uiConfig.scrollSensitivity) {
        mutableStateOf(uiConfig.scrollSensitivity)
    }
    var stickDeadzoneDraft by remember(uiConfig.stickDeadzone) {
        mutableStateOf(uiConfig.stickDeadzone)
    }
    var editingSlot by remember { mutableStateOf<GamepadActionSlot?>(null) }

    LaunchedEffect(Unit) {
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("GamepadControlSettingsScreen")
        GamepadControlPlugin.loadPersistedConfig(context)
    }

    fun update(next: GamepadConfig) {
        GamepadControlPlugin.commitConfig(context, next)
    }

    val effectiveProfile = uiState.effectiveProfile
    val (ltLabel, rtLabel) = remember(effectiveProfile) { formatTriggerPairLabels(effectiveProfile) }

    val gamepadIcon = rememberSettingsSemanticIcon(SettingsIconRole.GAMEPAD_CONTROL)
    val swapIcon = rememberSettingsSemanticIcon(SettingsIconRole.NAV_ICON_CROSS_SCALE)
    val hideHudIcon = rememberSettingsSemanticIcon(SettingsIconRole.SPACE_PRIVACY_HIDDEN)
    val speedIcon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK_SPEED)
    val touchIcon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_GESTURE)
    val scrollSpeedIcon = rememberSettingsSemanticIcon(SettingsIconRole.DISPLAY_SCALE)
    val deadzoneIcon = rememberSettingsSemanticIcon(SettingsIconRole.THEME_LIGHT_CONTROL)
    val invertScrollIcon = rememberSettingsSemanticIcon(SettingsIconRole.FULLSCREEN_GESTURE_REVERSE)
    val playerIcon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYBACK)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // 1. 实时手柄连接状态、型号识别与摇杆/扳机测试仪（独立订阅高频遥测，不触发整页重组）
        GamepadStatusAndTesterCard(
            connected = uiState.connected,
            pluginEnabled = pluginEnabled,
            controllerName = uiState.controllerName,
            vendorId = uiState.controllerVendorId,
            productId = uiState.controllerProductId,
            detectedProfile = uiState.detectedProfile,
            effectiveProfile = effectiveProfile,
            activeMode = uiState.mode,
            gamepadIcon = gamepadIcon,
        )

        // 2. 总开关（在设置模块内直接联动内置插件启停）
        if (showMasterSwitch) {
            Spacer(modifier = Modifier.height(14.dp))
            GamepadSettingsCard {
                SettingsItemAnchor("gamepad.enabled") {
                    AppSwitchPreference(
                        icon = gamepadIcon,
                        title = "启用手柄全局控制",
                        subtitle = if (pluginEnabled) {
                            "连接 Xbox、PlayStation、Switch 或通用手柄后全局操控浏览与视频播放"
                        } else {
                            "已关闭；下方配置会保留，重新开启后立即生效"
                        },
                        checked = pluginEnabled,
                        onCheckedChange = { enabled ->
                            com.android.purebilibili.core.util.AnalyticsHelper.logSettingChange(
                                "gamepad_control",
                                enabled.toString(),
                            )
                            scope.launch {
                                PluginManager.setEnabled(BuiltinPluginIds.GAMEPAD_CONTROL, enabled)
                            }
                        },
                    )
                }
            }
        }

        // 3. 手柄型号与按键方案
        Spacer(modifier = Modifier.height(14.dp))
        AppPreferenceSectionTitle("手柄型号与按键方案")
        GamepadSettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = "按键图标与适配方案",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                AppText(
                    text = if (uiConfig.controllerProfileOverride == GamepadControllerProfile.AUTO) {
                        val detectedText = if (uiState.connected) {
                            "当前自动识别为：${controllerProfileBadgeLabel(uiState.detectedProfile)}"
                        } else {
                            "连接 Xbox / PS4 / PS5 DualSense / Switch / 8BitDo 等手柄时自动匹配"
                        }
                        detectedText
                    } else {
                        "已手动固定为 ${controllerProfileDisplayName(uiConfig.controllerProfileOverride)} 按键风格"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppSurfaceTokens.onSurfaceVariantSummary(),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GamepadControllerProfile.entries.forEach { profile ->
                        AppFilterChip(
                            selected = uiConfig.controllerProfileOverride == profile,
                            onClick = { update(uiConfig.copy(controllerProfileOverride = profile)) },
                            label = {
                                AppText(
                                    text = controllerProfileDisplayName(profile),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                        )
                    }
                }
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            SettingsItemAnchor("gamepad.swap_confirm_back") {
                AppSwitchPreference(
                    icon = swapIcon,
                    title = "互换确认与返回键",
                    subtitle = when (effectiveProfile) {
                        GamepadControllerProfile.PLAYSTATION -> "开启后 ○ 确认、✕ 返回（适配日版/亚洲 PlayStation 习惯）"
                        GamepadControllerProfile.SWITCH -> "手动强制互换确认与返回按键映射（优先于下方自动适配）"
                        else -> "开启后 B 确认、A 返回，贴合个人操作习惯"
                    },
                    checked = uiConfig.swapConfirmBack,
                    enabled = pluginEnabled,
                    onCheckedChange = { update(uiConfig.copy(swapConfirmBack = it)) },
                )
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            SettingsItemAnchor("gamepad.auto_swap_switch") {
                AppSwitchPreference(
                    icon = gamepadIcon,
                    title = "Nintendo Switch 手柄自动适配 A/B",
                    subtitle = "识别到 Switch Pro / Joy-Con 时自动以右侧 A 键确认、下方 B 键返回",
                    checked = uiConfig.autoSwapSwitchConfirmBack,
                    enabled = pluginEnabled,
                    onCheckedChange = { update(uiConfig.copy(autoSwapSwitchConfirmBack = it)) },
                )
            }
        }

        // 4. 提示栏与光标外观
        Spacer(modifier = Modifier.height(14.dp))
        AppPreferenceSectionTitle("提示栏与光标显示")
        GamepadSettingsCard {
            SettingsItemAnchor("gamepad.hide_bottom_key_hints") {
                AppSwitchPreference(
                    icon = hideHudIcon,
                    title = "隐藏底部键位显示",
                    subtitle = if (!uiConfig.showLegendHud) {
                        "已隐藏屏幕底部的手柄键位提示栏，使用中也可按提示栏快捷键随时恢复"
                    } else {
                        "开启后隐藏屏幕底部的手柄键位提示栏，使用中也可按提示栏快捷键随时切换"
                    },
                    checked = !uiConfig.showLegendHud,
                    enabled = pluginEnabled,
                    onCheckedChange = { hide -> update(uiConfig.copy(showLegendHud = !hide)) },
                )
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = "虚拟光标大小",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GamepadCursorSize.entries.forEach { size ->
                        AppFilterChip(
                            selected = uiConfig.cursorSize == size,
                            onClick = { update(uiConfig.copy(cursorSize = size)) },
                            label = {
                                AppText(
                                    text = cursorSizeDisplayName(size),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = "虚拟光标配色",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GamepadCursorColorStyle.entries.forEach { colorStyle ->
                        AppFilterChip(
                            selected = uiConfig.cursorColorStyle == colorStyle,
                            onClick = { update(uiConfig.copy(cursorColorStyle = colorStyle)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(cursorPreviewSwatchColor(colorStyle))
                                        .border(0.6.dp, Color.Gray.copy(alpha = 0.5f), CircleShape),
                                )
                            },
                            label = {
                                AppText(
                                    text = cursorColorDisplayName(colorStyle),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                        )
                    }
                }
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            SettingsItemAnchor("gamepad.hover_highlight") {
                AppSwitchPreference(
                    icon = speedIcon,
                    title = "光标悬停卡片高亮",
                    subtitle = "移动光标经过视频卡片时触发悬浮抬升反馈",
                    checked = uiConfig.hoverHighlightEnabled,
                    enabled = pluginEnabled,
                    onCheckedChange = { update(uiConfig.copy(hoverHighlightEnabled = it)) },
                )
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            SettingsItemAnchor("gamepad.auto_hide_cursor") {
                AppSwitchPreference(
                    icon = touchIcon,
                    title = "触屏与静止自动隐藏光标",
                    subtitle = "手指触屏或 4.5 秒无摇杆操作时淡出光标，不遮挡观看",
                    checked = uiConfig.autoHideCursor,
                    enabled = pluginEnabled,
                    onCheckedChange = {
                        update(uiConfig.copy(autoHideCursor = it, hideCursorOnTouch = it))
                    },
                )
            }
        }

        // 5. 操控模式与摇杆手感
        Spacer(modifier = Modifier.height(14.dp))
        AppPreferenceSectionTitle("操控模式与摇杆手感")
        GamepadSettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = "默认操控模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                AppText(
                    text = "随时可按 ${
                        formatGamepadKeyBadge(
                            resolveBoundKeyCode(uiConfig, GamepadActionSlot.TOGGLE_MODE, effectiveProfile),
                            effectiveProfile,
                        )
                    } 键在两种模式之间一键切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppSurfaceTokens.onSurfaceVariantSummary(),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        GamepadControlMode.CURSOR to "智能光标（推荐）",
                        GamepadControlMode.FOCUS to "焦点导航",
                    ).forEach { (mode, label) ->
                        AppFilterChip(
                            selected = uiConfig.defaultMode == mode,
                            onClick = { update(uiConfig.copy(defaultMode = mode)) },
                            label = { AppText(text = label, maxLines = 1, overflow = TextOverflow.Clip) },
                        )
                    }
                }
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GamepadSliderRow(
                    icon = gamepadIcon,
                    title = "左摇杆光标灵敏度",
                    valueLabel = "${(cursorSensitivityDraft * 100).roundToInt()}%",
                    value = cursorSensitivityDraft,
                    valueRange = 0.5f..2.2f,
                    onValueChange = { cursorSensitivityDraft = it },
                    onValueChangeFinished = { update(uiConfig.copy(cursorSensitivity = cursorSensitivityDraft)) },
                )
                GamepadSliderRow(
                    icon = scrollSpeedIcon,
                    title = "右摇杆滚动速度",
                    valueLabel = "${(scrollSensitivityDraft * 100).roundToInt()}%",
                    value = scrollSensitivityDraft,
                    valueRange = 0.5f..2.2f,
                    onValueChange = { scrollSensitivityDraft = it },
                    onValueChangeFinished = { update(uiConfig.copy(scrollSensitivity = scrollSensitivityDraft)) },
                )
                GamepadSliderRow(
                    icon = deadzoneIcon,
                    title = "摇杆防漂移死区",
                    valueLabel = "${(stickDeadzoneDraft * 100).roundToInt()}%",
                    value = stickDeadzoneDraft,
                    valueRange = 0.08f..0.32f,
                    onValueChange = { stickDeadzoneDraft = it },
                    onValueChangeFinished = { update(uiConfig.copy(stickDeadzone = stickDeadzoneDraft)) },
                )
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            SettingsItemAnchor("gamepad.invert_scroll") {
                AppSwitchPreference(
                    icon = invertScrollIcon,
                    title = "反转右摇杆滚动方向",
                    subtitle = "默认向下推右摇杆向下浏览列表，开启后变为自然拖动方向",
                    checked = uiConfig.invertRightStickScroll,
                    onCheckedChange = { update(uiConfig.copy(invertRightStickScroll = it)) },
                )
            }
        }

        // 6. 视频播放操控
        Spacer(modifier = Modifier.height(14.dp))
        AppPreferenceSectionTitle("视频播放操控")
        GamepadSettingsCard {
            SettingsItemAnchor("gamepad.player_shortcuts") {
                AppSwitchPreference(
                    icon = playerIcon,
                    title = "播放页手柄快捷操控",
                    subtitle = "开启后在视频详情页与全屏支持按住右扳机倍速、肩键快进退、弹幕与全屏/三连快捷操作",
                    checked = uiConfig.playerShortcutsEnabled,
                    onCheckedChange = { update(uiConfig.copy(playerShortcutsEnabled = it)) },
                )
            }
            AppHorizontalDivider(color = AppSurfaceTokens.divider())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = "按住 $rtLabel 倍速档位",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(1.5f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                        AppFilterChip(
                            selected = abs(uiConfig.rtBoostSpeed - speed) < 0.05f,
                            onClick = { update(uiConfig.copy(rtBoostSpeed = speed)) },
                            label = { AppText(text = "${speed}x", maxLines = 1, overflow = TextOverflow.Clip) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = "快进 / 快退步长（按住 $ltLabel + 肩键可切换上下分 P）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15, 30).forEach { seconds ->
                        AppFilterChip(
                            selected = uiConfig.seekStepSeconds == seconds,
                            onClick = { update(uiConfig.copy(seekStepSeconds = seconds)) },
                            label = { AppText(text = "${seconds} 秒", maxLines = 1, overflow = TextOverflow.Clip) },
                        )
                    }
                }
            }
        }

        // 7. 自定义键位（按作用域分组）
        Spacer(modifier = Modifier.height(14.dp))
        AppPreferenceSectionTitle("自定义键位")
        AppText(
            text = "点击任意操作后直接按下手柄实体键即可录制（未连接手柄时也可手动点选按键）；同场景冲突键位将自动互换。",
            style = MaterialTheme.typography.bodySmall,
            color = AppSurfaceTokens.onSurfaceVariantSummary(),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )

        GamepadKeyBindingScopeSection(
            groupTitle = "全局基础按键",
            slots = GamepadActionSlot.entries.filter { it.scope == GamepadActionScope.GLOBAL },
            config = uiConfig,
            effectiveProfile = effectiveProfile,
            onSlotClick = { editingSlot = it },
        )
        Spacer(modifier = Modifier.height(10.dp))
        GamepadKeyBindingScopeSection(
            groupTitle = "浏览导航按键",
            slots = GamepadActionSlot.entries.filter { it.scope == GamepadActionScope.BROWSING },
            config = uiConfig,
            effectiveProfile = effectiveProfile,
            onSlotClick = { editingSlot = it },
        )
        Spacer(modifier = Modifier.height(10.dp))
        GamepadKeyBindingScopeSection(
            groupTitle = "视频播放按键",
            slots = GamepadActionSlot.entries.filter { it.scope == GamepadActionScope.PLAYER },
            config = uiConfig,
            effectiveProfile = effectiveProfile,
            onSlotClick = { editingSlot = it },
        )

        Spacer(modifier = Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiConfig.customBindings.isNotEmpty()) {
                AppOutlinedButton(
                    onClick = { update(uiConfig.copy(customBindings = emptyMap())) },
                ) {
                    AppText("恢复默认键位")
                }
            }
            AppOutlinedButton(
                onClick = { update(GamepadConfig()) },
            ) {
                AppText("重置全部手柄设置")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    editingSlot?.let { slot ->
        GamepadKeyCaptureDialog(
            slot = slot,
            config = uiConfig,
            effectiveProfile = effectiveProfile,
            onDismiss = { editingSlot = null },
            onSave = { keyCode ->
                editingSlot = null
                update(applyCustomBinding(uiConfig, slot, keyCode, effectiveProfile))
            },
            onResetSlot = {
                editingSlot = null
                update(resetCustomBinding(uiConfig, slot))
            },
            onUnbindSlot = {
                editingSlot = null
                update(clearCustomBinding(uiConfig, slot, effectiveProfile))
            },
        )
    }
}

@Composable
private fun GamepadSettingsCard(
    content: @Composable () -> Unit,
) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun GamepadKeyBindingScopeSection(
    groupTitle: String,
    slots: List<GamepadActionSlot>,
    config: GamepadConfig,
    effectiveProfile: GamepadControllerProfile,
    onSlotClick: (GamepadActionSlot) -> Unit,
) {
    GamepadSettingsCard {
        AppText(
            text = groupTitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppContentColors.accent(),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        slots.forEachIndexed { index, slot ->
            val boundCode = resolveBoundKeyCode(config, slot, effectiveProfile)
            val customized = isSlotCustomized(config, slot, effectiveProfile)
            val badge = formatGamepadKeyBadge(boundCode, effectiveProfile)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSlotClick(slot) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = slot.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = slot.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppSurfaceTokens.onSurfaceVariantSummary(),
                    )
                }
                Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (customized) {
                                AppContentColors.accent().copy(alpha = 0.14f)
                            } else {
                                AppSurfaceTokens.surfaceContainer()
                            },
                        )
                        .border(
                            width = 0.8.dp,
                            color = if (customized) {
                                AppContentColors.accent().copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    AppText(
                        text = if (customized) "$badge · 自定义" else badge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (customized) {
                            AppContentColors.accent()
                        } else if (boundCode == GamepadKeyCodes.UNBOUND) {
                            AppContentColors.secondaryText()
                        } else {
                            AppContentColors.primaryText()
                        },
                    )
                }
            }
            if (index != slots.lastIndex) {
                AppHorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamepadKeyCaptureDialog(
    slot: GamepadActionSlot,
    config: GamepadConfig,
    effectiveProfile: GamepadControllerProfile,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onResetSlot: () -> Unit,
    onUnbindSlot: () -> Unit,
) {
    var capturedKeyCode by remember(slot) { mutableStateOf<Int?>(null) }
    val currentKeyCode = resolveBoundKeyCode(config, slot, effectiveProfile)
    val isCustomized = isSlotCustomized(config, slot, effectiveProfile)
    val conflictSlot = capturedKeyCode?.let { findConflictingSlot(config, slot, it, effectiveProfile) }

    DisposableEffect(slot) {
        GamepadControlEngine.startKeyCapture(slot) { keyCode ->
            capturedKeyCode = keyCode
        }
        onDispose {
            GamepadControlEngine.stopKeyCapture()
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("自定义「${slot.title}」") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = "直接按下手柄上的任意实体按键即可录制，或在下方快速选择按键。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(AppShapes.container(ContainerLevel.Field))
                        .background(AppSurfaceTokens.surfaceContainer())
                        .border(
                            width = 1.5.dp,
                            color = AppContentColors.accent(),
                            shape = AppShapes.container(ContainerLevel.Field),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val text = capturedKeyCode?.let {
                        "已捕获：${formatGamepadKeyDisplayName(it, effectiveProfile)}"
                    } ?: "当前：${formatGamepadKeyDisplayName(currentKeyCode, effectiveProfile)}\n等待按下手柄按键…"
                    AppText(
                        text = text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                        textAlign = TextAlign.Center,
                    )
                }

                if (conflictSlot != null) {
                    AppText(
                        text = "该按键已分配给「${conflictSlot.title}」，保存后将自动互换键位。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.accent(),
                    )
                }

                AppText(
                    text = "或手动选择常用按键（${controllerProfileBadgeLabel(effectiveProfile)}）：",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText(),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GamepadKeyCodes.COMMON_BINDABLE_KEYS.forEach { keyCode ->
                        val selected = (capturedKeyCode ?: currentKeyCode) == keyCode
                        AppFilterChip(
                            selected = selected,
                            onClick = { capturedKeyCode = keyCode },
                            label = {
                                AppText(
                                    text = formatGamepadKeyBadge(keyCode, effectiveProfile),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                enabled = capturedKeyCode != null,
                onClick = { capturedKeyCode?.let(onSave) },
            ) {
                AppText("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (slot.allowUnbind && currentKeyCode != GamepadKeyCodes.UNBOUND) {
                    AppTextButton(onClick = onUnbindSlot) {
                        AppText("解绑")
                    }
                }
                if (isCustomized) {
                    AppTextButton(onClick = onResetSlot) {
                        AppText("默认")
                    }
                }
                AppTextButton(onClick = onDismiss) {
                    AppText("取消")
                }
            }
        },
    )
}

@Composable
private fun GamepadStatusAndTesterCard(
    connected: Boolean,
    pluginEnabled: Boolean,
    controllerName: String?,
    vendorId: Int,
    productId: Int,
    detectedProfile: GamepadControllerProfile,
    effectiveProfile: GamepadControllerProfile,
    activeMode: GamepadControlMode,
    gamepadIcon: ImageVector,
) {
    val telemetry by GamepadControlEngine.telemetry.collectAsStateWithLifecycle()
    val accent = AppContentColors.accent()
    val container = if (connected && pluginEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        AppSurfaceTokens.cardContainer()
    }
    val (ltLabel, rtLabel) = remember(effectiveProfile) { formatTriggerPairLabels(effectiveProfile) }

    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.container(ContainerLevel.Card),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(
                    imageVector = gamepadIcon,
                    contentDescription = null,
                    tint = if (connected && pluginEnabled) accent else AppSurfaceTokens.onSurfaceVariantSummary(),
                    modifier = Modifier.size(26.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(
                            text = if (connected) {
                                controllerName ?: "已连接游戏手柄"
                            } else {
                                "未检测到游戏手柄"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (connected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                AppText(
                                    text = controllerProfileBadgeLabel(detectedProfile),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                )
                            }
                        }
                    }
                    val subtitle = if (connected) {
                        val modeLabel = if (activeMode == GamepadControlMode.CURSOR) "智能光标模式" else "焦点导航模式"
                        val idInfo = if (vendorId != 0 || productId != 0) {
                            " · ID ${vendorId.toString(16).uppercase().padStart(4, '0')}:${
                                productId.toString(16).uppercase().padStart(4, '0')
                            }"
                        } else {
                            ""
                        }
                        val statusPrefix = if (pluginEnabled) modeLabel else "已暂停（开关未开启）"
                        "$statusPrefix · 最近输入：${telemetry.lastButtonLabel}$idInfo"
                    } else {
                        "支持蓝牙 / USB 连接 Xbox、PlayStation (PS4/PS5)、Switch 及主流第三方手柄"
                    }
                    AppText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppSurfaceTokens.onSurfaceVariantSummary(),
                    )
                }
            }

            if (connected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StickPreviewDial(label = "左摇杆 (LS)", x = telemetry.leftX, y = telemetry.leftY, accent = accent)
                    TriggerPreviewBars(
                        ltLabel = ltLabel,
                        rtLabel = rtLabel,
                        ltValue = telemetry.leftTrigger,
                        rtValue = telemetry.rightTrigger,
                        accent = accent,
                    )
                    StickPreviewDial(label = "右摇杆 (RS)", x = telemetry.rightX, y = telemetry.rightY, accent = accent)
                }
            }
        }
    }
}

@Composable
private fun TriggerPreviewBars(
    ltLabel: String,
    rtLabel: String,
    ltValue: Float,
    rtValue: Float,
    accent: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(86.dp),
    ) {
        SingleTriggerBar(label = ltLabel, value = ltValue, accent = accent)
        SingleTriggerBar(label = rtLabel, value = rtValue, accent = accent)
    }
}

@Composable
private fun SingleTriggerBar(
    label: String,
    value: Float,
    accent: Color,
) {
    val clamped = value.coerceIn(0f, 1f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AppSurfaceTokens.onSurfaceVariantSummary(),
            )
            AppText(
                text = "${(clamped * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = AppSurfaceTokens.onSurfaceVariantSummary(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.16f)),
        ) {
            if (clamped > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clamped)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun StickPreviewDial(
    label: String,
    x: Float,
    y: Float,
    accent: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val radius = size.minDimension / 2f - 4.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = accent.copy(alpha = 0.25f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            val dotCenter = Offset(
                x = center.x + x * (radius - 6.dp.toPx()),
                y = center.y + y * (radius - 6.dp.toPx()),
            )
            drawCircle(
                color = accent,
                radius = 5.5.dp.toPx(),
                center = dotCenter,
            )
        }
        AppText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppSurfaceTokens.onSurfaceVariantSummary(),
        )
    }
}

@Composable
private fun GamepadSliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = AppContentColors.accent(),
                modifier = Modifier.size(20.dp),
            )
            AppText(title, style = MaterialTheme.typography.bodyLarge)
        }
        AppText(
            text = valueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = AppSurfaceTokens.onSurfaceVariantSummary(),
        )
    }
    AppSlider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun cursorPreviewSwatchColor(style: GamepadCursorColorStyle): Color = when (style) {
    GamepadCursorColorStyle.CLASSIC_WHITE -> Color.White
    GamepadCursorColorStyle.BILI_PINK -> Color(0xFFFB7299)
    GamepadCursorColorStyle.CYAN -> Color(0xFF38BDF8)
    GamepadCursorColorStyle.GOLD -> Color(0xFFFACC15)
}
