package com.android.purebilibili.feature.home.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.GlassMorphSprings
import com.android.purebilibili.core.ui.motion.iosMorphTween

internal fun <T> iosTopTabCapsuleMotionSpec(): TweenSpec<T> = iosMorphTween(260)

/** 液态玻璃顶部分类胶囊位移弹簧，与底栏及全局液态玻璃形变共用同一套阻尼手感。 */
internal fun <T> glassTopTabCapsuleSpringSpec(): SpringSpec<T> =
    GlassMorphSprings.Expand.asComposeSpring()

/** 原生 MD3 顶部分类翻页节奏；玻璃与 Miuix 路径保持各自默认，不共用本档。 */
internal fun <T> nativeMd3TopTabSelectionSpec(): TweenSpec<T> = tween(
    durationMillis = 240,
    easing = AppMotionEasing.M1Standard,
)
