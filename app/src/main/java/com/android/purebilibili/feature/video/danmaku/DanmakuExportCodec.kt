package com.android.purebilibili.feature.video.danmaku

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_BOTTOM
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_REVERSE
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_SCROLL
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_TOP
import com.android.purebilibili.danmaku.engine.DanmakuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

enum class DanmakuExportFormat(val extension: String, val mimeType: String, val label: String) {
    XML("xml", "application/xml", "XML"),
    ASS("ass", "text/plain", "ASS"),
}

internal fun encodeDanmakuToBilibiliXml(
    cid: Long,
    items: List<DanmakuItem>,
): String = buildString(items.size * 64 + 256) {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<i>\n")
    append("  <chatserver>chat.bilibili.com</chatserver>\n")
    append("  <chatid>").append(cid).append("</chatid>\n")
    append("  <mission>0</mission>\n")
    append("  <maxlimit>").append(items.size).append("</maxlimit>\n")
    append("  <state>0</state>\n")
    append("  <real_name>0</real_name>\n")
    append("  <source>k-v</source>\n")
    for (item in items.sortedBy { it.showAtTime }) {
        val rawText = (item.mergedOriginalText ?: item.text).orEmpty()
        if (rawText.isBlank()) continue
        val timeSec = String.format(Locale.US, "%.3f", item.showAtTime.coerceAtLeast(0L) / 1000.0)
        val mode = when (item.layerType) {
            DANMAKU_LAYER_BOTTOM -> 4
            DANMAKU_LAYER_TOP -> 5
            DANMAKU_LAYER_REVERSE -> 6
            else -> 1
        }
        val fontSize = (25f * item.textSizeScale.coerceIn(0.5f, 2.5f)).roundToInt()
        val rgb24 = (item.textColor ?: 0xFFFFFF) and 0xFFFFFF
        val userHash = item.userHash.ifBlank { "0" }
        append("  <d p=\"")
            .append(timeSec).append(',')
            .append(mode).append(',')
            .append(fontSize).append(',')
            .append(rgb24).append(",0,")
            .append(item.pool).append(',')
            .append(escapeXml(userHash)).append(',')
            .append(item.danmakuId)
            .append("\">")
            .append(escapeXml(rawText))
            .append("</d>\n")
    }
    append("</i>\n")
}

