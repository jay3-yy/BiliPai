package com.bytedance.danmaku.render.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.bytedance.danmaku.render.engine.control.DanmakuController

private const val PLAYBACK_ANCHOR_SYNC_INTERVAL_MS = 500L

/**
 * Independent hardware-accelerated SurfaceView danmaku host.
 *
 * Renders on a dedicated `HandlerThread` driven by that thread's own `Choreographer`,
 * submitting directly onto its media-overlay Surface via `holder.lockHardwareCanvas()`.
 * This decouples both danmaku typesetting/drawing and RenderThread buffer submission from the
 * main Compose UI thread while preserving Android 11+ SurfaceFlinger / LTPO frame-rate hints.
 */
internal class DanmakuSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SurfaceView(context, attrs, defStyleAttr), DanmakuRenderHost, SurfaceHolder.Callback {

    override val controller: DanmakuController = DanmakuController(this)

    private val threadLock = Any()
    /**
     * 串行保护 Surface 帧提交与 `surfaceDestroyed` 销毁边界：
     * 锁顺序严格单向（`surfaceLock` -> `controller.renderLock`），`surfaceDestroyed` 仅获取 `surfaceLock`，
     * 保证回调返回前在途帧已执行完 `unlockCanvasAndPost`，且后续任意线程都不会再访问已销毁的 Surface。
     */
    private val surfaceLock = Any()
    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    // 以下字段仅在渲染线程读写，无跨线程竞态。
    private val cadence = RenderFrameCadence()
    private var renderChoreographer: Choreographer? = null
    private var framePending = false
    private var needsFrame = false
    private var wakeAtMs = 0L
    private var lastAnchorSyncMs = 0L

    // 以下状态由主线程维护并通过 volatile 发布给渲染线程。
    @Volatile
    private var surfaceReady = false
    @Volatile
    private var hostVisible = false
    @Volatile
    private var pendingFrameBeforeThread = false
    @Volatile
    private var targetFrameRateHz = 0f
    @Volatile
    private var cachedDisplayRefreshRateHz = 0f
    @Volatile
    private var currentAlpha = 255

    // 以下字段仅在主线程读写。
    private var appliedSurfaceFrameRateHz = -1f
    private var controllerReady = false

    private val syncPlaybackAnchorOnMain = Runnable {
        if (surfaceReady && hostVisible) {
            controller.refreshPlaybackAnchorFromMainThread()
        }
    }

    private val wakeFrame = Runnable {
        wakeAtMs = 0L
        enqueueFrameOnRenderThread()
    }

    private val renderFrame = Choreographer.FrameCallback { timeNs ->
        framePending = false
        if (canRender() && needsFrame) {
            if (cadence.shouldRender(timeNs)) {
                needsFrame = false
                cadence.onFrame(timeNs)
                drawToSurfaceOnRenderThread()
            } else {
                enqueueFrameOnRenderThread()
            }
        }
    }

    init {
        setZOrderMediaOverlay(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        setWillNotDraw(true)
        controllerReady = true
    }

    override fun asView(): View = this

    override fun setTargetFrameRateHz(fps: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { setTargetFrameRateHz(fps) }
            return
        }
        val normalized = if (fps.isFinite() && fps > 0f) fps else 0f
        if (targetFrameRateHz == normalized) return
        targetFrameRateHz = normalized
        updateCadenceForDisplayOnMain()
        applySurfaceFrameRateHint()
    }

