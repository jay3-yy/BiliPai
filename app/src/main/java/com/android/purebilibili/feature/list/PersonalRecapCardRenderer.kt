package com.android.purebilibili.feature.list

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.personal.CompletionKeepsakeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val RECAP_CARD_TAG = "PersonalRecapCard"
private const val RECAP_CARD_WIDTH_PX = 1080
private const val RECAP_RELATIVE_PATH = "Pictures/BiliPai/Recap"
private val RECAP_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val RECAP_FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

enum class PersonalRecapCardTheme(val label: String) {
    NIGHT_GLOW("暗夜流光"),
    WARM_PAPER("晨光相纸"),
}

data class PersonalRecapCardPayload(
    val windowLabel: String,
    val videoCount: Int,
    val totalDurationText: String,
    val completionPercent: Int?,
    val rssReadCount: Int,
    val topUps: List<String>,
    val keepsakeQuote: String?,
    val generatedAtMillis: Long = System.currentTimeMillis(),
)

internal fun buildPersonalRecapCardPayload(
    window: PersonalRecapWindow,
    videoStats: PersonalVideoRecapStats?,
    rssStats: PersonalRssRecapStats,
    keepsakes: List<CompletionKeepsakeRecord>,
    nowMs: Long = System.currentTimeMillis(),
): PersonalRecapCardPayload {
    val videos = videoStats?.videoCount ?: 0
    val durationText = videoStats?.totalDurationSec?.takeIf { it > 0L }?.let(::formatRecapDuration) ?: "0 分钟"
    val completionPct = if (videoStats != null && videoStats.videoCount > 0) {
        ((videoStats.finishedCount * 100f) / videoStats.videoCount.toFloat()).roundToInt().coerceIn(0, 100)
    } else {
        null
    }
    val topUps = videoStats?.topUps.orEmpty()
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .take(3)
    val featuredQuote = keepsakes
        .firstOrNull { it.reflection.isNotBlank() }
        ?.let { "「${it.reflection.trim().take(60)}」——《${it.title.take(20)}》" }
    return PersonalRecapCardPayload(
        windowLabel = window.label,
        videoCount = videos,
        totalDurationText = durationText,
        completionPercent = completionPct,
        rssReadCount = rssStats.readCount.coerceAtLeast(0),
        topUps = topUps,
        keepsakeQuote = featuredQuote,
        generatedAtMillis = nowMs,
    )
}

