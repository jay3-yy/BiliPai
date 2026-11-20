package com.android.purebilibili.feature.download

internal data class ResumableDownloadPlan(
    val append: Boolean,
    val rangeStartBytes: Long,
    val initialDownloadedBytes: Long,
    val totalBytes: Long,
    val alreadyComplete: Boolean
)

internal fun resolveResumableDownloadPlan(
    existingBytes: Long,
    totalBytes: Long,
    acceptsRanges: Boolean
): ResumableDownloadPlan {
    val safeExistingBytes = existingBytes.coerceAtLeast(0L)
    val safeTotalBytes = totalBytes.coerceAtLeast(0L)

    if (safeTotalBytes > 0L && safeExistingBytes == safeTotalBytes) {
        return ResumableDownloadPlan(
            append = false,
            rangeStartBytes = safeTotalBytes,
            initialDownloadedBytes = safeTotalBytes,
            totalBytes = safeTotalBytes,
            alreadyComplete = true
        )
    }

    if (acceptsRanges && safeExistingBytes > 0L && (safeTotalBytes == 0L || safeExistingBytes < safeTotalBytes)) {
        return ResumableDownloadPlan(
            append = true,
            rangeStartBytes = safeExistingBytes,
            initialDownloadedBytes = safeExistingBytes,
            totalBytes = safeTotalBytes,
            alreadyComplete = false
        )
    }

    return ResumableDownloadPlan(
        append = false,
        rangeStartBytes = 0L,
        initialDownloadedBytes = 0L,
        totalBytes = safeTotalBytes,
        alreadyComplete = false
    )
}
