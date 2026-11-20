package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.rememberAppLikeFilledIcon
import com.android.purebilibili.core.ui.rememberAppLikeIcon
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import com.android.purebilibili.feature.video.ui.components.CommentEntryBarContent
import com.android.purebilibili.feature.video.ui.components.resolveBottomInputBarPlaceholderTextColor
import top.yukonga.miuix.kmp.blur.Backdrop

private data class CommentEntryBarDimensions(
    val height: Dp,
    val horizontalPadding: Dp,
    val iconSize: Dp,
    val iconSpacing: Dp,
    val actionIconSize: Dp,
    val preferredActionPadding: Dp,
) {
    val commentChromeWidth = horizontalPadding * 2 + iconSize + iconSpacing
}

private object DynamicCommentBarSpec {
    val Glass = CommentEntryBarDimensions(
        height = 44.dp,
        horizontalPadding = AppSpacingTokens.Large,
        iconSize = 20.dp,
        iconSpacing = 6.dp,
        actionIconSize = 19.dp,
        preferredActionPadding = 10.dp,
    )
    val Native = CommentEntryBarDimensions(
        height = 48.dp,
        horizontalPadding = AppSpacingTokens.Medium,
        iconSize = 18.dp,
        iconSpacing = AppSpacingTokens.Small,
        actionIconSize = 22.dp,
        preferredActionPadding = 6.dp,
    )
    val MinimumActionPadding = AppSpacingTokens.ExtraSmall
    val ActionCountGap = AppSpacingTokens.ExtraSmall
    val DockGap = AppSpacingTokens.Small
    val CountWidthAllowance = 1.dp
    val OverflowIconSize = 20.dp
}

/**
 * 动态评论输入入口。
 *
 * 只负责打开共享的 [com.android.purebilibili.feature.video.ui.components.CommentInputDialog]；
 * 动态不再保留可编辑的内联输入框。
 */
