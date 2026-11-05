package com.android.purebilibili.feature.home.components.cards

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCardScrollLiteVisualPolicyTest {

    @Test
    fun `normal mode adds cover gradient behind compact stats`() {
        val policy = resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = false,
            compactStatsOnCover = true
        )

        assertEquals(0f, policy.coverShadowElevationDp, 0.0001f)
        assertTrue(policy.showCoverGradientMask)
        assertTrue(policy.showHistoryProgressBar)
        assertTrue(policy.showCompactStatsOnCover)
        assertFalse(policy.showSecondaryStatsRow)
    }

    @Test
    fun `normal mode removes cover gradient when stats move below cover`() {
        val policy = resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = false,
            compactStatsOnCover = false
        )

        assertEquals(0f, policy.coverShadowElevationDp, 0.0001f)
        assertFalse(policy.showCoverGradientMask)
        assertTrue(policy.showHistoryProgressBar)
        assertFalse(policy.showCompactStatsOnCover)
        assertTrue(policy.showSecondaryStatsRow)
    }

    @Test
    fun `scroll lite mode keeps stats without cover shadow`() {
        val policy = resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = true,
            compactStatsOnCover = true
        )

        assertEquals(0f, policy.coverShadowElevationDp, 0.0001f)
        assertTrue(policy.showCoverGradientMask)
        assertFalse(policy.showHistoryProgressBar)
        assertTrue(policy.showCompactStatsOnCover)
        assertFalse(policy.showSecondaryStatsRow)
    }

    @Test
    fun `scroll lite mode keeps secondary row when cover stats are disabled`() {
        val policy = resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = true,
            compactStatsOnCover = false
        )

        assertEquals(0f, policy.coverShadowElevationDp, 0.0001f)
        assertFalse(policy.showCompactStatsOnCover)
        assertTrue(policy.showSecondaryStatsRow)
    }

    @Test
    fun `normal mode keeps story card secondary stats row`() {
        val policy = resolveStoryVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = false
        )

        assertEquals(0f, policy.coverShadowElevationDp, 0.0001f)
        assertTrue(policy.showSecondaryStatsRow)
    }

    @Test
    fun `scroll lite mode removes story card shadows but keeps stats`() {
        val policy = resolveStoryVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = true
        )

        assertEquals(0f, policy.coverShadowElevationDp, 0.0001f)
        assertTrue(policy.showSecondaryStatsRow)
    }

    @Test
    fun `home video card variants do not attach shadow modifiers`() {
        listOf(
            "VideoCard.kt",
            "StoryVideoCard.kt"
        ).forEach { fileName ->
            val source = File("src/main/java/com/android/purebilibili/feature/home/components/cards/$fileName")
                .readText()

            assertFalse("$fileName should not draw video cover shadows", source.contains(".shadow("))
        }
    }

    @Test
    fun `elegant video card clips static cover container to cover shape`() {
        val source = File("src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt")
            .readText()

        val coverModifier = source
            .substringAfter(".testTag(\"home_video_cover\")")
            .substringBefore(".onGloballyPositioned")
        val aspectRatioIndex = coverModifier.indexOf(".aspectRatio(coverAspectRatio)")
        val clipIndex = coverModifier.indexOf(".clip(coverShape)")

        assertTrue(aspectRatioIndex >= 0)
        assertTrue(
            "首页视频封面本体必须裁剪 coverShape，不能只依赖 sharedBounds 的 overlay 裁剪。",
            aspectRatioIndex < clipIndex,
        )
    }



    @Test
    fun `video card keeps the title width while placing overflow action at the bottom end`() {
        val source = File("src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt")
            .readText()
        val titleBlock = source
            .substringAfter("// 标题独占整行")
            .substringBefore("Spacer(modifier = Modifier.height(if (compactMetadata)")

        assertTrue(titleBlock.contains(".fillMaxWidth()"))
        assertFalse(titleBlock.contains(".weight(1f)"))
        assertTrue(source.contains("modifier = Modifier.align(Alignment.BottomEnd)"))
        assertTrue(source.contains("contentAlignment = Alignment.BottomEnd"))
        // 溢出按钮视觉图标只占约 20dp：预留缩为 24dp（ExtraLarge），把更多宽度让给
        // UP 名称/日期行，名称不会被提前折叠省略。
        assertTrue(source.contains("Modifier.padding(end = AppSpacingTokens.ExtraLarge)"))
    }



    @Test
    fun `return target cover disables crossfade during and after shared return`() {
        // 返回过程中
        assertFalse(
            shouldEnableVideoCardCoverCrossfade(
                isScrollInProgress = false,
                isReturningFromDetail = true,
                useCoverSharedBounds = true,
                isSharedReturnTarget = true
            )
        )
        // clearReturning 之后仍是 lastClicked 目标：必须继续关 crossfade，否则会再闪一次
        assertFalse(
            shouldEnableVideoCardCoverCrossfade(
                isScrollInProgress = false,
                isReturningFromDetail = false,
                useCoverSharedBounds = true,
                isSharedReturnTarget = true
            )
        )
        // 非返回目标可正常 crossfade
        assertTrue(
            shouldEnableVideoCardCoverCrossfade(
                isScrollInProgress = false,
                isReturningFromDetail = false,
                useCoverSharedBounds = true,
                isSharedReturnTarget = false
            )
        )
    }

    @Test
    fun `video card cover request remembers crossfade to avoid rebuild flash`() {
        val source = File("src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt")
            .readText()
        assertTrue(source.contains("val coverImageRequest = remember("))
        assertTrue(source.contains("coverCrossfadeEnabled"))
        assertTrue(source.contains("pinnedSharedReturnCover"))
        assertTrue(source.contains(".placeholderMemoryCacheKey(requestCoverCacheKey)"))
        assertTrue(source.contains("model = coverImageRequest"))
    }

    @Test
    fun `shared return target pins cover source to avoid mid-return swap flash`() {
        assertTrue(shouldPinVideoCardCoverForSharedReturn(isSharedReturnTarget = true))
        assertFalse(shouldPinVideoCardCoverForSharedReturn(isSharedReturnTarget = false))
    }

    @Test
    fun `non return target cover keeps crossfade`() {
        // 同屏其它卡：返回会话中仍可 crossfade
        assertTrue(
            shouldEnableVideoCardCoverCrossfade(
                isScrollInProgress = false,
                isReturningFromDetail = true,
                useCoverSharedBounds = true,
                isSharedReturnTarget = false
            )
        )
        // lastClicked 返回目标：clear 后仍关 crossfade（防落位闪）
        assertFalse(
            shouldEnableVideoCardCoverCrossfade(
                isScrollInProgress = false,
                isReturningFromDetail = false,
                useCoverSharedBounds = true,
                isSharedReturnTarget = true
            )
        )
    }

    @Test
    fun `scrolling disables cover crossfade`() {
        assertFalse(
            shouldEnableVideoCardCoverCrossfade(
                isScrollInProgress = true,
                isReturningFromDetail = false,
                useCoverSharedBounds = false,
                isSharedReturnTarget = false
            )
        )
    }

    @Test
    fun `home video metadata keeps creator secondary to title`() {
        val onSurface = Color(0xFF1D1B20)
        val onSurfaceVariant = Color(0xFF49454F)
        val colors = resolveHomeVideoCardMetadataColors(
            onSurfaceColor = onSurface,
            onSurfaceVariantColor = onSurfaceVariant,
        )

        assertEquals(onSurfaceVariant, colors.upNameColor)
        assertEquals(onSurface.copy(alpha = 0.82f), colors.upMetaColor)
        assertEquals(onSurface.copy(alpha = 0.68f), colors.upBadgeTextColor)
        assertEquals(onSurface.copy(alpha = 0.10f), colors.upBadgeBackgroundColor)
        assertEquals(onSurface.copy(alpha = 0.72f), colors.publishTimeColor)
    }

    @Test
    fun `home video card reserves followed badge height to align publish rows`() {
        val cardSource = File("src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt")
            .readText()
        val upBadgeSource = File("src/main/java/com/android/purebilibili/core/ui/components/UpBadgeName.kt")
            .readText()

        assertTrue(
            cardSource.contains(
                "trailingSlotMinHeight = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall"
            )
        )
        assertTrue(upBadgeSource.contains(".heightIn(min = trailingSlotMinHeight)"))
    }

    @Test
    fun homeCardSourceVisual_staysHiddenWhileFlyingEntryOwnsReturn() {
        // Cover stays 0 until the flying entry unloads; the flying media slot owns those pixels.
        assertEquals(
            0f,
            resolveHomeCardReturnSourceVisualAlpha(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundProgress = 1f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardReturnSourceVisualAlpha(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundProgress = 0.10f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardReturnSourceVisualAlpha(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundProgress = 0.02f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveHomeCardReturnSourceVisualAlpha(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.IDLE,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundProgress = 0f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveHomeCardReturnSourceVisualAlpha(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundProgress = 1f,
                preferWholeCardReturn = true,
            ),
            0.001f,
        )
    }

    @Test
    fun retainedSourceCardStaysTransparentUntilFlyingEntryLands() {
        assertEquals(
            1f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = false,
                preferWholeCardReturn = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = false,
                preferWholeCardReturn = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0.2f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = true,
                preferWholeCardReturn = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0.04f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = true,
                preferWholeCardReturn = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.HELD,
                isVideoCardReturnGestureInProgress = true,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0.5f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = true,
                preferWholeCardReturn = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.IDLE,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = true,
                preferWholeCardReturn = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = true,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0.5f,
            ),
            0.001f,
        )
    }

    @Test
    fun flyingReturnContextIncludesMiuixSettleEvenWhenLegacyPhaseIsHeld() {
        assertTrue(
            isVideoCardFlyingReturnContext(
                isReturningFromDetail = false,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.HELD,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0.4f,
            )
        )
        assertTrue(
            isVideoCardFlyingReturnContext(
                isReturningFromDetail = false,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.HELD,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0.4f,
            )
        )
        assertFalse(
            isVideoCardFlyingReturnContext(
                isReturningFromDetail = false,
                isVideoCardReturnGestureInProgress = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0.4f,
            )
        )
        // Stationary list info stays hidden so it does not remain in place while the card flies.
        assertEquals(
            0f,
            resolveHomeCardChromeAlphaDuringShellReturnMorph(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.HELD,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0.16f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardReturnSourceVisualAlpha(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.HELD,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0.10f,
            ),
            0.001f,
        )
    }

    @Test
    fun homeCardChromeStaysHiddenUntilReverseReturnCompletes() {
        assertEquals(
            0f,
            resolveHomeCardChromeAlphaDuringShellReturnMorph(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 1f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardChromeAlphaDuringShellReturnMorph(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0.16f,
            ),
            0.001f,
        )
        // Morph finished: ownership transfers back to the stationary source card.
        assertEquals(
            1f,
            resolveHomeCardChromeAlphaDuringShellReturnMorph(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.IDLE,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveHomeCardChromeAlphaDuringShellReturnMorph(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = true,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 1f,
                preferWholeCardReturn = true,
            ),
            0.001f,
        )
        // 进场 OPENING：点击预武装时仍画整卡，飞卡盖住后再藏字。
        assertEquals(
            1f,
            resolveHomeCardChromeAlphaDuringShellReturnMorph(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardChromeAlphaDuringShellReturnMorph(
                useCardContainerSharedBounds = true,
                isSharedMorphSourceCard = true,
                isReturningFromDetail = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
                isSharedTransitionActive = true,
                transitionBackgroundProgress = 0.5f,
            ),
            0.001f,
        )
        assertTrue(
            isVideoCardSharedReturnTarget(
                bvid = "BV1xx",
                sourceRoute = "home?category=1",
                lastClickedVideoSourceKey = "home?category=1:BV1xx",
            )
        )
    }

    @Test
    fun sourceCardTakesOverAtTerminalDepthBeforeClockReachesIdle() {
        assertEquals(
            1f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = true,
                preferWholeCardReturn = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHomeCardStationaryRevealAlpha(
                isReturnContext = true,
                preferWholeCardReturn = false,
                transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
                isVideoCardReturnGestureInProgress = false,
                isSharedTransitionActive = false,
                transitionBackgroundProgress = 0.01f,
            ),
            0.001f,
        )
    }

    @Test
    fun duplicateLargeScreenCards_onlyClickedInstanceOwnsSharedReturn() {
        assertTrue(isVideoCardSharedSourceInstanceOwner(12L, null))
        assertTrue(isVideoCardSharedSourceInstanceOwner(12L, 12L))
        assertFalse(isVideoCardSharedSourceInstanceOwner(13L, 12L))
    }

    @Test
    fun horizontalCardChrome_followsOpeningAndStaysHiddenOnReturn() {
        val openingStart = resolveHorizontalCardChromeMotionFrame(
            useCardContainerSharedBounds = true,
            isSharedMorphSourceCard = true,
            transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
            transitionBackgroundProgress = 0f,
        )
        assertEquals(1f, openingStart.alpha, 0.001f)
        assertEquals(0f, openingStart.translationProgress, 0.001f)

        val openingMid = resolveHorizontalCardChromeMotionFrame(
            useCardContainerSharedBounds = true,
            isSharedMorphSourceCard = true,
            transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
            transitionBackgroundProgress = 0.14f,
        )
        assertEquals(0.5f, openingMid.alpha, 0.001f)
        assertEquals(0.5f, openingMid.translationProgress, 0.001f)

        val openingFinished = resolveHorizontalCardChromeMotionFrame(
            useCardContainerSharedBounds = true,
            isSharedMorphSourceCard = true,
            transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.OPENING,
            transitionBackgroundProgress = 0.28f,
        )
        assertEquals(0f, openingFinished.alpha, 0.001f)
        assertEquals(1f, openingFinished.translationProgress, 0.001f)

        val returnReveal = resolveHorizontalCardChromeMotionFrame(
            useCardContainerSharedBounds = true,
            isSharedMorphSourceCard = true,
            isReturningFromDetail = true,
            transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
            transitionBackgroundProgress = 0.19f,
        )
        assertEquals(0f, returnReveal.alpha, 0.001f)
        assertEquals(0f, returnReveal.translationProgress, 0.001f)

        val landed = resolveHorizontalCardChromeMotionFrame(
            useCardContainerSharedBounds = true,
            isSharedMorphSourceCard = true,
            isReturningFromDetail = true,
            transitionBackgroundPhase = VideoCardTransitionBackgroundPhase.IDLE,
            transitionBackgroundProgress = 0f,
        )
        assertEquals(1f, landed.alpha, 0.001f)
        assertEquals(0f, landed.translationProgress, 0.001f)
    }
}
