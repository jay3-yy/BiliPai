package com.android.purebilibili.core.lifecycle

import android.graphics.Canvas
import coil3.Image
import coil3.memory.MemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundImageCacheBudgetTest {
    private val mib = 1024L * 1024

    @Test
    fun writesStayWithinBackgroundLimitAndForegroundRestoresCapacity() {
        val cache = MemoryCache.Builder().maxSizeBytes(96 * mib).build()
        val budget = BackgroundImageCacheBudget(cache)
        fun writeImages() {
            repeat(8) { cache[MemoryCache.Key("cover-$it")] = MemoryCache.Value(SizedImage(20 * mib)) }
        }
        writeImages()
        budget.background(0L)
        writeImages()
        assertEquals(64 * mib, cache.maxSize)
        assertTrue(cache.size <= 64 * mib)
        budget.background(BACKGROUND_IMAGE_TRIM_DELAY_MS)
        writeImages()
        assertEquals(32 * mib, cache.maxSize)
        assertTrue(cache.size <= 32 * mib)
        val retainedBytes = cache.size
        budget.foreground()
        assertEquals(96 * mib, cache.maxSize)
        assertEquals(retainedBytes, cache.size)
    }

    @Test
    fun backgroundNeverRaisesASmallDeviceCacheBudget() {
        val cache = MemoryCache.Builder().maxSizeBytes(16 * mib).build()
        val budget = BackgroundImageCacheBudget(cache)
        budget.background(0L)
        assertEquals(16 * mib, cache.maxSize)
        budget.background(BACKGROUND_IMAGE_TRIM_DELAY_MS)
        assertEquals(16 * mib, cache.maxSize)
        budget.foreground()
        assertEquals(16 * mib, cache.maxSize)
    }

    // 使用真实 MemoryCache 的写入与淘汰行为，不分配大位图。
    private class SizedImage(override val size: Long) : Image {
        override val width = 1
        override val height = 1
        override val shareable = true
        override fun draw(canvas: Canvas) = Unit
    }
}
