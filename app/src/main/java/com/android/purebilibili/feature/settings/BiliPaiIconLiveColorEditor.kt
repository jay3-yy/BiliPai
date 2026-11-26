package com.android.purebilibili.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.core.store.BiliPaiIconColorSettings
import com.android.purebilibili.core.store.automaticBiliPaiIconBackground
import com.android.purebilibili.core.store.biliPaiIconBackgroundChoices
import com.android.purebilibili.core.store.biliPaiIconForegroundChoices
import com.android.purebilibili.core.store.biliPaiThemeIconBackground
import com.android.purebilibili.core.store.biliPaiThemeIconColor
import com.android.purebilibili.core.store.getBiliPaiIconColorSettings
import com.android.purebilibili.core.store.getBiliPaiIconColorSettingsSync
import com.android.purebilibili.core.store.isBiliPaiThemeIconExactMatch
import com.android.purebilibili.core.store.resolveBiliPaiThemeIconIndex
import com.android.purebilibili.core.theme.LocalAppThemeIconPrimary
import com.android.purebilibili.core.theme.formatMd3CustomColorHex
import com.android.purebilibili.core.theme.parseMd3CustomColorHex
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppTabRowRole
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import kotlin.math.roundToInt

private const val EXACT_SHORTCUT_BITMAP_SIZE_PX = 432

