package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.adaptiveSquircleBackground
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.rememberResolvedAppIconStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig

/** Opt-in at the settings boundary; other preferences and MD3 retain their defaults. */
val LocalAppPreferenceIconColors = staticCompositionLocalOf<Map<String, Color>?> { null }

@Composable
fun useColorfulPreferenceIcons(): Boolean = resolveUsesColorfulPreferenceIcons(
    rememberResolvedAppIconStyle(), LocalAppUiStyle.current, LocalAppPreferenceIconColors.current != null,
)

internal fun resolveUsesColorfulPreferenceIcons(style: AppIconStyle, uiStyle: AppUiStyle, settingsScope: Boolean): Boolean = when (style) {
    AppIconStyle.SOFT_COLORFUL, AppIconStyle.VIVID_COLORFUL -> true
    AppIconStyle.AUTO -> uiStyle == AppUiStyle.MIUIX && settingsScope
    else -> false
}

/** Soft colors lift toward white rather than mixing in a dull gray. */
internal fun resolveColorfulPreferenceColor(base: Color, style: AppIconStyle): Color =
    if (style == AppIconStyle.SOFT_COLORFUL) lerp(base, Color.White, 0.12f) else base

// Bright midtones keep the containers clear without washing out white glyphs.
val AppPreferenceIconPalette: List<Color> = listOf(
    Color(0xFF1CA5ED), Color(0xFFF09F1B), Color(0xFF55CA47), Color(0xFFC46EF4),
    Color(0xFF32C0CE), Color(0xFFF45255), Color(0xFF4F85F6), Color(0xFF8699AA),
    Color(0xFFF28B31), Color(0xFFEC76AB),
)

private val preferenceIconBottomColors = listOf(
    Color(0xFF1488E1), Color(0xFFE18A11), Color(0xFF27B434), Color(0xFF9F55DF),
    Color(0xFF1D9CC6), Color(0xFFDF3955), Color(0xFF3568E8), Color(0xFF6E8397),
    Color(0xFFE26314), Color(0xFFD94E91),
)

fun preferenceIconColorForKey(key: String): Color = when {
    // 个人功能入口按用途固定分色，同名入口在抽屉与资料页保持一致。
    key == "切换账号" -> AppPreferenceIconPalette[4]
    key == "历史记录" -> AppPreferenceIconPalette[7]
    key == "番剧影视" -> AppPreferenceIconPalette[3]
    key == "我的收藏" -> AppPreferenceIconPalette[1]
    key == "我的点赞" -> AppPreferenceIconPalette[9]
    key == "稍后再看" -> AppPreferenceIconPalette[2]
    key == "消息中心" -> AppPreferenceIconPalette[0]
    key == "退出登录" -> AppPreferenceIconPalette[5]
    key == "立即登录" -> AppPreferenceIconPalette[2]
    listOf("通知", "提醒", "警告", "屏蔽").any(key::contains) -> AppPreferenceIconPalette[5]
    listOf("隐私", "权限", "安全", "认证", "播放", "解码", "音频", "音量").any(key::contains) -> AppPreferenceIconPalette[2]
    listOf("语言", "主题", "外观", "颜色", "字体", "动画", "壁纸", "图标").any(key::contains) -> AppPreferenceIconPalette[3]
    listOf("缓存", "存储", "备份", "数据", "下载").any(key::contains) -> AppPreferenceIconPalette[6]
    listOf("省电", "电池", "打赏", "礼物").any(key::contains) -> AppPreferenceIconPalette[8]
    listOf("首页", "动态", "聊天", "评论", "互动").any(key::contains) -> AppPreferenceIconPalette[1]
    listOf("设备", "插件", "扩展").any(key::contains) -> AppPreferenceIconPalette[4]
    listOf("关于", "版本", "日志", "诊断", "声明", "构建").any(key::contains) -> AppPreferenceIconPalette[7]
    else -> AppPreferenceIconPalette[0]
}

