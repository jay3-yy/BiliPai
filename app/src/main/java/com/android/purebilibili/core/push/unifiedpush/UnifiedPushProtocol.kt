package com.android.purebilibili.core.push.unifiedpush

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class UnifiedPushDistributorInfo(
    val packageName: String,
    val label: String,
)

/**
 * UnifiedPush Android 2.1+ 广播协议轻量实现（零第三方依赖），
 * 兼容 ntfy、Sunup、NextPush、UP-FCM Distributor 等标准分发器。
 */
object UnifiedPushProtocol {
    const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"

    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_APPLICATION = "application"
    const val EXTRA_PI = "pi"
    const val EXTRA_ENDPOINT = "endpoint"
    const val EXTRA_BYTES_MESSAGE = "bytesMessage"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_FEATURES = "features"

    private const val ACTION_DUMMY = "org.unifiedpush.android.connector.DUMMY"

    fun listInstalledDistributors(context: Context): List<UnifiedPushDistributorInfo> {
        val pm = context.packageManager
        val intent = Intent(ACTION_REGISTER)
        val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
        }

        val seen = linkedSetOf<String>()
        return receivers.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName?.takeIf { it.isNotBlank() && it != context.packageName }
                ?: return@mapNotNull null
            if (!seen.add(pkg)) return@mapNotNull null
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            UnifiedPushDistributorInfo(
                packageName = pkg,
                label = appLabel,
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun register(
        context: Context,
        distributorPackage: String,
        instanceToken: String,
    ) {
        val appContext = context.applicationContext
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(ACTION_DUMMY).setPackage(appContext.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val intent = Intent(ACTION_REGISTER).apply {
            setPackage(distributorPackage)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(EXTRA_TOKEN, instanceToken)
            putExtra(EXTRA_APPLICATION, appContext.packageName)
            putExtra(EXTRA_PI, pendingIntent)
            putStringArrayListExtra(EXTRA_FEATURES, arrayListOf("org.unifiedpush.android.distributor.feature.BYTES_MESSAGE"))
        }
        appContext.sendBroadcast(intent)
    }

    fun unregister(
        context: Context,
        distributorPackage: String,
        instanceToken: String,
    ) {
        if (distributorPackage.isBlank() || instanceToken.isBlank()) return
        val appContext = context.applicationContext
        val intent = Intent(ACTION_UNREGISTER).apply {
            setPackage(distributorPackage)
            putExtra(EXTRA_TOKEN, instanceToken)
        }
        appContext.sendBroadcast(intent)
    }
}
