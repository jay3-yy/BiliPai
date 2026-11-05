package com.android.purebilibili.feature.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeFeedStateCollectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchingRetainedPagesStopsHiddenSubscriptionsAndResumesOnlyLatestContent() {
        val recommend = MutableStateFlow("recommend cached")
        val following = MutableStateFlow("following cached")
        var selectedPage by mutableStateOf(0)
        var unobscured by mutableStateOf(true)
        composeRule.setContent {
            Column {
                listOf(recommend, following).forEachIndexed { index, source ->
                    val work = resolveHomePagerPageWork(
                        visible = selectedPage == index,
                        currentPage = selectedPage == index,
                        pagerScrolling = false,
                        updatesAllowed = true,
                        workAllowed = true,
                        visualWorkActive = true,
                        unobscured = unobscured,
                    )
                    val displayed by source.collectAsHomeFeedState(work.collectUpdates)
                    BasicText(displayed)
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(1, recommend.subscriptionCount.value)
            assertEquals(0, following.subscriptionCount.value)
            following.value = "following latest"
        }
        composeRule.onNodeWithText("following cached").assertTextEquals("following cached")
        composeRule.runOnIdle { selectedPage = 1 }
        composeRule.onNodeWithText("following latest").assertTextEquals("following latest")
        composeRule.runOnIdle {
            assertEquals(0, recommend.subscriptionCount.value)
            assertEquals(1, following.subscriptionCount.value)
            unobscured = false
        }
        composeRule.runOnIdle {
            assertEquals(0, following.subscriptionCount.value)
            following.value = "following after overlay"
            recommend.value = "recommend after return"
        }
        composeRule.onNodeWithText("following latest").assertTextEquals("following latest")
        composeRule.runOnIdle { unobscured = true; selectedPage = 0 }
        composeRule.onNodeWithText("recommend after return").assertTextEquals("recommend after return")
        composeRule.runOnIdle {
            assertEquals(1, recommend.subscriptionCount.value)
            assertEquals(0, following.subscriptionCount.value)
        }
    }

    @Test
    fun pausedFeedKeepsDisplayedDataAndResumesFromLatestValue() {
        val source = MutableStateFlow("cached")
        var updatesAllowed by mutableStateOf(true)
        composeRule.setContent {
            val displayed by source.collectAsHomeFeedState(updatesAllowed)
            BasicText(displayed)
        }
        composeRule.onNodeWithText("cached").assertTextEquals("cached")
        composeRule.runOnIdle { updatesAllowed = false }
        composeRule.runOnIdle {
            assertEquals(0, source.subscriptionCount.value)
            source.value = "intermediate"
            source.value = "latest"
        }
        composeRule.onNodeWithText("cached").assertTextEquals("cached")
        composeRule.runOnIdle { updatesAllowed = true }
        composeRule.onNodeWithText("latest").assertTextEquals("latest")
    }

    @Test
    fun switchingSourceWhilePausedUsesItsOwnCacheAndRealZero() {
        val first = MutableStateFlow("previous category")
        val second = MutableStateFlow("0")
        var source by mutableStateOf<StateFlow<String>>(first)
        var updatesAllowed by mutableStateOf(false)
        composeRule.setContent {
            val displayed by source.collectAsHomeFeedState(updatesAllowed)
            BasicText(displayed)
        }
        composeRule.onNodeWithText("previous category").assertTextEquals("previous category")
        composeRule.runOnIdle { source = second }
        composeRule.onNodeWithText("0").assertTextEquals("0")
        composeRule.runOnIdle {
            assertEquals(0, first.subscriptionCount.value)
            assertEquals(0, second.subscriptionCount.value)
            first.value = "old request completed"
            second.value = "new category loaded"
        }
        composeRule.onNodeWithText("0").assertTextEquals("0")
        composeRule.runOnIdle { updatesAllowed = true }
        composeRule.onNodeWithText("new category loaded").assertTextEquals("new category loaded")
    }
}
