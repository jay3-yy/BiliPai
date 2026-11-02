package com.Android.purebilibili.feature.web

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.web.browserThemeContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewAppThemeTest {
    @Test
    fun explicitAppThemeControlsTheWebPageMediaQueryInBothDirections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        listOf(false, true, false).forEach { dark ->
            val finished = CountDownLatch(1)
            val result = AtomicReference<String>()
            lateinit var webView: WebView
            instrumentation.runOnMainSync {
                webView = WebView(browserThemeContext(instrumentation.targetContext, dark)).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript("matchMedia('(prefers-color-scheme: dark)').matches") {
                                result.set(it)
                                finished.countDown()
                            }
                        }
                    }
                    loadDataWithBaseURL("https://manga.bilibili.com/", "<html><head><meta name='color-scheme' content='light dark'></head><body>商品</body></html>", "text/html", "UTF-8", null)
                }
            }
            try {
                assertTrue("网页未加载完成", finished.await(10, TimeUnit.SECONDS))
                assertEquals(dark.toString(), result.get())
            } finally {
                instrumentation.runOnMainSync { webView.destroy() }
            }
        }
    }
}
