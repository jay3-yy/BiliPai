package com.android.purebilibili.feature.home.components.cards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCardLongPressPolicyTest {

    @Test
    fun shouldOpenLongPressMenu_opensWhenNoPreviewAndMenuAvailable() {
        assertTrue(shouldOpenLongPressMenu(hasPreviewAction = false, hasMenuAction = true))
    }

    @Test
    fun shouldOpenLongPressMenu_notOpenWhenPreviewActionExists() {
        assertFalse(shouldOpenLongPressMenu(hasPreviewAction = true, hasMenuAction = true))
    }

    @Test
    fun shouldOpenLongPressMenu_notOpenWhenMenuUnavailable() {
        assertFalse(shouldOpenLongPressMenu(hasPreviewAction = false, hasMenuAction = false))
    }

    @Test
    fun resolveVideoCardMenuOffset_usesLongPressPositionWithinCardRoot() {
        val result = resolveVideoCardMenuOffset(
            rootBoundsInRoot = Rect(left = 24f, top = 120f, right = 384f, bottom = 480f),
            anchorBoundsInRoot = Rect(left = 24f, top = 160f, right = 196f, bottom = 280f),
            density = 2f,
            pressOffsetInAnchorPx = Offset(60f, 24f)
        )

        assertEquals(DpOffset(30.dp, 32.dp), result)
    }

    @Test
    fun resolveVideoCardMenuOffset_fallsBackToAnchorBottomStartWithoutPressOffset() {
        val result = resolveVideoCardMenuOffset(
            rootBoundsInRoot = Rect(left = 24f, top = 120f, right = 384f, bottom = 480f),
            anchorBoundsInRoot = Rect(left = 200f, top = 140f, right = 232f, bottom = 172f),
            density = 2f,
            pressOffsetInAnchorPx = null
        )

        assertEquals(DpOffset(88.dp, 26.dp), result)
    }

    @Test
    fun resolveVideoCardMenuOffset_returnsZeroWhenAnchorOrRootMissing() {
        assertEquals(
            DpOffset.Zero,
            resolveVideoCardMenuOffset(
                rootBoundsInRoot = null,
                anchorBoundsInRoot = Rect(left = 0f, top = 0f, right = 10f, bottom = 10f),
                density = 2f,
                pressOffsetInAnchorPx = Offset.Zero
            )
        )
        assertEquals(
            DpOffset.Zero,
            resolveVideoCardMenuOffset(
                rootBoundsInRoot = Rect(left = 0f, top = 0f, right = 10f, bottom = 10f),
                anchorBoundsInRoot = null,
                density = 2f,
                pressOffsetInAnchorPx = Offset.Zero
            )
        )
    }
}
