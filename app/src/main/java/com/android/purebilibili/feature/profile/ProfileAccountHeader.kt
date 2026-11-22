package com.android.purebilibili.feature.profile

import com.android.purebilibili.data.model.response.SpaceAggregateImages
import com.android.purebilibili.feature.space.normalizeSpaceTopPhotoUrl
import com.android.purebilibili.feature.space.resolveSpaceTopImageItems

internal data class ProfileAccountHeaderPhoto(
    val header: String,
    val fullCover: String,
    val verticalBias: Float = 0f,
    val filterForTheme: Boolean = false,
)

internal fun resolveProfileAccountHeaderPhotos(
    images: SpaceAggregateImages?,
    isDarkTheme: Boolean,
): List<ProfileAccountHeaderPhoto> {
    if (images == null) return emptyList()
    val collection = resolveSpaceTopImageItems(images).mapNotNull { item ->
        val fullCover = normalizeSpaceTopPhotoUrl(item.fullCover)
        val header = normalizeSpaceTopPhotoUrl(item.header).ifEmpty { fullCover }
        if (header.isEmpty()) return@mapNotNull null
        ProfileAccountHeaderPhoto(
            header = header,
            fullCover = fullCover.ifEmpty { header },
            verticalBias = item.dy,
        )
    }
    if (collection.isNotEmpty()) return collection

    val day = normalizeSpaceTopPhotoUrl(images.imgUrl)
    val night = normalizeSpaceTopPhotoUrl(images.nightImgUrl)
    val photo = if (isDarkTheme) night.ifEmpty { day } else day
    return if (photo.isEmpty()) emptyList() else {
        listOf(ProfileAccountHeaderPhoto(header = photo, fullCover = photo, filterForTheme = true))
    }
}
