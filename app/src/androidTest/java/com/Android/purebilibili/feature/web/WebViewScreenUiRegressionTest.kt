package com.Android.purebilibili.feature.web

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.web.WebViewScreen
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AdaptiveScaffoldHazeHandle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAdaptiveScaffoldHazeFactory
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewScreenUiRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun webContentNeverEntersTopBarBlurCaptureAcrossStylesAndGlassModes() {
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val glass = mutableStateOf(false)
        val handle = AdaptiveScaffoldHazeHandle(HazeState(), mutableStateOf(true))
        var captureRequests = 0
        lateinit var rootView: View
        composeRule.setContent {
            val view = LocalView.current
            SideEffect { rootView = view.rootView }
            CompositionLocalProvider(
                LocalAppUiStyle provides style.value,
                LocalAppThemeConfig provides AppThemeConfig(
                    headerBlurEnabled = true,
                    liquidGlassEnabled = glass.value,
                ),
                LocalAdaptiveScaffoldHazeFactory provides {
                    captureRequests++
                    handle
                },
            ) {
                MaterialTheme {
                    // Exercise a real system WebView without depending on a remote page.
                    WebViewScreen(url = "about:blank", onBack = {})
                }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            for (glassEnabled in listOf(false, true)) {
                composeRule.runOnIdle {
                    style.value = uiStyle
                    glass.value = glassEnabled
                }
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertTrue(containsWebView(rootView))
                    assertEquals(0, captureRequests)
                }
            }
        }
    }

    @Test
    fun retainedExternalEntryDoesNotCreateWebViewOrRelaunchOnRecomposition() {
        val title = mutableStateOf("原文")
        var openAttempts = 0
        var backCalls = 0
        lateinit var rootView: View
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openAttempts++
            }
        }
        composeRule.setContent {
            val view = LocalView.current
            SideEffect { rootView = view.rootView }
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                // Navigation transitions can retain an entry after onBack has been requested.
                WebViewScreen(
                    url = "https://www.gcores.com/articles/220966",
                    title = title.value,
                    onBack = { backCalls++ },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, openAttempts)
            assertEquals(1, backCalls)
            assertFalse(containsWebView(rootView))
            title.value = "文章原文"
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, openAttempts)
            assertEquals(1, backCalls)
            assertFalse(containsWebView(rootView))
        }
    }

    @Test
    fun missingExternalHandlerReturnsToArticleWithoutWebViewFallbackAndAllowsRetry() {
        val showBrowser = mutableStateOf(false)
        var handlerAvailable = false
        var openAttempts = 0
        var backCalls = 0
        lateinit var rootView: View
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openAttempts++
                if (!handlerAvailable) throw IllegalArgumentException("No external handler")
            }
        }
        composeRule.setContent {
            val view = LocalView.current
            SideEffect { rootView = view.rootView }
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MaterialTheme {
                    Column {
                        Text("正在阅读文章")
                        Button(onClick = { showBrowser.value = true }) { Text("打开原文") }
                    }
                    if (showBrowser.value) {
                        WebViewScreen(
                            url = "http://www.jintiankansha.me/post/one",
                            title = "原文",
                            onBack = {
                                backCalls++
                                showBrowser.value = false
                            },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("打开原文").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("正在阅读文章").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, openAttempts)
            assertEquals(1, backCalls)
            assertFalse(showBrowser.value)
            assertFalse(containsWebView(rootView))
            handlerAvailable = true
        }
        composeRule.onNodeWithText("打开原文").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("正在阅读文章").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, openAttempts)
            assertEquals(2, backCalls)
            assertFalse(showBrowser.value)
            assertFalse(containsWebView(rootView))
        }
    }

    private fun containsWebView(view: View): Boolean {
        if (view is WebView) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsWebView(view.getChildAt(index))) return true
            }
        }
        return false
    }
}
