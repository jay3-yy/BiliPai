@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video

import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.store.FullscreenAspectRatio
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionActions
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import java.util.concurrent.atomic.AtomicLong
import com.android.purebilibili.R
import com.android.purebilibili.feature.video.ui.components.applyPlayerViewResizeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

@RunWith(AndroidJUnit4::class)
class PlayerViewportResizeUiRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private object LocalVideo : NavKey

    @Test
    fun fullscreenRecoveryRelayoutsCachedChildrenEvenWhenOuterSizeIsAlreadyCorrect() {
        rule.runOnUiThread {
            for (texture in listOf(false, true)) {
                val view = if (texture) {
                    LayoutInflater.from(rule.activity)
                        .inflate(R.layout.view_player_texture, null, false) as PlayerView
                } else PlayerView(rule.activity)
                view.useController = false
                val frame = view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
                frame.setAspectRatio(16f / 9f)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 1920, 1200)
                val surface = requireNotNull(view.videoSurfaceView)
                // 模拟重新挂载后外框正确、内部布局仍残留分栏位置；测量规格未变。
                repeat(2) {
                    frame.layout(0, 0, 1280, 720)
                    surface.layout(0, 0, 1280, 720)
                    applyPlayerViewResizeMode(view, AspectRatioFrameLayout.RESIZE_MODE_FIT, forceRelayout = true)
                    assertEquals(1920, view.width)
                    assertEquals(1200, view.height)
                    assertEquals(1920, surface.width)
                    assertEquals(1080, surface.height)
                    assertEquals(0, frame.left)
                    assertEquals(60, frame.top)
                }
            }
        }
    }

    @Test
    fun nativeSurfaceRecoveryUsesCurrentViewportEvenWhenOnlyOuterBoundsWereUpdated() {
        rule.runOnUiThread {
            for (texture in listOf(false, true)) {
                val layoutRes = if (texture) R.layout.view_detail_player_texture else R.layout.view_detail_player_surface
                val view = LayoutInflater.from(rule.activity).inflate(layoutRes, null, false) as PlayerView
                view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
                    .setAspectRatio(16f / 9f)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 1280, 720)
                // AndroidView can publish the new outer bounds before Media3's child measure.
                view.layout(0, 0, 1920, 1200)
                applyPlayerViewResizeMode(view, AspectRatioFrameLayout.RESIZE_MODE_FIT, forceRelayout = true)
                val surface = requireNotNull(view.videoSurfaceView)
                assertEquals("The video must not retain the split-pane width", 1920, surface.width)
                assertEquals("FIT must preserve the source ratio", 1080, surface.height)
                val frame = surface.parent as View
                assertEquals("FIT must center the letterbox vertically", 60, frame.top)
                assertEquals(0, frame.left)
                assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, view.resizeMode)
            }
        }
    }

    @Test
    fun splitPlayerExpandsAndReturnsWithoutRotatingOrReplacingPlayback() {
        val fullscreen = mutableStateOf(false)
        val frames = AtomicLong()
        lateinit var player: ExoPlayer
        lateinit var state: VideoPlayerState
        var viewport = IntSize.Zero
        val originalRatio = runBlocking {
            SettingsManager.getFullscreenAspectRatio(rule.activity).first().also {
                SettingsManager.setFullscreenAspectRatio(rule.activity, FullscreenAspectRatio.FIT)
            }
        }
        rule.runOnUiThread {
            player = ExoPlayer.Builder(rule.activity).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() }
            }
            state = VideoPlayerState(rule.activity, player, rule.activity.lifecycleScope)
            state.attachPlayerListeners()
            player.setMediaItem(MediaItem.fromUri("asset:///player_parity.mp4"))
            player.prepare()
            player.play()
        }
        try {
            rule.setContent {
                CompositionLocalProvider(LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = false)) {
                    MaterialTheme {
                        MiuixTheme {
                            NavDisplay(remember { navBackStackOf(LocalVideo) }) {
                                entry<LocalVideo> {
                                    val content = remember {
                                        movableContentOf<Boolean> { expanded ->
                                            VideoPlayerSection(
                                                state = VideoPlayerSectionState(
                                                    playerState = state,
                                                    uiState = VideoPlaybackUiState.Success(
                                                        ViewInfo(bvid = "local-fixture", cid = 1, title = "Local viewport resize"),
                                                        playUrl = "asset:///player_parity.mp4",
                                                    ),
                                                    bvid = "local-fixture",
                                                    isFullscreen = expanded,
                                                    followStatusKnown = false,
                                                    isFollowSubmitting = false,
                                                    likeStatusKnown = false,
                                                    isTripleSubmitting = false,
                                                    useTextureSurfaceForNavigation = true,
                                                    allowLivePlayerSharedElement = false,
                                                    danmakuHostActive = false,
                                                ),
                                                actions = VideoPlayerSectionActions(
                                                    onToggleFullscreen = { fullscreen.value = !fullscreen.value },
                                                    onQualityChange = {}, onBack = {}, onFollow = {},
                                                ),
                                                modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it },
                                            )
                                        }
                                    }
                                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                                        if (fullscreen.value) content(true)
                                        else Row(Modifier.fillMaxSize()) {
                                            Box(Modifier.weight(0.7f).aspectRatio(16f / 9f)) { content(false) }
                                            Box(Modifier.weight(0.3f).fillMaxHeight())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            rule.waitUntil(20_000) { frames.get() > 2 }
            var inline = IntSize.Zero
            var original: PlayerView? = null
            rule.runOnIdle { inline = viewport; original = playerView() }
            repeat(2) {
                val before = frames.get()
                rule.runOnIdle { fullscreen.value = true }
                rule.waitForIdle()
                rule.waitUntil(10_000) { frames.get() > before + 2 }
                rule.runOnIdle {
                    val view = playerView()
                    assertSame("Fullscreen must retain the native playback view", original, view)
                    assertTrue("Fullscreen must expand horizontally", viewport.width > inline.width)
                    assertEquals(viewport.width, view.width)
                    assertEquals(viewport.height, view.height)
                    val surface = requireNotNull(view.videoSurfaceView)
                    val expectedWidth = minOf(viewport.width, (viewport.height * 16f / 9f).toInt())
                    val expectedHeight = minOf(viewport.height, (viewport.width * 9f / 16f).toInt())
                    assertEquals("Video must fill the available FIT width", expectedWidth.toFloat(), surface.width.toFloat(), 2f)
                    assertEquals("Video must retain 16:9", expectedHeight.toFloat(), surface.height.toFloat(), 2f)
                    val location = IntArray(2)
                    val parentLocation = IntArray(2)
                    surface.getLocationInWindow(location)
                    view.getLocationInWindow(parentLocation)
                    assertEquals("Video must be centered, not stuck at the inline origin",
                        (viewport.height - surface.height) / 2f,
                        (location[1] - parentLocation[1]).toFloat(), 2f)
                }
                rule.runOnIdle { fullscreen.value = false }
                rule.waitForIdle()
                rule.runOnIdle {
                    assertEquals(inline, viewport)
                    assertEquals(inline.width, playerView().videoSurfaceView!!.width)
                }
            }
        } finally {
            rule.activityRule.scenario.close()
            rule.runOnUiThread { state.release(); player.release() }
            runBlocking {
                SettingsManager.setFullscreenAspectRatio(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                    originalRatio,
                )
            }
        }
    }

    private fun playerView(): PlayerView = requireNotNull(findPlayerView(rule.activity.window.decorView))

    private fun findPlayerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPlayerView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
