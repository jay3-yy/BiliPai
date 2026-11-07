// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.transition

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Direction of the interactive swipe that dismisses (pops) the current top entry.
 *
 * The renderer maps the finger travel along the matching axis onto the shared `animatedTop` driver
 * 1:1, so the dismiss gesture follows the same axis as the transition: a horizontal slide is
 * dismissed by a horizontal swipe, a bottom-up modal by a downward swipe. The values are **physical
 * screen directions** (which way the finger moves) and are NOT mirrored for layout direction — so
 * the "back" swipe is [LeftToRight] under LTR but [RightToLeft] under RTL; pick accordingly.
 *
 * Swipe-to-dismiss is opt-in: a [NavTransition] may declare a direction via
 * [NavTransition.dismissDirection] (none of the [NavTransitions] presets do by default), and a route
 * may set or override it (including disabling with [None]) through the `entry(swipeDismiss = …)` DSL.
 */
enum class NavSwipeDirection {
    /** No interactive swipe-to-dismiss; the entry is popped only via the back button / system back. */
    None,

    /** Horizontal swipe with the finger moving rightward (physical; the back swipe under LTR). */
    LeftToRight,

    /** Horizontal swipe with the finger moving leftward (physical; the back swipe under RTL). */
    RightToLeft,

    /** Vertical swipe with the finger moving downward. Natural dismiss for a bottom-up modal. */
    TopToBottom,

    /** Vertical swipe with the finger moving upward. */
    BottomToTop,
}

/**
 * The visual contract for a single navigation entry layer.
 *
 * A [NavTransition] maps an entry's continuous [relativeDepth][NavTransitionScope.relativeDepth]
 * onto a [Modifier] transform (translation, scale, alpha, clip ...). The same transition governs
 * both normal push/pop animation and predictive-back gestures, because the visual is always a pure
 * function of [animatedTop][NavTransitionScope.relativeDepth] (spec section 7.1).
 *
 * Marked [Stable] rather than [androidx.compose.runtime.Immutable] because a transition holds a
 * lambda ([transformEntry]); lambda identity is reference-based, which breaks the equals-stays-equal
 * contract of `@Immutable`.
 *
 * @see navGraphicsTransition for the deferred-read convenience constructor that covers most cases.
 */
