package com.Android.purebilibili.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.ProvideAppThemeConfig
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.feature.dynamic.DynamicList
import com.android.purebilibili.feature.dynamic.DynamicUiState
import com.android.purebilibili.feature.dynamic.rememberDynamicUserGridState
import com.android.purebilibili.feature.dynamic.components.DynamicCardActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardNavigationActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardPresentation
import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicAuthorSwitchUiRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun forwardedHeaderAndNestedAuthorNavigateToTheirOwnSpacesInFeedAndDetail() {
        val detail = mutableStateOf(false)
        val destinations = mutableListOf<Long>()
        val original = item(11L, 0, "原作者")
        val forward = item(22L, 0, "转发者").copy(type = "DYNAMIC_TYPE_FORWARD", orig = original)
        compose.setContent {
            val context = LocalContext.current
            val loader = remember(context) { ImageLoader.Builder(context).build() }
            MaterialTheme {
                DynamicCardV2(
                    item = forward,
                    gifImageLoader = loader,
                    actions = DynamicCardActions(navigation = DynamicCardNavigationActions(
                        onVideoClick = {}, onUserClick = { destinations += it },
                        onDynamicDetailClick = {},
                    )),
                    presentation = DynamicCardPresentation(isDetail = detail.value),
                )
            }
        }
        repeat(2) { mode ->
            compose.runOnIdle { detail.value = mode == 1 }
            compose.onNodeWithText("转发者").performClick()
            compose.onNodeWithContentDescription("查看转发者的个人主页").performClick()
            compose.onNodeWithText("@原作者").performClick()
        }
        compose.runOnIdle { assertEquals(listOf(22L, 22L, 11L, 22L, 22L, 11L), destinations) }
    }

    @Test fun switchingScrolledAuthorDisposesOldCardsAndStartsAtTop() {
        val owner = mutableStateOf(11L)
        val loading = mutableStateOf(false)
        val revision = mutableStateOf(0)
        var currentState: LazyStaggeredGridState? = null
        compose.setContent {
            val context = LocalContext.current
            val loader = remember(context) { ImageLoader.Builder(context).build() }
            val uid = owner.value
            val gridState = rememberDynamicUserGridState(uid)
            currentState = gridState
            // A new list instance models a same-author refresh without changing its identity.
            val cards = remember(uid, loading.value, revision.value) {
                if (loading.value) emptyList() else List(40) { item(uid, it, "作者${uid}卡片$it") }
            }
            ProvideAppThemeConfig(AppThemeConfig()) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        DynamicList(
                            state = DynamicUiState(), activeLoading = loading.value, activeError = null,
                            onRetry = {}, hasMore = false, isSelectedUserTabActive = true,
                            selectedUserId = uid, selectedUserName = "作者$uid", logicalTab = 0,
                            onOpenSelectedUser = {}, filteredItems = cards, listState = gridState,
                            statusBarHeight = 0.dp, topPaddingExtra = 0.dp, bottomPadding = 0.dp,
                            oldContentDividerIndex = -1, oldContentDividerLabel = "",
                            onVideoClick = {}, onBangumiClick = { _, _ -> }, onArticleClick = null,
                            onDynamicDetailClick = {}, onUserClick = {}, onTopicClick = {},
                            onLiveClick = { _, _, _ -> }, onMusicClick = null, onCollectionClick = null,
                            onCourseClick = null, onOpenLink = null, onSaveDynamicClick = null,
                            onShareToMessageClick = null, onCheckDynamicClick = null,
                            onReserveClick = { _, _ -> }, onLoginClick = {}, gifImageLoader = loader,
                            feedLayoutMode = SettingsManager.DynamicFeedLayoutMode.WATERFALL,
                        )
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(25)
        lateinit var oldState: LazyStaggeredGridState
        compose.runOnIdle {
            oldState = requireNotNull(currentState)
            assertTrue(oldState.firstVisibleItemIndex > 0)
            revision.value++
        }
        compose.runOnIdle {
            assertSame(oldState, currentState)
            assertTrue(requireNotNull(currentState).firstVisibleItemIndex > 0)
        }
        // Freeze animation time: passing must not depend on waiting for old animateItem fades.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { owner.value = 22L; loading.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertNotSame(oldState, currentState)
            assertEquals(0, requireNotNull(currentState).firstVisibleItemIndex)
        }
        compose.onAllNodesWithText("作者11卡片", substring = true).fetchSemanticsNodes().also {
            assertTrue(it.isEmpty())
        }
        compose.runOnIdle { loading.value = false }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("作者22卡片0").assertIsDisplayed()
        compose.onNodeWithText("作者11卡片25").assertDoesNotExist()
        // Returning to a previous owner is also a new layout, not its abandoned scroll position.
        compose.runOnIdle { owner.value = 11L }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("作者11卡片0").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requireNotNull(currentState).firstVisibleItemIndex) }
        compose.mainClock.autoAdvance = true
    }

    private fun item(uid: Long, index: Int, name: String) = DynamicItem(
        id_str = "$uid-$index", type = "DYNAMIC_TYPE_WORD",
        modules = DynamicModules(
            module_author = DynamicAuthorModule(mid = uid, name = name),
            module_dynamic = DynamicContentModule(desc = DynamicDesc(text = "正文\n".repeat(index % 4 + 1))),
        ),
    )
}
