package com.android.purebilibili.feature.live.components

import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.android.purebilibili.core.ui.AppPopupSurface
import com.android.purebilibili.core.ui.AppPopupSurfaceType
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.video.danmaku.DanmakuTapAnchor
import com.android.purebilibili.feature.video.ui.components.DanmakuToolbarPopupProperties
import com.android.purebilibili.feature.video.ui.components.resolveDanmakuToolbarPosition

/** 直播消息工具条不提供单条点赞；举报继续使用直播消息携带的签名信息。 */
@Composable
internal fun LiveDanmakuActionToolbar(
    item: LiveDanmakuItem,
    anchor: DanmakuTapAnchor,
    onDismiss: () -> Unit,
    onUserClick: () -> Unit,
    onReport: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val maxWidth = with(density) { (anchor.viewportRight - anchor.viewportLeft).toDp() - 12.dp }.coerceAtLeast(40.dp)
    val position = remember(anchor, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                resolveDanmakuToolbarPosition(anchor, windowSize, popupContentSize, gap)
        }
    }
    com.android.purebilibili.core.ui.LocalNavigationBackHandler(enabled = true, onBackCompleted = onDismiss)
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = DanmakuToolbarPopupProperties,
    ) {
        // 材质由公共弹出表面选择，玻璃和原生路径共用消息操作与定位。
        AppPopupSurface(
            type = AppPopupSurfaceType.DIALOG,
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.widthIn(max = maxWidth),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp)) {
                AppIconButton(onClick = onUserClick, enabled = item.uid > 0L, modifier = Modifier.size(40.dp)) {
                    AppIcon(Icons.Outlined.Person, "个人空间", Modifier.size(20.dp))
                }
                AppIconButton(onClick = {
                    clipboard.setText(AnnotatedString(item.text))
                    onDismiss()
                }, modifier = Modifier.size(40.dp)) {
                    AppIcon(Icons.Outlined.ContentCopy, "复制弹幕", Modifier.size(20.dp))
                }
                AppIconButton(onClick = onReport, modifier = Modifier.size(40.dp)) {
                    AppIcon(Icons.Outlined.Report, "举报弹幕", Modifier.size(20.dp))
                }
            }
        }
    }
}
