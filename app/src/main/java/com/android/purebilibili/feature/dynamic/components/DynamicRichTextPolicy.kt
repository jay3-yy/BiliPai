package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.OpusContentBlock
import com.android.purebilibili.data.model.response.plainText
import com.android.purebilibili.data.model.response.EmojiInfo
import com.android.purebilibili.data.model.response.RichTextNode

internal const val DYNAMIC_RICH_TEXT_URL_TAG = "URL"
internal const val DYNAMIC_RICH_TEXT_USER_TAG = "USER"
internal const val DYNAMIC_RICH_TEXT_VOTE_TAG = "VOTE"
internal const val DYNAMIC_RICH_TEXT_TOPIC_TAG = "TOPIC"
internal const val DYNAMIC_RICH_TEXT_TOPIC_KEYWORD_TAG = "TOPIC_KEYWORD"

/** 动态富文本原生链接 payload 前缀：LinkAnnotation.Clickable 用单一 tag 承载「类型:载荷」。 */
internal const val DYNAMIC_RICH_TEXT_LINK_URL_PREFIX = "URL:"
internal const val DYNAMIC_RICH_TEXT_LINK_USER_PREFIX = "USER:"
internal const val DYNAMIC_RICH_TEXT_LINK_USER_NAME_PREFIX = "USERNAME:"
internal const val DYNAMIC_RICH_TEXT_LINK_VOTE_PREFIX = "VOTE:"
internal const val DYNAMIC_RICH_TEXT_LINK_TOPIC_ID_PREFIX = "TOPIC:"
internal const val DYNAMIC_RICH_TEXT_LINK_TOPIC_KEYWORD_PREFIX = "TOPICKW:"

/** 构建原生 [LinkAnnotation.Clickable]；BasicText 在 Text 内部处理点击，优先于划选/卡片长按。 */
private fun dynamicRichTextLinkAnnotation(
    payload: String,
    listener: LinkInteractionListener?,
): LinkAnnotation = LinkAnnotation.Clickable(
    tag = payload,
    styles = null,
    linkInteractionListener = listener,
)

/** 动态富文本链接动作（纯数据，供分发与测试）。 */
internal sealed interface DynamicRichTextLinkAction {
    data class Url(val url: String) : DynamicRichTextLinkAction
    data class User(val mid: Long) : DynamicRichTextLinkAction
    data class UserName(val name: String) : DynamicRichTextLinkAction
    data class Vote(val voteId: Long) : DynamicRichTextLinkAction
    data class TopicId(val topicId: Long) : DynamicRichTextLinkAction
    data class TopicKeyword(val keyword: String) : DynamicRichTextLinkAction
}

internal fun resolveDynamicRichTextLinkAction(tag: String): DynamicRichTextLinkAction? {
    return when {
        tag.startsWith(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX) ->
            DynamicRichTextLinkAction.Url(tag.removePrefix(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX))
        tag.startsWith(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX) ->
            tag.removePrefix(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX).toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let(DynamicRichTextLinkAction::User)
        tag.startsWith(DYNAMIC_RICH_TEXT_LINK_USER_NAME_PREFIX) ->
            tag.removePrefix(DYNAMIC_RICH_TEXT_LINK_USER_NAME_PREFIX)
                .takeIf { it.isNotBlank() }
                ?.let(DynamicRichTextLinkAction::UserName)
        tag.startsWith(DYNAMIC_RICH_TEXT_LINK_VOTE_PREFIX) ->
            tag.removePrefix(DYNAMIC_RICH_TEXT_LINK_VOTE_PREFIX).toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let(DynamicRichTextLinkAction::Vote)
        tag.startsWith(DYNAMIC_RICH_TEXT_LINK_TOPIC_ID_PREFIX) ->
            tag.removePrefix(DYNAMIC_RICH_TEXT_LINK_TOPIC_ID_PREFIX).toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let(DynamicRichTextLinkAction::TopicId)
        tag.startsWith(DYNAMIC_RICH_TEXT_LINK_TOPIC_KEYWORD_PREFIX) ->
            tag.removePrefix(DYNAMIC_RICH_TEXT_LINK_TOPIC_KEYWORD_PREFIX)
                .takeIf { it.isNotBlank() }
                ?.let(DynamicRichTextLinkAction::TopicKeyword)
        else -> null
    }
}

internal enum class DynamicRichTextOpenMode {
    IN_APP,
    EXTERNAL
}

internal data class DynamicRichTextBuildResult(
    val annotatedString: AnnotatedString,
    val emojiUrlById: Map<String, String>
)

private val DYNAMIC_RICH_TEXT_URL_PATTERN =
    """((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])""".toRegex()
private val DYNAMIC_EMOTE_TOKEN_PATTERN = """\[[^\[\]]+\]""".toRegex()
private val DYNAMIC_IMAGE_PLACEHOLDERS = setOf("[图片]", "【图片】")

