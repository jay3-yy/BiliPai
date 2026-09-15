package com.android.purebilibili.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsSearchFocusIds {
    const val APPEARANCE_THEME = "appearance_theme"
    const val APPEARANCE_DISPLAY = "appearance_display"
    const val APPEARANCE_SPLASH = "appearance_splash"
    const val APPEARANCE_PERSONALIZATION = "appearance_personalization"
    const val APPEARANCE_TABLET = "appearance_tablet"
    const val HOME_OVERVIEW = "home_overview"

    const val PLAYBACK_DECODER = "playback_decoder"
    const val PLAYBACK_SPEED = "playback_speed"
    const val PLAYBACK_MINI_PLAYER = "playback_mini_player"
    const val PLAYBACK_GESTURE = "playback_gesture"
    const val PLAYBACK_DEBUG = "playback_debug"
    const val PLAYBACK_INTERACTION = "playback_interaction"
    const val PLAYBACK_FULLSCREEN = "playback_fullscreen"
    const val PLAYBACK_NETWORK = "playback_network"
    const val PLAYBACK_DATA_SAVER = "playback_data_saver"

    const val BOTTOM_BAR_BEHAVIOR = "bottom_bar_behavior"
    const val BOTTOM_BAR_START = "bottom_bar_start"
    const val BOTTOM_BAR_DISPLAY = "bottom_bar_display"
    const val BOTTOM_BAR_TOP_TABS = "bottom_bar_top_tabs"
    const val BOTTOM_BAR_TABLET = "bottom_bar_tablet"
    const val BOTTOM_BAR_CURRENT = "bottom_bar_current"
    const val BOTTOM_BAR_AVAILABLE = "bottom_bar_available"

    const val ANIMATION_VISUAL_EFFECTS = "animation_visual_effects"
    const val ANIMATION_START = "animation_start"
}

data class SettingsSearchFocusRequest(
    val target: SettingsSearchTarget,
    val focusId: String,
    val token: Long = System.nanoTime()
)

data class SettingsSceneDetailFocus(
    val target: SettingsSearchTarget,
    val focusId: String
)

object SettingsSearchFocusController {
    private val _request = MutableStateFlow<SettingsSearchFocusRequest?>(null)
    val request = _request.asStateFlow()

    fun submit(target: SettingsSearchTarget, focusId: String?) {
        if (focusId.isNullOrBlank()) {
            _request.value = null
            return
        }
        _request.value = SettingsSearchFocusRequest(target = target, focusId = focusId)
    }

    fun clear(token: Long? = null) {
        val current = _request.value ?: return
        if (token == null || current.token == token) {
            _request.value = null
        }
    }
}

internal fun resolveAppearanceSettingsScrollIndex(
    focusId: String,
    isTablet: Boolean
): Int? {
    return when (focusId) {
        SettingsSearchFocusIds.APPEARANCE_THEME -> 0
        SettingsSearchFocusIds.APPEARANCE_DISPLAY -> 2
        SettingsSearchFocusIds.APPEARANCE_SPLASH -> 4
        SettingsSearchFocusIds.APPEARANCE_PERSONALIZATION -> 6
        SettingsSearchFocusIds.APPEARANCE_TABLET -> null
        else -> null
    }
}

internal fun resolveHomeSettingsScrollIndex(focusId: String): Int? = when (focusId) {
    SettingsSearchFocusIds.HOME_OVERVIEW -> 0
    else -> null
}

internal fun resolvePlaybackSettingsScrollIndex(
    focusId: String
): Int? {
    return when (focusId) {
        SettingsSearchFocusIds.PLAYBACK_DECODER -> 0
        SettingsSearchFocusIds.PLAYBACK_SPEED -> 2
        SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER -> 4
        SettingsSearchFocusIds.PLAYBACK_GESTURE -> 6
        SettingsSearchFocusIds.PLAYBACK_DEBUG -> 8
        SettingsSearchFocusIds.PLAYBACK_NETWORK -> 10
        SettingsSearchFocusIds.PLAYBACK_DATA_SAVER -> 12
        SettingsSearchFocusIds.PLAYBACK_INTERACTION -> 14
        SettingsSearchFocusIds.PLAYBACK_FULLSCREEN -> 16
        else -> null
    }
}

