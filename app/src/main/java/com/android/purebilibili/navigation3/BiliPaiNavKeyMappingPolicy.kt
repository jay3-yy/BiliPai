package com.android.purebilibili.navigation3

import com.android.purebilibili.navigation.ScreenRoutes
import com.android.purebilibili.navigation.VideoRoute
import com.android.purebilibili.feature.settings.SettingsRootCategory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun resolveNavigation3SaveableStateKey(key: BiliPaiNavKey): String {
    // 带 openId 的媒体详情按进入会话隔离，避免同视频再进复用已退出页面的播放器状态。
    return when (key) {
        is BiliPaiNavKey.VideoDetail -> {
            "video:${key.bvid}:${key.cid}:${key.openId}"
        }
        is BiliPaiNavKey.Story -> {
            if (key.seedBvid.isBlank()) {
                "story:tab"
            } else {
                "story:seed:${key.seedBvid}:${key.seedCid}:${key.openId}"
            }
        }
        else -> "${key.routeBase}:$key"
    }
}

internal fun resolveRemovedNavigation3SaveableStateKeys(
    currentStack: List<BiliPaiNavKey>,
    replacementStack: List<BiliPaiNavKey>,
): Set<String> {
    val retainedStateKeys = replacementStack
        .mapTo(mutableSetOf(), ::resolveNavigation3SaveableStateKey)
    return currentStack
        .mapTo(linkedSetOf(), ::resolveNavigation3SaveableStateKey)
        .filterTo(linkedSetOf()) { it !in retainedStateKeys }
}

