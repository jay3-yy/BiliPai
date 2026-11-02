package com.android.purebilibili.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SpaceProfileRequestTest {
    @Test
    fun chargingFilterSurvivesSearchSortAndPaginationWhileOrdinaryVideosRemainUnfiltered() {
        val charging = buildSpaceVideoQueryParams(42L, 3, 30, "click", 17, "演示", "charging")
        assertEquals(mapOf("mid" to "42", "pn" to "3", "ps" to "30", "order" to "click",
            "tid" to "17", "keyword" to "演示", "special_type" to "charging"), charging)
        val ordinary = buildSpaceVideoQueryParams(42L, 1, 30, "pubdate", 0, "", null)
        assertFalse("special_type" in ordinary)
        assertFalse("keyword" in ordinary)
    }
}
