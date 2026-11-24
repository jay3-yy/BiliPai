package com.android.purebilibili.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.AppThemeRenderer
import com.android.purebilibili.core.ui.rememberAppThemeRenderer
import com.android.purebilibili.core.ui.renderer.glass.AppGlassListItem
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3ListItem
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixListItem

@Composable
fun AppListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    when (rememberAppThemeRenderer()) {
        AppThemeRenderer.MATERIAL3 -> AppMaterial3ListItem(
            headlineContent = headlineContent,
            modifier = modifier,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
        )
        AppThemeRenderer.MIUIX -> AppMiuixListItem(
            headlineContent = headlineContent,
            modifier = modifier,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
        )
        AppThemeRenderer.LIQUID_GLASS -> AppGlassListItem(
            headlineContent = headlineContent,
            modifier = modifier,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
        )
    }
}
