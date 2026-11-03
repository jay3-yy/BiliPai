package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppContentColors
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.clipLiquidDockScrollContent
import com.android.purebilibili.feature.home.components.miuix.DampedDragTrackingMode
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

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
    liquidGlassAlignShellStart: Boolean = false,
    liquidGlassLabelHorizontalPadding: Dp? = null,
    liquidGlassMeasurementLabels: List<String>? = null,
    liquidGlassAnimateLabelWidthChanges: Boolean = false,
    isPagerDraggingProvider: (() -> Boolean)? = null,
    pagerTargetPositionProvider: (() -> Float)? = null,
    isPagerNavigatingProvider: (() -> Boolean)? = null,
    itemContent: (@Composable ColumnScope.(index: Int, label: String, selected: Boolean) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    // Consume the host's resolved material immediately: per-control DataStore collectors
    // otherwise leave empty chrome and can disagree with the surrounding theme at mount.
    val renderConfig = LocalLiquidGlassRenderConfig.current
    val liquidGlassEnabled = resolveSegmentedControlLiquidGlassEnabled(
        storedLiquidGlassEnabled = false, // Legacy independent toggle is intentionally ignored.
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
        supportsIndependentLiquidGlass = false,
        androidNativeLiquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
    )
    val liquidGlassTuning = liquidGlassTuningOverride ?: renderConfig.tuning
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
    val parentGlass = com.android.purebilibili.core.ui.blur.LocalAppGlassContainer.current
        .takeIf { liquidGlassEnabled }
    val suppliedBackdrop = if (parentGlass != null) parentGlass.backdrop else miuixBackdrop ?: wallpaperBackdrop
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
        (suppliedBackdrop ?: localBackdrop)?.let {
            com.android.purebilibili.feature.home.components.liquid.rememberStableBackdrop(it)
        }
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
    // 文字标签统一由全局开关控制；自定义图标内容仍由入口声明是否有文字测量依据。
    // 原生材质不读取此策略，继续使用原来的槽位几何。
    val dynamicLengthEnabled = com.android.purebilibili.core.ui.animation.LocalLiquidIndicatorDynamicLengthEnabled.current
    val contentAdaptiveIndicatorEnabled = liquidGlassEnabled && dynamicLengthEnabled &&
        (itemContent == null || adaptIndicatorToLabel)
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
    // Badge reservations apply only to glass geometry; actual labels and native sizing stay intact.
    val measurementLabels = liquidGlassMeasurementLabels?.takeIf { it.size == items.size } ?: items
    val measuredIndicatorContentWidths = remember(measurementLabels, labelStyle, density, textMeasurer, contentAdaptiveIndicatorEnabled, effectiveIndicatorHeight, labelHorizontalPadding) {
        if (!contentAdaptiveIndicatorEnabled) null else measurementLabels.map { label ->
            with(density) {
                (textMeasurer.measure(AnnotatedString(label), labelStyle, maxLines = 1,
                    softWrap = false).size.width.toDp() +
                    labelHorizontalPadding * 2)
                    .coerceAtLeast(effectiveIndicatorHeight * FLOATING_DOCK_MIN_INDICATOR_ASPECT)
            }
        }
    }
    val animateLabelWidths = contentAdaptiveIndicatorEnabled && liquidGlassAnimateLabelWidthChanges &&
        com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val indicatorContentWidths = if (animateLabelWidths) {
        measuredIndicatorContentWidths?.map { width ->
            animateDpAsState(
                targetValue = width,
                animationSpec = tween(180, easing = AppMotionEasing.M1Standard),
                label = "glassAsyncBadgeIndicatorWidth",
            ).value
        }
    } else measuredIndicatorContentWidths
    // Only glass tightens shell ends; native sizing and authored narrow insets stay intact.
    val horizontalPadding = com.android.purebilibili.core.ui.resolveLiquidGlassIndicatorHorizontalInsetDp(
        requestedInsetDp = containerHorizontalPadding.value,
        liquidGlassEnabled = liquidGlassEnabled,
    ).dp.coerceAtLeast(0.dp)
    val verticalPadding = containerVerticalPadding.coerceIn(0.dp, effectiveHeight / 2)
    val contentWidth = effectiveItemWidth?.let { it * itemCount + horizontalPadding * 2 }
    // A scroll state enables overflow; it must not stretch a short glass shell to
    // the whole available viewport. width() still respects the parent's maximum.
    val widthModifier = if (contentWidth != null) {
        modifier.width(contentWidth)
    } else {
        modifier
    }
    // 仅页面显式要求时补偿动态长度裁掉的首槽空白，外壳起点不随计数与字号变化。
    // 滚动量在布局阶段读取，原生材质与其他 dock 的默认几何不变。
    val rootModifier = if (liquidGlassAlignShellStart && contentAdaptiveIndicatorEnabled && effectiveItemWidth != null) {
        widthModifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val leadingInsetPx = indicatorContentWidths?.firstOrNull()?.let { width ->
                resolveFloatingDockScrollableShellInsetPx(
                    slotWidthPx = effectiveItemWidth.toPx(),
                    capsuleWidthPx = width.toPx(),
                    distanceFromContentEdgePx = scrollState?.value?.toFloat() ?: 0f,
                ).roundToInt()
            } ?: 0
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(-leadingInsetPx, 0)
            }
        }
    } else widthModifier
    val selectedIndexState = rememberUpdatedState(safeSelectedIndex)
    val onSelectedState = rememberUpdatedState(onSelected)
    val enabledState = rememberUpdatedState(enabled)
    val onItemReselectedState = rememberUpdatedState(onItemReselected)
    // Glass callers own the expanded rounded liquidDockViewport, and the scroll node
    // below reserves captureInsets for press/velocity bloom. Clipping again to the resting
    // shell here cuts off that bloom. Keep native clipping and all layout/hit bounds intact.
    BoxWithConstraints(
        modifier = rootModifier.height(effectiveHeight).then(
            if (scrollState != null && !liquidGlassEnabled) Modifier.drawWithContent {
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
                    .layout { measurable, constraints ->
                        // 可滚动外壳与内容自适应胶囊共用首尾边界，原生路径不裁减槽位。
                        val slotWidthPx = with(density) { fittedSegmentedIndicatorWidth.toPx() }
                        fun inset(width: Dp?, distancePx: Int): Int = if (!contentAdaptiveIndicatorEnabled || width == null) 0 else
                            resolveFloatingDockScrollableShellInsetPx(
                                slotWidthPx, with(density) { width.toPx() }, distancePx.toFloat(),
                            ).roundToInt()
                        val leading = inset(indicatorContentWidths?.firstOrNull(), scrollState.value)
                        val trailing = inset(indicatorContentWidths?.lastOrNull(), scrollState.maxValue - scrollState.value)
                        val shellWidth = (constraints.maxWidth - leading - trailing).coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minWidth = shellWidth, maxWidth = shellWidth))
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(leading, 0)
                        }
                    }
                    // The viewport shell is outside the scrolling labels, but shares
                    // their actual dock gesture spring rather than a refresh offset.
                    .graphicsLayer { translationY = dockTranslationProvider.value() },
                liquidGlassTuning = liquidGlassTuning,
                isScrollInProgressProvider = isScrollInProgressProvider,
            ) {}
        }
        val floatingModifier = if (scrollState != null && contentWidth != null) {
            Modifier
                .then(
                    if (liquidGlassEnabled) Modifier.clipLiquidDockScrollContent(
                        leadingInsetPx = {
                            if (!contentAdaptiveIndicatorEnabled) 0f else indicatorContentWidths?.firstOrNull()?.let {
                                resolveFloatingDockScrollableShellInsetPx(
                                    with(density) { fittedSegmentedIndicatorWidth.toPx() },
                                    with(density) { it.toPx() }, scrollState.value.toFloat(),
                                ).roundToInt().toFloat()
                            } ?: 0f
                        },
                        trailingInsetPx = {
                            if (!contentAdaptiveIndicatorEnabled) 0f else indicatorContentWidths?.lastOrNull()?.let {
                                resolveFloatingDockScrollableShellInsetPx(
                                    with(density) { fittedSegmentedIndicatorWidth.toPx() },
                                    with(density) { it.toPx() },
                                    (scrollState.maxValue - scrollState.value).toFloat(),
                                ).roundToInt().toFloat()
                            } ?: 0f
                        },
                        translationYPx = { dockTranslationProvider.value() },
                        verticalOverflowPx = with(density) { captureInsets.verticalDp.dp.toPx() },
                    ) else Modifier
                )
                // 左右按外壳收口，上下与采样层共享形变余量，避免放大指示器被静态行高截断。
                // 不改变槽位尺寸、滚动范围或原生路径。
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
            enabled = enabled,
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
            isPagerDraggingProvider = isPagerDraggingProvider,
            pagerTargetPositionProvider = pagerTargetPositionProvider,
            isPagerNavigatingProvider = isPagerNavigatingProvider,
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
                    enabled = enabled,
                    onClick = {
                        if (enabled) onSelected(index)
                    },
                    selected = selected,
                    itemIndex = index,
                    iconCrossScaleEnabled = renderConfig.navigationIconCrossScaleEnabled,
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
