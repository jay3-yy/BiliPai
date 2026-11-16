package com.android.purebilibili.feature.space

import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.core.network.BilibiliApi
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.SpaceApi
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.data.repository.HistoryRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.UserContentRepository
import io.mockk.Runs
import io.mockk.just
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceInitialLoadTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val api = mockk<BilibiliApi>()
    private val spaceApi = mockk<SpaceApi>()
    private lateinit var model: SpaceViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(NetworkModule, ActionRepository, FavoriteRepository, HistoryRepository, UserContentRepository, VideoRepository, WbiUtils)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { VideoRepository.prefetchVerticalFlags(any()) } just Runs
        every { NetworkModule.api } returns api
        every { NetworkModule.spaceApi } returns spaceApi
        every { WbiUtils.sign(any(), any(), any()) } answers { firstArg() }
        coEvery { api.getNavInfo() } coAnswers { awaitCancellation() }
        coEvery { api.getUserCard(any(), any()) } coAnswers { awaitCancellation() }
        coEvery { spaceApi.getTopArc(any()) } coAnswers { awaitCancellation() }
        coEvery { spaceApi.getNotice(any()) } coAnswers { awaitCancellation() }
        coEvery { spaceApi.getSpaceInfo(any()) } coAnswers { awaitCancellation() }
        coEvery { spaceApi.getAppSpaceSupporters(any()) } coAnswers { awaitCancellation() }
        coEvery { spaceApi.getSeasonsSeriesList(any()) } coAnswers { awaitCancellation() }
        coEvery { FavoriteRepository.getFavFolders(any()) } coAnswers { awaitCancellation() }
        coEvery { FavoriteRepository.getCollectedFavFolders(any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        coEvery { spaceApi.getRelationStat(any()) } coAnswers { awaitCancellation() }
        coEvery { spaceApi.getUpStat(any()) } coAnswers { awaitCancellation() }
        coEvery { ActionRepository.getRelationDetail(any()) } coAnswers { awaitCancellation() }
        coEvery { HistoryRepository.getHistoryList(ps = 50) } coAnswers { awaitCancellation() }
        model = SpaceViewModel()
        store.put("space", model)
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `aggregate content appears while user card and keys are still pending`() = runTest(dispatcher) {
        val card = CompletableDeferred<UserCardResponse>()
        coEvery { api.getUserCard(any(), any()) } coAnswers { card.await() }
        coEvery { spaceApi.getSpaceAggregate(any()) } returns aggregate(1)

        model.loadSpaceInfo(1)
        runCurrent()

        val initial = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertEquals(1L, initial.userInfo.mid)
        assertEquals("UP1", initial.userInfo.name)
        card.complete(UserCardResponse(data = UserCardData(
            space = UserSpaceBase(l_img = "https://example.com/banner.jpg"),
            ipLocation = "上海",
        )))
        runCurrent()

        val enriched = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertEquals("https://example.com/banner.jpg", enriched.userInfo.topPhoto)
        assertEquals("上海", enriched.userInfo.ipLocation)
        assertEquals(enriched.userInfo, enriched.headerState.userInfo)
        assertEquals(initial.videos, enriched.videos)
    }

    @Test
    fun `legacy profile appears before videos metrics and user card complete`() = runTest(dispatcher) {
        coEvery { spaceApi.getSpaceAggregate(any()) } returns SpaceAggregateResponse(code = -1)
        coEvery { api.getNavInfo() } returns NavResponse(data = NavData(
            wbi_img = WbiImg(img_url = "https://example.com/img.png", sub_url = "https://example.com/sub.png"),
        ))
        coEvery { spaceApi.getSpaceInfo(any()) } returns SpaceInfoResponse(
            data = SpaceUserInfo(mid = 1, name = "UP1", face = "face"),
        )
        val videos = CompletableDeferred<Result<SpaceVideoData>>()
        coEvery { UserContentRepository.videos(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            videos.await()
        }

        model.loadSpaceInfo(1)
        runCurrent()

        val state = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertEquals("UP1", state.userInfo.name)
        assertTrue(state.isLoadingMore)
        assertTrue(state.tabShellState.tabStates.getValue(SpaceMainTab.CONTRIBUTION).isLoading)
        assertFalse(state.tabShellState.tabStates.getValue(SpaceMainTab.CONTRIBUTION).hasLoaded)
        val item = SpaceVideoItem(bvid = "BV1", title = "投稿")
        videos.complete(Result.success(SpaceVideoData(
            list = SpaceVideoList(vlist = listOf(item)), page = SpacePage(count = 1),
        )))
        runCurrent()

        val loaded = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertEquals(listOf(item), loaded.videos)
        assertEquals(listOf(item), loaded.homeVideos)
        assertEquals(1, loaded.totalVideos)
        assertFalse(loaded.isLoadingMore)
        assertFalse(loaded.hasMoreVideos)
        assertTrue(loaded.tabShellState.tabStates.getValue(SpaceMainTab.CONTRIBUTION).hasLoaded)
    }

    @Test
    fun `switching UP cancels pending card and preserves the new profile`() = runTest(dispatcher) {
        val oldCard = CompletableDeferred<UserCardResponse>()
        var oldRequestCancelled = false
        coEvery { api.getUserCard(1, any()) } coAnswers {
            try {
                oldCard.await()
            } finally {
                oldRequestCancelled = true
            }
        }
        coEvery { spaceApi.getSpaceAggregate(any()) } answers {
            aggregate(firstArg<Map<String, String>>().getValue("vmid").toLong())
        }
        model.loadSpaceInfo(1)
        runCurrent()
        model.loadSpaceInfo(2)
        runCurrent()
        oldCard.complete(UserCardResponse(data = UserCardData(ipLocation = "旧属地")))
        runCurrent()

        val state = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertTrue(oldRequestCancelled)
        assertEquals(2L, state.userInfo.mid)
        assertEquals("UP2", state.userInfo.name)
        assertFalse(state.userInfo.ipLocation == "旧属地")
    }

    @Test
    fun `home content loads once on demand and is reused on return`() = runTest(dispatcher) {
        coEvery { spaceApi.getSpaceAggregate(any()) } returns aggregate(1)
        val top = CompletableDeferred<SpaceTopArcResponse>()
        coEvery { spaceApi.getTopArc(1) } coAnswers { top.await() }
        coEvery { spaceApi.getNotice(1) } returns SpaceNoticeResponse(data = "公告")
        model.loadSpaceInfo(1)
        runCurrent()
        coVerify(exactly = 0) { spaceApi.getTopArc(any()) }
        coVerify(exactly = 0) { spaceApi.getNotice(any()) }
        coVerify(exactly = 0) { HistoryRepository.getHistoryList(ps = 50) }

        model.loadVisibleSpaceContent()
        model.loadVisibleSpaceContent()
        runCurrent()
        coVerify(exactly = 1) { spaceApi.getTopArc(1) }
        top.complete(SpaceTopArcResponse(data = SpaceTopArcData(bvid = "BVtop")))
        runCurrent()
        model.loadVisibleSpaceContent()
        runCurrent()

        coVerify(exactly = 1) { spaceApi.getNotice(1) }
        coVerify(exactly = 1) { spaceApi.getTopArc(1) }
        val state = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertEquals("BVtop", state.topVideo?.bvid)
        assertEquals("公告", state.notice)
        assertEquals(state.topVideo, state.headerState.topVideo)
    }

    @Test
    fun `missing tabs are discovered with one item after primary content and classroom loads full first page on selection`() = runTest(dispatcher) {
        coEvery { spaceApi.getSpaceAggregate(any()) } returns aggregate(1)
        coEvery { api.getNavInfo() } returns NavResponse(data = NavData(wbi_img = WbiImg("img", "sub")))
        coEvery { spaceApi.getTopArc(1) } returns SpaceTopArcResponse()
        coEvery { spaceApi.getNotice(1) } returns SpaceNoticeResponse()
        coEvery { spaceApi.getSpaceArticleList(any()) } returns SpaceArticleResponse(
            data = SpaceArticleData(lists = listOf(SpaceArticleItem(id = 7)), total = 10),
        )
        val item = SpaceCheeseItem(seasonId = 8, title = "课堂")
        coEvery { spaceApi.getSpaceCheese(1, 1, 1) } returns SpaceCheeseResponse(
            data = SpaceCheeseData(items = listOf(item), page = SpaceCheesePage(next = true)),
        )
        coEvery { spaceApi.getSpaceCheese(1, 1, 30) } returns SpaceCheeseResponse(
            data = SpaceCheeseData(items = listOf(item), page = SpaceCheesePage(next = false)),
        )
        model.loadSpaceInfo(1)
        runCurrent()
        model.loadVisibleSpaceSupplemental()
        runCurrent()
        coVerify(exactly = 0) { spaceApi.getSpaceArticleList(any()) }
        coVerify(exactly = 0) { spaceApi.getSpaceCheese(any(), any(), any()) }

        model.loadVisibleSpaceContent()
        runCurrent()
        model.loadVisibleSpaceSupplemental()
        model.loadVisibleSpaceSupplemental()
        runCurrent()
        coVerify(exactly = 1) { spaceApi.getSpaceArticleList(match { it["ps"] == "1" }) }
        coVerify(exactly = 1) { spaceApi.getSpaceCheese(1, 1, 1) }
        coVerify(exactly = 0) { spaceApi.getSpaceDynamic(any(), any()) }
        val discovered = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertTrue(discovered.contributionTabs.any { it.subTab == SpaceSubTab.ARTICLE })
        assertTrue(discovered.hasCheeseTab)
        assertTrue(discovered.cheeseItems.isEmpty())
        assertFalse(discovered.hasLoadedCheeseOnce)

        model.selectMainTab(SpaceMainTab.CHEESE)
        runCurrent()
        coVerify(exactly = 1) { spaceApi.getSpaceCheese(1, 1, 30) }
        assertEquals(listOf(item), assertIs<SpaceUiState.Success>(model.uiState.value).cheeseItems)
    }

    @Test
    fun `pending home result is cancelled when switching UP`() = runTest(dispatcher) {
        coEvery { spaceApi.getSpaceAggregate(any()) } answers {
            aggregate(firstArg<Map<String, String>>().getValue("vmid").toLong())
        }
        val oldTop = CompletableDeferred<SpaceTopArcResponse>()
        var cancelled = false
        coEvery { spaceApi.getTopArc(1) } coAnswers {
            try { oldTop.await() } finally { cancelled = true }
        }
        model.loadSpaceInfo(1)
        runCurrent()
        model.loadVisibleSpaceContent()
        runCurrent()
        model.loadSpaceInfo(2)
        runCurrent()
        oldTop.complete(SpaceTopArcResponse(data = SpaceTopArcData(bvid = "BVold")))
        runCurrent()

        val state = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertTrue(cancelled)
        assertEquals(2L, state.userInfo.mid)
        assertEquals(null, state.topVideo)
        assertEquals("", state.notice)
    }

    @Test
    fun `video preview is hydrated only on contribution selection and shares the pending WBI request`() = runTest(dispatcher) {
        val aggregate = aggregate(1)
        coEvery { spaceApi.getSpaceAggregate(any()) } returns aggregate.copy(data = aggregate.data!!.copy(
            archive = SpaceAggregateArchive(count = 100, item = listOf(SpaceAggregateArchiveItem(bvid = "BVpreview"))),
        ))
        val keys = CompletableDeferred<NavResponse>()
        coEvery { api.getNavInfo() } coAnswers { keys.await() }
        val videos = (1..30).map { SpaceVideoItem(bvid = "BV$it") }
        coEvery { UserContentRepository.videos(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(SpaceVideoData(list = SpaceVideoList(vlist = videos), page = SpacePage(count = 100)))
        model.loadSpaceInfo(1)
        runCurrent()
        assertFalse(assertIs<SpaceUiState.Success>(model.uiState.value).isLoadingMore)
        model.loadVisibleSpaceContent()
        runCurrent()
        coVerify(exactly = 0) { UserContentRepository.videos(any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        model.selectMainTab(SpaceMainTab.CONTRIBUTION)
        runCurrent()
        model.loadVisibleSpaceContent()
        runCurrent()
        coVerify(exactly = 1) { api.getNavInfo() }
        keys.complete(NavResponse(data = NavData(wbi_img = WbiImg("img", "sub"))))
        runCurrent()

        coVerify(exactly = 1) { UserContentRepository.videos(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        val state = assertIs<SpaceUiState.Success>(model.uiState.value)
        assertEquals(videos, state.videos)
        assertFalse(state.isLoadingMore)
        assertTrue(state.hasMoreVideos)
        assertEquals(listOf("BVpreview"), state.homeVideos.map { it.bvid })
    }

    private fun aggregate(mid: Long) = SpaceAggregateResponse(data = SpaceAggregateData(
        card = SpaceAggregateCard(mid = mid.toString(), name = "UP$mid", face = "face"),
        tab2 = listOf(SpaceAggregateTab(param = "contribute", items = listOf(
            SpaceAggregateTabItem(param = "video", title = "视频"),
        ))),
    ))
}
