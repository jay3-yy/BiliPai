package com.android.purebilibili.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BiliImageQualityPolicyTest {
    private val image = "https://i0.hdslb.com/bfs/archive/cover.jpg"

    @Test
    fun `normal and saver use distinct quality URLs without changing dimensions`() {
        val sized = "$image@960w_600h_1c.webp"
        assertEquals("$image@960w_600h_1c_100q.webp", BiliImageQualityPolicy.resolveUrl(sized, false))
        assertEquals("$image@960w_600h_1c_10q.webp", BiliImageQualityPolicy.resolveUrl(sized, true))
        assertNotEquals(BiliImageQualityPolicy.resolveUrl(sized, false), BiliImageQualityPolicy.resolveUrl(sized, true))
    }

    @Test
    fun `existing quality is replaced and repeated preparation is idempotent`() {
        val saver = BiliImageQualityPolicy.resolveUrl("$image@640w_400h_80q.webp", true)
        assertEquals("$image@640w_400h_10q.webp", saver)
        assertEquals(saver, BiliImageQualityPolicy.resolveUrl(saver, true))
        assertEquals("$image@640w_400h_100q.webp", BiliImageQualityPolicy.resolveUrl(saver, false))
        assertEquals("$image@640w_400h_100q.webp", BiliImageQualityPolicy.resolveUrl("$image@640w_400h", false))
    }

    @Test
    fun `raw cover and avatar keep their aspect ratio`() {
        assertEquals("$image@100q.webp", BiliImageQualityPolicy.resolveUrl(image, false))
        assertEquals("https://i1.hdslb.com/bfs/face/avatar.jpg@100q.webp",
            BiliImageQualityPolicy.resolveUrl("https://i1.hdslb.com/bfs/face/avatar.jpg", false))
    }

    @Test
    fun `foreign signed animated and local resources remain unchanged`() {
        listOf(
            "https://example.com/image.jpg",
            "https://hdslb.com.example.com/image.jpg",
            "$image?signature=secret",
            "$image@!thumbnail",
            "https://i0.hdslb.com/bfs/face/avatar.gif",
            "https://i0.hdslb.com/bfs/emote/emote.png",
            "https://i0.hdslb.com/bfs/garb/pendant.webp",
            "android.resource://com.android.purebilibili/42",
            "file:///tmp/image.png",
        ).forEach { url ->
            assertEquals(url, BiliImageQualityPolicy.resolveUrl(url, false))
            assertEquals(url, BiliImageQualityPolicy.resolveUrl(url, true))
        }
    }
    @Test
    fun `custom quality replaces existing quality without stacking transforms`() {
        val balanced = BiliImageQualityPolicy.resolveUrl("$image@960w_600h_1c_100q.webp", 70)
        assertEquals("$image@960w_600h_1c_70q.webp", balanced)
        assertEquals(balanced, BiliImageQualityPolicy.resolveUrl(balanced, 70))
        assertEquals("$image@960w_600h_1c_40q.webp", BiliImageQualityPolicy.resolveUrl(balanced, 40))
        assertEquals(image + "@1q.webp", BiliImageQualityPolicy.resolveUrl(image, -1))
        assertEquals(image + "@100q.webp", BiliImageQualityPolicy.resolveUrl(image, 500))
    }

    @Test
    fun `custom preview quality preserves signed foreign and animated resources`() {
        listOf("$image?sign=x", "https://example.com/photo.jpg", "https://i0.hdslb.com/bfs/face/a.gif").forEach {
            assertEquals(it, BiliImageQualityPolicy.resolveUrl(it, 40))
        }
    }
    @Test
    fun `view original overrides preview compression and default keeps source URL`() {
        assertEquals(image, BiliImageQualityPolicy.resolvePreviewUrl(image, 0, false))
        assertEquals(image, BiliImageQualityPolicy.resolvePreviewUrl(image, 40, true))
        assertEquals("$image@40q.webp", BiliImageQualityPolicy.resolvePreviewUrl(image, 40, false))
    }
}
