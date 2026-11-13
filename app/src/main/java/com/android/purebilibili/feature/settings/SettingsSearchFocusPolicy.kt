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
    const val PLAYBACK_COMMENTS = "playback_comments"
    const val PLAYBACK_FULLSCREEN = "playback_fullscreen"
    const val PLAYBACK_NETWORK = "playback_network"
    const val PLAYBACK_DATA_SAVER = "playback_data_saver"

    const val BOTTOM_BAR_BEHAVIOR = "bottom_bar_behavior"
    const val BOTTOM_BAR_START = "bottom_bar_start"
    const val BOTTOM_BAR_DISPLAY = "bottom_bar_display"
    const val BOTTOM_BAR_TOP_TABS = "bottom_bar_top_tabs"
    const val BOTTOM_BAR_SEARCH_TABS = "bottom_bar_search_tabs"
    const val BOTTOM_BAR_TABLET = "bottom_bar_tablet"
    const val BOTTOM_BAR_CURRENT = "bottom_bar_current"
    const val BOTTOM_BAR_AVAILABLE = "bottom_bar_available"

    const val ANIMATION_VISUAL_EFFECTS = "animation_visual_effects"
    const val ANIMATION_START = "animation_start"
}

data class SettingsSearchFocusRequest(
    val target: SettingsSearchTarget,
    val focusId: String,
    val token: Long = System.nanoTime(),
    val settingId: String? = null,
    val query: String = "",
)

data class SettingsSceneDetailFocus(
    val target: SettingsSearchTarget,
    val focusId: String
)

internal fun resolveSettingsItemScrollKey(settingId: String?): String? =
    settingId?.let { id -> settingsItemDirectory.firstOrNull { it.settingId == id }?.sectionKey }

internal object SettingsSearchHighlightTiming {
    const val HOLD_MILLIS = 1_600L
    const val FADE_MILLIS = 280
    const val UNCLAIMED_TIMEOUT_MILLIS = 5_000L
}

/** One timeout per page request; a row that is already locating owns its completion. */
internal suspend fun clearUnclaimedSettingsFocusRequest(token: Long) {
    kotlinx.coroutines.delay(SettingsSearchHighlightTiming.UNCLAIMED_TIMEOUT_MILLIS)
    SettingsSearchFocusController.clearIfUnclaimed(token)
}

object SettingsSearchFocusController {
    private val _request = MutableStateFlow<SettingsSearchFocusRequest?>(null)
    val request = _request.asStateFlow()

    private var claimedToken: Long? = null

    internal fun claim(token: Long): Boolean {
        if (_request.value?.token != token) return false
        claimedToken = token
        return true
    }

    internal fun clearIfUnclaimed(token: Long) {
        if (claimedToken != token) clear(token)
    }

    fun submit(target: SettingsSearchTarget, focusId: String?, settingId: String? = null, query: String = "") {
        claimedToken = null
        if (focusId.isNullOrBlank()) {
            _request.value = null
            return
        }
        _request.value = SettingsSearchFocusRequest(target = target, focusId = focusId, settingId = settingId, query = query.trim())
    }

    fun clear(token: Long? = null) {
        val current = _request.value ?: return
        if (token == null || current.token == token) {
            claimedToken = null
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
        SettingsSearchFocusIds.APPEARANCE_DISPLAY -> 4
        SettingsSearchFocusIds.APPEARANCE_SPLASH -> 6
        SettingsSearchFocusIds.APPEARANCE_PERSONALIZATION -> 6
        SettingsSearchFocusIds.APPEARANCE_TABLET -> null
        else -> null
    }
}

internal fun resolveHomeSettingsScrollIndex(focusId: String): Int? = when (focusId) {
    SettingsSearchFocusIds.HOME_OVERVIEW -> 0
    else -> null
}

/** Ordered section keys shared by search positioning and the merged playback page. */
internal val playbackSettingsSectionKeys = listOf(
    SettingsSearchFocusIds.PLAYBACK_DECODER,
    SettingsSearchFocusIds.PLAYBACK_SPEED,
    SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER,
    SettingsSearchFocusIds.PLAYBACK_DEBUG,
    SettingsSearchFocusIds.PLAYBACK_NETWORK,
    SettingsSearchFocusIds.PLAYBACK_DATA_SAVER,
    SettingsSearchFocusIds.PLAYBACK_INTERACTION,
    SettingsSearchFocusIds.PLAYBACK_COMMENTS,
    SettingsSearchFocusIds.PLAYBACK_FULLSCREEN,
)

internal fun resolvePlaybackSettingsScrollIndex(focusId: String): Int? {
    val sectionKey = if (focusId == SettingsSearchFocusIds.PLAYBACK_GESTURE) {
        SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
    } else focusId
    return playbackSettingsSectionKeys.indexOf(sectionKey)
        .takeIf { it >= 0 }
        ?.times(2)
}

internal fun resolveBottomBarSettingsScrollIndex(
    focusId: String
): Int? {
    return when (focusId) {
        SettingsSearchFocusIds.BOTTOM_BAR_START -> 0
        SettingsSearchFocusIds.BOTTOM_BAR_BEHAVIOR -> 1
        SettingsSearchFocusIds.BOTTOM_BAR_DISPLAY -> 3
        SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS -> 5
        SettingsSearchFocusIds.BOTTOM_BAR_SEARCH_TABS -> 7
        SettingsSearchFocusIds.BOTTOM_BAR_TABLET -> 9
        SettingsSearchFocusIds.BOTTOM_BAR_CURRENT -> 11
        SettingsSearchFocusIds.BOTTOM_BAR_AVAILABLE -> 13
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
        focusId = SettingsSearchFocusIds.PLAYBACK_COMMENTS
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
    SettingsSearchTarget.SPACE_PRIVACY,
    SettingsSearchTarget.LOGIN_ACTIVITY,
    SettingsSearchTarget.BLOCKED_LIST,
    SettingsSearchTarget.SETTINGS_SHARE,
    SettingsSearchTarget.WEBDAV_BACKUP,
    SettingsSearchTarget.DOWNLOAD_PATH,
    SettingsSearchTarget.IMAGE_SAVE_PATH,
    SettingsSearchTarget.CLEAR_CACHE,
    SettingsSearchTarget.PLUGINS,
    // 设备接力未开发完成：入口隐藏后与其他隐藏目标一样映射空焦点，恢复开关时随组生效。
    SettingsSearchTarget.NEARBY_HANDOFF,
    SettingsSearchTarget.EXPORT_LOGS,
    SettingsSearchTarget.OPEN_SOURCE_LICENSES,
    SettingsSearchTarget.OPEN_SOURCE_HOME,
    SettingsSearchTarget.CHECK_UPDATE,
    SettingsSearchTarget.VIEW_RELEASE_NOTES,
    SettingsSearchTarget.REPLAY_ONBOARDING,
    SettingsSearchTarget.TIPS,
    SettingsSearchTarget.KEYBOARD_SHORTCUTS,
    SettingsSearchTarget.OPEN_LINKS,
    SettingsSearchTarget.DONATE,
    SettingsSearchTarget.TELEGRAM,
    SettingsSearchTarget.TWITTER,
    SettingsSearchTarget.DISCLAIMER -> null
}
