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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.DynamicFeed
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.components.AppIcon
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.android.purebilibili.core.ui.components.AppGlassMenu
import com.android.purebilibili.core.ui.components.AppGlassMenuSource
import com.android.purebilibili.core.ui.components.LocalAppGlassPanelDismissAction
import com.android.purebilibili.core.ui.components.appGlassMenuAvailable

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

    // 锚点形变仅用于玻璃路径；原生底部面板与横屏侧栏保留原有尺寸和行为。
    val anchoredGlass = glassSource?.isAvailable == true && appGlassMenuAvailable()
    val panelScrollState = rememberScrollState()
    val shareContent: @Composable () -> Unit = {
        val finishPanel = LocalAppGlassPanelDismissAction.current.takeIf { anchoredGlass }
        val actionsEnabled = !switchingSheet && sharingTarget == null
        suspend fun hideSharePanel() {
            if (finishPanel == null) {
                hideVideoShareSheet(sheetState)
            } else {
                suspendCancellableCoroutine<Unit> { continuation ->
                    finishPanel { if (continuation.isActive) continuation.resume(Unit) }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLandscape && !anchoredGlass) Modifier.fillMaxHeight() else Modifier.heightIn(
                    max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp,
                ))
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        "分享",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    com.android.purebilibili.core.ui.components.AppTextButton(
                        onClick = {
                            if (anchoredGlass) shareScope.launch { hideSharePanel(); onDismiss() }
                            else onDismiss()
                        },
                        enabled = actionsEnabled,
                    ) { AppText("关闭") }
                }
            }
            if (!isLandscape) {
                AppText(
                    text = "分享",
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Column(
                Modifier
                    .weight(1f, fill = isLandscape && !anchoredGlass)
                    .verticalScroll(panelScrollState)
                    .then(if (isLandscape && !anchoredGlass) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 20.dp)
            ) {
                BoxWithConstraints(Modifier.widthIn(max = 216.dp).fillMaxWidth()) {
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
                AppText(
                    text = if (shareStyle == VideoShareStyle.CARD) {
                        "封面、标题与二维码"
                    } else {
                        "标题与可直接打开的视频链接"
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    color = AppContentColors.secondaryText(),
                    style = MaterialTheme.typography.bodySmall,
                )

                VideoShareTargets(sidePanel = isLandscape, modifier = Modifier.fillMaxWidth()) {
                    items.forEach { item ->
                        VideoShareSheetItemView(
                            item = item,
                            modifier = Modifier.weight(1f).fillMaxRowHeight(),
                            enabled = actionsEnabled,
                            onClick = {
                                if (sharingTarget != null || switchingSheet) return@VideoShareSheetItemView
                                when (item.target) {
                                    VideoShareTarget.BILIBILI_DYNAMIC -> {
                                        if (sharingTarget != null || switchingSheet) return@VideoShareSheetItemView
                                        if (TokenManager.csrfCache.isNullOrBlank()) {
                                            Toast.makeText(context, "请先登录后分享到动态", Toast.LENGTH_SHORT).show()
                                            return@VideoShareSheetItemView
                                        }
                                        switchingSheet = true
                                        shareScope.launch {
                                            hideSharePanel()
                                            showDynamicComposer = true
                                        }
                                    }
                                    VideoShareTarget.BILIBILI_FRIENDS -> {
                                        if (sharingTarget != null) return@VideoShareSheetItemView
                                        switchingSheet = true
                                        shareScope.launch {
                                            hideSharePanel()
                                            showFollowingPicker = true
                                        }
                                    }
                                    VideoShareTarget.WECHAT,
                                    VideoShareTarget.QQ -> {
                                        val packageName = item.target.packageName ?: return@VideoShareSheetItemView
                                        if (sharingTarget != null) return@VideoShareSheetItemView
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
                                        if (sharingTarget != null) return@VideoShareSheetItemView
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
                        )
                    }
                }

                AppHorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                if (sourcePayload.positionMs > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .toggleable(
                                value = preciseShare,
                                enabled = actionsEnabled,
                                role = Role.Checkbox,
                                onValueChange = { preciseShare = it },
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppCheckbox(
                            checked = preciseShare,
                            onCheckedChange = null,
                            enabled = actionsEnabled,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        AppText(
                            "从 ${sourcePayload.positionMs / 60000}:${(sourcePayload.positionMs / 1000 % 60).toString().padStart(2, '0')} 开始播放",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = actionsEnabled, role = Role.Button) {
                            shareScope.launch {
                                if (anchoredGlass) hideSharePanel()
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(effectivePayload.url)))
                                    onDismiss()
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
                                    if (anchoredGlass) onDismiss()
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    AppText("在其他应用中打开", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!isLandscape) {
                AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (anchoredGlass) Modifier else Modifier.navigationBarsPadding())
                        .heightIn(min = 48.dp)
                        .clickable(enabled = actionsEnabled, role = Role.Button) {
                            shareScope.launch {
                                hideSharePanel()
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = "取消",
                        color = AppContentColors.primaryText(),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
    if (anchoredGlass && AppGlassMenu(
            expanded = true,
            onDismissRequest = { if (!switchingSheet && sharingTarget == null) onDismiss() },
            modifier = modifier,
            source = glassSource,
            minWidth = 280.dp,
            maxWidth = 360.dp,
            maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp,
            scrollState = panelScrollState,
            interactiveContent = true,
            content = shareContent,
        )) return
    AppModalBottomSheet(
        onDismissRequest = { if (!switchingSheet && sharingTarget == null) onDismiss() },
        modifier = modifier,
        presentationOverride = videoSharePresentation(isLandscape),
        sheetState = sheetState,
        dragHandle = null,
    ) { shareContent() }
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
                .size(44.dp)
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
                        .size(44.dp)
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