internal fun buildDynamicRichTextAnnotatedString(
    desc: DynamicDesc,
    primaryColor: Color,
    textColor: Color,
    extraEmoteUrlMap: Map<String, String> = emptyMap(),
    linkListener: LinkInteractionListener? = null,
    collapseWebLinks: Boolean = false,
): AnnotatedString {
    return buildDynamicRichText(
        desc = desc,
        primaryColor = primaryColor,
        textColor = textColor,
        extraEmoteUrlMap = extraEmoteUrlMap,
        linkListener = linkListener,
        collapseWebLinks = collapseWebLinks,
    ).annotatedString
}

internal fun buildDynamicRichText(
    desc: DynamicDesc,
    primaryColor: Color,
    textColor: Color,
    extraEmoteUrlMap: Map<String, String> = emptyMap(),
    linkListener: LinkInteractionListener? = null,
    collapseWebLinks: Boolean = false,
): DynamicRichTextBuildResult {
    val nodeEmoteMap = collectDynamicEmojiUrlMap(desc.rich_text_nodes)
    val emoteUrlMap = buildMap {
        putAll(extraEmoteUrlMap)
        putAll(nodeEmoteMap)
    }
    val usedEmojiIds = linkedMapOf<String, String>()
    // A preview/detail response can contain the complete `text` alongside a
    // shortened node stream. Keep the complete text, but splice any actionable
    // node metadata (AT/link/vote/emoji) back into its exact range instead of
    // downgrading the whole paragraph to plain text.
    val renderNodes = resolveDynamicRichTextNodesForText(
        text = desc.text,
        nodes = desc.rich_text_nodes,
    )
    val annotated = buildAnnotatedString {
        val shouldRenderNodes = renderNodes.isNotEmpty() && (
            desc.text.isBlank() ||
                shouldUseDynamicRichTextNodes(desc) ||
                renderNodes != desc.rich_text_nodes
            )
        if (shouldRenderNodes) {
            renderNodes.forEach { node ->
                appendDynamicRichTextNode(
                    node = node,
                    primaryColor = primaryColor,
                    textColor = textColor,
                    emoteUrlMap = emoteUrlMap,
                    usedEmojiIds = usedEmojiIds,
                    linkListener = linkListener,
                    collapseWebLinks = collapseWebLinks,
                )
            }
        } else {
            appendDynamicRichTextExpandableText(
                text = desc.text,
                primaryColor = primaryColor,
                textColor = textColor,
                emoteUrlMap = emoteUrlMap,
                usedEmojiIds = usedEmojiIds,
                linkListener = linkListener,
                collapseWebLinks = collapseWebLinks,
            )
        }
    }
    return DynamicRichTextBuildResult(
        annotatedString = annotated,
        emojiUrlById = usedEmojiIds
    )
}

/**
 * Prefer structured nodes when they can render richer content (especially emoji images).
 * Fall back to plain text only when nodes are clearly truncated and have no emoji.
 */
internal fun shouldUseDynamicRichTextNodes(desc: DynamicDesc): Boolean {
    if (desc.rich_text_nodes.isEmpty()) return false
    if (desc.text.isBlank()) return true
    val nodeText = resolveDynamicRichTextNodeDisplayText(desc.rich_text_nodes)
    if (desc.rich_text_nodes.any(::isRenderableDynamicEmojiNode)) {
        // Some detail/opus payloads return emoji nodes with only a partial text-node stream.
        // Keep the complete desc.text as the source of truth and use the nodes only as the
        // shortcode -> image catalog in that case, otherwise adjacent body text disappears.
        return nodeText == desc.text
    }
    return nodeText.length >= desc.text.length
}

internal fun resolveDynamicOpusTextBlockRichDesc(
    blockText: String,
    preferredDesc: DynamicDesc?,
    blockRichTextNodes: List<RichTextNode> = emptyList(),
): DynamicDesc? {
    if (blockText.isBlank()) return null
    if (blockRichTextNodes.any { it.type.isNotBlank() }) {
        // Detail paragraphs can expose only TEXT nodes while the preview desc carries
        // actionable mention/topic/link metadata. Keep it so the detail response cannot
        // downgrade briefly highlighted content into plain text.
        val preferredActionableNodes = preferredDesc?.rich_text_nodes.orEmpty().filter {
            val type = it.type.trim().removePrefix("RICH_TEXT_NODE_TYPE_")
            type.isNotBlank() && !type.equals("TEXT", ignoreCase = true)
        }
        val blockHasActionableNode = blockRichTextNodes.any {
            it.type.trim().removePrefix("RICH_TEXT_NODE_TYPE_")
                .let { type -> type.isNotBlank() && !type.equals("TEXT", ignoreCase = true) }
        }
        val enrichedBlockNodes = blockRichTextNodes.map { node ->
            val nodeType = node.type.trim().removePrefix("RICH_TEXT_NODE_TYPE_")
            if (nodeType.equals("EMOJI", ignoreCase = true) && resolveDynamicEmojiIconUrl(node.emoji) == null) {
                val token = resolveDynamicRichTextNodeToken(node)
                val fallbackEmoji = preferredActionableNodes.firstOrNull {
                    resolveDynamicRichTextNodeToken(it) == token && resolveDynamicEmojiIconUrl(it.emoji) != null
                }?.emoji
                if (fallbackEmoji != null) node.copy(emoji = fallbackEmoji) else node
            } else {
                node
            }
        }
        val metadataNodes = buildList {
            addAll(enrichedBlockNodes)
            preferredActionableNodes.forEach { prefNode ->
                val key = resolveDynamicRichTextNodeToken(prefNode)
                val alreadyCovered = enrichedBlockNodes.any { blockNode ->
                    resolveDynamicRichTextNodeToken(blockNode) == key && (
                        resolveDynamicEmojiIconUrl(blockNode.emoji) != null ||
                            !blockNode.rid.isNullOrBlank() ||
                            !blockNode.jump_url.isNullOrBlank()
                    )
                }
                if (!alreadyCovered) {
                    add(prefNode)
                }
            }
        }
        val mergedPreferredNodes = if (metadataNodes.isNotEmpty()) {
            mergeDynamicRichTextMetadataIntoText(
                text = blockText,
                metadataNodes = metadataNodes,
            )
        } else {
            emptyList()
        }
        val resolvedBlockNodes = when {
            mergedPreferredNodes.isNotEmpty() -> mergedPreferredNodes
            blockHasActionableNode -> enrichedBlockNodes
            preferredActionableNodes.isNotEmpty() -> preferredDesc?.rich_text_nodes.orEmpty()
            else -> enrichedBlockNodes
        }
        return DynamicDesc(
            text = blockText,
            rich_text_nodes = resolvedBlockNodes,
        )
    }
    // Even when both metadata sources are absent, route the paragraph through
    // RichTextContent so its plain-text @/topic fallback can still run.
    return preferredDesc?.copy(text = blockText) ?: DynamicDesc(text = blockText)
}

