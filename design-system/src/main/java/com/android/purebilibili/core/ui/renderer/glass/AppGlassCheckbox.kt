package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppCheckboxColors
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.liquidControlThemeColor
import com.android.purebilibili.core.ui.resolveButtonContentColor

/**
 * 液态玻璃渲染线的 iOS 圆形勾选复选框。
 */
@Composable
internal fun AppGlassCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    colors: AppCheckboxColors?,
    interactionSource: MutableInteractionSource?,
) {
    val dark = isAppInDarkTheme()
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val activeFill = colors?.checkedColor ?: liquidControlThemeColor()
    val inactiveBorder = colors?.uncheckedColor ?: AppContentColors.secondaryText().copy(alpha = if (dark) 0.48f else 0.36f)
    val targetFill = if (checked) activeFill else AppGlassTokens.neutralPillFill(dark)
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = if (motionEnabled) tween(160) else tween(0),
        label = "AppGlassCheckboxFill",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (motionEnabled) spring(dampingRatio = 0.72f, stiffness = 560f) else tween(0),
        label = "AppGlassCheckboxCheck",
    )
    val checkColor = resolveButtonContentColor(activeFill, Color.White, MaterialTheme.colorScheme)
    val borderColor = if (checked) {
        Color.White.copy(alpha = if (dark) 0.28f else 0.45f)
    } else {
        inactiveBorder
    }
    val resolvedInteraction = interactionSource ?: remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .let { base ->
                if (onCheckedChange != null) {
                    base.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        interactionSource = resolvedInteraction,
                        indication = null,
                        onValueChange = onCheckedChange,
                    )
                } else base
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(fill)
                .border(if (checked) AppGlassTokens.SpecularBorderWidth else 1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checkScale > 0.01f) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    val s = checkScale
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.5f
                    fun pt(nx: Float, ny: Float) = Offset(
                        x = cx + (nx * size.width - cx) * s,
                        y = cy + (ny * size.height - cy) * s,
                    )
                    val p0 = pt(0.14f, 0.52f)
                    val p1 = pt(0.40f, 0.78f)
                    val p2 = pt(0.86f, 0.24f)
                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                    }
                    drawPath(
                        path = path,
                        color = checkColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}
