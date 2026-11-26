package com.android.purebilibili.feature.screenshot

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import android.view.Window
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val APP_SCREENSHOT_TAG = "AppScreenshot"
private const val APP_SCREENSHOT_RELATIVE_PATH = "Pictures/BiliPai/Screenshots"

data class AppScreenshotSavedImage(
    val result: AppScreenshotResult,
    val uri: Uri? = null
)

suspend fun captureAndSaveAppScreenshot(
    activity: Activity,
    timestampMs: Long = System.currentTimeMillis()
): AppScreenshotResult {
    return captureAndSaveAppScreenshotImage(activity, timestampMs).result
}

suspend fun captureAndSaveAppScreenshotImage(
    activity: Activity,
    timestampMs: Long = System.currentTimeMillis(),
    includeBrandWatermark: Boolean = false,
    isDarkTheme: Boolean = false,
    watermarkContext: AppScreenshotWatermarkContext? = null,
): AppScreenshotSavedImage {
    val bitmap = captureCurrentAppWindow(activity = activity)
        ?: return AppScreenshotSavedImage(AppScreenshotResult.CaptureFailed)
    val fileName = buildAppScreenshotFileName(timestampMs = timestampMs)
    var watermarkedBitmap: Bitmap? = null

    return try {
        val bitmapToSave = if (includeBrandWatermark) {
            composeAppScreenshotWithTopWatermark(
                context = activity,
                source = bitmap,
                isDarkTheme = isDarkTheme,
                watermarkContext = watermarkContext,
            ).also { watermarkedBitmap = it }
        } else {
            bitmap
        }
        val uri = saveAppScreenshotBitmapToGalleryUri(activity, bitmapToSave, fileName)
        if (uri != null) {
            AppScreenshotSavedImage(AppScreenshotResult.Success, uri)
        } else {
            AppScreenshotSavedImage(AppScreenshotResult.SaveFailed)
        }
    } finally {
        watermarkedBitmap?.recycle()
        bitmap.recycle()
    }
}

suspend fun captureCurrentAppWindow(activity: Activity): Bitmap? = withContext(Dispatchers.Main.immediate) {
    val window = activity.window
    val decorView = window.decorView
    if (!decorView.isAttachedToWindow || decorView.width <= 0 || decorView.height <= 0) {
        Logger.w(APP_SCREENSHOT_TAG, "Window capture skipped because decorView is not ready")
        return@withContext null
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        captureWindowWithPixelCopy(window = window, decorView = decorView)
            ?: drawDecorViewFallback(decorView)
    } else {
        drawDecorViewFallback(decorView)
    }
}

private suspend fun captureWindowWithPixelCopy(
    window: Window,
    decorView: View
): Bitmap? = suspendCancellableCoroutine { continuation ->
    val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
    val sourceRect = Rect(0, 0, decorView.width, decorView.height)

    try {
        PixelCopy.request(
            window,
            sourceRect,
            bitmap,
            { result ->
                if (!continuation.isActive) {
                    bitmap.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap)
                } else {
                    Logger.w(APP_SCREENSHOT_TAG, "PixelCopy window capture failed with code: $result")
                    bitmap.recycle()
                    continuation.resume(null)
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (e: Exception) {
        Logger.e(APP_SCREENSHOT_TAG, "PixelCopy window capture exception", e)
        bitmap.recycle()
        if (continuation.isActive) {
            continuation.resume(null)
        }
    }

    continuation.invokeOnCancellation {
        bitmap.recycle()
    }
}

private fun drawDecorViewFallback(decorView: View): Bitmap? {
    return runCatching {
        Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            decorView.draw(Canvas(bitmap))
        }
    }.onFailure { throwable ->
        Logger.e(APP_SCREENSHOT_TAG, "DecorView draw fallback failed", throwable)
    }.getOrNull()
}

fun cropAppScreenshotBitmap(
    bitmap: Bitmap,
    cropRect: AppScreenshotCropRect
): Bitmap? {
    return runCatching {
        Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width,
            cropRect.height
        )
    }.onFailure { throwable ->
        Logger.e(APP_SCREENSHOT_TAG, "Failed to crop app screenshot", throwable)
    }.getOrNull()
}

internal suspend fun saveAppScreenshotBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String = buildAppScreenshotFileName()
): Boolean = saveAppScreenshotBitmapToGalleryUri(context, bitmap, fileName) != null

internal suspend fun saveAppScreenshotBitmapToGalleryUri(
    context: Context,
    bitmap: Bitmap,
    fileName: String = buildAppScreenshotFileName()
): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.RELATIVE_PATH, APP_SCREENSHOT_RELATIVE_PATH)
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
    } catch (e: Exception) {
        Logger.e(APP_SCREENSHOT_TAG, "Failed to save app screenshot", e)
        resolver.delete(uri, null, null)
        null
    }
}

fun shareAppScreenshot(context: Context, uri: Uri): Boolean {
    return runCatching {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "BiliPai screenshot", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val chooser = Intent.createChooser(shareIntent, "分享截图").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
        true
    }.onFailure { throwable ->
        Logger.e(APP_SCREENSHOT_TAG, "Failed to share app screenshot", throwable)
    }.getOrDefault(false)
}