/**
 * Rebuild a node stream for a full opus paragraph while retaining actionable metadata from
 * the shorter preview description. The desktop opus API may return the complete paragraph as
 * WORD/TEXT nodes and expose the same AT node only in the preview response. Matching by the
 * displayed token keeps the full body text intact and gives RichTextContent an exact range on
 * which to place the user annotation.
 */
internal fun mergeDynamicRichTextMetadataIntoText(
    text: String,
    metadataNodes: List<RichTextNode>,
): List<RichTextNode> {
    if (text.isBlank() || metadataNodes.isEmpty()) return emptyList()
    val actionableNodes = metadataNodes.filter { node ->
        val type = node.type.trim().removePrefix("RICH_TEXT_NODE_TYPE_")
        type.isNotBlank() && !type.equals("TEXT", ignoreCase = true) &&
            resolveDynamicRichTextNodeToken(node).isNotBlank()
    }
    if (actionableNodes.isEmpty()) return emptyList()

    val result = mutableListOf<RichTextNode>()
    var cursor = 0
    // Detail nodes and preview metadata can be appended in different orders. Follow the
    // actual paragraph order so a later mention cannot advance past an earlier one.
    actionableNodes.sortedWith(
        compareBy<RichTextNode> { node ->
            findDynamicRichTextNodeMatch(text, node, 0)?.start ?: Int.MAX_VALUE
        }.thenByDescending { node ->
            if (node.type.trim().removePrefix("RICH_TEXT_NODE_TYPE_").equals("AT", ignoreCase = true) &&
                resolveDynamicRichTextUserMid(node) != null
            ) 1 else 0
        }
    ).forEach { node ->
        val match = findDynamicRichTextNodeMatch(text, node, cursor) ?: return@forEach
        val token = match.token
        val start = match.start
        if (start > cursor) {
            result += RichTextNode(
                type = "RICH_TEXT_NODE_TYPE_TEXT",
                text = text.substring(cursor, start),
            )
        }
        result += node.copy(
            text = token,
            orig_text = node.orig_text.ifBlank { token },
        )
        cursor = start + token.length
    }
    if (result.isEmpty()) return emptyList()
    if (cursor < text.length) {
        result += RichTextNode(
            type = "RICH_TEXT_NODE_TYPE_TEXT",
            text = text.substring(cursor),
        )
    }
    return result
}

private data class DynamicRichTextNodeMatch(
    val start: Int,
    val token: String,
)

/**
 * Match using both API display fields. AT nodes in particular are inconsistent:
 * some responses put `@name` in `text`, others put it only in `orig_text` (often
 * with a trailing space). Matching the original form first preserves the exact
 * mention range and avoids annotating an unrelated occurrence of the bare name.
 */
private fun findDynamicRichTextNodeMatch(
    text: String,
    node: RichTextNode,
    startIndex: Int,
): DynamicRichTextNodeMatch? {
    val candidates = buildList {
        add(node.orig_text)
        add(node.text)
        add(node.emoji?.text.orEmpty())
    }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .sortedWith(
            compareByDescending<String> { it.startsWith("@") }
                .thenByDescending(String::length)
        )

    candidates.forEach { candidate ->
        val start = text.indexOf(candidate, startIndex = startIndex)
        if (start >= 0) return DynamicRichTextNodeMatch(start = start, token = candidate)
    }
    return null
}

/**
 * Returns a node stream aligned to [text] whenever at least one actionable node
 * can be located. Plain TEXT-only streams are intentionally left to the existing
 * completeness check, while a shortened stream containing an AT node still gets
 * a full-text TEXT prefix/suffix around that annotation.
 */
