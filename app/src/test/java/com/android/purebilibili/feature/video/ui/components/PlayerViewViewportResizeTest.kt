package com.android.purebilibili.feature.video.ui.components

import android.app.Application
import android.view.View
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerViewViewportResizeTest {
    @Test
    fun sameResizeModeFollowsSplitFullscreenAndReturnViewport() {
        val view = PlayerView(RuntimeEnvironment.getApplication())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1034, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(618, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1034, 618)

        // 验证原生 View 树尺寸交接；不模拟解码或断言设备 Surface 已正确显示。
        for ((width, height) in listOf(2000 to 1200, 1034 to 618)) {
            applyPlayerViewResizeMode(
                playerView = view,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                forceRelayout = true,
                expectedWidth = width,
                expectedHeight = height,
            )
            assertEquals(width, view.width)
            assertEquals(height, view.height)
            val content = view.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
            assertEquals(width, content.width)
            assertEquals(height, content.height)
        }
    }
}
