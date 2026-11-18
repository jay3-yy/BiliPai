package com.android.purebilibili.core.ui.renderer.material3

import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.components.AppCardColors
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppCardVariant

@Composable
internal fun AppMaterial3Card(
    modifier: Modifier,
    shape: AppCardShape?,
    colors: AppCardColors?,
    variant: AppCardVariant,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedShape = shape?.toMaterial3Shape()
    val cardModifier = if (onLongClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        modifier.appCombinedClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        modifier
    }

    when (variant) {
        AppCardVariant.Filled -> {
            val resolvedColors = colors?.let {
                CardDefaults.cardColors(
                    containerColor = it.containerColor,
                    contentColor = it.contentColor.takeOrElse {
                        contentColorFor(it.containerColor)
                    },
                )
            } ?: CardDefaults.cardColors()

            if (onClick != null && onLongClick == null) {
                Card(
                    onClick = onClick,
                    modifier = modifier,
                    shape = resolvedShape ?: CardDefaults.shape,
                    colors = resolvedColors,
                    content = content,
                )
            } else {
                Card(
                    modifier = cardModifier,
                    shape = resolvedShape ?: CardDefaults.shape,
                    colors = resolvedColors,
                    content = content,
                )
            }
        }
        AppCardVariant.Elevated -> {
            val resolvedColors = colors?.let {
                CardDefaults.elevatedCardColors(
                    containerColor = it.containerColor,
                    contentColor = it.contentColor.takeOrElse {
                        contentColorFor(it.containerColor)
                    },
                )
            } ?: CardDefaults.elevatedCardColors()

            if (onClick != null && onLongClick == null) {
                ElevatedCard(
                    onClick = onClick,
                    modifier = modifier,
                    shape = resolvedShape ?: CardDefaults.elevatedShape,
                    colors = resolvedColors,
                    content = content,
                )
            } else {
                ElevatedCard(
                    modifier = cardModifier,
                    shape = resolvedShape ?: CardDefaults.elevatedShape,
                    colors = resolvedColors,
                    content = content,
                )
            }
        }
    }
}

private fun AppCardShape.toMaterial3Shape(): Shape = when (this) {
    is AppCardShape.Semantic -> AppShapes.resolveContainerShape(
        level = level,
        uiStyle = AppUiStyle.MATERIAL3,
    )
    is AppCardShape.Uniform -> RoundedCornerShape(cornerRadius)
}
