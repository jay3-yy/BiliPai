package com.example.hdr_player

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GlRenderThread(
    private val outputSurface: Surface,
    private val hdrDisplaySupported: Boolean
) : Thread("GlRenderThread") {
    companion object {
        private const val TAG = "HdrGlRenderThread"
    }
    private val readyLatch = CountDownLatch(1)
    private val running = AtomicBoolean(true)
    private val frameSyncObject = Object()

    private var frameAvailable = false
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoPar = 1.0f
    private var videoRotation = 0

    private var eglCore: EglCore? = null
    private var windowSurface: WindowSurface? = null
    private var textureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var fullFrameRect: FullFrameRect? = null
    private var hdrRenderSupported = false
    private var histogramEnabled = false
    private var histogramListener: ((IntArray) -> Unit)? = null
    private var histogramFbo = 0
    private var histogramTex = 0
    private var histogramBuffer: ByteBuffer? = null
    private val histogramSize = 64
    private var frameCounter = 0

    @Volatile private var targetPeakNits = 1000f
    @Volatile private var strength = 1.0f
    @Volatile private var saturation = 1.0f
    @Volatile private var highlightBoost = 1.0f
    @Volatile private var preDarken = 0.0f
    @Volatile private var highlightProtect = 0.65f

    private val texMatrix = FloatArray(16)
    private val combinedTexMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)

    fun awaitReady(): Boolean {
        return readyLatch.await(5, TimeUnit.SECONDS)
    }

    fun isHdrRenderSupported(): Boolean = hdrRenderSupported

    fun getInputSurface(): Surface? = inputSurface

    fun setToneMapOptions(options: ToneMapOptions) {
        targetPeakNits = options.targetPeakNits.toFloat()
        strength = options.strength
        saturation = options.saturation
        highlightBoost = options.highlightBoost
        preDarken = options.preDarken
        highlightProtect = options.highlightProtect
        requestRender()
    }

    fun setViewportSize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        if (videoWidth <= 0 || videoHeight <= 0) {
            try {
                surfaceTexture?.setDefaultBufferSize(width, height)
            } catch (_: Exception) {
                // ignore
            }
        }
        requestRender()
    }

    fun setVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float, rotationDegrees: Int) {
        videoWidth = width
        videoHeight = height
        videoPar = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1.0f
        videoRotation = rotationDegrees % 360
        if (width > 0 && height > 0) {
            try {
                surfaceTexture?.setDefaultBufferSize(width, height)
            } catch (_: Exception) {
                // ignore
            }
        }
        requestRender()
    }

    fun setHistogramEnabled(enabled: Boolean) {
        histogramEnabled = enabled
        if (!enabled) {
            histogramListener = null
        }
    }

    fun setHistogramListener(listener: ((IntArray) -> Unit)?) {
        histogramListener = listener
    }

    fun shutdown() {
        running.set(false)
        synchronized(frameSyncObject) {
            frameSyncObject.notifyAll()
        }
    }

    override fun run() {
        try {
            Log.d(TAG, "GL thread start")
            eglCore = EglCore()
            windowSurface = if (hdrDisplaySupported) {
                try {
                    val eglSurface = eglCore!!.createWindowSurface(outputSurface, EglCore.EGL_GL_COLORSPACE_BT2020_PQ_EXT)
                    hdrRenderSupported = true
                    WindowSurface(eglCore!!, eglSurface)
                } catch (e: Exception) {
                    Log.w(TAG, "BT2020_PQ surface failed, fallback to SDR", e)
                    hdrRenderSupported = false
                    WindowSurface(eglCore!!, outputSurface)
                }
            } else {
                hdrRenderSupported = false
                WindowSurface(eglCore!!, outputSurface)
            }

            windowSurface!!.makeCurrent()
            textureId = GlUtil.createExternalTexture()
            surfaceTexture = SurfaceTexture(textureId)
            val frameHandler = Handler(Looper.getMainLooper())
            surfaceTexture!!.setOnFrameAvailableListener({
                synchronized(frameSyncObject) {
                    frameAvailable = true
                    frameSyncObject.notifyAll()
                }
            }, frameHandler)
            try {
                if (videoWidth > 0 && videoHeight > 0) {
                    surfaceTexture!!.setDefaultBufferSize(videoWidth, videoHeight)
                } else {
                    surfaceTexture!!.setDefaultBufferSize(
                        viewportWidth.coerceAtLeast(1),
                        viewportHeight.coerceAtLeast(1)
                    )
                }
            } catch (_: Exception) {
                // ignore
            }
            inputSurface = Surface(surfaceTexture)
            fullFrameRect = FullFrameRect()
            Log.d(TAG, "GL ready, hdrRenderSupported=$hdrRenderSupported, inputSurface=${inputSurface != null}")
            readyLatch.countDown()

            while (running.get()) {
                var shouldRender = false
                synchronized(frameSyncObject) {
                    if (!frameAvailable) {
                        frameSyncObject.wait(250)
                    }
                    if (frameAvailable) {
                        frameAvailable = false
                        shouldRender = true
                    }
                }

                if (!running.get()) break
                if (!shouldRender) continue

                windowSurface!!.makeCurrent()
                surfaceTexture!!.updateTexImage()
                surfaceTexture!!.getTransformMatrix(texMatrix)

                val viewport = computeViewport()
                GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                applyRotation(texMatrix, combinedTexMatrix, videoRotation)

                fullFrameRect!!.drawFrame(
                    textureId,
                    combinedTexMatrix,
                    targetPeakNits,
                    strength,
                    saturation,
                    highlightBoost,
                    preDarken,
                    highlightProtect,
                    hdrRenderSupported,
                    (System.nanoTime() / 1_000_000L).toInt()
                )
                windowSurface!!.swapBuffers()

                frameCounter++
                if (histogramEnabled && histogramListener != null && frameCounter % 6 == 0) {
                    renderHistogram()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GL thread failed", e)
            readyLatch.countDown()
        } finally {
            inputSurface?.release()
            surfaceTexture?.release()
            windowSurface?.release()
            eglCore?.release()
            releaseHistogram()
        }
    }

    private fun requestRender() {
        synchronized(frameSyncObject) {
            frameAvailable = true
            frameSyncObject.notifyAll()
        }
    }

    private fun computeViewport(): IntArray {
        val viewW = viewportWidth.coerceAtLeast(1)
        val viewH = viewportHeight.coerceAtLeast(1)
        var vw = videoWidth
        var vh = videoHeight
        if (vw <= 0 || vh <= 0) {
            return intArrayOf(0, 0, viewW, viewH)
        }
        if (videoRotation == 90 || videoRotation == 270) {
            val tmp = vw
            vw = vh
            vh = tmp
        }
        val videoAspect = (vw * videoPar) / vh.toFloat()
        val viewAspect = viewW.toFloat() / viewH.toFloat()
        return if (videoAspect > viewAspect) {
            val outW = viewW
            val outH = (viewW / videoAspect).toInt().coerceAtLeast(1)
            val y = ((viewH - outH) / 2).coerceAtLeast(0)
            intArrayOf(0, y, outW, outH)
        } else {
            val outH = viewH
            val outW = (viewH * videoAspect).toInt().coerceAtLeast(1)
            val x = ((viewW - outW) / 2).coerceAtLeast(0)
            intArrayOf(x, 0, outW, outH)
        }
    }

    private fun applyRotation(srcMatrix: FloatArray, outMatrix: FloatArray, rotationDegrees: Int) {
        if (rotationDegrees == 0) {
            System.arraycopy(srcMatrix, 0, outMatrix, 0, 16)
            return
        }
        Matrix.setIdentityM(rotationMatrix, 0)
        Matrix.translateM(rotationMatrix, 0, 0.5f, 0.5f, 0f)
        Matrix.rotateM(rotationMatrix, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
        Matrix.translateM(rotationMatrix, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(outMatrix, 0, rotationMatrix, 0, srcMatrix, 0)
    }

    private fun ensureHistogramResources() {
        if (histogramFbo != 0) return
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        histogramTex = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, histogramTex)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            histogramSize,
            histogramSize,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val fboIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fboIds, 0)
        histogramFbo = fboIds[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, histogramFbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            histogramTex,
            0
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        histogramBuffer = ByteBuffer.allocateDirect(histogramSize * histogramSize * 4)
            .order(ByteOrder.nativeOrder())
    }

    private fun renderHistogram() {
        ensureHistogramResources()
        val buffer = histogramBuffer ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, histogramFbo)
        GLES20.glViewport(0, 0, histogramSize, histogramSize)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        fullFrameRect!!.drawFrame(
            textureId,
            combinedTexMatrix,
            targetPeakNits,
            strength,
            saturation,
            highlightBoost,
            preDarken,
            highlightProtect,
            hdrRenderSupported,
            (System.nanoTime() / 1_000_000L).toInt()
        )
        buffer.rewind()
        GLES20.glReadPixels(
            0,
            0,
            histogramSize,
            histogramSize,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        val bins = IntArray(64)
        buffer.rewind()
        for (i in 0 until histogramSize * histogramSize) {
            val r = buffer.get().toInt() and 0xFF
            val g = buffer.get().toInt() and 0xFF
            val b = buffer.get().toInt() and 0xFF
            buffer.get()
            val luma = 0.2627f * r + 0.6780f * g + 0.0593f * b
            val idx = ((luma / 255f) * (bins.size - 1)).toInt().coerceIn(0, bins.size - 1)
            bins[idx]++
        }
        histogramListener?.invoke(bins)
    }

    private fun releaseHistogram() {
        if (histogramFbo != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(histogramFbo), 0)
            histogramFbo = 0
        }
        if (histogramTex != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(histogramTex), 0)
            histogramTex = 0
        }
    }
}

