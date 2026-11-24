package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor
import com.android.purebilibili.core.ui.isAppInDarkTheme

/**
 * 液态玻璃渲染线的 iOS 胶囊角标与红点指示。
 */
@Composable
internal fun AppGlassBadge(
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    content: @Composable (RowScope.() -> Unit)?,
) {
    val dark = isAppInDarkTheme()
    val rimColor = Color.White.copy(alpha = if (dark) 0.28f else 0.48f)
    if (content == null) {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(0.6.dp, rimColor, CircleShape),
        )
        return
    }
    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor,
    ) {
        ProvideAppActionContentColor(contentColor) {
            Row(
                modifier = modifier
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(containerColor)
                    .border(0.6.dp, rimColor, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                    )
                ) {
                    content()
                }
            }
        }
    }
}
