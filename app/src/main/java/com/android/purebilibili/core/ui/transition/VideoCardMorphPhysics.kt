package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.android.purebilibili.core.ui.motion.AppMotionTokens

/** 参数集中在视频开合范围内，不改变其他页面及原生控件的运动。 */
internal object VideoCardMorphSprings {
    val openPosition = spec(1.1f, .229f)
    val openSize = spec(1.1f, .268f)
    val openCorner = spec(1f, .18f)
    val openEffects = spec(1f, .25f)
    // 引擎把背景遮罩与模糊分通道：遮罩更欠阻尼、更快到位（压暗先于模糊）。
    val openMask = spec(.95f, .12f)
    val closePosition = spec(.95f, .29f)
    val closeSize = spec(.95f, .29f)
    val closeCorner = spec(1f, .2f)
    val closeEffects = spec(1f, .15f)
    val closeMask = spec(.95f, .18f)

    // 导航 entry 的生命周期使用单调且略慢的弹簧，不能在属性弹簧过冲时提前卸载页面。
    val enterLifetime = openSize
    val returnLifetime = spec(1f, .29f)

    private fun spec(damping: Float, response: Float): SpringSpec<Float> =
        AppMotionTokens.folmeSpringSpec<Float>(damping, response).let {
            SpringSpec(it.dampingRatio, it.stiffness, visibilityThreshold = .001f)
        }
}

internal data class VideoCardMorphFrame(
    val position: Float,
    val size: Float,
    val corner: Float,
    val effects: Float,
    val mask: Float,
) {
    companion object {
        fun at(depth: Float) = VideoCardMorphFrame(depth, depth, depth, depth, depth)
    }
}

/**
 * 只在导航已有的帧中采样，不创建协程或帧循环。新目标继承各属性的当前位置及速度；
 * 手势直接移动当前属性，取消后再从手指释放位置继续弹簧恢复。
 */
internal class VideoCardMorphPhysics {
    private class Channel {
        var value = 0f
        var velocity = 0f
        private var animation: TargetBasedAnimation<Float, AnimationVector1D>? = null

        fun retarget(target: Float, spec: SpringSpec<Float>) {
            animation = TargetBasedAnimation(spec, Float.VectorConverter, value, target, AnimationVector1D(velocity))
        }
        fun sample(nanos: Long) {
            val active = animation ?: return
            value = active.getValueFromNanos(nanos).coerceIn(0f, 1f)
            velocity = active.getVelocityVectorFromNanos(nanos).value
        }
        fun seek(delta: Float, seconds: Float) {
            value = (value + delta).coerceIn(0f, 1f)
            velocity = if (seconds > 0f) delta / seconds else 0f
            animation = null
        }
        fun snap(depth: Float) {
            value = depth
            velocity = 0f
            animation = null
        }
    }

    private val position = Channel()
    private val size = Channel()
    private val corner = Channel()
    private val effects = Channel()
    private val mask = Channel()
    private var activeSettlement: Any? = null
    private var lastDriver = 0f
    private var lastFrameNanos = 0L

    fun sample(
        driverDepth: Float,
        settlement: Any?,
        targetDepth: Float,
        elapsedNanos: Long,
        gestureActive: Boolean,
        frameNanos: Long,
        durationScale: Float = 1f,
    ): VideoCardMorphFrame {
        if (gestureActive) {
            // 先以 Long 求帧间隔，再换算逻辑秒数；不能把绝对纳秒时间转成 Float。
            val seconds = if (lastFrameNanos == 0L) 0f else
                ((frameNanos - lastFrameNanos).toDouble() / 1_000_000_000.0 / durationScale).toFloat()
            val delta = driverDepth - lastDriver
            position.seek(delta, seconds)
            size.seek(delta, seconds)
            corner.seek(delta, seconds)
            effects.seek(delta, seconds)
            mask.seek(delta, seconds)
            activeSettlement = null
        } else if (settlement != null) {
            if (activeSettlement !== settlement) {
                activeSettlement = settlement
                val opening = targetDepth > 0f
                position.retarget(targetDepth, if (opening) VideoCardMorphSprings.openPosition else VideoCardMorphSprings.closePosition)
                size.retarget(targetDepth, if (opening) VideoCardMorphSprings.openSize else VideoCardMorphSprings.closeSize)
                corner.retarget(targetDepth, if (opening) VideoCardMorphSprings.openCorner else VideoCardMorphSprings.closeCorner)
                effects.retarget(targetDepth, if (opening) VideoCardMorphSprings.openEffects else VideoCardMorphSprings.closeEffects)
                mask.retarget(targetDepth, if (opening) VideoCardMorphSprings.openMask else VideoCardMorphSprings.closeMask)
            }
            position.sample(elapsedNanos)
            size.sample(elapsedNanos)
            corner.sample(elapsedNanos)
            effects.sample(elapsedNanos)
            mask.sample(elapsedNanos)
        } else {
            // 空闲端点也负责清除上次手势速度，避免下一次点击继承旧会话。
            position.snap(driverDepth)
            size.snap(driverDepth)
            corner.snap(driverDepth)
            effects.snap(driverDepth)
            mask.snap(driverDepth)
            activeSettlement = null
        }
        lastDriver = driverDepth
        lastFrameNanos = frameNanos
        return VideoCardMorphFrame(position.value, size.value, corner.value, effects.value, mask.value)
    }
}
