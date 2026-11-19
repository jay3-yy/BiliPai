package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.ReplyItem
import java.text.Normalizer
import java.util.Locale

private const val MIN_TEXT_LENGTH = 24
private const val MIN_AUTHORS = 3
private const val WINDOW_SECONDS = 15 * 60L
private const val MAX_NEAR_VARIANTS = 24

/** 只比较当前评论区的发言，评分按正文缓存，判定随分页和删除重新聚合。 */
class RepeatedReplyDetector {
    private data class Text(val normalized: String) {
        val start = normalized.take(12)
        val end = normalized.takeLast(12)
        val grams by lazy(LazyThreadSafetyMode.NONE) { normalized.windowed(3).toSet() }

        fun isNear(other: Text): Boolean {
            if (minOf(normalized.length, other.normalized.length) * 100 <
                maxOf(normalized.length, other.normalized.length) * 82
            ) return false
            val common = grams.count { it in other.grams }
            return common * 100 >= (grams.size + other.grams.size - common) * 82
        }
    }

    private data class CachedText(val raw: String, val text: Text?)
    private data class Sample(val reply: ReplyItem, val mid: Long, val text: Text)
    private val cache = mutableMapOf<Long, CachedText>()

    internal fun clear() = cache.clear()

    internal fun detect(replies: Sequence<ReplyItem>, exemptAuthors: Set<Long>): Set<Long> {
        val seen = hashSetOf<Long>()
        val samples = mutableListOf<Sample>()
        fun visit(reply: ReplyItem) {
            val mid = reply.authorMid()
            if (reply.rpid > 0L && mid > 0L && !reply.invisible && reply.ctime > 0L &&
                mid !in exemptAuthors && seen.add(reply.rpid)
            ) {
                val raw = reply.content.message
                val cached = cache[reply.rpid]?.takeIf { it.raw == raw } ?: run {
                    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                        .lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
                    val text = normalized.takeIf { it.length >= MIN_TEXT_LENGTH && it.toSet().size >= 10 }?.let(::Text)
                    CachedText(raw, text).also { cache[reply.rpid] = it }
                }
                cached.text?.let { samples += Sample(reply, mid, it) }
            }
            reply.replies.orEmpty().forEach(::visit)
        }
        replies.forEach(::visit)
        cache.keys.retainAll(seen)
        val repeated = hashSetOf<Long>()
        for (subject in samples.groupBy { it.reply.replyType to it.reply.oid }.values) {
            val byText = subject.groupBy { it.text }
            val heads = byText.keys.groupBy(Text::start)
            val tails = byText.keys.groupBy(Text::end)
            for ((text, exact) in byText) {
                val variants = (heads[text.start].orEmpty().take(MAX_NEAR_VARIANTS) +
                    tails[text.end].orEmpty().take(MAX_NEAR_VARIANTS)).distinct()
                val matches = exact + variants.filter { it != text && text.isNear(it) }.flatMap(byText::getValue)
                if (matches.size >= MIN_AUTHORS) {
                    markWindows(text, matches.sortedBy { it.reply.ctime }, repeated)
                }
            }
        }
        return repeated
    }

    private fun markWindows(text: Text, samples: List<Sample>, repeated: MutableSet<Long>) {
        val authors = mutableMapOf<Long, Int>()
        var end = 0
        var markedEnd = 0
        for ((start, sample) in samples.withIndex()) {
            while (end < samples.size && samples[end].reply.ctime - sample.reply.ctime <= WINDOW_SECONDS) {
                val mid = samples[end++].mid
                authors[mid] = (authors[mid] ?: 0) + 1
            }
            if (authors.size >= MIN_AUTHORS) {
                for (index in maxOf(start, markedEnd) until end) {
                    val match = samples[index]
                    if (match.text == text) repeated.add(match.reply.rpid)
                }
                markedEnd = end
            }
            val count = authors.getValue(sample.mid)
            if (count == 1) authors.remove(sample.mid) else authors[sample.mid] = count - 1
        }
    }
}
