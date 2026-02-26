package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.repository.CustomFieldRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.utils.KeePassEntryData
import takagi.ru.monica.utils.KeePassKdbxService
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date
import java.net.URI
import java.util.Locale
import java.util.UUID

import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.bitwarden.BitwardenFolder

sealed class CategoryFilter {
    object All : CategoryFilter()
    object Local : CategoryFilter() // Pure local view (Monica)
    object LocalOnly : CategoryFilter() // Local entries that have no matching item in Bitwarden
    object Starred : CategoryFilter()
    object Uncategorized : CategoryFilter()
    object LocalStarred : CategoryFilter()
    object LocalUncategorized : CategoryFilter()
    data class Custom(val categoryId: Long) : CategoryFilter()
    data class KeePassDatabase(val databaseId: Long) : CategoryFilter()
    data class KeePassGroupFilter(val databaseId: Long, val groupPath: String) : CategoryFilter()
    data class KeePassDatabaseStarred(val databaseId: Long) : CategoryFilter()
    data class KeePassDatabaseUncategorized(val databaseId: Long) : CategoryFilter()
    data class BitwardenVault(val vaultId: Long) : CategoryFilter()
    data class BitwardenFolderFilter(val folderId: String, val vaultId: Long) : CategoryFilter()
    data class BitwardenVaultStarred(val vaultId: Long) : CategoryFilter()
    data class BitwardenVaultUncategorized(val vaultId: Long) : CategoryFilter()
}

/**
 * ViewModel for password management
 */
