package com.android.purebilibili.feature.video.subtitle

import com.android.purebilibili.data.model.response.SubtitleItem

typealias SubtitleCue = com.android.purebilibili.core.subtitle.SubtitleCue
typealias SubtitleWordSpan = com.android.purebilibili.core.subtitle.SubtitleWordSpan
typealias SubtitleLoadResult = com.android.purebilibili.core.subtitle.SubtitleLoadResult
typealias SubtitleTrackMeta = com.android.purebilibili.core.subtitle.SubtitleTrackMeta
typealias SubtitleTrackOption = com.android.purebilibili.core.subtitle.SubtitleTrackOption
typealias SubtitleLanguageSelection = com.android.purebilibili.core.subtitle.SubtitleLanguageSelection
typealias SubtitleDisplayMode = com.android.purebilibili.core.subtitle.SubtitleDisplayMode
typealias SubtitleDisplayOption = com.android.purebilibili.core.subtitle.SubtitleDisplayOption
typealias SubtitlePreferenceSession = com.android.purebilibili.core.subtitle.SubtitlePreferenceSession
typealias SubtitleControlAvailability = com.android.purebilibili.core.subtitle.SubtitleControlAvailability
typealias SubtitleTextSizeSpec = com.android.purebilibili.core.subtitle.SubtitleTextSizeSpec
typealias SubtitleAutoPreference = com.android.purebilibili.core.subtitle.SubtitleAutoPreference

fun normalizeBilibiliSubtitleUrl(raw: String): String = com.android.purebilibili.core.subtitle.normalizeBilibiliSubtitleUrl(raw = raw)

fun isTrustedBilibiliSubtitleUrl(raw: String): Boolean = com.android.purebilibili.core.subtitle.isTrustedBilibiliSubtitleUrl(raw = raw)

fun isLikelyAiSubtitleTrack(track: SubtitleTrackMeta): Boolean = com.android.purebilibili.core.subtitle.isLikelyAiSubtitleTrack(track = track)

fun buildSubtitleTrackKey(
    subtitleId: Long,
    subtitleIdStr: String,
    languageCode: String?,
    subtitleUrl: String
): String = com.android.purebilibili.core.subtitle.buildSubtitleTrackKey(subtitleId = subtitleId, subtitleIdStr = subtitleIdStr, languageCode = languageCode, subtitleUrl = subtitleUrl)

fun mapPlayerInfoSubtitleTracks(subtitles: List<SubtitleItem>): List<SubtitleTrackMeta> = com.android.purebilibili.core.subtitle.mapPlayerInfoSubtitleTracks(subtitles = subtitles)

fun resolveSubtitleTrackDisplayLabel(track: SubtitleTrackMeta): String = com.android.purebilibili.core.subtitle.resolveSubtitleTrackDisplayLabel(track = track)

fun buildSubtitleTrackOptions(
    tracks: List<SubtitleTrackMeta>,
    selectedTrackKey: String?
): List<SubtitleTrackOption> = com.android.purebilibili.core.subtitle.buildSubtitleTrackOptions(tracks = tracks, selectedTrackKey = selectedTrackKey)

fun normalizeSubtitleVerticalOffsetFraction(value: Float): Float = com.android.purebilibili.core.subtitle.normalizeSubtitleVerticalOffsetFraction(value = value)

fun resolveSubtitleTextSizeSpec(
    playerWidthDp: Int,
    largeTextEnabled: Boolean
): SubtitleTextSizeSpec = com.android.purebilibili.core.subtitle.resolveSubtitleTextSizeSpec(playerWidthDp = playerWidthDp, largeTextEnabled = largeTextEnabled)

fun orderSubtitleTracksByPreference(tracks: List<SubtitleTrackMeta>): List<SubtitleTrackMeta> = com.android.purebilibili.core.subtitle.orderSubtitleTracksByPreference(tracks = tracks)

fun parseBiliSubtitleBody(rawJson: String): List<SubtitleCue> = com.android.purebilibili.core.subtitle.parseBiliSubtitleBody(rawJson = rawJson)

fun resolveDefaultSubtitleLanguages(
    tracks: List<SubtitleTrackMeta>,
    preferredPrimaryLanguage: String? = null
): SubtitleLanguageSelection = com.android.purebilibili.core.subtitle.resolveDefaultSubtitleLanguages(tracks = tracks, preferredPrimaryLanguage = preferredPrimaryLanguage)

fun resolveDefaultSubtitleDisplayMode(
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): SubtitleDisplayMode = com.android.purebilibili.core.subtitle.resolveDefaultSubtitleDisplayMode(hasPrimaryTrack = hasPrimaryTrack, hasSecondaryTrack = hasSecondaryTrack)

fun normalizeSubtitleDisplayMode(
    preferredMode: SubtitleDisplayMode,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): SubtitleDisplayMode = com.android.purebilibili.core.subtitle.normalizeSubtitleDisplayMode(preferredMode = preferredMode, hasPrimaryTrack = hasPrimaryTrack, hasSecondaryTrack = hasSecondaryTrack)