internal suspend fun renderPersonalRecapCardBitmap(
    payload: PersonalRecapCardPayload,
    theme: PersonalRecapCardTheme,
): Bitmap = withContext(Dispatchers.Default) {
    val width = RECAP_CARD_WIDTH_PX
    val pad = 64f
    val cardW = width - pad * 2
    val isDark = theme == PersonalRecapCardTheme.NIGHT_GLOW

    val primaryTextColor = if (isDark) Color.WHITE else Color.parseColor("#1D1F24")
    val secondaryTextColor = if (isDark) Color.argb(185, 255, 255, 255) else Color.parseColor("#5E636E")
    val accentColor = if (isDark) Color.parseColor("#8AB4F8") else Color.parseColor("#3B6EF5")
    val surfaceColor = if (isDark) Color.argb(34, 255, 255, 255) else Color.parseColor("#FFFFFF")
    val borderColor = if (isDark) Color.argb(66, 255, 255, 255) else Color.parseColor("#E3DDD3")

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        textSize = 52f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textSize = 28f
    }
    val metricValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        textSize = 48f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val metricLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textSize = 24f
    }
    val sectionLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 25f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        textSize = 30f
    }
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textSize = 23f
    }

    val topUpsText = if (payload.topUps.isNotEmpty()) payload.topUps.joinToString("  ·  ") else ""
    val topUpsLayout = if (topUpsText.isNotEmpty()) {
        buildRecapStaticLayout(topUpsText, bodyPaint, (cardW - 56f).roundToInt())
    } else null
    val quoteLayout = payload.keepsakeQuote?.let {
        buildRecapStaticLayout(it, bodyPaint, (cardW - 56f).roundToInt())
    }

    var totalH = 110f + 92f + 220f + 44f
    if (payload.completionPercent != null) totalH += 126f
    if (topUpsLayout != null) totalH += 96f + topUpsLayout.height + 28f
    if (quoteLayout != null) totalH += 96f + quoteLayout.height + 28f
    totalH += 116f

    val output = Bitmap.createBitmap(width, totalH.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = if (isDark) {
            LinearGradient(
                0f, 0f, width.toFloat(), totalH,
                Color.parseColor("#131726"),
                Color.parseColor("#1E2438"),
                Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                0f, 0f, width.toFloat(), totalH,
                Color.parseColor("#F5F0E6"),
                Color.parseColor("#EFE9DD"),
                Shader.TileMode.CLAMP,
            )
        }
    }
    canvas.drawRect(0f, 0f, width.toFloat(), totalH, bgPaint)

    var cursorY = 82f
    canvas.drawText("BiliPai · ${payload.windowLabel}时光档案", pad, cursorY, titlePaint)
    cursorY += 46f
    val dateStr = RECAP_DATE_FORMAT.format(
        Instant.ofEpochMilli(payload.generatedAtMillis).atZone(ZoneId.systemDefault())
    )
    canvas.drawText("记录于 $dateStr · 仅本地生成", pad, cursorY, subtitlePaint)
    cursorY += 48f

    // 核心数据双卡
    val tileGap = 24f
    val tileW = (cardW - tileGap) / 2f
    val tileH = 196f
    drawRecapGlassCard(canvas, RectF(pad, cursorY, pad + tileW, cursorY + tileH), surfaceColor, borderColor)
    canvas.drawText("${payload.videoCount}", pad + 30f, cursorY + 86f, metricValuePaint)
    canvas.drawText("观看视频 · ${payload.totalDurationText}", pad + 30f, cursorY + 144f, metricLabelPaint)

    val rightX = pad + tileW + tileGap
    drawRecapGlassCard(canvas, RectF(rightX, cursorY, rightX + tileW, cursorY + tileH), surfaceColor, borderColor)
    canvas.drawText("${payload.rssReadCount}", rightX + 30f, cursorY + 86f, metricValuePaint)
    canvas.drawText("已读文章 / 订阅", rightX + 30f, cursorY + 144f, metricLabelPaint)
    cursorY += tileH + 28f

    // 完播率进度卡
    payload.completionPercent?.let { pct ->
        val barCardH = 104f
        drawRecapGlassCard(canvas, RectF(pad, cursorY, pad + cardW, cursorY + barCardH), surfaceColor, borderColor)
        canvas.drawText("视频完播比例", pad + 28f, cursorY + 46f, sectionLabelPaint)
        val pctLabel = "$pct%"
        val pctWidth = metricLabelPaint.measureText(pctLabel)
        canvas.drawText(pctLabel, pad + cardW - 28f - pctWidth, cursorY + 46f, metricLabelPaint)

        val trackRect = RectF(pad + 28f, cursorY + 68f, pad + cardW - 28f, cursorY + 82f)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.argb(46, 255, 255, 255) else Color.parseColor("#E5E0D5")
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(trackRect, 7f, 7f, trackPaint)
        val fillW = (trackRect.width() * (pct / 100f)).coerceAtLeast(12f)
        canvas.drawRoundRect(RectF(trackRect.left, trackRect.top, trackRect.left + fillW, trackRect.bottom), 7f, 7f, fillPaint)
        cursorY += barCardH + 26f
    }

    // 常看 UP 主
    topUpsLayout?.let { layout ->
        val boxH = 82f + layout.height
        drawRecapGlassCard(canvas, RectF(pad, cursorY, pad + cardW, cursorY + boxH), surfaceColor, borderColor)
        canvas.drawText("最常陪伴的 UP 主", pad + 28f, cursorY + 44f, sectionLabelPaint)
        canvas.save()
        canvas.translate(pad + 28f, cursorY + 60f)
        layout.draw(canvas)
        canvas.restore()
        cursorY += boxH + 26f
    }

    // 完结纪念摘录
    quoteLayout?.let { layout ->
        val boxH = 82f + layout.height
        drawRecapGlassCard(canvas, RectF(pad, cursorY, pad + cardW, cursorY + boxH), surfaceColor, borderColor)
        canvas.drawText("完结纪念摘录", pad + 28f, cursorY + 44f, sectionLabelPaint)
        canvas.save()
        canvas.translate(pad + 28f, cursorY + 60f)
        layout.draw(canvas)
        canvas.restore()
        cursorY += boxH + 26f
    }

    // 页脚
    canvas.drawText("BiliPai · 纯净观影与阅读时光", pad, totalH - 42f, footerPaint)
    output
}

internal suspend fun savePersonalRecapCardToGallery(
    context: Context,
    bitmap: Bitmap,
    timestampMs: Long = System.currentTimeMillis(),
): Uri? = withContext(Dispatchers.IO) {
    val timePart = RECAP_FILE_TIME_FORMAT.format(
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    )
    val fileName = "BiliPai_Recap_$timePart.png"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, RECAP_RELATIVE_PATH)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
    try {
        val wrote = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        if (!wrote) {
            resolver.delete(uri, null, null)
            return@withContext null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    } catch (error: Exception) {
        Logger.e(RECAP_CARD_TAG, "Failed to save recap card", error)
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

internal suspend fun writePersonalRecapShareFile(
    context: Context,
    bitmap: Bitmap,
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("recap_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(4)
            ?.forEach { it.delete() }
        val file = File(dir, "recap_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.onFailure {
        Logger.e(RECAP_CARD_TAG, "Failed to write recap share file", it)
    }.getOrNull()
}

private fun drawRecapGlassCard(canvas: Canvas, rect: RectF, fillColor: Int, strokeColor: Int) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = strokeColor
    }
    canvas.drawRoundRect(rect, 28f, 28f, fill)
    canvas.drawRoundRect(rect, 28f, 28f, stroke)
}

private fun buildRecapStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
    StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(6f, 1.08f)
        .setIncludePad(false)
        .build()
