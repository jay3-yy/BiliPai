package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.android.purebilibili.core.ui.skeleton.skeletonBackground
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel

// 首屏骨架卡片数量（瀑布流两列时为 6 张）
internal const val DYNAMIC_FEED_SKELETON_ITEM_COUNT = 6


/**
 * 动态 feed 首屏骨架卡（扫光），对齐 BiliPai 的 DynamicCardSkeleton：
 * 头像 + 双行文字 + 正文条 + 封面块 + 底部操作占位。
 *
 * 返回 [State] 而非裸 Float:调用方把 state 包进 provider 传给骨架卡,
 * 值只在 draw 阶段被 [drawBehind] 读取,骨架期间逐帧仅重绘、不触发重组。
 */
/**
 * 动态 feed 首屏骨架卡，对齐 BiliPai 的 DynamicCardSkeleton：
 * 头像 + 双行文字 + 正文条 + 封面块 + 底部操作占位。
 *
 * [blockColor] 传固定色时用固定色——骨架与真实卡片共用同一个瀑布流、靠 item 身份原地
 * 替换时，运动中的灰块叠在卡片淡入之下，观感上就是「闪」；固定色只负责占位，切换时
 * 只剩「卡片淡入」这一件事。留空则退回 [pulse] 驱动的扫光，供独立骨架列表使用。
 */
@Composable
internal fun DynamicFeedSkeletonCard(
    modifier: Modifier = Modifier,
    pulse: () -> Float = { Float.NaN },
    blockColor: Color? = null,
) {
    val cardShape = AppShapes.container(ContainerLevel.Card)
    val textPlaceholderShape = AppShapes.container(ContainerLevel.Tag)
    val actionPlaceholderShape = AppShapes.container(ContainerLevel.Tag)
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = AppContentColors.secondaryText()
    // 传入固定色时用固定色（「骨架与真实卡片共用同一列表、靠 item 身份原地替换」的页面）；
    // 否则使用扫光，供独立骨架列表使用。
    val resolvedBlockColor: () -> Color = if (blockColor != null) {
        { blockColor }
    } else {
        com.android.purebilibili.core.ui.skeleton.skeletonFill(
            base = surfaceVariant,
            highlight = lerp(surfaceVariant, onSurfaceVariant.copy(alpha = 0.22f), 0.7f),
            position = pulse,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(AppSpacingTokens.Medium)
    ) {
        // 作者行：头像 + 名称/时间
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(AppSpacingTokens.TripleExtraLarge)
                    .clip(CircleShape)
                    .dynamicSkeletonBlockBackground(resolvedBlockColor)
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
            Column {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(AppSpacingTokens.Small + AppSpacingTokens.Micro)
                        .clip(textPlaceholderShape)
                        .dynamicSkeletonBlockBackground(resolvedBlockColor)
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(AppSpacingTokens.ExtraSmall)
                        .clip(textPlaceholderShape)
                        .dynamicSkeletonBlockBackground(resolvedBlockColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        // 正文占位条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppSpacingTokens.Small + AppSpacingTokens.Micro)
                .clip(textPlaceholderShape)
                .dynamicSkeletonBlockBackground(resolvedBlockColor)
        )
        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(AppSpacingTokens.Small + AppSpacingTokens.Micro)
                .clip(textPlaceholderShape)
                .dynamicSkeletonBlockBackground(resolvedBlockColor)
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        // 封面块（16:10，与视频卡一致）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(cardShape)
                .dynamicSkeletonBlockBackground(resolvedBlockColor)
        )

        Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))
        // 底部操作占位
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Micro)
                        .clip(actionPlaceholderShape)
                        .dynamicSkeletonBlockBackground(resolvedBlockColor)
                )
            }
        }
    }
}

/**
 * 骨架块背景:扫光在 draw 阶段解析,逐帧仅触发本层重绘,不触发重组。
 */
private fun Modifier.dynamicSkeletonBlockBackground(color: () -> Color): Modifier =
    skeletonBackground(color)
