package com.android.purebilibili.core.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.blur.AppGlassContainerContent
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.Backdrop

class AppGlassContainerUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    // 用可计数源代表包含消费者的页面，避免真正构造递归渲染图导致进程退出。
    private val pageSource = CountingBackdrop(Color.Red)
    private var clicks = 0

    @Test
    fun nestedDocksUseParentMaterialAndFollowSourceReplacement() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val initial = CountingBackdrop(Color.Blue)
        val replacement = CountingBackdrop(Color.Green)
        val parent = mutableStateOf<Backdrop?>(initial)
        showDocks(parent, mutableStateOf(true))
        capture()
        assertTrue(initial.draws.get() > 0)
        assertEquals(0, pageSource.draws.get())

        rule.runOnIdle { parent.value = null }
        capture()
        assertEquals(0, pageSource.draws.get())

        rule.runOnIdle { parent.value = replacement }
        capture()
        assertTrue(replacement.draws.get() > 0)
        assertEquals(0, pageSource.draws.get())
        rule.runOnIdle { initial.draws.set(0) }
        rule.onNodeWithTag("action").performClick()
        capture()
        assertEquals(0, initial.draws.get())
        assertEquals(1, clicks)
    }

    @Test
    fun glassSwitchReleasesMaterialInBothNativeStylesAndCanRestoreIt() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val material = CountingBackdrop(Color.Blue)
        val glass = mutableStateOf(true)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        showDocks(mutableStateOf(material), glass, style)
        capture()

        rule.runOnIdle { glass.value = false }
        rule.waitForIdle()
        for (nativeStyle in listOf(AppUiStyle.MATERIAL3, AppUiStyle.MIUIX)) {
            rule.runOnIdle { style.value = nativeStyle }
            rule.waitForIdle()
            rule.runOnIdle { material.draws.set(0); pageSource.draws.set(0) }
            capture()
            rule.onNodeWithTag("action").performClick()
            assertEquals(0, material.draws.get())
            assertEquals(0, pageSource.draws.get())
        }
        assertEquals(2, clicks)

        rule.runOnIdle { glass.value = true }
        capture()
        assertTrue(material.draws.get() > 0)
        assertEquals(0, pageSource.draws.get())
    }

    private fun showDocks(
        parent: State<Backdrop?>,
        glass: State<Boolean>,
        style: State<AppUiStyle> = mutableStateOf(AppUiStyle.MATERIAL3),
    ) {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value),
                    LocalAppUiStyle provides style.value,
                ) {
                    AppGlassContainerContent(
                        enabled = true,
                        backdrop = parent.value,
                        ownsDeformation = true,
                    ) {
                        BottomBarMatchedReusableLiquidDock(
                            shape = CircleShape,
                            backdrop = pageSource,
                            reuseEnabled = true,
                            elasticFeedbackEnabled = true,
                            modifier = Modifier.size(160.dp, 64.dp).testTag("dock"),
                        ) {
                            BottomBarMatchedReusableLiquidDock(
                                shape = CircleShape,
                                backdrop = pageSource,
                                reuseEnabled = true,
                                elasticFeedbackEnabled = true,
                            ) {
                                Box(Modifier.size(80.dp, 40.dp).testTag("action").clickable { clicks++ })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun capture() {
        rule.onNodeWithTag("dock").captureToImage()
    }

    private class CountingBackdrop(private val color: Color) : Backdrop {
        val draws = AtomicInteger()
        override val isCoordinatesDependent = false

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
            downscaleFactor: Float,
        ) {
            draws.incrementAndGet()
            drawRect(color)
        }
    }
}
