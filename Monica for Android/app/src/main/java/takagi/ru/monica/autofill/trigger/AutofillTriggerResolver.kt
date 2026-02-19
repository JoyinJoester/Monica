package takagi.ru.monica.autofill.trigger

import android.view.autofill.AutofillId
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.ParsedStructure

class AutofillTriggerResolver(
    private val sessionTtlMs: Long = 3 * 60 * 1000L,
    private val maxSessions: Int = 48
) {
    data class ResolveInput(
        val parsedStructure: ParsedStructure,
        val compatibilityTargets: List<ParsedItem>,
        val historicalTargets: List<ParsedItem>,
        val packageName: String,
        val webDomain: String?,
        val hasPasswordSignalNow: Boolean,
        val activityClassName: String?,
        val focusedAutofillId: AutofillId?
    )

    data class ResolveResult(
        val parsedStructure: ParsedStructure,
        val targets: List<ParsedItem>,
        val compatibilityAdded: Int,
        val historicalAdded: Int,
        val sessionRecoveryAdded: Int,
        val canRecoverFromSessionNow: Boolean
    )

    private data class SessionTargets(
        val usernameId: AutofillId? = null,
        val passwordId: AutofillId? = null,
        val updatedAt: Long = 0L
    )

    private val sessionTargets = mutableMapOf<String, SessionTargets>()

    fun clearSession() {
        sessionTargets.clear()
    }

    fun resolve(input: ResolveInput): ResolveResult {
        cleanupExpiredSessions()

        val sessionKey = buildSessionKey(
            packageName = input.packageName,
            domain = input.webDomain,
            isWebView = input.parsedStructure.webView,
            activityClassName = input.activityClassName
        )
        val cached = sessionTargets[sessionKey]

        var mergedItems = mergeCredentialItems(input.parsedStructure.items, input.compatibilityTargets)
        mergedItems = mergeCredentialItems(mergedItems, input.historicalTargets)
        var effectiveParsedStructure = if (mergedItems === input.parsedStructure.items) {
            input.parsedStructure
        } else {
            input.parsedStructure.copy(items = mergedItems)
        }
        var effectiveTargets = selectCredentialTargets(effectiveParsedStructure)

        val canRecover = shouldRecoverFromSession(
            existingTargets = effectiveTargets,
            cached = cached,
            focusedAutofillId = input.focusedAutofillId
        )

        var sessionRecoveryAdded = 0
        if (canRecover) {
            val recovered = collectSessionRecoveryTargets(
                existingTargets = effectiveTargets,
                cached = cached!!
            )
            sessionRecoveryAdded = recovered.size
            if (recovered.isNotEmpty()) {
                mergedItems = mergeCredentialItems(mergedItems, recovered)
                effectiveParsedStructure = if (mergedItems === input.parsedStructure.items) {
                    input.parsedStructure
                } else {
                    input.parsedStructure.copy(items = mergedItems)
                }
                effectiveTargets = selectCredentialTargets(effectiveParsedStructure)
            }
        }

        updateSession(
            sessionKey = sessionKey,
            cached = cached,
            effectiveTargets = effectiveTargets,
            hasPasswordSignalNow = input.hasPasswordSignalNow
        )

        return ResolveResult(
            parsedStructure = effectiveParsedStructure,
            targets = effectiveTargets,
            compatibilityAdded = input.compatibilityTargets.size,
            historicalAdded = input.historicalTargets.size,
            sessionRecoveryAdded = sessionRecoveryAdded,
            canRecoverFromSessionNow = canRecover
        )
    }

    private fun selectCredentialTargets(parsedStructure: ParsedStructure): List<ParsedItem> {
        return parsedStructure.items.filter { item ->
            item.hint == FieldHint.USERNAME ||
                item.hint == FieldHint.EMAIL_ADDRESS ||
                item.hint == FieldHint.PASSWORD ||
                item.hint == FieldHint.NEW_PASSWORD
        }
    }

    private fun mergeCredentialItems(existingItems: List<ParsedItem>, newItems: List<ParsedItem>): List<ParsedItem> {
        if (newItems.isEmpty()) return existingItems
        val merged = existingItems.toMutableList()
        newItems.forEach { candidate ->
            val duplicated = merged.any { it.id == candidate.id && it.hint == candidate.hint }
            if (!duplicated) {
                merged.add(candidate)
            }
        }
        return merged
    }

    private fun shouldRecoverFromSession(
        existingTargets: List<ParsedItem>,
        cached: SessionTargets?,
        focusedAutofillId: AutofillId?
    ): Boolean {
        if (!hasUsableSessionPair(cached)) return false

        val hasUsername = existingTargets.any {
            it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL_ADDRESS
        }
        val hasPassword = existingTargets.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }
        if (hasUsername && hasPassword) return false

        val usernameId = cached!!.usernameId
        val passwordId = cached.passwordId
        if (focusedAutofillId != null && (focusedAutofillId == usernameId || focusedAutofillId == passwordId)) {
            return true
        }
        return existingTargets.any { target ->
            target.id == usernameId || target.id == passwordId
        }
    }

    private fun collectSessionRecoveryTargets(
        existingTargets: List<ParsedItem>,
        cached: SessionTargets
    ): List<ParsedItem> {
        val existingIds = existingTargets.map { it.id }.toSet()
        val hasUsername = existingTargets.any {
            it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL_ADDRESS
        }
        val hasPassword = existingTargets.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }

        val recovered = mutableListOf<ParsedItem>()
        if (!hasUsername && cached.usernameId != null && !existingIds.contains(cached.usernameId)) {
            recovered.add(createRecoveryParsedItem(cached.usernameId, FieldHint.USERNAME, recovered.size))
        }
        if (!hasPassword && cached.passwordId != null &&
            cached.passwordId != cached.usernameId &&
            !existingIds.contains(cached.passwordId)
        ) {
            recovered.add(createRecoveryParsedItem(cached.passwordId, FieldHint.PASSWORD, recovered.size))
        }
        return recovered
    }

    private fun createRecoveryParsedItem(id: AutofillId, hint: FieldHint, order: Int): ParsedItem {
        return ParsedItem(
            id = id,
            hint = hint,
            accuracy = EnhancedAutofillStructureParserV2.Accuracy.LOWEST,
            value = null,
            isFocused = false,
            isVisible = true,
            traversalIndex = Int.MAX_VALUE - order
        )
    }

    private fun updateSession(
        sessionKey: String,
        cached: SessionTargets?,
        effectiveTargets: List<ParsedItem>,
        hasPasswordSignalNow: Boolean
    ) {
        val bestUsername = effectiveTargets
            .filter { it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL_ADDRESS }
            .sortedWith(compareByDescending<ParsedItem> { it.isFocused }.thenBy { it.traversalIndex })
            .firstOrNull()
            ?.id
        val bestPassword = effectiveTargets
            .filter { it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD }
            .sortedWith(compareByDescending<ParsedItem> { it.isFocused }.thenBy { it.traversalIndex })
            .firstOrNull()
            ?.id

        if (!hasPasswordSignalNow && bestPassword == null && cached?.passwordId == null) return

        val resolvedUsername = bestUsername ?: cached?.usernameId
        val resolvedPassword = bestPassword ?: cached?.passwordId
        if (resolvedUsername == null && resolvedPassword == null) return

        val normalizedPassword = if (resolvedPassword != null && resolvedPassword == resolvedUsername) {
            null
        } else {
            resolvedPassword
        }
        sessionTargets[sessionKey] = SessionTargets(
            usernameId = resolvedUsername,
            passwordId = normalizedPassword,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun hasUsableSessionPair(cached: SessionTargets?): Boolean {
        return cached?.usernameId != null && cached.passwordId != null
    }

    private fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        sessionTargets.entries.removeAll { (_, value) ->
            now - value.updatedAt > sessionTtlMs
        }
        if (sessionTargets.size <= maxSessions) return

        val oldestKeys = sessionTargets.entries
            .sortedBy { it.value.updatedAt }
            .take(sessionTargets.size - maxSessions)
            .map { it.key }
        oldestKeys.forEach { sessionTargets.remove(it) }
    }

    private fun buildSessionKey(
        packageName: String,
        domain: String?,
        isWebView: Boolean,
        activityClassName: String?
    ): String {
        val normalizedPackage = packageName.trim().lowercase()
        val normalizedDomain = normalizeDomain(domain).orEmpty()
        val normalizedActivity = activityClassName?.trim()?.lowercase().orEmpty()
        return if (isWebView && normalizedDomain.isNotBlank()) {
            "web:$normalizedDomain"
        } else {
            if (normalizedActivity.isNotBlank()) {
                "app:$normalizedPackage:$normalizedActivity"
            } else {
                "app:$normalizedPackage"
            }
        }
    }

    private fun normalizeDomain(rawDomain: String?): String? {
        val normalized = rawDomain?.trim()?.lowercase()?.ifBlank { null } ?: return null
        val withoutScheme = normalized.substringAfter("://", normalized)
        val hostAndMaybePath = withoutScheme.substringBefore('/')
        val host = hostAndMaybePath.substringBefore(':').removePrefix("www.")
        return host.ifBlank { null }
    }
}

