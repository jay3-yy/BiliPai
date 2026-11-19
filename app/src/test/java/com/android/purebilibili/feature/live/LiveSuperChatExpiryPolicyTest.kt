package com.android.purebilibili.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSuperChatExpiryPolicyTest {

    @Test
    fun durationFallsBackWhenMissing() {
        assertEquals(DEFAULT_LIVE_SUPER_CHAT_DURATION_SEC, resolveLiveSuperChatDurationSec(0))
        assertEquals(DEFAULT_LIVE_SUPER_CHAT_DURATION_SEC, resolveLiveSuperChatDurationSec(-1))
        assertEquals(30, resolveLiveSuperChatDurationSec(30))
    }

    @Test
    fun serverDeadlineWinsOverDurationEvenWhenExpired() {
        assertEquals(
            2_005L,
            resolveLiveSuperChatEndTime(endTime = 2_005L, startTime = 1_945L, duration = 300, nowEpochSeconds = 2_000L),
        )
        val expired = resolveLiveSuperChatEndTime(
            endTime = 1_999L, startTime = 1_945L, duration = 300, nowEpochSeconds = 2_000L,
        )
        assertEquals(1_999L, expired)
        assertTrue(shouldExpireLiveSuperChat(expired, 2_000L))
    }

    @Test
    fun startTimeAndDurationRecoverMissingDeadlineWithoutRestartingIt() {
        val endTime = resolveLiveSuperChatEndTime(
            endTime = 0L, startTime = 1_945L, duration = 60, nowEpochSeconds = 2_000L,
        )
        assertEquals(2_005L, endTime)
        assertEquals(5, remainingLiveSuperChatSeconds(endTime, 2_000L))
        assertEquals(2, remainingLiveSuperChatSeconds(endTime, 2_003L))
        assertFalse(shouldExpireLiveSuperChat(endTime, 2_004L))
        assertTrue(shouldExpireLiveSuperChat(endTime, 2_005L))
        assertEquals(0, remainingLiveSuperChatSeconds(endTime, 2_006L))
    }

    @Test
    fun panelReentryUsesTheSameAbsoluteDeadline() {
        val endTime = resolveLiveSuperChatEndTime(
            endTime = 2_005L, startTime = 0L, duration = 0, nowEpochSeconds = 2_000L,
        )
        assertEquals(5, remainingLiveSuperChatSeconds(endTime, 2_000L))
        // Hide at 2000 and reopen at 2003: displaying the same message cannot renew it.
        assertEquals(2, remainingLiveSuperChatSeconds(endTime, 2_003L))
        assertEquals(2, remainingLiveSuperChatSeconds(endTime, 2_003L))
    }

    @Test
    fun missingTimingIsNormalizedOnceAtReceipt() {
        val endTime = resolveLiveSuperChatEndTime(
            endTime = 0L, startTime = 0L, duration = 0, nowEpochSeconds = 2_000L,
        )
        assertEquals(2_060L, endTime)
        assertEquals(57, remainingLiveSuperChatSeconds(endTime, 2_003L))
        assertEquals(
            endTime,
            resolveLiveSuperChatEndTime(endTime, startTime = 0L, duration = 0, nowEpochSeconds = 2_003L),
        )
        assertEquals(
            2_030L,
            resolveLiveSuperChatEndTime(endTime = 0L, startTime = 0L, duration = 30, nowEpochSeconds = 2_000L),
        )
    }

    @Test
    fun flashExpiresAtServerDeadlineOrOriginalTenSecondCap() {
        val shortDeadline = resolveLiveSuperChatFlashEndTime(endTime = 2_005L, receivedAt = 2_000L)
        assertEquals(2_005L, shortDeadline)
        assertEquals(2, remainingLiveSuperChatSeconds(shortDeadline, 2_003L))
        val cappedDeadline = resolveLiveSuperChatFlashEndTime(endTime = 2_300L, receivedAt = 2_000L)
        assertEquals(2_010L, cappedDeadline)
        assertEquals(7, remainingLiveSuperChatSeconds(cappedDeadline, 2_003L))
        assertTrue(shouldExpireLiveSuperChat(cappedDeadline, 2_010L))
        assertTrue(shouldExpireLiveSuperChat(resolveLiveSuperChatFlashEndTime(1_999L, 2_000L), 2_000L))
    }

    @Test
    fun largeDeadlineDoesNotOverflowCountdown() {
        assertEquals(Int.MAX_VALUE, remainingLiveSuperChatSeconds(Int.MAX_VALUE.toLong() + 2_001L, 2_000L))
    }

    @Test
    fun countdownFormatting() {
        assertEquals("0s", formatLiveSuperChatCountdown(0))
        assertEquals("9s", formatLiveSuperChatCountdown(9))
        assertEquals("1:05", formatLiveSuperChatCountdown(65))
    }

    @Test
    fun portraitFlashFollowsDanmakuSwitch() {
        assertTrue(
            shouldShowLiveSuperChatFlash(
                showMediaOverlays = true,
                isLandscape = false,
                isDanmakuEnabled = true,
                flashEnabled = true,
            )
        )
        assertFalse(
            shouldShowLiveSuperChatFlash(
                showMediaOverlays = true,
                isLandscape = false,
                isDanmakuEnabled = false,
                flashEnabled = true,
            )
        )
    }

    @Test
    fun landscapeSuperChatsRemainVisibleWhenRegularDanmakuIsOff() {
        assertTrue(
            shouldShowLiveSuperChatFlash(
                showMediaOverlays = true,
                isLandscape = true,
                isDanmakuEnabled = false,
                flashEnabled = true,
            )
        )
    }

    @Test
    fun flashRespectsIndependentSettingAndHiddenOverlays() {
        for (isLandscape in listOf(false, true)) {
            assertFalse(
                shouldShowLiveSuperChatFlash(
                    showMediaOverlays = true,
                    isLandscape = isLandscape,
                    isDanmakuEnabled = true,
                    flashEnabled = false,
                )
            )
            assertFalse(
                shouldShowLiveSuperChatFlash(
                    showMediaOverlays = false,
                    isLandscape = isLandscape,
                    isDanmakuEnabled = true,
                    flashEnabled = true,
                )
            )
        }
    }
}
