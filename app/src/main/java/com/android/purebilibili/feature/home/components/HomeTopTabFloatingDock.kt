package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.blur.Backdrop

internal fun shouldHomeTopTabUseFloatingBottomBarDock(
    skinPlainStyle: Boolean,
    hasSkinStickerIcons: Boolean,
    presentation: AppTopTabPresentation,
    liquidGlassEnabled: Boolean,
    selectionIndicatorStyle: HomeSelectionIndicatorStyle,
): Boolean {
    if (skinPlainStyle || hasSkinStickerIcons) return false
    if (!liquidGlassEnabled) return false
    val usesLegacyUnderline = presentation == AppTopTabPresentation.MATERIAL_UNDERLINE &&
        selectionIndicatorStyle == HomeSelectionIndicatorStyle.MD3_UNDERLINE
    return !usesLegacyUnderline
}

internal fun shouldHomeTopTabChromeDrawOuterShell(
    drawOuterChrome: Boolean,
    innerOwnsFloatingDock: Boolean,
): Boolean = drawOuterChrome && !innerOwnsFloatingDock

internal fun shouldUseOfficialMd3HomeTopToolbar(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean,
): Boolean = uiStyle == AppUiStyle.MATERIAL3 && !liquidGlassEnabled

internal fun shouldUseOfficialMiuixHomeTopTabs(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean,
): Boolean = uiStyle == AppUiStyle.MIUIX && !liquidGlassEnabled

/** Glass exports the selected glyph in the exact accent, matching the bottom dock.
 * Its translucent shell alone is not the composited backdrop for contrast checks.
 * Native renderers retain the existing readable-foreground policy.
 */
internal fun resolveHomeTopTabSelectedContentColor(
    accent: Color,
    shellColor: Color,
    liquidGlassEnabled: Boolean,
): Color = if (liquidGlassEnabled) accent else resolveReadableBottomBarSkinForeground(
    preferredColor = accent,
    backgroundColor = shellColor,
)

/** Uses shared dock width policy with the top shell's own corner geometry. */
internal fun resolveHomeTopTabFloatingDockWidth(
    containerWidth: Dp,
    itemCount: Int,
    labelMode: Int,
    shellHeight: Dp = resolveHomeTopDockShellHeight(isFloatingStyle = true),
): Dp = resolveBiliPaiFloatingBottomBarWidth(
    containerWidth = containerWidth,
    itemCount = itemCount,
    minEdgePadding = resolveAndroidNativeBottomBarTuning(
        blurEnabled = true,
        darkTheme = false,
    ).outerHorizontalPaddingDp.dp,
    labelMode = labelMode,
    cornerRadius = shellHeight / 2,
)

