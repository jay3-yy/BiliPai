package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Content card that follows the active UI style:
 * - Material 3 → [Card] with the theme's large content-container shape
 * - Miuix → [MiuixCard] with native corner radius
 *
 * Prefer this over hand-rolled [Surface] + [RoundedCornerShape] for video/detail
 * summary panels so radius and container treatment stay theme-native.
 */
@Composable
fun AppContentCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = contentColorFor(containerColor),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalAppUiStyle.current) {
        AppUiStyle.MIUIX -> {
            if (onClick != null) {
                MiuixCard(
                    modifier = modifier,
                    cornerRadius = MiuixCardDefaults.CornerRadius,
                    insideMargin = contentPadding,
                    colors = MiuixCardDefaults.defaultColors(
                        color = containerColor,
                        contentColor = contentColor,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = onClick,
                    content = content,
                )
            } else {
                MiuixCard(
                    modifier = modifier,
                    cornerRadius = MiuixCardDefaults.CornerRadius,
                    insideMargin = contentPadding,
                    colors = MiuixCardDefaults.defaultColors(
                        color = containerColor,
                        contentColor = contentColor,
                    ),
                    content = content,
                )
            }
        }
        AppUiStyle.MATERIAL3 -> {
            if (onClick != null) {
                Card(
                    onClick = onClick,
                    modifier = modifier,
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(contentPadding),
                        content = content,
                    )
                }
            } else {
                Card(
                    modifier = modifier,
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(contentPadding),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * Density preset for [AppTagChip].
 */
enum class AppTagChipSize(
    val value: Int,
    val label: String,
) {
    STANDARD(0, "标准"),
    COMPACT(1, "紧凑"),
    SMALL(2, "更小");

    companion object {
        fun fromValue(value: Int): AppTagChipSize =
            entries.find { it.value == value } ?: STANDARD
    }
}

data class AppTagChipMetrics(
    val fontScale: Float,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val itemSpacingHorizontal: Dp,
    val itemSpacingVertical: Dp,
    val chipHeight: Dp?,
)

fun resolveAppTagChipMetrics(size: AppTagChipSize): AppTagChipMetrics = when (size) {
    AppTagChipSize.STANDARD -> AppTagChipMetrics(
        fontScale = 0.90f,
        horizontalPadding = 6.dp,
        verticalPadding = 4.dp,
        itemSpacingHorizontal = 4.dp,
        itemSpacingVertical = 4.dp,
        chipHeight = 28.dp,
    )
    AppTagChipSize.COMPACT -> AppTagChipMetrics(
        fontScale = 0.82f,
        horizontalPadding = 4.dp,
        verticalPadding = 3.dp,
        itemSpacingHorizontal = 3.dp,
        itemSpacingVertical = 4.dp,
        chipHeight = 26.dp,
    )
    AppTagChipSize.SMALL -> AppTagChipMetrics(
        fontScale = 0.75f,
        horizontalPadding = 3.dp,
        verticalPadding = 2.dp,
        itemSpacingHorizontal = 2.dp,
        itemSpacingVertical = 3.dp,
        chipHeight = 24.dp,
    )
}

/**
 * Compact tag / keyword chip:
 * - Material 3 → [AssistChip], retaining the selected compact tag size
 * - Miuix → themed [Surface] with [ContainerLevel.Chip] / pill-scale corners
 */
@Composable
fun AppTagChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: AppTagChipSize = AppTagChipSize.STANDARD,
) {
    val metrics = resolveAppTagChipMetrics(size)
    val sizeModifier = if (metrics.chipHeight != null) {
        Modifier.heightIn(min = metrics.chipHeight)
    } else {
        Modifier
    }
    val baseLabelStyle = MaterialTheme.typography.labelLarge
    val labelStyle = baseLabelStyle.copy(
        fontSize = baseLabelStyle.fontSize * metrics.fontScale,
        lineHeight = if (baseLabelStyle.lineHeight.isSpecified) {
            baseLabelStyle.lineHeight * metrics.fontScale
        } else {
            baseLabelStyle.lineHeight
        },
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val colorScheme = MaterialTheme.colorScheme
    when (LocalAppUiStyle.current) {
        AppUiStyle.MATERIAL3 -> {
            AssistChip(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier.then(sizeModifier),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = colorScheme.surfaceContainerHighest,
                    labelColor = colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.verticalPadding,
                ),
                label = {
                    AppText(
                        text = label,
                        style = labelStyle,
                        maxLines = 1,
                    )
                },
            )
        }
        AppUiStyle.MIUIX -> {
            Surface(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier.then(sizeModifier),
                shape = AppShapes.container(ContainerLevel.Chip),
                color = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurfaceVariant,
            ) {
                AppText(
                    text = label,
                    style = labelStyle,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        horizontal = metrics.horizontalPadding,
                        vertical = metrics.verticalPadding,
                    ),
                )
            }
        }
    }
}

/**
 * Small badge pill (non-clickable) for status labels on video detail.
 */
@Composable
fun AppStatusBadge(
    label: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (emphasized) {
        com.android.purebilibili.core.ui.AppSurfaceTokens.primaryContainer()
    } else {
        colorScheme.surfaceContainerHighest
    }
    val contentColor = if (emphasized) {
        com.android.purebilibili.core.ui.AppSurfaceTokens.onPrimaryContainer()
    } else {
        colorScheme.onSurfaceVariant
    }
    AppSurface(
        modifier = modifier,
        shape = when (LocalAppUiStyle.current) {
            AppUiStyle.MATERIAL3 -> MaterialTheme.shapes.small
            AppUiStyle.MIUIX -> AppShapes.container(ContainerLevel.Tag)
        },
        color = containerColor,
        contentColor = contentColor,
    ) {
        AppText(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
