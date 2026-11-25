package com.android.purebilibili.core.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes persisted requests and lifecycle effects before publishing execution state. */
internal class BuiltinPluginRegistry(
    private val readEnabled: suspend (String) -> Boolean,
    private val writeEnabled: suspend (String, Boolean) -> Unit,
    private val onFailure: (pluginId: String, error: Exception) -> Unit,
    private val onEnabledChanged: (BuiltinPlugin, Boolean) -> Unit
) {
    private val mutex = Mutex()
    private val pendingEnabledOverrides = mutableMapOf<String, Boolean>()
    private val readyPluginIds = MutableStateFlow<Set<String>>(emptySet())
    private val mutablePluginsFlow = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsFlow: StateFlow<List<PluginInfo>> = mutablePluginsFlow.asStateFlow()

    suspend fun register(plugin: BuiltinPlugin) {
        mutex.withLock {
            try {
                if (mutablePluginsFlow.value.any { it.plugin.id == plugin.id }) return@withLock

                val storedEnabled = try {
                    readEnabled(plugin.id).also { currentCoroutineContext().ensureActive() }
                } catch (error: Exception) {
                    onFailure(plugin.id, error)
                    if (error is CancellationException) throw error
                    return@withLock
                }
                val requestedEnabled = pendingEnabledOverrides.remove(plugin.id) ?: storedEnabled
                var enabled = false
                if (requestedEnabled) {
                    try {
                        plugin.onEnable()
                        currentCoroutineContext().ensureActive()
                        enabled = true
                    } catch (error: Exception) {
                        restoreLifecycle(plugin, enabled = false)
                        mutablePluginsFlow.update { current ->
                            if (current.any { it.plugin.id == plugin.id }) current else current + PluginInfo(plugin, false)
                        }
                        onFailure(plugin.id, error)
                        if (error is CancellationException) throw error
                        return@withLock
                    }
                }
                mutablePluginsFlow.update { current ->
                    if (current.any { it.plugin.id == plugin.id }) current else current + PluginInfo(plugin, enabled)
                }
            } finally {
                readyPluginIds.update { it + plugin.id }
            }
        }
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        mutex.withLock {
            val info = mutablePluginsFlow.value.firstOrNull { it.plugin.id == pluginId }
            if (info == null) {
                try {
                    writeEnabled(pluginId, enabled)
                    currentCoroutineContext().ensureActive()
                    pendingEnabledOverrides[pluginId] = enabled
                } catch (error: Exception) {
                    onFailure(pluginId, error)
                    throw error
                }
                return@withLock
            }
            if (info.enabled == enabled) return@withLock

            try {
                if (enabled) info.plugin.onEnable() else info.plugin.onDisable()
                currentCoroutineContext().ensureActive()
                writeEnabled(pluginId, enabled)
                currentCoroutineContext().ensureActive()
            } catch (error: Exception) {
                restoreLifecycle(info.plugin, info.enabled)
                onFailure(pluginId, error)
                if (error is CancellationException) throw error
                return@withLock
            }

            mutablePluginsFlow.update { plugins ->
                plugins.map { current ->
                    if (current.plugin.id == pluginId) current.copy(enabled = enabled) else current
                }
            }
            try {
                onEnabledChanged(info.plugin, enabled)
            } catch (error: Exception) {
                // Notification failure cannot roll back a committed lifecycle and store write.
                onFailure(pluginId, error)
                if (error is CancellationException) throw error
            }
        }
    }

    suspend fun awaitPluginReady(pluginId: String) {
        readyPluginIds.first { pluginId in it }
    }

    private suspend fun restoreLifecycle(plugin: BuiltinPlugin, enabled: Boolean) {
        withContext(NonCancellable) {
            try {
                if (enabled) plugin.onEnable() else plugin.onDisable()
            } catch (error: Exception) {
                onFailure(plugin.id, error)
            }
        }
    }
}
