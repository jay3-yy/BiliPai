package com.android.purebilibili.feature.login

import android.content.Context
import android.util.Base64
import com.android.purebilibili.core.plugin.kotlinpkg.ExternalKotlinPluginInstallStore
import com.android.purebilibili.core.plugin.js.BiliPaiJsPluginInstallStore
import com.android.purebilibili.core.plugin.skin.UiSkinInstallStore
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.settings.share.SettingsShareService
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import com.android.purebilibili.core.plugin.ExternalPluginInstallDecision
import com.android.purebilibili.core.plugin.evaluateExternalPluginInstall
import com.android.purebilibili.core.plugin.kotlinpkg.ExternalKotlinPluginPackagePreview
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.core.plugin.json.JsonPluginManager
import com.android.purebilibili.feature.personal.CompletionKeepsakeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Category-level selection of what a transfer carries besides the login session. */
data class BiliPaiTransferContentSelection(
    val settings: Boolean = false,
    val jsonRules: Boolean = false,
    val keepsakes: Boolean = false,
    val jsPluginIds: Set<String> = emptySet(),
    val skinIds: Set<String> = emptySet(),
    val kotlinPluginIds: Set<String> = emptySet(),
) {
    fun isNothingSelected(): Boolean = !settings && !jsonRules && !keepsakes && jsPluginIds.isEmpty() &&
        skinIds.isEmpty() && kotlinPluginIds.isEmpty()
}

data class BiliPaiTransferContentItem(val id: String, val name: String, val bytes: Long)

data class BiliPaiTransferContentInventory(
    val settingCount: Int,
    val jsPlugins: List<BiliPaiTransferContentItem>,
    val skins: List<BiliPaiTransferContentItem>,
    val kotlinPlugins: List<BiliPaiTransferContentItem>,
    val hasJsonRules: Boolean,
    val keepsakeCount: Int = 0,
)

data class BiliPaiTransferImportItemResult(val name: String, val success: Boolean, val message: String? = null)

data class BiliPaiTransferImportReport(
    val items: List<BiliPaiTransferImportItemResult>,
)

data class BiliPaiTransferPluginApproval(
    val key: String,
    val name: String,
    val capabilities: Set<PluginCapability>,
    val error: String? = null,
)

/**
 * Collects transferable settings/plugins on the sender and applies a payload on the receiver.
 * Session credentials are handled by [BiliPaiTransferSession]; this file only moves content.
 */
object BiliPaiTransferContent {

    suspend fun inventory(context: Context): BiliPaiTransferContentInventory {
        val jsStore = BiliPaiJsPluginInstallStore.createDefault(context)
        val skinStore = UiSkinInstallStore.createDefault(context)
        val kotlinStore = ExternalKotlinPluginInstallStore.createDefault(context)
        val keepsakeCount = CompletionKeepsakeStore.loadRecords(context).size
        return BiliPaiTransferContentInventory(
            settingCount = SettingsManager.getShareableSettingsEntryDefinitions().size,
            jsPlugins = jsStore.listInstalledPlugins().map {
                val bytes = jsStore.readScript(it).getOrNull()?.length?.toLong() ?: 0L
                BiliPaiTransferContentItem(it.manifest.id, it.manifest.title, bytes)
            },
            skins = skinStore.listInstalledPackages().map {
                BiliPaiTransferContentItem(it.skinId, it.displayName, fileBytes(File(it.packagePath)))
            },
            kotlinPlugins = kotlinStore.listInstalledPackages().map {
                BiliPaiTransferContentItem(
                    it.manifest.pluginId, it.manifest.displayName, fileBytes(File(it.packagePath)))
            },
            hasJsonRules = jsonRuleFiles(context).isNotEmpty() || jsonRulePrefsXml(context) != null,
            keepsakeCount = keepsakeCount,
        )
    }

