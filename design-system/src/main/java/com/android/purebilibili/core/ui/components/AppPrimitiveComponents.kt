@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.resolveButtonContentColor
import com.android.purebilibili.core.ui.resolveAppButtonContentPadding
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixButton
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixChip
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixFloatingActionButton
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixMessageSnackbar
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixNavigationDrawerItem
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixSnackbar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import top.yukonga.miuix.kmp.window.WindowListPopup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors as MiuixButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val APP_TAB_INDICATOR_DURATION_MILLIS =
    com.android.purebilibili.core.ui.motion.AppTabIndicatorMotion.DurationMillis
private val PiliPlusIndicatorDecelerate = com.android.purebilibili.core.ui.motion.AppTabIndicatorMotion.decelerate
private val PiliPlusIndicatorAccelerate = com.android.purebilibili.core.ui.motion.AppTabIndicatorMotion.accelerate

internal data class ElasticTabIndicatorBounds(
    val leftDp: Float,
    val widthDp: Float,
)

/** Tab geometry for the underline. Material's [TabPosition] constructor is internal. */
internal data class AppTabSlot(
    val left: Dp,
    val width: Dp,
    val contentWidth: Dp,
)

internal fun TabPosition.toAppTabSlot(): AppTabSlot = AppTabSlot(
    left = left,
    width = width,
    contentWidth = contentWidth,
)

/** Measure the label independently of its equal-width touch target. */
internal fun resolveMeasuredTabContentSlots(
    slots: List<AppTabSlot>,
    labelWidths: List<Dp>?,
): List<AppTabSlot> {
    if (labelWidths == null || labelWidths.size != slots.size) return slots
    return slots.mapIndexed { index, slot ->
        slot.copy(contentWidth = labelWidths[index].coerceAtLeast(24.dp).coerceAtMost(slot.width))
    }
}

/** Visual padding only; native button sizing and touch-target handling stay intact. */
object AppButtonDefaults {
    val ContentPadding = ButtonDefaults.ContentPadding
    val TextButtonContentPadding = ButtonDefaults.TextButtonContentPadding

}

internal fun resolveElasticTabIndicatorBounds(
    position: Float,
    tabLeftsDp: List<Float>,
    tabWidthsDp: List<Float>,
    contentWidthsDp: List<Float>,
    matchContentSize: Boolean,
): ElasticTabIndicatorBounds {
    if (
        tabLeftsDp.isEmpty() ||
        tabLeftsDp.size != tabWidthsDp.size ||
        tabLeftsDp.size != contentWidthsDp.size
    ) {
        return ElasticTabIndicatorBounds(leftDp = 0f, widthDp = 0f)
    }
    val lastIndex = tabLeftsDp.lastIndex
    val safePosition = position.coerceIn(0f, lastIndex.toFloat())
    val startIndex = kotlin.math.floor(safePosition).toInt()
    val endIndex = (startIndex + 1).coerceAtMost(lastIndex)
    val fraction = safePosition - startIndex

    fun edges(index: Int): Pair<Float, Float> {
        val width = if (matchContentSize) contentWidthsDp[index] else tabWidthsDp[index]
        val left = tabLeftsDp[index] + (tabWidthsDp[index] - width) / 2f
        return left to (left + width)
    }

    val (startLeft, startRight) = edges(startIndex)
    val (endLeft, endRight) = edges(endIndex)
    val trailingEdgeProgress = 1f - kotlin.math.cos(fraction * Math.PI.toFloat() / 2f)
    val leadingEdgeProgress = kotlin.math.sin(fraction * Math.PI.toFloat() / 2f)
    val left = startLeft + (endLeft - startLeft) * trailingEdgeProgress
    val right = startRight + (endRight - startRight) * leadingEdgeProgress
    return ElasticTabIndicatorBounds(
        leftDp = left,
        widthDp = (right - left).coerceAtLeast(0f),
    )
}

