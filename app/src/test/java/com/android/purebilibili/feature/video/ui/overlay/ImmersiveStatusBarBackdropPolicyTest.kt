package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImmersiveStatusBarBackdropPolicyTest {


    @Test
    fun `ambient capture balances visual freshness with low cpu and battery overhead`() {
        assertTrue(VIDEO_STATUS_BAR_AMBIENT_CAPTURE_INTERVAL_MS >= 500L)
        assertEquals(96, VIDEO_STATUS_BAR_AMBIENT_SAMPLE_WIDTH_PX)
        assertEquals(54, VIDEO_STATUS_BAR_AMBIENT_SAMPLE_HEIGHT_PX)
    }

}
