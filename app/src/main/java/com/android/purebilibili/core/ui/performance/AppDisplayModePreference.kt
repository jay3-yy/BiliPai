package com.android.purebilibili.core.ui.performance

import android.app.Activity
import android.os.Build
import android.view.Display
import java.util.Locale
import kotlin.math.abs

internal const val SYSTEM_AUTO_DISPLAY_MODE_ID = 0

internal data class AppDisplayMode(
    val modeId: Int,
    val refreshRate: Float,
    val width: Int,
    val height: Int,
)

/**
 * Returns the display modes exposed by Android. Mode `0` is deliberately not synthesized here:
 * it represents PiliPlus-style "auto", where the app releases its display-mode preference.
 */
internal fun Activity.supportedAppDisplayModes(): List<AppDisplayMode> {
    val targetDisplay = currentActivityDisplay() ?: return emptyList()
    return targetDisplay.supportedModes
        .map { mode ->
            AppDisplayMode(
                modeId = mode.modeId,
                refreshRate = mode.refreshRate,
                width = mode.physicalWidth,
                height = mode.physicalHeight,
            )
        }
        .sortedWith(
            compareByDescending<AppDisplayMode> { it.refreshRate }
                .thenByDescending { it.width * it.height }
                .thenBy { it.modeId },
        )
}

internal fun normalizePreferredDisplayModeId(
    preferredModeId: Int,
    supportedModes: List<AppDisplayMode>,
): Int = preferredModeId.takeIf { requestedId ->
    requestedId != SYSTEM_AUTO_DISPLAY_MODE_ID &&
        supportedModes.any { mode -> mode.modeId == requestedId }
} ?: SYSTEM_AUTO_DISPLAY_MODE_ID

internal fun displayModePreferenceLabel(mode: AppDisplayMode): String {
    val roundedRate = mode.refreshRate.toInt()
    val rateLabel = if (abs(mode.refreshRate - roundedRate) < 0.05f) {
        roundedRate.toString()
    } else {
        String.format(Locale.ROOT, "%.2f", mode.refreshRate).trimEnd('0').trimEnd('.')
    }
    return "$rateLabel Hz · ${mode.width} × ${mode.height}"
}

/** Applies one explicit user preference. No touch boost, timer, or idle refresh-rate vote. */
internal fun Activity.applyPreferredDisplayMode(preferredModeId: Int) {
    val resolvedModeId = normalizePreferredDisplayModeId(
        preferredModeId = preferredModeId,
        supportedModes = supportedAppDisplayModes(),
    )
    val currentAttrs = window.attributes
    if (currentAttrs.preferredDisplayModeId == resolvedModeId && currentAttrs.preferredRefreshRate == 0f) {
        return
    }
    window.attributes = currentAttrs.apply {
        preferredDisplayModeId = resolvedModeId
        preferredRefreshRate = 0f
    }
}

private fun Activity.currentActivityDisplay(): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay
    }
