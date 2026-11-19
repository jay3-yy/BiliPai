package com.android.purebilibili.feature.video

import com.itgsa.animation.sharedview.ViewAnimationType

/** 第二段机器可读声明优先；旧描述未声明坐标系时按协议使用屏幕坐标。 */
internal fun officialBoundsUseWindowCoordinates(metadata: String?): Boolean {
    val token = metadata?.split('|')?.getOrNull(1)?.trim()
    if (token.equals("WINDOW", ignoreCase = true)) return true
    if (token.equals("SCREEN", ignoreCase = true)) return false
    return metadata?.contains("WINDOW", ignoreCase = true) == true
}

/** 只使用设备声明可写的参数；旧版 int 声明允许公开合法值。 */
internal fun supportsOfficialBackType(metadata: String?, type: String): Boolean {
    if (metadata == null) return false
    val names = metadata.split('|').getOrNull(1)?.split(',')?.map(String::trim).orEmpty()
    val declared = names.filter { it.startsWith("VIEW_ANIM_") }
    return declared.isEmpty() || type in declared
}

/** 来源失效后只选择不依赖卡片落点的退场；未声明可用类型时保留设备策略。 */
internal fun resolveOfficialReturnAnimType(
    metadata: String?,
    motionEnabled: Boolean,
    sourceAvailable: Boolean,
): Int? {
    val preferredTypes = when {
        !motionEnabled -> listOf("VIEW_ANIM_NONE", "VIEW_ANIM_FOLLOW_SYSTEM")
        !sourceAvailable -> listOf(
            "VIEW_ANIM_FOLLOW_SYSTEM", "VIEW_ANIM_FADE_OUT", "VIEW_ANIM_EXPLODE_OUT", "VIEW_ANIM_NONE",
        )
        // 已有有效落点时，设备未声明 DEFAULT 可写就保留打开时的返回配置。
        else -> listOf("VIEW_ANIM_DEFAULT")
    }
    return when (preferredTypes.firstOrNull { supportsOfficialBackType(metadata, it) }) {
        "VIEW_ANIM_NONE" -> ViewAnimationType.VIEW_ANIM_NONE
        "VIEW_ANIM_FOLLOW_SYSTEM" -> ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM
        "VIEW_ANIM_FADE_OUT" -> ViewAnimationType.VIEW_ANIM_FADE_OUT
        "VIEW_ANIM_EXPLODE_OUT" -> ViewAnimationType.VIEW_ANIM_EXPLODE_OUT
        "VIEW_ANIM_DEFAULT" -> ViewAnimationType.VIEW_ANIM_DEFAULT
        else -> null
    }
}

internal data class OfficialVideoCardReturnPlan(
    val backAnimType: Int?,
    val useSourceBounds: Boolean,
    val updateRadius: Boolean,
    val updateEffects: Boolean,
)

/** 此门面的默认类型不清除跳过标记；只在关闭前提交返回控制，且不更新仅支持打开的字段。 */
internal fun resolveOfficialVideoCardReturnPlan(
    metadata: String?,
    oplusSdkWindow: Boolean,
    preparingToFinish: Boolean,
    motionEnabled: Boolean,
    sourceAvailable: Boolean,
): OfficialVideoCardReturnPlan? {
    // 系统保留打开时的往返配置；播放与恢复过程中不改写，关闭时按最终状态提交。
    if (!preparingToFinish) return null
    val useSourceBounds = motionEnabled && sourceAvailable
    return OfficialVideoCardReturnPlan(
        backAnimType = resolveOfficialReturnAnimType(metadata, motionEnabled, sourceAvailable),
        useSourceBounds = useSourceBounds,
        updateRadius = useSourceBounds && !oplusSdkWindow,
        updateEffects = !oplusSdkWindow,
    )
}
