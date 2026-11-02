package com.android.purebilibili.core.theme

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppFontWeightAxisTest {
    @Test
    fun staticFontDoesNotAdvertiseVariableWeights() {
        withFont(ByteBuffer.allocate(12).putInt(0x00010000).array()) {
            assertNull(readAppFontWeightAxis(it))
        }
    }

    @Test
    fun weightAxisIsFoundAfterOtherAxesAndPreservesFractionalRange() {
        withFont(variableFont(collection = false)) {
            assertEquals(AppFontWeightAxis(250.5f, 450f, 850f), readAppFontWeightAxis(it))
        }
    }

    @Test
    fun collectionUsesFirstFaceAndAbsoluteTableOffsets() {
        withFont(variableFont(collection = true)) {
            assertEquals(AppFontWeightAxis(250.5f, 450f, 850f), readAppFontWeightAxis(it))
        }
    }

    @Test
    fun emptyVariationTableUsesStaticFontMatching() {
        val bytes = variableFont(collection = false)
        ByteBuffer.wrap(bytes).putShort(88, 0).putShort(90, 0)
        withFont(bytes) { assertNull(readAppFontWeightAxis(it)) }
    }

    @Test
    fun incompleteAxesAreRejectedInsteadOfClaimingWeightSupport() {
        val bytes = variableFont(collection = false)
        // fvar 长度不足以容纳它声明的两个轴。
        ByteBuffer.wrap(bytes).putInt(24, 20)
        withFont(bytes) {
            assertFailsWith<IllegalArgumentException> { readAppFontWeightAxis(it) }
        }
    }

    /** 最小字体目录与 fvar 表，用于解析格式测试，不代替 Android 实际字体渲染验证。 */
    private fun variableFont(collection: Boolean): ByteArray {
        val buffer = ByteBuffer.allocate(160).order(ByteOrder.BIG_ENDIAN)
        val faceOffset = if (collection) 32 else 0
        if (collection) {
            buffer.putInt(0x74746366).putInt(0x00010000).putInt(1).putInt(faceOffset)
        }
        buffer.position(faceOffset)
        buffer.putInt(0x00010000).putShort(1).putShort(0).putShort(0).putShort(0)
        buffer.putInt(0x66766172).putInt(0).putInt(80).putInt(72)
        buffer.position(80)
        // 轴数组留出 8 字节空隙，每条记录多 4 字节，验证按表头偏移与步长读取。
        buffer.putShort(1).putShort(0).putShort(24).putShort(2)
            .putShort(2).putShort(24).putShort(0).putShort(12)
        buffer.position(104)
        buffer.putInt(0x77647468).putInt(75 * 65536).putInt(100 * 65536)
            .putInt(125 * 65536).putShort(0).putShort(256).putInt(0)
        buffer.putInt(0x77676874).putInt((250.5f * 65536).toInt()).putInt(450 * 65536)
            .putInt(850 * 65536).putShort(0).putShort(257)
        return buffer.array()
    }

    private fun withFont(bytes: ByteArray, block: (java.io.File) -> Unit) {
        val file = Files.createTempFile("font-weight-axis", ".ttf").toFile()
        try {
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }
}
