package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.FullscreenActionCount
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.core.store.FullscreenTopBarItem
import com.android.purebilibili.core.store.FullscreenTopBarSettings
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.FormatUtils

internal val PlayerActionCountTopOverflow = 6.dp
internal val PlayerActionCountEndOverflow = 4.dp

@Composable
internal fun rememberVisibleFullscreenCounts(): Set<FullscreenActionCount> {
    val context = LocalContext.current
    val visible by remember(context) { FullscreenActionCountSettings.getVisibleCounts(context) }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    return visible
}

@Composable
internal fun rememberVisibleFullscreenTopBarItems(): Set<FullscreenTopBarItem> {
    val context = LocalContext.current
    val visible by remember(context) { FullscreenTopBarSettings.getVisibleItems(context) }
        .collectAsStateWithLifecycle(initialValue = FullscreenTopBarSettings.defaultVisibleItems)
    return visible
}

internal fun formatPlayerActionCount(count: Long): String = FormatUtils.formatStat(count)

/** 播放器顶栏计数统一以按钮右上角定位，不参与按钮测量；未知不显示，真实零值显示 0。 */
@Composable
internal fun CountedPlayerAction(
    count: Long?,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val textStyle = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        shadow = with(LocalDensity.current) {
            Shadow(
                color = Color.Black.copy(alpha = 0.9f),
                offset = Offset(0f, 0.5.dp.toPx()),
                blurRadius = 2.dp.toPx(),
            )
        },
    )
    Box(modifier) {
        content()
        if (count != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val label = measurable.measure(Constraints(maxWidth = 44.dp.roundToPx()))
                        val endOverflowPx = PlayerActionCountEndOverflow.roundToPx()
                        val topOverflowPx = PlayerActionCountTopOverflow.roundToPx()
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            // 统一锚定按钮右上角，按钮材质或计数位数变化不改变按钮本体尺寸。
                            label.placeRelative(
                                x = (constraints.maxWidth + endOverflowPx - label.width).coerceAtLeast(0),
                                y = -topOverflowPx + 1.dp.roundToPx(),
                            )
                        }
                    },
            ) {
                AppText(
                    text = formatPlayerActionCount(count),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    style = textStyle,
                    tapToCopyEnabled = false,
                )
            }
        }
    }
}
