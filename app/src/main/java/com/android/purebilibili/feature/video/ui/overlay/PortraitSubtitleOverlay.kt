package com.android.purebilibili.feature.video.ui.overlay


import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackOption
import com.android.purebilibili.feature.video.subtitle.buildSubtitleTrackOptions
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleVerticalOffsetFraction
import com.android.purebilibili.feature.video.subtitle.resolveStickySubtitleText
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleControlAvailability
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayModeByAutoPreference
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayOptions
import com.android.purebilibili.feature.video.subtitle.resolveSubtitlePositionPollingIdentity
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTextAt
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTextSizeSpec
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTrackDisplayLabel
import com.android.purebilibili.feature.video.subtitle.shouldKeepSubtitleOverlayMounted
import com.android.purebilibili.feature.video.subtitle.shouldRenderPrimarySubtitle
import com.android.purebilibili.feature.video.subtitle.shouldRenderSecondarySubtitle
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.ui.section.resolveSubtitleLanguageLabel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
                isMuted = isMuted
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
    val positionMs by produceState(
        initialValue = player.currentPosition.coerceAtLeast(0L),
        key1 = player,
        key2 = pollingIdentity
    ) {
        value = player.currentPosition.coerceAtLeast(0L)
        while (isActive) {
            value = player.currentPosition.coerceAtLeast(0L)
            delay(if (player.isPlaying) 120L else 260L)
        }
    }

    val primaryRaw = if (shouldRenderPrimarySubtitle(displayMode)) {
        resolveSubtitleTextAt(success.subtitlePrimaryCues, subtitlePositionForOffset(positionMs, subtitleSession.timeOffsetMs))
    } else {
        null
    }
    val secondaryRaw = if (shouldRenderSecondarySubtitle(displayMode)) {
        resolveSubtitleTextAt(success.subtitleSecondaryCues, subtitlePositionForOffset(positionMs, subtitleSession.timeOffsetMs))
    } else {
        null
    }
    var stickyPrimary by remember(pollingIdentity, subtitleSession.timeOffsetMs) { mutableStateOf<String?>(null) }
    var stickySecondary by remember(pollingIdentity, subtitleSession.timeOffsetMs) { mutableStateOf<String?>(null) }
    var primaryBlankSince by remember(pollingIdentity, subtitleSession.timeOffsetMs) { mutableLongStateOf(-1L) }
    var secondaryBlankSince by remember(pollingIdentity, subtitleSession.timeOffsetMs) { mutableLongStateOf(-1L) }
    val primaryText = resolveStickySubtitleText(
        currentText = primaryRaw,
        previousText = stickyPrimary,
        blankGapMs = if (primaryRaw.isNullOrBlank() && primaryBlankSince >= 0L) {
            (positionMs - primaryBlankSince).coerceAtLeast(0L)
        } else {
            0L
        }
    )
    val secondaryText = resolveStickySubtitleText(
        currentText = secondaryRaw,
        previousText = stickySecondary,
        blankGapMs = if (secondaryRaw.isNullOrBlank() && secondaryBlankSince >= 0L) {
            (positionMs - secondaryBlankSince).coerceAtLeast(0L)
        } else {
            0L
        }
    )
    SideEffect {
        if (!primaryRaw.isNullOrBlank()) {
            stickyPrimary = primaryRaw
            primaryBlankSince = -1L
        } else if (primaryBlankSince < 0L) {
            primaryBlankSince = positionMs
        }
        if (!secondaryRaw.isNullOrBlank()) {
            stickySecondary = secondaryRaw
            secondaryBlankSince = -1L
        } else if (secondaryBlankSince < 0L) {
            secondaryBlankSince = positionMs
        }
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
                    .fillMaxWidth()
                    .padding(horizontal = subtitleAppearance.horizontalPadding.dp)
                    .padding(bottom = (bottomPaddingDp + subtitleAppearance.bottomPadding).dp)
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
                val showPrimary = !primaryText.isNullOrBlank()
                val showSecondary = !secondaryText.isNullOrBlank()
                val secondaryAsPrimary = showSecondary && !showPrimary
                if (showSecondary) PiliStyledSubtitleText(
                    text = secondaryText.orEmpty(),
                    baseFontSizeSp = if (secondaryAsPrimary) textSizeSpec.primarySp else textSizeSpec.secondarySp,
                    appearance = subtitleAppearance,
                    isFullscreen = false,
                    color = Color.White.copy(alpha = 0.88f),
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

