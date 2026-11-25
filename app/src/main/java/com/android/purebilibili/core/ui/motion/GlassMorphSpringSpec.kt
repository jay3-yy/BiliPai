package com.android.purebilibili.core.ui.motion

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI
import kotlin.math.roundToLong

/** 参数转换与阶段采样使用同一组官方弹簧配置，切换阶段时传递当前位置与速度。 */
internal data class GlassMorphSpringSpec(val dampingRatio: Float, val response: Float) {
    val stiffness: Float get() = (2.0 * PI / response).let { (it * it).toFloat() }

    private val sampler = FloatSpringSpec(dampingRatio = dampingRatio, stiffness = stiffness)

    fun <T> asComposeSpring(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

    data class Sample(val value: Float, val velocity: Float)

    fun sample(time: Float, from: Float, to: Float, velocity: Float = 0f): Sample {
        if (time <= 0f) return Sample(from, velocity)
        // FloatSpringSpec samples at millisecond precision. Round the seconds timeline
        // before conversion so boundaries such as 0.21s do not fall one millisecond behind.
        val nanos = (time * 1_000f).roundToLong() * 1_000_000L
        return Sample(
            sampler.getValueFromNanos(nanos, from, to, velocity),
            sampler.getVelocityFromNanos(nanos, from, to, velocity),
        )
    }
}

/** 液态玻璃全局统一形变弹簧；展开柔和舒展、回收利落收敛，与实测 iOS 液态玻璃手感一致。 */
internal object GlassMorphSprings {
    val Expand = GlassMorphSpringSpec(0.76f, 0.38f)
    val Return = GlassMorphSpringSpec(0.76f, 0.36f)

    /** 液态玻璃底栏分轴形变参数（收起先缩窄再落槽；展开垂直先抬升，水平克制收敛避免右侧单边拉扯）。 */
    val DockCollapseX = GlassMorphSpringSpec(0.74f, 0.36f)
    val DockCollapseY = GlassMorphSpringSpec(0.76f, 0.43f)
    val DockExpandX = GlassMorphSpringSpec(0.86f, 0.34f)
    val DockExpandY = GlassMorphSpringSpec(0.78f, 0.30f)
}
