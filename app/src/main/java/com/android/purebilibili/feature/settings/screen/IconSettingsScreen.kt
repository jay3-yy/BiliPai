package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.components.AppSegmentOption

import android.widget.Toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.store.AppIconAppearance
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 *  应用图标设置二级页面
 *  iOS 风格设计优化
 */

// 图标选项数据
data class IconOption(@androidx.annotation.StringRes val nameResId: Int, val key: String, @androidx.annotation.StringRes val descResId: Int, val iconRes: Int)

// 分组定义
data class IconGroup(@androidx.annotation.StringRes val titleResId: Int, val icons: List<IconOption>)

internal fun resolveIconSettingsContainerColor(
    background: Color,
    surfaceVariant: Color
): Color {
    return lerp(
        background.copy(alpha = 1f),
        surfaceVariant.copy(alpha = 1f),
        0.3f
    ).copy(alpha = 1f)
}

// This function was implicitly requested to be moved here.
// Assuming its content based on common patterns for such a function.
fun getIconGroups(): List<IconGroup> {
    return listOf(
        IconGroup(
            titleResId = R.string.icon_group_featured,
            icons = listOf(
                IconOption(R.string.icon_blue_snow_maid, "icon_blue_snow_maid", R.string.icon_blue_snow_maid_description, R.mipmap.ic_launcher_blue_snow_maid_round),
                IconOption(R.string.icon_blue_snow_maid_announcement, "icon_blue_snow_maid_announcement", R.string.icon_blue_snow_maid_announcement_description, R.mipmap.ic_launcher_blue_snow_maid_announcement_round),
                IconOption(R.string.icon_blue_snow_maid_front, "icon_blue_snow_maid_front", R.string.icon_blue_snow_maid_front_description, R.mipmap.ic_launcher_blue_snow_maid_front_round),
                IconOption(R.string.icon_3d, "icon_3d", R.string.icon_3d_description, R.mipmap.ic_launcher_3d_round),
                IconOption(R.string.icon_bilipai, "icon_bilipai", R.string.icon_bilipai_description, R.mipmap.ic_launcher_bilipai_round),
                IconOption(R.string.icon_bilipai_pink, "icon_bilipai_pink", R.string.icon_bilipai_pink_description, R.mipmap.ic_launcher_bilipai_pink_round),
                IconOption(R.string.icon_bilipai_white, "icon_bilipai_white", R.string.icon_bilipai_white_description, R.mipmap.ic_launcher_bilipai_white_round),
                IconOption(R.string.icon_bilipai_monet, "icon_bilipai_monet", R.string.icon_bilipai_monet_description, R.mipmap.ic_launcher_bilipai_monet_round)
            )
        )
    )
}

