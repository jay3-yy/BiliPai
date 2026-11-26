package com.android.purebilibili.feature.widget

import com.android.purebilibili.feature.personal.CompletionKeepsakeRecord

data class KeepsakeWidgetDisplayModel(
    val greeting: String,
    val badge: String,
    val bodyText: String,
    val footerText: String,
    val targetBvid: String = "",
    val isKeepsake: Boolean = false,
)

private val FALLBACK_DAILY_QUOTES = listOf(
    "慢慢看，喜欢的作品值得留一点安静的时间。",
    "挑一部下饭视频，或者接着听完昨晚没听完的那一期。",
    "好故事不需要赶进度，今天也按自己的节奏看就好。",
    "把心动的一帧和完结时的感想留下来，以后再翻会很有意思。",
    "累的时候就放一段白噪音，让耳机陪你歇一会儿。",
)

internal const val KEEPSAKE_WIDGET_RECAP_DEEP_LINK = "bilipai://personal_recap"

internal fun resolveKeepsakeWidgetDeepLink(display: KeepsakeWidgetDisplayModel): String? =
    if (display.targetBvid.isBlank()) KEEPSAKE_WIDGET_RECAP_DEEP_LINK else null

internal fun resolveKeepsakeWidgetGreeting(hourOfDay: Int): String = when (hourOfDay.coerceIn(0, 23)) {
    in 5..10 -> "早安"
    in 11..13 -> "午安"
    in 14..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了，早点休息"
}

internal fun pickKeepsakeWidgetDisplayModel(
    records: List<CompletionKeepsakeRecord>,
    epochDay: Long,
    hourOfDay: Int,
    manualOffset: Int = 0,
): KeepsakeWidgetDisplayModel {
    val greeting = resolveKeepsakeWidgetGreeting(hourOfDay)
    val withReflection = records.filter { it.reflection.isNotBlank() }
    val pool = if (withReflection.isNotEmpty()) withReflection else records

    if (pool.isNotEmpty()) {
        val index = Math.floorMod((epochDay + manualOffset).toInt(), pool.size)
        val picked = pool[index]
        val reflection = picked.reflection.trim()
        val bodyText = if (reflection.isNotEmpty()) {
            "“$reflection”"
        } else {
            "共看完 ${picked.episodeCount} 集，这一程已经收进纪念册。"
        }
        return KeepsakeWidgetDisplayModel(
            greeting = greeting,
            badge = picked.kind.badgeLabel,
            bodyText = bodyText,
            footerText = "${picked.title} · 共 ${picked.episodeCount} 集",
            targetBvid = picked.bvid,
            isKeepsake = true,
        )
    }

    val quoteIndex = Math.floorMod((epochDay + manualOffset).toInt(), FALLBACK_DAILY_QUOTES.size)
    return KeepsakeWidgetDisplayModel(
        greeting = greeting,
        badge = "每日一句",
        bodyText = FALLBACK_DAILY_QUOTES[quoteIndex],
        footerText = "BiliPai · 追完合集或番剧即可留下完结纪念",
        targetBvid = "",
        isKeepsake = false,
    )
}
