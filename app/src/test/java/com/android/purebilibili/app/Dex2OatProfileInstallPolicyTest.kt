package com.android.purebilibili.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class Dex2OatProfileInstallPolicyTest {

    @Test
    fun profileInstall_enabledOnAndroidNougatAndAbove() {
        assertFalse(PureApplicationRuntimeConfig.shouldRequestDex2OatProfileInstall(sdkInt = 23))
        assertTrue(PureApplicationRuntimeConfig.shouldRequestDex2OatProfileInstall(sdkInt = 24))
        assertTrue(PureApplicationRuntimeConfig.shouldRequestDex2OatProfileInstall(sdkInt = 35))
    }

    @Test
    fun profileInstallDelay_usesConservativePostStartupWindow() {
        assertEquals(8_000L, PureApplicationRuntimeConfig.dex2OatProfileInstallDelayMs())
    }
}
