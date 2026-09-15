package com.android.purebilibili.feature.message.notification

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.android.purebilibili.R
import com.android.purebilibili.app.MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MessageNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var polling: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        try {
            MessageNotificationNotifier.ensureChannels(this)
            val stop = PendingIntent.getService(this, NOTIFICATION_ID, stopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("BiliPai 消息通知")
                .setContentText("后台消息监测运行中")
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(MessageNotificationNotifier.contentIntent(this, NOTIFICATION_ID, "resident", "message_notification_settings"))
                .addAction(0, "停止", stop)
                .build()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            foregroundStarted = true
        } catch (e: RuntimeException) {
            Logger.e("MessageNotification", "Unable to promote resident service", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) return START_NOT_STICKY
        if (intent?.action == ACTION_STOP) {
            polling?.cancel()
            polling = null
            scope.launch {
                try {
                    MessageNotificationSettingsStore.setResidentEnabled(this@MessageNotificationService, false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("MessageNotification", "Unable to persist resident stop", e)
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId)
                }
            }
            return START_NOT_STICKY
        }
        if (polling?.isActive != true) polling = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val settings = MessageNotificationSettingsStore.getSettings(this@MessageNotificationService).first()
                    if (!settings.enabled || !settings.residentEnabled || !MessageNotificationNotifier.canPost(this@MessageNotificationService)) break
                    try {
                        MessageNotificationChecker(this@MessageNotificationService).runCheck()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e("MessageNotification", "Resident check failed", e)
                    }
                    delay(resolveMessageNotificationTiming(settings.mode).residentPollSeconds * 1000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("MessageNotification", "Unable to read resident settings", e)
            } finally {
                // A cancelled poll must not destroy the scope before ACTION_STOP persists its choice.
                if (isActive) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 5100
        private const val ACTION_STOP = "com.android.purebilibili.action.MESSAGE_NOTIFICATION_STOP"
        fun startIntent(context: Context) = Intent(context, MessageNotificationService::class.java)
        fun stopIntent(context: Context) = startIntent(context).setAction(ACTION_STOP)
    }
}
