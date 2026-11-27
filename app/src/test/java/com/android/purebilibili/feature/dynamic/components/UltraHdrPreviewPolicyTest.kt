package com.android.purebilibili.feature.dynamic.components

import android.content.pm.ActivityInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class UltraHdrPreviewPolicyTest {

    @Test
    fun enablesHdrColorModeOnlyOnApi34PlusWithActiveGainmapAndNonDismissing() {
        assertEquals(
            ActivityInfo.COLOR_MODE_HDR,
            resolveUltraHdrWindowColorMode(
                hasGainmap = true,
                boostEnabled = true,
                isDismissing = false,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            ),
        )
        assertEquals(
            ActivityInfo.COLOR_MODE_DEFAULT,
            resolveUltraHdrWindowColorMode(
                hasGainmap = false,
                boostEnabled = true,
                isDismissing = false,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            ),
        )
        assertEquals(
            ActivityInfo.COLOR_MODE_DEFAULT,
            resolveUltraHdrWindowColorMode(
                hasGainmap = true,
                boostEnabled = false,
                isDismissing = false,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            ),
        )
        assertEquals(
            ActivityInfo.COLOR_MODE_DEFAULT,
            resolveUltraHdrWindowColorMode(
                hasGainmap = true,
                boostEnabled = true,
                isDismissing = true,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            ),
        )
        assertEquals(
            ActivityInfo.COLOR_MODE_DEFAULT,
            resolveUltraHdrWindowColorMode(
                hasGainmap = true,
                boostEnabled = true,
                isDismissing = false,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
            ),
        )
    }
}