@Composable
internal fun DynamicCommentEntryBar(
    onClick: () -> Unit,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    isScrollInProgressProvider: () -> Boolean = { false },
) {
    val dimensions = if (liquidGlassEnabled) DynamicCommentBarSpec.Glass else DynamicCommentBarSpec.Native
    val shape = resolveSharedBottomBarCapsuleShape()
    val textColor = resolveBottomInputBarPlaceholderTextColor(
        inputContainerColor = if (liquidGlassEnabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            containerColor
        },
        onSurfaceColor = AppContentColors.primaryText(),
        onSurfaceVariantColor = AppContentColors.secondaryText(),
    )

    if (liquidGlassEnabled) {
        BottomBarMatchedReusableLiquidDock(
            shape = shape,
            // The glass renderer shapes the moving shell. An outer clip would cut off
            // the shell and content when the shared press transform moves them.
            modifier = modifier.height(dimensions.height),
            backdrop = backdrop,
            reuseEnabled = true,
            materialEnabled = true,
            elasticFeedbackEnabled = true,
            drawShellLens = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(dimensions.height.value),
            isScrollInProgressProvider = isScrollInProgressProvider,
            // 内容只含文字与图标，不需要再录制玻璃外壳。
            captureShellForContent = false,
        ) { liquidChromeActive ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (liquidChromeActive) Color.Transparent else containerColor, shape)
                    .appActionClickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = dimensions.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommentEntryBarContent(
                    textColor = textColor,
                    iconSize = dimensions.iconSize,
                    iconSpacing = dimensions.iconSpacing,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .height(dimensions.height)
                .appElasticPress()
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(containerColor)
                .appActionClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = dimensions.horizontalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            CommentEntryBarContent(
                textColor = textColor,
                iconSize = dimensions.iconSize,
                iconSpacing = dimensions.iconSpacing,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun DynamicDetailCommentBar(
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop?,
    isLiked: Boolean,
    repostEnabled: Boolean,
    likeCount: Long?,
    repostCount: Long?,
    onComment: () -> Unit,
    onLike: () -> Unit,
    onRepost: () -> Unit,
    onShare: () -> Unit,
    isScrollInProgressProvider: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        AppWindowAction(
            label = if (isLiked) "取消点赞" else "点赞",
            icon = if (isLiked) rememberAppLikeFilledIcon() else rememberAppLikeIcon(),
            iconTint = if (isLiked) AppContentColors.accent() else null,
            onClick = onLike,
            summary = likeCount?.let(FormatUtils::formatStat),
        ),
        AppWindowAction(
            label = "转发",
            icon = Icons.Outlined.IosShare,
            enabled = repostEnabled,
            onClick = onRepost,
            summary = repostCount?.let(FormatUtils::formatStat),
        ),
        AppWindowAction(label = "分享", icon = rememberAppShareIcon(), onClick = onShare),
    )
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val dimensions = if (liquidGlassEnabled) DynamicCommentBarSpec.Glass else DynamicCommentBarSpec.Native
    val actionHeight = dimensions.height
    val actionIconSize = dimensions.actionIconSize
    // 玻璃外壳留出折射边缘，原生操作栏使用自己的内边距。
    val preferredActionPadding = dimensions.preferredActionPadding
    val minimumActionPadding = DynamicCommentBarSpec.MinimumActionPadding
    val actionCountGap = DynamicCommentBarSpec.ActionCountGap
    val dockGap = DynamicCommentBarSpec.DockGap
    val countStyle = MaterialTheme.typography.labelSmall
    val commentStyle = if (liquidGlassEnabled) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Reserve the formatted width even while a count is unknown or changes digits.
    val countWidth = with(density) {
        maxOf(
            textMeasurer.measure("8888", style = countStyle).size.width,
            textMeasurer.measure("88.8亿", style = countStyle).size.width,
        ).toDp() + DynamicCommentBarSpec.CountWidthAllowance
    }
    val commentLabelWidth = with(density) {
        textMeasurer.measure("发评论", style = commentStyle).size.width.toDp()
    }
    val minimumCommentWidth = commentLabelWidth + dimensions.commentChromeWidth
    val countedActionWidth = maxOf(actionHeight, actionIconSize + actionCountGap + countWidth)
    val actionSlotsWidth = countedActionWidth * 2 + actionHeight

    BoxWithConstraints(modifier = modifier) {
        val availableActionWidth = maxWidth - minimumCommentWidth - dockGap
        val compact = availableActionWidth < actionSlotsWidth + minimumActionPadding * 2
        val actionPadding = ((availableActionWidth - actionSlotsWidth) / 2)
            .coerceIn(minimumActionPadding, preferredActionPadding)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DynamicCommentEntryBar(
                onClick = onComment,
                liquidGlassEnabled = liquidGlassEnabled,
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                containerColor = containerColor,
                isScrollInProgressProvider = isScrollInProgressProvider,
            )
            val actionModifier = if (compact) {
                Modifier.size(actionHeight)
            } else {
                Modifier.width(actionSlotsWidth + actionPadding * 2).height(actionHeight)
            }
            val actionContent: @Composable () -> Unit = {
                if (compact) {
                    AppWindowActionMenu(
                        groups = listOf(actions.map { action ->
                            val count = action.summary
                            if (count == null) action else action.copy(label = "${action.label} $count", summary = null)
                        }),
                        modifier = Modifier.fillMaxSize(),
                        anchorModifier = Modifier.size(actionHeight),
                    ) {
                        AppIcon(
                            rememberAppMoreIcon(),
                            contentDescription = "动态操作",
                            modifier = Modifier.size(DynamicCommentBarSpec.OverflowIconSize),
                        )
                    }
                } else {
                    DynamicDetailCommentActions(
                        actions = actions,
                        actionHeight = actionHeight,
                        iconSize = actionIconSize,
                        countWidth = countWidth,
                        countStyle = countStyle,
                        countGap = actionCountGap,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = actionPadding),
                    )
                }
            }

            if (liquidGlassEnabled) {
                val shape = resolveSharedBottomBarCapsuleShape()
                BottomBarMatchedReusableLiquidDock(
                    shape = shape,
                    modifier = actionModifier,
                    backdrop = backdrop,
                    reuseEnabled = true,
                    materialEnabled = true,
                    elasticFeedbackEnabled = true,
                    drawShellLens = true,
                    shellLensIntensity = resolveFloatingDockGeometryScale(actionHeight.value),
                    captureShellForContent = false,
                    isScrollInProgressProvider = isScrollInProgressProvider,
                ) { liquidChromeActive ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(if (liquidChromeActive) Color.Transparent else containerColor, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        actionContent()
                    }
                }
            } else {
                Box(
                    modifier = actionModifier
                        .clip(AppShapes.container(ContainerLevel.Card))
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    actionContent()
                }
            }
        }
    }
}

@Composable
private fun DynamicDetailCommentActions(
    actions: List<AppWindowAction>,
    actionHeight: Dp,
    iconSize: Dp,
    countWidth: Dp,
    countStyle: TextStyle,
    countGap: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((index, action) in actions.withIndex()) {
            val hasCountSlot = index < actions.lastIndex
            val tint = action.iconTint ?: if (action.enabled) AppContentColors.primaryText() else AppContentColors.secondaryText()
            AppIconButton(
                enabled = action.enabled,
                onClick = { action.onClick?.invoke() },
                modifier = (if (hasCountSlot) Modifier.weight(1f) else Modifier.width(actionHeight))
                    .height(actionHeight),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(countGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    action.icon?.let { icon ->
                        AppIcon(icon, contentDescription = action.label, tint = tint, modifier = Modifier.size(iconSize))
                    }
                    val count = action.summary
                    if (hasCountSlot && count != null) {
                        AppText(
                            text = count,
                            style = countStyle,
                            color = tint,
                            modifier = Modifier.widthIn(max = countWidth),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            tapToCopyEnabled = false,
                        )
                    }
                }
            }
        }
    }
}
