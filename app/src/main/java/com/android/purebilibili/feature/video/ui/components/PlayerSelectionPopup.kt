package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.window.Popup
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import kotlin.math.roundToInt

enum class PlayerListPopupPlacement { CENTER, START, END, END_BOTTOM, ANCHOR_END }

/** 同窗玻璃宿主可用时使用统一菜单；关闭玻璃及独立窗口保持原生组件。 */
@Composable
fun PlayerSelectionPopup(
    title: String,
    showTitle: Boolean = true,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    placement: PlayerListPopupPlacement = PlayerListPopupPlacement.CENTER,
    maxHeight: Dp? = 440.dp,
    minWidth: Dp = 120.dp,
    anchorBounds: IntRect? = null,
    scrollState: ScrollState = rememberScrollState(),
    maxWidth: Dp = 288.dp,
    sourceLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null,
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource? =
        com.android.purebilibili.core.ui.components.LocalAppGlassMenuSource.current,
    onSourceVisibilityChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val glassSource = source
    if (!rememberPlayerPopupHostActive(onDismissRequest)) return
    val anchorToGlassSource = glassSource?.isAvailable == true &&
        com.android.purebilibili.core.ui.components.appGlassMenuAvailable()
    if (placement == PlayerListPopupPlacement.ANCHOR_END && anchorBounds == null && !anchorToGlassSource) return
    // 仅玻璃列表使用播放器菜单上限；原生分支继续保留调用方的尺寸与定位。
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val safe = playerPanelSafeWindowBounds()
    val popupAnchorBounds = anchorBounds?.let { resolvePlayerPopupAnchorBounds(it, safe) }
    val glassPolicy = resolvePlayerSettingsMenuLayoutPolicy(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    )
    val glassMaxWidth = maxWidth.coerceAtMost(glassPolicy.maxWidthDp.dp)
    val glassMaxHeight = maxHeight?.coerceAtMost(glassPolicy.maxHeightDp.dp) ?: glassPolicy.maxHeightDp.dp
    if (com.android.purebilibili.core.ui.components.AppGlassMenu(
            expanded = true,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = title,
            // 真实触发源仅改变玻璃菜单的定位，原生布局继续使用调用方 placement。
            placement = if (anchorToGlassSource) com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.ANCHOR_END else when (placement) {
                PlayerListPopupPlacement.CENTER -> com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.CENTER
                PlayerListPopupPlacement.START -> com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.START
                PlayerListPopupPlacement.END -> com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.END
                PlayerListPopupPlacement.END_BOTTOM -> com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.END_BOTTOM
                PlayerListPopupPlacement.ANCHOR_END -> com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.ANCHOR_END
            },
            anchorBounds = popupAnchorBounds?.let { androidx.compose.ui.geometry.Rect(
                it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
            minWidth = minWidth.coerceAtMost(glassMaxWidth),
            maxWidth = glassMaxWidth,
            maxHeight = glassMaxHeight,
            scrollState = scrollState,
            showScrollbar = false,
            fitWholeItems = true,
            useAvailableHeight = true,
            source = source, sourceLayer = sourceLayer, onSourceVisibilityChange = onSourceVisibilityChange,
        ) {
            Column {
                if (showTitle) Text(title, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                content()
            }
        }) return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val gap = with(density) { 4.dp.roundToPx() }
    val requestedMaxHeight = with(density) { maxHeight?.roundToPx() }
    val viewport = resolvePlayerPopupViewport(
        safeWindowBounds = safe,
        requestedMaxHeight = requestedMaxHeight,
        horizontalMargin = gap,
        verticalMargin = gap,
        anchorBounds = popupAnchorBounds.takeIf { placement == PlayerListPopupPlacement.ANCHOR_END },
        anchorGap = gap,
    )
    if (viewport.bounds.width <= 0 || viewport.maxHeight <= 0) return
    val width = with(density) { viewport.bounds.width.toDp() }.coerceAtMost(maxWidth)
    val height = with(density) { viewport.maxHeight.toDp() }
    val menuModifier = modifier
        .widthIn(min = minWidth.coerceAtMost(width), max = width)
        .heightIn(max = height)
        .semantics { paneTitle = title }

    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        // 和倍速 / 画面比例菜单一致的行为：点菜单外收起菜单，且这一下点击被遮罩吃掉、
        // 不穿透给下层按钮（否则会出现"关闭又被同一个点击重新打开"的闪烁）。
        // 做法：Popup 内容铺满全屏 —— 一层透明遮罩负责消费点击并关闭，菜单本身叠在其上。
        val miuixAnchor = popupAnchorBounds ?: when (placement) {
            PlayerListPopupPlacement.CENTER -> IntRect(safe.center, IntSize.Zero)
            PlayerListPopupPlacement.START -> IntRect(safe.left, safe.center.y, safe.left, safe.center.y)
            PlayerListPopupPlacement.END -> IntRect(safe.right, safe.center.y, safe.right, safe.center.y)
            PlayerListPopupPlacement.END_BOTTOM -> IntRect(safe.right, safe.bottom, safe.right, safe.bottom)
            PlayerListPopupPlacement.ANCHOR_END -> return
        }
        var menuVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { menuVisible = true }
        val menuDensity = LocalDensity.current
        // height 只是约束上限（接近屏幕尺寸），拿它算偏移会算偏，
        // 必须用测量到的真实高度才能贴着锚点展开。
        val menuHeightPx = with(menuDensity) { height.roundToPx() }
        var menuMeasuredSize by remember { mutableStateOf(IntSize.Zero) }
        Popup(
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                // 关闭由遮罩自己处理：这里必须为 false，否则 Compose 会先吃掉这次点击
                // 但事件仍可能继续传递，表现为点按钮时"闪一下又出现"。
                dismissOnClickOutside = false,
                clippingEnabled = true,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 遮罩：消费点击并收起菜单，不穿透
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onDismissRequest() }
                )
                // 缩放原点必须跟着实际展开方向：向上展开钉底边，向下展开钉顶边，
                // 否则往下展开时动画会围着底边往上收，看起来像反了。
                val menuHeightForDirection = menuMeasuredSize.height.takeIf { it > 0 }
                    ?: menuHeightPx
                val aboveSpaceForDirection = miuixAnchor.top - viewport.bounds.top
                val scaleOrigin = if (menuHeightForDirection <= aboveSpaceForDirection) {
                    TransformOrigin(0.5f, 1f)
                } else {
                    TransformOrigin(0.5f, 0f)
                }
                AnimatedVisibility(
                    visible = menuVisible,
                    // 倍速菜单的观感是「整块从小变大」：用 scaleIn/scaleOut，
                    // 宽度和高度同时变化。
                    enter = fadeIn(tween(120)) + scaleIn(
                        animationSpec = tween(160),
                        initialScale = 0.85f,
                        transformOrigin = scaleOrigin,
                    ),
                    exit = fadeOut(tween(90)) + scaleOut(
                        animationSpec = tween(90),
                        targetScale = 0.85f,
                        transformOrigin = scaleOrigin,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged { menuMeasuredSize = it }
                        .absoluteOffset {
                            val h = if (menuMeasuredSize.height > 0) {
                                menuMeasuredSize.height
                            } else {
                                menuHeightPx
                            }
                            // 共用定位策略保留居中、两侧及锚点入口，并限制在安全区内。
                            resolvePlayerPopupOffset(
                                viewport = viewport.bounds,
                                popupSize = IntSize(
                                    menuMeasuredSize.width.takeIf { it > 0 }
                                        ?: with(menuDensity) { minWidth.coerceAtMost(width).roundToPx() },
                                    h,
                                ),
                                placement = placement,
                                anchorBounds = miuixAnchor,
                                layoutDirection = direction,
                                anchorGap = gap,
                            )
                        },
                ) {
                    AppSurface(
                        // MIUIX 的 DropdownImpl 每行自带一层更浅的背景（宽度只到内容末尾），
                        // 容器若用 surfaceContainerHigh 就会露出来变成阶梯状色块；
                        // 这里跟行背景取同一档色，视觉上才是一整块。
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        // 用设计系统的语义档位而不是硬编码：MiuixAlignedShapes.extraSmall
                        // 正是 8dp，Material3 下跟随它自己的 extraSmall，改规范时会一起变。
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            Modifier
                                .widthIn(min = minWidth.coerceAtMost(width), max = width)
                                .heightIn(max = height)
                                .verticalScroll(scrollState)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            if (showTitle) SmallTitle(text = title)
                            content()
                        }
                    }
                }
            }
        }
    } else if (placement == PlayerListPopupPlacement.CENTER) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {},
            title = if (showTitle) { { Text(title) } } else null,
            text = {
                Column(modifier = menuModifier.verticalScroll(scrollState)) { content() }
            },
        )
    } else {
        // 原生菜单从实际按钮边界定位；窗口坐标只在布局阶段换算，避免覆盖层偏移。
        val target = popupAnchorBounds ?: when (placement) {
            PlayerListPopupPlacement.START -> IntRect(safe.left, safe.center.y, safe.left, safe.center.y)
            PlayerListPopupPlacement.END -> IntRect(safe.right, safe.center.y, safe.right, safe.center.y)
            PlayerListPopupPlacement.END_BOTTOM -> IntRect(safe.right, safe.bottom, safe.right, safe.bottom)
            PlayerListPopupPlacement.CENTER,
            PlayerListPopupPlacement.ANCHOR_END -> return
        }
        val origin = remember { mutableStateOf(Offset.Zero) }
        // 这层 fillMaxSize 只是为了拿窗口原点来做绝对定位，但调用方把本组件放在底栏的
        // Column 里，直接 fillMaxSize 会把 Column 撑到整屏高（198 -> 1260），导致
        // align(BottomStart) 的底栏跑到顶部。用 Popup 承载后，fillMaxSize 只作用于 Popup
        // 自己的全屏窗口，不再参与父容器的测量。
        Popup(
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = true,
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { origin.value = it.positionInWindow() },
            ) {
                Box(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset {
                            IntOffset(
                                (target.left - origin.value.x).roundToInt(),
                                (target.top - origin.value.y).roundToInt(),
                            )
                        }
                        .size(
                            width = with(density) { target.width.coerceAtLeast(0).toDp() },
                            height = with(density) { target.height.coerceAtLeast(0).toDp() },
                        ),
                ) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = onDismissRequest,
                        modifier = menuModifier,
                        scrollState = scrollState,
                        // 播放器浮层内的菜单不要抢焦点：focusable = true 会让窗口重新分发
                        // insets，全屏页里依赖 BottomStart 对齐的底栏会被压到顶部。
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                        ),
                    ) {
                        if (showTitle) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        content()
                    }
                }
            }
        }
    }
}

