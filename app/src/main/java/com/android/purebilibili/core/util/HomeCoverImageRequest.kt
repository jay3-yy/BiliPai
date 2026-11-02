package com.android.purebilibili.core.util

import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale

/** Match cropped AsyncImage covers so preloading accepts the same decoded memory entries. */
internal fun ImageRequest.Builder.homeCoverDecodePolicy(): ImageRequest.Builder =
    scale(Scale.FILL).precision(Precision.INEXACT)