    suspend fun collect(
        context: Context,
        selection: BiliPaiTransferContentSelection,
        session: BiliPaiSessionBundle,
        contentOnly: Boolean = false,
    ): BiliPaiTransferPayload = withContext(Dispatchers.IO) {
        if (selection.isNothingSelected()) {
            require(!contentOnly) { "仅转移备份时请至少勾选一项设置或数据" }
            return@withContext BiliPaiTransferPayload(session = session, contentOnly = false)
        }
        val js = BiliPaiJsPluginInstallStore.createDefault(context)
        val skins = UiSkinInstallStore.createDefault(context)
        val kotlin = ExternalKotlinPluginInstallStore.createDefault(context)
        // Resolve every selected ID. A disappeared or unreadable item must never become a silent omission.
        val jsById = js.listInstalledPlugins().associateBy { it.manifest.id }
        val skinsById = skins.listInstalledPackages().associateBy { it.skinId }
        val kotlinById = kotlin.listInstalledPackages().associateBy { it.manifest.pluginId }
        BiliPaiTransferPayload(
            session = if (contentOnly) BiliPaiSessionBundle(mid = 0L, sessData = "") else session,
            contentOnly = contentOnly,
            settingsJson = if (selection.settings) transferCatching {
                transferSettingsBridge(context).exportJson()
            }.getOrElse { throw IllegalStateException("设置导出失败：${it.message}", it) } else null,
            jsonRules = if (selection.jsonRules) BiliPaiTransferredJsonRules(
                sharedPrefsXml = jsonRulePrefsXml(context), files = jsonRuleFiles(context),
            ).also { require(it.sharedPrefsXml != null || it.files.isNotEmpty()) { "所选 JSON 规则已不存在，请重新选择" } } else null,
            jsPlugins = selection.jsPluginIds.sorted().map { id ->
                val installed = jsById[id] ?: error("所选 JS 插件已不存在：$id，请重新选择")
                val script = js.readScript(installed).getOrElse {
                    throw IllegalStateException("无法读取 JS 插件：${installed.manifest.title}", it)
                }
                BiliPaiTransferredJsPlugin(installed.manifest, script,
                    installed.grantedCapabilities, installed.enabled)
            },
            skins = selection.skinIds.sorted().map { id ->
                val installed = skinsById[id] ?: error("所选皮肤已不存在：$id，请重新选择")
                BiliPaiTransferredPackage(installed.displayName, encode64(readPackage(File(installed.packagePath), installed.displayName)))
            },
            kotlinPlugins = selection.kotlinPluginIds.sorted().map { id ->
                val installed = kotlinById[id] ?: error("所选插件包已不存在：$id，请重新选择")
                BiliPaiTransferredPackage(installed.manifest.displayName,
                    encode64(readPackage(File(installed.packagePath), installed.manifest.displayName)))
            },
            keepsakes = if (selection.keepsakes) {
                CompletionKeepsakeStore.loadRecords(context, accountMid = session.mid.takeIf { it > 0L })
            } else {
                emptyList()
            },
        )
    }

    /** Only the receiver's explicit choices authorize plugin installation and capabilities. */
    suspend fun pluginApprovals(context: Context, payload: BiliPaiTransferPayload): List<BiliPaiTransferPluginApproval> =
        withContext(Dispatchers.IO) {
            payload.jsPlugins.mapIndexed { index, plugin ->
                BiliPaiTransferPluginApproval("js:$index", plugin.manifest.title,
                    plugin.grantedCapabilities.intersect(plugin.manifest.permissions))
            } + payload.kotlinPlugins.mapIndexed { index, plugin ->
                val preview = transferCatching {
                    ExternalKotlinPluginInstallStore.createDefault(context)
                        .previewPackage(decode64(plugin.bytesBase64)).getOrThrow().also(::checkKotlinInstallPolicy)
                }
                BiliPaiTransferPluginApproval("kotlin:$index", plugin.name,
                    preview.getOrNull()?.descriptor?.manifest?.capabilities.orEmpty(),
                    preview.exceptionOrNull()?.message ?: if (preview.isFailure) "插件包无效" else null)
            }
        }

