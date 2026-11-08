package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.android.purebilibili.feature.video.subtitle.PiliSubtitleSessions
import com.android.purebilibili.feature.video.subtitle.PiliStyledSubtitleText
import com.android.purebilibili.feature.video.subtitle.SubtitleToolsStore
import com.android.purebilibili.feature.video.subtitle.subtitlePositionForOffset
import com.android.purebilibili.feature.video.ui.components.PlayerSubtitleAppearance
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.subtitle.SUBTITLE_GAP_HOLD_MS
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackOption
import com.android.purebilibili.feature.video.subtitle.buildSubtitleTrackOptions
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleVerticalOffsetFraction
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleControlAvailability
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayModeByAutoPreference
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayOptions
import com.android.purebilibili.feature.video.subtitle.resolveSubtitlePositionPollingIdentity
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayTextAt
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTextSizeSpec
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTrackDisplayLabel
import com.android.purebilibili.feature.video.subtitle.shouldKeepSubtitleOverlayMounted
import com.android.purebilibili.feature.video.subtitle.shouldRenderPrimarySubtitle
import com.android.purebilibili.feature.video.subtitle.shouldRenderSecondarySubtitle
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.ui.section.resolveSubtitleLanguageLabel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import kotlinx.coroutines.launch


/**
 * Bottom padding for portrait subtitle text relative to chrome visibility.
 * Controls strip + progress + home indicator need more clearance when chrome is up.
 */
internal fun resolvePortraitSubtitleBottomPaddingDp(
    controlsVisible: Boolean,
    commentExpansionProgress: Float = 0f
): Int {
    if (commentExpansionProgress > 0.35f) return 36
    return if (controlsVisible) 88 else 40
}

internal fun shouldShowPortraitSubtitleChip(
    featureEnabled: Boolean,
    trackAvailable: Boolean
): Boolean = featureEnabled && trackAvailable

internal fun resolvePortraitSubtitleBelongsToPage(
    success: VideoPlaybackUiState.Success?,
    pageBvid: String,
    pageCid: Long
): Boolean {
    if (success == null || pageCid <= 0L || pageBvid.isBlank()) return false
    return success.subtitleOwnerBvid == pageBvid &&
        success.subtitleOwnerCid == pageCid &&
        success.info.bvid == pageBvid &&
        success.info.cid == pageCid
}

