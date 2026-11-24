package com.android.purebilibili.core.input.gamepad

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamepadAxisNormalizerTest {

    @Test
    fun stickInsideRadialDeadzoneReturnsZero() {
        val result = normalizeRadialStick(rawX = 0.10f, rawY = -0.09f, deadzone = 0.15f)
        assertEquals(0f, result.x)
        assertEquals(0f, result.y)
        assertFalse(result.isActive)
    }

    @Test
    fun stickAtFullDeflectionReachesUnitMagnitudeAndKeepsDirection() {
        val right = normalizeRadialStick(rawX = 1.0f, rawY = 0f, deadzone = 0.15f)
        assertTrue(right.isActive)
        assertTrue(abs(right.x - 1.0f) < 1e-4f)
        assertEquals(0f, right.y)

        val diagonal = normalizeRadialStick(rawX = 0.8f, rawY = 0.8f, deadzone = 0.15f)
        assertTrue(diagonal.isActive)
        assertTrue(abs(diagonal.x - diagonal.y) < 1e-4f)
        assertTrue(diagonal.x > 0.6f)
    }

    @Test
    fun precisionAimSlowsCursorMovementWithoutChangingSign() {
        val vector = NormalizedStickVector(x = 0.5f, y = -0.25f)
        val (normalDx, normalDy) = computeCursorStepPx(
            vector = vector,
            deltaSeconds = 1f / 60f,
            density = 2f,
            sensitivity = 1.0f,
            precisionAimHeld = false,
        )
        val (precisionDx, precisionDy) = computeCursorStepPx(
            vector = vector,
            deltaSeconds = 1f / 60f,
            density = 2f,
            sensitivity = 1.0f,
            precisionAimHeld = true,
        )

        assertTrue(normalDx > 0f && precisionDx > 0f)
        assertTrue(normalDy < 0f && precisionDy < 0f)
        assertTrue(precisionDx < normalDx * 0.4f)
        assertTrue(abs(precisionDy) < abs(normalDy) * 0.4f)
    }

    @Test
    fun triggerResolutionCombinesPrimaryAndFallbackAxesWithHysteresis() {
        assertEquals(0.8f, resolveTriggerValue(primaryAxis = 0f, fallbackAxis = 0.8f))
        assertEquals(0.7f, resolveTriggerValue(primaryAxis = 0.7f, fallbackAxis = 0.2f))

        assertFalse(resolveTriggerPressed(triggerValue = 0.45f, currentlyPressed = false))
        assertTrue(resolveTriggerPressed(triggerValue = 0.60f, currentlyPressed = false))
        assertTrue(resolveTriggerPressed(triggerValue = 0.40f, currentlyPressed = true))
        assertFalse(resolveTriggerPressed(triggerValue = 0.20f, currentlyPressed = true))
    }

    @Test
    fun sonyMinusOneToOneTriggerAxisNormalizesToZeroOne() {
        assertEquals(0f, normalizeAxisToUnitRange(value = -1.0f, min = -1.0f, max = 1.0f))
        assertEquals(0.5f, normalizeAxisToUnitRange(value = 0.0f, min = -1.0f, max = 1.0f))
        assertEquals(1.0f, normalizeAxisToUnitRange(value = 1.0f, min = -1.0f, max = 1.0f))
        assertEquals(1.0f, resolveMultiControllerTriggerValue(analogValue = 0f, digitalPressed = true))
        assertEquals(0.4f, resolveMultiControllerTriggerValue(analogValue = 0.4f, digitalPressed = false))
    }

    @Test
    fun detectControllerProfileIdentifiesXboxPlayStationSwitchAndGeneric() {
        assertEquals(
            GamepadControllerProfile.XBOX,
            detectControllerProfile(vendorId = 0x045E, productId = 0x0B13, deviceName = "Xbox Wireless Controller"),
        )
        assertEquals(
            GamepadControllerProfile.PLAYSTATION,
            detectControllerProfile(vendorId = 0x054C, productId = 0x0CE6, deviceName = "DualSense Wireless Controller"),
        )
        assertEquals(
            GamepadControllerProfile.PLAYSTATION,
            detectControllerProfile(vendorId = 0x054C, productId = 0x09CC, deviceName = "Wireless Controller"),
        )
        assertEquals(
            GamepadControllerProfile.PLAYSTATION,
            detectControllerProfile(vendorId = 0, productId = 0, deviceName = "PS4 DualShock 4"),
        )
        assertEquals(
            GamepadControllerProfile.SWITCH,
            detectControllerProfile(vendorId = 0x057E, productId = 0x2009, deviceName = "Nintendo Switch Pro Controller"),
        )
        assertEquals(
            GamepadControllerProfile.GENERIC,
            detectControllerProfile(vendorId = 0x3537, productId = 0x1001, deviceName = "GameSir G8 Galileo"),
        )
    }

    @Test
    fun buttonLabelsAdaptToControllerBrandGlyphs() {
        assertEquals("A", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_A, GamepadControllerProfile.XBOX))
        assertEquals("LB", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_L1, GamepadControllerProfile.XBOX))
        assertEquals("LT" to "RT", formatTriggerPairLabels(GamepadControllerProfile.XBOX))

        assertEquals("✕", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_A, GamepadControllerProfile.PLAYSTATION))
        assertEquals("○", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_B, GamepadControllerProfile.PLAYSTATION))
        assertEquals("□", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_X, GamepadControllerProfile.PLAYSTATION))
        assertEquals("△", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_Y, GamepadControllerProfile.PLAYSTATION))
        assertEquals("L1", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_L1, GamepadControllerProfile.PLAYSTATION))
        assertEquals("L2" to "R2", formatTriggerPairLabels(GamepadControllerProfile.PLAYSTATION))

        assertEquals("A", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_B, GamepadControllerProfile.SWITCH))
        assertEquals("B", formatGamepadKeyBadge(GamepadKeyCodes.BUTTON_A, GamepadControllerProfile.SWITCH))
        assertEquals("ZL" to "ZR", formatTriggerPairLabels(GamepadControllerProfile.SWITCH))
    }

    @Test
    fun switchAutoSwapMapsPhysicalEastToConfirmAndSouthToBack() {
        val config = GamepadConfig(autoSwapSwitchConfirmBack = true)
        val confirmKey = resolveBoundKeyCode(config, GamepadActionSlot.CONFIRM, GamepadControllerProfile.SWITCH)
        val backKey = resolveBoundKeyCode(config, GamepadActionSlot.BACK, GamepadControllerProfile.SWITCH)

        assertEquals(GamepadKeyCodes.BUTTON_B, confirmKey)
        assertEquals(GamepadKeyCodes.BUTTON_A, backKey)
        assertEquals("A", formatGamepadKeyBadge(confirmKey, GamepadControllerProfile.SWITCH))
        assertEquals("B", formatGamepadKeyBadge(backKey, GamepadControllerProfile.SWITCH))
    }

    @Test
    fun customBindingSwapsSameScopeConflictsAndAllowsCrossScopeSharing() {
        val base = GamepadConfig()
        // X (BUTTON_X) is OPEN_SEARCH in BROWSING and TOGGLE_DANMAKU in PLAYER.
        // Rebinding OPEN_SEARCH to BUTTON_Y should swap with REFRESH_OR_TOP (also BROWSING), without touching PLAYER.
        val conflict = findConflictingSlot(
            config = base,
            targetSlot = GamepadActionSlot.OPEN_SEARCH,
            keyCode = GamepadKeyCodes.BUTTON_Y,
            effectiveProfile = GamepadControllerProfile.XBOX,
        )
        assertEquals(GamepadActionSlot.REFRESH_OR_TOP, conflict)

        val updated = applyCustomBinding(
            config = base,
            targetSlot = GamepadActionSlot.OPEN_SEARCH,
            newKeyCode = GamepadKeyCodes.BUTTON_Y,
            effectiveProfile = GamepadControllerProfile.XBOX,
        )
        assertEquals(
            GamepadKeyCodes.BUTTON_Y,
            resolveBoundKeyCode(updated, GamepadActionSlot.OPEN_SEARCH, GamepadControllerProfile.XBOX),
        )
        assertEquals(
            GamepadKeyCodes.BUTTON_X,
            resolveBoundKeyCode(updated, GamepadActionSlot.REFRESH_OR_TOP, GamepadControllerProfile.XBOX),
        )
        // Player scope remains untouched
        assertEquals(
            GamepadKeyCodes.BUTTON_Y,
            resolveBoundKeyCode(updated, GamepadActionSlot.TOGGLE_FULLSCREEN, GamepadControllerProfile.XBOX),
        )
        assertEquals(
            GamepadActionSlot.OPEN_SEARCH,
            resolveActionSlotForKey(updated, GamepadKeyCodes.BUTTON_Y, inVideo = false, GamepadControllerProfile.XBOX),
        )
        assertEquals(
            GamepadActionSlot.TOGGLE_FULLSCREEN,
            resolveActionSlotForKey(updated, GamepadKeyCodes.BUTTON_Y, inVideo = true, GamepadControllerProfile.XBOX),
        )
    }

    @Test
    fun cannotUnbindConfirmOrBackButCanUnbindOptionalAndResetSlot() {
        val base = GamepadConfig()
        val afterConfirmClear = clearCustomBinding(base, GamepadActionSlot.CONFIRM, GamepadControllerProfile.XBOX)
        assertEquals(
            GamepadKeyCodes.BUTTON_A,
            resolveBoundKeyCode(afterConfirmClear, GamepadActionSlot.CONFIRM, GamepadControllerProfile.XBOX),
        )

        val afterHudClear = clearCustomBinding(base, GamepadActionSlot.TOGGLE_HUD, GamepadControllerProfile.XBOX)
        assertEquals(
            GamepadKeyCodes.UNBOUND,
            resolveBoundKeyCode(afterHudClear, GamepadActionSlot.TOGGLE_HUD, GamepadControllerProfile.XBOX),
        )
        assertNull(
            resolveActionSlotForKey(
                afterHudClear,
                GamepadKeyCodes.BUTTON_THUMBR,
                inVideo = false,
                GamepadControllerProfile.XBOX,
            ),
        )

        val afterReset = resetCustomBinding(afterHudClear, GamepadActionSlot.TOGGLE_HUD)
        assertEquals(
            GamepadKeyCodes.BUTTON_THUMBR,
            resolveBoundKeyCode(afterReset, GamepadActionSlot.TOGGLE_HUD, GamepadControllerProfile.XBOX),
        )
    }

    @Test
    fun v1PersistedGamepadConfigJsonDecodesCleanlyWithV2Defaults() {
        val v1Json = """{"defaultMode":"CURSOR","showLegendHud":false,"rtBoostSpeed":2.5}"""
        val parser = Json { ignoreUnknownKeys = true }
        val decoded = parser.decodeFromString<GamepadConfig>(v1Json)

        assertNotNull(decoded)
        assertFalse(decoded.showLegendHud)
        assertEquals(2.5f, decoded.rtBoostSpeed)
        assertEquals(GamepadControllerProfile.AUTO, decoded.controllerProfileOverride)
        assertEquals(10, decoded.seekStepSeconds)
        assertTrue(decoded.customBindings.isEmpty())
    }

    @Test
    fun directionalRepeatTrackerEmitsImmediatelyThenRepeatsAtInterval() {
        val tracker = DirectionalRepeatTracker(initialDelayMs = 300L, repeatIntervalMs = 100L)

        assertEquals(GamepadDirection.DOWN, tracker.update(GamepadDirection.DOWN, nowMs = 1000L))
        assertNull(tracker.update(GamepadDirection.DOWN, nowMs = 1200L))
        assertEquals(GamepadDirection.DOWN, tracker.update(GamepadDirection.DOWN, nowMs = 1300L))
        assertNull(tracker.update(GamepadDirection.DOWN, nowMs = 1350L))
        assertEquals(GamepadDirection.DOWN, tracker.update(GamepadDirection.DOWN, nowMs = 1400L))
        assertEquals(GamepadDirection.LEFT, tracker.update(GamepadDirection.LEFT, nowMs = 1450L))
        assertNull(tracker.update(null, nowMs = 1500L))
    }

    @Test
    fun dominantDirectionResolvesCardinalDirectionsFromHatAxes() {
        assertNull(resolveDominantDirection(0.2f, -0.3f))
        assertEquals(GamepadDirection.LEFT, resolveDominantDirection(-1f, 0f))
        assertEquals(GamepadDirection.RIGHT, resolveDominantDirection(0.9f, 0.2f))
        assertEquals(GamepadDirection.UP, resolveDominantDirection(0.1f, -0.8f))
        assertEquals(GamepadDirection.DOWN, resolveDominantDirection(0.2f, 0.85f))
    }

    @Test
    fun lockedScrollVectorEliminatesCrossAxisJitterAndSupportsAxisSwitch() {
        val (axis1, vec1) = resolveLockedScrollVector(rawX = 0.18f, rawY = 0.72f, currentAxis = null)
        assertEquals(GamepadScrollAxis.VERTICAL, axis1)
        assertEquals(0f, vec1.x)
        assertEquals(0.72f, vec1.y)

        // Moderate horizontal drift while scrolling vertically stays locked to vertical
        val (axis2, vec2) = resolveLockedScrollVector(rawX = 0.45f, rawY = 0.35f, currentAxis = axis1)
        assertEquals(GamepadScrollAxis.VERTICAL, axis2)
        assertEquals(0f, vec2.x)

        // Decisive horizontal push switches cleanly to horizontal
        val (axis3, vec3) = resolveLockedScrollVector(rawX = 0.85f, rawY = 0.15f, currentAxis = axis2)
        assertEquals(GamepadScrollAxis.HORIZONTAL, axis3)
        assertEquals(0.85f, vec3.x)
        assertEquals(0f, vec3.y)

        val (dx, dy) = computeScrollDragStepPx(
            lockedVector = NormalizedStickVector(x = 0f, y = 1.0f),
            deltaSeconds = 1f / 60f,
            density = 2f,
            scrollSensitivity = 1.0f,
            invertVertical = false,
        )
        assertEquals(0f, dx)
        assertTrue(dy < 0f)

        val (_, invertedDy) = computeScrollDragStepPx(
            lockedVector = NormalizedStickVector(x = 0f, y = 1.0f),
            deltaSeconds = 1f / 60f,
            density = 2f,
            scrollSensitivity = 1.0f,
            invertVertical = true,
        )
        assertTrue(invertedDy > 0f)
    }
}
