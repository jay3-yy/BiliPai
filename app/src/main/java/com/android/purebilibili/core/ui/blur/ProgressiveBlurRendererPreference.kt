package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.home.LiquidGlassSettingsStore
import com.android.purebilibili.core.store.home.ProgressiveBlurRenderer
import com.android.purebilibili.feature.home.components.isOfficialProgressiveBlurSupported

/** 每个窗口共享渲染方案；页面与顶栏只消费值，不分别订阅设置存储。 */
internal val LocalProgressiveBlurRenderer = compositionLocalOf { ProgressiveBlurRenderer.MIUIX }

/** 不支持 Android 方案的窗口无需订阅；设置页仍按原方式读取保存值。 */
@Composable
internal fun rememberWindowProgressiveBlurRenderer(): ProgressiveBlurRenderer {
    if (!isOfficialProgressiveBlurSupported()) return ProgressiveBlurRenderer.MIUIX
    val context = LocalContext.current
    val renderer by remember(context) {
        LiquidGlassSettingsStore.observeProgressiveBlurRenderer(context)
    }.collectAsStateWithLifecycle(initialValue = ProgressiveBlurRenderer.MIUIX)
    return renderer
}
