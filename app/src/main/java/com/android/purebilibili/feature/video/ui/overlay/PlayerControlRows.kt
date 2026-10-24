package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppExactTouchTargets
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import kotlin.math.roundToInt

enum class PlayerControlAnchor { QUALITY, SPEED, EPISODES, RATIO, TRANSLATION, SUBTITLE, ENHANCEMENT, CHAPTERS }

internal const val PILI_PLAYER_BOTTOM_ROW_HEIGHT_DP = 34
internal const val PILI_PLAYER_BOTTOM_VERTICAL_PADDING_DP = 12
internal const val PILI_PLAYER_PROGRESS_GAP_DP = 7
internal const val PILI_PLAYER_PROGRESS_HEIGHT_DP = 14
internal const val PILI_PLAYER_CHAPTER_PROGRESS_HEIGHT_DP = 23

internal fun piliPlayerBottomControlsHeightDp(hasChapters: Boolean): Int =
    PILI_PLAYER_BOTTOM_VERTICAL_PADDING_DP * 2 + PILI_PLAYER_BOTTOM_ROW_HEIGHT_DP +
        PILI_PLAYER_PROGRESS_GAP_DP + if (hasChapters) PILI_PLAYER_CHAPTER_PROGRESS_HEIGHT_DP
        else PILI_PLAYER_PROGRESS_HEIGHT_DP

internal fun Modifier.playerActionBounds(onBounds: (IntRect) -> Unit): Modifier =
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        if (bounds.width > 0f && bounds.height > 0f) {
            onBounds(IntRect(
                bounds.left.roundToInt(), bounds.top.roundToInt(),
                bounds.right.roundToInt(), bounds.bottom.roundToInt(),
            ))
        }
    }

/**
 * PiliPlus PlayerBar: content-width groups at opposite edges; crowded groups scale together,
 * including their hit transforms. No material-specific slots, hidden actions or horizontal scroll.
 */
@Composable
internal fun PiliPlayerBar(
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Layout(content = {
        Row(verticalAlignment = Alignment.CenterVertically) { left() }
        Row(verticalAlignment = Alignment.CenterVertically) { right() }
    }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        val leftGroup = measurables[0].measure(loose)
        val rightGroup = measurables[1].measure(loose)
        val totalWidth = leftGroup.width + rightGroup.width
        val width = constraints.maxWidth
        val height = constraints.constrainHeight(maxOf(leftGroup.height, rightGroup.height))
        val scale = if (totalWidth > width) width.toFloat() / totalWidth else 1f
        val y = ((height - maxOf(leftGroup.height, rightGroup.height) * scale) / 2f).roundToInt()
        val rightX = if (scale < 1f) (leftGroup.width * scale).roundToInt() else width - rightGroup.width
        layout(width, height) {
            leftGroup.placeWithLayer(0, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            rightGroup.placeWithLayer(rightX, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
internal fun PlayerPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    width: Dp,
    iconSizeDp: Int = 20,
    height: Dp = PILI_PLAYER_BOTTOM_ROW_HEIGHT_DP.dp,
) {
    PlayerControlIconAction(
        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        description = if (isPlaying) "暂停" else "播放",
        width = width,
        height = height,
        iconSize = iconSizeDp.dp,
        modifier = Modifier.testTag("player_play_pause"),
        onClick = onClick,
    )
}

@Composable
internal fun PlayerControlIconAction(
    icon: ImageVector,
    description: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false,
    iconSize: Dp = 22.dp,
    onLongClick: (() -> Unit)? = null,
) {
    PlayerControlSurface(
        modifier = modifier.width(width).height(height),
        shape = RoundedCornerShape(8.dp),
    ) {
        AppExactTouchTargets {
            Box(
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = description
                    this.selected = selected
                }.combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (dimmed) 0.5f else 1f),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
internal fun PlayerControlTextAction(
    text: String,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    fontSp: Int = 13,
    height: Dp = 30.dp,
) {
    PlayerControlSurface(modifier = modifier, shape = RoundedCornerShape(8.dp)) {
        AppExactTouchTargets {
            Box(
                modifier = Modifier.height(height).semantics {
                    contentDescription = description
                    this.selected = selected
                }.combinedClickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = text,
                    color = Color.White,
                    fontSize = fontSp.sp,
                    maxLines = 1,
                    softWrap = false,
                    tapToCopyEnabled = false,
                )
            }
        }
    }
}
