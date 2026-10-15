package com.android.purebilibili.feature.video.screen

import androidx.compose.ui.layout.ContentScale

/** Fill the 16:9 loading cover and preserve the list card's crop during handoff.
 * Cover and playback share the same viewport height, without resizing at the first frame.
 */
internal object VideoDetailCoverContentScale : ContentScale by ContentScale.Crop
