package com.android.purebilibili.feature.message.notification

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.android.purebilibili.R
import com.android.purebilibili.app.MESSAGE_NOTIFICATION_SERVICE_CHANNEL_ID
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MessageNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var polling: Job? = null
    private var sessionObserver: Job? = null
    private var settingsObserver: Job? = null
    private var foregroundStarted = false
    private var powerReceiverRegistered = false
    private val screenOn = MutableStateFlow(true)
    private val charging = MutableStateFlow(true)

    private val powerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshPowerState()
        }
    }

    private fun refreshPowerState() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        screenOn.value = pm?.isInteractive ?: true
        // PowerManager 没有 isCharging API，充电态用 ACTION_BATTERY_CHANGED 粘性广播查询。
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        charging.value = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun onCreate() {
        super.onCreate()
        try {
            refreshPowerState()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(this, powerStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            powerReceiverRegistered = true
        } catch (e: Exception) {
            Logger.e("MessageNotification", "Unable to track screen/charging state", e)
        }
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
        if (settingsObserver?.isActive != true) settingsObserver = scope.launch(Dispatchers.IO) {
            try {
                MessageNotificationSettingsStore.getSettings(this@MessageNotificationService).collect { settings ->
                    if (!settings.enabled || !settings.residentEnabled) stopSelf()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("MessageNotification", "Unable to observe resident settings", e)
                stopSelf()
            }
        }
        if (sessionObserver?.isActive != true) sessionObserver = scope.launch(Dispatchers.IO) {
            try {
                com.android.purebilibili.core.store.TokenManager.observeServiceSession(this@MessageNotificationService) {
                    MessageNotificationNotifier.cancelOtherAccounts(
                        this@MessageNotificationService,
                        com.android.purebilibili.core.store.TokenManager.midCache
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("MessageNotification", "Session observation unavailable; checks still refresh the session", e)
            }
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
                    refreshPowerState()
                    val idleMs = resolveResidentPollDelayMs(settings.mode, screenOn.value, charging.value)
                    withTimeoutOrNull(idleMs) {
                        // Settings changes wake every mode immediately, including stop/mode changes
                        // while the screen is off. Power changes only wake the long idle wait.
                        combine(
                            screenOn, charging,
                            MessageNotificationSettingsStore.getSettings(this@MessageNotificationService)
                        ) { on, plugged, latest ->
                            latest != settings || (idleMs == RESIDENT_IDLE_POLL_MS && (on || plugged))
                        }.first { it }
                    }
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
        if (powerReceiverRegistered) {
            try {
                unregisterReceiver(powerStateReceiver)
            } catch (e: Exception) {
                Logger.e("MessageNotification", "Unable to unregister power receiver", e)
            }
            powerReceiverRegistered = false
        }
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
