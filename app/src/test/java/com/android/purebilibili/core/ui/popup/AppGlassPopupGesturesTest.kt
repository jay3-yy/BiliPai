package com.android.purebilibili.core.ui.popup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppGlassPopupGesturesTest {
    @Test
    fun tallDraggedPopupKeepsItsWholeFrameInsideSafeBounds() {
        val bounds = Rect(16f, 80f, 384f, 880f)
        val frame = AppGlassPopupFrame(Rect(90f, -100f, 390f, 1100f), 24f, 1.2f, 1f, 0f)
        val fitted = fitPopupElasticFrame(frame, bounds)
        assertTrue(fitted.rect.top >= bounds.top)
        assertTrue(fitted.rect.bottom <= bounds.bottom)
        assertTrue(fitted.rect.left >= bounds.left)
        assertTrue(fitted.rect.right <= bounds.right)
        assertEquals(0.76f, fitted.contentScale, 0.0001f)
        assertEquals(frame.rect.center, fitted.rect.center)
    }

    @Test
    fun menuFollowsInsideBeforeSwitchingContinuouslyToEdgeRubberBand() {
        val bounds = Rect(40f, 100f, 340f, 600f)
        val origin = bounds.center
        val inside = popupMenuDragPull(origin + Offset(0f, 100f), origin, bounds, true)
        assertTrue(inside.y > 4f)
        assertEquals(Offset.Zero, popupMenuDragPull(origin + Offset(0f, 100f), origin, bounds, false))
        val atEdge = popupMenuDragPull(Offset(origin.x, bounds.bottom), origin, bounds, true)
        val pastEdge = popupMenuDragPull(Offset(origin.x, bounds.bottom + 0.01f), origin, bounds, true)
        assertEquals(atEdge.y, pastEdge.y, 0.01f)
        val far = popupMenuDragPull(Offset(origin.x, bounds.bottom + 100f), origin, bounds, true)
        assertTrue(far.y > atEdge.y)
    }

    @Test
    fun tallPanelMovesBothWaysInsteadOfHavingItsCenterClampedBack() {
        val bounds = Rect(16f, 80f, 384f, 880f)
        val base = AppGlassPopupFrame(Rect(40f, 90f, 360f, 870f), 24f, 1f, 1f, 0f)
        for (dy in listOf(-30f, 30f)) {
            val dragged = popupElasticFrame(base, 1f, Offset(0f, dy), 1f)
            val fitted = fitPopupElasticFrame(dragged, bounds)
            assertEquals(base.rect.center.y + dy, fitted.rect.center.y, 0.001f)
            assertTrue(fitted.rect.top >= bounds.top - 0.001f)
            assertTrue(fitted.rect.bottom <= bounds.bottom + 0.001f)
        }
    }

    @Test
    fun pressedGeometryReturnsWithoutSnappingBackToTheUnpressedCard() {
        val card = Rect(40f, 200f, 240f, 500f)
        val anchor = Rect(180f, 170f, 240f, 202f)
        val base = AppGlassPopupMotion.opening(0.45f, card, anchor, 24f, true)
        val pressed = popupElasticFrame(base, 1f, Offset(12f, -8f), 1f)
        assertTrue(pressed.rect.width > base.rect.width)
        val returning = AppGlassPopupMotion.closing(0f, 0.45f, card, anchor, 24f, true, startFrame = pressed)
        assertEquals(pressed.rect.left, returning.rect.left, 0.001f)
        assertEquals(pressed.rect.top, returning.rect.top, 0.001f)
        assertEquals(pressed.rect.width, returning.rect.width, 0.001f)
        assertEquals(pressed.rect.height, returning.rect.height, 0.001f)
        assertEquals(pressed.contentScale, returning.contentScale)
        assertEquals(pressed.radius, returning.radius)
    }
    @Test
    fun draggingEnlargesSurfaceAndContentAlongTheSameAxis() {
        val base = AppGlassPopupFrame(Rect(0f, 0f, 240f, 360f), 24f, 1f, 1f, 0f)
        val pressed = popupElasticFrame(base, 1f, Offset.Zero, 1f)
        val horizontal = popupElasticFrame(base, 1f, Offset(30f, 0f), 1f)
        val vertical = popupElasticFrame(base, 1f, Offset(0f, -30f), 1f)
        assertTrue(pressed.rect.width > base.rect.width && pressed.rect.height > base.rect.height)
        assertTrue(horizontal.rect.width > pressed.rect.width)
        assertEquals(pressed.rect.height, horizontal.rect.height, 0.001f)
        assertTrue(vertical.rect.height > pressed.rect.height)
        assertEquals(pressed.rect.width, vertical.rect.width, 0.001f)
        assertEquals(pressed.contentScale, horizontal.contentScale)
        assertEquals(pressed.contentScale, vertical.contentScale)
        assertEquals(pressed.radius, horizontal.radius)
        assertEquals(horizontal.rect.width / base.rect.width,
            horizontal.contentScale * horizontal.contentStretchX, 0.001f)
        assertEquals(vertical.rect.height / base.rect.height,
            vertical.contentScale * vertical.contentStretchY, 0.001f)
    }

    @Test
    fun dragBlendFinishesBeforeOpeningDoesAndHasNoSettlementStep() {
        assertEquals(0f, popupDragBlend(0f, 0.45f))
        assertTrue(popupDragBlend(0.2f, 0.45f) in 0.01f..0.99f)
        assertEquals(1f, popupDragBlend(0.32f, 0.45f))
        assertEquals(popupDragBlend(0.449f, 0.45f), popupDragBlend(0.45f, 0.45f))
    }

    @Test
    fun rubberBandHasMeasuredResistanceAndIndependentBoundedAxes() {
        val pull = popupRubberBandPull(Offset(120f, -80f), 240f)
        assertEquals(29.240507f, pull.x, 0.0001f)
        assertEquals(-23.937824f, pull.y, 0.0001f)
        assertEquals(Offset.Zero, popupRubberBandPull(Offset.Zero, 240f))
        val far = popupRubberBandPull(Offset(100000f, -100000f), 240f)
        assertTrue(far.x < 52.5f && far.y > -52.5f)
        assertEquals(far.x, -far.y)
        assertEquals(Offset.Zero, popupRubberBandPull(Offset(10f, 20f), 0f))
    }

    @Test
    fun popupPressGrowthIsCappedAndVerticalDragAddsThePull() {
        val base = AppGlassPopupFrame(Rect(0f, 0f, 240f, 360f), 24f, 1f, 1f, 0f)
        val actual = popupElasticFrame(base, 1f, Offset(30f, -20f), 1f)
        val swell = 1f + 16f / 360f
        assertEquals((240f + 15f) * swell, actual.rect.width, 0.001f)
        assertEquals((360f + 20f) * swell, actual.rect.height, 0.001f)
        assertEquals(base.rect.center + Offset(30f, -20f), actual.rect.center)
    }

    @Test
    fun confirmationDialogUsesItsOwnFourDpGrowthAndThreeDpPullLimit() {
        val base = AppGlassPopupFrame(Rect(0f, 0f, 300f, 200f), 24f, 1f, 1f, 0f)
        val pull = popupRubberBandPull(Offset(10000f, -10000f), 200f, maxPull = 3f)
        assertTrue(pull.x < 3f && pull.y > -3f)
        val pressed = popupElasticFrame(base, 1f, Offset.Zero, 1f, maxGrowthDp = 4f)
        assertEquals(304f, pressed.rect.width, 0.001f)
        assertEquals(200f * 304f / 300f, pressed.rect.height, 0.001f)
    }

    @Test
    fun draggedContentKeepsItsAxesWhenClosingOrReversing() {
        val base = AppGlassPopupFrame(Rect(40f, 200f, 280f, 560f), 24f, 1f, 1f, 0f)
        val dragged = popupElasticFrame(base, 1f, Offset(30f, -20f), 1f)
        val anchor = Rect(240f, 170f, 280f, 202f)
        val closing = AppGlassPopupMotion.closing(0f, 0.45f, base.rect, anchor, 24f, true, startFrame = dragged)
        assertEquals(dragged, closing)
        val resumed = AppGlassPopupMotion.retarget(0f, 0.25f, dragged, dragged, 0.01f, base)
        assertEquals(dragged, resumed)
        val settled = AppGlassPopupMotion.retarget(0.25f, 0.25f, dragged, dragged, 0.01f, base)
        assertEquals(1f, settled.contentStretchX)
        assertEquals(1f, settled.contentStretchY)
    }

    @Test
    fun globalTransformRemainsConsistentAcrossDisplayDensity() {
        val base = AppGlassPopupFrame(Rect(0f, 0f, 240f, 360f), 24f, 1f, 1f, 0f)
        val normal = popupElasticFrame(base, 1f, Offset(120f, -80f), 1f)
        val scaled = popupElasticFrame(base.copy(rect = Rect(0f, 0f, 720f, 1080f), radius = 72f),
            1f, Offset(360f, -240f), 3f)
        assertEquals(normal.rect.width, scaled.rect.width / 3f, 0.001f)
        assertEquals(normal.rect.height, scaled.rect.height / 3f, 0.001f)
        assertEquals(normal.radius, scaled.radius / 3f, 0.001f)
    }

    @Test
    fun tallAndShortMenusUseSameRubberBandLimitButSizeDependentStretch() {
        val short = AppGlassPopupFrame(Rect(0f, 0f, 160f, 240f), 24f, 1f, 1f, 0f)
        val tall = short.copy(rect = Rect(0f, 0f, 160f, 560f))
        val pull = popupRubberBandPull(Offset(0f, -120f), 160f)
        val shortDragged = popupElasticFrame(short, 0f, pull, 1f)
        val tallDragged = popupElasticFrame(tall, 0f, pull, 1f)
        assertEquals(shortDragged.rect.height - short.rect.height,
            tallDragged.rect.height - tall.rect.height, 0.001f)
        assertEquals(shortDragged.rect.center.y - short.rect.center.y,
            tallDragged.rect.center.y - tall.rect.center.y, 0.001f)
    }
}
