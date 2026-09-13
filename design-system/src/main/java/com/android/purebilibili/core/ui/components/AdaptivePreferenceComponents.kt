package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.LocalDynamicColorActive
import com.android.purebilibili.core.theme.resolveAndroidNativeChromeTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.resolveCompactCapsuleChromeSpec
import com.android.purebilibili.core.ui.LocalAppIconStyle
import com.android.purebilibili.core.ui.LocalAppListItemStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.adaptiveSquircleBackground
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.rememberResolvedAppIconStyle
import com.android.purebilibili.core.ui.rememberResolvedAppListItemStyle
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.android.purebilibili.core.ui.AppSurfaceTokens
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SliderPreference as MiuixSliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

private object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * Trailing preference value layout (version / channel / short detail on the right).
 *
 * Previously hard-capped at 120.dp + single-line ellipsis, which clipped common Chinese
 * labels like「开源约定与官方渠道」and version strings on phone-width settings rows.
 * Shared by every AdaptivePreferenceContent renderer (MD3 / Miuix / fallback).
 */
internal const val APP_PREFERENCE_VALUE_MAX_WIDTH_DP = 200
internal const val APP_PREFERENCE_VALUE_MAX_LINES = 2

internal fun Modifier.appPreferenceValueTextModifier(): Modifier =
    this.widthIn(max = APP_PREFERENCE_VALUE_MAX_WIDTH_DP.dp)

// ═══════════════════════════════════════════════════
//  Common iOS List Components (Reused across Settings, Profile, etc.)
// ═══════════════════════════════════════════════════

data class AdaptiveListComponentVisualSpec(
    val sectionStartPaddingDp: Int,
    val groupCornerRadiusDp: Int,
    val groupTonalElevationDp: Int,
    val iconCornerRadiusDp: Int,
    val iconContainerSizeDp: Int,
    val iconGlyphSizeDp: Int,
    val iconBackgroundAlpha: Float,
    val gridCornerRadiusDp: Int,
    val searchBarCornerRadiusDp: Int,
    val searchBarHeightDp: Int,
    val dividerThicknessDp: Float,
    val dividerStartIndentDp: Int
)

enum class AppPreferenceIconTreatment {
    TONAL,
    FILLED,
}

val LocalAppPreferenceIconTreatment = staticCompositionLocalOf {
    AppPreferenceIconTreatment.TONAL
}

val LocalAppPreferenceGroupPresentation = staticCompositionLocalOf {
    AppPreferenceGroupPresentation.CARD
}

data class AdaptiveListRowVisualSpec(
    val insideHorizontalPaddingDp: Int,
    val insideVerticalPaddingDp: Int,
    val trailingIconSizeDp: Int,
    val trailingSpacingDp: Int,
    val minTouchTargetHeightDp: Int
)

/**
 * Semantic list capabilities consumed by feature screens without exposing the active UI style.
 */
data class AdaptiveListVisualCapabilities(
    val componentSpec: AdaptiveListComponentVisualSpec,
    val rowSpec: AdaptiveListRowVisualSpec,
    val showExplicitActionChevron: Boolean,
)

internal fun resolveAdaptiveListComponentVisualSpec(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean = true,
): AdaptiveListComponentVisualSpec {
    val chromeTokens = resolveAndroidNativeChromeTokens(uiStyle)
    val compactChrome = resolveCompactCapsuleChromeSpec(uiStyle)
    val nonGlassMiuix = isMiuixNonGlassEnabled(uiStyle, liquidGlassEnabled)
    return if (uiStyle == AppUiStyle.MIUIX) {
        AdaptiveListComponentVisualSpec(
            sectionStartPaddingDp = chromeTokens.denseHorizontalSpacingDp,
            groupCornerRadiusDp = if (nonGlassMiuix) 16 else chromeTokens.containerCornerRadiusDp,
            groupTonalElevationDp = chromeTokens.tonalSurfaceElevationDp,
            iconCornerRadiusDp = 10,
            iconContainerSizeDp = 38,
            iconGlyphSizeDp = 20,
            iconBackgroundAlpha = chromeTokens.selectedContainerAlpha,
            gridCornerRadiusDp = if (nonGlassMiuix) 16 else chromeTokens.containerCornerRadiusDp,
            searchBarCornerRadiusDp = compactChrome.primaryCornerRadiusDp,
            searchBarHeightDp = compactChrome.primaryHeightDp,
            dividerThicknessDp = 0f,
            dividerStartIndentDp = chromeTokens.denseHorizontalSpacingDp
        )
    } else {
        AdaptiveListComponentVisualSpec(
            sectionStartPaddingDp = chromeTokens.denseHorizontalSpacingDp,
            groupCornerRadiusDp = chromeTokens.containerCornerRadiusDp,
            groupTonalElevationDp = chromeTokens.tonalSurfaceElevationDp,
            iconCornerRadiusDp = 12,
            iconContainerSizeDp = 40,
            iconGlyphSizeDp = 22,
            iconBackgroundAlpha = chromeTokens.selectedContainerAlpha,
            gridCornerRadiusDp = chromeTokens.containerCornerRadiusDp,
            searchBarCornerRadiusDp = compactChrome.primaryCornerRadiusDp,
            searchBarHeightDp = compactChrome.primaryHeightDp,
            dividerThicknessDp = 0f,
            dividerStartIndentDp = chromeTokens.denseHorizontalSpacingDp
        )
    }
}

