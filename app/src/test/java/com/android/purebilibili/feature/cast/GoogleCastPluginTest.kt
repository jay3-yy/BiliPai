package com.android.purebilibili.feature.plugin.googlecast

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import com.android.purebilibili.core.plugin.CastPluginApi
import com.android.purebilibili.core.plugin.CastDiscoveryRequirement
import com.android.purebilibili.plugin.sdk.PluginCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCastPluginTest {

    private val plugin = GoogleCastPlugin()

    @Test
    fun `plugin is CastPluginApi`() {
        val api: CastPluginApi = plugin
        assertEquals(plugin.id, api.id)
        assertEquals(resolveGoogleCastDiscoveryRequirement(android.os.Build.VERSION.SDK_INT), api.discoveryRequirement)
    }

    @Test
    fun `app owned discovery requires local network permission starting with android 17`() {
        assertEquals(CastDiscoveryRequirement.NONE, resolveGoogleCastDiscoveryRequirement(36))
        assertEquals(CastDiscoveryRequirement.RAW_LOCAL_NETWORK, resolveGoogleCastDiscoveryRequirement(37))
        assertEquals(CastDiscoveryRequirement.RAW_LOCAL_NETWORK, resolveGoogleCastDiscoveryRequirement(38))
    }

    @Test
    fun `plugin id is google_cast`() {
        assertEquals("google_cast", plugin.id)
        assertEquals(GOOGLE_CAST_PLUGIN_ID, plugin.id)
    }

    @Test
    fun `plugin name is Google Cast`() {
        assertEquals("Google Cast", plugin.name)
    }

    @Test
    fun `description mentions Chromecast or Google Cast`() {
        val desc = plugin.description.lowercase()
        assertTrue(
            "description should mention casting to devices",
            desc.contains("chromecast") || desc.contains("google cast") || desc.contains("投屏")
        )
    }

    @Test
    fun `version is 0 dot 1 dot 0`() {
        assertEquals("0.1.1", plugin.version)
    }

    @Test
    fun `icon is Icons dot Rounded dot Cast`() {
        assertEquals(Icons.Rounded.Cast, plugin.icon)
    }

    @Test
    fun `capability manifest includes expected capabilities`() {
        val manifest = plugin.capabilityManifest
        assertEquals("google_cast", manifest.pluginId)
        assertEquals("Google Cast", manifest.displayName)
        assertEquals("0.1.1", manifest.version)
        assertEquals(1, manifest.apiVersion)
        assertEquals(
            "com.android.purebilibili.feature.plugin.googlecast.GoogleCastPlugin",
            manifest.entryClassName
        )
        val caps = manifest.capabilities
        assertTrue("should include PLAYER_STATE", PluginCapability.PLAYER_STATE in caps)
        assertTrue("should include PLAYER_CONTROL", PluginCapability.PLAYER_CONTROL in caps)
        assertTrue("should include NETWORK", PluginCapability.NETWORK in caps)
        assertTrue("should include PLUGIN_STORAGE", PluginCapability.PLUGIN_STORAGE in caps)
    }
}
