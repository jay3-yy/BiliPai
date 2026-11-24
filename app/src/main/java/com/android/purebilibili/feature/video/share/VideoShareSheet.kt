package com.android.purebilibili.feature.video.share

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.SmartDisplay
import coil3.compose.AsyncImage
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.components.AppNativeSegmentedControl
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.core.ui.common.copyPlainTextToClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.AppGlassMenuSource

@Composable
internal fun VideoShareSheetHost(
    payload: VideoSharePayload?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    glassSource: AppGlassMenuSource? = null,
) {
    payload?.let { sharePayload ->
        VideoShareSheet(
            payload = sharePayload,
            onDismiss = onDismiss,
            modifier = modifier,
            glassSource = glassSource,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VideoShareSheet(
    payload: VideoSharePayload,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    glassSource: AppGlassMenuSource? = null,
) {
    val sourcePayload = payload
    var preciseShare by remember(sourcePayload.bvid, sourcePayload.page) { mutableStateOf(false) }
    val effectivePayload = sourcePayload.withPosition(preciseShare)
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLandscape = isLandscapeVideoShare()
    var sharingTarget by remember { mutableStateOf<VideoShareTarget?>(null) }
    var savingCardToGallery by remember { mutableStateOf(false) }
    var switchingSheet by remember { mutableStateOf(false) }
    var showFollowingPicker by remember { mutableStateOf(false) }
    var showDynamicComposer by remember { mutableStateOf(false) }
    var showMoreTargets by remember { mutableStateOf(false) }
    var moreShareMedia by remember { mutableStateOf<VideoShareCoverFile?>(null) }
    var shareStyle by remember { mutableStateOf(VideoShareStyle.LINK) }
    val neutralIconBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val neutralIconContent = AppContentColors.primaryText()
    val styleOptions = remember {
        listOf(
            AppSegmentOption(VideoShareStyle.LINK, "链接"),
            AppSegmentOption(VideoShareStyle.CARD, "卡片"),
        )
    }
    val items = listOf(
        VideoShareSheetItem(
            target = VideoShareTarget.BILIBILI_DYNAMIC,
            label = "分享到动态",
            iconText = null,
            iconVector = Icons.Outlined.DynamicFeed,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        VideoShareSheetItem(
            target = VideoShareTarget.BILIBILI_FRIENDS,
            label = "B 站好友",
            iconText = null,
            iconVector = Icons.Outlined.People,
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        VideoShareSheetItem(
            target = VideoShareTarget.WECHAT,
            label = "微信",
            iconText = "微",
            iconVector = null,
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        VideoShareSheetItem(
            target = VideoShareTarget.QQ,
            label = "QQ",
            iconText = "Q",
            iconVector = null,
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        VideoShareSheetItem(
            target = VideoShareTarget.COPY_LINK,
            label = "复制链接",
            iconText = null,
            iconVector = Icons.Outlined.Link,
            backgroundColor = neutralIconBackground,
            contentColor = neutralIconContent
        ),
        VideoShareSheetItem(
            target = VideoShareTarget.MORE,
            label = "更多",
            iconText = null,
            iconVector = Icons.Outlined.MoreHoriz,
            backgroundColor = neutralIconBackground,
            contentColor = neutralIconContent
        )
    )

    if (showDynamicComposer) {
        VideoShareToDynamicDialog(payload = effectivePayload, onDismiss = onDismiss)
        return
    }
    if (showFollowingPicker) {
        VideoShareToFollowingDialog(
            payload = effectivePayload,
            onDismiss = onDismiss,
            onSuccess = { count ->
                if (count > 0) VideoShareFeedbackEvents.prepared(payload.bvid)
                Toast.makeText(context, "已发送给 $count 位 B 站好友", Toast.LENGTH_SHORT).show()
            },
        )
        return
    }
    if (showMoreTargets) {
        VideoShareMoreTargetsSheet(
            shareMedia = moreShareMedia,
            onDismiss = onDismiss,
            onTargetClick = { target ->
                context.startTargetedVideoShare(
                    payload = effectivePayload,
                    packageName = target.packageName,
                    appName = target.label,
                    shareMedia = moreShareMedia,
                    preferredActivityClassName = target.activityClassName,
                )
                onDismiss()
            },
            onSystemChooserClick = {
                context.startMoreVideoShare(effectivePayload, moreShareMedia)
                onDismiss()
            },
        )
        return
    }

    @Suppress("UNUSED_VARIABLE")
    val ignoredGlassSource = glassSource
    val panelScrollState = rememberScrollState()
    val actionsEnabled = !switchingSheet && sharingTarget == null && !savingCardToGallery
    val resultHaptic = com.android.purebilibili.core.util.rememberHapticFeedback(AppHapticScene.RESULTS)
    suspend fun hideSharePanel() {
        hideVideoShareSheet(sheetState)
    }

    AppModalBottomSheet(
        onDismissRequest = { if (actionsEnabled) onDismiss() },
        modifier = modifier,
        presentationOverride = videoSharePresentation(isLandscape),
        sheetState = sheetState,
        dragHandle = if (isLandscape) null else ({ BottomSheetDefaults.DragHandle() }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLandscape) {
                        Modifier.fillMaxHeight()
                    } else {
                        Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                    },
                ),
        ) {
            // 顶栏：左侧标题 + 右侧紧凑「链接 / 卡片」模式切换（横屏补充关闭按钮）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = if (isLandscape) 12.dp else 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = "分享",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                BoxWithConstraints(Modifier.widthIn(min = 128.dp, max = 164.dp)) {
                    if (LocalAppThemeConfig.current.liquidGlassEnabled) {
                        BottomBarLiquidSegmentedControl(
                            liquidGlassAdaptIndicatorToLabel = false,
                            items = styleOptions.map { it.label },
                            selectedIndex = shareStyle.ordinal,
                            onSelected = { shareStyle = VideoShareStyle.entries[it] },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = actionsEnabled,
                            forceEqualWidth = true,
                        )
                    } else {
                        AppNativeSegmentedControl(
                            options = styleOptions,
                            selectedValue = shareStyle,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = actionsEnabled,
                            itemWidth = if (isMiuixNonGlassEnabled()) maxWidth / styleOptions.size else null,
                            onSelectionChange = { shareStyle = it },
                        )
                    }
                }
                if (isLandscape) {
                    com.android.purebilibili.core.ui.components.AppTextButton(
                        onClick = onDismiss,
                        enabled = actionsEnabled,
                    ) { AppText("关闭") }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = isLandscape)
                    .verticalScroll(panelScrollState)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                // 视频信息预览卡：直观展示封面、标题、UP 主与当前分享形态
                VideoSharePreviewCard(
                    payload = effectivePayload,
                    shareStyle = shareStyle,
                    isPreparingCard = savingCardToGallery || (sharingTarget != null && shareStyle == VideoShareStyle.CARD),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                // 分享渠道网格
                VideoShareTargets(sidePanel = isLandscape, modifier = Modifier.fillMaxWidth()) {
                    items.forEach { item ->
                        val onItemClick = appActionWithFeedback(AppHapticScene.VIDEO_ACTIONS) {
                            if (sharingTarget != null || switchingSheet) return@appActionWithFeedback
                            when (item.target) {
                                VideoShareTarget.BILIBILI_DYNAMIC -> {
                                    if (TokenManager.csrfCache.isNullOrBlank()) {
                                        Toast.makeText(context, "请先登录后分享到动态", Toast.LENGTH_SHORT).show()
                                        return@appActionWithFeedback
                                    }
                                    switchingSheet = true
                                    shareScope.launch {
                                        hideSharePanel()
                                        showDynamicComposer = true
                                    }
                                }
                                VideoShareTarget.BILIBILI_FRIENDS -> {
                                    switchingSheet = true
                                    shareScope.launch {
                                        hideSharePanel()
                                        showFollowingPicker = true
                                    }
                                }
                                VideoShareTarget.WECHAT,
                                VideoShareTarget.QQ -> {
                                    val packageName = item.target.packageName ?: return@appActionWithFeedback
                                    sharingTarget = item.target
                                    shareScope.launch {
                                        try {
                                            val shareMedia = prepareVideoShareMedia(
                                                context = context,
                                                payload = effectivePayload,
                                                style = shareStyle,
                                                progressMessage = "正在生成分享卡片",
                                            )
                                            hideSharePanel()
                                            context.startTargetedVideoShare(
                                                payload = effectivePayload,
                                                packageName = packageName,
                                                appName = item.label,
                                                shareMedia = shareMedia,
                                            )
                                        } finally {
                                            sharingTarget = null
                                            onDismiss()
                                        }
                                    }
                                }
                                VideoShareTarget.COPY_LINK -> {
                                    copyPlainTextToClipboard(context, effectivePayload.url, "视频链接")
                                    VideoShareFeedbackEvents.prepared(payload.bvid)
                                    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                                    shareScope.launch {
                                        hideSharePanel()
                                        onDismiss()
                                    }
                                }
                                VideoShareTarget.MORE -> {
                                    sharingTarget = item.target
                                    shareScope.launch {
                                        var openedLocalTargets = false
                                        try {
                                            val shareMedia = prepareVideoShareMedia(
                                                context = context,
                                                payload = effectivePayload,
                                                style = shareStyle,
                                                progressMessage = "正在生成分享卡片",
                                            )
                                            hideSharePanel()
                                            if (isLandscape) {
                                                moreShareMedia = shareMedia
                                                showMoreTargets = true
                                                openedLocalTargets = true
                                            } else {
                                                context.startMoreVideoShare(effectivePayload, shareMedia)
                                            }
                                        } finally {
                                            sharingTarget = null
                                            if (!openedLocalTargets) onDismiss()
                                        }
                                    }
                                }
                            }
                        }
                        VideoShareSheetItemView(
                            item = item,
                            modifier = Modifier.weight(1f).fillMaxRowHeight(),
                            enabled = actionsEnabled,
                            onClick = onItemClick,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 快捷操作组：保存分享卡片（卡片模式）+ 空降时间点 + 复制 BV 号 + 在其他应用中打开
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    if (shareStyle == VideoShareStyle.CARD) {
                        val saveCardClick = appActionWithFeedback(AppHapticScene.RESULTS) {
                            if (!actionsEnabled) return@appActionWithFeedback
                            savingCardToGallery = true
                            shareScope.launch {
                                try {
                                    val saved = saveVideoShareCardToGallery(context, effectivePayload)
                                    if (saved) {
                                        resultHaptic(com.android.purebilibili.core.util.HapticType.MEDIUM)
                                        VideoShareFeedbackEvents.prepared(payload.bvid)
                                        Toast.makeText(context, "已保存分享卡片到相册", Toast.LENGTH_SHORT).show()
                                        hideSharePanel()
                                        onDismiss()
                                    } else {
                                        resultHaptic(com.android.purebilibili.core.util.HapticType.HEAVY)
                                        Toast.makeText(context, "保存分享卡片失败", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    savingCardToGallery = false
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(enabled = actionsEnabled, role = Role.Button, onClick = saveCardClick)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (savingCardToGallery) {
                                AppCircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                AppIcon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            AppText(
                                text = if (savingCardToGallery) "正在保存分享卡片…" else "保存分享卡片到相册",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        AppHorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                    if (sourcePayload.positionMs > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = preciseShare,
                                    enabled = actionsEnabled,
                                    role = Role.Checkbox,
                                    onValueChange = { preciseShare = it },
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppCheckbox(
                                checked = preciseShare,
                                onCheckedChange = null,
                                enabled = actionsEnabled,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                            AppText(
                                text = "从 ${sourcePayload.positionMs / 60000}:${(sourcePayload.positionMs / 1000 % 60).toString().padStart(2, '0')} 开始播放",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppContentColors.primaryText(),
                            )
                        }
                        AppHorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                    if (effectivePayload.bvid.isNotBlank()) {
                        val copyBvidClick = appActionWithFeedback(AppHapticScene.TEXT) {
                            copyPlainTextToClipboard(context, effectivePayload.bvid, "BV号")
                            Toast.makeText(context, "已复制 ${effectivePayload.bvid}", Toast.LENGTH_SHORT).show()
                            shareScope.launch {
                                hideSharePanel()
                                onDismiss()
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(enabled = actionsEnabled, role = Role.Button, onClick = copyBvidClick)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = AppContentColors.secondaryText(),
                            )
                            AppText(
                                text = "复制 BV 号",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppContentColors.primaryText(),
                                modifier = Modifier.weight(1f),
                            )
                            AppText(
                                text = effectivePayload.bvid,
                                style = MaterialTheme.typography.labelMedium,
                                color = AppContentColors.secondaryText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AppHorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                    val openExternalClick = appActionWithFeedback(AppHapticScene.CONTROLS) {
                        shareScope.launch {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(effectivePayload.url)))
                                onDismiss()
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = actionsEnabled, role = Role.Button, onClick = openExternalClick)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = AppContentColors.secondaryText(),
                        )
                        AppText(
                            text = "在其他应用中打开",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppContentColors.primaryText(),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VideoSharePreviewCard(
    payload: VideoSharePayload,
    shareStyle: VideoShareStyle,
    isPreparingCard: Boolean,
    modifier: Modifier = Modifier,
) {
    val metaLine = remember(payload.upName, payload.bvid, payload.page) {
        buildList {
            if (payload.upName.isNotBlank()) add(payload.upName)
            if (payload.bvid.isNotBlank()) {
                add(if (payload.page > 1) "${payload.bvid} · P${payload.page}" else payload.bvid)
            }
        }.joinToString(" · ")
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (payload.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = payload.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                )
            } else {
                AppIcon(
                    imageVector = Icons.Outlined.SmartDisplay,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = AppContentColors.secondaryText(),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = payload.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = AppContentColors.primaryText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (metaLine.isNotBlank()) {
                    AppText(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (isPreparingCard) {
                    AppCircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                    )
                } else {
                    AppText(
                        text = if (shareStyle == VideoShareStyle.CARD) "图片卡片" else "直达链接",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoShareTargets(
    sidePanel: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = when {
            !sidePanel && maxWidth >= 540.dp * fontScale -> 6
            maxWidth >= 228.dp * fontScale -> 3
            else -> 2
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Immutable
private data class VideoShareSheetItem(
    val target: VideoShareTarget,
    val label: String,
    val iconText: String?,
    val iconVector: ImageVector?,
    val backgroundColor: Color,
    val contentColor: Color,
)

@Composable
private fun VideoShareSheetItemView(
    item: VideoShareSheetItem,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appIcon = remember(context, item.target) {
        item.target.packageName?.let { packageName ->
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (appIcon == null) item.backgroundColor else Color.Transparent)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                AndroidView(
                    factory = { viewContext -> ImageView(viewContext).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    } },
                    update = { it.setImageDrawable(appIcon) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                )
            } else if (item.iconVector != null) {
                if (item.target == VideoShareTarget.BILIBILI_FRIENDS) {
                    Icon(
                        imageVector = item.iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = item.contentColor,
                    )
                } else {
                    AppIcon(
                        imageVector = item.iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = item.contentColor,
                    )
                }
            } else {
                AppText(
                    text = item.iconText.orEmpty(),
                    color = item.contentColor,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        AppText(
            text = item.label,
            color = AppContentColors.primaryText(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal suspend fun hideVideoShareSheet(
    sheetState: androidx.compose.material3.SheetState
) {
    try {
        if (sheetState.isVisible) {
            sheetState.hide()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // A disappearing host can make hide fail; the caller still completes its action.
    }
}

private suspend fun saveVideoShareCardToGallery(
    context: Context,
    payload: VideoSharePayload,
): Boolean {
    val cardFile = prepareVideoShareCardFile(context, payload) ?: return false
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = "BiliPai_Share_${payload.bvid.ifBlank { "video" }}_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, cardFile.mimeType)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BiliPai")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val targetUri = resolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: return@withContext false
        try {
            resolver.openInputStream(cardFile.uri)?.use { input ->
                resolver.openOutputStream(targetUri)?.use { output ->
                    input.copyTo(output)
                } ?: return@withContext false
            } ?: return@withContext false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { resolver.delete(targetUri, null, null) }
            false
        }
    }
}

/**
 * 按分享形态准备媒体：链接模式不附图；卡片模式合成卡片图，失败时回退空（走链接）。
 */
private suspend fun prepareVideoShareMedia(
    context: Context,
    payload: VideoSharePayload,
    style: VideoShareStyle,
    progressMessage: String,
): VideoShareCoverFile? {
    return when (style) {
        VideoShareStyle.LINK -> null
        VideoShareStyle.CARD -> {
            Toast.makeText(context, progressMessage, Toast.LENGTH_SHORT).show()
            val cardFile = prepareVideoShareCardFile(context, payload)
            if (cardFile == null) {
                Toast.makeText(context, "卡片生成失败，已改用链接分享", Toast.LENGTH_SHORT).show()
            }
            cardFile
        }
    }
}

private fun Context.startTargetedVideoShare(
    payload: VideoSharePayload,
    packageName: String,
    appName: String,
    shareMedia: VideoShareCoverFile?,
    preferredActivityClassName: String? = null,
) {
    try {
        val mimeType = shareMedia?.mimeType ?: "text/plain"
        val activityClassName = preferredActivityClassName ?: resolveShareActivityClassName(
            packageName = packageName,
            mimeType = mimeType,
        )
        val intent = if (shareMedia != null) {
            buildVideoCoverShareIntent(
                payload = payload,
                coverUri = shareMedia.uri,
                mimeType = shareMedia.mimeType,
                packageName = packageName,
                activityClassName = activityClassName,
                contentResolver = contentResolver
            )
        } else {
            buildTargetedShareIntent(
                payload = payload,
                packageName = packageName,
                activityClassName = activityClassName,
            )
        }
        startActivityWithTaskFlag(intent)
        VideoShareFeedbackEvents.prepared(payload.bvid)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "未安装$appName", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(this, "无法打开$appName", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.startMoreVideoShare(
    payload: VideoSharePayload,
    shareMedia: VideoShareCoverFile?,
) {
    try {
        val sendIntent = if (shareMedia != null) {
            buildVideoCoverShareIntent(
                payload = payload,
                coverUri = shareMedia.uri,
                mimeType = shareMedia.mimeType,
                contentResolver = contentResolver
            )
        } else {
            buildVideoShareIntent(payload)
        }
        val chooser = Intent.createChooser(sendIntent, resolveVideoShareChooserTitle(payload))
        if (shareMedia != null) {
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityWithTaskFlag(chooser)
        VideoShareFeedbackEvents.prepared(payload.bvid)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "无法打开分享面板", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.startActivityWithTaskFlag(intent: Intent) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

/**
 * 锁定「发给好友 / 选一个聊天」入口，避免同包多 Activity 触发系统 Resolver。
 */
private fun Context.resolveShareActivityClassName(
    packageName: String,
    mimeType: String,
): String? {
    val query = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        setPackage(packageName)
    }
    val resolveInfos = packageManager.queryIntentActivities(query, 0)
    if (resolveInfos.isEmpty()) return null
    val candidates = resolveInfos.map { info ->
        ShareActivityCandidate(
            packageName = info.activityInfo.packageName,
            className = info.activityInfo.name,
            label = info.loadLabel(packageManager).toString(),
        )
    }
    return resolvePreferredShareActivity(candidates)?.className
}