@Composable
private fun rememberAdaptiveListComponentVisualSpec(): AdaptiveListComponentVisualSpec {
    val style = LocalAppUiStyle.current
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    return remember(style, glass) { resolveAdaptiveListComponentVisualSpec(style, glass) }
}

@Composable
private fun rememberAdaptiveListRowVisualSpec(): AdaptiveListRowVisualSpec {
    val style = LocalAppUiStyle.current
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    return remember(style, glass) { resolveAdaptiveListRowVisualSpec(style, glass) }
}

internal fun resolveAdaptiveListRowVisualSpec(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean = true,
): AdaptiveListRowVisualSpec {
    val chromeTokens = resolveAndroidNativeChromeTokens(uiStyle)
    return if (uiStyle == AppUiStyle.MIUIX) {
        AdaptiveListRowVisualSpec(
            insideHorizontalPaddingDp = 16,
            insideVerticalPaddingDp = 14,
            trailingIconSizeDp = 14,
            trailingSpacingDp = 6,
            minTouchTargetHeightDp = 48
        )
    } else {
        AdaptiveListRowVisualSpec(
            insideHorizontalPaddingDp = 18,
            insideVerticalPaddingDp = 16,
            trailingIconSizeDp = 16,
            trailingSpacingDp = 8,
            minTouchTargetHeightDp = chromeTokens.rowMinTouchTargetDp
        )
    }
}

internal fun resolveAdaptiveListVisualCapabilities(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean = true,
): AdaptiveListVisualCapabilities = AdaptiveListVisualCapabilities(
    componentSpec = resolveAdaptiveListComponentVisualSpec(uiStyle, liquidGlassEnabled),
    rowSpec = resolveAdaptiveListRowVisualSpec(uiStyle, liquidGlassEnabled),
    showExplicitActionChevron = !shouldUseNativeMiuixSearchBar(uiStyle),
)

@Composable
fun rememberAdaptiveListVisualCapabilities(): AdaptiveListVisualCapabilities {
    val uiStyle = LocalAppUiStyle.current
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    return remember(uiStyle, glass) {
        resolveAdaptiveListVisualCapabilities(uiStyle, glass)
    }
}

internal fun resolveAdaptiveGroupContainerColor(
    uiStyle: AppUiStyle,
    colorScheme: ColorScheme,
    globalWallpaperVisible: Boolean = false
): Color {
    val resolvedColor = when (uiStyle) {
        AppUiStyle.MIUIX -> colorScheme.surfaceContainer
        AppUiStyle.MATERIAL3 -> colorScheme.surfaceContainerLow
    }
    return resolveGlobalWallpaperListContainerColor(
        containerColor = resolvedColor,
        colorScheme = colorScheme,
        globalWallpaperVisible = globalWallpaperVisible,
        targetAlpha = 0.62f
    )
}

internal fun resolveAdaptiveSearchBarContainerColor(
    uiStyle: AppUiStyle,
    colorScheme: ColorScheme,
    globalWallpaperVisible: Boolean = false
): Color {
    val resolvedColor = when (uiStyle) {
        AppUiStyle.MIUIX -> colorScheme.surfaceContainerHigh
        AppUiStyle.MATERIAL3 -> colorScheme.surfaceContainerHigh
    }
    return resolveGlobalWallpaperListContainerColor(
        containerColor = resolvedColor,
        colorScheme = colorScheme,
        globalWallpaperVisible = globalWallpaperVisible,
        targetAlpha = 0.48f
    )
}

internal fun resolveAdaptiveSearchBarContainerOverride(
    requestedColor: Color,
    defaultColor: Color,
): Color = if (requestedColor == Color.Unspecified) defaultColor else requestedColor

internal fun shouldUseNativeMiuixSearchBar(
    uiStyle: AppUiStyle
): Boolean = uiStyle == AppUiStyle.MIUIX

internal fun resolveGlobalWallpaperListContainerColor(
    containerColor: Color,
    colorScheme: ColorScheme,
    globalWallpaperVisible: Boolean,
    targetAlpha: Float
): Color {
    if (!globalWallpaperVisible || containerColor.alpha == 0f) return containerColor
    if (!isDefaultListContainerColor(containerColor, colorScheme)) return containerColor
    val adjustedAlpha = if (colorScheme.background.luminance() > 0.5f) {
        targetAlpha
    } else {
        (targetAlpha + 0.12f).coerceAtMost(0.78f)
    }
    return containerColor.copy(alpha = containerColor.alpha.coerceAtMost(adjustedAlpha))
}

