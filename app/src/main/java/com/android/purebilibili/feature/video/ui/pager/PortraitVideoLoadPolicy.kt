package com.android.purebilibili.feature.video.ui.pager

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.player.PlaybackMediaCache
import com.android.purebilibili.core.player.buildPlaybackCacheKey
import com.android.purebilibili.core.util.MediaUtils
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.getBestVideo
import com.android.purebilibili.data.repository.playableVideoQualityIds
import com.android.purebilibili.plugin.sdk.PlaybackCdnPlugin
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.feature.plugin.applyPlaybackCdnPreference
import com.android.purebilibili.feature.plugin.buildPlaybackCdnCandidates
import com.android.purebilibili.feature.video.viewmodel.buildPlaybackVideoUrlCandidates
import com.android.purebilibili.feature.video.playback.audio.AudioSelectionDecision
import com.android.purebilibili.feature.video.playback.audio.resolveAudioStreamSelection
import com.android.purebilibili.feature.video.viewmodel.buildPlaybackAudioUrlCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.min

/**
 * Fallback when no user preference is available (e.g. unit tests / cold path).
 * Live portrait playback should pass the detail-page playable default quality instead.
 */
internal const val PORTRAIT_PLAYBACK_TARGET_QUALITY = 64
internal const val PORTRAIT_SWIPE_PREFETCH_OFFSET_THRESHOLD = 0.25f
internal const val PORTRAIT_VIDEO_HEAD_PREFETCH_BYTES = 1536L * 1024L
internal const val PORTRAIT_AUDIO_HEAD_PREFETCH_BYTES = 256L * 1024L

internal data class PortraitPagePlaybackIdentity(
    val bvid: String,
    val aid: Long,
    val cid: Long
)

internal data class PortraitPlaybackStreamUrls(
    val videoUrl: String,
    val audioUrl: String?,
    val audioSelection: AudioSelectionDecision? = null,
    val videoQualityId: Int? = null,
)

/**
 * Resolve the playurl qn for portrait pager / Story.
 *
 * Prefer the same playable default used by video detail so Wi‑Fi 1080P / VIP 4K-HDR
 * settings are honored. Cap invalid values to the safe fallback.
 */
internal fun resolvePortraitPlaybackTargetQuality(
    preferredQuality: Int? = null
): Int {
    val quality = preferredQuality ?: return PORTRAIT_PLAYBACK_TARGET_QUALITY
    return quality.takeIf { it > 0 } ?: PORTRAIT_PLAYBACK_TARGET_QUALITY
}

/**
 * Short label for the portrait chrome quality chip.
 * Auto-highest (marker ≥ 127) stays generic because actual track is per-video.
 */
internal fun resolvePortraitQualityLabel(qualityId: Int): String {
    return when {
        qualityId == 129 -> "HDR Vivid"
        qualityId == 100 -> "智能修复"
        qualityId >= 127 -> "自动"
        qualityId >= 126 -> "杜比"
        qualityId >= 125 -> "HDR"
        qualityId >= 120 -> "4K"
        qualityId >= 116 -> "1080P60"
        qualityId >= 112 -> "1080P+"
        qualityId >= 80 -> "1080P"
        qualityId >= 74 -> "720P60"
        qualityId >= 64 -> "720P"
        qualityId >= 32 -> "480P"
        qualityId >= 16 -> "360P"
        else -> "高清"
    }
}

/**
 * Qualities shown in the portrait quality menu.
 * Advertised tiers remain visible; the menu separately enables only actual playable DASH ids.
 */
internal fun resolvePortraitAvailableQualityIds(
    acceptQualities: List<Int>,
    dashVideoIds: List<Int>
): List<Int> {
    return (dashVideoIds + acceptQualities)
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
}

internal fun resolvePortraitQualityMenuLabels(qualityIds: List<Int>): List<String> {
    return qualityIds.map(::resolvePortraitQualityLabel)
}

/**
 * Keep the portrait quality menu useful while its dedicated playurl request is still warming up.
 * The detail player often already has the complete advertised list; if neither source does,
 * expose the universally requestable lower rungs instead of rendering a one-row, non-scrollable menu.
 */
