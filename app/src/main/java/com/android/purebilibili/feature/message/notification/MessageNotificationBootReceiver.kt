package com.android.purebilibili.feature.message.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MessageNotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = MessageNotificationSettingsStore.getSettings(appContext).first()
                MessageNotificationScheduler.sync(appContext, settings)
                if (settings.enabled && settings.residentEnabled && MessageNotificationNotifier.canPost(appContext)) {
                    ContextCompat.startForegroundService(appContext, MessageNotificationService.startIntent(appContext))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("MessageNotification", "Boot recovery unavailable; periodic work remains the fallback", e)
            } finally {
                pending.finish()
            }
        }
    }
}
