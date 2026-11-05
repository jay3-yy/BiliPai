package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import com.android.purebilibili.navigation3.resolveRelatedReturnSourceRestoreDecision
import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.android.purebilibili.core.ui.transition.resolveVideoHeroMotionSpec
import com.android.purebilibili.core.ui.transition.resolveVideoHeroLandingScale
import com.android.purebilibili.core.ui.transition.VideoCardTransitionSettleState
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class MiuixVideoCardNavTransitionTest {

    @Test
    fun nativeViewportContractsWithBodyFadeWithoutASecondHeightStage() {
        for ((sourceX, sourceY) in listOf(.46f to .25f, .92f to .12f, .3f to .08f)) {
            var previousY = sourceY
            for (i in 0..1000) {
                val depth = i / 1000f
                val x = resolveMiuixVideoCardOuterScale(sourceX, depth, 1f)
                val y = resolveMiuixVideoCardOuterScaleY(sourceX, sourceY, depth, 1f)
                val body = com.android.purebilibili.core.ui.transition.resolveVideoCardSecondaryContentVisualFrame(
                    morphDepthProgress = depth,
                    phase = com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase.RETURNING,
                    isReturnGestureInProgress = true,
                    motionTier = com.android.purebilibili.core.ui.adaptive.MotionTier.Normal,
                    preserveNativeViewport = true,
                )
                val cardY = x * sourceY / sourceX
                assertTrue(y >= cardY - .0001f)
                assertTrue(y <= x + .0001f)
                // Full content keeps the viewport; as it fades, contraction must already
                // be underway rather than waiting for an empty page to shrink again.
                if (body.alpha >= .9999f) assertEquals(x, y, .0001f)
                if (body.alpha in .1f..0.9f) {
                    assertTrue(y < x)
                    assertTrue(y > cardY)
                }
                assertTrue(y >= previousY - .0001f)
                assertTrue(y <= 1.0001f)
                previousY = y
            }
            assertEquals(sourceY, resolveMiuixVideoCardOuterScaleY(sourceX, sourceY, 0f, 1f), .0001f)
            assertEquals(1f, resolveMiuixVideoCardOuterScaleY(sourceX, sourceY, 1f, 1f), .0001f)
        }
    }

    @Test
    fun relatedVideoBackingDoesNotExposeWhiteThemeSurfaceAtTheWindowEdges() {
        assertEquals(Color.Black, resolveMiuixVideoCardBackingColor("video/BV123", Color.White))
        assertEquals(Color.Black, resolveMiuixVideoCardBackingColor("video/BV123?cid=10", Color.White))
        assertEquals(Color.White, resolveMiuixVideoCardBackingColor("home", Color.White))
        assertEquals(Color.White, resolveMiuixVideoCardBackingColor(null, Color.White))
    }


    @Test
    fun coveredPageDepthIsClampedReversibleAndDisabledForReducedMotion() {
        assertEquals(1f, resolveMiuixVideoCardCoveredScale(-1f, false))
        assertEquals(1f, resolveMiuixVideoCardCoveredScale(0f, false))
        assertEquals(0.985f, resolveMiuixVideoCardCoveredScale(0.5f, false), 0.0001f)
        assertEquals(0.97f, resolveMiuixVideoCardCoveredScale(1f, false), 0.0001f)
        assertEquals(0.97f, resolveMiuixVideoCardCoveredScale(2f, false), 0.0001f)
        // An interrupted return reuses its current depth; cancel restores the same path.
        for (depth in listOf(1f, 0.6f, 0.3f, 0.6f, 1f)) {
            assertEquals(1f - 0.03f * depth, resolveMiuixVideoCardCoveredScale(depth, false), 0.0001f)
            assertEquals(1f, resolveMiuixVideoCardCoveredScale(depth, true))
        }
    }

    @Test
    fun coveredBackgroundCornersGrowWithScaleAndReturnWithoutAFirstFrameJump() {
        for (density in listOf(1f, 1.5f, 3f)) {
            assertEquals(0f, resolveMiuixVideoCardCoveredCornerPx(0f, false, density))
            assertTrue(resolveMiuixVideoCardCoveredCornerPx(.001f, false, density) < .1f)
            for (depth in listOf(0f, .25f, .6f, 1f, .6f, .25f, 0f)) {
                val scale = resolveMiuixVideoCardCoveredScale(depth, false)
                val radius = resolveMiuixVideoCardCoveredCornerPx(depth, false, density)
                // 验证屏幕坐标圆角；冻结 Host 和真实来源页共用同一几何函数。
                assertEquals(24f * density * depth, radius * scale, .0001f)
                assertEquals(0f, resolveMiuixVideoCardCoveredCornerPx(depth, true, density))
            }
        }
        val cache = MiuixVideoCardCoveredShapeCache()
        val first = cache.resolve(4f)
        assertTrue(first === cache.resolve(4f))
        val second = cache.resolve(8f)
        assertTrue(first !== second)
        assertTrue(first === cache.resolve(12f))
    }
    @Test
    fun ordinaryReturnTracksDepthAndRoleChangesWithinOneFrame() {
        val scope = object : NavTransitionScope {
            override var relativeDepth by mutableFloatStateOf(-.4f)
            override var role by mutableStateOf(NavRole.Outgoing)
            override val change = NavChange.Pop
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override val gesture: NavGesture? = null
            override val settle: NavSettle? = null
        }
        val progress = MiuixVideoCardTransitionProgress()
        progress.bind(scope)
        assertEquals(.6f, progress.depthOrNull()!!, absoluteTolerance = .001f)
        assertEquals(.6f, progress.depthOrNull()!!, absoluteTolerance = .001f)
        scope.relativeDepth = -.8f
        assertEquals(.2f, progress.depthOrNull()!!, absoluteTolerance = .001f)
        scope.relativeDepth = 0f
        assertEquals(1f, progress.depthOrNull())
        scope.role = NavRole.Top
        assertEquals(1f, progress.depthOrNull())
        progress.clear()
        assertEquals(null, progress.depthOrNull())
        scope.role = NavRole.Outgoing
        scope.relativeDepth = -.3f
        progress.bind(scope)
        assertEquals(.7f, progress.depthOrNull()!!, absoluteTolerance = .001f)
    }

    @Test
    fun childPredictiveBackKeepsCoveredVideoFullscreenThroughCommitAndCancel() {
        val scope = object : NavTransitionScope {
            override var relativeDepth by mutableFloatStateOf(0f)
            override val role get() = if (relativeDepth > 0f) NavRole.Covered else NavRole.Top
            override val change = NavChange.Pop
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override var gesture: NavGesture? by mutableStateOf(null)
            override var settle: NavSettle? by mutableStateOf(null)
        }
        val progress = MiuixVideoCardTransitionProgress()
        // The retained video scope is already bound before opening BGM.
        progress.bind(scope)
        for (releasePhase in listOf(NavSettlePhase.Commit, NavSettlePhase.Cancel)) {
            scope.settle = null
            for (fraction in listOf(0f, .2f, .7f, .999f)) {
                scope.relativeDepth = 1f - fraction
                scope.gesture = NavGesture(fraction, NavSwipeEdge.Left, 500f)
                assertEquals(1f, progress.depthOrNull())
                assertEquals(VideoCardTransitionSettleState.Held, progress.settleStateOrNull())
                assertEquals(false, progress.isGestureInProgress())
                assertEquals(null, progress.gestureBackProgress())
            }
            scope.settle = object : NavSettle {
                override val phase = releasePhase
                override val releaseVelocity = 0f
                override val elapsedMillis = 0f
            }
            // Commit reveals the parent; cancel keeps it covered. Neither is a card return.
            scope.relativeDepth = if (releasePhase == NavSettlePhase.Commit) 0f else 1f
            assertEquals(1f, progress.depthOrNull())
            assertEquals(VideoCardTransitionSettleState.Held, progress.settleStateOrNull())
            assertEquals(null, progress.gestureBackProgress())
        }
    }

    @Test
    fun settledEntryRebindsRemovingScopeBeforeReturnStarts() {
        var depth by mutableFloatStateOf(-.5f)
        fun scope(removing: Boolean, lowerPage: Boolean = false) = object : NavTransitionScope {
            override val relativeDepth get() = if (lowerPage) depth + 1f else depth
            override val role get() = when {
                relativeDepth > 0f -> NavRole.Covered
                relativeDepth == 0f -> NavRole.Top
                removing -> NavRole.Outgoing
                else -> NavRole.Incoming
            }
            override val change = if (removing) NavChange.Pop else NavChange.Push
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override val gesture: NavGesture? = null
            override val settle: NavSettle? = null
        }
        val progress = MiuixVideoCardTransitionProgress()
        progress.bind(scope(removing = false))
        assertEquals(VideoCardTransitionSettleState.AutoEnter, progress.settleStateOrNull())
        depth = 0f
        assertEquals(VideoCardTransitionSettleState.Held, progress.settleStateOrNull())

        // Miuix captures isRemoving in a fresh scope before its driver leaves depth zero.
        progress.bind(scope(removing = true))
        depth = -.4f
        progress.bind(scope(removing = false, lowerPage = true))
        assertEquals(VideoCardTransitionSettleState.AutoReturn, progress.settleStateOrNull())
        assertEquals(.6f, progress.depthOrNull())

        // The source reaches Top in the final frame; it must not steal exit completion.
        depth = -1f
        progress.bind(scope(removing = false, lowerPage = true))
        assertEquals(VideoCardTransitionSettleState.Idle, progress.settleStateOrNull())
        assertEquals(0f, progress.depthOrNull())
    }

    @Test
    fun heroNavMotionReservesTimeForCommittedCardLanding() {
        val spec = resolveVideoHeroMotionSpec(360)
        val entering = resolveVideoHeroNavMotion(spec, false)
        val returning = resolveVideoHeroNavMotion(spec, true)
        assertEquals(360, (entering.programmatic as NavSettleSpec.Tween).durationMillis)
        assertEquals(299, (returning.programmatic as NavSettleSpec.Tween).durationMillis)
        assertEquals(spec.returnSpatialSpec, (returning.programmatic as NavSettleSpec.Tween).easing)
        assertEquals(spec.enterSpatialSpec, (entering.programmatic as NavSettleSpec.Tween).easing)
        assertEquals(spec.returnDurationMillis, (returning.commit as NavSettleSpec.Tween).durationMillis)
        assertTrue((returning.commit as NavSettleSpec.Tween).matchReleaseVelocity)
        assertEquals(spec.cancelStiffness, (returning.cancel as NavSettleSpec.Spring).stiffness)
        val reduced = resolveVideoHeroNavMotion(resolveVideoHeroMotionSpec(360, reducedMotion = true), true)
        assertTrue(!(reduced.commit as NavSettleSpec.Tween).matchReleaseVelocity)
        assertTrue(!(returning.programmatic as NavSettleSpec.Tween).matchReleaseVelocity)
    }

    @Test
    fun fullGestureLandsManuallyAndCommitHasNoSecondFlight() {
        val scope = object : NavTransitionScope {
            override var relativeDepth by mutableFloatStateOf(0f)
            override var role by mutableStateOf(NavRole.Top)
            override val change = NavChange.Pop
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override var gesture: NavGesture? by mutableStateOf(NavGesture(0f, NavSwipeEdge.Left, 500f))
            override var settle: NavSettle? by mutableStateOf(null)
        }
        val progress = MiuixVideoCardTransitionProgress()
        progress.bind(scope)
        assertEquals(1f, progress.depthOrNull())
        scope.relativeDepth = -.999f
        scope.gesture = NavGesture(.999f, NavSwipeEdge.Left, 500f)
        // Manual drag reaches (almost) full landing; the last sliver waits for the driver.
        assertEquals(.001f, progress.depthOrNull()!!, absoluteTolerance = .002f)
        scope.settle = object : NavSettle {
            override val phase = NavSettlePhase.Commit
            override val releaseVelocity = 0f
            override val elapsedMillis = 0f
        }
        scope.role = NavRole.Outgoing
        // Commit at a fully-dragged release has no remaining distance: no second flight.
        assertEquals(.001f, progress.depthOrNull()!!, absoluteTolerance = .002f)
        scope.relativeDepth = -.9995f
        assertEquals(.0005f, progress.depthOrNull()!!, absoluteTolerance = .002f)
        scope.relativeDepth = -1f
        assertEquals(0f, progress.depthOrNull())
    }

    @Test
    fun interruptingEntryRetainsItsCurrentPositionAndMapsGestureOneToOne() {
        val scope = object : NavTransitionScope {
            override var relativeDepth by mutableFloatStateOf(-.5f)
            override val role = NavRole.Incoming
            override val change = NavChange.Push
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override var gesture: NavGesture? by mutableStateOf(NavGesture(0f, NavSwipeEdge.Left, 500f))
            override val settle: NavSettle? = null
        }
        val progress = MiuixVideoCardTransitionProgress()
        progress.bind(scope)
        assertEquals(.5f, progress.depthOrNull())
        scope.relativeDepth = -.9f
        scope.gesture = NavGesture(.4f, NavSwipeEdge.Left, 500f)
        // Interruption keeps the pre-gesture position and gesture progress maps 1:1.
        assertEquals(.1f, progress.depthOrNull()!!, absoluteTolerance = .001f)
        // The first gesture frame must stay at the current position.
        val earlyScope = object : NavTransitionScope by scope {
            override val relativeDepth = -.9f
            override val gesture = NavGesture(0f, NavSwipeEdge.Left, 500f)
        }
        val earlyProgress = MiuixVideoCardTransitionProgress()
        earlyProgress.bind(earlyScope)
        assertEquals(.1f, earlyProgress.depthOrNull()!!, absoluteTolerance = .001f)
    }

    @Test
    fun cancelledGestureReturnsToFullscreenWithoutJumpingAtRelease() {
        val scope = object : NavTransitionScope {
            override var relativeDepth by mutableFloatStateOf(-.6f)
            override val role = NavRole.Incoming
            override val change = NavChange.Pop
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override var gesture: NavGesture? by mutableStateOf(NavGesture(.6f, NavSwipeEdge.Left, 500f))
            override var settle: NavSettle? by mutableStateOf(null)
        }
        val progress = MiuixVideoCardTransitionProgress()
        progress.bind(scope)
        val released = progress.depthOrNull()!!
        scope.settle = object : NavSettle {
            override val phase = NavSettlePhase.Cancel
            override val releaseVelocity = 0f
            override val elapsedMillis = 0f
        }
        assertEquals(released, progress.depthOrNull())
        scope.relativeDepth = -.3f
        assertTrue(progress.depthOrNull()!! > released)
        scope.relativeDepth = 0f
        assertEquals(1f, progress.depthOrNull())
    }

    @Test
    fun sourcePixelInverseScaleIsIndependentOfMorphDepth() {
        // Native chrome can measure once: FillWidthTop cancels the outer morph on BOTH
        // axes, including highly non-uniform horizontal cards and interrupted returns.
        for ((sourceX, sourceY) in listOf(.01f to .01f, .46f to .25f, .92f to .12f, 1f to .08f)) {
            val frozen = resolveMiuixVideoCardInverseScaleForDepth(sourceX, sourceY, 0f)
            for (autoReturning in listOf(false, true)) {
                for (step in 0..100) {
                    val live = resolveMiuixVideoCardInverseScaleForDepth(
                        sourceX, sourceY, step / 100f, autoReturning,
                    )
                    assertEquals(frozen.scaleX, live.scaleX, .0001f)
                    assertEquals(frozen.scaleY, live.scaleY, .0001f)
                    assertEquals(1f / sourceX, live.scaleX, .0001f)
                }
            }
        }
    }

    @Test
    fun reconstructedCardPixelsStayUndistortedAndFitTheShellAtHandoff() {
        for ((sourceX, sourceY) in listOf(.46f to .25f, .92f to .12f, .3f to .08f)) {
            for (autoReturning in listOf(false, true)) {
                for (step in 0..100) {
                    val depth = step / 100f
                    val landing = com.android.purebilibili.core.ui.transition
                        .resolveVideoHeroLandingScale(depth, autoReturning)
                    val x = resolveMiuixVideoCardOuterScale(sourceX, depth, landing)
                    val y = resolveMiuixVideoCardOuterScaleY(sourceX, sourceY, depth, landing)
                    val compensation = resolveMiuixVideoCardContentCompensation(
                        outerScaleX = x,
                        outerScaleY = y,
                        contentScale = MiuixVideoCardContentScale.FillWidthTop,
                    )
                    val inverse = resolveMiuixVideoCardInverseScaleForDepth(
                        sourceX, sourceY, depth, autoReturning,
                    )
                    val pixelX = inverse.scaleX * compensation.scaleX * x
                    val pixelY = inverse.scaleY * compensation.scaleY * y
                    // Compose the actual nested layers: equal screen-space scaling
                    // preserves text glyphs and cover aspect ratio throughout the return.
                    assertEquals(pixelX, pixelY, .0001f)
                    if (depth <= .18f) {
                        // Once source chrome appears, its full card height fills the clip.
                        // No residual detail-page height may remain as a blank band.
                        assertEquals(y, sourceY * pixelY, .0001f)
                    }
                }
            }
        }
    }

    @Test
    fun landingCompressionIsBoundedRelativeToFinalSizeOnBothAxes() {
        for (sourceScale in listOf(.05f, .2f, .8f, 1f)) {
            for (i in 0..1000) {
                val depth = i / 1000f
                val scale = resolveMiuixVideoCardOuterScale(sourceScale, depth,
                    resolveVideoHeroLandingScale(depth, true))
                val baseline = resolveMiuixVideoCardOuterScale(sourceScale, depth, 1f)
                assertTrue(abs(scale - baseline) <= .015f * sourceScale)
            }
            assertEquals(sourceScale, resolveMiuixVideoCardOuterScale(sourceScale, 0f, 1f))
            assertEquals(1f, resolveMiuixVideoCardOuterScale(sourceScale, 1f, 1f))
        }
    }

    @Test
    fun ordinaryRelatedSlideKeepsSourceUntilItsActualExitCompletes() {
        val scope = object : NavTransitionScope {
            override var relativeDepth by mutableFloatStateOf(-.4f)
            override var role by mutableStateOf(NavRole.Outgoing)
            override val change = NavChange.Pop
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override val gesture: NavGesture? = null
            override val settle: NavSettle? = null
        }
        val progress = MiuixVideoCardTransitionProgress()
        val fallback = NavTransitions.MiuixDefault
        val observed = progress.observe(fallback)
        with(observed) { Modifier.transformEntry(scope) }
        assertEquals(fallback.motion, observed.motion)
        assertEquals(fallback.opaqueDepth, observed.opaqueDepth)
        assertEquals(VideoCardTransitionSettleState.AutoReturn, progress.settleStateOrNull())
        val moving = resolveRelatedReturnSourceRestoreDecision(
            restorePending = true,
            transitionObserved = false,
            transitionAnimated = true,
            exposure = VideoCardTransitionExposure.Returning,
        )
        assertEquals(false, moving.shouldRestore)
        scope.relativeDepth = -1f
        assertEquals(VideoCardTransitionSettleState.Idle, progress.settleStateOrNull())
        val finished = resolveRelatedReturnSourceRestoreDecision(
            restorePending = true,
            transitionObserved = moving.transitionObserved,
            transitionAnimated = true,
            exposure = VideoCardTransitionExposure.Idle,
        )
        assertTrue(finished.shouldRestore)
    }

    @Test
    fun retainedGestureMetadataDoesNotMisclassifyCommitOrCancelAsSeek() {
        val scope = object : NavTransitionScope {
            override var relativeDepth by mutableFloatStateOf(-.4f)
            override var role by mutableStateOf(NavRole.Top)
            override val change = NavChange.Pop
            override val layoutSize = IntSize(1080, 2400)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(3f)
            override val gesture = NavGesture(.4f, NavSwipeEdge.Left, 500f)
            override var settle: NavSettle? by mutableStateOf(null)
        }
        val progress = MiuixVideoCardTransitionProgress()
        progress.bind(scope)
        assertTrue(progress.isGestureInProgress())
        assertEquals(.6f, progress.depthOrNull()!!, absoluteTolerance = .001f)
        scope.settle = object : NavSettle {
            override val phase = NavSettlePhase.Cancel
            override val releaseVelocity = 0f
            override val elapsedMillis = 0f
        }
        assertEquals(false, progress.isGestureInProgress())
        assertEquals(VideoCardTransitionSettleState.CancelRestore, progress.settleStateOrNull())
        scope.settle = object : NavSettle {
            override val phase = NavSettlePhase.Commit
            override val releaseVelocity = 2f
            override val elapsedMillis = 0f
        }
        scope.role = NavRole.Outgoing
        assertEquals(VideoCardTransitionSettleState.AutoReturn, progress.settleStateOrNull())
        scope.relativeDepth = -1f
        assertEquals(VideoCardTransitionSettleState.Idle, progress.settleStateOrNull())

        // The same live presentation subsequently drives a settings navigation. A released
        // video scope must not turn that unrelated transition back into video depth/blur.
        progress.clear()
        scope.relativeDepth = -.2f
        scope.role = NavRole.Incoming
        assertEquals(null, progress.depthOrNull())
        assertEquals(null, progress.settleStateOrNull())
        assertEquals(null, progress.gestureBackProgress())
        assertEquals(false, progress.isGestureInProgress())
        assertEquals(0f, progress.depthOr(0f))

        progress.bind(scope)
        assertEquals(.8f, progress.depthOrNull())
    }

    @Test
    fun returnDepthClearsBlurInsteadOfReversingIt() {
        assertEquals(
            1f,
            resolveMiuixVideoCardDepthProgress(relativeDepth = 0f),
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            0.5f,
            resolveMiuixVideoCardDepthProgress(relativeDepth = -0.5f),
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            0f,
            resolveMiuixVideoCardDepthProgress(relativeDepth = -1f),
            absoluteTolerance = 0.0001f,
        )
    }

    @Test
    fun cardClipKeepsPhysicalCornerRadiusAcrossNonUniformScale() {
        val radii = resolveMiuixVideoCardClipRadii(
            sourceCornerPx = 12f,
            outerScaleX = 0.5f,
            outerScaleY = 0.25f,
        )

        assertEquals(12f, radii.radiusX * 0.5f, absoluteTolerance = 0.0001f)
        assertEquals(12f, radii.radiusY * 0.25f, absoluteTolerance = 0.0001f)
    }

    @Test
    fun flyingCardCornersBloomMidPeelThenLandOnTheSourceRadius() {
        assertEquals(
            0f,
            resolveMiuixVideoCardGestureCornerPx(
                sourceCornerPx = 16f,
                morphProgress = 1f,
                floatingCornerPx = 28f,
            ),
            0.0001f,
        )
        assertEquals(
            32f,
            resolveMiuixVideoCardGestureCornerPx(
                sourceCornerPx = 16f,
                morphProgress = 1f,
                floatingCornerPx = 32f,
                fullscreenCornerPx = 32f,
            ),
            0.0001f,
        )
        assertEquals(
            16f,
            resolveMiuixVideoCardGestureCornerPx(
                sourceCornerPx = 16f,
                morphProgress = 0f,
                floatingCornerPx = 28f,
            ),
            0.0001f,
        )
        assertEquals(
            28f,
            resolveMiuixVideoCardGestureCornerPx(
                sourceCornerPx = 16f,
                morphProgress = 0.5f,
                floatingCornerPx = 28f,
            ),
            0.01f,
        )
        val mid = resolveMiuixVideoCardClipRadii(
            sourceCornerPx = 16f,
            outerScaleX = 0.5f,
            outerScaleY = 0.4f,
            morphProgress = 0.5f,
            floatingCornerPx = 28f,
        )
        assertEquals(28f, mid.radiusX * 0.5f, absoluteTolerance = 0.01f)
        assertEquals(28f, mid.radiusY * 0.4f, absoluteTolerance = 0.01f)

    }

    @Test
    fun transparentHorizontalRow_doesNotCutCoverWithAnOuterFloatingCorner() {
        for (shortSide in listOf(20f, 120f, 1080f)) {
            for (deviceCorner in listOf(12f, 32f, 96f, 240f)) {
                // 同一策略覆盖进入、返回及预测返回取消；外框保持矩形，媒体层拥有封面圆角。
                for (step in 0..100) {
                    assertEquals(0f, resolveMiuixVideoCardGestureCornerPx(
                        sourceCornerPx = 0f,
                        morphProgress = step / 100f,
                        floatingCornerPx = deviceCorner,
                        sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                        currentShortSidePx = shortSide,
                    ))
                }
                // 无底板的横卡例外不能改变其他来源布局的悬浮圆角。
                assertEquals(minOf(deviceCorner, shortSide / 2f), resolveMiuixVideoCardGestureCornerPx(
                    sourceCornerPx = 0f,
                    morphProgress = .5f,
                    floatingCornerPx = deviceCorner,
                    sourceLayout = VideoCardSourceLayout.COVER_ONLY,
                    currentShortSidePx = shortSide,
                ))
            }
        }
    }

    @Test
    fun horizontalCardCornersReachDeviceRadiusAndReverseToTheirSourceRadius() {
        for (source in listOf(8f, 12f, 48f)) {
            for (deviceCorner in listOf(12f, 28f, 96f, 240f)) {
                val samples = (0..100).map { step ->
                    resolveMiuixVideoCardGestureCornerPx(
                        sourceCornerPx = source,
                        morphProgress = step / 100f,
                        floatingCornerPx = deviceCorner,
                        sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                        currentShortSidePx = source * 10f,
                    )
                }
                assertEquals(source, samples.first(), .0001f)
                assertEquals(0f, samples.last(), .0001f)
                assertEquals(minOf(deviceCorner, source * 5f), samples[50], .0001f)
                assertTrue(samples.all { it in 0f..source * 5f })
                assertTrue(samples.drop(50).zipWithNext().all { (a, b) -> b <= a })
                val reversed = (100 downTo 0).map { step ->
                    resolveMiuixVideoCardGestureCornerPx(
                        source, step / 100f, deviceCorner,
                        sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                        currentShortSidePx = source * 10f,
                    )
                }
                assertEquals(samples, reversed.reversed())
            }
        }
    }

    @Test
    fun stackedCardCornersMatchHorizontalCardsForEveryDepthAndWindowSize() {
        for (source in listOf(8f, 16f, 48f)) {
            for (shortSide in listOf(20f, 120f, 1080f)) {
                for (deviceCorner in listOf(12f, 28f, 96f, 240f)) {
                    val samples = (0..100).map { step ->
                        val depth = step / 100f
                        val stacked = resolveMiuixVideoCardGestureCornerPx(
                            source, depth, deviceCorner, sourceLayout = VideoCardSourceLayout.STACKED,
                            currentShortSidePx = shortSide,
                        )
                        assertEquals(resolveMiuixVideoCardGestureCornerPx(
                            source, depth, deviceCorner, sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                            currentShortSidePx = shortSide,
                        ), stacked, .0001f)
                        assertEquals(resolveMiuixVideoCardGestureCornerPx(
                            source, depth, deviceCorner, sourceLayout = VideoCardSourceLayout.COVER_ONLY,
                            currentShortSidePx = shortSide,
                        ), stacked, .0001f)
                        assertTrue(stacked in 0f..shortSide / 2f)
                        stacked
                    }
                    assertEquals(minOf(source, shortSide / 2f), samples.first(), .0001f)
                    assertEquals(0f, samples.last(), .0001f)
                    assertEquals(minOf(deviceCorner, shortSide / 2f), samples[50], .0001f)
                    assertTrue(samples.drop(50).zipWithNext().all { (a, b) -> b <= a })
                }
            }
        }
        // 仅封面的悬浮圆角也受实际窗口短边约束。
        assertEquals(10f, resolveMiuixVideoCardGestureCornerPx(
            16f, .5f, 240f, currentShortSidePx = 20f,
        ), .0001f)
    }

    @Test
    fun horizontalCornerFitsItsCurrentShortSideAndRemainsCircularAfterUnequalScaling() {
        assertEquals(10f, resolveMiuixVideoCardGestureCornerPx(
            sourceCornerPx = 48f,
            morphProgress = .25f,
            floatingCornerPx = 240f,
            sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
            currentShortSidePx = 20f,
        ), .0001f)
        val radii = resolveMiuixVideoCardClipRadii(
            sourceCornerPx = 16f,
            outerScaleX = .8f,
            outerScaleY = .2f,
            morphProgress = .5f,
            floatingCornerPx = 96f,
            sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
            currentShortSidePx = 120f,
        )
        assertEquals(60f, radii.radiusX * .8f, .0001f)
        assertEquals(60f, radii.radiusY * .2f, .0001f)
    }

    @Test
    fun fillWidthTopPreservesAspectRatioAndTopAlignment() {
        val compensation = resolveMiuixVideoCardContentCompensation(
            outerScaleX = 0.5f,
            outerScaleY = 0.25f,
            contentScale = MiuixVideoCardContentScale.FillWidthTop,
        )

        assertEquals(0.5f, 0.5f * compensation.scaleX, absoluteTolerance = 0.0001f)
        assertEquals(0.5f, 0.25f * compensation.scaleY, absoluteTolerance = 0.0001f)
        assertEquals(TransformOrigin(0.5f, 0f), compensation.transformOrigin)
    }

    @Test
    fun cropCenterPreservesAspectRatioUsingCoverScale() {
        val compensation = resolveMiuixVideoCardContentCompensation(
            outerScaleX = 0.35f,
            outerScaleY = 0.6f,
            contentScale = MiuixVideoCardContentScale.CropCenter,
        )

        assertEquals(0.6f, 0.35f * compensation.scaleX, absoluteTolerance = 0.0001f)
        assertEquals(0.6f, 0.6f * compensation.scaleY, absoluteTolerance = 0.0001f)
        assertEquals(TransformOrigin.Center, compensation.transformOrigin)
    }

    @Test
    fun sideBySideAndStackedUseFillWidthTopForLandingAnchors() {
        assertEquals(
            MiuixVideoCardContentScale.FillWidthTop,
            resolveMiuixVideoCardContentScaleForSourceLayout(
                sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
            ),
        )
        assertEquals(
            MiuixVideoCardContentScale.FillWidthTop,
            resolveMiuixVideoCardContentScaleForSourceLayout(
                sourceLayout = VideoCardSourceLayout.STACKED,
            ),
        )
        assertEquals(
            MiuixVideoCardContentScale.CropCenter,
            resolveMiuixVideoCardContentScaleForSourceLayout(
                sourceLayout = VideoCardSourceLayout.STACKED,
                fullscreen = true,
            ),
        )
    }

}
