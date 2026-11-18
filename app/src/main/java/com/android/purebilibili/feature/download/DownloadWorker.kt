package com.android.purebilibili.feature.download

import android.content.Context
import android.os.Build
import androidx.work.*
import com.android.purebilibili.app.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

/**
 * 🔧 WorkManager Worker for background downloads
 * 
 * This worker handles video downloads in a way that survives app backgrounding
 * and process death. WorkManager automatically reschedules work if the process dies.
 */
class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val KEY_WORK_TOKEN = "download_work_token"
        private const val KEY_RETRY_WAKEUP = "download_retry_wakeup"
        const val TAG_DOWNLOAD = "video_download"

        // 通知 id 按任务派生：同一任务反复 setForeground 复用同一个 id，避免堆积；
        // 并发下载时各任务各自一条通知
        fun foregroundNotificationId(taskId: String?): Int = (taskId ?: "download").hashCode()
        
        /**
         * 调度下载任务
         */
        internal suspend fun enqueue(
            context: Context,
            taskId: String,
            workToken: String,
            retryDelayMs: Long? = null,
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val inputData = Data.Builder()
                .putString(KEY_TASK_ID, taskId)
                .putString(KEY_WORK_TOKEN, workToken)
                .putBoolean(KEY_RETRY_WAKEUP, retryDelayMs != null)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(TAG_DOWNLOAD)
                .addTag(taskId)
                .setInitialDelay(retryDelayMs ?: 0L, TimeUnit.MILLISECONDS)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    if (retryDelayMs == null) taskId else "$taskId:retry:$workToken",
                    if (retryDelayMs == null) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    workRequest
                ).await()

            com.android.purebilibili.core.util.Logger.d(
                "DownloadWorker",
                if (retryDelayMs == null) "已调度下载：$taskId" else "已安排下载重试：$taskId",
            )
        }
        
        /**
         * 取消下载任务
         */
        internal suspend fun cancel(context: Context, taskId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(taskId).await()
            com.android.purebilibili.core.util.Logger.d("DownloadWorker", "⏹️ Cancelled download: $taskId")
        }
        
        /**
         * 取消所有下载任务
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_DOWNLOAD)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val workToken = inputData.getString(KEY_WORK_TOKEN)
        val telemetry = DownloadTelemetry()
        var stage = DownloadStage.FOREGROUND
        var attempt: Int? = null
        val onStage: (DownloadStage) -> Unit = {
            stage = it
            if (stage == DownloadStage.COMPLETE) {
                telemetry.record(stage, DownloadOutcome.COMPLETED, attempt = attempt)
            }
        }
        var progressJob: Job? = null
        var foregroundStarted = false
        try {
            DownloadManager.init(applicationContext)
            if (inputData.getBoolean(KEY_RETRY_WAKEUP, false)) {
                DownloadManager.makeDownloadRetryReady(taskId, workToken)
                return@withContext Result.success()
            }
            attempt = DownloadManager.beginWorkerAttempt(taskId, workToken)
                ?: return@withContext Result.success()
            telemetry.record(DownloadStage.FOREGROUND, DownloadOutcome.STARTED, attempt = attempt)
            setForeground(getForegroundInfo())
            foregroundStarted = true
            progressJob = launch {
                while (isActive) {
                    delay(2_000L)
                    if (!DownloadManager.ownsActiveWork(taskId, workToken)) break
                    val progress = DownloadManager.tasks.value[taskId]?.progress ?: 0f
                    try {
                        setForeground(getForegroundInfo(progress))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        com.android.purebilibili.core.util.Logger.w("DownloadWorker", "下载通知更新失败", error)
                    }
                }
            }
            val timeout = resolveDownloadForegroundWorkTimeoutMs(Build.VERSION.SDK_INT)
            if (timeout == null) {
                DownloadManager.executeDownload(taskId, workToken, onStage)
            } else {
                withTimeout(timeout) { DownloadManager.executeDownload(taskId, workToken, onStage) }
            }
            Result.success()
        } catch (error: TimeoutCancellationException) {
            if (DownloadManager.ownsActiveWork(taskId, workToken)) {
                telemetry.record(stage, DownloadOutcome.PAUSED, attempt = attempt)
            }
            DownloadManager.finishWorkerAttempt(
                taskId, workToken, DownloadStatus.PAUSED,
                "下载已暂停：后台服务接近系统时限，请稍后继续",
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            val interrupted = DownloadManager.ownsActiveWork(taskId, workToken)
            if (attempt != null && stage != DownloadStage.COMPLETE) {
                val task = DownloadManager.tasks.value[taskId]
                val outcome = when {
                    interrupted -> DownloadOutcome.INTERRUPTED
                    task?.status == DownloadStatus.PAUSED -> DownloadOutcome.PAUSED
                    else -> DownloadOutcome.CANCELLED
                }
                telemetry.record(stage, outcome, attempt = attempt)
            }
            if (interrupted) {
                DownloadManager.finishWorkerAttempt(taskId, workToken, DownloadStatus.PENDING, "下载中断，等待重试")
            }
            throw cancelled
        } catch (error: Exception) {
            if (!DownloadManager.ownsActiveWork(taskId, workToken)) return@withContext Result.failure()
            progressJob?.cancel()
            val retry = foregroundStarted && isRetryableDownloadFailure(error) &&
                DownloadManager.deferDownloadRetry(taskId, workToken)
            if (!retry && !DownloadManager.ownsActiveWork(taskId, workToken)) {
                return@withContext Result.success()
            }
            val message = when {
                !foregroundStarted -> "无法启动下载服务：${error.message.orEmpty()}"
                retry -> "下载遇到网络错误，已安排重试"
                else -> error.message ?: "下载失败"
            }
            com.android.purebilibili.core.util.Logger.w("DownloadWorker", message, error)
            if (!retry) {
                DownloadManager.finishWorkerAttempt(taskId, workToken, DownloadStatus.FAILED, message)
            }
            telemetry.record(
                stage,
                if (retry) DownloadOutcome.RETRY else DownloadOutcome.FAILED,
                error,
                attempt = attempt,
            )
            if (retry) Result.success() else Result.failure()
        } finally {
            progressJob?.cancel()
        }
    }

    // CoroutineWorker 要求的抽象实现；实际渲染走带进度的重载
    override suspend fun getForegroundInfo(): ForegroundInfo = getForegroundInfo(0f)

    private suspend fun getForegroundInfo(progress: Float): ForegroundInfo {
        val task = DownloadManager.tasks.value[inputData.getString(KEY_TASK_ID)]
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        val notification = androidx.core.app.NotificationCompat.Builder(
            applicationContext,
            DOWNLOAD_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(task?.title?.takeIf { it.isNotBlank() } ?: "下载中...")
            .setContentText("下载中 $percent%")
            .setProgress(100, percent, percent <= 0)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        val serviceType = resolveDownloadForegroundServiceType(Build.VERSION.SDK_INT)
        return if (serviceType != null) {
            ForegroundInfo(foregroundNotificationId(inputData.getString(KEY_TASK_ID)), notification, serviceType)
        } else {
            ForegroundInfo(foregroundNotificationId(inputData.getString(KEY_TASK_ID)), notification)
        }
    }
}
