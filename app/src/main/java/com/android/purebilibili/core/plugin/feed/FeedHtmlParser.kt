package com.android.purebilibili.core.plugin.feed

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

private val htmlWhitespace = Regex("[\\t\\r\\n ]+")
private val ignoredTags = setOf("script", "style", "noscript", "nav", "header", "footer", "aside", "form")
private val boundaryTags = setOf("p", "div", "section", "article", "figure", "li")

fun feedPlainText(html: String): String = parseFeedHtml(html)
    .joinToString(" ") { block ->
        when (block) {
            is FeedBlock.Heading -> plain(block.inlines)
            is FeedBlock.Paragraph -> plain(block.inlines)
            is FeedBlock.Quote -> plain(block.inlines)
            is FeedBlock.BulletList -> block.items.joinToString(" ", transform = ::plain)
            is FeedBlock.NumberedList -> block.items.joinToString(" ", transform = ::plain)
            is FeedBlock.Code -> block.text
            is FeedBlock.Image -> block.alt
            is FeedBlock.EmbeddedLink -> block.title
        }
    }.replace(Regex("\\s+"), " ").trim()

fun parseFeedHtml(html: String, baseUrl: String? = null): List<FeedBlock> =
    parseFeedHtml(Jsoup.parseBodyFragment(html, baseUrl.orEmpty()).body(), baseUrl)

internal fun parseFeedHtml(content: Element, baseUrl: String?): List<FeedBlock> {
    val blocks = mutableListOf<FeedBlock>()
    val pending = mutableListOf<FeedInline>()
    fun flush() {
        val content = trimInlines(pending)
        if (content.isNotEmpty()) blocks += FeedBlock.Paragraph(content)
        pending.clear()
    }
    fun visit(node: Node, bold: Boolean = false, italic: Boolean = false, link: String? = null) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText.replace(htmlWhitespace, " ").replace('\u00A0', ' ')
                pending += if (link != null) FeedInline.Link(text, link) else FeedInline.Text(text, bold, italic)
            }
            is Element -> {
                val tag = node.normalName()
                if (tag in ignoredTags) return
                when (tag) {
                    "img" -> { flush(); imageBlock(node, baseUrl)?.let(blocks::add) }
                    "br" -> pending += FeedInline.Text("\n")
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        flush()
                        trimInlines(inlines(node.childNodes(), baseUrl)).takeIf { it.isNotEmpty() }
                            ?.let { blocks += FeedBlock.Heading(tag.drop(1).toInt(), it) }
                    }
                    "blockquote" -> {
                        flush()
                        trimInlines(inlines(node.childNodes(), baseUrl)).takeIf { it.isNotEmpty() }
                            ?.let { blocks += FeedBlock.Quote(it) }
                    }
                    "pre" -> {
                        flush()
                        node.wholeText().trim('\n', ' ', '\t').takeIf { it.isNotEmpty() }
                            ?.let { blocks += FeedBlock.Code(it) }
                    }
                    "ul", "ol" -> {
                        flush()
                        val lines = node.childNodes().filterIsInstance<Element>()
                            .filter { it.normalName() == "li" }
                            .map { trimInlines(inlines(it.childNodes(), baseUrl)) }
                            .filter { it.isNotEmpty() }
                        if (lines.isNotEmpty()) blocks += if (tag == "ol") FeedBlock.NumberedList(lines) else FeedBlock.BulletList(lines)
                    }
                    "iframe", "video", "audio" -> {
                        flush()
                        resolveFeedUrl(node.attr("src").ifBlank { node.attr("href") }, baseUrl)
                            ?.let { blocks += FeedBlock.EmbeddedLink("打开嵌入内容", it) }
                    }
                    "a", "b", "strong", "i", "em", "span", "small", "code" ->
                        node.childNodes().forEach {
                            visit(
                                it,
                                bold || tag == "b" || tag == "strong",
                                italic || tag == "i" || tag == "em",
                                if (tag == "a") resolveFeedUrl(node.attr("href"), baseUrl) else link,
                            )
                        }
                    else -> {
                        val boundary = tag in boundaryTags
                        if (boundary) flush()
                        node.childNodes().forEach { visit(it, bold, italic, link) }
                        if (boundary) flush()
                    }
                }
            }
            else -> Unit
        }
    }
    content.childNodes().forEach { visit(it) }
    flush()
    return blocks
}

private fun inlines(
    nodes: List<Node>, baseUrl: String?, bold: Boolean = false,
    italic: Boolean = false, link: String? = null,
): List<FeedInline> = buildList {
    nodes.forEach { node ->
        when (node) {
            is TextNode -> {
                val value = node.wholeText.replace(htmlWhitespace, " ").replace('\u00A0', ' ')
                if (value.isNotEmpty()) add(if (link != null) FeedInline.Link(value, link) else FeedInline.Text(value, bold, italic))
            }
            is Element -> {
                val tag = node.normalName()
                if (tag in ignoredTags || tag == "img") return@forEach
                if (tag == "br") add(FeedInline.Text("\n", bold, italic))
                else {
                    addAll(inlines(
                        node.childNodes(), baseUrl,
                        bold || tag == "b" || tag == "strong",
                        italic || tag == "i" || tag == "em",
                        if (tag == "a") resolveFeedUrl(node.attr("href"), baseUrl) else link,
                    ))
                    if (tag in boundaryTags) add(FeedInline.Text(" "))
                }
            }
            else -> Unit
        }
    }
}

private fun trimInlines(input: List<FeedInline>): List<FeedInline> {
    val output = input.toMutableList()
    if (output.firstOrNull() is FeedInline.Text) {
        val first = output.first() as FeedInline.Text
        output[0] = first.copy(text = first.text.trimStart())
    }
    if (output.lastOrNull() is FeedInline.Text) {
        val last = output.last() as FeedInline.Text
        output[output.lastIndex] = last.copy(text = last.text.trimEnd())
    }
    return output.filter { when (it) { is FeedInline.Text -> it.text.isNotEmpty(); is FeedInline.Link -> it.text.isNotEmpty() } }
}

private fun imageBlock(node: Element, baseUrl: String?): FeedBlock.Image? {
    val source = sequenceOf("data-src", "data-original", "data-lazy-src", "src")
        .mapNotNull { resolveFeedUrl(node.attr(it), baseUrl) }.firstOrNull()
        ?: node.attr("srcset").substringBefore(',').trim().substringBefore(' ')
            .let { resolveFeedUrl(it, baseUrl) }
    return source?.let { FeedBlock.Image(it, node.attr("alt")) }
}

private fun plain(inlines: List<FeedInline>): String = inlines.joinToString("") {
    when (it) { is FeedInline.Text -> it.text; is FeedInline.Link -> it.text }
}

internal fun resolveFeedUrl(raw: String?, baseUrl: String?): String? {
    val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val resolved = if (baseUrl.isNullOrBlank()) URI(candidate) else URI(baseUrl).resolve(candidate)
        resolved.toString().takeIf(::isHttpFeedUrl)
    }.getOrNull()
}
