package com.android.purebilibili.feature.settings

enum class AppThemeMode(val value: Int, val label: String) {
    FOLLOW_SYSTEM(0, "跟随系统"),
    LIGHT(1, "浅色模式"),
    DARK(2, "深色模式");

    companion object {
        fun fromValue(value: Int): AppThemeMode = entries.find { it.value == value } ?: FOLLOW_SYSTEM
    }
}