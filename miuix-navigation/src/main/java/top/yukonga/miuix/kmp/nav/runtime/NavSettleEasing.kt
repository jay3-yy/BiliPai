// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.runtime

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.roundToLong

/**
 * Samples the official spring over the programmatic tween's normalized one-second window.
 * The tween keeps its configured lifetime; gesture handoffs keep their live release velocity.
 * Response is the spring period measured in units of that normalized window.
 */
@Immutable
internal class NavSettleEasing(
    response: Float,
    damping: Float,
) : Easing {
    private val spring = FloatSpringSpec(
        dampingRatio = damping,
        stiffness = (2.0 * PI / response).let { (it * it).toFloat() },
    )

    override fun transform(fraction: Float): Float = spring.getValueFromNanos(
        playTimeNanos = (fraction * 1_000f).roundToLong() * 1_000_000L,
        initialValue = 0f,
        targetValue = 1f,
        initialVelocity = 0f,
    )
}

/** Default programmatic settle curve; transition presets can share the same fixed pacing. */
val NavProgrammaticEasing: Easing = NavSettleEasing(response = 0.8f, damping = 0.95f)