    /** Applies everything except the login session; each item fails independently. */
    suspend fun apply(
        context: Context,
        payload: BiliPaiTransferPayload,
        approvedPluginKeys: Set<String> = emptySet(),
        keepsakeAccountMidOverride: Long? = null,
    ): BiliPaiTransferImportReport = withContext(Dispatchers.IO) {
        val results = mutableListOf<BiliPaiTransferImportItemResult>()
        payload.settingsJson?.let { json ->
            results += transferCatching {
                transferSettingsBridge(context).applyJson(json)
            }.fold(
                onSuccess = { BiliPaiTransferImportItemResult("设置", true) },
                onFailure = { BiliPaiTransferImportItemResult("设置", false, it.message) },
            )
        }
        payload.jsonRules?.let { rules ->
            results += applyJsonRules(context, rules)
        }
        payload.jsPlugins.forEachIndexed { index, plugin ->
            if ("js:$index" !in approvedPluginKeys) {
                results += BiliPaiTransferImportItemResult("JS 插件：${plugin.manifest.title}", false, "未授权，已跳过")
                return@forEachIndexed
            }
            results += transferCatching {
                val store = BiliPaiJsPluginInstallStore.createDefault(context)
                val installed = store.installPlugin(
                    plugin.manifest, plugin.script, sourceUrl = null,
                    grantedCapabilities = plugin.grantedCapabilities,
                ).getOrThrow()
                if (plugin.enabled) store.setEnabled(installed.manifest.id, true)
            }.fold(
                onSuccess = { BiliPaiTransferImportItemResult("JS 插件：${plugin.manifest.title}", true) },
                onFailure = { BiliPaiTransferImportItemResult("JS 插件：${plugin.manifest.title}", false, it.message) },
            )
        }
        payload.skins.forEach { skin ->
            results += transferCatching {
                val store = UiSkinInstallStore.createDefault(context)
                val bytes = decode64(skin.bytesBase64)
                val preview = store.previewPackage(bytes).getOrThrow()
                store.installPreview(preview, bytes).getOrThrow()
            }.fold(
                onSuccess = { BiliPaiTransferImportItemResult("皮肤：${skin.name}", true) },
                onFailure = { BiliPaiTransferImportItemResult("皮肤：${skin.name}", false, it.message) },
            )
        }
        payload.kotlinPlugins.forEachIndexed { index, plugin ->
            if ("kotlin:$index" !in approvedPluginKeys) {
                results += BiliPaiTransferImportItemResult("插件包：${plugin.name}", false, "未授权，已跳过")
                return@forEachIndexed
            }
            results += transferCatching {
                val store = ExternalKotlinPluginInstallStore.createDefault(context)
                val bytes = decode64(plugin.bytesBase64)
                val preview = store.previewPackage(bytes).getOrThrow().also(::checkKotlinInstallPolicy)
                // The receiver explicitly reviewed this package and its declared capabilities.
                store.installPreview(preview, bytes, preview.descriptor.manifest.capabilities).getOrThrow()
            }.fold(
                onSuccess = { BiliPaiTransferImportItemResult("插件包：${plugin.name}", true) },
                onFailure = { BiliPaiTransferImportItemResult("插件包：${plugin.name}", false, it.message) },
            )
        }
        if (payload.keepsakes.isNotEmpty()) {
            results += transferCatching {
                val targetMid = keepsakeAccountMidOverride ?: payload.session.mid.takeIf { it > 0L }
                payload.keepsakes.asReversed().forEach { record ->
                    CompletionKeepsakeStore.saveRecord(context, record, accountMid = targetMid)
                }
            }.fold(
                onSuccess = { BiliPaiTransferImportItemResult("完结纪念（${payload.keepsakes.size} 条）", true) },
                onFailure = { BiliPaiTransferImportItemResult("完结纪念", false, it.message) },
            )
        }
        BiliPaiTransferImportReport(items = results)
    }

    private fun checkKotlinInstallPolicy(preview: ExternalKotlinPluginPackagePreview) {
        val decision = evaluateExternalPluginInstall(preview.descriptor, trustedSignerSha256 = emptySet())
        if (decision is ExternalPluginInstallDecision.Rejected) error(decision.reason)
    }

