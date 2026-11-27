package com.android.purebilibili.feature.anime4k.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import com.android.purebilibili.plugin.sdk.Anime4KConfig

/** Anime4K 的可见输出 SurfaceView，解码器输入 Surface 由 renderer 异步提供。 */
class Anime4KGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    initialConfig: Anime4KConfig = Anime4KConfig()
) : GLSurfaceView(context, attrs) {

    var onInputSurfaceChanged: (Surface?) -> Unit = {}
    var onFirstFrameRendered: () -> Unit = {}
    var onPipelineError: (Throwable) -> Unit = {}

    private val pipelineRenderer = Anime4KPipelineRenderer(
        context = context,
        initialConfig = initialConfig,
        onFrameAvailable = { requestRender() },
        onInputSurfaceChanged = { surface -> post { onInputSurfaceChanged(surface) } },
        onFirstFrameRendered = { post { onFirstFrameRendered() } },
        onPipelineError = { error -> post { onPipelineError(error) } }
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        setPreserveEGLContextOnPause(true)
        setRenderer(pipelineRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateConfig(config: Anime4KConfig) {
        queueEvent {
            pipelineRenderer.setConfig(config)
            // 切换算法或模型完成后再请求绘制，避免旧请求先被消费后画面停在透明帧。
            requestRender()
        }
    }

    fun updateInputSize(width: Int, height: Int) {
        queueEvent { pipelineRenderer.setInputSize(width, height) }
    }

    fun updateFlip(horizontal: Boolean, vertical: Boolean) {
        queueEvent { pipelineRenderer.setFlip(horizontal, vertical) }
        requestRender()
    }

    fun updateDisplayScaleMode(scaleMode: Anime4KDisplayScaleMode) {
        queueEvent { pipelineRenderer.setDisplayScaleMode(scaleMode) }
        requestRender()
    }

    fun updateTargetFrameRateHz(frameRateHz: Float) {
        queueEvent { pipelineRenderer.setTargetFrameRateHz(frameRateHz) }
    }

    override fun onResume() {
        super.onResume()
        queueEvent { pipelineRenderer.ensureInputSurface() }
    }

    override fun onPause() {
        queueEvent { pipelineRenderer.onHostPaused() }
        super.onPause()
    }

    override fun onDetachedFromWindow() {
        queueEvent { pipelineRenderer.release() }
        super.onDetachedFromWindow()
        // GLSurfaceView 会先处理退出标记，队列里的 release 未必执行。
        // super 已等待 GL 线程退出，EGL 资源由其销毁；独立输入资源在这里兜底归还。
        pipelineRenderer.releaseInputAfterThreadExit()
    }
}
