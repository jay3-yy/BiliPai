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
    preferSystemPredictiveBack: Boolean = false,
): Int? {
    val preferredTypes = when {
        !motionEnabled -> listOf("VIEW_ANIM_NONE", "VIEW_ANIM_FOLLOW_SYSTEM")
        !sourceAvailable -> listOf(
            "VIEW_ANIM_FOLLOW_SYSTEM", "VIEW_ANIM_FADE_OUT", "VIEW_ANIM_EXPLODE_OUT", "VIEW_ANIM_NONE",
        )
        preferSystemPredictiveBack -> listOf("VIEW_ANIM_FOLLOW_SYSTEM", "VIEW_ANIM_DEFAULT")
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
    sourceRadiusUnchanged: Boolean,
    preferSystemPredictiveBack: Boolean,
): OfficialVideoCardReturnPlan? {
    if (oplusSdkWindow && !preparingToFinish) return null
    val usableSource = sourceAvailable && (!oplusSdkWindow || sourceRadiusUnchanged)
    val useSourceBounds = motionEnabled && usableSource
    return OfficialVideoCardReturnPlan(
        backAnimType = resolveOfficialReturnAnimType(
            metadata, motionEnabled, usableSource,
            // 此门面的 FOLLOW_SYSTEM 会跳过回卡，正常来源仍交给其原生开合执行器。
            preferSystemPredictiveBack = preferSystemPredictiveBack && !oplusSdkWindow,
        ),
        useSourceBounds = useSourceBounds,
        updateRadius = useSourceBounds && !oplusSdkWindow,
        updateEffects = !oplusSdkWindow,
    )
}
