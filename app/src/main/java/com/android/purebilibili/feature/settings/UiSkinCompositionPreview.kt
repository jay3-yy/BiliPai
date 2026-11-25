package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage
import com.android.purebilibili.core.plugin.skin.UiSkinManifest
import com.android.purebilibili.core.plugin.skin.UiSkinState
import com.android.purebilibili.core.plugin.skin.rememberSkinRegionLuminance
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.OpticalContrastPalette
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.home.components.BottomBarSkinDecorativeTrim
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.IllustratedSkinBottomBar
import com.android.purebilibili.feature.home.components.resolveBottomBarUiSkinDecoration
import com.android.purebilibili.feature.home.components.usesIllustratedNavigation
import java.io.File

private enum class SkinPreviewScene(val label: String) {
    FLOATING_GLASS("悬浮玻璃"),
    PROGRESSIVE_BLUR("渐进顶栏"),
    DOCKED_PLAIN("沉底原画"),
}

data class UiSkinCompositionPreviewData(
    val displayName: String,
    val manifest: UiSkinManifest,
    val assetFiles: Map<String, String>,
    val darkMode: Boolean,
) {
    fun assetPath(assetPath: String?): String? {
        return assetPath?.let(assetFiles::get)
    }
}

data class UiSkinCompositionLayers(
    val bottomBarTrimImagePath: String?,
    val drawerBottomTrimImagePath: String?,
    val topAtmosphereImagePath: String?,
    val topTabBackgroundImagePath: String?,
    val profileBackgroundImagePath: String?,
    val profileSquaredBackgroundImagePath: String?,
    val profileVideoBackgroundPath: String?,
    val sideBackgroundImagePath: String?,
    val publishIconImagePath: String?,
    val bottomBarIconPaths: Map<String, String>,
    val bottomBarTrimTint: Color,
    val bottomSelectedTint: Color,
    val bottomUnselectedTint: Color,
    val topAtmosphereTint: Color,
    val topForegroundTint: Color,
    val searchCapsuleTint: Color,
    val hasBottomBarIcons: Boolean,
    val hasTopAtmosphere: Boolean,
)

fun resolveUiSkinCompositionLayers(data: UiSkinCompositionPreviewData): UiSkinCompositionLayers {
    val assets = data.manifest.assets
    val colors = data.manifest.colors
    val defaultFg = if (data.darkMode) Color.White else Color(0xFF1F2329)
    return UiSkinCompositionLayers(
        bottomBarTrimImagePath = data.assetPath(assets.bottomBarTrim),
        drawerBottomTrimImagePath = data.assetPath(assets.drawerBottomTrim),
        topAtmosphereImagePath = data.assetPath(assets.topAtmosphere),
        topTabBackgroundImagePath = data.assetPath(assets.homeTopTabBackground),
        profileBackgroundImagePath = data.assetPath(assets.homeProfileBackground),
        profileSquaredBackgroundImagePath = data.assetPath(assets.homeProfileSquaredBackground),
        profileVideoBackgroundPath = data.assetPath(assets.homeProfileVideoBackground),
        sideBackgroundImagePath = data.assetPath(assets.homeSideBackground),
        publishIconImagePath = data.assetPath(assets.dynamicPublishIcon),
        bottomBarIconPaths = assets.bottomBarIcons.mapValues { (_, path) ->
            data.assetPath(path) ?: path
        }.filterValues { it.isNotBlank() },
        bottomBarTrimTint = parsePreviewColor(colors.bottomBarTrimTint, defaultTrimTint(data.darkMode)),
        bottomSelectedTint = parsePreviewColor(colors.bottomBarSelectedTint, Color(0xFFFF6699)),
        bottomUnselectedTint = parsePreviewColor(colors.bottomBarIconTint, defaultFg.copy(alpha = 0.78f)),
        topAtmosphereTint = parsePreviewColor(colors.topAtmosphereTint, defaultAtmosphereTint(data.darkMode)),
        topForegroundTint = parsePreviewColor(colors.topForegroundTint, defaultFg),
        searchCapsuleTint = parsePreviewColor(colors.searchCapsuleTint, OpticalContrastPalette.Highlight),
        hasBottomBarIcons = assets.bottomBarIcons.isNotEmpty(),
        hasTopAtmosphere = assets.topAtmosphere != null || assets.homeTopTabBackground != null,
    )
}

private fun defaultTrimTint(dark: Boolean): Color =
    if (dark) Color(0xFF1B2A4A) else Color(0xFFEAF8FF)

