package takagi.ru.monica.bitwarden.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.CipherApiResponse
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotFieldGroupPreview
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotFieldPreview
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotPreview
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotPreviewStatus

class BitwardenSyncSnapshotPreviewParser {

    companion object {
        private const val TAG = "BwSyncSnapshotPreview"
        private val cipherStringPattern =
            Regex("^[0-9]+\\.[A-Za-z0-9+/_=-]+\\|[A-Za-z0-9+/_=-]+(?:\\|[A-Za-z0-9+/_=-]+)?$")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(
        payload: String?,
        symmetricKey: SymmetricCryptoKey?
    ): BitwardenSyncSnapshotPreview? {
        if (payload.isNullOrBlank()) return null

        val cipher = runCatching {
            json.decodeFromString<CipherApiResponse>(payload.trim())
        }.getOrElse {
            symmetricKey?.clear()
            return BitwardenSyncSnapshotPreview(
                status = BitwardenSyncSnapshotPreviewStatus.INVALID_PAYLOAD
            )
        }

        if (cipher.type !in setOf(1, 2, 3, 4)) {
            symmetricKey?.clear()
            return BitwardenSyncSnapshotPreview(
                status = BitwardenSyncSnapshotPreviewStatus.UNSUPPORTED_TYPE,
                cipherType = cipher.type,
                metadataFields = parseMetadataFields(cipher)
            )
        }

        val effectiveKey = if (symmetricKey != null) {
            BitwardenCipherKeyResolver.resolveCipherKey(cipher, symmetricKey, TAG)
        } else {
            null
        }

        return try {
            when (cipher.type) {
                1 -> parseLoginCipher(cipher, effectiveKey)
                2 -> parseSecureNoteCipher(cipher, effectiveKey)
                3 -> parseCardCipher(cipher, effectiveKey)
                4 -> parseIdentityCipher(cipher, effectiveKey)
                else -> BitwardenSyncSnapshotPreview(
                    status = BitwardenSyncSnapshotPreviewStatus.UNSUPPORTED_TYPE,
                    cipherType = cipher.type,
                    metadataFields = parseMetadataFields(cipher)
                )
            }
        } catch (_: Throwable) {
            BitwardenSyncSnapshotPreview(
                status = BitwardenSyncSnapshotPreviewStatus.INVALID_PAYLOAD,
                cipherType = cipher.type,
                metadataFields = parseMetadataFields(cipher)
            )
        } finally {
            BitwardenCipherKeyResolver.clearIfDerived(effectiveKey, symmetricKey)
            symmetricKey?.clear()
        }
    }

    private fun parseLoginCipher(
        cipher: CipherApiResponse,
        key: SymmetricCryptoKey?
    ): BitwardenSyncSnapshotPreview {
        val login = cipher.login
        val extraSections = buildList {
            val uriFields = login?.uris.orEmpty().mapIndexedNotNull { index, uri ->
                val suffix = uri.match?.let { " (match=$it)" }.orEmpty()
                decryptOrPlain(uri.uri, key)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { BitwardenSyncSnapshotFieldPreview("URI ${index + 1}$suffix", it) }
            }
            if (uriFields.isNotEmpty()) {
                add(BitwardenSyncSnapshotFieldGroupPreview(title = "Login URIs", fields = uriFields))
            }

            login?.fido2Credentials.orEmpty().forEachIndexed { index, credential ->
                val fields = buildList {
                    addIfPresent("Credential ID", decryptOrPlain(credential.credentialId, key))
                    addIfPresent("Key Type", decryptOrPlain(credential.keyType, key))
                    addIfPresent("Key Algorithm", decryptOrPlain(credential.keyAlgorithm, key))
                    addIfPresent("Key Curve", decryptOrPlain(credential.keyCurve, key))
                    addIfPresent("RP ID", decryptOrPlain(credential.rpId, key))
                    addIfPresent("RP Name", decryptOrPlain(credential.rpName, key))
                    addIfPresent("Counter", decryptOrPlain(credential.counter, key))
                    addIfPresent("User Name", decryptOrPlain(credential.userName, key))
                    addIfPresent("User Display Name", decryptOrPlain(credential.userDisplayName, key))
                    addIfPresent("Discoverable", decryptOrPlain(credential.discoverable, key))
                    addIfPresent("Creation Date", decryptOrPlain(credential.creationDate, key))
                    addHiddenIfPresent("User Handle", decryptOrPlain(credential.userHandle, key))
                    addHiddenIfPresent("Key Value", decryptOrPlain(credential.keyValue, key))
                }
                if (fields.isNotEmpty()) {
                    add(
                        BitwardenSyncSnapshotFieldGroupPreview(
                            title = "FIDO2 Credential ${index + 1}",
                            fields = fields
                        )
                    )
                }
            }
        }

        return BitwardenSyncSnapshotPreview(
            status = readyOrLocked(key),
            cipherType = cipher.type,
            title = decryptOrPlain(cipher.name, key).orEmpty(),
            username = decryptOrPlain(login?.username, key).orEmpty(),
            password = decryptOrPlain(login?.password, key).orEmpty(),
            totp = decryptOrPlain(login?.totp, key).orEmpty(),
            websites = login?.uris
                .orEmpty()
                .mapNotNull { decryptOrPlain(it.uri, key)?.trim() }
                .filter { it.isNotBlank() }
                .distinct(),
            notes = decryptOrPlain(cipher.notes, key).orEmpty(),
            customFields = parseCustomFields(cipher, key),
            metadataFields = parseMetadataFields(cipher),
            extraSections = extraSections
        )
    }

    private fun parseSecureNoteCipher(
        cipher: CipherApiResponse,
        key: SymmetricCryptoKey?
    ): BitwardenSyncSnapshotPreview {
        val secureNoteFields = buildList {
            addIfPresent("Secure Note Type", cipher.secureNote?.type?.toString())
        }

        return BitwardenSyncSnapshotPreview(
            status = readyOrLocked(key),
            cipherType = cipher.type,
            title = decryptOrPlain(cipher.name, key).orEmpty(),
            notes = decryptOrPlain(cipher.notes, key).orEmpty(),
            customFields = parseCustomFields(cipher, key),
            metadataFields = parseMetadataFields(cipher),
            extraSections = listOfNotNull(
                secureNoteFields.takeIf { it.isNotEmpty() }?.let {
                    BitwardenSyncSnapshotFieldGroupPreview(title = "Secure Note", fields = it)
                }
            )
        )
    }

    private fun parseCardCipher(
        cipher: CipherApiResponse,
        key: SymmetricCryptoKey?
    ): BitwardenSyncSnapshotPreview {
        val card = cipher.card
        val cardFields = buildList {
            addIfPresent("Cardholder Name", decryptOrPlain(card?.cardholderName, key))
            addIfPresent("Brand", decryptOrPlain(card?.brand, key))
            addHiddenIfPresent("Number", decryptOrPlain(card?.number, key))
            addIfPresent("Exp Month", decryptOrPlain(card?.expMonth, key))
            addIfPresent("Exp Year", decryptOrPlain(card?.expYear, key))
            addHiddenIfPresent("Code", decryptOrPlain(card?.code, key))
        }

        return BitwardenSyncSnapshotPreview(
            status = readyOrLocked(key),
            cipherType = cipher.type,
            title = decryptOrPlain(cipher.name, key).orEmpty(),
            notes = decryptOrPlain(cipher.notes, key).orEmpty(),
            customFields = parseCustomFields(cipher, key),
            metadataFields = parseMetadataFields(cipher),
            extraSections = listOfNotNull(
                cardFields.takeIf { it.isNotEmpty() }?.let {
                    BitwardenSyncSnapshotFieldGroupPreview(title = "Card", fields = it)
                }
            )
        )
    }

    private fun parseIdentityCipher(
        cipher: CipherApiResponse,
        key: SymmetricCryptoKey?
    ): BitwardenSyncSnapshotPreview {
        val identity = cipher.identity
        val identityFields = buildList {
            addIfPresent("Title", decryptOrPlain(identity?.title, key))
            addIfPresent("First Name", decryptOrPlain(identity?.firstName, key))
            addIfPresent("Middle Name", decryptOrPlain(identity?.middleName, key))
            addIfPresent("Last Name", decryptOrPlain(identity?.lastName, key))
            addIfPresent("Address 1", decryptOrPlain(identity?.address1, key))
            addIfPresent("Address 2", decryptOrPlain(identity?.address2, key))
            addIfPresent("Address 3", decryptOrPlain(identity?.address3, key))
            addIfPresent("City", decryptOrPlain(identity?.city, key))
            addIfPresent("State", decryptOrPlain(identity?.state, key))
            addIfPresent("Postal Code", decryptOrPlain(identity?.postalCode, key))
            addIfPresent("Country", decryptOrPlain(identity?.country, key))
            addIfPresent("Company", decryptOrPlain(identity?.company, key))
            addIfPresent("Email", decryptOrPlain(identity?.email, key))
            addIfPresent("Phone", decryptOrPlain(identity?.phone, key))
            addIfPresent("Username", decryptOrPlain(identity?.username, key))
            addHiddenIfPresent("SSN", decryptOrPlain(identity?.ssn, key))
            addHiddenIfPresent("Passport Number", decryptOrPlain(identity?.passportNumber, key))
            addHiddenIfPresent("License Number", decryptOrPlain(identity?.licenseNumber, key))
        }

        return BitwardenSyncSnapshotPreview(
            status = readyOrLocked(key),
            cipherType = cipher.type,
            title = decryptOrPlain(cipher.name, key).orEmpty(),
            notes = decryptOrPlain(cipher.notes, key).orEmpty(),
            customFields = parseCustomFields(cipher, key),
            metadataFields = parseMetadataFields(cipher),
            extraSections = listOfNotNull(
                identityFields.takeIf { it.isNotEmpty() }?.let {
                    BitwardenSyncSnapshotFieldGroupPreview(title = "Identity", fields = it)
                }
            )
        )
    }

    private fun parseCustomFields(
        cipher: CipherApiResponse,
        key: SymmetricCryptoKey?
    ): List<BitwardenSyncSnapshotFieldPreview> {
        return cipher.fields.orEmpty().mapNotNull { field ->
            val name = decryptOrPlain(field.name, key).orEmpty().trim()
            if (name.isBlank()) return@mapNotNull null
            val linkedSuffix = field.linkedId?.let { " [linkedId=$it]" }.orEmpty()
            BitwardenSyncSnapshotFieldPreview(
                name = name + linkedSuffix,
                value = decryptOrPlain(field.value, key).orEmpty(),
                hidden = field.type == 1
            )
        }
    }

    private fun parseMetadataFields(cipher: CipherApiResponse): List<BitwardenSyncSnapshotFieldPreview> {
        return buildList {
            addIfPresent("Cipher ID", cipher.id)
            addIfPresent("Type", cipher.type.toString())
            addIfPresent("Organization ID", cipher.organizationId)
            addIfPresent("Folder ID", cipher.folderId)
            addIfPresent("Favorite", cipher.favorite.toString())
            addIfPresent("Reprompt", cipher.reprompt.toString())
            addIfPresent("Revision Date", cipher.revisionDate)
            addIfPresent("Creation Date", cipher.creationDate)
            addIfPresent("Archived Date", cipher.archivedDate)
            addIfPresent("Deleted Date", cipher.deletedDate)
        }
    }

    private fun decryptOrPlain(value: String?, key: SymmetricCryptoKey?): String? {
        if (value.isNullOrBlank()) return null
        val decrypted = decryptString(value, key)
        if (decrypted != null) return decrypted
        if (looksLikeCipherString(value)) return null
        return value
    }

    private fun decryptString(value: String?, key: SymmetricCryptoKey?): String? {
        if (value.isNullOrBlank()) return null
        if (key == null) return null
        if (!looksLikeCipherString(value)) return null
        return runCatching {
            BitwardenCrypto.decryptToString(value, key)
        }.getOrNull()
    }

    private fun looksLikeCipherString(value: String): Boolean {
        return cipherStringPattern.matches(value)
    }

    private fun readyOrLocked(key: SymmetricCryptoKey?): BitwardenSyncSnapshotPreviewStatus {
        return if (key == null) {
            BitwardenSyncSnapshotPreviewStatus.VAULT_LOCKED
        } else {
            BitwardenSyncSnapshotPreviewStatus.READY
        }
    }

    private fun MutableList<BitwardenSyncSnapshotFieldPreview>.addIfPresent(
        name: String,
        value: String?
    ) {
        value?.trim()?.takeIf { it.isNotBlank() }?.let {
            add(BitwardenSyncSnapshotFieldPreview(name = name, value = it))
        }
    }

    private fun MutableList<BitwardenSyncSnapshotFieldPreview>.addHiddenIfPresent(
        name: String,
        value: String?
    ) {
        value?.trim()?.takeIf { it.isNotBlank() }?.let {
            add(BitwardenSyncSnapshotFieldPreview(name = name, value = it, hidden = true))
        }
    }
}