/** 选中态、禁用原因和点击行为共享，行的颜色、尺寸和交互由主题原生组件提供。 */
@Composable
fun PlayerSelectionItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    summary: String? = null,
    modifier: Modifier = Modifier,
    index: Int = 0,
    optionCount: Int = 1,
) {
    PlayerNativeDropdownItem(
        item = DropdownItem(text = text, summary = summary),
        optionSize = optionCount,
        isSelected = selected,
        index = index,
        enabled = enabled,
        modifier = modifier,
        onSelectedIndexChange = { onClick() },
    )
}

@Composable
internal fun PlayerNativeDropdownItem(
    item: DropdownItem,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    enabled: Boolean = item.enabled,
    modifier: Modifier = Modifier,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val itemModifier = modifier.semantics(mergeDescendants = true) { selected = isSelected }
    if (com.android.purebilibili.core.ui.components.LocalAppGlassMenuScope.current != null) {
        com.android.purebilibili.core.ui.components.AppDropdownMenuItem(
            modifier = itemModifier,
            text = {
                Column {
                    Text(item.text)
                    item.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            enabled = enabled,
            trailingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
            onClick = { onSelectedIndexChange(index) },
        )
        return
    }
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        Box(modifier = itemModifier) {
            DropdownImpl(
                item = item,
                optionSize = optionSize,
                isSelected = isSelected,
                index = index,
                enabled = enabled,
                onSelectedIndexChange = onSelectedIndexChange,
            )
        }
    } else {
        DropdownMenuItem(
            modifier = itemModifier,
            text = {
                Column {
                    Text(text = item.text)
                    item.summary?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            enabled = enabled,
            trailingIcon = if (isSelected) {
                { Icon(Icons.Default.Check, contentDescription = null) }
            } else null,
            onClick = { onSelectedIndexChange(index) },
        )
    }
}

@Composable
internal fun Modifier.playerPopupSelectedOption(selected: Boolean): Modifier {
    if (!selected) return this
    // 整项视口从列表顶部开始，不在打开时因选中项自动滚动而露出半行。
    if (com.android.purebilibili.core.ui.components.LocalAppGlassMenuScope.current?.fitWholeItems == true) return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(requester) { withFrameNanos { }; requester.bringIntoView() }
    return bringIntoViewRequester(requester)
}
