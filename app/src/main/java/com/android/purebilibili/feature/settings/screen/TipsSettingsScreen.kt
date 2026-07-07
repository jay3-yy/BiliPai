package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSPink
import com.android.purebilibili.core.theme.iOSTeal
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.rememberAdaptiveSemanticIconTint
import com.android.purebilibili.core.ui.components.IOSSectionTitle
import com.android.purebilibili.feature.settings.ui.SettingsLargeTitleHeader
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*

private data class TipEntry(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val content: String
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

    val basicTips = remember {
        listOf(
            TipEntry(
                icon = CupertinoIcons.Filled.HandDraw,
                iconTint = iOSBlue,
                title = "1. 摸鱼模式：播放器缩小策略",
                content = "在播放设置里把「播放器缩小策略」设为竖屏、横屏、全部或暂停时，再按对应条件缩小播放器。"
            ),
            TipEntry(
                icon = CupertinoIcons.Default.ChevronUp,
                iconTint = iOSTeal,
                title = "2. 回顶部不用狂划",
                content = "首页刷太深时，双击底栏「首页」图标，或双击顶部频道标题，可快速回到列表顶部。"
            ),
            TipEntry(
                icon = CupertinoIcons.Filled.HandTap,
                iconTint = iOSOrange,
                title = "3. 封面长按有快捷操作",
                content = "长按视频卡片可快速预览，并直接执行「稍后再看」等常用动作。"
            ),
            TipEntry(
                icon = CupertinoIcons.Default.MagnifyingGlass,
                iconTint = iOSGreen,
                title = "4. 搜索不只搜视频",
                content = "搜索页可切换「视频 / UP主 / 番剧 / 直播」，还能按分区、排序、时长做精确筛选。"
            )
        )
    }

    val hiddenTips = remember {
        listOf(
            TipEntry(
                icon = CupertinoIcons.Filled.ForwardEnd,
                iconTint = iOSPink,
                title = "5. 关闭自动连播，结尾更安静",
                content = "在播放设置关闭「自动播放下一个」后，视频播完会停在结束态，不再自动连播。"
            ),
            TipEntry(
                icon = CupertinoIcons.Filled.ArrowTriangle2CirclepathCamera,
                iconTint = iOSBlue,
                title = "6. 自动横竖屏更省手",
                content = "开启「自动横竖屏切换」后，跟随手机方向自动进出全屏，单手看片更顺。"
            ),
            TipEntry(
                icon = CupertinoIcons.Filled.Pip,
                iconTint = iOSTeal,
                title = "7. 小窗和画中画是两种玩法",
                content = "播放设置中的「后台播放模式」可选择应用内小窗、系统画中画，或让两者同时生效；画中画需先授予权限。"
            ),
            TipEntry(
                icon = CupertinoIcons.Filled.HandThumbsup,
                iconTint = iOSOrange,
                title = "8. 双击点赞可按喜好开关",
                content = "若你容易误触，可在播放设置关闭「双击点赞」；喜欢快操作就保持开启。"
            )
        )
    }

    val advancedTips = remember {
        listOf(
            TipEntry(
                icon = CupertinoIcons.Default.Sparkles,
                iconTint = iOSBlue,
                title = "9. 空降助手能跳过片头广告",
                content = "在设置开启「空降助手」后，可自动跳过赞助/片头片尾；也可改成仅提示不自动跳过。"
            ),
            TipEntry(
                icon = CupertinoIcons.Filled.Tag,
                iconTint = iOSPink,
                title = "10. 版本号连点有彩蛋",
                content = "在设置页连续点击版本号会触发隐藏彩蛋提示，适合探索党。"
            ),
            TipEntry(
                icon = CupertinoIcons.Filled.Gift,
                iconTint = iOSOrange,
                title = "11. 趣味彩蛋可随时关闭",
                content = "如果不想看到趣味提示，可在设置里关闭「趣味彩蛋」，界面会更克制。"
            ),
            TipEntry(
                icon = CupertinoIcons.Default.Link,
                iconTint = iOSTeal,
                title = "12. 链接默认打开可一步到位",
                content = "在设置中配置「默认打开链接」后，点到 B 站链接可更稳定地直达应用内页面。"
            )
        )
    }

    EntranceGroup {
        SettingsPageScaffold(
            title = screenTitle,
            onBack = onBack,
            backContentDescription = backLabel,
            bottomContentPadding = contentBottomPadding,
            header = { SettingsLargeTitleHeader(title = screenTitle) },
            lazyListContent = {
            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("基础技巧")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    TipSection(items = basicTips)
                }
            }

            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("隐藏技巧")
                }
            }
            item {
                Box(modifier = Modifier.entrance()) {
                    TipSection(items = hiddenTips)
                }
            }

            item {
                Box(modifier = Modifier.entrance()) {
                    IOSSectionTitle("进阶玩法")
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
                icon = tip.icon,
                iconTint = tip.iconTint,
                title = tip.title,
                content = tip.content
            )
            if (index != items.lastIndex) {
                TipDivider()
            }
        }
    }
}

@Composable
private fun TipItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    content: String
) {
    val effectiveIconTint = rememberAdaptiveSemanticIconTint(iconTint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveIconTint,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TipDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        thickness = 0.5.dp
    )
}
