package com.android.purebilibili.core.notification.liveupdate

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdatePolicyTest {

    @Test
    fun downloadShortTextRespectsStatusAndSevenCharBudget() {
        assertEquals("准备中", formatLiveUpdateDownloadShortText(0, paused = false))
        assertEquals("48%", formatLiveUpdateDownloadShortText(48, paused = false))
        assertEquals("100%", formatLiveUpdateDownloadShortText(140, paused = false))
        assertEquals("已暂停", formatLiveUpdateDownloadShortText(48, paused = true))
        assertTrue(formatLiveUpdateDownloadShortText(100, paused = false).length <= 7)
    }

    @Test
    fun downloadContentTextIncludesQueueRemainderOnlyWhenMultipleTasksExist() {
        assertEquals("下载中 62%", formatLiveUpdateDownloadContentText(62, remainingCount = 1, paused = false))
        assertEquals("下载中 62% · 队列剩余 4 项", formatLiveUpdateDownloadContentText(62, remainingCount = 4, paused = false))
        assertEquals("已暂停 · 35% · 队列剩余 2 项", formatLiveUpdateDownloadContentText(35, remainingCount = 2, paused = true))
    }

    @Test
    fun sleepShortTextFormatsCountdownAndFadeState() {
        assertEquals("淡出中", formatLiveUpdateSleepShortText(remainingMinutes = 1, fadingOut = true))
        assertEquals("<1分", formatLiveUpdateSleepShortText(remainingMinutes = 1, fadingOut = false))
        assertEquals("25分", formatLiveUpdateSleepShortText(remainingMinutes = 25, fadingOut = false))
    }

    @Test
    fun liveUpdatePromotionRequiresApi36AndSystemPermission() {
        assertTrue(resolveLiveUpdateShouldPromote(Build.VERSION_CODES.BAKLAVA, canPostPromoted = true))
        assertFalse(resolveLiveUpdateShouldPromote(Build.VERSION_CODES.BAKLAVA, canPostPromoted = false))
        assertFalse(resolveLiveUpdateShouldPromote(Build.VERSION_CODES.VANILLA_ICE_CREAM, canPostPromoted = true))
    }
}
