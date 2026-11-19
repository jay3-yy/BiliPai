package com.android.purebilibili.feature.home

enum class HomeAvatarAction {
    OPEN_DRAWER,
    OPEN_PROFILE,
    OPEN_LOGIN
}

fun resolveHomeAvatarAction(
    isLoggedIn: Boolean,
    isHomeDrawerEnabled: Boolean
): HomeAvatarAction {
    return when {
        !isHomeDrawerEnabled -> HomeAvatarAction.OPEN_PROFILE
        isLoggedIn -> HomeAvatarAction.OPEN_DRAWER
        else -> HomeAvatarAction.OPEN_LOGIN
    }
}
