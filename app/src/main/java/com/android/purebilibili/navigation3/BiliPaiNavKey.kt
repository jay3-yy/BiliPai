package com.android.purebilibili.navigation3

import com.android.purebilibili.feature.settings.SettingsRootCategory
import com.android.purebilibili.data.model.response.FavoriteSearchScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
@JsonClassDiscriminator("nav_key_class")
internal sealed interface BiliPaiNavKey : NavKey {
    val routeBase: String

    @Serializable
    data object MainHost : BiliPaiNavKey {
        override val routeBase: String = "main_host"
    }

    @Serializable
    data object Home : BiliPaiNavKey {
        override val routeBase: String = "home"
    }

    @Serializable
    data object ListenVideo : BiliPaiNavKey {
        override val routeBase: String = "listen_video"
    }

    @Serializable
    data object Dynamic : BiliPaiNavKey {
        override val routeBase: String = "dynamic"
    }

    @Serializable
    data class Search(
        val keyword: String = "",
        val openId: Long = 0L,
    ) : BiliPaiNavKey {
        override val routeBase: String = "search"

        companion object : BiliPaiNavKey {
            override val routeBase: String = "search"
        }
    }

    @Serializable
    data object SearchTrending : BiliPaiNavKey {
        override val routeBase: String = "search_trending"
    }

    @Serializable
    data class TopicDetail(
        val topicId: Long
    ) : BiliPaiNavKey {
        override val routeBase: String = "topic"
    }

    @Serializable
    data object Settings : BiliPaiNavKey {
        override val routeBase: String = "settings"
    }

    @Serializable
    data class SettingsCategory(
        val category: SettingsRootCategory,
    ) : BiliPaiNavKey {
        override val routeBase: String = "settings_category"
    }

    @Serializable
    data object SettingsSearch : BiliPaiNavKey {
        override val routeBase: String = "settings_search"
    }

    @Serializable
    data object OpenSourceLicenses : BiliPaiNavKey {
        override val routeBase: String = "open_source_licenses"
    }

    @Serializable
    data object AppearanceSettings : BiliPaiNavKey {
        override val routeBase: String = "appearance_settings"
    }

    @Serializable
    data object HomeSettings : BiliPaiNavKey {
        override val routeBase: String = "home_settings"
    }

    @Serializable
    data object IconSettings : BiliPaiNavKey {
        override val routeBase: String = "icon_settings"
    }

    @Serializable
    data object AnimationSettings : BiliPaiNavKey {
        override val routeBase: String = "animation_settings"
    }

    @Serializable
    data object PlaybackSettings : BiliPaiNavKey {
        override val routeBase: String = "playback_settings"
    }

    @Serializable
    data object PermissionSettings : BiliPaiNavKey {
        override val routeBase: String = "permission_settings"
    }
    @Serializable
    data object MessageNotificationSettings : BiliPaiNavKey {
        override val routeBase: String = "message_notification_settings"
    }

    @Serializable
    data class PluginsSettings(
        val importUrl: String? = null
    ) : BiliPaiNavKey {
        override val routeBase: String = "plugins_settings"
    }

    @Serializable
    data class JsPluginContent(
        val pluginId: String
    ) : BiliPaiNavKey {
        override val routeBase: String = "js_plugin"
    }

    @Serializable
    data class ExternalMedia(
        val launchId: String
    ) : BiliPaiNavKey {
        override val routeBase: String = "external_media"
    }

    @Serializable
    data object BottomBarSettings : BiliPaiNavKey {
        override val routeBase: String = "bottom_bar_settings"
    }

    @Serializable
    data object SettingsShare : BiliPaiNavKey {
        override val routeBase: String = "settings_share"
    }

    @Serializable
    data object WebDavBackup : BiliPaiNavKey {
        override val routeBase: String = "webdav_backup"
    }

    @Serializable
    data object TipsSettings : BiliPaiNavKey {
        override val routeBase: String = "tips_settings"
    }

    @Serializable
    data object Login : BiliPaiNavKey {
        override val routeBase: String = "login"
    }

