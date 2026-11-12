package com.android.purebilibili.core.ui.popup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** 只在输入回调中读写；固定手势起点与静止面板边界，不读取形变后的测量坐标。 */
internal class PopupScrollDragState(
    private val touchSlop: Float,
    private val onChanged: (PopupScrollDragState) -> Unit,
) : NestedScrollConnection {
    var origin = Offset.Zero
        private set
    private var pointer = Offset.Zero
    private var bounds = Rect.Zero
    private var active = false
    private var interactive = false
    private var edgePull = 0f
    private var hasScrolled = false
    private var releasedPull = Offset.Zero
    private var flingPending = false
    private var flingStarted = false
    var blocked = false
        private set
    var feedbackStarted = false

    val canSelect: Boolean get() = active && !blocked && !interactive && !hasScrolled &&
        (pointer - origin).getDistance() <= touchSlop

    val pull: Offset
        get() {
            if (!active || blocked) return Offset.Zero
            val outside = if (interactive) Offset.Zero else popupPullBeyondBounds(pointer, bounds)
            // 同向取较大的距离，避免重复累计；反向连续抵消，不在跨边界时切换距离口径。
            val vertical = maxOf(0f, edgePull, outside.y) + minOf(0f, edgePull, outside.y)
            val raw = Offset(outside.x, vertical)
            val distance = raw.getDistance()
            // 阈值外从零连续接入，不在刚越过触摸阈值时突然施加整段位移。
            return if (distance <= touchSlop) Offset.Zero else raw * (1f - touchSlop / distance)
        }

    fun begin(position: Offset, panelBounds: Rect, interactive: Boolean, feedbackStarted: Boolean) {
        origin = position
        pointer = position
        bounds = panelBounds
        this.interactive = interactive
        this.feedbackStarted = feedbackStarted
        edgePull = 0f
        hasScrolled = false
        releasedPull = Offset.Zero
        flingPending = false
        flingStarted = false
        blocked = false
        active = true
    }

    fun move(position: Offset) {
        if (!active) return
        pointer = position
        val drag = pointer - origin
        if (interactive && abs(drag.x) > touchSlop && abs(drag.x) > abs(drag.y)) blocked = true
        onChanged(this)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!active || blocked || source != NestedScrollSource.UserInput) return Offset.Zero
        // 手指反向时先释放现有拉伸，释放完的剩余位移再交给子列表滚动。
        val released = when {
            edgePull > 0f && available.y < 0f -> maxOf(-edgePull, available.y)
            edgePull < 0f && available.y > 0f -> minOf(-edgePull, available.y)
            else -> 0f
        }
        if (released != 0f) {
            edgePull += released
            onChanged(this)
        }
        return Offset(0f, released)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!active || blocked || source != NestedScrollSource.UserInput) return Offset.Zero
        hasScrolled = hasScrolled || consumed.y != 0f || available.y != 0f
        // 消费量来自实际滚动子节点，支持内嵌 LazyColumn；零增量保留已累积的拉伸。
        edgePull += available.y
        onChanged(this)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // 子列表可在 Main 阶段启动惯性，早于宿主 Final 阶段收到松手。
        if (active && !blocked && hasScrolled) {
            releasedPull = pull
            flingPending = true
        }
        if (!flingPending) return Velocity.Zero
        flingStarted = true
        // 向边界外的速度由回弹接管；朝列表内部的速度继续交给列表。
        val outward = if (releasedPull.y * available.y > 0f) available.y else 0f
        return Velocity(0f, outward)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!flingPending) return Velocity.Zero
        flingPending = false
        releasedPull = Offset.Zero
        // 惯性到达边界后就地结束，不再次拉伸面板，也不传给背后的纵向滚动宿主。
        return Velocity(0f, available.y)
    }

    fun end(cancelled: Boolean = false) {
        if (cancelled) {
            releasedPull = Offset.Zero
            flingPending = false
        } else if (active && !flingStarted) {
            releasedPull = pull
            flingPending = !blocked && hasScrolled
        }
        active = false
        edgePull = 0f
        feedbackStarted = false
    }
}
