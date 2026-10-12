package com.android.purebilibili.core.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import com.android.purebilibili.core.ui.LocalAppThemeConfig

/** Appearance is independent of native theme, enabled state and reduced-motion policy. */
@Composable
internal fun useAppLiquidValueControls(): Boolean =
    LocalAppThemeConfig.current.liquidGlassEnabled && Build.VERSION.SDK_INT >= 33
