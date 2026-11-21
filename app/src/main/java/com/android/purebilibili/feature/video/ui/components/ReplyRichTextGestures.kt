package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 移除传递给 BasicText 的 [LinkAnnotation]，防止 Compose 内置 `TextLinkScope`
 * 生成跨行外接矩形及最小触控扩展区吞掉普通文字、行尾空白或相邻行的点击，同时保留原有文本与样式。
 */
internal fun AnnotatedString.stripLinkAnnotations(): AnnotatedString {
    if (isEmpty() || !hasLinkAnnotations(0, length)) return this
    return flatMapAnnotations { range ->
        val link = range.item as? LinkAnnotation ?: return@flatMapAnnotations listOf(range)
        val baseStyle = link.styles?.style
        if (baseStyle != null) {
            listOf(AnnotatedString.Range(item = baseStyle, start = range.start, end = range.end))
        } else {
            emptyList()
        }
    }
}

/** 构造携带完整 [LinkAnnotation] 的 [TextLayoutResult]，供语义查询与回归测试读取。 */
internal fun TextLayoutResult.withSourceText(source: AnnotatedString): TextLayoutResult {
    val input = layoutInput
    if (input.text == source || input.text.text != source.text) return this
    return copy(
        layoutInput = TextLayoutInput(
            text = source,
            style = input.style,
            placeholders = input.placeholders,
            maxLines = input.maxLines,
            softWrap = input.softWrap,
            overflow = input.overflow,
            density = input.density,
            layoutDirection = input.layoutDirection,
            fontFamilyResolver = input.fontFamilyResolver,
            constraints = input.constraints,
        )
    )
}

/**
 * 为已剥离触摸链接层的富文本补充原始 [source] 与布局结果语义，
 * 使无障碍与 UI 测试仍可完整读取 [LinkAnnotation]。
 */
internal fun Modifier.replyRichTextSemantics(
    source: AnnotatedString,
    layoutResultProvider: () -> TextLayoutResult?,
): Modifier = semantics {
    text = source
    getTextLayoutResult { results ->
        val layout = layoutResultProvider() ?: return@getTextLayoutResult false
        results.add(layout)
        true
    }
}

/** 按可见字符与内联占位框精确命中 [source] 中的链接，避免外接矩形或触控扩展误伤普通正文。 */
internal fun TextLayoutResult.replyLinkAt(
    position: Offset,
    source: AnnotatedString = layoutInput.text,
): LinkAnnotation? {
    if (source.text != layoutInput.text.text) return null
    // 内联播放图标按占位区域排版，字符框不一定覆盖它的实际可见区域。
    placeholderRects.forEachIndexed { index, rect ->
        if (rect != null && rect.contains(position)) {
            val placeholder = layoutInput.placeholders.getOrNull(index) ?: return@forEachIndexed
            return source.getLinkAnnotations(placeholder.start, placeholder.end).firstOrNull()?.item
        }
    }
    val offset = getOffsetForPosition(position)
    val candidates = listOfNotNull(
        offset,
        offset - 1,
        // 代理对 emoji 右半区落点时，offset - 1 为低代理项，需回退到高代理项才能命中包围盒。
        (offset - 2).takeIf { offset - 1 in 1 until source.length && source[offset - 1].isLowSurrogate() },
    )
    // 插入位置可能落在字符右侧，左右字符中只有实际包围触点的字符可命中。
    return candidates.firstNotNullOfOrNull { index ->
        if (index !in 0 until source.length || !getBoundingBox(index).contains(position)) {
            null
        } else {
            source.getLinkAnnotations(index, index + 1).firstOrNull()?.item
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
        // 触摸链接由本手势按字符与占位框精确分发，语义树仍保留完整 LinkAnnotation 信息。
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
