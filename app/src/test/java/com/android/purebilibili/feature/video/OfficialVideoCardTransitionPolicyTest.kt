package com.android.purebilibili.feature.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialVideoCardTransitionPolicyTest {
    @Test
    fun boundsUseDeclaredCoordinateTokenBeforeDescription() {
        assertTrue(officialBoundsUseWindowCoordinates("rect|WINDOW|View bounds on screen"))
        assertFalse(officialBoundsUseWindowCoordinates("rect|SCREEN|Window-relative description"))
    }

    @Test
    fun legacyBoundsMetadataUsesProtocolDefaults() {
        assertFalse(officialBoundsUseWindowCoordinates(null))
        assertFalse(officialBoundsUseWindowCoordinates("rect"))
        assertFalse(officialBoundsUseWindowCoordinates("rect|View bounds"))
        assertTrue(officialBoundsUseWindowCoordinates("rect|window-relative bounds"))
    }

    @Test
    fun returnControlsRequireWritableParameterAndRespectDeclaredTypes() {
        assertFalse(supportsOfficialBackType(null, "VIEW_ANIM_NONE"))
        assertTrue(supportsOfficialBackType("int", "VIEW_ANIM_NONE"))
        val metadata = "int|VIEW_ANIM_DEFAULT, VIEW_ANIM_FOLLOW_SYSTEM|Back animation type"
        assertTrue(supportsOfficialBackType(metadata, "VIEW_ANIM_FOLLOW_SYSTEM"))
        assertFalse(supportsOfficialBackType(metadata, "VIEW_ANIM_NONE"))
    }
}
