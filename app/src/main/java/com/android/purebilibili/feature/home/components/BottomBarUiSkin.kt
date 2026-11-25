package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.OpticalContrastPalette
import com.android.purebilibili.feature.home.HomeVisualPalette

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.plugin.skin.UiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import java.io.File

data class BottomBarUiSkinDecoration(
    val skinId: String,
    val bottomTrimTint: Color,
    val bottomTrimAccent: Color,
    val bottomUnselectedTint: Color = Color.Unspecified,
    val bottomSelectedTint: Color = Color.Unspecified,
    val bottomTrimImagePath: String? = null,
    val liquidGlassSurfaceTint: Color? = null,
    val liquidGlassTintOpacity: Float = 0.22f,
    val iconMotion: BottomBarSkinMotionSpec = BottomBarSkinMotionSpec(),
    val bottomBarIconPaths: Map<BottomNavItem, BottomBarSkinIconPaths> = emptyMap(),
    val distinctRolePool: List<BottomBarSkinIconPaths> = emptyList(),
) {
    fun iconPathFor(
        item: BottomNavItem,
        selected: Boolean = false,
        visibleItems: List<BottomNavItem> = emptyList(),
    ): String? {
        if (visibleItems.size > 1 && distinctRolePool.size > 1 && item in visibleItems) {
            val deduplicated = resolveDeduplicatedVisibleIconPaths(visibleItems)
            deduplicated[item]?.let { return it.pathFor(selected) }
        }
        return bottomBarIconPaths[item]?.pathFor(selected)
    }

    fun illustratedIconPathFor(
        item: BottomNavItem,
        selected: Boolean,
        visibleItems: List<BottomNavItem> = emptyList(),
    ): String? {
        return iconPathFor(item = item, selected = selected, visibleItems = visibleItems)
    }

    private fun resolveDeduplicatedVisibleIconPaths(
        visibleItems: List<BottomNavItem>,
    ): Map<BottomNavItem, BottomBarSkinIconPaths> {
        val canonical = setOf(
            BottomNavItem.HOME,
            BottomNavItem.DYNAMIC,
            BottomNavItem.LISTEN_VIDEO,
            BottomNavItem.HISTORY,
            BottomNavItem.PROFILE,
        )
        val assigned = linkedMapOf<BottomNavItem, BottomBarSkinIconPaths>()
        val usedUnselectedPaths = linkedSetOf<String>()
        // 1. 先固定匹配官方原生语义槽位
        visibleItems.filter { it in canonical }.forEach { navItem ->
            val paths = bottomBarIconPaths[navItem] ?: return@forEach
            if (usedUnselectedPaths.add(paths.unselected)) {
                assigned[navItem] = paths
            }
        }
        // 2. 其余可见自定义槽位（如设置、收藏、短视频、直播等）优先分配尚未出场的装扮角色图标，避免相邻 Tab 撞脸
        visibleItems.forEach { navItem ->
            if (navItem in assigned) return@forEach
            val preferred = bottomBarIconPaths[navItem]
            val picked = if (preferred != null && preferred.unselected !in usedUnselectedPaths) {
                preferred
            } else {
                distinctRolePool.firstOrNull { it.unselected !in usedUnselectedPaths }
                    ?: preferred
                    ?: distinctRolePool.firstOrNull()
            }
            if (picked != null) {
                usedUnselectedPaths.add(picked.unselected)
                assigned[navItem] = picked
            }
        }
        return assigned
    }
}

data class BottomBarSkinIconPaths(
    val unselected: String,
    val selected: String? = null
) {
    fun pathFor(selected: Boolean): String {
        return if (selected) this.selected ?: unselected else unselected
    }
}

data class BottomBarSkinMotionSpec(
    val enabled: Boolean = false,
    val mode: String? = null,
)

data class DynamicPublishSkinDecoration(
    val iconPaths: BottomBarSkinIconPaths? = null,
    val iconTint: Color = Color.Unspecified,
    val shadeTop: Color = Color.Transparent,
    val shadeBottom: Color = Color.Transparent,
) {
    val hasShade: Boolean
        get() = shadeTop != Color.Transparent || shadeBottom != Color.Transparent
}

