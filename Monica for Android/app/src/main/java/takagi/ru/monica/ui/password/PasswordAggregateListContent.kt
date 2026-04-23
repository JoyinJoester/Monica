package takagi.ru.monica.ui.password

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.model.displayFullName
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.TotpViewModel

data class PasswordListAggregateConfig(
    val visibleContentTypes: List<PasswordPageContentType>,
    val selectedContentTypes: Set<PasswordPageContentType>,
    val onToggleContentType: (PasswordPageContentType) -> Unit,
    val totpViewModel: TotpViewModel,
    val bankCardViewModel: BankCardViewModel,
    val documentViewModel: DocumentViewModel,
    val noteViewModel: NoteViewModel,
    val passkeyViewModel: PasskeyViewModel,
    val onOpenTotp: (Long) -> Unit,
    val onOpenBankCard: (Long) -> Unit,
    val onOpenDocument: (Long) -> Unit,
    val onOpenNote: (Long?) -> Unit,
    val onOpenPasskey: (Long) -> Unit
)

internal data class PasswordAggregateListItemUi(
    val key: String,
    val entry: PasswordEntry,
    val type: PasswordPageContentType,
    val badgeText: String,
    val badgeColor: Color,
    val sortTime: Long,
    val secureItemId: Long? = null,
    val passkeyRecordId: Long? = null,
    val isDocument: Boolean = false
)

internal data class PasswordAggregateCardStyle(
    val iconCardsEnabled: Boolean,
    val unmatchedIconHandlingStrategy: UnmatchedIconHandlingStrategy,
    val passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode,
    val passwordCardDisplayFields: List<PasswordCardDisplayField>,
    val showAuthenticator: Boolean,
    val hideOtherContentWhenAuthenticator: Boolean,
    val totpTimeOffsetSeconds: Int,
    val smoothAuthenticatorProgress: Boolean
)

internal fun buildPasswordAggregateItems(
    selectedContentTypes: Set<PasswordPageContentType>,
    bankCards: List<SecureItem>,
    documents: List<SecureItem>,
    notes: List<SecureItem>,
    totpItems: List<SecureItem>,
    passkeys: List<PasskeyEntry>,
    searchQuery: String,
    categoryFilter: CategoryFilter
): List<PasswordAggregateListItemUi> {
    val items = mutableListOf<PasswordAggregateListItemUi>()
    appendCardItems(items, selectedContentTypes, bankCards, documents, searchQuery, categoryFilter)
    appendNoteItems(items, selectedContentTypes, notes, searchQuery, categoryFilter)
    appendAuthenticatorItems(items, selectedContentTypes, totpItems, searchQuery, categoryFilter)
    appendPasskeyItems(items, selectedContentTypes, passkeys, searchQuery, categoryFilter)
    return items.sortedByDescending(PasswordAggregateListItemUi::sortTime)
}

internal fun resolveNonEmptyAggregateContentTypes(
    configuredTypes: List<PasswordPageContentType>,
    bankCards: List<SecureItem>,
    documents: List<SecureItem>,
    notes: List<SecureItem>,
    totpItems: List<SecureItem>,
    passkeys: List<PasskeyEntry>,
    categoryFilter: CategoryFilter
): List<PasswordPageContentType> {
    return configuredTypes.filter { type ->
        when (type) {
            PasswordPageContentType.PASSWORD -> true
            PasswordPageContentType.CARD_WALLET ->
                bankCards.any { it.matchesAggregateCategory(categoryFilter) } ||
                    documents.any { it.matchesAggregateCategory(categoryFilter) }
            PasswordPageContentType.NOTE ->
                notes.any { it.matchesAggregateCategory(categoryFilter) }
            PasswordPageContentType.AUTHENTICATOR ->
                totpItems.any { item ->
                    val data = decodeJson<TotpData>(item.itemData) ?: return@any false
                    !item.isDeleted &&
                        data.boundPasswordId == null &&
                        item.matchesAggregateCategory(categoryFilter, data.categoryId)
                }
            PasswordPageContentType.PASSKEY ->
                passkeys.any { passkey ->
                    passkey.boundPasswordId == null &&
                        passkey.matchesAggregateCategory(categoryFilter)
                }
        }
    }
}

