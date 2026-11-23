package com.android.purebilibili.core.ui.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExternalDropPolicyTest {

    @Test
    fun bilibiliUrlAndBvIdRouteToOpenLink() {
        val urlAction = resolveExternalDropAction("  https://www.bilibili.com/video/BV1xx411c7mD  ")
        assertEquals(
            ExternalDropAction.OpenLink("https://www.bilibili.com/video/BV1xx411c7mD"),
            urlAction,
        )

        val bvAction = resolveExternalDropAction("BV1xx411c7mD")
        assertEquals(ExternalDropAction.OpenLink("BV1xx411c7mD"), bvAction)

        val shortLinkAction = resolveExternalDropAction("看看这个 https://b23.tv/abcdefg")
        assertEquals(ExternalDropAction.OpenLink("看看这个 https://b23.tv/abcdefg"), shortLinkAction)
    }

    @Test
    fun plainTextRoutesToNormalizedSearchAndTruncatesLongInput() {
        val searchAction = resolveExternalDropAction("  塞尔达\n  王国之泪  ")
        assertEquals(ExternalDropAction.Search("塞尔达 王国之泪"), searchAction)

        val longText = "a".repeat(EXTERNAL_DROP_MAX_SEARCH_LENGTH + 40)
        val truncated = assertIs<ExternalDropAction.Search>(resolveExternalDropAction(longText))
        assertEquals(EXTERNAL_DROP_MAX_SEARCH_LENGTH, truncated.keyword.length)
    }

    @Test
    fun blankOrNullTextReturnsNull() {
        assertNull(resolveExternalDropAction(null))
        assertNull(resolveExternalDropAction(""))
        assertNull(resolveExternalDropAction("   \n\t  "))
    }
}