private fun isDefaultListContainerColor(
    color: Color,
    colorScheme: ColorScheme
): Boolean {
    val opaqueColor = color.copy(alpha = 1f)
    return opaqueColor == colorScheme.background.copy(alpha = 1f) ||
        opaqueColor == colorScheme.surface.copy(alpha = 1f) ||
        opaqueColor == colorScheme.surfaceVariant.copy(alpha = 1f) ||
        opaqueColor == colorScheme.surfaceContainer.copy(alpha = 1f) ||
        opaqueColor == colorScheme.surfaceContainerLow.copy(alpha = 1f) ||
        opaqueColor == colorScheme.surfaceContainerHigh.copy(alpha = 1f)
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveAdaptiveSemanticIconTint(
    iconTint: Color,
    uiStyle: AppUiStyle,
    colorScheme: ColorScheme,
    useSemanticAccentRoles: Boolean = true,
): Color {
    if (
        uiStyle != AppUiStyle.MATERIAL3 ||
        iconTint == Color.Unspecified
    ) {
        return iconTint
    }
    // Settings icons are navigation affordances rather than status indicators.
    // MATERIAL3 therefore uses the active theme accent consistently across the whole
    // settings hierarchy; MIUIX keeps its familiar per-item colors.
    return colorScheme.primary
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveAdaptivePreferenceIconContainerColor(
    iconTint: Color,
    semanticTint: Color,
    treatment: AppPreferenceIconTreatment,
    iconStyle: AppIconStyle = AppIconStyle.AUTO,
    colorScheme: ColorScheme? = null,
): Color = when (iconStyle) {
    AppIconStyle.THEME_CONTAINER -> colorScheme?.secondaryContainer ?: semanticTint
    AppIconStyle.MD3_STANDARD -> Color.Transparent
    AppIconStyle.AUTO -> semanticTint
}

internal fun resolveAdaptivePreferenceIconContentColor(
    containerColor: Color,
    colorScheme: ColorScheme,
    iconStyle: AppIconStyle = AppIconStyle.AUTO,
): Color {
    if (containerColor == Color.Unspecified) return Color.Unspecified
    return when (iconStyle) {
        AppIconStyle.THEME_CONTAINER -> colorScheme.onSecondaryContainer
        AppIconStyle.MD3_STANDARD -> colorScheme.onSurfaceVariant
        AppIconStyle.AUTO -> {
            val opaqueContainer = containerColor.copy(alpha = 1f)
            when (opaqueContainer) {
                colorScheme.primary.copy(alpha = 1f) -> colorScheme.onPrimary
                colorScheme.secondary.copy(alpha = 1f) -> colorScheme.onSecondary
                colorScheme.tertiary.copy(alpha = 1f) -> colorScheme.onTertiary
                colorScheme.error.copy(alpha = 1f) -> colorScheme.onError
                colorScheme.primaryContainer.copy(alpha = 1f) -> colorScheme.onPrimaryContainer
                colorScheme.secondaryContainer.copy(alpha = 1f) -> colorScheme.onSecondaryContainer
                colorScheme.tertiaryContainer.copy(alpha = 1f) -> colorScheme.onTertiaryContainer
                colorScheme.errorContainer.copy(alpha = 1f) -> colorScheme.onErrorContainer
                else -> if (opaqueContainer.luminance() >= 0.72f) Color.Black else Color.White
            }
        }
    }
}

internal fun resolveAdaptivePreferenceIconGlyphColor(
    treatment: AppPreferenceIconTreatment,
    iconStyle: AppIconStyle,
    containerContentColor: Color,
    semanticIconColor: Color,
): Color = if (
    treatment == AppPreferenceIconTreatment.FILLED ||
    iconStyle == AppIconStyle.THEME_CONTAINER ||
    iconStyle == AppIconStyle.MD3_STANDARD
) {
    // MD3_STANDARD 的容器色是 Color.Transparent，若走 semanticIconColor 会得到透明
    // glyph（图标消失只剩文字）；这里用 containerContentColor（onSurfaceVariant 单色）。
    containerContentColor
} else {
    semanticIconColor
}

internal fun resolveAdaptivePreferenceIconBackgroundAlpha(
    treatment: AppPreferenceIconTreatment,
    tonalAlpha: Float,
    iconStyle: AppIconStyle = AppIconStyle.AUTO,
): Float = when (iconStyle) {
    AppIconStyle.THEME_CONTAINER -> 1f
    AppIconStyle.MD3_STANDARD -> 0f
    AppIconStyle.AUTO -> if (treatment == AppPreferenceIconTreatment.FILLED) 1f else tonalAlpha
}

@Composable
fun rememberAdaptiveSemanticIconTint(
    iconTint: Color,
    uiStyle: AppUiStyle = LocalAppUiStyle.current,
    dynamicColorActive: Boolean = LocalDynamicColorActive.current
): Color {
    val colorScheme = MaterialTheme.colorScheme
    return remember(iconTint, uiStyle, dynamicColorActive, colorScheme) {
        resolveAdaptiveSemanticIconTint(
            iconTint = iconTint,
            uiStyle = uiStyle,
            colorScheme = colorScheme,
            useSemanticAccentRoles = dynamicColorActive,
        )
    }
}

/**
 * 与 [AdaptivePreferenceContent] 图标最终呈现一致的颜色：MD3 官方推荐预设
 * 下为 onSurfaceVariant 单色，其余预设保留传入的多彩/语义色。
 * 用于无容器图标（如 WindowSpinnerPreference 的 startAction、发布渠道卡片），
 * 避免仅经 [rememberAdaptiveSemanticIconTint] 落到主题主色、与其他条目不一致。
 */
@Composable
fun rememberAdaptivePreferenceIconTint(
    iconTint: Color,
): Color {
    val iconStyle = rememberResolvedAppIconStyle()
    val colorScheme = MaterialTheme.colorScheme
    return remember(iconTint, iconStyle, colorScheme) {
        when (iconStyle) {
            AppIconStyle.MD3_STANDARD -> colorScheme.onSurfaceVariant
            else -> iconTint
        }
    }
}

@Composable
fun rememberAdaptivePreferenceIconContentColor(
    containerColor: Color,
): Color {
    val colorScheme = MaterialTheme.colorScheme
    val iconStyle = rememberResolvedAppIconStyle()
    return remember(containerColor, colorScheme, iconStyle) {
        resolveAdaptivePreferenceIconContentColor(containerColor, colorScheme, iconStyle)
    }
}

@Composable
fun rememberAdaptivePreferenceIconContainerColor(
    iconTint: Color,
): Color {
    val treatment = LocalAppPreferenceIconTreatment.current
    val semanticTint = rememberAdaptiveSemanticIconTint(iconTint)
    val iconStyle = rememberResolvedAppIconStyle()
    val colorScheme = MaterialTheme.colorScheme
    return remember(iconTint, semanticTint, treatment, iconStyle, colorScheme) {
        resolveAdaptivePreferenceIconContainerColor(
            iconTint = iconTint,
            semanticTint = semanticTint,
            treatment = treatment,
            iconStyle = iconStyle,
            colorScheme = colorScheme,
        )
    }
}

@Composable
fun AppAdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = AppSwitch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    modifier = modifier,
)

@Composable
fun AdaptivePreferenceSectionTitleRenderer(title: String) {
    val uiStyle = LocalAppUiStyle.current
    val visualSpec = rememberAdaptiveListComponentVisualSpec()
    if (uiStyle == AppUiStyle.MIUIX) {
        SmallTitle(
            text = title,
            textColor = AppSurfaceTokens.onSurfaceVariantSummary(),
            insideMargin = PaddingValues(
                start = visualSpec.sectionStartPaddingDp.dp,
                top = 24.dp,
                bottom = 8.dp
            )
        )
        return
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.sp,
        modifier = Modifier.padding(
            start = visualSpec.sectionStartPaddingDp.dp,
            top = 28.dp,
            bottom = 10.dp
        )
    )
}

@Composable
fun AdaptivePreferenceGroupRenderer(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: androidx.compose.ui.graphics.Shape? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    presentation: AppPreferenceGroupPresentation = AppPreferenceGroupPresentation.CARD,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedPresentation = if (
        LocalAppPreferenceGroupPresentation.current == AppPreferenceGroupPresentation.FLAT
    ) {
        AppPreferenceGroupPresentation.FLAT
    } else {
        presentation
    }
    if (resolvedPresentation == AppPreferenceGroupPresentation.FLAT) {
        Column(modifier = modifier, content = content)
        return
    }

    val uiStyle = LocalAppUiStyle.current
    val visualSpec = rememberAdaptiveListComponentVisualSpec()
    val colorScheme = MaterialTheme.colorScheme
    val defaultShape = RoundedCornerShape(visualSpec.groupCornerRadiusDp.dp)
    val appliedShape = shape ?: defaultShape
    val resolvedContainerColor = resolveAdaptiveGroupContainerColor(
        uiStyle = uiStyle,
        colorScheme = colorScheme,
        globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current && !isMiuixNonGlassEnabled()
    )

    if (uiStyle == AppUiStyle.MIUIX) {
        MiuixCard(
            modifier = modifier.padding(horizontal = 16.dp),
            cornerRadius = visualSpec.groupCornerRadiusDp.dp,
            insideMargin = PaddingValues(0.dp),
            colors = MiuixCardDefaults.defaultColors(color = resolvedContainerColor)
        ) {
            content()
        }
        return
    }
    
    Surface(
        modifier = modifier.padding(horizontal = 12.dp),
        shape = appliedShape,
        color = resolvedContainerColor,
        shadowElevation = 0.dp,
        tonalElevation = visualSpec.groupTonalElevationDp.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(content = content)
    }
}

@Composable
internal fun AdaptiveSwitchPreferenceContent(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val uiStyle = LocalAppUiStyle.current
    val visualSpec = rememberAdaptiveListComponentVisualSpec()
    val rowSpec = rememberAdaptiveListRowVisualSpec()
    val iconTreatment = LocalAppPreferenceIconTreatment.current
    val iconStyle = rememberResolvedAppIconStyle()
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(iconTint)
    val filledIconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    val iconContentColor = resolveAdaptivePreferenceIconGlyphColor(
        treatment = iconTreatment,
        iconStyle = iconStyle,
        containerContentColor = filledIconContentColor,
        semanticIconColor = effectiveIconTint,
    )
    val iconBackgroundAlpha = resolveAdaptivePreferenceIconBackgroundAlpha(
        iconTreatment,
        visualSpec.iconBackgroundAlpha,
        iconStyle,
    )
    val listItemStyle = rememberResolvedAppListItemStyle()
    if (listItemStyle == AppListItemStyle.NATIVE && uiStyle == AppUiStyle.MATERIAL3) {
        // MD3 原生:ListItem + M3 Switch
        val haptic = LocalHapticFeedback.current
        val hapticsEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
            },
            supportingContent = subtitle?.let { subtitleText ->
                {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
            },
            leadingContent = if (icon != null) {
                {
                    Box(
                        modifier = Modifier
                            .size(visualSpec.iconContainerSizeDp.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(effectiveIconTint.copy(alpha = iconBackgroundAlpha)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp),
                        )
                    }
                }
            } else {
                null
            },
            trailingContent = {
                AppAdaptiveSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rowSpec.minTouchTargetHeightDp.dp)
                .clickable(enabled = enabled) {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onCheckedChange(!checked)
                },
        )
        return
    }
    if (listItemStyle == AppListItemStyle.NATIVE && uiStyle == AppUiStyle.MIUIX) {
        val platformHaptic = LocalHapticFeedback.current
        val effectiveHaptic = if (LocalAppThemeConfig.current.hapticFeedbackEnabled) {
            platformHaptic
        } else {
            NoOpHapticFeedback
        }
        CompositionLocalProvider(LocalHapticFeedback provides effectiveHaptic) {
            MiuixSwitchPreference(
                checked = checked,
                onCheckedChange = onCheckedChange,
                title = title,
                titleColor = BasicComponentDefaults.titleColor(color = textColor),
                summary = subtitle,
                summaryColor = BasicComponentDefaults.summaryColor(color = subtitleColor),
                enabled = enabled,
                insideMargin = PaddingValues(
                    horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                    vertical = rowSpec.insideVerticalPaddingDp.dp
                ),
                startAction = {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(visualSpec.iconContainerSizeDp.dp)
                                .adaptiveSquircleBackground(
                                    color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                                    cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                            )
                        }
                    }
                }
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowSpec.minTouchTargetHeightDp.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(
                horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                vertical = rowSpec.insideVerticalPaddingDp.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(visualSpec.iconContainerSizeDp.dp)
                    .adaptiveSquircleBackground(
                        color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                        cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = textColor)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
            }
        }
        Spacer(modifier = Modifier.width(rowSpec.trailingSpacingDp.dp))
        AppAdaptiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun AdaptiveSliderPreferenceRenderer(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    val uiStyle = LocalAppUiStyle.current
    val visualSpec = rememberAdaptiveListComponentVisualSpec()
    val rowSpec = rememberAdaptiveListRowVisualSpec()
    val iconTreatment = LocalAppPreferenceIconTreatment.current
    val iconStyle = rememberResolvedAppIconStyle()
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(iconTint)
    val filledIconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    val iconContentColor = resolveAdaptivePreferenceIconGlyphColor(
        treatment = iconTreatment,
        iconStyle = iconStyle,
        containerContentColor = filledIconContentColor,
        semanticIconColor = effectiveIconTint,
    )
    val iconBackgroundAlpha = resolveAdaptivePreferenceIconBackgroundAlpha(
        iconTreatment,
        visualSpec.iconBackgroundAlpha,
        iconStyle,
    )
    val iconCornerRadius = visualSpec.iconCornerRadiusDp.dp
    if (shouldRouteSliderPreferenceToMiuixSliderPreference(uiStyle)) {
        MiuixSliderPreference(
            value = value,
            onValueChange = onValueChange,
            title = title,
            titleColor = BasicComponentDefaults.titleColor(color = textColor),
            summary = subtitle,
            summaryColor = BasicComponentDefaults.summaryColor(color = subtitleColor),
            valueText = valueLabel,
            valueRange = valueRange,
            steps = steps,
            insideMargin = PaddingValues(
                horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                vertical = rowSpec.insideVerticalPaddingDp.dp
            ),
            startAction = {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(visualSpec.iconContainerSizeDp.dp)
                            .adaptiveSquircleBackground(
                                color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                                cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                        )
                    }
                }
            }
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                vertical = rowSpec.insideVerticalPaddingDp.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(visualSpec.iconContainerSizeDp.dp)
                        .adaptiveSquircleBackground(
                            color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                            cornerRadius = iconCornerRadius,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = textColor)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor
                    )
                }
            }
            if (valueLabel != null) {
                Spacer(modifier = Modifier.width(rowSpec.trailingSpacingDp.dp))
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = valueColor
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun Md3NativeListItemContent(
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)?,
    textColor: Color,
    subtitleColor: Color,
    valueColor: Color,
    showChevron: Boolean,
    trailingContent: (@Composable (() -> Unit))? = null,
    minTouchHeight: Int,
    iconContainerColor: Color,
    iconContentColor: Color,
    iconBackgroundAlpha: Float,
    iconContainerSize: Dp,
    iconGlyphSize: Dp,
    iconCornerRadius: Dp,
) {
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
            )
        },
        supportingContent = subtitle?.let { subtitleText ->
            {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
            }
        },
        leadingContent = if (icon != null || iconPainter != null) {
            {
                Box(
                    modifier = Modifier
                        .size(iconContainerSize)
                        .clip(MaterialTheme.shapes.small)
                        .background(iconContainerColor.copy(alpha = iconBackgroundAlpha)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        icon != null -> Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(iconGlyphSize),
                        )

                        else -> Icon(
                            painter = iconPainter!!,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(iconGlyphSize),
                        )
                    }
                }
            }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent?.invoke()
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = valueColor,
                        maxLines = APP_PREFERENCE_VALUE_MAX_LINES,
                        softWrap = true,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .appPreferenceValueTextModifier(),
                    )
                }
                if (showChevron && onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = valueColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchHeight.dp)
            .clickable(enabled = onClick != null) {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick?.invoke()
            },
    )
}

