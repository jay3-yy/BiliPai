package com.android.purebilibili.feature.search

import com.android.purebilibili.data.model.response.SearchType
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchEmptyStatePolicyTest {

    @Test
    fun `resolveSearchEmptyStateReason returns no results when server list is empty`() {
        assertEquals(
            SearchEmptyStateReason.NO_RESULTS,
            resolveSearchEmptyStateReason(
                rawResultCount = 0,
                visibleResultCount = 0
            )
        )
    }

    @Test
    fun `resolveSearchEmptyStateReason returns filtered out when local filters hide all results`() {
        assertEquals(
            SearchEmptyStateReason.FILTERED_OUT,
            resolveSearchEmptyStateReason(
                rawResultCount = 8,
                visibleResultCount = 0
            )
        )
    }

    @Test
    fun `resolveSearchEmptyStateCopy returns search guidance for no results`() {
        assertEquals(
            SearchEmptyStateCopy(
                title = "未找到相关视频",
                subtitle = "试试其他关键词或调整筛选条件"
            ),
            resolveSearchEmptyStateCopy(
                reason = SearchEmptyStateReason.NO_RESULTS,
                searchType = SearchType.VIDEO
            )
        )
    }

    @Test
    fun `resolveSearchEmptyStateCopy returns filtered guidance when hidden by rules`() {
        assertEquals(
            SearchEmptyStateCopy(
                title = "结果已被过滤",
                subtitle = "当前结果被屏蔽规则隐藏，请调整过滤设置后重试"
            ),
            resolveSearchEmptyStateCopy(
                reason = SearchEmptyStateReason.FILTERED_OUT,
                searchType = SearchType.VIDEO
            )
        )
    }

    @Test
    fun `resolveSearchEmptyStateCopy supports live room type`() {
        assertEquals(
            SearchEmptyStateCopy(
                title = "未找到相关直播间",
                subtitle = "试试其他关键词或调整筛选条件"
            ),
            resolveSearchEmptyStateCopy(
                reason = SearchEmptyStateReason.NO_RESULTS,
                searchType = SearchType.LIVE
            )
        )
    }

    @Test
    fun `resolveSearchEmptyStateCopy supports article type`() {
        assertEquals(
            SearchEmptyStateCopy(
                title = "未找到相关专栏",
                subtitle = "试试其他关键词或调整筛选条件"
            ),
            resolveSearchEmptyStateCopy(
                reason = SearchEmptyStateReason.NO_RESULTS,
                searchType = SearchType.ARTICLE
            )
        )
    }
}
