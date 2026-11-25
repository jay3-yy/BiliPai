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
    val lastStatus: Int? = null,
    val measuredAtMs: Long = 0,
    val banned: Boolean = false,
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
    val updatedAtMs: Long = 0,
    val mirrorMode: CdnParallelMirrorMode = CdnParallelMirrorMode.SAFE_SIGNED,
    val concurrencyPreset: CdnConcurrencyPreset = CdnConcurrencyPreset.CONSERVATIVE,
    val allowCellular: Boolean = false,
    val autoConcurrencyReason: String = "",
    val bannedHostCount: Int = 0,
)

/** Process-local, bounded diagnostics. URLs/signatures never enter the observable state or disk. */
internal object CdnTransferRuntime {
    private data class Route(
        val authorizedUrls: List<String>,
        val poolUrls: List<String>,
        val registeredAtMs: Long,
    )

    private val routes = linkedMapOf<String, Route>()
    private var preferredPaths: Set<String> = emptySet()
    private val observedMedia = linkedMapOf<String, Pair<String, Long>>()
    private val mutableState = MutableStateFlow(CdnTransferSnapshot())
    val state = mutableState.asStateFlow()

    @Volatile
    var permits: Semaphore = Semaphore(CDN_MAX_CONNECTIONS, true)
        private set

    private var currentPermitCapacity: Int = CDN_MAX_CONNECTIONS
    private var parallelConfig: CdnParallelConfig = CdnParallelConfig()
    private val banList = CdnSessionBanList()
    private val autoConcurrency = CdnAutoConcurrencyController()
    private var exploreCursor: Int = 0
    private var activeVideoPathSignature: String? = null

    private val enabledState = MutableStateFlow(false)
    val enabledFlow = enabledState.asStateFlow()
    val enabled: Boolean get() = enabledState.value

    @Volatile
    var parallelEnabled: Boolean = false
        private set

    val allowCellular: Boolean get() = parallelConfig.allowCellular
    val allowOpenEndedParallel: Boolean get() = parallelConfig.allowOpenEndedParallel
    val isEnhancedMode: Boolean get() = parallelConfig.isEnhanced
    val maxConnectionsCap: Int get() = parallelConfig.maxConnections

    private var networkIdentity: String? = null
    private var limitedUntilMs = 0L
    private var lastPublishMs = 0L
    private var lastSampleMs = 0L
    private var lastSampleBytes = 0L
    private var snapshot = CdnTransferSnapshot()

    @Synchronized
    fun configure(active: Boolean, parallel: Boolean) {
        configure(
            CdnParallelConfig(
                active = active,
                parallel = parallel,
                mirrorMode = CdnParallelMirrorMode.SAFE_SIGNED,
                concurrencyPreset = CdnConcurrencyPreset.CONSERVATIVE,
                allowCellular = false,
                customHosts = emptyList(),
            )
        )
    }

    @Synchronized
    fun configure(config: CdnParallelConfig) {
        val previousConfig = parallelConfig
        parallelConfig = config
        val effectiveParallel = config.active && config.parallel
        parallelEnabled = effectiveParallel
        val targetCap = config.maxConnections.coerceIn(1, CDN_ENHANCED_MAX_CONNECTIONS)
        if (targetCap != currentPermitCapacity) {
            permits = Semaphore(targetCap, true)
            currentPermitCapacity = targetCap
        }
        snapshot = snapshot.copy(
            parallelEnabled = effectiveParallel,
            mirrorMode = config.mirrorMode,
            concurrencyPreset = config.concurrencyPreset,
            allowCellular = config.allowCellular,
        )
        if (!config.active) {
            routes.clear()
            observedMedia.clear()
            banList.reset()
            autoConcurrency.reset()
            activeVideoPathSignature = null
            snapshot = snapshot.copy(
                preferredHost = null,
                bannedHostCount = 0,
                autoConcurrencyReason = "",
            )
        } else if (previousConfig.mirrorMode != config.mirrorMode || previousConfig.customHosts != config.customHosts) {
            rebuildRoutePools()
        }
        enabledState.value = config.active
        publish(true)
    }

