package com.android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.feature.video.ui.overlay.TopControlBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class TopControlBarInsetsUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun chromeAvoidsUnconsumedStatusBarsAndReservesSideInsetsOnlyInFullscreen() {
        val fullscreen = mutableStateOf(true)
        val visible = mutableStateOf(false)
        val cutoutTop = mutableStateOf(0)
        val statusTop = mutableStateOf(0)
        val cutoutLeft = mutableStateOf(0)
        val cutoutRight = mutableStateOf(0)
        val gestureLeft = mutableStateOf(0)
        val gestureRight = mutableStateOf(0)
        val consumedTop = mutableStateOf(0)
        val portrait = mutableStateOf(true)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        var pixelsPerDp = 1f
        rule.setContent {
            pixelsPerDp = LocalDensity.current.density
            CompositionLocalProvider(LocalAppUiStyle provides style.value) {
                MaterialTheme { MiuixTheme {
                    Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets(top = consumedTop.value.dp))) {
                        TopControlBar(
                            title = "Cutout regression", isFullscreen = fullscreen.value,
                            statusBarVisible = visible.value, isPortrait = portrait.value,
                            likeStatusKnown = true, onBack = {}, showCurrentTime = false,
                            showInteractiveActions = false,
                            displayCutoutInsets = WindowInsets(
                                left = cutoutLeft.value.dp, right = cutoutRight.value.dp, top = cutoutTop.value.dp,
                            ),
                            statusBarInsets = WindowInsets(top = statusTop.value.dp),
                            systemGestureInsets = WindowInsets(left = gestureLeft.value.dp, right = gestureRight.value.dp),
                            onMoreClick = {},
                        )
                    }
                } }
            }
        }
        fun backBounds() = rule.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        for (theme in AppUiStyle.entries) {
            rule.runOnIdle {
                cutoutRight.value = 0; gestureLeft.value = 0; gestureRight.value = 0
            }
            rule.runOnIdle {
                style.value = theme
                fullscreen.value = true; visible.value = false; portrait.value = true
                cutoutTop.value = 0; statusTop.value = 0; cutoutLeft.value = 0; consumedTop.value = 0
            }
            val baseline = backBounds()
            val baselineRight = rule.onNodeWithContentDescription("更多设置").fetchSemanticsNode().boundsInRoot.right
            fun assertOffsets(topDp: Int, leftDp: Int = 0) {
                val bounds = backBounds()
                assertEquals("$theme top", baseline.top + topDp * pixelsPerDp, bounds.top, 1f)
                assertEquals("$theme left", baseline.left + leftDp * pixelsPerDp, bounds.left, 1f)
            }
            // Hidden bars still leave a real hole in the display. Stale status inset is ignored.
            rule.runOnIdle { cutoutTop.value = 48; statusTop.value = 64 }
            assertOffsets(48)
            rule.runOnIdle { visible.value = true; statusTop.value = 24 }
            assertOffsets(48)
            rule.runOnIdle { statusTop.value = 64 }
            assertOffsets(64) // max(48, 64), not 48 + 64
            rule.runOnIdle { consumedTop.value = 24 }
            assertOffsets(40) // Respect a host that already consumed part of the inset.
            rule.runOnIdle { fullscreen.value = false; consumedTop.value = 0 }
            assertOffsets(64) // Media-only padding does not move sibling controls below the status bar.
            rule.runOnIdle { consumedTop.value = 64 }
            assertOffsets(0) // A safe host already consumed the entire top inset.
            rule.runOnIdle { consumedTop.value = 0 }
            rule.runOnIdle { visible.value = false }
            assertOffsets(0)
            rule.runOnIdle {
                fullscreen.value = true; portrait.value = false
                cutoutTop.value = 0; cutoutLeft.value = 36
            }
            assertOffsets(0, 36) // Landscape must retain horizontal cutout protection.
            fun assertRightOffset(dp: Int) {
                val right = rule.onNodeWithContentDescription("更多设置").fetchSemanticsNode().boundsInRoot.right
                assertEquals("$theme right compensation", baselineRight - dp * pixelsPerDp, right, 1f)
            }
            assertRightOffset(36)
            // Rotating the camera to the right preserves exactly the same centered layout.
            rule.runOnIdle { cutoutLeft.value = 0; cutoutRight.value = 36 }
            assertOffsets(0, 36)
            assertRightOffset(36)
            // A larger back-gesture region wins on both sides, not just on the seek bar.
            rule.runOnIdle { gestureLeft.value = 48; gestureRight.value = 24 }
            assertOffsets(0, 48)
            assertRightOffset(48)
            rule.runOnIdle { fullscreen.value = false }
            assertOffsets(0, 0)
            assertRightOffset(0) // Inline has no side safe area, even with a camera and wide gestures.
            rule.runOnIdle { cutoutLeft.value = 36; cutoutRight.value = 0; visible.value = true; statusTop.value = 24 }
            assertOffsets(24, 0)
            assertRightOffset(0)
            rule.runOnIdle { consumedTop.value = 24 }
            assertOffsets(0, 0)
        }
    }
}
