package com.android.purebilibili.navigation3

import kotlin.test.Test
import kotlin.test.assertEquals

class BiliPaiNavEntryContentPolicyTest {

    @Test
    fun topLevelKeysResolveToDedicatedContentRoles() {
        assertEquals(BiliPaiNavEntryContentRole.MAIN_HOST, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.MainHost))
        assertEquals(BiliPaiNavEntryContentRole.HOME, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Home))
        assertEquals(BiliPaiNavEntryContentRole.DYNAMIC, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Dynamic))
        assertEquals(BiliPaiNavEntryContentRole.SEARCH, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Search))
        assertEquals(BiliPaiNavEntryContentRole.SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Settings))
        assertEquals(BiliPaiNavEntryContentRole.PROFILE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Profile))
        assertEquals(BiliPaiNavEntryContentRole.HISTORY, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.History))
        assertEquals(BiliPaiNavEntryContentRole.FAVORITE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Favorite))
        assertEquals(BiliPaiNavEntryContentRole.FAVORITE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.FavoriteSubscribed))
        assertEquals(BiliPaiNavEntryContentRole.WATCH_LATER, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.WatchLater))
        assertEquals(BiliPaiNavEntryContentRole.HISTORY, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.HistorySearch("猫")))
        assertEquals(BiliPaiNavEntryContentRole.FAVORITE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.FavoriteSearch("猫")))
        assertEquals(BiliPaiNavEntryContentRole.WATCH_LATER, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.WatchLaterSearch("猫")))
        assertEquals(BiliPaiNavEntryContentRole.LOGIN, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Login))
        assertEquals(BiliPaiNavEntryContentRole.STORY, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Story()))
        assertEquals(BiliPaiNavEntryContentRole.PARTITION, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Partition))
        assertEquals(BiliPaiNavEntryContentRole.SPACE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Space(1L)))
        assertEquals(BiliPaiNavEntryContentRole.WEB, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Web("https://example.com")))
        assertEquals(BiliPaiNavEntryContentRole.DYNAMIC_DETAIL, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.DynamicDetail("1")))
        assertEquals(BiliPaiNavEntryContentRole.ARTICLE_DETAIL, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.ArticleDetail(1L)))
        assertEquals(BiliPaiNavEntryContentRole.LIVE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Live(roomId = "1")))
        assertEquals(BiliPaiNavEntryContentRole.BANGUMI_DETAIL, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.BangumiDetail(1L)))
        assertEquals(BiliPaiNavEntryContentRole.BANGUMI_REVIEW, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.BangumiReview(1L, "番剧")))
        assertEquals(BiliPaiNavEntryContentRole.LIVE_LIST, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.LiveList))
        assertEquals(BiliPaiNavEntryContentRole.LIVE_SEARCH, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.LiveSearch))
        assertEquals(BiliPaiNavEntryContentRole.LIVE_AREA, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.LiveArea))
        assertEquals(BiliPaiNavEntryContentRole.LIVE_AREA_DETAIL, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.LiveAreaDetail(1, 2, "网游")))
        assertEquals(BiliPaiNavEntryContentRole.LIVE_FOLLOWING, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.LiveFollowing))
        assertEquals(BiliPaiNavEntryContentRole.INBOX, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Inbox))
        assertEquals(BiliPaiNavEntryContentRole.REPLY_ME, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.ReplyMe))
        assertEquals(BiliPaiNavEntryContentRole.AT_ME, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.AtMe))
        assertEquals(BiliPaiNavEntryContentRole.LIKE_ME, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.LikeMe))
        assertEquals(BiliPaiNavEntryContentRole.SYSTEM_NOTICE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.SystemNotice))
        assertEquals(BiliPaiNavEntryContentRole.CHAT, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Chat(1L, 1, "用户")))
        assertEquals(BiliPaiNavEntryContentRole.AUDIO_MODE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.AudioMode()))
        assertEquals(BiliPaiNavEntryContentRole.ONBOARDING, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Onboarding))
        assertEquals(BiliPaiNavEntryContentRole.FOLLOWING, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Following(1L)))
        assertEquals(BiliPaiNavEntryContentRole.DOWNLOAD_LIST, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.DownloadList))
        assertEquals(BiliPaiNavEntryContentRole.OFFLINE_VIDEO_PLAYER, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.OfflineVideoPlayer("task-1")))
        assertEquals(BiliPaiNavEntryContentRole.SEARCH_TRENDING, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.SearchTrending))
        assertEquals(BiliPaiNavEntryContentRole.TOPIC_DETAIL, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.TopicDetail(1L)))
        assertEquals(BiliPaiNavEntryContentRole.SEASON_SERIES_DETAIL, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.SeasonSeriesDetail("season", 1L, 2L, "合集", "UP")))
        assertEquals(BiliPaiNavEntryContentRole.BANGUMI, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Bangumi(2)))
        assertEquals(BiliPaiNavEntryContentRole.BANGUMI_PLAYER, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.BangumiPlayer(1L, 2L)))
        assertEquals(BiliPaiNavEntryContentRole.MUSIC_DETAIL, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.MusicDetail(1L)))
        assertEquals(BiliPaiNavEntryContentRole.NATIVE_MUSIC, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.NativeMusic("背景音乐", "BV1", 3L)))
        assertEquals(BiliPaiNavEntryContentRole.OPEN_SOURCE_LICENSES, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.OpenSourceLicenses))
        assertEquals(BiliPaiNavEntryContentRole.APPEARANCE_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.AppearanceSettings))
        assertEquals(BiliPaiNavEntryContentRole.HOME_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.HomeSettings))
        assertEquals(BiliPaiNavEntryContentRole.ICON_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.IconSettings))
        assertEquals(BiliPaiNavEntryContentRole.ANIMATION_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.AnimationSettings))
        assertEquals(BiliPaiNavEntryContentRole.PLAYBACK_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.PlaybackSettings))
        assertEquals(BiliPaiNavEntryContentRole.PERMISSION_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.PermissionSettings))
        assertEquals(BiliPaiNavEntryContentRole.PLUGINS_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.PluginsSettings()))
        assertEquals(BiliPaiNavEntryContentRole.BOTTOM_BAR_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.BottomBarSettings))
        assertEquals(BiliPaiNavEntryContentRole.SETTINGS_SHARE, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.SettingsShare))
        assertEquals(BiliPaiNavEntryContentRole.WEB_DAV_BACKUP, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.WebDavBackup))
        assertEquals(BiliPaiNavEntryContentRole.TIPS_SETTINGS, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.TipsSettings))
        assertEquals(
            BiliPaiNavEntryContentRole.KEYBOARD_SHORTCUTS_SETTINGS,
            resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.KeyboardShortcutsSettings)
        )
    }

    @Test
    fun videoDetailKeyResolvesToDedicatedContentRole() {
        assertEquals(
            BiliPaiNavEntryContentRole.VIDEO_DETAIL,
            resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.VideoDetail("BV1"))
        )
    }

    @Test
    fun remainingDetailKeysStayDeferredUntilTheirLegacyRouteBodiesAreExtracted() {
        assertEquals(BiliPaiNavEntryContentRole.CATEGORY, resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Category(1)))
        assertEquals(
            BiliPaiNavEntryContentRole.HOME,
            resolveBiliPaiNavEntryContentRole(BiliPaiNavKey.Unknown("download"))
        )
    }
}