@Composable
private fun AppElasticTabIndicator(
    selectedTabIndex: Int,
    tabSlots: List<AppTabSlot>,
    matchContentSize: Boolean,
    primary: Boolean,
    indicatorPositionProvider: (() -> Float)? = null,
) {
    if (tabSlots.isEmpty()) return
    val animateIndicator = !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current &&
        indicatorPositionProvider == null
    val safeIndex = selectedTabIndex.coerceIn(tabSlots.indices)
    val previousIndex = remember { mutableIntStateOf(selectedTabIndex) }
    val movingRight = remember(safeIndex) { safeIndex >= previousIndex.intValue }
    val target = tabSlots[safeIndex]
    val targetWidth = if (matchContentSize) target.contentWidth else target.width
    val targetLeft = target.left + (target.width - targetWidth) / 2f
    val targetRight = targetLeft + targetWidth
    val animatedLeft = animateDpAsState(
        targetValue = targetLeft,
        animationSpec = if (!animateIndicator) snap() else tween(
            durationMillis = APP_TAB_INDICATOR_DURATION_MILLIS,
            easing = if (movingRight) PiliPlusIndicatorAccelerate else PiliPlusIndicatorDecelerate,
        ),
        label = "appTabIndicatorLeft",
    )
    val animatedRight = animateDpAsState(
        targetValue = targetRight,
        animationSpec = if (!animateIndicator) snap() else tween(
            durationMillis = APP_TAB_INDICATOR_DURATION_MILLIS,
            easing = if (movingRight) PiliPlusIndicatorDecelerate else PiliPlusIndicatorAccelerate,
        ),
        label = "appTabIndicatorRight",
    )
    SideEffect {
        previousIndex.intValue = safeIndex
    }
    val tabLefts = remember(tabSlots) { tabSlots.map { it.left.value } }
    val tabWidths = remember(tabSlots) { tabSlots.map { it.width.value } }
    val contentWidths = remember(tabSlots) { tabSlots.map { it.contentWidth.value } }
    val indicatorModifier = Modifier
        .fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .tabIndicatorBounds {
            val followPosition = indicatorPositionProvider?.invoke()
            if (followPosition != null) {
                resolveElasticTabIndicatorBounds(
                    position = followPosition,
                    tabLeftsDp = tabLefts,
                    tabWidthsDp = tabWidths,
                    contentWidthsDp = contentWidths,
                    matchContentSize = matchContentSize,
                )
            } else {
                ElasticTabIndicatorBounds(
                    leftDp = animatedLeft.value.value,
                    widthDp = (animatedRight.value - animatedLeft.value).coerceAtLeast(0.dp).value,
                )
            }
        }
    if (primary) {
        TabRowDefaults.PrimaryIndicator(
            modifier = indicatorModifier,
            width = Dp.Unspecified,
        )
    } else {
        TabRowDefaults.SecondaryIndicator(modifier = indicatorModifier)
    }
}

@Composable
fun AppSnackbar(
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        AppMiuixSnackbar(
            modifier = modifier,
            action = action,
            content = content,
        )
        return
    }
    Snackbar(
        modifier = modifier,
        action = action,
        content = content,
    )
}

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        SnackbarHost(
            hostState = hostState,
            modifier = modifier,
        ) { data ->
            AppMiuixMessageSnackbar(
                message = data.visuals.message,
                actionLabel = data.visuals.actionLabel,
                onAction = data.visuals.actionLabel?.let { { data.performAction() } },
                withDismissAction = data.visuals.withDismissAction,
                onDismiss = { data.dismiss() },
                modifier = Modifier,
            )
        }
        return
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    )
}

private val MaterialTabHorizontalTextPadding = 16.dp

private enum class AppTabRowSlot { Tabs, Divider, Indicator }

internal data class ContentSizedTabAnchor(
    val index: Int,
    val leftPx: Int,
    val widthPx: Int,
)

/** Centers a variable-width tab in the scrollable underline rail. */
internal fun resolveContentSizedTabScrollOffsetPx(
    tabLeftPx: Int,
    tabWidthPx: Int,
    viewportWidthPx: Int,
    maxScrollPx: Int,
): Int {
    if (tabWidthPx <= 0 || viewportWidthPx <= 0) return 0
    val leadingSpacePx = (viewportWidthPx - tabWidthPx).coerceAtLeast(0) / 2
    return (tabLeftPx - leadingSpacePx).coerceIn(0, maxScrollPx.coerceAtLeast(0))
}

