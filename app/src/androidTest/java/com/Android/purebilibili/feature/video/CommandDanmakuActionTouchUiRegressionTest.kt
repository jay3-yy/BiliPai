package com.android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.video.danmaku.CommandDanmakuItem
import com.android.purebilibili.feature.video.danmaku.CommandDanmakuType
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import com.android.purebilibili.feature.video.ui.overlay.CommandDanmakuOverlay
import com.android.purebilibili.feature.video.ui.overlay.CommandDanmakuOverlayState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandDanmakuActionTouchUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    private var player: ExoPlayer? = null
    private var liked by mutableStateOf(false)
    private var coined by mutableStateOf(false)
    private var favorited by mutableStateOf(false)
    private var followed by mutableStateOf(false)
    private var likeKnown by mutableStateOf(true)
    private var followKnown by mutableStateOf(true)
    private var triplePending by mutableStateOf(false)
    private var followPending by mutableStateOf(false)
    private val actions = mutableListOf<String>()

    private fun showCard(attentionType: Int = 2) {
        rule.runOnIdle {
            player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        }
        val state = CommandDanmakuOverlayState()
        val item = CommandDanmakuItem(
            id = "independent-actions",
            type = CommandDanmakuType.ATTENTION,
            content = "",
            startTimeMs = 0L,
            durationMs = 60_000L,
            attentionType = attentionType,
            positionXRatio = 0.5f,
            positionYRatio = 0.5f,
        )
        rule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                Box(Modifier.size(360.dp, 200.dp).testTag("command-player")) {
                    CommandDanmakuOverlay(
                        items = listOf(item),
                        player = requireNotNull(player),
                        visible = true,
                        viewport = DanmakuViewport(
                            with(density) { 360.dp.roundToPx() },
                            with(density) { 200.dp.roundToPx() },
                            density.density,
                        ),
                        bottomInsetPx = 0,
                        state = state,
                        fontScale = 1f,
                        onFollowClick = { actions += "follow"; followed = true },
                        onLikeClick = { actions += "like"; liked = !liked },
                        onCoinClick = { actions += "coin"; coined = true },
                        onFavoriteClick = { actions += "favorite"; favorited = !favorited },
                        onVoteSubmit = { _, _, _ -> },
                        isFollowing = followed,
                        followStatusKnown = followKnown,
                        likeStatusKnown = likeKnown,
                        isLiked = liked,
                        isCoined = coined,
                        isFavorited = favorited,
                        isFollowSubmitting = followPending,
                        isTripleSubmitting = triplePending,
                    )
                }
            }
        }
    }

    private fun bounds(description: String): Rect =
        rule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    private fun tap(pointInRoot: Offset) {
        val host = rule.onNodeWithTag("command-player")
        val origin = host.fetchSemanticsNode().boundsInRoot.topLeft
        host.performTouchInput { click(pointInRoot - origin) }
        rule.waitForIdle()
    }

    private fun tapAction(description: String) = tap(bounds(description).center)

    @After
    fun releasePlayer() {
        rule.runOnIdle { player?.release() }
    }

    @Test
    fun eachIconUpdatesOnlyItsOwnAction_andAlreadyCoinedCanBeClickedAgain() {
        showCard()
        tapAction("点赞")
        rule.onNodeWithContentDescription("点赞").assertIsSelected()
        rule.onNodeWithContentDescription("投币").assertIsNotSelected().assertIsEnabled()
        rule.onNodeWithContentDescription("收藏").assertIsNotSelected().assertIsEnabled()
        tapAction("投币")
        rule.onNodeWithContentDescription("投币").assertIsSelected().assertIsEnabled()
        tapAction("投币")
        tapAction("收藏")
        rule.onNodeWithContentDescription("收藏").assertIsSelected()
        tapAction("点赞")
        rule.onNodeWithContentDescription("点赞").assertIsNotSelected()
        rule.onNodeWithContentDescription("关注").assertIsNotSelected().assertIsEnabled()
        rule.runOnIdle {
            assertEquals(listOf("like", "coin", "coin", "favorite", "like"), actions)
        }
    }

    @Test
    fun adjacentIconEdgesHitOnlyTheirSlot_andGapAndOutsideRowHitNothing() {
        showCard()
        val like = bounds("点赞")
        val coin = bounds("投币")
        val favorite = bounds("收藏")
        val follow = bounds("关注")
        assertEquals(like.right, coin.left, 0.5f)
        assertEquals(coin.right, favorite.left, 0.5f)
        assertTrue(follow.left > favorite.right)
        tap(Offset(like.right - 1f, like.center.y))
        tap(Offset(coin.left + 1f, coin.center.y))
        tap(Offset(coin.right - 1f, coin.center.y))
        tap(Offset(favorite.left + 1f, favorite.center.y))
        tap(Offset((favorite.right + follow.left) / 2f, favorite.center.y))
        tap(Offset(like.center.x, like.top - 1f))
        tap(Offset(coin.center.x, coin.bottom + 1f))
        tap(Offset(like.left - 1f, like.center.y))
        tap(Offset(follow.right + 1f, follow.center.y))
        rule.runOnIdle { assertEquals(listOf("like", "coin", "coin", "favorite"), actions) }
        tapAction("关注")
        rule.onNodeWithContentDescription("关注").assertIsSelected().assertIsNotEnabled()
        rule.onNodeWithContentDescription("点赞").assertIsEnabled()
        rule.onNodeWithContentDescription("投币").assertIsEnabled()
        rule.onNodeWithContentDescription("收藏").assertIsEnabled()
        tapAction("关注")
        rule.runOnIdle { assertEquals(listOf("like", "coin", "coin", "favorite", "follow"), actions) }
    }

    @Test
    fun pendingAndUnknownStatesGateOnlyTheirOwnActions_andFollowRecoversIndependently() {
        showCard()
        rule.runOnIdle { likeKnown = false; followKnown = false }
        rule.onNodeWithContentDescription("点赞").assertIsNotEnabled()
        rule.onNodeWithContentDescription("关注").assertIsNotEnabled()
        tapAction("点赞")
        tapAction("关注")
        tapAction("投币")
        tapAction("收藏")
        rule.runOnIdle {
            assertEquals(listOf("coin", "favorite"), actions)
            likeKnown = true
            followKnown = true
            triplePending = true
        }
        rule.onNodeWithContentDescription("点赞").assertIsNotEnabled()
        rule.onNodeWithContentDescription("投币").assertIsNotEnabled()
        rule.onNodeWithContentDescription("收藏").assertIsNotEnabled()
        rule.onNodeWithContentDescription("关注").assertIsEnabled()
        tapAction("点赞")
        tapAction("投币")
        tapAction("收藏")
        rule.runOnIdle { followPending = true }
        tapAction("关注")
        rule.runOnIdle {
            assertEquals(listOf("coin", "favorite"), actions)
            triplePending = false
            followPending = false
        }
        tapAction("关注")
        rule.onNodeWithContentDescription("关注").assertIsSelected().assertIsNotEnabled()
        tapAction("点赞")
        tapAction("投币")
        tapAction("收藏")
        rule.runOnIdle {
            assertEquals(listOf("coin", "favorite", "follow", "like", "coin", "favorite"), actions)
        }
    }

    @Test
    fun tripleOnlyCardKeepsIndependentIconTargetsWithoutFollow() {
        showCard(attentionType = 1)
        rule.onNodeWithContentDescription("关注").assertDoesNotExist()
        tapAction("点赞")
        tapAction("投币")
        tapAction("收藏")
        rule.runOnIdle { assertEquals(listOf("like", "coin", "favorite"), actions) }
    }

    @Test
    fun followOnlyCardDoesNotExposeEngagementActions() {
        showCard(attentionType = 0)
        rule.onNodeWithContentDescription("点赞").assertDoesNotExist()
        rule.onNodeWithContentDescription("投币").assertDoesNotExist()
        rule.onNodeWithContentDescription("收藏").assertDoesNotExist()
        tapAction("关注")
        rule.onNodeWithContentDescription("关注").assertIsSelected().assertIsNotEnabled()
        rule.runOnIdle { assertEquals(listOf("follow"), actions) }
    }
}
