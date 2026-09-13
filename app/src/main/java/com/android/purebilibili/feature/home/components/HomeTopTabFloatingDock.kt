package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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

/** Uses the exact home bottom-bar width contract, including its screen-edge inset. */
internal fun resolveHomeTopTabFloatingDockWidth(
    containerWidth: Dp,
    itemCount: Int,
    labelMode: Int,
): Dp = resolveBiliPaiFloatingBottomBarWidth(
    containerWidth = containerWidth,
    itemCount = itemCount,
    minEdgePadding = resolveAndroidNativeBottomBarTuning(
        blurEnabled = true,
        darkTheme = false,
    ).outerHorizontalPaddingDp.dp,
    labelMode = labelMode,
    cornerRadius = resolveBiliPaiBottomBarDockHeight(searchExpanded = false) / 2,
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
) {
    if (categories.isEmpty()) return
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
                        )
                    }
                    val onClick = {
                        if (selected) onReselected() else onSelected(index)
                    }
                    when {
                        showIcon && showText && selected -> FilledTonalButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            icon()
                            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                            AppText(text = label, maxLines = 1, tapToCopyEnabled = false)
                        }
                        showIcon && selected -> FilledTonalIconButton(onClick = onClick) { icon() }
                        showIcon -> IconButton(onClick = onClick) { icon() }
                        selected -> FilledTonalButton(onClick = onClick) {
                            AppText(text = label, maxLines = 1, tapToCopyEnabled = false)
                        }
                        else -> TextButton(onClick = onClick) {
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
    val dockHeight = resolveBiliPaiBottomBarDockHeight(searchExpanded = false)
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
    val selectedContentColor = resolveReadableBottomBarSkinForeground(
        preferredColor = MaterialTheme.colorScheme.primary,
        backgroundColor = shellColor,
    )
    val unselectedContentColor = resolveReadableBottomBarSkinForeground(
        preferredColor = MaterialTheme.colorScheme.onSurfaceVariant,
        backgroundColor = shellColor,
    )
    BottomBarFloatingSegmentedControl(
        items = categories,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        modifier = modifier,
        enabled = true,
        itemWidth = itemWidth,
        height = dockHeight,
        geometryMode = FloatingBottomBarGeometryMode.TopNavigation,
        indicatorHeight = resolveBiliPaiBottomBarIndicatorHeight(dockHeight),
        labelFontSize = labelFontSize,
        containerHorizontalPadding = AppSpacingTokens.ExtraSmall,
        containerVerticalPadding = AppSpacingTokens.ExtraSmall,
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
        dragSelectionEnabled = true,
        longPressDragSelectionEnabled = false,
        miuixBackdrop = miuixBackdrop,
        containerColorOverride = shellColor,
        selectedTextColorOverride = selectedContentColor,
        unselectedTextColorOverride = unselectedContentColor,
        indicatorPositionProvider = indicatorPositionProvider,
        isScrollInProgressProvider = isScrollInProgressProvider,
        onIndicatorPositionChanged = null,
        liquidGlassTuningOverride = liquidGlassTuning,
        onItemReselected = onReselected,
        itemContent = { index, label, selected ->
            val contentColor = LocalFloatingBottomBarContentColor.current
            val selectionScale = LocalFloatingBottomBarItemSelectionScale.current
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
                            selected = selected,
                        ),
                        contentDescription = label,
                        tint = contentColor,
                    )
                }
            }
            if (showText) {
                AppText(
                    text = label,
                    color = contentColor,
                    fontSize = labelFontSize,
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
