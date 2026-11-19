package com.android.purebilibili.navigation3

import com.android.purebilibili.feature.settings.isSettingsSubtreeRoute

internal fun resolveInitialBiliPaiBackStack(
    openPortraitFeedOnStartup: Boolean = false
): List<BiliPaiNavKey> {
    if (openPortraitFeedOnStartup) {
        return listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.Story())
    }
    return listOf(BiliPaiNavKey.MainHost)
}

internal fun pushBiliPaiNavKey(
    currentStack: List<BiliPaiNavKey>,
    key: BiliPaiNavKey
): List<BiliPaiNavKey> {
    val base = currentStack.ifEmpty { listOf(BiliPaiNavKey.MainHost) }
    val existingIndex = base.indexOfLast { existing ->
        areSameReusableBiliPaiDestination(existing, key)
    }
    return if (existingIndex >= 0) {
        // Each entry on the back stack needs a unique content key. Reopening a
        // singleton destination (for example Search from a video that was opened from Search)
        // returns to its existing instance instead of producing [Search, VideoDetail, Search].
        base.take(existingIndex + 1)
    } else {
        base + key
    }
}

/**
 * Navigation identity for destinations whose route carries transient presentation arguments.
 *
 * [BiliPaiNavKey.Space.targetBvid] only asks a newly opened space page to locate/highlight a
 * video. It must not make the same UP's space a second destination: Space -> Video -> same Space
 * would otherwise retain two blur sources and an outgoing touch layer during the transition.
 */
internal fun areSameReusableBiliPaiDestination(
    existing: BiliPaiNavKey,
    target: BiliPaiNavKey,
): Boolean {
    return when {
        existing is BiliPaiNavKey.Space && target is BiliPaiNavKey.Space ->
            existing.mid == target.mid
        else -> existing == target
    }
}

/**
 * 平板/侧栏切换 Category：同层 replace，避免 Category 叠 Category，或详情页上再叠一层 Category。
 *
 * - 栈内已有目标 Category → 返回已有条目，移除其上的搜索页或详情页，避免重复 contentKey
 * - 栈顶是 SettingsCategory 或其他设置子树详情 → 裁掉设置子树尾段后 push
 * - 栈顶是 Settings 根 / Search / 非设置页 → 普通 push
 */
internal fun pushOrReplaceSettingsCategoryNavKey(
    currentStack: List<BiliPaiNavKey>,
    key: BiliPaiNavKey.SettingsCategory,
): List<BiliPaiNavKey> {
    val base = currentStack.ifEmpty { listOf(BiliPaiNavKey.MainHost) }
    // Category navigation bypasses pushBiliPaiNavKey, so it must enforce the same
    // uniqueness rule before any replacement/append branch. In particular,
    // Category -> SettingsSearch -> the same Category must pop back to the existing page.
    val existingIndex = base.indexOf(key)
    if (existingIndex >= 0) return base.take(existingIndex + 1)

    val top = base.lastOrNull()
    if (top is BiliPaiNavKey.SettingsCategory) {
        return base.dropLast(1) + key
    }
    if (
        top != null &&
        top !is BiliPaiNavKey.Settings &&
        top !is BiliPaiNavKey.SettingsSearch &&
        isSettingsSubtreeRoute(top.routeBase)
    ) {
        var trimmed = base
        while (trimmed.size > 1) {
            val last = trimmed.last()
            if (last is BiliPaiNavKey.Settings || last == BiliPaiNavKey.MainHost) break
            if (!isSettingsSubtreeRoute(last.routeBase)) break
            trimmed = trimmed.dropLast(1)
        }
        return trimmed + key
    }
    return base + key
}

internal fun popBiliPaiNavKey(
    currentStack: List<BiliPaiNavKey>
): List<BiliPaiNavKey> {
    return if (currentStack.size <= 1) currentStack else currentStack.dropLast(1)
}

/**
 * 弹出栈顶所有非 [BiliPaiNavKey.MainHost] 条目，恢复到根。常用于「返回首页」入口：
 * 把视频详情（以及中间夹杂的搜索、登录等）一次性清理掉，由 popTransitionSpec 一次播放横向过渡。
 *
 * - 栈为空 → 维持空（调用方会兜底为 `[MainHost]`）
 * - 栈底已经是 MainHost 且只剩它 → 维持原样
 * - 栈底不是 MainHost（异常态）→ 维持原样，避免误删
 */
internal fun popBiliPaiNavKeyToRoot(
    currentStack: List<BiliPaiNavKey>
): List<BiliPaiNavKey> {
    if (currentStack.isEmpty()) return currentStack
    val root = currentStack.first()
    if (root != BiliPaiNavKey.MainHost) return currentStack
    return listOf(root)
}
