package com.android.purebilibili.feature.video

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.feature.video.ambient.AmbientFrameController
import com.android.purebilibili.feature.video.ambient.AmbientPresentation
import com.android.purebilibili.feature.video.ambient.BindPlayerAmbient
import com.android.purebilibili.feature.video.ambient.LocalAmbientController
import com.android.purebilibili.feature.video.ambient.LocalAmbientPresentation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AmbientStandaloneBindingUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun bindingOutsideNavDisplayDoesNotCrashAndDisposalClosesItsGate() {
        val shown = mutableStateOf(true)
        val presentation = AmbientPresentation()
        val controller = AmbientFrameController(presentation)
        rule.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            if (shown.value) {
                // Deliberately provide no NavDisplay transition scope, as in a companion pane.
                CompositionLocalProvider(
                    LocalAmbientPresentation provides presentation,
                    LocalAmbientController provides controller,
                ) {
                    BindPlayerAmbient(
                        player = player, playerView = null, identity = "video", quality = 80,
                        width = 160, height = 90, foreground = true, excluded = false,
                        hdr = false, anime4k = false, transitioning = false,
                        firstFrameReady = false, statusBar = false, diagnosticLogging = false,
                    )
                }
            }
        }
        rule.runOnIdle {
            assertTrue(presentation.visibilityGate())
            assertFalse(presentation.visible)
            shown.value = false
        }
        rule.runOnIdle {
            assertFalse(presentation.visibilityGate())
            assertFalse(presentation.visible)
        }
    }
}
