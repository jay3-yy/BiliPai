package com.android.purebilibili.feature.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadResumePolicyTest {

    @Test
    fun partialFileWithRangeSupport_resumesFromExistingBytes() {
        val plan = resolveResumableDownloadPlan(
            existingBytes = 40L,
            totalBytes = 100L,
            acceptsRanges = true
        )

        assertTrue(plan.append)
        assertEquals(40L, plan.rangeStartBytes)
        assertEquals(40L, plan.initialDownloadedBytes)
        assertEquals(100L, plan.totalBytes)
        assertFalse(plan.alreadyComplete)
    }

    @Test
    fun partialFileWithoutRangeSupport_restartsFromZero() {
        val plan = resolveResumableDownloadPlan(
            existingBytes = 40L,
            totalBytes = 100L,
            acceptsRanges = false
        )

        assertFalse(plan.append)
        assertEquals(0L, plan.rangeStartBytes)
        assertEquals(0L, plan.initialDownloadedBytes)
        assertFalse(plan.alreadyComplete)
    }

    @Test
    fun fullFile_skipsDownload() {
        val plan = resolveResumableDownloadPlan(
            existingBytes = 100L,
            totalBytes = 100L,
            acceptsRanges = true
        )

        assertTrue(plan.alreadyComplete)
        assertEquals(100L, plan.initialDownloadedBytes)
    }
    @Test
    fun oversizedTemporaryFileRestartsInsteadOfBeingPublished() {
        val plan = resolveResumableDownloadPlan(existingBytes = 101L, totalBytes = 100L, acceptsRanges = true)
        assertFalse(plan.alreadyComplete)
        assertFalse(plan.append)
        assertEquals(0L, plan.rangeStartBytes)
    }
}
