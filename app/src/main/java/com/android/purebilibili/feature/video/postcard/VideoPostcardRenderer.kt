package com.android.purebilibili.feature.video.postcard

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

private const val POSTCARD_TAG = "VideoPostcard"
private const val POSTCARD_WIDTH_PX = 1200
private const val POSTCARD_RELATIVE_PATH = "Pictures/BiliPai/Postcards"
private val POSTCARD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val POSTCARD_FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

internal suspend fun renderVideoPostcardBitmap(
    context: Context,
    draft: VideoPostcardDraft,
    config: VideoPostcardRenderConfig,
): Bitmap? = withContext(Dispatchers.Default) {
    val sourceFrame = decodeSampledFrame(context, draft.imageUri, maxSidePx = 1440) ?: return@withContext null
    try {
        when (config.template) {
            VideoPostcardTemplate.CINEMA -> renderCinemaPostcard(sourceFrame, draft, config)
            VideoPostcardTemplate.POLAROID -> renderPolaroidPostcard(sourceFrame, draft, config)
            VideoPostcardTemplate.ATMOSPHERE -> renderAtmospherePostcard(sourceFrame, draft, config)
        }
    } catch (error: Exception) {
        Logger.e(POSTCARD_TAG, "Failed to render video postcard", error)
        null
    } finally {
        sourceFrame.recycle()
    }
}

internal suspend fun saveVideoPostcardToGallery(
    context: Context,
    bitmap: Bitmap,
    videoTitle: String,
    timestampMs: Long = System.currentTimeMillis(),
): Uri? = withContext(Dispatchers.IO) {
    val safeTitle = videoTitle
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim()
        .ifEmpty { "BiliPai" }
        .take(40)
    val timePart = POSTCARD_FILE_TIME_FORMAT.format(
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    )
    val fileName = "Postcard_${safeTitle}_$timePart.png"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, POSTCARD_RELATIVE_PATH)
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
        Logger.e(POSTCARD_TAG, "Failed to save postcard to gallery", error)
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

internal suspend fun writeVideoPostcardShareFile(
    context: Context,
    bitmap: Bitmap,
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("postcard_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(4)
            ?.forEach { it.delete() }
        val file = File(dir, "postcard_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.onFailure {
        Logger.e(POSTCARD_TAG, "Failed to write share postcard file", it)
    }.getOrNull()
}

private fun decodeSampledFrame(context: Context, uri: Uri, maxSidePx: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null
        var sampleSize = 1
        while (max(srcW / sampleSize, srcH / sampleSize) > maxSidePx) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

private fun renderCinemaPostcard(
    frame: Bitmap,
    draft: VideoPostcardDraft,
    config: VideoPostcardRenderConfig,
): Bitmap {
    val width = POSTCARD_WIDTH_PX
    val horizontalPad = 56f
    val frameDrawW = width - horizontalPad * 2
    val aspect = (frame.width.toFloat() / frame.height.coerceAtLeast(1).toFloat()).coerceIn(0.65f, 2.4f)
    val frameDrawH = (frameDrawW / aspect).coerceIn(420f, 840f)
    val topBarH = 112f

    val quote = sanitizePostcardLine(config.quoteText, maxChars = 110)
    val mood = sanitizePostcardLine(config.moodNote, maxChars = 100)

    val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF8EC")
        textSize = 40f
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }
    val moodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B5B2A8")
        textSize = 27f
    }
    val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A877E")
        textSize = 24f
    }

    val textMaxW = (width - horizontalPad * 2).roundToInt()
    val quoteLayout = if (quote.isNotEmpty()) buildStaticLayout("「$quote」", quotePaint, textMaxW, Layout.Alignment.ALIGN_CENTER) else null
    val moodLayout = if (mood.isNotEmpty()) buildStaticLayout(mood, moodPaint, textMaxW, Layout.Alignment.ALIGN_CENTER) else null
    val metaText = if (config.showMetadata) buildMetadataLine(draft) else ""
    val metaLayout = if (metaText.isNotEmpty()) buildStaticLayout(metaText, metaPaint, textMaxW, Layout.Alignment.ALIGN_NORMAL) else null

    var bottomH = 68f
    if (quoteLayout != null) bottomH += quoteLayout.height + 30f
    if (moodLayout != null) bottomH += moodLayout.height + 24f
    if (metaLayout != null) bottomH += metaLayout.height + 44f
    bottomH = bottomH.coerceAtLeast(140f)

    val totalHeight = (topBarH + frameDrawH + bottomH).roundToInt()
    val output = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.parseColor("#0F1013"))

    // 微妙胶片齿孔装饰
    val sprocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D1F24")
    }
    var x = 44f
    while (x < width - 44f) {
        canvas.drawRoundRect(RectF(x, 34f, x + 28f, 52f), 6f, 6f, sprocketPaint)
        x += 58f
    }

    // 帧图绘制 + 轻微底部暗角
    val frameRect = RectF(horizontalPad, topBarH, horizontalPad + frameDrawW, topBarH + frameDrawH)
    drawCenterCropBitmap(canvas, frame, frameRect, cornerRadius = 10f)
    val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, frameRect.bottom - 140f, 0f, frameRect.bottom,
            Color.TRANSPARENT, Color.argb(145, 15, 16, 19), Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(frameRect.left, frameRect.bottom - 140f, frameRect.right, frameRect.bottom, vignettePaint)

    var cursorY = frameRect.bottom + 42f
    quoteLayout?.let {
        canvas.save()
        canvas.translate(horizontalPad, cursorY)
        it.draw(canvas)
        canvas.restore()
        cursorY += it.height + 24f
    }
    moodLayout?.let {
        canvas.save()
        canvas.translate(horizontalPad, cursorY)
        it.draw(canvas)
        canvas.restore()
        cursorY += it.height + 24f
    }
    if (metaLayout != null) {
        val dividerY = cursorY + 4f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A2C32")
            strokeWidth = 2f
        }
        canvas.drawLine(horizontalPad, dividerY, width - horizontalPad, dividerY, linePaint)
        canvas.save()
        canvas.translate(horizontalPad, dividerY + 20f)
        metaLayout.draw(canvas)
        canvas.restore()
    }

    return output
}

