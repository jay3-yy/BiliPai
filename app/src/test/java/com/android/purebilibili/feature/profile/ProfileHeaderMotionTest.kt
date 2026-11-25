package com.android.purebilibili.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileHeaderMotionTest {
    @Test
    fun `small pull toward either boundary settles fully open`() {
        assertEquals(0f, resolveProfileHeaderSnapOffset(50f, 250f, 0f))
        assertEquals(0f, resolveProfileHeaderSnapOffset(50f, 250f, -800f))
    }

    @Test
    fun `less than sixty percent remaining or fast upward fling settles closed`() {
        assertEquals(250f, resolveProfileHeaderSnapOffset(125f, 250f, 0f))
        assertEquals(250f, resolveProfileHeaderSnapOffset(20f, 250f, 1001f))
        assertEquals(0f, resolveProfileHeaderSnapOffset(20f, 250f, -1001f))
    }

    @Test
    fun `fully open closed and scrolled content are not snapped again`() {
        assertNull(resolveProfileHeaderSnapOffset(0f, 250f, 0f))
        assertNull(resolveProfileHeaderSnapOffset(250f, 250f, 0f))
        assertNull(resolveProfileHeaderSnapOffset(400f, 250f, -800f))
        assertNull(resolveProfileHeaderSnapOffset(20f, 0f, 0f))
    }

    @Test
    fun `progress stays bounded and is proportional across window sizes`() {
        assertEquals(1f, resolveProfileHeaderExpandedFraction(-20f, 250f))
        assertEquals(0f, resolveProfileHeaderExpandedFraction(400f, 250f))
        assertEquals(0.5f, resolveProfileHeaderExpandedFraction(125f, 250f))
        assertEquals(0.5f, resolveProfileHeaderExpandedFraction(200f, 400f))
    }

    @Test
    fun `avatar cutout bridge retracts and silhouette fades before full collapse`() {
        assertEquals(0f, ProfileAvatarFusionView.resolveGooeyBridgeFactor(0f), 0.001f)
        assertEquals(1f, ProfileAvatarFusionView.resolveGooeyBridgeFactor(0.5f), 0.001f)
        assertEquals(0f, ProfileAvatarFusionView.resolveGooeyBridgeFactor(0.85f), 0.001f)
        assertEquals(0f, ProfileAvatarFusionView.resolveGooeyBridgeFactor(0.99f), 0.001f)

        assertEquals(255, ProfileAvatarFusionView.resolveGooeyLayerAlpha(0.75f, false))
        assertEquals(0, ProfileAvatarFusionView.resolveGooeyLayerAlpha(0.96f, false))
        assertEquals(0, ProfileAvatarFusionView.resolveGooeyLayerAlpha(0.998f, false))
        assertEquals(255, ProfileAvatarFusionView.resolveGooeyLayerAlpha(0.96f, true))
    }
}
