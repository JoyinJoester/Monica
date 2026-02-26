package takagi.ru.monica.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.CompromisedPassword
import takagi.ru.monica.data.DuplicatePasswordGroup
import takagi.ru.monica.data.DuplicateUrlGroup
import takagi.ru.monica.data.InactivePasskeyAccount
import takagi.ru.monica.data.No2FAAccount
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasskeyDao
import takagi.ru.monica.data.PasswordStrengthDistribution
import takagi.ru.monica.data.SecurityAnalysisData
import takagi.ru.monica.data.SecurityAnalysisScopeOption
import takagi.ru.monica.data.SecurityAnalysisScopeType
import takagi.ru.monica.data.bitwarden.BitwardenVaultDao
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.PasswordStrengthAnalyzer
import takagi.ru.monica.utils.PasskeySupportCatalog
import takagi.ru.monica.utils.PwnedPasswordsChecker
import java.security.MessageDigest

/**
 * 实时安全分析 ViewModel
 */
class SecurityAnalysisViewModel(
    private val passwordRepository: PasswordRepository,
    private val securityManager: SecurityManager,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao,
    private val bitwardenVaultDao: BitwardenVaultDao,
    private val passkeyDao: PasskeyDao,
    private val passkeySupportCatalog: PasskeySupportCatalog
) : ViewModel() {

    private val tag = "SecurityAnalysisVM"

    private val _analysisData = MutableStateFlow(SecurityAnalysisData(isAnalyzing = true))
    val analysisData: StateFlow<SecurityAnalysisData> = _analysisData.asStateFlow()

    private var latestEntries: List<PasswordEntry> = emptyList()
    private val analysisMutex = Mutex()

    private val cachedEntriesById = mutableMapOf<Long, CachedAnalysisEntry>()
    private val passwordHashIndex = mutableMapOf<String, MutableSet<Long>>()
    private val normalizedUrlIndex = mutableMapOf<String, MutableSet<Long>>()
    private val pwnedCountByPasswordHash = mutableMapOf<String, Int>()
    private var compromisedRefreshJob: Job? = null
    private var compromisedRefreshScopeKey: String? = null
    private var autoObserveJob: Job? = null
    private var autoAnalysisEnabled: Boolean = false
    private var autoModeInitialized: Boolean = false

    private var keepassNameByIdCache: Map<Long, String> = emptyMap()
    private var bitwardenNameByIdCache: Map<Long, String> = emptyMap()

    private val knownTwoFaDomains = setOf(
        "google.com", "gmail.com", "facebook.com", "twitter.com", "x.com",
        "github.com", "microsoft.com", "apple.com", "amazon.com",
        "dropbox.com", "linkedin.com", "instagram.com", "reddit.com",
        "slack.com", "discord.com", "paypal.com", "netflix.com",
        "yahoo.com", "outlook.com", "icloud.com", "twitch.tv",
        "steam.com", "epic.com", "battle.net", "riot.com"
    )

    /**
     * 兼容旧调用：执行分析（现在为实时分析刷新）
     */
    fun performSecurityAnalysis() {
        refreshRealtimeAnalysis()
    }

    fun setAutoAnalysisEnabled(enabled: Boolean) {
        if (autoModeInitialized && autoAnalysisEnabled == enabled) return
        autoModeInitialized = true
        autoAnalysisEnabled = enabled
        if (enabled) {
            startAutoObservation()
            refreshRealtimeAnalysis()
        } else {
            autoObserveJob?.cancel()
            autoObserveJob = null
            compromisedRefreshJob?.cancel()
            compromisedRefreshJob = null
            compromisedRefreshScopeKey = null
            _analysisData.value = _analysisData.value.copy(isAnalyzing = false)
        }
    }

    /**
     * 手动刷新实时分析（通常无需调用，数据会自动更新）
     */
    fun refreshRealtimeAnalysis() {
        viewModelScope.launch {
            val entries = if (latestEntries.isNotEmpty()) {
                latestEntries
            } else {
                passwordRepository.getAllPasswordEntries().first()
            }
            latestEntries = entries
            recomputeAnalysis(
                passwords = entries,
                updateCache = true,
                reason = "manual_refresh"
            )
        }
    }

    fun selectScope(scopeKey: String) {
        if (_analysisData.value.selectedScopeKey == scopeKey) return
        _analysisData.value = _analysisData.value.copy(selectedScopeKey = scopeKey)
        if (!autoAnalysisEnabled && cachedEntriesById.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val entries = if (latestEntries.isNotEmpty()) {
                latestEntries
            } else {
                passwordRepository.getAllPasswordEntries().first()
            }
            latestEntries = entries
            recomputeAnalysis(
                passwords = entries,
                updateCache = autoAnalysisEnabled && cachedEntriesById.isEmpty(),
                reason = "scope_change"
            )
        }
    }

    private fun startAutoObservation() {
        if (autoObserveJob?.isActive == true) return
        autoObserveJob = viewModelScope.launch {
            passwordRepository.getAllPasswordEntries()
                .debounce(350)
                .collectLatest { entries ->
                    if (!autoAnalysisEnabled) return@collectLatest
                    latestEntries = entries
                    recomputeAnalysis(
                        passwords = entries,
                        updateCache = true,
                        reason = "entries_changed"
                    )
                }
        }
    }

    private suspend fun recomputeAnalysis(
        passwords: List<PasswordEntry>,
        updateCache: Boolean,
        reason: String
    ) {
        analysisMutex.withLock {
            try {
                _analysisData.value = _analysisData.value.copy(isAnalyzing = true, error = null)

                val changeSummary = if (updateCache || cachedEntriesById.isEmpty()) {
                    withContext(Dispatchers.Default) {
                        applyIncrementalChanges(passwords)
                    }
                } else {
                    ChangeSummary()
                }

                val scopeOptions = buildScopeOptions(passwords)
                val selectedScopeKey = _analysisData.value.selectedScopeKey
                    .takeIf { key -> scopeOptions.any { it.key == key } }
                    ?: SecurityAnalysisScopeOption.KEY_ALL
                val selectedScope = scopeOptions.firstOrNull { it.key == selectedScopeKey }
                    ?: SecurityAnalysisScopeOption.all(passwords.size)

                val scopedPasswordIdSet = buildScopedIdSet(passwords, selectedScope)
                val scopedCompromised = withContext(Dispatchers.Default) {
                    buildCompromisedFromCache(scopedPasswordIdSet)
                }
                val hasPendingCompromisedChecks = withContext(Dispatchers.Default) {
                    hasPendingCompromisedChecks(scopedPasswordIdSet)
                }
                val passkeySupportedDomains = passkeySupportCatalog.getSigninDomains()
                val passwordIdsWithPasskeys = loadBoundPasskeyPasswordIds(scopedPasswordIdSet)

                val result = withContext(Dispatchers.Default) {
                    buildAnalysisFromCache(
                        scopedPasswordIdSet = scopedPasswordIdSet,
                        compromisedPasswords = scopedCompromised,
                        passkeySupportedDomains = passkeySupportedDomains,
                        passwordIdsWithPasskeys = passwordIdsWithPasskeys
                    )
                }

                _analysisData.value = _analysisData.value.copy(
                    duplicatePasswords = result.duplicatePasswords,
                    duplicateUrls = result.duplicateUrls,
                    compromisedPasswords = scopedCompromised,
                    no2FAAccounts = result.no2FAAccounts,
                    inactivePasskeyAccounts = result.inactivePasskeyAccounts,
                    passwordStrengthDistribution = result.distribution,
                    selectedScopeKey = selectedScopeKey,
                    availableScopes = scopeOptions,
                    securityScore = result.score,
                    isAnalyzing = hasPendingCompromisedChecks,
                    analysisProgress = 100,
                    error = null
                )

                if (hasPendingCompromisedChecks) {
                    startCompromisedRefresh(
                        selectedScopeKey = selectedScopeKey,
                        scopedPasswordIdSet = scopedPasswordIdSet
                    )
                } else {
                    compromisedRefreshJob?.cancel()
                    compromisedRefreshJob = null
                    compromisedRefreshScopeKey = null
                }

                val scopedCount = scopedPasswordIdSet?.size ?: passwords.size
                Log.d(
                    tag,
                    "Realtime security analysis updated: reason=$reason, scope=$selectedScopeKey, entries=$scopedCount, score=${result.score}, compromised=${scopedCompromised.size}, inactivePasskeys=${result.inactivePasskeyAccounts.size}, pendingCompromised=$hasPendingCompromisedChecks, added=${changeSummary.added}, updated=${changeSummary.updated}, removed=${changeSummary.removed}"
                )
            } catch (e: Exception) {
                Log.e(tag, "Error during realtime security analysis", e)
                _analysisData.value = _analysisData.value.copy(
                    isAnalyzing = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun decryptPassword(value: String): String {
        if (value.isBlank()) return ""
        return runCatching { securityManager.decryptData(value) }
            .getOrElse { value }
            .trim()
    }

    private fun applyIncrementalChanges(passwords: List<PasswordEntry>): ChangeSummary {
        val incomingById = passwords.associateBy { it.id }
        val incomingIds = incomingById.keys
        val existingIds = cachedEntriesById.keys.toSet()

        var added = 0
        var updated = 0

        val removedIds = existingIds - incomingIds
        removedIds.forEach { id ->
            removeCachedEntry(id)
        }

        incomingById.forEach { (_, entry) ->
            val existing = cachedEntriesById[entry.id]
            if (existing == null) {
                val cached = buildCachedEntry(entry, null)
                upsertCachedEntry(cached)
                added++
                return@forEach
            }

            if (hasAnalysisRelevantChanges(existing.entry, entry)) {
                val cached = buildCachedEntry(entry, existing)
                upsertCachedEntry(cached)
                updated++
            } else if (existing.entry != entry) {
                cachedEntriesById[entry.id] = existing.copy(entry = entry)
            }
        }

        return ChangeSummary(
            added = added,
            updated = updated,
            removed = removedIds.size
        )
    }

    private fun hasAnalysisRelevantChanges(
        oldEntry: PasswordEntry,
        newEntry: PasswordEntry
    ): Boolean {
        return oldEntry.password != newEntry.password ||
            oldEntry.website != newEntry.website
    }

    private fun buildCachedEntry(
        entry: PasswordEntry,
        previous: CachedAnalysisEntry?
    ): CachedAnalysisEntry {
        val canReusePasswordMetrics = previous != null && previous.entry.password == entry.password
        val canReuseUrlMetrics = previous != null && previous.entry.website == entry.website

        val decryptedPassword = if (canReusePasswordMetrics) {
            null
        } else {
            decryptPassword(entry.password)
        }

        val passwordHash = if (canReusePasswordMetrics) {
            previous?.passwordHash
        } else {
            if (decryptedPassword.isNullOrBlank()) null else hashPassword(decryptedPassword)
        }

        val strengthBucket = if (canReusePasswordMetrics) {
            previous?.strengthBucket
        } else {
            if (decryptedPassword.isNullOrBlank()) null else toStrengthBucket(decryptedPassword)
        }

        val normalizedUrl = if (canReuseUrlMetrics) {
            previous?.normalizedUrl
        } else {
            entry.website.takeIf { it.isNotBlank() }?.let(::normalizeUrl)?.takeIf { it.isNotBlank() }
        }

        val domain = if (canReuseUrlMetrics) {
            previous?.domain
        } else {
            entry.website.takeIf { it.isNotBlank() }?.let(::extractDomain)?.takeIf { it.isNotBlank() }
        }

        val supports2FA = if (canReuseUrlMetrics) {
            previous?.supports2FA ?: false
        } else {
            val normalizedDomain = domain.orEmpty()
            knownTwoFaDomains.any { normalizedDomain.contains(it, ignoreCase = true) }
        }

        return CachedAnalysisEntry(
            entry = entry,
            passwordHash = passwordHash,
            normalizedUrl = normalizedUrl,
            domain = domain,
            supports2FA = supports2FA,
            strengthBucket = strengthBucket
        )
    }

    private fun upsertCachedEntry(entry: CachedAnalysisEntry) {
        val existing = cachedEntriesById[entry.entry.id]
        if (existing != null) {
            if (existing.passwordHash != entry.passwordHash) {
                removeIndexValue(passwordHashIndex, existing.passwordHash, entry.entry.id)
                addIndexValue(passwordHashIndex, entry.passwordHash, entry.entry.id)
            }
            if (existing.normalizedUrl != entry.normalizedUrl) {
                removeIndexValue(normalizedUrlIndex, existing.normalizedUrl, entry.entry.id)
                addIndexValue(normalizedUrlIndex, entry.normalizedUrl, entry.entry.id)
            }
        } else {
            addIndexValue(passwordHashIndex, entry.passwordHash, entry.entry.id)
            addIndexValue(normalizedUrlIndex, entry.normalizedUrl, entry.entry.id)
        }
        cachedEntriesById[entry.entry.id] = entry
    }

    private fun removeCachedEntry(id: Long) {
        val existing = cachedEntriesById.remove(id) ?: return
        removeIndexValue(passwordHashIndex, existing.passwordHash, id)
        removeIndexValue(normalizedUrlIndex, existing.normalizedUrl, id)
    }

    private fun addIndexValue(
        index: MutableMap<String, MutableSet<Long>>,
        key: String?,
        id: Long
    ) {
        if (key.isNullOrBlank()) return
        index.getOrPut(key) { mutableSetOf() }.add(id)
    }

    private fun removeIndexValue(
        index: MutableMap<String, MutableSet<Long>>,
        key: String?,
        id: Long
    ) {
        if (key.isNullOrBlank()) return
        val ids = index[key] ?: return
        ids.remove(id)
        if (ids.isEmpty()) {
            index.remove(key)
        }
    }

    private fun buildAnalysisFromCache(
        scopedPasswordIdSet: Set<Long>?,
        compromisedPasswords: List<CompromisedPassword>,
        passkeySupportedDomains: List<String>,
        passwordIdsWithPasskeys: Set<Long>
    ): AnalysisResult {
        val inScope: (Long) -> Boolean = if (scopedPasswordIdSet == null) {
            { true }
        } else {
            { id -> id in scopedPasswordIdSet }
        }

        val duplicatePasswords = passwordHashIndex
            .asSequence()
            .mapNotNull { (hash, ids) ->
                val entries = ids
                    .asSequence()
                    .filter(inScope)
                    .mapNotNull { id -> cachedEntriesById[id]?.entry }
                    .toList()
                if (entries.size > 1) {
                    DuplicatePasswordGroup(
                        passwordHash = hash,
                        count = entries.size,
                        entries = entries
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.count }
            .toList()

        val duplicateUrls = normalizedUrlIndex
            .asSequence()
            .mapNotNull { (url, ids) ->
                val entries = ids
                    .asSequence()
                    .filter(inScope)
                    .mapNotNull { id -> cachedEntriesById[id]?.entry }
                    .toList()
                if (entries.size > 1) {
                    DuplicateUrlGroup(
                        url = url,
                        count = entries.size,
                        entries = entries
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.count }
            .toList()

        val no2FAAccounts = cachedEntriesById.values
            .asSequence()
            .filter { cached -> inScope(cached.entry.id) && !cached.domain.isNullOrBlank() }
            .map { cached ->
                No2FAAccount(
                    entry = cached.entry,
                    domain = cached.domain.orEmpty(),
                    supports2FA = cached.supports2FA
                )
            }
            .sortedWith(
                compareByDescending<No2FAAccount> { it.supports2FA }
                    .thenBy { it.domain }
            )
            .toList()

        val inactivePasskeyAccounts = cachedEntriesById.values
            .asSequence()
            .filter { cached -> inScope(cached.entry.id) && !cached.domain.isNullOrBlank() }
            .mapNotNull { cached ->
                if (cached.entry.id in passwordIdsWithPasskeys) {
                    return@mapNotNull null
                }
                if (hasInlinePasskeyBinding(cached.entry)) {
                    return@mapNotNull null
                }
                val matchedDomain = passkeySupportCatalog.findMatchingDomain(
                    host = cached.domain.orEmpty(),
                    signinDomains = passkeySupportedDomains
                ) ?: return@mapNotNull null
                InactivePasskeyAccount(
                    entry = cached.entry,
                    domain = matchedDomain
                )
            }
            .sortedWith(
                compareBy<InactivePasskeyAccount> { it.domain.lowercase() }
                    .thenBy { it.entry.title.lowercase() }
            )
            .toList()

        var weak = 0
        var medium = 0
        var strong = 0
        var veryStrong = 0
        cachedEntriesById.values.forEach { cached ->
            if (!inScope(cached.entry.id)) return@forEach
            when (cached.strengthBucket) {
                StrengthBucket.WEAK -> weak++
                StrengthBucket.MEDIUM -> medium++
                StrengthBucket.STRONG -> strong++
                StrengthBucket.VERY_STRONG -> veryStrong++
                null -> Unit
            }
        }
        val distribution = PasswordStrengthDistribution(
            weak = weak,
            medium = medium,
            strong = strong,
            veryStrong = veryStrong
        )

        val score = calculateSecurityScore(
            duplicatePasswords = duplicatePasswords,
            duplicateUrls = duplicateUrls,
            no2FAAccounts = no2FAAccounts,
            distribution = distribution,
            compromisedPasswords = compromisedPasswords,
            inactivePasskeyAccounts = inactivePasskeyAccounts
        )

        return AnalysisResult(
            duplicatePasswords = duplicatePasswords,
            duplicateUrls = duplicateUrls,
            no2FAAccounts = no2FAAccounts,
            inactivePasskeyAccounts = inactivePasskeyAccounts,
            distribution = distribution,
            score = score
        )
    }

    private fun buildCompromisedFromCache(scopedPasswordIdSet: Set<Long>?): List<CompromisedPassword> {
        val inScope: (Long) -> Boolean = if (scopedPasswordIdSet == null) {
            { true }
        } else {
            { id -> id in scopedPasswordIdSet }
        }
        return cachedEntriesById.values
            .asSequence()
            .filter { cached -> inScope(cached.entry.id) }
            .mapNotNull { cached ->
                val hash = cached.passwordHash ?: return@mapNotNull null
                val count = pwnedCountByPasswordHash[hash] ?: return@mapNotNull null
                if (count > 0) {
                    CompromisedPassword(
                        entry = cached.entry,
                        breachCount = count
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.breachCount }
            .toList()
    }

    private fun hasPendingCompromisedChecks(scopedPasswordIdSet: Set<Long>?): Boolean {
        return collectUnresolvedCompromisedEntries(scopedPasswordIdSet).isNotEmpty()
    }

    private fun collectUnresolvedCompromisedEntries(scopedPasswordIdSet: Set<Long>?): Map<String, PasswordEntry> {
        val inScope: (Long) -> Boolean = if (scopedPasswordIdSet == null) {
            { true }
        } else {
            { id -> id in scopedPasswordIdSet }
        }

        val unresolved = LinkedHashMap<String, PasswordEntry>()
        cachedEntriesById.values.forEach { cached ->
            if (!inScope(cached.entry.id)) return@forEach
            val hash = cached.passwordHash ?: return@forEach
            if (pwnedCountByPasswordHash.containsKey(hash)) return@forEach
            unresolved.putIfAbsent(hash, cached.entry)
        }
        return unresolved
    }

    private fun startCompromisedRefresh(
        selectedScopeKey: String,
        scopedPasswordIdSet: Set<Long>?
    ) {
        val existingJob = compromisedRefreshJob
        if (existingJob?.isActive == true) {
            if (compromisedRefreshScopeKey == selectedScopeKey) {
                Log.d(tag, "Compromised refresh already running for scope=$selectedScopeKey, skip restart")
                return
            }
            existingJob.cancel()
        }

        compromisedRefreshScopeKey = selectedScopeKey
        compromisedRefreshJob = viewModelScope.launch {
            try {
                val unresolved = analysisMutex.withLock {
                    collectUnresolvedCompromisedEntries(scopedPasswordIdSet)
                }

                if (unresolved.isNotEmpty()) {
                    val plainPasswordByHash = unresolved.mapNotNull { (hash, entry) ->
                        val plain = decryptPassword(entry.password)
                        if (plain.isBlank()) {
                            null
                        } else {
                            hash to plain
                        }
                    }.toMap()

                    if (plainPasswordByHash.isNotEmpty()) {
                        val batchResult = PwnedPasswordsChecker.checkPasswordsBatch(
                            passwords = plainPasswordByHash.values.toList()
                        )
                        var resolvedCount = 0
                        var breachedCount = 0
                        analysisMutex.withLock {
                            plainPasswordByHash.forEach { (hash, plainPassword) ->
                                val count = batchResult[plainPassword]
                                if (count != null && count >= 0) {
                                    pwnedCountByPasswordHash[hash] = count
                                    resolvedCount++
                                    if (count > 0) breachedCount++
                                }
                            }
                        }
                        Log.d(
                            tag,
                            "Compromised batch finished: requested=${plainPasswordByHash.size}, resolved=$resolvedCount, breached=$breachedCount"
                        )
                    }
                }

                publishCompromisedResultForScope(
                    selectedScopeKey = selectedScopeKey,
                    scopedPasswordIdSet = scopedPasswordIdSet
                )
            } catch (e: CancellationException) {
                Log.d(tag, "Compromised refresh cancelled for scope=$selectedScopeKey")
            } catch (e: Exception) {
                Log.e(tag, "Error while refreshing compromised passwords", e)
                if (_analysisData.value.selectedScopeKey == selectedScopeKey) {
                    _analysisData.value = _analysisData.value.copy(isAnalyzing = false)
                }
            } finally {
                if (compromisedRefreshJob?.isActive != true) {
                    compromisedRefreshScopeKey = null
                }
            }
        }
    }

    private suspend fun publishCompromisedResultForScope(
        selectedScopeKey: String,
        scopedPasswordIdSet: Set<Long>?
    ) {
        val compromised = analysisMutex.withLock {
            buildCompromisedFromCache(scopedPasswordIdSet)
        }
        val current = _analysisData.value
        if (current.selectedScopeKey != selectedScopeKey) return

        val rescored = calculateSecurityScore(
            duplicatePasswords = current.duplicatePasswords,
            duplicateUrls = current.duplicateUrls,
            no2FAAccounts = current.no2FAAccounts,
            distribution = current.passwordStrengthDistribution,
            compromisedPasswords = compromised,
            inactivePasskeyAccounts = current.inactivePasskeyAccounts
        )
        _analysisData.value = current.copy(
            compromisedPasswords = compromised,
            securityScore = rescored,
            isAnalyzing = false,
            error = null
        )
    }

    private fun calculateSecurityScore(
        duplicatePasswords: List<DuplicatePasswordGroup>,
        duplicateUrls: List<DuplicateUrlGroup>,
        no2FAAccounts: List<No2FAAccount>,
        distribution: PasswordStrengthDistribution,
        compromisedPasswords: List<CompromisedPassword>,
        inactivePasskeyAccounts: List<InactivePasskeyAccount>
    ): Int {
        val duplicatePasswordPenalty = duplicatePasswords.sumOf { (it.count - 1).coerceAtLeast(0) } * 6
        val duplicateUrlPenalty = duplicateUrls.sumOf { (it.count - 1).coerceAtLeast(0) } * 2
        val twoFaPenalty = no2FAAccounts.count { it.supports2FA } * 2
        val weakPenalty = distribution.weak * 4
        val mediumPenalty = distribution.medium * 2
        val compromisedPenalty = compromisedPasswords.size * 10
        val inactivePasskeyPenalty = inactivePasskeyAccounts.size * 2

        val totalPenalty = duplicatePasswordPenalty +
            duplicateUrlPenalty +
            twoFaPenalty +
            weakPenalty +
            mediumPenalty +
            compromisedPenalty +
            inactivePasskeyPenalty

        return (100 - totalPenalty).coerceIn(0, 100)
    }

    private suspend fun loadBoundPasskeyPasswordIds(scopedPasswordIdSet: Set<Long>?): Set<Long> {
        return withContext(Dispatchers.IO) {
            if (scopedPasswordIdSet == null) {
                passkeyDao.getAllPasskeysSync()
                    .mapNotNull { it.boundPasswordId }
                    .toSet()
            } else {
                if (scopedPasswordIdSet.isEmpty()) return@withContext emptySet()
                scopedPasswordIdSet
                    .toList()
                    .chunked(800)
                    .flatMap { ids -> passkeyDao.getByBoundPasswordIds(ids) }
                    .mapNotNull { it.boundPasswordId }
                    .toSet()
            }
        }
    }

    private fun hasInlinePasskeyBinding(entry: PasswordEntry): Boolean {
        return PasskeyBindingCodec.decodeList(entry.passkeyBindings).isNotEmpty()
    }

    /**
     * 哈希密码用于分组（不用于安全目的）
     */
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun extractDomain(url: String): String {
        val normalized = normalizeUrl(url)
        return normalized.split("/").firstOrNull() ?: ""
    }

    private fun buildScopedIdSet(
        passwords: List<PasswordEntry>,
        scope: SecurityAnalysisScopeOption
    ): Set<Long>? {
        return when (scope.type) {
            SecurityAnalysisScopeType.ALL -> passwords
                .asSequence()
                .filter { isIncludedInVisibleSources(it) }
                .map { it.id }
                .toSet()
            SecurityAnalysisScopeType.LOCAL -> passwords
                .asSequence()
                .filter { isMonicaDatabaseEntry(it) }
                .map { it.id }
                .toSet()
            SecurityAnalysisScopeType.KEEPASS -> passwords
                .asSequence()
                .filter {
                    it.keepassDatabaseId == scope.sourceId &&
                        it.keepassDatabaseId in keepassNameByIdCache
                }
                .map { it.id }
                .toSet()
            SecurityAnalysisScopeType.BITWARDEN -> passwords
                .asSequence()
                .filter {
                    it.bitwardenVaultId == scope.sourceId &&
                        it.bitwardenVaultId in bitwardenNameByIdCache
                }
                .map { it.id }
                .toSet()
        }
    }

    private suspend fun ensureSourceNameCaches() {
        val keepassMap = withContext(Dispatchers.IO) {
            localKeePassDatabaseDao.getAllDatabasesSync()
                // 与本地 KeePass 管理页保持一致：安全分析范围不展示 WebDAV 挂载库
                .filterNot { it.isWebDavDatabase() }
                .associate { db ->
                    db.id to db.name.trim().ifBlank { "KeePass #${db.id}" }
                }
        }
        val bitwardenMap = withContext(Dispatchers.IO) {
            bitwardenVaultDao.getAllVaults()
                .associate { vault ->
                    val fallback = vault.email
                    val label = vault.displayName?.trim().takeUnless { it.isNullOrBlank() } ?: fallback
                    vault.id to label
                }
        }
        keepassNameByIdCache = keepassMap
        bitwardenNameByIdCache = bitwardenMap
    }

    private suspend fun buildScopeOptions(passwords: List<PasswordEntry>): List<SecurityAnalysisScopeOption> {
        ensureSourceNameCaches()
        val allCount = passwords.count { isIncludedInVisibleSources(it) }
        val localCount = passwords.count { isMonicaDatabaseEntry(it) }

        val keepassScopes = passwords
            .asSequence()
            .mapNotNull { it.keepassDatabaseId }
            .filter { it in keepassNameByIdCache }
            .distinct()
            .sortedWith(compareBy<Long> { id ->
                keepassNameByIdCache[id]?.lowercase() ?: ""
            }.thenBy { it })
            .map { id ->
                SecurityAnalysisScopeOption.keepass(
                    id = id,
                    name = keepassNameByIdCache[id],
                    itemCount = passwords.count { it.keepassDatabaseId == id }
                )
            }
            .toList()
        val bitwardenScopes = passwords
            .asSequence()
            .mapNotNull { it.bitwardenVaultId }
            .filter { it in bitwardenNameByIdCache }
            .distinct()
            .sortedWith(compareBy<Long> { id ->
                bitwardenNameByIdCache[id]?.lowercase() ?: ""
            }.thenBy { it })
            .map { id ->
                SecurityAnalysisScopeOption.bitwarden(
                    id = id,
                    name = bitwardenNameByIdCache[id],
                    itemCount = passwords.count { it.bitwardenVaultId == id }
                )
            }
            .toList()

        return buildList {
            add(SecurityAnalysisScopeOption.all(itemCount = allCount))
            add(SecurityAnalysisScopeOption.local(itemCount = localCount))
            addAll(keepassScopes)
            addAll(bitwardenScopes)
        }
    }

    private fun isMonicaDatabaseEntry(entry: PasswordEntry): Boolean {
        return entry.keepassDatabaseId == null && entry.bitwardenVaultId == null
    }

    private fun isIncludedInVisibleSources(entry: PasswordEntry): Boolean {
        if (isMonicaDatabaseEntry(entry)) return true
        val hasExistingKeePassBinding = entry.keepassDatabaseId?.let { it in keepassNameByIdCache } == true
        if (hasExistingKeePassBinding) return true
        val hasExistingBitwardenBinding = entry.bitwardenVaultId?.let { it in bitwardenNameByIdCache } == true
        return hasExistingBitwardenBinding
    }

    private fun toStrengthBucket(password: String): StrengthBucket {
        return when (PasswordStrengthAnalyzer.getStrengthLevel(PasswordStrengthAnalyzer.calculateStrength(password))) {
            PasswordStrengthAnalyzer.StrengthLevel.VERY_WEAK,
            PasswordStrengthAnalyzer.StrengthLevel.WEAK -> StrengthBucket.WEAK
            PasswordStrengthAnalyzer.StrengthLevel.FAIR -> StrengthBucket.MEDIUM
            PasswordStrengthAnalyzer.StrengthLevel.STRONG -> StrengthBucket.STRONG
            PasswordStrengthAnalyzer.StrengthLevel.VERY_STRONG -> StrengthBucket.VERY_STRONG
        }
    }

    private data class CachedAnalysisEntry(
        val entry: PasswordEntry,
        val passwordHash: String?,
        val normalizedUrl: String?,
        val domain: String?,
        val supports2FA: Boolean,
        val strengthBucket: StrengthBucket?
    )

    private enum class StrengthBucket {
        WEAK,
        MEDIUM,
        STRONG,
        VERY_STRONG
    }

    private data class ChangeSummary(
        val added: Int = 0,
        val updated: Int = 0,
        val removed: Int = 0
    )

    private data class AnalysisResult(
        val duplicatePasswords: List<DuplicatePasswordGroup>,
        val duplicateUrls: List<DuplicateUrlGroup>,
        val no2FAAccounts: List<No2FAAccount>,
        val inactivePasskeyAccounts: List<InactivePasskeyAccount>,
        val distribution: PasswordStrengthDistribution,
        val score: Int
    )
}
