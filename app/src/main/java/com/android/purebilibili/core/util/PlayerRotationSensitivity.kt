package com.android.purebilibili.core.util

internal data class PlayerRotationThresholds(
    val portraitSnap: Int,
    val landscapeEnterMin: Int,
    val landscapeEnterMax: Int,
    val landscapeKeepMin: Int,
    val landscapeKeepMax: Int,
)

internal enum class PlayerRotationSensitivity(val value: Int, val label: String, val thresholds: PlayerRotationThresholds) {
    STANDARD(0, "标准", PlayerRotationThresholds(25, 60, 120, 40, 140)),
    STEADY(1, "稳重", PlayerRotationThresholds(15, 70, 110, 50, 130)),
    SENSITIVE(2, "灵敏", PlayerRotationThresholds(35, 50, 130, 35, 145));

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}
