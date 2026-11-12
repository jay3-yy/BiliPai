package com.android.purebilibili.core.ui.popup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PopupScrollHintTest {
    @Test
    fun `thumb tracks top middle and bottom of overflowing content`() {
        val top = requireNotNull(resolvePopupScrollThumb(300f, 400, 800, 0, 28f))
        val middle = requireNotNull(resolvePopupScrollThumb(300f, 400, 800, 400, 28f))
        val bottom = requireNotNull(resolvePopupScrollThumb(300f, 400, 800, 800, 28f))
        assertEquals(100f, top.length, 0.001f)
        assertEquals(0f, top.top, 0.001f)
        assertEquals(100f, middle.top, 0.001f)
        assertEquals(300f, bottom.top + bottom.length, 0.001f)
    }

    @Test
    fun `short or unmeasured content has no hint and tiny tracks do not overflow`() {
        assertNull(resolvePopupScrollThumb(300f, 400, 0, 0, 28f))
        assertNull(resolvePopupScrollThumb(300f, 0, Int.MAX_VALUE, 0, 28f))
        assertNull(resolvePopupScrollThumb(0f, 400, 800, 0, 28f))
        val tiny = requireNotNull(resolvePopupScrollThumb(12f, 100, 10000, 10000, 28f))
        assertTrue(tiny.top >= 0f)
        assertEquals(12f, tiny.top + tiny.length, 0.001f)
    }
}