@Composable
private fun AppElasticScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    edgePadding: Dp,
    minTabWidth: Dp,
    scrollState: ScrollState,
    indicator: @Composable (tabSlots: List<AppTabSlot>) -> Unit,
    divider: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
) {
    // Plain holder: writing snapshot state during measure detaches nodes while
    // RectManager is still placing them (LayoutNode not found in RectList).
    val anchorHolder = remember {
        object { var value = ContentSizedTabAnchor(selectedTabIndex, 0, 0) }
    }
    LaunchedEffect(selectedTabIndex, scrollState) {
        withFrameNanos { }
        snapshotFlow { scrollState.maxValue to scrollState.viewportSize }
            .collectLatest { (maxScrollPx, viewportWidthPx) ->
                val anchor = anchorHolder.value
                val target = resolveContentSizedTabScrollOffsetPx(
                    tabLeftPx = anchor.leftPx,
                    tabWidthPx = anchor.widthPx,
                    viewportWidthPx = viewportWidthPx,
                    maxScrollPx = maxScrollPx,
                )
                if (scrollState.value != target) {
                    scrollState.scrollTo(target)
                }
            }
    }
    Surface(modifier = modifier, color = containerColor, contentColor = contentColor) {
        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.CenterStart)
                .horizontalScroll(scrollState)
                .selectableGroup()
                .clipToBounds()
        ) { constraints ->
            val paddingPx = edgePadding.roundToPx()
            val minTabWidthPx = minTabWidth.roundToPx()
            val tabMeasurables = subcompose(AppTabRowSlot.Tabs, tabs)
            val layoutHeight = tabMeasurables.maxOfOrNull {
                it.maxIntrinsicHeight(Constraints.Infinity)
            } ?: 0
            val tabConstraints = constraints.copy(
                minWidth = minTabWidthPx,
                minHeight = layoutHeight,
                maxHeight = layoutHeight,
            )
            val tabPlaceables = tabMeasurables.map { it.measure(tabConstraints) }
            val tabSlots = mutableListOf<AppTabSlot>()
            var left = edgePadding
            var layoutWidth = paddingPx * 2
            tabPlaceables.forEachIndexed { index, placeable ->
                val measurable = tabMeasurables[index]
                var contentWidth = minOf(
                    measurable.maxIntrinsicWidth(placeable.height),
                    placeable.width,
                ).toDp()
                contentWidth -= MaterialTabHorizontalTextPadding * 2
                if (contentWidth < 0.dp) contentWidth = 0.dp
                tabSlots += AppTabSlot(
                    left = left,
                    width = placeable.width.toDp(),
                    contentWidth = contentWidth,
                )
                left += placeable.width.toDp()
                layoutWidth += placeable.width
            }
            val safeIndex = selectedTabIndex.coerceIn(0, (tabSlots.size - 1).coerceAtLeast(0))
            val selected = tabSlots.getOrNull(safeIndex)
            anchorHolder.value = ContentSizedTabAnchor(
                index = safeIndex,
                leftPx = selected?.left?.roundToPx() ?: 0,
                widthPx = selected?.width?.roundToPx() ?: 0,
            )
            val dividerPlaceables = subcompose(AppTabRowSlot.Divider, divider).map { measurable ->
                measurable.measure(
                    constraints.copy(
                        minWidth = layoutWidth,
                        maxWidth = layoutWidth,
                        minHeight = 0,
                    )
                )
            }
            val indicatorPlaceables = subcompose(AppTabRowSlot.Indicator) {
                indicator(tabSlots)
            }.map { measurable ->
                measurable.measure(Constraints.fixed(layoutWidth.coerceAtLeast(0), layoutHeight.coerceAtLeast(0)))
            }
            layout(layoutWidth, layoutHeight) {
                var placeLeft = paddingPx
                tabPlaceables.forEach { placeable ->
                    placeable.placeRelative(placeLeft, 0)
                    placeLeft += placeable.width
                }
                dividerPlaceables.forEach { placeable ->
                    placeable.placeRelative(0, layoutHeight - placeable.height)
                }
                indicatorPlaceables.forEach { placeable ->
                    placeable.placeRelative(0, 0)
                }
            }
        }
    }
}

