package com.android.purebilibili.core.notification.liveupdate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.purebilibili.EXTRA_PENDING_NAVIGATION_ROUTE
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.app.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import com.android.purebilibili.app.LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import com.android.purebilibili.navigation.ScreenRoutes

/**
 * Android 16/17 实时状态胶囊（Live Updates）统一构造器。
 * 统一使用 AndroidX NotificationCompat（含 ProgressStyle、setShortCriticalText 与 setRequestPromotedOngoing），
 * 在 Android 16.0 (API 36.0) 到 17+ 均兼容晋升实时通知，低版本平滑回退至标准持续进度/倒计时通知。
 */
internal object LiveUpdateNotificationFactory {

    const val SLEEP_TIMER_LIVE_UPDATE_NOTIFICATION_ID = 43_021

    fun ensureLiveUpdateChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(LIVE_UPDATE_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_live_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_live_updates_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPromoteLiveUpdates(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return NotificationManagerCompat.from(context).canPostPromotedNotifications()
    }

    fun buildDownloadNotification(
        context: Context,
        state: LiveUpdateDownloadState,
        contentIntent: PendingIntent? = null,
    ): Notification {
        val clamped = state.progressPercent.coerceIn(0, 100)
        val indeterminate = clamped <= 0 && !state.paused
        val title = state.title.trim().ifEmpty { context.getString(R.string.live_update_downloading_fallback) }
        val contentText = formatLiveUpdateDownloadContentText(
            progressPercent = clamped,
            remainingCount = state.remainingCount,
            paused = state.paused,
        )
        val launchIntent = contentIntent ?: PendingIntent.getActivity(
            context,
            43_010,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_PENDING_NAVIGATION_ROUTE, ScreenRoutes.DownloadList.route)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val promote = resolveLiveUpdateShouldPromote(
            sdkInt = Build.VERSION.SDK_INT,
            canPostPromoted = canPromoteLiveUpdates(context),
        )
        val channelId = if (promote) {
            ensureLiveUpdateChannel(context)
            LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
        } else {
            DOWNLOAD_NOTIFICATION_CHANNEL_ID
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, clamped, indeterminate)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = NotificationCompat.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressIndeterminate(indeterminate)
            if (!indeterminate) {
                progressStyle.setProgress(clamped)
            }
            builder
                .setStyle(progressStyle)
                .setShortCriticalText(formatLiveUpdateDownloadShortText(clamped, state.paused))
            if (promote) {
                builder.setRequestPromotedOngoing(true)
            }
        }

        return builder.build()
    }

    fun postSleepTimerNotification(
        context: Context,
        state: LiveUpdateSleepTimerState,
        contentIntent: PendingIntent?,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureLiveUpdateChannel(context)
        val notification = buildSleepTimerNotification(context, state, contentIntent)
        manager.notify(SLEEP_TIMER_LIVE_UPDATE_NOTIFICATION_ID, notification)
    }

    fun cancelSleepTimerNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(SLEEP_TIMER_LIVE_UPDATE_NOTIFICATION_ID)
    }

    fun buildSleepTimerNotification(
        context: Context,
        state: LiveUpdateSleepTimerState,
        contentIntent: PendingIntent?,
    ): Notification {
        val title = context.getString(R.string.live_update_sleep_timer_title)
        val contentText = if (state.fadingOut) {
            context.getString(R.string.live_update_sleep_timer_fading)
        } else {
            state.title.trim().ifEmpty { context.getString(R.string.qs_tile_audio_playing) }
        }
        val promote = resolveLiveUpdateShouldPromote(
            sdkInt = Build.VERSION.SDK_INT,
            canPostPromoted = canPromoteLiveUpdates(context),
        )

        val builder = NotificationCompat.Builder(context, LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_headphones_24)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(state.deadlineEpochMs)
            .setShowWhen(true)
            .setUsesChronometer(!state.fadingOut)
            .setChronometerCountDown(!state.fadingOut)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setShortCriticalText(
                formatLiveUpdateSleepShortText(state.remainingMinutes, state.fadingOut),
            )
            if (promote) {
                builder.setRequestPromotedOngoing(true)
            }
        }

        return builder.build()
    }
}
