package com.android.purebilibili.core.plugin.feed

import net.dankito.readability4j.Readability4J
import net.dankito.readability4j.model.ReadabilityOptions
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val readMoreText = Regex(
    """(查看全文|阅读全文|阅读更多|继续阅读|Read more|Continue reading).*""",
    RegexOption.IGNORE_CASE,
)

fun cleanFeedSummary(text: String): String {
    return text
        .replace(Regex("<[^>]+>"), " ")
        .replace("\uFFFD", "")
        .replace("\uFFFC", "")
        .replace(readMoreText, "")
        .replace(Regex("\\s*[.。…]{2,}\\s*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun feedBodyNeedsRemoteFetch(item: ParsedFeedItem): Boolean {
    if (!isHttpFeedUrl(item.link)) return false
    val htmlText = feedPlainText(item.htmlContent)
    val summary = cleanFeedSummary(item.summary)
    val cue = Regex("""(查看全文|阅读全文|阅读更多|继续阅读|read more|continue reading|全文链接)""", RegexOption.IGNORE_CASE)
    if (cue.containsMatchIn(item.htmlContent) || cue.containsMatchIn(item.summary)) return true
    if (item.htmlContent.isBlank()) return true
    if (htmlText.length >= 180 && htmlText.length > summary.length + 80) return false
    return htmlText.trimEnd().endsWith("…") || htmlText.trimEnd().endsWith("...") ||
        (htmlText.length < 120 && summary.length > htmlText.length + 80)
}

/**
 * Adapt Instant View's extract-then-render flow to the existing native RSS reader.
 * Readability4J runs locally on downloaded HTML: no remote parsing service, WebView,
 * Telegram page model, or execution of website scripts is involved.
 */
fun extractArticleBody(pageHtml: String, baseUrl: String = ""): String? =
    extractReadableArticle(pageHtml, baseUrl)?.html

data class ReadableArticle internal constructor(
    val url: String,
    val title: String,
    val author: String,
    private val content: Element,
) {
    val html: String get() = content.html()

    internal fun readingBlocks(): List<FeedBlock> = parseFeedHtml(content, url)
}

fun extractReadableArticle(pageHtml: String, baseUrl: String): ReadableArticle? {
    if (pageHtml.isBlank() || pageHtml.length > 2 * 1024 * 1024) return null
    val document = Jsoup.parse(pageHtml, baseUrl)
    if (document.getAllElements().size > 20_000) return null
    document.select("script, style, noscript, nav, header, footer, aside, form, " +
        "[hidden], [aria-hidden=true], [role=navigation], [role=banner], [role=contentinfo]").remove()
    document.getAllElements().filter { element ->
        val style = element.attr("style").replace(articleWhitespace, "").lowercase()
        style.contains("display:none") || style.contains("visibility:hidden") ||
            articleNoise.containsMatchIn(element.id() + " " + element.className())
    }.forEach { it.remove() }

    // Resolve before Readability: its legacy URL resolver drops explicit ports and
    // does not consistently handle ../. The result is also cached independently of the page.
    document.select("a[href]").forEach { link ->
        resolveFeedUrl(link.attr("href"), document.baseUri())?.let { link.attr("href", it) }
            ?: link.removeAttr("href")
    }
    document.select("img").forEach { image ->
        val source = sequenceOf("data-src", "data-original", "data-lazy-src", "src")
            .map { image.attr(it) }
            .mapNotNull { resolveFeedUrl(it, document.baseUri()) }
            .firstOrNull()
            ?: image.attr("srcset").substringBefore(',').trim().substringBefore(' ')
                .let { resolveFeedUrl(it, document.baseUri()) }
        if (source != null) image.attr("src", source)
        listOf("data-src", "data-original", "data-lazy-src", "srcset").forEach { image.removeAttr(it) }
    }

    // Run against a clone; Readability mutates its DOM and the original is needed
    // for short Chinese articles and known site containers it may discard.
    val readable = runCatching {
        Readability4J(
            document.baseUri(), document.clone(),
            ReadabilityOptions(maxElemsToParse = 20_000, wordThreshold = 80),
        ).parse()
    }.getOrNull()
    val readableContent = readable?.articleContent
    // Retain known containers for short posts when Readability discards them.
    val content = if (readableContent != null && isReadableArticle(readableContent)) {
        readableContent
    } else {
        document.select(articleContainers).firstOrNull { isReadableArticle(it, minimumText = 20) }
            ?: document.body().takeIf { isReadableArticle(it, minimumText = 30, minimumParagraphs = 2) }
    } ?: return null

    return ReadableArticle(
        url = baseUrl,
        title = readable?.title?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: document.title(),
        author = readable?.byline.orEmpty(),
        content = content,
    )
}

private val articleWhitespace = Regex("\\s+")

private val articleNoise = Regex(
    """(?:^|[\s_-])(comment(?:s)?|related|recommend(?:ations)?|sidebar|share|social|advert(?:isement)?|ads|cookie-banner)(?:$|[\s_-])""",
    RegexOption.IGNORE_CASE,
)

private const val articleContainers = ".article__main__content, .article-body, .entry-content, " +
    ".post-content, .post-body, .article-content, #js_content, .rich_media_content, " +
    ".markdown-body, [itemprop=articleBody], .story-body, .post__content, .content-body, article, main"

private fun isReadableArticle(
    element: Element,
    minimumText: Int = 40,
    minimumParagraphs: Int = 1,
): Boolean {
    val textLength = element.text().length
    if (textLength <= minimumText) return false
    val linkLength = element.select("a").sumOf { it.text().length }
    if (linkLength.toDouble() / textLength > 0.5) return false
    return element.select("p, pre, blockquote").count { it.text().isNotBlank() } >= minimumParagraphs
}
