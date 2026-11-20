package com.android.purebilibili.feature.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock

/** 骨架投稿卡共用的 contentType：骨架 item 彼此同型，LazyGrid 可复用其内部状态。 */
internal const val SPACE_SKELETON_CARD_CONTENT_TYPE = "space_skeleton_card"

/**
 * UP 空间首屏骨架的 item 提供者，沿用正式列表的网格排版。骨架和正式 Pager 各自管理
 * 分页槽位，外层持有各标签的滚动状态。
 *
 * 列数、封面比例、内边距、头部形态都由调用方按真实内容同规则算好传入；色块用固定色
 * 而非呼吸脉冲（`rememberStaticSkeletonBlockColor`），避免「灰块呼吸 + 内容淡入」叠成闪烁。
 */
internal fun LazyStaggeredGridScope.SpaceLoadingSkeleton(
    columns: Int,
    coverAspectRatio: Float,
    bannerHeightDp: Float,
    useExpandedHeader: Boolean,
    blockColor: Color,
) {
    item(key = "space_skeleton_header", span = StaggeredGridItemSpan.FullLine) {
        SpaceHeaderSkeleton(
            bannerHeightDp = bannerHeightDp,
            useExpandedHeader = useExpandedHeader,
            blockColor = blockColor,
        )
    }
    // 主标签条占位：与真实 SpaceContentTabs 行同高，避免标签出现时整页下移。
    item(key = "space_skeleton_tabs", span = StaggeredGridItemSpan.FullLine) {
        ContentSkeletonBlock(
            blockColor,
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            AppShapes.container(ContainerLevel.Pill),
        )
    }
    item(key = "space_skeleton_section", span = StaggeredGridItemSpan.FullLine) {
        ContentSkeletonBlock(blockColor, Modifier.width(92.dp).height(20.dp))
    }
    items(
        count = columns * 4,
        key = { index -> "space_skeleton_card_$index" },
        contentType = { SPACE_SKELETON_CARD_CONTENT_TYPE },
    ) { index ->
        SpaceVideoCardSkeleton(
            slotIndex = index,
            coverAspectRatio = coverAspectRatio,
            blockColor = blockColor,
        )
    }
}

@Composable
private fun SpaceHeaderSkeleton(
    bannerHeightDp: Float,
    useExpandedHeader: Boolean,
    blockColor: Color,
) {
    Column(Modifier.fillMaxWidth()) {
        ContentSkeletonBlock(
            blockColor,
            Modifier.fillMaxWidth().height(bannerHeightDp.dp),
            AppShapes.container(ContainerLevel.Card),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContentSkeletonBlock(blockColor, Modifier.size(76.dp), CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.36f).height(20.dp))
                ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.62f).height(13.dp))
                ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.48f).height(13.dp))
            }
            if (useExpandedHeader) {
                Spacer(Modifier.width(24.dp))
                Column(
                    modifier = Modifier
                        .weight(0.8f, fill = false)
                        .widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) {
                            ContentSkeletonBlock(
                                blockColor,
                                Modifier.weight(1f).height(24.dp),
                            )
                        }
                    }
                    ContentSkeletonBlock(
                        blockColor,
                        Modifier.fillMaxWidth().height(40.dp),
                        AppShapes.container(ContainerLevel.Pill),
                    )
                }
            } else {
                ContentSkeletonBlock(
                    blockColor,
                    Modifier.width(88.dp).height(40.dp),
                    AppShapes.container(ContainerLevel.Pill),
                )
            }
        }
    }
}

@Composable
private fun SpaceVideoCardSkeleton(
    slotIndex: Int,
    coverAspectRatio: Float,
    blockColor: Color,
) {
    Column(Modifier.fillMaxWidth()) {
        ContentSkeletonBlock(
            blockColor,
            Modifier.fillMaxWidth().aspectRatio(coverAspectRatio),
            AppShapes.container(ContainerLevel.Card),
        )
        Spacer(Modifier.height(8.dp))
        ContentSkeletonBlock(
            blockColor,
            Modifier
                .fillMaxWidth(if (slotIndex % 2 == 0) 0.9f else 0.78f)
                .height(15.dp),
        )
        Spacer(Modifier.height(6.dp))
        ContentSkeletonBlock(
            blockColor,
            Modifier
                .fillMaxWidth(if (slotIndex % 3 == 0) 0.52f else 0.44f)
                .height(12.dp),
        )
    }
}
