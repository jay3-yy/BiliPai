package com.android.purebilibili.core.push.unifiedpush

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.purebilibili.core.push.PushConfigStore
import com.android.purebilibili.core.push.PushIngressRouter
import com.android.purebilibili.core.push.PushIngressSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BiliPaiUnifiedPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val token = intent.getStringExtra(UnifiedPushProtocol.EXTRA_TOKEN).orEmpty()
        if (token.isBlank()) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    val config = PushConfigStore.getConfig(appContext)
                    // 校验实例 Token，防止非授权广播覆盖端点或注入消息。
                    if (config.unifiedPushInstanceToken.isBlank() || token != config.unifiedPushInstanceToken) {
                        return@withTimeoutOrNull
                    }

                    when (action) {
                        UnifiedPushProtocol.ACTION_NEW_ENDPOINT -> {
                            val endpoint = intent.getStringExtra(UnifiedPushProtocol.EXTRA_ENDPOINT)
                                ?.trim()
                                .orEmpty()
                            if (endpoint.isNotEmpty()) {
                                PushConfigStore.setUnifiedPushEndpoint(appContext, endpoint)
                                PushConfigStore.appendLog(
                                    context = appContext,
                                    source = PushIngressSource.UNIFIED_PUSH,
                                    accepted = true,
                                    summary = "已分配端点",
                                )
                            }
                        }

                        UnifiedPushProtocol.ACTION_MESSAGE -> {
                            val bytes = intent.getByteArrayExtra(UnifiedPushProtocol.EXTRA_BYTES_MESSAGE)
                                ?: intent.getStringExtra(UnifiedPushProtocol.EXTRA_MESSAGE)
                                    ?.toByteArray(Charsets.UTF_8)
                                ?: return@withTimeoutOrNull
                            PushIngressRouter.handleIncoming(
                                context = appContext,
                                source = PushIngressSource.UNIFIED_PUSH,
                                rawBytes = bytes,
                            )
                        }

                        UnifiedPushProtocol.ACTION_UNREGISTERED -> {
                            PushConfigStore.setUnifiedPushEndpoint(appContext, "")
                            PushConfigStore.appendLog(
                                context = appContext,
                                source = PushIngressSource.UNIFIED_PUSH,
                                accepted = false,
                                summary = "分发器已取消注册",
                            )
                        }

                        UnifiedPushProtocol.ACTION_REGISTRATION_FAILED -> {
                            val reason = intent.getStringExtra(UnifiedPushProtocol.EXTRA_MESSAGE)
                                ?.trim()
                                ?.ifEmpty { null }
                                ?: "registration_failed"
                            PushConfigStore.appendLog(
                                context = appContext,
                                source = PushIngressSource.UNIFIED_PUSH,
                                accepted = false,
                                summary = "注册失败: $reason",
                            )
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