    @Synchronized
    fun register(video: List<String>, audio: List<String>) {
        val now = nowMs()
        val videoSignature = video.firstOrNull { it.isNotBlank() }?.let(::path).orEmpty()
        if (videoSignature.isNotBlank() && videoSignature != activeVideoPathSignature) {
            activeVideoPathSignature = videoSignature
            banList.reset()
            autoConcurrency.onNewSession(parallelConfig.maxConnections)
            exploreCursor = 0
        }
        if (snapshot.preferredHost != null && video.isNotEmpty() && video.none { path(it) in preferredPaths }) {
            snapshot = snapshot.copy(preferredHost = null)
            preferredPaths = emptySet()
            publish(true)
        }
        (video + audio).filter { url ->
            runCatching { URI(url).let { it.scheme == "https" && !it.host.isNullOrBlank() && !it.rawPath.isNullOrBlank() } }
                .getOrDefault(false)
        }.groupBy(::path).forEach { (trackPath, urls) ->
            if (trackPath.isNotBlank()) {
                val authorized = urls.distinct()
                val pool = buildParallelMirrorUrls(authorized, parallelConfig.mirrorMode, parallelConfig.customHosts)
                routes[trackPath] = Route(authorizedUrls = authorized, poolUrls = pool, registeredAtMs = now)
            }
        }
        while (routes.size > 64) routes.remove(routes.keys.first())
        syncBanFlags()
    }

