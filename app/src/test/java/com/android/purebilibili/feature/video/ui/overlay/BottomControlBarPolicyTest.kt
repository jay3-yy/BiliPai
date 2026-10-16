package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.feature.video.subtitle.SubtitleTrackOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomControlBarPolicyTest {

    @Test
    fun subtitleButtonVisibleOnlyWhenFullscreenAndTrackExists() {
        assertTrue(
            shouldShowSubtitleButtonInControlBar(
                isFullscreen = true,
                subtitleTrackAvailable = true
            )
        )
        assertFalse(
            shouldShowSubtitleButtonInControlBar(
                isFullscreen = true,
                subtitleTrackAvailable = false
            )
        )
        assertFalse(
            shouldShowSubtitleButtonInControlBar(
                isFullscreen = false,
                subtitleTrackAvailable = true
            )
        )
    }

    @Test
    fun subtitlePanelTrackOptions_dropBlankAndDeduplicateByTrackKey() {
        val options = resolveSubtitlePanelTrackOptions(
            listOf(
                SubtitleTrackOption(
                    trackKey = "zh",
                    languageCode = "zh-Hans",
                    label = "中文（简体）",
                    selected = true,
                    likelyAi = false
                ),
                SubtitleTrackOption(
                    trackKey = "zh",
                    languageCode = "zh-Hans",
                    label = "重复中文",
                    selected = false,
                    likelyAi = false
                ),
                SubtitleTrackOption(
                    trackKey = "",
                    languageCode = "en-US",
                    label = "英语",
                    selected = false,
                    likelyAi = false
                )
            )
        )

        assertEquals(1, options.size)
        assertEquals("中文（简体）", options.single().label)
    }

    @Test
    fun portraitSwitchButtonShownInFullscreenOnly() {
        assertTrue(
            shouldShowPortraitSwitchButtonInControlBar(
                isFullscreen = true
            )
        )
        assertFalse(
            shouldShowPortraitSwitchButtonInControlBar(
                isFullscreen = false
            )
        )
    }

    @Test
    fun episodeButtonVisibleOnlyWhenFullscreenAndHasEntry() {
        assertTrue(
            shouldShowEpisodeButtonInControlBar(
                isFullscreen = true,
                hasEpisodeEntry = true
            )
        )
        assertFalse(
            shouldShowEpisodeButtonInControlBar(
                isFullscreen = true,
                hasEpisodeEntry = false
            )
        )
        assertFalse(
            shouldShowEpisodeButtonInControlBar(
                isFullscreen = false,
                hasEpisodeEntry = true
            )
        )
    }

    @Test
    fun compactFullscreenMovesEpisodeOutOfInlineRow() {
        assertFalse(
            shouldShowEpisodeButtonInControlBar(
                isFullscreen = true,
                hasEpisodeEntry = true,
                widthDp = 393
            )
        )
        assertTrue(
            shouldShowEpisodeInMoreActions(
                isFullscreen = true,
                hasEpisodeEntry = true,
                showInlineEpisodeButton = false
            )
        )
    }

    @Test
    fun danmakuToggle_shownOnTabletInlinePlayerEvenWhenNotFullscreen() {
        assertTrue(
            shouldShowDanmakuToggleInControlBar(
                isFullscreen = true,
                widthDp = 393
            )
        )
        assertTrue(
            shouldShowDanmakuToggleInControlBar(
                isFullscreen = false,
                widthDp = 800
            )
        )
        assertFalse(
            shouldShowDanmakuToggleInControlBar(
                isFullscreen = false,
                widthDp = 411
            )
        )
    }

    @Test
    fun playbackOrderLabelShownWhenFullscreenAndLabelPresent() {
        assertTrue(
            shouldShowPlaybackOrderLabelInControlBar(
                isFullscreen = true,
                playbackOrderLabel = "自动连播"
            )
        )
        assertFalse(
            shouldShowPlaybackOrderLabelInControlBar(
                isFullscreen = true,
                playbackOrderLabel = ""
            )
        )
        assertFalse(
            shouldShowPlaybackOrderLabelInControlBar(
                isFullscreen = false,
                playbackOrderLabel = "自动连播"
            )
        )
    }

    @Test
    fun aspectRatioButtonShownInFullscreenOnly() {
        assertTrue(
            shouldShowAspectRatioButtonInControlBar(
                isFullscreen = true
            )
        )
        assertFalse(
            shouldShowAspectRatioButtonInControlBar(
                isFullscreen = false
            )
        )
    }

    @Test
    fun nextEpisodeButtonDependsOnFullscreenAndAvailability() {
        assertTrue(
            shouldShowNextEpisodeButtonInControlBar(
                isFullscreen = true,
                hasNextEpisode = true
            )
        )
        assertFalse(
            shouldShowNextEpisodeButtonInControlBar(
                isFullscreen = true,
                hasNextEpisode = false
            )
        )
        assertFalse(
            shouldShowNextEpisodeButtonInControlBar(
                isFullscreen = false,
                hasNextEpisode = true
            )
        )
    }

    @Test
    fun moreActionsButtonVisibleWhenFullscreenAndAnyActionAvailable() {
        assertTrue(
            shouldShowMoreActionsButtonInControlBar(
                isFullscreen = true,
                showEpisodeInMoreActions = true,
                showNextEpisodeButton = false,
                showPlaybackOrderLabel = false,
                showAudioQualityButton = false,
                showPortraitSwitchButton = false
            )
        )
        assertTrue(
            shouldShowMoreActionsButtonInControlBar(
                isFullscreen = true,
                showNextEpisodeButton = false,
                showPlaybackOrderLabel = false,
                showAudioQualityButton = false,
                showPortraitSwitchButton = true
            )
        )
        assertFalse(
            shouldShowMoreActionsButtonInControlBar(
                isFullscreen = true,
                showNextEpisodeButton = false,
                showPlaybackOrderLabel = false,
                showAudioQualityButton = false,
                showPortraitSwitchButton = false
            )
        )
        assertFalse(
            shouldShowMoreActionsButtonInControlBar(
                isFullscreen = false,
                showNextEpisodeButton = true,
                showPlaybackOrderLabel = true,
                showAudioQualityButton = true,
                showPortraitSwitchButton = true
            )
        )
    }

    @Test
    fun fullscreenBottomBar_doesNotConsumeNavigationBarPadding() {
        assertFalse(
            shouldApplyNavigationBarPaddingToBottomControlBar(
                isFullscreen = true
            )
        )
    }

    @Test
    fun inlineBottomBar_doesNotConsumeNavigationBarPaddingEither() {
        assertFalse(
            shouldApplyNavigationBarPaddingToBottomControlBar(
                isFullscreen = false
            )
        )
    }

    @Test
    fun floatingPanelMinWidthScalesWithLandscapeWidth() {
        assertEquals(168, resolveFloatingControlPanelMinWidthDp(widthDp = 560))
        assertEquals(176, resolveFloatingControlPanelMinWidthDp(widthDp = 720))
        assertEquals(184, resolveFloatingControlPanelMinWidthDp(widthDp = 1024))
    }

    @Test
    fun moreActionItemMinWidthScalesWithLandscapeWidth() {
        assertEquals(96, resolveMoreActionItemMinWidthDp(widthDp = 560))
        assertEquals(104, resolveMoreActionItemMinWidthDp(widthDp = 720))
        assertEquals(112, resolveMoreActionItemMinWidthDp(widthDp = 1024))
    }

    @Test
    fun moreActionsButtonAnchorOffsetScalesWithLandscapeWidth() {
        assertEquals(24, resolveMoreActionsButtonAnchorOffsetDp(widthDp = 560))
        assertEquals(26, resolveMoreActionsButtonAnchorOffsetDp(widthDp = 720))
        assertEquals(28, resolveMoreActionsButtonAnchorOffsetDp(widthDp = 1024))
    }

    @Test
    fun moreActionsPanelEndPaddingAnchorsToMoreButtonZone() {
        assertEquals(
            76,
            resolveMoreActionsPanelEndPaddingDp(
                horizontalPaddingDp = 16,
                fullscreenIconSizeDp = 20,
                rightActionSpacingDp = 16,
                moreButtonAnchorOffsetDp = 24
            )
        )
        assertEquals(
            86,
            resolveMoreActionsPanelEndPaddingDp(
                horizontalPaddingDp = 20,
                fullscreenIconSizeDp = 22,
                rightActionSpacingDp = 18,
                moreButtonAnchorOffsetDp = 26
            )
        )
    }

    @Test
    fun floatingPanelBottomOffsetClearsControlRowHeight() {
        assertEquals(
            78,
            resolveFloatingPanelBottomOffsetDp(
                bottomPaddingDp = 12,
                controlRowHeightDp = 46,
                gapDp = 20
            )
        )
        assertEquals(
            84,
            resolveFloatingPanelBottomOffsetDp(
                bottomPaddingDp = 14,
                controlRowHeightDp = 50,
                gapDp = 20
            )
        )
    }

    @Test
    fun fullscreenToggleTouchTargetUsesMinimumAccessibleSize() {
        assertEquals(40, resolveFullscreenToggleTouchTargetDp(iconSizeDp = 20))
        assertEquals(46, resolveFullscreenToggleTouchTargetDp(iconSizeDp = 30))
    }

    @Test
    fun floatingPanelsShouldConsumeBackgroundInteractions() {
        assertTrue(
            shouldConsumeBackgroundGesturesForFloatingPanels(
                showSubtitlePanel = true,
                showMoreActionsPanel = false
            )
        )
        assertTrue(
            shouldConsumeBackgroundGesturesForFloatingPanels(
                showSubtitlePanel = false,
                showMoreActionsPanel = true
            )
        )
        assertTrue(
            shouldConsumeBackgroundGesturesForFloatingPanels(
                showSubtitlePanel = true,
                showMoreActionsPanel = true
            )
        )
        assertFalse(
            shouldConsumeBackgroundGesturesForFloatingPanels(
                showSubtitlePanel = false,
                showMoreActionsPanel = false
            )
        )
    }

}
