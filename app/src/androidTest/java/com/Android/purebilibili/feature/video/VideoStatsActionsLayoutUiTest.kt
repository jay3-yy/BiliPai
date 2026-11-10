package com.Android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.ui.section.VideoSupplementStatsActions
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class VideoStatsActionsLayoutUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun onlineCountDoesNotCoverSupplementActionsAtNarrowWidthsOrLargeFonts() {
        val width = mutableStateOf(320)
        val fontScale = mutableStateOf(1f)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val glass = mutableStateOf(false)
        val online = mutableStateOf("")
        val showAi = mutableStateOf(true)
        val showNote = mutableStateOf(true)
        var aiClicks = 0
        var noteClicks = 0
        var expandClicks = 0
        rule.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density * maxWidth.value / 900f
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale.value),
                    LocalAppUiStyle provides style.value,
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value),
                ) {
                    MaterialTheme { MiuixTheme {
                        Box(Modifier.width(width.value.dp)) {
                            VideoTitleWithDesc(
                                info = ViewInfo(
                                    bvid = "stats-actions", title = "统计与操作入口",
                                    pubdate = 1_793_588_400L,
                                    stat = Stat(view = 22_990_000, danmaku = 12_000),
                                ),
                                onlineCount = online.value,
                                animateLayout = false,
                                showDescriptionAndTags = false,
                                onExpandedChange = { expandClicks++ },
                                trailingStatsContent = {
                                    VideoSupplementStatsActions(
                                        showAiSummary = showAi.value,
                                        showNote = showNote.value,
                                        onAiSummaryClick = { aiClicks++ },
                                        onNoteClick = { noteClicks++ },
                                    )
                                },
                            )
                        }
                    } }
                }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            for (useGlass in listOf(false, true)) {
                for ((widthDp, scale, count) in listOf(
                    Triple(280, 1f, "3000+人正在看"),
                    Triple(360, 1.5f, "99.9万人正在看"),
                    Triple(840, 1f, "0人正在看"),
                )) {
                    for ((ai, note) in listOf(true to true, true to false, false to true)) {
                        rule.runOnIdle {
                            style.value = uiStyle; glass.value = useGlass
                            width.value = widthDp; fontScale.value = scale
                            showAi.value = ai; showNote.value = note
                            online.value = ""
                        }
                        val labels = listOfNotNull("AI 总结".takeIf { ai }, "视频笔记".takeIf { note })
                        val beforeCountArrives = labels.associateWith { label ->
                            rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                        }
                        rule.runOnIdle { online.value = count }
                        val countBounds = rule.onNodeWithText(count, useUnmergedTree = true)
                            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                        val publishText = FormatUtils.formatPrecisePublishTime(timestampSeconds = 1_793_588_400L)
                        val publishBounds = rule.onNodeWithText(publishText, useUnmergedTree = true)
                            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                        for (label in labels) {
                            val action = rule.onNodeWithContentDescription(label).assertIsDisplayed()
                            val actionBounds = action.fetchSemanticsNode().boundsInRoot
                            assertFalse("$count overlaps $label", countBounds.overlaps(actionBounds))
                            assertEquals("$label must stay anchored when count arrives",
                                beforeCountArrives.getValue(label), actionBounds)
                            assertTrue("Online count must follow publication time", publishBounds.right <= countBounds.left)
                            assertTrue("Online count and publication time must share a row",
                                publishBounds.top < countBounds.bottom && countBounds.top < publishBounds.bottom)
                            assertTrue("$label must stay outside the metadata", countBounds.right <= actionBounds.left)
                            val previousClicks = if (label == "AI 总结") aiClicks else noteClicks
                            action.performClick()
                            rule.runOnIdle {
                                assertEquals(previousClicks + 1, if (label == "AI 总结") aiClicks else noteClicks)
                                assertEquals(0, expandClicks)
                            }
                        }
                    }
                }
            }
        }
    }
}
