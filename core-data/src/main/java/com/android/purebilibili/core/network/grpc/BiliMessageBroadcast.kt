package com.android.purebilibili.core.network.grpc

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.CoreDataLog
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** 订阅只在调用方收集期间存活；取消收集同时取消双向隧道及心跳。 */
object BiliMessageBroadcast {
    private val client by lazy {
        NetworkModule.okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(330, TimeUnit.SECONDS)
            .build()
    }

    fun notifications(identity: AccountSessionIdentity): Flow<Unit> = callbackFlow {
        if (TokenManager.accountIdentity.value != identity) {
            close()
            return@callbackFlow
        }
        val authenticated = CompletableDeferred<Unit>()
        val sequence = AtomicLong()
        val writerLock = Any()
        val sinkReady = CompletableDeferred<BufferedSink>()
        val requestSink = AtomicReference<BufferedSink>()
        fun send(sink: BufferedSink, path: String, type: String, payload: ByteArray) {
            synchronized(writerLock) {
                val frame = BroadcastFrames.encode(path, sequence.incrementAndGet(), type, payload)
                sink.write(ProtoWire.frame(frame))
                sink.flush()
            }
        }
        val body = object : RequestBody() {
            override fun contentType() = "application/grpc".toMediaType()
            override fun isDuplex() = true
            override fun writeTo(sink: BufferedSink) {
                requestSink.set(sink)
                sinkReady.complete(sink)
                send(sink, BROADCAST_CONTROL + "Auth", "bilibili.broadcast.v1.AuthReq", ProtoWire.message(
                    ProtoWire.string(1, UUID.randomUUID().toString()),
                    ProtoWire.string(2, UUID.randomUUID().toString()),
                ))
            }
        }
        val request = BiliGrpcClient.buildRequest(
            path = "/bilibili.broadcast.v1.BroadcastTunnel/CreateTunnel",
            body = body,
            baseUrl = "https://broadcast.chat.bilibili.com:7824",
        )
        val call = client.newCall(request)
        val heartbeat = launch(Dispatchers.IO) {
            authenticated.await()
            val sink = sinkReady.await()
            while (true) {
                delay(285_000L)
                try {
                    send(sink, BROADCAST_CONTROL + "Heartbeat", "bilibili.broadcast.v1.HeartbeatReq", byteArrayOf())
                } catch (error: IOException) {
                    close(error)
                    break
                }
            }
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(e) }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) throw BiliGrpcException(response.code, response.header("grpc-status"), response.header("grpc-message"))
                        val sink = checkNotNull(requestSink.get())
                        val source = response.body.source()
                        while (true) {
                            if (TokenManager.accountIdentity.value != identity) {
                                close()
                                return
                            }
                            val frame = BroadcastFrames.read(source) ?: break
                            check(frame.status == 0L) { "消息广播拒绝 ${frame.path}: ${frame.status}" }
                            if (frame.requiresAck) {
                                send(sink, BROADCAST_CONTROL + "MessageAck", "bilibili.broadcast.v1.MessageAckReq", BroadcastFrames.ack(frame))
                            }
                            when (frame.path) {
                                BROADCAST_CONTROL + "Auth" -> {
                                    if (authenticated.complete(Unit)) {
                                        CoreDataLog.d("MessageBroadcast", "广播鉴权成功，正在订阅消息")
                                        send(sink, BROADCAST_CONTROL + "Subscribe", "bilibili.broadcast.v1.TargetPath", ProtoWire.string(1, IM_NOTIFY_PATH))
                                    }
                                }
                                BROADCAST_CONTROL + "Subscribe" -> {
                                    CoreDataLog.d("MessageBroadcast", "消息广播订阅成功")
                                    trySend(Unit)
                                }
                                IM_NOTIFY_PATH -> {
                                    val notify = ProtoWire.parseFields(frame.payload)
                                    val uid = notify.firstOrNull { it.number == 1 }?.varint
                                    if (uid == identity.mid) trySend(Unit)
                                }
                            }
                        }
                        val trailers = response.trailers()
                        val grpcStatus = response.header("grpc-status") ?: trailers["grpc-status"]
                        if (grpcStatus != null && grpcStatus != "0") {
                            throw BiliGrpcException(response.code, grpcStatus, response.header("grpc-message") ?: trailers["grpc-message"])
                        }
                        throw IOException("消息广播连接已结束")
                    } catch (error: Exception) {
                        close(error)
                    }
                }
            }
        })
        awaitClose {
            heartbeat.cancel()
            call.cancel()
        }
    }
}
