package com.android.purebilibili.feature.home

internal const val HOME_HERO_CAROUSEL_MAX_ITEMS = 8
/** Extra peek inside the feed column; 0 keeps the banner flush with video cards. */
internal const val HOME_HERO_CAROUSEL_SIDE_PEEK_DP = 0f
internal const val HOME_HERO_CAROUSEL_PAGE_SPACING_DP = 0f
internal const val HOME_HERO_CAROUSEL_PHONE_ASPECT_RATIO = 16f / 9f
internal const val HOME_HERO_CAROUSEL_TABLET_ASPECT_RATIO = 2f
internal const val HOME_HERO_CAROUSEL_WIDE_ASPECT_RATIO = 21f / 9f
internal const val HOME_HERO_CAROUSEL_TABLET_BREAKPOINT_DP = 600f
internal const val HOME_HERO_CAROUSEL_WIDE_BREAKPOINT_DP = 840f
internal const val HOME_HERO_CAROUSEL_WIDE_MAX_WIDTH_DP = 980f
// Keep the hero below the top category dock. The dock's selection indicator can
// extend beyond its measured bounds, so reducing this reserved gap causes the
// banner to be covered at the top on the recommendation page.
private const val HOME_HERO_CAROUSEL_TOP_GAP_REDUCTION_DP = 0f

internal data class HomeHeroCarouselCardTransform(
    val rotationY: Float,
    val rotationZ: Float,
    val scale: Float,
    val alpha: Float,
    val cameraDistanceMultiplier: Float,
    val translationXFraction: Float,
    val pivotFractionX: Float,
    val zIndex: Float,
    val contentParallaxFraction: Float,
    val contentScale: Float,
    val edgeShadeAlpha: Float,
    val edgeShadeStartFromLeft: Boolean,
)

internal fun resolveHomeFeedTopPaddingDp(
    reservedTopPaddingDp: Float,
    showHeroCarousel: Boolean
): Float {
    val reductionDp = if (showHeroCarousel) HOME_HERO_CAROUSEL_TOP_GAP_REDUCTION_DP else 0f
    return (reservedTopPaddingDp - reductionDp).coerceAtLeast(0f)
}

internal fun <T> resolveHomeHeroCarouselItemOrNull(
    items: List<T>,
    page: Int
): T? = items.getOrNull(page)

internal fun <T> resolveHomeHeroCarouselItemKey(
    items: List<T>,
    page: Int,
    keySelector: (T) -> String
): String {
    val itemKey = items.getOrNull(page)?.let(keySelector).orEmpty()
    return itemKey.ifBlank { "hero_$page" }
}

internal fun <T> selectHomeHeroCarouselItems(
    items: List<T>,
    maxItems: Int = HOME_HERO_CAROUSEL_MAX_ITEMS,
    eligible: (T) -> Boolean = { true },
): List<T> {
    if (maxItems <= 0) return emptyList()
    return items.asSequence().filter(eligible).take(maxItems).toList()
}

internal fun <T, K> excludeHomeHeroCarouselItems(
    items: List<T>,
    carouselItems: List<T>,
    keySelector: (T) -> K
): List<T> {
    if (carouselItems.isEmpty()) return items
    val carouselKeys = carouselItems.mapTo(mutableSetOf(), keySelector)
    return items.filterNot { keySelector(it) in carouselKeys }
}

internal fun shouldShowHomeHeroCarousel(
    enabled: Boolean,
    category: HomeCategory,
    itemCount: Int
): Boolean {
    return enabled && category == HomeCategory.RECOMMEND && itemCount > 0
}

internal fun shouldYieldHomeTopPagerToHeroCarousel(
    heroCarouselPointerActive: Boolean
): Boolean = heroCarouselPointerActive

internal fun resolveHomeHeroCarouselAspectRatio(containerWidthDp: Float): Float {
    return when {
        containerWidthDp >= HOME_HERO_CAROUSEL_WIDE_BREAKPOINT_DP ->
            HOME_HERO_CAROUSEL_WIDE_ASPECT_RATIO
        containerWidthDp >= HOME_HERO_CAROUSEL_TABLET_BREAKPOINT_DP ->
            HOME_HERO_CAROUSEL_TABLET_ASPECT_RATIO
        else -> HOME_HERO_CAROUSEL_PHONE_ASPECT_RATIO
    }
}

internal fun resolveHomeHeroCarouselWidthDp(containerWidthDp: Float): Float {
    val availableWidthDp = containerWidthDp.coerceAtLeast(0f)
    return when {
        availableWidthDp >= HOME_HERO_CAROUSEL_WIDE_BREAKPOINT_DP ->
            availableWidthDp.coerceAtMost(HOME_HERO_CAROUSEL_WIDE_MAX_WIDTH_DP)
        //  [满宽] 840dp 以下（手机与小平板/折叠内屏）banner 不再收窄：
        //  容器超过 760dp 时两侧会露出邻卡边条，直接取满容器宽度。
        availableWidthDp >= HOME_HERO_CAROUSEL_TABLET_BREAKPOINT_DP ->
            availableWidthDp
        else -> availableWidthDp
    }
}

