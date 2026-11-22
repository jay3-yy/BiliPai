package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicCreatePic
import com.android.purebilibili.data.model.response.DynamicPublishDraft
import com.android.purebilibili.data.model.response.DynamicPublishMention
import com.android.purebilibili.data.model.response.DynamicPublishTopic
import com.android.purebilibili.data.model.response.DynamicPublishToken
import com.android.purebilibili.data.model.response.RichTextNode
import com.android.purebilibili.data.model.response.DynamicRichTextIssue
import com.android.purebilibili.data.model.response.DynamicUnlocatedRichText

/**
 * 更多菜单的管理动作与参数解析（对齐 BiliPai 作者区 morePanel 的
 * 置顶 / 可见范围 / 评论互动设置 / 不感兴趣 / 屏蔽作者）。
 */
sealed interface DynamicManageAction {
    data class NotInterested(val dynamicId: String) : DynamicManageAction
    data class ToggleTop(val dynamicId: String, val isCurrentlyTop: Boolean) : DynamicManageAction
    data class SetVisibility(val dynamicId: String, val dynType: Int, val isPrivate: Boolean) : DynamicManageAction
    data class SetReplySubject(val oid: Long, val replyType: Int, val action: Int) : DynamicManageAction
    data class BlockAuthor(
        val authorMid: Long,
        val authorName: String,
        val authorFace: String,
    ) : DynamicManageAction
    data class Report(val dynamicId: String, val authorMid: Long) : DynamicManageAction
    data class Edit(val dynamicId: String, val initialDraft: DynamicPublishDraft, val replyOid: Long? = null, val replyType: Int = 0) : DynamicManageAction
}

internal fun dispatchDynamicManageAction(
    action: DynamicManageAction,
    onReport: (DynamicManageAction.Report) -> Unit,
    onEdit: (DynamicManageAction.Edit) -> Unit,
    onNotInterested: (DynamicManageAction.NotInterested) -> Unit,
    onOther: (DynamicManageAction) -> Unit,
) {
    when (action) {
        is DynamicManageAction.Report -> onReport(action)
        is DynamicManageAction.Edit -> onEdit(action)
        is DynamicManageAction.NotInterested -> onNotInterested(action)
        else -> onOther(action)
    }
}

internal data class DynamicMenuCapabilities(
    val isOwnDynamic: Boolean,
    val canToggleTop: Boolean,
    val canSetVisibility: Boolean,
    val canManageComments: Boolean,
    val canEdit: Boolean,
    val canBlockAuthor: Boolean,
    val canReport: Boolean,
    val isPrivate: Boolean,
)

internal fun resolveDynamicMenuCapabilities(
    item: DynamicItem,
    currentUserMid: Long?,
): DynamicMenuCapabilities {
    val menuItems = item.modules.module_more?.three_point_items.orEmpty()
    val types = menuItems.mapTo(mutableSetOf()) { it.type }
    val authorMid = item.modules.module_author?.mid ?: 0L
    val hasAuthorOnlyServerAction = types.any {
        it == "THREE_POINT_EDIT" ||
            it == "THREE_POINT_PRIVATE" ||
            it == "THREE_POINT_DELETE" ||
            it == "THREE_POINT_TOP"
    }
    val isOwnDynamic = currentUserMid != null && currentUserMid > 0L && authorMid == currentUserMid ||
        hasAuthorOnlyServerAction
    val privateStatus = menuItems
        .firstOrNull { it.type == "THREE_POINT_PRIVATE" }
        ?.params
        ?.status
        ?: 0

    return DynamicMenuCapabilities(
        isOwnDynamic = isOwnDynamic,
        canToggleTop = isOwnDynamic,
        canSetVisibility = isOwnDynamic && types.contains("THREE_POINT_PRIVATE"),
        canManageComments = isOwnDynamic,
        canEdit = isOwnDynamic && types.contains("THREE_POINT_EDIT"),
        canBlockAuthor = !isOwnDynamic && authorMid > 0L,
        canReport = !isOwnDynamic && (types.isEmpty() || types.contains("THREE_POINT_REPORT")),
        isPrivate = privateStatus == 1,
    )
}

internal data class DynamicReportReason(
    val type: Int,
    val label: String
)

internal fun resolveDynamicReportReasons(): List<DynamicReportReason> = listOf(
    DynamicReportReason(4, "垃圾广告"),
    DynamicReportReason(8, "引战"),
    DynamicReportReason(1, "色情"),
    DynamicReportReason(5, "人身攻击"),
    DynamicReportReason(3, "违法信息"),
    DynamicReportReason(9, "涉政谣言"),
    DynamicReportReason(10, "涉社会事件谣言"),
    DynamicReportReason(12, "虚假不实信息"),
    DynamicReportReason(13, "违法信息外链"),
    DynamicReportReason(0, "其他"),
)

