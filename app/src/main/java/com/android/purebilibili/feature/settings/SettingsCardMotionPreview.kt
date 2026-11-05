package com.android.purebilibili.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.R
import kotlin.math.sin

/** 用本地示意场景分别展示各开关的作用；不启动播放器或改变真实页面的转场状态。 */
@Composable
internal fun SettingsCardMotionPreview(
    enabled: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val accent = AppContentColors.accent()
    val surface = MaterialTheme.colorScheme.surface
    val ink = AppContentColors.primaryText()
    val caption = stringResource(if (enabled) R.string.preview_caption_card_entrance_on else R.string.preview_caption_card_entrance_off)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress().coerceIn(0f, 1f)
            val contentHeight = size.height - 36.dp.toPx()
            // 仅淡入示意卡片，不改变槽位几何。
            drawRect(surface)
            repeat(4) { index ->
                val opacity = if (enabled) ((p - index * 0.15f) / 0.45f).coerceIn(0f, 1f) else 1f
                val w = size.width * 0.43f
                val h = contentHeight * 0.35f
                val x = size.width * (0.05f + (index % 2) * 0.47f)
                val y = contentHeight * (0.07f + (index / 2) * 0.46f)
                drawPreviewCover(Rect(Offset(x, y), Size(w, h)), accent, opacity, 0f)
                drawLine(ink.copy(alpha = opacity * 0.4f), Offset(x, y + h + 5.dp.toPx()),
                    Offset(x + w * 0.75f, y + h + 5.dp.toPx()), 3.dp.toPx())
            }
        }
        AppText(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

private fun DrawScope.drawPreviewCover(rect: Rect, accent: Color, opacity: Float, time: Float) {
    clipPath(Path().apply { addRect(rect) }) {
        drawRect(accent.copy(alpha = opacity * 0.35f), rect.topLeft, rect.size)
        drawCircle(accent.copy(alpha = opacity), rect.height * 0.18f,
            Offset(rect.left + rect.width * (0.65f + 0.12f * sin(time * Math.PI * 2).toFloat()), rect.top + rect.height * 0.3f))
        drawPath(Path().apply {
            moveTo(rect.left, rect.bottom)
            lineTo(rect.left + rect.width * 0.3f, rect.top + rect.height * 0.35f)
            lineTo(rect.left + rect.width * 0.65f, rect.top + rect.height * 0.7f)
            lineTo(rect.right, rect.top + rect.height * 0.45f)
            lineTo(rect.right, rect.bottom)
            close()
        }, accent.copy(alpha = opacity))
    }
}
