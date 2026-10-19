package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.layout.ContentScale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.repository.resolveCommentFraudLightMessage
import com.android.purebilibili.data.repository.shouldShowCommentFraudResultDialog
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.appContentDialogWidth
import com.android.purebilibili.core.store.PortraitPlayerCollapseMode
//  已改用 AppContentColors.accent()

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
// Refactored UI components
import com.android.purebilibili.feature.video.ui.section.VideoTitleSection
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.DescriptionSection
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.ActionButton
import com.android.purebilibili.feature.video.ui.components.resolveDanmakuTimestampJumpMs
import com.android.purebilibili.feature.video.ui.components.RelatedVideosHeader
import com.android.purebilibili.feature.video.ui.components.RelatedVideoItem
import com.android.purebilibili.feature.video.ui.components.CoinDialog
import com.android.purebilibili.feature.video.ui.components.CollectionRow
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PagesSelector
// Imports for moved classes
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoComposerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementEvent
import com.android.purebilibili.feature.video.viewmodel.VideoSupplementViewModel
import com.android.purebilibili.feature.video.viewmodel.toEngagementSeed
import com.android.purebilibili.feature.video.viewmodel.toSupplementSeed
import com.android.purebilibili.feature.video.viewmodel.QualitySwitchFailureDialogState
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.state.rememberVideoPlayerState
import com.android.purebilibili.feature.video.state.shouldReuseMiniPlayerAtEntry
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.shouldKeepVideoPlaybackAwake
import com.android.purebilibili.feature.video.ui.components.ReplyHeader
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.CommentFraudResultDialog
import com.android.purebilibili.feature.video.ui.components.VideoCommentSheetHost