internal fun filterPasswordAggregateItemsByQuickFilters(
    items: List<PasswordAggregateListItemUi>,
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
    quickFilterFavorite: Boolean,
    quickFilter2fa: Boolean,
    quickFilterNotes: Boolean,
    quickFilterUncategorized: Boolean,
    quickFilterLocalOnly: Boolean,
    quickFilterManualStackOnly: Boolean,
    quickFilterNeverStack: Boolean,
    quickFilterUnstacked: Boolean,
    effectiveStackCardMode: StackCardMode,
    manualStackedKeys: Set<String> = emptySet()
): List<PasswordAggregateListItemUi> {
    var filtered = items

    if (quickFilterFavorite && PasswordListQuickFilterItem.FAVORITE in configuredQuickFilterItems) {
        filtered = filtered.filter { it.entry.isFavorite }
    }
    if (quickFilter2fa && PasswordListQuickFilterItem.TWO_FA in configuredQuickFilterItems) {
        filtered = filtered.filter {
            it.type == PasswordPageContentType.AUTHENTICATOR || it.entry.authenticatorKey.isNotBlank()
        }
    }
    if (quickFilterNotes && PasswordListQuickFilterItem.NOTES in configuredQuickFilterItems) {
        filtered = filtered.filter { it.entry.notes.isNotBlank() }
    }
    if (quickFilterUncategorized && PasswordListQuickFilterItem.UNCATEGORIZED in configuredQuickFilterItems) {
        filtered = filtered.filter { it.entry.categoryId == null }
    }
    if (quickFilterLocalOnly && PasswordListQuickFilterItem.LOCAL_ONLY in configuredQuickFilterItems) {
        filtered = filtered.filter { it.entry.isLocalOnlyEntry() }
    }
    if (quickFilterManualStackOnly && PasswordListQuickFilterItem.MANUAL_STACK_ONLY in configuredQuickFilterItems) {
        filtered = filtered.filter { it.key in manualStackedKeys }
    }
    if (quickFilterNeverStack && PasswordListQuickFilterItem.NEVER_STACK in configuredQuickFilterItems) {
        filtered = emptyList()
    }
    if (
        quickFilterUnstacked &&
        PasswordListQuickFilterItem.UNSTACKED in configuredQuickFilterItems &&
        effectiveStackCardMode != StackCardMode.ALWAYS_EXPANDED
    ) {
        filtered = filtered.filter { it.key !in manualStackedKeys }
    }

    return filtered
}

private fun appendCardItems(
    items: MutableList<PasswordAggregateListItemUi>,
    selectedContentTypes: Set<PasswordPageContentType>,
    bankCards: List<SecureItem>,
    documents: List<SecureItem>,
    searchQuery: String,
    categoryFilter: CategoryFilter
) {
    if (!selectedContentTypes.contains(PasswordPageContentType.CARD_WALLET)) return

    bankCards
        .filter { it.matchesAggregateCategory(categoryFilter) && it.matchesAggregateQuery(searchQuery) }
        .forEach { item ->
            val data = decodeJson<BankCardData>(item.itemData)
            items += PasswordAggregateListItemUi(
                key = "bank:${item.id}",
                entry = item.toAggregatePasswordEntry(
                    subtitlePrimary = data?.bankName.orEmpty(),
                    subtitleSecondary = data?.cardholderName.orEmpty()
                ),
                type = PasswordPageContentType.CARD_WALLET,
                badgeText = "card",
                badgeColor = Color(0xFF43A047),
                sortTime = item.updatedAt.time,
                secureItemId = item.id
            )
        }

    documents
        .filter { it.matchesAggregateCategory(categoryFilter) && it.matchesAggregateQuery(searchQuery) }
        .forEach { item ->
            val data = decodeJson<DocumentData>(item.itemData)
            items += PasswordAggregateListItemUi(
                key = "document:${item.id}",
                entry = item.toAggregatePasswordEntry(
                    subtitlePrimary = data?.displayFullName().orEmpty(),
                    subtitleSecondary = data?.documentNumber.orEmpty()
                ),
                type = PasswordPageContentType.CARD_WALLET,
                badgeText = "card",
                badgeColor = Color(0xFF43A047),
                sortTime = item.updatedAt.time,
                secureItemId = item.id,
                isDocument = true
            )
        }
}

private fun appendNoteItems(
    items: MutableList<PasswordAggregateListItemUi>,
    selectedContentTypes: Set<PasswordPageContentType>,
    notes: List<SecureItem>,
    searchQuery: String,
    categoryFilter: CategoryFilter
) {
    if (!selectedContentTypes.contains(PasswordPageContentType.NOTE)) return

    notes
        .filter { it.matchesAggregateCategory(categoryFilter) && it.matchesAggregateQuery(searchQuery) }
        .forEach { item ->
            val decodedNote = NoteContentCodec.decodeFromItem(item)
            items += PasswordAggregateListItemUi(
                key = "note:${item.id}",
                entry = item.toAggregatePasswordEntry(
                    notesOverride = NoteContentCodec.toPlainPreview(
                        decodedNote.content,
                        decodedNote.isMarkdown
                    ).replace("\n", " ").trim()
                ),
                type = PasswordPageContentType.NOTE,
                badgeText = "note",
                badgeColor = Color(0xFFFB8C00),
                sortTime = item.updatedAt.time,
                secureItemId = item.id
            )
        }
}

