package com.android.purebilibili.feature.nearby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.Closeable
import java.util.ArrayDeque

/** 发现仅提供候选地址；连接时仍须验证已配对的 TLS 身份。 */
@Suppress("DEPRECATION")
internal class NearbyDiscovery(
    context: Context,
    private val ownPin: String,
    private val name: String,
    private val deviceType: String,
    private val port: Int,
    private val onPeers: (List<NearbyPeer>) -> Unit,
) : Closeable {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val peers = linkedMapOf<String, NearbyPeer>()
    private data class Candidate(val info: NsdServiceInfo, val generation: Long)
    private val queue = ArrayDeque<Candidate>()
    private val generations = mutableMapOf<String, Long>()
    private var nextGeneration = 0L
    private var resolving = false
    @Volatile private var closed = false
    private var registered = false
    private var discovering = false
    private val registration = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) = Unit
        override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
        override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
    }
    private val discovery = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) = Unit
        override fun onDiscoveryStopped(type: String) = Unit
        override fun onStartDiscoveryFailed(type: String, error: Int) = Unit
        override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
        override fun onServiceFound(info: NsdServiceInfo) {
            synchronized(this@NearbyDiscovery) {
                if (closed || !info.serviceType.startsWith(NEARBY_SERVICE.removeSuffix("."))) return
                if (queue.size < 32 && (generations.size < 32 || generations.containsKey(info.serviceName))) {
                    val generation = ++nextGeneration
                    generations[info.serviceName] = generation
                    queue.add(Candidate(info, generation))
                }
                resolveNext()
            }
        }
        override fun onServiceLost(info: NsdServiceInfo) {
            synchronized(this@NearbyDiscovery) {
                peers.remove(info.serviceName)
                generations.remove(info.serviceName)
                queue.removeAll { it.info.serviceName == info.serviceName }
                if (!closed) onPeers(peers.values.toList())
            }
        }
    }

    fun start() {
        val info = NsdServiceInfo().apply {
            serviceName = "BiliPai-${ownPin.take(8)}"
            serviceType = NEARBY_SERVICE
            port = this@NearbyDiscovery.port
            setAttribute("pin", ownPin)
            setAttribute("name", name)
            setAttribute("type", deviceType)
        }
        // NSD 不可用不影响通过配对码连接。
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration); registered = true }
        runCatching { manager.discoverServices(NEARBY_SERVICE, NsdManager.PROTOCOL_DNS_SD, discovery); discovering = true }
    }

    @Synchronized private fun resolveNext() {
        if (closed || resolving || queue.isEmpty()) return
        resolving = true
        val candidate = queue.removeFirst()
        val info = candidate.info
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = finish(null)
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) = finish(serviceInfo)
            private fun finish(resolved: NsdServiceInfo?) {
                synchronized(this@NearbyDiscovery) {
                    // 服务丢失后，系统仍可能回调旧的解析结果；同名重新出现也不能复用旧结果。
                    if (!closed && resolved != null && generations[info.serviceName] == candidate.generation) {
                        val pin = resolved.attributes["pin"]?.toString(Charsets.UTF_8).orEmpty()
                        val host = resolved.host?.hostAddress?.substringBefore('%').orEmpty()
                        if (pin != ownPin && Regex("[a-f0-9]{64}").matches(pin) &&
                            runCatching { requireLocalAddress(host) }.isSuccess && resolved.port in 1024..65535) {
                            val remoteType = NearbyDeviceFormFactor.fromWire(
                                resolved.attributes["type"]?.toString(Charsets.UTF_8)
                            ).wireValue
                            if (peers.size < 32 || peers.containsKey(resolved.serviceName)) {
                                peers[resolved.serviceName] = NearbyPeer(
                                    id = pin.take(32),
                                    name = resolved.attributes["name"]?.toString(Charsets.UTF_8)?.take(40)
                                        ?.takeIf { it.isNotBlank() } ?: "BiliPai 设备",
                                    pin = pin,
                                    host = host,
                                    port = resolved.port,
                                    deviceType = remoteType,
                                )
                            }
                            onPeers(peers.values.toList())
                        }
                    }
                    resolving = false
                    resolveNext()
                }
            }
        }
        try { manager.resolveService(info, listener) }
        catch (_: RuntimeException) { resolving = false; resolveNext() }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        if (discovering) runCatching { manager.stopServiceDiscovery(discovery) }
        if (registered) runCatching { manager.unregisterService(registration) }
        queue.clear()
        generations.clear()
        peers.clear()
    }
}
