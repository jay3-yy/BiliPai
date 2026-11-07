package com.android.purebilibili.feature.video

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDetailOpeningSeedTest {
    @Test
    fun relatedRowExcludesTitleAndStatisticsToTheRightOfCover() {
        assertEquals(
            IntRect(0, 0, 320, 200),
            resolveVideoDetailOpeningSeedCrop(
                Rect(20f, 100f, 1020f, 300f), Rect(20f, 100f, 340f, 300f),
                VideoCardSourceLayout.SIDE_BY_SIDE, 1000, 200,
            ),
        )
    }

    @Test
    fun stackedCardExcludesInformationBelowCover() {
        assertEquals(
            IntRect(0, 0, 400, 225),
            resolveVideoDetailOpeningSeedCrop(
                Rect(50f, 80f, 450f, 680f), Rect(50f, 80f, 450f, 305f),
                VideoCardSourceLayout.STACKED, 400, 600,
            ),
        )
    }

    @Test
    fun measuredCoverInsetsMapToBitmapPixelsRatherThanScreenCoordinates() {
        assertEquals(
            IntRect(24, 20, 424, 270),
            resolveVideoDetailOpeningSeedCrop(
                Rect(100f, 200f, 600f, 500f), Rect(112f, 210f, 312f, 335f),
                VideoCardSourceLayout.SIDE_BY_SIDE, 1000, 600,
            ),
        )
    }

    @Test
    fun missingCoverGeometryOnlyAllowsKnownCoverOnlyCards() {
        for (layout in listOf(VideoCardSourceLayout.SIDE_BY_SIDE, VideoCardSourceLayout.STACKED)) {
            assertNull(resolveVideoDetailOpeningSeedCrop(Rect(0f, 0f, 400f, 600f), null, layout, 400, 600))
        }
        assertEquals(
            IntRect(0, 0, 400, 225),
            resolveVideoDetailOpeningSeedCrop(null, null, VideoCardSourceLayout.COVER_ONLY, 400, 225),
        )
    }

    @Test
    fun coverOutsideCardCannotBecomeAnInvalidPainterRegion() {
        assertEquals(
            IntRect(0, 0, 300, 200),
            resolveVideoDetailOpeningSeedCrop(
                Rect(0f, 0f, 400f, 200f), Rect(-1f, -1f, 300f, 201f),
                VideoCardSourceLayout.SIDE_BY_SIDE, 400, 200,
            ),
        )
        assertNull(
            resolveVideoDetailOpeningSeedCrop(
                Rect(0f, 0f, 400f, 200f), Rect(410f, 0f, 500f, 200f),
                VideoCardSourceLayout.SIDE_BY_SIDE, 400, 200,
            ),
        )
    }
}
