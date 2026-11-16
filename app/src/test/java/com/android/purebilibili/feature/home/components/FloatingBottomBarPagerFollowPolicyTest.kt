package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.animation.canStartLiquidIndicatorRecoil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloatingBottomBarPagerFollowPolicyTest {
    @Test
    fun `pager acknowledgement of drag selection does not restart tap feedback`() {
        val dragTarget = resolveIndicatorOwnedTargetOnDragStop(
            targetIndex = 2, selectedIndex = 0, hasExternalPagerPosition = true,
            indicatorPosition = 1.6f,
        )
        assertFalse(shouldStartFloatingDockPagerSelectionFeedback(
            targetIndex = 2, tapTargetIndex = null, dragOwnedTargetIndex = dragTarget,
            isDragging = false,
        ))
        // A distinct programmatic destination must still interrupt the old drag settle.
        assertTrue(shouldStartFloatingDockPagerSelectionFeedback(
            targetIndex = 1, tapTargetIndex = null, dragOwnedTargetIndex = dragTarget,
            isDragging = false,
        ))
    }

    @Test
    fun `existing tap and active pointer keep their feedback ownership`() {
        assertFalse(shouldStartFloatingDockPagerSelectionFeedback(
            targetIndex = 2, tapTargetIndex = 2, dragOwnedTargetIndex = null,
            isDragging = false,
        ))
        assertFalse(shouldStartFloatingDockPagerSelectionFeedback(
            targetIndex = 2, tapTargetIndex = null, dragOwnedTargetIndex = null,
            isDragging = true,
        ))
        assertTrue(shouldStartFloatingDockPagerSelectionFeedback(
            targetIndex = 2, tapTargetIndex = null, dragOwnedTargetIndex = null,
            isDragging = false,
        ))
    }

    @Test
    fun `returning to the selected tab retains ownership until local absorption finishes`() {
        val target = resolveIndicatorOwnedTargetOnDragStop(
            targetIndex = 1, selectedIndex = 1, hasExternalPagerPosition = true,
            indicatorPosition = 1.3f,
        )
        assertEquals(1, target)
        assertTrue(shouldSuppressExternalPagerIndicatorFollow(
            ownedTargetIndex = target, previousExternalPosition = 1f,
            externalPosition = 1f, isPagerScrolling = false, internalPosition = 1.12f,
        ))
        assertFalse(shouldSuppressExternalPagerIndicatorFollow(
            ownedTargetIndex = target, previousExternalPosition = 1f,
            externalPosition = 1f, isPagerScrolling = false, internalPosition = 1f,
        ))
    }

    @Test
    fun `drag onto a new tab owns that target when pager position is provided`() {
        assertEquals(
            0,
            resolveIndicatorOwnedTargetOnDragStop(
                targetIndex = 0,
                selectedIndex = 1,
                hasExternalPagerPosition = true,
            ),
        )
        assertNull(
            resolveIndicatorOwnedTargetOnDragStop(
                targetIndex = 1,
                selectedIndex = 1,
                hasExternalPagerPosition = true,
            ),
        )
        assertNull(
            resolveIndicatorOwnedTargetOnDragStop(
                targetIndex = 0,
                selectedIndex = 1,
                hasExternalPagerPosition = false,
            ),
        )
    }

    @Test
    fun `pager follow is suppressed while catching up from the stale page`() {
        // Comments (1) → intro (0): first frame still reports comments.
        assertTrue(
            shouldSuppressExternalPagerIndicatorFollow(
                ownedTargetIndex = 0,
                previousExternalPosition = null,
                externalPosition = 1f,
                isPagerScrolling = false,
            ),
        )
        // Pager is approaching intro; still do not snap back or press.
        assertTrue(
            shouldSuppressExternalPagerIndicatorFollow(
                ownedTargetIndex = 0,
                previousExternalPosition = 1f,
                externalPosition = 0.72f,
                isPagerScrolling = true,
            ),
        )
        assertFalse(
            isExternalPagerCaughtUpToOwnedTarget(
                ownedTargetIndex = 0,
                externalPosition = 0.72f,
            ),
        )
        // Close to intro but the pager animation is still running — keep swallowing
        // follow so press() cannot bloom a second time.
        assertTrue(
            shouldSuppressExternalPagerIndicatorFollow(
                ownedTargetIndex = 0,
                previousExternalPosition = 0.08f,
                externalPosition = 0.02f,
                isPagerScrolling = true,
            ),
        )
        assertTrue(
            isExternalPagerCaughtUpToOwnedTarget(
                ownedTargetIndex = 0,
                externalPosition = 0.02f,
            ),
        )
        // Pager has settled on intro; ownership can drop without a follow press.
        assertFalse(
            shouldSuppressExternalPagerIndicatorFollow(
                ownedTargetIndex = 0,
                previousExternalPosition = 0.02f,
                externalPosition = 0f,
                isPagerScrolling = false,
            ),
        )
    }

    @Test
    fun `pager reversing away from the drag target resumes follow`() {
        assertFalse(
            shouldSuppressExternalPagerIndicatorFollow(
                ownedTargetIndex = 0,
                previousExternalPosition = 0.4f,
                externalPosition = 0.62f,
                isPagerScrolling = true,
            ),
        )
        assertFalse(
            isExternalPagerCaughtUpToOwnedTarget(
                ownedTargetIndex = 0,
                externalPosition = 0.62f,
            ),
        )
    }

    @Test
    fun `content swipe without an owned drag target still follows the pager`() {
        assertFalse(
            shouldSuppressExternalPagerIndicatorFollow(
                ownedTargetIndex = null,
                previousExternalPosition = 1f,
                externalPosition = 0.7f,
                isPagerScrolling = true,
            ),
        )
        assertFalse(
            isExternalPagerCaughtUpToOwnedTarget(
                ownedTargetIndex = null,
                externalPosition = 0.7f,
            ),
        )
    }

    @Test
    fun `enabled external motion reads pager position directly during content swipe`() {
        assertEquals(
            1.35f,
            resolveFloatingDockVisualIndicatorPosition(
                internalPosition = 1f,
                externalPosition = 1.35f,
                maxTabIndex = 4,
                externalPagerMotionEffectsEnabled = true,
                isDragging = false,
                ownedTargetIndex = null,
                isPagerScrolling = true,
            ),
        )
        assertEquals(
            1.35f,
            resolveFloatingDockVisualIndicatorPosition(
                internalPosition = 1f,
                externalPosition = 1.35f,
                maxTabIndex = 4,
                externalPagerMotionEffectsEnabled = true,
                isDragging = false,
                ownedTargetIndex = null,
                isPagerScrolling = false,
            ),
        )
        assertEquals(
            1f,
            resolveFloatingDockVisualIndicatorPosition(
                internalPosition = 1f,
                externalPosition = 1.35f,
                maxTabIndex = 4,
                externalPagerMotionEffectsEnabled = false,
                isDragging = false,
                ownedTargetIndex = null,
                isPagerScrolling = true,
            ),
        )
    }

    @Test
    fun `indicator drag ownership remains authoritative over external pager motion`() {
        assertEquals(
            2f,
            resolveFloatingDockVisualIndicatorPosition(
                internalPosition = 2f,
                externalPosition = 1.4f,
                maxTabIndex = 4,
                externalPagerMotionEffectsEnabled = true,
                isDragging = true,
                ownedTargetIndex = null,
                isPagerScrolling = true,
            ),
        )
        assertEquals(
            2f,
            resolveFloatingDockVisualIndicatorPosition(
                internalPosition = 2f,
                externalPosition = 1.4f,
                maxTabIndex = 4,
                externalPagerMotionEffectsEnabled = true,
                isDragging = false,
                ownedTargetIndex = 2,
                isPagerScrolling = true,
            ),
        )
    }

    @Test
    fun `selectedIndex cannot yank the indicator back to the stale page`() {
        assertFalse(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = false,
                isPagerScrolling = false,
                indicatorTarget = 0f,
                selectedIndex = 1,
                ownedTargetIndex = 0,
            ),
        )
        assertTrue(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = false,
                isPagerScrolling = false,
                indicatorTarget = 1f,
                selectedIndex = 0,
                ownedTargetIndex = null,
            ),
        )
        assertFalse(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = true,
                isPagerScrolling = false,
                indicatorTarget = 0.4f,
                selectedIndex = 1,
                ownedTargetIndex = null,
            ),
        )
        assertFalse(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = false,
                isPagerScrolling = false,
                indicatorTarget = 0f,
                selectedIndex = 0,
                ownedTargetIndex = 0,
            ),
        )
        assertFalse(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = false,
                isPagerScrolling = true,
                indicatorTarget = 0f,
                selectedIndex = 1,
                ownedTargetIndex = null,
            ),
        )
    }

    @Test
    fun `external pager follow suppresses the discrete settle spring`() {
        assertFalse(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = false,
                isPagerScrolling = false,
                indicatorTarget = 1f,
                selectedIndex = 2,
                ownedTargetIndex = null,
                hasExternalPagerFollow = true,
            ),
        )
        assertTrue(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = false,
                isPagerScrolling = false,
                indicatorTarget = 1f,
                selectedIndex = 2,
                ownedTargetIndex = null,
                hasExternalPagerFollow = false,
            ),
        )
        assertFalse(
            shouldAnimateIndicatorToSelectedIndex(
                isDragging = false,
                isPagerScrolling = false,
                indicatorTarget = 1f,
                selectedIndex = 2,
                ownedTargetIndex = 1,
                hasExternalPagerFollow = true,
            ),
        )
    }

    @Test
    fun `released pager can recoil before scroll settles in either direction`() {
        val held = resolveFloatingDockWobbleManualInteraction(
            indicatorDragging = false, pagerDragging = true, pagerScrolling = true,
            tapMotionActive = false, indicatorOwnsTarget = false,
        )
        val released = resolveFloatingDockWobbleManualInteraction(
            indicatorDragging = false, pagerDragging = false, pagerScrolling = true,
            tapMotionActive = false, indicatorOwnsTarget = false,
        )
        // A held gesture must not wobble, even when it is near either endpoint.
        for ((position, target) in listOf(0.98f to 1f, 0.02f to 0f)) {
            assertFalse(canStartLiquidIndicatorRecoil(position, target, 1f, interactionActive = held))
            assertTrue(canStartLiquidIndicatorRecoil(position, target, 1f, interactionActive = released))
        }
        assertFalse(canStartLiquidIndicatorRecoil(0.5f, 1f, 1f, interactionActive = released))
    }

    @Test
    fun `callers without explicit pointer state retain scrolling fallback`() {
        assertTrue(resolveFloatingDockWobbleManualInteraction(
            indicatorDragging = false, pagerDragging = null, pagerScrolling = true,
            tapMotionActive = false, indicatorOwnsTarget = false,
        ))
    }

}