internal fun resolveDynamicEditDraft(item: DynamicItem): DynamicPublishDraft {
    val dynamic = item.modules.module_dynamic
    val opus = dynamic?.major?.opus
    val richNodes = dynamic?.desc?.rich_text_nodes.orEmpty()
        .ifEmpty { opus?.summary?.rich_text_nodes.orEmpty() }
    val images = buildList {
        dynamic?.major?.draw?.items.orEmpty().forEach { image ->
            if (image.src.isNotBlank()) {
                add(
                    DynamicCreatePic(
                        img_src = image.src,
                        img_width = image.width,
                        img_height = image.height,
                        img_size = 0f,
                    )
                )
            }
        }
        opus?.pics.orEmpty().forEach { image ->
            if (image.url.isNotBlank()) {
                add(
                    DynamicCreatePic(
                        img_src = image.url,
                        img_width = image.width,
                        img_height = image.height,
                        img_size = image.size.toFloat(),
                    )
                )
            }
        }
    }.distinctBy { it.img_src }
    val additional = dynamic?.additional
    val isPrivate = item.modules.module_more?.three_point_items.orEmpty()
        .firstOrNull { it.type == "THREE_POINT_PRIVATE" }
        ?.params
        ?.status == 1
    val editText = dynamic?.desc?.text.orEmpty().ifBlank { opus?.summary?.text.orEmpty() }
    val restored = restoreDynamicEditTokens(editText, richNodes)
    val tokens = restored.tokens
    return DynamicPublishDraft(
        text = editText,
        title = opus?.title.orEmpty(),
        imageUris = images.map(DynamicCreatePic::img_src),
        voteId = additional?.vote?.vote_id ?: 0L,
        voteTitle = additional?.vote?.desc.orEmpty(),
        reserveId = additional?.reserve?.rid ?: 0L,
        private = isPrivate,
        mentions = richNodes.mapNotNull { node ->
            node.takeIf { it.type.contains("AT", ignoreCase = true) }
                ?.rid
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { DynamicPublishMention(uid = it, name = node.text.trim().removePrefix("@")) }
        }.distinctBy(DynamicPublishMention::uid),
        emotes = richNodes.mapNotNull { node ->
            node.takeIf { it.type.contains("EMOJI", ignoreCase = true) }
                ?.let { it.text.ifBlank { it.orig_text }.ifBlank { it.emoji?.text.orEmpty() } }
                ?.takeIf(String::isNotBlank)
        }.distinct(),
        topic = dynamic?.topic?.takeIf { it.id > 0L }?.let {
            DynamicPublishTopic(id = it.id, name = it.name)
        },
        existingImages = images,
        replyOption = null,
        richTokens = tokens,
        hasUnresolvedRichText = restored.unresolved,
        unresolvedRichTextRanges = restored.issues,
        hasUnlocatedRichText = restored.unlocated,
        unlocatedRichTextItems = restored.unlocatedItems,
    )
}

internal data class DynamicRestoredRichText(
    val tokens: List<DynamicPublishToken>,
    val unresolved: Boolean,
    val issues: List<DynamicRichTextIssue> = emptyList(),
    val unlocated: Boolean = false,
    val unlocatedItems: List<DynamicUnlocatedRichText> = emptyList(),
)

private val DYNAMIC_POSSIBLE_MENTION_REGEX = Regex("(?<![\\w@])@[^\\s@，。！？、：；,.!?;:]{1,30}")
private val DYNAMIC_POSSIBLE_EMOTE_REGEX = Regex("\\[[^\\[\\]\\r\\n]{1,30}]")