/** Top category navigation matching the active bottom-bar renderer contract. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopTabFloatingDock(
    categories: List<String>,
    categoryKeys: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onReselected: () -> Unit,
    showIcon: Boolean,
    showText: Boolean,
    iconFamily: AppSemanticIconFamily,
    itemWidth: Dp?,
    dockHeight: Dp,
    labelFontSize: TextUnit,
    liquidGlassEffectsEnabled: Boolean,
    backdropBlurEnabled: Boolean = liquidGlassEffectsEnabled,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    containerChromeVisible: Boolean = true,
    miuixBackdrop: Backdrop?,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    liquidGlassTuning: LiquidGlassTuning,
    indicatorPositionProvider: (() -> Float)?,
    isScrollInProgressProvider: () -> Boolean,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState? = null,
    horizontalPadding: Dp = AppSpacingTokens.ExtraSmall,
) {
    if (categories.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(0.01f)
    if (
        shouldUseOfficialMd3HomeTopToolbar(
            uiStyle = LocalAppUiStyle.current,
            liquidGlassEnabled = liquidGlassEffectsEnabled,
        )
    ) {
        val useBlur = backdropBlurEnabled && hazeState != null
        val toolbarShape = FloatingToolbarDefaults.ContainerShape
        val toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = if (useBlur) Color.Transparent else Color.Unspecified,
        )
        val toolbarModifier = if (backdropBlurEnabled && hazeState != null) {
            Modifier.unifiedBlur(
                hazeState = hazeState,
                shape = toolbarShape,
                surfaceType = BlurSurfaceType.HEADER,
                motionTier = motionTier,
                isScrolling = false,
                isTransitionRunning = isTransitionRunning,
                forceLowBudget = forceLowBlurBudget,
            )
        } else {
            Modifier
        }
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            val toolbarContent: @Composable RowScope.() -> Unit = {
                categories.forEachIndexed { index, label ->
                    val selected = selectedIndex == index
                    val categoryKey = categoryKeys.getOrNull(index) ?: label
                    val icon: @Composable () -> Unit = {
                        AppIcon(
                            imageVector = resolveTopTabCategoryIcon(
                                categoryKey = categoryKey,
                                iconFamily = AppSemanticIconFamily.MATERIAL,
                                selected = selected,
                            ),
                            contentDescription = if (showText) null else label,
                            modifier = if (showIcon && !showText) {
                                Modifier.size(
                                    resolveHomeTopDockIconSizeDp(1, liquidGlassEnabled = false).dp,
                                )
                            } else {
                                Modifier
                            },
                        )
                    }
                    val onClick = {
                        if (selected) onReselected() else onSelected(index)
                    }
                    when {
                        showIcon && showText && selected -> FilledTonalButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.appElasticPress(),
                        ) {
                            icon()
                            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                            AppText(text = label, maxLines = 1, tapToCopyEnabled = false)
                        }
                        showIcon && selected -> FilledTonalIconButton(onClick = onClick, modifier = Modifier.appElasticPress()) { icon() }
                        showIcon -> IconButton(onClick = onClick, modifier = Modifier.appElasticPress()) { icon() }
                        selected -> FilledTonalButton(onClick = onClick, modifier = Modifier.appElasticPress()) {
                            AppText(text = label, maxLines = 1, tapToCopyEnabled = false)
                        }
                        else -> TextButton(onClick = onClick, modifier = Modifier.appElasticPress()) {
                            AppText(text = label, maxLines = 1, tapToCopyEnabled = false)
                        }
                    }
                }
            }
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = toolbarModifier,
                colors = toolbarColors,
                shape = toolbarShape,
                content = toolbarContent,
            )
        }
        return
    }
    val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background())
    val isBlurActive = liquidGlassEffectsEnabled || (miuixBackdrop != null)
    val bottomBarTuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = isBlurActive,
        darkTheme = isDarkTheme,
    )
    val bottomBarContainerColor = resolveAndroidNativeFloatingBottomBarContainerColor(
        surfaceColor = AppSurfaceTokens.surfaceContainer(),
        tuning = bottomBarTuning,
        glassEnabled = liquidGlassEffectsEnabled,
        blurEnabled = isBlurActive,
        blurIntensity = currentUnifiedBlurIntensity(),
        liquidGlassPreset = liquidGlassPreset,
        liquidGlassTuning = liquidGlassTuning,
        globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current,
    )
    val shellColor = resolveBiliPaiBottomBarShellColor(
        containerColor = bottomBarContainerColor,
        liquidGlassEnabled = liquidGlassEffectsEnabled,
        darkTheme = isDarkTheme,
        liquidGlassTuning = liquidGlassTuning,
    )
    val selectedContentColor = resolveHomeTopTabSelectedContentColor(
        accent = AppContentColors.selectionAccent(liquidGlassEffectsEnabled),
        shellColor = shellColor,
        liquidGlassEnabled = liquidGlassEffectsEnabled,
    )
    val unselectedContentColor = resolveReadableBottomBarSkinForeground(
        preferredColor = AppContentColors.secondaryText(),
        backgroundColor = shellColor,
    )
    // Text-only glass shares segmented capsule geometry; native and icon navigation keep their original insets.
    val useTextSegmentedGeometry = liquidGlassEffectsEnabled && showText && !showIcon
    BottomBarFloatingSegmentedControl(
        items = categories,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        modifier = modifier,
        enabled = true,
        itemWidth = itemWidth,
        height = dockHeight,
        geometryMode = if (useTextSegmentedGeometry) {
            FloatingBottomBarGeometryMode.Segmented
        } else {
            FloatingBottomBarGeometryMode.TopNavigation
        },
        indicatorHeight = (dockHeight - AppSpacingTokens.ExtraSmall * 2).coerceAtLeast(0.dp),
        labelFontSize = labelFontSize,
        containerHorizontalPadding = horizontalPadding,
        containerVerticalPadding = AppSpacingTokens.ExtraSmall,
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
        dragSelectionEnabled = true,
        longPressDragSelectionEnabled = scrollState != null,
        miuixBackdrop = miuixBackdrop,
        containerColorOverride = shellColor,
        selectedTextColorOverride = selectedContentColor,
        unselectedTextColorOverride = unselectedContentColor,
        indicatorPositionProvider = indicatorPositionProvider,
        externalPagerMotionEffectsEnabled = indicatorPositionProvider != null,
        isScrollInProgressProvider = isScrollInProgressProvider,
        onIndicatorPositionChanged = null,
        liquidGlassTuningOverride = liquidGlassTuning,
        onItemReselected = onReselected,
        scrollState = scrollState,
        adaptIndicatorToLabel = showText,
        itemContent = { index, label, selected ->
            val contentColor = LocalFloatingBottomBarContentColor.current
            val selectionScale = LocalFloatingBottomBarItemSelectionScale.current
            // Glass renders this slot twice: an idle rail and a lens-clipped active
            // copy. The active copy must stay filled even before the pager commits
            // the hovered tab; its visible selection is owned by the moving mask.
            // Non-glass rails retain the native selected-index contract.
            val iconSelected = if (liquidGlassEffectsEnabled) {
                LocalFloatingBottomBarActiveContent.current
            } else selected
            val categoryKey = categoryKeys.getOrNull(index) ?: label
            if (showIcon) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        val scale = selectionScale()
                        scaleX = scale
                        scaleY = scale
                        clip = false
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = resolveTopTabCategoryIcon(
                            categoryKey = categoryKey,
                            iconFamily = iconFamily,
                            selected = iconSelected,
                        ),
                        contentDescription = label,
                        // Standalone glass icons use AppIcon's default dimensions, just
                        // like the bottom dock. Keep compact icon+text and native sizing.
                        modifier = if (liquidGlassEffectsEnabled && !showText) {
                            Modifier
                        } else {
                            Modifier.size(
                                resolveHomeTopDockIconSizeDp(
                                    if (showText) 0 else 1,
                                    liquidGlassEnabled = liquidGlassEffectsEnabled,
                                ).dp,
                            )
                        },
                        tint = contentColor,
                    )
                }
            }
            if (showText) {
                AppText(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    fontSize = labelFontSize,
                    lineHeight = resolveFloatingDockLabelLineHeight(
                        showIcon = showIcon,
                        fontScale = fontScale,
                    ),
                    // Pure text already shares bottom-dock typography and measured slots.
                    // Glass scrolls overflow instead of shrinking these labels below it.
                    autoSize = if (liquidGlassEffectsEnabled && !showIcon) null else {
                        TextAutoSize.StepBased(
                            minFontSize = (9f / fontScale).sp,
                            maxFontSize = labelFontSize,
                            stepSize = (0.5f / fontScale).sp,
                        )
                    },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    tapToCopyEnabled = false,
                )
            }
        },
    )
}
