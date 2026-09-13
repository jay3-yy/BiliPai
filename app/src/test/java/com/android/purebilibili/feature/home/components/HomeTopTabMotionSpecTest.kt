package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.motion.AppMotionEasing
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTopTabMotionSpecTest {
    @Test
    fun iosCapsule_usesSharedReversibleMorphCurve() {
        val spec = iosTopTabCapsuleMotionSpec<Float>()

        assertEquals(260, spec.durationMillis)
        assertEquals(AppMotionEasing.IosEaseInOut, spec.easing)
    }
}
