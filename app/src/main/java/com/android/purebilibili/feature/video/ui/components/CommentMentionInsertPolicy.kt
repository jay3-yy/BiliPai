package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.text.TextRange

internal data class CommentMentionQuery(
    val atIndex: Int,
    val query: String
)

internal fun resolveActiveCommentMentionQuery(
    text: String,
    cursor: Int
): CommentMentionQuery? {
    val safeCursor = cursor.coerceIn(0, text.length)
    if (safeCursor == 0) return null

    val atIndex = text.lastIndexOf('@', startIndex = safeCursor - 1)
    if (atIndex < 0) return null

    val query = text.substring(atIndex + 1, safeCursor)
    if (query.any { it.isWhitespace() }) return null

    return CommentMentionQuery(atIndex = atIndex, query = query)
}

internal fun insertCommentMentionText(
    text: String,
    cursor: Int,
    mentionName: String
): Pair<String, TextRange> {
    return insertCommentMentionsText(text, cursor, listOf(mentionName))
}

internal fun insertCommentMentionsText(
    text: String,
    cursor: Int,
    mentionNames: List<String>
): Pair<String, TextRange> {
    val safeCursor = cursor.coerceIn(0, text.length)
    if (mentionNames.isEmpty()) return text to TextRange(safeCursor)
    val activeQuery = resolveActiveCommentMentionQuery(text = text, cursor = safeCursor)
    val start = activeQuery?.atIndex ?: safeCursor
    val insertText = mentionNames.joinToString(separator = "") { "@$it " }
    val newText = text.replaceRange(start, safeCursor, insertText)
    val newCursor = start + insertText.length
    return newText to TextRange(newCursor)
}
