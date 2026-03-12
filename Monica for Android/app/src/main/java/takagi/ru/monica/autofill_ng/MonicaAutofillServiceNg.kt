package takagi.ru.monica.autofill_ng

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.view.inputmethod.InlineSuggestionsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.autofill_ng.AutofillPreferences
import takagi.ru.monica.autofill_ng.AutofillSaveTransparentActivity
import takagi.ru.monica.autofill_ng.DomainMatchStrategy
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill_ng.processor.AutofillProcessorNg
import takagi.ru.monica.autofill_ng.core.AutofillDiagnostics
import takagi.ru.monica.autofill_ng.core.AutofillLogger
import takagi.ru.monica.autofill_ng.core.safeTextOrNull
import takagi.ru.monica.autofill_ng.BitwardenLikeAutofillMatcherNg
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.utils.DeviceUtils
import java.security.MessageDigest

/**
 * Autofill service (Bitwarden engine).
 *
 * This service intentionally keeps a single deterministic pipeline:
 * parser -> bitwarden-style matcher -> bw-compatible response.
 */
class MonicaAutofillServiceNg : AutofillService() {
    private companion object {
        private val PACKAGE_NAME_REGEX =
            Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var passwordRepository: PasswordRepository
    private lateinit var autofillPreferences: AutofillPreferences
    private lateinit var diagnostics: AutofillDiagnostics

    private val parser = EnhancedAutofillStructureParserV2()
    private val matcher = BitwardenLikeAutofillMatcherNg()
    private val bwCompatProcessor by lazy { AutofillProcessorNg(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        AutofillLogger.initialize(applicationContext)

        val database = PasswordDatabase.getDatabase(applicationContext)
        passwordRepository = PasswordRepository(database.passwordEntryDao())
        autofillPreferences = AutofillPreferences(applicationContext)
        diagnostics = AutofillDiagnostics(applicationContext)
        scope.launch {
            runCatching { autofillPreferences.ensureBitwardenV2EngineMode() }
                .onFailure { AutofillLogger.w("AF", "Failed to enforce V2 engine mode: ${it.message}") }
        }

        AutofillLogger.i("AF", "MonicaAutofillServiceNg created")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val job = scope.launch {
            try {
                val response = withContext(Dispatchers.Default) {
                    processFillRequest(request, cancellationSignal)
                }
                callback.onSuccess(response)
            } catch (e: Exception) {
                AutofillLogger.e("AF", "onFillRequest failed", e)
                diagnostics.logError("AF", "Fill request failed: ${e.message}", e)
                callback.onFailure(e.message ?: "Autofill failed")
            }
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    private suspend fun processFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
    ): FillResponse? {
        if (cancellationSignal.isCanceled) return null
        if (!autofillPreferences.isAutofillEnabled.first()) return null

        val fillContext = request.fillContexts.lastOrNull() ?: return null
        val structure = fillContext.structure
        val fallbackPackage = structure.activityComponent?.packageName.orEmpty()

        if (fallbackPackage.isNotBlank() && autofillPreferences.isInBlacklist(fallbackPackage)) {
            AutofillLogger.i("AF", "Package blocked by blacklist: $fallbackPackage")
            return null
        }

        val respectAutofillOff = autofillPreferences.isV2RespectAutofillOffEnabled.first()
        val parsed = parser.parse(structure, respectAutofillOff = respectAutofillOff)
        val packageName = resolveEffectivePackageName(
            parsedApplicationId = parsed.applicationId,
            fallbackPackage = fallbackPackage,
        )
        val isCompatMode = (request.flags or FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST) == request.flags
        AutofillLogger.i(
            "AF",
            "Autofill request source diagnostics",
            metadata = mapOf(
                "sdk" to Build.VERSION.SDK_INT,
                "device" to "${Build.MANUFACTURER}/${Build.MODEL}",
                "windowNodeCount" to structure.windowNodeCount,
                "fallbackPackage" to if (fallbackPackage.isBlank()) "none" else fallbackPackage,
                "parsedApplicationId" to (parsed.applicationId ?: "none"),
                "resolvedPackageName" to if (packageName.isBlank()) "none" else packageName,
                "parsedWebDomain" to (parsed.webDomain ?: "none"),
                "parsedWebScheme" to (parsed.webScheme ?: "none"),
                "parsedItemCount" to parsed.items.size,
                "respectAutofillOff" to respectAutofillOff,
                "compatMode" to isCompatMode,
            )
        )
        if (packageName.isBlank()) return null

        val credentialTargets = selectCredentialTargets(parsed.items)
        if (credentialTargets.isEmpty()) {
            AutofillLogger.i("AF", "No credential fields detected")
            return null
        }

        val webDomain = parsed.webDomain?.takeIf { it.isNotBlank() }
        val fieldSignatureKey = buildFieldSignatureKey(
            packageName = packageName,
            webDomain = webDomain,
            credentialTargets = credentialTargets,
        )
        AutofillLogger.i(
            "AF",
            "Autofill target diagnostics",
            metadata = mapOf(
                "packageName" to packageName,
                "webDomain" to (webDomain ?: "none"),
                "targetCount" to credentialTargets.size,
                "fieldSignaturePresent" to !fieldSignatureKey.isNullOrBlank(),
                "fieldSignatureKey" to (fieldSignatureKey ?: "none"),
                "focusedTargetCount" to credentialTargets.count { it.isFocused },
                "visibleTargetCount" to credentialTargets.count { it.isVisible },
            )
        )
        if (!fieldSignatureKey.isNullOrBlank() &&
            autofillPreferences.isFieldSignatureBlocked(fieldSignatureKey)
        ) {
            AutofillLogger.i(
                "AF",
                "Skip autofill request: blocked field signature",
                metadata = mapOf(
                    "packageName" to packageName,
                    "webDomain" to (webDomain ?: "none"),
                    "targetCount" to credentialTargets.size,
                ),
            )
            return null
        }
        val requestUri = webDomain?.let { "https://$it" } ?: "androidapp://$packageName"
        val appDisplayName = resolveAppDisplayName(packageName)

        val allPasswords = passwordRepository.getAllPasswordEntries().first()
        val sourceFilter = autofillPreferences.v2DefaultSourceFilter.first()
        val defaultKeepassDatabaseId = autofillPreferences.v2DefaultKeepassDatabaseId.first()
        val defaultBitwardenVaultId = autofillPreferences.v2DefaultBitwardenVaultId.first()
        val scopedPasswords = applyDefaultSourceFilter(
            entries = allPasswords,
            sourceFilter = sourceFilter,
            keepassDatabaseId = defaultKeepassDatabaseId,
            bitwardenVaultId = defaultBitwardenVaultId,
        )
        val strictOnly = autofillPreferences.isBitwardenStrictModeEnabled.first()
        val allowSubdomainToggle = autofillPreferences.isBitwardenSubdomainMatchEnabled.first()
        val uriStrategy = autofillPreferences.domainMatchStrategy.first()
        val uriConfig = resolveUriStrategyConfig(uriStrategy, allowSubdomainToggle)
        val matchedPasswords = if (uriConfig.disableMatch) {
            emptyList()
        } else {
            matcher.match(
                entries = scopedPasswords,
                packageName = packageName,
                webDomain = webDomain,
                appDisplayName = appDisplayName,
                config = BitwardenLikeAutofillMatcherNg.Config(
                    strictOnly = strictOnly,
                    allowSubdomainMatch = uriConfig.allowSubdomainMatch,
                    allowBaseDomainMatch = uriConfig.allowBaseDomainMatch,
                    exactDomainOnly = uriConfig.exactDomainOnly,
                    maxSuggestions = 20,
                ),
            )
        }

        diagnostics.logPasswordMatching(
            packageName = packageName,
            domain = webDomain,
            matchStrategy = if (strictOnly) "bitwarden_v2_strict" else "bitwarden_v2_hybrid",
            totalPasswords = scopedPasswords.size,
            matchedPasswords = matchedPasswords.size,
        )

        val inlineRequest = getInlineRequest(request)
        val response = bwCompatProcessor.process(
            packageName = packageName,
            uri = requestUri,
            credentialTargets = credentialTargets,
            inlineRequest = inlineRequest,
            isCompatMode = isCompatMode,
            passwords = matchedPasswords,
            fieldSignatureKey = fieldSignatureKey,
        )

        if (response == null) {
            AutofillLogger.w("AF", "No fill response produced")
        } else {
            AutofillLogger.i(
                "AF",
                "Fill response ready: package=$packageName, domain=$webDomain, targets=${credentialTargets.size}, matches=${matchedPasswords.size}",
            )
        }
        return response
    }

    private data class UriStrategyConfig(
        val allowSubdomainMatch: Boolean,
        val allowBaseDomainMatch: Boolean,
        val exactDomainOnly: Boolean,
        val disableMatch: Boolean = false,
    )

    private fun resolveUriStrategyConfig(
        strategy: DomainMatchStrategy,
        allowSubdomainToggle: Boolean,
    ): UriStrategyConfig {
        return when (strategy) {
            DomainMatchStrategy.BASE_DOMAIN -> UriStrategyConfig(
                allowSubdomainMatch = allowSubdomainToggle,
                allowBaseDomainMatch = true,
                exactDomainOnly = false,
            )

            DomainMatchStrategy.DOMAIN -> UriStrategyConfig(
                allowSubdomainMatch = allowSubdomainToggle,
                allowBaseDomainMatch = false,
                exactDomainOnly = false,
            )

            DomainMatchStrategy.EXACT_MATCH -> UriStrategyConfig(
                allowSubdomainMatch = false,
                allowBaseDomainMatch = false,
                exactDomainOnly = true,
            )

            DomainMatchStrategy.NEVER -> UriStrategyConfig(
                allowSubdomainMatch = false,
                allowBaseDomainMatch = false,
                exactDomainOnly = false,
                disableMatch = true,
            )

            DomainMatchStrategy.STARTS_WITH,
            DomainMatchStrategy.REGEX -> {
                AutofillLogger.w(
                    "AF",
                    "URI strategy $strategy is not natively supported by NG matcher; fallback to BASE_DOMAIN",
                )
                UriStrategyConfig(
                    allowSubdomainMatch = allowSubdomainToggle,
                    allowBaseDomainMatch = true,
                    exactDomainOnly = false,
                )
            }
        }
    }

    private fun applyDefaultSourceFilter(
        entries: List<PasswordEntry>,
        sourceFilter: AutofillPreferences.AutofillDefaultSourceFilter,
        keepassDatabaseId: Long?,
        bitwardenVaultId: Long?,
    ): List<PasswordEntry> {
        return when (sourceFilter) {
            AutofillPreferences.AutofillDefaultSourceFilter.ALL -> entries
            AutofillPreferences.AutofillDefaultSourceFilter.LOCAL -> entries.filter { entry ->
                entry.keepassDatabaseId == null && entry.bitwardenVaultId == null
            }
            AutofillPreferences.AutofillDefaultSourceFilter.KEEPASS -> entries.filter { entry ->
                entry.keepassDatabaseId != null &&
                    (keepassDatabaseId == null || entry.keepassDatabaseId == keepassDatabaseId)
            }
            AutofillPreferences.AutofillDefaultSourceFilter.BITWARDEN -> entries.filter { entry ->
                entry.bitwardenVaultId != null &&
                    (bitwardenVaultId == null || entry.bitwardenVaultId == bitwardenVaultId)
            }
        }
    }

    private fun getInlineRequest(request: FillRequest): InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!DeviceUtils.supportsInlineSuggestions()) return null
        return request.inlineSuggestionsRequest
    }

    private fun selectCredentialTargets(items: List<ParsedItem>): List<ParsedItem> {
        if (items.isEmpty()) return emptyList()

        val filtered = items.filter { item ->
            item.hint == FieldHint.USERNAME ||
                item.hint == FieldHint.EMAIL_ADDRESS ||
                item.hint == FieldHint.PHONE_NUMBER ||
                item.hint == FieldHint.PASSWORD ||
                item.hint == FieldHint.NEW_PASSWORD
        }
        if (filtered.isEmpty()) return emptyList()

        // Keep targets close to Bitwarden behavior:
        // when a field is already populated, avoid anchoring manual-entry datasets on that
        // non-focused field as it can cause the framework to suppress the suggestion row.
        val hasFocusedCredential = filtered.any { it.isFocused }
        val preferredTargets = filtered.filter { item ->
            val hasValue = !item.value.isNullOrBlank()
            when {
                item.isFocused -> true
                !hasValue -> true
                hasFocusedCredential -> false
                else -> true
            }
        }.ifEmpty { filtered }

        val deduped = linkedMapOf<String, ParsedItem>()
        preferredTargets.sortedWith(
            compareByDescending<ParsedItem> { it.isFocused }
                .thenByDescending { hintPriority(it.hint) }
                .thenByDescending { it.accuracy.score }
                .thenBy { it.traversalIndex },
        ).forEach { item ->
            deduped.putIfAbsent(item.id.toString(), item)
        }

        return deduped.values.toList()
    }

    private fun hintPriority(hint: FieldHint): Int = when (hint) {
        FieldHint.PASSWORD, FieldHint.NEW_PASSWORD -> 3
        FieldHint.USERNAME, FieldHint.EMAIL_ADDRESS, FieldHint.PHONE_NUMBER -> 2
        else -> 0
    }

    private fun buildFieldSignatureKey(
        packageName: String,
        webDomain: String?,
        credentialTargets: List<ParsedItem>,
    ): String? {
        if (credentialTargets.isEmpty()) return null
        val normalizedPackage = packageName.trim().lowercase()
        if (normalizedPackage.isBlank()) return null
        val normalizedDomain = webDomain?.trim()?.lowercase().orEmpty()
        val targetSummary = credentialTargets
            .sortedWith(compareBy<ParsedItem> { it.traversalIndex }.thenBy { it.hint.name })
            .joinToString(separator = "|") { item ->
                buildString {
                    append(item.hint.name)
                    append('@')
                    append(item.traversalIndex)
                    append('@')
                    append(item.parentWebViewNodeId ?: -1)
                    append('@')
                    append(if (item.isVisible) '1' else '0')
                }
            }
        if (targetSummary.isBlank()) return null
        val rawSignature = buildString {
            append(normalizedPackage)
            append('|')
            append(normalizedDomain)
            append('|')
            append(targetSummary)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawSignature.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        scope.launch {
            try {
                if (!autofillPreferences.isRequestSaveDataEnabled.first()) {
                    callback.onSuccess()
                    return@launch
                }

                val saveIntent = withContext(Dispatchers.Default) { buildSaveIntent(request) }
                if (saveIntent == null) {
                    callback.onSuccess()
                    return@launch
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val code = (System.currentTimeMillis().toInt() and 0x7FFFFFFF)
                    val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    val pendingIntent = PendingIntent.getActivity(
                        this@MonicaAutofillServiceNg,
                        code,
                        saveIntent,
                        flags,
                    )
                    callback.onSuccess(pendingIntent.intentSender)
                } else {
                    saveIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(saveIntent)
                    callback.onSuccess()
                }
            } catch (e: Exception) {
                AutofillLogger.e("AF", "onSaveRequest failed", e)
                callback.onFailure(e.message ?: "Save failed")
            }
        }
    }

    private suspend fun buildSaveIntent(request: SaveRequest): Intent? {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return null
        val parsed = parser.parse(
            structure = structure,
            respectAutofillOff = false,
        )

        val usernameId = parsed.items.firstOrNull {
            it.hint == FieldHint.USERNAME ||
                it.hint == FieldHint.EMAIL_ADDRESS ||
                it.hint == FieldHint.PHONE_NUMBER
        }?.id
        val passwordId = parsed.items.firstOrNull {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }?.id

        val username = usernameId?.let { extractTextFromStructure(structure, it) }.orEmpty()
        val password = passwordId?.let { extractTextFromStructure(structure, it) }.orEmpty()
        if (password.isBlank()) {
            AutofillLogger.i("AF", "Skip save request: no password value")
            return null
        }

        val packageName = resolveEffectivePackageName(
            parsedApplicationId = parsed.applicationId,
            fallbackPackage = structure.activityComponent?.packageName.orEmpty(),
        )
        val website = parsed.webDomain.orEmpty()

        if (packageName.isNotBlank() && autofillPreferences.isInBlacklist(packageName)) {
            AutofillLogger.i("AF", "Skip save request: package in blacklist ($packageName)")
            return null
        }
        if (autofillPreferences.isSaveBlocked(packageName = packageName, webDomain = website)) {
            AutofillLogger.i("AF", "Skip save request: blocked target (pkg=$packageName, domain=$website)")
            return null
        }

        return Intent(this, AutofillSaveTransparentActivity::class.java).apply {
            putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME, username)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD, password)
            putExtra(AutofillSaveTransparentActivity.EXTRA_WEBSITE, website)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME, packageName)
        }
    }

    private fun extractTextFromStructure(
        structure: AssistStructure,
        targetId: AutofillId,
    ): String? {
        for (index in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(index).rootViewNode
            val value = extractTextFromNode(root, targetId)
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun extractTextFromNode(
        node: AssistStructure.ViewNode,
        targetId: AutofillId,
    ): String? {
        if (node.autofillId == targetId) {
            return node.autofillValue.safeTextOrNull(
                tag = "AF",
                fieldDescription = node.idEntry ?: node.className ?: "field",
            )
        }
        for (childIndex in 0 until node.childCount) {
            val child = node.getChildAt(childIndex) ?: continue
            if (child.visibility != View.VISIBLE) continue
            val value = extractTextFromNode(child, targetId)
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    override fun onConnected() {
        super.onConnected()
        AutofillLogger.i("AF", "Service connected")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        AutofillLogger.i("AF", "Service disconnected")
    }

    private fun resolveEffectivePackageName(
        parsedApplicationId: String?,
        fallbackPackage: String,
    ): String {
        val parsedCandidate = parsedApplicationId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore(':')
        val fallbackCandidate = fallbackPackage
            .trim()
            .substringBefore(':')
        return when {
            !parsedCandidate.isNullOrBlank() && isLikelyAndroidPackageName(parsedCandidate) -> parsedCandidate
            fallbackCandidate.isNotBlank() -> fallbackCandidate
            else -> parsedCandidate.orEmpty()
        }
    }

    private fun isLikelyAndroidPackageName(value: String): Boolean {
        if (value.length !in 3..255) return false
        if (!value.contains('.')) return false
        return PACKAGE_NAME_REGEX.matches(value)
    }

    private fun resolveAppDisplayName(packageName: String): String? {
        if (packageName.isBlank()) return null
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}


