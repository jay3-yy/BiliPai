package com.android.purebilibili.feature.video.ui.overlay

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

internal val LocalPlayerControlsTimeoutSeconds = compositionLocalOf { 0 }

internal fun resolvePlayerControlsTimeoutMillis(selection: Int, defaultMillis: Long): Long? = when (selection) {
    -1 -> null
    8, 15, 30 -> selection * 1_000L
    else -> defaultMillis
}

internal data class PlayerControlsAutoHidePolicy(
    val timeoutMillis: Long?,
    val focusModifier: Modifier,
    val canHide: () -> Boolean,
)

/** 所有材质共用超时策略；焦点、读屏和窗口失活期间取消计时。 */
@Composable
internal fun rememberPlayerControlsAutoHide(defaultMillis: Long): PlayerControlsAutoHidePolicy {
    val context = LocalContext.current
    val nativeAccessibility = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val exploring = remember(nativeAccessibility) { mutableStateOf(nativeAccessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(nativeAccessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring.value = it }
        nativeAccessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { nativeAccessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    val focused = remember { mutableStateOf(false) }
    val focusModifier = remember { Modifier.onFocusChanged { focused.value = it.hasFocus }.focusGroup() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateAsState()
    val window = LocalWindowInfo.current
    val blocked = focused.value || exploring.value || !window.isWindowFocused || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val selected = resolvePlayerControlsTimeoutMillis(LocalPlayerControlsTimeoutSeconds.current, defaultMillis)
    val accessibility = LocalAccessibilityManager.current
    val recommended = remember(selected, accessibility, lifecycle, window.isWindowFocused, exploring.value) {
        selected?.let {
            accessibility?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = it,
                containsIcons = true,
                containsText = true,
                containsControls = true,
            )?.coerceAtLeast(it) ?: it
        }
    }
    return PlayerControlsAutoHidePolicy(
        timeoutMillis = if (blocked) null else recommended,
        focusModifier = focusModifier,
        canHide = {
            !focused.value && !exploring.value && window.isWindowFocused &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        },
    )
}
