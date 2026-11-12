package com.android.purebilibili.core.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.Surface
import androidx.window.layout.WindowMetricsCalculator

internal const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

enum class AppFoldableDisplayRole {
    Standard,
    Cover,
    Inner,
    UnknownFoldable,
}

enum class AppDisplayNaturalOrientation {
    Portrait,
    Landscape,
    Unknown,
}

enum class AppFoldableDetectionBasis {
    None,
    CurrentFoldingFeature,
    HingeAngleSensor,
    WindowMetricsFallback,
}

data class AppDisplayContext(
    val currentWindowWidthDp: Int,
    val currentWindowHeightDp: Int,
    val maximumWindowWidthDp: Int? = null,
    val maximumWindowHeightDp: Int? = null,
    val displayModeWidthPx: Int? = null,
    val displayModeHeightPx: Int? = null,
    val displayRotation: Int? = null,
    val foldableDisplayRole: AppFoldableDisplayRole = AppFoldableDisplayRole.Standard,
    val naturalOrientation: AppDisplayNaturalOrientation = AppDisplayNaturalOrientation.Unknown,
    val detectionBasis: AppFoldableDetectionBasis = AppFoldableDetectionBasis.None,
    val isInMultiWindowMode: Boolean = false,
    val smallestScreenWidthDp: Int = 0,
) {
    val isFoldableCoverWindow: Boolean
        get() = foldableDisplayRole == AppFoldableDisplayRole.Cover

    val isKnownFoldableDevice: Boolean
        get() = foldableDisplayRole != AppFoldableDisplayRole.Standard

    val usesInWindowFullscreen: Boolean
        get() = isFoldableCoverWindow &&
            naturalOrientation == AppDisplayNaturalOrientation.Landscape
}

internal data class AppDisplayContextInput(
    val currentWindowWidthDp: Int,
    val currentWindowHeightDp: Int,
    val maximumWindowWidthDp: Int? = null,
    val maximumWindowHeightDp: Int? = null,
    val configurationOrientation: Int = Configuration.ORIENTATION_UNDEFINED,
    val displayRotation: Int? = null,
    val displayModeWidthPx: Int? = null,
    val displayModeHeightPx: Int? = null,
    val hasCurrentFoldingFeature: Boolean = false,
    val hasHingeAngleSensor: Boolean = false,
    val isInMultiWindowMode: Boolean = false,
    val smallestScreenWidthDp: Int = 0,
)

internal fun resolveAppDisplayContext(input: AppDisplayContextInput): AppDisplayContext {
    val currentShortSideDp = minOf(
        input.currentWindowWidthDp,
        input.currentWindowHeightDp,
    )
    val maximumShortSideDp = if (
        input.maximumWindowWidthDp != null &&
        input.maximumWindowHeightDp != null
    ) {
        minOf(input.maximumWindowWidthDp, input.maximumWindowHeightDp)
    } else {
        null
    }

    val (role, basis) = when {
        // A FoldingFeature exists only inside the current window, so this is direct evidence
        // that the activity is running on the foldable's inner display.
        input.hasCurrentFoldingFeature ->
            AppFoldableDisplayRole.Inner to AppFoldableDetectionBasis.CurrentFoldingFeature

        // Multi-window bounds do not describe a physical display. Keep the foldable identity,
        // but do not guess cover versus inner from a resized app window.
        input.hasHingeAngleSensor && input.isInMultiWindowMode ->
            AppFoldableDisplayRole.UnknownFoldable to AppFoldableDetectionBasis.HingeAngleSensor

        input.hasHingeAngleSensor && currentShortSideDp < LARGE_SCREEN_SMALLEST_WIDTH_DP ->
            AppFoldableDisplayRole.Cover to AppFoldableDetectionBasis.HingeAngleSensor

        input.hasHingeAngleSensor ->
            AppFoldableDisplayRole.Inner to AppFoldableDetectionBasis.HingeAngleSensor

        // Some vendor builds omit the hinge-angle feature. Retain the existing metrics signal
        // only as a low-confidence, full-window fallback; never use it in multi-window mode.
        !input.isInMultiWindowMode &&
            // 平板内容区可能被系统栏压到 600dp 以下，不能仅凭这个差值推断为外屏。
            input.smallestScreenWidthDp < LARGE_SCREEN_SMALLEST_WIDTH_DP &&
            maximumShortSideDp != null &&
            maximumShortSideDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP &&
            currentShortSideDp < LARGE_SCREEN_SMALLEST_WIDTH_DP ->
            AppFoldableDisplayRole.Cover to AppFoldableDetectionBasis.WindowMetricsFallback

        else -> AppFoldableDisplayRole.Standard to AppFoldableDetectionBasis.None
    }

    return AppDisplayContext(
        currentWindowWidthDp = input.currentWindowWidthDp,
        currentWindowHeightDp = input.currentWindowHeightDp,
        maximumWindowWidthDp = input.maximumWindowWidthDp,
        maximumWindowHeightDp = input.maximumWindowHeightDp,
        displayModeWidthPx = input.displayModeWidthPx,
        displayModeHeightPx = input.displayModeHeightPx,
        displayRotation = input.displayRotation,
        foldableDisplayRole = role,
        naturalOrientation = resolveDisplayNaturalOrientation(
            configurationOrientation = input.configurationOrientation,
            displayRotation = input.displayRotation,
            displayModeWidthPx = input.displayModeWidthPx,
            displayModeHeightPx = input.displayModeHeightPx,
        ),
        detectionBasis = basis,
        isInMultiWindowMode = input.isInMultiWindowMode,
        smallestScreenWidthDp = input.smallestScreenWidthDp,
    )
}

