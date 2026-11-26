package com.android.purebilibili.feature.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.purebilibili.R
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.settings.resolveIconOptionPreviewRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val WATERMARK_TAG = "ScreenshotWatermark"
internal const val GLOBAL_SCREENSHOT_PROMPT_COOLDOWN_MS = 1_800L
private const val SHARED_WATERMARK_PREFIX = "watermark_screenshot_"
private const val SHARED_WATERMARK_TTL_MS = 24 * 60 * 60 * 1000L

fun shouldTriggerGlobalScreenshotPrompt(
    enabled: Boolean,
    blocked: Boolean,
    nowMs: Long,
    lastTriggerMs: Long,
    cooldownMs: Long = GLOBAL_SCREENSHOT_PROMPT_COOLDOWN_MS,
): Boolean {
    if (!enabled || blocked) return false
    if (lastTriggerMs > 0L && nowMs - lastTriggerMs < cooldownMs) return false
    return true
}

data class AppScreenshotWatermarkContext(
    val title: String? = null,
    val bvid: String? = null,
) {
    val hasVideoInfo: Boolean
        get() = !title.isNullOrBlank() || !bvid.isNullOrBlank()
}

fun buildWatermarkVideoMetadataLine(
    title: String?,
    bvid: String?,
): String? {
    val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
    val cleanBvid = bvid?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        cleanTitle != null && cleanBvid != null -> "$cleanTitle · $cleanBvid"
        cleanTitle != null -> cleanTitle
        cleanBvid != null -> cleanBvid
        else -> null
    }
}

fun resolveWatermarkHeaderHeightPx(
    contentWidthPx: Int,
    hasVideoMetadata: Boolean = false,
): Int {
    if (contentWidthPx <= 0) return if (hasVideoMetadata) 140 else 96
    return if (hasVideoMetadata) {
        (contentWidthPx * 0.132f).roundToInt().coerceIn(132, 232)
    } else {
        (contentWidthPx * 0.088f).roundToInt().coerceIn(88, 164)
    }
}

/**
 * 在截图顶部拼接简洁的 BiliPai 客户端品牌水印条，原图完整保留在下方。
 * 在视频/听视频页触发时，额外附带当前视频标题与 BV 号。
 */
