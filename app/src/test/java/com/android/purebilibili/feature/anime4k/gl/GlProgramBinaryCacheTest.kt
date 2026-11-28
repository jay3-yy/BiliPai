package com.android.purebilibili.feature.anime4k.gl

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun driverSignatureIncludesBuildFingerprintAndAtomicWriteEvictsOldestEntries() {
        val sigA = buildGlDriverSignature("Qualcomm", "Adreno 740", "OpenGL ES 3.2", "oem/dev/15/A")
        val sigB = buildGlDriverSignature("Qualcomm", "Adreno 740", "OpenGL ES 3.2", "oem/dev/15/B")
        assertNotEquals(
            buildGlProgramBinaryCacheKey(sigA, "vs", "fs"),
            buildGlProgramBinaryCacheKey(sigB, "vs", "fs"),
        )

        val tempDir = Files.createTempDirectory("gl_program_cache_test").toFile()
        try {
            val first = File(tempDir, "a.bin")
            val second = File(tempDir, "b.bin")
            val third = File(tempDir, "c.bin")
            writeGlProgramBinaryAtomically(tempDir, first, serializeGlProgramBinary(1, byteArrayOf(1)), maxCachedPrograms = 2)
            first.setLastModified(1_000L)
            writeGlProgramBinaryAtomically(tempDir, second, serializeGlProgramBinary(1, byteArrayOf(2)), maxCachedPrograms = 2)
            second.setLastModified(2_000L)
            writeGlProgramBinaryAtomically(tempDir, third, serializeGlProgramBinary(1, byteArrayOf(3)), maxCachedPrograms = 2)

            assertFalse(first.exists())
            assertTrue(second.exists())
            assertTrue(third.exists())
            assertFalse(File(tempDir, "c.bin.tmp").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
