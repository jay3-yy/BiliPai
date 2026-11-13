package com.android.purebilibili.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.settings.ui.SettingsPinnedChromeLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SettingsPinnedChromeBlurTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun scrollingListReachesBlurBehindPinnedChromeAndUpdatesLive() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalAppThemeConfig provides AppThemeConfig(
                        headerBlurEnabled = false,
                        progressiveTopBlurEnabled = true,
                        progressiveTopFadeEnabled = false,
                    ),
                ) {
                    val source = rememberChromeBackdropSource(recordingBackground = Color.White)
                    val state = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()
                    SideEffect { list = state; scope = coroutineScope }
                    SettingsPinnedChromeLayout(
                        modifier = Modifier.size(240.dp, 320.dp),
                        chrome = {
                            BiliPaiImmersiveTopBar(
                                backdrop = source.takeIf { it.isReady }?.backdrop,
                                enabled = true,
                                fadeEnabled = false,
                                surfaceColor = Color.White,
                            ) {
                                Box(Modifier.fillMaxWidth().height(64.dp).testTag("pinned-chrome"))
                            }
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().then(source.modifier).background(Color.White)) {
                            LazyColumn(state = state, contentPadding = padding) {
                                items(12) { index ->
                                    Box(Modifier.fillMaxWidth().height(160.dp)
                                        .background(if (index == 0) Color.Green else Color.Blue))
                                }
                            }
                        }
                    }
                }
            }
        }
        fun headerPixel(): Color {
            val pixels = rule.onNodeWithTag("pinned-chrome").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 4]
        }
        rule.waitForIdle()
        val initial = headerPixel()
        assertTrue("Initial list inset keeps content below the header", initial.red > 0.9f)
        rule.runOnIdle { scope.launch { list.scrollToItem(2) } }
        rule.waitForIdle()
        val scrolled = headerPixel()
        assertTrue("Header must sample the scrolling list, not a background-only layer",
            scrolled.blue > 0.7f && scrolled.red < 0.3f)
        rule.runOnIdle { scope.launch { list.scrollToItem(0) } }
        rule.waitForIdle()
        assertTrue("Returning to the top must refresh the sampled background", headerPixel().red > 0.9f)
    }
}
