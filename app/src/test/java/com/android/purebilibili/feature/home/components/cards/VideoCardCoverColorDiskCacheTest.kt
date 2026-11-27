package com.android.purebilibili.feature.home.components.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VideoCardCoverColorDiskCacheTest {

    @Test
    fun encodesAndDecodesEntriesInOrderAndRespectsMaxEntriesCap() {
        val original = linkedMapOf(
            hashCoverColorKey64("cover_a") to 0xFF112233.toInt(),
            hashCoverColorKey64("cover_b") to 0xFF445566.toInt(),
            hashCoverColorKey64("cover_c") to 0xFF778899.toInt(),
        )

        val decoded = decodeCoverColorDiskEntries(encodeCoverColorDiskEntries(original))
        assertEquals(original, decoded)

        val capped = decodeCoverColorDiskEntries(encodeCoverColorDiskEntries(original), maxEntries = 2)
        assertEquals(2, capped.size)
        assertEquals(0xFF112233.toInt(), capped[hashCoverColorKey64("cover_a")])
        assertEquals(0xFF445566.toInt(), capped[hashCoverColorKey64("cover_b")])
    }

    @Test
    fun rejectsCorruptedHeaderAndTruncatedEntries() {
        assertTrue(decodeCoverColorDiskEntries(byteArrayOf()).isEmpty())
        assertTrue(decodeCoverColorDiskEntries(ByteArray(12) { 0x7F }).isEmpty())

        val valid = encodeCoverColorDiskEntries(mapOf(hashCoverColorKey64("k") to 0xFFABCDEF.toInt()))
        val truncated = valid.copyOf(valid.size - 3)
        assertTrue(decodeCoverColorDiskEntries(truncated).isEmpty())
    }

    @Test
    fun distinctCacheKeysProduceDistinctHashes() {
        assertNotEquals(
            hashCoverColorKey64("https://i0.hdslb.com/bfs/archive/a.jpg"),
            hashCoverColorKey64("https://i0.hdslb.com/bfs/archive/b.jpg"),
        )
    }
}
