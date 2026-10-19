package com.android.purebilibili.feature.space

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaceScreenStructureTest {

    @Test
    fun contentShortcutsKeepTheirTabSelectionCallbacks() {
        val source = File("src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt").readText()
        val call = source.substringAfter("SpaceContent(").substringBefore("DynamicCommentOverlayHost(")
        val declaration = source.substringAfter("private fun SpaceContent(").substringBefore(") {")
        assertTrue(call.contains("onMainTabSelected = viewModel::selectMainTab"))
        assertTrue(call.contains("onContributionTabSelected = viewModel::selectContributionTab"))
        assertTrue(declaration.contains("onMainTabSelected: (SpaceMainTab) -> Unit"))
        assertTrue(declaration.contains("onContributionTabSelected: (String) -> Unit"))
    }

    @Test
    fun `title and tabs share one measured chrome while full viewport content supplies blur`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val chrome = source.substringAfter("topBar = {").substringBefore(") { scaffoldPadding ->")
        assertTrue(chrome.contains("SpacePinnedTabs("))
        assertTrue(chrome.indexOf("AppTopBar(") < chrome.indexOf("SpacePinnedTabs("))
        assertFalse(chrome.contains("spaceChromeSource?.modifier"))
        val capture = source.indexOf(".then(spaceChromeSource?.modifier ?: Modifier)")
        val content = source.indexOf("SpaceContent(")
        assertTrue(capture >= 0 && capture < content)
        assertTrue(source.substring(capture, content).contains("globalWallpaperAwareBackground"))
        assertFalse(source.contains(".padding(top = chromeTopInset)"))
        assertFalse(source.contains("pinnedTabHeight"))
        assertTrue(source.contains("chromeTopInset = scaffoldPadding.calculateTopPadding()"))
    }


    @Test
    fun `contribution videos render as grid cards instead of full width rows`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("columns = GridCells.Fixed("))
        assertTrue(source.contains("resolveSpaceContentGridColumnCount("))
        assertTrue(source.contains("SpaceContributionVideoLayoutMode.GRID"))
        assertTrue(source.contains("SpaceHomeVideoCard("))
        assertTrue(source.contains("resolveSpaceContributionVideoItemKey("))
        assertFalse(source.contains("SpaceVideoListItemRow("))
    }

    @Test
    fun `space grid video card follows the home cover overlay treatment`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val cardSource = source
            .substringAfter("private fun SpaceHomeVideoCard(")
            .substringBefore("private fun SpaceAggregateMediaCard(")

        assertTrue(cardSource.contains("Brush.verticalGradient"))
        assertTrue(cardSource.contains("resolveVideoCardCoverOverlayTextShadow"))
        assertTrue(cardSource.contains("HorizontalVideoStatRow("))
        assertFalse(cardSource.contains("color = Color.Black.copy(alpha = 0.72f)"))
        assertTrue(source.contains("VideoCardCoverDurationText("))
        assertFalse(source.contains("color = Color.Black.copy(alpha = 0.72f)"))
    }

    @Test
    fun `contribution videos switch layout without dual placing lazy grid content`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val contributionVideoItems = source
            .substringAfter("items(\n                            items = state.videos")
            .substringBefore("if (state.isLoadingMore)")

        assertTrue(source.contains("showContributionVideoMenuActions"))
        assertTrue(source.contains("showContributionLayoutToggle"))
        assertTrue(source.contains("defaultSpaceContributionVideoLayoutMode()"))
        assertTrue(source.contains("toggleSpaceContributionVideoLayoutMode"))
        assertTrue(source.contains("resolveSpaceContributionVideoGridSpan("))
        assertTrue(source.contains("resolveSpaceContributionVideoItemKey("))
        assertTrue(source.contains("SpaceContributionVideoLayoutMode.SINGLE_COLUMN"))
        assertFalse(source.contains("mutableStateOf(SpaceContributionVideoLayoutMode.SINGLE_COLUMN)"))
        assertTrue(source.contains("SpaceArchiveListItemRow("))
        assertTrue(contributionVideoItems.contains("AnimatedVideoListItem(modifier = videoListItemModifier("))
        assertFalse(contributionVideoItems.contains("Modifier.animateItem()"))
        assertFalse(contributionVideoItems.contains("AnimatedContent("))
        assertFalse(contributionVideoItems.contains("SizeTransform("))
    }

    @Test
    fun `space high frequency video covers join shared element transition`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val horizontalArchiveCard = source
            .substringAfter("private fun SpaceArchiveListItemRow(")
            .substringBefore("private fun SpaceAudioListItem(")

        assertTrue(source.contains("sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(video.bvid)"))
        assertTrue(source.contains("sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(topVideo.bvid)"))
        assertTrue(source.contains("sharedTransitionKey = resolveSpaceArchiveSharedTransitionKey(item.bvid)"))
        assertTrue(source.contains("CardPositionManager.recordVideoCardPosition("))
        assertTrue(source.contains("videoCoverSharedElementKey("))
        assertTrue(source.contains("clipInOverlayDuringTransition = OverlayClip(coverShape)"))
        assertTrue(horizontalArchiveCard.contains("videoCardShellSharedBoundsOrEmpty("))
        assertTrue(horizontalArchiveCard.contains("HorizontalVideoCardFrame("))
        assertTrue(horizontalArchiveCard.contains("crossfadeSourceContent = true"))
        assertFalse(horizontalArchiveCard.contains("videoTitleSharedElementKey("))
    }

    @Test
    fun `space video cards freeze native pixels for return morph`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val homeCard = source
            .substringAfter("private fun SpaceHomeVideoCard(")
            .substringBefore("private fun SpaceAggregateMediaCard(")
        val aggregateCard = source
            .substringAfter("private fun SpaceAggregateMediaCard(")
            .substringBefore("private fun SpaceAggregatePosterCard(")
        val topCard = source
            .substringAfter("private fun SpaceTopVideoCard(")
            .substringBefore("private fun SpaceNoticeCard(")
        val archiveCard = source
            .substringAfter("private fun SpaceArchiveListItemRow(")
            .substringBefore("private fun SpaceAudioListItem(")

        listOf(homeCard, aggregateCard, topCard, archiveCard).forEach { card ->
            assertTrue(card.contains("rememberNativeVideoCardSnapshotController("))
            assertTrue(card.contains("nativeCardSnapshot.modifier"))
            assertTrue(card.contains("nativeCardSnapshot.capture()"))
        }
        assertTrue(homeCard.contains("nativeCardSnapshot.coverOverlayModifier"))
    }

    @Test
    fun `contribution video actions live in the top overflow menu`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("AppWindowActionMenu("))
        assertTrue(source.contains("label = \"播放全部\""))
        assertTrue(source.contains("\"切换为双列\""))
        assertTrue(source.contains("\"切换为单列\""))
        assertTrue(source.contains("\"排序：${'$'}{resolveSpaceVideoSortCompactLabel"))
        assertTrue(source.contains("children = VideoSortOrder.entries.map"))
        assertTrue(source.contains("selected = currentSuccessState?.sortOrder == order"))
        assertFalse(source.contains("showVideoSortMenu"))
        assertFalse(source.contains("SpaceContributionToolbar("))
        assertFalse(source.contains("SpaceContributionVideoToolbarActions("))
        assertFalse(source.contains("SpaceContributionTabRow("))
        assertFalse(source.contains("SpaceContributionVideoActions("))
    }


    @Test
    fun `space media library uses card semantic corners`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("AppShapes.borderedContainer(ContainerLevel.Card)"))
        assertTrue(source.contains("AppShapes.containerCornerDp(ContainerLevel.Card)"))
        assertFalse(source.contains("RoundedCornerShape("))
        assertFalse(source.contains("ContainerLevel.Dialog"))
        assertFalse(source.contains("sourceCornerDp = 12"))
        assertFalse(source.contains("sourceCornerDp = 14"))
    }

    @Test
    fun `space search action scrolls to focused search bar and dynamic body opens comments`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("resolveSpaceSearchBarGridItemIndex("))
        assertTrue(source.contains("resolveSpaceSearchBarRevealScrollOffsetPx("))
        assertTrue(source.contains("scrollOffset = searchBarRevealScrollOffsetPx"))
        assertTrue(source.contains("val searchFocusRequester = remember { FocusRequester() }"))
        assertTrue(source.contains(".focusRequester(searchFocusRequester)"))
        assertTrue(source.contains("SpaceSearchEntryChip("))
        assertTrue(source.contains("onSearchEntryClick = { viewModel.setSearchMode(true) }"))
        // bordered Field shape avoids iOS continuous-corner + BorderStroke chamfer
        assertTrue(source.contains("AppShapes.borderedContainer(ContainerLevel.Field)"))
        assertTrue(source.contains("onPrimaryClickOverride = { onSpaceDynamicCommentClick(dynamic) }"))
    }

    @Test
    fun `space profile exposes copy actions for its identifying text`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("copyOnLongPress(userInfo.name, \"UP主名称\")"))
        assertTrue(source.contains("copyOnLongPress(userInfo.sign, \"UP主简介\")"))
        assertTrue(source.contains("copyOnLongPress(userInfo.mid.toString(), \"UID\")"))
        assertTrue(source.contains("label = \"复制空间链接\""))
        assertTrue(source.contains("label = \"复制 UID\""))
        assertTrue(source.contains("label = \"分享\""))
        assertTrue(source.contains("label = \"举报\""))
        assertTrue(source.contains("https://space.bilibili.com/${'$'}mid"))
    }

    @Test
    fun `space official verify follows piliplus multiline badge presentation`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val officialTag = source
            .substringAfter("private fun SpaceOfficialTag(")
            .substringBefore("private fun SpaceBadgeChip(")

        assertTrue(source.contains("userInfo.official.spliceTitle.ifBlank"))
        assertTrue(officialTag.contains("Icons.Outlined.Bolt"))
        assertTrue(officialTag.contains("Color(0xFFFFCC00)"))
        assertTrue(officialTag.contains("fontSize = 12.sp"))
        assertFalse(officialTag.contains("maxLines = 1"))
        assertFalse(officialTag.contains("TextOverflow.Ellipsis"))
        assertFalse(officialTag.contains("widthIn("))
    }

    @Test
    fun `space follow actions follow piliplus header layout next to avatar`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("SpaceHeaderRelationActions("))
        assertTrue(source.contains("contentDescription = \"私信\""))
        assertTrue(source.contains("onMessageClick = onMessageClick"))
        assertTrue(source.contains("if (!isOwner)"))
        assertTrue(
            source.contains("PiliPlus 风格头部结构"),
            "relation actions should follow piliplus header layout next to avatar"
        )
        assertFalse(source.contains("resolveSpaceHeaderActionTopPaddingDp("))
        assertFalse(source.contains("topChromeInset = scaffoldPadding.calculateTopPadding()"))
    }

    @Test
    fun `space back button and up name stay in the pinned top bar`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val header = source
            .substringAfter("private fun SpaceHeader(")
            .substringBefore("private fun SpaceMainTabRow(")

        assertTrue(source.contains("resolveSpacePinnedTopChromeScrim("))
        assertFalse(source.contains("nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertFalse(source.contains("scrollBehavior = scrollBehavior"))
        assertFalse(
            header.contains(".alpha(contentAlpha)"),
            "header name must not fade/slide with the collapsing banner"
        )
        assertFalse(
            header.contains("IntOffset(0, -translateYPx)"),
            "header must not translate the name row under the pinned chrome"
        )
    }


    @Test
    fun `played video locate prompt is configurable and scoped to each space visit`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("getSpacePlayedVideoLocatePromptEnabled(context)"))
        assertTrue(source.contains("var playedVideoLocatePromptHandled by remember(mid, playedVideoBvid)"))
        assertFalse(source.contains("var playedVideoLocatePromptHandled by rememberSaveable"))
    }

    @Test
    fun `space screen includes liquid glass back to top button`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")

        assertTrue(source.contains("AppLiquidGlassBackToTopButton("))
        assertTrue(source.contains("rememberBackToTopButtonEnabled()"))
        assertTrue(source.contains("gridState.animateScrollToTop()"))
        assertTrue(source.contains("shouldShowScrollToTop("))
    }

    @Test
    fun `space recent liked videos section provides view all action to open user liked list`() {
        val source = loadSource("app/src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt")
        val likeSection = source.substringAfter("state.homeLikeVideos.isNotEmpty()")
            .substringBefore("itemsIndexed(")

        assertTrue(likeSection.contains("title = \"最近点赞的视频\""))
        assertTrue(likeSection.contains("actionLabel = \"查看全部\""))
        assertTrue(likeSection.contains("onLikedVideosClick(state.userInfo.mid, state.userInfo.name)"))
        assertTrue(likeSection.contains("onViewAllClick("))
        assertTrue(source.contains("onLikedVideosClick: ((Long, String) -> Unit)? = null"))
    }

    private fun loadSource(path: String): String {
        val normalizedPath = path.removePrefix("app/")
        val sourceFile = listOf(
            File(path),
            File(normalizedPath)
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
