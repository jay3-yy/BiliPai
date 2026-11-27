package com.android.purebilibili.feature.anime4k.gl

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GlProgramBinaryCacheTest {
    @Test
    fun cacheKeyChangesWhenDriverOrShaderChanges() {
        val base = buildGlProgramBinaryCacheKey("Qualcomm|Adreno 740|OpenGL ES 3.2", "vs", "fs")
        val otherDriver = buildGlProgramBinaryCacheKey("ARM|Mali-G720|OpenGL ES 3.2", "vs", "fs")
        val otherVertex = buildGlProgramBinaryCacheKey("Qualcomm|Adreno 740|OpenGL ES 3.2", "vs2", "fs")
        val otherFragment = buildGlProgramBinaryCacheKey("Qualcomm|Adreno 740|OpenGL ES 3.2", "vs", "fs2")

        assertEquals(64, base.length)
        assertNotEquals(base, otherDriver)
        assertNotEquals(base, otherVertex)
        assertNotEquals(base, otherFragment)
    }

    @Test
    fun programBinarySerializationRoundTripsAndRejectsTruncatedPayloads() {
        val payload = byteArrayOf(0x12, 0x34, 0x56, 0x78, -1, 0)
        val format = 0x8741 // GL_MALI_PROGRAM_BINARY_ARM
        val serialized = serializeGlProgramBinary(format, payload)
        val restored = deserializeGlProgramBinary(serialized)

        assertNotNull(restored)
        assertEquals(format, restored.first)
        assertContentEquals(payload, restored.second)

        val negativeFormat = -0x1234567
        val negativeRestored = deserializeGlProgramBinary(serializeGlProgramBinary(negativeFormat, payload))
        assertNotNull(negativeRestored)
        assertEquals(negativeFormat, negativeRestored.first)

        assertNull(deserializeGlProgramBinary(byteArrayOf()))
        assertNull(deserializeGlProgramBinary(byteArrayOf(1, 2, 3, 4)))
    }
}
