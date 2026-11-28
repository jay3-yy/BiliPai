package com.android.purebilibili.feature.video.danmaku

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PathParser
import com.android.purebilibili.danmaku.engine.DanmakuMaskFrame
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

internal data class WebMaskChunkIndex(
    val startTimeMs: Long,
    val offset: Int,
    val endOffset: Int
)

internal data class WebMaskRawFrame(
    val timeMs: Long,
    val svg: String
)

internal object WebMaskParser {
    private const val HEADER_SIZE = 16
    private const val INDEX_ENTRY_SIZE = 16
    private const val FRAME_HEADER_SIZE = 12
    private const val MAX_CHUNK_COUNT = 20_000
    private const val MAX_INFLATED_CHUNK_BYTES = 16 * 1024 * 1024
    private const val DEFAULT_CHUNK_DURATION_MS = 10_000L
    private const val SVG_PREFIX = "data:image/svg+xml;base64,"
    private val SVG_PREFIX_BYTES = SVG_PREFIX.encodeToByteArray()
    private val RAW_SVG_PREFIX_LOWER = "<svg".encodeToByteArray()
    private val svgCompositing = Regex(
        """<(?:[\w.-]+:)?(?:clipPath|mask|style)\b|\b(?:clip-path|mask)\s*[:=]""",
        RegexOption.IGNORE_CASE,
    )
    private val svgRenderer by lazy {
        // WebMask 输入来自网络，不展开 DTD 实体。
        SVG.setInternalEntitiesEnabled(false)
        SVG::getFromString
    }

    fun parseIndex(data: ByteArray): List<WebMaskChunkIndex> {
        if (data.size < HEADER_SIZE || !data.copyOfRange(0, 4).contentEquals("MASK".encodeToByteArray())) {
            return emptyList()
        }
        val header = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        header.position(4)
        val version = header.int
        header.int // upstream vU / codec tag
        val count = header.int
        if (version != 1 || count !in 1..MAX_CHUNK_COUNT) return emptyList()
        val indexEnd = HEADER_SIZE.toLong() + count.toLong() * INDEX_ENTRY_SIZE
        if (indexEnd > data.size) return emptyList()

        val starts = LongArray(count)
        val offsets = IntArray(count)
        repeat(count) { index ->
            header.int // Reserved.
            val start = header.int.toLong()
            header.int // Reserved.
            val rawOffset = header.int.toLong()
            if (rawOffset !in indexEnd..data.size.toLong()) return emptyList()
            starts[index] = start.coerceAtLeast(0L)
            offsets[index] = rawOffset.toInt()
        }
        if (!offsets.asList().zipWithNext().all { (left, right) -> left < right }) return emptyList()

        return List(count) { index ->
            WebMaskChunkIndex(
                startTimeMs = starts[index],
                offset = offsets[index],
                endOffset = offsets.getOrNull(index + 1) ?: data.size
            )
        }
    }

    /**
     * 解析单个 GZIP 解压后的分块帧流。
     * 每帧由 12 字节大端头（4B dataLength + 4B reserved + 4B timeMs）后接 dataLength 字节 UTF-8 SVG payload 组成。
     */
    internal fun parseChunkFrames(inflated: ByteArray): List<WebMaskRawFrame> {
        if (inflated.size <= FRAME_HEADER_SIZE) return emptyList()
        val buffer = ByteBuffer.wrap(inflated).order(ByteOrder.BIG_ENDIAN)
        val frames = ArrayList<WebMaskRawFrame>()
        var cursor = 0
        while (cursor + FRAME_HEADER_SIZE <= inflated.size) {
            buffer.position(cursor)
            val dataLength = buffer.int
            buffer.int // Reserved.
            val timeMs = buffer.int.toLong()
            val payloadStart = cursor + FRAME_HEADER_SIZE
            val payloadEnd = payloadStart.toLong() + dataLength.toLong()
            if (dataLength <= 0 || payloadEnd > inflated.size) break
            decodeSvgPayloadBytes(inflated, payloadStart, payloadEnd.toInt())?.let { svg ->
                frames += WebMaskRawFrame(
                    timeMs = timeMs.coerceAtLeast(0L),
                    svg = svg
                )
            }
            cursor = payloadEnd.toInt()
        }
        return frames
    }

