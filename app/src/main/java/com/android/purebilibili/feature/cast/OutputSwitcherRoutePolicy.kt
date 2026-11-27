package com.android.purebilibili.feature.cast

import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.os.Build
import com.android.purebilibili.feature.nearby.NearbyDeviceFormFactor
import java.io.IOException

internal const val FEATURE_BILIPAI_DLNA_CAST = "com.android.purebilibili.route.FEATURE_DLNA_CAST"
internal const val FEATURE_BILIPAI_NEARBY_HANDOFF = "com.android.purebilibili.route.FEATURE_NEARBY_HANDOFF"

internal const val ROUTE_ID_PREFIX_DLNA = "dlna:"
internal const val ROUTE_ID_PREFIX_NEARBY = "nearby:"

internal sealed interface OutputSwitcherRouteTarget {
    data class Dlna(
        val usn: String,
        val location: String,
        val friendlyName: String,
    ) : OutputSwitcherRouteTarget

    data class Nearby(
        val pin: String,
        val deviceName: String,
    ) : OutputSwitcherRouteTarget
}

internal fun buildDlnaRouteId(usn: String): String = "$ROUTE_ID_PREFIX_DLNA${usn.trim()}"

internal fun buildNearbyRouteId(pin: String): String = "$ROUTE_ID_PREFIX_NEARBY${pin.trim()}"

internal fun parseOutputSwitcherRouteId(
    routeId: String,
    dlnaLookup: (usn: String) -> OutputSwitcherRouteTarget.Dlna?,
    nearbyLookup: (pin: String) -> OutputSwitcherRouteTarget.Nearby?,
): OutputSwitcherRouteTarget? = when {
    routeId.startsWith(ROUTE_ID_PREFIX_DLNA) -> dlnaLookup(routeId.removePrefix(ROUTE_ID_PREFIX_DLNA))
    routeId.startsWith(ROUTE_ID_PREFIX_NEARBY) -> nearbyLookup(routeId.removePrefix(ROUTE_ID_PREFIX_NEARBY))
    else -> null
}

internal fun resolveNearbyRouteDeviceType(
    formFactor: NearbyDeviceFormFactor,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Int {
    if (sdkInt < Build.VERSION_CODES.R) return 0
    return when (formFactor) {
        NearbyDeviceFormFactor.TV -> MediaRoute2Info.TYPE_REMOTE_TV
        NearbyDeviceFormFactor.TABLET -> {
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                MediaRoute2Info.TYPE_REMOTE_TABLET
            } else {
                MediaRoute2Info.TYPE_REMOTE_SPEAKER
            }
        }
        NearbyDeviceFormFactor.PHONE -> MediaRoute2Info.TYPE_REMOTE_SPEAKER
    }
}

internal fun shouldDiscoverOutputSwitcherRoutes(
    preferredFeatures: Collection<String>,
    hasLocalNetworkPermission: Boolean,
): Boolean {
    if (!hasLocalNetworkPermission) return false
    if (preferredFeatures.isEmpty()) return false
    return preferredFeatures.any { feature ->
        feature == FEATURE_BILIPAI_DLNA_CAST ||
            feature == FEATURE_BILIPAI_NEARBY_HANDOFF ||
            feature == MediaRoute2Info.FEATURE_REMOTE_PLAYBACK ||
            feature == MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK ||
            feature == MediaRoute2Info.FEATURE_REMOTE_VIDEO_PLAYBACK
    }
}

internal fun resolveOutputSwitcherFailureReason(
    error: Throwable?,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Int {
    if (sdkInt < Build.VERSION_CODES.R) return 0
    return when (error) {
        is NoSuchElementException -> MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE
        is IOException -> MediaRoute2ProviderService.REASON_NETWORK_ERROR
        else -> MediaRoute2ProviderService.REASON_REJECTED
    }
}
