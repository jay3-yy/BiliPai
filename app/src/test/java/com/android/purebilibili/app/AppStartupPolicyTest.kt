package com.android.purebilibili.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupPolicyTest {


    @Test
    fun startupPlanKeepsCoreInitializersBeforeFirstInteractiveState() {
        val tasks = defaultAppStartupTasks(
            sdkInt = 34,
            deferredDelayMs = 900L,
            dex2OatDelayMs = 2_500L
        ).associateBy { it.id }

        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, tasks.getValue("plugin_manager_context_init").phase)
        assertEquals(StartupCriticality.REQUIRED, tasks.getValue("plugin_manager_context_init").criticality)
        assertEquals(StartupThread.MAIN, tasks.getValue("plugin_manager_context_init").thread)
        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, tasks.getValue("network_module_init").phase)
        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, tasks.getValue("token_manager_init").phase)
        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, tasks.getValue("wbi_key_restore").phase)
        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, tasks.getValue("video_repository_init").phase)
        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, tasks.getValue("background_manager_init").phase)
        assertEquals(StartupPhase.BEFORE_FIRST_INTERACTIVE, tasks.getValue("player_settings_cache_init").phase)
        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, tasks.getValue("notification_channel_init").phase)

        val orderedIds = defaultAppStartupTasks(
            sdkInt = 34,
            deferredDelayMs = 900L,
            dex2OatDelayMs = 2_500L
        ).map { it.id }
        assertTrue(
            "plugin context must be ready before deferred plugin registration",
            orderedIds.indexOf("plugin_manager_context_init") < orderedIds.indexOf("plugin_init")
        )
        assertTrue(
            "wbi_key_restore must run before video_repository_init",
            orderedIds.indexOf("wbi_key_restore") < orderedIds.indexOf("video_repository_init")
        )
    }

    @Test
    fun startupPlanDefersPlaylistTelemetryPluginAndProfileInstall() {
        val tasks = defaultAppStartupTasks(
            sdkInt = 34,
            deferredDelayMs = 900L,
            dex2OatDelayMs = 2_500L
        ).associateBy { it.id }

        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, tasks.getValue("playlist_restore").phase)
        assertEquals(StartupThread.MAIN_DELAYED, tasks.getValue("playlist_restore").thread)
        assertEquals(900L, tasks.getValue("playlist_restore").delayMs)

        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, tasks.getValue("telemetry_init").phase)
        assertEquals(StartupThread.MAIN_DELAYED, tasks.getValue("telemetry_init").thread)
        assertEquals(900L, tasks.getValue("telemetry_init").delayMs)

        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, tasks.getValue("plugin_init").phase)
        assertEquals(StartupThread.MAIN_IDLE, tasks.getValue("plugin_init").thread)

        assertEquals(StartupCriticality.DEFERRED, tasks.getValue("notification_channel_init").criticality)
        assertEquals(StartupThread.MAIN_IDLE, tasks.getValue("notification_channel_init").thread)

        assertEquals(StartupPhase.AFTER_FIRST_INTERACTIVE, tasks.getValue("dex2oat_profile_install").phase)
        assertEquals(StartupThread.MAIN_DELAYED, tasks.getValue("dex2oat_profile_install").thread)
        assertEquals(2_500L, tasks.getValue("dex2oat_profile_install").delayMs)
    }

    @Test
    fun startupPlanSkipsDex2OatInstallWhenSdkTooLow() {
        val taskIds = defaultAppStartupTasks(
            sdkInt = 23,
            deferredDelayMs = 900L,
            dex2OatDelayMs = 2_500L
        ).map { it.id }

        assertTrue("dex2oat_profile_install should be omitted below Android N", "dex2oat_profile_install" !in taskIds)
    }
}
