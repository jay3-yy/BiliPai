package com.android.purebilibili.core.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.resolveTopChromeRenderMode
import com.android.purebilibili.core.ui.TopChromeRenderMode
import dev.chrisbanes.haze.HazeState

/** List pages keep their viewport full height and apply scaffold insets as scroll content padding. */
@Composable
internal fun ImmersiveAppScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    topBarSurfaceColor: Color = containerColor,
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
    /** Let a screen opt into the progressive renderer when both top effects are temporarily on. */
    preferProgressiveTopBlur: Boolean = false,
    /**
     * Optional source owned by a wallpaper host. The host must attach its modifier to the
     * wallpaper layer so the top chrome samples the same visual background as the screen.
     */
    chromeBackdropSource: ChromeBackdropSource? = null,
    /** Optional Haze state whose source is owned by the surrounding wallpaper host. */
    externalHazeState: HazeState? = null,
    // Keep false until any outgoing skeleton transition has left composition.
    blurContentReady: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val config = LocalAppThemeConfig.current
    val lowBlurBudget = isLowBlurBudgetForced()
    val headerRequested = config.headerBlurEnabled && !preferProgressiveTopBlur && topBar != null
    val progressiveRequested = config.progressiveTopBlurEnabled && !headerRequested && topBar != null
    val hazeState = externalHazeState ?: if (
        headerRequested &&
            !lowBlurBudget &&
            shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT)
    ) {
        rememberRecoverableHazeState(initialBlurEnabled = true)
    } else {
        null
    }
    val hazeReady = hazeState != null && blurContentReady && recoverableBlurEnabled(hazeState)
    val progressive = shouldUseBiliPaiProgressiveTopBlur(
        enabled = progressiveRequested,
        hasBackdrop = true,
    ) && !lowBlurBudget
    // Keep recording while skeleton/loading content is shown. When the real content becomes
    // eligible, the already-warm backdrop can be published in the same composition instead of
    // making chrome briefly fall back while a new source records its first frame.
    val source = chromeBackdropSource ?: if (progressive) {
        rememberChromeBackdropSource()
    } else {
        null
    }
    val backdrop = source?.takeIf { blurContentReady && it.isReady }?.backdrop
    val renderMode = resolveTopChromeRenderMode(
        headerBlurRequested = headerRequested,
        progressiveBlurRequested = progressiveRequested,
        hazeAvailable = hazeReady,
        progressiveAvailable = backdrop != null,
    )
    val hazeActive = renderMode == TopChromeRenderMode.HAZE
    val progressiveActive = renderMode == TopChromeRenderMode.PROGRESSIVE
    val fadeActive = config.progressiveTopFadeEnabled && !hazeActive
    val blurActive = hazeActive || progressiveActive || fadeActive
    AppScaffold(
        modifier = modifier,
        topBarBlurHandledExternally = true,
        // 外部源可能仅含壁纸；只复用本框架录制的完整页面内容。
        topBarIconBackdropSource = source.takeIf { chromeBackdropSource == null },
        topBar = {
            if (topBar != null) {
                BiliPaiImmersiveTopBar(
                    backdrop = backdrop.takeIf { progressiveActive },
                    enabled = progressiveActive,
                    headerBlurActive = hazeActive,
                    surfaceColor = globalWallpaperAwareChromeColor(topBarSurfaceColor),
                    fadeEnabled = fadeActive,
                    extendBelowBounds = false,
                    modifier = Modifier.then(
                        if (progressiveActive || fadeActive) {
                            Modifier.background(Color.Transparent)
                        } else if (hazeActive && hazeState != null) {
                            Modifier
                                .unifiedBlur(
                                    hazeState = hazeState,
                                    surfaceType = BlurSurfaceType.HEADER,
                                    // 仅本框架的完整页面源允许采样当前窗口，外部壁纸源保持原语义。
                                    preferWindowBackdrop = externalHazeState == null && chromeBackdropSource == null,
                                )
                                .background(
                                    globalWallpaperAwareChromeColor(topBarSurfaceColor)
                                        .copy(alpha = AppSurfaceTokens.FrostedScrimAlpha)
                                )
                        } else {
                            Modifier.background(globalWallpaperAwareChromeColor(topBarSurfaceColor))
                        }
                    ),
                    content = topBar,
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (chromeBackdropSource == null) source?.modifier ?: Modifier else Modifier
                )
                .then(
                    if (externalHazeState == null && hazeState != null &&
                        com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current
                    ) {
                        Modifier.hazeSourceCompat(hazeState)
                    } else {
                        Modifier
                    }
                )
                .globalWallpaperAwareBackground(containerColor),
        ) {
            content(padding)
        }
    }
}
