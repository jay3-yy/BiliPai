package com.android.purebilibili.feature.plugin

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.util.NetworkUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Media3 synchronous [DataSource] boundary with BTR-inspired parallel multi-node range scheduling:
 * finite ranges and (when enhanced mode is active) probeable open-ended streams use an HTTP/1.1
 * sliding-window scheduler with ordered streaming delivery, speed-weighted host assignment,
 * pace-aware hedging, and partial-prefix retries; failures fall back seamlessly to [upstream].
 */
@UnstableApi
internal class CdnPlaybackDataSource(
    private val upstream: DataSource,
    private val background: Boolean = false,
    private val client: OkHttpClient? = null,
    private val isWifi: () -> Boolean = { NetworkModule.appContext?.let { NetworkUtils.isWifi(it) } == true },
    private val onCancelDelegate: (() -> Unit)? = null,
) : BaseDataSource(true) {
    private var spec: DataSpec? = null
    private var candidates: List<String> = emptyList()
    private var consumed = 0L
    private var window = ByteArray(0)
    private var windowOffset = 0
    private var session: StreamingRangeSession? = null
    private var delegateOpen = false
    private var started = false
    private var parallel = false
    private var monitored = false
    private var delegateHost = ""
    private var delegateBytes = 0L
    private var delegateStartedNs = 0L
    private var delegateFirstByteMs: Long? = null

    @Volatile
    private var closed = true
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val wakeSignal = AtomicReference(CompletableFuture<Unit>())

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        spec = dataSpec
        consumed = 0L
        window = ByteArray(0)
        windowOffset = 0
        session = null
        wakeSignal.set(CompletableFuture())

        if (CdnTransferRuntime.enabled) {
            val manager = NetworkModule.appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            CdnTransferRuntime.networkChanged(manager?.activeNetwork?.toString().orEmpty())
        }
        candidates = CdnTransferRuntime.candidates(dataSpec.uri.toString())
        val observedMedia = CdnTransferRuntime.observeMediaUrl(dataSpec.uri.toString())
        monitored = candidates.isNotEmpty() || observedMedia
        val networkAllowed = isWifi() || CdnTransferRuntime.allowCellular
        val getWithoutBody = dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET &&
            dataSpec.httpBody == null &&
            dataSpec.position >= 0

        val finiteParallel = candidates.isNotEmpty() &&
            CdnTransferRuntime.parallelEnabled &&
            networkAllowed &&
            getWithoutBody &&
            dataSpec.length >= CDN_MIN_PARALLEL_BYTES &&
            dataSpec.length - 1 <= Long.MAX_VALUE - dataSpec.position

        val openEndedProbe = !finiteParallel &&
            !background &&
            candidates.isNotEmpty() &&
            CdnTransferRuntime.parallelEnabled &&
            CdnTransferRuntime.allowOpenEndedParallel &&
            networkAllowed &&
            getWithoutBody &&
            dataSpec.length == C.LENGTH_UNSET.toLong()

        transferInitializing(dataSpec)

        var resolvedLength = dataSpec.length
        if (finiteParallel) {
            parallel = true
            resolvedLength = dataSpec.length
        } else if (openEndedProbe) {
            val probed = probeOpenEndedHead(dataSpec)
            if (probed != null) {
                parallel = true
                window = probed.bytes
                windowOffset = 0
                resolvedLength = probed.total - dataSpec.position
                val remainingStart = dataSpec.position + probed.bytes.size
                val remainingLength = resolvedLength - probed.bytes.size
                if (remainingLength > 0L) {
                    session = StreamingRangeSession(
                        dataSpec = dataSpec,
                        start = remainingStart,
                        length = remainingLength,
                        initialTotal = probed.total,
                    )
                }
            } else {
                parallel = false
                resolvedLength = openDelegate(dataSpec)
            }
        } else {
            parallel = false
            resolvedLength = openDelegate(dataSpec)
        }

        started = true
        transferStarted(dataSpec)
        return resolvedLength
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (closed || Thread.currentThread().isInterrupted) throw IOException("CDN read canceled")
        val current = spec ?: throw IOException("CDN source is not open")
        val expectedTotal = current.length
        if (expectedTotal != C.LENGTH_UNSET.toLong() && consumed >= expectedTotal) {
            return C.RESULT_END_OF_INPUT
        }

        val count: Int
        if (!parallel) {
            try {
                count = upstream.read(buffer, offset, length)
            } catch (error: IOException) {
                if (monitored) finishDelegate(false, closed)
                throw error
            }
            if (count > 0 && monitored) recordDelegateBytes(count)
        } else {
            if (windowOffset >= window.size) {
                val activeSession = ensureSession(current)
                if (activeSession == null) {
                    return C.RESULT_END_OF_INPUT
                }
                try {
                    if (!CdnTransferRuntime.parallelEnabled || (!isWifi() && !CdnTransferRuntime.allowCellular)) {
                        throw IOException("CDN parallel mode disabled")
                    }
                    val nextBytes = activeSession.nextPieceBytes() ?: return C.RESULT_END_OF_INPUT
                    window = nextBytes
                    windowOffset = 0
                } catch (error: IOException) {
                    cancelCalls()
                    if (closed || Thread.currentThread().isInterrupted || background) throw error
                    CdnTransferRuntime.fallback()
                    parallel = false
                    session = null
                    window = ByteArray(0)
                    windowOffset = 0
                    // Resume at exact delivered offset without discarding already delivered bytes.
                    openDelegate(current.subrange(consumed))
                    return read(buffer, offset, length)
                }
            }
            count = minOf(length, window.size - windowOffset)
            window.copyInto(buffer, offset, windowOffset, windowOffset + count)
            windowOffset += count
            if (windowOffset == window.size) {
                window = ByteArray(0)
                windowOffset = 0
            }
        }

        if (closed) throw IOException("CDN read canceled")
        if (count > 0) {
            consumed += count
            bytesTransferred(count)
            if (monitored) CdnTransferRuntime.delivered(count)
        } else if (count == C.RESULT_END_OF_INPUT && monitored) {
            finishDelegate(true)
        }
        return count
    }

    override fun getUri(): Uri? = if (delegateOpen) upstream.uri else spec?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = if (delegateOpen) upstream.responseHeaders else emptyMap()

    fun cancelPendingRequests() {
        closed = true
        cancelCalls()
        signalWake()
        val cancelDelegate = onCancelDelegate
        if (cancelDelegate != null) {
            cancelDelegate()
        } else if (delegateOpen) {
            upstream.close()
        }
    }

    override fun close() {
        closed = true
        cancelCalls()
        signalWake()
        try {
            if (delegateOpen) upstream.close()
        } finally {
            // CacheWriter can stop at its finite range without an EOF read.
            val current = spec
            val complete = current != null && current.length >= 0 && consumed >= current.length
            if (monitored) finishDelegate(complete, canceled = true)
            delegateOpen = false
            session = null
            window = ByteArray(0)
            windowOffset = 0
            spec = null
            if (started) {
                started = false
                transferEnded()
            }
        }
    }

    private fun ensureSession(current: DataSpec): StreamingRangeSession? {
        session?.let { return it }
        if (current.length == C.LENGTH_UNSET.toLong()) return null
        val remaining = current.length - consumed
        if (remaining <= 0L) return null
        return StreamingRangeSession(
            dataSpec = current,
            start = current.position + consumed,
            length = remaining,
            initialTotal = -1L,
        ).also { session = it }
    }

    private fun openDelegate(dataSpec: DataSpec): Long {
        if (closed) throw IOException("CDN open canceled")
        delegateHost = hostFromCdnUrl(dataSpec.uri.toString())
        delegateBytes = 0
        delegateStartedNs = System.nanoTime()
        delegateFirstByteMs = null
        if (monitored) CdnTransferRuntime.start(delegateHost)
        // Set before open so failed opens can still release upstream resources in close().
        delegateOpen = true
        return try {
            upstream.open(dataSpec)
        } catch (error: IOException) {
            if (monitored) finishDelegate(false, closed)
            throw error
        }
    }

    private fun recordDelegateBytes(count: Int) {
        delegateBytes += count
        val elapsedMs = ((System.nanoTime() - delegateStartedNs) / 1_000_000).coerceAtLeast(1)
        if (delegateFirstByteMs == null) delegateFirstByteMs = elapsedMs
        CdnTransferRuntime.progress(delegateHost, count, delegateBytes * 1_000 / elapsedMs, delegateFirstByteMs)
    }

    private fun finishDelegate(success: Boolean, canceled: Boolean = false) {
        if (delegateHost.isNotEmpty()) {
            CdnTransferRuntime.finish(delegateHost, success, canceled = canceled)
            delegateHost = ""
        }
    }

    private data class HeadProbeResult(val bytes: ByteArray, val total: Long)

    private fun probeOpenEndedHead(dataSpec: DataSpec): HeadProbeResult? {
        val ranked = CdnTransferRuntime.rank(candidates)
            .filter { CdnTransferRuntime.allowsUrl(it) }
            .ifEmpty { candidates }
            .distinctBy(::hostFromCdnUrl)
            .take(2)
        if (ranked.isEmpty()) return null
        val total = AtomicLong(-1L)
        val probeRange = CdnByteRange(
            start = dataSpec.position,
            endInclusive = dataSpec.position + CDN_HEAD_PROBE_BYTES - 1L,
        )
        val attempts = ranked.mapNotNull { url ->
            launchAttempt(
                dataSpec = dataSpec,
                range = probeRange,
                url = url,
                total = total,
                allowClampedEof = true,
            )
        }
        if (attempts.isEmpty()) return null
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
        var winner: Attempt? = null
        try {
            while (winner == null) {
                if (closed || Thread.currentThread().isInterrupted) break
                winner = attempts.firstOrNull { it.result.isDone && !it.result.isCompletedExceptionally }
                if (winner != null) break
                if (attempts.all { it.result.isDone }) break
                if (System.nanoTime() >= deadlineNs) break
                awaitSignal(attempts.filterNot { it.result.isDone }.map { it.result }, 100L)
            }
        } finally {
            attempts.filter { it !== winner }.forEach { it.call?.cancel() }
        }
        val succeeded = winner ?: return null
        val bytes = runCatching { succeeded.result.get() }.getOrNull() ?: return null
        val resolvedTotal = total.get()
        if (resolvedTotal <= dataSpec.position || bytes.isEmpty()) return null
        return HeadProbeResult(bytes = bytes, total = resolvedTotal)
    }

    private class Attempt(
        val range: CdnByteRange,
        val url: String,
        val permitPool: Semaphore,
        prefix: ByteArray = ByteArray(0),
        val allowClampedEof: Boolean = false,
    ) {
        val bytes = ByteArray(range.length.toInt()).also { prefix.copyInto(it) }

        @Volatile
        var received = prefix.size

        @Volatile
        var rangeValidated = prefix.isNotEmpty()

        @Volatile
        var lastProgressNs = System.nanoTime()

        val startedNs = System.nanoTime()
        val result = CompletableFuture<ByteArray>()

        @Volatile
        var call: Call? = null

        @Volatile
        var status: Int? = null

        val permitReleased = AtomicBoolean(false)

        fun releasePermitOnce() {
            if (permitReleased.compareAndSet(false, true)) {
                permitPool.release()
            }
        }
    }

    private inner class PieceSlot(
        val index: Int,
        val range: CdnByteRange,
        val primaryUrl: String,
    ) {
        val triedUrls = LinkedHashSet<String>()
        val activeAttempts = mutableListOf<Attempt>()
        var prefix: ByteArray = ByteArray(0)
        var completedBytes: ByteArray? = null
        var failedError: IOException? = null
        var round = 0
        var retryAfterNs = 0L
        var lastStatus: Int? = null
        var startedAtNs = 0L
        var dispatched = false
    }

    private inner class StreamingRangeSession(
        dataSpec: DataSpec,
        start: Long,
        length: Long,
        initialTotal: Long,
    ) {
        private val spec = dataSpec
        private val total = AtomicLong(initialTotal)
        private val enhanced = CdnTransferRuntime.isEnhancedMode
        private val targetConnections: Int
        private val pieces: List<PieceSlot>
        private var deliverIndex = 0
        private var nextDispatchIndex = 0
        private val inFlightLimit: Int
        private val rescueReserve: Int

        init {
            val urls = CdnTransferRuntime.candidates(dataSpec.uri.toString())
                .ifEmpty { candidates }
            val limit = if (background) 1 else CdnTransferRuntime.connectionLimit(saturated = false)
            targetConnections = if (background) 1 else limit.coerceAtMost(CdnTransferRuntime.maxConnectionsCap)
            rescueReserve = if (!background && targetConnections >= 8) {
                minOf(4, maxOf(1, targetConnections / 8))
            } else {
                0
            }
            val startupBoundary = if (CdnTransferRuntime.state.value.bufferMs < 5_000L) {
                minOf(CDN_WINDOW_BYTES / 2, length)
            } else {
                0L
            }
            val ranges = if (!enhanced) {
                // Preserve exact conservative window boundaries when operating in default safe mode.
                val firstWindow = if (startupBoundary > 0L) startupBoundary else minOf(CDN_WINDOW_BYTES, length)
                buildList {
                    var offset = 0L
                    while (offset < length) {
                        val windowSize = if (offset == 0L) firstWindow else minOf(CDN_WINDOW_BYTES, length - offset)
                        addAll(splitCdnRange(start + offset, windowSize, targetConnections))
                        offset += windowSize
                    }
                }
            } else {
                splitAdaptiveCdnPieces(
                    start = start,
                    length = length,
                    targetConnections = targetConnections,
                    maxConnections = CdnTransferRuntime.maxConnectionsCap,
                    startupBoundaryBytes = startupBoundary,
                )
            }
            val dispatchPool = if (enhanced) {
                CdnTransferRuntime.selectDispatchCandidates(urls, width = minOf(8, urls.size.coerceAtLeast(1)))
            } else {
                CdnTransferRuntime.rank(urls)
            }
            val assigned = assignPieceUrlsBySpeed(
                pieceCount = ranges.size,
                candidates = dispatchPool.ifEmpty { urls },
                speedOf = { CdnTransferRuntime.nodeSpeedBps(hostFromCdnUrl(it)) },
            )
            pieces = ranges.mapIndexed { index, range ->
                val selected = if (CdnTransferRuntime.state.value.preferredHost != null) {
                    CdnTransferRuntime.rank(urls).firstOrNull() ?: assigned[index]
                } else {
                    assigned[index]
                }
                PieceSlot(index = index, range = range, primaryUrl = selected)
            }
            inFlightLimit = if (background) {
                1
            } else if (!enhanced) {
                // Keep default safe mode bounded to the current window so legacy fallback tests stay exact.
                val firstWindowBoundary = start + (if (startupBoundary > 0L) startupBoundary else minOf(CDN_WINDOW_BYTES, length))
                ranges.count { it.start < firstWindowBoundary }.coerceAtLeast(1)
            } else {
                (targetConnections + 2).coerceIn(2, 34)
            }
        }

        fun nextPieceBytes(): ByteArray? {
            if (deliverIndex >= pieces.size) return null
            val pieceDeadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)

            while (true) {
                if (closed || Thread.currentThread().isInterrupted) {
                    throw IOException("CDN read canceled")
                }
                if (background && CdnTransferRuntime.state.value.bufferMs < CDN_PREFETCH_SAFE_BUFFER_MS) {
                    throw IOException("CDN prefetch yields to playback")
                }

                pumpDispatch()
                evaluateActiveSlots()

                val head = pieces[deliverIndex]
                val ready = head.completedBytes
                if (ready != null) {
                    head.completedBytes = null
                    deliverIndex++
                    pumpDispatch()
                    return ready
                }

                head.failedError?.let { throw it }

                if (System.nanoTime() >= pieceDeadlineNs) {
                    throw IOException("CDN piece timed out")
                }

                val activePending = pieces.subList(deliverIndex, nextDispatchIndex)
                    .flatMap { it.activeAttempts }
                if (activePending.any { it.result.isDone }) continue
                awaitSignal(activePending.map { it.result }, 100L)
            }
        }

        private fun pumpDispatch() {
            val maxAhead = if (!enhanced) {
                // In conservative mode, only advance past the startup window once startup pieces are delivered.
                if (deliverIndex == 0) inFlightLimit else deliverIndex + maxOf(1, targetConnections)
            } else {
                deliverIndex + inFlightLimit
            }
            val capIndex = minOf(pieces.size, maxAhead)
            while (nextDispatchIndex < capIndex) {
                val slot = pieces[nextDispatchIndex]
                if (slot.dispatched) {
                    nextDispatchIndex++
                    continue
                }
                val permitPool = CdnTransferRuntime.permits
                val keepReserve = if (nextDispatchIndex > deliverIndex) rescueReserve else 0
                if (background && permitPool.availablePermits() <= 1) break
                if (permitPool.availablePermits() <= keepReserve) break
                if (!permitPool.tryAcquire()) break

                slot.dispatched = true
                slot.startedAtNs = System.nanoTime()
                slot.triedUrls += slot.primaryUrl
                val attempt = launchAttempt(
                    dataSpec = spec,
                    range = slot.range,
                    url = slot.primaryUrl,
                    total = total,
                    prefix = slot.prefix,
                    reservedPool = permitPool,
                )
                slot.activeAttempts += attempt
                maybeLaunchStartupRace(slot)
                nextDispatchIndex++
            }
        }

        private fun maybeLaunchStartupRace(slot: PieceSlot) {
            if (!enhanced || background || slot.index != 0) return
            if (CdnTransferRuntime.state.value.bufferMs >= 5_000L) return
            val primaryHost = hostFromCdnUrl(slot.primaryUrl)
            val racerUrl = candidatePool().firstOrNull { url ->
                hostFromCdnUrl(url) != primaryHost && url !in slot.triedUrls
            } ?: return
            val pool = CdnTransferRuntime.permits
            if (!pool.tryAcquire()) return
            slot.triedUrls += racerUrl
            slot.activeAttempts += launchAttempt(
                dataSpec = spec,
                range = slot.range,
                url = racerUrl,
                total = total,
                prefix = slot.prefix,
                reservedPool = pool,
            )
        }

        private fun evaluateActiveSlots() {
            val nowNs = System.nanoTime()
            val bufferMs = CdnTransferRuntime.state.value.bufferMs
            val upper = minOf(nextDispatchIndex, pieces.size)

            for (idx in deliverIndex until upper) {
                val slot = pieces[idx]
                if (slot.completedBytes != null || slot.failedError != null) continue

                val winner = slot.activeAttempts.firstOrNull {
                    it.result.isDone && !it.result.isCompletedExceptionally
                }
                if (winner != null) {
                    val bytes = runCatching { winner.result.get() }.getOrNull()
                    if (bytes != null) {
                        slot.activeAttempts.filter { it !== winner }.forEach { it.call?.cancel() }
                        slot.activeAttempts.clear()
                        slot.completedBytes = bytes
                        continue
                    }
                }

                if (slot.retryAfterNs > 0L) {
                    if (nowNs >= slot.retryAfterNs) {
                        slot.retryAfterNs = 0L
                        launchNextCandidateOrRetry(slot)
                    }
                    continue
                }

                if (slot.activeAttempts.isNotEmpty() && slot.activeAttempts.all { it.result.isCompletedExceptionally }) {
                    handleAllSlotAttemptsFailed(slot, nowNs)
                    continue
                }

                if (!background && idx == deliverIndex && slot.activeAttempts.size == 1 && bufferMs < 15_000L) {
                    maybeHedgePrimary(slot, nowNs, bufferMs)
                }
            }
        }

        private fun handleAllSlotAttemptsFailed(slot: PieceSlot, nowNs: Long) {
            slot.lastStatus = slot.activeAttempts.mapNotNull { it.status }.lastOrNull() ?: slot.lastStatus
            val best = slot.activeAttempts
                .filter { it.rangeValidated && it.received > slot.prefix.size && it.received < it.bytes.size }
                .maxByOrNull { it.received }
            if (best != null) {
                val safeLen = minOf(best.received, best.bytes.size - 1)
                if (safeLen > slot.prefix.size) {
                    slot.prefix = best.bytes.copyOf(safeLen)
                }
            }
            slot.activeAttempts.clear()

            val pool = candidatePool()
            val distinctHosts = pool.map(::hostFromCdnUrl).distinct().size
            val terminalSingleHost = distinctHosts <= 1 && slot.lastStatus in setOf(200, 403, 412, 429)
            val maxRounds = if (enhanced) 3 else 1
            if (terminalSingleHost || nowNs - slot.startedAtNs > TimeUnit.SECONDS.toNanos(25)) {
                slot.failedError = IOException("CDN range failed (HTTP ${slot.lastStatus ?: -1})")
                return
            }

            val nextUntried = pool.firstOrNull { it !in slot.triedUrls }
            if (nextUntried != null) {
                launchSlotAttempt(slot, nextUntried)
                if (slot.prefix.isNotEmpty()) CdnTransferRuntime.resumed()
                return
            }

            if (slot.round + 1 < maxRounds) {
                slot.round++
                slot.triedUrls.clear()
                val pauseMs = minOf(2_000L, 500L shl (slot.round - 1))
                slot.retryAfterNs = nowNs + TimeUnit.MILLISECONDS.toNanos(pauseMs)
            } else {
                slot.failedError = IOException("CDN range exhausted")
            }
        }

        private fun launchNextCandidateOrRetry(slot: PieceSlot) {
            val pool = candidatePool()
            val nextUrl = pool.firstOrNull { it !in slot.triedUrls } ?: pool.firstOrNull()
            if (nextUrl == null) {
                slot.failedError = IOException("CDN no candidates available")
                return
            }
            launchSlotAttempt(slot, nextUrl)
            if (slot.prefix.isNotEmpty()) CdnTransferRuntime.resumed()
        }

        private fun launchSlotAttempt(slot: PieceSlot, url: String) {
            val permitPool = CdnTransferRuntime.permits
            if (background && permitPool.availablePermits() <= 1) {
                slot.failedError = IOException("CDN prefetch yields its slot")
                return
            }
            if (!permitPool.tryAcquire()) {
                slot.retryAfterNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50)
                return
            }
            slot.triedUrls += url
            slot.activeAttempts += launchAttempt(
                dataSpec = spec,
                range = slot.range,
                url = url,
                total = total,
                prefix = slot.prefix,
                reservedPool = permitPool,
            )
        }

        private fun maybeHedgePrimary(slot: PieceSlot, nowNs: Long, bufferMs: Long) {
            val primary = slot.activeAttempts.singleOrNull() ?: return
            val primaryHost = hostFromCdnUrl(primary.url)
            val pool = candidatePool()
            val altUrl = pool.firstOrNull {
                hostFromCdnUrl(it) != primaryHost && it !in slot.triedUrls
            } ?: return
            val elapsedMs = ((nowNs - primary.startedNs) / 1_000_000L).coerceAtLeast(1L)
            val sinceProgressMs = ((nowNs - primary.lastProgressNs) / 1_000_000L).coerceAtLeast(0L)
            val receivedSnapshot = primary.received
            val altBps = CdnTransferRuntime.nodeSpeedBps(hostFromCdnUrl(altUrl))
            val shouldHedge = shouldHedgePiece(
                elapsedMs = elapsedMs,
                sinceLastProgressMs = sinceProgressMs,
                receivedBytes = receivedSnapshot,
                pieceLength = primary.bytes.size,
                bufferMs = bufferMs,
                sessionConnectionBps = CdnTransferRuntime.sessionConnectionBps(),
                alternateHostBps = altBps,
            )
            if (!shouldHedge) return
            val permitPool = CdnTransferRuntime.permits
            if (!permitPool.tryAcquire()) return

            val carryPrefix = if (primary.rangeValidated && receivedSnapshot > 0 && receivedSnapshot < primary.bytes.size) {
                primary.bytes.copyOf(minOf(receivedSnapshot, primary.bytes.size - 1))
            } else {
                slot.prefix
            }
            slot.triedUrls += altUrl
            slot.activeAttempts += launchAttempt(
                dataSpec = spec,
                range = slot.range,
                url = altUrl,
                total = total,
                prefix = carryPrefix,
                reservedPool = permitPool,
            )
            CdnTransferRuntime.rescue()
        }

        private fun candidatePool(): List<String> {
            val current = CdnTransferRuntime.candidates(spec.uri.toString()).ifEmpty { candidates }
            return CdnTransferRuntime.rank(current)
        }
    }

    private fun launchAttempt(
        dataSpec: DataSpec,
        range: CdnByteRange,
        url: String,
        total: AtomicLong,
        prefix: ByteArray = ByteArray(0),
        reservedPool: Semaphore? = null,
        allowClampedEof: Boolean = false,
    ): Attempt {
        val pool = reservedPool ?: CdnTransferRuntime.permits.also { sem ->
            if (!sem.tryAcquire()) {
                val failed = Attempt(range, url, sem, prefix, allowClampedEof)
                failed.permitReleased.set(true)
                failed.result.completeExceptionally(IOException("CDN connection budget exhausted"))
                return failed
            }
        }
        val attempt = Attempt(range, url, pool, prefix, allowClampedEof)
        if (closed) {
            attempt.releasePermitOnce()
            attempt.result.completeExceptionally(IOException("CDN read canceled"))
            return attempt
        }
        if (prefix.size >= attempt.bytes.size) {
            attempt.releasePermitOnce()
            attempt.result.complete(attempt.bytes)
            return attempt
        }
        val requested = CdnByteRange(range.start + prefix.size, range.endInclusive)
        val call = try {
            val request = Request.Builder().url(url)
                .header("Referer", "https://www.bilibili.com")
                .header("User-Agent", "Mozilla/5.0")
                .apply { dataSpec.httpRequestHeaders.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=${requested.start}-${requested.endInclusive}")
                .header("Accept-Encoding", "identity")
                .build()
            (client ?: rangeClient).newCall(request)
        } catch (error: IllegalArgumentException) {
            attempt.releasePermitOnce()
            attempt.result.completeExceptionally(IOException("CDN invalid request", error))
            return attempt
        }

        attempt.call = call
        calls += call
        val host = hostFromCdnUrl(url)
        val startedNs = attempt.startedNs
        CdnTransferRuntime.start(host)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                end(call, success = false, canceled = call.isCanceled(), transferredBytes = (attempt.received - prefix.size).toLong(), speedSampleBps = 0L)
                attempt.result.completeExceptionally(e)
                signalWake()
            }

            override fun onResponse(call: Call, response: Response) {
                var success = false
                var failure: Exception? = null
                var finalBytes = attempt.bytes
                var transferredFromNetwork = 0L
                var elapsedMs = 1L
                try {
                    response.use {
                        attempt.status = it.code
                        val contentRange = it.header("Content-Range")
                        val actual = parseCdnContentRange(contentRange)
                        val validRange = if (allowClampedEof && actual != null) {
                            it.code == 206 &&
                                actual.start == requested.start &&
                                actual.end in requested.start..requested.endInclusive &&
                                (actual.end == requested.endInclusive || actual.end == actual.total - 1L)
                        } else {
                            isExactCdnRange(it.code, contentRange, requested)
                        }
                        if (!validRange || actual == null) throw IOException("CDN invalid range")
                        total.compareAndSet(-1, actual.total)
                        if (total.get() != actual.total) throw IOException("CDN mirrors disagree on length")

                        val expectedAttemptTotal = (prefix.size + (actual.end - actual.start + 1L)).toInt()
                        attempt.rangeValidated = true
                        val body = it.body ?: throw IOException("CDN empty body")
                        body.byteStream().use { input ->
                            var firstByteMs: Long? = null
                            while (attempt.received < expectedAttemptTotal) {
                                if (call.isCanceled() || closed) throw IOException("CDN read canceled")
                                val read = input.read(
                                    attempt.bytes,
                                    attempt.received,
                                    expectedAttemptTotal - attempt.received,
                                )
                                if (read < 0) throw IOException("CDN truncated range")
                                if (read == 0) continue
                                attempt.received += read
                                attempt.lastProgressNs = System.nanoTime()
                                transferredFromNetwork = (attempt.received - prefix.size).toLong()
                                elapsedMs = ((attempt.lastProgressNs - startedNs) / 1_000_000).coerceAtLeast(1L)
                                if (firstByteMs == null) firstByteMs = elapsedMs
                                CdnTransferRuntime.progress(
                                    host = host,
                                    bytes = read,
                                    speedBps = transferredFromNetwork * 1_000L / elapsedMs,
                                    firstByteMs = firstByteMs,
                                    sampleBytes = transferredFromNetwork.toInt(),
                                )
                            }
                            if (input.read() != -1) throw IOException("CDN oversized range")
                        }
                        if (expectedAttemptTotal < attempt.bytes.size) {
                            finalBytes = attempt.bytes.copyOf(expectedAttemptTotal)
                        }
                    }
                    success = true
                } catch (error: Exception) {
                    if (attempt.received == attempt.bytes.size) attempt.received = 0
                    failure = error
                } finally {
                    val sampleBps = if (success) transferredFromNetwork * 1_000L / elapsedMs else 0L
                    end(
                        call = call,
                        success = success,
                        canceled = call.isCanceled(),
                        transferredBytes = transferredFromNetwork,
                        speedSampleBps = sampleBps,
                    )
                }
                if (success) {
                    attempt.result.complete(finalBytes)
                } else {
                    attempt.result.completeExceptionally(failure ?: IOException("CDN range failed"))
                }
                signalWake()
            }

            private fun end(
                call: Call,
                success: Boolean,
                canceled: Boolean,
                transferredBytes: Long,
                speedSampleBps: Long,
            ) {
                calls -= call
                attempt.releasePermitOnce()
                CdnTransferRuntime.finish(
                    host = host,
                    success = success,
                    status = attempt.status,
                    canceled = canceled,
                    url = url,
                    receivedBytes = transferredBytes,
                    speedSampleBps = speedSampleBps,
                )
            }
        })
        if (closed) call.cancel()
        return attempt
    }

    private fun awaitSignal(pending: List<CompletableFuture<ByteArray>>, timeoutMs: Long) {
        val wake = wakeSignal.get()
        val futures = (pending + wake).toTypedArray()
        try {
            CompletableFuture.anyOf(*futures).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // Periodic check for cancellation and straggler hedging.
        } catch (_: ExecutionException) {
            // Caller loop handles completed or failed piece futures.
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("CDN read interrupted", error)
        }
        if (wake.isDone) {
            wakeSignal.compareAndSet(wake, CompletableFuture())
        }
    }

    private fun signalWake() {
        wakeSignal.get().complete(Unit)
    }

    private fun cancelCalls() {
        calls.forEach { it.cancel() }
    }

    companion object {
        // Dedicated HTTP/1.1 client so multi-range chunks do not serialize over one HTTP/2 TCP stream.
        private val rangeClient: OkHttpClient by lazy {
            NetworkModule.playbackOkHttpClient.newBuilder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .dispatcher(
                    Dispatcher().apply {
                        maxRequests = 64
                        maxRequestsPerHost = 12
                    }
                )
                .connectionPool(ConnectionPool(32, 3, TimeUnit.MINUTES))
                .cache(null)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
