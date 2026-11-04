package com.android.purebilibili.core.ui.blur

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagerBackdropRecordingTest {
    @Test
    fun swipeKeepsBothPartiallyExposedPagesRecording() {
        assertTrue(pagerBackdropIntersectsViewport(-120, 400, 0, 400))
        assertTrue(pagerBackdropIntersectsViewport(280, 400, 0, 400))
    }

    @Test
    fun retainedPagesOutsideTheViewportDoNotRecord() {
        assertFalse(pagerBackdropIntersectsViewport(-400, 400, 0, 400))
        assertFalse(pagerBackdropIntersectsViewport(400, 400, 0, 400))
        assertFalse(pagerBackdropIntersectsViewport(800, 400, 0, 400))
        assertFalse(pagerBackdropIntersectsViewport(0, 0, 0, 400))
        assertFalse(pagerBackdropIntersectsViewport(0, 400, 0, 0))
    }

    @Test
    fun visibleContentPaddingAndMultiplePagesUseTheActualViewport() {
        assertTrue(pagerBackdropIntersectsViewport(-400, 400, -24, 824))
        assertTrue(pagerBackdropIntersectsViewport(800, 400, -24, 824))
        assertFalse(pagerBackdropIntersectsViewport(-424, 400, -24, 824))
        assertFalse(pagerBackdropIntersectsViewport(824, 400, -24, 824))
    }
}