internal fun BiliPaiNavKey.toLegacyRoute(): String {
    return when (this) {
        BiliPaiNavKey.MainHost -> "main_host"
        BiliPaiNavKey.Home -> ScreenRoutes.Home.route
        BiliPaiNavKey.ListenVideo -> ScreenRoutes.ListenVideo.route
        BiliPaiNavKey.Dynamic -> ScreenRoutes.Dynamic.route
        is BiliPaiNavKey.Search -> {
            if (keyword.isNotEmpty()) {
                "search?keyword=${encodeRouteValue(keyword)}"
            } else {
                ScreenRoutes.Search.route
            }
        }
        BiliPaiNavKey.Search -> ScreenRoutes.Search.route
        BiliPaiNavKey.SearchTrending -> ScreenRoutes.SearchTrending.route
        is BiliPaiNavKey.TopicDetail -> ScreenRoutes.TopicDetail.createRoute(topicId)
        BiliPaiNavKey.Settings -> ScreenRoutes.Settings.route
        is BiliPaiNavKey.SettingsCategory -> "settings_category/${category.name}"
        BiliPaiNavKey.SettingsSearch -> "settings_search"
        BiliPaiNavKey.OpenSourceLicenses -> ScreenRoutes.OpenSourceLicenses.route
        BiliPaiNavKey.AppearanceSettings -> ScreenRoutes.AppearanceSettings.route
        BiliPaiNavKey.HomeSettings -> ScreenRoutes.HomeSettings.route
        BiliPaiNavKey.IconSettings -> ScreenRoutes.IconSettings.route
        BiliPaiNavKey.AnimationSettings -> ScreenRoutes.AnimationSettings.route
        BiliPaiNavKey.PlaybackSettings -> ScreenRoutes.PlaybackSettings.route
        BiliPaiNavKey.PermissionSettings -> ScreenRoutes.PermissionSettings.route
        BiliPaiNavKey.MessageNotificationSettings -> ScreenRoutes.MessageNotificationSettings.route
        is BiliPaiNavKey.PluginsSettings -> ScreenRoutes.PluginsSettings.createRoute(importUrl)
        is BiliPaiNavKey.JsPluginContent -> ScreenRoutes.JsPluginContent.createRoute(pluginId)
        is BiliPaiNavKey.ExternalMedia -> ScreenRoutes.ExternalMedia.createRoute(launchId)
        BiliPaiNavKey.BottomBarSettings -> ScreenRoutes.BottomBarSettings.route
        BiliPaiNavKey.SettingsShare -> ScreenRoutes.SettingsShare.route
        BiliPaiNavKey.WebDavBackup -> ScreenRoutes.WebDavBackup.route
        BiliPaiNavKey.TipsSettings -> ScreenRoutes.TipsSettings.route
        BiliPaiNavKey.Login -> ScreenRoutes.Login.route
        BiliPaiNavKey.Profile -> ScreenRoutes.Profile.route
        is BiliPaiNavKey.AicuQuery -> ScreenRoutes.AicuQuery.createRoute(uid, category)
        BiliPaiNavKey.History -> ScreenRoutes.History.route
        is BiliPaiNavKey.HistorySearch -> "history_search?query=${encodeRouteValue(query)}"
        BiliPaiNavKey.Favorite -> ScreenRoutes.Favorite.route
        BiliPaiNavKey.FavoriteSubscribed -> "favorite_subscribed"
        is BiliPaiNavKey.FavoriteSearch ->
            "favorite_search?query=${encodeRouteValue(query)}&scope=${scope.name}"
        BiliPaiNavKey.LikedVideos -> ScreenRoutes.LikedVideos.route
        BiliPaiNavKey.WatchLater -> ScreenRoutes.WatchLater.route
        is BiliPaiNavKey.WatchLaterSearch -> "watch_later_search?query=${encodeRouteValue(query)}"
        BiliPaiNavKey.Onboarding -> ScreenRoutes.Onboarding.route
        is BiliPaiNavKey.Following -> ScreenRoutes.Following.createRoute(mid)
        BiliPaiNavKey.DownloadList -> ScreenRoutes.DownloadList.route
        is BiliPaiNavKey.OfflineVideoPlayer -> ScreenRoutes.OfflineVideoPlayer.createRoute(taskId)
        BiliPaiNavKey.LiveList -> ScreenRoutes.LiveList.route
        BiliPaiNavKey.LiveSearch -> ScreenRoutes.LiveSearch.route
        BiliPaiNavKey.LiveArea -> ScreenRoutes.LiveArea.route
        is BiliPaiNavKey.LiveAreaDetail -> ScreenRoutes.LiveAreaDetail.createRoute(parentAreaId, areaId, title)
        BiliPaiNavKey.LiveFollowing -> ScreenRoutes.LiveFollowing.route
        BiliPaiNavKey.Inbox -> ScreenRoutes.Inbox.route
        BiliPaiNavKey.ReplyMe -> ScreenRoutes.ReplyMe.route
        BiliPaiNavKey.AtMe -> ScreenRoutes.AtMe.route
        BiliPaiNavKey.LikeMe -> ScreenRoutes.LikeMe.route
        BiliPaiNavKey.SystemNotice -> ScreenRoutes.SystemNotice.route
        is BiliPaiNavKey.Chat -> ScreenRoutes.Chat.createRoute(talkerId, sessionType, userName)
        BiliPaiNavKey.Partition -> ScreenRoutes.Partition.route
        is BiliPaiNavKey.Story -> ScreenRoutes.Story.createRoute(
            bvid = seedBvid,
            cid = seedCid,
            cover = seedCover,
            title = seedTitle
        )
        is BiliPaiNavKey.AudioMode -> ScreenRoutes.AudioMode.createRoute(sourceBvid, sourceCid)
        is BiliPaiNavKey.SeasonSeriesDetail -> ScreenRoutes.SeasonSeriesDetail.createRoute(
            type = type,
            id = id,
            mid = mid,
            title = title,
            ownerName = ownerName
        )
        is BiliPaiNavKey.Bangumi -> ScreenRoutes.Bangumi.createRoute(initialType)
        is BiliPaiNavKey.BangumiPlayer -> ScreenRoutes.BangumiPlayer.createRoute(seasonId, epId, resumePositionMs)
        is BiliPaiNavKey.MusicDetail -> ScreenRoutes.MusicDetail.createRoute(sid)
        is BiliPaiNavKey.NativeMusic -> ScreenRoutes.NativeMusic.createRoute(title, bvid, cid)
        is BiliPaiNavKey.VideoDetail -> VideoRoute.createRoute(
            bvid = bvid,
            cid = cid,
            coverUrl = coverUrl,
            startAudio = startAudio,
            autoPortrait = autoPortrait,
            fullscreen = fullscreen,
            resumePositionMs = resumePositionMs,
            commentRootRpid = commentRootRpid,
            commentTargetRpid = commentTargetRpid,
            initialVertical = initialVertical,
            directPortraitEntry = directPortraitEntry,
        )
        is BiliPaiNavKey.ArticleDetail -> ScreenRoutes.ArticleDetail.createRoute(articleId, title)
        is BiliPaiNavKey.DynamicDetail -> ScreenRoutes.DynamicDetail.createRoute(
            dynamicId = dynamicId,
            commentRootRpid = commentRootRpid,
            commentTargetRpid = commentTargetRpid
        )
        is BiliPaiNavKey.Space -> ScreenRoutes.Space.createRoute(mid, targetBvid)
        is BiliPaiNavKey.Category -> ScreenRoutes.Category.createRoute(tid, name)
        is BiliPaiNavKey.Live -> ScreenRoutes.Live.createRoute(roomId, title, uname, siteId)
        is BiliPaiNavKey.BangumiDetail -> ScreenRoutes.BangumiDetail.createRoute(seasonId, epId, mediaId)
        is BiliPaiNavKey.BangumiReview -> ScreenRoutes.BangumiReview.createRoute(mediaId, title)
        is BiliPaiNavKey.Web -> ScreenRoutes.Web.createRoute(url, title)
        is BiliPaiNavKey.Unknown -> route
    }
}

