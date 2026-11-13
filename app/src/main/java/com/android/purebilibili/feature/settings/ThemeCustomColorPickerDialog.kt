@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.android.purebilibili.feature.settings

import android.os.SystemClock
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import kotlin.math.roundToInt
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun ThemeCustomColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    roleTarget: ThemeRoleColorTarget? = null,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    colorContrast: Contrast = Contrast.Default,
    seedPaletteStyle: SeedPaletteStyle = SeedPaletteStyle.Standard,
    previewConfiguration: ThemePreviewConfiguration? = null,
    themeSeed: Color? = null,
    exactRoleTitle: String? = null,
    onReset: (() -> Unit)? = null,
) {
    val initialHsv = remember(initialHex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(parseMd3CustomColorHex(initialHex).toArgb(), it) }
    }
    var hue by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsv[2]) }
    var pendingHex by rememberSaveable(initialHex) { mutableStateOf(normalizeMd3CustomColorHex(initialHex)) }
    var previewChecked by rememberSaveable { mutableStateOf(true) }
    var previewValue by rememberSaveable { mutableFloatStateOf(0.65f) }
    var lastHapticAtMs by remember { mutableLongStateOf(0L) }
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT)
    val color = Color.hsv(hue, saturation, brightness)
    val colorHex = formatMd3CustomColorHex(color)
    val roleTargetLabel = roleTarget?.let { stringResource(it.labelRes) }
    val validHex = isValidMd3CustomColorHex(pendingHex)
    val foreground = resolveReadableForeground(color)
    val previewDark = when {
        roleTarget?.name?.startsWith("LIGHT_") == true -> false
        roleTarget?.name?.startsWith("DARK_") == true -> true
        else -> AppSurfaceTokens.background().luminance() < 0.5f
    }
    val hostConfig = LocalAppThemeConfig.current
    val previewUiStyle = LocalAppUiStyle.current
    val configuration = (previewConfiguration ?: ThemePreviewConfiguration(
        uiStyle = previewUiStyle, liquidGlassEnabled = hostConfig.liquidGlassEnabled,
    )).let { current ->
        current.copy(
            source = Md3ColorSource.CUSTOM,
            roleOverrides = if (roleTarget != null) roleTarget.write(current.roleOverrides, colorHex) else current.roleOverrides,
            syncControlAccentToSeed = roleTarget == null,
        )
    }
    val previewSeed = if (roleTarget == null) color else themeSeed ?: color
    val previewPalette = remember(previewSeed, previewDark, paletteStyle, colorSpec, colorContrast, seedPaletteStyle, configuration) {
        resolveThemePreviewPalette(previewSeed, previewDark, paletteStyle, colorSpec, colorContrast, seedPaletteStyle, configuration)
    }
    val previewScheme = previewPalette.material
    val previewIssues = remember(previewScheme) { assessThemeContrast(previewScheme).filterNot { it.passes } }
    val previewMiuixController = remember(previewPalette, previewDark) {
        ThemeController(
            colorSchemeMode = resolveMiuixColorSchemeMode(
                if (previewDark) AppThemeMode.DARK else AppThemeMode.LIGHT,
                dynamicColorEnabled = false,
            ),
            lightColors = previewPalette.miuix,
            darkColors = previewPalette.miuix,
            isDark = previewDark,
        )
    }
    val previewConfig = hostConfig.copy(liquidControlsUseThemeColor = true)
    val previewUsesLiquidControls = previewConfig.liquidGlassEnabled && Build.VERSION.SDK_INT >= 33
    val previewTrackColor = if (previewUsesLiquidControls) previewPalette.liquidAccent else previewScheme.primary
    val controlColorPreview = roleTarget == null || roleTarget == ThemeRoleColorTarget.LIGHT_CONTROL ||
        roleTarget == ThemeRoleColorTarget.DARK_CONTROL
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    fun updateHsv(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        saturation = s.coerceIn(0f, 1f)
        brightness = v.coerceIn(0f, 1f)
        pendingHex = formatMd3CustomColorHex(Color.hsv(hue, saturation, brightness))
        val now = SystemClock.elapsedRealtime()
        if (shouldEmitMd3ColorPickerSelectionHaptic(lastHapticAtMs, now)) {
            haptic(HapticType.SELECTION)
            lastHapticAtMs = now
        }
    }
    fun updateHex(input: String) {
        pendingHex = input.trim().uppercase().take(7)
        if (isValidMd3CustomColorHex(pendingHex)) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(parseMd3CustomColorHex(pendingHex).toArgb(), hsv)
            // Gray/black do not define a hue. Preserve it so the next drag remains predictable.
            if (hsv[1] > 0f) hue = hsv[0]
            if (hsv[2] > 0f) saturation = hsv[1]
            brightness = hsv[2]
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AppSurface(
            modifier = Modifier.padding(horizontal = 16.dp).imePadding().widthIn(max = 520.dp)
                .fillMaxWidth().heightIn(max = maxHeight),
            shape = AppShapes.container(ContainerLevel.Dialog),
            color = AppSurfaceTokens.surface(),
        ) {
            Column {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    AppText(exactRoleTitle ?: roleTargetLabel ?: "自定义主题颜色", style = MaterialTheme.typography.titleLarge)
                    AppText("先预览，再保存", style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(), modifier = Modifier.padding(top = 4.dp))
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Card),
                        color = color.copy(alpha = 0.10f),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).background(color, CircleShape)
                                    .border(1.dp, AppSurfaceTokens.divider(), CircleShape))
                                Column(Modifier.weight(1f)) {
                                    AppText(colorHex, style = MaterialTheme.typography.titleMedium)
                                    AppText(if (exactRoleTitle != null) "角色颜色（精确值）" else if (controlColorPreview) "输入颜色（精确值）" else roleTargetLabel.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText())
                                }
                            }
                            if (exactRoleTitle != null) {
                                AppSurface(color = color, contentColor = foreground,
                                    shape = AppShapes.container(ContainerLevel.Field)) {
                                    AppText("Aa 示例文字", color = foreground,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp))
                                }
                                AppText("按所选色值应用，配套文字自动匹配明暗。",
                                    style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                            } else if (controlColorPreview) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(28.dp).background(previewTrackColor, CircleShape)
                                        .border(1.dp, AppSurfaceTokens.divider(), CircleShape))
                                    Column(Modifier.weight(1f)) {
                                        AppText(formatMd3CustomColorHex(previewTrackColor), style = MaterialTheme.typography.titleSmall)
                                        AppText(
                                            if (previewUsesLiquidControls) "液态控件 · 跟随主题色时的轨道颜色"
                                            else if (previewDark) "深色主题 · 实际轨道颜色" else "浅色主题 · 实际轨道颜色",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.secondaryText(),
                                        )
                                    }
                                }
                                AppText(
                                    if (previewUsesLiquidControls) "液态控件跟随主题色时使用输入颜色。"
                                    else "主色使用输入色值；容器保留主题层级，配套文字自动匹配明暗。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                                CompositionLocalProvider(
                                    LocalLiquidControlThemeColor provides previewPalette.liquidAccent,
                                    LocalAppThemeConfig provides previewConfig,
                                ) {
                                    MiuixTheme(controller = previewMiuixController) {
                                        MaterialTheme(colorScheme = previewScheme) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                AppSwitch(checked = previewChecked, onCheckedChange = { previewChecked = it },
                                                    modifier = Modifier.semantics { contentDescription = "预览开关" })
                                                AppSlider(value = previewValue, onValueChange = { previewValue = it },
                                                    modifier = Modifier.weight(1f).semantics { contentDescription = "预览滑块" })
                                            }
                                        }
                                    }
                                }
                            } else {
                                AppSurface(
                                    color = previewScheme.surface,
                                    shape = AppShapes.container(ContainerLevel.Field),
                                ) {
                                    androidx.compose.material3.Text("Aa 示例文字", color = if (
                                        roleTarget == ThemeRoleColorTarget.LIGHT_SECONDARY_TEXT ||
                                        roleTarget == ThemeRoleColorTarget.DARK_SECONDARY_TEXT
                                    ) previewScheme.onSurfaceVariant else previewScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    if (exactRoleTitle == null) {
                        AppText("预览已应用当前高级覆盖色、表面与文字可读性修正。",
                            style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                        // 调色时警告数量与换行会变化；固定信息区域避免拖动中的选色板移位。
                        Box(Modifier.fillMaxWidth().height(72.dp).verticalScroll(rememberScrollState())) {
                            AppText(
                                if (previewIssues.isEmpty()) "当前配色的对比度达到目标。" else
                                    "最终配色有 ${previewIssues.size} 组配对低于目标：" +
                                        previewIssues.take(3).joinToString("；") { "${it.label} %.2f:1（目标 %.1f:1）".format(it.ratio, it.required) },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (previewIssues.isEmpty()) AppContentColors.secondaryText() else AppContentColors.error(),
                            )
                        }
                    }
                    ThemeColorSelectionControls(
                        hue = hue, saturation = saturation, brightness = brightness,
                        pendingHex = pendingHex, onHsvChange = ::updateHsv, onHexChange = ::updateHex,
                    )
                }
                AppHorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                if (onReset != null) {
                    AppTextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                        AppText("恢复此项壁纸配色")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { AppText("取消") }
                    AppButton(
                        onClick = { haptic(HapticType.LIGHT); onConfirm(normalizeMd3CustomColorHex(pendingHex)) },
                        enabled = validHex, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = foreground),
                    ) { AppText("保存并应用") }
                }
            }
        }
    }
}

