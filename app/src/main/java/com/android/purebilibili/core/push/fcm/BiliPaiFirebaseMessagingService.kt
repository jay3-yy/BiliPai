package com.android.purebilibili.core.push.fcm

import com.android.purebilibili.core.push.PushConfigStore
import com.android.purebilibili.core.push.PushCrypto
import com.android.purebilibili.core.push.PushIngressRouter
import com.android.purebilibili.core.push.PushIngressSource
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class BiliPaiFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        val appContext = applicationContext
        serviceScope.launch {
            try {
                PushConfigStore.setFcmToken(appContext, trimmed)
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val rawBytes = extractRawPayloadBytes(message.data) ?: return
        val appContext = applicationContext
        serviceScope.launch {
            try {
                PushIngressRouter.handleIncoming(
                    context = appContext,
                    source = PushIngressSource.FCM,
                    rawBytes = rawBytes,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        internal fun extractRawPayloadBytes(data: Map<String, String>): ByteArray? {
            if (data.isEmpty()) return null
            val directPayload = data["payload"]?.trim()
            if (!directPayload.isNullOrEmpty()) {
                return directPayload.toByteArray(Charsets.UTF_8)
            }
            val json = JSONObject().apply {
                for ((key, value) in data) {
                    when (key) {
                        "v" -> put(key, value.toIntOrNull() ?: PushCrypto.PUSH_PROTOCOL_VERSION)
                        "ts" -> put(key, value.toLongOrNull() ?: 0L)
                        else -> put(key, value)
                    }
                }
            }
            return json.toString().toByteArray(Charsets.UTF_8)
        }
    }
}
