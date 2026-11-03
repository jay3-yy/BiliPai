package com.android.purebilibili.feature.video

import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.video.danmaku.CommandDanmakuItem
import com.android.purebilibili.feature.video.danmaku.CommandDanmakuType
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import com.android.purebilibili.feature.video.danmaku.VoteOption
import com.android.purebilibili.feature.video.ui.gesture.detectPlayerTapGestures
import com.android.purebilibili.feature.video.ui.overlay.CommandDanmakuOverlay
import com.android.purebilibili.feature.video.ui.overlay.CommandDanmakuOverlayState
import com.android.purebilibili.feature.video.ui.overlay.rememberCommandDanmakuOverlayState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandDanmakuVisibilityUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var player: ExoPlayer

    @Before
    fun createPlayer() {
        rule.runOnIdle {
            player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
        }
    }

    @After
    fun releasePlayer() {
        rule.runOnIdle { player.release() }
    }

    @Test
    fun hiddenAttentionCard_removesLayoutAndTouchTargets_withoutRemovingRetainedHost() {
        val visible = mutableStateOf(true)
        val actions = IntArray(4)
        val playerTaps = mutableListOf<Offset>()
        var hostEntries = 0
        var hostDisposals = 0
        var tapTimeout = 0L
        val attention = CommandDanmakuItem(
            id = "attention", type = CommandDanmakuType.ATTENTION, content = "三连与关注",
            startTimeMs = 0, durationMs = 30_000, attentionType = 2,
            positionXRatio = 0.5f, positionYRatio = 0.5f,
        )
        rule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                val doubleTapSlopPx = android.view.ViewConfiguration.get(
                    androidx.compose.ui.platform.LocalContext.current
                ).scaledDoubleTapSlop.toFloat()
                tapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
                val commandState = rememberCommandDanmakuOverlayState("same-video-session")
                Box(Modifier.size(320.dp, 240.dp).testTag("retained-host")) {
                    DisposableEffect(Unit) {
                        hostEntries++
                        onDispose { hostDisposals++ }
                    }
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        detectPlayerTapGestures(
                            onTap = { playerTaps += it }, onDoubleTap = {},
                            doubleTapSlopPx = doubleTapSlopPx,
                        )
                    })
                    CommandDanmakuOverlay(
                        items = listOf(attention), player = player,
                        viewport = DanmakuViewport(
                            with(density) { 320.dp.roundToPx() },
                            with(density) { 240.dp.roundToPx() }, density.density,
                        ),
                        bottomInsetPx = 0, state = commandState, visible = visible.value,
                        fontScale = 1f,
                        onLikeClick = { actions[0]++ }, onCoinClick = { actions[1]++ },
                        onFavoriteClick = { actions[2]++ }, onFollowClick = { actions[3]++ },
                        onVoteSubmit = { _, _, _ -> },
                        followStatusKnown = true, likeStatusKnown = true,
                        isFollowing = false, isLiked = false, isCoined = false, isFavorited = false,
                        isFollowSubmitting = false, isTripleSubmitting = false,
                        modifier = Modifier.testTag("command-overlay"),
                    )
                }
            }
        }
        val labels = listOf("点赞", "投币", "收藏", "关注")
        val hostOrigin = rule.onNodeWithTag("retained-host").fetchSemanticsNode().boundsInRoot.topLeft
        val locations = labels.map { label ->
            rule.onNodeWithContentDescription(label).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot.center - hostOrigin
        }
        rule.onNodeWithTag("retained-host").performTouchInput { click(locations[0]) }
        rule.runOnIdle {
            assertEquals(listOf(1, 0, 0, 0), actions.toList())
            assertEquals(0, playerTaps.size)
            visible.value = false
        }
        rule.onNodeWithTag("command-overlay").assertDoesNotExist()
        labels.forEach { rule.onNodeWithContentDescription(it).assertDoesNotExist() }
        rule.onNodeWithTag("retained-host").assertIsDisplayed()
        locations.forEach { location ->
            rule.onNodeWithTag("retained-host").performTouchInput { click(location) }
            rule.mainClock.advanceTimeBy(tapTimeout + 100L)
            rule.waitForIdle()
        }
        rule.runOnIdle {
            assertEquals(locations, playerTaps)
            assertEquals(listOf(1, 0, 0, 0), actions.toList())
            assertEquals(1, hostEntries)
            assertEquals(0, hostDisposals)
            visible.value = true
        }
        labels.forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
        rule.onNodeWithTag("retained-host").performTouchInput { click(locations[0]) }
        rule.runOnIdle {
            assertEquals(listOf(2, 0, 0, 0), actions.toList())
            assertEquals(4, playerTaps.size)
            assertEquals(1, hostEntries)
            assertEquals(0, hostDisposals)
        }
    }

    @Test
    fun hidingAndShowing_keepsDismissedCardsAndPendingVoteSubmission_inSameSession() {
        val visible = mutableStateOf(true)
        val dismissedCard = CommandDanmakuItem(
            id = "dismiss-me", type = CommandDanmakuType.TEXT, content = "可关闭提示",
            startTimeMs = 0, durationMs = 30_000,
        )
        val vote = CommandDanmakuItem(
            id = "pending-vote", type = CommandDanmakuType.VOTE, content = "选择一个选项",
            startTimeMs = 0, durationMs = 30_000,
            voteTitle = "选择一个选项",
            voteOptions = listOf(VoteOption(id = "one", label = "选项一", optionIndex = 1)),
        )
        val items = mutableStateOf(listOf(dismissedCard))
        var voteSubmissions = 0
        rule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                val commandState = rememberCommandDanmakuOverlayState("same-video-session")
                Box(Modifier.size(320.dp, 240.dp).testTag("retained-host")) {
                    CommandDanmakuOverlay(
                        items = items.value, player = player,
                        viewport = DanmakuViewport(
                            with(density) { 320.dp.roundToPx() },
                            with(density) { 240.dp.roundToPx() }, density.density,
                        ),
                        bottomInsetPx = 0, state = commandState, visible = visible.value,
                        fontScale = 1f,
                        onLikeClick = {}, onCoinClick = {}, onFavoriteClick = {}, onFollowClick = {},
                        onVoteSubmit = { _, _, _ -> voteSubmissions++ },
                        followStatusKnown = true, likeStatusKnown = true,
                        isFollowing = false, isLiked = false, isCoined = false, isFavorited = false,
                        isFollowSubmitting = false, isTripleSubmitting = false,
                        modifier = Modifier.testTag("command-overlay"),
                    )
                }
            }
        }
        rule.onNodeWithText("可关闭提示").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭提示").performTouchInput { click(center) }
        rule.onNodeWithText("可关闭提示").assertDoesNotExist()
        rule.runOnIdle { items.value = listOf(dismissedCard, vote) }
        rule.onNodeWithText("选项一").performTouchInput { click(center) }
        rule.onNodeWithText("投票提交中…").assertIsDisplayed()
        rule.onNodeWithText("选项一").assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(1, voteSubmissions)
            visible.value = false
        }
        rule.onNodeWithTag("command-overlay").assertDoesNotExist()
        rule.onNodeWithText("选择一个选项").assertDoesNotExist()
        rule.onNodeWithText("投票提交中…").assertDoesNotExist()
        rule.onNodeWithTag("retained-host").assertIsDisplayed()
        rule.runOnIdle { visible.value = true }
        rule.onNodeWithText("可关闭提示").assertDoesNotExist()
        rule.onNodeWithText("投票提交中…").assertIsDisplayed()
        rule.onNodeWithText("选项一").assertIsNotEnabled().performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, voteSubmissions) }
    }

    @Test
    fun attentionClose_onlyDismissesItsCard_withoutSubmittingActions_orReturningOnReentry() {
        val visible = mutableStateOf(true)
        val state = CommandDanmakuOverlayState()
        var actionCount = 0
        var playerTaps = 0
        val triple = CommandDanmakuItem(
            id = "triple-only", type = CommandDanmakuType.ATTENTION, content = "",
            startTimeMs = 0, durationMs = 30_000, attentionType = 1,
            positionXRatio = 0f, positionYRatio = 0f,
        )
        val combined = triple.copy(
            id = "triple-and-follow", attentionType = 2,
            positionXRatio = 1f, positionYRatio = 1f,
        )
        rule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                Box(Modifier.size(320.dp, 240.dp).testTag("close-host")) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(onTap = { playerTaps++ })
                    })
                    CommandDanmakuOverlay(
                        items = listOf(triple, combined), player = player, visible = visible.value,
                        viewport = DanmakuViewport(
                            with(density) { 320.dp.roundToPx() },
                            with(density) { 240.dp.roundToPx() }, density.density,
                        ),
                        bottomInsetPx = 0, state = state, fontScale = 1f,
                        onFollowClick = { actionCount++ }, onLikeClick = { actionCount++ },
                        onCoinClick = { actionCount++ }, onFavoriteClick = { actionCount++ },
                        onVoteSubmit = { _, _, _ -> actionCount++ },
                        followStatusKnown = true, likeStatusKnown = true,
                        isFollowing = false, isLiked = false, isCoined = false, isFavorited = false,
                        isFollowSubmitting = false, isTripleSubmitting = false,
                    )
                }
            }
        }
        val closeButtons = rule.onAllNodesWithContentDescription("关闭提示")
        closeButtons.assertCountEquals(2)
        val hostOrigin = rule.onNodeWithTag("close-host").fetchSemanticsNode().boundsInRoot.topLeft
        val firstClose = closeButtons[0].fetchSemanticsNode().boundsInRoot
        val firstFavorite = rule.onAllNodesWithContentDescription("收藏")[0]
            .fetchSemanticsNode().boundsInRoot
        assertTrue(firstClose.center.x > firstFavorite.center.x)
        assertTrue(firstClose.center.y < firstFavorite.top)
        rule.onNodeWithTag("close-host").performTouchInput { click(firstClose.center - hostOrigin) }
        closeButtons.assertCountEquals(1)
        rule.onNodeWithContentDescription("关注").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue(state.isDismissed(triple.id))
            assertEquals(false, state.isDismissed(combined.id))
            assertEquals(0, actionCount)
            assertEquals(0, playerTaps)
            visible.value = false
        }
        closeButtons.assertCountEquals(0)
        rule.runOnIdle { visible.value = true }
        closeButtons.assertCountEquals(1)
        rule.onNodeWithContentDescription("关闭提示").performTouchInput { click(center) }
        closeButtons.assertCountEquals(0)
        rule.onNodeWithContentDescription("关注").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("点赞").assertCountEquals(0)
        rule.runOnIdle {
            assertTrue(state.isDismissed(combined.id))
            assertEquals(0, actionCount)
            assertEquals(0, playerTaps)
        }
    }
}