fun resolveSubtitleDisplayModeByAutoPreference(
    preference: SubtitleAutoPreference,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean,
    primaryTrackLikelyAi: Boolean,
    secondaryTrackLikelyAi: Boolean,
    isMuted: Boolean,
    primaryTrackLanguage: String?,
    secondaryTrackLanguage: String?
): SubtitleDisplayMode = com.android.purebilibili.core.subtitle.resolveSubtitleDisplayModeByAutoPreference(preference = preference, hasPrimaryTrack = hasPrimaryTrack, hasSecondaryTrack = hasSecondaryTrack, primaryTrackLikelyAi = primaryTrackLikelyAi, secondaryTrackLikelyAi = secondaryTrackLikelyAi, isMuted = isMuted, primaryTrackLanguage = primaryTrackLanguage, secondaryTrackLanguage = secondaryTrackLanguage)

fun resolveSubtitlePreferenceSession(
    bvid: String,
    cid: Long,
    primaryLanguage: String?,
    secondaryLanguage: String?,
    primaryTrackLikelyAi: Boolean,
    secondaryTrackLikelyAi: Boolean,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean,
    preference: SubtitleAutoPreference,
    isMuted: Boolean = false
): SubtitlePreferenceSession = com.android.purebilibili.core.subtitle.resolveSubtitlePreferenceSession(bvid = bvid, cid = cid, primaryLanguage = primaryLanguage, secondaryLanguage = secondaryLanguage, primaryTrackLikelyAi = primaryTrackLikelyAi, secondaryTrackLikelyAi = secondaryTrackLikelyAi, hasPrimaryTrack = hasPrimaryTrack, hasSecondaryTrack = hasSecondaryTrack, preference = preference, isMuted = isMuted)

fun resolveSubtitleDisplayModePreference(
    previousSessionKey: String?,
    nextSessionKey: String,
    previousMode: SubtitleDisplayMode,
    nextInitialMode: SubtitleDisplayMode
): SubtitleDisplayMode = com.android.purebilibili.core.subtitle.resolveSubtitleDisplayModePreference(previousSessionKey = previousSessionKey, nextSessionKey = nextSessionKey, previousMode = previousMode, nextInitialMode = nextInitialMode)

fun resolveSubtitleControlAvailability(
    primaryTrackBound: Boolean,
    secondaryTrackBound: Boolean,
    primaryCueAvailable: Boolean,
    secondaryCueAvailable: Boolean
): SubtitleControlAvailability = com.android.purebilibili.core.subtitle.resolveSubtitleControlAvailability(primaryTrackBound = primaryTrackBound, secondaryTrackBound = secondaryTrackBound, primaryCueAvailable = primaryCueAvailable, secondaryCueAvailable = secondaryCueAvailable)

fun resolveSubtitleDisplayOptions(
    primaryLabel: String,
    secondaryLabel: String,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): List<SubtitleDisplayOption> = com.android.purebilibili.core.subtitle.resolveSubtitleDisplayOptions(primaryLabel = primaryLabel, secondaryLabel = secondaryLabel, hasPrimaryTrack = hasPrimaryTrack, hasSecondaryTrack = hasSecondaryTrack)

fun shouldRenderPrimarySubtitle(mode: SubtitleDisplayMode): Boolean = com.android.purebilibili.core.subtitle.shouldRenderPrimarySubtitle(mode = mode)

fun shouldRenderSecondarySubtitle(mode: SubtitleDisplayMode): Boolean = com.android.purebilibili.core.subtitle.shouldRenderSecondarySubtitle(mode = mode)

fun resolveSubtitleTextAt(cues: List<SubtitleCue>, positionMs: Long): String? = com.android.purebilibili.core.subtitle.resolveSubtitleTextAt(cues = cues, positionMs = positionMs)

fun resolveSubtitlePositionPollingIdentity(
    bvid: String?,
    cid: Long,
): String = com.android.purebilibili.core.subtitle.resolveSubtitlePositionPollingIdentity(bvid = bvid, cid = cid)

fun shouldKeepSubtitleOverlayMounted(
    overlayEnabled: Boolean,
    isInPipMode: Boolean,
    isAudioOnly: Boolean,
    suppressOverlay: Boolean,
): Boolean = com.android.purebilibili.core.subtitle.shouldKeepSubtitleOverlayMounted(overlayEnabled = overlayEnabled, isInPipMode = isInPipMode, isAudioOnly = isAudioOnly, suppressOverlay = suppressOverlay)

fun resolveSubtitleDisplayTextAt(cues: List<SubtitleCue>, positionMs: Long): String? =
    com.android.purebilibili.core.subtitle.resolveSubtitleDisplayTextAt(cues, positionMs)
