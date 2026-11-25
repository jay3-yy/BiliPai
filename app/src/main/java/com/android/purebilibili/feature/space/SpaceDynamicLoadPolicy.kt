package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.ArchiveStat
import com.android.purebilibili.data.model.response.ArticleMajor
import com.android.purebilibili.data.model.response.DrawItem
import com.android.purebilibili.data.model.response.DrawMajor
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.DynamicStatModule
import com.android.purebilibili.data.model.response.EmojiInfo
import com.android.purebilibili.data.model.response.OpusContentBlock
import com.android.purebilibili.data.model.response.OpusMajor
import com.android.purebilibili.data.model.response.OpusPic
import com.android.purebilibili.data.model.response.OpusSummary
import com.android.purebilibili.data.model.response.RichTextNode
import com.android.purebilibili.data.model.response.SpaceDynamicContent
import com.android.purebilibili.data.model.response.SpaceDynamicDesc
import com.android.purebilibili.data.model.response.SpaceDynamicEmoji
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.SpaceDynamicMajor
import com.android.purebilibili.data.model.response.SpaceDynamicRichText
import com.android.purebilibili.data.model.response.StatItem

enum class SpaceDynamicPresentationState {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR
}

internal fun shouldRequestInitialSpaceDynamicLoad(
    hasLoadedOnce: Boolean,
    isLoading: Boolean
): Boolean {
    return !hasLoadedOnce && !isLoading
}

fun resolveSpaceDynamicPresentationState(
    itemCount: Int,
    isLoading: Boolean,
    hasLoadedOnce: Boolean,
    lastLoadFailed: Boolean
): SpaceDynamicPresentationState {
    if (itemCount > 0) return SpaceDynamicPresentationState.CONTENT
    if (isLoading || !hasLoadedOnce) return SpaceDynamicPresentationState.LOADING
    if (lastLoadFailed) return SpaceDynamicPresentationState.ERROR
    return SpaceDynamicPresentationState.EMPTY
}

/** Extra feed pages to pull when local search has no hit yet. */
internal const val SPACE_DYNAMIC_SEARCH_PREFETCH_PAGE_LIMIT = 5

internal const val SPACE_DYNAMIC_SEARCH_DEBOUNCE_MS = 300L

/**
 * Keep fetching more space dynamics while the query has no local matches.
 * Stops when a match appears, the feed ends, or [maxPages] pages were pulled this search.
 */
internal fun shouldPrefetchMoreSpaceDynamicsForSearch(
    query: String,
    matchCount: Int,
    hasMore: Boolean,
    pagesFetchedForSearch: Int,
    maxPages: Int = SPACE_DYNAMIC_SEARCH_PREFETCH_PAGE_LIMIT
): Boolean {
    if (query.trim().isEmpty()) return false
    if (matchCount > 0) return false
    if (!hasMore) return false
    if (pagesFetchedForSearch >= maxPages) return false
    return true
}

internal fun mergeSpaceDynamicPages(
    existing: List<SpaceDynamicItem>,
    incoming: List<SpaceDynamicItem>
): List<SpaceDynamicItem> {
    if (incoming.isEmpty()) return existing
    if (existing.isEmpty()) return incoming
    val seen = existing.mapTo(HashSet(existing.size + incoming.size)) { it.id_str }
    val merged = existing.toMutableList()
    incoming.forEach { item ->
        if (item.id_str.isNotBlank() && seen.add(item.id_str)) {
            merged += item
        }
    }
    return merged
}

/**
 * Build searchable plain text for a space dynamic.
 *
 * Bilibili space feeds often leave [SpaceDynamicDesc.text] empty and put body only in
 * rich_text_nodes (or only on reposted [SpaceDynamicItem.orig]). UI concatenates those nodes
 * without separators, so search must also join continuously — newline-joined node text
 * fails phrases that span multiple rich-text nodes (looks like "only video titles work").
 */
internal fun resolveSpaceDynamicSearchText(item: SpaceDynamicItem): String {
    return buildString {
        appendSpaceDynamicContentSearchText(item.modules.module_dynamic)
        item.orig?.let { orig ->
            appendSpaceDynamicContentSearchText(orig.modules.module_dynamic)
            appendLineIfNotBlank(orig.modules.module_author?.name)
        }
        appendLineIfNotBlank(item.modules.module_author?.name)
        appendLineIfNotBlank(item.type)
        appendLineIfNotBlank(item.id_str)
    }
}