@Composable
fun AppScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    minTabWidth: Dp = resolvePiliPlusScrollableUnderlineMinWidth(),
    indicatorPositionProvider: (() -> Float)? = null,
    divider: @Composable () -> Unit = {},
    tabs: @Composable () -> Unit,
) = AppElasticScrollableTabRow(
    selectedTabIndex = selectedTabIndex,
    modifier = modifier,
    containerColor = containerColor,
    contentColor = contentColor,
    edgePadding = edgePadding,
    minTabWidth = minTabWidth,
    scrollState = rememberScrollState(),
    indicator = { tabSlots ->
        AppElasticTabIndicator(
            selectedTabIndex = selectedTabIndex,
            tabSlots = tabSlots,
            matchContentSize = true,
            primary = false,
            indicatorPositionProvider = indicatorPositionProvider,
        )
    },
    divider = divider,
    tabs = tabs,
)

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color = containerColor.copy(alpha = 0.38f),
    disabledContentColor: Color = contentColor.copy(alpha = 0.38f),
    border: BorderStroke? = null,
    defaultElevation: Dp = 0.dp,
    pressedElevation: Dp = defaultElevation,
    contentPadding: PaddingValues = appButtonPadding(AppButtonDefaults.ContentPadding),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    // MD3 保留原有反馈；非玻璃 Miuix 只使用原生按钮的按压反馈。
    val elasticFeedbackEnabled = enabled && LocalAppUiStyle.current == AppUiStyle.MIUIX &&
        LocalAppThemeConfig.current.liquidGlassEnabled
    ProvideAppElasticFeedback(enabled = elasticFeedbackEnabled) {
        val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val interactionModifier = modifier.appElasticPress(enabled = elasticFeedbackEnabled, highlightShape = shape)
            .appDesktopInteractionVisuals(resolvedInteractionSource, enabled)
        val readableContentColor = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) contentColor
            else resolveButtonContentColor(containerColor, contentColor, MaterialTheme.colorScheme)
        val miuixColors = MiuixButtonColors(
            color = containerColor,
            disabledColor = disabledContainerColor,
            contentColor = readableContentColor,
            disabledContentColor = disabledContentColor,
        )
        when (LocalAppUiStyle.current) {
            AppUiStyle.MIUIX -> AppMiuixButton(
                onClick = feedbackClick,
                modifier = interactionModifier,
                enabled = enabled,
                colors = miuixColors,
                insideMargin = contentPadding,
                interactionSource = resolvedInteractionSource,
                border = border,
                content = content,
            )
            AppUiStyle.MATERIAL3 -> Button(
                onClick = feedbackClick,
                modifier = interactionModifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = readableContentColor,
                    disabledContainerColor = disabledContainerColor,
                    disabledContentColor = disabledContentColor,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = defaultElevation,
                    pressedElevation = pressedElevation,
                ),
                border = border,
                contentPadding = resolveAppButtonContentPadding(
                    uiStyle = LocalAppUiStyle.current,
                    requested = contentPadding,
                    defaultPadding = ButtonDefaults.ContentPadding,
                ),
                interactionSource = resolvedInteractionSource,
                content = {
                    com.android.purebilibili.core.ui.ProvideAppButtonSlotPalette(
                        containerColor = if (enabled) containerColor else disabledContainerColor,
                        enabled = enabled,
                    ) { content() }
                },
            )
        }
    }
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = appButtonPadding(AppButtonDefaults.ContentPadding),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    // MD3 保留原有反馈；非玻璃 Miuix 只使用原生按钮的按压反馈。
    val elasticFeedbackEnabled = enabled && LocalAppUiStyle.current == AppUiStyle.MIUIX &&
        LocalAppThemeConfig.current.liquidGlassEnabled
    ProvideAppElasticFeedback(enabled = elasticFeedbackEnabled) {
        val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val interactionModifier = modifier.appElasticPress(enabled = elasticFeedbackEnabled, highlightShape = shape)
            .appDesktopInteractionVisuals(resolvedInteractionSource, enabled)
        val miuixColors = colors?.let {
            MiuixButtonColors(
                color = it.containerColor,
                disabledColor = it.disabledContainerColor,
                contentColor = it.contentColor,
                disabledContentColor = it.disabledContentColor,
            )
        } ?: MiuixButtonDefaults.buttonColorsPrimary()
        val readableMiuixColors = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) miuixColors else MiuixButtonColors(
            color = miuixColors.color,
            disabledColor = miuixColors.disabledColor,
            contentColor = resolveButtonContentColor(miuixColors.color, miuixColors.contentColor, MaterialTheme.colorScheme),
            disabledContentColor = miuixColors.disabledContentColor,
        )
        val materialColors = colors ?: ButtonDefaults.buttonColors(
            containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),
            contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme),
        )
        val readableMaterialColors = materialColors.copy(
            contentColor = resolveButtonContentColor(materialColors.containerColor, materialColors.contentColor, MaterialTheme.colorScheme),
        )
        when (LocalAppUiStyle.current) {
            AppUiStyle.MIUIX -> AppMiuixButton(
                onClick = feedbackClick,
                modifier = interactionModifier,
                enabled = enabled,
                colors = readableMiuixColors,
                insideMargin = contentPadding,
                interactionSource = resolvedInteractionSource,
                border = border,
                content = content,
            )
            AppUiStyle.MATERIAL3 -> Button(
                onClick = feedbackClick,
                modifier = interactionModifier,
                enabled = enabled,
                shape = shape,
                colors = readableMaterialColors,
                elevation = elevation,
                border = border,
                contentPadding = resolveAppButtonContentPadding(
                    uiStyle = LocalAppUiStyle.current,
                    requested = contentPadding,
                    defaultPadding = ButtonDefaults.ContentPadding,
                ),
                interactionSource = resolvedInteractionSource,
                content = {
                    com.android.purebilibili.core.ui.ProvideAppButtonSlotPalette(
                        containerColor = if (enabled) readableMaterialColors.containerColor else readableMaterialColors.disabledContainerColor,
                        enabled = enabled,
                    ) { content() }
                },
            )
        }
    }
}

