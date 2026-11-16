package com.android.purebilibili.feature.message

import com.android.purebilibili.data.model.response.PrivateMessageItem

internal fun chatMessageKey(message: PrivateMessageItem): String = when {
    message.msg_key > 0L -> "key_${message.msg_key}"
    message.msg_seqno > 0L -> "seq_${message.msg_seqno}"
    else -> "${message.sender_uid}_${message.receiver_id}_${message.timestamp}_${message.msg_type}_${message.content}"
}

/** 仅以实际消息推进已读；新会话的系统提示不代表可确认的消息。 */
internal fun resolveChatReadSeqno(
    messages: List<PrivateMessageItem>,
    sessionMaxSeqno: Long = 0L,
): Long? {
    val messageSeqno = messages.asSequence()
        .filterNot { it.msg_type == 18 && it.msg_source == 18 }
        .map { it.msg_seqno }
        .filter { it > 0L }
        .maxOrNull() ?: return null
    // 会话最大序号只能扩展真实消息的已读边界，不能让空会话或系统提示触发上报。
    return maxOf(messageSeqno, sessionMaxSeqno)
}

/** Retains loaded history, updates overlaps, and orders messages by their server sequence. */
internal fun mergeChatMessages(
    existing: List<PrivateMessageItem>,
    incoming: List<PrivateMessageItem>,
): List<PrivateMessageItem> {
    val messages = linkedMapOf<String, PrivateMessageItem>()
    existing.forEach { messages[chatMessageKey(it)] = it }
    incoming.forEach { messages[chatMessageKey(it)] = it }
    return if (messages.values.all { it.msg_seqno > 0L }) {
        messages.values.sortedBy { it.msg_seqno }
    } else {
        messages.values.sortedWith(compareBy<PrivateMessageItem> { it.timestamp }.thenBy { it.msg_seqno })
    }
}
