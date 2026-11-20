package com.android.purebilibili.feature.live

import com.android.purebilibili.data.model.response.LiveMessageEmote
import com.android.purebilibili.data.model.response.compileLiveMessageEmotePattern
import com.android.purebilibili.data.repository.LiveEmoticonItem
import com.android.purebilibili.data.repository.LiveEmoticonPackage

internal fun LiveEmoticonItem.messageEmote(): LiveMessageEmote? = url.takeIf { it.isNotBlank() }?.let {
    LiveMessageEmote(
        url = it,
        width = width.coerceAtLeast(1),
        height = height.coerceAtLeast(1),
        official = emoticonUnique.startsWith("official_"),
        unique = emoticonUnique,
    )
}

/** 每次房间表情包更新时建立索引，聊天记录和画面弹幕共用。 */
internal class LiveMessageEmoteResolver(
    packages: List<LiveEmoticonPackage>,
) {
    private val roomResources = liveDraftEmoteResources(packages)
    private val inlinePattern = compileLiveMessageEmotePattern(roomResources.keys)
    private val standaloneResources = buildMap {
        for (pack in packages) {
            for (item in pack.items) {
                if (item.dmType != 1) continue
                val emote = item.messageEmote() ?: continue
                putIfAbsent(item.displayText, emote)
                if (item.emoticonUnique.isNotBlank()) putIfAbsent(item.emoticonUnique, emote)
            }
        }
    }

    fun resolve(message: LiveDanmakuItem): LiveDanmakuItem {
        if (message.isSystem || message.isSuperChat) return message
        val direct = message.emoticon?.takeIf { it.url.isNotBlank() }
            ?: message.emoticonUrl?.takeIf { it.isNotBlank() }?.let { LiveMessageEmote(it) }
            ?: if (message.dmType == 1) standaloneResources[message.text] else null
        val inline = buildMap {
            inlinePattern?.findAll(message.text)?.forEach { match ->
                val token = match.value
                put(token, roomResources.getValue(token))
            }
            // 回传的消息级资源优先，保留不在当前表情包中的内嵌图片。
            message.inlineEmotes.forEach { (key, emote) ->
                if (emote.url.isNotBlank()) put(key, emote)
            }
        }
        return message.copy(emoticon = direct, emoticonUrl = direct?.url, inlineEmotes = inline)
    }
}
