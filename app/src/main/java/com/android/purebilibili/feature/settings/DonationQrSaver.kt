package com.android.purebilibili.feature.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import com.android.purebilibili.core.util.Logger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 赞赏码直接写入相册，保留原始 JPEG 字节；不受普通图片的自定义目录影响。 */
internal suspend fun saveDonationQrToGallery(context: Context, @DrawableRes qrResource: Int): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var target: android.net.Uri? = null
    var completed = false
    try {
        val name = "BiliPai_Support_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BiliPai")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BiliPai")
                check(directory.isDirectory || directory.mkdirs())
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(directory, name).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        target = uri
        val output = resolver.openOutputStream(uri) ?: return@withContext false
        output.use { sink -> context.resources.openRawResource(qrResource).use { it.copyTo(sink) } }
        currentCoroutineContext().ensureActive()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            check(resolver.update(uri, published, null, null) > 0)
        }
        completed = true
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.e("DonationQr", "Unable to save appreciation code", error)
        false
    } finally {
        if (!completed) target?.let { uri ->
            try {
                resolver.delete(uri, null, null)
            } catch (error: Exception) {
                Logger.e("DonationQr", "Unable to remove incomplete appreciation image", error)
            }
        }
    }
}