class PasswordViewModel(
    private val repository: PasswordRepository,
    private val securityManager: SecurityManager,
    private val secureItemRepository: SecureItemRepository? = null,
    private val customFieldRepository: CustomFieldRepository? = null,
    context: Context? = null,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null
) : ViewModel() {
    private val decryptLock = Any()

    companion object {
        private const val SAVED_FILTER_ALL = "all"
        private const val SAVED_FILTER_LOCAL = "local"
        private const val SAVED_FILTER_LOCAL_ONLY = "local_only"
        private const val SAVED_FILTER_STARRED = "starred"
        private const val SAVED_FILTER_UNCATEGORIZED = "uncategorized"
        private const val SAVED_FILTER_LOCAL_STARRED = "local_starred"
        private const val SAVED_FILTER_LOCAL_UNCATEGORIZED = "local_uncategorized"
        private const val SAVED_FILTER_CUSTOM = "custom"
        private const val SAVED_FILTER_KEEPASS_DATABASE = "keepass_database"
        private const val SAVED_FILTER_KEEPASS_GROUP = "keepass_group"
        private const val SAVED_FILTER_KEEPASS_DATABASE_STARRED = "keepass_database_starred"
        private const val SAVED_FILTER_KEEPASS_DATABASE_UNCATEGORIZED = "keepass_database_uncategorized"
        private const val SAVED_FILTER_BITWARDEN_VAULT = "bitwarden_vault"
        private const val SAVED_FILTER_BITWARDEN_FOLDER = "bitwarden_folder"
        private const val SAVED_FILTER_BITWARDEN_VAULT_STARRED = "bitwarden_vault_starred"
        private const val SAVED_FILTER_BITWARDEN_VAULT_UNCATEGORIZED = "bitwarden_vault_uncategorized"
        private const val MONICA_MANUAL_STACK_GROUP_FIELD_TITLE = "__monica_manual_stack_group"
        private const val MONICA_NO_STACK_FIELD_TITLE = "__monica_no_stack"
    }

    enum class ManualStackMode {
        STACK,
        AUTO_STACK,
        NEVER_STACK
    }
    
    private val passwordHistoryManager: PasswordHistoryManager? = context?.let { PasswordHistoryManager(it) }
    private val settingsManager: takagi.ru.monica.utils.SettingsManager? = context?.let { takagi.ru.monica.utils.SettingsManager(it) }
    private val bitwardenRepository: BitwardenRepository? = context?.let { BitwardenRepository.getInstance(it.applicationContext) }
    private val keepassService = if (context != null && localKeePassDatabaseDao != null) {
        KeePassKdbxService(context.applicationContext, localKeePassDatabaseDao, securityManager)
    } else {
        null
    }
    
    // Trash settings
    private val trashSettings = settingsManager?.settingsFlow?.map { 
        it.trashEnabled to it.trashAutoDeleteDays 
    }?.stateIn(viewModelScope, SharingStarted.Eagerly, true to 30)

    // Smart Deduplication setting
    private val smartDeduplicationEnabled = settingsManager?.settingsFlow?.map { 
        it.smartDeduplicationEnabled 
    }?.stateIn(viewModelScope, SharingStarted.Eagerly, true) ?: kotlinx.coroutines.flow.MutableStateFlow(true)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    val categoryFilter = _categoryFilter.asStateFlow()

    val categories = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    private var hasLoggedDecryptAuthStateWarning = false

    init {
        restoreLastCategoryFilter()
        observeInvalidCustomCategoryFilter()
    }
    
    fun getBitwardenFolders(vaultId: Long): Flow<List<BitwardenFolder>> {
        return repository.getBitwardenFoldersByVaultId(vaultId)
    }
    
    private val debouncedSearchQuery: Flow<String> = searchQuery
        .debounce(300)
        .distinctUntilChanged()

    val passwordEntries: StateFlow<List<PasswordEntry>> = combine(
        debouncedSearchQuery,
        _categoryFilter
    ) { query, filter ->
        query to filter
    }
        .distinctUntilChanged()
        .flatMapLatest { (query, filter) ->
            val baseFlow: Flow<List<PasswordEntry>> = if (query.isNotBlank()) {
                // Extended search: query + custom fields, then apply current category filter in-memory.
                val searchFlow = repository.searchPasswordEntries(query).map { baseResults ->
                    val customFieldMatchIds = try {
                        customFieldRepository?.searchEntryIdsByFieldContent(query) ?: emptyList()
                    } catch (e: Exception) {
                        Log.w("PasswordViewModel", "Custom field search failed", e)
                        emptyList()
                    }
                    
                    if (customFieldMatchIds.isEmpty()) {
                        baseResults
                    } else {
                        val baseIds = baseResults.map { it.id }.toSet()
                        val additionalIds = customFieldMatchIds.filter { it !in baseIds }
                        
                        if (additionalIds.isEmpty()) {
                            baseResults
                        } else {
                            val additionalEntries = try {
                                repository.getPasswordsByIds(additionalIds)
                            } catch (e: Exception) {
                                Log.w("PasswordViewModel", "Failed to fetch custom field matched entries", e)
                                emptyList()
                            }
                            baseResults + additionalEntries
                        }
                    }
                }

                when (filter) {
                    is CategoryFilter.LocalOnly -> combine(
                        searchFlow,
                        repository.getAllPasswordEntries()
                    ) { searchResults, allEntries ->
                        val localOnlyIds = filterLocalOnlyComparedToBitwarden(allEntries)
                            .asSequence()
                            .map { it.id }
                            .toHashSet()
                        searchResults.filter { it.id in localOnlyIds }
                    }
                    else -> searchFlow.map { searchResults ->
                        applyCategoryFilterInMemory(searchResults, filter)
                    }
                }
            } else {

                when (filter) {
                    is CategoryFilter.All -> repository.getAllPasswordEntries()
                    is CategoryFilter.Local -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.keepassDatabaseId == null && it.bitwardenVaultId == null }
                    }
                    is CategoryFilter.LocalOnly -> repository.getAllPasswordEntries().map { list ->
                        filterLocalOnlyComparedToBitwarden(list)
                    }
                    is CategoryFilter.Starred -> repository.getFavoritePasswordEntries()
                    is CategoryFilter.Uncategorized -> repository.getUncategorizedPasswordEntries()
                    is CategoryFilter.LocalStarred -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.keepassDatabaseId == null && it.bitwardenVaultId == null && it.isFavorite }
                    }
                    is CategoryFilter.LocalUncategorized -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.keepassDatabaseId == null && it.bitwardenVaultId == null && it.categoryId == null }
                    }
                    is CategoryFilter.Custom -> repository.getPasswordEntriesByCategory(filter.categoryId)
                    is CategoryFilter.KeePassDatabase -> repository.getPasswordEntriesByKeePassDatabase(filter.databaseId)
                    is CategoryFilter.KeePassGroupFilter -> repository.getPasswordEntriesByKeePassGroup(filter.databaseId, filter.groupPath)
                    is CategoryFilter.KeePassDatabaseStarred -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.keepassDatabaseId == filter.databaseId && it.isFavorite }
                    }
                    is CategoryFilter.KeePassDatabaseUncategorized -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.keepassDatabaseId == filter.databaseId && it.keepassGroupPath.isNullOrBlank() }
                    }
                    is CategoryFilter.BitwardenVault -> repository.getPasswordEntriesByBitwardenVault(filter.vaultId)
                    is CategoryFilter.BitwardenFolderFilter -> repository.getPasswordEntriesByBitwardenFolder(
                        filter.vaultId,
                        filter.folderId
                    )
                    is CategoryFilter.BitwardenVaultStarred -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.bitwardenVaultId == filter.vaultId && it.isFavorite }
                    }
                    is CategoryFilter.BitwardenVaultUncategorized -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.bitwardenVaultId == filter.vaultId && it.bitwardenFolderId == null }
                    }
                }
            }
            // Combine with settings for smart deduplication logic
            combine(baseFlow, smartDeduplicationEnabled) { entries, smartDedupe ->
                // Dedupe logic:
                // 1. If searching, or explicit Local/KeePass/Bitwarden filter -> NO dedupe (show raw data).
                // 2. If "All" or other categories -> Apply Smart Dedupe if enabled.
                val isExplicitSourceView = when (filter) {
                    is CategoryFilter.BitwardenVault -> true
                    is CategoryFilter.BitwardenFolderFilter -> true // Explicit folder view
                    is CategoryFilter.KeePassDatabase -> true
                    is CategoryFilter.KeePassGroupFilter -> true
                    is CategoryFilter.KeePassDatabaseStarred -> true
                    is CategoryFilter.KeePassDatabaseUncategorized -> true
                    is CategoryFilter.Local -> true // Local view shows all local entries
                    is CategoryFilter.LocalOnly -> true
                    is CategoryFilter.LocalStarred -> true
                    is CategoryFilter.LocalUncategorized -> true
                    is CategoryFilter.BitwardenVaultStarred -> true
                    is CategoryFilter.BitwardenVaultUncategorized -> true
                    else -> false
                }
                
                // Smart dedupe is only for the "All" view and does not mutate source data.
                val shouldDedupe = !isExplicitSourceView && smartDedupe && filter is CategoryFilter.All
                
                val filtered = if (shouldDedupe) {
                    dedupeSmart(entries)
                } else {
                    entries
                }
                val decrypted = filtered.map { entry ->
                    entry.copy(password = decryptForDisplay(entry.password))
                }
                filterGhostEntriesForDisplay(decrypted)
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allPasswords: StateFlow<List<PasswordEntry>> = repository.getAllPasswordEntries()
        .map { entries ->
            entries.map { entry ->
                entry.copy(password = decryptForDisplay(entry.password))
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Smart Deduplication Logic
     * Display-layer dedupe for "All" view:
     * 1) merge same account across sources
     * 2) then keep one entry per unique password value within that account
     */
    private fun dedupeSmart(entries: List<PasswordEntry>): List<PasswordEntry> {
        if (entries.size <= 1) return entries

        val indexById = entries.mapIndexed { index, entry -> entry.id to index }.toMap()
        val accountGroups = entries.groupBy { buildDedupeKey(it) }
        val deduped = mutableListOf<PasswordEntry>()

        for ((_, groupEntries) in accountGroups) {
            if (groupEntries.size <= 1) {
                deduped.addAll(groupEntries)
                continue
            }

            val decrypted = groupEntries.map { entry ->
                entry to runCatching { securityManager.decryptData(entry.password) }.getOrNull()
            }

            val hasAnyDecrypted = decrypted.any { (_, password) -> password != null }
            if (!hasAnyDecrypted) {
                // When auth/MDK is unavailable, still collapse source-duplicates by account key.
                pickBestEntry(groupEntries)?.let { deduped.add(it) }
                continue
            }

            val knownPasswordBuckets = decrypted
                .filter { (_, password) -> password != null }
                .groupBy({ (_, password) -> password!! }, { (entry, _) -> entry })

            for ((_, candidates) in knownPasswordBuckets) {
                pickBestEntry(candidates)?.let { deduped.add(it) }
            }
        }

        return deduped.sortedBy { indexById[it.id] ?: Int.MAX_VALUE }
    }

    private fun applyCategoryFilterInMemory(
        entries: List<PasswordEntry>,
        filter: CategoryFilter
    ): List<PasswordEntry> {
        return when (filter) {
            is CategoryFilter.All -> entries
            is CategoryFilter.Local -> entries.filter { it.keepassDatabaseId == null && it.bitwardenVaultId == null }
            is CategoryFilter.LocalOnly -> entries // handled separately because it needs full dataset comparison
            is CategoryFilter.Starred -> entries.filter { it.isFavorite }
            is CategoryFilter.Uncategorized -> entries.filter { it.categoryId == null }
            is CategoryFilter.LocalStarred -> entries.filter {
                it.keepassDatabaseId == null && it.bitwardenVaultId == null && it.isFavorite
            }
            is CategoryFilter.LocalUncategorized -> entries.filter {
                it.keepassDatabaseId == null && it.bitwardenVaultId == null && it.categoryId == null
            }
            is CategoryFilter.Custom -> entries.filter { it.categoryId == filter.categoryId }
            is CategoryFilter.KeePassDatabase -> entries.filter { it.keepassDatabaseId == filter.databaseId }
            is CategoryFilter.KeePassGroupFilter -> entries.filter {
                it.keepassDatabaseId == filter.databaseId && it.keepassGroupPath == filter.groupPath
            }
            is CategoryFilter.KeePassDatabaseStarred -> entries.filter {
                it.keepassDatabaseId == filter.databaseId && it.isFavorite
            }
            is CategoryFilter.KeePassDatabaseUncategorized -> entries.filter {
                it.keepassDatabaseId == filter.databaseId && it.keepassGroupPath.isNullOrBlank()
            }
            is CategoryFilter.BitwardenVault -> entries.filter { it.bitwardenVaultId == filter.vaultId }
            is CategoryFilter.BitwardenFolderFilter -> entries.filter {
                it.bitwardenVaultId == filter.vaultId && it.bitwardenFolderId == filter.folderId
            }
            is CategoryFilter.BitwardenVaultStarred -> entries.filter {
                it.bitwardenVaultId == filter.vaultId && it.isFavorite
            }
            is CategoryFilter.BitwardenVaultUncategorized -> entries.filter {
                it.bitwardenVaultId == filter.vaultId && it.bitwardenFolderId == null
            }
        }
    }

    private fun filterGhostEntriesForDisplay(entries: List<PasswordEntry>): List<PasswordEntry> {
        if (entries.size <= 1) return entries

        val groups = entries.groupBy { buildGhostGroupKey(it) }
        val ghostIds = mutableSetOf<Long>()

        groups.values.forEach { group ->
            if (group.size <= 1) return@forEach
            if (!group.any { it.password.isNotBlank() }) return@forEach

            group.forEach { entry ->
                val isPasswordMode = entry.loginType.equals("PASSWORD", ignoreCase = true)
                if (isPasswordMode && entry.password.isBlank()) {
                    ghostIds += entry.id
                }
            }
        }

        if (ghostIds.isEmpty()) return entries
        return entries.filterNot { it.id in ghostIds }
    }

    private fun buildGhostGroupKey(entry: PasswordEntry): String {
        val sourceKey = when {
            !entry.bitwardenCipherId.isNullOrBlank() ->
                "bw:${entry.bitwardenVaultId}:${entry.bitwardenCipherId}"
            entry.bitwardenVaultId != null ->
                "bw-local:${entry.bitwardenVaultId}:${entry.bitwardenFolderId.orEmpty()}"
            entry.keepassDatabaseId != null ->
                "kp:${entry.keepassDatabaseId}:${entry.keepassGroupPath.orEmpty()}"
            else -> "local"
        }

        val title = normalizeComparableText(entry.title)
        val username = normalizeComparableText(entry.username)
        val website = normalizeWebsiteForGhostGrouping(entry.website)
        return "$sourceKey|$title|$website|$username"
    }

    private fun normalizeWebsiteForGhostGrouping(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty()) return ""
        return raw
            .lowercase(Locale.ROOT)
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun pickBestEntry(candidates: List<PasswordEntry>): PasswordEntry? {
        return candidates.maxWithOrNull(
            compareBy<PasswordEntry> { it.notes.length }
                .thenBy { it.website.length }
                .thenBy { it.username.length }
                .thenBy { if (it.isFavorite) 1 else 0 }
                .thenBy { if (it.keepassDatabaseId != null || it.bitwardenVaultId != null) 1 else 0 }
                .thenBy { it.updatedAt.time }
        )
    }

    private data class BitwardenComparableSignature(
        val username: String,
        val title: String,
        val domain: String
    )

    /**
     * "Local only" means:
     * 1) not a KeePass item
     * 2) not an already-synced Bitwarden cipher
     * 3) no matching item exists in any Bitwarden vault
     */
    private fun filterLocalOnlyComparedToBitwarden(entries: List<PasswordEntry>): List<PasswordEntry> {
        if (entries.isEmpty()) return emptyList()

        val bitwardenIndexByUsername = entries
            .asSequence()
            .filter { it.keepassDatabaseId == null && it.bitwardenVaultId != null && it.bitwardenCipherId != null }
            .map {
                BitwardenComparableSignature(
                    username = normalizeComparableText(it.username),
                    title = normalizeComparableText(it.title),
                    domain = extractComparableDomain(it.website)
                )
            }
            .filter { it.username.isNotBlank() && (it.title.isNotBlank() || it.domain.isNotBlank()) }
            .groupBy { it.username }

        return entries.filter { entry ->
            isLocalOnlyComparedToBitwarden(entry, bitwardenIndexByUsername)
        }
    }

    private fun isLocalOnlyComparedToBitwarden(
        entry: PasswordEntry,
        bitwardenIndexByUsername: Map<String, List<BitwardenComparableSignature>>
    ): Boolean {
        if (entry.keepassDatabaseId != null) return false
        if (entry.bitwardenCipherId != null) return false

        val username = normalizeComparableText(entry.username)
        if (username.isBlank()) return true

        val domain = extractComparableDomain(entry.website)
        val title = normalizeComparableText(entry.title)
        if (domain.isBlank() && title.isBlank()) return true

        val candidates = bitwardenIndexByUsername[username] ?: return true
        val matched = candidates.any { candidate ->
            (domain.isNotBlank() && domain == candidate.domain) ||
                (title.isNotBlank() && title == candidate.title)
        }
        return !matched
    }

    private fun normalizeComparableText(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun extractComparableDomain(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty()) return ""

        return runCatching {
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            val host = URI(withScheme).host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: ""
            if (host.isNotBlank()) host else raw
                .lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .substringBefore('/')
        }.getOrElse {
            raw.lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .substringBefore('/')
        }.trim()
    }

    private fun buildDedupeKey(entry: PasswordEntry): String {
        val title = normalizeDedupeText(entry.title)
        val username = normalizeDedupeText(entry.username)
        val website = normalizeWebsiteForDedupe(entry.website)
        return "$title|$username|$website"
    }

    private fun normalizeDedupeText(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeWebsiteForDedupe(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty()) return ""

        return runCatching {
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            val uri = URI(withScheme)
            val host = (uri.host ?: "").lowercase(Locale.ROOT).removePrefix("www.")
            if (host.isEmpty()) return@runCatching raw.lowercase(Locale.ROOT).trimEnd('/')

            val port = uri.port
            val hostWithPort = if (port == -1 || port == 80 || port == 443) host else "$host:$port"
            val path = (uri.path ?: "").trim().trimEnd('/').lowercase(Locale.ROOT)
            if (path.isBlank()) hostWithPort else "$hostWithPort$path"
        }.getOrElse {
            raw.lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .trimEnd('/')
        }
    }

    private fun decryptForDisplay(encryptedPassword: String): String {
        if (encryptedPassword.isEmpty()) return ""
        return runCatching {
            unwrapPasswordLayersForDisplay(encryptedPassword)
        }.getOrElse { error ->
            if (!hasLoggedDecryptAuthStateWarning) {
                Log.w("PasswordViewModel", "Skip decrypt due to auth/key state: ${error.message}")
                hasLoggedDecryptAuthStateWarning = true
            }
            ""
        }
    }

    /**
     * Historical data may contain nested encrypted payloads (ciphertext saved as plaintext, then encrypted again).
     * Try a few rounds and stop once value is stable.
     */
    private fun unwrapPasswordLayersForDisplay(value: String): String {
        var current = value
        repeat(3) {
            val decrypted = synchronized(decryptLock) {
                securityManager.decryptData(current)
            }
            if (decrypted == current) return current
            current = decrypted
        }
        return current
    }

    private fun syncKeePassDatabase(databaseId: Long) {
        val service = keepassService ?: return
        viewModelScope.launch {
            runCatching {
                val result = service.readPasswordEntries(databaseId)
                val data = result.getOrNull() ?: return@launch
                upsertKeePassEntries(databaseId, data)
                syncKeePassTotpEntries(databaseId)
            }.onFailure { error ->
                Log.w("PasswordViewModel", "KeePass sync failed for databaseId=$databaseId", error)
            }
        }
    }

    private suspend fun upsertKeePassEntries(databaseId: Long, entries: List<KeePassEntryData>) {
        val incomingEntries = entries.filter { shouldImportKeePassPasswordEntry(it) }
        val incomingKeys = incomingEntries
            .asSequence()
            .map { buildKeePassSyncKey(it.title, it.username, it.url, it.groupPath) }
            .toSet()

        incomingEntries.forEach { item ->
            val existingById = item.monicaLocalId?.let { repository.getPasswordEntryById(it) }
            val existing = if (existingById != null && existingById.keepassDatabaseId == databaseId) {
                existingById
            } else {
                repository.getDuplicateEntryInKeePass(
                    databaseId = databaseId,
                    title = item.title,
                    username = item.username,
                    website = item.url,
                    groupPath = item.groupPath
                )
            }
            val normalizedPassword = normalizeIncomingKeePassPassword(item.password)
            val encryptedPassword = securityManager.encryptData(normalizedPassword)
            if (existing != null) {
                val updated = existing.copy(
                    title = item.title,
                    username = item.username,
                    password = encryptedPassword,
                    website = item.url,
                    notes = item.notes,
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = item.groupPath,
                    isDeleted = false,
                    deletedAt = null,
                    updatedAt = Date()
                )
                repository.updatePasswordEntry(updated)
            } else {
                val newEntry = PasswordEntry(
                    title = item.title,
                    username = item.username,
                    password = encryptedPassword,
                    website = item.url,
                    notes = item.notes,
                    createdAt = Date(),
                    updatedAt = Date(),
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = item.groupPath
                )
                repository.insertPasswordEntry(newEntry)
            }
        }

        reconcileKeePassEntries(databaseId, incomingKeys)
    }

    private suspend fun syncKeePassTotpEntries(databaseId: Long) {
        val service = keepassService ?: return
        val secureRepo = secureItemRepository ?: return

        val snapshots = service
            .readSecureItems(databaseId, setOf(ItemType.TOTP))
            .getOrNull()
            ?: return

        val existingTotp = secureRepo.getItemsByType(ItemType.TOTP).first()
        snapshots.forEach { snapshot ->
            val incoming = snapshot.item
            val existingBySource = snapshot.sourceMonicaId
                ?.takeIf { it > 0 }
                ?.let { sourceId -> secureRepo.getItemById(sourceId) }
                ?.takeIf { it.itemType == ItemType.TOTP }

            val existing = existingBySource ?: existingTotp.firstOrNull {
                it.itemType == ItemType.TOTP &&
                    it.keepassDatabaseId == databaseId &&
                    it.keepassGroupPath == incoming.keepassGroupPath &&
                    it.title == incoming.title
            }

            if (existing == null) {
                secureRepo.insertItem(incoming)
            } else {
                secureRepo.updateItem(
                    existing.copy(
                        title = incoming.title,
                        notes = incoming.notes,
                        itemData = incoming.itemData,
                        isFavorite = incoming.isFavorite,
                        imagePaths = incoming.imagePaths,
                        keepassDatabaseId = incoming.keepassDatabaseId,
                        keepassGroupPath = incoming.keepassGroupPath,
                        isDeleted = false,
                        deletedAt = null,
                        updatedAt = Date()
                    )
                )
            }
        }
    }

    private fun normalizeIncomingKeePassPassword(raw: String): String {
        if (raw.isBlank()) return raw
        var current = raw
        repeat(3) {
            val decrypted = runCatching {
                synchronized(decryptLock) {
                    securityManager.decryptData(current)
                }
            }.getOrNull() ?: return current
            if (decrypted == current) return current
            current = decrypted
        }
        return current
    }

    private fun shouldImportKeePassPasswordEntry(item: KeePassEntryData): Boolean {
        // 仅导入“密码型”条目：至少有 username/password/url 之一。
        // 这样可避免把安全项/备注型条目误导入密码列表形成幽灵卡片。
        return item.username.isNotBlank() || item.password.isNotBlank() || item.url.isNotBlank()
    }

    private fun buildKeePassSyncKey(
        title: String,
        username: String,
        website: String,
        groupPath: String?
    ): String {
        val normalizedTitle = title.trim().lowercase(Locale.ROOT)
        val normalizedUsername = username.trim().lowercase(Locale.ROOT)
        val normalizedWebsite = normalizeWebsiteForDedupe(website)
        val normalizedGroup = groupPath?.trim().orEmpty()
        return "$normalizedGroup|$normalizedTitle|$normalizedUsername|$normalizedWebsite"
    }

    private suspend fun reconcileKeePassEntries(databaseId: Long, incomingKeys: Set<String>) {
        val localEntries = repository.getPasswordEntriesByKeePassDatabaseSync(databaseId)
        if (localEntries.isEmpty()) return

        val grouped = localEntries.groupBy { entry ->
            buildKeePassSyncKey(entry.title, entry.username, entry.website, entry.keepassGroupPath)
        }

        val keepIds = mutableSetOf<Long>()
        grouped.forEach { (key, candidates) ->
            if (key !in incomingKeys) return@forEach
            val keep = candidates.maxWithOrNull(
                compareBy<PasswordEntry> { if (decryptForDisplay(it.password).isNotBlank()) 1 else 0 }
                    .thenBy { it.updatedAt.time }
                    .thenBy { it.id }
            ) ?: candidates.first()
            keepIds += keep.id
        }

        val stale = localEntries.filter { entry ->
            val key = buildKeePassSyncKey(entry.title, entry.username, entry.website, entry.keepassGroupPath)
            key !in incomingKeys || entry.id !in keepIds
        }

        stale.forEach { repository.deletePasswordEntry(it) }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(filter: CategoryFilter) {
        applyCategoryFilter(filter, persist = true)
    }

    private fun applyCategoryFilter(filter: CategoryFilter, persist: Boolean) {
        _categoryFilter.value = filter
        if (persist) {
            persistCategoryFilter(filter)
        }
        when (filter) {
            is CategoryFilter.KeePassDatabase -> syncKeePassDatabase(filter.databaseId)
            is CategoryFilter.KeePassGroupFilter -> syncKeePassDatabase(filter.databaseId)
            is CategoryFilter.KeePassDatabaseStarred -> syncKeePassDatabase(filter.databaseId)
            is CategoryFilter.KeePassDatabaseUncategorized -> syncKeePassDatabase(filter.databaseId)
            else -> Unit
        }
    }

    private fun restoreLastCategoryFilter() {
        val manager = settingsManager ?: return
        viewModelScope.launch {
            runCatching { manager.settingsFlow.first() }
                .onSuccess { settings ->
                    if (_categoryFilter.value !is CategoryFilter.All) return@onSuccess
                    val restoredFilter = decodeSavedCategoryFilter(settings)
                    val sanitizedFilter = sanitizeRestoredCategoryFilter(restoredFilter)
                    if (sanitizedFilter != restoredFilter) {
                        applyCategoryFilter(CategoryFilter.All, persist = true)
                    } else {
                        applyCategoryFilter(sanitizedFilter, persist = false)
                    }
                }
                .onFailure { error ->
                    Log.w("PasswordViewModel", "Failed to restore last category filter", error)
                }
        }
    }

    private suspend fun sanitizeRestoredCategoryFilter(filter: CategoryFilter): CategoryFilter {
        if (filter is CategoryFilter.Custom) {
            return if (repository.getCategoryById(filter.categoryId) == null) {
                CategoryFilter.All
            } else {
                filter
            }
        }

        val keepassDatabaseId = when (filter) {
            is CategoryFilter.KeePassDatabase -> filter.databaseId
            is CategoryFilter.KeePassGroupFilter -> filter.databaseId
            is CategoryFilter.KeePassDatabaseStarred -> filter.databaseId
            is CategoryFilter.KeePassDatabaseUncategorized -> filter.databaseId
            else -> null
        } ?: return filter

        val dao = localKeePassDatabaseDao ?: return CategoryFilter.All
        val database = dao.getDatabaseById(keepassDatabaseId) ?: return CategoryFilter.All
        return if (database.isWebDavDatabase()) {
            CategoryFilter.All
        } else {
            filter
        }
    }

    private fun observeInvalidCustomCategoryFilter() {
        viewModelScope.launch {
            combine(_categoryFilter, categories) { filter, categoryList ->
                filter to categoryList
            }.collectLatest { (filter, categoryList) ->
                val customFilter = filter as? CategoryFilter.Custom ?: return@collectLatest
                if (categoryList.any { it.id == customFilter.categoryId }) return@collectLatest

                val existsInDb = repository.getCategoryById(customFilter.categoryId) != null
                if (!existsInDb &&
                    _categoryFilter.value is CategoryFilter.Custom &&
                    (_categoryFilter.value as CategoryFilter.Custom).categoryId == customFilter.categoryId
                ) {
                    applyCategoryFilter(CategoryFilter.All, persist = true)
                }
            }
        }
    }

    private fun decodeSavedCategoryFilter(settings: takagi.ru.monica.data.AppSettings): CategoryFilter {
        val type = settings.lastPasswordCategoryFilterType.lowercase(Locale.ROOT)
        return when (type) {
            SAVED_FILTER_ALL -> CategoryFilter.All
            SAVED_FILTER_LOCAL -> CategoryFilter.Local
            SAVED_FILTER_LOCAL_ONLY -> CategoryFilter.LocalOnly
            SAVED_FILTER_STARRED -> CategoryFilter.Starred
            SAVED_FILTER_UNCATEGORIZED -> CategoryFilter.Uncategorized
            SAVED_FILTER_LOCAL_STARRED -> CategoryFilter.LocalStarred
            SAVED_FILTER_LOCAL_UNCATEGORIZED -> CategoryFilter.LocalUncategorized
            SAVED_FILTER_CUSTOM -> settings.lastPasswordCategoryFilterPrimaryId
                ?.let { CategoryFilter.Custom(it) }
                ?: CategoryFilter.All
            SAVED_FILTER_KEEPASS_DATABASE -> settings.lastPasswordCategoryFilterPrimaryId
                ?.let { CategoryFilter.KeePassDatabase(it) }
                ?: CategoryFilter.All
            SAVED_FILTER_KEEPASS_DATABASE_STARRED -> settings.lastPasswordCategoryFilterPrimaryId
                ?.let { CategoryFilter.KeePassDatabaseStarred(it) }
                ?: CategoryFilter.All
            SAVED_FILTER_KEEPASS_DATABASE_UNCATEGORIZED -> settings.lastPasswordCategoryFilterPrimaryId
                ?.let { CategoryFilter.KeePassDatabaseUncategorized(it) }
                ?: CategoryFilter.All
            SAVED_FILTER_KEEPASS_GROUP -> {
                val databaseId = settings.lastPasswordCategoryFilterPrimaryId
                val groupPath = settings.lastPasswordCategoryFilterText
                if (databaseId != null && !groupPath.isNullOrBlank()) {
                    CategoryFilter.KeePassGroupFilter(databaseId, groupPath)
                } else {
                    CategoryFilter.All
                }
            }
            SAVED_FILTER_BITWARDEN_VAULT -> settings.lastPasswordCategoryFilterPrimaryId
                ?.let { CategoryFilter.BitwardenVault(it) }
                ?: CategoryFilter.All
            SAVED_FILTER_BITWARDEN_VAULT_STARRED -> settings.lastPasswordCategoryFilterPrimaryId
                ?.let { CategoryFilter.BitwardenVaultStarred(it) }
                ?: CategoryFilter.All
            SAVED_FILTER_BITWARDEN_VAULT_UNCATEGORIZED -> settings.lastPasswordCategoryFilterPrimaryId
                ?.let { CategoryFilter.BitwardenVaultUncategorized(it) }
                ?: CategoryFilter.All
            SAVED_FILTER_BITWARDEN_FOLDER -> {
                val vaultId = settings.lastPasswordCategoryFilterSecondaryId
                    ?: settings.lastPasswordCategoryFilterPrimaryId
                val folderId = settings.lastPasswordCategoryFilterText
                if (vaultId != null && !folderId.isNullOrBlank()) {
                    CategoryFilter.BitwardenFolderFilter(folderId, vaultId)
                } else {
                    CategoryFilter.All
                }
            }
            else -> CategoryFilter.All
        }
    }

    private fun persistCategoryFilter(filter: CategoryFilter) {
        val manager = settingsManager ?: return
        viewModelScope.launch {
            runCatching {
                when (filter) {
                    is CategoryFilter.All -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_ALL
                    )
                    is CategoryFilter.Local -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_LOCAL
                    )
                    is CategoryFilter.LocalOnly -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_LOCAL_ONLY
                    )
                    is CategoryFilter.Starred -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_STARRED
                    )
                    is CategoryFilter.Uncategorized -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_UNCATEGORIZED
                    )
                    is CategoryFilter.LocalStarred -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_LOCAL_STARRED
                    )
                    is CategoryFilter.LocalUncategorized -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_LOCAL_UNCATEGORIZED
                    )
                    is CategoryFilter.Custom -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_CUSTOM,
                        primaryId = filter.categoryId
                    )
                    is CategoryFilter.KeePassDatabase -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_KEEPASS_DATABASE,
                        primaryId = filter.databaseId
                    )
                    is CategoryFilter.KeePassDatabaseStarred -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_KEEPASS_DATABASE_STARRED,
                        primaryId = filter.databaseId
                    )
                    is CategoryFilter.KeePassDatabaseUncategorized -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_KEEPASS_DATABASE_UNCATEGORIZED,
                        primaryId = filter.databaseId
                    )
                    is CategoryFilter.KeePassGroupFilter -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_KEEPASS_GROUP,
                        primaryId = filter.databaseId,
                        text = filter.groupPath
                    )
                    is CategoryFilter.BitwardenVault -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_BITWARDEN_VAULT,
                        primaryId = filter.vaultId
                    )
                    is CategoryFilter.BitwardenVaultStarred -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_BITWARDEN_VAULT_STARRED,
                        primaryId = filter.vaultId
                    )
                    is CategoryFilter.BitwardenVaultUncategorized -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_BITWARDEN_VAULT_UNCATEGORIZED,
                        primaryId = filter.vaultId
                    )
                    is CategoryFilter.BitwardenFolderFilter -> manager.updateLastPasswordCategoryFilter(
                        type = SAVED_FILTER_BITWARDEN_FOLDER,
                        secondaryId = filter.vaultId,
                        text = filter.folderId
                    )
                }
            }.onFailure { error ->
                Log.w("PasswordViewModel", "Failed to persist category filter", error)
            }
        }
    }

    fun addCategory(name: String, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertCategory(Category(name = name))
            onResult(id)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (_categoryFilter.value is CategoryFilter.Custom && (_categoryFilter.value as CategoryFilter.Custom).categoryId == category.id) {
                applyCategoryFilter(CategoryFilter.All, persist = true)
            }
        }
    }
    
    fun updateCategorySortOrder(categories: List<Category>) {
        viewModelScope.launch {
            categories.forEachIndexed { index, category ->
                repository.updateCategorySortOrder(category.id, index)
            }
        }
    }

    fun movePasswordsToCategory(ids: List<Long>, categoryId: Long?) {
        viewModelScope.launch {
            repository.updateCategoryForPasswords(ids, categoryId)
            val targetCategory = categoryId?.let { repository.getCategoryById(it) }
            val targetVaultId = targetCategory?.bitwardenVaultId
            val targetFolderId = targetCategory?.bitwardenFolderId

            if (targetVaultId != null && !targetFolderId.isNullOrBlank()) {
                repository.bindPasswordsToBitwardenFolder(
                    ids = ids,
                    vaultId = targetVaultId,
                    folderId = targetFolderId
                )
            } else {
                // 仅清理尚未上传（无 cipherId）的待绑定条目，避免误改已同步条目
                repository.clearPendingBitwardenBinding(ids)
            }
        }
    }
    
    fun movePasswordsToKeePassDatabase(ids: List<Long>, databaseId: Long?) {
        viewModelScope.launch {
            repository.updateKeePassDatabaseForPasswords(ids, databaseId)
        }
    }

    fun movePasswordsToKeePassGroup(ids: List<Long>, databaseId: Long, groupPath: String) {
        viewModelScope.launch {
            repository.updateKeePassGroupForPasswords(ids, databaseId, groupPath)
        }
    }

    fun movePasswordsToBitwardenFolder(ids: List<Long>, vaultId: Long, folderId: String) {
        viewModelScope.launch {
            // Clear KeePass binding first so the same entry can switch storage target.
            repository.updateKeePassDatabaseForPasswords(ids, null)
            repository.bindPasswordsToBitwardenFolder(ids, vaultId, folderId)
        }
    }
    
    fun authenticate(password: String): Boolean {
        val isValid = securityManager.verifyMasterPassword(password)
        _isAuthenticated.value = isValid
        if (isValid) {
            SessionManager.markUnlocked()
        }
        return isValid
    }
    
    fun setMasterPassword(password: String) {
        securityManager.setMasterPassword(password)
        _isAuthenticated.value = true
        SessionManager.markUnlocked()
    }
    
    fun isMasterPasswordSet(): Boolean {
        return securityManager.isMasterPasswordSet()
    }
    
    fun logout() {
        _isAuthenticated.value = false
        SessionManager.markLocked()
    }
    
    fun addPasswordEntry(entry: PasswordEntry, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = createPasswordEntryInternal(entry, includeDetailedLog = true) ?: return@launch
            onResult(id)
        }
    }

    private suspend fun createPasswordEntryInternal(
        entry: PasswordEntry,
        includeDetailedLog: Boolean
    ): Long? {
        val boundEntry = applyCategoryBinding(entry)
        val encryptedEntry = boundEntry.copy(
            password = securityManager.encryptData(boundEntry.password),
            createdAt = Date(),
            updatedAt = Date()
        )
        val id = repository.insertPasswordEntry(encryptedEntry)

        val keepassId = boundEntry.keepassDatabaseId
        if (keepassId != null) {
            Log.d(
                "PasswordViewModel",
                "Create entry id=$id will sync to KeePass db=$keepassId group=${boundEntry.keepassGroupPath ?: "<root>"}"
            )
            val service = keepassService
            if (service != null) {
                val syncResult = service.addPasswordEntry(
                    databaseId = keepassId,
                    entry = boundEntry.copy(id = id),
                    resolvePassword = { it.password }
                )
                if (syncResult.isFailure) {
                    repository.deletePasswordEntryById(id)
                    Log.e("PasswordViewModel", "KeePass write failed: ${syncResult.exceptionOrNull()?.message}")
                    return null
                }
                Log.d("PasswordViewModel", "KeePass write success for entry id=$id db=$keepassId")
            }
        } else {
            Log.w("PasswordViewModel", "Create entry id=$id skipped KeePass sync because keepassDatabaseId is null")
        }

        if (includeDetailedLog) {
            val createDetails = mutableListOf<takagi.ru.monica.utils.FieldChange>()
            if (boundEntry.username.isNotBlank()) {
                createDetails.add(takagi.ru.monica.utils.FieldChange("用户名", "", boundEntry.username))
            }
            if (boundEntry.website.isNotBlank()) {
                createDetails.add(takagi.ru.monica.utils.FieldChange("网站", "", boundEntry.website))
            }
            if (boundEntry.password.isNotBlank()) {
                // 记录真实密码，在UI层隐藏显示
                createDetails.add(takagi.ru.monica.utils.FieldChange("密码", "", boundEntry.password))
            }
            if (boundEntry.notes.isNotBlank()) {
                createDetails.add(takagi.ru.monica.utils.FieldChange("备注", "", boundEntry.notes.take(50)))
            }
            takagi.ru.monica.utils.OperationLogger.logCreate(
                itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                itemId = id,
                itemTitle = boundEntry.title,
                details = createDetails
            )
        } else {
            takagi.ru.monica.utils.OperationLogger.logCreate(
                itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                itemId = id,
                itemTitle = boundEntry.title
            )
        }
        return id
    }

    fun addSecureItem(item: SecureItem) {
        viewModelScope.launch {
            secureItemRepository?.insertItem(item)
        }
    }
    
    /**
     * 快速添加密码（从底部导航栏快速添加）
     */
    fun quickAddPassword(title: String, username: String, password: String) {
        if (title.isBlank()) return
        val entry = PasswordEntry(
            title = title,
            username = username,
            password = password,
            website = "",
            notes = "",
            isFavorite = false
        )
        addPasswordEntry(entry)
    }
    
    fun updatePasswordEntry(entry: PasswordEntry) {
        viewModelScope.launch {
            updatePasswordEntryInternal(entry)
        }
    }

    private suspend fun updatePasswordEntryInternal(entry: PasswordEntry): Boolean {
        // 获取旧数据用于对比
        val oldEntry = repository.getPasswordEntryById(entry.id)
        
        // 应用分类绑定
        val boundEntry = applyCategoryBinding(entry)
        val entryToUpdate = if (boundEntry.bitwardenVaultId != null) {
            boundEntry.copy(bitwardenLocalModified = true)
        } else {
            boundEntry
        }
        
        val oldPassword = oldEntry?.let { decryptForDisplay(it.password) } ?: ""
        repository.updatePasswordEntry(
            entryToUpdate.copy(
                password = securityManager.encryptData(entryToUpdate.password),
                updatedAt = Date()
            )
        )

        val service = keepassService
        val oldKeepassId = oldEntry?.keepassDatabaseId
        val newKeepassId = entryToUpdate.keepassDatabaseId
        if (service != null) {
            if (oldKeepassId != null && oldKeepassId != newKeepassId) {
                val deleteResult = service.deletePasswordEntries(oldKeepassId, listOf(entryToUpdate.copy(keepassDatabaseId = oldKeepassId)))
                if (deleteResult.isFailure) {
                    Log.e("PasswordViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                }
            }
            if (newKeepassId != null) {
                val updateResult = service.updatePasswordEntry(
                    databaseId = newKeepassId,
                    entry = entryToUpdate,
                    resolvePassword = { it.password }
                )
                if (updateResult.isFailure) {
                    Log.e("PasswordViewModel", "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
                }
            }
        }
        
        // 记录更新操作
        val changes = takagi.ru.monica.utils.OperationLogger.compareAndGetChanges(
            old = oldEntry,
            new = entryToUpdate,
            fields = listOf(
                "用户名" to { it.username },
                "网站" to { it.website },
                "备注" to { it.notes }
            )
        )

        // 捕获密码变化（记录真实密码，在UI层隐藏显示）
        if (oldEntry != null && oldPassword != entryToUpdate.password) {
            val updatedChanges = changes.toMutableList()
            updatedChanges.add(
                takagi.ru.monica.utils.FieldChange(
                    fieldName = "密码",
                    oldValue = oldPassword,
                    newValue = entryToUpdate.password
                )
            )
            takagi.ru.monica.utils.OperationLogger.logUpdate(
                itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                itemId = entryToUpdate.id,
                itemTitle = entryToUpdate.title,
                changes = updatedChanges
            )
            return true
        }
        takagi.ru.monica.utils.OperationLogger.logUpdate(
            itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
            itemId = entryToUpdate.id,
            itemTitle = entryToUpdate.title,
            changes = changes
        )
        return true
    }
    
    fun deletePasswordEntry(entry: PasswordEntry) {
        viewModelScope.launch {
            val trashEnabled = trashSettings?.value?.first ?: true
            val service = keepassService
            val keepassId = entry.keepassDatabaseId
            val bitwardenVaultId = entry.bitwardenVaultId
            val bitwardenCipherId = entry.bitwardenCipherId
            val isBitwardenCipher = bitwardenVaultId != null && !bitwardenCipherId.isNullOrBlank()

            if (isBitwardenCipher) {
                val queueResult = bitwardenRepository?.queueCipherDelete(
                    vaultId = bitwardenVaultId!!,
                    cipherId = bitwardenCipherId!!,
                    entryId = entry.id
                )
                if (queueResult?.isFailure == true) {
                    Log.e(
                        "PasswordViewModel",
                        "Queue Bitwarden delete failed: ${queueResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                if (service != null && keepassId != null) {
                    val deleteResult = service.deletePasswordEntries(keepassId, listOf(entry))
                    if (deleteResult.isFailure) {
                        Log.e("PasswordViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }

                // Bitwarden 删除采用 tombstone，防止下一次拉取把条目复活。
                val softDeletedEntry = entry.copy(
                    isDeleted = true,
                    deletedAt = Date(),
                    updatedAt = Date(),
                    bitwardenLocalModified = true
                )
                repository.updatePasswordEntry(softDeletedEntry)
                takagi.ru.monica.utils.OperationLogger.logDelete(
                    itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                    itemId = entry.id,
                    itemTitle = entry.title,
                    detail = "移入回收站（待同步删除）"
                )
                return@launch
            }
             
            if (trashEnabled) {
                if (service != null && keepassId != null) {
                    val deleteResult = service.deletePasswordEntries(keepassId, listOf(entry))
                    if (deleteResult.isFailure) {
                        Log.e("PasswordViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
                // 软删除：移动到回收站
                val softDeletedEntry = entry.copy(
                    isDeleted = true,
                    deletedAt = Date(),
                    updatedAt = Date()
                )
                repository.updatePasswordEntry(softDeletedEntry)
                // 记录移入回收站操作
                takagi.ru.monica.utils.OperationLogger.logDelete(
                    itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                    itemId = entry.id,
                    itemTitle = entry.title,
                    detail = "移入回收站"
                )
            } else {
                if (service != null && keepassId != null) {
                    val deleteResult = service.deletePasswordEntries(keepassId, listOf(entry))
                    if (deleteResult.isFailure) {
                        Log.e("PasswordViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
                // 直接永久删除
                repository.deletePasswordEntry(entry)
                // 记录删除操作
                takagi.ru.monica.utils.OperationLogger.logDelete(
                    itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                    itemId = entry.id,
                    itemTitle = entry.title
                )
            }
        }
    }
    
    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, isFavorite)
        }
    }
    
    fun toggleGroupCover(id: Long, website: String, isGroupCover: Boolean) {
        viewModelScope.launch {
            if (isGroupCover) {
                // 设置为封面,会自动清除该分组的其他封面
                repository.setGroupCover(id, website)
            } else {
                // 取消封面
                repository.updateGroupCoverStatus(id, false)
            }
        }
    }
    
    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateSortOrders(items)
        }
    }

    /**
     * 更新绑定的验证器密钥
     */
    fun updateAuthenticatorKey(id: Long, authenticatorKey: String) {
        viewModelScope.launch {
            repository.updateAuthenticatorKey(id, authenticatorKey)
        }
    }

    /**
     * 更新绑定的通行密钥元数据
     */
    fun updatePasskeyBindings(id: Long, passkeyBindings: String) {
        viewModelScope.launch {
            repository.updatePasskeyBindings(id, passkeyBindings)
        }
    }
    
    suspend fun getPasswordEntryById(id: Long): PasswordEntry? {
        return repository.getPasswordEntryById(id)?.let { entry ->
            entry.copy(password = decryptForDisplay(entry.password))
        }
    }

    /**
     * Get linked TOTP data for a password entry
     */
    fun getLinkedTotpFlow(passwordId: Long): Flow<TotpData?> {
        return secureItemRepository?.getItemsByType(ItemType.TOTP)
            ?.map { items ->
                items.mapNotNull { item ->
                    try {
                        Json.decodeFromString<TotpData>(item.itemData)
                    } catch (e: Exception) {
                        null
                    }
                }.find { it.boundPasswordId == passwordId }
            } ?: flowOf(null)
    }
    
    /**
     * Verify master password
     */
    fun verifyMasterPassword(password: String): Boolean {
        return securityManager.verifyMasterPassword(password)
    }
    
    /**
     * Reset all application data - used for forgot password scenario
     * Supports selective clearing of different data categories
     */
    fun resetAllData(
        clearPasswords: Boolean = true,
        clearTotp: Boolean = true,
        clearDocuments: Boolean = true,
        clearBankCards: Boolean = true,
        clearGeneratorHistory: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                // Clear selected data categories
                if (clearPasswords) {
                    repository.deleteAllPasswordEntries()
                }
                
                if (secureItemRepository != null) {
                    if (clearTotp) {
                        secureItemRepository.deleteAllTotpEntries()
                    }
                    
                    if (clearDocuments) {
                        secureItemRepository.deleteAllDocuments()
                    }
                    
                    if (clearBankCards) {
                        secureItemRepository.deleteAllBankCards()
                    }
                }
                
                if (clearGeneratorHistory && passwordHistoryManager != null) {
                    passwordHistoryManager.clearHistory()
                }
                
                // Always clear security data when resetting
                securityManager.clearSecurityData()
                
                // Reset authentication state
                _isAuthenticated.value = false
            } catch (e: Exception) {
                // Handle error - log it
                Log.e("PasswordViewModel", "Error clearing data", e)
            }
        }
    }
    
    /**
     * Change master password
     * 修改主密码并重新加密所有数据
     */
    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            // 1. 验证当前密码
            if (!securityManager.verifyMasterPassword(currentPassword)) {
                // TODO: 通知UI密码错误
                return@launch
            }
            
            // 2. 获取所有加密数据
            val allPasswords = repository.getAllPasswordEntries().first()
            
            // 3. 使用当前密码解密所有数据
            val decryptedPasswords = allPasswords.map { entry ->
                entry.copy(password = decryptForDisplay(entry.password))
            }
            
            // 4. 设置新密码
            securityManager.setMasterPassword(newPassword)
            
            // 5. 使用新密码重新加密所有数据
            decryptedPasswords.forEach { entry ->
                repository.updatePasswordEntry(entry.copy(
                    password = securityManager.encryptData(entry.password),
                    updatedAt = Date()
                ))
            }
            
            // 6. 重新认证
            _isAuthenticated.value = true
        }
    }
    
    /**
     * Save security questions
     * 保存密保问题
     */
    fun saveSecurityQuestions(questions: List<Pair<String, String>>) {
        viewModelScope.launch {
            // TODO: 保存到DataStore或数据库
            // 答案应该加密存储
            questions.forEach { (question, answer) ->
                val encryptedAnswer = securityManager.encryptData(answer.lowercase())
                // 存储 question 和 encryptedAnswer
            }
        }
    }

    fun updateAppAssociationByWebsite(website: String, packageName: String, appName: String) {
        viewModelScope.launch {
            repository.updateAppAssociationByWebsite(website, packageName, appName)
        }
    }

    fun updateAppAssociationByTitle(title: String, packageName: String, appName: String) {
        viewModelScope.launch {
            repository.updateAppAssociationByTitle(title, packageName, appName)
        }
    }

    // ==========================================
    // Grouping Helpers
    // ==========================================

    private fun getPasswordInfoKey(entry: PasswordEntry): String {
        return "${entry.title}|${entry.website}|${entry.username}|${entry.notes}|${entry.appPackageName}|${entry.appName}"
    }

    private fun applyCategoryBinding(entry: PasswordEntry): PasswordEntry {
        val filterBoundEntry = when (val filter = _categoryFilter.value) {
            is CategoryFilter.KeePassDatabase -> {
                if (entry.keepassDatabaseId == null) entry.copy(keepassDatabaseId = filter.databaseId) else entry
            }
            is CategoryFilter.KeePassDatabaseStarred -> {
                if (entry.keepassDatabaseId == null) entry.copy(keepassDatabaseId = filter.databaseId) else entry
            }
            is CategoryFilter.KeePassDatabaseUncategorized -> {
                if (entry.keepassDatabaseId == null) entry.copy(keepassDatabaseId = filter.databaseId) else entry
            }
            is CategoryFilter.KeePassGroupFilter -> {
                if (entry.keepassDatabaseId == null) {
                    entry.copy(
                        keepassDatabaseId = filter.databaseId,
                        keepassGroupPath = entry.keepassGroupPath ?: filter.groupPath
                    )
                } else if (entry.keepassGroupPath.isNullOrBlank()) {
                    entry.copy(keepassGroupPath = filter.groupPath)
                } else {
                    entry
                }
            }
            else -> entry
        }

        // 如果条目已指派到 Bitwarden Vault，且没有指定文件夹，尝试从分类继承
        // 或者，如果条目是在本地创建（无 Vault），但分类绑定了 Bitwarden，则自动指派

        val categoryId = filterBoundEntry.categoryId ?: return filterBoundEntry
        val category = categories.value.find { it.id == categoryId } ?: return filterBoundEntry

        // KeePass 条目保持独立，不参与 Bitwarden 自动绑定
        if (filterBoundEntry.keepassDatabaseId != null) return filterBoundEntry

        // 分类未绑定 Bitwarden：清理“待上传”绑定（已同步条目保持映射不动）
        if (category.bitwardenVaultId == null || category.bitwardenFolderId == null) {
            return if (filterBoundEntry.bitwardenCipherId == null) {
                filterBoundEntry.copy(
                    bitwardenVaultId = null,
                    bitwardenFolderId = null,
                    bitwardenLocalModified = false
                )
            } else {
                filterBoundEntry
            }
        }
        
        // 自动绑定到分类关联的 Bitwarden 文件夹
        return filterBoundEntry.copy(
            bitwardenVaultId = category.bitwardenVaultId,
            bitwardenFolderId = category.bitwardenFolderId,
            // 如果是已同步的条目，且文件夹改变了，标记为本地修改
            bitwardenLocalModified = if (filterBoundEntry.bitwardenCipherId != null && filterBoundEntry.bitwardenFolderId != category.bitwardenFolderId) true else filterBoundEntry.bitwardenLocalModified
        )
    }

    /**
     * Save a group of passwords.
     * Updates existing entries to preserve IDs (and TOTP links), creates new ones if needed,
     * and deletes removed ones.
     * The callback receives the ID of the first password (for TOTP binding).
     */
    fun saveGroupedPasswords(
        originalIds: List<Long>,
        commonEntry: PasswordEntry, // Contains common info and ONE password (ignored)
        passwords: List<String>,
        customFields: List<CustomFieldDraft> = emptyList(), // 自定义字段
        onComplete: (firstPasswordId: Long?) -> Unit = {}
    ) {
        viewModelScope.launch {
            var firstId: Long? = null
            val normalizedInput = passwords
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val preservedExistingPasswords = if (normalizedInput.isEmpty() && originalIds.isNotEmpty()) {
                originalIds.mapNotNull { id ->
                    repository.getPasswordEntryById(id)?.let { decryptForDisplay(it.password).trim() }
                }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            val effectivePasswords = when {
                normalizedInput.isNotEmpty() -> normalizedInput
                commonEntry.loginType.equals("SSO", ignoreCase = true) -> listOf("")
                preservedExistingPasswords.isNotEmpty() -> preservedExistingPasswords
                else -> emptyList()
            }

            if (effectivePasswords.isEmpty()) {
                Log.w("PasswordViewModel", "Skip saveGroupedPasswords because PASSWORD mode has no non-empty password")
                onComplete(originalIds.firstOrNull())
                return@launch
            }
            
            // 应用分类绑定规则
            val boundCommonEntry = applyCategoryBinding(commonEntry)
            
            // 1. Process each password
            effectivePasswords.forEachIndexed { index, password ->
                if (index < originalIds.size) {
                    // Update existing
                    val id = originalIds[index]
                    if (index == 0) firstId = id
                    val draftEntry = boundCommonEntry.copy(
                        id = id,
                        password = password
                    )
                    val existingEntry = repository.getPasswordEntryById(id)
                    val updatedEntry = existingEntry?.copy(
                        title = draftEntry.title,
                        website = draftEntry.website,
                        username = draftEntry.username,
                        password = draftEntry.password,
                        notes = draftEntry.notes,
                        isFavorite = draftEntry.isFavorite,
                        appPackageName = draftEntry.appPackageName,
                        appName = draftEntry.appName,
                        email = draftEntry.email,
                        phone = draftEntry.phone,
                        addressLine = draftEntry.addressLine,
                        city = draftEntry.city,
                        state = draftEntry.state,
                        zipCode = draftEntry.zipCode,
                        country = draftEntry.country,
                        creditCardNumber = draftEntry.creditCardNumber,
                        creditCardHolder = draftEntry.creditCardHolder,
                        creditCardExpiry = draftEntry.creditCardExpiry,
                        creditCardCVV = draftEntry.creditCardCVV,
                        categoryId = draftEntry.categoryId,
                        keepassDatabaseId = draftEntry.keepassDatabaseId,
                        authenticatorKey = draftEntry.authenticatorKey,
                        passkeyBindings = draftEntry.passkeyBindings,
                        loginType = draftEntry.loginType,
                        ssoProvider = draftEntry.ssoProvider,
                        ssoRefEntryId = draftEntry.ssoRefEntryId,
                        bitwardenVaultId = draftEntry.bitwardenVaultId,
                        customIconType = draftEntry.customIconType,
                        customIconValue = draftEntry.customIconValue,
                        customIconUpdatedAt = draftEntry.customIconUpdatedAt
                    ) ?: draftEntry
                    updatePasswordEntryInternal(updatedEntry)
                } else {
                    // Create new
                    val newEntry = boundCommonEntry.copy(
                        id = 0, // Reset ID for new entry
                        password = password
                    )
                    val newId = createPasswordEntryInternal(newEntry, includeDetailedLog = false)
                    if (newId == null) {
                        Log.e("PasswordViewModel", "saveGroupedPasswords aborted due to KeePass write failure")
                        onComplete(firstId ?: originalIds.firstOrNull())
                        return@launch
                    }
                    if (index == 0) firstId = newId
                }
            }

            // 2. Delete leftovers
            if (originalIds.size > effectivePasswords.size) {
                val toDelete = originalIds.subList(effectivePasswords.size, originalIds.size)
                toDelete.forEach { id ->
                    repository.getPasswordEntryById(id)?.let { deletePasswordEntry(it) }
                }
            }
            
            // 3. 保存自定义字段（只针对第一个密码条目）
            firstId?.let { entryId ->
                saveCustomFieldsForEntry(entryId, customFields)
            }
            
            onComplete(firstId)
        }
    }
    
    // =============== 自定义字段相关方法 ===============
    
    /**
     * 获取指定密码条目的自定义字段（Flow）
     */
    fun getCustomFieldsByEntryId(entryId: Long): Flow<List<CustomField>> {
        return customFieldRepository?.getFieldsByEntryId(entryId) ?: flowOf(emptyList())
    }
    
    /**
     * 获取指定密码条目的自定义字段（同步版本）
     */
    suspend fun getCustomFieldsByEntryIdSync(entryId: Long): List<CustomField> {
        return customFieldRepository?.getFieldsByEntryIdSync(entryId) ?: emptyList()
    }
    
    /**
     * 保存密码条目的自定义字段
     * 同时更新密码条目的 updatedAt 以触发同步
     */
    suspend fun saveCustomFieldsForEntry(entryId: Long, fields: List<CustomFieldDraft>) {
        customFieldRepository?.saveFieldsForEntry(entryId, fields)
        
        // 更新密码条目的 updatedAt 以确保 WebDAV 同步能检测到自定义字段的变化
        repository.updatePasswordUpdatedAt(entryId, java.util.Date())
    }
    
    /**
     * 批量获取多个条目的自定义字段（用于列表显示优化）
     */
    suspend fun getCustomFieldsByEntryIds(entryIds: List<Long>): Map<Long, List<CustomField>> {
        return customFieldRepository?.getFieldsByEntryIds(entryIds) ?: emptyMap()
    }

    /**
     * 为选中的密码条目应用同一个手动堆叠分组。
     * 使用内部自定义字段持久化，优先级高于自动堆叠规则。
     *
     * @return 实际写入的条目数量
     */
    suspend fun applyManualStack(entryIds: List<Long>): Int {
        return applyManualStackMode(entryIds, ManualStackMode.STACK)
    }

    /**
     * 设置选中条目的堆叠模式：
     * STACK: 写入同一手动堆叠组
     * AUTO_STACK: 清除手动堆叠/不堆叠标记，回归自动堆叠
     * NEVER_STACK: 标记为永不参与堆叠
     */
    suspend fun applyManualStackMode(entryIds: List<Long>, mode: ManualStackMode): Int {
        val validIds = entryIds.distinct().filter { it > 0L }
        if (validIds.isEmpty()) return 0

        val stackGroupId = if (mode == ManualStackMode.STACK) UUID.randomUUID().toString() else null
        val existingFieldsByEntry = getCustomFieldsByEntryIds(validIds)

        validIds.forEach { entryId ->
            val keptFields = existingFieldsByEntry[entryId]
                .orEmpty()
                .asSequence()
                .filterNot {
                    it.title == MONICA_MANUAL_STACK_GROUP_FIELD_TITLE ||
                        it.title == MONICA_NO_STACK_FIELD_TITLE
                }
                .map { field ->
                    CustomFieldDraft(
                        title = field.title,
                        value = field.value,
                        isProtected = field.isProtected
                    )
                }
                .toMutableList()

            when (mode) {
                ManualStackMode.STACK -> {
                    keptFields += CustomFieldDraft(
                        title = MONICA_MANUAL_STACK_GROUP_FIELD_TITLE,
                        value = stackGroupId.orEmpty(),
                        isProtected = false
                    )
                }
                ManualStackMode.NEVER_STACK -> {
                    keptFields += CustomFieldDraft(
                        title = MONICA_NO_STACK_FIELD_TITLE,
                        value = "1",
                        isProtected = false
                    )
                }
                ManualStackMode.AUTO_STACK -> Unit
            }

            saveCustomFieldsForEntry(entryId, keptFields)
        }

        return validIds.size
    }
    
    /**
     * 搜索包含指定关键词的条目ID（通过自定义字段搜索）
     */
    suspend fun searchEntryIdsByCustomFieldContent(query: String): List<Long> {
        return customFieldRepository?.searchEntryIdsByFieldContent(query) ?: emptyList()
    }
}
