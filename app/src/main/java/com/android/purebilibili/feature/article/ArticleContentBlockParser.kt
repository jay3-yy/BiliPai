package com.android.purebilibili.feature.article

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@androidx.compose.runtime.Immutable
sealed interface ArticleContentBlock {
    @androidx.compose.runtime.Immutable
    data class Heading(
        val text: String,
        val spans: List<ArticleTextSpan> = emptyList()
    ) : ArticleContentBlock
    @androidx.compose.runtime.Immutable
    data class Paragraph(
        val text: String,
        val spans: List<ArticleTextSpan> = emptyList()
    ) : ArticleContentBlock
    @androidx.compose.runtime.Immutable
    data class Quote(
        val text: String,
        val spans: List<ArticleTextSpan> = emptyList()
    ) : ArticleContentBlock
    @androidx.compose.runtime.Immutable
    data class ListBlock(
        val ordered: Boolean,
        val items: List<String>
    ) : ArticleContentBlock
    @androidx.compose.runtime.Immutable
    data class Code(
        val language: String,
        val content: String
    ) : ArticleContentBlock
    @androidx.compose.runtime.Immutable
    data class MediaCard(
        val card: com.android.purebilibili.data.model.response.OpusLinkCard,
        val preview: Image? = null,
    ) : ArticleContentBlock
    @androidx.compose.runtime.Immutable
    data class Image(
        val url: String,
        val width: Int = 0,
        val height: Int = 0
    ) : ArticleContentBlock
}

/**
 * 行内富文本片段：保留专栏正文里的字号、颜色、加粗、斜体、删除线。
 * 空字段表示沿用外层块级样式。
 */
@androidx.compose.runtime.Immutable
data class ArticleTextSpan(
    val text: String,
    val fontSizeSp: Int? = null,
    val colorArgb: Long? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val link: String? = null,
)

private const val ARTICLE_HEADING_FONT_SIZE = 22

internal fun parseArticleContentBlocks(
    structuredParagraphs: List<JsonObject>,
    htmlContent: String?,
    ops: List<JsonObject> = emptyList()
): List<ArticleContentBlock> {
    val structuredBlocks = structuredParagraphs
        .flatMap(::parseStructuredParagraph)
        .mergeAdjacentListBlocks()
    val contentOps = ops.ifEmpty { parseOpsFromContentJson(htmlContent) }
    val opsBlocks = parseOpsBlocks(contentOps)
    val htmlBlocks = if (contentOps.isNotEmpty()) emptyList() else {
        parseHtmlBlocks(htmlContent).mergeAdjacentListBlocks()
    }
    return selectRicherArticleBlocks(structuredBlocks, opsBlocks, htmlBlocks)
}

private val articleContentJson = Json { ignoreUnknownKeys = true }

private fun parseOpsFromContentJson(content: String?): List<JsonObject> {
    val rawContent = content?.trim().orEmpty()
    if (!rawContent.startsWith("{") && !rawContent.startsWith("[")) return emptyList()
    return runCatching {
        val root = articleContentJson.parseToJsonElement(rawContent)
        val operations = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is JsonObject -> root["ops"]?.jsonArray
            else -> null
        }
        operations
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .orEmpty()
    }.getOrDefault(emptyList())
}

private fun parseStructuredParagraph(paragraph: JsonObject): List<ArticleContentBlock> {
    return when (paragraph["para_type"]?.jsonPrimitive?.intOrNull) {
        2 -> extractImages(paragraph)
        3 -> extractLineImage(paragraph)
        4 -> extractQuote(paragraph)
        5 -> extractList(paragraph)
        // opus/detail uses 6 for link cards, while x/article/view type=3 also uses
        // 6 for legacy list rows carrying format.list_format + text.
        6 -> extractLinkCardText(paragraph)
            .ifEmpty { extractLegacyFormattedList(paragraph) }
            .ifEmpty { extractLegacyOrTextBlocks(paragraph) }
        7 -> extractCode(paragraph)
        else -> extractLegacyOrTextBlocks(paragraph)
    }
}

