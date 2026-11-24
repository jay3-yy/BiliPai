package com.android.purebilibili.core.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox as MiuixTooltipBox

/**
 * Long-press / hover tooltip bridge.
 *
 * MIUIX uses the official [MiuixTooltipBox] plain-text convenience API.
 * Material 3 uses the official [TooltipBox] with [PlainTooltip].
 * 开启弹窗液态玻璃（Android 13+）时，在提示框表面叠加统一的玻璃弹窗材质与对比度保护。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptivePlainTooltipBox(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val useGlassTooltip = LocalAppThemeConfig.current.popupGlassEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    if (useGlassTooltip && text.isNotBlank() && enabled) {
        val tooltipState = rememberTooltipState()
        val sceneConfig = LocalAppThemeConfig.current
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                CompositionLocalProvider(
                    LocalAppThemeConfig provides sceneConfig.copy(liquidGlassEnabled = sceneConfig.popupGlassEnabled),
                    LocalAppPopupWindowMaterialEnabled provides true,
                ) {
                    AppPopupSurface(
                        type = AppPopupSurfaceType.DIALOG,
                        shape = RoundedCornerShape(10.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = AppContentColors.primaryText(),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.primaryText(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            },
            state = tooltipState,
            modifier = modifier,
            focusable = false,
            enableUserInput = enabled,
            content = content,
        )
        return
    }
    when (rememberPresetPrimitiveRenderer()) {
        PresetPrimitiveRenderer.MIUIX_BRIDGED -> {
            MiuixTooltipBox(
                text = text,
                modifier = modifier,
                enabled = enabled && text.isNotBlank(),
                positioning = TooltipAnchorPosition.Below,
                content = content
            )
        }
        PresetPrimitiveRenderer.MATERIAL3 -> {
            if (text.isNotBlank() && enabled) {
                val tooltipState = rememberTooltipState()
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip {
                            Text(text = text)
                        }
                    },
                    state = tooltipState,
                    modifier = modifier,
                    focusable = false,
                    enableUserInput = enabled,
                    content = content
                )
            } else {
                Box(modifier = modifier) {
                    content()
                }
            }
        }
    }
}
