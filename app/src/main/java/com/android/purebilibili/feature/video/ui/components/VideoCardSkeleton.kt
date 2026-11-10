package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import com.android.purebilibili.feature.home.HomeFeedSkeletonCard

/** BGM recommendations use ElegantVideoCard, so reuse its home-feed loading silhouette. */
@Composable
fun VideoCardSkeleton(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") index: Int = 0,
    coverAspectRatio: Float = com.android.purebilibili.core.ui.videoCardCoverAspectRatio(),
    compactMetadata: Boolean = false,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val pulse = com.android.purebilibili.core.ui.skeleton.rememberSkeletonSweepState(
        animated = animationGate.active,
    )
    HomeFeedSkeletonCard(
        pulse = { pulse.value },
        wallpaperTintEnabled = false,
        wallpaperEffectMode = HomeWallpaperEffectMode.OFF,
        isDataSaverActive = true,
        coverAspectRatio = coverAspectRatio,
        modifier = modifier.then(animationGate.modifier),
        compactMetadata = compactMetadata,
    )
}
