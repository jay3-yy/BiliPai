package com.android.purebilibili.core.filter

import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.core.store.ContentFilterSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentFilterProjectionTest {
    @Test
    fun changingAndRemovingRulesUpdatesLoadedContentWithoutMutatingSource() = runTest {
        val source = MutableStateFlow(listOf("正常内容", "剧透内容"))
        val rules = MutableStateFlow(ContentFilterSnapshot(keywordFilteringEnabled = true, scopes = setOf(ContentFilterScope.VIDEO)))
        val displayed = source.contentFiltered(backgroundScope, rules, StandardTestDispatcher(testScheduler)) { items, snapshot ->
            items.filterNot { snapshot.blocksText(it, ContentFilterScope.VIDEO) }
        }
        runCurrent()
        rules.value = rules.value.copy(keywords = listOf("剧透"))
        runCurrent()
        assertEquals(listOf("正常内容"), displayed.value)
        assertEquals(listOf("正常内容", "剧透内容"), source.value)
        rules.value = rules.value.copy(keywords = emptyList())
        runCurrent()
        assertEquals(source.value, displayed.value)
        source.value = listOf("另一个视频")
        runCurrent()
        assertEquals(listOf("另一个视频"), displayed.value)
    }

    @Test
    fun slowOldProjectionCannotReplaceNewRulesOrNewContent() = runTest {
        val source = MutableStateFlow(listOf("旧视频"))
        val rules = MutableStateFlow(ContentFilterSnapshot(keywords = listOf("旧")))
        val displayed = source.contentFiltered(backgroundScope, rules, StandardTestDispatcher(testScheduler)) { items, snapshot ->
            if (snapshot.keywords == listOf("旧")) delay(1_000)
            items.filterNot { item -> snapshot.keywords.any { item.contains(it) } }
        }
        runCurrent()
        source.value = listOf("新视频", "隐藏内容")
        rules.value = rules.value.copy(keywords = listOf("隐藏"))
        runCurrent()
        assertEquals(listOf("新视频"), displayed.value)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("新视频"), displayed.value)
    }
}
