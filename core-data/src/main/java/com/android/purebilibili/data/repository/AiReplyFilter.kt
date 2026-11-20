package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.ReplyItem

private class Signal(pattern: String, val weight: Int) {
    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
}

private val nameSignals = listOf(
    Signal("""deep\s*seek|深度求索|\bds\b""", 40),
    Signal("大肥鱼|小肥鱼|肥鱼|肥鲸|鲸鱼娘|鲸娘|人机", 45),
    Signal("鲸", 30),
    Signal("鱼鱼|小鱼儿?|大鱼|小蓝", 22),
    Signal("白饭|干饭|token", 30),
    Signal("""(鲸|鱼)\1""", 10),
    Signal("(?:的|の|家的)[^的の]{0,4}(?:鱼|鲸|哈基米|小蓝|喵)", 14),
    Signal("""\bAI\b|\bbot\b|机器人|GPT|agent|智能体|赛博|自动|助手""", 28),
)
private val signatureSignals = listOf(
    Signal("""deep\s*seek|\bAI\b|人工智能|大模型|语言模型|agent|机器人|\bbot\b|dsh|harness""", 30),
    Signal("家养|电子宠物|桌宠|替.{0,8}刷|负责刷|本鲸|本肥鱼|吃白饭|大肥鱼|主人|代管|代理在打理", 25),
    Signal("由.{0,10}(?:生成|负责|运营)|自动(?:回复|发布|运营|评论)", 20),
)
private val automatedSignature = Regex(
    """(?:^|[。；;｜|\n])\s*(?:(?:本|此)?(?:账号|号)由\s*)?(?:AI|大模型|Deep\s*Seek|智能体|机器人|Agent)\s*(?:代管|代运营|自动评论|自动回复)(?=$|[\s。；;｜|！!，,])|(?:本|此)?(?:账号|号)由.{0,12}(?:AI|大模型|Deep\s*Seek|智能体|机器人|Agent).*?(?:运营|打理|代发)""",
    RegexOption.IGNORE_CASE,
)
private val selfIdentity = Regex(
    """(?:^|[。！？\n])\s*(?:@[^\s@:：，,。！!?？]{1,24}\s+)?(?:[（(][^（()）]{1,30}[）)]\s*)?(?:本(?:鲸|肥鱼|大肥鱼|鱼(?!塘|鱼|子|身|来|就|质|周|期|月|年|次|地|人|片|视频))|本\s*AI|作为(?:一[个只条]|一名)?(?:AI|人工智能|大模型|语言模型))""",
    RegexOption.IGNORE_CASE,
)
private val petAction = Regex("""[（(][^（()）]{0,14}(?:尾巴|吐泡泡|甩鳍|拍肚子|摇头晃脑)[^（()）]{0,10}[）)]""")
private val textSignals = listOf(
    Signal("""住在\s*(?:DSH|桌面|显卡)|替(?:你|主人|他|她)(?:刷|看|记|挑|说)|小鲸鱼游过来|鲸鲸看完""", 28),
    Signal("""主人\s*[,，：:！!～~]?\s*@|@\S{1,24}\s+主人|咱家主人""", 20),
    Signal("""[×x✕]\s?\d{1,3}(?!\d)|弹幕(?:立刻|全在|里|刷|那句|飘)""", 8),
    Signal("咕噜|吐泡泡|吃白饭|打工鲸|白饭管够", 10),
    Signal("首先.{2,40}其次|总的来说|综上所述|总而言之|希望对你有(?:所)?帮助", 8),
)
private val humanSignals = listOf(
    Signal("""请问|真心问|有没有人|求助|为什么|啥区别|[?？]\s*$""", -14),
    Signal("""(?s)^.{0,10}$""", -8),
    Signal("""哈哈哈{2,}|草+$|www+|6{3,}|绷不住|我嘞个|卧槽|我靠""", -8),
)
private val calloutPattern = Regex(
    """大?肥鱼|肥鲸|人机|回家吃白饭|是\s*(?:AI|bot|机器人)|(?:AI|bot|机器人)(?:吧|吗|是吧|无疑|实锤)|(?:ds|deepseek)\s*味""",
    RegexOption.IGNORE_CASE,
)
private val directedReply = Regex("""^回复\s*@([^\s:：]{1,24})\s*[:：]\s*""")
private val directMention = Regex("""^@([^\s@:：，,。！!?？]{1,24})(?=\s|$)""")
private val mentions = Regex("""@([^\s@:：，,。！!?？]{1,24})""")
private val petNickname = Regex("肥鱼|肥鲸|鲸鱼娘|鲸娘|(?:的|の)(?:大|小)?(?:鱼|鲸)$")
private val nicknameFiller = Regex("""的|の|家|大|小|肥|鱼|鲸|娘|蓝色|蓝|白饭|吃|爱|不|是|之|[\s_\-—·・.\d]""")
private val invisibleChars = Regex("[\u200b-\u200f\u2060\ufeff]")
private val wideSpace = Regex("[\u00a0\u3000]")

