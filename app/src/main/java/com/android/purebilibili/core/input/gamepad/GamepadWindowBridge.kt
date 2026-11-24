package com.android.purebilibili.core.input.gamepad

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.ui.window.DialogWindowProvider
import com.android.purebilibili.core.util.Logger
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private const val TAG = "GamepadWindowBridge"
private const val WINDOW_ROOTS_CACHE_TTL_MS = 180L
private const val CURSOR_MODULE_REANCHOR_DP = 48f

/**
 * 进程内多窗口桥接器：
 * 1. 自动识别 Compose Dialog / ModalBottomSheet 创建的独立 Window，包装其 Window.Callback
 *    以持续截获手柄按键与摇杆事件；
 * 2. 注入合成点击、拖拽、悬停和原生高帧率连续滚动事件时，自动定位光标所处的精确模块与窗口；
 * 3. 在弹窗窗口覆盖 Activity 时，通过 DecorView.overlay 绘制同步光标，无需悬浮窗权限且不被弹窗遮挡。
 */
internal class GamepadWindowBridge(
    private val onKeyEvent: (KeyEvent) -> Boolean,
    private val onGenericMotionEvent: (MotionEvent) -> Boolean,
    private val onRealTouch: () -> Unit,
) {
    private var activeActivityRef: WeakReference<ComponentActivity>? = null
    private val hookedWindows = WeakHashMap<Window, Boolean>()

    @Volatile
    var isDispatchingSynthetic: Boolean = false
        private set

    private val reusableLoc = IntArray(2)
    private val reusableHoverProps = arrayOf(
        MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
    )
    private val reusableHoverCoords = arrayOf(
        MotionEvent.PointerCoords().apply {
            pressure = 0f
            size = 1f
        }
    )

    private var cachedGlobalRoots: List<View> = emptyList()
    private var cachedGlobalRootsAtMs: Long = 0L

    private var activePointerDownTimeMs: Long = 0L
    private var activePointerTargetRef: WeakReference<View>? = null

    private var activeScrollDragDownTimeMs: Long = 0L
    private var lastScrollMoveTimeMs: Long = 0L
    private var activeScrollDragAnchorScreenX: Float = 0f
    private var activeScrollDragAnchorScreenY: Float = 0f
    private var activeScrollDragStartLocalX: Float = 0f
    private var activeScrollDragStartLocalY: Float = 0f
    private var activeScrollDragCurrentLocalX: Float = 0f
    private var activeScrollDragCurrentLocalY: Float = 0f
    private var activeScrollDragAxis: GamepadScrollAxis? = null
    private var activeScrollDragCrossedSlop: Boolean = false
    private var activeScrollDragTargetRef: WeakReference<View>? = null

    private var dialogOverlayRootRef: WeakReference<View>? = null
    private val dialogCursorDrawable = DialogCursorOverlayDrawable()

    val isScrollDragActive: Boolean
        get() = activeScrollDragTargetRef?.get()?.isAttachedToWindow == true

    fun attachActivity(activity: ComponentActivity) {
        activeActivityRef = WeakReference(activity)
        invalidateWindowRootsCache()
        syncSubWindowCallbacks()
    }

    fun detachActivity(activity: ComponentActivity) {
        if (activeActivityRef?.get() === activity) {
            cancelActiveGestures()
            clearDialogCursorOverlay()
            invalidateWindowRootsCache()
            activeActivityRef = null
        }
    }

    fun currentActivity(): ComponentActivity? {
        return activeActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    /** 检查当前屏幕尺寸（px）。 */
    fun currentViewportSize(): Pair<Int, Int> {
        val decor = currentActivity()?.window?.decorView
        if (decor != null && decor.width > 0 && decor.height > 0) {
            return decor.width to decor.height
        }
        val metrics = currentActivity()?.resources?.displayMetrics
        return if (metrics != null) {
            metrics.widthPixels to metrics.heightPixels
        } else {
            1080 to 1920
        }
    }

    /** 检查当前是否处于系统软键盘展开或文本输入框获焦状态。 */
    fun isTextInputFocused(): Boolean {
        val topRoot = resolveTopmostRootView(forceRefresh = true) ?: return false
        val focused = topRoot.findFocus() ?: return false
        return focused.onCheckIsTextEditor()
    }

    /** 发现并包装所有 Compose Dialog / ModalBottomSheet 窗口的 Window.Callback。 */
    fun syncSubWindowCallbacks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        invalidateWindowRootsCache()
        val activityWindow = currentActivity()?.window
        val roots = getGlobalWindowViewsCached(forceRefresh = true)
        for (root in roots) {
            if (!root.isAttachedToWindow || root.visibility != View.VISIBLE) continue
            val dialogWindow = findDialogWindow(root) ?: continue
            if (dialogWindow === activityWindow) continue
            if (hookedWindows.containsKey(dialogWindow)) continue
            val currentCallback = dialogWindow.callback ?: continue
            if (currentCallback is GamepadWindowCallbackProxy) {
                hookedWindows[dialogWindow] = true
                continue
            }
            dialogWindow.callback = GamepadWindowCallbackProxy(
                original = currentCallback,
                onKey = onKeyEvent,
                onGenericMotion = onGenericMotionEvent,
                onTouch = { ev ->
                    if (!isDispatchingSynthetic &&
                        ev.actionMasked == MotionEvent.ACTION_DOWN &&
                        ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                    ) {
                        onRealTouch()
                    }
                },
            )
            hookedWindows[dialogWindow] = true
            Logger.d(TAG, "Hooked Dialog Window.Callback for gamepad routing")
        }
    }

    /** 当顶层是独立 Dialog 窗口时，同步在 Dialog DecorView.overlay 上绘制光标，避免被弹窗遮挡。 */
    fun syncDialogCursorOverlay(
        visible: Boolean,
        screenX: Float,
        screenY: Float,
        pressed: Boolean,
        precisionAim: Boolean,
        density: Float,
        cursorSize: GamepadCursorSize = GamepadCursorSize.MEDIUM,
        cursorColorStyle: GamepadCursorColorStyle = GamepadCursorColorStyle.CLASSIC_WHITE,
    ) {
        val activityDecor = currentActivity()?.window?.decorView
        val topRoot = resolveTopmostRootView(forceRefresh = false)
        val isDialogOnTop = topRoot != null && topRoot !== activityDecor
        if (!visible || !isDialogOnTop || topRoot == null) {
            clearDialogCursorOverlay()
            return
        }
        val previousRoot = dialogOverlayRootRef?.get()
        if (previousRoot !== topRoot) {
            previousRoot?.overlay?.remove(dialogCursorDrawable)
            dialogOverlayRootRef = WeakReference(topRoot)
            topRoot.overlay.add(dialogCursorDrawable)
        }
        topRoot.getLocationOnScreen(reusableLoc)
        dialogCursorDrawable.setBounds(0, 0, topRoot.width, topRoot.height)
        dialogCursorDrawable.update(
            localX = screenX - reusableLoc[0],
            localY = screenY - reusableLoc[1],
            pressed = pressed,
            precisionAim = precisionAim,
            density = density,
            cursorSize = cursorSize,
            cursorColorStyle = cursorColorStyle,
        )
        topRoot.postInvalidateOnAnimation()
    }

    fun clearDialogCursorOverlay() {
        dialogOverlayRootRef?.get()?.overlay?.remove(dialogCursorDrawable)
        dialogOverlayRootRef = null
        invalidateWindowRootsCache()
    }

    fun injectHover(screenX: Float, screenY: Float) {
        if (isScrollDragActive) return
        val target = resolveTargetRootForPoint(screenX, screenY, forceRefresh = false) ?: return
        val (localX, localY) = toLocalCoordinates(target, screenX, screenY)
        val now = SystemClock.uptimeMillis()
        reusableHoverCoords[0].x = localX
        reusableHoverCoords[0].y = localY
        val event = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_HOVER_MOVE,
            1,
            reusableHoverProps,
            reusableHoverCoords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
        dispatchSyntheticGenericMotion(target, event)
    }

    fun injectPointerDown(screenX: Float, screenY: Float) {
        finishScrollDragIfActive(cancel = true)
        val target = resolveTargetRootForPoint(screenX, screenY, forceRefresh = true) ?: return
        val (localX, localY) = toLocalCoordinates(target, screenX, screenY)
        val now = SystemClock.uptimeMillis()
        activePointerDownTimeMs = now
        activePointerTargetRef = WeakReference(target)
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, localX, localY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        dispatchSyntheticTouch(target, event)
    }

    fun injectPointerMove(screenX: Float, screenY: Float) {
        val target = activePointerTargetRef?.get()?.takeIf { it.isAttachedToWindow } ?: return
        val (localX, localY) = toLocalCoordinates(target, screenX, screenY)
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            activePointerDownTimeMs,
            now,
            MotionEvent.ACTION_MOVE,
            localX,
            localY,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        dispatchSyntheticTouch(target, event)
    }

    fun injectPointerUp(screenX: Float, screenY: Float) {
        val target = activePointerTargetRef?.get()?.takeIf { it.isAttachedToWindow }
        activePointerTargetRef = null
        if (target == null) return
        val (localX, localY) = toLocalCoordinates(target, screenX, screenY)
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            activePointerDownTimeMs,
            now,
            MotionEvent.ACTION_UP,
            localX,
            localY,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        dispatchSyntheticTouch(target, event)
    }

    fun cancelActiveGestures() {
        activePointerTargetRef?.get()?.takeIf { it.isAttachedToWindow }?.let { target ->
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(
                activePointerDownTimeMs,
                now,
                MotionEvent.ACTION_CANCEL,
                0f,
                0f,
                0,
            ).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            dispatchSyntheticTouch(target, cancel)
        }
        activePointerTargetRef = null
        finishScrollDragIfActive(cancel = true)
    }

    /**
     * 高帧率原生连续触控拖拽滚动（锚定在当前光标所在模块）：
     * - 相比逐帧派发 ACTION_SCROLL 鼠标滚轮事件，原生连续触摸拖拽不会反复打断 Compose
     *   MouseWheelScrollingLogic 动画，也不会导致 isScrollInProgress 60~120Hz 抖动触发模糊/贴合重绘；
     * - 仅在开始滚动、切换轴向或光标移入新模块（移动 > 48dp）时查询一次目标窗口与坐标，
     *   滚动过程中每帧仅执行一次轻量级 ACTION_MOVE，帧率与真实手指滑动一致；
     * - 首帧即越过 scaledTouchSlop，杜绝误触发卡片点击或长按菜单。
     */
    fun injectContinuousScroll(
        screenX: Float,
        screenY: Float,
        dxPx: Float,
        dyPx: Float,
        axis: GamepadScrollAxis,
        density: Float,
    ) {
        if (activePointerTargetRef?.get() != null) return
        if (abs(dxPx) < 0.05f && abs(dyPx) < 0.05f) return

        val now = SystemClock.uptimeMillis()
        val safeDensity = density.coerceAtLeast(1f)
        val currentTarget = activeScrollDragTargetRef?.get()?.takeIf { it.isAttachedToWindow }

        val cursorMovedToAnotherModule = currentTarget != null && hypot(
            (screenX - activeScrollDragAnchorScreenX).toDouble(),
            (screenY - activeScrollDragAnchorScreenY).toDouble(),
        ).toFloat() > CURSOR_MODULE_REANCHOR_DP * safeDensity

        val maxDragSpan = if (currentTarget != null) {
            max(max(currentTarget.width, currentTarget.height) * 2.2f, 2400f * safeDensity)
        } else {
            2400f * safeDensity
        }
        val exceededSpan = currentTarget != null && (
            abs(activeScrollDragCurrentLocalX - activeScrollDragStartLocalX) > maxDragSpan ||
                abs(activeScrollDragCurrentLocalY - activeScrollDragStartLocalY) > maxDragSpan
            )

        val needStartDrag = currentTarget == null ||
            activeScrollDragAxis != axis ||
            cursorMovedToAnotherModule ||
            exceededSpan

        if (needStartDrag) {
            if (currentTarget != null) {
                finishScrollDragIfActive(cancel = true)
            }
            val target = resolveTargetRootForPoint(screenX, screenY, forceRefresh = true) ?: return
            val (rawLocalX, rawLocalY) = toLocalCoordinates(target, screenX, screenY)
            val edgeMargin = 4f * safeDensity
            val maxX = (target.width - edgeMargin).coerceAtLeast(edgeMargin)
            val maxY = (target.height - edgeMargin).coerceAtLeast(edgeMargin)
            val anchorLocalX = rawLocalX.coerceIn(edgeMargin, maxX)
            val anchorLocalY = rawLocalY.coerceIn(edgeMargin, maxY)

            val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
                .toFloat()
                .coerceAtLeast(8f * safeDensity)
            val slopBreakout = touchSlop + 2f
            val slopDx = if (axis == GamepadScrollAxis.HORIZONTAL) {
                if (dxPx >= 0f) slopBreakout else -slopBreakout
            } else {
                0f
            }
            val slopDy = if (axis == GamepadScrollAxis.VERTICAL) {
                if (dyPx >= 0f) slopBreakout else -slopBreakout
            } else {
                0f
            }

            val downTime = (now - 8L).coerceAtLeast(0L)
            activeScrollDragDownTimeMs = downTime
            lastScrollMoveTimeMs = now
            activeScrollDragAnchorScreenX = screenX
            activeScrollDragAnchorScreenY = screenY
            activeScrollDragStartLocalX = anchorLocalX
            activeScrollDragStartLocalY = anchorLocalY
            activeScrollDragCurrentLocalX = anchorLocalX + slopDx + dxPx
            activeScrollDragCurrentLocalY = anchorLocalY + slopDy + dyPx
            activeScrollDragAxis = axis
            activeScrollDragCrossedSlop = true
            activeScrollDragTargetRef = WeakReference(target)

            val down = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                anchorLocalX,
                anchorLocalY,
                0,
            ).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            dispatchSyntheticTouch(target, down)

            val firstMove = MotionEvent.obtain(
                downTime,
                now,
                MotionEvent.ACTION_MOVE,
                activeScrollDragCurrentLocalX,
                activeScrollDragCurrentLocalY,
                0,
            ).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            dispatchSyntheticTouch(target, firstMove)
            return
        }

        // 连续帧热路径：零窗口查找、零数组分配，直接派发单次 ACTION_MOVE（保证时间戳严格递增）。
        val eventTime = maxOf(now, lastScrollMoveTimeMs + 1L)
        lastScrollMoveTimeMs = eventTime
        activeScrollDragCurrentLocalX += dxPx
        activeScrollDragCurrentLocalY += dyPx
        val move = MotionEvent.obtain(
            activeScrollDragDownTimeMs,
            eventTime,
            MotionEvent.ACTION_MOVE,
            activeScrollDragCurrentLocalX,
            activeScrollDragCurrentLocalY,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        dispatchSyntheticTouch(currentTarget, move)
    }

    fun finishScrollDragIfActive(cancel: Boolean = false) {
        val target = activeScrollDragTargetRef?.get()?.takeIf { it.isAttachedToWindow }
        val crossedSlop = activeScrollDragCrossedSlop
        activeScrollDragTargetRef = null
        activeScrollDragAxis = null
        activeScrollDragCrossedSlop = false
        if (target == null) return
        val eventTime = maxOf(SystemClock.uptimeMillis(), lastScrollMoveTimeMs + 1L)
        lastScrollMoveTimeMs = eventTime
        val action = if (cancel || !crossedSlop) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
        val endEvent = MotionEvent.obtain(
            activeScrollDragDownTimeMs,
            eventTime,
            action,
            activeScrollDragCurrentLocalX,
            activeScrollDragCurrentLocalY,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        dispatchSyntheticTouch(target, endEvent)
    }

    fun dispatchSyntheticKeyPress(keyCode: Int) {
        val target = resolveTopmostRootView(forceRefresh = true) ?: return
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, -1, 0, 0, InputDevice.SOURCE_DPAD)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0, 0, InputDevice.SOURCE_DPAD)
        isDispatchingSynthetic = true
        try {
            target.dispatchKeyEvent(down)
            target.dispatchKeyEvent(up)
        } finally {
            isDispatchingSynthetic = false
        }
    }

    /** 触发回退：若有独立弹窗在顶层，先向弹窗发送 BACK；否则调用 Activity onBackPressedDispatcher。 */
    fun dispatchBack() {
        val activity = currentActivity() ?: return
        val activityDecor = activity.window.decorView
        val topRoot = resolveTopmostRootView(forceRefresh = true)
        if (topRoot != null && topRoot !== activityDecor) {
            dispatchSyntheticKeyPress(KeyEvent.KEYCODE_BACK)
            return
        }
        isDispatchingSynthetic = true
        try {
            activity.onBackPressedDispatcher.onBackPressed()
        } finally {
            isDispatchingSynthetic = false
        }
    }

    private fun dispatchSyntheticTouch(target: View, event: MotionEvent): Boolean {
        isDispatchingSynthetic = true
        return try {
            target.dispatchTouchEvent(event)
        } finally {
            isDispatchingSynthetic = false
            event.recycle()
        }
    }

    private fun dispatchSyntheticGenericMotion(target: View, event: MotionEvent): Boolean {
        isDispatchingSynthetic = true
        return try {
            target.dispatchGenericMotionEvent(event)
        } finally {
            isDispatchingSynthetic = false
            event.recycle()
        }
    }

    private fun invalidateWindowRootsCache() {
        cachedGlobalRootsAtMs = 0L
        cachedGlobalRoots = emptyList()
    }

    private fun getGlobalWindowViewsCached(forceRefresh: Boolean): List<View> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val now = SystemClock.uptimeMillis()
        if (!forceRefresh && cachedGlobalRoots.isNotEmpty() && now - cachedGlobalRootsAtMs < WINDOW_ROOTS_CACHE_TTL_MS) {
            return cachedGlobalRoots
        }
        val fresh = runCatching { WindowInspector.getGlobalWindowViews() }.getOrNull().orEmpty()
        cachedGlobalRoots = fresh
        cachedGlobalRootsAtMs = now
        return fresh
    }

    private fun resolveTopmostRootView(forceRefresh: Boolean): View? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roots = getGlobalWindowViewsCached(forceRefresh)
            for (i in roots.lastIndex downTo 0) {
                val root = roots[i]
                if (!isInteractiveRoot(root)) continue
                return root
            }
        }
        return currentActivity()?.window?.decorView?.takeIf { it.isAttachedToWindow }
    }

    private fun resolveTargetRootForPoint(
        screenX: Float,
        screenY: Float,
        forceRefresh: Boolean,
    ): View? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roots = getGlobalWindowViewsCached(forceRefresh)
            var topmostInteractive: View? = null
            for (i in roots.lastIndex downTo 0) {
                val root = roots[i]
                if (!isInteractiveRoot(root)) continue
                if (topmostInteractive == null) {
                    topmostInteractive = root
                }
                root.getLocationOnScreen(reusableLoc)
                val inside = screenX >= reusableLoc[0] &&
                    screenX <= reusableLoc[0] + root.width &&
                    screenY >= reusableLoc[1] &&
                    screenY <= reusableLoc[1] + root.height
                if (inside) return root
                val params = root.layoutParams as? WindowManager.LayoutParams
                val isModal = params != null && (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) == 0
                if (isModal) return root
            }
            if (topmostInteractive != null) return topmostInteractive
        }
        return currentActivity()?.window?.decorView?.takeIf { it.isAttachedToWindow }
    }

    private fun isInteractiveRoot(root: View): Boolean {
        if (!root.isAttachedToWindow || root.visibility != View.VISIBLE) return false
        if (root.width <= 0 || root.height <= 0) return false
        val params = root.layoutParams as? WindowManager.LayoutParams
        if (params != null && (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
            return false
        }
        return true
    }

    private fun toLocalCoordinates(target: View, screenX: Float, screenY: Float): Pair<Float, Float> {
        target.getLocationOnScreen(reusableLoc)
        return (screenX - reusableLoc[0]) to (screenY - reusableLoc[1])
    }

    private fun findDialogWindow(root: View): Window? {
        val queue = ArrayDeque<View>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 48) {
            val current = queue.removeFirst()
            visited++
            if (current is DialogWindowProvider) {
                return current.window
            }
            if (current is ViewGroup) {
                for (index in 0 until current.childCount) {
                    queue.add(current.getChildAt(index))
                }
            }
        }
        return null
    }
}

