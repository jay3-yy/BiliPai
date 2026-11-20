package com.android.purebilibili.feature.settings

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.store.AboutBackgroundMode
import com.android.purebilibili.core.store.AboutBackgroundSettings
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.navigation3.LocalNavigationSharedSurfaceVisible
import top.yukonga.miuix.kmp.nav.core.LocalNavEntryContentVisible
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun miuixAboutHeroHeight() =
    (LocalConfiguration.current.screenHeightDp.dp * 0.52f).coerceIn(280.dp, 520.dp)

/** 默认渐变在首屏标识滚出时淡出，主题与自定义色继续随内容滚动。 */
@Composable
internal fun miuixAboutBackground(
    settings: AboutBackgroundSettings,
    fieldHeight: Dp,
    scrollOffset: () -> Float = { 0f },
    scrollProgress: () -> Float = { 0f },
    motionAllowed: Boolean = true,
): Modifier {
    val background = MiuixTheme.colorScheme.background
    val dark = background.luminance() < 0.5f
    val palette = when (settings.mode) {
        AboutBackgroundMode.DEFAULT -> return animatedMiuixAboutGradient(
            background = background,
            dark = dark,
            scrollProgress = scrollProgress,
            motionAllowed = motionAllowed,
        )
        AboutBackgroundMode.THEME -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        )
        AboutBackgroundMode.CUSTOM -> listOf(settings.topColor, settings.bottomColor, settings.glowColor)
            .map { lerp(background, Color(it), if (dark) 0.28f else 0.40f) }
        AboutBackgroundMode.NONE -> listOf(background, background, background)
    }
    return Modifier.drawWithCache {
        val fieldSize = Size(size.width, fieldHeight.toPx())
        val wash = Brush.verticalGradient(
            0f to palette[0],
            0.45f to palette[1],
            1f to background,
            endY = fieldSize.height,
        )
        val glow = Brush.radialGradient(
            colors = listOf(palette[2].copy(alpha = 0.8f), Color.Transparent),
            center = Offset(fieldSize.width * 0.32f, fieldSize.height * 0.48f),
            radius = fieldSize.maxDimension * 0.55f,
        )
        val fade = Brush.verticalGradient(
            0f to Color.Transparent,
            0.62f to Color.Transparent,
            1f to background,
            endY = fieldSize.height,
        )
        onDrawBehind {
            drawRect(background)
            translate(top = -scrollOffset()) {
                drawRect(brush = wash, size = fieldSize)
                drawRect(brush = glow, size = fieldSize)
                drawRect(brush = fade, size = fieldSize)
            }
        }
    }
}

private const val ABOUT_GRADIENT_CYCLE_NANOS = 7_000_000_000L

@Composable
private fun animatedMiuixAboutGradient(
    background: Color,
    dark: Boolean,
    scrollProgress: () -> Float,
    motionAllowed: Boolean,
): Modifier {
    var inViewport by remember { mutableStateOf(false) }
    val phase = remember { mutableFloatStateOf(0f) }
    val latestScrollProgress = rememberUpdatedState(scrollProgress)
    val gradientVisible by remember { derivedStateOf { latestScrollProgress.value() < 1f } }
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val pageActive = rememberVisualWorkActive() && LocalNavEntryContentVisible.current &&
        LocalNavigationSharedSurfaceVisible.current
    val animating = motionAllowed && motionEnabled && pageActive && inViewport && gradientVisible
    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        val initialPhase = phase.floatValue
        val startNanos = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { frameNanos ->
                val elapsed = (frameNanos - startNanos) % ABOUT_GRADIENT_CYCLE_NANOS
                phase.floatValue = (initialPhase + elapsed.toFloat() / ABOUT_GRADIENT_CYCLE_NANOS) % 1f
            }
        }
    }

    return Modifier
        .onVisibilityChanged(minFractionVisible = 0f) { inViewport = it }
        .clipToBounds()
        .drawWithCache {
            val base = if (dark) Color(0xFF151326) else Color(0xFFF7F4FC)
            val blue = aboutGlow(if (dark) Color(0xFF0B246F) else Color(0xFFDCE8FF))
            val purple = aboutGlow(if (dark) Color(0xFF2C2871) else Color(0xFFE6D8FA))
            val rose = aboutGlow(if (dark) Color(0xFF4E234E) else Color(0xFFF8DDEB))
            onDrawBehind {
                val progress = latestScrollProgress.value()
                if (progress >= 1f) {
                    drawRect(background)
                    return@onDrawBehind
                }
                val angle = (if (motionEnabled) phase.floatValue else 0f) * (2 * PI).toFloat()
                val x = cos(angle)
                val y = sin(angle)
                drawRect(base)
                drawAboutGlow(blue, Offset(0.04f + 0.08f * y, 0.57f + 0.08f * x), Size(1.08f, 1.01f))
                drawAboutGlow(purple, Offset(0.41f + 0.12f * y, -0.04f - 0.09f * x), Size(0.57f, 1.50f))
                drawAboutGlow(rose, Offset(0.62f - 0.13f * y, 0.03f + 0.05f * x), Size(0.52f, 0.78f))
                if (progress > 0f) drawRect(background, alpha = progress)
            }
        }
}

private fun aboutGlow(color: Color): Brush = Brush.radialGradient(
    0f to color,
    0.45f to color.copy(alpha = 0.72f),
    1f to color.copy(alpha = 0f),
    center = Offset.Zero,
    radius = 1f,
)

