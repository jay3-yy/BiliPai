package com.android.purebilibili.feature.video.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

private val SCREENSHOT_FILE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

private val ILLEGAL_FILE_CHAR_REGEX = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

fun resolveScreenshotDimensions(
    videoWidth: Int,
    videoHeight: Int,
    surfaceWidth: Int,
    surfaceHeight: Int,
): Pair<Int, Int> {
    if (videoWidth > 0 && videoHeight > 0) {
        return videoWidth to videoHeight
    }
    if (surfaceWidth > 0 && surfaceHeight > 0) {
        return surfaceWidth to surfaceHeight
    }
    return 1 to 1
}

fun buildScreenshotFileName(videoTitle: String, timestampMs: Long = System.currentTimeMillis()): String {
    val safeTitle = videoTitle
        .replace(ILLEGAL_FILE_CHAR_REGEX, "_")
        .trim()
        .ifEmpty { "video" }
        .take(64)

    val timePart = SCREENSHOT_FILE_TIME_FORMAT.format(
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    )
    return "${safeTitle}_$timePart.png"
}

suspend fun captureAndSaveVideoScreenshot(
    context: Context,
    playerView: PlayerView,
    videoWidth: Int,
    videoHeight: Int,
    videoTitle: String,
    timestampMs: Long = System.currentTimeMillis(),
): Boolean = captureAndSaveVideoScreenshotUri(
    context, playerView, videoWidth, videoHeight, videoTitle, timestampMs,
) != null

