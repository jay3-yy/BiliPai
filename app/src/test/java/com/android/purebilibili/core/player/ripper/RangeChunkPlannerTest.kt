package com.android.purebilibili.core.player.ripper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RangeChunkPlannerTest {

    @Test
    fun `parses content range with total`() {
        val range = parseContentRange("bytes 100-199/1000")
        assertEquals(ContentRange(100, 199, 1000), range)
        assertEquals(100, range?.length)
    }

    @Test
    fun `parses content range with unknown total`() {
        assertEquals(ContentRange(0, 9, null), parseContentRange("bytes 0-9/*"))
    }

    @Test
    fun `rejects malformed or inconsistent content range`() {
        assertNull(parseContentRange(null))
        assertNull(parseContentRange("bytes 10-5/100"))
        assertNull(parseContentRange("bytes 0-100/100"))
        assertNull(parseContentRange("items 0-1/2"))
    }

    @Test
    fun `warmup chunks use the smaller first size then grow`() {
        val first = planNextChunk(index = 0, position = 0, remaining = null, chunkBytes = 512, firstChunkBytes = 128)
        val third = planNextChunk(index = 2, position = 256, remaining = null, chunkBytes = 512, firstChunkBytes = 128)
        assertEquals(RangeChunk(0, 0, 128), first)
        assertEquals(RangeChunk(2, 256, 512), third)
    }

    @Test
    fun `chunk never exceeds remaining bytes`() {
        assertEquals(RangeChunk(5, 900, 100), planNextChunk(index = 5, position = 900, remaining = 100, chunkBytes = 512))
        assertNull(planNextChunk(index = 6, position = 1000, remaining = 0, chunkBytes = 512))
    }

    @Test
    fun `split range covers the whole range in order without gaps`() {
        val chunks = splitRange(start = 10, length = 1_000_000, concurrency = 8, minChunkBytes = 64 * 1024)
        assertEquals(8, chunks.size)
        assertEquals(10, chunks.first().start)
        assertEquals(10 + 1_000_000 - 1, chunks.last().endInclusive)
        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.endInclusive + 1, b.start) }
    }

    @Test
    fun `split range does not create chunks smaller than the minimum`() {
        val chunks = splitRange(start = 0, length = 100 * 1024, concurrency = 32, minChunkBytes = 64 * 1024)
        assertEquals(2, chunks.size)
    }

    @Test
    fun `concurrency and chunk size are clamped`() {
        assertEquals(1, clampRipperConcurrency(0))
        assertEquals(RIPPER_MAX_CONCURRENCY, clampRipperConcurrency(999))
        assertEquals(RIPPER_MIN_CHUNK_BYTES, clampRipperChunkBytes(1))
        assertEquals(RIPPER_MAX_CHUNK_BYTES, clampRipperChunkBytes(Long.MAX_VALUE))
    }
}
