package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor
import com.android.purebilibili.core.ui.skeleton.rememberSkeletonSweepState

/** 仅加载分支挂载；所有占位共用扫光，页面隐藏或移出视口时保留静态骨架。 */
@Composable
internal fun BangumiPlayerSkeleton(
    contentMode: BangumiPlayerContentMode,
    modifier: Modifier = Modifier,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val pulse = rememberSkeletonSweepState(
        animated = animationGate.active,
    )
    val color = rememberContentSkeletonBlockColor(pulse)
    val comments = contentMode == BangumiPlayerContentMode.Comments ||
        contentMode == BangumiPlayerContentMode.CommentOverlay
    LazyColumn(
        modifier = modifier
            .then(animationGate.modifier)
            .clearAndSetSemantics { },
        userScrollEnabled = false,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (contentMode == BangumiPlayerContentMode.Tabs || comments) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    repeat(2) { ContentSkeletonBlock(color, Modifier.width(72.dp).height(28.dp)) }
                }
            }
        }
        if (comments) {
            items(5) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContentSkeletonBlock(color, Modifier.size(36.dp), CircleShape)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ContentSkeletonBlock(color, Modifier.fillMaxWidth(0.35f).height(16.dp))
                        ContentSkeletonBlock(color, Modifier.fillMaxWidth().height(16.dp))
                        ContentSkeletonBlock(color, Modifier.fillMaxWidth(0.75f).height(16.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ContentSkeletonBlock(color, Modifier.fillMaxWidth(0.3f).aspectRatio(0.75f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ContentSkeletonBlock(color, Modifier.fillMaxWidth(0.9f).height(28.dp))
                        repeat(4) {
                            ContentSkeletonBlock(color, Modifier.fillMaxWidth().height(16.dp))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    repeat(5) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContentSkeletonBlock(color, Modifier.size(28.dp), CircleShape)
                            ContentSkeletonBlock(color, Modifier.width(28.dp).height(12.dp))
                        }
                    }
                }
            }
            item { ContentSkeletonBlock(color, Modifier.fillMaxWidth(0.8f).height(24.dp)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { ContentSkeletonBlock(color, Modifier.weight(1f).height(28.dp)) }
                }
            }
            items(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { ContentSkeletonBlock(color, Modifier.weight(1f).height(64.dp)) }
                }
            }
        }
    }
}
