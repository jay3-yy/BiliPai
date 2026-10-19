package com.android.purebilibili.feature.video.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.Logger
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

internal fun shouldStartForegroundWithFallback(primaryNotification: Any?): Boolean {
    return primaryNotification == null
}

internal fun shouldRefreshPlaybackServiceForeground(
    action: String?,
    isForegroundStarted: Boolean
): Boolean {
    return action == PlaybackService.ACTION_START_FOREGROUND ||
        action == PlaybackService.ACTION_MEDIA_CONTROL
}

internal fun shouldStopPlaybackServiceForStartId(
    stopActionStartId: Int,
    latestHandledStartId: Int
): Boolean {
    return stopActionStartId >= latestHandledStartId
}

internal fun resolvePlaybackServiceFallbackIconRes(iconKey: String): Int {
    return resolveNotificationSmallIconRes(iconKey)
}

/**
 * Android 17 后台音频入口。MediaSessionService 负责向系统暴露现有
 * MediaSession，前台通知仍复用 MiniPlayerManager 已稳定的样式和控制逻辑。
 */
class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "mini_player_channel"
        const val ACTION_START_FOREGROUND = "com.android.purebilibili.action.START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.android.purebilibili.action.STOP_FOREGROUND"
        const val ACTION_MEDIA_CONTROL = "com.android.purebilibili.action.MEDIA_CONTROL"
        const val NOTIFICATION_ID = 1002 // 必须与 MiniPlayerManager 中的 ID 一致
        @Volatile private var isForegroundStarted = false
        @Volatile private var latestHandledStartId = 0
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        MiniPlayerManager.getInstance(applicationContext).mediaSession

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        val manager = MiniPlayerManager.getInstance(applicationContext)
        val notification = manager.currentNotification
        if (startInForegroundRequired) {
            startAsForeground(notification ?: buildFallbackNotification())
            isForegroundStarted = true
        } else if (notification != null) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            isForegroundStarted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        latestHandledStartId = maxOf(latestHandledStartId, startId)
        Logger.d(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_START_FOREGROUND, ACTION_MEDIA_CONTROL -> {
                if (shouldRefreshPlaybackServiceForeground(action, isForegroundStarted)) {
                    try {
                        // 获取 MiniPlayerManager 中构建好的通知
                        val primaryNotification = MiniPlayerManager.getInstance(applicationContext).currentNotification
                        val notification = if (shouldStartForegroundWithFallback(primaryNotification)) {
                            Logger.w(TAG, "Primary notification missing, starting foreground with fallback notification")
                            buildFallbackNotification()
                        } else {
                            primaryNotification as Notification
                        }
                        startAsForeground(notification)
                        isForegroundStarted = true
                        // A notification action must promote the service before resuming audio.
                        if (action == ACTION_MEDIA_CONTROL) {
                            resolveMediaControlType(
                                intent?.getIntExtra(MiniPlayerManager.EXTRA_CONTROL_TYPE, 0) ?: 0
                            )?.let { control ->
                                MiniPlayerManager.getInstance(applicationContext).performMediaControl(control)
                            }
                        }
                    } catch (e: Exception) {
                        isForegroundStarted = false
                        Logger.e(TAG, "Failed to start foreground service", e)
                        stopSelf()
                    }
                }
            }
            ACTION_STOP_FOREGROUND -> {
                Logger.d(TAG, "Stopping foreground service")
                isForegroundStarted = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
                if (shouldStopPlaybackServiceForStartId(startId, latestHandledStartId)) {
                    stopSelfResult(startId)
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        isForegroundStarted = false
        latestHandledStartId = 0
        Logger.d(TAG, "onDestroy")
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildFallbackNotification(): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                resolveNotificationIconResByPriority(
                    launcherIconRes = resolveLaunchActivityIconRes(this),
                    fallbackIconKey = SettingsManager.getAppIconSync(this)
                )
            )
            .setContentTitle("BiliPai")
            .setContentText("正在准备播放控件")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "小窗播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "小窗播放控制"
            setShowBadge(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
