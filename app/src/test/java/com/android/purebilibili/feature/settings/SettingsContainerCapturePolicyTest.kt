package com.android.purebilibili.feature.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsContainerCapturePolicyTest {
    @Test
    fun cardDepthScalingDoesNotInvalidateTheCapturedOriginButRealScrollingDoes() {
        val captured = Rect(16f, 200f, 384f, 280f)
        val host = IntSize(400, 900)
        val scaled = Rect(21.52f, 207.5f, 378.48f, 285.1f)
        kotlin.test.assertTrue(matchesSettingsContainerSourceBounds(captured, captured, host, 0.97f))
        kotlin.test.assertTrue(matchesSettingsContainerSourceBounds(scaled, captured, host, 0.97f))
        kotlin.test.assertFalse(matchesSettingsContainerSourceBounds(scaled.translate(Offset(0f, 20f)), captured, host, 0.97f))
        kotlin.test.assertFalse(matchesSettingsContainerSourceBounds(scaled, captured, host, 1f))
    }

    @Test
    fun aMeasuredRowSuppliesTheFullExpansionOriginInHostCoordinates() {
        assertEquals(
            Rect(16f, 200f, 384f, 280f),
            resolveSettingsContainerCaptureBounds(
                Rect(16f, 224f, 384f, 304f), IntSize(368, 80),
                Offset(0f, 24f), IntSize(400, 900),
            ),
        )
    }

    @Test
    fun missingOrClippedMeasurementsDoNotProduceAWrongOrigin() {
        val rowSize = IntSize(368, 80)
        val hostSize = IntSize(400, 900)
        assertNull(resolveSettingsContainerCaptureBounds(null, rowSize, Offset.Zero, hostSize))
        assertNull(resolveSettingsContainerCaptureBounds(Rect(16f, 0f, 384f, 40f), rowSize, Offset.Zero, hostSize))
        assertNull(resolveSettingsContainerCaptureBounds(Rect(16f, 860f, 384f, 940f), rowSize, Offset.Zero, hostSize))
    }

    @Test
    fun recordedContentCardRetainsFullBoundsWhenClippedByViewportOrBottomBar() {
        val hostOrigin = Offset(0f, 24f)
        val hostSize = IntSize(400, 900)
        val cardSize = IntSize(368, 160)
        for ((full, visible) in listOf(
            Rect(16f, 824f, 384f, 984f) to Rect(16f, 824f, 384f, 844f),
            Rect(16f, -56f, 384f, 104f) to Rect(16f, 64f, 384f, 104f),
        )) {
            assertEquals(
                full.translate(-hostOrigin),
                resolveSettingsContainerCaptureBounds(
                    full, cardSize, hostOrigin, hostSize,
                    allowPartial = true, visibleBoundsInRoot = visible,
                ),
            )
            // 同一裁切来源没有完整像素时，不能把局部截图当成整卡。
            assertNull(resolveSettingsContainerCaptureBounds(
                full, cardSize, hostOrigin, hostSize,
                visibleBoundsInRoot = visible,
            ))
        }
    }

    @Test
    fun recordedContentCardStillRequiresAVisiblePortionAndFullGeometry() {
        val full = Rect(16f, 860f, 384f, 1020f)
        val cardSize = IntSize(368, 160)
        val hostSize = IntSize(400, 900)
        for (visible in listOf(null, Rect(16f, 860f, 384f, 860f), Rect(16f, 920f, 384f, 1020f))) {
            assertNull(resolveSettingsContainerCaptureBounds(
                full, cardSize, Offset.Zero, hostSize,
                allowPartial = true, visibleBoundsInRoot = visible,
            ))
        }
        assertNull(resolveSettingsContainerCaptureBounds(
            Rect(16f, 860f, 384f, 900f), cardSize, Offset.Zero, hostSize,
            allowPartial = true,
        ))
    }

    @Test
    fun partiallyVisibleCardReturnAcceptsDepthScalingButRejectsRealScrolling() {
        val captured = Rect(16f, 860f, 384f, 1020f)
        val host = IntSize(400, 900)
        val center = Offset(200f, 450f)
        val scaled = Rect(
            center + (captured.topLeft - center) * 0.97f,
            center + (captured.bottomRight - center) * 0.97f,
        )
        kotlin.test.assertTrue(matchesSettingsContainerSourceBounds(scaled, captured, host, 0.97f))
        kotlin.test.assertFalse(matchesSettingsContainerSourceBounds(
            scaled.translate(Offset(0f, -20f)), captured, host, 0.97f,
        ))
    }

    @Test
    fun nestedNavigationRowsResolveToTheirActualDestination() {
        assertEquals(BiliPaiNavKey.AnimationSettings, resolveSettingsContainerTarget(SettingsSearchTarget.ANIMATION))
        assertEquals(BiliPaiNavKey.BottomBarSettings, resolveSettingsContainerTarget(SettingsSearchTarget.NAVIGATION))
        assertEquals(BiliPaiNavKey.PlaybackSettings, resolveSettingsContainerTarget(SettingsSearchTarget.FULLSCREEN_GESTURE))
        assertEquals(BiliPaiNavKey.WebDavBackup, resolveSettingsContainerTarget(SettingsSearchTarget.WEBDAV_BACKUP))
    }

    @Test
    fun localActionsDoNotArmAPageExpansion() {
        for (target in listOf(
            SettingsSearchTarget.DOWNLOAD_PATH, SettingsSearchTarget.IMAGE_SAVE_PATH,
            SettingsSearchTarget.CLEAR_CACHE, SettingsSearchTarget.EXPORT_LOGS,
            SettingsSearchTarget.OPEN_LINKS, SettingsSearchTarget.CHECK_UPDATE,
        )) assertNull(resolveSettingsContainerTarget(target))
    }
}
