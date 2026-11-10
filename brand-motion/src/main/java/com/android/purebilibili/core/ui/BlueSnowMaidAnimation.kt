package com.android.purebilibili.core.ui

import android.os.SystemClock
import android.graphics.BitmapFactory
import androidx.annotation.RawRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.android.bilipai.brandmotion.R
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

val LocalMaidLottieAnimationEnabled = staticCompositionLocalOf { false }

enum class MaidAnimation(
    @RawRes val resource: Int,
    val durationMs: Long,
    @DrawableRes val staticResource: Int,
    val loopsWhileVisible: Boolean = false
) {
    WELCOME(R.raw.bilipai_maid_welcome, 1000L, R.drawable.bilipai_maid_static),
    CLEAN_COMPLETE(R.raw.bilipai_maid_clean_complete, 1200L, R.drawable.bilipai_maid_clean_static),
    CLEANING(R.raw.bilipai_maid_cleaning, 1600L, R.drawable.bilipai_maid_cleaning_static, loopsWhileVisible = true),
    RETRY(R.raw.bilipai_maid_retry, 1500L, R.drawable.bilipai_maid_retry_static),
    EMPTY(R.raw.bilipai_maid_empty, 1800L, R.drawable.bilipai_maid_empty_static),
    SEARCH_EMPTY(R.raw.bilipai_maid_search_empty, 2000L, R.drawable.bilipai_maid_search_empty_static),
    FAVORITE_SAVED(R.raw.bilipai_maid_favorite_saved, 1200L, R.drawable.bilipai_maid_favorite_static),
    FOLLOW_SUCCESS(R.raw.bilipai_maid_follow_success, 1200L, R.drawable.bilipai_maid_follow_static),
    UNFOLLOW_COMPLETE(R.raw.bilipai_maid_unfollow_complete, 1000L, R.drawable.bilipai_maid_unfollow_static),
    DISLIKE_CONFIRMED(R.raw.bilipai_maid_dislike_confirmed, 1000L, R.drawable.bilipai_maid_dislike_static),
    SHARE_READY(R.raw.bilipai_maid_share_ready, 1200L, R.drawable.bilipai_maid_share_static),
    COIN_SUCCESS(R.raw.bilipai_maid_coin_success, 1200L, R.drawable.bilipai_maid_coin_static),
    DOWNLOAD_COMPLETE(R.raw.bilipai_maid_download_complete, 1200L, R.drawable.bilipai_maid_download_static),
    LIKE_SUCCESS(R.raw.bilipai_maid_download_complete, 1200L, R.drawable.bilipai_maid_download_static),
    TRIPLE_SUCCESS(R.raw.bilipai_maid_triple_success, 1800L, R.drawable.bilipai_maid_triple_static)
}

/** One visible play per identity; replay is explicit, and assets are cached by raw resource. */
@Composable
fun BlueSnowMaidAnimation(
    animation: MaidAnimation,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    isVisible: Boolean = true,
    replayKey: Int = 0,
    reducedMotion: Boolean = false,
    staticDisplayDurationMs: Long = 200L,
    completionHoldDurationMs: Long = 0L
) {
    key(animation, replayKey) {
        MaidAnimationPlayer(animation, modifier, onFinished, isVisible, reducedMotion, staticDisplayDurationMs, completionHoldDurationMs)
    }
}

