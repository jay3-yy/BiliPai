package com.android.purebilibili.feature.video.ui.components

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentThreadNavigationBlurTest {

    @Test
    fun `scales blur radius with display density and adds separation scrim`() {
        val phoneDark = resolveCommentThreadBlurFrame(
            progress = 1f,
            density = 3f,
            isDarkTheme = true,
            sdkInt = Build.VERSION_CODES.S,
        )
        val tabletLight = resolveCommentThreadBlurFrame(
            progress = 1f,
            density = 2f,
            isDarkTheme = false,
            sdkInt = Build.VERSION_CODES.S,
        )

        assertEquals(54f, phoneDark.radiusPx, 0.1f)
        assertEquals(1f, phoneDark.alpha, 0.001f)
        assertEquals(0.22f, phoneDark.scrimAlpha, 0.001f)

        assertEquals(32f, tabletLight.radiusPx, 0.1f)
        assertEquals(0.12f, tabletLight.scrimAlpha, 0.001f)
        assertTrue(phoneDark.radiusPx > tabletLight.radiusPx)
    }

    @Test
    fun `zero progress disables blur and scrim cleanly`() {
        val idle = resolveCommentThreadBlurFrame(
            progress = 0f,
            density = 3.5f,
            isDarkTheme = true,
            sdkInt = Build.VERSION_CODES.S,
        )
        assertEquals(0f, idle.radiusPx, 0.001f)
        assertEquals(0f, idle.alpha, 0.001f)
        assertEquals(0f, idle.scrimAlpha, 0.001f)
    }
}