suspend fun composeAppScreenshotWithTopWatermark(
    context: Context,
    source: Bitmap,
    isDarkTheme: Boolean,
    watermarkContext: AppScreenshotWatermarkContext? = null,
): Bitmap = withContext(Dispatchers.Default) {
    val width = source.width.coerceAtLeast(1)
    val sourceHeight = source.height.coerceAtLeast(1)
    val cleanTitle = watermarkContext?.title?.trim()?.takeIf { it.isNotEmpty() }
    val cleanBvid = watermarkContext?.bvid?.trim()?.takeIf { it.isNotEmpty() }
    val hasVideoMetadata = cleanTitle != null || cleanBvid != null
    val headerHeight = resolveWatermarkHeaderHeightPx(width, hasVideoMetadata = hasVideoMetadata)
    val brandRowHeight = resolveWatermarkHeaderHeightPx(width, hasVideoMetadata = false)
    val output = Bitmap.createBitmap(width, headerHeight + sourceHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val backgroundColor = if (isDarkTheme) Color.rgb(20, 22, 24) else Color.WHITE
    val titleColor = if (isDarkTheme) Color.rgb(242, 244, 247) else Color.rgb(20, 23, 26)
    val subtitleColor = if (isDarkTheme) Color.rgb(155, 163, 174) else Color.rgb(101, 119, 134)
    val bvidBgColor = if (isDarkTheme) Color.argb(34, 255, 255, 255) else Color.argb(18, 0, 0, 0)
    val dividerColor = if (isDarkTheme) Color.argb(38, 255, 255, 255) else Color.argb(24, 0, 0, 0)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
    canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), bgPaint)

    val iconSize = (brandRowHeight * 0.48f).roundToInt().coerceAtLeast(36)
    val iconGap = brandRowHeight * 0.16f
    val subtitleGap = brandRowHeight * 0.12f
    val horizontalMargin = (width * 0.05f).coerceAtLeast(24f)

    val brandName = context.getString(R.string.app_name)
    val tagline = "· 哔哩哔哩第三方客户端"

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = brandRowHeight * 0.34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = subtitleColor
        textSize = brandRowHeight * 0.22f
        typeface = Typeface.DEFAULT
    }

    val titleWidth = titlePaint.measureText(brandName)
    val subtitleWidth = subtitlePaint.measureText(tagline)
    val fullLockupWidth = iconSize + iconGap + titleWidth + subtitleGap + subtitleWidth
    val compactLockupWidth = iconSize + iconGap + titleWidth
    val showTagline = fullLockupWidth <= width - horizontalMargin * 2f
    val lockupWidth = if (showTagline) fullLockupWidth else compactLockupWidth

    val startX = ((width - lockupWidth) / 2f).coerceAtLeast(horizontalMargin)
    val centerY = if (hasVideoMetadata) brandRowHeight * 0.50f else headerHeight / 2f

    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // 先将图标绘制到独立 (0, 0, iconSize, iconSize) 位图，规避 AdaptiveIconDrawable 非零 bounds 偏移导致不可见的问题
    val iconBitmap = renderAppIconBitmap(context = context, sizePx = iconSize)
    if (iconBitmap != null) {
        try {
            val iconLeft = startX
            val iconTop = centerY - iconSize / 2f
            val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            val cornerRadius = iconSize * 0.24f
            val clipPath = Path().apply {
                addRoundRect(iconRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(iconBitmap, iconLeft, iconTop, bitmapPaint)
            canvas.restore()
        } finally {
            iconBitmap.recycle()
        }
    }

    val titleX = startX + iconSize + iconGap
    val titleMetrics = titlePaint.fontMetrics
    val titleBaseline = centerY - (titleMetrics.ascent + titleMetrics.descent) / 2f
    canvas.drawText(brandName, titleX, titleBaseline, titlePaint)

    if (showTagline) {
        val subtitleX = titleX + titleWidth + subtitleGap
        val subtitleMetrics = subtitlePaint.fontMetrics
        val subtitleBaseline = centerY - (subtitleMetrics.ascent + subtitleMetrics.descent) / 2f
        canvas.drawText(tagline, subtitleX, subtitleBaseline, subtitlePaint)
    }

    if (hasVideoMetadata) {
        val maxMetaWidth = (width - horizontalMargin * 2f).coerceAtLeast(120f)
        val metaCenterY = brandRowHeight + (headerHeight - brandRowHeight) * 0.42f
        val metaTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subtitleColor
            textSize = brandRowHeight * 0.22f
            typeface = Typeface.DEFAULT
        }
        val bvidTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textSize = brandRowHeight * 0.20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bvidPadH = brandRowHeight * 0.14f
        val bvidPadV = brandRowHeight * 0.06f
        val gapBetweenTitleAndBvid = brandRowHeight * 0.14f
        val bvidWidth = cleanBvid?.let { bvidTextPaint.measureText(it) + bvidPadH * 2f } ?: 0f

        val availableTitleWidth = if (cleanBvid != null) {
            (maxMetaWidth - bvidWidth - gapBetweenTitleAndBvid).coerceAtLeast(0f)
        } else {
            maxMetaWidth
        }
        val ellipsizedTitle = if (cleanTitle != null && availableTitleWidth > 40f) {
            TextUtils.ellipsize(cleanTitle, metaTextPaint, availableTitleWidth, TextUtils.TruncateAt.END).toString()
        } else {
            null
        }
        val drawnTitleWidth = ellipsizedTitle?.let { metaTextPaint.measureText(it) } ?: 0f
        val totalMetaWidth = when {
            ellipsizedTitle != null && cleanBvid != null -> drawnTitleWidth + gapBetweenTitleAndBvid + bvidWidth
            ellipsizedTitle != null -> drawnTitleWidth
            else -> bvidWidth
        }
        var cursorX = ((width - totalMetaWidth) / 2f).coerceAtLeast(horizontalMargin)

        if (ellipsizedTitle != null) {
            val metrics = metaTextPaint.fontMetrics
            val baseline = metaCenterY - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(ellipsizedTitle, cursorX, baseline, metaTextPaint)
            cursorX += drawnTitleWidth + gapBetweenTitleAndBvid
        }

        if (cleanBvid != null) {
            val metrics = bvidTextPaint.fontMetrics
            val textHeight = metrics.descent - metrics.ascent
            val pillRect = RectF(
                cursorX,
                metaCenterY - textHeight / 2f - bvidPadV,
                cursorX + bvidWidth,
                metaCenterY + textHeight / 2f + bvidPadV,
            )
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bvidBgColor }
            val pillRadius = pillRect.height() / 2f
            canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)
            val baseline = metaCenterY - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(cleanBvid, cursorX + bvidPadH, baseline, bvidTextPaint)
        }
    }

    val dividerPaint = Paint().apply {
        color = dividerColor
        strokeWidth = 1f
    }
    canvas.drawLine(0f, headerHeight - 1f, width.toFloat(), headerHeight - 1f, dividerPaint)

    canvas.drawBitmap(source, 0f, headerHeight.toFloat(), bitmapPaint)

    output
}

internal fun renderAppIconBitmap(context: Context, sizePx: Int): Bitmap? {
    val safeSize = sizePx.coerceAtLeast(1)
    val iconRes = runCatching {
        val iconKey = SettingsManager.getAppIconSync(context)
        val appearance = SettingsManager.getAppIconAppearanceSync(context)
        resolveIconOptionPreviewRes(iconKey = iconKey, appearance = appearance)
    }.getOrDefault(R.mipmap.ic_launcher_blue_snow_maid_round)

    val drawable = (ContextCompat.getDrawable(context, iconRes)
        ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher_blue_snow_maid_round)
        ?: return null).mutate()

    return runCatching {
        Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888).also { bitmap ->
            val iconCanvas = Canvas(bitmap)
            drawable.setBounds(0, 0, safeSize, safeSize)
            drawable.draw(iconCanvas)
        }
    }.onFailure { throwable ->
        Logger.w(WATERMARK_TAG, "Failed to render app icon bitmap", throwable)
    }.getOrNull()
}

/**
 * 将带水印截图写入共享缓存目录，返回可供系统分享面板直接读取的 FileProvider Uri。
 */
suspend fun cacheWatermarkedScreenshotForShare(
    context: Context,
    bitmap: Bitmap,
    timestampMs: Long = System.currentTimeMillis(),
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
        directory.listFiles()
            ?.filter {
                it.name.startsWith(SHARED_WATERMARK_PREFIX) &&
                    timestampMs - it.lastModified() > SHARED_WATERMARK_TTL_MS
            }
            ?.forEach { it.delete() }

        val file = File(directory, "${SHARED_WATERMARK_PREFIX}${timestampMs}.png")
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to compress watermarked screenshot"
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.onFailure { throwable ->
        Logger.e(WATERMARK_TAG, "Failed to cache watermarked screenshot for share", throwable)
    }.getOrNull()
}
