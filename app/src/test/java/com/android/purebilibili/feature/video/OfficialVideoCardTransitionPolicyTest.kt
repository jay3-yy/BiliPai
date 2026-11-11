package com.android.purebilibili.feature.video

import com.itgsa.animation.sharedview.ViewAnimationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialVideoCardTransitionPolicyTest {
    @Test
    fun oplusSdkDoesNotWriteIrreversibleReturnControlsBeforeFinish() {
        // 页面首帧身份未知、后续绑定就绪，都不能在关闭前写入返回控制。
        for (sourceAvailable in listOf(false, true)) {
            assertNull(
                resolveOfficialVideoCardReturnPlan(
                    metadata = "int",
                    oplusSdkWindow = true,
                    preparingToFinish = false,
                    motionEnabled = true,
                    sourceAvailable = sourceAvailable,
                    sourceRadiusUnchanged = true,
                    preferSystemPredictiveBack = true,
                ),
            )
        }
    }

    @Test
    fun oplusSdkPreservesNativeCardReturnAndOnlyUpdatesWritableGeometry() {
        val plan = requireNotNull(resolveOfficialVideoCardReturnPlan(
            metadata = "int",
            oplusSdkWindow = true,
            preparingToFinish = true,
            motionEnabled = true,
            sourceAvailable = true,
            sourceRadiusUnchanged = true,
            preferSystemPredictiveBack = true,
        ))
        assertEquals(ViewAnimationType.VIEW_ANIM_DEFAULT, plan.backAnimType)
        assertTrue(plan.useSourceBounds)
        assertFalse(plan.updateRadius)
        assertFalse(plan.updateEffects)
    }

    @Test
    fun oplusSdkRejectsStaleCardGeometryAtFinish() {
        // 来源已失效或圆角改变时，都不能把旧截图收回新形状。
        for ((sourceAvailable, sameRadius) in listOf(false to true, true to false)) {
            val plan = requireNotNull(resolveOfficialVideoCardReturnPlan(
                metadata = "int",
                oplusSdkWindow = true,
                preparingToFinish = true,
                motionEnabled = true,
                sourceAvailable = sourceAvailable,
                sourceRadiusUnchanged = sameRadius,
                preferSystemPredictiveBack = false,
            ))
            assertEquals(ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM, plan.backAnimType)
            assertFalse(plan.useSourceBounds)
        }
    }

    @Test
    fun oplusSdkHonorsReducedMotionAtFinish() {
        val plan = requireNotNull(resolveOfficialVideoCardReturnPlan(
            metadata = "int",
            oplusSdkWindow = true,
            preparingToFinish = true,
            motionEnabled = false,
            sourceAvailable = true,
            sourceRadiusUnchanged = true,
            preferSystemPredictiveBack = true,
        ))
        assertEquals(ViewAnimationType.VIEW_ANIM_NONE, plan.backAnimType)
        assertFalse(plan.useSourceBounds)
    }

    @Test
    fun otherSdkImplementationsKeepLiveOverridesAndPredictiveBackPolicy() {
        val plan = requireNotNull(resolveOfficialVideoCardReturnPlan(
            metadata = "int",
            oplusSdkWindow = false,
            preparingToFinish = false,
            motionEnabled = true,
            sourceAvailable = true,
            sourceRadiusUnchanged = false,
            preferSystemPredictiveBack = true,
        ))
        assertEquals(ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM, plan.backAnimType)
        assertTrue(plan.useSourceBounds)
        assertTrue(plan.updateRadius)
        assertTrue(plan.updateEffects)
    }

    @Test
    fun systemPredictiveReturnRequiresWritableFollowSystemAndKeepsMotionPolicy() {
        val supported = "int|VIEW_ANIM_DEFAULT,VIEW_ANIM_FOLLOW_SYSTEM,VIEW_ANIM_NONE|Back animation type"
        assertEquals(
            ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM,
            resolveOfficialReturnAnimType(supported, true, true, preferSystemPredictiveBack = true),
        )
        assertEquals(
            ViewAnimationType.VIEW_ANIM_DEFAULT,
            resolveOfficialReturnAnimType(supported, true, true, preferSystemPredictiveBack = false),
        )
        assertEquals(
            ViewAnimationType.VIEW_ANIM_NONE,
            resolveOfficialReturnAnimType(supported, false, true, preferSystemPredictiveBack = true),
        )
        assertEquals(
            ViewAnimationType.VIEW_ANIM_DEFAULT,
            resolveOfficialReturnAnimType(
                "int|VIEW_ANIM_DEFAULT|Back animation type", true, true, preferSystemPredictiveBack = true,
            ),
        )
        assertNull(resolveOfficialReturnAnimType(null, true, true, preferSystemPredictiveBack = true))
    }

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

    @Test
    fun missingSourceUsesSupportedExitWithoutCardTarget() {
        val supportedExits = listOf(
            "VIEW_ANIM_FOLLOW_SYSTEM,VIEW_ANIM_FADE_OUT,VIEW_ANIM_EXPLODE_OUT,VIEW_ANIM_NONE" to ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM,
            "VIEW_ANIM_FADE_OUT,VIEW_ANIM_EXPLODE_OUT,VIEW_ANIM_NONE" to ViewAnimationType.VIEW_ANIM_FADE_OUT,
            "VIEW_ANIM_EXPLODE_OUT,VIEW_ANIM_NONE" to ViewAnimationType.VIEW_ANIM_EXPLODE_OUT,
            "VIEW_ANIM_NONE" to ViewAnimationType.VIEW_ANIM_NONE,
        )
        for ((types, expected) in supportedExits) {
            assertEquals(
                types,
                expected,
                resolveOfficialReturnAnimType(
                    "int|VIEW_ANIM_DEFAULT,$types|Back animation type",
                    motionEnabled = true,
                    sourceAvailable = false,
                ),
            )
        }
    }

    @Test
    fun missingSourceDoesNotRequestDefaultCardReturn() {
        for (metadata in listOf(null, "int|VIEW_ANIM_DEFAULT|Back animation type")) {
            assertNull(resolveOfficialReturnAnimType(metadata, motionEnabled = true, sourceAvailable = false))
        }
        assertEquals(
            ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM,
            resolveOfficialReturnAnimType("int", motionEnabled = true, sourceAvailable = false),
        )
    }

    @Test
    fun reducedMotionDoesNotSelectFadeOrExplode() {
        val animatedExits = "VIEW_ANIM_FADE_OUT,VIEW_ANIM_EXPLODE_OUT"
        for (sourceAvailable in listOf(false, true)) {
            assertEquals(
                ViewAnimationType.VIEW_ANIM_NONE,
                resolveOfficialReturnAnimType("int", motionEnabled = false, sourceAvailable = sourceAvailable),
            )
            assertEquals(
                ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM,
                resolveOfficialReturnAnimType(
                    "int|VIEW_ANIM_FOLLOW_SYSTEM,$animatedExits|Back animation type",
                    motionEnabled = false,
                    sourceAvailable = sourceAvailable,
                ),
            )
            assertNull(
                resolveOfficialReturnAnimType(
                    "int|$animatedExits|Back animation type",
                    motionEnabled = false,
                    sourceAvailable = sourceAvailable,
                ),
            )
        }
    }

    @Test
    fun availableSourceUsesDefaultCardReturn() {
        assertEquals(
            ViewAnimationType.VIEW_ANIM_DEFAULT,
            resolveOfficialReturnAnimType(
                "int|VIEW_ANIM_DEFAULT,VIEW_ANIM_FADE_OUT|Back animation type",
                motionEnabled = true,
                sourceAvailable = true,
            ),
        )
    }
}