    override fun applyDanmakuAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        if (currentAlpha == clamped) return
        currentAlpha = clamped
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { applySurfaceAlphaInternal() }
            return
        }
        applySurfaceAlphaInternal()
    }

    override fun requestRenderFrame(delayMs: Long) {
        val handler = synchronized(threadLock) { renderHandler }
        if (handler == null) {
            pendingFrameBeforeThread = true
            return
        }
        if (Looper.myLooper() === handler.looper) {
            requestRenderFrameOnRenderThread(delayMs)
        } else {
            handler.post { requestRenderFrameOnRenderThread(delayMs) }
        }
    }

    override fun cancelRenderFrames() {
        pendingFrameBeforeThread = false
        val handler = synchronized(threadLock) { renderHandler } ?: return
        if (Looper.myLooper() === handler.looper) {
            cancelRenderFramesOnRenderThread()
        } else {
            handler.post { cancelRenderFramesOnRenderThread() }
        }
    }

    private fun canRender(): Boolean = surfaceReady && hostVisible

    private fun ensureRenderThreadStarted(): Handler {
        synchronized(threadLock) {
            val existingThread = renderThread
            val existingHandler = renderHandler
            if (existingThread != null && existingThread.isAlive && existingHandler != null) {
                return existingHandler
            }
            val thread = HandlerThread("BiliPai-DanmakuRender", Process.THREAD_PRIORITY_DISPLAY).apply {
                start()
            }
            val handler = Handler(thread.looper)
            renderThread = thread
            renderHandler = handler
            handler.post {
                renderChoreographer = Choreographer.getInstance()
                cadence.updateCadence(
                    targetFps = targetFrameRateHz,
                    displayHz = cachedDisplayRefreshRateHz,
                )
            }
            return handler
        }
    }

    private fun stopRenderThread() {
        val threadToQuit: HandlerThread?
        val handlerToDrain: Handler?
        synchronized(threadLock) {
            threadToQuit = renderThread
            handlerToDrain = renderHandler
            renderThread = null
            renderHandler = null
        }
        if (handlerToDrain != null && threadToQuit != null && threadToQuit.isAlive) {
            handlerToDrain.post {
                cancelRenderFramesOnRenderThread()
                renderChoreographer = null
            }
        }
        threadToQuit?.quitSafely()
    }

    private fun requestRenderFrameOnRenderThread(delayMs: Long) {
        needsFrame = true
        if (!canRender()) return
        val handler = synchronized(threadLock) { renderHandler } ?: return
        if (delayMs <= 0L) {
            handler.removeCallbacks(wakeFrame)
            wakeAtMs = 0L
            enqueueFrameOnRenderThread()
        } else if (!framePending) {
            val deadline = SystemClock.uptimeMillis() + delayMs
            if (wakeAtMs == 0L || deadline < wakeAtMs) {
                handler.removeCallbacks(wakeFrame)
                wakeAtMs = deadline
                handler.postDelayed(wakeFrame, delayMs)
            }
        }
    }

    private fun cancelRenderFramesOnRenderThread() {
        val handler = synchronized(threadLock) { renderHandler }
        handler?.removeCallbacks(wakeFrame)
        if (framePending) {
            renderChoreographer?.removeFrameCallback(renderFrame)
        }
        framePending = false
        wakeAtMs = 0L
        needsFrame = false
        cadence.reset()
    }

    private fun enqueueFrameOnRenderThread() {
        if (!canRender() || framePending) return
        val choreographer = renderChoreographer ?: Choreographer.getInstance().also {
            renderChoreographer = it
        }
        framePending = true
        choreographer.postFrameCallback(renderFrame)
    }

    private fun updateHostVisibilityOnMain(): Boolean {
        val visible = isAttachedToWindow && isShown && windowVisibility == VISIBLE
        hostVisible = visible
        return visible
    }

    private fun updateCadenceForDisplayOnMain() {
        val displayHz = display?.refreshRate ?: 0f
        if (displayHz.isFinite() && displayHz > 0f) {
            cachedDisplayRefreshRateHz = displayHz
        }
        val target = targetFrameRateHz
        val displayRate = cachedDisplayRefreshRateHz
        val handler = synchronized(threadLock) { renderHandler } ?: return
        handler.post {
            cadence.updateCadence(targetFps = target, displayHz = displayRate)
        }
    }

    private fun applySurfaceAlphaInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sc = surfaceControl
            if (sc != null && sc.isValid) {
                runCatching {
                    SurfaceControl.Transaction().use { transaction ->
                        transaction.setAlpha(sc, currentAlpha / 255f).apply()
                    }
                }
            }
        } else {
            requestRenderFrame()
        }
    }

    private fun applySurfaceFrameRateHint(force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !surfaceReady) return
        val surface = holder.surface ?: return
        if (!surface.isValid) return
        val target = targetFrameRateHz
        if (!force && appliedSurfaceFrameRateHz == target) return
        appliedSurfaceFrameRateHz = target
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                surface.setFrameRate(
                    target,
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
                )
            } else {
                surface.setFrameRate(
                    target,
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                )
            }
        }
    }

    private fun drawToSurfaceOnRenderThread() {
        var didDrawFrame = false
        synchronized(surfaceLock) {
            if (!canRender()) return
            val surface = holder.surface ?: return
            if (!surface.isValid) return
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                null
            } ?: return
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                val alpha = currentAlpha
                val needsSoftwareAlphaLayer = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && alpha < 255
                val saveCount = if (needsSoftwareAlphaLayer) {
                    @Suppress("DEPRECATION")
                    canvas.saveLayerAlpha(
                        0f,
                        0f,
                        canvas.width.toFloat(),
                        canvas.height.toFloat(),
                        alpha,
                    )
                } else {
                    -1
                }
                controller.draw(this, canvas)
                if (saveCount >= 0) {
                    canvas.restoreToCount(saveCount)
                }
                didDrawFrame = true
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
        if (didDrawFrame) {
            val nowMs = SystemClock.uptimeMillis()
            if (nowMs - lastAnchorSyncMs >= PLAYBACK_ANCHOR_SYNC_INTERVAL_MS &&
                controller.hasPlaybackPositionProvider()
            ) {
                lastAnchorSyncMs = nowMs
                post(syncPlaybackAnchorOnMain)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        synchronized(surfaceLock) {
            surfaceReady = true
        }
        updateHostVisibilityOnMain()
        appliedSurfaceFrameRateHz = -1f
        ensureRenderThreadStarted()
        applySurfaceAlphaInternal()
        updateCadenceForDisplayOnMain()
        applySurfaceFrameRateHint(force = true)
        pendingFrameBeforeThread = false
        requestRenderFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            controller.onLayoutSizeChanged(width, height)
        }
        applySurfaceAlphaInternal()
        updateCadenceForDisplayOnMain()
        applySurfaceFrameRateHint()
        requestRenderFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        appliedSurfaceFrameRateHz = -1f
        removeCallbacks(syncPlaybackAnchorOnMain)
        // 无超时阻塞等待正在进行的 drawToSurfaceOnRenderThread 完成 unlockCanvasAndPost，
        // 即使 onDetachedFromWindow 已提前清空渲染线程引用也能严格守住 Surface 销毁契约。
        synchronized(surfaceLock) {
            surfaceReady = false
        }
        cancelRenderFrames()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val visible = updateHostVisibilityOnMain()
        if (surfaceReady && visible) {
            ensureRenderThreadStarted()
            updateCadenceForDisplayOnMain()
            applySurfaceFrameRateHint()
            requestRenderFrame()
        }
    }

    override fun onDetachedFromWindow() {
        hostVisible = false
        removeCallbacks(syncPlaybackAnchorOnMain)
        stopRenderThread()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (controllerReady) updateVisibilitySchedulingOnMain()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (controllerReady) updateVisibilitySchedulingOnMain()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (controllerReady && hasWindowFocus) {
            updateCadenceForDisplayOnMain()
        }
    }

    private fun updateVisibilitySchedulingOnMain() {
        val visible = updateHostVisibilityOnMain()
        if (surfaceReady && visible) {
            ensureRenderThreadStarted()
            updateCadenceForDisplayOnMain()
            applySurfaceFrameRateHint()
            if (pendingFrameBeforeThread) {
                pendingFrameBeforeThread = false
            }
            requestRenderFrame()
        } else {
            removeCallbacks(syncPlaybackAnchorOnMain)
            cancelRenderFrames()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            updateCadenceForDisplayOnMain()
        }
        controller.onLayoutSizeChanged(right - left, bottom - top)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (controller.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }
}
