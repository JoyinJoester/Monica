package takagi.ru.monica.bitwarden

import takagi.ru.monica.bitwarden.api.BitwardenApiFactory
import takagi.ru.monica.bitwarden.api.ProfileResponse
import takagi.ru.monica.data.bitwarden.BitwardenVault
import java.util.Locale

/**
 * Bitwarden Vault 身份规范化工具。
 *
 * 统一处理邮箱、服务器地址与账号键，避免各模块各自拼接导致身份漂移。
 */
object BitwardenVaultIdentity {

    private const val ACCOUNT_KEY_SEPARATOR = "|"

    data class ProfileIdentity(
        val email: String,
        val canonicalEmail: String,
        val userId: String?,
        val displayName: String?,
        val accountKey: String
    )

    fun canonicalizeEmail(email: String?): String {
        return email.orEmpty().trim().lowercase(Locale.ENGLISH)
    }

    fun normalizeServerUrl(serverUrl: String): String {
        return BitwardenApiFactory.inferServerUrls(serverUrl).vault
            .trim()
            .trimEnd('/')
            .lowercase(Locale.ENGLISH)
    }

    fun resolveCanonicalEmail(vault: BitwardenVault): String {
        val storedCanonical = vault.canonicalEmail.trim()
        return if (storedCanonical.isNotEmpty()) {
            storedCanonical.lowercase(Locale.ENGLISH)
        } else {
            canonicalizeEmail(vault.email)
        }
    }

    fun buildAccountKey(
        serverUrl: String,
        userId: String?,
        canonicalEmail: String
    ): String {
        val normalizedServerUrl = normalizeServerUrl(serverUrl)
        val normalizedUserId = userId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ENGLISH)
        val normalizedEmail = canonicalizeEmail(canonicalEmail)
        val accountIdentity = normalizedUserId ?: normalizedEmail
        return normalizedServerUrl + ACCOUNT_KEY_SEPARATOR + accountIdentity
    }

    fun createProfileIdentity(
        serverUrl: String,
        profile: ProfileResponse,
        fallbackEmail: String
    ): ProfileIdentity {
        val displayEmail = profile.email.trim().ifEmpty { fallbackEmail.trim() }
        val canonicalEmail = canonicalizeEmail(displayEmail)
        val normalizedUserId = profile.id.trim().takeIf { it.isNotEmpty() }
        return ProfileIdentity(
            email = displayEmail,
            canonicalEmail = canonicalEmail,
            userId = normalizedUserId,
            displayName = profile.name?.trim()?.takeIf { it.isNotEmpty() },
            accountKey = buildAccountKey(
                serverUrl = serverUrl,
                userId = normalizedUserId,
                canonicalEmail = canonicalEmail
            )
        )
    }
}
