package com.android.purebilibili.feature.agreement

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserAgreementTelemetryPolicyTest {

    @Test
    fun resolveInitialChoice_usesDefaultsWhenUnset() {
        val choice = resolveInitialUserAgreementTelemetryChoice(
            storedCrashTracking = null,
            storedAnalytics = null,
        )

        assertEquals(
            UserAgreementTelemetryChoice(
                crashTrackingEnabled = true,
                analyticsEnabled = true,
            ),
            choice,
        )
    }

    @Test
    fun resolveInitialChoice_preservesExistingOptOutOnRePrompt() {
        val choice = resolveInitialUserAgreementTelemetryChoice(
            storedCrashTracking = false,
            storedAnalytics = false,
        )

        assertFalse(choice.crashTrackingEnabled)
        assertFalse(choice.analyticsEnabled)
    }

    @Test
    fun shouldEnableStartupTelemetry_requiresAgreementAcceptance() {
        assertFalse(shouldEnableStartupTelemetry(agreementAccepted = false, preferenceEnabled = true))
        assertFalse(shouldEnableStartupTelemetry(agreementAccepted = false, preferenceEnabled = false))
        assertFalse(shouldEnableStartupTelemetry(agreementAccepted = true, preferenceEnabled = false))
        assertTrue(shouldEnableStartupTelemetry(agreementAccepted = true, preferenceEnabled = true))
    }

    @Test
    fun firstLaunchConsentAndOnboardingNotice_followMergedFlowAndReadOnlyReviewDesign() {
        val homeSource = loadSource("app/src/main/java/com/android/purebilibili/feature/home/HomeScreen.kt")
        val appSource = loadSource("app/src/main/java/com/android/purebilibili/app/PureApplication.kt")
        val onboardingSource = loadSource(
            "app/src/main/java/com/android/purebilibili/feature/onboarding/OnboardingScreen.kt"
        )

        assertFalse(homeSource.contains("HomeConsentDialogHost"))
        assertTrue(appSource.contains("UserAgreementStore.isAccepted"))
        assertTrue(onboardingSource.contains("SettingsPageScaffold("))
        assertFalse(onboardingSource.contains("finishAffinity()"))
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        return listOf(File(path), File(normalizedPath), File("../$path"))
            .firstOrNull(File::exists)
            ?.readText()
            ?: error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