internal data class HomeHeroCarouselLayout(
    val widthDp: Float,
    val heightDp: Float,
    val aspectRatio: Float,
)

/**
 * Desktop/landscape windows keep a cinematic strip instead of a 16:9 poster.
 * Height is taken from the current window, not the device type, so split-screen
 * and foldable covers shrink the banner with the available viewport.
 */
internal fun resolveHomeHeroCarouselMaxHeightDp(
    windowWidthDp: Float,
    windowHeightDp: Float,
): Float? {
    if (windowHeightDp <= 0f) return null
    val landscape = windowWidthDp > windowHeightDp
    return when {
        windowHeightDp < 480f -> (windowHeightDp * 0.40f).coerceIn(132f, 188f)
        landscape -> (windowHeightDp * 0.32f).coerceIn(168f, 248f)
        else -> null
    }
}

internal fun resolveHomeHeroCarouselLayout(
    containerWidthDp: Float,
    windowWidthDp: Float,
    windowHeightDp: Float,
): HomeHeroCarouselLayout {
    val widthCap = resolveHomeHeroCarouselWidthDp(containerWidthDp)
    val aspectRatio = resolveHomeHeroCarouselAspectRatio(widthCap)
    val unconstrainedHeight = if (aspectRatio > 0f) widthCap / aspectRatio else 0f
    val maxHeight = resolveHomeHeroCarouselMaxHeightDp(
        windowWidthDp = windowWidthDp,
        windowHeightDp = windowHeightDp,
    )
    return if (maxHeight != null && unconstrainedHeight > maxHeight) {
        val height = maxHeight
        val width = (height * aspectRatio).coerceAtMost(widthCap)
        HomeHeroCarouselLayout(
            widthDp = width,
            heightDp = height,
            aspectRatio = aspectRatio,
        )
    } else {
        HomeHeroCarouselLayout(
            widthDp = widthCap,
            heightDp = unconstrainedHeight,
            aspectRatio = aspectRatio,
        )
    }
}

internal fun resolveHomeHeroCarouselCardTransform(
    pageOffset: Float,
    pressedProgress: Float = 0f,
): HomeHeroCarouselCardTransform {
    val clampedOffset = pageOffset.coerceIn(-1f, 1f)
    val distance = kotlin.math.abs(clampedOffset)
    val baseScale = 1f - distance * 0.10f
    val pressMultiplier = 1f - pressedProgress.coerceIn(0f, 1f) * 0.02f
    val scale = baseScale * pressMultiplier
    val alpha = (1f - distance * 0.20f).coerceAtLeast(0.35f)

    // Cover-flow 折角：侧卡绕自身内缘向中心旋转，配合透视相机构成弧形展台。
    val rotationY = (-clampedOffset * 26f).coerceIn(-40f, 40f)
    val pivotFractionX = when {
        clampedOffset < 0f -> 0f // 右侧卡绕左缘（内缘）旋转
        clampedOffset > 0f -> 1f // 左侧卡绕右缘（内缘）旋转
        else -> 0.5f
    }
    // 向中心聚拢：pageOffset>0 为左邻卡（右移），<0 为右邻卡（左移）。
    val translationXFraction = kotlin.math.sign(clampedOffset) * 0.04f * distance

    // Parallax: inner content shifts opposite to gesture by 8% of card width for tactile depth.
    // Content scale: expands content by 16% at max offset so bounds never expose behind parallax shift.
    val contentParallaxFraction = -clampedOffset * 0.08f
    val contentScale = 1f + distance * 0.16f

    // Edge shading: soft scrim shadow between adjacent cards during swipe
    val edgeShadeAlpha = (distance * 0.25f).coerceIn(0f, 0.25f)
    val edgeShadeStartFromLeft = clampedOffset < 0f

    return HomeHeroCarouselCardTransform(
        rotationY = rotationY,
        rotationZ = 0f,
        scale = scale,
        alpha = alpha,
        cameraDistanceMultiplier = 10f,
        translationXFraction = translationXFraction,
        pivotFractionX = pivotFractionX,
        zIndex = 1f - distance * 0.01f,
        contentParallaxFraction = contentParallaxFraction,
        contentScale = contentScale,
        edgeShadeAlpha = edgeShadeAlpha,
        edgeShadeStartFromLeft = edgeShadeStartFromLeft,
    )
}

internal fun resolveHomeHeroCarouselPreviewAlpha(
    hasRenderedFirstFrame: Boolean
): Float = if (hasRenderedFirstFrame) 1f else 0f
