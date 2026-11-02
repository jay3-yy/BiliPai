package com.android.purebilibili.core.theme

import java.io.File
import java.io.RandomAccessFile

internal data class AppFontWeightAxis(val min: Float, val default: Float, val max: Float)

/** 读取首个字体的可变字重轴；TTC 与现有字体导入一样使用第 0 个字体。 */
internal fun readAppFontWeightAxis(file: File): AppFontWeightAxis? =
    RandomAccessFile(file, "r").use { input ->
        val fontOffset = if (input.readInt() == 0x74746366) { // ttcf
            input.seek(12L)
            input.readInt().toLong() and 0xffffffffL
        } else 0L
        input.seek(fontOffset + 4L)
        val tableCount = input.readUnsignedShort()
        for (index in 0 until tableCount) {
            input.seek(fontOffset + 12L + index * 16L)
            val tag = input.readInt()
            input.readInt() // checksum
            val tableOffset = input.readInt().toLong() and 0xffffffffL
            if (tag != 0x66766172) continue // fvar
            val tableLength = input.readInt().toLong() and 0xffffffffL
            input.seek(tableOffset + 4L)
            val axesOffset = input.readUnsignedShort()
            input.readUnsignedShort() // reserved
            val axisCount = input.readUnsignedShort()
            val axisSize = input.readUnsignedShort()
            if (axisCount == 0) return@use null
            require(axisSize >= 20 && axesOffset + axisCount.toLong() * axisSize <= tableLength) {
                "字体变化轴数据不完整"
            }
            for (axis in 0 until axisCount) {
                input.seek(tableOffset + axesOffset + axis.toLong() * axisSize)
                if (input.readInt() != 0x77676874) continue // wght
                val min = input.readInt() / 65536f
                val default = input.readInt() / 65536f
                val max = input.readInt() / 65536f
                require(min in 1f..1000f && default in min..max && max <= 1000f) {
                    "字体字重范围无效"
                }
                return@use AppFontWeightAxis(min, default, max)
            }
            return@use null
        }
        null
    }
