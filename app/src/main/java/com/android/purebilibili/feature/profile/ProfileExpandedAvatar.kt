/** 头像大图的形变、渐进模糊和文字遮罩。 */
package com.android.purebilibili.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun ProfileExpandedAvatar(
    user: UserState,
    state: ProfileAvatarExpansionState,
    fractionProvider: () -> Float,
    photoBottomProvider: () -> Float,
    statusBarTopPadding: Dp,
    listState: LazyListState,
    flingBehavior: FlingBehavior,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    // Decode once at viewport resolution, rather than restarting Coil for every drag size.
    val decodeWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val request = remember(context, user.face, decodeWidth) {
        ImageRequest.Builder(context).data(user.face).size(decodeWidth.coerceAtLeast(1)).build()
    }
    val visible by remember(fractionProvider) { derivedStateOf { fractionProvider() > 0f } }
    val blurCache = remember { com.android.purebilibili.core.ui.transition.BlurRenderEffectCache() }
    val blurEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion() &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced() &&
        com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect(android.os.Build.VERSION.SDK_INT)
    fun circularSizeDp(progress: Float): Float {
        val start = state.entranceSourceSize?.value ?: PROFILE_EXPANDED_AVATAR_SIZE_DP
        return start + (138f - start) * min(1f, progress * 3f)
    }
    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val progress = fractionProvider()
                        val circularSize = circularSizeDp(progress)
                        alpha = if (progress > 0f) 1f else 0f
                        shadowElevation = 16.dp.toPx() * progress * (1f - progress)
                        shape = RoundedCornerShape((circularSize / 2f * (1f - progress)).dp)
                        clip = true
                    }
                    .drawWithContent {
                        drawContent()
                        val progress = fractionProvider()
                        // Top and bottom scrims protect toolbar and identity text contrast.
                        val shade = Color(0x42000000).copy(alpha = (66f / 255f) * progress)
                        val toolbar = (statusBarTopPadding + 56.dp).toPx()
                        val topSolidEnd = (toolbar * 0.5f).coerceAtMost(size.height)
                        val topFadeEnd = (toolbar + 16.dp.toPx()).coerceAtMost(size.height)
                        val bottomSolidStart = (size.height - 36.dp.toPx()).coerceAtLeast(0f)
                        val bottomFadeStart = (size.height - 96.dp.toPx()).coerceAtLeast(0f)
                        drawRect(shade, size = Size(size.width, topSolidEnd))
                        drawRect(
                            Brush.verticalGradient(listOf(shade, Color.Transparent), startY = topSolidEnd, endY = topFadeEnd),
                            topLeft = Offset(0f, topSolidEnd), size = Size(size.width, topFadeEnd - topSolidEnd),
                        )
                        drawRect(shade, topLeft = Offset(0f, bottomSolidStart), size = Size(size.width, size.height - bottomSolidStart))
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, shade), startY = bottomFadeStart, endY = bottomSolidStart),
                            topLeft = Offset(0f, bottomFadeStart),
                            size = Size(size.width, bottomSolidStart - bottomFadeStart),
                        )
                    }
                    // An alpha-zero pointer node would steal taps from the editable avatar.
                    // Preload the image without any input nodes until the morph is visible.
                    .then(
                        if (visible) Modifier
                            .appScrollBoundaryHaptics(listState)
                            .scrollable(listState, Orientation.Vertical, reverseDirection = true, flingBehavior = flingBehavior)
                            .clickable(onClick = onCollapse)
                        else Modifier.clearAndSetSemantics {},
                    ),
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = "${user.name}的头像大图，点按收起",
                    contentScale = ContentScale.Crop,
                    onLoading = { state.reportPhotoLoad(user.face, ProfileAvatarPhotoLoadStatus.Loading) },
                    onSuccess = { state.reportPhotoLoad(user.face, ProfileAvatarPhotoLoadStatus.Ready) },
                    onError = {
                        state.reportPhotoLoad(user.face, ProfileAvatarPhotoLoadStatus.Failed)
                        state.reset()
                    },
                    modifier = Modifier.matchParentSize(),
                )
                if (blurEnabled) {
                    // Sample the avatar itself and mask its blurred lower edge with a
                    // 56dp gradient, and composites it over the lower photo region.
                    // This layer never samples the statistics, settings or feed below.
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = fractionProvider()
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                val band = (56.dp.toPx() * fractionProvider()).coerceAtLeast(1f)
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.White),
                                        startY = (size.height - band).coerceAtLeast(0f),
                                        endY = size.height,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                            .graphicsLayer { renderEffect = blurCache.resolve((20f * fractionProvider()).roundToInt().toFloat()) }
                            .clearAndSetSemantics {},
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val progress = fractionProvider()
        val circularSize = circularSizeDp(progress).dp.toPx()
        val imageWidth = (circularSize + (constraints.maxWidth - circularSize) * progress).roundToInt().coerceAtLeast(1)
        val imageHeight = (circularSize + (photoBottomProvider() - circularSize) * progress).roundToInt().coerceAtLeast(1)
        val image = measurables.single().measure(Constraints.fixed(imageWidth, imageHeight))
        val imageY = ((statusBarTopPadding + PROFILE_EXPANDED_AVATAR_TOP_DP.dp).toPx() * (1f - progress)).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            image.placeRelative((constraints.maxWidth - imageWidth) / 2, imageY)
        }
    }
}

/** Draw-phase toolbar color crossfade to white over the expanded photo. */
@Composable
internal fun ProfilePhotoToolbarIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    normalColor: Color,
    photoFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AppIcon(
            imageVector, contentDescription,
            tint = normalColor,
            modifier = Modifier.graphicsLayer { alpha = 1f - photoFractionProvider() },
        )
        AppIcon(
            imageVector, null,
            tint = Color.White,
            modifier = Modifier
                .graphicsLayer { alpha = photoFractionProvider() }
                .clearAndSetSemantics {},
        )
    }
}
