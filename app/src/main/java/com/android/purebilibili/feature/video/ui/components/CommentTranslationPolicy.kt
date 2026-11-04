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
    var nonLatinLetters = 0
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        index += Character.charCount(codePoint)
        if (!Character.isLetter(codePoint)) continue
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED -> Unit
            Character.UnicodeScript.LATIN -> foreignLetters++
            else -> nonLatinLetters++
        }
    }
    // Chinese text does not cancel a foreign passage; non-Latin scripts also have short words.
    return foreignLetters >= 3 || nonLatinLetters > 0
}
