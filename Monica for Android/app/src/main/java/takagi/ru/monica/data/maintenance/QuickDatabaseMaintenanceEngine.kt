package takagi.ru.monica.data.maintenance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.bitwarden.BitwardenVaultDao
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.repository.PasskeyRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import java.net.URI
import java.net.URLDecoder
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class QuickMaintenanceCategory {
    PASSWORDS,
    AUTHENTICATORS,
    BANK_CARDS,
    PASSKEYS
}

enum class QuickMaintenanceSourceKind {
    MONICA_LOCAL,
    KEEPASS,
    BITWARDEN
}

data class QuickMaintenanceSource(
    val key: String,
    val kind: QuickMaintenanceSourceKind,
    val label: String = "",
    val detail: String? = null
)

enum class QuickMaintenanceCategoryNote {
    PASSWORDS_NONE_FOUND,
    PASSWORDS_MATCHED_ONLY,
    AUTHENTICATORS_NONE_FOUND,
    AUTHENTICATORS_MATCHED_ONLY,
    BANK_CARDS_NONE_FOUND,
    BANK_CARDS_MATCHED_ONLY,
    PASSKEYS_SCAN_ONLY
}

data class QuickMaintenanceRequest(
    val categories: Set<QuickMaintenanceCategory>
)

data class QuickMaintenanceCategoryResult(
    val category: QuickMaintenanceCategory,
    val matchedGroups: Int = 0,
    val updatedEntries: Int = 0,
    val createdEntries: Int = 0,
    val skippedGroups: Int = 0,
    val note: QuickMaintenanceCategoryNote? = null
)

data class QuickMaintenanceResult(
    val categoryResults: List<QuickMaintenanceCategoryResult>,
    val sources: List<QuickMaintenanceSource>,
    val sourceStats: List<QuickMaintenanceSourceStats>,
    val sourceDiffs: List<QuickMaintenanceSourceDiff>,
    val totalMatchedGroups: Int,
    val totalUpdatedEntries: Int,
    val totalCreatedEntries: Int,
    val totalSkippedGroups: Int
)

data class QuickMaintenanceSourceStats(
    val sourceKey: String,
    val passwordCount: Int,
    val authenticatorCount: Int,
    val bankCardCount: Int,
    val passkeyCount: Int
) {
    val totalCount: Int
        get() = passwordCount + authenticatorCount + bankCardCount + passkeyCount
}

data class QuickMaintenanceDiffItem(
    val category: QuickMaintenanceCategory,
    val title: String,
    val missingSourceKeys: List<String>
)

data class QuickMaintenanceSourceDiff(
    val sourceKey: String,
    val extraItems: List<QuickMaintenanceDiffItem>
)

