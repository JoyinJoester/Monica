package takagi.ru.monica.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricUnlockRegressionGuardTest {

    @Test
    fun mainPasswordLoginUsesFullVaultUnlockPath() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val authenticateBody = source.substringAfter("fun authenticate(password: String): Boolean {")
            .substringBefore("fun restoreAuthenticatedUiState()")

        assertTrue(
            "Main app password login must repair MDK/KeyStore state by using unlockVaultWithPassword.",
            authenticateBody.contains("securityManager.unlockVaultWithPassword(password)")
        )
        assertFalse(
            "Do not regress to verifyMasterPassword here; it can allow login while biometric key repair failed.",
            authenticateBody.contains("securityManager.verifyMasterPassword(password)")
        )
    }

    @Test
    fun mdkWrapperRebuildHandlesInvalidatedAndUnrecoverableKeystoreKeys() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val persistBody = source.substringAfter("private fun persistKeystoreWrappedMdk(mdk: ByteArray): Boolean {")
            .substringBefore("private fun persistCompatKeystoreWrappedMdk")
        val ensureBody = source.substringAfter("private fun ensureMdkInitializedWithPassword(")
            .substringBefore("private fun ensureMdkKeystoreWrapper()")

        assertTrue(
            "Wrapper rebuild must recover from biometric enrollment invalidating the secure key.",
            persistBody.contains("KeyPermanentlyInvalidatedException")
        )
        assertTrue(
            "Wrapper rebuild must recover from AndroidKeyStore returning an unrecoverable stale key.",
            persistBody.contains("UnrecoverableKeyException")
        )
        assertTrue(
            "Invalid secure aliases must be deleted so a fresh wrapper can be persisted.",
            persistBody.contains("deleteSecureKeyAlias(KEY_ALIAS_DATA)")
        )
        assertTrue(
            "Missing wrapper aliases must force a wrapper rebuild after password unlock.",
            ensureBody.contains("hasKeystoreBlob = false")
        )
        assertTrue(
            "Password unlock must refresh the keystore wrapper even when an old blob exists.",
            ensureBody.contains("compatibility wrapper refresh after password unlock")
        )
        assertTrue(
            "Password unlock recovery must not accidentally write a fresh auth-bound wrapper on devices with active biometric auth windows.",
            ensureBody.contains("persistCompatKeystoreWrappedMdk(actualMdk)")
        )
        assertTrue(
            "Password unlock must clear stale MDK auth cooldown after rebuilding key material.",
            ensureBody.contains("mdkAuthUnavailableUntilMillis = 0L")
        )
    }

    @Test
    fun biometricUnlockClearsPreviousMdkCooldownBeforeReadingKeyMaterial() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val biometricBody = source.substringAfter("fun unlockVaultWithBiometric(): Boolean {")
            .substringBefore("fun isVaultRuntimeUnlocked()")

        assertTrue(
            "A previous failed MDK read must not block a fresh successful biometric auth attempt.",
            biometricBody.contains("mdkAuthUnavailableUntilMillis = 0L")
        )
    }

    @Test
    fun emptyRuntimeMdkCacheCannotMaskReadableKeystoreWrapper() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val getMdkBody = source.substringAfter("private fun getMdkForCrypto(): ByteArray? {")
            .substringBefore("private val DATA_PREFIX_MDK")
        val getOrCreateBody = source.substringAfter("private fun getOrCreateMdkBytes(): ByteArray {")
            .substringBefore("private fun getMdkForCrypto()")
        val ensureBody = source.substringAfter("private fun ensureMdkInitializedWithPassword(")
            .substringBefore("private fun ensureMdkKeystoreWrapper()")

        assertTrue(
            "An empty runtime MDK cache must be ignored so a freshly persisted wrapper can still be read.",
            getMdkBody.contains("cached.isNotEmpty()")
        )
        assertTrue(
            "Clearing an empty runtime MDK cache prevents repeated false locked states.",
            getMdkBody.contains("processCachedMdk = null")
        )
        assertTrue(
            "MDK recovery should also ignore empty runtime cache before falling back to the keystore wrapper.",
            getOrCreateBody.contains("cached.isNotEmpty()")
        )
        assertTrue(
            "Correct-password unlock must repair historical empty password-wrapped MDK blobs.",
            ensureBody.contains("password-wrapped MDK is empty; attempting recovery")
        )
        assertTrue(
            "Recovered or freshly generated MDK must be written back to the password blob.",
            ensureBody.contains("shouldRewritePasswordBlob = true")
        )
    }

    @Test
    fun compatDataEncryptionDoesNotUsePredictableMasterKeyString() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val encryptDataBody = source.substringAfter("fun encryptData(data: String): String {")
            .substringBefore("/**\n     * Compatibility helper")
        val legacyCompatBody = source.substringAfter("fun encryptDataLegacyCompat(data: String): String {")
            .substringBefore("private fun encryptDataV2")
        val compatEncryptBody = source.substringAfter("private fun encryptDataCompat(data: String): String {")
            .substringBefore("fun decryptData(")
        val legacyReadBody = source.substringAfter("private fun decryptLegacyV1OrPlainText(encryptedData: String): String {")
            .substringBefore("/**\n     * Generate secure random password")

        assertTrue(
            "Compat data encryption must have an explicit prefix so callers can treat it as ciphertext.",
            source.contains("private val DATA_PREFIX_COMPAT = \"C2|\"") &&
                compatEncryptBody.contains("return DATA_PREFIX_COMPAT +")
        )
        assertTrue(
            "MDK fallback and legacy-compat callers must write the compat Keystore format, not historical V1.",
            encryptDataBody.contains("return encryptDataCompat(data)") &&
                legacyCompatBody.contains("return encryptDataCompat(data)")
        )
        assertTrue(
            "Compat encryption must use the real Android Keystore compat key.",
            compatEncryptBody.contains("getOrGenerateCompatSecureKey()")
        )
        assertFalse(
            "New encryption paths must never derive keys from MasterKey.toString().",
            encryptDataBody.contains("masterKey.toString()") ||
                legacyCompatBody.contains("masterKey.toString()") ||
                compatEncryptBody.contains("masterKey.toString()")
        )
        assertTrue(
            "Historical unprefixed V1 reading can keep MasterKey.toString only for old-data compatibility.",
            legacyReadBody.contains("masterKey.toString()") &&
                legacyReadBody.contains("Keep it read-only so old local data can be opened and migrated")
        )
    }

    @Test
    fun compatDataPrefixIsRecognizedAtPasswordBoundaries() {
        val autofillSecretResolver = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillSecretResolver.kt"
        ).readText()
        val accountFillPolicy = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AccountFillPolicy.kt"
        ).readText()
        val dataExportImportViewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/DataExportImportViewModel.kt"
        ).readText()
        val bitwardenSyncService = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/service/BitwardenSyncService.kt"
        ).readText()
        val webDavHelper = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt"
        ).readText()

        assertTrue(
            "C2 compat ciphertext must be recognized anywhere password payloads are classified.",
            autofillSecretResolver.contains("DATA_PREFIX_COMPAT = \"C2|\"") &&
                accountFillPolicy.contains("value.startsWith(\"C2|\")") &&
                dataExportImportViewModel.contains("trimmed.startsWith(\"C2|\")") &&
                bitwardenSyncService.contains("candidate.startsWith(\"C2|\")") &&
                webDavHelper.contains("trimmed.startsWith(\"C2|\")")
        )
        assertTrue(
            "Autofill account identifiers should try decrypting all known encrypted payload formats, including C2.",
            accountFillPolicy.contains("if (looksEncryptedPayload(entry.username))")
        )
    }

    @Test
    fun monicaCiphertextDetectionRequiresExplicitPrefix() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val detectorBody = source.substringAfter("fun looksLikeMonicaCiphertext(value: String): Boolean {")
            .substringBefore("fun decryptDataIfMonicaCiphertext")
        val conditionalDecryptBody = source.substringAfter("fun decryptDataIfMonicaCiphertext(value: String): String {")
            .substringBefore("private fun encryptDataV2")

        assertTrue(
            "Field-level migration must only treat explicitly-prefixed Monica values as ciphertext, so legacy JSON/Base32/otpauth data stays readable.",
            detectorBody.contains("trimmed.startsWith(DATA_PREFIX_MDK)") &&
                detectorBody.contains("trimmed.startsWith(DATA_PREFIX_V2)") &&
                detectorBody.contains("trimmed.startsWith(DATA_PREFIX_COMPAT)")
        )
        assertFalse(
            "Do not classify random Base64-looking legacy values as encrypted field data.",
            detectorBody.contains("Base64.decode") ||
                detectorBody.contains("contains(\"==\")")
        )
        assertTrue(
            "Conditional field decrypt must leave legacy plaintext unchanged.",
            conditionalDecryptBody.contains("looksLikeMonicaCiphertext(value)") &&
                conditionalDecryptBody.contains("decryptData(value)") &&
            conditionalDecryptBody.contains("else {\n            value\n        }")
        )
    }

    @Test
    fun totpFieldEncryptionCompatibilityReadsThroughSingleHelper() {
        val resolverSource = projectFile(
            "app/src/main/java/takagi/ru/monica/util/TotpDataResolver.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt"
        ).readText()

        assertTrue(
            "TOTP resolver must support optional field decrypt before parsing so old plaintext and new ciphertext share one path.",
            resolverSource.contains("decryptIfNeeded: ((String) -> String)? = null") &&
                resolverSource.contains("val resolvedItemData = decryptIfNeeded") &&
                resolverSource.contains("json.decodeFromString<TotpData>(resolvedItemData)") &&
                resolverSource.contains("rawKey = resolvedItemData")
        )
        assertTrue(
            "TotpViewModel must decrypt only known Monica ciphertext and preserve the original storage shape when rewriting.",
            viewModelSource.contains("decryptDataIfMonicaCiphertext(value)") &&
                viewModelSource.contains("looksLikeMonicaCiphertext(value)") &&
                viewModelSource.contains("encodeStoredSensitiveValueForRewrite") &&
                viewModelSource.contains("securityManager?.encryptDataLegacyCompat(plainValue)")
        )
        assertFalse(
            "TotpViewModel must not parse item.itemData directly; direct parsing would hide encrypted legacy TOTP data from old users after migration.",
            viewModelSource.contains("Json.decodeFromString<TotpData>(item.itemData)") ||
                viewModelSource.contains("Json.decodeFromString<TotpData>(target.itemData)") ||
            viewModelSource.contains("Json.decodeFromString<TotpData>(candidate.itemData)")
        )
    }

    @Test
    fun sharedDatabaseAndSyncBoundariesDoNotExportLocalOnlyTotpCiphertext() {
        val mdbxStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val bitwardenSyncSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/service/BitwardenSyncService.kt"
        ).readText()
        val webDavHelperSource = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt"
        ).readText()
        val keePassKdbxSource = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/KeePassKdbxService.kt"
        ).readText()

        assertTrue(
            "MDBX write boundary must preserve current plaintext data, but decrypt explicitly-prefixed Monica ciphertext before writing shared payloads.",
            mdbxStoreSource.contains("\"authenticator_key\",") &&
                mdbxStoreSource.contains("fieldName = \"authenticator_key\"") &&
                mdbxStoreSource.contains("\"item_data\",") &&
                mdbxStoreSource.contains("fieldName = \"item_data\"") &&
                mdbxStoreSource.contains("!securityManager.looksLikeMonicaCiphertext(value)") &&
                mdbxStoreSource.contains("securityManager.decryptData(value)") &&
                mdbxStoreSource.contains("Cannot write encrypted")
        )
        assertTrue(
            "Bitwarden upload and pending reconciliation must compare/upload the real TOTP payload, not a local Room ciphertext wrapper.",
            bitwardenSyncSource.contains("private fun resolvePlainStoredSensitiveValueForBitwardenUpload(") &&
                bitwardenSyncSource.contains("securityManager.looksLikeMonicaCiphertext(storedValue)") &&
                bitwardenSyncSource.contains("securityManager.decryptData(storedValue)") &&
                bitwardenSyncSource.contains("val plainAuthenticatorKey = resolvePlainStoredSensitiveValueForBitwardenUpload(") &&
                bitwardenSyncSource.contains("rawKey = plainAuthenticatorKey") &&
                bitwardenSyncSource.contains("val localAuthenticatorKey = resolvePlainStoredSensitiveValueForBitwardenUpload(") &&
                bitwardenSyncSource.contains("localAuthenticatorKey == remoteTotp")
        )
        assertTrue(
            "ZIP/WebDAV/OneDrive backup must export portable TOTP values instead of device-local Room ciphertext.",
            webDavHelperSource.contains("portablePasswordForBackup(") &&
                webDavHelperSource.contains("portableSecureItemForBackup(") &&
                webDavHelperSource.contains("portableSensitiveBackupValue(") &&
                webDavHelperSource.contains("securityManager.decryptData(value)") &&
                webDavHelperSource.contains(".map { portablePasswordForBackup(it, securityManager) }") &&
                webDavHelperSource.contains(".map { portableSecureItemForBackup(it, securityManager) }")
        )
        assertTrue(
            "KeePass write boundary must decrypt Monica field ciphertext before storing MonicaItemData in KDBX.",
            keePassKdbxSource.contains("portableSecureItemDataForKeePass(") &&
                keePassKdbxSource.contains("securityManager.decryptData(itemData)") &&
                keePassKdbxSource.contains("FIELD_MONICA_ITEM_DATA to EntryValue.Encrypted(EncryptedValue.fromString(portableItemData))")
        )
    }

    @Test
    fun totpNewWritesStoreLocalCiphertextWithoutBreakingPortableBoundaries() {
        val totpViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt"
        ).readText()
        val passwordViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val bitwardenSyncSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/service/BitwardenSyncService.kt"
        ).readText()
        val cipherSyncSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/service/CipherSyncProcessor.kt"
        ).readText()

        assertTrue(
            "Standalone TOTP saves must encrypt new Room itemData writes, while keeping plaintext JSON only as the portable business value.",
            totpViewModelSource.contains("private fun encodeStoredSensitiveValueForNewWrite(plainValue: String): String") &&
                totpViewModelSource.contains("securityManager?.encryptDataLegacyCompat(plainValue)") &&
                totpViewModelSource.contains("val storedItemData = encodeStoredSensitiveValueForNewWrite(itemDataJson)") &&
                totpViewModelSource.contains("itemData = storedItemData")
        )
        assertTrue(
            "Password-bound TOTP saves must encrypt PasswordEntry.authenticatorKey before storing it locally.",
            passwordViewModelSource.contains("private fun encodeAuthenticatorKeyForStorage(value: String): String") &&
                passwordViewModelSource.contains("authenticatorKey = encodeAuthenticatorKeyForStorage(entryToUpdate.authenticatorKey)") &&
                passwordViewModelSource.contains("authenticatorKey = encodeAuthenticatorKeyForStorage(normalizedBoundEntry.authenticatorKey)") &&
                passwordViewModelSource.contains("repository.updateAuthenticatorKey(id, encodeAuthenticatorKeyForStorage(authenticatorKey))")
        )
        assertTrue(
            "Bitwarden sync imports must store TOTP locally encrypted, while upload paths still decrypt before sending.",
            bitwardenSyncSource.contains("private fun encodeBitwardenTotpForLocalStorage(totpPayload: String): String") &&
                bitwardenSyncSource.contains("authenticatorKey = encodeBitwardenTotpForLocalStorage(totp)") &&
                bitwardenSyncSource.contains("val storedTotp = remoteTotp?.let(::encodeBitwardenTotpForLocalStorage) ?: entry.authenticatorKey") &&
                cipherSyncSource.contains("private fun encodeBitwardenTotpForLocalStorage(totpPayload: String): String") &&
                cipherSyncSource.contains("val storedTotp = encodeBitwardenTotpForLocalStorage(totp)") &&
                cipherSyncSource.contains("authenticatorKey = storedTotp") &&
                cipherSyncSource.contains("securityManager.encryptDataLegacyCompat(json.encodeToString(totpData))") &&
                cipherSyncSource.contains("decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext")
        )
    }

    @Test
    fun bankCardAndDocumentItemDataUseEncryptedLocalStorageWithPortableBoundaries() {
        val codecSource = projectFile(
            "app/src/main/java/takagi/ru/monica/data/model/CardWalletDataCodec.kt"
        ).readText()
        val bankCardViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt"
        ).readText()
        val documentViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt"
        ).readText()
        val cipherSyncSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/service/CipherSyncProcessor.kt"
        ).readText()
        val cipherUploadSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/service/CipherUploadProcessor.kt"
        ).readText()
        val webDavHelperSource = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt"
        ).readText()
        val autofillStructuredSource = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillStructuredDataSupport.kt"
        ).readText()
        val mdbxViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()

        assertTrue(
            "Card wallet codec must support conditional field decrypt before parsing old plaintext or new ciphertext.",
            codecSource.contains("decryptIfNeeded: ((String) -> String)? = null") &&
                codecSource.contains("resolveStoredData(raw, decryptIfNeeded)") &&
                codecSource.contains("parseLegacyBankCardData(resolvedRaw)") &&
                codecSource.contains("parseLegacyDocumentData(resolvedRaw)")
        )
        assertTrue(
            "Bank card and document ViewModels must encrypt new local itemData writes and decrypt when reading for UI/edit.",
            bankCardViewModelSource.contains("private fun encodeCardDataForLocalStorage(cardData: BankCardData): String") &&
                bankCardViewModelSource.contains("securityManager?.encryptDataLegacyCompat(plainValue)") &&
                bankCardViewModelSource.contains("decryptIfNeeded = ::decryptStoredSensitiveValue") &&
                documentViewModelSource.contains("private fun encodeDocumentDataForLocalStorage(documentData: DocumentData): String") &&
                documentViewModelSource.contains("securityManager?.encryptDataLegacyCompat(plainValue)") &&
                documentViewModelSource.contains("decryptIfNeeded = ::decryptStoredSensitiveValue")
        )
        assertTrue(
            "Bitwarden download must store card/document itemData locally encrypted, while upload decrypts before building remote payloads.",
            cipherSyncSource.contains("private fun encodeSecureItemDataForLocalStorage(itemData: String): String") &&
                cipherSyncSource.contains("CardWalletDataCodec.encodeBankCardData(cardData)") &&
                cipherSyncSource.contains("CardWalletDataCodec.encodeDocumentData(docData)") &&
                cipherUploadSource.contains("decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext")
        )
        assertTrue(
            "Backup and autofill boundaries must parse/export portable card/document business data, not local Room ciphertext.",
            webDavHelperSource.contains("item.itemType != ItemType.BANK_CARD") &&
                webDavHelperSource.contains("item.itemType != ItemType.DOCUMENT") &&
                autofillStructuredSource.contains("decryptIfNeeded: ((String) -> String)? = null") &&
                autofillStructuredSource.contains("CardWalletDataCodec.parseBankCardData(") &&
                autofillStructuredSource.contains("CardWalletDataCodec.parseDocumentData(")
        )
        assertTrue(
            "MDBX import must wrap portable TOTP/card/document payloads in local ciphertext when caching them in Room.",
            mdbxViewModelSource.contains("private fun encodeMdbxSensitiveValueForLocalStorage(") &&
                mdbxViewModelSource.contains("itemType != ItemType.BANK_CARD") &&
                mdbxViewModelSource.contains("itemType != ItemType.DOCUMENT") &&
                mdbxViewModelSource.contains("authenticatorKey = encodeMdbxSensitiveValueForLocalStorage(") &&
                mdbxViewModelSource.contains("itemData = storedItemData")
        )
    }

    @Test
    fun highTrafficTotpReadSurfacesUseEncryptedFieldCompatibleResolver() {
        val files = listOf(
            "app/src/main/java/takagi/ru/monica/ui/components/TotpCodeCard.kt",
            "app/src/main/java/takagi/ru/monica/service/NotificationValidatorService.kt",
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillPickerActivityV2.kt",
            "app/src/main/java/takagi/ru/monica/ime/MonicaInputMethodService.kt",
            "app/src/main/java/takagi/ru/monica/ui/components/QrCodeDialog.kt",
            "app/src/main/java/takagi/ru/monica/ui/AuthenticatorTabPane.kt",
            "app/src/main/java/takagi/ru/monica/ui/totp/TotpListContent.kt",
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordBatchMoveMixedSupport.kt",
            "app/src/main/java/takagi/ru/monica/ui/screens/KeePassKdbxViewModel.kt",
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
        )

        files.forEach { relativePath ->
            val source = projectFile(relativePath).readText()
            assertFalse(
                "$relativePath must not directly decode SecureItem.itemData as plaintext TOTP; encrypted fields must pass through TotpDataResolver.",
                source.contains("Json.decodeFromString<TotpData>") ||
                    source.contains("decodeFromString<TotpData>") ||
                    source.contains("decodeFromString(TotpData.serializer(), item.itemData)")
            )
        }

        val combinedSource = files.joinToString("\n") { projectFile(it).readText() }
        assertTrue(
            "High-traffic TOTP surfaces should explicitly route stored itemData through the compatibility resolver.",
            combinedSource.contains("TotpDataResolver.parseStoredItemData") &&
                combinedSource.contains("decryptDataIfMonicaCiphertext")
        )
    }

    @Test
    fun productionTotpItemDataParsingIsCentralizedInResolver() {
        val mainSourceDir = projectPath("app/src/main/java/takagi/ru/monica")
        val offenders = mainSourceDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.endsWith("util/TotpDataResolver.kt") }
            .mapNotNull { file ->
                val source = file.readText()
                val directlyParsesTotp =
                    source.contains("Json.decodeFromString<TotpData>") ||
                        source.contains("json.decodeFromString<TotpData>") ||
                        source.contains("decodeFromString<TotpData>") ||
                        source.contains("decodeFromString(TotpData.serializer(), item.itemData)")
                if (directlyParsesTotp) file.relativeTo(mainSourceDir).invariantSeparatorsPath else null
            }
            .toList()

        assertTrue(
            "Stored TOTP parsing must stay centralized so plaintext, legacy V1, MDK/V2 and C2 field formats remain compatible. Offenders: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun legacySensitiveFieldMigrationRunsAfterUnlockAndOnlyTouchesLocalRoomCache() {
        val migrationSource = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SensitiveFieldMigrationManager.kt"
        ).readText()
        val mainActivitySource = projectFile(
            "app/src/main/java/takagi/ru/monica/MainActivity.kt"
        ).readText()
        val totpViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt"
        ).readText()
        val securityManagerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val passwordDaoSource = projectFile(
            "app/src/main/java/takagi/ru/monica/data/PasswordEntryDao.kt"
        ).readText()
        val secureItemDaoSource = projectFile(
            "app/src/main/java/takagi/ru/monica/data/SecureItemDao.kt"
        ).readText()

        assertTrue(
            "Legacy sensitive-field migration must keep resumable progress outside Room schema migrations.",
            migrationSource.contains("getSharedPreferences(PREFS_NAME") &&
                migrationSource.contains("KEY_VERSION") &&
                migrationSource.contains("lastIdKey(domain)") &&
                migrationSource.contains("completedKey(domain)") &&
                migrationSource.contains("failureCountKey(domain)") &&
                migrationSource.contains("blockedIdKey(domain)")
        )
        assertTrue(
            "Migration must be gated by usable unlocked key material and run from the authenticated main-app path.",
            migrationSource.contains("securityManager.canAccessVaultMaterialNow()") &&
                mainActivitySource.contains("LaunchedEffect(isAuthenticated)") &&
                mainActivitySource.contains("if (isAuthenticated)") &&
                mainActivitySource.contains("SensitiveFieldMigrationManager(") &&
                mainActivitySource.contains("withContext(Dispatchers.IO)") &&
                mainActivitySource.contains("delay(15_000)") &&
                mainActivitySource.contains("runUnlockedSmallBatch()")
        )
        assertTrue(
            "Migration must stay small-batch so it cannot block startup or lock the UI.",
            migrationSource.contains("private const val BATCH_SIZE = 3") &&
                migrationSource.contains("private const val MAX_BATCHES_PER_DOMAIN = 1") &&
                passwordDaoSource.contains("getAuthenticatorKeyMigrationBatch") &&
                secureItemDaoSource.contains("getItemDataMigrationBatch")
        )
        assertTrue(
            "Migration must only rewrite confirmed plaintext legacy values into C2/MDK-compatible local ciphertext.",
            migrationSource.contains("securityManager.looksLikeMonicaCiphertext(rawValue)") &&
                migrationSource.contains("securityManager.encryptDataLegacyCompat(plainValue)") &&
                migrationSource.contains("val roundTrip = securityManager.decryptData(encrypted)") &&
                migrationSource.contains("check(roundTrip == plainValue)") &&
                migrationSource.contains("TotpDataResolver.parseStoredItemData") &&
                migrationSource.contains("TotpDataResolver.fromAuthenticatorKey") &&
                migrationSource.contains("CardWalletDataCodec.parseBankCardData") &&
                migrationSource.contains("CardWalletDataCodec.parseDocumentData")
        )
        assertTrue(
            "Migration must use DAO column updates instead of repositories, so MDBX/Bitwarden/KeePass/autofill chains do not see a user edit.",
            migrationSource.contains("passwordEntryDao.updateAuthenticatorKey") &&
                migrationSource.contains("secureItemDao.updateItemData") &&
                passwordDaoSource.contains("UPDATE password_entries SET authenticatorKey = :authenticatorKey WHERE id = :id") &&
                secureItemDaoSource.contains("UPDATE secure_items SET itemData = :itemData WHERE id = :id")
        )
        assertFalse(
            "The migration manager must not depend on sync repositories or shared database writers.",
            migrationSource.contains("PasswordRepository") ||
                migrationSource.contains("SecureItemRepository") ||
                migrationSource.contains("MdbxVaultStore") ||
                migrationSource.contains("BitwardenSyncService") ||
                migrationSource.contains("KeePassKdbxService") ||
                migrationSource.contains("WebDavHelper")
        )
        assertTrue(
            "TOTP list merging and filtering must not decrypt migrated C2 fields on the main thread.",
            totpViewModelSource.contains("import kotlinx.coroutines.Dispatchers") &&
                totpViewModelSource.contains("}.flowOn(Dispatchers.Default)") &&
                totpViewModelSource.contains("val allTotpItems: StateFlow<List<SecureItem>> = allTotpItemsSource.stateIn")
        )
        assertTrue(
            "Opening the TOTP editor must use the compatibility parser; direct JSON decode loses migrated encrypted fields.",
            mainActivitySource.contains("initialData = totpViewModel.parseTotpDataForDisplay(item)") &&
                !mainActivitySource.contains("Json.decodeFromString(item.itemData)")
        )
        assertTrue(
            "C2 decrypt paths are hot after migration; the compat AndroidKeyStore key must be cached per SecurityManager instance.",
            securityManagerSource.contains("private var cachedCompatDataKey: SecretKey? = null") &&
                securityManagerSource.contains("private val compatDataKeyLock = Any()") &&
                securityManagerSource.contains("cachedCompatDataKey?.let { return it }") &&
                securityManagerSource.contains("cachedCompatDataKey = it")
        )
    }

    @Test
    fun pageSwitchHotPathsDoNotRunAuthOrBitwardenSyncWorkOnMainThread() {
        val mainActivitySource = projectFile(
            "app/src/main/java/takagi/ru/monica/MainActivity.kt"
        ).readText()
        val monicaContentBody = mainActivitySource
            .substringAfter("fun MonicaContent(")
            .substringBefore("DisposableEffect(lifecycleOwner)")
        val bitwardenOrchestratorSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/sync/BitwardenSyncOrchestrator.kt"
        ).readText()
        val bitwardenViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/viewmodel/BitwardenViewModel.kt"
        ).readText()
        val cardWalletScreenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/CardWalletScreen.kt"
        ).readText()
        val passkeyListScreenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PasskeyListScreen.kt"
        ).readText()
        val sendScreenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SendScreen.kt"
        ).readText()
        val securityManagerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val totpCodeCardSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/TotpCodeCard.kt"
        ).readText()
        val bankCardCardSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/BankCardCard.kt"
        ).readText()
        val documentCardSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/DocumentCard.kt"
        ).readText()
        val bankCardViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt"
        ).readText()
        val documentViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt"
        ).readText()
        val totpViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt"
        ).readText()
        val passwordViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val totpListContentSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/totp/TotpListContent.kt"
        ).readText()
        val authenticatorTabPaneSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/AuthenticatorTabPane.kt"
        ).readText()
        val passwordDetailScreenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PasswordDetailScreen.kt"
        ).readText()
        val addEditPasswordScreenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt"
        ).readText()
        val addEditBankCardScreenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditBankCardScreen.kt"
        ).readText()
        val addEditDocumentScreenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditDocumentScreen.kt"
        ).readText()
        val cardWalletDetailPaneContentSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/cardwallet/CardWalletDetailPaneContent.kt"
        ).readText()
        val commonNameSuggestionSheetSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/CommonNameSuggestionSheet.kt"
        ).readText()

        assertTrue(
            "Authenticated Compose recompositions must reuse an authenticated access state instead of re-running lock policy on every frame.",
            monicaContentBody.contains("val authenticatedAccessState = remember") &&
                monicaContentBody.contains("val mainAppAccessState = remember(") &&
                monicaContentBody.contains("if (isAuthenticated)") &&
                monicaContentBody.contains("authenticatedAccessState")
        )
        assertFalse(
            "Main app access state must not be resolved directly as a top-level composable value; that causes hot SecurityManager checks during page switches.",
            monicaContentBody.contains("val mainAppAccessState = MainAppLockPolicy.resolveAccessState(")
        )
        assertTrue(
            "Bitwarden sync execution must leave viewModelScope's Main dispatcher before network/database work starts.",
            bitwardenOrchestratorSource.contains("import kotlinx.coroutines.Dispatchers") &&
                bitwardenOrchestratorSource.contains("import kotlinx.coroutines.withContext") &&
                bitwardenOrchestratorSource.contains("withContext(Dispatchers.IO)") &&
                bitwardenOrchestratorSource.contains("executeSync(vaultId, silent)")
        )
        assertTrue(
            "Silent Bitwarden auto sync must not immediately warm every offline secret or refresh every UI snapshot during tab switching.",
            bitwardenViewModelSource.contains("private const val SILENT_SYNC_UI_REFRESH_DELAY_MS") &&
                bitwardenViewModelSource.contains("private const val SILENT_SYNC_CACHE_WARM_DELAY_MS") &&
                bitwardenViewModelSource.contains("val warmedCount = if (silent)") &&
                bitwardenViewModelSource.contains("scheduleSilentPostSyncRefresh(vault.id)") &&
                bitwardenViewModelSource.contains("scheduleSilentOfflineCacheWarm(vault.id)")
        )
        assertTrue(
            "Page-visible auto sync triggers should wait for a short stable visibility window so fast bottom-tab switches cancel them.",
                cardWalletScreenSource.contains("delay(1_200L)") &&
                cardWalletScreenSource.contains("SyncTaskRunner.request(") &&
                cardWalletScreenSource.contains("bitwardenViewModel?.requestPageEnterAutoSync(vaultId)") &&
                passkeyListScreenSource.contains("delay(1_200L)") &&
                passkeyListScreenSource.contains("viewModel.refreshKeePassPasskeys(trigger = \"PASSKEY_PAGE_ENTER\")") &&
                sendScreenSource.contains("delay(1_200L)") &&
                sendScreenSource.contains("bitwardenViewModel.requestPageEnterAutoSync()")
        )
        assertTrue(
            "Routine SecurityManager access checks must stay out of logcat hot paths; warning diagnostics remain separate.",
            securityManagerSource.contains("private fun logRoutineDebug(message: String)") &&
                securityManagerSource.contains("logRoutineDebug(\"canRestoreMainAppSession: session inactive -> locked\")") &&
                securityManagerSource.contains("logRoutineDebug(\"canRestoreMainAppSession: runtime MDK cache present\")") &&
                securityManagerSource.contains("logRoutineDebug(\"canAccessVaultNow: session inactive -> locked\")") &&
                securityManagerSource.contains("logRoutineDebug(\"canAccessVaultNow: runtime MDK cache present -> accessible\")")
        )
        assertFalse(
            "Routine SecurityManager success/locked checks must not spam android.util.Log during recomposition or page switches.",
            securityManagerSource.contains("android.util.Log.d(logTag, \"canRestoreMainAppSession: session inactive -> locked\")") ||
                securityManagerSource.contains("android.util.Log.d(logTag, \"canRestoreMainAppSession: runtime MDK cache present\")") ||
                securityManagerSource.contains("android.util.Log.d(logTag, \"canAccessVaultNow: session inactive -> locked\")") ||
                securityManagerSource.contains("android.util.Log.d(logTag, \"canAccessVaultNow: runtime MDK cache present -> accessible\")")
        )
        assertTrue(
            "Authenticator and card-wallet hot cards must consume parsed UI data so recomposition does not decrypt or parse encrypted payloads.",
                totpCodeCardSource.contains("parsedTotpData: TotpData? = null") &&
                totpListContentSource.contains("val parsedTotpItems by viewModel.parsedTotpItems.collectAsState()") &&
                totpListContentSource.contains("parsedTotpData = totpDataById[item.id]") &&
                authenticatorTabPaneSource.contains("val parsedTotpItems by totpViewModel.parsedTotpItems.collectAsState()") &&
                authenticatorTabPaneSource.contains("parsedTotpItems.firstOrNull { it.item.id == selectedTotpId }?.totpData") &&
                bankCardCardSource.contains("cardData: BankCardData? = null") &&
                documentCardSource.contains("documentData: DocumentData? = null") &&
                cardWalletScreenSource.contains("val parsedCards by bankCardViewModel.parsedCards.collectAsState") &&
                cardWalletScreenSource.contains("val parsedDocuments by documentViewModel.parsedDocuments.collectAsState") &&
                cardWalletScreenSource.contains("val parsedBillingAddresses by billingAddressViewModel.parsedBillingAddresses.collectAsState") &&
                cardWalletScreenSource.contains("parsedCards.map { it.item.toBankCardWalletListItem(it.cardData) }") &&
                cardWalletScreenSource.contains("parsedDocuments.map { it.item.toDocumentWalletListItem(it.documentData) }") &&
                cardWalletScreenSource.contains("cardData = walletItem.bankCardData") &&
                cardWalletScreenSource.contains("documentData = walletItem.documentData")
        )
        assertFalse(
            "Card composables must not construct SecurityManager; field decrypt belongs in ViewModel/repository state pipelines.",
            totpCodeCardSource.contains("SecurityManager(") ||
                bankCardCardSource.contains("SecurityManager(") ||
                documentCardSource.contains("SecurityManager(")
        )
        assertFalse(
            "Wide authenticator detail pane must not parse/decrypt encrypted TOTP payloads directly in Compose.",
            authenticatorTabPaneSource.contains("SecurityManager(") ||
                authenticatorTabPaneSource.contains("TotpDataResolver.parseStoredItemData")
        )
        assertTrue(
            "Card and authenticator ViewModels should prepare parsed models on a background dispatcher before UI rendering.",
            bankCardViewModelSource.contains("val parsedCards: StateFlow<List<ParsedBankCardItem>>") &&
                bankCardViewModelSource.contains("withContext(Dispatchers.Default)") &&
                documentViewModelSource.contains("val parsedDocuments: StateFlow<List<ParsedDocumentItem>>") &&
                documentViewModelSource.contains("withContext(Dispatchers.Default)") &&
                totpViewModelSource.contains("val parsedTotpItems: StateFlow<List<ParsedTotpItem>>") &&
                totpViewModelSource.contains("withContext(Dispatchers.Default)")
        )
        assertTrue(
            "Password detail must render the base entry before attachment reconcile, sibling grouping, password decrypt, and custom field loading finish.",
            passwordDetailScreenSource.indexOf("passwordEntry = entry") in 1 until
                passwordDetailScreenSource.indexOf("AttachmentContainer.keepassReconciler") &&
                passwordDetailScreenSource.contains("initialDetailDataLoaded = false") &&
                passwordDetailScreenSource.contains("if (!initialDetailDataLoaded) return@LaunchedEffect")
        )
        assertTrue(
            "Password detail linked TOTP should not regenerate the HMAC code every 100ms; only progress needs smooth ticking.",
            passwordDetailScreenSource.contains("var lastCodeSecond = Long.MIN_VALUE") &&
                passwordDetailScreenSource.contains("if (nowSecond != lastCodeSecond)") &&
                passwordDetailScreenSource.contains("TotpGenerator.generateOtp(totp)") &&
                passwordDetailScreenSource.contains("delay(if (settings.validatorSmoothProgress) 100 else 1_000)")
        )
        assertTrue(
            "Password detail linked TOTP lookup must parse encrypted authenticator items off the main thread.",
            passwordViewModelSource.contains("fun getLinkedTotpFlow(passwordId: Long): Flow<TotpData?>") &&
                passwordViewModelSource.contains("preferred?.second") &&
                passwordViewModelSource.contains("}.flowOn(Dispatchers.Default)")
        )
        assertTrue(
            "Password editor must not subscribe to the plaintext all-password stream just for SSO reference metadata.",
            addEditPasswordScreenSource.contains("val allPasswordsForRef by viewModel.allPasswordsForUi.collectAsState") &&
                !addEditPasswordScreenSource.contains("val allPasswordsForRef by viewModel.allPasswords.collectAsState")
        )
        assertTrue(
            "Password editor bank-card picker must reuse parsed card state instead of parsing encrypted card data in Compose.",
            addEditPasswordScreenSource.contains("val bankCards by bankCardViewModel.parsedCards.collectAsState") &&
                !addEditPasswordScreenSource.contains("bankCardViewModel.allCards.collectAsState") &&
                !addEditPasswordScreenSource.contains("bankCardViewModel.parseCardData(item.itemData)")
        )
        assertTrue(
            "Password editor load must move entry lookup, sibling scan, secret inspection, and custom fields off the main thread.",
            addEditPasswordScreenSource.contains("withContext(Dispatchers.IO) {\n                    viewModel.getRawPasswordEntryById(actualId)") &&
                addEditPasswordScreenSource.contains("withContext(Dispatchers.Default) {\n                        viewModel.inspectSecretState(rawEntry)") &&
                addEditPasswordScreenSource.contains("withContext(Dispatchers.IO) {\n                            viewModel.getRawActivePasswordEntries()") &&
                addEditPasswordScreenSource.contains("withContext(Dispatchers.IO) {\n                            viewModel.getCustomFieldsByEntryIdSync(actualId)")
        )
        assertTrue(
            "Card-wallet editors must parse encrypted itemData on a background dispatcher before filling form state.",
            addEditBankCardScreenSource.contains("withContext(Dispatchers.Default) {\n                    viewModel.parseCardData(item.itemData)") &&
                addEditDocumentScreenSource.contains("withContext(Dispatchers.Default) {\n                    viewModel.parseDocumentData(item.itemData)")
        )
        assertTrue(
            "Card-wallet editors must not show a blank edit form before encrypted fields have been parsed.",
            addEditBankCardScreenSource.contains("val isExistingCardReady = cardId == null || hasLoadedExistingCardFields") &&
                addEditBankCardScreenSource.contains("val canSave = isExistingCardReady && cardNumber.isNotBlank() && !isSaving") &&
                addEditBankCardScreenSource.contains("if (!isExistingCardReady)") &&
                addEditBankCardScreenSource.contains("BankCardEditLoadingPlaceholder(") &&
                addEditBankCardScreenSource.contains("withContext(Dispatchers.IO) {\n                viewModel.getCardById(cardId)") &&
                addEditDocumentScreenSource.contains("val isExistingDocumentReady = documentId == null || hasLoadedExistingDocumentFields") &&
                addEditDocumentScreenSource.contains("val canSave = isExistingDocumentReady && documentNumber.isNotBlank() && !isSaving") &&
                addEditDocumentScreenSource.contains("if (!isExistingDocumentReady)") &&
                addEditDocumentScreenSource.contains("DocumentEditLoadingPlaceholder(") &&
                addEditDocumentScreenSource.contains("withContext(Dispatchers.IO) {\n                viewModel.getDocumentById(documentId)")
        )
        assertTrue(
            "Card-wallet add/edit screens must not subscribe to full card/document replica lists while creating a new item.",
            addEditBankCardScreenSource.contains("if (cardId != null) viewModel.allCards else flowOf(emptyList())") &&
                addEditDocumentScreenSource.contains("if (documentId != null) viewModel.allDocuments else flowOf(emptyList())")
        )
        assertTrue(
            "Card-wallet common-name analysis must stay lazy so add-page navigation animations are not blocked by full SecureItem scans.",
            commonNameSuggestionSheetSource.contains("includeAnalyzedItems: Boolean = true") &&
                commonNameSuggestionSheetSource.contains("if (includeAnalyzedItems)") &&
                commonNameSuggestionSheetSource.contains("flowOf(emptyList())") &&
                addEditBankCardScreenSource.contains("includeAnalyzedItems = shouldLoadCommonNameAnalysis || showCommonNamePicker") &&
                addEditDocumentScreenSource.contains("includeAnalyzedItems = shouldLoadCommonNameAnalysis || showCommonNamePicker")
        )
        assertTrue(
            "Wide card-wallet detail pane should animate add/edit/detail changes instead of hard switching after heavy composition work.",
            cardWalletDetailPaneContentSource.contains("AnimatedContent(") &&
                cardWalletDetailPaneContentSource.contains("targetState = detailContent") &&
                cardWalletDetailPaneContentSource.contains("scaleIn(initialScale = 0.94f") &&
                cardWalletDetailPaneContentSource.contains("CardWalletDetailContent.BankCardAdd") &&
                cardWalletDetailPaneContentSource.contains("CardWalletDetailContent.DocumentAdd")
        )
    }

    @Test
    fun autofillPasswordSelectionReturnPathDoesNotBlockMainThread() {
        val pickerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillPickerActivityV2.kt"
        ).readText()
        val listItemSource = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/ui/PasswordListItem.kt"
        ).readText()
        val handleBody = pickerSource.substringAfter("private fun handleAutofill(password: PasswordEntry, forceAddUri: Boolean) {")
            .substringBefore("private suspend fun preparePasswordAutofill(")
        val prepareBody = pickerSource.substringAfter("private suspend fun preparePasswordAutofill(")
            .substringBefore("private fun handleGeneratedPasswordFill(")
        val otpActionBody = pickerSource.substringAfter("private suspend fun processSelectedOtpActions(password: PasswordEntry) {")
            .substringBefore("private suspend fun generateOtpCodeForPassword")

        assertFalse(
            "Autofill button confirmation must not decrypt passwords directly on the main-thread return path.",
            handleBody.contains("AutofillSecretResolver.decryptPasswordOrNull") ||
                handleBody.contains("AccountFillPolicy.resolveAccountIdentifier")
        )
        assertFalse(
            "Autofill button confirmation must not block on preference or database IO before returning to the target app.",
            handleBody.contains("runBlocking")
        )
        assertTrue(
            "Password autofill result preparation should happen on a background dispatcher and be warmed when the menu opens.",
            prepareBody.contains("withContext(Dispatchers.Default)") &&
                prepareBody.contains("AutofillSecretResolver.decryptPasswordOrNull") &&
                pickerSource.contains("private fun prewarmPasswordAutofill(password: PasswordEntry)") &&
                pickerSource.contains("passwordAutofillPreparation = lifecycleScope.async") &&
                pickerSource.contains("onPrepareAutofill = ::prewarmPasswordAutofill") &&
                listItemSource.contains("onPrepareAutofill?.invoke(password)")
        )
        assertTrue(
            "OTP copy/notification side effects should use suspending IO reads instead of runBlocking on the selection path.",
            otpActionBody.contains("withContext(Dispatchers.IO)") &&
                !otpActionBody.contains("runBlocking")
        )
        assertTrue(
            "Autofill picker initial load should use the password-list M3E loading indicator and move first database reads off the main thread.",
            pickerSource.contains("PasswordListInitialLoadingIndicator()") &&
                pickerSource.contains("val loadedData = withContext(Dispatchers.IO)") &&
                pickerSource.contains("AutofillPickerLoadedData(") &&
                !pickerSource.substringAfter("if (isLoading) {")
                    .substringBefore("} else {")
                    .contains("CircularProgressIndicator()")
        )
    }

    private fun projectFile(relativePath: String): File {
        val file = projectPath(relativePath)
        if (file.isFile) return file
        error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }

    private fun projectPath(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.exists() }
            ?: error("Unable to find project path: $relativePath from ${System.getProperty("user.dir")}")
    }
}
