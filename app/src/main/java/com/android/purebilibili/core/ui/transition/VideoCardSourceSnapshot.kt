package com.android.purebilibili.core.ui.transition

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

internal enum class VideoCardSourceLayout {
    STACKED,
    SIDE_BY_SIDE,
    COVER_ONLY,
}

/**
 * Which info-region rows the list card actually paints under the cover (not on-cover badges).
 *
 * Frozen at click time so return chrome can mirror the real card without inventing fields
 * (e.g. home dual-column: title + UP + 发布于; danmaku stays on cover, not in info).
 */
@Immutable
internal data class VideoCardSourceInfoPresentation(
    /** Preformatted publish row as shown on the card, e.g. "发布于 昨天"; blank = no row. */
    val publishTimeText: String = "",
    /**
     * When true, info region paints view/danmaku (and optional duration) text.
     * When false, those live on the cover overlay and must not appear under the title.
     */
    val showStatsInInfo: Boolean = false,
    /** Stacked feed cards place the uploader above off-cover statistics. */
    val ownerBeforeStats: Boolean = false,
    /**
     * Whether duration is painted in the source card's information region.
     *
     * Horizontal cards keep duration on the cover. Recording this separately prevents the
     * flying return chrome from inventing a duplicate duration row beside the cover.
     */
    val showDurationInInfo: Boolean = false,
    /** Horizontal personal cards paint the UP row before their date row. */
    val ownerBeforePublish: Boolean = false,
    /**
     * When true, list info sits on the home themed/tinted plate
     * ([resolveHomeCardInfoSurfaceAppearance]); flying chrome must paint the same plate.
     */
    val useTintedInfoSurface: Boolean = false,
    /** Whether the stationary card exposes the bottom-right overflow affordance. */
    val showOverflowMenu: Boolean = false,
    /** Regular home cards keep two title lines and single-line metadata at every width. */
    val singleLineMetadata: Boolean = false,
    /** Empty means no info-region online row; an unknown visible count is recorded as “—”. */
    val onlineCountText: String = "",
)

/**
 * Chrome painted directly over the stationary source cover.
 *
 * These flags are frozen at click time because home settings can move statistics and duration
 * between the cover and info region. The return overlay must mirror the clicked card rather than
 * re-resolving settings after navigation.
 */
@Immutable
internal data class VideoCardSourceCoverPresentation(
    val showGradientMask: Boolean = false,
    val showStatsOnCover: Boolean = false,
    val showSecondaryStatOnCover: Boolean = false,
    val showOnlineCountOnCover: Boolean = false,
    val showDurationOnCover: Boolean = false,
    val showDurationAsStat: Boolean = false,
    val useGlassStats: Boolean = false,
    val onlineCountText: String = "",
    val premiumBadgeText: String = "",
    val showHistoryProgressBar: Boolean = false,
    val historyProgressFraction: Float = 0f,
    /** 源封面的实际圆角，与透明横卡的整行外框圆角独立；未记录时沿用默认值。 */
    val cornerRadiusDp: Float? = null,
)

/**
 * Click-time chrome + cover frozen from the **stationary list card**.
 *
 * Text is already formatted by the source card so the landing frame cannot drift after data
 * refreshes or while the destination is in Loading.
 *
 * [infoPresentation] records **which** rows were visible so flying chrome can match
 * dynamically when home layout / scroll-lite policy changes.
 *
 * [coverUrl] / [coverCacheKey] / decode size must be the exact Coil request the list
 * AsyncImage uses at rest (including [com.android.purebilibili.feature.home.HomeCoverRequestSpec]
 * sized URL + `size(w,h)`). Detail resident / player-section covers reuse these so handoff
 * pixels match the stationary card — not `fixImageUrl` / default cache key.
 */
