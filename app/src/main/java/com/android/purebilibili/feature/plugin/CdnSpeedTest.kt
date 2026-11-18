package com.android.purebilibili.feature.plugin

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidate
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidateSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val SAMPLE_BVID = "BV1fK4y1t7hj"
private const val SAMPLE_CID = 196018899L
internal const val CDN_SPEED_TEST_SAMPLE_BYTES = 512 * 1024
private const val RESULT_TTL_MS = 30 * 60_000L

internal data class CdnSpeedResult(
    val preference: PlaybackCdnPreference,
    val bytesPerSecond: Long?,
    val responseMs: Long?,
)

internal sealed interface CdnSpeedTestUpdate {
    data object Preparing : CdnSpeedTestUpdate
    data class Testing(
        val preference: PlaybackCdnPreference,
        val tested: List<CdnSpeedResult>,
        val total: Int,
        val downloaded: Int = 0,
        val bytesPerSecond: Long? = null,
    ) : CdnSpeedTestUpdate
    data object Finished : CdnSpeedTestUpdate
    data class Failed(val message: String) : CdnSpeedTestUpdate
}

internal fun rankCdnSpeedResults(results: List<CdnSpeedResult>): List<CdnSpeedResult> =
    results.sortedWith(
        compareByDescending<CdnSpeedResult> { it.bytesPerSecond }
            .thenBy { it.responseMs },
    )

internal object CdnSpeedTest {
    private data class Report(
        val network: Network,
        val timeMs: Long,
        val results: List<CdnSpeedResult>,
    )

    // The current network and sample speeds are process-local. Signed playback URLs are never retained.
    private val report = MutableStateFlow<Report?>(null)
    private val measurement = Mutex()

    fun currentResults(context: Context): List<CdnSpeedResult>? {
        val cached = report.value ?: return null
        return resultsForNetwork(cached, context.getSystemService(ConnectivityManager::class.java)?.activeNetwork)
    }

    fun recommendedPreference(): PlaybackCdnPreference? {
        if (report.value == null) return null
        val context = NetworkModule.appContext ?: return null
        return currentResults(context)?.firstOrNull()?.takeIf { it.bytesPerSecond != null }?.preference
    }

    fun observeResults(context: Context) = callbackFlow<Network?> {
        val manager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(network) }
            override fun onLost(network: Network) { trySend(manager.activeNetwork) }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(manager.activeNetwork)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.combine(report) { network, cached -> resultsForNetwork(cached, network).orEmpty() }

    private fun resultsForNetwork(cached: Report?, network: Network?): List<CdnSpeedResult>? =
        cached?.results?.takeIf {
            network == cached.network && SystemClock.elapsedRealtime() - cached.timeMs < RESULT_TTL_MS
        }

    fun measure(context: Context) = channelFlow<CdnSpeedTestUpdate> {
        measurement.withLock { measureCurrentNetwork(context) }
    }

    private suspend fun ProducerScope<CdnSpeedTestUpdate>.measureCurrentNetwork(context: Context) {
        send(CdnSpeedTestUpdate.Preparing)
        val connectivity = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
        val network = connectivity.activeNetwork
        if (network == null) {
            send(CdnSpeedTestUpdate.Failed("未连接网络"))
            return
        }
        val data = try {
            VideoRepository.getInitialPlayUrlData(SAMPLE_BVID, SAMPLE_CID, targetQuality = 32)
        } catch (_: IOException) {
            null
        }
        val sample = data?.dash?.video?.firstOrNull { track ->
            (listOf(track.baseUrl) + track.backupUrl.orEmpty()).any(::isHostPortableCdnUrl)
        }
        if (sample == null) {
            send(CdnSpeedTestUpdate.Failed("无法获取测速视频流"))
            return
        }
        val urls = (listOf(sample.baseUrl) + sample.backupUrl.orEmpty())
            .filter { it.startsWith("https://") }
        val candidates = urls.map { PlaybackCdnCandidate(it, null, PlaybackCdnCandidateSource.ORIGINAL) }
        val targets = PlaybackCdnPreference.entries
            .filter { it != PlaybackCdnPreference.AUTO }
            // Prefer a named line when it and the sample's base URL use the same server.
            .sortedByDescending { it.host != null }
            .mapNotNull { preference ->
                val url = applyPlaybackCdnPreference(candidates, preference).firstOrNull()?.videoUrl
                    ?.takeIf { !isRiskyCdnUrl(it) && (preference.host == null || preference.host == hostFromCdnUrl(it)) }
                    ?: return@mapNotNull null
                preference to url
            }
            .distinctBy { (_, url) -> hostFromCdnUrl(url) }
        if (targets.isEmpty()) {
            send(CdnSpeedTestUpdate.Failed("测速视频没有可用线路"))
            return
        }
        val client = NetworkModule.playbackOkHttpClient.newBuilder()
            .cache(null)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
        val range = CdnByteRange(0L, CDN_SPEED_TEST_SAMPLE_BYTES - 1L)
        var results = emptyList<CdnSpeedResult>()
        for ((preference, url) in targets) {
            if (connectivity.activeNetwork != network) {
                send(CdnSpeedTestUpdate.Failed("网络已切换，请重新测速"))
                return
            }
            val current = CdnSpeedTestUpdate.Testing(preference, results, targets.size)
            send(current)
            val result = try {
                val measured = readExactCdnRange(client, url, range) { bytes, elapsedMs ->
                    trySend(current.copy(downloaded = bytes, bytesPerSecond = bytes * 1_000L / elapsedMs))
                }
                CdnSpeedResult(preference, measured.bytes.size * 1_000L / measured.elapsedMs, measured.firstByteMs)
            } catch (_: IOException) {
                CdnSpeedResult(preference, null, null)
            }
            results = results + result
        }
        if (connectivity.activeNetwork != network) {
            send(CdnSpeedTestUpdate.Failed("网络已切换，请重新测速"))
            return
        }
        val ranked = rankCdnSpeedResults(results)
        report.value = Report(network, SystemClock.elapsedRealtime(), ranked)
        send(CdnSpeedTestUpdate.Finished)
    }
}
