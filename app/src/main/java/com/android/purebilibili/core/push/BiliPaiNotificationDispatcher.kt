package com.android.purebilibili.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.app.CUSTOM_PUSH_DYNAMIC_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_LIVE_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_MESSAGE_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_TEST_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_VIDEO_CHANNEL_ID
import com.android.purebilibili.app.resolveAppNotificationChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object BiliPaiNotificationDispatcher {
    private const val IMAGE_FETCH_TIMEOUT_MS = 3_000L
    private const val MAX_IMAGE_BYTES = 1_500_000
    private const val COVER_MAX_DIMENSION_PX = 640
    private const val AVATAR_MAX_DIMENSION_PX = 192

    private val CUSTOM_PUSH_CHANNEL_IDS = setOf(
        CUSTOM_PUSH_VIDEO_CHANNEL_ID,
        CUSTOM_PUSH_LIVE_CHANNEL_ID,
        CUSTOM_PUSH_DYNAMIC_CHANNEL_ID,
        CUSTOM_PUSH_MESSAGE_CHANNEL_ID,
        CUSTOM_PUSH_TEST_CHANNEL_ID,
    )

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        for (spec in resolveAppNotificationChannels()) {
            if (spec.id !in CUSTOM_PUSH_CHANNEL_IDS) continue
            manager.createNotificationChannel(
                NotificationChannel(spec.id, spec.name, spec.importance).apply {
                    description = spec.description
                    setShowBadge(spec.showBadge)
                    if (spec.silent) setSound(null, null)
                },
            )
        }
    }

    fun canPost(context: Context, channelId: String? = null): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (channelId == null) return true
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(channelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    suspend fun dispatch(context: Context, payload: PushPayload): Boolean {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        if (!canPost(appContext, payload.type.channelId)) return false

        val coverBitmap = payload.cover?.let { url ->
            fetchBoundedBitmap(url, COVER_MAX_DIMENSION_PX)
        }
        val avatarBitmap = payload.avatar?.let { url ->
            fetchBoundedBitmap(url, AVATAR_MAX_DIMENSION_PX)
        }

        val notificationId = 71000 + (payload.id.hashCode() and 0x7FFF)
        val contentIntent = buildContentPendingIntent(appContext, notificationId, payload)
        val priority = when (payload.type) {
            PushNotificationType.LIVE,
            PushNotificationType.MESSAGE -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val category = when (payload.type) {
            PushNotificationType.MESSAGE -> NotificationCompat.CATEGORY_MESSAGE
            else -> NotificationCompat.CATEGORY_SOCIAL
        }

        val builder = NotificationCompat.Builder(appContext, payload.type.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setAutoCancel(true)
            .setPriority(priority)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(payload.timestampSeconds * 1000L)
            .setShowWhen(true)
            .setGroup("custom_push:${payload.group ?: payload.type.wireValue}")
            .setContentIntent(contentIntent)

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        if (coverBitmap != null) {
            val style = NotificationCompat.BigPictureStyle()
                .bigPicture(coverBitmap)
                .setBigContentTitle(payload.title)
                .setSummaryText(payload.body)
            if (avatarBitmap == null) {
                style.bigLargeIcon(null as Bitmap?)
            }
            builder.setStyle(style)
        } else if (payload.body.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
        }

        return try {
            NotificationManagerCompat.from(appContext)
                .notify("custom_push:${payload.id}", notificationId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildContentPendingIntent(
        context: Context,
        requestCode: Int,
        payload: PushPayload,
    ): PendingIntent {
        val intent = if (!payload.url.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(payload.url)).apply {
                setClass(context, MainActivity::class.java)
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                data = Uri.Builder()
                    .scheme("bilipai-custom-push")
                    .authority(payload.type.wireValue)
                    .appendPath(payload.id)
                    .build()
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 冷启动后台唤醒时使用轻量 HttpURLConnection 按通知缩略尺寸解码，不拉起全局图片缓存。
     */
    private suspend fun fetchBoundedBitmap(url: String, maxDimensionPx: Int): Bitmap? {
        return withTimeoutOrNull(IMAGE_FETCH_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2_000
                        readTimeout = 2_500
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "BiliPai-Push/1.0")
                    }
                    if (connection.responseCode !in 200..299) return@withContext null
                    val bytes = connection.inputStream.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) return@withContext null
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    }
                    decodeSampledBitmap(bytes, maxDimensionPx)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
            }
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > maxDimensionPx ||
            bounds.outHeight / sampleSize > maxDimensionPx
        ) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }
}
