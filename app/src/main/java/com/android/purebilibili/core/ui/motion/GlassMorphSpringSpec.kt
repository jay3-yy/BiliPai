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

/** 玻璃外壳共用响应；菜单独立控制通道，底栏由 Compose 统一驱动几何。 */
internal object GlassMorphSprings {
    val Expand = GlassMorphSpringSpec(0.75f, 0.35f)
    val Return = GlassMorphSpringSpec(0.8f, 0.49f)
}