private fun renderPolaroidPostcard(
    frame: Bitmap,
    draft: VideoPostcardDraft,
    config: VideoPostcardRenderConfig,
): Bitmap {
    val width = POSTCARD_WIDTH_PX
    val outerPad = 56f
    val innerPad = 42f
    val cardW = width - outerPad * 2
    val frameW = cardW - innerPad * 2
    val aspect = (frame.width.toFloat() / frame.height.coerceAtLeast(1).toFloat()).coerceIn(0.7f, 2.1f)
    val frameH = (frameW / aspect).coerceIn(440f, 820f)

    val quote = sanitizePostcardLine(config.quoteText, maxChars = 110)
    val mood = sanitizePostcardLine(config.moodNote, maxChars = 100)

    val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D1F24")
        textSize = 38f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val moodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A5F6A")
        textSize = 27f
    }
    val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C919C")
        textSize = 23f
    }

    val textW = frameW.roundToInt()
    val quoteLayout = if (quote.isNotEmpty()) buildStaticLayout("“$quote”", quotePaint, textW, Layout.Alignment.ALIGN_NORMAL) else null
    val moodLayout = if (mood.isNotEmpty()) buildStaticLayout(mood, moodPaint, textW, Layout.Alignment.ALIGN_NORMAL) else null
    val metaText = if (config.showMetadata) buildMetadataLine(draft) else ""
    val metaLayout = if (metaText.isNotEmpty()) buildStaticLayout(metaText, metaPaint, textW, Layout.Alignment.ALIGN_NORMAL) else null

    var cardBottomH = 56f
    if (quoteLayout != null) cardBottomH += quoteLayout.height + 24f
    if (moodLayout != null) cardBottomH += moodLayout.height + 22f
    if (metaLayout != null) cardBottomH += metaLayout.height + 36f
    cardBottomH = cardBottomH.coerceAtLeast(130f)

    val cardH = innerPad + frameH + cardBottomH
    val totalHeight = (outerPad * 2 + cardH).roundToInt()
    val output = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.parseColor("#EAE6DF"))

    val cardRect = RectF(outerPad, outerPad, outerPad + cardW, outerPad + cardH)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAF8F4")
        setShadowLayer(28f, 0f, 12f, Color.argb(38, 0, 0, 0))
    }
    canvas.drawRoundRect(cardRect, 22f, 22f, cardPaint)

    val frameRect = RectF(
        cardRect.left + innerPad,
        cardRect.top + innerPad,
        cardRect.right - innerPad,
        cardRect.top + innerPad + frameH,
    )
    drawCenterCropBitmap(canvas, frame, frameRect, cornerRadius = 12f)

    var cursorY = frameRect.bottom + 38f
    quoteLayout?.let {
        canvas.save()
        canvas.translate(frameRect.left, cursorY)
        it.draw(canvas)
        canvas.restore()
        cursorY += it.height + 20f
    }
    moodLayout?.let {
        canvas.save()
        canvas.translate(frameRect.left, cursorY)
        it.draw(canvas)
        canvas.restore()
        cursorY += it.height + 22f
    }
    metaLayout?.let {
        canvas.save()
        canvas.translate(frameRect.left, cursorY + 8f)
        it.draw(canvas)
        canvas.restore()
    }

    return output
}

