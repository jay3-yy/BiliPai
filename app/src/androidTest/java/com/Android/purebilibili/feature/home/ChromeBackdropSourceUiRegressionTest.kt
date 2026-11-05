package com.android.purebilibili.feature.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconBackdrop
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed
import com.android.purebilibili.core.ui.blur.rememberPagerChromeBackdropRecordingAllowed
import com.android.purebilibili.feature.home.components.floatingDockCaptureOverflow
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.blur
import com.android.purebilibili.core.ui.effect.lens
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.blur.drawBackdrop
import kotlinx.coroutines.launch

class ChromeBackdropSourceUiRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun isolatedSourceReusesChildDrawingWhileBackdropChangesAndKeepsLiveUpdates() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val background = mutableStateOf(Color.Red)
        val content = mutableStateOf(Color.Green)
        val rasterize = mutableStateOf(false)
        val childDraws = AtomicInteger()
        lateinit var originalSource: ChromeBackdropSource
        lateinit var currentSource: ChromeBackdropSource
        composeRule.setContent {
            val source = rememberChromeBackdropSource(
                recordingBackground = background.value,
                isolateContentDrawing = true,
                rasterizeContent = rasterize.value,
            )
            SideEffect { currentSource = source }
            Box(Modifier.size(80.dp).background(Color.Blue).testTag("isolated-backdrop")) {
                Box(Modifier.matchParentSize().then(source.modifier).drawWithContent {
                    childDraws.incrementAndGet()
                    drawRect(
                        color = content.value,
                        topLeft = Offset(size.width / 2f, 0f),
                        size = Size(size.width / 2f, size.height),
                    )
                })
                Box(Modifier.matchParentSize().drawBackdrop(
                    backdrop = source.backdrop,
                    shape = { RectangleShape },
                    effects = {},
                ))
            }
        }
        fun assertColors(left: Color, right: Color) {
            val pixels = composeRule.onNodeWithTag("isolated-backdrop").captureToImage().toPixelMap()
            for ((fraction, expected) in listOf(0.25f to left, 0.75f to right)) {
                val actual = pixels[(pixels.width * fraction).toInt(), pixels.height / 2]
                assertEquals(expected.red, actual.red, 0.03f)
                assertEquals(expected.green, actual.green, 0.03f)
                assertEquals(expected.blue, actual.blue, 0.03f)
            }
        }
        assertColors(Color.Red, Color.Green)
        composeRule.runOnIdle { originalSource = currentSource; childDraws.set(0) }
        // Enter, settle, then re-enter the rasterized path without replacing the source.
        // This verifies live pixels and identity, not GPU cost or frame timing.
        for (rasterized in listOf(true, false, true)) {
            composeRule.runOnIdle { rasterize.value = rasterized }
            assertColors(Color.Red, Color.Green)
            composeRule.runOnIdle { childDraws.set(0) }
            for (color in listOf(Color.Yellow, Color.Red)) {
                composeRule.runOnIdle { background.value = color }
                assertColors(color, Color.Green)
                composeRule.runOnIdle {
                    assertSame(originalSource, currentSource)
                    assertEquals("Backdrop-only updates must reuse child drawing", 0, childDraws.get())
                }
            }
        }
        composeRule.runOnIdle { content.value = Color.Yellow }
        assertColors(Color.Red, Color.Yellow)
        composeRule.runOnIdle { assertEquals(1, childDraws.get()) }
    }

    @Test
    fun scaffoldReusesPageSourceAndRestoresFallbackAndNativeIconScope() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val glass = mutableStateOf(true)
        val shareSource = mutableStateOf(true)
        lateinit var pageSource: ChromeBackdropSource
        var iconBackdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null
        composeRule.setContent {
            val source = rememberChromeBackdropSource()
            SideEffect { pageSource = source }
            CompositionLocalProvider(
                LocalAppUiStyle provides AppUiStyle.MATERIAL3,
                LocalAppThemeConfig provides AppThemeConfig(
                    headerBlurEnabled = false,
                    liquidGlassEnabled = glass.value,
                ),
                LocalGlobalWallpaperBackdropVisible provides false,
            ) {
                AppScaffold(
                    topBarIconBackdropSource = source.takeIf { shareSource.value },
                    topBar = {
                        val backdrop = LocalAppTopBarIconBackdrop.current
                        SideEffect { iconBackdrop = backdrop }
                        Box(Modifier.size(40.dp))
                    },
                ) { _ ->
                    Box(Modifier.size(80.dp).then(source.modifier).background(Color.Red))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(pageSource.backdrop, iconBackdrop)
            shareSource.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNotNull(iconBackdrop)
            assertNotSame(pageSource.backdrop, iconBackdrop)
            shareSource.value = true
            glass.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNull(iconBackdrop)
            glass.value = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertSame(pageSource.backdrop, iconBackdrop) }
    }

    @Test
    fun visibleSourceUpdatesWithoutDrawingChildrenTwice() = verifySource()

    @Test
    fun hiddenDockCaptureStillUpdatesItsConsumer() = verifySource(hidden = true)

    @Test
    fun pausedRecordingResumesWithCurrentContentAndTheSameSource() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val recording = mutableStateOf(true)
        val tint = mutableStateOf(Color.Red)
        lateinit var currentSource: ChromeBackdropSource
        lateinit var originalSource: ChromeBackdropSource
        composeRule.setContent {
            val source = rememberChromeBackdropSource(
                drawContentToScreen = false,
                isolateContentDrawing = true,
                recordingEnabled = recording.value,
            )
            SideEffect { currentSource = source }
            Box(Modifier.size(80.dp).background(Color.Blue).testTag("backdrop-result")) {
                Box(
                    Modifier.matchParentSize()
                        .then(source.modifier)
                        .drawBehind { drawRect(tint.value) },
                )
                if (recording.value) {
                    Box(Modifier.matchParentSize().drawBackdrop(
                        backdrop = source.backdrop,
                        shape = { RectangleShape },
                        effects = {},
                    ))
                }
            }
        }
        assertCenterColor(Color.Red)
        composeRule.runOnIdle {
            originalSource = currentSource
            recording.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { tint.value = Color.Green }
        assertCenterColor(Color.Blue)
        composeRule.runOnIdle { assertFalse(currentSource.isReady) }

        composeRule.runOnIdle { recording.value = true }
        assertCenterColor(Color.Green)
        composeRule.runOnIdle { assertSame(originalSource, currentSource) }
    }

    @Test
    fun pausedVisibleSourceStillDrawsLiveContentWithoutPublishingARecording() {
        val tint = mutableStateOf(Color.Red)
        lateinit var source: ChromeBackdropSource
        composeRule.setContent {
            val current = rememberChromeBackdropSource(recordingEnabled = false)
            SideEffect { source = current }
            Box(Modifier.size(80.dp).testTag("backdrop-result").then(current.modifier).drawBehind {
                drawRect(tint.value)
            })
        }
        assertCenterColor(Color.Red)
        composeRule.runOnIdle {
            assertFalse(source.isReady)
            tint.value = Color.Green
        }
        assertCenterColor(Color.Green)
        composeRule.runOnIdle { assertFalse(source.isReady) }
    }

    @Test
    fun pagerRetainedSourcesOnlyRecordExposedPagesAndKeepIdentityAcrossReturn() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val parentRecordingAllowed = mutableStateOf(true)
        val sources = mutableMapOf<Int, ChromeBackdropSource>()
        lateinit var pager: androidx.compose.foundation.pager.PagerState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        lateinit var firstSource: ChromeBackdropSource
        composeRule.setContent {
            val state = androidx.compose.foundation.pager.rememberPagerState { 3 }
            val pagerScope = androidx.compose.runtime.rememberCoroutineScope()
            SideEffect { pager = state; scope = pagerScope }
            CompositionLocalProvider(LocalChromeBackdropRecordingAllowed provides parentRecordingAllowed.value) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = state,
                    beyondViewportPageCount = 2,
                    modifier = Modifier.width(100.dp).height(80.dp),
                ) { page ->
                    CompositionLocalProvider(
                        LocalChromeBackdropRecordingAllowed provides
                            rememberPagerChromeBackdropRecordingAllowed(state, page),
                    ) {
                        val source = rememberChromeBackdropSource()
                        SideEffect { sources[page] = source }
                        Box(Modifier.size(100.dp, 80.dp).then(source.modifier).background(Color.Red))
                    }
                }
            }
        }
        composeRule.runOnIdle {
            firstSource = sources.getValue(0)
            assertTrue(firstSource.isReady)
            assertFalse(sources.getValue(1).isReady)
            assertFalse(sources.getValue(2).isReady)
            scope.launch { pager.scrollToPage(0, 0.4f) }
        }
        composeRule.runOnIdle {
            assertTrue(sources.getValue(0).isReady)
            assertTrue(sources.getValue(1).isReady)
            assertFalse(sources.getValue(2).isReady)
            scope.launch { pager.scrollToPage(1) }
        }
        composeRule.runOnIdle {
            assertFalse(sources.getValue(0).isReady)
            assertTrue(sources.getValue(1).isReady)
            scope.launch { pager.scrollToPage(0) }
        }
        composeRule.runOnIdle {
            assertSame(firstSource, sources.getValue(0))
            assertTrue(firstSource.isReady)
            assertFalse(sources.getValue(1).isReady)
            parentRecordingAllowed.value = false
        }
        composeRule.runOnIdle {
            assertFalse(firstSource.isReady)
            assertFalse(sources.getValue(1).isReady)
            parentRecordingAllowed.value = true
        }
        composeRule.runOnIdle {
            assertSame(firstSource, sources.getValue(0))
            assertTrue(firstSource.isReady)
        }
    }

    @Test
    fun hiddenDockCaptureIncludesBadgePixelsAboveItsContentBand() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        composeRule.setContent {
            val source = rememberChromeBackdropSource(drawContentToScreen = false)
            Box(Modifier.size(120.dp).background(Color.Blue).testTag("badge-capture-result")) {
                Box(
                    Modifier.align(Alignment.Center).width(100.dp)
                        .floatingDockCaptureOverflow(56.dp, 16.dp)
                        .then(source.modifier)
                        .padding(vertical = 16.dp)
                        .height(56.dp),
                ) {
                    // 背景与前景分层，徽标上半圆位于原有 56dp 内容带之外。
                    Box(Modifier.matchParentSize().background(Color.DarkGray, RoundedCornerShape(28.dp)))
                    Box(Modifier.matchParentSize().drawBehind {
                        drawCircle(Color.Red, radius = 8.dp.toPx(), center = Offset(40.dp.toPx(), 0f))
                    })
                }
                Box(Modifier.matchParentSize().drawBackdrop(
                    backdrop = source.backdrop,
                    shape = { RectangleShape },
                    effects = {},
                ))
            }
        }
        composeRule.waitForIdle()
        val pixels = composeRule.onNodeWithTag("badge-capture-result").captureToImage().toPixelMap()
        for (yDp in listOf(28f, 36f)) {
            val color = pixels[(pixels.width * 50f / 120f).toInt(), (pixels.height * yDp / 120f).toInt()]
            assertEquals("Badge must retain both halves", 1f, color.red, 0.05f)
            assertEquals(0f, color.blue, 0.05f)
        }
    }

    @Test
    fun nestedNavigationAndHomeSourcesDoNotMultiplyChildDrawing() =
        verifySource(nested = true)

    @Test
    fun themeBackgroundUpdatesKeepVisibleSourceAndModifierStable() =
        verifyRecordingBackground(drawContentToScreen = true)

    @Test
    fun themeBackgroundUpdatesKeepHiddenCaptureAndModifierStable() =
        verifyRecordingBackground(drawContentToScreen = false)

    @Test
    fun sparseGlyphAndImageSourceKeepsAnOpaqueBackgroundThroughBlurAndLens() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        composeRule.setContent {
            CompositionLocalProvider(LocalGlobalWallpaperBackdropVisible provides false) {
                val source = rememberChromeBackdropSource()
                // Magenta exposes accidental transparency. Sparse white strokes and a blue
                // image stand in for an article list with no painted background of its own.
                Box(Modifier.size(160.dp).background(Color.Magenta)) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .then(source.modifier)
                            .sharedGlobalWallpaperBackground(Color.Black)
                            .drawBehind {
                                repeat(4) { index ->
                                    drawRect(
                                        Color.White,
                                        topLeft = Offset(size.width * (0.1f + index * 0.12f), size.height * 0.08f),
                                        size = Size(size.width * 0.04f, size.height * 0.08f),
                                    )
                                }
                                drawRect(
                                    Color.Blue,
                                    topLeft = Offset(0f, size.height * 0.8f),
                                    size = Size(size.width, size.height * 0.2f),
                                )
                            },
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .testTag("sparse-glass-result")
                            .drawBackdrop(
                                backdrop = source.backdrop,
                                shape = { RoundedCornerShape(28.dp) },
                                effects = {
                                    blur(4.dp.toPx())
                                    lens(20.dp.toPx(), 20.dp.toPx())
                                },
                            ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val pixels = composeRule.onNodeWithTag("sparse-glass-result").captureToImage().toPixelMap()
        // The middle is far from the glyph/image footprints. It must stay opaque black,
        // rather than showing the magenta underlay or an expanded white/blue edge texel.
        for (fraction in listOf(0.3f, 0.5f, 0.7f)) {
            val color = pixels[(pixels.width * fraction).toInt(), pixels.height / 2]
            assertEquals(1f, color.alpha, 0.02f)
            assertEquals(0f, color.red, 0.05f)
            assertEquals(0f, color.green, 0.05f)
            assertEquals(0f, color.blue, 0.05f)
        }
    }

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
        val rasterize = mutableStateOf(false)
        val drawCalls = AtomicInteger()
        composeRule.setContent {
            val source = rememberChromeBackdropSource(
                isolateContentDrawing = nested,
                rasterizeContent = rasterize.value,
            )
            val innerSource = rememberChromeBackdropSource(
                isolateContentDrawing = nested,
                rasterizeContent = rasterize.value,
            )
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
                    // 首页顶栏消费内层 feed，导航底栏再消费包含顶栏的外层画面。
                    if (nested) {
                        Box(Modifier.matchParentSize().drawBackdrop(
                            backdrop = innerSource.backdrop,
                            shape = { RectangleShape },
                            effects = {},
                        ))
                    }
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
        if (nested) {
            // 开合、落位、再次开合都必须保留当前像素；记录次数不代表 GPU 帧时间。
            for ((enabled, color) in listOf(true to Color.Red, false to Color.Green, true to Color.Red)) {
                composeRule.runOnIdle { rasterize.value = enabled }
                composeRule.waitForIdle()
                assertCenterColor(tint.value)
                composeRule.runOnIdle {
                    drawCalls.set(0)
                    tint.value = color
                }
                composeRule.waitForIdle()
                assertCenterColor(color)
                composeRule.runOnIdle {
                    assertEquals("Nested sources must retain live child updates", 1, drawCalls.get())
                }
            }
        }
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
