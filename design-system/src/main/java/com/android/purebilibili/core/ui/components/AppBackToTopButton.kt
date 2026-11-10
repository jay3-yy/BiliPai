package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon

/**
 * Shared chrome for long-list screens that offer an explicit jump back to the top.
 * The caller owns placement and the actual scrolling behavior.
 */
@Composable
fun AppBackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
    contentDescription: String = "回到顶部",
) {
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION)
    val motionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motionEnabled) {
            fadeIn(animationSpec = AppMotionTokens.standardSpec()) +
            scaleIn(animationSpec = AppMotionTokens.standardSpec(), initialScale = 0.92f)
        } else androidx.compose.animation.EnterTransition.None,
        exit = if (motionEnabled) {
            fadeOut(animationSpec = AppMotionTokens.expressiveSpec()) +
            scaleOut(animationSpec = AppMotionTokens.expressiveSpec(), targetScale = 0.92f)
        } else androidx.compose.animation.ExitTransition.None,
    ) {
        AppSmallFloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            modifier = buttonModifier,
            containerColor = containerColor,
            contentColor = AppContentColors.accent(),
        ) {
            AppIcon(
                imageVector = rememberAppChevronUpIcon(),
                contentDescription = contentDescription,
            )
        }
    }
}
