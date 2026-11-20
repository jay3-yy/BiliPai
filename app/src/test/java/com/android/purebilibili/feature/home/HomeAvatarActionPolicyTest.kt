package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAvatarActionPolicyTest {

    @Test
    fun loggedInUser_opensDrawerWhenHomeDrawerIsEnabled() {
        val action = resolveHomeAvatarAction(
            isLoggedIn = true,
            isHomeDrawerEnabled = true
        )

        assertEquals(HomeAvatarAction.OPEN_DRAWER, action)
    }

    @Test
    fun loggedInUser_opensProfileWhenUsingSideNavigationLayout() {
        val action = resolveHomeAvatarAction(
            isLoggedIn = true,
            isHomeDrawerEnabled = false
        )

        assertEquals(HomeAvatarAction.OPEN_PROFILE, action)
    }

    @Test
    fun loggedOutUser_opensLogin() {
        val action = resolveHomeAvatarAction(
            isLoggedIn = false,
            isHomeDrawerEnabled = true
        )

        assertEquals(HomeAvatarAction.OPEN_LOGIN, action)
    }

    @Test
    fun loggedOutUser_opensProfileWhenDrawerIsDisabled() {
        assertEquals(HomeAvatarAction.OPEN_PROFILE, resolveHomeAvatarAction(false, false))
    }
}
