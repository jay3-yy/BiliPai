package com.android.purebilibili.core.ui.renderer.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalAppTextStyleOverride
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppMiuixListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier,
    overlineContent: (@Composable () -> Unit)?,
    supportingContent: (@Composable () -> Unit)?,
    leadingContent: (@Composable () -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
) {
    // Miuix 底座列表项始终保持原生 BasicComponent 排版与间距，不因液态玻璃开关退化。
    top.yukonga.miuix.kmp.basic.BasicComponent(
        modifier = modifier,
        startAction = leadingContent,
        endActions = trailingContent?.let { slot ->
            { ProvideAppActionContentColor(AppContentColors.secondaryText(), slot) }
        },
    ) {
        overlineContent?.let { NativeListItemText(MiuixTheme.textStyles.body2, AppContentColors.secondaryText(), it) }
        NativeListItemText(MiuixTheme.textStyles.main, AppContentColors.primaryText(), headlineContent)
        supportingContent?.let { NativeListItemText(MiuixTheme.textStyles.body2, AppContentColors.secondaryText(), it) }
    }
}

@Composable
private fun NativeListItemText(style: TextStyle, color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppTextStyleOverride provides style) {
        androidx.compose.material3.ProvideTextStyle(style) {
            ProvideAppActionContentColor(color, content)
        }
    }
}
