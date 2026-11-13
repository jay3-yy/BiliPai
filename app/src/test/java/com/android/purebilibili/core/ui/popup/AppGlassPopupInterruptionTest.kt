package com.android.purebilibili.core.ui.popup

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppGlassMenuPlacement
import com.android.purebilibili.core.ui.components.AppGlassMenuRequest
import com.android.purebilibili.core.ui.components.AppSingleChoicePopupRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppGlassPopupInterruptionTest {
    private val card = Rect(60f, 320f, 360f, 740f)
    private val anchor = Rect(300f, 240f, 360f, 300f)
    private fun controller() = AppGlassChoicePopupController({ _, _, _ -> }, {})
    private fun menu(
        press: com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress? = null,
        interactiveContent: Boolean = false,
        onDismiss: () -> Unit = {},
    ) = AppGlassMenuRequest(
        anchor = { anchor }, placement = AppGlassMenuPlacement.ANCHOR_END,
        maxWidth = 280.dp, minWidth = 160.dp, maxHeight = null,
        scrollState = ScrollState(0), modifier = Modifier, title = "菜单",
        onDismissRequest = onDismiss, content = {}, carriedPress = press,
        interactiveContent = interactiveContent,
    )

    @Test
    fun anchoredPredictivePreviewContinuesFromTheSameFrameInBothMotionStyles() {
        for (style in com.android.purebilibili.core.store.home.GlassMotionStyle.entries) {
            val morph = PopupMorphState()
            morph.beginOpen(sourceControl = true, style = style)
            morph.card = card
            morph.anchor = anchor
            morph.advanceTo(morph.openDuration)
            morph.predictiveBack.beginGesture()
            morph.predictiveBack.seek(0.8f)
            val preview = morph.frame(24f, 1f)
            morph.beginClose(preview, preview)
            assertEquals(preview, morph.frame(24f, 1f))
            assertTrue(morph.hasSourceControl)
            morph.advanceTo(0.0001f)
            assertEquals(preview.contentBlur, morph.frame(24f, 1f).contentBlur, 0.02f)
            morph.advanceTo(0.07f)
            val returning = morph.frame(24f, 1f)
            // 既要无跳变地接住预览，也要保留回收中段的源控件交接和连接颈。
            assertTrue(returning.sourceRect != null)
            assertTrue(returning.sourceAlpha > preview.sourceAlpha)
            assertTrue(returning.surfaceBlend > 0f)
            if (style == com.android.purebilibili.core.store.home.GlassMotionStyle.ELASTIC) {
                assertTrue((returning.lensProgress ?: 0f) > 0f)
            }
            morph.advanceTo(morph.closeDuration)
            assertTrue(morph.frame(24f, 1f).contentAlpha < preview.contentAlpha)
        }
    }

    @Test
    fun predictiveBackHandsTheVisibleFrameToClosingAndDismissesOnlyOnce() {
        for (style in com.android.purebilibili.core.store.home.GlassMotionStyle.entries) {
            var dismissals = 0
            val controller = controller()
            controller.preferredMotionStyle = style
            controller.showMenu(Any(), menu { dismissals++ })
            val morph = controller.morph
            morph.card = card
            morph.anchor = anchor
            morph.advanceTo(morph.openDuration)
            val resting = morph.frame(24f, 1f)
            morph.predictiveBack.beginGesture()
            morph.predictiveBack.seek(0.7f)
            val preview = morph.frame(24f, 1f)
            assertTrue(preview.rect.width < resting.rect.width)
            assertTrue(preview.contentBlur > resting.contentBlur)
            assertEquals(0, dismissals)

            controller.requestDismiss()
            assertEquals(preview, morph.frame(24f, 1f))
            assertEquals(0f, morph.predictiveBack.progress)
            assertEquals(0, dismissals)
            morph.advanceTo(morph.closeDuration)
            controller.finish()?.invoke()
            controller.finish()?.invoke()
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun draggingOutsideEitherEndKeepsActualDistanceAfterScrollingCancelsSelection() {
        val targets = mutableListOf<androidx.compose.ui.geometry.Offset>()
        val controller = AppGlassChoicePopupController({ _, _, pull -> targets += pull }, {})
        controller.showMenu(Any(), menu())
        controller.morph.card = card
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        controller.beginPress(card.center, allowGrowth = false)
        for (position in listOf(
            androidx.compose.ui.geometry.Offset(card.center.x, card.top - 80f),
            androidx.compose.ui.geometry.Offset(card.center.x, card.bottom + 80f),
        )) {
            controller.movePress(position, allowSelection = false)
            val expected = popupRubberBandPull(popupPullBeyondBounds(position, card), card.minDimension)
            assertEquals(expected, targets.last())
            controller.refreshPress()
            assertEquals(expected, targets.last())
        }
        controller.cancelPress()
        assertEquals(androidx.compose.ui.geometry.Offset.Zero, targets.last())
    }

    @Test
    fun fingerLightTracksWithoutStartingElasticMotion() {
        var elasticUpdates = 0
        val controller = AppGlassChoicePopupController({ _, _, _ -> elasticUpdates++ }, {})
        controller.showMenu(Any(), menu())
        val before = elasticUpdates
        val position = card.center
        controller.updateLight(position, true)
        assertTrue(controller.lightPressed)
        assertEquals(position, controller.lightPosition)
        assertEquals(before, elasticUpdates)
        controller.updateLight(position, false)
        assertFalse(controller.lightPressed)
    }

    @Test
    fun scrollingKeepsElasticRestUntilEdgePullAndRestoresRestWhenScrollingResumes() {
        val targets = mutableListOf<Pair<Boolean, androidx.compose.ui.geometry.Offset>>()
        val controller = AppGlassChoicePopupController({ _, pressed, pull -> targets += pressed to pull }, {})
        controller.showMenu(Any(), menu())
        controller.morph.card = card
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        controller.beginPress(card.center, allowGrowth = false)
        controller.movePress(card.center + androidx.compose.ui.geometry.Offset(0f, 100f),
            allowSelection = false, allowDeformation = false)
        assertEquals(false to androidx.compose.ui.geometry.Offset.Zero, targets.last())
        controller.movePress(card.center + androidx.compose.ui.geometry.Offset(0f, 24f),
            allowSelection = false, allowDeformation = true, fromScrollBoundary = true)
        assertEquals(true to popupRubberBandPull(androidx.compose.ui.geometry.Offset(0f, 24f), card.minDimension), targets.last())
        controller.movePress(card.center + androidx.compose.ui.geometry.Offset(0f, -100f),
            allowSelection = false, allowDeformation = false)
        assertEquals(false to androidx.compose.ui.geometry.Offset.Zero, targets.last())
    }

    @Test
    fun repeatedShowsKeepTheSameRequestAndClockWithoutDismissal() {
        val controller = controller()
        val owner = Any()
        var dismissals = 0
        val original = menu { dismissals++ }
        controller.showMenu(owner, original)
        controller.morph.advanceTo(0.12f)
        val session = controller.morph.session
        repeat(20) { controller.showMenu(owner, menu { dismissals++ }) }
        assertSame(original, controller.request?.menu)
        assertEquals(session, controller.morph.session)
        assertEquals(0.12f, controller.morph.seconds)
        assertEquals(0, dismissals)
    }

    @Test
    fun repeatedDismissDoesNotRestartClosingAndReopenRetainsCard() {
        val controller = controller()
        val owner = Any()
        controller.showMenu(owner, menu())
        controller.morph.card = card
        controller.morph.anchor = anchor
        controller.morph.advanceTo(0.12f)
        controller.requestDismiss()
        controller.morph.advanceTo(0.08f)
        val closingSession = controller.morph.session
        repeat(20) { controller.requestDismiss() }
        assertEquals(closingSession, controller.morph.session)
        assertEquals(0.08f, controller.morph.seconds)
        assertFalse(controller.morph.opening)
        controller.showMenu(owner, menu())
        assertTrue(controller.morph.opening)
        assertEquals(card, controller.morph.card)
        assertTrue(controller.morph.openStartFrame != null)
        assertEquals(0f, controller.morph.seconds)
        assertEquals(closingSession + 1, controller.morph.session)
    }

    @Test
    fun rapidMenuSelectionCommitsOnceAndDoesNotReopenPendingAction() {
        val controller = controller()
        val owner = Any()
        var actions = 0
        var dismissals = 0
        controller.showMenu(owner, menu { dismissals++ })
        repeat(20) { controller.menuScope.dispatch({ actions++ }, true) }
        val session = controller.morph.session
        controller.showMenu(owner, menu { dismissals++ })
        assertFalse(controller.morph.opening)
        assertEquals(session, controller.morph.session)
        controller.finish()?.invoke()
        controller.finish()?.invoke()
        assertEquals(1, actions)
        assertEquals(1, dismissals)
    }

    @Test
    fun interactivePanelActionWaitsForExitAndCommitsOnlyOnce() {
        val controller = controller()
        var actions = 0
        var dismissals = 0
        controller.showMenu(Any(), menu(interactiveContent = true) { dismissals++ })
        repeat(3) { controller.menuScope.dispatch({ actions++ }, true) }
        assertFalse(controller.morph.opening)
        assertEquals(0, actions)
        assertEquals(0, dismissals)
        controller.finish()?.invoke()
        controller.finish()?.invoke()
        assertEquals(1, actions)
        assertEquals(1, dismissals)
    }

    @Test
    fun resumedMenuUsesTheLatestDismissCallback() {
        val controller = controller()
        val owner = Any()
        var oldDismissals = 0
        var newDismissals = 0
        controller.showMenu(owner, menu { oldDismissals++ })
        controller.morph.card = card
        controller.morph.advanceTo(0.2f)
        controller.requestDismiss()
        controller.morph.advanceTo(0.05f)
        controller.showMenu(owner, menu { newDismissals++ })
        controller.requestDismiss()
        controller.finish()?.invoke()
        assertEquals(0, oldDismissals)
        assertEquals(1, newDismissals)
    }

    @Test
    fun rapidSettingsSelectionCommitsImmediatelyAndOnlyOnce() {
        val controller = controller()
        var selections = 0
        controller.show(AppSingleChoicePopupRequest(
            title = "设置", options = emptyList(), selectedValue = null,
            anchorBoundsInWindow = null, centered = true,
        )) { selections++ }
        repeat(20) { controller.select("value") }
        assertEquals(1, selections)
        assertFalse(controller.morph.opening)
        assertTrue(controller.request != null)
        controller.finish()?.invoke()
        controller.finish()?.invoke()
        assertEquals(1, selections)
    }

    @Test
    fun committedSettingsSelectionSurvivesParentExitWithoutRepeating() {
        val controller = controller()
        var selections = 0
        val request = AppSingleChoicePopupRequest(
            title = "设置", options = emptyList(), selectedValue = null,
            anchorBoundsInWindow = anchor, centered = false,
        )
        controller.show(request) { selections++ }
        controller.select("value")
        assertEquals(1, selections)
        controller.dismissWithParent()
        controller.finish()?.invoke()
        assertEquals(1, selections)
    }

    @Test
    fun holdingDuringOpeningDoesNotScheduleASecondPressBounceAtSettlement() {
        val elasticTargets = mutableListOf<Boolean>()
        val controller = AppGlassChoicePopupController({ _, pressed, _ -> elasticTargets += pressed }, {})
        controller.showMenu(Any(), menu())
        controller.morph.card = card
        controller.morph.advanceTo(0.05f)
        controller.beginPress(anchor.center)
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        controller.refreshPress()
        assertFalse(elasticTargets.any { it })
        controller.cancelPress()
        controller.beginPress(card.center)
        assertTrue(elasticTargets.last())
    }

    @Test
    fun sourceDragStartsGrowthAndCancellationReleasesIt() {
        val targets = mutableListOf<Pair<Boolean, androidx.compose.ui.geometry.Offset>>()
        val press = com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress()
        val controller = AppGlassChoicePopupController({ _, pressed, pull -> targets += pressed to pull }, {})
        controller.show(AppSingleChoicePopupRequest(
            title = "设置", options = emptyList(), selectedValue = null,
            anchorBoundsInWindow = anchor, centered = false, carriedPress = press,
        )) {}
        controller.morph.card = card
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        press.move(androidx.compose.ui.geometry.Offset(card.right + 80f, card.center.y))
        assertTrue(targets.last().first)
        assertTrue(targets.last().second.x > 0f)
        press.cancel()
        assertEquals(false to androidx.compose.ui.geometry.Offset.Zero, targets.last())
    }

    @Test
    fun globalMenuForwardsSourceDragToTheSharedFeedbackController() {
        val press = com.android.purebilibili.core.ui.components.AppSingleChoicePopupPress()
        val targets = mutableListOf<androidx.compose.ui.geometry.Offset>()
        val controller = AppGlassChoicePopupController({ _, _, pull -> targets += pull }, {})
        controller.showMenu(Any(), menu(press))
        controller.morph.card = card
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        press.move(androidx.compose.ui.geometry.Offset(card.left - 80f, card.center.y))
        assertTrue(targets.last().x < 0f)
        press.cancel()
        assertEquals(androidx.compose.ui.geometry.Offset.Zero, targets.last())
        assertTrue(controller.morph.opening)
    }

    @Test
    fun parentExitCancelsPendingChildActionsAndRunsOnlyOneUnanchoredClose() {
        val controller = controller()
        var actions = 0
        var dismissals = 0
        controller.showMenu(Any(), menu { dismissals++ })
        controller.morph.card = card
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        controller.menuScope.dispatch({ actions++ }, true)
        controller.morph.advanceTo(0.1f)
        controller.dismissWithParent()
        val session = controller.morph.session
        repeat(20) { controller.dismissWithParent() }
        assertEquals(session, controller.morph.session)
        assertFalse(controller.morph.hasSourceControl)
        assertTrue(controller.morph.interruptedClose)
        assertEquals(AppGlassPopupMotion.ShrinkSeconds,
            AppGlassPopupMotion.closeDuration(controller.morph.hasSourceControl))
        controller.finish()?.invoke()
        assertEquals(0, actions)
        assertEquals(0, dismissals)
    }

    @Test
    fun draggingDuringOpeningStartsGrowthBeforeSettlementWithoutRestartingAtTheEnd() {
        val targets = mutableListOf<Boolean>()
        val controller = AppGlassChoicePopupController({ _, pressed, _ -> targets += pressed }, {})
        controller.showMenu(Any(), menu())
        controller.morph.card = card
        controller.morph.advanceTo(0.1f)
        controller.beginPress(card.center)
        assertFalse(targets.last())
        controller.movePress(card.center + androidx.compose.ui.geometry.Offset(20f, 0f))
        assertTrue(targets.last())
        val count = targets.size
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        controller.refreshPress()
        assertEquals(count, targets.size)
        controller.cancelPress()
        assertFalse(targets.last())
    }


    @Test
    fun disablingGlobalDragKeepsPressFeedbackButRemovesDragDisplacement() {
        val targets = mutableListOf<Pair<Boolean, androidx.compose.ui.geometry.Offset>>()
        val controller = AppGlassChoicePopupController({ _, pressed, drag -> targets += pressed to drag }, {})
        controller.configure(1f, 24f, dragEnabled = false)
        controller.showMenu(Any(), menu())
        controller.morph.card = card
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        controller.beginPress(card.center)
        controller.movePress(card.center + androidx.compose.ui.geometry.Offset(100f, 50f))
        assertTrue(targets.last().first)
        assertEquals(androidx.compose.ui.geometry.Offset.Zero, targets.last().second)
    }

    @Test
    fun scrollingRetainsSurfaceDragUntilReleaseWithoutDismissingTheMenu() {
        val targets = mutableListOf<Pair<Boolean, androidx.compose.ui.geometry.Offset>>()
        val controller = AppGlassChoicePopupController({ _, pressed, drag -> targets += pressed to drag }, {})
        controller.showMenu(Any(), menu())
        controller.morph.card = card
        controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
        controller.beginPress(card.center)
        val position = card.center + androidx.compose.ui.geometry.Offset(0f, -100f)
        controller.movePress(position, allowSelection = false, fromScrollBoundary = true)
        controller.refreshPress()
        assertTrue(targets.last().first)
        assertEquals(popupRubberBandPull(androidx.compose.ui.geometry.Offset(0f, -100f), card.minDimension), targets.last().second)
        controller.releasePress(position)
        assertEquals(false to androidx.compose.ui.geometry.Offset.Zero, targets.last())
        assertTrue(controller.morph.opening)
    }

    @Test
    fun interruptedFramesPreserveAllOpticalPropertiesAtTheBoundary() {
        val start = AppGlassPopupMotion.opening(0.045f, card, anchor, 24f, true)
        val previous = AppGlassPopupMotion.opening(0.04f, card, anchor, 24f, true)
        assertEquals(start, AppGlassPopupMotion.closing(0f, 0.045f, card, anchor, 24f, true, startFrame = start))
        val settled = AppGlassPopupFrame(card, 24f, 1f, 1f, 0f)
        assertEquals(start, AppGlassPopupMotion.retarget(0f, 0.25f, start, previous, 0.005f, settled))
        assertEquals(settled, AppGlassPopupMotion.retarget(0.25f, 0.25f, start, previous, 0.005f, settled))
    }

    @Test
    fun reopenReversesOnceWithoutStartingAnotherSpringOscillation() {
        val start = AppGlassPopupMotion.closing(0.1f, 0.45f, card, anchor, 24f, true)
        val previous = AppGlassPopupMotion.closing(0.095f, 0.45f, card, anchor, 24f, true)
        val target = AppGlassPopupFrame(card, 24f, 1f, 1f, 0f)
        var previousWidth = start.rect.width
        var previousDirection = -1
        var reversals = 0
        for (step in 1..100) {
            val frame = AppGlassPopupMotion.retarget(step * 0.0025f, 0.25f, start, previous, 0.005f, target)
            val delta = frame.rect.width - previousWidth
            if (kotlin.math.abs(delta) > 0.0001f) {
                val direction = if (delta > 0f) 1 else -1
                if (direction != previousDirection) reversals++
                previousDirection = direction
            }
            assertTrue(frame.rect.width > 0f && frame.rect.height > 0f)
            assertTrue(frame.contentAlpha in 0f..1f && frame.surfaceAlpha in 0f..1f)
            previousWidth = frame.rect.width
        }
        assertEquals(1, reversals)
        assertEquals(card.width, previousWidth, 0.001f)
    }
    @Test
    fun aQueuedOldFrameCannotAdvanceAClosingOrReopenedPopup() {
        val controller = controller()
        val owner = Any()
        controller.showMenu(owner, menu())
        controller.morph.card = card
        controller.morph.anchor = anchor
        val openingSession = controller.morph.session
        controller.morph.advanceTo(0.12f, openingSession)
        val presentation = controller.morph.presentation
        controller.requestDismiss()
        val closingSession = controller.morph.session
        assertFalse(controller.morph.advanceTo(0.4f, openingSession))
        assertEquals(0f, controller.morph.seconds)
        controller.morph.advanceTo(0.08f, closingSession)
        controller.showMenu(owner, menu())
        assertEquals(presentation, controller.morph.presentation)
        assertFalse(controller.morph.advanceTo(0.5f, closingSession))
        assertEquals(0f, controller.morph.seconds)
        assertTrue(controller.morph.advanceTo(0.01f, controller.morph.session))
    }

    @Test
    fun oldCloseCompletionCannotFinishTheNextPresentationOrRestoreItsSource() {
        val controller = controller()
        var restorations = 0
        fun request() = AppSingleChoicePopupRequest(
            title = "设置", options = emptyList(), selectedValue = null,
            anchorBoundsInWindow = anchor, centered = false,
            onSourceVisibilityChange = { if (it) restorations++ },
        )
        controller.show(request()) {}
        controller.morph.card = card
        controller.requestDismiss()
        val oldSession = controller.morph.session
        val oldPresentation = controller.morph.presentation
        controller.finish(oldSession)?.invoke()
        val next = request()
        controller.show(next) {}
        controller.morph.card = card
        controller.requestDismiss()
        assertEquals(oldPresentation + 1, controller.morph.presentation)
        controller.finish(oldSession)?.invoke()
        assertSame(next, controller.request)
        assertEquals(1, restorations)
        controller.finish(controller.morph.session)?.invoke()
        assertEquals(2, restorations)
    }

    @Test
    fun repeatedCompleteLifecyclesStartFreshAndRejectEveryPreviousClock() {
        val controller = controller()
        val owner = Any()
        var previousSession = -1
        repeat(30) { index ->
            controller.showMenu(owner, menu())
            assertEquals(index + 1, controller.morph.presentation)
            assertEquals(0f, controller.morph.seconds)
            assertEquals(Rect.Zero, controller.morph.card)
            assertTrue(controller.morph.openStartFrame == null)
            assertFalse(controller.morph.advanceTo(0.5f, previousSession))
            controller.morph.card = card
            controller.morph.anchor = anchor
            controller.morph.advanceTo(AppGlassPopupMotion.OpenSeconds)
            controller.requestDismiss()
            previousSession = controller.morph.session
            controller.finish(previousSession)?.invoke()
        }
    }

}
