package com.android.purebilibili.core.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.LocalFloatingChromeBackdrop
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.Backdrop

class BackToTopBackdropUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    // A counting source detects unsafe sampling without creating a real RenderNode cycle
    // that could kill the test process before an assertion can report the regression.
    private val ancestorBackdrop = CountingBackdrop()
    private var clicks = 0

    @Test
    fun omittedSourceDoesNotSampleTheAncestorPageRecording() {
        showButton()
        assertButtonDrawsAndClicks()
        assertEquals(0, ancestorBackdrop.draws.get())
    }

    @Test
    fun unreadySourceDoesNotFallBackToTheAncestorPageRecording() {
        showButton(source = mutableStateOf<Backdrop?>(null))
        assertButtonDrawsAndClicks()
        assertEquals(0, ancestorBackdrop.draws.get())
    }

    @Test
    fun explicitSourceCanDisappearAndReturnWithoutSamplingTheAncestor() {
        val siblingBackdrop = CountingBackdrop()
        val source = mutableStateOf<Backdrop?>(siblingBackdrop)
        showButton(source = source)
        rule.onNodeWithTag("button-container").captureToImage()
        assertTrue(siblingBackdrop.draws.get() > 0)

        rule.runOnIdle { source.value = null }
        rule.waitForIdle()
        rule.runOnIdle { siblingBackdrop.draws.set(0) }
        assertButtonDrawsAndClicks()
        assertEquals(0, siblingBackdrop.draws.get())
        assertEquals(0, ancestorBackdrop.draws.get())

        rule.runOnIdle { source.value = siblingBackdrop }
        rule.onNodeWithTag("button-container").captureToImage()
        assertTrue(siblingBackdrop.draws.get() > 0)
        assertEquals(0, ancestorBackdrop.draws.get())
    }

    @Test
    fun glassOffKeepsTheButtonInteractiveWithoutSamplingEitherSource() {
        val siblingBackdrop = CountingBackdrop()
        showButton(
            source = mutableStateOf<Backdrop?>(siblingBackdrop),
            glass = mutableStateOf(false),
        )
        assertButtonDrawsAndClicks()
        assertEquals(0, siblingBackdrop.draws.get())
        assertEquals(0, ancestorBackdrop.draws.get())
    }

    private fun showButton(
        source: State<Backdrop?>? = null,
        glass: State<Boolean> = mutableStateOf(true),
    ) {
        // The positive sampling assertion must exercise the actual shader path.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value),
                    LocalFloatingChromeBackdrop provides ancestorBackdrop,
                ) {
                    Box(Modifier.size(120.dp).testTag("button-container")) {
                        if (source == null) {
                            AppLiquidGlassBackToTopButton(
                                visible = true,
                                onClick = { clicks++ },
                                modifier = Modifier.align(Alignment.Center),
                                draggable = false,
                            )
                        } else {
                            AppLiquidGlassBackToTopButton(
                                visible = true,
                                onClick = { clicks++ },
                                modifier = Modifier.align(Alignment.Center),
                                backdrop = source.value,
                                draggable = false,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertButtonDrawsAndClicks() {
        rule.onNodeWithTag("button-container").captureToImage()
        rule.onNodeWithContentDescription("回到顶部").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    private class CountingBackdrop : Backdrop {
        val draws = AtomicInteger()
        override val isCoordinatesDependent = false

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
            downscaleFactor: Float,
        ) {
            draws.incrementAndGet()
            drawRect(Color.Blue)
        }
    }
}
