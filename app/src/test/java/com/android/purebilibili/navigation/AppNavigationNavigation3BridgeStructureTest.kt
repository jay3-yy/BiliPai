package com.android.purebilibili.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationNavigation3BridgeStructureTest {

    @Test
    fun appNavigationMirrorsLegacyBackStackIntoNavigation3Keys() {
        val source = appNavigationSource()

        assertTrue(source.contains("navigation3BackStack"))
        assertTrue(source.contains("pushBiliPaiNavKey"))
        assertTrue(source.contains("popBiliPaiNavKey"))
        assertTrue(source.contains("legacyRouteToBiliPaiNavKey"))
    }

    @Test
    fun videoReturnRouteLayerUsesNavigation3MotionDecision() {
        val source = navigation3Source()

        assertTrue(source.contains("resolveBiliPaiNavMotionDecision"))
        assertTrue(source.contains("BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT"))
        assertTrue(source.contains("resolveBiliPaiBackGestureDecision"))
    }

    @Test
    fun appNavigationMirrorsReturnStateIntoNavigation3SessionBeforeLegacyFallback() {
        val source = appNavigationSource()

        assertTrue(source.contains("BiliPaiReturnSessionState"))
        assertTrue(source.contains("navigation3ReturnSession"))
        assertTrue(source.contains("resolveBiliPaiNavSourceMetadata"))
        assertTrue(source.contains("captureVideoCardTransitionSession("))
        assertTrue(source.contains("navigation3ReturnSession.transitionSession"))
        assertTrue(source.contains("?.hasUsableSourceGeometry"))
        assertFalse(source.contains("cardKey = CardPositionManager.lastClickedVideoSourceKey"))
        assertFalse(source.contains("sessionKey = navigation3ReturnSession.lastVideoSourceKey"))
        assertTrue(source.contains("matchedVisibleCardRoute"))
        assertTrue(source.contains("navigation3ReturnSession.markReturning"))
        assertTrue(source.contains("navigation3ReturnSession.isQuickReturnFromDetail"))
        assertFalse(source.contains("isQuickReturnFromDetail = CardPositionManager.isQuickReturnFromDetail"))
    }

    @Test
    fun sharedElementDisabledStillMarksVideoReturnState() {
        val source = appNavigationSource()
        val backMarker = source
            .substringAfter("fun markNavigation3VideoReturnBeforeBackAction")
            .substringBefore("val performSystemBackAction")
        val videoDetailBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->")

        assertFalse(backMarker.contains("if (!cardTransitionEnabled) return"))
        assertTrue(videoDetailBranch.contains("onMarkReturningFromDetail = {"))
        assertFalse(videoDetailBranch.contains("onMarkReturningFromDetail = {\n                                    if (cardTransitionEnabled)"))
        assertFalse(videoDetailBranch.contains("onBack = {\n                                    if (cardTransitionEnabled)"))
        assertFalse(videoDetailBranch.contains("onHomeClick = {\n                                    if (cardTransitionEnabled)"))
    }

    @Test
    fun videoDetailTopBackUsesNavDisplayProgrammaticBackCoordinator() {
        val source = appNavigationSource()
        val videoDetailBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->")
        val onBackBlock = videoDetailBranch
            .substringAfter("onBack = {")
            .substringBefore("onHomeClick = {")

        assertTrue(
            onBackBlock.contains("navigation3ProgrammaticBackDispatcher.dispatch()")
        )
        assertTrue(onBackBlock.contains("performSystemBackAction()"))
        assertFalse(onBackBlock.contains("popVideoDetailWithSharedReturnState("))
        assertTrue(
            source.contains(
                "programmaticBackDispatcher = navigation3ProgrammaticBackDispatcher"
            )
        )
    }

    @Test
    fun videoDetailHomeClickUsesPopToRootSoHorizontalReturnTransitionPlays() {
        val source = appNavigationSource()
        val videoDetailBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->")
        val onHomeClickBlock = videoDetailBranch
            .substringAfter("onHomeClick = {")
            .substringBefore("onNavigateToAudioMode")

        // 必须走 pop 路径才能触发 popTransitionSpec → 方向化横向过渡。
        assertTrue(onHomeClickBlock.contains("popBiliPaiNavKeyToRoot(navigation3BackStack)"))
        assertTrue(onHomeClickBlock.contains("mainBottomPagerState.switchToPage"))
        // 不再用 push 把 Home 叠到栈顶；避免 fade 兜底以及栈泄漏。
        assertFalse(onHomeClickBlock.contains("pushNavigation3Route(ScreenRoutes.Home.route)"))
    }

    @Test
    fun videoDetailRelatedVideoClickUsesParentDetailRouteForSharedElementPairing() {
        val source = appNavigationSource()
        val videoDetailBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->")
            .substringBefore("onBgmClick = { bgm ->")
        val onVideoClickBlock = videoDetailBranch
            .substringAfter("onVideoClick = { vid, options ->")

        assertTrue(onVideoClickBlock.contains("sourceRoute = \"video/${'$'}{videoKey.bvid}\""))
    }

    @Test
    fun portraitRedirectsResolveBeforeReservingNavigationDebounce() {
        val videoNavigation = appNavigationSource()
            .substringAfter("fun navigateToVideoRouteInNavigation3(")
            .substringBefore("fun navigateToVideoInNavigation3(")
        val debounceIndex = videoNavigation.indexOf("if (!canNavigate(false)) return")
        assertTrue(debounceIndex > videoNavigation.indexOf("navigateToPortraitStoryInNavigation3(seed"))
        assertTrue(debounceIndex > videoNavigation.indexOf("VideoRepository.isVerticalVideo(videoKey.bvid)"))
        assertTrue(debounceIndex < videoNavigation.indexOf("pushNavigation3Key(key)"))
        assertTrue(videoNavigation.contains("route = videoKey.copy("))
    }

    @Test
    fun videoCardOpeningIsPrearmedBeforeTheDetailDestinationMounts() {
        val source = appNavigationSource()
        val prearmHelper = source
            .substringAfter("fun prearmVideoCardOpening(")
            .substringBefore("var lastVideoDetailOpenId")
        val videoNavigation = source
            .substringAfter("fun navigateToVideoRouteInNavigation3(")
            .substringBefore("fun navigateToVideoInNavigation3(")

        assertTrue(prearmHelper.contains("beginOpeningIfNeeded(session.sourceRoute)"))
        assertTrue(videoNavigation.contains("prearmVideoCardOpening(transitionSession)"))
        assertTrue(
            videoNavigation.indexOf("prearmVideoCardOpening(transitionSession)") <
                videoNavigation.indexOf("pushNavigation3Key(key)")
        )
    }

    @Test
    fun immediateVideoDetailBackTargetKeepsLoadedPreviewContent() {
        val videoDetailBranch = appNavigationSource()
            .substringAfter("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->")

        assertTrue(videoDetailBranch.contains("keepLoadedContentForBackPreview ="))
        assertTrue(
            videoDetailBranch.contains(
                "navigation3BackStack.getOrNull(navigation3BackStack.lastIndex - 1) == videoKey"
            )
        )
    }

    @Test
    fun videoBackCancellationRestoresOnlyTheCurrentVideoPlayerSurface() {
        val source = appNavigationSource()
        val navHostCall = source
            .substringAfter("BiliPaiNavDisplayHost(")
            .substringBefore(") { key ->")

        assertTrue(navHostCall.contains("onNativeVideoBackCancelled ="))
        assertTrue(source.contains("predictiveBackCancelRecoveryGeneration ="))
        assertTrue(source.contains("shouldBindVideoDetailBackPreviewPlayer("))
    }

    @Test
    fun videoBackPreviewActivatesTargetPlaybackOnlyThroughTheExplicitBindPolicy() {
        val videoDetailBranch = appNavigationSource()
            .substringAfter("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->")

        assertTrue(videoDetailBranch.contains("val videoDetailPlaybackSessionActive ="))
        assertTrue(videoDetailBranch.contains("isVisible = videoDetailPlaybackSessionActive &&"))
        assertTrue(videoDetailBranch.contains("isPlaybackSessionActive = videoDetailPlaybackSessionActive"))
        assertTrue(videoDetailBranch.contains("bindLivePlayerForBackPreview = bindVideoBackPreviewPlayer"))
        assertTrue(videoDetailBranch.contains("activateBackPreviewPlayback ="))
        assertTrue(videoDetailBranch.contains("activateVideoBackPreviewPlayback"))
        assertTrue(videoDetailBranch.contains("navigation3ReturnSession.isReturningFromDetail"))
        assertFalse(videoDetailBranch.contains("isVisible = true"))
    }

    @Test
    fun homeVideoUsesComposeCardShellContainerTransformWithoutNativeBackgroundPreview() {
        val source = appNavigationSource()
        val videoDetailBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->")
        val homeVideoClickBlock = source
            .substringAfter("fun navigateToHomeVideoInNavigation3(request: HomeVideoClickRequest)")
            .substringBefore("fun navigateToFavoriteVideoInNavigation3")
        val navHostCall = source
            .substringAfter("BiliPaiNavDisplayHost(")
            .substringBefore(") { key ->")

        assertFalse(source.contains("fun shouldUseNativeVideoBackgroundReturnEffect("))
        assertFalse(source.contains("LocalNativeVideoCardTransitionController.current"))
        assertFalse(source.contains("NativeVideoCardTransitionOpenRequest"))
        assertFalse(source.contains("NativeVideoCardTransitionCloseRequest"))
        assertFalse(homeVideoClickBlock.contains("startOpen("))
        assertFalse(source.contains("&&\n                            !intent.isVerticalVideo"))
        assertFalse(videoDetailBranch.contains("!shouldUseNativeVideoCardTransition(videoKey)"))
        assertFalse(videoDetailBranch.contains("!shouldUseNativeVideoBackgroundReturnEffect(videoKey)"))
        assertTrue(videoDetailBranch.contains("transitionEnabled = shouldEnableVideoDetailSharedTransition("))
        assertTrue(videoDetailBranch.contains("shouldEnableVideoDetailSharedTransition("))
        assertTrue(videoDetailBranch.contains("sourceRoute = videoKey.sourceRoute"))
        assertFalse(videoDetailBranch.contains("nativeVideoBackPreviewVideoKey == videoKey.bvid"))
        assertFalse(videoDetailBranch.contains("hideVideoDetailForNativeBackPreview"))
        assertFalse(source.contains("previewNativeVideoBackProgress("))
        assertFalse(source.contains("nativeVideoBackPreviewVideoKey"))
        assertFalse(navHostCall.contains("onNativeVideoBackProgress = ::previewNativeVideoBackProgress"))
    }

    @Test
    fun inlinePartitionVideoClickKeepsPartitionAsVideoSourceRoute() {
        val source = appNavigationSource()
        val homeBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.HOME -> HomeScreen(")
            .substringBefore("BiliPaiNavEntryContentRole.HISTORY ->")
        val partitionVideoClickBlock = homeBranch
            .substringAfter("onPartitionVideoClick = { video ->")
            .substringBefore("onLiveClick = {")

        assertTrue(partitionVideoClickBlock.contains("sourceRoute = ScreenRoutes.Partition.route"))
    }

    @Test
    fun favoriteVideoClickKeepsFavoriteAsVideoSourceRoute() {
        val source = appNavigationSource()
        val favoriteBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.FAVORITE ->")
            .substringBefore("BiliPaiNavEntryContentRole.LIKED_VIDEOS ->")
        val videoClickBlock = favoriteBranch
            .substringAfter("onVideoClick = { bvid, cid, cover ->")
            .substringBefore("onFavoriteFolderClick =")

        assertTrue(videoClickBlock.contains("sourceRoute = ScreenRoutes.Favorite.route"))
    }


    @Test
    fun seasonSeriesVideosUseMatchingSharedElementSourceRoute() {
        val source = appNavigationSource()
        val detailBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.SEASON_SERIES_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.BANGUMI ->")

        assertTrue(detailBranch.contains("val seasonSeriesSourceRoute ="))
        assertTrue(
            detailBranch.contains(
                "LocalVideoCardSharedElementSourceRoute provides seasonSeriesSourceRoute"
            )
        )
        assertTrue(detailBranch.contains("sourceRoute = seasonSeriesSourceRoute"))
    }

    @Test
    fun dynamicDetailProvidesVideoCardSourceRouteForSharedElementReturn() {
        val source = appNavigationSource()
        val dynamicDetailBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.DYNAMIC_DETAIL ->")
            .substringBefore("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->")

        assertTrue(dynamicDetailBranch.contains("LocalVideoCardSharedElementSourceRoute provides dynamicKey.toLegacyRoute()"))
        assertTrue(dynamicDetailBranch.contains("DynamicDetailScreen("))
    }

    @Test
    fun categoryPassesVideoReturnStateToSuppressCardEnterAnimation() {
        val source = appNavigationSource()
        val categoryBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.CATEGORY ->")
            .substringBefore("BiliPaiNavEntryContentRole.SEASON_SERIES_DETAIL ->")

        assertTrue(categoryBranch.contains("isReturningFromVideoDetail = navigation3ReturnSession.isReturningFromDetail"))
        assertTrue(categoryBranch.contains("isQuickReturningFromVideoDetail ="))
        assertTrue(categoryBranch.contains("navigation3ReturnSession.isQuickReturnFromDetail"))
    }

    @Test
    fun searchPassesVideoReturnStateToSuppressCardEnterAnimation() {
        val source = appNavigationSource()
        val searchBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.SEARCH ->")
            .substringBefore("BiliPaiNavEntryContentRole.SEARCH_TRENDING ->")

        assertTrue(searchBranch.contains("isReturningFromVideoDetail = navigation3ReturnSession.isReturningFromDetail"))
        assertTrue(searchBranch.contains("isQuickReturningFromVideoDetail ="))
        assertTrue(searchBranch.contains("navigation3ReturnSession.isQuickReturnFromDetail"))
        assertTrue(searchBranch.contains("onVideoDetailReturnAnimationConsumed ="))
        assertTrue(searchBranch.contains("sourceRoute = ScreenRoutes.Search.route"))
    }

    @Test
    fun categoryCardsReceiveVideoReturnStateToSuppressEnterAnimation() {
        val source = categoryScreenSource()

        assertTrue(source.contains("isReturningFromVideoDetail: Boolean = false"))
        assertTrue(source.contains("isQuickReturningFromVideoDetail: Boolean = false"))
        assertTrue(source.contains("isReturningFromVideoDetail = isReturningFromVideoDetail"))
        assertTrue(source.contains("isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail"))
    }


    @Test
    fun appNavigationUsesUnifiedBackGestureDecision() {
        val source = appNavigationSource()

        val decisionIndex = source.indexOf("val backGestureDecision = remember(")
        val handlerIndex = source.indexOf("MainHostTabBackHandler(")

        assertTrue(decisionIndex >= 0)
        assertTrue(handlerIndex > decisionIndex)
        assertTrue(source.contains("resolveBiliPaiBackGestureDecision("))
        assertTrue(source.contains("shouldInterceptTabBack = backGestureDecision.interceptSystemBack"))
        assertTrue(source.contains("sourceMetadata = navigation3SourceMetadata"))
        assertFalse(source.contains("shouldUseClassicBackForVideoSharedElementReturn("))
        assertFalse(source.contains("shouldInterceptVideoSharedElementReturn ||"))
    }

    @Test
    fun appNavigationDoesNotOwnRemovedBackPreviewProgressState() {
        val source = appNavigationSource()

        assertFalse(source.contains("navigation3PredictiveBackGestureState"))
        assertFalse(source.contains("onPredictiveBackGestureChange"))
        assertFalse(source.contains("video" + "PredictiveReturnToCardEnabled"))
        assertFalse(source.contains("video" + "PredictiveReturnSourceBounds"))
        assertFalse(source.contains("shouldEnableVideo" + "PredictiveReturnToCard"))
        assertTrue(source.contains("val predictiveBackEnabled = appNavigationSettings.predictiveBackEnabled"))
        assertTrue(source.contains("LocalPredictiveBackGestureEnabled provides predictiveBackEnabled"))
        assertTrue(source.contains("isQuickReturnFromDetail = navigation3ReturnSession.isQuickReturnFromDetail"))
        assertTrue(source.contains("BiliPaiPredictiveBackAnimationStyle.fromStorageValue("))
        assertTrue(source.contains("appNavigationSettings.predictiveBackAnimationStyle"))
        assertFalse(source.contains("shouldUseClassicBackHandler"))
    }

    @Test
    fun cardPositionManagerKeepsOnlyGeometryFallbackState() {
        val source = productionSourceExceptCardPositionManager()

        assertFalse(source.contains("CardPositionManager.isReturningFromDetail"))
        assertFalse(source.contains("CardPositionManager.isQuickReturnFromDetail"))
        assertFalse(source.contains("CardPositionManager.lastVideoSourceRoute"))
        assertFalse(source.contains("CardPositionManager.shouldLimitSharedElementsForQuickReturn()"))
        assertFalse(source.contains("CardPositionManager.markReturning()"))
        assertFalse(source.contains("CardPositionManager.clearReturning()"))
        assertFalse(source.contains("CardPositionManager.recordVideoSourceRoute("))
    }

    @Test
    fun appNavigationUsesNavDisplayAsSingleMainChain() {
        val source = appNavigationSource()
        val buildFile = appBuildGradleSource()

        assertTrue(source.contains("BiliPaiNavDisplayHost("))
        assertTrue(source.contains("sharedTransitionScope = LocalSharedTransitionScope.current"))
        assertTrue(source.contains("resolveBiliPaiNavEntryContentRole"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.HOME ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.DYNAMIC ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.SEARCH ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.PROFILE ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.VIDEO_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.HISTORY ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.WATCH_LATER ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.FAVORITE ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LOGIN ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.STORY ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.PARTITION ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.CATEGORY ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.SPACE ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.WEB ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.DYNAMIC_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.ARTICLE_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LIVE ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.BANGUMI_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LIVE_LIST ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LIVE_SEARCH ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LIVE_AREA ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LIVE_AREA_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LIVE_FOLLOWING ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.INBOX ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.REPLY_ME ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.AT_ME ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.LIKE_ME ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.SYSTEM_NOTICE ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.CHAT ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.AUDIO_MODE ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.ONBOARDING ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.FOLLOWING ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.DOWNLOAD_LIST ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.OFFLINE_VIDEO_PLAYER ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.SEARCH_TRENDING ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.TOPIC_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.SEASON_SERIES_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.BANGUMI ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.BANGUMI_PLAYER ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.MUSIC_DETAIL ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.NATIVE_MUSIC ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.OPEN_SOURCE_LICENSES ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.APPEARANCE_SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.ICON_SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.ANIMATION_SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.PLAYBACK_SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.PERMISSION_SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.PLUGINS_SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.BOTTOM_BAR_SETTINGS ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.SETTINGS_SHARE ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.WEB_DAV_BACKUP ->"))
        assertTrue(source.contains("BiliPaiNavEntryContentRole.TIPS_SETTINGS ->"))
        assertTrue(source.contains("onBack = { performSystemBackAction() }"))
        assertFalse(source.contains("shouldUseBiliPaiNavDisplayMainChain()"))
        assertFalse(source.contains("DEFERRED_LEGACY_ROUTE"))
        assertFalse(source.contains("NavHost("))
        assertFalse(buildFile.contains("navigation-compose"))
    }

    @Test
    fun privacyProtectedNavigationUsesCentralAuthenticationGate() {
        val source = appNavigationSource()

        assertTrue(source.contains("SettingsManager.getPrivacyContentAuthenticationEnabled(context)"))
        assertTrue(source.contains("shouldRequirePrivacyAuthentication("))
        assertTrue(source.contains("privacyAuthenticationEnabled = privacyAuthenticationEnabled"))
        assertTrue(source.contains("onPrivacyAuthenticationRequired("))
        assertTrue(source.contains("pushNavigation3KeyDirect("))
        assertTrue(source.contains("PrivacyAuthenticationReason.OPEN_PRIVACY_CONTENT"))
    }

    @Test
    fun videoCardLaunchBottomBarUsesSimpleVisibilityOutsideBackdropSource() {
        val source = appNavigationSource()
        val sharedChrome = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/home/components/BottomBarMatchedLiquidChrome.kt"),
            File("src/main/java/com/android/purebilibili/feature/home/components/BottomBarMatchedLiquidChrome.kt")
        ).first { it.exists() }.readText()
        val mainHostBranch = source
            .substringAfter("BiliPaiNavEntryContentRole.MAIN_HOST -> {")
            .substringBefore("BiliPaiNavEntryContentRole.HOME ->")
        val sourceContent = source
            .substringAfter(".then(bottomBarBackdropSource.modifier)")
            .substringBefore("} // End of Content Box")
        val bottomBarOverlay = source
            .substringAfter("if (bottomBarCanMount)")
            .substringBefore("MainHostTabBackHandler(")

        assertFalse(mainHostBranch.contains("VideoCardTransitionBackgroundRouteContent(bottomPagerNavKeyForItem(currentBottomNavItem))"))
        assertTrue(source.contains("val activeMainHostRoute = currentBottomNavItem.route"))
        assertFalse(source.contains("activeMainHostRoute = activeBottomTabRoute"))
        assertTrue(source.contains("val isVideoDetailDestination = isVideoDetailRoute(currentRoute)"))
        assertTrue(source.contains("val bottomBarMountRoute = resolveVideoCardTransitionChromeBottomBarRoute("))
        assertTrue(source.contains("activeRoute = bottomBarMountRoute"))
        val showBottomBarBlock = source
            .substringAfter("val showBottomBar = shouldShowBottomBarForNavigation(")
            .substringBefore("val finalBottomBarVisible")
        assertTrue(showBottomBarBlock.contains("activeRoute = bottomBarMountRoute"))
        assertTrue(source.contains("shouldShowVideoCardTransitionSourceChrome("))
        assertTrue(source.contains("shouldDriveVideoCardTransitionChromeByProgress("))
        assertTrue(source.contains("isVideoCardTransitionBottomBarSource("))
        assertTrue(source.contains("sourceIsBottomBarDestination = sourceIsBottomBarDestination"))
        assertTrue(source.contains("resolveVideoCardTransitionChromeReveal("))
        assertTrue(source.contains("videoCardTransitionChromeReveal("))
        assertTrue(source.contains("resolveVideoCardTransitionChromeBottomBarRoute("))
        assertTrue(source.contains("videoCardSourceChromeVisible"))
        assertTrue(source.contains("!isVideoDetailDestination"))
        assertTrue(source.contains("bottomBarMountGate ||"))
        assertTrue(source.contains("bottomBarVisibilityState.currentState ||"))
        assertTrue(source.contains("val sideBarRouteGate = shouldShowBottomBarForNavigation("))
        assertTrue(source.contains("windowSizeClass.shouldUseSideNavigation && sideBarMountGate"))
        assertTrue(source.contains("transitionSession?.hostOriginInRoot"))
        assertTrue(source.contains("hostOriginInRoot = navigationHostOriginInRoot"))
        assertTrue(source.contains("val bottomBarReservesSpace = bottomBarCanMount"))
        assertTrue(bottomBarOverlay.contains("visibleState = bottomBarVisibilityState"))
        assertTrue(bottomBarOverlay.contains("BottomBarMatchedDockVisibility("))
        assertTrue(bottomBarOverlay.contains("edge = BottomBarMatchedDockEdge.BOTTOM"))
        assertTrue(sharedChrome.contains("slideOutVertically("))
        assertTrue(sharedChrome.contains("scaleOut("))
        assertTrue(sharedChrome.contains("TransformOrigin(0.5f, 1f)"))
        assertTrue(bottomBarOverlay.contains("miuixBackdrop = bottomBarBackdrop"))
        assertFalse(bottomBarOverlay.contains("videoCardTransitionBackgroundEffect("))
        assertFalse(source.contains("videoCardTransitionBackgroundProgress = videoCardTransitionBackgroundProgress"))
        assertFalse(source.contains("videoCardTransitionBackgroundPhaseState = videoCardTransitionBackgroundPhaseState"))
        assertFalse(sourceContent.contains("miuixBackdrop = bottomBarBackdrop"))
        assertFalse(source.contains("RenderGlobalBottomBarOverlay()"))
        assertFalse(source.contains("miuixBackdrop = null"))
        assertFalse(source.contains("bottomBarVideoCardLaunchFeedback"))
        assertFalse(source.contains("videoCardLaunchFeedback"))
        assertFalse(source.contains("shouldPlayBottomBarVideoCardLaunchFeedback"))
    }

    @Test
    fun profileAndHistoryBottomBarReselectScrollToTop() {
        val source = appNavigationSource()
        assertTrue(source.contains("val historyScrollChannel"))
        assertTrue(source.contains("val profileScrollChannel"))
        assertTrue(source.contains("val liveScrollChannel"))
        assertTrue(source.contains("val watchLaterScrollChannel"))
        assertTrue(source.contains("BottomNavItem.HISTORY -> historyScrollChannel.trySend(Unit)"))
        assertTrue(source.contains("BottomNavItem.PROFILE -> profileScrollChannel.trySend(Unit)"))
        assertTrue(source.contains("BottomNavItem.LIVE -> liveScrollChannel.trySend(Unit)"))
        assertTrue(source.contains("BottomNavItem.WATCHLATER -> watchLaterScrollChannel.trySend(Unit)"))
        assertTrue(source.contains("scrollToTopChannel = historyScrollChannel"))
        assertTrue(source.contains("scrollToTopChannel = profileScrollChannel"))
        assertTrue(source.contains("scrollToTopChannel = liveScrollChannel"))
        assertTrue(source.contains("scrollToTopChannel = watchLaterScrollChannel"))
    }

    @Test
    fun navigationBackStackReplacementIsAtomicForNavDisplay() {
        val source = appNavigationSource()
        val replacement = source
            .substringAfter("fun replaceNavigation3BackStack(keys: List<BiliPaiNavKey>)")
            .substringBefore("val navigation3ProgrammaticBackDispatcher")

        assertTrue(replacement.contains("Snapshot.withMutableSnapshot"))
        assertTrue(replacement.contains("if (navigation3BackStack == replacementStack) return"))
        assertTrue(replacement.contains("navigation3BackStack.clear()"))
        assertTrue(replacement.contains("navigation3BackStack.addAll(replacementStack)"))
    }

    private fun appNavigationSource(): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/navigation/AppNavigation.kt"),
            File("src/main/java/com/android/purebilibili/navigation/AppNavigation.kt")
        ).first { it.exists() }.readText()
    }

    private fun appBuildGradleSource(): String {
        return listOf(
            File("app/build.gradle.kts"),
            File("build.gradle.kts")
        ).first { it.exists() }.readText()
    }

    private fun categoryScreenSource(): String {
        return listOf(
            File("app/src/main/java/com/android/purebilibili/feature/category/CategoryScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/category/CategoryScreen.kt")
        ).first { it.exists() }.readText()
    }

    private fun productionSourceExceptCardPositionManager(): String {
        val root = listOf(
            File("app/src/main/java"),
            File("src/main/java")
        ).first { it.exists() }

        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "CardPositionManager.kt" }
            .joinToString(separator = "\n") { it.readText() }
    }

    private fun navigation3Source(): String {
        val root = listOf(
            File("app/src/main/java/com/android/purebilibili/navigation3"),
            File("src/main/java/com/android/purebilibili/navigation3")
        ).first { it.exists() }

        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }
    }
}
