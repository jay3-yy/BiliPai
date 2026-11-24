package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor
import com.android.purebilibili.core.ui.isAppInDarkTheme

/**
 * 液态玻璃渲染线的 iOS 26 圆形悬浮操作按钮。
 */
@Composable
internal fun AppGlassFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    small: Boolean,
    interactionSource: MutableInteractionSource?,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme()
    val defaultMaterialShape = if (small) FloatingActionButtonDefaults.smallShape else FloatingActionButtonDefaults.shape
    val resolvedShape = if (shape == defaultMaterialShape) CircleShape else shape
    val buttonSize = if (small) 44.dp else 56.dp
    val rimColor = Color.White.copy(alpha = if (dark) 0.28f else 0.52f)
    val resolvedInteraction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by resolvedInteraction.collectIsPressedAsState()
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && motionEnabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = "AppGlassFabPress",
    )

    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor,
    ) {
        ProvideAppActionContentColor(contentColor) {
            Box(
                modifier = modifier
                    .graphicsLayer {
                        if (pressScale != 1f) {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                    }
                    .size(buttonSize)
                    .shadow(
                        elevation = if (dark) 6.dp else 8.dp,
                        shape = resolvedShape,
                        ambientColor = Color.Black.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                    )
                    .clip(resolvedShape)
                    .background(containerColor)
                    .border(AppGlassTokens.SpecularBorderWidth, rimColor, resolvedShape)
                    .clickable(
                        interactionSource = resolvedInteraction,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
