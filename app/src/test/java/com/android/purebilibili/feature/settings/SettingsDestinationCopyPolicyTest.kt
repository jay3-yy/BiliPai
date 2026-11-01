package com.android.purebilibili.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsDestinationCopyPolicyTest {

    @Test
    fun destinationTitles_areUniqueAcrossTargets() {
        val titles = SettingsSearchTarget.entries
            .map { settingsDestinationCopy(it).title }
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun destinationCopy_neverBlank() {
        SettingsSearchTarget.entries.forEach { target ->
            val copy = settingsDestinationCopy(target)
            assertTrue(copy.title.isNotBlank(), "blank title for $target")
            assertTrue(copy.summary.isNotBlank(), "blank summary for $target")
        }
    }

    @Test
    fun knownDestinations_keepUnifiedTitles() {
        assertEquals("外观设置", settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title)
        assertEquals("播放器与画质", settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title)
        assertEquals("导航设置", settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title)
        assertEquals("WebDAV 云备份", settingsDestinationCopy(SettingsSearchTarget.WEBDAV_BACKUP).title)
        assertEquals("首页与推荐", settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).title)
        assertEquals("互动与评论", settingsDestinationCopy(SettingsSearchTarget.INTERACTION_COMMENT).title)
        assertEquals("动画与触感", settingsDestinationCopy(SettingsSearchTarget.ANIMATION).title)
    }
}
