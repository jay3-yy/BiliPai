package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import com.android.purebilibili.feature.video.ui.components.CommentEntryBarContent
import com.android.purebilibili.feature.video.ui.components.resolveBottomInputBarPlaceholderTextColor
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop

/**
 * 动态评论输入入口。
 *
 * 只负责打开共享的 [com.android.purebilibili.feature.video.ui.components.CommentInputDialog]；
 * 动态不再保留可编辑的内联输入框。
 */
@Composable
internal fun DynamicCommentEntryBar(
    onClick: () -> Unit,
    liquidGlassEnabled: Boolean,
    backdrop: MiuixBackdrop?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val shape = resolveSharedBottomBarCapsuleShape()
    val textColor = resolveBottomInputBarPlaceholderTextColor(
        inputContainerColor = if (liquidGlassEnabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            containerColor
        },
        onSurfaceColor = AppContentColors.primaryText(),
        onSurfaceVariantColor = AppContentColors.secondaryText(),
    )

    if (liquidGlassEnabled) {
        BottomBarMatchedReusableLiquidDock(
            shape = shape,
            modifier = modifier
                .height(44.dp)
                .clip(shape),
            backdrop = backdrop,
            reuseEnabled = true,
            materialEnabled = liquidGlassEnabled,
            elasticFeedbackEnabled = true,
            drawShellLens = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(44f),
            // 内容只含文字与图标，不需要再录制玻璃外壳。
            captureShellForContent = false,
        ) { liquidChromeActive ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (liquidChromeActive) Color.Transparent else containerColor)
                    .appActionClickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommentEntryBarContent(
                    textColor = textColor,
                    iconSize = 20.dp,
                    iconSpacing = 6.dp,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .height(48.dp)
                .appElasticPress()
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(containerColor)
                .appActionClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CommentEntryBarContent(
                textColor = textColor,
                iconSize = 18.dp,
                iconSpacing = 8.dp,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
