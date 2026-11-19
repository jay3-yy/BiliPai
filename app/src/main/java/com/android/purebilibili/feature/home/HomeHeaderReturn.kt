package com.android.purebilibili.feature.home

internal enum class HomeHeaderControl {
    AVATAR,
    SEARCH,
    END_ACTION,
}

// Each committed return is a distinct event, including consecutive visits to the same page.
internal class HomeHeaderReturn(
    val source: HomeHeaderControl,
    val landingFromSystemWindow: Boolean = false,
) {
    private var consumed = false

    fun consume(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }
}
