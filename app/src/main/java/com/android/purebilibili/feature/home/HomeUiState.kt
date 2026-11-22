// 文件路径: feature/home/HomeUiState.kt
package com.android.purebilibili.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.purebilibili.R
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.LiveRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * 用户状态
 *  性能优化：@Immutable 告诉 Compose 此类不可变，减少不必要的重组
 */
@Immutable
data class UserState(
    val isLogin: Boolean = false,
    val face: String = "",
    val name: String = "",
    val mid: Long = 0,
    val level: Int = 0,
    val isSeniorMember: Boolean = false,
    val currentLevelMinExp: Int = 0,
    val currentLevelExp: Int = 0,
    val nextLevelExp: Int = 0,
    val coin: Double = 0.0,
    val bcoin: Double = 0.0,
    val following: Int = 0,
    val follower: Int = 0,
    val dynamic: Int = 0,
    val isVip: Boolean = false,
    /** Server vip.label.text, e.g. 大会员 / 年度大会员 */
    val vipLabel: String = "",
    /** vip.type: 1 monthly, 2 annual — used when [vipLabel] is empty */
    val vipType: Int = 0,
    /** nav.officialVerify.type: -1 无认证，0 个人认证，1 机构认证 — 头像角标用 */
    val officialVerifyType: Int = -1,
    //  [New] 顶部背景图
    val topPhoto: String = ""
)

/**
 * 首页分类枚举（含 Bilibili 分区 ID）
 */
enum class HomeCategory(val label: String, val tid: Int = 0) {
    RECOMMEND("推荐", 0),
    FOLLOW("关注", 0),    //  关注动态
    POPULAR("热门", 0),
    LIVE("直播", 0),
    ANIME("追番", 13),     // 番剧分区
    GAME("游戏", 4),       // 游戏分区
    KNOWLEDGE("知识", 36), // 知识分区
    TECH("科技", 188)      // 科技分区
}

/**
 * 直播子分类
 */
enum class LiveSubCategory(val label: String) {
    FOLLOWED("关注"),
    POPULAR("热门")
}

enum class PopularSubCategory(val label: String) {
    COMPREHENSIVE("综合热门"),
    RANKING("排行榜"),
    WEEKLY("每周必看"),
    PRECIOUS("入站必刷")
}

enum class TodayWatchMode(val label: String) {
    RELAX("今晚轻松看"),
    LEARN("深度学习看")
}

internal fun resolveHomeCategoryLabelRes(category: HomeCategory): Int = when (category) {
    HomeCategory.RECOMMEND -> R.string.home_category_recommend
    HomeCategory.FOLLOW -> R.string.home_category_follow
    HomeCategory.POPULAR -> R.string.home_category_popular
    HomeCategory.LIVE -> R.string.home_category_live
    HomeCategory.ANIME -> R.string.home_category_anime
    HomeCategory.GAME -> R.string.home_category_game
    HomeCategory.KNOWLEDGE -> R.string.home_category_knowledge
    HomeCategory.TECH -> R.string.home_category_tech
}

internal fun resolveLiveSubCategoryLabelRes(subCategory: LiveSubCategory): Int = when (subCategory) {
    LiveSubCategory.FOLLOWED -> R.string.home_live_subcategory_followed
    LiveSubCategory.POPULAR -> R.string.home_live_subcategory_popular
}

internal fun resolvePopularSubCategoryLabelRes(subCategory: PopularSubCategory): Int = when (subCategory) {
    PopularSubCategory.COMPREHENSIVE -> R.string.home_popular_subcategory_comprehensive
    PopularSubCategory.RANKING -> R.string.home_popular_subcategory_ranking
    PopularSubCategory.WEEKLY -> R.string.home_popular_subcategory_weekly
    PopularSubCategory.PRECIOUS -> R.string.home_popular_subcategory_precious
}

internal fun resolveTodayWatchModeLabelRes(mode: TodayWatchMode): Int = when (mode) {
    TodayWatchMode.RELAX -> R.string.home_today_watch_mode_relax
    TodayWatchMode.LEARN -> R.string.home_today_watch_mode_learn
}

@Immutable
data class TodayUpRank(
    val mid: Long,
    val name: String,
    val score: Double,
    val watchCount: Int
)

@Immutable
data class TodayWatchPlan(
    val mode: TodayWatchMode = TodayWatchMode.RELAX,
    val upRanks: ImmutableList<TodayUpRank> = persistentListOf(),
    val videoQueue: ImmutableList<VideoItem> = persistentListOf(),
    val explanationByBvid: ImmutableMap<String, String> = persistentMapOf(),
    val scoreByBvid: ImmutableMap<String, Double> = persistentMapOf(),
    val confidenceByBvid: ImmutableMap<String, Float> = persistentMapOf(),
    val historySampleCount: Int = 0,
    val nightSignalUsed: Boolean = false,
    val generatedAt: Long = 0L
)

