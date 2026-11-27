package com.android.purebilibili.danmaku.engine

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.bytedance.danmaku.render.engine.DanmakuRenderHost
import com.bytedance.danmaku.render.engine.DanmakuSurfaceView
import com.bytedance.danmaku.render.engine.DanmakuView
import com.bytedance.danmaku.render.engine.utils.CMD_SET_TOUCHABLE

/** Android View host that keeps the vendored renderer out of app-layer APIs. */
class DanmakuRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    val usesIndependentSurface: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rendererHost: DanmakuRenderHost = if (usesIndependentSurface) {
        DanmakuSurfaceView(context)
    } else {
        DanmakuView(context)
    }

    private val rendererView: View = rendererHost.asView()

    val engine: DanmakuEngine = ByteDanceDanmakuEngine(rendererHost)

    init {
        clipChildren = false
        clipToPadding = false
        addView(
            rendererView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setRendererTouchable(touchable: Boolean) {
        rendererView.isClickable = touchable
        rendererView.isLongClickable = touchable
        rendererHost.controller.executeCommand(CMD_SET_TOUCHABLE, param = touchable)
    }

    /**
     * Controls visibility of the inner renderer surface directly.
     * Essential for SurfaceView mode because parent Compose `graphicsLayer { alpha = 0f }`
     * does not hide an independent SurfaceFlinger plane.
     */
    fun setSurfaceOverlayVisible(visible: Boolean) {
        val targetVisibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (rendererView.visibility != targetVisibility) {
            rendererView.visibility = targetVisibility
        }
    }

    fun releaseRenderer() {
        engine.close()
        removeAllViews()
    }
}
