package com.android.purebilibili.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSelectionPreferencePolicyTest {

    @Test
    fun `single choice only dispatches a changed value`() {
        assertFalse(shouldDispatchAppChoiceSelection("current", "current"))
        assertTrue(shouldDispatchAppChoiceSelection("current", "next"))
    }

    @Test
    fun `continuous slider clamps without snapping`() {
        assertEquals(0f, resolveAppSliderDialogValue(-1f, 0f..10f, steps = 0))
        assertEquals(3.25f, resolveAppSliderDialogValue(3.25f, 0f..10f, steps = 0))
        assertEquals(10f, resolveAppSliderDialogValue(12f, 0f..10f, steps = 0))
    }

    @Test
    fun `stepped slider snaps to the nearest tick`() {
        assertEquals(5f, resolveAppSliderDialogValue(4.6f, 0f..10f, steps = 3))
        assertEquals(7.5f, resolveAppSliderDialogValue(7.1f, 0f..10f, steps = 3))
    }

    @Test
    fun `invalid slider range resolves to its start`() {
        assertEquals(5f, resolveAppSliderDialogValue(9f, 5f..5f, steps = 4))
    }

    @Test
    fun singleChoicePreference_keepsMiuixSpinnerOnlyForMiuixStyleAndSharedRowOtherwise() {
        val source = listOf(
            java.io.File(
                "design-system/src/main/java/com/android/purebilibili/core/ui/components/AppSelectionPreferenceComponents.kt"
            ),
            java.io.File(
                "../design-system/src/main/java/com/android/purebilibili/core/ui/components/AppSelectionPreferenceComponents.kt"
            ),
            java.io.File(
                "src/main/java/com/android/purebilibili/core/ui/components/AppSelectionPreferenceComponents.kt"
            ),
        ).first { it.exists() }.readText()
        val start = source.indexOf("fun <T> AppSingleChoicePreference(")
        val end = source.indexOf("fun <T> AppSingleChoiceDialog(")
        assertTrue(start >= 0 && end > start, "AppSingleChoicePreference section markers missing")
        val section = source.substring(start, end)

        // WindowSpinner 仅限 MIUIX 预设的跟随弹出分支
        assertTrue(section.contains("presentation == AppSingleChoicePresentation.WINDOW_POPUP &&"))
        assertTrue(section.contains("LocalAppUiStyle.current == AppUiStyle.MIUIX"))
        assertTrue(section.contains("WindowSpinnerPreference("))
        assertTrue(section.contains("AppPreference("))
        assertTrue(section.contains("DropdownMenu("))
    }
}
