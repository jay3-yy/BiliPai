package com.android.purebilibili.feature.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.drawBackdrop

class ChromeBackdropSourceUiRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visibleSourceUpdatesWithoutDrawingChildrenTwice() = verifySource()

    @Test
    fun hiddenDockCaptureStillUpdatesItsConsumer() = verifySource(hidden = true)

    @Test
    fun nestedNavigationAndHomeSourcesDoNotMultiplyChildDrawing() =
        verifySource(nested = true)

    @Test
    fun themeBackgroundUpdatesKeepVisibleSourceAndModifierStable() =
        verifyRecordingBackground(drawContentToScreen = true)

    @Test
    fun themeBackgroundUpdatesKeepHiddenCaptureAndModifierStable() =
        verifyRecordingBackground(drawContentToScreen = false)

    private fun verifyRecordingBackground(drawContentToScreen: Boolean) {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val background = mutableStateOf(Color.Red)
        val drawCalls = AtomicInteger()
        lateinit var currentSource: ChromeBackdropSource
        composeRule.setContent {
            val source = rememberChromeBackdropSource(
                drawContentToScreen = drawContentToScreen,
                recordingBackground = background.value,
            )
            SideEffect { currentSource = source }
            Box(Modifier.size(80.dp).testTag("backdrop-result").background(Color.Blue)) {
                Box(Modifier.matchParentSize().then(source.modifier).drawWithContent {
                    drawCalls.incrementAndGet()
                    drawContent()
                })
                Box(Modifier.matchParentSize().drawBackdrop(
                    backdrop = source.backdrop,
                    shape = { RectangleShape },
                    effects = {},
                ))
            }
        }
        composeRule.waitForIdle()
        val originalSource = currentSource
        val originalModifier = originalSource.modifier
        assertCenterColor(Color.Red)
        // Include removing the explicit background: the previous frame must not linger.
        for (color in listOf(Color.Green, Color.Unspecified, Color.Red)) {
            composeRule.runOnIdle {
                drawCalls.set(0)
                background.value = color
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertSame(originalSource, currentSource)
                assertSame(originalModifier, currentSource.modifier)
                assertEquals("Each theme frame records children once", 1, drawCalls.get())
            }
            assertCenterColor(if (color == Color.Unspecified) Color.Blue else color)
        }
    }

    @Test
    fun wallpaperStaysAlignedWhenRetainedPageMovesAndReturns() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val pageOffsetPx = mutableStateOf(0)
        composeRule.setContent {
            val source = rememberChromeBackdropSource(drawContentToScreen = false)
            Box(Modifier.size(100.dp).clipToBounds().testTag("wallpaper-result")) {
                Box(Modifier.matchParentSize().then(source.modifier).drawBehind {
                    drawRect(Color.Red, size = Size(size.width / 2f, size.height))
                    drawRect(
                        Color.Green,
                        topLeft = Offset(size.width / 2f, 0f),
                        size = Size(size.width / 2f, size.height),
                    )
                })
                CompositionLocalProvider(
                    LocalGlobalWallpaperBackdropVisible provides true,
                    LocalGlobalWallpaperBackdropSource provides source,
                ) {
                    Box(
                        Modifier.matchParentSize()
                            .offset { IntOffset(pageOffsetPx.value, 0) }
                            .sharedGlobalWallpaperBackground(Color.Black),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val width = composeRule.onNodeWithTag("wallpaper-result").captureToImage().width
        // Move through layout only, retaining the same composition and coordinate object.
        for (offset in listOf(0, width / 4, 0, -width / 4, 0)) {
            composeRule.runOnIdle { pageOffsetPx.value = offset }
            composeRule.waitForIdle()
            val pixels = composeRule.onNodeWithTag("wallpaper-result")
                .captureToImage().toPixelMap()
            assertPixelColor(Color.Red, pixels[width * 2 / 5, pixels.height / 2])
            assertPixelColor(Color.Green, pixels[width * 3 / 5, pixels.height / 2])
            if (offset == 0) {
                assertPixelColor(Color.Red, pixels[width / 10, pixels.height / 2])
            }
        }
    }

    private fun verifySource(hidden: Boolean = false, nested: Boolean = false) {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val tint = mutableStateOf(Color.Red)
        val drawCalls = AtomicInteger()
        composeRule.setContent {
            val source = rememberChromeBackdropSource()
            val innerSource = rememberChromeBackdropSource()
            Box(Modifier.size(80.dp).testTag("backdrop-result").background(Color.Blue)) {
                Box(
                    Modifier.matchParentSize()
                        .alpha(if (hidden) 0f else 1f)
                        .then(source.modifier)
                ) {
                    Box(
                        Modifier.matchParentSize()
                            .then(if (nested) innerSource.modifier else Modifier)
                            .drawBehind {
                                drawCalls.incrementAndGet()
                                drawRect(tint.value)
                            }
                    )
                }
                // No tint/blur: the consumer must reproduce the source, even when it is hidden.
                Box(
                    Modifier.matchParentSize().drawBackdrop(
                        backdrop = source.backdrop,
                        shape = { RectangleShape },
                        effects = {},
                    )
                )
            }
        }
        composeRule.waitForIdle()
        assertCenterColor(Color.Red)

        composeRule.runOnIdle {
            drawCalls.set(0)
            tint.value = Color.Green
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("One child draw after a draw-only invalidation", 1, drawCalls.get())
        }
        assertCenterColor(Color.Green)
    }

    private fun assertCenterColor(expected: Color) {
        val image = composeRule.onNodeWithTag("backdrop-result").captureToImage()
        val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
        assertPixelColor(expected, pixel)
    }

    private fun assertPixelColor(expected: Color, pixel: Color) {
        assertEquals(expected.red, pixel.red, 0.01f)
        assertEquals(expected.green, pixel.green, 0.01f)
        assertEquals(expected.blue, pixel.blue, 0.01f)
        assertEquals(expected.alpha, pixel.alpha, 0.01f)
    }
}