suspend fun captureAndSaveVideoScreenshotUri(
    context: Context,
    playerView: PlayerView,
    videoWidth: Int,
    videoHeight: Int,
    videoTitle: String,
    timestampMs: Long = System.currentTimeMillis(),
): Uri? {
    val bitmap = captureVideoScreenshot(
        playerView = playerView,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
    ) ?: return null

    val fileName = buildScreenshotFileName(videoTitle = videoTitle, timestampMs = timestampMs)
    return try {
        saveScreenshotToGalleryUri(context = context, bitmap = bitmap, fileName = fileName)
    } finally {
        bitmap.recycle()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun captureVideoScreenshot(
    playerView: PlayerView,
    videoWidth: Int,
    videoHeight: Int,
): Bitmap? = withContext(Dispatchers.Main.immediate) {
    val videoSurface = playerView.videoSurfaceView
    val (targetWidth, targetHeight) = resolveScreenshotDimensions(
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        surfaceWidth = videoSurface?.width ?: playerView.width,
        surfaceHeight = videoSurface?.height ?: playerView.height,
    )

    when (videoSurface) {
        is TextureView -> {
            val bitmap = runCatching { videoSurface.getBitmap(targetWidth, targetHeight) }.getOrNull()
            if (bitmap == null) {
                Logger.w("VideoScreenshot", "TextureView getBitmap returned null")
                null
            } else {
                resizeBitmapIfNeeded(bitmap = bitmap, targetWidth = targetWidth, targetHeight = targetHeight)
            }
        }

        is SurfaceView -> captureSurfaceViewBitmap(
            surfaceView = videoSurface,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        else -> {
            val fallbackWidth = maxOf(playerView.width, 1)
            val fallbackHeight = maxOf(playerView.height, 1)
            val fallbackBitmap = runCatching {
                Bitmap.createBitmap(fallbackWidth, fallbackHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    playerView.draw(canvas)
                }
            }.getOrNull()

            if (fallbackBitmap == null) {
                Logger.w("VideoScreenshot", "Fallback draw capture failed")
                null
            } else {
                resizeBitmapIfNeeded(
                    bitmap = fallbackBitmap,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                )
            }
        }
    }
}

/**
 * Captures a deliberately small current-frame sample for ambient player chrome.
 * The caller renders the top edge of this sample into the status bar. SurfaceView PixelCopy
 * writes directly into the small destination so frequent live Haze refreshes do not allocate
 * a full-resolution screenshot.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun captureVideoAmbientFrame(
    playerView: PlayerView,
    targetWidth: Int = 160,
    targetHeight: Int = 90,
): Bitmap? = withContext(Dispatchers.Main.immediate) {
    val safeWidth = targetWidth.coerceAtLeast(1)
    val safeHeight = targetHeight.coerceAtLeast(1)
    when (val videoSurface = playerView.videoSurfaceView) {
        is TextureView -> runCatching {
            videoSurface.getBitmap(safeWidth, safeHeight)
        }.getOrNull()

        is SurfaceView -> captureSurfaceViewAmbientBitmap(
            surfaceView = videoSurface,
            targetWidth = safeWidth,
            targetHeight = safeHeight,
        )

        else -> runCatching {
            Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val sourceWidth = playerView.width.coerceAtLeast(1)
                val sourceHeight = playerView.height.coerceAtLeast(1)
                canvas.scale(
                    safeWidth.toFloat() / sourceWidth,
                    safeHeight.toFloat() / sourceHeight,
                )
                playerView.draw(canvas)
            }
        }.getOrNull()
    }
}

suspend fun saveScreenshotToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): Boolean = saveScreenshotToGalleryUri(context, bitmap, fileName) != null

suspend fun saveScreenshotToGalleryUri(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BiliPai/Screenshots")
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext null

    try {
        val wrote = resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
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
        resolver.delete(uri, null, null)
        throw cancelled
    } catch (e: Exception) {
        Logger.e("VideoScreenshot", "Failed to save screenshot", e)
        resolver.delete(uri, null, null)
        null
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal suspend fun captureSurfaceViewBitmap(
    surfaceView: SurfaceView,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap? = suspendCancellableCoroutine { continuation ->
    val copyWidth = maxOf(surfaceView.width, 1)
    val copyHeight = maxOf(surfaceView.height, 1)
    val sourceBitmap = Bitmap.createBitmap(copyWidth, copyHeight, Bitmap.Config.ARGB_8888)

    try {
        PixelCopy.request(
            surfaceView,
            sourceBitmap,
            { result ->
                if (!continuation.isActive) {
                    sourceBitmap.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    val captured = resizeBitmapIfNeeded(
                        bitmap = sourceBitmap,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                    )
                    continuation.resume(captured, onCancellation = { captured.recycle() })
                } else {
                    Logger.w("VideoScreenshot", "PixelCopy failed with code: $result")
                    sourceBitmap.recycle()
                    continuation.resume(null)
                }
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (e: Exception) {
        Logger.e("VideoScreenshot", "PixelCopy exception", e)
        sourceBitmap.recycle()
        if (continuation.isActive) {
            continuation.resume(null)
        }
    }

    // PixelCopy 无法取消；取消后的位图由结果回调回收，避免回收仍在写入的目标。
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private suspend fun captureSurfaceViewAmbientBitmap(
    surfaceView: SurfaceView,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap? = suspendCancellableCoroutine { continuation ->
    val bitmap = Bitmap.createBitmap(
        targetWidth.coerceAtLeast(1),
        targetHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    try {
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                if (!continuation.isActive) {
                    bitmap.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap, onCancellation = { bitmap.recycle() })
                } else {
                    bitmap.recycle()
                    continuation.resume(null)
                }
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (error: Exception) {
        Logger.w("VideoAmbientFrame", "PixelCopy failed: ${error.message}")
        bitmap.recycle()
        if (continuation.isActive) continuation.resume(null)
    }

    // PixelCopy cannot be cancelled. The callback above owns cleanup for cancelled
    // requests; recycling here would destroy a destination still being written.
}

private fun resizeBitmapIfNeeded(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    val safeWidth = maxOf(targetWidth, 1)
    val safeHeight = maxOf(targetHeight, 1)
    if (bitmap.width == safeWidth && bitmap.height == safeHeight) {
        return bitmap
    }
    return Bitmap.createScaledBitmap(bitmap, safeWidth, safeHeight, true).also { scaled ->
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
    }
}
