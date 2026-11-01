package com.android.purebilibili.feature.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.android.purebilibili.data.model.response.VideoItem
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeFeedCoverPreloadTest {
    @get:Rule val rule = createComposeRule()
    private val started = CopyOnWriteArrayList<String>()
    private val cancelled = CopyOnWriteArrayList<String>()
    private val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Context>())
        .components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    val url = chain.request.data.toString()
                    started += url
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled += url
                    }
                }
            })
        }
        .build()

    @After fun closeLoader() = loader.shutdown()

    @Test
    fun scrollAndVisibilityCancelInFlightPreloadsAndResumeFromCurrentViewport() {
        rule.mainClock.autoAdvance = false
        val visible = mutableStateOf<IntRange?>(6..8)
        val scrolling = mutableStateOf(false)
        val allowed = mutableStateOf(true)
        val videos = videos("feed")
        rule.setContent {
            HomeFeedCoverPreload(videos, visible, scrolling, HomeCoverRequestSpec(640, 400), allowed.value, 2, loader)
        }
        rule.mainClock.advanceTimeBy(400)
        rule.waitUntil { started.size == 2 }
        assertRequested("feed-9", "feed-10")

        rule.runOnIdle { scrolling.value = true }
        rule.mainClock.advanceTimeByFrame()
        rule.waitUntil { cancelled.size == 2 }
        rule.runOnIdle {
            visible.value = 1..3
            allowed.value = false
            scrolling.value = false
        }
        rule.mainClock.advanceTimeBy(800)
        assertEquals(2, started.size)

        rule.runOnIdle { allowed.value = true }
        rule.mainClock.advanceTimeBy(400)
        rule.waitUntil { started.size == 4 }
        assertRequested("feed-9", "feed-10", "feed-4", "feed-5")
        rule.runOnIdle { allowed.value = false }
        rule.mainClock.advanceTimeByFrame()
        rule.waitUntil { cancelled.size == 4 }
    }

    @Test
    fun replacingFeedCancelsOldRequestsAndUsesTheNewContents() {
        rule.mainClock.autoAdvance = false
        val visible = mutableStateOf<IntRange?>(6..8)
        val scrolling = mutableStateOf(false)
        val feedVideos = mutableStateOf(videos("first"))
        rule.setContent {
            HomeFeedCoverPreload(feedVideos.value, visible, scrolling, HomeCoverRequestSpec(640, 400), true, 2, loader)
        }
        rule.mainClock.advanceTimeBy(400)
        rule.waitUntil { started.size == 2 }

        rule.runOnIdle {
            feedVideos.value = videos("second")
            visible.value = 0..1
        }
        rule.mainClock.advanceTimeBy(400)
        rule.waitUntil { cancelled.size == 2 && started.size == 4 }
        assertRequested("first-9", "first-10", "second-2", "second-3")
        assertTrue(cancelled.all { "first-" in it })
    }

    private fun videos(prefix: String) = List(12) {
        VideoItem(bvid = "BV$prefix$it", pic = "https://example.com/$prefix-$it.jpg")
    }

    private fun assertRequested(vararg names: String) {
        assertEquals(names.toSet(), started.map { it.substringAfterLast('/').substringBefore('.') }.toSet())
    }
}
