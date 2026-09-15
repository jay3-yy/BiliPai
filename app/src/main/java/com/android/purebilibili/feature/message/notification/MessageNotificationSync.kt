package com.android.purebilibili.feature.message.notification

import android.content.Context
import androidx.core.content.ContextCompat
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

internal object MessageNotificationSync {
    // Call only from a visible settings action, interactive startup, or the permitted boot receiver.
    // Re-read inside the lock: a stale UI snapshot must not undo a more recent switch change.
    suspend fun sync(context: Context): Boolean = messageNotificationMutex.withLock {
        val appContext = context.applicationContext
        val settings = MessageNotificationSettingsStore.getSettings(appContext).first()
        MessageNotificationScheduler.sync(appContext, settings)
        val intent = MessageNotificationService.startIntent(appContext)
        if (!settings.enabled) {
            appContext.stopService(intent)
            MessageNotificationStateStore.clearAll(appContext)
            MessageNotificationNotifier.cancelMessages(appContext)
        } else if (!settings.residentEnabled || !MessageNotificationNotifier.canPost(appContext)) {
            appContext.stopService(intent)
        } else {
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (e: RuntimeException) {
                Logger.e("MessageNotification", "Resident start unavailable; periodic work remains enabled", e)
                return@withLock false
            }
        }
        true
    }
}