@Composable
internal fun AdaptivePreferenceContent(
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    copyValue: String? = null,
    onClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    chevronTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    centered: Boolean = false,
    enableCopy: Boolean = false,
    onCopyRequest: ((text: String, label: String?) -> Unit)? = null,
    showChevron: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val uiStyle = LocalAppUiStyle.current
    val visualSpec = rememberAdaptiveListComponentVisualSpec()
    val rowSpec = rememberAdaptiveListRowVisualSpec()
    val iconTreatment = LocalAppPreferenceIconTreatment.current
    val iconStyle = rememberResolvedAppIconStyle()
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(iconTint)
    val filledIconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    val iconContentColor = resolveAdaptivePreferenceIconGlyphColor(
        treatment = iconTreatment,
        iconStyle = iconStyle,
        containerContentColor = filledIconContentColor,
        semanticIconColor = effectiveIconTint,
    )
    val iconBackgroundAlpha = resolveAdaptivePreferenceIconBackgroundAlpha(
        iconTreatment,
        visualSpec.iconBackgroundAlpha,
        iconStyle,
    )
    val iconCornerRadius = visualSpec.iconCornerRadiusDp.dp
    val clickableRenderer = resolveAppClickableItemRenderer(
        uiStyle = uiStyle,
        onClick = onClick,
        showChevron = showChevron,
        centered = centered
    )
    val listItemStyle = rememberResolvedAppListItemStyle()
    val nativeListItem = listItemStyle == AppListItemStyle.NATIVE
    if (nativeListItem && uiStyle == AppUiStyle.MATERIAL3) {
        Md3NativeListItemContent(
            icon = icon,
            iconPainter = iconPainter,
            title = title,
            subtitle = subtitle,
            value = value,
            onClick = onClick,
            textColor = textColor,
            subtitleColor = subtitleColor,
            valueColor = valueColor,
            showChevron = showChevron,
            trailingContent = trailingContent,
            minTouchHeight = rowSpec.minTouchTargetHeightDp,
            iconContainerColor = effectiveIconTint,
            iconContentColor = iconContentColor,
            iconBackgroundAlpha = iconBackgroundAlpha,
            iconContainerSize = visualSpec.iconContainerSizeDp.dp,
            iconGlyphSize = visualSpec.iconGlyphSizeDp.dp,
            iconCornerRadius = iconCornerRadius,
        )
        return
    }
    if (nativeListItem && clickableRenderer == AppClickableItemRenderer.MIUIX_ARROW) {
        BasicComponent(
            onClick = onClick,
            insideMargin = PaddingValues(
                horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                vertical = rowSpec.insideVerticalPaddingDp.dp
            ),
            startAction = {
                when {
                    icon != null -> {
                        Box(
                            modifier = Modifier
                                .size(visualSpec.iconContainerSizeDp.dp)
                                .adaptiveSquircleBackground(
                                    color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                                    cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                            )
                        }
                    }

                    iconPainter != null -> {
                        Box(
                            modifier = Modifier
                                .size(visualSpec.iconContainerSizeDp.dp)
                                .adaptiveSquircleBackground(
                                    color = if (effectiveIconTint == Color.Unspecified) {
                                        Color.Transparent
                                    } else {
                                        effectiveIconTint.copy(alpha = iconBackgroundAlpha)
                                    },
                                    cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                            )
                        }
                    }
                }
            },
            endActions = {
                trailingContent?.invoke()
                if (!value.isNullOrBlank()) {
                    if (trailingContent != null) Spacer(Modifier.width(8.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = valueColor,
                        maxLines = APP_PREFERENCE_VALUE_MAX_LINES,
                        softWrap = true,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier
                            .appPreferenceValueTextModifier()
                            .onLongPressAction(
                                enabled = enableCopy && onCopyRequest != null,
                                onLongPress = { onCopyRequest?.invoke(copyValue ?: value, title) },
                            )
                    )
                }
                if (showChevron && onClick != null) {
                    if (trailingContent != null || !value.isNullOrBlank()) Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = chevronTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        ) {
            Text(
                text = title,
                color = textColor,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }
        }
        return
    }
    if (clickableRenderer == AppClickableItemRenderer.MD3_BASIC) {
        val haptic = LocalHapticFeedback.current
        val hapticsEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rowSpec.minTouchTargetHeightDp.dp)
                .clickable(enabled = onClick != null) {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onClick?.invoke()
                }
                .padding(
                    horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                    vertical = rowSpec.insideVerticalPaddingDp.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null || iconPainter != null) {
                Box(
                    modifier = Modifier
                        .size(visualSpec.iconContainerSizeDp.dp)
                        .adaptiveSquircleBackground(
                            color = if (effectiveIconTint == Color.Unspecified) {
                                Color.Transparent
                            } else {
                                effectiveIconTint.copy(alpha = iconBackgroundAlpha)
                            },
                            cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                        )
                    } else if (iconPainter != null) {
                        Icon(
                            painter = iconPainter,
                            contentDescription = null,
                            tint = effectiveIconTint,
                            modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
            }
            if (trailingContent != null || !value.isNullOrBlank() || (onClick != null && showChevron)) {
                Spacer(modifier = Modifier.width(rowSpec.trailingSpacingDp.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingContent?.invoke()
                    if (!value.isNullOrBlank()) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = valueColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            maxLines = APP_PREFERENCE_VALUE_MAX_LINES,
                            softWrap = true,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .appPreferenceValueTextModifier()
                                .onLongPressAction(
                                    enabled = enableCopy && onCopyRequest != null,
                                    onLongPress = { onCopyRequest?.invoke(copyValue ?: value, title) },
                                )
                        )
                    }
                    if (onClick != null && showChevron) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = chevronTint,
                            modifier = Modifier.size(rowSpec.trailingIconSizeDp.dp)
                        )
                    }
                }
            }
        }
        return
    }
    if (clickableRenderer != AppClickableItemRenderer.MD3_BASIC) {
        BasicComponent(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rowSpec.minTouchTargetHeightDp.dp),
            title = title,
            summary = subtitle,
            onClick = onClick,
            insideMargin = PaddingValues(
                horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                vertical = rowSpec.insideVerticalPaddingDp.dp
            ),
            startAction = {
                when {
                    icon != null -> {
                        Box(
                            modifier = Modifier
                                .size(visualSpec.iconContainerSizeDp.dp)
                                .adaptiveSquircleBackground(
                                    color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                                    cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                            )
                        }
                    }

                    iconPainter != null -> {
                        Box(
                            modifier = Modifier
                                .size(visualSpec.iconContainerSizeDp.dp)
                                .adaptiveSquircleBackground(
                                    color = if (effectiveIconTint == Color.Unspecified) {
                                        Color.Transparent
                                    } else {
                                        effectiveIconTint.copy(alpha = iconBackgroundAlpha)
                                    },
                                    cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                            )
                        }
                    }
                }
            },
            endActions = {
                trailingContent?.invoke()
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppSurfaceTokens.onSurfaceVariantSummary(),
                        maxLines = APP_PREFERENCE_VALUE_MAX_LINES,
                        softWrap = true,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier
                            .appPreferenceValueTextModifier()
                            .onLongPressAction(
                                enabled = enableCopy && onCopyRequest != null,
                                onLongPress = { onCopyRequest?.invoke(copyValue ?: value, title) },
                            )
                    )
                    Spacer(modifier = Modifier.width(rowSpec.trailingSpacingDp.dp))
                }
                if (onClick != null && showChevron) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = AppSurfaceTokens.onSurfaceVariantActions(),
                        modifier = Modifier.size(rowSpec.trailingIconSizeDp.dp)
                    )
                }
            }
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
    ) {
        if (!centered) {
            if (icon != null || iconPainter != null) {
                if (effectiveIconTint != Color.Unspecified) {
                    Box(
                        modifier = Modifier
                            .size(visualSpec.iconContainerSizeDp.dp)
                            .adaptiveSquircleBackground(
                                color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                                cornerRadius = iconCornerRadius,
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                            )
                        } else if (iconPainter != null) {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                tint = iconContentColor,
                                modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.size(visualSpec.iconContainerSizeDp.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(visualSpec.iconContainerSizeDp.dp)
                            )
                        } else if (iconPainter != null) {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(visualSpec.iconContainerSizeDp.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
        }
        
        if (centered) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            // Title stays single-line so long search labels don't wrap under the trailing section path.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        if (!centered) {
            Spacer(modifier = Modifier.width(rowSpec.trailingSpacingDp.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent?.invoke()
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = valueColor,
                        maxLines = APP_PREFERENCE_VALUE_MAX_LINES,
                        softWrap = true,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier
                            .appPreferenceValueTextModifier()
                            .onLongPressAction(
                                enabled = enableCopy && onCopyRequest != null,
                                onLongPress = { onCopyRequest?.invoke(copyValue ?: value, title) },
                            )
                    )
                }
                if (onClick != null && showChevron) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = chevronTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AdaptivePreferenceDividerRenderer(
    modifier: Modifier = Modifier,
    startIndent: androidx.compose.ui.unit.Dp = 66.dp
) {
    // 迁移后所有样式统一使用 0.dp 分隔线，渲染器保持空操作。
    return
}


@Composable
fun AdaptivePreferenceGridItemRenderer(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val uiStyle = LocalAppUiStyle.current
    val visualSpec = rememberAdaptiveListComponentVisualSpec()
    val iconTreatment = LocalAppPreferenceIconTreatment.current
    val iconStyle = rememberResolvedAppIconStyle()
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(iconTint)
    val filledIconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    val iconContentColor = resolveAdaptivePreferenceIconGlyphColor(
        treatment = iconTreatment,
        iconStyle = iconStyle,
        containerContentColor = filledIconContentColor,
        semanticIconColor = effectiveIconTint,
    )
    val iconBackgroundAlpha = resolveAdaptivePreferenceIconBackgroundAlpha(
        iconTreatment,
        visualSpec.iconBackgroundAlpha,
        iconStyle,
    )
    val itemCornerRadius = visualSpec.gridCornerRadiusDp.dp
    val resolvedContainerColor = resolveGlobalWallpaperListContainerColor(
        containerColor = containerColor,
        colorScheme = MaterialTheme.colorScheme,
        globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current,
        targetAlpha = 0.62f
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(itemCornerRadius))
            .background(resolvedContainerColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .adaptiveSquircleBackground(
                    color = effectiveIconTint.copy(alpha = iconBackgroundAlpha),
                    cornerRadius = AppShapes.containerCornerDp(ContainerLevel.Field),
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconContentColor,
                modifier = Modifier.size(26.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AdaptiveSearchFieldRenderer(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    containerColor: Color = Color.Unspecified,
    shapeOverride: Shape? = null,
    heightOverride: Dp? = null,
    forceExpandedInput: Boolean = false,
    topBarChrome: Boolean = false,
    onSearch: () -> Unit = {},
    onClear: () -> Unit = { onQueryChange("") },
    showClearAction: Boolean = true,
    autoFocusEnabled: Boolean = forceExpandedInput,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val uiStyle = LocalAppUiStyle.current
    val colorScheme = MaterialTheme.colorScheme
    val visualSpec = rememberAdaptiveListComponentVisualSpec()
    val searchBarCornerRadius = visualSpec.searchBarCornerRadiusDp.dp
    val searchBarShape = shapeOverride ?: RoundedCornerShape(searchBarCornerRadius)
    val resolvedContainerColor = resolveAdaptiveSearchBarContainerOverride(
        requestedColor = containerColor,
        defaultColor = resolveAdaptiveSearchBarContainerColor(
            uiStyle = uiStyle,
            colorScheme = colorScheme,
            globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
        ),
    )
    val resolvedHeight = heightOverride ?: visualSpec.searchBarHeightDp.dp

    if (forceExpandedInput) {
        val fallbackFocusRequester = remember { FocusRequester() }
        val resolvedFocusRequester = focusRequester ?: fallbackFocusRequester
        LaunchedEffect(resolvedFocusRequester, autoFocusEnabled) {
            if (autoFocusEnabled) {
                delay(80)
                runCatching { resolvedFocusRequester.requestFocus() }
            }
        }
        val focusModifier = Modifier.focusRequester(resolvedFocusRequester)
        if (shouldUseNativeMiuixSearchBar(uiStyle)) {
            MiuixAdaptiveSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                modifier = modifier.then(focusModifier),
                placeholder = placeholder,
                containerColor = resolvedContainerColor,
                height = resolvedHeight,
                forceExpandedInput = true,
                onSearch = onSearch,
                interactionSource = interactionSource,
            )
            return
        }
        // 顶栏固定高度不能用 OutlinedTextField：默认 contentPadding 会把字裁掉（平板尤其明显）。
        // 用 BasicTextField + 可选聚焦描边，保证 44–56dp 内文字完整可见。
        if (topBarChrome) {
            val textStyle = MaterialTheme.typography.bodyLarge
            val resolvedInteraction = interactionSource ?: remember { MutableInteractionSource() }
            val isFocused by resolvedInteraction.collectIsFocusedAsState()
            val fieldShape = searchBarShape
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = modifier
                    .fillMaxWidth()
                    .height(resolvedHeight)
                    .clip(fieldShape)
                    .background(resolvedContainerColor, fieldShape)
                    .then(
                        if (isFocused) {
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = fieldShape,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .then(focusModifier),
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                interactionSource = resolvedInteraction,
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                        if (showClearAction && query.isNotEmpty()) {
                            IconButton(
                                onClick = onClear,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                },
            )
            return
        }
        val textStyle = MaterialTheme.typography.bodyMedium
        val sizeModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = resolvedHeight)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier
                .then(sizeModifier)
                .then(focusModifier),
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = textStyle,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = if (showClearAction && query.isNotEmpty()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            textStyle = textStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            shape = searchBarShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = resolvedContainerColor,
                unfocusedContainerColor = resolvedContainerColor,
                disabledContainerColor = resolvedContainerColor,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            interactionSource = interactionSource,
        )
        return
    }

    if (shouldUseNativeMiuixSearchBar(uiStyle)) {
        MiuixAdaptiveSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = modifier,
            placeholder = placeholder,
            containerColor = resolvedContainerColor,
            height = resolvedHeight,
            forceExpandedInput = forceExpandedInput,
            onSearch = { onSearch() },
            interactionSource = interactionSource,
        )
        return
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(resolvedHeight)
            .clip(searchBarShape)
            .background(resolvedContainerColor, searchBarShape),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
                if (showClearAction && query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun AppSearchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    containerColor: Color = Color.Unspecified,
) {
    val uiStyle = LocalAppUiStyle.current
    val colorScheme = MaterialTheme.colorScheme
    val visualSpec = rememberAdaptiveListVisualCapabilities().componentSpec
    val resolvedContainerColor = resolveAdaptiveSearchBarContainerOverride(
        requestedColor = containerColor,
        defaultColor = resolveAdaptiveSearchBarContainerColor(
            uiStyle = uiStyle,
            colorScheme = colorScheme,
            globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current,
        ),
    )
    val contentColor = AppSurfaceTokens.searchContent()
    val cornerRadius = visualSpec.searchBarCornerRadiusDp.dp
    val searchIcon = Icons.Default.Search

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = visualSpec.searchBarHeightDp.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(resolvedContainerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = searchIcon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}

@Composable
fun AdaptiveTextFieldRenderer(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
) {
    val uiStyle = LocalAppUiStyle.current
    if (shouldUseNativeMiuixSearchBar(uiStyle)) {
        Column(modifier = modifier.fillMaxWidth()) {
            MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = label ?: placeholder.orEmpty(),
                useLabelAsPlaceholder = label == null,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                colors = MiuixTextFieldDefaults.textFieldColors(
                    borderColor = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                ),
            )
            supportingText?.invoke()
        }
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText
    )
}

@Composable
private fun MiuixAdaptiveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    containerColor: Color,
    height: androidx.compose.ui.unit.Dp,
    forceExpandedInput: Boolean = false,
    onSearch: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
) {
    if (forceExpandedInput) {
        InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { onSearch() },
            expanded = true,
            onExpandedChange = {},
            modifier = modifier
                .fillMaxWidth()
                .height(height),
            label = placeholder,
            color = containerColor,
            interactionSource = interactionSource,
        )
        return
    }
    var expanded by rememberSaveable(query.isNotBlank()) {
        mutableStateOf(query.isNotBlank())
    }
    InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = expanded || query.isNotBlank(),
        onExpandedChange = { expanded = it },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        label = placeholder,
        color = containerColor,
        interactionSource = interactionSource,
    )
}
