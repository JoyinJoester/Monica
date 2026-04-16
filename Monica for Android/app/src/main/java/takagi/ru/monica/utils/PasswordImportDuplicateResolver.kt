package takagi.ru.monica.utils

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager

data class ImportedPasswordSnapshot(
    val title: String,
    val username: String,
    val website: String,
    val password: String,
    val notes: String = "",
    val email: String = "",
    val phone: String = "",
    val authenticatorKey: String = ""
)

object PasswordImportDuplicateResolver {
    suspend fun findMatchingEntry(
        passwordRepository: PasswordRepository,
        securityManager: SecurityManager,
        snapshot: ImportedPasswordSnapshot,
        localOnly: Boolean
    ): PasswordEntry? {
        val candidates = if (localOnly) {
            passwordRepository.getLocalDuplicateCandidates(
                title = snapshot.title,
                username = snapshot.username,
                website = snapshot.website
            )
        } else {
            passwordRepository.getDuplicateCandidates(
                title = snapshot.title,
                username = snapshot.username,
                website = snapshot.website
            )
        }

        return candidates.firstOrNull { candidate ->
            matches(candidate, snapshot, securityManager)
        }
    }

    private fun matches(
        candidate: PasswordEntry,
        snapshot: ImportedPasswordSnapshot,
        securityManager: SecurityManager
    ): Boolean {
        if (!normalizedEquals(candidate.title, snapshot.title)) return false
        if (!normalizedEquals(candidate.username, snapshot.username)) return false
        if (!normalizedEquals(candidate.website, snapshot.website)) return false
        if (!normalizedEquals(candidate.notes, snapshot.notes)) return false
        if (!normalizedEquals(candidate.email, snapshot.email)) return false
        if (!normalizedEquals(candidate.phone, snapshot.phone)) return false
        if (!normalizedEquals(candidate.authenticatorKey, snapshot.authenticatorKey)) return false

        if (candidate.password == snapshot.password) {
            return true
        }

        val decryptedCandidatePassword = runCatching { securityManager.decryptData(candidate.password) }.getOrNull()
        if (decryptedCandidatePassword == snapshot.password) {
            return true
        }

        val decryptedImportedPassword = runCatching { securityManager.decryptData(snapshot.password) }.getOrNull()
        return decryptedImportedPassword != null && decryptedImportedPassword == decryptedCandidatePassword
    }

    private fun normalizedEquals(left: String?, right: String?): Boolean {
        return normalize(left) == normalize(right)
    }

    private fun normalize(value: String?): String {
        return value
            ?.replace("\r\n", "\n")
            ?.trim()
            .orEmpty()
    }
}