    fun parseWindow(
        data: ByteArray,
        fps: Int,
        windowStartMs: Long,
        windowEndMs: Long,
        preParsedIndex: List<WebMaskChunkIndex>? = null,
        chunkCache: MutableMap<Int, List<DanmakuMaskFrame>>? = null,
    ): List<DanmakuMaskFrame> {
        val index = preParsedIndex ?: parseIndex(data)
        if (index.isEmpty()) return emptyList()
        val safeFps = fps.coerceIn(1, 60)
        val fallbackFrameDurationMs = (1_000L / safeFps).coerceAtLeast(1L)
        val safeStart = windowStartMs.coerceAtLeast(0L)
        val safeEnd = windowEndMs.coerceAtLeast(safeStart + 1L)
        val frames = ArrayList<DanmakuMaskFrame>()

        index.forEachIndexed chunkLoop@ { chunkIndex, chunk ->
            val chunkEnd = index.getOrNull(chunkIndex + 1)?.startTimeMs
                ?.takeIf { it > chunk.startTimeMs }
                ?: (chunk.startTimeMs + DEFAULT_CHUNK_DURATION_MS)
            if (chunkEnd < safeStart || chunk.startTimeMs > safeEnd) return@chunkLoop

            val chunkFrames = chunkCache?.get(chunk.offset) ?: run {
                val inflated = inflateBounded(data, chunk.offset, chunk.endOffset) ?: return@chunkLoop
                val rawFrames = parseChunkFrames(inflated)
                if (rawFrames.isEmpty()) return@chunkLoop
                ArrayList<DanmakuMaskFrame>(rawFrames.size).apply {
                    rawFrames.forEachIndexed { frameIndex, rawFrame ->
                        val frameStart = rawFrame.timeMs
                        val nextStart = rawFrames.getOrNull(frameIndex + 1)?.timeMs
                        val frameEnd = when {
                            nextStart != null && nextStart > frameStart -> nextStart
                            chunkEnd > frameStart -> chunkEnd
                            else -> frameStart + fallbackFrameDurationMs
                        }
                        val frame = parseMaskFrame(rawFrame.svg, frameStart, frameEnd)
                        if (frame != null) add(frame)
                    }
                }.also { decoded ->
                    chunkCache?.put(chunk.offset, decoded)
                }
            }

            for (i in 0 until chunkFrames.size) {
                val frame = chunkFrames[i]
                if (frame.endTimeMs >= safeStart && frame.startTimeMs <= safeEnd) {
                    frames += frame
                }
            }
        }
        return frames
    }

    private fun parseMaskFrame(svg: String, startTimeMs: Long, endTimeMs: Long): DanmakuMaskFrame? {
        if (svgCompositing.containsMatchIn(svg)) {
            val document = try {
                svgRenderer(svg)
            } catch (_: SVGParseException) {
                return null
            }
            val viewBox = document.documentViewBox
            val width = svgViewportSize(document.documentWidth, viewBox?.width(), 1920)
            val height = svgViewportSize(document.documentHeight, viewBox?.height(), 1080)
            return DanmakuMaskFrame(
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                path = null,
                sourceWidth = width,
                sourceHeight = height,
                visibilityPicture = document.renderToPicture(width, height),
            )
        }
        val parsed = parseSvgPath(svg) ?: return null
        return DanmakuMaskFrame(startTimeMs, endTimeMs, parsed.path, parsed.width, parsed.height)
    }

    private fun svgViewportSize(length: Float, viewBoxLength: Float?, fallback: Int): Int =
        (length.takeIf { it.isFinite() && it > 0f }
            ?: viewBoxLength?.takeIf { it.isFinite() && it > 0f })
            ?.roundToInt()?.coerceAtLeast(1) ?: fallback

    private data class ParsedSvgPath(val path: Path, val width: Int, val height: Int)

