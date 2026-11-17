/* ThanosEffect TextureView/EGL host port. See docs/telegram-thanos-port.md. */
package com.android.purebilibili.core.ui.animation.gl

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.view.Window
import java.util.concurrent.atomic.AtomicBoolean

internal fun isThanosEffectSupported(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return supportsThanosGlVersion(manager.deviceConfigurationInfo.reqGlEsVersion)
}

private fun particleBudget(context: Context): Int {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return when {
        manager == null || manager.isLowRamDevice -> 30_000
        manager.memoryClass >= 256 -> 120_000
        else -> 60_000
    }
}

/** A transparent window-wide TextureView, so particles can leave their list card. */
internal class ThanosEffectView private constructor(
    context: Context,
    private val bitmap: Bitmap,
    private val bounds: RectF,
    private var onFirstFrame: (() -> Unit)?,
    private var onComplete: (() -> Unit)?,
    private var onFinalTail: (() -> Unit)?,
    private val sweepReversed: Boolean,
    private val dissolveOrigin: android.graphics.PointF?,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var session: RenderSession? = null
    private var disposed = false
    private var firstFrameDelivered = false
    private var frameScheduled = false
    private val choreographer = Choreographer.getInstance()
    private val frameCallback = Choreographer.FrameCallback { frameNanos ->
        frameScheduled = false
        if (!disposed) session?.draw(frameNanos)
    }

    init {
        isOpaque = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (disposed) return
        session = RenderSession(
            context, bitmap, bounds, surface, width, height, particleBudget(context), sweepReversed, dissolveOrigin,
            scheduleFrame = { if (firstFrameDelivered) scheduleNextFrame() },
            complete = { if (!disposed) onComplete?.invoke() },
            finalTail = { if (!disposed) onFinalTail?.invoke() },
        ).also { it.start() }
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!disposed && !firstFrameDelivered) {
            // Handoff only once TextureView has acquired the first successfully swapped frame.
            firstFrameDelivered = true
            onFirstFrame?.invoke()
            scheduleNextFrame()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        session?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val current = session ?: return true
        current.destroySurface()
        post { if (!disposed) onComplete?.invoke() }
        return false // The EGL thread releases SurfaceTexture after destroying its EGL surface.
    }

    private fun scheduleNextFrame() {
        if (disposed || frameScheduled) return
        frameScheduled = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        onFirstFrame = null
        onComplete = null
        onFinalTail = null
        choreographer.removeFrameCallback(frameCallback)
        session?.close()
        (parent as? ViewGroup)?.removeView(this)
        if (session == null && !bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        /** Takes ownership of bitmap if a view is returned. */
        fun attach(
            window: Window,
            bitmap: Bitmap,
            windowBounds: RectF,
            onFirstFrame: () -> Unit,
            onComplete: () -> Unit,
            onFinalTail: (() -> Unit)? = null,
            sweepReversed: Boolean = false,
            // 窗口坐标中的触发按钮中心；提供时从该点向外消散。
            originInWindow: android.graphics.PointF? = null,
        ): ThanosEffectView? {
            val root = window.decorView as? ViewGroup ?: return null
            if (!root.isAttachedToWindow || !isThanosEffectSupported(root.context)) return null
            val location = IntArray(2)
            root.getLocationInWindow(location)
            val bounds = RectF(windowBounds).apply { offset(-location[0].toFloat(), -location[1].toFloat()) }
            val origin = originInWindow?.let {
                android.graphics.PointF(
                    (it.x - windowBounds.left) / windowBounds.width(),
                    (it.y - windowBounds.top) / windowBounds.height(),
                )
            }
            val view = ThanosEffectView(root.context, bitmap, bounds, onFirstFrame, onComplete, onFinalTail, sweepReversed, origin)
            return try {
                root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                view
            } catch (error: RuntimeException) {
                Log.w("ThanosEffect", "Cannot attach particle overlay", error)
                view.dispose()
                null
            }
        }
    }
}

/** Owns the EGL context, renderer and snapshot. UI never waits for this thread to exit. */
private class RenderSession(
    private val context: Context,
    private val bitmap: Bitmap,
    private val bounds: RectF,
    private val surfaceTexture: SurfaceTexture,
    private val initialWidth: Int,
    private val initialHeight: Int,
    private val budget: Int,
    private val sweepReversed: Boolean,
    private val dissolveOrigin: android.graphics.PointF?,
    private val scheduleFrame: () -> Unit,
    private val complete: () -> Unit,
    private val finalTail: () -> Unit,
) {
    private val thread = HandlerThread("BiliPai-Thanos")
    private lateinit var handler: Handler
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val surfaceDestroyed = AtomicBoolean(false)
    private var finalTailDelivered = false
    private var resourcesReleased = false
    private var display = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var nativeSurface: Surface? = null
    private var renderer: ParticleRenderer? = null

    fun start() {
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            if (closed.get()) return@post
            runSafely {
                initializeEgl()
                renderer = ParticleRenderer(
                    context.resources, bitmap, bounds, context.resources.displayMetrics.density, budget, sweepReversed, dissolveOrigin,
                )
                renderer?.initialize(initialWidth, initialHeight)
                // Reset frame has zero delta. Subsequent draws wait for UI's first-frame handoff.
                render(System.nanoTime())
            }
        }
    }

    fun draw(frameNanos: Long) {
        if (!closed.get()) handler.post { if (!closed.get()) runSafely { render(frameNanos) } }
    }

    fun resize(width: Int, height: Int) {
        if (!closed.get()) handler.post { if (!closed.get()) runSafely { renderer?.resize(width, height) } }
    }

    fun close() {
        closed.set(true)
        handler.post { releaseEgl() }
    }

    fun destroySurface() {
        if (!surfaceDestroyed.compareAndSet(false, true)) return
        closed.set(true)
        handler.post {
            try {
                releaseEgl()
            } finally {
                surfaceTexture.release()
                thread.quitSafely()
            }
        }
    }

    private fun initializeEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Cannot initialize EGL" }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No RGBA GLES3 EGL config"
        }
        val config = checkNotNull(configs[0])
        eglContext = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Cannot create GLES3 context" }
        nativeSurface = Surface(surfaceTexture)
        eglSurface = EGL14.eglCreateWindowSurface(display, config, nativeSurface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Cannot create EGL window surface" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) { "Cannot make EGL current" }
    }

    private fun render(frameNanos: Long) {
        val finished = checkNotNull(renderer).draw(frameNanos)
        check(EGL14.eglSwapBuffers(display, eglSurface)) { "Cannot swap EGL buffers" }
        if (!finalTailDelivered && checkNotNull(renderer).isInFinalTail()) {
            finalTailDelivered = true
            // Post after a successful swap, in order before the completion callback.
            main.post { finalTail() }
        }
        if (finished) finish() else main.post { if (!closed.get()) scheduleFrame() }
    }

    private fun finish() {
        if (!closed.compareAndSet(false, true)) return
        releaseEgl()
        main.post { complete() }
    }

    private inline fun runSafely(block: () -> Unit) {
        try {
            block()
        } catch (error: RuntimeException) {
            Log.w("ThanosEffect", "Particle rendering unavailable", error)
            finish()
        }
    }

    private fun releaseEgl() {
        if (resourcesReleased) return
        resourcesReleased = true
        try {
            renderer?.release()
        } finally {
            renderer = null
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglTerminate(display)
                EGL14.eglReleaseThread()
            }
            nativeSurface?.release()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