/** Same title keeps its color when rows are reordered, filtered or shown in search. */
@Composable
internal fun colorfulPreferenceIconColor(key: String?, fallback: Color): Color {
    val colors = LocalAppPreferenceIconColors.current.orEmpty()
    val style = rememberResolvedAppIconStyle()
    if (key == null) return resolveColorfulPreferenceColor(fallback, style)
    // Some preference titles include their current value (e.g. "主题色：樱花粉").
    // Changing the value must not change the functional icon's category color.
    val stableKey = key.substringBefore('：').trim()
    return resolveColorfulPreferenceColor(colors[key] ?: colors[stableKey] ?: preferenceIconColorForKey(stableKey), style)
}

/** Shared by ordinary, switch, slider, menu and manually authored settings rows. */
@Composable
internal fun Modifier.appPreferenceIconBackground(color: Color, cornerRadius: Dp): Modifier {
    if (!useColorfulPreferenceIcons()) {
        return adaptiveSquircleBackground(color = color, cornerRadius = cornerRadius)
    }
    val glassBorder = LocalAppThemeConfig.current.liquidGlassEnabled
    val style = rememberResolvedAppIconStyle()
    val base = if (color == Color.Unspecified || color.alpha == 0f) AppPreferenceIconPalette.first() else color
    val paletteIndex = AppPreferenceIconPalette.indexOfFirst { resolveColorfulPreferenceColor(it, style) == base }
    val bottom = if (paletteIndex >= 0) resolveColorfulPreferenceColor(preferenceIconBottomColors[paletteIndex], style) else base
    return background(
        // Each hue owns its endpoints; the lower color is not a black overlay.
        brush = Brush.verticalGradient(listOf(base, bottom)),
        shape = RoundedCornerShape(10.dp),
    ).drawWithCache {
        val strokeWidth = 1.dp.toPx()
        val radius = CornerRadius((10.dp.toPx() - strokeWidth / 2).coerceAtLeast(0f))
        val border = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.12f)),
        )
        onDrawWithContent {
            drawContent()
            if (glassBorder) {
                drawRoundRect(
                    brush = border,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size((size.width - strokeWidth).coerceAtLeast(0f), (size.height - strokeWidth).coerceAtLeast(0f)),
                    cornerRadius = radius,
                    style = Stroke(strokeWidth),
                )
            }
        }
    }
}

/** Leading functional icon only. Navigation, status and preview glyphs stay native. */
@Composable
fun AppPreferenceLeadingIcon(
    title: String,
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null,
    nativeSize: Dp = 24.dp,
) {
    val style = rememberResolvedAppIconStyle()
    if (style == AppIconStyle.THEME_CONTAINER) {
        val spec = rememberAdaptiveListVisualCapabilities().componentSpec
        Box(
            modifier = modifier
                .size(spec.iconContainerSizeDp.dp)
                .adaptiveSquircleBackground(MaterialTheme.colorScheme.primaryContainer, spec.iconCornerRadiusDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (imageVector != null) AppIcon(imageVector, contentDescription, Modifier.size(spec.iconGlyphSizeDp.dp), MaterialTheme.colorScheme.onPrimaryContainer)
            else if (painter != null) AppIcon(painter, contentDescription, Modifier.size(spec.iconGlyphSizeDp.dp), MaterialTheme.colorScheme.onPrimaryContainer)
        }
        return
    }
    if (!useColorfulPreferenceIcons()) {
        val nativeTint = if (style == AppIconStyle.MD3_STANDARD) com.android.purebilibili.core.ui.LocalAppLineIconColor.current.let {
            if (it == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else it
        } else tint
        if (imageVector != null) AppIcon(imageVector, contentDescription, modifier.size(nativeSize), nativeTint)
        else if (painter != null) AppIcon(painter, contentDescription, modifier.size(nativeSize), nativeTint)
        return
    }
    val color = colorfulPreferenceIconColor(title, AppPreferenceIconPalette.first())
    Box(
        modifier = modifier
            .size(32.dp)
            .appPreferenceIconBackground(color, 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (imageVector != null) AppIcon(imageVector, contentDescription, Modifier.size(22.dp), Color.White)
        else if (painter != null) AppIcon(painter, contentDescription, Modifier.size(22.dp), Color.White)
    }
}
