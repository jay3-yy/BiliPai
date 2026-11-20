package com.android.purebilibili.core.filter

import com.android.purebilibili.core.store.ContentFilterHistory
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.core.store.FilteredContentKind
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.plugin.sdk.FeedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentVideoFilterHistoryTest {
    @Test
    fun `the recorded video is the one actually hidden by the shared title rule`() {
        val rules = ContentFilterSnapshot(
            keywords = listOf("试映剧透"),
            scopes = setOf(ContentFilterScope.VIDEO),
            keywordFilteringEnabled = true,
        )
        val hidden = VideoItem(bvid = "BVFilterHistory01", title = "试映剧透与结局解析", owner = Owner(name = "测试投稿者"))
        val visible = hidden.copy(bvid = "BVFilterHistory02", title = "试映场次预告")

        assertFalse(rules.keepsVideo(hidden, FeedKind.HOME_RECOMMEND))
        assertTrue(rules.keepsVideo(visible, FeedKind.HOME_RECOMMEND))
        val history = ContentFilterHistory.snapshot(FilteredContentKind.VIDEO)
        val hit = history.single { it.id == hidden.bvid }
        assertEquals(hidden.title, hit.text)
        assertEquals(hidden.owner.name, hit.author)
        assertEquals(ContentFilterReason.KEYWORD, hit.reason)
        assertFalse(history.any { it.id == visible.bvid })
    }
}
