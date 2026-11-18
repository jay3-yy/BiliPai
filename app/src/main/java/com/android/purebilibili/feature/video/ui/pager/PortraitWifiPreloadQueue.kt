package com.android.purebilibili.feature.video.ui.pager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.core.util.VideoTrackCapabilities
import com.android.purebilibili.plugin.sdk.PlaybackCdnPlugin
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.feature.plugin.CdnSpeedTest
import com.android.purebilibili.feature.video.playback.audio.collectAudioStreamCandidates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

internal fun portraitWifiAvailability(context: Context): Flow<Boolean> = callbackFlow {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    fun publish() {
        trySend(NetworkUtils.isWifi(context) && !NetworkUtils.isMobileData(context))
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
        override fun onLost(network: Network) = publish()
    }
    try {
        manager.registerDefaultNetworkCallback(callback)
        publish()
    } catch (_: SecurityException) {
        trySend(false)
        close()
        return@callbackFlow
    }
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

internal data class PortraitPreparedPlayback(
    val info: ViewInfo,
    val playData: PlayUrlData,
    val mediaId: String,
    val source: MediaSource,
    val quality: Int,
    val audioQuality: Int,
    val speed: Float,
    val preferCodec: String,
    val secondPreferCodec: String,
    val cdnPreference: PlaybackCdnPreference,
    val measuredCdnPreference: PlaybackCdnPreference?,
)

/** The calling effect owns warm() and its cancellation. All player operations stay on Main. */
@UnstableApi
internal class PortraitWifiPreloadQueue(
    private val context: Context,
    private val player: ExoPlayer,
    private val factory: DefaultMediaSourceFactory,
    private val cdnPlugin: PlaybackCdnPlugin?,
) {
    private val originalPreloadConfiguration = player.preloadConfiguration
    private val originalPauseAtEnd = player.pauseAtEndOfMediaItems
    private var prepared: PortraitPreparedPlayback? = null
    private var claimedMediaId: String? = null
    private var closed = false

    suspend fun warm(
        identity: PortraitPagePlaybackIdentity,
        quality: Int,
        audioQuality: Int,
        speed: Float,
        preferCodec: String,
        secondPreferCodec: String,
        cdnPreference: PlaybackCdnPreference,
        onPreparedInfo: (ViewInfo) -> Unit,
    ) {
        if (closed || !NetworkUtils.isWifi(context) || NetworkUtils.isMobileData(context) || player.mediaItemCount != 1) return
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        var owned: PortraitPreparedPlayback? = null
        try {
            val (info, data) = withContext(Dispatchers.IO) {
                VideoRepository.getPortraitPlaybackDetails(identity.bvid, identity.aid, identity.cid, quality)
            }.getOrNull() ?: return
            val streams = withContext(Dispatchers.Default) {
                resolvePortraitPlaybackStreamUrls(
                    data, quality, requestedAudioQuality = audioQuality, playbackSpeed = speed,
                    preferCodec = preferCodec, secondPreferCodec = secondPreferCodec,
                    videoTrackFilter = { VideoTrackCapabilities.filterForPlayback(context, it) },
                )
            } ?: return
            if (!NetworkUtils.isWifi(context) || NetworkUtils.isMobileData(context) ||
                player.currentMediaItem?.mediaId != currentMediaId || player.mediaItemCount != 1
            ) return
            val measured = if (cdnPreference == PlaybackCdnPreference.AUTO) CdnSpeedTest.recommendedPreference() else null
            val urls = resolvePortraitPlaybackCdnUrls(
                streams, data.dash?.video.orEmpty(),
                data.dash?.let(::collectAudioStreamCandidates)?.map { it.track }.orEmpty(), quality, cdnPlugin, cdnPreference,
                measuredPreference = measured,
            )
            val mediaId = resolvePortraitMediaId(identity.bvid, info.cid.takeIf { it > 0L } ?: identity.cid)
            val video = factory.createMediaSource(MediaItem.Builder().setUri(urls.videoUrl).setMediaId(mediaId).build())
            val source = urls.audioUrl?.takeIf { it.isNotBlank() }?.let { audio ->
                MergingMediaSource(video, factory.createMediaSource(
                    MediaItem.Builder().setUri(audio).setMediaId("audio_$mediaId").build()
                ))
            } ?: video
            owned = PortraitPreparedPlayback(
                info, data, mediaId, source, quality, audioQuality, speed, preferCodec, secondPreferCodec, cdnPreference, measured,
            )
            // Buffer the next item without letting the internal playlist advance ahead of the UI.
            player.setPauseAtEndOfMediaItems(true)
            player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(3_000_000L))
            player.addMediaSource(source)
            prepared = owned
            onPreparedInfo(info)
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            com.android.purebilibili.core.util.Logger.w("PortraitWifiPreload", "下一条准备未完成: ${error.message}")
        } finally {
            owned?.let { release(it) }
        }
    }

    fun claim(
        identity: PortraitPagePlaybackIdentity,
        quality: Int,
        audioQuality: Int,
        speed: Float,
        preferCodec: String,
        secondPreferCodec: String,
        cdnPreference: PlaybackCdnPreference,
    ): PortraitPreparedPlayback? {
        if (closed) return null
        val candidate = prepared ?: return null
        val measured = if (cdnPreference == PlaybackCdnPreference.AUTO) CdnSpeedTest.recommendedPreference() else null
        if (candidate.mediaId.substringBefore('#') != identity.bvid ||
            (identity.cid > 0L && candidate.info.cid != identity.cid) ||
            candidate.quality != quality || candidate.audioQuality != audioQuality || candidate.speed != speed ||
            candidate.preferCodec != preferCodec || candidate.secondPreferCodec != secondPreferCodec ||
            candidate.cdnPreference != cdnPreference ||
            candidate.measuredCdnPreference != measured ||
            player.playerError != null || indexOf(candidate.mediaId) < 0
        ) return null
        claimedMediaId = candidate.mediaId
        return candidate
    }

    fun playClaimed(candidate: PortraitPreparedPlayback, positionMs: Long): Boolean {
        if (closed) return false
        val index = indexOf(candidate.mediaId)
        if (index < 0 || claimedMediaId != candidate.mediaId) return false
        player.seekTo(index, positionMs.coerceAtLeast(0L))
        // Keep a single foreground item; removing preceding items retains its prepared period.
        if (index > 0) player.removeMediaItems(0, index)
        claimedMediaId = null
        prepared = null
        restoreConfiguration()
        return true
    }

    fun clear(force: Boolean = false) {
        if (closed) return
        if (force) {
            claimedMediaId?.let { mediaId ->
                val index = indexOf(mediaId)
                if (index >= 0 && index != player.currentMediaItemIndex) player.removeMediaItem(index)
            }
            claimedMediaId = null
        }
        prepared?.let { release(it) }
        if (claimedMediaId == null) restoreConfiguration()
    }

    private fun release(candidate: PortraitPreparedPlayback) {
        if (closed) return
        if (prepared === candidate) prepared = null
        if (claimedMediaId == candidate.mediaId) return
        val index = indexOf(candidate.mediaId)
        if (index >= 0 && index != player.currentMediaItemIndex) player.removeMediaItem(index)
        restoreConfiguration()
    }

    private fun indexOf(mediaId: String): Int =
        (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId } ?: -1

    private fun restoreConfiguration() {
        player.setPreloadConfiguration(originalPreloadConfiguration)
        player.setPauseAtEndOfMediaItems(originalPauseAtEnd)
    }

    fun close() {
        if (closed) return
        clear(force = true)
        closed = true
    }
}