private val LocalBottomBarSkinMotionSpec = staticCompositionLocalOf {
    BottomBarSkinMotionSpec()
}

@Composable
internal fun ProvideBottomBarSkinMotion(
    decoration: BottomBarUiSkinDecoration?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBottomBarSkinMotionSpec provides (decoration?.iconMotion ?: BottomBarSkinMotionSpec()),
        content = content,
    )
}

data class TopTabSkinIconPaths(
    val unselected: String,
    val selected: String? = null
) {
    fun pathFor(selected: Boolean): String {
        return if (selected) {
            this.selected ?: unselected
        } else {
            unselected
        }
    }
}

data class HomeUiSkinDecoration(
    val skinId: String,
    val topAtmosphereTint: Color,
    val topForegroundTint: Color = Color.Unspecified,
    val searchCapsuleTint: Color,
    val searchCapsuleImagePath: String? = null,
    val topAtmosphereImagePath: String? = null,
    val topTabBackgroundImagePath: String? = null,
    val sideBackgroundImagePath: String? = null,
    val sideBottomTrimImagePath: String? = null,
    val sideBackgroundTint: Color? = null,
    val profileBackgroundImagePath: String? = null,
    val profileSquaredBackgroundImagePath: String? = null,
    val profileVideoBackgroundPath: String? = null,
    val profileVideoPlayMode: String? = null,
    val liquidGlassSurfaceTint: Color? = null,
    val liquidGlassTintOpacity: Float = 0.22f,
    val colorMode: String? = null,
    val topTabSkinIconPaths: Map<String, TopTabSkinIconPaths> = emptyMap(),
    val topTabPartitionSkinIconPaths: TopTabSkinIconPaths? = null
) {
    fun topTabIconPathFor(categoryKey: String, selected: Boolean = false): String? {
        val normalizedKey = categoryKey.trim().uppercase()
        val paths = topTabSkinIconPaths[normalizedKey] ?: return null
        return paths.pathFor(selected)
    }

    fun topTabPartitionIconPath(selected: Boolean = false): String? {
        return topTabPartitionSkinIconPaths?.pathFor(selected)
    }
}

internal fun shouldUseIllustratedSkinBottomBar(
    hasBackground: Boolean,
    hasIcons: Boolean,
    isTablet: Boolean,
): Boolean = hasBackground && hasIcons && !isTablet

internal fun BottomBarUiSkinDecoration?.usesIllustratedNavigation(isTablet: Boolean): Boolean =
    shouldUseIllustratedSkinBottomBar(
        hasBackground = !this?.bottomTrimImagePath.isNullOrBlank(),
        hasIcons = this?.bottomBarIconPaths?.isNotEmpty() == true,
        isTablet = isTablet,
    )

internal fun resolveIllustratedSkinIconSize(slotWidth: Dp): Dp =
    (slotWidth - 8.dp).coerceIn(24.dp, 52.dp)

internal fun resolveBottomBarSkinDockIconSize(): Dp = AppSpacingTokens.DoubleExtraLarge

internal fun resolveBottomBarSkinDockHeight(): Dp = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large

internal fun resolveBottomBarSkinDockContentPadding(): PaddingValues = PaddingValues(
    start = AppSpacingTokens.ExtraSmall,
    end = AppSpacingTokens.ExtraSmall,
    top = AppSpacingTokens.None,
    bottom = AppSpacingTokens.None
)

internal fun resolveBottomBarSkinIconLabelGap(): Dp = AppSpacingTokens.Micro

internal fun resolveBottomBarSkinDockIconTopPadding(): Dp = AppSpacingTokens.ExtraSmall

internal fun resolveBottomBarSkinDockLabelBottomPadding(): Dp = AppSpacingTokens.ExtraSmall

internal fun resolveBottomBarMiuixSkinDockIconSize(): Dp = AppSpacingTokens.DoubleExtraLarge

internal fun resolveBottomBarCompactSkinHomeIconSize(): Dp = AppSpacingTokens.DoubleExtraLarge

