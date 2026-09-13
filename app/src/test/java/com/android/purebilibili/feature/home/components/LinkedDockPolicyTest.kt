package com.android.purebilibili.feature.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkedDockPolicyTest {
    @Test
    fun globalCollapseRequestUsesCompactPlaybackPhaseWhenAudioIsPresent() {
        assertEquals(
            LinkedDockPhase.Playback,
            resolveLinkedDockRestingPhase(collapseRequested = true, hasAudio = true),
        )
    }

    @Test
    fun dockStaysExpandedWithoutAudioOrCollapseRequest() {
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockRestingPhase(collapseRequested = true, hasAudio = false),
        )
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockRestingPhase(collapseRequested = false, hasAudio = true),
        )
    }

    @Test
    fun expandedAudioOccupiesItsOwnRow() {
        val geometry = geometry(merge = 0f, search = 0f)
        assertEquals(336, geometry.audioWidth)
        assertEquals(0, geometry.audioX)
        assertEquals(0, geometry.audioY)
        assertEquals(72, geometry.top)
        assertEquals(136, geometry.height)
    }

    @Test
    fun compactPlaybackKeepsGapsBetweenThreeSeparateCapsules() {
        val geometry = geometry(merge = 1f, search = 0f)
        assertEquals(64, geometry.audioX)
        assertEquals(208, geometry.audioWidth)
        assertEquals(336, geometry.audioX + geometry.audioWidth + 8 + geometry.searchWidth)
        assertEquals(64, geometry.height)
    }

    @Test
    fun playbackGapsRemainVisibleAcrossWindowWidths() {
        for (width in listOf(240, 296, 336, 600)) {
            for (searchEnabled in listOf(false, true)) {
                val geometry = resolveLinkedDockGeometry(width, 56, 64, 8, true, searchEnabled, 1f, 0f)
                assertEquals(8, geometry.audioX - 56)
                val trailingGap = width - geometry.searchWidth - geometry.audioX - geometry.audioWidth
                assertEquals(if (searchEnabled) 8 else 0, trailingGap)
                assertTrue(geometry.audioWidth >= 112)
            }
        }
    }

    @Test
    fun searchLeavesOneAccessibleArtworkTarget() {
        val geometry = geometry(merge = 1f, search = 1f)
        assertEquals(56, geometry.audioWidth)
        assertEquals(208, geometry.searchWidth)
        assertEquals(64, geometry.height)
    }

    @Test
    fun narrowAndWideLayoutsDoNotOverlapAtRest() {
        for (width in listOf(240, 296, 336, 600)) {
            for (hasAudio in listOf(false, true)) {
                val geometry = resolveLinkedDockGeometry(width, 56, 64, 8, hasAudio, true, 1f, 1f)
                assertTrue(geometry.searchWidth >= 56)
                assertTrue(geometry.audioWidth >= 0)
                val occupiedWidth = 56 + geometry.audioWidth + geometry.searchWidth +
                    if (hasAudio) 16 else 8
                assertEquals(width, occupiedWidth)
            }
        }
    }

    @Test
    fun outOfRangeAnimationProgressCannotProduceNegativeSizes() {
        val geometry = geometry(merge = 1.05f, search = 1.04f)
        assertEquals(0, geometry.top)
        assertEquals(56, geometry.audioWidth)
        assertEquals(64, geometry.height)
    }

    private fun geometry(merge: Float, search: Float) =
        resolveLinkedDockGeometry(336, 56, 64, 8, true, true, merge, search)
}
