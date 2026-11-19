package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.data.model.response.ReplyItem
import kotlin.test.Test
import kotlin.test.assertEquals

class SubReplyThreadRowsTest {
    @Test
    fun `reply stays visible until a later page provides its parent`() {
        val child = ReplyItem(rpid = 30L, parent = 20L)
        val sibling = ReplyItem(rpid = 40L, parent = 10L)
        val firstPage = listOf(child, sibling)

        val partial = buildSubReplyThreadRows(rootId = 10L, links = firstPage.subReplyThreadLinks())
        assertEquals(firstPage, partial.map { firstPage[it.sourceIndex] })

        val parent = ReplyItem(rpid = 20L, parent = 10L)
        val loaded = firstPage + parent
        val complete = buildSubReplyThreadRows(rootId = 10L, links = loaded.subReplyThreadLinks())
        assertEquals(listOf(sibling, parent, child), complete.map { loaded[it.sourceIndex] })
        assertEquals(
            listOf(sibling, parent),
            complete.visibleThreadRows(setOf(parent.rpid)).map { loaded[it.sourceIndex] },
        )
    }

    @Test
    fun `opening a target reply reveals its ancestors and keeps other branches collapsed`() {
        val branch = ReplyItem(rpid = 20L, parent = 10L)
        val parent = ReplyItem(rpid = 30L, parent = branch.rpid)
        val target = ReplyItem(rpid = 40L, parent = parent.rpid)
        val otherBranch = ReplyItem(rpid = 50L, parent = 10L)
        val otherChild = ReplyItem(rpid = 60L, parent = otherBranch.rpid)
        val loaded = listOf(branch, parent, target, otherBranch, otherChild)
        val rows = buildSubReplyThreadRows(rootId = 10L, links = loaded.subReplyThreadLinks())
        val collapsed = setOf(branch.rpid, parent.rpid, otherBranch.rpid)

        assertEquals(
            listOf(branch, otherBranch),
            rows.visibleThreadRows(collapsed).map { loaded[it.sourceIndex] },
        )
        val revealed = rows.visibleThreadRows(collapsed - rows.collapsedBranchesHiding(target.rpid, collapsed))
        assertEquals(
            listOf(branch, parent, target, otherBranch),
            revealed.map { loaded[it.sourceIndex] },
        )
        assertEquals(3, resolveSubReplyTargetListIndex(10L, loaded, target.rpid, revealed))
    }

    @Test
    fun `reaction changes preserve thread links while rows show updated reply data`() {
        val reply = ReplyItem(rpid = 20L, parent = 10L, like = 2)
        val child = ReplyItem(rpid = 30L, parent = 20L, like = 3)
        val loaded = listOf(child, reply)
        val links = loaded.subReplyThreadLinks()
        val rows = buildSubReplyThreadRows(rootId = 10L, links = links)

        val updatedChild = child.copy(like = 4, action = 1)
        val updated = listOf(updatedChild, reply)
        assertEquals(links, updated.subReplyThreadLinks())
        assertEquals(listOf(reply, updatedChild), rows.map { updated[it.sourceIndex] })
    }
}
