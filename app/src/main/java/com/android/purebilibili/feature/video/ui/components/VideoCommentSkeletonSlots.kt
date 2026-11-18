package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.skeleton.CommentListItemSkeleton

/** 真实评论和临时骨架分开复用槽位，避免占位绘制状态带入实际内容。 */
internal const val VIDEO_COMMENT_ITEM_CONTENT_TYPE = "video_comment_item"
private const val VIDEO_COMMENT_SKELETON_CONTENT_TYPE = "video_comment_skeleton"

private const val VIDEO_COMMENT_FADE_IN_MILLIS = 220
private const val VIDEO_COMMENT_FADE_OUT_MILLIS = 160
private const val VIDEO_COMMENT_PLACEMENT_DAMPING = 0.9f
private const val VIDEO_COMMENT_PLACEMENT_STIFFNESS = 220f

/** 顶层评论预览会异步改变行高，邻近评论随布局更新，避免补齐时再跑一段弹簧位移。 */
@Composable
internal fun LazyItemScope.videoCommentItemModifier(animatePlacement: Boolean = false): Modifier =
    if (LocalAppThemeConfig.current.uiEntranceAnimationEnabled) {
        Modifier.animateItem(
            fadeInSpec = tween(durationMillis = VIDEO_COMMENT_FADE_IN_MILLIS),
            placementSpec = if (animatePlacement) spring(
                dampingRatio = VIDEO_COMMENT_PLACEMENT_DAMPING,
                stiffness = VIDEO_COMMENT_PLACEMENT_STIFFNESS,
            ) else null,
            fadeOutSpec = tween(durationMillis = VIDEO_COMMENT_FADE_OUT_MILLIS),
        )
    } else {
        Modifier
    }

/**
 * 骨架「稳定槽位」：拆成多个独立 lazy item，而不是以前那种「1 个 item 里塞 N 行」。
 *
 * 旧写法把 5 行骨架塞进同一个 item，数据到达后这个 item 被 N 个真实评论 item 取代，
 * 锚点 key 变化 + item 内部高度变化会一起把列表整体顶一下。改成与真实评论同量级的
 * 多个独立 item 之后，保留逐项测量；加载结束立即撤掉全部占位绘制。
 *
 * @param slotCount 需要铺的槽位数量，用 [com.android.purebilibili.feature.video.viewmodel.resolveVideoCommentSkeletonSlotCount] 按视口高度估算。
 * @param keyPrefix 槽位 key 前缀，同一页面里多处使用骨架时不能重复。
 */
internal fun LazyListScope.videoCommentSkeletonItems(
    slotCount: Int,
    keyPrefix: String,
) {
    val slots = List(slotCount.coerceAtLeast(1)) { it }
    items(
        items = slots,
        key = { "$keyPrefix$it" },
        contentType = { VIDEO_COMMENT_SKELETON_CONTENT_TYPE },
    ) { slotIndex ->
        CommentListItemSkeleton(
            // 占位不是实际评论；加载结束直接移除，避免离场图层在静止列表里残留。
            modifier = Modifier,
            slotIndex = slotIndex,
        )
    }
}
