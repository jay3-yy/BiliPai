package com.android.purebilibili.feature.download

internal fun resolvePreferredDownloadQuality(
    availableQualities: List<Int>,
    preferredQuality: Int?,
    currentQuality: Int,
): Int {
    if (preferredQuality == null || availableQualities.isEmpty()) return currentQuality
    return availableQualities.filter { it <= preferredQuality }.maxOrNull()
        ?: availableQualities.min()
}
