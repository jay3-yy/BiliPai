package com.android.purebilibili.feature.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadMergePolicyTest {

    @Test
    fun `isValidPartialContentResponse accepts matching 206 response`() {
        assertTrue(
            isValidPartialContentResponse(
                responseCode = 206,
                contentRange = "bytes 0-99/200",
                requestedStart = 0L,
                requestedEnd = 99L
            )
        )
    }

    @Test
    fun `isValidPartialContentResponse rejects full body 200 response for range download`() {
        assertFalse(
            isValidPartialContentResponse(
                responseCode = 200,
                contentRange = null,
                requestedStart = 0L,
                requestedEnd = 99L
            )
        )
    }

    @Test
    fun `isValidPartialContentResponse rejects mismatched range header`() {
        assertFalse(
            isValidPartialContentResponse(
                responseCode = 206,
                contentRange = "bytes 0-49/200",
                requestedStart = 0L,
                requestedEnd = 99L
            )
        )
    }

    @Test
    fun `resolveMuxerSampleBufferSize grows to largest advertised sample size`() {
        assertEquals(
            2 * 1024 * 1024,
            resolveMuxerSampleBufferSize(
                maxInputSizes = listOf(256 * 1024, 2 * 1024 * 1024)
            )
        )
    }

    @Test
    fun `resolveMuxerSampleBufferSize keeps default floor when sizes are missing`() {
        assertEquals(
            1024 * 1024,
            resolveMuxerSampleBufferSize(maxInputSizes = emptyList())
        )
    }
}
