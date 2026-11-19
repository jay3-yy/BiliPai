package com.android.purebilibili.feature.privacy

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal fun FragmentActivity.authenticatePrivacyAccess(
    request: PrivacyAuthenticationRequest,
    onResult: (PrivacyAuthenticationResult) -> Unit
) {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val availability = BiometricManager.from(this).canAuthenticate(authenticators)
    if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(PrivacyAuthenticationResult.Failure(resolvePrivacyAuthenticationUnavailableMessage(request)))
        return
    }

    var delivered = false
    fun deliver(result: PrivacyAuthenticationResult) {
        if (!delivered) {
            delivered = true
            onResult(result)
        }
    }

    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                deliver(PrivacyAuthenticationResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val message = when (errorCode) {
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED -> "已取消解锁"
                    else -> errString.toString().ifBlank { "解锁失败，请稍后重试" }
                }
                deliver(PrivacyAuthenticationResult.Failure(message))
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(request.reason.title)
        .setSubtitle(request.reason.subtitle)
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(promptInfo)
}

private fun resolvePrivacyAuthenticationUnavailableMessage(
    request: PrivacyAuthenticationRequest
): String {
    return when (request.reason) {
        PrivacyAuthenticationReason.OPEN_PRIVACY_CONTENT -> "请先设置系统锁屏后再解锁隐私内容"
    }
}