import com.android.purebilibili.feature.video.viewmodel.CommentSortMode  //  新增
import com.android.purebilibili.feature.video.ui.components.LikeBurstAnimation
import com.android.purebilibili.feature.video.ui.components.TripleSuccessAnimation
import com.android.purebilibili.feature.video.ui.components.VideoDetailSkeleton
import com.android.purebilibili.feature.video.ui.components.VideoActionFeedbackHost
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayModePreference
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.usecase.playPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.policy.reduceVideoDetailPostScroll
import com.android.purebilibili.feature.video.policy.reduceVideoDetailPreScroll
import com.android.purebilibili.feature.video.policy.resolveVideoDetailCollapseProgress
import com.android.purebilibili.feature.video.subtitle.resolveSubtitlePreferenceSession
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionEnterEasing
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionReturnEasing
import com.android.purebilibili.core.ui.transition.resolveVideoSharedCoverCacheKey
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionSourceCornerDp
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionVisualSpec
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellContainerTransform
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.core.ui.transition.videoSharedElementBoundsTransformSpec
import com.android.purebilibili.core.ui.rememberAppCollectionIcon
import com.android.purebilibili.core.ui.rememberAppDownloadIcon
import com.android.purebilibili.core.ui.rememberAppMusicIcon
import com.android.purebilibili.core.ui.rememberAppPhotoIcon
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.PlaybackService
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.player.PlaylistUiState
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.feature.video.player.buildPipPlaybackRemoteActions
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
// 📱 [新增] 竖屏全屏
import com.android.purebilibili.feature.video.ui.overlay.PortraitFullscreenOverlay
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.FormatUtils
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeEffectCompat
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import com.android.purebilibili.feature.video.ui.components.DanmakuContextMenu
import com.android.purebilibili.feature.video.ui.components.DanmakuBlockActionTarget
import com.android.purebilibili.feature.video.ui.components.resolveDanmakuBlockActionFeedbackMessage
import com.android.purebilibili.feature.video.danmaku.appendDanmakuKeywordBlockRule
import com.android.purebilibili.feature.video.danmaku.appendDanmakuUserHashBlockRule
import com.android.purebilibili.feature.video.ui.components.InteractiveChoiceOverlay
import com.android.purebilibili.feature.video.ui.feedback.VideoFeedbackAnchor
import com.android.purebilibili.feature.video.ui.feedback.TripleCelebrationPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveQualityReminderPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveTripleCelebrationPlacement
import com.android.purebilibili.feature.video.ui.feedback.resolveVideoFeedbackPlacement
import com.android.purebilibili.feature.video.ui.section.resolveForcedReturnCoverSharedElementSourceRoute
import com.android.purebilibili.feature.video.share.VideoSharePayload
import com.android.purebilibili.feature.video.share.VideoShareSheet
import com.android.purebilibili.feature.video.viewmodel.PlayerToastMessage
import com.android.purebilibili.feature.video.viewmodel.PlayerToastPresentation
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun VideoDetailFollowGroupDialog(
    viewModel: VideoPlaybackViewModel
) {
    val followGroupDialogVisible by viewModel.followGroupDialogVisible.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val followGroupTags by viewModel.followGroupTags.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val followGroupSelectedTagIds by viewModel.followGroupSelectedTagIds.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val isFollowGroupsLoading by viewModel.isFollowGroupsLoading.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val isSavingFollowGroups by viewModel.isSavingFollowGroups.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    if (!followGroupDialogVisible) return

    AppAlertDialog(
        onDismissRequest = {
            if (!isSavingFollowGroups) viewModel.dismissFollowGroupDialog()
        },
        title = { AppText("设置关注分组") },
        text = {
            if (isFollowGroupsLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveLoadingIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (followGroupTags.isEmpty()) {
                        AppText(
                            text = "暂无可用分组（不勾选即为默认分组）",
                            color = AppContentColors.secondaryText(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        followGroupTags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleFollowGroupSelection(tag.tagid) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppCheckbox(
                                    checked = followGroupSelectedTagIds.contains(tag.tagid),
                                    onCheckedChange = { viewModel.toggleFollowGroupSelection(tag.tagid) }
                                )
                                AppText(
                                    text = "${tag.name} (${tag.count})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppContentColors.primaryText()
                                )
                            }
                        }
                    }
                    AppText(
                        text = "可多选，确定后覆盖原分组设置。",
                        color = AppContentColors.secondaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = { viewModel.saveFollowGroupSelection() },
                enabled = !isFollowGroupsLoading && !isSavingFollowGroups
            ) {
                if (isSavingFollowGroups) {
                    AppCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AppContentColors.onAccent()
                    )
                } else {
                    AppText("确定")
                }
            }
        },
        dismissButton = {
            AppTextButton(
                onClick = { viewModel.dismissFollowGroupDialog() },
                enabled = !isSavingFollowGroups
            ) {
                AppText("取消")
            }
        }
    )
}

@Composable
internal fun VideoDetailPlaybackEndedDialog(
    viewModel: VideoPlaybackViewModel,
    player: Player
) {
    val showPlaybackEndedDialog by viewModel.showPlaybackEndedDialog.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    if (!showPlaybackEndedDialog) return

    val dialogLayout = remember {
        com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy(maxWidthDp = 420)
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { viewModel.dismissPlaybackEndedDialog() },
        properties = com.android.purebilibili.core.ui.resolveAppContentDialogProperties(
            usePlatformDefaultWidth = dialogLayout.usePlatformDefaultWidth,
        ),
    ) {
        com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
        AppSurface(
            modifier = Modifier.appContentDialogWidth(policy = dialogLayout),
            shape = AppShapes.container(ContainerLevel.Dialog),
            color = AppSurfaceTokens.surface(),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppText(
                    text = "播放完成",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                AppText(
                    text = "选择接下来的操作",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText()
                )
                AppButton(
                    onClick = {
                        viewModel.dismissPlaybackEndedDialog()
                        player.seekTo(0)
                        playPlayerFromUserAction(player)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = AppContentColors.primaryText()
                    )
                ) {
                    AppText("🔄 重播当前视频")
                }
                AppButton(
                    onClick = {
                        viewModel.dismissPlaybackEndedDialog()
                        viewModel.playNextRecommended()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                        contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                    )
                ) {
                    AppText("▶️ 播放下一个视频")
                }
                AppTextButton(
                    onClick = { viewModel.dismissPlaybackEndedDialog() }
                ) {
                    AppText("暂不操作")
                }
            }
        }
    }
}

