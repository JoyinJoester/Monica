package takagi.ru.monica.autofill_ng

import android.app.Activity
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill_ng.builder.AutofillDatasetBuilder
import takagi.ru.monica.autofill_ng.core.AutofillLogger
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager

class AutofillCipherCallbackActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_ARGS = "extra_args"
        private const val TAG = "AutofillCipherCallback"

        fun getIntent(context: Context, args: Args): Intent {
            return Intent(context, AutofillCipherCallbackActivity::class.java).apply {
                putExtra(EXTRA_ARGS, args)
            }
        }
    }

    @Parcelize
    data class Args(
        val passwordId: Long,
        val applicationId: String? = null,
        val webDomain: String? = null,
        val interactionIdentifier: String? = null,
        val interactionIdentifierAliases: ArrayList<String>? = null,
        val autofillIds: ArrayList<AutofillId>? = null,
        val autofillHints: ArrayList<String>? = null,
        val fieldSignatureKey: String? = null,
        val rememberLastFilled: Boolean = true,
    ) : Parcelable

    private val args: Args? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ARGS, Args::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ARGS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        lifecycleScope.launch {
            completeCipherAutofill()
        }
    }

    private suspend fun completeCipherAutofill() {
        val callbackArgs = args ?: run {
            cancelAndFinish("missing_args")
            return
        }
        val repository = PasswordRepository(
            PasswordDatabase.getDatabase(applicationContext).passwordEntryDao()
        )
        val passwordEntry = withContext(Dispatchers.IO) {
            repository.getPasswordEntryById(callbackArgs.passwordId)
        } ?: run {
            cancelAndFinish("missing_password_entry")
            return
        }

        val securityManager = SecurityManager(applicationContext)
        val accountValue = AccountFillPolicy.resolveAccountIdentifier(passwordEntry, securityManager)
        val decryptedPassword = AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = passwordEntry.password,
            logTag = TAG,
        )
        val resolvedTargets = resolveAutofillTargets(callbackArgs)
        if (resolvedTargets.ids.isEmpty()) {
            cancelAndFinish("missing_autofill_ids")
            return
        }
        val filledValues = resolveFilledValues(
            accountValue = accountValue,
            decryptedPassword = decryptedPassword,
            autofillIds = resolvedTargets.ids,
            autofillHints = resolvedTargets.hints,
        )
        if (filledValues.isEmpty()) {
            cancelAndFinish("no_resolved_values")
            return
        }

        val title = passwordEntry.title.ifBlank {
            getString(R.string.autofill_manual_entry_title)
        }
        val subtitle = accountValue.ifBlank {
            callbackArgs.webDomain
                ?: callbackArgs.applicationId
                ?: getString(R.string.app_name)
        }
        val menuPresentation = AutofillDatasetBuilder.RemoteViewsFactory.createPasswordEntry(
            context = this,
            title = title,
            username = subtitle
        )
        val fields = linkedMapOf<AutofillId, AutofillDatasetBuilder.FieldData?>()
        filledValues.forEach { (autofillId, value) ->
            fields[autofillId] = AutofillDatasetBuilder.FieldData(
                value = AutofillValue.forText(value),
                presentation = menuPresentation
            )
        }
        val dataset = AutofillDatasetBuilder.create(
            menuPresentation = menuPresentation,
            fields = fields
        ) { null }.build()

        withContext(Dispatchers.IO) {
            if (callbackArgs.rememberLastFilled) {
                rememberLastFilledCredential(
                    passwordId = passwordEntry.id,
                    primaryIdentifier = callbackArgs.interactionIdentifier,
                    aliases = callbackArgs.interactionIdentifierAliases.orEmpty(),
                )
            }
            rememberLearnedFieldSignature(callbackArgs.fieldSignatureKey)
        }

        AutofillLogger.i(
            "CALLBACK",
            "Returning authenticated dataset without picker UI",
            metadata = mapOf(
                "passwordId" to passwordEntry.id,
                "filledCount" to filledValues.size,
                "applicationId" to (callbackArgs.applicationId ?: "none"),
                "webDomain" to (callbackArgs.webDomain ?: "none"),
                "targetSource" to resolvedTargets.source,
            )
        )

        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }
        )
        finishWithoutAnimation()
    }

    private data class ResolvedAutofillTargets(
        val ids: List<AutofillId>,
        val hints: List<String>,
        val source: String,
    )

    private fun resolveAutofillTargets(callbackArgs: Args): ResolvedAutofillTargets {
        val assistStructure = getAssistStructureOrNull()
        if (assistStructure != null) {
            val parsedTargets = runCatching {
                val parser = EnhancedAutofillStructureParserV2()
                val parsed = parser.parse(assistStructure, respectAutofillOff = false)
                selectLoginFillableTargets(parsed.items)
            }.getOrDefault(emptyList())

            if (parsedTargets.isNotEmpty()) {
                return ResolvedAutofillTargets(
                    ids = parsedTargets.map { it.id },
                    hints = parsedTargets.map { it.hint.name },
                    source = "assist_structure",
                )
            }
        }

        return ResolvedAutofillTargets(
            ids = callbackArgs.autofillIds?.distinct().orEmpty(),
            hints = callbackArgs.autofillHints.orEmpty(),
            source = "callback_args",
        )
    }

    private fun getAssistStructureOrNull(): AssistStructure? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        }
    }

    private fun selectLoginFillableTargets(items: List<ParsedItem>): List<ParsedItem> {
        if (items.isEmpty()) return emptyList()
        val filtered = items.filter { isLoginHint(it.hint) }
        if (filtered.isEmpty()) return emptyList()

        val deduped = linkedMapOf<String, ParsedItem>()
        filtered.sortedWith(
            compareByDescending<ParsedItem> { it.isFocused }
                .thenByDescending { loginHintPriority(it.hint) }
                .thenByDescending { it.accuracy.score }
                .thenBy { it.traversalIndex }
        ).forEach { item ->
            deduped.putIfAbsent(item.id.toString(), item)
        }
        return deduped.values.toList()
    }

    private fun loginHintPriority(hint: FieldHint): Int = when (hint) {
        FieldHint.PASSWORD, FieldHint.NEW_PASSWORD -> 3
        FieldHint.USERNAME, FieldHint.EMAIL_ADDRESS, FieldHint.PHONE_NUMBER -> 2
        else -> 0
    }

    private fun isLoginHint(hint: FieldHint): Boolean {
        return hint == FieldHint.USERNAME ||
            hint == FieldHint.EMAIL_ADDRESS ||
            hint == FieldHint.PHONE_NUMBER ||
            hint == FieldHint.PASSWORD ||
            hint == FieldHint.NEW_PASSWORD
    }

    private fun resolveFilledValues(
        accountValue: String,
        decryptedPassword: String?,
        autofillIds: List<AutofillId>,
        autofillHints: List<String>,
    ): LinkedHashMap<AutofillId, String> {
        val normalizedHints = autofillHints.map { it.trim().lowercase() }
        val hasPasswordTarget = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name.lowercase() ||
                it == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name.lowercase() ||
                it.contains("password") ||
                it.contains("pass")
        }
        if (hasPasswordTarget && decryptedPassword.isNullOrBlank()) {
            Log.w(TAG, "Authentication callback canceled: password decryption unavailable")
            return linkedMapOf()
        }

        val fillEmailWithAccount = AccountFillPolicy.shouldFillEmailWithAccount(applicationContext)
        val hasUsernameHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() ||
                it.contains("username")
        }
        val hasPhoneHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() ||
                it.contains("phone") ||
                it.contains("mobile") ||
                it.contains("tel")
        }
        val hasEmailHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() ||
                it.contains("email")
        }
        val hasAccountHint = hasUsernameHint || hasPhoneHint
        val allowAccountInEmailField =
            fillEmailWithAccount || accountValue.contains("@") || (!hasAccountHint && hasEmailHint)

        val filledValues = linkedMapOf<AutofillId, String>()
        autofillIds.forEachIndexed { index, autofillId ->
            val normalizedHint = autofillHints.getOrNull(index)?.trim()?.lowercase().orEmpty()
            val value = when {
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() ||
                    normalizedHint.contains("username") -> accountValue
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() ||
                    normalizedHint.contains("phone") ||
                    normalizedHint.contains("mobile") ||
                    normalizedHint.contains("tel") -> accountValue
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() ||
                    normalizedHint.contains("email") -> if (allowAccountInEmailField) accountValue else null
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name.lowercase() ||
                    normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name.lowercase() ||
                    normalizedHint.contains("password") ||
                    normalizedHint.contains("pass") -> decryptedPassword
                else -> null
            }
            if (!value.isNullOrBlank()) {
                filledValues[autofillId] = value
            }
        }

        if (filledValues.isNotEmpty()) {
            return filledValues
        }

        Log.w(TAG, "No strict hint matched in callback, trying controlled fallback")
        autofillIds.forEachIndexed { index, autofillId ->
            val normalizedHint = autofillHints.getOrNull(index)?.lowercase().orEmpty()
            val fallbackValue = when {
                normalizedHint.contains("pass") -> decryptedPassword
                normalizedHint.contains("user") ||
                    normalizedHint.contains("email") ||
                    normalizedHint.contains("phone") ||
                    normalizedHint.contains("mobile") ||
                    normalizedHint.contains("tel") ||
                    normalizedHint.contains("号码") ||
                    normalizedHint.contains("手机号") ||
                    normalizedHint.contains("account") ||
                    normalizedHint.contains("login") -> accountValue
                autofillIds.size == 1 -> if (accountValue.isNotBlank()) accountValue else decryptedPassword
                index == 0 -> accountValue
                index == 1 -> decryptedPassword
                else -> null
            }
            if (!fallbackValue.isNullOrBlank()) {
                filledValues[autofillId] = fallbackValue
            }
        }
        return filledValues
    }

    private suspend fun rememberLastFilledCredential(
        passwordId: Long,
        primaryIdentifier: String?,
        aliases: List<String>,
    ) {
        val normalizedIdentifiers = buildList {
            primaryIdentifier
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            aliases
                .asSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .forEach(::add)
        }.distinct()
        if (normalizedIdentifiers.isEmpty()) return

        val preferences = AutofillPreferences(applicationContext)
        normalizedIdentifiers.forEach { identifier ->
            preferences.completeAutofillInteraction(identifier, passwordId)
        }
    }

    private suspend fun rememberLearnedFieldSignature(fieldSignatureKey: String?) {
        val signatureKey = fieldSignatureKey?.trim()?.lowercase().orEmpty()
        if (signatureKey.isBlank()) return
        AutofillPreferences(applicationContext).markFieldSignatureLearned(signatureKey)
    }

    private fun cancelAndFinish(reason: String) {
        AutofillLogger.w("CALLBACK", "Cancel autofill callback: $reason")
        setResult(Activity.RESULT_CANCELED)
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }
}