@Composable
private fun MaidAnimationPlayer(
    animation: MaidAnimation,
    modifier: Modifier,
    onFinished: () -> Unit,
    isVisible: Boolean,
    reducedMotion: Boolean,
    staticDisplayDurationMs: Long,
    completionHoldDurationMs: Long
) {
    val lottieAnimationEnabled = LocalMaidLottieAnimationEnabled.current
    val resources = LocalContext.current.resources
    val result = if (lottieAnimationEnabled) {
        rememberLottieComposition(LottieCompositionSpec.RawRes(animation.resource))
    } else {
        null
    }
    val player = rememberLottieAnimatable()
    val reduceMotion = rememberSystemReduceMotion() || reducedMotion
    val latestOnFinished by rememberUpdatedState(onFinished)
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var inViewport by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var playbackComplete by remember { mutableStateOf(false) }
    var remainingHoldMs by remember { mutableStateOf(completionHoldDurationMs.coerceIn(0L, 1_500L)) }
    // Only active playback consumes the deadline. Time in the background does not.
    var remainingPlaybackMs by remember { mutableStateOf(animation.durationMs + 400L) }
    val active = foreground && isVisible && inViewport
    LaunchedEffect(active, lottieAnimationEnabled, reduceMotion, staticDisplayDurationMs, completionHoldDurationMs) {
        if (!active || finished) return@LaunchedEffect
        if (!playbackComplete) {
            if (!lottieAnimationEnabled) {
                if (animation.loopsWhileVisible) return@LaunchedEffect
                delay(staticDisplayDurationMs.coerceIn(200L, 1_000L))
            } else {
                val composition = try {
                    withTimeoutOrNull(500L) {
                        requireNotNull(result).await().also { composition ->
                            // The timeline and static fallback share one packaged lossless WebP.
                            // Prepare before publishing to the player so no frame has missing art.
                            withContext(Dispatchers.IO) {
                                val asset = requireNotNull(composition.images["maid_bitmap"])
                                require(asset.fileName == resources.getResourceEntryName(animation.staticResource) + ".webp")
                                if (asset.bitmap == null) {
                                    val bitmap = requireNotNull(BitmapFactory.decodeResource(
                                        resources,
                                        animation.staticResource,
                                        BitmapFactory.Options().apply { inScaled = false }
                                    ))
                                    require(bitmap.width == asset.width && bitmap.height == asset.height)
                                    asset.bitmap = bitmap
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (composition == null) {
                    failed = true
                    delay(staticDisplayDurationMs.coerceIn(200L, 1_000L))
                } else if (reduceMotion) {
                    failed = false
                    player.snapTo(composition = composition, progress = 1f)
                    delay(staticDisplayDurationMs.coerceIn(200L, 1_000L))
                } else {
                    failed = false
                    do {
                        val startedAt = SystemClock.elapsedRealtime()
                        try {
                            val completed = withTimeoutOrNull(remainingPlaybackMs.coerceAtLeast(1L)) {
                                player.animate(
                                    composition = composition,
                                    iteration = 1,
                                    iterations = 1,
                                    initialProgress = if (player.composition == composition) player.progress else 0f,
                                    // Reset the frame clock so resuming cannot include time spent hidden.
                                    continueFromPreviousAnimate = false
                                )
                                true
                            } ?: false
                            if (!completed && animation.loopsWhileVisible) failed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            remainingPlaybackMs = (remainingPlaybackMs -
                                (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)
                        }
                        if (failed) break
                        if (animation.loopsWhileVisible) {
                            player.snapTo(composition = composition, progress = 0f)
                            remainingPlaybackMs = animation.durationMs + 400L
                        } else {
                            player.snapTo(composition = composition, progress = 1f)
                        }
                    } while (animation.loopsWhileVisible)
                }
            }
            // A waiting animation never emits success or holds up the real operation.
            // Reduced motion and resource errors keep the matching working pose still.
            if (animation.loopsWhileVisible) return@LaunchedEffect
            playbackComplete = true
        }
        val holdStartedAt = SystemClock.elapsedRealtime()
        try {
            delay(remainingHoldMs)
        } finally {
            remainingHoldMs = (remainingHoldMs -
                (SystemClock.elapsedRealtime() - holdStartedAt)).coerceAtLeast(0L)
        }
        finished = true
        latestOnFinished()
    }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInRoot()
            val rootSize = coordinates.findRootCoordinates().size
            inViewport = bounds.width > 0f && bounds.height > 0f &&
                bounds.right > 0f && bounds.bottom > 0f &&
                bounds.left < rootSize.width && bounds.top < rootSize.height
        },
        contentAlignment = Alignment.Center
    ) {
        if (!lottieAnimationEnabled) {
            // Keep the reserved layout and completion timing while hiding both Lottie and poster.
        } else if (failed || player.composition == null) {
            Image(
                painter = painterResource(animation.staticResource),
                contentDescription = "蓝雪女仆",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LottieAnimation(
                composition = player.composition,
                progress = { player.progress },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "蓝雪女仆" }
            )
        }
    }
}