private fun renderAtmospherePostcard(
    frame: Bitmap,
    draft: VideoPostcardDraft,
    config: VideoPostcardRenderConfig,
): Bitmap {
    val width = POSTCARD_WIDTH_PX
    val pad = 64f
    val frameW = width - pad * 2
    val aspect = (frame.width.toFloat() / frame.height.coerceAtLeast(1).toFloat()).coerceIn(0.7f, 2.2f)
    val frameH = (frameW / aspect).coerceIn(440f, 820f)
    val (topColor, bottomColor) = extractAtmosphereGradientColors(frame)

    val quote = sanitizePostcardLine(config.quoteText, maxChars = 110)
    val mood = sanitizePostcardLine(config.moodNote, maxChars = 100)

    val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val moodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 255, 255, 255)
        textSize = 27f
    }
    val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 255, 255, 255)
        textSize = 23f
    }

    val textW = (frameW - 48f).roundToInt()
    val quoteLayout = if (quote.isNotEmpty()) buildStaticLayout(quote, quotePaint, textW, Layout.Alignment.ALIGN_NORMAL) else null
    val moodLayout = if (mood.isNotEmpty()) buildStaticLayout(mood, moodPaint, textW, Layout.Alignment.ALIGN_NORMAL) else null
    val metaText = if (config.showMetadata) buildMetadataLine(draft) else ""
    val metaLayout = if (metaText.isNotEmpty()) buildStaticLayout(metaText, metaPaint, textW, Layout.Alignment.ALIGN_NORMAL) else null

    var cardTextH = 52f
    if (quoteLayout != null) cardTextH += quoteLayout.height + 24f
    if (moodLayout != null) cardTextH += moodLayout.height + 20f
    if (metaLayout != null) cardTextH += metaLayout.height + 28f
    cardTextH = cardTextH.coerceAtLeast(116f)

    val totalHeight = (pad + frameH + 34f + cardTextH + pad).roundToInt()
    val output = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), totalHeight.toFloat(),
            topColor, bottomColor, Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

    val frameRect = RectF(pad, pad, pad + frameW, pad + frameH)
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 0, 0, 0)
        setShadowLayer(34f, 0f, 16f, Color.argb(90, 0, 0, 0))
    }
    canvas.drawRoundRect(frameRect, 28f, 28f, shadowPaint)
    drawCenterCropBitmap(canvas, frame, frameRect, cornerRadius = 28f)

    val glassCardRect = RectF(pad, frameRect.bottom + 34f, pad + frameW, frameRect.bottom + 34f + cardTextH)
    val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 255, 255, 255)
    }
    val glassStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(80, 255, 255, 255)
    }
    canvas.drawRoundRect(glassCardRect, 28f, 28f, glassFill)
    canvas.drawRoundRect(glassCardRect, 28f, 28f, glassStroke)

    var cursorY = glassCardRect.top + 30f
    val textLeft = glassCardRect.left + 24f
    quoteLayout?.let {
        canvas.save()
        canvas.translate(textLeft, cursorY)
        it.draw(canvas)
        canvas.restore()
        cursorY += it.height + 18f
    }
    moodLayout?.let {
        canvas.save()
        canvas.translate(textLeft, cursorY)
        it.draw(canvas)
        canvas.restore()
        cursorY += it.height + 18f
    }
    metaLayout?.let {
        canvas.save()
        canvas.translate(textLeft, cursorY + 4f)
        it.draw(canvas)
        canvas.restore()
    }

    return output
}

