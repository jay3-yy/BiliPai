package com.android.purebilibili.feature.live

import com.android.purebilibili.data.model.response.LiveMessageEmote
import com.android.purebilibili.data.repository.LiveEmoticonItem
import com.android.purebilibili.data.repository.LiveEmoticonPackage
import com.android.purebilibili.feature.video.danmaku.DanmakuContentSegment
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuContentSegments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveMessageEmoteResourcePolicyTest {
    private val textEmote = LiveEmoticonItem(
        emoji = "害怕", url = "https://example.test/fear.png", dmType = 0, width = 90, height = 60,
    )
    private val largeEmote = LiveEmoticonItem(
        emoji = "大表情", url = "https://example.test/large.png", dmType = 1,
        emoticonUnique = "official_1", width = 180, height = 90,
    )
    private val packages = listOf(LiveEmoticonPackage(1, "房间表情", listOf(textEmote, largeEmote)))

    private val resolver = LiveMessageEmoteResolver(packages)

    @Test
    fun localTextEchoCarriesImagesIntoOverlayWithoutGlobalCacheOrServerEcho() {
        val resolved = resolver.resolve(
            LiveDanmakuItem(text = "我：害怕", isSelf = true),
        )
        assertEquals(textEmote.url, resolved.inlineEmotes["害怕"]?.url)
        assertEquals(90, resolved.inlineEmotes["害怕"]?.width)
        assertEquals(60, resolved.inlineEmotes["害怕"]?.height)
        assertEquals(listOf(DanmakuContentSegment("我：", false), DanmakuContentSegment("害怕", true)),
            resolveDanmakuContentSegments(resolved.text, true, resolved.inlineEmotes.mapValues { it.value.url }, emptyMap()))
    }

    @Test
    fun standaloneEchoKeepsSelectedImageEvenIfPackageIsUnavailable() {
        val resolved = LiveMessageEmoteResolver(emptyList()).resolve(LiveDanmakuItem(
            text = largeEmote.displayText, isSelf = true, dmType = 1, emoticon = largeEmote.messageEmote(),
        ))
        assertEquals(largeEmote.url, resolved.emoticonUrl)
        assertEquals(180, resolved.emoticon?.width)
        assertEquals(90, resolved.emoticon?.height)
    }

    @Test
    fun standaloneServerMessageCanResolveItsSendIdentifier() {
        val resolved = resolver.resolve(
            LiveDanmakuItem(text = "official_1", dmType = 1),
        )
        assertEquals(largeEmote.url, resolved.emoticon?.url)
    }

    @Test
    fun ordinaryTextDoesNotBecomeAnUnrelatedStandaloneImage() {
        val resolved = resolver.resolve(LiveDanmakuItem(text = "大表情"))
        assertNull(resolved.emoticon)
        assertTrue(resolved.inlineEmotes.isEmpty())
    }

    @Test
    fun serverResourcesWinAndMissingRoomResourcesDoNotLeak() {
        val serverEmote = LiveMessageEmote("https://example.test/server.png", 48, 24)
        val resolved = resolver.resolve(LiveDanmakuItem(
            text = "害怕", inlineEmotes = mapOf("害怕" to serverEmote),
        ))
        assertEquals(serverEmote, resolved.inlineEmotes["害怕"])
        assertTrue(LiveMessageEmoteResolver(emptyList()).resolve(LiveDanmakuItem(text = "害怕")).inlineEmotes.isEmpty())
    }
}