    @Serializable
    data object Profile : BiliPaiNavKey {
        override val routeBase: String = "profile"
    }

    @Serializable
    data class AicuQuery(val uid: Long = 0L, val category: String = "COMMENT") : BiliPaiNavKey {
        override val routeBase: String = "aicu"
    }

    @Serializable
    data object History : BiliPaiNavKey {
        override val routeBase: String = "history"
    }

    @Serializable
    data class HistorySearch(val query: String = "") : BiliPaiNavKey {
        override val routeBase: String = "history_search"
    }

    @Serializable
    data object Favorite : BiliPaiNavKey {
        override val routeBase: String = "favorite"
    }

    @Serializable
    data object FavoriteSubscribed : BiliPaiNavKey {
        override val routeBase: String = "favorite_subscribed"
    }

    @Serializable
    data class FavoriteSearch(
        val query: String = "",
        val scope: FavoriteSearchScope = FavoriteSearchScope.CURRENT_FOLDER,
    ) : BiliPaiNavKey {
        override val routeBase: String = "favorite_search"
    }

    @Serializable
    data object LikedVideos : BiliPaiNavKey {
        override val routeBase: String = "liked_videos"
    }

    @Serializable
    data object WatchLater : BiliPaiNavKey {
        override val routeBase: String = "watch_later"
    }

    @Serializable
    data class WatchLaterSearch(val query: String = "") : BiliPaiNavKey {
        override val routeBase: String = "watch_later_search"
    }

    @Serializable
    data object Onboarding : BiliPaiNavKey {
        override val routeBase: String = "onboarding"
    }

    @Serializable
    data class Following(
        val mid: Long
    ) : BiliPaiNavKey {
        override val routeBase: String = "following"
    }

    @Serializable
    data object DownloadList : BiliPaiNavKey {
        override val routeBase: String = "download_list"
    }

    @Serializable
    data class OfflineVideoPlayer(
        val taskId: String
    ) : BiliPaiNavKey {
        override val routeBase: String = "offline_video"
    }

    @Serializable
    data object LiveList : BiliPaiNavKey {
        override val routeBase: String = "live_list"
    }

    @Serializable
    data object LiveSearch : BiliPaiNavKey {
        override val routeBase: String = "live_search"
    }

    @Serializable
    data object LiveArea : BiliPaiNavKey {
        override val routeBase: String = "live_area"
    }

    @Serializable
    data class LiveAreaDetail(
        val parentAreaId: Int,
        val areaId: Int,
        val title: String = "",
        /** 每次进入分配自增 id，避免同级分区互跳后同一分区重复入栈时 contentKey 冲突。 */
        val openId: Long = 0L
    ) : BiliPaiNavKey {
        override val routeBase: String = "live_area_detail"
    }

    @Serializable
    data object LiveFollowing : BiliPaiNavKey {
        override val routeBase: String = "live_following"
    }

    @Serializable
    data object Inbox : BiliPaiNavKey {
        override val routeBase: String = "inbox"
    }

    @Serializable
    data object ReplyMe : BiliPaiNavKey {
        override val routeBase: String = "message/reply_me"
    }

    @Serializable
    data object AtMe : BiliPaiNavKey {
        override val routeBase: String = "message/at_me"
    }

    @Serializable
    data object LikeMe : BiliPaiNavKey {
        override val routeBase: String = "message/like_me"
    }

    @Serializable
    data object SystemNotice : BiliPaiNavKey {
        override val routeBase: String = "message/system_notice"
    }

    @Serializable
    data class Chat(
        val talkerId: Long,
        val sessionType: Int,
        val userName: String = ""
    ) : BiliPaiNavKey {
        override val routeBase: String = "chat"
    }

    @Serializable
    data object Partition : BiliPaiNavKey {
        override val routeBase: String = "partition"
    }

    @Serializable
    data class Story(
        val seedBvid: String = "",
        val seedCid: Long = 0L,
        val seedCover: String = "",
        val seedTitle: String = "",
        val sourceRoute: String? = null,
        /** 每次从卡片直达时刷新，避免 SaveableState 复用坏掉的播放器会话。 */
        val openId: Long = 0L
    ) : BiliPaiNavKey {
        override val routeBase: String = "story"
    }

