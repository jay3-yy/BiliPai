package com.android.purebilibili.feature.dynamic.components

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.withBiliImageQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImagePreviewPlaceholderTest {
    @Test
    fun previewReadsTheSourceQualityCacheAndRetainsItsDimensions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = "https://i0.hdslb.com/bfs/archive/preview.jpg"
        val cache = MemoryCache.Builder().maxSizeBytes(1024 * 1024L).build()
        val loader = ImageLoader.Builder(context).memoryCache(cache).build()
        try {
            for (saver in listOf(false, true)) {
                val sourceRequest = ImageRequest.Builder(context).data(source).memoryCacheKey(source).build()
                    .withBiliImageQuality(dataSaver = saver, listQuality = 60)
                val actualKey = MemoryCache.Key(source, sourceRequest.memoryCacheKeyExtras)
                val image = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).asImage()
                cache[actualKey] = MemoryCache.Value(image)
                val preview = resolveImagePreviewPlaceholder(context, loader, source, saver, 60)
                assertEquals(actualKey, preview.cacheKey)
                assertSame(image, preview.image)
                val decoded = requireNotNull(preview.image)
                assertEquals(160, decoded.width)
                assertEquals(90, decoded.height)
            }
            assertNull(resolveImagePreviewPlaceholder(context, loader, source, false, 80).image)
        } finally {
            loader.shutdown()
        }
    }
}