internal fun resolvePortraitQualityMenuIds(
    portraitQualityIds: List<Int>,
    detailQualityIds: List<Int>,
    selectedQualityId: Int,
): List<Int> {
    val merged = (portraitQualityIds + detailQualityIds + selectedQualityId)
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
    if (merged.size > 1) return merged

    val fallbackLowerQualities = listOf(80, 64, 32, 16)
        .filter { it < selectedQualityId }
    return (merged + fallbackLowerQualities)
        .distinct()
        .sortedDescending()
}

/**
 * After a quality switch or reload, pick the label to show from the actual track when possible.
 */
internal fun resolvePortraitDisplayedQualityId(
    requestedQuality: Int,
    returnedQuality: Int,
    dashVideoIds: List<Int>
): Int {
    val available = dashVideoIds.filter { it > 0 }
    if (requestedQuality >= 127) return available.maxOrNull() ?: returnedQuality
    return available.filter { it <= requestedQuality }.maxOrNull()
        ?: available.minOrNull()
        ?: returnedQuality.takeIf { it > 0 }
        ?: PORTRAIT_PLAYBACK_TARGET_QUALITY
}

internal fun shouldUsePortraitParallelPlaybackBootstrap(
    bvid: String,
    requestedCid: Long
): Boolean = bvid.trim().startsWith("BV", ignoreCase = true) && requestedCid > 0L

internal fun resolvePortraitPagePlaybackIdentity(item: Any): PortraitPagePlaybackIdentity? {
    return when (item) {
        is ViewInfo -> {
            val bvid = item.bvid.trim()
            if (bvid.isEmpty()) null
            else PortraitPagePlaybackIdentity(
                bvid = bvid,
                aid = item.aid,
                cid = item.cid
            )
        }

        is RelatedVideo -> {
            val bvid = item.bvid.trim()
            if (bvid.isEmpty()) null
            else PortraitPagePlaybackIdentity(
                bvid = bvid,
                aid = item.aid,
                cid = item.cid
            )
        }

        else -> null
    }
}

internal fun resolvePortraitPageOwnerMid(item: Any): Long {
    return when (item) {
        is ViewInfo -> item.owner.mid
        is RelatedVideo -> item.owner.mid
        else -> 0L
    }.takeIf { it > 0L } ?: 0L
}

/**
 * Story / 竖屏直达 seeds often only carry bvid+cover (no owner). After playurl bootstrap
 * succeeds, merge owner/title/pic from [loaded] so the chrome can render `@UP名`.
 */
internal fun enrichPortraitPageItemWithLoadedInfo(
    existing: Any,
    loaded: ViewInfo
): Any {
    return when (existing) {
        is ViewInfo -> existing.copy(
            aid = loaded.aid.takeIf { it > 0L } ?: existing.aid,
            cid = loaded.cid.takeIf { it > 0L } ?: existing.cid,
            title = loaded.title.ifBlank { existing.title },
            pic = loaded.pic.ifBlank { existing.pic },
            owner = if (loaded.owner.name.isNotBlank() || loaded.owner.mid > 0L) {
                loaded.owner
            } else {
                existing.owner
            },
            stat = if (loaded.stat.view > 0 || loaded.stat.like > 0) loaded.stat else existing.stat,
            pages = loaded.pages.ifEmpty { existing.pages },
            dimension = loaded.dimension ?: existing.dimension,
            ugc_season = loaded.ugc_season ?: existing.ugc_season
        )
        is RelatedVideo -> existing.copy(
            aid = loaded.aid.takeIf { it > 0L } ?: existing.aid,
            cid = loaded.cid.takeIf { it > 0L } ?: existing.cid,
            title = loaded.title.ifBlank { existing.title },
            pic = loaded.pic.ifBlank { existing.pic },
            owner = if (loaded.owner.name.isNotBlank() || loaded.owner.mid > 0L) {
                loaded.owner
            } else {
                existing.owner
            },
            stat = if (loaded.stat.view > 0 || loaded.stat.like > 0) loaded.stat else existing.stat,
            duration = loaded.pages.firstOrNull()?.duration?.toInt()?.takeIf { it > 0 }
                ?: existing.duration
        )
        else -> existing
    }
}