private fun defaultAtmosphereTint(dark: Boolean): Color =
    if (dark) Color(0xFF13203A) else Color(0xFFDFF5FF)

fun parsePreviewColor(value: String?, fallback: Color): Color {
    val normalized = value?.trim()?.removePrefix("#")?.takeIf { it.length == 6 || it.length == 8 }
        ?: return fallback
    val argb = if (normalized.length == 6) "FF$normalized" else normalized
    return runCatching { Color(argb.toLong(16)) }.getOrDefault(fallback)
}

@Composable
fun UiSkinCompositionPreview(
    data: UiSkinCompositionPreviewData,
    modifier: Modifier = Modifier,
    showLiquidGlass: Boolean = true,
) {
    var selectedScene by remember(showLiquidGlass) {
        mutableStateOf(if (showLiquidGlass) SkinPreviewScene.FLOATING_GLASS else SkinPreviewScene.DOCKED_PLAIN)
    }
    val layers = remember(data) { resolveUiSkinCompositionLayers(data) }
    val decoration = remember(data) {
        resolveBottomBarUiSkinDecoration(
            UiSkinState(
                enabled = true,
                activeSkin = InstalledUiSkinPackage(
                    manifest = data.manifest,
                    packageSha256 = "preview",
                    packagePath = "",
                    installedAtMillis = 0L,
                    assetFiles = data.assetFiles,
                ),
            ),
            isDark = data.darkMode,
        )
    }
    val sampledTopLum by rememberSkinRegionLuminance(
        filePath = layers.topAtmosphereImagePath,
        topFraction = 0f,
        bottomFraction = 0.28f,
    )
    val useDarkStatusIcons = when {
        sampledTopLum != null -> sampledTopLum!! > 0.52f
        layers.topForegroundTint.luminance() < 0.42f -> true
        else -> !data.darkMode
    }

    val isDockedPlain = selectedScene == SkinPreviewScene.DOCKED_PLAIN
    val isGlassScene = selectedScene == SkinPreviewScene.FLOATING_GLASS
    val illustratedDocked = isDockedPlain && decoration.usesIllustratedNavigation(isTablet = false)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 顶部多场景切换胶囊
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkinPreviewScene.entries.forEach { scene ->
                val active = selectedScene == scene
                AppSurface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { selectedScene = scene },
                ) {
                    AppText(
                        text = scene.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            AppContentColors.secondaryText()
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.container(ContainerLevel.Card))
                .background(if (data.darkMode) Color(0xFF0E1422) else Color(0xFFF3F5F9)),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            PreviewTopAtmosphere(
                layers = layers,
                feathered = !isDockedPlain,
                useDarkStatusIcons = useDarkStatusIcons,
                darkMode = data.darkMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
            )
            PreviewExtendedSurfaces(layers = layers)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (illustratedDocked) 0.dp else 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (illustratedDocked && decoration != null) {
                    IllustratedSkinBottomBar(
                        decoration = decoration,
                        currentItem = BottomNavItem.HOME,
                        visibleItems = listOf(
                            BottomNavItem.HOME,
                            BottomNavItem.DYNAMIC,
                            BottomNavItem.LISTEN_VIDEO,
                            BottomNavItem.PROFILE,
                        ),
                        onItemClick = {},
                        includeNavigationInset = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PreviewFloatingOrDockedBar(
                        layers = layers,
                        decoration = decoration,
                        dockShape = if (isDockedPlain) RoundedCornerShape(0.dp) else RoundedCornerShape(30.dp),
                        dockHeight = previewDockHeight(),
                        iconSize = previewDockIconSize(),
                        liquidGlass = isGlassScene,
                        darkMode = data.darkMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewExtendedSurfaces(
    layers: UiSkinCompositionLayers,
) {
    val hasDrawer = !layers.sideBackgroundImagePath.isNullOrBlank() ||
        !layers.drawerBottomTrimImagePath.isNullOrBlank()
    val profilePath = layers.profileBackgroundImagePath ?: layers.profileSquaredBackgroundImagePath
    val hasProfile = !profilePath.isNullOrBlank() || !layers.profileVideoBackgroundPath.isNullOrBlank()
    val hasPublish = !layers.publishIconImagePath.isNullOrBlank()
    if (!hasDrawer && !hasProfile && !hasPublish) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasProfile && profilePath != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AsyncImage(
                    model = File(profilePath),
                    contentDescription = "个人页背景",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 64.dp, height = 40.dp)
                        .clip(AppShapes.container(ContainerLevel.Chip)),
                )
                AppText(
                    text = "空间/个人页背景",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                )
            }
        }
        if (hasDrawer) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 40.dp)
                    .clip(AppShapes.container(ContainerLevel.Chip))
                    .background(layers.topAtmosphereTint.copy(alpha = 0.28f)),
            ) {
                layers.sideBackgroundImagePath?.let { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = "侧栏背景",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        layers.publishIconImagePath?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = "中位发布图标",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

fun previewDockHeight(): Dp = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large

fun previewDockIconSize(): Dp = AppSpacingTokens.DoubleExtraLarge

@Composable
private fun PreviewTopAtmosphere(
    layers: UiSkinCompositionLayers,
    feathered: Boolean,
    useDarkStatusIcons: Boolean,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusColor = if (useDarkStatusIcons) Color(0xFF1A1D24) else Color.White
    val pillBg = if (darkMode) Color(0xB81C2230) else Color(0xD8FFFFFF)
    val pillFg = if (darkMode) Color.White else Color(0xFF1F2329)

    Box(modifier = modifier) {
        val atmospherePath = layers.topAtmosphereImagePath ?: layers.topTabBackgroundImagePath
        if (!atmospherePath.isNullOrBlank()) {
            AsyncImage(
                model = File(atmospherePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (feathered) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.00f to Color.Black,
                                            0.56f to Color.Black,
                                            0.84f to Color.Black.copy(alpha = 0.42f),
                                            1.00f to Color.Transparent,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                        } else Modifier
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                layers.topAtmosphereTint,
                                layers.topAtmosphereTint.copy(alpha = if (feathered) 0f else 0.55f),
                            )
                        )
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 仿真状态栏（直观核验浅底深字 / 深底浅字）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = "14:16",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
                AppText(
                    text = "5G · 100%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                )
            }
            // 仿真搜索栏行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(pillBg),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillBg)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AppText(
                        text = "🔍 搜索感兴趣的内容",
                        fontSize = 10.sp,
                        color = pillFg.copy(alpha = 0.72f),
                    )
                }
            }
            // 仿真分类胶囊栏
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .then(
                            if (feathered) {
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(pillBg)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            } else {
                                Modifier.padding(vertical = 3.dp)
                            }
                        ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tabTextColor = if (feathered) pillFg else layers.topForegroundTint
                    listOf("直播", "推荐", "关注", "追番", "分区").forEachIndexed { idx, tab ->
                        AppText(
                            text = tab,
                            fontSize = 10.5.sp,
                            fontWeight = if (idx == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (idx == 1) tabTextColor else tabTextColor.copy(alpha = 0.76f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewFloatingOrDockedBar(
    layers: UiSkinCompositionLayers,
    decoration: com.android.purebilibili.feature.home.components.BottomBarUiSkinDecoration?,
    dockShape: Shape,
    dockHeight: Dp,
    iconSize: Dp,
    liquidGlass: Boolean,
    darkMode: Boolean,
) {
    val previewTabs = listOf(
        BottomNavItem.HOME to "推荐",
        BottomNavItem.DYNAMIC to "动态",
        BottomNavItem.LISTEN_VIDEO to "频道",
        BottomNavItem.PROFILE to "我的",
    )
    val visibleItems = remember { previewTabs.map { it.first } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dockHeight)
            .clip(dockShape)
            .background(if (darkMode) Color(0xFF141926) else Color(0xE6FFFFFF)),
    ) {
        if (decoration != null) {
            BottomBarSkinDecorativeTrim(
                decoration = decoration,
                clipShape = dockShape,
                isFloating = true,
                liquidGlassEnabled = liquidGlass,
                blurEnabled = true,
                modifier = Modifier.matchParentSize(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            previewTabs.forEachIndexed { idx, (navItem, label) ->
                val selected = idx == 0
                val iconPath = decoration?.iconPathFor(
                    item = navItem,
                    selected = selected,
                    visibleItems = visibleItems,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (!iconPath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(iconPath),
                            contentDescription = label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(iconSize - 2.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(iconSize - 4.dp)
                                .clip(AppShapes.container(ContainerLevel.Chip))
                                .background(layers.bottomBarTrimTint.copy(alpha = 0.35f)),
                        )
                    }
                    AppText(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) layers.bottomSelectedTint else layers.bottomUnselectedTint,
                    )
                }
            }
        }
    }
}