/** Secondary actions use the active renderer's own neutral palette. */
@Composable
private fun appSecondaryButtonColors(materialColors: ButtonColors): ButtonColors {
    if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
        val native = MiuixButtonDefaults.buttonColors()
        return materialColors.copy(
            containerColor = native.color,
            contentColor = native.contentColor,
            disabledContainerColor = native.disabledColor,
            disabledContentColor = native.disabledContentColor,
        )
    }
    return materialColors.copy(
        contentColor = com.android.purebilibili.core.ui.AppContentColors.neutralAction(),
        disabledContentColor = com.android.purebilibili.core.ui.AppContentColors.disabled(),
    )
}

@Composable
private fun appButtonPadding(materialPadding: PaddingValues): PaddingValues =
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) MiuixButtonDefaults.InsideMargin else materialPadding

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: androidx.compose.material3.ButtonColors = AppTextButtonDefaults.colors(),
    contentPadding: PaddingValues = appButtonPadding(AppButtonDefaults.TextButtonContentPadding),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    val elasticFeedbackEnabled = enabled && LocalAppUiStyle.current == AppUiStyle.MIUIX &&
        LocalAppThemeConfig.current.liquidGlassEnabled
    ProvideAppElasticFeedback(enabled = elasticFeedbackEnabled) {
        val readableColors = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) colors else colors.copy(
            contentColor = resolveButtonContentColor(colors.containerColor, colors.contentColor, MaterialTheme.colorScheme),
        )
        val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val interactionModifier = modifier.appElasticPress(enabled = elasticFeedbackEnabled, highlightShape = shape)
            .appDesktopInteractionVisuals(resolvedInteractionSource, enabled)
        val miuixColors = MiuixButtonColors(
            color = readableColors.containerColor,
            disabledColor = readableColors.disabledContainerColor,
            contentColor = readableColors.contentColor,
            disabledContentColor = readableColors.disabledContentColor,
        )
        when (LocalAppUiStyle.current) {
            AppUiStyle.MIUIX -> AppMiuixButton(
                onClick = feedbackClick,
                modifier = interactionModifier,
                enabled = enabled,
                colors = miuixColors,
                insideMargin = contentPadding,
                interactionSource = resolvedInteractionSource,
                content = content,
            )
            AppUiStyle.MATERIAL3 -> TextButton(
                onClick = feedbackClick,
                modifier = interactionModifier,
                enabled = enabled,
                shape = shape,
                colors = readableColors,
                contentPadding = contentPadding,
                interactionSource = resolvedInteractionSource,
                content = {
                    com.android.purebilibili.core.ui.ProvideAppButtonSlotPalette(
                        containerColor = if (enabled) readableColors.containerColor else readableColors.disabledContainerColor,
                        enabled = enabled,
                    ) { content() }
                },
            )
        }
    }
}

@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    labelText: String? = null,
    placeholderText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    miuixCornerRadius: androidx.compose.ui.unit.Dp? = null,
    miuixContainerColor: Color? = null,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    if (
        shouldUseMiuixOutlinedTextField(
            uiStyle = LocalAppUiStyle.current,
            hasPrefix = prefix != null,
            hasSuffix = suffix != null,
            liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled,
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = modifier,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            if (labelText == null) label?.invoke()
            val resolvedLabel = labelText ?: placeholderText.orEmpty()
            MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                // focusRequester 需要落在真正可聚焦的输入框上；外层 Column 只承担布局
                modifier = Modifier
                    .fillMaxWidth()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                label = resolvedLabel,
                useLabelAsPlaceholder = labelText == null && !placeholderText.isNullOrEmpty(),
                enabled = enabled,
                readOnly = readOnly,
                textStyle = textStyle,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                leadingIcon = if (prefix == null) leadingIcon else {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            leadingIcon?.invoke()
                            prefix()
                        }
                    }
                },
                trailingIcon = if (suffix == null) trailingIcon else {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suffix()
                            trailingIcon?.invoke()
                        }
                    }
                },
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                colors = MiuixTextFieldDefaults.textFieldColors(
                    backgroundColor = miuixContainerColor
                        ?: MiuixTheme.colorScheme.secondaryContainer,
                    borderColor = if (isError) {
                        AppContentColors.error()
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                ),
                // Miuix TextField 没有 Shape 参数，只能用圆角半径近似胶囊形
                cornerRadius = miuixCornerRadius ?: MiuixTextFieldDefaults.CornerRadius,
            )
            supportingText?.invoke()
        }
        return
    }
    val resolvedLabel = label ?: labelText?.let { text -> { Text(text) } }
    val resolvedPlaceholder = placeholder ?: placeholderText?.let { text -> { Text(text) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = resolvedLabel,
        placeholder = resolvedPlaceholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors,
    )
}

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: androidx.compose.ui.graphics.Shape? = null,
    glassSource: AppGlassMenuSource? = LocalAppGlassMenuSource.current,
    /**
     * 仅液态玻璃同窗菜单使用：真实触发控件在窗口坐标中的边界，弹窗按其右缘对齐并向上
     * 覆盖控件；长按等以按点为锚的调用保持默认 null，继续用局部小锚点。原生路径不读取。
     */
    glassAnchorBounds: androidx.compose.ui.geometry.Rect? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (properties.focusable && AppGlassMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            anchorBounds = glassAnchorBounds,
            scrollState = scrollState,
            source = glassSource,
            // 玻璃下拉菜单先使用安全区域内的完整高度，内容确实溢出时才出现滚动提示。
            useAvailableHeight = true,
            content = { Column(content = content) },
        )) return
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX && LocalAppThemeConfig.current.nativeMiuixPopupsEnabled) {
        WindowListPopup(
            show = expanded,
            popupModifier = modifier,
            onDismissRequest = onDismissRequest,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 288.dp).verticalScroll(scrollState),
                content = content,
            )
        }
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            scrollState = scrollState,
            properties = properties,
            shape = shape ?: androidx.compose.material3.MenuDefaults.shape,
            content = content,
        )
    }
}

