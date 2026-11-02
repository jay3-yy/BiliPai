package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.IdUtils
import com.android.purebilibili.core.util.decodeUrlComponentCompat
import com.android.purebilibili.core.util.encodeUrlComponentCompat
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentTarget
import java.net.URI

internal data class DynamicInteractionPreview(
    val item: DynamicInteractionItem,
    val commentTarget: BilibiliNavigationTarget.Dynamic?,
)

internal fun resolveVisibleDynamicInteractionItems(
    dynamic: DynamicItem,
    commentPreviewVisible: Boolean,
): List<DynamicInteractionPreview> = buildList {
    for (item in dynamic.modules.module_interaction?.items.orEmpty()) {
        if (item.desc?.let(::shouldRenderDynamicRichText) != true) continue
        val target = if (item.type == 1) {
            if (!commentPreviewVisible) continue
            resolveDynamicCommentPreviewTarget(dynamic, item)?.takeIf {
                it.commentRootRpid > 0L || it.commentTargetRpid > 0L
            } ?: continue
        } else null
        add(DynamicInteractionPreview(item, target))
    }
}

internal fun resolveDynamicCommentPreviewTarget(
    dynamic: DynamicItem,
    item: DynamicInteractionItem,
): BilibiliNavigationTarget.Dynamic? {
    val dynamicId = dynamic.id_str
    if (item.type != 1 || dynamicId.isBlank()) return null
    val subject = resolveDynamicCommentTarget(dynamic)

    fun locationFrom(url: String): BilibiliNavigationTarget.Dynamic? {
        val target = BilibiliNavigationTargetParser.parse(url)
        val root: Long
        val reply: Long
        when (target) {
            is BilibiliNavigationTarget.Dynamic -> {
                if (target.dynamicId != dynamicId) return null
                root = target.commentRootRpid
                reply = target.commentTargetRpid
            }
            is BilibiliNavigationTarget.CommentDetail -> {
                if (subject == null || target.oid != subject.oid || target.businessId != subject.type) return null
                root = target.rootReplyId
                reply = target.targetReplyId
            }
            is BilibiliNavigationTarget.Video -> {
                val aid = if (target.videoId.startsWith("av")) target.videoId.drop(2).toLongOrNull()
                    else IdUtils.bv2av(target.videoId)
                if (subject?.type != 1 || aid != subject.oid) return null
                root = target.commentRootRpid
                reply = target.commentTargetRpid
            }
            is BilibiliNavigationTarget.Article -> {
                if (subject?.type != 12 || target.articleId != subject.oid) return null
                root = target.commentRootRpid
                reply = target.commentTargetRpid
            }
            else -> return null
        }
        if (root <= 0L && reply <= 0L) return null
        return BilibiliNavigationTarget.Dynamic(dynamicId, root, reply, openComments = true)
    }

    // 官方点击参数覆盖 URI 同名参数；没有独立 URI 时应用到当前动态评论入口。
    val previewUri = item.uri.trim().ifBlank { "bilibili://following/detail/$dynamicId" }
    mergeCommentPreviewClickParams(previewUri, item.extend_click_param)?.let { url ->
        locationFrom(url)?.let { return it }
    }
    // AT rid 是用户 mid，正文链接也可能引用其他评论，二者都不能作为摘要身份。
    return BilibiliNavigationTarget.Dynamic(dynamicId, openComments = true)
}

private fun mergeCommentPreviewClickParams(url: String, params: Map<String, String>): String? {
    if (params.isEmpty()) return url
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    return buildString {
        append(url.substringBefore('#').substringBefore('?'))
        var hasQuery = false
        uri.rawQuery?.split('&')?.forEach { part ->
            if (part.isBlank()) return@forEach
            val key = runCatching { decodeUrlComponentCompat(part.substringBefore('=')) }.getOrNull()
            if (key in params) return@forEach
            append(if (hasQuery) '&' else '?')
            append(part)
            hasQuery = true
        }
        params.forEach { (key, value) ->
            append(if (hasQuery) '&' else '?')
            append(encodeUrlComponentCompat(key))
            append('=')
            append(encodeUrlComponentCompat(value))
            hasQuery = true
        }
        uri.rawFragment?.let { append('#').append(it) }
    }
}
