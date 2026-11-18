package com.android.purebilibili.core.util

internal fun isBilibiliHost(host: String?): Boolean = isHostOrSubdomain(host, "bilibili.com")

internal fun isBilibiliShortHost(host: String?): Boolean = isHostOrSubdomain(host, "b23.tv")

internal fun isBilibiliWebHost(host: String?): Boolean = isBilibiliHost(host) || isBilibiliShortHost(host)

internal fun isBilibiliMediaHost(host: String?): Boolean =
    isBilibiliHost(host) || isHostOrSubdomain(host, "bilivideo.com") || isHostOrSubdomain(host, "bilivideo.cn")

private fun isHostOrSubdomain(host: String?, domain: String): Boolean {
    val canonical = host?.lowercase()?.trimEnd('.') ?: return false
    return canonical == domain || canonical.endsWith(".$domain")
}
