package com.android.purebilibili.feature.video.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameOcrPolicyTest {

    @Test
    fun normalizesWhitespaceAndDropsBlankBlocks() {
        val blocks = normalizeOcrBlocks(
            listOf(
                "  ",
                "  第一行 \r\n\n  第二行  ",
                "\n",
                "val x = 42",
            )
        )
        assertEquals(2, blocks.size)
        assertEquals("第一行\n第二行", blocks[0].text)
        assertEquals("val x = 42", blocks[1].text)
    }

    @Test
    fun joinsOnlySelectedBlocksInOrder() {
        val blocks = normalizeOcrBlocks(listOf("A", "B", "C"))
        assertEquals("A\n\nC", joinSelectedOcrText(blocks, setOf(0, 2)))
        assertEquals("", joinSelectedOcrText(blocks, emptySet()))
    }

    @Test
    fun mapsModelDownloadAndGmsErrorsToReadableChineseHints() {
        assertTrue(resolveOcrFailureMessage("Waiting for the text optional module to be downloaded").contains("首次准备"))
        assertTrue(resolveOcrFailureMessage("Google Play services unavailable").contains("Google Play"))
        assertTrue(resolveOcrFailureMessage("unknown").contains("未能识别"))
    }
}