internal fun resolveMiuixDockedBottomBarItemHeight(hasUiSkinDecoration: Boolean): Dp {
    return if (hasUiSkinDecoration) {
        resolveBottomBarSkinDockHeight()
    } else {
        AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large
    }
}

@Composable
fun rememberBottomBarUiSkinDecoration(uiSkinState: UiSkinState): BottomBarUiSkinDecoration? {
    val isDark = com.android.purebilibili.core.ui.isAppInDarkTheme()
    return remember(uiSkinState, isDark) {
        resolveBottomBarUiSkinDecoration(uiSkinState, isDark)
    }
}

@Composable
fun rememberDynamicPublishSkinDecoration(uiSkinState: UiSkinState): DynamicPublishSkinDecoration? {
    return remember(uiSkinState) {
        resolveDynamicPublishSkinDecoration(uiSkinState)
    }
}

@Composable
fun rememberHomeUiSkinDecoration(uiSkinState: UiSkinState): HomeUiSkinDecoration? {
    val isDark = com.android.purebilibili.core.ui.isAppInDarkTheme()
    return remember(uiSkinState, isDark) {
        resolveHomeUiSkinDecoration(uiSkinState, isDark)
    }
}

fun resolveBottomBarUiSkinDecoration(
    uiSkinState: UiSkinState,
    isDark: Boolean = false,
): BottomBarUiSkinDecoration? {
    val activeSkin = uiSkinState.activeSkin
    return if (
        !uiSkinState.enabled ||
        activeSkin == null ||
        UiSkinSurface.HOME_BOTTOM_BAR !in activeSkin.manifest.surfaces
    ) {
        null
    } else {
        BottomBarUiSkinDecoration(
            skinId = activeSkin.manifest.skinId,
            bottomTrimTint = parseUiSkinColor(
                value = activeSkin.manifest.colors.bottomBarTrimTint,
                fallback = HomeVisualPalette.BottomBarIceLight
            ),
            bottomUnselectedTint = parseUiSkinColor(
                value = if (isDark) {
                    activeSkin.manifest.colors.bottomBarIconDarkTint
                        ?: activeSkin.manifest.colors.bottomBarIconTint
                } else {
                    activeSkin.manifest.colors.bottomBarIconTint
                },
                fallback = Color.Unspecified,
            ),
            bottomSelectedTint = parseUiSkinColor(
                value = if (isDark) {
                    activeSkin.manifest.colors.bottomBarSelectedDarkTint
                        ?: activeSkin.manifest.colors.bottomBarSelectedTint
                } else {
                    activeSkin.manifest.colors.bottomBarSelectedTint
                },
                fallback = Color.Unspecified,
            ),
            bottomTrimAccent = parseUiSkinColor(
                value = activeSkin.manifest.colors.topAtmosphereTint,
                fallback = HomeVisualPalette.BottomBarIce
            ),
            bottomTrimImagePath = activeSkin.assetFilePath(activeSkin.manifest.assets.bottomBarTrim),
            liquidGlassSurfaceTint = activeSkin.manifest.colors.liquidGlassSurfaceTint?.let {
                parseUiSkinColor(it, Color.Unspecified).takeUnless { c -> c == Color.Unspecified }
            },
            liquidGlassTintOpacity = (activeSkin.manifest.colors.liquidGlassTintOpacity ?: 0.22f)
                .coerceIn(0.08f, 0.45f),
            iconMotion = BottomBarSkinMotionSpec(
                enabled = activeSkin.manifest.motion.bottomBarIconAnimated,
                mode = activeSkin.manifest.motion.bottomBarIconAnimationMode,
            ),
            bottomBarIconPaths = resolveBottomBarSkinIconPaths(activeSkin),
            distinctRolePool = resolveDistinctBottomBarSkinRolePool(activeSkin),
        )
    }
}


