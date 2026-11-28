package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed
import com.android.purebilibili.core.ui.blur.rememberContinuousBackdrop
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** 视频与直播共用弹幕面板外壳，内容及预览、保存回调由各自播放页面提供。 */
@Composable
internal fun DanmakuSettingsPanelFrame(
    layoutPolicy: DanmakuSettingsPanelLayoutPolicy,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val glassSurface = LocalPlayerGlassSettingsSurface.current
    val shape = RoundedCornerShape(12.dp)
    val backdrop = LocalDanmakuPanelBlurBackdrop.current
        ?.takeIf { LocalChromeBackdropRecordingAllowed.current }
    val blurModifier = if (backdrop != null) {
        val radius = if (isLowBlurBudgetForced()) 6.dp else 24.dp
        val blurPx = with(LocalDensity.current) { radius.toPx() }
        Modifier.drawBackdrop(
            backdrop = rememberContinuousBackdrop(backdrop),
            shape = { shape },
            effects = { blur(blurPx, blurPx) },
            onDrawSurface = { drawRect(containerColor.copy(alpha = 0.78f)) },
        )
    } else Modifier
    AppSurface(
        modifier = modifier
            .then(
                if (layoutPolicy.anchor == DanmakuSettingsPanelAnchor.End) {
                    Modifier.width(layoutPolicy.maxWidthDp.dp)
                } else {
                    Modifier.widthIn(max = layoutPolicy.maxWidthDp.dp).fillMaxWidth()
                }
            )
            .padding(
                start = if (glassSurface) 0.dp else layoutPolicy.horizontalPaddingDp.dp,
                end = if (glassSurface) 0.dp else layoutPolicy.horizontalPaddingDp.dp,
                bottom = if (glassSurface) 0.dp else layoutPolicy.bottomPaddingDp.dp,
            )
            .heightIn(max = layoutPolicy.maxHeightDp.dp)
            .then(blurModifier)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        color = if (glassSurface || backdrop != null) Color.Transparent else containerColor,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}
