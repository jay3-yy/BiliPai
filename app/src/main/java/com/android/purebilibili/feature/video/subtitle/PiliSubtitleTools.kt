package com.android.purebilibili.feature.video.subtitle

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import com.android.purebilibili.core.ui.components.AppSlider as Slider
import com.android.purebilibili.core.ui.AppAlertDialog as AlertDialog

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.android.purebilibili.core.store.SettingsManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.android.purebilibili.data.repository.SubtitleContentRepository
import com.android.purebilibili.feature.video.ui.components.PlayerSubtitleAppearance
import com.android.purebilibili.feature.video.ui.components.PlayerSubtitleSettingsPanel
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlCallbacks
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlUiState
import com.android.purebilibili.feature.video.ui.section.resolveSubtitleBottomOffsetPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class PiliSubtitleTools internal constructor(
    val controls: SubtitleControlUiState,
    val callbacks: SubtitleControlCallbacks,
    val onLoadSubtitle: () -> Unit,
    val onSaveSubtitle: () -> Unit,
    val onSubtitleSettings: () -> Unit,
    internal val session: PiliSubtitleSession,
    internal val player: Player?,
    internal val appearance: PlayerSubtitleAppearance,
) {
    val externalSelected: Boolean get() = session.selectedTrackKey != null
}

private data class SubtitleExportRequest(val identity: SubtitleDocumentIdentity, val text: String)

