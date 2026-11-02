package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.withTimeoutOrNull

/** 按可见字符命中，避免跨行链接的外接矩形覆盖普通正文或行尾空白。 */
internal fun TextLayoutResult.replyLinkAt(position: Offset): LinkAnnotation? {
    val text = layoutInput.text
    val offset = getOffsetForPosition(position)
    // 插入位置可能落在字符右侧，左右两个字符中只有实际包围触点的字符可命中。
    return listOf(offset, offset - 1).firstNotNullOfOrNull { index ->
        if (index !in 0 until text.length || !getBoundingBox(index).contains(position)) {
            null
        } else {
            text.getLinkAnnotations(index, index + 1).firstOrNull()?.item
        }
    }
}

/** 链接短按与评论长按共用一次手势；移动超出阈值后交给列表滚动。 */
internal suspend fun PointerInputScope.detectReplyLinkGestures(
    linkAt: (Offset) -> LinkAnnotation?,
    onLinkClick: (LinkAnnotation) -> Unit,
    onLongPress: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val link = linkAt(down.position) ?: return@awaitEachGesture
        // 保留 LinkAnnotation 的键盘及无障碍入口，触摸只在这里分发一次。
        down.consume()
        var released = false
        var cancelled = false
        var upPosition = down.position
        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (!released && !cancelled) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                cancelled = change == null || event.changes.any { it.id != down.id && it.pressed } ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                if (!cancelled && change != null && !change.pressed) {
                    upPosition = change.position
                    released = true
                    change.consume()
                }
            }
            true
        } == true
        if (!finished) {
            onLongPress()
            // 长按打开菜单后的抬起不能再次打开链接。
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial)
                    .changes.firstOrNull { it.id == down.id }
                change?.consume()
                if (change == null || !change.pressed) break
            }
        } else if (released && linkAt(upPosition) == link) {
            onLinkClick(link)
        }
    }
}
