package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.SpaceElecBlock
import com.android.purebilibili.data.model.response.SpaceGuardBlock
import com.android.purebilibili.data.model.response.SpaceSupportersData

/**
 * 头部「N人为TA充电 / N人加入大航海」行的展示数据（对齐 PiliPlus 的 charge/guard 行）：
 * 前 3 个支持者头像堆叠 + 总数文案。
 */
@androidx.compose.runtime.Immutable
data class SpaceSupporterGroup(
    val count: Long,
    val avatarUrls: List<String>
)

internal fun resolveSpaceSupporterGroups(
    data: SpaceSupportersData?,
): Pair<SpaceSupporterGroup?, SpaceSupporterGroup?> {
    if (data == null) return null to null
    return resolveSpaceChargeGroup(data.elec) to resolveSpaceGuardGroup(data.guard)
}

internal fun resolveSpaceChargeGroup(block: SpaceElecBlock?): SpaceSupporterGroup? {
    val count = block?.total ?: 0L
    if (count <= 0L) return null
    return SpaceSupporterGroup(
        count = count,
        avatarUrls = block?.list.orEmpty().mapNotNull { user ->
            (user.avatar.ifBlank { user.face }).takeIf { it.isNotBlank() }
        }
    )
}

internal fun resolveSpaceGuardGroup(block: SpaceGuardBlock?): SpaceSupporterGroup? {
    if (block == null) return null
    // App 接口的 guard.count 类型不稳定，PiliPlus 从 desc（如 "320人加入了大航海"）取前导数字。
    val count = Regex("^\\d+").find(block.desc)?.value?.toLongOrNull() ?: 0L
    if (count <= 0L) return null
    return SpaceSupporterGroup(
        count = count,
        avatarUrls = block.item.mapNotNull { user ->
            (user.face.ifBlank { user.avatar }).takeIf { it.isNotBlank() }
        }
    )
}
