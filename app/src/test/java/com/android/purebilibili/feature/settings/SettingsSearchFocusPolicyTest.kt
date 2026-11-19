package com.android.purebilibili.feature.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSearchFocusPolicyTest {

    @Test
    fun concreteDecoderResultResolvesItsCardKey() {
        assertEquals(SettingsSearchFocusIds.PLAYBACK_DECODER, resolveSettingsItemScrollKey("playback.hw_decode"))
        assertNull(resolveSettingsItemScrollKey(null))
        assertNull(resolveSettingsItemScrollKey("missing.setting"))
    }

    @Test
    fun homeSearchCanReachCardAndRefreshRowsAfterTheGridOptions() {
        assertEquals(2, resolveSettingsSectionFocusIndex(
            homeSettingsScrollItemKeys, "appearance.home_duration_style", "home_list_title"
        ))
        assertEquals(3, resolveSettingsSectionFocusIndex(
            homeSettingsScrollItemKeys, ContentDisplaySetting.KEEP_RECOMMEND, "home_list_title"
        ))
        assertEquals(0, resolveSettingsSectionFocusIndex(
            homeSettingsScrollItemKeys, null, "home_list_title"
        ))
    }

    @Test
    fun animationSearchFindsPreviewAndRespectsAdvancedOnlyPageContents() {
        val normal = animationSettingsScrollItemKeys(advancedOnly = false)
        val advanced = animationSettingsScrollItemKeys(advancedOnly = true)
        assertEquals(2, resolveSettingsSectionFocusIndex(normal, "glass.popup_motion_style", "animation_glass"))
        assertEquals(2, resolveSettingsSectionFocusIndex(advanced, "glass.progressive_blur_radius", "animation_glass"))
        assertEquals(3, resolveSettingsSectionFocusIndex(normal, PROGRESSIVE_BLUR_RENDERER_SETTING_ID, "animation_glass"))
        assertEquals(4, resolveSettingsSectionFocusIndex(normal, "animation.bottom_bar_blur_enabled", "animation_glass"))
        assertNull(resolveSettingsSectionFocusIndex(advanced, "animation.bottom_bar_blur_enabled", "animation_glass"))
    }

    @Test
    fun pageTimeoutDoesNotClearRowThatAlreadyOwnsRequest() {
        try {
            SettingsSearchFocusController.submit(SettingsSearchTarget.PLAYBACK, SettingsSearchFocusIds.PLAYBACK_DECODER, "playback.hw_decode")
            val request = assertNotNull(SettingsSearchFocusController.request.value)
            assertTrue(SettingsSearchFocusController.claim(request.token))
            SettingsSearchFocusController.clearIfUnclaimed(request.token)
            assertEquals(request, SettingsSearchFocusController.request.value)
            SettingsSearchFocusController.clear(request.token)
            assertNull(SettingsSearchFocusController.request.value)
        } finally {
            SettingsSearchFocusController.clear()
        }
    }

    @Test
    fun oldRequestCannotClaimOrClearNewSearch() {
        try {
            SettingsSearchFocusController.submit(SettingsSearchTarget.PLAYBACK, SettingsSearchFocusIds.PLAYBACK_DECODER, "playback.hw_decode")
            val oldRequest = assertNotNull(SettingsSearchFocusController.request.value)
            SettingsSearchFocusController.submit(SettingsSearchTarget.PLAYBACK, SettingsSearchFocusIds.PLAYBACK_SPEED, "playback.remember_last_playback_speed")
            val newRequest = assertNotNull(SettingsSearchFocusController.request.value)
            assertFalse(SettingsSearchFocusController.claim(oldRequest.token))
            SettingsSearchFocusController.clearIfUnclaimed(oldRequest.token)
            assertEquals(newRequest, SettingsSearchFocusController.request.value)
            SettingsSearchFocusController.clearIfUnclaimed(newRequest.token)
            assertNull(SettingsSearchFocusController.request.value)
        } finally {
            SettingsSearchFocusController.clear()
        }
    }

    @Test
    fun appearanceFocusIndex_noLongerHostsTabletNavigationSection() {
        assertNull(
            resolveAppearanceSettingsScrollIndex(
                focusId = SettingsSearchFocusIds.APPEARANCE_TABLET,
                isTablet = true
            )
        )
        assertNull(resolveAppearanceSettingsScrollIndex(SettingsSearchFocusIds.HOME_OVERVIEW, isTablet = true))
        assertEquals(0, resolveHomeSettingsScrollIndex(SettingsSearchFocusIds.HOME_OVERVIEW))
    }

    @Test
    fun playbackSearchLocatesMiniPlayerGroupsAndLaterSections() {
        val page = PlaybackSettingsPage.PLAYBACK
        val focusId = SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER
        assertEquals(5, resolvePlaybackSettingsItemScrollIndex(page, focusId, "playback.mini_player_mode"))
        assertEquals(6, resolvePlaybackSettingsItemScrollIndex(page, focusId, "playback.audio_now_playing_bar_delete_button"))
        assertEquals(7, resolvePlaybackSettingsItemScrollIndex(page, focusId, "playback.music_lyrics_ui_style"))
        assertEquals(8, resolvePlaybackSettingsItemScrollIndex(page, focusId, "playback.audio_mode_auto_pip_enabled"))
        assertEquals(12, resolvePlaybackSettingsItemScrollIndex(page, SettingsSearchFocusIds.PLAYBACK_NETWORK, null))
        assertEquals(18, resolvePlaybackSettingsItemScrollIndex(page, SettingsSearchFocusIds.PLAYBACK_COMMENTS, null))
    }

    @Test
    fun fullscreenSearchLocatesOffscreenItemsInBothPlaybackPages() {
        val focusId = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
        assertEquals(20, resolvePlaybackSettingsItemScrollIndex(PlaybackSettingsPage.PLAYBACK, focusId, null))
        assertEquals(1, resolvePlaybackSettingsItemScrollIndex(PlaybackSettingsPage.FULLSCREEN, focusId, null))
        assertEquals(22, resolvePlaybackSettingsItemScrollIndex(
            PlaybackSettingsPage.PLAYBACK, focusId, "playback.set_system_brightness_enabled"
        ))
        assertEquals(10, resolvePlaybackSettingsItemScrollIndex(
            PlaybackSettingsPage.FULLSCREEN, focusId, "playback.progress_peak_danmaku_enabled"
        ))
        assertNull(resolvePlaybackSettingsItemScrollIndex(
            PlaybackSettingsPage.COMMENTS, focusId, "playback.progress_peak_danmaku_enabled"
        ))
    }

    @Test
    fun bottomBarFocusIndex_mapsAvailableItemsSection() {
        assertEquals(0, resolveBottomBarSettingsScrollIndex(SettingsSearchFocusIds.BOTTOM_BAR_START))
        assertEquals(1, resolveBottomBarSettingsScrollIndex(SettingsSearchFocusIds.BOTTOM_BAR_BEHAVIOR))
        assertEquals(7, resolveBottomBarSettingsScrollIndex(SettingsSearchFocusIds.BOTTOM_BAR_SEARCH_TABS))
        assertEquals(9, resolveBottomBarSettingsScrollIndex(SettingsSearchFocusIds.BOTTOM_BAR_TABLET))
        assertEquals(13, resolveBottomBarSettingsScrollIndex(SettingsSearchFocusIds.BOTTOM_BAR_AVAILABLE))
    }

    @Test
    fun animationFocusIndex_mapsVisualEffectsSection() {
        assertEquals(0, resolveAnimationSettingsScrollIndex(SettingsSearchFocusIds.ANIMATION_START))
        assertEquals(
            4,
            resolveAnimationSettingsScrollIndex(SettingsSearchFocusIds.ANIMATION_VISUAL_EFFECTS)
        )
    }

    @Test
    fun functionLevelSearchResult_carriesFocusId() {
        val results = resolveSettingsSearchResults("画中画")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER
            }
        )
    }

    @Test
    fun sceneSearchTargetsResolveToExistingDetailFocus() {
        assertEquals(
            SettingsSceneDetailFocus(
                target = SettingsSearchTarget.HOME_FEED,
                focusId = SettingsSearchFocusIds.HOME_OVERVIEW
            ),
            resolveSettingsSceneDetailFocus(SettingsSearchTarget.HOME_FEED)
        )
        assertEquals(
            SettingsSceneDetailFocus(
                target = SettingsSearchTarget.BOTTOM_BAR,
                focusId = SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS
            ),
            resolveSettingsSceneDetailFocus(SettingsSearchTarget.NAVIGATION)
        )
        assertEquals(
            SettingsSceneDetailFocus(
                target = SettingsSearchTarget.PLAYBACK,
                focusId = SettingsSearchFocusIds.PLAYBACK_NETWORK
            ),
            resolveSettingsSceneDetailFocus(SettingsSearchTarget.PLAYBACK_QUALITY)
        )
        assertEquals(
            SettingsSceneDetailFocus(
                target = SettingsSearchTarget.PLAYBACK,
                focusId = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
            ),
            resolveSettingsSceneDetailFocus(SettingsSearchTarget.FULLSCREEN_GESTURE)
        )
        assertEquals(
            SettingsSceneDetailFocus(
                target = SettingsSearchTarget.PLAYBACK,
                focusId = SettingsSearchFocusIds.PLAYBACK_COMMENTS
            ),
            resolveSettingsSceneDetailFocus(SettingsSearchTarget.INTERACTION_COMMENT)
        )
        assertEquals(
            SettingsSceneDetailFocus(
                target = SettingsSearchTarget.PLAYBACK,
                focusId = SettingsSearchFocusIds.PLAYBACK_DEBUG
            ),
            resolveSettingsSceneDetailFocus(SettingsSearchTarget.DIAGNOSTICS)
        )
    }
}