internal fun encodeDanmakuToAss(
    title: String,
    items: List<DanmakuItem>,
    width: Int = 1920,
    height: Int = 1080,
): String {
    val sorted = items.sortedBy { it.showAtTime }
    val baseFontSize = 46
    val lineHeight = 54
    val maxTrackCount = (height / 2 / lineHeight).coerceAtLeast(1)
    val scrollTrackEndTimes = LongArray(maxTrackCount)
    val topTrackEndTimes = LongArray(maxTrackCount)
    val bottomTrackEndTimes = LongArray(maxTrackCount)

    return buildString(sorted.size * 96 + 512) {
        append("[Script Info]\n")
        append("Title: ").append(title.replace('\n', ' ')).append('\n')
        append("ScriptType: v4.00+\n")
        append("PlayResX: ").append(width).append('\n')
        append("PlayResY: ").append(height).append('\n')
        append("ScaledBorderAndShadow: yes\n\n")

        append("[V4+ Styles]\n")
        append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        append("Style: Default,Sans,").append(baseFontSize)
            .append(",&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,2,20,20,20,1\n\n")

        append("[Events]\n")
        append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")

        for (item in sorted) {
            val rawText = (item.mergedOriginalText ?: item.text).orEmpty()
            if (rawText.isBlank()) continue
            val startMs = item.showAtTime.coerceAtLeast(0L)
            val durationMs = when (item.layerType) {
                DANMAKU_LAYER_TOP, DANMAKU_LAYER_BOTTOM -> 4_000L
                else -> 8_000L
            }
            val endMs = startMs + durationMs
            val rgb24 = (item.textColor ?: 0xFFFFFF) and 0xFFFFFF
            val colorTag = if (rgb24 != 0xFFFFFF) {
                val r = (rgb24 shr 16) and 0xFF
                val g = (rgb24 shr 8) and 0xFF
                val b = rgb24 and 0xFF
                String.format(Locale.US, "\\c&H%02X%02X%02X&", b, g, r)
            } else {
                ""
            }
            val escaped = escapeAss(rawText)
            val overrideTag = when (item.layerType) {
                DANMAKU_LAYER_TOP -> {
                    val track = allocateAssTrack(topTrackEndTimes, startMs, endMs)
                    val y = 24 + track * lineHeight
                    "{\\an8\\pos(${width / 2},$y)$colorTag}"
                }
                DANMAKU_LAYER_BOTTOM -> {
                    val track = allocateAssTrack(bottomTrackEndTimes, startMs, endMs)
                    val y = height - 24 - track * lineHeight
                    "{\\an2\\pos(${width / 2},$y)$colorTag}"
                }
                DANMAKU_LAYER_REVERSE -> {
                    val track = allocateAssTrack(scrollTrackEndTimes, startMs, endMs)
                    val y = 24 + track * lineHeight
                    val estWidth = (rawText.length * baseFontSize * 0.65f).roundToInt().coerceAtLeast(baseFontSize)
                    "{\\an7\\move(${-estWidth},$y,$width,$y)$colorTag}"
                }
                else -> {
                    val track = allocateAssTrack(scrollTrackEndTimes, startMs, endMs)
                    val y = 24 + track * lineHeight
                    val estWidth = (rawText.length * baseFontSize * 0.65f).roundToInt().coerceAtLeast(baseFontSize)
                    "{\\an7\\move($width,$y,${-estWidth},$y)$colorTag}"
                }
            }
            append("Dialogue: 0,")
                .append(formatAssTimestamp(startMs)).append(',')
                .append(formatAssTimestamp(endMs))
                .append(",Default,,0,0,0,,")
                .append(overrideTag)
                .append(escaped)
                .append('\n')
        }
    }
}

internal suspend fun exportDanmakuToDownloads(
    context: Context,
    fileNameBase: String,
    format: DanmakuExportFormat,
    content: String,
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val safeBase = fileNameBase
            .replace(Regex("""[\\/:*?"<>|\r\n]+"""), "_")
            .trim()
            .take(64)
            .ifBlank { "danmaku" }
        val fileName = "$safeBase.${format.extension}"
        val bytes = content.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, format.mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BiliPai/Danmaku")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("无法写入下载文件")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Downloads/BiliPai/Danmaku/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BiliPai/Danmaku"
            ).apply { mkdirs() }
            val outFile = File(dir, fileName)
            outFile.writeBytes(bytes)
            outFile.absolutePath
        }
    }
}

private fun allocateAssTrack(endTimes: LongArray, startMs: Long, endMs: Long): Int {
    for (i in endTimes.indices) {
        if (endTimes[i] <= startMs) {
            endTimes[i] = endMs
            return i
        }
    }
    var earliestIndex = 0
    for (i in 1 until endTimes.size) {
        if (endTimes[i] < endTimes[earliestIndex]) earliestIndex = i
    }
    endTimes[earliestIndex] = endMs
    return earliestIndex
}

private fun formatAssTimestamp(ms: Long): String {
    val totalCentis = (ms.coerceAtLeast(0L) / 10L)
    val centis = totalCentis % 100
    val totalSec = totalCentis / 100
    val sec = totalSec % 60
    val totalMin = totalSec / 60
    val min = totalMin % 60
    val hour = totalMin / 60
    return String.format(Locale.US, "%d:%02d:%02d.%02d", hour, min, sec, centis)
}

private fun escapeXml(text: String): String = buildString(text.length) {
    for (ch in text) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) append(ch)
        }
    }
}

private fun escapeAss(text: String): String = text
    .replace("\\", "\\\\")
    .replace("{", "\\{")
    .replace("}", "\\}")
    .replace("\r\n", "\\N")
    .replace("\n", "\\N")
    .replace("\r", "\\N")
