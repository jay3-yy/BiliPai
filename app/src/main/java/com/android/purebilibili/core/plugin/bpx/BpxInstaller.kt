package com.android.purebilibili.core.plugin.bpx

import android.content.Context
import com.android.purebilibili.core.plugin.json.JsonPluginManager
import com.android.purebilibili.core.plugin.json.JsonRulePlugin
import com.android.purebilibili.core.plugin.skin.UiSkinColorTokens
import com.android.purebilibili.core.plugin.skin.UiSkinInstallStore
import com.android.purebilibili.core.plugin.skin.UiSkinManifest
import com.android.purebilibili.core.plugin.skin.UiSkinSelection
import com.android.purebilibili.core.plugin.skin.UiSkinSettingsStore
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BpxInstaller {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val _extensionsFlow = MutableStateFlow<List<InstalledBpxRecord>>(emptyList())
    val extensionsFlow: StateFlow<List<InstalledBpxRecord>> = _extensionsFlow.asStateFlow()

    @Volatile
    private var loadedOnce = false

    suspend fun ensureLoaded(context: Context) {
        if (loadedOnce) return
        withContext(Dispatchers.IO) {
            refreshState(context.applicationContext)
            loadedOnce = true
        }
    }

    private fun refreshState(appContext: Context): List<InstalledBpxRecord> {
        val list = listInstalledSync(appContext)
        _extensionsFlow.value = list
        return list
    }

    fun readScriptSource(context: Context, record: InstalledBpxRecord): String? {
        val root = File(context.applicationContext.filesDir, "bpx_extensions")
        val file = File(root, record.sourceFileName)
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    suspend fun installAndActivate(
        context: Context,
        preview: BpxPreviewPackage,
        settingValues: Map<String, String> = emptyMap(),
    ): Result<InstalledBpxRecord> = runCatching {
        val appContext = context.applicationContext
        val root = File(appContext.filesDir, "bpx_extensions").also { it.mkdirs() }
        val safeId = preview.manifest.id.safeFileSegment()
        val ext = if (preview.format == "json") "bpx.json" else "bpx.js"
        val sourceFile = File(root, "$safeId.$ext")
        val existing = metaFile(root, safeId).takeIf { it.isFile }?.let {
            runCatching { json.decodeFromString(InstalledBpxRecord.serializer(), it.readText()) }.getOrNull()
        }
        val mergedSettings = existing?.settingValues.orEmpty() + settingValues.filterValues { it.isNotBlank() }
        sourceFile.writeText(preview.rawSource)

        val record = InstalledBpxRecord(
            manifest = preview.manifest,
            format = preview.format,
            sha256 = preview.sha256,
            sourceFileName = sourceFile.name,
            installedAtMs = System.currentTimeMillis(),
            enabled = true,
            trippedCount = 0,
            settingValues = mergedSettings,
        )
        metaFile(root, safeId).writeText(json.encodeToString(record))

        val slots = preview.manifest.mounts.map { it.slot }.toSet()
        if ("theme.skin" in slots && preview.manifest.skin != null) {
            installSkinFromBpx(appContext, preview.manifest)
        }
        if ("feed.pipe" in slots && preview.manifest.rules.isNotEmpty()) {
            JsonPluginManager.initialize(appContext)
            val rulePlugin = JsonRulePlugin(
                id = preview.manifest.id,
                name = preview.manifest.name,
                description = preview.manifest.description,
                version = preview.manifest.version,
                author = preview.manifest.author.name,
                type = "feed",
                rules = preview.manifest.rules,
            )
            JsonPluginManager.installOrReplacePlugin(rulePlugin)
        }
        refreshState(appContext)
        record
    }

    suspend fun setEnabled(context: Context, extensionId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val root = File(appContext.filesDir, "bpx_extensions")
        val mf = metaFile(root, extensionId.safeFileSegment())
        if (!mf.isFile) return@withContext
        val current = runCatching { json.decodeFromString(InstalledBpxRecord.serializer(), mf.readText()) }
            .getOrNull() ?: return@withContext
        val updated = current.copy(enabled = enabled, trippedCount = if (enabled) 0 else current.trippedCount)
        mf.writeText(json.encodeToString(updated))
        if ("feed.pipe" in current.manifest.mounts.map { it.slot } && current.manifest.rules.isNotEmpty()) {
            JsonPluginManager.initialize(appContext)
            JsonPluginManager.setEnabled(extensionId, enabled)
        }
        refreshState(appContext)
    }

    suspend fun updateSettings(
        context: Context,
        extensionId: String,
        newSettings: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val root = File(appContext.filesDir, "bpx_extensions")
        val mf = metaFile(root, extensionId.safeFileSegment())
        if (!mf.isFile) return@withContext
        val current = runCatching { json.decodeFromString(InstalledBpxRecord.serializer(), mf.readText()) }
            .getOrNull() ?: return@withContext
        mf.writeText(json.encodeToString(current.copy(settingValues = newSettings)))
        refreshState(appContext)
    }

    suspend fun recordCircuitBreakerFailure(context: Context, extensionId: String) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val root = File(appContext.filesDir, "bpx_extensions")
        val mf = metaFile(root, extensionId.safeFileSegment())
        if (!mf.isFile) return@withContext
        val current = runCatching { json.decodeFromString(InstalledBpxRecord.serializer(), mf.readText()) }
            .getOrNull() ?: return@withContext
        val nextFailures = current.trippedCount + 1
        val autoTripped = nextFailures >= 3
        mf.writeText(
            json.encodeToString(
                current.copy(
                    trippedCount = nextFailures,
                    enabled = if (autoTripped) false else current.enabled,
                )
            )
        )
        refreshState(appContext)
    }

    suspend fun uninstall(context: Context, extensionId: String) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val root = File(appContext.filesDir, "bpx_extensions")
        val safeId = extensionId.safeFileSegment()
        val mf = metaFile(root, safeId)
        val current = mf.takeIf { it.isFile }?.let {
            runCatching { json.decodeFromString(InstalledBpxRecord.serializer(), it.readText()) }.getOrNull()
        }
        mf.delete()
        current?.let { File(root, it.sourceFileName).delete() }
        File(File(appContext.filesDir, "bpx_storage"), safeId).deleteRecursively()
        if (current != null && "feed.pipe" in current.manifest.mounts.map { it.slot }) {
            JsonPluginManager.initialize(appContext)
            JsonPluginManager.removePlugin(extensionId)
        }
        refreshState(appContext)
    }

    fun listInstalledSync(context: Context): List<InstalledBpxRecord> {
        val root = File(context.applicationContext.filesDir, "bpx_extensions")
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.name.endsWith(".meta.json") }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InstalledBpxRecord.serializer(), file.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.installedAtMs }
            .orEmpty()
    }

    private fun metaFile(root: File, safeId: String): File = File(root, "$safeId.meta.json")

    private fun String.safeFileSegment(): String = replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun installSkinFromBpx(context: Context, bpx: BpxManifest) {
        val spec = bpx.skin ?: return
        val surfaces = spec.surfaces.mapNotNullTo(linkedSetOf()) { name ->
            UiSkinSurface.entries.find { it.name == name }
        }.ifEmpty { setOf(UiSkinSurface.HOME_TOP_CHROME, UiSkinSurface.HOME_BOTTOM_BAR) }

        val light = spec.tokens.light
        val dark = spec.tokens.dark
        val glass = spec.tokens.liquidGlass
        val uiManifest = UiSkinManifest(
            formatVersion = 1,
            skinId = bpx.id,
            displayName = bpx.name,
            version = bpx.version,
            apiVersion = 1,
            author = bpx.author.name,
            surfaces = surfaces,
            colors = UiSkinColorTokens(
                topAtmosphereTint = light?.topAtmosphereTint ?: dark?.topAtmosphereTint,
                topForegroundTint = light?.topForegroundTint,
                topForegroundDarkTint = dark?.topForegroundTint,
                bottomBarTrimTint = light?.bottomBarTrimTint ?: dark?.bottomBarTrimTint,
                bottomBarIconTint = light?.bottomBarIconTint,
                bottomBarIconDarkTint = dark?.bottomBarIconTint,
                bottomBarSelectedTint = light?.bottomBarSelectedTint,
                bottomBarSelectedDarkTint = dark?.bottomBarSelectedTint,
                playerProgressActiveTint = light?.seekbarAccent ?: dark?.seekbarAccent,
                liquidGlassSurfaceTint = glass?.surfaceTint,
                liquidGlassTintOpacity = glass?.tintOpacity?.coerceIn(0.08f, 0.45f),
            ),
            licenseNote = bpx.licenseNote ?: bpx.license ?: "BPX Theme Skin",
            communityShareable = !bpx.licenseNote.isNullOrBlank(),
            containsOfficialAssets = false,
        )
        val pkgBytes = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("skin-manifest.json").apply { time = 0L })
                zip.write(json.encodeToString(UiSkinManifest.serializer(), uiManifest).toByteArray())
                zip.closeEntry()
            }
            out.toByteArray()
        }
        val store = UiSkinInstallStore.createDefault(context)
        val preview = store.previewPackage(pkgBytes).getOrThrow()
        val installed = store.installPreview(preview, pkgBytes).getOrThrow()
        UiSkinSettingsStore.setSelection(
            context = context,
            selection = UiSkinSelection(
                enabled = true,
                selectedSkinId = installed.skinId,
                selectedInstallId = installed.installId,
            )
        )
    }
}
