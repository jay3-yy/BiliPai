package com.android.purebilibili.feature.settings

import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SettingsSearchUiPolicyTest {
    @Test
    fun telegramSearchResult_reusesSettingsSectionIconResource() {
        assertEquals(R.drawable.ic_telegram_mono, resolveSettingsEntryVisual(SettingsSearchTarget.TELEGRAM).iconResId)
    }

    @Test
    fun twitterSearchResult_usesTwitterIcon() {
        assertSame(AppIcons.Twitter, resolveSettingsEntryVisual(SettingsSearchTarget.TWITTER).icon)
    }

    @Test
    fun materialSearchResults_useSharedSettingsVectors() {
        assertEquals(R.drawable.ph_play_circle_24, resolveSettingsEntryVisual(SettingsSearchTarget.PLAYBACK).iconResId)
        assertEquals(R.drawable.ph_cloud_arrow_up_24, resolveSettingsEntryVisual(SettingsSearchTarget.WEBDAV_BACKUP).iconResId)
        assertEquals(R.drawable.ph_house_24, resolveSettingsEntryVisual(SettingsSearchTarget.HOME_FEED).iconResId)
    }
}
