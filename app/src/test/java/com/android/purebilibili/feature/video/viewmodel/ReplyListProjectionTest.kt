package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.data.model.response.*
import org.junit.Assert.*
import org.junit.Test

class ReplyListProjectionTest {
    @Test
    fun unblockingRestoresRootAndChildrenFromLoadedSourceWithoutChangingCounts() {
        val child = ReplyItem(rpid = 2, member = ReplyMember(mid = "42"), content = ReplyContent(message = "普通回复"))
        val root = ReplyItem(rpid = 1, content = ReplyContent(message = "剧透"), replies = listOf(child), rcount = 50)
        val source = listOf(root)
        val projection = ReplyListProjection()
        val keywordRules = ContentFilterSnapshot(keywords = listOf("剧透"), keywordFilteringEnabled = true)
        assertTrue(projection.project(source, keywordRules).isEmpty())
        val childBlocked = projection.project(source, ContentFilterSnapshot(blockedMids = setOf(42)))
        assertEquals(50, childBlocked.single().rcount)
        assertTrue(childBlocked.single().replies!!.isEmpty())
        val restored = projection.project(source, ContentFilterSnapshot())
        assertEquals(listOf(root), restored)
        assertEquals(listOf(child), restored.single().replies)
        assertEquals(listOf(child), source.single().replies)
        // 切换内容身份后不能恢复上一条评论。
        val next = listOf(ReplyItem(rpid = 3))
        assertEquals(listOf(3L), projection.project(next, ContentFilterSnapshot()).map { it.rpid })
    }
}
