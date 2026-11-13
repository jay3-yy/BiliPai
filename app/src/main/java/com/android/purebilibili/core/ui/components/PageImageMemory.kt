package com.android.purebilibili.core.ui.components

import android.graphics.Canvas
import android.graphics.drawable.Animatable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.Image
import java.util.Collections
import java.util.WeakHashMap

/** 主线程同步释放页面持有的像素，不依赖后台窗口继续重组或提交帧。 */
internal class PageImageMemory {
    var released by mutableStateOf(false)
        private set
    var generation by mutableLongStateOf(0L)
        private set
    private val slots = mutableSetOf<PageImageSlot>()

    fun register(slot: PageImageSlot) {
        slots.add(slot)
        if (released) slot.release()
    }

    fun unregister(slot: PageImageSlot) {
        slots.remove(slot)
        slot.dispose()
    }

    fun release() {
        released = true
        slots.forEach { it.release() }
        generation++
    }

    fun resume() { released = false }
}

internal val AppPageImageMemory = PageImageMemory()
internal val LocalPageImageMemory = androidx.compose.runtime.staticCompositionLocalOf { AppPageImageMemory }

internal class PageImageSlot(private val memory: PageImageMemory) {
    private val generation = memory.generation
    private var disposed = false
    val active: Boolean get() = !disposed && !memory.released && generation == memory.generation
    // 跟踪仍被请求状态或页面引用的资源，不额外保留已被新结果替换的旧图片。
    private val resources = Collections.newSetFromMap(WeakHashMap<ReleasablePageImage, Boolean>())
    var retained by mutableStateOf<ReleasablePageImage?>(null)
        private set

    fun hold(image: Image?, painter: Painter?): ReleasablePageImage =
        ReleasablePageImage(image, painter).also {
            if (!active) it.release() else resources.add(it)
        }

    fun retain(resource: ReleasablePageImage) {
        if (active) retained = resource
    }

    fun release() {
        resources.forEach { it.release() }
        resources.clear()
        retained = null
    }

    fun dispose() {
        disposed = true
        release()
    }
}

/** 请求状态、静态回放与占位只引用此持有器；回收时同时断开 Image 与 Painter。 */
internal class ReleasablePageImage(
    image: Image?,
    painter: Painter?,
) : Painter(), Image, RememberObserver {
    var image: Image? = image
        private set
    private var delegate: Painter? = painter
    private var remembered = false
    private var drawAlpha = 1f
    private var drawColorFilter: ColorFilter? = null
    private val originalSize = painter?.intrinsicSize ?: Size.Unspecified
    override val width = image?.width ?: -1
    override val height = image?.height ?: -1
    override val shareable = image?.shareable ?: true
    override val size: Long get() = image?.size ?: 0L
    override val intrinsicSize: Size get() = originalSize
    val isBitmap: Boolean get() = image is BitmapImage

    override fun DrawScope.onDraw() {
        val drawSize = this.size
        delegate?.let { with(it) { draw(drawSize, drawAlpha, drawColorFilter) } }
    }
    override fun draw(canvas: Canvas) { image?.draw(canvas) }
    override fun applyAlpha(alpha: Float): Boolean { drawAlpha = alpha; return true }
    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean { drawColorFilter = colorFilter; return true }
    override fun onRemembered() {
        if (!remembered) (delegate as? RememberObserver)?.onRemembered()
        remembered = true
    }
    override fun onForgotten() {
        if (remembered) (delegate as? RememberObserver)?.onForgotten()
        remembered = false
    }
    override fun onAbandoned() = onForgotten()

    fun stopAnimation() {
        (image as? DrawableImage)?.drawable?.let {
            it.setVisible(true, false)
            (it as? Animatable)?.stop()
        }
    }

    fun release() {
        onForgotten()
        (image as? DrawableImage)?.drawable?.let { (it as? Animatable)?.stop() }
        image = null
        delegate = null
    }
}
