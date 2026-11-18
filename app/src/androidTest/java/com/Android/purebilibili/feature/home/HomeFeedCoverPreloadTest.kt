package com.android.purebilibili.feature.home

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.android.purebilibili.data.model.response.VideoItem
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 预取泵的行为约定：**滚动中也要推进近场**，且**窗口变化不取消在途请求**。
 *
 * 断言方式：拦截器记录每次真正开始的请求；内存缓存预先灌好每个封面的解码结果，
 * 因此请求会立即完成（不会挂住后台车道），"记录到几条"就等于"真跑过几条"。
 */
class HomeFeedCoverPreloadTest {
    @get:Rule val rule = createComposeRule()
    private val started = CopyOnWriteArrayList<String>()
    private val completed = CopyOnWriteArrayList<String>()
    private val cancelled = CopyOnWriteArrayList<String>()
    @Volatile private var resumeRequests: CompletableDeferred<Unit>? = null

    @After
    fun closeLoader() = loader.shutdown()

    private val loader: ImageLoader by lazy {
        val image = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).asImage()
        ImageLoader.Builder(ApplicationProvider.getApplicationContext<Context>())
            .memoryCache { MemoryCache.Builder().maxSizeBytes(16L * 1024 * 1024).build() }
            .components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        val url = chain.request.data.toString()
                        started += url
                        return try {
                            resumeRequests?.await()
                            chain.proceed().also { if (it is SuccessResult) completed += url }
                        } catch (error: Throwable) {
                            cancelled += url
                            throw error
                        }
                    }
                })
            }
            .build()
            .also { built ->
                // 每个封面预灌一份内存缓存，保证预取请求立刻完成而不是挂住车道许可。
                val cache = requireNotNull(built.memoryCache)
                FEED_VIDEOS.forEach { video ->
                    val key = resolveHomeCoverImageSource(video, false, REQUEST_SPEC).cacheKey
                    cache[MemoryCache.Key(key)] = MemoryCache.Value(image)
                }
            }
    }

    @Test
    fun prefetchesTheNextRowWhileScrolling() {
        val visible = mutableStateOf<IntRange?>(6..8)
        val scrolling = mutableStateOf(true)
        rule.setContent {
            HomeFeedCoverPreload(
                videos = FEED_VIDEOS,
                visibleVideoRange = visible,
                isScrolling = scrolling,
                gridState = rememberHomeFeedGridState(regularLayout = true),
                columns = 2,
                requestSpec = REQUEST_SPEC,
                allowed = true,
                isDataSaverActive = false,
                isLowRamDevice = false,
                isMeteredNetwork = false,
                aheadCount = 2,
                imageLoader = loader,
            )
        }

        // 滚动中（scrolling = true）也必须把紧邻的一行排上：这是"滑到才加载"的修复点。
        rule.waitUntil(5_000) { requestedNames().containsAll(listOf("feed-9", "feed-10")) }
        rule.waitForIdle()
        assertEquals(0, cancelled.size)
    }

    @Test
    fun keepsStartedWorkAliveWhenTheWindowMovesOn() {
        val visible = mutableStateOf<IntRange?>(6..8)
        rule.setContent {
            HomeFeedCoverPreload(
                videos = FEED_VIDEOS,
                visibleVideoRange = visible,
                isScrolling = null,
                gridState = rememberHomeFeedGridState(regularLayout = true),
                columns = 2,
                requestSpec = REQUEST_SPEC,
                allowed = true,
                isDataSaverActive = false,
                isLowRamDevice = false,
                isMeteredNetwork = false,
                aheadCount = 2,
                imageLoader = loader,
            )
        }
        rule.waitUntil(5_000) { requestedNames().containsAll(listOf("feed-9", "feed-10")) }

        // 视口整体上移：新窗口要接着铺，但已经开始的请求一条都不许被取消。
        rule.runOnIdle { visible.value = 1..3 }
        rule.waitUntil(5_000) { requestedNames().containsAll(listOf("feed-4", "feed-5")) }
        rule.waitForIdle()
        assertEquals(0, cancelled.size)
    }

    @Test
    fun switchingAwayFromCoverTint_keepsAnInFlightCoverRequest() {
        val resume = CompletableDeferred<Unit>()
        resumeRequests = resume
        val warmCoverTint = mutableStateOf(true)
        val visible = mutableStateOf<IntRange?>(6..8)
        val scrolling = mutableStateOf(true)
        rule.setContent {
            HomeFeedCoverPreload(
                videos = FEED_VIDEOS,
                visibleVideoRange = visible,
                isScrolling = scrolling,
                gridState = rememberHomeFeedGridState(regularLayout = true),
                columns = 2,
                requestSpec = REQUEST_SPEC,
                allowed = true,
                isDataSaverActive = false,
                isLowRamDevice = false,
                isMeteredNetwork = false,
                aheadCount = 2,
                imageLoader = loader,
                warmCoverTint = warmCoverTint.value,
            )
        }
        rule.waitUntil(5_000) { "feed-9" in requestedNames() }
        rule.runOnIdle { warmCoverTint.value = false }
        rule.waitForIdle()
        resume.complete(Unit)

        rule.waitUntil(5_000) { completed.any { it.substringAfterLast('/').substringBefore('.') == "feed-9" } }
        assertEquals(1, requestedNames().count { it == "feed-9" })
        assertEquals(0, cancelled.size)
    }

    @Test
    fun aheadCountZeroDisablesTheWholePump() {
        val visible = mutableStateOf<IntRange?>(6..8)
        rule.setContent {
            HomeFeedCoverPreload(
                videos = FEED_VIDEOS,
                visibleVideoRange = visible,
                gridState = rememberHomeFeedGridState(regularLayout = true),
                columns = 2,
                requestSpec = REQUEST_SPEC,
                allowed = true,
                isDataSaverActive = false,
                isLowRamDevice = false,
                isMeteredNetwork = false,
                aheadCount = 0,
                imageLoader = loader,
            )
        }
        rule.waitForIdle()
        assertEquals(emptyList<String>(), requestedNames())
    }

    private fun requestedNames(): List<String> =
        started.map { it.substringAfterLast('/').substringBefore('.') }

    private companion object {
        val REQUEST_SPEC = HomeCoverRequestSpec(640, 400)

        val FEED_VIDEOS: List<VideoItem> = List(12) { index ->
            VideoItem(bvid = "BVfeed$index", pic = "https://example.com/feed-$index.jpg")
        }
    }
}
