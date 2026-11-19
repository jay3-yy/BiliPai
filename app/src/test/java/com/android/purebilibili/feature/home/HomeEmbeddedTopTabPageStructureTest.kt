package com.android.purebilibili.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class HomeEmbeddedTopTabPageStructureTest {

    @Test
    fun homePagerEmbedsLiveAndBangumiIndependentPages() {
        val source = File("src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt").readText()
        assertTrue(source.contains("shouldEmbedLivePageInHomeTopTab(category)"))
        assertTrue(source.contains("LiveListScreen("))
        assertTrue(source.contains("embeddedInHome = true"))
        assertTrue(source.contains("shouldEmbedBangumiPageInHomeTopTab(category)"))
        assertTrue(source.contains("HomeBangumiTabPage("))
        assertTrue(source.contains("scrollToTopRequestId = liveScrollToTopRequestId"))
        assertTrue(source.contains("scrollToTopRequestId = bangumiScrollToTopRequestId"))
        assertTrue(source.contains("scrollToTopRequestId = partitionScrollToTopRequestId"))
        assertTrue(source.contains("resolveHomeTopTabScrollTarget(entry)"))
        assertTrue(!source.contains("onLiveListClick()\n                    return@onCategorySelected"))
    }

    @Test
    fun liveListScreenSupportsHomeEmbeddedChrome() {
        val source = File("src/main/java/com/android/purebilibili/feature/live/LiveListScreen.kt").readText()
        assertTrue(source.contains("embeddedInHome: Boolean = false"))
        assertTrue(source.contains("if (embeddedInHome)"))
        assertTrue(source.contains("ImmersiveAppScaffold("))
        assertTrue(source.contains("topPadding = topInset"))
        assertTrue(source.contains("indicatorTopInset = topInset"))
        assertTrue(!source.contains("Modifier.padding(top = contentTopPadding)"))
        assertTrue(source.contains("contentDescription = \"关注直播\""))
        assertTrue(source.contains("onFollowingClick = onFollowingClick"))
    }

}
