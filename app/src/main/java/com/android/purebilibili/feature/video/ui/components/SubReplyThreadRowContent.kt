package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon

internal fun Modifier.subReplyRootConnector(
    color: Color,
    startBelowAvatar: Boolean = false,
): Modifier = drawWithCache {
    val layout = resolveReplyItemLayoutPolicy()
    val avatarDiameter = layout.avatarSizeDp.dp.toPx()
    val start = layout.horizontalPaddingDp.dp.toPx() + avatarDiameter / 2f
    val x = if (layoutDirection == LayoutDirection.Ltr) start else size.width - start
    val y = if (startBelowAvatar) ReplyItemTopPadding.toPx() + avatarDiameter + 2.dp.toPx() else 0f
    val strokeWidth = 1.dp.toPx()
    onDrawWithContent {
        drawContent()
        drawLine(color, Offset(x, y), Offset(x, size.height), strokeWidth)
    }
}

private fun Modifier.subReplyThreadConnectors(
    row: SubReplyThreadRow,
    indent: Dp,
    expanded: Boolean,
    color: Color,
): Modifier = drawWithCache {
    val layout = resolveReplyItemLayoutPolicy()
    val avatarRadius = layout.avatarSizeDp.dp.toPx() / 2f
    val avatarStart = layout.horizontalPaddingDp.dp.toPx()
    val avatarCenterY = ReplyItemTopPadding.toPx() + avatarRadius
    val strokeWidth = 1.dp.toPx()
    val visibleDepth = row.depth.coerceAtMost(MAX_SUB_REPLY_THREAD_INDENT)
    val step = indent.toPx()
    fun fromStart(x: Float): Float = if (layoutDirection == LayoutDirection.Ltr) x else size.width - x
    fun railX(level: Int): Float = fromStart(avatarStart + avatarRadius + step * level)

    val parentRail = visibleDepth - 1
    val parentX = railX(parentRail)
    val elbowEnd = fromStart(avatarStart + step * visibleDepth - 2.dp.toPx())
    val fullHeightRails = if (
        row.hasNextSibling || (expanded && row.depth >= MAX_SUB_REPLY_THREAD_INDENT)
    ) {
        row.continuingRails or (1 shl parentRail)
    } else {
        row.continuingRails
    }
    val elbow = Path().apply {
        val bendY = avatarCenterY - 9.dp.toPx()
        if ((fullHeightRails and (1 shl parentRail)) == 0) {
            moveTo(parentX, 0f)
            lineTo(parentX, bendY)
        } else {
            moveTo(parentX, bendY)
        }
        cubicTo(parentX, avatarCenterY, elbowEnd, avatarCenterY, elbowEnd, avatarCenterY)
    }
    onDrawWithContent {
        drawContent()
        for (rail in 0 until MAX_SUB_REPLY_THREAD_INDENT) {
            if ((fullHeightRails and (1 shl rail)) != 0) {
                val x = railX(rail)
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth)
            }
        }
        drawPath(elbow, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        if (expanded && row.depth < MAX_SUB_REPLY_THREAD_INDENT) {
            val x = railX(visibleDepth)
            drawLine(
                color,
                Offset(x, avatarCenterY + avatarRadius + 2.dp.toPx()),
                Offset(x, size.height),
                strokeWidth,
            )
        }
    }
}

@Composable
internal fun SubReplyThreadRowContent(
    row: SubReplyThreadRow?,
    collapsed: Boolean,
    lineColor: Color,
    actionColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Dp) -> Unit,
) {
    if (row == null) {
        content(0.dp)
        return
    }

    // Keep the avatar and text readable in narrow panes by capping the visible gutter at 48dp.
    val step = 24.dp
    val indent = step * row.depth.coerceAtMost(MAX_SUB_REPLY_THREAD_INDENT)
    val hasBranch = row.descendantCount > 0
    Column(
        modifier = modifier.subReplyThreadConnectors(
            row = row,
            indent = step,
            expanded = hasBranch && !collapsed,
            color = lineColor,
        ),
    ) {
        content(indent)
        if (hasBranch) {
            val actionStart = indent + resolveReplyItemLayoutPolicy().dividerStartPaddingDp.dp
            Row(
                modifier = Modifier
                    .padding(start = actionStart)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .appActionClickable(role = Role.Button, onClick = onToggle)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    modifier = Modifier.weight(1f, fill = false),
                    text = if (collapsed) {
                        pluralStringResource(
                            R.plurals.sub_reply_thread_expand,
                            row.descendantCount,
                            row.descendantCount,
                        )
                    } else {
                        stringResource(R.string.sub_reply_thread_collapse)
                    },
                    fontSize = VideoCommentTypographyTokens.action,
                    color = actionColor,
                )
                AppIcon(
                    imageVector = if (collapsed) rememberAppChevronDownIcon() else rememberAppChevronUpIcon(),
                    contentDescription = null,
                    tint = actionColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
