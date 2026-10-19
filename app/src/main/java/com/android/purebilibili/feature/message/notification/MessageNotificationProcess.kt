package com.android.purebilibili.feature.message.notification

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.network.CoreNetworkConfig
import com.android.purebilibili.core.network.NetworkModule

internal const val MESSAGE_PROCESS_SUFFIX = ":message"

internal fun isMessageNotificationProcess(context: Context): Boolean =
    isPrivateAppProcess(context, MESSAGE_PROCESS_SUFFIX)

internal fun isLightweightServiceProcess(context: Context): Boolean =
    isMessageNotificationProcess(context) || isPrivateAppProcess(context, ":playback")

private fun isPrivateAppProcess(context: Context, suffix: String): Boolean {
    val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
    }
    return name == context.packageName + suffix
}

/** No UI, player, image loader, plugins, startup recovery, analytics or scheduled startup tasks. */
internal fun initializeMessageNotificationProcess(context: Context) {
    NetworkModule.init(context, CoreNetworkConfig(
        debug = BuildConfig.DEBUG,
        allowHardcodedDnsFallback = BuildConfig.ALLOW_HARDCODED_DNS_FALLBACK
    ))
}
