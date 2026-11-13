package com.android.purebilibili.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.video.danmaku.DanmakuTapAnchor
import com.android.purebilibili.feature.video.ui.components.DanmakuToolbarBubbleShape
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DanmakuToolbarBubbleUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bubbleBelowDanmakuPointsUp() = assertPointer(above = false)
    @Test fun bubbleAboveDanmakuPointsDown() = assertPointer(above = true)

    private fun assertPointer(above: Boolean) {
        rule.setContent {
            val density = LocalDensity.current
            val anchor = with(density) {
                DanmakuTapAnchor(
                    left = 200.dp.toPx(), top = (if (above) 375 else 50).dp.toPx(),
                    right = 300.dp.toPx(), bottom = (if (above) 399 else 75).dp.toPx(),
                    tapX = 250.dp.toPx(), viewportLeft = 0f, viewportTop = 0f,
                    viewportRight = 500.dp.toPx(), viewportBottom = 400.dp.toPx(),
                )
            }
            val shape = with(density) {
                DanmakuToolbarBubbleShape(anchor, IntSize(500.dp.roundToPx(), 400.dp.roundToPx()), 6.dp.roundToPx())
            }
            Box(Modifier.size(200.dp, 52.dp).background(Color.White).testTag("bubble")) {
                Box(Modifier.matchParentSize().clip(shape).background(Color.Black))
            }
        }
        val pixels = rule.onNodeWithTag("bubble").captureToImage().toPixelMap()
        val y = if (above) (pixels.height * 0.96f).toInt() else (pixels.height * 0.04f).toInt()
        assertTrue("Pointer must occupy the edge facing the selected danmaku", pixels[pixels.width / 2, y].red < 0.1f)
        assertTrue("Only the pointer may occupy that edge", pixels[(pixels.width * 0.6f).toInt(), y].red > 0.9f)
    }
}