internal fun resolveDynamicRichTextNodesForText(
    text: String,
    nodes: List<RichTextNode>,
): List<RichTextNode> {
    if (nodes.isEmpty()) return emptyList()
    if (text.isBlank()) return nodes
    if (resolveDynamicRichTextNodeDisplayText(nodes) == text) return nodes
    return mergeDynamicRichTextMetadataIntoText(text, nodes)
}

internal fun collectDynamicEmojiUrlMap(nodes: List<RichTextNode>): Map<String, String> {
    if (nodes.isEmpty()) return emptyMap()
    val result = linkedMapOf<String, String>()
    nodes.forEach { node ->
        val iconUrl = resolveDynamicEmojiIconUrl(node.emoji) ?: return@forEach
        val tokens = listOf(
            node.text,
            node.orig_text,
            node.emoji?.text.orEmpty()
        ).map { it.trim() }.filter { it.isNotEmpty() }
        tokens.forEach { token ->
            result.putIfAbsent(token, iconUrl)
        }
    }
    return result
}

internal fun resolveDynamicEmojiIconUrl(emoji: EmojiInfo?): String? {
    if (emoji == null) return null
    val raw = sequenceOf(
        emoji.icon_url,
        emoji.webp_url,
        emoji.gif_url
    ).map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    return normalizeDynamicImageUrl(raw)
}

internal fun isRenderableDynamicEmojiNode(node: RichTextNode): Boolean {
    val nodeType = node.type.trim().removePrefix("RICH_TEXT_NODE_TYPE_")
    if (!nodeType.equals("EMOJI", ignoreCase = true)) return false
    return resolveDynamicEmojiIconUrl(node.emoji) != null
}

internal fun resolveDynamicRichTextNodeDisplayText(nodes: List<RichTextNode>): String {
    return nodes.joinToString(separator = "") { node ->
        resolveDynamicRichTextNodeToken(node)
    }
}

internal fun resolveDynamicRichTextNodeToken(node: RichTextNode): String {
    val emojiText = node.emoji?.text
    return when {
        node.text.isNotBlank() -> node.text
        node.orig_text.isNotBlank() -> node.orig_text
        !emojiText.isNullOrBlank() -> emojiText
        else -> ""
    }
}

internal fun resolveDynamicDescForImages(
    desc: DynamicDesc,
    hasImages: Boolean
): DynamicDesc {
    val edgeNormalized = normalizeDynamicDescEdges(desc)
    if (!hasImages) return edgeNormalized
    return normalizeDynamicDescEdges(
        edgeNormalized.copy(
            text = stripDynamicImagePlaceholders(edgeNormalized.text),
            rich_text_nodes = edgeNormalized.rich_text_nodes.filterNot { node ->
                isDynamicStandaloneImagePlaceholder(resolveDynamicRichTextNodeToken(node))
            }.map { node ->
                node.copy(
                    text = stripDynamicImagePlaceholders(node.text),
                    orig_text = stripDynamicImagePlaceholders(node.orig_text)
                )
            }.filterNot { node ->
                resolveDynamicRichTextNodeToken(node).isBlank() &&
                    node.emoji == null &&
                    node.jump_url.isNullOrBlank() &&
                    node.rid.isNullOrBlank()
            }
        )
    )
}

internal fun resolveDynamicOpusSummaryDescForImages(
    text: String,
    richTextNodes: List<RichTextNode>,
    hasImages: Boolean
): DynamicDesc? {
    val desc = resolveDynamicDescForImages(
        desc = DynamicDesc(
            text = text,
            rich_text_nodes = richTextNodes
        ),
        hasImages = hasImages
    )
    return desc.takeIf(::shouldRenderDynamicRichText)
}

/**
 * Prefer the description that can render emoji images when both desc and opus summary exist.
 */
internal fun resolvePreferredDynamicDesc(
    primary: DynamicDesc?,
    fallback: DynamicDesc?
): DynamicDesc? {
    if (primary == null) return fallback
    if (fallback == null) return primary
    val primaryHasEmoji = primary.rich_text_nodes.any(::isRenderableDynamicEmojiNode)
    val fallbackHasEmoji = fallback.rich_text_nodes.any(::isRenderableDynamicEmojiNode)
    return when {
        primaryHasEmoji -> primary
        fallbackHasEmoji -> fallback
        shouldUseDynamicRichTextNodes(primary) -> primary
        shouldUseDynamicRichTextNodes(fallback) -> fallback
        primary.text.isNotBlank() -> primary
        else -> fallback
    }
}

private fun resolveDynamicCopyNodeText(nodes: List<RichTextNode>): String =
    nodes.joinToString(separator = "") { node ->
        node.text.ifEmpty { resolveDynamicRichTextNodeToken(node) }
    }

