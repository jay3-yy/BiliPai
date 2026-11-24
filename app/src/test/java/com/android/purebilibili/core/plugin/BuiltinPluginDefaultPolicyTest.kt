package com.android.purebilibili.core.plugin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltinPluginDefaultPolicyTest {

    @Test
    fun castPluginsAreEnabledByDefault() {
        assertTrue(resolvePluginDefaultEnabled(BuiltinPluginIds.DLNA_CAST))
        assertTrue(resolvePluginDefaultEnabled(BuiltinPluginIds.GOOGLE_CAST))
        assertTrue(resolvePluginDefaultEnabled(BuiltinPluginIds.GAMEPAD_CONTROL))
    }

    @Test
    fun optionalPluginsStayDisabledByDefault() {
        assertFalse(resolvePluginDefaultEnabled(BuiltinPluginIds.SPONSOR_BLOCK))
        assertFalse(resolvePluginDefaultEnabled(BuiltinPluginIds.AD_FILTER))
        assertFalse(resolvePluginDefaultEnabled("unknown_plugin"))
    }
}
