package com.bytedance.danmaku.render.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.os.Looper
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

/**
 * Independent hardware-accelerated SurfaceView danmaku host.
 *
 * Renders directly onto its own media-overlay Surface via `holder.lockHardwareCanvas()`,
 * completely decoupling danmaku frame submission from the main Compose UI window and
 * allowing Android 11+ SurfaceFlinger / LTPO panels to pace the danmaku layer independently.
 */
internal class DanmakuSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SurfaceView(context, attrs, defStyleAttr), DanmakuRenderHost, SurfaceHolder.Callback {

    override val controller: DanmakuController = DanmakuController(this)

    private val cadence = RenderFrameCadence()
    private var framePending = false
    private var needsFrame = false
    private var wakeAtMs = 0L
    private var surfaceReady = false
    private var targetFrameRateHz = 0f
    private var cachedDisplayRefreshRateHz = 0f
    private var appliedSurfaceFrameRateHz = -1f
    private var currentAlpha = 255
    private var controllerReady = false

    private val wakeFrame = Runnable {
        wakeAtMs = 0L
        enqueueFrame()
    }

    private val renderFrame = Choreographer.FrameCallback { timeNs ->
        framePending = false
        if (canRender() && needsFrame) {
            if (cadence.shouldRender(timeNs)) {
                needsFrame = false
                cadence.onFrame(timeNs)
                drawToSurface()
            } else {
                enqueueFrame()
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
        updateCadenceForDisplay()
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { requestRenderFrame(delayMs) }
            return
        }
        needsFrame = true
        if (!canRender()) return
        if (delayMs <= 0L) {
            removeCallbacks(wakeFrame)
            wakeAtMs = 0L
            enqueueFrame()
        } else if (!framePending) {
            val deadline = SystemClock.uptimeMillis() + delayMs
            if (wakeAtMs == 0L || deadline < wakeAtMs) {
                removeCallbacks(wakeFrame)
                wakeAtMs = deadline
                postDelayed(wakeFrame, delayMs)
            }
        }
    }

    override fun cancelRenderFrames() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { cancelRenderFrames() }
            return
        }
        removeCallbacks(wakeFrame)
        if (framePending) Choreographer.getInstance().removeFrameCallback(renderFrame)
        framePending = false
        wakeAtMs = 0L
        needsFrame = false
        cadence.reset()
    }

    private fun canRender(): Boolean =
        surfaceReady && isAttachedToWindow && isShown && windowVisibility == VISIBLE

    private fun enqueueFrame() {
        if (!canRender() || framePending) return
        framePending = true
        Choreographer.getInstance().postFrameCallback(renderFrame)
    }

    private fun updateCadenceForDisplay() {
        val displayHz = display?.refreshRate ?: 0f
        if (displayHz.isFinite() && displayHz > 0f) {
            cachedDisplayRefreshRateHz = displayHz
        }
        cadence.updateCadence(targetFps = targetFrameRateHz, displayHz = cachedDisplayRefreshRateHz)
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
        if (!force && appliedSurfaceFrameRateHz == targetFrameRateHz) return
        appliedSurfaceFrameRateHz = targetFrameRateHz
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                surface.setFrameRate(
                    targetFrameRateHz,
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
                )
            } else {
                surface.setFrameRate(
                    targetFrameRateHz,
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                )
            }
        }
    }

    private fun drawToSurface() {
        if (!surfaceReady) return
        val surface = holder.surface ?: return
        if (!surface.isValid) return
        val canvas = try {
            holder.lockHardwareCanvas()
        } catch (_: Throwable) {
            null
        } ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val needsSoftwareAlphaLayer = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && currentAlpha < 255
            val saveCount = if (needsSoftwareAlphaLayer) {
                @Suppress("DEPRECATION")
                canvas.saveLayerAlpha(
                    0f,
                    0f,
                    canvas.width.toFloat(),
                    canvas.height.toFloat(),
                    currentAlpha,
                )
            } else {
                -1
            }
            controller.draw(this, canvas)
            if (saveCount >= 0) {
                canvas.restoreToCount(saveCount)
            }
        } finally {
            runCatching { holder.unlockCanvasAndPost(canvas) }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        appliedSurfaceFrameRateHz = -1f
        applySurfaceAlphaInternal()
        updateCadenceForDisplay()
        applySurfaceFrameRateHint(force = true)
        requestRenderFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            controller.onLayoutSizeChanged(width, height)
        }
        applySurfaceAlphaInternal()
        updateCadenceForDisplay()
        applySurfaceFrameRateHint()
        requestRenderFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        appliedSurfaceFrameRateHz = -1f
        cancelRenderFrames()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (surfaceReady) {
            updateCadenceForDisplay()
            applySurfaceFrameRateHint()
            requestRenderFrame()
        }
    }

    override fun onDetachedFromWindow() {
        cancelRenderFrames()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (controllerReady) updateVisibilityScheduling()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (controllerReady) updateVisibilityScheduling()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (controllerReady && hasWindowFocus) {
            updateCadenceForDisplay()
        }
    }

    private fun updateVisibilityScheduling() {
        if (canRender()) {
            updateCadenceForDisplay()
            applySurfaceFrameRateHint()
            requestRenderFrame()
        } else {
            cancelRenderFrames()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            updateCadenceForDisplay()
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
