package com.android.purebilibili.feature.video

/** 第二段机器可读声明优先；旧描述未声明坐标系时按协议使用屏幕坐标。 */
internal fun officialBoundsUseWindowCoordinates(metadata: String?): Boolean {
    val token = metadata?.split('|')?.getOrNull(1)?.trim()
    if (token.equals("WINDOW", ignoreCase = true)) return true
    if (token.equals("SCREEN", ignoreCase = true)) return false
    return metadata?.contains("WINDOW", ignoreCase = true) == true
}

/** 只使用设备声明可写的参数；旧版 int 声明允许公开合法值。 */
internal fun supportsOfficialBackType(metadata: String?, type: String): Boolean {
    if (metadata == null) return false
    val names = metadata.split('|').getOrNull(1)?.split(',')?.map(String::trim).orEmpty()
    val declared = names.filter { it.startsWith("VIEW_ANIM_") }
    return declared.isEmpty() || type in declared
}
