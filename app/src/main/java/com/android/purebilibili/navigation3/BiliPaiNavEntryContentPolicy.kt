package com.android.purebilibili.navigation3

internal enum class BiliPaiNavEntryContentRole {
    MAIN_HOST,
    HOME,
    DYNAMIC,
    SEARCH,
    SEARCH_TRENDING,
    TOPIC_DETAIL,
    SETTINGS,
    SETTINGS_CATEGORY,
    SETTINGS_SEARCH,
    OPEN_SOURCE_LICENSES,
    APPEARANCE_SETTINGS,
    ICON_SETTINGS,
    ANIMATION_SETTINGS,
    PLAYBACK_SETTINGS,
    PERMISSION_SETTINGS,
    PLUGINS_SETTINGS,
    JS_PLUGIN_CONTENT,
    EXTERNAL_MEDIA,
    BOTTOM_BAR_SETTINGS,
    SETTINGS_SHARE,
    WEB_DAV_BACKUP,
    TIPS_SETTINGS,
    PROFILE,
    VIDEO_DETAIL,
    HISTORY,
    FAVORITE,
    LIKED_VIDEOS,
    WATCH_LATER,
    ONBOARDING,
    FOLLOWING,
    DOWNLOAD_LIST,
    OFFLINE_VIDEO_PLAYER,
    LIVE_LIST,
    LIVE_SEARCH,
    LIVE_AREA,
    LIVE_AREA_DETAIL,
    LIVE_FOLLOWING,
    INBOX,
    REPLY_ME,
    AT_ME,
    LIKE_ME,
    SYSTEM_NOTICE,
    CHAT,
    AUDIO_MODE,
    LOGIN,
    STORY,
    PARTITION,
    CATEGORY,
    SEASON_SERIES_DETAIL,
    BANGUMI,
    BANGUMI_PLAYER,
    MUSIC_DETAIL,
    NATIVE_MUSIC,
    SPACE,
    WEB,
    DYNAMIC_DETAIL,
    ARTICLE_DETAIL,
    LIVE,
    BANGUMI_DETAIL
}

