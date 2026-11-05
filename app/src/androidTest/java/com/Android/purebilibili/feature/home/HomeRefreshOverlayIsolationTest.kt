package com.android.purebilibili.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class HomeRefreshOverlayIsolationTest(
    private val uiStyle: AppUiStyle,
    private val glassEnabled: Boolean,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun changingAndDismissingRefreshTipDoesNotRecomposeItsHost() {
        val text = mutableStateOf<String?>("新增 3 条内容")
        val hostCompositions = AtomicInteger()
        composeRule.setContent {
            SideEffect { hostCompositions.incrementAndGet() }
            CompositionLocalProvider(LocalAppUiStyle provides uiStyle) {
                MaterialTheme {
                    HomeRefreshDeltaTip(
                        text = text,
                        listTopPadding = 0.dp,
                        isLiquidGlassEnabled = glassEnabled,
                        isHeaderBlurEnabled = false,
                        isBottomBarBlurEnabled = false,
                    )
                }
            }
        }
        composeRule.onNodeWithText("新增 3 条内容").assertIsDisplayed()
        composeRule.runOnIdle { hostCompositions.set(0) }
        composeRule.runOnIdle { text.value = "暂无新内容" }
        composeRule.onNodeWithText("暂无新内容").assertIsDisplayed()
        composeRule.runOnIdle { text.value = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("暂无新内容").assertDoesNotExist()
        composeRule.runOnIdle { text.value = "新增 1 条内容" }
        composeRule.onNodeWithText("新增 1 条内容").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, hostCompositions.get()) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, glass={1}")
        fun styles(): List<Array<Any>> = listOf(
            arrayOf(AppUiStyle.MATERIAL3, false),
            arrayOf(AppUiStyle.MATERIAL3, true),
            arrayOf(AppUiStyle.MIUIX, false),
            arrayOf(AppUiStyle.MIUIX, true),
        )
    }
}
