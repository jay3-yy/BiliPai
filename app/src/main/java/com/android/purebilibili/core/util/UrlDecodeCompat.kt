package com.android.purebilibili.core.util

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

internal fun decodeUrlComponentCompat(
    value: String,
    charset: Charset = Charsets.UTF_8
): String {
    return URLDecoder.decode(value, charset.name())
}

/**
 * Percent-encode one URI component with plain JDK APIs so route builders stay runnable off-device.
 * Maps form-encoded '+' to '%20' like the existing network percentEncode helpers, which keeps
 * output identical to android.net.Uri.encode for spaces while staying decodable by
 * [decodeUrlComponentCompat].
 */
internal fun encodeUrlComponentCompat(
    value: String,
    charset: Charset = Charsets.UTF_8
): String {
    return URLEncoder.encode(value, charset.name()).replace("+", "%20")
}
