package com.android.purebilibili.feature.personal

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val KEEPSAKE_CARD_TAG = "CompletionKeepsakeCard"
private val KEEPSAKE_CARD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.getDefault())
private val KEEPSAKE_FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
private val KEEPSAKE_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/BiliPai"

enum class CompletionKeepsakeCardTheme(val label: String) {
    WARM_PAPER("暖纸纪念"),
    NIGHT_FILM("夜幕胶片"),
}

data class CompletionKeepsakeCardPayload(
    val badgeLabel: String,
    val title: String,
    val episodeSummary: String,
    val completedDate: String,
    val reflectionText: String,
    val coverUrl: String,
)

internal fun buildCompletionKeepsakeCardPayload(
    record: CompletionKeepsakeRecord,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CompletionKeepsakeCardPayload {
    val completedDate = KEEPSAKE_CARD_DATE_FORMAT.format(
        Instant.ofEpochMilli(record.completedAtMs).atZone(zoneId)
    )
    return CompletionKeepsakeCardPayload(
        badgeLabel = record.kind.badgeLabel,
        title = record.title.trim().ifBlank { "完结纪念" },
        episodeSummary = "共 ${record.episodeCount.coerceAtLeast(1)} 集",
        completedDate = completedDate,
        reflectionText = record.reflection.trim().ifBlank { "这一程已经认真看完，留下一枚完结印记。" },
        coverUrl = record.coverUrl.trim(),
    )
}

internal suspend fun renderCompletionKeepsakeCardBitmap(
    context: Context,
    payload: CompletionKeepsakeCardPayload,
    theme: CompletionKeepsakeCardTheme,
): Bitmap {
    val coverBitmap = loadKeepsakeCoverBitmap(context, payload.coverUrl)
    return withContext(Dispatchers.Default) {
        val isDark = theme == CompletionKeepsakeCardTheme.NIGHT_FILM
        val cardW = 1080
        val pad = 72f
        val contentW = (cardW - pad * 2).toInt()
        val coverH = if (coverBitmap != null) 540 else 0

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#FF9EC0") else Color.parseColor("#D84A7F")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#9BA4B5") else Color.parseColor("#7D7168")
            textSize = 30f
            textAlign = Paint.Align.RIGHT
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#F5F7FA") else Color.parseColor("#241E1A")
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val reflectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#E7ECF4") else Color.parseColor("#3B322C")
            textSize = 38f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#8A93A5") else Color.parseColor("#8D8176")
            textSize = 28f
        }

        val titleLayout = buildKeepsakeStaticLayout(payload.title, titlePaint, contentW, maxLines = 2)
        val reflectionLayout = buildKeepsakeStaticLayout(
            "“${payload.reflectionText}”",
            reflectionPaint,
            contentW - 76,
            maxLines = 6,
        )

        val topHeaderH = 128
        val coverBlockH = if (coverH > 0) coverH + 44 else 0
        val titleBlockH = titleLayout.height + 40
        val reflectionCardH = reflectionLayout.height + 76
        val footerBlockH = 112
        val totalH = (topHeaderH + coverBlockH + titleBlockH + reflectionCardH + footerBlockH).coerceAtLeast(780)

        val output = Bitmap.createBitmap(cardW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, cardW.toFloat(), totalH.toFloat(),
                if (isDark) Color.parseColor("#11141C") else Color.parseColor("#FCF8F2"),
                if (isDark) Color.parseColor("#1B1726") else Color.parseColor("#F3EADF"),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, cardW.toFloat(), totalH.toFloat(), bgPaint)

        // 顶部柔和装饰光斑
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#24FF9EC0") else Color.parseColor("#1CE66494")
        }
        canvas.drawCircle(cardW - 120f, 96f, 210f, glowPaint)

        // 顶部标签与完结日期
        canvas.drawText("${payload.badgeLabel} · ${payload.episodeSummary}", pad, 88f, badgePaint)
        canvas.drawText(payload.completedDate, cardW - pad, 88f, datePaint)

        var y = topHeaderH.toFloat()

        // 作品封面
        if (coverBitmap != null && !coverBitmap.isRecycled) {
            val coverRect = RectF(pad, y, cardW - pad, y + coverH)
            val clipPath = Path().apply { addRoundRect(coverRect, 32f, 32f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            drawCenterCropBitmap(canvas, coverBitmap, coverRect)
            canvas.restore()

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = if (isDark) Color.parseColor("#2AFFFFFF") else Color.parseColor("#1A000000")
            }
            canvas.drawRoundRect(coverRect, 32f, 32f, borderPaint)
            y += coverH + 44f
        }

        // 作品标题
        canvas.save()
        canvas.translate(pad, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 40f

        // 完结短评卡片
        val quoteRect = RectF(pad, y, cardW - pad, y + reflectionCardH)
        val quoteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#1FFFFFFF") else Color.parseColor("#CCFFFFFF")
        }
        val quoteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = if (isDark) Color.parseColor("#26FFFFFF") else Color.parseColor("#1C9C7E66")
        }
        canvas.drawRoundRect(quoteRect, 28f, 28f, quoteFill)
        canvas.drawRoundRect(quoteRect, 28f, 28f, quoteStroke)

        val accentBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#FF9EC0") else Color.parseColor("#D84A7F")
        }
        canvas.drawRoundRect(
            RectF(pad + 26f, y + 34f, pad + 34f, y + reflectionCardH - 34f),
            4f,
            4f,
            accentBar,
        )

        canvas.save()
        canvas.translate(pad + 54f, y + 38f)
        reflectionLayout.draw(canvas)
        canvas.restore()

        // 页脚印记
        canvas.drawText("BiliPai · 完结纪念", pad, totalH - 44f, footerPaint)
        output
    }
}

