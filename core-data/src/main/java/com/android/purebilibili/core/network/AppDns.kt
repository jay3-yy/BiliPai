package com.android.purebilibili.core.network

import android.os.Build
import android.os.SystemClock
import com.android.purebilibili.core.network.policy.resolveHardcodedDnsFallback
import okhttp3.Dns
import okhttp3.DnsCache
import okhttp3.android.AndroidDns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicLong

internal const val APP_DNS_FRESH_TTL_MS = 60_000L
internal const val APP_DNS_STALE_FALLBACK_TTL_MS = 300_000L
private const val APP_DNS_CACHE_MAX_HOSTS = 128

internal fun buildNetworkDnsSignature(
    dnsServerAddresses: List<String>,
    isPrivateDnsActive: Boolean,
    privateDnsServerName: String?,
): String {
    val servers = dnsServerAddresses.joinToString(",")
    val privateHost = privateDnsServerName?.trim().orEmpty()
    return "$servers|private=$isPrivateDnsActive|host=$privateHost"
}

/** Used only by the default network callback, whose events run on the same thread. */
internal class NetworkDnsInvalidationTracker {
    private var activeNetworkId: String? = null
    private var lastDnsSignature: String? = null

    fun onAvailable(networkId: String): Boolean {
        if (activeNetworkId == networkId) return false
        activeNetworkId = networkId
        lastDnsSignature = null
        return true
    }

    fun onLost(networkId: String): Boolean {
        if (activeNetworkId != networkId) return false
        activeNetworkId = null
        lastDnsSignature = null
        return true
    }

    fun onLinkPropertiesChanged(networkId: String, dnsSignature: String): Boolean {
        if (activeNetworkId != networkId) return false
        val previous = lastDnsSignature
        lastDnsSignature = dnsSignature
        return previous != null && previous != dnsSignature
    }
}

internal class AppDnsCache(
    private val freshTtlMs: Long = APP_DNS_FRESH_TTL_MS,
    private val staleFallbackTtlMs: Long = APP_DNS_STALE_FALLBACK_TTL_MS,
    private val maxHosts: Int = APP_DNS_CACHE_MAX_HOSTS,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private data class Entry(
        val addresses: List<InetAddress>,
        val resolvedAtMs: Long,
    )

    private val generation = AtomicLong(0L)

    private val entries = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxHosts
    }

    fun currentGeneration(): Long = generation.get()

    @Synchronized
    fun getFresh(
        hostname: String,
        expectedGeneration: Long = generation.get(),
    ): List<InetAddress>? {
        if (expectedGeneration != generation.get()) return null
        val entry = entries[hostname.lowercase()] ?: return null
        val ageMs = nowMs() - entry.resolvedAtMs
        return if (ageMs in 0L..freshTtlMs) entry.addresses else null
    }

    @Synchronized
    fun getStaleFallback(
        hostname: String,
        expectedGeneration: Long = generation.get(),
    ): List<InetAddress>? {
        if (expectedGeneration != generation.get()) return null
        val entry = entries[hostname.lowercase()] ?: return null
        val ageMs = nowMs() - entry.resolvedAtMs
        return if (ageMs in 0L..staleFallbackTtlMs) entry.addresses else null
    }

    @Synchronized
    fun put(
        hostname: String,
        addresses: List<InetAddress>,
        expectedGeneration: Long = generation.get(),
    ): Boolean {
        if (addresses.isEmpty() || expectedGeneration != generation.get()) return false
        entries[hostname.lowercase()] = Entry(addresses = addresses, resolvedAtMs = nowMs())
        return true
    }

    @Synchronized
    fun clear() {
        generation.incrementAndGet()
        entries.clear()
    }
}

private val sharedAppDnsCache = AppDnsCache()
private val sharedAndroidDnsCacheLock = Any()

@Volatile
private var sharedAndroidDnsCache: DnsCache? = null

private fun getOrCreateAndroidDnsCache(): DnsCache {
    sharedAndroidDnsCache?.let { return it }
    return synchronized(sharedAndroidDnsCacheLock) {
        sharedAndroidDnsCache ?: DnsCache().also { sharedAndroidDnsCache = it }
    }
}

fun clearAppDnsCache() {
    sharedAppDnsCache.clear()
    sharedAndroidDnsCache?.evictAll()
}

/**
 * Android 17 needs HTTPS DNS records as well as addresses to negotiate ECH.
 * Return AndroidDns itself: wrapping only lookup() drops its metadata-aware newCall().
 * It uses the system resolver and honors the platform's domain encryption policy;
 * effective ECH also depends on the DNS records and remote TLS server.
 * Debug configurations with explicit IP fallback keep their existing resolver.
 */
fun buildAppDns(allowHardcodedIpFallback: Boolean = false): Dns {
    if (Build.VERSION.SDK_INT >= 37 && !allowHardcodedIpFallback) {
        return AndroidDns(
            dnsCache = getOrCreateAndroidDnsCache(),
            includeServiceMetadata = true,
        )
    }
    return buildCachedAppDns(
        cache = sharedAppDnsCache,
        allowHardcodedIpFallback = allowHardcodedIpFallback,
    )
}

internal fun buildCachedAppDns(
    cache: AppDnsCache,
    allowHardcodedIpFallback: Boolean = false,
    systemLookup: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
): Dns {
    data class InFlightKey(val generation: Long, val host: String)

    val inFlight = ConcurrentHashMap<InFlightKey, CompletableFuture<List<InetAddress>>>()
    return Dns { hostname ->
        val normalizedHost = hostname.trim().lowercase()
        val generation = cache.currentGeneration()
        cache.getFresh(normalizedHost, generation)?.let { return@Dns it }
        // 同网络代次内按域名合并在途解析（Single-Flight）；切网推进代次后，新请求不复用旧在途且旧结果不回填。
        val flightKey = InFlightKey(generation = generation, host = normalizedHost)
        val created = CompletableFuture<List<InetAddress>>()
        val existing = inFlight.putIfAbsent(flightKey, created)
        if (existing != null) {
            return@Dns try {
                existing.get()
            } catch (error: ExecutionException) {
                throw (error.cause as? UnknownHostException)
                    ?: UnknownHostException("DNS lookup failed for $hostname").apply { initCause(error.cause) }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw UnknownHostException("DNS lookup interrupted for $hostname").apply { initCause(interrupted) }
            }
        }
        try {
            val resolved = try {
                systemLookup(hostname).also { addresses ->
                    if (cache.put(normalizedHost, addresses, generation)) {
                        CoreDataLog.d("ApiClient", "DNS resolved: $hostname -> $addresses")
                    }
                }
            } catch (error: UnknownHostException) {
                cache.getStaleFallback(normalizedHost, generation)?.let { stale ->
                    CoreDataLog.w("ApiClient", "DNS transient failure for $hostname, serving stale cache")
                    stale
                } ?: run {
                    CoreDataLog.e("ApiClient", "DNS failed for $hostname: ${error.message}")
                    val fallback = resolveHardcodedDnsFallback(hostname, allowHardcodedIpFallback)
                        ?: throw error
                    CoreDataLog.w(
                        "ApiClient",
                        "Using Hardcoded IP for ${fallback.description}: ${fallback.ipAddress}"
                    )
                    listOf(InetAddress.getByName(fallback.ipAddress))
                }
            }
            created.complete(resolved)
            resolved
        } catch (error: Throwable) {
            created.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(flightKey, created)
        }
    }
}