/**
 * Resolve the complete detail model used by portrait chrome.
 *
 * Collection pages are intentionally stored as lightweight [RelatedVideo] items in the pager,
 * so their freshly loaded [ViewInfo] must remain a separate source of truth. Otherwise fields
 * unavailable on [RelatedVideo], especially `ugc_season`, disappear after selecting an episode.
 */
internal fun resolvePortraitDetailInfo(
    targetBvid: String,
    sharedInfo: ViewInfo?,
    loadedPageInfo: ViewInfo?,
    fallbackInfo: ViewInfo?,
): ViewInfo? {
    val matchingSharedInfo = sharedInfo?.takeIf { it.bvid == targetBvid }
    val matchingLoadedInfo = loadedPageInfo?.takeIf { it.bvid == targetBvid }
    val matchingFallbackInfo = fallbackInfo?.takeIf { it.bvid == targetBvid }
    val primaryInfo = matchingSharedInfo ?: matchingLoadedInfo ?: matchingFallbackInfo ?: fallbackInfo
    return primaryInfo?.copy(
        ugc_season = primaryInfo.ugc_season
            ?: matchingLoadedInfo?.ugc_season
            ?: matchingFallbackInfo?.ugc_season,
    )
}

/** Overlay label: never show a bare `@` when seed/owner is still empty. */
internal fun resolvePortraitAuthorDisplayName(authorName: String): String {
    val trimmed = authorName.trim()
    return if (trimmed.isEmpty()) "UP主" else trimmed
}

internal fun resolvePortraitAuthorLabel(authorName: String): String {
    val display = resolvePortraitAuthorDisplayName(authorName)
    return if (display == "UP主") display else "@$display"
}

internal fun resolvePortraitPlaybackStreamUrls(
    playData: PlayUrlData,
    targetQuality: Int = PORTRAIT_PLAYBACK_TARGET_QUALITY,
    isHevcSupported: Boolean = MediaUtils.isHevcSupported(),
    isAv1Supported: Boolean = MediaUtils.isAv1Supported(),
    isDolbyAudioSupported: Boolean = MediaUtils.isDolbyAtmosAudioSupported(),
    isDolbyAudioSoftwareDecoded: Boolean = MediaUtils.isDolbySoftwareAudioDecoderRequired(),
    requestedAudioQuality: Int = -1,
    playbackSpeed: Float = 1.0f,
    preferCodec: String = "hev1",
    secondPreferCodec: String = "avc1",
    videoTrackFilter: (List<DashVideo>) -> List<DashVideo> = { it },
): PortraitPlaybackStreamUrls? {
    val dash = playData.dash
    if (dash != null) {
        val playableVideos = videoTrackFilter(dash.video)
        if (dash.video.isNotEmpty() && playableVideos.isEmpty()) return null
        val dashVideo = dash.copy(video = playableVideos).getBestVideo(
            targetQn = resolvePortraitDisplayedQualityId(
                requestedQuality = targetQuality,
                returnedQuality = playData.quality,
                dashVideoIds = playableVideos.filter { it.getValidUrl().isNotBlank() }.map { it.id }
            ),
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported,
            preferCodec = preferCodec,
            secondPreferCodec = secondPreferCodec,
        )
        val audioSelection = resolveAudioStreamSelection(
            dash = dash,
            requestedAudioQuality = requestedAudioQuality,
            playbackSpeed = playbackSpeed,
            isDolbyAudioSupported = isDolbyAudioSupported,
            isDolbyAudioSoftwareDecoded = isDolbyAudioSoftwareDecoded
        )
        val dashAudio = audioSelection.selected?.track
        val videoUrl = dashVideo?.getValidUrl()?.takeIf { it.isNotEmpty() }
            ?: playData.durl?.firstOrNull()?.url?.takeIf { it.isNotEmpty() }
        if (videoUrl.isNullOrEmpty()) return null
        val audioUrl = dashAudio?.getValidUrl()?.takeIf { it.isNotEmpty() }
        return PortraitPlaybackStreamUrls(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            audioSelection = audioSelection,
            videoQualityId = dashVideo?.id,
        )
    }

    val progressiveUrl = playData.durl?.firstOrNull()?.url?.takeIf { it.isNotEmpty() }
        ?: return null
    return PortraitPlaybackStreamUrls(
        videoUrl = progressiveUrl,
        audioUrl = null
    )
}

