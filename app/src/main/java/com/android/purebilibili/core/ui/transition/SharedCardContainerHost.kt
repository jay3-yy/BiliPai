package com.android.purebilibili.core.ui.transition

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

internal const val SHARED_CARD_CONTAINER_DURATION_MS = 300
private const val SHARED_CARD_CONTENT_FADE_MS = 140

/** Only container entries defer/reveal content; ordinary page entries retain their behavior. */
@Composable
internal fun rememberSharedCardDetailRevealAlpha(
    entryKey: Any,
    contentTransitionReady: Boolean,
    contentLoaded: Boolean,
): State<Float> {
    val deferredEntry = remember(entryKey) { !contentTransitionReady }
    if (!deferredEntry) return rememberUpdatedState(1f)
    val reduceMotion = rememberSystemReduceMotion()
    return animateFloatAsState(
        targetValue = if (contentTransitionReady && contentLoaded) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(
            SHARED_CARD_CONTENT_FADE_MS, easing = AppMotionEasing.M1Standard,
        ),
        label = "sharedCardDetailReveal",
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun sharedCardContainerBounds(
    modifier: Modifier,
    key: String,
    sharedScope: SharedTransitionScope? = LocalSharedTransitionScope.current,
    visibilityScope: AnimatedVisibilityScope? = LocalAnimatedVisibilityScope.current,
): Modifier {
    if (sharedScope == null || visibilityScope == null) return modifier
    return with(sharedScope) {
        modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibilityScope,
            // Clear outgoing glyphs early, then reveal the incoming surface. Do not keep
            // both full pages translucent throughout the entire bounds animation.
            enter = fadeIn(tween(170, delayMillis = 70, easing = AppMotionEasing.M1Standard)),
            exit = fadeOut(tween(100, easing = AppMotionEasing.M1Standard)),
            boundsTransform = { _, _ -> tween(SHARED_CARD_CONTAINER_DURATION_MS, easing = AppMotionEasing.M1Standard) },
            // Cards and detail pages have very different aspect ratios. Stretching the whole
            // subtree to the animated bounds distorts avatars, posters and glyphs on both sides.
            // Fill the moving container without stretching glyphs or shrinking a complete
            // detail page into a letterboxed thumbnail. Reveal it from the header down.
            resizeMode = scaleToBounds(ContentScale.Crop, Alignment.TopStart),
            clipInOverlayDuringTransition = OverlayClip(AppShapes.container(ContainerLevel.Card)),
        )
    }
}

/** The one container host used by Bangumi and non-video search results. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun <T : Any> SharedCardContainerHost(
    saver: Saver<T?, out Any>,
    keyOf: (T) -> String,
    content: @Composable ((T) -> Unit, Boolean) -> Unit,
    destination: @Composable (T, () -> Unit, (T) -> Unit, Boolean, Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val savedHub = rememberSaveableStateHolder()
    val reduceMotion = rememberSystemReduceMotion()
    val duration = if (reduceMotion) 0 else SHARED_CARD_CONTAINER_DURATION_MS
    var opened by rememberSaveable(stateSaver = saver) {
        mutableStateOf<T?>(null)
    }
    val transitionState = remember { SeekableTransitionState(opened) }
    var detailContentReady by remember { mutableStateOf(opened != null) }
    LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (transitionState.currentState != null &&
            transitionState.currentState == transitionState.targetState
        ) {
            detailContentReady = true
        }
    }
    val detailVisible = opened != null || transitionState.currentState != null || transitionState.targetState != null
    val closeDetail: () -> Unit = {
        scope.launch {
            transitionState.animateTo(null, tween(duration, easing = LinearEasing))
            opened = null
        }
    }
    val openDetail: (T) -> Unit = { target ->
        if (!detailVisible) {
            detailContentReady = false
            opened = target
            scope.launch {
                transitionState.animateTo(target, tween(duration, easing = LinearEasing))
                detailContentReady = true
            }
        }
    }
    val replaceDetail: (T) -> Unit = { target ->
        opened = target
        scope.launch { transitionState.snapTo(target) }
    }
    val predictiveBackEnabled = com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled.current
    PredictiveBackHandler(enabled = detailVisible) { progress ->
        var fraction = 0f
        try {
            progress.collect { event ->
                if (predictiveBackEnabled) {
                    fraction = event.progress.coerceIn(0f, 1f)
                    transitionState.seekTo(if (reduceMotion) 0f else fraction, null)
                }
            }
            // Continue the existing seek direction and let Compose use its remaining time.
            transitionState.animateTo(null)
            opened = null
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                opened?.let { detail ->
                    // Changing target to detail here creates a NEW transition while Compose
                    // also settles the interrupted initial values. That produces a second
                    // whole-page movement after release. Rewind the same shared bounds first.
                    val restoreFraction = transitionState.fraction
                    val restoreMillis = (duration * restoreFraction).toInt()
                    if (restoreMillis > 0 && !reduceMotion) {
                        channelFlow {
                            animate(restoreFraction, 0f,
                                animationSpec = tween(restoreMillis, easing = LinearEasing),
                            ) { value, _ -> trySend(value) }
                        }.conflate().collect { value -> transitionState.seekTo(value, null) }
                    }
                    // Both endpoints now match the original detail pixels; reset state
                    // without starting another bounds/fade animation.
                    transitionState.snapTo(detail)
                }
            }
            throw cancelled
        }
    }
    SharedTransitionLayout(
        // The backing surface belongs to an open detail/transition. Keep the source's
        // original wallpaper and material presentation when the container is closed.
        modifier = Modifier
            .fillMaxSize()
            .background(if (detailVisible) MaterialTheme.colorScheme.surface else Color.Transparent),
    ) {
        val sharedScope = this
        val transition = rememberTransition(transitionState, label = "shared-card-container")
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            contentKey = { if (it == null) "source" else "destination" },
            // Shared bounds own geometry and overlay content. Fade the surrounding source
            // page separately so its list/text cannot show through the opening detail.
            // Keep the seek timeline linear; boundsTransform supplies spatial easing once.
            transitionSpec = {
                val transform = when {
                    reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                    targetState == null -> fadeIn(tween(120, delayMillis = 100)) togetherWith ExitTransition.None
                    else -> EnterTransition.None togetherWith fadeOut(tween(100))
                }
                transform.using(null)
            },
        ) { target ->
            CompositionLocalProvider(
                LocalSharedTransitionScope provides if (reduceMotion) null else sharedScope,
                LocalAnimatedVisibilityScope provides this,
            ) {
                if (target == null) {
                    savedHub.SaveableStateProvider("source") { content(openDetail, !detailVisible) }
                } else {
                    destination(target, closeDetail, replaceDetail, detailContentReady,
                        sharedCardContainerBounds(Modifier.fillMaxSize(), keyOf(target))
                            .background(MaterialTheme.colorScheme.surface))
                }
            }
        }
    }
}