/**
 * Prefer the same text surface users see on the card (mapped [DynamicItem]), then fall back
 * to raw [SpaceDynamicItem] fields. This covers presentation fallbacks (e.g. article desc).
 */
internal fun resolveSpaceDynamicSearchTextForFilter(item: SpaceDynamicItem): String {
    val fromCard = resolveDynamicItemSearchText(resolveSpaceDynamicCardItem(item))
    val fromRaw = resolveSpaceDynamicSearchText(item)
    return when {
        fromCard.isBlank() -> fromRaw
        fromRaw.isBlank() -> fromCard
        fromCard == fromRaw -> fromCard
        else -> "$fromCard\n$fromRaw"
    }
}

internal fun filterSpaceDynamicItemsByQuery(
    items: List<SpaceDynamicItem>,
    query: String
): List<SpaceDynamicItem> {
    val normalizedQuery = normalizeSpaceDynamicSearchQuery(query)
    if (normalizedQuery.isEmpty()) return items

    return items.filter { item ->
        normalizeSpaceDynamicSearchHaystack(resolveSpaceDynamicSearchTextForFilter(item))
            .contains(normalizedQuery)
    }
}

internal fun resolveDynamicItemSearchText(item: DynamicItem): String {
    return buildString {
        appendDynamicContentSearchText(item.modules.module_dynamic)
        item.orig?.let { orig ->
            appendDynamicContentSearchText(orig.modules.module_dynamic)
            appendLineIfNotBlank(orig.modules.module_author?.name)
        }
        appendLineIfNotBlank(item.modules.module_author?.name)
        appendLineIfNotBlank(item.type)
        appendLineIfNotBlank(item.id_str)
    }
}

private fun StringBuilder.appendSpaceDynamicContentSearchText(content: SpaceDynamicContent?) {
    if (content == null) return
    appendRichDescSearchText(content.desc?.text, content.desc?.rich_text_nodes.orEmpty())
    val major = content.major ?: return
    major.archive?.let { archive ->
        appendLineIfNotBlank(archive.title)
        appendLineIfNotBlank(archive.desc)
        appendLineIfNotBlank(archive.bvid)
        appendLineIfNotBlank(archive.aid)
    }
    major.opus?.let { opus ->
        appendLineIfNotBlank(opus.title)
        appendRichDescSearchText(opus.summary?.text, opus.summary?.rich_text_nodes.orEmpty())
    }
    major.article?.let { article ->
        appendLineIfNotBlank(article.title)
        appendLineIfNotBlank(article.desc)
        appendLineIfNotBlank(article.label)
    }
}

private fun StringBuilder.appendDynamicContentSearchText(content: DynamicContentModule?) {
    if (content == null) return
    appendRichDescSearchText(
        plainText = content.desc?.text,
        nodes = content.desc?.rich_text_nodes.orEmpty().map { node ->
            SpaceDynamicRichText(
                type = node.type,
                text = node.text,
                orig_text = "",
                emoji = node.emoji?.let { emoji ->
                    SpaceDynamicEmoji(
                        icon_url = emoji.icon_url,
                        size = emoji.size,
                        text = emoji.text
                    )
                },
                jump_url = node.jump_url,
                rid = node.rid
            )
        }
    )
    val major = content.major ?: return
    major.archive?.let { archive ->
        appendLineIfNotBlank(archive.title)
        appendLineIfNotBlank(archive.desc)
        appendLineIfNotBlank(archive.bvid)
        appendLineIfNotBlank(archive.aid)
    }
    major.pgc?.let { pgc ->
        appendLineIfNotBlank(pgc.title)
        appendLineIfNotBlank(pgc.desc)
        appendLineIfNotBlank(pgc.bvid)
    }
    major.opus?.let { opus ->
        appendLineIfNotBlank(opus.title)
        appendRichDescSearchText(
            plainText = opus.summary?.text,
            nodes = opus.summary?.rich_text_nodes.orEmpty().map { node ->
                SpaceDynamicRichText(
                    type = node.type,
                    text = node.text,
                    orig_text = "",
                    emoji = node.emoji?.let { emoji ->
                        SpaceDynamicEmoji(
                            icon_url = emoji.icon_url,
                            size = emoji.size,
                            text = emoji.text
                        )
                    },
                    jump_url = node.jump_url,
                    rid = node.rid
                )
            }
        )
        opus.contentBlocks.forEach { block ->
            when (block) {
                is OpusContentBlock.Text -> appendLineIfNotBlank(block.text)
                is OpusContentBlock.Heading -> appendLineIfNotBlank(block.text)
                is OpusContentBlock.Quote -> appendLineIfNotBlank(block.text)
                is OpusContentBlock.ListBlock -> block.items.forEach(::appendLineIfNotBlank)
                is OpusContentBlock.Code -> appendLineIfNotBlank(block.text)
                is OpusContentBlock.LinkCard -> {
                    appendLineIfNotBlank(block.card.title)
                    appendLineIfNotBlank(block.card.description)
                    appendLineIfNotBlank(block.card.label)
                }
                is OpusContentBlock.Divider,
                is OpusContentBlock.Image -> Unit
            }
        }
    }
    major.article?.let { article ->
        appendLineIfNotBlank(article.title)
        appendLineIfNotBlank(article.desc)
        appendLineIfNotBlank(article.label)
    }
    major.ugc_season?.let { season ->
        appendLineIfNotBlank(season.title)
        appendLineIfNotBlank(season.desc)
        appendLineIfNotBlank(season.intro)
    }
    major.live_rcmd?.let { live ->
        appendLineIfNotBlank(live.content)
    }
}

