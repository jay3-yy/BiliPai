package com.android.purebilibili.core.network

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/** 仅在请求失败时输出连接过程；不记录路径、查询参数、请求头或正文。 */
internal class NetworkFailureEventListener : EventListener() {
    private val startedAt = System.nanoTime()
    private val events = mutableListOf<String>()

    // 快速连接回退可能同时尝试多个地址，各线程共用同一请求的监听器。
    @Synchronized
    private fun record(event: String) {
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        events.add("${elapsedMs}ms $event")
    }

    override fun dnsStart(call: Call, domainName: String) = record("dnsStart $domainName")

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) =
        record("dnsEnd $domainName addresses=${inetAddressList.size}")

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
        record("connectStart ${inetSocketAddress.address?.hostAddress}:${inetSocketAddress.port} proxy=${proxy.type()}")

    override fun secureConnectStart(call: Call) = record("tlsStart")

    override fun secureConnectEnd(call: Call, handshake: Handshake?) = record("tlsEnd")

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) = record("connectFailed ${inetSocketAddress.address?.hostAddress} ${ioe.javaClass.simpleName}")

    override fun connectionAcquired(call: Call, connection: Connection) =
        record("connectionAcquired ${connection.protocol()}")

    override fun requestHeadersEnd(call: Call, request: okhttp3.Request) = record("requestHeadersEnd")

    override fun responseHeadersEnd(call: Call, response: Response) =
        record("responseHeadersEnd status=${response.code}")

    override fun responseBodyStart(call: Call) = record("responseBodyStart")

    override fun callFailed(call: Call, ioe: IOException) {
        if (!shouldReportNetworkFailure(call.isCanceled(), ioe)) return
        record("callFailed ${ioe.javaClass.simpleName}")
        val trace = synchronized(this) { events.joinToString("; ") }
        CoreDataLog.w("NetworkFailure", "host=${call.request().url.host}; $trace")
    }
}