internal fun legacyRouteToBiliPaiNavKey(route: String?): BiliPaiNavKey {
    val normalized = route?.takeIf { it.isNotBlank() } ?: return BiliPaiNavKey.Home
    val routeBase = normalized.substringBefore("?")
    val segments = routeBase.split('/').filter { it.isNotBlank() }
    val query = parseQuery(normalized.substringAfter("?", missingDelimiterValue = ""))

    return when {
        normalized == "main_host" -> BiliPaiNavKey.MainHost
        normalized == ScreenRoutes.Home.route -> BiliPaiNavKey.Home
        normalized == ScreenRoutes.ListenVideo.route -> BiliPaiNavKey.ListenVideo
        normalized == ScreenRoutes.Dynamic.route -> BiliPaiNavKey.Dynamic
        routeBase == ScreenRoutes.Search.route -> {
            val keyword = query["keyword"].orEmpty()
            if (keyword.isNotEmpty()) {
                BiliPaiNavKey.Search(keyword = keyword)
            } else {
                BiliPaiNavKey.Search
            }
        }
        normalized == ScreenRoutes.SearchTrending.route -> BiliPaiNavKey.SearchTrending
        segments.firstOrNull() == "topic" && segments.size >= 2 -> {
            BiliPaiNavKey.TopicDetail(topicId = segments[1].toLongOrNull() ?: 0L)
        }
        normalized == ScreenRoutes.Settings.route -> BiliPaiNavKey.Settings
        routeBase == "settings_category" && segments.size >= 2 -> {
            val category = SettingsRootCategory.entries.firstOrNull { it.name == segments[1] }
                ?: SettingsRootCategory.APPEARANCE_THEME
            BiliPaiNavKey.SettingsCategory(category = category)
        }
        normalized == "settings_search" -> BiliPaiNavKey.SettingsSearch
        normalized == ScreenRoutes.OpenSourceLicenses.route -> BiliPaiNavKey.OpenSourceLicenses
        normalized == ScreenRoutes.AppearanceSettings.route -> BiliPaiNavKey.AppearanceSettings
        normalized == ScreenRoutes.HomeSettings.route -> BiliPaiNavKey.HomeSettings
        normalized == ScreenRoutes.IconSettings.route -> BiliPaiNavKey.IconSettings
        normalized == ScreenRoutes.AnimationSettings.route -> BiliPaiNavKey.AnimationSettings
        normalized == ScreenRoutes.PlaybackSettings.route -> BiliPaiNavKey.PlaybackSettings
        normalized == ScreenRoutes.PermissionSettings.route -> BiliPaiNavKey.PermissionSettings
        normalized == ScreenRoutes.MessageNotificationSettings.route -> BiliPaiNavKey.MessageNotificationSettings
        routeBase == "plugins_settings" -> BiliPaiNavKey.PluginsSettings(importUrl = query["importUrl"])
        segments.firstOrNull() == "js_plugin" && segments.size >= 2 -> {
            BiliPaiNavKey.JsPluginContent(pluginId = decodeRouteValue(segments[1]))
        }
        segments.firstOrNull() == "external_media" && segments.size >= 2 -> {
            BiliPaiNavKey.ExternalMedia(launchId = decodeRouteValue(segments[1]))
        }
        normalized == ScreenRoutes.BottomBarSettings.route -> BiliPaiNavKey.BottomBarSettings
        normalized == ScreenRoutes.SettingsShare.route -> BiliPaiNavKey.SettingsShare
        normalized == ScreenRoutes.WebDavBackup.route -> BiliPaiNavKey.WebDavBackup
        normalized == ScreenRoutes.TipsSettings.route -> BiliPaiNavKey.TipsSettings
        normalized == ScreenRoutes.Login.route -> BiliPaiNavKey.Login
        normalized == ScreenRoutes.Profile.route -> BiliPaiNavKey.Profile
        routeBase == "aicu" -> BiliPaiNavKey.AicuQuery(
            uid = query["uid"]?.toLongOrNull()?.takeIf { it > 0 } ?: 0L,
            category = com.android.purebilibili.data.model.response.AicuCategory.fromRoute(query["category"]).name,
        )
        normalized == ScreenRoutes.History.route -> BiliPaiNavKey.History
        routeBase == "history_search" -> BiliPaiNavKey.HistorySearch(query["query"].orEmpty())
        normalized == ScreenRoutes.Favorite.route -> BiliPaiNavKey.Favorite
        normalized == "favorite_subscribed" -> BiliPaiNavKey.FavoriteSubscribed
        routeBase == "favorite_search" -> BiliPaiNavKey.FavoriteSearch(
            query = query["query"].orEmpty(),
            scope = com.android.purebilibili.data.model.response.FavoriteSearchScope.entries
                .firstOrNull { it.name == query["scope"] }
                ?: com.android.purebilibili.data.model.response.FavoriteSearchScope.CURRENT_FOLDER,
        )
        normalized == ScreenRoutes.LikedVideos.route -> BiliPaiNavKey.LikedVideos
        normalized == ScreenRoutes.WatchLater.route -> BiliPaiNavKey.WatchLater
        routeBase == "watch_later_search" -> BiliPaiNavKey.WatchLaterSearch(query["query"].orEmpty())
        normalized == ScreenRoutes.Onboarding.route -> BiliPaiNavKey.Onboarding
        segments.firstOrNull() == "following" && segments.size >= 2 -> {
            BiliPaiNavKey.Following(mid = segments[1].toLongOrNull() ?: 0L)
        }
        normalized == ScreenRoutes.DownloadList.route -> BiliPaiNavKey.DownloadList
        segments.firstOrNull() == "offline_video" && segments.size >= 2 -> {
            BiliPaiNavKey.OfflineVideoPlayer(taskId = decodeRouteValue(segments[1]))
        }
        normalized == ScreenRoutes.LiveList.route -> BiliPaiNavKey.LiveList
        normalized == ScreenRoutes.LiveSearch.route -> BiliPaiNavKey.LiveSearch
        normalized == ScreenRoutes.LiveArea.route -> BiliPaiNavKey.LiveArea
        segments.firstOrNull() == "live_area_detail" && segments.size >= 3 -> {
            BiliPaiNavKey.LiveAreaDetail(
                parentAreaId = segments[1].toIntOrNull() ?: 0,
                areaId = segments[2].toIntOrNull() ?: 0,
                title = query["title"].orEmpty()
            )
        }
        normalized == ScreenRoutes.LiveFollowing.route -> BiliPaiNavKey.LiveFollowing
        normalized == ScreenRoutes.Inbox.route -> BiliPaiNavKey.Inbox
        normalized == ScreenRoutes.ReplyMe.route -> BiliPaiNavKey.ReplyMe
        normalized == ScreenRoutes.AtMe.route -> BiliPaiNavKey.AtMe
        normalized == ScreenRoutes.LikeMe.route -> BiliPaiNavKey.LikeMe
        normalized == ScreenRoutes.SystemNotice.route -> BiliPaiNavKey.SystemNotice
        segments.firstOrNull() == "chat" && segments.size >= 3 -> {
            BiliPaiNavKey.Chat(
                talkerId = segments[1].toLongOrNull() ?: 0L,
                sessionType = segments[2].toIntOrNull() ?: 1,
                userName = query["name"].orEmpty()
            )
        }
        normalized == ScreenRoutes.Partition.route -> BiliPaiNavKey.Partition
        routeBase == "story" -> {
            BiliPaiNavKey.Story(
                seedBvid = decodeRouteValue(query["bvid"].orEmpty()),
                seedCid = query["cid"]?.toLongOrNull() ?: 0L,
                seedCover = decodeRouteValue(query["cover"].orEmpty()),
                seedTitle = decodeRouteValue(query["title"].orEmpty())
            )
        }
        routeBase == "audio_mode" -> BiliPaiNavKey.AudioMode(
            sourceBvid = decodeRouteValue(query["bvid"].orEmpty()),
            sourceCid = query["cid"]?.toLongOrNull() ?: 0L
        )
        segments.firstOrNull() == "season_series_detail" && segments.size >= 3 -> {
            BiliPaiNavKey.SeasonSeriesDetail(
                type = decodeRouteValue(segments[1]),
                id = segments[2].toLongOrNull() ?: 0L,
                mid = query["mid"]?.toLongOrNull() ?: 0L,
                title = query["title"].orEmpty(),
                ownerName = query["ownerName"].orEmpty()
            )
        }
        segments.firstOrNull() == "bangumi" && segments.getOrNull(1) == "play" && segments.size >= 4 -> {
            BiliPaiNavKey.BangumiPlayer(
                seasonId = segments[2].toLongOrNull() ?: 0L,
                epId = segments[3].toLongOrNull() ?: 0L,
                resumePositionMs = query["resumePositionMs"]?.toLongOrNull() ?: 0L
            )
        }
        routeBase == "bangumi" -> {
            BiliPaiNavKey.Bangumi(initialType = query["type"]?.toIntOrNull() ?: 1)
        }
        segments.firstOrNull() == "music" && segments.size >= 2 -> {
            BiliPaiNavKey.MusicDetail(sid = segments[1].toLongOrNull() ?: 0L)
        }
        routeBase == "native_music" -> {
            BiliPaiNavKey.NativeMusic(
                title = query["title"].orEmpty(),
                bvid = query["bvid"].orEmpty(),
                cid = query["cid"]?.toLongOrNull() ?: 0L
            )
        }
        segments.firstOrNull() == VideoRoute.base && segments.size >= 2 -> {
            BiliPaiNavKey.VideoDetail(
                bvid = decodeRouteValue(segments[1]),
                cid = query["cid"]?.toLongOrNull() ?: 0L,
                coverUrl = query["cover"].orEmpty(),
                startAudio = query["startAudio"]?.toBooleanStrictOrNull() ?: false,
                autoPortrait = query["autoPortrait"]?.toBooleanStrictOrNull() ?: false,
                fullscreen = query["fullscreen"]?.toBooleanStrictOrNull() ?: false,
                resumePositionMs = query["resumePositionMs"]?.toLongOrNull() ?: 0L,
                commentRootRpid = query["commentRootRpid"]?.toLongOrNull() ?: 0L,
                commentTargetRpid = query["commentTargetRpid"]?.toLongOrNull() ?: 0L,
                initialVertical = query["initialVertical"]?.toBooleanStrictOrNull() ?: false,
                directPortraitEntry = query["directPortraitEntry"]?.toBooleanStrictOrNull() ?: false,
                sourceRoute = null
            )
        }
        // 动态通知沿用历史 video_player 路由（ScreenRoutes.VideoPlayer）：桥接到真实的视频详情目的地，
        // 否则点击通知只能落到 Unknown。
        segments.firstOrNull() == "video_player" && segments.size >= 2 -> {
            BiliPaiNavKey.VideoDetail(
                bvid = decodeRouteValue(segments[1]),
                cid = query["cid"]?.toLongOrNull() ?: 0L,
                coverUrl = query["cover"].orEmpty(),
                commentRootRpid = query["commentRootRpid"]?.toLongOrNull() ?: 0L,
                commentTargetRpid = query["commentTargetRpid"]?.toLongOrNull() ?: 0L,
                sourceRoute = null
            )
        }
        segments.firstOrNull() == "article" && segments.size >= 2 -> {
            BiliPaiNavKey.ArticleDetail(
                articleId = segments[1].toLongOrNull() ?: 0L,
                title = query["title"].orEmpty()
            )
        }
        segments.firstOrNull() == "dynamic_detail" && segments.size >= 2 -> {
            BiliPaiNavKey.DynamicDetail(
                dynamicId = decodeRouteValue(segments[1]),
                commentRootRpid = query["commentRootRpid"]?.toLongOrNull() ?: 0L,
                commentTargetRpid = query["commentTargetRpid"]?.toLongOrNull() ?: 0L
            )
        }
        segments.firstOrNull() == "space" && segments.size >= 2 -> {
            BiliPaiNavKey.Space(
                mid = segments[1].toLongOrNull() ?: 0L,
                targetBvid = query["targetBvid"].orEmpty()
            )
        }
        segments.firstOrNull() == "category" && segments.size >= 2 -> {
            BiliPaiNavKey.Category(
                tid = segments[1].toIntOrNull() ?: 0,
                name = query["name"].orEmpty()
            )
        }
        segments.firstOrNull() == "live" && segments.size >= 2 -> {
            BiliPaiNavKey.Live(
                siteId = query["site"] ?: "bilibili",
                roomId = segments[1],
                title = query["title"].orEmpty(),
                uname = query["uname"].orEmpty()
            )
        }
        segments.firstOrNull() == "bangumi_review" && segments.size >= 2 -> {
            BiliPaiNavKey.BangumiReview(
                mediaId = segments[1].toLongOrNull() ?: 0L,
                title = query["title"].orEmpty()
            )
        }
        segments.firstOrNull() == "bangumi" && segments.size >= 2 -> {
            BiliPaiNavKey.BangumiDetail(
                seasonId = segments[1].toLongOrNull() ?: 0L,
                epId = query["epId"]?.toLongOrNull() ?: 0L,
                mediaId = query["mediaId"]?.toLongOrNull() ?: 0L
            )
        }
        normalized.substringBefore("?") == "web" -> {
            BiliPaiNavKey.Web(
                url = query["url"].orEmpty(),
                title = query["title"].orEmpty()
            )
        }
        else -> BiliPaiNavKey.Unknown(normalized)
    }
}

