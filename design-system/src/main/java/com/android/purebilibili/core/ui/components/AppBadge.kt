package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.AppThemeRenderer
import com.android.purebilibili.core.ui.rememberAppThemeRenderer
import com.android.purebilibili.core.ui.renderer.glass.AppGlassBadge
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3Badge
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixBadge

@Composable
fun AppBadge(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    content: @Composable (RowScope.() -> Unit)? = null,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val resolvedContainer = if (containerColor == Color.Unspecified) scheme.error else containerColor
    val preferredContent = if (contentColor == Color.Unspecified) {
        androidx.compose.material3.contentColorFor(resolvedContainer)
    } else contentColor
    val resolvedContent = com.android.purebilibili.core.ui.resolveButtonContentColor(
        resolvedContainer, preferredContent, scheme,
    )
    when (rememberAppThemeRenderer()) {
        AppThemeRenderer.MATERIAL3 -> AppMaterial3Badge(
            modifier = modifier,
            containerColor = resolvedContainer,
            contentColor = resolvedContent,
            content = content,
        )
        AppThemeRenderer.MIUIX -> AppMiuixBadge(
            modifier = modifier,
            containerColor = resolvedContainer,
            contentColor = resolvedContent,
            content = content,
        )
        AppThemeRenderer.LIQUID_GLASS -> AppGlassBadge(
            modifier = modifier,
            containerColor = resolvedContainer,
            contentColor = resolvedContent,
            content = content,
        )
    }
}
