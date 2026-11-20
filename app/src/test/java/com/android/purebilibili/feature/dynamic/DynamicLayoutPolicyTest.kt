package com.android.purebilibili.feature.dynamic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.SettingsManager

class DynamicLayoutPolicyTest {

    @Test
    fun `manual prepend anchor is limited to the single-column list`() {
        assertFalse(
            shouldUseDynamicManualPrependAnchor(SettingsManager.DynamicFeedLayoutMode.WATERFALL)
        )
        assertTrue(
            shouldUseDynamicManualPrependAnchor(SettingsManager.DynamicFeedLayoutMode.LIST)
        )
    }

    @Test
    fun `dynamic feed uses the compact single-column content width`() {
        assertEquals(480.dp, resolveDynamicFeedMaxWidth())
    }

    @Test
    fun `dynamic timeline expands into dense adaptive columns on wide screens`() {
        assertEquals(1840.dp, resolveDynamicTimelineMaxWidth())
        assertEquals(360.dp, resolveDynamicTimelineMinColumnWidth())
        assertEquals(18.dp, resolveDynamicTimelineHorizontalSpacing())
        assertEquals(6.dp, resolveDynamicTimelineVerticalSpacing())
    }

    @Test
    fun `dynamic video card keeps vertical layout on wide content`() {
        assertEquals(
            DynamicVideoCardLayoutMode.VERTICAL,
            resolveDynamicVideoCardLayoutMode(containerWidthDp = 620)
        )
    }

    @Test
    fun `dynamic video card keeps vertical layout on compact content`() {
        assertEquals(
            DynamicVideoCardLayoutMode.VERTICAL,
            resolveDynamicVideoCardLayoutMode(containerWidthDp = 540)
        )
    }

    @Test
    fun `dynamic cards use flat list spacing`() {
        assertEquals(0.dp, resolveDynamicCardOuterPadding())
        assertEquals(12.dp, resolveDynamicCardContentPadding())
    }

    @Test
    fun `dynamic top areas tighten user list and tab spacing`() {
        assertEquals(10.dp, resolveDynamicHorizontalUserListHorizontalPadding())
        assertEquals(10.dp, resolveDynamicHorizontalUserListSpacing())
        assertEquals(14.dp, resolveDynamicTopBarHorizontalPadding())
    }

    @Test
    fun `dynamic tab indicator follows pager position within tab bounds`() {
        assertEquals(1.4f, resolveDynamicTabIndicatorPosition(1, 1.4f, 3))
        assertEquals(2f, resolveDynamicTabIndicatorPosition(1, 5f, 3))
        assertEquals(1f, resolveDynamicTabIndicatorPosition(1, Float.NaN, 3))
        assertEquals(0f, resolveDynamicTabIndicatorPosition(1, 1f, 0))
    }

    @Test
    fun `dynamic sidebar divider starts below top chrome`() {
        assertEquals(
            74.dp,
            resolveDynamicSidebarDividerTopOffset(topPadding = 24.dp)
        )
    }

    @Test
    fun `dynamic sidebar trims width without crowding avatar affordances`() {
        assertEquals(68.dp, resolveDynamicSidebarWidth(isExpanded = true))
        assertEquals(60.dp, resolveDynamicSidebarWidth(isExpanded = false))
    }

    @Test
    fun `dynamic user live badge only appears for live authors`() {
        assertEquals(true, shouldShowDynamicUserLiveBadge(isLive = true))
        assertEquals(false, shouldShowDynamicUserLiveBadge(isLive = false))
    }

    @Test
    fun `dynamic share and comment actions keep readable text labels`() {
        assertEquals("转发", resolveDynamicActionButtonText(label = "转发", count = 0))
        assertEquals("评论", resolveDynamicActionButtonText(label = "评论", count = 0))
        assertEquals("9", resolveDynamicActionButtonText(label = "评论", count = 9))
        assertEquals("1.2k", resolveDynamicActionButtonText(label = "评论", count = 1200))
        assertEquals("85", resolveDynamicActionButtonText(label = "点赞", count = 85))
        assertEquals("点赞", resolveDynamicActionButtonText(label = "点赞", count = 0))
    }

    @Test
    fun `dynamic action row uses equal slots so like button is not starved by earlier labels`() {
        assertEquals(1f, resolveDynamicActionButtonSlotWeight(), 0f)
        assertEquals(8.dp, resolveDynamicActionButtonSpacing())
    }

