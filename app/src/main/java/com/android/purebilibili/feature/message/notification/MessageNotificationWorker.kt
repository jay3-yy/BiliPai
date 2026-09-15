package com.android.purebilibili.feature.message.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.purebilibili.core.util.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class MessageNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (MessageNotificationChecker(applicationContext).runCheck()) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        Result.retry()
    } catch (e: Exception) {
        Logger.e("MessageNotification", "Background check failed", e)
        Result.success()
    }
}

internal object MessageNotificationScheduler {
    const val UNIQUE_WORK_NAME = "message_notification_poll"

    fun sync(context: Context, settings: MessageNotificationSettings) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!settings.enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val timing = resolveMessageNotificationTiming(settings.mode)
        val request = PeriodicWorkRequestBuilder<MessageNotificationWorker>(timing.periodicMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(timing.requiresBatteryNotLow)
                .build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
