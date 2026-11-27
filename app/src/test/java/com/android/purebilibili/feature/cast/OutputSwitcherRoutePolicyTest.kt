package com.android.purebilibili.feature.cast

import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.os.Build
import com.android.purebilibili.feature.nearby.NearbyDeviceFormFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OutputSwitcherRoutePolicyTest {

    @Test
    fun discoveryGateRequiresBothLocalNetworkPermissionAndRelevantFeature() {
        assertFalse(
            shouldDiscoverOutputSwitcherRoutes(
                preferredFeatures = listOf(FEATURE_BILIPAI_DLNA_CAST),
                hasLocalNetworkPermission = false,
            )
        )
        assertFalse(
            shouldDiscoverOutputSwitcherRoutes(
                preferredFeatures = emptyList(),
                hasLocalNetworkPermission = true,
            )
        )
        assertFalse(
            shouldDiscoverOutputSwitcherRoutes(
                preferredFeatures = listOf("com.example.unrelated"),
                hasLocalNetworkPermission = true,
            )
        )
        assertTrue(
            shouldDiscoverOutputSwitcherRoutes(
                preferredFeatures = listOf(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK),
                hasLocalNetworkPermission = true,
            )
        )
    }

    @Test
    fun routeIdsParseBackToDlnaAndNearbyTargets() {
        val dlna = OutputSwitcherRouteTarget.Dlna("uuid:123", "http://192.168.1.5:8080/desc.xml", "客厅电视")
        val nearby = OutputSwitcherRouteTarget.Nearby("abc123", "我的平板")

        assertEquals(
            dlna,
            parseOutputSwitcherRouteId(
                routeId = buildDlnaRouteId("uuid:123"),
                dlnaLookup = { if (it == "uuid:123") dlna else null },
                nearbyLookup = { null },
            ),
        )
        assertEquals(
            nearby,
            parseOutputSwitcherRouteId(
                routeId = buildNearbyRouteId("abc123"),
                dlnaLookup = { null },
                nearbyLookup = { if (it == "abc123") nearby else null },
            ),
        )
        assertNull(
            parseOutputSwitcherRouteId(
                routeId = "unknown:1",
                dlnaLookup = { dlna },
                nearbyLookup = { nearby },
            ),
        )
    }

    @Test
    fun nearbyDeviceTypeMapsTabletOnApi34PlusAndFallsBackBelow() {
        assertEquals(
            MediaRoute2Info.TYPE_REMOTE_TV,
            resolveNearbyRouteDeviceType(NearbyDeviceFormFactor.TV, Build.VERSION_CODES.R),
        )
        assertEquals(
            MediaRoute2Info.TYPE_REMOTE_TABLET,
            resolveNearbyRouteDeviceType(NearbyDeviceFormFactor.TABLET, Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
        assertEquals(
            MediaRoute2Info.TYPE_REMOTE_SPEAKER,
            resolveNearbyRouteDeviceType(NearbyDeviceFormFactor.TABLET, Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun failureReasonMapsOfflineNetworkAndRejectedErrors() {
        assertEquals(
            MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE,
            resolveOutputSwitcherFailureReason(NoSuchElementException("offline"), Build.VERSION_CODES.R),
        )
        assertEquals(
            MediaRoute2ProviderService.REASON_NETWORK_ERROR,
            resolveOutputSwitcherFailureReason(IOException("timeout"), Build.VERSION_CODES.R),
        )
        assertEquals(
            MediaRoute2ProviderService.REASON_REJECTED,
            resolveOutputSwitcherFailureReason(IllegalStateException("cancelled"), Build.VERSION_CODES.R),
        )
    }
}
