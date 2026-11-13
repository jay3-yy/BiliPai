package com.android.purebilibili.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.ViewPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewPointSegmentFillUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun nativeFillFollowsPositionWithinChapterAndBackwardSeek() = checkFill(glass = false)

    @Test
    fun glassFillFollowsPositionWithinChapterAndBackwardSeek() = checkFill(glass = true)

    private fun checkFill(glass: Boolean) {
        val position = mutableLongStateOf(0L)
        val chapters = listOf(
            ViewPoint(from = 0, to = 10, content = "A"),
            ViewPoint(from = 10, to = 20, content = "B"),
        )
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color.Red)) {
                Box(Modifier.background(Color.Black)) {
                    // Position changes only through the provider, including a simulated seek preview.
                    if (glass) {
                        com.android.purebilibili.feature.video.ui.overlay.glass.ViewPointSegmentBar(
                            viewPoints = chapters, durationMs = 20_000, currentPositionMs = 0,
                            onSeek = {}, positionProvider = { position.longValue },
                            modifier = Modifier.width(240.dp).testTag("chapters"),
                        )
                    } else {
                        com.android.purebilibili.feature.video.ui.overlay.ViewPointSegmentBar(
                            viewPoints = chapters, durationMs = 20_000, currentPositionMs = 0,
                            onSeek = {}, positionProvider = { position.longValue },
                            modifier = Modifier.width(240.dp).testTag("chapters"),
                        )
                    }
                }
            }
        }
        fun assertFill(at: Long, filled: List<Float>, empty: List<Float>) {
            rule.runOnIdle { position.longValue = at }
            val pixels = rule.onNodeWithTag("chapters").captureToImage().toPixelMap()
            for (x in filled) {
                val color = pixels[(pixels.width * x).toInt(), pixels.height - 2]
                assertTrue("Expected filled chapter at $at ms, x=$x", color.red > color.green + 0.25f)
            }
            for (x in empty) {
                val color = pixels[(pixels.width * x).toInt(), pixels.height - 2]
                assertEquals("Expected unplayed chapter at $at ms, x=$x", color.red, color.green, 0.05f)
            }
        }
        assertFill(0, emptyList(), listOf(0.1f, 0.35f, 0.6f, 0.9f))
        assertFill(5_000, listOf(0.1f), listOf(0.35f, 0.6f))
        assertFill(15_000, listOf(0.1f, 0.35f, 0.6f), listOf(0.9f))
        assertFill(20_000, listOf(0.1f, 0.35f, 0.6f, 0.9f), emptyList())
        assertFill(5_000, listOf(0.1f), listOf(0.35f, 0.6f, 0.9f))
    }
}
