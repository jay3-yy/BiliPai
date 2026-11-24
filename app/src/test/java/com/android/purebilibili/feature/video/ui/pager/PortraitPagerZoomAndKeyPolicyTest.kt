package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortraitPagerZoomAndKeyPolicyTest {

    @Test
    fun `resolvePortraitPagerItemKey uses stable bvid cid key and disambiguates duplicates`() {
        val items: List<Any> = listOf(
            ViewInfo(bvid = "BV1A", cid = 10L),
            RelatedVideo(bvid = "BV1B", cid = 20L),
            RelatedVideo(bvid = "BV1B", cid = 20L),
            RelatedVideo(bvid = "", cid = 0L),
        )

        assertEquals("BV1A#10", resolvePortraitPagerItemKey(items, 0))
        assertEquals("BV1B#20", resolvePortraitPagerItemKey(items, 1))
        assertEquals("BV1B#20#2", resolvePortraitPagerItemKey(items, 2))
        assertEquals("page:3", resolvePortraitPagerItemKey(items, 3))
    }

    @Test
    fun `resolvePortraitZoomedPan clamps inside scaled bounds and resets at 1x`() {
        val (clampedX, clampedY) = resolvePortraitZoomedPan(
            currentPanX = 400f,
            currentPanY = -900f,
            deltaX = 300f,
            deltaY = -400f,
            scale = 2f,
            containerWidth = 1000f,
            containerHeight = 2000f,
        )
        assertEquals(500f, clampedX)
        assertEquals(-1000f, clampedY)

        val (resetX, resetY) = resolvePortraitZoomedPan(
            currentPanX = 120f,
            currentPanY = 240f,
            deltaX = 10f,
            deltaY = 10f,
            scale = 1f,
            containerWidth = 1000f,
            containerHeight = 2000f,
        )
        assertEquals(0f, resetX)
        assertEquals(0f, resetY)
    }

    @Test
    fun `trimPortraitMetadataMap caps size while preserving active keys`() {
        val map = linkedMapOf(
            "BV1" to 0.56f,
            "BV2" to 0.56f,
            "BV3" to 0.56f,
            "BV4" to 0.56f,
        )
        trimPortraitMetadataMap(map, maxSize = 2, keepKeys = setOf("BV1"))

        assertEquals(2, map.size)
        assertTrue("BV1" in map)
        assertTrue("BV4" in map)
    }
}