private fun StringBuilder.appendRichDescSearchText(
    plainText: String?,
    nodes: List<SpaceDynamicRichText>
) {
    appendLineIfNotBlank(plainText)
    if (nodes.isEmpty()) return

    // Match on-screen concatenation (no separators between rich nodes).
    val continuous = buildString {
        nodes.forEach { node ->
            append(resolveSpaceDynamicRichTextNodeSearchToken(node))
        }
    }
    appendLineIfNotBlank(continuous)

    // Also keep per-node tokens so short unique fragments still match.
    nodes.forEach { node ->
        appendLineIfNotBlank(node.text)
        appendLineIfNotBlank(node.orig_text)
        appendLineIfNotBlank(node.emoji?.text)
    }
}

private fun resolveSpaceDynamicRichTextNodeSearchToken(node: SpaceDynamicRichText): String {
    val emojiText = node.emoji?.text.orEmpty()
    return when {
        node.text.isNotBlank() -> node.text
        node.orig_text.isNotBlank() -> node.orig_text
        emojiText.isNotBlank() -> emojiText
        else -> ""
    }
}

internal fun normalizeSpaceDynamicSearchQuery(query: String): String {
    return query.trim().lowercase()
}

internal fun normalizeSpaceDynamicSearchHaystack(text: String): String {
    // Keep CJK continuity; only collapse common whitespace so node joins still match.
    return text
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
}

private fun StringBuilder.appendLineIfNotBlank(value: String?) {
    val normalized = value?.trim().orEmpty()
    if (normalized.isEmpty()) return
    if (isNotEmpty()) append('\n')
    append(normalized)
}

internal fun resolveSpaceDynamicCardItems(items: List<SpaceDynamicItem>): List<DynamicItem> {
    return items.map(::resolveSpaceDynamicCardItem)
}

private fun SpaceDynamicRichText.toDynamicRichTextNode(): RichTextNode {
    return RichTextNode(
        type = type,
        // AT / text nodes sometimes only fill orig_text in space feeds.
        text = text.ifBlank { orig_text },
        orig_text = orig_text.ifBlank { text },
        emoji = emoji?.let { emoji ->
            EmojiInfo(
                icon_url = emoji.icon_url,
                webp_url = emoji.webp_url,
                gif_url = emoji.gif_url,
                size = emoji.size,
                text = emoji.text
            )
        },
        jump_url = jump_url,
        rid = rid
    )
}

private fun SpaceDynamicDesc.toDynamicDesc(): DynamicDesc {
    return DynamicDesc(
        text = text,
        rich_text_nodes = rich_text_nodes.map { it.toDynamicRichTextNode() }
    )
}

private fun resolveSpaceDynamicArticleFallbackDesc(major: SpaceDynamicMajor?): DynamicDesc? {
    val article = major?.article ?: return null
    val text = listOf(article.title.trim(), article.desc.trim())
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(separator = "\n")
    return text.takeIf { it.isNotBlank() }?.let { DynamicDesc(text = it) }
}