    /** Registers adaptive DASH sibling tracks for the active session without resetting session state. */
    @Synchronized
    fun registerAdaptiveTracks(video: List<String>, audio: List<String>) {
        if (!enabled) return
        val now = nowMs()
        (video + audio).filter { url ->
            runCatching { URI(url).let { it.scheme == "https" && !it.host.isNullOrBlank() && !it.rawPath.isNullOrBlank() } }
                .getOrDefault(false)
        }.groupBy(::path).forEach { (trackPath, urls) ->
            if (trackPath.isNotBlank()) {
                val existing = routes[trackPath]
                val authorized = ((existing?.authorizedUrls.orEmpty()) + urls).distinct()
                val pool = buildParallelMirrorUrls(authorized, parallelConfig.mirrorMode, parallelConfig.customHosts)
                routes[trackPath] = Route(authorizedUrls = authorized, poolUrls = pool, registeredAtMs = now)
            }
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
        val eligible = url in route.authorizedUrls || url in route.poolUrls
        if (!eligible || nowMs() - route.registeredAtMs > 10 * 60_000L) return emptyList()
        val allowed = route.poolUrls.filter { banList.allows(it) }
        return rank(allowed.ifEmpty { route.poolUrls })
    }

    @Synchronized
    fun rank(urls: List<String>): List<String> {
        val now = nowMs()
        val nodes = snapshot.nodes.associateBy { it.host }
        return urls.sortedWith(
            compareBy<String> {
                !banList.allows(it)
            }.thenBy {
                (nodes[hostFromCdnUrl(it)]?.cooldownUntilMs ?: 0) > now
            }.thenByDescending {
                hostFromCdnUrl(it) == snapshot.preferredHost
            }.thenByDescending {
                val node = nodes[hostFromCdnUrl(it)]
                if (node == null || now - node.updatedAtMs > 120_000) 0L else node.speedBps
            }
        )
    }

    @Synchronized
    fun selectDispatchCandidates(urls: List<String>, width: Int): List<String> {
        if (urls.isEmpty()) return emptyList()
        val ranked = rank(urls)
        val preferred = snapshot.preferredHost
        if (!preferred.isNullOrBlank()) {
            val pinned = ranked.filter { hostFromCdnUrl(it) == preferred }
            if (pinned.isNotEmpty()) {
                return (pinned + ranked.filterNot { hostFromCdnUrl(it) == preferred }).take(width.coerceAtLeast(1))
            }
        }
        val now = nowMs()
        val activePool = ranked.filter { banList.allows(it) }.ifEmpty { ranked }
        val speedsByHost = snapshot.nodes.associate { it.host to it.speedBps }
        val measuredAtByHost = snapshot.nodes.associate { it.host to it.measuredAtMs }
        val (selected, nextCursor) = selectParallelCandidates(
            pool = activePool,
            speedsByHost = speedsByHost,
            measuredAtByHost = measuredAtByHost,
            nowMs = now,
            width = width,
            exploreCursor = exploreCursor,
        )
        exploreCursor = nextCursor
        return selected
    }

    @Synchronized
    fun allowsUrl(url: String): Boolean = banList.allows(url)

    @Synchronized
    fun nodeSpeedBps(host: String): Long {
        val now = nowMs()
        val node = snapshot.nodes.firstOrNull { it.host == host } ?: return 0L
        if (node.measuredAtMs <= 0L || now - node.measuredAtMs > CDN_MEASUREMENT_TTL_MS) return 0L
        return node.speedBps
    }

    @Synchronized
    fun sessionConnectionBps(): Long {
        val now = nowMs()
        val freshSpeeds = snapshot.nodes
            .filter { it.speedBps > 0L && it.measuredAtMs > 0L && now - it.measuredAtMs <= CDN_MEASUREMENT_TTL_MS }
            .map { it.speedBps }
            .sorted()
        if (freshSpeeds.isNotEmpty()) {
            return freshSpeeds[freshSpeeds.size / 2]
        }
        val recentHistory = snapshot.speedHistory.lastOrNull()?.takeIf { it > 0L } ?: return 0L
        return recentHistory / snapshot.connections.coerceAtLeast(1)
    }

    @Synchronized
    fun networkChanged(identity: String) {
        if (networkIdentity == identity) return
        networkIdentity = identity
        limitedUntilMs = 0
        banList.reset()
        autoConcurrency.reset()
        snapshot = snapshot.copy(
            nodes = snapshot.nodes.map {
                it.copy(speedBps = 0, firstByteMs = null, cooldownUntilMs = 0, measuredAtMs = 0, banned = false)
            },
            speedHistory = emptyList(),
            bufferMs = 0,
            bannedHostCount = 0,
            autoConcurrencyReason = "",
        )
        lastSampleMs = 0
        lastSampleBytes = snapshot.networkBytes
        publish(true)
    }

    @Synchronized
    fun connectionLimit(saturated: Boolean = false): Int {
        if (!parallelConfig.isEnhanced) {
            val speed = snapshot.speedHistory.lastOrNull()?.takeIf { it > 0 }
                ?: snapshot.nodes.sumOf { it.speedBps * it.active }
            val count = resolveCdnConnections(snapshot.bufferMs, speed, snapshot.bitrateBps, nowMs() < limitedUntilMs)
            snapshot = snapshot.copy(connections = count, autoConcurrencyReason = "")
            publish()
            return count
        }
        autoConcurrency.onPlaybackSample(
            bufferMs = snapshot.bufferMs,
            bitrateBps = snapshot.bitrateBps,
            saturated = saturated,
            maxCap = parallelConfig.maxConnections,
        )
        val (count, reason) = autoConcurrency.resolveTargetConnections(
            bufferMs = snapshot.bufferMs,
            bitrateBps = snapshot.bitrateBps,
            maxCap = parallelConfig.maxConnections,
            isAuto = parallelConfig.concurrencyPreset.isAuto,
        )
        snapshot = snapshot.copy(connections = count, autoConcurrencyReason = reason)
        publish()
        return count
    }

    @Synchronized
    fun playback(bufferMs: Long, bitrateBps: Long) {
        val normalizedBuffer = bufferMs.coerceAtLeast(0)
        val normalizedBitrate = bitrateBps.coerceAtLeast(0)
        snapshot = snapshot.copy(bufferMs = normalizedBuffer, bitrateBps = normalizedBitrate)
        if (parallelConfig.isEnhanced) {
            autoConcurrency.onPlaybackSample(
                bufferMs = normalizedBuffer,
                bitrateBps = normalizedBitrate,
                saturated = permits.availablePermits() == 0,
                maxCap = parallelConfig.maxConnections,
            )
        }
        publish()
    }

    @Synchronized
    fun start(host: String) {
        changeNode(host) { it.copy(active = it.active + 1) }
        publish(true)
    }

    @Synchronized
    fun progress(
        host: String,
        bytes: Int,
        speedBps: Long,
        firstByteMs: Long?,
        sampleBytes: Int = bytes,
    ) {
        if (bytes <= 0) return
        val now = nowMs()
        if (firstByteMs != null && parallelConfig.isEnhanced) {
            autoConcurrency.onFirstByteSlow(
                firstByteMs = firstByteMs,
                saturated = permits.availablePermits() == 0,
                maxCap = parallelConfig.maxConnections,
            )
        }
        snapshot = snapshot.copy(networkBytes = snapshot.networkBytes + bytes)
        changeNode(host) { node ->
            val updateSpeed = speedBps > 0L && sampleBytes >= CDN_EWMA_MIN_SAMPLE_BYTES
            node.copy(
                bytes = node.bytes + bytes,
                speedBps = if (updateSpeed) updateNodeEwmaBps(node.speedBps, speedBps) else node.speedBps,
                measuredAtMs = if (updateSpeed) now else node.measuredAtMs,
                firstByteMs = firstByteMs ?: node.firstByteMs,
            )
        }
        publish()
    }

    @Synchronized
    fun finish(
        host: String,
        success: Boolean,
        status: Int? = null,
        canceled: Boolean = false,
        url: String? = null,
        receivedBytes: Long = 0L,
        speedSampleBps: Long = 0L,
    ) {
        val now = nowMs()
        if (status == 412 || status == 429) {
            limitedUntilMs = now + 30_000
            autoConcurrency.onPushback(status)
        }
        if (url != null) {
            if (success) {
                banList.recordSuccess(url)
            } else if (!canceled) {
                banList.recordFailure(url, receivedBytes, status)
            }
        }
        val bannedHosts = banList.bannedHosts()
        changeNode(host) { node ->
            val shouldUpdateSpeed = speedSampleBps > 0L && receivedBytes >= CDN_EWMA_MIN_SAMPLE_BYTES
            node.copy(
                active = (node.active - 1).coerceAtLeast(0),
                completed = node.completed + if (success) 1 else 0,
                failures = node.failures + if (!success && !canceled) 1 else 0,
                lastStatus = status ?: node.lastStatus,
                cooldownUntilMs = if (!success && !canceled) maxOf(node.cooldownUntilMs, now + 30_000) else node.cooldownUntilMs,
                speedBps = if (shouldUpdateSpeed) updateNodeEwmaBps(node.speedBps, speedSampleBps) else node.speedBps,
                measuredAtMs = if (shouldUpdateSpeed) now else node.measuredAtMs,
                banned = host in bannedHosts,
            )
        }
        snapshot = snapshot.copy(bannedHostCount = bannedHosts.size)
        publish(true)
    }

    @Synchronized
    fun delivered(bytes: Int) {
        if (bytes <= 0) return
        autoConcurrency.onBytesDelivered(bytes)
        snapshot = snapshot.copy(deliveredBytes = snapshot.deliveredBytes + bytes)
        publish()
    }

    @Synchronized
    fun fallback() {
        snapshot = snapshot.copy(fallbackCount = snapshot.fallbackCount + 1)
        publish(true)
    }

    @Synchronized
    fun resumed() {
        snapshot = snapshot.copy(resumedCount = snapshot.resumedCount + 1)
        publish(true)
    }

    @Synchronized
    fun rescue() {
        snapshot = snapshot.copy(rescueCount = snapshot.rescueCount + 1)
        publish(true)
    }

    @Synchronized
    fun avoid(host: String) {
        changeNode(host) { it.copy(cooldownUntilMs = nowMs() + 60_000) }
        publish(true)
    }

    @Synchronized
    fun preferHost(host: String?) {
        preferredPaths = if (host.isNullOrBlank()) emptySet() else routes.keys.toSet()
        snapshot = snapshot.copy(preferredHost = host?.takeIf { it.isNotBlank() })
        publish(true)
    }

    @Synchronized
    fun restoreRoutes() {
        preferredPaths = emptySet()
        banList.reset()
        autoConcurrency.reset()
        snapshot = snapshot.copy(
            preferredHost = null,
            nodes = snapshot.nodes.map { it.copy(cooldownUntilMs = 0, banned = false) },
            bannedHostCount = 0,
            autoConcurrencyReason = "",
        )
        limitedUntilMs = 0
        publish(true)
    }

    private fun rebuildRoutePools() {
        val entries = routes.entries.toList()
        entries.forEach { (trackPath, route) ->
            val pool = buildParallelMirrorUrls(
                originalUrls = route.authorizedUrls,
                mode = parallelConfig.mirrorMode,
                customHosts = parallelConfig.customHosts,
            )
            routes[trackPath] = route.copy(poolUrls = pool)
        }
    }

    private fun syncBanFlags() {
        val bannedHosts = banList.bannedHosts()
        snapshot = snapshot.copy(
            nodes = snapshot.nodes.map { it.copy(banned = it.host in bannedHosts) },
            bannedHostCount = bannedHosts.size,
        )
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
        if (lastSampleMs == 0L) {
            lastSampleMs = now
            lastSampleBytes = snapshot.networkBytes
        }
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
