package com.android.purebilibili.core.ui.components

import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.originalBiliImage
import com.android.purebilibili.core.util.withBiliImageQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BiliImageQualityRequestTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = "https://i0.hdslb.com/bfs/archive/cover.jpg@640w_400h.webp"

    @Test
    fun repeatedPreparationReusesRequestAndCacheIdentity() {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey("cover")
            .diskCacheKey("cover")
            .placeholderMemoryCacheKey("cover")
            .build()
            .withBiliImageQuality(dataSaver = false, listQuality = 0)

        assertSame(request, request.withBiliImageQuality(dataSaver = false, listQuality = 0))
        assertEquals(request.memoryCacheKeyExtras, request.placeholderMemoryCacheKey?.extras)
        assertEquals("cover|bilipai-image-quality-v1:100", request.diskCacheKey)
    }

    @Test
    fun qualitySwitchUpdatesAllCacheIdentitiesWithoutChangingDimensions() {
        val high = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey("cover")
            .diskCacheKey("cover")
            .placeholderMemoryCacheKey("cover")
            .build()
            .withBiliImageQuality(dataSaver = false, listQuality = 0)
        val saver = high.withBiliImageQuality(dataSaver = true, listQuality = 0)

        assertEquals("https://i0.hdslb.com/bfs/archive/cover.jpg@640w_400h_10q.webp", saver.data)
        assertNotEquals(high.memoryCacheKeyExtras, saver.memoryCacheKeyExtras)
        assertEquals(saver.memoryCacheKeyExtras, saver.placeholderMemoryCacheKey?.extras)
        assertEquals("cover|bilipai-image-quality-v1:10", saver.diskCacheKey)
        assertEquals(high.data, saver.withBiliImageQuality(dataSaver = false, listQuality = 0).data)
        assertEquals(high.diskCacheKey, saver.withBiliImageQuality(dataSaver = false, listQuality = 0).diskCacheKey)
    }

    @Test
    fun originalImageRemainsExemptAcrossQualitySwitches() {
        val original = ImageRequest.Builder(context).data(url).originalBiliImage().build()
        assertSame(original, original.withBiliImageQuality(dataSaver = true, listQuality = 0))
        assertSame(original, original.withBiliImageQuality(dataSaver = false, listQuality = 0))
    }
    @Test
    fun customQualityAndDataSaverKeepDistinctCacheIdentities() {
        val source = ImageRequest.Builder(context).data(url)
            .memoryCacheKey("cover").diskCacheKey("cover").placeholderMemoryCacheKey("cover").build()
        val balanced = source.withBiliImageQuality(dataSaver = false, listQuality = 70)
        val saver = balanced.withBiliImageQuality(dataSaver = true, listQuality = 70)
        val restored = saver.withBiliImageQuality(dataSaver = false, listQuality = 70)
        assertEquals("cover|bilipai-image-quality-v1:70", balanced.diskCacheKey)
        assertEquals("cover|bilipai-image-quality-v1:10", saver.diskCacheKey)
        assertNotEquals(balanced.memoryCacheKeyExtras, saver.memoryCacheKeyExtras)
        assertEquals(restored.memoryCacheKeyExtras, restored.placeholderMemoryCacheKey?.extras)
        assertEquals(balanced.data, restored.data)
        assertEquals(balanced.diskCacheKey, restored.diskCacheKey)
        assertSame(balanced, balanced.withBiliImageQuality(dataSaver = false, listQuality = 70))
        val original = source.newBuilder().originalBiliImage().build()
        assertSame(original, original.withBiliImageQuality(dataSaver = true, listQuality = 40))
    }
}
