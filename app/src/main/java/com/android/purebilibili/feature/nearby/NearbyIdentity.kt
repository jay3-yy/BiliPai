package com.android.purebilibili.feature.nearby

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Calendar
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/** 私钥不可导出；每个安装独立生成身份，不包含共享密钥或登录凭据。 */
internal class NearbyIdentity {
    // v2 补齐 DIGEST_NONE：Android Conscrypt 在 TLS 1.2/1.3 握手签名时会调用 NONEwithECDSA。
    private val alias = "bilipai_nearby_identity_v2"
    private val legacyAlias = "bilipai_nearby_identity_v1"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (store.containsAlias(legacyAlias)) {
            runCatching { store.deleteEntry(legacyAlias) }
        }
        if (!store.containsAlias(alias)) {
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 20) }.time
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(
                        KeyProperties.DIGEST_NONE,
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512,
                    )
                    .setCertificateSubject(X500Principal("CN=BiliPai Nearby"))
                    .setCertificateNotBefore(Date(0)).setCertificateNotAfter(end).build())
                generateKeyPair()
            }
        }
    }
    val pin: String get() = nearbyPin(store.getCertificate(alias).publicKey.encoded)
    private val keys = NearbyTlsKeyManager(
        identityAlias = alias,
        identityKey = store.getKey(alias, null) as PrivateKey,
        identityChain = arrayOf(store.getCertificate(alias) as X509Certificate),
    )

    /** 信任锚来自用户核对的配对码或已信任设备，绝不使用全局宽松 TrustManager。 */
    fun tls(acceptPin: (String) -> Boolean): SSLContext = SSLContext.getInstance("TLS").apply {
        init(arrayOf(keys), arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = verify(chain)
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = verify(chain)
            private fun verify(chain: Array<out X509Certificate>) {
                val certificate = chain.singleOrNull() ?: throw CertificateException("设备证书不正确")
                certificate.checkValidity()
                certificate.verify(certificate.publicKey)
                if (!acceptPin(nearbyPin(certificate.publicKey.encoded))) throw CertificateException("设备身份不匹配，请重新配对")
            }
        }), null)
    }
}