internal fun resolveDynamicFullText(item: DynamicItem): String {
    val content = item.modules.module_dynamic
    val major = content?.major
    val opus = major?.opus
    val paragraphs = opus?.contentBlocks.orEmpty().mapNotNull { block ->
        val text = if (block is OpusContentBlock.Text) {
            block.text.ifBlank { resolveDynamicCopyNodeText(block.richTextNodes) }
        } else {
            block.plainText
        }
        normalizeDynamicBodyText(text).takeIf { it.isNotBlank() }
    }
    val body = if (paragraphs.isNotEmpty()) {
        paragraphs.joinToString("\n\n")
    } else {
        val hasImages = !opus?.pics.isNullOrEmpty() || !major?.draw?.items.isNullOrEmpty()
        val desc = resolvePreferredDynamicDesc(
            primary = content?.desc?.let { resolveDynamicDescForImages(it, hasImages) },
            fallback = opus?.summary?.let {
                resolveDynamicOpusSummaryDescForImages(it.text, it.rich_text_nodes, hasImages)
            },
        )
        desc?.let {
            it.text.ifBlank { resolveDynamicCopyNodeText(it.rich_text_nodes) }
        }.orEmpty()
    }
    return listOfNotNull(
        resolveDynamicHeadlineTitle(opus, major?.article),
        body.takeIf { it.isNotBlank() },
        item.orig?.let(::resolveDynamicFullText)?.takeIf { it.isNotBlank() },
    ).joinToString("\n\n")
}

internal fun shouldRenderDynamicRichText(desc: DynamicDesc?): Boolean {
    if (desc == null) return false
    if (desc.text.isNotBlank()) return true
    return desc.rich_text_nodes.any { node ->
        val token = resolveDynamicRichTextNodeToken(node)
        token.isNotBlank() && !isDynamicStandaloneImagePlaceholder(token)
    }
}

private fun isDynamicStandaloneImagePlaceholder(text: String): Boolean {
    return text.trim() in DYNAMIC_IMAGE_PLACEHOLDERS
}

/**
 * 去掉正文首尾的空白行/空格。列表 summary 常带尾部换行，会在文末多撑出一行，
 * 使 seed 帧的「文字→图片」间距大于完整详情，网络回来后突然收紧。
 */
internal fun normalizeDynamicBodyText(text: String): String {
    if (text.isEmpty()) return text
    return text.trimEnd('\n', '\r', ' ', '\t').trimStart('\n', '\r')
}

internal fun normalizeDynamicDescEdges(desc: DynamicDesc): DynamicDesc {
    val normalizedText = normalizeDynamicBodyText(desc.text)
    val nodes = desc.rich_text_nodes
    if (nodes.isEmpty()) {
        return if (normalizedText == desc.text) desc else desc.copy(text = normalizedText)
    }
    val normalizedNodes = nodes.mapIndexed { index, node ->
        var text = node.text
        var origText = node.orig_text
        if (index == 0) {
            text = text.trimStart('\n', '\r')
            origText = origText.trimStart('\n', '\r')
        }
        if (index == nodes.lastIndex) {
            text = normalizeDynamicBodyText(text)
            origText = normalizeDynamicBodyText(origText)
        }
        if (text == node.text && origText == node.orig_text) node
        else node.copy(text = text, orig_text = origText)
    }
    return desc.copy(text = normalizedText, rich_text_nodes = normalizedNodes)
}

private fun stripDynamicImagePlaceholders(text: String): String {
    if (text.isBlank()) return text
    if (!DYNAMIC_IMAGE_PLACEHOLDERS.any { placeholder -> text.contains(placeholder) }) {
        return text
    }
    var sanitized = text
    // B 站图片动态会把真实图片另外放在媒体区，正文里的占位符不应再重复显示。
    DYNAMIC_IMAGE_PLACEHOLDERS.forEach { placeholder ->
        sanitized = sanitized.replace(placeholder, "")
    }
    return sanitized
        .lines()
        .map { line -> line.trimEnd() }
        .filterNot { line -> line.isBlank() }
        .joinToString(separator = "\n")
}

internal fun resolveDynamicRichTextOpenMode(
    rawUrl: String
): DynamicRichTextOpenMode? {
    val url = rawUrl.trim()
    if (url.isBlank()) return null

    if (BilibiliNavigationTargetParser.parse(url) != null || isDynamicRichTextInAppHost(url)) {
        return DynamicRichTextOpenMode.IN_APP
    }
    return DynamicRichTextOpenMode.EXTERNAL
}

