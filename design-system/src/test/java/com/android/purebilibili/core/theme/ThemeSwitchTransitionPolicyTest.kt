package com.android.purebilibili.core.theme

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeSwitchTransitionPolicyTest {

    @Test
    fun `storage parsing falls back to radial reveal for unknown values`() {
        assertEquals(
            ThemeSwitchTransitionStyle.RADIAL_REVEAL,
            ThemeSwitchTransitionStyle.fromStorageValue("radial_reveal"),
        )
        assertEquals(
            ThemeSwitchTransitionStyle.CROSSFADE,
            ThemeSwitchTransitionStyle.fromStorageValue("crossfade"),
        )
        assertEquals(
            ThemeSwitchTransitionStyle.NONE,
            ThemeSwitchTransitionStyle.fromStorageValue("none"),
        )
        assertEquals(
            ThemeSwitchTransitionStyle.RADIAL_REVEAL,
            ThemeSwitchTransitionStyle.fromStorageValue("unknown_value"),
        )
        assertEquals(
            ThemeSwitchTransitionStyle.RADIAL_REVEAL,
            ThemeSwitchTransitionStyle.fromStorageValue(null),
        )
    }

    @Test
    fun `reduced motion or disabled animations force immediate switch`() {
        assertEquals(
            ThemeSwitchTransitionStyle.NONE,
            resolveEffectiveThemeSwitchTransitionStyle(
                preferred = ThemeSwitchTransitionStyle.RADIAL_REVEAL,
                animationsEnabled = false,
                systemReduceMotion = false,
            ),
        )
        assertEquals(
            ThemeSwitchTransitionStyle.NONE,
            resolveEffectiveThemeSwitchTransitionStyle(
                preferred = ThemeSwitchTransitionStyle.CROSSFADE,
                animationsEnabled = true,
                systemReduceMotion = true,
            ),
        )
        assertEquals(
            ThemeSwitchTransitionStyle.RADIAL_REVEAL,
            resolveEffectiveThemeSwitchTransitionStyle(
                preferred = ThemeSwitchTransitionStyle.RADIAL_REVEAL,
                animationsEnabled = true,
                systemReduceMotion = false,
            ),
        )
    }

    @Test
    fun `radial reveal max radius reaches farthest corner`() {
        assertEquals(500f, resolveRadialRevealMaxRadius(Offset(0f, 0f), 300f, 400f), 0.01f)
        assertEquals(250f, resolveRadialRevealMaxRadius(Offset(150f, 200f), 300f, 400f), 0.01f)
        assertEquals(0f, resolveRadialRevealMaxRadius(Offset(10f, 10f), 0f, 400f), 0.01f)
    }

    @Test
    fun `snapshot overlay alpha reaches zero at completion`() {
        assertEquals(1f, resolveSnapshotOverlayAlpha(ThemeSwitchTransitionStyle.CROSSFADE, 0f), 0.001f)
        assertEquals(0.5f, resolveSnapshotOverlayAlpha(ThemeSwitchTransitionStyle.CROSSFADE, 0.5f), 0.001f)
        assertEquals(0f, resolveSnapshotOverlayAlpha(ThemeSwitchTransitionStyle.CROSSFADE, 1f), 0.001f)

        assertEquals(1f, resolveSnapshotOverlayAlpha(ThemeSwitchTransitionStyle.RADIAL_REVEAL, 0.8f), 0.001f)
        assertEquals(0f, resolveSnapshotOverlayAlpha(ThemeSwitchTransitionStyle.RADIAL_REVEAL, 1f), 0.001f)
        assertEquals(0f, resolveSnapshotOverlayAlpha(ThemeSwitchTransitionStyle.NONE, 0f), 0.001f)
    }
}
