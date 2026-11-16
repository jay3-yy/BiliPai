package com.android.purebilibili.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import coil3.compose.AsyncImage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.store.AboutBackgroundMode
import com.android.purebilibili.core.store.AboutBackgroundSettings
import androidx.compose.ui.layout.ContentScale
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
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun miuixAboutHeroHeight() =
    (LocalConfiguration.current.screenHeightDp.dp * 0.52f).coerceIn(280.dp, 520.dp)

/** 整页与编辑预览共用同一静态渐变；滚动值仅在绘制阶段读取。 */
@Composable
internal fun miuixAboutBackground(
    settings: AboutBackgroundSettings,
    fieldHeight: Dp,
    scrollOffset: () -> Float = { 0f },
): Modifier {
    val background = MiuixTheme.colorScheme.background
    val dark = background.luminance() < 0.5f
    val colors = MaterialTheme.colorScheme
    val defaultPalette = remember(colors.primary, dark) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(colors.primary.toArgb(), hsv)
        val hue = hsv[0]
        // 默认渐变保留主题色相，单独控制饱和度与亮度，避免容器色叠加后整页发灰。
        if (dark) listOf(
            Color.hsv(hue, 0.78f, 0.46f),
            Color.hsv((hue + 10f) % 360f, 0.70f, 0.34f),
            Color.hsv((hue + 24f) % 360f, 0.72f, 0.62f),
        ) else listOf(
            Color.hsv(hue, 0.25f, 0.96f),
            Color.hsv((hue + 10f) % 360f, 0.16f, 0.99f),
            Color.hsv((hue + 24f) % 360f, 0.32f, 1f),
        )
    }
    val palette = when (settings.mode) {
        // 默认背景从当前主题推导，动态取色、预设主题与深浅色切换共用同一入口。
        AboutBackgroundMode.DEFAULT -> defaultPalette
        AboutBackgroundMode.THEME -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        )
        // 自选颜色先融入页面底色，保持浅色与深色模式下标题的可读性。
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
        // 默认渐变以两处柔光形成宽色域，底部仍融入页面；自定义与主题模式保留原有绘制。
        val defaultHighlight = if (settings.mode == AboutBackgroundMode.DEFAULT) {
            Brush.radialGradient(
                colors = listOf(palette[0].copy(alpha = 0.65f), Color.Transparent),
                center = Offset(fieldSize.width * 0.88f, fieldSize.height * 0.18f),
                radius = fieldSize.maxDimension * 0.48f,
            )
        } else null
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
                defaultHighlight?.let { drawRect(brush = it, size = fieldSize) }
                drawRect(brush = fade, size = fieldSize)
            }
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
    onCustomizeBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copyVersion = rememberClipboardCopyHandler()
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = miuixAboutHeroHeight())
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
                        .combinedClickable(
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
                AppTextButton(onClick = onCustomizeBackground) {
                    AppText("自定义背景", tapToCopyEnabled = false)
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
