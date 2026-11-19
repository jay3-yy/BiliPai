package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconContentColor
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconContainerColor
import com.android.purebilibili.core.ui.components.rememberAdaptiveSemanticIconTint
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold

private data class TipEntry(
    @DrawableRes val iconResId: Int,
    val iconTint: Color,
    @androidx.annotation.StringRes val titleResId: Int,
    @androidx.annotation.StringRes val contentResId: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsSettingsScreen(
    onBack: () -> Unit
) {
    val screenTitle = stringResource(R.string.tips_title)
    val backLabel = stringResource(R.string.common_back)
    val contentBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 32.dp
    )

    val glyphs = currentSettingsIconGlyphs()
    val rememberedSecondaryTextColor = AppContentColors.secondaryText()
    val basicTips = remember(rememberedSecondaryTextColor, glyphs) {
        listOf(
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_pencil_line_24, R.drawable.ms_draw_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_01_title,
                contentResId = R.string.tips_01_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_caret_up_24, R.drawable.ms_keyboard_arrow_up_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_02_title,
                contentResId = R.string.tips_02_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_vibrate_24, R.drawable.ms_touch_app_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_03_title,
                contentResId = R.string.tips_03_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_file_magnifying_glass_24, R.drawable.ms_find_in_page_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_04_title,
                contentResId = R.string.tips_04_content,
            )
        )
    }

    val hiddenTips = remember(rememberedSecondaryTextColor) {
        listOf(
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_skip_forward_24, R.drawable.ms_skip_next_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_05_title,
                contentResId = R.string.tips_05_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_camera_rotate_24, R.drawable.ms_cameraswitch_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_06_title,
                contentResId = R.string.tips_06_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_picture_in_picture_24, R.drawable.ms_picture_in_picture_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_07_title,
                contentResId = R.string.tips_07_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_thumbs_up_24, R.drawable.ms_thumb_up_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_08_title,
                contentResId = R.string.tips_08_content,
            )
        )
    }

    val advancedTips = remember(rememberedSecondaryTextColor) {
        listOf(
            TipEntry(
                iconResId = R.drawable.ms_flare_24,
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_09_title,
                contentResId = R.string.tips_09_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_tag_simple_24, R.drawable.ms_label_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_10_title,
                contentResId = R.string.tips_10_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_gift_24, R.drawable.ms_card_giftcard_fill_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_11_title,
                contentResId = R.string.tips_11_content,
            ),
            TipEntry(
                iconResId = glyphs.selectResource(R.drawable.ph_link_24, R.drawable.ms_link_24),
                iconTint = rememberedSecondaryTextColor,
                titleResId = R.string.tips_12_title,
                contentResId = R.string.tips_12_content,
            )
        )
    }

    EntranceGroup {
        SettingsPageScaffold(
            title = screenTitle,
            onBack = onBack,
            backContentDescription = backLabel,
            bottomContentPadding = contentBottomPadding,
            lazyListContent = {
            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle(stringResource(R.string.tips_section_basics))
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    TipSection(items = basicTips)
                }
            }

            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle(stringResource(R.string.tips_section_hidden))
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    TipSection(items = hiddenTips)
                }
            }

            item {
                Box(modifier = Modifier.entrance()) {
                    AppPreferenceSectionTitle(stringResource(R.string.tips_section_advanced))
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    TipSection(items = advancedTips)
                }
            }
        },
        )
    }
}

@Composable
private fun TipSection(items: List<TipEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(AppSurfaceTokens.cardContainer())
    ) {
        items.forEachIndexed { index, tip ->
            TipItem(
                iconResId = tip.iconResId,
                iconTint = tip.iconTint,
                titleResId = tip.titleResId,
                contentResId = tip.contentResId,
            )
            if (index != items.lastIndex) {
                TipDivider()
            }
        }
    }
}

@Composable
private fun TipItem(
    @DrawableRes iconResId: Int,
    iconTint: Color,
    @androidx.annotation.StringRes titleResId: Int,
    @androidx.annotation.StringRes contentResId: Int,
) {
    val title = stringResource(titleResId)
    val content = stringResource(contentResId)
    val icon = rememberSettingsVector(iconResId)
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(iconTint)
    val iconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX || com.android.purebilibili.core.ui.components.useColorfulPreferenceIcons()) {
            SettingsFunctionIcon(
                title = title,
                imageVector = icon,
            )
        } else Box(
            modifier = Modifier
                .size(36.dp)
                .clip(AppShapes.container(ContainerLevel.Field))
                .background(effectiveIconTint),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = iconContentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
        Column {
            AppText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText()
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
            AppText(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText()
            )
        }
    }
}

@Composable
private fun TipDivider() {
    AppHorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        thickness = 0.5.dp
    )
}