internal suspend fun saveCompletionKeepsakeCardToGallery(
    context: Context,
    bitmap: Bitmap,
    timestampMs: Long = System.currentTimeMillis(),
): Uri? = withContext(Dispatchers.IO) {
    val timePart = KEEPSAKE_FILE_TIME_FORMAT.format(
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    )
    val fileName = "BiliPai_Keepsake_$timePart.png"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, KEEPSAKE_RELATIVE_PATH)
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
    } catch (cancelled: CancellationException) {
        runCatching { resolver.delete(uri, null, null) }
        throw cancelled
    } catch (error: Exception) {
        Logger.e(KEEPSAKE_CARD_TAG, "Failed to save keepsake card", error)
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

internal suspend fun writeCompletionKeepsakeShareFile(
    context: Context,
    bitmap: Bitmap,
): Uri? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("keepsake_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(4)
            ?.forEach { it.delete() }
        val file = File(dir, "keepsake_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.e(KEEPSAKE_CARD_TAG, "Failed to write keepsake share file", error)
        null
    }
}

private suspend fun loadKeepsakeCoverBitmap(context: Context, coverUrl: String): Bitmap? {
    if (coverUrl.isBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(1080, 675)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            (result as? SuccessResult)?.image?.toBitmap()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.w(KEEPSAKE_CARD_TAG, "Unable to load keepsake cover", error)
            null
        }
    }
}

private fun drawCenterCropBitmap(canvas: Canvas, source: Bitmap, dest: RectF) {
    val srcW = source.width.toFloat().coerceAtLeast(1f)
    val srcH = source.height.toFloat().coerceAtLeast(1f)
    val scale = maxOf(dest.width() / srcW, dest.height() / srcH)
    val scaledW = dest.width() / scale
    val scaledH = dest.height() / scale
    val left = ((srcW - scaledW) / 2f).toInt().coerceAtLeast(0)
    val top = ((srcH - scaledH) / 2f).toInt().coerceAtLeast(0)
    val srcRect = Rect(
        left,
        top,
        (left + scaledW.toInt()).coerceAtMost(source.width),
        (top + scaledH.toInt()).coerceAtMost(source.height),
    )
    canvas.drawBitmap(source, srcRect, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
}

private fun buildKeepsakeStaticLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    maxLines: Int,
): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(10f, 1f)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