@Composable
private fun ThemeSaturationValuePanel(hue: Float, saturation: Float, brightness: Float, onChange: (Float, Float) -> Unit) {
    val latestChange by rememberUpdatedState(onChange)
    var focused by remember { mutableStateOf(false) }
    val focusColor = AppContentColors.accent()
    Canvas(
        Modifier.fillMaxWidth().height(164.dp).clip(AppShapes.container(ContainerLevel.Card))
            .then(if (focused) Modifier.border(2.dp, focusColor, AppShapes.container(ContainerLevel.Card)) else Modifier)
            .pointerInput(Unit) {
                fun select(point: Offset) { latestChange((point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    1f - (point.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                detectTapGestures(onTap = ::select)
            }
            .pointerInput(Unit) {
                fun select(point: Offset) { latestChange((point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    1f - (point.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                detectDragGestures(onDragStart = ::select, onDrag = { change, _ -> change.consume(); select(change.position) })
            }
            .semantics {
                contentDescription = "饱和度与亮度选色板"
                stateDescription = "饱和度 ${(saturation * 100).roundToInt()}%，亮度 ${(brightness * 100).roundToInt()}%"
                customActions = listOf(
                    CustomAccessibilityAction("增加饱和度") { latestChange((saturation + 0.05f).coerceAtMost(1f), brightness); true },
                    CustomAccessibilityAction("降低饱和度") { latestChange((saturation - 0.05f).coerceAtLeast(0f), brightness); true },
                    CustomAccessibilityAction("增加亮度") { latestChange(saturation, (brightness + 0.05f).coerceAtMost(1f)); true },
                    CustomAccessibilityAction("降低亮度") { latestChange(saturation, (brightness - 0.05f).coerceAtLeast(0f)); true },
                )
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> { latestChange((saturation - 0.02f).coerceAtLeast(0f), brightness); true }
                    Key.DirectionRight -> { latestChange((saturation + 0.02f).coerceAtMost(1f), brightness); true }
                    Key.DirectionUp -> { latestChange(saturation, (brightness + 0.02f).coerceAtMost(1f)); true }
                    Key.DirectionDown -> { latestChange(saturation, (brightness - 0.02f).coerceAtLeast(0f)); true }
                    else -> false
                }
            }.onFocusChanged { focused = it.isFocused }.focusable(),
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val radius = 9.dp.toPx()
        val center = Offset((saturation * size.width).coerceIn(radius + 2, size.width - radius - 2),
            ((1f - brightness) * size.height).coerceIn(radius + 2, size.height - radius - 2))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius + 2.dp.toPx(), center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun ThemeHueStrip(hue: Float, onChange: (Float) -> Unit) {
    val latestChange by rememberUpdatedState(onChange)
    val layout = resolveMd3ColorPickerSliderLayout()
    var focused by remember { mutableStateOf(false) }
    val focusColor = AppContentColors.accent()
    Canvas(
        Modifier.fillMaxWidth().height(layout.frameHeight)
            .then(if (focused) Modifier.border(2.dp, focusColor, AppShapes.container(ContainerLevel.Field)) else Modifier)
            .pointerInput(Unit) {
                val inset = layout.horizontalPadding.toPx()
                fun select(point: Offset) { latestChange(((point.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f) * 360f) }
                detectTapGestures(onTap = ::select)
            }
            .pointerInput(Unit) {
                val inset = layout.horizontalPadding.toPx()
                fun select(point: Offset) { latestChange(((point.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f) * 360f) }
                detectDragGestures(onDragStart = ::select, onDrag = { change, _ -> change.consume(); select(change.position) })
            }
            .semantics {
                contentDescription = "色相"
                progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..360f)
                setProgress { latestChange(it.coerceIn(0f, 360f)); true }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> { latestChange((hue - 3f).coerceAtLeast(0f)); true }
                    Key.DirectionRight -> { latestChange((hue + 3f).coerceAtMost(360f)); true }
                    else -> false
                }
            }.onFocusChanged { focused = it.isFocused }.focusable(),
    ) {
        val inset = layout.horizontalPadding.toPx()
        val trackHeight = layout.trackHeight.toPx()
        val trackWidth = (size.width - 2 * inset).coerceAtLeast(1f)
        drawRoundRect(
            Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }, startX = inset, endX = inset + trackWidth),
            topLeft = Offset(inset, (size.height - trackHeight) / 2),
            size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
        )
        val center = Offset(inset + trackWidth * (hue / 360f), size.height / 2)
        drawCircle(Color.White, layout.thumbRadius.toPx(), center)
        drawCircle(Color.hsv(hue, 1f, 1f), 8.dp.toPx(), center)
        drawCircle(Color.Black.copy(alpha = 0.15f), layout.thumbRadius.toPx(), center, style = Stroke(1.dp.toPx()))
    }
}

/** 主题与图标共用的二维取色、色相、HEX 和常用颜色控件。 */
@Composable
internal fun ThemeColorSelectionControls(
    hue: Float,
    saturation: Float,
    brightness: Float,
    pendingHex: String,
    onHsvChange: (Float, Float, Float) -> Unit,
    onHexChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllColors by rememberSaveable { mutableStateOf(false) }
    val validHex = isValidMd3CustomColorHex(pendingHex)
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("拖动选色", style = MaterialTheme.typography.labelLarge)
            AppText("饱和 ${(saturation * 100).roundToInt()}% · 明亮 ${(brightness * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
        ThemeSaturationValuePanel(hue, saturation, brightness) { s, v -> onHsvChange(hue, s, v) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("色相", style = MaterialTheme.typography.labelLarge)
            AppText("${hue.roundToInt()}°", style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText())
        }
        ThemeHueStrip(hue) { onHsvChange(it, saturation, brightness) }
        AppTextField(
            value = pendingHex, onValueChange = onHexChange,
            modifier = Modifier.fillMaxWidth(), label = "HEX 颜色值",
            singleLine = true, isError = !validHex,
            supportingText = { AppText(if (validHex) "6 位颜色值，可省略 #" else "请输入 6 位颜色值，例如 #007AFF",
                style = MaterialTheme.typography.bodySmall) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("常用颜色", style = MaterialTheme.typography.labelLarge)
            AppTextButton(onClick = { showAllColors = !showAllColors }) {
                AppText(if (showAllColors) "收起" else "全部 ${ThemeColors.size}", style = MaterialTheme.typography.labelMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeColors.take(if (showAllColors) ThemeColors.size else 10).forEachIndexed { index, preset ->
                val hex = formatMd3CustomColorHex(preset)
                val selected = validHex && normalizeMd3CustomColorHex(pendingHex) == hex
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            onHexChange(hex)
                            haptic(HapticType.SELECTION)
                        })
                        .padding(4.dp).background(preset, CircleShape)
                        .border(if (selected) 2.dp else 1.dp,
                            if (selected) AppContentColors.primaryText() else AppSurfaceTokens.divider(), CircleShape)
                        .semantics { contentDescription = "${ThemeColorNames.getOrElse(index) { "颜色" }} $hex" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) AppText("✓", color = resolveReadableForeground(preset),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
