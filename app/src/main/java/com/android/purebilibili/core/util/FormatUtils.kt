package com.android.purebilibili.core.util

object FormatUtils {
    /**
     * 将数字格式化为 B站风格 (例如: 1.2万)
     */
    fun formatStat(count: Long): String {
        return when {
            count >= 100000000 -> String.format("%.1f亿", count / 100000000.0)
            count >= 10000 -> String.format("%.1f万", count / 10000.0)
            else -> count.toString()
        }
    }

    /**
     * 将秒数格式化为 mm:ss
     */
    fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    /**
     * 修复图片 URL (核心修复)
     * 1. 补全 https 前缀
     * 2. 自动添加缩放后缀节省流量
     */
    fun fixImageUrl(url: String?): String {
        if (url.isNullOrEmpty()) return "" // 防止空指针

        var newUrl = url

        // 修复无协议头的链接 (//i0.hdslb.com...)
        if (newUrl.startsWith("//")) {
            newUrl = "https:$newUrl"
        }
        // 修复 http 链接
        if (newUrl.startsWith("http://")) {
            newUrl = newUrl.replace("http://", "https://")
        }

        // 如果没有后缀，加上缩放参数 (宽640, 高400)
        if (!newUrl.contains("@")) {
            newUrl = "$newUrl@640w_400h.webp"
        }
        return newUrl
    }
}