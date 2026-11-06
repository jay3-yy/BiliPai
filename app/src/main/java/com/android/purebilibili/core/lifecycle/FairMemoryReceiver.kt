package com.android.purebilibili.core.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BadParcelableException
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val FAIR_MEMORY_TRIM_ACTION = "itgsa.intent.action.TRIM"
internal const val FAIR_MEMORY_KILL_ACTION = "itgsa.intent.action.KILL"
internal const val FAIR_MEMORY_HANDLED = 0
internal const val FAIR_MEMORY_NOT_HANDLED = 1
private const val REQUEST_BUDGET_MS = 2_500L

internal enum class FairMemoryOperation(val action: String) {
    TRIM("trim"), KILL("kill")
}

internal data class FairMemoryRequest(
    val notifyType: Int,
    val notifyId: Int,
    val callback: IBinder,
    val operation: FairMemoryOperation,
)

/** 广播与载荷动作必须一致；缺失的通知身份不能被默认的 0 替代。 */
@Suppress("DEPRECATION")
internal fun parseFairMemoryRequest(intent: Intent): FairMemoryRequest? {
    val operation = when (intent.action) {
        FAIR_MEMORY_TRIM_ACTION -> FairMemoryOperation.TRIM
        FAIR_MEMORY_KILL_ACTION -> FairMemoryOperation.KILL
        else -> return null
    }
    val common = intent.getBundleExtra("common") ?: return null
    val type = common.get("notifyType") as? Int ?: return null
    if (type != 1000 && type != 2000) return null
    val id = common.get("notifyId") as? Int ?: return null
    if (common.getString("action") != operation.action) return null
    val callback = common.getBinder("callback") ?: return null
    return FairMemoryRequest(type, id, callback, operation)
}

/** 每条通知使用自己的回调对象，不缓存上一次通知的 Binder。 */
internal fun replyFairMemory(request: FairMemoryRequest, result: Int): Boolean {
    val data = Parcel.obtain()
    return try {
        data.writeInt(request.notifyType)
        data.writeInt(request.notifyId)
        data.writeInt(result)
        data.writeBundle(Bundle())
        // 单向事务没有回复 Parcel，不能等待或读取 readException。
        request.callback.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
    } catch (error: RemoteException) {
        Logger.w("FairMemory", "Memory reply failed", error)
        false
    } finally {
        data.recycle()
    }
}

internal suspend fun executeFairMemoryRequest(
    remainingBudgetMs: Long,
    handle: suspend () -> Unit,
): Int {
    if (remainingBudgetMs <= 0L) return FAIR_MEMORY_NOT_HANDLED
    return try {
        val completed = withTimeoutOrNull(remainingBudgetMs) {
            handle()
            currentCoroutineContext().ensureActive()
            true
        } ?: false
        if (completed) FAIR_MEMORY_HANDLED else FAIR_MEMORY_NOT_HANDLED
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.w("FairMemory", "Memory request failed", error)
        FAIR_MEMORY_NOT_HANDLED
    }
}

/** scope 由应用或服务生命周期提供，注销由同一调用方负责。 */
internal fun registerFairMemoryReceiver(
    context: Context,
    scope: CoroutineScope,
    trim: suspend () -> Unit,
    checkpoint: suspend () -> Unit,
): BroadcastReceiver {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val receivedAtMs = SystemClock.elapsedRealtime()
            val request = try {
                parseFairMemoryRequest(intent)
            } catch (error: BadParcelableException) {
                Logger.w("FairMemory", "Invalid trim payload", error)
                null
            } catch (error: ClassCastException) {
                Logger.w("FairMemory", "Invalid trim field type", error)
                null
            } ?: return
            val pending = goAsync()
            var result = FAIR_MEMORY_NOT_HANDLED
            val job = scope.launch(Dispatchers.Default) {
                result = executeFairMemoryRequest(
                    REQUEST_BUDGET_MS - (SystemClock.elapsedRealtime() - receivedAtMs),
                    when (request.operation) {
                        FairMemoryOperation.TRIM -> trim
                        FairMemoryOperation.KILL -> checkpoint
                    },
                )
            }
            // 即使服务在协程开始前销毁，也必须结束 goAsync 并返回失败回执。
            job.invokeOnCompletion {
                try {
                    val replyResult = if (SystemClock.elapsedRealtime() - receivedAtMs <= REQUEST_BUDGET_MS) {
                        result
                    } else FAIR_MEMORY_NOT_HANDLED
                    Logger.d("FairMemory", "${request.operation} type=${request.notifyType} id=${request.notifyId} result=$replyResult")
                    if (!replyFairMemory(request, replyResult)) {
                        Logger.w("FairMemory", "Trim callback did not accept reply")
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
    // 系统 UID 可投递；不向普通应用开放主动清缓存入口。
    // 独立厂商进程须先确认发送方身份和权限，不能以无权限 EXPORTED 兜底。
    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(FAIR_MEMORY_TRIM_ACTION).apply { addAction(FAIR_MEMORY_KILL_ACTION) },
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    return receiver
}