    @Test
    fun `dynamic action text keeps share and comment counts when slot is narrow`() {
        assertEquals(
            "1.2k",
            resolveDynamicActionButtonText(label = "评论", count = 1200, slotWidthDp = 96)
        )
        assertEquals(
            "转发 3万",
            resolveDynamicActionButtonText(label = "转发", count = 34000, slotWidthDp = 96)
        )
        assertEquals(
            "1.2k",
            resolveDynamicActionButtonText(label = "评论", count = 1200, slotWidthDp = 140)
        )
    }

    @Test
    fun `dynamic sidebar beyond bounds adapts to scroll state`() {
        assertEquals(
            6,
            resolveDynamicSidebarBeyondBoundsItemCount(isScrollInProgress = false)
        )
        assertEquals(
            15,
            resolveDynamicSidebarBeyondBoundsItemCount(isScrollInProgress = true)
        )
    }

    @Test
    fun `dynamic sidebar fling damping uses stable reduction factor`() {
        assertEquals(
            0.70f,
            resolveDynamicSidebarFlingDampingFactor(),
            0.001f
        )
    }

    @Test
    fun `dynamic sidebar cascade animation only animates initial visible items before scroll`() {
        // Initial entrance on first visible items
        assertTrue(
            shouldAnimateSidebarItemCascade(
                index = 0,
                hasScrolled = false,
                initialEntranceActive = true
            )
        )
        assertTrue(
            shouldAnimateSidebarItemCascade(
                index = 7,
                hasScrolled = false,
                initialEntranceActive = true
            )
        )

        // Beyond max cascade items - should not delay or animate
        assertFalse(
            shouldAnimateSidebarItemCascade(
                index = 8,
                hasScrolled = false,
                initialEntranceActive = true
            )
        )
        assertFalse(
            shouldAnimateSidebarItemCascade(
                index = 25,
                hasScrolled = false,
                initialEntranceActive = true
            )
        )

        // After user scrolled - should never delay or animate
        assertFalse(
            shouldAnimateSidebarItemCascade(
                index = 0,
                hasScrolled = true,
                initialEntranceActive = true
            )
        )
        assertFalse(
            shouldAnimateSidebarItemCascade(
                index = 3,
                hasScrolled = true,
                initialEntranceActive = true
            )
        )

        // After initial entrance window elapsed
        assertFalse(
            shouldAnimateSidebarItemCascade(
                index = 0,
                hasScrolled = false,
                initialEntranceActive = false
            )
        )
    }

    @Test
    fun `dynamic sidebar avatar url formats correctly for various schemes`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/face/test.jpg",
            resolveDynamicSidebarUserAvatarUrl("https://i0.hdslb.com/bfs/face/test.jpg")
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/face/test.jpg",
            resolveDynamicSidebarUserAvatarUrl("http://i0.hdslb.com/bfs/face/test.jpg")
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/face/test.jpg",
            resolveDynamicSidebarUserAvatarUrl("//i0.hdslb.com/bfs/face/test.jpg")
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/face/test.jpg",
            resolveDynamicSidebarUserAvatarUrl("i0.hdslb.com/bfs/face/test.jpg")
        )
        assertEquals(
            "",
            resolveDynamicSidebarUserAvatarUrl("   ")
        )
    }

    @Test
    fun `dynamic sidebar avatar prefetch extracts candidate urls within limits`() {
        val users = listOf(
            SidebarUser(uid = 1L, name = "A", face = "https://a.jpg"),
            SidebarUser(uid = 2L, name = "B", face = ""),
            SidebarUser(uid = 3L, name = "C", face = "//c.jpg"),
            SidebarUser(uid = 4L, name = "D", face = "http://d.jpg")
        )
        val prefetchUrls = resolveDynamicSidebarAvatarPrefetchUrls(users, startIndex = 0, limit = 3)
        assertEquals(
            listOf("https://a.jpg", "https://c.jpg"),
            prefetchUrls
        )

        val scrolledUrls = resolveDynamicSidebarAvatarPrefetchUrls(users, startIndex = 2, limit = 2)
        assertEquals(
            listOf("https://c.jpg", "https://d.jpg"),
            scrolledUrls
        )
    }
}