/** SAF owns permission/filename selection; cue data is read/written only after an actual result. */
@Composable
fun rememberPiliSubtitleTools(
    bvid: String,
    cid: Long,
    player: Player?,
    nativeControls: SubtitleControlUiState,
    nativeCallbacks: SubtitleControlCallbacks,
    nativeTracks: List<SubtitleTrackMeta> = emptyList(),
    isFullscreen: Boolean = false,
    acquireNativeSubtitles: Boolean = false,
    nativeTracksKnown: Boolean = false,
    acquisitionActive: Boolean = true,
): PiliSubtitleTools {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val identity = remember(bvid, cid) { SubtitleDocumentIdentity(bvid, cid) }
    val currentIdentity by rememberUpdatedState(identity)
    val currentNativeCallbacks by rememberUpdatedState(nativeCallbacks)
    val session = remember(identity) { PiliSubtitleSessions.get(bvid, cid) }
    val sessionInUse = acquisitionActive && com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current
    DisposableEffect(session, sessionInUse) {
        if (sessionInUse) session.retainDownloadedCues()
        onDispose {
            if (sessionInUse) PiliSubtitleSessions.releaseAfterUiHandoff(session)
        }
    }
    val storedAppearance by remember(context) { SubtitleToolsStore.appearance(context) }
        .collectAsStateWithLifecycle(initialValue = PlayerSubtitleAppearance())
    val positionLocked by remember(context) { SettingsManager.getSubtitlePositionLocked(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    var appearance by remember { mutableStateOf(storedAppearance) }
    LaunchedEffect(storedAppearance) { appearance = storedAppearance }
    var showStyle by remember(identity) { mutableStateOf(false) }
    var showTiming by remember(identity) { mutableStateOf(false) }
    var showExport by remember(identity) { mutableStateOf(false) }
    var busy by remember(identity) { mutableStateOf(false) }
    var importIdentity by remember { mutableStateOf<SubtitleDocumentIdentity?>(null) }
    var exportRequest by remember { mutableStateOf<SubtitleExportRequest?>(null) }
    fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    var nativeSelectionSerial by remember(identity) { mutableLongStateOf(0L) }
    suspend fun loadNativeCues(track: SubtitleTrackMeta): List<SubtitleCue> = withContext(Dispatchers.IO) {
        session.acquiredCues[track.trackKey] ?: SubtitleContentRepository.cues(
            track.subtitleUrl, bvid, cid, track.id, track.idStr, track.lan
        ).getOrThrow()
    }
    val visualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    LaunchedEffect(identity, acquireNativeSubtitles, nativeTracks, nativeTracksKnown, sessionInUse, visualWorkActive) {
        if (!acquireNativeSubtitles || !sessionInUse || !visualWorkActive ||
            bvid.isBlank() || cid <= 0L
        ) return@LaunchedEffect
        val cueGeneration = session.downloadedCueGeneration
        val initializationSelectionSerial = nativeSelectionSerial
        try {
            if (session.acquiredInitialized) {
                val loaded = session.missingSelectedNativeTracks().associate { it.trackKey to loadNativeCues(it) }
                if (subtitleDocumentResultBelongsTo(identity, currentIdentity)) {
                    session.mergeAcquiredCues(loaded, cueGeneration)
                }
                return@LaunchedEffect
            }
            val tracks = if (nativeTracksKnown || nativeTracks.isNotEmpty()) nativeTracks else withContext(Dispatchers.IO) {
                mapPlayerInfoSubtitleTracks(SubtitleContentRepository.playerInfo(bvid, cid).getOrThrow().subtitle?.subtitles.orEmpty())
            }
            if (!subtitleDocumentResultBelongsTo(identity, currentIdentity)) return@LaunchedEffect
            session.acquiredTracks = tracks
            val languages = resolveDefaultSubtitleLanguages(tracks)
            val primary = tracks.firstOrNull { it.lan == languages.primaryLanguage } ?: tracks.firstOrNull()
            val secondary = tracks.firstOrNull { it.lan == languages.secondaryLanguage && it.trackKey != primary?.trackKey }
            val loaded = listOfNotNull(primary, secondary).associate { it.trackKey to loadNativeCues(it) }
            val preference = SettingsManager.getSubtitleAutoPreference(context).first()
            if (!subtitleDocumentResultBelongsTo(identity, currentIdentity)) return@LaunchedEffect
            if (!session.mergeAcquiredCues(loaded, cueGeneration)) return@LaunchedEffect
            if (initializationSelectionSerial == nativeSelectionSerial) {
                session.acquiredSecondaryTrackKey = secondary?.trackKey
                session.acquiredDisplayMode = resolveSubtitleDisplayModeByAutoPreference(
                    preference = preference,
                    hasPrimaryTrack = primary != null,
                    hasSecondaryTrack = secondary != null,
                    primaryTrackLikelyAi = primary?.let(::isLikelyAiSubtitleTrack) ?: false,
                    secondaryTrackLikelyAi = secondary?.let(::isLikelyAiSubtitleTrack) ?: false,
                    isMuted = player?.volume == 0f,
                    primaryTrackLanguage = primary?.lan,
                    secondaryTrackLanguage = secondary?.lan,
                )
            }
            if (session.selectedTrackKey == null) session.selectedTrackKey = primary?.trackKey
            session.acquiredInitialized = true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (identity == currentIdentity) message(error.message ?: "字幕加载失败")
        }
    }
    LaunchedEffect(acquireNativeSubtitles, nativeControls.trackAvailable) {
        if (!acquireNativeSubtitles && nativeControls.trackAvailable &&
            session.acquiredTracks.any { it.trackKey == session.selectedTrackKey }) {
            session.selectedTrackKey = null
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = importIdentity
        importIdentity = null
        if (uri != null && request != null && subtitleDocumentResultBelongsTo(request, currentIdentity)) {
            scope.launch {
                busy = true
                try {
                    val track = withContext(Dispatchers.IO) {
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: uri.lastPathSegment ?: "导入字幕"
                        val raw = context.contentResolver.openInputStream(uri)?.use { decodeSubtitleDocument(it.readBytes()) }
                            ?: error("无法读取字幕文件")
                        name to parseSubtitleDocument(name, raw)
                    }
                    if (subtitleDocumentResultBelongsTo(request, currentIdentity)) {
                        session.append(track.first, track.second)
                        currentNativeCallbacks.onEnabledChange(false)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (request == currentIdentity) message(error.message ?: "字幕导入失败")
                } finally { if (request == currentIdentity) busy = false }
            }
        }
    }
    val writeDocument: (android.net.Uri?) -> Unit = { uri ->
        val request = exportRequest
        exportRequest = null
        if (uri != null && request != null && subtitleDocumentResultBelongsTo(request.identity, currentIdentity)) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件")
                        stream.bufferedWriter(Charsets.UTF_8).use { it.write(request.text) }
                    }
                    if (request.identity == currentIdentity) message("字幕已保存")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (request.identity == currentIdentity) message(error.message ?: "字幕保存失败")
                }
            }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), writeDocument)
    val vttLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/vtt"), writeDocument)
    val srtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip"), writeDocument)

    val external = session.importedTracks.firstOrNull { it.key == session.selectedTrackKey }
    val acquired = session.acquiredTracks.firstOrNull { it.trackKey == session.selectedTrackKey }
    val managedNative = acquireNativeSubtitles && session.acquiredTracks.isNotEmpty()
    val availableNativeTracks = (nativeTracks + session.acquiredTracks).distinctBy { it.trackKey }
    val nativeOptions = if (managedNative) buildSubtitleTrackOptions(session.acquiredTracks, session.selectedTrackKey)
        else nativeControls.trackOptions
    val options = nativeOptions.map { if (external != null) it.copy(selected = false) else it } +
        session.importedTracks.map { SubtitleTrackOption(it.key, "local", it.name, it.key == session.selectedTrackKey, false) }
    val controls = when {
        external != null -> nativeControls.copy(
            trackAvailable = true, primaryAvailable = true, secondaryAvailable = false,
            enabled = session.externalEnabled, displayMode = if (session.externalEnabled) SubtitleDisplayMode.PRIMARY_ONLY else SubtitleDisplayMode.OFF,
            primaryLabel = external.name, trackOptions = options,
            largeTextEnabled = session.largeTextEnabled, positionLocked = positionLocked,
        )
        managedNative -> nativeControls.copy(
            trackAvailable = true, primaryAvailable = true,
            secondaryAvailable = session.acquiredSecondaryTrackKey != null,
            enabled = session.acquiredDisplayMode != SubtitleDisplayMode.OFF,
            displayMode = session.acquiredDisplayMode,
            primaryLabel = acquired?.let(::resolveSubtitleTrackDisplayLabel) ?: "字幕",
            secondaryLabel = session.acquiredTracks.firstOrNull { it.trackKey == session.acquiredSecondaryTrackKey }?.let(::resolveSubtitleTrackDisplayLabel) ?: "第二字幕",
            trackOptions = options, largeTextEnabled = session.largeTextEnabled, positionLocked = positionLocked,
        )
        else -> nativeControls.copy(
            trackAvailable = nativeControls.trackAvailable || session.importedTracks.isNotEmpty(), trackOptions = options
        )
    }
    val callbacks = nativeCallbacks.copy(
        onTrackSelected = { key ->
            val selection = ++nativeSelectionSerial
            if (session.importedTracks.any { it.key == key }) {
                session.selectedTrackKey = key
                session.externalEnabled = true
                currentNativeCallbacks.onEnabledChange(false)
            } else if (managedNative && session.acquiredTracks.any { it.trackKey == key }) {
                val track = session.acquiredTracks.first { it.trackKey == key }
                val cueGeneration = session.downloadedCueGeneration
                scope.launch {
                    try {
                        val cues = loadNativeCues(track)
                        if (identity == currentIdentity && selection == nativeSelectionSerial) {
                            if (!session.mergeAcquiredCues(mapOf(key to cues), cueGeneration)) return@launch
                            if (session.acquiredSecondaryTrackKey == key) {
                                session.acquiredSecondaryTrackKey = session.selectedTrackKey?.takeIf { previous ->
                                    session.acquiredTracks.any { it.trackKey == previous } && previous != key
                                }
                            }
                            session.selectedTrackKey = key
                            session.externalEnabled = true
                            session.acquiredDisplayMode = SubtitleDisplayMode.PRIMARY_ONLY
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (identity == currentIdentity && selection == nativeSelectionSerial) message(error.message ?: "字幕加载失败")
                    }
                }
            } else {
                session.selectedTrackKey = null
                currentNativeCallbacks.onTrackSelected(key)
                currentNativeCallbacks.onEnabledChange(true)
            }
        },
        onEnabledChange = { enabled ->
            if (acquired != null && managedNative) {
                session.acquiredDisplayMode = if (enabled) SubtitleDisplayMode.PRIMARY_ONLY else SubtitleDisplayMode.OFF
            } else if (session.selectedTrackKey != null) session.externalEnabled = enabled
            else currentNativeCallbacks.onEnabledChange(enabled)
        },
        onDisplayModeChange = { mode ->
            if (acquired != null && managedNative) {
                session.acquiredDisplayMode = normalizeSubtitleDisplayMode(mode, true, session.acquiredSecondaryTrackKey != null)
            } else if (session.selectedTrackKey != null) session.externalEnabled = mode != SubtitleDisplayMode.OFF
            else currentNativeCallbacks.onDisplayModeChange(mode)
        },
        onLargeTextChange = { enabled ->
            if (session.selectedTrackKey != null) session.largeTextEnabled = enabled
            else currentNativeCallbacks.onLargeTextChange(enabled)
        },
        onPositionLockedChange = { locked ->
            scope.launch { SettingsManager.setSubtitlePositionLocked(context, locked) }
        },
    )

    if (showStyle) PlayerSubtitleSettingsPanel(
        appearance = appearance,
        onAppearanceChange = { value ->
            appearance = normalizeSubtitleAppearance(value)
            val changed = appearance
            scope.launch { SubtitleToolsStore.setAppearance(context, changed) }
        },
        onDismiss = { showStyle = false },
        onTimingSettings = { showTiming = true },
        isFullscreen = isFullscreen,
    )
    if (showTiming) AlertDialog(
        onDismissRequest = { showTiming = false }, title = { Text("字幕时间偏移") },
        text = { Column {
            Text("${session.timeOffsetMs} ms（正值延迟字幕，与屏幕位置无关）")
            Slider(session.timeOffsetMs.toFloat(), { session.timeOffsetMs = it.roundToInt().toLong() }, valueRange = -60_000f..60_000f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ session.timeOffsetMs = (session.timeOffsetMs - 100L).coerceAtLeast(-60_000L) }) { Text("-100 ms") }
                TextButton({ session.timeOffsetMs = 0L }) { Text("重置") }
                TextButton({ session.timeOffsetMs = (session.timeOffsetMs + 100L).coerceAtMost(60_000L) }) { Text("+100 ms") }
            }
        } }, confirmButton = { TextButton({ showTiming = false }) { Text("完成") } }
    )
    if (showExport) AlertDialog(
        onDismissRequest = { showExport = false }, title = { Text("保存字幕") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (availableNativeTracks.isEmpty() && session.importedTracks.isEmpty()) Text("当前视频没有可导出的字幕，请先加载字幕。")
            val choices = availableNativeTracks.map { it.trackKey to resolveSubtitleTrackDisplayLabel(it) } + session.importedTracks.map { it.key to it.name }
            choices.forEach { (key, name) ->
                Text(name)
                Row {
                    SubtitleDocumentFormat.entries.forEach { format ->
                        TextButton(enabled = !busy, onClick = {
                            val requestIdentity = identity
                            busy = true
                            scope.launch {
                                try {
                                    val data = session.importedTracks.firstOrNull { it.key == key }?.cues ?: withContext(Dispatchers.IO) {
                                        val track = availableNativeTracks.first { it.trackKey == key }
                                        SubtitleContentRepository.cues(track.subtitleUrl, bvid, cid, track.id, track.idStr, track.lan).getOrThrow()
                                    }
                                    require(data.isNotEmpty()) { "字幕内容为空" }
                                    val text = withContext(Dispatchers.Default) { encodeSubtitleDocument(data, format) }
                                    if (subtitleDocumentResultBelongsTo(requestIdentity, currentIdentity)) {
                                        exportRequest = SubtitleExportRequest(requestIdentity, text)
                                        val fileName = "${bvid}_${cid}_${name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.${format.extension}"
                                        when (format) {
                                            SubtitleDocumentFormat.JSON -> jsonLauncher.launch(fileName)
                                            SubtitleDocumentFormat.WEBVTT -> vttLauncher.launch(fileName)
                                            SubtitleDocumentFormat.SRT -> srtLauncher.launch(fileName)
                                        }
                                        showExport = false
                                    }
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    if (requestIdentity == currentIdentity) message(error.message ?: "读取字幕失败")
                                } finally { if (requestIdentity == currentIdentity) busy = false }
                            }
                        }) { Text(format.name) }
                    }
                }
            }
        } }, confirmButton = { TextButton({ showExport = false }) { Text("关闭") } }
    )
    return PiliSubtitleTools(controls, callbacks,
        onLoadSubtitle = { if (identity.bvid.isNotBlank() && identity.cid > 0 && !busy) { nativeSelectionSerial++; importIdentity = identity; importLauncher.launch(arrayOf("*/*")) } },
        onSaveSubtitle = { showExport = true }, onSubtitleSettings = { showStyle = true },
        session = session, player = player, appearance = appearance)
}

