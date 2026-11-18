package com.android.purebilibili.core.ui

import com.android.purebilibili.core.theme.AppUiStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptivePullToRefreshPolicyTest {

    @Test
    fun `miuix style routes to miuix bridged renderer`() {
        assertEquals(
            PresetPrimitiveRenderer.MIUIX_BRIDGED,
            resolveAdaptivePullToRefreshRenderer(
                uiStyle = AppUiStyle.MIUIX
            )
        )
    }

    @Test
    fun `material3 style keeps material renderer`() {
        assertEquals(
            PresetPrimitiveRenderer.MATERIAL3,
            resolveAdaptivePullToRefreshRenderer(
                uiStyle = AppUiStyle.MATERIAL3
            )
        )
    }

    @Test
    fun `miuix refresh texts use localized home hints`() {
        assertEquals(
            listOf("下拉刷新...", "松手刷新", "正在刷新...", "刷新完成"),
            resolveMiuixPullToRefreshTexts()
        )
    }

    @Test
    fun `pull refresh indicator top inset clamps overlay chrome height`() {
        assertEquals(0f, resolvePullRefreshIndicatorTopInsetDp(-8f), 0.001f)
        assertEquals(0f, resolvePullRefreshIndicatorTopInsetDp(0f), 0.001f)
        assertEquals(96f, resolvePullRefreshIndicatorTopInsetDp(96f), 0.001f)
        assertEquals(0f, resolveScaffoldedPullRefreshIndicatorTopInsetDp(), 0.001f)
    }

    @Test
    fun `miuix pull container stays mounted when idle even if offscreen or unfocused`() {
        assertTrue(
            shouldMountMiuixPullToRefresh(
                enabled = true,
                isRefreshing = false,
                allowContentScrollWhileRefreshing = false,
                reduceMotion = false,
                inViewport = false,
                resumed = false,
            )
        )
        assertFalse(
            shouldMountMiuixPullToRefresh(
                enabled = false,
                isRefreshing = false,
                allowContentScrollWhileRefreshing = false,
                reduceMotion = false,
                inViewport = true,
                resumed = true,
            )
        )
    }

    @Test
    fun `miuix pull container unmounts offscreen spinner when refreshing`() {
        assertFalse(
            shouldMountMiuixPullToRefresh(
                enabled = true,
                isRefreshing = true,
                allowContentScrollWhileRefreshing = false,
                reduceMotion = false,
                inViewport = false,
                resumed = true,
            )
        )
        assertFalse(
            shouldMountMiuixPullToRefresh(
                enabled = true,
                isRefreshing = true,
                allowContentScrollWhileRefreshing = false,
                reduceMotion = false,
                inViewport = true,
                resumed = false,
            )
        )
        assertTrue(
            shouldMountMiuixPullToRefresh(
                enabled = true,
                isRefreshing = true,
                allowContentScrollWhileRefreshing = false,
                reduceMotion = false,
                inViewport = true,
                resumed = true,
            )
        )
        assertFalse(
            shouldMountMiuixPullToRefresh(
                enabled = true,
                isRefreshing = true,
                allowContentScrollWhileRefreshing = true,
                reduceMotion = false,
                inViewport = true,
                resumed = true,
            )
        )
    }

    @Test
    fun `material pull indicator stays visible while dragging even if decorative gate is false`() {
        assertTrue(
            shouldRenderMaterialPullToRefreshIndicator(
                gateActive = false,
                distanceFraction = 0.25f,
                isRefreshing = false,
                inViewport = true,
                resumed = true,
            )
        )
        assertTrue(
            shouldRenderMaterialPullToRefreshIndicator(
                gateActive = false,
                distanceFraction = 0f,
                isRefreshing = true,
                inViewport = true,
                resumed = true,
            )
        )
        assertFalse(
            shouldRenderMaterialPullToRefreshIndicator(
                gateActive = false,
                distanceFraction = 0f,
                isRefreshing = true,
                inViewport = false,
                resumed = true,
            )
        )
    }
}
