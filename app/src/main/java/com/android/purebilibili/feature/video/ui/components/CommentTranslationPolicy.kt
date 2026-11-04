package com.android.purebilibili.feature.video.ui.components

private val commentTranslationUrl = Regex("(?i)\\b(?:https?://|www\\.)\\S+")
private val commentTranslationMention = Regex("@[^\\s@:：]+")
private val commentTranslationEmote = Regex("\\[[^\\]\\n]{1,30}]")
private val commentTranslationReplyPrefix = Regex("^\\s*回复\\s*(?:@[^\\s@:：]+|[^\\s:：]+)\\s*[:：]")

/** Server language detection is a hint: mixed-language replies can be marked unavailable. */
internal fun shouldOfferCommentTranslation(
    switch: Int?,
    message: String,
    oid: Long,
    rpid: Long,
): Boolean {
    if (switch == 2) return true
    // A negative/missing switch can come from Chinese mentions or a mixed-language body.
    if (oid <= 0 || rpid <= 0) return false

    val text = message.replace(commentTranslationReplyPrefix, " ")
        .replace(commentTranslationUrl, " ")
        .replace(commentTranslationMention, " ")
        .replace(commentTranslationEmote, " ")
    var foreignLetters = 0
    var foreignWords = 0
    var hanLetters = 0
    var nonLatinLetters = 0
    var inForeignWord = false
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        index += Character.charCount(codePoint)
        if (!Character.isLetter(codePoint)) {
            inForeignWord = false
            continue
        }
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN -> {
                hanLetters++
                inForeignWord = false
            }
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED -> Unit
            Character.UnicodeScript.LATIN -> {
                foreignLetters++
                if (!inForeignWord) foreignWords++
                inForeignWord = true
            }
            else -> {
                nonLatinLetters++
                if (!inForeignWord) foreignWords++
                inForeignWord = true
            }
        }
    }
    // 中文为主的内容服务端翻译返回空，不提供翻译入口；夹杂的外文须占多数且不止一个词，
    // 避免品牌名等单词级拉丁词（如「view没货啊」）触发无结果的翻译。
    // 非拉丁文字（假名、谚文等）短词即可翻译，不受中文占比影响。
    return nonLatinLetters > 0 ||
        (foreignLetters >= 3 && (hanLetters == 0 || (foreignWords >= 2 && foreignLetters >= hanLetters)))
}
