package takagi.ru.monica.data.dedup

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.first
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
import takagi.ru.monica.repository.PasskeyRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager

class DedupEngine(
    private val passwordRepository: PasswordRepository,
    private val secureItemRepository: SecureItemRepository,
    private val passkeyRepository: PasskeyRepository,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao,
    private val bitwardenVaultDao: BitwardenVaultDao,
    private val securityManager: SecurityManager,
    private val ignoreStore: DedupIgnoreStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun scan(
        scope: DedupScope,
        keepassDatabaseId: Long? = null,
        bitwardenVaultId: Long? = null
    ): List<DedupCluster> {
        val keepassLookup = localKeePassDatabaseDao.getAllDatabasesSync().associateBy { it.id }
        val bitwardenLookup = bitwardenVaultDao.getAllVaults().associateBy { it.id }
        val passwordEntries = applyScopeToPasswords(
            passwordRepository.getAllPasswordEntries().first(),
            scope,
            keepassDatabaseId,
            bitwardenVaultId
        )
        val secureItems = applyScopeToSecureItems(
            secureItemRepository.getAllItems().first(),
            scope,
            keepassDatabaseId,
            bitwardenVaultId
        )
        val passkeys = applyScopeToPasskeys(
            passkeyRepository.getAllPasskeysSync(),
            scope,
            keepassDatabaseId,
            bitwardenVaultId
        )

        return buildList {
            addAll(buildExactPasswordClusters(passwordEntries, keepassLookup, bitwardenLookup))
            addAll(buildCrossSourcePasswordClusters(passwordEntries, keepassLookup, bitwardenLookup))
            addAll(
                buildSecureItemClusters(
                    secureItems,
                    ItemType.TOTP,
                    DedupClusterType.DUPLICATE_TOTP,
                    keepassLookup,
                    bitwardenLookup
                )
            )
            addAll(
                buildSecureItemClusters(
                    secureItems,
                    ItemType.BANK_CARD,
                    DedupClusterType.DUPLICATE_BANK_CARD,
                    keepassLookup,
                    bitwardenLookup
                )
            )
            addAll(
                buildSecureItemClusters(
                    secureItems,
                    ItemType.DOCUMENT,
                    DedupClusterType.DUPLICATE_DOCUMENT,
                    keepassLookup,
                    bitwardenLookup
                )
            )
            addAll(buildPasskeyClusters(passkeys, keepassLookup, bitwardenLookup))
        }
            .filterNot { ignoreStore.isIgnored(it.id) }
            .sortedWith(
                compareByDescending<DedupCluster> { it.itemCount }
                    .thenBy { it.type.ordinal }
                    .thenBy { it.keyLabel }
            )
    }

    suspend fun execute(
        cluster: DedupCluster,
        action: DedupAction,
        preferredSource: DedupPreferredSource,
        preferredKeepassDatabaseId: Long? = null,
        preferredBitwardenVaultId: Long? = null
    ): DedupActionResult {
        return when (action) {
            DedupAction.APPLY_PASSWORD_PREFERENCE -> applyPasswordPreference(
                cluster,
                preferredSource,
                preferredKeepassDatabaseId,
                preferredBitwardenVaultId
            )
            DedupAction.MOVE_LOCAL_SECURE_ITEM_COPIES_TO_TRASH -> moveLocalSecureItemCopiesToTrash(cluster)
            DedupAction.IGNORE_CLUSTER -> {
                ignoreStore.ignore(cluster.id)
                DedupActionResult(message = "已忽略该去重建议")
            }
        }
    }

    private fun buildExactPasswordClusters(
        entries: List<PasswordEntry>,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>
    ): List<DedupCluster> {
        val groups = entries.groupBy { entry ->
            listOf(
                sourceIdentity(entry),
                normalizeText(entry.title),
                normalizeText(entry.username),
                normalizeWebsite(entry.website),
                decryptComparablePassword(entry.password)
            ).joinToString("|")
        }

        return groups.values.mapNotNull { group ->
            if (group.size <= 1) return@mapNotNull null
            val refs = group.map { entry ->
                val descriptor = sourceDescriptorOf(entry, keepassLookup, bitwardenLookup)
                DedupEntityRef.PasswordRef(
                    entryId = entry.id,
                    title = entry.title.ifBlank { entry.website.ifBlank { "Untitled" } },
                    subtitle = entry.username.ifBlank { entry.website },
                    sourceKind = descriptor.kind,
                    sourceLabel = descriptor.label,
                    sourceInstanceKey = descriptor.instanceKey,
                    isLocal = entry.isLocalOnlyEntry()
                )
            }
            buildCluster(
                type = DedupClusterType.EXACT_PASSWORD_DUPLICATE,
                key = "exact_password|${group.first().id}|${group.joinToString(",") { it.id.toString() }}",
                keyLabel = buildPasswordKeyLabel(group.first()),
                refs = refs
            )
        }
    }

    private fun buildCrossSourcePasswordClusters(
        entries: List<PasswordEntry>,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>
    ): List<DedupCluster> {
        val groups = entries.groupBy { entry ->
            listOf(
                normalizeText(entry.title),
                normalizeText(entry.username),
                normalizeWebsite(entry.website)
            ).joinToString("|")
        }

        return groups.values.mapNotNull { group ->
            if (group.size <= 1) return@mapNotNull null
            val sourceInstances = group.map { sourceIdentity(it) }.toSet()
            if (sourceInstances.size <= 1) return@mapNotNull null

            val refs = group.map { entry ->
                val descriptor = sourceDescriptorOf(entry, keepassLookup, bitwardenLookup)
                DedupEntityRef.PasswordRef(
                    entryId = entry.id,
                    title = entry.title.ifBlank { entry.website.ifBlank { "Untitled" } },
                    subtitle = entry.username.ifBlank { entry.website },
                    sourceKind = descriptor.kind,
                    sourceLabel = descriptor.label,
                    sourceInstanceKey = descriptor.instanceKey,
                    isLocal = entry.isLocalOnlyEntry()
                )
            }
            buildCluster(
                type = DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR,
                key = "mirror_password|${groupsKey(group)}",
                keyLabel = buildPasswordKeyLabel(group.first()),
                refs = refs
            )
        }
    }

    private fun buildSecureItemClusters(
        items: List<SecureItem>,
        type: ItemType,
        clusterType: DedupClusterType,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>
    ): List<DedupCluster> {
        val filtered = items.filter { it.itemType == type }
        val groups = filtered.groupBy { buildSecureFingerprint(it) }

        return groups.values.mapNotNull { group ->
            if (group.size <= 1) return@mapNotNull null
            val refs = group.map { item ->
                val descriptor = sourceDescriptorOf(item, keepassLookup, bitwardenLookup)
                DedupEntityRef.SecureItemRef(
                    itemId = item.id,
                    itemType = item.itemType,
                    title = item.title.ifBlank { secureItemFallbackTitle(item) },
                    subtitle = secureItemSubtitle(item),
                    sourceKind = descriptor.kind,
                    sourceLabel = descriptor.label,
                    sourceInstanceKey = descriptor.instanceKey,
                    isLocal = isLocalItem(item)
                )
            }
            buildCluster(
                type = clusterType,
                key = "${type.name.lowercase(Locale.ROOT)}|${groupsKey(group)}",
                keyLabel = secureClusterKeyLabel(group.first()),
                refs = refs
            )
        }
    }

    private fun buildPasskeyClusters(
        passkeys: List<PasskeyEntry>,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>
    ): List<DedupCluster> {
        val groups = passkeys.groupBy { passkey ->
            "${normalizeText(passkey.rpId)}|${normalizeText(passkey.userName)}"
        }

        return groups.values.mapNotNull { group ->
            if (group.size <= 1) return@mapNotNull null
            val refs = group.map { passkey ->
                val descriptor = sourceDescriptorOf(passkey, keepassLookup, bitwardenLookup)
                DedupEntityRef.PasskeyRef(
                    credentialId = passkey.credentialId,
                    title = passkey.rpName.ifBlank { passkey.rpId },
                    subtitle = passkey.userName,
                    sourceKind = descriptor.kind,
                    sourceLabel = descriptor.label,
                    sourceInstanceKey = descriptor.instanceKey,
                    isLocal = isLocalPasskey(passkey)
                )
            }
            buildCluster(
                type = DedupClusterType.PASSKEY_ACCOUNT_CONFLICT,
                key = "passkey|${groupsKey(group)}",
                keyLabel = "${group.first().rpId} · ${group.first().userName}",
                refs = refs
            )
        }
    }

    private fun buildCluster(
        type: DedupClusterType,
        key: String,
        keyLabel: String,
        refs: List<DedupEntityRef>
    ): DedupCluster {
        val sourceKinds = refs.map { it.sourceKind }.toSet()
        val sourceDescriptors = refs
            .map { ref ->
                DedupSourceDescriptor(
                    instanceKey = ref.sourceInstanceKey,
                    kind = ref.sourceKind,
                    label = ref.sourceLabel
                )
            }
            .distinctBy { it.instanceKey }
            .sortedWith(compareBy<DedupSourceDescriptor> { it.kind.ordinal }.thenBy { it.label })
        val supportedActions = buildSupportedActions(type, refs, sourceKinds)
        return DedupCluster(
            id = stableHash("$type|$key|${refs.joinToString("|") { it.stableId }}"),
            type = type,
            keyLabel = keyLabel,
            itemCount = refs.size,
            items = refs.sortedWith(
                compareBy<DedupEntityRef> { it.sourceKind.ordinal }
                    .thenBy { it.sourceLabel }
                    .thenBy { it.title }
            ),
            sources = sourceKinds,
            sourceDescriptors = sourceDescriptors,
            supportedActions = supportedActions
        )
    }

    private fun buildSupportedActions(
        type: DedupClusterType,
        refs: List<DedupEntityRef>,
        sourceKinds: Set<DedupSourceKind>
    ): List<DedupAction> {
        val actions = mutableListOf<DedupAction>()
        when (type) {
            DedupClusterType.EXACT_PASSWORD_DUPLICATE,
            DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR -> {
                if (refs.any { it is DedupEntityRef.PasswordRef }) {
                    actions += DedupAction.APPLY_PASSWORD_PREFERENCE
                }
            }
            DedupClusterType.DUPLICATE_TOTP,
            DedupClusterType.DUPLICATE_BANK_CARD,
            DedupClusterType.DUPLICATE_DOCUMENT -> {
                if (
                    sourceKinds == setOf(DedupSourceKind.MONICA_LOCAL) &&
                    refs.any { it is DedupEntityRef.SecureItemRef && it.isLocal }
                ) {
                    actions += DedupAction.MOVE_LOCAL_SECURE_ITEM_COPIES_TO_TRASH
                }
            }
            DedupClusterType.PASSKEY_ACCOUNT_CONFLICT -> Unit
        }

        if (sourceKinds.isNotEmpty()) {
            actions += DedupAction.IGNORE_CLUSTER
        }
        return actions.distinct()
    }

    private suspend fun applyPasswordPreference(
        cluster: DedupCluster,
        preferredSource: DedupPreferredSource,
        preferredKeepassDatabaseId: Long?,
        preferredBitwardenVaultId: Long?
    ): DedupActionResult {
        val entries = cluster.items
            .filterIsInstance<DedupEntityRef.PasswordRef>()
            .mapNotNull { ref -> passwordRepository.getPasswordEntryById(ref.entryId) }
            .filter { !it.isDeleted && !it.isArchived }

        if (entries.size <= 1) {
            return DedupActionResult(message = "当前分组没有可处理的密码副本")
        }

        val keeper = if (cluster.type == DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR) {
            selectPreferredPasswordKeeper(
                entries,
                preferredSource,
                preferredKeepassDatabaseId,
                preferredBitwardenVaultId
            )
        } else {
            selectBestPassword(entries)
        }
        val idsToArchive = entries.filterNot { it.id == keeper.id }.map { it.id }

        if (idsToArchive.isEmpty()) {
            return DedupActionResult(message = "当前分组没有多余的密码副本")
        }

        passwordRepository.archivePasswordsByIds(idsToArchive)
        val keeperSource = sourceKindOf(keeper)
        val preferredSourceKind = preferredSource.toSourceKind()
        val message = when (cluster.type) {
            DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR -> {
                if (keeperSource == preferredSourceKind) {
                    "已保留${sourceLabel(keeperSource)}主项，并归档 ${idsToArchive.size} 条其他密码副本"
                } else {
                    "该分组没有${sourceLabel(preferredSourceKind)}记录，已保留${sourceLabel(keeperSource)}主项，并归档 ${idsToArchive.size} 条其他密码副本"
                }
            }
            else -> {
                "已保留 1 条${sourceLabel(keeperSource)}记录，并归档 ${idsToArchive.size} 条重复密码"
            }
        }
        return DedupActionResult(message = message, changedCount = idsToArchive.size)
    }

    private suspend fun moveLocalSecureItemCopiesToTrash(cluster: DedupCluster): DedupActionResult {
        val localItems = cluster.items
            .filterIsInstance<DedupEntityRef.SecureItemRef>()
            .filter { it.isLocal }
            .mapNotNull { ref -> secureItemRepository.getItemById(ref.itemId) }
            .filter { !it.isDeleted && isLocalItem(it) }

        if (localItems.isEmpty()) {
            return DedupActionResult(message = "没有可移入回收站的 Monica 本地条目")
        }

        val itemsToTrash = if (cluster.sources == setOf(DedupSourceKind.MONICA_LOCAL)) {
            selectRedundantSecureItems(localItems)
        } else {
            localItems
        }

        if (itemsToTrash.isEmpty()) {
            return DedupActionResult(message = "当前分组没有多余的 Monica 本地副本")
        }

        itemsToTrash.forEach { item ->
            secureItemRepository.softDeleteItem(item)
        }
        return DedupActionResult(
            message = "已将 ${itemsToTrash.size} 条 Monica 本地副本移入回收站",
            changedCount = itemsToTrash.size
        )
    }

    private fun selectPreferredPasswordKeeper(
        entries: List<PasswordEntry>,
        preferredSource: DedupPreferredSource,
        preferredKeepassDatabaseId: Long?,
        preferredBitwardenVaultId: Long?
    ): PasswordEntry {
        val preferredEntries = entries.filter { entry ->
            when (preferredSource) {
                DedupPreferredSource.MONICA_LOCAL -> entry.isLocalOnlyEntry()
                DedupPreferredSource.KEEPASS -> preferredKeepassDatabaseId?.let { id ->
                    entry.keepassDatabaseId == id
                } ?: entry.keepassDatabaseId != null
                DedupPreferredSource.BITWARDEN -> preferredBitwardenVaultId?.let { id ->
                    entry.bitwardenVaultId == id
                } ?: entry.bitwardenVaultId != null
            }
        }
        return selectBestPassword(preferredEntries.ifEmpty { entries })
    }

    private fun selectBestPassword(entries: List<PasswordEntry>): PasswordEntry {
        return entries.maxWithOrNull(
            compareBy<PasswordEntry> { if (it.isFavorite) 1 else 0 }
                .thenBy { it.notes.length }
                .thenBy { it.website.length }
                .thenBy { it.username.length }
                .thenBy { it.updatedAt.time }
        ) ?: entries.first()
    }

    private fun selectRedundantSecureItems(items: List<SecureItem>): List<SecureItem> {
        if (items.size <= 1) return emptyList()
        val keeper = items.maxWithOrNull(
            compareBy<SecureItem> { if (it.isFavorite) 1 else 0 }
                .thenBy { it.notes.length }
                .thenBy { it.title.length }
                .thenBy { it.updatedAt.time }
        ) ?: return emptyList()
        return items.filterNot { it.id == keeper.id }
    }

    private fun applyScopeToPasswords(
        entries: List<PasswordEntry>,
        scope: DedupScope,
        keepassDatabaseId: Long?,
        bitwardenVaultId: Long?
    ): List<PasswordEntry> {
        return when (scope) {
            DedupScope.ALL -> entries
            DedupScope.MONICA_LOCAL -> entries.filter { it.isLocalOnlyEntry() }
            DedupScope.KEEPASS -> keepassDatabaseId?.let { id ->
                entries.filter { it.keepassDatabaseId == id }
            } ?: entries.filter { it.isKeePassEntry() }
            DedupScope.BITWARDEN -> bitwardenVaultId?.let { id ->
                entries.filter { it.bitwardenVaultId == id }
            } ?: entries.filter { it.isBitwardenEntry() || it.bitwardenVaultId != null }
        }
    }

    private fun applyScopeToSecureItems(
        items: List<SecureItem>,
        scope: DedupScope,
        keepassDatabaseId: Long?,
        bitwardenVaultId: Long?
    ): List<SecureItem> {
        return when (scope) {
            DedupScope.ALL -> items
            DedupScope.MONICA_LOCAL -> items.filter { isLocalItem(it) }
            DedupScope.KEEPASS -> keepassDatabaseId?.let { id ->
                items.filter { it.keepassDatabaseId == id }
            } ?: items.filter { it.keepassDatabaseId != null }
            DedupScope.BITWARDEN -> bitwardenVaultId?.let { id ->
                items.filter { it.bitwardenVaultId == id }
            } ?: items.filter { it.bitwardenVaultId != null }
        }
    }

    private fun applyScopeToPasskeys(
        passkeys: List<PasskeyEntry>,
        scope: DedupScope,
        keepassDatabaseId: Long?,
        bitwardenVaultId: Long?
    ): List<PasskeyEntry> {
        return when (scope) {
            DedupScope.ALL -> passkeys
            DedupScope.MONICA_LOCAL -> passkeys.filter { isLocalPasskey(it) }
            DedupScope.KEEPASS -> keepassDatabaseId?.let { id ->
                passkeys.filter { it.keepassDatabaseId == id }
            } ?: passkeys.filter { it.keepassDatabaseId != null }
            DedupScope.BITWARDEN -> bitwardenVaultId?.let { id ->
                passkeys.filter { it.bitwardenVaultId == id }
            } ?: passkeys.filter { it.bitwardenVaultId != null }
        }
    }

    private fun sourceKindOf(entry: PasswordEntry): DedupSourceKind {
        return when {
            entry.keepassDatabaseId != null -> DedupSourceKind.KEEPASS
            entry.bitwardenVaultId != null -> DedupSourceKind.BITWARDEN
            else -> DedupSourceKind.MONICA_LOCAL
        }
    }

    private fun sourceKindOf(item: SecureItem): DedupSourceKind {
        return when {
            item.keepassDatabaseId != null -> DedupSourceKind.KEEPASS
            item.bitwardenVaultId != null -> DedupSourceKind.BITWARDEN
            else -> DedupSourceKind.MONICA_LOCAL
        }
    }

    private fun sourceKindOf(entry: PasskeyEntry): DedupSourceKind {
        return when {
            entry.keepassDatabaseId != null -> DedupSourceKind.KEEPASS
            entry.bitwardenVaultId != null -> DedupSourceKind.BITWARDEN
            else -> DedupSourceKind.MONICA_LOCAL
        }
    }

    private fun sourceIdentity(entry: PasswordEntry): String {
        return when {
            entry.keepassDatabaseId != null -> "keepass:${entry.keepassDatabaseId}"
            entry.bitwardenVaultId != null -> "bitwarden:${entry.bitwardenVaultId}"
            else -> "monica"
        }
    }

    private fun sourceDescriptorOf(
        entry: PasswordEntry,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>
    ): DedupSourceDescriptor {
        return when {
            entry.keepassDatabaseId != null -> {
                val database = keepassLookup[entry.keepassDatabaseId]
                DedupSourceDescriptor(
                    instanceKey = "keepass:${entry.keepassDatabaseId}",
                    kind = DedupSourceKind.KEEPASS,
                    label = database?.name?.takeIf { it.isNotBlank() } ?: "KeePass database"
                )
            }
            entry.bitwardenVaultId != null -> {
                val vault = bitwardenLookup[entry.bitwardenVaultId]
                val label = vault?.displayName
                    ?.takeIf { it.isNotBlank() && !it.equals("Bitwarden", ignoreCase = true) }
                    ?: vault?.email
                    ?: vault?.serverUrl?.let(::compactServerLabel)
                    ?: "Bitwarden"
                DedupSourceDescriptor(
                    instanceKey = "bitwarden:${entry.bitwardenVaultId}",
                    kind = DedupSourceKind.BITWARDEN,
                    label = label
                )
            }
            else -> DedupSourceDescriptor(
                instanceKey = "monica",
                kind = DedupSourceKind.MONICA_LOCAL,
                label = "Monica local"
            )
        }
    }

    private fun sourceDescriptorOf(
        item: SecureItem,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>
    ): DedupSourceDescriptor {
        return when {
            item.keepassDatabaseId != null -> {
                val database = keepassLookup[item.keepassDatabaseId]
                DedupSourceDescriptor(
                    instanceKey = "keepass:${item.keepassDatabaseId}",
                    kind = DedupSourceKind.KEEPASS,
                    label = database?.name?.takeIf { it.isNotBlank() } ?: "KeePass database"
                )
            }
            item.bitwardenVaultId != null -> {
                val vault = bitwardenLookup[item.bitwardenVaultId]
                val label = vault?.displayName
                    ?.takeIf { it.isNotBlank() && !it.equals("Bitwarden", ignoreCase = true) }
                    ?: vault?.email
                    ?: vault?.serverUrl?.let(::compactServerLabel)
                    ?: "Bitwarden"
                DedupSourceDescriptor(
                    instanceKey = "bitwarden:${item.bitwardenVaultId}",
                    kind = DedupSourceKind.BITWARDEN,
                    label = label
                )
            }
            else -> DedupSourceDescriptor(
                instanceKey = "monica",
                kind = DedupSourceKind.MONICA_LOCAL,
                label = "Monica local"
            )
        }
    }

    private fun sourceDescriptorOf(
        entry: PasskeyEntry,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>
    ): DedupSourceDescriptor {
        return when {
            entry.keepassDatabaseId != null -> {
                val database = keepassLookup[entry.keepassDatabaseId]
                DedupSourceDescriptor(
                    instanceKey = "keepass:${entry.keepassDatabaseId}",
                    kind = DedupSourceKind.KEEPASS,
                    label = database?.name?.takeIf { it.isNotBlank() } ?: "KeePass database"
                )
            }
            entry.bitwardenVaultId != null -> {
                val vault = bitwardenLookup[entry.bitwardenVaultId]
                val label = vault?.displayName
                    ?.takeIf { it.isNotBlank() && !it.equals("Bitwarden", ignoreCase = true) }
                    ?: vault?.email
                    ?: vault?.serverUrl?.let(::compactServerLabel)
                    ?: "Bitwarden"
                DedupSourceDescriptor(
                    instanceKey = "bitwarden:${entry.bitwardenVaultId}",
                    kind = DedupSourceKind.BITWARDEN,
                    label = label
                )
            }
            else -> DedupSourceDescriptor(
                instanceKey = "monica",
                kind = DedupSourceKind.MONICA_LOCAL,
                label = "Monica local"
            )
        }
    }

    private fun compactServerLabel(serverUrl: String): String {
        return runCatching {
            URI(serverUrl).host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: serverUrl
    }

    private fun decryptComparablePassword(value: String): String {
        if (value.isBlank()) return ""
        return runCatching { securityManager.decryptData(value) }
            .getOrDefault(value)
            .trim()
    }

    private fun buildPasswordKeyLabel(entry: PasswordEntry): String {
        val site = normalizeWebsite(entry.website)
        val user = entry.username.ifBlank { entry.title }
        return listOf(site.ifBlank { entry.title }, user)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "Password entry" }
    }

    private fun buildSecureFingerprint(item: SecureItem): String {
        val objectData = runCatching { json.parseToJsonElement(item.itemData).jsonObject }.getOrNull()
        return when (item.itemType) {
            ItemType.TOTP -> {
                val issuer = objectData?.get("issuer")?.jsonPrimitive?.contentOrNull.orEmpty()
                val account = objectData?.get("accountName")?.jsonPrimitive?.contentOrNull
                    ?: objectData?.get("account")?.jsonPrimitive?.contentOrNull
                    ?: objectData?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: parseOtpUriAccount(item.itemData)
                    ?: ""
                val secret = objectData?.get("secret")?.jsonPrimitive?.contentOrNull
                    ?: parseOtpUriSecret(item.itemData)
                    ?: ""
                listOf(
                    normalizeText(issuer),
                    normalizeText(account),
                    normalizeSecret(secret),
                    normalizeText(item.title)
                ).joinToString("|")
            }
            ItemType.BANK_CARD -> {
                val cardNumber = objectData?.get("cardNumber")?.jsonPrimitive?.contentOrNull.orEmpty()
                "${normalizeSecret(cardNumber)}|${normalizeText(item.title)}"
            }
            ItemType.DOCUMENT -> {
                val documentNumber = objectData?.get("documentNumber")?.jsonPrimitive?.contentOrNull.orEmpty()
                "${normalizeText(documentNumber)}|${normalizeText(item.title)}"
            }
            else -> normalizeText(item.title)
        }
    }

    private fun secureClusterKeyLabel(item: SecureItem): String {
        val subtitle = secureItemSubtitle(item)
        val title = item.title.ifBlank { secureItemFallbackTitle(item) }
        return listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun secureItemSubtitle(item: SecureItem): String {
        val objectData = runCatching { json.parseToJsonElement(item.itemData).jsonObject }.getOrNull()
        return when (item.itemType) {
            ItemType.TOTP -> {
                objectData?.get("accountName")?.jsonPrimitive?.contentOrNull
                    ?: objectData?.get("account")?.jsonPrimitive?.contentOrNull
                    ?: parseOtpUriAccount(item.itemData)
                    ?: ""
            }
            ItemType.BANK_CARD -> {
                val cardNumber = objectData?.get("cardNumber")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (cardNumber.length >= 4) "**** ${cardNumber.takeLast(4)}" else cardNumber
            }
            ItemType.DOCUMENT -> objectData?.get("documentNumber")?.jsonPrimitive?.contentOrNull.orEmpty()
            else -> item.notes
        }
    }

    private fun secureItemFallbackTitle(item: SecureItem): String {
        return when (item.itemType) {
            ItemType.TOTP -> "TOTP"
            ItemType.BANK_CARD -> "Bank card"
            ItemType.DOCUMENT -> "Document"
            else -> "Secure item"
        }
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

    private fun groupsKey(group: List<Any>): String {
        return group.joinToString("|") { item ->
            when (item) {
                is PasswordEntry -> "${item.id}:${normalizeText(item.title)}:${normalizeText(item.username)}:${normalizeWebsite(item.website)}"
                is SecureItem -> "${item.id}:${item.itemType.name}:${normalizeText(item.title)}"
                is PasskeyEntry -> "${item.credentialId}:${normalizeText(item.rpId)}:${normalizeText(item.userName)}"
                else -> item.toString()
            }
        }
    }

    private fun isLocalItem(item: SecureItem): Boolean {
        return item.keepassDatabaseId == null && item.bitwardenVaultId == null
    }

    private fun isLocalPasskey(entry: PasskeyEntry): Boolean {
        return entry.keepassDatabaseId == null && entry.bitwardenVaultId == null
    }

    private fun normalizeText(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeWebsite(value: String): String {
        val raw = value.trim().lowercase(Locale.ROOT)
        if (raw.isBlank()) return ""
        return raw.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    }

    private fun normalizeSecret(value: String): String {
        return value.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
    }

    private fun stableHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun DedupPreferredSource.toSourceKind(): DedupSourceKind {
        return when (this) {
            DedupPreferredSource.MONICA_LOCAL -> DedupSourceKind.MONICA_LOCAL
            DedupPreferredSource.KEEPASS -> DedupSourceKind.KEEPASS
            DedupPreferredSource.BITWARDEN -> DedupSourceKind.BITWARDEN
        }
    }

    private fun sourceLabel(source: DedupSourceKind): String {
        return when (source) {
            DedupSourceKind.MONICA_LOCAL -> "Monica"
            DedupSourceKind.KEEPASS -> "KeePass"
            DedupSourceKind.BITWARDEN -> "Bitwarden"
        }
    }
}
