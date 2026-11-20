package com.android.purebilibili.navigation3

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.ui.transition.VideoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.NavigationReturnLanding
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/** 同行同组的入口可以跨过宽控件联动，其他入口仍按实际间距判定。 */
internal fun resolveSystemWindowNeighborDirection(
    source: Rect,
    neighbor: Rect,
    gap: Float,
    sameMotionGroup: Boolean = false,
): Offset? {
    val overlapY = min(source.bottom, neighbor.bottom) - max(source.top, neighbor.top)
    val overlapX = min(source.right, neighbor.right) - max(source.left, neighbor.left)
    if (overlapY >= min(source.height, neighbor.height) * 0.5f) {
        if (neighbor.left >= source.right - 1f && (sameMotionGroup || neighbor.left - source.right <= gap)) {
            return Offset(1f, 0f)
        }
        if (source.left >= neighbor.right - 1f && (sameMotionGroup || source.left - neighbor.right <= gap)) {
            return Offset(-1f, 0f)
        }
    }
    if (overlapX >= min(source.width, neighbor.width) * 0.5f) {
        if (neighbor.top >= source.bottom - 1f && (sameMotionGroup || neighbor.top - source.bottom <= gap)) return Offset(0f, 1f)
        if (source.top >= neighbor.bottom - 1f && (sameMotionGroup || source.top - neighbor.bottom <= gap)) return Offset(0f, -1f)
    }
    return null
}

internal class SystemWindowControlNeighbor(
    val view: View,
    val route: String?,
    val density: Float,
) {
    class Motion(val phase: SystemWindowMotionPhase, val direction: Offset)
    class SharedMotion(
        val origin: com.android.purebilibili.feature.settings.SettingsSharedContainerOrigin,
        val direction: Offset,
    )

    var coordinates: LayoutCoordinates? = null
    var motion by mutableStateOf<Motion?>(null)
    var sharedMotion by mutableStateOf<SharedMotion?>(null)
    var motionGroup: Any? = null
    var returnHandledByOwner = false
    var eligible = false
    var inViewport by mutableStateOf(false)
    val pressure = Animatable(0f)
    val groupLanding = NavigationReturnLanding()

    fun visibleBounds(): Rect? = coordinates?.takeIf { it.isAttached }
        ?.boundsInWindow()?.takeIf { it.width > 1f && it.height > 1f }

    // 仅变换绘制层，不改变槽位、原生组件尺寸和系统窗口的来源落点。
    val modifier: Modifier = Modifier.graphicsLayer {
        val event = motion
        val shared = sharedMotion
        val bounds = shared?.origin?.sharedBounds
        val amount = if (shared != null && bounds != null && shared.origin.available) {
            // 展开、预测返回和取消都跟随共享边界；两端为零，不在落位后补弹。
            navigationSharedNeighborPressure(bounds.open.value)
        } else if (shared != null) 0f else pressure.value
        val direction = shared?.direction ?: event?.direction ?: Offset.Zero
        applyControlNeighborPressure(amount, direction)
    }
}

/** 按打开时的实例保存邻项；回收后的项目或另一个窗口不能接替返回联动。 */
internal object SystemWindowControlNeighbors {
    private val controls = mutableSetOf<SystemWindowControlNeighbor>()

    fun attach(control: SystemWindowControlNeighbor) { controls.add(control) }
    fun detach(control: SystemWindowControlNeighbor) {
        controls.remove(control)
        control.coordinates = null
        control.groupLanding.clear()
    }

    private fun direction(
        source: SystemWindowControlNeighbor,
        sourceBounds: Rect,
        neighbor: SystemWindowControlNeighbor,
    ): Offset? {
        if (!neighbor.eligible || neighbor === source || neighbor.view !== source.view ||
            neighbor.route != source.route
        ) return null
        if ((source.motionGroup != null || neighbor.motionGroup != null) &&
            source.motionGroup !== neighbor.motionGroup
        ) return null
        val neighborBounds = neighbor.visibleBounds() ?: return null
        return resolveSystemWindowNeighborDirection(
            source = sourceBounds,
            neighbor = neighborBounds,
            gap = 24f * source.density,
            sameMotionGroup = source.motionGroup != null && neighbor.motionGroup === source.motionGroup,
        )
    }

    fun beginGroupNavigation(source: SystemWindowControlNeighbor) {
        val group = source.motionGroup as? NavigationControlMotionGroupState ?: return
        group.sharedReturn = null
        group.systemReturn = null
        controls.filter { it.motionGroup === group }.forEach {
            it.groupLanding.clear()
            it.motion = null
            it.sharedMotion = null
        }
    }

    fun isGroupRestored(group: NavigationControlMotionGroupState): Boolean =
        controls.filter { it.motionGroup === group }.all {
            !it.pressure.isRunning && it.motion?.phase?.running != true
        }