internal fun resolveBiliPaiNavEntryContentRole(key: BiliPaiNavKey): BiliPaiNavEntryContentRole {
    return when (key) {
        BiliPaiNavKey.MainHost -> BiliPaiNavEntryContentRole.MAIN_HOST
        BiliPaiNavKey.Home -> BiliPaiNavEntryContentRole.HOME
        BiliPaiNavKey.Dynamic -> BiliPaiNavEntryContentRole.DYNAMIC
        BiliPaiNavKey.Search -> BiliPaiNavEntryContentRole.SEARCH
        BiliPaiNavKey.SearchTrending -> BiliPaiNavEntryContentRole.SEARCH_TRENDING
        is BiliPaiNavKey.TopicDetail -> BiliPaiNavEntryContentRole.TOPIC_DETAIL
        BiliPaiNavKey.Settings -> BiliPaiNavEntryContentRole.SETTINGS
        is BiliPaiNavKey.SettingsCategory -> BiliPaiNavEntryContentRole.SETTINGS_CATEGORY
        BiliPaiNavKey.SettingsSearch -> BiliPaiNavEntryContentRole.SETTINGS_SEARCH
        BiliPaiNavKey.OpenSourceLicenses -> BiliPaiNavEntryContentRole.OPEN_SOURCE_LICENSES
        BiliPaiNavKey.AppearanceSettings -> BiliPaiNavEntryContentRole.APPEARANCE_SETTINGS
        BiliPaiNavKey.IconSettings -> BiliPaiNavEntryContentRole.ICON_SETTINGS
        BiliPaiNavKey.AnimationSettings -> BiliPaiNavEntryContentRole.ANIMATION_SETTINGS
        BiliPaiNavKey.PlaybackSettings -> BiliPaiNavEntryContentRole.PLAYBACK_SETTINGS
        BiliPaiNavKey.PermissionSettings -> BiliPaiNavEntryContentRole.PERMISSION_SETTINGS
        is BiliPaiNavKey.PluginsSettings -> BiliPaiNavEntryContentRole.PLUGINS_SETTINGS
        is BiliPaiNavKey.JsPluginContent -> BiliPaiNavEntryContentRole.JS_PLUGIN_CONTENT
        is BiliPaiNavKey.ExternalMedia -> BiliPaiNavEntryContentRole.EXTERNAL_MEDIA
        BiliPaiNavKey.BottomBarSettings -> BiliPaiNavEntryContentRole.BOTTOM_BAR_SETTINGS
        BiliPaiNavKey.SettingsShare -> BiliPaiNavEntryContentRole.SETTINGS_SHARE
        BiliPaiNavKey.WebDavBackup -> BiliPaiNavEntryContentRole.WEB_DAV_BACKUP
        BiliPaiNavKey.TipsSettings -> BiliPaiNavEntryContentRole.TIPS_SETTINGS
        BiliPaiNavKey.Profile -> BiliPaiNavEntryContentRole.PROFILE
        is BiliPaiNavKey.VideoDetail -> BiliPaiNavEntryContentRole.VIDEO_DETAIL
        BiliPaiNavKey.History -> BiliPaiNavEntryContentRole.HISTORY
        BiliPaiNavKey.Favorite -> BiliPaiNavEntryContentRole.FAVORITE
        BiliPaiNavKey.LikedVideos -> BiliPaiNavEntryContentRole.LIKED_VIDEOS
        BiliPaiNavKey.WatchLater -> BiliPaiNavEntryContentRole.WATCH_LATER
        BiliPaiNavKey.Onboarding -> BiliPaiNavEntryContentRole.ONBOARDING
        is BiliPaiNavKey.Following -> BiliPaiNavEntryContentRole.FOLLOWING
        BiliPaiNavKey.DownloadList -> BiliPaiNavEntryContentRole.DOWNLOAD_LIST
        is BiliPaiNavKey.OfflineVideoPlayer -> BiliPaiNavEntryContentRole.OFFLINE_VIDEO_PLAYER
        BiliPaiNavKey.LiveList -> BiliPaiNavEntryContentRole.LIVE_LIST
        BiliPaiNavKey.LiveSearch -> BiliPaiNavEntryContentRole.LIVE_SEARCH
        BiliPaiNavKey.LiveArea -> BiliPaiNavEntryContentRole.LIVE_AREA
        is BiliPaiNavKey.LiveAreaDetail -> BiliPaiNavEntryContentRole.LIVE_AREA_DETAIL
        BiliPaiNavKey.LiveFollowing -> BiliPaiNavEntryContentRole.LIVE_FOLLOWING
        BiliPaiNavKey.Inbox -> BiliPaiNavEntryContentRole.INBOX
        BiliPaiNavKey.ReplyMe -> BiliPaiNavEntryContentRole.REPLY_ME
        BiliPaiNavKey.AtMe -> BiliPaiNavEntryContentRole.AT_ME
        BiliPaiNavKey.LikeMe -> BiliPaiNavEntryContentRole.LIKE_ME
        BiliPaiNavKey.SystemNotice -> BiliPaiNavEntryContentRole.SYSTEM_NOTICE
        is BiliPaiNavKey.Chat -> BiliPaiNavEntryContentRole.CHAT
        is BiliPaiNavKey.AudioMode -> BiliPaiNavEntryContentRole.AUDIO_MODE
        BiliPaiNavKey.Login -> BiliPaiNavEntryContentRole.LOGIN
        is BiliPaiNavKey.Story -> BiliPaiNavEntryContentRole.STORY
        BiliPaiNavKey.Partition -> BiliPaiNavEntryContentRole.PARTITION
        is BiliPaiNavKey.Category -> BiliPaiNavEntryContentRole.CATEGORY
        is BiliPaiNavKey.SeasonSeriesDetail -> BiliPaiNavEntryContentRole.SEASON_SERIES_DETAIL
        is BiliPaiNavKey.Bangumi -> BiliPaiNavEntryContentRole.BANGUMI
        is BiliPaiNavKey.BangumiPlayer -> BiliPaiNavEntryContentRole.BANGUMI_PLAYER
        is BiliPaiNavKey.MusicDetail -> BiliPaiNavEntryContentRole.MUSIC_DETAIL
        is BiliPaiNavKey.NativeMusic -> BiliPaiNavEntryContentRole.NATIVE_MUSIC
        is BiliPaiNavKey.Space -> BiliPaiNavEntryContentRole.SPACE
        is BiliPaiNavKey.Web -> BiliPaiNavEntryContentRole.WEB
        is BiliPaiNavKey.DynamicDetail -> BiliPaiNavEntryContentRole.DYNAMIC_DETAIL
        is BiliPaiNavKey.ArticleDetail -> BiliPaiNavEntryContentRole.ARTICLE_DETAIL
        is BiliPaiNavKey.Live -> BiliPaiNavEntryContentRole.LIVE
        is BiliPaiNavKey.BangumiDetail -> BiliPaiNavEntryContentRole.BANGUMI_DETAIL
        is BiliPaiNavKey.Unknown -> BiliPaiNavEntryContentRole.HOME
    }
}
