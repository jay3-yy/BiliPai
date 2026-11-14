package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.AppSignUtils
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.CancellationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeFeedRequestPolicyTest {
    @Test
    fun legacyLoginRetainsPhoneIdentityAndReportsCurrentLayout() {
        val initial = buildSignedHomePhoneFeedParams(idx = 0, accessToken = "test-token", platform = TokenManager.ACCESS_TOKEN_PLATFORM_TV, columns = 2, timestampSec = 123L)
        val next = buildSignedHomePhoneFeedParams(idx = 3, accessToken = "test-token", platform = TokenManager.ACCESS_TOKEN_PLATFORM_TV, columns = 4, flush = HOME_FEED_FLUSH_PULL_UP, timestampSec = 124L)
        assertEquals(AppSignUtils.TV_APP_KEY, initial["appkey"])
        assertEquals("android", initial["mobi_app"])
        assertEquals("android", initial["device"])
        assertEquals("8130300", initial["build"])
        assertEquals("test-token", initial["access_key"])
        assertEquals("true", initial["pull"])
        assertEquals("false", next["pull"])
        assertEquals("3", next["idx"])
        assertNull(initial["ps"])
        assertNull(next["ps"])
        assertEquals("2", initial["column"])
        assertEquals("4", next["column"])
        assertEquals("5", initial["flush"])
        assertEquals("8", next["flush"])
        assertEquals("124", next["ts"])
    }

    @Test
    fun manualRefreshRemainsPullEvenAfterPaginationAdvanced() {
        val refresh = buildSignedHomePhoneFeedParams(
            idx = 7, accessToken = "test-token", platform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID,
            flush = HOME_FEED_FLUSH_PULL_DOWN, timestampSec = 123L,
        )
        assertEquals("true", refresh["pull"])
        assertEquals("6", refresh["flush"])
        assertEquals("7", refresh["idx"])
    }

    @Test
    fun androidCredentialUsesItsOwnAppKeyAndSignature() {
        val android = buildSignedHomePhoneFeedParams(idx = 0, accessToken = "test-token", platform = TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID, timestampSec = 123L)
        val tv = buildSignedHomePhoneFeedParams(idx = 0, accessToken = "test-token", platform = TokenManager.ACCESS_TOKEN_PLATFORM_TV, columns = 2, timestampSec = 123L)
        assertEquals(AppSignUtils.ANDROID_APP_KEY, android["appkey"])
        assertNotEquals(tv["sign"], android["sign"])
        assertEquals(AppSignUtils.signForAndroidApi(android - "sign")["sign"], android["sign"])
    }

    @Test
    fun blankUnknownAndHdCredentialsCannotUsePhoneSigning() {
        assertFailsWith<IllegalArgumentException> {
            buildSignedHomePhoneFeedParams(0, " ", TokenManager.ACCESS_TOKEN_PLATFORM_TV)
        }
        for (platform in listOf(TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD, "unknown")) {
            assertFailsWith<IllegalArgumentException> {
                buildSignedHomePhoneFeedParams(0, "test-token", platform)
            }
        }
        assertEquals(HomeFeedSource.HD, homeMobileFeedSource(TokenManager.ACCESS_TOKEN_PLATFORM_ANDROID_HD))
    }

    @Test
    fun unavailableMobileCredentialProducesWebOnlyResultAndNotice() {
        val web = listOf(VideoItem(bvid = "web"))
        val page = mergeHomeFeedResults(Result.success(web), Result.failure(IllegalStateException("missing credential")), HomeFeedSource.MOBILE).getOrThrow()
        assertEquals(web, page.videos)
        assertEquals(setOf(HomeFeedSource.WEB), page.sources)
        assertNotNull(page.notice)
    }

    @Test
    fun mergedFeedsDeduplicateAndKeepTheirActualSources() {
        val web = listOf(VideoItem(bvid = "shared"), VideoItem(bvid = "web"))
        val mobile = listOf(VideoItem(bvid = "phone"), VideoItem(bvid = "shared"))
        val page = mergeHomeFeedResults(Result.success(web), Result.success(mobile), HomeFeedSource.MOBILE).getOrThrow()
        assertEquals(listOf("phone", "shared", "web"), page.videos.map { it.bvid })
        assertEquals(setOf(HomeFeedSource.WEB, HomeFeedSource.MOBILE), page.sources)
        assertNull(page.notice)
    }

    @Test
    fun webFailureAndHdCompatibilityAreVisible() {
        val mobile = Result.success(listOf(VideoItem(bvid = "hd")))
        val fallback = mergeHomeFeedResults(Result.failure(IllegalStateException("web failed")), mobile, HomeFeedSource.HD).getOrThrow()
        assertEquals(setOf(HomeFeedSource.HD), fallback.sources)
        assertTrue(fallback.notice.orEmpty().contains("网页端推荐不可用"))
        val merged = mergeHomeFeedResults(Result.success(listOf(VideoItem(bvid = "web"))), mobile, HomeFeedSource.HD).getOrThrow()
        assertTrue(merged.notice.orEmpty().contains("平板端"))
    }

    @Test
    fun emptyBranchesFailAndCancellationCannotBecomeFallback() {
        val empty = Result.success(emptyList<VideoItem>())
        assertTrue(mergeHomeFeedResults(empty, empty, HomeFeedSource.MOBILE).isFailure)
        val cancelled = Result.failure<List<VideoItem>>(CancellationException("cancelled"))
        val videos = Result.success(listOf(VideoItem(bvid = "web")))
        assertFailsWith<CancellationException> { mergeHomeFeedResults(videos, cancelled, HomeFeedSource.MOBILE) }
        assertFailsWith<CancellationException> { mergeHomeFeedResults(cancelled, videos, HomeFeedSource.MOBILE) }
    }
}
