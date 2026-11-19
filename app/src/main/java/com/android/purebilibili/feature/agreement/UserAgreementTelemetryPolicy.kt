package com.android.purebilibili.feature.agreement

import android.content.Context
import com.android.purebilibili.core.store.DEFAULT_ANALYTICS_ENABLED
import com.android.purebilibili.core.store.DEFAULT_CRASH_TRACKING_ENABLED

data class UserAgreementTelemetryChoice(
    val crashTrackingEnabled: Boolean,
    val analyticsEnabled: Boolean,
)

internal fun resolveInitialUserAgreementTelemetryChoice(
    storedCrashTracking: Boolean?,
    storedAnalytics: Boolean?,
    defaultCrashTracking: Boolean = DEFAULT_CRASH_TRACKING_ENABLED,
    defaultAnalytics: Boolean = DEFAULT_ANALYTICS_ENABLED,
): UserAgreementTelemetryChoice = UserAgreementTelemetryChoice(
    crashTrackingEnabled = storedCrashTracking ?: defaultCrashTracking,
    analyticsEnabled = storedAnalytics ?: defaultAnalytics,
)

internal fun shouldEnableStartupTelemetry(
    agreementAccepted: Boolean,
    preferenceEnabled: Boolean,
): Boolean = agreementAccepted && preferenceEnabled

internal fun readInitialUserAgreementTelemetryChoice(
    context: Context,
): UserAgreementTelemetryChoice {
    val crashPrefs = context.getSharedPreferences("crash_tracking", Context.MODE_PRIVATE)
    val analyticsPrefs = context.getSharedPreferences("analytics_tracking", Context.MODE_PRIVATE)
    return resolveInitialUserAgreementTelemetryChoice(
        storedCrashTracking = if (crashPrefs.contains("enabled")) {
            crashPrefs.getBoolean("enabled", DEFAULT_CRASH_TRACKING_ENABLED)
        } else {
            null
        },
        storedAnalytics = if (analyticsPrefs.contains("enabled")) {
            analyticsPrefs.getBoolean("enabled", DEFAULT_ANALYTICS_ENABLED)
        } else {
            null
        },
    )
}
