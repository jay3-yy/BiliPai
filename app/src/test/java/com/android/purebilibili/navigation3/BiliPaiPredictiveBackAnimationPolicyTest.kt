package com.android.purebilibili.navigation3

import com.android.purebilibili.navigation3.predictiveback.BiliPaiAospCrossActivityPredictiveBackAnimation
import com.android.purebilibili.navigation3.predictiveback.BiliPaiClassicPredictiveBackAnimation
import com.android.purebilibili.navigation3.predictiveback.BiliPaiDefaultPredictiveBackAnimation
import com.android.purebilibili.navigation3.predictiveback.BiliPaiDisabledPredictiveBackAnimation
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.predictiveback.BiliPaiScalePredictiveBackAnimation
import com.android.purebilibili.navigation3.predictiveback.BiliPaiSharedElementPredictiveBackAnimation
import com.android.purebilibili.navigation3.predictiveback.resolveBiliPaiPredictiveBackAnimationHandler
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiliPaiPredictiveBackAnimationPolicyTest {

    @Test
    fun sharedElementRoute_usesSharedElementHandler() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT,
            style = BiliPaiPredictiveBackAnimationStyle.AOSP,
        )
        assertTrue(handler is BiliPaiSharedElementPredictiveBackAnimation)
    }

    @Test
    fun sharedElementPredictivePop_keepsRouteLayerStillForCardReturn() {
        val function = sharedElementPredictivePopFunction()

        assertFalse(function.contains("slideOutHorizontally"))
        assertFalse(function.contains("slideInHorizontally"))
        assertTrue(function.contains("noOpSharedElementContentTransform()"))
        assertFalse(function.contains("initialContentExit = fadeOut("))
    }

    @Test
    fun classicCardRoute_usesScaleHandlerByDefault() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
        )
        assertTrue(handler is BiliPaiScalePredictiveBackAnimation)
    }

    @Test
    fun classicCardRoute_defaultStyle_delegatesToNavEngineDefaults() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
            style = BiliPaiPredictiveBackAnimationStyle.DEFAULT,
        )
        assertTrue(handler is BiliPaiDefaultPredictiveBackAnimation)
    }

    @Test
    fun classicCardRoute_scaleStyle_usesScaleHandler() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
            style = BiliPaiPredictiveBackAnimationStyle.SCALE,
        )
        assertTrue(handler is BiliPaiScalePredictiveBackAnimation)
    }

    @Test
    fun scalePredictiveBack_keepsBackgroundSceneAtFullScale() {
        val source = scalePredictiveBackSource()

        assertFalse(source.contains("EnterExitState.PostExit -> 0.85f"))
        assertFalse(source.contains("scaleX = animatedScale"))
        assertFalse(source.contains("scaleY = animatedScale"))
    }

    @Test
    fun scalePredictiveBack_doesNotDrawTargetPageScrim() {
        val source = scalePredictiveBackSource()

        assertFalse(source.contains("drawRect(color = Color.Black"))
        assertFalse(source.contains("dynamicAlpha"))
    }

    @Test
    fun classicCardRoute_aospStyle_usesAospHandler() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
            style = BiliPaiPredictiveBackAnimationStyle.AOSP,
        )
        assertTrue(handler is BiliPaiAospCrossActivityPredictiveBackAnimation)
    }

    @Test
    fun classicCardRoute_classicStyle_usesClassicHandler() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
            style = BiliPaiPredictiveBackAnimationStyle.CLASSIC,
        )
        assertTrue(handler is BiliPaiClassicPredictiveBackAnimation)
    }

    @Test
    fun disabledClassicCard_usesDisabledHandler() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
            predictiveBackEnabled = false,
        )
        assertTrue(handler is BiliPaiDisabledPredictiveBackAnimation)
    }

    @Test
    fun disabledSharedElementRoute_keepsSharedElementHandler() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT,
            predictiveBackEnabled = false,
        )
        assertTrue(handler is BiliPaiSharedElementPredictiveBackAnimation)
    }

    @Test
    fun fallbackRoute_usesDefaultHandler() {
        val handler = resolveBiliPaiPredictiveBackAnimationHandler(
            routeTransition = BiliPaiNavRouteTransition.FALLBACK,
        )
        assertTrue(handler is BiliPaiDefaultPredictiveBackAnimation)
    }

    private fun sharedElementPredictivePopFunction(): String {
        val source = sharedElementPredictiveBackSource()
        val functionStart = source.indexOf("override fun AnimatedContentTransitionScope<Scene<BiliPaiNavKey>>.onPredictivePopTransitionSpec")
        val functionEnd = source.indexOf("override fun AnimatedContentTransitionScope<Scene<BiliPaiNavKey>>.onPopTransitionSpec")
        return source.substring(functionStart, functionEnd)
    }

    private fun sharedElementPredictiveBackSource(): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/navigation3/predictiveback/BiliPaiSharedElementPredictiveBackAnimation.kt"),
            File("src/main/java/com/android/purebilibili/navigation3/predictiveback/BiliPaiSharedElementPredictiveBackAnimation.kt")
        ).first { it.exists() }.readText()
    }

    private fun scalePredictiveBackSource(): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/navigation3/predictiveback/BiliPaiScalePredictiveBackAnimation.kt"),
            File("src/main/java/com/android/purebilibili/navigation3/predictiveback/BiliPaiScalePredictiveBackAnimation.kt")
        ).first { it.exists() }.readText()
    }
}
