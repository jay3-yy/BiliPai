package com.android.purebilibili.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.feature.home.LocalRootOwnsMovingWallpaper

/**
 * Settings retain an opaque visual backing. Static global wallpaper replays the root
 * recording rather than accumulating translucent page fills during push/pop.
 */
@Composable
internal fun SettingsOpaqueSurfaceHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sharedWallpaper = LocalGlobalWallpaperBackdropVisible.current &&
        LocalGlobalWallpaperBackdropSource.current != null
    val movingWallpaper = LocalGlobalWallpaperBackdropVisible.current && LocalRootOwnsMovingWallpaper.current
    val backing = if (movingWallpaper) Modifier
        else Modifier.globalWallpaperAwareBackground(AppSurfaceTokens.groupedListContainer())
    CompositionLocalProvider(LocalGlobalWallpaperBackdropVisible provides (sharedWallpaper || movingWallpaper)) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(backing),
        ) {
            content()
        }
    }
}