@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
    dismissOnClick: Boolean = true,
    selected: Boolean? = null,
) {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS, onClick)
    val glassScope = LocalAppGlassMenuScope.current
    val menuModifier = modifier.glassMenuItem(enabled, dismissOnClick, feedbackClick).glassMenuSelectedItem(selected == true)
    val menuClick = { if (glassScope != null) glassScope.dispatch(feedbackClick, dismissOnClick) else feedbackClick() }
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    // MD3 弹窗查询菜单项固有宽度，原生行在无限宽度测量时会产生无效约束。
    // 原生弹窗保留原生行；其他弹窗使用下方可计算固有尺寸的布局。
    if (glassScope == null && com.android.purebilibili.core.ui.isMiuixNonGlassEnabled() &&
        LocalAppThemeConfig.current.nativeMiuixPopupsEnabled
    ) {
        top.yukonga.miuix.kmp.basic.BasicComponent(
            modifier = menuModifier.semantics { if (!enabled) disabled() },
            startAction = leadingIcon,
            endActions = trailingIcon?.let { slot -> { slot() } },
            enabled = enabled,
            holdDownState = selected == true,
            onClick = menuClick,
            interactionSource = resolvedInteractionSource,
        ) {
            ProvideAppActionContentColor(
                if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
                text,
            )
        }
        return
    }
    if (glassScope == null && LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        AppSurface(
            onClick = menuClick,
            enabled = enabled,
            interactionSource = resolvedInteractionSource,
            modifier = menuModifier.fillMaxWidth(),
            color = Color.Transparent,
            contentColor = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .heightIn(min = AppChromeSizeTokens.CompactRowMinHeightDp.dp)
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leadingIcon?.invoke()
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { text() }
                trailingIcon?.invoke()
            }
        }
        return
    }
    DropdownMenuItem(
        text = text,
        onClick = menuClick,
        modifier = menuModifier.appDesktopInteractionVisuals(resolvedInteractionSource, enabled),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = resolvedInteractionSource,
    )
}

@Composable
fun AppModalNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    gesturesEnabled: Boolean = true,
    scrimColor: Color,
    content: @Composable () -> Unit,
) = ModalNavigationDrawer(
    drawerContent = drawerContent,
    modifier = modifier,
    drawerState = drawerState,
    gesturesEnabled = gesturesEnabled,
    scrimColor = scrimColor,
    content = content,
)