@Immutable
data class TodayWatchCardUiConfig(
    val showUpRank: Boolean = true,
    val showReasonHint: Boolean = true,
    val queuePreviewLimit: Int = 6,
    val enableWaterfallAnimation: Boolean = true,
    val waterfallExponent: Float = 1.38f
)

/**
 * 单个分类的内容状态 (新增)
 */
@Stable
data class CategoryContent(
    val videos: ImmutableList<VideoItem> = persistentListOf(),
    val liveRooms: ImmutableList<LiveRoom> = persistentListOf(),
    val followedLiveRooms: ImmutableList<LiveRoom> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pageIndex: Int = 1, //  保存分页索引
    val hasMore: Boolean = true, //  保存是否还有更多数据
    val loadMoreError: String? = null,
    val refreshError: String? = null,
)

/**
 * 首页 UI 状态
 *  性能优化：@Stable 告诉 Compose 此类字段变化可被追踪，优化重组
 */
@Stable
data class HomeUiState(
    // 兼容旧字段便于迁移（将被 categoryStates 替代）
    val videos: ImmutableList<VideoItem> = persistentListOf(),
    val liveRooms: ImmutableList<LiveRoom> = persistentListOf(),  // 热门直播
    val followedLiveRooms: ImmutableList<LiveRoom> = persistentListOf(),  // 🔴 [新增] 关注的主播直播
    val isLoading: Boolean = false,
    val error: String? = null,
    
    // 📺 [核心变更] 各分类独立状态缓存
    // 使用 Map 保存每个分类的数据，切换时直接读取
    val categoryStates: ImmutableMap<HomeCategory, CategoryContent> = persistentMapOf(),
    // 热门子页签需要独立缓存，切换时保留旧页内容并让 Pager 负责横向位移。
    val popularCategoryStates: ImmutableMap<PopularSubCategory, CategoryContent> = persistentMapOf(),
    
    val user: UserState = UserState(),
    val currentCategory: HomeCategory = HomeCategory.RECOMMEND,
    val liveSubCategory: LiveSubCategory = LiveSubCategory.FOLLOWED,
    val popularSubCategory: PopularSubCategory = PopularSubCategory.COMPREHENSIVE,
    val refreshKey: Long = 0L,
    val followingMids: ImmutableSet<Long> = persistentSetOf(),
    val messageUnreadCount: Int = 0,
    //  [新增] 标签页显示索引（独立于内容分类，用于特殊分类导航后保持标签位置）
    val displayedTabIndex: Int = 0,
    // 稳定记录当前展示的顶栏页签身份，避免标签重排后仅靠数字索引误判到分区/订阅或其他分类。
    val displayedTabEntry: HomeTopTabEntry = HomeTopTabEntry.Category(currentCategory),
    //  [彩蛋] 刷新成功后的趣味消息
    val refreshMessage: String? = null,
    //  [新增] 增量刷新新增条数（null 表示不展示）
    val refreshNewItemsCount: Int? = null,
    //  [新增] 新增条数提示触发键（用于一次性 UI 动效）
    val refreshNewItemsKey: Long = 0L,
    //  [新增] 新增条数提示已消费键（防止离开页面后重复弹出）
    val refreshNewItemsHandledKey: Long = 0L,
    //  [新增] 推荐流旧内容锚点（刷新前首条视频 bvid）
    val recommendOldContentAnchorBvid: String? = null,
    //  [新增] 推荐流中“旧内容起始”索引（用于插入分割线）
    val recommendOldContentStartIndex: Int? = null,
    //  [新增] 推荐流旧内容分割线已解锁的刷新键
    val recommendOldContentRevealKey: Long = 0L,
    //  [新增] 正在消散动画中的视频 BVIDs（动画完成后移除）
    val dissolvingVideos: ImmutableSet<String> = persistentSetOf(),
    //  [新增] 今日看什么推荐单
    val todayWatchMode: TodayWatchMode = TodayWatchMode.RELAX,
    val todayWatchPlan: TodayWatchPlan? = null,
    val todayWatchLoading: Boolean = false,
    val todayWatchError: String? = null,
    val todayWatchPluginEnabled: Boolean = false,
    val todayWatchCollapsed: Boolean = false,
    val todayWatchCardConfig: TodayWatchCardUiConfig = TodayWatchCardUiConfig(),
    //  [新增] 刷新撤销状态
    val undoAvailable: Boolean = false
)