@Composable
internal fun VideoDetailQualitySwitchFailureDialog(
    context: Context,
    viewModel: VideoPlaybackViewModel,
    qualitySwitchFailureDialog: QualitySwitchFailureDialogState?,
    qualitySwitchFailureDialogEnabled: Boolean,
    qualitySwitchFailureDialogOnceEnabled: Boolean,
    qualitySwitchFailureDialogShown: Boolean,
    playerDiagnosticLoggingEnabled: Boolean,
    qualitySwitchDialogScope: CoroutineScope
) {
    LaunchedEffect(
        qualitySwitchFailureDialog?.requestedQualityId,
        qualitySwitchFailureDialogEnabled,
        qualitySwitchFailureDialogOnceEnabled,
        qualitySwitchFailureDialogShown
    ) {
        val dialog = qualitySwitchFailureDialog ?: return@LaunchedEffect
        val shouldSuppressDialog = !qualitySwitchFailureDialogEnabled ||
            (qualitySwitchFailureDialogOnceEnabled && qualitySwitchFailureDialogShown)
        if (shouldSuppressDialog) {
            viewModel.dismissQualitySwitchFailureDialog()
        }
    }

    qualitySwitchFailureDialog
        ?.takeIf {
            qualitySwitchFailureDialogEnabled &&
                !(qualitySwitchFailureDialogOnceEnabled && qualitySwitchFailureDialogShown)
        }
        ?.let { dialog ->
            var onceForCurrentDialog by remember(dialog) {
                mutableStateOf(qualitySwitchFailureDialogOnceEnabled)
            }
            LaunchedEffect(qualitySwitchFailureDialogOnceEnabled) {
                onceForCurrentDialog = qualitySwitchFailureDialogOnceEnabled
            }

            fun dismissQualitySwitchFailureDialogAfterUserChoice() {
                qualitySwitchDialogScope.launch {
                    if (onceForCurrentDialog) {
                        com.android.purebilibili.core.store.SettingsManager
                            .markQualitySwitchFailureDialogShown(context)
                    }
                    viewModel.dismissQualitySwitchFailureDialog()
                }
            }

            AppAlertDialog(
                onDismissRequest = { dismissQualitySwitchFailureDialogAfterUserChoice() },
                title = { AppText(dialog.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppText(dialog.message)
                        AppTextButton(
                            onClick = {
                                qualitySwitchDialogScope.launch {
                                    com.android.purebilibili.core.store.SettingsManager
                                        .setPlayerDiagnosticLoggingEnabled(
                                            context,
                                            !playerDiagnosticLoggingEnabled
                                        )
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            AppText(
                                if (playerDiagnosticLoggingEnabled) {
                                    "关闭诊断日志"
                                } else {
                                    "开启诊断日志"
                                }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    val nextValue = !onceForCurrentDialog
                                    onceForCurrentDialog = nextValue
                                    qualitySwitchDialogScope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setQualitySwitchFailureDialogOnceEnabled(context, nextValue)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText(
                                text = "仅提示一次",
                                modifier = Modifier.weight(1f),
                                tapToCopyEnabled = false
                            )
                            AppCheckbox(
                                checked = onceForCurrentDialog,
                                onCheckedChange = { checked ->
                                    onceForCurrentDialog = checked
                                    qualitySwitchDialogScope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setQualitySwitchFailureDialogOnceEnabled(context, checked)
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    AppTextButton(
                        onClick = {
                            com.android.purebilibili.core.util.LogCollector.exportAndShare(context)
                            dismissQualitySwitchFailureDialogAfterUserChoice()
                        }
                    ) {
                        AppText("导出日志")
                    }
                },
                dismissButton = {
                    AppTextButton(onClick = { dismissQualitySwitchFailureDialogAfterUserChoice() }) {
                        AppText("关闭")
                    }
                }
            )
        }
}

@Composable
internal fun VideoDetailDanmakuContextMenu(
    context: Context,
    viewModel: VideoPlaybackViewModel,
    activeDanmakuBlockRulesRaw: String,
    activeDanmakuScope: com.android.purebilibili.core.store.DanmakuSettingsScope,
    sortPreferenceScope: CoroutineScope
) {
    val danmakuMenuState by viewModel.danmakuMenuState.collectAsStateWithLifecycle(
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    if (!danmakuMenuState.visible) return

    DanmakuContextMenu(
        text = danmakuMenuState.text,
        onDismiss = { viewModel.hideDanmakuMenu() },
        onLike = { viewModel.likeDanmaku(danmakuMenuState.dmid) },
        onRecall = { viewModel.recallDanmaku(danmakuMenuState.dmid) },
        onReport = { reason ->
            viewModel.reportDanmaku(danmakuMenuState.dmid, reason)
        },
        voteCount = danmakuMenuState.voteCount,
        hasLiked = danmakuMenuState.hasLiked,
        voteLoading = danmakuMenuState.voteLoading,
        canVote = danmakuMenuState.canVote,
        timestampJumpMs = resolveDanmakuTimestampJumpMs(danmakuMenuState.text),
        onSeekToTimestamp = { viewModel.seekTo(it) },
        canRecall = danmakuMenuState.isSelf,
        canBlockKeyword = danmakuMenuState.text.isNotBlank(),
        onBlockKeyword = {
            val updatedRules = appendDanmakuKeywordBlockRule(
                rawRules = activeDanmakuBlockRulesRaw,
                keyword = danmakuMenuState.text
            )
            val changed = updatedRules != activeDanmakuBlockRulesRaw
            sortPreferenceScope.launch {
                com.android.purebilibili.core.store.SettingsManager.setDanmakuBlockRulesRaw(
                    context,
                    updatedRules,
                    activeDanmakuScope
                )
            }
            viewModel.toast(
                resolveDanmakuBlockActionFeedbackMessage(
                    target = DanmakuBlockActionTarget.KEYWORD,
                    changed = changed
                )
            )
        },
        canBlockUser = danmakuMenuState.userHash.isNotBlank(),
        onBlockUser = {
            val userHash = danmakuMenuState.userHash
            if (userHash.isBlank()) {
                viewModel.toast("该弹幕缺少发送者标识")
            } else {
                val updatedRules = appendDanmakuUserHashBlockRule(
                    rawRules = activeDanmakuBlockRulesRaw,
                    userHash = userHash
                )
                val changed = updatedRules != activeDanmakuBlockRulesRaw
                sortPreferenceScope.launch {
                    com.android.purebilibili.core.store.SettingsManager.setDanmakuBlockRulesRaw(
                        context,
                        updatedRules,
                        activeDanmakuScope
                    )
                }
                viewModel.toast(
                    resolveDanmakuBlockActionFeedbackMessage(
                        target = DanmakuBlockActionTarget.USER,
                        changed = changed
                    )
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExternalPlaylistQueueSheet(
    visible: Boolean,
    title: String,
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    hazeState: HazeState,
    presentation: ExternalPlaylistQueueSheetPresentation,
    onDismiss: () -> Unit,
    onVideoSelected: (Int, PlaylistItem) -> Unit
) {
    // Keep the inline host composed while hidden so its exit can finish and a
    // quick reopen reverses the current transition instead of remounting it.
    if (!visible && presentation == ExternalPlaylistQueueSheetPresentation.MODAL) return

    com.android.purebilibili.core.ui.LocalNavigationBackHandler(enabled = visible) {
        onDismiss()
    }

    val configuration = LocalConfiguration.current
    val listMaxHeight = resolveExternalPlaylistQueueListMaxHeightDp(configuration.screenHeightDp).dp
    val navigationBarBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomSpacerHeight = resolveExternalPlaylistQueueBottomSpacerDp(
        navigationBarBottomPadding.value.roundToInt()
    ).dp
    val sheetShape = AppShapes.container(ContainerLevel.Sheet)
    val motionEnabled = LocalComponentMotionEnabled.current

    when (presentation) {
        ExternalPlaylistQueueSheetPresentation.INLINE_HAZE -> {
            val interactionSource = remember { MutableInteractionSource() }
            val useHazeEffect = shouldAllowRuntimeShaderBackedHazeEffect(Build.VERSION.SDK_INT)
            AnimatedVisibility(
                visible = visible,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .animateEnterExit(
                                enter = if (motionEnabled) fadeIn(AppMotionTokens.standardSpec()) else EnterTransition.None,
                                exit = if (motionEnabled) fadeOut(AppMotionTokens.standardSpec()) else ExitTransition.None,
                            )
                            .background(Color.Black.copy(alpha = 0.18f))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onDismiss() }
                    )

                    AppSurface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .animateEnterExit(
                                enter = if (motionEnabled) {
                                    slideInVertically(AppMotionTokens.emphasizedSpec()) { it }
                                } else EnterTransition.None,
                                exit = if (motionEnabled) {
                                    slideOutVertically(AppMotionTokens.standardSpec()) { it }
                                } else ExitTransition.None,
                            )
                            .fillMaxWidth()
                            .clip(sheetShape)
                            .then(
                                if (useHazeEffect) {
                                    Modifier.hazeEffectCompat(
                                        state = hazeState,
                                        style = HazeMaterials.ultraThin()
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        shape = sheetShape,
                        color = AppSurfaceTokens.surface().copy(alpha = 0.74f),
                        tonalElevation = 0.dp,
                        border = BorderStroke(
                            width = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        ExternalPlaylistQueueSheetContent(
                            title = title,
                            playlist = playlist,
                            currentIndex = currentIndex,
                            listMaxHeight = listMaxHeight,
                            bottomSpacerHeight = bottomSpacerHeight,
                            onVideoSelected = onVideoSelected,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }
        ExternalPlaylistQueueSheetPresentation.MODAL -> {
            AppModalBottomSheet(
                onDismissRequest = onDismiss,
                containerColor = Color.Transparent,
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            ) {
                AppSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .unifiedBlur(hazeState = hazeState, shape = sheetShape),
                    shape = sheetShape,
                    color = AppSurfaceTokens.surface().copy(alpha = 0.80f),
                    tonalElevation = 0.dp,
                    border = BorderStroke(
                        width = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    ExternalPlaylistQueueSheetContent(
                        title = title,
                        playlist = playlist,
                        currentIndex = currentIndex,
                        listMaxHeight = listMaxHeight,
                        bottomSpacerHeight = bottomSpacerHeight,
                        onVideoSelected = onVideoSelected,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExternalPlaylistQueueSheetContent(
    title: String,
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    listMaxHeight: androidx.compose.ui.unit.Dp,
    bottomSpacerHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onVideoSelected: (Int, PlaylistItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceIn(0, playlist.lastIndex.coerceAtLeast(0)),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = "${playlist.size}个视频",
                color = AppContentColors.accent(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            AppIconButton(onClick = onDismiss) {
                AppIcon(
                    imageVector = rememberAppChevronDownIcon(),
                    contentDescription = "收起${title}队列",
                )
            }
        }

        AppHorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = listMaxHeight),
            contentPadding = PaddingValues(bottom = bottomSpacerHeight)
        ) {
            items(
                playlist.size,
                key = { index ->
                    val item = playlist[index]
                    resolveIndexedVideoLazyKey(
                        namespace = "video_playlist",
                        index = index,
                        bvid = item.bvid
                    )
                }
            ) { index ->
                val item = playlist[index]
                val selected = index == currentIndex
                val normalizedCoverUrl = normalizePlaylistCoverUrlForUi(item.cover)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onVideoSelected(index, item) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "${index + 1}",
                        color = if (selected) {
                            AppContentColors.accent()
                        } else {
                            AppContentColors.secondaryText()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(54.dp)
                            .clip(AppShapes.container(ContainerLevel.Field))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        if (normalizedCoverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = normalizedCoverUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AppText(
                                    text = "无封面",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppContentColors.secondaryText()
                                )
                            }
                        }
                        if (item.duration > 0L) {
                            AppText(
                                text = FormatUtils.formatDuration(item.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .background(Color.Black.copy(alpha = 0.68f), AppShapes.container(ContainerLevel.Chip))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        AppText(
                            text = item.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = AppContentColors.primaryText()
                        )
                        Spacer(Modifier.height(4.dp))
                        AppText(
                            text = listOfNotNull(
                                item.owner.takeIf { it.isNotBlank() },
                                item.publishedAt?.takeIf { it > 0L }?.let { FormatUtils.formatPublishTime(it) },
                            ).joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = AppContentColors.secondaryText(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        val stats = listOfNotNull(
                            item.playCount?.let { "${FormatUtils.formatStat(it)}播放" },
                            item.commentCount?.let { "${FormatUtils.formatStat(it)}评论" },
                            "正在播放".takeIf { selected },
                        ).joinToString(" · ")
                        if (stats.isNotBlank()) {
                            AppText(
                                text = stats,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) AppContentColors.accent() else AppContentColors.secondaryText(),
                            )
                        }
                    }
                    if (selected) {
                        AppIcon(
                            imageVector = rememberAppPlayIcon(),
                            contentDescription = "当前播放",
                            tint = AppContentColors.accent()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetachedVideoCommentThreadHost(
    visible: Boolean,
    successState: VideoPlaybackUiState.Success?,
    commentState: CommentUiState,
    commentViewModel: VideoCommentViewModel,
    forceInitialize: Boolean,
    viewModel: VideoPlaybackViewModel,
    onUpClick: (Long) -> Unit,
    onNavigateToRelatedVideo: (String) -> Unit,
    onSearchKeywordClick: (String) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    screenHeightPx: Int,
    topReservedPx: Int,
    onTimestampClick: (Long) -> Unit,
    onBackToTop: () -> Unit = {},
    onCoveredBlurProgressChange: ((Float) -> Unit)? = null,
    threadListModifier: Modifier = Modifier,
) {
    if (!visible) return

    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()

    VideoCommentSheetHost(
        threadListModifier = threadListModifier,
        mainSheetVisible = resolveVideoDetailCommentThreadHostMainSheetVisible(
            useEmbeddedPresentation = com.android.purebilibili.feature.video.ui.pager
                .shouldUseEmbeddedVideoSubReplyPresentation(),
            subReplyVisible = subReplyState.visible
        ),
        onDismiss = { commentViewModel.closeSubReply() },
        commentViewModel = commentViewModel,
        aid = successState?.info?.aid ?: 0L,
        upMid = commentState.upMid,
        expectedReplyCount = commentState.replyCount,
        emoteMap = successState?.emoteMap ?: emptyMap(),
        onRootCommentClick = { viewModel.openRootCommentComposer() },
        onReplyClick = { replyItem ->
            android.util.Log.d("VideoDetailScreen", "📝 Reply to: ${replyItem.member.uname}")
            viewModel.setReplyingTo(replyItem)
            viewModel.showCommentInputDialog()
        },
        onUserClick = onUpClick,
        onVideoClick = onNavigateToRelatedVideo,
        onSearchKeywordClick = onSearchKeywordClick,
        onOpenBilibiliLink = onOpenBilibiliLink,
        screenHeightPx = screenHeightPx,
        topReservedPx = topReservedPx,
        onTimestampClick = onTimestampClick,
        maxTimestampMs = successState?.videoDurationMs?.takeIf { it > 0L },
        onBackToTop = onBackToTop,
        forceInitialize = forceInitialize,
        handleFraudEvents = false,
        // 楼中楼（嵌入呈现）不盖全屏阴影：播放器上方保持可见，点背景关闭仍有效。
        maxScrimAlphaOverride = 0f,
        onCoveredBlurProgressChange = onCoveredBlurProgressChange
    )
}
