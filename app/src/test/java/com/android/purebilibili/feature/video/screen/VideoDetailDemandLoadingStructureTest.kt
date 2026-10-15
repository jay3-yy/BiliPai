package com.android.purebilibili.feature.video.screen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailDemandLoadingStructureTest {
    @Test
    fun networkRequestEffectDoesNotWaitForEntryAnimation() {
        val source = source("state/VideoPlayerState.kt")
        val marker = "    LaunchedEffect(\n        player,\n        bvid,\n        cid,\n        reuseFromMiniPlayerAtEntry,"
        assertTrue(source.contains(marker))
        val effect = source.substringAfter(marker).substringBefore("    return holder")
        val keys = effect.substringBefore(") {")
        assertFalse(keys.contains("entryTransitionFinished"))
        assertFalse(effect.contains("if (!entryTransitionFinished)"))
        assertTrue(effect.contains("viewModel.loadVideo("))
    }

    @Test
    fun playbackPreparationStillWaitsForPlayerAttachmentAndEntryGate() {
        val source = source("state/VideoPlayerState.kt")
        val gate = source.substringAfter("DisposableEffect(viewModel, player, entryTransitionFinished, playbackSessionActive)")
            .substringBefore("    LaunchedEffect(")
        assertTrue(gate.indexOf("viewModel.attachPlayer(player)") < gate.indexOf("viewModel.setPlaybackPreparationAllowed"))
        assertTrue(source("viewmodel/VideoPlaybackViewModel.kt").contains("playbackPreparationAllowed.first { it }"))
    }

    @Test
    fun relatedRequestStartsBeforeThePlaybackLoader() {
        val source = source("viewmodel/VideoPlaybackViewModel.kt").substringAfter("    fun loadVideo(")
            .substringBefore("    private fun scheduleHdrAutoUpgradeIfNeeded(")
        assertTrue(source.indexOf("loadRelatedVideosForRequest(playbackRequest.bvid, requestToken)") < source.indexOf("playbackLoader.load("))
        assertTrue(source.indexOf("val videoInfoDeferred = async") < source.indexOf("refreshVipStatusForPreferredQualityIfNeeded("))
    }

    @Test
    fun phoneAndTabletKeepPlaybackStateSeparateFromMetadata() {
        val screen = source("screen/VideoDetailScreenStateHolder.kt")
        assertTrue(screen.contains("detailState.contentSnapshot(uiState, currentBvid, playbackTargetCid)"))
        assertTrue(screen.contains("detailContentState != null ->"))
        assertTrue(screen.contains("detailContentState = detailContentState"))
        assertTrue(source("screen/TabletVideoLayout.kt").contains("val contentState = detailContentState"))
        assertTrue(source("screen/LargeScreenVideoLayout.kt").contains("val success = detailContentState"))
    }

    @Test
    fun theDetailPathDoesNotEnableSpeculativeRelatedPlaybackRequests() {
        val source = source("viewmodel/VideoPlaybackViewModel.kt")
        val policy = source.substringAfter("internal fun resolveRelatedPlayUrlPreloadCount(")
            .substringBefore("internal fun resolvePlaybackIntentForSourceReplacement(")
        assertFalse(policy.contains("return 1"))
        assertFalse(policy.contains("return 2"))
        assertTrue(policy.contains("return 0"))
    }

    @Test
    fun keepingMetadataVisibleDoesNotRemoveThePlaybackRetryEntry() {
        val player = source("screen/VideoDetailPlayerTransitionHost.kt")
        assertTrue(player.contains("uiState is VideoPlaybackUiState.Error"))
        assertTrue(player.contains("VideoDetailPlaybackErrorNotice("))
        assertTrue(player.contains("onRetry = playbackActions.retryPlayback"))
        assertTrue(source("screen/VideoDetailScreenStateHolder.kt").contains("retryPlayback = viewModel::retry"))
        assertTrue(source("screen/VideoDetailScreenStateHolder.kt").contains("showPlaybackErrorNotice = detailContentState != null"))
    }

    @Test
    fun onlyRelatedNetworkAttemptsWaitForTheWeakNetworkPermit() {
        val source = source("viewmodel/VideoPlaybackViewModel.kt")
        val related = source.substringAfter("    private fun loadRelatedVideosForRequest(")
            .substringBefore("    private fun scheduleDeferredPostLoadWork(")
        assertTrue(related.indexOf("relatedLoadScheduler.awaitPermit(") < related.indexOf("VideoRepository.getRelatedVideosResult(bvid)"))
        assertTrue(related.indexOf("repeat(2)") < related.indexOf("relatedLoadScheduler.awaitPermit("))
        val initialLoad = source.substringAfter("    fun loadVideo(")
            .substringBefore("    private fun scheduleHdrAutoUpgradeIfNeeded(")
        assertFalse(initialLoad.contains("awaitPermit("))
        assertTrue(initialLoad.indexOf("VideoPlaybackUiState.Loading.Initial") <
            initialLoad.indexOf("loadRelatedVideosForRequest(playbackRequest.bvid, requestToken)"))
        assertTrue(initialLoad.indexOf("val videoInfoDeferred = async") <
            initialLoad.indexOf("loadRelatedVideosForRequest(playbackRequest.bvid, requestToken)"))
        assertTrue(initialLoad.contains("relatedPlaybackPreparedToken = -1L"))
        assertTrue(initialLoad.contains("relatedFirstFrameToken = -1L"))
    }

    @Test
    fun bandwidthObservationIsPassiveAndRemovedWithPlayerOwnership() {
        val source = source("viewmodel/VideoPlaybackViewModel.kt")
        assertTrue(source.contains("override fun onBandwidthEstimate("))
        assertTrue(source.contains("previousPlayer.removeAnalyticsListener(videoBandwidthListener)"))
        assertTrue(source.contains("player.removeAnalyticsListener(videoBandwidthListener)"))
        assertTrue(source.contains("exoPlayer?.removeAnalyticsListener(videoBandwidthListener)"))
        val observer = source("viewmodel/VideoNetworkPressureObserver.kt")
        assertFalse(observer.contains("getRelatedVideos"))
        assertFalse(observer.contains("registerNetworkCallback"))
        assertFalse(observer.contains("TRANSPORT_CELLULAR"))
    }

    @Test
    fun returningToLoadedDetailsDoesNotReuseTheChildPlayersReadySignal() {
        val source = source("viewmodel/VideoPlaybackViewModel.kt")
        val restore = source.substringAfter("    fun restoreFromCache(")
            .substringBefore("    private fun publishSubjectSnapshot(")
        assertTrue(restore.indexOf("relatedPlaybackPreparedToken = -1L") <
            restore.indexOf("loadRelatedVideosForRequest("))
        assertTrue(restore.indexOf("relatedFirstFrameToken = -1L") <
            restore.indexOf("loadRelatedVideosForRequest("))
        val keepLoaded = source.substringAfter("        if (keepLoadedUi) {")
            .substringBefore("        if (!playbackRequest.force && isPlayerPlayingSameVideo && isUiLoaded)")
        assertTrue(keepLoaded.contains("relatedPlaybackPreparedToken = currentLoadRequestToken"))
        assertTrue(keepLoaded.contains("if (playbackStallRecoveryFirstFrameRendered || player.isPlaying)"))
    }

    @Test
    fun commentInitializationWaitsForEntryButShimmerBypassesDisabledTransitions() {
        val screen = source("screen/VideoDetailScreenStateHolder.kt")
        val commentEffect = screen.substringAfter(
            "LaunchedEffect(detailContentState?.info?.aid, preferredCommentSortMode, isVisible, entryTransitionFinished)"
        ).substringBefore("    //  核心修改")
        assertTrue(commentEffect.contains("if (!isVisible || !entryTransitionFinished) return@LaunchedEffect"))
        assertTrue(commentEffect.contains("commentViewModel.init("))
        assertTrue(screen.contains("animated = !transitionEnabled || entryTransitionFinished"))
    }

    @Test
    fun navigationGateDoesNotTreatTimeoutAsCompletionWhileMotionIsActive() {
        val observer = source("screen/VideoDetailEntryTransitionObserver.kt")
        val navigationGate = observer.substringAfter("if (navigationTransitionScope != null)")
            .substringBefore("if (heroDriver != null)")
        assertTrue(navigationGate.contains("navigationTransitionScope.isRunning"))
        assertTrue(navigationGate.contains("navigationTransitionScope.role != NavRole.Top"))
        assertTrue(navigationGate.indexOf("if (navigationTransitionScope.isRunning)") <
            navigationGate.indexOf("heroDriver.progressProvider()"))
        assertTrue(observer.contains("var finished by remember { mutableStateOf(!deferLoad) }"))
        assertFalse(observer.contains("remember(deferLoad)"))
        assertFalse(navigationGate.contains("delay("))
        val timeout = observer.substringAfter("val timeoutJob = launch")
            .substringBefore("        try {")
        assertFalse(timeout.contains("finished = true"))
    }

    private fun source(path: String): String = listOf(
        File("app/src/main/java/com/android/purebilibili/feature/video/$path"),
        File("src/main/java/com/android/purebilibili/feature/video/$path")
    ).first { it.exists() }.readText().replace("\r\n", "\n")
}
