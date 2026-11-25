package com.android.purebilibili.core.plugin.js

import android.content.Context
import com.android.purebilibili.plugin.sdk.PluginCapability
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class InstalledBiliPaiJsPlugin(
    val manifest: BiliPaiJsPluginManifest,
    val sourceUrl: String? = null,
    val scriptPath: String,
    val installedAtMillis: Long,
    val enabled: Boolean = false,
    val grantedCapabilities: Set<PluginCapability> = emptySet()
)

class BiliPaiJsPluginInstallStore(
    private val rootDir: File,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val metadataDir = File(rootDir, "installed")
    private val packageDir = File(rootDir, "packages")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun installPlugin(
        manifest: BiliPaiJsPluginManifest,
        script: String,
        sourceUrl: String?,
        grantedCapabilities: Set<PluginCapability>
    ): Result<InstalledBiliPaiJsPlugin> = runCatching {
        validateBiliPaiJsPluginManifest(manifest)?.let { error ->
            throw IllegalArgumentException(error)
        }
        val pluginDir = File(packageDir, manifest.id)
        pluginDir.mkdirs()
        metadataDir.mkdirs()
        val scriptFile = File(pluginDir, "plugin.js")
        scriptFile.writeText(script, Charsets.UTF_8)
        val installed = InstalledBiliPaiJsPlugin(
            manifest = manifest,
            sourceUrl = sourceUrl,
            scriptPath = scriptFile.absolutePath,
            installedAtMillis = clock(),
            enabled = false,
            grantedCapabilities = grantedCapabilities intersect manifest.permissions
        )
        writeInstalled(installed)
        installed
    }

    fun listInstalledPlugins(): List<InstalledBiliPaiJsPlugin> {
        return metadataDir
            .takeIf { it.exists() }
            ?.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    json.decodeFromString(InstalledBiliPaiJsPlugin.serializer(), file.readText(Charsets.UTF_8))
                }.getOrNull()
            }
            .sortedBy { it.manifest.title }
    }

    fun readScript(installed: InstalledBiliPaiJsPlugin): Result<String> = runCatching {
        File(installed.scriptPath).readText(Charsets.UTF_8)
    }

    fun setEnabled(pluginId: String, enabled: Boolean): InstalledBiliPaiJsPlugin? {
        val installed = readInstalled(pluginId) ?: return null
        val updated = installed.copy(enabled = enabled)
        writeInstalled(updated)
        return updated
    }

    fun removePlugin(pluginId: String): Boolean {
        val installed = readInstalled(pluginId) ?: return false
        File(installed.scriptPath).delete()
        File(packageDir, pluginId).deleteRecursively()
        metadataFile(pluginId).delete()
        return true
    }

    fun readInstalled(pluginId: String): InstalledBiliPaiJsPlugin? {
        val file = metadataFile(pluginId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(InstalledBiliPaiJsPlugin.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun writeInstalled(installed: InstalledBiliPaiJsPlugin) {
        metadataDir.mkdirs()
        metadataFile(installed.manifest.id)
            .writeText(json.encodeToString(InstalledBiliPaiJsPlugin.serializer(), installed), Charsets.UTF_8)
    }

    private fun metadataFile(pluginId: String): File {
        return File(metadataDir, "$pluginId.json")
    }

    companion object {
        fun createDefault(context: Context): BiliPaiJsPluginInstallStore {
            return BiliPaiJsPluginInstallStore(File(context.filesDir, "bilipai_js_plugins"))
        }
    }
}
