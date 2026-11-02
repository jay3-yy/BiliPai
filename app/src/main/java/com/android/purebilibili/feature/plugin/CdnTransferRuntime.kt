package com.android.purebilibili.feature.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.util.concurrent.Semaphore

internal data class CdnNodeTransfer(
    val host: String,
    val active: Int = 0,
    val bytes: Long = 0,
    val speedBps: Long = 0,
    val firstByteMs: Long? = null,
    val completed: Int = 0,
    val failures: Int = 0,
    val cooldownUntilMs: Long = 0,
    val updatedAtMs: Long = 0,
    val lastStatus: Int? = null
)

internal data class CdnTransferSnapshot(
    val nodes: List<CdnNodeTransfer> = emptyList(),
    val preferredHost: String? = null,
    val networkBytes: Long = 0,
    val deliveredBytes: Long = 0,
    val fallbackCount: Int = 0,
    val resumedCount: Int = 0,
    val rescueCount: Int = 0,
    val bufferMs: Long = 0,
    val bitrateBps: Long = 0,
    val connections: Int = 1,
    val speedHistory: List<Long> = emptyList(),
    val parallelEnabled: Boolean = false,
    val updatedAtMs: Long = 0
)

/** Process-local, bounded diagnostics. URLs/signatures never enter the observable state or disk. */
internal object CdnTransferRuntime {
    private data class Route(val urls: List<String>, val registeredAtMs: Long)
    private val routes = linkedMapOf<String, Route>()
    private var preferredPaths: Set<String> = emptySet()
    private val observedMedia = linkedMapOf<String, Pair<String, Long>>()
    private val mutableState = MutableStateFlow(CdnTransferSnapshot())
    val state = mutableState.asStateFlow()
    val permits = Semaphore(CDN_MAX_CONNECTIONS, true)
    private val enabledState = MutableStateFlow(false)
    val enabledFlow = enabledState.asStateFlow()
    val enabled: Boolean get() = enabledState.value
    @Volatile var parallelEnabled: Boolean = false
        private set
    private var networkIdentity: String? = null
    private var limitedUntilMs = 0L
    private var lastPublishMs = 0L
    private var lastSampleMs = 0L
    private var lastSampleBytes = 0L
    private var snapshot = CdnTransferSnapshot()

    @Synchronized
    fun configure(active: Boolean, parallel: Boolean) {
        parallelEnabled = active && parallel
        snapshot = snapshot.copy(parallelEnabled = parallelEnabled)
        if (!active) { routes.clear(); observedMedia.clear(); snapshot = snapshot.copy(preferredHost = null) }
        enabledState.value = active
        publish(true)
    }

    @Synchronized
    fun register(video: List<String>, audio: List<String>) {
        val now = nowMs()
        if (snapshot.preferredHost != null && video.isNotEmpty() && video.none { path(it) in preferredPaths }) {
            snapshot = snapshot.copy(preferredHost = null)
            preferredPaths = emptySet()
            publish(true)
        }
        (video + audio).filter { url ->
            runCatching { URI(url).let { it.scheme == "https" && !it.host.isNullOrBlank() && !it.rawPath.isNullOrBlank() } }.getOrDefault(false)
        }.groupBy(::path).forEach { (path, urls) ->
            if (path.isNotBlank()) routes[path] = Route(urls.distinct(), now)
        }
        while (routes.size > 64) routes.remove(routes.keys.first())
    }

    /** Actual media used by audio/mini players is diagnostic input, not new parallel authorization. */
    @Synchronized
    fun observeMediaUrl(url: String): Boolean {
        if (!enabled || !isDiagnosticMediaUrl(url)) return false
        observedMedia.remove(path(url))
        observedMedia[path(url)] = url to nowMs()
        while (observedMedia.size > 2) observedMedia.remove(observedMedia.keys.first())
        return true
    }

    @Synchronized
    fun recentMediaUrls(): List<String> {
        val now = nowMs()
        return observedMedia.values.toList().asReversed()
            .filter { now - it.second <= 10 * 60_000L }
            .flatMap { (url, _) -> candidates(url).ifEmpty { listOf(url) } }
            .distinct().take(3)
    }

    @Synchronized
    fun candidates(url: String): List<String> {
        if (!enabled) return emptyList()
        val route = routes[path(url)] ?: return emptyList()
        // Only exact, original signed URLs registered by playurl are eligible.
        if (url !in route.urls || nowMs() - route.registeredAtMs > 10 * 60_000L) return emptyList()
        return rank(route.urls)
    }

    @Synchronized
    fun rank(urls: List<String>): List<String> {
        val now = nowMs()
        val nodes = snapshot.nodes.associateBy { it.host }
        return urls.sortedWith(compareBy<String> {
            (nodes[hostFromCdnUrl(it)]?.cooldownUntilMs ?: 0) > now
        }.thenByDescending { hostFromCdnUrl(it) == snapshot.preferredHost }.thenByDescending {
            val node = nodes[hostFromCdnUrl(it)]
            if (node == null || now - node.updatedAtMs > 120_000) 0L else node.speedBps
        })
    }

    @Synchronized
    fun networkChanged(identity: String) {
        if (networkIdentity == identity) return
        networkIdentity = identity
        limitedUntilMs = 0
        snapshot = snapshot.copy(
            nodes = snapshot.nodes.map { it.copy(speedBps = 0, firstByteMs = null, cooldownUntilMs = 0) },
            speedHistory = emptyList(),
            bufferMs = 0
        )
        lastSampleMs = 0
        lastSampleBytes = snapshot.networkBytes
        publish(true)
    }

