package com.android.purebilibili.data.model

/**
 * 评论反诈检测结果状态
 */
enum class CommentFraudStatus {
    /** 评论正常显示 */
    NORMAL,
    /** 仅自己可见 (ShadowBan) */
    SHADOW_BANNED,
    /** 发表账号查询时评论或所在楼层已失效 */
    DELETED,
    /** 游客在最新评论列表中未找到，但可打开回复页 */
    UNDER_REVIEW,
    /** 评论数据存在但前端隐藏（invisible，可能被 UP 拉黑） */
    INVISIBLE,
    /** 检测失败 / 未知 */
    UNKNOWN
}
