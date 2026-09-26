package com.android.purebilibili.feature.list

import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonListHeaderCollapsePolicyTest {

    @Test
    fun `collapse mode falls back to reverse scroll behavior`() {
        assertEquals(
            CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL,
            CommonListHeaderCollapseMode.fromValue(999)
        )
    }

    @Test
    fun `max collapse reserves status bar inset for collapsible modes`() {
        assertEquals(
            0f,
            resolveCommonListHeaderMaxCollapsePxForMode(
                collapseMode = CommonListHeaderCollapseMode.ALWAYS_VISIBLE,
                fixedTopBarHeightPx = 200,
                statusBarHeightPx = 48f,
            )
        )
        assertEquals(
            152f,
            resolveCommonListHeaderMaxCollapsePxForMode(
                collapseMode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL,
                fixedTopBarHeightPx = 200,
                statusBarHeightPx = 48f,
            )
        )
        assertEquals(
            152f,
            resolveCommonListHeaderMaxCollapsePxForMode(
                collapseMode = CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY,
                fixedTopBarHeightPx = 200,
                statusBarHeightPx = 48f,
            )
        )
    }

    @Test
    fun `always visible mode ignores scroll deltas`() {
        assertEquals(
            0f,
            resolveCommonListHeaderOffsetPx(
                currentOffsetPx = -80f,
                scrollDeltaYPx = -40f,
                maxCollapsePx = 160f,
                isAtTop = false,
                mode = CommonListHeaderCollapseMode.ALWAYS_VISIBLE
            )
        )
    }

    @Test
    fun `reverse scroll mode collapses and restores within bounds`() {
        assertEquals(
            -32f,
            resolveCommonListHeaderOffsetPx(
                currentOffsetPx = 0f,
                scrollDeltaYPx = -32f,
                maxCollapsePx = 160f,
                isAtTop = true,
                mode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            )
        )
        assertEquals(
            -100f,
            resolveCommonListHeaderOffsetPx(
                currentOffsetPx = -60f,
                scrollDeltaYPx = -40f,
                maxCollapsePx = 160f,
                isAtTop = false,
                mode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            )
        )
        assertEquals(
            -20f,
            resolveCommonListHeaderOffsetPx(
                currentOffsetPx = -60f,
                scrollDeltaYPx = 40f,
                maxCollapsePx = 160f,
                isAtTop = false,
                mode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            )
        )
    }

    @Test
    fun `top only mode stays collapsed until list reaches top`() {
        assertEquals(
            -160f,
            resolveCommonListHeaderOffsetPx(
                currentOffsetPx = -160f,
                scrollDeltaYPx = 48f,
                maxCollapsePx = 160f,
                isAtTop = false,
                mode = CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY
            )
        )
        assertEquals(
            0f,
            resolveCommonListHeaderOffsetPx(
                currentOffsetPx = -160f,
                scrollDeltaYPx = 0f,
                maxCollapsePx = 160f,
                isAtTop = true,
                mode = CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY
            )
        )
    }

    @Test
    fun `settled page at top expands header`() {
        assertEquals(
            0f,
            resolveCommonListHeaderOffsetForSettledContent(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                maxCollapsePx = 240f,
                mode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            )
        )
    }

    @Test
    fun `settled page away from top keeps header collapsed`() {
        assertEquals(
            -240f,
            resolveCommonListHeaderOffsetForSettledContent(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                maxCollapsePx = 240f,
                mode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            )
        )
        assertEquals(
            -240f,
            resolveCommonListHeaderOffsetForSettledContent(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 1,
                maxCollapsePx = 240f,
                mode = CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY
            )
        )
    }

    @Test
    fun `always visible mode stays expanded after page switch`() {
        assertEquals(
            0f,
            resolveCommonListHeaderOffsetForSettledContent(
                firstVisibleItemIndex = 8,
                firstVisibleItemScrollOffset = 120,
                maxCollapsePx = 240f,
                mode = CommonListHeaderCollapseMode.ALWAYS_VISIBLE
            )
        )
    }

    @Test
    fun `header ignores gesture delta when list consumes no vertical scroll`() {
        assertEquals(
            -40f,
            resolveCommonListHeaderOffsetAfterContentScroll(
                currentOffsetPx = -40f,
                contentConsumedDeltaYPx = 0f,
                maxCollapsePx = 240f,
                isAtTop = false,
                mode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            )
        )
    }

    @Test
    fun `header follows vertical scroll consumed by list`() {
        assertEquals(
            -100f,
            resolveCommonListHeaderOffsetAfterContentScroll(
                currentOffsetPx = -40f,
                contentConsumedDeltaYPx = -60f,
                maxCollapsePx = 240f,
                isAtTop = false,
                mode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL
            )
        )
    }
}
