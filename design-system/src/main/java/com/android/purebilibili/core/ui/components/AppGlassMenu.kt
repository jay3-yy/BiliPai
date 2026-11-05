package com.android.purebilibili.core.ui.components

import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import top.yukonga.miuix.kmp.blur.Backdrop
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** 同窗菜单可重用页面背景；独立 Dialog/Popup 窗口必须使用自己的宿主。 */
interface AppGlassMenuPresenter {
    val ownerView: View
    fun show(owner: Any, request: AppGlassMenuRequest)
    fun dismiss(owner: Any)
}

enum class AppGlassMenuPlacement { ANCHOR_END, CENTER, START, END, END_BOTTOM }

class AppGlassMenuRequest(
    val anchor: () -> Rect?,
    val placement: AppGlassMenuPlacement,
    val maxWidth: Dp,
    val minWidth: Dp,
    val maxHeight: Dp?,
    val scrollState: ScrollState,
    val modifier: Modifier,
    val title: String,
    val onDismissRequest: () -> Unit,
    val content: @Composable (AppGlassMenuScope) -> Unit,
    val backdrop: Backdrop? = null,
    val sourceLayer: GraphicsLayer? = null,
    val carriedPress: AppSingleChoicePopupPress? = null,
    val sourceBounds: () -> Rect? = { null },
    val sourceAvailable: () -> Boolean = { sourceLayer?.isReleased == false },
    val onSourceVisibilityChange: (Boolean) -> Unit = {},
    val interactiveContent: Boolean = false,
    val darkTheme: Boolean? = null,
    val showScrollbar: Boolean = false,
    val fitWholeItems: Boolean = false,
    val useAvailableHeight: Boolean = false,
)

class AppGlassMenuScope(
    val pressedItem: () -> Any?,
    val register: (Any, LayoutCoordinates, Boolean, Boolean, () -> Unit) -> Unit,
    val unregister: (Any) -> Unit,
    val dispatch: (action: () -> Unit, dismiss: Boolean) -> Unit,
    val backdrop: Backdrop? = null,
    val fitWholeItems: Boolean = false,
)

val LocalAppGlassMenuActive = compositionLocalOf { false }

val LocalAppGlassMenuBackdrop = compositionLocalOf<Backdrop?> { null }
/** 媒体控件可显式指定深色材质；普通页面沿用宿主主题。 */
val LocalAppGlassMenuDarkTheme = compositionLocalOf<Boolean?> { null }

val LocalAppGlassMenuPresenter = compositionLocalOf<AppGlassMenuPresenter?> { null }
val LocalAppGlassMenuScope = compositionLocalOf<AppGlassMenuScope?> { null }
/** Interactive panels can finish their action after the shared popup has returned to its source. */
val LocalAppGlassPanelDismissAction = compositionLocalOf<((() -> Unit) -> Unit)?> { null }

@Composable
fun appGlassMenuAvailable(): Boolean {
    val presenter = LocalAppGlassMenuPresenter.current
    return com.android.purebilibili.core.ui.LocalAppThemeConfig.current.popupGlassEnabled &&
        presenter != null && presenter.ownerView === LocalView.current
}