@Composable
fun PiliExternalSubtitleOverlay(
    tools: PiliSubtitleTools,
    isFullscreen: Boolean,
    controlsVisible: Boolean,
    bottomControlsHeightPx: Int,
    modifier: Modifier = Modifier,
    isPortrait: Boolean = false,
    persistentBottomOverlayHeightProvider: () -> Int = { 0 },
) {
    val session = tools.session
    val track = session.importedTracks.firstOrNull { it.key == session.selectedTrackKey }
    val nativeTrack = session.acquiredTracks.firstOrNull { it.trackKey == session.selectedTrackKey }
    if (track == null && nativeTrack == null) return
    val player = tools.player ?: return
    if (track != null && !session.externalEnabled) return
    if (nativeTrack != null && session.acquiredDisplayMode == SubtitleDisplayMode.OFF) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offsetFlow = remember(context, isPortrait) {
        if (isPortrait) SettingsManager.getSubtitlePortraitVerticalOffsetFraction(context)
        else SettingsManager.getSubtitleVerticalOffsetFraction(context)
    }
    val storedOffset by offsetFlow.collectAsStateWithLifecycle(initialValue = 0f)
    var verticalOffset by remember(session.identity, isPortrait) { mutableFloatStateOf(storedOffset) }
    var dragging by remember(session.identity, isPortrait) { mutableStateOf(false) }
    LaunchedEffect(storedOffset) { if (!dragging) verticalOffset = storedOffset }
    val mode = if (track != null) SubtitleDisplayMode.PRIMARY_ONLY else session.acquiredDisplayMode
    val primaryCues = track?.cues ?: session.acquiredCues[session.selectedTrackKey].orEmpty()
    val secondaryCues = session.acquiredCues[session.acquiredSecondaryTrackKey].orEmpty()
    val subtitleTimeline = remember(primaryCues, secondaryCues, session.timeOffsetMs) {
        subtitlePositionTimeline(primaryCues, secondaryCues, session.timeOffsetMs)
    }
    val position by com.android.purebilibili.core.player.rememberPlaybackPosition(
        player = player,
        identity = session.identity,
        enabled = primaryCues.isNotEmpty() || secondaryCues.isNotEmpty(),
        timeline = subtitleTimeline,
    )
    val effectivePosition = subtitlePositionForOffset(position, session.timeOffsetMs)
    val text = if (shouldRenderPrimarySubtitle(mode)) resolveSubtitleTextAt(primaryCues, effectivePosition) else null
    val secondaryText = if (shouldRenderSecondarySubtitle(mode))
        resolveSubtitleTextAt(session.acquiredCues[session.acquiredSecondaryTrackKey].orEmpty(), effectivePosition) else null
    if (text.isNullOrBlank() && secondaryText.isNullOrBlank()) return
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val navigationInsetPx = WindowInsets.navigationBars.getBottom(density)
        val spatialModifier = Modifier.offset {
            val bottomOffsetPx = resolveSubtitleBottomOffsetPx(
                isFullscreen, controlsVisible, tools.controls.positionLocked,
                navigationInsetPx, bottomControlsHeightPx, density.density,
                persistentBottomOverlayHeightPx = persistentBottomOverlayHeightProvider(),
            )
            IntOffset(0, (heightPx * verticalOffset).roundToInt() - bottomOffsetPx)
        }.padding(horizontal = tools.appearance.horizontalPadding.dp)
            .padding(bottom = tools.appearance.bottomPadding.dp)
            .then(if (tools.controls.positionLocked) Modifier else Modifier.pointerInput(session.identity, heightPx) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        scope.launch {
                            if (isPortrait) SettingsManager.setSubtitlePortraitVerticalOffsetFraction(context, verticalOffset)
                            else SettingsManager.setSubtitleVerticalOffsetFraction(context, verticalOffset)
                        }
                    },
                    onDragCancel = { dragging = false },
                    onDrag = { change, amount ->
                        verticalOffset = normalizeSubtitleVerticalOffsetFraction(verticalOffset + amount.y / heightPx)
                        change.consume()
                    },
                )
            })
        Column(spatialModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (!secondaryText.isNullOrBlank()) PiliStyledSubtitleText(
                secondaryText, if (tools.controls.largeTextEnabled) 16 else 14, tools.appearance, isFullscreen,
                color = androidx.compose.ui.graphics.Color(tools.appearance.textColor).copy(alpha = 0.88f))
            if (!text.isNullOrBlank()) PiliStyledSubtitleText(
                text, if (tools.controls.largeTextEnabled) 18 else 16, tools.appearance, isFullscreen)
        }
    }
}