internal fun resolveDisplayNaturalOrientation(
    configurationOrientation: Int,
    displayRotation: Int?,
    displayModeWidthPx: Int? = null,
    displayModeHeightPx: Int? = null,
): AppDisplayNaturalOrientation {
    val fromRotation = when (displayRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> when (configurationOrientation) {
            Configuration.ORIENTATION_LANDSCAPE -> AppDisplayNaturalOrientation.Landscape
            Configuration.ORIENTATION_PORTRAIT -> AppDisplayNaturalOrientation.Portrait
            else -> AppDisplayNaturalOrientation.Unknown
        }

        Surface.ROTATION_90,
        Surface.ROTATION_270 -> when (configurationOrientation) {
            Configuration.ORIENTATION_LANDSCAPE -> AppDisplayNaturalOrientation.Portrait
            Configuration.ORIENTATION_PORTRAIT -> AppDisplayNaturalOrientation.Landscape
            else -> AppDisplayNaturalOrientation.Unknown
        }

        else -> AppDisplayNaturalOrientation.Unknown
    }

    if (fromRotation != AppDisplayNaturalOrientation.Unknown) {
        return fromRotation
    }

    if (
        displayModeWidthPx != null &&
        displayModeHeightPx != null &&
        displayModeWidthPx > 0 &&
        displayModeHeightPx > 0 &&
        displayModeWidthPx != displayModeHeightPx
    ) {
        return if (displayModeWidthPx > displayModeHeightPx) {
            AppDisplayNaturalOrientation.Landscape
        } else {
            AppDisplayNaturalOrientation.Portrait
        }
    }

    return AppDisplayNaturalOrientation.Unknown
}

internal fun isLandscapeNaturalDisplay(
    configurationOrientation: Int,
    displayRotation: Int?,
    displayModeWidthPx: Int? = null,
    displayModeHeightPx: Int? = null,
): Boolean = resolveDisplayNaturalOrientation(
    configurationOrientation = configurationOrientation,
    displayRotation = displayRotation,
    displayModeWidthPx = displayModeWidthPx,
    displayModeHeightPx = displayModeHeightPx,
) == AppDisplayNaturalOrientation.Landscape

@Suppress("DEPRECATION")
internal fun Activity.resolveAppDisplayContext(
    configuration: Configuration = resources.configuration,
    hasCurrentFoldingFeature: Boolean = false,
): AppDisplayContext {
    val density = resources.displayMetrics.density.coerceAtLeast(1f)
    val maximumBounds = runCatching {
        WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this).bounds
    }.getOrNull()
    val currentDisplay = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
        else windowManager.defaultDisplay
    }.getOrNull()
    val displayMode = runCatching { currentDisplay?.mode }.getOrNull()
    val hasHingeAngleSensor = hasFoldableHingeAngleSensor()

    return resolveAppDisplayContext(
        AppDisplayContextInput(
            currentWindowWidthDp = configuration.screenWidthDp,
            currentWindowHeightDp = configuration.screenHeightDp,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
            maximumWindowWidthDp = maximumBounds?.let { (it.width() / density).toInt() },
            maximumWindowHeightDp = maximumBounds?.let { (it.height() / density).toInt() },
            configurationOrientation = configuration.orientation,
            displayRotation = currentDisplay?.rotation,
            displayModeWidthPx = displayMode?.physicalWidth,
            displayModeHeightPx = displayMode?.physicalHeight,
            hasCurrentFoldingFeature = hasCurrentFoldingFeature,
            hasHingeAngleSensor = hasHingeAngleSensor,
            isInMultiWindowMode = isInMultiWindowMode,
        )
    )
}

internal fun Context.hasFoldableHingeAngleSensor(): Boolean = runCatching {
    packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
}.getOrDefault(false)

internal fun Context.isLargeScreenOrFoldableConfiguration(): Boolean {
    return resolveLargeScreenOrFoldableConfiguration(
        smallestScreenWidthDp = resources.configuration.smallestScreenWidthDp,
        hasHingeAngleSensor = hasFoldableHingeAngleSensor(),
    )
}

internal fun resolveLargeScreenOrFoldableConfiguration(
    smallestScreenWidthDp: Int,
    hasHingeAngleSensor: Boolean,
): Boolean = smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP || hasHingeAngleSensor
