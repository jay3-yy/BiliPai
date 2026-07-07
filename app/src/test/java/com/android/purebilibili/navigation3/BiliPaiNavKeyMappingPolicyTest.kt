package com.android.purebilibili.navigation3

import com.android.purebilibili.navigation.ScreenRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BiliPaiNavKeyMappingPolicyTest {

    @Test
    fun topLevelRoutes_mapToNavigation3Keys() {
        assertEquals(BiliPaiNavKey.MainHost, legacyRouteToBiliPaiNavKey("main_host"))
        assertEquals(BiliPaiNavKey.Home, legacyRouteToBiliPaiNavKey(ScreenRoutes.Home.route))
        assertEquals(BiliPaiNavKey.Dynamic, legacyRouteToBiliPaiNavKey(ScreenRoutes.Dynamic.route))
        assertEquals(BiliPaiNavKey.Search, legacyRouteToBiliPaiNavKey(ScreenRoutes.Search.route))
        assertEquals(BiliPaiNavKey.Profile, legacyRouteToBiliPaiNavKey(ScreenRoutes.Profile.route))
    }

    @Test
    fun videoRoute_preservesNavigationArguments() {
        val route = "video/BV1xx411c7mD?cid=123&cover=https%3A%2F%2Fexample.com%2Fcover.jpg" +
            "&startAudio=true&autoPortrait=true&fullscreen=true&resumePositionMs=456&commentRootRpid=789&commentTargetRpid=790"

        val key = assertIs<BiliPaiNavKey.VideoDetail>(legacyRouteToBiliPaiNavKey(route))

        assertEquals("BV1xx411c7mD", key.bvid)
        assertEquals(123L, key.cid)
        assertEquals("https://example.com/cover.jpg", key.coverUrl)
        assertEquals(true, key.startAudio)
        assertEquals(true, key.autoPortrait)
        assertEquals(true, key.fullscreen)
        assertEquals(456L, key.resumePositionMs)
        assertEquals(789L, key.commentRootRpid)
        assertEquals(790L, key.commentTargetRpid)
    }

    @Test
    fun videoRoute_preservesInitialVerticalHint() {
        val route = "video/BV1vertical?cid=123&cover=https%3A%2F%2Fexample.com%2Fcover.jpg" +
            "&startAudio=false&autoPortrait=true&fullscreen=false&resumePositionMs=0" +
            "&commentRootRpid=0&commentTargetRpid=0&initialVertical=true"

        val key = assertIs<BiliPaiNavKey.VideoDetail>(legacyRouteToBiliPaiNavKey(route))

        assertEquals(true, key.initialVertical)
    }

    @Test
    fun dynamicDetailRoute_preservesCommentArguments() {
        val route = ScreenRoutes.DynamicDetail.createRoute(
            dynamicId = "1073543151725051921",
            commentRootRpid = 265141324256L,
            commentTargetRpid = 265141324257L
        )

        val key = assertIs<BiliPaiNavKey.DynamicDetail>(legacyRouteToBiliPaiNavKey(route))

        assertEquals("1073543151725051921", key.dynamicId)
        assertEquals(265141324256L, key.commentRootRpid)
        assertEquals(265141324257L, key.commentTargetRpid)
        assertEquals(route, key.toLegacyRoute())
    }

    @Test
    fun navKey_roundTripsToLegacyRouteForCurrentBridge() {
        val key = BiliPaiNavKey.Space(mid = 42L)

        assertEquals(ScreenRoutes.Space.createRoute(42L), key.toLegacyRoute())
        assertEquals(key, legacyRouteToBiliPaiNavKey(key.toLegacyRoute()))
    }

    @Test
    fun settingsSecondaryRoutes_mapToNavigation3Keys() {
        assertEquals(BiliPaiNavKey.OpenSourceLicenses, legacyRouteToBiliPaiNavKey(ScreenRoutes.OpenSourceLicenses.route))
        assertEquals(BiliPaiNavKey.AppearanceSettings, legacyRouteToBiliPaiNavKey(ScreenRoutes.AppearanceSettings.route))
        assertEquals(BiliPaiNavKey.IconSettings, legacyRouteToBiliPaiNavKey(ScreenRoutes.IconSettings.route))
        assertEquals(BiliPaiNavKey.AnimationSettings, legacyRouteToBiliPaiNavKey(ScreenRoutes.AnimationSettings.route))
        assertEquals(BiliPaiNavKey.PlaybackSettings, legacyRouteToBiliPaiNavKey(ScreenRoutes.PlaybackSettings.route))
        assertEquals(BiliPaiNavKey.PermissionSettings, legacyRouteToBiliPaiNavKey(ScreenRoutes.PermissionSettings.route))
        assertEquals(BiliPaiNavKey.PluginsSettings(), legacyRouteToBiliPaiNavKey(ScreenRoutes.PluginsSettings.createRoute()))
        val pluginImportRoute = "plugins_settings?importUrl=https%3A%2F%2Fexample.com%2Fa.bpplugin"
        assertEquals(
            BiliPaiNavKey.PluginsSettings(importUrl = "https://example.com/a.bpplugin"),
            legacyRouteToBiliPaiNavKey(pluginImportRoute)
        )
        assertEquals(BiliPaiNavKey.BottomBarSettings, legacyRouteToBiliPaiNavKey(ScreenRoutes.BottomBarSettings.route))
        assertEquals(BiliPaiNavKey.SettingsShare, legacyRouteToBiliPaiNavKey(ScreenRoutes.SettingsShare.route))
        assertEquals(BiliPaiNavKey.WebDavBackup, legacyRouteToBiliPaiNavKey(ScreenRoutes.WebDavBackup.route))
        assertEquals(BiliPaiNavKey.TipsSettings, legacyRouteToBiliPaiNavKey(ScreenRoutes.TipsSettings.route))
    }

    @Test
    fun jsPluginRoutes_roundTripWithCompactIdentifiersOnly() {
        val contentKey = BiliPaiNavKey.JsPluginContent(pluginId = "live.tv")
        val mediaKey = BiliPaiNavKey.ExternalMedia(launchId = "launch-123")

        assertEquals("js_plugin/live.tv", contentKey.toLegacyRoute())
        assertEquals(contentKey, legacyRouteToBiliPaiNavKey(contentKey.toLegacyRoute()))
        assertEquals("external_media/launch-123", mediaKey.toLegacyRoute())
        assertEquals(mediaKey, legacyRouteToBiliPaiNavKey(mediaKey.toLegacyRoute()))
    }

    @Test
    fun liveSecondaryRoutes_mapToNavigation3Keys() {
        assertEquals(BiliPaiNavKey.LiveList, legacyRouteToBiliPaiNavKey(ScreenRoutes.LiveList.route))
        assertEquals(BiliPaiNavKey.LiveSearch, legacyRouteToBiliPaiNavKey(ScreenRoutes.LiveSearch.route))
        assertEquals(BiliPaiNavKey.LiveArea, legacyRouteToBiliPaiNavKey(ScreenRoutes.LiveArea.route))
        val liveAreaDetailRoute = "live_area_detail/1/2?title=%E7%BD%91%E6%B8%B8"
        assertEquals(
            BiliPaiNavKey.LiveAreaDetail(parentAreaId = 1, areaId = 2, title = "网游"),
            legacyRouteToBiliPaiNavKey(liveAreaDetailRoute)
        )
        assertEquals(BiliPaiNavKey.LiveFollowing, legacyRouteToBiliPaiNavKey(ScreenRoutes.LiveFollowing.route))
    }

    @Test
    fun messageRoutes_mapToNavigation3Keys() {
        assertEquals(BiliPaiNavKey.Inbox, legacyRouteToBiliPaiNavKey(ScreenRoutes.Inbox.route))
        assertEquals(BiliPaiNavKey.ReplyMe, legacyRouteToBiliPaiNavKey(ScreenRoutes.ReplyMe.route))
        assertEquals(BiliPaiNavKey.AtMe, legacyRouteToBiliPaiNavKey(ScreenRoutes.AtMe.route))
        assertEquals(BiliPaiNavKey.LikeMe, legacyRouteToBiliPaiNavKey(ScreenRoutes.LikeMe.route))
        assertEquals(BiliPaiNavKey.SystemNotice, legacyRouteToBiliPaiNavKey(ScreenRoutes.SystemNotice.route))
        assertEquals(
            BiliPaiNavKey.Chat(talkerId = 42L, sessionType = 1, userName = "测试用户"),
            legacyRouteToBiliPaiNavKey("chat/42/1?name=%E6%B5%8B%E8%AF%95%E7%94%A8%E6%88%B7")
        )
    }

    @Test
    fun standaloneUtilityRoutes_mapToNavigation3Keys() {
        assertEquals(BiliPaiNavKey.Onboarding, legacyRouteToBiliPaiNavKey(ScreenRoutes.Onboarding.route))
        assertEquals(BiliPaiNavKey.Following(42L), legacyRouteToBiliPaiNavKey(ScreenRoutes.Following.createRoute(42L)))
        assertEquals(BiliPaiNavKey.DownloadList, legacyRouteToBiliPaiNavKey(ScreenRoutes.DownloadList.route))
        assertEquals(
            BiliPaiNavKey.OfflineVideoPlayer("task-1"),
            legacyRouteToBiliPaiNavKey("offline_video/task-1")
        )
    }

    @Test
    fun contentMediaRoutes_mapToNavigation3Keys() {
        assertEquals(BiliPaiNavKey.SearchTrending, legacyRouteToBiliPaiNavKey(ScreenRoutes.SearchTrending.route))
        assertEquals(BiliPaiNavKey.TopicDetail(42L), legacyRouteToBiliPaiNavKey(ScreenRoutes.TopicDetail.createRoute(42L)))
        assertEquals(
            BiliPaiNavKey.SeasonSeriesDetail("season", 1L, 2L, "合集", "UP主"),
            legacyRouteToBiliPaiNavKey("season_series_detail/season/1?mid=2&title=%E5%90%88%E9%9B%86&ownerName=UP%E4%B8%BB")
        )
        assertEquals(BiliPaiNavKey.Bangumi(initialType = 2), legacyRouteToBiliPaiNavKey(ScreenRoutes.Bangumi.createRoute(2)))
        assertEquals(
            BiliPaiNavKey.BangumiPlayer(seasonId = 1L, epId = 2L, resumePositionMs = 3000L),
            legacyRouteToBiliPaiNavKey(ScreenRoutes.BangumiPlayer.createRoute(1L, 2L, 3000L))
        )
        assertEquals(BiliPaiNavKey.MusicDetail(100L), legacyRouteToBiliPaiNavKey(ScreenRoutes.MusicDetail.createRoute(100L)))
        assertEquals(
            BiliPaiNavKey.NativeMusic(title = "背景音乐", bvid = "BV1", cid = 3L),
            legacyRouteToBiliPaiNavKey("native_music?title=%E8%83%8C%E6%99%AF%E9%9F%B3%E4%B9%90&bvid=BV1&cid=3")
        )
    }

    @Test
    fun cardReturnTargets_matchExistingSharedElementDestinations() {
        assertEquals(true, isCardReturnTargetNavKey(BiliPaiNavKey.MainHost))
        assertEquals(true, isCardReturnTargetNavKey(BiliPaiNavKey.Home))
        assertEquals(true, isCardReturnTargetNavKey(BiliPaiNavKey.Search))
        assertEquals(true, isCardReturnTargetNavKey(BiliPaiNavKey.Space(42L)))
        assertEquals(true, isCardReturnTargetNavKey(BiliPaiNavKey.LikedVideos))
        assertEquals(
            true,
            isCardReturnTargetNavKey(
                BiliPaiNavKey.SeasonSeriesDetail(type = "series", id = 1L, mid = 2L)
            )
        )
        assertEquals(false, isCardReturnTargetNavKey(BiliPaiNavKey.VideoDetail("BV1")))
        assertEquals(false, isCardReturnTargetNavKey(BiliPaiNavKey.Settings))
    }
}
