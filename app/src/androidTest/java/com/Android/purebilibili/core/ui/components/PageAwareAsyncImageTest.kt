package com.android.purebilibili.core.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.crossfadeMillis
import com.android.purebilibili.feature.home.LocalHomeCardCoverLoadingAllowed
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PageAwareAsyncImageTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun backgroundReleaseDropsImageAndPainterWithoutWaitingForRecomposition() {
        val memory = PageImageMemory()
        val slot = PageImageSlot(memory)
        memory.register(slot)
        val bitmap = solidBitmap(android.graphics.Color.RED)
        val painter = object : androidx.compose.ui.graphics.painter.Painter(), androidx.compose.runtime.RememberObserver {
            override val intrinsicSize = androidx.compose.ui.geometry.Size(8f, 8f)
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() = Unit
            override fun onRemembered() = Unit
            override fun onForgotten() { forgotten++ }
            override fun onAbandoned() = Unit
            var forgotten = 0
        }
        val resource = slot.hold(bitmap.asImage(), painter)
        resource.onRemembered()
        slot.retain(resource)
        memory.release()
        // 同一调用栈中检查，无帧推进、无 Compose 重组。
        assertNull(resource.image)
        assertEquals(1, painter.forgotten)
        assertEquals(0L, resource.size)
        assertNull(slot.retained)
        memory.resume()
        // 后台前发出的旧请求即使回前台才返回，也不能重新占用旧槽位。
        val lateResult = slot.hold(bitmap.asImage(), null)
        assertNull(lateResult.image)
        memory.unregister(slot)
    }

    @Test
    fun releasedImageReloadsCurrentIdentityAndKeepsNativeCallbackImageType() {
        val memory = PageImageMemory()
        val image = mutableStateOf(solidBitmap(android.graphics.Color.RED))
        val successes = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalPageImageMemory provides memory) {
                PageAwareAsyncImage(
                    model = image.value,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).background(Color.Gray).testTag("page-image"),
                    onSuccess = {
                        org.junit.Assert.assertTrue(it.result.image is coil3.BitmapImage)
                        successes.incrementAndGet()
                    },
                )
            }
        }
        composeRule.waitUntil(5_000) { successes.get() == 1 }
        composeRule.runOnIdle { memory.release() }
        composeRule.waitForIdle()
        assertPixel(Color.Gray)
        composeRule.runOnIdle { image.value = solidBitmap(android.graphics.Color.BLUE) }
        composeRule.waitForIdle()
        assertEquals(1, successes.get())
        composeRule.runOnIdle { memory.resume() }
        composeRule.waitUntil(5_000) { successes.get() == 2 }
        assertPixel(Color.Blue)
    }

    @Test
    fun decodedHomeBitmapSurvivesTransitionPauseWithoutRestartingRequest() {
        val allowed = mutableStateOf(true)
        val image = mutableStateOf(solidBitmap(android.graphics.Color.RED))
        val successes = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalHomeCardCoverLoadingAllowed provides allowed.value) {
                PageAwareAsyncImage(
                    model = image.value,
                    loadingAllowed = allowed.value,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).background(Color.Gray).testTag("page-image"),
                    onSuccess = { successes.incrementAndGet() },
                )
            }
        }
        composeRule.waitUntil(5_000) { successes.get() == 1 }
        for (loadAllowed in listOf(false, true, false, true, false)) {
            composeRule.runOnIdle { allowed.value = loadAllowed }
            composeRule.waitForIdle()
            assertPixel(Color.Red)
            assertEquals("Returning home must reuse the loaded node", 1, successes.get())
        }
        // 暂停中换成另一张图片，既不能借用旧像素，也不能因保留节点而发出新请求。
        composeRule.runOnIdle { image.value = solidBitmap(android.graphics.Color.BLUE) }
        composeRule.waitForIdle()
        assertPixel(Color.Gray)
        assertEquals(1, successes.get())
        composeRule.runOnIdle { allowed.value = true }
        composeRule.waitUntil(5_000) { successes.get() == 2 }
        assertPixel(Color.Blue)
    }

    @Test
    fun homeImageHasNoFadeAndDoesNotRestartWhenLoadWindowChanges() {
        val loadWindow = mutableStateOf(true)
        val successes = AtomicInteger()
        val requests = java.util.concurrent.ConcurrentLinkedQueue<ImageRequest>()
        val bitmap = solidBitmap(android.graphics.Color.RED)
        composeRule.setContent {
            val context = LocalContext.current
            val request = androidx.compose.runtime.remember(context) {
                ImageRequest.Builder(context).data(bitmap).crossfade(200).build()
            }
            CompositionLocalProvider(LocalHomeCardCoverLoadingAllowed provides loadWindow.value) {
                PageAwareAsyncImage(
                    model = request,
                    // 单独改变首页窗口许可，验证它不会作为图片请求身份的一部分。
                    loadingAllowed = true,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    onSuccess = {
                        requests.add(it.result.request)
                        successes.incrementAndGet()
                    },
                )
            }
        }
        composeRule.waitUntil(5_000) { successes.get() == 1 }
        assertEquals(0, requests.single().crossfadeMillis)
        composeRule.runOnIdle { loadWindow.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { loadWindow.value = true }
        composeRule.waitForIdle()
        assertEquals(1, successes.get())
    }

    @Test
    fun nonHomeImageRetainsItsRequestedFade() {
        val requests = java.util.concurrent.ConcurrentLinkedQueue<ImageRequest>()
        val bitmap = solidBitmap(android.graphics.Color.RED)
        composeRule.setContent {
            val context = LocalContext.current
            val request = androidx.compose.runtime.remember(context) {
                ImageRequest.Builder(context).data(bitmap).crossfade(100).build()
            }
            PageAwareAsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                onSuccess = { requests.add(it.result.request) },
            )
        }
        composeRule.waitUntil(5_000) { requests.isNotEmpty() }
        assertEquals(100, requests.single().crossfadeMillis)
    }

    @Test
    fun inactivePageDefersRequestAndKeepsDecodedImageWhenHiddenAgain() {
        val allowed = mutableStateOf(false)
        val successes = AtomicInteger()
        val bitmap = solidBitmap(android.graphics.Color.RED)
        composeRule.setContent {
            CompositionLocalProvider(LocalPageImageLoadingAllowed provides allowed.value) {
                PageAwareAsyncImage(
                    model = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).background(Color.Gray).testTag("page-image"),
                    onSuccess = { successes.incrementAndGet() },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, successes.get())
        assertPixel(Color.Gray)
        composeRule.runOnIdle { allowed.value = true }
        composeRule.waitUntil(5_000) { successes.get() == 1 }
        composeRule.waitForIdle()
        assertPixel(Color.Red)
        composeRule.runOnIdle { allowed.value = false }
        composeRule.waitForIdle()
        assertEquals(1, successes.get())
        assertPixel(Color.Red)
    }

    @Test
    fun changedImageIdentityWhileInactiveDoesNotDisplayOldResult() {
        val allowed = mutableStateOf(true)
        val image = mutableStateOf(solidBitmap(android.graphics.Color.RED))
        val successes = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalPageImageLoadingAllowed provides allowed.value) {
                PageAwareAsyncImage(
                    model = image.value,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).background(Color.Gray).testTag("page-image"),
                    onSuccess = { successes.incrementAndGet() },
                )
            }
        }
        composeRule.waitUntil(5_000) { successes.get() == 1 }
        composeRule.runOnIdle {
            allowed.value = false
            image.value = solidBitmap(android.graphics.Color.BLUE)
        }
        composeRule.waitForIdle()
        assertEquals(1, successes.get())
        assertPixel(Color.Gray)
        composeRule.runOnIdle { allowed.value = true }
        composeRule.waitUntil(5_000) { successes.get() == 2 }
        composeRule.waitForIdle()
        assertPixel(Color.Blue)
    }

    @Test
    fun pausedRemountedCoverUsesExactMemoryCacheIdentityWithoutLoading() {
        val cacheKey = mutableStateOf("red-cover")
        val readCache = mutableStateOf(true)
        val successes = AtomicInteger()
        val memoryCache = MemoryCache.Builder().maxSizeBytes(1024 * 1024L).build().apply {
            set(MemoryCache.Key("red-cover"), MemoryCache.Value(solidBitmap(android.graphics.Color.RED).asImage()))
            set(MemoryCache.Key("blue-cover"), MemoryCache.Value(solidBitmap(android.graphics.Color.BLUE).asImage()))
        }
        composeRule.setContent {
            val context = LocalContext.current
            val loader = androidx.compose.runtime.remember { ImageLoader.Builder(context).memoryCache(memoryCache).build() }
            PageAwareAsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://example.invalid/cover")
                    .memoryCacheKey(cacheKey.value)
                    .memoryCachePolicy(if (readCache.value) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .build(),
                loadingAllowed = false,
                imageLoader = loader,
                contentDescription = null,
                modifier = Modifier.size(64.dp).background(Color.Gray).testTag("page-image"),
                onSuccess = { successes.incrementAndGet() },
            )
        }
        composeRule.waitForIdle()
        assertPixel(Color.Red)
        composeRule.runOnIdle { cacheKey.value = "blue-cover" }
        assertPixel(Color.Blue)
        composeRule.runOnIdle { cacheKey.value = "missing-cover" }
        assertPixel(Color.Gray)
        composeRule.runOnIdle { cacheKey.value = "red-cover" }
        assertPixel(Color.Red)
        composeRule.runOnIdle { readCache.value = false }
        assertPixel(Color.Gray)
        assertEquals(0, successes.get())
    }

    private fun solidBitmap(color: Int) = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
        eraseColor(color)
    }

    private fun assertPixel(expected: Color) {
        val image = composeRule.onNodeWithTag("page-image").captureToImage()
        val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
        assertEquals(expected.red, pixel.red, 0.02f)
        assertEquals(expected.green, pixel.green, 0.02f)
        assertEquals(expected.blue, pixel.blue, 0.02f)
    }
}
