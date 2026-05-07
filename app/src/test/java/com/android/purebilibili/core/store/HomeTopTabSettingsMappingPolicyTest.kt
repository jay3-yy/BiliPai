package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTopTabSettingsMappingPolicyTest {

    @Test
    fun emptyPreferences_useExpectedTopTabDefaults() {
        val prefs = mutablePreferencesOf()

        val result = mapHomeTopTabSettingsFromPreferences(prefs)

        assertEquals(
            listOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME"),
            result.orderIds
        )
        assertEquals(
            setOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME"),
            result.visibleIds
        )
        assertEquals("RECOMMEND", result.defaultCategoryId)
    }

    @Test
    fun populatedPreferences_mapTopTabOrderAndVisibility() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("top_tab_order") to "POPULAR,LIVE,RECOMMEND,FOLLOW",
            stringPreferencesKey("top_tab_visible_tabs") to "POPULAR,RECOMMEND",
            stringPreferencesKey("default_home_top_tab_id") to "POPULAR"
        )

        val result = mapHomeTopTabSettingsFromPreferences(prefs)

        assertEquals(listOf("POPULAR", "LIVE", "RECOMMEND", "FOLLOW"), result.orderIds)
        assertEquals(setOf("POPULAR", "RECOMMEND"), result.visibleIds)
        assertEquals("POPULAR", result.defaultCategoryId)
    }
}
