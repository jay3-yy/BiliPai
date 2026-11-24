package com.android.purebilibili.feature.profile

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Copy while the picker grant is valid; previews and saved wallpapers then use our own file. */
internal suspend fun importWallpaperImage(
    context: Context,
    source: Uri,
    destinationDirectory: File,
): File {
    var imported: File? = null
    try {
        return withContext(Dispatchers.IO) {
            var mimeType: String? = null
            val file = copyWallpaperImage(
                destinationDirectory = destinationDirectory,
                openSource = { context.contentResolver.openInputStream(source) },
                validateImage = { candidate ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(candidate.absolutePath, options)
                    mimeType = options.outMimeType
                    options.outWidth > 0 && options.outHeight > 0
                },
            )
            imported = file
            val extension = when (mimeType) {
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                else -> "img"
            }
            val renamed = File(file.parentFile, "${file.nameWithoutExtension}.$extension")
            val result = if (renamed == file) file else {
                check(file.renameTo(renamed)) { "无法保存壁纸文件" }
                renamed
            }
            imported = result
            ensureActive()
            result
        }
    } catch (error: Throwable) {
        // Also handles cancellation during the dispatch back to the UI thread.
        imported?.delete()
        throw error
    }
}

internal fun copyWallpaperImage(
    destinationDirectory: File,
    openSource: () -> InputStream?,
    validateImage: (File) -> Boolean,
): File {
    if (!destinationDirectory.isDirectory && !destinationDirectory.mkdirs()) {
        throw IOException("无法创建壁纸目录")
    }
    val destination = File.createTempFile("wallpaper_", ".img", destinationDirectory)
    try {
        val source = openSource() ?: throw IOException("无法读取所选图片，请重新从相册选择")
        source.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        if (destination.length() == 0L || !validateImage(destination)) {
            throw IOException("图片为空、已损坏或格式不受支持，请选择其他图片")
        }
        return destination
    } catch (error: Throwable) {
        destination.delete()
        throw error
    }
}


/** Preserve original GIF bytes and video format; validate off the main thread. */
internal suspend fun importWallpaperMedia(
    context: Context,
    source: Uri,
    destinationDirectory: File,
): File {
    val video = withContext(Dispatchers.IO) {
        context.contentResolver.getType(source)?.startsWith("video/") == true ||
            com.android.purebilibili.core.ui.wallpaper.isVideoWallpaper(source.toString())
    }
    if (!video) return importWallpaperImage(context, source, destinationDirectory)
    var imported: File? = null
    try {
        return withContext(Dispatchers.IO) {
            if (!destinationDirectory.isDirectory && !destinationDirectory.mkdirs()) {
                throw IOException("无法创建壁纸目录")
            }
            val extension = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(source).orEmpty())
                ?.takeIf { com.android.purebilibili.core.ui.wallpaper.isVideoWallpaper("wallpaper.$it") }
                ?: source.lastPathSegment?.substringAfterLast('.', "")?.takeIf {
                    it.lowercase() in setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp")
                } ?: "video"
            val file = File.createTempFile("wallpaper_", ".$extension", destinationDirectory)
            imported = file
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("无法读取所选视频")
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            if (file.length() == 0L) throw IOException("视频文件为空，请选择其他视频")
            val metadata = android.media.MediaMetadataRetriever()
            try {
                runCatching { metadata.setDataSource(file.absolutePath) }.getOrElse {
                    throw IOException("视频损坏或格式不受支持", it)
                }
                val width = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                if (width <= 0) throw IOException("视频损坏或格式不受支持")
            } finally {
                metadata.release()
            }
            ensureActive()
            file
        }
    } catch (error: Throwable) {
        imported?.delete()
        throw error
    }
}
