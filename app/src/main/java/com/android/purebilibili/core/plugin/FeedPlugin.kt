// 文件路径: core/plugin/FeedPlugin.kt
package com.android.purebilibili.core.plugin

import com.android.purebilibili.data.model.response.VideoItem

/**
 * 📰 信息流处理插件接口
 * 
 * 用于实现首页推荐流的增强功能，如：
 * - 过滤广告
 * - 过滤推广内容
 * - 自定义过滤规则
 */
interface FeedPlugin : Plugin {
    
    /**
     * 判断是否显示该推荐项
     * 
     * @param item 推荐项数据
     * @return true 表示显示，false 表示隐藏
     */
    fun shouldShowItem(item: VideoItem): Boolean

    /**
     * 带信息流来源的判断(默认委托给 [shouldShowItem] 以保持向后兼容)。
     *
     * 需要按来源区分行为(如 PiliNara 的「过滤器也应用于热门/排行/搜索」开关)时覆写此方法。
     */
    fun shouldShowItem(item: VideoItem, feedKind: FeedKind): Boolean = shouldShowItem(item)
}

/**
 * 信息流来源类型，供 FeedPlugin 按来源区分过滤行为。
 */
enum class FeedKind {
    /** 未标注来源(默认) */
    GENERIC,
    /** 首页推荐流 */
    HOME_RECOMMEND,
    /** 热门 */
    HOME_POPULAR,
    /** 排行榜 */
    HOME_RANK,
    /** 分区 */
    HOME_REGION,
    /** 搜索结果 */
    SEARCH
}
