package com.android.purebilibili.feature.search

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchLandingUiPolicyTest {

    @Test
    fun `search discovery section uses original style when trending action is absent`() {
        assertTrue(shouldUseOriginalSearchDiscoverStyle(showTrendingAction = false))
        assertFalse(shouldUseOriginalSearchDiscoverStyle(showTrendingAction = true))
    }

    @Test
    fun `search discovery section keeps two columns to match original layout`() {
        assertEquals(2, resolveSearchKeywordSectionColumns(requestedColumns = 1, showTrendingAction = false))
        assertEquals(2, resolveSearchKeywordSectionColumns(requestedColumns = 4, showTrendingAction = false))
        assertEquals(3, resolveSearchKeywordSectionColumns(requestedColumns = 3, showTrendingAction = true))
    }

    @Test
    fun `search discovery original cell colors use themed primary tint`() {
        val light = resolveSearchDiscoverOriginalCellColors(lightColorScheme())
        val dark = resolveSearchDiscoverOriginalCellColors(darkColorScheme())

        assertTrue(light.containerColor.alpha > 0f)
        assertTrue(light.borderColor.alpha > 0f)
        assertTrue(dark.containerColor.alpha > light.containerColor.alpha)
    }

    @Test
    fun `search discovery original subtitle keeps all non blank metadata`() {
        assertEquals("15小时前更新", resolveSearchDiscoverOriginalSubtitle("15小时前更新"))
        assertEquals("47分钟前更新", resolveSearchDiscoverOriginalSubtitle("47分钟前更新"))
        assertEquals("关注的 UP 主", resolveSearchDiscoverOriginalSubtitle("关注的 UP 主"))
        assertEquals("与最近搜索相关", resolveSearchDiscoverOriginalSubtitle("与最近搜索相关"))
        assertEquals(null, resolveSearchDiscoverOriginalSubtitle("   "))
    }
}
