package com.android.purebilibili.core.ui.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** 有限响应的解析弹簧，阶段切换时传递当前位置与速度，不依赖积分步长。 */
internal data class GlassMorphSpringSpec(val dampingRatio: Float, val response: Float) {
    val stiffness: Float get() = (2.0 * PI / response).let { (it * it).toFloat() }

    fun <T> asComposeSpring(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

    data class Sample(val value: Float, val velocity: Float)

    fun sample(time: Float, from: Float, to: Float, velocity: Float = 0f): Sample {
        if (time <= 0f) return Sample(from, velocity)
        val omega = (2.0 * PI / response).toFloat()
        val displacement = from - to
        val decay = exp(-dampingRatio * omega * time)
        if (dampingRatio >= 0.9999f) {
            val b = velocity + omega * displacement
            return Sample(to + decay * (displacement + b * time), decay * (velocity - omega * b * time))
        }
        val alpha = dampingRatio * omega
        val frequency = omega * sqrt(1f - dampingRatio * dampingRatio)
        val b = (velocity + alpha * displacement) / frequency
        val c = cos(frequency * time)
        val s = sin(frequency * time)
        val position = displacement * c + b * s
        return Sample(to + decay * position, decay * (-alpha * position + frequency * (b * c - displacement * s)))
    }
}

/** 玻璃外壳共用响应；菜单独立控制通道，底栏由 Compose 统一驱动几何。 */
internal object GlassMorphSprings {
    val Expand = GlassMorphSpringSpec(0.75f, 0.35f)
    val Return = GlassMorphSpringSpec(0.8f, 0.49f)
}
