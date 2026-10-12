package com.android.purebilibili.feature.video.ui.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache

/** Blur the covered comment content, never the foreground thread or its controls. */
internal fun Modifier.commentThreadNavigationBlur(
    progress: () -> Float,
): Modifier {
    val effectCache = BlurRenderEffectCache()
    return graphicsLayer {
        renderEffect = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val frame = resolvePredictiveBackBlurFrame(progress = progress())
            if (frame.blurRadiusPx > 0.5f) {
                renderEffect = effectCache.resolve(frame.blurRadiusPx)
            }
        }
    }
}