internal fun resolvePortraitPlaybackCdnUrls(
    streamUrls: PortraitPlaybackStreamUrls,
    cachedDashVideos: List<DashVideo>,
    cachedDashAudios: List<DashAudio>,
    targetQuality: Int = PORTRAIT_PLAYBACK_TARGET_QUALITY,
    cdnPlugin: PlaybackCdnPlugin?,
    preference: PlaybackCdnPreference = PlaybackCdnPreference.DEFAULT,
    measuredPreference: PlaybackCdnPreference? = com.android.purebilibili.feature.plugin.CdnSpeedTest.recommendedPreference(),
): PortraitPlaybackStreamUrls {
    // Discovery can select a different quality (or codec) than the preference.
    // Only take backups from that exact URL's track, never from the requested tier.
    val selectedTrack = cachedDashVideos.find {
        it.id == targetQuality && it.getValidUrl() == streamUrls.videoUrl
    } ?: cachedDashVideos.find {
        it.getValidUrl() == streamUrls.videoUrl || streamUrls.videoUrl in it.backupUrl.orEmpty()
    }
    val rawVideoUrls = buildPlaybackVideoUrlCandidates(
        videoUrl = streamUrls.videoUrl,
        quality = selectedTrack?.id ?: targetQuality,
        cachedDashVideos = cachedDashVideos,
    )

    val rawAudioUrls = buildPlaybackAudioUrlCandidates(
        audioUrl = streamUrls.audioUrl,
        cachedDashAudios = cachedDashAudios
    )
    val rewrite = cdnPlugin?.rewritePlaybackCandidates(rawVideoUrls, rawAudioUrls)
    val selected = applyPlaybackCdnPreference(
        candidates = rewrite?.candidates ?: buildPlaybackCdnCandidates(rawVideoUrls, rawAudioUrls),
        preference = preference,
        pluginApplied = rewrite != null,
        measuredPreference = measuredPreference,
    ).firstOrNull()
    return streamUrls.copy(
        videoUrl = selected?.videoUrl ?: streamUrls.videoUrl,
        audioUrl = selected?.audioUrl ?: streamUrls.audioUrl,
    )
}

internal fun resolvePortraitSwipePrefetchTargetPage(
    isScrollInProgress: Boolean,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    lastPageIndex: Int,
    prefetchThreshold: Float = PORTRAIT_SWIPE_PREFETCH_OFFSET_THRESHOLD
): Int? {
    if (!isScrollInProgress) return null
    return when {
        currentPageOffsetFraction <= -prefetchThreshold -> {
            val targetPage = currentPage + 1
            targetPage.takeIf { it <= lastPageIndex }
        }

        currentPageOffsetFraction >= prefetchThreshold -> {
            val targetPage = currentPage - 1
            targetPage.takeIf { it >= 0 }
        }

        else -> null
    }
}

internal fun resolvePortraitEarlyPlaybackPage(
    isScrollInProgress: Boolean,
    currentPage: Int,
    lastCommittedPage: Int,
    lastPageIndex: Int,
): Int? {
    if (!isScrollInProgress) return null
    // Pager's currentPage changes as soon as the drag crosses the snap midpoint. Binding that
    // page immediately is both reachable (offset is normally bounded near ±0.5) and stable in
    // either direction; the previous 0.58 offset threshold could never fire.
    if (currentPage == lastCommittedPage) return null
    return currentPage.takeIf { it in 0..lastPageIndex }
}

internal fun resolvePortraitPlayUrlPreloadCount(
    prefetchVideoEnabled: Boolean,
    isWifi: Boolean,
    availableTargets: Int
): Int {
    if (availableTargets <= 0) return 0
    val maxCount = when {
        isWifi && prefetchVideoEnabled -> 3
        isWifi -> 2
        prefetchVideoEnabled -> 1
        else -> 0
    }
    return min(availableTargets, maxCount)
}