private fun AnnotatedString.Builder.appendDynamicRichTextNode(
    node: RichTextNode,
    primaryColor: Color,
    textColor: Color,
    emoteUrlMap: Map<String, String>,
    usedEmojiIds: MutableMap<String, String>,
    linkListener: LinkInteractionListener?,
    collapseWebLinks: Boolean,
) {
    val nodeType = node.type.trim().removePrefix("RICH_TEXT_NODE_TYPE_")
    val displayToken = resolveDynamicRichTextNodeToken(node)
    when {
        nodeType.equals("EMOJI", ignoreCase = true) -> {
            val iconUrl = resolveDynamicEmojiIconUrl(node.emoji)
                ?: emoteUrlMap[displayToken]
            if (!iconUrl.isNullOrBlank() && displayToken.isNotBlank()) {
                usedEmojiIds[displayToken] = iconUrl
                appendInlineContent(id = displayToken, alternateText = displayToken)
            } else if (displayToken.isNotBlank()) {
                withStyle(SpanStyle(color = textColor)) {
                    append(displayToken)
                }
            }
        }

        nodeType.equals("VOTE", ignoreCase = true) -> {
            appendDynamicRichTextVote(
                displayText = displayToken,
                voteId = node.rid,
                primaryColor = primaryColor,
                linkListener = linkListener,
            )
        }

        nodeType.equals("TOPIC", ignoreCase = true) -> {
            appendDynamicRichTextTopic(
                node = node,
                primaryColor = primaryColor,
                linkListener = linkListener,
            )
        }

        shouldRenderDynamicRichTextLink(nodeType, node) -> {
            appendDynamicRichTextLinkNode(
                displayText = displayToken,
                targetUrl = resolveDynamicRichTextLinkTarget(node),
                primaryColor = primaryColor,
                textColor = textColor,
                linkListener = linkListener,
                collapseWebLinks = collapseWebLinks,
            )
        }

        nodeType.equals("AT", ignoreCase = true) -> {
            appendDynamicRichTextAtMention(
                node = node,
                primaryColor = primaryColor,
                linkListener = linkListener
            )
        }

        else -> {
            appendDynamicRichTextExpandableText(
                text = displayToken,
                primaryColor = primaryColor,
                textColor = textColor,
                emoteUrlMap = emoteUrlMap,
                usedEmojiIds = usedEmojiIds,
                linkListener = linkListener,
                collapseWebLinks = collapseWebLinks,
            )
        }
    }
}

internal fun resolveDynamicRichTextTopicId(node: RichTextNode): Long? {
    node.rid
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { return it }

    val jumpUrl = node.jump_url.orEmpty()
    DYNAMIC_TOPIC_QUERY_ID_PATTERN.find(jumpUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { return it }

    return DYNAMIC_TOPIC_PATH_ID_PATTERN.find(jumpUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
}

private val DYNAMIC_TOPIC_QUERY_ID_PATTERN =
    """(?:[?&](?:topic_id|topicId)=)(\d+)""".toRegex(RegexOption.IGNORE_CASE)
private val DYNAMIC_TOPIC_PATH_ID_PATTERN =
    """(?:topic|topic-detail)/(\d+)""".toRegex(RegexOption.IGNORE_CASE)

private fun AnnotatedString.Builder.appendDynamicRichTextTopic(
    node: RichTextNode,
    primaryColor: Color,
    linkListener: LinkInteractionListener?,
) {
    val displayToken = resolveDynamicRichTextNodeToken(node)
    val keyword = displayToken.trim().removePrefix("#").removeSuffix("#").trim()
    val topicId = resolveDynamicRichTextTopicId(node)
    // 带 topicId 优先跳话题详情；无 id 时回落关键词搜索。原生链接单一 tag
    // 承载其中一种载荷，由 [resolveDynamicRichTextLinkAction] 解析。
    val payload = when {
        topicId != null -> DYNAMIC_RICH_TEXT_LINK_TOPIC_ID_PREFIX + topicId
        keyword.isNotEmpty() -> DYNAMIC_RICH_TEXT_LINK_TOPIC_KEYWORD_PREFIX + keyword
        else -> null
    }
    if (payload != null) {
        withLink(dynamicRichTextLinkAnnotation(payload, linkListener)) {
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                append(displayToken)
            }
        }
    } else {
        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
            append(displayToken)
        }
    }
}

internal fun resolveDynamicRichTextUserMid(node: RichTextNode): Long? {
    node.rid
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { return it }

    // Space feeds often put the mid only on jump_url (//space.bilibili.com/{mid}).
    when (val target = BilibiliNavigationTargetParser.parse(node.jump_url.orEmpty())) {
        is BilibiliNavigationTarget.Space -> return target.mid.takeIf { it > 0L }
        else -> Unit
    }
    return null
}

private fun AnnotatedString.Builder.appendDynamicRichTextAtMention(
    node: RichTextNode,
    primaryColor: Color,
    linkListener: LinkInteractionListener?,
) {
    val mid = resolveDynamicRichTextUserMid(node)
    if (mid != null) {
        withLink(
            dynamicRichTextLinkAnnotation(
                DYNAMIC_RICH_TEXT_LINK_USER_PREFIX + mid,
                linkListener,
            )
        ) {
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                append(resolveDynamicRichTextNodeToken(node))
            }
        }
    } else {
        val display = resolveDynamicRichTextNodeToken(node)
        val name = display.trim().removePrefix("@").trim()
        if (name.isNotEmpty()) {
            withLink(dynamicRichTextLinkAnnotation(DYNAMIC_RICH_TEXT_LINK_USER_NAME_PREFIX + name, linkListener)) {
                withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                    append(display)
                }
            }
        } else {
            append(display)
        }
    }
}

private fun shouldRenderDynamicRichTextLink(
    nodeType: String,
    node: RichTextNode
): Boolean {
    val normalized = nodeType.uppercase()
    if (normalized in setOf("AT", "EMOJI", "VOTE")) return false
    if (normalized in DYNAMIC_RICH_TEXT_LINK_NODE_TYPES) {
        return !resolveDynamicRichTextLinkTarget(node).isNullOrBlank()
    }
    val display = resolveDynamicRichTextNodeToken(node)
    return !resolveDynamicRichTextLinkTarget(node).isNullOrBlank() &&
        DYNAMIC_RICH_TEXT_URL_PATTERN.containsMatchIn(display)
}

