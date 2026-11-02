package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.SpaceArticleItem
import com.android.purebilibili.data.model.response.SpaceArticleStats
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaceArticlePresentationPolicyTest {
    @Test
    fun `opus preserves formatted counts and does not fabricate missing counts`() {
        assertEquals("2.3万点赞", buildSpaceArticleStatsText(SpaceArticleItem(displayStats = listOf("2.3万点赞"))))
        assertEquals("图文", buildSpaceArticleStatsText(SpaceArticleItem(displayStats = emptyList())))
    }


    @Test
    fun `empty remote view renders an unavailable reading count instead of fake zero`() {
        val article = SpaceArticleItem(
            stats = SpaceArticleStats(view = 0, like = 11_000),
        )

        assertEquals("图文 · —阅读 · 1.1万点赞", buildSpaceArticleStatsText(article))
    }

    @Test
    fun `available remote view remains visible`() {
        val article = SpaceArticleItem(
            stats = SpaceArticleStats(view = 12_000, like = 345),
        )

        assertEquals("图文 · 1.2万阅读 · 345点赞", buildSpaceArticleStatsText(article))
    }
}