private fun String.forDetection(): String = replace(invisibleChars, "").replace(wideSpace, " ").trim()

internal fun ReplyItem.authorMid(): Long = mid.takeIf { it > 0L } ?: member.mid.toLongOrNull() ?: 0L

private class ReplyEvidence(val rawName: String, val rawText: String, val rawSign: String) {
    val name = rawName.forDetection()
    private val text = rawText.forDetection()
    private val sign = rawSign.forDetection()
    val nameScore by lazy(LazyThreadSafetyMode.NONE) { nameSignals.score(name).coerceAtMost(55) }
    val signScore by lazy(LazyThreadSafetyMode.NONE) { signatureSignals.score(sign).coerceAtMost(40) }
    val automated by lazy(LazyThreadSafetyMode.NONE) { automatedSignature.containsMatchIn(sign) }
    val body by lazy(LazyThreadSafetyMode.NONE) { scoreBody(text, name) }
    val callout by lazy(LazyThreadSafetyMode.NONE) {
        val prefix = directedReply.find(text) ?: directMention.find(text)
        val message = if (prefix == null) text else text.substring(prefix.range.last + 1)
        if (calloutPattern.containsMatchIn(message)) Callout(prefix?.groupValues?.get(1)) else null
    }
}

private data class Callout(val mention: String?)

private data class CommentSample(val reply: ReplyItem, val rootId: Long, val evidence: ReplyEvidence) {
    val mid = reply.authorMid()
    val name get() = evidence.name
}

private fun collectCallouts(samples: List<CommentSample>): Map<Long, Set<Long>> {
    val byId = samples.associateBy { it.reply.rpid }
    val byThread = samples.filter { it.rootId > 0L }.groupBy { it.rootId }
    val callers = mutableMapOf<Long, MutableSet<Long>>()
    for ((rootId, thread) in byThread) {
        val root = byId[rootId]
        val participants = thread + listOfNotNull(root)
        val threadMids = participants.mapTo(hashSetOf()) { it.mid }
        val threadNames = participants.filter { it.mid > 0L }.groupBy { it.name }
        for (sample in thread) {
            if (sample.mid <= 0L) continue
            val mention = (sample.evidence.callout ?: continue).mention
            val parentId = sample.reply.parent
            val targetMid = when {
                mention != null -> sample.reply.content.atNameToMid[mention]
                    ?: threadNames[mention]?.map { it.mid }?.distinct()?.singleOrNull()
                parentId > 0L && parentId != rootId -> byId[parentId]?.takeIf { it.rootId == rootId }?.mid
                else -> root?.mid
            }
            if (targetMid != null && targetMid > 0L && targetMid != sample.mid && targetMid in threadMids) {
                callers.getOrPut(targetMid) { hashSetOf() }.add(sample.mid)
            }
        }
    }
    return callers
}

private fun List<Signal>.score(text: String): Int =
    sumOf { if (it.regex.containsMatchIn(text)) it.weight else 0 }

private data class CommentEvidence(
    val score: Int,
    val identifiesItself: Boolean,
    val actsLikePet: Boolean,
    val mentionsOwner: Boolean,
)