    @Synchronized
    fun connectionLimit(): Int {
        val speed = snapshot.speedHistory.lastOrNull()?.takeIf { it > 0 }
            ?: snapshot.nodes.sumOf { it.speedBps * it.active }
        val count = resolveCdnConnections(snapshot.bufferMs, speed, snapshot.bitrateBps, nowMs() < limitedUntilMs)
        snapshot = snapshot.copy(connections = count)
        publish()
        return count
    }

    @Synchronized
    fun playback(bufferMs: Long, bitrateBps: Long) {
        snapshot = snapshot.copy(bufferMs = bufferMs.coerceAtLeast(0), bitrateBps = bitrateBps.coerceAtLeast(0))
        publish()
    }

    @Synchronized
    fun start(host: String) {
        changeNode(host) { it.copy(active = it.active + 1) }
        publish(true)
    }

    @Synchronized
    fun progress(host: String, bytes: Int, speedBps: Long, firstByteMs: Long?) {
        if (bytes <= 0) return
        snapshot = snapshot.copy(networkBytes = snapshot.networkBytes + bytes)
        changeNode(host) { it.copy(bytes = it.bytes + bytes, speedBps = speedBps, firstByteMs = firstByteMs ?: it.firstByteMs) }
        publish()
    }

    @Synchronized
    fun finish(host: String, success: Boolean, status: Int? = null, canceled: Boolean = false) {
        val now = nowMs()
        if (status == 412 || status == 429) limitedUntilMs = now + 30_000
        changeNode(host) {
            it.copy(
                active = (it.active - 1).coerceAtLeast(0),
                completed = it.completed + if (success) 1 else 0,
                failures = it.failures + if (!success && !canceled) 1 else 0,
                lastStatus = status ?: it.lastStatus,
                cooldownUntilMs = if (!success && !canceled) maxOf(it.cooldownUntilMs, now + 30_000) else it.cooldownUntilMs
            )
        }
        publish(true)
    }

    @Synchronized fun delivered(bytes: Int) {
        snapshot = snapshot.copy(deliveredBytes = snapshot.deliveredBytes + bytes)
        publish()
    }
    @Synchronized fun fallback() { snapshot = snapshot.copy(fallbackCount = snapshot.fallbackCount + 1); publish(true) }
    @Synchronized fun resumed() { snapshot = snapshot.copy(resumedCount = snapshot.resumedCount + 1); publish(true) }
    @Synchronized fun rescue() { snapshot = snapshot.copy(rescueCount = snapshot.rescueCount + 1); publish(true) }
    @Synchronized fun avoid(host: String) {
        changeNode(host) { it.copy(cooldownUntilMs = nowMs() + 60_000) }; publish(true)
    }
    @Synchronized fun preferHost(host: String?) {
        preferredPaths = if (host.isNullOrBlank()) emptySet() else routes.keys.toSet()
        snapshot = snapshot.copy(preferredHost = host?.takeIf { it.isNotBlank() })
        publish(true)
    }
    @Synchronized fun restoreRoutes() {
        preferredPaths = emptySet()
        snapshot = snapshot.copy(preferredHost = null, nodes = snapshot.nodes.map { it.copy(cooldownUntilMs = 0) })
        limitedUntilMs = 0
        publish(true)
    }

    private fun changeNode(host: String, change: (CdnNodeTransfer) -> CdnNodeTransfer) {
        val previous = snapshot.nodes.firstOrNull { it.host == host } ?: CdnNodeTransfer(host)
        val next = change(previous).copy(updatedAtMs = nowMs())
        val nodes = snapshot.nodes.filterNot { it.host == host } + next
        // Never discard an active transfer when bounding history.
        snapshot = snapshot.copy(nodes = nodes.filter { it.active > 0 } + nodes.filter { it.active == 0 }.takeLast(24))
    }

    private fun publish(force: Boolean = false) {
        val now = nowMs()
        if (!force && now - lastPublishMs < 250) return
        if (lastSampleMs == 0L) { lastSampleMs = now; lastSampleBytes = snapshot.networkBytes }
        if (now - lastSampleMs >= 1_000) {
            val speed = (snapshot.networkBytes - lastSampleBytes) * 1_000 / (now - lastSampleMs)
            snapshot = snapshot.copy(speedHistory = (snapshot.speedHistory + speed).takeLast(12))
            lastSampleMs = now
            lastSampleBytes = snapshot.networkBytes
        }
        lastPublishMs = now
        mutableState.value = snapshot.copy(updatedAtMs = now)
    }

    private fun path(url: String): String = runCatching { URI(url).rawPath.orEmpty() }.getOrDefault("")
    private fun nowMs(): Long = System.nanoTime() / 1_000_000
}

/** Restrict diagnostics to actual Bilibili media, excluding manifests, images and arbitrary URLs. */
internal fun isDiagnosticMediaUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    val host = uri.host.orEmpty().lowercase()
    val path = uri.rawPath.orEmpty().lowercase()
    uri.scheme.equals("https", true) &&
        listOf("bilivideo.com", "bilivideo.cn", "akamaized.net").any { host == it || host.endsWith(".$it") } &&
        (path.endsWith(".m4s") || path.endsWith(".mp4") || path.endsWith(".flv"))
}.getOrDefault(false)
