package com.android.purebilibili.feature.video.screen

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.resolveVideoDetailMediaLayoutHandoffProgress
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailReturnMediaLayoutTest {
    @Test
    fun opaqueCoverOcclusionPreservesInsetAndRoundedEdges() {
        val frame = VideoDetailReturnMediaLayoutFrame(
            offsetXPx = 20, offsetYPx = 48, widthPx = 1000, heightPx = 600,
        )
        assertEquals(Rect(37f, 65f, 1003f, 631f),
            resolveVideoDetailOpaqueCoverInterior(frame, coverAlpha = 1f, cornerRadiusPx = 16f))
        // A loading image still has an opaque cover backing; a partial crossfade does not.
        assertEquals(null, resolveVideoDetailOpaqueCoverInterior(frame, 0.999f, 16f))
        assertEquals(null, resolveVideoDetailOpaqueCoverInterior(frame, 0f, 16f))
    }

    @Test
    fun opaqueCoverOcclusionRejectsTinyOrInvalidCoverage() {
        val tiny = VideoDetailReturnMediaLayoutFrame(0, 0, 10, 10)
        assertEquals(null, resolveVideoDetailOpaqueCoverInterior(tiny, 1f, 16f))
        assertEquals(null, resolveVideoDetailOpaqueCoverInterior(tiny, Float.NaN, 0f))
        assertEquals(Rect(1f, 1f, 9f, 9f), resolveVideoDetailOpaqueCoverInterior(tiny, 1f, 0f))
    }

    @Test
    fun horizontalCoverStaysInsideTheContractingShellInBothDirectionsAndOnCancel() {
        val hostWidth = 1080f
        val hostHeight = 2400f
        val bounds = Rect(24f, 800f, 1056f, 1088f)
        val cover = Rect(24f, 800f, 456f, 1070f)
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = hostWidth,
            sourceBounds = bounds,
            sourceCoverBounds = cover,
            sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
        )
        val sourceX = bounds.width / hostWidth
        val sourceY = bounds.height / hostHeight
        for (phase in listOf(
            VideoCardTransitionBackgroundPhase.OPENING,
            VideoCardTransitionBackgroundPhase.RETURNING,
            VideoCardTransitionBackgroundPhase.HELD,
        )) {
            // Reverse sampling also covers cancelling/re-grabbing a predictive return.
            for (step in (0..1000).toList() + (1000 downTo 0).toList()) {
                val depth = step / 1000f
                val gesture = phase == VideoCardTransitionBackgroundPhase.HELD
                val alpha = resolveVideoDetailFlyingSourceChromeAlpha(
                    depth, phase, gesture, VideoCardSourceLayout.SIDE_BY_SIDE,
                )
                val handoff = resolveVideoDetailMediaLayoutHandoffProgress(
                    depth, phase, gesture, VideoCardSourceLayout.SIDE_BY_SIDE,
                    sourceChromeAlpha = alpha,
                )
                val frame = resolveVideoDetailReturnMediaLayoutFrame(
                    containerWidthPx = hostWidth.toInt(),
                    containerHeightPx = 608,
                    landingLayout = layout,
                    handoffProgress = handoff,
                )
                val outer = com.android.purebilibili.navigation3.predictiveback
                    .resolveMiuixVideoCardOuterScales(sourceX, sourceY, depth, 1f)
                val x = outer.x
                val y = outer.y
                val visibleCoverBottom = (frame.offsetYPx + frame.heightPx) * x
                assertTrue(
                    visibleCoverBottom <= hostHeight * y + 1f,
                    "Cover clipped at depth=$depth phase=$phase: bottom=$visibleCoverBottom shell=${hostHeight * y}",
                )
                val detailAlpha = com.android.purebilibili.core.ui.transition
                    .resolveVideoCardDetailChromeAlpha(
                        depth, phase, gesture, sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                    )
                assertEquals(1f, detailAlpha + alpha, 0.0001f)
                assertEquals(alpha, handoff, 0.0001f)
            }
        }
    }

    @Test
    fun sixteenByTenSourceCoverHasTheSameOpeningReturnAndIdleEndpoint() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1080f,
            sourceBounds = Rect(20f, 100f, 520f, 660f),
            sourceCoverBounds = Rect(20f, 100f, 520f, 412.5f),
            sourceInfoBounds = Rect(20f, 412.5f, 520f, 660f),
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        val inverse = com.android.purebilibili.navigation3.predictiveback
            .resolveMiuixVideoCardInverseScaleForDepth(
                sourceScaleX = 500f / 1080f,
                sourceScaleY = 560f / 2400f,
                depth = 0f,
            )
        for (phase in listOf(
            VideoCardTransitionBackgroundPhase.OPENING,
            VideoCardTransitionBackgroundPhase.RETURNING,
            VideoCardTransitionBackgroundPhase.IDLE,
        )) {
            val handoff = resolveVideoDetailMediaLayoutHandoffProgress(
                morphDepthProgress = 0f,
                phase = phase,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
                isCommittedCardReturn = phase != VideoCardTransitionBackgroundPhase.OPENING,
            )
            val frame = resolveVideoDetailReturnMediaLayoutFrame(
                containerWidthPx = 1080,
                containerHeightPx = 608,
                landingLayout = layout,
                handoffProgress = handoff,
                inverseScaleX = inverse.scaleX,
                inverseScaleY = inverse.scaleY,
            )
            assertEquals(675, frame.heightPx)
            assertEquals(312.5f, frame.heightPx * layout.sourceScale, 0.001f)
            assertEquals(frame.heightPx, resolveVideoDetailStackedInfoTopPx(layout, inverse.scaleY, frame))
        }
    }

    @Test
    fun stackedInfoFollowsMediaEdgeBeforeLandingWithTheOriginalGap() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1080f,
            sourceBounds = Rect(20f, 100f, 520f, 640f),
            sourceCoverBounds = Rect(20f, 100f, 520f, 380f),
            sourceInfoBounds = Rect(20f, 392f, 520f, 640f),
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        // Include entering (handoff 0) and an early loading return (partial handoff).
        for (handoff in listOf(0f, 0.25f, 0.75f, 1f)) {
            val frame = resolveVideoDetailReturnMediaLayoutFrame(
                containerWidthPx = 1080,
                containerHeightPx = 608,
                landingLayout = layout,
                handoffProgress = handoff,
                inverseScaleX = 1.7f,
                inverseScaleY = 2f,
                contentTopInsetPx = 63,
            )
            assertEquals(
                frame.offsetYPx + frame.heightPx + 24,
                resolveVideoDetailStackedInfoTopPx(layout, 2f, frame),
            )
        }
        assertEquals(
            584,
            resolveVideoDetailStackedInfoTopPx(layout, 2f, null),
        )
    }

    @Test
    fun stackedCoverWithTopOffsetMeetsStationaryInfoWithoutAMeasuredGap() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1080f,
            sourceBounds = Rect(20f, 100f, 520f, 640f),
            sourceCoverBounds = Rect(20f, 106f, 520f, 386f),
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        for (inverseY in listOf(1f, 1.3f, 1.83f, 2f, 2.4f)) {
            val frame = resolveVideoDetailReturnMediaLayoutFrame(
                containerWidthPx = 1080,
                containerHeightPx = 608,
                landingLayout = layout,
                handoffProgress = 1f,
                inverseScaleY = inverseY,
                contentTopInsetPx = 63,
            )
            val stationaryTop = (286f * inverseY).roundToInt()
            assertEquals(stationaryTop, resolveVideoDetailStackedInfoTopPx(layout, inverseY, frame))
            assertEquals(stationaryTop, resolveVideoDetailStackedInfoTopPx(layout, inverseY, null))
        }
    }

    @Test
    fun mediaBottomIsIndependentOfSystemBarInsetRounding() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1080f,
            sourceBounds = Rect(20f, 100f, 520f, 640f),
            sourceCoverBounds = Rect(20f, 100f, 520f, 380f),
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        for (step in 0..100) {
            fun frame(inset: Int) = resolveVideoDetailReturnMediaLayoutFrame(
                containerWidthPx = 1080,
                containerHeightPx = 608,
                landingLayout = layout,
                handoffProgress = step / 100f,
                inverseScaleY = 1.83f,
                contentTopInsetPx = inset,
            )
            val cover = frame(63)
            val backing = frame(0)
            assertEquals(backing.offsetYPx + backing.heightPx, cover.offsetYPx + cover.heightPx)
        }
    }

    @Test
    fun fullBarSnapshotKeepsItsAspectRatioWhileTheOuterShellIsStillTall() {
        val bounds = Rect(24f, 1800f, 1056f, 1968f)
        // Source bitmap reconstruction must preserve the width-based scale on both
        // axes even before the flying shell reaches its final bounds.
        val inverse = com.android.purebilibili.navigation3.predictiveback
            .resolveMiuixVideoCardInverseScaleForDepth(
                sourceScaleX = bounds.width / 1080f,
                sourceScaleY = bounds.height / 2400f,
                depth = 0.1f,
            )
        assertEquals(inverse.scaleX, inverse.scaleY, .0001f)
        val frame = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 1080,
            containerHeightPx = 608,
            landingLayout = null,
            handoffProgress = 1f,
            inverseScaleX = inverse.scaleX,
            inverseScaleY = inverse.scaleX,
            nativeSnapshotBounds = bounds,
        )
        assertEquals(bounds.width / bounds.height, frame.widthPx.toFloat() / frame.heightPx, 0.03f)
        assertEquals(0, frame.offsetXPx)
        assertEquals(0, frame.offsetYPx)
    }

    @Test
    fun nativeCapsuleUsesLocalOriginAndOneInverseCompensation() {
        val bounds = Rect(20f, 880f, 660f, 952f)
        val frame = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 720,
            containerHeightPx = 400,
            landingLayout = null,
            handoffProgress = 1f,
            inverseScaleX = 1.125f,
            inverseScaleY = 1.125f,
            nativeSnapshotBounds = bounds,
        )
        assertEquals(0, frame.offsetXPx)
        assertEquals(0, frame.offsetYPx)
        assertEquals(720, frame.widthPx)
        assertEquals(81, frame.heightPx)
        // The shell's uniform source scale maps this local frame back to the original bar.
        assertEquals(bounds.width, frame.widthPx / 1.125f, 0.001f)
        assertEquals(bounds.height, frame.heightPx / 1.125f, 0.001f)
        val movedBounds = bounds.translate(Offset(100f, -400f))
        assertEquals(frame, resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 720,
            containerHeightPx = 400,
            landingLayout = null,
            handoffProgress = 1f,
            inverseScaleX = 1.125f,
            inverseScaleY = 1.125f,
            nativeSnapshotBounds = movedBounds,
        ))
    }

    @Test
    fun floatingCardOmitsSystemBarInsetAndRestoresItNearFullscreen() {
        for (depth in listOf(-1f, 0f, 0.5f, 0.9f)) {
            assertEquals(0f, resolveVideoDetailCardTopInsetFraction(depth))
        }
        assertEquals(0.5f, resolveVideoDetailCardTopInsetFraction(0.95f), 0.0001f)
        assertEquals(1f, resolveVideoDetailCardTopInsetFraction(1f))
        assertEquals(1f, resolveVideoDetailCardTopInsetFraction(2f))
        val cover = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 600,
            containerHeightPx = 400,
            landingLayout = null,
            handoffProgress = 0f,
            contentTopInsetPx = 0,
        )
        assertEquals(0, cover.offsetYPx)
        assertEquals(400, cover.heightPx)
        val expanded = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 600,
            containerHeightPx = 400,
            landingLayout = null,
            handoffProgress = 0f,
            contentTopInsetPx = 40,
        )
        assertEquals(40, expanded.offsetYPx)
        assertEquals(360, expanded.heightPx)
    }

    @Test
    fun splitPlayerKeepsItsOwnOriginBeforeReturn() {
        val frame = VideoDetailReturnMediaLayoutFrame(0, 0, 700, 394)
        assertEquals(frame, resolveVideoDetailReturnMediaFrameInSlot(frame, Offset(220f, 32f), 0f))
    }

    @Test
    fun splitPlayerConvertsEntryLandingToLocalCoordinates() {
        val frame = VideoDetailReturnMediaLayoutFrame(20, 10, 400, 225)
        val local = resolveVideoDetailReturnMediaFrameInSlot(frame, Offset(220f, 32f), 1f)
        assertEquals(-200, local.offsetXPx)
        assertEquals(-22, local.offsetYPx)
        assertEquals(frame.widthPx, local.widthPx)
        assertEquals(frame.heightPx, local.heightPx)
        // The final cover lands at the same entry-space origin despite a shifted player pane.
        assertEquals(20f, local.offsetXPx + 220f)
        assertEquals(10f, local.offsetYPx + 32f)
    }

    @Test
    fun splitPlayerOriginCompensationIsContinuousAndReversible() {
        val frame = VideoDetailReturnMediaLayoutFrame(0, 0, 700, 394)
        val half = resolveVideoDetailReturnMediaFrameInSlot(frame, Offset(220f, 32f), 0.5f)
        assertEquals(-110, half.offsetXPx)
        assertEquals(-16, half.offsetYPx)
        assertEquals(frame, resolveVideoDetailReturnMediaFrameInSlot(frame, Offset(220f, 32f), -1f))
    }

    @Test
    fun stackedCardUsesMeasuredCoverGeometry() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1000f,
            sourceBounds = Rect(100f, 200f, 500f, 700f),
            sourceCoverBounds = Rect(100f, 200f, 500f, 440f),
            sourceLayout = VideoCardSourceLayout.STACKED,
        )

        assertTrue(layout.canRender)
        assertEquals(240f, layout.coverHeightPx)
        assertEquals(260f, layout.infoHeightPx)
    }

    @Test
    fun horizontalCardUsesMeasuredFullCardHeight() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1000f,
            sourceBounds = Rect(40f, 100f, 840f, 360f),
            sourceCoverBounds = Rect(40f, 100f, 320f, 300f),
            sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
        )

        assertTrue(layout.canRender)
        assertEquals(260f, layout.infoHeightPx)
        assertEquals(280f, layout.coverWidthPx)
    }

    @Test
    fun invalidBoundsDoNotProduceReturnGeometry() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1000f,
            sourceBounds = null,
            sourceCoverBounds = null,
        )

        assertFalse(layout.canRender)
    }

    @Test
    fun entryCoverMatchesManualStartViewportBelowStatusBar() {
        for (topInset in listOf(0, 72)) {
            val frame = resolveVideoDetailReturnMediaLayoutFrame(
                containerWidthPx = 1088,
                containerHeightPx = 612 + topInset,
                landingLayout = null,
                handoffProgress = 0f,
                contentTopInsetPx = topInset,
            )
            assertEquals(0, frame.offsetXPx)
            assertEquals(topInset, frame.offsetYPx)
            assertEquals(1088, frame.widthPx)
            assertEquals(612, frame.heightPx)
        }
    }

    @Test
    fun statusBarInsetFadesOutOfReturnGeometryWithoutChangingCardLanding() {
        val landing = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1088f,
            sourceBounds = Rect(0f, 0f, 544f, 420f),
            sourceCoverBounds = Rect(0f, 0f, 544f, 306f),
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        fun frame(progress: Float, inset: Int) = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 1088,
            containerHeightPx = 684,
            landingLayout = landing,
            handoffProgress = progress,
            contentTopInsetPx = inset,
        )
        assertEquals(72, frame(0f, 72).offsetYPx)
        assertEquals(612, frame(0f, 72).heightPx)
        assertEquals(36, frame(0.5f, 72).offsetYPx)
        assertEquals(frame(1f, 0), frame(1f, 72))
    }

    @Test
    fun coverInsetCannotProduceEmptyOrNegativeMediaSize() {
        for (inset in listOf(-20, 1000)) {
            val frame = resolveVideoDetailReturnMediaLayoutFrame(
                containerWidthPx = 1088,
                containerHeightPx = 100,
                landingLayout = null,
                handoffProgress = 0f,
                contentTopInsetPx = inset,
            )
            assertTrue(frame.heightPx >= 1)
            assertEquals(100, frame.offsetYPx + frame.heightPx)
        }
    }

    @Test
    fun mediaFrameInterpolatesToMeasuredCover() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1000f,
            sourceBounds = Rect(0f, 0f, 500f, 300f),
            sourceCoverBounds = Rect(0f, 0f, 200f, 180f),
            sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
        )
        val frame = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 1000,
            containerHeightPx = 600,
            landingLayout = layout,
            handoffProgress = 1f,
        )

        assertEquals(400, frame.widthPx)
        // Side-by-side media overdraws one source pixel below the landing edge. The outer
        // shared-bounds clip removes the excess and prevents the black player layer peeking out.
        assertEquals(362, frame.heightPx)
    }

    @Test
    fun mediaFrameUsesCurrentInverseYSoCoverTracksTheClip() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1000f,
            sourceBounds = Rect(0f, 0f, 500f, 300f),
            sourceCoverBounds = Rect(0f, 0f, 500f, 240f),
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        val frozen = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 1000,
            containerHeightPx = 600,
            landingLayout = layout,
            handoffProgress = 1f,
        )
        val clipTracked = resolveVideoDetailReturnMediaLayoutFrame(
            containerWidthPx = 1000,
            containerHeightPx = 600,
            landingLayout = layout,
            handoffProgress = 1f,
            inverseScaleX = 2f,
            inverseScaleY = 2.4f,
        )

        assertEquals(1000, frozen.widthPx)
        assertEquals(480, frozen.heightPx)
        assertEquals(1000, clipTracked.widthPx)
        assertEquals(576, clipTracked.heightPx)
    }

    @Test
    fun playerLayoutResolvesTheMediaFrameOncePerNodeSize() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/" +
                "VideoDetailReturnMediaLayout.kt"
        ).readText()
        val player = source.substringAfter("internal fun Modifier.videoDetailReturnPlayerLayout(")
        // layout 与 draw 在同一帧各解析一次 frame 会重复插值、逆缩放读取与对象分配；
        // 返回手势期间逐帧命中，因此必须按节点尺寸与输入做记忆化。
        assertTrue(player.contains("var memoFrame: VideoDetailReturnMediaLayoutFrame? = null"))
        assertTrue(player.contains("nativeBounds == memoNativeBounds"))
        assertTrue(player.contains("return cached"))
        assertTrue(player.contains("memoFrame = resolved"))
    }

    @Test
    fun stackedCardPrefersMeasuredInfoBandOverSubtraction() {
        val layout = resolveVideoDetailReturnSourceCardLayout(
            viewportWidthPx = 1000f,
            sourceBounds = Rect(100f, 200f, 500f, 900f),
            sourceCoverBounds = Rect(100f, 200f, 500f, 640f),
            sourceLayout = VideoCardSourceLayout.STACKED,
            sourceInfoBounds = Rect(100f, 640f, 500f, 880f),
        )

        assertTrue(layout.canRender)
        // 实测信息面 240px；「卡底 900 − 封面底 640」的减法推导会得到 260px，
        // 差的这 20px 就是卡槽里没被盖住的那段近白带。
        assertEquals(240f, layout.infoHeightPx)
        // infoTop 相对卡片左上角 = 640 − 200 = 440；sourceScale = 400 / 1000 = 0.4。
        assertEquals(1100f, layout.infoAnchorYInViewportPx, 0.001f)
    }

    @Test
    fun stackedCardFallsBackWhenMeasuredInfoBandIsDirty() {
        // 压在封面里 / 窄于整卡 / 越出卡底：任一脏数据都必须回退减法推导。
        for (dirty in listOf(
            Rect(120f, 300f, 300f, 500f),
            Rect(120f, 640f, 260f, 880f),
            Rect(100f, 640f, 500f, 1000f),
        )) {
            val layout = resolveVideoDetailReturnSourceCardLayout(
                viewportWidthPx = 1000f,
                sourceBounds = Rect(100f, 200f, 500f, 900f),
                sourceCoverBounds = Rect(100f, 200f, 500f, 640f),
                sourceLayout = VideoCardSourceLayout.STACKED,
                sourceInfoBounds = dirty,
            )

            assertTrue(layout.canRender)
            assertEquals(260f, layout.infoHeightPx)
            assertEquals(440f / 0.4f, layout.infoAnchorYInViewportPx, 0.001f)
        }
    }
}
