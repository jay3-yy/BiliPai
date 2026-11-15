package com.android.purebilibili.feature.home

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeCoverPreloadWindowPolicyTest {

    /**
     * 统一入口，默认值即规格里的“默认入参”：
     * total=100, columns=2, rows=2, FORWARD, 静止, 非省流, 非低端机, 非计费网络。
     */
    private fun window(
        visible: IntRange?,
        total: Int = 100,
        columns: Int = 2,
        rows: Int = 2,
        direction: HomeScrollDirection = HomeScrollDirection.FORWARD,
        scrolling: Boolean = false,
        dataSaver: Boolean = false,
        lowRam: Boolean = false,
        metered: Boolean = false,
        maxItems: Int = HOME_COVER_PRELOAD_MAX_ITEMS,
    ): HomeCoverPreloadWindow = resolveHomeCoverPreloadWindow(
        visibleVideoRange = visible,
        totalItemCount = total,
        columns = columns,
        visibleRowCount = rows,
        direction = direction,
        isScrolling = scrolling,
        isDataSaverActive = dataSaver,
        isLowRamDevice = lowRam,
        isMeteredNetwork = metered,
        maxItems = maxItems,
    )

    // W1
    @Test
    fun dataSaverProducesEmptyWindow() {
        val result = window(visible = 8..11, dataSaver = true)

        assertNull(result.near)
        assertNull(result.far)
        assertTrue(result.isEmpty)
        assertFalse(result.tintEnabled)
    }

    // W2
    @Test
    fun headerOnlyScreenProducesEmptyWindow() {
        val result = window(visible = null)

        assertTrue(result.isEmpty)
        assertEquals(0, result.nearCount)
        assertEquals(0, result.farCount)
    }

    // W3
    @Test
    fun emptyListProducesEmptyWindow() {
        val result = window(visible = null, total = 0)

        assertTrue(result.isEmpty)
    }

    // W4
    @Test
    fun outOfBoundsVisibleRangeProducesEmptyWindow() {
        val result = window(visible = 150..160, total = 100)

        assertTrue(result.isEmpty)
    }

    // W5
    @Test
    fun scrolledToBottomProducesEmptyWindow() {
        val result = window(visible = 98..99, total = 100)

        assertTrue(result.isEmpty)
    }

    // W6
    @Test
    fun singleVisibleRowLimitsFarToSingleRow() {
        val result = window(visible = 8..11, rows = 1)

        assertEquals(12..13, result.near)
        assertEquals(14..15, result.far)
    }

    // W7
    @Test
    fun farRowsAreClampedToThreeAtMost() {
        val result = window(visible = 8..11, rows = 5)

        assertEquals(12..13, result.near)
        assertEquals(14..19, result.far)
    }

    // W8
    @Test
    fun waterfallLargeRowCountStillClampsFarToThree() {
        val result = window(visible = 8..11, rows = 8)

        assertEquals(12..13, result.near)
        assertEquals(14..19, result.far)
    }

    // W9
    @Test
    fun singleColumnGridPreloadsOneColumnWide() {
        // 表里期望 far = 10..12（3 张），对应 farRows = 3，因此显式传 rows = 3。
        val result = window(visible = 8..8, columns = 1, rows = 3)

        assertEquals(9..9, result.near)
        assertEquals(10..12, result.far)

        // 同样的输入改用默认 2 行，则远场按 columns * farRows = 2 张收口。
        assertEquals(10..11, window(visible = 8..8, columns = 1).far)
    }

    // W10
    @Test
    fun threeColumnGridSpansThreeItemsPerRow() {
        val result = window(visible = 8..13, columns = 3, rows = 3)

        assertEquals(14..16, result.near)
        assertEquals(17..25, result.far)
    }

    // W11
    @Test
    fun scrollingForwardKeepsNearAndDropsFar() {
        val result = window(visible = 8..11, scrolling = true)

        assertEquals(12..13, result.near)
        assertNull(result.far)
        assertTrue(result.tintEnabled)
        assertFalse(result.isEmpty)
    }

    // W12
    @Test
    fun scrollingBackwardPreloadsAboveVisibleRange() {
        val result = window(
            visible = 8..11,
            direction = HomeScrollDirection.BACKWARD,
            scrolling = true,
        )

        assertEquals(6..7, result.near)
        assertNull(result.far)
    }

    // W13
    @Test
    fun backwardNearWindowIsClippedAtTop() {
        val result = window(visible = 1..2, direction = HomeScrollDirection.BACKWARD)

        assertEquals(0..0, result.near)
        assertNull(result.far)
    }

    // W14
    @Test
    fun backwardAtVeryTopProducesEmptyWindow() {
        val result = window(visible = 0..1, direction = HomeScrollDirection.BACKWARD)

        assertTrue(result.isEmpty)
    }

    // W15
    @Test
    fun lowRamDeviceSettledKeepsNearOnly() {
        val result = window(visible = 8..11, lowRam = true)

        assertEquals(12..13, result.near)
        assertNull(result.far)
    }

    // W16
    @Test
    fun lowRamDeviceScrollingKeepsNearOnly() {
        val result = window(visible = 8..11, lowRam = true, scrolling = true)

        assertEquals(12..13, result.near)
        assertNull(result.far)
    }

    // W17
    @Test
    fun dataSaverWhileScrollingProducesEmptyWindow() {
        val result = window(visible = 8..11, dataSaver = true, scrolling = true)

        assertTrue(result.isEmpty)
    }

    // W18
    @Test
    fun dirtyColumnCountFallsBackToSingleColumn() {
        // 表里期望 far = 13..15（3 张），对应 farRows = 3，因此显式传 rows = 3。
        val result = window(visible = 8..11, columns = 0, rows = 3)

        assertEquals(12..12, result.near)
        assertEquals(13..15, result.far)

        // 换成默认 2 行时，远场严格等于 columns(归一化为 1) * farRows(2) = 2 张。
        assertEquals(13..14, window(visible = 8..11, columns = 0).far)
    }

    // W19
    @Test
    fun itemCapIsReachedExactlyWithoutOverflow() {
        val result = window(visible = 8..13, columns = 3, rows = 3)

        assertEquals(14..16, result.near)
        assertEquals(17..25, result.far)
        assertEquals(12, result.nearCount + result.farCount)
        assertEquals(3, result.nearCount)
        assertEquals(9, result.farCount)
    }

    // W20
    @Test
    fun meteredNetworkKeepsNearAndDropsFar() {
        val result = window(visible = 8..11, metered = true)

        assertEquals(12..13, result.near)
        assertNull(result.far)
    }

    // W21
    @Test
    fun unknownDirectionWhileScrollingIsTreatedAsForward() {
        val idle = window(visible = 8..11, direction = HomeScrollDirection.IDLE, scrolling = true)
        val forward = window(visible = 8..11, direction = HomeScrollDirection.FORWARD, scrolling = true)

        assertEquals(forward.near, idle.near)
        assertEquals(12..13, idle.near)
        assertNull(idle.far)
    }

    // W22
    @Test
    fun overflowTruncatesFarBeforeTouchingNear() {
        val result = window(visible = 8..15, columns = 4, rows = 3, maxItems = 12)

        assertEquals(16..19, result.near)
        assertEquals(4, result.nearCount)
        // 远场从 20 起，只剩 8 张预算，于是从远端截断到 20..27。
        assertEquals(20..27, result.far)
        assertEquals(12, result.nearCount + result.farCount)
    }

    // W23
    @Test
    fun scrollDirectionIsDerivedFromPreviousFirstVisibleIndex() {
        assertEquals(
            HomeScrollDirection.BACKWARD,
            resolveHomeScrollDirection(previousFirstVisibleIndex = 12, currentFirstVisibleIndex = 8),
        )
        assertEquals(
            HomeScrollDirection.FORWARD,
            resolveHomeScrollDirection(previousFirstVisibleIndex = 8, currentFirstVisibleIndex = 12),
        )
        assertEquals(
            HomeScrollDirection.IDLE,
            resolveHomeScrollDirection(previousFirstVisibleIndex = 8, currentFirstVisibleIndex = 8),
        )
        assertEquals(
            HomeScrollDirection.IDLE,
            resolveHomeScrollDirection(previousFirstVisibleIndex = null, currentFirstVisibleIndex = 8),
        )
    }

    // W24
    @Test
    fun decodeBudgetIsDerivedFromPerItemBytes() {
        // 1080 * 607 * 4 = 2,622,240 字节；10MB / 2,622,240 = 3.99 → 3 张。
        assertEquals(3, resolveCoverPreloadItemBudget(1080, 607))
        // 小卡片（512 * 320 * 4 = 655,360）字节预算可容纳 16 张，但被全局上限压到 12。
        assertTrue(resolveCoverPreloadItemBudget(512, 320) >= 12)
        assertEquals(HOME_COVER_PRELOAD_MAX_ITEMS, resolveCoverPreloadItemBudget(512, 320))
        // 未测量尺寸 / 非法尺寸不允许崩溃，返回 0。
        assertEquals(0, resolveCoverPreloadItemBudget(0, 0))
        assertEquals(0, resolveCoverPreloadItemBudget(-1, 320))
        assertEquals(0, resolveCoverPreloadItemBudget(512, -1))
        assertEquals(0, resolveCoverPreloadItemBudget(512, 320, maxDecodeBytes = 0L))
    }

    // W25
    @Test
    fun windowInvariantsHoldForRandomInputs() {
        val random = Random(20240521)
        val directions = HomeScrollDirection.values()

        repeat(200) {
            val total = random.nextInt(1, 200)
            val columns = random.nextInt(-2, 7)
            val rows = random.nextInt(0, 13)
            val maxItems = random.nextInt(0, 21)
            val firstVisible = random.nextInt(0, total)
            val lastVisible = minOf(total - 1, firstVisible + random.nextInt(0, 12))
            val direction = directions[random.nextInt(directions.size)]
            val isScrolling = random.nextInt(0, 2) == 0
            val isDataSaverActive = random.nextInt(0, 2) == 0
            val isLowRamDevice = random.nextInt(0, 2) == 0
            val isMeteredNetwork = random.nextInt(0, 2) == 0

            val result = window(
                visible = firstVisible..lastVisible,
                total = total,
                columns = columns,
                rows = rows,
                direction = direction,
                scrolling = isScrolling,
                dataSaver = isDataSaverActive,
                lowRam = isLowRamDevice,
                metered = isMeteredNetwork,
                maxItems = maxItems,
            )

            val context = "total=$total columns=$columns rows=$rows maxItems=$maxItems " +
                "visible=$firstVisible..$lastVisible direction=$direction scrolling=$isScrolling " +
                "saver=$isDataSaverActive lowRam=$isLowRamDevice metered=$isMeteredNetwork " +
                "near=${result.near} far=${result.far}"

            if (isDataSaverActive) {
                assertTrue(result.isEmpty, "data saver must produce an empty window: $context")
            }
            if (maxItems <= 0) {
                assertTrue(result.isEmpty, "zero item budget must produce an empty window: $context")
            }
            if (isScrolling) {
                assertNull(result.far, "scrolling must never use the far window: $context")
            }

            result.near?.let { near ->
                assertTrue(near.first >= 0, "near must not be negative: $context")
                assertTrue(near.last < total, "near must stay inside the list: $context")
                assertTrue(near.first <= near.last, "near must be a valid range: $context")
            }
            result.far?.let { far ->
                assertTrue(far.first >= 0, "far must not be negative: $context")
                assertTrue(far.last < total, "far must stay inside the list: $context")
                assertTrue(far.first <= far.last, "far must be a valid range: $context")
            }
            val near = result.near
            val far = result.far
            if (near != null && far != null) {
                assertTrue(
                    far.last < near.first || far.first > near.last,
                    "near and far must not overlap: $context",
                )
            }
            assertTrue(
                result.nearCount + result.farCount <= maxItems.coerceAtLeast(0),
                "near + far must respect the item budget: $context",
            )
        }
    }

    // 补充：容量为 0 时整体关闭。
    @Test
    fun zeroItemBudgetProducesEmptyWindow() {
        val result = window(visible = 8..11, maxItems = 0)

        assertTrue(result.isEmpty)
        assertFalse(result.tintEnabled)
    }

    // 补充：单侧超过容量时 near 自身被截断，且不再分配远场。
    @Test
    fun nearIsTruncatedWhenColumnsExceedBudget() {
        val forward = window(visible = 8..11, columns = 4, rows = 2, maxItems = 2)
        assertEquals(12..13, forward.near)
        assertEquals(2, forward.nearCount)
        assertNull(forward.far)

        val backward = window(
            visible = 20..23,
            columns = 4,
            rows = 2,
            direction = HomeScrollDirection.BACKWARD,
            maxItems = 2,
        )
        // 向上保留离可见区最近的尾部 18..19。
        assertEquals(18..19, backward.near)
        assertEquals(2, backward.nearCount)
        assertNull(backward.far)
    }

    // 补充：BACKWARD 静止时远场铺在可见区上方，超预算时从头部截断。
    @Test
    fun backwardFarWindowGrowsAboveAndTruncatesFromHead() {
        val result = window(
            visible = 50..53,
            columns = 4,
            rows = 3,
            direction = HomeScrollDirection.BACKWARD,
        )

        assertEquals(46..49, result.near)
        // 远场理论区间 34..45 共 12 张，预算只剩 8 张 → 从头部截断为 38..45。
        assertEquals(38..45, result.far)
        assertEquals(12, result.nearCount + result.farCount)
    }

    // 补充：BACKWARD 远场被列表顶部夹住。
    @Test
    fun backwardFarWindowIsClippedAtTop() {
        val result = window(visible = 4..9, columns = 3, rows = 2, direction = HomeScrollDirection.BACKWARD)

        assertEquals(1..3, result.near)
        assertEquals(0..0, result.far)
    }

    // 补充：非法可见区间（first > last）视为无可用锚点。
    @Test
    fun invertedVisibleRangeProducesEmptyWindow() {
        val result = window(visible = 11..8)

        assertTrue(result.isEmpty)
    }

    // 补充：只有一项且已到底时没有下一屏。
    @Test
    fun singleItemListAtBottomProducesEmptyWindow() {
        val result = window(visible = 0..0, total = 1)

        assertTrue(result.isEmpty)
    }

    // 补充：tintEnabled 只跟随 near，远场存在与否不影响取色。
    @Test
    fun tintFollowsNearWindowOnly() {
        val settled = window(visible = 8..11)
        assertTrue(settled.tintEnabled)
        assertTrue(settled.far != null)

        // 到底部时 near 为空：既不取色，窗口整体也为空（far 依赖 near 起算）。
        val bottom = window(visible = 98..99)
        assertFalse(bottom.tintEnabled)
        assertTrue(bottom.isEmpty)

        // 砍掉远场（低端机）后 near 仍在，取色开关不受影响。
        val lowRam = window(visible = 8..11, lowRam = true)
        assertNull(lowRam.far)
        assertTrue(lowRam.tintEnabled)
    }
}