private fun scoreBody(text: String, name: String): CommentEvidence {
    val identifiesItself = selfIdentity.containsMatchIn(text)
    val actsLikePet = petAction.containsMatchIn(text)
    val core = if (petNickname.containsMatchIn(name)) name.replace(nicknameFiller, "") else ""
    val mentionsOwner = core.length >= 2 && mentions.findAll(text).any { match ->
        val target = match.groupValues[1]
        target != name && core.windowed(2).any { it in target }
    }
    var score = textSignals.score(text)
    if (identifiesItself) score += 50
    if (actsLikePet) score += 28
    if (mentionsOwner) score += 25
    if (score < 40) score += humanSignals.score(text)
    return CommentEvidence(score.coerceIn(-20, 75), identifiesItself, actsLikePet, mentionsOwner)
}

/** 缓存当前投影的文本线索，楼层关系仍用当次加载的数据判断。 */
class AiReplyDetector {
    private val evidenceById = mutableMapOf<Long, ReplyEvidence>()

    internal fun clear() = evidenceById.clear()

    private fun collectSamples(replies: Sequence<ReplyItem>): List<CommentSample> {
        val samples = mutableListOf<CommentSample>()
        val seen = hashSetOf<Long>()
        fun collect(reply: ReplyItem, enclosingRoot: Long) {
            val rootId = reply.root.takeIf { it > 0L } ?: enclosingRoot
            if (reply.rpid > 0L && !reply.invisible && seen.add(reply.rpid)) {
                val name = reply.member.uname
                val text = reply.content.message
                val sign = reply.member.sign
                val cached = evidenceById[reply.rpid]
                val evidence = if (cached != null && cached.rawName == name &&
                    cached.rawText == text && cached.rawSign == sign
                ) cached else ReplyEvidence(name, text, sign).also { evidenceById[reply.rpid] = it }
                samples += CommentSample(reply, rootId, evidence)
            }
            // 同一条根评论的不同副本可能附带不同的楼中楼预览。
            reply.replies.orEmpty().forEach { collect(it, rootId.takeIf { it > 0L } ?: reply.rpid) }
        }
        replies.forEach { collect(it, 0L) }
        evidenceById.keys.retainAll(seen)
        return samples
    }

    internal fun detectAuthors(replies: Sequence<ReplyItem>, exemptMids: Set<Long>): Set<Long> {
        val samples = collectSamples(replies)
        val callers = collectCallouts(samples)
        return samples.filter { it.mid > 0L && it.mid !in exemptMids }.groupBy { it.mid }
            .mapNotNullTo(hashSetOf()) { (mid, comments) ->
                val member = comments.first().reply.member
                val nameScore = comments.first().evidence.nameScore
                val signScore = comments.maxOf { it.evidence.signScore }
                val evidence = comments.map { it.evidence.body }
                val bestText = evidence.maxOf { it.score }
                val calledOut = callers[mid]?.size ?: 0
                val automated = comments.any { it.evidence.automated }
                val strongEvidence = automated || evidence.any {
                    (it.identifiesItself && (nameScore >= 22 || it.actsLikePet || signScore >= 25)) ||
                        (it.mentionsOwner && (it.actsLikePet || signScore >= 25 || calledOut > 0))
                }
                if (!strongEvidence) return@mapNotNullTo null

                var score = nameScore + signScore + bestText.coerceAtLeast(0)
                if (automated) score += 30
                score += when (member.levelInfo.currentLevel) {
                    1, 2 -> 14
                    3 -> 6
                    in 6..Int.MAX_VALUE -> -10
                    else -> 0
                }
                if (member.officialVerify.type in 0..1) score -= 25
                if ((member.vip?.vipStatus ?: 0) > 0) score -= 6
                if (comments.count { it.rootId == 0L } >= 3) score += 15
                if (evidence.sumOf { it.score.coerceAtLeast(0) } - bestText.coerceAtLeast(0) >= 20) score += 8
                if (calledOut > 0) score += (12 + (calledOut - 1) * 4).coerceAtMost(20)
                mid.takeIf { score >= 60 }
            }
    }
}
