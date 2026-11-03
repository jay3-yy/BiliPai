package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchSubmitPolicyTest {

    @Test
    fun `search submit opens native video for raw bv`() {
        val action = resolveSearchSubmitAction("BV1xx411c7mD")

        val target = assertIs<SearchSubmitAction.OpenNativeTarget>(action).target
        assertEquals(BilibiliNavigationTarget.Video("BV1xx411c7mD"), target)
    }

    @Test
    fun `search submit opens native video for bilibili link`() {
        val action = resolveSearchSubmitAction("https://www.bilibili.com/video/BV1xx411c7mD")

        val target = assertIs<SearchSubmitAction.OpenNativeTarget>(action).target
        assertEquals(BilibiliNavigationTarget.Video("BV1xx411c7mD"), target)
    }

    @Test
    fun `search submit keeps normal text as search keyword`() {
        val action = assertIs<SearchSubmitAction.OpenSearch>(resolveSearchSubmitAction("  猫和老鼠  "))

        assertEquals("猫和老鼠", action.keyword)
    }

    @Test
    fun `search submit unwraps search url keyword`() {
        val action = assertIs<SearchSubmitAction.OpenSearch>(
            resolveSearchSubmitAction("https://search.bilibili.com/all?keyword=%E7%8C%AB")
        )

        assertEquals("猫", action.keyword)
    }

    @Test
    fun `search submit resolves short links instead of searching their text`() {
        listOf("b23.tv/abc123", "https://b23.tv/abc123").forEach { shortLink ->
            val action = assertIs<SearchSubmitAction.OpenLink>(resolveSearchSubmitAction(shortLink))

            assertEquals("https://b23.tv/abc123", action.url)
        }
    }
    @Test
    fun `search submit extracts short link from official share text`() {
        listOf("https://b23.tv/1RLfKkP", "b23.tv/1RLfKkP").forEach { link ->
            val action = assertIs<SearchSubmitAction.OpenLink>(
                resolveSearchSubmitAction("【BV1xx411c7mD 视频标题】 $link 复制后打开哔哩哔哩"),
            )
            assertEquals("https://b23.tv/1RLfKkP", action.url)
        }
    }

    @Test
    fun `search submit opens video from full share text`() {
        val action = assertIs<SearchSubmitAction.OpenNativeTarget>(
            resolveSearchSubmitAction("【BV1Q541167Qg 视频标题】 https://www.bilibili.com/video/BV1xx411c7mD/?share_source=copy_link"),
        )
        assertEquals(BilibiliNavigationTarget.Video("BV1xx411c7mD"), action.target)
    }

}
