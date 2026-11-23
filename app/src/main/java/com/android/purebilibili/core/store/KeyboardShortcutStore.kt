package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.ui.adaptive.CustomKeyBinding
import com.android.purebilibili.core.ui.adaptive.CustomShortcutOverrides
import com.android.purebilibili.core.ui.adaptive.CustomShortcutSlot
import com.android.purebilibili.core.ui.adaptive.decodeCustomShortcutOverrides
import com.android.purebilibili.core.ui.adaptive.encodeCustomShortcutOverrides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

object KeyboardShortcutStore {
    private val key = stringPreferencesKey("custom_keyboard_shortcuts_v1")
    private val snapshot = MutableStateFlow(CustomShortcutOverrides())

    /** 供 Activity.dispatchKeyEvent / onProvideKeyboardShortcuts 同步读取的最新快照。 */
    val current: CustomShortcutOverrides
        get() = snapshot.value

    /** 快捷键录制弹窗打开时为 true，暂停 Activity 级全局快捷键拦截。 */
    @Volatile
    var isCapturingShortcut: Boolean = false

    fun observe(context: Context): Flow<CustomShortcutOverrides> =
        context.settingsDataStore.data
            .map { prefs -> decodeCustomShortcutOverrides(prefs[key]) }
            .onEach { decoded -> snapshot.value = decoded }

    suspend fun saveBinding(
        context: Context,
        slot: CustomShortcutSlot,
        binding: CustomKeyBinding,
    ) {
        context.settingsDataStore.edit { prefs ->
            val updated = decodeCustomShortcutOverrides(prefs[key]).withBinding(slot, binding)
            val encoded = encodeCustomShortcutOverrides(updated)
            if (encoded.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = encoded
            }
            snapshot.value = updated
        }
    }

    suspend fun resetSlot(
        context: Context,
        slot: CustomShortcutSlot,
    ) {
        context.settingsDataStore.edit { prefs ->
            val updated = decodeCustomShortcutOverrides(prefs[key]).resetSlot(slot)
            val encoded = encodeCustomShortcutOverrides(updated)
            if (encoded.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = encoded
            }
            snapshot.value = updated
        }
    }

    suspend fun replaceAll(
        context: Context,
        overrides: CustomShortcutOverrides,
    ) {
        context.settingsDataStore.edit { prefs ->
            val encoded = encodeCustomShortcutOverrides(overrides)
            if (encoded.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = encoded
            }
            snapshot.value = overrides
        }
    }

    suspend fun resetAll(context: Context) {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(key)
        }
        snapshot.value = CustomShortcutOverrides()
    }
}