internal fun resolveIconOptionPreviewRes(
    iconKey: String,
    appearance: AppIconAppearance
): Int {
    return when (iconKey to appearance) {
        "icon_3d" to AppIconAppearance.LIGHT -> R.mipmap.ic_launcher_3d_light_round
        "icon_3d" to AppIconAppearance.DARK -> R.mipmap.ic_launcher_3d_dark_round
        "icon_blue_snow_maid" to AppIconAppearance.LIGHT ->
            R.mipmap.ic_launcher_blue_snow_maid_light_round
        "icon_blue_snow_maid" to AppIconAppearance.DARK ->
            R.mipmap.ic_launcher_blue_snow_maid_dark_round
        "icon_blue_snow_maid_announcement" to AppIconAppearance.LIGHT ->
            R.mipmap.ic_launcher_blue_snow_maid_announcement_light_round
        "icon_blue_snow_maid_announcement" to AppIconAppearance.DARK ->
            R.mipmap.ic_launcher_blue_snow_maid_announcement_dark_round
        "icon_blue_snow_maid_front" to AppIconAppearance.LIGHT ->
            R.mipmap.ic_launcher_blue_snow_maid_front_light_round
        "icon_blue_snow_maid_front" to AppIconAppearance.DARK ->
            R.mipmap.ic_launcher_blue_snow_maid_front_dark_round
        else -> getIconGroups()
            .flatMap { it.icons }
            .first { it.key == iconKey }
            .iconRes
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val iconAppearance by SettingsManager.getAppIconAppearance(context)
        .collectAsStateWithLifecycle(initialValue = AppIconAppearance.FOLLOW_SYSTEM)
    val screenTitle = stringResource(R.string.icon_settings_title)
    val backLabel = stringResource(R.string.common_back)

    val iconGroups = getIconGroups()
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SettingsPageScaffold(
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backLabel,
        bottomContentPadding = bottomContentPadding,
        scrollHost = SettingsPageScrollHost.External,
    ) {
        IconSettingsContent(
            state = state,
            viewModel = viewModel,
            context = context,
            iconGroups = iconGroups,
            iconAppearance = iconAppearance,
        )
    }
}

// 提取 IconOption 和 IconGroup 以便 Content 使用 (如果它们在 Screen 内部定义，需要移出来或传递)
// 原代码中它们是在 Screen 内部定义的。我应该把它们移到顶层或 companion object，或者作为参数传递。
// 为简单起见，我把它们作为参数传递，或者在 Content 内部重新定义（如果有必要）。
// 但最好是把数据定义移出去。
// 不过为了最小化改动，我还是在 Content 重新定义或者接受 pass-in。
// 原代码 line 54-83 定义了数据。
// 我直接把 LazyVerticalGrid 的内容提取到 Content。

@Composable
fun IconSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    context: android.content.Context,
    iconGroups: List<IconGroup>,
    iconAppearance: AppIconAppearance
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val contentBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 24.dp
    )
    val appearanceFollowSystem = stringResource(R.string.icon_appearance_follow_system)
    val appearanceLight = stringResource(R.string.icon_appearance_light)
    val appearanceDark = stringResource(R.string.icon_appearance_dark)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = settingsScrollContentPadding(
            extraTop = 16.dp,
            extraBottom = contentBottomPadding,
            extraHorizontal = 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize()
    ) {
            // 提示信息
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(AppShapes.container(ContainerLevel.Card))
                        .background(AppSurfaceTokens.cardContainer())
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_info_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    AppText(
                        text = stringResource(R.string.icon_settings_apply_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.container(ContainerLevel.Card))
                        .background(AppSurfaceTokens.cardContainer())
                ) {
                    SettingsSingleChoicePreference(
                        title = stringResource(R.string.icon_appearance_title),
                        subtitle = stringResource(R.string.icon_appearance_summary),
                        options = listOf(
                            AppSegmentOption(AppIconAppearance.FOLLOW_SYSTEM, appearanceFollowSystem),
                            AppSegmentOption(AppIconAppearance.LIGHT, appearanceLight),
                            AppSegmentOption(AppIconAppearance.DARK, appearanceDark)
                        ),
                        selectedValue = iconAppearance,
                        onSelectionChange = viewModel::setAppIconAppearance
                    )
                }
            }

            iconGroups.forEach { group ->
                // 分组标题
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppText(
                        text = stringResource(group.titleResId),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AppContentColors.primaryText(),
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                    )
                }

                items(group.icons, key = { it.key }) { option ->
                    val isSelected = state.appIcon == option.key
                    val optionAccessibilityLabel = stringResource(option.nameResId) + ", " +
                        stringResource(option.descResId)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(AppShapes.container(ContainerLevel.Dialog))
                            .clickable {
                                if (!isSelected) {
                                    Toast.makeText(context, context.getString(R.string.icon_settings_switching), Toast.LENGTH_SHORT).show()
                                    viewModel.setAppIcon(option.key)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 图标主体
                            // iOS App Icon 形状: 连续曲率圆角 (Squircle)
                            val iconShape = AppShapes.container(ContainerLevel.Dialog)
                            AsyncImage(
                                model = resolveIconOptionPreviewRes(option.key, iconAppearance),
                                contentDescription = optionAccessibilityLabel,
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = iconShape,
                                        spotColor = Color.Black.copy(alpha = 0.15f)
                                    )
                                    .clip(iconShape)
                                    .then(
                                        if (isSelected) Modifier.border(
                                            width = 2.dp,
                                            color = AppContentColors.accent(),
                                            shape = iconShape
                                        ) else Modifier
                                    )
                            )

                            // 选中标记 (右下角悬浮)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSelected,
                                enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                            ) {
                                AppIcon(
                                    com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_check_circle_fill_24),
                                    contentDescription = null,
                                    tint = AppContentColors.accent(),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(AppSurfaceTokens.cardContainer(), androidx.compose.foundation.shape.CircleShape)
                                        .border(2.dp, AppSurfaceTokens.cardContainer(), androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        AppText(
                            text = stringResource(option.nameResId),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) AppContentColors.accent()
                                    else AppContentColors.primaryText(),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
