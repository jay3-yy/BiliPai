package com.android.purebilibili.feature.video.ambient

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Destination ownership remains with the source until the platform callback finishes. */
internal interface AmbientFrameSource {
    suspend fun capture(width: Int, height: Int): Bitmap?
    fun release()
    fun boundsInWindow(): androidx.compose.ui.geometry.Rect? = null
}

internal class ViewAmbientFrameSource(private val view: View) : AmbientFrameSource {
    private var scratch: Bitmap? = null
    private val location = IntArray(2)
    override fun boundsInWindow(): androidx.compose.ui.geometry.Rect? {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
        view.getLocationInWindow(location)
        return androidx.compose.ui.geometry.Rect(
            location[0].toFloat(), location[1].toFloat(),
            (location[0] + view.width).toFloat(), (location[1] + view.height).toFloat(),
        )
    }
    override suspend fun capture(width: Int, height: Int): Bitmap? = withContext(Dispatchers.Main.immediate) {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return@withContext null
        val destination = scratch?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                scratch?.recycle(); scratch = it
            }
        when (view) {
            is TextureView -> if (view.isAvailable) view.getBitmap(destination) else null
            is SurfaceView -> {
                if (!view.holder.surface.isValid) return@withContext null
                // PixelCopy has no cancellation API. Wait for completion before returning its
                // buffer, including on lifecycle cancellation. The controller discards late data.
                withContext(NonCancellable) {
                    suspendCoroutine { continuation ->
                        try {
                            PixelCopy.request(view, destination, { result ->
                                continuation.resume(destination.takeIf { result == PixelCopy.SUCCESS })
                            }, Handler(Looper.getMainLooper()))
                        } catch (_: IllegalArgumentException) { continuation.resume(null) }
                    }
                }
            }
            else -> null
        }
    }
    override fun release() { scratch?.recycle(); scratch = null }
}

internal data class AmbientRenderFrame(
    val raw: ImageBitmap,
    val glow: ImageBitmap,
    val atMs: Long,
    val transitionMs: Long = 120L,
    val refreshGeneration: Int = 0,
    val videoBoundsInWindow: androidx.compose.ui.geometry.Rect? = null,
    val contentBoundsInWindow: androidx.compose.ui.geometry.Rect? = videoBoundsInWindow,
)

@Stable
internal class AmbientPresentation {
    private var bindingOwner: Any? = null
    var previous by mutableStateOf<AmbientRenderFrame?>(null)
    var current by mutableStateOf<AmbientRenderFrame?>(null)
    var visible by mutableStateOf(false)
    var requiredRefresh by mutableStateOf(0)
    var epoch by mutableStateOf(0L)
    var layoutEnabled by mutableStateOf(false)
    var inlineBoundsInWindow by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var visibilityGate: () -> Boolean by mutableStateOf({ true })
    var videoBoundsInWindow: () -> androidx.compose.ui.geometry.Rect? by mutableStateOf({ null })
    var opacity by mutableStateOf(0.30f)
    var aspectRatio by mutableStateOf(16f / 9f)
    var failureFadeAt by mutableStateOf<Long?>(null)
    fun clear() { epoch++; previous = null; current = null; failureFadeAt = null }

    fun claimBinding(owner: Any) { bindingOwner = owner }

    /** 旧输出的退出回调不能清除新输出；同时解除捕获视频 View 的几何回调。 */
    fun releaseBinding(owner: Any): Boolean {
        if (bindingOwner !== owner) return false
        bindingOwner = null
        visibilityGate = { false }
        videoBoundsInWindow = { null }
        visible = false
        layoutEnabled = false
        return true
    }
}

internal data class AmbientRunPolicy(
    val active: Boolean,
    val glow: Boolean,
    val playing: Boolean,
    val saving: Boolean,
    val refreshGeneration: Int,
    val diagnosticLogging: Boolean,
)

/** A stopped sampler owns no timer; only a new observable policy can wake it. */
internal suspend fun awaitAmbientSamplePolicy(
    lastRefresh: Int,
    policy: () -> AmbientRunPolicy,
): AmbientRunPolicy = snapshotFlow { policy() }.first {
    it.active && shouldSampleAmbientFrame(it.playing, it.refreshGeneration, lastRefresh)
}