private class GamepadWindowCallbackProxy(
    private val original: Window.Callback,
    private val onKey: (KeyEvent) -> Boolean,
    private val onGenericMotion: (MotionEvent) -> Boolean,
    private val onTouch: (MotionEvent) -> Unit,
) : Window.Callback by original {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onKey(event)) return true
        return original.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (onGenericMotion(event)) return true
        return original.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onTouch(event)
        return original.dispatchTouchEvent(event)
    }
}

private class DialogCursorOverlayDrawable : Drawable() {
    private var x: Float = 0f
    private var y: Float = 0f
    private var pressed: Boolean = false
    private var precisionAim: Boolean = false
    private var density: Float = 2f
    private var cursorSize: GamepadCursorSize = GamepadCursorSize.MEDIUM
    private var cursorColorStyle: GamepadCursorColorStyle = GamepadCursorColorStyle.CLASSIC_WHITE

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun update(
        localX: Float,
        localY: Float,
        pressed: Boolean,
        precisionAim: Boolean,
        density: Float,
        cursorSize: GamepadCursorSize,
        cursorColorStyle: GamepadCursorColorStyle,
    ) {
        this.x = localX
        this.y = localY
        this.pressed = pressed
        this.precisionAim = precisionAim
        this.density = density.coerceAtLeast(1f)
        this.cursorSize = cursorSize
        this.cursorColorStyle = cursorColorStyle
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val scale = cursorSize.radiusScale
        val outerRadius = (if (pressed) 13f else if (precisionAim) 11f else 15f) * scale * density
        val idleColor = when (cursorColorStyle) {
            GamepadCursorColorStyle.CLASSIC_WHITE -> Color.argb(235, 255, 255, 255)
            GamepadCursorColorStyle.BILI_PINK -> Color.argb(240, 251, 114, 153)
            GamepadCursorColorStyle.CYAN -> Color.argb(240, 56, 189, 248)
            GamepadCursorColorStyle.GOLD -> Color.argb(240, 250, 204, 21)
        }
        val accentColor = when {
            pressed -> if (cursorColorStyle == GamepadCursorColorStyle.BILI_PINK) {
                Color.argb(245, 255, 255, 255)
            } else {
                Color.argb(240, 251, 114, 153)
            }
            precisionAim -> Color.argb(235, 56, 189, 248)
            else -> idleColor
        }
        haloPaint.color = if (pressed) Color.argb(72, 251, 114, 153) else Color.argb(56, 0, 0, 0)
        canvas.drawCircle(x, y, outerRadius + 3f * density, haloPaint)

        ringPaint.color = accentColor
        ringPaint.strokeWidth = 2.4f * density
        canvas.drawCircle(x, y, outerRadius, ringPaint)

        centerPaint.color = accentColor
        canvas.drawCircle(x, y, 2.8f * scale * density, centerPaint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