fun resolveDynamicPublishSkinDecoration(uiSkinState: UiSkinState): DynamicPublishSkinDecoration? {
    val activeSkin = uiSkinState.activeSkin
    if (
        !uiSkinState.enabled ||
        activeSkin == null ||
        UiSkinSurface.DYNAMIC_PUBLISH !in activeSkin.manifest.surfaces
    ) {
        return null
    }
    val colors = activeSkin.manifest.colors
    val iconPaths = activeSkin.assetFilePath(activeSkin.manifest.assets.dynamicPublishIcon)
        ?.let { unselectedPath ->
            BottomBarSkinIconPaths(
                unselected = unselectedPath,
                selected = activeSkin.assetFilePath(activeSkin.manifest.assets.dynamicPublishSelectedIcon),
            )
        }
    val decoration = DynamicPublishSkinDecoration(
        iconPaths = iconPaths,
        iconTint = parseUiSkinColor(colors.dynamicPublishIconTint, Color.Unspecified),
        shadeTop = parseUiSkinColor(colors.dynamicPublishShadeTop, Color.Transparent),
        shadeBottom = parseUiSkinColor(colors.dynamicPublishShadeBottom, Color.Transparent),
    )
    return decoration.takeIf {
        it.iconPaths != null || it.iconTint != Color.Unspecified || it.hasShade
    }
}

fun resolveHomeUiSkinDecoration(
    uiSkinState: UiSkinState,
    isDark: Boolean = false,
): HomeUiSkinDecoration? {
    val activeSkin = uiSkinState.activeSkin
    return if (!uiSkinState.enabled || activeSkin == null) {
        null
    } else {
        val manifest = activeSkin.manifest
        val hasTopChrome = UiSkinSurface.HOME_TOP_CHROME in manifest.surfaces
        val hasDrawer = UiSkinSurface.HOME_DRAWER in manifest.surfaces
        val hasProfile = UiSkinSurface.PROFILE in manifest.surfaces
        val hasTopDecoration = (hasTopChrome || hasDrawer || hasProfile) &&
            (
                manifest.assets.topAtmosphere != null ||
                    manifest.assets.homeTopTabBackground != null ||
                    manifest.assets.searchCapsuleBackground != null ||
                    manifest.assets.homeSideBackground != null ||
                    manifest.assets.drawerBottomTrim != null ||
                    manifest.assets.homeProfileBackground != null ||
                    manifest.assets.homeProfileSquaredBackground != null ||
                    manifest.assets.homeProfileVideoBackground != null ||
                    manifest.colors.topAtmosphereTint != null ||
                    manifest.colors.topForegroundTint != null ||
                    manifest.colors.searchCapsuleTint != null ||
                    manifest.colors.sideBackgroundTint != null
                )
        if (!hasTopDecoration) return null
        HomeUiSkinDecoration(
            skinId = manifest.skinId,
            topAtmosphereTint = parseUiSkinColor(
                value = manifest.colors.topAtmosphereTint,
                fallback = HomeVisualPalette.BottomBarIce
            ),
            topForegroundTint = if (hasTopChrome) {
                parseUiSkinColor(
                    value = if (isDark) {
                        manifest.colors.topForegroundDarkTint ?: manifest.colors.topForegroundTint
                    } else {
                        manifest.colors.topForegroundTint
                    },
                    fallback = Color.Unspecified,
                )
            } else Color.Unspecified,
            searchCapsuleTint = parseUiSkinColor(
                value = manifest.colors.searchCapsuleTint,
                fallback = OpticalContrastPalette.Highlight
            ),
            searchCapsuleImagePath = if (hasTopChrome) {
                activeSkin.assetFilePath(manifest.assets.searchCapsuleBackground)
            } else null,
            topAtmosphereImagePath = if (hasTopChrome) {
                activeSkin.assetFilePath(manifest.assets.topAtmosphere)
            } else null,
            topTabBackgroundImagePath = if (hasTopChrome) {
                activeSkin.assetFilePath(manifest.assets.homeTopTabBackground)
            } else null,
            sideBackgroundImagePath = if (hasDrawer) {
                activeSkin.assetFilePath(manifest.assets.homeSideBackground)
            } else null,
            sideBottomTrimImagePath = if (hasDrawer) {
                activeSkin.assetFilePath(manifest.assets.drawerBottomTrim)
            } else null,
            sideBackgroundTint = if (hasDrawer) {
                manifest.colors.sideBackgroundTint?.let { value ->
                    parseUiSkinColor(value = value, fallback = Color.Transparent)
                }
            } else null,
            profileBackgroundImagePath = if (hasProfile) {
                activeSkin.assetFilePath(manifest.assets.homeProfileBackground)
            } else null,
            profileSquaredBackgroundImagePath = if (hasProfile) {
                activeSkin.assetFilePath(manifest.assets.homeProfileSquaredBackground)
            } else null,
            profileVideoBackgroundPath = if (hasProfile) {
                activeSkin.assetFilePath(manifest.assets.homeProfileVideoBackground)
            } else null,
            profileVideoPlayMode = manifest.motion.profileVideoPlayMode,
            liquidGlassSurfaceTint = manifest.colors.liquidGlassSurfaceTint?.let {
                parseUiSkinColor(it, Color.Unspecified).takeUnless { c -> c == Color.Unspecified }
            },
            liquidGlassTintOpacity = (manifest.colors.liquidGlassTintOpacity ?: 0.22f)
                .coerceIn(0.08f, 0.45f),
            colorMode = manifest.colors.colorMode,
        )
    }
}