    private suspend fun applyJsonRules(
        context: Context,
        rules: BiliPaiTransferredJsonRules,
    ): BiliPaiTransferImportItemResult = transferCatching {
        require(rules.files.map { it.name }.distinct().size == rules.files.size) { "规则文件重复" }
        JsonPluginManager.initialize(context)
        JsonPluginManager.importTransferredRules(
            rules.files.associate { it.name to it.content },
            readJsonRuleEnabledStates(rules.sharedPrefsXml),
        )
    }.fold(
        onSuccess = { BiliPaiTransferImportItemResult("JSON 规则插件", true) },
        onFailure = { BiliPaiTransferImportItemResult("JSON 规则插件", false, it.message) },
    )

    // Serialize live preferences, including edits not yet flushed to the XML file.
    private fun jsonRulePrefsXml(context: Context): String? {
        val entries = context.getSharedPreferences("json_plugins", Context.MODE_PRIVATE).all
            .filter { it.key.startsWith("enabled_") && it.value is Boolean }
        if (entries.isEmpty()) return null
        val output = StringWriter()
        Xml.newSerializer().apply {
            setOutput(output)
            startDocument("UTF-8", true)
            startTag(null, "map")
            entries.toSortedMap().forEach { (key, value) ->
                startTag(null, "boolean")
                attribute(null, "name", key)
                attribute(null, "value", value.toString())
                endTag(null, "boolean")
            }
            endTag(null, "map")
            endDocument()
        }
        return output.toString()
    }

    private fun readJsonRuleEnabledStates(xml: String?): Map<String, Boolean> {
        if (xml == null) return emptyMap()
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val states = mutableMapOf<String, Boolean>()
        var event = parser.eventType
        var hasRoot = false
        while (event != XmlPullParser.END_DOCUMENT) {
            require(event != XmlPullParser.DOCDECL && event != XmlPullParser.ENTITY_REF) { "规则配置包含不支持的 XML" }
            if (event == XmlPullParser.START_TAG) {
                when {
                    parser.depth == 1 && parser.name == "map" -> hasRoot = true
                    parser.depth == 2 && parser.name == "boolean" -> {
                        val key = parser.getAttributeValue(null, "name").orEmpty()
                        val value = parser.getAttributeValue(null, "value")
                        require(key.startsWith("enabled_") && value in setOf("true", "false")) { "规则启用配置无效" }
                        states[key.removePrefix("enabled_")] = value == "true"
                    }
                    else -> error("规则配置格式无效")
                }
            }
            event = parser.nextToken()
        }
        require(hasRoot) { "规则配置缺少 map" }
        return states
    }

    private fun jsonRuleFiles(context: Context): List<BiliPaiTransferredFile> {
        val directory = File(context.filesDir, "json_plugins")
        if (!directory.exists()) return emptyList()
        require(directory.isDirectory) { "JSON 规则目录不可用" }
        val files = directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?: error("无法读取 JSON 规则目录，请重试")
        return files.sortedBy { it.name }.map { BiliPaiTransferredFile(it.name, it.readText(Charsets.UTF_8)) }
    }

    private fun transferSettingsBridge(context: Context) = BiliPaiTransferSettingsBridge(context)

    private fun fileBytes(file: File): Long = file.takeIf { it.isFile }?.length() ?: 0L

    private fun readPackage(file: File, name: String): ByteArray {
        require(file.isFile) { "无法读取所选内容：$name，请重新选择" }
        require(file.length() <= 79_000) { "$name 过大，请取消勾选后重试" }
        return file.readBytes()
    }

    private fun encode64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun decode64(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)
}

/**
 * Thin adapter over [SettingsShareService] so transfer stays in-memory: export/import
 * work on raw JSON strings without going through share Uris.
 */
internal class BiliPaiTransferSettingsBridge(private val context: Context) {
    private val service = SettingsShareService(context)

    suspend fun exportJson(): String =
        service.createExportArtifact(profileName = "设备传输", includeDeviceDebug = false).json

    suspend fun applyJson(rawJson: String) {
        val session = service.buildTransferImportSession(rawJson).getOrThrow()
        service.applyImport(session).getOrThrow()
    }
}