@Composable
fun AppNavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION, onClick)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        AppMiuixNavigationDrawerItem(
            label = label,
            selected = selected,
            onClick = feedbackClick,
            modifier = modifier,
            icon = icon,
            badge = badge,
            interactionSource = resolvedInteractionSource,
        )
        return
    }
    NavigationDrawerItem(
        label = label,
        selected = selected,
        onClick = feedbackClick,
        modifier = modifier.appDesktopInteractionVisuals(resolvedInteractionSource),
        icon = icon,
        badge = badge,
        colors = colors,
        interactionSource = resolvedInteractionSource,
    )
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = appSecondaryButtonColors(ButtonDefaults.outlinedButtonColors()),
    elevation: androidx.compose.material3.ButtonElevation? = null,
    border: BorderStroke? = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
        null
    } else if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        BorderStroke(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.20f else 0.10f))
    } else ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = appButtonPadding(AppButtonDefaults.ContentPadding),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    val elasticFeedbackEnabled = enabled && LocalAppUiStyle.current == AppUiStyle.MIUIX &&
        LocalAppThemeConfig.current.liquidGlassEnabled
    ProvideAppElasticFeedback(enabled = elasticFeedbackEnabled) {
        val readableColors = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) colors else colors.copy(
            contentColor = resolveButtonContentColor(colors.containerColor, colors.contentColor, MaterialTheme.colorScheme),
        )
        val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val interactionModifier = modifier.appElasticPress(enabled = elasticFeedbackEnabled, highlightShape = shape)
            .appDesktopInteractionVisuals(resolvedInteractionSource, enabled)
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppMiuixButton(
                onClick = feedbackClick,
                modifier = interactionModifier,
                enabled = enabled,
                colors = MiuixButtonColors(
                    color = readableColors.containerColor,
                    contentColor = readableColors.contentColor,
                    disabledColor = readableColors.disabledContainerColor,
                    disabledContentColor = readableColors.disabledContentColor,
                ),
                insideMargin = contentPadding,
                interactionSource = resolvedInteractionSource,
                border = border,
                content = content,
            )
            return@ProvideAppElasticFeedback
        }
        OutlinedButton(
            onClick = feedbackClick,
            modifier = interactionModifier,
            enabled = enabled,
            shape = shape,
            colors = readableColors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = resolvedInteractionSource,
            content = {
                com.android.purebilibili.core.ui.ProvideAppButtonSlotPalette(
                    containerColor = if (enabled) readableColors.containerColor else readableColors.disabledContainerColor,
                    enabled = enabled,
                ) { content() }
            },
        )
    }
}

@Composable
fun AppAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    horizontalArrangement: Arrangement.Horizontal = AssistChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = AssistChipDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    ProvideAppElasticFeedback(enabled = enabled) {
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppMiuixChip(
                onClick = feedbackClick,
                selected = false,
                enabled = enabled,
                modifier = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) modifier else
                    modifier.appElasticPress(enabled = enabled, highlightShape = shape),
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                interactionSource = interactionSource,
                label = label,
            )
            return@ProvideAppElasticFeedback
        }
        AssistChip(
            onClick = feedbackClick,
            label = label,
            modifier = modifier.appElasticPress(enabled = enabled, highlightShape = shape),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            horizontalArrangement = horizontalArrangement,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        )
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    horizontalArrangement: Arrangement.Horizontal = FilterChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = FilterChipDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    // 仅供承担导航作用的标签选择原生 Miuix 导航配色。
    miuixNavigationColors: Boolean = false,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS, onClick)
    ProvideAppElasticFeedback(enabled = enabled) {
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppMiuixChip(
                onClick = feedbackClick,
                selected = selected,
                enabled = enabled,
                modifier = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) modifier else
                    modifier.appElasticPress(enabled = enabled, highlightShape = shape),
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                interactionSource = interactionSource,
                navigationColors = miuixNavigationColors,
                label = label,
            )
            return@ProvideAppElasticFeedback
        }
        FilterChip(
            selected = selected,
            onClick = feedbackClick,
            label = label,
            modifier = modifier.appElasticPress(enabled = enabled, highlightShape = shape),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            horizontalArrangement = horizontalArrangement,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        )
    }
}

@Composable
fun AppInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    ProvideAppElasticFeedback(enabled = enabled) {
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppMiuixChip(
                onClick = feedbackClick,
                selected = selected,
                enabled = enabled,
                modifier = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) modifier else
                    modifier.appElasticPress(enabled = enabled),
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                interactionSource = null,
                label = label,
            )
            return@ProvideAppElasticFeedback
        }
        InputChip(
            selected = selected,
            onClick = feedbackClick,
            label = label,
            modifier = modifier.appElasticPress(enabled = enabled),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

@Composable
private fun resolveReadableFloatingActionButtonColors(
    requestedContainerColor: Color,
    requestedContentColor: Color,
): Pair<Color, Color> {
    val defaultContainer = FloatingActionButtonDefaults.containerColor
    val defaultContent = contentColorFor(defaultContainer)
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val resolvedContainer = if (isMiuix) {
        resolveMiuixFabContainerColor(
            requested = requestedContainerColor,
            defaultMaterialContainer = defaultContainer,
            miuixDefaultContainer = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled())
                MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primaryContainer,
        )
    } else {
        requestedContainerColor
    }
    val preferredContent = if (isMiuix) {
        resolveMiuixFabContentColor(
            requested = requestedContentColor,
            defaultMaterialContent = defaultContent,
            miuixDefaultContent = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled())
                MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        requestedContentColor
    }
    return resolvedContainer to resolveButtonContentColor(
        containerColor = resolvedContainer,
        contentColor = preferredContent,
        colorScheme = MaterialTheme.colorScheme,
    )
}