@Composable
internal fun BottomBarSkinIcon(
    iconPath: String,
    contentDescription: String?,
    selected: Boolean = false,
    size: Dp = resolveBottomBarSkinDockIconSize(),
    modifier: Modifier = Modifier
) {
    val motion = LocalBottomBarSkinMotionSpec.current
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val looping = motion.enabled && selected && motion.mode.isLoopingSkinMotionMode() && animationGate.active
    val loopScale = if (looping) {
        rememberInfiniteTransition(label = "skinIconLoop").animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 720),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skinIconLoopScale",
        )
    } else {
        null
    }
    Box(modifier = modifier.size(size).then(animationGate.modifier)) {
        if (motion.enabled) {
            AnimatedContent(
                targetState = iconPath,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.86f)) togetherWith
                        fadeOut(tween(120))
                },
                // Selection may change scale/loop motion, but it must not replace the image
                // subtree when the destination still resolves to the same fixed asset.
                contentKey = { stableIconPath -> stableIconPath },
                label = "skinIconSelection",
                modifier = Modifier.fillMaxSize(),
            ) { stableIconPath ->
                SkinIconImage(
                    iconPath = stableIconPath,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = loopScale?.value ?: 1f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        } else {
            SkinIconImage(
                iconPath = iconPath,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun String?.isLoopingSkinMotionMode(): Boolean {
    return this?.trim()?.lowercase() in setOf("loop", "cycle", "repeat", "always")
}

@Composable
private fun SkinIconImage(
    iconPath: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    val scalePolicy = rememberSkinIconScalePolicy(iconPath)
    AsyncImage(
        model = File(iconPath),
        contentDescription = contentDescription,
        contentScale = scalePolicy.contentScale,
        alignment = scalePolicy.alignment,
        modifier = modifier,
    )
}

/**
 * 图标尺寸兼容策略：B 站装扮图标原图尺寸差异大（留白/装饰边/非正方形）。
 * 纯函数 [resolveSkinIconScalePolicy] 按原图宽高比决定缩放方式，便于单元测试。
 * 规则：正方形或近正方形 → Fit 居中；宽高比失衡（留白多）→ Crop 居中兜底，
 * 避免图标在 32dp 框内显得过小。
 */
data class SkinIconScalePolicy(
    val contentScale: ContentScale,
    val alignment: Alignment
)

private val ICON_ASPECT_TOLERANCE = 0.25f

fun resolveSkinIconScalePolicy(imageAspectRatio: Float): SkinIconScalePolicy {
    // imageAspectRatio = width / height；1.0 为正方形。
    val ratio = if (imageAspectRatio <= 0f) 1f else imageAspectRatio
    val balanced = ratio in (1f - ICON_ASPECT_TOLERANCE)..(1f + ICON_ASPECT_TOLERANCE)
    return if (balanced) {
        SkinIconScalePolicy(ContentScale.Fit, Alignment.Center)
    } else {
        SkinIconScalePolicy(ContentScale.Crop, Alignment.Center)
    }
}

@Composable
private fun rememberSkinIconScalePolicy(iconPath: String): SkinIconScalePolicy {
    // 默认用 Fit 居中（保留原图观感）；精确宽高比需异步解码，此处保守取平衡态。
    // 真实合成预览组件复用此策略，用户导入前即可看到尺寸效果。
    return remember(iconPath) { resolveSkinIconScalePolicy(1f) }
}

@Composable
internal fun BottomBarSkinDecorativeTrim(
    decoration: BottomBarUiSkinDecoration?,
    modifier: Modifier = Modifier,
    clipShape: androidx.compose.ui.graphics.Shape? = null,
    isFloating: Boolean = false,
    liquidGlassEnabled: Boolean = false,
    blurEnabled: Boolean = false,
) {
    if (decoration == null) return
    val isDark = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val imagePath = decoration.bottomTrimImagePath
    val hasArtwork = !imagePath.isNullOrBlank()
    // 显式隔离「沉底 vs 悬浮 × 开启/关闭液态玻璃」四种光学叠加权重：
    // 1. 沉底 + 关闭玻璃/模糊：100% 还原官方装扮实底与原画；
    // 2. 悬浮 + 关闭玻璃：胶囊轮廓内饱满着色与贴图裁切，四角不外露；
    // 3. 开启液态玻璃（悬浮或沉底）：在玻璃折射层之上叠加柔雾护底与高还原 tail_bg 贴图，
    //    既保留玻璃边缘高光与透镜折射，又防止底层高反差视频封面吞没角色图标与标签文字。
    val surfaceVeilColor = if (liquidGlassEnabled) {
        val rawTint = decoration.liquidGlassSurfaceTint ?: decoration.bottomTrimTint
        val frostBase = if (isDark) Color(0xFF141820) else Color(0xFFF8FAFD)
        androidx.compose.ui.graphics.lerp(frostBase, rawTint, 0.38f)
            .copy(alpha = if (hasArtwork) 0.54f else decoration.liquidGlassTintOpacity.coerceIn(0.22f, 0.44f))
    } else if (blurEnabled) {
        decoration.bottomTrimTint.copy(alpha = if (isFloating) 0.80f else 0.78f)
    } else if (isFloating) {
        decoration.bottomTrimTint.copy(alpha = 0.96f)
    } else {
        decoration.bottomTrimTint
    }
    val artworkAlpha = when {
        liquidGlassEnabled && isFloating -> 0.76f
        liquidGlassEnabled -> 0.82f
        blurEnabled -> 0.90f
        isFloating -> 0.96f
        else -> 1.0f
    }
    val accentMultiplier = if (liquidGlassEnabled) 0.42f else 1.0f

    Box(
        modifier = modifier
            .then(clipShape?.let { Modifier.clip(it) } ?: Modifier)
            .clearAndSetSemantics {}
            .drawBehind {
                drawRect(surfaceVeilColor)
                // 仅在缺失官方 tail_bg 原图时绘制几何氛围云边，避免污染官方原画纹理
                if (!hasArtwork) {
                    val trimHeight = size.height * 0.36f
                    val top = size.height - trimHeight
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                decoration.bottomTrimTint.copy(alpha = 0.08f * accentMultiplier),
                                decoration.bottomTrimTint.copy(alpha = 0.28f * accentMultiplier)
                            ),
                            startY = top,
                            endY = size.height
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, trimHeight),
                        cornerRadius = CornerRadius(trimHeight, trimHeight)
                    )
                }
            }
    ) {
        if (hasArtwork && imagePath != null) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                alpha = artworkAlpha,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {}
            )
        }
    }
}