    private fun captureGroupLanding(source: SystemWindowControlNeighbor): (() -> Unit)? {
        val group = source.motionGroup as? NavigationControlMotionGroupState ?: return null
        val sourceReference = WeakReference(source)
        val members = controls.filter {
            it.eligible && it.motionGroup === group && it.view === source.view && it.route == source.route
        }.map(::WeakReference)
        return {
            val current = sourceReference.get()
            if (current != null && current in controls) members.forEach { reference ->
                reference.get()?.takeIf { it in controls && it.motionGroup === group }?.let {
                    it.groupLanding.request()
                }
            }
        }
    }

    fun linkShared(
        anchor: VideoCardSourceAnchor,
        origin: com.android.purebilibili.feature.settings.SettingsSharedContainerOrigin,
    ) {
        val source = controls.firstOrNull { anchor.hasCoordinates(it.coordinates) } ?: return
        val bounds = source.visibleBounds() ?: return
        val group = source.motionGroup as? NavigationControlMotionGroupState
        captureGroupLanding(source)?.let { deliver ->
            group?.sharedReturn = NavigationControlMotionGroupState.SharedReturn(origin, deliver)
        }
        controls.forEach { neighbor ->
            val direction = direction(source, bounds, neighbor) ?: return@forEach
            neighbor.sharedMotion = SystemWindowControlNeighbor.SharedMotion(origin, direction)
        }
    }

    fun capture(anchor: VideoCardSourceAnchor): ((SystemWindowMotionPhase) -> Unit)? {
        val source = controls.firstOrNull { anchor.hasCoordinates(it.coordinates) } ?: return null
        if (!source.eligible) return null
        val bounds = source.visibleBounds() ?: return null
        val neighbors = controls.mapNotNull { candidate ->
            val direction = direction(source, bounds, candidate) ?: return@mapNotNull null
            WeakReference(candidate) to direction
        }
        val groupLanding = captureGroupLanding(source)?.let(::ControlGroupLandingDelivery)
        if (neighbors.isEmpty() && groupLanding == null) return null
        val sourceReference = WeakReference(source)
        return { phase ->
            if (phase == SystemWindowMotionPhase.RETURNED && groupLanding != null) {
                (sourceReference.get()?.motionGroup as? NavigationControlMotionGroupState)?.systemReturn = groupLanding
            }
            val currentSource = sourceReference.get()
            // 打开与返回必须仍属于同一个来源实例。
            if (currentSource != null && currentSource in controls && anchor.hasCoordinates(currentSource.coordinates)) {
                if (currentSource.visibleBounds() != null) neighbors.forEach { (reference, direction) ->
                    val neighbor = reference.get()?.takeIf { it in controls } ?: return@forEach
                    if (neighbor.visibleBounds() == null) return@forEach
                    // 受力方向绑定本次静止几何；不能用已被挤开的边界重做邻项判定，
                    // 否则位移可能把邻项推过距离阈值，导致结束回调无法释放。
                    if (phase.returning && neighbor.returnHandledByOwner) return@forEach
                    neighbor.sharedMotion = null
                    // 返回途中先释放进入时的让位，等整组复原后再响应落位冲量。
                    val neighborPhase = if (groupLanding != null && phase.returning) SystemWindowMotionPhase.OPENED else phase
                    neighbor.motion = SystemWindowControlNeighbor.Motion(neighborPhase, direction)
                }
            }
        }
    }
}

@Composable
internal fun ObserveSystemWindowControlNeighbor(
    control: SystemWindowControlNeighbor,
    pageActive: Boolean,
    returnHandledByOwner: Boolean,
    motionGroup: Any? = null,
) {
    val inViewport = control.inViewport
    val active = rememberVisualWorkActive() && pageActive && inViewport
    val enabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    SideEffect {
        control.eligible = active && enabled
        control.returnHandledByOwner = returnHandledByOwner
        control.motionGroup = motionGroup
    }
    DisposableEffect(control) {
        SystemWindowControlNeighbors.attach(control)
        onDispose { SystemWindowControlNeighbors.detach(control) }
    }
    val event = control.motion
    val shared = control.sharedMotion
    LaunchedEffect(control, event, shared, active, enabled, pageActive, inViewport) {
        if (shared != null || !enabled || !pageActive || !inViewport) {
            control.pressure.snapTo(0f)
            return@LaunchedEffect
        }
        if (!active) {
            if (event?.phase?.running != true) control.pressure.snapTo(0f)
            return@LaunchedEffect
        }
        if (event == null) {
            control.pressure.snapTo(0f)
            return@LaunchedEffect
        }
        // 只对真实阶段切换响应：开始时牵引，结束/取消时从当前位置释放。
        // 没有进度回调，不使用延时模拟系统窗口的持续时间。
        control.pressure.animateTo(
            if (event.phase.running) 1f else 0f,
            controlNeighborPressureSpring(loading = event.phase.running),
        )
    }
}

internal fun navigationSharedNeighborPressure(open: Float): Float {
    val fraction = open.coerceIn(0f, 1f)
    // 两端不仅回到零，速度也回到零；取消手势按同一路径退回，避免尾端被截断。
    val envelope = 4f * fraction * (1f - fraction)
    return envelope * envelope
}
