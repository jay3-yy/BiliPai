package com.android.purebilibili.core.plugin.skin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val UI_SKIN_PREFS = "ui_skin_settings"
private const val KEY_ENABLED = "enabled"
private const val KEY_SELECTED_SKIN_ID = "selected_skin_id"
private const val KEY_SELECTED_INSTALL_ID = "selected_install_id"
private const val KEY_DISABLED_SURFACES = "disabled_surfaces"
private const val KEY_CUSTOM_FAN_NUMBER = "custom_fan_number"
private const val KEY_TONE_POLICY = "tone_policy"

object UiSkinSettingsStore {

    private val watchedKeys = setOf(
        KEY_ENABLED,
        KEY_SELECTED_SKIN_ID,
        KEY_SELECTED_INSTALL_ID,
        KEY_DISABLED_SURFACES,
        KEY_CUSTOM_FAN_NUMBER,
        KEY_TONE_POLICY,
    )

    fun observe(context: Context): Flow<UiSkinState> {
        val appContext = context.applicationContext
        return callbackFlow {
            val prefs = appContext.getSharedPreferences(UI_SKIN_PREFS, Context.MODE_PRIVATE)
            fun sendLatest() {
                trySend(readState(appContext))
            }
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key in watchedKeys) {
                    sendLatest()
                }
            }
            sendLatest()
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }.distinctUntilChanged()
    }

    fun readSelection(context: Context): UiSkinSelection {
        val prefs = context.applicationContext.getSharedPreferences(UI_SKIN_PREFS, Context.MODE_PRIVATE)
        val disabledNames = prefs.getStringSet(KEY_DISABLED_SURFACES, emptySet()).orEmpty()
        val disabledSurfaces = disabledNames.mapNotNullTo(linkedSetOf()) { name ->
            UiSkinSurface.entries.find { it.name == name }
        }
        val toneName = prefs.getString(KEY_TONE_POLICY, UiSkinTonePolicy.AUTO.name)
        val tonePolicy = UiSkinTonePolicy.entries.find { it.name == toneName } ?: UiSkinTonePolicy.AUTO
        return UiSkinSelection(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            selectedSkinId = prefs.getString(KEY_SELECTED_SKIN_ID, null),
            selectedInstallId = prefs.getString(KEY_SELECTED_INSTALL_ID, null),
            disabledSurfaces = disabledSurfaces,
            customFanNumber = prefs.getString(KEY_CUSTOM_FAN_NUMBER, "000088") ?: "000088",
            tonePolicy = tonePolicy,
        )
    }

    fun readState(context: Context): UiSkinState {
        return resolveUiSkinState(
            selection = readSelection(context),
            installedSkins = UiSkinInstallStore.createDefault(context).listInstalledPackages()
        )
    }

    fun setSelection(
        context: Context,
        selection: UiSkinSelection
    ) {
        val current = readSelection(context)
        val switchingSkin = selection.selectedInstallId != null &&
            selection.selectedInstallId != current.selectedInstallId
        val effectiveDisabled = if (switchingSkin && selection.disabledSurfaces.isEmpty()) {
            emptySet()
        } else {
            selection.disabledSurfaces
        }
        val effectiveFanNo = selection.customFanNumber.ifBlank { current.customFanNumber }
        val effectiveTone = if (switchingSkin && selection.tonePolicy == UiSkinTonePolicy.AUTO) {
            UiSkinTonePolicy.AUTO
        } else {
            selection.tonePolicy
        }
        context.applicationContext
            .getSharedPreferences(UI_SKIN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, selection.enabled)
            .putString(KEY_SELECTED_SKIN_ID, selection.selectedSkinId)
            .putString(KEY_SELECTED_INSTALL_ID, selection.selectedInstallId)
            .putStringSet(KEY_DISABLED_SURFACES, effectiveDisabled.mapTo(linkedSetOf()) { it.name })
            .putString(KEY_CUSTOM_FAN_NUMBER, effectiveFanNo)
            .putString(KEY_TONE_POLICY, effectiveTone.name)
            .apply()
    }

    fun setTonePolicy(
        context: Context,
        tonePolicy: UiSkinTonePolicy,
    ) {
        val current = readSelection(context)
        setSelection(context, current.copy(tonePolicy = tonePolicy))
    }

    fun setSurfaceEnabled(
        context: Context,
        surface: UiSkinSurface,
        enabled: Boolean,
    ) {
        val current = readSelection(context)
        val updatedDisabled = if (enabled) {
            current.disabledSurfaces - surface
        } else {
            current.disabledSurfaces + surface
        }
        setSelection(context, current.copy(disabledSurfaces = updatedDisabled))
    }

    fun setCustomFanNumber(
        context: Context,
        fanNumber: String,
    ) {
        val normalized = fanNumber.filter(Char::isDigit).take(8).ifBlank { "000088" }.padStart(6, '0')
        val current = readSelection(context)
        setSelection(context, current.copy(customFanNumber = normalized))
    }
}

@Composable
fun rememberUiSkinState(context: Context): State<UiSkinState> {
    return UiSkinSettingsStore.observe(context).collectAsStateWithLifecycle(initialValue = UiSkinState())
}