private val DYNAMIC_RICH_TEXT_LINK_NODE_TYPES = setOf(
    "WEB",
    "LINK",
    "URL",
    "TOPIC",
    "GOODS",
    "BV",
    "AV",
    "CV",
    "VIEW_PICTURE",
    "TAOBAO",
    "MAIL",
    "OGV_SEASON",
    "OGV_EP",
    "LOTTERY",
)

private fun resolveDynamicRichTextLinkTarget(node: RichTextNode): String? {
    normalizeDynamicRichTextUrl(node.jump_url)?.let { return it }
    return DYNAMIC_RICH_TEXT_URL_PATTERN.find(node.orig_text)?.value
        ?: DYNAMIC_RICH_TEXT_URL_PATTERN.find(resolveDynamicRichTextNodeToken(node))?.value
}

/**
 * Expand known emote shortcodes and plain URLs inside free text.
 */
private fun AnnotatedString.Builder.appendDynamicRichTextExpandableText(
    text: String,
    primaryColor: Color,
    textColor: Color,
    emoteUrlMap: Map<String, String>,
    usedEmojiIds: MutableMap<String, String>,
    linkListener: LinkInteractionListener? = null,
    collapseWebLinks: Boolean,
) {
    if (text.isEmpty()) return
    if (emoteUrlMap.isEmpty()) {
        withStyle(SpanStyle(color = textColor)) {
            appendDynamicRichTextPlainText(
                text = text,
                primaryColor = primaryColor,
                linkListener = linkListener,
                collapseWebLinks = collapseWebLinks,
            )
        }
        return
    }

    var lastIndex = 0
    DYNAMIC_EMOTE_TOKEN_PATTERN.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            withStyle(SpanStyle(color = textColor)) {
                appendDynamicRichTextPlainText(
                    text = text.substring(lastIndex, match.range.first),
                    primaryColor = primaryColor,
                    linkListener = linkListener,
                    collapseWebLinks = collapseWebLinks,
                )
            }
        }
        val token = match.value
        val iconUrl = emoteUrlMap[token]
        if (!iconUrl.isNullOrBlank()) {
            usedEmojiIds[token] = iconUrl
            appendInlineContent(id = token, alternateText = token)
        } else {
            withStyle(SpanStyle(color = textColor)) {
                append(token)
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        withStyle(SpanStyle(color = textColor)) {
            appendDynamicRichTextPlainText(
                text = text.substring(lastIndex),
                primaryColor = primaryColor,
                linkListener = linkListener,
                collapseWebLinks = collapseWebLinks,
            )
        }
    }
}

private val DYNAMIC_RICH_TEXT_TOPIC_PATTERN = Regex("""#([^#\n\r\t]+)#""")
private val DYNAMIC_RICH_TEXT_MENTION_PATTERN =
    Regex("""(?<![A-Za-z0-9_.])@[\p{L}\p{N}_.·-]{1,32}""")

private enum class DynamicPlainTextTokenKind { URL, TOPIC, MENTION }

private data class DynamicPlainTextToken(
    val range: IntRange,
    val kind: DynamicPlainTextTokenKind,
    val value: String,
    val keyword: String? = null
)

private fun AnnotatedString.Builder.appendDynamicRichTextPlainText(
    text: String,
    primaryColor: Color,
    linkListener: LinkInteractionListener? = null,
    collapseWebLinks: Boolean,
) {
    val tokens = mutableListOf<DynamicPlainTextToken>()
    DYNAMIC_RICH_TEXT_URL_PATTERN.findAll(text).forEach { match ->
        tokens += DynamicPlainTextToken(
            range = match.range,
            kind = DynamicPlainTextTokenKind.URL,
            value = match.value
        )
    }
    DYNAMIC_RICH_TEXT_TOPIC_PATTERN.findAll(text).forEach { match ->
        val overlapsUrl = tokens.any { existing ->
            match.range.first <= existing.range.last && match.range.last >= existing.range.first
        }
        if (!overlapsUrl) {
            val kw = match.groupValues[1].trim()
            if (kw.isNotEmpty()) {
                tokens += DynamicPlainTextToken(
                    range = match.range,
                    kind = DynamicPlainTextTokenKind.TOPIC,
                    value = match.value,
                    keyword = kw
                )
            }
        }
    }
    // Detail paragraphs can contain only TEXT nodes, while other spans in the same
    // paragraph still have metadata. Highlight missing mentions inside plain fragments;
    // structured AT nodes keep their user IDs and are rendered before this fallback.
    DYNAMIC_RICH_TEXT_MENTION_PATTERN.findAll(text).forEach { match ->
        val overlapsExisting = tokens.any { existing ->
            match.range.first <= existing.range.last && match.range.last >= existing.range.first
        }
        if (!overlapsExisting) {
            tokens += DynamicPlainTextToken(
                range = match.range,
                kind = DynamicPlainTextTokenKind.MENTION,
                value = match.value,
            )
        }
    }
    tokens.sortBy { it.range.first }

    var lastIndex = 0
    tokens.forEach { token ->
        if (token.range.first > lastIndex) {
            append(text.substring(lastIndex, token.range.first))
        }
        when (token.kind) {
            DynamicPlainTextTokenKind.URL -> appendDynamicRichTextLink(
                displayText = token.value,
                targetUrl = token.value,
                primaryColor = primaryColor,
                linkListener = linkListener,
                collapseWebLinks = collapseWebLinks,
            )
            DynamicPlainTextTokenKind.TOPIC -> {
                val kw = token.keyword.orEmpty()
                if (kw.isNotEmpty()) {
                    withLink(
                        dynamicRichTextLinkAnnotation(
                            DYNAMIC_RICH_TEXT_LINK_TOPIC_KEYWORD_PREFIX + kw,
                            linkListener,
                        )
                    ) {
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                            append(token.value)
                        }
                    }
                } else {
                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                        append(token.value)
                    }
                }
            }
            DynamicPlainTextTokenKind.MENTION -> withLink(
                dynamicRichTextLinkAnnotation(
                    DYNAMIC_RICH_TEXT_LINK_USER_NAME_PREFIX + token.value.removePrefix("@"),
                    linkListener,
                )
            ) {
                withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                    append(token.value)
                }
            }
        }
        lastIndex = token.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