@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    ProvideAppElasticFeedback(enabled = true) {
        val (resolvedContainerColor, resolvedContentColor) = resolveReadableFloatingActionButtonColors(
            requestedContainerColor = containerColor,
            requestedContentColor = contentColor,
        )
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppMiuixFloatingActionButton(
                onClick = feedbackClick,
                modifier = modifier.appElasticPress(highlightShape = shape),
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
                small = false,
                content = content,
            )
            return@ProvideAppElasticFeedback
        }
        FloatingActionButton(
            onClick = feedbackClick,
            modifier = modifier.appElasticPress(highlightShape = shape),
            shape = shape,
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun AppSmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.smallShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    ProvideAppElasticFeedback(enabled = true) {
        val (resolvedContainerColor, resolvedContentColor) = resolveReadableFloatingActionButtonColors(
            requestedContainerColor = containerColor,
            requestedContentColor = contentColor,
        )
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppMiuixFloatingActionButton(
                onClick = feedbackClick,
                modifier = modifier.appElasticPress(highlightShape = shape),
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
                small = true,
                content = content,
            )
            return@ProvideAppElasticFeedback
        }
        SmallFloatingActionButton(
            onClick = feedbackClick,
            modifier = modifier.appElasticPress(highlightShape = shape),
            shape = shape,
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun AppTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color = LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null,
) = Tab(
    selected = selected,
    onClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION, onClick),
    modifier = modifier,
    enabled = enabled,
    text = text,
    icon = icon,
    selectedContentColor = selectedContentColor,
    unselectedContentColor = unselectedContentColor,
    interactionSource = interactionSource,
)

@Composable
fun AppTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContentColor: Color = LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Tab(
    selected = selected,
    onClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION, onClick),
    modifier = modifier,
    enabled = enabled,
    selectedContentColor = selectedContentColor,
    unselectedContentColor = unselectedContentColor,
    interactionSource = interactionSource,
    content = content,
)

@Composable
fun AppPrimaryTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    indicatorPositionProvider: (() -> Float)? = null,
    labelContentWidths: List<Dp>? = null,
    tabs: @Composable () -> Unit,
) {
    @Suppress("DEPRECATION")
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        indicator = { tabPositions ->
            AppElasticTabIndicator(
                selectedTabIndex = selectedTabIndex,
                tabSlots = resolveMeasuredTabContentSlots(
                    tabPositions.map { it.toAppTabSlot() }, labelContentWidths,
                ),
                matchContentSize = true,
                primary = true,
                indicatorPositionProvider = indicatorPositionProvider,
            )
        },
        divider = {},
        tabs = tabs,
    )
}

@Composable
fun AppPrimaryScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    minTabWidth: Dp = resolvePiliPlusScrollableUnderlineMinWidth(),
    indicatorPositionProvider: (() -> Float)? = null,
    labelContentWidths: List<Dp>? = null,
    tabs: @Composable () -> Unit,
) {
    AppElasticScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        edgePadding = edgePadding,
        minTabWidth = minTabWidth,
        scrollState = scrollState,
        indicator = { tabSlots ->
            AppElasticTabIndicator(
                selectedTabIndex = selectedTabIndex,
                tabSlots = resolveMeasuredTabContentSlots(tabSlots, labelContentWidths),
                matchContentSize = true,
                primary = true,
                indicatorPositionProvider = indicatorPositionProvider,
            )
        },
        divider = {},
        tabs = tabs,
    )
}

@Composable
fun AppSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = SuggestionChipDefaults.shape,
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    elevation: ChipElevation? = SuggestionChipDefaults.suggestionChipElevation(),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled),
    horizontalArrangement: Arrangement.Horizontal = SuggestionChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = SuggestionChipDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
): Unit = AppExactTouchTargets {
    val feedbackClick = appActionWithFeedback(onClick)
    ProvideAppElasticFeedback(enabled = enabled) {
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppMiuixChip(
                onClick = feedbackClick,
                selected = false,
                enabled = enabled,
                modifier = modifier.appElasticPress(enabled = enabled, highlightShape = shape),
                leadingIcon = icon,
                trailingIcon = null,
                interactionSource = interactionSource,
                label = label,
            )
            return@ProvideAppElasticFeedback
        }
        SuggestionChip(
            onClick = feedbackClick,
            label = label,
            modifier = modifier.appElasticPress(enabled = enabled, highlightShape = shape),
            enabled = enabled,
            icon = icon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            horizontalArrangement = horizontalArrangement,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        )
    }
}