@Immutable
internal data class VideoCardSourceChromeSnapshot(
    val title: String,
    val ownerName: String,
    val ownerFaceUrl: String = "",
    val viewText: String = "",
    val danmakuText: String = "",
    val durationText: String = "",
    val followed: Boolean = false,
    val recommendationReason: String = "",
    /** What the list info column actually showed at click. */
    val infoPresentation: VideoCardSourceInfoPresentation = VideoCardSourceInfoPresentation(),
    /** What the list cover actually painted at click. */
    val coverPresentation: VideoCardSourceCoverPresentation = VideoCardSourceCoverPresentation(),
    /** Exact list-card cover request URL (sized / quality resolved). */
    val coverUrl: String = "",
    /** Exact list-card Coil memoryCacheKey / diskCacheKey. */
    val coverCacheKey: String = "",
    /** Coil `size(w,h)` from list HomeCoverRequestSpec; 0 = omit size(). */
    val coverDecodeWidthPx: Int = 0,
    val coverDecodeHeightPx: Int = 0,
    /** Opaque click-time information plate, including the wallpaper behind its tint. */
    val infoContainerColor: Color? = null,
    /** 信息区在整卡录制图层中的局部像素矩形；不含悬停、落位反馈或页面景深缩放。 */
    val nativeInfoBounds: Rect? = null,
    /** Compact playback bar uses an early source-chrome handoff on return. */
    val isNowPlayingBar: Boolean = false,
)

/** 相同内容也可能来自另一次点击；只有原始快照实例能使用该次点击的像素。 */
internal fun ownsVideoCardSourceSnapshot(
    expected: VideoCardSourceChromeSnapshot?,
    recorded: VideoCardSourceChromeSnapshot?,
): Boolean = expected != null && expected === recorded

/** Freeze the measured source-cover decode size together with the click-time snapshot. */
internal fun VideoCardSourceChromeSnapshot.withMeasuredCoverDecodeSize(
    coverBounds: Rect?,
): VideoCardSourceChromeSnapshot = copy(
    coverDecodeWidthPx = coverBounds?.width?.roundToInt()?.coerceAtLeast(0) ?: 0,
    coverDecodeHeightPx = coverBounds?.height?.roundToInt()?.coerceAtLeast(0) ?: 0,
)

/** Build info-presentation from the list card’s live display flags (call at click). */
internal fun resolveVideoCardSourceInfoPresentation(
    publishTimeText: String,
    showStatsInInfo: Boolean,
    showDurationInInfo: Boolean = false,
    ownerBeforePublish: Boolean = false,
    useTintedInfoSurface: Boolean = false,
    showOverflowMenu: Boolean = false,
    ownerBeforeStats: Boolean = false,
    singleLineMetadata: Boolean = false,
    onlineCountText: String = "",
): VideoCardSourceInfoPresentation = VideoCardSourceInfoPresentation(
    publishTimeText = publishTimeText.trim(),
    showStatsInInfo = showStatsInInfo,
    ownerBeforeStats = ownerBeforeStats,
    showDurationInInfo = showDurationInInfo,
    ownerBeforePublish = ownerBeforePublish,
    useTintedInfoSurface = useTintedInfoSurface,
    showOverflowMenu = showOverflowMenu,
    singleLineMetadata = singleLineMetadata,
    onlineCountText = onlineCountText,
)

internal fun resolveVideoCardSourceLayout(
    cardBounds: Rect?,
    coverBounds: Rect?,
): VideoCardSourceLayout {
    val card = cardBounds?.takeIf { it.width > 1f && it.height > 1f }
        ?: return VideoCardSourceLayout.COVER_ONLY
    val cover = coverBounds?.takeIf { it.width > 1f && it.height > 1f }
        ?: return VideoCardSourceLayout.COVER_ONLY
    val horizontalTolerance = card.width * 0.1f
    val verticalTolerance = card.height * 0.1f
    val coverSpansCardWidth = cover.left <= card.left + horizontalTolerance &&
        cover.right >= card.right - horizontalTolerance
    val coverSpansCardHeight = cover.top <= card.top + verticalTolerance &&
        cover.bottom >= card.bottom - verticalTolerance
    return when {
        coverSpansCardWidth && !coverSpansCardHeight -> VideoCardSourceLayout.STACKED
        coverSpansCardHeight && !coverSpansCardWidth -> VideoCardSourceLayout.SIDE_BY_SIDE
        else -> VideoCardSourceLayout.COVER_ONLY
    }
}