class QuickDatabaseMaintenanceEngine(
    private val passwordRepository: PasswordRepository,
    private val secureItemRepository: SecureItemRepository,
    private val passkeyRepository: PasskeyRepository,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao,
    private val bitwardenVaultDao: BitwardenVaultDao,
    private val securityManager: SecurityManager,
    private val keePassBridge: KeePassCompatibilityBridge
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadSources(): List<QuickMaintenanceSource> = withContext(Dispatchers.IO) {
        val keepassDatabases = localKeePassDatabaseDao.getAllDatabasesSync()
        val vaults = bitwardenVaultDao.getAllVaults()
            .filter { it.syncEnabled }

        buildList {
            add(
                QuickMaintenanceSource(
                    key = MONICA_SOURCE_KEY,
                    kind = QuickMaintenanceSourceKind.MONICA_LOCAL
                )
            )
            keepassDatabases.forEach { database ->
                add(
                    QuickMaintenanceSource(
                        key = keepassSourceKey(database.id),
                        kind = QuickMaintenanceSourceKind.KEEPASS,
                        label = database.name
                    )
                )
            }
            vaults.forEach { vault ->
                add(
                    QuickMaintenanceSource(
                        key = bitwardenSourceKey(vault.id),
                        kind = QuickMaintenanceSourceKind.BITWARDEN,
                        label = vaultLabel(vault)
                    )
                )
            }
        }
    }

    suspend fun loadSourceStats(sources: List<QuickMaintenanceSource>): List<QuickMaintenanceSourceStats> =
        withContext(Dispatchers.IO) {
            buildSourceStats(sources)
        }

    suspend fun run(request: QuickMaintenanceRequest): QuickMaintenanceResult = withContext(Dispatchers.IO) {
        val sources = loadSources()
        val keepassDatabases = localKeePassDatabaseDao.getAllDatabasesSync()
        val vaults = bitwardenVaultDao.getAllVaults().filter { it.syncEnabled }
        val sourceStats = buildSourceStats(sources)
        val sourceDiffs = buildSourceDiffs(request.categories, sources)
        val categoryResults = buildList {
            if (QuickMaintenanceCategory.PASSWORDS in request.categories) {
                add(syncPasswords(keepassDatabases, vaults))
            }
            if (QuickMaintenanceCategory.AUTHENTICATORS in request.categories) {
                add(syncSecureItems(ItemType.TOTP, QuickMaintenanceCategory.AUTHENTICATORS, keepassDatabases, vaults))
            }
            if (QuickMaintenanceCategory.BANK_CARDS in request.categories) {
                val cards = syncSecureItems(ItemType.BANK_CARD, QuickMaintenanceCategory.BANK_CARDS, keepassDatabases, vaults)
                val documents = syncSecureItems(ItemType.DOCUMENT, QuickMaintenanceCategory.BANK_CARDS, keepassDatabases, vaults)
                add(
                    QuickMaintenanceCategoryResult(
                        category = QuickMaintenanceCategory.BANK_CARDS,
                        matchedGroups = cards.matchedGroups + documents.matchedGroups,
                        updatedEntries = cards.updatedEntries + documents.updatedEntries,
                        createdEntries = cards.createdEntries + documents.createdEntries,
                        skippedGroups = cards.skippedGroups + documents.skippedGroups,
                        note = if ((cards.matchedGroups + documents.matchedGroups) == 0) {
                            QuickMaintenanceCategoryNote.BANK_CARDS_NONE_FOUND
                        } else {
                            QuickMaintenanceCategoryNote.BANK_CARDS_MATCHED_ONLY
                        }
                    )
                )
            }
            if (QuickMaintenanceCategory.PASSKEYS in request.categories) {
                add(scanPasskeys())
            }
        }

        QuickMaintenanceResult(
            categoryResults = categoryResults,
            sources = sources,
            sourceStats = sourceStats,
            sourceDiffs = sourceDiffs,
            totalMatchedGroups = categoryResults.sumOf { it.matchedGroups },
            totalUpdatedEntries = categoryResults.sumOf { it.updatedEntries },
            totalCreatedEntries = categoryResults.sumOf { it.createdEntries },
            totalSkippedGroups = categoryResults.sumOf { it.skippedGroups }
        )
    }

    private suspend fun buildSourceStats(
        sources: List<QuickMaintenanceSource>
    ): List<QuickMaintenanceSourceStats> {
        if (sources.isEmpty()) return emptyList()
        val counters = sources.associate { source ->
            source.key to MutableSourceStats()
        }.toMutableMap()

        passwordRepository.getAllPasswordEntries().first()
            .filter { !it.isDeleted && !it.isArchived }
            .forEach { entry ->
                counters.getOrPut(sourceKeyOf(entry)) { MutableSourceStats() }.passwordCount += 1
            }

        secureItemRepository.getAllItems().first()
            .filter { !it.isDeleted }
            .forEach { item ->
                val counter = counters.getOrPut(sourceKeyOf(item)) { MutableSourceStats() }
                when (item.itemType) {
                    ItemType.TOTP -> counter.authenticatorCount += 1
                    ItemType.BANK_CARD,
                    ItemType.DOCUMENT -> counter.bankCardCount += 1
                    else -> Unit
                }
            }

        passkeyRepository.getAllPasskeysSync()
            .forEach { passkey ->
                counters.getOrPut(sourceKeyOf(passkey)) { MutableSourceStats() }.passkeyCount += 1
            }

        return sources.map { source ->
            val count = counters[source.key] ?: MutableSourceStats()
            QuickMaintenanceSourceStats(
                sourceKey = source.key,
                passwordCount = count.passwordCount,
                authenticatorCount = count.authenticatorCount,
                bankCardCount = count.bankCardCount,
                passkeyCount = count.passkeyCount
            )
        }.sortedByDescending { it.totalCount }
    }

    private suspend fun buildSourceDiffs(
        categories: Set<QuickMaintenanceCategory>,
        sources: List<QuickMaintenanceSource>
    ): List<QuickMaintenanceSourceDiff> {
        val sourceKeys = sources.map { it.key }.toSet()
        if (sourceKeys.isEmpty()) return emptyList()

        val extrasBySource = linkedMapOf<String, MutableList<QuickMaintenanceDiffItem>>()

        fun appendDiff(
            category: QuickMaintenanceCategory,
            title: String,
            presentSourceKeys: Set<String>
        ) {
            if (presentSourceKeys.isEmpty() || presentSourceKeys.size == sourceKeys.size) return
            val missing = sourceKeys.filterNot { presentSourceKeys.contains(it) }
            if (missing.isEmpty()) return
            presentSourceKeys.forEach { sourceKey ->
                extrasBySource.getOrPut(sourceKey) { mutableListOf() }.add(
                    QuickMaintenanceDiffItem(
                        category = category,
                        title = title,
                        missingSourceKeys = missing
                    )
                )
            }
        }

        if (QuickMaintenanceCategory.PASSWORDS in categories) {
            val entries = passwordRepository.getAllPasswordEntries().first()
                .filter { !it.isDeleted && !it.isArchived }
            entries.groupBy { passwordFingerprint(it) }
                .values
                .forEach { group ->
                    val present = group.mapTo(linkedSetOf()) { sourceKeyOf(it) }
                    appendDiff(
                        category = QuickMaintenanceCategory.PASSWORDS,
                        title = passwordDiffLabel(group.first()),
                        presentSourceKeys = present
                    )
                }
        }

        if (QuickMaintenanceCategory.AUTHENTICATORS in categories || QuickMaintenanceCategory.BANK_CARDS in categories) {
            val items = secureItemRepository.getAllItems().first().filter { !it.isDeleted }
            if (QuickMaintenanceCategory.AUTHENTICATORS in categories) {
                items.filter { it.itemType == ItemType.TOTP }
                    .groupBy { secureFingerprint(it) }
                    .values
                    .forEach { group ->
                        val present = group.mapTo(linkedSetOf()) { sourceKeyOf(it) }
                        appendDiff(
                            category = QuickMaintenanceCategory.AUTHENTICATORS,
                            title = secureDiffLabel(group.first()),
                            presentSourceKeys = present
                        )
                    }
            }
            if (QuickMaintenanceCategory.BANK_CARDS in categories) {
                items.filter { it.itemType == ItemType.BANK_CARD || it.itemType == ItemType.DOCUMENT }
                    .groupBy { secureFingerprint(it) }
                    .values
                    .forEach { group ->
                        val present = group.mapTo(linkedSetOf()) { sourceKeyOf(it) }
                        appendDiff(
                            category = QuickMaintenanceCategory.BANK_CARDS,
                            title = secureDiffLabel(group.first()),
                            presentSourceKeys = present
                        )
                    }
            }
        }

        if (QuickMaintenanceCategory.PASSKEYS in categories) {
            val passkeys = passkeyRepository.getAllPasskeysSync()
            passkeys.groupBy { "${normalizeText(it.rpId)}|${normalizeText(it.userName)}" }
                .values
                .forEach { group ->
                    val present = group.mapTo(linkedSetOf()) { sourceKeyOf(it) }
                    appendDiff(
                        category = QuickMaintenanceCategory.PASSKEYS,
                        title = passkeyDiffLabel(group.first()),
                        presentSourceKeys = present
                    )
                }
        }

        return extrasBySource.map { (sourceKey, items) ->
            QuickMaintenanceSourceDiff(
                sourceKey = sourceKey,
                extraItems = items.sortedWith(compareBy({ it.category.ordinal }, { it.title })).take(30)
            )
        }.sortedByDescending { it.extraItems.size }
    }

    private suspend fun syncPasswords(
        keepassDatabases: List<LocalKeePassDatabase>,
        vaults: List<BitwardenVault>
    ): QuickMaintenanceCategoryResult {
        val entries = passwordRepository.getAllPasswordEntries().first()
            .filter { !it.isDeleted && !it.isArchived }
        val groups = entries
            .groupBy { passwordFingerprint(it) }
            .values
            .filter { it.size > 1 }

        val keepassSyncBuffer = linkedMapOf<Long, MutableList<PasswordEntry>>()
        var updatedCount = 0
        var createdCount = 0

        groups.forEach { group ->
            val canonical = pickCanonicalPassword(group)
            group.forEach { entry ->
                val synced = mergePasswordEntry(entry, canonical)
                if (synced != entry) {
                    passwordRepository.updatePasswordEntry(synced)
                    updatedCount += 1
                    synced.keepassDatabaseId?.let { databaseId ->
                        keepassSyncBuffer.getOrPut(databaseId) { mutableListOf() }.add(synced)
                    }
                }
            }

            val existingKeys = group.mapTo(linkedSetOf()) { sourceKeyOf(it) }

            if (MONICA_SOURCE_KEY !in existingKeys) {
                val created = createPasswordClone(canonical, targetLocal = true)
                passwordRepository.insertPasswordEntry(created)
                createdCount += 1
            }

            keepassDatabases.forEach { database ->
                val targetKey = keepassSourceKey(database.id)
                if (targetKey !in existingKeys) {
                    val created = createPasswordClone(canonical, keepassDatabaseId = database.id)
                    val newId = passwordRepository.insertPasswordEntry(created)
                    val inserted = created.copy(id = newId)
                    createdCount += 1
                    keepassSyncBuffer.getOrPut(database.id) { mutableListOf() }.add(inserted)
                }
            }

            vaults.forEach { vault ->
                val targetKey = bitwardenSourceKey(vault.id)
                if (targetKey !in existingKeys) {
                    val created = createPasswordClone(canonical, bitwardenVaultId = vault.id)
                    passwordRepository.insertPasswordEntry(created)
                    createdCount += 1
                }
            }
        }

        flushKeePassPasswordChanges(keepassSyncBuffer)

        return QuickMaintenanceCategoryResult(
            category = QuickMaintenanceCategory.PASSWORDS,
            matchedGroups = groups.size,
            updatedEntries = updatedCount,
            createdEntries = createdCount,
            note = if (groups.isEmpty()) {
                QuickMaintenanceCategoryNote.PASSWORDS_NONE_FOUND
            } else {
                QuickMaintenanceCategoryNote.PASSWORDS_MATCHED_ONLY
            }
        )
    }

    private suspend fun syncSecureItems(
        type: ItemType,
        category: QuickMaintenanceCategory,
        keepassDatabases: List<LocalKeePassDatabase>,
        vaults: List<BitwardenVault>
    ): QuickMaintenanceCategoryResult {
        val items = secureItemRepository.getAllItems().first()
            .filter { !it.isDeleted && it.itemType == type }
        val groups = items
            .groupBy { secureFingerprint(it) }
            .values
            .filter { it.size > 1 }

        val keepassSyncBuffer = linkedMapOf<Long, MutableList<SecureItem>>()
        var updatedCount = 0
        var createdCount = 0

        groups.forEach { group ->
            val canonical = pickCanonicalSecureItem(group)
            group.forEach { item ->
                val synced = mergeSecureItem(item, canonical)
                if (synced != item) {
                    secureItemRepository.updateItem(synced)
                    updatedCount += 1
                    synced.keepassDatabaseId?.let { databaseId ->
                        keepassSyncBuffer.getOrPut(databaseId) { mutableListOf() }.add(synced)
                    }
                }
            }

            val existingKeys = group.mapTo(linkedSetOf()) { sourceKeyOf(it) }

            if (MONICA_SOURCE_KEY !in existingKeys) {
                secureItemRepository.insertItem(createSecureItemClone(canonical, targetLocal = true))
                createdCount += 1
            }

            keepassDatabases.forEach { database ->
                val targetKey = keepassSourceKey(database.id)
                if (targetKey !in existingKeys) {
                    val created = createSecureItemClone(canonical, keepassDatabaseId = database.id)
                    val newId = secureItemRepository.insertItem(created)
                    val inserted = created.copy(id = newId)
                    createdCount += 1
                    keepassSyncBuffer.getOrPut(database.id) { mutableListOf() }.add(inserted)
                }
            }

            vaults.forEach { vault ->
                val targetKey = bitwardenSourceKey(vault.id)
                if (targetKey !in existingKeys) {
                    secureItemRepository.insertItem(createSecureItemClone(canonical, bitwardenVaultId = vault.id))
                    createdCount += 1
                }
            }
        }

        flushKeePassSecureItemChanges(keepassSyncBuffer)

        return QuickMaintenanceCategoryResult(
            category = category,
            matchedGroups = groups.size,
            updatedEntries = updatedCount,
            createdEntries = createdCount,
            note = when {
                groups.isEmpty() && type == ItemType.TOTP -> QuickMaintenanceCategoryNote.AUTHENTICATORS_NONE_FOUND
                groups.isEmpty() && type == ItemType.BANK_CARD -> QuickMaintenanceCategoryNote.BANK_CARDS_NONE_FOUND
                type == ItemType.TOTP -> QuickMaintenanceCategoryNote.AUTHENTICATORS_MATCHED_ONLY
                else -> QuickMaintenanceCategoryNote.BANK_CARDS_MATCHED_ONLY
            }
        )
    }

    private suspend fun scanPasskeys(): QuickMaintenanceCategoryResult {
        val passkeys = passkeyRepository.getAllPasskeysSync()
        val groups = passkeys
            .groupBy { "${normalizeText(it.rpId)}|${normalizeText(it.userName)}" }
            .values
            .filter { it.size > 1 }

        return QuickMaintenanceCategoryResult(
            category = QuickMaintenanceCategory.PASSKEYS,
            matchedGroups = groups.size,
            skippedGroups = groups.size,
            note = QuickMaintenanceCategoryNote.PASSKEYS_SCAN_ONLY
        )
    }

    private fun mergePasswordEntry(existing: PasswordEntry, canonical: PasswordEntry): PasswordEntry {
        val contentChanged = passwordContentChanged(existing, canonical)
        val now = if (contentChanged) Date() else existing.updatedAt
        return existing.copy(
            title = canonical.title,
            website = canonical.website,
            username = canonical.username,
            password = canonical.password,
            notes = canonical.notes,
            isFavorite = canonical.isFavorite,
            appPackageName = canonical.appPackageName,
            appName = canonical.appName,
            email = canonical.email,
            phone = canonical.phone,
            addressLine = canonical.addressLine,
            city = canonical.city,
            state = canonical.state,
            zipCode = canonical.zipCode,
            country = canonical.country,
            creditCardNumber = canonical.creditCardNumber,
            creditCardHolder = canonical.creditCardHolder,
            creditCardExpiry = canonical.creditCardExpiry,
            creditCardCVV = canonical.creditCardCVV,
            categoryId = canonical.categoryId,
            authenticatorKey = canonical.authenticatorKey,
            passkeyBindings = canonical.passkeyBindings,
            loginType = canonical.loginType,
            ssoProvider = canonical.ssoProvider,
            ssoRefEntryId = canonical.ssoRefEntryId,
            updatedAt = now,
            bitwardenLocalModified = existing.bitwardenLocalModified || (existing.isBitwardenEntry() && contentChanged)
        )
    }

    private fun createPasswordClone(
        canonical: PasswordEntry,
        targetLocal: Boolean = false,
        keepassDatabaseId: Long? = null,
        bitwardenVaultId: Long? = null
    ): PasswordEntry {
        val now = Date()
        return canonical.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            deletedAt = null,
            isArchived = false,
            archivedAt = null,
            keepassDatabaseId = if (targetLocal || bitwardenVaultId != null) null else keepassDatabaseId,
            keepassGroupPath = null,
            keepassEntryUuid = keepassDatabaseId?.let { UUID.randomUUID().toString() },
            keepassGroupUuid = null,
            bitwardenVaultId = if (targetLocal || keepassDatabaseId != null) null else bitwardenVaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false
        )
    }

    private fun mergeSecureItem(existing: SecureItem, canonical: SecureItem): SecureItem {
        val patchedData = patchSecureItemData(canonical, existing.categoryId ?: canonical.categoryId, existing.keepassDatabaseId)
        val contentChanged = secureItemContentChanged(existing, canonical, patchedData)
        val now = if (contentChanged) Date() else existing.updatedAt
        return existing.copy(
            title = canonical.title,
            notes = canonical.notes,
            isFavorite = canonical.isFavorite,
            itemData = patchedData,
            imagePaths = canonical.imagePaths,
            categoryId = canonical.categoryId,
            updatedAt = now,
            bitwardenLocalModified = existing.bitwardenLocalModified ||
                (existing.bitwardenVaultId != null && existing.bitwardenCipherId != null && contentChanged),
            syncStatus = when {
                existing.bitwardenVaultId == null -> "NONE"
                existing.bitwardenCipherId.isNullOrBlank() -> "PENDING"
                contentChanged -> "PENDING"
                else -> existing.syncStatus
            }
        )
    }

    private fun createSecureItemClone(
        canonical: SecureItem,
        targetLocal: Boolean = false,
        keepassDatabaseId: Long? = null,
        bitwardenVaultId: Long? = null
    ): SecureItem {
        val now = Date()
        return canonical.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            deletedAt = null,
            keepassDatabaseId = if (targetLocal || bitwardenVaultId != null) null else keepassDatabaseId,
            keepassGroupPath = null,
            keepassEntryUuid = keepassDatabaseId?.let { UUID.randomUUID().toString() },
            keepassGroupUuid = null,
            bitwardenVaultId = if (targetLocal || keepassDatabaseId != null) null else bitwardenVaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            syncStatus = when {
                targetLocal || keepassDatabaseId != null -> "NONE"
                bitwardenVaultId != null -> "PENDING"
                else -> canonical.syncStatus
            },
            itemData = patchSecureItemData(canonical, canonical.categoryId, keepassDatabaseId)
        )
    }

    private suspend fun flushKeePassPasswordChanges(buffer: Map<Long, List<PasswordEntry>>) {
        buffer.forEach { (databaseId, entries) ->
            keePassBridge.upsertLegacyPasswordEntries(
                databaseId = databaseId,
                entries = entries.distinctBy { it.id },
                resolvePassword = { resolveComparablePassword(it.password) },
                forceSyncWrite = true
            )
        }
    }

    private suspend fun flushKeePassSecureItemChanges(buffer: Map<Long, List<SecureItem>>) {
        buffer.forEach { (databaseId, items) ->
            keePassBridge.upsertLegacySecureItems(
                databaseId = databaseId,
                items = items.distinctBy { it.id },
                forceSyncWrite = true
            )
        }
    }

    private fun pickCanonicalPassword(group: List<PasswordEntry>): PasswordEntry {
        return group.maxWithOrNull(
            compareBy<PasswordEntry>(
                { completenessScore(it) },
                { sourcePriorityOf(it) },
                { it.updatedAt.time }
            )
        ) ?: group.first()
    }

    private fun pickCanonicalSecureItem(group: List<SecureItem>): SecureItem {
        return group.maxWithOrNull(
            compareBy<SecureItem>(
                { completenessScore(it) },
                { sourcePriorityOf(it) },
                { it.updatedAt.time }
            )
        ) ?: group.first()
    }

    private fun completenessScore(entry: PasswordEntry): Int {
        return listOf(
            entry.title,
            entry.website,
            entry.username,
            resolveComparablePassword(entry.password),
            entry.notes,
            entry.authenticatorKey,
            entry.email,
            entry.phone,
            entry.addressLine,
            entry.city,
            entry.state,
            entry.zipCode,
            entry.country,
            entry.creditCardNumber,
            entry.creditCardHolder,
            entry.creditCardExpiry,
            entry.passkeyBindings
        ).sumOf { it.trim().length } + if (entry.isFavorite) 20 else 0
    }

    private fun completenessScore(item: SecureItem): Int {
        return item.title.length + item.notes.length + item.itemData.length + item.imagePaths.length + if (item.isFavorite) 20 else 0
    }

    private fun sourcePriorityOf(entry: PasswordEntry): Int = when {
        entry.bitwardenVaultId != null -> 3
        entry.keepassDatabaseId != null -> 2
        else -> 1
    }

    private fun sourcePriorityOf(item: SecureItem): Int = when {
        item.bitwardenVaultId != null -> 3
        item.keepassDatabaseId != null -> 2
        else -> 1
    }

    private fun passwordContentChanged(existing: PasswordEntry, canonical: PasswordEntry): Boolean {
        return existing.title != canonical.title ||
            existing.website != canonical.website ||
            existing.username != canonical.username ||
            existing.password != canonical.password ||
            existing.notes != canonical.notes ||
            existing.isFavorite != canonical.isFavorite ||
            existing.appPackageName != canonical.appPackageName ||
            existing.appName != canonical.appName ||
            existing.email != canonical.email ||
            existing.phone != canonical.phone ||
            existing.addressLine != canonical.addressLine ||
            existing.city != canonical.city ||
            existing.state != canonical.state ||
            existing.zipCode != canonical.zipCode ||
            existing.country != canonical.country ||
            existing.creditCardNumber != canonical.creditCardNumber ||
            existing.creditCardHolder != canonical.creditCardHolder ||
            existing.creditCardExpiry != canonical.creditCardExpiry ||
            existing.creditCardCVV != canonical.creditCardCVV ||
            existing.categoryId != canonical.categoryId ||
            existing.authenticatorKey != canonical.authenticatorKey ||
            existing.passkeyBindings != canonical.passkeyBindings ||
            existing.loginType != canonical.loginType ||
            existing.ssoProvider != canonical.ssoProvider ||
            existing.ssoRefEntryId != canonical.ssoRefEntryId
    }

    private fun secureItemContentChanged(existing: SecureItem, canonical: SecureItem, patchedData: String): Boolean {
        return existing.title != canonical.title ||
            existing.notes != canonical.notes ||
            existing.isFavorite != canonical.isFavorite ||
            existing.itemData != patchedData ||
            existing.imagePaths != canonical.imagePaths ||
            existing.categoryId != canonical.categoryId
    }

    private fun patchSecureItemData(
        canonical: SecureItem,
        categoryId: Long?,
        keepassDatabaseId: Long?
    ): String {
        if (canonical.itemType != ItemType.TOTP) return canonical.itemData
        val data = runCatching { json.decodeFromString<TotpData>(canonical.itemData) }.getOrNull() ?: return canonical.itemData
        return json.encodeToString(
            data.copy(
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId
            )
        )
    }

    private fun passwordFingerprint(entry: PasswordEntry): String {
        return listOf(
            normalizeText(entry.title),
            normalizeText(entry.username),
            normalizeWebsite(entry.website)
        ).joinToString("|")
    }

    private fun secureFingerprint(item: SecureItem): String {
        val objectData = runCatching { json.parseToJsonElement(item.itemData).jsonObject }.getOrNull()
        return when (item.itemType) {
            ItemType.TOTP -> {
                val totp = runCatching { json.decodeFromString<TotpData>(item.itemData) }.getOrNull()
                if (totp != null) {
                    listOf(
                        normalizeText(totp.issuer),
                        normalizeText(totp.accountName),
                        normalizeSecret(totp.secret),
                        normalizeText(item.title)
                    ).joinToString("|")
                } else {
                    listOf(
                        normalizeText(parseOtpUriIssuer(item.itemData).orEmpty()),
                        normalizeText(parseOtpUriAccount(item.itemData).orEmpty()),
                        normalizeSecret(parseOtpUriSecret(item.itemData).orEmpty()),
                        normalizeText(item.title)
                    ).joinToString("|")
                }
            }
            ItemType.BANK_CARD -> {
                val cardNumber = objectData?.get("cardNumber")?.jsonPrimitive?.contentOrNull.orEmpty()
                "${normalizeSecret(cardNumber)}|${normalizeText(item.title)}"
            }
            else -> normalizeText(item.title)
        }
    }

    private fun sourceKeyOf(entry: PasswordEntry): String {
        return when {
            entry.keepassDatabaseId != null -> keepassSourceKey(entry.keepassDatabaseId)
            entry.bitwardenVaultId != null -> bitwardenSourceKey(entry.bitwardenVaultId)
            else -> MONICA_SOURCE_KEY
        }
    }

    private fun sourceKeyOf(item: SecureItem): String {
        return when {
            item.keepassDatabaseId != null -> keepassSourceKey(item.keepassDatabaseId)
            item.bitwardenVaultId != null -> bitwardenSourceKey(item.bitwardenVaultId)
            else -> MONICA_SOURCE_KEY
        }
    }

    private fun sourceKeyOf(item: PasskeyEntry): String {
        return when {
            item.keepassDatabaseId != null -> keepassSourceKey(item.keepassDatabaseId)
            item.bitwardenVaultId != null -> bitwardenSourceKey(item.bitwardenVaultId)
            else -> MONICA_SOURCE_KEY
        }
    }

    private fun passwordDiffLabel(entry: PasswordEntry): String {
        val title = entry.title.ifBlank { entry.website.ifBlank { entry.username } }
        return title.ifBlank { "(untitled)" }
    }

    private fun secureDiffLabel(item: SecureItem): String {
        return item.title.ifBlank { "(untitled)" }
    }

    private fun passkeyDiffLabel(item: PasskeyEntry): String {
        val account = item.userName.ifBlank { item.userDisplayName }
        return "${item.rpId} / ${account.ifBlank { "unknown" }}"
    }

    private fun resolveComparablePassword(value: String): String {
        if (value.isBlank()) return ""
        return runCatching { securityManager.decryptData(value) }
            .getOrDefault(value)
            .trim()
    }

    private fun normalizeText(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun normalizeWebsite(value: String): String {
        val raw = value.trim().lowercase(Locale.ROOT)
        if (raw.isBlank()) return ""
        return raw.removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun normalizeSecret(value: String): String {
        return value.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
    }

    private fun parseOtpUriIssuer(raw: String): String? {
        val normalized = raw.trim()
        if (!normalized.lowercase(Locale.ROOT).startsWith("otpauth://")) return null
        return runCatching {
            val label = URI(normalized).rawPath?.removePrefix("/").orEmpty()
            val decoded = URLDecoder.decode(label, Charsets.UTF_8.name())
            decoded.substringBefore(":", "").trim()
        }.getOrNull()
    }

    private fun parseOtpUriAccount(raw: String): String? {
        val normalized = raw.trim()
        if (!normalized.lowercase(Locale.ROOT).startsWith("otpauth://")) return null
        return runCatching {
            val label = URI(normalized).rawPath?.removePrefix("/").orEmpty()
            val decoded = URLDecoder.decode(label, Charsets.UTF_8.name())
            decoded.substringAfter(":", decoded).trim()
        }.getOrNull()
    }

    private fun parseOtpUriSecret(raw: String): String? {
        val normalized = raw.trim()
        if (!normalized.lowercase(Locale.ROOT).startsWith("otpauth://")) return null
        return runCatching {
            val query = URI(normalized).rawQuery.orEmpty()
            query.split("&")
                .mapNotNull { part ->
                    val key = part.substringBefore("=", "")
                    val value = part.substringAfter("=", "")
                    if (key.equals("secret", ignoreCase = true)) {
                        URLDecoder.decode(value, Charsets.UTF_8.name())
                    } else {
                        null
                    }
                }
                .firstOrNull()
        }.getOrNull()
    }

    private fun keepassSourceKey(databaseId: Long): String = "keepass:$databaseId"

    private fun bitwardenSourceKey(vaultId: Long): String = "bitwarden:$vaultId"

    private fun vaultLabel(vault: BitwardenVault): String {
        return vault.displayName
            ?.takeIf { it.isNotBlank() && !it.equals("Bitwarden", ignoreCase = true) }
            ?: vault.email
    }

    companion object {
        private const val MONICA_SOURCE_KEY = "monica"
    }
}

private data class MutableSourceStats(
    var passwordCount: Int = 0,
    var authenticatorCount: Int = 0,
    var bankCardCount: Int = 0,
    var passkeyCount: Int = 0
)

fun createQuickDatabaseMaintenanceEngine(
    passwordRepository: PasswordRepository,
    secureItemRepository: SecureItemRepository,
    passkeyRepository: PasskeyRepository,
    localKeePassDatabaseDao: LocalKeePassDatabaseDao,
    bitwardenVaultDao: BitwardenVaultDao,
    securityManager: SecurityManager,
    workspaceRepository: KeePassWorkspaceRepository
): QuickDatabaseMaintenanceEngine {
    return QuickDatabaseMaintenanceEngine(
        passwordRepository = passwordRepository,
        secureItemRepository = secureItemRepository,
        passkeyRepository = passkeyRepository,
        localKeePassDatabaseDao = localKeePassDatabaseDao,
        bitwardenVaultDao = bitwardenVaultDao,
        securityManager = securityManager,
        keePassBridge = KeePassCompatibilityBridge(workspaceRepository)
    )
}
