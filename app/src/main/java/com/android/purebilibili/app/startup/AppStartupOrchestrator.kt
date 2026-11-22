package com.android.purebilibili.app

import android.os.Build
import android.os.Handler
import android.os.Looper

internal class AppStartupOrchestrator(
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val deferredDelayMs: Long = PureApplicationRuntimeConfig.deferredNonCriticalStartupDelayMs(),
    private val dex2OatDelayMs: Long = PureApplicationRuntimeConfig.dex2OatProfileInstallDelayMs(),
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {

    fun startupTasks(): List<AppStartupTask> {
        return defaultAppStartupTasks(
            sdkInt = sdkInt,
            deferredDelayMs = deferredDelayMs,
            dex2OatDelayMs = dex2OatDelayMs
        )
    }

    fun runImmediate(taskRunner: (AppStartupTask) -> Unit) {
        startupTasks()
            .filter { it.phase == StartupPhase.BEFORE_FIRST_INTERACTIVE }
            .forEach(taskRunner)
    }

    fun scheduleDeferred(taskRunner: (AppStartupTask) -> Unit) {
        val deferredTasks = startupTasks()
            .filter { it.phase == StartupPhase.AFTER_FIRST_INTERACTIVE }

        val idleTasks = deferredTasks.filter { it.thread == StartupThread.MAIN_IDLE }
        if (idleTasks.isNotEmpty()) {
            val pendingIdleTasks = ArrayDeque(idleTasks)
            Looper.myQueue().addIdleHandler {
                val nextTask = pendingIdleTasks.removeFirstOrNull() ?: return@addIdleHandler false
                taskRunner(nextTask)
                pendingIdleTasks.isNotEmpty()
            }
        }

        deferredTasks
            .filter { it.thread == StartupThread.MAIN_DELAYED }
            .forEach { task ->
                mainHandler.postDelayed({ taskRunner(task) }, task.delayMs)
            }
    }
}
