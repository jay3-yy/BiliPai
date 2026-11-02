@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.data.model.response.AiAudioInfo
import com.android.purebilibili.data.model.response.AiAudioItem
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.plugin.Anime4KPlugin
import com.android.purebilibili.plugin.sdk.VideoEnhancementPluginApi
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackOption
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.overlay.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Debug-only local playback laboratory. No account, network, or production routing is replaced. */
class PlayerParityTestActivity : ComponentActivity() {
    lateinit var player: ExoPlayer
        private set
    lateinit var playerView: PlayerView
        private set
    val renderedFrames = AtomicLong()
    var fullscreen by mutableStateOf(false)
    private var isBuffering by mutableStateOf(false)
    var glass by mutableStateOf(false)
    var uiStyle by mutableStateOf(AppUiStyle.MATERIAL3)
    var richCapabilities by mutableStateOf(true)
    var currentAspectRatio by mutableStateOf(VideoAspectRatio.FIT)
        private set
    var selectedPage by mutableIntStateOf(0)
        private set
    var audioLanguage by mutableStateOf("")
        private set
    var subtitleSelected by mutableStateOf(false)
        private set
    @Volatile var subtitleCue = ""
        private set
    lateinit var enhancement: VideoEnhancementPluginApi
        private set
    private val pages = List(3) { index -> Page(cid = index + 1L, page = index + 1, part = "Local episode ${index + 1}", duration = 3) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fullscreen = intent.getBooleanExtra("fullscreen", false)
        glass = intent.getBooleanExtra("glass", false)
        uiStyle = AppUiStyle.entries.firstOrNull {
            it.name.equals(intent.getStringExtra("style"), ignoreCase = true)
        } ?: AppUiStyle.MATERIAL3
        enhancement = Anime4KPlugin()
        runBlocking { enhancement.onEnable() }
        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setVideoFrameMetadataListener { _, _, _, _ -> renderedFrames.incrementAndGet() }
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    isBuffering = player.playbackState == Player.STATE_BUFFERING
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val identity = mediaItem?.mediaId.orEmpty().split(':')
                    selectedPage = identity.firstOrNull()?.toIntOrNull() ?: 0
                    audioLanguage = identity.getOrNull(1).orEmpty()
                }
                override fun onTracksChanged(tracks: Tracks) {
                    subtitleSelected = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
                }
                override fun onCues(cueGroup: CueGroup) {
                    subtitleCue = cueGroup.cues.joinToString { it.text.toString() }
                }
            })
            setMediaItem(localItem(0, ""))
            prepare()
            play()
        }
        setContent {
            val configuration = LocalConfiguration.current
            val config by enhancement.configState.collectAsState()
            CompositionLocalProvider(
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass),
                LocalAppUiStyle provides uiStyle,
            ) {
                MaterialTheme { MiuixTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        val height = if (fullscreen) Modifier.fillMaxHeight() else Modifier.height(
                            if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 260.dp else 300.dp,
                        )
                        Box(Modifier.fillMaxWidth().then(height).testTag("parity_player_viewport")) {
                            AndroidView(
                                factory = { context -> PlayerView(context).also {
                                    it.useController = false; it.player = player; playerView = it
                                } },
                                update = { it.resizeMode = currentAspectRatio.playerResizeMode },
                                modifier = Modifier.fillMaxSize(),
                            )
                            VideoPlayerOverlay(
                                state = VideoPlayerOverlayState(
                                    player = player, title = "Local player parity", isVisible = true,
                                    isFullscreen = fullscreen,
                                    isBuffering = isBuffering,
                                    currentQualityLabel = "Local MP4", qualityLabels = listOf("Local MP4"),
                                    qualityIds = listOf(16), switchableQualityIds = listOf(16),
                                    currentQuality = 16, bvid = "local-fixture", cid = selectedPage + 1L,
                                    videoDuration = 3, videoTitle = "Local player parity",
                                    currentAspectRatio = currentAspectRatio,
                                    pages = if (richCapabilities) pages else emptyList(), currentPageIndex = selectedPage,
                                    viewPoints = if (richCapabilities) listOf(
                                        ViewPoint("Opening", 0, 1), ViewPoint("Local chapter", 1, 3),
                                    ) else emptyList(),
                                    aiAudioInfo = if (richCapabilities) AiAudioInfo(items = listOf(
                                        AiAudioItem("en", "Local English stream", "asset:///player_parity_en.mp4"),
                                    )) else null,
                                    currentAudioLang = audioLanguage,
                                    subtitleControlState = SubtitleControlUiState(
                                        trackAvailable = richCapabilities, primaryAvailable = true,
                                        enabled = subtitleSelected,
                                        trackOptions = listOf(SubtitleTrackOption(
                                            trackKey = "en", languageCode = "en", label = "Local English subtitles",
                                            selected = subtitleSelected, likelyAi = false,
                                        )),
                                    ),
                                    anime4kAvailable = richCapabilities,
                                    anime4kEnabled = config.rememberedEnabled,
                                    anime4kPreset = config.preset, videoEnhancementAlgorithm = config.algorithm,
                                    playerDiagnosticLoggingEnabled = false,
                                ),
                                actions = VideoPlayerOverlayActions(
                                    onToggleVisible = {}, onQualitySelected = { player.prepare() },
                                    onBack = { if (fullscreen) fullscreen = false else finish() },
                                    onToggleFullscreen = { fullscreen = !fullscreen },
                                    onPortraitFullscreen = { fullscreen = true },
                                    onPlaybackSpeedChange = { player.setPlaybackSpeed(it) },
                                    onSeekTo = { player.seekTo(it) },
                                    onPageSelect = { index ->
                                        player.setMediaItem(localItem(index, audioLanguage)); player.prepare(); player.play()
                                    },
                                    onAudioLangChange = { language ->
                                        val position = player.currentPosition
                                        player.setMediaItem(localItem(selectedPage, language), position)
                                        player.prepare(); player.play()
                                    },
                                    onAspectRatioChange = { ratio ->
                                        currentAspectRatio = ratio
                                        playerView.resizeMode = ratio.playerResizeMode
                                    },
                                    subtitleControlCallbacks = SubtitleControlCallbacks(
                                        onTrackSelected = { language ->
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                .setPreferredTextLanguage(language).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                                        },
                                        onDisplayModeChange = { mode ->
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, mode == SubtitleDisplayMode.OFF).build()
                                        },
                                    ),
                                    onAnime4kToggle = {
                                        enhancement.setRememberAcrossVideos(true, currentVideoEnabled = it)
                                    },
                                    onAnime4kPresetChange = enhancement::setPreset,
                                    onVideoEnhancementAlgorithmChange = enhancement::setAlgorithm,
                                ),
                            )
                        }
                    }
                } }
            }
        }
    }

    private fun localItem(page: Int, language: String): MediaItem = MediaItem.Builder()
        .setUri(if (language == "en") "asset:///player_parity_en.mp4" else "asset:///player_parity.mp4")
        .setMediaId("$page:$language")
        .setSubtitleConfigurations(listOf(
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse("asset:///player_parity.vtt"))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(),
        )).build()

    fun rotateToLandscape(landscape: Boolean) {
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun hasValidVideoSurface(): Boolean = ::playerView.isInitialized &&
        (playerView.videoSurfaceView as? SurfaceView)?.holder?.surface?.isValid == true

    override fun onDestroy() {
        if (::playerView.isInitialized) playerView.player = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
