package com.android.purebilibili.feature.video.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.video.ui.components.PlayerSubtitleAppearance

/** Imported documents own cue data, not a fabricated Bilibili download URL. */
data class ImportedSubtitleTrack(val key: String, val name: String, val cues: List<SubtitleCue>)

class PiliSubtitleSession internal constructor(val identity: SubtitleDocumentIdentity) {
    var importedTracks by mutableStateOf<List<ImportedSubtitleTrack>>(emptyList())
        private set
    var acquiredTracks by mutableStateOf<List<SubtitleTrackMeta>>(emptyList())
    var acquiredCues by mutableStateOf<Map<String, List<SubtitleCue>>>(emptyMap())
        private set
    var acquiredSecondaryTrackKey by mutableStateOf<String?>(null)
    var acquiredDisplayMode by mutableStateOf(SubtitleDisplayMode.OFF)
    var acquiredInitialized by mutableStateOf(false)
    var selectedTrackKey by mutableStateOf<String?>(null)
    var externalEnabled by mutableStateOf(true)
    var largeTextEnabled by mutableStateOf(false)
    var timeOffsetMs by mutableLongStateOf(0L)
    private var nextId = 0L
    private var downloadedCueUsers = 0
    internal var downloadedCueGeneration = 0L
        private set

    internal fun retainDownloadedCues() { downloadedCueUsers++ }

    internal fun releaseDownloadedCues() {
        check(downloadedCueUsers > 0)
        downloadedCueUsers--
    }

    internal fun trimUnusedDownloadedCues() {
        if (downloadedCueUsers != 0) return
        acquiredCues = emptyMap()
        downloadedCueGeneration++
    }

    internal fun mergeAcquiredCues(cues: Map<String, List<SubtitleCue>>, generation: Long): Boolean {
        if (downloadedCueUsers == 0 || generation != downloadedCueGeneration) return false
        acquiredCues = acquiredCues + cues
        return true
    }

    /** 恢复已选原生轨道的正文，导入文档与显示选项不参与清理或重新初始化。 */
    internal fun missingSelectedNativeTracks(): List<SubtitleTrackMeta> {
        val primary = acquiredTracks.firstOrNull { it.trackKey == selectedTrackKey } ?: return emptyList()
        return listOfNotNull(primary, acquiredTracks.firstOrNull { it.trackKey == acquiredSecondaryTrackKey })
            .distinctBy { it.trackKey }
            .filterNot { acquiredCues.containsKey(it.trackKey) }
    }

    fun append(name: String, cues: List<SubtitleCue>) {
        val track = ImportedSubtitleTrack("local:${identity.bvid}:${identity.cid}:${++nextId}", name, cues)
        importedTracks = importedTracks + track
        selectedTrackKey = track.key
        externalEnabled = true
    }
}

object PiliSubtitleSessions {
    private val sessions = LinkedHashMap<SubtitleDocumentIdentity, PiliSubtitleSession>(24, 0.75f, true)
    private val cleanupHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    internal fun releaseAfterUiHandoff(session: PiliSubtitleSession) {
        session.releaseDownloadedCues()
        // 同一轮界面更新中的全屏/普通模式可先接手；仅无人继续使用的会话释放正文。
        cleanupHandler.post { session.trimUnusedDownloadedCues() }
    }
    @Synchronized fun get(bvid: String, cid: Long): PiliSubtitleSession {
        val identity = SubtitleDocumentIdentity(bvid, cid)
        return sessions.getOrPut(identity) { PiliSubtitleSession(identity) }.also {
            if (sessions.size > 24) sessions.remove(sessions.keys.first())
        }
    }
}

/** Common native/imported text renderer: actual glyph stroke, weight, scale and background. */
@Composable
fun PiliStyledSubtitleText(
    text: String,
    baseFontSizeSp: Int,
    appearance: PlayerSubtitleAppearance,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(appearance.textColor),
) {
    if (text.isBlank()) return
    val scale = if (isFullscreen) appearance.fullscreenFontScale else appearance.inlineFontScale
    val context = LocalContext.current
    val fontFamily = remember(context, appearance.fontFileName) { loadSubtitleFontFamily(context, appearance.fontFileName) }
    val style = TextStyle(fontFamily = fontFamily, color = color, fontSize = (baseFontSizeSp * scale).sp,
        fontWeight = FontWeight(appearance.fontWeight * 100), textAlign = TextAlign.Center)
    val strokePx = with(LocalDensity.current) { appearance.strokeWidth.dp.toPx() }
    Box(modifier.background(Color.Black.copy(alpha = appearance.backgroundOpacity)).padding(horizontal = 4.dp, vertical = 2.dp)) {
        if (strokePx > 0f) BasicText(text, style = style.copy(color = Color(appearance.strokeColor), drawStyle = Stroke(strokePx)), maxLines = 4)
        BasicText(text, style = style, maxLines = 4)
    }
}

