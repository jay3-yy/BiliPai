package com.android.purebilibili.feature.bangumi

import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiDetailHeroPolicyTest {
    @Test
    fun collapseProgressTracksHeaderAndStaysCollapsedAfterItLeavesViewport() {
        assertEquals(0f, bangumiHeroCollapseFraction(0, 0, 320f))
        assertEquals(0.5f, bangumiHeroCollapseFraction(0, 160, 320f))
        assertEquals(1f, bangumiHeroCollapseFraction(0, 500, 320f))
        assertEquals(1f, bangumiHeroCollapseFraction(1, 0, 320f))
        assertEquals(0f, bangumiHeroCollapseFraction(0, -10, 0f))
    }
}
