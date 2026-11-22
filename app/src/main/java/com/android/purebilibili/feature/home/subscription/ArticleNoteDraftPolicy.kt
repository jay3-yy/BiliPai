package com.android.purebilibili.feature.home.subscription

import com.android.purebilibili.core.plugin.feed.FeedBlock
import com.android.purebilibili.core.plugin.feed.FeedInline
import com.android.purebilibili.feature.video.note.VideoNoteBlock
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument

/** 摘录/摘要成稿策略：与视频笔记共用同一文档模型，仅不含时间戳。 */

internal fun buildArticleExcerptDocument(
    articleTitle: String,
    excerpts: List<String>
): VideoNoteEditorDocument {
    val blocks = excerpts
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { excerpt -> VideoNoteBlock.Quote(text = excerpt) }
    if (blocks.isEmpty()) {
        return VideoNoteEditorDocument(
            title = articleTitle,
            blocks = listOf(VideoNoteBlock.Text(""))
        )
    }
    return VideoNoteEditorDocument(
        title = articleTitle,
        blocks = blocks + listOf(VideoNoteBlock.Text("\n"))
    )
}

/** 提取式摘要草稿：无外部 AI 依赖，取开头若干完整句。 */
internal fun buildArticleSummaryDraft(
    articleTitle: String,
    articlePlainText: String
): VideoNoteEditorDocument? {
    val sentences = extractArticleSummarySentences(articlePlainText)
    if (sentences.isEmpty()) return null
    return VideoNoteEditorDocument(
        title = articleTitle,
        blocks = listOf(
            VideoNoteBlock.Text("摘要\n", bold = true),
            VideoNoteBlock.Text(sentences.joinToString(separator = "") { it + "\n" }),
        )
    )
}

private val ARTICLE_SUMMARY_WHITESPACE_REGEX = Regex("\\s+")
private val ARTICLE_SUMMARY_SENTENCE_SPLIT_REGEX = Regex("(?<=[。！？!?；;])")

internal fun extractArticleSummarySentences(
    plainText: String,
    maxChars: Int = 160
): List<String> {
    val normalized = plainText.replace(ARTICLE_SUMMARY_WHITESPACE_REGEX, " ").trim()
    if (normalized.isBlank()) return emptyList()
    val sentences = normalized.split(ARTICLE_SUMMARY_SENTENCE_SPLIT_REGEX)
    val picked = mutableListOf<String>()
    var length = 0
    sentences.forEach { sentence ->
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return@forEach
        if (length >= maxChars) return@forEach
        picked += trimmed
        length += trimmed.length
    }
    return picked.takeIf { it.isNotEmpty() && length >= 20 } ?: emptyList()
}

/** 摘录模式下可被选中的块：标题/段落/引用/代码/列表，返回纯文本。 */
internal fun feedBlockExcerptText(block: FeedBlock): String? {
    return when (block) {
        is FeedBlock.Heading -> block.inlines.joinToString(separator = "") { it.displayText() }.takeIf { it.isNotBlank() }
        is FeedBlock.Paragraph -> block.inlines.joinToString(separator = "") { it.displayText() }.takeIf { it.isNotBlank() }
        is FeedBlock.Quote -> block.inlines.joinToString(separator = "") { it.displayText() }.takeIf { it.isNotBlank() }
        is FeedBlock.Code -> block.text.takeIf { it.isNotBlank() }
        is FeedBlock.BulletList -> block.items
            .map { items -> items.joinToString(separator = "") { it.displayText() } }
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "• $it" }
        is FeedBlock.NumberedList -> block.items
            .mapIndexed { index, items -> "${index + 1}. " + items.joinToString(separator = "") { it.displayText() } }
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n")
        else -> null
    }
}

private fun FeedInline.displayText(): String = when (this) {
    is FeedInline.Text -> text
    is FeedInline.Link -> text
}