@Stable
fun interface NavTransition {
    /**
     * Applies this transition's visual transform to [this] entry layer using [scope].
     *
     * Implementations should read [scope] properties inside a deferred-read block
     * (`graphicsLayer { }` / layout) so the animation runs without recomposition.
     */
    fun Modifier.transformEntry(scope: NavTransitionScope): Modifier

    /**
     * The relative depth beyond which the layer below this entry is considered fully occluded and may
     * be culled. Defaults to `1f` (opaque slide: only the top plus one covered layer renders). A
     * modal transition keeps lower layers visible by returning a larger value (for example `2f`).
     */
    val opaqueDepth: Float get() = 1f

    /** A container transition supplies its own moving clip instead of the host corner clip. */
    val handlesOwnClipping: Boolean get() = false

    /** Compose and lay out the incoming entry at its source frame before starting a push. */
    val preparesEnterFrame: Boolean get() = false

    /** Keep a removing entry across an opted-in spring's first endpoint crossing until it settles. */
    val retainsSpringOvershoot: Boolean get() = false

    /**
     * The direction of the interactive swipe that dismisses this entry. Defaults to
     * [NavSwipeDirection.None]: the swipe-to-dismiss gesture is **opt-in**. Enable it either by
     * authoring a transition that declares a direction matching its own motion (e.g. an LTR
     * horizontal slide -> [NavSwipeDirection.LeftToRight] / RTL -> [NavSwipeDirection.RightToLeft], a
     * bottom-up modal -> [NavSwipeDirection.TopToBottom]), or per route via the
     * `entry(swipeDismiss = …)` DSL. None of the built-in [NavTransitions] presets enable it by
     * default. Directions are physical (not mirrored for layout direction); see [NavSwipeDirection].
     */
    val dismissDirection: NavSwipeDirection get() = NavSwipeDirection.None

    /**
     * Settle physics for the phases this transition governs (gesture commit / gesture cancel /
     * programmatic full step). The host resolves the motion from the topmost presented entry's
     * governing transition — during a pop that is the leaving entry, so a preset's own motion
     * carries its exit. Defaults to the established navigation feel ([NavMotion.Default]).
     */
    val motion: NavMotion get() = NavMotion.Default

    /**
     * Alpha fraction (0..1) of the fullscreen dim scrim the host renders just beneath the top-most
     * visible layer, evaluated each frame against [scope] — the context of the **covered layer
     * directly below** that top-most layer, whose [NavTransitionScope.relativeDepth] runs from 0
     * (fully revealed) to 1 (fully covered).
     *
     * The scrim is part of the covered treatment, so it is governed by the transition of the layer
     * **above** (the same boundary-ownership rule as `transformEntry` for `0 < d`). The split of
     * responsibilities with the effects layer: `NavDisplayEffects.dimAmount` owns whether the scrim
     * exists and how dark it can get, this method owns the curve along the motion — the final scrim
     * alpha is `dimAmount * scrimFraction(scope)`.
     *
     * The default follows the depth linearly (the scrim lightens as the layer below is revealed),
     * which suits slide-style transitions. A card-style transition can instead hold the scrim during
     * a gesture and fade it only across the post-commit sweep by reading [NavTransitionScope.gesture].
     *
     * The host evaluates this inside a deferred `graphicsLayer { }` block, so reads of [scope] are
     * per-frame and cost no recomposition.
     */
    fun scrimFraction(scope: NavTransitionScope): Float = scope.relativeDepth.coerceIn(0f, 1f)
}

/**
 * Builds a [NavTransition] from a deferred-read [graphicsLayer][androidx.compose.ui.graphics.graphicsLayer]
 * block, the recommended way to author custom transitions.
 *
 * The [block] receiver is [GraphicsLayerScope] (so callers set `translationX`, `scaleX`, `alpha`,
 * `cameraDistance` ... directly) and its single argument is the [NavTransitionScope] (read
 * `scope.relativeDepth`, `scope.layoutSize`, etc. inside the block). Because the block runs inside
 * `Modifier.graphicsLayer { }`, every depth read is deferred to the draw phase, so transitions cause
 * zero recomposition while they animate (spec section 6.2).
 *
 * @param opaqueDepth see [NavTransition.opaqueDepth]; defaults to `1f`.
 * @param dismissDirection see [NavTransition.dismissDirection]; defaults to [NavSwipeDirection.None]
 *   (swipe-to-dismiss is opt-in).
 * @param motion see [NavTransition.motion]; defaults to [NavMotion.Default].
 * @param scrim see [NavTransition.scrimFraction]; the lambda receives the covered layer's scope and
 *   returns the scrim alpha fraction (0..1). `null` (default) keeps the depth-linear default curve.
 * @param block the per-frame graphics-layer transform.
 */
fun navGraphicsTransition(
    opaqueDepth: Float = 1f,
    dismissDirection: NavSwipeDirection = NavSwipeDirection.None,
    motion: NavMotion = NavMotion.Default,
    scrim: ((NavTransitionScope) -> Float)? = null,
    block: GraphicsLayerScope.(NavTransitionScope) -> Unit,
): NavTransition = object : NavTransition {
    override val opaqueDepth: Float = opaqueDepth

    override val dismissDirection: NavSwipeDirection = dismissDirection

    override val motion: NavMotion = motion

    override fun scrimFraction(scope: NavTransitionScope): Float = scrim?.invoke(scope) ?: super.scrimFraction(scope)

    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier = this.graphicsLayer { block(scope) }
}