private fun extractAtmosphereGradientColors(frame: Bitmap): Pair<Int, Int> {
    val scaled = Bitmap.createScaledBitmap(frame, 16, 16, true)
    try {
        var topR = 0L; var topG = 0L; var topB = 0L; var topCount = 0
        var botR = 0L; var botG = 0L; var botB = 0L; var botCount = 0
        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                val px = scaled.getPixel(x, y)
                if (y < scaled.height / 2) {
                    topR += Color.red(px); topG += Color.green(px); topB += Color.blue(px); topCount++
                } else {
                    botR += Color.red(px); botG += Color.green(px); botB += Color.blue(px); botCount++
                }
            }
        }
        val tColor = darkenForAtmosphere(
            (topR / topCount.coerceAtLeast(1)).toInt(),
            (topG / topCount.coerceAtLeast(1)).toInt(),
            (topB / topCount.coerceAtLeast(1)).toInt(),
            factor = 0.56f,
        )
        val bColor = darkenForAtmosphere(
            (botR / botCount.coerceAtLeast(1)).toInt(),
            (botG / botCount.coerceAtLeast(1)).toInt(),
            (botB / botCount.coerceAtLeast(1)).toInt(),
            factor = 0.34f,
        )
        return tColor to bColor
    } finally {
        if (scaled !== frame) scaled.recycle()
    }
}

private fun darkenForAtmosphere(r: Int, g: Int, b: Int, factor: Float): Int {
    val nr = (r * factor + 14).roundToInt().coerceIn(18, 175)
    val ng = (g * factor + 16).roundToInt().coerceIn(18, 175)
    val nb = (b * factor + 22).roundToInt().coerceIn(22, 185)
    return Color.rgb(nr, ng, nb)
}

private fun buildMetadataLine(draft: VideoPostcardDraft): String {
    val dateText = POSTCARD_DATE_FORMAT.format(
        Instant.ofEpochMilli(draft.capturedAtMillis).atZone(ZoneId.systemDefault())
    )
    val timeCode = formatPostcardTimestamp(draft.positionMs)
    val title = sanitizePostcardLine(draft.videoTitle, maxChars = 34).replace('\n', ' ')
    val up = sanitizePostcardLine(draft.upName, maxChars = 18).replace('\n', ' ')
    return buildList {
        if (title.isNotEmpty()) add(title)
        if (up.isNotEmpty()) add("UP $up")
        add(timeCode)
        add(dateText)
        add("BiliPai")
    }.joinToString(" · ")
}

private fun drawCenterCropBitmap(
    canvas: Canvas,
    bitmap: Bitmap,
    dest: RectF,
    cornerRadius: Float,
) {
    val srcAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    val dstAspect = dest.width() / dest.height().coerceAtLeast(1f)
    val srcRect = if (srcAspect > dstAspect) {
        val targetSrcW = (bitmap.height * dstAspect).roundToInt().coerceIn(1, bitmap.width)
        val left = (bitmap.width - targetSrcW) / 2
        Rect(left, 0, left + targetSrcW, bitmap.height)
    } else {
        val targetSrcH = (bitmap.width / dstAspect).roundToInt().coerceIn(1, bitmap.height)
        val top = (bitmap.height - targetSrcH) / 2
        Rect(0, top, bitmap.width, top + targetSrcH)
    }

    canvas.save()
    val path = Path().apply { addRoundRect(dest, cornerRadius, cornerRadius, Path.Direction.CW) }
    canvas.clipPath(path)
    canvas.drawBitmap(bitmap, srcRect, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

private fun buildStaticLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    alignment: Layout.Alignment,
): StaticLayout = StaticLayout.Builder
    .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
    .setAlignment(alignment)
    .setLineSpacing(6f, 1.08f)
    .setIncludePad(false)
    .build()