private fun parseUiSkinColor(
    value: String?,
    fallback: Color
): Color {
    val normalized = value
        ?.trim()
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 || it.length == 8 }
        ?: return fallback
    val argb = if (normalized.length == 6) "FF$normalized" else normalized
    return runCatching { Color(argb.toLong(16)) }.getOrDefault(fallback)
}

private fun resolveBottomBarSkinIconPaths(
    activeSkin: com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage
): Map<BottomNavItem, BottomBarSkinIconPaths> {
    val manifestIcons = activeSkin.manifest.assets.bottomBarIcons
    val roleIcons = buildMap {
        linkedMapOf(
            "home" to ("home_selected" to BottomNavItem.HOME),
            "following" to ("following_selected" to BottomNavItem.DYNAMIC),
            "member" to ("member_selected" to BottomNavItem.HISTORY),
            "channel" to ("channel_selected" to BottomNavItem.LISTEN_VIDEO),
            "profile" to ("profile_selected" to BottomNavItem.PROFILE)
        ).forEach { (unselectedKey, selectedKeyAndItem) ->
            val (selectedKey, item) = selectedKeyAndItem
            activeSkin.assetFilePath(manifestIcons[unselectedKey])?.let { unselectedPath ->
                put(
                    item,
                    BottomBarSkinIconPaths(
                        unselected = unselectedPath,
                        selected = activeSkin.assetFilePath(manifestIcons[selectedKey])
                    )
                )
            }
        }
        if (BottomNavItem.LISTEN_VIDEO !in this) {
            activeSkin.assetFilePath(activeSkin.manifest.assets.homeChannelIcon)?.let { unselectedPath ->
                put(
                    BottomNavItem.LISTEN_VIDEO,
                    BottomBarSkinIconPaths(
                        unselected = unselectedPath,
                        selected = activeSkin.assetFilePath(
                            activeSkin.manifest.assets.homeChannelSelectedIcon
                        )
                    )
                )
            }
        }
    }
    if (roleIcons.isEmpty()) return emptyMap()

    // Imported Bilibili skins expose five artwork roles while BiliPai lets users choose
    // other destinations. Keep every visible slot themed by mapping those destinations
    // to the closest artwork role and falling back to an available skin icon.
    val preferredRole = mapOf(
        BottomNavItem.HOME to BottomNavItem.HOME,
        BottomNavItem.DYNAMIC to BottomNavItem.DYNAMIC,
        BottomNavItem.STORY to BottomNavItem.HISTORY,
        BottomNavItem.HISTORY to BottomNavItem.HISTORY,
        BottomNavItem.LISTEN_VIDEO to BottomNavItem.LISTEN_VIDEO,
        BottomNavItem.PROFILE to BottomNavItem.PROFILE,
        BottomNavItem.FAVORITE to BottomNavItem.HISTORY,
        BottomNavItem.LIVE to BottomNavItem.LISTEN_VIDEO,
        BottomNavItem.WATCHLATER to BottomNavItem.HISTORY,
        BottomNavItem.SETTINGS to BottomNavItem.PROFILE,
        BottomNavItem.PLUGINS to BottomNavItem.PROFILE,
    )
    val fallback = roleIcons[BottomNavItem.HOME] ?: roleIcons.values.first()
    return BottomNavItem.entries.associateWith { item ->
        roleIcons[preferredRole.getValue(item)] ?: fallback
    }
}

