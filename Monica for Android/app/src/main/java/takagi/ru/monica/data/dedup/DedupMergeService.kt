package takagi.ru.monica.data.dedup

import java.net.URI
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.LocalMdbxDatabaseDao
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.bitwarden.BitwardenVaultDao
import takagi.ru.monica.data.isLocalOnlyItem
import takagi.ru.monica.data.isLocalOnlyPasskey
import takagi.ru.monica.data.model.BillingAddressData
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.data.model.PaymentAccountData
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.CustomFieldRepository
import takagi.ru.monica.repository.PasskeyRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.util.TotpDataResolver

class DedupMergeService(
    private val passwordRepository: PasswordRepository,
    private val secureItemRepository: SecureItemRepository,
    private val passkeyRepository: PasskeyRepository,
    private val customFieldRepository: CustomFieldRepository,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao,
    private val localMdbxDatabaseDao: LocalMdbxDatabaseDao,
    private val bitwardenVaultDao: BitwardenVaultDao,
    private val securityManager: SecurityManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSourceOptions(): List<DedupMergeSourceOption> {
        val entries = passwordRepository.getAllPasswordEntries().first()
        val secureItems = secureItemRepository.getAllItems().first()
        val passkeys = passkeyRepository.getAllPasskeysSync()
        val keepassDatabases = localKeePassDatabaseDao.getAllDatabasesSync()
        val mdbxDatabases = localMdbxDatabaseDao.getAllDatabasesSnapshot()
        val bitwardenVaults = bitwardenVaultDao.getAllVaults()
        val passwordCounts = entries.groupingBy(::sourceKeyOf).eachCount()
        val secureItemCounts = secureItems.groupingBy(::sourceKeyOf).eachCount()
        val passkeyCounts = passkeys.groupingBy(::sourceKeyOf).eachCount()

        return buildList {
            add(
                DedupMergeSourceOption(
                    key = SOURCE_MONICA,
                    kind = DedupMergeSourceKind.MONICA_LOCAL,
                    label = "Monica 本地",
                    passwordCount = passwordCounts[SOURCE_MONICA] ?: 0,
                    secureItemCount = secureItemCounts[SOURCE_MONICA] ?: 0,
                    passkeyCount = passkeyCounts[SOURCE_MONICA] ?: 0
                )
            )
            mdbxDatabases.forEach { database ->
                val key = mdbxSourceKey(database.id)
                add(
                    DedupMergeSourceOption(
                        key = key,
                        kind = DedupMergeSourceKind.MDBX,
                        label = database.name.ifBlank { "MDBX ${database.id}" },
                        passwordCount = passwordCounts[key] ?: 0,
                        secureItemCount = secureItemCounts[key] ?: 0,
                        passkeyCount = passkeyCounts[key] ?: 0
                    )
                )
            }
            keepassDatabases.forEach { database ->
                val key = keepassSourceKey(database.id)
                add(
                    DedupMergeSourceOption(
                        key = key,
                        kind = DedupMergeSourceKind.KEEPASS,
                        label = database.name.ifBlank { "KeePass ${database.id}" },
                        passwordCount = passwordCounts[key] ?: 0,
                        secureItemCount = secureItemCounts[key] ?: 0,
                        passkeyCount = passkeyCounts[key] ?: 0
                    )
                )
            }
            bitwardenVaults.forEach { vault ->
                val key = bitwardenSourceKey(vault.id)
                add(
                    DedupMergeSourceOption(
                        key = key,
                        kind = DedupMergeSourceKind.BITWARDEN,
                        label = bitwardenLabel(vault),
                        passwordCount = passwordCounts[key] ?: 0,
                        secureItemCount = secureItemCounts[key] ?: 0,
                        passkeyCount = passkeyCounts[key] ?: 0
                    )
                )
            }
        }.sortedWith(compareBy<DedupMergeSourceOption> { it.kind.ordinal }.thenBy { it.label })
    }

    suspend fun getTargetOptions(): List<DedupMergeTargetOption> {
        val entries = passwordRepository.getAllPasswordEntries().first()
        val secureItems = secureItemRepository.getAllItems().first()
        val passkeys = passkeyRepository.getAllPasskeysSync()
        val mdbxDatabases = localMdbxDatabaseDao.getAllDatabasesSnapshot()
        return buildList {
            add(
                DedupMergeTargetOption(
                    target = DedupMergeTarget.MonicaLocal,
                    label = "Monica 本地",
                    passwordCount = entries.count { it.isLocalOnlyEntry() },
                    secureItemCount = secureItems.count { it.isLocalOnlyItem() },
                    passkeyCount = passkeys.count { it.isLocalOnlyPasskey() }
                )
            )
            mdbxDatabases.forEach { database ->
                add(
                    DedupMergeTargetOption(
                        target = DedupMergeTarget.MdbxDatabase(
                            databaseId = database.id,
                            label = database.name.ifBlank { "MDBX ${database.id}" }
                        ),
                        label = database.name.ifBlank { "MDBX ${database.id}" },
                        passwordCount = entries.count { it.mdbxDatabaseId == database.id },
                        secureItemCount = secureItems.count { it.mdbxDatabaseId == database.id },
                        passkeyCount = passkeys.count { it.mdbxDatabaseId == database.id }
                    )
                )
            }
        }
    }

    suspend fun buildPlan(
        selectedSourceKeys: Set<String>,
        target: DedupMergeTarget?
    ): DedupMergePlan {
        val sourceOptions = getSourceOptions()
        val selectedOptions = sourceOptions.filter { it.key in selectedSourceKeys }
        val allEntries = passwordRepository.getAllPasswordEntries().first()
        val allSecureItems = secureItemRepository.getAllItems().first()
        val allPasskeys = passkeyRepository.getAllPasskeysSync()
        val targetSourceKey = target?.sourceKey()
        val sourceEntries = allEntries.filter { sourceKeyOf(it) in selectedSourceKeys }
        val sourceSecureItems = allSecureItems.filter { sourceKeyOf(it) in selectedSourceKeys }
        val sourcePasskeys = allPasskeys.filter { sourceKeyOf(it) in selectedSourceKeys }
        val targetEntries = target
            ?.let { selectedTarget -> allEntries.filter { it.belongsToTarget(selectedTarget) } }
            .orEmpty()
        val targetSecureItems = target
            ?.let { selectedTarget -> allSecureItems.filter { it.belongsToTarget(selectedTarget) } }
            .orEmpty()
        val customFieldsByEntry = customFieldRepository.getFieldsByEntryIds(
            (sourceEntries + targetEntries).map { it.id }.distinct()
        )
        val targetExistingKeys = targetEntries
            .map(::mergeIdentityKey)
            .toSet()
        val targetExistingFingerprints = targetEntries
            .map { entry -> exactContentFingerprint(entry, customFieldsByEntry[entry.id].orEmpty()) }
            .toSet()
        val targetExistingSecureKeys = targetSecureItems
            .map(::mergeIdentityKey)
            .toSet()
        val targetExistingSecureFingerprints = targetSecureItems
            .map(::exactContentFingerprint)
            .toSet()

        val resolvedPasswords = sourceEntries
            .groupBy(::mergeIdentityKey)
            .map { (mergeKey, group) ->
                val keeper = selectBestPassword(group, customFieldsByEntry)
                val mergedEntry = mergePasswordGroup(keeper, group)
                val mergedCustomFields = mergeCustomFields(keeper, group, customFieldsByEntry)
                val existsInTarget = mergeKey in targetExistingKeys ||
                    exactContentFingerprint(mergedEntry, mergedCustomFields) in targetExistingFingerprints
                val sourceLabels = group
                    .map { entry -> labelForSourceKey(sourceKeyOf(entry), sourceOptions) }
                    .distinct()
                DedupResolvedPassword(
                    mergeKey = mergeKey,
                    entry = target?.let { buildTargetEntry(mergedEntry, it) } ?: mergedEntry.copy(id = 0),
                    customFields = mergedCustomFields,
                    sourceEntryIds = group.map { it.id },
                    sourceLabels = sourceLabels,
                    conflictFields = conflictFields(group, customFieldsByEntry),
                    existsInTarget = existsInTarget
                )
            }
            .sortedWith(
                compareBy<DedupResolvedPassword> { it.existsInTarget }
                    .thenByDescending { it.sourceEntryIds.size }
                    .thenBy { it.entry.title.lowercase(Locale.ROOT) }
                    .thenBy { it.entry.username.lowercase(Locale.ROOT) }
            )

        val resolvedSecureItems = sourceSecureItems
            .groupBy(::mergeIdentityKey)
            .map { (mergeKey, group) ->
                val keeper = selectBestSecureItem(group)
                val mergedItem = mergeSecureItemGroup(keeper, group)
                val existsInTarget = mergeKey in targetExistingSecureKeys ||
                    exactContentFingerprint(mergedItem) in targetExistingSecureFingerprints
                val sourceLabels = group
                    .map { item -> labelForSourceKey(sourceKeyOf(item), sourceOptions) }
                    .distinct()
                DedupResolvedSecureItem(
                    mergeKey = mergeKey,
                    item = target?.let { buildTargetItem(mergedItem, it) } ?: mergedItem.copy(id = 0),
                    sourceItemIds = group.map { it.id },
                    sourceLabels = sourceLabels,
                    conflictFields = conflictFields(group),
                    existsInTarget = existsInTarget
                )
            }
            .sortedWith(
                compareBy<DedupResolvedSecureItem> { it.existsInTarget }
                    .thenByDescending { it.sourceItemIds.size }
                    .thenBy { it.item.itemType.ordinal }
                    .thenBy { it.item.title.lowercase(Locale.ROOT) }
            )

        val warnings = buildList {
            if (selectedSourceKeys.isEmpty()) add("请选择至少一个源数据库")
            if (target == null) add("请选择一个目标数据库")
            if (sourceEntries.isEmpty() && sourceSecureItems.isEmpty() && selectedSourceKeys.isNotEmpty()) {
                add("选中的源数据库没有可写入的密码或安全项")
            }
            if (targetSourceKey != null && targetSourceKey in selectedSourceKeys) {
                add("目标数据库也被选为源库；目标现有条目只用于判断跳过，不会被更新或覆盖")
            }
            if (resolvedPasswords.any { it.existsInTarget }) {
                add("目标数据库已有的同类密码会跳过，不会覆盖现有条目")
            }
            if (resolvedSecureItems.any { it.existsInTarget }) {
                add("目标数据库已有的同类验证器、卡片、证件或笔记会跳过，不会覆盖")
            }
            if (sourcePasskeys.isNotEmpty()) {
                add("通行密钥包含 Android Keystore 私钥，去重合并只统计 ${sourcePasskeys.size} 条，不会自动复制")
            }
        }

        return DedupMergePlan(
            selectedSources = selectedOptions,
            target = target,
            totalSourcePasswords = sourceEntries.size,
            totalSourceSecureItems = sourceSecureItems.size,
            unsupportedSourcePasskeys = sourcePasskeys.size,
            uniquePasswords = resolvedPasswords.size,
            uniqueSecureItems = resolvedSecureItems.size,
            duplicateGroups = sourceEntries.groupBy(::mergeIdentityKey).values.count { it.size > 1 },
            duplicateSecureItemGroups = sourceSecureItems.groupBy(::mergeIdentityKey).values.count { it.size > 1 },
            targetExistingDuplicates = resolvedPasswords.count { it.existsInTarget },
            targetExistingSecureItems = resolvedSecureItems.count { it.existsInTarget },
            previewPasswords = resolvedPasswords,
            previewSecureItems = resolvedSecureItems,
            warnings = warnings
        )
    }

    suspend fun executePlan(plan: DedupMergePlan): DedupMergeExecutionResult {
        val target = plan.target ?: error("No dedup merge target selected")
        val freshPlan = buildPlan(
            selectedSourceKeys = plan.selectedSources.map { it.key }.toSet(),
            target = target
        )
        val rowsToInsert = freshPlan.previewPasswords.filterNot { it.existsInTarget }
        val secureItemsToInsert = freshPlan.previewSecureItems.filterNot { it.existsInTarget }
        if (rowsToInsert.isEmpty() && secureItemsToInsert.isEmpty()) {
            return DedupMergeExecutionResult(
                insertedPasswords = 0,
                skippedExistingPasswords = freshPlan.targetExistingDuplicates,
                skippedExistingSecureItems = freshPlan.targetExistingSecureItems,
                skippedUnsupportedPasskeys = freshPlan.unsupportedSourcePasskeys,
                failedPasswords = 0,
                targetLabel = target.label()
            )
        }

        val ids = passwordRepository.insertPasswordEntries(rowsToInsert.map { it.entry })
        val fieldsToInsert = rowsToInsert.zip(ids).flatMap { (resolved, newEntryId) ->
            resolved.customFields.map { field ->
                field.copy(id = 0, entryId = newEntryId)
            }
        }
        if (fieldsToInsert.isNotEmpty()) {
            customFieldRepository.insertFields(fieldsToInsert)
        }

        if (target is DedupMergeTarget.MdbxDatabase && ids.isNotEmpty()) {
            val persistedEntries = rowsToInsert.zip(ids).map { (resolved, newEntryId) ->
                resolved.entry.copy(id = newEntryId)
            }
            passwordRepository.updatePasswordEntries(persistedEntries)
        }

        val insertedSecureItemIds = secureItemsToInsert.mapNotNull { resolved ->
            runCatching {
                secureItemRepository.insertItem(resolved.item)
            }.getOrNull()
        }

        val failedCount = (rowsToInsert.size - ids.size).coerceAtLeast(0)
        return DedupMergeExecutionResult(
            insertedPasswords = ids.size,
            insertedSecureItems = insertedSecureItemIds.size,
            skippedExistingPasswords = freshPlan.targetExistingDuplicates,
            skippedExistingSecureItems = freshPlan.targetExistingSecureItems,
            skippedUnsupportedPasskeys = freshPlan.unsupportedSourcePasskeys,
            failedPasswords = failedCount,
            failedSecureItems = (secureItemsToInsert.size - insertedSecureItemIds.size).coerceAtLeast(0),
            targetLabel = target.label()
        )
    }

    private fun selectBestPassword(
        entries: List<PasswordEntry>,
        customFieldsByEntry: Map<Long, List<CustomField>>
    ): PasswordEntry {
        return entries.maxWithOrNull(
            compareBy<PasswordEntry> { if (it.isFavorite) 1 else 0 }
                .thenBy { fieldCompletenessScore(it, customFieldsByEntry[it.id].orEmpty()) }
                .thenBy { it.updatedAt.time }
        ) ?: entries.first()
    }

    private fun mergePasswordGroup(
        keeper: PasswordEntry,
        entries: List<PasswordEntry>
    ): PasswordEntry {
        val iconSource = entries.firstOrNull { it.customIconValue?.isNotBlank() == true }
        return keeper.copy(
            title = firstNonBlank(keeper.title, entries) { it.title },
            website = firstNonBlank(keeper.website, entries) { it.website },
            username = firstNonBlank(keeper.username, entries) { it.username },
            password = firstNonBlank(keeper.password, entries) { it.password },
            notes = firstNonBlank(keeper.notes, entries) { it.notes },
            isFavorite = entries.any { it.isFavorite },
            appPackageName = firstNonBlank(keeper.appPackageName, entries) { it.appPackageName },
            appName = firstNonBlank(keeper.appName, entries) { it.appName },
            email = firstNonBlank(keeper.email, entries) { it.email },
            phone = firstNonBlank(keeper.phone, entries) { it.phone },
            addressLine = firstNonBlank(keeper.addressLine, entries) { it.addressLine },
            city = firstNonBlank(keeper.city, entries) { it.city },
            state = firstNonBlank(keeper.state, entries) { it.state },
            zipCode = firstNonBlank(keeper.zipCode, entries) { it.zipCode },
            country = firstNonBlank(keeper.country, entries) { it.country },
            creditCardNumber = firstNonBlank(keeper.creditCardNumber, entries) { it.creditCardNumber },
            creditCardHolder = firstNonBlank(keeper.creditCardHolder, entries) { it.creditCardHolder },
            creditCardExpiry = firstNonBlank(keeper.creditCardExpiry, entries) { it.creditCardExpiry },
            creditCardCVV = firstNonBlank(keeper.creditCardCVV, entries) { it.creditCardCVV },
            authenticatorKey = firstNonBlank(keeper.authenticatorKey, entries) { it.authenticatorKey },
            passkeyBindings = firstNonBlank(keeper.passkeyBindings, entries) { it.passkeyBindings },
            sshKeyData = firstNonBlank(keeper.sshKeyData, entries) { it.sshKeyData },
            ssoProvider = firstNonBlank(keeper.ssoProvider, entries) { it.ssoProvider },
            wifiMetadata = firstNonBlank(keeper.wifiMetadata, entries) { it.wifiMetadata },
            customIconType = iconSource?.customIconType ?: keeper.customIconType,
            customIconValue = iconSource?.customIconValue ?: keeper.customIconValue,
            customIconUpdatedAt = iconSource?.customIconUpdatedAt ?: keeper.customIconUpdatedAt
        )
    }

    private fun mergeCustomFields(
        keeper: PasswordEntry,
        entries: List<PasswordEntry>,
        customFieldsByEntry: Map<Long, List<CustomField>>
    ): List<CustomField> {
        val orderedEntries = listOf(keeper) + entries.filter { it.id != keeper.id }
        val seenFingerprints = mutableSetOf<String>()
        var nextSortOrder = 0
        return orderedEntries
            .flatMap { entry ->
                customFieldsByEntry[entry.id]
                    .orEmpty()
                    .sortedWith(compareBy<CustomField> { it.sortOrder }.thenBy { it.id })
            }
            .mapNotNull { field ->
                if (field.title.isBlank()) return@mapNotNull null
                val fingerprint = listOf(
                    normalizeText(field.title),
                    field.value.trim(),
                    field.isProtected.toString()
                ).joinToString("\u0000")
                if (!seenFingerprints.add(fingerprint)) {
                    null
                } else {
                    field.copy(id = 0, entryId = 0, sortOrder = nextSortOrder++)
                }
            }
    }

    private fun firstNonBlank(
        current: String,
        entries: List<PasswordEntry>,
        selector: (PasswordEntry) -> String
    ): String {
        if (current.isNotBlank()) return current
        return entries.asSequence()
            .map(selector)
            .firstOrNull { it.isNotBlank() }
            ?: current
    }

    private fun fieldCompletenessScore(entry: PasswordEntry, customFields: List<CustomField>): Int {
        return listOf(
            entry.title,
            entry.website,
            entry.username,
            entry.password,
            entry.notes,
            entry.appPackageName,
            entry.appName,
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
            entry.creditCardCVV,
            entry.authenticatorKey,
            entry.passkeyBindings,
            entry.sshKeyData,
            entry.ssoProvider,
            entry.wifiMetadata,
            entry.customIconValue.orEmpty()
        ).count { it.isNotBlank() } + customFields.count { it.title.isNotBlank() && it.value.isNotBlank() }
    }

    private fun conflictFields(
        entries: List<PasswordEntry>,
        customFieldsByEntry: Map<Long, List<CustomField>>
    ): Set<String> {
        if (entries.size <= 1) return emptySet()
        return buildSet {
            addIfDistinct(entries) { normalizeText(it.title) }?.let { add("标题") }
            addIfDistinct(entries) { normalizeWebsite(it.website) }?.let { add("网址") }
            addIfDistinct(entries) { normalizeText(it.username) }?.let { add("用户名") }
            addIfDistinct(entries) { decryptComparablePassword(it.password) }?.let { add("密码") }
            addIfDistinct(entries) { it.notes.trim() }?.let { add("备注") }
            addIfDistinct(entries) { normalizeSecret(it.authenticatorKey) }?.let { add("验证器") }
            addIfDistinct(entries) { it.loginType.uppercase(Locale.ROOT) }?.let { add("类型") }
            val customFieldFingerprints = entries.map { entry ->
                customFieldsByEntry[entry.id]
                    .orEmpty()
                    .filter { it.title.isNotBlank() }
                    .sortedWith(compareBy({ it.title.lowercase(Locale.ROOT) }, { it.sortOrder }, { it.value }))
                    .joinToString("|") { "${it.title}:${it.value}:${it.isProtected}" }
            }.toSet()
            if (customFieldFingerprints.size > 1) add("自定义字段")
        }
    }

    private fun addIfDistinct(entries: List<PasswordEntry>, selector: (PasswordEntry) -> String): Unit? {
        return if (entries.map(selector).toSet().size > 1) Unit else null
    }

    private fun exactContentFingerprint(entry: PasswordEntry, customFields: List<CustomField>): String {
        val customFieldFingerprint = customFields
            .filter { it.title.isNotBlank() }
            .sortedWith(compareBy({ normalizeText(it.title) }, { it.value.trim() }, { it.isProtected }))
            .joinToString("\u0002") { field ->
                listOf(normalizeText(field.title), field.value.trim(), field.isProtected.toString())
                    .joinToString("\u0001")
            }
        return listOf(
            normalizeText(entry.title),
            normalizeWebsite(entry.website),
            normalizeText(entry.username),
            decryptComparablePassword(entry.password),
            entry.notes.trim(),
            normalizeText(entry.appPackageName),
            normalizeText(entry.appName),
            normalizeText(entry.email),
            entry.phone.trim(),
            entry.addressLine.trim(),
            entry.city.trim(),
            entry.state.trim(),
            entry.zipCode.trim(),
            entry.country.trim(),
            decryptComparablePassword(entry.creditCardNumber),
            entry.creditCardHolder.trim(),
            entry.creditCardExpiry.trim(),
            decryptComparablePassword(entry.creditCardCVV),
            normalizeSecret(entry.authenticatorKey),
            entry.passkeyBindings.trim(),
            entry.sshKeyData.trim(),
            entry.loginType.uppercase(Locale.ROOT).ifBlank { "PASSWORD" },
            normalizeText(entry.ssoProvider),
            entry.wifiMetadata.trim(),
            customFieldFingerprint
        ).joinToString("\u0003")
    }

    private fun buildTargetEntry(entry: PasswordEntry, target: DedupMergeTarget): PasswordEntry {
        val now = Date()
        return when (target) {
            DedupMergeTarget.MonicaLocal -> entry.copy(
                id = 0,
                createdAt = now,
                updatedAt = now,
                categoryId = null,
                boundNoteId = null,
                keepassDatabaseId = null,
                keepassGroupPath = null,
                keepassEntryUuid = null,
                keepassGroupUuid = null,
                mdbxDatabaseId = null,
                mdbxFolderId = null,
                bitwardenVaultId = null,
                bitwardenCipherId = null,
                bitwardenFolderId = null,
                bitwardenRevisionDate = null,
                bitwardenLocalModified = false,
                ssoRefEntryId = null,
                replicaGroupId = null,
                isDeleted = false,
                deletedAt = null,
                isArchived = false,
                archivedAt = null
            )
            is DedupMergeTarget.MdbxDatabase -> entry.copy(
                id = 0,
                createdAt = now,
                updatedAt = now,
                categoryId = null,
                boundNoteId = null,
                keepassDatabaseId = null,
                keepassGroupPath = null,
                keepassEntryUuid = null,
                keepassGroupUuid = null,
                mdbxDatabaseId = target.databaseId,
                mdbxFolderId = null,
                bitwardenVaultId = null,
                bitwardenCipherId = null,
                bitwardenFolderId = null,
                bitwardenRevisionDate = null,
                bitwardenLocalModified = false,
                ssoRefEntryId = null,
                replicaGroupId = null,
                isDeleted = false,
                deletedAt = null,
                isArchived = false,
                archivedAt = null
            )
        }
    }

    private fun selectBestSecureItem(items: List<SecureItem>): SecureItem {
        return items.maxWithOrNull(
            compareBy<SecureItem> { if (it.isFavorite) 1 else 0 }
                .thenBy { secureItemCompletenessScore(it) }
                .thenBy { it.updatedAt.time }
        ) ?: items.first()
    }

    private fun mergeSecureItemGroup(
        keeper: SecureItem,
        items: List<SecureItem>
    ): SecureItem {
        return keeper.copy(
            title = firstNonBlankSecure(keeper.title, items) { it.title },
            notes = firstNonBlankSecure(keeper.notes, items) { it.notes },
            itemData = firstNonBlankSecure(keeper.itemData, items) { it.itemData },
            imagePaths = firstNonBlankSecure(keeper.imagePaths, items) { it.imagePaths },
            isFavorite = items.any { it.isFavorite }
        )
    }

    private fun firstNonBlankSecure(
        current: String,
        items: List<SecureItem>,
        selector: (SecureItem) -> String
    ): String {
        if (current.isNotBlank()) return current
        return items.asSequence()
            .map(selector)
            .firstOrNull { it.isNotBlank() }
            ?: current
    }

    private fun secureItemCompletenessScore(item: SecureItem): Int {
        return listOf(
            item.title,
            item.notes,
            item.itemData,
            item.imagePaths
        ).count { it.isNotBlank() }
    }

    private fun conflictFields(items: List<SecureItem>): Set<String> {
        if (items.size <= 1) return emptySet()
        return buildSet {
            addIfDistinctSecure(items) { normalizeText(it.title) }?.let { add("标题") }
            addIfDistinctSecure(items) { it.notes.trim() }?.let { add("备注") }
            addIfDistinctSecure(items) { exactSecureItemDataFingerprint(it) }?.let { add("内容") }
            addIfDistinctSecure(items) { it.imagePaths.trim() }?.let { add("附件") }
        }
    }

    private fun addIfDistinctSecure(items: List<SecureItem>, selector: (SecureItem) -> String): Unit? {
        return if (items.map(selector).toSet().size > 1) Unit else null
    }

    private fun exactContentFingerprint(item: SecureItem): String {
        return listOf(
            item.itemType.name,
            normalizeText(item.title),
            item.notes.trim(),
            item.imagePaths.trim(),
            exactSecureItemDataFingerprint(item)
        ).joinToString("\u0003")
    }

    private fun exactSecureItemDataFingerprint(item: SecureItem): String {
        return when (item.itemType) {
            ItemType.TOTP -> decodeTotpData(item)?.let { data ->
                listOf(
                    "totp",
                    normalizeSecret(decryptComparablePassword(data.secret)),
                    normalizeText(data.issuer),
                    normalizeText(data.accountName),
                    data.period.toString(),
                    data.digits.toString(),
                    data.algorithm.uppercase(Locale.ROOT),
                    data.otpType.name,
                    data.counter.toString(),
                    decryptComparablePassword(data.pin),
                    normalizeText(data.link),
                    normalizeText(data.associatedApp),
                    data.steamFingerprint.trim(),
                    data.steamDeviceId.trim(),
                    data.steamSerialNumber.trim(),
                    data.steamSharedSecretBase64.trim(),
                    data.steamRevocationCode.trim(),
                    data.steamIdentitySecret.trim(),
                    data.steamTokenGid.trim()
                ).joinToString("\u0001")
            }
            ItemType.BANK_CARD -> decodeBankCardData(item)?.let { data ->
                listOf(
                    "bank_card",
                    decryptComparablePassword(data.cardNumber).filter { it.isDigit() },
                    normalizeText(data.cardholderName),
                    data.expiryMonth.trim(),
                    data.expiryYear.trim(),
                    decryptComparablePassword(data.cvv),
                    normalizeText(data.bankName),
                    data.cardType.name,
                    normalizeText(data.brand),
                    normalizeText(data.nickname),
                    data.validFromMonth.trim(),
                    data.validFromYear.trim(),
                    decryptComparablePassword(data.pin),
                    data.iban.trim(),
                    data.swiftBic.trim(),
                    data.routingNumber.trim(),
                    data.accountNumber.trim(),
                    data.branchCode.trim(),
                    data.currency.trim()
                ).joinToString("\u0001")
            }
            ItemType.DOCUMENT -> decodeDocumentData(item)?.let { data ->
                listOf(
                    "document",
                    data.documentType.name,
                    decryptComparablePassword(data.documentNumber).trim().uppercase(Locale.ROOT),
                    normalizeText(data.displayNameForCompare()),
                    data.issuedDate.trim(),
                    data.expiryDate.trim(),
                    normalizeText(data.issuedBy),
                    normalizeText(data.nationality),
                    data.passportNumber.trim().uppercase(Locale.ROOT),
                    data.licenseNumber.trim().uppercase(Locale.ROOT),
                    data.ssn.trim()
                ).joinToString("\u0001")
            }
            ItemType.BILLING_ADDRESS -> decodeBillingAddressData(item)?.let { data ->
                listOf(
                    "billing_address",
                    normalizeText(data.fullName),
                    normalizeText(data.company),
                    normalizeText(data.streetAddress),
                    normalizeText(data.apartment),
                    normalizeText(data.city),
                    normalizeText(data.stateProvince),
                    data.postalCode.trim().uppercase(Locale.ROOT),
                    normalizeText(data.country),
                    data.phone.filter { it.isDigit() || it == '+' },
                    normalizeText(data.email)
                ).joinToString("\u0001")
            }
            ItemType.PAYMENT_ACCOUNT -> decodePaymentAccountData(item)?.let { data ->
                listOf(
                    "payment_account",
                    data.paymentType.name,
                    normalizeText(data.provider),
                    normalizeText(data.accountName),
                    normalizeText(data.accountHolderName),
                    normalizeText(data.email),
                    data.phone.filter { it.isDigit() || it == '+' },
                    normalizeText(data.username),
                    normalizeText(data.accountId),
                    data.maskedAccountNumber.trim(),
                    data.linkedCardLast4.trim(),
                    data.iban.trim(),
                    data.swiftBic.trim(),
                    normalizeText(data.website),
                    data.currency.trim().uppercase(Locale.ROOT)
                ).joinToString("\u0001")
            }
            ItemType.NOTE -> decodeNoteData(item)?.let { data ->
                listOf(
                    "note",
                    data.content.trim(),
                    data.tags.map(::normalizeText).sorted().joinToString(","),
                    data.isMarkdown.toString()
                ).joinToString("\u0001")
            }
            ItemType.PASSWORD -> null
        } ?: listOf("raw", item.itemData.trim()).joinToString("\u0001")
    }

    private fun buildTargetItem(item: SecureItem, target: DedupMergeTarget): SecureItem {
        val now = Date()
        return when (target) {
            DedupMergeTarget.MonicaLocal -> item.copy(
                id = 0,
                createdAt = now,
                updatedAt = now,
                categoryId = null,
                keepassDatabaseId = null,
                keepassGroupPath = null,
                keepassEntryUuid = null,
                keepassGroupUuid = null,
                mdbxDatabaseId = null,
                mdbxFolderId = null,
                isDeleted = false,
                deletedAt = null,
                replicaGroupId = null,
                bitwardenVaultId = null,
                bitwardenCipherId = null,
                bitwardenFolderId = null,
                bitwardenRevisionDate = null,
                bitwardenLocalModified = false,
                syncStatus = "NONE"
            )
            is DedupMergeTarget.MdbxDatabase -> item.copy(
                id = 0,
                createdAt = now,
                updatedAt = now,
                categoryId = null,
                keepassDatabaseId = null,
                keepassGroupPath = null,
                keepassEntryUuid = null,
                keepassGroupUuid = null,
                mdbxDatabaseId = target.databaseId,
                mdbxFolderId = null,
                isDeleted = false,
                deletedAt = null,
                replicaGroupId = null,
                bitwardenVaultId = null,
                bitwardenCipherId = null,
                bitwardenFolderId = null,
                bitwardenRevisionDate = null,
                bitwardenLocalModified = false,
                syncStatus = "NONE"
            )
        }
    }

    private fun PasswordEntry.belongsToTarget(target: DedupMergeTarget): Boolean {
        return when (target) {
            DedupMergeTarget.MonicaLocal -> isLocalOnlyEntry()
            is DedupMergeTarget.MdbxDatabase -> mdbxDatabaseId == target.databaseId
        }
    }

    private fun SecureItem.belongsToTarget(target: DedupMergeTarget): Boolean {
        return when (target) {
            DedupMergeTarget.MonicaLocal -> isLocalOnlyItem()
            is DedupMergeTarget.MdbxDatabase -> mdbxDatabaseId == target.databaseId
        }
    }

    private fun mergeIdentityKey(entry: PasswordEntry): String {
        val site = normalizeWebsite(entry.website)
        val username = normalizeText(entry.username)
        val title = normalizeText(entry.title)
        val packageName = normalizeText(entry.appPackageName)
        val loginType = entry.loginType.uppercase(Locale.ROOT).ifBlank { "PASSWORD" }
        return when {
            site.isNotBlank() && username.isNotBlank() -> "site|$loginType|$site|$username"
            packageName.isNotBlank() && username.isNotBlank() -> "app|$loginType|$packageName|$username"
            title.isNotBlank() && username.isNotBlank() -> "title_user|$loginType|$title|$username"
            else -> "entry|${entry.id}"
        }
    }

    private fun mergeIdentityKey(item: SecureItem): String {
        return when (item.itemType) {
            ItemType.TOTP -> {
                val data = decodeTotpData(item)
                val secret = data?.secret
                    ?.let(::decryptComparablePassword)
                    ?.let(::normalizeSecret)
                    .orEmpty()
                val issuer = data?.issuer?.let(::normalizeText).orEmpty()
                val accountName = data?.accountName?.let(::normalizeText).orEmpty()
                when {
                    secret.isNotBlank() -> "totp_secret|$secret"
                    issuer.isNotBlank() && accountName.isNotBlank() -> "totp_account|$issuer|$accountName"
                    else -> "secure_exact|${exactContentFingerprint(item)}"
                }
            }
            ItemType.BANK_CARD -> {
                val cardNumber = decodeBankCardData(item)
                    ?.cardNumber
                    ?.let(::decryptComparablePassword)
                    ?.filter { it.isDigit() }
                    .orEmpty()
                if (cardNumber.isNotBlank()) {
                    "bank_card|$cardNumber"
                } else {
                    "secure_exact|${exactContentFingerprint(item)}"
                }
            }
            ItemType.DOCUMENT -> {
                val data = decodeDocumentData(item)
                val documentNumber = data
                    ?.documentNumber
                    ?.let(::decryptComparablePassword)
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    .orEmpty()
                if (documentNumber.isNotBlank()) {
                    "document|${data?.documentType?.name.orEmpty()}|$documentNumber"
                } else {
                    "secure_exact|${exactContentFingerprint(item)}"
                }
            }
            ItemType.BILLING_ADDRESS -> {
                val data = decodeBillingAddressData(item)
                val addressKey = listOf(
                    data?.fullName?.let(::normalizeText).orEmpty(),
                    data?.streetAddress?.let(::normalizeText).orEmpty(),
                    data?.apartment?.let(::normalizeText).orEmpty(),
                    data?.city?.let(::normalizeText).orEmpty(),
                    data?.stateProvince?.let(::normalizeText).orEmpty(),
                    data?.postalCode?.trim()?.uppercase(Locale.ROOT).orEmpty(),
                    data?.country?.let(::normalizeText).orEmpty()
                ).joinToString("|")
                if (addressKey.replace("|", "").isNotBlank()) {
                    "billing_address|$addressKey"
                } else {
                    "secure_exact|${exactContentFingerprint(item)}"
                }
            }
            ItemType.PAYMENT_ACCOUNT -> {
                val data = decodePaymentAccountData(item)
                val accountKey = listOf(
                    data?.provider?.let(::normalizeText).orEmpty(),
                    data?.accountName?.let(::normalizeText).orEmpty(),
                    data?.email?.let(::normalizeText).orEmpty(),
                    data?.username?.let(::normalizeText).orEmpty(),
                    data?.accountId?.let(::normalizeText).orEmpty(),
                    data?.maskedAccountNumber?.trim().orEmpty(),
                    data?.linkedCardLast4?.trim().orEmpty()
                ).joinToString("|")
                if (accountKey.replace("|", "").isNotBlank()) {
                    "payment_account|$accountKey"
                } else {
                    "secure_exact|${exactContentFingerprint(item)}"
                }
            }
            ItemType.NOTE,
            ItemType.PASSWORD -> "secure_exact|${exactContentFingerprint(item)}"
        }
    }

    private fun sourceKeyOf(entry: PasswordEntry): String {
        return when {
            entry.mdbxDatabaseId != null -> mdbxSourceKey(entry.mdbxDatabaseId)
            entry.keepassDatabaseId != null -> keepassSourceKey(entry.keepassDatabaseId)
            entry.bitwardenVaultId != null -> bitwardenSourceKey(entry.bitwardenVaultId)
            else -> SOURCE_MONICA
        }
    }

    private fun sourceKeyOf(item: SecureItem): String {
        return when {
            item.mdbxDatabaseId != null -> mdbxSourceKey(item.mdbxDatabaseId)
            item.keepassDatabaseId != null -> keepassSourceKey(item.keepassDatabaseId)
            item.bitwardenVaultId != null -> bitwardenSourceKey(item.bitwardenVaultId)
            else -> SOURCE_MONICA
        }
    }

    private fun sourceKeyOf(entry: PasskeyEntry): String {
        return when {
            entry.mdbxDatabaseId != null -> mdbxSourceKey(entry.mdbxDatabaseId)
            entry.keepassDatabaseId != null -> keepassSourceKey(entry.keepassDatabaseId)
            entry.bitwardenVaultId != null -> bitwardenSourceKey(entry.bitwardenVaultId)
            else -> SOURCE_MONICA
        }
    }

    private fun labelForSourceKey(
        sourceKey: String,
        sourceOptions: List<DedupMergeSourceOption>
    ): String {
        return sourceOptions.firstOrNull { it.key == sourceKey }?.label ?: when {
            sourceKey.startsWith("mdbx:") -> "MDBX"
            sourceKey.startsWith("keepass:") -> "KeePass"
            sourceKey.startsWith("bitwarden:") -> "Bitwarden"
            else -> "Monica 本地"
        }
    }

    private fun bitwardenLabel(vault: BitwardenVault): String {
        return vault.displayName
            ?.takeIf { it.isNotBlank() && !it.equals("Bitwarden", ignoreCase = true) }
            ?: vault.email.takeIf { it.isNotBlank() }
            ?: compactServerLabel(vault.serverUrl)
            ?: "Bitwarden ${vault.id}"
    }

    private fun compactServerLabel(serverUrl: String): String? {
        return runCatching {
            URI(serverUrl).host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun DedupMergeTarget.label(): String {
        return when (this) {
            DedupMergeTarget.MonicaLocal -> "Monica 本地"
            is DedupMergeTarget.MdbxDatabase -> label
        }
    }

    private fun DedupMergeTarget.sourceKey(): String {
        return when (this) {
            DedupMergeTarget.MonicaLocal -> SOURCE_MONICA
            is DedupMergeTarget.MdbxDatabase -> mdbxSourceKey(databaseId)
        }
    }

    private fun normalizeText(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeWebsite(value: String): String {
        val raw = value.trim().lowercase(Locale.ROOT)
        if (raw.isBlank()) return ""
        return raw
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun normalizeSecret(value: String): String {
        return value.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
    }

    private fun decryptComparablePassword(value: String): String {
        if (value.isBlank()) return ""
        return runCatching { securityManager.decryptData(value) }
            .getOrDefault(value)
            .trim()
    }

    private fun decodeTotpData(item: SecureItem): TotpData? {
        return TotpDataResolver.parseStoredItemData(
            itemData = item.itemData,
            fallbackIssuer = item.title,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        )
    }

    private fun decodeBankCardData(item: SecureItem): BankCardData? {
        return CardWalletDataCodec.parseBankCardData(
            raw = item.itemData,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        )
    }

    private fun decodeDocumentData(item: SecureItem): DocumentData? {
        return CardWalletDataCodec.parseDocumentData(
            raw = item.itemData,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        )
    }

    private fun decodeBillingAddressData(item: SecureItem): BillingAddressData? {
        return CardWalletDataCodec.parseBillingAddressData(
            raw = item.itemData,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        )
    }

    private fun decodePaymentAccountData(item: SecureItem): PaymentAccountData? {
        return CardWalletDataCodec.parsePaymentAccountData(
            raw = item.itemData,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        )
    }

    private fun decodeNoteData(item: SecureItem): NoteData? {
        return runCatching { json.decodeFromString<NoteData>(item.itemData) }.getOrNull()
    }

    private fun DocumentData.displayNameForCompare(): String {
        return listOf(firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { fullName }
    }

    private companion object {
        const val SOURCE_MONICA = "monica"

        fun mdbxSourceKey(databaseId: Long): String = "mdbx:$databaseId"
        fun keepassSourceKey(databaseId: Long): String = "keepass:$databaseId"
        fun bitwardenSourceKey(vaultId: Long): String = "bitwarden:$vaultId"
    }
}
