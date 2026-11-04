package com.android.purebilibili.feature.video.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentTranslationPolicyTest {
    private fun show(text: String, switch: Int? = null, oid: Long = 1, rpid: Long = 2) =
        shouldOfferCommentTranslation(switch, text, oid, rpid)

    @Test
    fun missingSwitchOffersTranslationForForeignText() {
        assertTrue(show("This is a great video!"))
        assertTrue(show("こんにちは！", switch = 0))
        assertTrue(show("Прекрасное видео"))
        assertTrue(show("中文 mixed with English"))
    }

    @Test
    fun missingSwitchIgnoresChineseUrlsMentionsAndEmotes() {
        assertFalse(show("这个视频真好"))
        assertFalse(show("谢谢 UP 主"))
        assertFalse(show("看这里 https://example.com/path"))
        assertFalse(show("@username [happy] 赞"))
        assertFalse(show("123 !!!"))
        assertFalse(show("Great video", oid = 0))
        assertFalse(show("Great video", rpid = 0))
    }

    @Test
    fun serverLanguageDetectionDoesNotHideForeignComments() {
        assertTrue(show("Cool", switch = 1))
        assertTrue(show("Be careful，this，BOOM，boom you know? Just BOOM!", switch = 1))
        assertTrue(show("Great video", switch = 3))
        assertFalse(show("这个视频真好", switch = 1))
        assertFalse(show("123 !!!", switch = 1))
        assertTrue(show("中文", switch = 2))
    }

    @Test
    fun mentionsAndReplyPrefixesDoNotMaskForeignBody() {
        assertTrue(show("@中文名 This is a great video!"))
        assertTrue(show("回复 @大海英语看世界 :Nice video! Thank you so much!", switch = 1))
        assertTrue(show("回复 @中文名字:Nice"))
        assertTrue(show("回复 中文名字：Cool"))
        assertTrue(show("这是非常长的一段中文前言，接下来 This is a long English sentence", switch = 0))
        assertTrue(show("少量汉字 This is a long English comment with Chinese names."))
        assertFalse(show("回复 @SomeEnglishName：谢谢"))
        assertFalse(show("@中文名：谢谢"))
        assertFalse(show("回复 SomeEnglishName：123 [happy]"))
    }

    @Test
    fun chineseDominantCommentsKeepTranslationHidden() {
        // 评论区真实回归：品牌名等少量拉丁字母不应让中文内容出现翻译按钮（服务端返回空）。
        assertFalse(show("pura x view不是卖爆了么，还没一加卖得多"))
        assertFalse(show("view没货啊"))
        assertFalse(show("这是非常长的一段中文前言，接下来 cool"))
    }

    @Test
    fun foreignScriptsAreNotLimitedToLatinOrAnAllowlist() {
        assertTrue(show("شكرا"))
        assertTrue(show("안녕하세요"))
        assertTrue(show("বাংলা"))
        assertTrue(show("தமிழ்"))
        assertTrue(show("ქართული"))
        assertTrue(show("𐐀𐐨𐐻"))
        assertFalse(show("中文 😀🎉 → © 123"))
        assertFalse(show("[বাংলা] @தமிழ் https://example.com/ქართული"))
    }
}
