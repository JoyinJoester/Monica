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
import takagi.ru.monica.data.OperationLog
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.OperationType
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TIMELINE_FIELD_MAINTENANCE_SNAPSHOT_PAYLOAD
import takagi.ru.monica.data.model.TimelineMaintenanceSnapshotPayload
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenFolderDao
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.bitwarden.BitwardenVaultDao
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.OperationLogRepository
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.repository.PasskeyRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.FieldChange
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

enum class QuickMaintenanceMode {
    FULL_BIDIRECTIONAL,
    TARGET_DATABASE
}

data class QuickMaintenancePlan(
    val conflictCount: Int,
    val conflictSamples: List<String>,
    val estimatedGroups: Int
)

data class QuickMaintenanceProgress(
    val stage: String,
    val message: String,
    val processed: Int = 0,
    val total: Int = 0
)

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
    val categories: Set<QuickMaintenanceCategory>,
    val mode: QuickMaintenanceMode = QuickMaintenanceMode.FULL_BIDIRECTIONAL,
    val targetSourceKey: String? = null
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
    val totalSkippedGroups: Int,
    val passwordConflictHandled: Int = 0,
    val fieldConflictCount: Int = 0,
    val operationLogs: List<String> = emptyList()
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
    private val bitwardenFolderDao: BitwardenFolderDao,
    private val bitwardenVaultDao: BitwardenVaultDao,
    private val securityManager: SecurityManager,
    private val keePassBridge: KeePassCompatibilityBridge,
    private val operationLogRepository: OperationLogRepository,
    private val snapshotManager: MaintenanceSnapshotManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun plan(request: QuickMaintenanceRequest): QuickMaintenancePlan = withContext(Dispatchers.IO) {
        val passwordGroups = if (QuickMaintenanceCategory.PASSWORDS in request.categories) {
            passwordRepository.getAllPasswordEntries().first()
                .filter { !it.isDeleted && !it.isArchived }
                .groupBy { passwordFingerprint(it) }
                .values
        } else {
            emptyList()
        }
        val secureGroups = if (QuickMaintenanceCategory.AUTHENTICATORS in request.categories || QuickMaintenanceCategory.BANK_CARDS in request.categories) {
            secureItemRepository.getAllItems().first()
                .filter { !it.isDeleted }
                .groupBy { "${it.itemType.name}|${secureFingerprint(it)}" }
                .values
        } else {
            emptyList()
        }

        val conflictSamples = mutableListOf<String>()
        var conflictCount = 0

        passwordGroups.forEach { group ->
            val distinctPasswords = group.map { resolveComparablePassword(it.password) }.filter { it.isNotBlank() }.distinct()
            if (distinctPasswords.size > 1) {
                conflictCount += 1
                if (conflictSamples.size < 12) {
                    conflictSamples += "密码冲突: ${passwordDiffLabel(group.first())} (${distinctPasswords.size} 个密码版本)"
                }
            }
            if (hasPasswordFieldConflict(group)) {
                conflictCount += 1
                if (conflictSamples.size < 12) {
                    conflictSamples += "字段冲突: ${passwordDiffLabel(group.first())}"
                }
            }
        }

        secureGroups.forEach { group ->
            if (hasSecureItemFieldConflict(group)) {
                conflictCount += 1
                if (conflictSamples.size < 12) {
                    conflictSamples += "字段冲突: ${secureDiffLabel(group.first())}"
                }
            }
        }

        QuickMaintenancePlan(
            conflictCount = conflictCount,
            conflictSamples = conflictSamples,
            estimatedGroups = passwordGroups.size + secureGroups.size
        )
    }

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

    suspend fun run(
        request: QuickMaintenanceRequest,
        onProgress: (QuickMaintenanceProgress) -> Unit = {}
    ): QuickMaintenanceResult = withContext(Dispatchers.IO) {
        val sources = loadSources()
        require(request.mode != QuickMaintenanceMode.TARGET_DATABASE || !request.targetSourceKey.isNullOrBlank()) {
            "Target source key is required for TARGET_DATABASE mode"
        }
        val keepassDatabases = localKeePassDatabaseDao.getAllDatabasesSync()
        val vaults = bitwardenVaultDao.getAllVaults().filter { it.syncEnabled }

        onProgress(QuickMaintenanceProgress(stage = "SCAN", message = "正在扫描数据源", processed = 0, total = 5))

        operationLogRepository.deleteOldMaintenanceSnapshotLogs(
            snapshotFieldName = TIMELINE_FIELD_MAINTENANCE_SNAPSHOT_PAYLOAD,
            daysToKeep = SNAPSHOT_RETENTION_DAYS
        )

        val snapshotPayload = snapshotManager.createPayload()
        writeSnapshotLog(request, snapshotPayload)

        onProgress(QuickMaintenanceProgress(stage = "PLAN", message = "正在生成合并计划", processed = 1, total = 5))
        val sourceStats = buildSourceStats(sources)
        val sourceDiffs = buildSourceDiffs(request.categories, sources)
        val operationLogs = mutableListOf<String>()
        var passwordConflictHandled = 0
        var fieldConflictCount = 0

        onProgress(QuickMaintenanceProgress(stage = "MERGE", message = "正在执行合并", processed = 2, total = 5))
        val categoryResults = buildList {
            if (QuickMaintenanceCategory.PASSWORDS in request.categories) {
                val passwordResult = syncPasswords(request, keepassDatabases, vaults)
                passwordConflictHandled += passwordResult.second
                fieldConflictCount += passwordResult.third
                operationLogs += passwordResult.fourth
                add(passwordResult.first)
            }
            if (QuickMaintenanceCategory.AUTHENTICATORS in request.categories) {
                val result = syncSecureItems(request, ItemType.TOTP, QuickMaintenanceCategory.AUTHENTICATORS, keepassDatabases, vaults)
                fieldConflictCount += result.second
                operationLogs += result.third
                add(result.first)
            }
            if (QuickMaintenanceCategory.BANK_CARDS in request.categories) {
                val cards = syncSecureItems(request, ItemType.BANK_CARD, QuickMaintenanceCategory.BANK_CARDS, keepassDatabases, vaults)
                val documents = syncSecureItems(request, ItemType.DOCUMENT, QuickMaintenanceCategory.BANK_CARDS, keepassDatabases, vaults)
                fieldConflictCount += cards.second + documents.second
                operationLogs += cards.third + documents.third
                add(
                    QuickMaintenanceCategoryResult(
                        category = QuickMaintenanceCategory.BANK_CARDS,
                        matchedGroups = cards.first.matchedGroups + documents.first.matchedGroups,
                        updatedEntries = cards.first.updatedEntries + documents.first.updatedEntries,
                        createdEntries = cards.first.createdEntries + documents.first.createdEntries,
                        skippedGroups = cards.first.skippedGroups + documents.first.skippedGroups,
                        note = if ((cards.first.matchedGroups + documents.first.matchedGroups) == 0) {
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

        onProgress(QuickMaintenanceProgress(stage = "SUMMARY", message = "正在整理冲突处理结果", processed = 4, total = 5))

        QuickMaintenanceResult(
            categoryResults = categoryResults,
            sources = sources,
            sourceStats = sourceStats,
            sourceDiffs = sourceDiffs,
            totalMatchedGroups = categoryResults.sumOf { it.matchedGroups },
            totalUpdatedEntries = categoryResults.sumOf { it.updatedEntries },
            totalCreatedEntries = categoryResults.sumOf { it.createdEntries },
            totalSkippedGroups = categoryResults.sumOf { it.skippedGroups },
            passwordConflictHandled = passwordConflictHandled,
            fieldConflictCount = fieldConflictCount,
            operationLogs = operationLogs
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
        request: QuickMaintenanceRequest,
        keepassDatabases: List<LocalKeePassDatabase>,
        vaults: List<BitwardenVault>
    ): Quadruple<QuickMaintenanceCategoryResult, Int, Int, List<String>> {
        val entries = passwordRepository.getAllPasswordEntries().first()
            .filter { !it.isDeleted && !it.isArchived }
        val groups = entries
            .groupBy { passwordFingerprint(it) }
            .values

        val keepassSyncBuffer = linkedMapOf<Long, MutableList<PasswordEntry>>()
        var updatedCount = 0
        var createdCount = 0
        var passwordConflictHandled = 0
        var fieldConflictCount = 0
        val operationLogs = mutableListOf<String>()
        val folderSyncContext = buildFolderSyncContext(vaults)

        val targetSourceKeys = resolveTargetSourceKeys(request, keepassDatabases, vaults)

        groups.forEach { group ->
            val canonical = pickCanonicalPassword(group)
            val folderSeed = resolveFolderSeed(canonical, group, folderSyncContext)
            if (hasPasswordFieldConflict(group)) {
                fieldConflictCount += 1
                operationLogs += "字段冲突: ${passwordDiffLabel(canonical)} -> 已按完整度规则自动合并"
            }

            val conflictPasswords = group
                .map { resolveComparablePassword(it.password) }
                .filter { it.isNotBlank() }
                .distinct()
            if (conflictPasswords.size > 1) {
                passwordConflictHandled += 1
                operationLogs += "密码冲突: ${passwordDiffLabel(canonical)} -> 已生成多密码副本 (${conflictPasswords.size})"
            }

            group.forEach entryLoop@ { entry ->
                val sourceKey = sourceKeyOf(entry)
                if (!shouldUpdateExistingEntry(request, sourceKey)) {
                    return@entryLoop
                }
                val folderTarget = resolveFolderTargetForSource(sourceKey, folderSeed, folderSyncContext)
                val synced = mergePasswordEntry(entry, canonical, folderTarget)
                if (synced != entry) {
                    passwordRepository.updatePasswordEntry(synced)
                    updatedCount += 1
                    synced.keepassDatabaseId?.let { databaseId ->
                        keepassSyncBuffer.getOrPut(databaseId) { mutableListOf() }.add(synced)
                    }
                }
            }

            val existingKeys = group.mapTo(linkedSetOf()) { sourceKeyOf(it) }
            targetSourceKeys.forEach { targetKey ->
                if (targetKey !in existingKeys) {
                    val folderTarget = resolveFolderTargetForSource(targetKey, folderSeed, folderSyncContext)
                    val created = createPasswordCloneForSource(canonical, targetKey, folderTarget)
                    val newId = passwordRepository.insertPasswordEntry(created)
                    createdCount += 1
                    if (created.keepassDatabaseId != null) {
                        keepassSyncBuffer.getOrPut(created.keepassDatabaseId) { mutableListOf() }
                            .add(created.copy(id = newId))
                    }
                }
            }

            if (conflictPasswords.size > 1) {
                val secondaryPasswords = conflictPasswords.drop(1)
                targetSourceKeys.forEach { targetKey ->
                    secondaryPasswords.forEachIndexed { index, rawPassword ->
                        val existingVariant = group.firstOrNull {
                            sourceKeyOf(it) == targetKey && resolveComparablePassword(it.password) == rawPassword
                        }
                        if (existingVariant == null) {
                            val encrypted = runCatching { securityManager.encryptData(rawPassword) }.getOrDefault(rawPassword)
                            val folderTarget = resolveFolderTargetForSource(targetKey, folderSeed, folderSyncContext)
                            val variant = createPasswordCloneForSource(canonical.copy(
                                title = if (canonical.title.isBlank()) "冲突密码 #${index + 2}" else "${canonical.title} [冲突密码 ${index + 2}]",
                                password = encrypted,
                                notes = canonical.notes + "\n\n[同步合并] 保留冲突密码版本"
                            ), targetKey, folderTarget)
                            val newId = passwordRepository.insertPasswordEntry(variant)
                            createdCount += 1
                            if (variant.keepassDatabaseId != null) {
                                keepassSyncBuffer.getOrPut(variant.keepassDatabaseId) { mutableListOf() }
                                    .add(variant.copy(id = newId))
                            }
                        }
                    }
                }
            }
        }

        flushKeePassPasswordChanges(keepassSyncBuffer)

        return Quadruple(
            QuickMaintenanceCategoryResult(
                category = QuickMaintenanceCategory.PASSWORDS,
                matchedGroups = groups.size,
                updatedEntries = updatedCount,
                createdEntries = createdCount,
                note = if (groups.isEmpty()) {
                    QuickMaintenanceCategoryNote.PASSWORDS_NONE_FOUND
                } else {
                    QuickMaintenanceCategoryNote.PASSWORDS_MATCHED_ONLY
                }
            ),
            passwordConflictHandled,
            fieldConflictCount,
            operationLogs
        )
    }

    private suspend fun syncSecureItems(
        request: QuickMaintenanceRequest,
        type: ItemType,
        category: QuickMaintenanceCategory,
        keepassDatabases: List<LocalKeePassDatabase>,
        vaults: List<BitwardenVault>
    ): Triple<QuickMaintenanceCategoryResult, Int, List<String>> {
        val items = secureItemRepository.getAllItems().first()
            .filter { !it.isDeleted && it.itemType == type }
        val groups = items
            .groupBy { secureFingerprint(it) }
            .values

        val keepassSyncBuffer = linkedMapOf<Long, MutableList<SecureItem>>()
        var updatedCount = 0
        var createdCount = 0
        var fieldConflictCount = 0
        val operationLogs = mutableListOf<String>()
        val targetSourceKeys = resolveTargetSourceKeys(request, keepassDatabases, vaults)

        groups.forEach { group ->
            val canonical = pickCanonicalSecureItem(group)
            if (hasSecureItemFieldConflict(group)) {
                fieldConflictCount += 1
                operationLogs += "字段冲突: ${secureDiffLabel(canonical)} -> 已按完整度规则自动合并"
            }
            group.forEach itemLoop@ { item ->
                if (!shouldUpdateExistingEntry(request, sourceKeyOf(item))) {
                    return@itemLoop
                }
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

            targetSourceKeys.forEach { targetKey ->
                if (targetKey !in existingKeys) {
                    val created = createSecureItemCloneForSource(canonical, targetKey)
                    val newId = secureItemRepository.insertItem(created)
                    createdCount += 1
                    if (created.keepassDatabaseId != null) {
                        keepassSyncBuffer.getOrPut(created.keepassDatabaseId) { mutableListOf() }
                            .add(created.copy(id = newId))
                    }
                }
            }
        }

        flushKeePassSecureItemChanges(keepassSyncBuffer)

        return Triple(
            QuickMaintenanceCategoryResult(
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
            ),
            fieldConflictCount,
            operationLogs
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

    private fun mergePasswordEntry(
        existing: PasswordEntry,
        canonical: PasswordEntry,
        folderTarget: ResolvedFolderTarget
    ): PasswordEntry {
        val folderAwareCanonical = canonical.copy(
            categoryId = folderTarget.categoryId ?: canonical.categoryId,
            keepassGroupPath = if (existing.keepassDatabaseId != null) {
                folderTarget.keepassGroupPath ?: existing.keepassGroupPath
            } else {
                existing.keepassGroupPath
            },
            bitwardenFolderId = if (existing.bitwardenVaultId != null) {
                folderTarget.bitwardenFolderId ?: existing.bitwardenFolderId
            } else {
                existing.bitwardenFolderId
            }
        )
        val contentChanged = passwordContentChanged(existing, folderAwareCanonical)
        val now = if (contentChanged) Date() else existing.updatedAt
        return existing.copy(
            title = folderAwareCanonical.title,
            website = folderAwareCanonical.website,
            username = folderAwareCanonical.username,
            password = folderAwareCanonical.password,
            notes = folderAwareCanonical.notes,
            isFavorite = folderAwareCanonical.isFavorite,
            appPackageName = folderAwareCanonical.appPackageName,
            appName = folderAwareCanonical.appName,
            email = folderAwareCanonical.email,
            phone = folderAwareCanonical.phone,
            addressLine = folderAwareCanonical.addressLine,
            city = folderAwareCanonical.city,
            state = folderAwareCanonical.state,
            zipCode = folderAwareCanonical.zipCode,
            country = folderAwareCanonical.country,
            creditCardNumber = folderAwareCanonical.creditCardNumber,
            creditCardHolder = folderAwareCanonical.creditCardHolder,
            creditCardExpiry = folderAwareCanonical.creditCardExpiry,
            creditCardCVV = folderAwareCanonical.creditCardCVV,
            categoryId = folderAwareCanonical.categoryId,
            keepassGroupPath = if (existing.keepassDatabaseId != null) {
                folderTarget.keepassGroupPath ?: existing.keepassGroupPath
            } else {
                existing.keepassGroupPath
            },
            keepassGroupUuid = if (existing.keepassDatabaseId != null && folderTarget.keepassGroupPath != null && folderTarget.keepassGroupPath != existing.keepassGroupPath) {
                null
            } else {
                existing.keepassGroupUuid
            },
            authenticatorKey = folderAwareCanonical.authenticatorKey,
            passkeyBindings = folderAwareCanonical.passkeyBindings,
            loginType = folderAwareCanonical.loginType,
            ssoProvider = folderAwareCanonical.ssoProvider,
            ssoRefEntryId = folderAwareCanonical.ssoRefEntryId,
            bitwardenFolderId = if (existing.bitwardenVaultId != null) {
                folderTarget.bitwardenFolderId ?: existing.bitwardenFolderId
            } else {
                existing.bitwardenFolderId
            },
            updatedAt = now,
            bitwardenLocalModified = existing.bitwardenLocalModified || (existing.isBitwardenEntry() && contentChanged)
        )
    }

    private fun createPasswordClone(
        canonical: PasswordEntry,
        targetLocal: Boolean = false,
        keepassDatabaseId: Long? = null,
        bitwardenVaultId: Long? = null,
        folderTarget: ResolvedFolderTarget = ResolvedFolderTarget()
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
            categoryId = folderTarget.categoryId ?: canonical.categoryId,
            keepassDatabaseId = if (targetLocal || bitwardenVaultId != null) null else keepassDatabaseId,
            keepassGroupPath = if (targetLocal || bitwardenVaultId != null) null else folderTarget.keepassGroupPath,
            keepassEntryUuid = keepassDatabaseId?.let { UUID.randomUUID().toString() },
            keepassGroupUuid = null,
            bitwardenVaultId = if (targetLocal || keepassDatabaseId != null) null else bitwardenVaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = if (targetLocal || keepassDatabaseId != null) null else folderTarget.bitwardenFolderId,
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
            existing.keepassGroupPath != canonical.keepassGroupPath ||
            existing.bitwardenFolderId != canonical.bitwardenFolderId ||
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

    private suspend fun writeSnapshotLog(
        request: QuickMaintenanceRequest,
        payload: TimelineMaintenanceSnapshotPayload
    ) {
        val modeLabel = when (request.mode) {
            QuickMaintenanceMode.FULL_BIDIRECTIONAL -> "全数据库双向"
            QuickMaintenanceMode.TARGET_DATABASE -> "同步到目标数据库(${request.targetSourceKey})"
        }
        val changes = listOf(
            FieldChange(
                fieldName = TIMELINE_FIELD_MAINTENANCE_SNAPSHOT_PAYLOAD,
                oldValue = "",
                newValue = json.encodeToString(payload)
            )
        )
        operationLogRepository.insertLog(
            OperationLog(
                itemType = OperationLogItemType.CATEGORY.name,
                itemId = System.currentTimeMillis(),
                itemTitle = "同步合并快照 · $modeLabel",
                operationType = OperationType.UPDATE.name,
                changesJson = json.encodeToString(changes)
            )
        )
    }

    private fun resolveTargetSourceKeys(
        request: QuickMaintenanceRequest,
        keepassDatabases: List<LocalKeePassDatabase>,
        vaults: List<BitwardenVault>
    ): Set<String> {
        if (request.mode == QuickMaintenanceMode.TARGET_DATABASE) {
            return setOf(request.targetSourceKey ?: MONICA_SOURCE_KEY)
        }
        return buildSet {
            add(MONICA_SOURCE_KEY)
            keepassDatabases.forEach { add(keepassSourceKey(it.id)) }
            vaults.forEach { add(bitwardenSourceKey(it.id)) }
        }
    }

    private fun shouldUpdateExistingEntry(request: QuickMaintenanceRequest, sourceKey: String): Boolean {
        return request.mode != QuickMaintenanceMode.TARGET_DATABASE || request.targetSourceKey == sourceKey
    }

    private fun createPasswordCloneForSource(
        canonical: PasswordEntry,
        sourceKey: String,
        folderTarget: ResolvedFolderTarget
    ): PasswordEntry {
        return when {
            sourceKey == MONICA_SOURCE_KEY -> createPasswordClone(canonical, targetLocal = true, folderTarget = folderTarget)
            sourceKey.startsWith("keepass:") -> createPasswordClone(canonical, keepassDatabaseId = sourceKey.substringAfter(':').toLong(), folderTarget = folderTarget)
            sourceKey.startsWith("bitwarden:") -> createPasswordClone(canonical, bitwardenVaultId = sourceKey.substringAfter(':').toLong(), folderTarget = folderTarget)
            else -> createPasswordClone(canonical, targetLocal = true, folderTarget = folderTarget)
        }
    }

    private suspend fun buildFolderSyncContext(vaults: List<BitwardenVault>): FolderSyncContext {
        val categories = passwordRepository.getAllCategories().first()
        val folderByVault = vaults.associate { vault ->
            vault.id to bitwardenFolderDao.getFoldersByVault(vault.id)
        }
        return FolderSyncContext(categories, folderByVault)
    }

    private fun resolveFolderSeed(
        canonical: PasswordEntry,
        group: List<PasswordEntry>,
        context: FolderSyncContext
    ): FolderSeed? {
        val ordered = buildList {
            add(canonical)
            addAll(group.filter { it.id != canonical.id })
        }
        val folderName = ordered
            .mapNotNull { resolveFolderName(it, context) }
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return null

        val keepassPathByDatabase = linkedMapOf<Long, String>()
        ordered.forEach { entry ->
            val databaseId = entry.keepassDatabaseId ?: return@forEach
            val groupPath = entry.keepassGroupPath?.trim().orEmpty()
            if (groupPath.isNotBlank() && keepassPathByDatabase[databaseId].isNullOrBlank()) {
                keepassPathByDatabase[databaseId] = groupPath
            }
        }

        val bitwardenFolderByVault = linkedMapOf<Long, String>()
        ordered.forEach { entry ->
            val vaultId = entry.bitwardenVaultId ?: return@forEach
            val folderId = entry.bitwardenFolderId?.trim().orEmpty()
            if (folderId.isNotBlank() && bitwardenFolderByVault[vaultId].isNullOrBlank()) {
                bitwardenFolderByVault[vaultId] = folderId
            }
        }

        return FolderSeed(
            folderName = folderName,
            keepassPathByDatabase = keepassPathByDatabase,
            bitwardenFolderByVault = bitwardenFolderByVault
        )
    }

    private fun resolveFolderName(entry: PasswordEntry, context: FolderSyncContext): String? {
        entry.categoryId?.let { categoryId ->
            val categoryName = context.categoryNameById[categoryId]?.trim().orEmpty()
            if (categoryName.isNotBlank()) return categoryName
        }
        val keepassName = folderNameFromKeepassPath(entry.keepassGroupPath)
        if (keepassName.isNotBlank()) return keepassName
        val vaultId = entry.bitwardenVaultId
        val folderId = entry.bitwardenFolderId
        if (vaultId != null && !folderId.isNullOrBlank()) {
            return context.findBitwardenFolderName(vaultId, folderId)
        }
        return null
    }

    private suspend fun resolveFolderTargetForSource(
        sourceKey: String,
        seed: FolderSeed?,
        context: FolderSyncContext
    ): ResolvedFolderTarget {
        if (seed == null) return ResolvedFolderTarget()
        val categoryId = ensureCategoryId(seed.folderName, context)
        return when {
            sourceKey == MONICA_SOURCE_KEY -> {
                ResolvedFolderTarget(categoryId = categoryId)
            }
            sourceKey.startsWith("keepass:") -> {
                val databaseId = sourceKey.substringAfter(':').toLongOrNull()
                val groupPath = databaseId?.let { seed.keepassPathByDatabase[it] } ?: seed.folderName
                ResolvedFolderTarget(
                    categoryId = categoryId,
                    keepassGroupPath = groupPath
                )
            }
            sourceKey.startsWith("bitwarden:") -> {
                val vaultId = sourceKey.substringAfter(':').toLongOrNull()
                val folderId = if (vaultId == null) {
                    null
                } else {
                    seed.bitwardenFolderByVault[vaultId]
                        ?: ensureBitwardenFolderId(vaultId, seed.folderName, categoryId, context)
                }
                ResolvedFolderTarget(
                    categoryId = categoryId,
                    bitwardenFolderId = folderId
                )
            }
            else -> {
                ResolvedFolderTarget(categoryId = categoryId)
            }
        }
    }

    private fun folderNameFromKeepassPath(path: String?): String {
        val value = path?.trim().orEmpty()
        if (value.isBlank()) return ""
        return value
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
    }

    private suspend fun ensureCategoryId(folderName: String, context: FolderSyncContext): Long? {
        val normalized = normalizeFolderNameKey(folderName)
        if (normalized.isBlank()) return null
        context.categoryIdByNormalizedName[normalized]?.let { return it }
        val trimmedName = folderName.trim()
        val id = passwordRepository.insertCategory(Category(name = trimmedName))
        if (id > 0) {
            context.categoryIdByNormalizedName[normalized] = id
            context.categoryNameById[id] = trimmedName
            return id
        }
        return context.categoryIdByNormalizedName[normalized]
    }

    private suspend fun ensureBitwardenFolderId(
        vaultId: Long,
        folderName: String,
        categoryId: Long?,
        context: FolderSyncContext
    ): String? {
        val normalized = normalizeFolderNameKey(folderName)
        if (normalized.isBlank()) return null

        val folderIdByName = context.folderIdByVaultAndNormalizedName.getOrPut(vaultId) { mutableMapOf() }
        folderIdByName[normalized]?.let { return it }

        val folderId = UUID.randomUUID().toString()
        val trimmedName = folderName.trim()
        bitwardenFolderDao.insert(
            BitwardenFolder(
                vaultId = vaultId,
                bitwardenFolderId = folderId,
                name = trimmedName,
                encryptedName = null,
                revisionDate = Date().toInstant().toString(),
                isLocalModified = true,
                localMonicaCategoryId = categoryId
            )
        )
        folderIdByName[normalized] = folderId
        context.folderNameByVaultAndId.getOrPut(vaultId) { mutableMapOf() }[folderId] = trimmedName
        return folderId
    }

    private fun normalizeFolderNameKey(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun createSecureItemCloneForSource(canonical: SecureItem, sourceKey: String): SecureItem {
        return when {
            sourceKey == MONICA_SOURCE_KEY -> createSecureItemClone(canonical, targetLocal = true)
            sourceKey.startsWith("keepass:") -> createSecureItemClone(canonical, keepassDatabaseId = sourceKey.substringAfter(':').toLong())
            sourceKey.startsWith("bitwarden:") -> createSecureItemClone(canonical, bitwardenVaultId = sourceKey.substringAfter(':').toLong())
            else -> createSecureItemClone(canonical, targetLocal = true)
        }
    }

    private fun hasPasswordFieldConflict(group: List<PasswordEntry>): Boolean {
        if (group.size <= 1) return false
        val candidates = listOf(
            group.map { normalizeText(it.title) },
            group.map { normalizeText(it.website) },
            group.map { normalizeText(it.username) },
            group.map { normalizeText(it.notes) },
            group.map { normalizeText(it.email) },
            group.map { normalizeText(it.phone) }
        )
        return candidates.any { values -> values.filter { it.isNotBlank() }.distinct().size > 1 }
    }

    private fun hasSecureItemFieldConflict(group: List<SecureItem>): Boolean {
        if (group.size <= 1) return false
        val candidates = listOf(
            group.map { normalizeText(it.title) },
            group.map { normalizeText(it.notes) },
            group.map { normalizeText(it.itemData) }
        )
        return candidates.any { values -> values.filter { it.isNotBlank() }.distinct().size > 1 }
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
        private const val SNAPSHOT_RETENTION_DAYS = 14
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class MutableSourceStats(
    var passwordCount: Int = 0,
    var authenticatorCount: Int = 0,
    var bankCardCount: Int = 0,
    var passkeyCount: Int = 0
)

private data class ResolvedFolderTarget(
    val categoryId: Long? = null,
    val keepassGroupPath: String? = null,
    val bitwardenFolderId: String? = null
)

private data class FolderSeed(
    val folderName: String,
    val keepassPathByDatabase: Map<Long, String>,
    val bitwardenFolderByVault: Map<Long, String>
)

private class FolderSyncContext(
    categories: List<Category>,
    foldersByVault: Map<Long, List<BitwardenFolder>>
) {
    val categoryNameById: MutableMap<Long, String> = categories.associate { it.id to it.name }.toMutableMap()
    val categoryIdByNormalizedName: MutableMap<String, Long> = categories
        .mapNotNull { category ->
            val normalized = normalizeFolderNameKey(category.name)
            if (normalized.isBlank()) null else normalized to category.id
        }
        .toMap()
        .toMutableMap()

    val folderNameByVaultAndId: MutableMap<Long, MutableMap<String, String>> = foldersByVault
        .mapValues { (_, folders) -> folders.associate { it.bitwardenFolderId to it.name }.toMutableMap() }
        .toMutableMap()

    val folderIdByVaultAndNormalizedName: MutableMap<Long, MutableMap<String, String>> = foldersByVault
        .mapValues { (_, folders) ->
            folders
                .mapNotNull { folder ->
                    val normalized = normalizeFolderNameKey(folder.name)
                    if (normalized.isBlank()) null else normalized to folder.bitwardenFolderId
                }
                .toMap()
                .toMutableMap()
        }
        .toMutableMap()

    fun findBitwardenFolderName(vaultId: Long, folderId: String): String? {
        return folderNameByVaultAndId[vaultId]?.get(folderId)
    }
}

private fun normalizeFolderNameKey(value: String): String {
    return value.trim().lowercase(Locale.ROOT)
}

fun createQuickDatabaseMaintenanceEngine(
    database: PasswordDatabase,
    passwordRepository: PasswordRepository,
    secureItemRepository: SecureItemRepository,
    passkeyRepository: PasskeyRepository,
    localKeePassDatabaseDao: LocalKeePassDatabaseDao,
    bitwardenFolderDao: BitwardenFolderDao,
    bitwardenVaultDao: BitwardenVaultDao,
    securityManager: SecurityManager,
    workspaceRepository: KeePassWorkspaceRepository,
    operationLogRepository: OperationLogRepository
): QuickDatabaseMaintenanceEngine {
    return QuickDatabaseMaintenanceEngine(
        passwordRepository = passwordRepository,
        secureItemRepository = secureItemRepository,
        passkeyRepository = passkeyRepository,
        localKeePassDatabaseDao = localKeePassDatabaseDao,
        bitwardenFolderDao = bitwardenFolderDao,
        bitwardenVaultDao = bitwardenVaultDao,
        securityManager = securityManager,
        keePassBridge = KeePassCompatibilityBridge(workspaceRepository),
        operationLogRepository = operationLogRepository,
        snapshotManager = MaintenanceSnapshotManager(database)
    )
}
