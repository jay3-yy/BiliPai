package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppGlassMenuSource
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.feature.dynamic.SidebarUser
import com.android.purebilibili.feature.dynamic.shouldShowDynamicUserLiveBadge
import com.android.purebilibili.feature.live.rememberLiveCardContainerTransition
import kotlin.math.PI
import kotlin.math.sin

private val StaticLiveBarHeights = floatArrayOf(0.24f, 0.9f, 0.54f)

@Composable
internal fun DynamicUserMenuAnchor(
    user: SidebarUser,
    enabled: Boolean,
    onOpen: () -> Unit,
    content: @Composable (avatarModifier: Modifier, source: AppGlassMenuSource) -> Unit,
) {
    val source = rememberAppGlassMenuSource()
    val menuModifier = Modifier.appGlassMenuSource(source, onOpen = onOpen, enabled = enabled)
    // 有独立进房按钮时，玻璃管理菜单只从头像区域处理按住手势。
    val hasLiveAction = user.isLive && user.liveRoomId > 0L
    Box(if (hasLiveAction) Modifier else menuModifier) {
        content(if (hasLiveAction) menuModifier else Modifier, source)
    }
}

@Composable
internal fun DynamicUserAvatarWithLiveBadge(
    user: SidebarUser,
    onLiveClick: (Long, String, String) -> Unit,
    modifier: Modifier = Modifier,
    avatarModifier: Modifier = Modifier,
    name: (@Composable () -> Unit)? = null,
    avatar: @Composable BoxScope.() -> Unit,
) {
    val showBadge = shouldShowDynamicUserLiveBadge(user.isLive)
    val transition = if (showBadge && user.liveRoomId > 0L) {
        rememberLiveCardContainerTransition(
            roomId = user.liveRoomId,
            title = user.liveRoomTitle,
            hostName = user.name,
            color = Color.Transparent,
            cornerRadius = 0.dp,
            avatarSource = true,
            onClick = { onLiveClick(user.liveRoomId, user.liveRoomTitle, user.name) },
        )
    } else null
    // 头像、直播徽标和昵称共用转场快照，只有直播徽标处理进房点击。
    Column(
        modifier = modifier.then(transition?.modifier ?: Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showBadge) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-8).dp),
            ) {
                Box(modifier = avatarModifier, content = avatar)
                DynamicUserLiveBadge(
                    modifier = if (transition != null) {
                        Modifier
                            .zIndex(1f)
                            .clip(CircleShape)
                            .appActionClickable(
                                role = Role.Button,
                                onClickLabel = "进入${user.name}的直播间",
                                onClick = transition.onClick,
                            )
                    } else Modifier,
                )
            }
        } else {
            Box(modifier = avatarModifier, content = avatar)
        }
        if (name != null) {
            Spacer(Modifier.height(AppSpacingTokens.ExtraSmall))
            name()
        }
    }
}

@Composable
private fun DynamicUserLiveBadge(modifier: Modifier = Modifier) {
    val contentColor = AppContentColors.onAccent()
    Row(
        modifier = modifier
            .heightIn(min = 16.dp)
            .background(AppContentColors.accent(), CircleShape)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DynamicLivePulseIndicator(color = contentColor)
        AppText(
            text = "直播中",
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            tapToCopyEnabled = false,
        )
    }
}

@Composable
private fun DynamicLivePulseIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val progress = if (!reduceMotion && animationGate.active) {
        val transition = rememberInfiniteTransition(label = "dynamicLivePulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_200, easing = LinearEasing),
            ),
            label = "dynamicLivePulseProgress",
        )
    } else {
        null
    }

    Canvas(modifier = modifier.size(width = 10.dp, height = 9.dp).then(animationGate.modifier)) {
        drawDynamicLivePulseBars(
            progress = progress?.value,
            color = color,
        )
    }
}

private fun DrawScope.drawDynamicLivePulseBars(
    progress: Float?,
    color: Color,
) {
    val barWidth = size.width * 0.18f
    val gap = size.width * 0.14f
    val minHeight = size.height * 0.3f
    val availableHeight = size.height - minHeight

    repeat(3) { index ->
        val heightFraction = progress?.let { value ->
            val phase = value * 2f * PI.toFloat() + index * 2.1f
            0.5f + 0.5f * sin(phase)
        } ?: StaticLiveBarHeights[index]
        val barHeight = minHeight + availableHeight * heightFraction
        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = size.width * 0.09f + index * (barWidth + gap),
                y = size.height - barHeight,
            ),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
}
