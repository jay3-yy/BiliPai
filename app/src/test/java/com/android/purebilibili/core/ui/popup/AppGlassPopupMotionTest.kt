package com.android.purebilibili.core.ui.popup

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import com.android.purebilibili.core.ui.components.AppGlassMenuPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppGlassPopupMotionTest {
    private val card = Rect(60f, 320f, 360f, 740f)
    private val anchor = Rect(300f, 240f, 360f, 300f)

    @Test
    fun anchoredOpeningStartsAsACapsuleAtTheControl() {
        val frame = AppGlassPopupMotion.opening(0f, card, anchor, 24f)
        assertEquals(anchor.center, frame.rect.center)
        assertEquals(0.41f * anchor.minDimension, frame.rect.width, 0.0001f)
        assertEquals(frame.rect.width, frame.rect.height, 0.0001f)
        assertEquals(frame.rect.width / 2f, frame.radius, 0.0001f)
        assertEquals(0f, frame.contentAlpha)
    }

    @Test
    fun openingSettlesOnTheMeasuredCardWithoutChangingItsBounds() {
        val frame = AppGlassPopupMotion.opening(AppGlassPopupMotion.OpenSeconds, card, anchor, 24f)
        assertEquals(card, frame.rect)
        assertEquals(24f, frame.radius)
        assertEquals(1f, frame.contentScale)
        assertEquals(1f, frame.contentAlpha)
        assertEquals(0f, frame.contentBlur)
    }

    @Test
    fun closingMidOpeningContinuesFromTheCurrentFrame() {
        val opening = AppGlassPopupMotion.opening(0.12f, card, anchor, 24f)
        val closing = AppGlassPopupMotion.closing(0f, 0.12f, card, anchor, 24f)
        assertEquals(opening.rect.left, closing.rect.left, 0.0001f)
        assertEquals(opening.rect.top, closing.rect.top, 0.0001f)
        assertEquals(opening.rect.width, closing.rect.width, 0.0001f)
        assertEquals(opening.rect.height, closing.rect.height, 0.0001f)
        assertEquals(opening.radius, closing.radius, 0.0001f)
        assertEquals(opening.contentAlpha, closing.contentAlpha)
        assertEquals(opening.contentScale, closing.contentScale)
        assertEquals(opening.contentBlur, closing.contentBlur, 0.0001f)
    }

    @Test
    fun anchoredReturnLandsAtTheControlAndBecomesInvisible() {
        assertEquals(0.5f, AppGlassPopupMotion.closeDuration(anchor))
        val frame = AppGlassPopupMotion.closing(0.5f, 0.45f, card, anchor, 24f)
        assertEquals(anchor.center, frame.rect.center)
        assertEquals(0f, frame.contentAlpha)
        assertEquals(0f, frame.surfaceAlpha)
        assertTrue(frame.rect.width < anchor.width)
    }

    @Test
    fun centeredDismissalShrinksMonotonicallyInsteadOfJumpingBetweenBezierRoots() {
        assertEquals(0.2f, AppGlassPopupMotion.closeDuration(Rect.Zero))
        var previousWidth = card.width
        var previousAlpha = 1f
        for (step in 0..40) {
            val frame = AppGlassPopupMotion.closing(step * 0.005f, 0.45f, card, Rect.Zero, 24f)
            assertTrue(frame.rect.width <= previousWidth + 0.0001f)
            assertTrue(frame.surfaceAlpha <= previousAlpha + 0.0001f)
            previousWidth = frame.rect.width
            previousAlpha = frame.surfaceAlpha
        }
        assertEquals(card.width * 0.8f, previousWidth, 0.0001f)
        assertEquals(0f, previousAlpha, 0.0001f)
    }

    @Test
    fun springOvershootNeverProducesInvalidOpticalGeometry() {
        for (step in 0..100) {
            for (frame in listOf(
                AppGlassPopupMotion.opening(step * 0.005f, card, anchor, 24f),
                AppGlassPopupMotion.closing(step * 0.005f, 0.45f, card, anchor, 24f),
            )) {
                assertTrue(frame.rect.width > 0f && frame.rect.height > 0f)
                assertTrue(frame.contentScale.isFinite() && frame.contentScale > 0f)
                assertTrue(frame.radius.isFinite() && frame.radius >= 0f)
                assertTrue(frame.contentAlpha in 0f..1f)
                assertTrue(frame.surfaceAlpha in 0f..1f)
            }
        }
    }

    @Test
    fun popupOverlapsTheActualValueControlInsteadOfOpeningBelowTheRow() {
        val control = Rect(300f, 200f, 360f, 232f)
        val position = resolveAppGlassPopupPosition(control, false,
            IntSize(160, 200), IntSize(400, 900), 16, 8)
        assertEquals(IntOffset(200, 192), position)
        val popup = Rect(position.x.toFloat(), position.y.toFloat(), position.x + 160f, position.y + 200f)
        assertTrue(popup.contains(control.topLeft) && popup.contains(control.bottomRight - Offset(0.1f, 0.1f)))
    }

    @Test
    fun edgeAndNarrowWindowPlacementStaysWithinTheHost() {
        val nearBottom = resolveAppGlassPopupPosition(Rect(150f, 840f, 210f, 880f), false,
            IntSize(180, 300), IntSize(220, 900), 16, 8)
        assertEquals(IntOffset(24, 584), nearBottom)
        assertEquals(IntOffset(8, 16), resolveAppGlassPopupPosition(null, true,
            IntSize(84, 80), IntSize(100, 112), 16, 8))
    }

    @Test
    fun settledOpticalOutlineKeepsTheCardCornerInsteadOfBecomingAnEllipse() {
        val frame = AppGlassPopupMotion.opening(0.45f, card, anchor, 24f)
        val (outline, radius) = glassPopupLocalOutline(frame, card, card.size, false)
        assertEquals(Rect(Offset.Zero, card.size), outline)
        assertEquals(24f, radius)
        assertTrue(radius < outline.minDimension / 2f)
    }

    @Test
    fun sourceControlStartsAndEndsAtItsOwnBounds() {
        val first = AppGlassPopupMotion.opening(0f, card, anchor, 24f, sourceControl = true)
        assertEquals(anchor, first.sourceRect)
        assertEquals(1f, first.sourceAlpha)
        val last = AppGlassPopupMotion.closing(0.5f, 0.45f, card, anchor, 24f, sourceControl = true)
        assertEquals(anchor, last.sourceRect)
        assertEquals(1f, last.sourceAlpha)
        assertEquals(0f, last.surfaceAlpha)
    }

    @Test
    fun spatialMotionAndBlurRemainEquivalentAcrossDisplayDensities() {
        fun Rect.scaled(density: Float) = Rect(left * density, top * density, right * density, bottom * density)
        for (density in listOf(1f, 1.5f, 2f, 3f, 4f)) {
            for (step in 0..100) {
                val time = step * 0.005f
                val reference = AppGlassPopupMotion.opening(time, card, anchor, 24f, true)
                val scaled = AppGlassPopupMotion.opening(time, card.scaled(density), anchor.scaled(density), 24f * density, true, density)
                assertEquals(reference.rect.center.x, scaled.rect.center.x / density, 0.001f)
                assertEquals(reference.rect.center.y, scaled.rect.center.y / density, 0.001f)
                assertEquals(reference.contentBlur, scaled.contentBlur / density, 0.001f)
                assertEquals(reference.surfaceBlend, scaled.surfaceBlend / density, 0.001f)
                val closing = AppGlassPopupMotion.closing(time, 0.12f, card, anchor, 24f, true)
                val scaledClosing = AppGlassPopupMotion.closing(time, 0.12f, card.scaled(density), anchor.scaled(density), 24f * density, true, density)
                assertEquals(closing.rect.center.y, scaledClosing.rect.center.y / density, 0.001f)
                assertEquals(closing.sourceRect!!.center.y, scaledClosing.sourceRect!!.center.y / density, 0.001f)
                assertEquals(closing.contentBlur, scaledClosing.contentBlur / density, 0.001f)
            }
        }
    }

    @Test
    fun earlyReturnPreservesTheSourceGlyphAndUnionAtTheTurn() {
        for (time in listOf(0f, 0.02f, 0.07f, 0.12f)) {
            val from = AppGlassPopupMotion.opening(time, card, anchor, 24f, true, 3f)
            val returning = AppGlassPopupMotion.closing(0f, time, card, anchor, 24f, true, 3f)
            assertEquals(from.sourceRect!!.left, returning.sourceRect!!.left, 0.001f)
            assertEquals(from.sourceRect.top, returning.sourceRect.top, 0.001f)
            assertEquals(from.sourceRect.width, returning.sourceRect.width, 0.001f)
            assertEquals(from.sourceRect.height, returning.sourceRect.height, 0.001f)
            assertEquals(from.sourceAlpha, returning.sourceAlpha)
            assertEquals(from.sourceBlur, returning.sourceBlur)
            assertEquals(from.surfaceBlend, returning.surfaceBlend)
        }
    }

    @Test
    fun unionConnectsNearbySurfacesWithoutFillingADistantGap() {
        val frame = AppGlassPopupFrame(Rect(0f, 0f, 60f, 60f), 30f, 1f, 1f, 0f,
            sourceRect = Rect(70f, 0f, 130f, 60f), surfaceBlend = 28f)
        assertTrue(popupSurfaceDistance(65f, 30f, frame) < 0f)
        assertTrue(popupSurfaceDistance(65f, -30f, frame) > 0f)
        val separated = frame.copy(sourceRect = Rect(140f, 0f, 200f, 60f))
        assertTrue(popupSurfaceDistance(100f, 30f, separated) > 0f)
        val translated = frame.copy(rect = frame.rect.translate(Offset(-200f, -80f)),
            sourceRect = frame.sourceRect!!.translate(Offset(-200f, -80f)))
        assertEquals(popupSurfaceDistance(65f, 30f, frame), popupSurfaceDistance(-135f, -50f, translated))
    }

    @Test
    fun opticalEnvelopeContainsBothSourceAndCardOutsideTheCardBounds() {
        val bounds = glassPopupSurfaceBounds(card, anchor, 3f)
        for (point in listOf(card.topLeft, card.bottomRight, anchor.topLeft, anchor.bottomRight)) {
            assertTrue(bounds.contains(point))
        }
        val settled = AppGlassPopupMotion.opening(0.45f, card, anchor, 24f, true)
        assertEquals(null, settled.sourceRect)
        val end = AppGlassPopupMotion.closing(0.5f, 0.45f, card, anchor, 24f, true)
        assertEquals(0f, end.materialProgress)
        assertEquals(0f, end.surfaceBlend)
        assertEquals(anchor, end.sourceRect)
    }

    @Test
    fun selectedOptionStartsInViewWithClampedScrollAtBothEnds() {
        assertEquals(0, resolveGlassMenuInitialScroll(Rect(8f, 8f, 152f, 54f), 200, 800))
        assertEquals(423, resolveGlassMenuInitialScroll(Rect(8f, 500f, 152f, 546f), 200, 800))
        assertEquals(800, resolveGlassMenuInitialScroll(Rect(8f, 980f, 152f, 1026f), 200, 800))
        assertEquals(0, resolveGlassMenuInitialScroll(Rect(8f, 30f, 152f, 76f), 200, 0))
    }

    @Test
    fun menuWithoutASourceControlShrinksInsteadOfFlyingIntoItsAnchor() {
        val start = AppGlassPopupMotion.opening(0.45f, card, anchor, 24f)
        val end = AppGlassPopupMotion.closing(0.2f, 0.45f, card, anchor, 24f,
            startFrame = start, returnToControl = false)
        assertEquals(card.width * 0.8f, end.rect.width, 0.001f)
        assertEquals(0f, end.surfaceAlpha)
        assertEquals(0f, end.contentAlpha)
    }

    @Test
    fun playerPlacementsStayWithinPhoneAndNarrowSplitScreenHosts() {
        val popup = IntSize(160, 220)
        val host = IntSize(400, 800)
        assertEquals(IntOffset(16, 290), resolveAppGlassPopupPosition(null, false, popup, host, 16, 8,
            AppGlassMenuPlacement.START))
        assertEquals(IntOffset(224, 290), resolveAppGlassPopupPosition(null, false, popup, host, 16, 8,
            AppGlassMenuPlacement.END))
        assertEquals(IntOffset(224, 564), resolveAppGlassPopupPosition(null, false, popup, host, 16, 8,
            AppGlassMenuPlacement.END_BOTTOM))
        for (placement in AppGlassMenuPlacement.entries) {
            val position = resolveAppGlassPopupPosition(null, false, IntSize(84, 80), IntSize(100, 112), 16, 8, placement)
            assertTrue(position.x >= 0 && position.x + 84 <= 100)
            assertTrue(position.y >= 0 && position.y + 80 <= 112)
        }
    }

    @Test
    fun sourceAvailabilitySelectsTheSharedCloseClockAndFallbackHasNoReturningGlyph() {
        assertEquals(AppGlassPopupMotion.ReturnSeconds, AppGlassPopupMotion.closeDuration(true))
        assertEquals(AppGlassPopupMotion.ShrinkSeconds, AppGlassPopupMotion.closeDuration(false))
        val fallback = AppGlassPopupMotion.closing(
            AppGlassPopupMotion.closeDuration(false), AppGlassPopupMotion.OpenSeconds,
            card, anchor, 24f, sourceControl = false, returnToControl = false,
        )
        assertEquals(0f, fallback.surfaceAlpha)
        assertEquals(0f, fallback.sourceAlpha)
        assertEquals(null, fallback.sourceRect)
        val returned = AppGlassPopupMotion.closing(
            AppGlassPopupMotion.closeDuration(true), AppGlassPopupMotion.OpenSeconds,
            card, anchor, 24f, sourceControl = true,
        )
        assertEquals(anchor.center.x, returned.sourceRect!!.center.x, 0.001f)
        assertEquals(anchor.center.y, returned.sourceRect!!.center.y, 0.001f)
        assertEquals(0f, returned.surfaceAlpha)
        assertEquals(1f, returned.sourceAlpha)
    }

    @Test
    fun detachedSourceFadesItsRemainingOpticsInsteadOfLeavingASecondLobe() {
        val sourceFrame = AppGlassPopupMotion.opening(0.06f, card, anchor, 24f, true)
        val fallback = AppGlassPopupMotion.closing(
            AppGlassPopupMotion.ShrinkSeconds, 0.06f, card, anchor, 24f,
            sourceControl = false, startFrame = sourceFrame, returnToControl = false,
        )
        assertEquals(0f, fallback.sourceAlpha)
        assertEquals(0f, fallback.surfaceBlend)
        assertEquals(0f, fallback.materialProgress)
        assertEquals(0f, fallback.surfaceAlpha)
    }

    @Test
    fun openingCornersSettleWithoutUndershootingOrSnappingAtTheEnd() {
        for (panel in listOf(card, Rect(40f, 200f, 340f, 280f))) {
            for (step in 0..100) {
                val frame = AppGlassPopupMotion.opening(step * 0.005f, panel, anchor, 24f, true)
                assertTrue(frame.radius <= frame.rect.minDimension / 2f + 0.0001f)
                assertTrue(frame.radius >= minOf(24f, frame.rect.minDimension / 2f) - 0.0001f)
                if (step * 0.005f >= 0.3f) assertEquals(24f, frame.radius, 0.001f)
            }
            val before = AppGlassPopupMotion.opening(AppGlassPopupMotion.OpenSeconds - 0.0001f, panel, anchor, 24f, true)
            val after = AppGlassPopupMotion.opening(AppGlassPopupMotion.OpenSeconds, panel, anchor, 24f, true)
            assertEquals(before.radius, after.radius, 0.001f)
        }
    }

    @Test
    fun returnRoundnessProgressesToACapsuleForTallAndWideMenus() {
        for (panel in listOf(card, Rect(40f, 200f, 340f, 280f))) {
            var previous = 0f
            for (step in 0..100) {
                val frame = AppGlassPopupMotion.closing(step * 0.005f,
                    AppGlassPopupMotion.OpenSeconds, panel, anchor, 24f, true)
                val roundness = popupCornerRoundness(frame)
                assertTrue(roundness >= previous - 0.0001f)
                assertTrue(frame.radius <= frame.rect.minDimension / 2f + 0.0001f)
                previous = roundness
            }
            assertEquals(1f, previous, 0.0001f)
        }
    }

    @Test
    fun interruptedRoundnessStaysWithinTheCurrentShapeAcrossDensityAndTinyMenus() {
        for (density in listOf(1f, 3f)) {
            val panel = Rect(0f, 0f, 280f * density, 32f * density)
            val source = Rect(220f * density, 0f, 280f * density, 32f * density)
            val start = AppGlassPopupMotion.opening(0.12f, panel, source, 24f * density, true, density)
            val previous = AppGlassPopupMotion.opening(0.115f, panel, source, 24f * density, true, density)
            val target = AppGlassPopupMotion.opening(AppGlassPopupMotion.OpenSeconds,
                panel, source, 24f * density, true, density)
            for (step in 0..100) {
                val frame = AppGlassPopupMotion.retarget(step * 0.0025f, 0.25f, start, previous, 0.005f, target)
                assertTrue(frame.radius >= 0f && frame.radius <= frame.rect.minDimension / 2f + 0.001f)
                val (_, clipRadius) = glassPopupLocalOutline(frame, panel, panel.size, false)
                val scale = (frame.rect.width / panel.width).coerceAtLeast(0.001f)
                assertEquals(frame.radius, clipRadius * scale, 0.001f)
            }
        }
    }

    @Test
    fun repeatedDirectionChangesPreserveTheVisibleShapeAndCornerAtEachBoundary() {
        for (panel in listOf(card, Rect(40f, 200f, 340f, 280f))) {
            for (density in listOf(1f, 3f)) {
                val radius = 24f * density
                var start = AppGlassPopupMotion.opening(0.12f, panel, anchor, radius, true, density)
                var previous = AppGlassPopupMotion.opening(0.115f, panel, anchor, radius, true, density)
                repeat(20) { reversal ->
                    val closing = reversal % 2 == 0
                    val duration = if (closing) AppGlassPopupMotion.ReturnSeconds else 0.25f
                    val target = if (closing) {
                        AppGlassPopupMotion.closing(duration, 0.12f, panel, anchor, radius,
                            true, density, startFrame = start)
                    } else AppGlassPopupFrame(panel, radius.coerceAtMost(panel.minDimension / 2f), 1f, 1f, 0f)
                    assertEquals(start, AppGlassPopupMotion.retarget(0f, duration, start, previous, 0.005f, target))
                    val next = AppGlassPopupMotion.retarget(0.08f, duration, start, previous, 0.005f, target)
                    val before = AppGlassPopupMotion.retarget(0.075f, duration, start, previous, 0.005f, target)
                    assertTrue(next.radius.isFinite())
                    assertTrue(next.radius >= 0f && next.radius <= next.rect.minDimension / 2f)
                    assertTrue(next.contentAlpha in 0f..1f && next.surfaceAlpha in 0f..1f)
                    assertTrue(next.rect.width > 0f && next.rect.height > 0f)
                    start = next
                    previous = before
                }
            }
        }
    }

    @Test
    fun sourceMorphRevealsItsShellBeforeTextAndTransfersMaterialContinuously() {
        val start = AppGlassPopupMotion.opening(0f, card, anchor, 24f, true)
        val emerging = AppGlassPopupMotion.opening(0.02f, card, anchor, 24f, true)
        val settled = AppGlassPopupMotion.opening(0.45f, card, anchor, 24f, true)
        assertEquals(0f, start.materialProgress)
        assertEquals(0f, start.surfaceBlend)
        assertEquals(0f, emerging.contentAlpha)
        assertTrue(emerging.rect.width > start.rect.width)
        assertTrue(emerging.materialProgress > 0f)
        assertEquals(1f, settled.materialProgress)
        assertEquals(1f, settled.contentAlpha)
        assertEquals(null, settled.sourceRect)
        var previousMaterial = 0f
        for (step in 0..90) {
            val frame = AppGlassPopupMotion.opening(step * 0.005f, card, anchor, 24f, true)
            assertTrue(frame.materialProgress >= previousMaterial)
            previousMaterial = frame.materialProgress
            frame.sourceRect?.let { source ->
                assertTrue(frame.surfaceBlend <= minOf(frame.rect.minDimension, source.minDimension) * 0.65f + 0.001f)
            }
        }
    }

    @Test
    fun earlyCloseAndReopenPreserveMaterialAndNeckAtTheTurn() {
        val before = AppGlassPopupMotion.opening(0.065f, card, anchor, 24f, true)
        val start = AppGlassPopupMotion.opening(0.07f, card, anchor, 24f, true)
        val close = AppGlassPopupMotion.closing(0f, 0.07f, card, anchor, 24f, true, startFrame = start)
        assertEquals(start.materialProgress, close.materialProgress)
        assertEquals(start.surfaceBlend, close.surfaceBlend)
        assertEquals(start.contentAlpha, close.contentAlpha)
        val target = AppGlassPopupFrame(card, 24f, 1f, 1f, 0f)
        assertEquals(start, AppGlassPopupMotion.retarget(0f, 0.25f, start, before, 0.005f, target))
        assertEquals(target, AppGlassPopupMotion.retarget(0.25f, 0.25f, start, before, 0.005f, target))
    }

    @Test
    fun sourceOwnsItsBackingAtBothEndsAndMenuOwnsItWhenExpanded() {
        val start = AppGlassPopupMotion.opening(0f, card, anchor, 24f, true)
        val expanded = AppGlassPopupMotion.opening(0.45f, card, anchor, 24f, true)
        val returned = AppGlassPopupMotion.closing(0.5f, 0.45f, card, anchor, 24f, true)
        assertEquals(0f, popupMaterialOwnership(start, sourceAvailable = true))
        assertEquals(1f, popupMaterialOwnership(expanded, sourceAvailable = true))
        assertEquals(0f, popupMaterialOwnership(returned, sourceAvailable = true))
        assertEquals(1f, popupMaterialOwnership(start, sourceAvailable = false))
        assertEquals(1f, popupMaterialOwnership(returned, sourceAvailable = false))
        assertEquals(1f, popupMaterialOwnership(expanded.copy(sourceAlpha = 1f,
            materialProgress = 0f), sourceAvailable = true))
    }

    @Test
    fun materialHandoffStaysBoundedAndContinuousWhenAnEarlyCloseReverses() {
        val before = AppGlassPopupMotion.opening(0.055f, card, anchor, 24f, true)
        val start = AppGlassPopupMotion.opening(0.06f, card, anchor, 24f, true)
        val turned = AppGlassPopupMotion.closing(0f, 0.06f, card, anchor, 24f, true, startFrame = start)
        assertEquals(popupMaterialOwnership(start, true), popupMaterialOwnership(turned, true))
        val target = AppGlassPopupFrame(card, 24f, 1f, 1f, 0f)
        var previous = popupMaterialOwnership(start, true)
        for (step in 0..100) {
            val frame = AppGlassPopupMotion.retarget(step * 0.0025f, 0.25f,
                start, before, 0.005f, target)
            val ownership = popupMaterialOwnership(frame, sourceAvailable = true)
            assertTrue(ownership in 0f..1f)
            assertTrue(kotlin.math.abs(ownership - previous) < 0.04f)
            previous = ownership
        }
        assertEquals(1f, previous)
    }

}
