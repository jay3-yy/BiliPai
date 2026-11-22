package com.android.purebilibili.feature.video.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal fun rememberDanmakuPanelColors(scheme: ColorScheme): DanmakuSettingsPanelSurfaceColors =
    remember(scheme) { resolveDanmakuSettingsPanelSurfaceColors(scheme) }
