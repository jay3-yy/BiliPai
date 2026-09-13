package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.home.components.miuix.DampedDragTrackingMode
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.flow.map

/**
 * Reuse wrapper around [FloatingBottomBar]. No local drawBackdrop / lens / vibrancy.
 * Pager follow and drag flags are forwarded into the same dock implementation.
 */
@Composable
internal fun BottomBarFloatingSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    itemWidth: Dp?,
    height: Dp,
    indicatorHeight: Dp,
    labelFontSize: TextUnit,
    allowLabelOverflow: Boolean = false,
    containerHorizontalPadding: Dp,
    containerVerticalPadding: Dp,
    liquidGlassEffectsEnabled: Boolean,
    dragSelectionEnabled: Boolean,
    longPressDragSelectionEnabled: Boolean,
    miuixBackdrop: Backdrop?,
    containerColorOverride: Color? = null,
    selectedTextColorOverride: Color?,
    unselectedTextColorOverride: Color?,
    indicatorPositionProvider: (() -> Float)?,
    onIndicatorPositionChanged: ((Float) -> Unit)?,
    isScrollInProgressProvider: () -> Boolean = { false },
    externalPagerMotionEffectsEnabled: Boolean = false,
    liquidGlassTuningOverride: LiquidGlassTuning? = null,
    onItemReselected: (() -> Unit)? = null,
    tapPressRefractionEnabled: Boolean = true,
    indicatorIdleSurfaceColorOverride: Color? = null,
    geometryMode: FloatingBottomBarGeometryMode = FloatingBottomBarGeometryMode.Segmented,
    itemContent: (@Composable ColumnScope.(index: Int, label: String, selected: Boolean) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val homeSettings by SettingsManager
        .getHomeSettings(context)
        .map { it as HomeSettings? }
        .collectAsStateWithLifecycle(
            // Do not render provisional chrome while the persisted setting is loading.
            initialValue = null
        )
    val resolvedHomeSettings = homeSettings ?: return
    val liquidGlassEnabled = resolveSegmentedControlLiquidGlassEnabled(
        storedLiquidGlassEnabled = resolvedHomeSettings.isBottomBarLiquidGlassEnabled,
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
        supportsIndependentLiquidGlass = false,
        androidNativeLiquidGlassEnabled = resolvedHomeSettings.androidNativeLiquidGlassEnabled,
    )
    val storedLiquidGlassTuning = remember(
        resolvedHomeSettings.liquidGlassProgress,
        resolvedHomeSettings.liquidGlassAdvancedSettings,
        resolvedHomeSettings.liquidGlassReadabilityMode,
    ) {
        resolveLiquidGlassTuning(
            resolvedHomeSettings.liquidGlassProgress,
            resolvedHomeSettings.liquidGlassAdvancedSettings,
            resolvedHomeSettings.liquidGlassReadabilityMode,
        )
    }
    val liquidGlassTuning = liquidGlassTuningOverride ?: storedLiquidGlassTuning
    // The app theme can be forced independently from the system theme. Derive chrome tone from
    // the active semantic palette so a forced MD3 dark theme never receives a light-mode scrim.
    val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background())
    val itemCount = items.size
    val maxTabIndex = (itemCount - 1).coerceAtLeast(0)
    val safeSelectedIndex = selectedIndex.coerceIn(0, maxTabIndex)
    val selectedTextColor = selectedTextColorOverride ?: MaterialTheme.colorScheme.primary
    // 液态玻璃模式下与首页底栏保持一致，使用纯净中性玻璃材质，不走主题色
    val themedIndicatorSurface = indicatorIdleSurfaceColorOverride
    val unselectedTextColor = unselectedTextColorOverride
        ?: resolveLiquidSegmentedControlUnselectedTextColor(
            onSurface = MaterialTheme.colorScheme.onSurface,
            enabled = enabled,
        )
    val shellColor = containerColorOverride ?: resolveBiliPaiBottomBarShellColor(
        containerColor = AppSurfaceTokens.cardContainer(),
        liquidGlassEnabled = liquidGlassEnabled,
        darkTheme = isDarkTheme,
        liquidGlassTuning = liquidGlassTuning,
    )
    // A supplied page backdrop already contains the pixels behind this dock. Combining it
    // with a local source can make MIUI's native background-blur graph sample itself when
    // nested pages mount another backdrop (for example Bangumi/Film), overflowing RenderThread.
    // Keep the local source strictly as a fallback so visual blur remains real in both paths.
    val localBackdrop = if (liquidGlassEnabled && miuixBackdrop == null) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val effectiveBackdrop = if (liquidGlassEnabled) {
        miuixBackdrop ?: localBackdrop
    } else {
        null
    }
    val floatingMode = if (effectiveBackdrop != null) {
        FloatingBottomBarMode.LiquidGlass
    } else {
        FloatingBottomBarMode.None
    }
    val effectiveHeight = height.coerceAtLeast(0.dp)
    val effectiveItemWidth = itemWidth?.coerceAtLeast(48.dp)
    val horizontalPadding = containerHorizontalPadding.coerceAtLeast(0.dp)
    val verticalPadding = containerVerticalPadding.coerceIn(0.dp, effectiveHeight / 2)
    val rootModifier = if (effectiveItemWidth != null) {
        modifier.width(effectiveItemWidth * itemCount + horizontalPadding * 2)
    } else {
        modifier
    }
    val selectedIndexState = rememberUpdatedState(safeSelectedIndex)
    val onSelectedState = rememberUpdatedState(onSelected)
    val enabledState = rememberUpdatedState(enabled)
    val onItemReselectedState = rememberUpdatedState(onItemReselected)
    // Scrollable timeline/live rows are commonly hosted by clipping lazy/list parents. Report
    // the vertical bloom room as part of this component's layout instead of relying on draw
    // overflow alone; the 40dp dock remains centered while its refraction can reach outside it.
    val scaleOverflow = resolveCompactDockScaleOverflowDp(
        shellHeightDp = effectiveHeight.value,
        indicatorHeightDp = indicatorHeight.value,
    ).dp
    val viewportHeight = effectiveHeight + scaleOverflow * 2

    BoxWithConstraints(
        modifier = rootModifier.height(viewportHeight)
    ) {
        val indicatorWidthDp = when {
            constraints.hasBoundedWidth ->
                resolveFloatingDockSlotWidthPx(maxWidth.value, horizontalPadding.value, itemCount)
            effectiveItemWidth != null -> effectiveItemWidth.value
            else -> indicatorHeight.value * FLOATING_DOCK_MIN_INDICATOR_ASPECT
        }
        val fittedSegmentedIndicatorWidth = resolveSegmentedControlIndicatorWidthDp(
            slotWidthDp = indicatorWidthDp,
            indicatorHeightDp = indicatorHeight.value,
            itemCount = itemCount,
        ).dp
        val captureInsets = resolveFloatingDockCaptureInsets(
            shellHeightDp = effectiveHeight.value,
            requestedIndicatorHeightDp = indicatorHeight.value,
            indicatorWidthDp = fittedSegmentedIndicatorWidth.value,
            geometryMode = geometryMode,
        )
        if (effectiveBackdrop != null && miuixBackdrop == null && localBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .bottomBarMatchedCaptureOverflow(
                        horizontalInset = captureInsets.horizontalDp.dp,
                        verticalInset = captureInsets.verticalDp.dp,
                    )
                    .alpha(0f)
                    .layerBackdrop(localBackdrop)
                    .background(AppSurfaceTokens.background())
            )
        }
        FloatingBottomBar(
            selectedIndex = { selectedIndexState.value },
            onSelected = { index ->
                if (enabledState.value && index in items.indices) onSelectedState.value(index)
            },
            onReselected = {
                if (!enabledState.value) return@FloatingBottomBar
                val reselected = onItemReselectedState.value
                if (reselected != null) {
                    reselected()
                } else {
                    onSelectedState.value(selectedIndexState.value)
                }
            },
            backdrop = effectiveBackdrop,
            tabsCount = itemCount,
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeight),
            mode = floatingMode,
            colors = FloatingBottomBarColors(
                containerColor = shellColor,
                indicatorColor = selectedTextColor,
                contentColor = unselectedTextColor,
                activeContentColor = selectedTextColor,
            ),
            shellHeight = effectiveHeight,
            indicatorHeight = indicatorHeight,
            indicatorWidth = fittedSegmentedIndicatorWidth,
            geometryMode = geometryMode,
            indicatorPositionProvider = indicatorPositionProvider,
            contentHorizontalPadding = horizontalPadding,
            contentVerticalPadding = verticalPadding,
            tapPressRefractionEnabled = tapPressRefractionEnabled,
            indicatorIdleSurfaceColorOverride = themedIndicatorSurface,
            isScrollInProgressProvider = isScrollInProgressProvider,
            // No per-screen exceptions for the shared liquid dock gesture.
            dragSelectionEnabled = enabled && itemCount > 1,
            longPressDragSelectionEnabled =
                longPressDragSelectionEnabled && enabled && itemCount > 1,
            dragTrackingMode = DampedDragTrackingMode.SPRING,
            onIndicatorPositionChanged = onIndicatorPositionChanged,
            externalPagerMotionEffectsEnabled = externalPagerMotionEffectsEnabled,
            liquidGlassTuning = liquidGlassTuning,
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == safeSelectedIndex
                FloatingBottomBarItem(
                    onClick = {
                        if (enabled) onSelected(index)
                    },
                    selected = selected,
                    itemIndex = index,
                    iconCrossScaleEnabled = resolvedHomeSettings.navigationIconCrossScaleEnabled,
                ) {
                    if (itemContent != null) {
                        itemContent(index, label, selected)
                    } else {
                        val contentColor = LocalFloatingBottomBarContentColor.current
                        AppText(
                            text = label,
                            modifier = if (allowLabelOverflow) {
                                Modifier.wrapContentWidth(
                                    align = Alignment.CenterHorizontally,
                                    unbounded = true,
                                )
                            } else {
                                Modifier
                            },
                            color = contentColor,
                            fontSize = labelFontSize,
                            fontWeight = if (selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = if (allowLabelOverflow) TextOverflow.Visible else TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
