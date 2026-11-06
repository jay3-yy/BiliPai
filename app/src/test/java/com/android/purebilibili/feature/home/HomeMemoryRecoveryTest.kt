package com.android.purebilibili.feature.home

import androidx.compose.ui.unit.Density
import com.android.purebilibili.core.lifecycle.FairMemoryNavigationState
import com.android.purebilibili.core.lifecycle.FairMemoryRecoverySnapshot
import com.android.purebilibili.core.lifecycle.FairMemoryRecoveryStore
import com.android.purebilibili.core.lifecycle.forActivityState
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeMemoryRecoveryTest {
    private fun anchor(key: String) = HomeFeedScrollAnchor(
        key, 12f, true, 1080, 2, 3f, 1f,
    )

    private fun feed() = HomeFeedMemorySnapshot(
        "checkpoint", HomeCategory.RECOMMEND.name, PopularSubCategory.COMPREHENSIVE.name,
        listOf(VideoItem(bvid = "BV1", title = "saved card")),
        pageIndex = 8, hasMore = true, recommendRequestIndex = 12,
        anchor = anchor("home_video_bvid_BV1_0"),
    )

    @Test
    fun cardIdentitySurvivesInsertionAndHeadersWhileMissingCardsHaveNoTarget() {
        val first = VideoItem(bvid = "BV1")
        val duplicate = VideoItem(bvid = "BV1")
        val keys = resolveHomeCategoryVideoGridKeys(listOf(first, duplicate))
        val saved = anchor(keys[1])
        val changed = resolveHomeCategoryVideoGridKeys(listOf(VideoItem(bvid = "BV2"), first, duplicate))
        assertEquals(6, resolveHomeMemoryAnchorIndex(saved, changed, headerItemCount = 3, dividerIndex = 1))
        assertNull(resolveHomeMemoryAnchorIndex(saved, listOf(changed.first()), 3, null))
    }

    @Test
    fun pixelOffsetIsReusedOnlyForTheSameMeasuredLayout() {
        val saved = anchor("card")
        assertEquals(36, resolveHomeMemoryScrollOffsetPx(saved, true, 1080, 2, Density(3f, 1f)))
        listOf(
            saved.copy(regularLayout = false),
            saved.copy(viewportWidthPx = 600),
            saved.copy(columns = 3),
            saved.copy(density = 2f),
            saved.copy(fontScale = 1.2f),
        ).forEach { changed ->
            assertEquals(0, resolveHomeMemoryScrollOffsetPx(changed, true, 1080, 2, Density(3f, 1f)))
        }
    }

    @Test
    fun diskSnapshotCarriesCardsButActivityStateKeepsOnlyNavigationAndAnchor() {
        val snapshot = FairMemoryRecoverySnapshot(
            "old-process", 42L,
            FairMemoryNavigationState(listOf(BiliPaiNavKey.MainHost), "home", feed()),
            savedAtMs = 100L,
        )
        val json = FairMemoryRecoveryStore.json
        val disk = json.decodeFromString(FairMemoryRecoverySnapshot.serializer(), json.encodeToString(snapshot))
        assertEquals("BV1", disk.navigation.homeFeed?.videos?.single()?.bvid)
        val activity = json.decodeFromString(FairMemoryRecoverySnapshot.serializer(),
            json.encodeToString(snapshot.forActivityState()))
        assertTrue(requireNotNull(activity.navigation.homeFeed).videos.isEmpty())
        assertEquals(feed().anchor, activity.navigation.homeFeed?.anchor)
        assertEquals(12, activity.navigation.homeFeed?.recommendRequestIndex)
    }

    @Test
    fun feedTransferIsSingleUseAndNeverCrossesAccounts() {
        val saved = feed()
        HomeMemoryRecoveryRuntime.install(42L, saved)
        assertNull(HomeMemoryRecoveryRuntime.take(43L))
        assertNull(HomeMemoryRecoveryRuntime.take(42L))
        HomeMemoryRecoveryRuntime.install(42L, saved)
        assertEquals(saved, HomeMemoryRecoveryRuntime.take(42L))
        assertNull(HomeMemoryRecoveryRuntime.take(42L))
    }
}
