package com.android.purebilibili.feature.home

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AdaptiveScaffoldHazeHandle
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAdaptiveScaffoldHaze
import com.android.purebilibili.core.ui.LocalAdaptiveScaffoldHazeFactory
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconBackdrop
import dev.chrisbanes.haze.HazeState
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.blur.Backdrop

class ScaffoldTopBlurOwnershipUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun externalTopBlurSkipsScaffoldSamplingAndPreservesIconSourceAcrossThemes() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val glass = mutableStateOf(false)
        val external = mutableStateOf(true)
        val factoryCalls = AtomicInteger()
        val handle = AdaptiveScaffoldHazeHandle(HazeState(), mutableStateOf(true))
        lateinit var source: ChromeBackdropSource
        var currentHandle: AdaptiveScaffoldHazeHandle? = null
        var iconBackdrop: Backdrop? = null

        composeRule.setContent {
            val pageSource = rememberChromeBackdropSource()
            SideEffect { source = pageSource }
            CompositionLocalProvider(
                LocalAppUiStyle provides style.value,
                LocalAppThemeConfig provides AppThemeConfig(
                    headerBlurEnabled = true,
                    liquidGlassEnabled = glass.value,
                ),
                LocalAdaptiveScaffoldHazeFactory provides {
                    factoryCalls.incrementAndGet()
                    handle
                },
            ) {
                AppScaffold(
                    topBarBlurHandledExternally = external.value,
                    topBarIconBackdropSource = pageSource,
                    topBar = {
                        val haze = LocalAdaptiveScaffoldHaze.current
                        val backdrop = LocalAppTopBarIconBackdrop.current
                        SideEffect {
                            currentHandle = haze
                            iconBackdrop = backdrop
                        }
                        Box(Modifier.size(40.dp))
                    },
                ) {
                    Box(Modifier.size(80.dp).then(pageSource.modifier))
                }
            }
        }

        for (uiStyle in AppUiStyle.entries) {
            for (glassEnabled in listOf(false, true)) {
                composeRule.runOnIdle {
                    factoryCalls.set(0)
                    style.value = uiStyle
                    glass.value = glassEnabled
                }
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertEquals(0, factoryCalls.get())
                    assertNull(currentHandle)
                    assertSame(if (glassEnabled) source.backdrop else null, iconBackdrop)
                    external.value = false
                }
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertTrue(factoryCalls.get() > 0)
                    assertSame(handle, currentHandle)
                    factoryCalls.set(0)
                    external.value = true
                }
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertEquals(0, factoryCalls.get())
                    assertNull(currentHandle)
                    assertSame(if (glassEnabled) source.backdrop else null, iconBackdrop)
                }
            }
        }
    }
}