internal class AmbientFrameController(
    private val presentation: AmbientPresentation,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val sessionMutex = Mutex()
    private var generation = 0L
    fun invalidate() { generation++; presentation.clear() }

    suspend fun run(
        source: AmbientFrameSource,
        width: Int,
        height: Int,
        onFrame: ((ImageBitmap?) -> Unit)? = null,
        policy: () -> AmbientRunPolicy,
    ) {
        val session = ++generation
        sessionMutex.withLock {
            val analyzer = AmbientFrameAnalyzer()
            val (w, h) = ambientSampleSize(width, height)
            val pixels = IntArray(w * h)
            var failures = 0
            var lastRefresh = -1
            var frames = 0
            var reportAt = clock()
            try {
                while (currentCoroutineContext().isActive && generation == session) {
                    if (!policy().active) {
                        source.release()
                    }
                    // snapshotFlow waits without a timer while hidden or paused on the final frame.
                    val config = awaitAmbientSamplePolicy(lastRefresh, policy)
                    val started = clock()
                    val capturedBounds = source.boundsInWindow()
                    val sample = try { source.capture(w, h) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    currentCoroutineContext().ensureActive()
                    if (generation != session || !policy().active || policy().refreshGeneration != config.refreshGeneration) continue
                    // Discard a copy taken while the video surface moved/resized.
                    if (capturedBounds != source.boundsInWindow()) { delay(16); continue }
                    if (sample == null) {
                        failures++
                        if (failures >= 3) {
                            presentation.failureFadeAt = started
                            if (config.diagnosticLogging) android.util.Log.d("VideoAmbient", "sampling failed; retry backoff=2000ms")
                            delay(2000)
                            lastRefresh = -1
                            presentation.clear()
                            onFrame?.invoke(null)
                        } else {
                            // Surface 暂不可用（全屏切换重建中）：短间隔重试，避免模糊条回落黑底后
                            // 还要等满一个采样周期才恢复。
                            delay(100)
                        }
                        continue
                    } else {
                        val analyzed = withContext(Dispatchers.Default) {
                            // source 的复用缓冲在本轮处理完成前不会再次采样。
                            // 像素读取、快照复制和分析在同一 worker 顺序执行。
                            sample.getPixels(pixels, 0, w, 0, 0, w, h)
                            val raw = sample.copy(Bitmap.Config.ARGB_8888, false)
                                ?: return@withContext null
                            val crop = analyzer.analyze(pixels, w, h, started)
                            val glow = if (config.glow) {
                                // HardwareBufferRenderer 仅 API 34+ 可用；失败或低版本回退 CPU 内核。
                                val gpuGlow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    AmbientGpuBlurPolicy.glow(raw, crop)
                                } else {
                                    null
                                }
                                gpuGlow ?: Bitmap.createBitmap(
                                    analyzer.blur(pixels, w, h, crop), w, h, Bitmap.Config.ARGB_8888
                                )
                            } else {
                                raw
                            }
                            Triple(raw, crop, glow)
                        } ?: continue
                        val raw = analyzed.first
                        val result = analyzed.third
                        val contentBounds = capturedBounds?.let { bounds ->
                            val crop = analyzed.second
                            androidx.compose.ui.geometry.Rect(
                                bounds.left + bounds.width * crop.left / w,
                                bounds.top + bounds.height * crop.top / h,
                                bounds.left + bounds.width * crop.right / w,
                                bounds.top + bounds.height * crop.bottom / h,
                            )
                        }
                        currentCoroutineContext().ensureActive()
                        if (generation != session || !policy().active || policy().refreshGeneration != config.refreshGeneration) continue
                        // Never recycle published images: Compose/RenderThread may still reference them.
                        val oldCurrent = presentation.current
                        val oldPrevious = presentation.previous
                        val publishAt = clock()
                        val elapsed = publishAt - (oldCurrent?.atMs ?: 0)
                        presentation.previous = if (config.glow && oldCurrent != null && oldPrevious != null && elapsed < oldCurrent.transitionMs) {
                            val blended = withContext(Dispatchers.Default) {
                                val a = oldPrevious.glow.asAndroidBitmap()
                                val b = oldCurrent.glow.asAndroidBitmap()
                                if (a.width != b.width || a.height != b.height) b else {
                                    val ap = IntArray(a.width * a.height)
                                    val bp = IntArray(ap.size)
                                    a.getPixels(ap, 0, a.width, 0, 0, a.width, a.height)
                                    b.getPixels(bp, 0, b.width, 0, 0, b.width, b.height)
                                    val mix = (elapsed / oldCurrent.transitionMs.toFloat()).coerceIn(0f, 1f)
                                    for (i in ap.indices) {
                                        var c = -0x1000000
                                        for (channel in 0..2) {
                                            val shift = channel * 8
                                            val av = ap[i] shr shift and 255
                                            val bv = bp[i] shr shift and 255
                                            c = c or ((av + (bv - av) * mix).toInt().coerceIn(0, 255) shl shift)
                                        }
                                        ap[i] = c
                                    }
                                    Bitmap.createBitmap(ap, a.width, a.height, Bitmap.Config.ARGB_8888)
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            if (generation != session || !policy().active || policy().refreshGeneration != config.refreshGeneration) continue
                            oldCurrent.copy(glow = blended.asImageBitmap())
                        } else oldCurrent
                        val rawFrame = raw.asImageBitmap()
                        presentation.current = AmbientRenderFrame(rawFrame, result.asImageBitmap(), clock(),
                            if (config.refreshGeneration != lastRefresh) 48L else analyzer.transitionMs,
                            config.refreshGeneration, capturedBounds,
                            contentBoundsInWindow = contentBounds)
                        onFrame?.invoke(rawFrame)
                        presentation.failureFadeAt = null
                        failures = 0; frames++
                        lastRefresh = config.refreshGeneration
                    }
                    val now = clock()
                    if (config.diagnosticLogging && now - reportAt >= 10000) {
                        android.util.Log.d("VideoAmbient", "fps=${frames * 1000f / (now - reportAt)} sample_ms=${now - started} saving=${config.saving} static=${analyzer.static}")
                        frames = 0; reportAt = now
                    }
                    val dueAt = started + ambientIntervalMs(config.glow, config.saving, analyzer.static)
                    // Seek/resume/foreground events interrupt the interval, not an in-flight
                    // PixelCopy. Its callback still owns the destination until completion.
                    val remaining = (dueAt - clock()).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        withTimeoutOrNull(remaining) {
                            snapshotFlow { policy() }.first { latest ->
                                latest.active != config.active || latest.refreshGeneration != config.refreshGeneration ||
                                    latest.playing != config.playing || latest.saving != config.saving || latest.glow != config.glow
                            }
                        }
                    }
                }
            } finally { source.release() }
        }
    }
}
