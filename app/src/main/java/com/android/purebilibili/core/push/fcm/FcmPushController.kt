package com.android.purebilibili.core.push.fcm

import android.content.Context
import com.android.purebilibili.core.push.PushConfigStore
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface FcmAvailability {
    data object Ready : FcmAvailability
    data object MissingFirebaseConfig : FcmAvailability
    data object MissingPlayServices : FcmAvailability
}

sealed interface FcmTokenResult {
    data class Success(val token: String) : FcmTokenResult
    data class Failure(val reason: String) : FcmTokenResult
}

object FcmPushController {
    fun checkAvailability(context: Context): FcmAvailability {
        val appContext = context.applicationContext
        val gmsStatus = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)
        if (gmsStatus != ConnectionResult.SUCCESS) {
            return FcmAvailability.MissingPlayServices
        }
        val hasFirebaseApp = runCatching {
            FirebaseApp.getApps(appContext).isNotEmpty() || FirebaseApp.initializeApp(appContext) != null
        }.getOrDefault(false)
        if (!hasFirebaseApp) {
            return FcmAvailability.MissingFirebaseConfig
        }
        return FcmAvailability.Ready
    }

    fun setAutoInitEnabled(context: Context, enabled: Boolean) {
        if (checkAvailability(context) != FcmAvailability.Ready) return
        runCatching {
            FirebaseMessaging.getInstance().isAutoInitEnabled = enabled
        }
    }

    suspend fun fetchAndStoreToken(context: Context): FcmTokenResult {
        val appContext = context.applicationContext
        return when (checkAvailability(appContext)) {
            FcmAvailability.MissingPlayServices -> FcmTokenResult.Failure(
                "设备未安装或未启用 Google Play 服务，请改用 UnifiedPush 通道",
            )
            FcmAvailability.MissingFirebaseConfig -> FcmTokenResult.Failure(
                "当前安装包未包含 Firebase 配置（google-services.json），请改用 UnifiedPush 通道",
            )
            FcmAvailability.Ready -> {
                setAutoInitEnabled(appContext, true)
                val tokenResult = suspendCancellableCoroutine { continuation ->
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener
                        if (task.isSuccessful && !task.result.isNullOrBlank()) {
                            continuation.resume(FcmTokenResult.Success(task.result))
                        } else {
                            val msg = task.exception?.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: "获取 FCM Token 失败，请检查网络连接"
                            continuation.resume(FcmTokenResult.Failure(msg))
                        }
                    }
                }
                if (tokenResult is FcmTokenResult.Success) {
                    PushConfigStore.setFcmToken(appContext, tokenResult.token)
                }
                tokenResult
            }
        }
    }
}
