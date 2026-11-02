package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.FullscreenActionCount
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.core.ui.components.AppText
import java.util.Locale

internal val PlayerActionCountTopOverflow = 5.dp
internal val PlayerActionCountEndOverflow = 2.dp

@Composable
internal fun rememberVisibleFullscreenCounts(): Set<FullscreenActionCount> {
    val context = LocalContext.current
    val visible by remember(context) { FullscreenActionCountSettings.getVisibleCounts(context) }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    return visible
}

internal fun formatPlayerActionCount(count: Long): String {
    val value = count.coerceAtLeast(0)
    return when {
        value >= 100_000_000L -> if (value >= 99_900_000_000L) "999亿+"
            else if (value >= 10_000_000_000L) "${value / 100_000_000L}亿"
            else String.format(Locale.ROOT, "%.1f亿", value / 100_000_000.0)
        value >= 1_000_000L -> "${value / 10_000L}万"
        value >= 10_000L -> String.format(Locale.ROOT, "%.1f万", value / 10_000.0)
        else -> value.toString()
    }
}

/** 横竖屏共用按钮右上角计数，不参与按钮测量；未知不显示，真实零值显示 0。 */
@Composable
internal fun CountedPlayerAction(
    count: Long?,
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
                    .offset(x = PlayerActionCountEndOverflow, y = -PlayerActionCountTopOverflow),
                contentAlignment = Alignment.TopEnd,
            ) {
                AppText(
                    text = formatPlayerActionCount(count),
                    modifier = Modifier
                        .widthIn(max = 44.dp)
                        .padding(vertical = 1.dp),
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
