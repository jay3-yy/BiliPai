package com.android.purebilibili.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.basic.RefreshState

class MiuixPullReboundGuardTest {

    private var refreshState: RefreshState = RefreshState.Pulling
    private var dragOffset = 0f
    private val guard = MiuixPullReboundGuard(
        currentRefreshState = { refreshState },
        currentDragOffset = { dragOffset },
    )

    @Test
    fun `release below threshold arms the rebound guard`() {
        refreshState = RefreshState.Pulling
        dragOffset = 120f

        guard.onPointerEvent(pressed = true)
        guard.onPointerEvent(pressed = false)

        assertTrue(guard.armed)
        assertEquals(1, guard.armGeneration)
    }

    @Test
    fun `release at the refresh threshold keeps native refresh handling`() {
        refreshState = RefreshState.ThresholdReached
        dragOffset = 240f

        guard.onPointerEvent(pressed = true)
        guard.onPointerEvent(pressed = false)

        assertFalse(guard.armed)
        assertEquals(0, guard.armGeneration)
    }

    @Test
    fun `release without pull distance does not arm`() {
        refreshState = RefreshState.Pulling
        dragOffset = 0f

        guard.onPointerEvent(pressed = true)
        guard.onPointerEvent(pressed = false)

        assertFalse(guard.armed)
    }

    @Test
    fun `running rebound spring keeps the guard armed until it settles`() {
        armGuard(offset = 160f)

        guard.onOffsetChanged(
            offset = 120f,
            refreshState = RefreshState.Pulling,
            isRefreshing = false,
        )
        guard.onOffsetChanged(
            offset = 40f,
            refreshState = RefreshState.Pulling,
            isRefreshing = false,
        )

        assertTrue(guard.armed)

        guard.onOffsetChanged(
            offset = 0f,
            refreshState = RefreshState.Pulling,
            isRefreshing = false,
        )

        assertFalse(guard.armed)
    }

    @Test
    fun `pulling down again hands control back to the native container`() {
        armGuard(offset = 100f)

        guard.onOffsetChanged(
            offset = 140f,
            refreshState = RefreshState.Pulling,
            isRefreshing = false,
        )

        assertFalse(guard.armed)
    }

    @Test
    fun `refreshing or leaving the pulling state disarms the guard`() {
        armGuard(offset = 100f)
        guard.onOffsetChanged(
            offset = 80f,
            refreshState = RefreshState.Pulling,
            isRefreshing = true,
        )
        assertFalse(guard.armed)

        armGuard(offset = 100f)
        guard.onOffsetChanged(
            offset = 80f,
            refreshState = RefreshState.Refreshing,
            isRefreshing = false,
        )
        assertFalse(guard.armed)
    }

    @Test
    fun `missing spring start disarms instead of swallowing later upward input`() {
        armGuard(offset = 100f)

        guard.confirmSpringStarted()

        assertFalse(guard.armed)
    }

    @Test
    fun `started spring keeps the guard armed after confirmation`() {
        armGuard(offset = 100f)
        dragOffset = 60f

        guard.confirmSpringStarted()

        assertTrue(guard.armed)
    }

    @Test
    fun `guard consumes upward touch input only while armed`() {
        val connection = miuixReboundGuardConnection(guard)
        val upward = Offset(0f, -24f)

        assertEquals(Offset.Zero, connection.onPreScroll(upward, NestedScrollSource.UserInput))

        armGuard(offset = 100f)
        assertEquals(Offset.Zero, connection.onPreScroll(upward, NestedScrollSource.UserInput))
        guard.onPointerEvent(pressed = true)

        assertEquals(upward, connection.onPreScroll(upward, NestedScrollSource.UserInput))
        assertEquals(
            Offset.Zero,
            connection.onPreScroll(Offset(0f, 24f), NestedScrollSource.UserInput),
        )
        assertEquals(
            Offset.Zero,
            connection.onPreScroll(upward, NestedScrollSource.SideEffect),
        )

        refreshState = RefreshState.Refreshing
        assertEquals(
            Offset.Zero,
            connection.onPreScroll(upward, NestedScrollSource.UserInput),
        )

        refreshState = RefreshState.Pulling
        dragOffset = 0f
        assertEquals(
            Offset.Zero,
            connection.onPreScroll(upward, NestedScrollSource.UserInput),
        )
    }

    private fun armGuard(offset: Float) {
        refreshState = RefreshState.Pulling
        dragOffset = offset
        guard.onPointerEvent(pressed = true)
        guard.onPointerEvent(pressed = false)
    }
}
