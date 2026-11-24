package com.android.purebilibili.core.network

import android.os.Build
import android.os.SystemClock
import com.android.purebilibili.core.network.policy.resolveHardcodedDnsFallback
import okhttp3.Dns
import okhttp3.android.AndroidDns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

internal const val APP_DNS_FRESH_TTL_MS = 60_000L
internal const val APP_DNS_STALE_FALLBACK_TTL_MS = 300_000L
private const val APP_DNS_CACHE_MAX_HOSTS = 128

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

    private val entries = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxHosts
    }

    @Synchronized
    fun getFresh(hostname: String): List<InetAddress>? {
        val entry = entries[hostname.lowercase()] ?: return null
        val ageMs = nowMs() - entry.resolvedAtMs
        return if (ageMs in 0L..freshTtlMs) entry.addresses else null
    }

    @Synchronized
    fun getStaleFallback(hostname: String): List<InetAddress>? {
        val entry = entries[hostname.lowercase()] ?: return null
        val ageMs = nowMs() - entry.resolvedAtMs
        return if (ageMs in 0L..staleFallbackTtlMs) entry.addresses else null
    }

    @Synchronized
    fun put(hostname: String, addresses: List<InetAddress>) {
        if (addresses.isEmpty()) return
        entries[hostname.lowercase()] = Entry(addresses = addresses, resolvedAtMs = nowMs())
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

private val sharedAppDnsCache = AppDnsCache()

fun clearAppDnsCache() {
    sharedAppDnsCache.clear()
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
        return AndroidDns()
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
    val inFlight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()
    return Dns { hostname ->
        val normalizedHost = hostname.trim().lowercase()
        cache.getFresh(normalizedHost)?.let { return@Dns it }
        // 同域名并发解析合并（Single-Flight），避免冷启动或 TTL 过期瞬间多次阻塞系统 DNS。
        val created = CompletableFuture<List<InetAddress>>()
        val existing = inFlight.putIfAbsent(normalizedHost, created)
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
                    cache.put(normalizedHost, addresses)
                    CoreDataLog.d("ApiClient", "DNS resolved: $hostname -> $addresses")
                }
            } catch (error: UnknownHostException) {
                cache.getStaleFallback(normalizedHost)?.let { stale ->
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
            inFlight.remove(normalizedHost, created)
        }
    }
}
