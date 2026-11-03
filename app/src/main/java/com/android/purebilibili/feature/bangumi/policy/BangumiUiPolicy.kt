package com.android.purebilibili.feature.bangumi

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import com.android.purebilibili.core.theme.AdaptiveAccentColors
import com.android.purebilibili.core.ui.components.userVipPink
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.ACCESSIBLE_TEXT_MIN_CONTRAST
import com.android.purebilibili.core.theme.AccessibleContainerColors
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors

/** Fixed business colors shared by cover badges and episode labels. */
@Composable
internal fun bangumiBrandBadgeColors(label: String): AdaptiveAccentColors? {
    val color = when (label.trim()) {
        "会员", "大会员", "会员专享" -> userVipPink()
        "免费", "限免" -> bangumiFreeColor()
        else -> return null
    }
    return AdaptiveAccentColors(color, Color.White)
}

/** 免费与限免使用独立于主题强调色的橙色。 */
@Composable
internal fun bangumiFreeColor(): Color =
    if (com.android.purebilibili.core.ui.isAppInDarkTheme()) Color(0xFFD66011) else Color(0xFFFF7F24)

/**
 * Cover-corner badges (大会员 / 出品) sit on a small primary fill.
 * Use onPrimary first so light-gold dark-theme skins get a dark label;
 * fall back to black/white by contrast instead of hard-coding either.
 */
internal fun resolveBangumiCoverBadgeColors(
    primary: Color,
    onPrimary: Color,
    surface: Color,
    onSurface: Color,
): AccessibleContainerColors {
    return resolveAccessibleContainerColors(
        containerColor = primary,
        contentColor = onPrimary,
        backgroundColor = surface,
        fallbackContentColors = listOf(onSurface, Color.Black, Color.White),
        minimumContrast = ACCESSIBLE_TEXT_MIN_CONTRAST,
    )
}

internal fun resolveBangumiNavigationTitleFontSizeSp(screenWidthDp: Int): Float {
    return if (screenWidthDp >= 380) 22f else 20f
}

internal fun resolveBangumiTypeTabFontSizeSp(screenWidthDp: Int): Float {
    return if (screenWidthDp >= 380) 16f else 14f
}

internal fun resolveBangumiPlayerTopControlsPaddingTopDp(
    isFullscreen: Boolean,
    statusBarsInsetDp: Float
): Float {
    return 8f
}

internal fun resolveBangumiDanmakuTopInsetDp(
    isFullscreen: Boolean,
    statusBarsInsetDp: Float
): Float {
    return if (isFullscreen) 0f else 52f
}

internal fun resolveBangumiPortraitPlayerContainerTopPaddingDp(
    statusBarsInsetDp: Float
): Float {
    return statusBarsInsetDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
}

internal fun resolveBangumiFullscreen(
    isLandscape: Boolean,
    isTablet: Boolean,
    usesInWindowFullscreen: Boolean,
    userRequestedFullscreen: Boolean,
): Boolean {
    return if (isTablet || usesInWindowFullscreen) {
        userRequestedFullscreen
    } else {
        isLandscape
    }
}

internal fun resolveBangumiToggleOrientationTarget(
    isFullscreen: Boolean,
    isTablet: Boolean,
    usesInWindowFullscreen: Boolean,
): Int? {
    if (isTablet || usesInWindowFullscreen) return null
    return if (isFullscreen) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

internal data class BangumiEpisodePreviewWindow(
    val startIndex: Int,
    val endExclusive: Int
)

internal fun resolveBangumiEpisodePageCount(
    episodeCount: Int,
    episodesPerPage: Int
): Int {
    if (episodeCount <= 0 || episodesPerPage <= 0) return 0
    return (episodeCount + episodesPerPage - 1) / episodesPerPage
}

internal fun resolveBangumiEpisodePageLabel(
    episodeCount: Int,
    page: Int,
    episodesPerPage: Int,
    descending: Boolean
): String {
    val pageCount = resolveBangumiEpisodePageCount(episodeCount, episodesPerPage)
    if (pageCount == 0) return ""
    val safePage = page.coerceIn(0, pageCount - 1)
    return if (descending) {
        val high = episodeCount - safePage * episodesPerPage
        val low = maxOf(1, high - episodesPerPage + 1)
        "$high-$low"
    } else {
        val low = safePage * episodesPerPage + 1
        val high = minOf(episodeCount, low + episodesPerPage - 1)
        "$low-$high"
    }
}

internal fun <T> orderBangumiEpisodes(episodes: List<T>, descending: Boolean): List<T> {
    return if (descending) episodes.asReversed() else episodes
}

internal fun resolveBangumiEpisodePreviewWindow(
    episodeCount: Int,
    selectedPage: Int,
    episodesPerPage: Int,
    previewCount: Int
): BangumiEpisodePreviewWindow {
    if (episodeCount <= 0 || episodesPerPage <= 0 || previewCount <= 0) {
        return BangumiEpisodePreviewWindow(startIndex = 0, endExclusive = 0)
    }
    val maxPage = (episodeCount - 1) / episodesPerPage
    val safePage = selectedPage.coerceIn(0, maxPage)
    val pageStart = safePage * episodesPerPage
    val pageEnd = minOf(pageStart + episodesPerPage, episodeCount)
    return BangumiEpisodePreviewWindow(
        startIndex = pageStart,
        endExclusive = minOf(pageStart + previewCount, pageEnd)
    )
}
