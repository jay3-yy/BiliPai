package com.android.purebilibili.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.VideoPreviewDialog
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VideoPreviewWorkGateUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun hidingCancelsPendingPreviewRequestAndRestoringResumesTheUsersPlayIntent() {
        val allowed = mutableStateOf(true)
        val video = mutableStateOf(VideoItem(bvid = "BVpreview", cid = 7, title = "预览"))
        val requests = AtomicInteger()
        val cancellations = AtomicInteger()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalVisualWorkAllowed provides allowed.value) {
                    VideoPreviewDialog(
                        video = video.value,
                        onDismiss = {},
                        onWatchLater = {},
                        onShare = {},
                        onPlay = {},
                        backgroundBlurEnabled = false,
                        onGetPreviewUrl = { _, _ ->
                            requests.incrementAndGet()
                            try {
                                awaitCancellation()
                            } finally {
                                cancellations.incrementAndGet()
                            }
                        },
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("播放视频预览").performClick()
        rule.waitUntil { requests.get() == 1 }

        rule.runOnIdle { allowed.value = false }
        rule.waitUntil { cancellations.get() == 1 }
        rule.onNodeWithText("全屏播放").assertExists()
        rule.runOnIdle { assertEquals(1, requests.get()) }

        rule.runOnIdle { allowed.value = true }
        rule.waitUntil { requests.get() == 2 }

        // A new content identity must not inherit the previous video's play intent/request.
        rule.runOnIdle { video.value = VideoItem(bvid = "BVnext", cid = 8, title = "下一条") }
        rule.waitUntil { cancellations.get() == 2 }
        rule.onNodeWithContentDescription("播放视频预览").assertExists()
        rule.runOnIdle { assertEquals(2, requests.get()) }
    }
}
