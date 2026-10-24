@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.feature.anime4k.Anime4KPreset
import com.android.purebilibili.feature.anime4k.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.anime4k.decodeVideoEnhancementConfig
import com.android.purebilibili.feature.plugin.Anime4KPlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** All decoding, seeking and item changes below use the bundled MP4, never network/account mocks. */
@RunWith(AndroidJUnit4::class)
class PlayerParityEmulatorTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<PlayerParityTestActivity>
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalEnhancement = "{}"
    private var originalSpeed = 1f

    @Before
    fun launchLocalPlayer() {
        runBlocking {
            originalEnhancement = PluginStore.getConfigJson(context, Anime4KPlugin.PLUGIN_ID) ?: "{}"
            originalSpeed = SettingsManager.getLastPlaybackSpeed(context).first()
        }
        scenario = ActivityScenario.launch(Intent(context, PlayerParityTestActivity::class.java))
        awaitPlayer { it.player.playbackState == Player.STATE_READY && it.renderedFrames.get() > 0 && it.hasValidVideoSurface() }
    }

    @After
    fun releaseAndRestorePreferences() {
        if (::scenario.isInitialized) scenario.close()
        runBlocking {
            PluginStore.setConfigJson(context, Anime4KPlugin.PLUGIN_ID, originalEnhancement)
            SettingsManager.setLastPlaybackSpeed(context, originalSpeed)
        }
    }

    @Test
    fun actualRotationRetainsDecoderAndFourModeControlGeometryAcrossGlass() {
        var originalPlayer: Player? = null
        scenario.onActivity { originalPlayer = it.player }
        val nativeByMode = mutableMapOf<Pair<Boolean, Boolean>, Map<String, Rect>>()
        val menuByMode = mutableMapOf<Pair<Boolean, Boolean>, Pair<List<String>, Rect>>()
        for (uiStyle in AppUiStyle.entries) {
        scenario.onActivity { it.uiStyle = uiStyle }
        for (landscape in listOf(false, true)) {
            scenario.onActivity { it.rotateToLandscape(landscape) }
            awaitPlayer { (it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) == landscape }
            for (fullscreen in listOf(false, true)) {
                scenario.onActivity { it.fullscreen = fullscreen; it.glass = false }
                val frameBefore = AtomicSnapshot.frames(scenario)
                awaitPlayer { it.renderedFrames.get() > frameBefore + 1 && it.hasValidVideoSurface() }
                scenario.onActivity { assertSame("Rotation recreated the playback session", originalPlayer, it.player) }
                assertReferenceGate(fullscreen)
                val native = visibleControlBounds()
                assertOrderedAndContained(native)
                val expectedNative = nativeByMode.getOrPut(landscape to fullscreen) { native }
                assertEquals("Native style changed the player geometry", expectedNative, native)
                scenario.onActivity { it.glass = true }
                val glass = visibleControlBounds()
                assertEquals("Glass changed the available controls", native.keys, glass.keys)
                native.forEach { (tag, expected) -> assertEquals("Glass moved $tag", expected, glass.getValue(tag)) }
                assertOrderedAndContained(glass)
                // Menus retain the same reachable items, not a second glass-only presentation.
                var expectedMenu: Pair<List<String>, Rect>? = null
                for (material in listOf(false, true)) {
                    scenario.onActivity { it.glass = material }
                    rule.onNodeWithTag("player_speed_action").performClick()
                    val menuLabels = rule.onAllNodes(hasAnyAncestor(isPopup()), useUnmergedTree = true)
                        .fetchSemanticsNodes().flatMap { node ->
                            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        }
                    val menu = menuLabels to rule.onNode(isPopup()).fetchSemanticsNode().boundsInRoot
                    val styleIndependentMenu = menuByMode.getOrPut(landscape to fullscreen) { menu }
                    assertEquals("Native style changed the menu contents or bounds", styleIndependentMenu, menu)
                    if (expectedMenu == null) expectedMenu = menu
                    else assertEquals("Glass changed the menu contents or bounds", expectedMenu, menu)
                    rule.onNodeWithText("1.5X").performScrollTo().assertIsDisplayed().performClick()
                    awaitPlayer { it.player.playbackParameters.speed == 1.5f }
                    scenario.onActivity { it.player.setPlaybackSpeed(1f) }
                }
            }
        }
        }
    }

    @Test
    fun fourModeMenusChangeActualPlayerTracksSeekItemSurfaceAndPersistedEnhancement() {
        for (landscape in listOf(false, true)) {
            scenario.onActivity { it.rotateToLandscape(landscape) }
            awaitPlayer { (it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) == landscape }
            for (fullscreen in listOf(false, true)) {
                scenario.onActivity {
                    it.fullscreen = fullscreen
                    it.glass = fullscreen
                    it.player.setPlaybackSpeed(1f)
                    it.player.play()
                }
                rule.onNodeWithTag("player_speed_action").performClick()
                rule.onNodeWithText("1.5X").performScrollTo().performClick()
                awaitPlayer { it.player.playbackParameters.speed == 1.5f }
                rule.waitUntil(10_000) {
                    runBlocking { SettingsManager.getLastPlaybackSpeed(context).first() == 1.5f }
                }

                if (rule.onAllNodesWithTag("player_ratio_action").fetchSemanticsNodes().isNotEmpty()) {
                    var chooseCrop = true
                    scenario.onActivity { chooseCrop = it.playerView.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_ZOOM }
                    rule.onNodeWithTag("player_ratio_action").performClick()
                    rule.onNodeWithText(if (chooseCrop) "裁剪" else "自动").performScrollTo().performClick()
                    awaitPlayer {
                        it.playerView.resizeMode == if (chooseCrop) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }

                rule.onNodeWithTag("player_subtitle_action").performClick()
                rule.onNodeWithText("关闭字幕").performScrollTo().performClick()
                awaitPlayer { it.player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) }
                rule.onNodeWithTag("player_subtitle_action").performClick()
                rule.onNodeWithText("Local English subtitles").performScrollTo().performClick()
                awaitPlayer { it.subtitleSelected && it.subtitleCue.contains("Local subtitle fixture") }

                var targetPage = 0
                var currentLanguage = ""
                scenario.onActivity { targetPage = (it.selectedPage + 1) % 3; currentLanguage = it.audioLanguage }
                rule.onNodeWithTag("player_episode_action").performClick()
                rule.onNodeWithText("Local episode ${targetPage + 1}", substring = true).performScrollTo().performClick()
                awaitPlayer { it.player.currentMediaItem?.mediaId == "$targetPage:$currentLanguage" && it.player.playbackState == Player.STATE_READY }
                scenario.onActivity { assertEquals(targetPage, it.selectedPage) }

                val language = if (currentLanguage.isEmpty()) "en" else ""
                rule.onNodeWithTag("player_translation_action").performClick()
                // Bounded local streams exercise real replacement, not server translation.
                rule.onNodeWithText(if (language.isEmpty()) "关闭翻译" else "Local English stream").performScrollTo().performClick()
                awaitPlayer {
                    val expectedAsset = if (language.isEmpty()) "player_parity.mp4" else "player_parity_en.mp4"
                    it.player.currentMediaItem?.mediaId == "$targetPage:$language" &&
                        it.player.currentMediaItem?.localConfiguration?.uri?.lastPathSegment == expectedAsset &&
                        it.player.playbackState == Player.STATE_READY
                }

                rule.onNodeWithTag("player_play_pause").performClick()
                awaitPlayer { !it.player.playWhenReady }
                scenario.onActivity { it.player.seekTo(0) }
                rule.onNodeWithTag("player_chapter_action").performClick()
                rule.onNodeWithText("Local chapter", substring = true).performScrollTo().performClick()
                awaitPlayer { it.player.currentPosition in 950L..1100L }
                rule.onNodeWithTag("player_progress_seek_area").performTouchInput { click(percentOffset(0.6f, 0.5f)) }
                awaitPlayer { it.player.currentPosition in 1400L..2200L }

                val preset = if (fullscreen) Anime4KPreset.QUALITY else Anime4KPreset.FAST
                scenario.onActivity {
                    it.enhancement.setPreset(preset)
                    it.enhancement.setAlgorithm(VideoEnhancementAlgorithm.FSR_1_0)
                    it.enhancement.setRememberAcrossVideos(true, currentVideoEnabled = true)
                }
                awaitPlayer {
                    it.enhancement.configState.value.algorithm == VideoEnhancementAlgorithm.FSR_1_0 &&
                        it.enhancement.configState.value.preset == preset &&
                        it.enhancement.configState.value.rememberedEnabled
                }
                rule.onNodeWithContentDescription("更多").performClick()
                rule.onNodeWithText("超分辨率").performScrollTo().performClick()
                rule.onNodeWithText(if (preset == Anime4KPreset.FAST) "效率" else "画质", useUnmergedTree = true)
                    .performScrollTo().performClick()
                awaitPlayer {
                    it.enhancement.configState.value.algorithm == VideoEnhancementAlgorithm.ANIME4K &&
                        it.enhancement.configState.value.preset == preset &&
                        it.enhancement.configState.value.rememberedEnabled
                }
                rule.waitUntil(10_000) {
                    runBlocking {
                        val json = PluginStore.getConfigJson(context, Anime4KPlugin.PLUGIN_ID) ?: return@runBlocking false
                        val stored = decodeVideoEnhancementConfig(json)
                        stored.algorithm == VideoEnhancementAlgorithm.ANIME4K &&
                            stored.preset == preset && stored.rememberedEnabled
                    }
                }
                pressBack()
                rule.onNodeWithText("超分辨率").assertDoesNotExist()
                // The persisted configuration is verified; device-specific shader output is not.
                scenario.onActivity { it.player.play() }
                val before = AtomicSnapshot.frames(scenario)
                awaitPlayer { it.renderedFrames.get() > before + 1 && it.hasValidVideoSurface() }
            }
        }
    }

    @Test
    fun inlineEpisodeSelectionChangesMediaIdentityWithoutEnteringFullscreen() {
        scenario.onActivity { it.rotateToLandscape(false); it.fullscreen = false }
        awaitPlayer { it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        rule.onNodeWithTag("player_episode_action").performClick()
        rule.onNodeWithText("Local episode 3", substring = true).performScrollTo().performClick()
        awaitPlayer { it.player.currentMediaItem?.mediaId == "2:" && it.player.playbackState == Player.STATE_READY }
        scenario.onActivity { assertFalse(it.fullscreen) }
    }

    private fun assertReferenceGate(fullscreen: Boolean) {
        var extended = fullscreen
        scenario.onActivity { extended = fullscreen || it.resources.configuration.screenWidthDp >= 500 }
        for (tag in listOf("player_ratio_action", "player_quality_action")) {
            if (extended) rule.onNodeWithTag(tag).assertExists() else rule.onNodeWithTag(tag).assertDoesNotExist()
        }
        rule.onAllNodesWithTag("player_play_pause").assertCountEquals(1)
        rule.onNodeWithTag("player_more_actions").assertDoesNotExist()
        rule.onNodeWithTag("player_danmaku_toggle").assertDoesNotExist()
        rule.onNodeWithTag("player_enhancement_action").assertDoesNotExist()
    }

    private fun visibleControlBounds(): Map<String, Rect> {
        val order = listOf(
            "player_play_pause", "player_previous_episode", "player_next_episode", "player_enhancement_action",
            "player_chapter_action", "player_episode_action", "player_ratio_action", "player_translation_action",
            "player_subtitle_action", "player_speed_action", "player_quality_action", "player_fullscreen_toggle",
        )
        return order.filter { rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
            .associateWith { rule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
    }

    private fun assertOrderedAndContained(bounds: Map<String, Rect>) {
        val viewport = rule.onNodeWithTag("parity_player_viewport").fetchSemanticsNode().boundsInRoot
        bounds.forEach { (tag, rect) ->
            assertTrue("$tag is clipped away", rect.width > 1f && rect.height > 1f)
            assertTrue("$tag escapes the surface", rect.left >= viewport.left - 1f && rect.right <= viewport.right + 1f && rect.top >= viewport.top - 1f && rect.bottom <= viewport.bottom + 1f)
            val measured = rule.onNodeWithTag(tag).fetchSemanticsNode().size
            assertEquals(
                "$tag is partially clipped instead of uniformly scaled",
                rect.width / measured.width, rect.height / measured.height, 0.05f,
            )
        }
        bounds.entries.zipWithNext().forEach { (left, right) ->
            assertTrue("${left.key} overlaps ${right.key}", left.value.right <= right.value.left + 1f)
        }
    }

    private fun awaitPlayer(predicate: (PlayerParityTestActivity) -> Boolean) {
        rule.waitUntil(20_000) {
            var matches = false
            scenario.onActivity { matches = predicate(it) }
            matches
        }
    }

    private object AtomicSnapshot {
        fun frames(scenario: ActivityScenario<PlayerParityTestActivity>): Long {
            var value = 0L
            scenario.onActivity { value = it.renderedFrames.get() }
            return value
        }
    }
}