/** 按视口的宽和高分别缩放，宽屏和窄分屏都保留同样的颜色分布。 */
private fun DrawScope.drawAboutGlow(brush: Brush, center: Offset, radii: Size) {
    translate(left = size.width * center.x, top = size.height * center.y) {
        scale(scaleX = size.width * radii.width, scaleY = size.height * radii.height, pivot = Offset.Zero) {
            drawCircle(brush = brush, radius = 1f, center = Offset.Zero)
        }
    }
}

/** 字样跟随主题主色，保留页面与预览一致的字重、字距和颜色。 */
@Composable
internal fun MiuixAboutWordmark(
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val nameColor = lerp(
        MiuixTheme.colorScheme.onBackground,
        MaterialTheme.colorScheme.primary,
        0.82f,
    )
    AppText(
        text = "BiliPai",
        modifier = modifier,
        fontSize = if (compact) 32.sp else 36.sp,
        lineHeight = if (compact) 40.sp else 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
        color = nameColor,
        maxLines = 1,
        tapToCopyEnabled = false,
    )
}

@Composable
internal fun MiuixAboutOverview(
    versionName: String,
    appIconRes: Int,
    versionHint: String?,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHeroHeightChanged: (Int) -> Unit = {},
) {
    val copyVersion = rememberClipboardCopyHandler()
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = miuixAboutHeroHeight())
                .onSizeChanged { onHeroHeightChanged(it.height) }
                .padding(horizontal = 24.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth < 300.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 应用图标可能包含自适应图标的前景与背景层，使用 Drawable 资源加载。
                AsyncImage(
                    model = appIconRes,
                    contentDescription = "BiliPai 图标",
                    modifier = Modifier
                        .size(if (compact) 80.dp else 96.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(20.dp))
                MiuixAboutWordmark(compact = compact)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .appCombinedClickable(
                            longPressScene = com.android.purebilibili.core.ui.AppHapticScene.TEXT,
                            role = Role.Button,
                            onClickLabel = "查看版本",
                            onLongClickLabel = "复制版本号",
                            onClick = onVersionClick,
                            onLongClick = { copyVersion("v$versionName", "版本") },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = "$versionName (${BuildConfig.VERSION_CODE})",
                        tapToCopyEnabled = false,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    versionHint?.let {
                        AppText(
                            text = it,
                            tapToCopyEnabled = false,
                            style = MaterialTheme.typography.bodySmall,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        AboutDevelopersSection(modifier = Modifier.fillMaxWidth())
    }
}

/** 随机顺序在一次页面访问内保持稳定，避免重组时头像跳位。 */
@Composable
internal fun AboutDevelopersSection(modifier: Modifier = Modifier) {
    val developer = AboutDevelopers.first { it.githubLogin == "jay3-yy" }
    val otherLogins = rememberSaveable {
        AboutDevelopers.filter { it.githubLogin != developer.githubLogin }
            .shuffled().map { it.githubLogin }
    }
    val others = remember(otherLogins) {
        otherLogins.map { login -> AboutDevelopers.first { it.githubLogin == login } }
    }
    val uriHandler = LocalUriHandler.current
    val contributorsState = rememberLazyListState()
    Column(modifier = modifier) {
        AppPreferenceSectionTitle("开发者")
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClickLabel = "打开 jay3-yy 的 GitHub") {
                    uriHandler.openUri(developer.profileUrl)
                }
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.avatar_jay3_yy),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(CircleShape),
            )
            AppText(
                text = developer.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText(),
                tapToCopyEnabled = false,
            )
            AppText(
                text = "@${developer.githubLogin}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText(),
                tapToCopyEnabled = false,
            )
        }
        AppPreferenceSectionTitle("其他贡献者（随机排序）")
        SettingsCardGroup {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = contributorsState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(others, key = { it.githubLogin }, contentType = { "developer" }) {
                    AboutDeveloperItem(developer = it)
                }
            }
            AboutContributorsScrollIndicator(
                state = contributorsState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .height(3.dp),
            )
        }
    }
}

/** 贡献者等宽排列；位置读取只触发绘制，列表静止后无需逐帧任务。 */
@Composable
private fun AboutContributorsScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val color = AppContentColors.secondaryText()
    Spacer(modifier.drawBehind {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull() ?: return@drawBehind
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val step = item.size + info.mainAxisItemSpacing
        val content = info.totalItemsCount * step - info.mainAxisItemSpacing +
            info.beforeContentPadding + info.afterContentPadding
        if (content <= viewport) return@drawBehind
        val thumbWidth = (size.width * viewport / content).coerceAtLeast(24.dp.toPx())
            .coerceAtMost(size.width)
        val scroll = state.firstVisibleItemIndex * step + state.firstVisibleItemScrollOffset
        val progress = (scroll / (content - viewport)).coerceIn(0f, 1f)
        val offset = (size.width - thumbWidth) * progress
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - thumbWidth - offset else offset
        drawRoundRect(color.copy(alpha = 0.12f), cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(
            color = color.copy(alpha = 0.75f),
            topLeft = Offset(x, 0f),
            size = Size(thumbWidth, size.height),
            cornerRadius = CornerRadius(size.height / 2),
        )
    })
}

@Composable
internal fun AboutCommunitySection(
    onTelegramClick: () -> Unit,
    onTelegramGroupClick: () -> Unit,
    onDisclaimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AppPreferenceSectionTitle("社区与发布")
        SettingsCardGroup {
            AppPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.RELEASE_CHANNEL),
                title = "Telegram 频道",
                subtitle = "获取版本发布与更新通知",
                onClick = onTelegramClick,
            )
            AppPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTION_COMMENT),
                title = "Telegram 群组",
                subtitle = "交流使用体验与问题反馈",
                onClick = onTelegramGroupClick,
            )
            AppPreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.DISCLAIMER),
                title = "使用声明",
                onClick = onDisclaimerClick,
            )
        }
    }
}