private fun resolveDistinctBottomBarSkinRolePool(
    activeSkin: com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage,
): List<BottomBarSkinIconPaths> {
    val manifestIcons = activeSkin.manifest.assets.bottomBarIcons
    val orderedPairs = listOf(
        "home" to "home_selected",
        "following" to "following_selected",
        "channel" to "channel_selected",
        "member" to "member_selected",
        "profile" to "profile_selected",
    )
    val pool = mutableListOf<BottomBarSkinIconPaths>()
    val seen = mutableSetOf<String>()
    orderedPairs.forEach { (unselectedKey, selectedKey) ->
        val u = activeSkin.assetFilePath(manifestIcons[unselectedKey]) ?: return@forEach
        val s = activeSkin.assetFilePath(manifestIcons[selectedKey])
        val sig = "$u|${s.orEmpty()}"
        if (seen.add(sig)) {
            pool.add(BottomBarSkinIconPaths(unselected = u, selected = s))
        }
    }
    activeSkin.assetFilePath(activeSkin.manifest.assets.homeChannelIcon)?.let { u ->
        val s = activeSkin.assetFilePath(activeSkin.manifest.assets.homeChannelSelectedIcon)
        if (seen.add("$u|${s.orEmpty()}")) {
            pool.add(BottomBarSkinIconPaths(unselected = u, selected = s))
        }
    }
    return pool
}
