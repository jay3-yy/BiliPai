package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.network.policy.GithubMirrorSettings
import com.android.purebilibili.core.network.policy.GithubMirrorSource
import com.android.purebilibili.core.network.policy.normalizeGithubMirrorPrefix
import com.android.purebilibili.core.network.policy.resolveGithubMirrorPrefix

internal typealias AppUpdateMirror = GithubMirrorSource
internal typealias AppUpdateMirrorPreference = GithubMirrorSettings

internal fun normalizeAppUpdateMirrorPrefix(value: String): String? =
    normalizeGithubMirrorPrefix(value)

internal fun resolveAppUpdateMirrorUrl(
    source: AppUpdateMirror,
    customPrefix: String,
    assetUrl: String,
): String? {
    if (!assetUrl.startsWith("https://github.com/jay3-yy/BiliPai/releases/download/")) return null
    val prefix = resolveGithubMirrorPrefix(source, customPrefix) ?: return null
    return prefix + assetUrl
}

internal fun resolvePrimaryAppUpdateMirrorUrl(
    mirrorEnabled: Boolean,
    source: AppUpdateMirror,
    customPrefix: String,
    assetUrl: String,
    expectedSha256: String?,
): String? {
    if (!mirrorEnabled || !isAppUpdateSha256(expectedSha256)) return null
    return resolveAppUpdateMirrorUrl(source, customPrefix, assetUrl)
}

internal fun resolveGithubMirrorEnabledSubtitle(enabled: Boolean): String = if (enabled) {
    "优先通过镜像加速更新、装扮与壁纸归档等 GitHub 资源，失败时自动回退直连"
} else {
    "关闭后直连 GitHub；开启后可加速更新、装扮与壁纸归档等资源"
}

internal fun isAppUpdateSha256(value: String?): Boolean =
    value != null && value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
