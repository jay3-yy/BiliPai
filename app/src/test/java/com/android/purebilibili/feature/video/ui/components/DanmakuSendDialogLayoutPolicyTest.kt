package com.android.purebilibili.feature.video.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuSendDialogLayoutPolicyTest {

    @Test
    fun `custom RGB survives while invalid mode and size use safe defaults`() {
        val selection = resolveDanmakuSendSelectionState(
            initialColor = 123,
            initialMode = 99,
            initialFontSize = 77,
            colorOptions = listOf(16777215, 16646914),
            modeOptions = listOf(1, 4, 5),
            fontSizeOptions = listOf(18, 25)
        )

        assertEquals(123, selection.color)
        assertEquals(1, selection.mode)
        assertEquals(25, selection.fontSize)
    }

    @Test
    fun `VIP gradual color survives style restoration`() {
        val selection = resolveDanmakuSendSelectionState(
            initialColor = DANMAKU_SEND_VIP_GRADUAL_COLOR,
            initialMode = 5,
            initialFontSize = 18,
            colorOptions = danmakuSendColorOptions().map { it.value },
            modeOptions = danmakuSendModeOptions().map { it.value },
            fontSizeOptions = danmakuSendFontSizeOptions().map { it.value },
        )
        assertEquals(DANMAKU_SEND_VIP_GRADUAL_COLOR, selection.color)
        assertEquals(5, selection.mode)
        assertEquals(18, selection.fontSize)
    }
}
