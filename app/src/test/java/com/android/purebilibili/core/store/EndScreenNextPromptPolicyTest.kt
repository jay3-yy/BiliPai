package com.android.purebilibili.core.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EndScreenNextPromptPolicyTest {

    @Test
    fun `stored valid mode overrides legacy flags`() {
        assertEquals(
            EndScreenNextPromptMode.OFF,
            resolveEndScreenNextPromptMode(
                storedModeValue = EndScreenNextPromptMode.OFF.value,
                legacyNextWatchHintEnabled = true,
                legacyEndScreenCountdownEnabled = true,
            ),
        )
        assertEquals(
            EndScreenNextPromptMode.TOP_RIGHT_HINT,
            resolveEndScreenNextPromptMode(
                storedModeValue = EndScreenNextPromptMode.TOP_RIGHT_HINT.value,
                legacyNextWatchHintEnabled = false,
                legacyEndScreenCountdownEnabled = true,
            ),
        )
        assertEquals(
            EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN,
            resolveEndScreenNextPromptMode(
                storedModeValue = EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN.value,
                legacyNextWatchHintEnabled = true,
                legacyEndScreenCountdownEnabled = false,
            ),
        )
    }

    @Test
    fun `missing or invalid stored mode migrates from legacy flags`() {
        // 两旧键均开启或缺失 -> 优先右下角倒计时
        assertEquals(
            EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN,
            resolveEndScreenNextPromptMode(
                storedModeValue = null,
                legacyNextWatchHintEnabled = true,
                legacyEndScreenCountdownEnabled = true,
            ),
        )
        assertEquals(
            EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN,
            resolveEndScreenNextPromptMode(
                storedModeValue = 999,
                legacyNextWatchHintEnabled = null,
                legacyEndScreenCountdownEnabled = null,
            ),
        )
        // 仅开倒计时 -> 右下角倒计时
        assertEquals(
            EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN,
            resolveEndScreenNextPromptMode(
                storedModeValue = null,
                legacyNextWatchHintEnabled = false,
                legacyEndScreenCountdownEnabled = true,
            ),
        )
        // 仅开接下来观看 -> 右上角提示
        assertEquals(
            EndScreenNextPromptMode.TOP_RIGHT_HINT,
            resolveEndScreenNextPromptMode(
                storedModeValue = null,
                legacyNextWatchHintEnabled = true,
                legacyEndScreenCountdownEnabled = false,
            ),
        )
        // 两旧键全关 -> 关闭
        assertEquals(
            EndScreenNextPromptMode.OFF,
            resolveEndScreenNextPromptMode(
                storedModeValue = null,
                legacyNextWatchHintEnabled = false,
                legacyEndScreenCountdownEnabled = false,
            ),
        )
    }

    @Test
    fun `legacy write sync flags stay mutually exclusive`() {
        assertEquals(
            LegacyEndScreenPromptFlags(nextWatchHintEnabled = false, endScreenNextCountdownEnabled = false),
            resolveLegacyEndScreenPromptFlags(EndScreenNextPromptMode.OFF),
        )
        assertEquals(
            LegacyEndScreenPromptFlags(nextWatchHintEnabled = true, endScreenNextCountdownEnabled = false),
            resolveLegacyEndScreenPromptFlags(EndScreenNextPromptMode.TOP_RIGHT_HINT),
        )
        assertEquals(
            LegacyEndScreenPromptFlags(nextWatchHintEnabled = false, endScreenNextCountdownEnabled = true),
            resolveLegacyEndScreenPromptFlags(EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN),
        )
    }

    @Test
    fun `render visibility enforces mutual exclusion on main player and fallback elsewhere`() {
        assertFalse(shouldShowTopRightNextWatchOverlay(EndScreenNextPromptMode.OFF, fallbackFromCountdown = false))
        assertFalse(shouldShowTopRightNextWatchOverlay(EndScreenNextPromptMode.OFF, fallbackFromCountdown = true))
        assertFalse(shouldShowBottomRightEndScreenCountdown(EndScreenNextPromptMode.OFF))

        assertTrue(shouldShowTopRightNextWatchOverlay(EndScreenNextPromptMode.TOP_RIGHT_HINT, fallbackFromCountdown = false))
        assertTrue(shouldShowTopRightNextWatchOverlay(EndScreenNextPromptMode.TOP_RIGHT_HINT, fallbackFromCountdown = true))
        assertFalse(shouldShowBottomRightEndScreenCountdown(EndScreenNextPromptMode.TOP_RIGHT_HINT))

        // 普通视频页：右下角倒计时启用，右上角提示关闭，避免双卡重叠
        assertFalse(shouldShowTopRightNextWatchOverlay(EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN, fallbackFromCountdown = false))
        assertTrue(shouldShowBottomRightEndScreenCountdown(EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN))
        // 番剧 / 离线 / 竖屏页：回退展示右上角提示
        assertTrue(shouldShowTopRightNextWatchOverlay(EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN, fallbackFromCountdown = true))
    }
}
