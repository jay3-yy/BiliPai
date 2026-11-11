package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcException
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem

internal enum class CommentReadApiMode {
    AUTH,
    GUEST
}

internal data class CommentReadPlan(
    val primary: CommentReadApiMode,
    val fallback: CommentReadApiMode?
)

internal fun resolveCommentReadPlan(hasSession: Boolean): CommentReadPlan {
    return if (hasSession) {
        CommentReadPlan(
            primary = CommentReadApiMode.AUTH,
            fallback = CommentReadApiMode.GUEST
        )
    } else {
        CommentReadPlan(
            primary = CommentReadApiMode.GUEST,
            fallback = CommentReadApiMode.AUTH
        )
    }
}

internal fun shouldFallbackCommentRead(code: Int): Boolean {
    // -403: WBI 签名失败（桌面文档注明 wbi/main 签错返回 -403 而非 -352）
    return code in setOf(-101, -111, -352, -403, -412)
}

/** 评论区限制是读取结果，不能被后续 REST 请求的错误覆盖。 */
internal fun resolveGrpcCommentReadRestriction(failure: Throwable?): CommentReadException? {
    val code = (failure as? BiliGrpcException)?.businessCode ?: return null
    if (code != 12002 && code != 12061) return null
    return CommentReadException(code).apply { initCause(failure) }
}

internal fun hasRenderableCommentPayload(data: ReplyData?): Boolean {
    if (data == null) return false
    return data.replies.orEmpty().isNotEmpty() ||
        data.hots.orEmpty().isNotEmpty() ||
        data.collectTopReplies().isNotEmpty() ||
        data.voteCard != null ||
        data.control?.inputDisable == true ||
        !data.control?.sectionNotice().isNullOrBlank() ||
        !data.control?.emptyMessage().isNullOrBlank()
}

private fun collectRenderableComments(data: ReplyData): Sequence<ReplyItem> {
    return sequenceOf(
        data.collectTopReplies().asSequence(),
        data.hots.orEmpty().asSequence(),
        data.replies.orEmpty().asSequence()
    ).flatten()
}

internal fun hasAnyReplyLocation(data: ReplyData?): Boolean {
    if (data == null) return false
    return collectRenderableComments(data)
        .any { !it.replyControl?.location.isNullOrBlank() }
}

internal fun shouldFallbackGrpcCommentReadOnMissingLocation(data: ReplyData?): Boolean {
    return data != null &&
        collectRenderableComments(data).any() &&
        !hasAnyReplyLocation(data)
}

internal fun shouldFallbackGuestHotCommentReadOnEmptySuccess(
    primaryMode: CommentReadApiMode,
    page: Int,
    mode: Int,
    responseCode: Int,
    data: ReplyData?
): Boolean {
    return primaryMode == CommentReadApiMode.GUEST &&
        shouldFallbackHotCommentReadOnEmptySuccess(
            page = page,
            mode = mode,
            responseCode = responseCode,
            data = data,
        )
}

internal fun shouldFallbackHotCommentReadOnEmptySuccess(
    page: Int,
    mode: Int,
    responseCode: Int,
    data: ReplyData?,
): Boolean {
    return page == 1 &&
        mode == 3 &&
        responseCode == 0 &&
        !hasRenderableCommentPayload(data)
}

internal fun shouldFallbackCommentReadOnEmptyRenderableSuccess(
    responseCode: Int,
    data: ReplyData?
): Boolean {
    return responseCode == 0 &&
        data != null &&
        data.getAllCount() > 0 &&
        !hasRenderableCommentPayload(data)
}

/** 保留服务端业务码，UI 可区分评论区限制与临时网络失败。 */
class CommentReadException(val code: Int, serverMessage: String? = null) :
    Exception(resolveCommentReadErrorMessage(code, serverMessage)) {
    val commentInputBlocked: Boolean get() = code == 12061 || code == 12002
}

internal fun resolveCommentReadErrorMessage(code: Int, serverMessage: String? = null): String {
    val detail = serverMessage?.trim()?.takeIf {
        it.isNotBlank() && !it.equals("success", ignoreCase = true) && it != "0"
    }
    if ((code == 12061 || code == 12002) && detail != null) return detail
    return when (code) {
        -352 -> "请求频率过高，请稍后再试"
        -111 -> "签名验证失败，请稍后重试"
        -101 -> "评论加载失败，请稍后重试或切换排序"
        -400 -> "请求参数错误"
        -412 -> "请求被拦截，请稍后再试"
        12002 -> "评论区已关闭"
        12061 -> "评论区暂不可用"
        12006 -> "评论不存在或暂不可见"
        12009 -> "评论内容不存在"
        else -> detail?.let { "$it ($code)" } ?: "加载评论失败 ($code)"
    }
}

/** Location supplementation must not replace the gRPC ranking, cursor or reply controls. */
internal fun mergeCommentReplyLocations(
    data: ReplyData,
    supplements: List<ReplyItem>,
): ReplyData {
    val locations = supplements.asSequence()
        .filter { it.rpid > 0 && !it.replyControl?.location.isNullOrBlank() }
        .associate { it.rpid to requireNotNull(it.replyControl).location }
    if (locations.isEmpty()) return data
    fun merge(item: ReplyItem): ReplyItem {
        val location = locations[item.rpid]
        val control = if (item.replyControl?.location.isNullOrBlank() && location != null) {
            (item.replyControl ?: com.android.purebilibili.data.model.response.ReplyControl())
                .copy(location = location)
        } else item.replyControl
        return item.copy(replyControl = control, replies = item.replies?.map(::merge))
    }
    return data.copy(
        root = data.root?.let(::merge),
        replies = data.replies?.map(::merge),
        hots = data.hots?.map(::merge),
        topReplies = data.topReplies?.map(::merge),
        top = data.top?.let { it.copy(
            upper = it.upper?.let(::merge), admin = it.admin?.let(::merge), vote = it.vote?.let(::merge),
        ) },
        upper = data.upper?.let { it.copy(top = it.top?.let(::merge)) },
    )
}

internal fun collectReplyLocationCandidates(data: ReplyData): List<ReplyItem> {
    fun flatten(item: ReplyItem): List<ReplyItem> = listOf(item) +
        item.replies.orEmpty().flatMap(::flatten)
    return (listOfNotNull(data.root) + data.replies.orEmpty() + data.hots.orEmpty() +
        data.collectTopReplies()).flatMap(::flatten).distinctBy { it.rpid }
}
