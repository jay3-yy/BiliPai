package com.android.purebilibili.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.LocalFloatingChromeBackdrop
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** App-level back-to-top button with real backdrop glass and a safe opaque fallback. */
@Composable
fun AppLiquidGlassBackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalFloatingChromeBackdrop.current,
    contentDescription: String = "回到顶部",
) {
    val glassActive = LocalAppThemeConfig.current.liquidGlassEnabled && !isLowBlurBudgetForced()
    val localBackdrop = if (glassActive && backdrop == null) rememberLayerBackdrop() else null
    val effectiveBackdrop = backdrop ?: localBackdrop
    val dockColor = AppSurfaceTokens.surfaceContainerHigh()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.offset(y = (-20).dp),
        enter = fadeIn(animationSpec = AppMotionTokens.standardSpec()) +
            scaleIn(animationSpec = AppMotionTokens.standardSpec(), initialScale = 0.92f),
        exit = fadeOut(animationSpec = AppMotionTokens.expressiveSpec()) +
            scaleOut(animationSpec = AppMotionTokens.expressiveSpec(), targetScale = 0.92f),
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (glassActive && backdrop == null && localBackdrop != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0f)
                        .layerBackdrop(localBackdrop)
                        .background(AppSurfaceTokens.background())
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (glassActive && effectiveBackdrop != null) {
                            Modifier.biliPaiFloatingDockShell(
                                backdrop = effectiveBackdrop,
                                containerColor = dockColor,
                                pressProgress = 0f,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier.background(dockColor, CircleShape)
                        }
                    )
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = rememberAppChevronUpIcon(),
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