private fun resolveSpaceDynamicContentDesc(content: SpaceDynamicContent): DynamicDesc? {
    val mappedDesc = content.desc?.toDynamicDesc()
    if (mappedDesc != null && (mappedDesc.text.isNotBlank() || mappedDesc.rich_text_nodes.isNotEmpty())) {
        return mappedDesc
    }
    return resolveSpaceDynamicArticleFallbackDesc(content.major)
}

internal fun resolveSpaceDynamicCardItem(item: SpaceDynamicItem): DynamicItem {
    return DynamicItem(
        id_str = item.id_str,
        type = item.type,
        visible = item.visible,
        basic = item.basic,
        // Forward cards render nested content via DynamicCard(ForwardedContent) when orig is set.
        orig = item.orig?.let(::resolveSpaceDynamicCardItem),
        modules = DynamicModules(
            module_tag = item.modules.module_tag,
            module_dispute = item.modules.module_dispute,
            module_interaction = item.modules.module_interaction,
            module_author = item.modules.module_author?.let { author ->
                DynamicAuthorModule(
                    mid = author.mid,
                    name = author.name,
                    face = author.face,
                    pub_time = author.pub_time,
                    pub_ts = author.pub_ts,
                    official_verify = author.official_verify,
                    vip = author.vip
                )
            },
            module_dynamic = item.modules.module_dynamic?.let { content ->
                DynamicContentModule(
                    desc = resolveSpaceDynamicContentDesc(content),
                    topic = content.topic,
                    additional = content.additional,
                    major = content.major?.let { major ->
                        DynamicMajor(
                            type = major.type,
                            archive = major.archive?.let { archive ->
                                ArchiveMajor(
                                    aid = archive.aid,
                                    bvid = archive.bvid,
                                    title = archive.title,
                                    cover = archive.cover,
                                    desc = archive.desc,
                                    duration_text = archive.duration_text,
                                    stat = ArchiveStat(
                                        play = archive.stat.play,
                                        danmaku = archive.stat.danmaku
                                    ),
                                    badge = archive.badge,
                                    isChargingArc = archive.isChargingArc,
                                    elecArcType = archive.elecArcType,
                                    isUgcpay = archive.isUgcpay,
                                    ugcPay = archive.ugcPay,
                                    ugcPayPreview = archive.ugcPayPreview
                                )
                            },
                            draw = major.draw?.let { draw ->
                                DrawMajor(
                                    id = draw.id,
                                    items = draw.items.map { drawItem ->
                                        DrawItem(
                                            src = drawItem.src,
                                            width = drawItem.width,
                                            height = drawItem.height,
                                            live_url = drawItem.live_url
                                        )
                                    }
                                )
                            },
                            opus = major.opus?.let { opus ->
                                OpusMajor(
                                    summary = opus.summary?.let { summary ->
                                        OpusSummary(
                                            text = summary.text,
                                            rich_text_nodes = summary.rich_text_nodes.map { it.toDynamicRichTextNode() }
                                        )
                                    },
                                    pics = opus.pics.map { pic ->
                                        OpusPic(
                                            url = pic.src,
                                            width = pic.width,
                                            height = pic.height,
                                            live_url = pic.live_url
                                        )
                                    },
                                    title = opus.title
                                )
                            },
                            article = major.article?.let { article ->
                                ArticleMajor(
                                    id = article.id,
                                    title = article.title,
                                    desc = article.desc,
                                    covers = article.covers,
                                    jump_url = article.jump_url,
                                    label = article.label
                                )
                            }
                        )
                    }
                )
            },
            module_more = item.modules.module_more,
            module_stat = item.modules.module_stat?.let { stat ->
                DynamicStatModule(
                    comment = StatItem(
                        count = stat.comment.count,
                        forbidden = stat.comment.forbidden,
                        status = stat.comment.status,
                    ),
                    forward = StatItem(
                        count = stat.forward.count,
                        forbidden = stat.forward.forbidden,
                        status = stat.forward.status,
                    ),
                    like = StatItem(
                        count = stat.like.count,
                        forbidden = stat.like.forbidden,
                        status = stat.like.status,
                    )
                )
            }
        )
    )
}