private fun extractLegacyOrTextBlocks(paragraph: JsonObject): List<ArticleContentBlock> {
    val blocks = mutableListOf<ArticleContentBlock>()
    extractInlineRich(paragraph["heading"]).takeIf { it.text.isNotBlank() }?.let {
        blocks += ArticleContentBlock.Heading(it.text, it.spans)
    }
    extractInlineRich(paragraph["text"]).takeIf { it.text.isNotBlank() }?.let { rich ->
        blocks += if (maxFontSize(paragraph["text"]) >= ARTICLE_HEADING_FONT_SIZE) {
            ArticleContentBlock.Heading(rich.text, rich.spans)
        } else {
            ArticleContentBlock.Paragraph(rich.text, rich.spans)
        }
    }
    blocks += extractImages(paragraph)
    blocks += extractLineImage(paragraph)
    return blocks
}

private fun extractQuote(paragraph: JsonObject): List<ArticleContentBlock> {
    val rich = extractInlineRich(paragraph["text"])
    if (rich.text.isBlank()) return emptyList()
    return listOf(ArticleContentBlock.Quote(rich.text, rich.spans))
}

private fun extractList(paragraph: JsonObject): List<ArticleContentBlock> {
    val listObject = paragraph["list"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return emptyList()
    val ordered = listObject["style"]?.jsonPrimitive?.intOrNull == 1
    val items = listObject["items"]
        ?.let { runCatching { it.jsonArray }.getOrNull() }
        .orEmpty()
        .mapNotNull { item ->
            val itemObject = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            extractNodesText(itemObject["nodes"]).takeIf { it.isNotBlank() }
        }
    if (items.isEmpty()) return emptyList()
    return listOf(ArticleContentBlock.ListBlock(ordered = ordered, items = items))
}

private fun extractLegacyFormattedList(paragraph: JsonObject): List<ArticleContentBlock> {
    val format = paragraph["format"]
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?: return emptyList()
    val listFormat = format["list_format"]
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?: return emptyList()
    val text = extractInlineText(paragraph["text"])
    if (text.isBlank()) return emptyList()

    val style = listFormat["style"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val ordered = style == "1" || style.equals("ordered", ignoreCase = true)
    return listOf(ArticleContentBlock.ListBlock(ordered = ordered, items = listOf(text)))
}

private fun extractCode(paragraph: JsonObject): List<ArticleContentBlock> {
    val codeObject = paragraph["code"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return emptyList()
    val content = decodeHtmlEntities(
        codeObject["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    ).trim()
    if (content.isBlank()) return emptyList()
    val language = codeObject["lang"]?.jsonPrimitive?.contentOrNull
        .orEmpty()
        .removePrefix("language-")
        .trim()
    return listOf(ArticleContentBlock.Code(language = language, content = content))
}

private fun extractLinkCardText(paragraph: JsonObject): List<ArticleContentBlock> {
    val cards = com.android.purebilibili.data.model.response.DynamicModulesFlexibleSerializer
        .extractParagraphLinkCards(paragraph)
    if (cards.isNotEmpty()) return cards.map { ArticleContentBlock.MediaCard(it) }
    val card = paragraph["link_card"]
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?.get("card")
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?: return emptyList()
    // 空商品组不生成占位商品或仅包含组编号的正文。
    if (card["type"]?.jsonPrimitive?.contentOrNull == "LINK_CARD_TYPE_GOODS") return emptyList()
    val nested = listOf("ugc", "common", "opus", "live", "music", "goods", "vote")
        .firstNotNullOfOrNull { key ->
            card[key]?.let { runCatching { it.jsonObject }.getOrNull() }
        }
    val title = nested?.get("title")?.jsonPrimitive?.contentOrNull
        ?: nested?.get("name")?.jsonPrimitive?.contentOrNull
        ?: card["oid"]?.jsonPrimitive?.contentOrNull
        ?: return emptyList()
    if (title.isBlank() || title == "undefined") return emptyList()
    return listOf(ArticleContentBlock.Paragraph(title.trim()))
}

private fun extractLineImage(paragraph: JsonObject): List<ArticleContentBlock> {
    val image = parseImageObject(
        paragraph["line"]
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.get("pic")
            ?.let { runCatching { it.jsonObject }.getOrNull() }
    ) ?: return emptyList()
    return listOf(image)
}

private fun extractInlineText(element: JsonElement?): String = extractNodesText(
    runCatching { element?.jsonObject?.get("nodes") }.getOrNull()
)

internal data class InlineRichText(val text: String, val spans: List<ArticleTextSpan>)

private val emptyInlineRich = InlineRichText("", emptyList())

/** 结构化段落的行内富文本：同时产出纯文本（兜底）与带样式的 span 列表。 */
private fun extractInlineRich(element: JsonElement?): InlineRichText {
    val nodes = runCatching { element?.jsonObject?.get("nodes")?.jsonArray }.getOrNull()
        ?: return emptyInlineRich
    val spans = mutableListOf<ArticleTextSpan>()
    val plain = StringBuilder()
    nodes.forEach { node ->
        val nodeObject = runCatching { node.jsonObject }.getOrNull() ?: return@forEach
        val richObject = runCatching { nodeObject["rich"]?.jsonObject }.getOrNull()
        val wordObject = runCatching { nodeObject["word"]?.jsonObject }.getOrNull()
        val text = wordObject?.get("words")?.jsonPrimitive?.contentOrNull
            ?: richObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: richObject?.get("orig_text")?.jsonPrimitive?.contentOrNull
            ?: richObject?.get("emoji")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: nodeObject["formula"]?.jsonObject?.get("latex_content")?.jsonPrimitive?.contentOrNull
            ?: return@forEach
        if (text.isEmpty()) return@forEach
        plain.append(text)
        val fontSize = wordObject?.get("font_size")?.jsonPrimitive?.intOrNull
            ?.takeIf { it in 8..64 }
        val color = wordObject?.get("color")?.jsonPrimitive?.contentOrNull
            ?.let(::parseArticleColor)
        val style = wordObject?.get("style")?.let { runCatching { it.jsonObject }.getOrNull() }
        fun styleFlag(key: String): Boolean =
            style?.get(key)?.jsonPrimitive?.contentOrNull == "true" ||
                style?.get(key)?.jsonPrimitive?.intOrNull == 1 ||
                style?.get(key)?.jsonPrimitive?.booleanOrNull == true
        val bold = styleFlag("bold")
        val italic = styleFlag("italic")
        val strike = styleFlag("strikethrough") || styleFlag("strike")
        val link = normalizeArticleLink(richObject?.get("jump_url")?.jsonPrimitive?.contentOrNull)
        spans += if (fontSize != null || color != null || bold || italic || strike || link != null) {
            ArticleTextSpan(
                text = text,
                fontSizeSp = fontSize,
                colorArgb = color,
                bold = bold,
                italic = italic,
                strikethrough = strike,
                link = link,
            )
        } else {
            ArticleTextSpan(text = text)
        }
    }
    val text = plain.toString().trim()
    if (text.isBlank()) return emptyInlineRich
    return InlineRichText(text, spans)
}

/** "#RRGGBB" / "#AARRGGBB" / rgb(r,g,b) → ARGB Long。 */
internal fun parseArticleColor(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val hex = when {
        value.startsWith("#") -> value.substring(1)
        value.startsWith("rgb(", ignoreCase = true) -> value.substringAfter("(")
            .substringBefore(")")
            .split(",", ";")
            .mapNotNull { it.trim().toIntOrNull() }
            .takeIf { it.size >= 3 }
            ?.joinToString("") { it.coerceIn(0, 255).toString(16).padStart(2, '0') }
            ?: return null
        else -> return null
    }
    return when (hex.length) {
        6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it }
        8 -> hex.toLongOrNull(16)
        else -> null
    }
}

private fun extractNodesText(nodesElement: JsonElement?): String {
    val nodes = runCatching { nodesElement?.jsonArray }.getOrNull() ?: return ""
    return buildString {
        nodes.forEach { node ->
            val nodeObject = runCatching { node.jsonObject }.getOrNull() ?: return@forEach
            val word = nodeObject["word"]
                ?.jsonObject
                ?.get("words")
                ?.jsonPrimitive
                ?.contentOrNull
            val richText = runCatching {
                val richObj = nodeObject["rich"]?.jsonObject
                richObj?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: richObj?.get("orig_text")?.jsonPrimitive?.contentOrNull
                    ?: richObj?.get("emoji")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val formula = nodeObject["formula"]
                ?.jsonObject
                ?.get("latex_content")
                ?.jsonPrimitive
                ?.contentOrNull
            append(word ?: richText ?: formula.orEmpty())
        }
    }.trim()
}

private fun maxFontSize(element: JsonElement?): Int {
    val nodes = runCatching { element?.jsonObject?.get("nodes")?.jsonArray }.getOrNull() ?: return 0
    return nodes.maxOfOrNull { node ->
        runCatching {
            node.jsonObject["word"]?.jsonObject?.get("font_size")?.jsonPrimitive?.intOrNull
        }.getOrNull() ?: 0
    } ?: 0
}

private fun extractImages(paragraph: JsonObject): List<ArticleContentBlock.Image> {
    val results = mutableListOf<ArticleContentBlock.Image>()
    collectPicObjects(paragraph["pic"]).forEach { pic ->
        parseImageObject(pic)?.let(results::add)
    }
    if (results.isNotEmpty()) return results
    collectPicObjects(paragraph["pics"]).forEach { pic ->
        parseImageObject(pic)?.let(results::add)
    }
    return results
}

private fun collectPicObjects(element: JsonElement?): List<JsonObject> {
    if (element == null) return emptyList()
    runCatching { element.jsonArray }.getOrNull()?.let { array ->
        return array.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
    }
    val obj = runCatching { element.jsonObject }.getOrNull() ?: return emptyList()
    val nested = obj["pics"]?.let { runCatching { it.jsonArray }.getOrNull() }
    if (nested != null) {
        return nested.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
    }
    return listOf(obj)
}

private fun decodeHtmlEntities(raw: String): String {
    return raw
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

private fun parseImageObject(image: JsonObject?): ArticleContentBlock.Image? {
    if (image == null) return null
    val rawUrl = image["url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    if (rawUrl.isBlank()) return null
    return ArticleContentBlock.Image(
        url = normalizeImageUrl(rawUrl),
        width = image["width"]?.jsonPrimitive?.intOrNull ?: 0,
        height = image["height"]?.jsonPrimitive?.intOrNull ?: 0
    )
}

private fun parseHtmlBlocks(htmlContent: String?): List<ArticleContentBlock> {
    if (htmlContent.isNullOrBlank()) return emptyList()
    if (htmlContent.trimStart().startsWith("{")) return emptyList()

    val blocks = mutableListOf<ArticleContentBlock>()
    val blockRegex = Regex("""(?is)<(h[1-6]|p|pre|blockquote|li|figure)\b[^>]*>(.*?)</\1>|<img\b[^>]*>""")
    blockRegex.findAll(htmlContent).forEach { match ->
        val tag = match.groupValues.getOrNull(1).orEmpty().lowercase()
        val content = if (tag.isBlank()) match.value else match.groupValues[2]
        when {
            tag.startsWith("h") -> cleanupHtmlText(content).takeIf { it.isNotBlank() }?.let {
                blocks += ArticleContentBlock.Heading(it, parseHtmlSpans(content))
            }

            tag == "p" -> blocks += parseHtmlInlineBlocks(content, kind = HtmlInlineKind.Paragraph)

            tag == "blockquote" -> blocks += parseHtmlInlineBlocks(content, kind = HtmlInlineKind.Quote)

            tag == "pre" -> decodeHtmlEntities(cleanupHtmlText(content)).takeIf { it.isNotBlank() }?.let {
                blocks += ArticleContentBlock.Code(language = "", content = it)
            }

            tag == "li" -> blocks += parseHtmlInlineBlocks(content, kind = HtmlInlineKind.ListItem)

            tag == "figure" -> blocks += parseHtmlInlineBlocks(content, kind = HtmlInlineKind.Paragraph)

            match.value.startsWith("<img", ignoreCase = true) -> {
                (parseArticleHtmlMediaCard(match.value) ?: parseHtmlImage(match.value))?.let { blocks += it }
            }
        }
    }
    return blocks
}

private enum class HtmlInlineKind {
    Paragraph,
    Quote,
    ListItem
}

private fun parseHtmlInlineBlocks(
    content: String,
    kind: HtmlInlineKind
): List<ArticleContentBlock> {
    val result = mutableListOf<ArticleContentBlock>()
    val imgRegex = Regex("""(?is)<img\b[^>]*>""")
    var lastIndex = 0
    imgRegex.findAll(content).forEach { match ->
        appendHtmlTextBlock(
            target = result,
            text = content.substring(lastIndex, match.range.first),
            kind = kind
        )
        (parseArticleHtmlMediaCard(match.value) ?: parseHtmlImage(match.value))?.let(result::add)
        lastIndex = match.range.last + 1
    }
    appendHtmlTextBlock(
        target = result,
        text = content.substring(lastIndex),
        kind = kind
    )
    return result
}

private fun appendHtmlTextBlock(
    target: MutableList<ArticleContentBlock>,
    text: String,
    kind: HtmlInlineKind
) {
    if (text.isBlank()) return
    val spans = parseHtmlSpans(text)
    val plainText = cleanupHtmlText(text)
    if (plainText.isBlank()) return
    target += when (kind) {
        HtmlInlineKind.Paragraph -> ArticleContentBlock.Paragraph(plainText, spans)
        HtmlInlineKind.Quote -> ArticleContentBlock.Quote(plainText, spans)
        HtmlInlineKind.ListItem -> ArticleContentBlock.ListBlock(ordered = false, items = listOf(plainText))
    }
}

private data class HtmlSpanStyle(
    val fontSizeSp: Int?,
    val colorArgb: Long?,
    val bold: Boolean,
    val italic: Boolean,
    val strikethrough: Boolean,
    val link: String? = null,
) {
    companion object {
        val Default = HtmlSpanStyle(null, null, false, false, false)
    }
}

private val htmlStyleTagRegex = Regex("""(?is)<(/?)([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>|[^<]+""")

/** 从 HTML 片段提取带样式的行内 span；<img> 已在上游拆出，这里不会再遇到。 */
internal fun parseHtmlSpans(raw: String): List<ArticleTextSpan> {
    val spans = mutableListOf<ArticleTextSpan>()
    val styleStack = ArrayDeque<HtmlSpanStyle>().apply { addLast(HtmlSpanStyle.Default) }
    val currentText = StringBuilder()

    fun currentStyle(): HtmlSpanStyle = styleStack.last()

    fun flush() {
        val text = currentText.toString()
        currentText.clear()
        if (text.isEmpty()) return
        val style = currentStyle()
        val styled = style != HtmlSpanStyle.Default
        spans += if (styled) {
            ArticleTextSpan(
                text = text,
                fontSizeSp = style.fontSizeSp,
                colorArgb = style.colorArgb,
                bold = style.bold,
                italic = style.italic,
                strikethrough = style.strikethrough,
                link = style.link,
            )
        } else {
            ArticleTextSpan(text = text)
        }
    }

    htmlStyleTagRegex.findAll(raw).forEach { match ->
        val token = match.value
        if (!token.startsWith("<")) {
            currentText.append(decodeHtmlEntities(token))
            return@forEach
        }
        val closing = match.groupValues[1] == "/"
        val tag = match.groupValues[2].lowercase()
        when (tag) {
            "b", "strong" -> {
                flush()
                if (closing) {
                    unwindHtmlStyle(styleStack) { it.bold }
                } else {
                    val top = styleStack.last()
                    styleStack.addLast(top.copy(bold = true))
                }
            }
            "i", "em" -> {
                flush()
                if (closing) {
                    unwindHtmlStyle(styleStack) { it.italic }
                } else {
                    val top = styleStack.last()
                    styleStack.addLast(top.copy(italic = true))
                }
            }
            "del", "s", "strike" -> {
                flush()
                if (closing) {
                    unwindHtmlStyle(styleStack) { it.strikethrough }
                } else {
                    val top = styleStack.last()
                    styleStack.addLast(top.copy(strikethrough = true))
                }
            }
            "span" -> {
                flush()
                if (closing) {
                    unwindHtmlStyle(styleStack) {
                        it.fontSizeSp != null || it.colorArgb != null
                    }
                } else {
                    val tagHtml = Regex("""(?is)<span\b[^>]*>""").find(token)?.value ?: token
                    val styleAttr = extractHtmlAttribute(tagHtml, "style").orEmpty()
                    val fontSize = Regex("""font-size\s*:\s*(\d+(?:\.\d+)?)\s*px""", RegexOption.IGNORE_CASE)
                        .find(styleAttr)?.groupValues?.getOrNull(1)
                        ?.toFloatOrNull()?.toInt()
                        ?.takeIf { it in 8..64 }
                    val color = Regex("""(?:^|;)\s*color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
                        .find(styleAttr)?.groupValues?.getOrNull(1)?.trim()
                        ?.let(::parseArticleColor)
                    if (fontSize != null || color != null) {
                        val top = styleStack.last()
                        styleStack.addLast(top.copy(fontSizeSp = fontSize, colorArgb = color))
                    }
                }
            }
            "a" -> {
                flush()
                if (closing) {
                    unwindHtmlStyle(styleStack) { it.link != null }
                } else {
                    normalizeArticleLink(extractHtmlAttribute(token, "href"))?.let { url ->
                        styleStack.addLast(styleStack.last().copy(link = url))
                    }
                }
            }
            "br" -> currentText.append('\n')
            else -> Unit
        }
    }
    flush()
    return spans
}

/** 关闭标签时只回退到引入对应样式的层级，容忍未闭合标签的混排。 */
private fun unwindHtmlStyle(
    stack: ArrayDeque<HtmlSpanStyle>,
    matches: (HtmlSpanStyle) -> Boolean
) {
    for (index in stack.size - 1 downTo 1) {
        if (matches(stack[index])) {
            while (stack.size > index) stack.removeLast()
            return
        }
    }
}

private fun parseOpsBlocks(ops: List<JsonObject>): List<ArticleContentBlock> {
    if (ops.isEmpty()) return emptyList()

    return buildList {
        val pendingSegments = mutableListOf<Pair<String, JsonObject?>>()
        // Quill 把 header/list/blockquote 挂在「\n」操作的 attributes 上，单独记录。
        var pendingLineAttributes: JsonObject? = null

        fun blockquoteActive(): Boolean = pendingLineAttributes?.get("blockquote")
            ?.jsonPrimitive?.contentOrNull
            ?.equals("true", ignoreCase = true) == true

        fun headerLevel(): Int? = pendingLineAttributes?.get("header")
            ?.jsonPrimitive?.intOrNull
            ?.takeIf { it in 1..6 }

        fun listStyle(): String? = pendingLineAttributes?.get("list")
            ?.jsonPrimitive?.contentOrNull

        fun flushText() {
            val text = pendingSegments.joinToString("") { it.first }.trim()
            val allSpans = pendingSegments.map { (segment, attrs) ->
                val style = parseOpsSpanStyle(attrs)
                ArticleTextSpan(
                    text = segment,
                    colorArgb = style?.first,
                    bold = style?.second == true,
                    italic = style?.third == true,
                    strikethrough = style?.fourth == true,
                    link = normalizeArticleLink(attrs?.get("link")?.jsonPrimitive?.contentOrNull),
                )
            }
            val spans = allSpans.takeIf { values -> values.any {
                it.colorArgb != null || it.bold || it.italic || it.strikethrough || it.link != null
            } }.orEmpty()
            pendingSegments.clear()
            if (text.isBlank()) {
                pendingLineAttributes = null
                return
            }

            val header = headerLevel()
            val listStyleValue = listStyle()
            val isQuote = blockquoteActive()
            pendingLineAttributes = null
            add(
                when {
                    header != null -> ArticleContentBlock.Heading(text, spans)
                    isQuote -> ArticleContentBlock.Quote(text, spans)
                    !listStyleValue.isNullOrBlank() -> ArticleContentBlock.ListBlock(
                        ordered = listStyleValue.equals("ordered", ignoreCase = true),
                        items = listOf(text)
                    )
                    else -> ArticleContentBlock.Paragraph(text, spans)
                }
            )
        }

        ops.forEach { op ->
            val attributes = op["attributes"]
                ?.let { runCatching { it.jsonObject }.getOrNull() }
            when (val insert = op["insert"]) {
                is JsonPrimitive -> {
                    val segments = insert.contentOrNull.orEmpty().split('\n')
                    segments.forEachIndexed { index, segment ->
                        if (segment.isNotEmpty()) {
                            pendingSegments += segment to attributes
                        }
                        if (index < segments.lastIndex) {
                            // 块级格式属于当前行的终止换行，必须先读取再清空。
                            pendingLineAttributes = attributes
                            flushText()
                        }
                    }
                }

                is JsonObject -> {
                    flushText()
                    (parseArticleOpsMediaCard(insert) ?: parseOpsImage(insert))?.let(::add)
                }

                else -> Unit
            }
        }
        flushText()
    }.mergeAdjacentListBlocks()
}

/** Quill 行内样式属性：返回 (color, bold, italic, strike) 四元组；无样式返回 null。 */
private fun parseOpsSpanStyle(attributes: JsonObject?): Quadruple<Long?, Boolean, Boolean, Boolean>? {
    attributes ?: return null
    fun flag(key: String): Boolean =
        attributes[key]?.jsonPrimitive?.booleanOrNull == true ||
            attributes[key]?.jsonPrimitive?.contentOrNull == "true"

    val bold = flag("bold")
    val italic = flag("italic")
    val strike = flag("strike")
    val color = attributes["color"]?.jsonPrimitive?.contentOrNull?.let(::parseArticleColor)
    if (!bold && !italic && !strike && color == null) return null
    return Quadruple(color, bold, italic, strike)
}

internal data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun parseOpsImage(insert: JsonObject): ArticleContentBlock.Image? {
    val directImage = insert["image"]
    if (directImage is JsonPrimitive) {
        val url = directImage.contentOrNull.orEmpty().trim()
        if (url.isNotBlank()) {
            return ArticleContentBlock.Image(url = normalizeImageUrl(url))
        }
    }

    val cardKeys = listOf(
        "native-image",
        "image-card",
        "cut-off",
        "article-card",
        "live-card",
        "goods-card",
        "video-card",
        "mall-card",
        "vote-card"
    )
    return cardKeys.firstNotNullOfOrNull { key ->
        parseImageObject(insert[key]?.let { runCatching { it.jsonObject }.getOrNull() })
    }
}

private fun List<ArticleContentBlock>.mergeAdjacentListBlocks(): List<ArticleContentBlock> {
    if (size < 2) return this
    return buildList {
        this@mergeAdjacentListBlocks.forEach { block ->
            val previous = lastOrNull() as? ArticleContentBlock.ListBlock
            if (block is ArticleContentBlock.ListBlock && previous?.ordered == block.ordered) {
                removeAt(lastIndex)
                add(previous.copy(items = previous.items + block.items))
            } else {
                add(block)
            }
        }
    }
}

private fun parseHtmlImage(rawBlock: String): ArticleContentBlock.Image? {
    val imgTag = Regex("""(?is)<img\b[^>]*>""").find(rawBlock)?.value ?: rawBlock
    val url = extractHtmlAttribute(imgTag, "data-src")
        ?: extractHtmlAttribute(imgTag, "src")
        ?: return null
    return ArticleContentBlock.Image(
        url = normalizeImageUrl(url),
        width = extractHtmlAttribute(imgTag, "width")?.toIntOrNull() ?: 0,
        height = extractHtmlAttribute(imgTag, "height")?.toIntOrNull() ?: 0
    )
}

private fun extractHtmlAttribute(tag: String, name: String): String? {
    val regex = Regex("""(?is)\b$name\s*=\s*["']([^"']+)["']""")
    return regex.find(tag)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun cleanupHtmlText(raw: String): String {
    return raw
        .replace(Regex("""(?is)<br\s*/?>"""), "\n")
        .replace(Regex("""(?is)<[^>]+>"""), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}

private fun normalizeImageUrl(rawUrl: String): String {
    return when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
        else -> rawUrl
    }
}

internal fun normalizeArticleLink(raw: String?): String? {
    val value = raw?.let(::decodeHtmlEntities)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://www.bilibili.com$value"
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
        else -> null
    }
}