private fun appendAuthenticatorItems(
    items: MutableList<PasswordAggregateListItemUi>,
    selectedContentTypes: Set<PasswordPageContentType>,
    totpItems: List<SecureItem>,
    searchQuery: String,
    categoryFilter: CategoryFilter
) {
    if (!selectedContentTypes.contains(PasswordPageContentType.AUTHENTICATOR)) return

    totpItems
        .mapNotNull { item ->
            val data = decodeJson<TotpData>(item.itemData) ?: return@mapNotNull null
            if (item.isDeleted || data.boundPasswordId != null) return@mapNotNull null
            if (!item.matchesAggregateCategory(categoryFilter, data.categoryId)) return@mapNotNull null
            if (!item.matchesAggregateQuery(searchQuery, data.issuer, data.accountName)) return@mapNotNull null
            PasswordAggregateListItemUi(
                key = "totp:${item.id}",
                entry = item.toAggregatePasswordEntry(
                    subtitlePrimary = data.issuer,
                    subtitleSecondary = data.accountName,
                    authenticatorKey = TotpDataResolver.toBitwardenPayload(item.title, data),
                    websiteOverride = data.issuer
                ),
                type = PasswordPageContentType.AUTHENTICATOR,
                badgeText = "2FA",
                badgeColor = Color(0xFFE53935),
                sortTime = item.updatedAt.time,
                secureItemId = item.id
            )
        }
        .forEach(items::add)
}

private fun appendPasskeyItems(
    items: MutableList<PasswordAggregateListItemUi>,
    selectedContentTypes: Set<PasswordPageContentType>,
    passkeys: List<PasskeyEntry>,
    searchQuery: String,
    categoryFilter: CategoryFilter
) {
    if (!selectedContentTypes.contains(PasswordPageContentType.PASSKEY)) return

    passkeys
        .filter { it.boundPasswordId == null }
        .filter { it.matchesAggregateCategory(categoryFilter) && it.matchesAggregateQuery(searchQuery) }
        .forEach { passkey ->
            items += PasswordAggregateListItemUi(
                key = "passkey:${passkey.id.takeIf { it > 0L } ?: passkey.credentialId}",
                entry = passkey.toAggregatePasswordEntry(),
                type = PasswordPageContentType.PASSKEY,
                badgeText = "passkey",
                badgeColor = Color(0xFF8E24AA),
                sortTime = passkey.lastUsedAt,
                passkeyRecordId = passkey.id.takeIf { it > 0L }
            )
        }
}

private fun SecureItem.toAggregatePasswordEntry(
    subtitlePrimary: String = "",
    subtitleSecondary: String = "",
    notesOverride: String = notes,
    authenticatorKey: String = "",
    websiteOverride: String = ""
): PasswordEntry {
    return PasswordEntry(
        id = id,
        title = title,
        website = websiteOverride,
        username = listOf(subtitlePrimary, subtitleSecondary)
            .filter(String::isNotBlank)
            .joinToString(" · "),
        password = "",
        notes = notesOverride,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFavorite = isFavorite,
        sortOrder = sortOrder,
        categoryId = categoryId,
        keepassDatabaseId = keepassDatabaseId,
        keepassGroupPath = keepassGroupPath,
        keepassEntryUuid = keepassEntryUuid,
        keepassGroupUuid = keepassGroupUuid,
        authenticatorKey = authenticatorKey,
        bitwardenVaultId = bitwardenVaultId,
        bitwardenCipherId = bitwardenCipherId,
        bitwardenFolderId = bitwardenFolderId,
        bitwardenRevisionDate = bitwardenRevisionDate,
        bitwardenLocalModified = bitwardenLocalModified
    )
}

private fun PasskeyEntry.toAggregatePasswordEntry(): PasswordEntry {
    return PasswordEntry(
        id = credentialId.hashCode().toLong(),
        title = rpName.ifBlank { rpId },
        website = rpId,
        username = userDisplayName.ifBlank { userName },
        password = "",
        notes = notes,
        createdAt = java.util.Date(createdAt),
        updatedAt = java.util.Date(lastUsedAt),
        categoryId = categoryId,
        keepassDatabaseId = keepassDatabaseId,
        keepassGroupPath = keepassGroupPath,
        bitwardenVaultId = bitwardenVaultId,
        bitwardenCipherId = bitwardenCipherId,
        bitwardenFolderId = bitwardenFolderId
    )
}

