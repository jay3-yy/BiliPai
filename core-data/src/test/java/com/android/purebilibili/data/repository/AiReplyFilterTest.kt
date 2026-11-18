package com.android.purebilibili.data.repository

import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyLevelInfo
import com.android.purebilibili.data.model.response.ReplyMember
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AiReplyFilterTest {
    private val enabled = ContentFilterSnapshot(keywordFilteringEnabled = true, hideAiReplies = true)

    @Test
    fun `account and comment evidence hide a bot while ordinary AI discussion survives`() {
        val bot = reply(1, 11, "小林的大肥鱼", "本鲸看完了（甩甩尾巴）@小林 这期可以看看")
        val human = reply(2, 22, "鲸鱼爱好者", "请问这个 DeepSeek 模型为什么老是自称本鲸，还经常写（甩甩尾巴）？")
        val filter = ReplyContentFilter(enabled, sequenceOf(bot, human), currentMid = 0)
        assertEquals(listOf(human), filter.filter(listOf(bot, human)))
    }

    @Test
    fun `a callout is attached to the addressed author and requires corroborating evidence`() {
        val root = reply(1, 11, "其他观众", "这期不错")
        val candidate = reply(2, 22, "阿林的肥鱼", "@阿林 我来吃白饭了", root = 1)
        val bystander = reply(3, 33, "大肥鱼粉丝", "你们都在聊什么", root = 1)
        val accusation = reply(4, 44, "另一位观众", "回复 @阿林的肥鱼：你是 AI 吧", root = 1)
        val otherAccusation = accusation.copy(content = ReplyContent("回复 @大肥鱼粉丝：你是 AI 吧"))
        val nicknameOnly = accusation.copy(content = ReplyContent("@阿林的肥鱼 你有看过这期吗"))
        val replies = listOf(root, candidate, bystander)
        assertEquals(emptySet<Long>(), AiReplyDetector().detectAuthors(replies.asSequence() + otherAccusation, emptySet()))
        assertEquals(emptySet<Long>(), AiReplyDetector().detectAuthors(replies.asSequence() + nicknameOnly, emptySet()))
        assertEquals(setOf(22L), AiReplyDetector().detectAuthors(replies.asSequence() + accusation, emptySet()))
    }

    @Test
    fun `signature from REST supplements the same mobile author and permits detection`() {
        val mobile = reply(1, 11, "游客", "刚看完这一期")
        val rest = Json.decodeFromString<ReplyItem>(
            """{"rpid":7,"mid":11,"member":{"mid":"11","sign":"此账号由 DeepSeek 代管"}}"""
        )
        val data = ReplyData(replies = listOf(mobile), grpcNextOffset = "next")
        val merged = mergeCommentReplyMetadata(data, listOf(rest))
        assertEquals("next", merged.grpcNextOffset)
        val replies = requireNotNull(merged.replies)
        assertEquals(emptyList<ReplyItem>(), ReplyContentFilter(enabled, replies.asSequence(), currentMid = 0).filter(replies))
    }

    @Test
    fun `opt out and allowlisting restore raw replies without overriding the user blacklist`() {
        val bot = reply(1, 11, "大肥鱼", "本鲸看完了（甩甩尾巴）")
        val replies = listOf(bot)
        fun display(rules: ContentFilterSnapshot, upMid: Long = 0L, currentMid: Long = 0L) =
            ReplyContentFilter(rules, replies.asSequence(), upMid, currentMid).filter(replies)

        assertEquals(emptyList<ReplyItem>(), display(enabled))
        assertEquals(replies, display(enabled.copy(hideAiReplies = false)))
        assertEquals(replies, display(enabled.copy(keywordFilteringEnabled = false)))
        assertEquals(replies, display(enabled.copy(aiReplyAllowedMids = setOf(11L))))
        assertEquals(replies, display(enabled, upMid = 11L))
        assertEquals(replies, display(enabled, currentMid = 11L))
        assertEquals(emptyList<ReplyItem>(), display(enabled.copy(aiReplyAllowedMids = setOf(11L), blockedMids = setOf(11L))))
    }

    @Test
    fun `thread context remains usable when other replies from the same bot are hidden`() {
        val bot = reply(1, 11, "大肥鱼", "本鲸看完了（甩甩尾巴）")
        val child = reply(2, 11, "大肥鱼", "吃完了", root = 1)
        val other = reply(3, 33, "观众", "说说怎么回事", root = 1)
        val root = bot.copy(replies = listOf(child, other), rcount = 8)
        val filter = ReplyContentFilter(enabled, sequenceOf(root), currentMid = 0)
        assertEquals(listOf(other), filter.filter(listOf(child, other)))
        assertEquals(root.copy(replies = listOf(other)), filter.filter(root, keepThreadContext = true))
    }

    @Test
    fun `a later page changes the verdict and an edited comment invalidates its old evidence`() {
        val detector = AiReplyDetector()
        val root = reply(1, 11, "其他观众", "这期不错")
        val candidate = reply(2, 22, "阿林的肥鱼", "@阿林 我来吃白饭了", root = 1)
        val accusation = reply(3, 33, "另一位观众", "回复 @阿林的肥鱼：你是 AI 吧", root = 1)
        assertEquals(emptySet<Long>(), detector.detectAuthors(sequenceOf(root, candidate), emptySet()))
        assertEquals(setOf(22L), detector.detectAuthors(sequenceOf(root, candidate.copy(like = 9), accusation), emptySet()))
        val edited = candidate.copy(content = ReplyContent("这个视频的原声很好听"))
        assertEquals(emptySet<Long>(), detector.detectAuthors(sequenceOf(root, edited, accusation), emptySet()))
    }

    @Test
    fun `fold mode keeps the original reply and thread, and allowlisting clears the fold`() {
        val regular = reply(2, 22, "观众", "有人知道出处吗", root = 1)
        val bot = reply(1, 11, "大肥鱼", "本鲸看完了（甩甩尾巴）").copy(replies = listOf(regular))
        val detector = AiReplyDetector()
        val filter = ReplyContentFilter(enabled, sequenceOf(bot), currentMid = 0, foldDetectedReplies = true, detector = detector)
        val folded = filter.filter(listOf(bot)).single()
        assertEquals(bot.copy(aiBotFolded = true), folded)

        val allowed = enabled.copy(aiReplyAllowedMids = setOf(11L))
        val restored = ReplyContentFilter(allowed, sequenceOf(folded), currentMid = 0, foldDetectedReplies = true, detector = detector)
        assertEquals(bot, restored.filter(folded))
    }

    private fun reply(rpid: Long, uid: Long, name: String, text: String, root: Long = 0L) = ReplyItem(
        rpid = rpid,
        root = root,
        mid = uid,
        member = ReplyMember(mid = uid.toString(), uname = name, levelInfo = ReplyLevelInfo(currentLevel = 2)),
        content = ReplyContent(text),
    )
}
