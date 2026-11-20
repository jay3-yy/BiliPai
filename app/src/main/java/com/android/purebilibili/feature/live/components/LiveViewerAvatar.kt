package com.android.purebilibili.feature.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.android.purebilibili.data.model.response.normalizeSearchImageUrl

@Composable
internal fun LiveViewerAvatar(
    face: String,
    modifier: Modifier = Modifier,
) {
    val placeholder = rememberVectorPainter(Icons.Outlined.Person)
    AsyncImage(
        model = normalizeSearchImageUrl(face).takeIf(String::isNotEmpty),
        contentDescription = null,
        placeholder = placeholder,
        fallback = placeholder,
        error = placeholder,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape).background(Color(0xFFE2E6EC)),
    )
}
