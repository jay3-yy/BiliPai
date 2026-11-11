package com.android.purebilibili.core.ui.popup

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.home.GlassPopupPalette
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.ProvideAppGlassPopupContentColors
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.components.AppSingleChoicePopupRequest
import com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress
import com.android.purebilibili.core.ui.components.AppGlassMenuScope
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.glass.GlassColorBlendMode
import top.yukonga.miuix.kmp.glass.GlassColorLayer
import top.yukonga.miuix.kmp.glass.GlassMaterial
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import kotlin.math.roundToInt

/** 单选菜单使用入口的实际快照和窗口坐标，动作在退场完成后交还宿主。 */
@Composable
internal fun BoxScope.MiuixGlassChoicePopupOverlay(
    request: AppSingleChoicePopupRequest,
    controller: AppGlassChoicePopupController,
    backdrop: Backdrop,
    tuning: LiquidGlassTuning,
    backdropReady: Boolean,
    onFinished: (Int) -> Unit,
) {
    val sourceBounds = requireNotNull(request.controlBoundsInWindow)
    val sourceLayer = requireNotNull(request.sourceLayer)
    val menu = request.menu
    val isDark = isAppInDarkTheme()
    val palette = rememberGlassPopupPalette()
    val surfaceColor = if (palette == GlassPopupPalette.THEME_FROSTED) {
        resolveThemeFrostedPopupColor(isDark, MaterialTheme.colorScheme)
    } else {
        resolveAppGlassSurfaceColor(isDark, false, false, menu?.backdrop != null, tuning)
    }
    val material = remember(surfaceColor) {
        GlassMaterial(AppGlassPopupBackdropBlur, GlassColorLayer(surfaceColor, GlassColorBlendMode.SrcOver))
    }
    val pressedColor = AppContentColors.primaryText().copy(alpha = 0.12f)
    val selectedBringIntoView = remember(request) { BringIntoViewRequester() }
    val pressRows = remember(request) { linkedMapOf<Any, Pair<LayoutCoordinates, () -> Unit>>() }
    var pressedKey by remember(request) { mutableStateOf<Any?>(null) }
    val menuScope = remember(controller, backdrop, pressRows) {
        AppGlassMenuScope(
            pressedItem = { pressedKey },
            register = { key, coordinates, enabled, dismiss, action ->
                if (enabled) pressRows[key] = coordinates to { controller.menuScope.dispatch(action, dismiss) }
                else pressRows.remove(key)
            },
            unregister = { key -> pressRows.remove(key) },
            dispatch = controller.menuScope.dispatch,
            backdrop = backdrop,
        )
    }
    DisposableEffect(request, controller, pressRows) {
        val listener = object : AppSingleChoicePopupPress.Listener {
            private fun rowAt(position: Offset): Any? = pressRows.entries.firstOrNull { (_, row) ->
                row.first.isAttached && row.first.boundsInWindow().contains(position)
            }?.key

            override fun onMove(positionInWindow: Offset) {
                pressedKey = rowAt(positionInWindow)
            }

            override fun onRelease(positionInWindow: Offset) {
                val action = rowAt(positionInWindow)?.let { pressRows[it]?.second }
                pressedKey = null
                action?.invoke()
            }

            override fun onCancel() { pressedKey = null }
        }
        request.carriedPress?.attach(listener)
        onDispose {
            request.carriedPress?.attach(null)
            pressRows.clear()
        }
    }
    val density = LocalDensity.current
    val anchor = rememberGlassPopupAnchor()
    var hostOrigin by remember { mutableStateOf<Offset?>(null) }
    var anchorReady by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    val show = started && controller.morph.opening
    val transition = updateTransition(show, label = "popupExitCompletion")
    transition.animateFloat(transitionSpec = { GlassMotion.transformBounds(targetState) }, label = "popupBounds") { if (it) 1f else 0f }
    transition.animateFloat(transitionSpec = { GlassMotion.transformCenter(targetState) }, label = "popupCenter") { if (it) 1f else 0f }
    transition.animateFloat(transitionSpec = { GlassMotion.transformIconMaterial(targetState) }, label = "popupIcon") { if (it) 1f else 0f }
    transition.animateFloat(transitionSpec = { GlassMotion.transformContentMaterial(targetState) }, label = "popupContent") { if (it) 1f else 0f }

    LaunchedEffect(anchorReady, backdropReady) {
        if (anchorReady && backdropReady && controller.morph.opening) {
            withFrameNanos { }
            started = true
        }
    }
    LaunchedEffect(controller, started) {
        if (started) {
            snapshotFlow { Triple(controller.morph.opening, transition.targetState, transition.isRunning) }
                .first { (opening, target, running) -> !opening && !target && !running }
            onFinished(controller.morph.session)
        } else {
            snapshotFlow { controller.morph.opening }.first { !it }
            onFinished(controller.morph.session)
        }
    }
    LaunchedEffect(request, started) {
        if (started && request.options.any { it.value == request.selectedValue }) {
            snapshotFlow { transition.currentState && transition.targetState && !transition.isRunning }.first { it }
            selectedBringIntoView.bringIntoView()
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInWindow() }) {
        val origin = hostOrigin
        if (origin != null) {
            val left = (sourceBounds.left - origin.x).roundToInt()
            val top = (sourceBounds.top - origin.y).roundToInt()
            val width = with(density) { sourceBounds.width.toDp() }
            val height = with(density) { sourceBounds.height.toDp() }
            Box(
                Modifier.offset { IntOffset(left, top) }
                    .size(width, height)
                    .glassPopupAnchor(anchor, cornerRadius = minOf(width, height) / 2)
                    .onGloballyPositioned { anchorReady = true },
            )
            GlassTransformPopup(
                show = show,
                onDismissRequest = controller::requestDismiss,
                anchor = anchor,
                backdrop = backdrop,
                anchorContent = {
                    Canvas(Modifier.fillMaxSize()) {
                        if (!sourceLayer.isReleased) drawLayer(sourceLayer)
                    }
                },
                sizing = if (menu == null) GlassPopupSizing() else GlassPopupSizing(
                    minWidth = menu.minWidth,
                    maxWidth = menu.maxWidth,
                    maxHeight = menu.maxHeight ?: 416.dp,
                ),
                visuals = GlassPopupDefaults.visuals(material = material),
            ) {
                ProvideAppGlassPopupContentColors(glassSurfaceColor = surfaceColor) {
                    menu?.let { Box(it.modifier) { it.content(menuScope) } }
                    request.options.forEachIndexed { index, option ->
                        val rowKey = remember(request, index) { Any() }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .then(if (option.value == request.selectedValue) Modifier.bringIntoViewRequester(selectedBringIntoView) else Modifier)
                                .onGloballyPositioned { coordinates ->
                                    pressRows[rowKey] = coordinates to { controller.select(option.value) }
                                }
                                .drawBehind {
                                    if (pressedKey === rowKey) drawRect(pressedColor)
                                }
                                .selectable(
                                    selected = option.value == request.selectedValue,
                                    role = Role.RadioButton,
                                    onClick = { controller.select(option.value) },
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            request.optionLeading?.let { leading ->
                                leading(option.value)
                                Spacer(Modifier.width(14.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                AppText(text = option.label, tapToCopyEnabled = false, style = MaterialTheme.typography.bodyLarge)
                                option.description?.let {
                                    AppText(text = it, tapToCopyEnabled = false, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (option.value == request.selectedValue) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