private fun SecureItem.matchesAggregateCategory(
    filter: CategoryFilter,
    effectiveCategoryId: Long? = categoryId
): Boolean {
    if (isDeleted) return false
    return when (filter) {
        is CategoryFilter.All -> true
        is CategoryFilter.Archived -> false
        is CategoryFilter.Local -> keepassDatabaseId == null && bitwardenVaultId == null
        is CategoryFilter.LocalOnly -> keepassDatabaseId == null && bitwardenVaultId == null
        is CategoryFilter.Starred -> isFavorite
        is CategoryFilter.Uncategorized -> effectiveCategoryId == null
        is CategoryFilter.LocalStarred ->
            keepassDatabaseId == null && bitwardenVaultId == null && isFavorite
        is CategoryFilter.LocalUncategorized ->
            keepassDatabaseId == null && bitwardenVaultId == null && effectiveCategoryId == null
        is CategoryFilter.Custom ->
            effectiveCategoryId == filter.categoryId &&
                keepassDatabaseId == null &&
                bitwardenVaultId == null
        is CategoryFilter.KeePassDatabase -> keepassDatabaseId == filter.databaseId
        is CategoryFilter.KeePassGroupFilter ->
            keepassDatabaseId == filter.databaseId && keepassGroupPath == filter.groupPath
        is CategoryFilter.KeePassDatabaseStarred ->
            keepassDatabaseId == filter.databaseId && isFavorite
        is CategoryFilter.KeePassDatabaseUncategorized ->
            keepassDatabaseId == filter.databaseId && effectiveCategoryId == null
        is CategoryFilter.BitwardenVault -> bitwardenVaultId == filter.vaultId
        is CategoryFilter.BitwardenFolderFilter ->
            bitwardenVaultId == filter.vaultId && bitwardenFolderId == filter.folderId
        is CategoryFilter.BitwardenVaultStarred ->
            bitwardenVaultId == filter.vaultId && isFavorite
        is CategoryFilter.BitwardenVaultUncategorized ->
            bitwardenVaultId == filter.vaultId && effectiveCategoryId == null
    }
}

private fun PasskeyEntry.matchesAggregateCategory(filter: CategoryFilter): Boolean {
    return when (filter) {
        is CategoryFilter.All -> true
        is CategoryFilter.Archived -> false
        is CategoryFilter.Local -> keepassDatabaseId == null && bitwardenVaultId == null
        is CategoryFilter.LocalOnly -> keepassDatabaseId == null && bitwardenVaultId == null
        is CategoryFilter.Starred -> false
        is CategoryFilter.Uncategorized -> categoryId == null
        is CategoryFilter.LocalStarred -> false
        is CategoryFilter.LocalUncategorized ->
            keepassDatabaseId == null && bitwardenVaultId == null && categoryId == null
        is CategoryFilter.Custom ->
            categoryId == filter.categoryId &&
                keepassDatabaseId == null &&
                bitwardenVaultId == null
        is CategoryFilter.KeePassDatabase -> keepassDatabaseId == filter.databaseId
        is CategoryFilter.KeePassGroupFilter ->
            keepassDatabaseId == filter.databaseId && keepassGroupPath == filter.groupPath
        is CategoryFilter.KeePassDatabaseStarred -> false
        is CategoryFilter.KeePassDatabaseUncategorized ->
            keepassDatabaseId == filter.databaseId && categoryId == null
        is CategoryFilter.BitwardenVault -> bitwardenVaultId == filter.vaultId
        is CategoryFilter.BitwardenFolderFilter ->
            bitwardenVaultId == filter.vaultId && bitwardenFolderId == filter.folderId
        is CategoryFilter.BitwardenVaultStarred -> false
        is CategoryFilter.BitwardenVaultUncategorized ->
            bitwardenVaultId == filter.vaultId && categoryId == null
    }
}

private fun SecureItem.matchesAggregateQuery(
    query: String,
    vararg extraValues: String?
): Boolean {
    if (query.isBlank()) return true
    val candidates = buildList {
        add(title)
        add(notes)
        extraValues.filterNotNull().forEach(::add)
    }
    return candidates.any { it.contains(query, ignoreCase = true) }
}

private fun PasskeyEntry.matchesAggregateQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return listOf(rpName, rpId, userName, userDisplayName, notes)
        .any { it.contains(query, ignoreCase = true) }
}

private fun otpTypeBadge(otpType: OtpType): String = when (otpType) {
    OtpType.HOTP -> "HOTP"
    OtpType.STEAM -> "Steam"
    OtpType.YANDEX -> "Yandex"
    OtpType.MOTP -> "mOTP"
    OtpType.TOTP -> "TOTP"
}

private inline fun <reified T> decodeJson(raw: String): T? {
    return runCatching { Json.decodeFromString<T>(raw) }.getOrNull()
}
