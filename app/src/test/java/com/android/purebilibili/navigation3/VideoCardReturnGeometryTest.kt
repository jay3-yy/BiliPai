package com.android.purebilibili.navigation3

import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCardReturnGeometryTest {
    private val session = VideoCardTransitionSession(
        bvid = "BV1source", sourceRoute = "home", sourceKey = "home:BV1source",
        cardBounds = Rect(0f, 0f, 100f, 200f), coverBounds = Rect(0f, 0f, 100f, 60f),
        infoBounds = Rect(0f, 60f, 100f, 200f), sourceCornerDp = 12,
        cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT, coverIdentity = "cover",
        cardFullyVisible = true, isSingleColumnCard = false,
        sourceLayout = VideoCardSourceLayout.STACKED, sourceChromeSnapshot = null,
    )

    @Test
    fun movedAndResizedCardPreservesMeasuredMediaAndInfoRegions() {
        val refreshed = session.withReturnBounds(Rect(20f, 40f, 220f, 440f))
        assertEquals(Rect(20f, 40f, 220f, 160f), refreshed.coverBounds)
        assertEquals(Rect(20f, 160f, 220f, 440f), refreshed.infoBounds)
        assertEquals(session.sourceKey, refreshed.sourceKey)
    }

    @Test
    fun missingOriginalCardDisablesMorphInsteadOfReusingClickCoordinates() {
        val refreshed = session.withReturnBounds(null)
        assertFalse(refreshed.hasUsableSourceGeometry)
        assertNull(refreshed.cardBounds)
        assertNull(refreshed.coverBounds)
        assertNull(refreshed.infoBounds)
    }
}