private fun AnnotatedString.Builder.appendDynamicRichTextLinkNode(
    displayText: String,
    targetUrl: String?,
    primaryColor: Color,
    textColor: Color,
    linkListener: LinkInteractionListener?,
    collapseWebLinks: Boolean,
) {
    val urls = DYNAMIC_RICH_TEXT_URL_PATTERN.findAll(displayText).toList()
    if (urls.isEmpty()) {
        appendDynamicRichTextLink(displayText, targetUrl, primaryColor, linkListener, collapseWebLinks)
        return
    }

    // 部分富文本节点会把说明和网址一起返回。链接的点击范围只覆盖网址。
    var cursor = 0
    urls.forEach { url ->
        withStyle(SpanStyle(color = textColor)) {
            append(displayText.substring(cursor, url.range.first))
        }
        appendDynamicRichTextLink(
            displayText = url.value,
            targetUrl = if (urls.size == 1) targetUrl else url.value,
            primaryColor = primaryColor,
            linkListener = linkListener,
            collapseWebLinks = collapseWebLinks,
        )
        cursor = url.range.last + 1
    }
    withStyle(SpanStyle(color = textColor)) {
        append(displayText.substring(cursor))
    }
}

private fun AnnotatedString.Builder.appendDynamicRichTextLink(
    displayText: String,
    targetUrl: String?,
    primaryColor: Color,
    linkListener: LinkInteractionListener?,
    collapseWebLinks: Boolean,
) {
    val resolvedUrl = targetUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: displayText
    withLink(
        dynamicRichTextLinkAnnotation(
            DYNAMIC_RICH_TEXT_LINK_URL_PREFIX + resolvedUrl,
            linkListener,
        )
    ) {
        withStyle(
            SpanStyle(
                color = primaryColor,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(resolveDynamicWebLinkDisplayText(displayText, collapseWebLinks))
        }
    }
}

internal fun resolveDynamicWebLinkDisplayText(
    displayText: String,
    collapseWebLinks: Boolean,
): String {
    if (!collapseWebLinks) return displayText
    val normalized = normalizeDynamicRichTextUrl(displayText).orEmpty()
    val isWebUrl = normalized.startsWith("https://") || normalized.startsWith("http://")
    return if (isWebUrl && DYNAMIC_RICH_TEXT_URL_PATTERN.matches(normalized)) {
        displayText.replace(displayText.trim(), "网页链接")
    } else {
        displayText
    }
}

private fun AnnotatedString.Builder.appendDynamicRichTextVote(
    displayText: String,
    voteId: String?,
    primaryColor: Color,
    linkListener: LinkInteractionListener?,
) {
    val normalizedVoteId = voteId?.trim()?.toLongOrNull()?.takeIf { it > 0L }
    if (normalizedVoteId != null) {
        withLink(
            dynamicRichTextLinkAnnotation(
                DYNAMIC_RICH_TEXT_LINK_VOTE_PREFIX + normalizedVoteId,
                linkListener,
            )
        ) {
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                append(displayText)
            }
        }
    } else {
        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
            append(displayText)
        }
    }
}

private fun normalizeDynamicRichTextUrl(rawUrl: String?): String? {
    val url = rawUrl?.trim().orEmpty()
    if (url.isBlank()) return null
    return when {
        url.startsWith("//") -> "https:$url"
        else -> url
    }
}

private fun normalizeDynamicImageUrl(rawUrl: String): String {
    return when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
        else -> rawUrl
    }
}

private fun isDynamicRichTextInAppHost(url: String): Boolean {
    val normalized = normalizeDynamicRichTextUrl(url) ?: return false
    val host = runCatching { java.net.URI(normalized) }
        .getOrNull()
        ?.host
        ?.lowercase()
        .orEmpty()
    return host.contains("b23.tv") || host.contains("bilibili.com")
}