/** 返回是否已由同窗玻璃宿主接管；不具备同窗采样时由调用点继续原生路径。 */
@Composable
fun AppGlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    placement: AppGlassMenuPlacement = AppGlassMenuPlacement.ANCHOR_END,
    anchorBounds: Rect? = null,
    offset: DpOffset = DpOffset.Zero,
    minWidth: Dp = 160.dp,
    maxWidth: Dp = 280.dp,
    maxHeight: Dp? = null,
    scrollState: ScrollState = rememberScrollState(),
    sourceLayer: GraphicsLayer? = null,
    source: AppGlassMenuSource? = LocalAppGlassMenuSource.current,
    carriedPress: AppSingleChoicePopupPress? = null,
    onSourceVisibilityChange: (Boolean) -> Unit = {},
    interactiveContent: Boolean = false,
    showScrollbar: Boolean = false,
    fitWholeItems: Boolean = false,
    useAvailableHeight: Boolean = false,
    content: @Composable () -> Unit,
): Boolean {
    val presenter = LocalAppGlassMenuPresenter.current
    if (!com.android.purebilibili.core.ui.LocalAppThemeConfig.current.popupGlassEnabled ||
        presenter == null || presenter.ownerView !== LocalView.current) return false
    // 关闭时保留来源身份供退场使用，但不订阅随列表滚动变化的边界。
    val activeSource = source?.takeIf { it.enabled && (!expanded || it.isAvailable) }
    val suppliedBackdrop = LocalAppGlassMenuBackdrop.current
    val suppliedDarkTheme = LocalAppGlassMenuDarkTheme.current
    val owner = remember { Any() }
    val generation = remember { intArrayOf(0) }
    var measuredAnchor by remember { mutableStateOf<Rect?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val retainedAnchor = remember { arrayOfNulls<Rect>(1) }
    val resolvedAnchor = if (expanded) {
        (anchorBounds ?: activeSource?.bounds ?: measuredAnchor)?.translate(
            with(density) { Offset(offset.x.toPx(), offset.y.toPx()) },
        )
    } else retainedAnchor[0]
    SideEffect {
        if (expanded) retainedAnchor[0] = resolvedAnchor
        // 下次打开重新测量，避免列表已经滚动后先使用上次的坐标。
        else measuredAnchor = null
    }
    val currentAnchor by rememberUpdatedState(resolvedAnchor)
    val latestExpanded by rememberUpdatedState(expanded)
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestContent by rememberUpdatedState(content)
    val popupConfig by rememberUpdatedState(com.android.purebilibili.core.ui.LocalAppThemeConfig.current)
    val locals = currentCompositionLocalContext
    val contentLocals by rememberUpdatedState(locals)
    // 显式锚点和真实触发源无需布局占位，避免横向 dock 为零尺寸节点再分配间距。
    // 关闭时不挂坐标观察器，避免列表里的每个菜单随滚动写回布局状态。
    // 展开后先测量锚点，下一帧再由下方 effect 交给弹窗宿主。
    if (expanded && anchorBounds == null && activeSource == null && placement == AppGlassMenuPlacement.ANCHOR_END) {
        Box(Modifier.onGloballyPositioned {
            measuredAnchor = it.parentLayoutCoordinates?.boundsInWindow() ?: it.boundsInWindow()
        })
    }
    LaunchedEffect(expanded, presenter, suppliedDarkTheme, currentAnchor != null || placement != AppGlassMenuPlacement.ANCHOR_END) {
        val requestGeneration = ++generation[0]
        if (expanded && (currentAnchor != null || placement != AppGlassMenuPlacement.ANCHOR_END)) {
            presenter.show(owner, AppGlassMenuRequest(
                anchor = { currentAnchor }, placement = placement, maxWidth = maxWidth,
                minWidth = minWidth, maxHeight = maxHeight, scrollState = scrollState,
                backdrop = suppliedBackdrop,
                darkTheme = suppliedDarkTheme,
                // 交互面板只响应自身滚动边界，不把触发按钮的按住拖动作为面板拉伸。
                carriedPress = (activeSource?.press ?: carriedPress).takeUnless { interactiveContent },
                sourceBounds = { activeSource?.bounds ?: anchorBounds },
                sourceAvailable = { activeSource?.isAvailable ?: (sourceLayer?.isReleased == false) },
                sourceLayer = activeSource?.layer ?: sourceLayer,
                onSourceVisibilityChange = { visible ->
                    source?.visible = visible
                    if (visible) { source?.press?.cancel(); source?.press = null }
                    onSourceVisibilityChange(visible)
                },
                modifier = modifier, title = title, onDismissRequest = {
                    scope.launch {
                        withFrameNanos { }
                        if (generation[0] == requestGeneration && latestExpanded) latestDismiss()
                    }
                },
                interactiveContent = interactiveContent,
                showScrollbar = showScrollbar,
                fitWholeItems = fitWholeItems,
                useAvailableHeight = useAvailableHeight,
                content = { scope ->
                    CompositionLocalProvider(contentLocals) {
                        CompositionLocalProvider(
                            com.android.purebilibili.core.ui.LocalAppThemeConfig provides popupConfig.copy(liquidGlassEnabled = popupConfig.popupGlassEnabled),
                            LocalAppGlassMenuScope provides scope.takeUnless { interactiveContent },
                            LocalAppGlassPanelDismissAction provides if (interactiveContent) {
                                { action: () -> Unit -> scope.dispatch(action, true) }
                            } else null,
                            LocalAppGlassMenuBackdrop provides scope.backdrop,
                            // 菜单绘制在独立覆盖层，不能继承触发控件的玻璃形变归属。
                            com.android.purebilibili.core.ui.blur.LocalAppGlassContainer provides null,
                        ) {
                            com.android.purebilibili.core.ui.blur.AppGlassContainerContent(
                                enabled = interactiveContent,
                                backdrop = scope.backdrop,
                                ownsDeformation = true,
                            ) { latestContent() }
                        }
                    }
                },
            ))
        } else presenter.dismiss(owner)
    }
    DisposableEffect(presenter, owner) { onDispose {
        generation[0]++
        presenter.dismiss(owner)
    } }
    return true
}

@Composable
internal fun Modifier.glassMenuItem(
    enabled: Boolean,
    dismissOnClick: Boolean,
    onClick: () -> Unit,
): Modifier {
    val scope = LocalAppGlassMenuScope.current ?: return this
    val key = remember { Any() }
    val latestClick by rememberUpdatedState(onClick)
    val pressedColor = com.android.purebilibili.core.ui.AppContentColors.primaryText().copy(alpha = 0.12f)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    SideEffect { coordinates[0]?.takeIf { it.isAttached }?.let {
        scope.register(key, it, enabled, dismissOnClick) { latestClick() }
    } }
    DisposableEffect(scope, key) { onDispose { scope.unregister(key) } }
    return this
        .onGloballyPositioned { coordinates[0] = it; scope.register(key, it, enabled, dismissOnClick) { latestClick() } }
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        .drawBehind {
            // 绘制阶段读取当前命中项，滑过条目只刷新背景，不触发保存或重复点击。
            if (enabled && scope.pressedItem() === key) drawRect(pressedColor)
        }

}

@Composable
internal fun Modifier.glassMenuSelectedItem(selected: Boolean): Modifier {
    if (!selected || LocalAppGlassMenuScope.current == null) return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(requester) {
        withFrameNanos { }
        requester.bringIntoView()
    }
    return bringIntoViewRequester(requester).semantics { this.selected = true }
}
