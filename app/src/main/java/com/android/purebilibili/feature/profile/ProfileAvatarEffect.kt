/** 头像融合视图的 Compose 宿主与生命周期适配。 */
package com.android.purebilibili.feature.profile

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.feature.home.UserState
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics

/** Keep native fusion off the page backdrop; only the avatar/cutout enter its buffers. */
@Composable
internal fun ProfileAvatarEffect(
    user: UserState,
    expandedFractionProvider: () -> Float,
    statusBarTopPadding: Dp,
    borderColor: Color,
    listState: LazyListState,
    flingBehavior: FlingBehavior,
    onAvatarClick: () -> Unit,
    avatarSourceRect: androidx.compose.runtime.MutableState<androidx.compose.ui.geometry.Rect?>,
    avatarEntranceScale: () -> Float,
    modifier: Modifier = Modifier,
    photoScrollConnection: NestedScrollConnection? = null,
) {
    val density = LocalDensity.current
    val latestDensity = rememberUpdatedState(density)
    val parentComposition = rememberCompositionContext()
    val latestUser = rememberUpdatedState(user)
    val latestBorder = rememberUpdatedState(borderColor)
    val latestClick = rememberUpdatedState(onAvatarClick)
    val latestFling = rememberUpdatedState(flingBehavior)
    val lowBudget = isLowBlurBudgetForced()
    val latestLowBudget = rememberUpdatedState(lowBudget)
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion() && !lowBudget
    val latestMotion = rememberUpdatedState(motionEnabled)
    val latestProvider = rememberUpdatedState(expandedFractionProvider)
    val latestEntranceScale = rememberUpdatedState(avatarEntranceScale)
    val latestPhotoScrollConnection = rememberUpdatedState(photoScrollConnection)
    val latestTop = rememberUpdatedState(with(density) { statusBarTopPadding.toPx() })
    val host = remember { arrayOfNulls<ProfileAvatarFusionView>(1) }
    // Refresh the native display list even when Compose reuses its cached draw layer,
    // or the fully absorbed view has no visible child to invalidate it.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(latestProvider.value(), latestMotion.value, latestTop.value to latestLowBudget.value) }
            .collect { host[0]?.prepareFrame() }
    }
    AndroidView(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                host[0]?.setViewportOrigin(position.x, position.y)
            }
            .drawWithContent {
                // Observe scroll in Compose's draw phase, including reverse gestures after disappearance.
                latestProvider.value()
                latestEntranceScale.value()
                host[0]?.prepareFrame()
                this@drawWithContent.drawContent()
            },
        factory = { context ->
            ProfileAvatarFusionView(context, !lowBudget).apply {
                host[0] = this
                val avatar = ComposeView(context).apply {
                    val avatarView = this
                    setParentCompositionContext(parentComposition)
                    pivotX = 0f
                    pivotY = 0f
                    setContent {
                        // Preserve scaled layout density and software-canvas-safe image requests together.
                        CompositionLocalProvider(LocalDensity provides latestDensity.value) {
                            val currentUser = latestUser.value
                            val context = LocalContext.current
                            val avatarRequest = remember(context, currentUser.face) {
                                ImageRequest.Builder(context)
                                    .data(currentUser.face)
                                    .memoryCacheKey(com.android.purebilibili.feature.dynamic.components.resolveImagePreviewPlaceholderCacheKey(currentUser.face) ?: currentUser.face)
                                    .buildForProfileFusion()
                            }
                            AsyncImage(
                                model = avatarRequest,
                                contentDescription = "${currentUser.name}的头像，查看大图",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                                    .alpha(if (com.android.purebilibili.feature.dynamic.components.isImagePreviewSourceHidden(avatarSourceRect.value)) 0f else 1f)
                                    .clip(CircleShape)
                                    .border(2.dp, latestBorder.value, CircleShape)
                                    .then(latestPhotoScrollConnection.value?.let { Modifier.nestedScroll(it) } ?: Modifier)
                                    .appScrollBoundaryHaptics(listState)
                                    .scrollable(listState, Orientation.Vertical, reverseDirection = true, flingBehavior = latestFling.value)
                                    .clickable {
                                        val location = IntArray(2)
                                        avatarView.getLocationInWindow(location)
                                        val width = avatarView.width * avatarView.scaleX
                                        val height = avatarView.height * avatarView.scaleY
                                        avatarSourceRect.value = androidx.compose.ui.geometry.Rect(
                                            location[0].toFloat(), location[1].toFloat(),
                                            location[0] + width, location[1] + height,
                                        )
                                        latestClick.value()
                                    },
                            )
                        }
                    }
                }
                val baseSize = (100f * density.density).roundToInt().coerceAtLeast(1)
                addView(avatar, FrameLayout.LayoutParams(baseSize, baseSize))
                setFrameUpdater {
                    setHardwareEffectsEnabled(!latestLowBudget.value)
                    val expanded = latestProvider.value().coerceIn(0f, 1f)
                    // Layout coordinates use the app's Compose density, not device resource density.
                    // Cutout bounds are already physical pixels in this host's viewport.
                    val layoutDensity = latestDensity.value.density
                    val cutout = notchInfo
                    val restingSizePx = resolveProfileAvatarSizePx(
                        density = layoutDensity,
                        expandedFraction = expanded,
                        circularCutoutWidthPx = if (cutout != null && cutout.isLikelyCircle) cutout.bounds.width() else 0f,
                    )
                    // 入场缩放围绕头像中心，收起时居中落入挖孔圆心避免底部残留。
                    val sizePx = restingSizePx * (1f + (latestEntranceScale.value() - 1f) * expanded)
                    val scale = sizePx / avatar.layoutParams.width
                    val endY = if (cutout != null && cutout.isLikelyCircle) {
                        cutout.bounds.bottom - cutout.bounds.width() * 0.75f
                    } else -29f * layoutDensity
                    val startY = latestTop.value + PROFILE_EXPANDED_AVATAR_TOP_DP * layoutDensity
                    avatar.visibility = if (expanded <= 0f) View.INVISIBLE else View.VISIBLE
                    avatar.scaleX = scale
                    avatar.scaleY = scale
                    avatar.translationX = (width - sizePx) / 2f
                    avatar.translationY = endY + (startY - endY) * expanded + (restingSizePx - sizePx) / 2f
                    setStatusBarHeight(latestTop.value.toInt())
                    updateFrame(1f - expanded, latestMotion.value)
                }
            }
        },
        update = { view ->
            val avatar = view.getChildAt(0)
            val size = (100f * density.density).roundToInt().coerceAtLeast(1)
            val params = avatar.layoutParams
            if (params.width != size || params.height != size) {
                params.width = size
                params.height = size
                avatar.layoutParams = params
            }
            view.prepareFrame()
        },
        onRelease = { view ->
            (view.getChildAt(0) as? ComposeView)?.disposeComposition()
            view.release()
            if (host[0] === view) host[0] = null
        },
    )
}
