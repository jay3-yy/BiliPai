package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.android.purebilibili.core.store.home.HomeSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeSettingsStoreParityTest {

    @Test
    fun `home store maps defaults the same way as settings manager policy`() {
        val prefs = mutablePreferencesOf()

        assertEquals(
            mapHomeSettingsFromPreferences(prefs),
            HomeSettingsStore.mapFromPreferences(prefs)
        )
    }

    @Test
    fun `home store maps populated preferences the same way as settings manager policy`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("display_mode") to 1,
            booleanPreferencesKey("bottom_bar_floating") to false,
            intPreferencesKey("bottom_bar_label_mode") to 2
        )

        assertEquals(
            mapHomeSettingsFromPreferences(prefs),
            HomeSettingsStore.mapFromPreferences(prefs)
        )
    }

    @Test
    fun `home settings keep retired card surface effects disabled`() {
        val result = mapHomeSettingsFromPreferences(mutablePreferencesOf())

        assertFalse(result.showHomeCoverGlassBadges)
        assertFalse(result.showHomeInfoGlassBadges)
        assertEquals(HomeCardBadgeEffectMode.OFF, result.homeCardBadgeEffectMode)
        assertEquals(HomeCardInfoGlassMode.OFF, result.homeCardInfoGlassMode)
        assertEquals(HomeWallpaperEffectMode.OFF, result.homeWallpaperEffectMode)
        assertEquals(HomeWallpaperEffectScope.GLOBAL, result.homeWallpaperEffectScope)
        assertFalse(result.showHomeUpBadges)
        assertFalse(result.showHomeUpAvatars)
        assertTrue(result.showHomePublishTime)
        assertEquals(HomeDurationStyle.OUTSIDE_COVER, result.homeDurationStyle)
    }

    @Test
    fun `home settings ignore saved retired card surface effects`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_card_badge_effect_mode") to HomeCardBadgeEffectMode.LIGHT_BLUR.value,
            intPreferencesKey("home_card_info_glass_mode") to HomeCardInfoGlassMode.REALTIME_BLUR.value,
            intPreferencesKey("home_wallpaper_effect_mode") to HomeWallpaperEffectMode.OFF.value,
            intPreferencesKey("home_wallpaper_effect_scope") to HomeWallpaperEffectScope.HOME_ONLY.value,
            booleanPreferencesKey("home_up_badges_visible") to false,
            booleanPreferencesKey("home_up_avatars_visible") to false,
            booleanPreferencesKey("home_video_duration_badges_visible") to false
        )

        val result = mapHomeSettingsFromPreferences(prefs)

        assertFalse(result.showHomeCoverGlassBadges)
        assertFalse(result.showHomeInfoGlassBadges)
        assertEquals(HomeCardBadgeEffectMode.OFF, result.homeCardBadgeEffectMode)
        assertEquals(HomeCardInfoGlassMode.OFF, result.homeCardInfoGlassMode)
        assertEquals(HomeWallpaperEffectMode.OFF, result.homeWallpaperEffectMode)
        assertEquals(HomeWallpaperEffectScope.HOME_ONLY, result.homeWallpaperEffectScope)
        assertEquals(false, result.showHomeUpBadges)
        assertEquals(false, result.showHomeUpAvatars)
        assertEquals(HomeDurationStyle.HIDDEN, result.homeDurationStyle)
    }

    @Test
    fun `home settings ignore retired badge mode when info glass is unset`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("home_card_badge_effect_mode") to HomeCardBadgeEffectMode.LIGHT_BLUR.value
        )
        val result = mapHomeSettingsFromPreferences(prefs)
        assertEquals(HomeCardBadgeEffectMode.OFF, result.homeCardBadgeEffectMode)
        assertEquals(HomeCardInfoGlassMode.OFF, result.homeCardInfoGlassMode)
    }

    @Test
    fun `home settings map legacy both glass flags off to effect off`() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("home_cover_glass_badges_visible") to false,
            booleanPreferencesKey("home_info_glass_badges_visible") to false
        )
        val result = mapHomeSettingsFromPreferences(prefs)
        assertEquals(HomeCardBadgeEffectMode.OFF, result.homeCardBadgeEffectMode)
        assertFalse(result.showHomeCoverGlassBadges)
        assertFalse(result.showHomeInfoGlassBadges)
    }
}
