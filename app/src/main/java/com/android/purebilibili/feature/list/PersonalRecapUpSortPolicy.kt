package com.android.purebilibili.feature.list

internal enum class PersonalRecapUpSortOrder(val label: String, val shareLabel: String) {
    VIDEO_COUNT("按视频数", "视频数占比"),
    ESTIMATED_DURATION("按估算观看时长", "估算时长占比"),
}

internal fun sortPersonalRecapUps(
    ups: List<PersonalUpRecap>,
    order: PersonalRecapUpSortOrder,
): List<PersonalUpRecap> = ups.sortedWith(
    when (order) {
        PersonalRecapUpSortOrder.VIDEO_COUNT ->
            compareByDescending<PersonalUpRecap> { it.watchCount }.thenByDescending { it.totalDurationSec }
        PersonalRecapUpSortOrder.ESTIMATED_DURATION ->
            compareByDescending<PersonalUpRecap> { it.totalDurationSec }.thenByDescending { it.watchCount }
    }.thenBy { it.mid },
)

/** 分母包含当前时段全部已统计视频；没有对应总量时不生成占比。 */
internal fun personalRecapUpShare(
    up: PersonalUpRecap,
    stats: PersonalVideoRecapStats,
    order: PersonalRecapUpSortOrder,
): Float? = when (order) {
    PersonalRecapUpSortOrder.VIDEO_COUNT ->
        if (stats.videoCount > 0) up.watchCount.toFloat() / stats.videoCount else null
    PersonalRecapUpSortOrder.ESTIMATED_DURATION ->
        if (stats.totalDurationSec > 0L) up.totalDurationSec.toFloat() / stats.totalDurationSec else null
}
