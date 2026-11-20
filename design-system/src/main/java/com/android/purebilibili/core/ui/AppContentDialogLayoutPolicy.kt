package com.android.purebilibili.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * 内容型窗口 Dialog 的平板安全尺寸策略。
 *
 * 关闭平台默认宽度后自行限宽居中，避免大屏上被拉成过宽或异常竖条。
 */
@Immutable
data class AppContentDialogLayoutPolicy(
    val usePlatformDefaultWidth: Boolean,
    val horizontalPaddingDp: Int,
    val minWidthDp: Int,
    val maxWidthDp: Int,
)

/** 短确认 / Alert 类内容（约 360dp）。 */
fun resolveAppCompactContentDialogLayoutPolicy(): AppContentDialogLayoutPolicy {
    return resolveAppContentDialogLayoutPolicy(maxWidthDp = 360)
}

/** 表单 / 滑块 / 一般面板（约 420dp）。 */
fun resolveAppContentDialogLayoutPolicy(
    maxWidthDp: Int = 420,
    minWidthDp: Int = 280,
    horizontalPaddingDp: Int = 24,
): AppContentDialogLayoutPolicy {
    return AppContentDialogLayoutPolicy(
        usePlatformDefaultWidth = false,
        horizontalPaddingDp = horizontalPaddingDp.coerceAtLeast(0),
        minWidthDp = minWidthDp.coerceAtLeast(0),
        maxWidthDp = maxWidthDp.coerceAtLeast(minWidthDp.coerceAtLeast(0)),
    )
}

/** 列表 / 批量选择等稍宽面板（约 560dp）。 */
fun resolveAppExpandedContentDialogLayoutPolicy(): AppContentDialogLayoutPolicy {
    return resolveAppContentDialogLayoutPolicy(maxWidthDp = 560)
}

/**
 * 合并调用方 DialogProperties，并强制关闭平台默认宽度。
 */
fun resolveAppContentDialogProperties(
    base: DialogProperties = DialogProperties(),
    usePlatformDefaultWidth: Boolean = false,
): DialogProperties {
    return DialogProperties(
        dismissOnBackPress = base.dismissOnBackPress,
        dismissOnClickOutside = base.dismissOnClickOutside,
        securePolicy = base.securePolicy,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        decorFitsSystemWindows = base.decorFitsSystemWindows,
    )
}

/**
 * 内容弹窗表面宽度：满宽 → 水平边距 → 限宽。
 * 默认附加 [wrapContentHeight]；需要固定高度的调用方传 [wrapHeight] = false。
 */
fun Modifier.appContentDialogWidth(
    policy: AppContentDialogLayoutPolicy = resolveAppContentDialogLayoutPolicy(),
    wrapHeight: Boolean = true,
): Modifier {
    val widthConstrained = this
        .fillMaxWidth()
        .padding(horizontal = policy.horizontalPaddingDp.dp)
        .widthIn(
            min = policy.minWidthDp.dp,
            max = policy.maxWidthDp.dp,
        )
    return if (wrapHeight) {
        widthConstrained.wrapContentHeight()
    } else {
        widthConstrained
    }
}