/**
 * Derives primary and secondary subtitle line states from a playback position [State] without
 * reading the position in the caller's composition scope, keeping cue switches isolated to the
 * leaf text composable.
 */
@Composable
internal fun rememberSubtitleLineStates(
    positionState: State<Long>,
    primaryCues: List<SubtitleCue>,
    secondaryCues: List<SubtitleCue>,
    displayMode: SubtitleDisplayMode,
    timeOffsetMs: Long,
    enabled: Boolean = true,
): Pair<State<String?>, State<String?>> {
    val primaryState = remember(positionState, primaryCues, displayMode, timeOffsetMs, enabled) {
        derivedStateOf {
            if (!enabled || !shouldRenderPrimarySubtitle(displayMode)) return@derivedStateOf null
            resolveSubtitleDisplayTextAt(
                primaryCues,
                subtitlePositionForOffset(positionState.value, timeOffsetMs),
            )
        }
    }
    val secondaryState = remember(positionState, secondaryCues, displayMode, timeOffsetMs, enabled) {
        derivedStateOf {
            if (!enabled || !shouldRenderSecondarySubtitle(displayMode)) return@derivedStateOf null
            resolveSubtitleDisplayTextAt(
                secondaryCues,
                subtitlePositionForOffset(positionState.value, timeOffsetMs),
            )
        }
    }
    return primaryState to secondaryState
}

/**
 * Renders primary/secondary subtitle lines in their own leaf composition scope so state reads
 * stay isolated from parent movable/host containers.
 */
@Composable
internal fun PiliDualSubtitleLines(
    primaryTextProvider: () -> String?,
    secondaryTextProvider: () -> String?,
    appearance: PlayerSubtitleAppearance,
    primaryTextSizeSp: Int,
    secondaryTextSizeSp: Int,
    isFullscreen: Boolean,
    useLocalTextStyleForGlass: Boolean = false,
) {
    val primaryText = primaryTextProvider()
    val secondaryText = secondaryTextProvider()
    val showPrimary = !primaryText.isNullOrBlank()
    val showSecondary = !secondaryText.isNullOrBlank()
    val secondaryAsPrimary = showSecondary && !showPrimary
    if (LocalAppThemeConfig.current.liquidGlassEnabled && appearance == PlayerSubtitleAppearance()) {
        val subtitleShadow = Shadow(
            color = Color.Black.copy(alpha = 0.85f),
            offset = Offset(0f, 1.5f),
            blurRadius = 6f,
        )
        val baseStyle = if (useLocalTextStyleForGlass) LocalTextStyle.current else TextStyle.Default
        val glassStyle = baseStyle.copy(shadow = subtitleShadow)
        AppText(
            text = secondaryText.orEmpty(),
            color = Color.White.copy(alpha = if (showSecondary) 0.88f else 0f),
            fontSize = (if (secondaryAsPrimary) primaryTextSizeSp else secondaryTextSizeSp).sp,
            fontWeight = if (secondaryAsPrimary) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = glassStyle,
            modifier = Modifier.then(if (showSecondary) Modifier else Modifier.height(0.dp)),
        )
        AppText(
            text = primaryText.orEmpty(),
            color = Color.White.copy(alpha = if (showPrimary) 1f else 0f),
            fontSize = primaryTextSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = glassStyle,
            modifier = Modifier.then(if (showPrimary) Modifier else Modifier.height(0.dp)),
        )
    } else {
        if (showSecondary) PiliStyledSubtitleText(
            text = secondaryText.orEmpty(),
            baseFontSizeSp = if (secondaryAsPrimary) primaryTextSizeSp else secondaryTextSizeSp,
            appearance = appearance,
            isFullscreen = isFullscreen,
            color = Color(appearance.textColor).copy(alpha = 0.88f),
        )
        if (showPrimary) PiliStyledSubtitleText(
            text = primaryText.orEmpty(),
            baseFontSizeSp = primaryTextSizeSp,
            appearance = appearance,
            isFullscreen = isFullscreen,
        )
    }
}
