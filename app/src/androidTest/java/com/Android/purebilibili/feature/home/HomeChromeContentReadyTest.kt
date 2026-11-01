package com.android.purebilibili.feature.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeChromeContentReadyTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun paginationAndErrorsDoNotRecomposeTheHostWhenContentRemainsReady() {
        val source = MutableStateFlow(CategoryContent(isLoading = true))
        var commits = 0
        rule.setContent {
            val ready by source.collectHomeChromeContentReady(updatesAllowed = true)
            SideEffect { commits++ }
            BasicText("ready:$ready")
        }
        rule.onNodeWithText("ready:false").assertExists()
        rule.runOnIdle {
            source.value = CategoryContent(videos = persistentListOf(VideoItem(bvid = "BVfirst")))
        }
        rule.onNodeWithText("ready:true").assertExists()
        val baseline = rule.runOnIdle { commits }

        rule.runOnIdle { source.value = source.value.copy(isLoading = true) }
        rule.runOnIdle {
            source.value = source.value.copy(
                videos = persistentListOf(VideoItem(bvid = "BVfirst"), VideoItem(bvid = "BVnext")),
                isLoading = false,
                pageIndex = 2,
            )
        }
        rule.runOnIdle { source.value = source.value.copy(loadMoreError = "加载失败") }
        rule.runOnIdle { assertEquals(baseline, commits) }
    }

    @Test
    fun pausedReadinessFreezesButAnotherCategoryUsesItsOwnCachedReadiness() {
        val first = MutableStateFlow(CategoryContent())
        val second = MutableStateFlow(CategoryContent(isLoading = true))
        var source by mutableStateOf<StateFlow<CategoryContent>>(first)
        var allowed by mutableStateOf(true)
        rule.setContent {
            val ready by source.collectHomeChromeContentReady(allowed)
            BasicText("ready:$ready")
        }
        rule.onNodeWithText("ready:true").assertExists()
        rule.runOnIdle { allowed = false }
        rule.runOnIdle {
            assertEquals(0, first.subscriptionCount.value)
            first.value = CategoryContent(isLoading = true)
        }
        rule.onNodeWithText("ready:true").assertExists()
        rule.runOnIdle { source = second }
        rule.onNodeWithText("ready:false").assertExists()
        rule.runOnIdle { second.value = CategoryContent() }
        rule.onNodeWithText("ready:false").assertExists()
        rule.runOnIdle { allowed = true }
        rule.onNodeWithText("ready:true").assertExists()
    }
}
