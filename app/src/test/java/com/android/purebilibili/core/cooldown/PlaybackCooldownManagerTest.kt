package com.android.purebilibili.core.cooldown

import com.android.purebilibili.data.repository.VideoRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackCooldownManagerTest {
    @AfterTest
    fun tearDown() {
        PlaybackCooldownManager.accountScopeProvider = {
            com.android.purebilibili.data.repository.videoPlaybackAccountScope()
        }
        PlaybackCooldownManager.nowMsProvider = { System.currentTimeMillis() }
        PlaybackCooldownManager.clearAll()
        VideoRepository.clearPlayUrlRiskCooldown()
    }

    @Test
    fun `switching playback account scope resets video and global cooldowns immediately`() {
        var activeScope = "scope-account-a"
        var now = 10_000L
        PlaybackCooldownManager.accountScopeProvider = { activeScope }
        PlaybackCooldownManager.nowMsProvider = { now }
        PlaybackCooldownManager.clearAll()

        repeat(3) {
            PlaybackCooldownManager.recordFailure("BV1videoA", "risk")
        }
        assertIs<CooldownStatus.VideoCooldown>(PlaybackCooldownManager.getCooldownStatus("BV1videoA"))

        repeat(5) { idx ->
            PlaybackCooldownManager.recordFailure("BV1other$idx", "risk")
        }
        assertEquals(8, PlaybackCooldownManager.getConsecutiveFailures())
        assertIs<CooldownStatus.GlobalCooldown>(PlaybackCooldownManager.getCooldownStatus("BV1fresh"))

        activeScope = "scope-account-b"
        assertEquals(CooldownStatus.Ready, PlaybackCooldownManager.getCooldownStatus("BV1videoA"))
        assertEquals(CooldownStatus.Ready, PlaybackCooldownManager.getCooldownStatus("BV1fresh"))
        assertFalse(PlaybackCooldownManager.isCoolingDown("BV1videoA"))
        assertEquals(0, PlaybackCooldownManager.getConsecutiveFailures())
    }

    @Test
    fun `playUrl risk cooldown is scoped to the playback account that triggered it`() {
        VideoRepository.clearPlayUrlRiskCooldown()
        VideoRepository.recordPlayUrlRiskCooldown(nowMs = 5_000L, currentScope = "scope-guest")

        assertTrue(VideoRepository.isAppApiCoolingDown(nowMs = 10_000L, currentScope = "scope-guest"))
        assertTrue(VideoRepository.getAppApiCooldownRemainingMs(nowMs = 10_000L, currentScope = "scope-guest") > 0L)

        // Switching to a logged-in or dedicated playback account clears the previous scope's cooldown.
        assertFalse(VideoRepository.isAppApiCoolingDown(nowMs = 10_000L, currentScope = "scope-vip"))
        assertEquals(0L, VideoRepository.getAppApiCooldownRemainingMs(nowMs = 10_000L, currentScope = "scope-vip"))
    }
}