@Composable
internal fun BiliPaiIconLiveColorEditor(
    onApply: (colors: BiliPaiIconColorSettings, appPrimaryColor: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = LocalAppThemeIconPrimary.current.toArgb()
    val flow = remember(context) { getBiliPaiIconColorSettings(context) }
    val saved by flow.collectAsStateWithLifecycle(initialValue = getBiliPaiIconColorSettingsSync(context))
    var editing by rememberSaveable { mutableStateOf(false) }
    val requested = saved.foreground ?: primary
    val matchedIndex = resolveBiliPaiThemeIconIndex(requested, saved.background)
    val matched = biliPaiThemeIconColor(matchedIndex)
    val matchedBg = biliPaiThemeIconBackground(matchedIndex)
    val exactMatch = isBiliPaiThemeIconExactMatch(requested, saved.background, matchedIndex)

    Column(
        modifier = modifier
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(AppSurfaceTokens.cardContainer())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BiliPaiAdaptiveColorPreview(
                argb = matched,
                description = stringResource(R.string.icon_live_color_matched),
                modifier = Modifier.size(68.dp),
                background = matchedBg,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = stringResource(R.string.icon_live_color_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconMatchStatusBadge(exact = exactMatch)
                }
                AppText(
                    text = stringResource(
                        if (saved.foreground == null) R.string.icon_live_color_follow_app
                        else R.string.icon_live_color_independent
                    ) + " · " + formatMd3CustomColorHex(Color(matched)),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = stringResource(R.string.icon_live_color_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AppPrimaryButton(
            text = stringResource(R.string.icon_live_color_edit),
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (editing) {
        BiliPaiIconColorSheet(
            initialColors = saved,
            appPrimary = primary,
            onDismiss = { editing = false },
            onApply = { selected ->
                onApply(selected, primary)
                Toast.makeText(context, context.getString(R.string.icon_live_color_applied_toast), Toast.LENGTH_SHORT).show()
                editing = false
            },
        )
    }
}

@Composable
private fun IconMatchStatusBadge(
    exact: Boolean,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (exact) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
    }
    val textColor = if (exact) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        AppContentColors.secondaryText()
    }
    Box(
        modifier = modifier
            .clip(AppShapes.container(ContainerLevel.Pill))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        AppText(
            text = stringResource(if (exact) R.string.icon_live_color_exact_badge else R.string.icon_live_color_approx_badge),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
        )
    }
}

/** 两个预览均由 AdaptiveIconDrawable 施加系统遮罩，避免完整 108dp 图层预览与桌面比例不同。 */
@Composable
private fun BiliPaiAdaptiveColorPreview(
    argb: Int,
    description: String,
    modifier: Modifier = Modifier,
    background: Int? = null,
) {
    val context = LocalContext.current
    val drawable = remember(context, argb, background) {
        val foreground = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_bilipai_mark)).mutate()
        foreground.setTint(argb)
        val backgroundColor = background ?: automaticBiliPaiIconBackground(argb)
        AdaptiveIconDrawable(ColorDrawable(backgroundColor), foreground)
    }
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

/** 使用精确 ARGB 渲染自适应位图并请求固定到桌面，实现任意颜色零色差。 */
private fun pinExactBiliPaiIconShortcut(
    context: Context,
    foregroundArgb: Int,
    backgroundArgb: Int,
): Boolean {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!manager.isRequestPinShortcutSupported) return false
    val bitmap = Bitmap.createBitmap(
        EXACT_SHORTCUT_BITMAP_SIZE_PX,
        EXACT_SHORTCUT_BITMAP_SIZE_PX,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(backgroundArgb or (0xFF shl 24))
    val foreground = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_bilipai_mark)).mutate()
    foreground.setTint(foregroundArgb or (0xFF shl 24))
    foreground.setBounds(0, 0, EXACT_SHORTCUT_BITMAP_SIZE_PX, EXACT_SHORTCUT_BITMAP_SIZE_PX)
    foreground.draw(canvas)

    val launchIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
    val fgHex = Integer.toHexString(foregroundArgb and 0xFFFFFF).padStart(6, '0')
    val bgHex = Integer.toHexString(backgroundArgb and 0xFFFFFF).padStart(6, '0')
    val shortcut = ShortcutInfo.Builder(context, "bilipai_exact_${fgHex}_$bgHex")
        .setShortLabel(context.getString(R.string.app_name))
        .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
        .setIntent(launchIntent)
        .build()
    return manager.requestPinShortcut(shortcut, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiliPaiIconColorSheet(
    initialColors: BiliPaiIconColorSettings,
    appPrimary: Int,
    onDismiss: () -> Unit,
    onApply: (BiliPaiIconColorSettings) -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    val pinShortcutSupported = remember(context) {
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true
    }

    var independent by rememberSaveable { mutableStateOf(initialColors.foreground != null) }
    var draft by rememberSaveable { mutableIntStateOf(initialColors.foreground ?: appPrimary) }
    var draftBackground by rememberSaveable { mutableStateOf(initialColors.background) }
    var editingBackground by rememberSaveable { mutableStateOf(false) }
    var resetEditor by rememberSaveable { mutableIntStateOf(0) }
    var foregroundValid by rememberSaveable { mutableStateOf(true) }
    var backgroundValid by rememberSaveable { mutableStateOf(true) }

    val requested = if (independent) draft else appPrimary
    val effectiveRequestedBg = draftBackground ?: automaticBiliPaiIconBackground(requested)
    val matchedIndex = resolveBiliPaiThemeIconIndex(requested, draftBackground)
    val matched = biliPaiThemeIconColor(matchedIndex)
    val matchedBg = biliPaiThemeIconBackground(matchedIndex)
    val exactMatch = isBiliPaiThemeIconExactMatch(requested, draftBackground, matchedIndex)

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部标题栏 + 互换前景/背景
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = stringResource(R.string.icon_live_color_edit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    onClick = {
                        haptic(HapticType.LIGHT)
                        val oldFg = requested
                        val oldBg = effectiveRequestedBg
                        independent = true
                        draft = oldBg
                        draftBackground = oldFg
                        foregroundValid = true
                        backgroundValid = true
                        resetEditor++
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = stringResource(R.string.icon_live_color_swap),
                        tint = AppContentColors.secondaryText(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 实时对比预览卡片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.container(ContainerLevel.Card))
                        .background(AppSurfaceTokens.cardContainer())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val label = stringResource(R.string.icon_live_color_requested)
                            BiliPaiAdaptiveColorPreview(
                                argb = requested,
                                description = label,
                                modifier = Modifier.size(72.dp),
                                background = effectiveRequestedBg,
                            )
                            AppText(label, style = MaterialTheme.typography.labelMedium)
                            AppText(
                                text = formatMd3CustomColorHex(Color(requested)),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppContentColors.secondaryText(),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val label = stringResource(R.string.icon_live_color_matched)
                            BiliPaiAdaptiveColorPreview(
                                argb = matched,
                                description = label,
                                modifier = Modifier.size(72.dp),
                                background = matchedBg,
                            )
                            AppText(label, style = MaterialTheme.typography.labelMedium)
                            AppText(
                                text = formatMd3CustomColorHex(Color(matched)),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppContentColors.secondaryText(),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconMatchStatusBadge(exact = exactMatch)
                        if (!exactMatch) {
                            AppTextButton(
                                onClick = {
                                    haptic(HapticType.LIGHT)
                                    independent = true
                                    draft = matched
                                    if (draftBackground != null) {
                                        draftBackground = matchedBg
                                    }
                                    foregroundValid = true
                                    backgroundValid = true
                                    resetEditor++
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                AppText(
                                    text = stringResource(R.string.icon_live_color_snap_exact),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                // 调色对象分段：标志前景 / 图标背景
                AppThemeAdaptiveTabRow(
                    options = listOf(
                        AppSegmentOption(false, stringResource(R.string.icon_palette_foreground)),
                        AppSegmentOption(true, stringResource(R.string.icon_palette_background)),
                    ),
                    selectedValue = editingBackground,
                    onSelectionChange = {
                        editingBackground = it
                        foregroundValid = true
                        backgroundValid = true
                    },
                    role = AppTabRowRole.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 子模式切换：前景来源 或 背景自动/自定义
                if (!editingBackground) {
                    AppThemeAdaptiveTabRow(
                        options = listOf(
                            AppSegmentOption(false, stringResource(R.string.icon_live_color_follow_app)),
                            AppSegmentOption(true, stringResource(R.string.icon_live_color_independent)),
                        ),
                        selectedValue = independent,
                        onSelectionChange = {
                            independent = it
                            foregroundValid = true
                            resetEditor++
                        },
                        role = AppTabRowRole.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!independent) {
                        AppText(
                            text = stringResource(R.string.icon_live_color_follow_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                } else {
                    val customBackground = draftBackground != null
                    AppThemeAdaptiveTabRow(
                        options = listOf(
                            AppSegmentOption(false, stringResource(R.string.icon_live_color_bg_mode_auto)),
                            AppSegmentOption(true, stringResource(R.string.icon_live_color_bg_mode_custom)),
                        ),
                        selectedValue = customBackground,
                        onSelectionChange = { useCustom ->
                            draftBackground = if (useCustom) {
                                draftBackground ?: automaticBiliPaiIconBackground(requested)
                            } else {
                                null
                            }
                            backgroundValid = true
                            resetEditor++
                        },
                        role = AppTabRowRole.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 零色差预置色板（单点即可保证主图标零色差）
                val showSwatches = if (editingBackground) draftBackground != null else true
                if (showSwatches) {
                    val swatches = if (editingBackground) biliPaiIconBackgroundChoices else biliPaiIconForegroundChoices
                    val activeColor = if (editingBackground) effectiveRequestedBg else requested
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(
                            text = stringResource(R.string.icon_live_color_exact_swatches),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppContentColors.secondaryText(),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            items(swatches, key = { it }) { swatchArgb ->
                                val isSelected = (activeColor or (0xFF shl 24)) == (swatchArgb or (0xFF shl 24))
                                val swatchColor = Color(swatchArgb)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) {
                                                AppContentColors.accent()
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape,
                                        )
                                        .appActionClickable {
                                            haptic(HapticType.LIGHT)
                                            if (editingBackground) {
                                                draftBackground = swatchArgb
                                                backgroundValid = true
                                            } else {
                                                draft = swatchArgb
                                                independent = true
                                                foregroundValid = true
                                            }
                                            resetEditor++
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        AppIcon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 自由取色（自定义背景或编辑前景时显示）
                if (!editingBackground || draftBackground != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppText(
                            text = stringResource(R.string.icon_live_color_free_picker),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppContentColors.secondaryText(),
                        )
                        key(editingBackground, resetEditor) {
                            IconContinuousColorSelection(
                                color = if (editingBackground) effectiveRequestedBg else requested,
                                onColorChange = { color ->
                                    if (editingBackground) {
                                        draftBackground = color
                                    } else {
                                        draft = color
                                        independent = true
                                    }
                                },
                                onValidityChange = { valid ->
                                    if (editingBackground) backgroundValid = valid else foregroundValid = valid
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (pinShortcutSupported) {
                    AppText(
                        text = stringResource(R.string.icon_live_color_pin_shortcut_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
            }

            // 底部操作区：应用到主图标 + 零色差桌面图标
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppPrimaryButton(
                    text = stringResource(R.string.icon_live_color_apply),
                    enabled = foregroundValid && backgroundValid,
                    onClick = {
                        onApply(BiliPaiIconColorSettings(if (independent) draft else null, draftBackground))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pinShortcutSupported) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .clip(AppShapes.container(ContainerLevel.Pill))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
                            .appActionClickable(enabled = foregroundValid && backgroundValid) {
                                haptic(HapticType.LIGHT)
                                val ok = pinExactBiliPaiIconShortcut(
                                    context = context,
                                    foregroundArgb = requested,
                                    backgroundArgb = effectiveRequestedBg,
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.icon_live_color_pin_shortcut_sent
                                        else R.string.icon_live_color_pin_shortcut_failed
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.AddToHomeScreen,
                            contentDescription = null,
                            tint = AppContentColors.primaryText(),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        AppText(
                            text = stringResource(R.string.icon_live_color_pin_shortcut),
                            style = MaterialTheme.typography.labelLarge,
                            color = AppContentColors.primaryText(),
                        )
                    }
                }
            }
        }
    }
}

/** 编辑过程只更新草稿，取色展示复用主题颜色编辑器。 */
@Composable
private fun IconContinuousColorSelection(
    color: Int,
    onColorChange: (Int) -> Unit,
    onValidityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    var pendingHex by rememberSaveable { mutableStateOf(formatMd3CustomColorHex(Color(color))) }
    var lastEmitted by rememberSaveable { mutableIntStateOf(color) }
    LaunchedEffect(color) {
        if (color != lastEmitted) {
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            pendingHex = formatMd3CustomColorHex(Color(color))
            lastEmitted = color
            onValidityChange(true)
        }
    }
    ThemeColorSelectionControls(
        hue = hue,
        saturation = saturation,
        brightness = brightness,
        pendingHex = pendingHex,
        modifier = modifier,
        onHsvChange = { h, s, v ->
            hue = h; saturation = s; brightness = v
            val next = Color.hsv(h, s, v)
            pendingHex = formatMd3CustomColorHex(next)
            lastEmitted = next.toArgb()
            onValidityChange(true)
            onColorChange(lastEmitted)
        },
        onHexChange = { input ->
            pendingHex = input.trim().uppercase().take(7)
            val valid = isValidMd3CustomColorHex(pendingHex)
            onValidityChange(valid)
            if (valid) {
                val next = parseMd3CustomColorHex(pendingHex).toArgb()
                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(next, it) }
                hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                lastEmitted = next
                onColorChange(next)
            }
        },
    )
}
