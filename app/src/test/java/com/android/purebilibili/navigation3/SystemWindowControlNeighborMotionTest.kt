package com.android.purebilibili.navigation3

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemWindowControlNeighborMotionTest {
    @Test
    fun bothNeighborsMoveAwayFromTheSourceAndReleaseRetracesTheirDisplacement() {
        val source = Rect(100f, 0f, 140f, 40f)
        val left = Rect(48f, 0f, 88f, 40f)
        val right = Rect(152f, 0f, 192f, 40f)
        listOf(left, right).forEach { neighbor ->
            val direction = requireNotNull(resolveSystemWindowNeighborDirection(source, neighbor, 24f))
            val pivot = controlNeighborPressurePivot(direction.x)
            // 远端固定：左邻项以左缘为支点，右邻项以右缘为支点。
            assertEquals(if (neighbor.center.x < source.center.x) 0f else 1f, pivot, 0f)
            val displacement = direction.x * controlNeighborPressureDisplacement(1f, neighbor.width, 6f)
            org.junit.Assert.assertTrue(displacement * (neighbor.center.x - source.center.x) > 0f)
            assertEquals(0f, controlNeighborPressureDisplacement(0f, neighbor.width, 6f), 0f)
            val rebound = direction.x * controlNeighborPressureDisplacement(-0.02f, neighbor.width, 6f)
            org.junit.Assert.assertTrue(rebound * displacement < 0f)
        }
    }

    @Test
    fun settingsAndSearchInTheSameRowReactInOppositeDirections() {
        val search = Rect(56f, 8f, 304f, 48f)
        val settings = Rect(316f, 10f, 352f, 46f)
        assertEquals(Offset(-1f, 0f), resolveSystemWindowNeighborDirection(settings, search, 24f))
        assertEquals(Offset(1f, 0f), resolveSystemWindowNeighborDirection(search, settings, 24f))
    }

    @Test
    fun homeHeaderGroupLinksAvatarAndSettingsAcrossTheSearchField() {
        val avatar = Rect(16f, 10f, 52f, 46f)
        val settings = Rect(316f, 10f, 352f, 46f)
        assertEquals(
            Offset(1f, 0f),
            resolveSystemWindowNeighborDirection(avatar, settings, 24f, sameMotionGroup = true),
        )
        assertEquals(
            Offset(-1f, 0f),
            resolveSystemWindowNeighborDirection(settings, avatar, 24f, sameMotionGroup = true),
        )
        assertNull(resolveSystemWindowNeighborDirection(avatar, settings, 24f))
    }

    @Test
    fun explicitlyGroupedServiceRowsCanReactAcrossTheirSectionSpacing() {
        val first = Rect(16f, 16f, 80f, 64f)
        val second = Rect(16f, 110f, 80f, 158f)
        assertNull(resolveSystemWindowNeighborDirection(first, second, 24f))
        assertEquals(Offset(0f, 1f), resolveSystemWindowNeighborDirection(first, second, 24f, sameMotionGroup = true))
        assertEquals(Offset(0f, -1f), resolveSystemWindowNeighborDirection(second, first, 24f, sameMotionGroup = true))
    }

    @Test
    fun stackedShortcutsReactAlongTheirColumn() {
        val first = Rect(16f, 16f, 80f, 64f)
        val second = Rect(16f, 80f, 80f, 128f)
        assertEquals(Offset(0f, 1f), resolveSystemWindowNeighborDirection(first, second, 24f))
        assertEquals(Offset(0f, -1f), resolveSystemWindowNeighborDirection(second, first, 24f))
    }

    @Test
    fun distantDiagonalAndEnclosingControlsDoNotJoinTheTransition() {
        val source = Rect(100f, 100f, 140f, 140f)
        assertNull(resolveSystemWindowNeighborDirection(source, Rect(16f, 100f, 56f, 140f), 24f))
        assertNull(resolveSystemWindowNeighborDirection(source, Rect(150f, 150f, 190f, 190f), 24f))
        assertNull(resolveSystemWindowNeighborDirection(source, Rect(80f, 80f, 180f, 180f), 24f))
    }

    @Test
    fun misalignedNeighborMustStillShareHalfOfTheRow() {
        val source = Rect(100f, 100f, 140f, 140f)
        assertNull(resolveSystemWindowNeighborDirection(source, Rect(150f, 135f, 190f, 175f), 24f))
    }
}
