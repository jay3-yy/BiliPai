package com.android.purebilibili.feature.home.components.cards

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.feedContentTypography

/**
 * Shared statistics row for every horizontal video card.
 *
 * FlowRow lets the second metric move to the next line when the information column is narrow;
 * a plain Row would clip the trailing number or force the owner name out of the card.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HorizontalVideoStatRow(
    playText: String,
    danmakuText: String,
    playIcon: ImageVector = Icons.Outlined.PlayCircleOutline,
    danmakuIcon: ImageVector = Icons.Outlined.Subtitles,
    modifier: Modifier = Modifier,
    contentColor: Color = AppContentColors.secondaryText(),
    textStyle: TextStyle = feedContentTypography().statistic,
    singleLine: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 1)
    val spacing = with(LocalDensity.current) {
        textMeasurer.measure(" ", style = textStyle, maxLines = 1).size.width.toDp()
    }
    if (singleLine) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            HorizontalVideoStatItem(playIcon, playText, Modifier.weight(1f, fill = false), contentColor, textStyle, constrainText = true)
            if (danmakuText.isNotBlank()) {
                HorizontalVideoStatItem(danmakuIcon, danmakuText, Modifier.weight(1f, fill = false), contentColor, textStyle, constrainText = true)
            }
        }
        return
    }
    FlowRow(
        modifier = modifier,
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(HORIZONTAL_VIDEO_STAT_WRAP_SPACING_DP.dp),
    ) {
        HorizontalVideoStatItem(
            icon = playIcon,
            text = playText,
            contentColor = contentColor,
            textStyle = textStyle,
        )
        if (danmakuText.isNotBlank()) {
            HorizontalVideoStatItem(
                icon = danmakuIcon,
                text = danmakuText,
                contentColor = contentColor,
                textStyle = textStyle,
            )
        }
    }
}

@Composable
internal fun HorizontalVideoStatItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = AppContentColors.secondaryText(),
    textStyle: TextStyle = feedContentTypography().statistic,
    constrainText: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HORIZONTAL_VIDEO_STAT_ICON_TEXT_GAP_DP.dp),
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(HORIZONTAL_VIDEO_STAT_ICON_SIZE_DP.dp),
        )
        AppText(
            text = text,
            modifier = if (constrainText) Modifier.weight(1f, fill = false) else Modifier,
            style = textStyle,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = if (constrainText) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}
