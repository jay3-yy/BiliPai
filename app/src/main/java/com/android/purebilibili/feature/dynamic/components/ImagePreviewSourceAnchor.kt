package com.android.purebilibili.feature.dynamic.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

@Stable
private class NonSnapshotImagePreviewRectState(
    override var value: Rect? = null,
) : MutableState<Rect?> {
    override fun component1(): Rect? = value
    override fun component2(): (Rect?) -> Unit = { value = it }
}

/**
 * 记录缩略图在窗口中的坐标，供 [ImagePreviewDialog] 做「落位/回位」morph。
 *
 * 使用非快照状态引用承载布局坐标，避免列表滚动时 `onGloballyPositioned` 逐帧回写快照状态触发组合阶段重算；
 * 预览开合与翻页由 [ImagePreviewDialog] 内部激活源矩形的 `StateFlow` 驱动显隐刷新。
 */
@Composable
fun rememberImagePreviewSourceRect(): MutableState<Rect?> {
    return remember { NonSnapshotImagePreviewRectState() }
}

fun Modifier.imagePreviewSourceBounds(target: MutableState<Rect?>): Modifier =
    onGloballyPositioned { coordinates ->
        target.value = coordinates.boundsInWindow()
    }

fun Modifier.imagePreviewGallerySourceBounds(
    target: MutableMap<Int, Rect>,
    pageIndex: Int,
): Modifier = onGloballyPositioned { coordinates ->
    target[pageIndex] = coordinates.boundsInWindow()
}

/**
 * 评论图片链路的落位锚点：缩略图窗口坐标 + 缩略图真实圆角。
 * 由 [CommentPictures] 在捕获处构造（单图 Card / 九宫格 Field 圆角不同），
 * 经 onImagePreview 回调透传到 ImagePreviewDialog 的 sourceRect/sourceCornerRadiusDp。
 */
data class ImagePreviewSourceAnchor(
    val rect: Rect,
    val cornerRadiusDp: Float,
    /** Bounds for the other thumbnails in this same gallery, keyed by preview page index. */
    val galleryRects: Map<Int, Rect> = emptyMap(),
    /** 稳定身份键（如图片 URL）：预览期间隐藏原位卡片优先按它匹配，几何判定兜底。 */
    val sourceKey: String? = null,
)
