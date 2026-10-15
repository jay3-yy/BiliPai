package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
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
    scrollState: ScrollState? = null,
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
    onIndicatorDragStateChanged: ((Boolean) -> Unit)? = null,
    isScrollInProgressProvider: () -> Boolean = { false },
    externalPagerMotionEffectsEnabled: Boolean = false,
    liquidGlassTuningOverride: LiquidGlassTuning? = null,
    onItemReselected: (() -> Unit)? = null,
    tapPressRefractionEnabled: Boolean = true,
    indicatorIdleSurfaceColorOverride: Color? = null,
    geometryMode: FloatingBottomBarGeometryMode = FloatingBottomBarGeometryMode.Segmented,
    adaptIndicatorToLabel: Boolean = true,
    liquidGlassLabelHorizontalPadding: Dp? = null,
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
    val selectedTextColor = selectedTextColorOverride
        ?: AppContentColors.selectionAccent(liquidGlassEnabled)
    // 液态玻璃模式下与首页底栏保持一致，使用纯净中性玻璃材质，不走主题色
    val themedIndicatorSurface = indicatorIdleSurfaceColorOverride
    val unselectedTextColor = unselectedTextColorOverride
        ?: resolveLiquidSegmentedControlUnselectedTextColor(
            onSurface = AppContentColors.primaryText(),
            enabled = enabled,
        )
    val shellColor = containerColorOverride ?: resolveBiliPaiBottomBarShellColor(
        containerColor = AppSurfaceTokens.cardContainer(),
        liquidGlassEnabled = liquidGlassEnabled,
        darkTheme = isDarkTheme,
        liquidGlassTuning = liquidGlassTuning,
    )
    // Material boundary: only glass may reuse the wallpaper recording. Native MD3/Miuix
    // renderers retain their original colors, geometry and source selection.
    val wallpaperSource = LocalGlobalWallpaperBackdropSource.current
    val wallpaperBackdrop = if (liquidGlassEnabled && LocalGlobalWallpaperBackdropVisible.current) {
        wallpaperSource?.takeIf { it.isReady }?.backdrop
    } else null
    // Inline docks must not sample an ancestor page source that also records the dock.
    // The root wallpaper-only source is independent, contains real pixels at this position,
    // and remains mounted across navigation. Prefer an explicitly supplied sibling source.
    val suppliedBackdrop = miuixBackdrop ?: wallpaperBackdrop
    // A supplied page backdrop already contains the pixels behind this dock. Combining it
    // with a local source can make MIUI's native background-blur graph sample itself when
    // nested pages mount another backdrop (for example Bangumi/Film), overflowing RenderThread.
    // Keep the local source strictly as a fallback so visual blur remains real in both paths.
    val localBackdrop = if (liquidGlassEnabled && suppliedBackdrop == null) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val effectiveBackdrop = if (liquidGlassEnabled) {
        suppliedBackdrop ?: localBackdrop
    } else {
        null
    }
    val floatingMode = if (effectiveBackdrop != null) {
        FloatingBottomBarMode.LiquidGlass
    } else {
        FloatingBottomBarMode.None
    }
    val effectiveHeight = height.coerceAtLeast(0.dp)
    // Glass reuses the bottom dock's inset and contour; disabled renderers retain caller geometry.
    val effectiveGeometryMode = if (liquidGlassEnabled && geometryMode == FloatingBottomBarGeometryMode.Segmented) {
        FloatingBottomBarGeometryMode.CompactDock
    } else {
        geometryMode
    }
    val effectiveIndicatorHeight = if (liquidGlassEnabled) {
        resolveBiliPaiBottomBarIndicatorHeight(effectiveHeight)
    } else {
        indicatorHeight
    }
    val effectiveItemWidth = itemWidth?.coerceAtLeast(0.dp)
    // This wrapper can also render without glass. Content-width capsules and shell trimming
    // belong exclusively to glass; disabled renderers retain their established slot geometry.
    val contentAdaptiveIndicatorEnabled = adaptIndicatorToLabel && liquidGlassEnabled
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val resolvedLabelFontSize = if (itemContent == null) {
        com.android.purebilibili.core.ui.appTabLabelFontSize(labelFontSize)
    } else labelFontSize
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = resolvedLabelFontSize,
        fontWeight = if (itemContent == null) FontWeight.SemiBold else FontWeight.Bold,
    )
    val labelHorizontalPadding = liquidGlassLabelHorizontalPadding
        ?: com.android.purebilibili.core.ui.components.resolveLiquidGlassTabSlotHorizontalPadding(itemCount)
    val indicatorContentWidths = remember(items, labelStyle, density, textMeasurer, contentAdaptiveIndicatorEnabled, effectiveIndicatorHeight, labelHorizontalPadding) {
        if (!contentAdaptiveIndicatorEnabled) null else items.map { label ->
            with(density) {
                (textMeasurer.measure(AnnotatedString(label), labelStyle, maxLines = 1,
                    softWrap = false).size.width.toDp() +
                    labelHorizontalPadding * 2)
                    .coerceAtLeast(effectiveIndicatorHeight)
            }
        }
    }
    val horizontalPadding = containerHorizontalPadding.coerceAtLeast(0.dp)
    val verticalPadding = containerVerticalPadding.coerceIn(0.dp, effectiveHeight / 2)
    val contentWidth = effectiveItemWidth?.let { it * itemCount + horizontalPadding * 2 }
    // A scroll state enables overflow; it must not stretch a short glass shell to
    // the whole available viewport. width() still respects the parent's maximum.
    val rootModifier = if (contentWidth != null) {
        modifier.width(contentWidth)
    } else {
        modifier
    }
    val selectedIndexState = rememberUpdatedState(safeSelectedIndex)
    val onSelectedState = rememberUpdatedState(onSelected)
    val enabledState = rememberUpdatedState(enabled)
    val onItemReselectedState = rememberUpdatedState(onItemReselected)
    // The shell owns layout and input. Cross-axis scroll clipping already leaves
    // drawing room for the lens; press bloom must never enlarge the hit viewport.
    BoxWithConstraints(
        modifier = rootModifier.height(effectiveHeight).then(
            if (scrollState != null) Modifier.drawWithContent {
                // Keep the scrolling lens inside the fixed dock horizontally. Its vertical
                // press bloom may still overflow without covering adjacent avatar/actions.
                clipRect(top = -size.height, bottom = size.height * 2f) {
                    this@drawWithContent.drawContent()
                }
            } else Modifier
        )
    ) {
        val indicatorWidthDp = when {
            effectiveItemWidth != null -> effectiveItemWidth.value
            constraints.hasBoundedWidth ->
                resolveFloatingDockSlotWidthPx(maxWidth.value, horizontalPadding.value, itemCount)
            else -> effectiveIndicatorHeight.value * FLOATING_DOCK_MIN_INDICATOR_ASPECT
        }
        val fittedSegmentedIndicatorWidth = if (contentAdaptiveIndicatorEnabled ||
            effectiveGeometryMode == FloatingBottomBarGeometryMode.TopNavigation ||
            effectiveGeometryMode == FloatingBottomBarGeometryMode.CompactDock
        ) {
            // Even a one-category top dock follows the shell inset, without the
            // compact segmented control's independent width/aspect cap.
            indicatorWidthDp.dp
        } else {
            resolveSegmentedControlIndicatorWidthDp(
                slotWidthDp = indicatorWidthDp,
                indicatorHeightDp = effectiveIndicatorHeight.value,
                itemCount = itemCount,
            ).dp
        }
        val captureInsets = resolveFloatingDockCaptureInsets(
            shellHeightDp = effectiveHeight.value,
            requestedIndicatorHeightDp = effectiveIndicatorHeight.value,
            indicatorWidthDp = fittedSegmentedIndicatorWidth.value,
            geometryMode = effectiveGeometryMode,
        )
        if (effectiveBackdrop != null && suppliedBackdrop == null && localBackdrop != null) {
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
        val dockTranslationProvider = remember { mutableStateOf<() -> Float>({ 0f }) }
        if (scrollState != null) {
            BottomBarMatchedLiquidDock(
                backdrop = effectiveBackdrop,
                containerColor = shellColor.copy(alpha = liquidGlassTuning.surfaceAlpha),
                shape = resolveSharedBottomBarCapsuleShape(),
                blurEnabled = liquidGlassEnabled,
                glassEnabled = liquidGlassEnabled,
                shellLensIntensity = resolveFloatingDockGeometryScale(effectiveHeight.value),
                blurRadius = liquidGlassTuning.backdropBlurRadius.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(effectiveHeight)
                    // The viewport shell is outside the scrolling labels, but shares
                    // their actual dock gesture spring rather than a refresh offset.
                    .graphicsLayer { translationY = dockTranslationProvider.value() },
                liquidGlassTuning = liquidGlassTuning,
                isScrollInProgressProvider = isScrollInProgressProvider,
            ) {}
        }
        val floatingModifier = if (scrollState != null && contentWidth != null) {
            Modifier
                // Expand the scroll clip in both axes, then inset its content by
                // the same amount. Slots and scroll range retain their resting geometry,
                // while the first/last indicator can bloom outside the dock ends.
                .bottomBarMatchedCaptureOverflow(
                    horizontalInset = captureInsets.horizontalDp.dp,
                    verticalInset = captureInsets.verticalDp.dp,
                )
                .horizontalScroll(scrollState)
                .padding(
                    horizontal = captureInsets.horizontalDp.dp,
                    vertical = captureInsets.verticalDp.dp,
                )
                .width(contentWidth)
                .height(effectiveHeight)
        } else {
            Modifier
                .fillMaxWidth()
                .height(effectiveHeight)
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
            modifier = floatingModifier,
            mode = floatingMode,
            colors = FloatingBottomBarColors(
                containerColor = shellColor,
                indicatorColor = selectedTextColor,
                contentColor = unselectedTextColor,
                activeContentColor = selectedTextColor,
            ),
            shellHeight = effectiveHeight,
            indicatorHeight = effectiveIndicatorHeight,
            indicatorWidth = fittedSegmentedIndicatorWidth,
            indicatorContentWidths = indicatorContentWidths,
            geometryMode = effectiveGeometryMode,
            indicatorPositionProvider = indicatorPositionProvider,
            contentHorizontalPadding = horizontalPadding,
            contentVerticalPadding = verticalPadding,
            tapPressRefractionEnabled = tapPressRefractionEnabled,
            indicatorIdleSurfaceColorOverride = themedIndicatorSurface,
            isScrollInProgressProvider = isScrollInProgressProvider,
            // A scrolling rail may explicitly reserve horizontal drags for browsing its labels.
            // Forward that choice rather than silently turning indicator selection back on.
            dragSelectionEnabled = dragSelectionEnabled && enabled && itemCount > 1,
            longPressDragSelectionEnabled =
                longPressDragSelectionEnabled && enabled && itemCount > 1,
            dragTrackingMode = DampedDragTrackingMode.SPRING,
            onIndicatorPositionChanged = onIndicatorPositionChanged,
            onIndicatorDragStateChanged = onIndicatorDragStateChanged,
            externalPagerMotionEffectsEnabled = externalPagerMotionEffectsEnabled,
            liquidGlassTuning = liquidGlassTuning,
            drawShell = scrollState == null,
            onDockTranslationProviderChanged = if (scrollState != null) {
                { dockTranslationProvider.value = it }
            } else null,
            dragScrollState = scrollState,
            dragScrollViewportWidthPx = { constraints.maxWidth.toFloat() },
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
                        val allowUnboundedLabel = allowLabelOverflow && scrollState == null
                        AppText(
                            text = label,
                            modifier = if (allowUnboundedLabel) {
                                Modifier.wrapContentWidth(
                                    align = Alignment.CenterHorizontally,
                                    unbounded = true,
                                )
                            } else {
                                Modifier
                            },
                            color = contentColor,
                            fontSize = resolvedLabelFontSize,
                            fontWeight = if (selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = if (allowUnboundedLabel) {
                                TextOverflow.Visible
                            } else {
                                TextOverflow.Ellipsis
                            },
                        )
                    }
                }
            }
        }
    }
}