internal fun resolveBottomBarSettingsScrollIndex(
    focusId: String
): Int? {
    return when (focusId) {
        SettingsSearchFocusIds.BOTTOM_BAR_START -> 0
        SettingsSearchFocusIds.BOTTOM_BAR_BEHAVIOR -> 1
        SettingsSearchFocusIds.BOTTOM_BAR_DISPLAY -> 3
        SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS -> 5
        SettingsSearchFocusIds.BOTTOM_BAR_TABLET -> 7
        SettingsSearchFocusIds.BOTTOM_BAR_CURRENT -> 9
        SettingsSearchFocusIds.BOTTOM_BAR_AVAILABLE -> 11
        else -> null
    }
}

internal fun resolveAnimationSettingsScrollIndex(
    focusId: String
): Int? {
    return when (focusId) {
        SettingsSearchFocusIds.ANIMATION_START -> 0
        SettingsSearchFocusIds.ANIMATION_VISUAL_EFFECTS -> 4
        else -> null
    }
}

internal fun resolveSettingsSceneDetailFocus(
    target: SettingsSearchTarget
): SettingsSceneDetailFocus? = when (target) {
    SettingsSearchTarget.INTERFACE_THEME -> SettingsSceneDetailFocus(
        target = SettingsSearchTarget.APPEARANCE,
        focusId = SettingsSearchFocusIds.APPEARANCE_THEME
    )
    SettingsSearchTarget.HOME_FEED -> SettingsSceneDetailFocus(
        target = SettingsSearchTarget.HOME_FEED,
        focusId = SettingsSearchFocusIds.HOME_OVERVIEW
    )
    SettingsSearchTarget.NAVIGATION -> SettingsSceneDetailFocus(
        target = SettingsSearchTarget.BOTTOM_BAR,
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS
    )
    SettingsSearchTarget.PLAYBACK_QUALITY -> SettingsSceneDetailFocus(
        target = SettingsSearchTarget.PLAYBACK,
        focusId = SettingsSearchFocusIds.PLAYBACK_NETWORK
    )
    SettingsSearchTarget.FULLSCREEN_GESTURE -> SettingsSceneDetailFocus(
        target = SettingsSearchTarget.PLAYBACK,
        focusId = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
    )
    SettingsSearchTarget.INTERACTION_COMMENT -> SettingsSceneDetailFocus(
        target = SettingsSearchTarget.PLAYBACK,
        focusId = SettingsSearchFocusIds.PLAYBACK_INTERACTION
    )
    SettingsSearchTarget.DIAGNOSTICS -> SettingsSceneDetailFocus(
        target = SettingsSearchTarget.PLAYBACK,
        focusId = SettingsSearchFocusIds.PLAYBACK_DEBUG
    )
    SettingsSearchTarget.DATA_BACKUP,
    SettingsSearchTarget.PRIVACY_PERMISSION,
    SettingsSearchTarget.MESSAGE_NOTIFICATION,
    SettingsSearchTarget.ABOUT_SUPPORT,
    SettingsSearchTarget.APPEARANCE,
    SettingsSearchTarget.ANIMATION,
    SettingsSearchTarget.PLAYBACK,
    SettingsSearchTarget.BOTTOM_BAR,
    SettingsSearchTarget.PERMISSION,
    SettingsSearchTarget.BLOCKED_LIST,
    SettingsSearchTarget.SETTINGS_SHARE,
    SettingsSearchTarget.WEBDAV_BACKUP,
    SettingsSearchTarget.DOWNLOAD_PATH,
    SettingsSearchTarget.IMAGE_SAVE_PATH,
    SettingsSearchTarget.CLEAR_CACHE,
    SettingsSearchTarget.PLUGINS,
    SettingsSearchTarget.EXPORT_LOGS,
    SettingsSearchTarget.OPEN_SOURCE_LICENSES,
    SettingsSearchTarget.OPEN_SOURCE_HOME,
    SettingsSearchTarget.CHECK_UPDATE,
    SettingsSearchTarget.VIEW_RELEASE_NOTES,
    SettingsSearchTarget.REPLAY_ONBOARDING,
    SettingsSearchTarget.TIPS,
    SettingsSearchTarget.OPEN_LINKS,
    SettingsSearchTarget.DONATE,
    SettingsSearchTarget.TELEGRAM,
    SettingsSearchTarget.TWITTER,
    SettingsSearchTarget.DISCLAIMER -> null
}
