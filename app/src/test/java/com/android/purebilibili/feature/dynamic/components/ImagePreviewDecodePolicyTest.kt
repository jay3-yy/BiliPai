package com.android.purebilibili.feature.dynamic.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImagePreviewDecodePolicyTest {

    @Test
    fun fullscreenPreview_staysBelowCanvasBitmapLimit() {
        val size = resolveImageDecodeSize(ImageDecodeTarget.FULLSCREEN_PREVIEW)

        assertEquals(ImageDecodeSize(widthPx = 4096, heightPx = 4096), size)
        assertTrue(estimateArgb8888ByteCount(size) < 100L * 1024L * 1024L)
    }

    @Test
    fun commentThumbnail_usesSmallerDecodeBudget() {
        val previewSize = resolveImageDecodeSize(ImageDecodeTarget.FULLSCREEN_PREVIEW)
        val thumbnailSize = resolveImageDecodeSize(ImageDecodeTarget.COMMENT_THUMBNAIL)

        assertEquals(ImageDecodeSize(widthPx = 1024, heightPx = 1024), thumbnailSize)
        assertTrue(estimateArgb8888ByteCount(thumbnailSize) < estimateArgb8888ByteCount(previewSize))
    }

    @Test
    fun dynamicGrid_usesSmallerDecodeBudgetForMultiImageThanSingleImage() {
        val singleSize = resolveDynamicGridDecodeSize(displayCount = 1)
        val multiSize = resolveDynamicGridDecodeSize(displayCount = 4)

        assertEquals(ImageDecodeSize(widthPx = 1280, heightPx = 1280), singleSize)
        assertEquals(ImageDecodeSize(widthPx = 768, heightPx = 768), multiSize)
        assertTrue(estimateArgb8888ByteCount(multiSize) < estimateArgb8888ByteCount(singleSize))
    }

    @Test
    fun originalQuality_staysBelowCanvasBitmapLimit() {
        val previewSize = resolveImageDecodeSize(ImageDecodeTarget.FULLSCREEN_PREVIEW)
        val originalSize = resolveImageDecodeSize(ImageDecodeTarget.ORIGINAL_QUALITY)

        assertEquals(ImageDecodeSize(widthPx = 4608, heightPx = 4608), originalSize)
        assertTrue(estimateArgb8888ByteCount(originalSize) > estimateArgb8888ByteCount(previewSize))
        assertTrue(estimateArgb8888ByteCount(originalSize) < 100L * 1024L * 1024L)
    }
}
