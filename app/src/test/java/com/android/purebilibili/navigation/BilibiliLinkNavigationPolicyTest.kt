package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BilibiliLinkNavigationPolicyTest {

    @Test
    fun `lookalike shopping domains remain external`() {
        listOf("bilibili.com.example.org", "fakeb23.tv", "b23.tv.example.org").forEach { host ->
            assertIs<BilibiliLinkNavigationAction.External>(
                resolveBilibiliLinkNavigationAction("https://$host/shop")
            )
        }
    }

    @Test
    fun `bilibili video link resolves to native target`() {
        val action = resolveBilibiliLinkNavigationAction("https://www.bilibili.com/video/BV1xx411c7mD")

        val native = assertIs<BilibiliLinkNavigationAction.NativeTarget>(action)
        val target = assertIs<BilibiliNavigationTarget.Video>(native.target)
        assertEquals("BV1xx411c7mD", target.videoId)
    }

    @Test
    fun `unknown bilibili link falls back to in app web`() {
        val action = resolveBilibiliLinkNavigationAction("https://www.bilibili.com/blackboard/activity-test")

        val web = assertIs<BilibiliLinkNavigationAction.InAppWeb>(action)
        assertEquals("https://www.bilibili.com/blackboard/activity-test", web.url)
    }

    @Test
    fun `article identifiers and links resolve to the same native target`() {
        val links = listOf(
            "cv12345",
            "https://www.bilibili.com/read/cv12345",
            "https://www.bilibili.com/h5/note-app/view?cvid=12345",
        )
        links.forEach { link ->
            val native = assertIs<BilibiliLinkNavigationAction.NativeTarget>(
                resolveBilibiliLinkNavigationAction(link)
            )
            assertEquals(BilibiliNavigationTarget.Article(12345L), native.target)
        }
    }

    @Test
    fun `dynamic link resolves to native target`() {
        val action = resolveBilibiliLinkNavigationAction("https://t.bilibili.com/1199344045210468386")

        val native = assertIs<BilibiliLinkNavigationAction.NativeTarget>(action)
        val target = assertIs<BilibiliNavigationTarget.Dynamic>(native.target)
        assertEquals("1199344045210468386", target.dynamicId)
    }

    @Test
    fun `external link remains external`() {
        val action = resolveBilibiliLinkNavigationAction("https://example.com/demo")

        val external = assertIs<BilibiliLinkNavigationAction.External>(action)
        assertEquals("https://example.com/demo", external.url)
    }
}
