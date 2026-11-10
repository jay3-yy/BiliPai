package com.android.purebilibili.navigation3.predictiveback

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

class BiliPaiMiuixNavTransitionTest {

    @Test
    fun coveredPageBlurStartsStrongAndClearsWithNavigationDepth() {
        assertEquals(0f, resolveMiuixNavCoveredBlurProgress(-1f))
        assertEquals(0f, resolveMiuixNavCoveredBlurProgress(0f))
        assertEquals(0.0625f, resolveMiuixNavCoveredBlurProgress(0.25f))
        assertEquals(0.25f, resolveMiuixNavCoveredBlurProgress(0.5f))
        assertEquals(1f, resolveMiuixNavCoveredBlurProgress(1f))
        assertEquals(1f, resolveMiuixNavCoveredBlurProgress(2f))
    }

    @Test
    fun blurWrapperPreservesEachStylesSettleTiming() {
        val styles = listOf(
            BiliPaiPredictiveBackAnimationStyle.AOSP to AospNavTransition,
            BiliPaiPredictiveBackAnimationStyle.SCALE to
                scaleNavTransition(BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT),
            BiliPaiPredictiveBackAnimationStyle.CLASSIC to ClassicNavTransition,
            BiliPaiPredictiveBackAnimationStyle.MIUIX to NavTransitions.MiuixDefault,
        )
        for ((style, base) in styles) {
            for (blurEnabled in listOf(false, true)) {
                val wrapped = biliPaiMiuixNavTransition(
                    animation = style,
                    exitDirection = BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT,
                    isLightBackground = false,
                    miuixTransitionBlurEnabled = blurEnabled,
                    miuixPredictiveBackProgressEnabled = false,
                )
                assertEquals(base.motion.programmatic, wrapped.motion.programmatic, "$style programmatic")
                assertEquals(base.motion.commit, wrapped.motion.commit, "$style commit")
                assertEquals(base.motion.cancel, wrapped.motion.cancel, "$style cancel")
            }
        }
    }

    @Test
    fun aospCommitScrimUsesItsReleaseClockRatherThanPageDepth() {
        val wrapped = biliPaiMiuixNavTransition(
            animation = BiliPaiPredictiveBackAnimationStyle.AOSP,
            exitDirection = BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT,
            isLightBackground = false,
        )
        val scope = object : NavTransitionScope {
            override val relativeDepth = 0.6f
            override val role = NavRole.Covered
            override val change = NavChange.Pop
            override val gesture = NavGesture(0.4f, NavSwipeEdge.Left, 500f)
            override val settle = object : NavSettle {
                override val phase = NavSettlePhase.Commit
                override val releaseVelocity = 0f
                override val elapsedMillis = 110f
            }
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
        }
        assertEquals(0.5f, wrapped.scrimFraction(scope), 0.0001f)
    }
}
