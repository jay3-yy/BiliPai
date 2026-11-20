package com.android.purebilibili.core.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import com.android.purebilibili.core.ui.LocalAppThemeConfig

// A damped spring keeps interrupted/reversed reflows continuous, without a page fade.
private const val VIDEO_LIST_REFLOW_STIFFNESS = 220f
private const val VIDEO_LIST_REFLOW_DAMPING = 0.9f

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.videoListBoundsAnimation(
    scope: LookaheadScope,
    enabled: Boolean = true,
    targetModifier: Modifier = Modifier,
): Modifier =
    if (enabled && LocalAppThemeConfig.current.uiEntranceAnimationEnabled) {
        animateBounds(
            lookaheadScope = scope,
            modifier = targetModifier,
            boundsTransform = { _, _ ->
                spring(dampingRatio = VIDEO_LIST_REFLOW_DAMPING, stiffness = VIDEO_LIST_REFLOW_STIFFNESS)
            },
            animateMotionFrameOfReference = false,
        )
    } else then(targetModifier)

/** Keep the lazy placement node outside the local size animation's coordinate space. */
@Composable
internal fun AnimatedVideoListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useLookaheadBounds: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val animateItemBounds = useLookaheadBounds && enabled &&
        LocalAppThemeConfig.current.uiEntranceAnimationEnabled
    Box(modifier = modifier) {
        if (!animateItemBounds) {
            // 只有实际做尺寸形变的卡片才需要预测布局；可删除的卡片自己负责收起。
            Box(content = content)
            return@Box
        }
        LookaheadScope {
            // Unlike animateContentSize, animateBounds also interpolates the child's constraints,
            // so covers resize gradually when a grid cell's fixed width changes.
            Box(modifier = Modifier.videoListBoundsAnimation(this), content = content)
        }
    }
}

/** Stable item keys and one child tree; disable enter/exit ghosts during column changes. */
@Composable
internal fun LazyGridItemScope.videoListItemModifier(enabled: Boolean = true): Modifier =
    if (enabled && LocalAppThemeConfig.current.uiEntranceAnimationEnabled) {
        Modifier.animateItem(
            fadeInSpec = null,
            fadeOutSpec = null,
            placementSpec = spring(dampingRatio = VIDEO_LIST_REFLOW_DAMPING, stiffness = VIDEO_LIST_REFLOW_STIFFNESS),
        )
    } else Modifier

@Composable
internal fun LazyStaggeredGridItemScope.videoListItemModifier(enabled: Boolean = true): Modifier =
    if (enabled && LocalAppThemeConfig.current.uiEntranceAnimationEnabled) {
        Modifier.animateItem(
            fadeInSpec = null,
            fadeOutSpec = null,
            placementSpec = spring(dampingRatio = VIDEO_LIST_REFLOW_DAMPING, stiffness = VIDEO_LIST_REFLOW_STIFFNESS),
        )
    } else Modifier

internal class VideoListLayoutControl(
    val singleColumn: Boolean,
    val toggle: () -> Unit,
)

@Composable
internal fun rememberVideoListLayoutControl(
    defaultSingleColumn: Boolean = false,
    key: Any? = Unit,
): VideoListLayoutControl {
    var singleColumn by rememberSaveable(key) { mutableStateOf(defaultSingleColumn) }
    return VideoListLayoutControl(
        singleColumn = singleColumn,
        toggle = { singleColumn = !singleColumn },
    )
}

@Composable
internal fun VideoListLayoutToggle(
    singleColumn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
) {
    AppIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        AppIcon(
            imageVector = if (singleColumn) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda,
            contentDescription = if (singleColumn) "切换为双列" else "切换为单列",
            modifier = iconModifier,
        )
    }
}

/**
 * 视频列表列数策略：用户单列偏好优先；网格模式下每列保证 ≥320dp 的最小可读卡宽，
 * ≥960dp 的大窗口推导出第 3 列并封顶，避免大平板内容利用不足。
 */
internal fun resolveVideoListColumns(singleColumn: Boolean, availableWidthDp: Float): Int {
    if (singleColumn || availableWidthDp < 320f) return 1
    return (availableWidthDp / 320f).toInt().coerceIn(2, 3)
}
