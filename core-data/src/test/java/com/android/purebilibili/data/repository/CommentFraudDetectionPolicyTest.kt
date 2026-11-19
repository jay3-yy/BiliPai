package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.CommentFraudStatus
import org.junit.Test
import org.junit.Assert.assertEquals

class CommentFraudDetectionPolicyTest {

    @Test
    fun `reply status should be normal when guest probe found`() {
        val status = resolveReplyFraudStatus(
            guestProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            authProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.NORMAL, status)
    }

    @Test
    fun `reply status should be invisible when guest probe found invisible comment`() {
        val status = resolveReplyFraudStatus(
            guestProbe = CommentPresenceProbe(requestSucceeded = true, found = true, invisible = true),
            authProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.INVISIBLE, status)
    }

    @Test
    fun `reply status should be shadow banned when only auth probe found`() {
        val status = resolveReplyFraudStatus(
            guestProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.SHADOW_BANNED, status)
    }

    @Test
    fun `reply status should be invisible when auth probe found invisible comment`() {
        val status = resolveReplyFraudStatus(
            guestProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authProbe = CommentPresenceProbe(requestSucceeded = true, found = true, invisible = true),
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.INVISIBLE, status)
    }

    @Test
    fun `reply status should be deleted when auth probe reports deleted hint`() {
        val status = resolveReplyFraudStatus(
            guestProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authProbe = CommentPresenceProbe(
                requestSucceeded = true,
                found = false,
                deletedHint = true
            ),
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.DELETED, status)
    }

    @Test
    fun `reply status should be unknown when guest probe failed`() {
        val status = resolveReplyFraudStatus(
            guestProbe = CommentPresenceProbe(requestSucceeded = false, found = false),
            authProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.UNKNOWN, status)
    }

    @Test
    fun `root status should be under review when auth found and guest reply page visible`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            guestReplyPageVisible = true,
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.UNDER_REVIEW, status)
    }

    @Test
    fun `root status should be shadow banned when auth found and guest reply page deleted`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            guestReplyPageVisible = false,
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.SHADOW_BANNED, status)
    }

    @Test
    fun `root status should be invisible when auth seek found invisible comment`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true, invisible = true),
            guestReplyPageVisible = null,
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.INVISIBLE, status)
    }

    @Test
    fun `root status should be deleted when auth seek probe has deleted hint`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = CommentPresenceProbe(
                requestSucceeded = true,
                found = false,
                deletedHint = true
            ),
            guestReplyPageVisible = null,
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.DELETED, status)
    }

    @Test
    fun `root status should be normal when guest seek found comment`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            guestReplyPageVisible = null,
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.NORMAL, status)
    }

    @Test
    fun `root status should be invisible when guest seek found invisible comment`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true, invisible = true),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            guestReplyPageVisible = null,
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.INVISIBLE, status)
    }

    @Test
    fun `root status should stay unknown when probes are not conclusive`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            guestReplyPageVisible = null,
            confirmedNotFoundAfterRetry = false
        )
        assertEquals(CommentFraudStatus.UNKNOWN, status)
    }

    @Test
    fun `root status should be deleted when confirmed not found after retry`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            guestReplyPageVisible = null,
            confirmedNotFoundAfterRetry = true
        )
        assertEquals(CommentFraudStatus.DELETED, status)
    }
    @Test
    fun `manual check accepts a public reply page after the root leaves the latest list`() {
        val status = resolveRootFraudStatus(
            guestSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = false),
            authSeekProbe = CommentPresenceProbe(requestSucceeded = true, found = true),
            guestReplyPageVisible = true,
            confirmedNotFoundAfterRetry = false,
            manual = true,
        )
        assertEquals(CommentFraudStatus.NORMAL, status)
    }

}