    @Serializable
    data class AudioMode(
        val sourceBvid: String = "",
        val sourceCid: Long = 0L,
        val sourceResumePositionMs: Long = 0L
    ) : BiliPaiNavKey {
        override val routeBase: String = "audio_mode"
    }

    @Serializable
    data class SeasonSeriesDetail(
        val type: String,
        val id: Long,
        val mid: Long,
        val title: String = "",
        val ownerName: String = "",
        val sharedElementTransition: Boolean = false
    ) : BiliPaiNavKey {
        override val routeBase: String = "season_series_detail"
    }

    @Serializable
    data class Bangumi(
        val initialType: Int = 1
    ) : BiliPaiNavKey {
        override val routeBase: String = "bangumi"
    }

    @Serializable
    data class BangumiPlayer(
        val seasonId: Long,
        val epId: Long,
        val resumePositionMs: Long = 0L
    ) : BiliPaiNavKey {
        override val routeBase: String = "bangumi/play"
    }

    @Serializable
    data class MusicDetail(
        val sid: Long
    ) : BiliPaiNavKey {
        override val routeBase: String = "music"
    }

    @Serializable
    data class NativeMusic(
        val title: String,
        val bvid: String,
        val cid: Long
    ) : BiliPaiNavKey {
        override val routeBase: String = "native_music"
    }

    @Serializable
    data class VideoDetail(
        val bvid: String,
        val cid: Long = 0L,
        val coverUrl: String = "",
        val startAudio: Boolean = false,
        val autoPortrait: Boolean = false,
        val fullscreen: Boolean = false,
        val resumePositionMs: Long = 0L,
        val commentRootRpid: Long = 0L,
        val commentTargetRpid: Long = 0L,
        val initialVertical: Boolean = false,
        /** 「竖屏直达」+ 卡片过渡：放大进详情壳后立刻进 standalone 竖屏全屏，不进内联详情。 */
        val directPortraitEntry: Boolean = false,
        val sourceRoute: String? = null,
        /** 每次进入详情时刷新，避免同一视频复用已退出会话的 SaveableState。 */
        val openId: Long = 0L,
    ) : BiliPaiNavKey {
        override val routeBase: String = "video"
    }

    @Serializable
    data class ArticleDetail(
        val articleId: Long,
        val title: String = ""
    ) : BiliPaiNavKey {
        override val routeBase: String = "article"
    }

    @Serializable
    data class DynamicDetail(
        val dynamicId: String,
        val commentRootRpid: Long = 0L,
        val commentTargetRpid: Long = 0L
    ) : BiliPaiNavKey {
        override val routeBase: String = "dynamic_detail"
    }

    @Serializable
    data class Space(
        val mid: Long,
        val targetBvid: String = ""
    ) : BiliPaiNavKey {
        override val routeBase: String = "space"
    }

    @Serializable
    data class Category(
        val tid: Int,
        val name: String = ""
    ) : BiliPaiNavKey {
        override val routeBase: String = "category"
    }

    @Serializable
    data class Live(
        val siteId: String = "bilibili",
        val roomId: String,
        val title: String = "",
        val uname: String = ""
    ) : BiliPaiNavKey {
        override val routeBase: String = "live"
    }

    @Serializable
    data class BangumiDetail(
        val seasonId: Long,
        val epId: Long = 0L,
        val mediaId: Long = 0L
    ) : BiliPaiNavKey {
        override val routeBase: String = "bangumi"
    }

    @Serializable
    data class BangumiReview(
        val mediaId: Long,
        val title: String = ""
    ) : BiliPaiNavKey {
        override val routeBase: String = "bangumi_review"
    }

    @Serializable
    data class Web(
        val url: String,
        val title: String = ""
    ) : BiliPaiNavKey {
        override val routeBase: String = "web"
    }

    @Serializable
    data class Unknown(
        val route: String
    ) : BiliPaiNavKey {
        override val routeBase: String = route.substringBefore("?").substringBefore("/")
    }
}
