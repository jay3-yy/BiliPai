package com.android.purebilibili.feature.login

import com.google.zxing.BinaryBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** Decodes supported login and transfer QR payloads without opening scanned URLs. */
object BiliPaiQrDecoder {
    fun decode(bytes: ByteArray, width: Int, height: Int, rotation: Int = 0): String? {
        return decodeRaw(bytes, width, height, rotation)?.takeIf {
            it.startsWith("bilipai://transfer/") ||
                it.startsWith("bilipai://ext/") ||
                parseBilibiliLoginQr(it) != null
        }
    }

    /** Recognition is separate from validation, so unsupported QR codes can show feedback. */
    internal fun decodeRaw(bytes: ByteArray, width: Int, height: Int, rotation: Int = 0): String? {
        val frame = rotateQrLuminance(bytes, width, height, rotation) ?: return null
        val source = PlanarYUVLuminanceSource(frame.bytes, frame.width, frame.height, 0, 0, frame.width, frame.height, false)
        return decodeLuminance(source)
    }

    /** Decodes a QR code from an album image; when [acceptAny] it returns the raw text.
     *  Retries in four orientations as a fallback for missing EXIF rotation. */
    fun decodeBitmap(bitmap: android.graphics.Bitmap, acceptAny: Boolean = false): String? {
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            val oriented = if (rotation == 0) bitmap else android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true,
            )
            val text = try {
                val pixels = IntArray(oriented.width * oriented.height)
                oriented.getPixels(pixels, 0, oriented.width, 0, 0, oriented.width, oriented.height)
                decodeLuminance(com.google.zxing.RGBLuminanceSource(oriented.width, oriented.height, pixels))
            } finally {
                if (oriented !== bitmap) oriented.recycle()
            } ?: continue
            if (
                acceptAny ||
                text.startsWith("bilipai://transfer/") ||
                text.startsWith("bilipai://ext/") ||
                parseBilibiliLoginQr(text) != null
            ) {
                return text
            }
            // An unrelated result must not abort the remaining orientation attempts.
        }
        return null
    }

    private fun decodeLuminance(source: com.google.zxing.LuminanceSource): String? {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        return try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: ReaderException) {
            try {
                MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text
            } catch (_: ReaderException) {
                null
            }
        }
    }
}

internal fun extractTvAuthCode(raw: String): String? {
    return parseBilibiliLoginQr(raw)?.takeIf { it.type == BilibiliLoginQrType.TV }?.key
}

internal data class QrLuminanceFrame(val bytes: ByteArray, val width: Int, val height: Int)

/** CameraX supplies the clockwise rotation needed to display the sensor frame upright. */
internal fun rotateQrLuminance(bytes: ByteArray, width: Int, height: Int, rotation: Int): QrLuminanceFrame? {
    if (width <= 0 || height <= 0 || width.toLong() * height > bytes.size) return null
    if (rotation !in listOf(0, 90, 180, 270)) return null
    if (rotation == 0) return QrLuminanceFrame(bytes, width, height)
    val rotatedWidth = if (rotation == 180) width else height
    val rotatedHeight = if (rotation == 180) height else width
    val output = ByteArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        val index = when (rotation) {
            90 -> x * rotatedWidth + (height - 1 - y)
            180 -> (height - 1 - y) * width + (width - 1 - x)
            else -> (width - 1 - x) * rotatedWidth + y
        }
        output[index] = bytes[y * width + x]
    }
    return QrLuminanceFrame(output, rotatedWidth, rotatedHeight)
}

/** Power-of-two album sampling with no extra shrink once the image fits the decode budget. */
internal fun resolveQrAlbumSampleSize(width: Int, height: Int, maxDimension: Int): Int? {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return null
    val longest = maxOf(width, height).toLong()
    var sample = 1
    while ((longest + sample - 1) / sample > maxDimension) {
        if (sample > Int.MAX_VALUE / 2) return null
        sample *= 2
    }
    return sample
}
