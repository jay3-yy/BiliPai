package com.android.purebilibili.feature.video

import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.Choreographer
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnLayout
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.transition.VideoCardMorphFrame
import com.android.purebilibili.core.ui.transition.VideoCardMorphPhysics
import com.android.purebilibili.core.ui.transition.VideoCardMorphSprings
import com.android.purebilibili.core.ui.transition.resolveDeviceDisplayCornerRadiusPx
import com.android.purebilibili.core.ui.transition.resolveVideoCardWholeCardReturnHandoffAlpha
import com.android.purebilibili.core.ui.transition.resolveVideoCardMorphScale
import com.android.purebilibili.core.ui.transition.resolveVideoCardMorphCornerPx
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionMaxBlurRadiusPx
import com.android.purebilibili.core.ui.transition.resolveVideoCardTransitionScrimAlpha
import java.util.function.Consumer
import kotlin.math.roundToInt

/** 视频专属透明窗口：内容在应用内形变，背景模糊交给窗口合成器。 */
@androidx.annotation.RequiresApi(31)
class AppVideoActivity : VideoActivity(), Choreographer.FrameCallback {
    private val token get() = intent.getStringExtra(AppVideoCardWindow.EXTRA_SESSION)
    private val session get() = AppVideoCardWindow.session(token)
    private lateinit var surface: FrameLayout
    private var seed: ImageView? = null
    private var detail: View? = null
    private var physics = VideoCardMorphPhysics()
    private var frame = VideoCardMorphFrame.at(0f)
    private var driver: TargetBasedAnimation<Float, *>? = null
    private var settlement: Any? = null
    private var startNanos = 0L
    private var pausedAtNanos = 0L
    private var depth = 0f
    private var closing = false
    private var motionEnabled = true
    private var blurEnabled = true
    private var systemBlurEnabled = false
    private var root = false
    private var radius = 0f
    private var clipHeight = 0
    private var returnBounds: Rect? = null
    private var gestureStartDepth = 1f
    private var started = false
    private var openingFramePresented = false
    private var openingCompleted = false
    private var waitingForOpeningFrame = false
    private var openingSeedCommitted = false
    private val startOpening = Runnable {
        if (waitingForOpeningFrame && openingSeedCommitted && started &&
            window.decorView.hasWindowFocus() && !closing && !isFinishing
        ) {
            waitingForOpeningFrame = false
            openingFramePresented = true
            animateTo(1f)
        }
    }
    private var lastFallbackRadius = 0
    private var scheduledEffects: AppVideoCardWindowEffects? = null
    private var effectsGeneration = 0L
    private val decorLocation = IntArray(2)
    private val blurListener = Consumer<Boolean> { enabled ->
        systemBlurEnabled = enabled
        if (::surface.isInitialized) applyFrame()
    }
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            stopFrames()
            returnBounds = currentReturnBounds()
            gestureStartDepth = depth
            val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            animationScale = if (scale == 0f) 1f else scale
            // 从触摸开始计时，避免首个进度把停留详情页的时间算进松手速度。
            physics.sample(depth, null, 0f, 0, true, System.nanoTime(), animationScale)
        }
        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            if (!motionEnabled || returnBounds == null) return
            depth = gestureStartDepth * (1f - backEvent.progress)
            frame = physics.sample(depth, null, 0f, 0, true, System.nanoTime(), animationScale)
            applyFrame()
        }
        override fun handleOnBackCancelled() { animateTo(1f) }
        override fun handleOnBackPressed() { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = findViewById(android.R.id.content)
        surface.pivotX = 0f
        surface.pivotY = 0f
        surface.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, clipHeight, radius)
            }
        }
        onBackPressedDispatcher.addCallback(this, back)
        // 透明窗口挂载前清除默认 dim，等待卡片首帧提交后再按同一时钟推进。
        window.attributes = window.attributes.apply { blurBehindRadius = 0; dimAmount = 0f }
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        systemBlurEnabled = windowManager.isCrossWindowBlurEnabled
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
        val source = session
        if (source != null) {
            detail = surface.getChildAt(0)
            surface.setBackgroundColor(source.backgroundColor)
            seed = ImageView(this).apply {
                setImageBitmap(source.pixels)
                setBackgroundColor(source.backgroundColor)
                scaleType = ImageView.ScaleType.FIT_XY
                surface.addView(this, FrameLayout.LayoutParams(-1, 1))
            }
        }
        if (source != null && savedInstanceState == null) {
            waitingForOpeningFrame = true
            surface.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    applyFrame()
                    // 占位图尺寸变更必须先完成布局，不能把一像素高的首帧交给窗口。
                    if (seed?.isLayoutRequested == true) return false
                    surface.viewTreeObserver.removeOnPreDrawListener(this)
                    // 先提交位于来源落点的卡片像素，再让形变和背景模糊共同推进。
                    surface.viewTreeObserver.registerFrameCommitCallback {
                        openingSeedCommitted = true
                        surface.post(startOpening)
                    }
                    return true
                }
            })
        } else {
            openingFramePresented = true
            openingCompleted = true
            depth = 1f
            frame = VideoCardMorphFrame.at(1f)
            physics.sample(1f, null, 1f, 0, false, System.nanoTime())
        }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    internal fun updateRootState(isRoot: Boolean) {
        root = isRoot
        back.isEnabled = root && !isFullscreen && !isInPipMode && session != null
    }

    internal override fun updateOfficialReturnMotion(enabled: Boolean, realtimeBlurEnabled: Boolean) {
        super.updateOfficialReturnMotion(enabled, realtimeBlurEnabled)
        motionEnabled = enabled
        blurEnabled = realtimeBlurEnabled
        if (!enabled) {
            stopFrames()
            depth = if (closing) 0f else 1f
            if (depth == 1f) openingCompleted = true
            frame = VideoCardMorphFrame.at(depth)
            physics.sample(depth, null, depth, 0, false, System.nanoTime())
            if (closing) finishWindow()
        }
        if (::surface.isInitialized) applyFrame()
    }

    private fun animateTo(target: Float) {
        stopFrames()
        val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (!motionEnabled || scale == 0f) {
            depth = target
            if (target == 1f) openingCompleted = true
            frame = VideoCardMorphFrame.at(target)
            physics.sample(depth, null, depth, 0, false, System.nanoTime())
            applyFrame()
            if (closing) finishWindow()
            return
        }
        driver = TargetBasedAnimation(
            if (target == 0f) VideoCardMorphSprings.returnLifetime else VideoCardMorphSprings.enterLifetime,
            Float.VectorConverter, depth, target,
        )
        settlement = Any()
        // 起点取启动时刻；下一次 vsync 直接采样已走过的时间，不额外显示一帧零进度。
        startNanos = System.nanoTime()
        animationScale = scale
        if (started && window.decorView.hasWindowFocus()) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            pausedAtNanos = startNanos
        }
    }
    private var animationScale = 1f

    override fun doFrame(frameTimeNanos: Long) {
        val animation = driver ?: return
        if (!started || !window.decorView.hasWindowFocus()) {
            pauseFrames()
            return
        }
        val elapsed = resolveAppVideoCardElapsedNanos(startNanos, frameTimeNanos, animationScale)
        depth = animation.getValueFromNanos(elapsed)
        frame = physics.sample(depth, settlement, animation.targetValue, elapsed, false, frameTimeNanos)
        applyFrame()
        if (animation.isFinishedFromNanos(elapsed) && physics.isFinished(elapsed)) {
            depth = animation.targetValue
            if (depth == 1f) openingCompleted = true
            frame = VideoCardMorphFrame.at(depth)
            physics.sample(depth, null, depth, 0, false, frameTimeNanos)
            applyFrame()
            driver = null
            if (closing) finishWindow()
        } else Choreographer.getInstance().postFrameCallback(this)
    }

    private fun applyFrame() {
        if (surface.width == 0 || surface.height == 0) return
        val source = session
        val bounds = returnBounds ?: source?.bounds
        val p = frame.size
        if (bounds == null || isInPipMode || !motionEnabled) {
            seed?.alpha = 0f
            detail?.alpha = 1f
            surface.scaleX = 1f; surface.scaleY = 1f
            surface.translationX = 0f; surface.translationY = 0f
            surface.clipToOutline = false
        } else {
            val dimensions = resolveAppVideoCardWindowSize(
                bounds.width().toFloat(), bounds.height().toFloat(),
                surface.width.toFloat(), surface.height.toFloat(), p,
            )
            val visibleWidth = dimensions.width
            val visibleHeight = dimensions.height
            val scale = visibleWidth / surface.width
            // decor coordinates stay independent of the animated content view.
            window.decorView.getLocationOnScreen(decorLocation)
            val x = bounds.exactCenterX() - decorLocation[0] + (surface.width / 2f - (bounds.exactCenterX() - decorLocation[0])) * frame.position
            val y = bounds.exactCenterY() - decorLocation[1] + (surface.height / 2f - (bounds.exactCenterY() - decorLocation[1])) * frame.position
            surface.scaleX = scale; surface.scaleY = scale
            surface.translationX = x - visibleWidth / 2
            surface.translationY = y - visibleHeight / 2
            clipHeight = (visibleHeight / scale).roundToInt()
            val windowRadius = resolveDeviceDisplayCornerRadiusPx(window.decorView.rootWindowInsets)
            radius = resolveAppVideoCardWindowRadius(
                source?.clipRadius ?: 0f, windowRadius, frame.corner, scale, visibleWidth, visibleHeight,
            )
            // 满屏时也保留外框裁剪，直到物理屏幕圆角自然覆盖它。
            surface.clipToOutline = true
            surface.invalidateOutline()
            seed?.let { image ->
                val pixels = requireNotNull(source).pixels
                val height = (pixels.height.toFloat() / pixels.width * surface.width).roundToInt()
                if (image.layoutParams.height != height) image.layoutParams = image.layoutParams.apply { this.height = height }
                image.alpha = resolveAppVideoCardSeedAlpha(p, openingCompleted)
                // 截图覆盖完整详情后逐渐让出画面；两层同时淡化会在中段额外透出底色。
                detail?.alpha = 1f
            }
        }
        // 位置、尺寸、圆角、模糊与遮罩各用自己的弹簧，不能以尺寸通道代替其他属性。
        val effectsVisible = motionEnabled && openingFramePresented && started && !isInPipMode && bounds != null
        val requestedBlur = if (effectsVisible && blurEnabled) (40f * resources.displayMetrics.density * frame.effects).roundToInt() else 0
        val blur = if (systemBlurEnabled) requestedBlur else 0
        // 来源页回退复用同窗路径的 RenderEffect 峰值；窗口合成器保留独立半径参数。
        val fallback = if (systemBlurEnabled || requestedBlur == 0) 0 else (
            resolveVideoCardTransitionMaxBlurRadiusPx(MotionTier.Normal, resources.displayMetrics.density) * frame.effects
        ).roundToInt()
        val dim = if (!effectsVisible) 0f else
            resolveVideoCardTransitionScrimAlpha(
                frame.mask, source != null && Color(source.backgroundColor).luminance() > .5f, MotionTier.Normal,
            )
        val effects = AppVideoCardWindowEffects(blur, fallback, dim)
        if (effects != scheduledEffects && window.decorView.hasWindowFocus()) {
            scheduledEffects = effects
            val generation = effectsGeneration
            // 固定本次内容帧的参数，不能在提交回调中读取下一帧尚未提交的进度。
            surface.viewTreeObserver.registerFrameCommitCallback {
                surface.post {
                    if (generation == effectsGeneration && started && !isFinishing) commitEffects(effects)
                }
            }
            surface.invalidate()
        }
    }

    private fun commitEffects(effects: AppVideoCardWindowEffects) {
        if (effects.fallbackRadius != lastFallbackRadius) {
            val r = effects.fallbackRadius.toFloat()
            session?.sourceContent?.get()?.setRenderEffect(
                if (r > 0f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null,
            )
            lastFallbackRadius = effects.fallbackRadius
        }
        window.attributes = window.attributes.apply {
            blurBehindRadius = effects.blurRadius
            dimAmount = effects.dimAmount
        }
    }

    override fun finish() {
        if (closing || isFinishing) return
        returnBounds = currentReturnBounds()
        if (!::surface.isInitialized || returnBounds == null || isInPipMode ||
            !started || !window.decorView.hasWindowFocus()
        ) {
            finishWindow()
            return
        }
        closing = true
        animateTo(0f)
    }

    private fun finishWindow() {
        cancelPendingEffects()
        cancelPendingOpening()
        clearFallback()
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
    private fun currentReturnBounds(): Rect? {
        val source = session ?: return null
        return source.anchor.bounds(false)?.takeIf {
            it.width() == source.bounds.width() && it.height() == source.bounds.height() &&
                source.anchor.radiusPx() == source.radius
        }
    }
    private fun stopFrames() {
        Choreographer.getInstance().removeFrameCallback(this)
        driver = null
        pausedAtNanos = 0L
    }
    private fun pauseFrames() {
        Choreographer.getInstance().removeFrameCallback(this)
        if (driver != null && pausedAtNanos == 0L) pausedAtNanos = System.nanoTime()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::surface.isInitialized) return
        if (!hasFocus) {
            pauseFrames()
            cancelPendingEffects()
        } else if (started) {
            if (waitingForOpeningFrame && openingSeedCommitted) surface.post(startOpening)
            if (driver != null && pausedAtNanos != 0L) {
                startNanos += System.nanoTime() - pausedAtNanos
                pausedAtNanos = 0L
                Choreographer.getInstance().postFrameCallback(this)
            }
            applyFrame()
        }
    }
    override fun onStop() {
        started = false
        cancelPendingEffects()
        cancelPendingOpening()
        openingFramePresented = true
        openingCompleted = true
        stopFrames()
        if (closing && !isFinishing) finishWindow()
        depth = 1f
        frame = VideoCardMorphFrame.at(1f)
        physics.sample(1f, null, 1f, 0, false, System.nanoTime())
        if (::surface.isInitialized) {
            window.attributes = window.attributes.apply { blurBehindRadius = 0; dimAmount = 0f }
        }
        clearFallback()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if (::surface.isInitialized) { updateRootState(root); applyFrame() }
    }
    override fun onStart() {
        super.onStart()
        started = true
    }
    override fun onDestroy() {
        cancelPendingEffects()
        cancelPendingOpening()
        stopFrames()
        clearFallback()
        windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        if (!isChangingConfigurations) AppVideoCardWindow.release(token)
        super.onDestroy()
    }

    private fun clearFallback() {
        if (lastFallbackRadius != 0) session?.sourceContent?.get()?.setRenderEffect(null)
        lastFallbackRadius = 0
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (closing) { stopFrames(); finishWindow(); return }
        // 初次窗口配置和旋转只更新几何，保留当前开场进度与速度。
        updateRootState(root)
        if (::surface.isInitialized) {
            surface.requestLayout()
            surface.doOnLayout { applyFrame() }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        cancelPendingOpening()
        openingFramePresented = true
        openingCompleted = true
        stopFrames()
        depth = 1f
        frame = VideoCardMorphFrame.at(1f)
        physics.sample(1f, null, 1f, 0, false, System.nanoTime())
        updateRootState(root)
        applyFrame()
    }

    private fun cancelPendingOpening() {
        waitingForOpeningFrame = false
        if (::surface.isInitialized) surface.removeCallbacks(startOpening)
    }

    private fun cancelPendingEffects() {
        effectsGeneration++
        scheduledEffects = null
    }
}

private data class AppVideoCardWindowEffects(val blurRadius: Int, val fallbackRadius: Int, val dimAmount: Float)

/** 来源和详情从第一段几何位移开始交接，不再先放大整卡、后集中换页。 */
internal fun resolveAppVideoCardOpeningSeedAlpha(progress: Float): Float =
    1f - progress.coerceIn(0f, 1f)

/** 完整展开后才切换到返回时间轴；中途关闭或反向拖动仍延续当前开场像素。 */
internal fun resolveAppVideoCardSeedAlpha(progress: Float, openingCompleted: Boolean): Float =
    if (openingCompleted) resolveVideoCardWholeCardReturnHandoffAlpha(progress)
    else resolveAppVideoCardOpeningSeedAlpha(progress)

/** 开场、返回及取消恢复共用系统时间尺度，不额外加速透明窗口。 */
internal fun resolveAppVideoCardElapsedNanos(
    startNanos: Long, frameNanos: Long, systemDurationScale: Float,
): Long = ((frameNanos - startNanos).coerceAtLeast(0L).toDouble() /
    systemDurationScale).toLong()

internal data class AppVideoCardWindowSize(val width: Float, val height: Float)

/** 返回本地裁剪半径；屏幕上显示的圆角不受内容缩放影响。 */
internal fun resolveAppVideoCardWindowRadius(
    sourceRadius: Float, windowRadius: Float, progress: Float, scale: Float,
    visibleWidth: Float, visibleHeight: Float,
): Float = resolveVideoCardMorphCornerPx(
    sourceRadius, windowRadius, progress, visibleWidth, visibleHeight,
) / scale

/** 宽高包络与长宽比分别插值，避免把扁卡片在展开中途拉成长条。 */
internal fun resolveAppVideoCardWindowSize(
    sourceWidth: Float, sourceHeight: Float,
    targetWidth: Float, targetHeight: Float,
    progress: Float,
): AppVideoCardWindowSize {
    val scale = resolveVideoCardMorphScale(sourceWidth / targetWidth, sourceHeight / targetHeight, progress)
    return AppVideoCardWindowSize(targetWidth * scale.x, targetHeight * scale.y)
}
