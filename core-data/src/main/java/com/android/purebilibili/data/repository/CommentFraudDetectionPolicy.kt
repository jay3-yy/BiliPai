package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.CommentFraudStatus

data class CommentPresenceProbe(
    val requestSucceeded: Boolean,
    val found: Boolean,
    val deletedHint: Boolean = false,
    val invisible: Boolean = false
)

fun resolveReplyFraudStatus(
    guestProbe: CommentPresenceProbe,
    authProbe: CommentPresenceProbe,
    confirmedNotFoundAfterRetry: Boolean
): CommentFraudStatus {
    if (guestProbe.requestSucceeded && guestProbe.found) {
        return if (guestProbe.invisible) {
            CommentFraudStatus.INVISIBLE
        } else {
            CommentFraudStatus.NORMAL
        }
    }

    if (authProbe.requestSucceeded && authProbe.found) {
        // 自己被拉黑/评论被标记隐身时，auth 侧同样能读到 invisible 标记
        if (authProbe.invisible) {
            return CommentFraudStatus.INVISIBLE
        }
        return if (guestProbe.requestSucceeded) {
            CommentFraudStatus.SHADOW_BANNED
        } else {
            CommentFraudStatus.UNKNOWN
        }
    }

    if (!authProbe.requestSucceeded) {
        return CommentFraudStatus.UNKNOWN
    }

    if (authProbe.deletedHint || confirmedNotFoundAfterRetry) {
        return CommentFraudStatus.DELETED
    }

    return CommentFraudStatus.UNKNOWN
}

fun resolveRootFraudStatus(
    guestSeekProbe: CommentPresenceProbe,
    authSeekProbe: CommentPresenceProbe,
    guestReplyPageVisible: Boolean?,
    confirmedNotFoundAfterRetry: Boolean,
    manual: Boolean = false,
): CommentFraudStatus {
    if (guestSeekProbe.requestSucceeded && guestSeekProbe.found) {
        return if (guestSeekProbe.invisible) {
            CommentFraudStatus.INVISIBLE
        } else {
            CommentFraudStatus.NORMAL
        }
    }

    if (authSeekProbe.requestSucceeded && authSeekProbe.found) {
        if (!guestSeekProbe.requestSucceeded) return CommentFraudStatus.UNKNOWN
        // 自己被拉黑/评论被标记隐身时，auth 侧同样能读到 invisible 标记
        if (authSeekProbe.invisible) {
            return CommentFraudStatus.INVISIBLE
        }
        return when (guestReplyPageVisible) {
            true -> if (manual) CommentFraudStatus.NORMAL else CommentFraudStatus.UNDER_REVIEW
            false -> CommentFraudStatus.SHADOW_BANNED
            null -> CommentFraudStatus.UNKNOWN
        }
    }

    if (!authSeekProbe.requestSucceeded) {
        return CommentFraudStatus.UNKNOWN
    }

    if (authSeekProbe.deletedHint || confirmedNotFoundAfterRetry) {
        return CommentFraudStatus.DELETED
    }

    return CommentFraudStatus.UNKNOWN
}
