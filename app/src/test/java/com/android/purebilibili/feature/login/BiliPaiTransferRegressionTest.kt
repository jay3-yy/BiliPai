package com.android.purebilibili.feature.login

import android.app.Application
import android.content.Context
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.core.plugin.js.BiliPaiJsPluginManifest
import com.android.purebilibili.core.plugin.json.JsonPluginManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class BiliPaiTransferRegressionTest {
    @Test
    fun maximumPayload_fitsQrBudget_andDecryptsAfterOutOfOrderScanning() {
        val sender = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val receiver = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val base64 = Base64.getUrlEncoder()
        val request = BiliPaiTransferRequest(
            transferId = UUID.randomUUID().toString(),
            receiverDeviceId = base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(receiver.public.encoded)),
            receiverPublicKey = base64.encodeToString(receiver.public.encoded),
            expiresAt = System.currentTimeMillis() + 600_000,
        )
        val seed = BiliPaiTransferPayload(session = BiliPaiSessionBundle(42L, "session"), settingsJson = "")
        val json = Json { encodeDefaults = true }
        val overhead = json.encodeToString(BiliPaiTransferPayload.serializer(), seed).toByteArray().size
        val payload = seed.copy(settingsJson = "x".repeat(79_000 - overhead))
        val envelope = BiliPaiTransferCrypto.encryptPayload(sender, "sender", request, payload)
        val qrs = BiliPaiTransferCodec.encodeEnvelopeQrs(envelope)
        assertTrue(qrs.size in 2..128)
        // Encode the longest actual chunk, including its URI prefix and metadata.
        QRCodeWriter().encode(qrs.maxBy { it.length }, BarcodeFormat.QR_CODE, 512, 512)
        val buffer = BiliPaiTransferChunkBuffer()
        var joined: String? = null
        qrs.reversed().forEach { joined = buffer.offer(it).getOrThrow() }
        val decoded = BiliPaiTransferCrypto.decryptPayload(
            receiver.private, request, BiliPaiTransferCodec.decodeEnvelope(requireNotNull(joined)))
        assertEquals(payload, decoded)
    }

    @Test
    fun oversizeEnvelope_reportsReductionInsteadOfUnrenderableSingleQr() {
        val envelope = BiliPaiTransferEnvelope(
            transferId = "id", senderDeviceId = "sender", senderPublicKey = "key", wrappedKey = "key",
            iv = "iv", ciphertext = "x".repeat(110_000), signature = "sig", expiresAt = Long.MAX_VALUE,
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            BiliPaiTransferCodec.encodeEnvelopeQrs(envelope)
        }
        assertTrue(error.message.orEmpty().contains("减少"))
    }

    @Test
    fun duplicateChunks_doNotAdvance_andConflictingOrForeignChunksDoNotOverwrite() {
        val qrs = BiliPaiTransferChunks.split("first", "a".repeat(4_000))
        val buffer = BiliPaiTransferChunkBuffer()
        assertNull(buffer.offer(qrs.first()).getOrThrow())
        assertNull(buffer.offer(qrs.first()).getOrThrow())
        assertEquals(1, buffer.receivedCount())
        assertTrue(buffer.offer(qrs.first().substringBefore("&d=") + "&d=conflict").isFailure)
        assertTrue(buffer.offer(qrs.first().replace("id=first", "id=second")).isFailure)
        var joined: String? = null
        qrs.drop(1).forEach { joined = buffer.offer(it).getOrThrow() }
        assertEquals("a".repeat(4_000), joined)
        buffer.reset()
        assertEquals(0, buffer.receivedCount())
        val next = BiliPaiTransferChunks.split("second", "new transfer").single()
        assertEquals("new transfer", buffer.offer(next).getOrThrow())
    }

    @Test
    fun unapprovedPlugins_areSkipped_withoutWritingScriptsOrPackages() = runTest {
        val context = RuntimeEnvironment.getApplication() as Context
        val payload = BiliPaiTransferPayload(
            session = BiliPaiSessionBundle(42L, "session"),
            jsPlugins = listOf(BiliPaiTransferredJsPlugin(
                BiliPaiJsPluginManifest("transfer.unapproved", "未授权插件", permissions = setOf(PluginCapability.NETWORK)),
                script = "should not be installed", grantedCapabilities = setOf(PluginCapability.NETWORK), enabled = true,
            )),
            kotlinPlugins = listOf(BiliPaiTransferredPackage("未授权包", "invalid package")),
        )
        val report = BiliPaiTransferContent.apply(context, payload)
        assertEquals(2, report.items.size)
        assertTrue(report.items.all { !it.success && it.message == "未授权，已跳过" })
        assertFalse(java.io.File(context.filesDir, "bilipai_js_plugins/packages/transfer.unapproved/plugin.js").exists())
    }

    @Test
    fun missingSelectedPlugin_isAnExportError() = runTest {
        val error = transferCatching {
            BiliPaiTransferContent.collect(
                RuntimeEnvironment.getApplication(),
                BiliPaiTransferContentSelection(jsPluginIds = setOf("missing.plugin")),
                BiliPaiSessionBundle(42L, "session"),
            )
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("missing.plugin"))
    }

    @Test
    fun jsonRuleImport_updatesLiveRulesAndCachedPreferences_preservingOtherRules() = runTest {
        val context = RuntimeEnvironment.getApplication() as Context
        JsonPluginManager.initialize(context)
        val prefs = context.getSharedPreferences("json_plugins", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled_existing", false).commit()
        val payload = BiliPaiTransferPayload(
            session = BiliPaiSessionBundle(42L, "session"),
            jsonRules = BiliPaiTransferredJsonRules(
                sharedPrefsXml = """<map><boolean name="enabled_transfer_rule" value="false"/></map>""",
                files = listOf(BiliPaiTransferredFile("transfer_rule.json", """
                    {"id":"transfer_rule","name":"传输规则","type":"feed",
                     "rules":[{"field":"title","op":"contains","value":"广告","action":"hide"}]}
                """.trimIndent())),
            ),
        )
        val report = BiliPaiTransferContent.apply(context, payload)
        assertTrue(report.items.single().toString(), report.items.single().success)
        val rule = JsonPluginManager.plugins.value.single { it.plugin.id == "transfer_rule" }
        assertFalse(rule.enabled)
        assertFalse(prefs.getBoolean("enabled_transfer_rule", true))
        assertFalse(prefs.getBoolean("enabled_existing", true))
    }

    @Test
    fun transferErrors_doNotSwallowCoroutineCancellation() {
        assertThrows(CancellationException::class.java) {
            transferCatching<Unit> { throw CancellationException("closed") }
        }
    }

    @Test
    fun chunkBufferReportsMissingIndicesAndCompressesLargeJsonSettings() {
        val qrs = BiliPaiTransferChunks.split("t1", "a".repeat(2_600))
        assertTrue(qrs.size >= 3)
        val buffer = BiliPaiTransferChunkBuffer()
        assertNull(buffer.offer(qrs[0]).getOrThrow())
        assertNull(buffer.offer(qrs[2]).getOrThrow())
        assertTrue(1 in buffer.missingIndices())
        assertFalse(0 in buffer.missingIndices())
        assertFalse(2 in buffer.missingIndices())

        // Large pretty-printed JSON payload compresses well below the 79KB ciphertext cap
        val sender = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val receiver = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val base64 = Base64.getUrlEncoder()
        val request = BiliPaiTransferRequest(
            transferId = UUID.randomUUID().toString(),
            receiverDeviceId = base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(receiver.public.encoded)),
            receiverPublicKey = base64.encodeToString(receiver.public.encoded),
            expiresAt = System.currentTimeMillis() + 600_000,
        )
        val bigSettings = (1..350).joinToString(",", "{", "}") { "\"setting_key_$it\": \"value_text_$it\"" }
        val payload = BiliPaiTransferPayload(
            session = BiliPaiSessionBundle(42L, "session"),
            settingsJson = bigSettings,
        )
        val envelope = BiliPaiTransferCrypto.encryptPayload(sender, "sender", request, payload)
        val encodedQrs = BiliPaiTransferCodec.encodeEnvelopeQrs(envelope)
        assertTrue("Expected GZIP compression to keep chunk count compact, got ${encodedQrs.size}", encodedQrs.size <= 8)
        val decrypted = BiliPaiTransferCrypto.decryptPayload(receiver.private, request, envelope)
        assertEquals(payload, decrypted)
    }

    @Test
    fun contentOnlyTransfer_stripsSessionCredentialsAndRejectsEmptySelection() = runTest {
        val context = RuntimeEnvironment.getApplication() as Context
        val emptyError = transferCatching {
            BiliPaiTransferContent.collect(
                context = context,
                selection = BiliPaiTransferContentSelection(),
                session = BiliPaiSessionBundle(mid = 99L, sessData = "secret-cookie"),
                contentOnly = true,
            )
        }.exceptionOrNull()
        assertNotNull(emptyError)

        val payload = BiliPaiTransferContent.collect(
            context = context,
            selection = BiliPaiTransferContentSelection(settings = true),
            session = BiliPaiSessionBundle(mid = 99L, sessData = "secret-cookie"),
            contentOnly = true,
        )
        assertTrue(payload.contentOnly)
        assertEquals(0L, payload.session.mid)
        assertEquals("", payload.session.sessData)
        assertNotNull(payload.settingsJson)
    }
}