internal fun resolvePortraitPlayUrlPreloadTargets(
    committedPage: Int,
    pageItems: List<Any>,
    preloadCount: Int
): List<PortraitPagePlaybackIdentity> {
    if (preloadCount <= 0 || pageItems.isEmpty()) return emptyList()
    val targets = mutableListOf<PortraitPagePlaybackIdentity>()
    var pageIndex = committedPage + 1
    while (targets.size < preloadCount && pageIndex < pageItems.size) {
        resolvePortraitPagePlaybackIdentity(pageItems[pageIndex])?.let { identity ->
            if (targets.none { it.bvid == identity.bvid && it.cid == identity.cid }) {
                targets += identity
            }
        }
        pageIndex += 1
    }
    return targets
}

/** Only the candidate and the forward window remain eligible for background warming. */
internal fun resolvePortraitPrefetchWindowTargets(
    anchorPage: Int,
    pageItems: List<Any>,
    preloadCount: Int,
    swipeTargetPage: Int? = null,
    warmAnchor: Boolean = false,
): List<PortraitPagePlaybackIdentity> {
    if (anchorPage !in pageItems.indices) return emptyList()
    return buildList {
        swipeTargetPage?.let { pageItems.getOrNull(it) }
            ?.let(::resolvePortraitPagePlaybackIdentity)?.let { add(it) }
        if (warmAnchor) {
            pageItems.getOrNull(anchorPage)?.let(::resolvePortraitPagePlaybackIdentity)?.let { add(it) }
        }
        addAll(resolvePortraitPlayUrlPreloadTargets(anchorPage, pageItems, preloadCount))
    }.distinctBy { resolvePortraitMediaId(it.bvid, it.cid) }
}

internal fun buildPortraitPlaybackHttpHeaders(): Map<String, String> {
    return mapOf(
        "Referer" to "https://www.bilibili.com",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )
}

/** Warms only the opening media ranges so the next page can start from disk cache. */
@UnstableApi
internal suspend fun prefetchPortraitPlaybackHead(
    context: Context,
    streamUrls: PortraitPlaybackStreamUrls
) {
    // 头部预取是纯加速项：蜂窝网络下跳过，滑动多页累积的流量不小。
    if (!com.android.purebilibili.core.util.NetworkUtils.isWifi(context)) return
    coroutineScope {
        val headers = buildPortraitPlaybackHttpHeaders()
        buildList {
            add(streamUrls.videoUrl to PORTRAIT_VIDEO_HEAD_PREFETCH_BYTES)
            streamUrls.audioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->
                add(audioUrl to PORTRAIT_AUDIO_HEAD_PREFETCH_BYTES)
            }
        }.map { (url, length) ->
            async(Dispatchers.IO) {
                val (upstreamFactory, cancelUpstream) = PlaybackMediaCache.buildCancelableOkHttpDataSourceFactory(
                    callFactory = NetworkModule.playbackOkHttpClient,
                    defaultRequestProperties = headers,
                )
                val uri = Uri.parse(url)
                PlaybackMediaCache.prefetchRange(
                    context = context.applicationContext,
                    upstreamFactory = upstreamFactory,
                    url = uri,
                    cacheKey = buildPlaybackCacheKey(uri = uri, explicitKey = null),
                    position = 0L,
                    length = length,
                    onCancelUpstream = cancelUpstream,
                )
            }
        }.awaitAll()
    }
}

@UnstableApi
internal fun buildPortraitCachedMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
    val headers = buildPortraitPlaybackHttpHeaders()
    val upstreamFactory = com.android.purebilibili.core.player.PlaybackHttpDataSourceFactory.create(
        context = context,
        fallbackClient = NetworkModule.playbackOkHttpClient,
        headers = headers,
    )
    val dataSourceFactory: DataSource.Factory =
        PlaybackMediaCache.buildCachedDataSourceFactory(context, upstreamFactory)
    return DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)
}
