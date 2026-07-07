package com.android.purebilibili.navigation3

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BiliPaiNavContentTransformPolicyStructureTest {

    @Test
    fun disabledVideoDirectionalReturnKeepsTargetPageImmediatelyVisible() {
        val source = contentTransformPolicySource()
        val returnFunctionStart = source.indexOf("private fun disabledVideoDirectionReturnTransform")
        val returnFunctionEnd = source.length
        val returnFunction = source.substring(returnFunctionStart, returnFunctionEnd)

        assertTrue(returnFunction.contains("EnterTransition.None togetherWith"))
        assertTrue(returnFunction.contains("ExitTransition.None").not())
    }

    @Test
    fun disabledVideoDirectionalReturnMovesOnlyOutgoingPageHorizontally() {
        val source = contentTransformPolicySource()
        val returnFunctionStart = source.indexOf("private fun disabledVideoDirectionReturnTransform")
        val returnFunctionEnd = source.length
        val returnFunction = source.substring(returnFunctionStart, returnFunctionEnd)

        assertTrue(returnFunction.contains("slideOutHorizontally("))
        assertTrue(returnFunction.contains("slideInHorizontally(").not())
    }

    @Test
    fun disabledVideoDirectionalReturnSlidesFullyOffscreen() {
        val source = contentTransformPolicySource()
        val returnFunctionStart = source.indexOf("private fun disabledVideoDirectionReturnTransform")
        val returnFunctionEnd = source.length
        val returnFunction = source.substring(returnFunctionStart, returnFunctionEnd)

        assertTrue(returnFunction.contains("targetOffsetX = { width -> directionSign * width }"))
    }

    @Test
    fun disabledVideoDirectionalReturnUsesResponsiveMotionWindow() {
        val source = contentTransformPolicySource()

        assertTrue(source.contains("NAV3_DISABLED_VIDEO_RETURN_MILLIS = 220"))
    }

    @Test
    fun disabledVideoDirectionalReturnFadesAlongsideSlide() {
        val source = contentTransformPolicySource()
        val returnFunctionStart = source.indexOf("private fun disabledVideoDirectionReturnTransform")
        val returnFunctionEnd = source.length
        val returnFunction = source.substring(returnFunctionStart, returnFunctionEnd)

        assertTrue(returnFunction.contains("EnterTransition.None togetherWith"))
        assertTrue(returnFunction.contains("ExitTransition.None").not())
        // 与前向方向（slideIn + fadeIn togetherWith fadeOut）对称：
        // 返回方向也应 fadeOut 退出页面，使入口/出口视觉效果一致。
        assertTrue(returnFunction.contains("fadeOut("))
    }

    @Test
    fun spaceForwardUsesLightSlideAndFade() {
        val source = contentTransformPolicySource()

        assertTrue(source.contains("BiliPaiNavRouteTransition.SPACE_FORWARD"))
        assertTrue(source.contains("private fun spaceForwardTransform()"))
        assertTrue(source.contains("initialOffsetX = { width -> width / 8 }"))
        assertTrue(source.contains("fadeIn(animationSpec = tween(NAV3_SPACE_FORWARD_MILLIS))"))
    }

    @Test
    fun lightSiblingForwardUsesSmallSlideAndFade() {
        val source = contentTransformPolicySource()
        val functionStart = source.indexOf("private fun lightSiblingForwardTransform")
        val functionEnd = source.indexOf("private fun lightSiblingPopTransform")
        val function = source.substring(functionStart, functionEnd)

        assertTrue(source.contains("BiliPaiNavRouteTransition.LIGHT_SIBLING_FORWARD"))
        assertTrue(function.contains("slideInHorizontally("))
        assertTrue(function.contains("initialOffsetX = { width -> width / 8 }"))
        assertTrue(function.contains("fadeIn(animationSpec = tween(NAV3_LIGHT_SIBLING_MILLIS"))
        assertTrue(function.contains("fadeOut(animationSpec = tween(NAV3_FALLBACK_FADE_MILLIS))"))
    }

    @Test
    fun lightSiblingPopMovesOnlyOutgoingPageSlightly() {
        val source = contentTransformPolicySource()
        val functionStart = source.indexOf("private fun lightSiblingPopTransform")
        val functionEnd = source.indexOf("private fun disabledVideoDirectionReturnTransform")
        val function = source.substring(functionStart, functionEnd)

        assertTrue(source.contains("BiliPaiNavRouteTransition.LIGHT_SIBLING_POP"))
        assertTrue(function.contains("EnterTransition.None togetherWith"))
        assertTrue(function.contains("slideOutHorizontally("))
        assertTrue(function.contains("targetOffsetX = { width -> width / 8 }"))
        assertTrue(function.contains("fadeOut(animationSpec = tween(NAV3_LIGHT_SIBLING_MILLIS"))
    }

    @Test
    fun settingsIosPushForwardUsesParallaxSlide() {
        val source = contentTransformPolicySource()

        assertTrue(source.contains("BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_FORWARD"))
        assertTrue(source.contains("private fun settingsIosPushForwardTransform()"))
        assertTrue(source.contains("resolveSettingsIosPushForwardContentTransform("))
    }

    @Test
    fun settingsIosPushPopUsesParallaxSlide() {
        val source = contentTransformPolicySource()

        assertTrue(source.contains("BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP"))
        assertTrue(source.contains("private fun settingsIosPushPopTransform()"))
        assertTrue(source.contains("resolveSettingsIosPushPopContentTransform("))
    }

    @Test
    fun bottomBarSiblingForwardUsesFullWidthHorizontalSlide() {
        val source = contentTransformPolicySource()

        assertTrue(source.contains("BiliPaiNavRouteTransition.BOTTOM_BAR_SIBLING_FORWARD"))
        assertTrue(source.contains("private fun bottomBarSiblingForwardTransform()"))
        assertTrue(source.contains("resolveBottomBarLikeHorizontalContentTransform("))
    }

    @Test
    fun bottomBarSiblingPopUsesFullWidthHorizontalSlide() {
        val source = contentTransformPolicySource()

        assertTrue(source.contains("BiliPaiNavRouteTransition.BOTTOM_BAR_SIBLING_POP"))
        assertTrue(source.contains("private fun bottomBarSiblingPopTransform()"))
    }

    private fun contentTransformPolicySource(): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/navigation3/BiliPaiNavContentTransformPolicy.kt"),
            File("src/main/java/com/android/purebilibili/navigation3/BiliPaiNavContentTransformPolicy.kt")
        ).first { it.exists() }.readText()
    }
}
