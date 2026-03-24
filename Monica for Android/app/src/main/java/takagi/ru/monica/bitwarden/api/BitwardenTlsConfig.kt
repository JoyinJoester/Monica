package takagi.ru.monica.bitwarden.api

import android.util.Base64
import java.security.MessageDigest

/**
 * Bitwarden 连接的 TLS 配置。
 *
 * 注意：
 * 1. 不允许关闭 hostname 校验。
 * 2. 未提供配置时使用系统默认 TLS 信任链。
 */
data class BitwardenTlsConfig(
    val certificateAlias: String? = null,
    val caCertificatePem: String? = null,
    val mtlsEnabled: Boolean = false,
    val clientCertPkcs12Base64: String? = null,
    val clientCertPassword: String? = null
) {
    fun isEmpty(): Boolean {
        val hasCustomCa = !caCertificatePem.isNullOrBlank()
        val hasClientP12 = mtlsEnabled && !clientCertPkcs12Base64.isNullOrBlank()
        return !hasCustomCa && !hasClientP12
    }

    fun cacheFingerprint(): String {
        val raw = listOf(
            certificateAlias.orEmpty(),
            caCertificatePem.orEmpty(),
            mtlsEnabled.toString(),
            clientCertPkcs12Base64.orEmpty(),
            clientCertPassword.orEmpty()
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
