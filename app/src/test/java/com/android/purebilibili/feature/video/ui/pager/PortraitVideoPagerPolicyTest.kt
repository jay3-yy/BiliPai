package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.ViewInfo
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortraitVideoPagerPolicyTest {

    @Test
    fun resolvePortraitInitialPageIndex_returnsFirstPageWhenInitialMatchesInfo() {
        val index = resolvePortraitInitialPageIndex(
            initialBvid = "BV1",
            initialInfoBvid = "BV1",
            recommendations = listOf(RelatedVideo(bvid = "BV2"))
        )

        assertEquals(0, index)
    }

    @Test
    fun resolvePortraitInitialPageIndex_pointsToRecommendationWhenMatched() {
        val index = resolvePortraitInitialPageIndex(
            initialBvid = "BV3",
            initialInfoBvid = "BV1",
            recommendations = listOf(
                RelatedVideo(bvid = "BV2"),
                RelatedVideo(bvid = "BV3"),
                RelatedVideo(bvid = "BV4")
            )
        )

        assertEquals(2, index)
    }

    @Test
    fun resolvePortraitInitialPageIndex_fallsBackToFirstPageWhenNotFound() {
        val index = resolvePortraitInitialPageIndex(
            initialBvid = "BV9",
            initialInfoBvid = "BV1",
            recommendations = listOf(RelatedVideo(bvid = "BV2"))
        )

        assertEquals(0, index)
    }

    @Test
    fun resolvePortraitPagerRepeatMode_defaultsToOffForOrderedPlayback() {
        assertEquals(Player.REPEAT_MODE_OFF, resolvePortraitPagerRepeatMode())
    }

    @Test
    fun sharedPlayerSurfaceRebindPolicy_requiresCurrentReadyPageWithVideoFrame() {
        assertTrue(
            shouldRebindSharedPlayerSurfaceOnAttach(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasPlayerView = true,
                videoWidth = 720,
                videoHeight = 1280
            )
        )
    }

    @Test
    fun sharedPlayerSurfaceRebindPolicy_allowsCurrentPageRebindBeforeVideoSizeAvailable() {
        assertTrue(
            shouldRebindSharedPlayerSurfaceOnAttach(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasPlayerView = true,
                videoWidth = 0,
                videoHeight = 1280
            )
        )
    }

    @Test
    fun sharedPlayerSurfaceRebindPolicy_skipsWhenPageIsNotReady() {
        assertFalse(
            shouldRebindSharedPlayerSurfaceOnAttach(
                isCurrentPage = false,
                isPlayerReadyForThisVideo = true,
                hasPlayerView = true,
                videoWidth = 720,
                videoHeight = 1280
            )
        )
    }

    @Test
    fun initialAspectRatio_resetsToPortraitFallbackWhenTargetVideoNotReady() {
        assertEquals(
            9f / 16f,
            resolvePortraitInitialVideoAspectRatio(
                itemBvid = "BV_NEXT",
                currentPlayingBvid = "BV_PREV",
                playerVideoWidth = 1920,
                playerVideoHeight = 1080
            )
        )
    }

    @Test
    fun initialAspectRatio_usesPlayerVideoSizeWhenTargetVideoAlreadyReady() {
        assertEquals(
            9f / 16f,
            resolvePortraitInitialVideoAspectRatio(
                itemBvid = "BV_CURRENT",
                currentPlayingBvid = "BV_CURRENT",
                playerVideoWidth = 720,
                playerVideoHeight = 1280
            )
        )
    }

    @Test
    fun runtimeAspectRatio_keepsKnownPortraitAspectWhenPlayerReportsRotatedLandscapeSize() {
        assertEquals(
            9f / 16f,
            resolvePortraitRuntimeVideoAspectRatio(
                knownVideoAspectRatio = 9f / 16f,
                playerVideoWidth = 1920,
                playerVideoHeight = 1080
            )
        )
    }

    @Test
    fun runtimeAspectRatio_usesPlayerAspectWhenOrientationMatchesKnownVideo() {
        assertEquals(
            720f / 1280f,
            resolvePortraitRuntimeVideoAspectRatio(
                knownVideoAspectRatio = 9f / 16f,
                playerVideoWidth = 720,
                playerVideoHeight = 1280
            )
        )
    }

    @Test
    fun portraitViewportSize_canFillContainerForImmersivePlayback() {
        val layout = resolvePortraitVideoViewportSize(
            containerWidth = 1200,
            containerHeight = 800,
            currentVideoAspect = 16f / 9f,
            fillContainer = true
        )

        assertEquals(1200, layout.width)
        assertEquals(800, layout.height)
    }

    @Test
    fun portraitViewportSize_fitModeKeepsAspectInsideContainer() {
        val layout = resolvePortraitVideoViewportSize(
            containerWidth = 1200,
            containerHeight = 800,
            currentVideoAspect = 16f / 9f,
            fillContainer = false
        )

        assertEquals(1200, layout.width)
        assertEquals(675, layout.height)
    }

    @Test
    fun portraitPager_defaultViewportPolicy_doesNotForceFillContainer() {
        assertFalse(resolvePortraitPagerFillContainer())
        assertFalse(
            resolvePortraitPagerFillContainer(
                aspectRatio = VideoAspectRatio.FIT,
                isVerticalContent = true
            )
        )
        assertTrue(
            resolvePortraitPagerFillContainer(
                aspectRatio = VideoAspectRatio.FILL,
                isVerticalContent = true
            )
        )
        // Stretch is coerced to Fit for vertical content, so do not fill-deform.
        assertFalse(
            resolvePortraitPagerFillContainer(
                aspectRatio = VideoAspectRatio.STRETCH,
                isVerticalContent = true
            )
        )
    }

    @Test
    fun portraitPager_defaultResizeMode_prefersFit() {
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            resolvePortraitPagerResizeMode()
        )
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            resolvePortraitPagerResizeMode(
                aspectRatio = VideoAspectRatio.FILL,
                isVerticalContent = true
            )
        )
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            resolvePortraitPagerResizeMode(
                aspectRatio = VideoAspectRatio.STRETCH,
                isVerticalContent = true
            )
        )
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            resolvePortraitPagerResizeMode(
                aspectRatio = VideoAspectRatio.RATIO_16_9,
                isVerticalContent = true
            )
        )
    }

    @Test
    fun portraitPager_fixedRatioUsesTargetViewportAspect() {
        assertEquals(
            16f / 9f,
            resolvePortraitPagerViewportAspect(
                aspectRatio = VideoAspectRatio.RATIO_16_9,
                currentVideoAspect = 9f / 16f,
                isVerticalContent = true
            )
        )
        assertEquals(
            4f / 3f,
            resolvePortraitPagerViewportAspect(
                aspectRatio = VideoAspectRatio.RATIO_4_3,
                currentVideoAspect = 9f / 16f,
                isVerticalContent = true
            )
        )
        assertEquals(
            9f / 16f,
            resolvePortraitPagerViewportAspect(
                aspectRatio = VideoAspectRatio.FIT,
                currentVideoAspect = 9f / 16f,
                isVerticalContent = true
            )
        )
    }

    @Test
    fun portraitOverlayControlsVisible_preservesHiddenModeUnlessDetailSheetIsOpen() {
        assertFalse(
            resolvePortraitOverlayControlsVisible(
                portraitOverlayVisible = false,
                showDetailSheet = false
            )
        )
        assertFalse(
            resolvePortraitOverlayControlsVisible(
                portraitOverlayVisible = true,
                showDetailSheet = true
            )
        )
        assertTrue(
            resolvePortraitOverlayControlsVisible(
                portraitOverlayVisible = true,
                showDetailSheet = false
            )
        )
    }

    @Test
    fun portraitOverlayTap_togglesFromCurrentVisibility() {
        assertFalse(resolvePortraitOverlayVisibilityAfterTap(currentlyVisible = true))
        assertTrue(resolvePortraitOverlayVisibilityAfterTap(currentlyVisible = false))
    }

    @Test
    fun portraitPlaybackAllowed_onlyWhenStoryTabVisibleAndLifecycleResumed() {
        assertTrue(
            shouldAllowPortraitPlayback(
                isCurrentStoryTab = true,
                isLifecycleResumed = true
            )
        )
        assertFalse(
            shouldAllowPortraitPlayback(
                isCurrentStoryTab = false,
                isLifecycleResumed = true
            )
        )
        assertFalse(
            shouldAllowPortraitPlayback(
                isCurrentStoryTab = true,
                isLifecycleResumed = false
            )
        )
    }

    @Test
    fun portraitPlaybackAllowed_inBackgroundWhenBackgroundAudioContinues() {
        // 「后台播放」开启且策略允许继续播放时，ON_STOP 不应触发暂停
        assertTrue(
            shouldAllowPortraitPlayback(
                isCurrentStoryTab = true,
                isLifecycleResumed = false,
                continueInBackgroundAudio = true
            )
        )
        // 离开当前 tab（滑动到别的视频）时即使后台播放开启也不继续
        assertFalse(
            shouldAllowPortraitPlayback(
                isCurrentStoryTab = false,
                isLifecycleResumed = false,
                continueInBackgroundAudio = true
            )
        )
    }

    @Test
    fun portraitDanmakuSurface_usesVideoViewportSoDisplayAreaMatchesVideoHeight() {
        assertEquals(
            PortraitDanmakuSurfaceMode.VideoViewport,
            resolvePortraitDanmakuSurfaceMode(
                currentVideoAspect = 16f / 9f,
                displayAreaMode = PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT
            )
        )
    }

    @Test
    fun portraitDanmakuSurface_usesPageTopWhenDisplayAreaModeRequestsScreenTop() {
        assertEquals(
            PortraitDanmakuSurfaceMode.Page,
            resolvePortraitDanmakuSurfaceMode(
                currentVideoAspect = 9f / 16f,
                displayAreaMode = PortraitDanmakuDisplayAreaMode.SCREEN_TOP
            )
        )
    }

    @Test
    fun portraitDanmakuLoad_waitsForSettingsBeforeLoading() {
        assertFalse(
            shouldLoadPortraitDanmaku(
                settingsLoaded = false,
                cid = 123L,
                danmakuEnabled = true
            )
        )
        assertTrue(
            shouldLoadPortraitDanmaku(
                settingsLoaded = true,
                cid = 123L,
                danmakuEnabled = true
            )
        )
        assertFalse(
            shouldLoadPortraitDanmaku(
                settingsLoaded = true,
                cid = 0L,
                danmakuEnabled = true
            )
        )
        assertFalse(
            shouldLoadPortraitDanmaku(
                settingsLoaded = true,
                cid = 123L,
                danmakuEnabled = false
            )
        )
    }

    @Test
    fun pageDanmakuSurface_isInsetFromStatusBar() {
        assertTrue(
            shouldInsetPortraitDanmakuFromStatusBar(PortraitDanmakuSurfaceMode.Page)
        )
        assertFalse(
            shouldInsetPortraitDanmakuFromStatusBar(PortraitDanmakuSurfaceMode.VideoViewport)
        )
    }

    @Test
    fun portraitDanmakuTopInset_keepsHiddenBarsAndCutoutSafeArea() {
        assertEquals(
            84,
            resolvePortraitDanmakuTopInsetPx(
                visibleStatusBarTopPx = 0,
                statusBarsIgnoringVisibilityTopPx = 48,
                displayCutoutTopPx = 84,
            )
        )
        assertEquals(
            48,
            resolvePortraitDanmakuTopInsetPx(
                visibleStatusBarTopPx = 48,
                statusBarsIgnoringVisibilityTopPx = 0,
                displayCutoutTopPx = 0,
            )
        )
    }



    @Test
    fun sharedPlayerEntry_reusesExistingFrameWhenSharedPlayerAlreadyHasVideoSize() {
        assertEquals(
            0,
            resolvePortraitInitialRenderedFirstFrameGeneration(
                useSharedPlayer = true,
                sharedPlayerHasFrameAtEntry = true
            )
        )
    }

    @Test
    fun portraitInteraction_staysEnabledForCurrentPageEvenWhenSharedModelStillPointsToPreviousVideo() {
        assertTrue(
            shouldHandlePortraitVideoInteraction(
                isCurrentPage = true,
                aid = 2002L,
                bvid = "BV_NEXT"
            )
        )
    }

    @Test
    fun portraitActiveAid_prefersLoadedVideoIdentityWhenPlayerIsReady() {
        assertEquals(
            2002L,
            resolvePortraitActiveAid(
                isPlayerReadyForThisVideo = true,
                itemAid = 1001L,
                currentPlayingAid = 2002L
            )
        )
    }

    @Test
    fun portraitActiveAid_fallsBackToItemIdentityOnlyBeforePlayerTargetsPage() {
        assertEquals(
            1001L,
            resolvePortraitActiveAid(
                isPlayerReadyForThisVideo = false,
                itemAid = 1001L,
                currentPlayingAid = 2002L
            )
        )
        assertEquals(
            0L,
            resolvePortraitActiveAid(
                isPlayerReadyForThisVideo = true,
                itemAid = 1001L,
                currentPlayingAid = 0L
            )
        )
    }

    @Test
    fun portraitFavoriteTap_withoutQuickSave_opensFavoriteFolders() {
        assertEquals(
            PortraitFavoriteAction.OpenFavoriteFolders,
            resolvePortraitFavoriteAction(
                isLongPress = false,
                quickSaveDefaultFolder = false,
            )
        )
    }


    @Test
    fun portraitRecommendationSnapshot_extractsStableBvidSetFromMixedPageItems() {
        assertEquals(
            setOf("BV_INFO", "BV_RELATED"),
            snapshotPortraitPageBvids(
                listOf(
                    ViewInfo(bvid = "BV_INFO", aid = 1L),
                    RelatedVideo(bvid = "BV_RELATED", aid = 2L),
                    "ignored"
                )
            )
        )
    }

    @Test
    fun portraitCommentExpansion_usesSharedProgressInsteadOfSecondaryScaleTween() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt").readText()

        assertFalse(source.contains("portrait_comment_player_scale"))
        assertFalse(source.contains("commentExpandedPlayerScale by animateFloatAsState"))
        assertTrue(source.contains("resolvePortraitCommentPlayerTransform("))
        assertTrue(source.contains("commentExpansionTransform.scale"))
    }

    @Test
    fun portraitOverlay_receivesCommentExpansionProgress() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt").readText()

        assertTrue(source.contains("commentExpansionProgress = commentSheetVisibilityProgress"))
    }

    @Test
    fun portraitOverlayVisibility_isHoistedAboveIndividualPageItems() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt").readText()

        assertTrue(source.contains("var portraitOverlayVisible by rememberSaveable(initialInfo.bvid)"))
        assertTrue(source.contains("portraitOverlayVisible = portraitOverlayVisible"))
        assertTrue(source.contains("onPortraitOverlayVisibleChange = { visible ->"))
        assertFalse(source.contains("var isOverlayVisible by remember"))
    }

    @Test
    fun portraitVideoSwitch_cancelsPendingDanmakuLoadBeforeNewMediaLoads() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt").readText()

        assertTrue(source.contains("danmakuManager.clearForVideoChange()"))
        assertFalse(source.contains("danmakuManager.clear()\n                isLoading = true"))
    }

    @Test
    fun portraitPager_isolatesDanmakuAndCommentStateFromUnderlyingDetailPage() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt").readText()
        val pagerSignature = source
            .substringAfter("fun PortraitVideoPager(")
            .substringBefore(") {")

        assertTrue(source.contains("rememberIsolatedDanmakuManager("))
        assertTrue(source.contains("key = \"portrait_comments_\$initialBvid\""))
        assertTrue(source.contains("commentViewModel.clearForVideoChange()"))
        assertTrue(pagerSignature.contains("viewModel: VideoPlaybackViewModel,"))
        assertTrue(pagerSignature.contains("engagementViewModel: VideoEngagementViewModel,"))
        assertTrue(pagerSignature.contains("sharedPlayer: ExoPlayer?"))
        assertTrue(pagerSignature.contains("useTextureSurfaceForNavigation: Boolean = false"))
        assertFalse(pagerSignature.contains("commentViewModel: VideoCommentViewModel"))
    }

    @Test
    fun letterboxAmbientCapture_requiresCurrentReadyPageAndSettings() {
        assertTrue(
            shouldCapturePortraitLetterboxAmbientFrame(
                isCurrentPage = true,
                letterboxAmbientHazeEnabled = true,
                letterboxBarHeightPx = 40,
                isPlayerReadyForThisVideo = true,
            )
        )
        assertFalse(
            shouldCapturePortraitLetterboxAmbientFrame(
                isCurrentPage = false,
                letterboxAmbientHazeEnabled = true,
                letterboxBarHeightPx = 40,
                isPlayerReadyForThisVideo = true,
            )
        )
        assertFalse(
            shouldCapturePortraitLetterboxAmbientFrame(
                isCurrentPage = true,
                letterboxAmbientHazeEnabled = false,
                letterboxBarHeightPx = 40,
                isPlayerReadyForThisVideo = true,
            )
        )
        assertFalse(
            shouldCapturePortraitLetterboxAmbientFrame(
                isCurrentPage = true,
                letterboxAmbientHazeEnabled = true,
                letterboxBarHeightPx = 0,
                isPlayerReadyForThisVideo = true,
            )
        )
        assertFalse(
            shouldCapturePortraitLetterboxAmbientFrame(
                isCurrentPage = true,
                letterboxAmbientHazeEnabled = true,
                letterboxBarHeightPx = 40,
                isPlayerReadyForThisVideo = false,
            )
        )
    }

    @Test
    fun portraitDanmakuOverlay_composesOnlyForActiveMatchingSurface() {
        assertTrue(
            shouldComposePortraitDanmakuOverlay(
                danmakuEnabled = true,
                surfaceMode = PortraitDanmakuSurfaceMode.Page,
                expectedMode = PortraitDanmakuSurfaceMode.Page,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
            )
        )
        assertFalse(
            shouldComposePortraitDanmakuOverlay(
                danmakuEnabled = true,
                surfaceMode = PortraitDanmakuSurfaceMode.VideoViewport,
                expectedMode = PortraitDanmakuSurfaceMode.Page,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
            )
        )
        assertFalse(
            shouldComposePortraitDanmakuOverlay(
                danmakuEnabled = false,
                surfaceMode = PortraitDanmakuSurfaceMode.Page,
                expectedMode = PortraitDanmakuSurfaceMode.Page,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
            )
        )
        assertFalse(
            shouldComposePortraitDanmakuOverlay(
                danmakuEnabled = true,
                surfaceMode = PortraitDanmakuSurfaceMode.Page,
                expectedMode = PortraitDanmakuSurfaceMode.Page,
                isCurrentPage = false,
                isPlayerReadyForThisVideo = true,
            )
        )
    }

    @Test
    fun portraitPager_sourcesUseThrottledProgressAndLetterboxPolicies() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt"
        ).readText()

        assertTrue(source.contains("shouldCommitPortraitProgressToDetailState("))
        assertTrue(source.contains("shouldCapturePortraitLetterboxAmbientFrame("))
        assertTrue(source.contains("shouldComposePortraitDanmakuOverlay("))
        assertFalse(source.contains("if (false && danmakuEnabled"))
        assertFalse(source.contains("val shouldCaptureLetterboxAmbient = false &&"))
    }
}