    private fun parseSvgPath(svg: String): ParsedSvgPath? {
        val geometry = parseWebMaskSvg(svg) ?: return null
        val combined = Path()
        val matrix = Matrix()
        val element = Path()
        for (shape in geometry.shapes) {
            element.rewind()
            when (val outline = shape.outline) {
                is WebMaskOutline.PathData ->
                    element.set(PathParser.createPathFromPathData(outline.value) ?: continue)
                is WebMaskOutline.Rect ->
                    element.addRect(outline.x, outline.y, outline.x + outline.width, outline.y + outline.height, Path.Direction.CW)
                is WebMaskOutline.Ellipse ->
                    element.addOval(outline.cx - outline.rx, outline.cy - outline.ry, outline.cx + outline.rx, outline.cy + outline.ry, Path.Direction.CW)
                is WebMaskOutline.Polygon -> {
                    element.moveTo(outline.points[0], outline.points[1])
                    for (i in 2 until outline.points.size step 2) {
                        element.lineTo(outline.points[i], outline.points[i + 1])
                    }
                    element.close()
                }
            }
            element.fillType = if (shape.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
            if (shape.transform != WebMaskTransform()) {
                with(shape.transform) {
                    matrix.setValues(floatArrayOf(a, c, x, b, d, y, 0f, 0f, 1f))
                }
                element.transform(matrix)
            }
            if (combined.isEmpty) combined.set(element)
            else if (!combined.op(element, Path.Op.UNION)) return null
        }
        // B 站 .webmask SVG 按 Web CSS mask-image 语义生成：实心黑底=显示弹幕，镂空=人物遮挡区。
        // 引擎使用 PorterDuff.Mode.DST_OUT（实心=挖空弹幕），需用有效画面外接框减去 SVG 路径还原人物遮挡区。
        val maskBounds = RectF()
        @Suppress("DEPRECATION")
        combined.computeBounds(maskBounds, true)
        val left = maskBounds.left.coerceIn(0f, geometry.width.toFloat())
        val top = maskBounds.top.coerceIn(0f, geometry.height.toFloat())
        val right = maskBounds.right.coerceIn(left, geometry.width.toFloat())
        val bottom = maskBounds.bottom.coerceIn(top, geometry.height.toFloat())
        if (right <= left || bottom <= top) return null
        val viewportRectPath = Path().apply {
            addRect(left, top, right, bottom, Path.Direction.CW)
        }
        val occlusionPath = Path()
        if (!occlusionPath.op(viewportRectPath, combined, Path.Op.DIFFERENCE) || occlusionPath.isEmpty) {
            return null
        }
        return ParsedSvgPath(occlusionPath, geometry.width, geometry.height)
    }

    private fun decodeSvgPayloadBytes(data: ByteArray, start: Int, end: Int): String? {
        var pos = start
        while (pos < end && data[pos] <= ' '.code.toByte()) pos++
        if (pos >= end) return null
        if (startsWithIgnoreAsciiCase(data, pos, end, RAW_SVG_PREFIX_LOWER)) {
            return String(data, pos, end - pos, Charsets.UTF_8)
        }
        val prefixPos = indexOfBytes(data, pos, end, SVG_PREFIX_BYTES)
        if (prefixPos < 0) return null
        val b64Start = prefixPos + SVG_PREFIX_BYTES.size
        var b64End = b64Start
        while (b64End < end && isBase64Byte(data[b64End])) {
            b64End++
        }
        if (b64End <= b64Start) return null
        return try {
            val decoded = Base64.getDecoder().decode(data.copyOfRange(b64Start, b64End))
            String(decoded, Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun startsWithIgnoreAsciiCase(data: ByteArray, start: Int, end: Int, lowerPattern: ByteArray): Boolean {
        if (end - start < lowerPattern.size) return false
        for (i in lowerPattern.indices) {
            val b = data[start + i].toInt()
            val lower = if (b in 'A'.code..'Z'.code) b + 32 else b
            if (lower != lowerPattern[i].toInt()) return false
        }
        return true
    }

    private fun indexOfBytes(data: ByteArray, start: Int, end: Int, pattern: ByteArray): Int {
        val lastStart = end - pattern.size
        if (lastStart < start) return -1
        val first = pattern[0]
        var i = start
        while (i <= lastStart) {
            if (data[i] == first) {
                var j = 1
                while (j < pattern.size && data[i + j] == pattern[j]) j++
                if (j == pattern.size) return i
            }
            i++
        }
        return -1
    }

    private fun isBase64Byte(b: Byte): Boolean {
        val c = b.toInt()
        return (c in 'A'.code..'Z'.code) ||
            (c in 'a'.code..'z'.code) ||
            (c in '0'.code..'9'.code) ||
            c == '+'.code || c == '/'.code || c == '='.code
    }

    private fun inflateBounded(data: ByteArray, offset: Int, endOffset: Int): ByteArray? = try {
        GZIPInputStream(ByteArrayInputStream(data, offset, endOffset - offset)).use { gzip ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = gzip.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_INFLATED_CHUNK_BYTES) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    } catch (_: Exception) {
        null
    }
}
