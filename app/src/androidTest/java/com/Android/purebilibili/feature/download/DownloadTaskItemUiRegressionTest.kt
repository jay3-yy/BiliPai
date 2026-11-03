package com.android.purebilibili.feature.download

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DownloadTaskItemUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun downloadDetailsUseFullWidthAndDoNotCompeteWithActions() {
        val size = mutableStateOf(Triple(320, false, 1f))
        rule.setContent {
            val (width, stacked, scale) = size.value
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                MaterialTheme {
                    Box(Modifier.width(width.dp)) {
                        DownloadTaskItem(
                            task = task(), offlinePlayable = false,
                            speedBytesPerSecond = 2L * 1024 * 1024,
                            stacked = stacked, onClick = {}, onPauseResume = {}, onDelete = {},
                            modifier = Modifier.testTag("download_card"),
                        )
                    }
                }
            }
        }
        for (case in listOf(Triple(320, false, 1f), Triple(480, false, 1f), Triple(320, false, 2f), Triple(160, true, 1f), Triple(160, true, 2f))) {
            rule.runOnIdle { size.value = case }
            val card = rule.onNodeWithTag("download_card").fetchSemanticsNode().boundsInRoot
            val owner = rule.onNodeWithText(OWNER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val status = rule.onNodeWithText("下载中 50% · 2.0 MB/s", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val assets = rule.onNodeWithText(ASSETS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val pause = rule.onNodeWithContentDescription("暂停").fetchSemanticsNode().boundsInRoot
            val delete = rule.onNodeWithContentDescription("删除").fetchSemanticsNode().boundsInRoot
            assertTrue("Download status needs the card width, not a narrow cover-side lane", status.width >= card.width - 32f)
            assertTrue("Asset details need the card width", assets.width >= card.width - 32f)
            assertTrue("Details belong below the title/owner block", status.top >= owner.bottom)
            assertTrue("Actions must not squeeze or cover details", pause.top >= assets.bottom && delete.top >= assets.bottom)
            assertEquals("Actions should form one horizontal group", pause.top, delete.top, 1f)
            assertTrue(pause.right <= delete.left)
            for (action in listOf(pause, delete)) {
                assertTrue("Keep usable touch targets", action.width >= 48f && action.height >= 48f)
                assertTrue("Keep actions inside the card", action.right <= card.right && action.bottom <= card.bottom)
            }
        }
    }

    @Test
    fun longFailureIsBoundedAndDoesNotOverlapResumeOrDelete() {
        val error = "网络连接失败，请检查网络后重试。".repeat(12)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(160.dp)) {
                        DownloadTaskItem(
                            task = task().copy(status = DownloadStatus.FAILED, errorMessage = error),
                            offlinePlayable = false, stacked = true,
                            onClick = {}, onPauseResume = {}, onDelete = {},
                            modifier = Modifier.testTag("download_card"),
                        )
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        val failure = rule.onNodeWithText(error, useUnmergedTree = true)
        failure.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Long errors must not expand into an unbounded card", layouts.single().lineCount <= 2)
        val errorBounds = failure.fetchSemanticsNode().boundsInRoot
        val resume = rule.onNodeWithContentDescription("继续").fetchSemanticsNode().boundsInRoot
        val delete = rule.onNodeWithContentDescription("删除").fetchSemanticsNode().boundsInRoot
        assertTrue(resume.top >= errorBounds.bottom && delete.top >= errorBounds.bottom)
        assertTrue(resume.right <= delete.left)
    }

    private fun task() = DownloadTask(
        bvid = "BV1DownloadLayout", cid = 1L, title = "长视频标题：缓存中的视频应当清楚展示标题和下载状态",
        cover = "", ownerName = OWNER, ownerFace = "", duration = 120,
        quality = 80, qualityDesc = "1080P", videoUrl = "", audioUrl = "",
        status = DownloadStatus.DOWNLOADING, progress = 0.5f,
        assets = listOf(
            DownloadAssetState(DownloadAssetKind.VIDEO, DownloadAssetStatus.DOWNLOADING, 100, 50),
            DownloadAssetState(DownloadAssetKind.AUDIO, DownloadAssetStatus.COMPLETED, 100, 100),
            DownloadAssetState(DownloadAssetKind.DANMAKU, DownloadAssetStatus.PENDING),
        ),
    )

    private companion object {
        const val OWNER = "比较长的 UP 主名称"
        const val ASSETS = "视频 50% · 音频完成 · 弹幕等待"
    }
}