@Composable
fun PortraitSubtitleHost(
    success: VideoPlaybackUiState.Success?,
    player: ExoPlayer,
    pageBvid: String,
    pageCid: Long,
    isCurrentPage: Boolean,
    controlsVisible: Boolean,
    commentExpansionProgress: Float,
    subtitleAutoPreference: SubtitleAutoPreference,
    isMuted: Boolean,
    onSubtitleTrackSelected: (String) -> Unit,
    onControlsChange: (SubtitleControlUiState, SubtitleControlCallbacks) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val featureEnabled = isSubtitleFeatureEnabledForUser()
    val belongsToPage = resolvePortraitSubtitleBelongsToPage(
        success = success,
        pageBvid = pageBvid,
        pageCid = pageCid
    )
    if (!featureEnabled || !isCurrentPage || success == null || !belongsToPage) {
        LaunchedEffect(featureEnabled, isCurrentPage, belongsToPage) {
            onControlsChange(SubtitleControlUiState(), SubtitleControlCallbacks())
        }
        return
    }

    val subtitleSession = remember(pageBvid, pageCid) { PiliSubtitleSessions.get(pageBvid, pageCid) }
    val subtitleContext = LocalContext.current
    val subtitleAppearance by remember(subtitleContext) {
        SubtitleToolsStore.appearance(subtitleContext)
    }.collectAsStateWithLifecycle(initialValue = PlayerSubtitleAppearance())
    val primaryTrackBound = !success.subtitlePrimaryTrackKey.isNullOrBlank() ||
        !success.subtitlePrimaryLanguage.isNullOrBlank()
    val secondaryTrackBound = !success.subtitleSecondaryTrackKey.isNullOrBlank() ||
        !success.subtitleSecondaryLanguage.isNullOrBlank()
    val primaryCueAvailable = success.subtitlePrimaryCues.isNotEmpty()
    val secondaryCueAvailable = success.subtitleSecondaryCues.isNotEmpty()
    val availability = resolveSubtitleControlAvailability(
        primaryTrackBound = primaryTrackBound,
        secondaryTrackBound = secondaryTrackBound,
        primaryCueAvailable = primaryCueAvailable,
        secondaryCueAvailable = secondaryCueAvailable
    )
    if (!availability.trackAvailable) {
        LaunchedEffect(availability.trackAvailable) {
            onControlsChange(SubtitleControlUiState(), SubtitleControlCallbacks())
        }
        return
    }

    val primaryLabel = success.subtitleTracks.firstOrNull {
        it.trackKey == success.subtitlePrimaryTrackKey
    }?.let(::resolveSubtitleTrackDisplayLabel)
        ?: resolveSubtitleLanguageLabel(
            languageCode = success.subtitlePrimaryLanguage,
            fallbackLabel = "中文"
        )
    val secondaryLabel = success.subtitleTracks.firstOrNull {
        it.trackKey == success.subtitleSecondaryTrackKey
    }?.let(::resolveSubtitleTrackDisplayLabel)
        ?: resolveSubtitleLanguageLabel(
            languageCode = success.subtitleSecondaryLanguage,
            fallbackLabel = "英文"
        )
    val trackOptions = buildSubtitleTrackOptions(
        tracks = success.subtitleTracks,
        selectedTrackKey = success.subtitlePrimaryTrackKey
    )

    val sessionKey = remember(
        pageBvid,
        pageCid,
        success.subtitlePrimaryLanguage,
        success.subtitleSecondaryLanguage,
        success.subtitlePrimaryLikelyAi,
        success.subtitleSecondaryLikelyAi,
        subtitleAutoPreference
    ) {
        "${pageBvid}_${pageCid}_${success.subtitlePrimaryLanguage}_${success.subtitleSecondaryLanguage}_" +
            "${success.subtitlePrimaryLikelyAi}_${success.subtitleSecondaryLikelyAi}_${subtitleAutoPreference.name}"
    }
    var displayModePreference by rememberSaveable(sessionKey) {
        mutableStateOf(
            resolveSubtitleDisplayModeByAutoPreference(
                preference = subtitleAutoPreference,
                hasPrimaryTrack = availability.primarySelectable,
                hasSecondaryTrack = availability.secondarySelectable,
                primaryTrackLikelyAi = success.subtitlePrimaryLikelyAi,
                secondaryTrackLikelyAi = success.subtitleSecondaryLikelyAi,
                isMuted = isMuted,
                primaryTrackLanguage = success.subtitlePrimaryLanguage,
                secondaryTrackLanguage = success.subtitleSecondaryLanguage
            )
        )
    }
    var largeTextEnabled by rememberSaveable("${sessionKey}_large") {
        mutableStateOf(false)
    }

    val displayMode = normalizeSubtitleDisplayMode(
        preferredMode = displayModePreference,
        hasPrimaryTrack = primaryCueAvailable || primaryTrackBound,
        hasSecondaryTrack = secondaryCueAvailable || secondaryTrackBound
    )
    val overlayEnabled = displayMode != SubtitleDisplayMode.OFF
    val configuration = LocalConfiguration.current
    val textSizeSpec = remember(configuration.screenWidthDp, largeTextEnabled) {
        resolveSubtitleTextSizeSpec(
            playerWidthDp = configuration.screenWidthDp,
            largeTextEnabled = largeTextEnabled
        )
    }

    val pollingIdentity = resolveSubtitlePositionPollingIdentity(
        bvid = pageBvid,
        cid = pageCid
    )
    val subtitleTimeline = remember(success.subtitlePrimaryCues, success.subtitleSecondaryCues, subtitleSession.timeOffsetMs) {
        com.android.purebilibili.feature.video.subtitle.subtitlePositionTimeline(
            success.subtitlePrimaryCues, success.subtitleSecondaryCues, subtitleSession.timeOffsetMs,
            endHoldMs = SUBTITLE_GAP_HOLD_MS,
        )
    }
    val positionMs by com.android.purebilibili.core.player.rememberPlaybackPosition(
        player = player,
        identity = pollingIdentity,
        enabled = overlayEnabled && subtitleSession.selectedTrackKey == null &&
            (primaryCueAvailable || secondaryCueAvailable),
        timeline = subtitleTimeline,
    )

    val primaryText = if (shouldRenderPrimarySubtitle(displayMode)) {
        resolveSubtitleDisplayTextAt(success.subtitlePrimaryCues, subtitlePositionForOffset(positionMs, subtitleSession.timeOffsetMs))
    } else {
        null
    }
    val secondaryText = if (shouldRenderSecondarySubtitle(displayMode)) {
        resolveSubtitleDisplayTextAt(success.subtitleSecondaryCues, subtitlePositionForOffset(positionMs, subtitleSession.timeOffsetMs))
    } else {
        null
    }

    val keepMounted = shouldKeepSubtitleOverlayMounted(
        overlayEnabled = overlayEnabled,
        isInPipMode = false,
        isAudioOnly = false,
        suppressOverlay = false
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val storedPortraitOffset by SettingsManager
        .getSubtitlePortraitVerticalOffsetFraction(context)
        .collectAsStateWithLifecycle(initialValue = 0f)
    val subtitlePositionLocked by SettingsManager
        .getSubtitlePositionLocked(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val bottomPaddingDp = resolvePortraitSubtitleBottomPaddingDp(
        controlsVisible = controlsVisible && !subtitlePositionLocked,
        commentExpansionProgress = commentExpansionProgress
    )
    var subtitleVerticalOffsetFraction by rememberSaveable(pageBvid) {
        mutableFloatStateOf(storedPortraitOffset)
    }
    var isDraggingSubtitleOffset by remember { mutableStateOf(false) }
    LaunchedEffect(storedPortraitOffset, pageBvid) {
        if (!isDraggingSubtitleOffset) {
            subtitleVerticalOffsetFraction = storedPortraitOffset
        }
    }
    val controlState = SubtitleControlUiState(
        trackAvailable = availability.trackAvailable,
        primaryAvailable = availability.primarySelectable,
        secondaryAvailable = availability.secondarySelectable,
        enabled = overlayEnabled,
        displayMode = displayMode,
        primaryLabel = primaryLabel,
        secondaryLabel = secondaryLabel,
        trackOptions = trackOptions,
        largeTextEnabled = largeTextEnabled,
        positionLocked = subtitlePositionLocked,
    )
    val currentTrackSelected = rememberUpdatedState(onSubtitleTrackSelected)
    val controlCallbacks = remember(sessionKey, context, scope) {
        SubtitleControlCallbacks(
            onDisplayModeChange = { displayModePreference = it },
            onEnabledChange = {
                displayModePreference = if (it) SubtitleDisplayMode.PRIMARY_ONLY else SubtitleDisplayMode.OFF
            },
            onTrackSelected = {
                currentTrackSelected.value(it)
                displayModePreference = SubtitleDisplayMode.PRIMARY_ONLY
            },
            onLargeTextChange = { largeTextEnabled = it },
            onPositionLockedChange = { locked ->
                scope.launch { SettingsManager.setSubtitlePositionLocked(context, locked) }
            },
        )
    }
    LaunchedEffect(controlState, controlCallbacks) {
        onControlsChange(controlState, controlCallbacks)
    }

    // 未自定义字幕时保留玻璃原有排版；自定义样式与原生路径共用渲染器。
    val glassLayout = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
        subtitleAppearance == PlayerSubtitleAppearance()
    Box(modifier = modifier.fillMaxSize()) {
        if (keepMounted && subtitleSession.selectedTrackKey == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = with(density) {
                                (configuration.screenHeightDp * subtitleVerticalOffsetFraction)
                                    .dp
                                    .roundToPx()
                            }
                        )
                    }
                    .then(
                        if (glassLayout) Modifier
                            .fillMaxWidth(0.92f)
                            .padding(horizontal = 12.dp)
                            .padding(bottom = bottomPaddingDp.dp)
                            .padding(vertical = 6.dp)
                        else Modifier
                            .fillMaxWidth()
                            .padding(horizontal = subtitleAppearance.horizontalPadding.dp)
                            .padding(bottom = (bottomPaddingDp + subtitleAppearance.bottomPadding).dp)
                    )
                    .then(
                        if (subtitlePositionLocked) {
                            Modifier
                        } else {
                            Modifier.pointerInput(configuration.screenHeightDp) {
                                detectDragGestures(
                                    onDragStart = { isDraggingSubtitleOffset = true },
                                    onDragEnd = {
                                        isDraggingSubtitleOffset = false
                                        scope.launch {
                                            SettingsManager.setSubtitlePortraitVerticalOffsetFraction(
                                                context,
                                                subtitleVerticalOffsetFraction
                                            )
                                        }
                                    },
                                    onDragCancel = { isDraggingSubtitleOffset = false },
                                    onDrag = { change, dragAmount ->
                                        val screenHeightPx = with(density) {
                                            configuration.screenHeightDp.dp.toPx()
                                        }.coerceAtLeast(1f)
                                        subtitleVerticalOffsetFraction =
                                            normalizeSubtitleVerticalOffsetFraction(
                                                subtitleVerticalOffsetFraction +
                                                    dragAmount.y / screenHeightPx
                                            )
                                        change.consume()
                                    }
                                )
                            }
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (glassLayout) {
                    val shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 6f
                    )
                    val showPrimary = !primaryText.isNullOrBlank()
                    val showSecondary = !secondaryText.isNullOrBlank()
                    val secondaryAsPrimary = showSecondary && !showPrimary
                    AppText(
                        text = secondaryText.orEmpty(),
                        color = Color.White.copy(alpha = if (showSecondary) 0.88f else 0f),
                        fontSize = if (secondaryAsPrimary) {
                            textSizeSpec.primarySp.sp
                        } else {
                            textSizeSpec.secondarySp.sp
                        },
                        fontWeight = if (secondaryAsPrimary) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                        modifier = Modifier.then(
                            if (showSecondary) Modifier else Modifier.height(0.dp)
                        )
                    )
                    AppText(
                        text = primaryText.orEmpty(),
                        color = Color.White.copy(alpha = if (showPrimary) 1f else 0f),
                        fontSize = textSizeSpec.primarySp.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                        modifier = Modifier.then(
                            if (showPrimary) Modifier else Modifier.height(0.dp)
                        )
                    )
                } else {
                    val showPrimary = !primaryText.isNullOrBlank()
                    val showSecondary = !secondaryText.isNullOrBlank()
                    val secondaryAsPrimary = showSecondary && !showPrimary
                    if (showSecondary) PiliStyledSubtitleText(
                        text = secondaryText.orEmpty(),
                        baseFontSizeSp = if (secondaryAsPrimary) textSizeSpec.primarySp else textSizeSpec.secondarySp,
                        appearance = subtitleAppearance,
                        isFullscreen = false,
                        color = Color(subtitleAppearance.textColor).copy(alpha = 0.88f),
                    )
                    if (showPrimary) PiliStyledSubtitleText(
                        text = primaryText.orEmpty(),
                        baseFontSizeSp = textSizeSpec.primarySp,
                        appearance = subtitleAppearance,
                        isFullscreen = false,
                    )
                }
            }
        }

    }
}

