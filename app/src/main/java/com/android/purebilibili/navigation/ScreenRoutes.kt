package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.encodeUrlComponentCompat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class ScreenRoutes(val route: String) {
    object Home : ScreenRoutes("home")
    object ListenVideo : ScreenRoutes("listen_video")
    object Search : ScreenRoutes("search") {
        fun createRoute(keyword: String = ""): String {
            return if (keyword.isBlank()) "search" else "search?keyword=${URLEncoder.encode(keyword, StandardCharsets.UTF_8.name())}"
        }
    }
    object SearchTrending : ScreenRoutes("search_trending")
    object TopicDetail : ScreenRoutes("topic/{topicId}") {
        fun createRoute(topicId: Long): String {
            return "topic/$topicId"
        }
    }
    object Settings : ScreenRoutes("settings")
    object Login : ScreenRoutes("login")
    object Profile : ScreenRoutes("profile")
    object AccountLedger : ScreenRoutes("account_ledger/{kind}") {
        fun createRoute(kind: com.android.purebilibili.data.model.AccountLedgerKind): String = "account_ledger/${kind.name.lowercase()}"
    }

    //  新增路由：历史记录和收藏
    object AicuQuery : ScreenRoutes("aicu?uid={uid}&category={category}") {
        fun createRoute(uid: Long? = null, category: String = "COMMENT"): String =
            "aicu?uid=${uid?.takeIf { it > 0 } ?: 0}&category=${URLEncoder.encode(category, StandardCharsets.UTF_8.name())}"
    }
    object History : ScreenRoutes("history")
    object Favorite : ScreenRoutes("favorite")
    object LikedVideos : ScreenRoutes("liked_videos") {
        fun createRoute(
            mid: Long = 0L,
            ownerName: String = "",
            isCoinArchive: Boolean = false,
        ): String {
            return if (mid > 0L) {
                val archiveType = if (isCoinArchive) "&type=coin" else ""
                "liked_videos?mid=$mid&ownerName=${encodeUrlComponentCompat(ownerName)}$archiveType"
            } else {
                "liked_videos"
            }
        }
    }
    object WatchLater : ScreenRoutes("watch_later")  //  [新增] 稍后再看
    object LiveList : ScreenRoutes("live_list")  //  [新增] 直播列表
    object LiveSearch : ScreenRoutes("live_search")
    object LiveArea : ScreenRoutes("live_area")
    object LiveAreaDetail : ScreenRoutes("live_area_detail/{parentAreaId}/{areaId}?title={title}") {
        fun createRoute(parentAreaId: Int, areaId: Int, title: String): String {
            return "live_area_detail/$parentAreaId/$areaId?title=${encodeUrlComponentCompat(title)}"
        }
    }
    object LiveMatch : ScreenRoutes("live_match")

    //  比赛详情页（赛事）
    object MatchInfo : ScreenRoutes("match_info/{cid}") {
        fun createRoute(cid: Long): String = "match_info/$cid"
    }
    object LiveFollowing : ScreenRoutes("live_following")
    
    //  关注列表页面
    object Following : ScreenRoutes("following/{mid}") {
        fun createRoute(mid: Long): String {
            return "following/$mid"
        }
    }
    
    //  离线缓存列表
    object DownloadList : ScreenRoutes("download_list")
    
    // 🔧 [新增] 离线视频播放
    object OfflineVideoPlayer : ScreenRoutes("offline_video/{taskId}") {
        fun createRoute(taskId: String): String {
            return "offline_video/${encodeUrlComponentCompat(taskId)}"
        }
    }
    
    //  动态页面
    object Dynamic : ScreenRoutes("dynamic")

    //  动态详情页面
    object DynamicDetail : ScreenRoutes("dynamic_detail/{dynamicId}?commentRootRpid={commentRootRpid}&commentTargetRpid={commentTargetRpid}&openComments={openComments}") {
        fun createRoute(
            dynamicId: String,
            commentRootRpid: Long = 0L,
            commentTargetRpid: Long = 0L,
            openComments: Boolean = false,
        ): String {
            val encodedDynamicId = URLEncoder.encode(dynamicId, StandardCharsets.UTF_8.name())
            return "dynamic_detail/$encodedDynamicId" +
                "?commentRootRpid=${commentRootRpid.coerceAtLeast(0L)}" +
                "&commentTargetRpid=${commentTargetRpid.coerceAtLeast(0L)}" +
                if (openComments) "&openComments=true" else ""
        }
    }

    object ArticleCollection : ScreenRoutes("article_collection/{collectionId}?title={title}&currentArticleId={currentArticleId}") {
        fun createRoute(collectionId: Long, title: String = "", currentArticleId: Long = 0L): String =
            "article_collection/$collectionId?title=${encodeUrlComponentCompat(title)}&currentArticleId=$currentArticleId"
    }

    object ArticleDetail : ScreenRoutes("article/{articleId}?title={title}&commentRootRpid={commentRootRpid}&commentTargetRpid={commentTargetRpid}") {
        fun createRoute(articleId: Long, title: String? = null, commentRootRpid: Long = 0L, commentTargetRpid: Long = 0L): String {
            val encodedTitle = title?.let(::encodeUrlComponentCompat).orEmpty()
            val base = "article/$articleId?title=$encodedTitle"
            if (commentRootRpid <= 0L && commentTargetRpid <= 0L) return base
            return base + "&commentRootRpid=${commentRootRpid.coerceAtLeast(0L)}" +
                "&commentTargetRpid=${commentTargetRpid.coerceAtLeast(0L)}"
        }
    }
    
    //  [新增] 竖屏短视频 (故事模式)
    object Story : ScreenRoutes("story?bvid={bvid}&cid={cid}&cover={cover}&title={title}") {
        const val baseRoute: String = "story"

        fun createRoute(
            bvid: String = "",
            cid: Long = 0L,
            cover: String = "",
            title: String = ""
        ): String {
            val encodedCover = encodeUrlComponentCompat(cover)
            val encodedTitle = encodeUrlComponentCompat(title)
            return "story?bvid=${encodeUrlComponentCompat(bvid)}&cid=$cid&cover=$encodedCover&title=$encodedTitle"
        }
    }

    //  开源许可证页面
    object OpenSourceLicenses : ScreenRoutes("open_source_licenses")
    
    //  二级设置页面
    object AppearanceSettings : ScreenRoutes("appearance_settings")
    object HomeSettings : ScreenRoutes("home_settings")
    object PlaybackSettings : ScreenRoutes("playback_settings")
    object SpacePrivacySettings : ScreenRoutes("space_privacy_settings")
    object LoginActivitySettings : ScreenRoutes("login_activity_settings")
    object PermissionSettings : ScreenRoutes("permission_settings")  //  权限管理
    object MessageNotificationSettings : ScreenRoutes("message_notification_settings")
    object PluginsSettings : ScreenRoutes("plugins_settings?importUrl={importUrl}") {  //  插件中心
        fun createRoute(importUrl: String? = null): String {
            if (importUrl.isNullOrBlank()) return "plugins_settings"
            return "plugins_settings?importUrl=${encodeUrlComponentCompat(importUrl)}"
        }
    }
    object JsPluginContent : ScreenRoutes("js_plugin/{pluginId}") {
        fun createRoute(pluginId: String): String {
            return "js_plugin/${encodeUrlComponentCompat(pluginId)}"
        }
    }
    object ExternalMedia : ScreenRoutes("external_media/{launchId}") {
        fun createRoute(launchId: String): String {
            return "external_media/${encodeUrlComponentCompat(launchId)}"
        }
    }
    object BottomBarSettings : ScreenRoutes("bottom_bar_settings")  //  底栏管理
    object SettingsShare : ScreenRoutes("settings_share")
    object WebDavBackup : ScreenRoutes("webdav_backup") // WebDAV 备份中心
    object TipsSettings : ScreenRoutes("tips_settings") // [Feature] 小贴士 & 隐藏操作
    object KeyboardShortcutsSettings : ScreenRoutes("keyboard_shortcuts_settings") // [Feature] 键盘快捷键帮助
    //  [新增] 更多外观设置子页面

    object IconSettings : ScreenRoutes("icon_settings")  // 图标设置
    object AnimationSettings : ScreenRoutes("animation_settings")  // 动画设置

    // [修复] 添加 aid 参数支持，用于移动端推荐流（可能只返回 aid）
    object VideoPlayer : ScreenRoutes("video_player/{bvid}?cid={cid}&aid={aid}&commentRootRpid={commentRootRpid}&commentTargetRpid={commentTargetRpid}") {
        fun createRoute(
            bvid: String,
            cid: Long = 0,
            aid: Long = 0,
            commentRootRpid: Long = 0,
            commentTargetRpid: Long = 0
        ): String {
            return "video_player/$bvid?cid=$cid&aid=$aid&commentRootRpid=$commentRootRpid&commentTargetRpid=$commentTargetRpid"
        }
    }
    
    //  [新增] UP主空间页面
    object Space : ScreenRoutes("space/{mid}?targetBvid={targetBvid}") {
        fun createRoute(mid: Long, targetBvid: String = ""): String {
            return targetBvid.trim().takeIf { it.isNotEmpty() }
                ?.let {
                    "space/$mid?targetBvid=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}"
                }
                ?: "space/$mid"
        }
    }

    //  [新增] 合集/系列详情页面
    object SeasonSeriesDetail : ScreenRoutes("season_series_detail/{type}/{id}?mid={mid}&title={title}&ownerName={ownerName}") {
        fun createRoute(type: String, id: Long, mid: Long, title: String, ownerName: String = ""): String {
            // Encode title to handle special characters
            val encodedTitle = encodeUrlComponentCompat(title)
            val encodedOwnerName = encodeUrlComponentCompat(ownerName)
            return "season_series_detail/$type/$id?mid=$mid&title=$encodedTitle&ownerName=$encodedOwnerName"
        }
    }
    
    //  [新增] 直播播放页面
    object Live : ScreenRoutes("live/{roomId}?title={title}&uname={uname}&site={site}") {
        fun createRoute(roomId: Any, title: String, uname: String, siteId: String = "bilibili"): String {
            val encodedTitle = encodeUrlComponentCompat(title)
            val encodedUname = encodeUrlComponentCompat(uname)
            return "live/$roomId?title=$encodedTitle&uname=$encodedUname&site=$siteId"
        }
    }
    
    //  [新增] 音频模式页面
    object AudioMode : ScreenRoutes("audio_mode") {
        fun createRoute(bvid: String = "", cid: Long = 0L): String {
            if (bvid.isBlank()) return route
            return "audio_mode?bvid=$bvid&cid=$cid"
        }
    }
    
    //  [新增] 番剧/影视页面 - 支持初始类型参数
    object Bangumi : ScreenRoutes("bangumi?type={type}") {
        fun createRoute(initialType: Int = 1): String {
            return "bangumi?type=$initialType"
        }
    }
    
    object BangumiDetail : ScreenRoutes("bangumi/{seasonId}?epId={epId}&mediaId={mediaId}") {
        fun createRoute(seasonId: Long, epId: Long = 0, mediaId: Long = 0): String {
            return "bangumi/$seasonId?epId=$epId&mediaId=$mediaId"
        }
    }

    object BangumiReview : ScreenRoutes("bangumi_review/{mediaId}?title={title}") {
        fun createRoute(mediaId: Long, title: String = ""): String {
            return "bangumi_review/$mediaId?title=${encodeUrlComponentCompat(title)}"
        }
    }
    
    //  [新增] 番剧播放页面
    object BangumiPlayer : ScreenRoutes("bangumi/play/{seasonId}/{epId}?resumePositionMs={resumePositionMs}&preferredAid={preferredAid}&isCourse={isCourse}") {
        fun createRoute(
            seasonId: Long,
            epId: Long,
            resumePositionMs: Long = 0L,
            preferredAid: Long = 0L,
            isCourse: Boolean = false
        ): String {
            val route = "bangumi/play/$seasonId/$epId"
            val resumePosition = resumePositionMs.coerceAtLeast(0L)
            val aid = preferredAid.coerceAtLeast(0L)
            return if (resumePosition > 0L || aid > 0L || isCourse) {
                "$route?resumePositionMs=$resumePosition&preferredAid=$aid&isCourse=$isCourse"
            } else {
                route
            }
        }
    }
    
    //  分区页面
    object Partition : ScreenRoutes("partition")
    
    //  分类详情页面
    object Category : ScreenRoutes("category/{tid}?name={name}") {
        fun createRoute(tid: Int, name: String): String {
            return "category/$tid?name=${encodeUrlComponentCompat(name)}"
        }
    }

    // [新增] 新手引导页面
    object Onboarding : ScreenRoutes("onboarding")
    
    // [新增] 私信相关页面
    object Inbox : ScreenRoutes("inbox")  // 收件箱
    object ReplyMe : ScreenRoutes("message/reply_me")
    object AtMe : ScreenRoutes("message/at_me")
    object LikeMe : ScreenRoutes("message/like_me")
    object SystemNotice : ScreenRoutes("message/system_notice")
    object Chat : ScreenRoutes("chat/{talkerId}/{sessionType}?name={name}") {
        fun createRoute(talkerId: Long, sessionType: Int, userName: String): String {
            return "chat/$talkerId/$sessionType?name=${encodeUrlComponentCompat(userName)}"
        }
    }
    
    // [新增] In-app Browser
    object Web : ScreenRoutes("web?url={url}&title={title}") {
        fun createRoute(url: String, title: String? = null, mobileUserAgent: Boolean = false): String {
            val encodedUrl = encodeUrlComponentCompat(url)
            val encodedTitle = title?.let(::encodeUrlComponentCompat) ?: ""
            return "web?url=$encodedUrl&title=$encodedTitle&mua=${if (mobileUserAgent) "1" else "0"}"
        }
    }
    
    // [新增] Audio Player
    object WeeklySeries : ScreenRoutes("weekly_series?number={number}") {
        fun createRoute(number: Int? = null): String =
            "weekly_series" + (number?.takeIf { it > 0 }?.let { "?number=$it" } ?: "")
    }

    object BgmDetail : ScreenRoutes("bgm_detail") {
        fun createRoute(musicId: String, aid: Long = 0, cid: Long = 0, showVideos: Boolean = false): String =
            "bgm_detail?musicId=${encodeUrlComponentCompat(musicId)}&aid=$aid&cid=$cid&showVideos=$showVideos"
    }

    object MusicDetail : ScreenRoutes("music/{sid}") {
        fun createRoute(sid: Long): String {
            return "music/$sid"
        }
    }
    
    // [新增] Native Music - 用于 MA 格式的原生音乐播放 (从视频 DASH 流提取音频)
    object NativeMusic : ScreenRoutes("native_music?title={title}&bvid={bvid}&cid={cid}") {
        fun createRoute(title: String, bvid: String, cid: Long): String {
            return "native_music?title=${encodeUrlComponentCompat(title)}&bvid=${encodeUrlComponentCompat(bvid)}&cid=$cid"
        }
    }
    companion object {
        fun createMusicRoute(musicId: String): String? {
            val id = musicId.trim()
            val auSid = id.removePrefix("au").removePrefix("AU").toLongOrNull()
            return when {
                auSid != null && auSid > 0 -> MusicDetail.createRoute(auSid)
                Regex("MA[0-9A-Za-z]+", RegexOption.IGNORE_CASE).matches(id) -> BgmDetail.createRoute(id)
                else -> null
            }
        }
    }

}
