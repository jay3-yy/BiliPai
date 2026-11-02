package com.android.purebilibili.feature.home

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchEntry
import com.android.purebilibili.core.util.prefetchHomeCoverImages
import com.android.purebilibili.data.model.response.VideoItem
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class HomeCoverDecodeCacheTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun returnPreheatReusesOversizedAndFullSizeSmallBitmapsAcceptedByTheFeed() = runTest {
        val images = listOf(
            Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888).asImage(),
            Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).asImage(),
        )
        val results = ConcurrentLinkedQueue<ImageResult>()
        val loader = loader(results)
        try {
            val entries = images.mapIndexed { index, image ->
                val key = "return-cover-$index"
                requireNotNull(loader.memoryCache)[MemoryCache.Key(key)] = MemoryCache.Value(image)
                // No fetcher supports this URI: a miss must fail instead of using the network.
                HomeCoverReturnPrefetchEntry("BV_$index", "memory-only://$index", key, 640, 400)
            }
            prefetchHomeCoverImages(context, entries, loader)

            assertEquals(2, results.size)
            val byKey = results.map { it as SuccessResult }.associateBy { it.memoryCacheKey?.key }
            images.forEachIndexed { index, image ->
                val result = byKey.getValue("return-cover-$index")
                assertEquals(DataSource.MEMORY_CACHE, result.dataSource)
                assertSame(image, result.image)
            }
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun forwardPreloadReusesDecodedCoverAfterTheFeedStops() {
        val results = ConcurrentLinkedQueue<ImageResult>()
        val loader = loader(results)
        val video = VideoItem(bvid = "BV_preloaded", pic = "memory-only://cover")
        val spec = HomeCoverRequestSpec(640, 400)
        val source = resolveHomeCoverImageSource(video, false, spec)
        val image = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888).asImage()
        requireNotNull(loader.memoryCache)[MemoryCache.Key(source.cacheKey)] = MemoryCache.Value(image)
        try {
            rule.mainClock.autoAdvance = false
            val visible = mutableStateOf<IntRange?>(0..0)
            val scrolling = mutableStateOf(true)
            val videos = listOf(VideoItem(bvid = "BV_visible"), video)
            rule.setContent {
                HomeFeedCoverPreload(videos, visible, scrolling, spec, true, 1, loader)
            }
            rule.mainClock.advanceTimeBy(400)
            assertEquals(0, results.size)
            rule.runOnIdle { scrolling.value = false }
            rule.mainClock.advanceTimeBy(400)
            rule.waitUntil { results.isNotEmpty() }

            val result = results.single() as SuccessResult
            assertEquals(DataSource.MEMORY_CACHE, result.dataSource)
            assertSame(image, result.image)
        } finally {
            loader.shutdown()
        }
    }

    private fun loader(results: ConcurrentLinkedQueue<ImageResult>): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(4L * 1024 * 1024).build() }
            .components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                        chain.proceed().also { results.add(it) }
                })
            }
            .build()
}
