package com.android.purebilibili.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.purebilibili.data.model.CommentFraudStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 评论反诈全生命周期记录实体
 * 包含官方 ctime、初始状态、复检状态、UID、BV号及风控定性
 */
@Serializable
@Entity(tableName = "comment_fraud_records")
data class CommentFraudRecord(
    @PrimaryKey
    @SerialName("rpid")
    val rpid: Long,

    @SerialName("oid")
    val oid: Long,

    @SerialName("type")
    val type: Int = 1,

    @SerialName("root")
    val root: Long = 0L,

    @SerialName("parent")
    val parent: Long = 0L,

    @SerialName("uid")
    val uid: Long = 0L,

    @SerialName("source_id")
    val source_id: String? = null, // 例如 "BV1aQJV6CEyG" 或 "cv12345"

    @SerialName("origin_url")
    val origin_url: String? = null,

    @SerialName("message")
    val message: String = "",

    @SerialName("initial_status")
    val initial_status: String? = null, // 发评初检状态

    @SerialName("status")
    val status: String = "UNKNOWN", // 当前/最后复检状态

    @SerialName("post_time")
    val post_time: Long = 0L, // B站服务器官方 ctime (毫秒)，0 表示未记录

    @SerialName("timestamp")
    val timestamp: Long = System.currentTimeMillis() // 本地最后检查时间
) {
    /** 当前状态枚举 */
    val fraudStatus: CommentFraudStatus
        get() = parseStatus(status)

    /** 初始状态枚举 */
    val initialFraudStatus: CommentFraudStatus?
        get() = initial_status?.let { parseStatus(it) }

    /** 业务类型文本描述 */
    val typeName: String
        get() = when (type) {
            1 -> "视频 (type=1)"
            12 -> "专栏 (type=12)"
            11, 17 -> "动态 (type=$type)"
            else -> "业务 (type=$type)"
        }

    /** 
     * 风险定性分析
     * 对齐原作者的流程
     */
    val fraudAssessment: String
        get() {
            val init = initialFraudStatus
            val curr = fraudStatus
            val timeDiff = timestamp - post_time
            val days = if (post_time > 0L && timeDiff > 0L) (timeDiff / (1000L * 60 * 60 * 24)).toInt() else 0

            return when {
                // 0. 初检进行中
                curr == CommentFraudStatus.UNKNOWN -> "⏳ 状态检测中 (等待系统处理...)"

                // 1. 状态健康公开
                curr == CommentFraudStatus.NORMAL -> "🟢 评论正常显示 (路人视角可见)"

                // 2. 状态演化 (初检正常，后续复查异常)
                init == CommentFraudStatus.NORMAL && curr == CommentFraudStatus.SHADOW_BANNED -> {
                    if (days > 0) "⚠️ 秋后算账 (发评 $days 天后转为 shadowBan)"
                    else "⚠️ 延迟拦截 (初检正常，后转为 shadowBan)"
                }
                init == CommentFraudStatus.NORMAL && curr == CommentFraudStatus.DELETED -> {
                    if (days > 0) "⚠️ 评论已失效 (发评 $days 天后已被删除或清理)"
                    else "⚠️ 延迟失效 (初检正常，后已被删除或清理)"
                }

                // 3. 即时状态 / 固有状态
                curr == CommentFraudStatus.SHADOW_BANNED -> "🔴 评论被 shadowBan (仅自己可见)"
                curr == CommentFraudStatus.DELETED -> "⚫ 评论已失效 (已被删除或不存在)"
                curr == CommentFraudStatus.UNDER_REVIEW -> "🟡 评论疑似审核中 (处于先审后发队列)"
                curr == CommentFraudStatus.INVISIBLE -> "🟠 评论被软屏蔽 (Invisible: 前端强制隐藏)"

                else -> "⚪ 状态待确定"
            }
        }

    companion object {
        fun parseStatus(raw: String): CommentFraudStatus {
            return when (raw.lowercase().replace("_", "").replace("-", "")) {
                "normal", "ok" -> CommentFraudStatus.NORMAL
                "shadowban", "shadowbanned" -> CommentFraudStatus.SHADOW_BANNED
                "deleted", "del" -> CommentFraudStatus.DELETED
                "invisible", "inv" -> CommentFraudStatus.INVISIBLE
                "underreview", "review", "auditing" -> CommentFraudStatus.UNDER_REVIEW
                else -> CommentFraudStatus.UNKNOWN
            }
        }
    }
}
