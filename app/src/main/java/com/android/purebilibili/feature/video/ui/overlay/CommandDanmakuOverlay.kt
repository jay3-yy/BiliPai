package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ThumbUp
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppExactTouchTargets
import com.android.purebilibili.core.ui.components.AppIconButtonDefaults
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage

import com.android.purebilibili.feature.video.danmaku.CommandDanmakuItem
import com.android.purebilibili.feature.video.danmaku.CommandDanmakuType
import com.android.purebilibili.feature.video.danmaku.VoteDanmakuKind
import com.android.purebilibili.feature.video.danmaku.VoteOption
import com.android.purebilibili.feature.video.danmaku.resolveGradeStarOptions
import com.android.purebilibili.feature.video.danmaku.isActiveAt
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import com.android.purebilibili.data.model.response.DynamicVoteInfo
import com.android.purebilibili.data.repository.DynamicVoteRepository
import com.android.purebilibili.feature.video.ui.feedback.canSubmitVideoFollowAction
import com.android.purebilibili.feature.video.ui.feedback.resolveVideoActionTint

@Composable
internal fun CommandDanmakuOverlay(
    items: List<CommandDanmakuItem>,
    player: Player,
    visible: Boolean,
    viewport: DanmakuViewport,
    bottomInsetPx: Int,
    state: CommandDanmakuOverlayState,
    fontScale: Float,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onVoteSubmit: (CommandDanmakuItem, VoteOption, Int) -> Unit,
    onLinkClick: (CommandDanmakuItem) -> Unit = {},
    isFollowing: Boolean = false,
    followStatusKnown: Boolean,
    likeStatusKnown: Boolean,
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    isFollowSubmitting: Boolean,
    isTripleSubmitting: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val placementHeightPx = (viewport.heightPx - bottomInsetPx.coerceAtLeast(0)).coerceAtLeast(0)
    if (placementHeightPx == 0) return
    val currentPosition by produceState(
        initialValue = player.currentPosition, key1 = player,
    ) {
        while (true) {
            value = player.currentPosition
            kotlinx.coroutines.delay(80)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        val active = items.filter {
            !state.isDismissed(it.id) && it.isActiveAt(currentPosition)
        }
        active.forEach { item ->
            key(item.id) {
                CommandDanmakuCard(
                    item = item,
                    viewport = viewport,
                    placementHeightPx = placementHeightPx,
                    state = state,
                    fontScale = fontScale,
                    onFollowClick = onFollowClick,
                    onLikeClick = onLikeClick,
                    onCoinClick = onCoinClick,
                    onFavoriteClick = onFavoriteClick,
                    onVoteSubmit = onVoteSubmit,
                    onLinkClick = onLinkClick,
                    isFollowing = isFollowing,
                    followStatusKnown = followStatusKnown,
                    likeStatusKnown = likeStatusKnown,
                    isLiked = isLiked,
                    isCoined = isCoined,
                    isFavorited = isFavorited,
                    isFollowSubmitting = isFollowSubmitting,
                    isTripleSubmitting = isTripleSubmitting,
                    onDismiss = { state.dismiss(item.id) }
                )
            }
        }
    }
}

@Composable
private fun CommandDanmakuCard(
    item: CommandDanmakuItem,
    viewport: DanmakuViewport,
    placementHeightPx: Int,
    state: CommandDanmakuOverlayState,
    fontScale: Float,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onVoteSubmit: (CommandDanmakuItem, VoteOption, Int) -> Unit,
    onLinkClick: (CommandDanmakuItem) -> Unit,
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    likeStatusKnown: Boolean,
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    isFollowSubmitting: Boolean,
    isTripleSubmitting: Boolean,
    onDismiss: () -> Unit
) {
    val isVote = item.type == CommandDanmakuType.VOTE && item.voteKind != VoteDanmakuKind.GRADE
    val isGrade = item.type == CommandDanmakuType.VOTE && item.voteKind == VoteDanmakuKind.GRADE
    val isAttention = item.type == CommandDanmakuType.ATTENTION
    val containerWidth = viewport.widthPx
    // 官方客户端不对投票卡片做底栏避让：按整个视频视口定位与限高，
    // 否则卡片被压短后只能内部滚动，选项被截断、状态行也看不见。
    val containerHeight = if (isVote) viewport.heightPx else placementHeightPx
    val (xRatio, yRatio) = when (item.type) {
        CommandDanmakuType.ATTENTION -> if (item.positionXRatio != null && item.positionYRatio != null) {
            item.positionXRatio to item.positionYRatio
        } else {
            mapAttentionPosition(item.posX, item.posY)
        }
        CommandDanmakuType.VOTE -> if (isVote) 0.49f to 0.12f else 0.08f to 0.10f
        CommandDanmakuType.UP -> 0.08f to 0.10f
        CommandDanmakuType.LINK -> 0.08f to 0.18f
        CommandDanmakuType.TEXT -> 0.08f to 0.10f
    }
    val density = LocalDensity.current
    val visualDensity = remember(density.density, density.fontScale, fontScale) {
        Density(density.density, density.fontScale * fontScale.coerceIn(0.3f, 2f))
    }
    val requestedCardWidthPx = when {
        isVote -> {
            with(visualDensity) { (VOTE_CARD_BODY_WIDTH_DP + VOTE_CLOSE_OVERHANG_DP).dp.roundToPx() }
        }
        isGrade -> {
            // Keep the reference phone card's absolute width when entering fullscreen.
            with(visualDensity) { (GRADE_CARD_BODY_WIDTH_DP + VOTE_CLOSE_OVERHANG_DP).dp.roundToPx() }
        }
        else -> {
            val requestedCardWidthDp = when (item.type) {
                CommandDanmakuType.ATTENTION -> resolveAttentionCommandCardWidthDp(item.attentionType, visualDensity.fontScale)
                else -> 220
            }
            with(visualDensity) { requestedCardWidthDp.dp.roundToPx() }
        }
    }
    val cardWidthPx = resolveCommandDanmakuCardWidthPx(
        containerWidthPx = containerWidth,
        requestedCardWidthPx = requestedCardWidthPx
    )
    val cardWidthDp = with(density) { cardWidthPx.toDp() }
    val maxCardHeightDp = with(visualDensity) { containerHeight.toDp() }
    // Start conservatively at the full viewport height so the first frame cannot extend below it;
    // onSizeChanged then tightens the position to the measured card height.
    var measuredCardHeightPx by remember(item.id, viewport, density.fontScale) {
        mutableIntStateOf(containerHeight)
    }
    val attentionEdgeInsetPx = if (isAttention) with(density) { 12.dp.roundToPx() } else 0
    val x = if (isAttention && item.positionXRatio != null) {
        resolveAttentionCommandOffsetPx(containerWidth, cardWidthPx, item.positionXRatio, attentionEdgeInsetPx)
    } else resolveCommandDanmakuHorizontalOffsetPx(
        containerWidthPx = containerWidth,
        cardWidthPx = cardWidthPx,
        xRatio = if (isGrade) {
            0.5f - cardWidthPx / (2f * containerWidth.coerceAtLeast(1))
        } else {
            xRatio
        },
    )
    val y = if (isAttention && item.positionYRatio != null) {
        resolveAttentionCommandOffsetPx(containerHeight, measuredCardHeightPx, item.positionYRatio, attentionEdgeInsetPx)
    } else resolveCommandDanmakuVerticalOffsetPx(
        containerHeightPx = containerHeight,
        cardHeightPx = measuredCardHeightPx,
        yRatio = if (isGrade) {
            0.5f - measuredCardHeightPx / (2f * containerHeight.coerceAtLeast(1))
        } else {
            yRatio
        },
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .width(cardWidthDp)
            .heightIn(max = with(density) { containerHeight.toDp() })
            .onSizeChanged { measuredCardHeightPx = it.height }
            // 卡片是交互 UI：点击（含双击）不得穿透到播放器的控件显隐与播放/暂停手势。
            .pointerInput(item.id) {
                detectTapGestures(onTap = {})
            }
    ) {
        // Keep native touch expansion without reserving 48dp layout boxes inside the card.
        CompositionLocalProvider(
            LocalDensity provides visualDensity,
            LocalMinimumInteractiveComponentSize provides 0.dp,
        ) {
            if (isVote) {
                VoteCommandCard(
                    item = item,
                    state = state,
                    maxHeightDp = maxCardHeightDp,
                    onVoteSubmit = onVoteSubmit,
                    onDismiss = onDismiss,
                )
            } else if (isGrade) {
                GradeCommandCard(
                    item = item,
                    state = state,
                    maxHeightDp = maxCardHeightDp,
                    onVoteSubmit = onVoteSubmit,
                    onDismiss = onDismiss,
                )
            } else if (isAttention) {
                AttentionCommandCard(
                    item = item,
                    isFollowing = isFollowing,
                    followStatusKnown = followStatusKnown,
                    likeStatusKnown = likeStatusKnown,
                    isLiked = isLiked,
                    isCoined = isCoined,
                    isFavorited = isFavorited,
                    isFollowSubmitting = isFollowSubmitting,
                    isTripleSubmitting = isTripleSubmitting,
                    onFollowClick = onFollowClick,
                    onLikeClick = onLikeClick,
                    onCoinClick = onCoinClick,
                    onFavoriteClick = onFavoriteClick,
                    onDismiss = onDismiss,
                )
            } else {
                val isLinkWithTarget = item.type == CommandDanmakuType.LINK &&
                    (item.linkBvid.isNotBlank() || item.linkAid > 0L)
                AppSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLinkWithTarget) {
                                Modifier.clickable(role = Role.Button) { onLinkClick(item) }
                            } else {
                                Modifier
                            }
                        ),
                    color = Color.Black.copy(alpha = 0.8f),
                    contentColor = Color.White,
                    shape = AppShapes.container(ContainerLevel.Chip)
                ) {
                    Box {
                        InfoCommandCard(item)
                        CommandDanmakuCloseButton(
                            onDismiss = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCommandCard(item: CommandDanmakuItem) {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = COMMAND_CLOSE_RESERVE_DP.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconUrl.isNotBlank()) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(
                text = if (item.type == CommandDanmakuType.LINK) "关联视频" else "UP 主提示",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.74f)
            )
            AppText(
                text = item.linkTitle.ifBlank { item.content },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val COMMAND_ICON_BUTTON_SIZE_DP = 32
private const val COMMAND_CLOSE_RESERVE_DP = COMMAND_ICON_BUTTON_SIZE_DP + 4
private const val ATTENTION_ICON_SLOT_DP = 44
private const val ATTENTION_FOLLOW_TARGET_DP = 66
private const val ATTENTION_ACTION_SPACING_DP = 8
private const val ATTENTION_HORIZONTAL_PADDING_DP = 5
private const val VOTE_CLOSE_OVERHANG_DP = 14
private const val VOTE_CARD_BODY_WIDTH_DP = 130
private const val GRADE_CARD_BODY_WIDTH_DP = 180

@Composable
private fun AttentionCommandCard(
    item: CommandDanmakuItem,
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    likeStatusKnown: Boolean,
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    isFollowSubmitting: Boolean,
    isTripleSubmitting: Boolean,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showTriple = item.attentionType == 1 || item.attentionType == 2
    val followEnabled = canSubmitVideoFollowAction(followStatusKnown, isFollowing, isFollowSubmitting)

    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.8f),
        contentColor = Color.White,
        shape = RoundedCornerShape(4.dp),
    ) {
        AppExactTouchTargets {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ATTENTION_HORIZONTAL_PADDING_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showTriple) {
                    AttentionCommandActions(
                        likeStatusKnown = likeStatusKnown,
                        isLiked = isLiked,
                        isCoined = isCoined,
                        isFavorited = isFavorited,
                        isSubmitting = isTripleSubmitting,
                        onLikeClick = onLikeClick,
                        onCoinClick = onCoinClick,
                        onFavoriteClick = onFavoriteClick,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(width = ATTENTION_ICON_SLOT_DP.dp, height = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.iconUrl.isNotBlank()) {
                            AsyncImage(
                                model = item.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                            )
                        } else {
                            AppIcon(Icons.Rounded.Person, contentDescription = null, tint = Color.White)
                        }
                    }
                }
                if (item.attentionType != 1) {
                    Spacer(Modifier.width(ATTENTION_ACTION_SPACING_DP.dp))
                    AttentionCommandFollowButton(
                        isFollowing = isFollowing,
                        followStatusKnown = followStatusKnown,
                        enabled = followEnabled,
                        isSubmitting = isFollowSubmitting,
                        onClick = onFollowClick,
                    )
                }
                if (!showTriple) {
                    CommandDanmakuCloseButton(
                        onDismiss = onDismiss,
                        containerColor = Color.Transparent,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandDanmakuCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Black.copy(alpha = 0.34f),
) {
    AppIconButton(
        onClick = onDismiss,
        modifier = modifier.size(COMMAND_ICON_BUTTON_SIZE_DP.dp),
        colors = AppIconButtonDefaults.colors(
            containerColor = containerColor,
            contentColor = Color.White
        )
    ) {
        AppIcon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "关闭提示",
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AttentionCommandActions(
    likeStatusKnown: Boolean,
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    isSubmitting: Boolean,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val activeColor = AppContentColors.accent()
    Row(verticalAlignment = Alignment.CenterVertically) {
        AttentionCommandIconSlot(
            icon = Icons.Rounded.ThumbUp,
            iconSize = 24.dp,
            enabled = likeStatusKnown && !isSubmitting,
            selected = likeStatusKnown && isLiked,
            description = "点赞",
            stateDescription = when {
                !likeStatusKnown -> "点赞状态尚未确认"
                isLiked -> "已点赞"
                else -> "未点赞"
            },
            tint = resolveVideoActionTint(
                likeStatusKnown && isLiked, activeColor,
                if (likeStatusKnown) Color.White else Color.White.copy(alpha = 0.38f),
            ),
            onClick = onLikeClick,
        )
        AttentionCommandIconSlot(
            icon = attentionFilledCoinIcon(),
            iconSize = 24.dp,
            enabled = !isSubmitting,
            selected = isCoined,
            description = "投币",
            stateDescription = if (isCoined) "已投币" else "未投币",
            tint = Color.Unspecified,
            backgroundColor = if (isCoined) activeColor.copy(alpha = 0.22f) else Color.Unspecified,
            onClick = onCoinClick,
        )
        AttentionCommandIconSlot(
            icon = Icons.Rounded.Star,
            iconSize = 30.dp,
            enabled = !isSubmitting,
            selected = isFavorited,
            description = "收藏",
            stateDescription = if (isFavorited) "已收藏" else "未收藏",
            tint = resolveVideoActionTint(isFavorited, activeColor, Color.White),
            onClick = onFavoriteClick,
        )
    }
}

@Composable
private fun AttentionCommandFollowButton(
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    enabled: Boolean,
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val actionable = enabled
    val followed = followStatusKnown && isFollowing
    val containerColor = when {
        actionable -> scheme.primary
        followed -> scheme.primaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val contentColor = when {
        actionable -> scheme.onPrimary
        followed -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val label = when {
        isSubmitting -> "处理中"
        !followStatusKnown -> "—"
        isFollowing -> "已关注"
        else -> "关注"
    }
    val baseTextStyle = MaterialTheme.typography.labelMedium
    val textStyle = remember(baseTextStyle) {
        baseTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp)
    }
    Row(
        modifier = Modifier
            .widthIn(min = ATTENTION_FOLLOW_TARGET_DP.dp)
            .heightIn(min = 24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "关注"
                selected = followed
                stateDescription = if (!followStatusKnown && !isSubmitting) "关注状态尚未确认" else label
            }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actionable) {
            AppIcon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        AppText(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AttentionCommandIconSlot(
    icon: ImageVector,
    iconSize: Dp,
    enabled: Boolean,
    selected: Boolean,
    description: String,
    stateDescription: String,
    tint: Color = Color.White,
    backgroundColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    var pulseKey by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (pressed) 1.1f else 1f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "attentionActionPressScale",
    )
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            pressed = true
            delay(420)
            pressed = false
        }
    }
    Box(
        modifier = Modifier
            .size(width = ATTENTION_ICON_SLOT_DP.dp, height = 28.dp)
            .clickable(enabled = enabled, role = Role.Button) {
                pulseKey += 1
                onClick()
            }
            .semantics {
                contentDescription = description
                this.stateDescription = stateDescription
                this.selected = selected
            }
            .then(
                if (backgroundColor != Color.Unspecified) Modifier.background(backgroundColor, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.requiredSize(iconSize).graphicsLayer {
                val currentScale = scale.value
                scaleX = currentScale
                scaleY = currentScale
            },
        )
    }
}

private var cachedAttentionCoinIcon: ImageVector? = null

private fun attentionFilledCoinIcon(): ImageVector {
    cachedAttentionCoinIcon?.let { return it }
    return ImageVector.Builder(
        name = "AttentionFilledCoin",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f)
            arcTo(10f, 10f, 0f, true, true, 12f, 22f)
            arcTo(10f, 10f, 0f, true, true, 12f, 2f)
            close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(16f, 5f)
            lineTo(8f, 6.5f)
            moveTo(7f, 10f)
            lineTo(17f, 10f)
            lineTo(17f, 17f)
            moveTo(7f, 10f)
            lineTo(7f, 17f)
            moveTo(12f, 6f)
            lineTo(12f, 19f)
        }
    }.build().also { cachedAttentionCoinIcon = it }
}

internal fun resolveAttentionCommandCardWidthDp(attentionType: Int, fontScale: Float): Int {
    val tripleWidth = ATTENTION_ICON_SLOT_DP * 3
    val padding = ATTENTION_HORIZONTAL_PADDING_DP * 2
    if (attentionType == 1) return tripleWidth + padding
    val followWidth = ATTENTION_FOLLOW_TARGET_DP + (36f * (fontScale - 1f).coerceAtLeast(0f)).roundToInt()
    return when (attentionType) {
        2 -> tripleWidth + ATTENTION_ACTION_SPACING_DP + followWidth + padding
        else -> ATTENTION_ICON_SLOT_DP + ATTENTION_ACTION_SPACING_DP + followWidth + COMMAND_ICON_BUTTON_SIZE_DP + padding
    }
}

@Composable
private fun VoteCommandCard(
    item: CommandDanmakuItem,
    maxHeightDp: Dp,
    state: CommandDanmakuOverlayState,
    onVoteSubmit: (CommandDanmakuItem, VoteOption, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val voteId = item.voteId.toLongOrNull()
    var loadedInfo by remember(item.id) { mutableStateOf<DynamicVoteInfo?>(null) }
    var isLoading by remember(item.id) { mutableStateOf(false) }
    var loadError by remember(item.id) { mutableStateOf<String?>(null) }
    var loadAttempt by remember(item.id) { mutableIntStateOf(0) }
    val selection = state.selection(item.id)
    val needsResults = selection != null || item.voteSelectedIndex != null
    LaunchedEffect(item.id, item.voteOptions, loadAttempt, needsResults) {
        if (voteId == null) return@LaunchedEffect
        // 选项缺失时取选项；已投票时再取一次实时票数，用于官方客户端式的百分比结果。
        if (item.voteOptions.isNotEmpty() && !needsResults) return@LaunchedEffect
        isLoading = true
        loadError = null
        try {
            DynamicVoteRepository.getVoteInfo(voteId).fold(
                onSuccess = { loadedInfo = it },
                onFailure = { loadError = it.message ?: "投票选项加载失败" },
            )
        } finally {
            isLoading = false
        }
    }
    val options = remember(item.voteOptions, loadedInfo) {
        if (item.voteOptions.isNotEmpty()) item.voteOptions else {
            loadedInfo?.options?.map { option ->
                VoteOption(
                    id = option.opt_idx.toString(),
                    label = option.opt_desc,
                    optionIndex = option.opt_idx,
                )
            }.orEmpty()
        }
    }
    val selectedIndex = item.voteSelectedIndex ?: loadedInfo?.my_votes?.firstOrNull()?.takeIf { it > 0 }
    val selectedOptionId = selection?.id ?: selectedIndex?.let { index ->
        options.firstOrNull { it.optionIndex == index }?.id
    }
    val hasVoted = selection != null || selectedIndex != null
    val isSubmitting = state.isSubmitting(item.id)
    val voteCounts = remember(loadedInfo) {
        loadedInfo?.options?.associate { it.opt_idx to it.cnt }.orEmpty()
    }
    val totalVotes = remember(loadedInfo, voteCounts) {
        val joined = loadedInfo?.join_num ?: 0
        if (joined > 0) joined else voteCounts.values.sum()
    }
    // 官方客户端只在投票后展示结果，未投票时不显示占比以免影响选择。
    val showPercent = hasVoted && voteCounts.isNotEmpty() && totalVotes > 0
    val baseTextStyle = MaterialTheme.typography.bodySmall
    val textStyle = remember(baseTextStyle) {
        baseTextStyle.copy(fontSize = 10.sp, lineHeight = 14.sp)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = VOTE_CLOSE_OVERHANG_DP.dp, end = VOTE_CLOSE_OVERHANG_DP.dp),
            color = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (maxHeightDp - VOTE_CLOSE_OVERHANG_DP.dp).coerceAtLeast(0.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = item.voteTitle.ifBlank { loadedInfo?.title?.ifBlank { item.content } ?: item.content },
                    style = textStyle,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        options.isNotEmpty() -> options.forEachIndexed { index, option ->
                            VoteCommandOption(
                                label = option.label,
                                textStyle = textStyle,
                                isSelected = selectedOptionId == option.id,
                                enabled = !hasVoted && !isSubmitting,
                                percent = if (showPercent) {
                                    option.optionIndex?.let { optionIndex ->
                                        voteCounts[optionIndex]?.let { count ->
                                            (count * 100f / totalVotes).roundToInt()
                                        }
                                    }
                                } else null,
                                onClick = {
                                    if (state.beginVoteSubmission(item)) {
                                        onVoteSubmit(item, option, option.optionIndex ?: index + 1)
                                    }
                                },
                            )
                        }
                        isLoading -> AppText("加载投票选项中…", style = textStyle, color = Color.White)
                        voteId != null -> VoteCommandOption(
                            label = if (loadError != null) "选项加载失败，点此重试" else "投票选项暂不可用，点此重试",
                            textStyle = textStyle,
                            onClick = { loadAttempt++ },
                        )
                        else -> AppText("缺少投票 ID，无法加载选项", style = textStyle, color = Color.White.copy(alpha = 0.74f))
                    }
                }
                if (isSubmitting) {
                    AppText(
                        text = "投票提交中…",
                        style = textStyle,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
        OutsideCommandDanmakuCloseButton(
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun OutsideCommandDanmakuCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .clickable(role = Role.Button, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "关闭提示",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun VoteCommandOption(
    label: String,
    textStyle: TextStyle,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    percent: Int? = null,
) {
    // 选中项用当前配色的浅色容器（蓝色配色即浅蓝），未选中项保持半透明白。
    val colors = MaterialTheme.colorScheme
    val labelColor = if (isSelected) colors.onPrimaryContainer else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) {
                    colors.primaryContainer
                } else {
                    Color.White.copy(alpha = if (enabled) 0.22f else 0.14f)
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            style = textStyle,
            color = labelColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (percent != null) {
            Spacer(modifier = Modifier.width(6.dp))
            AppText(
                text = "$percent%",
                style = textStyle,
                color = labelColor.copy(alpha = 0.82f),
            )
        }
    }
}

/**
 * 星位始终按 API 的合法分数 2/4/6/8/10 对齐，不按服务端列表顺序猜测分数。
 */
@Composable
private fun GradeCommandCard(
    item: CommandDanmakuItem,
    maxHeightDp: Dp,
    state: CommandDanmakuOverlayState,
    onVoteSubmit: (CommandDanmakuItem, VoteOption, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedGradeScore = state.gradeScore(item)
    val summary = state.gradeSummary(item)
    val isSubmitting = state.isSubmitting(item.id)
    val gradeStarOptions = remember(item.id, item.voteOptions) {
        resolveGradeStarOptions(item.voteOptions)
    }
    val baseTextStyle = MaterialTheme.typography.bodySmall
    val titleStyle = remember(baseTextStyle) {
        baseTextStyle.copy(fontSize = 10.sp, lineHeight = 14.sp)
    }
    val statisticsStyle = remember(baseTextStyle) {
        baseTextStyle.copy(fontSize = 8.sp, lineHeight = 10.sp)
    }
    val averageStyle = remember(baseTextStyle) {
        baseTextStyle.copy(fontSize = 20.sp, lineHeight = 24.sp)
    }
    val participantText = remember(summary?.participantCount) {
        summary?.participantCount?.let { "${it}人参与" } ?: "参与人数暂无"
    }
    val averageText = remember(summary?.averageScore) {
        summary?.averageScore?.let { String.format(Locale.ROOT, "%.1f", it) }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = VOTE_CLOSE_OVERHANG_DP.dp, end = VOTE_CLOSE_OVERHANG_DP.dp),
            color = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (maxHeightDp - VOTE_CLOSE_OVERHANG_DP.dp).coerceAtLeast(0.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = item.voteTitle.ifBlank { item.content },
                        modifier = Modifier.weight(1f),
                        style = titleStyle,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = participantText,
                        style = statisticsStyle,
                        color = Color.White.copy(alpha = 0.74f),
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = if (selectedGradeScore == null) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth().padding(start = 2.dp, end = 4.dp)
                    },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GradeStarRating(
                        modifier = Modifier.weight(1f),
                        starOptions = gradeStarOptions,
                        selectedScore = selectedGradeScore,
                        isSubmitting = isSubmitting,
                        onSelect = { option ->
                            if (state.beginGradeSubmission(item, option)) {
                                onVoteSubmit(item, option, -1)
                            }
                        },
                    )
                    if (selectedGradeScore != null) {
                        if (averageText != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText(
                                    text = "平均",
                                    style = statisticsStyle,
                                    color = Color.White.copy(alpha = 0.74f),
                                )
                                AppText(
                                    text = averageText,
                                    style = averageStyle,
                                    color = Color(0xFFFFB112),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        } else {
                            AppText(
                                text = "平均暂无",
                                style = statisticsStyle,
                                color = Color.White.copy(alpha = 0.74f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        OutsideCommandDanmakuCloseButton(
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun GradeStarRating(
    modifier: Modifier = Modifier,
    starOptions: List<VoteOption?>,
    selectedScore: Int?,
    isSubmitting: Boolean,
    onSelect: (VoteOption) -> Unit,
) {
    Row(
        modifier = modifier.semantics {
            if (isSubmitting) contentDescription = "评分提交中"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (selectedScore == null) Arrangement.SpaceBetween else Arrangement.Start,
    ) {
        starOptions.forEachIndexed { index, option ->
            val isFilled = selectedScore != null && (index + 1) * 2 <= selectedScore
            val canSelect = option != null && selectedScore == null && !isSubmitting
            Box(
                modifier = (if (selectedScore == null) Modifier.width(18.dp) else Modifier.weight(1f))
                    .height(if (selectedScore == null) 22.dp else 20.dp)
                    .clickable(
                        enabled = canSelect,
                        role = Role.Button,
                        onClick = { option?.let(onSelect) },
                    )
                    .semantics { selected = selectedScore == (index + 1) * 2 },
                contentAlignment = if (selectedScore == null) Alignment.Center else Alignment.CenterStart,
            ) {
                AppIcon(
                    imageVector = if (isFilled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (option == null) {
                        "${index + 1}星不可用"
                    } else {
                        "${index + 1}星"
                    },
                    tint = when {
                        isFilled -> Color(0xFFFFB112)
                        option == null -> Color.White.copy(alpha = 0.26f)
                        else -> Color.White
                    },
                    modifier = Modifier.size(if (selectedScore == null) 18.dp else 12.dp),
                )
            }
        }
    }
}

internal fun resolveCommandDanmakuBottomInsetPx(
    viewportHeightPx: Int,
    surfaceHeightPx: Int,
    controlsReserveHeightPx: Int,
): Int {
    val viewportHeight = viewportHeightPx.coerceAtLeast(0)
    val bottomLetterboxHeight = (surfaceHeightPx.coerceAtLeast(viewportHeight) - viewportHeight) / 2
    return (controlsReserveHeightPx.coerceAtLeast(0) - bottomLetterboxHeight)
        .coerceIn(0, viewportHeight)
}

internal fun resolveCommandDanmakuCardWidthPx(
    containerWidthPx: Int,
    requestedCardWidthPx: Int
): Int {
    return requestedCardWidthPx.coerceIn(0, containerWidthPx.coerceAtLeast(0))
}

internal fun resolveCommandDanmakuVerticalOffsetPx(
    containerHeightPx: Int,
    cardHeightPx: Int,
    yRatio: Float
): Int {
    val safeHeight = containerHeightPx.coerceAtLeast(0)
    val maxOffset = (safeHeight - cardHeightPx.coerceAtLeast(0)).coerceAtLeast(0)
    return (safeHeight * yRatio).roundToInt().coerceIn(0, maxOffset)
}

internal fun resolveCommandDanmakuHorizontalOffsetPx(
    containerWidthPx: Int,
    cardWidthPx: Int,
    xRatio: Float
): Int {
    val maxOffset = (containerWidthPx - cardWidthPx).coerceAtLeast(0)
    return (containerWidthPx * xRatio).roundToInt().coerceIn(0, maxOffset)
}

internal fun resolveAttentionCommandOffsetPx(
    containerSizePx: Int,
    cardSizePx: Int,
    positionRatio: Float,
    edgeInsetPx: Int,
): Int {
    val available = (containerSizePx.coerceAtLeast(0) - cardSizePx.coerceAtLeast(0)).coerceAtLeast(0)
    val inset = edgeInsetPx.coerceIn(0, available / 2)
    val ratio = if (positionRatio.isFinite()) positionRatio.coerceIn(0f, 1f) else 0.5f
    return inset + ((available - inset * 2) * ratio).roundToInt()
}

internal fun mapAttentionPosition(posX: Float, posY: Float): Pair<Float, Float> {
    val x = ((posX - 118f) / (549f - 118f)).coerceIn(0f, 0.82f)
    val y = ((posY - 82f) / (293f - 82f)).coerceIn(0f, 0.78f)
    return x to y
}
