package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale

/** Same circular dock material used by floating chrome, without sampling its own icon. */
object BiliPaiTopBarIconDecorator : AppTopBarIconDecorator {
    @Composable
    override fun Render(modifier: Modifier, enabled: Boolean, content: @Composable (Modifier) -> Unit) {
        val ownsDrag = remember { mutableStateOf(false) }
        val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
        val visualSize = AppChromeSizeTokens.TopBarIconVisualSize
        BottomBarMatchedReusableLiquidDock(
            shape = CircleShape,
            modifier = modifier
                .size(visualSize)
                .then(if (enabled && glassEnabled) Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { ownsDrag.value = true },
                        onDragEnd = { ownsDrag.value = false },
                        onDragCancel = { ownsDrag.value = false },
                        onDrag = { change, _ -> change.consume() },
                    )
                } else Modifier),
            backdrop = com.android.purebilibili.core.ui.blur.rememberSurfaceOverlayBackdrop(
                LocalAppTopBarIconBackdrop.current, LocalAppTopBarSurfaceColor.current,
            ),
            reuseEnabled = true,
            elasticFeedbackEnabled = enabled,
            allowConsumedDrag = { ownsDrag.value },
            useNeutralLiquidContainer = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(visualSize.value),
            // 内容只含图标，不需要再录制玻璃外壳。
            captureShellForContent = false,
        ) { glassSurfaceProvided ->
            CompositionLocalProvider(LocalAppIconButtonGlassSurfaceProvided provides glassSurfaceProvided) {
                Box(Modifier.fillMaxSize()) { content(Modifier.matchParentSize()) }
            }
        }
    }
}