internal fun isCardReturnTargetNavKey(key: BiliPaiNavKey): Boolean {
    return when (key) {
        BiliPaiNavKey.Home,
        BiliPaiNavKey.MainHost,
        BiliPaiNavKey.Dynamic,
        is BiliPaiNavKey.Search,
        BiliPaiNavKey.Search,
        BiliPaiNavKey.History,
        is BiliPaiNavKey.HistorySearch,
        BiliPaiNavKey.Favorite,
        BiliPaiNavKey.FavoriteSubscribed,
        is BiliPaiNavKey.FavoriteSearch,
        BiliPaiNavKey.LikedVideos,
        BiliPaiNavKey.WatchLater,
        is BiliPaiNavKey.WatchLaterSearch,
        BiliPaiNavKey.Partition,
        is BiliPaiNavKey.DynamicDetail,
        is BiliPaiNavKey.Space,
        is BiliPaiNavKey.SeasonSeriesDetail,
        is BiliPaiNavKey.Category -> true
        else -> false
    }
}

private fun parseQuery(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    return query.split('&')
        .mapNotNull { part ->
            val key = part.substringBefore("=", missingDelimiterValue = "")
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter("=", missingDelimiterValue = "")
            decodeRouteValue(key) to decodeRouteValue(value)
        }
        .toMap()
}

private fun decodeRouteValue(value: String): String {
    return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

private fun encodeRouteValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())
