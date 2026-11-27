package com.android.purebilibili.feature.home

import androidx.compose.material3.DrawerValue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeDrawerGesturePolicyTest {

    @Test
    fun `closed drawer only opens from explicit avatar action`() {
        assertFalse(
            shouldEnableHomeDrawerGestures(
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Closed,
            )
        )
    }

    @Test
    fun `drawer keeps horizontal close gesture while open or opening`() {
        assertTrue(
            shouldEnableHomeDrawerGestures(
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Open,
            )
        )
        assertTrue(
            shouldEnableHomeDrawerGestures(
                currentValue = DrawerValue.Open,
                targetValue = DrawerValue.Open,
            )
        )
        assertTrue(
            shouldEnableHomeDrawerGestures(
                currentValue = DrawerValue.Open,
                targetValue = DrawerValue.Closed,
            )
        )
    }

    @Test
    fun `first top tab swipe opens drawer only when enabled and settled on page zero`() {
        assertTrue(
            shouldAllowHomeFirstTabSwipeToOpenDrawer(
                isSwipeDrawerEnabled = true,
                isHomeDrawerAvailable = true,
                hideTopTabs = false,
                isTopLevelActive = true,
                currentPage = 0,
                isPagerScrollInProgress = false,
                isDrawerClosed = true,
            )
        )
        assertFalse(
            shouldAllowHomeFirstTabSwipeToOpenDrawer(
                isSwipeDrawerEnabled = false,
                isHomeDrawerAvailable = true,
                hideTopTabs = false,
                isTopLevelActive = true,
                currentPage = 0,
                isPagerScrollInProgress = false,
                isDrawerClosed = true,
            )
        )
        assertFalse(
            shouldAllowHomeFirstTabSwipeToOpenDrawer(
                isSwipeDrawerEnabled = true,
                isHomeDrawerAvailable = true,
                hideTopTabs = false,
                isTopLevelActive = true,
                currentPage = 1,
                isPagerScrollInProgress = false,
                isDrawerClosed = true,
            )
        )
        assertFalse(
            shouldAllowHomeFirstTabSwipeToOpenDrawer(
                isSwipeDrawerEnabled = true,
                isHomeDrawerAvailable = true,
                hideTopTabs = true,
                isTopLevelActive = true,
                currentPage = 0,
                isPagerScrollInProgress = false,
                isDrawerClosed = true,
            )
        )
    }
}