/** Map server nodes to the displayed text without guessing between repeated matches. */
internal fun restoreDynamicEditTokens(text: String, nodes: List<RichTextNode>): DynamicRestoredRichText {
    if (nodes.isEmpty()) {
        // These patterns only signal that metadata may be missing; they do not create rich nodes.
        val issues = (DYNAMIC_POSSIBLE_MENTION_REGEX.findAll(text) + DYNAMIC_POSSIBLE_EMOTE_REGEX.findAll(text))
            .map { DynamicRichTextIssue(it.range.first, it.range.last + 1) }.toList().distinct()
        return DynamicRestoredRichText(emptyList(), issues.isNotEmpty(), issues)
    }
    fun variants(node: RichTextNode): List<String> = listOf(node.text, node.orig_text, node.emoji?.text.orEmpty())
        .filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
    fun tokenFor(node: RichTextNode, start: Int, matched: String): DynamicPublishToken? {
        val type = when {
            node.type.contains("AT", ignoreCase = true) && node.rid?.toLongOrNull()?.let { it > 0L } == true -> 2
            node.type.contains("EMOJI", ignoreCase = true) -> 9
            else -> return null
        }
        val tokenText = if (type == 2) matched.trimEnd() else matched
        if (tokenText.isEmpty()) return null
        return DynamicPublishToken(start, start + tokenText.length, type, if (type == 2) node.rid.orEmpty() else "", tokenText)
    }
    fun isSpecial(node: RichTextNode) = node.type.contains("AT", ignoreCase = true) || node.type.contains("EMOJI", ignoreCase = true)

    // A complete sequential match disambiguates repeated text without relying on search order.
    var offset = 0
    val exact = mutableListOf<DynamicPublishToken>()
    var complete = nodes.isNotEmpty()
    for (node in nodes) {
        val match = variants(node).firstOrNull { text.startsWith(it, offset) }
        if (match == null) { complete = false; break }
        if (isSpecial(node)) {
            val token = tokenFor(node, offset, match)
            if (token == null) { complete = false; break }
            exact += token
        }
        offset += match.length
    }
    if (complete && offset == text.length) return DynamicRestoredRichText(exact, false)

    var cursor = 0
    var unresolved = false
    val unlocatedItems = mutableListOf<DynamicUnlocatedRichText>()
    val issues = mutableListOf<DynamicRichTextIssue>()
    val result = mutableListOf<DynamicPublishToken>()
    for (node in nodes) {
        val matches = variants(node).flatMap { variant ->
            buildList {
                var index = text.indexOf(variant, cursor)
                while (index >= 0) {
                    add(index to variant)
                    index = text.indexOf(variant, index + 1)
                }
            }
        }.distinctBy { it.first to it.second }
        val positions = matches.map { it.first }.distinct()
        if (positions.size != 1) {
            if (isSpecial(node)) {
                unresolved = true
                if (matches.isEmpty()) {
                    val hint = variants(node).firstOrNull()?.take(60)
                    val kind = if (node.type.contains("EMOJI", ignoreCase = true)) "表情" else "@ 用户"
                    unlocatedItems += DynamicUnlocatedRichText(if (hint == null) "未定位的$kind" else "$kind：$hint")
                } else matches.groupBy { it.first }.forEach { (start, variantsAtStart) ->
                    issues += DynamicRichTextIssue(start, start + variantsAtStart.maxOf { it.second.length })
                }
            }
            continue
        }
        val start = positions.single()
        val match = matches.filter { it.first == start }.maxBy { it.second.length }.second
        cursor = start + match.length
        if (isSpecial(node)) {
            val token = tokenFor(node, start, match)
            if (token == null) {
                unresolved = true
                issues += DynamicRichTextIssue(start, start + match.length)
            } else result += token
        }
    }
    val unresolvedRanges = issues.distinct().filterNot { issue ->
        result.any { token -> token.start <= issue.start && token.end >= issue.end }
    }
    if (unresolved && unresolvedRanges.isEmpty() && unlocatedItems.isEmpty()) {
        unlocatedItems += DynamicUnlocatedRichText("未定位的 @ 或表情")
    }
    return DynamicRestoredRichText(result, unresolved, unresolvedRanges, unlocatedItems.isNotEmpty(), unlocatedItems)
}

// x/v2/reply/subject/modify 的 action 取值
internal const val DYNAMIC_REPLY_ACTION_ENABLE_SELECTION = 1 // 开启评论精选
internal const val DYNAMIC_REPLY_ACTION_DISABLE_SELECTION = 2 // 停止评论精选
internal const val DYNAMIC_REPLY_ACTION_CLOSE_REPLY = 3 // 关闭评论
internal const val DYNAMIC_REPLY_ACTION_OPEN_REPLY = 4 // 恢复评论

internal fun resolveDynamicPinnedMenuLabel(isCurrentlyTop: Boolean): String =
    if (isCurrentlyTop) "取消置顶" else "置顶"

internal fun resolveDynamicVisibilityMenuLabel(isPrivate: Boolean): String =
    if (isPrivate) "设为公开" else "设为仅自己可见"

// x/dynamic/feed/dyn/private_pub_setting 的 action 取值
internal fun resolveDynamicVisibilityAction(isPrivate: Boolean): String =
    if (isPrivate) "private_pub" else "public_pub"

internal fun resolveDynamicReplySelectionAction(isCurrentlyEnabled: Boolean): Int =
    if (isCurrentlyEnabled) DYNAMIC_REPLY_ACTION_DISABLE_SELECTION else DYNAMIC_REPLY_ACTION_ENABLE_SELECTION

internal fun resolveDynamicReplyOpenAction(isCurrentlyEnabled: Boolean): Int =
    if (isCurrentlyEnabled) DYNAMIC_REPLY_ACTION_CLOSE_REPLY else DYNAMIC_REPLY_ACTION_OPEN_REPLY

// 评论互动目标：评论区 oid/type，来自 basic.comment_id_str / comment_type。
internal fun resolveDynamicReplySubjectOid(item: DynamicItem): Long? =
    item.basic?.comment_id_str?.toLongOrNull()?.takeIf { it > 0L }

internal fun resolveDynamicReplySubjectType(item: DynamicItem): Int =
    item.basic?.comment_type ?: 0

// 可见范围接口的 object_id 参数：{"dyn_id":"...","dyn_type":N}
internal fun buildDynamicVisibilityObjectId(dynamicId: String, dynType: Int): String =
    """{"dyn_id":"$dynamicId","dyn_type":$dynType}"""

// 动态类型数值（dyn_type）：优先取 three_point_items 下发的参数，无则回退 0。
internal fun resolveDynamicDynType(item: DynamicItem): Int =
    item.modules.module_more?.three_point_items
        ?.firstNotNullOfOrNull { itemParams -> itemParams.params?.dyn_type?.takeIf { it > 0 } }
        ?: 0
